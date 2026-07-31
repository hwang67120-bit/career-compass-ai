"""GitHub 저장소 코드에서 결정론적으로(LLM 없이) 기술 근거를 추출한다.

매니페스트 파일(예: `package.json`, `pom.xml`)의 실제 내용과 파일 확장자
개수만 사용한다. LLM 요약을 쓰지 않으므로 모든 근거는 실제로 그 파일에
있는 문자열이다 — 근거 없는 기술을 만들어내지 않는다(`AGENTS.md`).

이 휴리스틱(매니페스트 목록, 키워드 매핑, 언어 확장자 매핑)은 확인 필요
상태다. 알려진 기술만 인식하며, 목록에 없는 기술은 근거가 있어도 놓친다.
"""

from app.providers.github_repository import GitHubRepositoryClient
from app.schemas.technical_evidence import EvidenceSource, TechnicalEvidenceExtraction
from app.services.technical_evidence_builder import TechnicalEvidenceBuilder

_ID_PREFIX = "repo-evidence"

# 벤더·생성물 디렉터리는 사용자가 직접 작성한 코드가 아니므로 제외한다.
_EXCLUDED_PATH_SEGMENTS = {
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

# 매니페스트 파일명(대소문자 무시) — 이 파일들의 내용만 실제로 조회한다.
_MANIFEST_FILENAMES = {
    "package.json",
    "pom.xml",
    "build.gradle",
    "build.gradle.kts",
    "requirements.txt",
    "pyproject.toml",
    "go.mod",
    "cargo.toml",
}

# 매니페스트 파일 한 줄에서 이 키워드가 보이면(대소문자 무시) 오른쪽 기술명을
# 근거로 채택한다. 확장할수록 더 많은 기술을 인식한다(확인 필요 — 목록 확정 전).
_MANIFEST_KEYWORD_SKILLS: dict[str, str] = {
    "spring-boot": "Spring Boot",
    "springframework": "Spring Framework",
    "hibernate": "Hibernate",
    "react": "React",
    "next": "Next.js",
    "vue": "Vue.js",
    "@angular/core": "Angular",
    "express": "Express.js",
    "nestjs": "NestJS",
    "typescript": "TypeScript",
    "fastapi": "FastAPI",
    "django": "Django",
    "flask": "Flask",
    "torch": "PyTorch",
    "tensorflow": "TensorFlow",
    "numpy": "NumPy",
    "pandas": "Pandas",
    "gin-gonic": "Gin",
    "actix-web": "Actix",
}

# 파일 확장자로 판단하는 사용 언어. 흔한 소스 확장자만 다룬다(확인 필요).
_EXTENSION_LANGUAGES: dict[str, str] = {
    ".java": "Java",
    ".py": "Python",
    ".js": "JavaScript",
    ".jsx": "JavaScript",
    ".ts": "TypeScript",
    ".tsx": "TypeScript",
    ".go": "Go",
    ".rs": "Rust",
    ".kt": "Kotlin",
    ".kts": "Kotlin",
    ".rb": "Ruby",
    ".php": "PHP",
    ".swift": "Swift",
    ".cs": "C#",
    ".cpp": "C++",
    ".cc": "C++",
    ".c": "C",
    ".scala": "Scala",
}

# 언어 근거로 채택하는 최소 파일 수 — 1개짜리 우연한 파일로 언어 근거를 만들지 않는다.
_MIN_LANGUAGE_FILE_COUNT = 2
# 한 언어당 근거로 남기는 예시 파일 경로 개수.
_MAX_LANGUAGE_EVIDENCE_FILES = 3
# 매니페스트 조회 요청 수 상한 — 대형 모노레포에서 GitHub API를 과도하게 부르지 않는다.
_MAX_MANIFEST_FILES_TO_FETCH = 20


def _is_excluded(file_path: str) -> bool:
    segments = set(file_path.split("/"))
    return bool(segments & _EXCLUDED_PATH_SEGMENTS)


def select_manifest_paths(tree_paths: list[str]) -> list[str]:
    """전체 파일 경로 중 실제로 내용을 조회할 매니페스트 파일만 고른다.

    벤더 디렉터리를 제외하고, 경로 깊이가 얕은(루트에 가까운) 파일을
    우선하며 상한(`_MAX_MANIFEST_FILES_TO_FETCH`) 안에서만 고른다.
    """
    candidates = [
        path
        for path in tree_paths
        if not _is_excluded(path) and path.rsplit("/", 1)[-1].lower() in _MANIFEST_FILENAMES
    ]
    candidates.sort(key=lambda path: path.count("/"))
    return candidates[:_MAX_MANIFEST_FILES_TO_FETCH]


def _extract_manifest_evidence(
    builder: TechnicalEvidenceBuilder, file_path: str, content: str
) -> None:
    for line in content.splitlines():
        lowered = line.lower()
        for keyword, skill_name in _MANIFEST_KEYWORD_SKILLS.items():
            if keyword in lowered:
                builder.add(skill_name, detail=line.strip(), file_path=file_path)


def _extract_language_evidence(builder: TechnicalEvidenceBuilder, tree_paths: list[str]) -> None:
    paths_by_language: dict[str, list[str]] = {}
    for path in tree_paths:
        if _is_excluded(path):
            continue
        for extension, language in _EXTENSION_LANGUAGES.items():
            if path.lower().endswith(extension):
                paths_by_language.setdefault(language, []).append(path)
                break

    for language, paths in paths_by_language.items():
        if len(paths) < _MIN_LANGUAGE_FILE_COUNT:
            continue
        for example_path in paths[:_MAX_LANGUAGE_EVIDENCE_FILES]:
            builder.add(
                language,
                detail=f"{language} 소스 파일 {len(paths)}개 중 하나",
                file_path=example_path,
            )


def extract_repository_evidence(
    tree_paths: list[str], manifest_contents: dict[str, str]
) -> TechnicalEvidenceExtraction:
    """파일 경로 목록과 매니페스트 파일 내용만으로 근거를 만든다(순수 함수, 네트워크 없음).

    입력:
        tree_paths: 저장소의 전체 파일 경로 목록.
        manifest_contents: `select_manifest_paths`로 고른 경로별 파일 원문.

    반환:
        근거(evidence)와 근거로 뒷받침되는 기술(skills) 목록. 모든 근거의
        `evidenceSource`는 `REPOSITORY`다.
    """
    builder = TechnicalEvidenceBuilder(evidence_source=EvidenceSource.REPOSITORY, id_prefix=_ID_PREFIX)
    for file_path, content in manifest_contents.items():
        _extract_manifest_evidence(builder, file_path, content)
    _extract_language_evidence(builder, tree_paths)
    return builder.build()


async def analyze_repository(
    client: GitHubRepositoryClient, owner: str, repository: str, commit_sha: str
) -> TechnicalEvidenceExtraction:
    """저장소를 조회하고 근거를 추출하는 전체 과정을 수행한다.

    입력:
        client: GitHub API 호출 클라이언트.
        owner, repository, commit_sha: Java가 등록 시 확인한 저장소 좌표.

    반환:
        `extract_repository_evidence`와 동일한 결과.

    예외:
        `GitHubRepositoryClient`가 던지는 예외를 그대로 전달한다.
    """
    async with client.open_session() as http_client:
        tree_paths = await client.fetch_tree(owner, repository, commit_sha, http_client=http_client)
        manifest_paths = select_manifest_paths(tree_paths)

        manifest_contents: dict[str, str] = {}
        for file_path in manifest_paths:
            manifest_contents[file_path] = await client.fetch_file_text(
                owner, repository, commit_sha, file_path, http_client=http_client
            )

    return extract_repository_evidence(tree_paths, manifest_contents)
