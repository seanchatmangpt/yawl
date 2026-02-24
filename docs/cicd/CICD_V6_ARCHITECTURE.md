# YAWL v6.0.0 - Enterprise-Grade CI/CD Pipeline Architecture

**Version:** 1.0.0
**Date:** February 17, 2026
**Status:** Reference Architecture
**Target:** YAWL v6.0.0 and beyond

---

## 1. Executive Summary

This document defines a comprehensive, enterprise-grade CI/CD pipeline for YAWL v6.0.0 with focus on:

- **GitOps-driven deployments** using ArgoCD for declarative, reproducible infrastructure
- **Artifact lifecycle management** with semantic versioning, signing, and metadata tracking
- **Automated release orchestration** with changelog generation and promotion workflows
- **Software Bill of Materials (SBOM)** generation and compliance validation
- **Multi-strategy deployment** patterns (blue-green, canary, progressive rollout)
- **Secure secret management** via HashiCorp Vault and OIDC federation
- **Production-grade observability** with distributed tracing and real-time alerting

**Key Metrics:**
- Build time: < 15 minutes (compile + test + scan)
- Test coverage: ≥ 80% (automated enforcement)
- Security scanning: 0 critical vulnerabilities
- Deployment confidence: Blue-green + canary with automated rollback
- Release cycle: Weekly (automated from develop), Manual (main branch)

---

## 2. System Architecture

### 2.1 Overall Pipeline Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Developer Workflow                             │
│  feature/xxx → PR → Review → Merge to develop/main              │
└──────────────────────┬──────────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
        v                             v
  ┌──────────────┐          ┌──────────────────┐
  │   CI/Build   │          │   Artifact Mgmt  │
  │   Pipeline   │          │   & Registry     │
  └──────┬───────┘          └────────┬─────────┘
         │                           │
    ┌────┴──────────────┐            │
    │                   │            │
    v                   v            v
┌─────────┐      ┌────────────┐  ┌──────────┐
│Compile  │      │  Security  │  │ SBOM Gen │
│         │      │  Scanning  │  │ & Sign   │
└────┬────┘      └────┬───────┘  └─────┬────┘
     │                │               │
     └────────┬───────┴───────────────┘
              │
    ┌─────────v────────────┐
    │  Tests & Coverage    │
    │ - Unit Tests         │
    │ - Integration Tests  │
    │ - Performance Tests  │
    └─────────┬────────────┘
              │
    ┌─────────v────────────┐
    │ Build Artifacts      │
    │ - Docker Image       │
    │ - Maven Package      │
    │ - Helm Chart         │
    └─────────┬────────────┘
              │
    ┌─────────v────────────────────────────┐
    │      GitOps Configuration             │
    │  (ArgoCD Declarative Manifests)       │
    └─────────┬────────────────────────────┘
              │
        ┌─────┴──────┬──────────┐
        │            │          │
        v            v          v
    ┌───────┐  ┌─────────┐  ┌──────────┐
    │  Dev  │  │ Staging │  │Production│
    │  Env  │  │  Env    │  │  Env     │
    └───────┘  └─────────┘  └──────────┘
        ↓          ↓             ↓
   (Manual)   (Auto Canary)  (Blue-Green)
```

### 2.2 Component Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      GitHub / VCS                             │
│  - Feature Branches                                           │
│  - Pull Requests                                              │
│  - Release Tags (semantic versioning)                         │
└───────────────────┬──────────────────────────────────────────┘
                    │
        ┌───────────┴────────────┐
        │                        │
        v                        v
┌──────────────────────┐  ┌──────────────────────┐
│  GitHub Actions      │  │  ArgoCD              │
│  - CI/Build          │  │  - Declarative Deploys
│  - Testing           │  │  - GitOps Sync       │
│  - Artifact Build    │  │  - Multi-env Mgmt    │
│  - Release Automation│  │  - Progressive Deploy│
└──────────┬───────────┘  └──────────┬───────────┘
           │                        │
        ┌──┴──────────┬─────────────┴──┐
        │             │                │
        v             v                v
┌──────────────┐ ┌─────────────┐ ┌────────────────┐
│ Nexus/       │ │ Docker      │ │ HashiCorp Vault│
│ Artifactory  │ │ Registry    │ │ - Secrets Mgmt │
│ - JARs       │ │ - Images    │ │ - OIDC Tokens  │
│ - SBOMs      │ │ - Scanning  │ │ - Rotation     │
│ - Signatures │ │ - Signing   │ │ - Audit Logs   │
└──────────────┘ └─────────────┘ └────────────────┘
```

