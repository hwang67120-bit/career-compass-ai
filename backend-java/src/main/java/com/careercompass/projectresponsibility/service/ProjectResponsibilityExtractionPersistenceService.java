package com.careercompass.projectresponsibility.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.projectresponsibility.config.ProjectResponsibilityReviewPolicyProperties;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityCandidate;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityEvidence;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityExtractionTask;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilitySnapshotExclusion;
import com.careercompass.projectresponsibility.domain.ProjectTechnologyFinding;
import com.careercompass.projectresponsibility.domain.ProjectTechnologyFindingEvidence;
import com.careercompass.projectresponsibility.domain.ProjectTechnologySuggestion;
import com.careercompass.projectresponsibility.domain.ProjectTechnologySuggestionEvidence;

import com.careercompass.projectresponsibility.repository.ProjectResponsibilityCandidateRepository;
import com.careercompass.projectresponsibility.repository.ProjectResponsibilityExtractionTaskRepository;
import com.careercompass.projectresponsibility.repository.ProjectResponsibilitySnapshotExclusionRepository;
import com.careercompass.projectresponsibility.repository.ProjectTechnologyFindingRepository;
import com.careercompass.projectresponsibility.repository.ProjectTechnologySuggestionRepository;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.service.PreparedRepositorySnapshot;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionEnvelope;
import com.careercompass.technologytag.domain.TechnologyTag;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import com.careercompass.userprofile.domain.UserProfileVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProjectResponsibilityExtractionPersistenceService {

    private final ProjectResponsibilityExtractionTaskRepository taskRepository;
    private final ProjectResponsibilityCandidateRepository candidateRepository;
    private final ProjectTechnologyFindingRepository findingRepository;
    private final ProjectTechnologySuggestionRepository suggestionRepository;
    private final ProjectResponsibilitySnapshotExclusionRepository exclusionRepository;
    private final TechnologyTagRepository technologyTagRepository;
    private final ProjectResponsibilityReviewPolicyProperties reviewPolicy;
    private final Clock clock;

    /**
     * 기능: 채용 분석에 연결된 프로젝트 추출 작업을 외부 호출 전에 저장한다.
     * 반환 값: 새 추출 작업 식별자를 반환한다.
     */
    @Transactional
    public UUID createTask(
            JobAnalysis jobAnalysis,
            ProjectSource projectSource,
            UserProfileVersion profileVersion,
            Set<UUID> selectedTechnologyTagIds
    ) {
        ProjectResponsibilityExtractionTask task =
                ProjectResponsibilityExtractionTask.create(
                        UUID.randomUUID(),
                        projectSource,
                        jobAnalysis.getId(),
                        profileVersion,
                        selectedTechnologyTagIds,
                        Instant.now(clock));
        return taskRepository.save(task).getId();
    }

    /**
     * 기능: 검증을 통과한 후보·최소 근거·제외 사유와 추출 실행 상태를 원자적으로 저장한다.
     * 반환 값: 사용자 확인이 필요한 후보가 존재하면 true를 반환한다.
     */
    @Transactional
    public boolean completeTask(
            UUID taskId,
            List<ProjectResponsibilityCandidateDraft> candidates,
            List<ProjectTechnologyFindingDraft> selectedTechnologyFindings,
            List<ProjectTechnologySuggestionDraft> technologySuggestions,
            PreparedRepositorySnapshot snapshot,
            Set<UUID> failedTechnologyTagIds,
            String failureCode,
            PythonProjectResponsibilityExtractionEnvelope.ModelExecution modelExecution
    ) {
        ProjectResponsibilityExtractionTask task = taskRepository.findById(taskId)
                .orElseThrow();
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(
                reviewPolicy.candidateRetentionDays(), ChronoUnit.DAYS);

        for (PreparedRepositorySnapshot.Exclusion exclusion : snapshot.exclusions()) {
            exclusionRepository.save(ProjectResponsibilitySnapshotExclusion.create(
                    UUID.randomUUID(),
                    task,
                    exclusion.path(),
                    exclusion.reason()));
        }
        for (ProjectResponsibilityCandidateDraft candidateDraft : candidates) {
            ProjectResponsibilityCandidate candidate =
                    ProjectResponsibilityCandidate.create(
                            UUID.randomUUID(),
                            task,
                            candidateDraft.text(),
                            candidateDraft.technologyTagIds(),
                            now,
                            expiresAt);
            for (String evidenceId : candidateDraft.sourceEvidenceIds()) {
                PreparedRepositorySnapshot.Evidence evidence =
                        snapshot.evidenceById().get(evidenceId);
                if (evidence == null) {
                    throw new IllegalStateException(
                            "PROJECT_RESPONSIBILITY_EVIDENCE_NOT_FOUND");
                }
                candidate.addSourceEvidence(ProjectResponsibilityEvidence.create(
                        UUID.randomUUID(),
                        candidate,
                        evidence.evidenceId(),
                        evidence.path(),
                        evidence.text()));
            }
            candidateRepository.save(candidate);
        }
        for (ProjectTechnologyFindingDraft findingDraft : selectedTechnologyFindings) {
            TechnologyTag technologyTag = technologyTagRepository
                    .findById(findingDraft.technologyTagId()).orElseThrow();
            ProjectTechnologyFinding finding = ProjectTechnologyFinding.create(
                    UUID.randomUUID(), task, technologyTag, findingDraft.findingStatus());
            for (String evidenceId : findingDraft.sourceEvidenceIds()) {
                PreparedRepositorySnapshot.Evidence evidence =
                        snapshot.evidenceById().get(evidenceId);
                if (evidence == null) {
                    throw new IllegalStateException(
                            "PROJECT_TECHNOLOGY_FINDING_EVIDENCE_NOT_FOUND");
                }
                finding.addSourceEvidence(ProjectTechnologyFindingEvidence.create(
                        UUID.randomUUID(), finding, evidence.evidenceId(),
                        evidence.path(), evidence.text()));
            }
            findingRepository.save(finding);
        }
        for (ProjectTechnologySuggestionDraft suggestionDraft : technologySuggestions) {
            TechnologyTag technologyTag = technologyTagRepository
                    .findById(suggestionDraft.technologyTagId()).orElseThrow();
            ProjectTechnologySuggestion suggestion = ProjectTechnologySuggestion.create(
                    UUID.randomUUID(), task, technologyTag, now, expiresAt);
            for (String evidenceId : suggestionDraft.sourceEvidenceIds()) {
                PreparedRepositorySnapshot.Evidence evidence =
                        snapshot.evidenceById().get(evidenceId);
                if (evidence == null) {
                    throw new IllegalStateException(
                            "PROJECT_TECHNOLOGY_SUGGESTION_EVIDENCE_NOT_FOUND");
                }
                suggestion.addSourceEvidence(ProjectTechnologySuggestionEvidence.create(
                        UUID.randomUUID(), suggestion, evidence.evidenceId(),
                        evidence.path(), evidence.text()));
            }
            suggestionRepository.save(suggestion);
        }

        task.completeExtraction(
                failedTechnologyTagIds,
                failureCode,
                modelExecution == null ? null : modelExecution.provider(),
                modelExecution == null ? null : modelExecution.model());
        boolean requiresConfirmation =
                !candidates.isEmpty() || !technologySuggestions.isEmpty();
        if (!requiresConfirmation) {
            task.completeReview(now);
        }
        taskRepository.save(task);
        return requiresConfirmation;
    }

    /**
     * 기능: 표준 선택 기술이 없어 Python을 실행하지 않은 작업을 후보 없는 완료로 저장한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void completeWithoutCandidates(UUID taskId) {
        ProjectResponsibilityExtractionTask task = taskRepository.findById(taskId)
                .orElseThrow();
        Instant now = Instant.now(clock);
        task.completeExtraction(Set.of(), null, null, null);
        task.completeReview(now);
        taskRepository.save(task);
    }

    /**
     * 기능: 저장소 또는 Python 호출이 전부 실패한 추출 작업의 원인을 저장한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void failTask(UUID taskId, String failureCode) {
        ProjectResponsibilityExtractionTask task = taskRepository.findById(taskId)
                .orElseThrow();
        task.failExtraction(failureCode);
        taskRepository.save(task);
    }
}
