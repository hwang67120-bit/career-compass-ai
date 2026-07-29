"""이력서·포트폴리오 PDF에서 텍스트를 추출하는 내부 API다.

계약: contracts/document-extraction.md

현재 범위: 요청 검증, 내부 인증과 PDF 텍스트 추출까지만 실제로 동작한다.
개인정보 제거와 LLM 구조화 추출(계약 5절 `ProfileCandidatePayload`)은
아직 방식이 정해지지 않아 구현하지 않았다(확인 필요: PII 제거 방식,
이력서 구조화 프롬프트). 이 지점에 도달하면 계약에 없는 501로 명시해
성공을 지어내지 않는다. Java는 501 응답을 정식 계약 상태로 처리하지
않아야 한다.
"""

from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, Header, UploadFile
from fastapi.responses import JSONResponse

from app.documents.settings import DocumentExtractionSettings, get_document_extraction_settings
from app.guardrails.internal_auth import verify_internal_token
from app.schemas.envelope import error_envelope, resolve_request_id
from app.services.pdf_extraction import (
    PdfNoExtractableTextError,
    PdfUnreadableError,
    extract_pdf_text,
)

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
) -> JSONResponse:
    request_id = resolve_request_id(x_request_id)

    field_errors = []
    if not _is_uuid(document_id):
        field_errors.append("documentId는 UUID 형식이어야 합니다.")
    if not _is_uuid(extraction_task_id):
        field_errors.append("extractionTaskId는 UUID 형식이어야 합니다.")
    if document_type not in _ALLOWED_DOCUMENT_TYPES:
        field_errors.append("documentType은 RESUME 또는 PORTFOLIO여야 합니다.")

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

    # 여기까지는 실제로 검증됨: 요청 필드, 내부 인증, PDF 텍스트 추출(len(pages) 페이지).
    # 개인정보 제거·LLM 구조화 추출은 방식이 정해지지 않아 다음 단계로 남긴다.
    # 계약에 정의된 코드가 아니므로 Java는 이 errorType을 별도로 분기하지 않는다.
    return JSONResponse(
        status_code=501,
        content=error_envelope(
            request_id,
            "PII_LLM_PIPELINE_NOT_IMPLEMENTED",
            f"PDF 텍스트 추출은 완료됐습니다({len(pages)}페이지). "
            "개인정보 제거·LLM 구조화 추출 방식이 아직 확정되지 않아 다음 단계는 미구현입니다.",
        ),
    )


def _is_uuid(value: str) -> bool:
    try:
        UUID(value)
    except ValueError:
        return False
    return True
