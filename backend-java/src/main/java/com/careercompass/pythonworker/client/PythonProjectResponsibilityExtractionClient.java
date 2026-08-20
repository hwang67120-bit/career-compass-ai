package com.careercompass.pythonworker.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.careercompass.common.observability.RequestCorrelationContext;
import com.careercompass.pythonworker.config.ProjectResponsibilityExtractionPolicyProperties;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionEnvelope;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionRequest;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionException;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionFailure;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionResponseViolation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonProjectResponsibilityExtractionClient {

    private static final Set<String> ALLOWED_FILE_TYPES =
            Set.of("MANIFEST", "CONFIGURATION", "SOURCE", "TEST");
    private static final Set<String> ALLOWED_DETECTION_SOURCES =
            Set.of("MANIFEST", "LANGUAGE");

    private final RestClient restClient;
    private final String internalServiceToken;
    private final ProjectResponsibilityExtractionPolicyProperties policy;

    public PythonProjectResponsibilityExtractionClient(
            @Qualifier("pythonJobPostingExtractionRestClient") RestClient restClient,
            PythonWorkerProperties properties,
            ProjectResponsibilityExtractionPolicyProperties policy
    ) {
        this.restClient = restClient;
        this.internalServiceToken = properties.internalToken();
        this.policy = policy;
    }

    /**
     * 기능: 선택 기술과 저장소 스냅숏을 Python에 전달해 프로젝트 근거 후보를 추출한다.
     * 반환 값: 저장 전에 계약 검증을 통과한 감지 기술과 담당 업무 후보를 반환한다.
     */
    public PythonProjectResponsibilityExtractionEnvelope.Data extract(
            PythonProjectResponsibilityExtractionRequest request
    ) {
        validateRequest(request);
        PythonProjectResponsibilityExtractionEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri("/internal/v1/project-responsibility-extractions")
                    .header(PythonWorkerRequestHeaders.INTERNAL_TOKEN, internalServiceToken)
                    .header(PythonWorkerRequestHeaders.REQUEST_ID,
                            RequestCorrelationContext.currentOrCreate().toString())
                    .body(request)
                    .exchange((httpRequest, response) -> response.bodyTo(
                            PythonProjectResponsibilityExtractionEnvelope.class));
        } catch (ResourceAccessException exception) {
            throw new PythonProjectResponsibilityExtractionException(
                    PythonProjectResponsibilityExtractionFailure.MODEL_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            throw new PythonProjectResponsibilityExtractionException(
                    PythonProjectResponsibilityExtractionFailure.RESPONSE_INVALID,
                    PythonProjectResponsibilityExtractionResponseViolation
                            .RESPONSE_DESERIALIZATION_INVALID,
                    exception);
        }
        if (envelope == null || envelope.data() == null) {
            throw failureFromEnvelope(envelope);
        }
        validateData(request, envelope.data());
        return envelope.data();
    }

    private void validateRequest(PythonProjectResponsibilityExtractionRequest request) {
        if (request == null || isBlank(request.extractionTaskId())
                || isBlank(request.projectSourceId()) || request.repositorySnapshot() == null
                || request.selectedTechnologyTags() == null
                || request.selectedTechnologyTags().isEmpty()
                || request.selectedTechnologyTags().size() > policy.maxSelectedTechnologyTags()) {
            throw requestInvalid();
        }
        Set<String> tagIds = new HashSet<>();
        for (PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag tag
                : request.selectedTechnologyTags()) {
            if (tag == null || isBlank(tag.technologyTagId()) || isBlank(tag.canonicalName())
                    || !tagIds.add(tag.technologyTagId())) {
                throw requestInvalid();
            }
        }
        validateSnapshot(request.repositorySnapshot());
    }

    private void validateSnapshot(
            PythonProjectResponsibilityExtractionRequest.RepositorySnapshot snapshot
    ) {
        if (isBlank(snapshot.sourceUrl()) || snapshot.fetchedAt() == null
                || isBlank(snapshot.repositoryVersion()) || snapshot.readmes() == null
                || snapshot.files() == null || snapshot.readmes().size() > policy.maxReadmes()
                || snapshot.readmes().size() + snapshot.files().size()
                > policy.maxEvidenceItems()) {
            throw requestInvalid();
        }
        Set<String> evidenceIds = new HashSet<>();
        int totalTextCodePoints = codePointCount(snapshot.description());
        for (PythonProjectResponsibilityExtractionRequest.ReadmeEvidence readme
                : snapshot.readmes()) {
            if (readme == null || !isValidEvidence(
                    readme.evidenceId(), readme.path(), readme.text(), evidenceIds)) {
                throw requestInvalid();
            }
            totalTextCodePoints += codePointCount(readme.text());
        }
        int manifestCount = 0;
        int configurationCount = 0;
        for (PythonProjectResponsibilityExtractionRequest.FileEvidence file : snapshot.files()) {
            if (file == null || !ALLOWED_FILE_TYPES.contains(file.fileType())
                    || !isValidEvidence(
                            file.evidenceId(), file.path(), file.text(), evidenceIds)) {
                throw requestInvalid();
            }
            if ("MANIFEST".equals(file.fileType())) {
                manifestCount++;
            } else if ("CONFIGURATION".equals(file.fileType())) {
                configurationCount++;
            }
            totalTextCodePoints += codePointCount(file.text());
        }
        if (manifestCount > policy.maxManifests()
                || configurationCount > policy.maxConfigurations()
                || totalTextCodePoints > policy.maxTotalTextCodePoints()) {
            throw requestInvalid();
        }
    }

    private boolean isValidEvidence(
            String evidenceId, String path, String text, Set<String> evidenceIds
    ) {
        return !isBlank(evidenceId) && !isBlank(path) && !isBlank(text)
                && evidenceIds.add(evidenceId)
                && codePointCount(text) <= policy.maxEvidenceTextCodePoints();
    }

    private void validateData(
            PythonProjectResponsibilityExtractionRequest request,
            PythonProjectResponsibilityExtractionEnvelope.Data data
    ) {
        if (!request.extractionTaskId().equals(data.extractionTaskId())
                || !request.projectSourceId().equals(data.projectSourceId())) {
            throw responseInvalid(
                    PythonProjectResponsibilityExtractionResponseViolation.IDENTIFIER_MISMATCH);
        }
        if (!request.repositorySnapshot().repositoryVersion().equals(data.repositoryVersion())) {
            throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                    .REPOSITORY_VERSION_MISMATCH);
        }
        Set<String> requestEvidenceIds = requestEvidenceIds(request.repositorySnapshot());
        validateDetectedTechnologies(data.detectedTechnologies(), requestEvidenceIds);
        validateResponsibilityCandidates(
                data.responsibilityEvidenceCandidates(), requestEvidenceIds);
        validateModelExecution(data.modelExecution());
    }

    private void validateDetectedTechnologies(
            List<PythonProjectResponsibilityExtractionEnvelope.DetectedTechnology> detected,
            Set<String> requestEvidenceIds
    ) {
        if (detected == null || detected.size() > policy.maxDetectedTechnologies()) {
            throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                    .DETECTED_TECHNOLOGY_INVALID);
        }
        Set<String> detectionKeys = new HashSet<>();
        for (PythonProjectResponsibilityExtractionEnvelope.DetectedTechnology technology
                : detected) {
            if (technology == null || isBlank(technology.detectedName())
                    || !ALLOWED_DETECTION_SOURCES.contains(technology.source())
                    || !detectionKeys.add(
                            technology.detectedName() + "\u0000" + technology.source())) {
                throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                        .DETECTED_TECHNOLOGY_INVALID);
            }
            if (technology.technologyTagId() != null || technology.canonicalName() != null
                    || technology.findingStatus() != null) {
                throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                        .FORBIDDEN_TECHNOLOGY_FIELD);
            }
            validateEvidenceReferences(technology.evidenceIds(), requestEvidenceIds);
        }
    }

    private void validateResponsibilityCandidates(
            List<PythonProjectResponsibilityExtractionEnvelope.ResponsibilityEvidenceCandidate>
                    candidates,
            Set<String> requestEvidenceIds
    ) {
        if (candidates == null) {
            throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                    .RESPONSIBILITY_CANDIDATE_INVALID);
        }
        Set<String> candidateIds = new HashSet<>();
        for (PythonProjectResponsibilityExtractionEnvelope.ResponsibilityEvidenceCandidate
                candidate : candidates) {
            if (candidate == null || isBlank(candidate.evidenceId())
                    || !candidateIds.add(candidate.evidenceId())
                    || !"PROJECT_RESPONSIBILITY".equals(candidate.category())
                    || isBlank(candidate.text())
                    || codePointCount(candidate.text())
                    > policy.maxResponsibilityTextCodePoints()
                    || !"UNCONFIRMED".equals(candidate.confirmationStatus())) {
                throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                        .RESPONSIBILITY_CANDIDATE_INVALID);
            }
            validateEvidenceReferences(candidate.sourceEvidenceIds(), requestEvidenceIds);
        }
    }

    private void validateEvidenceReferences(
            List<String> evidenceIds, Set<String> requestEvidenceIds
    ) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                    .EVIDENCE_REFERENCE_INVALID);
        }
        Set<String> uniqueIds = new HashSet<>();
        for (String evidenceId : evidenceIds) {
            if (isBlank(evidenceId) || !requestEvidenceIds.contains(evidenceId)
                    || !uniqueIds.add(evidenceId)) {
                throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                        .EVIDENCE_REFERENCE_INVALID);
            }
        }
    }

    private Set<String> requestEvidenceIds(
            PythonProjectResponsibilityExtractionRequest.RepositorySnapshot snapshot
    ) {
        Set<String> ids = new HashSet<>();
        snapshot.readmes().forEach(readme -> ids.add(readme.evidenceId()));
        snapshot.files().forEach(file -> ids.add(file.evidenceId()));
        return ids;
    }

    private void validateModelExecution(
            PythonProjectResponsibilityExtractionEnvelope.ModelExecution execution
    ) {
        if (execution == null
                || !"PROJECT_RESPONSIBILITY_EXTRACTION".equals(execution.stage())
                || !"OLLAMA".equals(execution.provider()) || isBlank(execution.model())) {
            throw responseInvalid(PythonProjectResponsibilityExtractionResponseViolation
                    .MODEL_EXECUTION_INVALID);
        }
    }

    private PythonProjectResponsibilityExtractionException failureFromEnvelope(
            PythonProjectResponsibilityExtractionEnvelope envelope
    ) {
        String errorType = envelope != null && envelope.error() != null
                ? envelope.error().errorType() : null;
        if ("PROJECT_RESPONSIBILITY_EXTRACTION_MODEL_UNAVAILABLE".equals(errorType)) {
            return new PythonProjectResponsibilityExtractionException(
                    PythonProjectResponsibilityExtractionFailure.MODEL_UNAVAILABLE);
        }
        if ("INVALID_PROJECT_RESPONSIBILITY_EXTRACTION_REQUEST".equals(errorType)
                || "INTERNAL_UNAUTHORIZED".equals(errorType)
                || "INTERNAL_TOKEN_REQUIRED".equals(errorType)) {
            return requestInvalid();
        }
        return new PythonProjectResponsibilityExtractionException(
                PythonProjectResponsibilityExtractionFailure.RESPONSE_INVALID);
    }

    private PythonProjectResponsibilityExtractionException requestInvalid() {
        return new PythonProjectResponsibilityExtractionException(
                PythonProjectResponsibilityExtractionFailure.REQUEST_INVALID);
    }

    private PythonProjectResponsibilityExtractionException responseInvalid(
            PythonProjectResponsibilityExtractionResponseViolation violation
    ) {
        return new PythonProjectResponsibilityExtractionException(
                PythonProjectResponsibilityExtractionFailure.RESPONSE_INVALID, violation);
    }

    private int codePointCount(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
