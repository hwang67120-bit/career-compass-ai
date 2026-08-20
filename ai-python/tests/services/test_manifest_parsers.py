from app.services.manifest_parsers import (
    extract_cargo_toml_dependencies,
    extract_go_mod_dependencies,
    extract_gradle_dependencies,
    extract_package_json_dependencies,
    extract_pom_xml_dependencies,
    extract_pyproject_toml_dependencies,
    extract_requirements_txt_dependencies,
)


def test_extract_package_json_dependencies_reads_all_dependency_sections() -> None:
    content = """
    {
        "dependencies": {"react": "18.0.0", "next": "14.0.0"},
        "devDependencies": {"typescript": "5.0.0"},
        "peerDependencies": {"react-dom": "18.0.0"}
    }
    """

    names = extract_package_json_dependencies(content)

    assert set(names) == {"react", "next", "typescript", "react-dom"}


def test_extract_package_json_dependencies_ignores_unrelated_text_in_scripts() -> None:
    """예전 방식(문자열 검색)이라면 scripts 안의 'react-scripts build'도 오탐했을 것."""
    content = """
    {
        "dependencies": {"vue": "3.0.0"},
        "scripts": {"build": "react-scripts build"}
    }
    """

    names = extract_package_json_dependencies(content)

    assert names == ["vue"]


def test_extract_package_json_dependencies_returns_empty_for_invalid_json() -> None:
    assert extract_package_json_dependencies("not valid json") == []


def test_extract_pom_xml_dependencies_reads_artifact_ids_with_namespace() -> None:
    content = """<?xml version="1.0"?>
    <project xmlns="http://maven.apache.org/POM/4.0.0">
      <dependencies>
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
          <groupId>org.hibernate</groupId>
          <artifactId>hibernate-core</artifactId>
        </dependency>
      </dependencies>
    </project>
    """

    names = extract_pom_xml_dependencies(content)

    assert names == ["spring-boot-starter-web", "hibernate-core"]


def test_extract_pom_xml_dependencies_returns_empty_for_malformed_xml() -> None:
    assert extract_pom_xml_dependencies("<not-closed>") == []


def test_extract_requirements_txt_dependencies_strips_version_specifiers_and_comments() -> None:
    content = """
    fastapi>=0.139.2
    # a comment line
    numpy==2.2.6  # inline comment
    -r other-requirements.txt

    google-genai>=2.14.0
    """

    names = extract_requirements_txt_dependencies(content)

    assert names == ["fastapi", "numpy", "google-genai"]


def test_extract_pyproject_toml_dependencies_reads_pep621_style() -> None:
    content = """
    [project]
    name = "example"
    dependencies = ["fastapi>=0.100.0", "httpx>=0.28.1"]

    [project.optional-dependencies]
    dev = ["pytest>=8.0.0"]
    """

    names = extract_pyproject_toml_dependencies(content)

    assert set(names) == {"fastapi", "httpx", "pytest"}


def test_extract_pyproject_toml_dependencies_reads_poetry_style_and_excludes_python() -> None:
    content = """
    [tool.poetry.dependencies]
    python = "^3.10"
    django = "^5.0"

    [tool.poetry.group.dev.dependencies]
    mypy = "^1.0"
    """

    names = extract_pyproject_toml_dependencies(content)

    assert set(names) == {"django", "mypy"}
    assert "python" not in names


def test_extract_pyproject_toml_dependencies_returns_empty_for_invalid_toml() -> None:
    assert extract_pyproject_toml_dependencies("not = [valid toml") == []


def test_extract_cargo_toml_dependencies_reads_dependency_sections() -> None:
    content = """
    [dependencies]
    actix-web = "4.0"

    [dev-dependencies]
    tokio-test = "0.4"
    """

    names = extract_cargo_toml_dependencies(content)

    assert set(names) == {"actix-web", "tokio-test"}


def test_extract_gradle_dependencies_reads_quoted_coordinates_groovy_and_kotlin() -> None:
    content = """
    dependencies {
        implementation 'org.springframework.boot:spring-boot-starter-web'
        implementation("org.hibernate:hibernate-core:6.4.0")
        testImplementation "junit:junit:4.13"
    }
    """

    names = extract_gradle_dependencies(content)

    assert set(names) == {
        "org.springframework.boot:spring-boot-starter-web",
        "org.hibernate:hibernate-core:6.4.0",
        "junit:junit:4.13",
    }


def test_extract_go_mod_dependencies_reads_require_block_and_single_line() -> None:
    content = """
    module example.com/app

    go 1.21

    require github.com/gin-gonic/gin v1.9.1

    require (
        github.com/stretchr/testify v1.8.4
        golang.org/x/sync v0.5.0
    )
    """

    names = extract_go_mod_dependencies(content)

    assert set(names) == {
        "github.com/gin-gonic/gin",
        "github.com/stretchr/testify",
        "golang.org/x/sync",
    }
