# syntax=docker/dockerfile:1
# backend-java(Spring Boot) 컨테이너. 프론트 static 자산을 포함해 8080에서 서빙한다.
# 요구사항: docs/operations/runtime-connectivity-runbook.md "컨테이너 환경 파일 검토안".
#
# NOTE(Codex 확인): 이 프로젝트엔 gradlew 래퍼가 없어 고정 빌더 이미지(gradle:8.14-jdk21)를
# 쓴다(결정 ①). 아래 3가지는 backend-java 빌드 설정에 맞게 확인 필요:
#   1) bootJar 산출물만 나오는지(plain jar 비활성). bootJar만 실행하면 build/libs에 단일 jar.
#   2) /actuator/health 헬스체크 — spring-boot-starter-actuator 의존성이 있어야 함.
#   3) 실행 env 변수명이 application.yml과 일치하는지(compose와 대조).

# --- builder: Gradle 8.14 + JDK 21로 bootJar 생성 ---
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon

# --- runtime: JRE 21 슬림 + 비-root ---
FROM eclipse-temurin:21-jre AS runtime
RUN useradd -m -u 10001 appuser
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080

# HEALTHCHECK 미설정(의도적): JRE 이미지엔 curl/wget이 없어 간단한 명령이 없다.
# Codex 확정 필요 — 예: 런타임에 curl 설치 후 `curl -f http://127.0.0.1:8080/actuator/health`,
# 또는 actuator 없으면 다른 liveness 경로. 확정 전까지 compose의 depends_on은 Java를
# health가 아닌 started 기준으로만 참조한다.

ENTRYPOINT ["java", "-jar", "app.jar"]
