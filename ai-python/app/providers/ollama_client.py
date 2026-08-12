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


def _build_client(settings: OllamaSettings) -> httpx.AsyncClient:
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


async def get_ollama_job_posting_provider():
    """`job-postings/extract`가 쓰는, 채용공고 직무명·기술용 모델(`OLLAMA_MODEL`) provider다."""
    settings = OllamaSettings()
    async with _build_client(settings) as client:
        yield OllamaProvider(client=client, model_name=settings.ollama_model)


async def get_ollama_job_posting_responsibility_provider():
    """`job-postings/extract`가 쓰는, 담당 업무 추출 전용 모델
    (`OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL`) provider다.

    직무명·기술(`OLLAMA_MODEL`)과 다른 모델을 쓴다 — 2026-08-03 확인:
    qwen2.5는 담당 업무 추출에서 evidence를 계속 못 채웠고 exaone3.5는
    성공했다(`docs/current-work.md` 참고).
    """
    settings = OllamaSettings()
    async with _build_client(settings) as client:
        yield OllamaProvider(
            client=client, model_name=settings.ollama_job_posting_responsibility_model
        )


async def get_ollama_evidence_judge_provider():
    """`job-evidence-similarities`가 쓰는, 근거 의미 비교 판정용 모델
    (`OLLAMA_EVIDENCE_JUDGE_MODEL`, 기본 qwen2.5) provider다."""
    settings = OllamaSettings()
    async with _build_client(settings) as client:
        yield OllamaProvider(client=client, model_name=settings.ollama_evidence_judge_model)


async def get_ollama_project_responsibility_provider():
    """`project-responsibility-extractions`가 쓰는, 저장소 담당 업무 추출용 모델
    (`OLLAMA_PROJECT_RESPONSIBILITY_MODEL`, 기본 qwen2.5) provider다."""
    settings = OllamaSettings()
    async with _build_client(settings) as client:
        yield OllamaProvider(client=client, model_name=settings.ollama_project_responsibility_model)
