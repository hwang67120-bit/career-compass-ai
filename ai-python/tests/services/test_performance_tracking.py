import logging

import pytest

from app.services.performance_tracking import StageOperation, measure_stage


def test_measure_stage_logs_component_operation_and_duration(
    caplog: pytest.LogCaptureFixture,
) -> None:
    with (
        caplog.at_level(logging.INFO, logger="app.performance"),
        measure_stage("ollama", StageOperation.EXTRACT_JOB_POSTING),
    ):
        pass

    assert len(caplog.records) == 1
    message = caplog.records[0].getMessage()
    assert "stage=ollama.extract_job_posting" in message
    assert "duration_ms=" in message


def test_measure_stage_does_not_log_stage_content_only_name(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """단계 이름 외의 내용(예: 실제로 처리한 값)이 로그에 안 남는지 확인한다."""
    secret_value = "실제 채용공고 원문 텍스트입니다"

    with (
        caplog.at_level(logging.INFO, logger="app.performance"),
        measure_stage("ollama", StageOperation.EXTRACT_JOB_POSTING),
    ):
        _ = secret_value

    assert secret_value not in caplog.records[0].getMessage()


def test_measure_stage_still_logs_and_reraises_on_exception(
    caplog: pytest.LogCaptureFixture,
) -> None:
    with (
        caplog.at_level(logging.INFO, logger="app.performance"),
        pytest.raises(ValueError, match="boom"),
        measure_stage("github", StageOperation.GITHUB_FETCH_TREE),
    ):
        raise ValueError("boom")

    assert len(caplog.records) == 1
    assert "stage=github.fetch_tree" in caplog.records[0].getMessage()


def test_measure_stage_combines_component_and_operation_for_different_providers(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """같은 작업이라도 provider(component)가 다르면 로그로 구분할 수 있어야 한다."""
    with (
        caplog.at_level(logging.INFO, logger="app.performance"),
        measure_stage("gemini", StageOperation.EMBED_USER_PROFILE),
    ):
        pass

    assert "stage=gemini.embed_user_profile" in caplog.records[0].getMessage()