---

## 3. GitOps Workflow Design

### 3.1 Repository Structure

```
yawl-gitops/
├── README.md
├── DEPLOYMENT_GUIDE.md
│
├── envs/
│   ├── dev/
│   │   ├── kustomization.yaml
│   │   ├── values.dev.yaml
│   │   ├── networking/
│   │   │   └── ingress.yaml
│   │   ├── secrets/
│   │   │   └── secrets-vault-ref.yaml (Vault references)
│   │   └── monitoring/
│   │       └── alerts-dev.yaml
│   │
│   ├── staging/
│   │   ├── kustomization.yaml
│   │   ├── values.staging.yaml
│   │   ├── networking/
│   │   ├── secrets/
│   │   └── monitoring/
│   │       └── alerts-staging.yaml
│   │
│   └── production/
│       ├── kustomization.yaml
│       ├── values.prod.yaml
│       ├── networking/
│       │   ├── ingress.yaml
│       │   └── network-policy.yaml
│       ├── secrets/
│       │   └── secrets-vault-ref.yaml
│       ├── monitoring/
│       │   └── alerts-prod.yaml
│       ├── backup/
│       │   └── backup-policy.yaml
│       └── disaster-recovery/
│           └── dr-config.yaml
│
├── base/
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   ├── statefulset.yaml (for persistence)
│   ├── rbac/
│   │   ├── serviceaccount.yaml
│   │   ├── role.yaml
│   │   └── rolebinding.yaml
│   ├── hpa/
│   │   └── horizontal-pod-autoscaler.yaml
│   └── pdb/
│       └── pod-disruption-budget.yaml
│
├── apps/
│   ├── yawl-engine/
│   │   ├── app.yaml (ArgoCD Application CRD)
│   │   └── sync-policy.yaml
│   ├── yawl-stateless/
│   │   ├── app.yaml
│   │   └── sync-policy.yaml
│   ├── yawl-integration/
│   │   ├── app.yaml
│   │   └── sync-policy.yaml
│   └── yawl-monitoring/
│       ├── app.yaml
│       └── sync-policy.yaml
│
├── argocd/
│   ├── argocd-cm.yaml (ArgoCD ConfigMap)
│   ├── applicationset.yaml (Multi-env ApplicationSet)
│   ├── repo-credentials.yaml
│   └── oidc-config.yaml
│
├── helm/
│   ├── yawl-parent/
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   ├── values-dev.yaml
│   │   ├── values-staging.yaml
│   │   ├── values-prod.yaml
│   │   ├── templates/
│   │   └── charts/ (subcharts)
│   └── yawl-operators/
│       ├── Chart.yaml
│       └── values.yaml
│
└── scripts/
    ├── bootstrap-argocd.sh
    ├── sync-all-apps.sh
    ├── promote-to-env.sh
    └── emergency-rollback.sh
```

### 3.2 ArgoCD Configuration

**File:** `/home/user/yawl/.github/argocd/argocd-cm.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-cm
  namespace: argocd
data:
  # RBAC Configuration
  policy.default: role:readonly
  policy.csv: |
    # Admin role - full access
    p, role:admin, applications, *, */*, allow
    p, role:admin, repositories, *, *, allow
    p, role:admin, clusters, *, *, allow

    # Developer role - read + sync
    p, role:developer, applications, get, */*, allow
    p, role:developer, applications, sync, */*, allow
    p, role:developer, applications, override, */*, allow

    # Read-only role
    p, role:viewer, applications, get, */*, allow
    p, role:viewer, repositories, get, *, allow

    # OIDC mapping
    g, yawl-admins@example.com, role:admin
    g, yawl-developers@example.com, role:developer
    g, yawl-viewers@example.com, role:viewer

  # OIDC Integration
  oidc.config: |
    name: GitHub
    issuer: https://token.actions.githubusercontent.com
    clientID: arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com
    clientSecret: $github-oidc-secret
    requestedIDTokenAudience: sts.amazonaws.com
    requestedScopes:
      - openid
      - profile
      - email
    userIDKey: sub
    userNameKey: aud
    groupsKey: groups

  # Webhook Configuration
  url: https://argocd.example.com
  webhook.github.secret: $github-webhook-secret

  # Notification Configuration
  notificationaccount: argocd-notification
  applicationInstanceLabelKey: argocd.argoproj.io/instance

  # Sync Policy
  server.insecure: "false"
  server.logformat: json
  server.grpc.max_size_mb: 200
```

