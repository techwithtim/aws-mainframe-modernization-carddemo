# ============================================================================
# AWS CardDemo Modernized - Multi-Stage Docker Build
# ============================================================================
# Description: Production-ready containerized build for Java 21 Spring Boot
#              application migrated from COBOL mainframe system
# Base Images: Eclipse Temurin 21 (official OpenJDK distribution)
# Architecture: Multi-stage build (Build + Runtime) for optimized image size
# Target Size: ~200MB runtime image (Alpine-based JRE)
# ============================================================================

# ============================================================================
# Stage 1: Build Stage
# ============================================================================
# Purpose: Compile Java 21 source code and package Spring Boot application
# Base: Maven 3.9 with Eclipse Temurin 21 JDK (full development environment)
# Output: carddemo-modernized-*.jar executable JAR file
# ============================================================================

FROM maven:3.9-eclipse-temurin-21 AS build

# Set working directory for build
WORKDIR /app

# Copy Maven project descriptor first (enables Docker layer caching)
# Maven dependencies are cached if pom.xml hasn't changed
COPY pom.xml .

# Download dependencies in separate layer for better caching
# This step is cached unless pom.xml changes
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build the application
# -DskipTests: Skip tests in Docker build (tests run in CI/CD pipeline)
# -B: Batch mode (non-interactive, cleaner logs)
# clean: Remove previous build artifacts
# package: Compile, test, and package into JAR
RUN mvn clean package -DskipTests -B

# Verify JAR was created successfully
RUN ls -lh /app/target/*.jar

# ============================================================================
# Stage 2: Runtime Stage
# ============================================================================
# Purpose: Minimal production runtime environment for Spring Boot application
# Base: Eclipse Temurin 21 JRE Alpine (minimal JRE, ~200MB total)
# Security: Non-root user, minimal attack surface
# Health: Configured health check endpoint
# ============================================================================

FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for health checks (required by Docker HEALTHCHECK)
# Alpine package manager: apk
RUN apk add --no-cache curl

# Create non-root user for security best practices
# PCI-DSS compliance: Applications should not run as root
# useradd equivalent in Alpine: adduser
RUN addgroup -S spring && \
    adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy JAR from build stage (matches pattern carddemo-*.jar)
# Rename to standard app.jar for consistent entrypoint
COPY --from=build /app/target/carddemo-*.jar app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose application port (Spring Boot default: 8080)
EXPOSE 8080

# Configure health check
# Endpoint: /actuator/health (Spring Boot Actuator)
# Interval: Check every 30 seconds
# Timeout: Wait 3 seconds for response
# Start period: Wait 40 seconds before first check (app startup time)
# Retries: Mark unhealthy after 3 consecutive failures
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Configure JVM options for containerized environment
# Java 21+ automatically detects container memory limits (cgroup awareness)
# Additional optimizations:
# - UseContainerSupport: Enable container-aware JVM settings
# - MaxRAMPercentage: Use up to 80% of container memory for heap
# - InitialRAMPercentage: Start with 50% of container memory
# - MinRAMPercentage: Minimum heap for small containers
# - UseG1GC: G1 garbage collector (optimal for server applications)
# - MaxGCPauseMillis: Target max GC pause time (200ms SLA)
# - UseStringDeduplication: Reduce memory for duplicate strings
# - ExitOnOutOfMemoryError: Exit on OOM for container restart

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=80.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:MinRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Set Spring Boot profile from environment variable (default: prod)
# Override at runtime: docker run -e SPRING_PROFILES_ACTIVE=dev
ENV SPRING_PROFILES_ACTIVE=prod

# Application entrypoint
# Uses shell form to enable JAVA_OPTS expansion
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar

# ============================================================================
# Build & Run Instructions
# ============================================================================
# Build image:
#   docker build -t carddemo-modernized:latest .
#
# Run container:
#   docker run -d -p 8080:8080 \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     -e DB_HOST=postgres \
#     -e DB_NAME=carddemo \
#     --name carddemo \
#     carddemo-modernized:latest
#
# Health check:
#   curl http://localhost:8080/actuator/health
#
# View logs:
#   docker logs -f carddemo
#
# Stop container:
#   docker stop carddemo
# ============================================================================

# ============================================================================
# Security & Compliance Notes
# ============================================================================
# - Non-root user (spring:spring) for PCI-DSS compliance
# - Minimal Alpine base image reduces attack surface
# - No sensitive data in image (credentials via environment variables)
# - Health check enables container orchestration (Kubernetes liveness probe)
# - JVM tuned for container resource limits
# - Automatic restart on OutOfMemoryError
# ============================================================================

# ============================================================================
# Image Size Optimization
# ============================================================================
# - Multi-stage build separates build dependencies from runtime
# - Alpine-based JRE (~40MB) vs full JDK (~400MB)
# - Only JAR file copied to runtime stage
# - Expected runtime image size: ~200MB (JRE + JAR + minimal OS)
# ============================================================================
