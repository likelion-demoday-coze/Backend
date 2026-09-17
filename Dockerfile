# =========================
# 1. Build Stage
# =========================
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar --no-daemon -x test


# =========================
# 2. Runtime Stage
# =========================
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app app

COPY --chown=app:app --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER app

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]