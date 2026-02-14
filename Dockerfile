# Multi-stage build for YAWL Workflow Engine
FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /app

# Copy build configuration
COPY build/ ./build/
COPY src/ ./src/
COPY pom.xml ./

# Build YAWL with Ant
RUN apt-get update && apt-get install -y ant && \
    cd build && \
    ant -f build.xml all

# Runtime stage
FROM tomcat:9.0-jdk11-eclipse-temurin

# Install PostgreSQL client for health checks
RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*

# Copy built WAR files
COPY --from=builder /app/build/output/*.war /usr/local/tomcat/webapps/

# Copy Tomcat configuration
COPY docker/tomcat-users.xml /usr/local/tomcat/conf/
COPY docker/context.xml /usr/local/tomcat/conf/
COPY docker/server.xml /usr/local/tomcat/conf/

# Set environment variables
ENV YAWL_DB_HOST=cloudsql-proxy \
    YAWL_DB_PORT=5432 \
    YAWL_DB_NAME=yawl \
    YAWL_DB_USER=postgres \
    TOMCAT_HEAP_SIZE=1024m

# Copy startup script
COPY docker/startup.sh /usr/local/tomcat/bin/
RUN chmod +x /usr/local/tomcat/bin/startup.sh

# Expose ports
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/resourceService/ || exit 1

# Run Tomcat
CMD ["/usr/local/tomcat/bin/startup.sh"]
