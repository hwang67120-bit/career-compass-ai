"""로컬 Ollama의 구조화 출력 API를 호출한다."""

import json

import httpx
from pydantic import ValidationError

from app.schemas.job_evidence_similarity import JudgeVerdict
from app.schemas.job_posting import JobPostingCoreExtraction, JobPostingResponsibilityExtraction
from app.schemas.job_search_keywords import GeneratedKeywordSuggestions
from app.schemas.project_responsibility import ProjectResponsibilityExtraction
from app.services.performance_tracking import set_last_usage


def _content_and_record_usage(response: httpx.Response) -> str:
    """Ollama 응답에서 content를 꺼내고, 토큰 사용량을 계측에 기록한다.

    Ollama `/api/chat`는 `prompt_eval_count`(입력)와 `eval_count`(출력) 토큰 수를
    함께 반환한다 — 지금까지 버리던 값을 계측으로 넘긴다. 응답 내용(content)은
    로그에 남기지 않고 호출부로만 반환한다.
    """
    payload = response.json()
    set_last_usage(payload.get("prompt_eval_count"), payload.get("eval_count"))
    return payload["message"]["content"]

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

_JOB_POSTING_RESPONSIBILITY_EXTRACTION_SYSTEM_PROMPT = (
    "제공된 채용 공고에서 '담당 업무'·'주요 업무'처럼 이 직무가 실제로 하는 일을 "
    "서술한 부분만 추출한다. 자격 요건·기술·우대 사항·근무 조건·회사 소개는 담당 "
    "업무가 아니다. 확인할 수 없으면 만들지 않고 빈 배열로 남긴다. "
    "반드시 evidence 배열부터 먼저 전부 채운 다음 responsibilities를 채운다. "
    "sourceText는 반드시 원문에서 이어져 있는 부분을 글자 하나까지 그대로 복사한 것이어야 한다. "
    "요약·재구성하지 않는다. 정확히 이어 붙여 복사할 수 없으면 그 항목은 만들지 않는다. "
    "responsibilities의 모든 항목은 evidenceIds에 evidence 배열에 실제로 존재하는 "
    "evidenceId를 하나 이상 채워 넣는다. 근거를 만들지 못하면 그 항목은 만들지 않는다."
)

_EVIDENCE_JUDGE_SYSTEM_PROMPT = (
    "너는 채용공고의 '담당 업무' 하나와 지원자의 '프로젝트 업무' 목록을 받는다. "
    "지원자 프로젝트 중 담당 업무와 의미상 같은 종류의 일을 하는 것을 판단한다. "
    "가장 관련 있는 프로젝트 하나를 bestMatchUserEvidenceId에 그 id로 담는다. "
    "그 프로젝트가 실제로 같은 종류의 업무면 judgment는 RELATED, 목록에 같은 종류의 "
    "업무가 하나도 없으면 judgment는 NOT_RELATED로 하고 bestMatchUserEvidenceId는 null로 둔다. "
    "id는 반드시 제공된 목록에 있는 값만 쓴다. 새 id·점수·설명을 만들지 않는다. "
    "기술 스택 이름이 같은지가 아니라 하는 일(업무)이 같은지로 판단한다."
)


