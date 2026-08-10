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
import com.careercompass.jobanalysis.exception.InvalidJobAnalysisRequestException;
import com.careercompass.jobanalysis.exception.JobAnalysisInputNotFoundException;
import com.careercompass.jobanalysis.exception.JobAnalysisNotFoundException;
import com.careercompass.jobanalysis.exception.ProjectSourceUnavailableException;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.domain.ProjectSourceStatus;
import com.careercompass.projectsource.repository.ProjectSourceRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(JobAnalysisService.class);

    private final JobAnalysisRepository jobAnalysisRepository;
    private final JobAnalysisPostingRepository jobAnalysisPostingRepository;
    private final UserProfileVersionRepository userProfileVersionRepository;
    private final ProjectSourceRepository projectSourceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public JobAnalysisService(
            JobAnalysisRepository jobAnalysisRepository,
            JobAnalysisPostingRepository jobAnalysisPostingRepository,
            UserProfileVersionRepository userProfileVersionRepository,
            ProjectSourceRepository projectSourceRepository,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.jobAnalysisRepository = jobAnalysisRepository;
        this.jobAnalysisPostingRepository = jobAnalysisPostingRepository;
        this.userProfileVersionRepository = userProfileVersionRepository;
        this.projectSourceRepository = projectSourceRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

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
     * 기능: 대기 중인 분석 작업을 하나 선점하고 RUNNING으로 표시한다.
     * 반환 값: 선점한 작업(없으면 빈 값)을 반환한다.
     *
     * 2026-08-04 임시 작업(코덱스 사용량 한도 공백기 대응, docs/current-work.md 참고) —
     * JobAnalysisWorker가 이 메서드를 호출한다. 짧은 트랜잭션 안에서 SKIP LOCKED로 잠금
     * 선점하고 즉시 커밋해, 다른 워커 인스턴스와 경합하지 않는다.
     */
    @Transactional
    public Optional<JobAnalysis> claimNextQueuedAnalysis() {
        Optional<JobAnalysis> claimed =
                jobAnalysisRepository.findNextQueuedForUpdateSkipLocked();
        claimed.ifPresent(analysis -> {
            analysis.markRunning(Instant.now(clock));
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
     * 기능: 추출에 성공한 채용공고 결과를 내부 중간 산출물로 저장하고 작업을 실패로
     * 표시한다. 확정 계약의 PARTIALLY_COMPLETED는 비교 결과가 하나 이상 생성된 뒤 후속
     * 단계가 실패해야 하는데, 이번 범위는 COMPARING_EVIDENCE를 실행하지 않아 이 조건을
     * 충족할 수 없다(코덱스 확인, PR #48). 여기서 저장하는 결과는 개발 연결 검증용
     * 중간 산출물이며 사용자용 완료 결과로 노출하지 않는다.
     * 반환 값: 없음.
     */
    @Transactional
    public void recordExtractionOnlyFailure(
            UUID jobAnalysisId,
            List<JobAnalysisPosting> postings
    ) {
        JobAnalysis jobAnalysis = jobAnalysisRepository.findById(jobAnalysisId)
                .orElseThrow(JobAnalysisInputNotFoundException::new);
        postings.forEach(jobAnalysisPostingRepository::save);
        jobAnalysis.markFailed(Instant.now(clock), JobAnalysisFailureCode.COMPARISON_STAGE_NOT_IMPLEMENTED);
        jobAnalysisRepository.save(jobAnalysis);
        log.info(
                "job_analysis_extraction_only_marked_failed jobAnalysisId={} postingCount={}",
                jobAnalysisId,
                postings.size()
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
}
