"""저장소·수기 입력 등 여러 추출기가 공유하는 근거 조립 도구다.

각 추출기가 서로 다른 `id_prefix`를 쓰면, 나중에 여러 출처의 결과를
`merge_technical_evidence`로 합쳐도 evidence_id가 겹치지 않는다.
"""

from dataclasses import dataclass, field

from app.schemas.technical_evidence import (
    EvidenceSource,
    TechnicalEvidence,
    TechnicalEvidenceExtraction,
    TechnicalSkill,
)


@dataclass
class TechnicalEvidenceBuilder:
    """근거를 추가하면서 기술명별로 evidence_id를 모아 최종 결과를 만든다."""

    evidence_source: EvidenceSource
    id_prefix: str
    evidence: list[TechnicalEvidence] = field(default_factory=list)
    skill_evidence_ids: dict[str, list[str]] = field(default_factory=dict)
    _next_id: int = field(default=1, init=False)

    def add(self, skill_name: str, detail: str, file_path: str | None = None) -> str:
        """근거 하나를 추가하고 생성된 evidence_id를 반환한다."""
        evidence_id = f"{self.id_prefix}-{self._next_id}"
        self._next_id += 1
        self.evidence.append(
            TechnicalEvidence(
                evidence_id=evidence_id,
                evidence_source=self.evidence_source,
                file_path=file_path,
                detail=detail,
            )
        )
        self.skill_evidence_ids.setdefault(skill_name, []).append(evidence_id)
        return evidence_id

    def build(self) -> TechnicalEvidenceExtraction:
        skills = [
            TechnicalSkill(skill_name=skill_name, evidence_ids=evidence_ids)
            for skill_name, evidence_ids in self.skill_evidence_ids.items()
        ]
        return TechnicalEvidenceExtraction(evidence=self.evidence, skills=skills)
