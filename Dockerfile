# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Stage 1: build with the Maven Wrapper (Maven repository is cached by BuildKit)
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: minimal runtime, non-root user, container health check
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
