# syntax=docker/dockerfile:1
# ai-python(FastAPI 분석 워커) 컨테이너. 요구사항: docs/operations/python-docker-requirements.md
# Ollama는 이미지에 넣지 않는다 — 별도 모델 머신을 OLLAMA_BASE_URL로 호출.

# --- builder: uv로 프로덕션 의존성만 설치 ---
FROM python:3.10-slim AS builder
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/
ENV UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=0 \
    UV_PROJECT_ENVIRONMENT=/app/.venv
WORKDIR /app
# 의존성 레이어 캐시: 매니페스트 먼저 복사 → dev 그룹 제외로 설치
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

# --- runtime: 슬림 이미지 + 비-root ---
FROM python:3.10-slim AS runtime
RUN useradd -m -u 10001 appuser
WORKDIR /app
COPY --from=builder /app/.venv /app/.venv
COPY app ./app
ENV PATH="/app/.venv/bin:$PATH"
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8000

# curl 없는 슬림 이미지라 Python stdlib로 점검. 토큰 없는 /livez 사용(토큰 노출 방지).
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/livez', timeout=3).status==200 else 1)"

# uvicorn은 .venv/bin에 있음(위 PATH). Ollama 꺼져 있어도 API는 기동됨.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
