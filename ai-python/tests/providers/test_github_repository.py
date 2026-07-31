import pytest

from app.providers.github_repository import (
    GitHubRepositoryClient,
    GitHubRepositoryNotFoundError,
)
from app.services.repository_evidence import analyze_repository

# 실제 GitHub 공개 API를 호출한다(mock 아님) — README.md의 GitHub 예시와 동일한
# 저장소를 사용해 별도 승인·토큰 없이 항상 접근 가능하도록 한다.
_OWNER = "octocat"
_REPOSITORY = "Hello-World"
_REF = "master"


@pytest.fixture
def client() -> GitHubRepositoryClient:
    return GitHubRepositoryClient()


@pytest.mark.asyncio
async def test_fetch_tree_returns_file_paths(client: GitHubRepositoryClient) -> None:
    paths = await client.fetch_tree(_OWNER, _REPOSITORY, _REF)

    assert isinstance(paths, list)
    assert len(paths) > 0
    assert all(isinstance(path, str) for path in paths)


@pytest.mark.asyncio
async def test_fetch_tree_rejects_unknown_repository(client: GitHubRepositoryClient) -> None:
    with pytest.raises(GitHubRepositoryNotFoundError):
        await client.fetch_tree(_OWNER, "this-repository-should-not-exist-abc123", _REF)


@pytest.mark.asyncio
async def test_fetch_file_text_returns_real_file_content(
    client: GitHubRepositoryClient,
) -> None:
    paths = await client.fetch_tree(_OWNER, _REPOSITORY, _REF)
    file_path = paths[0]

    content = await client.fetch_file_text(_OWNER, _REPOSITORY, _REF, file_path)

    assert isinstance(content, str)
    assert len(content) > 0


@pytest.mark.asyncio
async def test_analyze_repository_does_not_fail_without_manifest_files(
    client: GitHubRepositoryClient,
) -> None:
    """Hello-World에는 매니페스트 파일이 없다 — 근거 없이도 오류 없이 끝나야 한다."""
    result = await analyze_repository(client, _OWNER, _REPOSITORY, _REF)

    assert result.evidence == []
    assert result.skills == []


@pytest.mark.asyncio
async def test_fetch_calls_reuse_a_shared_session_without_closing_it(
    client: GitHubRepositoryClient,
) -> None:
    """`open_session()`으로 만든 클라이언트를 여러 호출에 넘기면 재사용되고,
    호출이 끝나도 호출자가 직접 닫기 전까지는 닫히지 않는다."""
    async with client.open_session() as session:
        paths = await client.fetch_tree(_OWNER, _REPOSITORY, _REF, http_client=session)
        assert not session.is_closed

        content = await client.fetch_file_text(
            _OWNER, _REPOSITORY, _REF, paths[0], http_client=session
        )
        assert isinstance(content, str)
        assert not session.is_closed

    assert session.is_closed
