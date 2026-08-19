import logging

import pytest

from app.services.performance_tracking import (
    StageOperation,
    measure_stage,
    set_last_usage,
    set_request_id,
)


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


def test_measure_stage_logs_outcome_request_id_and_tokens(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """성공 시 outcome·requestId·토큰 사용량이 구조화 로그에 함께 남는지 확인한다."""
    set_request_id("req-123")
    try:
        with (
            caplog.at_level(logging.INFO, logger="app.performance"),
            measure_stage("ollama", StageOperation.EXTRACT_JOB_POSTING),
        ):
            set_last_usage(150, 40)
    finally:
        set_request_id(None)

    message = caplog.records[0].getMessage()
    assert "outcome=success" in message
    assert "requestId=req-123" in message
    assert "promptTokens=150" in message
    assert "completionTokens=40" in message


def test_measure_stage_records_error_outcome_and_type(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """예외 시 outcome=error와 예외 타입을 남기고 그대로 다시 던진다."""
    with caplog.at_level(logging.INFO, logger="app.performance"):
        with pytest.raises(ValueError, match="boom"):
            with measure_stage("ollama", StageOperation.EXTRACT_JOB_POSTING):
                raise ValueError("boom")

    message = caplog.records[0].getMessage()
    assert "outcome=error" in message
    assert "errorType=ValueError" in message


def test_measure_stage_defaults_when_no_request_id_or_usage(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """requestId·토큰이 없으면 none으로 남는다(원문 유출 없이)."""
    set_request_id(None)
    with (
        caplog.at_level(logging.INFO, logger="app.performance"),
        measure_stage("ollama", StageOperation.EMBED_JOB_POSTING),
    ):
        pass

    message = caplog.records[0].getMessage()
    assert "requestId=none" in message
    assert "promptTokens=none" in message
    assert "completionTokens=none" in message


def test_measure_stage_does_not_leak_usage_between_stages(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """한 단계에서 기록한 토큰이 다음 단계로 새지 않는지 확인한다."""
    set_request_id(None)
    with caplog.at_level(logging.INFO, logger="app.performance"):
        with measure_stage("ollama", StageOperation.EXTRACT_JOB_POSTING):
            set_last_usage(100, 20)
        with measure_stage("ollama", StageOperation.EMBED_JOB_POSTING):
            pass

    assert "promptTokens=100" in caplog.records[0].getMessage()
    assert "promptTokens=none" in caplog.records[1].getMessage()
