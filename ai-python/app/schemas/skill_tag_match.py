"""후보 기술 태그와 고정 태그 목록 비교 결과의 스키마를 정의한다.

계약 미확정(확인 필요) — docs/current-work.md "채용공고 검색·전체 분석
파이프라인" 절. 고정 태그 목록은 실제 채용공고에서 추출된 `rawName`을
모아 만든다(2026-07-31 사용자 확인) — 사람이 자기소개에 쓰는 추상적인
표현이 아니라 채용 시장에서 실제로 쓰이는 구체적인 이름과 비교해야
정확한 매칭이 된다.
"""

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class TagMatchRecommendation(str, Enum):
    """후보 태그를 어떻게 처리할지에 대한 권고다. Python은 권고만 하고,
    실제로 값을 바꾸는 건 사용자 확인을 받은 뒤 Java가 한다."""

    EXACT_MATCH = "EXACT_MATCH"
    """대소문자만 다를 뿐 고정 태그와 완전히 같은 문자열이다."""

    SUGGEST_CORRECTION = "SUGGEST_CORRECTION"
    """오타·표기 차이로 추정된다(임베딩 유사도가 임계값 이상). 사용자에게
    `bestMatchTag`로 수정할지 물어보고, 승인 후에만 정규화한다."""

    NO_MATCH = "NO_MATCH"
    """고정 태그 목록 어디에도 대응하는 항목이 없다. 채용 시장에 대응하는
    태그가 없다는 뜻이므로, 이 기술은 비교(조건 판정·유사도)에서 제외하는
    것을 권고한다. 사용자의 수기 입력 자체를 지우지는 않는다."""


class SkillTagMatch(BaseModel):
    """후보 태그 하나에 대한 판단 결과다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    candidate_tag: str = Field(alias="candidateTag")
    recommendation: TagMatchRecommendation
    best_match_tag: str | None = Field(default=None, alias="bestMatchTag")
    similarity: float | None = Field(default=None, ge=-1.0, le=1.0)
