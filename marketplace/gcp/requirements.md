# GCP Marketplace Requirements for YAWL

This document outlines the requirements for publishing YAWL Workflow Engine on Google Cloud Marketplace.

## Overview

YAWL (Yet Another Workflow Language) is a BPM/Workflow engine with formal foundations based on Petri Nets. This guide covers the technical and business requirements for GCP Marketplace publication.

## Technical Requirements

### 1. Solution Architecture

| Component | Requirement | YAWL Implementation |
|-----------|-------------|---------------------|
| Container Runtime | Docker/Kubernetes | GKE deployment with Helm charts |
| Database | PostgreSQL 14+ | Cloud SQL (PostgreSQL) |
| Storage | Object storage | Cloud Storage (GCS) |
| Networking | VPC-native | Private cluster with authorized networks |
| Monitoring | Cloud Monitoring | Managed Prometheus integration |

### 2. Container Requirements

- **Base Image**: Must use approved base images (Ubuntu, Debian, Alpine, or Distroless)
- **Image Registry**: Google Artifact Registry or Container Registry
- **Security Scanning**: All images must pass Container Analysis API scanning
- **Vulnerability Management**: No critical vulnerabilities allowed at publication

```dockerfile
# Required labels
LABEL org.opencontainers.image.source="https://github.com/yawlfoundation/yawl"
LABEL org.opencontainers.image.description="YAWL Workflow Engine"
LABEL org.opencontainers.image.licenses="LGPL-3.0"
```

### 3. Kubernetes Requirements

- **Helm Chart**: Production-grade Helm chart required
- **Kubernetes Version**: Support GKE 1.27+ (current stable channel)
- **Resource Requests**: Define CPU/memory requests and limits
- **Probes**: Liveness and readiness probes required
- **ConfigMaps/Secrets**: Externalized configuration

### 4. Database Requirements

| Setting | Value | Notes |
|---------|-------|-------|
| Version | PostgreSQL 14+ | Cloud SQL supported versions |
| High Availability | REGIONAL | Multi-zone deployment |
| Backup | Enabled | 30-day retention minimum |
| SSL | Required | ENCRYPTED_ONLY mode |
| Deletion Protection | Enabled | Production requirement |

### 5. Security Requirements

- **Service Accounts**: Minimal IAM permissions (principle of least privilege)
- **Workload Identity**: Required for GKE workloads
- **Private Cluster**: Private endpoint and nodes recommended
- **Network Policies**: Calico network policies enabled
- **Binary Authorization**: Optional but recommended

### 6. IAM Roles Required

```hcl
# GKE Node Service Account
roles/logging.logWriter
roles/monitoring.metricWriter
roles/monitoring.viewer
roles/stackdriver.resourceMetadata.writer
roles/artifactregistry.reader
roles/storage.objectViewer

# Application Service Account
roles/cloudsql.client
roles/cloudsql.instanceUser
roles/storage.objectAdmin
roles/secretmanager.secretAccessor
```

## Business Requirements

### 1. Partner Requirements

- [ ] Google Cloud Partner registration
- [ ] Signed Marketplace Publisher Agreement
- [ ] Tax documentation (W-9/W-8BEN)
- [ ] Banking information for payouts

### 2. Listing Requirements

- **Product Name**: YAWL Workflow Engine
- **Publisher**: YAWL Foundation
- **Category**: Developer Tools > Workflow
- **Tags**: workflow, bpm, petri-net, java, kubernetes
- **Support URL**: https://yawlfoundation.org/support
- **Documentation URL**: https://yawlfoundation.org/docs

### 3. Solution Metadata

```yaml
# Minimum required metadata
name: yawl-workflow-engine
version: 5.2.0
description: |
  YAWL (Yet Another Workflow Language) is a BPM/Workflow engine
  with formal foundations based on Petri Nets.
website: https://yawlfoundation.org
documentation: https://yawlfoundation.org/docs
support: https://yawlfoundation.org/support
```

### 4. Pricing Requirements

- Must define at least one pricing plan
- Pricing must be in USD
- Free tier or trial recommended for evaluation
- Consumption-based or subscription model

## Publication Checklist

### Pre-Submission

- [ ] Container images built and scanned
- [ ] Helm charts tested on GKE
- [ ] Terraform deployment validated
- [ ] Documentation complete
- [ ] Support process established

### Technical Validation

- [ ] Deployment Manager integration tested
- [ ] IAM roles validated
- [ ] Network connectivity verified
- [ ] Monitoring dashboards configured
- [ ] Backup/restore procedures documented

### Review Process

- [ ] Submit for Google technical review
- [ ] Address feedback from review
- [ ] Complete security assessment
- [ ] Finalize pricing
- [ ] Set launch date

## Required APIs

The following Google Cloud APIs must be enabled in the customer project:

```bash
# Required APIs
gcloud services enable compute.googleapis.com
gcloud services enable container.googleapis.com
gcloud services enable sqladmin.googleapis.com
gcloud services enable servicenetworking.googleapis.com
gcloud services enable secretmanager.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable artifactregistry.googleapis.com
gcloud services enable monitoring.googleapis.com
gcloud services enable logging.googleapis.com
```

## Deployment Models

### Single-Tenant (Recommended for Marketplace)

- Dedicated GKE cluster per customer
- Dedicated Cloud SQL instance
- Private VPC networking
- Full isolation between deployments

### Multi-Tenant (Not Recommended)

- Shared GKE cluster with namespace isolation
- Shared Cloud SQL with schema separation
- Requires additional security considerations

## Resource Quotas

Default resource requirements for a standard deployment:

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| GKE Nodes | 3 | 5 |
| Node CPU | 4 vCPU | 8 vCPU |
| Node Memory | 16 GB | 32 GB |
| Cloud SQL Storage | 100 GB | 500 GB |
| Cloud SQL CPU | 2 vCPU | 4 vCPU |
| Cloud SQL Memory | 8 GB | 16 GB |

## Support Tiers

| Tier | Response Time | Coverage | Channels |
|------|---------------|----------|----------|
| Basic | 72 hours | Business hours | Email |
| Standard | 24 hours | 24x5 | Email, Web |
| Premium | 4 hours | 24x7 | Email, Web, Phone |

## References

- [GCP Marketplace Partner Portal](https://console.cloud.google.com/partner)
- [Solution Guide for Kubernetes Apps](https://cloud.google.com/marketplace/docs/partners/kubernetes-solutions)
- [Deployment Manager Documentation](https://cloud.google.com/deployment-manager/docs)
- [GKE Best Practices](https://cloud.google.com/kubernetes-engine/docs/best-practices)
