# YAWL Multi-Cloud Architecture

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Architecture Overview

YAWL (Yet Another Workflow Language) is a Java-based BPM/Workflow engine designed for deployment across multiple cloud platforms. This document describes the multi-cloud architecture that enables consistent deployment on AWS, Azure, GCP, Oracle Cloud, IBM Cloud, and Teradata Vantage.

### 1.1 Design Principles

| Principle | Description |
|-----------|-------------|
| **Cloud-Agnostic Core** | All business logic remains platform-independent |
| **Externalized State** | State stored in managed databases and caches |
| **Container-First** | Docker containers as the deployment unit |
| **Infrastructure as Code** | Terraform/Helm for reproducible deployments |
| **Security by Default** | TLS, encrypted storage, least-privilege access |

### 1.2 Component Architecture

```
                                    +-------------------+
                                    |  DNS / CDN        |
                                    |  (Cloud-specific) |
                                    +--------+----------+
                                             |
                                    +--------v----------+
                                    |  Load Balancer    |
                                    |  (L7/TLS term)    |
                                    +--------+----------+
                                             |
              +------------------------------+------------------------------+
              |                              |                              |
    +---------v---------+          +---------v---------+          +---------v---------+
    |  Ingress          |          |  Ingress          |          |  Ingress          |
    |  Controller       |          |  Controller       |          |  Controller       |
    +---------+---------+          +---------+---------+          +---------+---------+
              |                              |                              |
              +------------------------------+------------------------------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
          +---------v---------+    +---------v---------+    +---------v---------+
          |  YAWL Engine      |    |  Resource         |    |  Worklet          |
          |  (yawl.war)       |    |  Service          |    |  Service          |
          |  Replicas: 2+     |    |  (resourceService)|    |  (workletService) |
          +---------+---------+    +---------+---------+    +---------+---------+
                    |                        |                        |
                    +------------------------+------------------------+
                                             |
                    +------------------------+------------------------+
                    |                                                 |
          +---------v---------+                              +---------v---------+
          |  PostgreSQL       |                              |  Redis Cluster    |
          |  (Managed DB)     |                              |  (Cache/Sessions) |
          |  Primary + Read   |                              |  Sentinel Mode    |
          |  Replicas         |                              |                   |
          +-------------------+                              +-------------------+
```

---

## 2. Service Components

### 2.1 Core Services (Required)

| Service | WAR File | State | Replicas | Description |
|---------|----------|-------|----------|-------------|
| **Engine** | `yawl.war` | Database | 2+ | Core workflow execution engine |
| **ResourceService** | `resourceService.war` | Database | 2+ | Human/non-human resource management |
| **WorkletService** | `workletService.war` | Database | 1+ | Dynamic process adaptation |

### 2.2 Extended Services (Optional)

| Service | WAR File | State | Description |
|---------|----------|-------|-------------|
| **MonitorService** | `monitorService.war` | Stateless | Process monitoring UI |
| **SchedulingService** | `schedulingService.war` | Database | Calendar-based scheduling |
| **CostService** | `costService.war` | Database | Cost tracking |
| **Balancer** | `balancer.war` | Stateless | Load balancing across engines |
| **MailService** | `mailService.war` | Stateless | Email notifications |
| **DocumentStore** | `documentStore.war` | Database | Document storage |

### 2.3 Service Endpoints

| Service | Interface | Path | Purpose |
|---------|-----------|------|---------|
| Engine | A | `/ia/*` | Design-time operations |
| Engine | B | `/ib/*` | Client/runtime operations |
| Engine | X | `/ix/*` | Extended operations |
| Engine | E | `/logGateway` | Event log access |
| ResourceService | B | `/ib/*` | Resource operations |
| WorkletService | B | `/ib/*` | Worklet operations |

---

## 3. Container Architecture

### 3.1 Container Images

Each YAWL service is packaged as a Docker container:

```dockerfile
# Base structure for all services
FROM eclipse-temurin:11-jre-alpine

# Tomcat 9.x servlet container
ENV CATALINA_HOME=/opt/tomcat
ENV TOMCAT_VERSION=9.0.85

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Non-root user
RUN addgroup -S yawl && adduser -S yawl -G yawl
USER yawl

EXPOSE 8080 8443
CMD ["/opt/tomcat/bin/catalina.sh", "run"]
```

### 3.2 Image Registry Strategy

