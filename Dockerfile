# YAWL v5.2 - Cloud-Native Container Image
# Production-ready image with Spring Boot Actuator for Kubernetes/Cloud Run

FROM eclipse-temurin:25-jre-alpine

LABEL maintainer="YAWL Foundation"
LABEL version="5.2"
LABEL description="YAWL Workflow Engine with Spring Boot Actuator for cloud-native deployments"

# Install required packages
RUN apk add --no-cache \
    bash \
    curl \
    postgresql-client \
    && rm -rf /var/cache/apk/*

# Create application user for security
RUN addgroup -S yawl && adduser -S yawl -G yawl

# Set working directory
WORKDIR /app

# Copy application JAR
COPY target/yawl-5.2.jar /app/yawl.jar
COPY src/main/resources/application.yml /app/config/application.yml

# Create directories for workflow specifications and logs
RUN mkdir -p /app/specifications /app/logs /app/data \
    && chown -R yawl:yawl /app

# Switch to non-root user
USER yawl

# Health check using actuator liveness endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Expose ports
# 8080 - Main application port
# 9090 - Actuator management port (optional separate port)
EXPOSE 8080 9090

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Application configuration
ENV SPRING_PROFILES_ACTIVE=production
ENV MANAGEMENT_HEALTH_PROBES_ENABLED=true
ENV MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always

# Start application with actuator endpoints
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/yawl.jar"]
