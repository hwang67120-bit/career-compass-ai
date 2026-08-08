package com.careercompass.jobanalysis.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.service.JobAnalysisService;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.Work24AccessException;
import com.careercompass.jobsearch.exception.Work24AccessFailure;
import com.careercompass.jobsearch.provider.JobPostingProvider;
import com.careercompass.pythonworker.client.PythonJobPostingExtractionClient;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.pythonworker.exception.PythonExtractionFailure;
import com.careercompass.userprofile.domain.UserProfileVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link JobAnalysisWorker}의 오케스트레이션 로직만 검증한다(실제 Provider·Python 호출
 * 없음, Spring 컨텍스트 없음) — 2026-08-04 임시 작업.
 */
class JobAnalysisWorkerTest {

    private static final UUID JOB_ANALYSIS_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");

    private JobAnalysisService jobAnalysisService;
    private JobPostingProvider jobPostingProvider;
    private ObjectProvider<JobPostingProvider> jobPostingProviderObjectProvider;
    private PythonJobPostingExtractionClient pythonJobPostingExtractionClient;
    private JobAnalysisWorker worker;
    private JobAnalysis jobAnalysis;
    private UserProfileVersion profileVersion;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jobAnalysisService = org.mockito.Mockito.mock(JobAnalysisService.class);
        jobPostingProvider = org.mockito.Mockito.mock(JobPostingProvider.class);
        jobPostingProviderObjectProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(jobPostingProviderObjectProvider.getIfAvailable()).thenReturn(jobPostingProvider);
        when(jobPostingProvider.providerName()).thenReturn("WORK24");
        pythonJobPostingExtractionClient =
                org.mockito.Mockito.mock(PythonJobPostingExtractionClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);

        worker = new JobAnalysisWorker(
                jobAnalysisService,
                jobPostingProviderObjectProvider,
                pythonJobPostingExtractionClient,
                clock,
                5
        );

