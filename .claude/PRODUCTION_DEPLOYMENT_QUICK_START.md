# YAWL Production Deployment — Quick Start Guide

**For**: DevOps/SRE teams  
**Time to Production**: 2-3 days  
**Team Size**: 2-3 engineers

---

## Phase 1: Prepare (Day 1, 4 hours)

### 1.1 Verify Infrastructure (30 min)

```bash
# Clone the guide
cd /home/user/yawl

# Check cluster readiness
kubectl cluster-info
kubectl get nodes
kubectl top nodes

# Checklist:
# ✓ K8s 1.27+
# ✓ 5+ nodes (staging) or 1000+ nodes (production)
# ✓ DNS working
# ✓ Storage classes available
```

### 1.2 Prepare Secrets (30 min)

```bash
# Create namespace
kubectl create namespace yawl-prod

# Generate secrets
kubectl create secret generic yawl-postgresql-prod \
  --from-literal=postgres-password=$(openssl rand -base64 32) \
  --from-literal=password=$(openssl rand -base64 32) \
  -n yawl-prod

kubectl create secret generic yawl-redis-prod \
  --from-literal=password=$(openssl rand -base64 32) \
  -n yawl-prod

# Save passwords to secure location (e.g., HashiCorp Vault)
kubectl get secret yawl-postgresql-prod -n yawl-prod -o yaml > /tmp/pg-secret.yaml
kubectl get secret yawl-redis-prod -n yawl-prod -o yaml > /tmp/redis-secret.yaml

# Secure these files!
chmod 600 /tmp/pg-secret.yaml /tmp/redis-secret.yaml
```

### 1.3 Deploy Infrastructure (3 hours)

```bash
# Add Helm repos
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Deploy PostgreSQL
helm install postgres-prod bitnami/postgresql \
  -n yawl-prod \
  --set auth.existingSecret=yawl-postgresql-prod \
  --set primary.persistence.size=100Gi \
  --set primary.persistence.storageClassName=fast-ssd-replicated \
  --set readReplicas.replicaCount=2 \
  --set metrics.enabled=true \
  --wait --timeout 10m

# Deploy Redis
helm install redis-prod bitnami/redis \
  -n yawl-prod \
  --set auth.existingSecret=yawl-redis-prod \
  --set master.persistence.size=8Gi \
  --set replica.replicaCount=2 \
  --set metrics.enabled=true \
  --wait --timeout 10m

# Deploy Prometheus/Grafana
helm install prometheus prometheus-community/kube-prometheus-stack \
  -n monitoring \
  --create-namespace \
  --wait --timeout 10m

# Verify all running
kubectl get pods -n yawl-prod
kubectl get pods -n monitoring
```

---

## Phase 2: Deploy YAWL Engine (Day 2, 6 hours)

### 2.1 Create Values File

```bash
cat > /tmp/values-prod.yaml << 'YAML'
global:
  namespace: yawl-prod

services:
  engine:
    replicaCount: 500         # Start at 500, HPA will scale to 5000
    resources:
      requests:
        cpu: 2000m
        memory: 4Gi
      limits:
        cpu: 4000m
        memory: 6Gi
    autoscaling:
      enabled: true
      minReplicas: 500
      maxReplicas: 5000
      targetCPUUtilizationPercentage: 65
      targetMemoryUtilizationPercentage: 75

postgresql:
  enabled: false

externalDatabase:
  host: postgres-prod-postgresql.yawl-prod.svc.cluster.local
  port: 5432
  database: yawl
  existingSecret: yawl-postgresql-prod

redis:
  enabled: false

externalRedis:
  host: redis-prod-master.yawl-prod.svc.cluster.local
  port: 6379
  existingSecret: yawl-redis-prod
YAML
```

### 2.2 Deploy YAWL Engine

```bash
# Build the chart (if needed)
cd /home/user/yawl
mvn -T 1.5C clean package -pl yawl-engine -DskipTests

# Deploy via Helm
helm install yawl-prod ./helm/yawl \
  -n yawl-prod \
  -f /tmp/values-prod.yaml \
  --wait --timeout 15m

# Watch rollout
kubectl rollout status deployment/yawl-engine -n yawl-prod --timeout=10m

# Verify
kubectl get pods -n yawl-prod -l app.kubernetes.io/name=yawl-engine | wc -l
```

### 2.3 Test Connectivity

```bash
# Port forward to test
kubectl port-forward -n yawl-prod svc/yawl-engine 8080:8080 &
sleep 5

# Test API
curl -v http://localhost:8080/yawl/ib

# Check engine health
curl -s http://localhost:8080/actuator/health | jq

# Cleanup
kill %1
```

---

## Phase 3: Validate (Day 3, 4 hours)

### 3.1 Run Load Test (1 hour)

