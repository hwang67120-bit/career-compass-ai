"""환경변수에서 모델 제공자(Ollama·Gemini) 연결 설정을 읽는다."""

from pydantic import AnyHttpUrl, PositiveFloat
from pydantic_settings import BaseSettings, SettingsConfigDict


class OllamaSettings(BaseSettings):
    """Ollama 연결에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    ollama_base_url: AnyHttpUrl = AnyHttpUrl("http://127.0.0.1:11434")
    ollama_model: str
    ollama_embedding_model: str
    ollama_connect_timeout_seconds: PositiveFloat = 3
    ollama_read_timeout_seconds: PositiveFloat = 120


class GeminiSettings(BaseSettings):
    """Gemini 연결에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    gemini_api_key: str
    gemini_model: str
    gemini_embedding_model: str
