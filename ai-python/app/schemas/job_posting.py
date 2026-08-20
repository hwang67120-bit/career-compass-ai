"""채용 공고 구조화 추출 결과의 요청·응답 스키마를 정의한다.

계약: contracts/job-posting-extraction.md (부분 확정). 추출값과 근거를
`evidenceId`로 연결한다. 채용공고는 페이지 개념이 없으므로
`pageNumber`는 없다.
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


class JobPostingResponsibility(BaseModel):
    """공고에서 확인한 담당 업무(주요 업무) 항목이다.

    2026-08-03 추가(제안, 코덱스 확인 필요) — 기존 스키마에는 직무명·기술만
    있고 담당 업무 필드가 없어서, 채용공고 임베딩(`job_posting_embedding.py`)이
    사용자 경험 임베딩(README 서술형 텍스트)과 비교할 때 채용공고 쪽엔 서술형
    텍스트가 전혀 안 들어가는 비대칭이 있었다. 다른 필드와 같은 원칙 — 근거
    없는 항목은 만들지 않는다.
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    raw_text: str = Field(alias="rawText")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class JobPostingCoreExtraction(BaseModel):
    """직무명·필수/우대 기술만 추출하는 스키마다(`responsibilities` 없음).

    `JobPostingExtraction`에 `responsibilities`를 추가하자 qwen2.5의 evidence
    배열 생성이 통째로 비어버리는 회귀가 실제로 재현됐다(2026-08-03, 원래
    잘 되던 fixture로도 재현됨). 원래 잘 되던 부분(jobTitle·기술)은 이 좁은
    스키마로 그대로 두고, `provider.extract_job_posting`이 이 스키마로 모델을
    호출한다. `responsibilities`는 `JobPostingResponsibilityExtraction`으로
    완전히 별도 호출한 뒤 서비스 계층에서 합친다(`job_posting_extraction.py`의
    `extract_job_posting_profile`).
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[JobPostingEvidence] = Field(default_factory=list)
    job_title: str | None = Field(default=None, alias="jobTitle")
    job_title_evidence_ids: list[str] = Field(default_factory=list, alias="jobTitleEvidenceIds")
    required_skills: list[JobPostingSkill] = Field(default_factory=list, alias="requiredSkills")
    preferred_skills: list[JobPostingSkill] = Field(default_factory=list, alias="preferredSkills")


class JobPostingResponsibilityExtraction(BaseModel):
    """담당 업무만 추출하는 좁은 스키마다 — `JobPostingCoreExtraction`과 별도 호출."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[JobPostingEvidence] = Field(default_factory=list)
    responsibilities: list[JobPostingResponsibility] = Field(default_factory=list)


class JobPostingExtraction(BaseModel):
    """채용 공고에서 추출한 구조화 결과다(핵심 추출 + 담당 업무 추출을 합친 최종 결과)."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[JobPostingEvidence] = Field(default_factory=list)
    job_title: str | None = Field(default=None, alias="jobTitle")
    job_title_evidence_ids: list[str] = Field(default_factory=list, alias="jobTitleEvidenceIds")
    responsibilities: list[JobPostingResponsibility] = Field(default_factory=list)
    required_skills: list[JobPostingSkill] = Field(default_factory=list, alias="requiredSkills")
    preferred_skills: list[JobPostingSkill] = Field(default_factory=list, alias="preferredSkills")
