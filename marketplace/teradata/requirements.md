# Teradata Vantage Cloud/Multi-Cloud Marketplace Requirements

## Executive Summary

This document outlines the technical and business requirements for integrating YAWL workflow engine with Teradata Vantage for cloud and multi-cloud marketplace deployments. Teradata Vantage provides enterprise-grade analytics, data warehousing, and AI/ML capabilities across AWS, Azure, and GCP.

## Table of Contents

1. [Deployment Options](#deployment-options)
2. [Technical Requirements](#technical-requirements)
3. [Business Requirements](#business-requirements)
4. [Multi-Cloud Architecture](#multi-cloud-architecture)
5. [Connectivity Requirements](#connectivity-requirements)
6. [Performance Requirements](#performance-requirements)
7. [Compliance and Certifications](#compliance-and-certifications)

---

## Deployment Options

### 1. VantageCloud Lake (Cloud-Native)

| Feature | Description |
|---------|-------------|
| Architecture | Cloud-native, object storage-based |
| Compute | Elastic, on-demand compute clusters |
| Storage | Performance-optimized, low-cost object storage |
| Scaling | Automatic and dynamic compute scaling |
| Best For | Data science, ML workloads, ad-hoc analytics |

**Pricing**: From $4.80/hour (Standard), $6.00/hour (Lake), $7.20/hour (Lake+)

### 2. VantageCloud Enterprise

| Feature | Description |
|---------|-------------|
| Architecture | Traditional enterprise data warehouse |
| Compute | Fixed capacity with flexible scaling |
| Storage | Block and object storage options |
| Management | Fully managed by Teradata |
| Best For | Mission-critical production workloads |

**Pricing**: Starting at $9,000/month (Enterprise), $10,500/month (Enterprise+)

### 3. Cloud Service Provider Availability

| Provider | Lake | Enterprise | Marketplace |
|----------|------|------------|-------------|
| AWS | Yes | Yes | AWS Marketplace |
| Azure | Yes | Yes | Azure Marketplace |
| GCP | Yes | Yes | Google Cloud Marketplace |

---

## Technical Requirements

### Minimum System Requirements

#### Compute Resources

```yaml
# VantageCloud Lake Minimum
primary_cluster:
  instance_type: "TD_COMPUTE_SMALL"
  min_instances: 2
  max_instances: 8

compute_cluster:
  instance_type: "TD_COMPUTE_SMALL"
  min_instances: 1
  max_instances: 4
  scaling_policy: "STANDARD"

# VantageCloud Enterprise Minimum
system:
  tcore_hours: 25000  # Minimum per VM
  vm_count: 2
  storage_tb: 2
```

#### Storage Configuration

```yaml
storage:
  block_storage:
    type: "managed"
    tier: "premium"
    encryption: "AES-256"
    minimum_tb: 1

  object_storage:
    type: "native"
    tier: "performance-optimized"
    encryption: "server-side"
    minimum_tb: 1
```

### Network Requirements

#### Port Configuration

| Port | Protocol | Purpose |
|------|----------|---------|
| 1025 | TCP | Teradata JDBC/SQL |
| 443 | HTTPS | REST API / Console |
| 22 | SSH | Admin access (restricted) |
| 9047 | TCP | Teradata Studio |

#### VPC/Network Configuration

```yaml
network:
  vpc:
    cidr: "10.0.0.0/16"
    subnets:
      - name: "primary"
        cidr: "10.0.1.0/24"
        az: "us-east-1a"
      - name: "secondary"
        cidr: "10.0.2.0/24"
        az: "us-east-1b"

  security_groups:
    - name: "teradata-ingress"
      rules:
        - port: 1025
          source: "10.0.0.0/8"
          description: "JDBC access from internal"
        - port: 443
          source: "0.0.0.0/0"
          description: "HTTPS console access"
```

### Database Connector Requirements

#### JDBC Driver Configuration

```xml
<!-- Maven dependency for Teradata JDBC -->
<dependency>
    <groupId>com.teradata.jdbc</groupId>
    <artifactId>terajdbc</artifactId>
    <version>20.00.00.10</version>
</dependency>
```

#### Connection String Format

```
jdbc:teradata://[host]/DATABASE=[database],USER=[username],PASSWORD=[password]
```

#### Advanced Connection Properties

```yaml
jdbc_properties:
  # Connection pooling
  LOGIN_TIMEOUT: "60"
  RESPONSE_BUFFER_SIZE: "65536"

  # SSL/TLS
  ENCRYPTDATA: "true"
  SSLMODE: "require"

  # Performance
  TYPE: "FASTEXPORT"
  CHARSET: "UTF8"

  # High Availability
  COP: "OFF"
  TMODE: "TERA"
```

---

## Business Requirements

### Pricing Models

#### 1. Consumption-Based Pricing

| Component | Price (US Region) | Unit |
|-----------|------------------|------|
| Base Compute | $183.00 | per 100 TCore Hours |
| Advanced Compute | $269.00 | per 100 TCore Hours |
| Enterprise Compute | $326.00 | per 100 TCore Hours |
| Block Storage | $1,700.00 | per TiB/year |
| Object Storage | $276.00 | per TiB/year |
| Enterprise Edition Units | $210.00 | per 100 units |

#### 2. Commitment Pricing

| Commitment Term | Discount |
|-----------------|----------|
| 1 Year | 15% |
| 3 Year | 30% |

### Support Tiers

| Tier | Response Time (P1) | Features |
|------|-------------------|----------|
| Premier Cloud (Included) | 1 hour | 24x7 support, community forums |
| Priority Service (+$1,500/mo) | 30 minutes | Enhanced coverage, priority handling |
| Direct Quick Start (One-time) | N/A | Implementation assistance |

### SLA Requirements

| Metric | Target |
|--------|--------|
| Uptime SLA | 99.95% |
| Data Durability | 99.999999999% (11 9s) |
| RPO (Recovery Point Objective) | < 1 hour |
| RTO (Recovery Time Objective) | < 4 hours |

---

## Multi-Cloud Architecture

### QueryGrid for Data Federation

QueryGrid enables seamless data connectivity across hybrid and multi-cloud environments.

#### Supported Connectors

| Connector | Source | Target |
|-----------|--------|--------|
| Teradata-to-Teradata | Vantage | Vantage |
| Teradata-to-Spark | Vantage | Spark/Hadoop |
| Teradata-to-Hive | Vantage | Hive |
| Teradata-to-Presto | Vantage | Presto/Trino |
| Arrow Flight SQL | Vantage | Any Arrow-compatible |

#### Configuration Example

```yaml
querygrid:
  version: "3.0"
  links:
    - name: "aws-to-azure"
      source:
        system: "vantagecloud-lake-aws"
        database: "production"
      target:
        system: "vantagecloud-enterprise-azure"
        database: "analytics"
      properties:
        pushdown: "full"
        authentication: "kerberos"
```

### Multi-Cloud Data Mesh

```yaml
data_mesh:
  domains:
    - name: "order-management"
      owner: "operations"
      primary_system: "vantagecloud-aws"
      replication:
        - target: "vantagecloud-azure"
          mode: "async"
          latency_sla: "5m"

    - name: "customer-analytics"
      owner: "marketing"
      primary_system: "vantagecloud-azure"
      replication:
        - target: "vantagecloud-gcp"
          mode: "async"
          latency_sla: "15m"
```

---

## Connectivity Requirements

### YAWL-Teradata Integration Patterns

#### Pattern 1: Direct JDBC Integration

```yaml
integration:
  type: "direct_jdbc"
  yawl_engine:
    connection_pool:
      max_connections: 20
      min_connections: 5
      connection_timeout_ms: 30000

  query_patterns:
    - name: "workflow_metrics"
      schedule: "*/5 * * * *"
      query: |
        SELECT
          workflow_id,
          task_id,
          start_time,
          end_time,
          status
        FROM yawl.workflow_executions
        WHERE processing_date = CURRENT_DATE
```

#### Pattern 2: REST API Integration

```yaml
integration:
  type: "rest_api"
  endpoint: "https://[host]:443/api/query"
  authentication:
    type: "oauth2"
    token_endpoint: "https://[host]/oauth/token"
    client_id: "${TERADATA_CLIENT_ID}"
    client_secret: "${TERADATA_CLIENT_SECRET}"
```

#### Pattern 3: QueryGrid Federation

```yaml
integration:
  type: "querygrid"
  link: "yawl-to-vantage"
  pushdown: true
  optimization:
    predicate_pushdown: true
    projection_pushdown: true
```

---

## Performance Requirements

### Query Optimization for YAWL Workflow Data

#### Table Design Best Practices

```sql
-- Partitioned fact table for workflow executions
CREATE MULTISET TABLE yawl.workflow_executions,
    FALLBACK,
    NO BEFORE JOURNAL,
    NO AFTER JOURNAL,
    CHECKSUM = DEFAULT,
    DEFAULT MERGEBLOCKRATIO,
    MAP = TD_MAP1
(
    workflow_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36),
    specification_id VARCHAR(100),
    start_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP(6) WITH TIME ZONE,
    status VARCHAR(20),
    error_message CLOB,
    processing_date DATE FORMAT 'YYYY-MM-DD' NOT NULL
)
PRIMARY INDEX (workflow_id, task_id)
PARTITION BY RANGE_N(
    processing_date BETWEEN DATE '2024-01-01' AND DATE '2025-12-31'
    EACH INTERVAL '1' DAY
)
INDEX (case_id) WITH LOAD IDENTITY
INDEX (start_time) WITH LOAD IDENTITY;
```

#### Statistics Collection

```sql
-- Collect statistics for query optimization
COLLECT STATISTICS ON yawl.workflow_executions
    INDEX (workflow_id, task_id),
    COLUMN (case_id),
    COLUMN (processing_date),
    COLUMN (status)
    USING SAMPLE 100 PERCENT;
```

### Performance Benchmarks

| Operation | Target Latency | Throughput |
|-----------|---------------|------------|
| Simple SELECT (indexed) | < 10ms | 10,000 TPS |
| Complex JOIN (3+ tables) | < 500ms | 1,000 TPS |
| Bulk Load (FastLoad) | N/A | 1M rows/sec |
| Bulk Export (FastExport) | N/A | 2M rows/sec |

---

## Compliance and Certifications

### Security Certifications

| Certification | Status | Scope |
|---------------|--------|-------|
| SOC 1 Type II | Certified | Financial reporting controls |
| SOC 2 Type II | Certified | Security, availability, confidentiality |
| ISO 27001 | Certified | Information security management |
| PCI DSS | Certified | Payment card data |
| HIPAA | Compliant | Healthcare data |
| GDPR | Compliant | EU data protection |
| CCPA | Compliant | California privacy |

### Security Controls

```yaml
security:
  encryption:
    at_rest:
      algorithm: "AES-256"
      key_management: "customer-managed or Teradata-managed"
    in_transit:
      protocol: "TLS 1.2+"
      certificate: "SHA-256"

  access_control:
    authentication:
      methods:
        - "username/password"
        - "LDAP/Active Directory"
        - "SAML 2.0 SSO"
        - "OAuth 2.0"
    authorization:
      model: "RBAC"
      granularity: "column-level"

  audit:
    logging: "enabled"
    retention_days: 90
    events:
      - "login_success"
      - "login_failure"
      - "data_access"
      - "schema_changes"
      - "admin_operations"
```

### Data Residency Requirements

```yaml
data_residency:
  regions:
    - name: "US"
      locations:
        - "us-east-1"
        - "us-west-2"
      data_classification:
        - "public"
        - "internal"
        - "confidential"

    - name: "EU"
      locations:
        - "eu-west-1"
        - "eu-central-1"
      data_classification:
        - "public"
        - "internal"
        - "confidential"
        - "pii"

  cross_region_transfer:
    allowed: false
    exceptions:
      - "anonymized_data"
      - "aggregate_statistics"
```

---

## Appendix A: YAWL Schema Definitions

### Workflow Schema

```sql
CREATE DATABASE yawl
    AS PERMANENT = 60e9, -- 60GB
       SPOOL = 120e9;    -- 120GB

-- Workflow definitions
CREATE MULTISET TABLE yawl.workflow_specifications (
    specification_id VARCHAR(100) NOT NULL,
    specification_xml CLOB,
    version VARCHAR(20),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (specification_id)
);

-- Case management
CREATE MULTISET TABLE yawl.cases (
    case_id VARCHAR(36) NOT NULL,
    specification_id VARCHAR(100) NOT NULL,
    status VARCHAR(20),
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    data_payload JSON,
    PRIMARY KEY (case_id)
);

-- Task queue
CREATE MULTISET TABLE yawl.task_queue (
    task_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    task_name VARCHAR(100),
    assigned_to VARCHAR(100),
    status VARCHAR(20),
    priority INTEGER,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    claimed_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (task_id)
);

-- Audit log
CREATE MULTISET TABLE yawl.audit_log (
    log_id BIGINT GENERATED ALWAYS AS IDENTITY,
    case_id VARCHAR(36),
    event_type VARCHAR(50),
    event_data JSON,
    user_id VARCHAR(100),
    timestamp TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (log_id)
);
```

---

## Appendix B: Environment Variables

```bash
# Teradata Connection
export TERADATA_HOST="your-vantage-instance.teradata.com"
export TERADATA_PORT="1025"
export TERADATA_DATABASE="yawl"
export TERADATA_USER="${TERADATA_USER}"
export TERADATA_PASSWORD="${TERADATA_PASSWORD}"

# SSL Configuration
export TERADATA_SSL_MODE="require"
export TERADATA_SSL_CA="/path/to/ca-cert.pem"

# Connection Pool
export TERADATA_POOL_SIZE="10"
export TERADATA_POOL_TIMEOUT="30000"

# QueryGrid (if applicable)
export QUERYGRID_LINK="yawl-to-vantage"
export QUERYGRID_PUSHDOWN="true"
```

---

## Appendix C: Reference Links

- [Teradata Vantage Documentation](https://docs.teradata.com/)
- [Teradata JDBC Driver Guide](https://developers.teradata.com/quickstarts/create-applications/jdbc/)
- [AWS Marketplace - Teradata VantageCloud](https://aws.amazon.com/marketplace/pp/prodview-zhvzzxw2q5rqa)
- [Azure Marketplace - Teradata Vantage](https://marketplace.microsoft.com/en-us/product/saas/teradata.teradata-vantage-saas)
- [Teradata Security and Compliance](https://www.teradata.com/trust-security-center/data-compliance)
- [QueryGrid Documentation](https://docs.teradata.com/r/QueryGrid)
