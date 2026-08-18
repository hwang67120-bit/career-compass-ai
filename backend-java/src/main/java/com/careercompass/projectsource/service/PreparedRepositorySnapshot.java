package com.careercompass.projectsource.service;

import java.util.List;
import java.util.Map;

import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionRequest;

public record PreparedRepositorySnapshot(
        PythonProjectResponsibilityExtractionRequest.RepositorySnapshot requestSnapshot,
        Map<String, Evidence> evidenceById,
        List<Exclusion> exclusions
) {
    public record Evidence(String evidenceId, String path, String text) {
    }

    public record Exclusion(String path, String reason) {
    }
}
