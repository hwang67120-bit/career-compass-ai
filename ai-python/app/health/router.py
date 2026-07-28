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
