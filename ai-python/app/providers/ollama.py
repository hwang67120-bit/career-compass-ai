"""로컬 Ollama의 구조화 출력 API를 호출한다."""

import json

import httpx
from pydantic import ValidationError

from app.schemas.job_posting import JobPostingExtraction
from app.schemas.job_search_keywords import GeneratedKeywordSuggestions
from app.schemas.profile_candidate import ProfileCandidatePayload

_JOB_SEARCH_KEYWORD_SYSTEM_PROMPT = (
    "제공된 희망 직무와 기술 목록에 대한 동의어, 영문 표기, 채용 사이트에서 실제로 "
    "흔히 쓰는 다른 표현만 만든다. "
    "제공되지 않은 새로운 직무, 기술, 회사명, 연차, 지역, 고용 형태, 연봉 조건을 "
    "만들지 않는다. "
    "입력에 있는 문자열과 대소문자만 다르거나 완전히 같은 표현은 다시 만들지 않는다. "
    "결과는 keywords 배열에 문자열만 담는다. 만들 수 있는 표현이 없으면 빈 배열을 "
    "반환한다."
)

_JOB_POSTING_EXTRACTION_SYSTEM_PROMPT = (
    "제공된 채용 공고에 직접 존재하는 정보만 추출한다. "
    "확인할 수 없는 값은 만들지 않고 null 또는 빈 배열로 남긴다. "
    "반드시 evidence 배열부터 먼저 전부 채운 다음 jobTitle, requiredSkills, "
    "preferredSkills를 채운다. "
    "sourceText는 반드시 원문에서 이어져 있는 부분을 글자 하나까지 그대로 복사한 것이어야 한다. "
    "요약·재구성하지 않는다. 정확히 이어 붙여 복사할 수 없으면 그 항목은 만들지 않는다. "
    "requiredSkills, preferredSkills의 모든 항목은 evidenceIds에 evidence 배열에 "
    "실제로 존재하는 evidenceId를 하나 이상 채워 넣는다. jobTitle을 채웠으면 "
    "jobTitleEvidenceIds도 채운다. 근거를 만들지 못하면 그 항목은 만들지 않는다."
)

_RESUME_EXTRACTION_SYSTEM_PROMPT = (
    "제공된 이력서·포트폴리오에 직접 존재하는 정보만 추출한다. "
    "확인할 수 없는 값은 만들지 않고 null 또는 빈 배열로 남긴다. "
    "원문은 '[페이지 N]' 표시로 페이지가 구분되어 있다. "
    "반드시 evidence 배열부터 먼저 전부 채운 다음 skills, workExperiences, "
    "projects, education, certifications를 채운다. skills 등을 먼저 만들고 "
    "evidence를 나중에 채우지 않는다 — 순서를 지키지 않으면 앞쪽 항목이 "
    "evidenceIds 없이 남는 문제가 실제로 발생했다. "
    "모든 근거(evidence)는 그 값이 실제로 있던 페이지 번호(pageNumber)를 정확히 표시한다. "
    "sourceText는 반드시 원문에서 이어져 있는 부분을 글자 하나까지 그대로 복사한 것이어야 한다. "
    "앞에 있는 제목 줄이나 다른 줄을 이어 붙이거나, 띄어쓰기·줄바꿈을 바꾸거나, "
    "요약·재구성하지 않는다. 정확히 이어 붙여 복사할 수 없으면 그 항목은 만들지 않는다. "
    "skills, workExperiences, projects, projects[*].technologies, education, "
    "certifications의 모든 항목은 evidenceIds에 evidence 배열에 실제로 존재하는 "
    "evidenceId를 하나 이상 반드시 채워 넣는다. evidenceIds가 빈 배열인 항목은 "
    "만들지 않는다. "
    "이름, 이메일, 전화번호, 생년월일, 사진, 상세 주소는 어떤 필드나 근거에도 포함하지 않는다."
)


class OllamaUnavailableError(RuntimeError):
    """Ollama에 연결할 수 없거나 모델이 준비되지 않은 경우다."""


class OllamaResponseError(RuntimeError):
    """Ollama 응답이 프로젝트 스키마를 통과하지 못한 경우다."""


class OllamaProvider:
    """로컬 Ollama 모델을 호출한다."""

    provider_name = "ollama"

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
                "content": _JOB_POSTING_EXTRACTION_SYSTEM_PROMPT,
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

    async def generate_job_search_keyword_suggestions(
        self, desired_role: str, skill_names: list[str]
    ) -> GeneratedKeywordSuggestions:
        """희망 직무·기술에 대한 동의어·영문 표기 제안을 만든다.

        입력:
            desired_role: 사용자가 입력한 희망 직무.
            skill_names: 검증된 기술명 목록(저장소 근거·수기 입력 병합 결과).

        반환:
            아직 출처 태그가 없는 원시 제안 목록. 서비스 계층
            (`app/services/job_search_keywords.py`)이 최종 응답으로 조립한다.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = GeneratedKeywordSuggestions.model_json_schema()
        messages = [
            {"role": "system", "content": _JOB_SEARCH_KEYWORD_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": (
                    f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
                    f"\n\n희망 직무: {desired_role}"
                    f"\n기술 목록: {', '.join(skill_names) if skill_names else '(없음)'}"
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
            return GeneratedKeywordSuggestions.model_validate_json(content)
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

    async def extract_resume_profile(self, page_marked_text: str) -> ProfileCandidatePayload:
        """이력서·포트폴리오를 계약 `ProfileCandidatePayload` 스키마로 추출한다.

        입력:
            page_marked_text: 개인정보 제거가 끝난, '[페이지 N]' 표시가 포함된 원문.

        반환:
            기술·경력·프로젝트·학력·자격증과 근거가 포함된 구조화 결과.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = ProfileCandidatePayload.model_json_schema()
        messages = [
            {"role": "system", "content": _RESUME_EXTRACTION_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": (
                    f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
                    f"\n\n이력서·포트폴리오 원문:\n{page_marked_text}"
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
            return ProfileCandidatePayload.model_validate_json(content)
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
