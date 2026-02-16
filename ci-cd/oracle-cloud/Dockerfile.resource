# Dockerfile for YAWL Resource Service
# Oracle Cloud Infrastructure compatible container image
# Version: 1.0.0

# Build stage
FROM eclipse-temurin:25-jdk-jammy AS builder

LABEL maintainer="YAWL Foundation"
LABEL description="YAWL Resource Service - Build Stage"
LABEL version="5.2"

# Install Ant
ARG ANT_VERSION=1.10.14
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && curl -fsSL https://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz | tar xz -C /opt \
    && ln -s /opt/apache-ant-${ANT_VERSION} /opt/ant \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV ANT_HOME=/opt/ant
ENV PATH="${ANT_HOME}/bin:${PATH}"

WORKDIR /build

# Copy source code
COPY . .

# Build WAR files
RUN ant -f build/build.xml buildWebApps

# Runtime stage
FROM eclipse-temurin:25-jre-jammy

LABEL maintainer="YAWL Foundation"
LABEL description="YAWL Resource Service - Runtime"
LABEL version="5.2"
LABEL org.opencontainers.image.source="https://github.com/yawlfoundation/yawl"

# Install required packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    fontconfig \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r yawl && useradd -r -g yawl yawl

# Install Tomcat
ARG TOMCAT_VERSION=9.0.85
ARG TOMCAT_MAJOR=9

RUN curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-${TOMCAT_MAJOR}/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz | tar xz -C /opt \
    && ln -s /opt/apache-tomcat-${TOMCAT_VERSION} /opt/tomcat \
    && rm -rf /opt/tomcat/webapps/* \
    && chown -R yawl:yawl /opt/tomcat

ENV CATALINA_HOME=/opt/tomcat
ENV PATH="${CATALINA_HOME}/bin:${PATH}"

WORKDIR /app

# Copy WAR files from builder
COPY --from=builder /build/webapps/resourceService.war ${CATALINA_HOME}/webapps/ROOT.war

# Copy shared libraries
COPY --from=builder /build/build/3rdParty/lib/*.jar ${CATALINA_HOME}/lib/

# Create directories for logs and data
RUN mkdir -p ${CATALINA_HOME}/logs ${CATALINA_HOME}/data \
    && chown -R yawl:yawl ${CATALINA_HOME}

# Environment variables for YAWL configuration
ENV YAWL_DATABASE_TYPE=postgres
ENV YAWL_DATABASE_HOST=localhost
ENV YAWL_DATABASE_PORT=5432
ENV YAWL_DATABASE_NAME=yawl
ENV YAWL_DATABASE_USER=yawl
ENV YAWL_DATABASE_PASSWORD=""
ENV YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
ENV YAWL_LOG_LEVEL=INFO
ENV JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseG1GC"

# OCI-specific environment variables
ENV OCI_REGION=""
ENV OCI_COMPARTMENT_ID=""

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8080/resourceService/health || exit 1

# Expose port
EXPOSE 8080

# Switch to non-root user
USER yawl

# Start Tomcat
ENTRYPOINT ["catalina.sh"]
CMD ["run"]
