# YAWL v6.0.0-Alpha Docker Guide

**Target Platforms**: Docker 24.0+, Docker Compose 2.x, Kubernetes 1.28+
**Base Images**: eclipse-temurin:25-jre-alpine (runtime), eclipse-temurin:25-jdk-alpine (builder)
**Java Version**: JDK/JRE 25 with Virtual Threads (standard, no preview flags)
**Estimated Time**: 15-30 minutes

---

## Table of Contents

1. [Quick Start Guide](#1-quick-start-guide)
2. [Configuration Options](#2-configuration-options)
3. [Production Deployment Guide](#3-production-deployment-guide)
4. [Development Setup](#4-development-setup)
5. [Troubleshooting Common Issues](#5-troubleshooting-common-issues)
6. [Upgrade from Previous Versions](#6-upgrade-from-previous-versions)
7. [Security Best Practices](#7-security-best-practices)
8. [Performance Tuning](#8-performance-tuning)

---

## 1. Quick Start Guide

### 1.1 Prerequisites

- Docker 24.0+ or Docker Desktop 4.25+
- Docker Compose 2.x (included with Docker Desktop)
- 8GB RAM minimum (16GB recommended for production)
- 20GB disk space
- Linux, macOS, or Windows with WSL2

### 1.2 Verify Prerequisites

```bash
# Check Docker version
docker --version
# Expected: Docker version 24.0.0 or later

# Check Docker Compose
docker compose version
# Expected: Docker Compose version v2.x

# Check available resources
docker info | grep -E "Memory|CPUs"
# Recommended: Memory >= 8GB, CPUs >= 4

# Verify disk space
df -h /var/lib/docker  # Linux
# Or on macOS: docker system df
```

### 1.3 Pull and Run (Fastest)

```bash
# Pull the latest image (when published)
docker pull yawlfoundation/yawl:6.0.0-alpha

# Run with H2 in-memory database (for testing only)
docker run -d \
  --name yawl-engine \
  -p 8080:8080 \
  -p 9090:9090 \
  -e SPRING_PROFILES_ACTIVE=development \
  -e DB_TYPE=h2 \
  yawlfoundation/yawl:6.0.0-alpha

# Check health
docker logs -f yawl-engine

# Access the engine
curl http://localhost:8080/actuator/health
```

### 1.4 Build from Source

```bash
# Clone the repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Build the Docker image
docker build \
  -f Dockerfile.modernized \
  -t yawl:6.0.0-alpha \
  --build-arg VERSION=6.0.0-Alpha \
  --build-arg BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ") \
  --build-arg VCS_REF=$(git rev-parse --short HEAD) \
  .

# Run the built image
docker run -d \
  --name yawl-engine \
  -p 8080:8080 \
  -p 9090:9090 \
  yawl:6.0.0-alpha
```

### 1.5 Docker Compose Quick Start

Create `docker-compose.yml`:

```yaml
version: '3.9'

services:
  # PostgreSQL Database (Production)
  postgres:
    image: postgres:16-alpine
    container_name: yawl-db
    environment:
      POSTGRES_DB: yawl
      POSTGRES_USER: yawl
      POSTGRES_PASSWORD: ${DB_PASSWORD:-yawl_secure_password}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U yawl -d yawl"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - yawl-network

  # YAWL Engine
  yawl-engine:
    image: yawl:6.0.0-alpha
    build:
      context: .
      dockerfile: Dockerfile.modernized
    container_name: yawl-engine
    environment:
      SPRING_PROFILES_ACTIVE: production
      DB_TYPE: postgres
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: yawl
      DB_USER: yawl
      DB_PASSWORD: ${DB_PASSWORD:-yawl_secure_password}
      JAVA_OPTS: >-
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:+UseZGC
        -XX:+ZGenerational
    ports:
      - "8080:8080"
      - "9090:9090"
    volumes:
      - yawl_logs:/app/logs
      - yawl_data:/app/data
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "/app/healthcheck.sh"]
      interval: 30s
      timeout: 15s
      start_period: 120s
      retries: 3
    networks:
      - yawl-network
    restart: unless-stopped

volumes:
  postgres_data:
  yawl_logs:
  yawl_data:

networks:
  yawl-network:
    driver: bridge
```

Start the stack:

```bash
# Start services
docker compose up -d

# View logs
docker compose logs -f yawl-engine

# Check status
docker compose ps

# Stop services
docker compose down
```

---

## 2. Configuration Options

### 2.1 Environment Variables

#### Core Application Settings

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `production` | Spring profile (development, staging, production) |
| `MAIN_PORT` | `8080` | Main application port |
| `TZ` | `UTC` | Timezone |

#### Database Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_TYPE` | `postgres` | Database type (postgres, mysql, h2, derby, hsqldb) |
| `DB_HOST` | `postgres` | Database hostname |
| `DB_PORT` | `5432` | Database port |
| `DB_NAME` | `yawl` | Database name |
| `DB_USER` | `yawl` | Database username |
| `DB_PASSWORD` | (required) | Database password |

#### JVM Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | (see below) | JVM options string |
| `HEALTH_LOG` | `/app/logs/healthcheck.log` | Health check log path |

Default `JAVA_OPTS`:

```bash
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:InitialRAMPercentage=50.0
-XX:+UseZGC
-XX:+ZGenerational
-XX:+UseStringDeduplication
-XX:+ExitOnOutOfMemoryError
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/heap-dump.hprof
-Djava.security.egd=file:/dev/./urandom
-Djava.io.tmpdir=/app/temp
-Dfile.encoding=UTF-8
-Djdk.virtualThreadScheduler.parallelism=200
-Djdk.virtualThreadScheduler.maxPoolSize=256
-Djdk.tracePinnedThreads=short
```

#### Logging Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level |
| `LOGGING_LEVEL_ORG_YAWLFOUNDATION` | `DEBUG` | YAWL-specific logging level |

#### Management and Actuator

| Variable | Default | Description |
|----------|---------|-------------|
| `MANAGEMENT_HEALTH_PROBES_ENABLED` | `true` | Enable health probes |
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `when-authorized` | Health endpoint detail level |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info,metrics,prometheus` | Exposed actuator endpoints |

#### OpenTelemetry (Optional)

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_SERVICE_NAME` | `yawl-engine` | Service name for telemetry |
| `OTEL_TRACES_EXPORTER` | `otlp` | Traces exporter type |
| `OTEL_METRICS_EXPORTER` | `prometheus` | Metrics exporter type |
| `OTEL_LOGS_EXPORTER` | `otlp` | Logs exporter type |

### 2.2 Volume Mounts

| Container Path | Purpose |
|----------------|---------|
| `/app/specifications` | Workflow specification files |
| `/app/logs` | Application logs |
| `/app/data` | Persistent data storage |
| `/app/temp` | Temporary files |
| `/app/config` | Custom configuration files |

### 2.3 Exposed Ports

| Port | Service | Description |
|------|---------|-------------|
| `8080` | Main Application | YAWL Engine API |
| `9090` | Management | Actuator/Prometheus metrics |
| `8081` | Resource Service | Resource management (optional) |

### 2.4 Configuration Files

Mount custom configuration files:

```bash
docker run -d \
  --name yawl-engine \
  -v /path/to/application.yml:/app/config/application.yml:ro \
  -v /path/to/log4j2.xml:/app/config/log4j2.xml:ro \
  yawl:6.0.0-alpha
```

Example `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:${DB_PORT:5432}/${DB_NAME:yawl}
    username: ${DB_USER:yawl}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

yawl:
  engine:
    max-workers: 10
    session-timeout: 3600000
  workitem:
    default-priority: 5
```

---

## 3. Production Deployment Guide

### 3.1 Pre-Deployment Checklist

- [ ] Docker 24.0+ installed on all nodes
- [ ] Database server configured (PostgreSQL 16+ recommended)
- [ ] SSL/TLS certificates obtained
- [ ] Secrets configured in secrets manager
- [ ] Resource limits calculated
- [ ] Monitoring infrastructure ready
- [ ] Backup strategy defined

### 3.2 Production Docker Compose

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    container_name: yawl-db
    environment:
      POSTGRES_DB: yawl
      POSTGRES_USER: yawl
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
      POSTGRES_INITDB_ARGS: "-c timezone=UTC"
    secrets:
      - db_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/migrations:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U yawl -d yawl"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - yawl-network
    deploy:
      resources:
        limits:
          memory: 4G
        reservations:
          memory: 2G
    restart: always

  yawl-engine:
    image: yawlfoundation/yawl:6.0.0-alpha
    container_name: yawl-engine
    secrets:
      - db_password
      - jwt_secret
    environment:
      SPRING_PROFILES_ACTIVE: production
      DB_TYPE: postgres
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: yawl
      DB_USER: yawl
      DB_PASSWORD_FILE: /run/secrets/db_password
      JAVA_OPTS: >-
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:+UseZGC
        -XX:+ZGenerational
        -XX:+UseStringDeduplication
        -XX:+ExitOnOutOfMemoryError
        -XX:HeapDumpPath=/app/logs/heap-dump.hprof
        -Djdk.virtualThreadScheduler.parallelism=200
        -Djdk.virtualThreadScheduler.maxPoolSize=256
        -Djdk.tracePinnedThreads=short
        -Dyawl.jwt.secret.file=/run/secrets/jwt_secret
      MANAGEMENT_HEALTH_PROBES_ENABLED: "true"
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health,info,metrics,prometheus
      LOGGING_LEVEL_ROOT: WARN
      LOGGING_LEVEL_ORG_YAWLFOUNDATION: INFO
    ports:
      - "8080:8080"
      - "9090:9090"
    volumes:
      - yawl_logs:/app/logs
      - yawl_data:/app/data
      - yawl_specs:/app/specifications
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "/app/healthcheck.sh"]
      interval: 30s
      timeout: 15s
      start_period: 120s
      retries: 3
    networks:
      - yawl-network
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '2'
          memory: 4G
        reservations:
          cpus: '1'
          memory: 2G
      restart_policy:
        condition: on-failure
        delay: 10s
        max_attempts: 3
        window: 120s
    restart: always

  # Optional: Prometheus for metrics
  prometheus:
    image: prom/prometheus:v2.48.0
    container_name: yawl-prometheus
    ports:
      - "9091:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'
    networks:
      - yawl-network
    restart: always

secrets:
  db_password:
    file: ./secrets/db_password.txt
  jwt_secret:
    file: ./secrets/jwt_secret.txt

volumes:
  postgres_data:
  yawl_logs:
  yawl_data:
  yawl_specs:
  prometheus_data:

networks:
  yawl-network:
    driver: bridge
```

### 3.3 Kubernetes Deployment

For Kubernetes deployments, see the complete manifests in the `k8s/` directory. Key resources:

```yaml
# k8s/production/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
  template:
    metadata:
      labels:
        app: yawl-engine
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: yawl-service-account
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
      - name: yawl-engine
        image: yawlfoundation/yawl:6.0.0-alpha
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: metrics
        envFrom:
        - configMapRef:
            name: yawl-config
        env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-secrets
              key: db-password
        resources:
          requests:
            cpu: "500m"
            memory: "2Gi"
          limits:
            cpu: "2000m"
            memory: "4Gi"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 30
          timeoutSeconds: 15
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        volumeMounts:
        - name: yawl-logs
          mountPath: /app/logs
        - name: yawl-data
          mountPath: /app/data
      volumes:
      - name: yawl-logs
        emptyDir: {}
      - name: yawl-data
        persistentVolumeClaim:
          claimName: yawl-data-pvc
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - yawl-engine
              topologyKey: kubernetes.io/hostname
```

### 3.4 Health Check Endpoints

| Endpoint | Purpose | Response |
|----------|---------|----------|
| `/actuator/health` | Overall health | `{"status":"UP"}` |
| `/actuator/health/liveness` | Liveness probe | `{"status":"UP"}` |
| `/actuator/health/readiness` | Readiness probe | `{"status":"UP"}` |
| `/actuator/info` | Application info | Version, build info |
| `/actuator/prometheus` | Prometheus metrics | Prometheus text format |

### 3.5 Database Connection Pool Configuration

For production, tune HikariCP settings:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # Per instance
      minimum-idle: 5              # Maintain 5 idle connections
      connection-timeout: 30000    # 30s to acquire connection
      idle-timeout: 600000         # 10min idle -> close
      max-lifetime: 1800000        # 30min max lifetime
      leak-detection-threshold: 60000  # Log if held > 60s
```

**Formula**: `connections = (core_count * 2) + effective_spindle_count`

---

## 4. Development Setup

### 4.1 Development Docker Compose

```yaml
version: '3.9'

services:
  # H2 Database (Development Only)
  h2:
    image: oscarfonts/h2:latest
    container_name: yawl-h2
    environment:
      H2_OPTIONS: -web -webAllowOthers -tcp -tcpAllowOthers
    ports:
      - "1521:1521"  # TCP
      - "8082:8082"  # Web Console
    volumes:
      - h2_data:/opt/h2-data
    networks:
      - yawl-network

  # YAWL Engine (Development Mode)
  yawl-engine:
    image: yawl:6.0.0-alpha
    build:
      context: .
      dockerfile: Dockerfile.modernized
      target: runtime
    container_name: yawl-engine
    environment:
      SPRING_PROFILES_ACTIVE: development
      DB_TYPE: h2
      DB_HOST: h2
      DB_PORT: 1521
      DB_NAME: yawl
      DB_USER: sa
      DB_PASSWORD: ""
      JAVA_OPTS: >-
        -Xms512m
        -Xmx1g
        -XX:+UseZGC
        -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
      LOGGING_LEVEL_ROOT: DEBUG
      LOGGING_LEVEL_ORG_YAWLFOUNDATION: TRACE
    ports:
      - "8080:8080"
      - "5005:5005"  # Remote debugging
    volumes:
      - ./src:/app/src:ro
      - ./logs:/app/logs
    depends_on:
      - h2
    networks:
      - yawl-network

volumes:
  h2_data:

networks:
  yawl-network:
    driver: bridge
```

### 4.2 Hot Reload Development

For development with hot reload:

```bash
# Build with development target
docker build \
  -f Dockerfile.modernized \
  --target builder \
  -t yawl:dev-builder \
  .

# Run with volume mounts for source changes
docker run -it --rm \
  -v $(pwd)/src:/build/src \
  -v $(pwd)/test:/build/test \
  -p 8080:8080 \
  yawl:dev-builder \
  mvn spring-boot:run -DskipTests
```

### 4.3 Remote Debugging

Connect your IDE to the remote debugger on port 5005:

```bash
# Start with debug port exposed
docker run -d \
  --name yawl-debug \
  -p 8080:8080 \
  -p 5005:5005 \
  -e JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  yawl:6.0.0-alpha

# Connect from IDE:
# Host: localhost
# Port: 5005
```

### 4.4 Running Tests in Container

```bash
# Run all tests
docker run --rm \
  -v $(pwd):/build \
  -w /build \
  maven:3.9-eclipse-temurin-25 \
  mvn clean test

# Run specific test class
docker run --rm \
  -v $(pwd):/build \
  -w /build \
  maven:3.9-eclipse-temurin-25 \
  mvn test -Dtest=YEngineTest
```

---

## 5. Troubleshooting Common Issues

### 5.1 Container Won't Start

**Symptoms**: Container exits immediately or keeps restarting.

**Diagnosis**:

```bash
# Check container logs
docker logs yawl-engine

# Check exit code
docker inspect yawl-engine --format='{{.State.ExitCode}}'

# Run interactively for debugging
docker run -it --rm \
  --entrypoint /bin/sh \
  yawl:6.0.0-alpha
```

**Common Causes**:

1. **Database Connection Failure**
   ```bash
   # Verify database is accessible
   docker exec yawl-engine nc -zv $DB_HOST $DB_PORT

   # Check credentials
   docker exec yawl-engine psql -h $DB_HOST -U $DB_USER -d $DB_NAME
   ```

2. **Memory Issues**
   ```bash
   # Check memory limits
   docker stats yawl-engine

   # Increase memory
   docker update --memory 4g yawl-engine
   ```

3. **Port Conflicts**
   ```bash
   # Check what's using the port
   lsof -i :8080
   netstat -tuln | grep 8080
   ```

### 5.2 Health Check Failures

**Symptoms**: Container marked unhealthy.

**Diagnosis**:

```bash
# Check health status
docker inspect --format='{{json .State.Health}}' yawl-engine | jq

# Run health check manually
docker exec yawl-engine /app/healthcheck.sh
echo $?  # 0 = healthy, 1 = unhealthy

# Check health check logs
docker exec yawl-engine cat /app/logs/healthcheck.log
```

**Solutions**:

1. Increase start period for slow networks:
   ```yaml
   healthcheck:
     start_period: 180s  # Increase from 120s
   ```

2. Check application startup time:
   ```bash
   docker logs yawl-engine 2>&1 | grep -E "Started|Error"
   ```

### 5.3 Database Connection Issues

**Symptoms**: "Connection refused" or timeout errors.

**Diagnosis**:

```bash
# Test network connectivity
docker exec yawl-engine ping -c 3 $DB_HOST

# Test port connectivity
docker exec yawl-engine nc -zv $DB_HOST $DB_PORT

# Check DNS resolution
docker exec yawl-engine nslookup $DB_HOST

# View connection pool status (if actuator enabled)
curl http://localhost:8080/actuator/health/db
```

**Solutions**:

1. Ensure containers are on same network:
   ```bash
   docker network inspect yawl-network
   ```

2. Use service names (not localhost) in Docker Compose:
   ```yaml
   environment:
     DB_HOST: postgres  # Service name, not localhost
   ```

3. Wait for database to be ready:
   ```yaml
   depends_on:
     postgres:
       condition: service_healthy
   ```

### 5.4 OutOfMemoryError

**Symptoms**: Container crashes with OOM or Java heap space error.

**Diagnosis**:

```bash
# Check heap dump (if generated)
docker exec yawl-engine ls -la /app/logs/heap-dump.hprof

# Copy heap dump for analysis
docker cp yawl-engine:/app/logs/heap-dump.hprof ./heap-dump.hprof

# Analyze with jhat or VisualVM
jhat heap-dump.hprof
```

**Solutions**:

1. Increase container memory:
   ```yaml
   deploy:
     resources:
       limits:
         memory: 4G
   ```

2. Adjust JVM heap settings:
   ```yaml
   environment:
     JAVA_OPTS: >-
       -XX:MaxRAMPercentage=75.0
       -XX:InitialRAMPercentage=50.0
   ```

3. Enable GC logging for analysis:
   ```yaml
   environment:
     JAVA_OPTS: >-
       -Xlog:gc*:file=/app/logs/gc.log:time,uptime:filecount=5,filesize=10m
   ```

### 5.5 Slow Startup

**Symptoms**: Container takes too long to start.

**Diagnosis**:

```bash
# Time the startup
time docker run --rm yawl:6.0.0-alpha

# Check startup logs
docker logs yawl-engine 2>&1 | grep -E "Started|seconds"
```

**Solutions**:

1. Use Class Data Sharing (CDS):
   ```dockerfile
   # In Dockerfile
   RUN java -Xshare:dump
   ```

2. Reduce classpath scanning:
   ```yaml
   environment:
     SPRING_MAIN_LAZY_INITIALIZATION: "true"
   ```

3. Increase start period in health check:
   ```yaml
   healthcheck:
     start_period: 180s
   ```

### 5.6 Log Collection Issues

**Symptoms**: Logs not appearing or truncated.

**Diagnosis**:

```bash
# Check container logs
docker logs yawl-engine --tail=100

# Check log files in container
docker exec yawl-engine ls -la /app/logs/

# Check log file permissions
docker exec yawl-engine stat /app/logs/
```

**Solutions**:

1. Ensure proper permissions:
   ```dockerfile
   RUN chown -R yawl:yawl /app/logs
   ```

2. Configure log rotation:
   ```yaml
   environment:
     LOGGING_FILE_MAX_SIZE: 100MB
     LOGGING_FILE_MAX_HISTORY: 10
   ```

---

## 6. Upgrade from Previous Versions

### 6.1 Upgrade from YAWL v5.x

**Prerequisites**:
- Backup existing database
- Export workflow specifications
- Review breaking changes

**Step 1: Backup**:

```bash
# Backup PostgreSQL database
docker exec yawl-db pg_dump -U yawl yawl > yawl_backup_$(date +%Y%m%d).sql

# Backup specifications
docker cp yawl-engine:/app/specifications ./specs_backup/

# Backup configuration
docker cp yawl-engine:/app/config ./config_backup/
```

**Step 2: Stop Old Version**:

```bash
# Stop containers gracefully
docker compose down

# Verify all containers stopped
docker ps -a | grep yawl
```

**Step 3: Update Configuration**:

Key configuration changes from v5.x to v6.0:

| v5.x | v6.0 | Notes |
|------|------|-------|
| `CATALINA_OPTS` | `JAVA_OPTS` | Now using Spring Boot, not Tomcat |
| `yawl.db.url` | `DB_HOST`, `DB_PORT`, `DB_NAME` | Split into separate variables |
| Tomcat 10.x | Spring Boot 3.5 | Embedded server |
| Java 21 | Java 25 | Virtual Threads now standard |

**Step 4: Pull New Image**:

```bash
# Pull new version
docker pull yawlfoundation/yawl:6.0.0-alpha

# Or build from source
git checkout v6.0.0-alpha
docker build -f Dockerfile.modernized -t yawl:6.0.0-alpha .
```

**Step 5: Run Database Migrations**:

```bash
# Run migrations (if using Flyway)
docker run --rm \
  --network yawl-network \
  -v $(pwd)/database/migrations:/flyway/sql \
  flyway/flyway:10 \
  -url=jdbc:postgresql://postgres:5432/yawl \
  -user=yawl \
  -password=${DB_PASSWORD} \
  migrate
```

**Step 6: Start New Version**:

```bash
# Start with new image
docker compose up -d

# Monitor startup
docker compose logs -f yawl-engine
```

**Step 7: Verify Upgrade**:

```bash
# Check version
curl http://localhost:8080/actuator/info | jq .version

# Verify database connectivity
curl http://localhost:8080/actuator/health/db

# Test workflow execution
curl http://localhost:8080/api/ib/workitems
```

### 6.2 Rollback Procedure

If upgrade fails:

```bash
# 1. Stop new version
docker compose down

# 2. Restore database
docker exec -i yawl-db psql -U yawl yawl < yawl_backup_YYYYMMDD.sql

# 3. Start old version
docker compose -f docker-compose.v5.yml up -d

# 4. Verify rollback
curl http://localhost:8080/yawl/api/ib/workitems
```

---

## 7. Security Best Practices

### 7.1 Container Security

**Non-Root User**:

The container runs as non-root user `yawl` (UID 1000) by default:

```dockerfile
USER yawl
```

**Read-Only Root Filesystem** (recommended for production):

```yaml
securityContext:
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
volumes:
  - name: tmp
    emptyDir: {}
  - name: logs
    emptyDir: {}
volumeMounts:
  - name: tmp
    mountPath: /app/temp
  - name: logs
    mountPath: /app/logs
```

**Drop Capabilities**:

```yaml
securityContext:
  capabilities:
    drop:
      - ALL
    add:
      - NET_BIND_SERVICE
```

### 7.2 Secrets Management

**Never hardcode secrets** in Docker images or compose files.

**Option 1: Docker Secrets** (Swarm mode):

```yaml
secrets:
  db_password:
    external: true
services:
  yawl-engine:
    secrets:
      - db_password
    environment:
      DB_PASSWORD_FILE: /run/secrets/db_password
```

**Option 2: Kubernetes Secrets**:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: yawl-secrets
type: Opaque
stringData:
  db-password: "your-secure-password"
  jwt-secret: "your-jwt-secret"
```

**Option 3: External Secrets Operator**:

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: ClusterSecretStore
  target:
    name: yawl-secrets
  data:
    - secretKey: db-password
      remoteRef:
        key: yawl/production/database
        property: password
```

### 7.3 Network Security

**Network Isolation**:

```yaml
networks:
  yawl-network:
    driver: bridge
    internal: true  # No external access
  yawl-frontend:
    driver: bridge  # External access
```

**Kubernetes NetworkPolicy**:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-network-policy
  namespace: yawl
spec:
  podSelector:
    matchLabels:
      app: yawl-engine
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - port: 8080
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - port: 5432
```

### 7.4 Image Security

**Scan for Vulnerabilities**:

```bash
# Using Trivy
trivy image yawl:6.0.0-alpha

# Using Docker Scout
docker scout cves yawl:6.0.0-alpha

# Using Snyk
snyk container test yawl:6.0.0-alpha
```

**Sign Images**:

```bash
# Sign with Cosign
cosign sign --key cosign.key yawlfoundation/yawl:6.0.0-alpha

# Verify signature
cosign verify --key cosign.pub yawlfoundation/yawl:6.0.0-alpha
```

**Use Specific Digests**:

```yaml
image: yawlfoundation/yawl@sha256:abc123...
```

### 7.5 TLS/SSL Configuration

**Enable HTTPS**:

```yaml
# Using nginx reverse proxy
services:
  nginx:
    image: nginx:alpine
    ports:
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    depends_on:
      - yawl-engine
```

**nginx.conf**:

```nginx
server {
    listen 443 ssl;
    server_name yawl.example.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    location / {
        proxy_pass http://yawl-engine:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 8. Performance Tuning

### 8.1 JVM Tuning for Containers

**Recommended Settings for Different Memory Sizes**:

| Container Memory | MaxRAMPercentage | Approximate Heap |
|------------------|------------------|------------------|
| 2 GB | 75% | 1.5 GB |
| 4 GB | 75% | 3 GB |
| 8 GB | 70% | 5.6 GB |
| 16 GB | 65% | 10.4 GB |

**Optimized JAVA_OPTS**:

```bash
JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+UseStringDeduplication \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heap-dump.hprof \
  -XX:MaxGCPauseMillis=100 \
  -XX:GCTimeRatio=4 \
  -Djava.security.egd=file:/dev/./urandom \
  -Djava.io.tmpdir=/app/temp \
  -Dfile.encoding=UTF-8 \
  -Djdk.virtualThreadScheduler.parallelism=200 \
  -Djdk.virtualThreadScheduler.maxPoolSize=256 \
  -Djdk.tracePinnedThreads=short \
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime:filecount=5,filesize=10m"
```

### 8.2 Virtual Threads Optimization

YAWL v6.0 uses Java 25 Virtual Threads for high concurrency:

```bash
# Virtual thread scheduler settings
-Djdk.virtualThreadScheduler.parallelism=200    # Carrier threads
-Djdk.virtualThreadScheduler.maxPoolSize=256    # Max pool size
-Djdk.tracePinnedThreads=short                  # Detect pinning issues
```

**Monitor virtual thread performance**:

```bash
# JFR recording for virtual threads
docker exec yawl-engine jcmd 1 JFR.start \
  settings=profile.jfc \
  filename=/app/logs/virtual-threads.jfr
```

### 8.3 Database Connection Pool Sizing

**Calculate Optimal Pool Size**:

```
connections = (core_count * 2) + effective_spindle_count

Example for 8-core server with SSD:
connections = (8 * 2) + 1 = 17 (use 20)
```

**Production HikariCP Settings**:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      # Performance optimizations
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useLocalSessionState: true
        rewriteBatchedStatements: true
        cacheResultSetMetadata: true
        cacheServerConfiguration: true
        elideSetAutoCommits: true
        maintainTimeStats: false
```

### 8.4 Container Resource Limits

**Kubernetes Resource Configuration**:

```yaml
resources:
  requests:
    cpu: "1000m"      # 1 CPU core guaranteed
    memory: "2Gi"     # 2 GB memory guaranteed
  limits:
    cpu: "4000m"      # 4 CPU cores burst
    memory: "4Gi"     # 4 GB memory limit
```

**Docker Compose Resource Limits**:

```yaml
deploy:
  resources:
    limits:
      cpus: '4'
      memory: 4G
    reservations:
      cpus: '1'
      memory: 2G
```

### 8.5 Performance Monitoring

**Key Metrics to Monitor**:

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| CPU Usage | < 70% | > 85% |
| Memory Usage | < 75% | > 90% |
| GC Pause Time | < 100ms | > 500ms |
| Request Latency (p95) | < 500ms | > 1s |
| Error Rate | < 0.1% | > 1% |
| Connection Pool Usage | < 80% | > 95% |

**Prometheus Queries**:

```promql
# Request latency (p95)
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket[5m])
)

# Connection pool usage
hikaricp_connections_active / hikaricp_connections_max

# GC pause time
rate(jvm_gc_pause_seconds_sum[5m])

# Memory usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

### 8.6 Benchmarking

**Load Testing with k6**:

```javascript
// k6-script.js
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up
    { duration: '5m', target: 100 },  // Steady state
    { duration: '2m', target: 200 },  // Spike
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% < 500ms
  },
};

export default function() {
  let res = http.get('http://yawl-engine:8080/api/ib/workitems');
  check(res, {
    'status is 200': (r) => r.status == 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}
```

```bash
# Run load test
k6 run k6-script.js
```

---

## Appendix A: Quick Reference Commands

```bash
# Build image
docker build -f Dockerfile.modernized -t yawl:6.0.0-alpha .

# Run container
docker run -d -p 8080:8080 --name yawl-engine yawl:6.0.0-alpha

# View logs
docker logs -f yawl-engine

# Execute command in container
docker exec -it yawl-engine /bin/sh

# Check health
docker inspect --format='{{.State.Health.Status}}' yawl-engine

# Resource usage
docker stats yawl-engine

# Copy files from container
docker cp yawl-engine:/app/logs ./logs

# Clean up
docker compose down -v  # Remove containers and volumes
docker system prune -a  # Remove unused resources
```

---

## Appendix B: Health Check Script Details

The container includes a multi-strategy health check script at `/app/healthcheck.sh`:

1. **Strategy 1**: HTTP health endpoint (most reliable)
2. **Strategy 2**: TCP port check (fallback)
3. **Strategy 3**: Process check (last resort)

```bash
# Run health check manually
docker exec yawl-engine /app/healthcheck.sh

# View health check logs
docker exec yawl-engine cat /app/logs/healthcheck.log
```

---

## Appendix C: OCI Image Labels

The image includes OCI standard labels:

```bash
# View image labels
docker inspect --format='{{json .Config.Labels}}' yawl:6.0.0-alpha | jq

# Example output:
{
  "org.opencontainers.image.title": "YAWL Workflow Engine",
  "org.opencontainers.image.version": "6.0.0-Alpha",
  "org.opencontainers.image.description": "YAWL v6.0.0-Alpha - Yet Another Workflow Language...",
  "org.opencontainers.image.source": "https://github.com/yawlfoundation/yawl",
  "org.opencontainers.image.vendor": "YAWL Foundation",
  "org.opencontainers.image.licenses": "LGPL-3.0-or-later",
  "org.yawlfoundation.yawl.java-version": "25",
  "org.yawlfoundation.yawl.features": "virtual-threads,zgc,petri-net,mcp,a2a"
}
```

---

## Support

For Docker-related issues:
- Check container logs: `docker logs <container>`
- Review Docker documentation: https://docs.docker.com/
- Check health status: `docker inspect <container>`

For YAWL issues:
- Check application logs within container: `docker exec <container> cat /app/logs/yawl.log`
- Review YAWL documentation: https://yawlfoundation.org
- GitHub Issues: https://github.com/yawlfoundation/yawl/issues
