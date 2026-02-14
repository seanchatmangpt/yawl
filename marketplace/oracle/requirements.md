# Oracle Cloud Infrastructure (OCI) Marketplace Requirements

## YAWL Workflow Engine - OCI Marketplace Listing

**Version:** 5.2
**Listing Type:** OCI Marketplace Partner Image
**Target Market:** Enterprise BPM/Workflow Automation

---

## 1. Executive Summary

YAWL (Yet Another Workflow Language) is a BPM/Workflow engine with formal foundations based on Petri Nets and Workflow Patterns. This document outlines requirements for publishing YAWL on Oracle Cloud Infrastructure Marketplace.

### Key Value Propositions for OCI Customers

- **Enterprise-grade workflow engine** with 36+ workflow patterns
- **Formal verification** capabilities through Petri Net foundations
- **Multi-service architecture** supporting distributed deployments
- **Oracle integration** with Autonomous Database, OKE, and Object Storage

---

## 2. Technical Requirements

### 2.1 Supported OCI Services

| Service | Integration Type | Priority |
|---------|-----------------|----------|
| Oracle Kubernetes Engine (OKE) | Primary deployment | P0 |
| Autonomous Database (ADB) | Persistence layer | P0 |
| Object Storage | Document/attachment storage | P1 |
| Oracle Cloud Infrastructure Registry (OCIR) | Container registry | P0 |
| Oracle Identity Cloud Service (IDCS) | Authentication | P1 |
| Oracle Monitoring | Observability | P2 |
| Oracle Logging | Centralized logging | P2 |

### 2.2 Container Requirements

```yaml
# Container specifications
base_image: ghcr.io/oracle/oraclelinux:8
java_runtime: OpenJDK 17 or Oracle JDK 17
memory_minimum: 2Gi
memory_recommended: 4Gi
cpu_minimum: 2
cpu_recommended: 4
storage_minimum: 10Gi
```

### 2.3 Database Requirements

#### Autonomous Database (ADB) Support

- **ADB-S (Shared)**: Development and testing workloads
- **ADB-D (Dedicated)**: Production enterprise workloads

```sql
-- Required schema objects
-- Hibernate DDL auto-generation supported
-- Connection via Oracle Wallet or mTLS
```

**Connection Configuration:**
```properties
# Hibernate configuration for OCI Autonomous Database
hibernate.dialect=org.hibernate.dialect.OracleDialect
hibernate.connection.driver_class=oracle.jdbc.OracleDriver
hibernate.connection.url=jdbc:oracle:thin:@${ADB_TNS_NAME}?TNS_ADMIN=${WALLET_PATH}
hibernate.hbm2ddl.auto=update
hibernate.connection.pool_size=20
```

### 2.4 OKE Deployment Architecture

```yaml
# Kubernetes deployment structure
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  labels:
    app: yawl-engine
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
  template:
    spec:
      containers:
      - name: yawl-engine
        image: ${OCIR_REGION}/${TENANCY}/yawl-engine:${VERSION}
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        env:
        - name: DB_TYPE
          value: "oracle"
        - name: DB_CONNECT_STRING
          valueFrom:
            secretKeyRef:
              name: yawl-db-secret
              key: connect-string
        volumeMounts:
        - name: wallet
          mountPath: /wallet
          readOnly: true
      volumes:
      - name: wallet
        secret:
          secretName: adb-wallet
          optional: false
```

### 2.5 Object Storage Integration

```java
// OCI Object Storage configuration for YAWL attachments
public class OCIObjectStorageConfig {
    private String compartmentId;
    private String bucketNamespace;
    private String bucketName;
    private String region;

    // Pre-authenticated request support for secure document access
    private boolean enableParRequests;
    private Duration parExpirationDuration;
}
```

---

## 3. OCI Marketplace Listing Requirements

### 3.1 Listing Information

| Field | Requirement |
|-------|-------------|
| **Listing Name** | YAWL Workflow Engine |
| **Short Description** | Enterprise workflow automation with formal verification |
| **Long Description** | (500-2000 characters, see listing-template.json) |
| **Categories** | Business Applications > BPM, Developer Tools |
| **Supported Regions** | All OCI commercial regions |
| **Pricing Model** | Bring Your Own License (BYOL) / Hourly |
| **Version** | 5.2.x |

### 3.2 Pricing Models

#### Option 1: BYOL (Bring Your Own License)
- Customer provides YAWL license
- OCI charges only for infrastructure
- Target: Existing YAWL customers migrating to OCI

#### Option 2: Hourly Metered
- Hourly rate per OCPU
- Includes YAWL licensing
- Target: New customers

### 3.3 Required Documentation

1. **Product Overview** (PDF, max 10 pages)
2. **Deployment Guide** (OCI Markdown)
3. **Security Whitepaper** (PDF)
4. **Architecture Diagram** (PNG/SVG, OCI architecture icons)
5. **Release Notes** (Markdown)

---

## 4. Security Requirements

### 4.1 OCI Security Best Practices

- [ ] OCI CIS Benchmark compliance
- [ ] Least-privilege IAM policies
- [ ] Encryption at rest (Oracle-managed or customer-managed keys)
- [ ] Encryption in transit (TLS 1.2+)
- [ ] Network security (VCN, subnets, security lists)
- [ ] Vulnerability scanning (OCI Vulnerability Scanning Service)
- [ ] Audit logging enabled

### 4.2 Identity and Access Management

