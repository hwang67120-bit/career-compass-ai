from fastapi.testclient import TestClient

from app.guardrails.settings import get_internal_auth_settings
from app.main import app
from app.observability.metrics import metrics_snapshot, record_stage, reset_metrics
from app.services.performance_tracking import StageOperation, measure_stage

client = TestClient(app)


def _token() -> str:
    return get_internal_auth_settings().internal_service_token


def test_record_and_snapshot_aggregates() -> None:
    reset_metrics()
    record_stage("ollama.extract_job_posting", 100.0, False, 50, 10)
    record_stage("ollama.extract_job_posting", 300.0, True, 20, 0)

    stats = metrics_snapshot()["ollama.extract_job_posting"]
    assert stats["calls"] == 2
    assert stats["errors"] == 1
    assert stats["errorRate"] == 0.5
    assert stats["promptTokensTotal"] == 70
    assert stats["completionTokensTotal"] == 10
    assert stats["p95LatencyMs"] is not None
    assert stats["avgLatencyMs"] == 200.0


def test_measure_stage_feeds_registry() -> None:
    reset_metrics()
    with measure_stage("ollama", StageOperation.EMBED_JOB_POSTING):
        pass

    snapshot = metrics_snapshot()
    assert snapshot["ollama.embed_job_posting"]["calls"] == 1


def test_metrics_endpoint_requires_internal_token() -> None:
    response = client.get("/internal/v1/metrics")

    assert response.status_code == 422
    assert response.json()["error"]["errorType"] == "INTERNAL_TOKEN_REQUIRED"


def test_metrics_endpoint_rejects_wrong_token() -> None:
    response = client.get(
        "/internal/v1/metrics", headers={"X-Internal-Token": "wrong-token"}
    )

    assert response.status_code == 401
    assert response.json()["error"]["errorType"] == "INTERNAL_UNAUTHORIZED"


def test_metrics_endpoint_returns_snapshot_with_token() -> None:
    reset_metrics()
    record_stage("gemini.extract_job_posting", 200.0, False, 100, 30)

    response = client.get(
        "/internal/v1/metrics", headers={"X-Internal-Token": _token()}
    )

    assert response.status_code == 200
    stages = response.json()["stages"]
    assert stages["gemini.extract_job_posting"]["calls"] == 1
    assert stages["gemini.extract_job_posting"]["promptTokensTotal"] == 100
