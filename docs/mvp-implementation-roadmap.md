# MVP 구현 로드맵

이 문서는 현재 구현 상태와 논의된 Java·Python·프론트 작업을 하나의 실행 순서로 정리한다.
실제 완료 상태는 [`current-work.md`](current-work.md)에 기록하고, 이 문서는 우선순위와 담당 경계를 관리한다.

- 작성일: 2026-07-29
- 기준 브랜치: `origin/develop`
- 기준 커밋: `17abcca`
- MVP 완료 기준: 최소 `BROWSER_VERIFIED`

## 1. 확정한 방향

- PDF 이력서를 모든 직군의 기본 입력으로 사용한다.
- GitHub 저장소는 개발 직군을 위한 선택 근거이며 분석의 필수 입력으로 사용하지 않는다.
- 프론트는 사용자 요청과 진행 상태를 표시한다.
- Java는 사용자 인증·자료 소유권·분석 작업 상태·Python 호출·최종 저장을 담당한다.
- Python은 PDF 텍스트 추출 직후 개인정보를 제거·검증하고 LLM 구조화와 의미 분석을 담당한다.
- Java–Python 통신은 [`../contracts/document-extraction.md`](../contracts/document-extraction.md)를 기준으로 한다.
- PDF 원본은 영구 저장하지 않고 Java의 비공개 임시 저장소에서 처리 후 삭제하는 방식을 사용한다.
- Mock 테스트만으로 통합 완료를 판정하지 않는다.

## 2. 현재 핵심 차단 요소

### 2.1 PDF와 텍스트 입력 불일치

현재 Java 사용자 API는 텍스트를 저장하지만 Python 문서 추출 계약은 PDF 바이트를 요구한다.
Java가 실제 PDF를 수신하고 Python 호출 시점까지 읽을 수 있는 흐름이 없으므로
`executeDocumentExtraction`을 실제 서비스 흐름에서 시작할 수 없다.

다음 내용을 사용자 API 계약에서 먼저 확정한다.

- PDF 등록 API의 경로, `multipart/form-data` 필드와 성공 상태 코드
- PDF 등록과 `UserDocument` 생성 시점
- 임시 파일 생성 위치, 접근 권한과 UUID 기반 파일명
- `ExtractionTask` 생성 전후의 PDF 임시 보관 범위
- 완료·실패 시 즉시 삭제와 비정상 종료 잔여 파일 정리 방식
- 임시 보관 만료시간과 최대 파일 크기의 환경설정 이름
- 삭제 실패 시 작업 상태, 운영 로그와 재처리 방법

### 2.2 사용자 분석 시작 API

다음 API는 아직 확정 계약이 아니다.

```http
POST /api/v1/documents/{documentId}/extractions
```

구현 전에 아래 항목을 API 문서에서 확정한다.

- 요청 본문 또는 multipart 포함 여부
- `202 Accepted` 응답과 `Location` 헤더 사용 여부
- 응답의 `extractionTaskId`, 작업 상태와 조회 경로
- 인증된 사용자의 문서 소유권 검증
- 중복 실행, 이미 완료된 문서와 처리 중 작업의 상태 코드
- PDF 임시 파일 만료·누락, Python 장애와 계약 위반 오류
- 사용자가 재시도할 때 새 `ExtractionTask`를 생성하는 규칙

### 2.3 원문 보관 정책

현재 `user_document.original_text`는 원문 텍스트를 저장하지만 별도 보관 동의와 보관 기간이 확정되지 않았다.
다음 정책을 확정하기 전에는 PDF 원본 영구 저장, 새로운 원문 영구 저장과 보관 기간 하드코딩을 추가하지 않는다.

- 원문 텍스트 저장 동의 방식
- 보관 기간과 사용자 삭제 기능
- 분석 후보 확정 후 원문 유지 여부
- 백업·로그·캐시에 원문이 남지 않게 하는 범위

