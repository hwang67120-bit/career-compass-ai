package com.careercompass.jobanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisStatus;
import com.careercompass.jobanalysis.domain.JobAnalysisStep;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.jobanalysis.service.model.EvidenceComparisonSummary;
import com.careercompass.projectresponsibility.repository.UserProfileProjectResponsibilityRepository;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobAnalysisExecutionServiceTest {

    private static final UUID ANALYSIS_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_PROFILE_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    private JobAnalysisRepository jobAnalysisRepository;
    private JobAnalysisExecutionService service;

    @BeforeEach
    void setUp() {
        jobAnalysisRepository = mock(JobAnalysisRepository.class);
        service = new JobAnalysisExecutionService(
                jobAnalysisRepository,
                mock(JobAnalysisPostingRepository.class),
                mock(UserProfileVersionRepository.class),
                mock(UserProfileProjectResponsibilityRepository.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void claimNextQueuedAnalysis_withQueuedAnalysis_loadsProjectSourcesAndMarksRunning() {
        JobAnalysis jobAnalysis = queuedAnalysis();
        when(jobAnalysisRepository.findNextQueuedForUpdateSkipLocked())
                .thenReturn(Optional.of(jobAnalysis));
        when(jobAnalysisRepository.findByIdWithProjectSources(ANALYSIS_ID))
                .thenReturn(Optional.of(jobAnalysis));

        Optional<JobAnalysis> claimedAnalysis = service.claimNextQueuedAnalysis();

        assertThat(claimedAnalysis).contains(jobAnalysis);
        assertThat(jobAnalysis.getAnalysisStatus()).isEqualTo(JobAnalysisStatus.RUNNING);
        assertThat(jobAnalysis.getUpdatedAt()).isEqualTo(NOW);
        verify(jobAnalysisRepository).findByIdWithProjectSources(ANALYSIS_ID);
        verify(jobAnalysisRepository).save(jobAnalysis);
    }

    @Test
    void claimNextQueuedAnalysis_withoutQueuedAnalysis_returnsEmpty() {
        when(jobAnalysisRepository.findNextQueuedForUpdateSkipLocked())
                .thenReturn(Optional.empty());

        Optional<JobAnalysis> claimedAnalysis = service.claimNextQueuedAnalysis();

        assertThat(claimedAnalysis).isEmpty();
        verify(jobAnalysisRepository, never()).findByIdWithProjectSources(ANALYSIS_ID);
    }

    @Test
    void finishEvidenceComparison_withoutFailure_marksAnalysisCompleted() {
        JobAnalysis jobAnalysis = runningAnalysis();
        when(jobAnalysisRepository.findById(ANALYSIS_ID))
                .thenReturn(Optional.of(jobAnalysis));

        service.finishEvidenceComparison(
                ANALYSIS_ID,
                new EvidenceComparisonSummary(3, 3, 3, null)
        );

        assertThat(jobAnalysis.getAnalysisStatus()).isEqualTo(JobAnalysisStatus.COMPLETED);
        assertThat(jobAnalysis.getCurrentStep()).isEqualTo(JobAnalysisStep.FINISHED);
        assertThat(jobAnalysis.getCompletedUnits()).isEqualTo(3);
        assertThat(jobAnalysis.getTotalUnits()).isEqualTo(3);
        assertThat(jobAnalysis.getFailureCode()).isNull();
        assertThat(jobAnalysis.getUpdatedAt()).isEqualTo(NOW);
        verify(jobAnalysisRepository).save(jobAnalysis);
    }

    @Test
    void finishEvidenceComparison_withSuccessfulCallAndFailure_marksPartiallyCompleted() {
        JobAnalysis jobAnalysis = runningAnalysis();
        when(jobAnalysisRepository.findById(ANALYSIS_ID))
                .thenReturn(Optional.of(jobAnalysis));

        service.finishEvidenceComparison(
                ANALYSIS_ID,
                new EvidenceComparisonSummary(
                        2,
                        3,
                        1,
                        JobAnalysisFailureCode.EVIDENCE_COMPARISON_MODEL_UNAVAILABLE)
        );

        assertThat(jobAnalysis.getAnalysisStatus())
                .isEqualTo(JobAnalysisStatus.PARTIALLY_COMPLETED);
        assertThat(jobAnalysis.getCurrentStep()).isEqualTo(JobAnalysisStep.FINISHED);
        assertThat(jobAnalysis.getCompletedUnits()).isEqualTo(2);
        assertThat(jobAnalysis.getTotalUnits()).isEqualTo(3);
        assertThat(jobAnalysis.getFailureCode())
                .isEqualTo(JobAnalysisFailureCode.EVIDENCE_COMPARISON_MODEL_UNAVAILABLE);
        assertThat(jobAnalysis.getUpdatedAt()).isEqualTo(NOW);
        verify(jobAnalysisRepository).save(jobAnalysis);
    }

    @Test
    void finishEvidenceComparison_withoutSuccessfulCall_marksAnalysisFailed() {
        JobAnalysis jobAnalysis = runningAnalysis();
        when(jobAnalysisRepository.findById(ANALYSIS_ID))
                .thenReturn(Optional.of(jobAnalysis));

        service.finishEvidenceComparison(
                ANALYSIS_ID,
                new EvidenceComparisonSummary(
                        0,
                        3,
                        0,
                        JobAnalysisFailureCode.EVIDENCE_COMPARISON_INVALID_RESPONSE)
        );

        assertThat(jobAnalysis.getAnalysisStatus()).isEqualTo(JobAnalysisStatus.FAILED);
        assertThat(jobAnalysis.getCurrentStep())
                .isEqualTo(JobAnalysisStep.FINALIZING_RESULT);
        assertThat(jobAnalysis.getFailureCode())
                .isEqualTo(JobAnalysisFailureCode.EVIDENCE_COMPARISON_INVALID_RESPONSE);
        assertThat(jobAnalysis.getUpdatedAt()).isEqualTo(NOW);
        verify(jobAnalysisRepository).save(jobAnalysis);
    }

    private JobAnalysis queuedAnalysis() {
        return JobAnalysis.createQueued(
                ANALYSIS_ID,
                USER_ID,
                USER_PROFILE_ID,
                1,
                List.of(),
                NOW.minusSeconds(60)
        );
    }

    private JobAnalysis runningAnalysis() {
        JobAnalysis jobAnalysis = JobAnalysis.createQueued(
                ANALYSIS_ID,
                USER_ID,
                USER_PROFILE_ID,
                1,
                List.of(),
                NOW.minusSeconds(60)
        );
        jobAnalysis.markRunning(NOW.minusSeconds(30));
        return jobAnalysis;
    }
}
