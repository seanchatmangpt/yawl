# YAWL v6.0.0 Deployment Documentation Enhancements - Implementation Guide

**Status:** Ready for Implementation  
**Date:** 2026-02-20  
**Priority:** CRITICAL (Phase 1), HIGH (Phase 2)  

---

## Quick Reference: Critical Fixes

All CRITICAL fixes should be completed within 2-3 days to enable safe production deployment.

### Fix 1: Health Check Endpoint Updates

**Files to Update:**
- `/home/user/yawl/docs/DEPLOY-DOCKER.md` (Lines 92, 262, 667)
- `/home/user/yawl/docs/DEPLOYMENT-READINESS-CHECKLIST.md` (Line 318)

**Old Endpoint:**
```bash
/yawl/api/ib/workitems
```

**New Endpoint:**
```bash
/actuator/health/liveness
```

**Example Fix:**
```diff
# Before
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/yawl/api/ib/workitems || exit 1

# After
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health/liveness || exit 1
```

**Testing:**
```bash
# Verify endpoint exists
curl -s http://localhost:8080/actuator/health/liveness | jq .

# Expected output:
# {
#   "status": "UP",
#   "components": { ... }
# }
```

---

### Fix 2: Build Command Updates (Ant -> Maven)

**Files to Update:**
- `/home/user/yawl/docs/DEPLOY-DOCKER.md` (Lines 194-196)
- `/home/user/yawl/docs/DEPLOY-TOMCAT.md` (if applicable)
- `/home/user/yawl/docs/DEPLOY-JETTY.md` (if applicable)

**Old Commands:**
```bash
ant clean
ant buildAll
```

**New Commands:**
```bash
# Build entire project with parallel compilation
mvn -T 1.5C clean package -DskipTests

# OR build specific module (faster)
mvn -T 1.5C clean package -pl yawl-engine -am -DskipTests

# OR with tests
mvn -T 1.5C clean verify
```

**Context to Add:**
```
## Build System: Maven vs. Ant

YAWL v6.0.0 uses **Maven 3.9+** as the primary build system.
The legacy Ant build system is deprecated and no longer maintained.

### Quick Build Reference

| Use Case | Command | Time |
|----------|---------|------|
| Fast build (no tests) | `mvn -T 1.5C clean package -DskipTests` | ~2min |
| Fast single module | `mvn -T 1.5C clean package -pl yawl-engine -am -DskipTests` | ~30sec |
| Full validation | `mvn -T 1.5C clean verify` | ~5min |
| Build specific module | `mvn -T 1.5C clean package -pl MODULE_NAME -am` | ~1min |

**Flags:**
- `-T 1.5C` = parallel build (1.5x CPU cores)
- `-DskipTests` = skip unit tests (faster, less safe)
- `-pl` = build only specified modules
- `-am` = include required dependencies
```

---

### Fix 3: Dockerfile Path Updates

**Files to Update:**
- `/home/user/yawl/docs/DEPLOY-DOCKER.md` (Lines 70, 105, 129)
- `/home/user/yawl/docs/DEPLOYMENT_PLAYBOOKS.md` (if references exist)

**Old Paths:**
```
containerization/Dockerfile.engine
containerization/Dockerfile.resourceService
containerization/Dockerfile.workletService
```

**New Paths:**
```
docker/production/Dockerfile.engine
docker/production/Dockerfile.resourceService  # (if exists)
docker/production/Dockerfile.workletService   # (if exists)
```

**Context to Add:**
```
## Dockerfile Locations

YAWL v6.0.0 reorganized Dockerfiles by purpose and environment:

| Purpose | Location | Use |
|---------|----------|-----|
| Development | `docker/development/Dockerfile.dev` | Local development |
| Production Engine | `docker/production/Dockerfile.engine` | Production deployment |
| Production MCP/A2A | `docker/production/Dockerfile.mcp-a2a-app` | Autonomous agents |
| Security Testing | `docker/security-tester/Dockerfile` | Security validation |

**Note:** Old `containerization/` directory has been deprecated.
If you have references to it, update to `docker/production/`.
```

---

### Fix 4: Image Version and Registry Updates

