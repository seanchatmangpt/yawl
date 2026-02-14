# YAWL Workflow Engine - OCI Security Requirements

## Overview

This document outlines the security requirements and controls for deploying YAWL Workflow Engine on Oracle Cloud Infrastructure (OCI) in compliance with OCI security best practices and industry standards.

---

## 1. Security Architecture

### 1.1 Defense in Depth Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                     Perimeter Security                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Edge Security                          │  │
│  │  - WAF (Web Application Firewall)                         │  │
│  │  - DDoS Protection                                        │  │
│  │  - DNS Security                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              Network Security                       │  │  │
│  │  │  - VCN with Private Subnets                        │  │  │
│  │  │  - Security Lists / NSGs                           │  │  │
│  │  │  - Bastion Host for SSH Access                     │  │  │
│  │  │  ┌─────────────────────────────────────────────┐   │  │  │
│  │  │  │           Compute Security                  │   │  │  │
│  │  │  │  - OKE with Private Workers                │   │  │  │
│  │  │  │  - Pod Security Policies                    │   │  │  │
│  │  │  │  - Runtime Security (Falco)                 │   │  │  │
│  │  │  │  ┌─────────────────────────────────────┐    │   │  │  │
│  │  │  │  │        Application Security        │    │   │  │  │
│  │  │  │  │  - Input Validation                │    │   │  │  │
│  │  │  │  │  - Authentication (IDCS/OAuth2)    │    │   │  │  │
│  │  │  │  │  - Authorization (RBAC)            │    │   │  │  │
│  │  │  │  │  - Session Management              │    │   │  │  │
│  │  │  │  │  ┌─────────────────────────────┐   │    │   │  │  │
│  │  │  │  │  │      Data Security          │   │    │   │  │  │
│  │  │  │  │  │  - Encryption at Rest       │   │    │   │  │  │
│  │  │  │  │  │  - Encryption in Transit    │   │    │   │  │  │
│  │  │  │  │  │  - Key Management (Vault)   │   │    │   │  │  │
│  │  │  │  │  │  - Data Masking             │   │    │   │  │  │
│  │  │  │  │  └─────────────────────────────┘   │    │   │  │  │
│  │  │  │  └─────────────────────────────────────┘    │   │  │  │
│  │  │  └─────────────────────────────────────────────┘   │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Identity and Access Management (IAM)

### 2.1 IAM Policy Requirements

```hcl
# YAWL Engine IAM Policy
resource "oci_identity_policy" "yawl_security_policy" {
  compartment_id = var.compartment_id
  name           = "yawl-security-policy"
  description    = "Security policy for YAWL workflow engine"

  statements = [
    # Principle of least privilege
    "Allow group yawl-admins to manage all-resources in compartment id ${var.compartment_id}",

    # Read-only for auditors
    "Allow group yawl-auditors to read all-resources in compartment id ${var.compartment_id}",

    # Developers - limited access
    "Allow group yawl-developers to read instances in compartment id ${var.compartment_id}",
    "Allow group yawl-developers to use vnics in compartment id ${var.compartment_id}",
    "Allow group yawl-developers to read buckets in compartment id ${var.compartment_id}",

    # Service principals
    "Allow service oke to manage all-resources in compartment id ${var.compartment_id}",
    "Allow dynamic-group yawl-engine-dg to manage buckets in compartment id ${var.compartment_id}",
    "Allow dynamic-group yawl-engine-dg to manage objects in compartment id ${var.compartment_id}",
    "Allow dynamic-group yawl-engine-dg to read vaults in compartment id ${var.compartment_id}",
    "Allow dynamic-group yawl-engine-dg to read keys in compartment id ${var.compartment_id}",
  ]
}
```

### 2.2 Dynamic Group Configuration

```hcl
# Dynamic group for YAWL engine pods
resource "oci_identity_dynamic_group" "yawl_engine" {
  compartment_id = var.tenancy_id
  name           = "yawl-engine-dg"
  description    = "Dynamic group for YAWL engine pods"

  matching_rule = "ALL {instance.compartment.id = '${var.compartment_id}', tag.namespace.name = 'yawl', tag.key.name = 'service', tag.value.name = 'engine'}"
}
```

