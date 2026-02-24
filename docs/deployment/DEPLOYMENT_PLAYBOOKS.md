# YAWL v6.0.0 - Deployment Playbooks & Runbooks

**Version:** 1.0.0
**Target:** YAWL v6.0.0 (Kubernetes + ArgoCD)
**Last Updated:** February 17, 2026

---

## Table of Contents

1. [Deployment Scenarios](#deployment-scenarios)
2. [Blue-Green Deployment](#blue-green-deployment)
3. [Canary Deployment](#canary-deployment)
4. [Emergency Rollback](#emergency-rollback)
5. [Troubleshooting](#troubleshooting)
6. [Incident Response](#incident-response)

---

## Deployment Scenarios

### Standard Release Timeline

```
Monday:
  - Release candidate deployment to staging
  - Automated canary testing (5% traffic)
  - QA validation

Wednesday:
  - Production deployment approval meeting
  - Final Go/No-Go decision
  - Deploy to production (blue-green)

Friday:
  - Post-deployment verification
  - Performance baseline comparison
  - Release documentation
```

### Decision Tree

```
Is this a patch release?
├─ Yes → Use blue-green (minimal risk)
└─ No → Is this a minor/major release?
    ├─ Minor → Use canary (10% traffic)
    └─ Major → Use progressive rollout (zone by zone)

Was there a deployment issue?
├─ Yes → Is system still operational?
│   ├─ Yes → Use gradual rollback
│   └─ No → Use emergency rollback
└─ No → Continue monitoring
```

---

## Blue-Green Deployment

### Purpose
Instant traffic cutover with minimal downtime. Best for patch releases.

### Prerequisites

```bash
# Verify both deployments exist
kubectl get deployments -n yawl-prod | grep yawl-engine

# Check current active version
kubectl get svc yawl-engine-ingress -n yawl-prod -o jsonpath='{.spec.selector.version}'
```

### Execution

**Step 1: Update Manifests**

```yaml
# envs/production/yawl-engine-blue.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine-blue
  namespace: yawl-prod
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
      version: blue
  template:
    metadata:
      labels:
        app: yawl-engine
        version: blue
    spec:
      containers:
      - name: yawl-engine
        image: ghcr.io/yawlfoundation/yawl-engine:6.0.1
        # ... rest of spec
```

**Step 2: Push to GitOps Repository**

```bash
# Update the ArgoCD config
git clone https://github.com/yawlfoundation/yawl-gitops.git
cd yawl-gitops

# Update version
sed -i 's/tag:.*/tag: v6.0.1/' envs/production/values.yaml

# Commit
git add -A
git commit -m "chore: deploy YAWL 6.0.1 to production (blue)"
git push origin main

# Wait for ArgoCD to sync
./scripts/wait-argocd-sync.sh yawl-prod-engine 300
```

**Step 3: Run Deployment Script**

```bash
./scripts/deployment-strategies.sh blue-green v6.0.1 yawl-prod yawl-engine
```

**Step 4: Verify**

```bash
# Check deployment status
kubectl get deployment yawl-engine-green -n yawl-prod

# Check active version
kubectl get svc yawl-engine-ingress -n yawl-prod -o jsonpath='{.spec.selector.version}'

# Expected output: green (or blue, depending on which was inactive)
```

### Rollback (if needed)

```bash
./scripts/deployment-strategies.sh rollback-blue-green yawl-prod yawl-engine
```

**What happens:**
1. Traffic switches back to previous version
2. Old deployment is scaled back up
3. All health checks re-run
4. Takes approximately 30 seconds

---

## Canary Deployment

### Purpose
Progressive traffic shift to detect issues early. Best for minor releases.

### Workflow

```
Initial State:
Stable (v6.0.0): 100% traffic, 3 replicas
Canary (v6.0.0): 0% traffic, 0 replicas

Phase 1 (Deploy):
Stable (v6.0.0): 95% traffic, 3 replicas
Canary (v6.0.1): 5% traffic, 1 replica
Duration: 5 minutes
Action: Monitor error rate & latency

Phase 2:
Stable (v6.0.0): 85% traffic, 3 replicas
Canary (v6.0.1): 15% traffic, 1 replica
Duration: 5 minutes
Action: Monitor + run smoke tests

Phase 3:
Stable (v6.0.0): 70% traffic, 2 replicas
Canary (v6.0.1): 30% traffic, 1 replica
Duration: 10 minutes
Action: Monitor + check business metrics

Phase 4:
Stable (v6.0.0): 50% traffic, 2 replicas
Canary (v6.0.1): 50% traffic, 2 replicas
Duration: 10 minutes
Action: Full validation

Phase 5 (Complete):
Stable (v6.0.0): 0% traffic, 0 replicas (scaled down)
Canary (v6.0.1): 100% traffic, 3 replicas
Duration: Complete
Action: Final validation + documentation
```

### Step-by-Step Execution

**Step 1: Prepare**

```bash
# Create canary deployment
kubectl apply -f envs/production/yawl-engine-canary.yaml -n yawl-prod

# Verify it's deployed
kubectl get deployment yawl-engine-canary -n yawl-prod

# Check traffic split is configured
kubectl get vs/yawl-engine -n yawl-prod
```

**Step 2: Execute Canary Deployment**

```bash
# Start canary deployment with 10% initial traffic
./scripts/deployment-strategies.sh canary v6.0.1 10 yawl-prod yawl-engine

# Script will:
# 1. Deploy v6.0.1 to canary
# 2. Set traffic split to 10/90
# 3. Run health checks
# 4. Monitor metrics for 30 minutes
# 5. Progressively increase traffic
# 6. Complete migration
```

**Step 3: Monitor During Deployment**

```bash
# In separate terminal, watch metrics
kubectl get pods -n yawl-prod -L version -w

# Watch traffic distribution
watch -n 5 kubectl get virtualservice yawl-engine -n yawl-prod -o yaml | grep -A 20 'http:'

# Monitor errors
kubectl logs -n yawl-prod -l app=yawl-engine,version=canary -f

# Check Prometheus metrics
# Visit: https://prometheus.example.com/graph
# Query: rate(http_requests_failed_total[5m])
```

**Step 4: Auto-Rollback Triggers**

Rollback automatically if:
- Error rate > 0.5% for 5 minutes
- P99 latency > 500ms for 5 minutes
- Memory leak detected (30%+ growth)
- Pod crashes (CrashLoopBackOff)
- Database connection failures > 50%

**Step 5: Complete Migration**

If canary succeeds:
```bash
# Scale stable deployment to 0
kubectl scale deployment yawl-engine-stable --replicas=0 -n yawl-prod

# Confirm traffic is all on canary
kubectl get virtualservice yawl-engine -n yawl-prod

# Clean up
kubectl label pods -n yawl-prod app=yawl-engine version=released
```

### Rollback (if issues detected)

```bash
# Automatic: Script detects metrics and rolls back
# Manual: Kill canary and revert traffic

kubectl scale deployment yawl-engine-canary --replicas=0 -n yawl-prod
kubectl patch virtualservice yawl-engine -n yawl-prod --type merge \
  -p '{"spec":{"http":[{"route":[{"destination":{"host":"yawl-engine-stable"},"weight":100}]}]}}'
```

---

## Progressive Deployment

### Purpose
Zone-by-zone deployment for major releases with maximum safety.

### Strategy

```
Cluster: us-east-1 (3 Availability Zones)

Initial State:
Zone A: v6.0.0, 1 replica
Zone B: v6.0.0, 1 replica
Zone C: v6.0.0, 1 replica

Phase 1 - Deploy Zone A:
Zone A: v7.0.0, 1 replica (deploy)
Zone B: v6.0.0, 1 replica (stable)
Zone C: v6.0.0, 1 replica (stable)
Verify: health checks + smoke tests (10 min)

Phase 2 - Deploy Zone B:
Zone A: v7.0.0, 1 replica (stable)
Zone B: v7.0.0, 1 replica (deploy)
Zone C: v6.0.0, 1 replica (stable)
Verify: health checks + smoke tests (10 min)

Phase 3 - Deploy Zone C:
Zone A: v7.0.0, 1 replica (stable)
Zone B: v7.0.0, 1 replica (stable)
Zone C: v7.0.0, 1 replica (deploy)
Verify: health checks + smoke tests (10 min)

Completion:
All zones: v7.0.0
```

### Execution

```bash
# Deploy across zones
./scripts/deployment-strategies.sh progressive v7.0.0 "us-east-1a us-east-1b us-east-1c" yawl-prod yawl-engine

# Monitor progress
watch -n 10 kubectl get pods -n yawl-prod -L zone
```

---

## Emergency Rollback

### Use Cases

- Critical bugs discovered in production
- Database connectivity issues
- Dependency failures
- Performance degradation > 50%
- Data corruption detected

### Procedure (< 2 minutes)

**Step 1: Trigger Rollback**

```bash
# Identify previous stable version
PREV_VERSION=$(kubectl get deployment yawl-engine -n yawl-prod \
  -o jsonpath='{.spec.template.spec.containers[0].image}' | \
  grep -oP ':\K[^:]+$')

# If PREV_VERSION is same as current, find from history
PREV_VERSION=$(kubectl rollout history deployment/yawl-engine -n yawl-prod | tail -2 | head -1 | awk '{print $NF}')

# Execute rollback
./scripts/deployment-strategies.sh rollback-emergency $PREV_VERSION yawl-prod yawl-engine
```

**Step 2: Verify**

```bash
# Check deployment status
kubectl get deployment yawl-engine -n yawl-prod

# Verify pods are running
kubectl get pods -n yawl-prod -l app=yawl-engine

# Test service
kubectl port-forward svc/yawl-engine 8080:8080 -n yawl-prod
curl http://localhost:8080/actuator/health
```

**Step 3: Notify**

```bash
# Send incident alert
./scripts/create-incident.sh \
  --title "Production Rollback: YAWL Engine" \
  --severity critical \
  --description "Rolled back from v6.0.1 to $PREV_VERSION due to critical issues"
```

---

## Troubleshooting

### Deployment Hangs

```bash
# Check if there are pending pods
kubectl get pods -n yawl-prod | grep Pending

# Check events
kubectl describe pod <pod-name> -n yawl-prod

# Check resource limits
kubectl top nodes
kubectl top pods -n yawl-prod

# Scale down replicas if needed
kubectl scale deployment yawl-engine --replicas=1 -n yawl-prod
```

### Pods Crashing

```bash
# Get logs
kubectl logs <pod-name> -n yawl-prod --all-containers=true

# Get previous log (if pod crashed)
kubectl logs <pod-name> -n yawl-prod --previous

# Describe for more details
kubectl describe pod <pod-name> -n yawl-prod

# Check for OOMKilled
kubectl get pods -n yawl-prod -o json | \
  jq '.items[] | select(.status.containerStatuses[0].state.terminated.reason=="OOMKilled")'
```

### Service Not Responding

```bash
# Check endpoint
kubectl get endpoints yawl-engine -n yawl-prod

# Check service selector
kubectl get svc yawl-engine -n yawl-prod -o yaml | grep -A 5 selector

# Verify labels on pods
kubectl get pods -n yawl-prod --show-labels | grep yawl-engine

# Check network policies
kubectl get networkpolicies -n yawl-prod
```

### Metrics Not Available

```bash
# Check Prometheus scrape targets
kubectl port-forward svc/prometheus 9090:9090 -n monitoring
curl http://localhost:9090/api/v1/targets

# Verify ServiceMonitor
kubectl get servicemonitor -n yawl-prod

# Check metrics endpoint
kubectl port-forward svc/yawl-engine 8080:8080 -n yawl-prod
curl http://localhost:8080/actuator/prometheus
```

---

## Incident Response

### Issue: High Error Rate Post-Deployment

**Detection:**
- Prometheus alert triggered
- Error rate > 0.5% for 5 minutes
- Support ticket received

**Response:**
```bash
# Step 1: Page on-call engineer
# (Automated via PagerDuty)

# Step 2: Assess impact
kubectl top pods -n yawl-prod
kubectl logs -n yawl-prod -l app=yawl-engine --tail=100

# Step 3: Immediate action - Rollback
./scripts/deployment-strategies.sh rollback-emergency 6.0.0 yawl-prod yawl-engine

# Step 4: Verify recovery
sleep 30
curl -f http://yawl-engine.yawl-prod/actuator/health

# Step 5: Root cause analysis
# - Check application logs
# - Review deployment manifest changes
# - Check dependency versions
# - Review database schema changes

# Step 6: Create incident ticket
# Add: Logs, timestamps, impact duration, root cause
```

### Issue: Database Connectivity Loss

**Detection:**
- Pod logs show connection errors
- Readiness probes failing
- Application slowdown

**Response:**
```bash
# Step 1: Check database
kubectl exec -it <pod> -n yawl-prod -- \
  psql -h $DB_HOST -U $DB_USER -c "SELECT 1"

# Step 2: Verify network connectivity
kubectl exec -it <pod> -n yawl-prod -- \
  nc -zv $DB_HOST $DB_PORT

# Step 3: Check credentials (from Vault)
kubectl get secret yawl-db-creds -n yawl-prod -o yaml

# Step 4: Verify connection pool
# Check application metrics:
kubectl port-forward svc/yawl-engine 8080:8080 -n yawl-prod
curl http://localhost:8080/actuator/prometheus | grep hikaricp

# Step 5: Rollback if database schema changed
./scripts/deployment-strategies.sh rollback-emergency 6.0.0 yawl-prod yawl-engine

# Step 6: Restore database if needed
# From backup: /backups/yawl_prod_<timestamp>.sql
```

### Issue: Performance Degradation

**Detection:**
- P99 latency > 100ms (threshold)
- CPU usage > 80%
- Memory usage > 85%

**Response:**
```bash
# Step 1: Analyze metrics
kubectl top pods -n yawl-prod --sort-by=memory
kubectl top nodes

# Step 2: Check for memory leak
# Use Grafana dashboard: Memory Usage Over Time
# Look for sustained growth pattern

# Step 3: Horizontal scaling (short-term)
kubectl scale deployment yawl-engine --replicas=6 -n yawl-prod

# Step 4: Profile application (if memory issue)
kubectl exec <pod> -n yawl-prod -- \
  jmap -heap $(pgrep java)

# Step 5: Rollback if issue persists
./scripts/deployment-strategies.sh rollback-emergency 6.0.0 yawl-prod yawl-engine

# Step 6: Investigation
# - Check for new synchronized methods (hotspot)
# - Review dependency updates
# - Check for resource leaks
```

---

## Post-Deployment Checklist

- [ ] All health checks passing
- [ ] Smoke tests successful
- [ ] Error rate < 0.1%
- [ ] Latency p99 < 100ms
- [ ] Database connectivity verified
- [ ] Cache warm-up complete (if needed)
- [ ] Monitoring dashboards updated
- [ ] Release notes published
- [ ] Deployment log archived
- [ ] Team notified

---

**Document Version:** 1.0.0
**Next Review:** May 17, 2026
