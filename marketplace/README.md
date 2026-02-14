# YAWL Marketplace Configurations

Complete, production-ready marketplace deployment configurations for YAWL (Yet Another Workflow Language) across multiple cloud platforms and container registries.

## Overview

This directory contains everything needed to deploy YAWL to:
- Google Cloud Platform (GCP) Marketplace
- Amazon Web Services (AWS) Marketplace
- Microsoft Azure Marketplace
- Docker Hub Container Registry
- Kubernetes via Helm Charts

## Quick Navigation

### 1. GCP Marketplace (`gcp-marketplace/`)

Deploy YAWL on Google Cloud Platform with managed Kubernetes Engine.

**File:** `metadata.yaml` (90 lines)

Features:
- GCP Marketplace catalog integration
- Kubernetes cluster constraints (1.24+)
- Configurable properties (replicas, storage class, monitoring)
- Multi-container image support
- Service account configuration

**Quick Start:**
```bash
# Upload to GCP Console for one-click deployment
gsutil cp gcp-marketplace/metadata.yaml gs://your-bucket/
```

**Documentation:** See `gcp-marketplace/metadata.yaml`

---

### 2. AWS Marketplace (`aws-marketplace/`)

Deploy YAWL on AWS with CloudFormation infrastructure as code.

**File:** `product.yaml` (329 lines)

Features:
- Complete CloudFormation template
- RDS PostgreSQL with Multi-AZ, backup, and encryption
- Application Load Balancer with SSL/TLS support
- Auto Scaling Group for EC2 instances
- VPC, subnets, and security groups
- IAM roles and CloudWatch monitoring

**Quick Start:**
```bash
aws cloudformation create-stack \
  --stack-name yawl-deployment \
  --template-body file://aws-marketplace/product.yaml \
  --parameters ParameterKey=DomainName,ParameterValue=yawl.example.com \
  --capabilities CAPABILITY_IAM
```

**Documentation:** See AWS CloudFormation parameters in `product.yaml`

---

### 3. Azure Marketplace (`azure-marketplace/`)

Deploy YAWL on Microsoft Azure with interactive UI.

**File:** `createUiDefinition.json` (416 lines)

Features:
- Interactive deployment wizard UI
- Application, database, network, and security configuration
- VM sizing and database sizing options
- VNet and subnet management
- Azure AD authentication options
- Azure Monitor integration
- Resource tagging

**Quick Start:**
```bash
# Deploy via Azure Portal
az deployment group create \
  --name yawl-deployment \
  --resource-group yawl-rg \
  --template-file azure-marketplace/createUiDefinition.json
```

**Documentation:** See Azure UI sections in `createUiDefinition.json`

---

### 4. Docker Hub (`docker-hub/`)

Deploy YAWL using Docker containers.

**Files:**
- `Dockerfile` (51 lines) - Multi-stage Alpine-based container image
- `docker-compose.yml` (158 lines) - Complete stack with PostgreSQL, Redis, Prometheus, Grafana
- `.dockerignore` (28 lines) - Docker build optimizations
- `.env.example` (30 lines) - Environment configuration template
- `README.md` (208 lines) - Docker deployment guide

Features:
- Multi-stage optimized build (minimal image size)
- Java 17 with Eclipse Temurin
- Non-root user for security
- Health checks
- PostgreSQL 14 database
- Redis 7 cache
- Prometheus monitoring
- Grafana visualization

**Quick Start:**
```bash
cd docker-hub
cp .env.example .env
docker-compose up -d
```

Access YAWL at: http://localhost:8080

**Documentation:** See `docker-hub/README.md`

---

### 5. Kubernetes Helm Charts (`helm-repo/`)

Deploy YAWL on Kubernetes clusters with Helm.

**Files:**
- `Chart.yaml` (51 lines) - Helm chart metadata
- `values.yaml` (261 lines) - Default values
- `values-gcp.yaml` (200 lines) - GCP-specific overrides
- `values-aws.yaml` (276 lines) - AWS-specific overrides
- `values-azure.yaml` (278 lines) - Azure-specific overrides
- `README.md` (312 lines) - Helm deployment guide

Features:
- Multi-cloud support (GCP, AWS, Azure)
- High availability (3+ replicas default)
- Horizontal Pod Autoscaling
- Persistent volumes for data
- Network and security policies
- RBAC configuration
- Monitoring with Prometheus/Grafana
- Backup and disaster recovery
- External database/cache support

**Quick Start:**
```bash
# Default deployment
helm install yawl . --namespace yawl --create-namespace

# GCP deployment
helm install yawl . -f values-gcp.yaml --namespace yawl --create-namespace

# AWS deployment
helm install yawl . -f values-aws.yaml --namespace yawl --create-namespace

# Azure deployment
helm install yawl . -f values-azure.yaml --namespace yawl --create-namespace
```

**Documentation:** See `helm-repo/README.md`

---

## Master Guide

For comprehensive deployment instructions, configuration examples, and cloud-specific integration details:

**See:** `MARKETPLACE_GUIDE.md`

