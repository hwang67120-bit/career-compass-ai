# 채용공고 구조화 추출 계약

상태: **제안 — 코덱스 확인 필요**

이 계약은 Java가 사람인·고용24 같은 공식 채용 API에서 확보한 채용공고 원문을 Python이 구조화하는 내부 API를 정의한다. PDF·이력서 계약에는 의존하지 않는다.

- 입력이 PDF가 아니라 **텍스트**다 — 파일 업로드, PII 제거 단계가 없다. 이 텍스트는 사용자가
  직접 입력하지 않고, [`job-search-tool.md`](job-search-tool.md) 계약으로 Java가 공식 채용 API
  (사람인·고용24)에서 받아온다.
- 채용공고는 공개된 회사 정보이므로 개인정보 가드레일(계약 7절 상당)이 적용되지 않는다.

함께 적용하는 문서:

- [`docs/architecture/llm-providers.md`](../docs/architecture/llm-providers.md)
- [`docs/architecture/domain-state-ownership.md`](../docs/architecture/domain-state-ownership.md)

## 1. 실행 경계

1. Java가 [`job-search-tool.md`](job-search-tool.md) 계약으로 사람인·고용24 공식 API에서
   채용공고 원문(`sourceText`)을 받는다.
2. Java가 텍스트 길이와 기본 검증을 수행한다.
3. Java가 `JobPosting`을 등록하고 별도의 `ExtractionTask`를 생성한다.
4. Java는 사용자 요청과 분리된 실행 흐름에서 이 계약의 Python API를 호출한다.
5. Python 내부 API 호출은 한 번의 동기 HTTP 요청이다.
6. 성공하면 Java가 구조화 결과를 저장하고 `ExtractionTask`를 완료 상태로 전환한다.
7. 실패하면 Java가 실패 원인을 `ExtractionTask`에 기록한다. 재시도는 새 `ExtractionTask`를 생성한다.

`JobPosting`과 `ExtractionTask`의 상태는 Java가 소유한다. Python은 데이터베이스 상태를 직접 변경하지 않는다.

## 2. 책임 경계

### Java

- 사용자 인증과 텍스트 길이·형식을 검증한다.
- `JobPosting`과 `ExtractionTask` 식별자를 생성한다.
- Python 응답을 계약 스키마로 다시 검증한다.
- 성공한 결과를 저장하고 사용자의 수정·확정 흐름을 제공한다.

### Python

- 채용공고 원문에서 직무명·필수/우대 기술·담당 업무를 추출한다.
- 확인할 수 없는 값은 만들지 않고, 근거 없는 항목은 응답에서 제외한다(이력서와 같은 원칙, `app/services/resume_extraction.py`의 `filter_unevidenced_candidates`에 대응).
- 직무명·기술 추출(Ollama)이 재시도까지 실패하면 Gemini로 폴백한다(7절 참고). 어느 단계를 어느 provider가 처리했는지는 `modelExecutions`로 숨기지 않고 응답에 남긴다.
- 사용자 계정, 작업 상태를 저장하지 않는다.

## 3. 내부 API

```http
POST /internal/v1/job-postings/extract
Content-Type: application/json
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

파일이 없으므로 `multipart/form-data`가 아니라 JSON 본문을 사용한다.

### 요청 본문

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `jobPostingId` | UUID 문자열 | 예 | Java가 생성한 `JobPosting` 식별자 |
| `extractionTaskId` | UUID 문자열 | 예 | 이번 실행을 나타내는 `ExtractionTask` 식별자 |
| `sourceText` | string | 예 | 공백이 아닌 문자열, 설정된 최대 길이 이하 |

## 4. 성공 응답

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
    "extractionTaskId": "25a89eb8-224f-4457-ae6f-53dc32414f0d",
    "status": "EXTRACTED",
    "extraction": {
      "jobTitle": null,
      "jobTitleEvidenceIds": [],
      "responsibilities": [],
      "requiredSkills": [],
      "preferredSkills": [],
      "evidence": []
    },
    "modelExecutions": [
      {
        "stage": "CORE_EXTRACTION",
        "provider": "ollama",
        "model": "configured-core-model-name"
      },
      {
        "stage": "RESPONSIBILITY_EXTRACTION",
        "provider": "ollama",
        "model": "configured-responsibility-model-name"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-07-30T08:00:00Z"
}
```

`jobTitle`은 확신할 수 없으면 `null`이다(실제 확인됨 — 아래 8절 참고). `piiRemoved` 필드는 없다(개인정보 제거 단계가 없으므로).

### `modelExecutions` (2026-08-04, PR #45 리뷰 반영 — 코덱스 확인 필요)

직무명·기술(`CORE_EXTRACTION`)과 담당 업무(`RESPONSIBILITY_EXTRACTION`)는
서로 다른 provider·모델이 처리할 수 있다 — 기본 구성도 이미 두 단계가
서로 다른 Ollama 모델을 쓴다(8절 참고). 예전 계약의 단일
`modelProvider`/`modelName` 필드는 이 혼합 실행을 표현할 수 없어서(어느
필드값이 core 것인지 responsibility 것인지 구분 불가), 필드를 배열로
바꿨다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `modelExecutions[].stage` | `"CORE_EXTRACTION"` \| `"RESPONSIBILITY_EXTRACTION"` | 어느 추출 단계인지 |
| `modelExecutions[].provider` | string | 실제로 그 단계를 처리한 provider(`"ollama"` 또는 `"gemini"`) |
| `modelExecutions[].model` | string | 실제로 그 단계를 처리한 모델 이름 |

