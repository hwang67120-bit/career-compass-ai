# PDF 문서 추출 계약

상태: **MVP 확정**

이 계약은 8월 21일 MVP 흐름(`PDF 등록 → 추출·수정·확정 → 채용공고 등록 → 조건 판정+의미 분석 → 결과 화면 → 테스트 배포`) 중 Java가 생성한 문서 추출 작업을 Python이 실행하는 내부 API만 정의한다.

함께 적용하는 문서:

- [`docs/architecture/domain-state-ownership.md`](../docs/architecture/domain-state-ownership.md)
- [`docs/architecture/java-python-connection.md`](../docs/architecture/java-python-connection.md)
- [`docs/architecture/guardrails.md`](../docs/architecture/guardrails.md)
- [`docs/architecture/llm-providers.md`](../docs/architecture/llm-providers.md)

## 1. 실행 경계

외부 사용자 요청과 Java–Python 내부 호출의 경계를 분리한다.

1. Java가 사용자에게서 PDF를 받는다.
2. Java가 파일 형식, 설정된 최대 크기와 기본 안전 검사를 수행한다.
3. 검증이 실패하면 `UserDocument`와 `ExtractionTask`를 생성하지 않고 사용자에게 즉시 오류를 반환한다.
4. 검증이 통과하면 Java가 `UserDocument`를 등록하고 별도의 `ExtractionTask`를 생성한다.
5. Java는 사용자 요청과 분리된 실행 흐름에서 이 계약의 Python API를 호출한다.
6. Python 내부 API 호출은 한 번의 동기 HTTP 요청이지만, 사용자 관점의 문서 추출은 비동기 작업이다.
7. 성공하면 Java가 `ProfileCandidate`를 생성하고 `ExtractionTask`를 완료 상태로 전환한다.
8. 실패하면 Java가 실패 원인을 `ExtractionTask`에 기록한다. 재시도는 기존 작업을 갱신하지 않고 새 `ExtractionTask`를 생성한다.

`UserDocument`, `ExtractionTask`, `ProfileCandidate`와 `UserProfile`의 상태는 Java가 소유한다. Python은 데이터베이스 상태를 직접 변경하지 않는다.

## 2. 책임 경계

### Java

- 사용자 인증과 사용자별 자료 접근 권한을 확인한다.
- PDF 형식, 설정된 최대 크기와 업로드 요청을 검증한다.
- `UserDocument`와 `ExtractionTask` 식별자를 생성한다.
- Python 호출과 작업 상태 전이를 오케스트레이션한다.
- Python 응답을 계약 스키마로 다시 검증한다.
- 성공한 후보를 `ProfileCandidate`로 저장하고 사용자의 수정·확정 흐름을 제공한다.
- 실패 응답의 `retryable`은 사용자에게 재시도 가능 여부를 안내하는 용도로만 사용한다.

### Python

- PDF 바이트에서 페이지별 텍스트를 추출한다.
- 추출 직후 개인정보 제거를 수행한다.
- 개인정보가 제거된 텍스트만 Ollama 또는 Gemini에 전달한다.
- 구조화 후보와 최소 범위의 원문 근거를 반환한다.
- 사용자 계정, 작업 상태와 확정 프로필을 저장하지 않는다.
- PDF 원본, 추출 원문 전체와 개인정보가 포함된 값을 일반 로그에 기록하지 않는다.

Java는 PDF 바이트에서 내용을 미리 제거할 수 없으므로 텍스트 개인정보 제거의 실행 책임은 Python에 둔다. Java는 Python이 `piiRemoved=true`로 반환한 성공 응답만 저장한다.

## 3. 내부 API

