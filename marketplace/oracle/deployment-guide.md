# YAWL Workflow Engine - OCI Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on Oracle Cloud Infrastructure (OCI) using Oracle Kubernetes Engine (OKE), Autonomous Database (ADB), and Object Storage.

---

## Prerequisites

### Required Access

- OCI Tenancy with Compartment Administrator access
- OCI CLI installed and configured (`oci setup config`)
- kubectl installed and configured
- Helm 3.x installed
- Docker installed (for local testing)

### Required OCI Services

- Oracle Kubernetes Engine (OKE)
- Autonomous Database (ADB) - Oracle or PostgreSQL
- Object Storage
- OCI Registry (OCIR)
- Vault (optional, for secrets management)

### Resource Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| OKE Nodes | 3 nodes, 2 OCPU each | 3 nodes, 4 OCPU each |
| Autonomous DB | 1 OCPU | 2 OCPU |
| Object Storage | 10 GB | 100 GB |
| VCN | 1 VCN with 3 subnets | - |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         OCI Region                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                        VCN                                 │  │
│  │  ┌─────────────┐  ┌─────────────────────────────────────┐ │  │
│  │  │   Public    │  │           Private Subnet            │ │  │
│  │  │   Subnet    │  │  ┌───────────────────────────────┐  │ │  │
│  │  │             │  │  │      OKE Cluster              │  │ │  │
│  │  │ ┌─────────┐ │  │  │  ┌───────┐ ┌───────┐ ┌──────┐│  │ │  │
│  │  │ │   LB    │ │  │  │  │ Engine│ │Resource│ │Worklet││  │ │  │
│  │  │ │(nginx)  │ │  │  │  │ Pod   │ │Service │ │Service││  │ │  │
│  │  │ └────┬────┘ │  │  │  └───┬───┘ └───┬───┘ └──┬───┘│  │ │  │
│  │  │      │      │  │  │      │         │        │    │  │ │  │
│  │  └──────┼──────┘  │  └──────┼─────────┼────────┼────┘  │ │  │
│  │         │         │         │         │        │       │ │  │
│  │         │         └─────────┼─────────┼────────┼───────┘ │  │
│  │         │                   │         │        │         │  │
│  │         │         ┌─────────┴─────────┴────────┴───────┐ │  │
│  │         │         │        Private Subnet              │ │  │
│  │         │         │  ┌─────────────────────────────┐   │ │  │
│  │         │         │  │   Autonomous Database       │   │ │  │
│  │         │         │  │   (Wallet Authentication)   │   │ │  │
│  │         │         │  └─────────────────────────────┘   │ │  │
│  │         │         └─────────────────────────────────────┘ │  │
│  └─────────┼─────────────────────────────────────────────────┘  │
│            │                                                    │
│  ┌─────────┴─────────────────────────────────────────────────┐  │
│  │                    Object Storage                          │  │
│  │                    (Documents/Attachments)                 │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Create OCI Resources

### 1.1 Set Environment Variables

```bash
# Set your OCI tenancy details
export TENANCY_OCID="ocid1.tenancy.oc1..your-tenancy"
export COMPARTMENT_OCID="ocid1.compartment.oc1..your-compartment"
export REGION="us-phoenix-1"
export YAWL_VERSION="5.2.0"

# Generate unique suffix for resource names
export UNIQUE_SUFFIX=$(date +%Y%m%d%H%M)
export PREFIX="yawl-${UNIQUE_SUFFIX}"
```

### 1.2 Create VCN and Subnets

```bash
# Create VCN
VCN_OCID=$(oci network vcn create \
  --compartment-id $COMPARTMENT_OCID \
  --display-name "${PREFIX}-vcn" \
  --cidr-block "10.0.0.0/16" \
  --dns-label "yawlvcn" \
  --query 'data.id' \
  --raw-output)

# Create Public Subnet (for Load Balancer)
PUBLIC_SUBNET_OCID=$(oci network subnet create \
  --compartment-id $COMPARTMENT_OCID \
  --vcn-id $VCN_OCID \
  --display-name "${PREFIX}-public-subnet" \
  --cidr-block "10.0.1.0/24" \
  --dns-label "public" \
  --is-public true \
  --query 'data.id' \
  --raw-output)

# Create Private Subnet (for OKE Nodes)
PRIVATE_SUBNET_OCID=$(oci network subnet create \
  --compartment-id $COMPARTMENT_OCID \
  --vcn-id $VCN_OCID \
  --display-name "${PREFIX}-private-subnet" \
  --cidr-block "10.0.2.0/24" \
  --dns-label "private" \
  --is-public false \
  --query 'data.id' \
  --raw-output)

# Create Database Subnet
DB_SUBNET_OCID=$(oci network subnet create \
  --compartment-id $COMPARTMENT_OCID \
  --vcn-id $VCN_OCID \
  --display-name "${PREFIX}-db-subnet" \
  --cidr-block "10.0.3.0/24" \
  --dns-label "database" \
  --is-public false \
  --query 'data.id' \
  --raw-output)

echo "VCN: $VCN_OCID"
echo "Public Subnet: $PUBLIC_SUBNET_OCID"
echo "Private Subnet: $PRIVATE_SUBNET_OCID"
echo "Database Subnet: $DB_SUBNET_OCID"
```

