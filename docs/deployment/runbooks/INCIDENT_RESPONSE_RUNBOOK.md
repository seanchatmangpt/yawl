# Production Incident Response Runbook - YAWL v5.2

**Emergency Operations Guide**
**Version:** 5.2
**Date:** 2026-02-16

---

## Table of Contents

1. [Service Outage (P1)](#1-service-outage-p1)
2. [Data Loss / Corruption (P0)](#2-data-loss--corruption-p0)
3. [Performance Degradation (P2)](#3-performance-degradation-p2)
4. [Database Connectivity Issues (P1)](#4-database-connectivity-issues-p1)
5. [OOMKilled Pods (P2)](#5-oomkilled-pods-p2)
6. [Memory Leak (P2)](#6-memory-leak-p2)
7. [Certificate Expiry (P1)](#7-certificate-expiry-p1)
8. [Rollback Procedures](#8-rollback-procedures)

---

## 1. Service Outage (P1)

**Severity:** CRITICAL (P1)
**Impact:** All users unable to access YAWL
**SLA:** Restore within 15 minutes

### Detection

- Health check failures
- Prometheus alert: `YawlEngineDown`
- User reports of 502/504 errors
- PagerDuty incident triggered

### Immediate Actions (< 5 minutes)

```bash
# 1. Acknowledge incident
# PagerDuty: Acknowledge alert
# Slack: Post in #yawl-incidents

# 2. Check pod status
kubectl get pods -n yawl -o wide

# 3. Check recent deployments
kubectl rollout history deployment/yawl-engine -n yawl

# 4. Check recent events
kubectl get events -n yawl --sort-by='.lastTimestamp' | tail -30

# 5. Quick log check for errors
kubectl logs -n yawl deployment/yawl-engine --tail=200 | grep -i error
```

### Diagnosis (< 10 minutes)

#### Scenario A: All Pods CrashLoopBackOff

```bash
# Check why pods are crashing
kubectl describe pod -n yawl -l app=yawl-engine

# Common causes:
# 1. Database connection failure
# 2. Configuration error
# 3. Image pull failure
# 4. Resource limits too low
```

**Resolution:**
```bash
# If database connection failure:
kubectl exec -it -n yawl yawl-engine-xxx -- nc -zv postgres 5432

# If configuration error:
kubectl get configmap yawl-config -n yawl -o yaml | grep -A 20 "data:"

# If image pull failure:
kubectl describe pod -n yawl -l app=yawl-engine | grep -A 5 "Events:"

# Quick fix: Rollback to last known good version
kubectl rollout undo deployment/yawl-engine -n yawl
kubectl rollout status deployment/yawl-engine -n yawl
```

#### Scenario B: Pods Running But Not Ready

```bash
# Check readiness probe status
kubectl describe pod -n yawl -l app=yawl-engine | grep -A 10 "Readiness:"

# Check health endpoint
kubectl exec -it -n yawl yawl-engine-xxx -- curl localhost:8080/engine/health

# Common causes:
# 1. Database connection pool exhausted
# 2. Slow startup (increase initialDelaySeconds)
# 3. Health check misconfigured
```

**Resolution:**
```bash
# If database pool exhausted, scale up:
kubectl scale deployment yawl-engine -n yawl --replicas=10

# If slow startup, patch readiness probe:
kubectl patch deployment yawl-engine -n yawl -p '
{
  "spec": {
    "template": {
      "spec": {
        "containers": [{
          "name": "yawl-engine",
          "readinessProbe": {
            "initialDelaySeconds": 60,
            "periodSeconds": 10
          }
        }]
      }
    }
  }
}'
```

#### Scenario C: Ingress / Load Balancer Issues

```bash
# Check ingress status
kubectl get ingress -n yawl
kubectl describe ingress yawl-ingress -n yawl

# Check load balancer
kubectl get svc -n yawl

# Test connectivity from ingress to pod
INGRESS_POD=$(kubectl get pods -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx -o name | head -1)
kubectl exec -n ingress-nginx $INGRESS_POD -- curl http://yawl-engine.yawl.svc.cluster.local:8080/engine/health
```

### Mitigation (< 15 minutes)

```bash
# Option 1: Rollback to last known good version
kubectl rollout undo deployment/yawl-engine -n yawl

# Option 2: Scale up to overwhelm issue
kubectl scale deployment yawl-engine -n yawl --replicas=10

# Option 3: Switch to standby cluster (disaster recovery)
# See DISASTER_RECOVERY_RUNBOOK.md

# Option 4: Direct traffic to maintenance page
kubectl patch ingress yawl-ingress -n yawl -p '
{
  "spec": {
    "rules": [{
      "host": "yawl.example.com",
      "http": {
        "paths": [{
          "path": "/",
          "pathType": "Prefix",
          "backend": {
            "service": {
              "name": "maintenance-page",
              "port": {"number": 80}
            }
          }
        }]
      }
    }]
  }
}'
```

### Verification

```bash
# 1. Check pod health
kubectl get pods -n yawl
# All pods should be Running and Ready (1/1)

# 2. Test health endpoint
curl https://yawl.example.com/engine/health
# Should return: {"status": "UP"}

# 3. Test case creation
curl -X POST https://yawl.example.com/engine/api/cases \
  -H "Content-Type: application/json" \
  -d '{"specId": "test-spec", "caseParams": {}}'

# 4. Check metrics
curl https://yawl.example.com/engine/metrics | grep yawl_engine_active_cases
```

### Postmortem (within 24 hours)

```markdown
# Incident Postmortem Template

**Date:** YYYY-MM-DD
**Severity:** P1
**Duration:** XX minutes

## Summary
[Brief description of incident]

## Timeline
- HH:MM - Incident detected
- HH:MM - Team notified
- HH:MM - Root cause identified
- HH:MM - Mitigation applied
- HH:MM - Service restored
- HH:MM - Incident closed

## Root Cause
[Detailed explanation]

## Resolution
[How it was fixed]

## Impact
- Users affected: XXX
- Revenue impact: $XXX
- Cases failed: XXX

## Action Items
- [ ] Fix root cause permanently
- [ ] Add monitoring to detect earlier
- [ ] Update runbook with learnings
- [ ] Train team on prevention

## Lessons Learned
[What we learned]
```

---

## 2. Data Loss / Corruption (P0)

**Severity:** CRITICAL (P0)
**Impact:** Data integrity compromised
**SLA:** Restore within 30 minutes

### Detection

- Database corruption alerts
- Backup verification failures
- Data validation errors
- User reports of missing data

### Immediate Actions (< 5 minutes)

```bash
# 1. STOP ALL WRITES IMMEDIATELY
kubectl scale deployment yawl-engine -n yawl --replicas=0

# 2. Create emergency database snapshot
# GCP:
gcloud sql backups create --instance yawl-db --async

# AWS:
aws rds create-db-snapshot \
  --db-instance-identifier yawl-prod-db \
  --db-snapshot-identifier emergency-$(date +%s)

# Azure:
az postgres server-backup show \
  --resource-group yawl-prod \
  --server-name yawl-db

# 3. Notify team and stakeholders
# Slack: #yawl-incidents
# Email: executives, affected customers

# 4. Begin investigation
kubectl logs -n yawl deployment/yawl-engine --tail=1000 > incident-logs.txt
```

### Diagnosis (< 15 minutes)

```bash
# 1. Assess extent of data loss
kubectl exec -it postgres-0 -n yawl -- psql -U postgres -d yawl -c "
SELECT COUNT(*) FROM yawl_cases WHERE created_at > NOW() - INTERVAL '1 hour';
SELECT COUNT(*) FROM yawl_workitems WHERE status = 'ACTIVE';
"

# 2. Check database logs
kubectl logs -n yawl postgres-0 --tail=500 | grep -i error

# 3. Compare with last backup
# Restore backup to test database and compare row counts

# 4. Check application logs for corruption indicators
kubectl logs -n yawl deployment/yawl-engine --tail=2000 | grep -i "constraint\|unique\|duplicate\|corrupt"
```

### Recovery Procedures

#### Option 1: Point-in-Time Recovery (Preferred)

```bash
# GCP: Restore to specific timestamp
gcloud sql instances clone yawl-db yawl-db-restored \
  --point-in-time '2026-02-16T10:30:00Z'

# AWS: Restore from automated backup
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier yawl-prod-db \
  --target-db-instance-identifier yawl-prod-db-restored \
  --restore-time 2026-02-16T10:30:00Z

# Azure: Restore to point in time
az postgres server restore \
  --resource-group yawl-prod \
  --name yawl-db-restored \
  --source-server yawl-db \
  --restore-point-in-time "2026-02-16T10:30:00Z"

# Update connection string to restored database
kubectl set env deployment/yawl-engine -n yawl \
  DATABASE_URL=postgresql://postgres@yawl-db-restored:5432/yawl

# Resume operations
kubectl scale deployment yawl-engine -n yawl --replicas=3
```

#### Option 2: Manual Data Recovery

```bash
# Export good data from backup
kubectl exec -it postgres-backup-0 -n yawl -- pg_dump -U postgres yawl -t yawl_cases > good_data.sql

# Import into corrupted database
kubectl exec -i postgres-0 -n yawl -- psql -U postgres yawl < good_data.sql

# Verify data integrity
kubectl exec -it postgres-0 -n yawl -- psql -U postgres -d yawl -c "
SELECT table_name, pg_size_pretty(pg_total_relation_size(table_name::text)) AS size
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY pg_total_relation_size(table_name::text) DESC;
"
```

### Verification

```bash
# 1. Data integrity checks
kubectl exec -it postgres-0 -n yawl -- psql -U postgres -d yawl <<EOF
-- Check for orphaned work items
SELECT COUNT(*) FROM yawl_workitems wi
WHERE NOT EXISTS (SELECT 1 FROM yawl_cases c WHERE c.id = wi.case_id);

-- Check for duplicate cases
SELECT case_external_id, COUNT(*)
FROM yawl_cases
GROUP BY case_external_id
HAVING COUNT(*) > 1;

-- Verify foreign key constraints
SELECT conname, conrelid::regclass, confrelid::regclass
FROM pg_constraint
WHERE contype = 'f';
EOF

# 2. Application-level validation
curl -X POST https://yawl.example.com/engine/api/validate-all-cases

# 3. Spot-check recent cases
curl https://yawl.example.com/engine/api/cases?limit=100
```

### Communication

```markdown
**Customer Communication Template**

Subject: [RESOLVED] Data Integrity Issue - YAWL Service

Dear Valued Customer,

We experienced a data integrity issue today from HH:MM to HH:MM UTC that may have affected your workflow cases.

WHAT HAPPENED:
[Brief explanation]

IMPACT:
- Affected time window: HH:MM to HH:MM UTC
- Cases created during this window: XXX
- Data loss: [None / Minimal / Describe]

RESOLUTION:
[How we fixed it]

YOUR ACTION REQUIRED:
- Review cases created between HH:MM and HH:MM
- Re-submit any failed cases
- Contact support if you identify any issues

We sincerely apologize for this disruption.

YAWL Support Team
```

---

## 3. Performance Degradation (P2)

**Severity:** HIGH (P2)
**Impact:** Slow response times, user complaints
**SLA:** Diagnose within 30 minutes, mitigate within 2 hours

### Detection

- Prometheus alert: `YawlHighLatency`
- p95 latency > 2 seconds
- User reports of slow performance

### Diagnosis

```bash
# 1. Check current performance metrics
kubectl exec -n yawl deployment/yawl-engine -- \
  curl localhost:8080/engine/metrics | grep duration

# 2. Check resource utilization
kubectl top pods -n yawl

# 3. Check database performance
kubectl exec -it postgres-0 -n yawl -- psql -U postgres -d yawl -c "
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 seconds'
ORDER BY duration DESC;
"

# 4. Check for slow queries
kubectl logs -n yawl deployment/yawl-engine | grep "SlowQueryWarning"

# 5. Check database connection pool
kubectl logs -n yawl deployment/yawl-engine | grep "HikariPool"
```

### Mitigation

```bash
# Option 1: Scale up replicas
kubectl scale deployment yawl-engine -n yawl --replicas=10

# Option 2: Increase resource limits
kubectl patch deployment yawl-engine -n yawl -p '
{
  "spec": {
    "template": {
      "spec": {
        "containers": [{
          "name": "yawl-engine",
          "resources": {
            "requests": {"memory": "2Gi", "cpu": "1000m"},
            "limits": {"memory": "4Gi", "cpu": "2000m"}
          }
        }]
      }
    }
  }
}'

# Option 3: Tune database connection pool
kubectl set env deployment/yawl-engine -n yawl \
  HIKARI_MAXIMUM_POOL_SIZE=50 \
  HIKARI_MINIMUM_IDLE=10

# Option 4: Add database read replicas
# (Cloud provider specific - see cloud deployment guides)

# Option 5: Enable caching
kubectl set env deployment/yawl-engine -n yawl \
  YAWL_CACHE_ENABLED=true \
  YAWL_CACHE_SIZE=1000
```

---

## 4. Database Connectivity Issues (P1)

**Severity:** CRITICAL (P1)
**Impact:** Cannot read/write data
**SLA:** Restore within 15 minutes

### Diagnosis

```bash
# 1. Test database connectivity from pod
kubectl exec -it -n yawl yawl-engine-xxx -- nc -zv postgres 5432

# 2. Check database service
kubectl get svc postgres -n yawl

# 3. Check database pods
kubectl get pods -n yawl -l app=postgres

# 4. Check database logs
kubectl logs -n yawl postgres-0 --tail=100
```

### Resolution

```bash
# If database pod down, restart it:
kubectl delete pod postgres-0 -n yawl

# If connection pool exhausted:
kubectl set env deployment/yawl-engine -n yawl \
  HIKARI_MAXIMUM_POOL_SIZE=100

# If network policy blocking traffic:
kubectl apply -f k8s/base/network-policies.yaml

# If credentials invalid:
kubectl get secret yawl-db-credentials -n yawl -o yaml
kubectl delete secret yawl-db-credentials -n yawl
# External Secrets Operator will recreate it

# Force reconnection:
kubectl rollout restart deployment/yawl-engine -n yawl
```

---

## 5. OOMKilled Pods (P2)

**Severity:** HIGH (P2)
**Impact:** Pods restarting frequently
**SLA:** Resolve within 4 hours

### Diagnosis

```bash
# Check pod status
kubectl get pods -n yawl
# Look for "OOMKilled" in REASON column

# Check memory usage
kubectl top pods -n yawl

# Check resource limits
kubectl describe pod -n yawl yawl-engine-xxx | grep -A 5 "Limits:"

# Get heap dump before restart
kubectl exec -n yawl yawl-engine-xxx -- jcmd 1 GC.heap_dump /tmp/heap.hprof
kubectl cp yawl/yawl-engine-xxx:/tmp/heap.hprof ./heap.hprof
```

### Resolution

```bash
# Increase memory limits
kubectl patch deployment yawl-engine -n yawl -p '
{
  "spec": {
    "template": {
      "spec": {
        "containers": [{
          "name": "yawl-engine",
          "resources": {
            "limits": {"memory": "8Gi"}
          }
        }]
      }
    }
  }
}'

# Tune JVM heap
kubectl set env deployment/yawl-engine -n yawl \
  JAVA_OPTS="-Xmx4g -Xms2g -XX:MaxMetaspaceSize=512m"
```

---

## 6. Memory Leak (P2)

**Severity:** HIGH (P2)
**Impact:** Gradual performance degradation
**SLA:** Identify within 8 hours

### Diagnosis

```bash
# Enable heap dump on OOM
kubectl set env deployment/yawl-engine -n yawl \
  JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

# Take heap dump
kubectl exec -n yawl yawl-engine-xxx -- jcmd 1 GC.heap_dump /tmp/heap.hprof
kubectl cp yawl/yawl-engine-xxx:/tmp/heap.hprof ./heap-$(date +%s).hprof

# Analyze with Eclipse MAT or VisualVM
# Look for objects with high retained heap
```

### Mitigation

```bash
# Restart pods periodically (temporary workaround)
kubectl rollout restart deployment/yawl-engine -n yawl

# Enable G1GC with aggressive collection
kubectl set env deployment/yawl-engine -n yawl \
  JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

---

## 7. Certificate Expiry (P1)

**Severity:** CRITICAL (P1)
**Impact:** TLS handshake failures
**SLA:** Renew within 1 hour

### Diagnosis

```bash
# Check certificate expiry
echo | openssl s_client -connect yawl.example.com:443 2>/dev/null | \
  openssl x509 -noout -dates

# Check SPIFFE SVID expiry
kubectl exec -n yawl deployment/yawl-engine -- \
  curl localhost:8080/engine/spiffe/svid | jq '.expiresAt'
```

### Resolution

```bash
# Renew Let's Encrypt certificate
kubectl delete certificate yawl-tls -n yawl
# cert-manager will automatically renew

# Force SPIFFE SVID rotation
kubectl rollout restart deployment/yawl-engine -n yawl
```

---

## 8. Rollback Procedures

### Rollback Deployment

```bash
# View rollout history
kubectl rollout history deployment/yawl-engine -n yawl

# Rollback to previous version
kubectl rollout undo deployment/yawl-engine -n yawl

# Rollback to specific revision
kubectl rollout undo deployment/yawl-engine -n yawl --to-revision=5

# Monitor rollback
kubectl rollout status deployment/yawl-engine -n yawl
```

### Rollback Database Migration

```bash
# Connect to database
kubectl exec -it postgres-0 -n yawl -- psql -U postgres yawl

# Check migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;

# Rollback last migration (if using Flyway)
# Manually run rollback SQL script
\i /path/to/rollback/V5.2__Undo.sql
```

---

## Emergency Contacts

| Role | Contact | Phone | Availability |
|------|---------|-------|--------------|
| On-Call Engineer | PagerDuty | - | 24/7 |
| Database Admin | db-admin@yawl.org | +1-555-0123 | 24/7 |
| Security Team | security@yawl.org | +1-555-0456 | Business hours |
| Engineering Manager | manager@yawl.org | +1-555-0789 | Business hours |

---

**Document Owner:** DevOps Team
**Last Updated:** 2026-02-16
**Review Cycle:** Quarterly
