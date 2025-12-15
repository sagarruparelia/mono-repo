# =============================================================================
# BFF Dockerfile - Spring Boot 3.5.0 + Java 21
# Multi-stage build with security best practices
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY apps/bff/.mvn/ .mvn/
COPY apps/bff/mvnw apps/bff/pom.xml ./

# Make Maven wrapper executable and download dependencies
RUN chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -B -q

# Copy source code
COPY apps/bff/src/ src/

# Build the application (skip tests - they should run in CI before Docker build)
RUN ./mvnw package -DskipTests -B -q && \
    # Extract layers for better caching
    mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Labels for container metadata
LABEL org.opencontainers.image.title="BFF Service" \
      org.opencontainers.image.description="Backend for Frontend - Spring Boot WebFlux" \
      org.opencontainers.image.vendor="mono-repo" \
      org.opencontainers.image.source="https://github.com/org/mono-repo"

# Install security updates and required packages
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
        curl \
        dumb-init && \
    rm -rf /var/cache/apk/*

# Create non-root user for security
RUN addgroup -g 1000 -S spring && \
    adduser -u 1000 -S spring -G spring

WORKDIR /app

# Copy extracted layers from builder (better Docker layer caching)
COPY --from=builder /app/target/extracted/dependencies/ ./
COPY --from=builder /app/target/extracted/spring-boot-loader/ ./
COPY --from=builder /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/target/extracted/application/ ./

# Set ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check - uses Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health/liveness || exit 1

# JVM settings optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start the application using Spring Boot's layered jar launcher
CMD ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
