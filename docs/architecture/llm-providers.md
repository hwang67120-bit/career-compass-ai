# LLM 제공자(Ollama·Gemini) 연동

이 문서는 `AGENTS.md`의 `docs/architecture` 정의에 따라 삭제되지 않고 유지되는 아키텍처 설명이다. 계층 이름 정의는 [layer-terminology.md](layer-terminology.md)를 따른다.

## 공통 인터페이스

`ai-python/app/providers/`의 `OllamaProvider`와 `GeminiProvider`는 같은 시그니처를 공유한다.

```python
async def extract_job_posting(self, source_text: str) -> JobPostingExtraction
```

- 공통 스키마: `ai-python/app/schemas/job_posting.py`의 `Evidence`, `JobPostingExtraction`
- 목적: 두 제공자를 나중에 `LlmGateway`(노션 "Python AI 분석 구현 문서" 16번 항목)에서 서로 바꿔 끼울 수 있게 한다.
- 이 스키마는 예시일 뿐이며, 실제 채용 공고 추출 API 계약은 `contracts`에서 확정한다.

## OllamaProvider

- 위치: `ai-python/app/providers/ollama.py`
- 설정: `ai-python/app/providers/settings.py`의 `OllamaSettings` (`OLLAMA_MODEL` 필수, `OLLAMA_BASE_URL` 기본값 `http://127.0.0.1:11434`)
- `verify_model()`로 연결·모델 설치 여부를 먼저 확인하고, `extract_job_posting()`으로 구조화 결과를 받는다.
- 예외: `OllamaUnavailableError`(연결 실패·타임아웃), `OllamaResponseError`(스키마 불일치)
- 검증: 실제 로컬 Ollama 서버를 호출하는 테스트 (`tests/providers/test_ollama.py`), mock 아님.

## GeminiProvider

- 위치: `ai-python/app/providers/gemini.py`
- 설정: `OllamaSettings`와 같은 파일의 `GeminiSettings` (`GEMINI_API_KEY`, `GEMINI_MODEL` 둘 다 필수)
- 예외: `GeminiUnavailableError`(연결·서버 오류), `GeminiResponseError`(요청 거부·스키마 불일치)
- 검증: 실제 Gemini API를 호출하는 테스트 (`tests/providers/test_gemini.py`). 노션 문서의 "Gemini 무료 등급 데이터 제한" 정책에 따라 **직접 만든 가상 채용 공고만** 사용하고 실제 이력서·개인정보는 절대 전달하지 않는다.

### 구현 중 실제로 발견한 문제와 해결

1. **`response_schema`(구형 방식)는 `additionalProperties`를 거부한다.** 우리 스키마는 `pydantic.ConfigDict(extra="forbid")`를 쓰는데, 이게 JSON 스키마에 `additionalProperties: false`를 넣는다. Ollama는 이 필드를 무시하지만 Gemini API는 400 에러로 거부했다. → `GenerateContentConfig.response_json_schema`(신형 방식, `$ref`·`$defs`·`additionalProperties` 지원)로 전환해 해결했다. `response_schema`와 `response_json_schema`는 동시에 설정할 수 없다.
2. **모델 가용성은 계정별로 다르다.** 처음 시도한 `gemini-2.5-flash`는 이 API 키 계정에서 "신규 사용자에게 더 이상 제공되지 않음"(404)으로 거부됐다. 실제 사용 가능한 모델은 `client.models.list()`로 직접 조회해서 확인해야 하며, 노션 문서나 외부 요금표에 적힌 모델명을 그대로 믿지 않는다.

## 모델 선정 상태 — 확인 필요

- `.env`의 `OLLAMA_MODEL=qwen2.5:latest`(채용공고용), `GEMINI_MODEL=gemini-flash-latest`는 **연동 코드 검증용 임시값**이며 최종 채택된 모델이 아니다.
- 실제 모델 선정은 노션 "의존성" 문서의 기준(한국어 이력서·공고 용어, JSON 형식 준수, 응답 시간)으로 별도 평가한 뒤 확정한다.
- `.env.example`에는 값 없이 항목 이름만 남겨 이 상태를 표시한다.

### 이력서 전용 모델(`OLLAMA_RESUME_MODEL`) — 2026-07-29 평가 결과 채택

`evaluation/model_comparison.py`로 설치된 모델 3종을 같은 평가 PDF(`tests/fixtures/resumes/`) 4개로 비교했다(스키마 통과율, 근거 검증 통과율, 처리 시간).

| 모델 | 통과율 | 평균 시간 | 비고 |
|---|---:|---:|---|
| `exaone3.5:latest` | 100% | 36.8s | 채택 |
| `qwen2.5:latest` | 75% | 70.9s | 1건 타임아웃(120s) |
| `llama3.2:latest` | 0% | 60.7s | 근거 원문 할루시네이션 — 근거 자체를 지어냄 |

**계약은 완화하지 않는다.** 처음엔 "모든 후보 항목이 근거를 가져야 한다"는 계약 5절 요건 자체를 완화하는 안을 시도했으나(코덱스 검토 결과) 되돌렸다. 대신 `app/services/resume_extraction.py`의 `filter_unevidenced_candidates`가 **근거를 못 채운 후보 항목을 응답에서 조용히 제거**해서 계약을 지킨다 — 근거 없는 값을 사실처럼 반환하지 않되, 전체 요청을 실패시키지도 않는다. 할루시네이션(근거 원문이 실제 페이지에 없음)·중복 근거 ID·유령 참조는 여전히 오류로 차단한다.

위 평가는 이 필터링을 적용한 뒤의 통과율이다(필터링 후에는 "근거 없는 항목이 있는지"가 아니라 "할루시네이션·유령 참조가 있는지"만 통과 여부를 가른다).

3개 모델 모두 초기(필터링 적용 전) 평가에서는 0%였다. `evidence` 필드가 스키마상 후보 목록보다 뒤에 선언돼 있던 것을 원인으로 추정해 맨 앞으로 옮겨봤지만, 이건 **확인된 단일 원인이 아니라 시도한 개선 중 하나**였다 — 실제로는 `exaone3.5`에는 효과가 없었고 `llama3.2`는 실패 유형만 바뀌었으며 `qwen2.5`는 오히려 타임아웃이 늘었다. 위 표의 통과율 차이를 만든 실질적 변화는 필터링 도입이다.

이 평가는 4개 PDF, 3개 모델로 진행한 1차 결과이며 최종 확정이 아니다 — 더 큰 모델이나 다른 평가 자료로 재평가할 수 있다.

## 테스트 환경 참고

- `ai-python/tests/conftest.py`에서 `truststore.inject_into_ssl()`을 호출한다. 이는 이 개발 환경의 회사망 TLS 검사(프록시) 때문에 필요한 **테스트 실행 전용** 우회이며, `app/` 운영 코드에는 적용하지 않는다.
