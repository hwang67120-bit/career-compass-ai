from app.services.repository_readme import select_readme_paths


def test_select_readme_paths_picks_known_readme_filenames() -> None:
    tree_paths = [
        "README.md",
        "backend-java/src/main/java/App.java",
        "docs/architecture/README.md",
    ]

    selected = select_readme_paths(tree_paths)

    assert selected == ["README.md", "docs/architecture/README.md"]


def test_select_readme_paths_accepts_extensionless_readme() -> None:
    tree_paths = ["README", "src/main.py"]

    selected = select_readme_paths(tree_paths)

    assert selected == ["README"]


def test_select_readme_paths_is_case_insensitive() -> None:
    tree_paths = ["readme.md", "Readme.txt"]

    selected = select_readme_paths(tree_paths)

    assert set(selected) == {"readme.md", "Readme.txt"}


def test_select_readme_paths_excludes_vendor_directories() -> None:
    tree_paths = ["node_modules/some-lib/README.md", "README.md"]

    selected = select_readme_paths(tree_paths)

    assert selected == ["README.md"]


def test_select_readme_paths_prefers_shallower_paths_and_caps_count() -> None:
    tree_paths = [f"packages/service-{i}/README.md" for i in range(5)]
    tree_paths.append("README.md")

    selected = select_readme_paths(tree_paths)

    assert selected[0] == "README.md"
    assert len(selected) == 3


def test_select_readme_paths_returns_empty_when_no_readme() -> None:
    tree_paths = ["src/main.py", "backend-java/build.gradle"]

    selected = select_readme_paths(tree_paths)

    assert selected == []
