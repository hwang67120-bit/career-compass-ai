from app.services.repository_evidence import (
    extract_repository_evidence,
    select_manifest_paths,
)


def test_select_manifest_paths_picks_known_manifest_filenames() -> None:
    tree_paths = [
        "backend-java/build.gradle",
        "backend-java/src/main/java/App.java",
        "ai-python/pyproject.toml",
        "README.md",
    ]

    selected = select_manifest_paths(tree_paths)

    assert selected == ["backend-java/build.gradle", "ai-python/pyproject.toml"]


def test_select_manifest_paths_excludes_vendor_directories() -> None:
    tree_paths = [
        "node_modules/some-lib/package.json",
        "frontend/package.json",
        "vendor/lib/pom.xml",
    ]

    selected = select_manifest_paths(tree_paths)

    assert selected == ["frontend/package.json"]


def test_select_manifest_paths_prefers_shallower_paths_and_caps_count() -> None:
    tree_paths = [f"packages/service-{i}/package.json" for i in range(25)]
    tree_paths.append("package.json")

    selected = select_manifest_paths(tree_paths)

    assert selected[0] == "package.json"
    assert len(selected) == 20


def test_extract_repository_evidence_finds_skill_from_manifest_keyword() -> None:
    manifest_contents = {
        "frontend/package.json": '{"dependencies": {"react": "18.0.0", "next": "14.0.0"}}',
    }

    result = extract_repository_evidence(
        tree_paths=list(manifest_contents), manifest_contents=manifest_contents
    )

    skill_names = {skill.skill_name for skill in result.skills}
    assert "React" in skill_names
    assert "Next.js" in skill_names


def test_extract_repository_evidence_evidence_ids_are_internally_consistent() -> None:
    manifest_contents = {
        "backend-java/build.gradle": "implementation 'org.springframework.boot:spring-boot-starter-web'",
    }

    result = extract_repository_evidence(
        tree_paths=list(manifest_contents), manifest_contents=manifest_contents
    )

    known_evidence_ids = {evidence.evidence_id for evidence in result.evidence}
    for skill in result.skills:
        for evidence_id in skill.evidence_ids:
            assert evidence_id in known_evidence_ids


def test_extract_repository_evidence_ignores_unknown_manifest_content() -> None:
    manifest_contents = {"requirements.txt": "some-unknown-package==1.0.0"}

    result = extract_repository_evidence(
        tree_paths=list(manifest_contents), manifest_contents=manifest_contents
    )

    assert result.skills == []
    assert result.evidence == []


def test_extract_repository_evidence_requires_minimum_file_count_for_language() -> None:
    tree_paths = ["app/Main.java", "app/Helper.java", "scripts/one_off.rb"]

    result = extract_repository_evidence(tree_paths=tree_paths, manifest_contents={})

    skill_names = {skill.skill_name for skill in result.skills}
    assert "Java" in skill_names
    assert "Ruby" not in skill_names


def test_extract_repository_evidence_language_evidence_caps_example_files() -> None:
    tree_paths = [f"src/File{i}.java" for i in range(10)]

    result = extract_repository_evidence(tree_paths=tree_paths, manifest_contents={})

    java_skill = next(skill for skill in result.skills if skill.skill_name == "Java")
    assert len(java_skill.evidence_ids) == 3
