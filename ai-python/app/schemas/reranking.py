"""후보 재정렬 결과의 스키마를 정의한다."""

from pydantic import BaseModel, ConfigDict


class RankedCandidate(BaseModel):
    """순위가 매겨진 후보와 유사도다."""

    model_config = ConfigDict(extra="forbid")

    candidate_id: str
    similarity: float


class RerankResult(BaseModel):
    """재정렬 결과와 평가를 위한 기록이다."""

    model_config = ConfigDict(extra="forbid")

    ranked: list[RankedCandidate]
    excluded_candidate_ids: list[str]
    minimum_similarity: float
