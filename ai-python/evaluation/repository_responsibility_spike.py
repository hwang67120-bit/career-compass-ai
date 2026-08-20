"""qwen2.5·exaone3.5가 실제 GitHub 저장소 README에서 프로젝트 담당 업무 근거를
원문에 붙여(grounding) 뽑는지 확인하는 스파이크(2026-08-12).

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다.

왜: LLM-as-judge(근거 의미 비교)는 사용자 프로젝트의 담당 업무 근거를 입력으로
받는데, 이 근거를 저장소에서 뽑는 부분이 미검증이다. "소형 로컬 모델이 코드를
몰라서 판단 근거를 못 만든다"는 우려를 말이 아니라 실측으로 확인한다. 핵심 관점은
모델의 코드 지식이 아니라 **README 원문에서 근거를 붙여 뽑느냐**다.

무엇을 재나:
- grounding: 반환한 evidenceQuote가 README 원문에 실제로 존재하는가(지어냈나).
- 모델 비교: qwen2.5 vs exaone3.5 중 어느 쪽이 근거를 더 잘 붙이나.
- 사용자가 선택한 기술 스택을 힌트로 줬을 때 관련 업무에 집중하나.

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.repository_responsibility_spike
"""

import asyncio
import base64
import json
import sys

import httpx
from pydantic import BaseModel, ValidationError

from app.providers.settings import OllamaSettings

# 바꿔서 네 저장소로 테스트 가능(owner, name, 프론트에서 선택했다고 가정한 기술).
REPO_OWNER = "hwang67120-bit"
REPO_NAME = "career-compass-ai"
SELECTED_TECH = ["Java", "Spring Boot", "Python", "FastAPI"]

MODELS = ["qwen2.5:latest", "exaone3.5:latest"]

# 운영에서 쓰는 README 문자 상한(user_profile_embedding, 4000자)과 맞춘다.
README_CHAR_LIMIT = 4000


class ProjectResponsibility(BaseModel):
    responsibility: str  # judge 입력으로 쓸 짧은 담당 업무 문장
    evidence_quote: str  # README 원문에서 그대로 복사한 근거


class ProjectAnalysis(BaseModel):
    responsibilities: list[ProjectResponsibility]


_SYSTEM_PROMPT = (
    "너는 지원자의 GitHub 저장소 README를 받아, 이 프로젝트가 실제로 '하는 일'(담당 업무·기능)을 추출한다. "
    "사용자가 선택한 기술 스택을 참고해 그와 관련된 업무에 집중한다. "
    "각 항목의 evidenceQuote는 README 원문에서 글자 그대로 복사한 부분이어야 한다(요약·번역·재구성 금지). "
    "원문에 근거가 없으면 그 항목을 만들지 않는다. 뽑을 수 없으면 빈 배열을 반환한다. "
    "responsibility는 그 근거를 바탕으로 한 짧은 담당 업무 문장이다."
)


async def fetch_readme(owner: str, name: str) -> str:
    async with httpx.AsyncClient(timeout=15.0) as client:
        response = await client.get(
            f"https://api.github.com/repos/{owner}/{name}/readme",
            headers={"Accept": "application/vnd.github+json"},
        )
        response.raise_for_status()
        content = response.json()["content"]
    return base64.b64decode(content).decode("utf-8", errors="replace")


async def extract(model: str, readme: str, settings: OllamaSettings) -> ProjectAnalysis | str:
    """반환: 성공 시 ProjectAnalysis, 실패 시 사유 문자열."""
    schema = ProjectAnalysis.model_json_schema()
    user_content = (
        f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
        f"\n\n사용자가 선택한 기술 스택: {', '.join(SELECTED_TECH)}"
        f"\n\nREADME:\n{readme}"
    )
    messages = [
        {"role": "system", "content": _SYSTEM_PROMPT},
        {"role": "user", "content": user_content},
    ]
    timeout = httpx.Timeout(connect=3.0, read=180.0, write=10.0, pool=5.0)
    try:
        async with httpx.AsyncClient(
            base_url=str(settings.ollama_base_url).rstrip("/"), timeout=timeout
        ) as client:
            response = await client.post(
                "/api/chat",
                json={
                    "model": model,
                    "stream": False,
                    "format": schema,
                    "options": {"temperature": 0},
                    "messages": messages,
                },
            )
            response.raise_for_status()
            return ProjectAnalysis.model_validate_json(response.json()["message"]["content"])
    except httpx.HTTPError as error:
        return f"호출 실패: {error}"
    except (KeyError, ValueError, ValidationError) as error:
        return f"응답 형식 오류: {error}"


def is_grounded(readme: str, quote: str) -> bool:
    """evidenceQuote가 README 원문에 그대로(연속) 있는가."""
    norm_readme = " ".join(readme.split()).lower()
    norm_quote = " ".join(quote.split()).lower()
    return len(norm_quote) >= 8 and norm_quote in norm_readme


def quote_coverage(readme: str, quote: str) -> float:
    """인용문의 단어 중 몇 %가 README에 실제로 있나(조각 이어붙임·경미한 변형도 근거로 인정).

    연속 일치(is_grounded)는 표·마크다운이 많은 README에서 너무 빡세다.
    coverage가 높으면 지어낸 게 아니라 실제 내용을 재구성한 것이다.
    """
    readme_tokens = set(w for w in "".join(
        c if c.isalnum() else " " for c in readme.lower()
    ).split())
    quote_tokens = [w for w in "".join(
        c if c.isalnum() else " " for c in quote.lower()
    ).split() if len(w) >= 2]
    if not quote_tokens:
        return 0.0
    return sum(1 for w in quote_tokens if w in readme_tokens) / len(quote_tokens)


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    settings = OllamaSettings()

    print(f"[저장소] {REPO_OWNER}/{REPO_NAME}  |  선택 기술: {', '.join(SELECTED_TECH)}")
    readme_full = await fetch_readme(REPO_OWNER, REPO_NAME)
    readme = readme_full[:README_CHAR_LIMIT]
    print(f"[README] 전체 {len(readme_full)}자 중 앞 {len(readme)}자 사용")
    print("[README 미리보기]")
    print("  " + " ".join(readme[:300].split()))
    print("-" * 70)

    for model in MODELS:
        print(f"\n=== {model} ===")
        result = await extract(model, readme, settings)
        if isinstance(result, str):
            print(f"  {result}")
            continue
        items = result.responsibilities
        if not items:
            print("  (담당 업무 근거를 하나도 못 뽑음 — 빈 배열)")
            continue
        exact = grounded = 0
        for i, item in enumerate(items, 1):
            verbatim = is_grounded(readme, item.evidence_quote)
            coverage = quote_coverage(readme, item.evidence_quote)
            exact += verbatim
            real = verbatim or coverage >= 0.8  # 연속 일치 or 단어 80%+ 겹침
            grounded += real
            if verbatim:
                mark = "그대로 인용"
            elif coverage >= 0.8:
                mark = f"내용 근거O(단어 {coverage:.0%} 겹침, 재구성)"
            else:
                mark = f"근거X(단어 {coverage:.0%}만 겹침 — 지어냈을 가능성)"
            print(f"  {i}. [{mark}] {item.responsibility}")
            print(f"       ↳ 인용: {' '.join(item.evidence_quote.split())[:90]}")
        print(f"  → 그대로 인용 {exact}/{len(items)} · 내용상 근거 있음 {grounded}/{len(items)}")


if __name__ == "__main__":
    asyncio.run(main())
