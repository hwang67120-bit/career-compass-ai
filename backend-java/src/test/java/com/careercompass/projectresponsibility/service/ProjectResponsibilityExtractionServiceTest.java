package com.careercompass.projectresponsibility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.service.RepositorySnapshotService;
import com.careercompass.pythonworker.client.PythonProjectResponsibilityExtractionClient;
import com.careercompass.pythonworker.config.ProjectResponsibilityExtractionPolicyProperties;
import com.careercompass.technologytag.service.TechnologyTagResolutionService;
import com.careercompass.userprofile.domain.UserProfileVersion;
import org.junit.jupiter.api.Test;

class ProjectResponsibilityExtractionServiceTest {

    @Test
    void extract_withNoStandardTechnologyTags_completesWithoutRemoteCalls() {
        RepositorySnapshotService snapshotService = mock(RepositorySnapshotService.class);
        PythonProjectResponsibilityExtractionClient extractionClient =
                mock(PythonProjectResponsibilityExtractionClient.class);
        TechnologyTagResolutionService resolutionService =
                mock(TechnologyTagResolutionService.class);
        ProjectResponsibilityExtractionPersistenceService persistenceService =
                mock(ProjectResponsibilityExtractionPersistenceService.class);
        ProjectResponsibilityExtractionService service =
                new ProjectResponsibilityExtractionService(
                        snapshotService,
                        extractionClient,
                        resolutionService,
                        persistenceService,
                        new ProjectResponsibilityExtractionPolicyProperties(
                                10, 30, 3, 20, 10, 30, 2000, 20000, 500));
        JobAnalysis jobAnalysis = mock(JobAnalysis.class);
        UserProfileVersion profileVersion = mock(UserProfileVersion.class);
        ProjectSource projectSource = mock(ProjectSource.class);
        UUID taskId = UUID.randomUUID();
        when(profileVersion.getTechnologyTags()).thenReturn(List.of());
        when(jobAnalysis.getProjectSources()).thenReturn(List.of(projectSource));
        when(persistenceService.createTask(
                jobAnalysis,
                projectSource,
                profileVersion,
                Set.of())).thenReturn(taskId);

        ProjectResponsibilityExtractionOutcome outcome =
                service.extract(jobAnalysis, profileVersion);

        assertThat(outcome.requiresUserConfirmation()).isFalse();
        assertThat(outcome.partiallyExtracted()).isFalse();
        verify(persistenceService).completeWithoutCandidates(taskId);
        verifyNoInteractions(snapshotService, extractionClient, resolutionService);
    }
}
