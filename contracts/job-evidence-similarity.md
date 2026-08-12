# 채용공고 근거 의미 비교 내부 계약

상태: 제안 — 후보 품질 평가와 확인 필요 항목을 확정하기 전에는 구현하지 않는다.

## 목적과 책임 경계

Java가 전달한 채용공고 담당 업무와 사용자 프로젝트 업무 근거를 Python이 의미적으로 비교한다.

- Java: 필수·우대 기술 등 명확한 조건 판정, 개인정보 제거, 작업·저장·권한 관리
- Python: `RESPONSIBILITY`와 `PROJECT_RESPONSIBILITY`의 의미 비교
- 제외: 기술 보유 여부, 지원 가능 여부, 합격 확률, 추천 순위와 자연어 설명

Python 결과는 Java의 `MATCHED`, `MISMATCHED`, `NEEDS_REVIEW`,
`NOT_APPLICABLE`을 변경할 수 없고 두 결과를 불투명한 총점으로 합치지 않는다.

## 엔드포인트

```http
POST /internal/v1/job-evidence-similarities
Content-Type: application/json
X-Internal-Token: {INTERNAL_SERVICE_TOKEN}
X-Request-Id: {uuid}
```

## 요청

```json
{
  "comparisonTaskId": "37ac4f55-7140-4c14-a6aa-fcfc7ea5d75a",
  "jobAnalysisId": "10000000-0000-0000-0000-000000000001",
  "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
  "jobEvidence": [
    {
      "evidenceId": "job-responsibility-1",
      "category": "RESPONSIBILITY",
      "text": "대규모 트래픽을 처리하는 백엔드 API를 개발합니다."
    }
  ],
  "userEvidence": [
    {
      "evidenceId": "project-responsibility-1",
      "projectSourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
      "category": "PROJECT_RESPONSIBILITY",
      "text": "Redis 캐시와 비동기 작업으로 API 응답 부하를 줄였습니다."
    }
  ]
}
```

- 근거 식별자는 요청 안에서 중복할 수 없다.
- 공고 category는 `RESPONSIBILITY`, 사용자 category는 `PROJECT_RESPONSIBILITY`만 허용한다.
- 개인정보가 제거되고 사용자가 확인한 최소 근거만 전송한다.
- 개수와 길이 제한은 실제 표본 측정 후 양쪽 설정으로 확정한다.