### 3.3 ApplicationSet for Multi-Environment Deployment

**File:** `/home/user/yawl/.github/argocd/applicationset.yaml`

```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: yawl-environments
  namespace: argocd
spec:
  generators:
    # Matrix generator for env x component combinations
    - matrix:
        generators:
          - list:
              elements:
                - name: dev
                  cluster: dev-cluster
                  namespace: yawl-dev
                  syncWave: 0
                  canary: "false"

                - name: staging
                  cluster: staging-cluster
                  namespace: yawl-staging
                  syncWave: 1
                  canary: "true"

                - name: production
                  cluster: prod-cluster
                  namespace: yawl-prod
                  syncWave: 2
                  canary: "false"

          - list:
              elements:
                - component: yawl-engine
                  chart: ./helm/yawl-parent

                - component: yawl-stateless
                  chart: ./helm/yawl-parent

                - component: yawl-integration
                  chart: ./helm/yawl-parent

  template:
    metadata:
      name: 'yawl-{{name}}-{{component}}'
      labels:
        env: '{{name}}'
        component: '{{component}}'
    spec:
      project: default
      source:
        repoURL: https://github.com/yawlfoundation/yawl-gitops
        targetRevision: main
        path: 'helm/{{component}}'
        helm:
          releaseName: 'yawl-{{component}}'
          values: |
            replicaCount: {{ if eq name "production" }}3{{ else }}1{{ end }}
            image:
              tag: 'v6.0.0'
            env: '{{name}}'
            component: '{{component}}'
          valuesObject:
            # Environment-specific values
            canary:
              enabled: {{ if eq canary "true" }}true{{ else }}false{{ end }}

      destination:
        server: 'https://{{cluster}}'
        namespace: '{{namespace}}'

      syncPolicy:
        automated:
          prune: true
          selfHeal: true
          allowEmpty: false
        syncOptions:
          - CreateNamespace=true
          - PrunePropagationPolicy=foreground
          - PruneLast=true
          - RespectIgnoreDifferences=true
        retry:
          limit: 5
          backoff:
            duration: 5s
            factor: 2
            maxDuration: 3m
```

---

## 4. Artifact Management Strategy

### 4.1 Semantic Versioning Scheme

```
YAWL v6.0.0-PRERELEASE+BUILD_METADATA
         │ │ │ │         │              │
         │ │ │ │         │              └─ Build metadata (optional)
         │ │ │ │         └──────────────── Pre-release tag
         │ │ │ └──────────────────────── Patch version
         │ │ └────────────────────────── Minor version
         │ └──────────────────────────── Major version
         └────────────────────────────── Version prefix

Examples:
- 6.0.0              Production release
- 6.0.1              Patch release (bugfixes)
- 6.1.0              Minor release (features, backwards compatible)
- 7.0.0              Major release (breaking changes)
- 6.1.0-alpha.1      Alpha pre-release
- 6.1.0-beta.2       Beta pre-release
- 6.1.0-rc.3         Release candidate
- 6.0.0-hotfix.1     Hotfix release
- 6.0.0+20260217.abc123  Build metadata (Git commit)
```

### 4.2 Artifact Types and Paths

```
Nexus Repository Structure:
├── releases/
│   └── org/yawlfoundation/yawl/
│       ├── yawl-parent/6.0.0/
│       ├── yawl-engine/6.0.0/
│       ├── yawl-stateless/6.0.0/
│       └── ...
│
├── snapshots/
│   └── org/yawlfoundation/yawl/
│       ├── yawl-parent/6.0.0-SNAPSHOT/
│       ├── yawl-engine/6.0.0-SNAPSHOT/
│       └── ...
│
└── sbom/
    └── org/yawlfoundation/yawl/
        └── 6.0.0/
            ├── yawl-parent-6.0.0.spdx.json
            ├── yawl-parent-6.0.0.cyclonedx.xml
            ├── yawl-parent-6.0.0.sbom.spdx.txt
            └── checksums/
                ├── checksums.sha256
                └── checksums.asc
```

