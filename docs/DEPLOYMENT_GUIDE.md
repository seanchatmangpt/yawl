# YAWL v6.0.0-GA Deployment Guide

**Audience**: DevOps Engineers, SREs | **Updated**: February 2026

---

## Table of Contents

1. [System Requirements](#1-system-requirements)
2. [Docker Deployment](#2-docker-deployment)
3. [Kubernetes Deployment](#3-kubernetes-deployment)
4. [Configuration Management](#4-configuration-management)
5. [Monitoring & Observability](#5-monitoring--observability)
6. [Scaling Guidelines](#6-scaling-guidelines)
7. [Security Hardening](#7-security-hardening)
8. [Disaster Recovery](#8-disaster-recovery)

---

## 1. System Requirements

### Hardware Requirements

| Environment | CPU | RAM | Disk | Network |
|-------------|-----|-----|------|---------|
| **Development** | 4 cores | 8 GB | 20 GB SSD | 100 Mbps |
| **Staging** | 8 cores | 16 GB | 50 GB SSD | 1 Gbps |
| **Production (Small)** | 16 cores | 32 GB | 100 GB SSD | 1 Gbps |
| **Production (Large)** | 32+ cores | 64+ GB | 500 GB SSD | 10 Gbps |

### Software Requirements

| Software | Version | Notes |
|----------|---------|-------|
| **Java** | 25+ | OpenJDK or GraalVM |
| **Maven** | 3.9.11+ | Build tool |
| **Docker** | 24.0+ | Container runtime |
| **Kubernetes** | 1.28+ | Orchestration (optional) |

### Java 25 Features Used

| Feature | Benefit | Required |
|---------|---------|----------|
| Virtual Threads | 1000× concurrency | Yes |
| Structured Concurrency | Parallel GRPO | Yes |
| Scoped Values | Thread context | Yes |
| Compact Object Headers | 25% memory reduction | Optional |

### Operating System Support

| OS | Version | Status |
|----|---------|--------|
| Ubuntu | 22.04 LTS | Full |
| Debian | 12+ | Full |
| RHEL/CentOS | 9+ | Full |
| Amazon Linux | 2023 | Full |
| macOS | 13+ | Development |

---

## 2. Docker Deployment

### Quick Start

```bash
# Build image
docker build -t yawl:6.0.0-ga .

# Run container
docker run -d \
  --name yawl-engine \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xmx4g -XX:+UseCompactObjectHeaders" \
  yawl:6.0.0-ga
```

### Dockerfile Example

```dockerfile
FROM eclipse-temurin:25-jdk-alpine

# Install dependencies
RUN apk add --no-cache curl

# Create app user
RUN addgroup -S yawl && adduser -S yawl -G yawl

# Copy application
COPY --chown=yawl:yawl target/yawl-engine.jar /app/
COPY --chown=yawl:yawl config/ /app/config/

WORKDIR /app
USER yawl

# Java 25 optimizations
ENV JAVA_OPTS="-Xmx4g -XX:+UseCompactObjectHeaders -XX:+UseZGC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar yawl-engine.jar"]
```

### Docker Compose

```yaml
version: '3.8'

services:
  yawl-engine:
    image: yawl:6.0.0-ga
    ports:
      - "8080:8080"
    environment:
      - JAVA_OPTS=-Xmx4g -XX:+UseCompactObjectHeaders
      - YAWL_DB_URL=jdbc:postgresql://postgres:5432/yawl
      - YAWL_DB_USER=yawl
      - YAWL_DB_PASSWORD=${DB_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          memory: 6G
        reservations:
          memory: 4G

  postgres:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=yawl
      - POSTGRES_USER=yawl
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U yawl"]
      interval: 10s
      timeout: 5s
      retries: 5

  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]

volumes:
  postgres-data:
  ollama-data:
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xmx2g` | JVM options |
| `YAWL_DB_URL` | (required) | Database JDBC URL |
| `YAWL_DB_USER` | (required) | Database username |
| `YAWL_DB_PASSWORD` | (required) | Database password |
| `YAWL_ENGINE_MODE` | `stateful` | Engine mode |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |

---

## 3. Kubernetes Deployment

### Namespace and ConfigMap

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: yawl
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  YAWL_ENGINE_MODE: "stateful"
  OLLAMA_BASE_URL: "http://ollama:11434"
```

### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: yawl-secrets
  namespace: yawl
type: Opaque
stringData:
  YAWL_DB_URL: "jdbc:postgresql://postgres:5432/yawl"
  YAWL_DB_USER: "yawl"
  YAWL_DB_PASSWORD: "your-secure-password"
```

### Deployment

```yaml
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
        prometheus.io/port: "8080"
        prometheus.io/path: "/metrics"
    spec:
      containers:
      - name: yawl-engine
        image: yawl:6.0.0-ga
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: yawl-config
        - secretRef:
            name: yawl-secrets
        env:
        - name: JAVA_OPTS
          value: "-Xmx4g -XX:+UseCompactObjectHeaders -XX:+UseZGC"
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "6Gi"
            cpu: "4"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        volumeMounts:
        - name: config
          mountPath: /app/config
      volumes:
      - name: config
        configMap:
          name: yawl-engine-config
```

### Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine-hpa
  namespace: yawl
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Service and Ingress

```yaml
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  selector:
    app: yawl-engine
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: yawl-ingress
  namespace: yawl
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - yawl.yourcompany.com
    secretName: yawl-tls
  rules:
  - host: yawl.yourcompany.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: yawl-engine
            port:
              number: 80
```

---

## 4. Configuration Management

### Configuration Files

| File | Purpose |
|------|---------|
| `config/yawl-engine.xml` | Engine configuration |
| `config/yawl-datasource.xml` | Database connection |
| `config/yawl-security.xml` | Authentication/authorization |
| `config/rl-config.toml` | GRPO engine settings |

### rl-config.toml Example

```toml
[grpo]
k = 4
stage = "VALIDITY_GAP"
max_validations = 3
timeout_secs = 60

[llm]
provider = "ollama"
base_url = "http://localhost:11434"
model = "qwen2.5-coder"

[memory]
enabled = true
max_patterns = 10000
eviction_policy = "lru"

[logging]
level = "INFO"
format = "json"
output = "/var/log/yawl/engine.log"
```

### Database Configuration

```xml
<!-- config/yawl-datasource.xml -->
<datasource>
    <name>YawlDataSource</name>
    <driver>org.postgresql.Driver</driver>
    <url>${YAWL_DB_URL}</url>
    <username>${YAWL_DB_USER}</username>
    <password>${YAWL_DB_PASSWORD}</password>

    <pool>
        <minSize>5</minSize>
        <maxSize>50</maxSize>
        <acquireTimeout>30s</acquireTimeout>
        <idleTimeout>600s</idleTimeout>
    </pool>
</datasource>
```

---

## 5. Monitoring & Observability

### Metrics Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/health` | Liveness probe |
| `/health/ready` | Readiness probe |
| `/metrics` | Prometheus metrics |
| `/actuator/prometheus` | Spring Boot metrics |

### Key Metrics to Monitor

| Metric | Alert Threshold |
|--------|-----------------|
| `yawl_cases_active` | > 100,000 |
| `yawl_case_duration_p99` | > 5s |
| `yawl_engine_memory_used` | > 80% |
| `jvm_threads_virtual` | > 100,000 |
| `grpo_generation_latency` | > 10s |

### Prometheus Rules

```yaml
groups:
- name: yawl-alerts
  rules:
  - alert: YawlHighMemory
    expr: yawl_engine_memory_used_ratio > 0.8
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "YAWL engine memory usage high"

  - alert: YawlCaseBacklog
    expr: yawl_cases_pending > 10000
    for: 10m
    labels:
      severity: critical
    annotations:
      summary: "YAWL case backlog growing"

  - alert: YawlGrpoSlow
    expr: grpo_generation_latency_seconds > 10
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "GRPO generation is slow"
```

### Grafana Dashboard

Import the YAWL dashboard from `monitoring/grafana-dashboard.json` for:
- Case throughput over time
- Virtual thread utilization
- GRPO generation latency
- Memory breakdown by component

---

## 6. Scaling Guidelines

### Vertical Scaling

| Metric | Action |
|--------|--------|
| CPU > 70% | Add 2 cores |
| Memory > 80% | Add 4 GB |
| Case latency > 2s | Add resources or scale horizontally |

### Horizontal Scaling

```bash
# Scale Kubernetes deployment
kubectl scale deployment yawl-engine --replicas=5 -n yawl

# Or let HPA handle it automatically
kubectl get hpa -n yawl
```

### Scaling Factors

| Factor | Impact | Recommendation |
|--------|--------|----------------|
| Concurrent cases | Linear | Add replicas |
| GRPO usage | High CPU | Dedicated GRPO pool |
| Large workflows | Memory | Vertical scaling |
| Database I/O | Connection pool | Tune pool size |

### Capacity Planning

| Users | Concurrent Cases | Recommended Setup |
|-------|------------------|-------------------|
| 100 | 1,000 | 2 replicas, 4GB each |
| 1,000 | 10,000 | 5 replicas, 8GB each |
| 10,000 | 100,000 | 10 replicas, 16GB each |
| 100,000 | 1,000,000 | 20+ replicas, 32GB each |

---

## 7. Security Hardening

### Network Security

```yaml
# Network Policy
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
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
```

### Authentication

| Method | Use Case |
|--------|----------|
| mTLS | Service-to-service |
| OAuth2/OIDC | User authentication |
| API Keys | Machine-to-machine |

### RBAC Configuration

```xml
<!-- config/yawl-security.xml -->
<security>
    <authentication>
        <method>oauth2</method>
        <issuer>https://auth.yourcompany.com</issuer>
        <audience>yawl-api</audience>
    </authentication>

    <authorization>
        <role-mapping>
            <role name="yawl-admin" groups="yawl-admins"/>
            <role name="yawl-user" groups="yawl-users"/>
        </role-mapping>
    </authorization>
</security>
```

### Secrets Management

```bash
# Use external secrets operator
kubectl apply -f - <<EOF
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-db-secret
  namespace: yawl
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: yawl-secrets
  data:
  - secretKey: YAWL_DB_PASSWORD
    remoteRef:
      key: yawl/database
      property: password
EOF
```

---

## 8. Disaster Recovery

### Backup Strategy

| Component | Frequency | Retention |
|-----------|-----------|-----------|
| Database | Hourly | 30 days |
| Workflow specs | Daily | 90 days |
| Configuration | On change | 1 year |

### Backup Script

```bash
#!/bin/bash
# scripts/backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/$DATE"

mkdir -p "$BACKUP_DIR"

# Database backup
pg_dump -h postgres -U yawl yawl > "$BACKUP_DIR/database.sql"

# Workflow specs
kubectl exec -n yawl deployment/yawl-engine -- \
  tar czf - /app/specs > "$BACKUP_DIR/specs.tar.gz"

# Configuration
kubectl get configmap,secret -n yawl -o yaml > "$BACKUP_DIR/k8s-config.yaml"

# Upload to S3
aws s3 sync "$BACKUP_DIR" s3://yawl-backups/$DATE/
```

### Recovery Procedure

```bash
#!/bin/bash
# scripts/restore.sh

BACKUP_DATE=$1
BACKUP_DIR="/backups/$BACKUP_DATE"

# Restore database
psql -h postgres -U yawl yawl < "$BACKUP_DIR/database.sql"

# Restore specs
kubectl exec -n yawl deployment/yawl-engine -- \
  tar xzf - -C /app < "$BACKUP_DIR/specs.tar.gz"

# Restart engine
kubectl rollout restart deployment/yawl-engine -n yawl
```

### High Availability

| Component | HA Strategy |
|-----------|-------------|
| Engine pods | 3+ replicas |
| Database | PostgreSQL streaming replication |
| Ollama | Horizontal scaling with shared volume |
| Ingress | Multiple replicas + load balancer |

### RTO/RPO Targets

| Metric | Target | Strategy |
|--------|--------|----------|
| RTO (Recovery Time) | < 15 minutes | Automated failover |
| RPO (Recovery Point) | < 1 hour | Hourly backups |

---

## Related Documentation

- [GA Release Guide](./GA_RELEASE_GUIDE.md) — What's new in v6.0.0-GA
- [Migration Guide](./MIGRATION_GUIDE.md) — v5→v6 migration
- [RL User Guide](./RL_USER_GUIDE.md) — GRPO usage

---

*Last Updated: February 26, 2026*
*Version: YAWL v6.0.0-GA*
