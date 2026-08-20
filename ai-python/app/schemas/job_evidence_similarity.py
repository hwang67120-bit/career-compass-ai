"""채용공고 담당 업무와 사용자 프로젝트 업무의 의미 비교(LLM-as-judge) 스키마다.

계약: contracts/job-evidence-similarity.md.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class JobEvidence(BaseModel):
    """공고 담당 업무 근거 하나."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence_id: str = Field(alias="evidenceId")
    category: str
    text: str


class UserEvidence(BaseModel):
    """사용자 프로젝트 업무 근거 하나."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence_id: str = Field(alias="evidenceId")
    project_source_id: str = Field(alias="projectSourceId")
    category: str
    text: str


class SimilarityRequest(BaseModel):
    """Java가 보내는 비교 요청. 근거 검증은 라우터에서 계약 오류로 처리한다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    comparison_task_id: str = Field(alias="comparisonTaskId")
    job_analysis_id: str = Field(alias="jobAnalysisId")
    job_posting_id: str = Field(alias="jobPostingId")
    job_evidence: list[JobEvidence] = Field(alias="jobEvidence")
    user_evidence: list[UserEvidence] = Field(alias="userEvidence")


class JudgeVerdict(BaseModel):
    """모델이 반환하는 판정값. 점수·confidence·자유 문장 없이 이 둘만."""

    best_match_user_evidence_id: str | None
    judgment: Literal["RELATED", "NOT_RELATED"]