```http
POST /internal/v1/documents/extract
Content-Type: multipart/form-data
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

PDF는 Base64 JSON이 아니라 `multipart/form-data`로 전달한다. 바이너리를 별도 인코딩하지 않고 파일과 폼 필드를 한 요청으로 전달하기 위한 결정이다.

### 요청 헤더

| 헤더 | 필수 | 규칙 |
|---|---:|---|
| `X-Internal-Token` | 예 | Java와 Python에 환경변수로 주입한 동일한 내부 서비스 토큰 |
| `X-Request-Id` | 예 | Java가 요청마다 생성한 UUID. Python 응답의 `requestId`에 그대로 반환 |

실제 토큰은 소스, 계약 예제, 로그와 오류 응답에 기록하지 않는다.

### Multipart 요청 필드

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `documentId` | UUID 문자열 | 예 | Java가 생성한 `UserDocument` 식별자 |
| `extractionTaskId` | UUID 문자열 | 예 | 이번 실행을 나타내는 `ExtractionTask` 식별자 |
| `documentType` | 문자열 | 예 | `RESUME`, `PORTFOLIO` 중 하나 |
| `file` | PDF 바이너리 | 예 | 파트의 `Content-Type`은 `application/pdf`, Java와 Python에 설정된 최대 크기 이하 |

사용자 UUID, 실명, 이메일, 전화번호와 GitHub 계정 정보는 요청에 포함하지 않는다.

## 4. 성공 응답

HTTP 상태는 `200 OK`다. Python의 성공은 추출 결과 반환을 의미하고, Java의 `ProfileCandidate` 저장 완료를 의미하지 않는다.

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "documentId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
    "extractionTaskId": "25a89eb8-224f-4457-ae6f-53dc32414f0d",
    "status": "EXTRACTED",
    "candidate": {
      "skills": [],
      "workExperiences": [],
      "projects": [],
      "education": [],
      "certifications": [],
      "evidence": []
    },
    "modelProvider": "ollama",
    "modelName": "configured-model-name",
    "piiRemoved": true
  },
  "error": null,
  "timestamp": "2026-07-28T08:00:00Z"
}
```

### 성공 데이터

| 필드 | 타입 | 규칙 |
|---|---|---|
| `documentId` | UUID | 요청값과 동일 |
| `extractionTaskId` | UUID | 요청값과 동일 |
| `status` | 문자열 | 성공 시 `EXTRACTED`만 허용. Java는 이를 작업 완료 상태로 매핑 |
| `candidate` | `ProfileCandidatePayload` | 아래 후보 스키마 |
| `modelProvider` | 문자열 | 실제 사용한 `ollama`, `gemini` 중 하나 |
| `modelName` | 문자열 | 실제 사용한 설정 모델 이름 |
| `piiRemoved` | boolean | 성공 시 반드시 `true` |

`timestamp`는 UTC RFC 3339 형식으로 반환한다. 응답에 정의되지 않은 추가 필드는 허용하지 않는다.

## 5. 후보 스키마

Python은 문서에 직접 적혀 있고 근거가 연결된 항목만 반환한다. 확인할 수 없는 값은 생성하지 않는다.

### `ProfileCandidatePayload`

| 필드 | 타입 | 설명 |
|---|---|---|
| `skills` | `CandidateSkill[]` | 문서에서 확인한 기술 |
| `workExperiences` | `CandidateWorkExperience[]` | 경력·업무 경험 |
| `projects` | `CandidateProject[]` | 프로젝트 경험 |
| `education` | `CandidateEducation[]` | 학력 |
| `certifications` | `CandidateCertification[]` | 자격증 |
| `evidence` | `CandidateEvidence[]` | 후보 값과 연결되는 최소 원문 근거 |

해당 항목이 없으면 필드를 생략하지 않고 빈 배열을 반환한다.

### `CandidateSkill`

| 필드 | 타입 | 설명 |
|---|---|---|
| `rawName` | string | 문서에 적힌 기술 이름 |
| `normalizedName` | string 또는 `null` | 검증 가능한 대표 이름. 확신할 수 없으면 `null` |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `CandidateWorkExperience`

| 필드 | 타입 | 설명 |
|---|---|---|
| `companyName` | string 또는 `null` | 문서에 적힌 회사명 |
| `jobTitle` | string 또는 `null` | 문서에 적힌 직무·직책 |
| `rawPeriod` | string 또는 `null` | 문서의 기간 원문 |
| `startedOn` | string 또는 `null` | 확인 가능한 경우 `YYYY`, `YYYY-MM`, `YYYY-MM-DD` |
| `endedOn` | string 또는 `null` | 재직 중이거나 확인 불가하면 `null` |
| `responsibilities` | string[] | 문서에 적힌 주요 업무 |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `CandidateProject`

| 필드 | 타입 | 설명 |
|---|---|---|
| `projectName` | string 또는 `null` | 문서에 적힌 프로젝트명 |
| `role` | string 또는 `null` | 사용자가 직접 담당했다고 확인되는 역할 |
| `summary` | string 또는 `null` | 원문을 벗어나지 않는 프로젝트 설명 |
| `technologies` | `CandidateSkill[]` | 프로젝트에서 사용했다고 확인되는 기술 |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

프로젝트 전체 기술과 사용자가 직접 담당한 기술을 같은 사실로 확정하지 않는다. 직접 담당 여부가 확인되지 않으면 `role`을 `null`로 반환한다.

### `CandidateEducation`

