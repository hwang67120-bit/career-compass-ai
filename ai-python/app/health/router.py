from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/internal/v1", tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["UP"]
    model_ready: bool


@router.get("/health", response_model=HealthResponse)
def get_health() -> HealthResponse:
    return HealthResponse(status="UP", model_ready=False)
