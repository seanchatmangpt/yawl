# YAWL v6.0.0 – Production Deployment Runbook

**Document type**: Operational Runbook  
**Version**: 6.0.0  
**Last updated**: 2026-02-17  
**Owner**: Platform Engineering  
**Review cadence**: Before each major release  

---

## Table of Contents

1. [Pre-Deployment Validation Gates](#1-pre-deployment-validation-gates)
2. [Infrastructure Prerequisites](#2-infrastructure-prerequisites)
3. [Build Verification](#3-build-verification)
4. [Container Build and Security Scan](#4-container-build-and-security-scan)
5. [Helm Deployment](#5-helm-deployment)
6. [Post-Deployment Verification](#6-post-deployment-verification)
7. [Health Check Validation](#7-health-check-validation)
8. [Performance Baseline Verification](#8-performance-baseline-verification)
9. [Rollback Procedures](#9-rollback-procedures)
10. [Operational Checklists](#10-operational-checklists)

---

## 1. Pre-Deployment Validation Gates

All gates must pass before deployment proceeds. Any failure triggers an automatic halt.

### Gate 1: Build Verification

```bash
# Must complete with 0 compilation errors
mvn clean compile --batch-mode --no-transfer-progress
echo "Exit: $?"   # Must be 0
```

### Gate 2: Test Verification

```bash
# All tests must pass – zero failures, zero errors
mvn clean test --batch-mode --no-transfer-progress
echo "Exit: $?"   # Must be 0

# Verify test report
grep -c '<failure' target/surefire-reports/*.xml && echo "FAIL: test failures found" || echo "PASS: no test failures"
grep -c '<error'   target/surefire-reports/*.xml && echo "FAIL: test errors found"   || echo "PASS: no test errors"
```

### Gate 3: HYPER_STANDARDS Compliance

```bash
# Zero violations allowed
TODO_COUNT=$(grep -rn "TODO\|FIXME\|XXX\|HACK" src/ --include="*.java" | wc -l)
MOCK_COUNT=$(grep -rn "mock\|stub\|fake"        src/ --include="*.java" | wc -l)
echo "TODO/FIXME count: ${TODO_COUNT}  (must be 0)"
echo "Mock/stub count:  ${MOCK_COUNT}  (must be 0)"
[ "${TODO_COUNT}" -eq 0 ] && [ "${MOCK_COUNT}" -eq 0 ] && echo "PASS" || echo "FAIL"
```

### Gate 4: Security Scan

```bash
# Trivy vulnerability scan – CRITICAL = block, HIGH = block
trivy image \
    --config security/container-security/trivy-scan.yaml \
    --exit-code 1 \
    --severity CRITICAL,HIGH \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0
echo "Exit: $?"   # Must be 0

# Secret scan
trivy fs --scanners secret --exit-code 1 .
echo "Exit: $?"   # Must be 0
```

### Gate 5: Helm Chart Validation

```bash
# Lint the chart
helm lint /home/user/yawl/helm/yawl --strict

# Dry-run render (detect template errors without cluster access)
helm template yawl-test /home/user/yawl/helm/yawl \
    --set secrets.database.password=test \
    --set postgresql.auth.postgresPassword=test \
    > /dev/null
echo "Exit: $?"   # Must be 0
```

---

## 2. Infrastructure Prerequisites

### Kubernetes Cluster Requirements

| Requirement | Minimum | Recommended |
|---|---|---|
| Kubernetes version | 1.27 | 1.29 |
| Worker nodes | 3 | 5+ |
| Node CPU | 4 vCPU | 8 vCPU |
| Node Memory | 16 GB | 32 GB |
| Storage class | block/RWO | SSD/NVMe RWO |
| Ingress controller | nginx | nginx or istio-gateway |
| Cert-manager | 1.13+ | latest |
| Prometheus Operator | 0.70+ | latest |
| Kyverno | 1.11+ | latest |

### Required Tools (deploying workstation)

```bash
# Verify all tools are installed and at minimum version
kubectl version --client
helm version        # >= 3.13
kustomize version   # >= 5.0
cosign version      # >= 2.2
trivy --version     # >= 0.50
jq --version
yq --version
```

### Namespace Creation

```bash
kubectl apply -f /home/user/yawl/k8s/base/namespace.yaml

# Label namespace for Istio injection (if using Istio)
kubectl label namespace yawl istio-injection=enabled

# Verify
kubectl get namespace yawl -o yaml | grep -E "istio-injection|name"
```

### Secrets Setup

```bash
# Create database secret (never commit passwords to Git)
kubectl create secret generic yawl-db-credentials \
    --namespace yawl \
    --from-literal=password="${YAWL_DB_PASSWORD}" \
    --from-literal=postgres-password="${POSTGRES_PASSWORD}" \
    --dry-run=client -o yaml | kubectl apply -f -

# Create API keys secret
kubectl create secret generic yawl-api-keys \
    --namespace yawl \
    --from-literal=engineApiKey="${YAWL_ENGINE_API_KEY}" \
    --from-literal=resourceServiceKey="${YAWL_RESOURCE_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -

# Create TLS secret (or let cert-manager provision it)
kubectl create secret tls yawl-tls-secret \
    --namespace yawl \
    --cert=tls.crt \
    --key=tls.key \
    --dry-run=client -o yaml | kubectl apply -f -

# Verify secrets exist (never print values)
kubectl get secrets -n yawl
```

### Environment Variables Required for Production

```bash
# All must be set – none may be hardcoded in YAML
: "${YAWL_ENGINE_URL:?Required}"
: "${YAWL_USERNAME:?Required}"
: "${YAWL_PASSWORD:?Required}"
: "${ZHIPU_API_KEY:?Required if using Z.AI integrations}"
: "${DATABASE_URL:?Required}"
: "${DATABASE_PASSWORD:?Required}"
```

---

## 3. Build Verification

### Full Build with Tests

```bash
cd /home/user/yawl

# Compile + test + package (complete verification)
mvn clean package --batch-mode --no-transfer-progress

# Verify WAR/JAR artifacts exist
ls -lh target/*.jar 2>/dev/null || ls -lh */target/*.jar 2>/dev/null
echo "Build artifacts verified"
```

### Schema Validation

```bash
# Validate YAWL specifications against schema
for spec in exampleSpecs/*.yawl; do
    xmllint --schema schema/YAWL_Schema4.0.xsd "${spec}" --noout 2>&1 \
        && echo "PASS: ${spec}" \
        || echo "FAIL: ${spec}"
done
```

---

## 4. Container Build and Security Scan

### Build the Production Image

```bash
# Build with full provenance metadata
docker build \
    -f Dockerfile.v6 \
    --target runtime \
    --build-arg VERSION=6.0.0 \
    --build-arg BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ) \
    --build-arg VCS_REF=$(git rev-parse --short HEAD) \
    --build-arg VCS_URL=https://github.com/yawlfoundation/yawl \
    -t ghcr.io/yawlfoundation/yawl/engine:6.0.0 \
    -t ghcr.io/yawlfoundation/yawl/engine:latest \
    .

# Verify image was built and inspect OCI labels
docker inspect ghcr.io/yawlfoundation/yawl/engine:6.0.0 \
    --format '{{json .Config.Labels}}' | jq .

# Confirm non-root user
docker inspect ghcr.io/yawlfoundation/yawl/engine:6.0.0 \
    --format '{{.Config.User}}'
# Expected: yawl (UID 10001)
```

### Security Scan

```bash
# Full vulnerability scan (must return exit 0)
trivy image \
    --config security/container-security/trivy-scan.yaml \
    --exit-code 1 \
    --severity CRITICAL,HIGH \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0

# Generate CycloneDX SBOM
mkdir -p sbom
trivy image \
    --format cyclonedx \
    --output sbom/yawl-engine-6.0.0-container.cdx.json \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0

# Generate SPDX SBOM
trivy image \
    --format spdx-json \
    --output sbom/yawl-engine-6.0.0-container.spdx.json \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0

echo "SBOM files:"
ls -lh sbom/
```

### Sign Image

```bash
# Push to registry first
docker push ghcr.io/yawlfoundation/yawl/engine:6.0.0

# Sign with cosign (keyless – recommended)
COSIGN_EXPERIMENTAL=1 cosign sign --yes \
    --annotations "org.opencontainers.image.version=6.0.0" \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0

# Attach SBOM attestation
COSIGN_EXPERIMENTAL=1 cosign attest --yes \
    --predicate sbom/yawl-engine-6.0.0-container.cdx.json \
    --type cyclonedx \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0

# Verify
COSIGN_EXPERIMENTAL=1 cosign verify \
    --certificate-identity-regexp "https://github.com/yawlfoundation/yawl.*" \
    --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
    ghcr.io/yawlfoundation/yawl/engine:6.0.0 \
    | jq .
```

---

## 5. Helm Deployment

### New Installation

```bash
RELEASE_NAME=yawl
NAMESPACE=yawl
CHART_DIR=/home/user/yawl/helm/yawl

# Add Helm dependencies
helm dependency update "${CHART_DIR}"

# Install with production values
helm install "${RELEASE_NAME}" "${CHART_DIR}" \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    --values "${CHART_DIR}/values.yaml" \
    --set secrets.database.password="${YAWL_DB_PASSWORD}" \
    --set postgresql.auth.postgresPassword="${POSTGRES_PASSWORD}" \
    --set secrets.apiKeys.engineApiKey="${YAWL_ENGINE_API_KEY}" \
    --timeout 10m \
    --wait \
    --atomic \
    --debug

echo "Installation exit: $?"
```

### Upgrade Deployment

```bash
# Perform rolling upgrade with atomic rollback on failure
helm upgrade "${RELEASE_NAME}" "${CHART_DIR}" \
    --namespace "${NAMESPACE}" \
    --values "${CHART_DIR}/values.yaml" \
    --set image.tag=6.0.0 \
    --set secrets.database.password="${YAWL_DB_PASSWORD}" \
    --set postgresql.auth.postgresPassword="${POSTGRES_PASSWORD}" \
    --timeout 10m \
    --wait \
    --atomic \
    --cleanup-on-fail

echo "Upgrade exit: $?"
```

### Canary Deployment (Istio)

```bash
# Step 1: Deploy canary with 5% traffic
helm upgrade "${RELEASE_NAME}" "${CHART_DIR}" \
    --namespace "${NAMESPACE}" \
    --set istio.enabled=true \
    --set istio.canary.enabled=true \
    --set istio.canary.weight=5 \
    --set istio.canary.stableWeight=95 \
    --wait

# Step 2: Monitor error rate for 15 minutes
# kubectl top pods -n yawl
# curl http://engine:9090/actuator/prometheus | grep yawl_engine_errors

# Step 3: Graduate to 100% if metrics are healthy
helm upgrade "${RELEASE_NAME}" "${CHART_DIR}" \
    --namespace "${NAMESPACE}" \
    --set istio.canary.weight=100 \
    --set istio.canary.stableWeight=0 \
    --wait

# Step 4: Disable canary routing once all traffic on new version
helm upgrade "${RELEASE_NAME}" "${CHART_DIR}" \
    --namespace "${NAMESPACE}" \
    --set istio.canary.enabled=false \
    --wait
```

---

## 6. Post-Deployment Verification

```bash
# Wait for all pods to be Ready
kubectl rollout status deployment/yawl-engine -n yawl --timeout=5m
kubectl rollout status deployment/yawl-resource-service -n yawl --timeout=5m
kubectl rollout status statefulset/yawl-postgresql -n yawl --timeout=5m

# Verify pod count matches expected replicas
kubectl get pods -n yawl -l app.kubernetes.io/part-of=yawl

# Verify no pods are in CrashLoopBackOff or Error state
kubectl get pods -n yawl | grep -vE "Running|Completed" | grep -v "NAME"

# Check engine logs for startup errors
kubectl logs -n yawl -l app.kubernetes.io/component=engine --tail=50 | grep -E "ERROR|WARN|Started"

# Verify YAWL engine Interface A endpoint
ENGINE_POD=$(kubectl get pods -n yawl -l app.kubernetes.io/component=engine -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n yawl "${ENGINE_POD}" -- \
    curl -sf http://localhost:8080/yawl/ia || echo "Interface A not responding"

# Verify engine Interface B endpoint
kubectl exec -n yawl "${ENGINE_POD}" -- \
    curl -sf http://localhost:8080/yawl/ib || echo "Interface B not responding"

# Verify actuator health
kubectl exec -n yawl "${ENGINE_POD}" -- \
    curl -sf http://localhost:9090/actuator/health | jq .
```

---

## 7. Health Check Validation

```bash
ENGINE_URL="https://yawl.example.com"   # Replace with actual URL

# Interface A (design-time) – must return 200
curl -sf "${ENGINE_URL}/yawl/ia" -o /dev/null -w "Interface A: %{http_code}\n"

# Interface B (runtime) – must return 200
curl -sf "${ENGINE_URL}/yawl/ib" -o /dev/null -w "Interface B: %{http_code}\n"

# Kubernetes readiness (management port via port-forward for internal check)
kubectl port-forward -n yawl svc/yawl-engine 9090:9090 &
PF_PID=$!
sleep 2
curl -sf http://localhost:9090/actuator/health/readiness | jq .status
kill ${PF_PID}

# Check all dependent services accessible
kubectl exec -n yawl "${ENGINE_POD}" -- \
    sh -c "nc -z -w3 yawl-postgresql 5432 && echo 'DB: OK' || echo 'DB: FAIL'"
kubectl exec -n yawl "${ENGINE_POD}" -- \
    sh -c "nc -z -w3 yawl-redis-master 6379 && echo 'Redis: OK' || echo 'Redis: FAIL'"

# Database connectivity verified
kubectl exec -n yawl "${ENGINE_POD}" -- \
    curl -sf http://localhost:9090/actuator/health/db | jq .
```

---

## 8. Performance Baseline Verification

Performance targets (from PERFORMANCE_BASELINE.md):

| Metric | Threshold | Action if breached |
|---|---|---|
| Engine startup time | < 60 seconds | Investigate JVM / classpath issues |
| Case creation latency p99 | < 500 ms | Scale horizontally or tune DB |
| Work-item checkout latency p99 | < 200 ms | Check connection pool |
| DB query optimization | Verified | Review Hibernate statistics |
| Connection pool configured | min:5 max:20 | Check HikariCP settings |

```bash
# Verify startup time from logs
kubectl logs -n yawl -l app.kubernetes.io/component=engine \
    | grep -E "Started.*seconds" | tail -1

# Run performance smoke tests
kubectl port-forward -n yawl svc/yawl-engine 8080:8080 &
PF_PID=$!
sleep 2

# Case creation latency (average of 10 requests)
for i in $(seq 1 10); do
    curl -sf -w "%{time_total}\n" -o /dev/null \
        -X POST http://localhost:8080/yawl/ia \
        -H "Content-Type: application/xml" \
        -d '<launchCase specID="test"/>'
done | awk '{ sum+=$1; count++ } END { printf "Avg case creation: %.3fs (threshold: 0.5s)\n", sum/count }'

kill ${PF_PID}

# Check Prometheus metrics for p99 latency
kubectl port-forward -n yawl svc/yawl-engine 9090:9090 &
PF_PID=$!
sleep 2
curl -sf "http://localhost:9090/actuator/prometheus" \
    | grep -E "yawl_case_creation|hikaricp_connections"
kill ${PF_PID}
```

---

## 9. Rollback Procedures

### Automatic Rollback (Helm atomic)

The `--atomic` flag in the Helm upgrade command automatically rolls back on failure.

### Manual Rollback

```bash
# View Helm release history
helm history yawl -n yawl

# Rollback to previous release
helm rollback yawl -n yawl --wait --timeout 5m

# Rollback to specific revision (from history)
helm rollback yawl 3 -n yawl --wait --timeout 5m

# Verify rollback succeeded
kubectl get pods -n yawl
helm status yawl -n yawl
```

### Rollback Criteria (MANDATORY triggers)

| Trigger | Action |
|---|---|
| Any test failures detected | IMMEDIATE ROLLBACK |
| HYPER_STANDARDS violations | ROLLBACK + remediate |
| Security vulnerability (CRITICAL) | ROLLBACK + embargo |
| Performance degradation > 20% | ROLLBACK + investigate |
| Health checks failing > 5 min | ROLLBACK |
| Engine startup time > 120 seconds | ROLLBACK |
| Database connection pool exhausted | ROLLBACK |
| OOM errors in pod logs | ROLLBACK + adjust limits |

### Emergency Stop

```bash
# Scale down all YAWL pods immediately (last resort)
kubectl scale deployment \
    yawl-engine \
    yawl-resource-service \
    yawl-worklet-service \
    yawl-monitor-service \
    --replicas=0 -n yawl

# Restore from backup if database was corrupted
# See database backup/restore procedures in database/connection-pooling/README.md
```

---

## 10. Operational Checklists

### Pre-Deployment Sign-Off Checklist

- [ ] `mvn clean compile` passes (0 errors)
- [ ] `mvn clean test` passes (0 failures, 0 errors)
- [ ] HYPER_STANDARDS compliance: 0 TODO/FIXME, 0 mock/stub
- [ ] Schema validation: all YAWL specs valid
- [ ] Trivy scan: 0 CRITICAL, 0 HIGH vulnerabilities
- [ ] Trivy secret scan: 0 secrets detected
- [ ] Image signed with cosign
- [ ] SBOM generated (CycloneDX + SPDX)
- [ ] SBOM attached to OCI registry
- [ ] Kyverno policies validated (dry-run)
- [ ] Helm chart linted (0 errors)
- [ ] Helm template render validated (0 errors)
- [ ] Database migration scripts validated (dry-run)
- [ ] All required secrets created in target namespace
- [ ] Environment variables set (no hardcoded credentials)
- [ ] Rollback plan documented and tested
- [ ] Monitoring alerts configured and tested
- [ ] On-call rotation notified

### Post-Deployment Sign-Off Checklist

- [ ] All pods Running / Completed (0 CrashLoopBackOff)
- [ ] Interface A `/yawl/ia` responds 200
- [ ] Interface B `/yawl/ib` responds 200
- [ ] `/actuator/health/liveness` returns `{"status":"UP"}`
- [ ] `/actuator/health/readiness` returns `{"status":"UP"}`
- [ ] Database connectivity verified via actuator
- [ ] Prometheus scraping active (metrics visible in Grafana)
- [ ] HPA configured and working (kubectl get hpa -n yawl)
- [ ] PodDisruptionBudget in place
- [ ] NetworkPolicy applied
- [ ] Engine startup time < 60 seconds (verified from logs)
- [ ] Case creation p99 latency < 500ms
- [ ] Work-item checkout p99 latency < 200ms
- [ ] No ERROR-level messages in engine logs (first 5 minutes)
- [ ] Backup CronJob scheduled and verified

### Contacts and Escalation

| Role | Contact | When to escalate |
|---|---|---|
| Platform Lead | platform-lead@example.com | Deployment failures |
| Security | security@example.com | Any CRITICAL CVE |
| Database Admin | dba@example.com | Database corruption/loss |
| On-call | PagerDuty #yawl-oncall | Production SLO breach |

---

## Appendix: Key File Locations

| Artifact | Path |
|---|---|
| Production Dockerfile | `/home/user/yawl/Dockerfile.v6` |
| Entrypoint script | `/home/user/yawl/docker/entrypoint.sh` |
| Health check script | `/home/user/yawl/docker/healthcheck.sh` |
| Helm chart | `/home/user/yawl/helm/yawl/` |
| Engine StatefulSet | `/home/user/yawl/helm/yawl/templates/statefulset/engine-statefulset.yaml` |
| Istio service mesh | `/home/user/yawl/helm/yawl/templates/istio/service-mesh.yaml` |
| Prometheus monitoring | `/home/user/yawl/helm/yawl/templates/monitoring/prometheus.yaml` |
| PostgreSQL StatefulSet | `/home/user/yawl/helm/yawl/charts/postgresql/templates/statefulset.yaml` |
| Trivy scan config | `/home/user/yawl/security/container-security/trivy-scan.yaml` |
| Kyverno image signing | `/home/user/yawl/security/kubernetes-security/kyverno-policies/image-signing.yaml` |
| Kyverno pod security | `/home/user/yawl/security/kubernetes-security/kyverno-policies/pod-security.yaml` |
| SBOM generation guide | `/home/user/yawl/sbom/sbom-generation.md` |
| CI security pipeline | `/home/user/yawl/.github/workflows/security-scanning.yml` |
| K8s base manifests | `/home/user/yawl/k8s/base/` |
| Database migrations | `/home/user/yawl/database/migrations/` |
