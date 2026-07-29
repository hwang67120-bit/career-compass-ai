package com.careercompass.pythonworker.client;

import com.careercompass.document.domain.DocumentType;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.DocumentExtractionData;
import com.careercompass.pythonworker.dto.DocumentExtractionErrorResponse;
import com.careercompass.pythonworker.dto.DocumentExtractionSuccessResponse;
import com.careercompass.pythonworker.dto.DocumentExtractionStatus;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload.CandidateCertification;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload.CandidateEducation;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload.CandidateEvidence;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload.CandidateProject;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload.CandidateSkill;
import com.careercompass.pythonworker.dto.ProfileCandidatePayload.CandidateWorkExperience;
import com.careercompass.pythonworker.exception.PythonDocumentExtractionContractException;
import com.careercompass.pythonworker.exception.PythonDocumentExtractionException;
import com.careercompass.pythonworker.exception.PythonPiiLlmPipelineNotImplementedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Component
public class PythonDocumentExtractionClient {

    private static final String EXTRACTION_PATH = "/internal/v1/documents/extract";
    private static final String PIPELINE_NOT_IMPLEMENTED = "PII_LLM_PIPELINE_NOT_IMPLEMENTED";
    private static final Set<String> MODEL_PROVIDERS = Set.of("ollama", "gemini");
    private static final Map<String, Integer> CONTRACT_ERROR_STATUSES = Map.ofEntries(
            Map.entry("INTERNAL_TOKEN_REQUIRED", 422),
            Map.entry("INTERNAL_UNAUTHORIZED", 401),
            Map.entry("INVALID_EXTRACTION_REQUEST", 422),
            Map.entry("UNSUPPORTED_MEDIA_TYPE", 415),
            Map.entry("FILE_TOO_LARGE", 413),
            Map.entry("PDF_UNREADABLE", 422),
            Map.entry("NO_EXTRACTABLE_TEXT", 422),
            Map.entry("PII_SANITIZATION_FAILED", 500),
            Map.entry("MODEL_UNAVAILABLE", 503),
            Map.entry("MODEL_RESPONSE_INVALID", 502),
            Map.entry("EXTRACTION_FAILED", 500)
    );

    private final RestClient restClient;
    private final ObjectReader successResponseReader;
    private final ObjectReader errorResponseReader;
    private final String internalServiceToken;

