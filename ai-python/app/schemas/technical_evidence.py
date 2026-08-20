"""기술 근거의 요청·응답 스키마를 정의한다. 출처(저장소·수기 입력)를 안 섞는다.

계약 미확정(확인 필요) — docs/current-work.md "Python 다음 작업" 1·2번.
저장소 근거(`app/services/repository_evidence.py`)와 수기 입력 근거
(`app/services/manual_skill_evidence.py`)가 같은 스키마를 쓰지만,
`evidenceSource`로 항상 구분된다 — 검증된 근거와 자기 신고 값을 하나의
표시처럼 섞지 않는다(`AGENTS.md` "사실, 추정과 미확인 구분").
"""

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class EvidenceSource(str, Enum):
    """근거가 어디서 왔는지 나타낸다."""

    REPOSITORY = "REPOSITORY"
    MANUAL = "MANUAL"


class TechnicalEvidence(BaseModel):
    """근거 하나. `evidenceSource`에 따라 `filePath` 유무가 달라진다.

    - `REPOSITORY`: 실제 저장소 파일 경로와 그 파일에서 그대로 찾은 문자열.
    - `MANUAL`: 파일 경로가 없다(`None`) — 사용자가 직접 입력했다는 사실 자체가
      근거다.
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence_id: str = Field(alias="evidenceId")
    evidence_source: EvidenceSource = Field(alias="evidenceSource")
    file_path: str | None = Field(default=None, alias="filePath")
    detail: str


class TechnicalSkill(BaseModel):
    """근거로 뒷받침되는 기술(언어·프레임워크)이다.

    `evidenceIds`가 가리키는 근거들의 `evidenceSource`가 서로 다를 수 있다
    (예: 저장소에서도 발견되고 수기로도 입력된 경우) — 그 자체가 "본인 신고와
    실제 사용이 모두 확인된 기술"이라는 유용한 신호다.
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    skill_name: str = Field(alias="skillName")
    evidence_ids: list[str] = Field(default_factory=list, alias="evidenceIds")


class TechnicalEvidenceExtraction(BaseModel):
    """근거·기술 추출 결과다. 저장소 근거·수기 입력 근거 모두 이 타입을 쓴다."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    evidence: list[TechnicalEvidence] = Field(default_factory=list)
    skills: list[TechnicalSkill] = Field(default_factory=list)
