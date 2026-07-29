from fpdf import FPDF
from fastapi.testclient import TestClient

from app.documents.settings import get_document_extraction_settings
from app.guardrails.settings import get_internal_auth_settings
from app.main import app

client = TestClient(app)

KOREAN_FONT_PATH = "C:/Windows/Fonts/malgun.ttf"

VALID_DOCUMENT_ID = "11111111-1111-1111-1111-111111111111"
VALID_EXTRACTION_TASK_ID = "22222222-2222-2222-2222-222222222222"


def _valid_token() -> str:
    return get_internal_auth_settings().internal_service_token


def _make_pdf_bytes(page_texts: list[str]) -> bytes:
    pdf = FPDF()
    pdf.add_font("Malgun", fname=KOREAN_FONT_PATH)
    for text in page_texts:
        pdf.add_page()
        if text:
            pdf.set_font("Malgun", size=12)
            pdf.cell(text=text)
    return bytes(pdf.output())


def _valid_form_data(**overrides: str) -> dict:
    data = {
        "documentId": VALID_DOCUMENT_ID,
        "extractionTaskId": VALID_EXTRACTION_TASK_ID,
        "documentType": "RESUME",
    }
    data.update(overrides)
    return data


def test_extract_rejects_missing_internal_token() -> None:
    response = client.post(
        "/internal/v1/documents/extract",
        data=_valid_form_data(),
        files={"file": ("resume.pdf", _make_pdf_bytes(["경력 사항"]), "application/pdf")},
    )

    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INTERNAL_TOKEN_REQUIRED"


def test_extract_rejects_wrong_internal_token() -> None:
    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": "wrong-token"},
        data=_valid_form_data(),
        files={"file": ("resume.pdf", _make_pdf_bytes(["경력 사항"]), "application/pdf")},
    )

    assert response.status_code == 401
    assert response.json()["error"]["errorType"] == "INTERNAL_UNAUTHORIZED"


def test_extract_rejects_invalid_ids_and_document_type() -> None:
    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": _valid_token()},
        data=_valid_form_data(documentId="not-a-uuid", documentType="COVER_LETTER"),
        files={"file": ("resume.pdf", _make_pdf_bytes(["경력 사항"]), "application/pdf")},
    )

    assert response.status_code == 422
    body = response.json()
    assert body["error"]["errorType"] == "INVALID_EXTRACTION_REQUEST"
    assert len(body["error"]["fieldErrors"]) == 2


def test_extract_rejects_non_pdf_content_type() -> None:
    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": _valid_token()},
        data=_valid_form_data(),
        files={"file": ("resume.txt", b"plain text", "text/plain")},
    )

    assert response.status_code == 415
    assert response.json()["error"]["errorType"] == "UNSUPPORTED_MEDIA_TYPE"


def test_extract_rejects_file_larger_than_configured_limit() -> None:
    def tiny_limit() -> object:
        settings = get_document_extraction_settings()
        return settings.model_copy(update={"document_extraction_max_pdf_size_bytes": 10})

    app.dependency_overrides[get_document_extraction_settings] = tiny_limit
    try:
        response = client.post(
            "/internal/v1/documents/extract",
            headers={"X-Internal-Token": _valid_token()},
            data=_valid_form_data(),
            files={"file": ("resume.pdf", _make_pdf_bytes(["경력 사항"]), "application/pdf")},
        )
    finally:
        app.dependency_overrides.pop(get_document_extraction_settings, None)

    assert response.status_code == 413
    assert response.json()["error"]["errorType"] == "FILE_TOO_LARGE"


def test_extract_rejects_unreadable_pdf() -> None:
    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": _valid_token()},
        data=_valid_form_data(),
        files={"file": ("resume.pdf", b"this is not a real pdf", "application/pdf")},
    )

    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "PDF_UNREADABLE"


def test_extract_rejects_pdf_without_extractable_text() -> None:
    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": _valid_token()},
        data=_valid_form_data(),
        files={"file": ("resume.pdf", _make_pdf_bytes([""]), "application/pdf")},
    )

    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "NO_EXTRACTABLE_TEXT"


def test_extract_reaches_not_implemented_boundary_after_real_extraction() -> None:
    request_id = "41a89594-09f8-45ca-a558-3f4e84ca838e"

    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": _valid_token(), "X-Request-Id": request_id},
        data=_valid_form_data(),
        files={"file": ("resume.pdf", _make_pdf_bytes(["Java, Spring Boot 경력 3년"]), "application/pdf")},
    )

    assert response.status_code == 501
    body = response.json()
    assert body["requestId"] == request_id
    assert body["error"]["errorType"] == "PII_LLM_PIPELINE_NOT_IMPLEMENTED"
    assert "1페이지" in body["error"]["message"]
