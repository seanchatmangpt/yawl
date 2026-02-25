# YAWL on IBM Cloud

**Version:** 6.0.0
**Last Updated:** 2026-02-25

---

## 1. Overview

Deploy YAWL Workflow Engine on IBM Cloud with Red Hat OpenShift integration.

### 1.1 Key Features on IBM Cloud

- **Red Hat OpenShift**: Managed Kubernetes with OpenShift features
- **Databases for PostgreSQL**: Managed PostgreSQL
- **Databases for Redis**: Managed Redis
- **Secrets Manager**: IBM Secrets Manager
- **IBM Log Analysis**: Log aggregation
- **IBM Cloud Monitoring**: Metrics and alerting

### 1.2 Architecture on IBM Cloud

```
                    +-------------------+
                    |  IBM Cloud        |
                    |  Internet Services|
                    +--------+----------+
                             |
                    +--------v----------+
                    |  OpenShift Router |
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v------------+ +----v------------+ +----v------------+
    | OpenShift Pod   | | OpenShift Pod   | | OpenShift Pod   |
    | Engine Pod 1    | | Engine Pod 2    | | Engine Pod N    |
    +-----------------+ +-----------------+ +-----------------+
         |                   |                   |
         +-------------------+-------------------+
                             |
              +--------------v--------------+
              |  Databases for              |
              |  PostgreSQL                |
              +-------------+---------------+
                            |
              +-------------v---------------+
              |  Databases for Redis        |
              +-----------------------------+
```

---

## 2. IBM Cloud Services

| Service | Purpose |
|---------|---------|
| **Red Hat OpenShift** | Container platform |
| **Databases for PostgreSQL** | Managed database |
| **Databases for Redis** | Managed cache |
| **Secrets Manager** | Secrets |
| **Log Analysis** | Logging |
| **Monitoring** | Metrics |

---

## 3. Deployment

### 3.1 Prerequisites

```bash
# Install IBM Cloud CLI
brew install ibm-cloud-cli

# Login
ibmcloud login

# Target resource group
ibmcloud target -g yawl-rg
```

### 3.2 Create OpenShift Cluster

```bash
ibmcloud oc cluster create classic \
  --name yawl-cluster \
  --location dal10 \
  --machine-type b3c.4x16 \
  --workers 3
```

### 3.3 Deploy YAWL

```bash
oc new-project yawl

helm upgrade --install yawl yawl/yawl-stack \
  --namespace yawl
```

---

## 4. Next Steps

- [Security Overview](../security/security-overview.md)
- [Operations Guide](../operations/scaling-guide.md)
