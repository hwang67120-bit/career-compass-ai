"""채용 공고 구조화 추출 결과의 요청·응답 스키마를 정의한다.

계약: contracts/job-posting-extraction.md (제안). 이력서의
`app/schemas/profile_candidate.py`와 같은 근거 연결 방식(evidenceId
기반)을 쓴다 — 채용공고는 페이지 개념이 없으므로 `pageNumber`는 없다.
"""

from pydantic import BaseModel, ConfigDict, Field


class JobPostingEvidence(BaseModel):
    """추출값과 연결된 원문 근거다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence_id: str = Field(alias="evidenceId")
    field_path: str = Field(alias="fieldPath")
    value: str
    source_text: str = Field(alias="sourceText")


class JobPostingSkill(BaseModel):
    """공고에서 확인한 필수·우대 기술이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    raw_name: str = Field(alias="rawName")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class JobPostingExtraction(BaseModel):
    """채용 공고에서 추출한 구조화 결과다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[JobPostingEvidence] = Field(default_factory=list)
    job_title: str | None = Field(default=None, alias="jobTitle")
    job_title_evidence_ids: list[str] = Field(default_factory=list, alias="jobTitleEvidenceIds")
    required_skills: list[JobPostingSkill] = Field(default_factory=list, alias="requiredSkills")
    preferred_skills: list[JobPostingSkill] = Field(default_factory=list, alias="preferredSkills")
