package com.careercompass.jobanalysis.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.domain.JobAnalysisStep;
import com.careercompass.jobanalysis.dto.CreateJobAnalysisRequest;
import com.careercompass.jobanalysis.dto.JobAnalysisPostingResponse;
import com.careercompass.jobanalysis.dto.JobPostingComparisonSnapshot;
import com.careercompass.jobanalysis.exception.InvalidJobAnalysisRequestException;
import com.careercompass.jobanalysis.exception.JobAnalysisInputNotFoundException;
import com.careercompass.jobanalysis.exception.JobAnalysisNotFoundException;
import com.careercompass.jobanalysis.exception.ProjectSourceUnavailableException;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.projectresponsibility.domain.UserProfileProjectResponsibility;
import com.careercompass.projectresponsibility.repository.UserProfileProjectResponsibilityRepository;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.domain.ProjectSourceStatus;
import com.careercompass.projectsource.repository.ProjectSourceRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final JobAnalysisPostingRepository jobAnalysisPostingRepository;
    private final UserProfileVersionRepository userProfileVersionRepository;
    private final UserProfileProjectResponsibilityRepository
            userProfileProjectResponsibilityRepository;
    private final ProjectSourceRepository projectSourceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * 기능: 여러 워커가 중복 처리하지 않도록 SKIP LOCKED로 대기 작업을 하나 선점한다.
     * 반환 값: 선점한 작업(없으면 빈 값)을 반환한다.
     */
    @Transactional
    public Optional<JobAnalysis> claimNextQueuedAnalysis() {
        Optional<JobAnalysis> claimed =
                jobAnalysisRepository.findNextQueuedForUpdateSkipLocked();
        claimed.ifPresent(analysis -> {
            analysis.markRunning(Instant.now(clock));
            analysis.getProjectSources();
            jobAnalysisRepository.save(analysis);
        });
        return claimed;
    }

    /**
     * 기능: 분석 작업에 고정된 사용자 프로필 버전을 조회한다.
     * 반환 값: 목표 직무·기술 태그가 포함된 프로필 버전을 반환한다.
     */
    @Transactional(readOnly = true)
    public UserProfileVersion loadFixedProfileVersion(JobAnalysis jobAnalysis) {
        return userProfileVersionRepository
                .findByUserProfile_IdAndProfileVersionAndUserProfile_UserId(
                        jobAnalysis.getUserProfileId(),
                        jobAnalysis.getUserProfileVersion(),
                        jobAnalysis.getUserId()
                )
                .orElseThrow(JobAnalysisInputNotFoundException::new);
    }

    /**
     * 기능: 실행 중인 분석 작업의 현재 단계를 갱신한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void advanceStep(UUID jobAnalysisId, JobAnalysisStep step) {
        jobAnalysisRepository.findById(jobAnalysisId).ifPresent(jobAnalysis -> {
            jobAnalysis.advanceStep(step, Instant.now(clock));
            jobAnalysisRepository.save(jobAnalysis);
        });
    }

    /**
     * 기능: 공식 Provider의 검색 결과가 정상적으로 0건일 때 작업을 완료로 표시한다
     * (developer-job-analysis-api.md "부분 완료와 실패": 0건 응답은 실패가 아니다).
     * 반환 값: 없음.
     */
    @Transactional
    public void recordEmptySearchResult(UUID jobAnalysisId) {
        jobAnalysisRepository.findById(jobAnalysisId).ifPresent(jobAnalysis -> {
            jobAnalysis.markCompleted(Instant.now(clock));
            jobAnalysisRepository.save(jobAnalysis);
            log.info("job_analysis_completed_no_postings jobAnalysisId={}", jobAnalysisId);
        });
    }

    /**
     * 기능: 추출된 채용공고를 저장하고 프로젝트 근거 후보에 대한 사용자 확인을 기다린다.
     * 반환 값: 없음.
     */
    @Transactional
    public void recordExtractionAwaitingUserConfirmation(
            UUID jobAnalysisId,
            List<JobAnalysisPosting> postings
    ) {
        JobAnalysis jobAnalysis = jobAnalysisRepository.findById(jobAnalysisId)
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        postings.forEach(jobAnalysisPostingRepository::save);
        jobAnalysis.awaitUserConfirmation(Instant.now(clock));
        jobAnalysisRepository.save(jobAnalysis);
        log.info(
                "job_analysis_extraction_awaiting_user_confirmation "
                        + "jobAnalysisId={} postingCount={}",
                jobAnalysisId,
                postings.size()
        );
    }

    /**
     * 기능: 추출에 성공한 채용공고를 저장하고 근거 비교 단계로 전환한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void recordExtractionReadyForComparison(
            UUID jobAnalysisId,
            List<JobAnalysisPosting> postings
    ) {
        JobAnalysis jobAnalysis = jobAnalysisRepository.findById(jobAnalysisId)
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        postings.forEach(jobAnalysisPostingRepository::save);
        jobAnalysis.advanceStep(JobAnalysisStep.COMPARING_EVIDENCE, Instant.now(clock));
        jobAnalysisRepository.save(jobAnalysis);
        log.info(
                "job_analysis_extraction_ready_for_comparison "
                        + "jobAnalysisId={} postingCount={}",
                jobAnalysisId,
                postings.size()
        );
    }

    /**
     * 기능: 분석에 고정된 프로필 버전에서 사용자가 확정한 프로젝트 담당 업무를 조회한다.
     * 반환 값: 비교에 사용할 후보 식별자, 저장소 식별자와 확정 문장을 반환한다.
     */
    @Transactional(readOnly = true)
    public List<ConfirmedProjectResponsibility> listConfirmedResponsibilities(
            JobAnalysis jobAnalysis
    ) {
        UserProfileVersion profileVersion = userProfileVersionRepository
                .findByUserProfile_IdAndProfileVersionAndUserProfile_UserId(
                        jobAnalysis.getUserProfileId(),
                        jobAnalysis.getUserProfileVersion(),
                        jobAnalysis.getUserId()
                )
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        List<UserProfileProjectResponsibility> responsibilities =
                userProfileProjectResponsibilityRepository
                        .findAllByUserProfileVersion_IdOrderByDisplayOrderAsc(
                                profileVersion.getId());
        return responsibilities.stream()
                .map(responsibility -> new ConfirmedProjectResponsibility(
                        responsibility.getSourceCandidateId(),
                        responsibility.getProjectSource().getId(),
                        responsibility.getConfirmedText()
                ))
                .toList();
    }

    /**
     * 기능: 공고 하나의 의미 비교 결과 JSON을 해당 분석 소유 공고에 저장한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void recordPostingComparison(
            UUID jobAnalysisId,
            UUID postingId,
            String comparisonJson
    ) {
        JobAnalysisPosting posting = jobAnalysisPostingRepository
                .findByIdAndJobAnalysisId(postingId, jobAnalysisId)
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        posting.recordComparison(comparisonJson);
        jobAnalysisPostingRepository.save(posting);
    }

    /**
     * 기능: 공고별 비교 처리 결과를 기준으로 분석을 완료, 부분 완료 또는 실패로 확정한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void finishEvidenceComparison(
            UUID jobAnalysisId,
            int completedUnits,
            int totalUnits,
            int successfulCallCount,
            JobAnalysisFailureCode failureCode
    ) {
        JobAnalysis jobAnalysis = jobAnalysisRepository.findById(jobAnalysisId)
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        Instant now = Instant.now(clock);
        jobAnalysis.advanceStep(JobAnalysisStep.FINALIZING_RESULT, now);
        if (failureCode == null) {
            jobAnalysis.markComparisonCompleted(completedUnits, totalUnits, now);
        } else if (successfulCallCount > 0) {
            jobAnalysis.markComparisonPartiallyCompleted(
                    completedUnits, totalUnits, failureCode, now);
        } else {
            jobAnalysis.markFailed(now, failureCode);
        }
        jobAnalysisRepository.save(jobAnalysis);
        log.info(
                "job_analysis_comparison_finished jobAnalysisId={} status={} "
                        + "completedUnits={} totalUnits={}",
                jobAnalysisId,
                jobAnalysis.getAnalysisStatus(),
                completedUnits,
                totalUnits
        );
    }

    /**
     * 기능: 작업을 주어진 원인으로 실패 표시한다.
     * 반환 값: 없음.
     */
    @Transactional
    public void markAnalysisFailed(UUID jobAnalysisId, JobAnalysisFailureCode failureCode) {
        jobAnalysisRepository.findById(jobAnalysisId).ifPresent(jobAnalysis -> {
            jobAnalysis.markFailed(Instant.now(clock), failureCode);
            jobAnalysisRepository.save(jobAnalysis);
            log.warn(
                    "job_analysis_failed jobAnalysisId={} failureCode={}",
                    jobAnalysisId,
                    failureCode
            );
        });
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

    /**
     * 기능: 분석 작업에 저장된 채용공고 추출 결과 목록을 조회한다.
     * 반환 값: 저장된 순서의 결과 목록을 반환한다(호출 전 소유권 확인은 호출부 책임).
     */
    @Transactional(readOnly = true)
    public List<JobAnalysisPosting> listPostings(UUID jobAnalysisId) {
        return jobAnalysisPostingRepository.findByJobAnalysisIdOrderByCreatedAtAsc(jobAnalysisId);
    }
    /**
     * 기능: 분석 작업에 저장된 공고 메타데이터와 의미 비교 결과를 사용자 응답 형태로 조회한다.
     * 반환 값: 저장 순서대로 정렬된 공고별 비교 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    public List<JobAnalysisPostingResponse> listPostingResults(UUID jobAnalysisId) {
        return jobAnalysisPostingRepository
                .findByJobAnalysisIdOrderByCreatedAtAsc(jobAnalysisId)
                .stream()
                .map(posting -> new JobAnalysisPostingResponse(
                        posting.getId(),
                        posting.getJobPostingId(),
                        posting.getProviderPostingId(),
                        posting.getProvider(),
                        posting.getCompanyName(),
                        posting.getOriginalJobTitle(),
                        posting.getSourceUrl(),
                        parseComparison(posting.getComparisonJson())
                ))
                .toList();
    }

    private JobPostingComparisonSnapshot parseComparison(String comparisonJson) {
        if (comparisonJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(comparisonJson, JobPostingComparisonSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("STORED_COMPARISON_JSON_INVALID", exception);
        }
    }
}
