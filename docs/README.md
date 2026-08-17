# Career Compass 문서

현재 MVP는 공개 GitHub 저장소와 사용자가 직접 선택한 기술 태그를 기준으로 개발자 채용공고를 비교합니다. PDF·이력서·포트폴리오 입력은 2026-08-03 범위 결정으로 폐기했습니다.

## 현재 구현 기준

- [현재 작업 상태](current-work.md)
- [개발자 채용공고 분석 API](api/developer-job-analysis-api.md)
- [분석 작업과 SSE 처리 결정](architecture/backend-job-processing-and-sse.md)
- [실행 환경 연결 운영 규칙](operations/runtime-connectivity-runbook.md)
- [채용공고 검색 도구 계약](../contracts/job-search-tool.md)
- [채용공고 추출 계약](../contracts/job-posting-extraction.md)

## 아키텍처 결정 기록(ADR)

- [ADR 0001 · 멀티 머신 · 도메인별 병렬 에이전트 구성](adr/0001-multi-machine-parallel-agent-setup.md)
- [ADR 0002 · 바이브 코딩 품질 관리](adr/0002-vibe-coding-quality-management.md)

## 폐기된 이전 설계

다음 문서는 과거 PDF 중심 설계의 결정 이력을 보존하기 위한 자료이며 현재 구현 기준이 아닙니다.

- [이전 MVP 구현 로드맵](mvp-implementation-roadmap.md)
- [이전 분석 책임 경계](analysis-responsibility-boundaries.md)
- [이전 문서 상태 소유권](architecture/domain-state-ownership.md)
- [폐기된 문서 추출 계약](../contracts/document-extraction.md)

적용된 Flyway 마이그레이션과 기존 DB 데이터는 문서 기능 제거와 별개로 보존합니다.
