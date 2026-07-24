"""Java 서버가 호출하는 Python 분석 API의 진입점이다."""

from fastapi import FastAPI

from app.config import get_settings

settings = get_settings()
app = FastAPI(title=settings.app_name)


@app.get("/health")
def check_health() -> dict[str, str]:
    """서버가 요청을 처리할 수 있는 상태인지 확인한다."""
    return {"status": "ok"}
