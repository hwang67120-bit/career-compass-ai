import pytest

from app.providers.gemini import GeminiUnavailableError
from app.schemas.job_posting import (
    JobPostingCoreExtraction,
    JobPostingEvidence,
    JobPostingExtraction,
    JobPostingResponsibility,
    JobPostingResponsibilityExtraction,
    JobPostingSkill,
)
from app.services.job_posting_extraction import (
    JobPostingEvidenceValidationError,
    _merge_core_and_responsibilities,
    extract_job_posting_profile,
    filter_unevidenced_candidates,
    validate_evidence,
)

SOURCE_TEXT = "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상."

_HALLUCINATED_CORE = JobPostingCoreExtraction(
    evidence=[
        JobPostingEvidence(
            evidence_id="e1",
            field_path="requiredSkills[0].rawName",
            value="Python",
            source_text="원문에 없는 문장",
        )
    ],
    required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["e1"])],
)

_VALID_CORE = JobPostingCoreExtraction(
    evidence=[
        JobPostingEvidence(
            evidence_id="e1",
            field_path="requiredSkills[0].rawName",
            value="Python",
            source_text="Python 3년 이상",
        )
    ],
    required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["e1"])],
)

_EMPTY_RESPONSIBILITIES = JobPostingResponsibilityExtraction()


class _RetryFakeProvider:
    """근거 검증 실패 후 재시도 로직만 검증하는 가짜 provider(네트워크 없음).

    직무명·기술 추출(`extract_job_posting`)과 담당 업무 추출
    (`extract_job_posting_responsibilities`)이 독립 호출이라, 각각 별도
    응답 큐와 호출 횟수를 갖는다.
    """

    provider_name = "fake"
    model_name = "fake-model"

    def __init__(
        self,
        core_responses: list[JobPostingCoreExtraction],
        responsibility_responses: list[JobPostingResponsibilityExtraction] | None = None,
    ) -> None:
        self._core_responses = core_responses
        self._responsibility_responses = responsibility_responses or [_EMPTY_RESPONSIBILITIES] * 5
        self.core_call_count = 0
        self.responsibility_call_count = 0
        self.unload_call_count = 0

    async def extract_job_posting(self, source_text: str) -> JobPostingCoreExtraction:
        response = self._core_responses[self.core_call_count]
        self.core_call_count += 1
        return response

    async def extract_job_posting_responsibilities(
        self, source_text: str
    ) -> JobPostingResponsibilityExtraction:
        response = self._responsibility_responses[self.responsibility_call_count]
        self.responsibility_call_count += 1
        return response

    async def unload_model(self) -> None:
        self.unload_call_count += 1


class _FakeGeminiProvider:
    """Ollama가 재시도까지 실패했을 때 타는 Gemini 폴백 경로만 검증하는 가짜 provider."""

    provider_name = "gemini"
    model_name = "fake-gemini-model"

    def __init__(
        self,
        response: JobPostingExtraction | None = None,
        error: Exception | None = None,
    ) -> None:
        self._response = response
        self._error = error
        self.call_count = 0

    async def extract_job_posting(self, source_text: str) -> JobPostingExtraction:
        self.call_count += 1
        if self._error is not None:
            raise self._error
        return self._response


def test_validate_evidence_rejects_hallucinated_source_text() -> None:
    payload = JobPostingExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="requiredSkills[0].rawName",
                value="Python",
                source_text="원문에 없는 문장",
            )
        ]
    )

    with pytest.raises(JobPostingEvidenceValidationError):
        validate_evidence(payload, SOURCE_TEXT)


def test_validate_evidence_rejects_dangling_reference() -> None:
    payload = JobPostingExtraction(
        required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["ghost"])],
    )

    with pytest.raises(JobPostingEvidenceValidationError):
        validate_evidence(payload, SOURCE_TEXT)


def test_validate_evidence_works_on_core_extraction_without_responsibilities_field() -> None:
    """`JobPostingCoreExtraction`에는 `responsibilities` 속성이 없다 — getattr
    기본값으로 건너뛰고 나머지(직무명·기술) 검증은 그대로 동작해야 한다."""
    payload = JobPostingCoreExtraction(
        required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["ghost"])],
    )

    with pytest.raises(JobPostingEvidenceValidationError):
        validate_evidence(payload, SOURCE_TEXT)


