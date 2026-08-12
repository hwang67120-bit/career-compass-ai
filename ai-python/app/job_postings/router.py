"""채용공고 구조화 추출과 근거 의미 비교(LLM-as-judge) 내부 API다.

- `POST /job-postings/extract`: 공고 원문 구조화 추출
  (contracts/job-posting-extraction.md). Java가 최소 sourceText를 전달한다.
- `POST /job-evidence-similarities`: 공고 담당 업무와 사용자 프로젝트 업무의
  의미 비교(contracts/job-evidence-similarity.md).
"""

import functools
from uuid import UUID

from fastapi import APIRouter, Depends, Header
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from app.guardrails.internal_auth import verify_internal_token
from app.job_postings.settings import (
    JobPostingExtractionSettings,
    get_job_posting_extraction_settings,
)
from app.providers.gemini_client import (
    build_gemini_job_posting_fallback_provider,
    get_gemini_settings_if_configured,
)
from app.providers.ollama import OllamaProvider, OllamaResponseError, OllamaUnavailableError
from app.providers.ollama_client import (
    get_ollama_evidence_judge_provider,
    get_ollama_job_posting_provider,
    get_ollama_job_posting_responsibility_provider,
)
from app.providers.settings import GeminiSettings
from app.schemas.envelope import FieldError, error_envelope, resolve_request_id, success_envelope
from app.schemas.job_evidence_similarity import SimilarityRequest
from app.services.job_evidence_similarity import compare_evidence
from app.services.job_posting_extraction import (
    JobPostingEvidenceValidationError,
    extract_job_posting_profile,
)

router = APIRouter(
    prefix="/internal/v1",
    tags=["job-postings"],
    dependencies=[Depends(verify_internal_token)],
)


class JobPostingExtractionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    job_posting_id: str = Field(alias="jobPostingId")
    extraction_task_id: str = Field(alias="extractionTaskId")
    source_text: str = Field(alias="sourceText")


def _is_uuid(value: str) -> bool:
    try:
        UUID(value)
    except ValueError:
        return False
    return True


@router.post("/job-postings/extract")
async def extract_job_posting(
    request: JobPostingExtractionRequest,
    x_request_id: str | None = Header(default=None),
    settings: JobPostingExtractionSettings = Depends(get_job_posting_extraction_settings),
    ollama_provider: OllamaProvider = Depends(get_ollama_job_posting_provider),
    ollama_responsibility_provider: OllamaProvider = Depends(
        get_ollama_job_posting_responsibility_provider
    ),
    gemini_settings: GeminiSettings | None = Depends(get_gemini_settings_if_configured),
) -> JSONResponse:
    request_id = resolve_request_id(x_request_id)

    field_errors = []
    if not _is_uuid(request.job_posting_id):
        field_errors.append(FieldError(field_name="jobPostingId", message="UUID 형식이어야 합니다."))
    if not _is_uuid(request.extraction_task_id):
        field_errors.append(
            FieldError(field_name="extractionTaskId", message="UUID 형식이어야 합니다.")
        )
    if not request.source_text.strip():
        field_errors.append(
            FieldError(field_name="sourceText", message="공백이 아닌 문자열이어야 합니다.")
        )
    elif len(request.source_text) > settings.job_posting_extraction_max_text_length:
        field_errors.append(
            FieldError(field_name="sourceText", message="설정된 최대 길이를 초과했습니다.")
        )

    if field_errors:
        return JSONResponse(
            status_code=422,
            content=error_envelope(
                request_id,
                "INVALID_EXTRACTION_REQUEST",
                "요청 필드가 계약을 따르지 않습니다.",
                field_errors=field_errors,
            ),
        )

    fallback_provider_factory = (
        functools.partial(build_gemini_job_posting_fallback_provider, gemini_settings)
        if gemini_settings is not None
        else None
    )

    try:
        result = await extract_job_posting_profile(
            request.source_text,
            ollama_provider,
            ollama_responsibility_provider,
            fallback_provider_factory,
        )
    except OllamaUnavailableError:
        return JSONResponse(
            status_code=503,
            content=error_envelope(
                request_id,
                "MODEL_UNAVAILABLE",
                "Ollama 모델을 사용할 수 없습니다.",
                retryable=True,
            ),
        )
    except (OllamaResponseError, JobPostingEvidenceValidationError):
        return JSONResponse(
            status_code=502,
            content=error_envelope(
                request_id,
                "MODEL_RESPONSE_INVALID",
                "모델 응답이 후보 스키마 또는 근거 검증을 통과하지 못했습니다.",
            ),
        )

    return JSONResponse(
        status_code=200,
        content=success_envelope(
            request_id,
            {
                "jobPostingId": request.job_posting_id,
                "extractionTaskId": request.extraction_task_id,
                "status": "EXTRACTED",
                "extraction": result.extraction.model_dump(by_alias=True),
                "modelExecutions": [
                    {
                        "stage": execution.stage.value,
                        "provider": execution.provider.value,
                        "model": execution.model,
                    }
                    for execution in result.model_executions
                ],
            },
        ),
    )