| Cloud | Registry | Image Path |
|-------|----------|------------|
| AWS | ECR | `123456789012.dkr.ecr.us-east-1.amazonaws.com/yawl/engine:5.2` |
| Azure | ACR | `yawlmcr.azurecr.io/yawl/engine:5.2` |
| GCP | Artifact Registry | `us-docker.pkg.dev/yawl-project/yawl/engine:5.2` |
| Oracle | OCIR | `iad.ocir.io/tenant/yawl/engine:5.2` |
| IBM | ICR | `icr.io/yawl/engine:5.2` |

### 3.3 Container Resource Requirements

| Service | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---------|-------------|-----------|----------------|--------------|
| Engine | 500m | 2000m | 1Gi | 4Gi |
| ResourceService | 500m | 2000m | 1Gi | 4Gi |
| WorkletService | 250m | 1000m | 512Mi | 2Gi |
| MonitorService | 100m | 500m | 256Mi | 1Gi |
| SchedulingService | 250m | 1000m | 512Mi | 2Gi |
| CostService | 100m | 500m | 256Mi | 1Gi |
| Balancer | 100m | 500m | 256Mi | 512Mi |

---

## 4. Data Architecture

### 4.1 Database Schema

YAWL uses Hibernate ORM with 73 mapping files. Key entities:

```
+------------------+       +------------------+
| YSpecification   |       | YNetInstance     |
+------------------+       +------------------+
| id (PK)          |<----->| id (PK)          |
| identifier       |       | case_id          |
| version          |       | spec_id (FK)     |
| root_net_id      |       | status           |
+------------------+       | created_time     |
                           +------------------+
                                   |
                                   v
                           +------------------+
                           | YWorkItem        |
                           +------------------+
                           | id (PK)           |
                           | case_id (FK)      |
                           | task_id           |
                           | status            |
                           | enablement_time   |
                           +------------------+
```

### 4.2 Managed Database Configuration

| Cloud | Service | Instance Type | HA Configuration |
|-------|---------|---------------|------------------|
| AWS | RDS for PostgreSQL | db.r6g.xlarge | Multi-AZ, 2 read replicas |
| Azure | Azure Database for PostgreSQL | GP_Gen5_4 | Zone-redundant, 1 replica |
| GCP | Cloud SQL for PostgreSQL | db-custom-4-16384 | Regional HA, read replica |
| Oracle | Autonomous Transaction Processing | ATP-OCPU-4 | Always-on HA |
| IBM | Databases for PostgreSQL | 4 cores, 16GB | HA enabled |

### 4.3 Connection Pooling

```yaml
# Hibernate C3P0 configuration
hibernate:
  c3p0:
    min_size: 5
    max_size: 50
    timeout: 300
    max_statements: 100
    unreturned_connection_timeout: 30
```

### 4.4 Redis Cache Architecture

Redis provides:
- HTTP session clustering
- Hibernate L2 cache
- Work item caching
- Process state caching

```
+-------------------+     +-------------------+
|  Redis Sentinel 1 |     |  Redis Sentinel 2 |
|  (Monitor)        |     |  (Monitor)        |
+--------+----------+     +--------+----------+
         |                         |
         +------------+------------+
                      |
         +------------v------------+
         |      Redis Master       |
         |    (Cache + Sessions)   |
         +------------+------------+
                      |
         +------------+------------+
         |                       |
+--------v----------+    +--------v----------+
|  Redis Replica 1  |    |  Redis Replica 2  |
+-------------------+    +-------------------+
```

---

## 5. Network Architecture

### 5.1 Network Topology

```
+------------------------------------------------------------------+
|                          VPC / VNet                               |
+------------------------------------------------------------------+
|                                                                   |
|  +-------------------------+    +-----------------------------+  |
|  |     Public Subnet       |    |      Private Subnet         |  |
|  |                         |    |                             |  |
|  |  +-----------------+    |    |  +-----------------------+  |  |
|  |  |  Load Balancer  |    |    |  |  Kubernetes Nodes    |  |  |
|  |  |  (ALB/CLB/LB)   |    |    |  |  +-----------------+ |  |  |
|  |  +--------+--------+    |    |  |  |  YAWL Engine    | |  |  |
|  |           |             |    |  |  +-----------------+ |  |  |
|  +-----------|-------------+    |  |  |  Resource Svc   | |  |  |
|              |                  |  |  +-----------------+ |  |  |
|              +----------------->|  |  |  Worklet Svc    | |  |  |
|                                 |  |  +-----------------+ |  |  |
|                                 |  +-----------------------+  |  |
|                                 |                             |  |
|                                 +-----------------------------+  |
|                                                                   |
|  +-------------------------+    +-----------------------------+  |
|  |   Data Subnet           |    |   Cache Subnet              |  |
|  |                         |    |                             |  |
|  |  +-----------------+    |    |  +-----------------------+  |  |
|  |  |  PostgreSQL     |    |    |  |  Redis Cluster        |  |  |
|  |  |  (Managed)      |    |    |  |  (Managed)            |  |  |
|  |  +-----------------+    |    |  +-----------------------+  |  |
|  +-------------------------+    +-----------------------------+  |
|                                                                   |
+------------------------------------------------------------------+
```

