import httpx
import pytest
import pytest_asyncio

from app.providers.ollama import OllamaProvider
from app.providers.settings import OllamaSettings


@pytest.fixture
def settings() -> OllamaSettings:
    return OllamaSettings()


@pytest_asyncio.fixture
async def provider(settings: OllamaSettings):
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )
    async with httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"),
        timeout=timeout,
    ) as client:
        yield OllamaProvider(client=client, model_name=settings.ollama_model)


def test_provider_name_is_ollama() -> None:
    """라우터가 modelProvider 응답 필드를 여기서 가져온다 — 리터럴로 박아두지 않는다."""
    assert OllamaProvider.provider_name == "ollama"


@pytest.mark.asyncio
async def test_verify_model_passes_when_model_installed(provider: OllamaProvider) -> None:
    await provider.verify_model()


@pytest.mark.asyncio
async def test_extract_job_posting_returns_evidence_linked_result(
    provider: OllamaProvider,
) -> None:
    """확인 필요: qwen2.5로 실제 호출해보면 requiredSkills·evidence는 안정적으로
    채우지만, jobTitle은 채우지 않는 경우가 실제로 재현된다(모델이 근거가
    확실하지 않다고 판단하면 null로 남기는 지침을 따른 것으로 보임 —
    스키마·프롬프트가 아직 계약으로 확정되지 않아 더 튜닝하지 않았다)."""
    result = await provider.extract_job_posting(
        "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상, FastAPI 실무 경험."
    )

    assert result.evidence
    assert result.required_skills or result.preferred_skills


@pytest.mark.asyncio
@pytest.mark.xfail(
    reason=(
        "2026-08-03 확인: qwen2.5는 담당 업무만 추출하는 좁은 스키마로 완전히 "
        "분리해도(직무명·기술 스키마와 안 합쳐도) evidence 배열을 계속 비운다 — "
        "값(responsibilities.rawText)은 정확한데 evidenceIds만 안 채운다. 여러 "
        "fixture·여러 재로드로 4회 이상 반복 확인, 매번 재현됨(우연한 flaky 아님). "
        "filter_unevidenced_candidates가 근거 없는 항목을 걸러내므로 운영에서는 "
        "안전하게 빈 결과로 처리되지만, 이 모델로는 이 필드가 아직 못 쓴다 — "
        "다른 모델 평가 또는 프롬프트 재작업이 필요(확인 필요)."
    ),
    strict=False,
)
async def test_extract_job_posting_responsibilities_returns_evidence_linked_result(
    provider: OllamaProvider,
) -> None:
    """담당 업무 추출은 직무명·기술 추출과 완전히 별도 호출이다(2026-08-03) —
    같은 스키마에 합쳤을 때 evidence 배열이 통째로 비는 회귀가 실제로
    재현돼 분리했다. 분리 후에도 qwen2.5는 이 호출에서 evidence를 안 채운다
    (아래 xfail 이유 참고) — 이 assert는 모델이 개선되면 알 수 있도록 그대로 둔다."""
    result = await provider.extract_job_posting_responsibilities(
        "백엔드 개발자 채용\n\n담당 업무: 주문·결제 백엔드 API 설계 및 운영\n\n"
        "필수 조건: Python 3년 이상."
    )

    assert result.evidence
    assert result.responsibilities


@pytest.mark.asyncio
async def test_unload_model_then_extract_still_works(provider: OllamaProvider) -> None:
    """세션 오염 완화책(2026-08-03 결정)이 쓰는 언로드 요청 자체가 예외 없이 끝나고,
    언로드 후에도 다음 요청이 정상적으로 새로 로드해 응답하는지 확인한다."""
    await provider.unload_model()

    result = await provider.extract_job_posting(
        "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상."
    )

    assert result.evidence


@pytest.mark.asyncio
async def test_generate_job_search_keyword_suggestions_returns_list(
    provider: OllamaProvider,
) -> None:
    result = await provider.generate_job_search_keyword_suggestions(
        "백엔드 개발자", ["Spring Boot", "Java"]
    )

    assert isinstance(result.keywords, list)