### 4.3 Artifact Signing and Verification

**GPG Signing Process:**
1. Build artifact
2. Generate SHA-256 checksum
3. Sign checksum with project GPG key
4. Upload artifact, checksum, and signature
5. Publish public GPG key to keyserver

**Cosign Signing Process (Container Images):**
1. Build OCI image
2. Push to registry
3. Generate cosign signature with private key
4. Upload signature to OCI registry
5. Verify with public key (SBOM included in signature)

### 4.4 Artifact Metadata Tracking

```json
{
  "artifact": {
    "id": "yawl-engine-6.0.0.jar",
    "type": "maven-jar",
    "version": "6.0.0",
    "releaseDate": "2026-02-17T10:30:00Z",
    "buildNumber": "12345",
    "gitCommit": "abc123def456",
    "gitTag": "v6.0.0",
    "gitBranch": "main"
  },
  "build": {
    "duration": 845,
    "status": "success",
    "javaVersion": "25",
    "mavenVersion": "3.9.9",
    "profiles": ["java25", "prod"],
    "skipTests": false
  },
  "quality": {
    "testCoverage": 82.5,
    "criticalIssues": 0,
    "highIssues": 2,
    "mediumIssues": 12,
    "codeSmells": 45
  },
  "security": {
    "criticalVulnerabilities": 0,
    "highVulnerabilities": 0,
    "scanDate": "2026-02-17T10:35:00Z",
    "scanTool": "OWASP Dependency-Check"
  },
  "signatures": {
    "gpg": {
      "keyId": "0x1234567890ABCDEF",
      "signature": "-----BEGIN PGP SIGNATURE-----",
      "algorithm": "RSA-4096"
    },
    "cosign": {
      "imageDigest": "sha256:abcdef123456",
      "signaturePresent": true,
      "sbomPresent": true
    }
  },
  "provenance": {
    "generator": "github-actions",
    "invocationId": "https://github.com/yawlfoundation/yawl/actions/runs/12345",
    "sourceRepository": "https://github.com/yawlfoundation/yawl",
    "completeness": 0.95,
    "reproducible": true
  }
}
```

---

## 5. Release Automation

### 5.1 Release Cut Process

```
Developer commits → develop branch
                ↓
    [Nightly CI/CD runs]
                ↓
    QA approval on staging
                ↓
    Trigger Release Workflow
    - Create release/X.Y.Z branch
    - Update version in pom.xml
    - Generate CHANGELOG entries
    - Create git tag vX.Y.Z
    - Build release artifacts
    - Sign artifacts with GPG
    - Generate SBOM (SPDX + CycloneDX)
    - Create GitHub Release (with notes)
    - Push artifacts to Nexus
    - Create Docker image tags
    - Push to Docker registry
    - Create Helm chart release
    - Tag commit: [skip ci]
                ↓
    Promote to Staging ArgoCD
    (Automated, with approval gate)
                ↓
    Canary test in staging (15 min)
    - Health checks
    - Smoke tests
    - Performance baseline
                ↓
    Manual approval for Production
    (Slack notification with approval link)
                ↓
    Promote to Production ArgoCD
    (Blue-green deployment)
                ↓
    Post-deployment verification
    - Health checks
    - Smoke tests
    - Distributed tracing analysis
                ↓
    Success! Announce release
```

### 5.2 Hotfix Branching Strategy

```
Production issue detected
        ↓
Create hotfix/X.Y.(Z+1) from main
        ↓
  Apply minimal fix
        ↓
  Run full test suite
        ↓
  Merge back to main
  Merge back to develop
        ↓
  Create vX.Y.(Z+1) release tag
        ↓
  Build and push artifacts
        ↓
  Deploy to Production (blue-green)
        ↓
  Document in CHANGELOG.md
```

---

## 6. Software Bill of Materials (SBOM)

### 6.1 SBOM Generation Strategy

**Tools:**
- CycloneDX Maven Plugin (for Java dependencies)
- Syft (for OS-level packages in Docker images)
- cyclonedx-npm-plugin (for Node.js deps if applicable)

