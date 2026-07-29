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
    field_errors = body["error"]["fieldErrors"]
    assert len(field_errors) == 2
    assert {"fieldName": "documentId", "message": "UUID 형식이어야 합니다."} in field_errors
    assert {"fieldName": "documentType", "message": "RESUME 또는 PORTFOLIO여야 합니다."} in field_errors


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


def test_extract_never_leaks_pii_or_internal_token_into_logs_or_response(caplog) -> None:
    """Gate 1 요건: 개인정보·내부 토큰·모델 원문 응답이 로그나 응답에 남지 않아야 한다."""
    fake_email = "hong.gildong.secret@example-fake.com"
    fake_phone = "010-9999-8888"
    fake_rrn = "901231-1234567"
    token = _valid_token()

    text = (
        f"홍길동. 이메일: {fake_email}. 전화번호: {fake_phone}. "
        f"주민등록번호: {fake_rrn}. Java, Spring Boot 3년 경력. "
        "ABC회사에서 결제 시스템을 개발했다."
    )

    with caplog.at_level("DEBUG"):
        response = client.post(
            "/internal/v1/documents/extract",
            headers={"X-Internal-Token": token, "X-Request-Id": "log-leak-test"},
            data=_valid_form_data(),
            files={"file": ("resume.pdf", _make_pdf_bytes([text]), "application/pdf")},
        )

    assert response.status_code in (200, 502, 503)

    secrets = [fake_email, fake_phone, fake_rrn, token]
    for secret in secrets:
        assert secret not in response.text
        assert secret not in caplog.text


def test_extract_calls_real_ollama_and_honestly_reports_the_outcome() -> None:
    """실제 로컬 Ollama를 호출한다(mock 아님). Ollama가 꺼져 있으면 실패한다.

    확인 필요: 지금 임시로 설정된 모델(OLLAMA_MODEL)은 근거(evidence) 연결
    규칙을 매번 만족시키지는 못한다 — 성공(200)하거나, 근거 검증 실패로
    502 MODEL_RESPONSE_INVALID를 정직하게 반환하는 경우가 둘 다 실제로
    관찰된다. 둘 다 "가짜 성공"이 아니라 정상적인 계약 동작이므로 둘 다
    허용하되, 완전히 다른 실패(요청 오류 등)는 여전히 실패로 처리한다.
    """
    request_id = "41a89594-09f8-45ca-a558-3f4e84ca838e"

    response = client.post(
        "/internal/v1/documents/extract",
        headers={"X-Internal-Token": _valid_token(), "X-Request-Id": request_id},
        data=_valid_form_data(),
        files={
            "file": (
                "resume.pdf",
                _make_pdf_bytes(
                    ["백엔드 개발자 김철수. Java, Spring Boot 3년 경력. ABC회사에서 결제 시스템을 개발했다."]
                ),
                "application/pdf",
            )
        },
    )

    assert response.status_code in (200, 502)
    body = response.json()
    assert body["requestId"] == request_id

    if response.status_code == 200:
        data = body["data"]
        assert data["documentId"] == VALID_DOCUMENT_ID
        assert data["extractionTaskId"] == VALID_EXTRACTION_TASK_ID
        assert data["status"] == "EXTRACTED"
        assert data["modelProvider"] == "ollama"
        assert data["piiRemoved"] is True
        assert "skills" in data["candidate"]
        assert "evidence" in data["candidate"]
    else:
        assert body["error"]["errorType"] == "MODEL_RESPONSE_INVALID"
