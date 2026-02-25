# YAWL on Oracle Cloud Infrastructure (OCI)

**Version:** 6.0.0
**Last Updated:** 2026-02-25

---

## 1. Overview

Deploy YAWL Workflow Engine on Oracle Cloud Infrastructure with OCI-native services integration.

### 1.1 Key Features on OCI

- **OKE Integration**: Oracle Kubernetes Engine
- **Autonomous Transaction Processing**: Self-managing PostgreSQL alternative
- **OCI Cache**: Redis-compatible caching
- **OCI Vault**: Secrets management
- **OCI Logging**: Native log aggregation
- **Dynamic Groups**: IAM for resources

### 1.2 Architecture on OCI

```
                    +-------------------+
                    |  OCI Load         |
                    |  Balancer         |
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v------------+ +----v------------+ +----v------------+
    | OKE Node Pool   | | OKE Node Pool   | | OKE Node Pool   |
    | Engine Pod 1    | | Engine Pod 2    | | Engine Pod N    |
    +-----------------+ +-----------------+ +-----------------+
         |                   |                   |
         +-------------------+-------------------+
                             |
              +--------------v--------------+
              |  Autonomous Transaction     |
              |  Processing (ATP)           |
              +-------------+---------------+
                            |
              +-------------v---------------+
              |  OCI Cache                  |
              +-----------------------------+
```

---

## 2. OCI Services

| Service | Purpose |
|---------|---------|
| **OKE** | Kubernetes cluster |
| **ATP** | Autonomous database |
| **OCI Cache** | Redis caching |
| **Vault** | Secrets |
| **Compartment** | Resource grouping |

---

## 3. Deployment

### 3.1 Prerequisites

```bash
# Install OCI CLI
pip install oci-cli

# Configure
oci setup config

# Verify
oci iam compartment list
```

### 3.2 Create OKE Cluster

```bash
oci ce cluster create \
  --compartment-id $COMPARTMENT_ID \
  --name yawl-cluster \
  --kubernetes-version v1.28 \
  --vcn-id $VCN_ID
```

### 3.3 Deploy via Terraform

```bash
cd terraform/oracle
terraform init
terraform apply
```

---

## 4. Next Steps

- [Security Overview](../security/security-overview.md)
- [Operations Guide](../operations/scaling-guide.md)