**Generation Points:**
1. Maven build: Generate CycloneDX SBOM
2. Docker image build: Generate Syft SBOM for image layers
3. Helm chart: Generate SBOM for chart dependencies
4. Aggregate: Combine all SBOMs into unified document

### 6.2 SBOM Validation and Compliance

**Validation Checklist:**
- All direct and transitive dependencies documented
- License information present for all components
- Vulnerability information from CVE databases
- Component provenance (source repository URL)
- Version pinning enforced
- No suspicious or untrusted components

**Compliance Checks:**
- License compatibility (e.g., no GPL in proprietary builds)
- Vulnerability severity thresholds
- Known bad components list
- Supply chain risk assessment

### 6.3 SBOM Publication

```
CycloneDX Format:
https://maven.pkg.github.com/yawlfoundation/yawl/sbom/yawl-engine/6.0.0/yawl-engine-6.0.0.cyclonedx.xml

SPDX Format:
https://maven.pkg.github.com/yawlfoundation/yawl/sbom/yawl-engine/6.0.0/yawl-engine-6.0.0.spdx.json

Signed SBOM:
https://ghcr.io/yawlfoundation/yawl-engine:6.0.0.sbom

Accessible via:
- Cosign sbom fetch ghcr.io/yawlfoundation/yawl-engine:6.0.0
- curl -H "Accept: application/vnd.cyclonedx+json" ...
```

---

## 7. Deployment Automation Patterns

### 7.1 Blue-Green Deployment

```
Production Cluster:

┌─────────────────────────────────────┐
│         Ingress/Load Balancer       │
│    (Routes 100% traffic to Blue)    │
└──────────────────┬──────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        v                     v
┌──────────────┐      ┌──────────────┐
│ Blue (v6.0)  │      │ Green (v5.9) │
│ Running      │      │ Idle         │
│ 3 replicas   │      │ 0 replicas   │
└──────────────┘      └──────────────┘

Deployment Steps:
1. Scale Green to match Blue (3 replicas)
2. Deploy v6.0.1 to Green
3. Run smoke tests against Green
4. Switch ingress: 100% → Green
5. Run production tests
6. Keep Blue for rollback (5 min)
7. Scale Blue to 0
8. Swap: Green becomes new Blue

Rollback:
1. Switch ingress: 100% → Blue
2. Restore Blue from previous state
3. Done! (30 seconds)
```

### 7.2 Canary Deployment

```
Staging Cluster:

┌──────────────────────────────┐
│    Ingress/Load Balancer     │
│  (Initially 95/5 split)      │
└────────────┬─────────────────┘
             │
     ┌───────┴────────┐
     │                │
     v                v
┌─────────────┐   ┌──────────┐
│ Stable v5.9 │   │Canary v6.0│
│ 95% traffic │   │ 5% traffic│
│ 3 replicas  │   │ 1 replica │
└─────────────┘   └──────────┘

Progressive Rollout:
Time  | Stable | Canary | Status
------|--------|--------|--------
  0m  |  95%   |  5%    | Start
  5m  |  85%   | 15%    | Monitor
 10m  |  70%   | 30%    | Check metrics
 15m  |  50%   | 50%    | Run tests
 20m  |  30%   | 70%    | Full validation
 25m  |  10%   | 90%    | Final checks
 30m  |  0%    | 100%   | Complete

Metrics Monitored:
- Error rate (< 0.1% threshold)
- Latency (p99 < 100ms)
- Memory usage (< 80% threshold)
- CPU usage (< 70% threshold)
- Business metrics (transaction volume)

Auto-Rollback Triggers:
- Error rate > 0.5%
- Latency p99 > 500ms
- Memory leak detection
- OOM kill events
```

### 7.3 Progressive Rollout Strategy