# 2026-08-12 확정 임시값(contracts/job-evidence-similarity.md). 실제 표본 후 재조정.
_MAX_JOB_EVIDENCE = 20
_MAX_USER_EVIDENCE = 30
_MAX_EVIDENCE_TEXT_LENGTH = 500


def _validate_similarity_request(request: SimilarityRequest) -> list[FieldError]:
    """계약의 요청 규칙(category·중복·개수·길이)을 검사한다. 빈 목록이면 통과."""
    errors: list[FieldError] = []
    if not request.job_evidence:
        errors.append(FieldError(field_name="jobEvidence", message="최소 1개의 공고 근거가 필요합니다."))
    if len(request.job_evidence) > _MAX_JOB_EVIDENCE:
        errors.append(FieldError(field_name="jobEvidence", message=f"공고 근거는 최대 {_MAX_JOB_EVIDENCE}개입니다."))
    if len(request.user_evidence) > _MAX_USER_EVIDENCE:
        errors.append(
            FieldError(field_name="userEvidence", message=f"사용자 근거는 최대 {_MAX_USER_EVIDENCE}개입니다.")
        )

    seen_ids: set[str] = set()
    checks = (
        ("jobEvidence", "RESPONSIBILITY", request.job_evidence),
        ("userEvidence", "PROJECT_RESPONSIBILITY", request.user_evidence),
    )
    for field, expected_category, items in checks:
        for item in items:
            if item.category != expected_category:
                errors.append(
                    FieldError(
                        field_name=f"{field}[{item.evidence_id}].category",
                        message=f"{expected_category}만 허용됩니다.",
                    )
                )
            if not item.text.strip():
                errors.append(
                    FieldError(field_name=f"{field}[{item.evidence_id}].text", message="공백이 아닌 문자열이어야 합니다.")
                )
            elif len(item.text) > _MAX_EVIDENCE_TEXT_LENGTH:
                errors.append(
                    FieldError(
                        field_name=f"{field}[{item.evidence_id}].text",
                        message=f"최대 {_MAX_EVIDENCE_TEXT_LENGTH}자입니다.",
                    )
                )
            if item.evidence_id in seen_ids:
                errors.append(FieldError(field_name=field, message=f"근거 식별자가 중복됩니다: {item.evidence_id}"))
            seen_ids.add(item.evidence_id)
    return errors


@router.post("/job-evidence-similarities")
async def compare_job_evidence_similarities(
    request: SimilarityRequest,
    x_request_id: str | None = Header(default=None),
    provider: OllamaProvider = Depends(get_ollama_evidence_judge_provider),
) -> JSONResponse:
    request_id = resolve_request_id(x_request_id)

    field_errors = _validate_similarity_request(request)
    if field_errors:
        return JSONResponse(
            status_code=422,
            content=error_envelope(
                request_id,
                "INVALID_SIMILARITY_REQUEST",
                "요청 필드가 계약을 따르지 않습니다.",
                field_errors=field_errors,
            ),
        )

    try:
        results, status = await compare_evidence(request, provider)
    except OllamaUnavailableError:
        return JSONResponse(
            status_code=503,
            content=error_envelope(
                request_id,
                "SEMANTIC_COMPARISON_MODEL_UNAVAILABLE",
                "판정 모델을 사용할 수 없습니다.",
                retryable=True,
            ),
        )
    except OllamaResponseError:
        return JSONResponse(
            status_code=502,
            content=error_envelope(
                request_id,
                "SEMANTIC_COMPARISON_RESPONSE_INVALID",
                "모델 응답이 판정 스키마를 통과하지 못했습니다.",
            ),
        )

    return JSONResponse(
        status_code=200,
        content=success_envelope(
            request_id,
            {
                "comparisonTaskId": request.comparison_task_id,
                "jobAnalysisId": request.job_analysis_id,
                "jobPostingId": request.job_posting_id,
                "status": status,
                "method": "LLM_JUDGE",
                "results": results,
                "modelExecution": {
                    "stage": "EVIDENCE_SEMANTIC_COMPARISON",
                    "provider": "OLLAMA",
                    "model": provider.model_name,
                },
            },
        ),
    )
