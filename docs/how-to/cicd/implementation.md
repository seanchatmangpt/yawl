# YAWL v6.0.0 - CI/CD Implementation Guide

**Version:** 1.0.0
**Date:** February 17, 2026
**Target Audience:** DevOps Engineers, Platform Architects, Release Managers

---

## Quick Start (5 minutes)

### 1. Enable GitHub Actions Workflows

```bash
cd /home/user/yawl

# Enable all workflows
for workflow in .github/workflows/*.yml; do
  gh workflow enable "$(basename "$workflow")"
done

# Verify
gh workflow list
```

### 2. Configure Secrets

```bash
# Required GitHub Actions secrets
gh secret set NEXUS_USER --body "your-nexus-username"
gh secret set NEXUS_PASSWORD --body "your-nexus-password"
gh secret set GPG_PRIVATE_KEY --body "$(cat ~/.gnupg/yawl-private.key)"
gh secret set GPG_KEY_ID --body "0x1234567890ABCDEF"
gh secret set SLACK_WEBHOOK_URL --body "https://hooks.slack.com/services/..."
gh secret set GITHUB_TOKEN --body "${{ secrets.GITHUB_TOKEN }}"  # Auto-set
```

### 3. Deploy ArgoCD (Kubernetes)

```bash
# Create argocd namespace
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for deployment
kubectl wait --for=condition=available --timeout=300s \
  deployment/argocd-server -n argocd

# Port-forward to access UI
kubectl port-forward svc/argocd-server -n argocd 8080:443
# Access: https://localhost:8080
# Username: admin
# Password: $(kubectl get secret argocd-initial-admin-secret -n argocd -o jsonpath="{.data.password}" | base64 --decode)
```

### 4. Deploy HashiCorp Vault

```bash
# Add Vault Helm repo
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update

# Install Vault
helm install vault hashicorp/vault -n vault --create-namespace \
  -f - << 'EOF'
server:
  dev:
    enabled: true
  dataStorage:
    size: 10Gi
ui:
  enabled: true
EOF

# Unseal Vault (dev mode auto-unseals)
# For production, use auto-unseal or manual unsealing
```

### 5. Create ArgoCD Applications

```bash
# Clone GitOps repository
git clone https://github.com/yawlfoundation/yawl-gitops.git

# Bootstrap ArgoCD applications
kubectl apply -f yawl-gitops/apps/

# Check status
argocd app list
```

---

## Phase 1: Foundation (Week 1-2)

### Objectives

- Set up GitHub Actions CI/CD pipeline
- Configure Maven artifact publishing
- Implement basic security scanning
- Create SBOM generation

### Implementation Steps

#### 1.1 GitHub Actions Setup

```bash
# Verify workflow files exist
ls -la .github/workflows/
# Expected:
# - ci.yml (existing)
# - release.yml (new)
# - artifact-management.yml (new)
# - secret-management.yml (new)

# Test workflows
gh workflow run ci.yml --ref develop
gh workflow view ci.yml
```

#### 1.2 Maven Configuration

```bash
# Update pom.xml with distribution management
cat >> pom.xml << 'EOF'
<distributionManagement>
  <repository>
    <id>nexus</id>
    <url>https://nexus.example.com/repository/releases</url>
  </repository>
  <snapshotRepository>
    <id>nexus</id>
    <url>https://nexus.example.com/repository/snapshots</url>
  </snapshotRepository>
</distributionManagement>
EOF

# Build and test locally
mvn clean package
```

#### 1.3 SBOM Generation

```bash
# Add CycloneDX plugin to pom.xml
mvn org.cyclonedx:cyclonedx-maven-plugin:generateBom

# Verify SBOM generated
find . -name "bom.json" -o -name "bom.xml"
```

#### 1.4 Security Scanning

```bash
# Run OWASP Dependency Check locally
mvn org.owasp:dependency-check-maven:check \
  -DfailBuildOnCVSS=7 \
  -Dformats=HTML,JSON

# Review report
open target/dependency-check-report.html
```

### Deliverables

- [ ] All workflows enabled in GitHub
- [ ] Nexus artifact repository configured
- [ ] SBOM generation working
- [ ] Security scan baseline established
- [ ] Documentation complete

---

## Phase 2: GitOps & Deployment (Week 3-4)

### Objectives

- Deploy ArgoCD for declarative deployments
- Create Helm charts for all components
- Implement blue-green deployments
- Set up canary testing

### Implementation Steps

#### 2.1 ArgoCD Deployment

