"""이력서·포트폴리오 PDF에서 텍스트를 추출하고 구조화하는 내부 API다.

계약: contracts/document-extraction.md

파이프라인: 요청 검증 → 내부 인증 → PDF 텍스트 추출 → 개인정보 제거 →
Ollama 구조화 추출 → 근거 검증 → 응답. 개인정보 제거는 이메일·전화번호·
주민등록번호를 정규식으로 치환하는 방식이다(backend-java의
BasicPersonalInformationSanitizer와 동일 패턴 재사용, docs/architecture 참고).
이름 등 정규식으로 잡히지 않는 개인정보는 LLM 프롬프트 지시에만 의존하므로
완전한 보장은 아니다 — 실제 사용자 자료로 재검증 필요.

Gemini는 노션 "무료 등급 데이터 제한" 정책 때문에 이 파이프라인에서 쓰지
않는다(실제 이력서를 Gemini에 보내지 않음). 실제 문서 추출은 Ollama만
사용한다.
"""

from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, Header, UploadFile
from fastapi.responses import JSONResponse

from app.documents.settings import DocumentExtractionSettings, get_document_extraction_settings
from app.guardrails.internal_auth import verify_internal_token
from app.providers.ollama import OllamaProvider, OllamaResponseError, OllamaUnavailableError
from app.providers.ollama_client import get_ollama_provider
from app.schemas.envelope import FieldError, error_envelope, resolve_request_id, success_envelope
from app.services.pdf_extraction import (
    PdfNoExtractableTextError,
    PdfUnreadableError,
    extract_pdf_text,
)
from app.services.resume_extraction import EvidenceValidationError, extract_resume_profile

router = APIRouter(
    prefix="/internal/v1",
    tags=["documents"],
    dependencies=[Depends(verify_internal_token)],
)

_ALLOWED_DOCUMENT_TYPES = {"RESUME", "PORTFOLIO"}


@router.post("/documents/extract")
async def extract_document(
    document_id: str = Form(..., alias="documentId"),
    extraction_task_id: str = Form(..., alias="extractionTaskId"),
    document_type: str = Form(..., alias="documentType"),
    file: UploadFile = File(...),
    x_request_id: str | None = Header(default=None),
    settings: DocumentExtractionSettings = Depends(get_document_extraction_settings),
    ollama_provider: OllamaProvider = Depends(get_ollama_provider),
) -> JSONResponse:
    request_id = resolve_request_id(x_request_id)

    field_errors = []
    if not _is_uuid(document_id):
        field_errors.append(FieldError(field_name="documentId", message="UUID 형식이어야 합니다."))
    if not _is_uuid(extraction_task_id):
        field_errors.append(
            FieldError(field_name="extractionTaskId", message="UUID 형식이어야 합니다.")
        )
    if document_type not in _ALLOWED_DOCUMENT_TYPES:
        field_errors.append(
            FieldError(field_name="documentType", message="RESUME 또는 PORTFOLIO여야 합니다.")
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

    if file.content_type != "application/pdf":
        return JSONResponse(
            status_code=415,
            content=error_envelope(
                request_id,
                "UNSUPPORTED_MEDIA_TYPE",
                "PDF 파일만 업로드할 수 있습니다.",
            ),
        )

    content = await file.read()
    if len(content) > settings.document_extraction_max_pdf_size_bytes:
        return JSONResponse(
            status_code=413,
            content=error_envelope(
                request_id,
                "FILE_TOO_LARGE",
                "설정된 최대 크기를 초과했습니다.",
            ),
        )

    try:
        pages = extract_pdf_text(content)
    except PdfUnreadableError:
        return JSONResponse(
            status_code=422,
            content=error_envelope(
                request_id,
                "PDF_UNREADABLE",
                "PDF 파일을 열 수 없습니다.",
            ),
        )
    except PdfNoExtractableTextError:
        return JSONResponse(
            status_code=422,
            content=error_envelope(
                request_id,
                "NO_EXTRACTABLE_TEXT",
                "PDF에서 추출 가능한 텍스트를 찾지 못했습니다.",
            ),
        )

    try:
        candidate = await extract_resume_profile(pages, ollama_provider)
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
    except (OllamaResponseError, EvidenceValidationError):
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
                "documentId": document_id,
                "extractionTaskId": extraction_task_id,
                "status": "EXTRACTED",
                "candidate": candidate.model_dump(by_alias=True),
                "modelProvider": "ollama",
                "modelName": ollama_provider.model_name,
                "piiRemoved": True,
            },
        ),
    )


def _is_uuid(value: str) -> bool:
    try:
        UUID(value)
    except ValueError:
        return False
    return True
