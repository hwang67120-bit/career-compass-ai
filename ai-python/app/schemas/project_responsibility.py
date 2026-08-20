"""프로젝트 담당 업무 근거 추출 스키마다.

계약: contracts/project-responsibility-extraction.md. Java가 저장소 스냅숏과
선택 기술 태그를 보내면 Python이 기술 근거와 `PROJECT_RESPONSIBILITY` 후보를
추출한다. Python은 GitHub를 직접 조회하지 않고 전달받은 자료만 쓴다.
"""

from pydantic import BaseModel, ConfigDict, Field


class SelectedTechnologyTag(BaseModel):
    technology_tag_id: str = Field(alias="technologyTagId")
    canonical_name: str = Field(alias="canonicalName")

    model_config = ConfigDict(populate_by_name=True)


class ReadmeEvidence(BaseModel):
    evidence_id: str = Field(alias="evidenceId")
    path: str
    text: str

    model_config = ConfigDict(populate_by_name=True)


class FileEvidence(BaseModel):
    evidence_id: str = Field(alias="evidenceId")
    path: str
    file_type: str = Field(alias="fileType")  # MANIFEST|CONFIGURATION|SOURCE|TEST
    related_technology_tag_ids: list[str] = Field(
        default_factory=list, alias="relatedTechnologyTagIds"
    )
    text: str

    model_config = ConfigDict(populate_by_name=True)


class RepositorySnapshot(BaseModel):
    source_url: str = Field(alias="sourceUrl")
    fetched_at: str = Field(alias="fetchedAt")
    repository_version: str = Field(alias="repositoryVersion")
    description: str | None = None
    readmes: list[ReadmeEvidence] = Field(default_factory=list)
    files: list[FileEvidence] = Field(default_factory=list)

    model_config = ConfigDict(populate_by_name=True)


class ProjectResponsibilityRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    extraction_task_id: str = Field(alias="extractionTaskId")
    project_source_id: str = Field(alias="projectSourceId")
    selected_technology_tags: list[SelectedTechnologyTag] = Field(alias="selectedTechnologyTags")
    repository_snapshot: RepositorySnapshot = Field(alias="repositorySnapshot")


# --- 모델(LLM)이 반환하는 담당 업무 후보 ---
class ProjectResponsibilityCandidate(BaseModel):
    text: str  # 근거로 확인 가능한 짧은 담당 업무 문장
    source_evidence_ids: list[str]  # 입력 근거 evidenceId 인용(제공된 것만)


class ProjectResponsibilityExtraction(BaseModel):
    responsibilities: list[ProjectResponsibilityCandidate]
