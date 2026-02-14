# Quick Start: YAWL Teradata Deployment Guide

This guide provides step-by-step instructions to deploy YAWL with Teradata integration on Google Cloud Platform.

## Prerequisites

1. GCP Project with billing enabled
2. Terraform 1.3+ installed locally
3. gcloud CLI configured
4. Docker installed (for building images)
5. kubectl installed (for Kubernetes operations)
6. Access to Teradata JDBC driver license/download

## Quick Deployment (15 minutes)

### Step 1: Prepare Environment

```bash
# Set environment variables
export PROJECT_ID="your-gcp-project"
export REGION="us-central1"
export ZONE="${REGION}-a"

# Authenticate with GCP
gcloud auth login
gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable compute.googleapis.com \
    container.googleapis.com \
    sqladmin.googleapis.com \
    redis.googleapis.com \
    storage.googleapis.com \
    artifactregistry.googleapis.com
```

### Step 2: Configure Terraform Variables

```bash
cd /home/user/yawl/teradata

# Copy and edit configuration
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
cat > terraform.tfvars <<EOF
gcp_project_id              = "$PROJECT_ID"
gcp_region                  = "$REGION"
environment                 = "prod"
teradata_admin_password     = "$(openssl rand -base64 32)"
yawl_db_password            = "$(openssl rand -base64 32)"
alert_email                 = "your-email@example.com"
teradata_instance_type      = "n2-highmem-8"
teradata_disk_size          = 500
gke_node_count              = 3
enable_monitoring           = true
enable_automatic_backups    = true
EOF
```

### Step 3: Deploy Infrastructure

```bash
# Initialize Terraform backend
terraform init \
    -backend-config="bucket=yawl-terraform-state-${PROJECT_ID}" \
    -backend-config="prefix=teradata"

# Validate configuration
terraform validate

# Plan deployment
terraform plan -out=tfplan

# Apply configuration (takes ~20-30 minutes)
terraform apply tfplan

# Save outputs
terraform output > deployment-outputs.json
```

### Step 4: Initialize Teradata Database

```bash
# Get Teradata instance IP
TERADATA_IP=$(terraform output -raw teradata_instance_ip)
TERADATA_PORT=$(terraform output -raw teradata_port)

# Wait for Teradata to be ready (5-10 minutes)
echo "Waiting for Teradata to start..."
sleep 300

# Connect and initialize schema
gcloud compute ssh yawl-teradata --zone=$ZONE <<'EOF'
bteq <<'BTEQ'
.LOGON $TERADATA_IP/dbc,ChangeMe
.RUN FILE = /home/yawl/teradata/schema-yawl-base.sql
.RUN FILE = /home/yawl/teradata/schema-yawl-extensions.sql
.EXIT
BTEQ
EOF
```

### Step 5: Deploy YAWL Application

```bash
# Get GKE credentials
gcloud container clusters get-credentials yawl-teradata-cluster --region $REGION

# Create namespace
kubectl create namespace yawl

# Create secrets for database credentials
kubectl create secret generic yawl-db-credentials \
    --from-literal=teradata-host=$TERADATA_IP \
    --from-literal=teradata-port=$TERADATA_PORT \
    --from-literal=postgres-host=$(terraform output -raw yawl_metadata_connection_name | cut -d: -f1) \
    -n yawl

# Deploy YAWL application
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-workflow
  namespace: yawl
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl
  template:
    metadata:
      labels:
        app: yawl
    spec:
      containers:
      - name: yawl
        image: gcr.io/$PROJECT_ID/yawl-teradata:latest
        ports:
        - containerPort: 8080
        env:
        - name: TERADATA_HOST
          valueFrom:
            secretKeyRef:
              name: yawl-db-credentials
              key: teradata-host
        - name: TERADATA_PORT
          valueFrom:
            secretKeyRef:
              name: yawl-db-credentials
              key: teradata-port
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
EOF

# Verify deployment
kubectl get pods -n yawl
kubectl get services -n yawl
```

### Step 6: Verify Deployment

```bash
# Check all resources
echo "=== GCP Resources ==="
gcloud compute instances list --filter="name:yawl-teradata"
gcloud container clusters list --region=$REGION
gcloud redis instances list --region=$REGION
gcloud sql instances list

# Check connectivity
echo "=== Connectivity Tests ==="
gcloud compute ssh yawl-teradata --zone=$ZONE \
    --command="bteq < EOF
.logevent console;
SELECT COUNT(*) AS schema_count FROM dbc.tables
WHERE databasename='yawl_workflow';
.EXIT;
EOF"

# Check YAWL pods
echo "=== YAWL Pods ==="
kubectl get pods -n yawl -o wide

# Test YAWL endpoint
echo "=== YAWL Access ==="
kubectl port-forward -n yawl service/yawl-workflow 8080:8080 &
sleep 5
curl http://localhost:8080/resourceService/
kill %1
```