def test_filter_unevidenced_candidates_removes_skill_without_evidence() -> None:
    payload = JobPostingExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="requiredSkills[0].rawName",
                value="Python",
                source_text="Python 3년 이상",
            )
        ],
        required_skills=[
            JobPostingSkill(raw_name="Python", evidence_ids=["e1"]),
            JobPostingSkill(raw_name="Java", evidence_ids=[]),
        ],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert [s.raw_name for s in filtered.required_skills] == ["Python"]
    assert [e.evidence_id for e in filtered.evidence] == ["e1"]


def test_validate_evidence_rejects_dangling_responsibility_reference() -> None:
    payload = JobPostingExtraction(
        responsibilities=[JobPostingResponsibility(raw_text="백엔드 API 운영", evidence_ids=["ghost"])],
    )

    with pytest.raises(JobPostingEvidenceValidationError):
        validate_evidence(payload, SOURCE_TEXT)


def test_filter_unevidenced_candidates_removes_responsibility_without_evidence() -> None:
    payload = JobPostingExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="responsibilities[0].rawText",
                value="백엔드 개발자를 채용합니다",
                source_text="백엔드 개발자를 채용합니다",
            )
        ],
        responsibilities=[
            JobPostingResponsibility(raw_text="백엔드 개발자를 채용합니다", evidence_ids=["e1"]),
            JobPostingResponsibility(raw_text="근거 없는 업무", evidence_ids=[]),
        ],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert [r.raw_text for r in filtered.responsibilities] == ["백엔드 개발자를 채용합니다"]
    assert [e.evidence_id for e in filtered.evidence] == ["e1"]


def test_filter_unevidenced_candidates_clears_job_title_without_evidence() -> None:
    payload = JobPostingExtraction(
        job_title="백엔드 개발자",
        job_title_evidence_ids=[],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert filtered.job_title is None


def test_merge_core_and_responsibilities_remaps_colliding_evidence_ids() -> None:
    """두 호출은 독립된 LLM 요청이라 evidenceId가 우연히 겹칠 수 있다(둘 다 "e1"부터
    시작하는 식) — 담당 업무 쪽에 `r_` 접두사를 붙여 병합 후 충돌을 막는다."""
    core = JobPostingCoreExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="requiredSkills[0].rawName",
                value="Python",
                source_text="Python 3년 이상",
            )
        ],
        required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["e1"])],
    )
    responsibilities = JobPostingResponsibilityExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="responsibilities[0].rawText",
                value="백엔드 개발",
                source_text="백엔드 개발자를 채용합니다",
            )
        ],
        responsibilities=[JobPostingResponsibility(raw_text="백엔드 개발", evidence_ids=["e1"])],
    )

    merged = _merge_core_and_responsibilities(core, responsibilities)

    assert [e.evidence_id for e in merged.evidence] == ["e1", "r_e1"]
    assert merged.responsibilities[0].evidence_ids == ["r_e1"]
    assert merged.required_skills[0].evidence_ids == ["e1"]


@pytest.mark.asyncio
async def test_extract_job_posting_profile_retries_core_once_after_unload_on_evidence_failure() -> None:
    """세션 오염 완화책(2026-08-03 결정) — 근거 검증 실패 시 언로드 후 1회 재시도해
    재시도에서 성공하면 그 결과를 돌려준다."""
    provider = _RetryFakeProvider(core_responses=[_HALLUCINATED_CORE, _VALID_CORE])

    result = await extract_job_posting_profile(SOURCE_TEXT, provider)

    assert provider.unload_call_count == 1
    assert provider.core_call_count == 2
    assert provider.responsibility_call_count == 1
    assert [s.raw_name for s in result.extraction.required_skills] == ["Python"]
    assert result.core_provider_name == "fake"
    assert result.core_model_name == "fake-model"


@pytest.mark.asyncio
async def test_extract_job_posting_profile_raises_when_core_retry_also_fails_and_no_fallback() -> None:
    """재시도까지 실패하고 폴백 provider가 없으면(2026-08-03 결정) 예외를 그대로
    전달한다 — 재시도가 진짜 결함을 감추지 않는다. 담당 업무 호출은 core 결과와
    무관하게 독립적으로 시도된다(둘 다 실패했을 때 Gemini를 한 번만 부르기
    위한 구조, 2026-08-04)."""
    provider = _RetryFakeProvider(core_responses=[_HALLUCINATED_CORE, _HALLUCINATED_CORE])

    with pytest.raises(JobPostingEvidenceValidationError):
        await extract_job_posting_profile(SOURCE_TEXT, provider)

    assert provider.unload_call_count == 1
    assert provider.core_call_count == 2
    assert provider.responsibility_call_count == 1


