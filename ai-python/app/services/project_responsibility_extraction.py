"""저장소 README에서 프로젝트 담당 업무 근거를 뽑고 grounding으로 검증한다.

judge(contracts/job-evidence-similarity.md)의 사용자 근거
(`PROJECT_RESPONSIBILITY`)를 만드는 서비스다. 모델 출력을 README 원문과
대조(단어 겹침)해 근거가 실제로 붙었는지 확인하고, 약하면 `NEEDS_REVIEW`로
표시한다 — 지어낸 근거가 조용히 확정되지 않게 한다(AGENTS.md, 사용자 확인 원칙).

endpoint와 Java 계약은 아직 미정이라(선구현 방지) 이 서비스는 README '텍스트'를
입력으로 받는다 — GitHub 조회 주체(Java냐 Python이냐) 결정을 나중으로 미룬다.
"""

from app.providers.ollama import OllamaProvider

# 스파이크(2026-08-12, evaluation/repository_responsibility_spike.py)에서 정한 값:
# 인용 단어의 이 비율 이상이 README에 있으면 근거로 인정한다. 표·마크다운 README에서
# "그대로 연속 일치"는 너무 빡세서(모델이 조각을 이어붙임) 단어 겹침으로 잰다.
_GROUNDING_THRESHOLD = 0.8


def _tokens(text: str) -> list[str]:
    return "".join(c if c.isalnum() else " " for c in text.lower()).split()


def grounding_score(readme_text: str, quote: str) -> float:
    """인용문이 README에 얼마나 근거하는가(0.0~1.0).

    1) 공백 정규화 후 그대로(연속) 들어 있으면 1.0 — 짧은 한국어 인용은 조사
       때문에 단어 겹침만으론 낮게 나와서(예: "처리" vs "처리로") 부분일치를 먼저 본다.
    2) 아니면(표·마크다운 때문에 조각을 이어붙인 경우) 단어 겹침 비율.
    """
    norm_readme = " ".join(readme_text.split()).lower()
    norm_quote = " ".join(quote.split()).lower()
    if len(norm_quote) >= 6 and norm_quote in norm_readme:
        return 1.0

    readme_tokens = set(_tokens(readme_text))
    quote_tokens = [token for token in _tokens(quote) if len(token) >= 2]
    if not quote_tokens:
        return 0.0
    return sum(1 for token in quote_tokens if token in readme_tokens) / len(quote_tokens)


async def extract_project_responsibilities(
    readme_text: str, selected_tech: list[str], provider: OllamaProvider
) -> list[dict]:
    """README에서 담당 업무 근거 후보를 뽑아 grounding 상태와 함께 반환한다.

    provider 예외(OllamaUnavailableError/OllamaResponseError)는 그대로 전달한다.

    반환 각 항목:
        responsibility: 담당 업무 문장
        evidenceQuote: README 원문 근거
        status: "GROUNDED"(근거 충분) | "NEEDS_REVIEW"(근거 약함 — 사용자 확인)
        coverage: 근거 단어 겹침 비율
    """
    extraction = await provider.extract_project_responsibilities(readme_text, selected_tech)

    results: list[dict] = []
    for item in extraction.responsibilities:
        if not item.evidence_quote.strip():
            continue  # 근거 없는 항목은 버린다(지어내기 방지)
        coverage = grounding_score(readme_text, item.evidence_quote)
        status = "GROUNDED" if coverage >= _GROUNDING_THRESHOLD else "NEEDS_REVIEW"
        results.append(
            {
                "responsibility": item.responsibility,
                "evidenceQuote": item.evidence_quote,
                "status": status,
                "coverage": round(coverage, 2),
            }
        )
    return results
