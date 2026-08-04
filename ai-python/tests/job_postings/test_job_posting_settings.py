import pytest
from pydantic import ValidationError

from app.job_postings.settings import JobPostingExtractionSettings


@pytest.mark.parametrize("invalid_limit", [0, -1])
def test_job_posting_extraction_settings_rejects_non_positive_text_limit(
    invalid_limit: int,
) -> None:
    with pytest.raises(ValidationError):
        JobPostingExtractionSettings(
            job_posting_extraction_max_text_length=invalid_limit
        )
