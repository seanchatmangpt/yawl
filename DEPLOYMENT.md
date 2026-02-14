# YAWL GCP Marketplace Deployment Guide

## Overview

This guide provides comprehensive instructions for deploying YAWL Workflow Engine to Google Cloud Platform through the GCP Marketplace.

## Prerequisites

- Google Cloud Project with billing enabled
- gcloud CLI installed and configured
- kubectl installed (for Kubernetes deployment)
- Docker installed (for building images)
- Terraform 1.3+ installed (for IaC deployment)

## Quick Start

### Option 1: One-Click Deployment (GCP Marketplace)

1. Navigate to [GCP Marketplace](https://console.cloud.google.com/marketplace)
2. Search for "YAWL Workflow Engine"
3. Click "Launch on Compute Engine" or "Launch on GKE"
4. Configure parameters:
   - Project
   - Region
   - Machine type
   - Database tier
5. Click "Deploy"

### Option 2: Kubernetes Deployment (Recommended for Production)

1. **Set environment variables:**
   ```bash
   export GCP_PROJECT_ID="your-project-id"
   export GCP_REGION="us-central1"
   ```

2. **Run deployment script:**
   ```bash
   chmod +x deploy/deploy.sh
   ./deploy/deploy.sh
   ```

3. **Verify deployment:**
   ```bash
   kubectl get deployment yawl -n yawl
   kubectl get services -n yawl
   ```

### Option 3: Cloud Run Deployment (Lightweight)

1. **Run Cloud Run deployment script:**
   ```bash
   chmod +x deploy/deploy-cloud-run.sh
   ./deploy/deploy-cloud-run.sh
   ```

2. **Access YAWL:**
   ```bash
   gcloud run services describe yawl-workflow --region=us-central1 --format='value(status.url)'
   ```

### Option 4: Terraform Deployment (Infrastructure as Code)

1. **Configure Terraform variables:**
   ```bash
   cp terraform/terraform.tfvars.example terraform/terraform.tfvars
   # Edit terraform.tfvars with your settings
   ```

2. **Initialize and deploy:**
   ```bash
   cd terraform
   terraform init
   terraform plan
   terraform apply
   ```

## Architecture

### Kubernetes Architecture (GKE)

```
┌─────────────────────────────────────────┐
│       GCP Marketplace                   │
│  ┌─────────────────────────────────┐    │
│  │   Load Balancer / Ingress      │    │
│  └────────────────┬────────────────┘    │
│                   │                     │
│  ┌────────────────▼────────────────┐    │
│  │   GKE Cluster (yawl-cluster)   │    │
│  │  ┌─────────────────────────────┤    │
│  │  │  Pod 1      Pod 2   Pod 3    │    │
│  │  │  YAWL      YAWL    YAWL      │    │
│  │  └──┬──────────┬────────┬──────┘    │
│  │     └──────────┼────────┘           │
│  │                │                    │
│  │  ┌────────────▼──────────────────┐  │
│  │  │   Cloud SQL Proxy Service    │  │
│  │  │   (PostgreSQL Connection)    │  │
│  │  └────────────┬──────────────────┘  │
│  └───────────────┼────────────────────┘ │
└─────────────────┼──────────────────────┘
                  │
        ┌─────────▼────────────┐
        │  Cloud SQL Instance  │
        │  (PostgreSQL 14)     │
        └──────────────────────┘
```

### Cloud Run Architecture

```
┌──────────────────────────┐
│   GCP Marketplace        │
│   ┌────────────────────┐ │
│   │  Cloud Run Service │ │
│   │  (YAWL Container)  │ │
│   └────────┬───────────┘ │
└────────────┼──────────────┘
             │
    ┌────────▼────────┐
    │  Cloud SQL      │
    │  (PostgreSQL)   │
    └─────────────────┘
```

## Components

### Docker Image
- **Location**: `docker/`
- **Base**: Tomcat 9.0 with Java 11
- **Services**: All YAWL microservices
- **Size**: ~1.2GB

### Kubernetes Manifests
- **Deployment**: 3 replicas with auto-scaling
- **Service**: LoadBalancer type with session affinity
- **Ingress**: GCP Load Balancer integration
- **ConfigMap**: Application configuration
- **Secrets**: Database credentials
- **RBAC**: Service accounts and roles
- **NetworkPolicy**: Network isolation
- **HPA**: Horizontal Pod Autoscaler

### Infrastructure (Terraform)
- GKE Cluster with node pools
- Cloud SQL PostgreSQL instance
- Cloud Memorystore Redis
- Cloud Storage for backups
- Artifact Registry
- VPC with subnets
- Cloud Monitoring and Logging

## Configuration

### Database Configuration

**Cloud SQL Instance Details:**
- Engine: PostgreSQL 14
- Tier: db-custom-2-7680 (2 vCPU, 7.68GB RAM)
- Storage: 100GB SSD with auto-resize
- Backups: Daily with 30-day retention
- High Availability: Regional configuration

**Database Credentials:**
- Username: `yawl`
- Password: Auto-generated and stored in Kubernetes Secret
- Database: `yawl`

### Scaling Configuration

**Horizontal Pod Autoscaler:**
- Minimum replicas: 3
- Maximum replicas: 10
- CPU target: 70% utilization
- Memory target: 80% utilization

**Node Autoscaling:**
- Minimum nodes: 3
- Maximum nodes: 10
- Machine type: n2-standard-4 (4 vCPU, 16GB RAM)

## Security

### Network Security
- Private Cloud SQL instance
- VPC networking with private subnets
- Network policies restrict pod-to-pod communication
- Service accounts with minimal permissions

### Data Security
- All database connections over SSL/TLS
- Secrets stored in Kubernetes Secrets or Google Secret Manager
- Regular backups with encryption
- Application runs as non-root user

### Access Control
- RBAC for Kubernetes resources
- IAM roles for GCP resources
- Service account authentication
- Network policies for traffic control

## Monitoring and Logging

### Cloud Monitoring
- Pod CPU and memory metrics
- Application-level metrics via Prometheus
- Database performance metrics
- Alert policies for high resource usage

### Cloud Logging
- Container logs aggregated in Cloud Logging
- Application logs with structured formatting
- Audit logs for compliance
- Log retention: 30 days

### Health Checks
- Liveness probe: HTTP GET `/resourceService/`
- Readiness probe: HTTP GET `/resourceService/`
- Probes run every 30 seconds
- Failure threshold: 3 consecutive failures

## Cost Estimation

### Monthly Cost (Approximate)

**GKE:**
- Cluster management: $0.10/cluster/hour
- Node pool (3 nodes): ~$300/month
- Load Balancer: ~$20/month

**Cloud SQL:**
- Instance (db-custom-2-7680): ~$450/month
- Storage (100GB SSD): ~$15/month
- Backups: ~$50/month

**Additional Services:**
- Cloud Memorystore (5GB): ~$30/month
- Cloud Storage (backups): ~$5/month
- Monitoring and Logging: ~$20/month

**Total Estimated Cost: ~$890/month**

## Troubleshooting

### Deployment Issues

**Pods not starting:**
```bash
kubectl describe pod -n yawl
kubectl logs -n yawl deployment/yawl
```

**Database connection issues:**
```bash
kubectl exec -it pod/yawl-XXX -n yawl -- psql -h cloudsql-proxy -U yawl -d yawl
```

**High resource usage:**
```bash
kubectl top nodes
kubectl top pods -n yawl
```

### Common Issues

**CloudSQL Proxy connection timeout:**
- Verify VPC network configuration
- Check Cloud SQL instance is running
- Verify credentials in secrets

**Out of memory errors:**
- Increase pod memory limits
- Scale up node resources
- Check application logs for memory leaks

**Deployment timeout:**
- Increase readiness probe initialDelaySeconds
- Check resource requests vs node capacity
- Verify artifact registry image availability

## Maintenance

### Regular Tasks

**Daily:**
- Monitor application health
- Check error logs

**Weekly:**
- Review performance metrics
- Update dependencies (if applicable)

**Monthly:**
- Database maintenance
- Security patches
- Cost review

### Backup and Restore

**Automated Backups:**
```bash
# Cloud SQL automatic backups are enabled
# Backup window: 03:00 UTC
# Retention: 30 days
```

**Manual Backup:**
```bash
gcloud sql backups create \
  --instance=yawl-postgres-14 \
  --description="Manual backup"
```

**Restore from Backup:**
```bash
gcloud sql backups restore BACKUP_ID \
  --backup-instance=yawl-postgres-14 \
  --backup-configuration=automated
```

## Support and Documentation

- **YAWL Foundation**: https://www.yawlfoundation.org
- **Documentation**: https://docs.yawlfoundation.org
- **Support Forum**: https://forum.yawlfoundation.org
- **GitHub**: https://github.com/yawlfoundation/yawl

## License

YAWL is distributed under the GNU LGPL 3.0 License. See LICENSE.txt for details.

## Contact

For support related to this GCP Marketplace deployment:
- Email: support@yawlfoundation.org
- Website: https://www.yawlfoundation.org/support
