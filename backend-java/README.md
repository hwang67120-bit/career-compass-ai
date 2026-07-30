# Java 백엔드

Java 서버는 사용자 인증, 도메인 API, 상태 관리, 데이터 저장, 외부 GitHub 조회, Python 작업 오케스트레이션과 최종 조건 판정을 담당한다.

이 문서의 API 구현 상태는 원격 `java` 브랜치를 기준으로 한다. 아직 구현되지 않은 PDF·분석 API를 완료된 API로 표시하지 않는다.

## 구현 API 목록

| 영역 | 기능 | Method | Endpoint | 성공 상태 |
|---|---|---|---|---:|
| 화면 | 브라우저 확인 화면 | `GET` | `/` | 200 |
| 인증 | GitHub 로그인 시작 | `GET` | `/oauth2/authorization/github` | 302 |
| 인증 | 현재 사용자 확인 | `GET` | `/api/v1/auth/me` | 200 |
| 인증 | CSRF 토큰 조회 | `GET` | `/api/v1/auth/csrf` | 200 |
| 인증 | 로그아웃 | `POST` | `/api/v1/auth/logout` | 204 |
| 문서 | 텍스트 문서 등록 | `POST` | `/api/v1/documents` | 201 |
| 프로젝트 | 공개 GitHub 저장소 등록 | `POST` | `/api/v1/project-sources/github` | 201 |
| 운영 | 애플리케이션 상태 확인 | `GET` | `/actuator/health` | 200 |

## 인증

### 운영 프로필

`prod`는 GitHub OAuth 로그인과 `JSESSIONID` 서버 세션을 사용한다.

```text
GET /oauth2/authorization/github
→ GitHub 로그인·동의
→ /login/oauth2/code/github
→ 내부 사용자 연결
→ /
```

상태 변경 요청은 먼저 `GET /api/v1/auth/csrf`를 호출한 뒤 응답의 `headerName`과 `token`을 요청 헤더에 포함한다. 비밀번호, 이메일, GitHub 액세스 토큰과 저장소 권한은 데이터베이스에 저장하지 않는다.

### 개발·테스트 프로필

`dev`, `prod`는 GitHub OAuth 로그인을 사용한다. `test`만 테스트 설정의 고정 UUID를 사용하며 `TEST_USER_ID` 환경변수는 요구하지 않는다.

## 공통 응답

로그아웃의 `204 No Content`를 제외한 도메인 API는 다음 응답 구조를 사용한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestId` | UUID | 응답 추적 식별자 |
| `data` | Object 또는 `null` | 성공 데이터 |
| `error` | Object 또는 `null` | 실패 정보 |
| `timestamp` | OffsetDateTime | 응답 생성 시각 |

### 성공

```json
{
  "requestId": "f85cf40d-3994-454c-aedd-a310d8b3e938",
  "data": {},
  "error": null,
  "timestamp": "2026-07-28T09:00:00+09:00"
}
```

### 실패

```json
{
  "requestId": "9d74f739-18f2-4f0b-ad5b-1c593aa94214",
  "data": null,
  "error": {
    "errorType": "INVALID_INPUT",
    "message": "입력 내용을 확인해 주세요.",
    "fieldErrors": [
      {
        "fieldName": "repositoryUrl",
        "message": "GitHub 저장소 주소를 입력해 주세요."
      }
    ],
    "retryable": false
  },
  "timestamp": "2026-07-28T09:00:00+09:00"
}
```

| 오류 필드 | 타입 | 설명 |
|---|---|---|
| `errorType` | String | 클라이언트 분기용 고정 식별자 |
| `message` | String | 사용자에게 표시할 안전한 메시지 |
| `fieldErrors` | Array | 필드명과 검증 실패 이유 |
| `retryable` | Boolean | 사용자가 나중에 다시 시도할 수 있는지 여부 |

## 인증 API

### 현재 사용자 확인

```http
GET /api/v1/auth/me
```

로그인하지 않은 경우에도 `200 OK`이며 `authenticated=false`, `userId=null`을 반환한다.

```json
{
  "authenticated": true,
  "userId": "60000000-0000-0000-0000-000000000001"
}
```

### CSRF 토큰 조회

```http
GET /api/v1/auth/csrf
```

```json
{
  "headerName": "X-CSRF-TOKEN",
  "token": "{session-csrf-token}"
}
```

예시의 토큰 문자열은 형식 설명용이며 실제 토큰을 문서, 소스와 로그에 저장하지 않는다.

## 사용자 문서 API

### 텍스트 문서 등록

현재 API는 PDF 업로드 전 단계의 텍스트 등록 API다.

```http
POST /api/v1/documents
Content-Type: application/json
```

```json
{
  "documentType": "RESUME",
  "text": "Java와 Spring Boot를 사용한 백엔드 프로젝트 경험"
}
```

| 요청 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `documentType` | String | 예 | `RESUME`, `PORTFOLIO` |
| `text` | String | 예 | 공백이 아닌 문자열, 설정된 최대 길이 이하 |

성공하면 `201 Created`와 `Location: /api/v1/documents/{documentId}`를 반환한다.

| 응답 `data` | 타입 | 설명 |
|---|---|---|
| `documentId` | UUID | 문서 식별자 |
| `documentType` | String | 문서 종류 |
| `documentStatus` | String | 현재 `REGISTERED` |
| `createdAt` | Instant | 등록 시각 |

PDF 업로드와 `ExtractionTask` 생성은 [문서 추출 계약](../contracts/document-extraction.md)을 기준으로 다음 단계에서 구현한다.

## GitHub 프로젝트 API

### 공개 저장소 등록

```http
POST /api/v1/project-sources/github
Content-Type: application/json
```

```json
{
  "repositoryUrl": "https://github.com/octocat/Hello-World"
}
```

`https://github.com/{owner}/{repository}` 형식의 공개 저장소만 허용한다. URL 문법만 확인하지 않고 GitHub API에서 저장소, 기본 브랜치와 현재 커밋 SHA를 조회한 뒤 저장한다.

