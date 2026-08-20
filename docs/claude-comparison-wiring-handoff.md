# Phase C 구현 스펙 — 비교(judge) 배선 [Claude → Codex 핸드오프]

작성 2026-08-18 (Claude). Codex가 8/20 리셋 후 구현·컴파일·테스트하는 것을 전제로 한다.
Claude는 Java를 로컬 빌드할 수 없어 코드를 직접 검증하지 못했다 — 아래는 실측 조사로
매핑한 설계다. **file:line은 재확인 필수(코드가 계속 바뀜).**

## 목표
추출까지 끝난 분석에서, **확정된 사용자 프로젝트 담당업무 vs 공고 담당업무**를 LLM judge로
비교해 분석을 `COMPLETED`(또는 `PARTIALLY_COMPLETED`)로 완료하고 결과를 노출한다.
현재는 `COMPARISON_STAGE_NOT_IMPLEMENTED` 스텁으로 실패 처리된다.

## 현재 상태 (2026-08-18)
- **Phase A 검증 완료**: 데모 공급자(#106) + GitHub PAT 인증(#107) + Ollama 모델 스왑(#108)로
  전체 파이프라인 통과. DevSample 백엔드 공고 추출 = 직무명 True·필수기술 3·근거 6.
- **judge 자체는 작동**: `POST /internal/v1/job-evidence-similarities`(Python), 실제 qwen2.5
  36/36. Java 클라이언트 `PythonEvidenceSimilarityClient.compare(...)` 존재·검증됨.
- **재개 메커니즘 존재**: 사용자 확정 완료 → `ProjectResponsibilityReviewService.completeReview`
  → 확정 담당업무로 새 `UserProfileVersion` 생성 → `JobAnalysis.resumeAfterUserConfirmation(...)`로
  분석 재개. 프론트 확정 UI만 없다(별도 항목).

## 흐름
1. 1차 처리(`JobAnalysisWorker.processClaimedAnalysis`): repo 분석 → 검색 → 추출 → 공고 저장.
   `responsibilityOutcome.requiresUserConfirmation()==true`면 `AWAITING_USER_CONFIRMATION`.
2. 사용자 확정(`PUT /api/v1/project-responsibility-candidates/{id}/decision`) → 모두 검토 완료 시
   `completeReview` → `resumeAfterUserConfirmation` → 분석 재큐, `currentStep=COMPARING_EVIDENCE`.
3. 워커 재처리: `JobAnalysisWorker.java`의 `if (currentStep == COMPARING_EVIDENCE)` 분기
   (현재 ~108~113행, `COMPARISON_STAGE_NOT_IMPLEMENTED` 스텁) — **여기에 비교를 구현한다.**

## 백엔드 구현 (워커 COMPARING_EVIDENCE 분기 교체)

### 필요 입력
- **저장된 공고**: `jobAnalysisService.listPostings(jobAnalysisId)` → `List<JobAnalysisPosting>`.
  각 posting의 `extractionJson`(문자열)에 공고 추출 결과(jobTitle·requiredSkills·
  responsibilities·evidence). **공고 근거 = responsibilities에 연결된 evidence 항목.**
- **확정 사용자 근거**: 재개 시 고정된 `UserProfileVersion`(확정 담당업무 포함), 또는
  `ProjectResponsibilityCandidate` 중 `CONFIRMED` 조회. 각 candidate: `id`, `confirmedText`,
  소속 `projectSource`(→ projectSourceId), `extractionTask`.

### judge 호출 (계약 `contracts/job-evidence-similarity.md`)
`PythonEvidenceSimilarityClient.compare(comparisonTaskId, jobAnalysisId, jobPostingId,
List<JobEvidence>, List<UserEvidence>)`:
- **jobEvidence**: `{evidenceId, category:"RESPONSIBILITY", text}` — 공고 담당업무 근거.
  ※계약: 공고 category는 `RESPONSIBILITY`만 허용.
- **userEvidence**: `{evidenceId: candidate.id, projectSourceId, category:"PROJECT_RESPONSIBILITY",
  text: confirmedText}` — 확정 프로젝트 담당업무만. ※계약: 사용자 category는
  `PROJECT_RESPONSIBILITY`만 허용.
- 응답 `Data{status, method, results[]{jobEvidenceId, status, bestMatchUserEvidenceId, score,
  judgment(RELATED/NOT_RELATED), unavailableReason}, modelExecution}`.
- **공고별로 1회 호출**(공고마다 jobPostingId 다름). 공고 담당업무 근거가 0개면 그 공고는
  `NOT_CALCULABLE`로 두고 judge 호출 생략 가능(불필요한 호출 방지).

### 저장 (신규)
- **권장**: `JobAnalysisPosting`에 `comparison`(JSON, nullable) 컬럼 추가 + Flyway 마이그레이션
  (다음 번호). judge 응답의 항목 결과·method·provider·model을 그 공고 행에 저장. `extraction`
  컬럼과 같은 패턴(ObjectMapper로 직렬화). ※워커는 스프링 자동구성 Jackson(3.x)과 충돌 피하려
  `new ObjectMapper()`(2.x)를 직접 쓴다 — 기존 주석 참고.

### 완료 처리
- 모든 공고 비교 후: 하나 이상 `CALCULATED`면 `COMPLETED`, 일부만 계산되면
  `PARTIALLY_COMPLETED`(계약·이슈 #86의 부분계산 정의 확인 필요). 단계는
  `COMPARING_EVIDENCE → FINALIZING_RESULT → FINISHED`로 전진 후 완료.
- `JobAnalysisService`에 `recordComparisonCompleted(jobAnalysisId, 결과)` 류 메서드 신설
  (기존 `recordExtractionCompletedWithoutComparison` 대체 경로).

## 결과 API
- `GET /api/v1/job-analyses/{id}` 응답(`JobAnalysisResponse`)과 공고 목록에 비교 결과를 포함.
  현재는 상태·단계·카운트만 반환한다.

## 프론트엔드 (별도, 8/22+ Codex)
`backend-java/src/main/resources/static/app.js`:
1. **`AWAITING_USER_CONFIRMATION` 처리**: `renderJobAnalysisState`에 케이스 없음 → 무한 스피너.
   상태 안내 + 확정 화면으로 유도 필요.
2. **담당업무 후보 확정 UI**: `GET /api/v1/project-sources/{id}/responsibility-candidates`로
   후보 조회, `PUT .../project-responsibility-candidates/{id}/decision`으로 확정/거부. 현재 없음.
3. **결과 렌더**: `renderJobAnalysisState`가 `COMPLETED`에서 상태 텍스트만 표시. 비교 결과
   (관련 공고·judgment)를 표시. `styles.css`에 `.similarity-bars`/`.similarity-fill` CSS는 있으나
   미연결.

## 결정 필요 / 리스크
- **이슈 #86**: `PARTIALLY_CALCULATED` 발생 조건, `unavailableReason` 3종(빈 근거 책임 경계).
  비교 구현과 함께 확정 필요.
- **담당업무 추출 안정성**: #108 모델 스왑(담당업무=exaone3.5) 후 공고 담당업무가 실제로
  추출되는지 서버 검증(judge 입력 전제). qwen2.5 담당업무 약점은 문서화됨.
- **확정 사용자 근거 로드 경로**: 재개된 분석에서 확정 담당업무를 어디서 읽을지(고정
  UserProfileVersion vs CONFIRMED candidates) 명확히. `completeReview`가 만든 프로필 버전이
  단일 진실.

## 검증 (서버)
1. 데모 모드(`SPRING_PROFILES_ACTIVE=prod,demo`, `JOB_SEARCH_PROVIDER=dev-sample`) + PAT + 스왑.
2. 새 분석 → `AWAITING_USER_CONFIRMATION` 도달.
3. (프론트 전) `PUT decision` API로 후보 확정 → 재개.
4. 워커 재처리 → 비교 로그 → `COMPLETED`. ai-python judge 로그(`job-evidence-similarities`) 확인.
5. `GET /job-analyses/{id}`에 비교 결과 포함 확인.

## 참고
- 전체 맥락: 메모리 `project_career_compass_java_handoff_readiness`,
  `project_career_compass_public_data_unusable`.
- 공공 API 데이터는 개발 직무에 부적합(2008 행정공고) 확정 — 데모는 DevSample로.
