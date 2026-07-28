# PDF 문서 추출 계약 (제안)

상태: 제안 — Java 쪽과 확정 전. 8월 21일 MVP 흐름(`PDF 등록 → 추출·수정·확정 → 채용공고 등록 → 조건 판정+의미 분석 → 결과 화면 → 테스트 배포`) 중 "추출" 단계에만 범위를 좁힌다. `docs/architecture/java-python-connection.md`(연결 방식)와 `docs/architecture/guardrails.md`(가드레일 원칙)를 함께 따른다.

## 책임 경계 (제안, 확인 필요)

- Java: PDF 형식·크기·안전성 검사, `UserDocument` 생성, 이 계약으로 Python 호출.
- Python: PDF에서 텍스트 추출(`app/services/pdf_extraction.py`, 이미 구현됨), **텍스트 추출 직후 개인정보 제거**, 그 다음에만 LLM(Ollama·Gemini)에 전달해 구조화 추출.
- **확인 필요**: 개인정보 제거를 Python이 할지 Java가 텍스트 추출 전 단계에서 미리 할지. 이 문서는 "Python이 추출 직후 자체적으로 제거한다"를 제안으로 둔다 — Notion 문서가 "Python은 문서의 텍스트와 구조화 항목을 추출한다"고 명시하고 있고, 원본 PDF 자체(바이트)에는 Java가 텍스트 내용을 미리 알 방법이 없기 때문이다. Gemini 무료 등급 데이터 정책([llm-providers.md](../docs/architecture/llm-providers.md) 참고)때문에 이 순서가 중요하다 — 개인정보가 제거되지 않은 원문이 Gemini로 나가면 안 된다.

## 요청 — Java → Python

`POST /internal/v1/documents/extract`

| 필드 | 타입 | 설명 |
|---|---|---|
| `documentId` | UUID | `UserDocument` 식별자 |
| `documentType` | `"RESUME"` \| `"PORTFOLIO"` | 문서 종류 |
| `file` | PDF 원본 바이트 | 전송 방식(multipart vs base64)은 확인 필요 |

사용자 실명, 이메일, 전화번호 같은 계정 정보는 이 요청에 포함하지 않는다 (`documentId`로만 식별).

## 응답 — Python → Java

### 성공

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | `"EXTRACTED"` | |
| `candidate` | Object | 추출된 구조화 후보(경력·기술·프로젝트), 필드 목록은 확인 필요 — `app/schemas/job_posting.py`의 `Evidence` 패턴과 동일하게 각 항목에 원문 근거를 연결한다 |
| `modelProvider` | `"ollama"` \| `"gemini"` | |
| `modelName` | string | |
| `piiRemoved` | boolean | 개인정보 제거를 실제로 수행했는지 |

### 실패 — 기존에 구현된 예외를 그대로 매핑

| `errorType` | 대응하는 Python 예외 |
|---|---|
| `PDF_UNREADABLE` | `PdfUnreadableError` |
| `NO_EXTRACTABLE_TEXT` | `PdfNoExtractableTextError` (스캔 PDF, OCR로 보완하지 않음) |
| `MODEL_UNAVAILABLE` | `OllamaUnavailableError` / `GeminiUnavailableError` |
| `MODEL_RESPONSE_INVALID` | `OllamaResponseError` / `GeminiResponseError` |

`docs/README.md`의 공통 응답 포맷(`requestId`/`data`/`error`/`timestamp`)과 `errorType`/`retryable` 구조를 그대로 따른다.

## 정확성 우선 원칙

- 이 단계는 성능보다 정확성을 우선한다 — 처리 시간 최적화(비동기·캐시·병렬화)는 하지 않고, 실제 처리 시간을 측정한 뒤 느린 구간에만 나중에 적용한다.
- 모델 선택은 여전히 확인 필요이지만, 속도가 아니라 추출 정확도 기준으로 고른다 ([embedding-similarity.md](../docs/architecture/embedding-similarity.md)에서 이미 확인한 것처럼 로컬 모델이 항상 더 낫지 않을 수 있다).

## 확인 필요 (8월 21일 범위 밖 포함 가능)

- PDF 전송 방식(multipart/base64)
- `candidate` 구조화 필드 목록 확정
- 개인정보 제거 책임 소재 (위 "책임 경계" 참고)
- 재시도 정책 — 오케스트레이션 원칙 6번(멱등성 보장된 작업만 재시도)에 따라 Java가 결정, Python은 같은 요청을 반복 호출해도 안전하게 만든다(멱등)
