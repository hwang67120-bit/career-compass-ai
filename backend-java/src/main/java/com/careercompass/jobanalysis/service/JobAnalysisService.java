package com.careercompass.jobanalysis.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.dto.CreateJobAnalysisRequest;
import com.careercompass.jobanalysis.exception.InvalidJobAnalysisRequestException;
import com.careercompass.jobanalysis.exception.JobAnalysisInputNotFoundException;
import com.careercompass.jobanalysis.exception.JobAnalysisNotFoundException;
import com.careercompass.jobanalysis.exception.ProjectSourceUnavailableException;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.domain.ProjectSourceStatus;
import com.careercompass.projectsource.repository.ProjectSourceRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class JobAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(JobAnalysisService.class);

    private final JobAnalysisRepository jobAnalysisRepository;
    private final UserProfileVersionRepository userProfileVersionRepository;
    private final ProjectSourceRepository projectSourceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /**
     * 기능: 현재 사용자의 프로필 버전과 선택한 저장소를 검증하고 분석 작업을 대기열에 저장한다.
     * 반환 값: QUEUED 상태로 생성한 분석 작업 식별자를 반환한다.
     */
    @Transactional
    public UUID createCurrentUserJobAnalysis(
            CreateJobAnalysisRequest request
    ) {
        validateRequest(request);
        UUID userId = currentUserProvider.getCurrentUserId();
        UserProfileVersion profileVersion = userProfileVersionRepository
                .findByUserProfile_IdAndProfileVersionAndUserProfile_UserId(
                        request.userProfileId(),
                        request.userProfileVersion(),
                        userId
                )
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        validateProfile(profileVersion);
        List<ProjectSource> projectSources = retrieveProjectSources(
                request.projectSourceIds(),
                userId
        );

        UUID jobAnalysisId = UUID.randomUUID();
        Instant queuedAt = Instant.now(clock);
        JobAnalysis jobAnalysis = JobAnalysis.createQueued(
                jobAnalysisId,
                userId,
                request.userProfileId(),
                request.userProfileVersion(),
                projectSources,
                queuedAt
        );
        jobAnalysisRepository.save(jobAnalysis);
        log.info(
                "job_analysis_queued jobAnalysisId={} userProfileId={} "
                        + "userProfileVersion={} projectSourceCount={}",
                jobAnalysisId,
                request.userProfileId(),
                request.userProfileVersion(),
                projectSources.size()
        );
        return jobAnalysisId;
    }

    private void validateRequest(CreateJobAnalysisRequest request) {
        if (request == null) {
            throw invalid("request", "요청 본문은 필수입니다.");
        }
        if (request.userProfileId() == null) {
            throw invalid(
                    "userProfileId",
                    "사용자 프로필 식별자는 필수입니다."
            );
        }
        if (request.userProfileVersion() == null
                || request.userProfileVersion() < 1) {
            throw invalid(
                    "userProfileVersion",
                    "사용자 프로필 버전은 1 이상이어야 합니다."
            );
        }
        if (request.projectSourceIds() == null
                || request.projectSourceIds().isEmpty()) {
            throw invalid(
                    "projectSourceIds",
                    "분석할 저장소를 하나 이상 선택해야 합니다."
            );
        }
        if (request.projectSourceIds().stream().anyMatch(
                projectSourceId -> projectSourceId == null
        )) {
            throw invalid(
                    "projectSourceIds",
                    "저장소 식별자는 null일 수 없습니다."
            );
        }
        if (new HashSet<>(request.projectSourceIds()).size()
                != request.projectSourceIds().size()) {
            throw invalid(
                    "projectSourceIds",
                    "같은 저장소를 중복 선택할 수 없습니다."
            );
        }
    }

    private void validateProfile(UserProfileVersion profileVersion) {
        if (profileVersion.getTargetJobTitle().isBlank()
                || profileVersion.getTechnologyTags().isEmpty()) {
            throw invalid(
                    "userProfileId",
                    "분석 프로필에 목표 직무와 기술 태그가 필요합니다."
            );
        }
    }

    private List<ProjectSource> retrieveProjectSources(
            List<UUID> projectSourceIds,
            UUID userId
    ) {
        Map<UUID, ProjectSource> projectSourcesById = new HashMap<>();
        projectSourceRepository.findAllById(projectSourceIds)
                .forEach(projectSource -> projectSourcesById.put(
                        projectSource.getId(),
                        projectSource
                ));
        if (projectSourcesById.size() != projectSourceIds.size()) {
            throw new JobAnalysisInputNotFoundException();
        }

        return projectSourceIds.stream()
                .map(projectSourceId -> {
                    ProjectSource projectSource =
                            projectSourcesById.get(projectSourceId);
                    if (!projectSource.getUserId().equals(userId)) {
                        throw new JobAnalysisInputNotFoundException();
                    }
                    if (projectSource.getProjectSourceStatus()
                            != ProjectSourceStatus.REGISTERED) {
                        throw new ProjectSourceUnavailableException();
                    }
                    return projectSource;
                })
                .toList();
    }

    private InvalidJobAnalysisRequestException invalid(
            String fieldName,
            String fieldMessage
    ) {
        return new InvalidJobAnalysisRequestException(
                fieldName,
                fieldMessage
        );
    }

    /**
     * 기능: 현재 사용자가 소유한 분석 작업을 조회한다. 다른 사용자의 작업은 존재 여부를
     * 노출하지 않도록 찾지 못했을 때와 같은 예외를 던진다
     * (docs/architecture/backend-job-processing-and-sse.md의 404 규칙).
     * 반환 값: 조회한 분석 작업을 반환한다.
     */
    @Transactional(readOnly = true)
    public JobAnalysis getCurrentUserJobAnalysis(UUID jobAnalysisId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        JobAnalysis jobAnalysis = jobAnalysisRepository.findById(jobAnalysisId)
                .orElseThrow(JobAnalysisNotFoundException::new);
        if (!jobAnalysis.getUserId().equals(userId)) {
            throw new JobAnalysisNotFoundException();
        }
        return jobAnalysis;
    }


}