## 성공 응답

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "comparisonTaskId": "37ac4f55-7140-4c14-a6aa-fcfc7ea5d75a",
    "jobAnalysisId": "10000000-0000-0000-0000-000000000001",
    "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
    "status": "CALCULATED",
    "method": "LLM_JUDGE",
    "results": [
      {
        "jobEvidenceId": "job-responsibility-1",
        "status": "CALCULATED",
        "bestMatchUserEvidenceId": "project-responsibility-1",
        "judgment": "RELATED",
        "unavailableReason": null
      }
    ],
    "modelExecution": {
      "stage": "EVIDENCE_SEMANTIC_COMPARISON",
      "provider": "OLLAMA",
      "model": "evaluated-model-name"
    }
  },
  "error": null,
  "timestamp": "2026-08-11T08:00:00Z"
}
```

- `status`: `CALCULATED`, `PARTIALLY_CALCULATED`, `NOT_CALCULABLE`
- `method`: `LLM_JUDGE`
- `LLM_JUDGE`는 `RELATED` 또는 `NOT_RELATED`인 `judgment`만 반환한다.
- LLM 판정은 별도 보정 전 숫자 점수와 confidence를 반환하지 않는다.
- provider는 실제 분석을 실행한 `OLLAMA` 또는 `GEMINI`, model은 실제 모델 이름이다.
- Ollama가 기본이며 Gemini는 폴백과 제한적인 교차검증에 사용한다.

## 계산 불가

```json
{
  "jobEvidenceId": "job-responsibility-1",
  "status": "NOT_CALCULABLE",
  "bestMatchUserEvidenceId": null,
  "score": null,
  "judgment": null,
  "unavailableReason": "COMPATIBLE_USER_EVIDENCE_MISSING"
}
```

근거 부족은 0점이나 모델 장애가 아니다. 안전 처리 후 근거가 빈 경우도
`JOB_EVIDENCE_EMPTY_AFTER_SANITIZATION` 또는 `USER_EVIDENCE_EMPTY_AFTER_SANITIZATION`으로 구분한다.

## 품질 게이트

1. 같은 업무 쌍이 다른 업무 쌍보다 더 관련 있게 나와야 한다.
2. 서로 다른 기술을 동일한 최고값으로 만들면 안 된다.
3. 반복 결과와 근거 식별자가 안정적이어야 한다.
4. 반환 근거가 입력에 존재하고 판정을 뒷받침해야 한다.
5. 지연시간과 로컬 자원 사용량을 기록한다.

2026-08-11 `nomic-embed-text` 재평가에서 같은 백엔드 업무는 `0.9948`, 관련 없는
프론트엔드 업무는 `0.9980`이었고 Python–Java와 Python–React가 모두 `1.0000`이었다.
따라서 이 모델과 현재 계산법은 품질 게이트 실패다. Gemini 할당량 소진은 일시적 제약일 뿐
모델 선택 근거가 아니다.

2026-08-12 `ai-python/evaluation/job_evidence_judge_spike.py`로 `LLM_JUDGE`(Ollama)를 후보 3개
모델(qwen2.5·exaone3.5·llama3.2)로 평가했다. 17개 공고 담당 업무를 도메인별 사용자 프로젝트
근거 7개와 비교(각 3회 반복, temperature 0)한 결과 `qwen2.5:latest`가 best-match 36/36,
`RELATED`/`NOT_RELATED` 분류 42/42, 반환 근거 유효 51/51, 비결정성 0으로 위 5개 게이트를 모두
통과했다. exaone3.5(30/36, 오판 존재)와 llama3.2(18/36, 도메인 붕괴)는 부적합이다. 다만 사용자
프로젝트 근거가 도메인별 1개 합성 표본이라 도메인 구분력만 증명했고, 실제 시장 정확도와 유사
프로젝트 경합 난이도는 아직 검증 전이다([job-fit-semantic-similarity.md](../docs/architecture/job-fit-semantic-similarity.md) 2026-08-12 결과 참고).

## 저장·오류·보안

Python은 영구 저장하지 않는다. Java는 식별자, 항목 결과, method, provider, model,
계산 시각과 근거 버전을 저장한다. 원문, 개인정보, 자격증명과 임베딩 벡터는 별도 정책 없이
영구 저장하지 않는다.

| HTTP | errorType | retryable |
|---:|---|---:|
| 401 | `INTERNAL_UNAUTHORIZED` | false |
| 422 | `INVALID_SIMILARITY_REQUEST` | false |
| 502 | `SEMANTIC_COMPARISON_RESPONSE_INVALID` | false |
| 503 | `SEMANTIC_COMPARISON_MODEL_UNAVAILABLE` | true |

Java는 식별자, 결과 개수, 근거 참조, enum, judgment, provider와 model을 검증한다. 계약 위반 응답은 저장하지 않는다.

## 공동 계약 테스트

1. 담당 업무와 프로젝트 업무 정상 비교 및 다른 category 거부
2. 근거 부족의 `NOT_CALCULABLE`
3. 결과 개수·순서, 식별자와 judgment 검증
4. 내부 토큰 누락·불일치와 선택 모델 장애
5. Java와 Python을 함께 실행한 실제 HTTP 요청

## 구현 전 확인 필요

1. 합성공고 fixture LLM_JUDGE 품질 평가 — Ollama 기본 경로는 2026-08-12 완료
   (`qwen2.5:latest`가 도메인 구분 게이트 통과, 위 품질 게이트 절 참고). 남은 것: Gemini 폴백이
   같은 계약을 지키는지, 실제 시장 표본과 사용자 근거 다수 경합 난이도.
2. 최종 provider, model과 LLM 판정 enum (Ollama 기본 유력 후보: `qwen2.5:latest`)
3. 사용자 프로젝트 근거의 생성·확인·버전 관리
4. 최고 근거 개수, 배열·문장 제한과 모델 변경 정책
5. `NOT_CALCULABLE`을 정상 완료로 볼지 여부

확정 전에는 DTO, enum, 저장 테이블과 Python endpoint를 구현하지 않는다.
