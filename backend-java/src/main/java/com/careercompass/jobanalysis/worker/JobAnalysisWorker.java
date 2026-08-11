package com.careercompass.jobanalysis.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.domain.JobAnalysisStep;
import com.careercompass.jobanalysis.service.JobAnalysisService;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.JobPostingProviderNotConfiguredException;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessException;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessFailure;
import com.careercompass.jobsearch.provider.JobPostingProvider;
import com.careercompass.pythonworker.client.PythonJobPostingExtractionClient;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기 중인 {@link JobAnalysis}를 하나씩 선점해 실제로 진행시킨다.
 *
 * 현재는 검색(공공취업정보 API)과 추출(Python) 두 단계만 실제로 연결한다.
 * 조건판정·유사도 비교(COMPARING_EVIDENCE 이후)는 Python 쪽에
 * 아직 API가 없어서 이번 범위에 없다(docs/current-work.md, 계획 파일 참고) — 코덱스가
 * 돌아오면 이어서 확장해야 한다.
 *
 * test 프로필에서는 빈을 만들지 않는다 — 테스트가 끝나 Testcontainers DB가 종료된 뒤에도
 * 스케줄러가 계속 폴링해 연결 오류·타임아웃을 일으키는 문제를 막는다.
 */
@Component
@Profile("!test")
public class JobAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisWorker.class);

    private final JobAnalysisService jobAnalysisService;
    private final ObjectProvider<JobPostingProvider> jobPostingProvider;
    private final PythonJobPostingExtractionClient pythonJobPostingExtractionClient;
    private final Clock clock;
    private final int searchResultLimit;

    /**
     * 스프링이 자동 구성하는 Jackson(tools.jackson, 3.x)에 의존하지 않고 직접
     * 만든다 — 이 클라이언트가 쓰는 com.fasterxml.jackson(2.x)과 다른 라이브러리라
     * 빈 주입으로는 타입이 안 맞는다(확인 필요, 계획 파일 참고).
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobAnalysisWorker(
            JobAnalysisService jobAnalysisService,
            ObjectProvider<JobPostingProvider> jobPostingProvider,
            PythonJobPostingExtractionClient pythonJobPostingExtractionClient,
            Clock clock,
            @Value("${job-analysis.worker.search-result-limit}") int searchResultLimit
    ) {
        this.jobAnalysisService = jobAnalysisService;
        this.jobPostingProvider = jobPostingProvider;
        this.pythonJobPostingExtractionClient = pythonJobPostingExtractionClient;
        this.clock = clock;
        this.searchResultLimit = searchResultLimit;
    }

    /**
     * 기능: 매 tick마다 대기 중인 작업을 하나만 선점해 처리한다. 외부 호출(공공취업정보·Python)은
     * 선점 트랜잭션이 끝난 뒤 트랜잭션 밖에서 실행한다(backend-job-processing-and-sse.md 확정 사항).
     */
    @Scheduled(fixedDelayString = "${job-analysis.worker.fixed-delay-ms}")
    public void pollAndProcessOne() {
        Optional<JobAnalysis> claimed = jobAnalysisService.claimNextQueuedAnalysis();
        if (claimed.isEmpty()) {
            return;
        }
        processClaimedAnalysis(claimed.get());
    }

    private void processClaimedAnalysis(JobAnalysis jobAnalysis) {
        UUID jobAnalysisId = jobAnalysis.getId();
        try {
            JobPostingProvider provider = jobPostingProvider.getIfAvailable();
            if (provider == null) {
                throw new JobPostingProviderNotConfiguredException();
            }

            UserProfileVersion profileVersion =
                    jobAnalysisService.loadFixedProfileVersion(jobAnalysis);
            String keyword = profileVersion.getTargetJobTitle();

            jobAnalysisService.advanceStep(jobAnalysisId, JobAnalysisStep.SEARCHING_JOB_POSTINGS);
            List<JobPostingCandidate> candidates = provider.search(keyword, searchResultLimit);

            if (candidates.isEmpty()) {
                jobAnalysisService.recordEmptySearchResult(jobAnalysisId);
                return;
            }

            jobAnalysisService.advanceStep(jobAnalysisId, JobAnalysisStep.EXTRACTING_JOB_POSTINGS);
            List<JobAnalysisPosting> savedPostings =
                    extractCandidates(jobAnalysisId, provider, candidates);

            if (savedPostings.isEmpty()) {
                jobAnalysisService.markAnalysisFailed(
                        jobAnalysisId, JobAnalysisFailureCode.ALL_EXTRACTIONS_FAILED);
            } else {
                jobAnalysisService.recordExtractionCompletedWithoutComparison(
                        jobAnalysisId, savedPostings);
            }
        } catch (JobPostingProviderNotConfiguredException exception) {
            logProcessingFailure(jobAnalysisId, exception);
            jobAnalysisService.markAnalysisFailed(
                    jobAnalysisId, JobAnalysisFailureCode.JOB_POSTING_PROVIDER_NOT_CONFIGURED);
        } catch (PublicEmploymentAccessException exception) {
            logProcessingFailure(jobAnalysisId, exception);
            JobAnalysisFailureCode failureCode =
                    exception.getFailure() == PublicEmploymentAccessFailure.INVALID_RESPONSE
                    ? JobAnalysisFailureCode.DEPENDENCY_INVALID_RESPONSE
                    : JobAnalysisFailureCode.DEPENDENCY_UNAVAILABLE;
            jobAnalysisService.markAnalysisFailed(jobAnalysisId, failureCode);
        } catch (RuntimeException exception) {
            logProcessingFailure(jobAnalysisId, exception);
            jobAnalysisService.markAnalysisFailed(
                    jobAnalysisId, JobAnalysisFailureCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void logProcessingFailure(UUID jobAnalysisId, RuntimeException exception) {
        log.warn(
                "job_analysis_processing_failed jobAnalysisId={} failure={}",
                jobAnalysisId,
                exception.getClass().getSimpleName(),
                exception
        );
    }

    private List<JobAnalysisPosting> extractCandidates(
            UUID jobAnalysisId,
            JobPostingProvider provider,
            List<JobPostingCandidate> candidates
    ) {
        Instant now = Instant.now(clock);
        List<JobAnalysisPosting> savedPostings = new ArrayList<>();
        for (JobPostingCandidate candidate : candidates) {
            try {
                savedPostings.add(
                        extractOneCandidate(jobAnalysisId, provider, candidate, now));
            } catch (PythonExtractionException exception) {
                log.warn(
                        "job_analysis_posting_extraction_failed jobAnalysisId={} "
                                + "providerPostingId={} failure={} responseViolation={}",
                        jobAnalysisId,
                        candidate.providerPostingId(),
                        exception.getFailure(),
                        exception.getResponseViolation(),
                        exception
                );
            } catch (PublicEmploymentAccessException | JsonProcessingException exception) {
                log.warn(
                        "job_analysis_posting_extraction_failed jobAnalysisId={} "
                                + "providerPostingId={} failure={} responseViolation=none",
                        jobAnalysisId,
                        candidate.providerPostingId(),
                        exception.getClass().getSimpleName(),
                        exception
                );
            }
        }
        return savedPostings;
    }

    private JobAnalysisPosting extractOneCandidate(
            UUID jobAnalysisId,
            JobPostingProvider provider,
            JobPostingCandidate candidate,
            Instant now
    ) throws JsonProcessingException {
        String sourceText = provider.fetchSourceText(candidate);
        UUID jobPostingId = UUID.randomUUID();
        UUID extractionTaskId = UUID.randomUUID();
        PythonJobPostingExtractionEnvelope.Data data = pythonJobPostingExtractionClient.extract(
                jobPostingId.toString(),
                extractionTaskId.toString(),
                sourceText
        );
        return JobAnalysisPosting.create(
                UUID.randomUUID(),
                jobAnalysisId,
                candidate.providerPostingId(),
                provider.providerName(),
                jobPostingId,
                extractionTaskId,
                candidate.companyName(),
                candidate.originalJobTitle(),
                candidate.sourceUrl(),
                objectMapper.writeValueAsString(data.extraction()),
                objectMapper.writeValueAsString(data.modelExecutions()),
                now
        );
    }
}
