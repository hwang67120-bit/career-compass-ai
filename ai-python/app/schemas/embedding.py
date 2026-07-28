"""임베딩 벡터의 요청·응답 스키마를 정의한다."""

from pydantic import BaseModel, ConfigDict


class EmbeddingVector(BaseModel):
    """생성된 임베딩과 추적에 필요한 메타데이터다."""

    model_config = ConfigDict(extra="forbid")

    values: list[float]
    model: str
    dimension: int
    version: str
