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
import com.careercompass.jobanalysis.service.JobEvidenceComparisonService;
import com.careercompass.jobanalysis.service.JobAnalysisExecutionService;
import com.careercompass.jobanalysis.service.JobAnalysisJsonCodec;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.JobPostingProviderNotConfiguredException;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessException;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessFailure;
import com.careercompass.jobsearch.provider.JobPostingProvider;
import com.careercompass.projectresponsibility.service.ProjectResponsibilityExtractionOutcome;
import com.careercompass.projectresponsibility.service.ProjectResponsibilityExtractionService;
import com.careercompass.projectsource.exception.RepositorySnapshotException;
import com.careercompass.projectsource.exception.RepositorySnapshotFailure;
import com.careercompass.pythonworker.client.PythonJobPostingExtractionClient;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionException;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionFailure;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 * 공개 저장소 프로젝트 근거 후보 추출과 공고 검색·구조화 추출을 연결한다.
 * 사용자 확인 뒤 조건 판정·의미 비교 단계는 별도 구현 단위에서 이어진다.
 *
 * test 프로필에서는 빈을 만들지 않는다 — 테스트가 끝나 Testcontainers DB가 종료된 뒤에도
 * 스케줄러가 계속 폴링해 연결 오류·타임아웃을 일으키는 문제를 막는다.
 */