## 3. 구현 순서와 담당

| 단계 | 우선순위 | 담당 | 작업 | 완료 조건 |
| --- | ---: | --- | --- | --- |
| A | 0 | 공통·사용자 확인 | PDF 등록·임시 보관과 사용자 분석 시작 API 계약 확정 | 요청·응답·상태 코드·삭제 규칙이 문서에 확정됨 |
| B | 1 | Python 담당 | 실제 PDF → 개인정보 제거·검증 → 실제 LLM → 계약 응답 연결 | 개인정보 제거 실패 시 모델 미호출, 실제 제공자 테스트 통과 |
| C | 1 | Java 담당 | PDF 업로드와 비공개 임시 파일 수명주기 구현 | 크기·형식·권한·즉시 삭제·잔여 파일 정리 테스트 통과 |
| D | 1 | Java 담당 | `ExtractionTask` 생성·실행·조회와 `ProfileCandidate` 저장 구현 | 상태 전이와 Python 성공·실패 응답 처리 테스트 통과 |
| E | 2 | Java 담당 | `GET /api/v1/system/python-status` 구현 | 내부 토큰을 포함한 실제 Python 상태 호출 확인 |
| F | 2 | 공통·프론트 | 추출 후보 조회·수정·확정 API와 화면 구현 | 사용자가 확정하기 전 후보가 `UserProfile`로 사용되지 않음 |
| G | 2 | Python 담당 | 이력서 모델 후보 비교와 신뢰성 평가 | 동일 평가 자료·스키마 통과율·근거 오류율로 모델 선택 |
| H | 3 | 공통 | 채용공고 등록·구조화 계약과 저장 구현 | 필수·우대·업무·기술·근거가 분리되어 저장됨 |
| I | 3 | Java·Python | 확정 프로필과 채용공고 비교 실행 API 연결 | Java 조건 판정과 Python 의미 분석이 분리되어 반환됨 |
| J | 3 | 프론트 | 샘플 결과를 실제 분석 API와 작업 상태로 교체 | 분석 시작·진행·완료·실패가 실제 서버 상태로 표시됨 |
| K | 4 | 공통 | 실제 Java–Python HTTP 계약 테스트 | 실제 PDF·multipart·환경변수·PostgreSQL·Ollama로 통과 |
| L | 4 | 공통 | 브라우저 전체 흐름 검증 | PDF 등록부터 결과와 실패 표시까지 `BROWSER_VERIFIED` |

## 4. Java 구현 범위

Java는 기존 `PythonDocumentExtractionClient`를 재사용하고 다음 유스케이스를 구현한다.

- `createExtractionTask`: 사용자 소유권과 실행 가능 상태를 확인하고 새 작업을 생성한다.
- `executeDocumentExtraction`: 임시 PDF를 읽어 Python에 전달하고 작업 상태를 전환한다.
- `retrieveExtractionTask`: 현재 사용자에게 자신의 작업 상태와 실패 정보를 반환한다.
- `saveProfileCandidate`: 계약 검증을 통과한 후보와 최소 근거를 저장한다.

추가 구현 대상은 다음과 같다.

- `UserDocument`, `ExtractionTask`, `ProfileCandidate` 저장 모델과 Flyway 마이그레이션
- PDF 임시 저장 설정 객체와 파일 수명주기 서비스
- 분석 시작·상태 조회·후보 조회 API
- Python 연결 상태를 반환하는 서비스와 사용자 API
- Python 왕복 시간과 단계별 처리 시간 측정

Controller에는 HTTP 변환만 두고 작업 상태 전이와 Python 장애 분류는 Service에서 처리한다.

## 5. Python 구현 범위

Python 담당은 내부 API 경계를 유지한다.

```http
POST /internal/v1/documents/extract
```

구현·검증 대상은 다음과 같다.