```
Multi-Zone Production Deployment:

US-EAST-1a Zone     US-EAST-1b Zone     US-EAST-1c Zone
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│v5.9 (stable) │    │v5.9 (stable) │    │v5.9 (stable) │
│  3 replicas  │    │  3 replicas  │    │  3 replicas  │
└──────────────┘    └──────────────┘    └──────────────┘

Phase 1: Deploy to zone A
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│v6.0 (new)    │    │v5.9 (stable) │    │v5.9 (stable) │
│  3 replicas  │    │  3 replicas  │    │  3 replicas  │
└──────────────┘    └──────────────┘    └──────────────┘
         ↓           Monitor (10 min)

Phase 2: Deploy to zone B
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│v6.0 (new)    │    │v6.0 (new)    │    │v5.9 (stable) │
│  3 replicas  │    │  3 replicas  │    │  3 replicas  │
└──────────────┘    └──────────────┘    └──────────────┘
         ↓           Monitor (10 min)

Phase 3: Deploy to zone C
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│v6.0 (new)    │    │v6.0 (new)    │    │v6.0 (new)    │
│  3 replicas  │    │  3 replicas  │    │  3 replicas  │
└──────────────┘    └──────────────┘    └──────────────┘

Automated Rollback per Zone:
- If errors detected in phase N, rollback zone N
- Keep previously successful zones on v6.0
- Manual intervention required for full rollback
```

---

## 8. Secret Management with HashiCorp Vault

### 8.1 Vault Integration Architecture

```
┌─────────────────────────────────────┐
│       HashiCorp Vault                │
│   (Central Secret Store)             │
└────┬────────────────────┬────────────┘
     │                    │
     v                    v
┌──────────────┐   ┌────────────────────┐
│ Kubernetes   │   │ CI/CD Pipeline     │
│ Secrets      │   │ (GitHub Actions)   │
│ (Injected)   │   │ (OAuth OIDC)       │
└──────────────┘   └────────────────────┘

Authentication Methods:
1. Kubernetes Auth (pods)
   - ServiceAccount token + namespace
   - Fine-grained RBAC per namespace

2. GitHub OIDC (Actions)
   - JWT token from GitHub
   - Claims: repo, branch, environment
   - No long-lived credentials

3. OIDC Federation (External)
   - AWS, GCP, Azure STS tokens
   - Machine-identifiable credentials
```

### 8.2 Secret Rotation Policies

```yaml
# Database credentials rotation
engine: database
path: secret/data/yawl/prod/db
config:
  connection_uri: "postgresql://user:pass@host:5432/yawl"
  rotation:
    ttl: 2592000  # 30 days
    statements:
      - "ALTER USER {{name}} WITH PASSWORD '{{password}}';"

# API keys rotation
engine: generic
path: secret/data/yawl/prod/api-keys
config:
  rotation:
    ttl: 1814400  # 21 days
    provider: custom-rotation-function

# TLS certificates renewal
engine: pki
path: pki/issue/yawl-prod
config:
  ttl: 8760h      # 1 year
  auto_rotate: true
```

### 8.3 Secure Credential Injection

**In GitHub Actions:**
```yaml
- name: Fetch secrets from Vault
  uses: hashicorp/vault-action@v2
  with:
    url: https://vault.example.com
    path: jwt
    role: github-yawl-ci
    jwtGithubAudience: https://github.com/yawlfoundation
    secrets: |
      secret/data/yawl/ci/nexus-user | NEXUS_USER
      secret/data/yawl/ci/nexus-pass | NEXUS_PASS
      secret/data/yawl/ci/gpg-key | GPG_PRIVATE_KEY
      secret/data/yawl/ci/docker-token | DOCKER_TOKEN
```

**In Kubernetes (Vault Agent):**
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: yawl-engine-pod
spec:
  serviceAccountName: yawl-engine
  containers:
  - name: yawl
    image: ghcr.io/yawlfoundation/yawl-engine:6.0.0
    env:
    - name: DATABASE_PASSWORD
      valueFrom:
        secretKeyRef:
          name: yawl-db-creds
          key: password
  initContainers:
  - name: vault-init
    image: vault:1.15
    args:
    - "agent"
    - "-config=/vault/config/agent.hcl"
    - "-exit-after-auth"
    volumeMounts:
    - name: vault-config
      mountPath: /vault/config
```

---

## 9. Production-Grade Observability

### 9.1 Distributed Tracing Integration

```yaml
# OpenTelemetry Configuration
otel:
  exporter:
    otlp:
      endpoint: https://otel-collector.example.com:4317
      headers:
        Authorization: Bearer $OTEL_TOKEN

  sampling:
    probability: 0.1  # Sample 10% of traces
    rules:
      - service_name: yawl-engine
        probability: 0.5  # 50% for engine
      - operation: /health
        probability: 0.01  # 1% for health checks

  attributes:
    service.name: yawl-engine
    service.version: 6.0.0
    deployment.environment: production
    deployment.availability_zone: us-east-1a

  span_processors:
    - batch:
        schedule_delay_millis: 5000
        max_queue_size: 2048
        max_export_batch_size: 512
