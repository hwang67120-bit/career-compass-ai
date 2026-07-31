import pytest

from app.schemas.embedding import EmbeddingVector
from app.services.user_profile_embedding import (
    MAX_README_CHARS_PER_FILE,
    UserProfileTextEmpty,
    build_user_profile_text,
    embed_user_profile,
)


class _FakeEmbeddingProvider:
    """네트워크 없이 오케스트레이션만 검증하기 위한 가짜 provider다."""

    def __init__(self) -> None:
        self.received_texts: list[str] | None = None

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]:
        self.received_texts = texts
        return [
            EmbeddingVector(values=[1.0, 0.0], model="test-model", dimension=2, version="v1")
            for _ in texts
        ]


def test_build_user_profile_text_combines_skills_and_readme() -> None:
    text = build_user_profile_text(
        readme_texts={"README.md": "이 프로젝트는 채용 매칭 서비스입니다."},
        skill_names=["Java", "Spring Boot"],
    )

    assert "보유 기술: Java, Spring Boot" in text
    assert "[README.md]" in text
    assert "채용 매칭 서비스" in text


def test_build_user_profile_text_works_with_only_skills() -> None:
    text = build_user_profile_text(readme_texts={}, skill_names=["Python"])

    assert text == "보유 기술: Python"


def test_build_user_profile_text_works_with_only_readme() -> None:
    text = build_user_profile_text(readme_texts={"README.md": "설명"}, skill_names=[])

    assert "[README.md]" in text
    assert "보유 기술" not in text


def test_build_user_profile_text_truncates_long_readme() -> None:
    long_readme = "가" * (MAX_README_CHARS_PER_FILE + 500)

    text = build_user_profile_text(readme_texts={"README.md": long_readme}, skill_names=[])

    content_section = text.split("[README.md]\n", 1)[1]
    assert len(content_section) == MAX_README_CHARS_PER_FILE


def test_build_user_profile_text_raises_when_everything_empty() -> None:
    with pytest.raises(UserProfileTextEmpty):
        build_user_profile_text(readme_texts={}, skill_names=[])


def test_build_user_profile_text_raises_when_readme_is_blank() -> None:
    with pytest.raises(UserProfileTextEmpty):
        build_user_profile_text(readme_texts={"README.md": "   "}, skill_names=[])


@pytest.mark.asyncio
async def test_embed_user_profile_passes_combined_text_to_provider() -> None:
    provider = _FakeEmbeddingProvider()

    result = await embed_user_profile(
        provider, readme_texts={"README.md": "설명"}, skill_names=["Java"]
    )

    assert provider.received_texts is not None
    assert len(provider.received_texts) == 1
    assert "보유 기술: Java" in provider.received_texts[0]
    assert result.model == "test-model"


@pytest.mark.asyncio
async def test_embed_user_profile_raises_before_calling_provider_when_empty() -> None:
    provider = _FakeEmbeddingProvider()

    with pytest.raises(UserProfileTextEmpty):
        await embed_user_profile(provider, readme_texts={}, skill_names=[])

    assert provider.received_texts is None
