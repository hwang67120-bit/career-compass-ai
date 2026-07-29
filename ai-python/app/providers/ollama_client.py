"""FastAPI 의존성으로 주입할 Ollama HTTP 클라이언트를 만든다.

이전에는 `lru_cache`로 프로세스 전체에서 `httpx.AsyncClient`를 하나만
재사용했는데, 이 클라이언트가 특정 이벤트 루프에 묶여 있어서 이벤트 루프가
바뀌면(`TestClient`가 요청마다 새 이벤트 루프를 쓰는 경우 등) "Event loop is
closed" 오류가 실제로 발생했다. 요청마다 새로 만드는 쪽이 약간의 연결
재사용 이득보다 안전하다.
"""

import httpx

from app.providers.ollama import OllamaProvider
from app.providers.settings import OllamaSettings


async def get_ollama_provider():
    """FastAPI 의존성으로 주입할 `OllamaProvider`를 만든다.

    요청이 끝나면 클라이언트를 닫는다(`async with`) — 연결을 프로세스
    전체에서 들고 있지 않는다.
    """
    settings = OllamaSettings()
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )
    async with httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"),
        timeout=timeout,
    ) as client:
        yield OllamaProvider(client=client, model_name=settings.ollama_resume_model)