**Files to Update:**
- `/home/user/yawl/docs/DEPLOY-DOCKER.md` (Line 435, 200)
- `/home/user/yawl/docs/KUBERNETES_DEPLOYMENT.md` (create if needed)

**Old Image References:**
```yaml
image: yawl:5.2-engine
image: yawl:5.2-resourceService
```

**New Image References:**
```yaml
# For local builds
image: yawl/engine:6.0.0-alpha
image: yawl/resource-service:6.0.0-alpha

# For registry deployments (recommended)
image: ghcr.io/yawlfoundation/yawl/engine:6.0.0-alpha
image: ghcr.io/yawlfoundation/yawl/resource-service:6.0.0-alpha
```

**Context to Add:**
```
## Image Versioning and Registry

### Version Scheme

YAWL uses semantic versioning with the following format:
```
MAJOR.MINOR.PATCH-VARIANT
Example: 6.0.0-alpha, 6.0.1, 6.1.0-rc1
```

**Current Version:** 6.0.0-alpha
- Major update: v6 (major architecture changes)
- Minor updates: vX.1, vX.2, etc. (new features)
- Patches: vX.Y.1, vX.Y.2, etc. (bug fixes)

### Image Registries

| Registry | URL | Use |
|----------|-----|-----|
| GitHub Container Registry | `ghcr.io/yawlfoundation/` | Production (recommended) |
| Docker Hub | (not yet published) | Future use |
| Local | `yawl/engine:TAG` | Local development |

### Pulling Images

```bash
# From GitHub Container Registry (recommended for production)
docker pull ghcr.io/yawlfoundation/yawl/engine:6.0.0-alpha

# Local build
docker build -t yawl/engine:6.0.0-alpha \
  -f docker/production/Dockerfile.engine .
```
```

---

### Fix 5: Environment Variable Configuration

**Files to Create/Update:**
- Create: `/home/user/yawl/docs/ENVIRONMENT_VARIABLES_REFERENCE.md`
- Update: `/home/user/yawl/docs/DEPLOY-DOCKER.md` (add reference)

**New Document: ENVIRONMENT_VARIABLES_REFERENCE.md**

```markdown
# YAWL v6.0.0 Environment Variables Reference

## Configuration Approaches

YAWL v6.0.0 uses a **hierarchy** of configuration:

1. **Hardcoded defaults** (lowest priority)
2. **application.properties** (file-based)
3. **application-{profile}.properties** (profile-specific)
4. **Environment variables** (recommended)
5. **Command-line arguments** (highest priority)

## Database Configuration

| Variable | Type | Required | Default | Example |
|----------|------|----------|---------|---------|
| `DB_TYPE` | string | Yes | `h2` | `postgres`, `mysql`, `h2` |
| `DB_HOST` | string | Yes | `localhost` | `db.example.com` |
| `DB_PORT` | int | Yes | varies | `5432` (postgres), `3306` (mysql) |
| `DB_NAME` | string | Yes | `yawl` | `yawl_prod` |
| `DB_USER` | string | Yes | - | `yawl_user` |
| `DB_PASSWORD` | string | Yes (sensitive) | - | See Secrets section |
| `DB_PASSWORD_FILE` | string | Alternative | - | `/run/secrets/db_password` |

## Secrets Management

### Using Environment Variables (NOT RECOMMENDED for production)

```bash
export DB_PASSWORD="your-secure-password-here"
```

### Using Secret Files (RECOMMENDED)

```bash
# Docker
docker run -e DB_PASSWORD_FILE=/run/secrets/db_password \
  -v /path/to/secret:/run/secrets/db_password:ro \
  yawl/engine:6.0.0-alpha

# Kubernetes
kubectl create secret generic yawl-secrets \
  --from-literal=DB_PASSWORD=your-password \
  -n yawl
