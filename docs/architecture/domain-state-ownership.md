# 도메인 상태 소유권 (이력서·프로필 파이프라인)

이 문서는 `AGENTS.md`의 `docs/architecture` 정의에 따라 삭제되지 않고 유지되는 아키텍처 설명이다. 실제 Java 엔티티·enum 구현 전에 상태 소유권과 전이를 먼저 확정하기 위한 문서다.

## 배경

문서 등록 여부, 추출 성공 여부, 사용자 확인 여부를 하나의 `DocumentStatus` enum에 섞으면 서로 다른 대상(문서/작업/프로필)의 상태가 뒤섞여 나중에 반드시 복잡해진다. 그래서 대상별로 상태를 분리하고, 구현 전에 상태 전이와 대표 시나리오를 먼저 문서화한다.

## 상태 소유자 표

| 대상 | 책임 |
|---|---|
| `UserDocument` | 등록된 문서와 출처 (검증을 통과한 원본만, 불변) |
| `ExtractionTask` | 추출 진행·실패 (비동기 작업 단위) |
| `ProfileCandidate` | 사용자가 수정할 추출 후보 (검토·수정 상태는 여기 소속) |
| `UserProfile` | 사용자가 확정한 사실 (버전 관리됨) |
| `ProjectSource` | GitHub 저장소 출처 |
| `AnalysisJob` | 전체 분석 진행 상태 |

## 확정한 규칙

1. **업로드 실패는 저장하지 않는다.** 입력 검증 실패는 `UserDocument`를 생성하지 않고 오류 응답만 즉시 반환한다. (근거: 노션 플로우차트 "입력 검증 통과 여부 → 아니오 → 검증 실패 응답 구성·반환"에 저장 단계가 없음)
2. **문서 등록과 정보 추출은 분리된 대상이다.** `UserDocument` 등록은 동기적으로 즉시 처리하고, `ExtractionTask`는 별도의 비동기 작업 단위로 생성한다.
3. **검토·수정 중 상태는 `ProfileCandidate`가 가진다.** `UserDocument`는 등록된 사실만 표현하는 불변 객체로 유지한다.
4. **재확정은 무효화가 아니라 새 버전 생성이다.** 확정된 `UserProfile`을 다시 수정하면 기존 분석 결과를 삭제하지 않고, 새 버전을 만든다. 기존 분석 결과는 "이전 버전 기준"으로 남고, 새 분석은 새 버전을 기준으로 다시 실행해야 한다. (근거: 노션 플로우차트 "확정 프로필·채용공고 버전 생성: 확인된 데이터만 새 버전으로 저장")
5. **추출 실패 후 재시도는 새 `ExtractionTask`를 생성한다.** 같은 작업을 갱신하지 않고 새로 만들어 실패 이력을 보존한다. 새 작업은 같은 `UserDocument`를 참조한다.

## 상태 전이표

| 단계 | 주체 | 성공 시 | 실패 시 | 재시도 |
|---|---|---|---|---|
| PDF 검증 | Java | `UserDocument` 등록 진행 | 오류 응답만 반환, 아무것도 저장하지 않음 | 사용자가 새로 업로드 |
| `UserDocument` 등록 | Java | `ExtractionTask` 생성 | 등록 실패 (원자적 처리, 부분 상태 없음) | 사용자가 새로 업로드 |
| `ExtractionTask` 실행 | Python 호출 | `ProfileCandidate` 생성, 작업 상태 완료 | 작업 상태 실패로 기록, 원인 저장 | 새 `ExtractionTask` 생성 (규칙 5) |
| `ProfileCandidate` 검토·수정 | 사용자 | 확정 액션 시 `UserProfile` 생성 | 실패 개념 없음 — 사용자가 검토를 미루면 대기 상태 유지 | 해당 없음 |
| `UserProfile` 확정 | Java | 분석 가능 상태로 전환 | 해당 없음 | 재수정 시 새 버전 생성 (규칙 4) |

## 대표 시나리오

| 시나리오 | 관련 대상 | 흐름 |
|---|---|---|
| 정상 PDF 업로드 및 확정 | `UserDocument` → `ExtractionTask` → `ProfileCandidate` → `UserProfile` | 전체 전이표를 성공 경로로 통과 |
| 규격 외 PDF 거절 | 없음 (아무것도 생성 안 됨) | PDF 검증 실패, 오류 응답만 반환 |
| 등록 성공 후 Python 추출 실패 | `UserDocument`(생성됨) + `ExtractionTask`(실패) | `UserDocument`는 유지, `ExtractionTask`만 실패로 기록 |
| 사용자가 추출 결과를 수정하고 확정 | `ProfileCandidate` → `UserProfile` | 검토·수정 후 확정 액션으로 `UserProfile` 최초 버전 생성 |
| 확정 프로필을 다시 수정 | `UserProfile`(새 버전) | 기존 버전·기존 분석 결과 유지, 새 버전 생성 (규칙 4) |
| 공개 GitHub 저장소 조회 실패 | `ProjectSource` | 조회 실패 처리 방식은 확인 필요 (아래 항목 참고) |

## 확인 필요 — 아직 정해지지 않음

- **사용자가 무엇을 해야 `UserProfile`이 CONFIRMED가 되는가** — 명시적 확정 액션(API 호출)이 필요하다는 방향은 제안하지만, 정확한 트리거(버튼, 화면 흐름)는 화면 설계와 함께 확정해야 한다.
- **PDF와 GitHub 결과를 하나의 프로필로 합치는 시점** — `ProjectSource`가 `UserProfile`과 어떻게 연결되는지 아직 정책이 없다.
- **원본 PDF 파일의 삭제 시점** — 저장 비용과 재처리 필요성 사이의 정책 결정이 필요하다.
- **GitHub 저장소가 변경됐을 때 기존 `ProjectSource` 근거를 어떻게 처리할지** — 정책 없음.

## 다음 단계

이 문서가 확정되면, Python 쪽 `ExtractionTask` 실행 결과 상태(성공/모델 없음/입력 오류/제한시간 초과/생성 실패/취소)를 `contracts`에 정의해 Java의 `ExtractionTask` 상태 전이와 연결한다.
