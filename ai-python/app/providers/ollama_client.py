"""요청마다 새 연결을 맺지 않도록 Ollama HTTP 클라이언트를 한 번만 만든다."""

from functools import lru_cache

import httpx

from app.providers.ollama import OllamaProvider
from app.providers.settings import OllamaSettings


@lru_cache
def _get_ollama_http_client() -> httpx.AsyncClient:
    settings = OllamaSettings()
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )
    return httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"),
        timeout=timeout,
    )


def get_ollama_provider() -> OllamaProvider:
    """FastAPI 의존성으로 주입할 `OllamaProvider`를 만든다."""
    settings = OllamaSettings()
    return OllamaProvider(client=_get_ollama_http_client(), model_name=settings.ollama_model)
