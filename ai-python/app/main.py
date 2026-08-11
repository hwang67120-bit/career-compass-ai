"""Java 서버가 호출하는 Python 분석 API의 진입점이다."""

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.health.router import router as health_router
from app.job_postings.router import router as job_postings_router
from app.providers.ollama_process import ensure_ollama_running
from app.schemas.envelope import FieldError, error_envelope, resolve_request_id

# uvicorn 기본 로깅 설정은 uvicorn.* 로거만 다루고 root 로거는 그대로 둔다(레벨
# WARNING). app.* 로거는 root로 전파되는데 root에 핸들러가 없으면 INFO 로그가
# 조용히 버려진다 — app.performance, app.job_posting_extraction의 INFO 로그가
# 안 보이던 원인이었다.
logging.basicConfig(level=logging.INFO)

settings = get_settings()
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """서버 시작 시 Ollama가 응답하지 않으면 자동으로 띄워본다.

    Gemini는 API 키만 있으면 되지만 Ollama는 별도 로컬 프로세스가 떠 있어야
    한다. 여기서 실패해도(설정 누락, 실행 파일 없음 등) 서버 시작은 계속
    한다 — Ollama가 필요 없는 API까지 막을 이유는 없다.
    """
    try:
        ollama_ready = await asyncio.to_thread(ensure_ollama_running)
    except Exception:  # noqa: BLE001 — 설정 누락 등으로 서버 시작을 막지 않는다
        logger.warning("Ollama 자동 실행 확인 중 오류가 발생했습니다.", exc_info=True)
    else:
        if ollama_ready:
            logger.info("Ollama 연결을 확인했습니다.")
        else:
            logger.warning(
                "Ollama를 자동으로 켜지 못했습니다. 설치 여부와 PATH를 확인하세요."
            )
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.include_router(health_router)
app.include_router(job_postings_router)


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
            field_errors=[
                FieldError(
                    field_name=".".join(str(part) for part in error["loc"]),
                    message=error["msg"],
                )
                for error in exc.errors()
            ],
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
