# ── Build stage ──────────────────────────────────────────────────────────────
# Option A (CI/full): build inside Docker
# FROM eclipse-temurin:17-jdk AS build
# WORKDIR /workspace
# COPY gradlew . && COPY gradle gradle && COPY build.gradle . && COPY settings.gradle . && COPY src src
# RUN chmod +x ./gradlew && ./gradlew bootJar -x test -x spotbugsMain -x spotbugsTest --no-daemon

# Option B (fast-rebuild): copy pre-built JAR from host
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY build/libs/app.jar app.jar

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user for security
RUN groupadd --system uip && useradd --system --gid uip uip
USER uip

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