성공하면 `201 Created`와 `Location: /api/v1/project-sources/{projectSourceId}`를 반환한다.

| 응답 `data` | 타입 | 설명 |
|---|---|---|
| `projectSourceId` | UUID | 프로젝트 출처 식별자 |
| `repositoryUrl` | String | 정규화된 저장소 URL |
| `repositoryFullName` | String | `{owner}/{repository}` |
| `defaultBranch` | String | 등록 시점 기본 브랜치 |
| `commitSha` | String | 등록 시점 최신 커밋 식별자 |
| `status` | String | 현재 `REGISTERED` |

등록 실패 요청은 `ProjectSource`로 저장하지 않는다.

## 공통 예외 처리

컨트롤러·검증·도메인 예외는 `GlobalExceptionHandler`가 `ApiResponse` 형식으로 변환한다. 인증·인가 실패는 Spring Security의 `ApiAuthenticationEntryPoint`와 `ApiAccessDeniedHandler`가 같은 오류 형식으로 반환한다.

| HTTP | `errorType` | 발생 조건 | `retryable` |
|---:|---|---|---:|
| 400 | `INVALID_INPUT` | 필드 검증 실패, 잘못된 JSON·문서 종류 | false |
| 400 | `INVALID_GITHUB_REPOSITORY_URL` | 허용되지 않은 GitHub 저장소 URL | false |
| 401 | `UNAUTHORIZED` | 로그인 사용자 확인 실패 | false |
| 403 | `FORBIDDEN` | 권한 부족 또는 CSRF 검증 실패 | false |
| 404 | `GITHUB_REPOSITORY_UNAVAILABLE` | 공개 저장소를 확인할 수 없음 | false |
| 413 | `PAYLOAD_TOO_LARGE` | 문서 텍스트가 설정된 최대 길이 초과 | false |
| 429 | `GITHUB_RATE_LIMITED` | GitHub API 요청 한도 도달 | true |
| 503 | `GITHUB_SERVICE_UNAVAILABLE` | GitHub 장애, 잘못된 응답 또는 리다이렉트 거부 | 원인에 따라 다름 |

### `GlobalExceptionHandler` 매핑

| Java 예외 | HTTP | `errorType` |
|---|---:|---|
| `MethodArgumentNotValidException` | 400 | `INVALID_INPUT` |
| `HttpMessageNotReadableException` | 400 | `INVALID_INPUT` |
| `DocumentTextTooLargeException` | 413 | `PAYLOAD_TOO_LARGE` |
| `InvalidGitHubRepositoryUrlException` | 400 | `INVALID_GITHUB_REPOSITORY_URL` |
| `GitHubAccessException(REPOSITORY_UNAVAILABLE)` | 404 | `GITHUB_REPOSITORY_UNAVAILABLE` |
| `GitHubAccessException(RATE_LIMITED)` | 429 | `GITHUB_RATE_LIMITED` |
| `GitHubAccessException(REDIRECTED)` | 503 | `GITHUB_SERVICE_UNAVAILABLE`, 재시도 불가 |
| `GitHubAccessException(SERVICE_UNAVAILABLE)` | 503 | `GITHUB_SERVICE_UNAVAILABLE`, 재시도 가능 |
| `GitHubAccessException(INVALID_RESPONSE)` | 503 | `GITHUB_SERVICE_UNAVAILABLE`, 재시도 가능 |
| `CurrentUserUnavailableException` | 401 | `UNAUTHORIZED` |

오류 응답에는 내부 예외, 스택 트레이스, 토큰, 저장소 인증정보와 사용자 원문을 포함하지 않는다.

## 현재 미구현 API

- PDF 파일 업로드와 `ExtractionTask` 생성
- 추출 결과 조회·수정·확정
- 등록 문서·프로젝트 조회와 삭제
- GitHub 저장소 수동 새로 조회와 커밋 변경 비교
- 채용공고 등록
- 조건 판정·의미 분석 작업 생성
- 분석 상태와 결과 조회

## 구현 근거

- [Spring MVC 주석 기반 Controller](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Framework REST Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [GitHub REST 저장소 API](https://docs.github.com/en/rest/repos/repos)
- [GitHub REST 커밋 API](https://docs.github.com/en/rest/commits/commits)
