package com.careercompass.jobanalysis.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.service.JobAnalysisService;
import com.careercompass.jobsearch.client.Work24JobDetailFetcher;
import com.careercompass.jobsearch.client.Work24JobSearchClient;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.Work24AccessException;
import com.careercompass.pythonworker.client.PythonJobPostingExtractionClient;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기 중인 {@link JobAnalysis}를 하나씩 선점해 실제로 진행시킨다.
 *
 * 2026-08-04 임시 작업(코덱스 사용량 한도 공백기 대응) — 지금은 검색(Work24)과 추출(Python)
 * 두 단계만 실제로 연결한다. 조건판정·유사도 비교(COMPARING_EVIDENCE 이후)는 Python 쪽에
 * 아직 API가 없어서 이번 범위에 없다(docs/current-work.md, 계획 파일 참고) — 코덱스가
 * 돌아오면 이어서 확장해야 한다.
 */
@Component
public class JobAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisWorker.class);
    private static final int SEARCH_RESULT_LIMIT = 5;

    private final JobAnalysisService jobAnalysisService;
    private final Work24JobSearchClient work24JobSearchClient;
    private final Work24JobDetailFetcher work24JobDetailFetcher;
    private final PythonJobPostingExtractionClient pythonJobPostingExtractionClient;
    private final Clock clock;

    /**
     * 스프링이 자동 구성하는 Jackson(tools.jackson, 3.x)에 의존하지 않고 직접
     * 만든다 — 이 클라이언트가 쓰는 com.fasterxml.jackson(2.x)과 다른 라이브러리라
     * 빈 주입으로는 타입이 안 맞는다(확인 필요, 계획 파일 참고).
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobAnalysisWorker(
            JobAnalysisService jobAnalysisService,
            Work24JobSearchClient work24JobSearchClient,
            Work24JobDetailFetcher work24JobDetailFetcher,
            PythonJobPostingExtractionClient pythonJobPostingExtractionClient,
            Clock clock
    ) {
        this.jobAnalysisService = jobAnalysisService;
        this.work24JobSearchClient = work24JobSearchClient;
        this.work24JobDetailFetcher = work24JobDetailFetcher;
        this.pythonJobPostingExtractionClient = pythonJobPostingExtractionClient;
        this.clock = clock;
    }

    /**
     * 기능: 매 tick마다 대기 중인 작업을 하나만 선점해 처리한다. 외부 호출(Work24·Python)은
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
            UserProfileVersion profileVersion =
                    jobAnalysisService.loadFixedProfileVersion(jobAnalysis);
            String keyword = profileVersion.getTargetJobTitle();

            List<JobPostingCandidate> candidates =
                    work24JobSearchClient.search(keyword, SEARCH_RESULT_LIMIT);
            List<JobAnalysisPosting> savedPostings = extractCandidates(jobAnalysisId, candidates);

            if (savedPostings.isEmpty()) {
                jobAnalysisService.markAnalysisFailed(jobAnalysisId);
            } else {
                jobAnalysisService.recordExtractedPostings(jobAnalysisId, savedPostings);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "job_analysis_processing_failed jobAnalysisId={} failure={}",
                    jobAnalysisId,
                    exception.getClass().getSimpleName(),
                    exception
            );
            jobAnalysisService.markAnalysisFailed(jobAnalysisId);
        }
    }

    private List<JobAnalysisPosting> extractCandidates(
            UUID jobAnalysisId,
            List<JobPostingCandidate> candidates
    ) {
        Instant now = Instant.now(clock);
        List<JobAnalysisPosting> savedPostings = new ArrayList<>();
        for (JobPostingCandidate candidate : candidates) {
            try {
                savedPostings.add(
                        extractOneCandidate(jobAnalysisId, candidate, now));
            } catch (Work24AccessException | PythonExtractionException
                    | JsonProcessingException exception) {
                log.warn(
                        "job_analysis_posting_extraction_failed jobAnalysisId={} "
                                + "providerPostingId={} failure={}",
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
            JobPostingCandidate candidate,
            Instant now
    ) throws JsonProcessingException {
        String sourceText = work24JobDetailFetcher.fetchSourceText(candidate.providerPostingId());
        PythonJobPostingExtractionEnvelope.Data data = pythonJobPostingExtractionClient.extract(
                candidate.providerPostingId(),
                UUID.randomUUID().toString(),
                sourceText
        );
        return JobAnalysisPosting.create(
                UUID.randomUUID(),
                jobAnalysisId,
                candidate.providerPostingId(),
                candidate.companyName(),
                candidate.originalJobTitle(),
                candidate.sourceUrl(),
                objectMapper.writeValueAsString(data.extraction()),
                objectMapper.writeValueAsString(data.modelExecutions()),
                now
        );
    }
}