### 1.3 Create Autonomous Database

```bash
# Create Autonomous Database (Oracle)
ADB_OCID=$(oci db autonomous-database create \
  --compartment-id $COMPARTMENT_OCID \
  --display-name "${PREFIX}-adb" \
  --db-name "YAWL${UNIQUE_SUFFIX}" \
  --admin-password "YourSecurePassword123!" \
  --cpu-core-count 2 \
  --data-storage-size-in-tbs 1 \
  --db-workload OLTP \
  --is-auto-scaling-enabled true \
  --subnet-id $DB_SUBNET_OCID \
  --query 'data.id' \
  --raw-output)

# Wait for ADB to be available
oci db autonomous-database get \
  --autonomous-database-id $ADB_OCID \
  --wait-for-state AVAILABLE

# Download Wallet
mkdir -p ~/yawl-wallet
oci db autonomous-database generate-wallet \
  --autonomous-database-id $ADB_OCID \
  --password "WalletPassword123!" \
  --file ~/yawl-wallet/wallet.zip

unzip ~/yawl-wallet/wallet.zip -d ~/yawl-wallet
```

### 1.4 Create Object Storage Bucket

```bash
# Create bucket for YAWL documents
oci os bucket create \
  --compartment-id $COMPARTMENT_OCID \
  --name "${PREFIX}-documents" \
  --public-access-type NoPublicAccess

export BUCKET_NAME="${PREFIX}-documents"
```

### 1.5 Create OKE Cluster

```bash
# Create OKE cluster
CLUSTER_OCID=$(oci ce cluster create \
  --compartment-id $COMPARTMENT_OCID \
  --name "${PREFIX}-cluster" \
  --kubernetes-version "1.28" \
  --vcn-id $VCN_OCID \
  --service-lb-subnet-ids "[\"$PUBLIC_SUBNET_OCID\"]" \
  --endpoint-subnet-id $PRIVATE_SUBNET_OCID \
  --is-private false \
  --query 'data.id' \
  --raw-output)

# Wait for cluster creation (takes 10-15 minutes)
oci ce cluster get \
  --cluster-id $CLUSTER_OCID \
  --wait-for-state ACTIVE

# Create Node Pool
NODE_POOL_OCID=$(oci ce node-pool create \
  --compartment-id $COMPARTMENT_OCID \
  --cluster-id $CLUSTER_OCID \
  --name "${PREFIX}-node-pool" \
  --kubernetes-version "1.28" \
  --node-shape "VM.Standard.E4.Flex" \
  --node-shape-config '{"ocpus": 4, "memoryInGBs": 16}' \
  --subnet-id $PRIVATE_SUBNET_OCID \
  --size 3 \
  --query 'data.id' \
  --raw-output)

# Get kubeconfig
oci ce cluster create-kubeconfig \
  --cluster-id $CLUSTER_OCID \
  --file ~/.kube/config \
  --region $REGION

# Verify cluster access
kubectl get nodes
```

---

## Step 2: Configure OCI Registry (OCIR)

### 2.1 Create Repository

```bash
# Login to OCIR
docker login ${REGION}.ocir.io \
  -u "${TENANCY_OCID}/your-username" \
  -p $(oci auth token generate --description "OCIR Token" --query 'data.token' --raw-output)

# Create repository
oci artifacts container repository create \
  --compartment-id $COMPARTMENT_OCID \
  --display-name "yawl-engine" \
  --is-public false
```

### 2.2 Push YAWL Images

```bash
# Pull and tag YAWL images
docker pull yawlfoundation/yawl-engine:${YAWL_VERSION}
docker pull yawlfoundation/yawl-resource-service:${YAWL_VERSION}
docker pull yawlfoundation/yawl-worklet-service:${YAWL_VERSION}

# Tag for OCIR
docker tag yawlfoundation/yawl-engine:${YAWL_VERSION} \
  ${REGION}.ocir.io/${TENANCY_OCID}/yawl-engine:${YAWL_VERSION}
docker tag yawlfoundation/yawl-resource-service:${YAWL_VERSION} \
  ${REGION}.ocir.io/${TENANCY_OCID}/yawl-resource-service:${YAWL_VERSION}
docker tag yawlfoundation/yawl-worklet-service:${YAWL_VERSION} \
  ${REGION}.ocir.io/${TENANCY_OCID}/yawl-worklet-service:${YAWL_VERSION}

# Push to OCIR
docker push ${REGION}.ocir.io/${TENANCY_OCID}/yawl-engine:${YAWL_VERSION}
docker push ${REGION}.ocir.io/${TENANCY_OCID}/yawl-resource-service:${YAWL_VERSION}
docker push ${REGION}.ocir.io/${TENANCY_OCID}/yawl-worklet-service:${YAWL_VERSION}
```

