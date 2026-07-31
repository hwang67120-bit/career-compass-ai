"""채용공고 검색어의 요청·응답 스키마를 정의한다.

계약 미확정(확인 필요) — docs/current-work.md "Python 다음 작업" 3번.
검색어는 사용자가 채용 사이트에서 직접 검색할 때 쓰라고 보여주는 제안일
뿐이며, 다른 조회 API에 자동으로 넘기지 않는다(2026-07-30 사용자 확인).
"""

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class KeywordSource(str, Enum):
    """검색어가 어디서 왔는지 나타낸다."""

    INPUT = "INPUT"
    """사용자의 희망 직무 또는 검증된 기술명 그대로 — AI가 만들지 않았다."""

    GENERATED = "GENERATED"
    """LLM이 만든 동의어·영문 표기 등 확장 표현."""


class JobSearchKeyword(BaseModel):
    """검색어 하나와 출처."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    keyword: str
    source: KeywordSource


class JobSearchKeywordSet(BaseModel):
    """최종 검색어 목록 — 입력 그대로인 것과 AI가 확장한 것이 항상 구분된다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    keywords: list[JobSearchKeyword] = Field(default_factory=list)


class GeneratedKeywordSuggestions(BaseModel):
    """LLM 응답 전용 스키마 — 아직 출처 태그가 붙지 않은 원시 제안이다.

    최종 응답(`JobSearchKeywordSet`)이 아니다. 서비스 계층이 이 제안들에
    `KeywordSource.GENERATED`를 붙이고 입력과 중복되는 항목을 제거한다.
    """

    model_config = ConfigDict(extra="forbid")

    keywords: list[str] = Field(default_factory=list)
