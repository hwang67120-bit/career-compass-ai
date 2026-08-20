import pytest
from google import genai

from app.providers.embedding import GeminiEmbeddingProvider
from app.providers.settings import GeminiSettings

pytestmark = pytest.mark.real_gemini


@pytest.fixture
def settings() -> GeminiSettings:
    return GeminiSettings()


@pytest.fixture
def provider(settings: GeminiSettings) -> GeminiEmbeddingProvider:
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiEmbeddingProvider(client=client, model_name=settings.gemini_embedding_model)


@pytest.mark.asyncio
async def test_embed_returns_vector_with_metadata(provider: GeminiEmbeddingProvider) -> None:
    result = await provider.embed(["Python backend developer with FastAPI experience"])

    assert len(result) == 1
    assert result[0].model == provider.model_name
    assert result[0].dimension == len(result[0].values)
    assert result[0].dimension > 0


@pytest.mark.asyncio
async def test_embed_returns_vectors_in_input_order(provider: GeminiEmbeddingProvider) -> None:
    result = await provider.embed(["first text", "second text"])

    assert len(result) == 2
    assert result[0].values != result[1].values


@pytest.mark.asyncio
async def test_embed_rejects_empty_list(provider: GeminiEmbeddingProvider) -> None:
    with pytest.raises(ValueError, match="texts"):
        await provider.embed([])


@pytest.mark.asyncio
async def test_embed_rejects_blank_text(provider: GeminiEmbeddingProvider) -> None:
    with pytest.raises(ValueError, match="texts"):
        await provider.embed(["   "])
