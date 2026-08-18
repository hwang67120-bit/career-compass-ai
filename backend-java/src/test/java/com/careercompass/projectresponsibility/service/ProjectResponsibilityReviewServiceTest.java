package com.careercompass.projectresponsibility.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisStatus;
import com.careercompass.jobanalysis.repository.JobAnalysisRepository;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityCandidate;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityCandidateStatus;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityExtractionTask;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityReviewStatus;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityDecisionRequest;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityDecisionResponse;
import com.careercompass.projectresponsibility.exception.ProjectResponsibilityConflictException;
import com.careercompass.projectresponsibility.exception.ProjectResponsibilityStateConflictException;
import com.careercompass.projectresponsibility.repository.ProjectResponsibilityCandidateRepository;
import com.careercompass.projectresponsibility.repository.ProjectResponsibilityExtractionTaskRepository;
import com.careercompass.projectresponsibility.repository.ProjectTechnologyFindingRepository;
import com.careercompass.projectresponsibility.repository.ProjectTechnologySuggestionRepository;
import com.careercompass.projectresponsibility.repository.UserProfileProjectResponsibilityRepository;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import com.careercompass.userprofile.domain.UserProfile;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.careercompass.userprofile.repository.UserProfileRepository;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectResponsibilityReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ANALYSIS_ID = UUID.randomUUID();

    @Mock private ProjectResponsibilityExtractionTaskRepository taskRepository;
    @Mock private ProjectResponsibilityCandidateRepository candidateRepository;
    @Mock private ProjectTechnologyFindingRepository findingRepository;
    @Mock private ProjectTechnologySuggestionRepository suggestionRepository;
    @Mock private UserProfileProjectResponsibilityRepository responsibilityRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileVersionRepository profileVersionRepository;
    @Mock private JobAnalysisRepository jobAnalysisRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private TechnologyTagRepository technologyTagRepository;

    private ProjectResponsibilityReviewService service;

    @BeforeEach
    void setUp() {
        service = new ProjectResponsibilityReviewService(
                taskRepository, candidateRepository, findingRepository,
                suggestionRepository, responsibilityRepository,
                userProfileRepository, profileVersionRepository,
                jobAnalysisRepository, currentUserProvider,
                technologyTagRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void decide_lastConfirmedCandidate_createsOneProfileVersionAndQueuesAnalysis() {
        TestFixture fixture = fixture();
        when(taskRepository.findByCandidateIdForUpdate(fixture.candidate().getId()))
                .thenReturn(Optional.of(fixture.task()));
        when(candidateRepository.findByIdAndExtractionTask_Id(
                fixture.candidate().getId(), fixture.task().getId()))
                .thenReturn(Optional.of(fixture.candidate()));
        when(candidateRepository.existsByExtractionTask_IdAndCandidateStatus(
                fixture.task().getId(), ProjectResponsibilityCandidateStatus.UNCONFIRMED))
                .thenReturn(false);
        when(candidateRepository.findAllByExtractionTask_IdAndCandidateStatusOrderByCreatedAtAsc(
                fixture.task().getId(), ProjectResponsibilityCandidateStatus.CONFIRMED))
                .thenReturn(List.of(fixture.candidate()));
        when(userProfileRepository.findByIdForUpdate(fixture.profile().getId()))
                .thenReturn(Optional.of(fixture.profile()));
        when(responsibilityRepository.findAllByUserProfileVersion_IdOrderByDisplayOrderAsc(any()))
                .thenReturn(List.of());
        when(jobAnalysisRepository.findByIdForUpdate(ANALYSIS_ID))
                .thenReturn(Optional.of(fixture.analysis()));

        ProjectResponsibilityDecisionResponse response = service.decide(
                fixture.candidate().getId(),
                new ProjectResponsibilityDecisionRequest(
                        0, ProjectResponsibilityDecisionRequest.Decision.CONFIRM,
                        "Spring Boot 주문 API 요청 검증 담당"));

        assertThat(response.reviewCompleted()).isTrue();
        assertThat(response.resumedJobAnalysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(fixture.task().getReviewStatus())
                .isEqualTo(ProjectResponsibilityReviewStatus.REVIEW_COMPLETED);
        assertThat(fixture.analysis().getAnalysisStatus()).isEqualTo(JobAnalysisStatus.QUEUED);
        assertThat(fixture.analysis().getUserProfileVersion()).isEqualTo(2);
        verify(profileVersionRepository, times(1)).save(any(UserProfileVersion.class));
        verify(responsibilityRepository, times(1)).save(any());
    }

    @Test
    void decide_lastCandidateWithPendingTechnologySuggestion_keepsReviewWaiting() {
        TestFixture fixture = fixture();
        when(taskRepository.findByCandidateIdForUpdate(fixture.candidate().getId()))
                .thenReturn(Optional.of(fixture.task()));
        when(candidateRepository.findByIdAndExtractionTask_Id(
                fixture.candidate().getId(), fixture.task().getId()))
                .thenReturn(Optional.of(fixture.candidate()));
        when(candidateRepository.existsByExtractionTask_IdAndCandidateStatus(
                fixture.task().getId(), ProjectResponsibilityCandidateStatus.UNCONFIRMED))
                .thenReturn(false);
        when(suggestionRepository.existsByExtractionTask_IdAndDecisionStatus(
                eq(fixture.task().getId()), any()))
                .thenReturn(true);

        ProjectResponsibilityDecisionResponse response = service.decide(
                fixture.candidate().getId(),
                new ProjectResponsibilityDecisionRequest(
                        0, ProjectResponsibilityDecisionRequest.Decision.CONFIRM,
                        "Spring Boot 주문 API 요청 검증 담당"));

        assertThat(response.reviewCompleted()).isFalse();
        assertThat(response.resumedJobAnalysisId()).isNull();
        assertThat(fixture.task().getReviewStatus())
                .isEqualTo(ProjectResponsibilityReviewStatus.AWAITING_USER_CONFIRMATION);
        verify(profileVersionRepository, never()).save(any());
        verify(jobAnalysisRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void decide_staleVersion_throwsConflictWithoutSavingCandidate() {
        TestFixture fixture = fixture();
        when(taskRepository.findByCandidateIdForUpdate(fixture.candidate().getId()))
                .thenReturn(Optional.of(fixture.task()));
        when(candidateRepository.findByIdAndExtractionTask_Id(
                fixture.candidate().getId(), fixture.task().getId()))
                .thenReturn(Optional.of(fixture.candidate()));

        assertThatThrownBy(() -> service.decide(
                fixture.candidate().getId(),
                new ProjectResponsibilityDecisionRequest(
                        1, ProjectResponsibilityDecisionRequest.Decision.CONFIRM,
                        "Spring Boot 주문 API 담당")))
                .isInstanceOf(ProjectResponsibilityConflictException.class);

        verify(candidateRepository, never()).saveAndFlush(any());
        verify(profileVersionRepository, never()).save(any());
    }

    @Test
    void decide_sameConfirmedRequest_replaysExistingResultWithoutCreatingVersion() {
        TestFixture fixture = fixture();
        fixture.candidate().confirm("Spring Boot 주문 API 담당", NOW.minusSeconds(10));
        fixture.task().completeReview(NOW.minusSeconds(5));
        when(taskRepository.findByCandidateIdForUpdate(fixture.candidate().getId()))
                .thenReturn(Optional.of(fixture.task()));
        when(candidateRepository.findByIdAndExtractionTask_Id(
                fixture.candidate().getId(), fixture.task().getId()))
                .thenReturn(Optional.of(fixture.candidate()));

        ProjectResponsibilityDecisionResponse response = service.decide(
                fixture.candidate().getId(),
                new ProjectResponsibilityDecisionRequest(
                        0, ProjectResponsibilityDecisionRequest.Decision.CONFIRM,
                        "Spring Boot 주문 API 담당"));

        assertThat(response.reviewCompleted()).isTrue();
        assertThat(response.resumedJobAnalysisId()).isEqualTo(ANALYSIS_ID);
        verify(candidateRepository, never()).saveAndFlush(any());
        verify(profileVersionRepository, never()).save(any());
    }

    @Test
    void decide_differentDecisionAfterConfirmation_throwsStateConflict() {
        TestFixture fixture = fixture();
        fixture.candidate().confirm("Spring Boot 주문 API 담당", NOW.minusSeconds(10));
        when(taskRepository.findByCandidateIdForUpdate(fixture.candidate().getId()))
                .thenReturn(Optional.of(fixture.task()));
        when(candidateRepository.findByIdAndExtractionTask_Id(
                fixture.candidate().getId(), fixture.task().getId()))
                .thenReturn(Optional.of(fixture.candidate()));

        assertThatThrownBy(() -> service.decide(
                fixture.candidate().getId(),
                new ProjectResponsibilityDecisionRequest(
                        0, ProjectResponsibilityDecisionRequest.Decision.REJECT, null)))
                .isInstanceOf(ProjectResponsibilityStateConflictException.class);

        verify(candidateRepository, never()).saveAndFlush(any());
        verify(profileVersionRepository, never()).save(any());
    }

    private TestFixture fixture() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), USER_ID, NOW.minusSeconds(100));
        UserProfileVersion baseVersion = UserProfileVersion.create(
                UUID.randomUUID(), profile, 1, "백엔드 개발자", "0".repeat(64), NOW.minusSeconds(90));
        ProjectSource source = ProjectSource.create(
                UUID.randomUUID(), USER_ID, "https://github.com/example/sample",
                "example/sample", "main", "a".repeat(40), NOW.minusSeconds(80));
        JobAnalysis analysis = JobAnalysis.createQueued(
                ANALYSIS_ID, USER_ID, profile.getId(), 1, List.of(source), NOW.minusSeconds(70));
        analysis.markRunning(NOW.minusSeconds(60));
        analysis.awaitUserConfirmation(NOW.minusSeconds(50));
        ProjectResponsibilityExtractionTask task = ProjectResponsibilityExtractionTask.create(
                UUID.randomUUID(), source, ANALYSIS_ID, baseVersion,
                Set.of(UUID.randomUUID()), NOW.minusSeconds(40));
        ProjectResponsibilityCandidate candidate = ProjectResponsibilityCandidate.create(
                UUID.randomUUID(), task, "Spring Boot 주문 API 구현", Set.of(UUID.randomUUID()),
                NOW.minusSeconds(30), NOW.plusSeconds(3600));
        return new TestFixture(profile, task, candidate, analysis);
    }

    private record TestFixture(
            UserProfile profile,
            ProjectResponsibilityExtractionTask task,
            ProjectResponsibilityCandidate candidate,
            JobAnalysis analysis) {
    }
}
