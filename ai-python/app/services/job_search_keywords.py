"""희망 직무·검증된 기술로 채용공고 검색어를 만든다.

검색어는 사용자가 채용 사이트에서 직접 검색할 때 참고하라고 보여주는
제안이다 — 다른 조회 API에 자동으로 넘기지 않는다(2026-07-30 사용자 확인,
docs/current-work.md 참고).

입력 그대로인 값(`KeywordSource.INPUT`)과 LLM이 만든 확장 표현
(`KeywordSource.GENERATED`)을 항상 구분해서 반환한다 — 희망 직무·기술은
사용자 사실이고, 동의어·영문 표기는 AI 추정이라 서로 다른 데이터로
관리한다(`AGENTS.md` "사실, 추정과 미확인 구분").
"""

from typing import Protocol

from app.schemas.job_search_keywords import (
    GeneratedKeywordSuggestions,
    JobSearchKeyword,
    JobSearchKeywordSet,
    KeywordSource,
)
from app.services.performance_tracking import StageOperation, measure_stage


class KeywordSuggestionProvider(Protocol):
    """`OllamaProvider`·`GeminiProvider`가 공통으로 구현하는 부분이다."""

    provider_name: str

    async def generate_job_search_keyword_suggestions(
        self, desired_role: str, skill_names: list[str]
    ) -> GeneratedKeywordSuggestions: ...


def build_input_keywords(desired_role: str, skill_names: list[str]) -> list[str]:
    """희망 직무와 기술명을 입력 그대로의 검색어 목록으로 만든다(순수 함수).

    앞뒤 공백을 제거하고, 대소문자를 구분하지 않고 중복을 제거한다(먼저
    나온 표기를 그대로 쓴다). 희망 직무가 있으면 항상 첫 번째다.
    """
    candidates = [desired_role, *skill_names]
    result: list[str] = []
    seen_lowercase: set[str] = set()
    for raw_value in candidates:
        value = raw_value.strip()
        if not value or value.lower() in seen_lowercase:
            continue
        seen_lowercase.add(value.lower())
        result.append(value)
    return result


def combine_keyword_sources(
    input_keywords: list[str], generated_keywords: list[str]
) -> JobSearchKeywordSet:
    """입력 검색어와 LLM 제안을 하나의 결과로 합친다(순수 함수).

    입력:
        input_keywords: `build_input_keywords`의 결과.
        generated_keywords: LLM이 만든 원시 제안(`GeneratedKeywordSuggestions.keywords`).

    반환:
        입력 검색어(`INPUT`)가 먼저 나오고, 입력과 중복되지 않는 LLM 제안
        (`GENERATED`)이 뒤따르는 결과. LLM 제안 사이의 중복도 제거한다.
    """
    keywords = [
        JobSearchKeyword(keyword=value, source=KeywordSource.INPUT) for value in input_keywords
    ]

    seen_lowercase = {value.lower() for value in input_keywords}
    for raw_value in generated_keywords:
        value = raw_value.strip()
        if not value or value.lower() in seen_lowercase:
            continue
        seen_lowercase.add(value.lower())
        keywords.append(JobSearchKeyword(keyword=value, source=KeywordSource.GENERATED))

    return JobSearchKeywordSet(keywords=keywords)


async def generate_job_search_keywords(
    provider: KeywordSuggestionProvider, desired_role: str, skill_names: list[str]
) -> JobSearchKeywordSet:
    """검색어 생성 전체 과정을 수행한다.

    입력:
        provider: LLM 제안을 만들 provider(`OllamaProvider` 등).
        desired_role: 사용자가 입력한 희망 직무.
        skill_names: 검증된 기술명 목록(저장소 근거·수기 입력 병합 결과).

    반환:
        `combine_keyword_sources`와 동일한 결과.

    예외:
        provider가 던지는 예외(예: `OllamaUnavailableError`)를 그대로 전달한다.
    """
    input_keywords = build_input_keywords(desired_role, skill_names)
    with measure_stage(provider.provider_name, StageOperation.GENERATE_JOB_SEARCH_KEYWORDS):
        suggestions = await provider.generate_job_search_keyword_suggestions(
            desired_role, skill_names
        )
    return combine_keyword_sources(input_keywords, suggestions.keywords)
