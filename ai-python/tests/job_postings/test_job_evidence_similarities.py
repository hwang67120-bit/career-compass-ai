import httpx
from fastapi.testclient import TestClient

from app.guardrails.settings import get_internal_auth_settings
from app.main import app
from app.providers.ollama import OllamaProvider
from app.providers.ollama_client import get_ollama_evidence_judge_provider
from app.providers.settings import OllamaSettings
from app.schemas.job_evidence_similarity import JudgeVerdict

client = TestClient(app)


def _valid_token() -> str:
    return get_internal_auth_settings().internal_service_token


def _valid_body(**overrides) -> dict:
    body = {
        "comparisonTaskId": "11111111-1111-1111-1111-111111111111",
        "jobAnalysisId": "22222222-2222-2222-2222-222222222222",
        "jobPostingId": "33333333-3333-3333-3333-333333333333",
        "jobEvidence": [
            {"evidenceId": "job-1", "category": "RESPONSIBILITY", "text": "커머스 백엔드 API 설계·운영"}
        ],
        "userEvidence": [
            {
                "evidenceId": "user-a",
                "projectSourceId": "ps-1",
                "category": "PROJECT_RESPONSIBILITY",
                "text": "Redis 캐시로 주문 API 지연을 줄임",
            }
        ],
    }
    body.update(overrides)
    return body


class _AlwaysVerdictProvider:
    def __init__(self, verdict: JudgeVerdict) -> None:
        self.verdict = verdict
        self.model_name = "qwen2.5:latest"

    async def judge_evidence_relation(self, job_text, user_items):
        return self.verdict


def _judge_override(verdict: JudgeVerdict):
    async def _provider():
        yield _AlwaysVerdictProvider(verdict)

    return _provider


def _unreachable_judge_provider():
    async def _provider():
        async with httpx.AsyncClient(
            base_url="http://127.0.0.1:1",
            timeout=httpx.Timeout(connect=1, read=1, write=1, pool=1),
        ) as broken:
            yield OllamaProvider(
                client=broken, model_name=OllamaSettings().ollama_evidence_judge_model
            )

    return _provider


def test_similarity_rejects_missing_internal_token() -> None:
    response = client.post("/internal/v1/job-evidence-similarities", json=_valid_body())
    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INTERNAL_TOKEN_REQUIRED"


def test_similarity_rejects_wrong_category() -> None:
    body = _valid_body(
        jobEvidence=[{"evidenceId": "job-1", "category": "PROJECT_RESPONSIBILITY", "text": "x"}]
    )
    response = client.post(
        "/internal/v1/job-evidence-similarities",
        headers={"X-Internal-Token": _valid_token()},
        json=body,
    )
    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INVALID_SIMILARITY_REQUEST"


def test_similarity_succeeds_with_fake_provider() -> None:
    app.dependency_overrides[get_ollama_evidence_judge_provider] = _judge_override(
        JudgeVerdict(best_match_user_evidence_id="user-a", judgment="RELATED")
    )
    try:
        response = client.post(
            "/internal/v1/job-evidence-similarities",
            headers={"X-Internal-Token": _valid_token()},
            json=_valid_body(),
        )
    finally:
        app.dependency_overrides.pop(get_ollama_evidence_judge_provider, None)

    assert response.status_code == 200, response.text
    data = response.json()["data"]
    assert data["method"] == "LLM_JUDGE"
    assert data["status"] == "CALCULATED"
    assert data["results"][0]["bestMatchUserEvidenceId"] == "user-a"
    assert data["results"][0]["judgment"] == "RELATED"
    assert data["modelExecution"] == {
        "stage": "EVIDENCE_SEMANTIC_COMPARISON",
        "provider": "OLLAMA",
        "model": "qwen2.5:latest",
    }


def test_similarity_reports_model_unavailable() -> None:
    app.dependency_overrides[get_ollama_evidence_judge_provider] = _unreachable_judge_provider()
    try:
        response = client.post(
            "/internal/v1/job-evidence-similarities",
            headers={"X-Internal-Token": _valid_token()},
            json=_valid_body(),
        )
    finally:
        app.dependency_overrides.pop(get_ollama_evidence_judge_provider, None)

    assert response.status_code == 503
    assert response.json()["error"]["errorType"] == "SEMANTIC_COMPARISON_MODEL_UNAVAILABLE"
