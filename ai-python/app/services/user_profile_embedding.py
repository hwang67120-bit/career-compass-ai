"""사용자 경험·주요 업무를 임베딩한다.

이력서 PDF를 쓰지 않는 지금 구조에서(2026-07-30 사용자 확인), "이 사용자가
무엇을 했는지"에 가장 가까운 서술형 텍스트는 사용자가 선택한 GitHub
저장소의 README다. 검증된 기술(저장소 근거·수기 입력 병합 결과,
`app/services/technical_profile.py`)과 함께 하나의 텍스트로 합쳐 임베딩한다.

README가 하나도 없거나(비공개 정보 없음, 작성 안 함) 기술 목록이 비어
있으면 임베딩할 내용이 없다는 뜻이라 예외를 던진다 — 빈 벡터를 만들어
있는 것처럼 취급하지 않는다.
"""

from typing import Protocol

from app.schemas.embedding import EmbeddingVector

# 확인 필요 — 임시값. README가 길면(뱃지·스크린샷 설명 등 포함) 임베딩
# 입력이 불필요하게 커진다. 실제 임베딩 모델의 입력 한도와 README 분포를
# 보고 재조정해야 한다.
MAX_README_CHARS_PER_FILE = 4000


class UserProfileTextEmpty(ValueError):
    """README도 기술 목록도 없어서 임베딩할 내용이 없는 경우다."""


class EmbeddingProvider(Protocol):
    """`OllamaEmbeddingProvider`·`GeminiEmbeddingProvider`가 공통으로 구현하는 부분이다."""

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]: ...


def build_user_profile_text(readme_texts: dict[str, str], skill_names: list[str]) -> str:
    """README 내용과 검증된 기술 목록을 하나의 임베딩 입력 텍스트로 합친다(순수 함수).

    입력:
        readme_texts: 저장소 경로별 README 원문(`repository_readme.fetch_repository_readmes`).
        skill_names: 검증된 기술명 목록(저장소 근거·수기 입력 병합 결과).

    반환:
        README 섹션들과 기술 목록을 이어 붙인 텍스트.

    예외:
        UserProfileTextEmpty: README와 기술 목록이 모두 비어 있는 경우.
    """
    sections: list[str] = []

    if skill_names:
        sections.append("보유 기술: " + ", ".join(skill_names))

    for path, content in readme_texts.items():
        truncated = content.strip()[:MAX_README_CHARS_PER_FILE]
        if truncated:
            sections.append(f"[{path}]\n{truncated}")

    if not sections:
        raise UserProfileTextEmpty(
            "README와 검증된 기술 목록이 모두 비어 있어 임베딩할 내용이 없습니다."
        )

    return "\n\n".join(sections)


async def embed_user_profile(
    provider: EmbeddingProvider, readme_texts: dict[str, str], skill_names: list[str]
) -> EmbeddingVector:
    """README와 검증된 기술을 합쳐 사용자 경험 임베딩을 만든다.

    입력:
        provider: 임베딩을 만들 provider(`OllamaEmbeddingProvider` 등).
        readme_texts: `repository_readme.fetch_repository_readmes`의 결과.
        skill_names: 검증된 기술명 목록.

    반환:
        사용자 경험 임베딩 하나.

    예외:
        UserProfileTextEmpty: `build_user_profile_text`와 동일.
        provider가 던지는 예외(예: `EmbeddingUnavailableError`)를 그대로 전달한다.
    """
    text = build_user_profile_text(readme_texts, skill_names)
    vectors = await provider.embed([text])
    return vectors[0]
