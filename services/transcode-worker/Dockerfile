# ── Stage 1: Build the Spring Boot fat-jar ──────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Cache Maven dependencies layer separately from source
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn dependency:go-offline -q

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# ── Stage 2: Runtime — NVIDIA CUDA + FFmpeg + JRE ────────────────────────────
# nvcr.io/nvidia/cuda provides CUDA libs; we layer FFmpeg with h264_nvenc on top.
# Using Ubuntu 22.04 base to match the CUDA driver ABI.
FROM nvcr.io/nvidia/cuda:12.3.1-runtime-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install JRE 21 + FFmpeg (Ubuntu's FFmpeg build includes nvenc when GPU present)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        ffmpeg \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# HLS output directory (must match the Docker volume mount in docker-compose)
RUN mkdir -p /streams
VOLUME ["/streams"]

WORKDIR /app

COPY --from=builder /build/target/transcode-worker-*.jar app.jar

# Health probe: Spring Actuator /health
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8090/actuator/health || exit 1

EXPOSE 8090

ENTRYPOINT ["java", \
  "-Xms128m", "-Xmx512m", \
  "-XX:+UseContainerSupport", \
  "-jar", "/app/app.jar"]
