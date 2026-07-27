"""Gemini의 구조화 출력 API를 호출한다."""

import httpx
from google import genai
from google.genai import errors, types
from pydantic import ValidationError

from app.schemas.job_posting import JobPostingExtraction


class GeminiUnavailableError(RuntimeError):
    """Gemini에 연결할 수 없거나 서버 오류가 발생한 경우다."""


class GeminiResponseError(RuntimeError):
    """Gemini 응답이 프로젝트 스키마를 통과하지 못한 경우다."""


class GeminiProvider:
    """Gemini API를 호출한다."""

    def __init__(self, client: genai.Client, model_name: str) -> None:
        self.client = client
        self.model_name = model_name

    async def extract_job_posting(self, source_text: str) -> JobPostingExtraction:
        """채용 공고를 프로젝트 JSON 스키마로 추출한다.

        입력:
            source_text: 텍스트 추출과 안전 검사가 끝난 채용 공고 원문.

        반환:
            직무명, 필수·우대 기술과 원문 근거가 포함된 구조화 결과.

        예외:
            GeminiUnavailableError: 연결 실패 또는 Gemini 서버 오류.
            GeminiResponseError: 요청 거부 또는 응답이 프로젝트 스키마와 다른 경우.
        """
        try:
            response = await self.client.aio.models.generate_content(
                model=self.model_name,
                contents=source_text,
                config=types.GenerateContentConfig(
                    system_instruction=(
                        "제공된 채용 공고에 직접 존재하는 정보만 추출한다. "
                        "모든 추출값에는 원문 근거를 연결한다. "
                        "자료에 없는 값은 만들지 않는다."
                    ),
                    response_mime_type="application/json",
                    response_json_schema=JobPostingExtraction.model_json_schema(),
                    temperature=0,
                ),
            )
        except errors.ServerError as error:
            raise GeminiUnavailableError("Gemini 서버 오류가 발생했습니다.") from error
        except errors.ClientError as error:
            raise GeminiResponseError("Gemini 요청이 거부되었습니다.") from error
        except httpx.HTTPError as error:
            raise GeminiUnavailableError("Gemini 요청에 실패했습니다.") from error

        if response.text is None:
            raise GeminiResponseError("Gemini가 빈 응답을 반환했습니다.")

        try:
            return JobPostingExtraction.model_validate_json(response.text)
        except ValidationError as error:
            raise GeminiResponseError(
                "Gemini 응답이 프로젝트 스키마와 일치하지 않습니다."
            ) from error