Ollama가 재시도까지 실패해 Gemini로 폴백한 단계는 그 단계의
`provider`/`model`만 `"gemini"`/Gemini 모델 이름으로 바뀐다(9절 참고).
배열은 항상 두 항목(`CORE_EXTRACTION`, `RESPONSIBILITY_EXTRACTION`)을
포함한다 — 실패해서 두 단계 모두 실패 처리된 경우(전체가 502/503으로
응답) 외에는 값이 비지 않는다.

### `JobPostingResponsibility`

| 필드 | 타입 | 설명 |
|---|---|---|
| `rawText` | string | 공고에 적힌 담당 업무 서술(자격 요건·우대 사항·근무 조건 제외) |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `JobPostingSkill`

| 필드 | 타입 | 설명 |
|---|---|---|
| `rawName` | string | 공고에 적힌 기술 이름 |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `JobPostingEvidence`

| 필드 | 타입 | 설명 |
|---|---|---|
| `evidenceId` | string | 응답 안에서 중복되지 않는 식별자 |
| `fieldPath` | string | 연결 대상 필드 경로 |
| `value` | string | 근거가 뒷받침하는 값 |
| `sourceText` | string | 판정을 확인할 수 있는 최소 원문 범위 |

모든 후보 항목(`responsibilities`, `requiredSkills`, `preferredSkills`, `jobTitle`을 채운 경우)은 근거를 가져야 한다. 근거를 못 만든 항목은 응답에서 제외한다(빈 값으로 채우지 않음 — `document-extraction.md`에서 코덱스와 합의한 방식과 동일).

## 5. 실패 응답

`document-extraction.md` 6절과 같은 봉투 형식이다.

| HTTP | `errorType` | 원인 | `retryable` |
|---:|---|---|---:|
| 422 | `INTERNAL_TOKEN_REQUIRED` | `X-Internal-Token` 누락 | false |
| 401 | `INTERNAL_UNAUTHORIZED` | 내부 토큰 불일치 | false |
| 422 | `INVALID_EXTRACTION_REQUEST` | UUID 형식 오류, 빈 텍스트, 최대 길이 초과 | false |
| 503 | `MODEL_UNAVAILABLE` | `OllamaUnavailableError`, `GeminiUnavailableError` | true |
| 502 | `MODEL_RESPONSE_INVALID` | 모델 응답이 스키마·근거 검증을 통과하지 못함 | false |

## 6. 성능 측정

최적화 전에 모델 호출 시간과 전체 처리 시간을 측정한다.

## 7. Gemini 폴백과 데이터 전송 범위 (2026-08-04, PR #45 리뷰 반영 — 코덱스 확인 필요)

Ollama(로컬)가 재시도까지 실패하면 Gemini(외부 API)로 폴백한다. Gemini는
외부 서비스이므로 "채용공고는 공개 정보라 정책 확인이 필요 없다"는 판단은
쓰지 않는다 — 이 판단 자체가 근거 없는 자체 추론이었다.

- **확정된 것**: Gemini로 보내기 직전 이메일·전화번호로 보이는 문자열을
  `[REDACTED]`로 치환한다(`app/guardrails/contact_info_redaction.py`).
  Ollama는 로컬 실행이라 이 처리를 거치지 않는다.
- **확인 필요**: 이 치환의 정규식이 실제 채용공고 원문 표본으로 검증되지
  않았다(변형 표기·비표준 구분자를 놓칠 수 있음). 또한 "연락처만 지우면
  충분한지, 그 이상(예: 담당자 이름)까지 지워야 하는지"는 계약으로 확정된
  범위가 아니다 — Java의 [`job-search-tool.md`](job-search-tool.md)가
  제안한 "sourceText에서 연락처·담당자 정보 제거"가 실제로 적용되면 이
  중복 방어선의 필요 범위도 다시 판단해야 한다.
- Gemini 요청 자체가 무료 등급 요청 제한으로 실패할 수 있다는 점(정책이
  아니라 가용성 문제)은 기존과 동일하게 5절 `MODEL_UNAVAILABLE`로 처리된다.

## 8. 확인 필요 (병합 전 코덱스 검토 요청)

- 이 문서 전체가 **제안**이며, Java 쪽 사용자 API·`JobPosting` 저장 모델과 함께 확정해야 한다.
- 채용공고 텍스트 최대 길이를 전용 설정으로 확정해야 한다.
- `jobTitle`이 채워지지 않는 경우(9절 참고) Java가 어떻게 처리할지 — 재시도, 사용자 직접 입력 등.
- `modelExecutions[].stage`/`provider` 값 이름(`CORE_EXTRACTION` 등, 대문자 스네이크)과 `provider` 값의 대소문자(`"ollama"` 소문자, 기존 `modelProvider` 예시와 동일하게 맞춤)가 실제로 Java 쪽 파싱 규칙과 맞는지.
- 7절의 Gemini 데이터 전송 범위·연락처 제거 검증 범위.

## 9. 실제 검증에서 발견한 사항

`qwen2.5:latest`로 실제 호출해보면 `requiredSkills`·`evidence`는 안정적으로 채우지만, `jobTitle`은 채우지 않는 경우가 실제로 재현됐다(원문에 "백엔드 개발자를 채용합니다"처럼 직무가 명확해도). 이력서와 마찬가지로 근거 없는 값을 지어내지 않는 지침을 모델이 보수적으로 따른 것으로 보인다. 스키마·프롬프트가 계약으로 확정되지 않아 이번에는 더 튜닝하지 않았다 — `docs/architecture/llm-providers.md`의 모델 평가 방식(같은 평가 자료로 통과율 비교)을 채용공고에도 적용할 필요가 있다.