@pytest.mark.asyncio
async def test_extract_job_posting_profile_retries_responsibilities_independently() -> None:
    """담당 업무 추출의 검증 실패는 core 추출과 별개로 재시도된다 — core가 이미
    성공했어도 담당 업무 쪽만 다시 언로드+재시도한다."""
    hallucinated_responsibility = JobPostingResponsibilityExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="r1",
                field_path="responsibilities[0].rawText",
                value="백엔드 개발",
                source_text="원문에 없는 문장",
            )
        ],
        responsibilities=[JobPostingResponsibility(raw_text="백엔드 개발", evidence_ids=["r1"])],
    )
    valid_responsibility = JobPostingResponsibilityExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="r1",
                field_path="responsibilities[0].rawText",
                value="백엔드 개발",
                source_text="백엔드 개발자를 채용합니다",
            )
        ],
        responsibilities=[JobPostingResponsibility(raw_text="백엔드 개발", evidence_ids=["r1"])],
    )
    provider = _RetryFakeProvider(
        core_responses=[_VALID_CORE],
        responsibility_responses=[hallucinated_responsibility, valid_responsibility],
    )

    result = await extract_job_posting_profile(SOURCE_TEXT, provider)

    assert provider.core_call_count == 1
    assert provider.responsibility_call_count == 2
    assert provider.unload_call_count == 1
    assert [r.raw_text for r in result.extraction.responsibilities] == ["백엔드 개발"]


@pytest.mark.asyncio
async def test_extract_job_posting_profile_falls_back_to_gemini_when_ollama_fails() -> None:
    """2026-08-04 결정 — Ollama가 재시도까지 실패하면 Gemini로 폴백한다. 채용공고는
    공개 정보라 개인정보 가드레일이 적용되지 않으므로 이력서·희망 직무와 달리
    Gemini를 실사용 폴백으로 쓸 수 있다."""
    ollama_provider = _RetryFakeProvider(core_responses=[_HALLUCINATED_CORE, _HALLUCINATED_CORE])
    gemini_response = JobPostingExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="g1",
                field_path="requiredSkills[0].rawName",
                value="Python",
                source_text="Python 3년 이상",
            )
        ],
        required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["g1"])],
    )
    gemini_provider = _FakeGeminiProvider(response=gemini_response)

    result = await extract_job_posting_profile(
        SOURCE_TEXT, ollama_provider, fallback_provider=gemini_provider
    )

    assert gemini_provider.call_count == 1
    assert [s.raw_name for s in result.extraction.required_skills] == ["Python"]
    assert result.core_provider_name == "gemini"
    assert result.core_model_name == "fake-gemini-model"


@pytest.mark.asyncio
async def test_extract_job_posting_profile_raises_original_ollama_error_when_gemini_also_fails() -> None:
    """Ollama도 실패하고 Gemini 폴백도 실패하면, Gemini 예외가 아니라 원래
    Ollama 예외를 그대로 전달한다 — 라우터가 이미 처리하는 예외 타입을
    유지해서 새 예외 종류를 별도로 처리하지 않아도 되게 한다."""
    ollama_provider = _RetryFakeProvider(core_responses=[_HALLUCINATED_CORE, _HALLUCINATED_CORE])
    gemini_provider = _FakeGeminiProvider(error=GeminiUnavailableError("Gemini도 실패"))

    with pytest.raises(JobPostingEvidenceValidationError):
        await extract_job_posting_profile(
            SOURCE_TEXT, ollama_provider, fallback_provider=gemini_provider
        )

    assert gemini_provider.call_count == 1


@pytest.mark.asyncio
async def test_extract_job_posting_profile_raises_ollama_error_when_no_fallback_configured() -> None:
    """`fallback_provider`를 안 넘기면(기존 동작 그대로) Ollama 실패가 그대로 올라간다."""
    ollama_provider = _RetryFakeProvider(core_responses=[_HALLUCINATED_CORE, _HALLUCINATED_CORE])

    with pytest.raises(JobPostingEvidenceValidationError):
        await extract_job_posting_profile(SOURCE_TEXT, ollama_provider)