| 필드 | 타입 | 설명 |
|---|---|---|
| `institutionName` | string 또는 `null` | 학교·교육기관명 |
| `major` | string 또는 `null` | 전공 |
| `degree` | string 또는 `null` | 학위·과정명 |
| `rawPeriod` | string 또는 `null` | 기간 원문 |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `CandidateCertification`

| 필드 | 타입 | 설명 |
|---|---|---|
| `name` | string | 자격증명 |
| `issuer` | string 또는 `null` | 발급기관 |
| `acquiredOn` | string 또는 `null` | 확인 가능한 경우 `YYYY`, `YYYY-MM`, `YYYY-MM-DD` |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `CandidateEvidence`

| 필드 | 타입 | 설명 |
|---|---|---|
| `evidenceId` | string | 응답 안에서 중복되지 않는 식별자 |
| `fieldPath` | string | 연결 대상 필드 경로. 예: `skills[0].rawName` |
| `value` | string | 근거가 뒷받침하는 후보 값 |
| `sourceText` | string | 판정을 확인할 수 있는 최소 원문 범위 |
| `pageNumber` | integer | PDF의 1부터 시작하는 페이지 번호 |

모든 후보 항목은 하나 이상의 유효한 `evidenceIds`를 가져야 한다. 존재하지 않는 근거를 참조하거나 동일한 근거 식별자를 중복 정의하면 계약 위반이다.

> **제안 — 코덱스 확인 필요 (2026-07-29)**: 실제 설치된 Ollama 모델(qwen2.5, exaone3.5, llama3.2) 3종을 같은 평가 PDF로 검증한 결과, 스키마·프롬프트를 조정해도 "모든 후보 항목이 근거를 가져야 한다" 요건을 안정적으로 통과하는 모델이 없었다(0% 통과). 반면 근거가 있는 항목의 `sourceText`가 원문과 다르거나(할루시네이션) 존재하지 않는 근거를 참조하는 경우는 명확히 구분해서 잡을 수 있었다.
>
> 제안: 후보 항목의 `evidenceIds`는 **비어 있을 수 있다**(모델이 근거를 연결하지 못한 경우 빈 배열로 남긴다). 다만 다음은 완화하지 않는다 — ① `evidenceIds`에 값이 있으면 그 근거는 반드시 `evidence` 배열에 실제로 존재해야 한다(존재하지 않는 근거 참조는 여전히 계약 위반). ② 근거 식별자 중복 정의는 여전히 계약 위반. ③ `evidence`의 `sourceText`는 반드시 원문에 실제로 있는 문장이어야 한다(할루시네이션 금지, 완화 대상 아님).
>
> 이 제안이 확정되기 전까지 Java는 `evidenceIds`가 빈 항목을 계약 위반으로 처리하지 않아야 하며, 사용자 확인·수정 단계에서 근거 없는 항목을 낮은 신뢰도로 표시하는 방식을 검토한다.

이름, 이메일, 전화번호, 생년월일, 사진과 상세 주소는 후보나 근거에 포함하지 않는다. `sourceText`에도 분석에 필요하지 않은 개인정보를 남기지 않는다.

## 6. 실패 응답