    public PythonDocumentExtractionClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            PythonWorkerProperties properties
    ) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
        this.successResponseReader = objectMapper
                .readerFor(DocumentExtractionSuccessResponse.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.errorResponseReader = objectMapper
                .readerFor(DocumentExtractionErrorResponse.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.internalServiceToken = properties.internalToken();
    }

    public DocumentExtractionSuccessResponse extract(
            UUID documentId,
            UUID extractionTaskId,
            DocumentType documentType,
            byte[] pdfBytes,
            String originalFilename
    ) {
        validateRequest(documentId, extractionTaskId, documentType, pdfBytes, originalFilename);
        UUID requestId = UUID.randomUUID();

        DocumentExtractionSuccessResponse response = restClient.post()
                .uri(EXTRACTION_PATH)
                .header(PythonWorkerRequestHeaders.INTERNAL_TOKEN, internalServiceToken)
                .header(PythonWorkerRequestHeaders.REQUEST_ID, requestId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(createMultipartBody(
                        documentId,
                        extractionTaskId,
                        documentType,
                        pdfBytes,
                        originalFilename
                ))
                .exchange((request, httpResponse) -> {
                    if (httpResponse.getStatusCode().isError()) {
                        throwExtractionFailure(
                                requestId,
                                httpResponse.getStatusCode(),
                                errorResponseReader.readValue(httpResponse.getBody())
                        );
                    }
                    return successResponseReader.readValue(httpResponse.getBody());
                });

        validateSuccessResponse(response, requestId, documentId, extractionTaskId);
        return response;
    }

    private MultiValueMap<String, Object> createMultipartBody(
            UUID documentId,
            UUID extractionTaskId,
            DocumentType documentType,
            byte[] pdfBytes,
            String originalFilename
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("documentId", documentId.toString());
        body.add("extractionTaskId", extractionTaskId.toString());
        body.add("documentType", documentType.name());

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        body.add("file", new HttpEntity<>(
                new NamedByteArrayResource(pdfBytes, originalFilename),
                fileHeaders
        ));
        return body;
    }

    private void throwExtractionFailure(
            UUID requestId,
            HttpStatusCode statusCode,
            DocumentExtractionErrorResponse response
    ) {
        validateErrorResponse(response, requestId, statusCode);
        if (statusCode.value() == 501
                && PIPELINE_NOT_IMPLEMENTED.equals(response.error().errorType())) {
            throw new PythonPiiLlmPipelineNotImplementedException(statusCode, response);
        }
        throw new PythonDocumentExtractionException(statusCode, response);
    }

    private void validateRequest(
            UUID documentId,
            UUID extractionTaskId,
            DocumentType documentType,
            byte[] pdfBytes,
            String originalFilename
    ) {
        if (documentId == null || extractionTaskId == null || documentType == null) {
            throw new IllegalArgumentException("문서 추출 식별자와 문서 종류는 필수입니다.");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF 파일 내용은 비어 있을 수 없습니다.");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("PDF 원본 파일명은 필수입니다.");
        }
    }

    private void validateSuccessResponse(
            DocumentExtractionSuccessResponse response,
            UUID requestId,
            UUID documentId,
            UUID extractionTaskId
    ) {
        if (response == null
                || !requestId.equals(response.requestId())
                || response.timestamp() == null
                || response.error() != null
                || response.data() == null) {
            throw contractViolation("성공 응답 봉투가 계약과 일치하지 않습니다.");
        }

        DocumentExtractionData extraction = response.data();
        if (!documentId.equals(extraction.documentId())
                || !extractionTaskId.equals(extraction.extractionTaskId())
                || extraction.status() != DocumentExtractionStatus.EXTRACTED
                || !extraction.piiRemoved()
                || extraction.candidate() == null
                || !MODEL_PROVIDERS.contains(extraction.modelProvider())
                || extraction.modelName() == null
                || extraction.modelName().isBlank()) {
            throw contractViolation("문서 추출 성공 데이터가 계약과 일치하지 않습니다.");
        }
        validateCandidate(extraction.candidate());
    }

    private void validateCandidate(ProfileCandidatePayload candidate) {
        if (candidate.skills() == null
                || candidate.workExperiences() == null
                || candidate.projects() == null
                || candidate.education() == null
                || candidate.certifications() == null
                || candidate.evidence() == null) {
            throw contractViolation("후보 목록은 생략할 수 없습니다.");
        }

        Set<String> evidenceIds = new HashSet<>();
        for (CandidateEvidence evidence : candidate.evidence()) {
            if (evidence == null
                    || isBlank(evidence.evidenceId())
                    || isBlank(evidence.fieldPath())
                    || isBlank(evidence.value())
                    || isBlank(evidence.sourceText())
                    || evidence.pageNumber() < 1
                    || !evidenceIds.add(evidence.evidenceId())) {
                throw contractViolation("후보 근거가 계약과 일치하지 않습니다.");
            }
        }

        List<List<String>> evidenceReferences = new ArrayList<>();
        for (CandidateSkill skill : candidate.skills()) {
            validateSkill(skill, evidenceReferences);
        }
        for (CandidateWorkExperience workExperience : candidate.workExperiences()) {
            if (workExperience == null
                    || workExperience.responsibilities() == null) {
                throw contractViolation("경력 후보가 계약과 일치하지 않습니다.");
            }
            evidenceReferences.add(workExperience.evidenceIds());
        }
        for (CandidateProject project : candidate.projects()) {
            if (project == null || project.technologies() == null) {
                throw contractViolation("프로젝트 후보가 계약과 일치하지 않습니다.");
            }
            evidenceReferences.add(project.evidenceIds());
            for (CandidateSkill technology : project.technologies()) {
                validateSkill(technology, evidenceReferences);
            }
        }
        for (CandidateEducation education : candidate.education()) {
            if (education == null) {
                throw contractViolation("학력 후보가 계약과 일치하지 않습니다.");
            }
            evidenceReferences.add(education.evidenceIds());
        }
        for (CandidateCertification certification : candidate.certifications()) {
            if (certification == null || isBlank(certification.name())) {
                throw contractViolation("자격증 후보가 계약과 일치하지 않습니다.");
            }
            evidenceReferences.add(certification.evidenceIds());
        }

        for (List<String> references : evidenceReferences) {
            if (references == null
                    || references.isEmpty()
                    || references.stream().anyMatch(reference ->
                    isBlank(reference) || !evidenceIds.contains(reference))) {
                throw contractViolation("후보가 유효한 근거를 참조하지 않습니다.");
            }
        }
    }

    private void validateSkill(
            CandidateSkill skill,
            List<List<String>> evidenceReferences
    ) {
        if (skill == null || isBlank(skill.rawName())) {
            throw contractViolation("기술 후보가 계약과 일치하지 않습니다.");
        }
        evidenceReferences.add(skill.evidenceIds());
    }

    private void validateErrorResponse(
            DocumentExtractionErrorResponse response,
            UUID requestId,
            HttpStatusCode statusCode
    ) {
        if (response == null
                || !requestId.equals(response.requestId())
                || response.data() != null
                || response.error() == null
                || response.error().fieldErrors() == null
                || response.timestamp() == null
                || isBlank(response.error().errorType())
                || isBlank(response.error().message())) {
            throw contractViolation("실패 응답 봉투가 계약과 일치하지 않습니다.");
        }

        if (statusCode.value() == 501
                && PIPELINE_NOT_IMPLEMENTED.equals(response.error().errorType())) {
            return;
        }

        Integer expectedStatus = CONTRACT_ERROR_STATUSES.get(response.error().errorType());
        if (expectedStatus == null || expectedStatus != statusCode.value()) {
            throw contractViolation("실패 상태 코드와 오류 유형이 계약과 일치하지 않습니다.");
        }
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private PythonDocumentExtractionContractException contractViolation(String message) {
        return new PythonDocumentExtractionContractException(message);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] pdfBytes, String filename) {
            super(pdfBytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