This guide includes:
- Complete directory structure reference
- Feature highlights by platform
- Configuration examples
- Environment variables reference
- Cloud-specific integration details
- Security best practices
- Production deployment checklist

---

## File Structure

```
marketplace/
├── README.md                          (this file)
├── MARKETPLACE_GUIDE.md              (comprehensive deployment guide)
│
├── gcp-marketplace/
│   └── metadata.yaml                 (90 lines - GCP marketplace config)
│
├── aws-marketplace/
│   └── product.yaml                  (329 lines - CloudFormation template)
│
├── azure-marketplace/
│   └── createUiDefinition.json       (416 lines - Azure deployment UI)
│
├── docker-hub/
│   ├── Dockerfile                    (51 lines - container image)
│   ├── docker-compose.yml            (158 lines - complete stack)
│   ├── .dockerignore                 (28 lines - build exclusions)
│   ├── .env.example                  (30 lines - environment template)
│   └── README.md                     (208 lines - deployment guide)
│
└── helm-repo/
    ├── Chart.yaml                    (51 lines - chart metadata)
    ├── values.yaml                   (261 lines - default values)
    ├── values-gcp.yaml               (200 lines - GCP values)
    ├── values-aws.yaml               (276 lines - AWS values)
    ├── values-azure.yaml             (278 lines - Azure values)
    └── README.md                     (312 lines - deployment guide)

Total: 16 files, 3,098 lines of configuration
```

---

## Deployment Summary

| Platform | Type | Entry Point | Components |
|----------|------|-------------|------------|
| **GCP** | Marketplace | metadata.yaml | GKE + PostgreSQL + Redis |
| **AWS** | CloudFormation | product.yaml | EC2 + RDS + ALB + ASG |
| **Azure** | ARM Template | createUiDefinition.json | AKS + Azure DB + AppGw |
| **Docker** | Container Registry | docker-compose.yml | App + PostgreSQL + Redis |
| **Kubernetes** | Helm | helm-repo/ | StatefulSet + Services + Ingress |

---

## Key Features

### All Platforms
- PostgreSQL 14 database
- Redis 7 caching
- Health checks and monitoring
- Persistent data storage
- Security best practices
- Backup and recovery support
- Scalability options

### Cloud-Specific
- **GCP:** Workload Identity, Cloud SQL, Cloud Monitoring
- **AWS:** RDS Multi-AZ, ALB, CloudWatch, S3 backups
- **Azure:** Managed Identity, Key Vault, Application Gateway
- **Docker:** Multi-stage builds, docker-compose, volume management
- **Kubernetes:** RBAC, Network Policies, HPA, Pod Disruption Budgets

---

## Getting Started

### Choose Your Platform

1. **GCP Users:** Start with `gcp-marketplace/README.md` or `MARKETPLACE_GUIDE.md`
2. **AWS Users:** Start with `aws-marketplace/product.yaml` or `MARKETPLACE_GUIDE.md`
3. **Azure Users:** Start with `azure-marketplace/createUiDefinition.json` or `MARKETPLACE_GUIDE.md`
4. **Docker Users:** Start with `docker-hub/README.md`
5. **Kubernetes Users:** Start with `helm-repo/README.md`

### Configuration Steps

1. **Review** the appropriate configuration file for your platform
2. **Update** placeholder values (passwords, domains, cloud IDs)
3. **Deploy** using the platform-specific instructions
4. **Verify** health checks and monitoring
5. **Configure** backups and security settings

---

## Support and Documentation

- **YAWL Documentation:** https://docs.yawl.org
- **YAWL GitHub:** https://github.com/yawl/yawl
- **Community Forum:** https://forum.yawl.org
- **Issue Tracker:** https://github.com/yawl/yawl/issues

---

## Security

All configurations include:
- Non-root container users
- Network policies
- RBAC (Role-Based Access Control)
- Secret management
- SSL/TLS encryption
- Database encryption at rest
- Audit logging

---

## Production Checklist

Before deploying to production:

- [ ] Update all placeholder passwords and secrets
- [ ] Configure domain names and certificates
- [ ] Enable monitoring and alerting
- [ ] Set up automated backups
- [ ] Configure auto-scaling policies
- [ ] Review and adjust resource limits
- [ ] Test disaster recovery procedures
- [ ] Enable audit logging
- [ ] Configure access controls
- [ ] Review security settings

---

## Version Information

- **Chart Version:** 1.0.0
- **App Version:** 1.0.0
- **Minimum Kubernetes:** 1.24
- **Database:** PostgreSQL 14
- **Cache:** Redis 7
- **Base Image:** Alpine Linux (Docker)
- **Java Version:** 17 (Eclipse Temurin)

---

## License

All configurations are provided as-is for deploying YAWL. See the main YAWL repository for licensing information.

---

## File Statistics

- **Total Configuration Files:** 16
- **Total Lines of Code:** 3,098
- **Total Size:** ~108 KB
- **Formats:** YAML, JSON, Dockerfile, Shell Scripts

---

Last Updated: 2026-02-14

For the latest version and updates, visit: https://github.com/yawl/yawl
