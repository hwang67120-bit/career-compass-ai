"""사용자 GitHub 저장소 README에서 뽑는 프로젝트 담당 업무 근거 스키마다.

judge(contracts/job-evidence-similarity.md)의 사용자 근거
(`PROJECT_RESPONSIBILITY`) 입력을 만든다. 모델은 README 원문 근거에 붙은
담당 업무만 반환한다(근거 없는 항목은 만들지 않는다, AGENTS.md).
"""

from pydantic import BaseModel


class ProjectResponsibilityCandidate(BaseModel):
    responsibility: str  # 담당 업무 문장
    evidence_quote: str  # README 원문에서 그대로 복사한 근거


class ProjectResponsibilityExtraction(BaseModel):
    responsibilities: list[ProjectResponsibilityCandidate]
