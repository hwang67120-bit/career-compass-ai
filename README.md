# career-compass-ai

AI 취업 방향 분석 서비스 — 사용자의 이력서·포트폴리오·희망 조건과 채용시장·회사 데이터를 비교해, 어떤 회사와 직무를 목표로 해야 하는지, 부족한 역량은 무엇인지, 해당 분야의 수요와 경쟁은 어느 정도인지 데이터와 근거로 제공한다.

## 구조

- `backend-java`: Java 서버 — 인증·인가, 비즈니스 정책, 데이터 저장, 최종 판정
- `ai-python`: Python 서버 — 문서 처리, 정보 추출, 임베딩, 유사도 계산, LLM 실행
- `contracts`: Java와 Python 사이의 요청·응답 계약
- `deploy`: Docker Compose와 배포 설정
- `docs`: 작업 기준과 설계를 공유하는 문서 (`docs/tasks`: 진행 중 작업, `docs/architecture`: 시스템 구조 설명)

## 협업 규칙

작업을 시작하기 전에 [AGENTS.md](AGENTS.md)를 먼저 읽는다.

## 현재 상태

초기 단계, MVP 진행 중.