### 2.3 IDCS Integration

```yaml
# IDCS OAuth2 Configuration
idcs:
  enabled: true
  tenant_id: "${IDCS_TENANT_ID}"

  oauth2:
    issuer: "https://idcs-${IDCS_TENANT}.identity.oraclecloud.com"
    authorization_endpoint: "https://idcs-${IDCS_TENANT}.identity.oraclecloud.com/oauth2/v1/authorize"
    token_endpoint: "https://idcs-${IDCS_TENANT}.identity.oraclecloud.com/oauth2/v1/token"
    userinfo_endpoint: "https://idcs-${IDCS_TENANT}.identity.oraclecloud.com/admin/v1/Me"
    jwk_set_uri: "https://idcs-${IDCS_TENANT}.identity.oraclecloud.com/admin/v1/SigningCert/jwk"

  roles:
    admin_group: "YAWL Administrators"
    user_group: "YAWL Users"
    auditor_group: "YAWL Auditors"
```

---

## 3. Network Security

### 3.1 VCN Security Configuration

```hcl
# Security List for Public Subnet (Load Balancer)
resource "oci_core_security_list" "public" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-public-security-list"

  # Ingress rules
  ingress_security_rules {
    protocol = "6"  # TCP
    source   = "0.0.0.0/0"

    tcp_options {
      min = 443
      max = 443
    }
    description = "HTTPS from internet"
  }

  ingress_security_rules {
    protocol = "6"  # TCP
    source   = "10.0.0.0/8"  # OCI internal

    tcp_options {
      min = 80
      max = 80
    }
    description = "HTTP for health checks"
  }

  # Egress rules
  egress_security_rules {
    protocol    = "6"  # TCP
    destination = "10.0.2.0/24"  # Private subnet

    tcp_options {
      min = 8080
      max = 8080
    }
    description = "To YAWL engine"
  }
}

# Security List for Private Subnet (OKE Nodes)
resource "oci_core_security_list" "private" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-private-security-list"

  # Ingress rules
  ingress_security_rules {
    protocol = "6"  # TCP
    source   = "10.0.1.0/24"  # Public subnet (LB)

    tcp_options {
      min = 8080
      max = 8080
    }
    description = "From Load Balancer"
  }

  ingress_security_rules {
    protocol = "6"  # TCP
    source   = "10.0.2.0/24"  # Private subnet (inter-pod)

    tcp_options {
      min = 1
      max = 65535
    }
    description = "Inter-pod communication"
  }

  # Egress rules - restricted
  egress_security_rules {
    protocol    = "6"  # TCP
    destination = "10.0.3.0/24"  # Database subnet

    tcp_options {
      min = 1521
      max = 1521
    }
    description = "To Autonomous Database"
  }

  egress_security_rules {
    protocol    = "6"  # TCP
    destination = "0.0.0.0/0"

    tcp_options {
      min = 443
      max = 443
    }
    description = "HTTPS egress (OCI APIs)"
  }
}
```

### 3.2 Network Security Groups (NSGs)

```hcl
# NSG for YAWL Engine
resource "oci_core_network_security_group" "yawl_engine" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-engine-nsg"
}

resource "oci_core_network_security_group_security_rule" "yawl_engine_ingress" {
  network_security_group_id = oci_core_network_security_group.yawl_engine.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = oci_core_network_security_group.lb.id
  source_type               = "NETWORK_SECURITY_GROUP"

  tcp_options {
    destination_port_range {
      min = 8080
      max = 8080
    }
  }
  description = "From Load Balancer"
}
```

### 3.3 WAF Configuration

