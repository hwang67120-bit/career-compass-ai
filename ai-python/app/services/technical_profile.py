"""저장소 근거와 수기 입력 근거를 하나의 기술 프로필로 합친다.

"합친다"는 하나의 목록으로 다루기 편하게 만든다는 뜻이고, 근거 출처를
지우는 게 아니다 — 모든 근거는 `evidenceSource`를 그대로 유지한다. 같은
기술명이 여러 출처에 있으면 그 기술의 근거 목록에 양쪽 evidenceId가 모두
남아서, "본인 신고와 저장소 사용이 모두 확인된 기술"을 그대로 알 수 있다.

기술명 일치는 정확히 같은 문자열(대소문자 구분)만 같은 기술로 본다(확인
필요 — "JS"와 "JavaScript" 같은 동의어 처리는 아직 하지 않는다).
"""

from app.schemas.technical_evidence import TechnicalEvidenceExtraction, TechnicalSkill


def merge_technical_evidence(
    *extractions: TechnicalEvidenceExtraction,
) -> TechnicalEvidenceExtraction:
    """여러 출처의 근거·기술 추출 결과를 하나로 합친다(순수 함수).

    입력:
        extractions: 예를 들어 저장소 근거 추출 결과와 수기 입력 근거 추출
            결과. 순서는 결과의 `skills` 순서에 영향을 준다(먼저 나온
            출처의 기술명이 먼저 나온다).

    반환:
        근거는 그대로 모두 이어붙이고, 기술은 이름이 정확히 같으면 근거
        목록을 합친 하나의 항목으로 만든 결과.
    """
    all_evidence = [evidence for extraction in extractions for evidence in extraction.evidence]

    skill_evidence_ids: dict[str, list[str]] = {}
    for extraction in extractions:
        for skill in extraction.skills:
            skill_evidence_ids.setdefault(skill.skill_name, []).extend(skill.evidence_ids)

    skills = [
        TechnicalSkill(skill_name=skill_name, evidence_ids=evidence_ids)
        for skill_name, evidence_ids in skill_evidence_ids.items()
    ]

    return TechnicalEvidenceExtraction(evidence=all_evidence, skills=skills)