```bash
# Create argocd values
cat > argocd-values.yaml << 'EOF'
redis:
  enabled: true
server:
  ingress:
    enabled: true
    hosts:
      - argocd.example.com
  extraArgs:
    - --insecure  # Only for dev; use TLS in prod
repoServer:
  replicas: 2
EOF

# Deploy
helm install argocd argo/argo-cd \
  -n argocd --create-namespace \
  -f argocd-values.yaml
```

#### 2.2 Helm Chart Creation

```bash
# Create Helm chart structure
helm create helm/yawl-parent

# Update Chart.yaml
cat > helm/yawl-parent/Chart.yaml << 'EOF'
apiVersion: v2
name: yawl-parent
description: YAWL Workflow Engine
type: application
version: 6.0.0
appVersion: "6.0.0"
EOF

# Create values for each environment
cp helm/yawl-parent/values.yaml helm/yawl-parent/values-dev.yaml
cp helm/yawl-parent/values.yaml helm/yawl-parent/values-staging.yaml
cp helm/yawl-parent/values.yaml helm/yawl-parent/values-prod.yaml

# Customize each environment
# Dev: 1 replica, no persistence
# Staging: 2 replicas, 20GB persistence
# Prod: 3 replicas, 100GB persistence, HA database
```

#### 2.3 Blue-Green Infrastructure

```bash
# Create blue-green deployments
kubectl apply -f - << 'EOF'
---
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
        image: ghcr.io/yawlfoundation/yawl-engine:6.0.0

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine-green
  namespace: yawl-prod
spec:
  replicas: 0
  selector:
    matchLabels:
      app: yawl-engine
      version: green
  template:
    metadata:
      labels:
        app: yawl-engine
        version: green
    spec:
      containers:
      - name: yawl-engine
        image: ghcr.io/yawlfoundation/yawl-engine:6.0.0
EOF
```

#### 2.4 Deployment Scripts

```bash
# Make deployment script executable
chmod +x scripts/deployment-strategies.sh

# Test locally (dry-run)
./scripts/deployment-strategies.sh --help

# Test blue-green dry-run
./scripts/deployment-strategies.sh blue-green v6.0.1 --dry-run
```

### Deliverables

- [ ] ArgoCD deployed and operational
- [ ] Helm charts created for all components
- [ ] Blue-green infrastructure ready
- [ ] Deployment scripts tested
- [ ] Staging environment deployed

---

## Phase 3: Secret Management (Week 5-6)

### Objectives

- Deploy HashiCorp Vault
- Integrate OIDC for GitHub Actions
- Implement secret rotation
- Add cosign image signing

### Implementation Steps

#### 3.1 HashiCorp Vault Setup

```bash
# Initialize Vault
kubectl exec -it vault-0 -n vault -- \
  vault operator init \
  -key-shares=3 \
  -key-threshold=2

# Unseal Vault (save keys securely)
kubectl exec -it vault-0 -n vault -- vault operator unseal
# (repeat 2 more times with different keys)

# Enable secret engine
kubectl exec -it vault-0 -n vault -- \
  vault secrets enable -version=2 kv

# Create secret policies
kubectl exec -it vault-0 -n vault -- vault policy write yawl - << 'EOF'
path "secret/data/yawl/*" {
  capabilities = ["read", "list"]
}
EOF
```

#### 3.2 OIDC Configuration

```bash
# Configure Vault OIDC auth
kubectl exec -it vault-0 -n vault -- vault auth enable jwt

# Configure JWT auth for GitHub
kubectl exec -it vault-0 -n vault -- \
  vault write auth/jwt/config \
    jwks_url="https://token.actions.githubusercontent.com/.well-known/jwks" \
    bound_issuer="https://token.actions.githubusercontent.com"

# Create role for YAWL CI
kubectl exec -it vault-0 -n vault -- \
  vault write auth/jwt/role/yawl-ci \
    bound_audiences="https://github.com/yawlfoundation" \
    policies="yawl" \
    ttl=1h
```

#### 3.3 Secret Rotation Policies

```bash
# Enable database secret engine
kubectl exec -it vault-0 -n vault -- \
  vault secrets enable database

# Configure PostgreSQL connection
kubectl exec -it vault-0 -n vault -- \
  vault write database/config/postgresql \
    plugin_name=postgresql-database-plugin \
    allowed_roles="yawl-app" \
    connection_url="postgresql://{{username}}:{{password}}@postgres:5432/yawl" \
    username="vault" \
    password="vault-password"

# Create dynamic role
kubectl exec -it vault-0 -n vault -- \
  vault write database/roles/yawl-app \
    db_name=postgresql \
    creation_statements="CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}';" \
    default_ttl="1h" \
    max_ttl="24h"
```

