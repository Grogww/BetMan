# syntax=docker/dockerfile:1

# ---------- Stage 1: build (Maven + JDK 21) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies get their own layer, reused until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Tests run in the CI pipeline, not in the image build
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: runtime (JRE 21 only) ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S -G spring spring

COPY --from=build --chown=spring:spring /workspace/target/betman-*.jar app.jar
USER spring

EXPOSE 8080

# Readiness includes the database, so "healthy" means ready to serve requests.
# sh -c expands SERVER_PORT at runtime (exec form alone doesn't expand variables)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["sh", "-c", "wget -q -O /dev/null http://localhost:${SERVER_PORT:-8080}/actuator/health/readiness || exit 1"]

# Size the heap from the container memory limit instead of the host's RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
