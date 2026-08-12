import httpx
from fastapi.testclient import TestClient

from app.guardrails.settings import get_internal_auth_settings
from app.main import app
from app.providers.ollama import OllamaProvider
from app.providers.ollama_client import get_ollama_project_responsibility_provider
from app.providers.settings import OllamaSettings
from app.schemas.project_responsibility import (
    ProjectResponsibilityCandidate,
    ProjectResponsibilityExtraction,
)

client = TestClient(app)

_URL = "/internal/v1/project-responsibility-extractions"


def _valid_token() -> str:
    return get_internal_auth_settings().internal_service_token


def _valid_body(**overrides) -> dict:
    body = {
        "extractionTaskId": "11111111-1111-1111-1111-111111111111",
        "projectSourceId": "22222222-2222-2222-2222-222222222222",
        "selectedTechnologyTags": [
            {"technologyTagId": "tag-spring", "canonicalName": "Spring Boot"}
        ],
        "repositorySnapshot": {
            "sourceUrl": "https://github.com/example/sample",
            "fetchedAt": "2026-08-12T08:00:00Z",
            "repositoryVersion": "abc123",
            "readmes": [
                {"evidenceId": "readme-1", "path": "README.md", "text": "Spring Boot로 주문 API를 구현했습니다."}
            ],
            "files": [],
        },
    }
    body.update(overrides)
    return body


class _FakeExtractionProvider:
    def __init__(self, candidates) -> None:
        self._extraction = ProjectResponsibilityExtraction(responsibilities=candidates)
        self.model_name = "qwen2.5:latest"

    async def extract_project_responsibilities(self, evidence_items, selected_tech_names):
        return self._extraction


def _provider_override(candidates):
    async def _provider():
        yield _FakeExtractionProvider(candidates)

    return _provider


def _unreachable_provider():
    async def _provider():
        async with httpx.AsyncClient(
            base_url="http://127.0.0.1:1",
            timeout=httpx.Timeout(connect=1, read=1, write=1, pool=1),
        ) as broken:
            yield OllamaProvider(
                client=broken, model_name=OllamaSettings().ollama_project_responsibility_model
            )

    return _provider


def test_rejects_missing_internal_token() -> None:
    response = client.post(_URL, json=_valid_body())
    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INTERNAL_TOKEN_REQUIRED"


def test_rejects_empty_selected_tags() -> None:
    response = client.post(
        _URL,
        headers={"X-Internal-Token": _valid_token()},
        json=_valid_body(selectedTechnologyTags=[]),
    )
    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INVALID_PROJECT_RESPONSIBILITY_EXTRACTION_REQUEST"


def test_succeeds_with_fake_provider() -> None:
    app.dependency_overrides[get_ollama_project_responsibility_provider] = _provider_override(
        [ProjectResponsibilityCandidate(text="Spring Boot 기반 주문 API 구현", source_evidence_ids=["readme-1"])]
    )
    try:
        response = client.post(
            _URL, headers={"X-Internal-Token": _valid_token()}, json=_valid_body()
        )
    finally:
        app.dependency_overrides.pop(get_ollama_project_responsibility_provider, None)

    assert response.status_code == 200, response.text
    data = response.json()["data"]
    assert data["extractionTaskId"] == "11111111-1111-1111-1111-111111111111"
    assert data["repositoryVersion"] == "abc123"
    assert data["technologyEvidenceCandidates"][0]["technologyTagId"] == "tag-spring"
    assert data["technologyEvidenceCandidates"][0]["findingStatus"] == "FOUND"
    assert data["responsibilityEvidenceCandidates"][0]["category"] == "PROJECT_RESPONSIBILITY"
    assert data["modelExecution"]["stage"] == "PROJECT_RESPONSIBILITY_EXTRACTION"
    assert data["modelExecution"]["provider"] == "OLLAMA"


def test_reports_model_unavailable() -> None:
    app.dependency_overrides[get_ollama_project_responsibility_provider] = _unreachable_provider()
    try:
        response = client.post(
            _URL, headers={"X-Internal-Token": _valid_token()}, json=_valid_body()
        )
    finally:
        app.dependency_overrides.pop(get_ollama_project_responsibility_provider, None)

    assert response.status_code == 503
    assert response.json()["error"]["errorType"] == "PROJECT_RESPONSIBILITY_EXTRACTION_MODEL_UNAVAILABLE"
