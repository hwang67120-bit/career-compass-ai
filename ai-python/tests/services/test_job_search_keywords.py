import pytest

from app.schemas.job_search_keywords import GeneratedKeywordSuggestions, KeywordSource
from app.services.job_search_keywords import (
    build_input_keywords,
    combine_keyword_sources,
    generate_job_search_keywords,
)


class _FakeKeywordProvider:
    """네트워크 없이 오케스트레이션 로직만 검증하기 위한 가짜 provider다."""

    def __init__(self, keywords: list[str]) -> None:
        self._keywords = keywords
        self.received_calls: list[tuple[str, list[str]]] = []

    async def generate_job_search_keyword_suggestions(
        self, desired_role: str, skill_names: list[str]
    ) -> GeneratedKeywordSuggestions:
        self.received_calls.append((desired_role, skill_names))
        return GeneratedKeywordSuggestions(keywords=self._keywords)


def test_build_input_keywords_puts_desired_role_first() -> None:
    result = build_input_keywords("백엔드 개발자", ["Python", "FastAPI"])

    assert result == ["백엔드 개발자", "Python", "FastAPI"]


def test_build_input_keywords_deduplicates_case_insensitively() -> None:
    result = build_input_keywords("백엔드 개발자", ["Python", "python", " Python "])

    assert result == ["백엔드 개발자", "Python"]


def test_build_input_keywords_ignores_blank_values() -> None:
    result = build_input_keywords("  ", ["Python", ""])

    assert result == ["Python"]


def test_combine_keyword_sources_tags_input_and_generated_separately() -> None:
    result = combine_keyword_sources(
        input_keywords=["백엔드 개발자", "Python"],
        generated_keywords=["서버 개발자", "Backend Engineer"],
    )

    sources_by_keyword = {item.keyword: item.source for item in result.keywords}
    assert sources_by_keyword["백엔드 개발자"] == KeywordSource.INPUT
    assert sources_by_keyword["Python"] == KeywordSource.INPUT
    assert sources_by_keyword["서버 개발자"] == KeywordSource.GENERATED
    assert sources_by_keyword["Backend Engineer"] == KeywordSource.GENERATED


def test_combine_keyword_sources_drops_generated_duplicates_of_input() -> None:
    result = combine_keyword_sources(
        input_keywords=["백엔드 개발자"],
        generated_keywords=["백엔드 개발자", "백엔드개발자 "],
    )

    keywords = [item.keyword for item in result.keywords]
    assert keywords == ["백엔드 개발자", "백엔드개발자"]
    assert result.keywords[1].source == KeywordSource.GENERATED


def test_combine_keyword_sources_drops_duplicates_among_generated() -> None:
    result = combine_keyword_sources(
        input_keywords=[],
        generated_keywords=["서버 개발자", "서버 개발자", "Server Developer"],
    )

    keywords = [item.keyword for item in result.keywords]
    assert keywords == ["서버 개발자", "Server Developer"]


def test_combine_keyword_sources_handles_no_generated_keywords() -> None:
    result = combine_keyword_sources(input_keywords=["Java"], generated_keywords=[])

    assert len(result.keywords) == 1
    assert result.keywords[0].source == KeywordSource.INPUT


@pytest.mark.asyncio
async def test_generate_job_search_keywords_combines_provider_result_with_input() -> None:
    provider = _FakeKeywordProvider(keywords=["서버 개발자"])

    result = await generate_job_search_keywords(
        provider, desired_role="백엔드 개발자", skill_names=["Python"]
    )

    keywords = [item.keyword for item in result.keywords]
    assert keywords == ["백엔드 개발자", "Python", "서버 개발자"]
    assert provider.received_calls == [("백엔드 개발자", ["Python"])]
