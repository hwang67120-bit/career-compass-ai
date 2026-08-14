from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.guardrails.internal_auth import verify_internal_token

router = APIRouter(
    prefix="/internal/v1",
    tags=["health"],
    dependencies=[Depends(verify_internal_token)],
)


class HealthResponse(BaseModel):
    status: Literal["UP"]
    model_ready: bool


@router.get("/health", response_model=HealthResponse)
def get_health() -> HealthResponse:
    return HealthResponse(status="UP", model_ready=False)


# 컨테이너 liveness — 인증 없음(프로세스 생존만 확인, 민감정보 없음).
# Docker healthcheck가 내부 토큰 없이 쓸 수 있도록 별도 라우터로 둔다.
liveness_router = APIRouter(tags=["health"])


class LivenessResponse(BaseModel):
    status: Literal["alive"]


@liveness_router.get("/livez", response_model=LivenessResponse)
def get_liveness() -> LivenessResponse:
    return LivenessResponse(status="alive")