---

## Step 3: Deploy YAWL Using Helm

### 3.1 Create Kubernetes Secrets

```bash
# Create namespace
kubectl create namespace yawl

# Create ADB wallet secret
kubectl create secret generic adb-wallet \
  --namespace yawl \
  --from-file=wallet=/path/to/yawl-wallet

# Create database credentials secret
kubectl create secret generic yawl-db-credentials \
  --namespace yawl \
  --from-literal=username=admin \
  --from-literal=password='YourSecurePassword123!'

# Create OCI config secret for Object Storage access
kubectl create secret generic oci-config \
  --namespace yawl \
  --from-literal=tenancy=${TENANCY_OCID} \
  --from-literal=user=${USER_OCID} \
  --from-literal=fingerprint=${FINGERPRINT} \
  --from-file=private_key=/path/to/api_key.pem \
  --from-literal=region=${REGION} \
  --from-literal=compartment=${COMPARTMENT_OCID} \
  --from-literal=bucket=${BUCKET_NAME}
```

### 3.2 Create values.yaml

```bash
cat > values-oci.yaml <<EOF
# YAWL OCI-specific values
replicaCount: 3

image:
  registry: ${REGION}.ocir.io
  repository: ${TENANCY_OCID}
  tag: ${YAWL_VERSION}
  pullPolicy: Always
  pullSecrets:
    - name: ocir-secret

services:
  engine:
    name: yawl-engine
    repository: yawl-engine
    port: 8080
    resources:
      requests:
        cpu: 1000m
        memory: 2Gi
      limits:
        cpu: 2000m
        memory: 4Gi

  resourceService:
    name: yawl-resource-service
    repository: yawl-resource-service
    port: 8080
    resources:
      requests:
        cpu: 500m
        memory: 1Gi
      limits:
        cpu: 1000m
        memory: 2Gi

  workletService:
    name: yawl-worklet-service
    repository: yawl-worklet-service
    port: 8080
    resources:
      requests:
        cpu: 500m
        memory: 1Gi
      limits:
        cpu: 1000m
        memory: 2Gi

database:
  type: oracle
  autonomous:
    enabled: true
    walletSecret: adb-wallet
    credentialsSecret: yawl-db-credentials
    tnsAlias: yawl_high
    poolSize: 20

objectStorage:
  enabled: true
  provider: oci
  configSecret: oci-config

ingress:
  enabled: true
  className: nginx
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "100m"
  hosts:
    - host: yawl.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: yawl-tls
      hosts:
        - yawl.example.com

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

monitoring:
  enabled: true
  serviceMonitor:
    enabled: true
    namespace: monitoring
    interval: 30s

logging:
  enabled: true
  format: json
  level: INFO
EOF
```

### 3.3 Install YAWL Helm Chart

```bash
# Add YAWL Helm repository (or use local chart)
helm repo add yawl https://yawlfoundation.github.io/charts
helm repo update

# Install YAWL
helm install yawl yawl/yawl-engine \
  --namespace yawl \
  --values values-oci.yaml \
  --version ${YAWL_VERSION}

# Wait for deployment
kubectl rollout status deployment/yawl-engine -n yawl --timeout=10m
kubectl rollout status deployment/yawl-resource-service -n yawl --timeout=10m
kubectl rollout status deployment/yawl-worklet-service -n yawl --timeout=10m
```

### 3.4 Verify Deployment

```bash
# Check pods
kubectl get pods -n yawl

# Check services
kubectl get services -n yawl

# Check ingress
kubectl get ingress -n yawl

# View logs
kubectl logs -f deployment/yawl-engine -n yawl
```

---

## Step 4: Post-Deployment Configuration

### 4.1 Initialize Database Schema

```bash
# Connect to engine pod and run schema initialization
kubectl exec -it deployment/yawl-engine -n yawl -- \
  java -jar /app/yawl-engine.jar --init-db

# Verify tables created
kubectl exec -it deployment/yawl-engine -n yawl -- \
  java -jar /app/yawl-engine.jar --verify-db
```

### 4.2 Configure Administrator Account

```bash
# Create admin user
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -X POST http://localhost:8080/yawl/admin/user \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "SecureAdminPassword123!",
    "email": "admin@example.com",
    "role": "administrator"
  }'
```

### 4.3 Upload Sample Specification

```bash
# Upload a sample workflow specification
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -X POST http://localhost:8080/yawl/specification \
  -H "Content-Type: application/xml" \
  --data-binary @/app/samples/basic-order-process.yawl
```

