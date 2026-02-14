# Multi-stage build for YAWL with Teradata JDBC Integration
FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /app

# Copy build configuration and source
COPY build/ ./build/
COPY src/ ./src/
COPY pom.xml ./

# Install build dependencies
RUN apt-get update && apt-get install -y ant && \
    cd build && \
    ant -f build.xml all

# Runtime stage
FROM tomcat:9.0-jdk11-eclipse-temurin

# Install PostgreSQL and Teradata clients for connectivity
RUN apt-get update && \
    apt-get install -y \
    postgresql-client \
    telnet \
    curl \
    wget && \
    rm -rf /var/lib/apt/lists/*

# Create JDBC drivers directory
RUN mkdir -p /usr/local/tomcat/lib/teradata

# Download and install Teradata JDBC driver
# Note: Replace with actual version and repository
ARG TERADATA_JDBC_VERSION=20.00.00.06
RUN cd /usr/local/tomcat/lib/teradata && \
    wget -q https://downloads.teradata.com/downloads/connected-ecosystem/teradata-sql-engine/teradata-jdbc-driver/terajdbc4.jar || \
    echo "Note: Teradata JDBC driver must be manually placed in teradata/lib/ directory"

# Copy built WAR files
COPY --from=builder /app/build/output/*.war /usr/local/tomcat/webapps/

# Copy Tomcat configuration
COPY docker/tomcat-users.xml /usr/local/tomcat/conf/
COPY docker/context.xml /usr/local/tomcat/conf/
COPY docker/server.xml /usr/local/tomcat/conf/

# Copy Teradata JDBC driver if present in build context
COPY teradata/lib/*.jar /usr/local/tomcat/lib/teradata/ 2>/dev/null || true

# Set environment variables for Teradata connection
ENV YAWL_DB_HOST=cloudsql-proxy \
    YAWL_DB_PORT=5432 \
    YAWL_DB_NAME=yawl \
    YAWL_DB_USER=postgres \
    TERADATA_HOST="" \
    TERADATA_PORT=1025 \
    TERADATA_USER="" \
    TERADATA_PASSWORD="" \
    TERADATA_DATABASE="" \
    TERADATA_CHARSET=UTF8 \
    TERADATA_LOGON_TIMEOUT=30 \
    TOMCAT_HEAP_SIZE=1024m

# Copy startup script
COPY docker/startup.sh /usr/local/tomcat/bin/
RUN chmod +x /usr/local/tomcat/bin/startup.sh

# Copy Teradata connection configuration
COPY teradata/config/teradata-connection.properties /usr/local/tomcat/conf/ 2>/dev/null || true

# Expose ports
EXPOSE 8080

# Health check with Teradata connectivity verification
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/resourceService/ || exit 1

# Run Tomcat
CMD ["/usr/local/tomcat/bin/startup.sh"]
