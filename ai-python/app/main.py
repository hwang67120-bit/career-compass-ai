"""Java 서버가 호출하는 Python 분석 API의 진입점이다."""

from fastapi import FastAPI

from app.config import get_settings
from app.health.router import router as health_router

settings = get_settings()
app = FastAPI(title=settings.app_name)
app.include_router(health_router)
