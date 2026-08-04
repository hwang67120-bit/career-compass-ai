"""FastAPI 의존성으로 주입할 Gemini 클라이언트를 만든다.

`app/providers/ollama_client.py`와 같은 이유(이벤트 루프 문제 회피)로 요청마다
새로 만든다.
"""

from google import genai

from app.providers.gemini import GeminiProvider
from app.providers.settings import GeminiSettings


async def get_gemini_job_posting_fallback_provider():
    """`job-postings/extract`가 Ollama 실패 시 쓰는 Gemini 폴백 provider다.

    채용공고는 공개 회사 정보라 개인정보 가드레일이 적용되지 않는다
    (`contracts/job-posting-extraction.md` 서문) — 이력서·희망 직무처럼
    Gemini 무료 등급 데이터 제한 정책 확인이 필요한 데이터가 아니다.
    다만 무료 등급 요청 제한으로 이따금 실패할 수 있다(확인 필요).
    """
    settings = GeminiSettings()
    client = genai.Client(api_key=settings.gemini_api_key)
    yield GeminiProvider(client=client, model_name=settings.gemini_model)
