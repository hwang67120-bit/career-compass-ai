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


def test_livez_returns_alive_without_token() -> None:
    # 컨테이너 healthcheck용 — 토큰 없이 200이어야 한다(토큰 노출 방지).
    response = client.get("/livez")

    assert response.status_code == 200
    assert response.json() == {"status": "alive"}
