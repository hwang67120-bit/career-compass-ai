from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_get_health_rejects_missing_token() -> None:
    response = client.get("/internal/v1/health")

    assert response.status_code == 422


def test_get_health_rejects_wrong_token() -> None:
    response = client.get(
        "/internal/v1/health",
        headers={"X-Internal-Token": "wrong-token"},
    )

    assert response.status_code == 401