모든 실패는 Java의 공통 응답 필드와 같은 모양으로 반환한다.

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": null,
  "error": {
    "errorType": "NO_EXTRACTABLE_TEXT",
    "message": "PDF에서 추출 가능한 텍스트를 찾지 못했습니다.",
    "fieldErrors": [],
    "retryable": false
  },
  "timestamp": "2026-07-28T08:00:00Z"
}
```

계약 검증 단계에서 `X-Request-Id`가 유효하면 실패 응답에도 같은 값을 반환한다. 헤더가 없거나 UUID 형식이 아니면 Python이 새 UUID를 생성한다.

| HTTP | `errorType` | Python 원인 | `retryable` | Java 처리 |
|---:|---|---|---:|---|
| 422 | `INTERNAL_TOKEN_REQUIRED` | `X-Internal-Token` 누락 | false | 호출 설정 오류로 기록 |
| 401 | `INTERNAL_UNAUTHORIZED` | 내부 토큰 불일치 | false | 호출 중단, 비밀값 점검 |
| 422 | `INVALID_EXTRACTION_REQUEST` | UUID, 문서 종류 또는 multipart 필드 오류 | false | 작업 실패 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | PDF가 아닌 요청 파트 | false | 작업 실패 |
| 413 | `FILE_TOO_LARGE` | Python 설정 최대 크기 초과 | false | 작업 실패 |
| 422 | `PDF_UNREADABLE` | `PdfUnreadableError` | false | 작업 실패 |
| 422 | `NO_EXTRACTABLE_TEXT` | `PdfNoExtractableTextError` | false | 스캔 PDF·텍스트 없음 안내 |
| 500 | `PII_SANITIZATION_FAILED` | 개인정보 제거 완료를 확인하지 못함 | false | LLM 호출 없이 작업 실패 |
| 503 | `MODEL_UNAVAILABLE` | `OllamaUnavailableError`, `GeminiUnavailableError` | true | 사용자에게 새 작업 재시도 가능 안내 |
| 502 | `MODEL_RESPONSE_INVALID` | `OllamaResponseError`, `GeminiResponseError`, 후보 스키마 불일치 | false | 작업 실패, 모델 응답 점검 |
| 500 | `EXTRACTION_FAILED` | 분류되지 않은 내부 추출 오류 | false | 작업 실패, 내부 로그 점검 |

운영 오류 응답에는 Python 예외 클래스, 스택 트레이스, 모델 원문 응답, 토큰과 개인정보를 포함하지 않는다.

## 7. 개인정보 가드레일

처리 순서는 다음과 같이 고정한다.

```text
PDF 바이트 수신
→ 페이지 텍스트 추출
→ 개인정보 제거
→ 제거 결과 검증
→ LLM 구조화 추출
→ 후보 스키마 검증
→ 응답
```

- 개인정보 제거 전에 Ollama·Gemini를 호출하지 않는다.
- 개인정보 제거 전후의 원문 전체를 데이터베이스, 일반 로그, 임베딩, 캐시와 학습 데이터에 저장하지 않는다.
- 개인정보 제거 완료를 확인할 수 없으면 `PII_SANITIZATION_FAILED`로 종료한다.
- 성공 응답의 `piiRemoved`는 항상 `true`다. `false`인 성공 응답은 계약 위반으로 Java가 거부한다.
- Python이 반환하는 근거는 후보 값을 확인하는 최소 문장 범위로 제한한다.

## 8. 실패, 부분 완료와 재시도

- 이 API는 성공 후보 전체 또는 실패 중 하나만 반환한다. 부분 후보를 성공으로 반환하지 않는다.
- PDF 텍스트 추출에 성공했어도 개인정보 제거 또는 모델 구조화가 실패하면 전체 작업은 실패다.
- `retryable=true`는 Java가 같은 `ExtractionTask`를 자동 재호출하라는 의미가 아니다.
- 사용자가 재시도하면 Java는 같은 `UserDocument`를 참조하는 새 `ExtractionTask`를 생성한다.
- MVP에서는 자동 재시도, 캐시, 병렬 호출과 모델 자동 대체를 하지 않는다.
- Python 호출은 데이터베이스 부작용이 없어야 한다. 같은 요청이 다시 들어와도 Python이 사용자·작업 데이터를 중복 저장하지 않는다.

## 9. 성능 측정

최적화 전에 다음 구간을 측정한다.

- PDF 텍스트 추출 시간
- 개인정보 제거 시간
- 모델 호출 시간
- 후보 스키마 검증 시간
- Python 내부 API 전체 처리 시간
- Java에서 측정한 Python 왕복 시간

측정값은 원문, 개인정보, 내부 토큰과 모델 응답 전체를 포함하지 않는다. 실제 병목이 확인되기 전에는 비동기 내부 처리, 캐시와 병렬화를 추가하지 않는다.

## 10. 구현·계약 테스트 기준

Java와 Python은 같은 예제 파일과 JSON 결과를 사용해 다음을 검증한다.

1. 정상 PDF가 multipart 요청으로 전달된다.
2. `documentId`, `extractionTaskId`와 `X-Request-Id`가 응답과 일치한다.
3. 내부 토큰 누락과 불일치를 구분한다.
4. PDF가 아닌 파일, 설정 크기 초과, 손상 PDF와 스캔 PDF를 구분한다.
5. 개인정보 제거 실패 시 모델을 호출하지 않는다.
6. 모든 후보 항목이 유효한 근거를 참조한다.
7. 근거와 후보에 제거 대상 개인정보가 남지 않는다.
8. 모델 장애와 잘못된 모델 응답을 구분한다.
9. Python 응답의 추가 필드와 잘못된 enum 값을 Java가 거부한다.
10. Java와 Python을 동시에 실행하고 실제 HTTP 요청으로 계약을 확인한다.

## 11. 구현 근거

- 파일과 폼 필드를 함께 받는 FastAPI 공식 방식: [FastAPI Request Files](https://fastapi.tiangolo.com/tutorial/request-files/)
- `multipart/form-data` 표준: [RFC 7578](https://www.rfc-editor.org/info/rfc7578/)
- Java HTTP 클라이언트 기준: [Spring Framework REST Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
