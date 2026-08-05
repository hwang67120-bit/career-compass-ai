# Claude 전달: 개발용 샘플 채용 Provider로 분석 연결 검증

상태: **사용자 확인 — 개발 환경 통합 검증용으로만 적용**
작성일: 2026-08-05

## 1. 배경과 목표

- 사람인 채용정보 API는 승인 대기 중이다.
- 고용24 채용정보 API는 실제 호출 결과 개인회원 사용이 거부되어 현재 계정으로 연결 검증을 완료할 수 없다.
- 외부 Provider 문제 때문에 검색 이후의 Java 작업 처리, Python 구조화 추출, LLM 실행과 브라우저 결과 표시까지 모두 막혀 있다.
- 제품 목표는 합격 예측이 아니라 사용자 기술 태그·GitHub 근거와 채용공고 요구사항을 분류·비교하고 근거를 보여주는 것이다.

따라서 외부 채용 API만 합성 데이터로 대체하는 **개발 전용 샘플 Provider**를 사용해 분석 본체를 먼저 연결한다. Java 서버, PostgreSQL, Python 서버, 실제 LLM과 브라우저 요청은 대체하지 않는다.

## 2. 확정 원칙

1. 샘플 Provider는 `dev` 프로필에서만 사용할 수 있다.
2. `prod`와 기본 프로필에서는 샘플 Provider Bean이 생성되거나 선택되면 안 된다.
3. 공고는 개인정보가 없는 프로젝트 전용 합성 데이터로 작성한다.
4. 응답·DB·로그·화면에 Provider를 `DEV_SAMPLE`로 표시한다.
5. `DEV_SAMPLE` 결과를 실제 시장 통계, 실제 공고 수 또는 배포 완료 근거로 사용하지 않는다.
6. 샘플 Provider는 네트워크를 호출하지 않으며 API 키를 요구하지 않는다.
7. 실제 Provider가 하나도 없으면 가짜 성공을 만들지 않고 `JOB_PROVIDERS_NOT_CONFIGURED`에 해당하는 실패로 구분한다.
8. Provider 원문 전체, API 키, 내부 토큰과 개인정보를 일반 로그에 남기지 않는다.
9. 이 작업은 `contracts/job-search-tool.md`의 운영 Provider 목록을 변경하지 않는다. 샘플 Provider는 계약 외부 인터넷 Provider가 아니라 개발 검증용 입력 어댑터다.

## 3. Claude 담당 범위

담당 디렉터리: `backend-java`
주요 도메인: `jobsearch`, `jobanalysis`
참고 브랜치/PR: `claude/wire-job-analysis-python`, PR #48

### 구현할 것

1. PR #48의 Work24 직접 의존성을 `JobPostingProvider` 같은 명시적인 Provider 경계 뒤로 분리한다.
2. Work24 Adapter는 코드로 보존하되 활성화되지 않았을 때 인증키가 없어도 애플리케이션 시작을 막지 않게 한다.
3. `dev`에서만 생성되는 `DevSampleJobPostingProvider`를 추가한다.
4. 합성 공고에는 다음 분석 입력을 포함한다.
   - 원문 직무명: 백엔드 개발자
   - 필수 기술: Java, Spring Boot, PostgreSQL, Git
   - 우대 기술: Docker, AWS
   - 주요 업무: REST API 개발, 데이터베이스 설계, 외부 AI 서비스 연동
   - 경력·학력 등 확인되지 않은 조건은 임의로 만들지 않는다.
5. 작업 상태가 검색, 추출, 비교 순서로 실제 갱신되고 실패 이유가 내부 실패 코드로 남도록 한다.
6. 브라우저 결과와 진행 로그에 `DEV_SAMPLE`임을 명확히 표시한다.
7. 검증 실패 시 남아 있는 `rawXmlPrefix` 같은 Provider 원문 로그를 제거한다.

### 구현하지 않을 것