### 5.2 Network Security Groups

| Security Group | Inbound | Outbound | Purpose |
|----------------|---------|----------|---------|
| `sg-lb` | 80, 443 (0.0.0.0/0) | 8080 (sg-app) | Load balancer |
| `sg-app` | 8080 (sg-lb) | 5432 (sg-db), 6379 (sg-cache) | Application pods |
| `sg-db` | 5432 (sg-app) | None | Database |
| `sg-cache` | 6379, 26379 (sg-app) | None | Redis cluster |

### 5.3 Service Mesh (Optional)

For enhanced observability and security, Istio service mesh can be deployed:

```yaml
# Istio VirtualService for Engine
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: yawl-engine
spec:
  hosts:
  - yawl-engine
  http:
  - route:
    - destination:
        host: yawl-engine
        subset: v5-2
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: yawl-engine
spec:
  host: yawl-engine
  subsets:
  - name: v5-2
    labels:
      version: v5.2.0
```

---

## 6. Security Architecture

### 6.1 Authentication Flow

```
+----------+     +----------+     +----------+     +----------+
|  Client  |---->|  OAuth   |---->|  YAWL    |---->| Database |
|          |     |  Provider|     |  Engine  |     |          |
+----------+     +----------+     +----------+     +----------+
     |                |                 |                |
     | 1. Request     |                 |                |
     |--------------->|                 |                |
     |                |                 |                |
     | 2. Token       |                 |                |
     |<---------------|                 |                |
     |                |                 |                |
     | 3. API Request + Token           |                |
     |--------------------------------->|                |
     |                |                 |                |
     |                | 4. Validate     |                |
     |                |<----------------|                |
     |                |                 |                |
     |                | 5. Valid        |                |
     |                |---------------->|                |
     |                |                 |                |
     |                |                 | 6. Query       |
     |                |                 |--------------->|
     |                |                 |                |
     |                |                 | 7. Results     |
     |                |                 |<---------------|
     |                |                 |                |
     | 8. Response    |                 |                |
     |<---------------------------------|                |
```

### 6.2 Secrets Management

| Secret Type | AWS | Azure | GCP | Oracle | IBM |
|-------------|-----|-------|-----|--------|-----|
| DB Credentials | Secrets Manager | Key Vault | Secret Manager | Vault | Secrets Manager |
| API Keys | Secrets Manager | Key Vault | Secret Manager | Vault | Secrets Manager |
| TLS Certificates | ACM | Key Vault | Certificate Manager | Certificates | Certificate Manager |
| Service Account | IAM Roles | Managed Identity | Service Account | Dynamic Group | IAM |

### 6.3 Encryption

| Layer | Encryption | Key Management |
|-------|------------|----------------|
| In Transit | TLS 1.3 | Cloud-managed or BYOK |
| At Rest (DB) | AES-256 | Cloud KMS + CMEK |
| At Rest (Storage) | AES-256 | Cloud KMS + CMEK |
| Backups | AES-256 | Cloud KMS + CMEK |

---

## 7. High Availability Architecture

### 7.1 Availability Zones

