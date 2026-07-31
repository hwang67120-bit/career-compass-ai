"""Gemini의 구조화 출력 API를 호출한다."""

import httpx
from google import genai
from google.genai import errors, types
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


class GeminiUnavailableError(RuntimeError):
    """Gemini에 연결할 수 없거나 서버 오류가 발생한 경우다."""


class GeminiResponseError(RuntimeError):
    """Gemini 응답이 프로젝트 스키마를 통과하지 못한 경우다."""


class GeminiProvider:
    """Gemini API를 호출한다."""

    provider_name = "gemini"

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
                    system_instruction=_JOB_POSTING_EXTRACTION_SYSTEM_PROMPT,
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

    async def extract_resume_profile(self, page_marked_text: str) -> ProfileCandidatePayload:
        """이력서·포트폴리오를 계약 `ProfileCandidatePayload` 스키마로 추출한다.

        주의: 노션 문서의 "Gemini 무료 등급 데이터 제한" 정책에 따라 실제
        사용자 이력서를 이 메서드에 전달하지 않는다. 실제 문서 추출
        파이프라인은 `OllamaProvider`만 사용하고, 이 메서드는 직접 만든
        가상 이력서로 인터페이스를 검증하는 용도로만 쓴다.

        입력:
            page_marked_text: 개인정보 제거가 끝난, '[페이지 N]' 표시가 포함된 원문.

        반환:
            기술·경력·프로젝트·학력·자격증과 근거가 포함된 구조화 결과.

        예외:
            GeminiUnavailableError: 연결 실패 또는 Gemini 서버 오류.
            GeminiResponseError: 요청 거부 또는 응답이 프로젝트 스키마와 다른 경우.
        """
        try:
            response = await self.client.aio.models.generate_content(
                model=self.model_name,
                contents=page_marked_text,
                config=types.GenerateContentConfig(
                    system_instruction=_RESUME_EXTRACTION_SYSTEM_PROMPT,
                    response_mime_type="application/json",
                    response_json_schema=ProfileCandidatePayload.model_json_schema(),
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
            return ProfileCandidatePayload.model_validate_json(response.text)
        except ValidationError as error:
            raise GeminiResponseError(
                "Gemini 응답이 프로젝트 스키마와 일치하지 않습니다."
            ) from error

    async def generate_job_search_keyword_suggestions(
        self, desired_role: str, skill_names: list[str]
    ) -> GeneratedKeywordSuggestions:
        """희망 직무·기술에 대한 동의어·영문 표기 제안을 만든다.

        주의: 희망 직무·기술명은 이력서 원문보다 민감도가 낮지만, 실제
        사용자 값을 Gemini(무료 등급 데이터 제한 정책 적용 대상)에 보내도
        되는지는 아직 확인되지 않았다(확인 필요) — `extract_resume_profile`과
        같은 이유로, 이 메서드는 실제 서비스에 연결하기 전 정책 확인이
        필요하다. 지금은 `OllamaProvider`와의 인터페이스·모델 비교 검증
        용도로만 쓴다.

        입력:
            desired_role: 사용자가 입력한 희망 직무.
            skill_names: 검증된 기술명 목록(저장소 근거·수기 입력 병합 결과).

        반환:
            아직 출처 태그가 없는 원시 제안 목록. 서비스 계층
            (`app/services/job_search_keywords.py`)이 최종 응답으로 조립한다.

        예외:
            GeminiUnavailableError: 연결 실패 또는 Gemini 서버 오류.
            GeminiResponseError: 요청 거부 또는 응답이 프로젝트 스키마와 다른 경우.
        """
        skill_text = ", ".join(skill_names) if skill_names else "(없음)"
        try:
            response = await self.client.aio.models.generate_content(
                model=self.model_name,
                contents=f"희망 직무: {desired_role}\n기술 목록: {skill_text}",
                config=types.GenerateContentConfig(
                    system_instruction=_JOB_SEARCH_KEYWORD_SYSTEM_PROMPT,
                    response_mime_type="application/json",
                    response_json_schema=GeneratedKeywordSuggestions.model_json_schema(),
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
            return GeneratedKeywordSuggestions.model_validate_json(response.text)
        except ValidationError as error:
            raise GeminiResponseError(
                "Gemini 응답이 프로젝트 스키마와 일치하지 않습니다."
            ) from error