```hcl
# OCI WAF Policy
resource "oci_waf_web_app_firewall_policy" "yawl" {
  compartment_id = var.compartment_id
  display_name   = "yawl-waf-policy"

  # Enable all OWASP Top 10 protections
  request_access_control {
    default_action_name = "ALLOW"

    access_control_rules {
      name      = "block-sql-injection"
      action    = "BLOCK"
      condition = "SQLInjectionCondition"
    }

    access_control_rules {
      name      = "block-xss"
      action    = "BLOCK"
      condition = "XSSCondition"
    }
  }

  request_protection {
    # Rate limiting
    rate_limiting {
      name            = "rate-limit-api"
      rate_in_requests = 100
      rate_in_period   = 60
      action          = "BLOCK"
    }
  }
}
```

---

## 4. Data Security

### 4.1 Encryption at Rest

```yaml
# Autonomous Database Encryption
database:
  autonomous:
    encryption:
      algorithm: AES-256
      key_management: CUSTOMER_MANAGED  # or ORACLE_MANAGED

# OCI Vault Key Configuration
vault:
  compartment_id: "${COMPARTMENT_OCID}"
  display_name: "yawl-vault"
  key:
    display_name: "yawl-encryption-key"
    key_shape:
      algorithm: AES
      length: 256
    protection_mode: HSM  # Hardware Security Module
```

### 4.2 Encryption in Transit

```yaml
# TLS Configuration
tls:
  version: "1.3"  # Minimum TLS 1.2
  protocols:
    - TLSv1.2
    - TLSv1.3
  cipher_suites:
    - TLS_AES_256_GCM_SHA384
    - TLS_CHACHA20_POLY1305_SHA256
    - TLS_AES_128_GCM_SHA256
  certificates:
    provider: lets-encrypt  # or OCI Certificate Authority
    auto_renewal: true
```

### 4.3 Secrets Management

```yaml
# OCI Vault Integration for Secrets
secrets:
  provider: oci-vault

  # Database credentials
  db_credentials:
    secret_id: "ocid1.vaultsecret.oc1..secret1"
    rotation_interval: 90d

  # API keys
  api_keys:
    secret_id: "ocid1.vaultsecret.oc1..secret2"
    rotation_interval: 30d

  # OAuth client secrets
  oauth_secrets:
    secret_id: "ocid1.vaultsecret.oc1..secret3"
    rotation_interval: 30d
```

```hcl
# Terraform: Create secrets in OCI Vault
resource "oci_vault_secret" "db_password" {
  compartment_id = var.compartment_id
  vault_id       = oci_vault.yawl.id
  key_id         = oci_vault_key.yawl.id
  secret_name    = "yawl-db-password"
  description    = "YAWL database password"

  secret_content {
    content_type = "BASE64"
    content      = base64encode(var.db_password)
  }
}
```

---

## 5. Container Security

### 5.1 Pod Security Standards

```yaml
# Pod Security Policy (Restricted)
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: yawl-restricted
spec:
  privileged: false
  runAsUser:
    rule: MustRunAsNonRoot
  seLinux:
    rule: RunAsAny
  fsGroup:
    rule: RunAsAny
  supplementalGroups:
    rule: RunAsAny
  volumes:
    - configMap
    - secret
    - emptyDir
    - projected
    - persistentVolumeClaim
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  hostNetwork: false
  hostIPC: false
  hostPID: false
```

### 5.2 Container Image Security

```yaml
# Container security context
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop:
      - ALL
  seccompProfile:
    type: RuntimeDefault
```

### 5.3 Image Scanning

```bash
# Scan images with OCI Vulnerability Scanning Service
oci vulnerability-scanning container scan result list \
  --compartment-id $COMPARTMENT_OCID \
  --repository ${REGION}.ocir.io/${TENANCY_OCID}/yawl-engine

# Required: No CRITICAL or HIGH vulnerabilities
# Threshold: 0 critical, 0 high, max 5 medium
```

---

## 6. Logging and Monitoring

### 6.1 Audit Logging Requirements

