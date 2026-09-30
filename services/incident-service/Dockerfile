# ==============================================================================
# Multi-stage Docker build for incident-service
# ==============================================================================
# Stage 1: Build the application with Maven
# Stage 2: Run with a minimal JRE image
#
# WHY MULTI-STAGE?
# - Build stage has Maven + JDK (large, ~500MB)
# - Run stage only has JRE (small, ~100MB)
# - Final image is lean and secure (no build tools in production)

# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first — Docker caches this layer
# If dependencies don't change, Docker skips re-downloading them
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: Run ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create a non-root user for security
# Never run containers as root in production
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Document the port (doesn't actually expose it — that's done in docker-compose)
EXPOSE 8081

# Health check — Docker/orchestrator can verify the container is healthy
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8081/api/v1/health || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
