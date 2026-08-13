package com.careercompass.pythonworker.dto;

import java.time.Instant;
import java.util.List;

public record PythonProjectResponsibilityExtractionRequest(
        String extractionTaskId, String projectSourceId,
        List<SelectedTechnologyTag> selectedTechnologyTags,
        RepositorySnapshot repositorySnapshot
) {
    public record SelectedTechnologyTag(String technologyTagId, String canonicalName) {
    }
    public record RepositorySnapshot(
            String sourceUrl, Instant fetchedAt, String repositoryVersion,
            String description, List<ReadmeEvidence> readmes, List<FileEvidence> files
    ) {
    }
    public record ReadmeEvidence(String evidenceId, String path, String text) {
    }
    public record FileEvidence(String evidenceId, String path, String fileType, String text) {
    }
}
