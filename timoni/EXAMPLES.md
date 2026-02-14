# YAWL Timoni Module - Usage Examples

This document provides practical examples for deploying YAWL using the Timoni module.

## Table of Contents

1. [Basic Deployment](#basic-deployment)
2. [Environment-Specific Deployments](#environment-specific-deployments)
3. [Advanced Configuration](#advanced-configuration)
4. [Troubleshooting](#troubleshooting)
5. [CI/CD Integration](#cicd-integration)

## Basic Deployment

### Prerequisite Setup

Before deploying, ensure your Kubernetes cluster is configured and database secrets are created:

```bash
# Login to your GCP project
gcloud auth login
gcloud config set project my-gcp-project

# Get cluster credentials
gcloud container clusters get-credentials yawl-cluster --zone us-central1-a

# Verify cluster connection
kubectl cluster-info
```

### Create Database Secret

```bash
# For production
kubectl create namespace yawl-prod
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='your-secure-db-password' \
  -n yawl-prod

# For staging
kubectl create namespace yawl-staging
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='your-secure-db-password' \
  -n yawl-staging

# For development
kubectl create namespace yawl-dev
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='your-secure-db-password' \
  -n yawl-dev
```

### Basic Production Deployment

```bash
cd /home/user/yawl/timoni

# Dry run to preview changes
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --namespace yawl-prod \
  --dry-run

# Apply deployment
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --namespace yawl-prod
```

### Verify Deployment

```bash
# Watch pod startup
kubectl get pods -n yawl-prod -w

# Check deployment status
kubectl rollout status deployment/yawl -n yawl-prod

# View pod logs
kubectl logs -n yawl-prod -l app=yawl -f

# Check service endpoints
kubectl get svc -n yawl-prod
```

## Environment-Specific Deployments

### Development Deployment

For quick local testing with minimal resources:

```bash
./deploy.sh development apply
```

This uses `values-development.cue` which provides:
- Single replica for minimal resource usage
- DEBUG logging for detailed troubleshooting
- Disabled health probes for faster iteration
- Always pull latest image tag

### Staging Deployment

For pre-production testing with moderate resources:

```bash
./deploy.sh staging apply
```

This uses `values-staging.cue` which provides:
- Three replicas for redundancy
- INFO logging level
- Resource requests/limits suitable for testing
- Pod anti-affinity enabled

### Production Deployment

For production workloads with full HA/DR:

```bash
./deploy.sh production apply
```

This uses `values-production.cue` which provides:
- Five replicas with HPA (scales to 15)
- WARN logging level
- High resource requests/limits
- Pod disruption budget enforced
- Network policies enabled
- Node affinity for specific node pools

## Advanced Configuration

### Custom Image Registry

Override the image registry for your specific setup:

```bash
# Create a custom values file
cat > values-custom-registry.cue << 'EOF'
package main

values: {
  image: {
    registry: "docker.io"
    repository: "yawlfoundation/yawl"
    tag: "1.0.0"
    pullPolicy: "IfNotPresent"
  }
}
EOF

# Deploy with custom registry
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-custom-registry.cue \
  --namespace yawl-prod
```

### Custom Resource Allocation

Override resource requests and limits:

```bash
cat > values-high-resource.cue << 'EOF'
package main

values: {
  resources: {
    requests: {
      cpu: "4"
      memory: "8Gi"
    }
    limits: {
      cpu: "8"
      memory: "16Gi"
    }
  }
}
EOF

timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-high-resource.cue \
  --namespace yawl-prod
```

### Multiple Cloud SQL Instances

Configure failover or read replicas:

```bash
cat > values-multi-sql.cue << 'EOF'
package main

values: {
  cloudSQLProxy: {
    instances: [
      "my-project:us-central1:yawl-postgres-primary=tcp:5432",
      "my-project:us-central1:yawl-postgres-replica=tcp:5433",
    ]
  }
}
EOF

timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-multi-sql.cue \
  --namespace yawl-prod
```

### Enable Detailed SQL Logging

For debugging database issues:

```bash
cat > values-debug-db.cue << 'EOF'
package main

values: {
  database: {
    showSQL: true
    formatSQL: true
    generateStatistics: true
  }
  logging: {
    level: "DEBUG"
  }
}
EOF

timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-debug-db.cue \
  --namespace yawl-prod
```

### Custom JVM Tuning

Optimize garbage collection for your workload:

```bash
cat > values-jvm-tuning.cue << 'EOF'
package main

values: {
  jvm: {
    heapSize: "8192m"
    initialHeap: "4096m"
    maxHeap: "8192m"
    gcType: "G1GC"
    gcPauseTarget: 100
    additionalOptions: [
      "-XX:+UnlockExperimentalVMOptions",
      "-XX:G1NewCollectionPercentThreshold=25",
      "-XX:G1MaxNewGenPercent=50",
      "-XX:+ParallelRefProcEnabled",
      "-XX:+AlwaysPreTouch",
      "-XX:+UseStringDeduplication",
    ]
  }
}
EOF

timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-jvm-tuning.cue \
  --namespace yawl-prod
```

### Connect to Multiple Node Pools

Distribute workload across specific node pools:

```bash
cat > values-node-pools.cue << 'EOF'
package main

values: {
  affinity: {
    nodeAffinity: {
      enabled: true
      preferredDuringScheduling: {
        weight: 100
        key: "cloud.google.com/gke-nodepool"
        values: ["yawl-pool-1", "yawl-pool-2", "yawl-pool-3"]
      }
    }
  }
}
EOF

timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-node-pools.cue \
  --namespace yawl-prod
```

## Troubleshooting

### Check Pod Status

```bash
# Get all pods in namespace
kubectl get pods -n yawl-prod

# Get detailed pod information
kubectl describe pod <pod-name> -n yawl-prod

# Check pod events
kubectl get events -n yawl-prod --sort-by='.lastTimestamp'
```

### View Application Logs

```bash
# YAWL container logs
kubectl logs <pod-name> -c yawl -n yawl-prod

# Cloud SQL Proxy logs
kubectl logs <pod-name> -c cloud-sql-proxy -n yawl-prod

# Follow logs in real-time
kubectl logs -f <pod-name> -n yawl-prod --all-containers=true

# Get logs from previous pod (if it crashed)
kubectl logs <pod-name> -n yawl-prod --previous
```

### Test Health Endpoints

```bash
# Port forward to service
kubectl port-forward svc/yawl 8080:80 -n yawl-prod

# Test health endpoint
curl -v http://localhost:8080/resourceService/

# Test metrics endpoint
curl http://localhost:8080/metrics
```

### Database Connectivity Issues

```bash
# Check Cloud SQL Proxy connection
kubectl exec <pod-name> -c cloud-sql-proxy -n yawl-prod -- ps aux

# Test database connectivity from pod
kubectl exec <pod-name> -c yawl -n yawl-prod -- \
  psql -h cloudsql-proxy -U yawl -d yawl -c "SELECT version();"

# Check database credentials secret
kubectl get secret yawl-db-credentials -n yawl-prod -o jsonpath='{.data.password}' | base64 -d
```

### Resource Usage

```bash
# Check current resource usage
kubectl top pods -n yawl-prod -l app=yawl

# Check namespace resource quota
kubectl describe resourcequota -n yawl-prod

# Get pod resource requests and limits
kubectl describe pod <pod-name> -n yawl-prod | grep -A 5 "Limits\|Requests"
```

### Generate Manifest Manually

```bash
# Export resources as YAML
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --namespace yawl-prod \
  --dry-run --output yaml > deployment.yaml

# Review the manifest
cat deployment.yaml

# Apply manually
kubectl apply -f deployment.yaml
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Deploy YAWL to Production

on:
  push:
    branches:
      - main
    paths:
      - 'timoni/**'

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Setup gcloud
        uses: google-github-actions/setup-gcloud@v1
        with:
          project_id: ${{ secrets.GCP_PROJECT_ID }}
          service_account_key: ${{ secrets.GCP_SA_KEY }}

      - name: Install timoni
        run: |
          curl -sL https://timoni.sh/install.sh | sh

      - name: Get cluster credentials
        run: |
          gcloud container clusters get-credentials yawl-cluster \
            --zone us-central1-a

      - name: Create namespace and secrets
        run: |
          kubectl create namespace yawl-prod --dry-run=client -o yaml | kubectl apply -f -
          kubectl create secret generic yawl-db-credentials \
            --from-literal=password='${{ secrets.DB_PASSWORD }}' \
            -n yawl-prod \
            --dry-run=client -o yaml | kubectl apply -f -

      - name: Deploy with timoni
        run: |
          cd timoni
          timoni bundle apply yawl \
            --values values.cue \
            --values values-production.cue \
            --namespace yawl-prod \
            --timeout 5m

      - name: Verify deployment
        run: |
          kubectl rollout status deployment/yawl \
            -n yawl-prod \
            --timeout 5m
```

### GitLab CI Example

```yaml
deploy-production:
  stage: deploy
  image: ubuntu:latest
  script:
    - apt-get update && apt-get install -y curl kubectl
    - curl -sL https://timoni.sh/install.sh | sh
    - gcloud container clusters get-credentials yawl-cluster --zone us-central1-a
    - kubectl create namespace yawl-prod --dry-run=client -o yaml | kubectl apply -f -
    - kubectl create secret generic yawl-db-credentials
        --from-literal=password=$DB_PASSWORD
        -n yawl-prod
        --dry-run=client -o yaml | kubectl apply -f -
    - cd timoni
    - timoni bundle apply yawl
        --values values.cue
        --values values-production.cue
        --namespace yawl-prod
    - kubectl rollout status deployment/yawl -n yawl-prod
  only:
    - main
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    environment {
        GCP_PROJECT_ID = credentials('gcp-project-id')
        DB_PASSWORD = credentials('db-password')
        CLUSTER_NAME = 'yawl-cluster'
        CLUSTER_ZONE = 'us-central1-a'
    }

    stages {
        stage('Setup') {
            steps {
                sh '''
                    curl -sL https://timoni.sh/install.sh | sh
                    gcloud container clusters get-credentials $CLUSTER_NAME \
                        --zone $CLUSTER_ZONE
                '''
            }
        }

        stage('Create Secrets') {
            steps {
                sh '''
                    kubectl create namespace yawl-prod --dry-run=client -o yaml | kubectl apply -f -
                    kubectl create secret generic yawl-db-credentials \
                        --from-literal=password=$DB_PASSWORD \
                        -n yawl-prod \
                        --dry-run=client -o yaml | kubectl apply -f -
                '''
            }
        }

        stage('Deploy') {
            steps {
                dir('timoni') {
                    sh '''
                        timoni bundle apply yawl \
                            --values values.cue \
                            --values values-production.cue \
                            --namespace yawl-prod \
                            --timeout 5m
                    '''
                }
            }
        }

        stage('Verify') {
            steps {
                sh 'kubectl rollout status deployment/yawl -n yawl-prod'
            }
        }
    }
}
```

## Rollback Procedures

### Rollback to Previous Deployment

```bash
# View rollout history
kubectl rollout history deployment/yawl -n yawl-prod

# Rollback to previous version
kubectl rollout undo deployment/yawl -n yawl-prod

# Rollback to specific revision
kubectl rollout undo deployment/yawl -n yawl-prod --to-revision=2

# Watch rollback progress
kubectl rollout status deployment/yawl -n yawl-prod -w
```

### Manual Rollback via Timoni

```bash
# Apply previous values configuration
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue.backup \
  --namespace yawl-prod
```

## Upgrade Procedures

### Rolling Update with Zero Downtime

```bash
# Update image tag in values file
cat > values-upgrade.cue << 'EOF'
package main

values: {
  image: {
    tag: "1.0.1"  // New version
  }
}
EOF

# Apply update
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values values-upgrade.cue \
  --namespace yawl-prod

# Monitor rollout
kubectl rollout status deployment/yawl -n yawl-prod -w
```

## Performance Testing

### Load Testing with Siege

```bash
# Port forward to service
kubectl port-forward svc/yawl 8080:80 -n yawl-prod &

# Install siege
sudo apt-get install siege

# Run load test
siege -c 50 -r 10 -b http://localhost:8080/resourceService/

# Stop port forward
kill %1
```

### Monitor Metrics During Load

```bash
# In another terminal, monitor pod resources
kubectl top pods -n yawl-prod -l app=yawl --containers -w

# Check HPA scaling
kubectl get hpa -n yawl-prod -w
```

## References

- [Timoni CLI Reference](https://timoni.sh/cli/)
- [Kubernetes Deployment Strategies](https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/)
- [Google Cloud SQL Proxy](https://cloud.google.com/sql/docs/postgres/cloud-sql-proxy)
