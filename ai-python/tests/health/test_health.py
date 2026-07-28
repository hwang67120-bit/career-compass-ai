from fastapi.testclient import TestClient

from app.guardrails.settings import get_internal_auth_settings
from app.main import app

client = TestClient(app)


def test_get_health_returns_up() -> None:
    token = get_internal_auth_settings().internal_service_token
    response = client.get(
        "/internal/v1/health",
        headers={"X-Internal-Token": token},
    )

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "model_ready": False,
    }