```

## Java Configuration (JAVA_OPTS)

| Variable | Default | Purpose | Example |
|----------|---------|---------|---------|
| `JAVA_OPTS` | See below | JVM configuration | `-Xms1g -Xmx4g -XX:+UseZGC` |

### Default JAVA_OPTS (Production)

```
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
-Dfile.encoding=UTF-8
-Djdk.virtualThreadScheduler.parallelism=200
-Djdk.virtualThreadScheduler.maxPoolSize=256
-Djdk.tracePinnedThreads=short
```

## Monitoring and Observability

| Variable | Default | Example |
|----------|---------|---------|
| `MANAGEMENT_HEALTH_PROBES_ENABLED` | `true` | Health endpoints enabled |
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `when-authorized` | Hide by default, show if authenticated |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info,metrics,prometheus` | Exposed actuator endpoints |
| `OTEL_SERVICE_NAME` | `yawl-engine` | Service name in traces |
| `OTEL_TRACES_EXPORTER` | `otlp` | OpenTelemetry trace exporter |
| `OTEL_METRICS_EXPORTER` | `prometheus` | OpenTelemetry metrics exporter |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OpenTelemetry collector endpoint |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logger level |
| `LOGGING_LEVEL_ORG_YAWLFOUNDATION` | `INFO` | YAWL logger level |

## Example Configurations

### Development (H2 In-Memory Database)

```bash
export DB_TYPE=h2
export SPRING_PROFILES_ACTIVE=development
export LOGGING_LEVEL_ROOT=DEBUG
export LOGGING_LEVEL_ORG_YAWLFOUNDATION=DEBUG
```

### Staging (PostgreSQL)

```bash
export DB_TYPE=postgres
export DB_HOST=postgres.staging.svc.cluster.local
export DB_PORT=5432
export DB_NAME=yawl_staging
export DB_USER=yawl_user
export DB_PASSWORD_FILE=/run/secrets/db_password
export SPRING_PROFILES_ACTIVE=production
export LOGGING_LEVEL_ROOT=INFO
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4317
```

### Production (PostgreSQL with Replication)

```bash
export DB_TYPE=postgres
export DB_HOST=postgres-primary.yawl.svc.cluster.local
export DB_PORT=5432
export DB_NAME=yawl_prod
export DB_USER=yawl_user
export DB_PASSWORD_FILE=/run/secrets/db_password
export SPRING_PROFILES_ACTIVE=production,kubernetes
export LOGGING_LEVEL_ROOT=WARN
export JAVA_OPTS="-Xms2g -Xmx8g -XX:+UseZGC -XX:+ZGenerational"
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4317
export MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=when-authorized
```
```

---

### Fix 6: Secret Management Documentation

**Files to Create/Update:**
- Create: `/home/user/yawl/docs/SECRETS_MANAGEMENT.md`
- Update: `/home/user/yawl/docs/DEPLOYMENT-READINESS-CHECKLIST.md` (add reference)

**New Document: SECRETS_MANAGEMENT.md**

```markdown
# YAWL v6.0.0 Secrets Management Guide

## Overview

YAWL v6.0.0 uses a multi-layered secrets strategy for different deployment environments.

## Secrets Hierarchy (Docker Compose)

### What Constitutes a Secret?

| Item | Classification | Example |
|------|----------------|---------|
| Database password | SECRET | `yawl_secure_password_12345` |
| JWT signing key | SECRET | `openssl rand -base64 64` |
| API keys | SECRET | Authentication tokens |
| TLS certificates | SECRET | Private keys (.key files) |
| Encryption keys | SECRET | Data encryption material |
| Username/Email | CONFIG | Database username |
| Service names | CONFIG | `postgres`, `engine` |
| Port numbers | CONFIG | `5432`, `8080` |

## Docker Compose Secrets

### Setup

```bash
# Create secrets directory
mkdir -p secrets/

# Generate secrets
openssl rand -base64 32 > secrets/db_password.txt
openssl rand -base64 64 > secrets/jwt_signing_key.txt
openssl rand -base64 32 > secrets/grafana_admin_password

# Set permissions (secrets readable only by Docker daemon)
chmod 600 secrets/*.txt

# NEVER commit secrets to Git!
echo "secrets/" >> .gitignore
```

### Using in docker-compose.prod.yml

```yaml
secrets:
  db_password:
    file: ./secrets/db_password.txt

services:
  postgres:
    secrets:
      - db_password
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
```

## Kubernetes Secrets

### Creating Secrets

