# YAWL Compliance Matrix

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This document maps YAWL capabilities to common compliance frameworks and regulations.

---

## 2. Compliance Frameworks

### 2.1 SOC 2 Type II

| Control | YAWL Implementation | Status |
|---------|---------------------|--------|
| **Security** | | |
| Logical Access | RBAC, MFA, SSO | Supported |
| System Integrity | Container scanning, patches | Supported |
| Network Security | VPC, Security Groups, WAF | Supported |
| **Availability** | | |
| Monitoring | CloudWatch/Azure Monitor | Supported |
| Backup/Recovery | Automated backups, DR | Supported |
| Incident Response | Runbooks, alerts | Supported |
| **Confidentiality** | | |
| Encryption | TLS 1.2+, AES-256 | Supported |
| Data Classification | Configurable policies | Supported |
| **Processing Integrity** | | |
| Input Validation | Schema validation | Supported |
| Error Handling | Comprehensive logging | Supported |
| **Privacy** | | |
| Data Minimization | Configurable retention | Supported |
| Consent Management | Custom implementation | Supported |

### 2.2 GDPR

| Requirement | YAWL Implementation | Status |
|-------------|---------------------|--------|
| **Data Protection** | | |
| Encryption | At rest and in transit | Supported |
| Pseudonymization | Data masking | Supported |
| Data Portability | Export capabilities | Supported |
| **Rights** | | |
| Right to Access | Audit logs, exports | Supported |
| Right to Rectification | Data modification | Supported |
| Right to Erasure | Data deletion | Supported |
| **Accountability** | | |
| Records of Processing | Audit logging | Supported |
| DPIA Support | Documentation | Supported |
| DPO Contact | Configuration | Supported |

### 2.3 HIPAA

| Requirement | YAWL Implementation | Status |
|-------------|---------------------|--------|
| **Administrative** | | |
| Access Management | RBAC, MFA | Supported |
| Training | Documentation | Supported |
| Incident Response | Procedures, logging | Supported |
| **Physical** | | |
| Facility Access | Cloud provider responsibility | N/A |
| Workstation Security | Customer responsibility | N/A |
| **Technical** | | |
| Access Control | RBAC, audit logging | Supported |
| Integrity Controls | Encryption, validation | Supported |
| Transmission Security | TLS encryption | Supported |

### 2.4 PCI DSS

| Requirement | YAWL Implementation | Status |
|-------------|---------------------|--------|
| **Network Security** | | |
| Firewall | Security groups, WAF | Supported |
| Default Passwords | Forced change on install | Supported |
| **Data Protection** | | |
| Cardholder Data | Not stored by default | Configurable |
| Encryption | TLS 1.2+, AES-256 | Supported |
| **Access Control** | | |
| Need-to-Know | RBAC | Supported |
| Unique IDs | Per-user authentication | Supported |
| Physical Access | Cloud provider | N/A |
| **Monitoring** | | |
| Access Logs | Audit logging | Supported |
| Security Testing | Vulnerability scanning | Supported |
| Information Security Policy | Documentation | Supported |

---

## 3. Cloud Provider Compliance

### 3.1 AWS

| Certification | AWS Status | YAWL Compatible |
|---------------|------------|-----------------|
| SOC 1/2/3 | Yes | Yes |
| ISO 27001 | Yes | Yes |
| PCI DSS | Yes | Yes |
| HIPAA | Yes (BAA available) | Yes |
| FedRAMP | Yes (GovCloud) | Yes |
| GDPR | Yes | Yes |

### 3.2 Azure

| Certification | Azure Status | YAWL Compatible |
|---------------|--------------|-----------------|
| SOC 1/2/3 | Yes | Yes |
| ISO 27001 | Yes | Yes |
| PCI DSS | Yes | Yes |
| HIPAA | Yes (BAA available) | Yes |
| FedRAMP | Yes | Yes |
| GDPR | Yes | Yes |

### 3.3 GCP

| Certification | GCP Status | YAWL Compatible |
|---------------|------------|-----------------|
| SOC 1/2/3 | Yes | Yes |
| ISO 27001 | Yes | Yes |
| PCI DSS | Yes | Yes |
| HIPAA | Yes (BAA available) | Yes |
| FedRAMP | Yes | Yes |
| GDPR | Yes | Yes |

---

## 4. Data Residency

### 4.1 Regional Deployment Options

| Region | AWS | Azure | GCP |
|--------|-----|-------|-----|
| US East | Yes | Yes | Yes |
| US West | Yes | Yes | Yes |
| EU (Ireland) | Yes | Yes | Yes |
| EU (Frankfurt) | Yes | Yes | Yes |
| UK | Yes | Yes | Yes |
| Asia Pacific | Yes | Yes | Yes |

### 4.2 Data Residency Configuration

```yaml
# Configure data residency
dataResidency:
  region: eu-west-1
  restrictDataTransfer: true
  allowedRegions:
    - eu-west-1
    - eu-central-1
```

---

## 5. Audit and Evidence

### 5.1 Audit Trail Requirements

| Requirement | YAWL Support | Evidence |
|-------------|--------------|----------|
| User authentication events | Yes | Audit logs |
| Authorization decisions | Yes | Audit logs |
| Data access | Yes | Audit logs |
| Configuration changes | Yes | Audit logs |
| System events | Yes | System logs |

### 5.2 Log Retention

```yaml
# Log retention configuration
logRetention:
  audit: 2555d  # 7 years
  security: 90d
  application: 30d
  access: 90d
```

---

## 6. Compliance Checklist

### Pre-Deployment

- [ ] Select compliant cloud region
- [ ] Enable encryption at rest
- [ ] Configure TLS for all traffic
- [ ] Set up audit logging
- [ ] Configure RBAC
- [ ] Enable MFA for admin users
- [ ] Review and accept BAAs (if applicable)

### Ongoing

- [ ] Regular access reviews
- [ ] Vulnerability scanning
- [ ] Security patching
- [ ] Backup verification
- [ ] Incident response drills
- [ ] Compliance audits

---

## 7. Third-Party Certifications

YAWL relies on cloud provider certifications for infrastructure compliance. Application-level compliance is the customer's responsibility.

| Layer | Responsibility |
|-------|---------------|
| Physical | Cloud Provider |
| Infrastructure | Cloud Provider |
| Platform | Shared |
| Application | Customer |
| Data | Customer |

---

## 8. Contact

For compliance-related questions:
- **Email**: compliance@yawl.io
- **Documentation**: https://docs.yawl.io/compliance
- **Security**: security@yawl.io