_PROJECT_RESPONSIBILITY_SYSTEM_PROMPT = (
    "너는 지원자 저장소의 근거 자료 목록(각각 id가 붙어 있음)을 받아, 이 프로젝트가 "
    "실제로 '하는 일'(담당 업무·기능)을 추출한다. 사용자가 선택한 기술과 관련된 업무에 "
    "집중한다. 각 항목의 source_evidence_ids에는 그 업무의 근거가 되는 자료의 id를 "
    "제공된 목록에서 하나 이상 골라 담는다 — 목록에 없는 id를 만들지 않는다. 근거 자료에 "
    "없는 내용을 지어내지 않는다. text는 근거로 확인 가능한 짧은 담당 업무 문장이며 새로운 "
    "성과·역할을 만들지 않는다. 뽑을 수 없으면 빈 배열을 반환한다."
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

    async def extract_job_posting(self, source_text: str) -> JobPostingCoreExtraction:
        """채용 공고에서 직무명·필수/우대 기술을 프로젝트 JSON 스키마로 추출한다.

        담당 업무(`responsibilities`)는 여기 없다 — 같은 스키마에 넣으면
        qwen2.5의 evidence 배열 생성이 통째로 비어버리는 회귀가 실제로
        재현돼(2026-08-03), `extract_job_posting_responsibilities`로 완전히
        별도 호출한다.

        입력:
            source_text: 텍스트 추출과 안전 검사가 끝난 채용 공고 원문.

        반환:
            직무명, 필수·우대 기술과 원문 근거가 포함된 구조화 결과.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = JobPostingCoreExtraction.model_json_schema()
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
            content = _content_and_record_usage(response)
            return JobPostingCoreExtraction.model_validate_json(content)
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

    async def extract_job_posting_responsibilities(
        self, source_text: str
    ) -> JobPostingResponsibilityExtraction:
        """채용 공고에서 담당 업무만 별도 스키마로 추출한다.

        `extract_job_posting`과 완전히 분리된 호출이다 — 하나로 합쳤을 때
        생긴 회귀(위 참고) 때문에 서비스 계층(`job_posting_extraction.py`의
        `extract_job_posting_profile`)이 두 결과를 합친다.

        입력:
            source_text: 텍스트 추출과 안전 검사가 끝난 채용 공고 원문.

        반환:
            담당 업무와 원문 근거가 포함된 구조화 결과.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = JobPostingResponsibilityExtraction.model_json_schema()
        messages = [
            {
                "role": "system",
                "content": _JOB_POSTING_RESPONSIBILITY_EXTRACTION_SYSTEM_PROMPT,
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
            content = _content_and_record_usage(response)
            return JobPostingResponsibilityExtraction.model_validate_json(content)
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

    async def extract_project_responsibilities(
        self, evidence_items: list[tuple[str, str]], selected_tech_names: list[str]
    ) -> ProjectResponsibilityExtraction:
        """저장소 근거 자료(readme·파일)에서 담당 업무 후보를 추출한다.

        각 후보는 제공된 근거 id(`source_evidence_ids`)를 인용한다. 근거
        유효성·기술 연결·grounding 검증은 서비스 계층
        (`project_responsibility_extraction`)이 한다.

        입력:
            evidence_items: (근거 evidenceId, text) 목록(readme + 파일).
            selected_tech_names: 사용자가 선택한 기술명(추출 힌트).

        반환:
            담당 업무 문장과 인용 근거 id가 담긴 후보 목록.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = ProjectResponsibilityExtraction.model_json_schema()
        tech = ", ".join(selected_tech_names) if selected_tech_names else "(없음)"
        evidence_block = "\n\n".join(f"[id={eid}]\n{text}" for eid, text in evidence_items)
        messages = [
            {"role": "system", "content": _PROJECT_RESPONSIBILITY_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": (
                    f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
                    f"\n\n사용자가 선택한 기술: {tech}"
                    f"\n\n근거 자료:\n{evidence_block}"
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
            content = _content_and_record_usage(response)
            return ProjectResponsibilityExtraction.model_validate_json(content)
        except httpx.TimeoutException as error:
            raise OllamaUnavailableError("Ollama 응답 제한시간을 초과했습니다.") from error
        except httpx.HTTPError as error:
            raise OllamaUnavailableError("Ollama 요청에 실패했습니다.") from error
        except (KeyError, TypeError, ValueError, ValidationError) as error:
            raise OllamaResponseError(
                "Ollama 응답이 프로젝트 스키마와 일치하지 않습니다."
            ) from error

    async def judge_evidence_relation(
        self, job_text: str, user_items: list[tuple[str, str]]
    ) -> JudgeVerdict:
        """공고 담당 업무 하나를 사용자 프로젝트 업무 목록과 비교해 판정한다.

        입력:
            job_text: 공고 담당 업무 근거 문장.
            user_items: (사용자 근거 id, 문장) 목록.

        반환:
            best-match 근거 id(또는 None)와 RELATED/NOT_RELATED 판정.

        예외:
            OllamaUnavailableError: Ollama 연결 실패, 제한시간 초과 또는 요청 실패.
            OllamaResponseError: Ollama 응답이 프로젝트 스키마와 다른 경우.
        """
        schema = JudgeVerdict.model_json_schema()
        user_lines = "\n".join(f"- {uid}: {text}" for uid, text in user_items)
        messages = [
            {"role": "system", "content": _EVIDENCE_JUDGE_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": (
                    f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
                    f"\n\n채용공고 담당 업무:\n{job_text}"
                    f"\n\n지원자 프로젝트 업무 목록:\n{user_lines}"
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
            content = _content_and_record_usage(response)
            return JudgeVerdict.model_validate_json(content)
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

    async def unload_model(self) -> None:
        """모델을 즉시 메모리에서 내려 다음 요청이 새로 로드하게 만든다.

        같은 모델 로드 세션에서 다른 요청을 먼저 처리한 뒤에만 특정 입력의
        근거 검증이 실패하는 현상(세션 오염, 2026-08-03 확인)에 대한 완화책 —
        `extract_job_posting_profile`이 근거 검증 실패 후 재시도 전에 호출한다.
        `keep_alive: 0`만 보내고 `prompt`를 비우면 Ollama가 생성 없이 즉시
        언로드한다(`done_reason: "unload"`로 실제 확인함).

        예외:
            OllamaUnavailableError: Ollama 연결 실패 또는 요청 실패.
        """
        try:
            response = await self.client.post(
                "/api/generate",
                json={"model": self.model_name, "keep_alive": 0},
            )
            response.raise_for_status()
        except httpx.TimeoutException as error:
            raise OllamaUnavailableError(
                "Ollama 모델 언로드 요청 제한시간을 초과했습니다."
            ) from error
        except httpx.HTTPError as error:
            raise OllamaUnavailableError(
                "Ollama 모델 언로드 요청에 실패했습니다."
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
            content = _content_and_record_usage(response)
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