```bash
# Method 1: From literal values
kubectl create secret generic yawl-secrets \
  --from-literal=DB_PASSWORD=your-password \
  --from-literal=JWT_SIGNING_KEY=$(openssl rand -base64 64) \
  -n yawl

# Method 2: From files
kubectl create secret generic yawl-secrets \
  --from-file=db-password=secrets/db_password.txt \
  --from-file=jwt-key=secrets/jwt_signing_key.txt \
  -n yawl

# Method 3: From env file
kubectl create secret generic yawl-secrets \
  --from-env-file=secrets.env \
  -n yawl

# View secrets (will show base64 encoded)
kubectl get secrets -n yawl
kubectl get secret yawl-secrets -n yawl -o yaml
```

### Decoding Secrets

```bash
# Get encoded secret
kubectl get secret yawl-secrets -n yawl -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
```

### Using Secrets in Pods

```yaml
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: yawl-secrets
        key: DB_PASSWORD

# OR: Load all secrets as environment variables
envFrom:
  - secretRef:
      name: yawl-secrets
```

## Production Secret Management Best Practices

### Option 1: External Secrets Operator (Recommended)

```yaml
# Install ESO
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace

# Create SecretStore
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: vault-secret-store
  namespace: yawl
spec:
  provider:
    vault:
      server: "https://vault.example.com:8200"
      path: "secret"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "yawl-role"

# Create ExternalSecret
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-secrets
  namespace: yawl
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-secret-store
    kind: SecretStore
  target:
    name: yawl-secrets
    creationPolicy: Owner
  data:
    - secretKey: DB_PASSWORD
      remoteRef:
        key: yawl/db_password
```

### Option 2: Sealed Secrets

```bash
# Install Sealed Secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Seal a secret
echo -n 'my-password' | kubectl create secret generic mysecret \
  --dry-run=client \
  --from-file=password=/dev/stdin \
  -o yaml | kubeseal -f - > mysealedsecret.yaml

# Apply sealed secret
kubectl apply -f mysealedsecret.yaml
```

### Option 3: AWS Secrets Manager

```bash
# Store secret
aws secretsmanager create-secret \
  --name yawl/db-password \
  --secret-string "your-secure-password"

# Retrieve in pod via IAM role
# (requires IRSA configuration)
```

## Secret Rotation

### Kubernetes Secret Rotation

```bash
# Step 1: Update secret
kubectl patch secret yawl-secrets -n yawl \
  --type='json' \
  -p='[{"op": "replace", "path": "/data/DB_PASSWORD", "value":"'$(echo -n 'new-password' | base64)'"}]'

# Step 2: Restart pods (forces new connections with new secret)
kubectl rollout restart deployment/yawl-engine -n yawl

# Step 3: Verify
kubectl logs -f deployment/yawl-engine -n yawl | grep "database connection"
```

## Audit and Compliance

### Never Do This:

```bash
# ❌ DON'T commit secrets
git add secrets.env
git commit -m "Add secrets" # NEVER DO THIS!

# ❌ DON'T echo secrets
echo $DB_PASSWORD

# ❌ DON'T put secrets in Dockerfiles
ENV DB_PASSWORD=mysecret # WRONG!

# ❌ DON'T hardcode secrets
password = "hardcoded_value" # WRONG!
```

### Do This Instead:

