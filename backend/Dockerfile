FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY build/libs/mini-exchange-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