---

## Step 5: Configure Load Balancer

### 5.1 OCI Load Balancer (Alternative to Ingress)

```bash
# Create OCI Load Balancer service
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: yawl-lb
  namespace: yawl
  annotations:
    oci.oraclecloud.com/load-balancer-type: lb
    service.beta.kubernetes.io/oci-load-balancer-internal: "false"
    service.beta.kubernetes.io/oci-load-balancer-shape: "flexible"
    service.beta.kubernetes.io/oci-load-balancer-shape-flex-min: "10"
    service.beta.kubernetes.io/oci-load-balancer-shape-flex-max: "100"
spec:
  type: LoadBalancer
  selector:
    app: yawl-engine
  ports:
    - name: https
      port: 443
      targetPort: 8443
    - name: http
      port: 80
      targetPort: 8080
EOF

# Get Load Balancer IP
kubectl get svc yawl-lb -n yawl
```

### 5.2 Configure SSL Certificate

```bash
# Create TLS secret with your certificate
kubectl create secret tls yawl-tls \
  --namespace yawl \
  --cert=/path/to/cert.pem \
  --key=/path/to/key.pem

# Update ingress to use TLS
kubectl patch ingress yawl-ingress -n yawl \
  --type=json \
  -p='[{"op": "add", "path": "/spec/tls", "value": [{"hosts": ["yawl.example.com"], "secretName": "yawl-tls"}]}]'
```

---

## Step 6: Configure Monitoring (Optional)

### 6.1 Deploy Prometheus Operator

```bash
# Add Prometheus community Helm repo
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install kube-prometheus-stack
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace
```

### 6.2 Enable YAWL Metrics

```yaml
# Update values-oci.yaml
monitoring:
  enabled: true
  serviceMonitor:
    enabled: true
    namespace: monitoring
    labels:
      release: prometheus
    interval: 30s
    path: /actuator/prometheus
```

```bash
# Upgrade Helm release
helm upgrade yawl yawl/yawl-engine \
  --namespace yawl \
  --values values-oci.yaml
```

---

## Step 7: High Availability Configuration

### 7.1 Multi-AZ Deployment

```bash
# Create node pools in different ADs
for AD in 1 2 3; do
  oci ce node-pool create \
    --compartment-id $COMPARTMENT_OCID \
    --cluster-id $CLUSTER_OCID \
    --name "${PREFIX}-node-pool-ad${AD}" \
    --kubernetes-version "1.28" \
    --node-shape "VM.Standard.E4.Flex" \
    --node-shape-config '{"ocpus": 4, "memoryInGBs": 16}' \
    --subnet-id $PRIVATE_SUBNET_OCID \
    --placement-configs "[{\"availabilityDomain\":\"AD${AD}\",\"subnetId\":\"$PRIVATE_SUBNET_OCID\"}]" \
    --size 1
done
```

### 7.2 Pod Anti-Affinity

```yaml
# Update deployment with anti-affinity
spec:
  template:
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: yawl-engine
                topologyKey: topology.kubernetes.io/zone
```

---

## Troubleshooting

### Common Issues

#### Database Connection Failures

```bash
# Check wallet secret
kubectl describe secret adb-wallet -n yawl

# Test connectivity
kubectl run test-oracle --rm -it --image=oraclelinux:8 -- \
  curl -v telnet://your-adb-host:1521
```

#### Image Pull Errors

```bash
# Verify OCIR secret
kubectl describe secret ocir-secret -n yawl

# Test image pull
kubectl run test-pull --rm -it --image=${REGION}.ocir.io/${TENANCY_OCID}/yawl-engine:${YAWL_VERSION}
```

#### Pod Startup Issues

```bash
# Check events
kubectl describe pod -n yawl -l app=yawl-engine

# Check logs
kubectl logs -n yawl -l app=yawl-engine --all-containers
```

### Health Checks

```bash
# Engine health
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl http://localhost:8080/yawl/health

# Database health
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl http://localhost:8080/yawl/health/db

# Full diagnostics
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl http://localhost:8080/yawl/diagnostics
```

---

## Cleanup

```bash
# Delete Helm release
helm uninstall yawl -n yawl

# Delete namespace
kubectl delete namespace yawl

# Delete OCI resources
oci ce cluster delete --cluster-id $CLUSTER_OCID --force
oci db autonomous-database delete --autonomous-database-id $ADB_OCID --force
oci os bucket delete --bucket-name $BUCKET_NAME --force
oci network vcn delete --vcn-id $VCN_OCID --force
```

---

## Support

For issues or questions:

- **Documentation**: https://yawlfoundation.github.io/yawl/
- **Community**: https://github.com/yawlfoundation/yawl/discussions
- **Enterprise Support**: support@yawlfoundation.org