```
                Region
+------------------------------------------+
|                                          |
|  +----------------+  +----------------+  |
|  |  AZ-1          |  |  AZ-2          |  |
|  |                |  |                |  |
|  | +------------+ |  | +------------+ |  |
|  | | Engine Pod | |  | | Engine Pod | |  |
|  | +------------+ |  | +------------+ |  |
|  |                |  |                |  |
|  | +------------+ |  | +------------+ |  |
|  | | Resource   | |  | | Resource   | |  |
|  | | Service    | |  | | Service    | |  |
|  | +------------+ |  | +------------+ |  |
|  |                |  |                |  |
|  | +------------+ |  | +------------+ |  |
|  | | Redis Node | |  | | Redis Node | |  |
|  | +------------+ |  | +------------+ |  |
|  +----------------+  +----------------+  |
|                                          |
|  +----------------+  +----------------+  |
|  |  AZ-3 (DB)     |  |  AZ-4 (DB)     |  |
|  |                |  |                |  |
|  | +------------+ |  | +------------+ |  |
|  | | PostgreSQL | |  | | PostgreSQL | |  |
|  | | Primary    | |  | | Standby    | |  |
|  | +------------+ |  | +------------+ |  |
|  +----------------+  +----------------+  |
|                                          |
+------------------------------------------+
```

### 7.2 Failure Scenarios and Recovery

| Failure | Detection | Recovery | RTO | RPO |
|---------|-----------|----------|-----|-----|
| Pod Crash | Liveness probe | Pod restart | 30s | 0 |
| Node Failure | Node controller | Pod reschedule | 2m | 0 |
| AZ Failure | Health checks | Cross-AZ failover | 5m | 0 |
| Region Failure | Global LB | Cross-region failover | 30m | 5m |
| DB Failure | DB health checks | Replica promotion | 2m | 0 |

### 7.3 Pod Disruption Budget

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: yawl-engine-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: yawl-engine
```

---

## 8. Observability Architecture

### 8.1 Logging Stack

```
+----------------+     +----------------+     +----------------+
|  YAWL Engine   |     |  Resource Svc  |     |  Worklet Svc   |
|  (Log4J2)      |     |  (Log4J2)      |     |  (Log4J2)      |
+-------+--------+     +-------+--------+     +-------+--------+
        |                      |                      |
        +----------------------+----------------------+
                               |
                      +--------v--------+
                      |  Fluentd/       |
                      |  Fluent Bit     |
                      |  (DaemonSet)    |
                      +--------+--------+
                               |
              +----------------+----------------+
              |                |                |
      +-------v-------+ +------v-------+ +------v-------+
      |  Cloud Logs   | |  Elasticsearch| |  S3/Blob     |
      |  (Native)     | |  (Self-hosted)| |  (Archive)   |
      +---------------+ +--------------+ +--------------+
```

### 8.2 Metrics Stack

| Metric Type | Source | Collection | Visualization |
|-------------|--------|------------|---------------|
| Infrastructure | cAdvisor | Prometheus | Grafana/Cloud |
| JVM Metrics | JMX Exporter | Prometheus | Grafana/Cloud |
| Business Metrics | YAWL Events | Prometheus | Grafana |
| Custom Metrics | Application | Prometheus | Grafana |

### 8.3 Key Metrics

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `yawl_cases_active` | Active workflow cases | > 10000 |
| `yawl_workitems_pending` | Pending work items | > 5000 |
| `yawl_case_duration_p99` | 99th percentile case duration | > 3600s |
| `yawl_engine_heap_used` | JVM heap usage | > 80% |
| `yawl_db_connections_active` | Active DB connections | > 80% of pool |
| `yawl_redis_hit_rate` | Redis cache hit rate | < 80% |

### 8.4 Distributed Tracing

```yaml
# OpenTelemetry configuration
receivers:
  otlp:
    protocols:
      grpc:
      http:

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024

exporters:
  # Cloud-specific exporters
  awsxray:
  azuremonitor:
  googlecloud:

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [awsxray]  # or azuremonitor, googlecloud
```

---

## 9. CI/CD Architecture

### 9.1 Pipeline Stages

```
+-------------+    +-------------+    +-------------+    +-------------+
|   Source    |--->|   Build     |--->|   Test      |--->|   Package   |
|   (Git)     |    |   (Maven)   |    |   (JUnit)   |    |   (Docker)  |
+-------------+    +-------------+    +-------------+    +-------------+
                                                                |
                                                                v
+-------------+    +-------------+    +-------------+    +-------------+
|   Prod      |<---|   Stage     |<---|   Dev       |<---|   Security  |
|   Deploy    |    |   Deploy    |    |   Deploy    |    |   Scan      |
+-------------+    +-------------+    +-------------+    +-------------+
```

### 9.2 GitOps Deployment

```yaml
# ArgoCD Application
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: yawl-engine
spec:
  project: default
  source:
    repoURL: https://github.com/yawl/yawl-k8s
    targetRevision: main
    path: charts/yawl-engine
    helm:
      valueFiles:
      - values-prod.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: yawl
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

