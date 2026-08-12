package com.careercompass.projectresponsibility.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.projectresponsibility.domain.*;
import com.careercompass.projectresponsibility.dto.*;
import com.careercompass.projectresponsibility.exception.*;
import com.careercompass.projectresponsibility.repository.*;
import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.technologytag.domain.TechnologyTag;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import com.careercompass.userprofile.domain.*;
import com.careercompass.userprofile.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectResponsibilityReviewService {
    private static final int MAX_CONFIRMED_TEXT_CODE_POINTS = 500;
    private final ProjectResponsibilityExtractionTaskRepository taskRepository;
    private final ProjectResponsibilityCandidateRepository candidateRepository;
    private final UserProfileProjectResponsibilityRepository responsibilityRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileVersionRepository profileVersionRepository;
    private final JobAnalysisRepository jobAnalysisRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TechnologyTagRepository technologyTagRepository;
    private final Clock clock;

    public ProjectResponsibilityReviewService(
            ProjectResponsibilityExtractionTaskRepository taskRepository,
            ProjectResponsibilityCandidateRepository candidateRepository,
            UserProfileProjectResponsibilityRepository responsibilityRepository,
            UserProfileRepository userProfileRepository,
            UserProfileVersionRepository profileVersionRepository,
            JobAnalysisRepository jobAnalysisRepository,
            CurrentUserProvider currentUserProvider,
            TechnologyTagRepository technologyTagRepository,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.candidateRepository = candidateRepository;
        this.responsibilityRepository = responsibilityRepository;
        this.userProfileRepository = userProfileRepository;
        this.profileVersionRepository = profileVersionRepository;
        this.jobAnalysisRepository = jobAnalysisRepository;
        this.currentUserProvider = currentUserProvider;
        this.technologyTagRepository = technologyTagRepository;
        this.clock = clock;
    }

    /**
     * 기능: 현재 사용자의 프로젝트 분석 미리보기 후보를 조회한다.
     * 반환 값: 저장소 버전, 검토 상태와 미확정·확정 후보 목록을 반환한다.
     */
    @Transactional(readOnly = true)
    public ProjectResponsibilityReviewResponse retrieve(UUID projectSourceId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        ProjectResponsibilityExtractionTask task = taskRepository
                .findFirstByProjectSource_IdOrderByCreatedAtDesc(projectSourceId)
                .filter(found -> found.getProjectSource().getUserId().equals(userId))
                .orElseThrow(ProjectResponsibilityNotFoundException::new);
        return toReviewResponse(task);
    }

    /**
     * 기능: 후보 결정을 저장하고 마지막 결정이면 프로필 버전과 분석 재개 상태를 원자적으로 저장한다.
     * 반환 값: 갱신된 후보, 전체 검토 완료 여부와 재개한 분석 식별자를 반환한다.
     */
    @Transactional
    public ProjectResponsibilityDecisionResponse decide(
            UUID candidateId, ProjectResponsibilityDecisionRequest request) {
        validateRequest(request);
        UUID userId = currentUserProvider.getCurrentUserId();
        ProjectResponsibilityExtractionTask task = taskRepository
                .findByCandidateIdForUpdate(candidateId)
                .filter(found -> found.getProjectSource().getUserId().equals(userId))
                .orElseThrow(ProjectResponsibilityNotFoundException::new);
        ProjectResponsibilityCandidate candidate = candidateRepository
                .findByIdAndExtractionTask_Id(candidateId, task.getId())
                .orElseThrow(ProjectResponsibilityNotFoundException::new);
        Instant now = Instant.now(clock);
        if (!candidate.getExpiresAt().isAfter(now)) {
            throw new ProjectResponsibilityExpiredException();
        }
        if (candidate.getCandidateStatus() != ProjectResponsibilityCandidateStatus.UNCONFIRMED) {
            if (isSameFinalDecision(candidate, request)
                    && isReplayVersion(candidate.getLockVersion(), request.expectedVersion())) {
                boolean reviewCompleted =
                        task.getReviewStatus() == ProjectResponsibilityReviewStatus.REVIEW_COMPLETED;
                return new ProjectResponsibilityDecisionResponse(
                        toCandidateResponse(candidate), reviewCompleted,
                        reviewCompleted ? task.getLinkedJobAnalysisId() : null);
            }
            throw new ProjectResponsibilityStateConflictException();
        }
        if (candidate.getLockVersion() != request.expectedVersion()) {
            throw new ProjectResponsibilityConflictException();
        }
        if (request.decision() == ProjectResponsibilityDecisionRequest.Decision.CONFIRM) {
            candidate.confirm(validateText(request.confirmedText()), now);
        } else {
            if (request.confirmedText() != null) {
                throw new InvalidProjectResponsibilityDecisionException();
            }
            candidate.reject(now);
        }
        candidateRepository.saveAndFlush(candidate);
        if (candidateRepository.existsByExtractionTask_IdAndCandidateStatus(
                task.getId(), ProjectResponsibilityCandidateStatus.UNCONFIRMED)) {
            return new ProjectResponsibilityDecisionResponse(toCandidateResponse(candidate), false, null);
        }
        UUID resumedAnalysisId = completeReview(task, now);
        return new ProjectResponsibilityDecisionResponse(
                toCandidateResponse(candidate), true, resumedAnalysisId);
    }

    private UUID completeReview(ProjectResponsibilityExtractionTask task, Instant now) {
        List<ProjectResponsibilityCandidate> confirmed = candidateRepository
                .findAllByExtractionTask_IdAndCandidateStatusOrderByCreatedAtAsc(
                        task.getId(), ProjectResponsibilityCandidateStatus.CONFIRMED);
        UserProfileVersion fixedVersion = task.getBaseUserProfileVersion();
        if (!confirmed.isEmpty()) {
            fixedVersion = createProfileVersion(task, confirmed, now);
        }
        UUID analysisId = task.getLinkedJobAnalysisId();
        if (analysisId != null) {
            JobAnalysis analysis = jobAnalysisRepository.findByIdForUpdate(analysisId)
                    .orElseThrow(ProjectResponsibilityNotFoundException::new);
            analysis.resumeAfterUserConfirmation(
                    fixedVersion.getUserProfile().getId(), fixedVersion.getProfileVersion(), now);
        }
        task.completeReview(now);
        return analysisId;
    }

    private UserProfileVersion createProfileVersion(
            ProjectResponsibilityExtractionTask task,
            List<ProjectResponsibilityCandidate> confirmed, Instant now) {
        UserProfileVersion base = task.getBaseUserProfileVersion();
        UserProfile profile = userProfileRepository.findByIdForUpdate(base.getUserProfile().getId())
                .orElseThrow(ProjectResponsibilityNotFoundException::new);
        int nextVersion = profile.getCurrentVersion() + 1;
        profile.advanceVersion(nextVersion, now);
        UserProfileVersion created = UserProfileVersion.create(
                UUID.randomUUID(), profile, nextVersion, base.getTargetJobTitle(),
                fingerprint(base, confirmed), now);
        for (UserProfileTechnologyTag tag : base.getTechnologyTags()) {
            created.addTechnologyTag(UserProfileTechnologyTag.create(
                    UUID.randomUUID(), created, tag.getTechnologyTag(), tag.getRawName(),
                    tag.getNormalizedName(), tag.getDisplayName(), tag.getSourceType(),
                    tag.getDisplayOrder()));
        }
        profileVersionRepository.save(created);
        int displayOrder = 0;
        List<UserProfileProjectResponsibility> existingResponsibilities = responsibilityRepository
                .findAllByUserProfileVersion_IdOrderByDisplayOrderAsc(base.getId());
        for (UserProfileProjectResponsibility existing : existingResponsibilities) {
            if (!existing.getProjectSource().getId().equals(task.getProjectSource().getId())) {
                responsibilityRepository.save(UserProfileProjectResponsibility.copy(
                        UUID.randomUUID(), created, existing, displayOrder++));
            }
        }
        for (ProjectResponsibilityCandidate candidate : confirmed) {
            responsibilityRepository.save(UserProfileProjectResponsibility.create(
                    UUID.randomUUID(), created, candidate,
                    candidate.getConfirmedText(), displayOrder++));
        }
        return created;
    }

    private String fingerprint(UserProfileVersion base, List<ProjectResponsibilityCandidate> candidates) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(base.getContentFingerprint().getBytes(StandardCharsets.UTF_8));
            for (ProjectResponsibilityCandidate candidate : candidates) {
                digest.update(candidate.getId().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(candidate.getConfirmedText().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void validateRequest(ProjectResponsibilityDecisionRequest request) {
        if (request == null || request.decision() == null || request.expectedVersion() < 0) {
            throw new InvalidProjectResponsibilityDecisionException();
        }
    }

    private String validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidProjectResponsibilityDecisionException();
        }
        String normalized = text.strip();
        if (normalized.codePointCount(0, normalized.length()) > MAX_CONFIRMED_TEXT_CODE_POINTS) {
            throw new InvalidProjectResponsibilityDecisionException();
        }
        return normalized;
    }

    private boolean isSameFinalDecision(
            ProjectResponsibilityCandidate candidate,
            ProjectResponsibilityDecisionRequest request) {
        if (request.decision() == ProjectResponsibilityDecisionRequest.Decision.REJECT) {
            return candidate.getCandidateStatus() == ProjectResponsibilityCandidateStatus.REJECTED
                    && request.confirmedText() == null;
        }
        if (candidate.getCandidateStatus() != ProjectResponsibilityCandidateStatus.CONFIRMED
                || request.confirmedText() == null) {
            return false;
        }
        return candidate.getConfirmedText().equals(request.confirmedText().strip());
    }

    private boolean isReplayVersion(long currentVersion, long expectedVersion) {
        return expectedVersion == currentVersion
                || (expectedVersion < Long.MAX_VALUE && expectedVersion + 1 == currentVersion);
    }

    private ProjectResponsibilityReviewResponse toReviewResponse(
            ProjectResponsibilityExtractionTask task) {
        return new ProjectResponsibilityReviewResponse(
                task.getProjectSource().getId(), task.getRepositoryVersion(),
                task.getReviewStatus().name(), task.getLinkedJobAnalysisId(),
                candidateRepository.findAllByExtractionTask_IdOrderByCreatedAtAsc(task.getId())
                        .stream().map(this::toCandidateResponse).toList());
    }

    private ProjectResponsibilityCandidateResponse toCandidateResponse(
            ProjectResponsibilityCandidate candidate) {
        Map<UUID, TechnologyTag> technologyTags = technologyTagRepository
                .findAllById(candidate.getTechnologyTagIds()).stream()
                .collect(Collectors.toMap(TechnologyTag::getId, Function.identity()));
        List<ProjectResponsibilityTechnologyTagResponse> relatedTechnologyTags =
                candidate.getTechnologyTagIds().stream()
                        .map(technologyTags::get)
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(TechnologyTag::getDisplayName))
                        .map(tag -> new ProjectResponsibilityTechnologyTagResponse(
                                tag.getId(), tag.getDisplayName()))
                        .toList();
        List<ProjectResponsibilityEvidenceResponse> sourceEvidence =
                candidate.getSourceEvidence().stream()
                        .map(evidence -> new ProjectResponsibilityEvidenceResponse(
                                evidence.getEvidenceId(),
                                evidence.getFilePath(),
                                evidence.getExcerpt()))
                        .toList();
        return new ProjectResponsibilityCandidateResponse(
                candidate.getId(), "PROJECT_RESPONSIBILITY",
                candidate.getExtractedText(), candidate.getConfirmedText(),
                candidate.getCandidateStatus().name(), candidate.getLockVersion(),
                relatedTechnologyTags, sourceEvidence, candidate.getCreatedAt(),
                candidate.getExpiresAt(), candidate.getDecidedAt());
    }
}
