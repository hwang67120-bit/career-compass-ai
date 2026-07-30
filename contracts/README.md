# Java–Python 계약

`contracts`는 Java와 Python 사이의 요청·응답 형식을 관리한다. 양쪽 구현보다 계약 변경을 먼저 수행한다.

## 적용 규칙

1. 새로운 서버 간 통신이나 필드 변경은 계약을 먼저 수정한다.
2. Java DTO와 Python Pydantic 모델은 같은 필드명, 타입, enum과 필수 여부를 사용한다.
3. 성공·실패 HTTP 상태와 `errorType`, `retryable`을 양쪽에서 동일하게 처리한다.
4. 양쪽 서버는 같은 요청·응답 예제로 계약 테스트를 실행한다.
5. 계약에 없는 필드와 상태를 구현에서 임의로 추가하지 않는다.
6. 비밀키, 내부 토큰, 실제 사용자 자료와 개인정보를 예제에 포함하지 않는다.
7. 계약과 구현이 다르면 구현을 계속하지 않고 문서를 먼저 동기화한다.

## 계약 목록

| 기능 | 문서 | 상태 |
|---|---|---|
| PDF 문서 추출 | [document-extraction.md](document-extraction.md) | MVP 확정 |
| 채용공고 구조화 추출 | [job-posting-extraction.md](job-posting-extraction.md) | 제안 — 코덱스 확인 필요 |

## PDF 문서 추출 요약

```http
POST /internal/v1/documents/extract
Content-Type: multipart/form-data
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

- Java가 `UserDocument`와 `ExtractionTask`를 생성한다.
- Python은 PDF 텍스트 추출, 개인정보 제거와 구조화 후보 생성을 담당한다.
- 요청은 `documentId`, `extractionTaskId`, `documentType`, PDF `file`을 포함한다.
- Python 성공 응답은 근거가 연결된 후보와 `piiRemoved=true`를 포함한다.
- 실패·재시도·후보 필드의 상세 기준은 계약 본문을 최종 기준으로 사용한다.
- 사용자 재시도는 같은 작업 갱신이 아니라 새로운 `ExtractionTask` 생성이다.

## 변경 순서

```text
계약 수정
→ Java DTO·클라이언트·계약 테스트
→ Python 스키마·라우터·계약 테스트
→ Java와 Python 실제 연결 테스트
→ 브라우저 흐름 확인
```
