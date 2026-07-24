from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_get_health_returns_up() -> None:
    response = client.get("/internal/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "model_ready": False,
    }
