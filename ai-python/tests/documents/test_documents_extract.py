import httpx
from fpdf import FPDF
from fastapi.testclient import TestClient

from app.documents.settings import get_document_extraction_settings
from app.guardrails.settings import get_internal_auth_settings
from app.main import app
from app.providers.ollama import OllamaProvider
from app.providers.ollama_client import get_ollama_provider
from app.providers.settings import OllamaSettings

client = TestClient(app)

KOREAN_FONT_PATH = "C:/Windows/Fonts/malgun.ttf"

VALID_DOCUMENT_ID = "11111111-1111-1111-1111-111111111111"
VALID_EXTRACTION_TASK_ID = "22222222-2222-2222-2222-222222222222"

FAKE_EMAIL = "hong.gildong.secret@example-fake.com"
FAKE_PHONE = "010-9999-8888"
FAKE_RRN = "901231-1234567"
PII_SECRETS = [FAKE_EMAIL, FAKE_PHONE, FAKE_RRN]

RESUME_TEXT_WITH_PII = (
    f"백엔드 개발자 김철수. 이메일: {FAKE_EMAIL}. 전화번호: {FAKE_PHONE}. "
    f"주민등록번호: {FAKE_RRN}. Java, Spring Boot 3년 경력. "
    "ABC회사에서 결제 시스템을 개발했다."
)


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


def test_extract_succeeds_with_real_ollama_and_leaks_no_pii(caplog) -> None:
    """실제 로컬 Ollama를 호출한다(mock 아님). 반드시 200을 기대한다.

    이 테스트는 Ollama가 꺼져 있거나 채택 모델(OLLAMA_RESUME_MODEL,
    exaone3.5:latest)이 설치되어 있지 않으면 실패해야 한다 — 실행 환경
    문제를 조용히 통과시키지 않는다.
    """
    request_id = "41a89594-09f8-45ca-a558-3f4e84ca838e"
    token = _valid_token()

    with caplog.at_level("DEBUG"):
        response = client.post(
            "/internal/v1/documents/extract",
            headers={"X-Internal-Token": token, "X-Request-Id": request_id},
            data=_valid_form_data(),
            files={"file": ("resume.pdf", _make_pdf_bytes([RESUME_TEXT_WITH_PII]), "application/pdf")},
        )

    assert response.status_code == 200, (
        "실제 Ollama 성공을 기대했다. Ollama 실행 여부와 OLLAMA_RESUME_MODEL "
        f"설치 여부를 확인해라. 응답: {response.text}"
    )

    body = response.json()
    assert body["requestId"] == request_id
    data = body["data"]
    assert data["documentId"] == VALID_DOCUMENT_ID
    assert data["extractionTaskId"] == VALID_EXTRACTION_TASK_ID
    assert data["status"] == "EXTRACTED"
    assert data["modelProvider"] == "ollama"
    assert data["piiRemoved"] is True
    assert "skills" in data["candidate"]
    assert "evidence" in data["candidate"]

    for secret in [*PII_SECRETS, token]:
        assert secret not in response.text
        assert secret not in caplog.text


def test_extract_reports_model_unavailable_and_leaks_no_pii(caplog) -> None:
    """Ollama에 연결할 수 없을 때 503 MODEL_UNAVAILABLE을 반환하고, 이때도 PII·토큰이 안 새는지 확인한다.

    실제 mock 없이, 존재하지 않는 포트로 실제 연결을 시도해 진짜 연결
    실패를 유도한다.
    """
    token = _valid_token()

    async def unreachable_ollama_provider():
        settings = OllamaSettings()
        async with httpx.AsyncClient(
            base_url="http://127.0.0.1:1", timeout=httpx.Timeout(connect=1, read=1, write=1, pool=1)
        ) as broken_client:
            yield OllamaProvider(client=broken_client, model_name=settings.ollama_resume_model)

    app.dependency_overrides[get_ollama_provider] = unreachable_ollama_provider
    try:
        with caplog.at_level("DEBUG"):
            response = client.post(
                "/internal/v1/documents/extract",
                headers={"X-Internal-Token": token, "X-Request-Id": "model-unavailable-test"},
                data=_valid_form_data(),
                files={"file": ("resume.pdf", _make_pdf_bytes([RESUME_TEXT_WITH_PII]), "application/pdf")},
            )
    finally:
        app.dependency_overrides.pop(get_ollama_provider, None)

    assert response.status_code == 503
    assert response.json()["error"]["errorType"] == "MODEL_UNAVAILABLE"

    for secret in [*PII_SECRETS, token]:
        assert secret not in response.text
        assert secret not in caplog.text


def test_extract_reports_model_unavailable_when_model_not_installed(caplog) -> None:
    """실제 Ollama 서버는 살아있지만, 설정된 모델이 설치돼 있지 않은 경우다.

    실제 로컬 Ollama에 이 이름의 모델이 설치돼 있으면 안 된다(임의 이름).
    """
    token = _valid_token()

    async def provider_with_missing_model():
        settings = OllamaSettings()
        timeout = httpx.Timeout(
            connect=settings.ollama_connect_timeout_seconds,
            read=settings.ollama_read_timeout_seconds,
            write=10.0,
            pool=5.0,
        )
        async with httpx.AsyncClient(
            base_url=str(settings.ollama_base_url).rstrip("/"), timeout=timeout
        ) as real_client:
            yield OllamaProvider(client=real_client, model_name="this-model-does-not-exist:latest")

    app.dependency_overrides[get_ollama_provider] = provider_with_missing_model
    try:
        with caplog.at_level("DEBUG"):
            response = client.post(
                "/internal/v1/documents/extract",
                headers={"X-Internal-Token": token, "X-Request-Id": "model-not-installed-test"},
                data=_valid_form_data(),
                files={"file": ("resume.pdf", _make_pdf_bytes([RESUME_TEXT_WITH_PII]), "application/pdf")},
            )
    finally:
        app.dependency_overrides.pop(get_ollama_provider, None)

    assert response.status_code == 503
    assert response.json()["error"]["errorType"] == "MODEL_UNAVAILABLE"

    for secret in [*PII_SECRETS, token]:
        assert secret not in response.text
        assert secret not in caplog.text