- 실제 PDF 페이지 텍스트 추출
- 이메일·전화번호·주민등록번호와 계약상 제거 대상 개인정보 제거
- 개인정보 제거 완료 검증과 `PII_SANITIZATION_FAILED`
- 이력서 전용 Pydantic 스키마와 구조화 프롬프트
- 실제 Ollama 또는 Gemini 호출
- 후보와 최소 근거의 계약 검증
- 모델 장애와 모델 응답 오류 구분
- 개인정보가 후보·근거·로그에 남지 않는지 확인

모델 교체는 파이프라인 연결을 막지 않고 병렬 평가한다.
`qwen2.5`, `exaone3.5` 같은 모델 이름만으로 선택하지 않고 같은 평가 PDF에서
스키마 통과율, 근거 연결 오류, 생성된 사실과 처리 시간을 비교한다.

## 6. 프론트 구현 범위

- 텍스트 입력 중심 화면을 PDF 업로드 흐름으로 변경한다.
- 분석 시작 응답의 작업 식별자로 실제 상태를 조회한다.
- 샘플 단계, 체크리스트, 유사도와 부족 역량 값을 제거한다.
- 처리 단계, 실패 원인과 재시도 가능 여부를 서버 응답으로 표시한다.
- 추출 후보를 사용자가 수정·확정할 수 있게 한다.
- 채용공고 하나를 등록하고 조건 판정·의미 분석 결과를 한 화면에 표시한다.
- AI 실행 중단 기능은 서버 중단 계약이 확정된 뒤 실제 API에 연결한다.

## 7. 검증 게이트

### Gate 1: 서버별 검증

- Java 21 컴파일과 Java 기능 테스트
- Python 문법·단위 테스트
- 실제 PDF와 실제 Ollama 또는 Gemini를 사용한 Python 제공자 호출
- 개인정보, 내부 토큰과 모델 원문 응답의 로그 노출 검사

이 단계만 통과하면 최대 `UNIT_TESTED`다.

### Gate 2: 실제 통합 검증

다음을 함께 실행한다.

```text
Java + PostgreSQL + Python + Ollama
```

브라우저가 아닌 실제 HTTP 요청으로 다음을 확인한다.

- PDF multipart 업로드
- Java 임시 파일 생성과 삭제
- 동일한 내부 토큰과 요청 식별자 전달
- Python 계약 성공·실패 응답
- Java 작업 상태 전이와 후보 저장
- 개인정보가 응답·DB·일반 로그에 남지 않음

이 단계가 통과해야 `INTEGRATION_TESTED`다.

### Gate 3: 브라우저 검증

실제 브라우저에서 다음 흐름을 확인한다.

```text
로그인
→ PDF 등록
→ 분석 시작
→ 진행 상태
→ 후보 수정·확정
→ 채용공고 등록
→ 분석 결과
→ 실패와 재시도 안내
```

이 단계가 통과해야 MVP 완료 기준인 `BROWSER_VERIFIED`다.

## 8. MVP 이후로 미루는 작업

- GitHub 저장소 내용을 분석 결과에 결합하는 기능
- 관리자 전용 AI 실행 화면
- 측정 전 캐시·병렬 처리·자동 재시도
- 여러 모델 자동 대체
- PDF 외 DOCX·HWP 입력
- 운영 배포 전 대규모 성능 최적화

## 9. 다음 즉시 작업

1. PDF 등록과 임시 보관 수명주기를 포함한 사용자 API 계약을 확정한다.
2. 원문 텍스트 동의·보관 기간은 별도 정책 결정으로 기록한다.
3. Python 담당은 실제 제공자를 사용한 이력서 파이프라인 결과를 전달한다.
4. Java 담당은 계약 확정 후 PDF 업로드와 `ExtractionTask` 구현을 시작한다.
5. 양쪽 구현이 합쳐지면 같은 가상 개인정보 PDF로 실제 HTTP 통합 테스트를 실행한다.
