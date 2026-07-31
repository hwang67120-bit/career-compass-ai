"""매니페스트 파일에서 실제 의존성 식별자를 뽑아낸다.

이전에는 파일 전체를 텍스트로 보고 키워드를 문자열 검색했는데, 이미 각
형식에 맞는 표준 파서가 있는데도 그걸 쓰지 않는 "바퀴의 재발명"이었다
(2026-08-01 문제 제기). 여기서는 실제 형식(JSON·XML·TOML)에 맞는 파서로
의존성 이름만 정확히 뽑고, 파일 전체에서 우연히 걸리는 문자열은 근거로
쓰지 않는다.

`build.gradle`·`build.gradle.kts`(Groovy/Kotlin DSL)와 `go.mod`는 표준
파서가 없어 여전히 정규식으로 좌표·모듈 경로만 뽑는다(전체 줄 대신 실제
좌표 문자열만 대상으로 하므로 이전보다는 더 정확하다).
"""

import json
import re
import sys
from json import JSONDecodeError

from defusedxml import ElementTree
from defusedxml.ElementTree import ParseError as XmlParseError
from packaging.requirements import InvalidRequirement, Requirement

if sys.version_info >= (3, 11):
    import tomllib
else:
    import tomli as tomllib


def extract_package_json_dependencies(content: str) -> list[str]:
    """`dependencies`·`devDependencies`·`peerDependencies` 키(패키지 이름)를 뽑는다."""
    try:
        data = json.loads(content)
    except JSONDecodeError:
        return []
    if not isinstance(data, dict):
        return []

    names: list[str] = []
    for key in ("dependencies", "devDependencies", "peerDependencies"):
        section = data.get(key)
        if isinstance(section, dict):
            names.extend(section.keys())
    return names


def extract_pom_xml_dependencies(content: str) -> list[str]:
    """`<dependency><artifactId>` 값을 뽑는다. 네임스페이스 유무와 무관하게 찾는다."""
    try:
        root = ElementTree.fromstring(content)
    except XmlParseError:
        return []
    return [
        element.text.strip()
        for element in root.findall(".//{*}artifactId")
        if element.text and element.text.strip()
    ]


def _requirement_name(specifier: str) -> str | None:
    try:
        return Requirement(specifier).name
    except InvalidRequirement:
        return None


def extract_requirements_txt_dependencies(content: str) -> list[str]:
    """`requirements.txt`의 각 줄을 PEP 508 요구사항으로 파싱해 패키지 이름만 뽑는다."""
    names: list[str] = []
    for raw_line in content.splitlines():
        line = raw_line.split("#", 1)[0].strip()
        if not line or line.startswith(("-r", "-e", "-", "--")):
            continue
        name = _requirement_name(line)
        if name:
            names.append(name)
    return names


def _poetry_dependency_names(section: object) -> list[str]:
    if not isinstance(section, dict):
        return []
    return [name for name in section if name.lower() != "python"]


def extract_pyproject_toml_dependencies(content: str) -> list[str]:
    """PEP 621(`project.dependencies`)과 Poetry(`tool.poetry.dependencies` 등) 둘 다 지원한다."""
    try:
        data = tomllib.loads(content)
    except tomllib.TOMLDecodeError:
        return []

    names: list[str] = []

    project = data.get("project")
    if isinstance(project, dict):
        for specifier in project.get("dependencies") or []:
            name = _requirement_name(specifier)
            if name:
                names.append(name)
        optional = project.get("optional-dependencies")
        if isinstance(optional, dict):
            for specifiers in optional.values():
                for specifier in specifiers or []:
                    name = _requirement_name(specifier)
                    if name:
                        names.append(name)

    tool = data.get("tool")
    poetry = tool.get("poetry") if isinstance(tool, dict) else None
    if isinstance(poetry, dict):
        names.extend(_poetry_dependency_names(poetry.get("dependencies")))
        names.extend(_poetry_dependency_names(poetry.get("dev-dependencies")))
        groups = poetry.get("group")
        if isinstance(groups, dict):
            for group in groups.values():
                if isinstance(group, dict):
                    names.extend(_poetry_dependency_names(group.get("dependencies")))

    return names


def extract_cargo_toml_dependencies(content: str) -> list[str]:
    """`[dependencies]`·`[dev-dependencies]`·`[build-dependencies]` 섹션 키(crate 이름)를 뽑는다."""
    try:
        data = tomllib.loads(content)
    except tomllib.TOMLDecodeError:
        return []

    names: list[str] = []
    for key in ("dependencies", "dev-dependencies", "build-dependencies"):
        section = data.get(key)
        if isinstance(section, dict):
            names.extend(section.keys())
    return names


# "group:artifact" 또는 "group:artifact:version" 형태의 인용된 좌표만 뽑는다
# (예: implementation("org.springframework.boot:spring-boot-starter-web")).
_GRADLE_COORDINATE_PATTERN = re.compile(r"[\"']([\w.\-]+:[\w.\-]+(?::[\w.\-+]+)?)[\"']")


def extract_gradle_dependencies(content: str) -> list[str]:
    """Groovy·Kotlin DSL 둘 다에서 흔한 문자열 좌표 표기만 뽑는다(확인 필요 — 표준 파서 없음)."""
    return _GRADLE_COORDINATE_PATTERN.findall(content)


_GO_MOD_MODULE_VERSION_PATTERN = re.compile(r"^([\w.\-/]+)\s+v[\w.\-+]+")


def extract_go_mod_dependencies(content: str) -> list[str]:
    """`require` 블록과 한 줄 선언 모두에서 모듈 경로만 뽑는다(확인 필요 — 표준 파서 없음)."""
    modules: list[str] = []
    in_require_block = False
    for raw_line in content.splitlines():
        line = raw_line.strip()
        if line.startswith("require ("):
            in_require_block = True
            continue
        if in_require_block and line == ")":
            in_require_block = False
            continue

        candidate = line[len("require ") :] if line.startswith("require ") else line if in_require_block else None
        if candidate is None:
            continue
        match = _GO_MOD_MODULE_VERSION_PATTERN.match(candidate)
        if match:
            modules.append(match.group(1))
    return modules


MANIFEST_DEPENDENCY_EXTRACTORS = {
    "package.json": extract_package_json_dependencies,
    "pom.xml": extract_pom_xml_dependencies,
    "build.gradle": extract_gradle_dependencies,
    "build.gradle.kts": extract_gradle_dependencies,
    "requirements.txt": extract_requirements_txt_dependencies,
    "pyproject.toml": extract_pyproject_toml_dependencies,
    "go.mod": extract_go_mod_dependencies,
    "cargo.toml": extract_cargo_toml_dependencies,
}
