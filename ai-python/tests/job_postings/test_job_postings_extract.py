import httpx
from fastapi.testclient import TestClient

from app.guardrails.settings import get_internal_auth_settings
from app.job_postings.settings import get_job_posting_extraction_settings
from app.main import app
from app.providers.gemini import GeminiUnavailableError
from app.providers.gemini_client import get_gemini_job_posting_fallback_provider
from app.providers.ollama import OllamaProvider
from app.providers.ollama_client import get_ollama_job_posting_provider
from app.providers.settings import OllamaSettings

client = TestClient(app)

VALID_JOB_POSTING_ID = "33333333-3333-3333-3333-333333333333"
VALID_EXTRACTION_TASK_ID = "44444444-4444-4444-4444-444444444444"

VALID_JOB_POSTING_TEXT = (
    "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상, FastAPI 실무 경험. "
    "우대 조건: AWS 운영 경험."
)


def _valid_token() -> str:
    return get_internal_auth_settings().internal_service_token


def _valid_body(**overrides: str) -> dict:
    body = {
        "jobPostingId": VALID_JOB_POSTING_ID,
        "extractionTaskId": VALID_EXTRACTION_TASK_ID,
        "sourceText": VALID_JOB_POSTING_TEXT,
    }
    body.update(overrides)
    return body


def test_extract_rejects_missing_internal_token() -> None:
    response = client.post("/internal/v1/job-postings/extract", json=_valid_body())

    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INTERNAL_TOKEN_REQUIRED"


def test_extract_rejects_wrong_internal_token() -> None:
    response = client.post(
        "/internal/v1/job-postings/extract",
        headers={"X-Internal-Token": "wrong-token"},
        json=_valid_body(),
    )

    assert response.status_code == 401
    assert response.json()["error"]["errorType"] == "INTERNAL_UNAUTHORIZED"


def test_extract_rejects_invalid_ids_and_empty_text() -> None:
    response = client.post(
        "/internal/v1/job-postings/extract",
        headers={"X-Internal-Token": _valid_token()},
        json=_valid_body(jobPostingId="not-a-uuid", sourceText="   "),
    )

    assert response.status_code == 422
    body = response.json()
    assert body["error"]["errorType"] == "INVALID_EXTRACTION_REQUEST"
    field_errors = body["error"]["fieldErrors"]
    assert {"fieldName": "jobPostingId", "message": "UUID 형식이어야 합니다."} in field_errors
    assert {"fieldName": "sourceText", "message": "공백이 아닌 문자열이어야 합니다."} in field_errors


def test_extract_rejects_text_larger_than_configured_limit() -> None:
    def tiny_limit():
        settings = get_job_posting_extraction_settings()
        return settings.model_copy(update={"job_posting_extraction_max_text_length": 10})

    app.dependency_overrides[get_job_posting_extraction_settings] = tiny_limit
    try:
        response = client.post(
            "/internal/v1/job-postings/extract",
            headers={"X-Internal-Token": _valid_token()},
            json=_valid_body(),
        )
    finally:
        app.dependency_overrides.pop(get_job_posting_extraction_settings, None)

    assert response.status_code == 422
    body = response.json()
    assert body["error"]["errorType"] == "INVALID_EXTRACTION_REQUEST"
    assert {"fieldName": "sourceText", "message": "설정된 최대 길이를 초과했습니다."} in body["error"]["fieldErrors"]


def test_extract_succeeds_with_real_ollama(caplog) -> None:
    """실제 로컬 Ollama를 호출한다(mock 아님). Ollama 미기동/모델 미설치 시 실패해야 한다."""
    request_id = "55555555-5555-5555-5555-555555555555"
    token = _valid_token()

    with caplog.at_level("DEBUG"):
        response = client.post(
            "/internal/v1/job-postings/extract",
            headers={"X-Internal-Token": token, "X-Request-Id": request_id},
            json=_valid_body(),
        )

    assert response.status_code == 200, (
        "실제 Ollama 성공을 기대했다. Ollama 실행 여부와 OLLAMA_MODEL 설치 여부를 "
        f"확인해라. 응답: {response.text}"
    )

    body = response.json()
    assert body["requestId"] == request_id
    data = body["data"]
    assert data["jobPostingId"] == VALID_JOB_POSTING_ID
    assert data["extractionTaskId"] == VALID_EXTRACTION_TASK_ID
    assert data["status"] == "EXTRACTED"
    assert data["modelProvider"] == "ollama"
    assert "requiredSkills" in data["extraction"]
    assert "evidence" in data["extraction"]

    assert token not in response.text
    assert token not in caplog.text


def test_extract_reports_model_unavailable() -> None:
    """존재하지 않는 포트로 실제 연결 실패를 유도해 503을 확인한다(mock 아님).

    Gemini 폴백(2026-08-04)도 함께 실패하도록 오버라이드한다 — 안 그러면
    Ollama가 죽어도 Gemini가 대신 성공해서 200이 나와, "둘 다 죽었을 때"를
    검증하지 못한다.
    """

    async def unreachable_provider():
        async with httpx.AsyncClient(
            base_url="http://127.0.0.1:1",
            timeout=httpx.Timeout(connect=1, read=1, write=1, pool=1),
        ) as broken_client:
            yield OllamaProvider(client=broken_client, model_name=OllamaSettings().ollama_model)

    async def unavailable_gemini_fallback():
        class _AlwaysFailsGemini:
            provider_name = "gemini"
            model_name = "unavailable"

            async def extract_job_posting(self, source_text: str):
                raise GeminiUnavailableError("테스트: Gemini도 사용할 수 없음")

        yield _AlwaysFailsGemini()

    app.dependency_overrides[get_ollama_job_posting_provider] = unreachable_provider
    app.dependency_overrides[get_gemini_job_posting_fallback_provider] = unavailable_gemini_fallback
    try:
        response = client.post(
            "/internal/v1/job-postings/extract",
            headers={"X-Internal-Token": _valid_token()},
            json=_valid_body(),
        )
    finally:
        app.dependency_overrides.pop(get_ollama_job_posting_provider, None)
        app.dependency_overrides.pop(get_gemini_job_posting_fallback_provider, None)

    assert response.status_code == 503
    assert response.json()["error"]["errorType"] == "MODEL_UNAVAILABLE"


def test_extract_falls_back_to_gemini_when_ollama_unavailable() -> None:
    """Ollama가 죽어도 Gemini 폴백이 성공하면 200을 반환하고 modelProvider가
    gemini로 표시된다(2026-08-04) — 실제 응답에 어느 모델이 실제로 근거를
    만들었는지 정직하게 남긴다."""

    async def unreachable_provider():
        async with httpx.AsyncClient(
            base_url="http://127.0.0.1:1",
            timeout=httpx.Timeout(connect=1, read=1, write=1, pool=1),
        ) as broken_client:
            yield OllamaProvider(client=broken_client, model_name=OllamaSettings().ollama_model)

    app.dependency_overrides[get_ollama_job_posting_provider] = unreachable_provider
    try:
        response = client.post(
            "/internal/v1/job-postings/extract",
            headers={"X-Internal-Token": _valid_token()},
            json=_valid_body(),
        )
    finally:
        app.dependency_overrides.pop(get_ollama_job_posting_provider, None)

    assert response.status_code == 200, (
        "실제 Gemini 폴백 성공을 기대했다. GEMINI_API_KEY 설정을 확인해라. "
        f"응답: {response.text}"
    )
    data = response.json()["data"]
    assert data["modelProvider"] == "gemini"
    assert "requiredSkills" in data["extraction"]
