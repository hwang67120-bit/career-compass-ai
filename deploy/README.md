# 서버 테스트 배포 (deploy/)

career-compass를 상시 서버에서 테스트 배포하는 절차다(AWS 전 스테이징). Ollama는 서버에 설치하지 않고 **별도 모델 머신**을 `OLLAMA_BASE_URL`로 호출한다. 브라우저 접근은 Java(8080)만 공개한다.

## 사전 준비 (서버)
- Docker + Compose 플러그인 설치
- 최신 코드 `git pull` (develop)
- 모델 머신에서 Ollama 실행 + 서버에서 접근 확인: `curl <OLLAMA_BASE_URL>/api/tags` → 200

## 환경변수
```bash
cp deploy/.env.example deploy/.env
# deploy/.env에 실제 값 채우기: INTERNAL_SERVICE_TOKEN, DB_PASSWORD,
# OLLAMA_BASE_URL(모델 머신 주소), GEMINI_*, GITHUB_OAUTH_* 등
# deploy/.env는 .gitignore로 커밋 제외됨
```

## Phase 1 — Python + DB (지금 가능, Docker 빌드 첫 검증)
Java Dockerfile 확정 전에도 Python·DB로 배포 파이프라인을 먼저 검증한다.
```bash
docker compose -f deploy/compose.yaml --env-file deploy/.env config      # 문법·치환 검증
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d postgres ai-python
docker compose -f deploy/compose.yaml ps                                  # 상태 확인
```
확인:
- `postgres` = healthy, `ai-python` = healthy (`/livez` 기반)
- `docker compose logs ai-python` → 시작 로그. 모델 머신이 켜져 있으면 "Ollama 연결을 확인했습니다", 꺼져 있으면 원격 경고(로컬 PATH 문제로 오인하지 않음)
- Python·Postgres 포트는 host에 공개되지 않음(내부망 전용) — 헬스는 `ps`의 health로 확인

## Phase 2 — 전체 스택 (Codex의 Java Dockerfile 확정 후)
`backend-java/Dockerfile`의 확인 4점(고정 빌더/bootJar/actuator/env명)이 정리되면:
```bash
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build
```
확인:
- 브라우저 `http://<서버 접근주소>:8080` (내부망이면 `JAVA_BIND_ADDRESS`를 LAN IP로 + 방화벽)
- Flyway 마이그레이션, Java→Python 내부 토큰 요청 성공
- 참고: 제품 파이프라인(조건 비교·결과)이 아직 구현 중이면 추출 단계까지만 보일 수 있음

## 검증 체크리스트 (runtime-connectivity-runbook 절차와 동일)
1. 비밀값 없이 `docker compose config` 성공
2. Python·Java 이미지 각각 빌드
3. Ollama 끈 상태에서 PG·Python·Java 기동, 상태가 구분됨
4. Flyway 마이그레이션과 Java→Python 내부 토큰 요청 성공
5. 모델 머신 켠 뒤 서버→Ollama 연결·실제 분석 요청 확인
6. 재시작 뒤 DB 데이터·자동 시작·로그 회전 확인
7. 브라우저에서 분석 단계·단계별 실패 표시 확인

## 정리
```bash
docker compose -f deploy/compose.yaml down       # 컨테이너 종료(볼륨=DB 데이터 유지)
docker compose -f deploy/compose.yaml down -v    # 볼륨까지 삭제(데이터 초기화)
```
