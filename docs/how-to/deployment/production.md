# YAWL Production Deployment Process

## Overview

This document describes the comprehensive production deployment process for the YAWL Workflow Engine, including validation gates, readiness checks, deployment strategies, and rollback procedures.

**Last Updated:** February 18, 2026
**YAWL Version:** 6.0.0-GA

## Table of Contents

1. [Pre-Deployment Requirements](#pre-deployment-requirements)
2. [Production Readiness Validation](#production-readiness-validation)
3. [Deployment Strategies](#deployment-strategies)
4. [Manual Approval Process](#manual-approval-process)
5. [Deployment Execution](#deployment-execution)
6. [Post-Deployment Validation](#post-deployment-validation)
7. [Monitoring & Observability](#monitoring--observability)
8. [Rollback Procedures](#rollback-procedures)
9. [Incident Response](#incident-response)
10. [Quick Reference](#quick-reference)

---

## Pre-Deployment Requirements

### System Prerequisites

Before deploying to production, ensure the following are in place:

#### 1. Infrastructure Requirements

- **Kubernetes Cluster:** v1.24+ (tested with GKE, EKS, AKS)
- **Container Registry:** Docker-compatible (Docker Hub, ECR, GCR, ACR)
- **Database:** PostgreSQL 12+ or Oracle 21c
- **Load Balancer:** Kubernetes Service with LoadBalancer type
- **Namespace:** `yawl-production` (or configured environment name)

#### 2. Access & Credentials

- GitHub Actions deployment token (`GITHUB_TOKEN`)
- Cloud provider credentials (GCP, AWS, Azure as applicable)
- Kubernetes cluster access (`kubeconfig`)
- Database credentials (encrypted in GitHub Secrets)
- Docker registry credentials
- GPG signing key for artifact verification

#### 3. Configuration Files

Required configuration files must exist in the repository:

```
src/main/resources/
├── application-production.yml        # Production configuration
├── application-production.yaml       # Alternative format
├── db/migration/                     # Database migration scripts
│   ├── V1_0_0__initial_schema.sql
│   ├── V1_0_1__add_columns.sql
│   └── ...
├── logback-spring-production.xml     # Production logging config
└── kubernetes-secrets/               # Base64-encoded secret manifests
    ├── db-credentials.yaml
    ├── api-keys.yaml
    └── encryption-keys.yaml
```

#### 4. Secrets Management

The following GitHub Secrets must be configured:

| Secret Name | Purpose | Required |
|------------|---------|----------|
| `DATABASE_URL` | Production database connection string | Yes |
| `DATABASE_USER` | Database username | Yes |
| `DATABASE_PASSWORD` | Database password (encrypted) | Yes |
| `ENCRYPTION_KEY` | Master encryption key for sensitive data | Yes |
| `API_SIGNING_KEY` | Key for signing JWT tokens | Yes |
| `DOCKER_REGISTRY_URL` | Container registry URL | Yes |
| `DOCKER_REGISTRY_USERNAME` | Registry credentials | Yes |
| `DOCKER_REGISTRY_PASSWORD` | Registry credentials | Yes |
| `KUBECONFIG_PRODUCTION` | Kubernetes cluster config | Yes |
| `GPG_PRIVATE_KEY` | GPG key for artifact signing | No |
| `GPG_KEY_ID` | GPG key identifier | No |
| `SLACK_WEBHOOK_URL` | Slack notifications | No |
| `NEXUS_USER` | Artifact repository credentials | No |
| `NEXUS_PASSWORD` | Artifact repository credentials | No |
| `ARGOCD_TOKEN` | ArgoCD sync token | No |

---

## Production Readiness Validation

### Automated Readiness Checks

The `validate-production-readiness.sh` script performs comprehensive validation:

#### 1. Java Version & Compilation (Stage 1)

- **Java 25+ required** for all production deployments
- **GRPO (Generational Reference Processing Optimization)** recommended
- **Preview features enabled for virtual threads**
- Full Maven compilation with `mvn clean compile`
- Zero compilation errors mandatory

**Check Command:**
```bash
bash ci-cd/scripts/validate-production-readiness.sh production
```

#### 2. Unit Test Coverage (Stage 2)

- **100% test pass rate required**
- All JUnit tests must pass
- JaCoCo coverage minimum:
  - Line coverage: 65%
  - Branch coverage: 55%

**Example Output:**
```
2. UNIT TEST COVERAGE & PASS RATE
----------------------------------------
Running unit tests...
✓ PASSED: Unit Test Pass Rate (100%)
✓ PASSED: Test Execution (487 tests)
✓ PASSED: JaCoCo Coverage Report Generated
```

#### 3. HYPER_STANDARDS Compliance (Stage 3)

**Forbidden Patterns - ZERO TOLERANCE:**

| Pattern | Type | Consequence |
|---------|------|------------|
| `TODO`, `FIXME`, `XXX`, `HACK` | Deferred work markers | Deployment blocked |
| `mockFetch()`, `stubValidation()` | Mock/stub method names | Deployment blocked |
| `class MockService`, `class StubClient` | Mock class names | Deployment blocked |
| Empty method bodies: `public void method() {}` | No-op methods | Deployment blocked |
| `catch (e) { return; }` | Silent fallbacks | Deployment blocked |
| `import org.mockito.*` | Mock imports in production code | Deployment blocked |
| `DUMMY_CONFIG`, `PLACEHOLDER_VALUE` | Placeholder constants | Deployment blocked |
| `if (isTestMode) return mock()` | Conditional mocks | Deployment blocked |

**Example Validation Output:**
```
3. HYPER_STANDARDS COMPLIANCE
----------------------------------------
Checking for deferred work markers...
✓ PASSED: No Deferred Work Markers
✓ PASSED: No Mock/Stub Methods in Production Code
✓ PASSED: No Mock/Stub Classes in Production Code
✓ PASSED: No Empty Method Bodies
✓ PASSED: No Silent Exception Fallbacks
✓ PASSED: No Mock Framework Imports in Production Code
✓ PASSED: No Placeholder Constants
✓ PASSED: No Conditional Mock Logic
```

#### 4. Configuration Management (Stage 4)

- Environment-specific configuration file found
- All required properties present:
  - `spring.datasource.url`
  - `spring.jpa.hibernate.ddl-auto`
  - `logging.level.root`
  - `server.port`
  - `server.servlet.context-path`

#### 5. Database Schema Validation (Stage 5)

- Migration files validated (Flyway/Liquibase)
- No pending migrations blocking deployment
- Database connection verified

## OpenSage Memory Configuration

For YAWL 6.0.0-GA with Java 25, configure OpenSage memory settings in `src/main/resources/application-production.yml`:

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      maximum-pool-size: 50  # Optimal for virtual threads
      minimum-idle: 10

yawl:
  opensage:
    memory:
      # Virtual thread optimized settings
      heap-alloc: "256M"
      direct-alloc: "512M"
      stack-alloc: "1G"
      gc:
        young-gen: "30%"
        old-gen: "60%"
        metaspace: "256M"
        g1-rset-region: "256M"
        g1-h-region-threads: "4"

    # GRPO (Generational Reference Processing Optimization)
    gc:
      grpo:
        enabled: true
        young-gc-interval: "4s"
        old-gc-threshold: "75%"
        gc-thread-pinning: true
        reference-queue-processing: true
```

**Memory Allocation Recommendations:**

| Component | Recommendation | Virtual Thread Benefit |
|-----------|----------------|------------------------|
| Heap Size | 8GB initial, 16GB max | 50-70% more efficient |
| Metaspace | 256MB initial, 512MB max | Lower with virtual threads |
| G1 Heap | 30% young, 60% old | Optimized for short-lived objects |
| Virtual Stack | 256KB per thread | 200 bytes, ~10,000 per 256MB |

#### 6. Secret Management (Stage 6)

- **Zero hardcoded credentials** in source code
- All secrets loaded from environment variables
- GitHub Secrets properly configured
- Encryption key rotation verified (if applicable)

#### 7. Security Scanning (Stage 7)

- No known vulnerable dependencies
- No deprecated API usage
- Security context defined in Kubernetes manifests

#### 8. Performance & Resource Limits (Stage 8)

- Timeout configurations present
- Connection pool settings defined
- Resource limits specified:
  - CPU: minimum 1000m, maximum 4000m (virtual thread optimized)
  - Memory: minimum 4Gi, maximum 8Gi (OpenSage optimized)

#### 9. Virtual Thread Configuration (Stage 9)

- **Virtual thread executor configured** for I/O-bound operations
- **Carrier thread pool tuned** for optimal performance
- **Structured concurrency implemented** for coordinated execution
- **Pinning detection enabled** for monitoring

#### 10. Documentation & Artifacts (Stage 9)

- `README.md` present and current
- `CHANGELOG.md` updated for release
- SBOM (CycloneDX) generated

#### 11. Integration Tests (Stage 10)

- Integration test suite present
- All integration tests passing
- Smoke test suite available

#### 12. Kubernetes/Container Readiness (Stage 11)

- Dockerfile configured and tested
- Kubernetes manifests valid (validated with kubeval/kubeconform)
- Helm chart present and lints successfully

#### 13. Monitoring & Observability (Stage 12)

- Health check endpoints configured
- Prometheus metrics enabled
- Logging properly configured

### Running Readiness Validation

#### Local Testing (Pre-PR)

```bash
# Validate for staging
bash ci-cd/scripts/validate-production-readiness.sh staging

# Validate for production
bash ci-cd/scripts/validate-production-readiness.sh production
```

#### Automated in CI/CD

The readiness validation automatically runs as the first stage of the production deployment workflow. If any critical check fails, the workflow is halted immediately.

---

## Deployment Strategies

### Strategy Comparison

| Strategy | Risk Level | Rollback Speed | User Impact | Duration |
|----------|-----------|-----------------|------------|----------|
| **Blue-Green** | Low | < 1 minute | None (instant switchover) | 5-10 minutes |
| **Canary** | Very Low | Automatic at any phase | 5% → 25% → 50% → 100% | 30+ minutes |
| **Rolling** | Medium | Gradual during rollout | Minimal (progressive) | 10-15 minutes |

### Blue-Green Deployment

**Best for:** Production environments, zero-downtime requirement

**Process:**

1. **Deploy Green** (new version)
   - Full deployment to `yawl-green` namespace/label
   - Run complete smoke test suite
   - Verify all endpoints accessible

2. **Validation Phase** (5 minutes)
   - Monitor error rates
   - Check latency metrics
   - Verify database consistency

3. **Traffic Switch** (1 second)
   - Update load balancer to route to green
   - Instant user traffic migration
   - Keep blue running for quick rollback

4. **Promotion** (after 10 minutes stability)
   - Blue becomes standby
   - Green promoted to production
   - Ready for next deployment

**Command:**
```bash
gh workflow run production-deployment.yml \
  -f deployment_env=production \
  -f deployment_strategy=blue-green \
  -f version=6.0.0-GA
```

### Canary Deployment

**Best for:** Lower-risk changes, gradual rollout validation

**Phases:**

| Phase | Duration | Traffic | Validation |
|-------|----------|---------|------------|
| 1 | 10 minutes | 5% to canary | Error rates < 0.1% |
| 2 | 10 minutes | 25% to canary | P99 latency < 200ms |
| 3 | 10 minutes | 50% to canary | Database consistency |
| 4 | - | 100% complete | Full rollout |

**Automatic Rollback Triggers:**

- Error rate > 1% → rollback to previous version
- P99 latency > 500ms → rollback
- Database errors detected → rollback
- Health check failures > 5% → rollback

**Command:**
```bash
gh workflow run production-deployment.yml \
  -f deployment_env=production \
  -f deployment_strategy=canary \
  -f version=6.0.0-GA
```

### Rolling Deployment

**Best for:** Stateless services, gradual rollout acceptable

**Configuration:**
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 25%        # Max 25% more pods during rollout
    maxUnavailable: 10%  # Max 10% pods unavailable
```

**Process:**

1. Scale new pods gradually (25% increments)
2. Drain and terminate old pods
3. Health checks verify each pod before continuing
4. Rollout completes when all pods updated

**Command:**
```bash
gh workflow run production-deployment.yml \
  -f deployment_env=production \
  -f deployment_strategy=rolling \
  -f version=6.0.0-GA
```

---

## Manual Approval Process

### Approval Gate (Production Only)

For **production** deployments, manual approval is required from code owners:

#### Step 1: Deployment Initiated

```bash
gh workflow run production-deployment.yml \
  -f deployment_env=production \
  -f deployment_strategy=canary \
  -f version=6.0.0-GA
```

#### Step 2: Approval Issue Created

An issue is automatically created with:
- Deployment version
- Strategy
- Readiness status
- Required reviewers

**Issue Title:** `Approval Required: Production Deployment v5.2.0`

#### Step 3: Code Owner Review

Required permissions:
- Repository owner
- Code owner (defined in CODEOWNERS file)
- Deployment approver role

**Checklist for Reviewers:**

- [ ] Version is stable release (not pre-release)
- [ ] All readiness checks passed
- [ ] Database migrations are safe
- [ ] No risky changes in this version
- [ ] Team is aware of deployment
- [ ] Rollback plan understood

#### Step 4: Approval Actions

**To Approve:**
```
Comment on issue: APPROVED
```

**To Reject:**
```
Comment on issue: REJECTED
Reason: [explain rejection]
```

#### Step 5: Deployment Proceeds

- Approval timeout: 2 hours
- After approval: deployment begins immediately
- All deployment stages executed

### Bypass Approval (Staging Only)

For **staging** environment, approval can be skipped:

```bash
gh workflow run production-deployment.yml \
  -f deployment_env=staging \
  -f deployment_strategy=canary \
  -f skip_manual_approval=true
```

---

## Deployment Execution

### Pre-Deployment Phase (5 minutes)

1. **Environment Checks**
   - Kubernetes cluster connectivity
   - Namespace readiness
   - Storage availability
   - Database connectivity

2. **Manifest Validation**
   - Kubernetes YAML validation
   - Helm chart linting
   - Policy enforcement (e.g., no :latest tags)

3. **Configuration Verification**
   - All ConfigMaps mounted
   - All Secrets available
   - Resource limits defined

### Deployment Phase

**Blue-Green Example Workflow:**

```
Stage 1: Readiness Validation (5 min)
  ├─ Java compilation
  ├─ 100% test pass rate
  ├─ HYPER_STANDARDS check
  ├─ Configuration validation
  └─ Secret verification

Stage 2: Manual Approval (if production)
  └─ Await CODEOWNERS approval (max 2 hours)

Stage 3: Pre-Deployment Checks (5 min)
  ├─ Manifest validation
  ├─ Helm chart validation
  └─ Environment config verification

Stage 4: Blue-Green Deploy (5 min)
  ├─ Deploy to GREEN namespace
  ├─ Run smoke tests
  ├─ Monitor metrics
  └─ Verify endpoints

Stage 5: Traffic Switch (1 sec)
  └─ Update load balancer selector

Stage 6: Post-Deployment (5 min)
  ├─ Full smoke test suite
  ├─ Endpoint verification
  ├─ Database consistency
  └─ Agent availability check

Stage 7: Audit Logging
  └─ Generate deployment audit trail

Elapsed Time: ~25 minutes
```

### Monitoring Deployment Progress

```bash
# Watch pod status
kubectl get pods -n yawl-production -w

# Check deployment status
kubectl get deployment -n yawl-production

# View logs
kubectl logs -n yawl-production -l app=yawl -f

# Get events
kubectl get events -n yawl-production --sort-by='.lastTimestamp'
```

---

## Post-Deployment Validation

### Smoke Test Suite

Comprehensive tests run automatically after deployment:

#### Deployment Validation
- Namespace exists and ready
- Service deployed and endpoints available
- Deployment replicas healthy

#### Pod Health
- All pods in Running state
- All pods Ready
- No pod restarts or crashes
- CPU/Memory within limits

#### Service Endpoints (10 tests)
```
GET /api/health                 → 200 OK
GET /api/version                → 200 OK
GET /api/status                 → 200 OK
GET /api/v1/specifications      → 200 OK
GET /api/v1/cases               → 200 OK
GET /api/v1/workitems           → 200 OK
GET /api/admin/database/status  → 200 OK
GET /api/health/db              → 200 OK
GET /metrics                    → 200 OK
POST /api/auth/token            → 401 Unauthorized
```

#### API Functionality (5 tests)
- Specification list endpoint works
- Case list endpoint works
- Work item list endpoint works
- Database connectivity verified
- Authentication properly enforced

#### Performance
- Response time < 2 seconds (health endpoint)
- Memory usage monitoring active
- No excessive pod restarts

#### Security
- TLS/HTTPS enforced (if configured)
- Security headers present
- Unauthorized access denied

#### Logging & Observability
- No critical errors in logs
- Prometheus metrics available
- Log aggregation working

### Running Smoke Tests Manually

```bash
# Full smoke test suite
bash ci-cd/scripts/smoke-tests.sh full production

# Canary-specific tests
bash ci-cd/scripts/smoke-tests.sh canary production

# Blue-green specific tests
bash ci-cd/scripts/smoke-tests.sh blue-green production
```

### Success Criteria

Deployment is considered **successful** when:

✅ All readiness checks passed
✅ Deployment completed within timeout
✅ 100% of smoke tests passed
✅ No critical errors in logs
✅ All endpoints responding correctly
✅ Database connectivity verified
✅ Metrics being collected
✅ Zero pod restarts post-deployment

---

## Monitoring & Observability

### Real-Time Monitoring Dashboard

Set up monitoring dashboards for:

#### Service Metrics
- Request rate (requests/sec)
- Error rate (%)
- P50, P95, P99 latency
- Throughput (operations/sec)

#### Resource Metrics
- CPU usage (%)
- Memory usage (%)
- Disk I/O
- Network bandwidth

#### Application Metrics
- Active workflows
- Work items processed
- Database query latency
- Engine throughput

#### Infrastructure Metrics
- Pod restart count
- Failed health checks
- Image pull errors
- Volume usage

### Alerting Rules

Configure alerts for immediate notification:

| Alert | Threshold | Action |
|-------|-----------|--------|
| High Error Rate | > 1% for 5 min | Page on-call |
| Pod Restart Loop | 3+ restarts/10 min | Auto-rollback |
| Database Connection Loss | > 0 for 1 min | Critical alert |
| Memory Pressure | > 90% for 5 min | Scale up or rollback |
| Health Check Failure | 5+ consecutive | Evict and restart pod |
| Virtual Thread Pinning | > 1000/hour | Warning alert |
| Carrier Thread Starvation | > 90% utilization | Critical alert |
| GC GRPO Overhead | > 20% | Warning alert |
| Virtual Thread Creation Rate | > 10,000/min | Critical alert |

### Log Aggregation

All logs should be aggregated to centralized system:

```
Logs Required:
├─ Application logs (stdout/stderr)
├─ Access logs
├─ Error traces
├─ Audit logs
└─ Database logs
```

**Important Log Fields:**
- Timestamp (UTC)
- Pod name and version
- Request ID (correlation)
- User ID (if applicable)
- Error stack trace
- Deployment version

---

## Rollback Procedures

### Automatic Rollback Triggers

**Production deployments automatically rollback when:**

1. **Smoke tests fail** (30 seconds after deployment)
2. **Error rate > 5%** (averaged over 2 minutes)
3. **Health checks fail** (3+ consecutive failures)
4. **Pod crash loops** (restart count > 5)
5. **Database connectivity lost** (connection timeout)
6. **Out of memory** (OOMKilled state)

### Manual Rollback

#### Immediate Rollback (Blue-Green)

```bash
# Switch traffic back to blue
kubectl patch service yawl -n yawl-production \
  -p '{"spec":{"selector":{"version":"blue"}}}'

# Verify traffic switched
kubectl get svc yawl -n yawl-production -o jsonpath='{.spec.selector}'

# Monitor logs
kubectl logs -n yawl-production -l version=blue -f
```

#### Kubernetes Rollback (Rolling Deployment)

```bash
# Get deployment history
kubectl rollout history deployment/yawl -n yawl-production

# Rollback to previous revision
kubectl rollout undo deployment/yawl -n yawl-production

# Verify rollback
kubectl rollout status deployment/yawl -n yawl-production --timeout=5m
```

#### Full Rollback via Deployment Script

```bash
bash ci-cd/scripts/deploy.sh \
  --environment production \
  --target kubernetes \
  --rollback
```

### Rollback Verification

After rollback, verify:

```bash
# Check pods are running with previous version
kubectl get pods -n yawl-production -o wide

# Verify endpoints responding
curl http://yawl.yawl-production.svc.cluster.local:8080/api/health

# Check logs for errors
kubectl logs -n yawl-production -l app=yawl --tail=50

# Verify service endpoints
kubectl describe svc yawl -n yawl-production
```

### Post-Rollback Actions

1. **Incident Creation**
   - Create incident issue with title: `Incident: Production Deployment v5.2.0 Rollback`
   - Include rollback reason and timestamp
   - Notify incident response team

2. **Root Cause Analysis**
   - Review deployment logs
   - Check metrics for anomalies
   - Identify failing component

3. **Fix Development**
   - Address root cause in feature branch
   - Update code and tests
   - Validate locally before re-deployment

4. **Re-Deployment**
   - Only after root cause fixed
   - Use same deployment process
   - Schedule with team awareness

---

## Incident Response

### Incident Severity Levels

| Level | Impact | Response Time | Actions |
|-------|--------|---------------|---------|
| **P1 (Critical)** | Complete service down | 5 minutes | Immediate rollback + incident team |
| **P2 (High)** | Partial outage | 15 minutes | Investigate, prepare rollback decision |
| **P3 (Medium)** | Degraded performance | 1 hour | Monitor, plan maintenance |
| **P4 (Low)** | Minor issues | Next business day | Document and plan fix |

### P1 Incident Response Playbook

```
1. [0 min] Service Down Detected
   ├─ Alarm triggered (automated)
   ├─ Page on-call engineer
   └─ Create incident channel in Slack

2. [5 min] Immediate Assessment
   ├─ Check dashboard metrics
   ├─ Review recent deployments
   ├─ Determine if rollback needed
   └─ Notify stakeholders

3. [5-10 min] Decision Point
   ├─ IF: Clear deployment issue → ROLLBACK
   ├─ ELSE IF: Infrastructure issue → Scale or troubleshoot
   └─ ELSE → Investigate root cause

4. [10 min] Execute Rollback
   ├─ Run: bash deploy.sh --rollback
   ├─ Monitor: kubectl logs
   └─ Verify: smoke tests

5. [15 min] Service Recovery
   ├─ Confirm all endpoints responding
   ├─ Check error rates dropped
   └─ Update incident status

6. [20+ min] Post-Incident
   ├─ Document timeline
   ├─ Schedule RCA meeting
   ├─ Identify preventive measures
   └─ Update runbooks
```

### Incident Communication

**Template for Stakeholder Update:**

```
INCIDENT NOTICE: YAWL Production Outage
========================================

Started: 2026-02-18 14:30:00 UTC
Detected: 2026-02-18 14:32:00 UTC
Status: RESOLVED at 2026-02-18 14:38:00 UTC

Duration: 8 minutes

Root Cause: Deployment v5.2.0 introduced database connection pool exhaustion
Response: Automatic rollback to v5.1.9 (< 5 minutes)

Impact: Users experienced intermittent connection errors
Affected Users: ~2% (canary phase)

Recovery: All services restored, users unaffected
Next Steps: Root cause analysis, fix deployment, reschedule for next week

Contact: on-call-team@yawlfoundation.org for status updates
```

---

## Quick Reference

### Common Deployment Commands

```bash
# Validate production readiness (local)
bash ci-cd/scripts/validate-production-readiness.sh production

# Trigger production deployment (GitHub CLI)
gh workflow run production-deployment.yml \
  -f deployment_env=production \
  -f deployment_strategy=blue-green

# Monitor deployment progress
kubectl get pods -n yawl-production -w

# Run smoke tests manually
bash ci-cd/scripts/smoke-tests.sh full production

# Immediate rollback
bash ci-cd/scripts/deploy.sh --environment production --rollback

# Check deployment history
kubectl rollout history deployment/yawl -n yawl-production
```

### Important Files & Locations

| File | Purpose |
|------|---------|
| `.github/workflows/production-deployment.yml` | Main deployment workflow |
| `ci-cd/scripts/validate-production-readiness.sh` | Readiness validation |
| `ci-cd/scripts/smoke-tests.sh` | Post-deployment tests |
| `ci-cd/scripts/deploy.sh` | Multi-cloud deployment script |
| `k8s/deployment.yaml` | Kubernetes deployment manifest |
| `helm/yawl/Chart.yaml` | Helm chart configuration |
| `src/main/resources/application-production.yml` | Production config |

### Environment Variables

```bash
# Required for production deployment
export ENVIRONMENT=production
export VERSION=5.2.0
export KUBECONFIG=/path/to/kubeconfig
export DATABASE_URL="jdbc:postgresql://db:5432/yawl"
export DOCKER_REGISTRY_URL="ghcr.io"
```

### Useful kubectl Commands

```bash
# Get deployment status
kubectl get deploy -n yawl-production

# Get pod status
kubectl get pods -n yawl-production -o wide

# Get service info
kubectl describe svc yawl -n yawl-production

# Get events
kubectl get events -n yawl-production --sort-by='.lastTimestamp'

# View logs
kubectl logs -n yawl-production -l app=yawl -f

# Port forward for local testing
kubectl port-forward -n yawl-production svc/yawl 8080:8080

# Describe pod for debugging
kubectl describe pod -n yawl-production <pod-name>

# Get resource usage
kubectl top nodes
kubectl top pods -n yawl-production

# Check node status
kubectl get nodes -o wide
```

---

## Support & Escalation

### Deployment Issues

**Issue:** Deployment stuck in pending state

```bash
# Check pod events
kubectl describe pod -n yawl-production <pod-name>

# Common causes:
# - Insufficient resources (scale node pool)
# - Pull image error (check registry credentials)
# - Volume mount issue (check PVC status)
```

**Issue:** Smoke tests failing

```bash
# Run tests with verbose output
bash ci-cd/scripts/smoke-tests.sh full production

# Check endpoint manually
curl -v http://localhost:8080/api/health

# Review logs
kubectl logs -n yawl-production -l app=yawl --tail=100
```

**Issue:** Database migration stuck

```bash
# Check Flyway status
kubectl exec -it <pod-name> -c yawl -- \
  java -jar yawl.jar flyway:info

# Manual intervention (only if necessary)
kubectl port-forward svc/postgres 5432:5432
# Connect with psql and review migration status
```

### Escalation Process

1. **Level 1** - On-call engineer
   - Handles common issues
   - Executes runbooks
   - Initiates rollback if needed

2. **Level 2** - Senior engineer
   - Investigates root cause
   - Reviews code changes
   - Authorizes hotfixes

3. **Level 3** - Architect/Tech Lead
   - Strategic decisions
   - Infrastructure changes
   - Post-incident reviews

### Getting Help

```
Slack: #yawl-deployments (real-time alerts)
Email: deployments@yawlfoundation.org
GitHub: Issue in yawl-engine repository
Wiki: https://wiki.yawlfoundation.org/deployment
```

---

## Version History

| Date | Version | Changes |
|------|---------|---------|
| 2026-02-18 | 1.0 | Initial production deployment documentation |

---

## Appendix

### A. Checklist: Ready for Production Deployment

- [ ] Code merged to main/master branch
- [ ] All tests passing (100%)
- [ ] No HYPER_STANDARDS violations
- [ ] Code reviewed by 2+ team members
- [ ] Secrets configured in GitHub
- [ ] Database migrations tested
- [ ] CHANGELOG.md updated
- [ ] README.md current
- [ ] Helm chart values verified
- [ ] Kubernetes manifests validated
- [ ] Disaster recovery plan reviewed
- [ ] Team notified of deployment window
- [ ] Runbooks updated
- [ ] Monitoring dashboards prepared
- [ ] Incident response team on standby

### B. Template: Deployment Runbook

```markdown
# Deployment Runbook: YAWL v6.0.0.0

## Pre-Deployment
- [ ] Verify all readiness checks passed
- [ ] Confirm approval obtained
- [ ] Check team availability

## Deployment
- [ ] Start deployment workflow
- [ ] Monitor pod status
- [ ] Watch metrics dashboard
- [ ] Verify health checks passing

## Post-Deployment
- [ ] Run smoke tests
- [ ] Check error rates
- [ ] Verify database consistency
- [ ] Document deployment time

## Rollback (if needed)
- [ ] Execute rollback script
- [ ] Verify service recovery
- [ ] Create incident issue
- [ ] Schedule RCA
```

### C. Emergency Contacts

```
On-Call: +1-555-YAWL-911
Slack: @yawl-oncall
PagerDuty: yawl-incidents
```

---

**End of Document**

For questions or updates, contact the YAWL Foundation operations team.