---

## 10. Multi-Cloud Consistency

### 10.1 Abstraction Layer

To ensure consistent behavior across clouds:

```yaml
# Cloud-agnostic configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-cloud-config
data:
  # These values are injected by cloud-specific overlays
  DATABASE_HOST: "${DATABASE_HOST}"
  DATABASE_PORT: "5432"
  DATABASE_NAME: "yawl"
  REDIS_HOST: "${REDIS_HOST}"
  REDIS_PORT: "6379"
  LOG_LEVEL: "INFO"
```

### 10.2 Cloud-Specific Overlays

| Cloud | Overlay Files | Customizations |
|-------|---------------|----------------|
| AWS | `kustomize/overlays/aws/` | IAM roles, ALB, RDS, ElastiCache |
| Azure | `kustomize/overlays/azure/` | Managed Identity, AGW, Azure DB, Redis |
| GCP | `kustomize/overlays/gcp/` | Workload Identity, GCLB, Cloud SQL, Memorystore |
| Oracle | `kustomize/overlays/oracle/` | Dynamic Groups, LB, ATP, Redis |
| IBM | `kustomize/overlays/ibm/` | IAM, ALB, Databases for PostgreSQL, Redis |

---

## 11. Cost Optimization

### 11.1 Resource Right-Sizing

| Environment | Engine Replicas | DB Instance | Redis | Est. Monthly Cost |
|-------------|-----------------|-------------|-------|-------------------|
| Development | 1 | db.t3.medium | cache.t3.micro | $200 |
| Staging | 2 | db.r6g.large | cache.r6g.large | $800 |
| Production | 3+ | db.r6g.xlarge | cache.r6g.xlarge | $2,500+ |

### 11.2 Cost Controls

- **Spot/Preemptible Instances**: For stateless services (MonitorService, Balancer)
- **Reserved Instances**: For steady-state workloads (Engine, ResourceService)
- **Auto-scaling**: Scale down during low-traffic periods
- **Storage Tiering**: Move old logs/archives to cold storage

---

## 12. Reference Architecture Diagrams

### 12.1 Minimal Production Deployment

```
+------------------------------------------------------------------+
|                           Production                              |
+------------------------------------------------------------------+
|                                                                   |
|  Internet ---> [ Cloud Load Balancer (TLS) ]                      |
|                          |                                        |
|            +-------------+-------------+                          |
|            |                           |                          |
|    +-------v-------+           +-------v-------+                  |
|    |  Engine Pod   |           |  Engine Pod   |                  |
|    |  (AZ-1)       |           |  (AZ-2)       |                  |
|    +-------+-------+           +-------+-------+                  |
|            |                           |                          |
|            +-------------+-------------+                          |
|                          |                                        |
|            +-------------v-------------+                          |
|            |    PostgreSQL (HA)       |                          |
|            |    Primary    Standby    |                          |
|            +-------------+-------------+                          |
|                          |                                        |
|            +-------------v-------------+                          |
|            |    Redis (Sentinel)      |                          |
|            |    Master    Replica     |                          |
|            +---------------------------+                          |
|                                                                   |
+------------------------------------------------------------------+
```

### 12.2 Enterprise Multi-Region Deployment

```
+------------------------------------------------------------------+
|                        Global Load Balancer                        |
+------------------------------------------------------------------+
|                                   |                               |
|          +------------------------+------------------------+      |
|          |                                                 |      |
|  +-------v--------------------------------+   +-----------v----+-+
|  |            Region: US-East              |   | Region: EU-West |
|  |                                         |   |                 |
|  |  [ LB ] --> [ Engine x3 ] --> [ DB HA ] |   | [ Same setup ]  |
|  |                    |                    |   |                 |
|  |            [ Redis Cluster ]            |   |                 |
|  |                                         |   |                 |
|  +-----------------------------------------+   +-----------------+
|                                                                   |
+------------------------------------------------------------------+
```

---

## 13. Next Steps

1. Review [Prerequisites](prerequisites.md) for environment requirements
2. Follow the [Deployment Guide](deployment-guide.md) for step-by-step instructions
3. Configure [Security](../security/security-overview.md) for production
4. Set up [Operations](../operations/scaling-guide.md) for ongoing management
