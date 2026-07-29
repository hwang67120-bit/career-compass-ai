"""Java 서버가 호출하는 Python 분석 API의 진입점이다."""

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.documents.router import router as documents_router
from app.health.router import router as health_router
from app.schemas.envelope import error_envelope, resolve_request_id

settings = get_settings()
app = FastAPI(title=settings.app_name)
app.include_router(health_router)
app.include_router(documents_router)


@app.exception_handler(RequestValidationError)
async def handle_request_validation_error(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """요청 검증 실패를 계약 봉투 형식으로 반환한다.

    `X-Internal-Token` 헤더 누락은 `INTERNAL_TOKEN_REQUIRED`로, 그 외
    multipart 필드 오류는 `INVALID_EXTRACTION_REQUEST`로 구분한다.
    """
    request_id = resolve_request_id(request.headers.get("x-request-id"))
    is_missing_token = any(
        error["loc"][-1] == "x-internal-token" for error in exc.errors()
    )
    if is_missing_token:
        return JSONResponse(
            status_code=422,
            content=error_envelope(
                request_id, "INTERNAL_TOKEN_REQUIRED", "X-Internal-Token 헤더가 필요합니다."
            ),
        )
    return JSONResponse(
        status_code=422,
        content=error_envelope(
            request_id,
            "INVALID_EXTRACTION_REQUEST",
            "요청 필드가 계약을 따르지 않습니다.",
            field_errors=[str(error) for error in exc.errors()],
        ),
    )


@app.exception_handler(HTTPException)
async def handle_http_exception(request: Request, exc: HTTPException) -> JSONResponse:
    """내부 토큰 불일치(401)만 계약 봉투 형식으로 재구성한다."""
    if exc.status_code != 401:
        return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})

    request_id = resolve_request_id(request.headers.get("x-request-id"))
    return JSONResponse(
        status_code=401,
        content=error_envelope(
            request_id, "INTERNAL_UNAUTHORIZED", "내부 서비스 토큰이 일치하지 않습니다."
        ),
    )