```bash
# Deploy load generator
helm install load-test ./helm/yawl-load-test \
  -n yawl-prod \
  --set targetRPS=10000 \
  --set duration=300 \
  --wait

# Monitor metrics
kubectl port-forward -n yawl-prod svc/prometheus 9090:9090 &
# Open http://localhost:9090

# Key queries:
# yawl_active_cases
# histogram_quantile(0.99, yawl_case_create_duration_ms)
# rate(yawl_cases_created_total[1m])
```

### 3.2 Chaos Test (1 hour)

```bash
# Kill 10% of pods
PODS=$(kubectl get pods -n yawl-prod -l app.kubernetes.io/name=yawl-engine -o name | wc -l)
KILL_COUNT=$((PODS / 10))

for pod in $(kubectl get pods -n yawl-prod -l app.kubernetes.io/name=yawl-engine -o name | shuf | head -$KILL_COUNT); do
  kubectl delete $pod -n yawl-prod &
done

# Monitor recovery (should be <5 min)
watch -n5 'kubectl get pods -n yawl-prod -l app.kubernetes.io/name=yawl-engine -o jsonpath="{.items[?(@.status.phase==\"Running\")].metadata.name}" | wc -l'

# Verify metrics stable
curl -s http://prometheus:9090/api/v1/query?query='yawl_active_cases'
```

### 3.3 Final Validation (2 hours)

```bash
# Database consistency
kubectl exec -it -n yawl-prod postgres-prod-0 -- psql -U postgres -d yawl << SQL
  SELECT COUNT(*) as case_count FROM yawl_cases;
  SELECT COUNT(*) as work_item_count FROM yawl_work_items;
  SELECT * FROM pg_stat_replication;
SQL

# Metrics stable
curl -s http://prometheus:9090/api/v1/query?query='histogram_quantile(0.99,yawl_case_create_duration_ms)' | jq
curl -s http://prometheus:9090/api/v1/query?query='rate(yawl_cases_created_total[1m])' | jq

# No errors in logs
kubectl logs -n yawl-prod -l app.kubernetes.io/name=yawl-engine --tail=100 | grep -i error | wc -l
# Should be < 5
```

---

## Phase 4: Production Cutover (Day 3, 2 hours)

```bash
# Update DNS/Load Balancer to point to yawl.example.com → yawl-engine (production)
# Use blue-green strategy if updating existing production

# Gradual traffic shift (if using Istio):
kubectl patch vs yawl-engine -n yawl-prod --type merge \
  -p '{"spec":{"http":[{"route":[{"destination":{"host":"old-engine.production.svc.cluster.local"},"weight":100},{"destination":{"host":"yawl-engine.yawl-prod.svc.cluster.local"},"weight":0}]}]}}'

# Wait 30 min, then:
for weight in 10 25 50 100; do
  kubectl patch vs yawl-engine -n yawl-prod --type merge \
    -p "{\"spec\":{\"http\":[{\"route\":[{\"destination\":{\"host\":\"old-engine.production.svc.cluster.local\"},\"weight\":$((100-weight))},{\"destination\":{\"host\":\"yawl-engine.yawl-prod.svc.cluster.local\"},\"weight\":$weight}]}]}}"
  sleep 1800  # 30 min
done

# Decommission old engine
kubectl delete deployment old-engine -n production
```

---

## Monitoring Dashboard

**Open Grafana** (see dashboard in main guide):
```bash
# Port forward
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80 &

# Open browser: http://localhost:3000
# User: admin
# Password: (get from secret)
kubectl get secret -n monitoring prometheus-grafana -o jsonpath="{.data.admin-password}" | base64 -d
```

---

## Troubleshooting

See `/home/user/yawl/.claude/1M_AGENT_PRODUCTION_GUIDE.md` Section 5 for:
- Heartbeat timeout storms
- Marketplace query latency spikes
- Discovery backoff issues
- GC pause issues
- Network saturation

---

## Success Criteria

- [ ] 500+ pods running in yawl-prod
- [ ] Case creation latency p99 < 500ms
- [ ] Case creation throughput > 50K/sec
- [ ] Database replication lag < 100ms
- [ ] Zero data loss during chaos test
- [ ] All Prometheus alerts green
- [ ] Grafana dashboards showing metrics

**Once all checked**: Production deployment complete!

---

**Quick Help**:
```bash
# Check status
watch -n5 'kubectl get deployment -n yawl-prod yawl-engine && kubectl get hpa -n yawl-prod yawl-engine'

# Tail logs
kubectl logs -n yawl-prod -l app.kubernetes.io/name=yawl-engine --tail=100 -f

# Port forward Prometheus
kubectl port-forward -n monitoring svc/prometheus 9090:9090

# Delete entire deployment (if needed)
helm uninstall yawl-prod -n yawl-prod
```

For detailed procedures, see: `/home/user/yawl/.claude/1M_AGENT_PRODUCTION_GUIDE.md`
