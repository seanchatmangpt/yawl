# Teradata Vantage Security Requirements

## Overview

This document defines the security standards and compliance requirements for Teradata Vantage deployments integrated with YAWL workflow engine. All deployments must adhere to these requirements to ensure data protection, regulatory compliance, and operational security.

## Table of Contents

1. [Security Certifications](#security-certifications)
2. [Data Encryption](#data-encryption)
3. [Access Control](#access-control)
4. [Network Security](#network-security)
5. [Audit and Logging](#audit-and-logging)
6. [Data Governance](#data-governance)
7. [Incident Response](#incident-response)
8. [Compliance Mapping](#compliance-mapping)

---

## Security Certifications

### Teradata VantageCloud Certifications

| Certification | Status | Scope | Audit Frequency |
|---------------|--------|-------|-----------------|
| SOC 1 Type II | Certified | Internal controls over financial reporting | Annual |
| SOC 2 Type II | Certified | Security, availability, confidentiality | Annual |
| ISO 27001 | Certified | Information security management | Annual |
| ISO 27017 | Certified | Cloud security controls | Annual |
| ISO 27018 | Certified | Protection of PII in public clouds | Annual |
| PCI DSS | Certified | Payment card data protection | Annual |
| HIPAA | Compliant | Protected health information | Ongoing |
| GDPR | Compliant | EU data protection | Ongoing |
| CCPA | Compliant | California consumer privacy | Ongoing |

### Certification Verification

```bash
# Request audit reports from Teradata
# Contact: your Teradata account team or security@teradata.com

# SOC 2 report request
curl -X POST "https://www.teradata.com/trust-security-center" \
  -d "report_type=soc2" \
  -d "company=${COMPANY_NAME}" \
  -d "email=${CONTACT_EMAIL}"
```

---

## Data Encryption

### Encryption at Rest

#### Requirements

| Requirement | Standard | Implementation |
|-------------|----------|----------------|
| Algorithm | AES-256 | All stored data |
| Key Management | Customer-managed or Teradata-managed | KMS integration |
| Scope | All databases, backups, logs | Default enabled |

#### Configuration

```sql
-- Verify encryption status
SELECT
    DatabaseName,
    TableName,
    ProtectionType,
    EncryptionFlag
FROM DBC.TablesV
WHERE DatabaseName = 'yawl';

-- Enable column-level encryption for sensitive data
CREATE MULTISET TABLE yawl.sensitive_workflow_data (
    workflow_id VARCHAR(36) NOT NULL,
    pii_data JSON,
    encrypted_pii VARBYTE(64000)
)
PRIMARY INDEX (workflow_id);

-- Encrypt sensitive column using AES256
INSERT INTO yawl.sensitive_workflow_data
SELECT
    workflow_id,
    NULL,
    ENCRYPT_AES256('${ENCRYPTION_KEY}', CAST(pii_data AS VARBYTE))
FROM staging.workflow_data;
```

### Encryption in Transit

#### Requirements

| Requirement | Standard | Implementation |
|-------------|----------|----------------|
| Protocol | TLS 1.2+ | All connections |
| Certificate | SHA-256 with RSA 2048+ | Public certificates |
| Cipher Suites | ECDHE, AES-GCM | Strong encryption only |

#### JDBC Configuration

```java
// Secure JDBC connection properties
Properties props = new Properties();
props.setProperty("ENCRYPTDATA", "true");
props.setProperty("SSLMODE", "require");
props.setProperty("SSLVERSION", "TLSv1.2");
props.setProperty("SSLCIPHER", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");

Connection conn = DriverManager.getConnection(
    "jdbc:teradata://" + host + "/DATABASE=yawl",
    props
);
```

### Key Management

```yaml
# Customer-managed key configuration (AWS KMS)
encryption:
  provider: "aws_kms"
  key_specification:
    algorithm: "AES_256"
    key_usage: "ENCRYPT_DECRYPT"
    key_origin: "AWS_KMS"

  key_rotation:
    enabled: true
    rotation_period_days: 365

  key_policies:
    - name: "yawl-teradata-encryption-key"
      description: "Encryption key for YAWL workflow data"
      policy:
        Version: "2012-10-17"
        Statement:
          - Effect: "Allow"
            Principal:
              AWS: "arn:aws:iam::${ACCOUNT_ID}:role/TeradataVantageRole"
            Action:
              - "kms:Encrypt"
              - "kms:Decrypt"
              - "kms:GenerateDataKey"
            Resource: "*"
```

---

## Access Control

### Authentication Methods

| Method | Use Case | Security Level |
|--------|----------|----------------|
| Username/Password | Legacy applications | Medium |
| LDAP/Active Directory | Enterprise integration | High |
| SAML 2.0 SSO | Web applications | High |
| OAuth 2.0 | API access | High |
| Certificate-based | Service accounts | Very High |

### Role-Based Access Control (RBAC)

```sql
-- Create roles for YAWL integration
CREATE ROLE yawl_admin;
CREATE ROLE yawl_operator;
CREATE ROLE yawl_analyst;
CREATE ROLE yawl_readonly;

-- Grant privileges to roles
-- Admin role - full access
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER
    ON yawl TO yawl_admin;

-- Operator role - operational access
GRANT SELECT, INSERT, UPDATE
    ON yawl TO yawl_operator;

-- Analyst role - analytical access
GRANT SELECT
    ON yawl TO yawl_analyst;
GRANT SELECT
    ON yawl_analytics TO yawl_analyst;

-- Readonly role - read-only access
GRANT SELECT
    ON yawl_analytics TO yawl_readonly;

-- Assign roles to users
GRANT yawl_operator TO yawl_app_user;
GRANT yawl_analyst TO yawl_analytics_user;
GRANT yawl_readonly TO yawl_report_user;
```

### Column-Level Security

```sql
-- Create security policy for sensitive columns
CREATE SECURITY POLICY yawl_pii_policy
    FOR yawl.workflow_executions
    COLUMNS (customer_data, error_message)
    USING (
        CASE
            WHEN CURRENT_USER IN ('yawl_admin', 'yawl_operator')
            THEN TRUE
            ELSE FALSE
        END
    );

-- Apply row-level security
CREATE SECURITY POLICY yawl_department_policy
    FOR yawl.workflow_executions
    USING (
        CASE
            WHEN CURRENT_USER = 'yawl_admin'
            THEN TRUE
            WHEN department_id = (
                SELECT department_id
                FROM yawl.user_departments
                WHERE username = CURRENT_USER
            )
            THEN TRUE
            ELSE FALSE
        END
    );
```

### Password Policy

```sql
-- Set password complexity requirements
MODIFY USER yawl_app_user AS
    PASSWORD = (
        MINCHARS=12,
        MAXCHARS=64,
        MINDIGITS=1,
        MINUPPER=1,
        MINLOWER=1,
        MINSPECIAL=1,
        EXPIRE_PASSWORD=90,
        MAX_LOGON_ATTEMPTS=5,
        LOCK_TIME=30
    );
```

---

## Network Security

### Firewall Rules

```yaml
# Security group rules for Teradata Vantage
security_groups:
  teradata_ingress:
    description: "Ingress rules for Teradata Vantage"
    rules:
      # JDBC from YAWL engine only
      - name: "jdbc-yawl-engine"
        port: 1025
        protocol: TCP
        source:
          type: "security_group"
          value: "${YAWL_ENGINE_SG_ID}"
        description: "JDBC access from YAWL engine"

      # HTTPS for console (restricted)
      - name: "https-console"
        port: 443
        protocol: TCP
        source:
          type: "cidr"
          value: "${ADMIN_CIDR_BLOCKS}"
        description: "HTTPS console access for admins"

      # Teradata Studio (internal only)
      - name: "studio"
        port: 9047
        protocol: TCP
        source:
          type: "cidr"
          value: "${INTERNAL_CIDR_BLOCKS}"
        description: "Teradata Studio access"

  teradata_egress:
    description: "Egress rules for Teradata Vantage"
    rules:
      # Allow all outbound (can be restricted)
      - name: "allow-all"
        port: 0
        protocol: ALL
        destination:
          type: "cidr"
          value: "0.0.0.0/0"
```

### PrivateLink / Private Endpoints

```hcl
# AWS PrivateLink for secure connectivity
resource "aws_vpc_endpoint" "teradata" {
  vpc_id              = aws_vpc.teradata.id
  service_name        = "com.amazonaws.${var.aws_region}.teradata-vantage"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.primary.id, aws_subnet.secondary.id]
  security_group_ids  = [aws_security_group.teradata.id]
  private_dns_enabled = true

  tags = {
    Name = "${var.project_name}-teradata-endpoint"
  }
}
```

```hcl
# Azure Private Endpoint
resource "azurerm_private_endpoint" "teradata" {
  name                = "${var.project_name}-teradata-pe"
  location            = azurerm_resource_group.teradata.location
  resource_group_name = azurerm_resource_group.teradata.name
  subnet_id           = azurerm_subnet.primary.id

  private_service_connection {
    name                           = "${var.project_name}-teradata-psc"
    private_connection_resource_id = var.teradata_resource_id
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "teradata-dns-zone-group"
    private_dns_zone_ids = [azurerm_private_dns_zone.teradata.id]
  }
}
```

### IP Allowlisting

```sql
-- Configure IP allowlisting in Teradata
CREATE SECURITY POLICY ip_allowlist_policy
    FOR SYSTEM
    USING (
        CASE
            WHEN $HOST IN ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')
            THEN TRUE
            WHEN $HOST IN (${ALLOWED_PUBLIC_IPS})
            THEN TRUE
            ELSE FALSE
        END
    );
```

---

## Audit and Logging

### Audit Events

| Event Category | Events Captured | Retention |
|----------------|-----------------|-----------|
| Authentication | Login success/failure, session management | 90 days |
| Authorization | Permission grants/revokes, role changes | 1 year |
| Data Access | SELECT, INSERT, UPDATE, DELETE operations | 90 days |
| Schema Changes | CREATE, ALTER, DROP operations | 1 year |
| Admin Operations | User management, configuration changes | 1 year |

### Audit Configuration

```sql
-- Enable comprehensive auditing
BEGIN QUERY LOGGING
    ON ALL
    WITH ALL
    LIMIT SQLTEXT=10000;

-- Create audit log table (managed by Teradata)
-- Query audit logs via:
SELECT
    LogDate,
    LogTime,
    UserName,
    QueryText,
    ErrorCode,
    StatementType
FROM DBC.DBQLogTbl
WHERE DatabaseName = 'yawl'
ORDER BY LogDate DESC, LogTime DESC;

-- Export audit logs to external SIEM
EXPORT_AUDIT_LOGS (
    START_TIMESTAMP = '${START_TIME}',
    END_TIMESTAMP = '${END_TIME}',
    DESTINATION = 's3://yawl-audit-logs/teradata/'
);
```

### SIEM Integration

```yaml
# SIEM integration configuration (Splunk example)
splunk:
  host: "splunk.yawl.internal"
  port: 8089
  index: "teradata_audit"

  inputs:
    - name: "teradata_audit"
      type: "tcp"
      port: 9997
      sourcetype: "teradata:audit"

  event_types:
    - event: "login"
      query: |
        SELECT * FROM DBC.DBQLogTbl
        WHERE StatementType = 'Logon'
        AND LogDate >= CURRENT_DATE - 1

    - event: "data_access"
      query: |
        SELECT * FROM DBC.DBQLogTbl
        WHERE DatabaseName = 'yawl'
        AND StatementType IN ('Select', 'Insert', 'Update', 'Delete')
        AND LogDate >= CURRENT_DATE - 1

  alerts:
    - name: "failed_login_attempts"
      condition: "count(login_failure) > 5 within 5m"
      severity: "high"
      action: "email,slack"
```

### Log Retention Policy

```yaml
log_retention:
  audit_logs:
    hot_storage_days: 90
    cold_storage_days: 365
    archive_storage_years: 7
    purge_after_years: 7

  query_logs:
    hot_storage_days: 30
    cold_storage_days: 90
    purge_after_days: 90

  error_logs:
    hot_storage_days: 30
    cold_storage_days: 180
    purge_after_days: 180
```

---

## Data Governance

### Data Classification

```sql
-- Create data classification tags
CREATE MULTISET TABLE yawl.data_classification (
    table_name VARCHAR(128) NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    classification VARCHAR(50) NOT NULL,  -- PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED
    pii_flag CHAR(1) DEFAULT 'N',
    encryption_required CHAR(1) DEFAULT 'N',
    retention_days INTEGER,
    PRIMARY KEY (table_name, column_name)
);

-- Classify YAWL data
INSERT INTO yawl.data_classification VALUES
    ('workflow_executions', 'workflow_id', 'INTERNAL', 'N', 'N', 365),
    ('workflow_executions', 'case_id', 'INTERNAL', 'N', 'N', 365),
    ('workflow_executions', 'customer_data', 'CONFIDENTIAL', 'Y', 'Y', 90),
    ('workflow_executions', 'error_message', 'INTERNAL', 'N', 'N', 90),
    ('audit_log', 'event_data', 'CONFIDENTIAL', 'Y', 'Y', 365);
```

### Data Masking

```sql
-- Create masking functions for sensitive data
CREATE FUNCTION yawl.mask_email(IN email VARCHAR(255))
RETURNS VARCHAR(255)
LANGUAGE SQL
DETERMINISTIC
CONTAINS SQL
SQL SECURITY DEFINER
RETURN
    CASE
        WHEN CURRENT_USER IN ('yawl_admin')
        THEN email
        ELSE REGEXP_REPLACE(email, '(.{1,2})@(.)', '***@***')
    END;

-- Create masking function for PII
CREATE FUNCTION yawl.mask_pii(IN data VARCHAR(1000))
RETURNS VARCHAR(1000)
LANGUAGE SQL
DETERMINISTIC
CONTAINS SQL
SQL SECURITY DEFINER
RETURN
    CASE
        WHEN CURRENT_USER IN ('yawl_admin', 'yawl_operator')
        THEN data
        ELSE '***REDACTED***'
    END;

-- Apply masking to views
CREATE VIEW yawl.v_workflow_executions_safe AS
SELECT
    workflow_id,
    task_id,
    case_id,
    yawl.mask_email(customer_email) AS customer_email,
    yawl.mask_pii(customer_data) AS customer_data,
    start_time,
    end_time,
    status
FROM yawl.workflow_executions;
```

### Data Retention

```sql
-- Create data retention policy procedure
CREATE PROCEDURE yawl.apply_retention_policy()
BEGIN
    -- Delete workflow executions older than retention period
    DELETE FROM yawl.workflow_executions
    WHERE processing_date < CURRENT_DATE - 365;

    -- Archive old audit logs
    INSERT INTO yawl.audit_log_archive
    SELECT * FROM yawl.audit_log
    WHERE timestamp < CURRENT_TIMESTAMP - INTERVAL '90' DAY;

    DELETE FROM yawl.audit_log
    WHERE timestamp < CURRENT_TIMESTAMP - INTERVAL '90' DAY;

    -- Log retention action
    INSERT INTO yawl.retention_log (action_timestamp, records_deleted, table_name)
    VALUES (CURRENT_TIMESTAMP, ACTIVITY_COUNT, 'workflow_executions');
END;
```

---

## Incident Response

### Security Incident Classification

| Severity | Description | Response Time | Escalation |
|----------|-------------|---------------|------------|
| Critical | Active data breach, system compromise | 15 minutes | CISO, Legal |
| High | Unauthorized access, data exfiltration attempt | 1 hour | Security Team Lead |
| Medium | Suspicious activity, policy violation | 4 hours | Security Analyst |
| Low | Audit finding, minor policy deviation | 24 hours | DBA Team |

### Incident Response Procedures

```yaml
incident_response:
  detection:
    sources:
      - "Teradata audit logs"
      - "SIEM alerts"
      - "User reports"
      - "Automated monitoring"

  investigation:
    steps:
      - name: "1. Assess scope"
        actions:
          - "Identify affected systems"
          - "Determine data classification"
          - "Assess potential impact"

      - name: "2. Contain"
        actions:
          - "Revoke compromised credentials"
          - "Isolate affected systems"
          - "Block suspicious IPs"

      - name: "3. Eradicate"
        actions:
          - "Remove unauthorized access"
          - "Patch vulnerabilities"
          - "Update security controls"

      - name: "4. Recover"
        actions:
          - "Restore from backup if needed"
          - "Verify system integrity"
          - "Resume normal operations"

      - name: "5. Post-incident"
        actions:
          - "Document findings"
          - "Update security procedures"
          - "Conduct lessons learned"
```

### Emergency Access Revocation

```sql
-- Emergency procedure to revoke all access
-- Run by security admin during incident

-- Lock all YAWL users
MODIFY USER yawl_app_user AS LOCKED;
MODIFY USER yawl_analytics_user AS LOCKED;
MODIFY USER yawl_report_user AS LOCKED;

-- Revoke all active sessions
CALL SYSLIB.TerminateAllSessions('yawl%');

-- Revoke role privileges temporarily
REVOKE yawl_operator FROM yawl_app_user;
REVOKE yawl_analyst FROM yawl_analytics_user;

-- Log emergency action
INSERT INTO yawl.security_incidents (
    incident_id,
    action,
    reason,
    performed_by,
    timestamp
) VALUES (
    '${INCIDENT_ID}',
    'EMERGENCY_ACCESS_REVOCATION',
    '${INCIDENT_REASON}',
    CURRENT_USER,
    CURRENT_TIMESTAMP
);
```

---

## Compliance Mapping

### GDPR Compliance

| Requirement | Implementation | Status |
|-------------|----------------|--------|
| Data Minimization | Column-level access control, data masking | Implemented |
| Purpose Limitation | Role-based access, audit logging | Implemented |
| Storage Limitation | Automated retention policies | Implemented |
| Data Subject Rights | Data export, deletion procedures | Implemented |
| Data Portability | Export to standard formats | Implemented |
| Security | Encryption, access control, monitoring | Implemented |
| Breach Notification | SIEM integration, incident response | Implemented |

### HIPAA Compliance

| Requirement | Implementation | Status |
|-------------|----------------|--------|
| Access Control | RBAC, unique user identification | Implemented |
| Audit Controls | Comprehensive audit logging | Implemented |
| Integrity | Data validation, checksums | Implemented |
| Transmission Security | TLS 1.2+ encryption | Implemented |
| Encryption | AES-256 at rest, TLS in transit | Implemented |
| Business Associate Agreement | Available from Teradata | Available |

### SOC 2 Type II Controls

| Trust Service Criteria | Control | Evidence |
|------------------------|---------|----------|
| Security | Access control, encryption | Teradata audit report |
| Availability | 99.95% SLA, disaster recovery | Teradata SLA documentation |
| Confidentiality | Data classification, encryption | Security policies |
| Processing Integrity | Data validation, error handling | Application logs |

---

## Security Checklist

### Pre-Deployment

- [ ] Review Teradata security certifications
- [ ] Configure encryption at rest and in transit
- [ ] Set up network security groups/firewalls
- [ ] Create RBAC roles and assign permissions
- [ ] Enable comprehensive audit logging
- [ ] Configure SIEM integration
- [ ] Document data classification
- [ ] Set up data retention policies

### Post-Deployment

- [ ] Verify all encryption is active
- [ ] Test access control enforcement
- [ ] Validate audit log capture
- [ ] Configure monitoring alerts
- [ ] Test incident response procedures
- [ ] Schedule regular security reviews
- [ ] Document security baseline
- [ ] Train users on security policies

### Ongoing Operations

- [ ] Weekly security log review
- [ ] Monthly access review
- [ ] Quarterly penetration testing
- [ ] Annual security certification review
- [ ] Continuous vulnerability monitoring
- [ ] Regular security awareness training

---

## Contact Information

| Role | Contact | Purpose |
|------|---------|---------|
| Teradata Security | security@teradata.com | Security certifications, incident reporting |
| Teradata Support | 1-877-MY-TDATA | 24/7 technical support |
| Cloud Console | https://console.intellicloud.teradata.com | System management |
| Support Portal | https://support.teradatacloud.com/ | Incident submission |

---

## Appendix: Security Configuration Template

```yaml
# security-config.yaml
# Complete security configuration template

encryption:
  at_rest:
    enabled: true
    algorithm: AES-256
    key_management: customer_managed
    kms_provider: aws_kms
    key_rotation_days: 365

  in_transit:
    enabled: true
    protocol: TLS
    min_version: "1.2"
    cipher_suites:
      - "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
      - "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"

access_control:
  authentication:
    primary: saml_sso
    fallback: ldap
    mfa: required
    session_timeout_minutes: 60

  authorization:
    model: rbac
    roles:
      - name: yawl_admin
        permissions: [full_access]
      - name: yawl_operator
        permissions: [read, write, update]
      - name: yawl_analyst
        permissions: [read_analytics]
      - name: yawl_readonly
        permissions: [read_reports]

  password_policy:
    min_length: 12
    max_length: 64
    require_uppercase: true
    require_lowercase: true
    require_digits: true
    require_special: true
    expiry_days: 90
    history_count: 12
    max_attempts: 5
    lockout_minutes: 30

network:
  firewall:
    default_deny: true
    allowed_ports:
      - 1025  # JDBC
      - 443   # HTTPS
      - 9047  # Studio

  private_endpoint: enabled
  ip_allowlist:
    - "${YAWL_ENGINE_CIDR}"
    - "${ADMIN_CIDR}"

audit:
  enabled: true
  events:
    - authentication
    - authorization
    - data_access
    - schema_changes
    - admin_operations
  retention_days: 90
  siem_integration:
    enabled: true
    provider: splunk
    endpoint: "${SPLUNK_ENDPOINT}"

monitoring:
  alerts:
    - name: failed_login_attempts
      threshold: 5
      window_minutes: 5
      severity: high

    - name: unusual_data_access
      threshold: 10000
      window_minutes: 60
      severity: medium

    - name: schema_modification
      threshold: 1
      severity: critical
