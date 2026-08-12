"""환경변수에서 모델 제공자(Ollama·Gemini) 연결 설정을 읽는다."""

from pydantic import AnyHttpUrl, PositiveFloat
from pydantic_settings import BaseSettings, SettingsConfigDict


class OllamaSettings(BaseSettings):
    """Ollama 연결에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    ollama_base_url: AnyHttpUrl = AnyHttpUrl("http://127.0.0.1:11434")
    ollama_model: str
    ollama_job_posting_responsibility_model: str
    ollama_embedding_model: str
    # 근거 의미 비교(LLM-as-judge)용 모델. 2026-08-12 품질 게이트 통과 모델.
    ollama_evidence_judge_model: str = "qwen2.5:latest"
    # 저장소 담당 업무 근거 추출용 모델(project-responsibility-extraction 계약).
    ollama_project_responsibility_model: str = "qwen2.5:latest"
    ollama_connect_timeout_seconds: PositiveFloat = 3
    ollama_read_timeout_seconds: PositiveFloat = 120


class GeminiSettings(BaseSettings):
    """Gemini 연결에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    gemini_api_key: str
    gemini_model: str
    gemini_embedding_model: str


class GitHubRepositorySettings(BaseSettings):
    """저장소 코드 분석을 위한 GitHub API 연결 설정이다.

    인증 토큰 없이 공개 저장소만 조회한다(Java의 GitHubRestClient와 동일한
    전제). 비인증 요청은 시간당 60회로 제한된다.
    """

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    github_api_base_url: AnyHttpUrl = AnyHttpUrl("https://api.github.com")
    github_raw_base_url: AnyHttpUrl = AnyHttpUrl("https://raw.githubusercontent.com")
    github_api_connect_timeout_seconds: PositiveFloat = 3
    github_api_read_timeout_seconds: PositiveFloat = 8
