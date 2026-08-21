package com.careercompass.jobanalysis.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.domain.JobAnalysisStep;
import com.careercompass.jobanalysis.exception.JobAnalysisInputNotFoundException;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.projectresponsibility.domain.UserProfileProjectResponsibility;
import com.careercompass.projectresponsibility.repository.UserProfileProjectResponsibilityRepository;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class JobAnalysisExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(JobAnalysisExecutionService.class);

    private final JobAnalysisRepository jobAnalysisRepository;
    private final JobAnalysisPostingRepository jobAnalysisPostingRepository;
    private final UserProfileVersionRepository userProfileVersionRepository;
    private final UserProfileProjectResponsibilityRepository
            userProfileProjectResponsibilityRepository;
    private final Clock clock;

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
        return findFixedProfileVersion(jobAnalysis);
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
        JobAnalysis jobAnalysis = requireJobAnalysis(jobAnalysisId);
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
        JobAnalysis jobAnalysis = requireJobAnalysis(jobAnalysisId);
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
        UserProfileVersion profileVersion = findFixedProfileVersion(jobAnalysis);
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
            int completedPostingCount,
            int totalPostingCount,
            int successfulPythonCallCount,
            JobAnalysisFailureCode failureCode
    ) {
        JobAnalysis jobAnalysis = requireJobAnalysis(jobAnalysisId);
        Instant now = Instant.now(clock);
        jobAnalysis.advanceStep(JobAnalysisStep.FINALIZING_RESULT, now);
        updateAnalysisFromComparisonOutcome(
                jobAnalysis,
                completedPostingCount,
                totalPostingCount,
                successfulPythonCallCount,
                failureCode,
                now);
        jobAnalysisRepository.save(jobAnalysis);
        log.info(
                "job_analysis_comparison_finished jobAnalysisId={} status={} "
                        + "completedUnits={} totalUnits={}",
                jobAnalysisId,
                jobAnalysis.getAnalysisStatus(),
                completedPostingCount,
                totalPostingCount
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
     * 기능: 분석 작업에 저장된 채용공고 추출 결과 목록을 조회한다.
     * 반환 값: 저장된 순서의 결과 목록을 반환한다(호출 전 소유권 확인은 호출부 책임).
     */
    @Transactional(readOnly = true)
    public List<JobAnalysisPosting> listPostings(UUID jobAnalysisId) {
        return jobAnalysisPostingRepository.findByJobAnalysisIdOrderByCreatedAtAsc(jobAnalysisId);
    }

    /**
     * 기능: 분석에 고정된 사용자 프로필 버전을 소유자 조건과 함께 조회한다.
     * 반환 값: 고정된 사용자 프로필 버전을 반환한다.
     */
    private UserProfileVersion findFixedProfileVersion(JobAnalysis jobAnalysis) {
        return userProfileVersionRepository
                .findByUserProfile_IdAndProfileVersionAndUserProfile_UserId(
                        jobAnalysis.getUserProfileId(),
                        jobAnalysis.getUserProfileVersion(),
                        jobAnalysis.getUserId()
                )
                .orElseThrow(JobAnalysisInputNotFoundException::new);
    }

    /**
     * 기능: 상태를 변경할 분석 작업을 식별자로 조회하고 없으면 중단한다.
     * 반환 값: 조회한 분석 작업을 반환한다.
     */
    private JobAnalysis requireJobAnalysis(UUID jobAnalysisId) {
        return jobAnalysisRepository.findById(jobAnalysisId)
                .orElseThrow(JobAnalysisInputNotFoundException::new);
    }

    /**
     * 기능: 비교 처리 수와 실패 여부에 따라 분석을 완료, 부분 완료 또는 실패 상태로 변경한다.
     * 반환 값: 없음.
     */
    private void updateAnalysisFromComparisonOutcome(
            JobAnalysis jobAnalysis,
            int completedPostingCount,
            int totalPostingCount,
            int successfulPythonCallCount,
            JobAnalysisFailureCode failureCode,
            Instant now
    ) {
        if (failureCode == null) {
            jobAnalysis.markComparisonCompleted(
                    completedPostingCount,
                    totalPostingCount,
                    now);
            return;
        }
        if (successfulPythonCallCount > 0) {
            jobAnalysis.markComparisonPartiallyCompleted(
                    completedPostingCount,
                    totalPostingCount,
                    failureCode,
                    now);
            return;
        }
        jobAnalysis.markFailed(now, failureCode);
    }
}
