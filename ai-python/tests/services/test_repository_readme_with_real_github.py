import pytest

from app.providers.github_repository import GitHubRepositoryClient
from app.services.repository_readme import fetch_repository_readmes, select_readme_paths

# 실제 GitHub 공개 API를 호출한다(mock 아님) — README.md의 GitHub 예시와 동일한
# 저장소를 사용해 별도 승인·토큰 없이 항상 접근 가능하도록 한다.
_OWNER = "octocat"
_REPOSITORY = "Hello-World"
_REF = "master"


@pytest.fixture
def client() -> GitHubRepositoryClient:
    return GitHubRepositoryClient()


@pytest.mark.asyncio
async def test_fetch_repository_readmes_returns_real_readme_content(
    client: GitHubRepositoryClient,
) -> None:
    tree_paths = await client.fetch_tree(_OWNER, _REPOSITORY, _REF)
    readme_paths = select_readme_paths(tree_paths)
    assert readme_paths, "Hello-World에는 README 파일이 있어야 한다"

    contents = await fetch_repository_readmes(client, _OWNER, _REPOSITORY, _REF, tree_paths)

    assert set(contents) == set(readme_paths)
    assert all(len(text) > 0 for text in contents.values())


@pytest.mark.asyncio
async def test_fetch_repository_readmes_returns_empty_when_no_readme(
    client: GitHubRepositoryClient,
) -> None:
    contents = await fetch_repository_readmes(
        client, _OWNER, _REPOSITORY, _REF, tree_paths=["some/other/file.txt"]
    )

    assert contents == {}
