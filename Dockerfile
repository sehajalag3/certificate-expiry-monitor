# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN mvn -B -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --from=build /workspace/target/certificate-expiry-monitor-*.jar /app/app.jar
RUN mkdir -p /app/data && chown -R app:app /app

USER app
VOLUME ["/app/data"]
EXPOSE 8080

ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
