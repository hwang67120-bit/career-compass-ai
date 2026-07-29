"""Java-Python 내부 API가 공통으로 쓰는 응답 봉투(envelope)를 정의한다.

계약: contracts/document-extraction.md 4절, 6절.
"""

from datetime import datetime, timezone
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class ErrorDetail(BaseModel):
    """봉투의 오류 상세 정보다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    error_type: str = Field(alias="errorType")
    message: str
    field_errors: list[str] = Field(default_factory=list, alias="fieldErrors")
    retryable: bool = False


class ApiEnvelope(BaseModel):
    """모든 내부 API 응답이 따르는 공통 봉투 형식이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    request_id: str = Field(alias="requestId")
    data: dict | None = None
    error: ErrorDetail | None = None
    timestamp: str


def resolve_request_id(x_request_id: str | None) -> str:
    """`X-Request-Id` 헤더를 검증하고 응답에 쓸 요청 ID를 정한다.

    유효한 UUID 문자열이면 그대로 반환하고(계약: 그대로 echo), 헤더가
    없거나 UUID 형식이 아니면 새 UUID를 생성한다.
    """
    if x_request_id:
        try:
            UUID(x_request_id)
        except ValueError:
            pass
        else:
            return x_request_id
    return str(uuid4())


def current_timestamp() -> str:
    """UTC RFC 3339 형식의 현재 시각 문자열을 반환한다."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def success_envelope(request_id: str, data: dict) -> dict:
    """성공 응답 봉투를 만든다."""
    envelope = ApiEnvelope(request_id=request_id, data=data, error=None, timestamp=current_timestamp())
    return envelope.model_dump(by_alias=True)


def error_envelope(
    request_id: str,
    error_type: str,
    message: str,
    retryable: bool = False,
    field_errors: list[str] | None = None,
) -> dict:
    """실패 응답 봉투를 만든다."""
    envelope = ApiEnvelope(
        request_id=request_id,
        data=None,
        error=ErrorDetail(
            error_type=error_type,
            message=message,
            field_errors=field_errors or [],
            retryable=retryable,
        ),
        timestamp=current_timestamp(),
    )
    return envelope.model_dump(by_alias=True)
