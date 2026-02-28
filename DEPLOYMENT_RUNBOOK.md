# YAWL v6.0.0 Production Deployment Runbook

**Version**: 6.0.0-GA  
**Last Updated**: February 2026  
**Target Audience**: DevOps engineers, system administrators  
**Estimated Time**: 2–4 hours for complete deployment

---

## Table of Contents

1. [Pre-Deployment Checklist](#pre-deployment-checklist)
2. [Architecture Overview](#architecture-overview)
3. [Docker Deployment](#docker-deployment)
4. [Kubernetes Deployment](#kubernetes-deployment)
5. [Database Setup](#database-setup)
6. [JVM Configuration](#jvm-configuration)
7. [Security Hardening](#security-hardening)
8. [Monitoring & Observability](#monitoring--observability)
9. [Post-Deployment Validation](#post-deployment-validation)
10. [Rollback Procedures](#rollback-procedures)

---

## Pre-Deployment Checklist

Before beginning deployment, verify:

```bash
# Check system requirements
cat <<'CHECK' > /tmp/pre-check.sh
#!/bin/bash
set -e

echo "YAWL v6.0.0 Pre-Deployment Validation"
echo "======================================"
echo ""

# Java 25 check
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not installed"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | grep "25\." || echo "NOT FOUND")
if [ "$JAVA_VERSION" = "NOT FOUND" ]; then
    echo "ERROR: Java 25+ required. Found: $(java -version 2>&1)"
    exit 1
fi
echo "✓ Java 25+ installed: $(java -version 2>&1 | head -1)"

# Docker check (if using Docker)
if [ -z "$SKIP_DOCKER_CHECK" ]; then
    if command -v docker &> /dev/null; then
        DOCKER_VERSION=$(docker --version)
        echo "✓ Docker installed: $DOCKER_VERSION"
    else
        echo "⚠ Docker not found (required for containerized deployment)"
    fi
fi

# PostgreSQL connectivity (if external DB)
if [ ! -z "$DB_HOST" ]; then
    if ! nc -z $DB_HOST $DB_PORT 2>/dev/null; then
        echo "ERROR: Cannot reach database at $DB_HOST:$DB_PORT"
        exit 1
    fi
    echo "✓ Database connectivity verified: $DB_HOST:$DB_PORT"
fi

# Disk space check (minimum 10GB for engine + data)
DISK_USAGE=$(df /home | awk 'NR==2 {print $4}')
if [ "$DISK_USAGE" -lt 10485760 ]; then
    echo "ERROR: Insufficient disk space (require 10GB free, have ${DISK_USAGE}KB)"
    exit 1
fi
echo "✓ Disk space verified: ${DISK_USAGE}KB available"

# Memory check (minimum 8GB for JVM)
MEM_AVAILABLE=$(free -b | awk 'NR==2 {print $7}')
MEM_GB=$((MEM_AVAILABLE / 1024 / 1024 / 1024))
if [ "$MEM_GB" -lt 8 ]; then
    echo "WARNING: Less than 8GB available ($MEM_GB GB) - minimum recommended"
fi
echo "✓ System memory: ${MEM_GB}GB available"

# Port availability checks
for PORT in 8080 8081 9090; do
    if netstat -tuln 2>/dev/null | grep -q ":$PORT "; then
        echo "WARNING: Port $PORT already in use"
    else
        echo "✓ Port $PORT available"
    fi
done

echo ""
echo "Pre-deployment validation passed!"
CHECK

chmod +x /tmp/pre-check.sh
/tmp/pre-check.sh
```

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                    YAWL v6.0.0 Architecture                   │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐   │
│  │  YEngine    │  │  Stateless   │  │  MCP/A2A Servers  │   │
│  │ (Stateful)  │  │  YStateless  │  │   (Agents)        │   │
│  └─────┬───────┘  └──────┬───────┘  └─────────┬─────────┘   │
│        │                  │                    │             │
│        └──────────────────┼────────────────────┘             │
│                           │                                  │
│               ┌───────────▼────────────────┐                │
│               │   WorkflowEventStore      │                │
│               │ (Event log + telemetry)   │                │
│               └───────────┬────────────────┘                │
│                           │                                  │
│        ┌──────────────────┼──────────────────┐              │
│        │                  │                  │              │
│  ┌─────▼────────┐  ┌──────▼──────┐  ┌──────▼────────┐     │
│  │  Predictive  │  │Prescriptive │  │Optimization  │     │
│  │     (PI)     │  │    (PI)     │  │     (PI)      │     │
│  └──────────────┘  └─────────────┘  └───────────────┘     │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  PostgreSQL / H2 Database (configurable)              │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

**Key Components**:
- **YEngine**: Stateful workflow execution, case management
- **YStatelessEngine**: Stateless case operations (cloud-native)
- **MCP/A2A Servers**: AI agent integration endpoints
- **WorkflowEventStore**: Event sourcing infrastructure
- **PI Stack**: Predictive, prescriptive, optimization capabilities

---

## Docker Deployment

### 1. Build Docker Image

```bash
# Clone or navigate to repository
cd /home/user/yawl

# Build YAWL engine image
docker build \
  --tag yawl-engine:6.0.0 \
  --file docker/production/Dockerfile.engine \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  --build-arg VCS_REF=$(git rev-parse --short HEAD) \
  .

# Verify image
docker image inspect yawl-engine:6.0.0

# Tag for registry (if pushing to private registry)
docker tag yawl-engine:6.0.0 myregistry.azurecr.io/yawl-engine:6.0.0
docker push myregistry.azurecr.io/yawl-engine:6.0.0
```

### 2. Run Container

```bash
# Create environment file
cat > /etc/yawl/prod.env << 'ENV'
# Application
SPRING_PROFILES_ACTIVE=production
SPRING_APPLICATION_NAME=yawl-engine
YAWL_VERSION=6.0.0

# Database (use PostgreSQL for production)
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.com:5432/yawl_prod
SPRING_DATASOURCE_USERNAME=yawl_user
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}  # Use secrets manager
SPRING_JPA_HIBERNATE_DDL_AUTO=validate

# Connection pool tuning
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=10
SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=30000

# JVM options
JAVA_OPTS=-Xms4g -Xmx8g -XX:+UseContainerSupport -XX:+UseShenandoahGC

# Security
SECURITY_JWT_SECRET=${JWT_SECRET}  # Use secrets manager
SERVER_SSL_KEY_STORE=/etc/yawl/keystore.jks
SERVER_SSL_KEY_STORE_PASSWORD=${KEYSTORE_PASSWORD}

# MCP/A2A integration
MCP_ENABLED=true
MCP_PORT=8081
A2A_ENABLED=true
A2A_PORT=9091

# Monitoring
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus
MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=true
ENV

# Run container with production settings
docker run -d \
  --name yawl-engine-prod \
  --restart=always \
  --env-file /etc/yawl/prod.env \
  --publish 8080:8080 \
  --publish 8081:8081 \
  --publish 9090:9090 \
  --volume /var/lib/yawl/data:/app/data \
  --volume /var/lib/yawl/logs:/app/logs \
  --volume /var/lib/yawl/specifications:/app/specifications \
  --volume /etc/yawl/keystore.jks:/etc/yawl/keystore.jks:ro \
  --log-driver json-file \
  --log-opt max-size=10m \
  --log-opt max-file=10 \
  --health-cmd="curl -sf http://localhost:8080/actuator/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-start-period=60s \
  --health-retries=3 \
  yawl-engine:6.0.0

# Verify container is running
docker ps | grep yawl-engine-prod
docker logs -f yawl-engine-prod
```

### 3. Docker Compose (for local/staging)

```bash
cat > docker-compose.prod.yml << 'COMPOSE'
version: '3.9'

services:
  yawl-engine:
    image: yawl-engine:6.0.0
    container_name: yawl-engine-prod
    restart: always
    ports:
      - "8080:8080"
      - "8081:8081"
      - "9090:9090"
    environment:
      SPRING_PROFILES_ACTIVE: production
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/yawl_prod
      SPRING_DATASOURCE_USERNAME: yawl_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
    volumes:
      - yawl_data:/app/data
      - yawl_logs:/app/logs
      - /etc/yawl/keystore.jks:/etc/yawl/keystore.jks:ro
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  postgres:
    image: postgres:16-alpine
    container_name: yawl-postgres
    environment:
      POSTGRES_DB: yawl_prod
      POSTGRES_USER: yawl_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U yawl_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  prometheus:
    image: prom/prometheus:latest
    container_name: yawl-prometheus
    ports:
      - "9091:9090"
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'

volumes:
  yawl_data:
  yawl_logs:
  postgres_data:
  prometheus_data:
COMPOSE

docker-compose -f docker-compose.prod.yml up -d
docker-compose -f docker-compose.prod.yml logs -f yawl-engine
```

---

## Kubernetes Deployment

### 1. Create Namespace & Secrets

```bash
# Create namespace
kubectl create namespace yawl-prod

# Create database secret
kubectl create secret generic yawl-db-credentials \
  --from-literal=username=yawl_user \
  --from-literal=password=$(openssl rand -base64 32) \
  -n yawl-prod

# Create TLS secret
kubectl create secret tls yawl-tls \
  --cert=path/to/tls.crt \
  --key=path/to/tls.key \
  -n yawl-prod

# Create JWT secret
kubectl create secret generic yawl-jwt \
  --from-literal=secret=$(openssl rand -base64 32) \
  -n yawl-prod
```

### 2. ConfigMap for Application Settings

```bash
cat > /tmp/yawl-configmap.yaml << 'CONFIGEOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl-prod
data:
  application.yml: |
    server:
      port: 8080
      ssl:
        enabled: true
        key-store-type: PKCS12
    spring:
      datasource:
        hikari:
          maximum-pool-size: 30
          minimum-idle: 10
          connection-timeout: 30000
      jpa:
        hibernate:
          ddl-auto: validate
    management:
      endpoints:
        web:
          exposure:
            include: health,metrics,prometheus
      metrics:
        export:
          prometheus:
            enabled: true
CONFIGEOF

kubectl apply -f /tmp/yawl-configmap.yaml
```

### 3. Stateful Engine Deployment

```bash
cat > /tmp/yawl-engine-deployment.yaml << 'DEPLOYEOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl-prod
  labels:
    app: yawl-engine
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
      serviceAccountName: yawl-engine
      containers:
      - name: yawl-engine
        image: myregistry.azurecr.io/yawl-engine:6.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: 8080
        - name: mcp
          containerPort: 8081
        - name: metrics
          containerPort: 9090
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production,kubernetes"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres-service:5432/yawl_prod"
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: yawl-db-credentials
              key: username
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-db-credentials
              key: password
        - name: JAVA_OPTS
          value: "-Xms4g -Xmx8g -XX:+UseContainerSupport -XX:+UseShenandoahGC -XX:MaxRAMPercentage=75.0"
        volumeMounts:
        - name: yawl-data
          mountPath: /app/data
        - name: yawl-logs
          mountPath: /app/logs
        resources:
          requests:
            memory: "4Gi"
            cpu: "2000m"
          limits:
            memory: "8Gi"
            cpu: "4000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: http
            scheme: HTTPS
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: http
            scheme: HTTPS
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          runAsNonRoot: true
          runAsUser: 1000
          capabilities:
            drop:
            - ALL
      volumes:
      - name: yawl-data
        persistentVolumeClaim:
          claimName: yawl-data-pvc
      - name: yawl-logs
        persistentVolumeClaim:
          claimName: yawl-logs-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine-service
  namespace: yawl-prod
spec:
  selector:
    app: yawl-engine
  ports:
  - name: http
    port: 8080
    targetPort: 8080
  - name: mcp
    port: 8081
    targetPort: 8081
  - name: metrics
    port: 9090
    targetPort: 9090
  type: LoadBalancer
DEPLOYEOF

kubectl apply -f /tmp/yawl-engine-deployment.yaml
```

### 4. StatefulSet for Persistent State (Optional)

```bash
cat > /tmp/yawl-statefulset.yaml << 'STATEFULEOF'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: yawl-engine-stateful
  namespace: yawl-prod
spec:
  serviceName: yawl-engine-stateful
  replicas: 1
  selector:
    matchLabels:
      app: yawl-engine-stateful
  template:
    metadata:
      labels:
        app: yawl-engine-stateful
    spec:
      containers:
      - name: yawl-engine
        image: myregistry.azurecr.io/yawl-engine:6.0.0
        ports:
        - containerPort: 8080
          name: http
        volumeMounts:
        - name: yawl-state
          mountPath: /app/data
  volumeClaimTemplates:
  - metadata:
      name: yawl-state
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 100Gi
STATEFULEOF

kubectl apply -f /tmp/yawl-statefulset.yaml
```

### 5. Verify K8s Deployment

```bash
# Check all resources
kubectl get all -n yawl-prod

# Check pod status
kubectl get pods -n yawl-prod -o wide
kubectl describe pod <pod-name> -n yawl-prod

# View logs
kubectl logs -f deployment/yawl-engine -n yawl-prod

# Check service
kubectl get svc -n yawl-prod
kubectl port-forward svc/yawl-engine-service 8080:8080 -n yawl-prod
```

---

## Database Setup

### 1. PostgreSQL Setup (Production Recommended)

```bash
# Create PostgreSQL database and user
psql -U postgres << 'PSQLEOF'
-- Create user with strong password
CREATE USER yawl_user WITH ENCRYPTED PASSWORD 'REPLACE_WITH_STRONG_PASSWORD';

-- Create database
CREATE DATABASE yawl_prod OWNER yawl_user;

-- Grant privileges
GRANT CONNECT ON DATABASE yawl_prod TO yawl_user;
GRANT USAGE ON SCHEMA public TO yawl_user;
GRANT CREATE ON SCHEMA public TO yawl_user;

-- Create extensions needed by YAWL
\c yawl_prod
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS uuid-ossp;

-- Set work_mem for large queries
ALTER USER yawl_user SET work_mem TO '256MB';
PSQLEOF

# Enable SSL connections (recommended)
cat >> /etc/postgresql/16/main/postgresql.conf << 'CONFEOF'
ssl = on
ssl_cert_file = '/etc/postgresql/server.crt'
ssl_key_file = '/etc/postgresql/server.key'
CONFEOF

# Restart PostgreSQL
systemctl restart postgresql
```

### 2. Database Schema Initialization

```bash
# Using Spring Boot auto-migration (recommended)
# Set ddl-auto: validate or migrate based on version

# Or manual migration with Flyway:
cat > /tmp/flyway.conf << 'FLYWAYEOF'
flyway.locations=classpath:db/migration
flyway.baselineOnMigrate=true
flyway.out_of_order=false
FLYWAYEOF

# Database tuning for production
psql -U yawl_user -d yawl_prod << 'TUNEEOF'
-- Connection pooling settings
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '4GB';
ALTER SYSTEM SET effective_cache_size = '12GB';
ALTER SYSTEM SET maintenance_work_mem = '1GB';
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;

-- Query optimization
ALTER SYSTEM SET work_mem = '256MB';
ALTER SYSTEM SET max_wal_size = '2GB';

-- Performance monitoring
ALTER SYSTEM SET log_statement = 'mod';
ALTER SYSTEM SET log_duration = 'on';
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- Reload config
SELECT pg_reload_conf();
TUNEEOF
```

### 3. Database Backup Strategy

```bash
# Daily backup script
cat > /usr/local/bin/backup-yawl-db.sh << 'BACKUPEOF'
#!/bin/bash
set -e

BACKUP_DIR="/var/backups/yawl"
RETENTION_DAYS=30
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DB_NAME="yawl_prod"

mkdir -p $BACKUP_DIR

# Full backup
pg_dump -U yawl_user \
    --host=localhost \
    --format=custom \
    --file=$BACKUP_DIR/yawl_${TIMESTAMP}.dump \
    $DB_NAME

# Compress
gzip $BACKUP_DIR/yawl_${TIMESTAMP}.dump

# Cleanup old backups
find $BACKUP_DIR -name "yawl_*.dump.gz" -mtime +$RETENTION_DAYS -delete

echo "Backup completed: $BACKUP_DIR/yawl_${TIMESTAMP}.dump.gz"
BACKUPEOF

chmod +x /usr/local/bin/backup-yawl-db.sh

# Add to crontab (daily at 2 AM)
echo "0 2 * * * /usr/local/bin/backup-yawl-db.sh" | crontab -
```

---

## JVM Configuration

### 1. JVM Flags (Java 25 Optimized)

```bash
# Minimal configuration (for 4GB heap)
export JAVA_OPTS="
  -Xms4g
  -Xmx8g
  -XX:+UseContainerSupport
  -XX:+UseShenandoahGC
  -XX:ShenandoahGCHeuristics=compact
  -XX:+UseCompactObjectHeaders
  -XX:+UseStringDeduplication
  -XX:+ExitOnOutOfMemoryError
  -XX:HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/app/logs/heap-dump.hprof
  -XX:MaxGCPauseMillis=50
  -XX:MaxRAMPercentage=75.0
  -Djava.security.egd=file:/dev/./urandom
  -Dfile.encoding=UTF-8
  -Djdk.virtualThreadScheduler.parallelism=200
"

# High-performance configuration (8GB+ heap)
export JAVA_OPTS="
  -Xms8g
  -Xmx16g
  -XX:+UseContainerSupport
  -XX:+UseZGC
  -XX:ZUncommitDelay=300
  -XX:+UseCompactObjectHeaders
  -XX:+UseStringDeduplication
  -XX:+ExitOnOutOfMemoryError
  -XX:HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/app/logs/heap-dump.hprof
  -XX:GCTimeRatio=9
  -Xlog:gc*:file=/app/logs/gc.log:uptime:level,tag
  -Dfile.encoding=UTF-8
  -Djdk.virtualThreadScheduler.parallelism=400
  -Djdk.virtualThreadScheduler.maxPoolSize=512
"

# Low-latency configuration (MCP/A2A priority)
export JAVA_OPTS="
  -Xms4g
  -Xmx8g
  -XX:+UseContainerSupport
  -XX:+UseShenandoahGC
  -XX:ShenandoahGCHeuristics=aggressive
  -XX:MaxGCPauseMillis=10
  -XX:+UseCompactObjectHeaders
  -Dfile.encoding=UTF-8
  -Djdk.virtualThreadScheduler.parallelism=400
"
```

### 2. Monitoring JVM Performance

```bash
# Enable GC logging
export JAVA_OPTS="$JAVA_OPTS -Xlog:gc*:file=/app/logs/gc.log:uptime:level,tag"

# Monitor with jstat (while running)
jstat -gc -h 10 $(pgrep -f yawl-engine) 1000  # Every 1 second

# Analyze heap dumps
jhsdb jmap --binaryheap --pid $(pgrep -f yawl-engine)
jhat heap-dump.hprof

# Monitor thread pools
jcmd $(pgrep -f yawl-engine) Thread.print > threads.txt
```

---

## Security Hardening

### 1. TLS/SSL Configuration

```bash
# Generate self-signed certificate (for testing)
keytool -genkey -alias yawl \
    -keyalg RSA \
    -keysize 2048 \
    -keystore /etc/yawl/keystore.jks \
    -storepass $(openssl rand -base64 32) \
    -keypass $(openssl rand -base64 32) \
    -validity 365

# Production: Use CA-signed certificate
# Copy certificates to keystore
keytool -import -alias yawl-ca \
    -file /path/to/ca-certificate.crt \
    -keystore /etc/yawl/keystore.jks \
    -storepass ${KEYSTORE_PASSWORD}

# Configure in application
cat > config/application.yml << 'TLSEOF'
server:
  ssl:
    enabled: true
    key-store: file:///etc/yawl/keystore.jks
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: yawl
    protocol: TLSv1.3
    ciphers: TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_AES_128_GCM_SHA256
TLSEOF
```

### 2. Authentication & Authorization

```bash
# Set up JWT authentication
cat > config/security.yml << 'SECEOF'
security:
  jwt:
    enabled: true
    secret: ${JWT_SECRET}  # Minimum 32 characters
    expiration: 3600  # 1 hour
    refresh-expiration: 604800  # 7 days
  cors:
    allowed-origins: https://console.yawl.example.com
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: Content-Type,Authorization
    allow-credentials: true
SECEOF

# Create admin user (first time)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "REPLACE_WITH_STRONG_PASSWORD",
    "email": "admin@yawl.example.com",
    "roles": ["ADMIN"]
  }'
```

### 3. Network Security (Firewall Rules)

```bash
# UFW firewall rules (if using UFW)
ufw allow 22/tcp  # SSH
ufw allow 8080/tcp  # YAWL HTTP
ufw allow 8081/tcp  # MCP server
ufw allow 5432/tcp  # PostgreSQL (internal only)
ufw enable

# iptables rules (alternative)
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
iptables -A INPUT -p tcp --dport 8081 -j ACCEPT
iptables -A INPUT -p tcp --dport 22 -j ACCEPT
iptables -A INPUT -j DROP  # Default deny
iptables-save > /etc/iptables/rules.v4
```

---

## Monitoring & Observability

### 1. Prometheus Metrics Scraping

```bash
cat > config/prometheus.yml << 'PROMEOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'yawl-engine'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:9090']
    scrape_interval: 10s

  - job_name: 'postgres'
    static_configs:
      - targets: ['localhost:9187']

  - job_name: 'node'
    static_configs:
      - targets: ['localhost:9100']
PROMEOF

# Start Prometheus
prometheus --config.file=config/prometheus.yml --storage.tsdb.path=/var/lib/prometheus
```

### 2. Grafana Dashboard Setup

```bash
# Create dashboard
curl -X POST http://localhost:3000/api/dashboards/db \
  -H "Authorization: Bearer ${GRAFANA_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @grafana/dashboards/yawl-engine.json
```

### 3. Log Aggregation (ELK Stack)

```yaml
# Filebeat configuration
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /app/logs/yawl.log
    fields:
      service: yawl-engine
      environment: production

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  username: elastic
  password: ${ELASTIC_PASSWORD}
```

---

## Post-Deployment Validation

```bash
# 1. Health check
curl -v https://localhost:8080/actuator/health

# 2. Readiness check
curl https://localhost:8080/actuator/health/readiness

# 3. Metrics validation
curl https://localhost:9090/actuator/prometheus | head -20

# 4. Database connectivity
curl https://localhost:8080/api/v1/diagnostics/database

# 5. MCP server availability
curl https://localhost:8081/mcp/capabilities

# 6. Verify case creation latency <500ms
time curl -X POST https://localhost:8080/api/v1/cases \
  -H "Content-Type: application/json" \
  -d '{"specification": "order-fulfillment"}'

# 7. Check GC metrics
curl https://localhost:9090/actuator/metrics/jvm.gc.pause | jq '.measurements'

# 8. Validate event store
curl https://localhost:8080/api/v1/diagnostics/event-store

# Full validation script
cat > /usr/local/bin/validate-yawl.sh << 'VALIDEOF'
#!/bin/bash
set -e

YAWL_URL="${1:-https://localhost:8080}"
FAILED=0

echo "YAWL v6.0.0 Deployment Validation"
echo "===================================="

echo -n "Health check... "
if curl -sf $YAWL_URL/actuator/health > /dev/null; then
    echo "✓"
else
    echo "✗ FAILED"
    ((FAILED++))
fi

echo -n "Database connectivity... "
if curl -sf $YAWL_URL/api/v1/diagnostics/database > /dev/null; then
    echo "✓"
else
    echo "✗ FAILED"
    ((FAILED++))
fi

echo -n "Metrics available... "
if curl -sf $YAWL_URL/actuator/prometheus | grep -q "jvm_"; then
    echo "✓"
else
    echo "✗ FAILED"
    ((FAILED++))
fi

echo ""
if [ $FAILED -eq 0 ]; then
    echo "All validation checks passed!"
    exit 0
else
    echo "$FAILED validation checks failed!"
    exit 1
fi
VALIDEOF

chmod +x /usr/local/bin/validate-yawl.sh
/usr/local/bin/validate-yawl.sh
```

---

## Rollback Procedures

### 1. Docker Rollback

```bash
# Stop current container
docker stop yawl-engine-prod

# Restart previous version
docker run -d \
  --name yawl-engine-previous \
  --env-file /etc/yawl/prod.env \
  --publish 8080:8080 \
  yawl-engine:6.0.0-previous

# Or use docker-compose
docker-compose -f docker-compose.prod.yml down
# Edit docker-compose.prod.yml to use previous image
docker-compose -f docker-compose.prod.yml up -d
```

### 2. Kubernetes Rollback

```bash
# Check rollout history
kubectl rollout history deployment/yawl-engine -n yawl-prod

# Rollback to previous version
kubectl rollout undo deployment/yawl-engine -n yawl-prod

# Rollback to specific revision
kubectl rollout undo deployment/yawl-engine --to-revision=2 -n yawl-prod

# Watch rollout progress
kubectl rollout status deployment/yawl-engine -n yawl-prod -w
```

### 3. Database Rollback

```bash
# Restore from backup
pg_restore --clean --no-privileges \
    --host=localhost \
    --username=yawl_user \
    --dbname=yawl_prod \
    /var/backups/yawl/yawl_20260228_150000.dump.gz
```

---

## Troubleshooting Reference

See TROUBLESHOOTING_GUIDE.md for:
- High GC pause times
- Database bottlenecks
- Lock contention
- Memory growth / leak detection
- Throughput degradation

---

**Deployment Complete!** Proceed to PRODUCTION_CHECKLIST.md for post-deployment validation.

