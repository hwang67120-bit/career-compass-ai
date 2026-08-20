package com.careercompass.jobanalysis.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.careercompass.jobanalysis.dto.JobPostingComparisonSnapshot;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JobAnalysisJsonCodec {

    private final ObjectMapper objectMapper;

    public JobAnalysisJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    public List<PythonEvidenceSimilarityRequest.JobEvidence> parseJobEvidence(
            String extractionJson
    ) throws JsonProcessingException {
        JsonNode extraction = objectMapper.readTree(extractionJson);
        Map<String, String> evidenceTextById = new LinkedHashMap<>();
        for (JsonNode evidence : extraction.path("evidence")) {
            String evidenceId = evidence.path("evidenceId").asText("").strip();
            String sourceText = evidence.path("sourceText").asText("").strip();
            if (!evidenceId.isBlank() && !sourceText.isBlank()) {
                evidenceTextById.putIfAbsent(evidenceId, sourceText);
            }
        }

        Map<String, String> linkedResponsibilities = new LinkedHashMap<>();
        for (JsonNode responsibility : extraction.path("responsibilities")) {
            for (JsonNode evidenceIdNode : responsibility.path("evidenceIds")) {
                String evidenceId = evidenceIdNode.asText("").strip();
                String sourceText = evidenceTextById.get(evidenceId);
                if (sourceText != null) {
                    linkedResponsibilities.putIfAbsent(evidenceId, sourceText);
                }
            }
        }

        List<PythonEvidenceSimilarityRequest.JobEvidence> result = new ArrayList<>();
        linkedResponsibilities.forEach((evidenceId, text) ->
                result.add(new PythonEvidenceSimilarityRequest.JobEvidence(
                        evidenceId,
                        "RESPONSIBILITY",
                        text
                )));
        return List.copyOf(result);
    }

    public JobPostingComparisonSnapshot parseComparison(String comparisonJson)
            throws JsonProcessingException {
        return objectMapper.readValue(
                comparisonJson,
                JobPostingComparisonSnapshot.class
        );
    }
}
