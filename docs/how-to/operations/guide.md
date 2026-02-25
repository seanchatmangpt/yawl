# YAWL Operations Guide

**Version:** 6.0.0
**Last Updated:** 2026-02-16
**Target Audience:** DevOps, SRE, Production Operations Teams

---

## Table of Contents

1. [Production Deployment](#production-deployment)
2. [Configuration Management](#configuration-management)
3. [Monitoring and Logging](#monitoring-and-logging)
4. [Performance Tuning](#performance-tuning)
5. [Troubleshooting](#troubleshooting)
6. [Backup and Recovery](#backup-and-recovery)
7. [Security Operations](#security-operations)
8. [Scaling and High Availability](#scaling-and-high-availability)

---

## Production Deployment

### Pre-Deployment Checklist

**Must Complete Before Production:**
- [ ] Maven build executed successfully (`mvn clean install -Pprod`)
- [ ] WAR files generated and validated
- [ ] Performance baselines measured (< 60s startup, < 500ms case creation)
- [ ] All tests passing in full environment (102+ tests)
- [ ] Security scan clean (OWASP dependency-check)
- [ ] 2-week staging stability validation
- [ ] Rollback plan tested
- [ ] Team trained on operations
- [ ] Monitoring and alerting configured
- [ ] Backup and recovery procedures documented

### Deployment Options

#### Option 1: Docker Compose (Staging/Small Production)

```bash
# Copy environment template
cp .env.example .env

# Edit environment variables
nano .env

# Required variables:
# - DATABASE_PASSWORD
# - YAWL_JDBC_PASSWORD
# - REDIS_PASSWORD
# - YAWL_ADMIN_PASSWORD

# Start all services
docker-compose --profile production up -d

# Verify deployment
docker-compose ps
curl http://localhost:8888/health
```

**Services Started:**
- **yawl-engine** - Core workflow engine (port 8080)
- **yawl-resource-service** - Resource management (port 9095)
- **yawl-worklet-service** - Dynamic selection (port 9092)
- **postgresql** - Database (port 5432)
- **redis** - Cache and sessions (port 6379)

#### Option 2: Kubernetes (Production)

```bash
# Create namespace
kubectl create namespace yawl

# Create secrets
kubectl create secret generic yawl-db-credentials \
  --from-literal=host=postgres-host \
  --from-literal=port=5432 \
  --from-literal=username=yawl_admin \
  --from-literal=password=$DB_PASSWORD \
  --namespace yawl

kubectl create secret generic yawl-admin-credentials \
  --from-literal=username=admin \
  --from-literal=password=$ADMIN_PASSWORD \
  --namespace yawl

# Deploy YAWL stack
kubectl apply -f k8s/base/

# Verify deployment
kubectl get pods -n yawl
kubectl get svc -n yawl
kubectl get ingress -n yawl

# Check health
kubectl exec -n yawl deploy/yawl-engine -- curl localhost:8080/health
```

#### Option 3: Helm Chart (Enterprise)

```bash
# Add YAWL Helm repository
helm repo add yawl https://helm.yawl.io
helm repo update

# Create values file
cat > values.yaml <<EOF
global:
  environment: production
  imageRegistry: your-registry.io/yawl
  imageTag: "5.2.0"

engine:
  replicaCount: 3
  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 4Gi

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: yawl.yourdomain.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: yawl-tls
      hosts:
        - yawl.yourdomain.com

externalDatabase:
  host: postgres.example.com
  port: 5432
  database: yawl
  existingSecret: yawl-db-credentials
EOF

# Deploy
helm upgrade --install yawl yawl/yawl-stack \
  --namespace yawl \
  --create-namespace \
  --values values.yaml \
  --timeout 10m

# Verify
helm status yawl -n yawl
kubectl get all -n yawl
```

### Cloud-Specific Deployment

#### AWS (EKS + RDS + ElastiCache)

```bash
# Deploy infrastructure with Terraform
cd terraform/aws
terraform init
terraform plan
terraform apply

# Deploy YAWL
kubectl apply -f k8s/aws/

# Configure AWS-specific settings
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-aws-config
  namespace: yawl
data:
  AWS_REGION: us-east-1
  RDS_ENDPOINT: yawl-db.abc123.us-east-1.rds.amazonaws.com
  ELASTICACHE_ENDPOINT: yawl-redis.abc123.0001.use1.cache.amazonaws.com
EOF
```

#### Azure (AKS + PostgreSQL + Redis Cache)

```bash
# Deploy infrastructure
cd terraform/azure
terraform init
terraform apply

# Deploy YAWL
kubectl apply -f k8s/azure/

# Configure managed identity
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl
  annotations:
    azure.workload.identity/client-id: $AZURE_CLIENT_ID
EOF
```

#### GCP (GKE + Cloud SQL + Memorystore)

```bash
# Deploy infrastructure
cd terraform/gcp
terraform init
terraform apply

# Deploy YAWL
kubectl apply -f k8s/gcp/

# Configure workload identity
kubectl annotate serviceaccount yawl-engine \
  iam.gke.io/gcp-service-account=yawl-sa@project.iam.gserviceaccount.com \
  -n yawl
```

---

## Configuration Management

### Environment Variables

**Required Variables:**
```bash
# Engine Configuration
YAWL_ENGINE_URL=http://localhost:8080/yawl/ia
YAWL_USERNAME=admin
YAWL_PASSWORD=<from-vault>

# Database Configuration
DATABASE_URL=jdbc:postgresql://postgres:5432/yawl
DATABASE_USERNAME=yawl_admin
DATABASE_PASSWORD=<from-vault>
YAWL_JDBC_USER=yawl_admin
YAWL_JDBC_PASSWORD=<from-vault>

# Redis Configuration
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<from-vault>
```

**Optional Variables:**
```bash
# Integration Settings
ZHIPU_API_KEY=<from-vault>
ZAI_API_KEY=<from-vault>
MCP_SERVER_PORT=3000
A2A_SERVER_PORT=8080

# Performance Settings
JAVA_OPTS=-Xmx4g -Xms2g
HIKARI_MAX_POOL_SIZE=20
HIKARI_MIN_IDLE=5

# Logging
LOG_LEVEL=INFO
LOG_FORMAT=json
```

### Configuration Files

**Database Configuration** (`hibernate.properties`):
```properties
# PostgreSQL (production)
hibernate.connection.driver_class=org.postgresql.Driver
hibernate.connection.url=jdbc:postgresql://${DATABASE_HOST}:5432/${DATABASE_NAME}
hibernate.connection.username=${DATABASE_USERNAME}
hibernate.connection.password=${DATABASE_PASSWORD}
hibernate.dialect=org.hibernate.dialect.PostgreSQL15Dialect

# Connection Pool (HikariCP)
hibernate.hikari.maximumPoolSize=20
hibernate.hikari.minimumIdle=5
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.idleTimeout=600000
hibernate.hikari.maxLifetime=1800000

# Performance
hibernate.jdbc.batch_size=20
hibernate.order_inserts=true
hibernate.order_updates=true
hibernate.jdbc.batch_versioned_data=true
```

**Logging Configuration** (`log4j2.xml`):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="INFO">
  <Appenders>
    <!-- Console (JSON for cloud) -->
    <Console name="Console" target="SYSTEM_OUT">
      <JsonLayout compact="true" eventEol="true" />
    </Console>

    <!-- File (local debugging) -->
    <RollingFile name="RollingFile" fileName="logs/yawl.log"
                 filePattern="logs/yawl-%d{yyyy-MM-dd}-%i.log.gz">
      <PatternLayout pattern="%d{ISO8601} [%t] %-5level %logger{36} - %msg%n"/>
      <Policies>
        <TimeBasedTriggeringPolicy interval="1"/>
        <SizeBasedTriggeringPolicy size="100 MB"/>
      </Policies>
      <DefaultRolloverStrategy max="30"/>
    </RollingFile>
  </Appenders>

  <Loggers>
    <Root level="info">
      <AppenderRef ref="Console"/>
      <AppenderRef ref="RollingFile"/>
    </Root>

    <!-- YAWL-specific loggers -->
    <Logger name="org.yawlfoundation.yawl" level="info"/>
    <Logger name="org.yawlfoundation.yawl.engine" level="debug"/>

    <!-- Reduce noise -->
    <Logger name="org.hibernate" level="warn"/>
    <Logger name="com.zaxxer.hikari" level="warn"/>
  </Loggers>
</Configuration>
```

### Secret Management

#### HashiCorp Vault

```bash
# Store secrets in Vault
vault kv put secret/yawl/prod \
  database_password="secure_db_pass" \
  admin_password="secure_admin_pass" \
  redis_password="secure_redis_pass"

# Retrieve in deployment
export DATABASE_PASSWORD=$(vault kv get -field=database_password secret/yawl/prod)
```

#### AWS Secrets Manager

```bash
# Store secret
aws secretsmanager create-secret \
  --name yawl/prod/database \
  --secret-string '{"username":"yawl_admin","password":"secure_pass"}'

# Reference in ECS/EKS
# secrets:
#   - name: DATABASE_PASSWORD
#     valueFrom: arn:aws:secretsmanager:us-east-1:123456:secret:yawl/prod/database:password
```

#### Kubernetes Secrets

```bash
# Create from literal
kubectl create secret generic yawl-secrets \
  --from-literal=database-password=secure_pass \
  --namespace yawl

# Reference in Pod
# env:
#   - name: DATABASE_PASSWORD
#     valueFrom:
#       secretKeyRef:
#         name: yawl-secrets
#         key: database-password
```

---

## Monitoring and Logging

### Health Checks

**Endpoints:**
```bash
# Overall health
curl http://localhost:8080/health
# Expected: {"status":"UP"}

# Readiness (Kubernetes)
curl http://localhost:8080/health/ready
# Expected: {"status":"UP","checks":[...]}

# Liveness (Kubernetes)
curl http://localhost:8080/health/live
# Expected: {"status":"UP"}

# Metrics (Prometheus)
curl http://localhost:8080/metrics
```

**Kubernetes Probes:**
```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  initialDelaySeconds: 120
  periodSeconds: 30
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

### Metrics and Monitoring

#### Prometheus Integration

```bash
# Install Prometheus Operator
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace

# Create ServiceMonitor for YAWL
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  selector:
    matchLabels:
      app: yawl-engine
  endpoints:
    - port: http
      path: /metrics
      interval: 30s
EOF
```

#### Key Metrics to Monitor

| Metric | Description | Alert Threshold |
|--------|-------------|----------------|
| **yawl_engine_startup_time_seconds** | Engine startup time | > 60s |
| **yawl_case_creation_duration_seconds** | Case launch latency | > 0.5s (p95) |
| **yawl_workitem_checkout_duration_seconds** | Work item checkout | > 0.2s (p95) |
| **yawl_active_cases_total** | Active cases count | Monitor trend |
| **yawl_database_connections_active** | Active DB connections | > 80% pool |
| **jvm_memory_used_bytes** | JVM memory usage | > 90% Xmx |
| **jvm_gc_pause_seconds** | GC pause time | > 1s |

#### Grafana Dashboards

```bash
# Import YAWL dashboard
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-dashboard
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
data:
  yawl-overview.json: |
    {
      "dashboard": {
        "title": "YAWL Overview",
        "panels": [
          {
            "title": "Active Cases",
            "targets": [{"expr": "yawl_active_cases_total"}]
          },
          {
            "title": "Case Launch Latency (p95)",
            "targets": [{"expr": "histogram_quantile(0.95, yawl_case_creation_duration_seconds_bucket)"}]
          }
        ]
      }
    }
EOF
```

### Logging

#### Centralized Logging (ELK Stack)

```bash
# Deploy Elasticsearch
helm upgrade --install elasticsearch elastic/elasticsearch \
  --namespace logging \
  --create-namespace

# Deploy Kibana
helm upgrade --install kibana elastic/kibana \
  --namespace logging

# Deploy Filebeat (ships logs)
kubectl apply -f k8s/logging/filebeat-daemonset.yaml

# Access Kibana
kubectl port-forward -n logging svc/kibana 5601:5601
# Open: http://localhost:5601
```

#### Log Aggregation (Loki)

```bash
# Deploy Loki stack
helm upgrade --install loki grafana/loki-stack \
  --namespace logging \
  --create-namespace \
  --set promtail.enabled=true

# Query logs in Grafana
# LogQL: {namespace="yawl", app="yawl-engine"}
```

#### Application Logs

**Key Log Patterns:**
```
# Case lifecycle
INFO  YEngine - Case launched: case_abc123, spec=OrderProcessing v1.0
INFO  YNetRunner - Case case_abc123 completed successfully

# Work item events
DEBUG YWorkItemRepository - Work item enabled: item_xyz789
DEBUG YWorkItemRepository - Work item completed: item_xyz789

# Errors
ERROR YEngine - Failed to launch case: spec not found
WARN  HikariCP - Connection pool exhausted
```

**Search Queries:**
```bash
# Find errors
kubectl logs -n yawl deploy/yawl-engine | grep ERROR

# Find slow operations
kubectl logs -n yawl deploy/yawl-engine | grep "duration > 1000ms"

# Find specific case
kubectl logs -n yawl deploy/yawl-engine | grep "case_abc123"
```

---

## Performance Tuning

### JVM Tuning

**Recommended JVM Settings:**
```bash
export JAVA_OPTS="
  -Xms2g
  -Xmx4g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+UseStringDeduplication
  -XX:+ParallelRefProcEnabled
  -XX:InitiatingHeapOccupancyPercent=45
  -verbose:gc
  -Xlog:gc*:file=/var/log/yawl/gc.log:time,uptime:filecount=10,filesize=100m
"
```

**Memory Sizing Guide:**

| Workload | Heap Size | Metaspace | Total RAM |
|----------|-----------|-----------|-----------|
| **Small** (< 100 cases/day) | 1-2GB | 256MB | 2-3GB |
| **Medium** (100-1000 cases/day) | 2-4GB | 512MB | 4-6GB |
| **Large** (> 1000 cases/day) | 4-8GB | 1GB | 8-12GB |

### Database Tuning

**PostgreSQL Configuration:**
```properties
# postgresql.conf
shared_buffers = 4GB
effective_cache_size = 12GB
work_mem = 64MB
maintenance_work_mem = 1GB
max_connections = 200

# Tuning for YAWL workload
random_page_cost = 1.1  # SSD
effective_io_concurrency = 200
checkpoint_completion_target = 0.9
wal_buffers = 16MB
```

**Connection Pool Tuning:**
```properties
# HikariCP settings
hikari.maximumPoolSize=20
hikari.minimumIdle=5
hikari.connectionTimeout=30000
hikari.idleTimeout=600000
hikari.maxLifetime=1800000
hikari.leakDetectionThreshold=60000
```

### Redis Tuning

```bash
# redis.conf
maxmemory 2gb
maxmemory-policy allkeys-lru
timeout 300
tcp-keepalive 60
```

### Performance Baselines

**Target Metrics:**
| Metric | Target | Measurement |
|--------|--------|-------------|
| Engine startup | < 60s | Time to /health returns UP |
| Case creation (p95) | < 500ms | launchCase API latency |
| Work item checkout (p95) | < 200ms | checkOut API latency |
| Concurrent throughput | > 100 cases/sec | Load test result |
| Memory (1000 active cases) | < 512MB heap | JVM metrics |

---

## Troubleshooting

### Common Issues

#### Issue 1: Engine Won't Start

**Symptoms:**
- Container restarts repeatedly
- Liveness probe fails
- Logs show database connection errors

**Diagnosis:**
```bash
# Check logs
kubectl logs -n yawl deploy/yawl-engine --tail=100

# Check database connectivity
kubectl exec -n yawl deploy/yawl-engine -- \
  pg_isready -h $DATABASE_HOST -p 5432

# Verify secrets
kubectl get secret yawl-db-credentials -n yawl -o yaml
```

**Resolution:**
```bash
# Fix database credentials
kubectl create secret generic yawl-db-credentials \
  --from-literal=password=correct_password \
  --namespace yawl \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart deployment
kubectl rollout restart deployment/yawl-engine -n yawl
```

#### Issue 2: Slow Performance

**Symptoms:**
- Case creation > 1 second
- Work items not appearing
- High database CPU

**Diagnosis:**
```bash
# Check database queries
psql -d yawl -c "
SELECT pid, now() - query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active'
ORDER BY duration DESC;
"

# Check connection pool
curl http://localhost:8080/metrics | grep hikari

# Check JVM memory
curl http://localhost:8080/metrics | grep jvm_memory
```

**Resolution:**
```bash
# Add database indexes
psql -d yawl -c "
CREATE INDEX idx_workitem_status ON yawl_workitem(status);
CREATE INDEX idx_case_specid ON yawl_case(spec_id);
"

# Increase connection pool
kubectl set env deployment/yawl-engine \
  HIKARI_MAX_POOL_SIZE=30 \
  -n yawl

# Increase JVM heap
kubectl set env deployment/yawl-engine \
  JAVA_OPTS="-Xmx6g" \
  -n yawl
```

#### Issue 3: Out of Memory

**Symptoms:**
- Pod gets OOMKilled
- GC overhead errors in logs

**Diagnosis:**
```bash
# Check memory limits
kubectl describe pod -n yawl -l app=yawl-engine | grep -A5 Limits

# Generate heap dump
kubectl exec -n yawl deploy/yawl-engine -- \
  jmap -dump:format=b,file=/tmp/heap.hprof 1

# Copy heap dump
kubectl cp yawl/pod-name:/tmp/heap.hprof ./heap.hprof
```

**Resolution:**
```bash
# Increase memory limits
kubectl patch deployment yawl-engine -n yawl --patch '
spec:
  template:
    spec:
      containers:
      - name: yawl-engine
        resources:
          limits:
            memory: 6Gi
          requests:
            memory: 3Gi
'

# Increase heap size
kubectl set env deployment/yawl-engine \
  JAVA_OPTS="-Xmx4g" \
  -n yawl
```

### Diagnostic Commands

```bash
# Check pod status
kubectl get pods -n yawl

# Describe pod (events)
kubectl describe pod -n yawl pod-name

# View logs
kubectl logs -n yawl pod-name --tail=100 -f

# Previous container logs (after crash)
kubectl logs -n yawl pod-name --previous

# Execute into container
kubectl exec -it -n yawl pod-name -- /bin/bash

# Port forward for debugging
kubectl port-forward -n yawl svc/yawl-engine 8080:8080

# Check resource usage
kubectl top pods -n yawl
kubectl top nodes
```

---

## Backup and Recovery

### Database Backup

**Automated Backup (PostgreSQL):**
```bash
#!/bin/bash
# backup-yawl-db.sh

BACKUP_DIR=/backups/yawl
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/yawl_$DATE.sql.gz"

# Create backup
pg_dump -h postgres -U yawl_admin yawl | gzip > "$BACKUP_FILE"

# Keep last 30 days
find "$BACKUP_DIR" -name "yawl_*.sql.gz" -mtime +30 -delete

# Upload to S3
aws s3 cp "$BACKUP_FILE" s3://yawl-backups/
```

**Schedule with Kubernetes CronJob:**
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: yawl-db-backup
  namespace: yawl
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/bash
            - -c
            - |
              pg_dump -h $DB_HOST -U $DB_USER yawl | \
              gzip > /backups/yawl_$(date +%Y%m%d).sql.gz
            env:
            - name: DB_HOST
              value: postgres
            - name: DB_USER
              valueFrom:
                secretKeyRef:
                  name: yawl-db-credentials
                  key: username
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: yawl-db-credentials
                  key: password
            volumeMounts:
            - name: backups
              mountPath: /backups
          volumes:
          - name: backups
            persistentVolumeClaim:
              claimName: yawl-backups-pvc
          restartPolicy: OnFailure
```

### Restore Procedure

```bash
# Stop YAWL engine
kubectl scale deployment yawl-engine --replicas=0 -n yawl

# Restore database
gunzip -c yawl_20260216.sql.gz | \
  psql -h postgres -U yawl_admin yawl

# Restart YAWL engine
kubectl scale deployment yawl-engine --replicas=3 -n yawl

# Verify
kubectl get pods -n yawl
curl http://yawl-engine/health
```

### Disaster Recovery

**RTO (Recovery Time Objective):** 15 minutes
**RPO (Recovery Point Objective):** 0 (no data loss with WAL archiving)

**DR Runbook:**
1. Detect failure (monitoring alerts)
2. Assess damage (logs, metrics)
3. Restore database from backup
4. Redeploy YAWL services
5. Verify health checks
6. Resume traffic

---

## Security Operations

### Security Scanning

```bash
# OWASP Dependency Check
mvn clean verify -Psecurity-audit

# Container scanning
docker scan yawl/engine:5.2

# Kubernetes security scan
kubectl kube-bench run --targets master,node
```

### Access Control

**Kubernetes RBAC:**
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: yawl-operator
  namespace: yawl
rules:
- apiGroups: ["apps"]
  resources: ["deployments", "statefulsets"]
  verbs: ["get", "list", "watch", "update", "patch"]
- apiGroups: [""]
  resources: ["pods", "pods/log"]
  verbs: ["get", "list", "watch"]
```

### Certificate Management

```bash
# Auto-renewal with cert-manager
kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: yawl-tls
  namespace: yawl
spec:
  secretName: yawl-tls
  dnsNames:
  - yawl.yourdomain.com
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
EOF
```

---

## Scaling and High Availability

### Horizontal Scaling

```bash
# Manual scaling
kubectl scale deployment yawl-engine --replicas=5 -n yawl

# Auto-scaling (HPA)
kubectl apply -f - <<EOF
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
  maxReplicas: 10
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
EOF
```

### High Availability Setup

**Requirements:**
- Multiple availability zones
- Database replication
- Redis cluster
- Load balancer

**Example (AWS):**
```bash
# Multi-AZ RDS
aws rds create-db-instance \
  --db-instance-identifier yawl-postgres \
  --engine postgres \
  --multi-az \
  --backup-retention-period 35

# ElastiCache cluster mode
aws elasticache create-replication-group \
  --replication-group-id yawl-redis \
  --num-cache-clusters 3 \
  --automatic-failover-enabled

# EKS across 3 AZs
eksctl create cluster \
  --name yawl-cluster \
  --zones us-east-1a,us-east-1b,us-east-1c
```

---

## Additional Resources

- **Production Validation Report:** `/home/user/yawl/PRODUCTION_READINESS_VALIDATION_FINAL.md`
- **Security Guide:** `/home/user/yawl/SECURITY_MIGRATION_GUIDE.md`
- **Performance Baselines:** `/home/user/yawl/docs/performance/PERFORMANCE_BASELINES.md`
- **Cloud Deployment Guides:** `/home/user/yawl/docs/marketplace/{aws,azure,gcp}/`

---

**Total Lines:** ~850 (condensed to 250 core content)
**Estimated Reading Time:** 30 minutes
**Difficulty:** Advanced
