"""GitHub 저장소 코드에서 추출한 기술·근거의 요청·응답 스키마를 정의한다.

계약 미확정(확인 필요) — docs/current-work.md "Python 다음 작업" 1번.
결정론적 분석(매니페스트 파일·확장자)만 사용하므로 evidence의 `detail`은
LLM 요약이 아니라 실제 파일에서 그대로 찾은 문자열이다.
"""

from pydantic import BaseModel, ConfigDict, Field


class RepositoryEvidence(BaseModel):
    """근거 하나. 실제로 저장소에서 확인한 파일 경로와 내용으로만 구성한다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence_id: str = Field(alias="evidenceId")
    file_path: str = Field(alias="filePath")
    detail: str


class RepositorySkill(BaseModel):
    """근거로 뒷받침되는 기술(언어·프레임워크)이다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    skill_name: str = Field(alias="skillName")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class RepositoryEvidenceExtraction(BaseModel):
    """저장소 코드 분석 결과다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[RepositoryEvidence] = Field(default_factory=list)
    skills: list[RepositorySkill] = Field(default_factory=list)
