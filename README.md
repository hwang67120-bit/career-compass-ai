# career-compass-ai

사용자의 이력서·포트폴리오와 채용공고를 근거로 비교해 지원 조건, 업무·기술 유사도와 보완할 역량을 보여주는 AI 취업 방향 분석 서비스다.

## MVP 최우선 흐름

```text
PDF 등록
→ 추출 결과 수정·확정
→ 채용공고 등록
→ Java 조건 판정 + Python 의미 분석
→ 결과 화면
→ 테스트 서버 배포
```

8월 21일 MVP까지 이 흐름에 직접 필요하지 않은 부가기능은 우선 구현하지 않는다. 오케스트레이션은 전체 흐름의 정확성, 단계별 실패 확인과 실제 처리 시간 측정을 먼저 완료한 뒤 확인된 병목만 개선한다.

## 프로젝트 구조

- `backend-java`: 인증·인가, 사용자 API, 상태 관리, 데이터 저장, 조건 판정, Python 작업 오케스트레이션과 최종 결과
- `ai-python`: PDF 텍스트 추출, 개인정보 제거, 구조화 추출, 임베딩, 의미 유사도와 LLM 실행
- `contracts`: Java와 Python 사이의 요청·응답 계약
- `deploy`: Docker Compose와 배포 설정
- `docs`: 정책, 설계, 상태 소유권과 결정 기록
- `postman`: 개발 환경 API 연결 확인 자료

## 현재 구현 상태

| 영역 | 상태 |
|---|---|
| GitHub OAuth 로그인과 서버 세션 | Java 구현 완료 |
| 텍스트 이력서·포트폴리오 등록 | Java 구현 완료 |
| 공개 GitHub 저장소 검증·등록 | Java 구현 완료 |
| 브라우저 확인 화면 | Java 구현 완료 |
| PDF 페이지 텍스트 추출 | Python 서비스 구현 완료 |
| PDF Java–Python 추출 계약 | MVP 확정 |
| Ollama·Gemini 구조화 추출, 임베딩·유사도·재정렬 | Python 내부 구현 완료 (API 미연결) |
| 내부 서비스 인증(2차 방어선) | Python 구현 완료, Java 쪽 헤더 전송은 미구현 |
| PDF 업로드·비동기 추출 작업 연결 | 미구현 |
| 추출 후보 수정·확정 | 미구현 |
| 채용공고 등록·분석·결과 화면 | 미구현 |

구현 상태는 브랜치 통합 시점에 따라 작업본과 차이가 날 수 있다. API의 구현 여부는 `java` 브랜치, Python 기능은 `python` 브랜치, 공유 정책과 계약은 통합된 프로젝트 문서를 최종 기준으로 확인한다.

## 문서 안내

- 협업·정책·코드 규칙: [AGENTS.md](AGENTS.md)
- Java API와 공통 예외: [backend-java/README.md](backend-java/README.md)
- Python API와 내부 구조: [ai-python/README.md](ai-python/README.md)
- Java–Python 계약 목록: [contracts/README.md](contracts/README.md)
- PDF 문서 추출 계약: [contracts/document-extraction.md](contracts/document-extraction.md)
- 상태 소유권: [docs/architecture/domain-state-ownership.md](docs/architecture/domain-state-ownership.md)
- Java–Python 연결: [docs/architecture/java-python-connection.md](docs/architecture/java-python-connection.md)
- 가드레일: [docs/architecture/guardrails.md](docs/architecture/guardrails.md)
- 계층·컴포넌트 이름 정의: [docs/architecture/layer-terminology.md](docs/architecture/layer-terminology.md)
- LLM 제공자 연동(Ollama·Gemini): [docs/architecture/llm-providers.md](docs/architecture/llm-providers.md)
- 임베딩과 의미 유사도: [docs/architecture/embedding-similarity.md](docs/architecture/embedding-similarity.md)
- 후보 재정렬: [docs/architecture/reranking.md](docs/architecture/reranking.md)
- 학습 데이터 기준: [docs/architecture/training-data-standards.md](docs/architecture/training-data-standards.md)
- 오차 범위 계산·보정: [docs/architecture/error-calibration.md](docs/architecture/error-calibration.md)

## 책임 경계

### Java

- 사용자를 인증하고 자신의 자료에만 접근하도록 제한한다.
- 문서, 추출 작업, 사용자 확인 후보, 확정 프로필과 분석 작업 상태를 소유한다.
- 명확한 필수·우대 조건을 규칙으로 판정한다.
- Python 호출 순서, 실패 상태와 최종 결과를 관리한다.

### Python

- PDF에서 텍스트와 구조화 후보를 추출한다.
- 개인정보를 제거한 최소 데이터만 모델에 전달한다.
- 업무·기술·프로젝트의 의미 유사도를 계산한다.
- Java의 사용자·작업 상태를 직접 변경하지 않는다.

### 공통 계약

- 서버 간 통신 변경은 `contracts`를 먼저 수정한다.
- Java DTO와 Python Pydantic 모델은 같은 필드, enum과 오류를 사용한다.
- 양쪽 서버에서 같은 예제로 계약 테스트를 실행한다.
- 계약에 없는 필드와 상태를 구현에서 임의로 추가하지 않는다.

## 완료 기준

1. Java와 Python 단위 테스트
2. 두 서버를 함께 실행한 실제 HTTP 연결 테스트
3. 사용자 흐름을 확인하는 브라우저 테스트
4. 테스트 서버 배포 후 외부 환경 확인

현재 MVP 완료 판정에는 1~3단계를 적용하고, 서버 컴퓨터가 준비되면 4단계를 추가한다.