```yaml
# OCI Audit Log Configuration
audit:
  enabled: true
  retention_period: 365d

  # Events to log
  events:
    - authentication_success
    - authentication_failure
    - authorization_failure
    - privilege_escalation
    - configuration_change
    - data_access
    - data_modification
    - admin_operations

  # Sensitive operations (enhanced logging)
  sensitive_operations:
    - user_create
    - user_delete
    - role_assignment
    - permission_grant
    - specification_upload
    - specification_delete
```

### 6.2 Security Monitoring

```yaml
# OCI Monitoring Alerts
monitoring:
  namespace: "yawl/security"

  alarms:
    - name: multiple-auth-failures
      metric: auth_failures
      threshold: 5
      period: 5m
      severity: HIGH

    - name: unusual-api-activity
      metric: api_call_rate
      threshold: 1000
      period: 1m
      severity: MEDIUM

    - name: privileged-action
      metric: admin_operations
      threshold: 1
      period: 1m
      severity: INFO

    - name: data-exfiltration-attempt
      metric: data_export_volume
      threshold: 100MB
      period: 1h
      severity: CRITICAL
```

### 6.3 SIEM Integration

```yaml
# OCI Logging Analytics / SIEM Integration
logging_analytics:
  enabled: true

  sources:
    - name: yawl-engine-logs
      type: oci-log
      log_group_id: "${LOG_GROUP_OCID}"

    - name: yawl-audit-logs
      type: oci-audit

    - name: yawl-access-logs
      type: custom
      parser: COMBINED_APACHE

  rules:
    - name: detect-brute-force
      query: "Source = 'yawl' AND (Event = 'auth_failure' | stats count() by SourceIP | where count() > 5"
      action: alert

    - name: detect-sql-injection
      query: "Source = 'yawl' AND Message contains 'sql injection' OR Message matches /union.*select/i"
      action: alert
```

---

## 7. Compliance Requirements

### 7.1 OCI CIS Benchmark Compliance

| Control | Requirement | Status |
|---------|-------------|--------|
| 1.1 | Ensure IAM policies are least privilege | Required |
| 1.2 | Ensure no security lists allow 0.0.0.0/0 to port 22 | Required |
| 1.3 | Ensure no security lists allow 0.0.0.0/0 to port 3389 | Required |
| 2.1 | Ensure encryption at rest is enabled | Required |
| 2.2 | Ensure encryption in transit is enabled | Required |
| 3.1 | Ensure audit logs are enabled | Required |
| 3.2 | Ensure audit log retention is 365+ days | Required |
| 4.1 | Ensure Object Storage buckets are not public | Required |
| 4.2 | Ensure Object Storage uses HTTPS | Required |
| 5.1 | Ensure database encryption is enabled | Required |
| 5.2 | Ensure database is not publicly accessible | Required |

### 7.2 SOC 2 Type II Controls

```yaml
# SOC 2 Control Mapping
soc2_controls:
  # Security (CC6.0)
  CC6.1:
    description: "Logical and physical access controls"
    implemented_by:
      - IAM policies
      - VCN security lists
      - NSGs
      - Bastion host

  CC6.2:
    description: "System account management"
    implemented_by:
      - IDCS integration
      - RBAC
      - Service accounts

  CC6.3:
    description: "Network access controls"
    implemented_by:
      - Private subnets
      - WAF
      - Security lists

  CC6.6:
    description: "Boundary protection"
    implemented_by:
      - VCN
      - Subnets
      - Load balancer

  CC6.7:
    description: "Data transmission security"
    implemented_by:
      - TLS 1.2+
      - Certificate management

  CC6.8:
    description: "Input/output controls"
    implemented_by:
      - Input validation
      - Output encoding
      - WAF rules
```

### 7.3 GDPR Compliance

```yaml
# GDPR Data Protection Controls
gdpr:
  data_classification:
    - category: personal_data
      fields:
        - user_name
        - user_email
        - ip_address
      protection: encryption

    - category: sensitive_data
      fields:
        - health_records
        - financial_data
      protection: encryption + access_control

  data_retention:
    default_period: 90d
    audit_logs: 365d
    personal_data: 30d  # After case completion

  data_subject_rights:
    right_to_access: automated_api
    right_to_deletion: automated_api
    right_to_portability: json_export

  cross_border_transfer:
    mechanism: standard_contractual_clauses
    approved_countries:
      - EU member states
      - UK
      - US (with appropriate safeguards)
```

