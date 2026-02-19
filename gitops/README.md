# YAWL Deployment Automation & DevOps

Complete deployment automation infrastructure for YAWL v6.0.0 with GitOps workflows, multi-environment support, and comprehensive monitoring.

## Directory Structure

```
yawl/
├── gitops/
│   └── argocd/
│       ├── apps/
│       │   ├── yawl-application.yaml      # Single ArgoCD application
│       │   └── yawl-application-set.yaml  # Multi-environment ApplicationSet
│       ├── projects/
│       │   └── yawl-project.yaml          # ArgoCD project with RBAC
│       └── base/
│           └── kustomization.yaml         # Kustomize base configuration
├── helm/
│   └── yawl/
│       ├── envs/
│       │   ├── values-dev.yaml            # Development environment values
│       │   ├── values-staging.yaml        # Staging environment values
│       │   └── values-production.yaml     # Production environment values
│       └── values.yaml                    # Base Helm values
├── monitoring/
│   ├── prometheus/
│   │   └── prometheus-stack.yaml          # kube-prometheus-stack config
│   ├── grafana/
│   │   └── dashboards/
│   │       └── yawl-engine-dashboard.json # Grafana dashboard
│   └── alertmanager/
│       └── alertmanager-config.yaml       # Alert routing configuration
├── terraform/
│   └── environments/
│       ├── dev/main.tf                    # Development infrastructure
│       ├── staging/main.tf                # Staging infrastructure
│       └── prod/main.tf                   # Production infrastructure
├── scripts/
│   └── ci-cd/
│       ├── deploy.sh                      # Multi-strategy deployment
│       ├── rollback.sh                    # Automated rollback
│       ├── smoke-tests.sh                 # Post-deployment validation
│       └── validate-production-readiness.sh
└── .github/
    └── workflows/
        ├── gitops-deploy.yml              # GitOps deployment pipeline
        ├── maven-ci-cd.yml                # Build and test pipeline
        ├── production-deployment.yml      # Production deployment
        └── security-scanning.yml          # Security scanning
```

## Quick Start

### 1. Prerequisites

- Kubernetes cluster (1.27+)
- Helm 3.x
- kubectl configured
- ArgoCD installed (for GitOps)
- Terraform 1.6+ (for infrastructure)

### 2. Deploy with Helm

```bash
# Development
helm install yawl ./helm/yawl \
  -f helm/yawl/values.yaml \
  -f helm/yawl/envs/values-dev.yaml \
  -n yawl-dev --create-namespace

# Staging
helm install yawl ./helm/yawl \
  -f helm/yawl/values.yaml \
  -f helm/yawl/envs/values-staging.yaml \
  -n yawl-staging --create-namespace

# Production
helm install yawl ./helm/yawl \
  -f helm/yawl/values.yaml \
  -f helm/yawl/envs/values-production.yaml \
  -n yawl-prod --create-namespace
```

### 3. Deploy with ArgoCD (GitOps)

```bash
# Install ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Apply YAWL project and application
kubectl apply -f gitops/argocd/projects/yawl-project.yaml -n argocd
kubectl apply -f gitops/argocd/apps/yawl-application.yaml -n argocd
```

### 4. Infrastructure with Terraform

```bash
cd terraform/environments/staging
terraform init
terraform apply -var-file=terraform.tfvars
```

## Deployment Strategies

### Rolling Update (Default)
Standard Kubernetes rolling update with zero downtime.

```bash
./scripts/ci-cd/deploy.sh -e staging -v 6.0.0 -s rolling
```

### Blue-Green Deployment
Deploy to inactive slot, verify, then switch traffic.

```bash
./scripts/ci-cd/deploy.sh -e production -v 6.0.1 -s blue-green
```

### Canary Deployment
Gradual traffic shift with automatic rollback on failure.

```bash
./scripts/ci-cd/deploy.sh -e production -v 6.0.1 -s canary
```

## Monitoring Setup

### Install Prometheus Stack

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  -f monitoring/prometheus/prometheus-stack.yaml
```

### Import Grafana Dashboard

The YAWL Engine dashboard is automatically provisioned when using the prometheus-stack.yaml configuration.

### Alert Routing

Configure alertmanager with your notification channels:

```bash
kubectl apply -f monitoring/alertmanager/alertmanager-config.yaml -n monitoring
```

## Rollback Procedures

### Automatic Rollback

The CI/CD pipeline automatically rolls back on deployment failure.

### Manual Rollback

```bash
# Rollback to previous version
./scripts/ci-cd/rollback.sh -e production

# Rollback to specific revision
./scripts/ci-cd/rollback.sh -e production -r 42

# Rollback with notification
./scripts/ci-cd/rollback.sh -e production --slack-webhook https://hooks.slack.com/...
```

### Helm Rollback

```bash
helm rollback yawl -n yawl-prod
```

### ArgoCD Rollback

```bash
argocd app rollback yawl-production <revision>
```

## Security Scanning

The pipeline includes multiple security scanning layers:

1. **Trivy** - Container vulnerability scanning
2. **Grype** - Secondary vulnerability scanner
3. **OWASP Dependency Check** - Java dependency analysis
4. **SBOM Generation** - CycloneDX and SPDX

## Multi-Environment Configuration

| Environment | Replicas | Database | Monitoring | Auto-Sync |
|-------------|----------|----------|------------|-----------|
| Dev | 1 | Single | Basic | Yes |
| Staging | 2-5 | Multi-AZ | Full | Yes |
| Production | 3-20 | HA + Read Replicas | Full | Manual |

## GitOps Workflow

1. Developer pushes code to GitHub
2. CI pipeline builds and tests
3. Container image pushed to registry
4. Helm values updated with new tag
5. ArgoCD detects change (or manual sync)
6. Application deployed to cluster
7. Post-deployment smoke tests run
8. Rollback on failure

## Configuration Management

### Secrets

Store secrets in cloud-native secret managers:

- **AWS**: AWS Secrets Manager + External Secrets Operator
- **GCP**: Secret Manager + External Secrets Operator
- **Azure**: Key Vault + External Secrets Operator

### ConfigMaps

Environment-specific configuration in Helm values files.

## CI/CD Pipeline Stages

1. **Build & Test** - Compile, unit tests, coverage
2. **Security Scan** - Trivy, OWASP, SBOM
3. **Container Build** - Multi-arch image, signing
4. **GitOps Update** - Update Helm values
5. **ArgoCD Sync** - Deploy to cluster
6. **Smoke Tests** - Validate deployment
7. **Rollback** - Automatic on failure
8. **Notify** - Slack, PagerDuty

## Troubleshooting

### Check Application Health

```bash
kubectl get all -n yawl-prod
kubectl logs -f deployment/yawl-engine -n yawl-prod
```

### Check ArgoCD Status

```bash
argocd app get yawl-production
argocd app history yawl-production
```

### Run Smoke Tests

```bash
./scripts/ci-cd/smoke-tests.sh production
```

### Validate Production Readiness

```bash
./scripts/ci-cd/validate-production-readiness.sh production
```

## Support

- Documentation: https://yawlfoundation.github.io/yawl/v6.0/
- Issues: https://github.com/yawlfoundation/yawl/issues
- Slack: #yawl-support
