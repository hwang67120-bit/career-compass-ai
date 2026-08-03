import logging

import pytest

from app.services.performance_tracking import measure_stage


def test_measure_stage_logs_stage_name_and_duration(caplog: pytest.LogCaptureFixture) -> None:
    with caplog.at_level(logging.INFO, logger="app.performance"), measure_stage("example.stage"):
        pass

    assert len(caplog.records) == 1
    message = caplog.records[0].getMessage()
    assert "stage=example.stage" in message
    assert "duration_ms=" in message


def test_measure_stage_does_not_log_stage_content_only_name(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """단계 이름 외의 내용(예: 실제로 처리한 값)이 로그에 안 남는지 확인한다."""
    secret_value = "실제 채용공고 원문 텍스트입니다"

    with caplog.at_level(logging.INFO, logger="app.performance"), measure_stage("example.stage"):
        _ = secret_value

    assert secret_value not in caplog.records[0].getMessage()


def test_measure_stage_still_logs_and_reraises_on_exception(
    caplog: pytest.LogCaptureFixture,
) -> None:
    with (
        caplog.at_level(logging.INFO, logger="app.performance"),
        pytest.raises(ValueError, match="boom"),
        measure_stage("example.failing_stage"),
    ):
        raise ValueError("boom")

    assert len(caplog.records) == 1
    assert "stage=example.failing_stage" in caplog.records[0].getMessage()
