"""채용공고와 사용자 기술을 비교한 요약의 스키마를 정의한다.

계약 미확정(확인 필요) — docs/current-work.md "Python 다음 작업". LLM을
쓰지 않는 결정론적 구조화 데이터다(2026-08-01 사용자 확인) — 자연어
추천 문장을 생성하지 않는다. 일치 여부와 유사도 점수만 반환하고, 문장으로
꾸미는 건 프론트엔드·Java의 몫이다.
"""

from pydantic import BaseModel, ConfigDict, Field


class SkillFit(BaseModel):
    """채용공고가 요구한 기술 하나와 사용자 보유 여부다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    skill_name: str = Field(alias="skillName")
    required: bool
    """`True`면 필수 기술, `False`면 우대 기술."""
    matched: bool
    """사용자의 검증된 기술 목록에 있으면 `True`."""


class JobFitSummary(BaseModel):
    """채용공고 하나에 대한 적합도 요약이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    skills: list[SkillFit] = Field(default_factory=list)
    similarity: float | None = Field(default=None, ge=-1.0, le=1.0)
    """의미 유사도 점수(`app/services/similarity.py`). 합격 확률이나 실제
    능력 보장이 아니다(`AGENTS.md`)."""
