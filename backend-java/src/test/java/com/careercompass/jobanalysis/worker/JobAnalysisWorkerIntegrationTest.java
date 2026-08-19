package com.careercompass.jobanalysis.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careercompass.jobanalysis.service.JobEvidenceComparisonService;
import com.careercompass.jobanalysis.service.JobAnalysisService;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.provider.JobPostingProvider;
import com.careercompass.projectresponsibility.service.ProjectResponsibilityExtractionOutcome;
import com.careercompass.projectresponsibility.service.ProjectResponsibilityExtractionService;
import com.careercompass.pythonworker.client.PythonJobPostingExtractionClient;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.pythonworker.exception.PythonExtractionFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * JobAnalysisWorker의 상태 전이를 실제 PostgreSQL로 검증한다(PR #48 코덱스 확인 사항).
 * JobAnalysisWorker는 test 프로필에서 스프링 빈으로 만들어지지 않으므로(스케줄러가
 * Testcontainers 종료 뒤에도 폴링하는 문제 방지), 여기서는 실제 JobAnalysisService
 * 빈을 그대로 쓰고 JobPostingProvider·PythonJobPostingExtractionClient만 목으로 준비해
 * 워커를 직접 생성한다.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "github.api.connect-timeout=3s",
        "github.api.read-timeout=8s",
        "python.worker.internal-token=integration-test-token",
        "test.user-id=30000000-0000-0000-0000-000000000003"
})
class JobAnalysisWorkerIntegrationTest {

    private static final String PATH = "/api/v1/job-analyses";
    private static final UUID TEST_USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("80000000-0000-0000-0000-000000000003");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobAnalysisService jobAnalysisService;

    private JobEvidenceComparisonService jobEvidenceComparisonService;
    private JobPostingProvider jobPostingProvider;
    private PythonJobPostingExtractionClient pythonJobPostingExtractionClient;
    private ProjectResponsibilityExtractionService projectResponsibilityExtractionService;
    private JobAnalysisWorker worker;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM project_source WHERE id = ?", PROJECT_SOURCE_ID);
        insertUser(TEST_USER_ID);
        insertProjectSource(PROJECT_SOURCE_ID, TEST_USER_ID);

        jobEvidenceComparisonService = mock(JobEvidenceComparisonService.class);
        jobPostingProvider = mock(JobPostingProvider.class);
        when(jobPostingProvider.providerName()).thenReturn("PUBLIC_EMPLOYMENT");
        pythonJobPostingExtractionClient = mock(PythonJobPostingExtractionClient.class);
        projectResponsibilityExtractionService =
                mock(ProjectResponsibilityExtractionService.class);
        when(projectResponsibilityExtractionService.extract(any(), any()))
                .thenReturn(new ProjectResponsibilityExtractionOutcome(false, false));

        ObjectProvider<JobPostingProvider> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(jobPostingProvider);

