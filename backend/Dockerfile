# syntax=docker/dockerfile:1

# --- build stage: 소스에서 실행 가능한 jar 생성 ---
# Render에는 사전 빌드된 jar가 없으므로 컨테이너 안에서 gradle 빌드를 수행한다.
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# 래퍼/빌드 스크립트 먼저 복사 (소스 변경 시 의존성 레이어 캐시 활용)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
# 테스트는 CI에서 돌리므로 이미지 빌드에서는 제외해 빌드 시간을 줄인다.
RUN ./gradlew clean bootJar --no-daemon -x test

# --- runtime stage: JRE만 포함한 가벼운 이미지 ---
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# 컨테이너 메모리(Render free 512MB)에 맞춘 힙 상한. 필요 시 env로 덮어쓴다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

COPY --from=build /app/build/libs/*.jar app.jar

# 문서용. 실제 바인딩 포트는 $PORT(Render가 주입) → application.yml의 server.port
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
