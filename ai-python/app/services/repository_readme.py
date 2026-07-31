"""GitHub 저장소의 README를 조회한다.

매니페스트 파일(`repository_evidence.py`)은 기술 근거(사실 조각)를 주지만
"이 프로젝트가 무엇을 하는지" 설명하는 서술형 텍스트는 아니다. README는
보통 그 설명을 담고 있어서, 이력서 PDF가 없는 지금 구조에서 "사용자
경험·주요 업무" 임베딩(`user_profile_embedding.py`)에 쓸 서술형 텍스트에
가장 가깝다.
"""

from app.providers.github_repository import GitHubRepositoryClient
from app.services.repository_paths import is_excluded

# README로 인정하는 파일명(대소문자 무시). 확장자 없는 README도 허용한다
# (GitHub 관례).
_README_FILENAMES = {"readme.md", "readme", "readme.rst", "readme.txt"}

# 저장소 안에 README가 여러 개 있어도(모노레포 등) 이 개수까지만 조회한다.
# 루트에 가까운 것을 우선한다.
_MAX_README_FILES = 3


def select_readme_paths(tree_paths: list[str]) -> list[str]:
    """전체 파일 경로 중 실제로 내용을 조회할 README만 고른다.

    벤더 디렉터리를 제외하고, 경로 깊이가 얕은(루트에 가까운) 파일을
    우선하며 상한(`_MAX_README_FILES`) 안에서만 고른다.
    """
    candidates = [
        path
        for path in tree_paths
        if not is_excluded(path) and path.rsplit("/", 1)[-1].lower() in _README_FILENAMES
    ]
    candidates.sort(key=lambda path: path.count("/"))
    return candidates[:_MAX_README_FILES]


async def fetch_repository_readmes(
    client: GitHubRepositoryClient,
    owner: str,
    repository: str,
    commit_sha: str,
    tree_paths: list[str],
) -> dict[str, str]:
    """README 파일들의 원문을 조회한다.

    `tree_paths`는 이미 조회된 파일 트리를 그대로 받는다 — 저장소 근거
    추출(`repository_evidence.analyze_repository`)이 이미 트리를 조회했다면
    다시 조회하지 않도록, 트리 조회는 호출자 책임으로 둔다.

    입력:
        client: GitHub API 호출 클라이언트.
        owner, repository, commit_sha: Java가 등록 시 확인한 저장소 좌표.
        tree_paths: 저장소의 전체 파일 경로 목록.

    반환:
        README 경로별 원문. README가 없으면 빈 딕셔너리.

    예외:
        `GitHubRepositoryClient`가 던지는 예외를 그대로 전달한다.
    """
    readme_paths = select_readme_paths(tree_paths)
    if not readme_paths:
        return {}

    contents: dict[str, str] = {}
    async with client.open_session() as http_client:
        for path in readme_paths:
            contents[path] = await client.fetch_file_text(
                owner, repository, commit_sha, path, http_client=http_client
            )
    return contents