        worker = new JobAnalysisWorker(
                jobAnalysisService,
                jobEvidenceComparisonService,
                objectProvider,
                pythonJobPostingExtractionClient,
                projectResponsibilityExtractionService,
                Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC),
                5
        );
    }

    @Test
    void pollAndProcessOne_withExtractionSucceeding_completesComparison()
            throws Exception {
        UUID jobAnalysisId = queueAnalysis();
        when(jobPostingProvider.search(anyString(), anyInt())).thenReturn(List.of(
                new JobPostingCandidate(
                        "posting-1", "회사", "백엔드 개발자", "서울", "https://example.invalid", null)
        ));
        when(jobPostingProvider.fetchSourceText(any())).thenReturn("채용공고 본문");
        when(pythonJobPostingExtractionClient.extract(anyString(), anyString(), anyString()))
                .thenReturn(new PythonJobPostingExtractionEnvelope.Data(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "EXTRACTED",
                        Map.of(),
                        List.of()
                ));

        worker.pollAndProcessOne();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT analysis_status, current_step, failure_code FROM job_analysis WHERE id = ?",
                jobAnalysisId
        );
        assertThat(row.get("analysis_status")).isEqualTo("COMPLETED");
        assertThat(row.get("current_step")).isEqualTo("FINISHED");
        assertThat(row.get("failure_code")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_analysis_posting WHERE job_analysis_id = ?",
                Integer.class,
                jobAnalysisId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT comparison FROM job_analysis_posting WHERE job_analysis_id = ?",
                String.class,
                jobAnalysisId
        )).isNotNull();
    }

    @Test
    void pollAndProcessOne_withEmptySearchResult_marksCompletedAndFinished() throws Exception {
        UUID jobAnalysisId = queueAnalysis();
        when(jobPostingProvider.search(anyString(), anyInt())).thenReturn(List.of());

        worker.pollAndProcessOne();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT analysis_status, current_step, failure_code FROM job_analysis WHERE id = ?",
                jobAnalysisId
        );
        assertThat(row.get("analysis_status")).isEqualTo("COMPLETED");
        assertThat(row.get("current_step")).isEqualTo("FINISHED");
        assertThat(row.get("failure_code")).isNull();
    }

    @Test
    void pollAndProcessOne_withAllExtractionsFailing_marksAllExtractionsFailed() throws Exception {
        UUID jobAnalysisId = queueAnalysis();
        when(jobPostingProvider.search(anyString(), anyInt())).thenReturn(List.of(
                new JobPostingCandidate(
                        "posting-1", "회사", "백엔드 개발자", "서울", "https://example.invalid", null)
        ));
        when(jobPostingProvider.fetchSourceText(any())).thenReturn("채용공고 본문");
        when(pythonJobPostingExtractionClient.extract(anyString(), anyString(), anyString()))
                .thenThrow(new PythonExtractionException(PythonExtractionFailure.UNAVAILABLE));

        worker.pollAndProcessOne();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT analysis_status, failure_code FROM job_analysis WHERE id = ?",
                jobAnalysisId
        );
        assertThat(row.get("analysis_status")).isEqualTo("FAILED");
        assertThat(row.get("failure_code")).isEqualTo("ALL_EXTRACTIONS_FAILED");
    }

    @SuppressWarnings("unchecked")
    @Test
    void pollAndProcessOne_withoutConfiguredProvider_marksProviderNotConfigured() throws Exception {
        UUID jobAnalysisId = queueAnalysis();
        ObjectProvider<JobPostingProvider> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        JobAnalysisWorker workerWithoutProvider = new JobAnalysisWorker(
                jobAnalysisService,
                jobEvidenceComparisonService,
                emptyProvider,
                pythonJobPostingExtractionClient,
                projectResponsibilityExtractionService,
                Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC),
                5
        );

        workerWithoutProvider.pollAndProcessOne();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT analysis_status, failure_code FROM job_analysis WHERE id = ?",
                jobAnalysisId
        );
        assertThat(row.get("analysis_status")).isEqualTo("FAILED");
        assertThat(row.get("failure_code")).isEqualTo("JOB_POSTING_PROVIDER_NOT_CONFIGURED");
    }

    private UUID queueAnalysis() throws Exception {
        UUID userProfileId = saveProfile();
        MvcResult result = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userProfileId": "%s",
                                  "userProfileVersion": 1,
                                  "projectSourceIds": ["%s"]
                                }
                                """.formatted(userProfileId, PROJECT_SOURCE_ID)))
                .andExpect(status().isAccepted())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return UUID.fromString(location.substring((PATH + "/").length()));
    }

    private UUID saveProfile() throws Exception {
        mockMvc.perform(put("/api/v1/user-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetJobTitle": "백엔드 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user_profile WHERE user_id = ?",
                UUID.class,
                TEST_USER_ID
        );
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO user_account (id, user_status, created_at) VALUES (?, 'ACTIVE', ?)",
                userId,
                Timestamp.from(Instant.parse("2026-08-04T00:00:00Z"))
        );
    }

    private void insertProjectSource(UUID projectSourceId, UUID userId) {
        jdbcTemplate.update(
                """
                INSERT INTO project_source (
                    id, user_id, repository_url, repository_full_name,
                    default_branch, commit_sha, project_source_status, created_at
                )
                VALUES (?, ?, ?, ?, 'main', ?, 'REGISTERED', ?)
                """,
                projectSourceId,
                userId,
                "https://github.com/current/repo",
                "current/repo",
                "0123456789012345678901234567890123456789",
                Timestamp.from(Instant.parse("2026-08-04T00:00:00Z"))
        );
    }
}
