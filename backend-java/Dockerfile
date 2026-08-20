# syntax=docker/dockerfile:1
# backend-java(Spring Boot 4.1, Java 21) 컨테이너. 프론트 static을 8080에서 서빙한다.
# - gradlew 래퍼가 없어 고정 빌더 gradle:8.14-jdk21 사용(wrapper.properties의 8.14와 일치).
# - bootJar만 실행해 단일 실행 jar 생성(plain jar 미생성).
# - 실행 프로파일은 prod(OAuth·세션) — compose에서 SPRING_PROFILES_ACTIVE=prod 주입.

# --- builder: Gradle 8.14 + JDK 21로 bootJar 생성 ---
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon

# --- runtime: JRE 21 + 비-root ---
FROM eclipse-temurin:21-jre AS runtime
# actuator health 확인용 curl (JRE 이미지엔 기본 미포함)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd -m -u 10001 appuser
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080

# actuator /actuator/health (application.yml에서 health 노출 확인됨). Flyway 마이그레이션
# 시작 시간을 감안해 start-period 넉넉히.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
