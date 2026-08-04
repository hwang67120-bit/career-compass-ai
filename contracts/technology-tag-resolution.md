# 기술 태그 정규화 내부 계약

## 1. 목적과 책임

Python이 저장소 또는 채용공고에서 확인한 기술명 원문을 Java가 관리하는 표준 기술 태그와
정확히 연결한다. Java는 대표 이름과 확인된 별칭만 사용하며 AI 유사도만으로 값을 합치거나
새 별칭을 자동 등록하지 않는다.

- 호출자: Python
- 제공자: Java
- 저장: 이 API는 입력과 결과를 저장하지 않는다.
- 미확인 값: 실패로 만들지 않고 `UNRESOLVED`로 반환한다.
- 원문: 입력 순서와 `rawName`을 그대로 보존한다.

## 2. 요청

```http
POST /internal/v1/technology-tags/resolve
Content-Type: application/json
X-Internal-Token: {INTERNAL_SERVICE_TOKEN}
```

```json
{
  "technologyNames": [
    "Kubernetes",
    "k8s",
    "Postgres",
    "unknown-tool"
  ]
}
```

검증 규칙:

- `technologyNames`는 필수이며 1개 이상 30개 이하다.
- 각 기술명은 `null` 또는 공백일 수 없고 Unicode 코드 포인트 기준 100자 이하다.
- 내부 토큰은 Java와 Python이 환경변수 `INTERNAL_SERVICE_TOKEN`으로 공유한다.
- 토큰과 기술명 원문은 일반 로그에 기록하지 않는다.

## 3. 성공 응답

```json
{
  "requestId": "674e5357-dc0b-42a5-92dc-e12fc2df2292",
  "data": {
    "results": [
      {
        "rawName": "Kubernetes",
        "technologyTagId": "70000000-0000-0000-0000-000000000026",
        "canonicalName": "Kubernetes",
        "matchStatus": "MATCHED",
        "matchMethod": "CANONICAL"
      },
      {
        "rawName": "k8s",
        "technologyTagId": "70000000-0000-0000-0000-000000000026",
        "canonicalName": "Kubernetes",
        "matchStatus": "MATCHED",
        "matchMethod": "ALIAS"
      },
      {
        "rawName": "Postgres",
        "technologyTagId": "70000000-0000-0000-0000-000000000020",
        "canonicalName": "PostgreSQL",
        "matchStatus": "MATCHED",
        "matchMethod": "ALIAS"
      },
      {
        "rawName": "unknown-tool",
        "technologyTagId": null,
        "canonicalName": null,
        "matchStatus": "UNRESOLVED",
        "matchMethod": "NONE"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-07-31T10:00:00Z"
}
```

## 4. 정규화와 매칭

1. Java는 NFKC 적용 후 앞뒤 공백과 대소문자 차이를 제거한다.
2. 공백, 하이픈과 언더스코어는 비교에서 제외한다.
3. `+`, `#`, `.`, `/`처럼 기술을 구분하는 문자는 보존한다.
4. 활성 표준 태그의 `normalizedKey`와 정확히 일치하면 `CANONICAL`이다.
5. 확인된 `normalizedAlias`와 정확히 일치하면 `ALIAS`다.
6. 둘 다 없으면 `UNRESOLVED`다.
7. 부분 문자열 검색과 AI 유사도는 이 API의 확정 매칭에 사용하지 않는다.

`C`, `C++`, `C#`, `Java`, `JavaScript`, `Spring Framework`, `Spring Boot`처럼 관련은 있지만
서로 다른 기술은 하나로 합치지 않는다.

## 5. 중복과 근거

응답은 입력 항목마다 하나의 결과를 같은 순서로 반환한다. 여러 원문이 같은
`technologyTagId`에 연결되어도 결과를 제거하지 않는다. 최종 분석 단계가
`technologyTagId` 기준으로 태그를 하나로 합치고 연결된 모든 `rawName`을 근거로 유지한다.

## 6. 오류

| HTTP | errorType | retryable | 조건 |
|---|---|---:|---|
| 400 | `INVALID_TECHNOLOGY_TAG_RESOLUTION_REQUEST` | false | 배열·항목 개수 또는 기술명 검증 실패 |
| 401 | `INTERNAL_UNAUTHORIZED` | false | 내부 토큰 누락 또는 불일치 |

오류 응답도 공통 `requestId/data/error/timestamp` 봉투를 사용한다. 토큰 값, 내부 예외와
데이터베이스 정보를 응답에 포함하지 않는다.

## 7. 공동 계약 테스트

1. 대표 이름 직접 일치
2. 별칭 `k8s`와 `Postgres` 일치
3. 대소문자·공백·하이픈·언더스코어 차이
4. `C`, `C++`, `C#` 구분
5. 미등록 기술의 `UNRESOLVED`
6. 동일 대표 태그로 연결되는 여러 원문의 순서와 원문 보존
7. 빈 배열, 30개 초과, 공백 항목과 100자 초과
8. 내부 토큰 누락과 불일치
9. Java와 Python을 동시에 실행한 실제 HTTP 요청

## 8. 공식 참고 자료

- [Spring Security Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Data JPA Query Methods](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)