- 사람인 승인을 가정한 성공 처리
- 고용24 개인회원 제한 우회
- 민간 사이트 크롤링 또는 비공개 API 호출
- 샘플 공고를 운영 데이터나 시장 통계로 저장
- 합격 확률, 채용 가능성 또는 지원자 순위 생성
- 새 Redis, 병렬 Provider 호출, 측정되지 않은 자동 재시도

## 4. 테스트 시나리오

### 자동 테스트

1. `dev` 프로필에서 샘플 Provider가 선택된다.
2. `prod` 및 기본 프로필에서 샘플 Provider가 생성되지 않는다.
3. Work24 비활성 상태에서는 인증키가 없어도 관련 Bean 구성 때문에 시작이 실패하지 않는다.
4. 샘플 공고의 Provider 값과 출처가 `DEV_SAMPLE`로 저장·응답된다.
5. 활성 Provider가 없으면 성공 빈 목록이 아니라 구성 오류로 처리된다.
6. 샘플 원문에 이메일·전화번호·담당자 정보가 포함되지 않는다.
7. 기존 Work24·Python 응답 검증·작업 큐 테스트가 약화되지 않는다.
8. 전체 Java 테스트를 Java 21과 실제 PostgreSQL/Testcontainers 환경에서 실행한다.

### 실제 연결 테스트

다음 구성요소는 Mock으로 대체하지 않는다.

```text
브라우저
→ Java 사용자 API
→ PostgreSQL 작업 큐
→ DEV_SAMPLE 공고 입력
→ Python 내부 API
→ 실제 로컬 LLM
→ Java 결과 저장
→ 브라우저 진행 상태와 결과 표시
```

확인 항목:

- 분석 생성 응답과 `Location` 헤더
- `QUEUED`에서 실행·종료 상태까지의 변경
- Python 요청의 `jobPostingId`, `extractionTaskId` UUID 일치
- 구조화된 필수·우대 기술과 주요 업무
- 기술 태그·GitHub 근거 비교 결과
- 실패 시 단계와 내부 실패 코드 표시
- 결과 화면의 `DEV_SAMPLE` 안내

이 테스트는 Java–Python–DB–LLM–브라우저 연결을 검증하지만 실제 외부 채용 Provider 연결 완료를 의미하지 않는다. 실제 사람인 또는 다른 승인 Provider로 같은 흐름을 다시 실행해야 최종 `BROWSER_VERIFIED`로 판정한다.

## 5. PR #48 처리 기준

PR #48에서 유지할 항목:

- PostgreSQL 작업 선점과 상태 갱신
- Python 채용공고 추출 클라이언트
- `jobPostingId`·`extractionTaskId` 저장과 응답 재검증
- 분석 조회 API와 프론트 폴링
- 설정으로 분리된 검색 결과 수·텍스트 길이 제한
- 관련 단위·통합 테스트

병합 전에 보완할 항목:

- PR을 다시 Draft로 전환
- Provider 원문 응답 로그 제거
- Work24 활성화 여부와 Provider 선택을 명시적인 설정·경계로 분리
- `DEV_SAMPLE`이 운영에서 생성되지 않는 테스트 추가
- PR 본문을 실제 개인회원 제한과 현재 검증 범위에 맞게 최신화

## 6. 완료 보고 형식

Claude는 작업 후 다음을 구분해서 보고한다.

- 수정한 파일
- 개발 전용 Provider 격리 방식
- 실행한 Java 테스트와 통과 개수
- 실제 Java–Python–PostgreSQL–LLM 연결 결과
- 브라우저에서 확인한 단계와 결과
- 실제 외부 Provider라서 검증하지 못한 범위
- 사람인 승인 후 교체하거나 추가할 설정과 Adapter

실행하지 않은 테스트를 통과했다고 표현하지 않고, 샘플 Provider 결과를 실제 채용 API 검증 결과로 표현하지 않는다.
