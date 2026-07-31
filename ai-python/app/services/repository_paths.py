"""저장소 파일 경로 필터링에서 공통으로 쓰는 규칙이다.

`repository_evidence.py`(매니페스트)와 `repository_readme.py`(README) 둘 다
벤더·생성물 디렉터리를 같은 기준으로 제외한다.
"""

# 벤더·생성물 디렉터리는 사용자가 직접 작성한 코드가 아니므로 제외한다.
EXCLUDED_PATH_SEGMENTS = {
    "node_modules",
    "vendor",
    ".venv",
    "venv",
    "dist",
    "build",
    "target",
    ".git",
    "__pycache__",
}


def is_excluded(file_path: str) -> bool:
    """경로 어딘가에 벤더·생성물 디렉터리가 포함돼 있으면 `True`다."""
    segments = set(file_path.split("/"))
    return bool(segments & EXCLUDED_PATH_SEGMENTS)
