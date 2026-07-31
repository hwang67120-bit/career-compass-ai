import pytest

from app.schemas.embedding import EmbeddingVector
from app.schemas.skill_tag_match import TagMatchRecommendation
from app.services.skill_tag_matching import (
    SUGGESTION_SIMILARITY_THRESHOLD,
    decide_skill_tag_match,
    find_best_canonical_match,
    match_skill_tag,
)


def make_vector(values: list[float]) -> EmbeddingVector:
    return EmbeddingVector(values=values, model="test-model", dimension=len(values), version="v1")


class _FakeEmbeddingProvider:
    """네트워크 없이 오케스트레이션만 검증하기 위한 가짜 provider다."""

    def __init__(self, vectors_by_text: dict[str, EmbeddingVector]) -> None:
        self._vectors_by_text = vectors_by_text
        self.received_texts: list[str] | None = None

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]:
        self.received_texts = texts
        return [self._vectors_by_text[text] for text in texts]


def test_find_best_canonical_match_picks_highest_similarity() -> None:
    candidate = make_vector([1.0, 0.0])
    result = find_best_canonical_match(
        candidate_vector=candidate,
        canonical_tags=["orthogonal", "identical"],
        canonical_vectors=[make_vector([0.0, 1.0]), make_vector([1.0, 0.0])],
    )

    assert result == ("identical", pytest.approx(1.0))


def test_find_best_canonical_match_returns_none_for_empty_list() -> None:
    candidate = make_vector([1.0, 0.0])
    result = find_best_canonical_match(candidate, [], [])

    assert result is None


def test_decide_skill_tag_match_returns_exact_match_case_insensitive() -> None:
    result = decide_skill_tag_match("spring boot", ["Spring Boot", "Java"], best_match=None)

    assert result.recommendation == TagMatchRecommendation.EXACT_MATCH
    assert result.best_match_tag == "Spring Boot"
    assert result.similarity == 1.0


def test_decide_skill_tag_match_suggests_correction_above_threshold() -> None:
    result = decide_skill_tag_match(
        "스프링부트",
        ["Spring Boot"],
        best_match=("Spring Boot", SUGGESTION_SIMILARITY_THRESHOLD + 0.01),
    )

    assert result.recommendation == TagMatchRecommendation.SUGGEST_CORRECTION
    assert result.best_match_tag == "Spring Boot"


def test_decide_skill_tag_match_returns_no_match_below_threshold() -> None:
    result = decide_skill_tag_match(
        "우쿨렐레",
        ["Spring Boot"],
        best_match=("Spring Boot", SUGGESTION_SIMILARITY_THRESHOLD - 0.2),
    )

    assert result.recommendation == TagMatchRecommendation.NO_MATCH


def test_decide_skill_tag_match_returns_no_match_when_canonical_list_empty() -> None:
    result = decide_skill_tag_match("Rust", [], best_match=None)

    assert result.recommendation == TagMatchRecommendation.NO_MATCH
    assert result.best_match_tag is None
    assert result.similarity is None


@pytest.mark.asyncio
async def test_match_skill_tag_skips_embedding_call_on_exact_match() -> None:
    provider = _FakeEmbeddingProvider({})

    result = await match_skill_tag(provider, "Java", ["Java", "Python"])

    assert result.recommendation == TagMatchRecommendation.EXACT_MATCH
    assert provider.received_texts is None


@pytest.mark.asyncio
async def test_match_skill_tag_skips_embedding_call_when_canonical_list_empty() -> None:
    provider = _FakeEmbeddingProvider({})

    result = await match_skill_tag(provider, "Java", [])

    assert result.recommendation == TagMatchRecommendation.NO_MATCH
    assert provider.received_texts is None


@pytest.mark.asyncio
async def test_match_skill_tag_calls_embedding_provider_for_fuzzy_case() -> None:
    provider = _FakeEmbeddingProvider(
        {
            "스프링부트": make_vector([1.0, 0.0]),
            "Spring Boot": make_vector([0.99, 0.01]),
        }
    )

    result = await match_skill_tag(provider, "스프링부트", ["Spring Boot"])

    assert provider.received_texts == ["스프링부트", "Spring Boot"]
    assert result.recommendation == TagMatchRecommendation.SUGGEST_CORRECTION
    assert result.best_match_tag == "Spring Boot"
