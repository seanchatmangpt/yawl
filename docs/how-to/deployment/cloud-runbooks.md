# YAWL v6.0.0 Cloud Deployment Runbooks
**Enterprise Production Operations Guide**  
**Version**: 5.2  
**Date**: 2026-02-15

---

## Table of Contents

1. [GKE/GCP Deployment Runbook](#1-gkegcp-deployment-runbook)
2. [EKS/AWS Deployment Runbook](#2-eksaws-deployment-runbook)
3. [AKS/Azure Deployment Runbook](#3-aksazure-deployment-runbook)
4. [Observability Setup](#4-observability-setup)
5. [Troubleshooting Guide](#5-troubleshooting-guide)
6. [Incident Response Procedures](#6-incident-response-procedures)
7. [Disaster Recovery](#7-disaster-recovery)

---

## 1. GKE/GCP Deployment Runbook

### 1.1 Prerequisites

**Required Tools**:
```bash
# Install gcloud CLI
curl https://sdk.cloud.google.com | bash
gcloud init

# Install kubectl
gcloud components install kubectl

# Install SPIRE
wget https://github.com/spiffe/spire/releases/download/v1.8.0/spire-1.8.0-linux-amd64-musl.tar.gz
tar -xzf spire-1.8.0-linux-amd64-musl.tar.gz
```

**GCP Project Setup**:
```bash
# Set project
export PROJECT_ID="yawl-prod"
gcloud config set project $PROJECT_ID

# Enable APIs
gcloud services enable \
  container.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  cloudtrace.googleapis.com \
  logging.googleapis.com
```

---

### 1.2 Step-by-Step Deployment

#### Step 1: Create GKE Cluster

```bash
# Create production cluster
gcloud container clusters create yawl-prod \
  --region us-central1 \
  --node-locations us-central1-a,us-central1-b,us-central1-c \
  --machine-type n2-standard-4 \
  --num-nodes 1 \
  --enable-autoscaling \
  --min-nodes 1 \
  --max-nodes 3 \
  --disk-size 100 \
  --disk-type pd-ssd \
  --workload-pool=${PROJECT_ID}.svc.id.goog \
  --enable-stackdriver-kubernetes \
  --enable-ip-alias \
  --network "projects/${PROJECT_ID}/global/networks/default" \
  --subnetwork "projects/${PROJECT_ID}/regions/us-central1/subnetworks/default" \
  --addons HorizontalPodAutoscaling,HttpLoadBalancing,GcePersistentDiskCsiDriver \
  --maintenance-window-start "2026-02-16T04:00:00Z" \
  --maintenance-window-duration 4h \
  --release-channel regular

# Get credentials
gcloud container clusters get-credentials yawl-prod --region us-central1
```

#### Step 2: Create Cloud SQL Database

```bash
# Create PostgreSQL instance
gcloud sql instances create yawl-db \
  --database-version POSTGRES_15 \
  --tier db-n1-standard-2 \
  --region us-central1 \
  --storage-type SSD \
  --storage-size 100GB \
  --storage-auto-increase \
  --backup-start-time 02:00 \
  --enable-bin-log \
  --retained-backups-count 7 \
  --transaction-log-retention-days 7 \
  --network projects/${PROJECT_ID}/global/networks/default

# Create database
gcloud sql databases create yawl --instance yawl-db

# Create user
gcloud sql users create yawl-engine \
  --instance yawl-db \
  --password $(openssl rand -base64 32)

# Store password in Secret Manager
echo -n "$(openssl rand -base64 32)" | \
  gcloud secrets create yawl-db-password --data-file=-
```

#### Step 3: Deploy SPIRE

```bash
# Create SPIRE namespace
kubectl create namespace spire

# Deploy SPIRE server
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spire-server
  namespace: spire
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: spire-server
  namespace: spire
spec:
  serviceName: spire-server
  replicas: 1
  selector:
    matchLabels:
      app: spire-server
  template:
    metadata:
      labels:
        app: spire-server
    spec:
      serviceAccountName: spire-server
      containers:
        - name: spire-server
          image: ghcr.io/spiffe/spire-server:1.8.0
          args:
            - -config
            - /run/spire/config/server.conf
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-data
              mountPath: /run/spire/data
          livenessProbe:
            httpGet:
              path: /live
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
      volumes:
        - name: spire-config
          configMap:
            name: spire-server
        - name: spire-data
          persistentVolumeClaim:
            claimName: spire-data
---
apiVersion: v1
kind: Service
metadata:
  name: spire-server
  namespace: spire
spec:
  type: ClusterIP
  selector:
    app: spire-server
  ports:
    - name: grpc
      port: 8081
      targetPort: 8081
EOF

# Deploy SPIRE agent (DaemonSet)
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: spire-agent
  namespace: spire
spec:
  selector:
    matchLabels:
      app: spire-agent
  template:
    metadata:
      labels:
        app: spire-agent
    spec:
      hostPID: true
      hostNetwork: true
      containers:
        - name: spire-agent
          image: ghcr.io/spiffe/spire-agent:1.8.0
          args:
            - -config
            - /run/spire/config/agent.conf
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-socket
              mountPath: /run/spire/sockets
            - name: spire-bundle
              mountPath: /run/spire/bundle
          securityContext:
            privileged: true
      volumes:
        - name: spire-config
          configMap:
            name: spire-agent
        - name: spire-socket
          hostPath:
            path: /run/spire/sockets
            type: DirectoryOrCreate
        - name: spire-bundle
          configMap:
            name: spire-bundle
EOF

# Register YAWL engine workload
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/engine \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/yawl-prod/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-service-account \
  -selector k8s:container-name:yawl-engine
```

#### Step 4: Deploy External Secrets Operator

```bash
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace

# Create SecretStore
kubectl apply -f - <<EOF
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: gcpsm-secret-store
  namespace: yawl
spec:
  provider:
    gcpsm:
      projectID: "${PROJECT_ID}"
      auth:
        workloadIdentity:
          clusterLocation: us-central1
          clusterName: yawl-prod
          serviceAccountRef:
            name: yawl-service-account
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-db-credentials
  namespace: yawl
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcpsm-secret-store
    kind: SecretStore
  target:
    name: yawl-db-credentials
    creationPolicy: Owner
  data:
    - secretKey: DATABASE_PASSWORD
      remoteRef:
        key: yawl-db-password
EOF
```

#### Step 5: Deploy YAWL

```bash
# Create namespace
kubectl apply -f k8s/base/namespace.yaml

# Create service account with Workload Identity
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-service-account
  namespace: yawl
  annotations:
    iam.gke.io/gcp-service-account: yawl-engine@${PROJECT_ID}.iam.gserviceaccount.com
EOF

# Bind GCP service account
gcloud iam service-accounts add-iam-policy-binding \
  yawl-engine@${PROJECT_ID}.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:${PROJECT_ID}.svc.id.goog[yawl/yawl-service-account]"

# Deploy YAWL components
kubectl apply -k k8s/base/

# Wait for rollout
kubectl rollout status deployment/yawl-engine -n yawl
```

#### Step 6: Deploy Ingress

```bash
# Install nginx-ingress controller
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install nginx-ingress ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.service.type=LoadBalancer

# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Create Let's Encrypt issuer
kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
EOF

# Deploy ingress
kubectl apply -f k8s/base/ingress.yaml
```

---

### 1.3 Verification

```bash
# Check all pods are running
kubectl get pods -n yawl

# Check SPIRE health
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server healthcheck

# Get ingress IP
kubectl get ingress yawl-ingress -n yawl

# Test health endpoint
INGRESS_IP=$(kubectl get ingress yawl-ingress -n yawl -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
curl -H "Host: yawl.example.com" http://${INGRESS_IP}/engine/health
```

---

## 2. EKS/AWS Deployment Runbook

### 2.1 Prerequisites

```bash
# Install AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Install eksctl
curl --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# Configure AWS credentials
aws configure
```

---

### 2.2 Step-by-Step Deployment

#### Step 1: Create EKS Cluster

```bash
# Create cluster config
cat > yawl-cluster.yaml <<EOF
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: yawl-prod
  region: us-east-1
  version: "1.28"

iam:
  withOIDC: true

managedNodeGroups:
  - name: yawl-workers
    instanceType: m5.xlarge
    desiredCapacity: 3
    minSize: 3
    maxSize: 10
    volumeSize: 100
    volumeType: gp3
    privateNetworking: true
    iam:
      withAddonPolicies:
        autoScaler: true
        cloudWatch: true
        ebs: true

addons:
  - name: vpc-cni
  - name: coredns
  - name: kube-proxy
  - name: aws-ebs-csi-driver
    serviceAccountRoleARN: arn:aws:iam::ACCOUNT_ID:role/AmazonEKS_EBS_CSI_DriverRole
EOF

# Create cluster
eksctl create cluster -f yawl-cluster.yaml

# Update kubeconfig
aws eks update-kubeconfig --region us-east-1 --name yawl-prod
```

#### Step 2: Create RDS Database

```bash
# Create DB subnet group
aws rds create-db-subnet-group \
  --db-subnet-group-name yawl-db-subnet \
  --db-subnet-group-description "YAWL Database Subnet Group" \
  --subnet-ids subnet-abc123 subnet-def456

# Create security group
VPC_ID=$(aws eks describe-cluster --name yawl-prod --query "cluster.resourcesVpcConfig.vpcId" --output text)
aws ec2 create-security-group \
  --group-name yawl-db-sg \
  --description "YAWL Database Security Group" \
  --vpc-id $VPC_ID

# Allow PostgreSQL from EKS nodes
NODE_SG=$(aws eks describe-cluster --name yawl-prod --query "cluster.resourcesVpcConfig.clusterSecurityGroupId" --output text)
aws ec2 authorize-security-group-ingress \
  --group-id sg-yawl-db \
  --protocol tcp \
  --port 5432 \
  --source-group $NODE_SG

# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier yawl-prod-db \
  --db-instance-class db.r5.large \
  --engine postgres \
  --engine-version 15.4 \
  --master-username postgres \
  --master-user-password $(openssl rand -base64 32) \
  --allocated-storage 100 \
  --storage-type gp3 \
  --storage-encrypted \
  --vpc-security-group-ids sg-yawl-db \
  --db-subnet-group-name yawl-db-subnet \
  --backup-retention-period 7 \
  --preferred-backup-window "03:00-04:00" \
  --preferred-maintenance-window "sun:04:00-sun:05:00"

# Store password in Secrets Manager
aws secretsmanager create-secret \
  --name yawl-db-password \
  --secret-string $(openssl rand -base64 32)
```

#### Step 3: Deploy SPIRE (AWS-specific)

```bash
# Deploy SPIRE with AWS IID attestation
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: spire-server
  namespace: spire
data:
  server.conf: |
    server {
      bind_address = "0.0.0.0"
      bind_port = "8081"
      trust_domain = "yawl.cloud"
      data_dir = "/run/spire/data"
      log_level = "INFO"
    }
    
    plugins {
      DataStore "sql" {
        plugin_data {
          database_type = "postgres"
          connection_string = "postgresql://spire:password@postgres:5432/spire"
        }
      }
      
      NodeAttestor "aws_iid" {
        plugin_data {
          account_ids = ["ACCOUNT_ID"]
        }
      }
      
      KeyManager "memory" {
        plugin_data {}
      }
    }
EOF

# Register workloads (same as GKE)
```

#### Step 4: Deploy External Secrets Operator (AWS)

```bash
# Install External Secrets Operator
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace

# Create IAM role for YAWL
cat > trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/oidc.eks.us-east-1.amazonaws.com/id/OIDC_ID"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "oidc.eks.us-east-1.amazonaws.com/id/OIDC_ID:sub": "system:serviceaccount:yawl:yawl-service-account"
        }
      }
    }
  ]
}
EOF

aws iam create-role \
  --role-name yawl-engine-role \
  --assume-role-policy-document file://trust-policy.json

aws iam attach-role-policy \
  --role-name yawl-engine-role \
  --policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite

# Create SecretStore
kubectl apply -f - <<EOF
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secretsmanager
  namespace: yawl
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: yawl-service-account
EOF
```

#### Step 5: Deploy YAWL

```bash
# Create namespace
kubectl create namespace yawl

# Create service account with IRSA
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-service-account
  namespace: yawl
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/yawl-engine-role
EOF

# Deploy YAWL
kubectl apply -k k8s/base/
```

---

## 3. AKS/Azure Deployment Runbook

### 3.1 Prerequisites

```bash
# Install Azure CLI
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# Login
az login

# Install kubectl
az aks install-cli
```

---

### 3.2 Step-by-Step Deployment

#### Step 1: Create AKS Cluster

```bash
# Create resource group
az group create --name yawl-prod --location eastus

# Create AKS cluster
az aks create \
  --resource-group yawl-prod \
  --name yawl-prod \
  --node-count 3 \
  --node-vm-size Standard_D4s_v3 \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 10 \
  --network-plugin azure \
  --enable-managed-identity \
  --enable-workload-identity \
  --enable-oidc-issuer \
  --enable-addons monitoring \
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group yawl-prod --name yawl-prod
```

#### Step 2: Create Azure Database for PostgreSQL

```bash
# Create PostgreSQL server
az postgres flexible-server create \
  --resource-group yawl-prod \
  --name yawl-db \
  --location eastus \
  --admin-user postgres \
  --admin-password $(openssl rand -base64 32) \
  --sku-name Standard_D4s_v3 \
  --storage-size 128 \
  --version 15 \
  --backup-retention 7 \
  --high-availability Enabled

# Create database
az postgres flexible-server db create \
  --resource-group yawl-prod \
  --server-name yawl-db \
  --database-name yawl

# Store password in Key Vault
az keyvault create \
  --name yawl-keyvault \
  --resource-group yawl-prod \
  --location eastus

az keyvault secret set \
  --vault-name yawl-keyvault \
  --name yawl-db-password \
  --value $(openssl rand -base64 32)
```

#### Step 3: Deploy YAWL with Workload Identity

```bash
# Create user-assigned managed identity
az identity create \
  --resource-group yawl-prod \
  --name yawl-identity

# Get identity details
IDENTITY_CLIENT_ID=$(az identity show --resource-group yawl-prod --name yawl-identity --query clientId -o tsv)
IDENTITY_OBJECT_ID=$(az identity show --resource-group yawl-prod --name yawl-identity --query principalId -o tsv)

# Grant Key Vault access
az keyvault set-policy \
  --name yawl-keyvault \
  --object-id $IDENTITY_OBJECT_ID \
  --secret-permissions get list

# Create service account
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-service-account
  namespace: yawl
  annotations:
    azure.workload.identity/client-id: ${IDENTITY_CLIENT_ID}
EOF

# Deploy YAWL
kubectl apply -k k8s/base/
```

---

## 4. Observability Setup

### 4.1 Prometheus + Grafana

```bash
# Install kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false

# Create ServiceMonitor for YAWL
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
  endpoints:
    - port: http
      path: /engine/metrics
      interval: 30s
EOF

# Access Grafana
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
# Username: admin
# Password: prom-operator
```

---

### 4.2 OpenTelemetry Collector

```bash
# Install OpenTelemetry Operator
kubectl apply -f https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml

# Deploy collector
kubectl apply -f - <<EOF
apiVersion: opentelemetry.io/v1alpha1
kind: OpenTelemetryCollector
metadata:
  name: yawl-otel
  namespace: yawl
spec:
  mode: deployment
  config: |
    receivers:
      otlp:
        protocols:
          grpc:
          http:
    
    processors:
      batch:
    
    exporters:
      jaeger:
        endpoint: jaeger-collector:14250
        tls:
          insecure: true
      prometheus:
        endpoint: "0.0.0.0:8889"
    
    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [batch]
          exporters: [jaeger]
        metrics:
          receivers: [otlp]
          processors: [batch]
          exporters: [prometheus]
EOF
```

---

## 5. Troubleshooting Guide

### 5.1 Pod Crashes

**Symptom**: Pod in CrashLoopBackOff

**Diagnosis**:
```bash
# Check pod status
kubectl get pods -n yawl

# View logs
kubectl logs -n yawl deployment/yawl-engine --tail=100

# Check events
kubectl describe pod -n yawl yawl-engine-xxx

# Common issues:
# 1. Database connection failure
# 2. Out of memory (OOMKilled)
# 3. Missing secrets
```

**Resolution**:
```bash
# Fix database connectivity
kubectl exec -it -n yawl yawl-engine-xxx -- nc -zv postgres 5432

# Increase memory limits
kubectl patch deployment yawl-engine -n yawl -p '{"spec":{"template":{"spec":{"containers":[{"name":"yawl-engine","resources":{"limits":{"memory":"4Gi"}}}]}}}}'

# Check secrets
kubectl get secret yawl-db-credentials -n yawl -o yaml
```

---

### 5.2 High Latency

**Symptom**: p95 latency > 500ms

**Diagnosis**:
```bash
# Check metrics
kubectl exec -n yawl deployment/yawl-engine -- curl localhost:8080/engine/metrics | grep duration

# Check database connection pool
kubectl logs -n yawl deployment/yawl-engine | grep HikariPool

# Check resource utilization
kubectl top pods -n yawl
```

**Resolution**:
```bash
# Scale up replicas
kubectl scale deployment yawl-engine -n yawl --replicas=5

# Tune connection pool
# Edit configmap: maximumPoolSize=50

# Add database read replicas
```

---

### 5.3 SPIRE Issues

**Symptom**: SVID validation fails

**Diagnosis**:
```bash
# Check SPIRE agent logs
kubectl logs -n spire daemonset/spire-agent

# Check SPIRE server
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry show

# Check workload registration
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry show -spiffeID spiffe://yawl.cloud/engine
```

**Resolution**:
```bash
# Re-register workload
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/engine \
  -parentID spiffe://yawl.cloud/spire/agent/... \
  -selector k8s:ns:yawl

# Restart pod to get new SVID
kubectl rollout restart deployment/yawl-engine -n yawl
```

---

## 6. Incident Response Procedures

### 6.1 Service Outage

**Severity**: P1 (Critical)

**Detection**:
- Health check failures
- Prometheus alert: `YawlEngineDown`
- User reports

**Immediate Actions** (< 5 minutes):
```bash
# 1. Check pod status
kubectl get pods -n yawl -o wide

# 2. Check recent events
kubectl get events -n yawl --sort-by='.lastTimestamp' | tail -20

# 3. Check logs
kubectl logs -n yawl deployment/yawl-engine --tail=200 | grep ERROR

# 4. Notify team
# Slack: #yawl-incidents
# PagerDuty: Trigger incident
```

**Mitigation** (< 15 minutes):
```bash
# If database issue: failover to replica
# If pod issue: rollback to last known good version
kubectl rollout undo deployment/yawl-engine -n yawl

# Scale up replicas
kubectl scale deployment yawl-engine -n yawl --replicas=10
```

**Root Cause Analysis** (within 24 hours):
- Collect logs and metrics
- Analyze deployment changes
- Document timeline and resolution
- Create postmortem report

---

### 6.2 Data Loss

**Severity**: P0 (Critical)

**Detection**:
- Database corruption alerts
- Backup verification failures

**Immediate Actions**:
```bash
# 1. Stop writes
kubectl scale deployment yawl-engine -n yawl --replicas=0

# 2. Snapshot database
# GCP:
gcloud sql backups create --instance yawl-db
# AWS:
aws rds create-db-snapshot --db-instance-identifier yawl-prod-db --db-snapshot-identifier emergency-$(date +%s)

# 3. Assess extent of data loss
# Check database logs
```

**Recovery**:
```bash
# Restore from last good backup
# GCP:
gcloud sql backups restore BACKUP_ID --backup-instance yawl-db
# AWS:
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier yawl-prod-db-restored \
  --db-snapshot-identifier SNAPSHOT_ID

# Update connection strings
# Resume operations
```

---

## 7. Disaster Recovery

### 7.1 Multi-Region Failover

**RTO**: 15 minutes  
**RPO**: 5 minutes

**Failover Procedure**:
```bash
# 1. Update DNS to point to DR region
# 2. Promote read replica to primary
# GCP:
gcloud sql instances promote-replica yawl-db-dr --region us-west1

# 3. Deploy YAWL in DR region
kubectl apply -k k8s/overlays/dr/ --context dr-cluster

# 4. Verify health
curl https://yawl-dr.example.com/engine/health
```

**Failback**:
```bash
# 1. Sync data from DR to primary
# 2. Update DNS back to primary region
# 3. Scale down DR deployment
```

---

### 7.2 Backup Verification

**Schedule**: Weekly

```bash
# Automated backup test
#!/bin/bash
BACKUP_ID=$(gcloud sql backups list --instance yawl-db --limit 1 --format "value(id)")

# Restore to test instance
gcloud sql instances clone yawl-db yawl-test --backup-id $BACKUP_ID

# Run validation queries
kubectl run -it --rm test-backup --image=postgres:15 -- \
  psql -h yawl-test -U postgres -c "SELECT COUNT(*) FROM yawl_cases;"

# Cleanup
gcloud sql instances delete yawl-test --quiet
```

---

## Appendix: Quick Reference

### Useful Commands

```bash
# Check all YAWL components
kubectl get all -n yawl

# View resource usage
kubectl top pods -n yawl

# Exec into pod
kubectl exec -it -n yawl deployment/yawl-engine -- /bin/bash

# Port-forward for debugging
kubectl port-forward -n yawl svc/yawl-engine 8080:8080

# View HPA status
kubectl get hpa -n yawl

# Drain node for maintenance
kubectl drain NODE_NAME --ignore-daemonsets --delete-emptydir-data
```

### Health Check Endpoints

```
/engine/health        - Overall health
/engine/health/ready  - Readiness probe
/engine/health/live   - Liveness probe
/engine/metrics       - Prometheus metrics
```

### Emergency Contacts

- **On-Call Engineer**: PagerDuty
- **Database Admin**: db-team@example.com
- **Security Team**: security@example.com
- **Manager**: manager@example.com

---

**Document Owner**: DevOps Team  
**Last Updated**: 2026-02-15  
**Review Cycle**: Quarterly
