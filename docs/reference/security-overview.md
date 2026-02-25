# YAWL Security Overview

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Security Architecture

YAWL implements a defense-in-depth security strategy across multiple layers.

### 1.1 Security Layers

```
+-------------------------------------------------------+
|                    Perimeter Layer                     |
|  DDoS Protection | WAF | CDN | Edge Security          |
+-------------------------------------------------------+
|                    Network Layer                       |
|  VPC Isolation | Security Groups | Network Policies   |
+-------------------------------------------------------+
|                    Application Layer                   |
|  Authentication | Authorization | Input Validation    |
+-------------------------------------------------------+
|                    Data Layer                          |
|  Encryption at Rest | Encryption in Transit | Audit   |
+-------------------------------------------------------+
```

---

## 2. Authentication

### 2.1 Supported Authentication Methods

| Method | Use Case | Configuration |
|--------|----------|---------------|
| **Basic Auth** | Simple deployments | Built-in |
| **OAuth 2.0/OIDC** | Enterprise SSO | External provider |
| **SAML 2.0** | Enterprise federation | External provider |
| **mTLS** | Service-to-service | Certificate-based |
| **API Keys** | Service accounts | Built-in |

### 2.2 OAuth 2.0/OIDC Configuration

```yaml
# OAuth configuration
security:
  oauth2:
    client:
      clientId: yawl-client
      clientSecret: ${OAUTH_CLIENT_SECRET}
      accessTokenUri: https://auth.example.com/oauth/token
      userAuthorizationUri: https://auth.example.com/oauth/authorize
      scope: openid,profile,email
    resource:
      userInfoUri: https://auth.example.com/oauth/userinfo
      jwt:
        key-uri: https://auth.example.com/oauth/jwks
```

### 2.3 Session Management

```yaml
# Session configuration
session:
  timeout: 3600  # 1 hour
  max-concurrent: 1  # Single session per user
  cookie:
    secure: true
    http-only: true
    same-site: strict
  store: redis  # Distributed sessions
```

---

## 3. Authorization

### 3.1 Role-Based Access Control (RBAC)

| Role | Permissions | Use Case |
|------|-------------|----------|
| **Administrator** | Full access | System administrators |
| **Process Manager** | Manage specs, cases | Business users |
| **Participant** | Complete work items | End users |
| **Observer** | Read-only access | Auditors |

### 3.2 RBAC Configuration

```yaml
# RBAC configuration
authorization:
  roles:
    - name: administrator
      permissions:
        - "*"
    - name: process_manager
      permissions:
        - "spec:*"
        - "case:*"
        - "workitem:read"
    - name: participant
      permissions:
        - "workitem:read"
        - "workitem:complete"
    - name: observer
      permissions:
        - "*:read"
```

### 3.3 Resource-Level Security

```xml
<!-- Task-level authorization in YAWL specification -->
<task id="approve_expense">
  <resourcing>
    <offer>
      <role>finance_manager</role>
    </offer>
  </resourcing>
  <privileges>
    <read>
      <role>finance_manager</role>
      <role>auditor</role>
    </read>
    <write>
      <role>finance_manager</role>
    </write>
  </privileges>
</task>
```

---

## 4. Network Security

### 4.1 VPC Configuration

```yaml
# VPC configuration (Terraform)
resource "aws_vpc" "yawl" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "yawl-vpc"
  }
}
```

### 4.2 Security Groups

```yaml
# Security group rules
Inbound:
  - Port 443 (HTTPS): Load balancer only
  - Port 8080: Internal services only

Outbound:
  - Port 5432: Database subnet only
  - Port 6379: Redis subnet only
  - Port 443: External APIs (restricted)
```

### 4.3 Network Policies (Kubernetes)

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-engine-policy
  namespace: yawl
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8080
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgresql
      ports:
        - protocol: TCP
          port: 5432
    - to:
        - podSelector:
            matchLabels:
              app: redis
      ports:
        - protocol: TCP
          port: 6379
```

---

## 5. Data Protection

### 5.1 Encryption at Rest

| Data Type | Encryption | Key Management |
|-----------|------------|----------------|
| Database | AES-256 | Cloud KMS |
| Redis | AES-256 | Cloud KMS |
| Backups | AES-256 | Cloud KMS |
| Secrets | Envelope encryption | Cloud KMS |

### 5.2 Encryption in Transit

```yaml
# TLS configuration
tls:
  version: "1.2"  # Minimum TLS version
  protocols:
    - TLSv1.2
    - TLSv1.3
  ciphers:
    - ECDHE-ECDSA-AES128-GCM-SHA256
    - ECDHE-RSA-AES128-GCM-SHA256
    - ECDHE-ECDSA-AES256-GCM-SHA384
    - ECDHE-RSA-AES256-GCM-SHA384
  certificate:
    source: letsencrypt  # or cloud-managed
```

### 5.3 Data Masking

```yaml
# Sensitive data masking configuration
data-masking:
  patterns:
    - name: credit_card
      pattern: '\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b'
      replacement: '****-****-****-$LAST4'
    - name: ssn
      pattern: '\b\d{3}[-\s]?\d{2}[-\s]?\d{4}\b'
      replacement: '***-**-$LAST4'
    - name: email
      pattern: '\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'
      replacement: '***@***.***'
```

---

## 6. Secrets Management

### 6.1 Secrets Architecture

```
+-------------+     +-------------+     +-------------+
|  Developer  |     |  CI/CD      |     |  Runtime    |
+------+------+     +------+------+     +------+------+
       |                   |                   |
       v                   v                   v