---

## 8. Vulnerability Management

### 8.1 Scanning Requirements

```yaml
# Vulnerability Scanning Configuration
vulnerability_scanning:
  schedule: daily

  targets:
    - type: container_images
      registry: ${REGION}.ocir.io/${TENANCY_OCID}
      repositories:
        - yawl-engine
        - yawl-resource-service
        - yawl-worklet-service

    - type: oke_cluster
      cluster_id: "${CLUSTER_OCID}"

  thresholds:
    critical: 0  # Block deployment
    high: 0      # Block deployment
    medium: 5    # Warning only
    low: 100     # Information only

  remediation_sla:
    critical: 24h
    high: 7d
    medium: 30d
    low: 90d
```

### 8.2 Patch Management

```yaml
# Patch Management Process
patching:
  security_patches:
    automation: enabled
    testing: required
    approval: security_team
    deployment_window: "Sat 02:00-06:00 UTC"

  minor_updates:
    automation: enabled
    testing: required
    approval: release_manager

  major_updates:
    automation: disabled
    testing: full_regression
    approval: change_advisory_board
```

---

## 9. Incident Response

### 9.1 Security Incident Response Plan

```yaml
# Incident Response Configuration
incident_response:
  severity_levels:
    critical:
      description: "Active breach, data exfiltration, service compromise"
      response_time: 15m
      escalation: immediate

    high:
      description: "Vulnerability exploited, unauthorized access"
      response_time: 1h
      escalation: 4h

    medium:
      description: "Security policy violation, suspicious activity"
      response_time: 4h
      escalation: 24h

    low:
      description: "Minor security event, informational"
      response_time: 24h
      escalation: 72h

  contacts:
    security_team: security@example.com
    on_call: +1-555-SEC-RITY
    oci_support: oracle_support
```

### 9.2 Forensics Requirements

```yaml
# Forensic Data Collection
forensics:
  enabled: true

  evidence_sources:
    - audit_logs
    - access_logs
    - container_logs
    - network_flows
    - memory_dumps  # On critical incidents only

  retention:
    incident_evidence: 7y
    memory_dumps: 90d

  chain_of_custody:
    hashing: SHA-256
    signing: digital_signature
    storage: immutable_bucket
```

---

## 10. Security Checklist

### Pre-Deployment

- [ ] All IAM policies follow least privilege
- [ ] Network security groups configured
- [ ] WAF enabled with OWASP rules
- [ ] TLS 1.2+ enforced everywhere
- [ ] Secrets stored in OCI Vault
- [ ] Container images scanned (no critical/high)
- [ ] Pod security policies enforced
- [ ] Audit logging enabled
- [ ] Security monitoring configured

### Post-Deployment

- [ ] Penetration testing completed
- [ ] Security baseline established
- [ ] Incident response procedures documented
- [ ] Security training completed for operators
- [ ] Compliance audit scheduled

### Ongoing

- [ ] Weekly vulnerability scans
- [ ] Monthly access reviews
- [ ] Quarterly security assessments
- [ ] Annual penetration testing
- [ ] Annual compliance audits

---

## Appendix A: Security Contact Information

| Role | Contact | Escalation |
|------|---------|------------|
| Security Team | security@yawlfoundation.org | Primary |
| CISO | ciso@yawlfoundation.org | 2nd Level |
| OCI Support | Oracle Support Portal | Infrastructure |
| Incident Response | +1-555-YAWL-SEC | 24/7 |

## Appendix B: Reference Documentation

- [OCI Security Best Practices](https://docs.oracle.com/en-us/iaas/Content/Security/Concepts/security_overview.htm)
- [OCI CIS Benchmark](https://www.cisecurity.org/benchmark/oracle_cloud)
- [OWASP Top 10](https://owasp.org/Top10/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