```bash
# ✅ Add to .gitignore
echo "secrets/" >> .gitignore
echo "*.secret" >> .gitignore

# ✅ Use environment variables
export DB_PASSWORD="$(cat secrets/db_password.txt)"

# ✅ Use secret management systems
# - Docker secrets
# - Kubernetes secrets
# - External Secrets Operator
# - HashiCorp Vault
# - AWS Secrets Manager

# ✅ Audit and log
# Who accessed the secret?
# When was it rotated?
# What is the access policy?
```
```

---

## Phase 1 Summary

All 6 critical fixes above should be completed and committed within 2-3 days.

**Total Effort:** ~10 hours  
**Impact:** Enables safe production deployment  
**Risk if Skipped:** HIGH (broken documentation, deployment failures)  

---

## Phase 2: Core Documentation (1 week, ~20 hours)

### 2.1 Complete AWS EKS Deployment Guide

**Location:** `/home/user/yawl/docs/CLOUD_DEPLOYMENT_RUNBOOKS.md`

**Add Section:**
- EKS cluster creation (eksctl)
- RDS PostgreSQL setup
- Secrets Manager integration
- VPC/Security Group configuration
- IAM role setup for IRSA
- Estimated effort: 4 hours

### 2.2 Complete Azure AKS Deployment Guide

**Location:** `/home/user/yawl/docs/CLOUD_DEPLOYMENT_RUNBOOKS.md`

**Add Section:**
- AKS cluster creation (az aks)
- Azure Database for PostgreSQL
- Key Vault integration
- Managed identity configuration
- Network policies
- Estimated effort: 4 hours

### 2.3 Kubernetes Step-by-Step Deployment

**Create:** `/home/user/yawl/docs/KUBERNETES_DEPLOYMENT_GUIDE.md`

**Content:**
- Prerequisites and prerequisites verification
- Namespace creation
- Secret configuration
- ConfigMap setup
- Deployment manifest explanation
- Service configuration
- Verification and health checks
- Estimated effort: 4 hours

### 2.4 Operational Runbooks (8 hours)

Create 8 new runbooks:

1. **STARTUP_RUNBOOK.md** (2h)
   - Start all services in dependency order
   - Health check procedures
   - Verification steps

2. **SHUTDOWN_RUNBOOK.md** (1h)
   - Graceful shutdown of all services
   - Draining in-flight requests
   - Data consistency verification

3. **SCALING_RUNBOOK.md** (2h)
   - Adding new nodes
   - Removing nodes
   - Rebalancing workloads
   - HPA configuration

4. **DATABASE_MIGRATIONS.md** (2h)
   - Migration procedures
   - Testing migrations
   - Rollback strategies
   - Long-running migration handling

5. **DISASTER_RECOVERY.md** (2h)
   - Backup procedures
   - Restore procedures
   - RTO/RPO targets
   - Testing recovery

6. **MONITORING_SETUP.md** (2h)
   - Prometheus configuration
   - Grafana dashboard setup
   - Alert configuration
   - Log aggregation

7. **SECURITY_HARDENING.md** (2h)
   - Security checklist
   - TLS configuration
   - Network policies
   - RBAC setup

8. **TROUBLESHOOTING_DETAILED.md** (2h)
   - Common issues by category
   - Diagnostic procedures
   - Log analysis
   - Resolution steps

---

## Phase 3: Enhancements (1 week, ~16 hours)

Not critical but valuable for operational excellence.

### 3.1 Architecture Diagrams (6h)
- Network topology
- Service dependencies
- Database replication
- Traffic flow

### 3.2 Performance Tuning Guide (6h)
- JVM optimization
- Database tuning
- Container resource limits
- Benchmarking procedures

### 3.3 Troubleshooting Index (4h)
- Cross-document index
- Symptom-to-solution mapping
- Common error codes

---

## Validation Checklist

After completing each phase, verify:

- [ ] All links work (no broken references)
- [ ] Code examples are tested and working
- [ ] Commands can be copy-pasted
- [ ] All file paths are correct
- [ ] All commands use correct tools (Maven not Ant)
- [ ] All endpoints match actual code
- [ ] All image versions match releases
- [ ] Markdown formatting is correct
- [ ] No hardcoded secrets in examples
- [ ] All recommendations are actionable

---

## CI/CD Integration

Consider adding:

```bash
# scripts/validate-docs.sh
#!/bin/bash
set -e

# Check for hardcoded secrets
if grep -r "password.*=" docs/ | grep -v 'PASSWORD_FILE'; then
  echo "ERROR: Hardcoded passwords in documentation"
  exit 1
fi

# Check for deprecated Ant commands
if grep -r "ant " docs/*.md | grep -v "DEPRECATED"; then
  echo "ERROR: Ant commands found in documentation"
  exit 1
fi

# Check for outdated endpoints
if grep -r "yawl/api/ib/workitems" docs/ | grep -v "DEPRECATED"; then
  echo "ERROR: Outdated endpoints in documentation"
  exit 1
fi

echo "Documentation validation passed"
```

---

## Next Steps

1. Review this plan with the team
2. Create PR with Phase 1 fixes
3. Get approval before production deployment
4. Complete Phase 2 before announcing v6.0.0 GA
5. Schedule Phase 3 for post-release sprint

