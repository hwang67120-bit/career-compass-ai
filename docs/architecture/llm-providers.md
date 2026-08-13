# LLM 제공자(Ollama·Gemini) 연동

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

(2026-08-03: 이력서 전용 모델 평가 절 — PDF·이력서 파이프라인이 MVP에서 제거되면서 지웠다.
필터링 기반 근거 검증 원칙과 evidence-first 순서 실험 등 방법론은 채용공고 추출에도 그대로
쓰이고 있다 — 필요하면 git 이력에서 확인 가능.)

## 테스트 환경 참고

- `ai-python/tests/conftest.py`에서 `truststore.inject_into_ssl()`을 호출한다. 이는 이 개발 환경의 회사망 TLS 검사(프록시) 때문에 필요한 **테스트 실행 전용** 우회이며, `app/` 운영 코드에는 적용하지 않는다.
