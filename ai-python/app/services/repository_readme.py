"""저장소 파일 목록에서 README 파일 경로를 선별한다.

매니페스트 파일(`repository_evidence.py`)은 기술 근거(사실 조각)를 주지만
"이 프로젝트가 무엇을 하는지" 설명하는 서술형 텍스트는 아니다. README는
보통 그 설명을 담고 있어서, 이력서 PDF가 없는 지금 구조에서 "사용자
경험·주요 업무" 임베딩(`user_profile_embedding.py`)에 쓸 서술형 텍스트에
가장 가깝다.

GitHub를 직접 조회하지 않는다 — Java가 전달한 파일 목록에서 README 경로만
선별하고, 원문(text)은 Java가 함께 전달한다(책임 경계:
`docs/analysis-responsibility-boundaries.md`).
"""

from app.services.repository_paths import is_excluded

# README로 인정하는 파일명(대소문자 무시). 확장자 없는 README도 허용한다
# (GitHub 관례).
_README_FILENAMES = {"readme.md", "readme", "readme.rst", "readme.txt"}

# 저장소 안에 README가 여러 개 있어도(모노레포 등) 이 개수까지만 쓴다.
# 루트에 가까운 것을 우선한다.
_MAX_README_FILES = 3


def select_readme_paths(tree_paths: list[str]) -> list[str]:
    """전체 파일 경로 중 실제로 쓸 README만 고른다.

    벤더 디렉터리를 제외하고, 경로 깊이가 얕은(루트에 가까운) 파일을
    우선하며 상한(`_MAX_README_FILES`) 안에서만 고른다. 조회는 Java가
    담당하며, 이 함수는 전달받은 경로 목록에서 대상만 선별한다.
    """
    candidates = [
        path
        for path in tree_paths
        if not is_excluded(path) and path.rsplit("/", 1)[-1].lower() in _README_FILENAMES
    ]
    candidates.sort(key=lambda path: path.count("/"))
    return candidates[:_MAX_README_FILES]
