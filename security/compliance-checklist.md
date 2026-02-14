# YAWL Security Compliance Checklist

**Date**: 2026-02-14
**Status**: Implementation Guide
**Compliance Frameworks**: HIPAA, SOC2 Type II, PCI-DSS 3.2.1
**Last Updated**: 2026-02-14

---

## Executive Summary

This document provides a comprehensive compliance checklist for the YAWL application across three major regulatory frameworks:

- **HIPAA** (Health Insurance Portability and Accountability Act)
- **SOC2** (Service Organization Control Type II)
- **PCI-DSS** (Payment Card Industry Data Security Standard)

Each section maps specific compliance requirements to implementation tasks and validation methods.

---

## Table of Contents

1. [HIPAA Compliance Checklist](#hipaa-compliance-checklist)
2. [SOC2 Compliance Checklist](#soc2-compliance-checklist)
3. [PCI-DSS Compliance Checklist](#pci-dss-compliance-checklist)
4. [Cross-Framework Controls](#cross-framework-controls)
5. [Implementation Timeline](#implementation-timeline)
6. [Audit and Testing](#audit-and-testing)
7. [Incident Response](#incident-response)
8. [Documentation Requirements](#documentation-requirements)

---

## HIPAA Compliance Checklist

### §164.308 - Administrative Safeguards

#### 164.308(a)(1) - Security Management Process
- [ ] Conduct annual risk assessments
- [ ] Document security vulnerabilities and remediation plans
- [ ] Implement risk mitigation measures
- [ ] Assign Chief Security Officer (CSO) or equivalent
- [ ] Document all security incidents and breaches
- [ ] Maintain breach log with containment steps
- [ ] Sanction policy for non-compliance

**Implementation Status**: Pending
**Evidence File**: `/security/security-policy.yaml`
**Review Frequency**: Quarterly

#### 164.308(a)(2) - Assigned Security Responsibility
- [ ] Designate Security Officer
- [ ] Document job title and responsibilities
- [ ] Define reporting hierarchy
- [ ] Assign incident response duties
- [ ] Establish access approval workflows

**Implementation Status**: Pending
**Responsible Party**: Security Team

#### 164.308(a)(3) - Workforce Security
- [ ] Implement authorization and supervision procedures
  - [ ] Develop authorization procedures
  - [ ] Define supervision roles
  - [ ] Document approval workflows
  - [ ] Implement RBAC (See: `/security/rbac-policies.yaml`)

- [ ] Implement termination procedures
  - [ ] Disable access within 24 hours
  - [ ] Recover equipment
  - [ ] Document exit checklist
  - [ ] Preserve audit logs

- [ ] Implement change management
  - [ ] Track all access changes
  - [ ] Maintain change logs
  - [ ] Test changes before deployment
  - [ ] Document rollback procedures

**Implementation Status**: Complete (RBAC)
**Evidence File**: `/security/rbac-policies.yaml`

#### 164.308(a)(4) - Information Access Management
- [ ] Implement access controls based on role
- [ ] Define minimum necessary access principle
- [ ] Document access levels for each role
- [ ] Implement emergency access procedures
- [ ] Log all access granting decisions

**Implementation Status**: Complete (RBAC)
**Evidence File**: `/security/rbac-policies.yaml`

#### 164.308(a)(5) - Security Awareness and Training
- [ ] Conduct mandatory security training for all staff
  - [ ] Initial training before access
  - [ ] Annual refresher training
  - [ ] Role-specific training
  - [ ] Track attendance

- [ ] Implement security reminders
  - [ ] Monthly security bulletins
  - [ ] Phishing awareness campaign
  - [ ] Password hygiene reminders
  - [ ] Data protection guidelines

- [ ] Implement protection from malicious software
  - [ ] Anti-malware on all endpoints
  - [ ] Regular virus definition updates
  - [ ] USB scanning procedures
  - [ ] Email filtering

- [ ] Implement log-in monitoring
  - [ ] Failed login attempts logged
  - [ ] Account lockout after 5 failed attempts
  - [ ] Session timeout after 30 minutes
  - [ ] Alert on unusual access patterns

**Implementation Status**: Pending
**Review Frequency**: Monthly

#### 164.308(a)(6) - Security Incident Procedures
- [ ] Establish incident response team
- [ ] Document response procedures
  - [ ] Detection and analysis
  - [ ] Containment and eradication
  - [ ] Recovery procedures
  - [ ] Post-incident review

- [ ] Implement breach notification procedures
  - [ ] Identify breached individuals
  - [ ] Notification within 60 days
  - [ ] Notification format and content
  - [ ] Document all notifications

- [ ] Maintain incident log
  - [ ] Date and time of discovery
  - [ ] Description of incident
  - [ ] Individuals affected
  - [ ] Actions taken
  - [ ] Outcome

**Implementation Status**: Pending
**Review Frequency**: Quarterly

#### 164.308(a)(7) - Contingency Planning
- [ ] Establish data backup procedures
  - [ ] Backup frequency (daily minimum)
  - [ ] Backup verification (test restoration)
  - [ ] Off-site backup storage
  - [ ] Encryption of backups

- [ ] Establish disaster recovery plan
  - [ ] RTO (Recovery Time Objective): ≤ 4 hours
  - [ ] RPO (Recovery Point Objective): ≤ 1 hour
  - [ ] Recovery procedures documented
  - [ ] Regular DR testing (quarterly)

- [ ] Establish business continuity plan
  - [ ] Critical function identification
  - [ ] Priority restoration order
  - [ ] Alternative processing sites
  - [ ] Annual testing

- [ ] Establish emergency access procedures
  - [ ] Temporary access procedures
  - [ ] Emergency access justification
  - [ ] Emergency access logging
  - [ ] Emergency access review

**Implementation Status**: Pending
**Review Frequency**: Quarterly

#### 164.308(a)(8) - Evaluation
- [ ] Conduct periodic security evaluations
  - [ ] Frequency: Annually
  - [ ] Scope: All safeguards
  - [ ] Documentation: Findings and recommendations
  - [ ] Remediation: Track corrective actions

**Implementation Status**: Pending

### §164.312 - Technical Safeguards

#### 164.312(a)(1) - Access Controls
- [ ] Implement unique user identification
  - [ ] Service Account: `yawl-admin` (See: RBAC)
  - [ ] Service Account: `yawl-developer`
  - [ ] Service Account: `yawl-viewer`
  - [ ] Service Account: `yawl-operator`
  - [ ] Service Account: `yawl-security-officer`

- [ ] Implement emergency access procedures
  - [ ] Just-In-Time (JIT) access
  - [ ] Temporary access with expiration
  - [ ] Approval and logging
  - [ ] Post-incident revocation

- [ ] Implement encryption and decryption
  - [ ] In-transit encryption: TLS 1.3
  - [ ] At-rest encryption: AES-256-GCM
  - [ ] Key management: Cloud KMS
  - [ ] Key rotation: 90 days

**Implementation Status**: Partial
**Evidence File**: `/security/security-policy.yaml`, `/security/rbac-policies.yaml`

#### 164.312(a)(2) - Audit Controls
- [ ] Implement audit log generation
  - [ ] Log all access to PHI (Protected Health Information)
  - [ ] Log all authentication attempts
  - [ ] Log all authorization changes
  - [ ] Log all data modifications
  - [ ] Retention: 7 years minimum

- [ ] Implement audit log review procedures
  - [ ] Automated log monitoring
  - [ ] Alert on suspicious activity
  - [ ] Manual review: Monthly
  - [ ] Document findings

- [ ] Implement audit log integrity controls
  - [ ] Centralized logging (immutable)
  - [ ] Log encryption
  - [ ] Tamper detection
  - [ ] Off-site backup

**Implementation Status**: Partial
**Evidence File**: `/security/security-policy.yaml`

#### 164.312(a)(2)(i) - User Identification and Authentication
- [ ] Implement strong authentication
  - [ ] Multi-Factor Authentication (MFA): Required
  - [ ] Authentication methods: OAuth2, SAML2, MFA, certificates
  - [ ] Password policy: 14 chars, complex, 90-day rotation
  - [ ] Session timeout: 30 minutes

**Implementation Status**: Pending
**Evidence File**: `/security/security-policy.yaml`

#### 164.312(a)(2)(ii) - Emergency Access Procedure
- [ ] Temporary access procedures
  - [ ] Emergency access authorization
  - [ ] Time-limited access tokens
  - [ ] Audit logging of emergency access
  - [ ] Post-incident revocation

**Implementation Status**: Pending

#### 164.312(a)(2)(iii) - Encryption and Decryption
- [ ] In-transit encryption
  - [ ] HTTPS/TLS 1.3 for all APIs
  - [ ] Cipher suites: TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
  - [ ] Certificate pinning (optional)
  - [ ] Regular certificate rotation

- [ ] At-rest encryption
  - [ ] Database encryption: AES-256-GCM
  - [ ] File system encryption
  - [ ] Backup encryption
  - [ ] Key rotation: 90 days

**Implementation Status**: Pending

---

## SOC2 Compliance Checklist

### CC6 - Logical and Physical Access Controls

#### CC6.1 - Logical Access Restrictions
- [ ] Implement access control framework
  - [ ] Role-based access control (RBAC)
  - [ ] Principle of least privilege
  - [ ] Service accounts with limited permissions
  - [ ] Regular access reviews

- [ ] Identify and manage authorized users
  - [ ] User provisioning procedures
  - [ ] User deprovisioning procedures
  - [ ] Access approval workflows
  - [ ] Segregation of duties

**Implementation Status**: Complete
**Evidence File**: `/security/rbac-policies.yaml`

#### CC6.2 - User Access Provisioning and De-provisioning
- [ ] Implement user provisioning
  - [ ] Automated provisioning workflow
  - [ ] Manager approval required
  - [ ] Job role-based access
  - [ ] Provisioning documentation

- [ ] Implement user deprovisioning
  - [ ] Automatic access removal on termination
  - [ ] Exit checklist
  - [ ] Equipment recovery
  - [ ] Data archival

**Implementation Status**: Pending
**Review Frequency**: Monthly

#### CC6.3 - User Access Revocation
- [ ] Implement timely access revocation
  - [ ] Revocation within 24 hours of termination
  - [ ] Automated deprovisioning
  - [ ] Manager notification
  - [ ] Audit trail

**Implementation Status**: Pending

### CC7 - System Monitoring

#### CC7.1 - System Monitoring
- [ ] Implement comprehensive monitoring
  - [ ] Application logging
  - [ ] System logging
  - [ ] Security logging
  - [ ] Network monitoring

- [ ] Implement log aggregation
  - [ ] Centralized log collection
  - [ ] Real-time alerting
  - [ ] Log retention: 7 years
  - [ ] Log integrity verification

**Implementation Status**: Partial

#### CC7.2 - Detecting and Investigating Anomalies
- [ ] Implement anomaly detection
  - [ ] Failed login monitoring
  - [ ] Unusual access patterns
  - [ ] Data exfiltration detection
  - [ ] Configuration change alerts

- [ ] Implement investigation procedures
  - [ ] Incident investigation process
  - [ ] Forensics preservation
  - [ ] Root cause analysis
  - [ ] Remediation tracking

**Implementation Status**: Pending

#### CC7.3 - System Monitoring Procedures
- [ ] Implement monitoring procedures
  - [ ] Log review schedule: Daily
  - [ ] Alert review: Real-time
  - [ ] Escalation procedures
  - [ ] Investigation documentation

**Implementation Status**: Pending
**Review Frequency**: Daily

#### CC7.4 - System Monitoring Tools
- [ ] Implement monitoring tools
  - [ ] SIEM (Security Information and Event Management)
  - [ ] Log aggregation (ELK, Splunk)
  - [ ] Performance monitoring
  - [ ] Network monitoring

**Implementation Status**: Pending

---

## PCI-DSS Compliance Checklist

### Requirement 1 - Network Segmentation

#### 1.1 - Firewall Configuration Standards
- [ ] Implement firewall rules
  - [ ] Inbound rules: Deny all except necessary
  - [ ] Outbound rules: Deny all except necessary
  - [ ] Database isolation from internet
  - [ ] API gateway as entry point

- [ ] Document firewall configuration
  - [ ] Data flow diagrams
  - [ ] Firewall rules documentation
  - [ ] Change log
  - [ ] Annual review

**Implementation Status**: Partial
**Evidence File**: `/security/network-policies.yaml`

#### 1.2 - Network Access Segmentation
- [ ] Implement network segmentation
  - [ ] Separate networks: Production, Development, Management
  - [ ] VLAN segmentation
  - [ ] Database isolation
  - [ ] No direct internet access to databases

- [ ] Implement access controls
  - [ ] Restrict unnecessary protocols
  - [ ] Block P2P protocols
  - [ ] Restrict internet access
  - [ ] IP whitelisting

**Implementation Status**: Partial
**Evidence File**: `/security/network-policies.yaml`

#### 1.3 - Prohibit Direct Public Access
- [ ] Restrict access to payment systems
  - [ ] No direct internet access
  - [ ] API gateway required
  - [ ] Firewall protection
  - [ ] IPS/IDS protection

**Implementation Status**: Pending

### Requirement 2 - Default Security Parameters

#### 2.2 - Security Configuration Standards
- [ ] Configure systems securely
  - [ ] Remove unnecessary services
  - [ ] Disable unnecessary protocols
  - [ ] Set strong passwords
  - [ ] Document all changes

- [ ] Disable unnecessary accounts
  - [ ] Remove default accounts
  - [ ] Set account lockout
  - [ ] Log account changes
  - [ ] Review periodically

**Implementation Status**: Partial
**Evidence File**: `/security/pod-security-standards.yaml`

#### 2.2.4 - Configure Services Securely
- [ ] Security configurations
  - [ ] TLS 1.3 minimum
  - [ ] Strong cipher suites
  - [ ] Disable weak protocols
  - [ ] Remove unnecessary features

**Implementation Status**: Pending

### Requirement 3 - Protect Stored Data

#### 3.2 - Render PAD Unreadable
- [ ] Encrypt stored payment data
  - [ ] Algorithm: AES-256
  - [ ] Key rotation: 90 days
  - [ ] Secure key storage
  - [ ] Encrypt backups

**Implementation Status**: Pending
**Evidence File**: `/security/security-policy.yaml`

#### 3.4 - Cryptographic Key Management
- [ ] Implement key management
  - [ ] Generate strong keys
  - [ ] Distribute securely
  - [ ] Store securely
  - [ ] Rotate regularly

- [ ] Key rotation procedures
  - [ ] Frequency: 90 days
  - [ ] Document procedures
  - [ ] Test restoration
  - [ ] Archive old keys

**Implementation Status**: Pending

### Requirement 6 - Security Updates and Patches

#### 6.2 - Security Patches and Updates
- [ ] Implement patch management
  - [ ] Patch assessment: Monthly
  - [ ] Testing: 30 days for critical, 90 days for standard
  - [ ] Documentation: All patches applied
  - [ ] Emergency patching: 7 days critical

**Implementation Status**: Pending
**Review Frequency**: Monthly

#### 6.3 - Security Testing
- [ ] Implement security testing
  - [ ] Code review: All code changes
  - [ ] Static analysis: SAST
  - [ ] Dynamic analysis: DAST
  - [ ] Penetration testing: Annually

**Implementation Status**: Pending
**Review Frequency**: Quarterly

### Requirement 7 - Restrict Access to Data

#### 7.1 - Restrict Access
- [ ] Implement access controls
  - [ ] Role-based access
  - [ ] Principle of least privilege
  - [ ] Need-to-know principle
  - [ ] Regular access reviews

**Implementation Status**: Complete
**Evidence File**: `/security/rbac-policies.yaml`

#### 7.2 - Access Provisioning
- [ ] Implement provisioning procedures
  - [ ] Formal approval process
  - [ ] Documentation
  - [ ] Job role-based access
  - [ ] Segregation of duties

**Implementation Status**: Pending

### Requirement 8 - User Identification and Authentication

#### 8.1 - User Identification
- [ ] Implement unique identification
  - [ ] All users have unique IDs
  - [ ] Service accounts identified
  - [ ] Username/ID documented
  - [ ] Automated assignment

**Implementation Status**: Complete
**Evidence File**: `/security/rbac-policies.yaml`

#### 8.2 - Strong Authentication
- [ ] Implement strong passwords
  - [ ] Minimum length: 14 characters
  - [ ] Complexity requirements
  - [ ] History: Don't reuse 12 passwords
  - [ ] Expiration: 90 days

- [ ] Implement MFA
  - [ ] Required for all users
  - [ ] Methods: OTP, hardware tokens
  - [ ] Documented procedures
  - [ ] Training provided

**Implementation Status**: Partial
**Evidence File**: `/security/security-policy.yaml`

#### 8.5 - Prevent Misuse of User IDs
- [ ] Implement session management
  - [ ] Session timeout: 30 minutes
  - [ ] Log off inactive users
  - [ ] Prevent concurrent sessions
  - [ ] Alert on multiple logins

**Implementation Status**: Pending

### Requirement 10 - Logging and Monitoring

#### 10.1 - Audit Trails
- [ ] Implement comprehensive logging
  - [ ] User access logging
  - [ ] Authentication attempts
  - [ ] Authorization changes
  - [ ] Data access/modification
  - [ ] System changes

- [ ] Log all transactions
  - [ ] Cardholder data transactions
  - [ ] Failed access attempts
  - [ ] Administrative access
  - [ ] Invalid access attempts

**Implementation Status**: Partial
**Evidence File**: `/security/security-policy.yaml`

#### 10.2 - Log Review Procedures
- [ ] Implement log review
  - [ ] Automated analysis: Real-time
  - [ ] Manual review: Daily
  - [ ] Exception handling
  - [ ] Escalation procedures

**Implementation Status**: Pending
**Review Frequency**: Daily

#### 10.3 - Log Protection
- [ ] Protect audit logs
  - [ ] Restrict access: Read-only for non-admins
  - [ ] Prevent modification
  - [ ] Verify integrity
  - [ ] Encrypt logs

**Implementation Status**: Pending

#### 10.7 - Retention of Audit History
- [ ] Maintain logs for minimum period
  - [ ] Retention: 1 year minimum
  - [ ] Online access: 3 months minimum
  - [ ] Off-site backup: Yes
  - [ ] Compliance: HIPAA 7 years

**Implementation Status**: Pending
**Retention Period**: 7 years (HIPAA requirement)

---

## Cross-Framework Controls

### Identity and Access Management
- [ ] Service accounts implemented (5 roles)
  - [ ] yawl-admin (Full cluster access)
  - [ ] yawl-developer (Limited development access)
  - [ ] yawl-viewer (Read-only access)
  - [ ] yawl-operator (Infrastructure operations)
  - [ ] yawl-security-officer (Audit and compliance)

**Implementation Status**: Complete
**Evidence File**: `/security/rbac-policies.yaml`

### Network Security
- [ ] Default deny policies implemented
  - [ ] Ingress: Default deny all
  - [ ] Egress: Default deny all
  - [ ] Allow exceptions documented
  - [ ] Network segmentation: Application, Database, Cache

**Implementation Status**: Complete
**Evidence File**: `/security/network-policies.yaml`

### Encryption
- [ ] In-transit: TLS 1.3 minimum
- [ ] At-rest: AES-256-GCM
- [ ] Key management: Cloud KMS
- [ ] Key rotation: 90 days

**Implementation Status**: Partial
**Evidence File**: `/security/security-policy.yaml`

### Audit and Logging
- [ ] Centralized logging: Yes
- [ ] Log retention: 7 years (HIPAA)
- [ ] Real-time alerting: Yes
- [ ] Log integrity: Yes

**Implementation Status**: Partial

---

## Implementation Timeline

### Phase 1 - Foundation (Weeks 1-4)
- [x] Create RBAC policies
- [x] Create network policies
- [x] Create pod security standards
- [ ] Deploy to test environment
- [ ] Conduct initial security audit

### Phase 2 - Authentication & Encryption (Weeks 5-8)
- [ ] Implement MFA
- [ ] Deploy TLS 1.3 infrastructure
- [ ] Implement AES-256-GCM encryption
- [ ] Deploy key management system
- [ ] Test encryption key rotation

### Phase 3 - Monitoring & Logging (Weeks 9-12)
- [ ] Deploy SIEM solution
- [ ] Implement centralized logging
- [ ] Deploy real-time alerting
- [ ] Establish log retention policies
- [ ] Test log integrity verification

### Phase 4 - Validation & Certification (Weeks 13-16)
- [ ] Conduct third-party audit
- [ ] Remediate findings
- [ ] Obtain compliance certification
- [ ] Document compliance artifacts
- [ ] Establish continuous monitoring

---

## Audit and Testing

### Annual Audit Procedures
1. **Risk Assessment**
   - Update threat model
   - Assess new vulnerabilities
   - Document mitigation strategies
   - Review compliance controls

2. **Security Assessment**
   - Vulnerability scanning (quarterly)
   - Penetration testing (annually)
   - Code review and static analysis
   - Configuration review

3. **Compliance Audit**
   - HIPAA audit (annually)
   - SOC2 audit (annually)
   - PCI-DSS assessment (annually)
   - Document findings and remediation

### Testing Requirements

#### Unit Testing
- 80% code coverage minimum
- Security-focused test cases
- Input validation testing
- Error handling testing

#### Integration Testing
- API security testing
- Authentication flow testing
- Authorization enforcement testing
- Data encryption verification

#### System Testing
- End-to-end security testing
- Performance under attack scenarios
- Failover and recovery testing
- Backup and restoration testing

#### Security Testing
- OWASP Top 10 assessment
- SQL injection testing
- XSS (Cross-Site Scripting) testing
- CSRF (Cross-Site Request Forgery) testing
- Broken authentication testing
- Sensitive data exposure testing

---

## Incident Response

### Incident Classification
- **Critical**: Data breach, system compromise, financial impact > $100k
- **High**: Unauthorized access, malware detection, unplanned downtime > 1 hour
- **Medium**: Failed security control, non-critical system compromise
- **Low**: Failed login attempt (< 5), warning condition

### Response Procedures
1. **Detection**: Automated alerts or manual discovery
2. **Analysis**: Determine scope and impact
3. **Containment**: Isolate affected systems
4. **Eradication**: Remove threat
5. **Recovery**: Restore normal operations
6. **Post-Incident**: Analysis and prevention

### Notification Requirements
- **HIPAA**: 60 days for breach notification
- **PCI-DSS**: 30 days for incident report
- **SOC2**: Immediate notification to stakeholders

---

## Documentation Requirements

### Required Documents
1. **Security Policy** (`/security/security-policy.yaml`)
   - Update frequency: Annually
   - Approval: Security Officer, CEO

2. **RBAC Policies** (`/security/rbac-policies.yaml`)
   - Update frequency: Quarterly or as roles change
   - Approval: Security Officer

3. **Network Policies** (`/security/network-policies.yaml`)
   - Update frequency: Quarterly or as architecture changes
   - Approval: Security Officer, Infrastructure Lead

4. **Pod Security Standards** (`/security/pod-security-standards.yaml`)
   - Update frequency: As Kubernetes updates released
   - Approval: Security Officer

5. **Compliance Checklist** (`/security/compliance-checklist.md`)
   - Update frequency: Monthly
   - Approval: Compliance Officer

6. **Risk Assessment** (To be created)
   - Update frequency: Annually
   - Approval: Security Officer, CTO

7. **Incident Response Plan** (To be created)
   - Update frequency: Annually
   - Approval: Security Officer, Legal

8. **Business Continuity Plan** (To be created)
   - Update frequency: Annually
   - Approval: IT Director

9. **Data Classification Policy** (To be created)
   - Update frequency: Annually
   - Approval: Chief Compliance Officer

10. **Access Control Procedures** (To be created)
    - Update frequency: Quarterly
    - Approval: Security Officer

### Audit Trail Requirements
- Document all changes with date, time, and person responsible
- Maintain 7-year retention for all security documentation
- Version control for all policies
- Digital signature/approval workflow

---

## Compliance Status Summary

| Framework | Overall Status | Key Controls | Gaps | Timeline |
|-----------|----------------|-------------|------|----------|
| HIPAA | 35% | RBAC, NIST Controls | Authentication, Logging, Encryption | 16 weeks |
| SOC2 | 40% | Access Controls | Monitoring, Investigation, Testing | 16 weeks |
| PCI-DSS | 30% | Network Segmentation, RBAC | Encryption, Key Management, Testing | 16 weeks |

**Last Assessment**: 2026-02-14
**Next Assessment**: 2026-05-15
**Compliance Officer**: [To Be Assigned]
**Security Officer**: [To Be Assigned]

---

## Approval and Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| CEO | | | |
| CTO | | | |
| Chief Compliance Officer | | | |
| Security Officer | | | |
| Legal Counsel | | | |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-14 | Security Team | Initial creation |

---

**Document Classification**: Internal Use Only
**Retention Period**: 7 years (HIPAA requirement)
**Last Updated**: 2026-02-14
**Next Review**: 2026-05-14
