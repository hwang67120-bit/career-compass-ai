"""로컬 Ollama의 구조화 출력 API를 호출한다."""

import json

import httpx
from pydantic import ValidationError

from app.schemas.job_posting import JobPostingExtraction


class OllamaUnavailableError(RuntimeError):
    """Ollama에 연결할 수 없거나 모델이 준비되지 않은 경우다."""


class OllamaResponseError(RuntimeError):
    """Ollama 응답이 프로젝트 스키마를 통과하지 못한 경우다."""


class OllamaProvider:
    """로컬 Ollama 모델을 호출한다."""

    def __init__(self, client: httpx.AsyncClient, model_name: str) -> None:
        self.client = client
        self.model_name = model_name

    async def verify_model(self) -> None:
        """Ollama 연결과 설정된 모델의 설치 여부를 확인한다."""
        try:
            response = await self.client.get("/api/tags")
            response.raise_for_status()
            models = response.json().get("models", [])
        except (httpx.HTTPError, ValueError) as error:
            raise OllamaUnavailableError(
                "Ollama 연결 상태를 확인할 수 없습니다."
            ) from error

        installed_names = {
            value
            for item in models
            for value in (item.get("name"), item.get("model"))
            if value
        }
        if self.model_name not in installed_names:
            raise OllamaUnavailableError(
                "설정된 Ollama 모델이 설치되어 있지 않습니다."
            )

    async def extract_job_posting(self, source_text: str) -> JobPostingExtraction:
        """채용 공고를 프로젝트 JSON 스키마로 추출한다.

        입력:
            source_text: 텍스트 추출과 안전 검사가 끝난 채용 공고 원문.

        반환:
            직무명, 필수·우대 기술과 원문 근거가 포함된 구조화 결과.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = JobPostingExtraction.model_json_schema()
        messages = [
            {
                "role": "system",
                "content": (
                    "제공된 채용 공고에 직접 존재하는 정보만 추출한다. "
                    "모든 추출값에는 원문 근거를 연결한다. "
                    "자료에 없는 값은 만들지 않는다."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
                    f"\n\n채용 공고 원문:\n{source_text}"
                ),
            },
        ]

        try:
            response = await self.client.post(
                "/api/chat",
                json={
                    "model": self.model_name,
                    "stream": False,
                    "format": schema,
                    "options": {"temperature": 0},
                    "messages": messages,
                },
            )
            response.raise_for_status()
            content = response.json()["message"]["content"]
            return JobPostingExtraction.model_validate_json(content)
        except httpx.TimeoutException as error:
            raise OllamaUnavailableError(
                "Ollama 응답 제한시간을 초과했습니다."
            ) from error
        except httpx.HTTPError as error:
            raise OllamaUnavailableError(
                "Ollama 요청에 실패했습니다."
            ) from error
        except (KeyError, TypeError, ValueError, ValidationError) as error:
            raise OllamaResponseError(
                "Ollama 응답이 프로젝트 스키마와 일치하지 않습니다."
            ) from error
