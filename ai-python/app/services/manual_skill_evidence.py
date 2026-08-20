"""수기로 입력한 기술을 저장소 근거와 같은 스키마로 감싼다.

파일 근거가 없는 자기 신고(self-declared) 값이라는 걸 `evidenceSource=MANUAL`로
표시한다 — 검증된 저장소 근거와 절대 안 섞이게 하기 위해서다
(`AGENTS.md` "사실, 추정과 미확인 구분": 사용자 사실과 AI·코드 근거를
서로 다른 데이터로 관리한다).
"""

from app.schemas.technical_evidence import EvidenceSource, TechnicalEvidenceExtraction
from app.services.technical_evidence_builder import TechnicalEvidenceBuilder

_ID_PREFIX = "manual-evidence"


def build_manual_skill_evidence(skill_names: list[str]) -> TechnicalEvidenceExtraction:
    """사용자가 직접 입력한 기술명 목록을 근거 스키마로 감싼다(순수 함수).

    입력:
        skill_names: 사용자가 입력한 기술명 원문. 앞뒤 공백은 제거하고,
            대소문자를 구분하지 않고 중복은 하나만 남긴다(먼저 나온 표기를
            그대로 쓴다).

    반환:
        각 기술마다 근거 하나(`evidenceSource=MANUAL`, `filePath=None`)를
        가진 결과. 파일 근거가 없다는 점 자체가 저장소 근거와의 차이다.
    """
    builder = TechnicalEvidenceBuilder(evidence_source=EvidenceSource.MANUAL, id_prefix=_ID_PREFIX)
    seen_lowercase: set[str] = set()

    for raw_name in skill_names:
        name = raw_name.strip()
        if not name or name.lower() in seen_lowercase:
            continue
        seen_lowercase.add(name.lower())
        builder.add(name, detail=f"사용자가 직접 입력한 기술: {name}")

    return builder.build()
