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
| PDF 문서 추출 | [document-extraction.md](document-extraction.md) | 폐기 — MVP 입력에서 제거 |
| 기술 태그 정규화 | [technology-tag-resolution.md](technology-tag-resolution.md) | 확정 |
| 채용공고 근거 의미 유사도 | [job-evidence-similarity.md](job-evidence-similarity.md) | 제안 — 사용자 승인 필요 |
| 채용공고 검색 도구 | [job-search-tool.md](job-search-tool.md) | 부분 확정 — 제공자·제한 수치 확인 필요 |
| 채용공고 구조화 추출 | [job-posting-extraction.md](job-posting-extraction.md) | 제안 — 코덱스 확인 필요 |

## 채용공고 검색 도구 요약

```http
POST /internal/v1/tools/job-search
Content-Type: application/json
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

- Python과 LLM은 임의 URL에 접근하지 않고 Java 내부 검색 도구만 호출한다.
- Java는 분석 상태·고정 프로필·저장소 근거와 멱등 키를 검증한다.
- URL, Provider 선택, API 키와 호출 한도는 Java 설정이 소유한다.
- 운영 Provider는 인사혁신처 공공취업정보 API 하나이며 병렬 호출이나 대체 Provider 호출을 하지 않는다.
- 외부 API 호출은 DB 트랜잭션 밖에서 실행한다.
- 성공 결과는 최소 공고 원문, 출처, 수집 시각과 변경 지문을 포함한다.

## 변경 순서

```text
계약 수정
→ Java DTO·클라이언트·계약 테스트
→ Python 스키마·라우터·계약 테스트
→ Java와 Python 실제 연결 테스트
→ 브라우저 흐름 확인
```
