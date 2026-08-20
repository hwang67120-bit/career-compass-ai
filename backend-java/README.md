# Career Compass Java Backend

Java 21과 Spring Boot 4 기반 사용자 API입니다.

## 현재 책임

- GitHub OAuth 세션 인증과 CSRF
- 공개 GitHub 저장소 검증·등록·조회
- 표준 기술 태그 검색과 내부 정규화
- Python 분석 서버 연결 확인
- 요청 ID 기반 구조화 로그
- PostgreSQL과 Flyway 데이터 관리

PDF, 이력서·포트폴리오 텍스트 등록과 Java PDF 추출 클라이언트는 2026-08-03 MVP 범위에서 제거했습니다.

## API

| Method | Endpoint | 기능 |
|---|---|---|
| `GET` | `/api/v1/auth/me` | 로그인 상태 |
| `GET` | `/api/v1/auth/csrf` | CSRF 토큰 |
| `POST` | `/api/v1/auth/logout` | 로그아웃 |
| `POST` | `/api/v1/project-sources/github` | 공개 저장소 등록 |
| `GET` | `/api/v1/project-sources` | 프로젝트 출처 목록 |
| `GET` | `/api/v1/technology-tags` | 표준 기술 태그 검색 |
| `POST` | `/internal/v1/technology-tags/resolve` | 내부 기술 태그 정규화 |
| `GET` | `/api/v1/system/python-status` | Python 연결 확인 |

분석 작업 API는 [개발자 채용공고 분석 API](../docs/api/developer-job-analysis-api.md)를 기준으로 후속 구현합니다.

## 실행과 테스트

Linux에서 이 디렉터리만 IntelliJ 프로젝트로 엽니다.

```bash
/home/mycom/.sdkman/candidates/gradle/8.14.3/bin/gradle test --no-daemon
```

환경변수 목록과 전체 구조는 [루트 README](../README.md)를 확인합니다.

## 데이터베이스 주의

적용된 Flyway 파일은 수정하거나 삭제하지 않습니다. `V1__create_user_document.sql`로 생성된 기존 테이블은 현재 코드에서 사용하지 않으며, 삭제하려면 별도의 신규 마이그레이션과 데이터 삭제 승인이 필요합니다.