## Customization Options

### Database Size Configuration

```bash
# Small deployment (dev/test)
teradata_instance_type      = "n2-standard-4"
teradata_disk_size          = 200
yawl_db_initial_space_gb    = 25
gke_node_count              = 1

# Large deployment (production)
teradata_instance_type      = "n2-highmem-32"
teradata_disk_size          = 2000
yawl_db_initial_space_gb    = 200
gke_node_count              = 10
```

### High Availability

```bash
# Enable multi-zone deployment
enable_multi_zone_deployment = true

# Enable cross-region replication
enable_cross_region_replication = true
replica_region                  = "us-west1"
```

### Security Hardening

```bash
# Restrict network access
allowed_cidr_blocks = ["203.0.113.0/24"]  # Your office IP

# Enable all security features
enable_encryption_at_rest   = true
enable_encryption_in_transit = true
teradata_port               = 1025  # Non-default port
```

## Monitoring

### Access Cloud Monitoring Dashboard

```bash
# Open GCP Console
gcloud compute instances list --filter="name:yawl-teradata" \
    --format="table(name,zone)" | \
    xargs -I {} echo "Instance created: {}"

# View in Cloud Console
echo "https://console.cloud.google.com/monitoring?project=$PROJECT_ID"
```

### View Logs

```bash
# Real-time YAWL logs
kubectl logs -f -n yawl deployment/yawl-workflow

# Teradata logs
gcloud compute ssh yawl-teradata --zone=$ZONE \
    --command="tail -f /var/log/teradata/tdserver.log"

# GCP logs
gcloud logging read "resource.type=gce_instance" --limit 50 --format json
```

## Cleanup

To remove all resources and avoid charges:

```bash
# Destroy Terraform resources
terraform destroy

# Remove GCP project resources
gcloud projects delete $PROJECT_ID

# Clean up local files
rm -rf terraform.tfstate* tfplan
```

## Troubleshooting

### Teradata won't start

```bash
# Check GCP instance status
gcloud compute instances describe yawl-teradata --zone=$ZONE

# SSH and check service
gcloud compute ssh yawl-teradata --zone=$ZONE
sudo systemctl status teradata
sudo journalctl -u teradata -n 100
```

### YAWL can't connect to Teradata

```bash
# Check network connectivity
kubectl exec -it -n yawl pod/yawl-workflow-xxx -- \
    telnet teradata-ip 1025

# Check firewall rules
gcloud compute firewall-rules list --filter="name:teradata"

# Verify credentials
kubectl get secret yawl-db-credentials -n yawl -o yaml
```

### Pod fails to start

```bash
# Check pod events
kubectl describe pod -n yawl pod/yawl-workflow-xxx

# Check pod logs
kubectl logs -n yawl pod/yawl-workflow-xxx --tail=100

# Check resource availability
kubectl describe nodes
kubectl top nodes
```

## Performance Testing

```bash
# Test workflow throughput
kubectl exec -it -n yawl deployment/yawl-workflow -- bash

# Inside container:
curl -X POST http://localhost:8080/resourceService/launch \
    -H "Content-Type: application/json" \
    -d '{"processDefinition":"YAWLProcess","caseData":{}}'

# Monitor performance
kubectl top pods -n yawl --containers
gcloud monitoring time-series list --filter \
    "metric.type=\"compute.googleapis.com/instance/cpu/utilization\""
```

## Next Steps

1. **Configure External Access**: Set up load balancer and DNS
2. **Setup Backups**: Configure automated backup schedule
3. **Create Process Definitions**: Build your workflows
4. **Setup Users**: Configure participants and resources
5. **Integrate External Services**: Connect web services and APIs
6. **Setup Monitoring Alerts**: Configure email/SMS notifications
7. **Plan Capacity**: Monitor usage and scale as needed

## Documentation

- Full Integration Guide: `TERADATA_INTEGRATION.md`
- Database Schema: `schema-yawl-base.sql`, `schema-yawl-extensions.sql`
- Terraform Configuration: `main.tf`, `variables.tf`
- Connection Config: `teradata-connection.properties.example`

## Support

For issues or questions:
1. Check `TERADATA_INTEGRATION.md` troubleshooting section
2. Review GCP Cloud Logging
3. Check Teradata error logs
4. Contact YAWL support at support@yawlfoundation.org

---

**Deployment Time**: ~30-45 minutes
**Estimated Cost**: $200-500/month depending on configuration
**SLA**: 99.9% uptime (with multi-zone deployment)
