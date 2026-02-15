# YAWL Workflow Engine - GCP Deployment Guide

This guide covers deploying YAWL Workflow Engine on Google Cloud Platform using Google Kubernetes Engine (GKE) and Cloud SQL (PostgreSQL).

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Quick Start](#quick-start)
4. [GKE Cluster Setup](#gke-cluster-setup)
5. [Cloud SQL Setup](#cloud-sql-setup)
6. [YAWL Deployment](#yawl-deployment)
7. [Configuration](#configuration)
8. [Networking](#networking)
9. [Monitoring](#monitoring)
10. [Backup and Recovery](#backup-and-recovery)
11. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Tools

```bash
# Google Cloud CLI
gcloud --version

# kubectl
kubectl version --client

# Helm 3
helm version

# Terraform (optional, for IaC deployment)
terraform version
```

### GCP Project Setup

```bash
# Set your project ID
export PROJECT_ID="your-project-id"
gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable \\
    compute.googleapis.com \\
    container.googleapis.com \\
    sqladmin.googleapis.com \\
    servicenetworking.googleapis.com \\
    secretmanager.googleapis.com \\
    storage.googleapis.com \\
    artifactregistry.googleapis.com \\
    monitoring.googleapis.com \\
    logging.googleapis.com
```

### Quota Requirements

Ensure your project has sufficient quota:

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPUs | 16 | 32 |
| In-use IPs | 5 | 10 |
| SSD Storage | 500 GB | 1 TB |
| Cloud SQL Instances | 1 | 2 |

## Architecture Overview

```
                    +-------------------+
                    |   Cloud Armor     |
                    |   (WAF/DDoS)      |
                    +--------+----------+
                             |
                    +--------v----------+
                    |   HTTP(S) LB      |
                    |   (Global)        |
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v----+         +----v----+         +----v----+
    |  GKE    |         |  GKE    |         |  GKE    |
    |  Node 1 |         |  Node 2 |         |  Node 3 |
    +----+----+         +----+----+         +----+----+
         |                   |                   |
         +-------------------+-------------------+
                             |
                    +--------v----------+
                    |   VPC Network     |
                    |  (Private)        |
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v----------+  +-----v------+   +-------v------+
    | YAWL Engine   |  | Resource   |   | Worklet      |
    | (Pod)         |  | Service    |   | Service      |
    +---------------+  +------------+   +--------------+
                             |
                    +--------v----------+
                    |   Cloud SQL       |
                    |   (PostgreSQL)    |
                    +-------------------+

```

## Quick Start

### Option 1: Terraform Deployment

```bash
# Clone the repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl/terraform

# Copy and edit variables
cp gcp.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your settings

# Initialize and apply
terraform init
terraform plan
terraform apply
```

### Option 2: Manual Deployment

Follow the sections below for step-by-step manual deployment.

## GKE Cluster Setup

### Create VPC Network

```bash
# Create custom VPC
gcloud compute networks create yawl-vpc \\
    --project=$PROJECT_ID \\
    --subnet-mode=custom \\
    --bgp-routing-mode=regional

# Create subnets
gcloud compute networks subnets create yawl-gke-subnet \\
    --project=$PROJECT_ID \\
    --network=yawl-vpc \\
    --region=us-central1 \\
    --range=10.0.0.0/16 \\
    --secondary-range=pods=10.4.0.0/14,services=10.0.32.0/20

gcloud compute networks subnets create yawl-db-subnet \\
    --project=$PROJECT_ID \\
    --network=yawl-vpc \\
    --region=us-central1 \\
    --range=10.1.0.0/24
```

### Create GKE Cluster

```bash
# Create private GKE cluster
gcloud container clusters create yawl-cluster \\
    --project=$PROJECT_ID \\
    --region=us-central1 \\
    --network=yawl-vpc \\
    --subnetwork=yawl-gke-subnet \\
    --cluster-secondary-range-name=pods \\
    --services-secondary-range-name=services \\
    --enable-private-nodes \\
    --enable-private-endpoint \\
    --master-ipv4-cidr=172.16.0.0/28 \\
    --enable-master-authorized-networks \\
    --master-authorized-networks=0.0.0.0/0 \\
    --workload-pool=$PROJECT_ID.svc.id.goog \\
    --release-channel=regular \\
    --machine-type=e2-standard-4 \\
    --num-nodes=3 \\
    --enable-autoscaling \\
    --min-nodes=1 \\
    --max-nodes=5 \\
    --disk-size=100 \\
    --disk-type=pd-balanced \\
    --enable-vertical-pod-autoscaling \\
    --enable-network-policy \\
    --enable-dataplane-v2

# Get cluster credentials
gcloud container clusters get-credentials yawl-cluster \\
    --region=us-central1 \\
    --project=$PROJECT_ID
```

### Create Node Pool (Optional)

```bash
# Create dedicated node pool for YAWL workloads
gcloud container node-pools create yawl-pool \\
    --project=$PROJECT_ID \\
    --cluster=yawl-cluster \\
    --region=us-central1 \\
    --machine-type=e2-standard-8 \\
    --num-nodes=3 \\
    --enable-autoscaling \\
    --min-nodes=2 \\
    --max-nodes=10 \\
    --node-labels=app=yawl \\
    --node-taints=dedicated=yawl:NoSchedule
```

## Cloud SQL Setup

### Create Private Connection

```bash
# Allocate IP range for Cloud SQL
gcloud compute addresses create google-managed-services-yawl \\
    --project=$PROJECT_ID \\
    --global \\
    --purpose=VPC_PEERING \\
    --prefix-length=16 \\
    --network=projects/$PROJECT_ID/global/networks/yawl-vpc

# Create private connection
gcloud services vpc-peerings connect \\
    --project=$PROJECT_ID \\
    --service=servicenetworking.googleapis.com \\
    --ranges=google-managed-services-yawl \\
    --network=yawl-vpc
```

### Create Cloud SQL Instance

```bash
# Create PostgreSQL instance
gcloud sql instances create yawl-db \\
    --project=$PROJECT_ID \\
    --database-version=POSTGRES_15 \\
    --tier=db-custom-4-16384 \\
    --region=us-central1 \\
    --storage-type=SSD \\
    --storage-size=100GB \\
    --storage-auto-increase \\
    --storage-auto-increase-limit=500 \\
    --availability-type=REGIONAL \\
    --network=yawl-vpc \\
    --no-assign-ip \\
    --enable-point-in-time-recovery \\
    --backup-start-time=02:00 \\
    --backup-location=us-central1 \\
    --maintenance-window-day=SUN \\
    --maintenance-window-hour=3 \\
    --database-flags=log_checkpoints=on,log_connections=on,log_disconnections=on

# Create database
gcloud sql databases create yawl \\
    --project=$PROJECT_ID \\
    --instance=yawl-db

# Create user
gcloud sql users create yawl_admin \\
    --project=$PROJECT_ID \\
    --instance=yawl-db \\
    --password="$(openssl rand -base64 32)"
```

### Store Credentials in Secret Manager

```bash
# Store database password
echo -n "$(gcloud sql users describe yawl_admin --instance=yawl-db --format='value(password)')" | \\
    gcloud secrets create yawl-db-password \\
    --project=$PROJECT_ID \\
    --data-file=-

# Store connection string
echo -n "jdbc:postgresql:///yawl?cloudSqlInstance=$PROJECT_ID:us-central1:yawl-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory" | \\
    gcloud secrets create yawl-db-connection \\
    --project=$PROJECT_ID \\
    --data-file=-
```

## YAWL Deployment

### Create Namespace and Service Account

```bash
# Create namespace
kubectl create namespace yawl

# Create service account
kubectl create serviceaccount yawl-engine \\
    --namespace=yawl

# Annotate for Workload Identity
gcloud iam service-accounts create yawl-engine \\
    --project=$PROJECT_ID \\
    --display-name="YAWL Engine Service Account"

gcloud iam service-accounts add-iam-policy-binding \\
    yawl-engine@$PROJECT_ID.iam.gserviceaccount.com \\
    --project=$PROJECT_ID \\
    --role=roles/iam.workloadIdentityUser \\
    --member="serviceAccount:$PROJECT_ID.svc.id.goog[yawl/yawl-engine]"

kubectl annotate serviceaccount yawl-engine \\
    --namespace=yawl \\
    iam.gke.io/gcp-service-account=yawl-engine@$PROJECT_ID.iam.gserviceaccount.com
```

### Grant IAM Permissions

```bash
# Grant Cloud SQL access
gcloud projects add-iam-policy-binding $PROJECT_ID \\
    --member="serviceAccount:yawl-engine@$PROJECT_ID.iam.gserviceaccount.com" \\
    --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding $PROJECT_ID \\
    --member="serviceAccount:yawl-engine@$PROJECT_ID.iam.gserviceaccount.com" \\
    --role="roles/cloudsql.instanceUser"

# Grant Secret Manager access
gcloud projects add-iam-policy-binding $PROJECT_ID \\
    --member="serviceAccount:yawl-engine@$PROJECT_ID.iam.gserviceaccount.com" \\
    --role="roles/secretmanager.secretAccessor"

# Grant Storage access
gcloud projects add-iam-policy-binding $PROJECT_ID \\
    --member="serviceAccount:yawl-engine@$PROJECT_ID.iam.gserviceaccount.com" \\
    --role="roles/storage.objectAdmin"
```

### Create Kubernetes Secrets

```bash
# Create database credentials secret
kubectl create secret generic yawl-db-credentials \\
    --namespace=yawl \\
    --from-literal=username=yawl_admin \\
    --from-literal=password="$(gcloud secrets versions access latest --secret=yawl-db-password)"

# Create Cloud SQL connection secret
kubectl create secret generic yawl-cloudsql-connection \\
    --namespace=yawl \\
    --from-literal=connection_name="$PROJECT_ID:us-central1:yawl-db"
```

### Deploy Using Helm

```bash
# Add YAWL Helm repository
helm repo add yawl https://yawlfoundation.github.io/charts
helm repo update

# Deploy YAWL
helm upgrade --install yawl yawl/yawl \\
    --namespace=yawl \\
    --set engine.replicaCount=3 \\
    --set engine.image.repository=gcr.io/$PROJECT_ID/yawl-engine \\
    --set engine.image.tag=5.2.0 \\
    --set database.host="/cloudsql/$PROJECT_ID:us-central1:yawl-db" \\
    --set database.name=yawl \\
    --set database.existingSecret=yawl-db-credentials \\
    --set resources.requests.cpu=1 \\
    --set resources.requests.memory=2Gi \\
    --set resources.limits.cpu=2 \\
    --set resources.limits.memory=4Gi \\
    --set cloudsql.enabled=true \\
    --set cloudsql.instance=$PROJECT_ID:us-central1:yawl-db \\
    --wait
```

### Deploy Cloud SQL Proxy

```yaml
# cloudsql-proxy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cloudsql-proxy
  namespace: yawl
spec:
  replicas: 2
  selector:
    matchLabels:
      app: cloudsql-proxy
  template:
    metadata:
      labels:
        app: cloudsql-proxy
    spec:
      serviceAccountName: yawl-engine
      containers:
        - name: cloudsql-proxy
          image: gcr.io/cloudsql-docker/gce-proxy:1.33.8
          command:
            - "/cloud_sql_proxy"
            - "--instances=$PROJECT_ID:us-central1:yawl-db=tcp:0.0.0.0:5432"
            - "--structured_logs"
          ports:
            - containerPort: 5432
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 256Mi
          livenessProbe:
            exec:
              command: ["nc", "-z", "127.0.0.1", "5432"]
            initialDelaySeconds: 10
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: cloudsql-proxy
  namespace: yawl
spec:
  selector:
    app: cloudsql-proxy
  ports:
    - port: 5432
      targetPort: 5432
```

```bash
# Apply Cloud SQL Proxy
envsubst < cloudsql-proxy.yaml | kubectl apply -f -
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `YAWL_DB_HOST` | Database host | `cloudsql-proxy` |
| `YAWL_DB_PORT` | Database port | `5432` |
| `YAWL_DB_NAME` | Database name | `yawl` |
| `YAWL_DB_USER` | Database username | `yawl_admin` |
| `YAWL_DB_PASSWORD` | Database password | (from secret) |
| `YAWL_ENGINE_PORT` | Engine API port | `8080` |
| `JAVA_OPTS` | JVM options | `-Xmx2g -Xms1g` |

### ConfigMap Example

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  YAWL_DB_HOST: "cloudsql-proxy"
  YAWL_DB_PORT: "5432"
  YAWL_DB_NAME: "yawl"
  YAWL_ENGINE_PORT: "8080"
  JAVA_OPTS: "-Xmx2g -Xms1g -XX:+UseG1GC"
  LOG_LEVEL: "INFO"
```

## Networking

### Internal Load Balancer

```yaml
# ilb.yaml
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine-internal
  namespace: yawl
  annotations:
    cloud.google.com/load-balancer-type: "Internal"
    networking.gke.io/internal-load-balancer-allow-global-access: "true"
spec:
  type: LoadBalancer
  selector:
    app: yawl-engine
  ports:
    - port: 80
      targetPort: 8080
```

### External Load Balancer with SSL

```yaml
# external-lb.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: yawl-ingress
  namespace: yawl
  annotations:
    kubernetes.io/ingress.global-static-ip-name: "yawl-ip"
    networking.gke.io/managed-certificates: "yawl-cert"
    kubernetes.io/ingress.class: "gce"
spec:
  rules:
    - host: yawl.yourdomain.com
      http:
        paths:
          - path: /*
            pathType: ImplementationSpecific
            backend:
              service:
                name: yawl-engine
                port:
                  number: 80
---
apiVersion: networking.gke.io/v1
kind: ManagedCertificate
metadata:
  name: yawl-cert
  namespace: yawl
spec:
  domains:
    - yawl.yourdomain.com
```

### Reserve Static IP

```bash
# Reserve global static IP
gcloud compute addresses create yawl-ip \\
    --project=$PROJECT_ID \\
    --global
```

## Monitoring

### Enable Managed Prometheus

```bash
# Already enabled in cluster creation
# Verify metrics collection
kubectl get pods -n gmp-system
```

### Create Custom Dashboard

```bash
# Create monitoring dashboard
gcloud monitoring dashboards create \\
    --project=$PROJECT_ID \\
    --config-from-file=dashboard.json
```

### Alert Policies

```bash
# Create alert for engine health
gcloud alpha monitoring policies create \\
    --project=$PROJECT_ID \\
    --notification-channels=projects/$PROJECT_ID/notificationChannels/CHANNEL_ID \\
    --display-name="YAWL Engine Down" \\
    --condition-display-name="Engine health is 0" \\
    --condition-filter='metric.type="custom.googleapis.com/yawl/engine_health"' \\
    --condition-threshold-value=1 \\
    --condition-threshold-comparison="COMPARISON_LT"
```

### Log-based Metrics

```bash
# Create log-based metric for errors
gcloud logging metrics create yawl_errors \\
    --project=$PROJECT_ID \\
    --description="YAWL error log entries" \\
    --filter='resource.type="k8s_container" AND resource.labels.container_name="yawl-engine" AND severity>=ERROR'
```

## Backup and Recovery

### Cloud SQL Backups

```bash
# Create on-demand backup
gcloud sql backups create \\
    --project=$PROJECT_ID \\
    --instance=yawl-db

# List backups
gcloud sql backups list \\
    --project=$PROJECT_ID \\
    --instance=yawl-db

# Restore from backup
gcloud sql backups restore BACKUP_ID \\
    --project=$PROJECT_ID \\
    --restore-instance=yawl-db
```

### Database Export

```bash
# Export to Cloud Storage
gcloud sql export sql yawl-db \\
    gs://$PROJECT_ID-yawl-backups/backup-$(date +%Y%m%d).sql \\
    --database=yawl \\
    --project=$PROJECT_ID
```

### Kubernetes Backup with Velero

```bash
# Install Velero
velero install \\
    --provider gcp \\
    --plugins velero/velero-plugin-for-gcp:v1.7.0 \\
    --bucket $PROJECT_ID-velero-backups \\
    --secret-file ./credentials-velero

# Create backup
velero backup create yawl-backup \\
    --include-namespaces yawl

# Schedule daily backups
velero schedule create daily-yawl-backup \\
    --schedule="0 2 * * *" \\
    --include-namespaces yawl
```

## Troubleshooting

### Common Issues

#### Pod Stuck in Pending

```bash
# Check events
kubectl describe pod POD_NAME -n yawl

# Check node resources
kubectl describe nodes

# Check resource quotas
kubectl get resourcequota -n yawl
```

#### Database Connection Issues

```bash
# Verify Cloud SQL Proxy is running
kubectl logs -l app=cloudsql-proxy -n yawl

# Test connectivity
kubectl run psql-test --rm -it --image=postgres:15 -- \\
    psql "host=cloudsql-proxy port=5432 user=yawl_admin password=PASSWORD dbname=yawl"

# Check IAM permissions
gcloud projects get-iam-policy $PROJECT_ID \\
    --flatten="bindings[].members" \\
    --filter="bindings.members:yawl-engine"
```

#### High Memory Usage

```bash
# Check pod metrics
kubectl top pods -n yawl

# Check VPA recommendations
kubectl get vpa -n yawl -o yaml

# Adjust resource limits
kubectl set resources deployment/yawl-engine \\
    --namespace=yawl \\
    --limits=memory=4Gi \\
    --requests=memory=2Gi
```

### Useful Commands

```bash
# Check cluster status
gcloud container clusters describe yawl-cluster \\
    --region=us-central1 \\
    --project=$PROJECT_ID

# Check Cloud SQL status
gcloud sql instances describe yawl-db \\
    --project=$PROJECT_ID

# View recent logs
kubectl logs -l app=yawl-engine -n yawl --tail=100

# Port forward for local access
kubectl port-forward svc/yawl-engine 8080:80 -n yawl

# Execute command in pod
kubectl exec -it DEPLOYMENT/yawl-engine -n yawl -- /bin/sh
```

### Support Contacts

- **Documentation**: https://yawlfoundation.org/docs
- **Community Support**: https://yawlfoundation.org/community
- **Enterprise Support**: support@yawlfoundation.org
- **GitHub Issues**: https://github.com/yawlfoundation/yawl/issues

## References

- [GKE Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Cloud SQL Documentation](https://cloud.google.com/sql/docs)
- [YAWL Documentation](https://yawlfoundation.org/docs)
- [Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity)
