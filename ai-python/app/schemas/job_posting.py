"""채용 공고 구조화 추출 결과의 요청·응답 스키마를 정의한다."""

from pydantic import BaseModel, ConfigDict, Field


class Evidence(BaseModel):
    """추출값과 연결된 원문 근거다."""

    model_config = ConfigDict(extra="forbid")

    field: str
    value: str
    source_text: str


class JobPostingExtraction(BaseModel):
    """채용 공고에서 추출한 구조화 결과다."""

    model_config = ConfigDict(extra="forbid")

    job_title: str
    required_skills: list[str] = Field(default_factory=list)
    preferred_skills: list[str] = Field(default_factory=list)
    evidence: list[Evidence] = Field(default_factory=list)