        jobAnalysis = JobAnalysis.createQueued(
                JOB_ANALYSIS_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                List.of(),
                Instant.parse("2026-08-04T00:00:00Z")
        );
        profileVersion = org.mockito.Mockito.mock(UserProfileVersion.class);
        when(profileVersion.getTargetJobTitle()).thenReturn("백엔드 개발자");
        when(jobAnalysisService.loadFixedProfileVersion(jobAnalysis))
                .thenReturn(profileVersion);
    }

    @Test
    void pollAndProcessOne_withNoQueuedAnalysis_doesNothing() {
        when(jobAnalysisService.claimNextQueuedAnalysis()).thenReturn(Optional.empty());

        worker.pollAndProcessOne();

        verify(jobPostingProvider, never()).search(anyString(), any(Integer.class));
        verify(jobAnalysisService, never()).recordExtractedPostings(any(), any());
        verify(jobAnalysisService, never()).markAnalysisFailed(any());
    }

    @Test
    void pollAndProcessOne_withNoProviderConfigured_marksAnalysisFailed() {
        when(jobAnalysisService.claimNextQueuedAnalysis())
                .thenReturn(Optional.of(jobAnalysis));
        when(jobPostingProviderObjectProvider.getIfAvailable()).thenReturn(null);

        worker.pollAndProcessOne();

        verify(jobAnalysisService).markAnalysisFailed(JOB_ANALYSIS_ID);
        verify(jobAnalysisService, never()).loadFixedProfileVersion(any());
        verify(jobAnalysisService, never()).recordExtractedPostings(any(), any());
    }

    @Test
    void pollAndProcessOne_withAllCandidatesSucceeding_recordsAllPostings() {
        when(jobAnalysisService.claimNextQueuedAnalysis())
                .thenReturn(Optional.of(jobAnalysis));
        when(jobPostingProvider.search(eq("백엔드 개발자"), any(Integer.class)))
                .thenReturn(List.of(
                        candidate("posting-1"),
                        candidate("posting-2")
                ));
        when(jobPostingProvider.fetchSourceText(any(JobPostingCandidate.class)))
                .thenReturn("채용공고 본문");
        when(pythonJobPostingExtractionClient.extract(anyString(), anyString(), anyString()))
                .thenReturn(extractionData());

        worker.pollAndProcessOne();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobAnalysisPosting>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobAnalysisService).recordExtractedPostings(eq(JOB_ANALYSIS_ID), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allSatisfy(
                posting -> assertThat(posting.getProvider()).isEqualTo("WORK24"));
        verify(jobAnalysisService, never()).markAnalysisFailed(any());

        ArgumentCaptor<String> jobPostingIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(pythonJobPostingExtractionClient, times(2))
                .extract(jobPostingIdCaptor.capture(), anyString(), anyString());
        List<String> sentJobPostingIds = jobPostingIdCaptor.getAllValues();
        assertThat(sentJobPostingIds).doesNotContain("posting-1", "posting-2");
        assertThat(sentJobPostingIds).allSatisfy(
                jobPostingId -> assertThatCode(() -> UUID.fromString(jobPostingId))
                        .doesNotThrowAnyException());
        assertThat(sentJobPostingIds).doesNotHaveDuplicates();
    }

    @Test
    void pollAndProcessOne_withOneCandidateFailingExtraction_recordsOnlySuccessfulOnes() {
        when(jobAnalysisService.claimNextQueuedAnalysis())
                .thenReturn(Optional.of(jobAnalysis));
        JobPostingCandidate failingCandidate = candidate("posting-1");
        JobPostingCandidate succeedingCandidate = candidate("posting-2");
        when(jobPostingProvider.search(eq("백엔드 개발자"), any(Integer.class)))
                .thenReturn(List.of(failingCandidate, succeedingCandidate));
        when(jobPostingProvider.fetchSourceText(failingCandidate))
                .thenThrow(new Work24AccessException(Work24AccessFailure.SERVICE_UNAVAILABLE));
        when(jobPostingProvider.fetchSourceText(succeedingCandidate))
                .thenReturn("채용공고 본문");
        when(pythonJobPostingExtractionClient.extract(anyString(), anyString(), anyString()))
                .thenReturn(extractionData());

        worker.pollAndProcessOne();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobAnalysisPosting>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobAnalysisService).recordExtractedPostings(eq(JOB_ANALYSIS_ID), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getProviderPostingId()).isEqualTo("posting-2");
    }

    @Test
    void pollAndProcessOne_withAllCandidatesFailingExtraction_marksAnalysisFailed() {
        when(jobAnalysisService.claimNextQueuedAnalysis())
                .thenReturn(Optional.of(jobAnalysis));
        when(jobPostingProvider.search(eq("백엔드 개발자"), any(Integer.class)))
                .thenReturn(List.of(candidate("posting-1")));
        when(jobPostingProvider.fetchSourceText(any(JobPostingCandidate.class)))
                .thenReturn("채용공고 본문");
        when(pythonJobPostingExtractionClient.extract(anyString(), anyString(), anyString()))
                .thenThrow(new PythonExtractionException(PythonExtractionFailure.UNAVAILABLE));

        worker.pollAndProcessOne();

        verify(jobAnalysisService).markAnalysisFailed(JOB_ANALYSIS_ID);
        verify(jobAnalysisService, never()).recordExtractedPostings(any(), any());
    }

    @Test
    void pollAndProcessOne_withSearchThrowing_marksAnalysisFailed() {
        when(jobAnalysisService.claimNextQueuedAnalysis())
                .thenReturn(Optional.of(jobAnalysis));
        when(jobPostingProvider.search(eq("백엔드 개발자"), any(Integer.class)))
                .thenThrow(new Work24AccessException(Work24AccessFailure.SERVICE_UNAVAILABLE));

        worker.pollAndProcessOne();

        verify(jobAnalysisService).markAnalysisFailed(JOB_ANALYSIS_ID);
        verify(pythonJobPostingExtractionClient, never())
                .extract(anyString(), anyString(), anyString());
    }

    private JobPostingCandidate candidate(String providerPostingId) {
        return new JobPostingCandidate(
                providerPostingId,
                "예시회사",
                "백엔드 개발자",
                "서울",
                "https://www.work24.go.kr/wk/a/b/1500/empDetailAuthView.do?wantedAuthNo="
                        + providerPostingId,
                null
        );
    }

    private PythonJobPostingExtractionEnvelope.Data extractionData() {
        return new PythonJobPostingExtractionEnvelope.Data(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "EXTRACTED",
                Map.of(),
                List.of()
        );
    }
}