#### 3.4 Cosign Configuration

```bash
# Install cosign
curl -sSLO https://github.com/sigstore/cosign/releases/latest/download/cosign-linux-amd64
chmod +x cosign-linux-amd64

# Generate keys (do once, store securely)
./cosign-linux-amd64 generate-key-pair

# Store private key in GitHub secret
gh secret set COSIGN_PRIVATE_KEY --body "$(cat cosign.key)"
gh secret set COSIGN_PASSWORD --body "your-secure-password"
```

### Deliverables

- [ ] Vault deployed and initialized
- [ ] OIDC federation configured
- [ ] Secret rotation policies active
- [ ] Cosign keys generated and stored
- [ ] Secret access audit logging enabled

---

## Phase 4: Observability & Compliance (Week 7-8)

### Objectives

- Deploy OpenTelemetry stack
- Configure distributed tracing
- Implement alerting rules
- Document SLSA compliance

### Implementation Steps

#### 4.1 OpenTelemetry Deployment

```bash
# Add OpenTelemetry Helm repo
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo update

# Deploy OpenTelemetry Collector
helm install otel-collector open-telemetry/opentelemetry-collector \
  -n observability --create-namespace

# Deploy Jaeger for trace visualization
helm install jaeger jaegertracing/jaeger \
  -n observability
```

#### 4.2 Application Instrumentation

```bash
# Add OpenTelemetry dependencies to pom.xml
cat >> pom.xml << 'EOF'
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-api</artifactId>
  <version>1.52.0</version>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-jaeger-thrift</artifactId>
  <version>1.52.0</version>
</dependency>
EOF

# Update application properties
cat >> src/main/resources/application-prod.properties << 'EOF'
otel.exporter.otlp.endpoint=http://otel-collector:4317
otel.service.name=yawl-engine
otel.service.version=6.0.0
otel.metrics.exporter=otlp
otel.traces.exporter=otlp
EOF
```

#### 4.3 Alerting Rules

```bash
# Create PrometheusRule for Kubernetes
kubectl apply -f - << 'EOF'
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: yawl-alerts
  namespace: yawl-prod
spec:
  groups:
  - name: yawl
    interval: 30s
    rules:
    - alert: HighErrorRate
      expr: rate(http_requests_failed_total[5m]) > 0.01
      for: 5m
      annotations:
        summary: "High error rate detected"
        severity: warning
EOF
```

#### 4.4 SLSA Compliance Documentation

```bash
# Create SLSA compliance document
cat > docs/SLSA_COMPLIANCE.md << 'EOF'
# YAWL v6.0.0 - SLSA v1.0 Compliance

## Level 3 Attestation

### Build Requirements Met

- [x] Version control (Git with signed commits)
- [x] CI/CD platform (GitHub Actions)
- [x] Build isolation (ephemeral runners)
- [x] Auditable provenance (30-day retention)
- [x] Artifact signing (GPG + Cosign)

### Provenance Attestation

All build artifacts include in-toto provenance statements with:
- Builder identification
- Build configuration
- Material references
- Completeness claims
- Reproducibility attestation

EOF
```

### Deliverables

- [ ] OpenTelemetry collector deployed
- [ ] Jaeger tracing visualization working
- [ ] Prometheus alerts configured
- [ ] Grafana dashboards created
- [ ] SLSA compliance documented

---

## Phase 5: Optimization & Hardening (Week 9+)

### Objectives

- Performance tuning
- Cost optimization
- Security hardening
- Disaster recovery testing

### Implementation Steps

#### 5.1 Performance Tuning

```bash
# Analyze build times
gh workflow run ci.yml -f branch=main

# Optimize Maven build
# - Enable parallel compilation: -T 1C
# - Cache dependencies: actions/setup-java cache: maven
# - Skip unnecessary tests: -DskipTests=true in certain jobs

# Expected improvements:
# - Compile: 10% faster (parallel)
# - Test: 20% faster (parallel execution)
# - Artifact push: 30% faster (Docker layer caching)
```

#### 5.2 Cost Optimization

```bash
# Remove unnecessary artifact retention
gh workflow view artifact-management.yml

# Update retention policies
# - Docker untagged images: 7 days (was 30)
# - Build artifacts: 3 days (was 5)
# - Test reports: 3 days (was 7)
# - SBOMs: 30 days (was 90)

# Use GitHub's native caching
# - Docker buildx cache: type=gha,mode=max
# - Maven cache: actions/setup-java cache: maven
```

#### 5.3 Security Hardening

