FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY . .

# Build the application
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="YAWL Team <support@yawl.org>"
LABEL description="YAWL - Yet Another Workflow Language"
LABEL version="1.0.0"

# Install required packages
RUN apk add --no-cache \
    bash \
    curl \
    && rm -rf /var/cache/apk/*

# Create application user
RUN addgroup -g 1001 yawl && \
    adduser -D -u 1001 -G yawl yawl

# Set working directory
WORKDIR /home/yawl

# Copy built application from builder
COPY --from=builder /app/target/yawl-*.jar /app/yawl.jar

# Set ownership
RUN chown -R yawl:yawl /home/yawl /app

# Switch to non-root user
USER yawl

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose ports
EXPOSE 8080 8443 9090

# Environment variables
ENV JAVA_OPTS="-Xmx1024m -Xms256m -XX:+UseG1GC"
ENV YAWL_HOME=/home/yawl

# Run application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/yawl.jar"]