```

### 9.2 Real-Time Alerting Rules

```yaml
groups:
- name: yawl-production
  interval: 30s
  rules:
  # Deployment failure
  - alert: DeploymentFailure
    expr: |
      increase(deployment_failed_total[5m]) > 0
    for: 5m
    annotations:
      summary: "Deployment failure detected"
      severity: critical
      action: "Page on-call; rollback deployment"

  # High error rate
  - alert: HighErrorRate
    expr: |
      rate(http_requests_failed_total[5m]) > 0.001
    for: 5m
    annotations:
      summary: "Error rate > 0.1%"
      severity: warning

  # Database connection pool exhaustion
  - alert: DBPoolExhaustion
    expr: |
      hikaricp_connections_active / hikaricp_connections_max > 0.9
    for: 5m
    annotations:
      summary: "Database connection pool > 90%"
      severity: critical

  # Memory pressure
  - alert: HighMemoryUsage
    expr: |
      process_resident_memory_bytes / node_memory_MemTotal_bytes > 0.85
    for: 10m
    annotations:
      summary: "Memory usage > 85%"
      severity: warning
```

---

## 10. Security and Compliance

### 10.1 Supply Chain Security (SLSA Framework)

**SLSA Level 3 Implementation:**

```
Build Requirements:
✓ Version control (git with signed commits)
✓ CI/CD platform (GitHub Actions)
✓ Build isolation (separate VM per build)
✓ Auditable provenance (Build log retention)
✓ Artifact signing (GPG + Cosign)
✓ Artifact hashing (SHA-256 + SRI)

Provenance Format (SLSA v1.0):
{
  "_type": "https://in-toto.io/Statement/v0.1",
  "subject": [{
    "name": "yawl-engine-6.0.0.jar",
    "digest": {
      "sha256": "abc123..."
    }
  }],
  "predicateType": "https://slsa.dev/provenance/v0.2",
  "predicate": {
    "builder": {
      "id": "https://github.com/yawlfoundation/yawl/.github/workflows/ci.yml@refs/tags/v6.0.0"
    },
    "buildType": "https://github.com/yawlfoundation/yawl/build-types/maven",
    "invocation": {
      "configSource": {
        "uri": "https://github.com/yawlfoundation/yawl",
        "digest": {
          "sha1": "commit-hash"
        },
        "entryPoint": ".github/workflows/ci.yml"
      },
      "parameters": {
        "MAVEN_PROFILE": "java25",
        "JAVA_VERSION": "25"
      },
      "environment": {
        "os": "Ubuntu 22.04 LTS",
        "arch": "x86_64"
      }
    },
    "materials": [
      {
        "uri": "git+https://github.com/yawlfoundation/yawl@refs/tags/v6.0.0",
        "digest": {
          "sha1": "commit-hash"
        }
      }
    ],
    "completeness": {
      "parameters": true,
      "environment": true,
      "materials": true
    },
    "reproducible": true
  }
}
```

### 10.2 Vulnerability Scanning

**Multi-Layer Scanning:**
1. **Dependencies** (OWASP Dependency-Check)
2. **Container images** (Trivy + Grype)
3. **Code** (CodeQL + SAST tools)
4. **Configuration** (Kubernetes + Helm)
5. **Secrets** (TruffleHog + git-secrets)

**Scanning Schedule:**
- Per-commit: Dependency check, code scanning
- Nightly: Full container image scan, secret scan
- Weekly: Configuration audit, policy review

---

## 11. Disaster Recovery & Rollback

### 11.1 Automated Rollback Triggers

```
Condition                          | Action
-----------------------------------|---------------------------
Error rate > 1% for 5 min          | Immediate rollback
Latency p99 > 1 second for 5 min   | Immediate rollback
Memory leak detected (30% growth)   | Gradual rollback + alert
Database connection failures > 50% | Pause deploy + alert
Health check failures > 10%        | Immediate rollback
Custom metric threshold breach     | Gradual rollback

