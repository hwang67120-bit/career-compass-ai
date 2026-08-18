"""FastAPI 의존성으로 주입할 Gemini 클라이언트를 만든다.

`GEMINI_API_KEY` 등이 설정돼 있지 않아도 Ollama만으로 채용공고 추출 API가
정상 동작해야 한다(2026-08-04, PR #45 리뷰 반영) — 설정 부재를 예외가 아니라
`None`으로 다룬다. `get_gemini_settings_if_configured`는 매 요청마다 호출돼도
가벼운 설정 확인일 뿐 실제 Gemini 클라이언트(네트워크 자원)를 만들지 않는다.

`build_gemini_job_posting_fallback_provider`는 실제 클라이언트를 만드는
쪽이다 — `app/services/job_posting_extraction.py`가 Ollama 재시도까지
실패했을 때만 호출해서 지연 생성하고, `async with`가 끝나면 클라이언트를
정리한다.
"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from google import genai
from pydantic import ValidationError

from app.providers.gemini import GeminiProvider
from app.providers.settings import GeminiSettings


def get_gemini_settings_if_configured() -> GeminiSettings | None:
    """Gemini 환경변수가 있으면 설정 객체를, 없거나 비어 있으면 `None`을 반환한다(예외로 죽지 않음).

    환경변수가 빈 문자열로 존재하는 경우(예: 서버 `.env`의 `GEMINI_API_KEY=`)도
    미설정으로 다룬다 — 그렇지 않으면 폴백에서 `genai.Client("")`가
    `ValueError`로 죽는다.
    """
    try:
        settings = GeminiSettings()
    except ValidationError:
        return None
    if not settings.gemini_api_key.strip() or not settings.gemini_model.strip():
        return None
    return settings


@asynccontextmanager
async def build_gemini_job_posting_fallback_provider(
    settings: GeminiSettings,
) -> AsyncIterator[GeminiProvider]:
    """Gemini 클라이언트를 실제로 만들고, 사용이 끝나면 정리한다.

    `job-postings/extract`가 Ollama 실패 시에만 호출하는 폴백 provider다.
    """
    client = genai.Client(api_key=settings.gemini_api_key)
    try:
        yield GeminiProvider(client=client, model_name=settings.gemini_model)
    finally:
        client.close()
