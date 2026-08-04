"""채용공고 원문을 구조화 추출하는 내부 API다.

계약(제안): contracts/job-posting-extraction.md — Java·프론트와 아직 확정
전이다. PDF가 아니라 JSON 본문을 받고, 개인정보 제거 단계가 없다(공개
회사 정보).
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
    get_ollama_job_posting_provider,
    get_ollama_job_posting_responsibility_provider,
)
from app.providers.settings import GeminiSettings
from app.schemas.envelope import FieldError, error_envelope, resolve_request_id, success_envelope
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
                        "provider": execution.provider,
                        "model": execution.model,
                    }
                    for execution in result.model_executions
                ],
            },
        ),
    )