Rollback Procedure:
1. Detect anomaly via monitoring
2. Trigger automatic rollback in Argo CD
3. Revert to previous stable version
4. Run smoke tests
5. Notify on-call engineer
6. Create incident ticket
7. Begin root cause analysis
```

### 11.2 Backup and Recovery

```yaml
# Persistent Volume Backups
backup:
  schedule: "0 2 * * *"  # Daily at 2 AM
  retention: 30 days
  destinations:
    - s3://yawl-backups/prod/
    - gcs://yawl-backups/prod/

  # Database backups
  databases:
    - name: yawl_prod
      method: pg_dump
      retention: 90 days
      verification: integrity-check

  # Helm chart state
  helm_state: true
  retention: 90 days

# Disaster Recovery Plan
rto: 1 hour      # Recovery Time Objective
rpo: 15 minutes  # Recovery Point Objective

Scenarios:
1. Single pod failure
   - K8s auto-restarts (< 30 seconds)

2. Zone failure
   - Multi-AZ redundancy
   - Traffic rerouted (< 5 minutes)

3. Region failure
   - Manual failover to secondary region
   - DNS update required (< 10 minutes)

4. Data corruption
   - Restore from point-in-time backup
   - Validation against SLSA provenance
```

---

## 12. Metrics and KPIs

### 12.1 Pipeline Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Build Time | < 15 min | Total time from commit to artifact |
| Test Coverage | ≥ 80% | Code coverage (JaCoCo) |
| Security Issues | 0 critical | OWASP + CodeQL findings |
| Deployment Frequency | 1x weekly | Releases to production |
| Lead Time | < 7 days | Commit to production |
| MTTR | < 30 min | Mean time to recovery |
| MTBF | > 30 days | Mean time between failures |
| Rollback Rate | < 5% | % of deployments rolled back |

### 12.2 Deployment Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Deployment Success Rate | ≥ 99% | Successful deployments |
| Zero-downtime Deployments | 100% | Deployments with no service interruption |
| Canary Detection Time | < 5 min | Time to detect canary issues |
| Rollback Time | < 5 min | Time from detection to rollback complete |
| Health Check Pass Rate | ≥ 99.5% | Post-deployment health checks |

---

## 13. Cost Optimization

### 13.1 Resource Utilization

- **Container image optimization**: Multi-stage builds, distroless bases
- **Registry cleanup**: Auto-delete untagged images after 30 days
- **Artifact retention**: Keep 5 latest minor versions, 3 latest patch versions
- **Build optimization**: Parallel test execution, dependency caching
- **Infrastructure**: Right-sizing based on actual usage

### 13.2 Pipeline Efficiency

- **Scheduled builds**: Run expensive scans during off-peak hours
- **Build matrix**: Only test critical Java versions (skip optional ones)
- **Docker layer caching**: Leverage buildx cache across runs
- **Maven cache**: Share ~/.m2/repository across builds

---

## 14. Implementation Roadmap

### Phase 1 (Week 1-2): Foundation
- Set up GitHub Actions base workflows
- Configure Maven artifact publishing
- Implement basic security scanning
- Create SBOM generation pipeline

### Phase 2 (Week 3-4): GitOps & Deployment
- Deploy ArgoCD to dev/staging/prod
- Create Helm charts for all components
- Implement blue-green deployments
- Set up canary testing

### Phase 3 (Week 5-6): Secret Management & Security
- Deploy HashiCorp Vault
- Integrate OIDC for GitHub Actions
- Implement secret rotation
- Add cosign image signing

### Phase 4 (Week 7-8): Observability & Compliance
- Deploy OpenTelemetry collector
- Configure distributed tracing
- Implement alerting rules
- Document SLSA compliance

### Phase 5 (Week 9+): Optimization & Hardening
- Performance tuning
- Cost optimization
- Security hardening
- Disaster recovery testing

---

## 15. References and Resources

- [SLSA Framework](https://slsa.dev/)
- [ArgoCD Documentation](https://argo-cd.readthedocs.io/)
- [SBOM Best Practices](https://www.ntia.gov/files/ntia/publications/sbom_formats_use_cases_and_preferences_050621_0.pdf)
- [HashiCorp Vault](https://www.vaultproject.io/)
- [OpenTelemetry](https://opentelemetry.io/)
- [Kubernetes Security](https://kubernetes.io/docs/concepts/security/)

---

**Document Version:** 1.0.0
**Last Updated:** February 17, 2026
**Next Review:** May 17, 2026
