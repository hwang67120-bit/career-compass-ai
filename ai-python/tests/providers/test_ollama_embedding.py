import httpx
import pytest
import pytest_asyncio

from app.providers.embedding import OllamaEmbeddingProvider
from app.providers.settings import OllamaSettings


@pytest.fixture
def settings() -> OllamaSettings:
    return OllamaSettings()


@pytest_asyncio.fixture
async def provider(settings: OllamaSettings):
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )
    async with httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"),
        timeout=timeout,
    ) as client:
        yield OllamaEmbeddingProvider(client=client, model_name=settings.ollama_embedding_model)


@pytest.mark.asyncio
async def test_embed_returns_vector_with_metadata(provider: OllamaEmbeddingProvider) -> None:
    result = await provider.embed(["Python backend developer with FastAPI experience"])

    assert len(result) == 1
    assert result[0].model == provider.model_name
    assert result[0].dimension == len(result[0].values)
    assert result[0].dimension > 0


@pytest.mark.asyncio
async def test_embed_returns_vectors_in_input_order(provider: OllamaEmbeddingProvider) -> None:
    result = await provider.embed(["first text", "second text"])

    assert len(result) == 2
    assert result[0].values != result[1].values


@pytest.mark.asyncio
async def test_embed_rejects_empty_list(provider: OllamaEmbeddingProvider) -> None:
    with pytest.raises(ValueError, match="texts"):
        await provider.embed([])


@pytest.mark.asyncio
async def test_embed_rejects_blank_text(provider: OllamaEmbeddingProvider) -> None:
    with pytest.raises(ValueError, match="texts"):
        await provider.embed(["   "])
