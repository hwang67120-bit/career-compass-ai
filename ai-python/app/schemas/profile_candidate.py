"""이력서·포트폴리오 구조화 추출 결과의 스키마를 정의한다.

계약: contracts/document-extraction.md 5절 `ProfileCandidatePayload`.
필드명은 Java DTO와 동일한 camelCase를 그대로 쓴다.
"""

from pydantic import BaseModel, ConfigDict, Field


class CandidateEvidence(BaseModel):
    """후보 값과 연결되는 최소 원문 근거다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence_id: str = Field(alias="evidenceId")
    field_path: str = Field(alias="fieldPath")
    value: str
    source_text: str = Field(alias="sourceText")
    page_number: int = Field(alias="pageNumber")


class CandidateSkill(BaseModel):
    """문서에서 확인한 기술이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    raw_name: str = Field(alias="rawName")
    normalized_name: str | None = Field(default=None, alias="normalizedName")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class CandidateWorkExperience(BaseModel):
    """경력·업무 경험이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    company_name: str | None = Field(default=None, alias="companyName")
    job_title: str | None = Field(default=None, alias="jobTitle")
    raw_period: str | None = Field(default=None, alias="rawPeriod")
    started_on: str | None = Field(default=None, alias="startedOn")
    ended_on: str | None = Field(default=None, alias="endedOn")
    responsibilities: list[str] = Field(default_factory=list)
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class CandidateProject(BaseModel):
    """프로젝트 경험이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    project_name: str | None = Field(default=None, alias="projectName")
    role: str | None = None
    summary: str | None = None
    technologies: list[CandidateSkill] = Field(default_factory=list)
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class CandidateEducation(BaseModel):
    """학력이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    institution_name: str | None = Field(default=None, alias="institutionName")
    major: str | None = None
    degree: str | None = None
    raw_period: str | None = Field(default=None, alias="rawPeriod")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class CandidateCertification(BaseModel):
    """자격증이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    name: str
    issuer: str | None = None
    acquired_on: str | None = Field(default=None, alias="acquiredOn")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class ProfileCandidatePayload(BaseModel):
    """이력서·포트폴리오에서 추출한 구조화 후보 전체다.

    필드 순서를 의도적으로 `evidence`가 먼저 오도록 뒀다. Ollama의 제약된
    JSON 생성은 스키마의 필드 선언 순서를 따르는 경향이 있어서, 근거를
    나중에(`evidence`가 마지막) 선언하면 skills 등 앞쪽 항목을 생성할 때
    아직 만들지 않은 근거를 참조하지 못해 evidenceIds가 빈 채로 남는
    문제가 실제로 관찰됐다(qwen2.5·exaone3.5·llama3.2 공통).
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[CandidateEvidence] = Field(default_factory=list)
    skills: list[CandidateSkill] = Field(default_factory=list)
    work_experiences: list[CandidateWorkExperience] = Field(
        default_factory=list, alias="workExperiences"
    )
    projects: list[CandidateProject] = Field(default_factory=list)
    education: list[CandidateEducation] = Field(default_factory=list)
    certifications: list[CandidateCertification] = Field(default_factory=list)
