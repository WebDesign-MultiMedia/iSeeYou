# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the fat jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Copy the POM first so the dependency layer is reused between code changes.
# (No BuildKit cache mounts: Railway rejects ones without its own id prefix.)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Runtime stage: slim JRE, non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/familymap-api.jar app.jar
USER app
EXPOSE 8080
# Size the heap from the container's memory limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