```hcl
# Terraform IAM policy example
resource "oci_identity_policy" "yawl_policy" {
  compartment_id = var.compartment_id
  name           = "yawl-engine-policy"
  description    = "IAM policy for YAWL workflow engine"

  statements = [
    "Allow service blockstorage, compute, objectstorage-ingestion to read vaults in compartment id ${var.compartment_id}",
    "Allow dynamic-group yawl-engine-dg to read buckets in compartment id ${var.compartment_id}",
    "Allow dynamic-group yawl-engine-dg to manage objects in compartment id ${var.compartment_id} where target.bucket.name='${var.bucket_name}'"
  ]
}
```

### 4.3 Network Security

```
# VCN Security List Rules
Ingress:
  - Port 8080 (HTTP) - From load balancer subnet only
  - Port 8443 (HTTPS) - From load balancer subnet only
  - Port 9090 (Management) - From management subnet only

Egress:
  - Port 443 (HTTPS) - To OCI services
  - Port 1521 (Oracle DB) - To Autonomous Database
  - Port 5432 (PostgreSQL) - Alternative database option
```

---

## 5. Integration Requirements

### 5.1 Oracle Identity Cloud Service (IDCS)

```xml
<!-- IDCS integration for YAWL authentication -->
<security>
    <oauth2>
        <issuer>https://idcs-${IDCS_TENANT}.identity.oraclecloud.com</issuer>
        <jwk-uri>https://idcs-${IDCS_TENANT}.identity.oraclecloud.com/admin/v1/SigningCert/jwk</jwk-uri>
        <audience>yawl-engine</audience>
    </oauth2>
</security>
```

### 5.2 Oracle Monitoring Integration

```yaml
# OCI Monitoring metrics
metrics:
  - name: yawl.case.started
    namespace: yawl
    dimensions: [specification_id, process_name]
  - name: yawl.case.completed
    namespace: yawl
    dimensions: [specification_id, process_name]
  - name: yawl.workitem.duration
    namespace: yawl
    dimensions: [task_name, resource_id]
  - name: yawl.engine.active_cases
    namespace: yawl
    dimensions: [instance_id]
```

### 5.3 Oracle Logging Integration

```xml
<!-- Log4j2 OCI Logging configuration -->
<Configuration>
    <Appenders>
        <OciLogging name="OciLogging"
            logId="${env:OCI_LOG_ID}"
            compartmentId="${env:OCI_COMPARTMENT_ID}"/>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="OciLogging"/>
        </Root>
    </Loggers>
</Configuration>
```

---

## 6. Helm Chart Requirements

### 6.1 values.yaml Schema

```yaml
# YAWL Helm Chart values.yaml
replicaCount: 3

image:
  repository: ${OCIR_REGION}/${TENANCY}/yawl-engine
  tag: "5.2.0"
  pullPolicy: IfNotPresent

service:
  type: LoadBalancer
  port: 443
  targetPort: 8443

resources:
  requests:
    cpu: 1000m
    memory: 2Gi
  limits:
    cpu: 2000m
    memory: 4Gi

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

database:
  type: oracle
  autonomous:
    enabled: true
    walletSecret: adb-wallet
    tnsAlias: yawl_high

objectStorage:
  enabled: true
  compartmentId: ""
  bucketName: yawl-documents

idcs:
  enabled: false
  tenantId: ""
  clientId: ""
  clientSecret: ""

ingress:
  enabled: true
  className: nginx
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  hosts:
    - host: yawl.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: yawl-tls
      hosts:
        - yawl.example.com
```

---

## 7. Validation Checklist

### Pre-Submission

- [ ] Docker image builds successfully with Oracle Linux base
- [ ] Helm chart installs on OKE without errors
- [ ] Autonomous Database connection verified
- [ ] Object Storage integration tested
- [ ] IDCS authentication working
- [ ] All documentation complete and reviewed
- [ ] Security scan passed (no critical/high vulnerabilities)
- [ ] Architecture diagram created with OCI icons

### OCI Partner Requirements

- [ ] OPN (Oracle PartnerNetwork) membership active
- [ ] Marketplace agreement signed
- [ ] Tax documentation submitted
- [ ] Support SLA defined

---

## 8. Support Requirements

### 8.1 Support Tiers

| Tier | Response Time | Description |
|------|---------------|-------------|
| Critical | 1 hour | Production down |
| High | 4 hours | Major feature unavailable |
| Medium | 1 business day | Feature degraded |
| Low | 3 business days | General inquiry |

### 8.2 Support Channels

- Oracle Support integration for infrastructure issues
- YAWL community forums for application-level support
- Direct support for enterprise customers

---

## 9. Timeline

| Phase | Duration | Milestones |
|-------|----------|------------|
| Technical Integration | 4 weeks | OKE, ADB, Object Storage |
| Security Hardening | 2 weeks | CIS compliance, vulnerability scan |
| Documentation | 2 weeks | All marketplace docs |
| Testing | 2 weeks | UAT, performance, security |
| Marketplace Submission | 2 weeks | Oracle review |
| **Total** | **12 weeks** | |

---

## 10. Appendix

### A. Related OCI Documentation

- [OCI Container Engine for Kubernetes](https://docs.oracle.com/en-us/iaas/Content/ContEng/home.htm)
- [Autonomous Database Documentation](https://docs.oracle.com/en-us/iaas/Content/Database/Concepts/adboverview.htm)
- [OCI Marketplace Publisher Guide](https://docs.oracle.com/en-us/iaas/Content/Marketplace/Concepts/marketoverview.htm)

### B. YAWL Resources

- YAWL Foundation: https://yawlfoundation.org
- Source Repository: https://github.com/yawlfoundation/yawl
- Documentation: https://yawlfoundation.github.io/

### C. Contacts

- OCI Marketplace Team: cloudmarketplace_ww@oracle.com
- YAWL Technical: support@yawlfoundation.org