```bash
# Add CODEOWNERS for code review
cat > .github/CODEOWNERS << 'EOF'
# Require approval from security team
* @yawl-security-team

# Require approval from DevOps for infra changes
.github/ @yawl-devops
terraform/ @yawl-devops
helm/ @yawl-devops
EOF

# Configure branch protection
gh api repos/:owner/:repo/branches/main/protection \
  --input - << 'EOF'
{
  "enforce_admins": true,
  "required_status_checks": {
    "contexts": ["build", "test", "security-scan"]
  },
  "required_pull_request_reviews": {
    "require_code_owner_reviews": true,
    "required_approving_review_count": 2
  },
  "dismiss_stale_reviews": true,
  "require_branches_to_be_up_to_date": true
}
EOF
```

#### 5.4 Disaster Recovery Testing

```bash
# Monthly DR drill
# 1. Backup database
# 2. Simulate region failure
# 3. Failover to secondary region
# 4. Run smoke tests
# 5. Verify data consistency
# 6. Document issues and improvements

cat > scripts/dr-drill.sh << 'EOF'
#!/bin/bash

echo "DR Drill Procedure"
echo "=================="

# Step 1: Backup
echo "1. Creating backup..."
kubectl exec -n yawl-prod postgres-0 -- \
  pg_dump yawl > /backups/yawl-dr-drill-$(date +%s).sql

# Step 2: Failover
echo "2. Failing over to secondary region..."
# Update DNS/ingress to point to secondary region

# Step 3: Test
echo "3. Running smoke tests..."
curl -f http://yawl-secondary.example.com/actuator/health

# Step 4: Verify
echo "4. Verifying data consistency..."
# Compare record counts, checksums, etc.

echo "DR Drill Complete"
EOF

chmod +x scripts/dr-drill.sh
```

### Deliverables

- [ ] Build time reduced to < 12 minutes
- [ ] Monthly cost reduced by 30%
- [ ] Security hardening complete
- [ ] DR plan documented and tested
- [ ] Team trained on procedures

---

## Validation Checklist

### Pre-Production Verification

- [ ] All GitHub Actions workflows passing
- [ ] Code coverage ≥ 80%
- [ ] Security scans showing 0 critical issues
- [ ] SBOM generated and validated
- [ ] Artifact signing verified
- [ ] ArgoCD applications synced
- [ ] Blue-green deployment tested
- [ ] Canary deployment tested in staging
- [ ] Vault secrets accessible
- [ ] Distributed tracing working
- [ ] Alerts configured and tested
- [ ] Runbooks reviewed by team
- [ ] DR plan validated

### Post-Deployment Verification

- [ ] Production deployment completed successfully
- [ ] Health checks all green
- [ ] Smoke tests passed
- [ ] Error rate < 0.1%
- [ ] Latency p99 < 100ms
- [ ] Metrics visible in dashboards
- [ ] Logs aggregated and searchable
- [ ] Team notified of successful deployment
- [ ] Release notes published

---

## Support & Troubleshooting

### Common Issues

**GitHub Actions Timeout**
- Solution: Increase timeout in workflow (max 360 min)
- Or: Optimize build (parallel compilation, caching)

**Kubernetes Pod Crash**
- Check logs: `kubectl logs <pod> -n yawl-prod`
- Check events: `kubectl describe pod <pod> -n yawl-prod`
- Check resources: `kubectl top pods -n yawl-prod`

**Vault Authentication Failure**
- Verify OIDC token: Validate JWT in jwt.io
- Check policy: `vault policy read yawl`
- Check role: `vault read auth/jwt/role/yawl-ci`

**ArgoCD Sync Stuck**
- Check application status: `argocd app get yawl-prod`
- Retry sync: `argocd app sync yawl-prod`
- Check logs: `kubectl logs -n argocd argocd-server`

### Getting Help

- **GitHub Discussions:** https://github.com/yawlfoundation/yawl/discussions
- **Slack Channel:** #yawl-cicd-help
- **Documentation:** https://docs.yawlfoundation.org
- **Issues:** https://github.com/yawlfoundation/yawl/issues

---

## Next Steps

1. **Week 1-2:** Complete Foundation phase
2. **Week 3-4:** Complete GitOps phase
3. **Week 5-6:** Complete Secret Management phase
4. **Week 7-8:** Complete Observability phase
5. **Week 9+:** Optimization & hardening

For questions or issues, refer to:
- Architecture Doc: `docs/CICD_V6_ARCHITECTURE.md`
- Deployment Playbooks: `docs/DEPLOYMENT_PLAYBOOKS.md`
- GitHub Workflows: `.github/workflows/`

---

**Document Version:** 1.0.0
**Last Updated:** February 17, 2026
**Next Review:** May 17, 2026