@Component
@Profile("!test")
public class JobAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisWorker.class);

    private final JobAnalysisExecutionService jobAnalysisExecutionService;
    private final JobEvidenceComparisonService jobEvidenceComparisonService;
    private final ObjectProvider<JobPostingProvider> jobPostingProvider;
    private final PythonJobPostingExtractionClient pythonJobPostingExtractionClient;
    private final ProjectResponsibilityExtractionService
            projectResponsibilityExtractionService;
    private final JobAnalysisJsonCodec jobAnalysisJsonCodec;
    private final Clock clock;
    private final int searchResultLimit;

    public JobAnalysisWorker(
            JobAnalysisExecutionService jobAnalysisExecutionService,
            JobEvidenceComparisonService jobEvidenceComparisonService,
            ObjectProvider<JobPostingProvider> jobPostingProvider,
            PythonJobPostingExtractionClient pythonJobPostingExtractionClient,
            ProjectResponsibilityExtractionService projectResponsibilityExtractionService,
            JobAnalysisJsonCodec jobAnalysisJsonCodec,
            Clock clock,
            @Value("${job-analysis.worker.search-result-limit}") int searchResultLimit
    ) {
        this.jobAnalysisExecutionService = jobAnalysisExecutionService;
        this.jobEvidenceComparisonService = jobEvidenceComparisonService;
        this.jobPostingProvider = jobPostingProvider;
        this.pythonJobPostingExtractionClient = pythonJobPostingExtractionClient;
        this.projectResponsibilityExtractionService =
                projectResponsibilityExtractionService;
        this.jobAnalysisJsonCodec = jobAnalysisJsonCodec;
        this.clock = clock;
        this.searchResultLimit = searchResultLimit;
    }

    /**
     * 기능: 매 tick마다 대기 중인 작업을 하나만 선점해 처리한다. 외부 호출(공공취업정보·Python)은
     * 선점 트랜잭션이 끝난 뒤 트랜잭션 밖에서 실행한다(backend-job-processing-and-sse.md 확정 사항).
     */
    @Scheduled(fixedDelayString = "${job-analysis.worker.fixed-delay-ms}")
    public void pollAndExecuteOneAnalysis() {
        Optional<JobAnalysis> claimed = jobAnalysisExecutionService.claimNextQueuedAnalysis();
        if (claimed.isEmpty()) {
            return;
        }
        executeClaimedAnalysis(claimed.get());
    }

    private void executeClaimedAnalysis(JobAnalysis jobAnalysis) {
        UUID jobAnalysisId = jobAnalysis.getId();
        try {
            executeCurrentAnalysisStep(jobAnalysis);
        } catch (JobPostingProviderNotConfiguredException exception) {
            recordAnalysisFailure(
                    jobAnalysisId,
                    JobAnalysisFailureCode.JOB_POSTING_PROVIDER_NOT_CONFIGURED,
                    exception);
        } catch (RepositorySnapshotException exception) {
            recordAnalysisFailure(
                    jobAnalysisId,
                    mapRepositorySnapshotFailure(exception),
                    exception);
        } catch (PythonProjectResponsibilityExtractionException exception) {
            recordAnalysisFailure(
                    jobAnalysisId,
                    mapProjectResponsibilityExtractionFailure(exception),
                    exception);
        } catch (PublicEmploymentAccessException exception) {
            recordAnalysisFailure(
                    jobAnalysisId,
                    mapPublicEmploymentAccessFailure(exception),
                    exception);
        } catch (RuntimeException exception) {
            recordAnalysisFailure(
                    jobAnalysisId,
                    JobAnalysisFailureCode.DEPENDENCY_UNAVAILABLE,
                    exception);
        }
    }

    private void executeCurrentAnalysisStep(JobAnalysis jobAnalysis) {
        if (jobAnalysis.getCurrentStep() == JobAnalysisStep.COMPARING_EVIDENCE) {
            jobEvidenceComparisonService.compare(jobAnalysis);
            return;
        }
        extractProjectAndJobPostingEvidence(jobAnalysis);
    }

    private void extractProjectAndJobPostingEvidence(JobAnalysis jobAnalysis) {
        UUID jobAnalysisId = jobAnalysis.getId();
        JobPostingProvider provider = requireJobPostingProvider();
        UserProfileVersion profileVersion =
                jobAnalysisExecutionService.loadFixedProfileVersion(jobAnalysis);

        ProjectResponsibilityExtractionOutcome responsibilityOutcome =
                extractProjectResponsibilities(jobAnalysis, profileVersion);
        List<JobPostingCandidate> candidates =
                searchJobPostings(
                        jobAnalysisId,
                        provider,
                        profileVersion.getTargetJobTitle());
        if (candidates.isEmpty()) {
            jobAnalysisExecutionService.recordEmptySearchResult(jobAnalysisId);
            return;
        }

        List<JobAnalysisPosting> savedPostings =
                extractJobPostings(jobAnalysisId, provider, candidates);
        continueAfterEvidenceExtraction(
                jobAnalysis,
                responsibilityOutcome,
                savedPostings);
    }

    private JobPostingProvider requireJobPostingProvider() {
        JobPostingProvider provider = jobPostingProvider.getIfAvailable();
        if (provider == null) {
            throw new JobPostingProviderNotConfiguredException();
        }
        return provider;
    }

    private ProjectResponsibilityExtractionOutcome extractProjectResponsibilities(
            JobAnalysis jobAnalysis,
            UserProfileVersion profileVersion
    ) {
        jobAnalysisExecutionService.advanceStep(
                jobAnalysis.getId(),
                JobAnalysisStep.ANALYZING_REPOSITORIES);
        return projectResponsibilityExtractionService.extract(
                jobAnalysis,
                profileVersion);
    }

    private List<JobPostingCandidate> searchJobPostings(
            UUID jobAnalysisId,
            JobPostingProvider provider,
            String targetJobTitle
    ) {
        jobAnalysisExecutionService.advanceStep(
                jobAnalysisId,
                JobAnalysisStep.SEARCHING_JOB_POSTINGS);
        return provider.search(targetJobTitle, searchResultLimit);
    }

    private void continueAfterEvidenceExtraction(
            JobAnalysis jobAnalysis,
            ProjectResponsibilityExtractionOutcome responsibilityOutcome,
            List<JobAnalysisPosting> savedPostings
    ) {
        UUID jobAnalysisId = jobAnalysis.getId();
        if (savedPostings.isEmpty()) {
            jobAnalysisExecutionService.markAnalysisFailed(
                    jobAnalysisId,
                    JobAnalysisFailureCode.ALL_EXTRACTIONS_FAILED);
            return;
        }
        if (responsibilityOutcome.requiresUserConfirmation()) {
            jobAnalysisExecutionService.recordExtractionAwaitingUserConfirmation(
                    jobAnalysisId,
                    savedPostings);
            return;
        }

        jobAnalysisExecutionService.recordExtractionReadyForComparison(
                jobAnalysisId,
                savedPostings);
        jobEvidenceComparisonService.compare(jobAnalysis);
    }

    private JobAnalysisFailureCode mapRepositorySnapshotFailure(
            RepositorySnapshotException exception
    ) {
        return exception.getFailure() == RepositorySnapshotFailure.TREE_TRUNCATED
                ? JobAnalysisFailureCode.PROJECT_REPOSITORY_TREE_TRUNCATED
                : JobAnalysisFailureCode.PROJECT_REPOSITORY_SNAPSHOT_INVALID;
    }

    private JobAnalysisFailureCode mapProjectResponsibilityExtractionFailure(
            PythonProjectResponsibilityExtractionException exception
    ) {
        return exception.getFailure()
                == PythonProjectResponsibilityExtractionFailure.MODEL_UNAVAILABLE
                ? JobAnalysisFailureCode.PROJECT_RESPONSIBILITY_MODEL_UNAVAILABLE
                : JobAnalysisFailureCode.PROJECT_RESPONSIBILITY_EXTRACTION_INVALID_RESPONSE;
    }

    private JobAnalysisFailureCode mapPublicEmploymentAccessFailure(
            PublicEmploymentAccessException exception
    ) {
        return exception.getFailure() == PublicEmploymentAccessFailure.INVALID_RESPONSE
                ? JobAnalysisFailureCode.DEPENDENCY_INVALID_RESPONSE
                : JobAnalysisFailureCode.DEPENDENCY_UNAVAILABLE;
    }

    private void recordAnalysisFailure(
            UUID jobAnalysisId,
            JobAnalysisFailureCode failureCode,
            RuntimeException exception
    ) {
        logAnalysisFailure(jobAnalysisId, exception);
        jobAnalysisExecutionService.markAnalysisFailed(jobAnalysisId, failureCode);
    }

    private void logAnalysisFailure(UUID jobAnalysisId, RuntimeException exception) {
        log.warn(
                "job_analysis_processing_failed jobAnalysisId={} failure={}",
                jobAnalysisId,
                exception.getClass().getSimpleName(),
                exception
        );
    }

    private List<JobAnalysisPosting> extractJobPostings(
            UUID jobAnalysisId,
            JobPostingProvider provider,
            List<JobPostingCandidate> candidates
    ) {
        Instant now = Instant.now(clock);
        List<JobAnalysisPosting> savedPostings = new ArrayList<>();
        for (JobPostingCandidate candidate : candidates) {
            try {
                savedPostings.add(
                        extractJobPosting(jobAnalysisId, provider, candidate, now));
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

    private JobAnalysisPosting extractJobPosting(
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
                jobAnalysisJsonCodec.serialize(data.extraction()),
                jobAnalysisJsonCodec.serialize(data.modelExecutions()),
                now
        );
    }
}