+-------------+     +-------------+     +-------------+
|  Vault      |     |  Secrets    |     |  Pod        |
|  (Dev)      |     |  Manager    |     |  Secrets    |
+-------------+     +-------------+     +-------------+
       |                   |                   |
       +-------------------+-------------------+
                           |
                  +--------v--------+
                  |  Cloud KMS      |
                  +-----------------+
```

### 6.2 Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: yawl-db-credentials
  namespace: yawl
type: Opaque
stringData:
  username: yawl_admin
  password: ${DB_PASSWORD}  # Injected from external secrets
  host: ${DB_HOST}
  port: "5432"
```

### 6.3 External Secrets Integration

```yaml
# AWS Secrets Manager integration
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secretsmanager
  namespace: yawl
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: yawl-engine
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-db-credentials
  namespace: yawl
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
  target:
    name: yawl-db-credentials
  data:
    - secretKey: username
      remoteRef:
        key: yawl/database
        property: username
    - secretKey: password
      remoteRef:
        key: yawl/database
        property: password
```

---

## 7. Audit Logging

### 7.1 Audit Events

| Event Category | Events Logged |
|----------------|---------------|
| **Authentication** | Login, logout, failed attempts |
| **Authorization** | Permission grants, denials |
| **Data Access** | Read, write, delete operations |
| **Configuration** | Settings changes |
| **Administration** | User management, role changes |

### 7.2 Audit Log Format

```json
{
  "timestamp": "2026-02-14T10:30:00Z",
  "event_type": "AUTHENTICATION",
  "event_action": "LOGIN",
  "event_result": "SUCCESS",
  "user_id": "user-123",
  "user_name": "john.doe@example.com",
  "source_ip": "192.168.1.100",
  "user_agent": "Mozilla/5.0...",
  "resource_type": "session",
  "resource_id": "session-abc123",
  "details": {
    "method": "oidc",
    "provider": "okta"
  }
}
```

### 7.3 Audit Log Retention

| Environment | Retention Period | Storage |
|-------------|------------------|---------|
| Development | 30 days | Standard |
| Staging | 90 days | Standard |
| Production | 7 years | Archive (after 1 year) |

---

## 8. Vulnerability Management

### 8.1 Security Scanning

| Scan Type | Frequency | Tool |
|-----------|-----------|------|
| Container Image | Every build | Trivy, Snyk |
| Dependencies | Daily | Snyk, Dependabot |
| Infrastructure | Weekly | Checkov, tfsec |
| Application | Monthly | OWASP ZAP |
| Penetration | Annually | Manual + automated |

### 8.2 Container Security

```yaml
# Pod Security Standards
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: yawl-restricted
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'configMap'
    - 'emptyDir'
    - 'projected'
    - 'secret'
    - 'persistentVolumeClaim'
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'MustRunAs'
    ranges:
      - min: 1000
        max: 65535
```

---

## 9. Compliance

### 9.1 Supported Compliance Frameworks

| Framework | Status | Notes |
|-----------|--------|-------|
| **SOC 2 Type II** | Supported | Annual audit required |
| **ISO 27001** | Compatible | Customer certification |
| **GDPR** | Supported | Data processing agreement |
| **HIPAA** | Compatible | BAA available |
| **PCI DSS** | Compatible | For payment workflows |

### 9.2 Compliance Checklist

- [ ] Data encryption at rest and in transit
- [ ] Access logging and monitoring
- [ ] Regular security assessments
- [ ] Incident response procedures
- [ ] Data retention policies
- [ ] Privacy policy and notices
- [ ] User consent management
- [ ] Data subject rights procedures

---

## 10. Incident Response

### 10.1 Security Incident Classification

| Severity | Description | Response Time |
|----------|-------------|---------------|
| **Critical** | Active breach, data exfiltration | 15 minutes |
| **High** | Vulnerability exploited, unauthorized access | 1 hour |
| **Medium** | Potential vulnerability, suspicious activity | 4 hours |
| **Low** | Policy violation, minor security issue | 24 hours |

### 10.2 Incident Response Procedure

```
1. Detection
   - Automated alerts
   - User reports
   - Security scans

2. Containment
   - Isolate affected systems
   - Revoke compromised credentials
   - Block malicious IPs

3. Eradication
   - Remove threat
   - Patch vulnerabilities
   - Update security controls

4. Recovery
   - Restore services
   - Verify integrity
   - Monitor for recurrence

5. Post-Incident
   - Document incident
   - Update procedures
   - Train team
```

---

## 11. Security Best Practices

### 11.1 Deployment Checklist

- [ ] Enable TLS for all external traffic
- [ ] Configure network policies
- [ ] Enable audit logging
- [ ] Set up secrets management
- [ ] Configure RBAC
- [ ] Enable container security policies
- [ ] Set up vulnerability scanning
- [ ] Configure backup encryption
- [ ] Enable DDoS protection
- [ ] Configure WAF rules

### 11.2 Runtime Security

- Use non-root containers
- Read-only root filesystem
- Drop all capabilities
- Use security contexts
- Enable Pod Security Standards
- Monitor for anomalies
- Regular security updates

---

## 12. Next Steps

- [Compliance Matrix](compliance-matrix.md)
- [Deployment Guide](../deployment/deployment-guide.md)
- [Operations Guide](../operations/scaling-guide.md)
