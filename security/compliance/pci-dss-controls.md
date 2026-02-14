# PCI DSS Controls for YAWL

## Overview

This document maps YAWL security controls to Payment Card Industry Data Security Standard (PCI DSS) v4.0 requirements for organizations processing payment card data.

## PCI DSS v4.0 Requirements Mapping

### Requirement 1: Install and Maintain Network Security Controls

#### 1.1 - Processes and mechanisms for network security controls

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 1.1.1 - Document security policies | Network security policy | Policy documentation |
| 1.1.2 - Review network diagrams | Current network architecture | Architecture documentation |
| 1.1.3 - Additional procedures | Incident response procedures | IR documentation |

#### 1.2 - Network security controls (NSCs) between trusted and untrusted networks

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 1.2.1 - Firewall configurations | Network policies with default deny | `network-policies.yaml` |
| 1.2.2 - Inbound/outbound restrictions | Explicit allow rules only | Network policy configs |
| 1.2.3 - DMZ implementation | Separate PCI CDE zone | Network segmentation |
| 1.2.4 - Segregation of duties | Separate admin responsibilities | Role definitions |

#### 1.3 - Network access to and from the cardholder data environment

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 1.3.1 - Restrict inbound traffic | Explicit allow-list | Network policies |
| 1.3.2 - Restrict outbound traffic | Egress policies | Network policies |
| 1.3.3 - Personal firewall | Pod-level security | Pod security standards |

#### 1.4 - Stealth-mode technologies

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 1.4.1 - Security posture concealed | Minimal service exposure | Service configuration |
| 1.4.2 - IP filtering | Network ACLs | ACL configurations |

### Requirement 2: Apply Secure Configurations to All System Components

#### 2.1 - Configuration standards

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 2.1.1 - Vendor defaults changed | Custom security configs | Configuration files |
| 2.1.2 - Default passwords removed | No default passwords | Password policy |
| 2.1.3 - Primary functions only | Minimal container images | Container security |

#### 2.2 - System component hardening

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 2.2.1 - Configuration standards | CIS benchmarks applied | `cis-benchmarks.md` |
| 2.2.2 - Unnecessary services disabled | Minimal attack surface | Container configuration |
| 2.2.3 - Security parameters | Hardened configurations | `Dockerfile.security` |
| 2.2.4 - Required functionality only | Essential components only | Component inventory |
| 2.2.5 - Unnecessary protocols disabled | TLS 1.3 only | TLS configuration |
| 2.2.6 - System security parameters | Hardened settings | Security configs |

#### 2.3 - Cryptographic key management

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 2.3.1 - Key management procedures | Vault key management | `vault-integration.md` |
| 2.3.2 - Key storage | Secure key storage | Vault configuration |
| 2.3.3 - Key rotation | Automated key rotation | Rotation policies |

### Requirement 3: Protect Stored Account Data

#### 3.1 - Data retention policies

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 3.1.1 - Retention policy | Documented retention limits | Retention policy |
| 3.1.2 - Data disposal | Secure deletion | Disposal procedures |

#### 3.2 - Sensitive authentication data not stored

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 3.2.1 - No SAD storage | SAD never persisted | Data flow diagrams |
| 3.2.2 - No post-authorization SAD | Immediate deletion | Data handling procedures |

#### 3.3 - Mask PAN display

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 3.3.1 - PAN masking | First 6/last 4 only | Masking implementation |
| 3.3.2 - Need-to-know basis | Access controls | RBAC configuration |

#### 3.4 - Render PAN unreadable

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 3.4.1 - PAN protection | Encryption, tokenization | Encryption config |
| 3.4.2 - Hashing | Strong hashing (SHA-256+) | Hashing implementation |

#### 3.5 - Cryptographic key management

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 3.5.1 - Key procedures | Documented procedures | Key management docs |
| 3.5.2 - Secure key storage | HSM-backed storage | Vault HSM config |
| 3.5.3 - Key access | Dual control, split knowledge | Key custodian procedures |
| 3.5.4 - Key rotation | Annual key rotation | Rotation schedule |

#### 3.6 - Cryptography

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 3.6.1 - Strong cryptography | AES-256, TLS 1.3 | Cryptography config |
| 3.6.2 - Secure protocols | TLS 1.2 minimum | Protocol configuration |

### Requirement 4: Protect Cardholder Data with Strong Cryptography

#### 4.1 - Data transmission encryption

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 4.1.1 - TLS encryption | TLS 1.2+ required | TLS configuration |
| 4.1.2 - Encryption strength | Strong ciphers only | Cipher configuration |
| 4.1.3 - Certificate validity | Valid certificates | Certificate management |

#### 4.2 - Never send PANs by end-user messaging

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 4.2.1 - No PANs in messaging | Policy prohibition | Policy documentation |

### Requirement 5: Protect All Systems and Networks from Malicious Software

#### 5.1 - Malware protection

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 5.1.1 - Anti-malware deployed | Container scanning | Trivy, Grype configs |
| 5.1.2 - Malware detection | Signature-based detection | Scanner configuration |
| 5.1.3 - Automatic updates | Daily signature updates | Update configuration |

#### 5.2 - Malware mechanisms

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 5.2.1 - Detection and removal | Automated scanning | Scan results |
| 5.2.2 - Performance monitoring | Alert on failures | Alert configuration |
| 5.2.3 - Periodic evaluations | Quarterly assessments | Assessment reports |

### Requirement 6: Develop and Maintain Secure Systems and Software

#### 6.1 - Vulnerability management

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 6.1.1 - Security patches | Monthly patching cycle | Patch records |
| 6.1.2 - Critical patches | 30-day critical patching | Patch SLAs |
| 6.1.3 - Vulnerability rankings | CVSS-based ranking | Vulnerability reports |

#### 6.2 - Secure systems and software processes

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 6.2.1 - Secure development | OWASP guidelines | SDLC documentation |
| 6.2.2 - Software engineering | Secure coding standards | Coding standards |
| 6.2.3 - Removal of test data | No production data in test | Data handling procedures |
| 6.2.4 - Review of custom code | Mandatory code review | PR approval records |

#### 6.3 - Security vulnerabilities

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 6.3.1 - Identification | Automated scanning | SAST/DAST configs |
| 6.3.2 - Risk ranking | CVSS-based ranking | Vulnerability reports |
| 6.3.3 - Remediation | Patch management | Remediation records |

#### 6.4 - Public-facing web applications

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 6.4.1 - WAF deployed | Web application firewall | WAF configuration |
| 6.4.2 - WAF evaluation | Automatic updates | WAF rules |
| 6.4.3 - Attack detection | Anomaly detection | Detection rules |

### Requirement 7: Restrict Access by Business Need to Know

#### 7.1 - Access control systems

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 7.1.1 - Need-to-know basis | RBAC implementation | `kubernetes-security/rbac.yaml` |
| 7.1.2 - Access control policy | Documented policy | Access policy |
| 7.1.3 - Role-based access | Role definitions | Role matrix |

#### 7.2 - Access to system components

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 7.2.1 - Least privileges | Minimal permissions | RBAC configuration |
| 7.2.2 - Privilege assignment | Formal process | Access request records |
| 7.2.3 - Default deny | Implicit deny rules | Network policies |

### Requirement 8: Identify Users and Authenticate Access

#### 8.1 - User identification

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 8.1.1 - Unique IDs | Unique user identifiers | User management |
| 8.1.2 - Group accounts | No shared accounts | Account policy |
| 8.1.3 - Additional authentication | MFA required | MFA configuration |

#### 8.2 - User authentication

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 8.2.1 - Strong authentication | Password policy | Authentication policy |
| 8.2.2 - Password complexity | 12+ characters, complexity | Password policy |
| 8.2.3 - MFA for CDE | MFA required | MFA configuration |
| 8.2.4 - MFA for remote access | MFA required | Remote access config |
| 8.2.5 - MFA for privileged access | MFA required | Privileged access config |

#### 8.3 - Password management

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 8.3.1 - Password guidance | Strong password guidance | User documentation |
| 8.3.2 - Password changes | 90-day rotation | Password policy |
| 8.3.3 - Password history | 4-password history | Password policy |
| 8.3.4 - Account lockout | 6 attempts, 30-min lockout | Lockout configuration |
| 8.3.5 - Lockout duration | 30-minute minimum | Lockout configuration |
| 8.3.6 - Password reset | Verify identity | Reset procedures |

### Requirement 9: Restrict Physical Access

#### 9.1 - Physical access controls

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 9.1.1 - Physical security | Data center security | Facility security |
| 9.1.2 - Badge access | Badge-controlled access | Access records |
| 9.1.3 - Visitor access | Escort required | Visitor logs |

### Requirement 10: Log and Monitor Access

#### 10.1 - Audit logs

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 10.1.1 - Audit trails | Comprehensive logging | Logging configuration |
| 10.1.2 - Individual access | User-level logging | Audit logs |
| 10.1.3 - Log review | Daily log review | Review procedures |

#### 10.2 - Log entries

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 10.2.1 - User identification | User ID in logs | Log format |
| 10.2.2 - Event type | Action type logged | Log format |
| 10.2.3 - Timestamp | Accurate timestamps | Time sync config |
| 10.2.4 - Status | Success/failure logged | Log format |
| 10.2.5 - Event origin | Source identification | Log format |

#### 10.3 - Log protection

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 10.3.1 - Log integrity | Tamper-evident storage | Log protection |
| 10.3.2 - Log retention | 1-year online, 3-year archive | Retention policy |
| 10.3.3 - Log security | Access restricted | Log access controls |

### Requirement 11: Test Security Systems

#### 11.1 - Vulnerability assessments

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 11.1.1 - Quarterly scans | Quarterly external scans | Scan reports |
| 11.1.2 - Internal scans | Quarterly internal scans | Scan reports |
| 11.1.3 - Remediation | Critical fixes within 30 days | Remediation records |

#### 11.2 - External vulnerability assessments

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 11.2.1 - ASV scans | Approved Scanning Vendor | ASV reports |
| 11.2.2 - Rescan after changes | Post-change scanning | Scan records |

#### 11.3 - Penetration testing

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 11.3.1 - Annual pen testing | Annual third-party testing | Pen test reports |
| 11.3.2 - Segmentation testing | Annual segmentation tests | Test reports |
| 11.3.3 - Exploitable vulnerabilities | Fix critical issues | Remediation records |

### Requirement 12: Support Information Security

#### 12.1 - Security policy

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 12.1.1 - Information security policy | Documented policy | Policy documentation |
| 12.1.2 - Annual review | Annual policy review | Review records |

#### 12.2 - Security responsibilities

| Requirement | YAWL Implementation | Evidence |
|-------------|---------------------|----------|
| 12.2.1 - Security roles | Defined responsibilities | Role definitions |
| 12.2.2 - Incident response | IR procedures | IR documentation |

## PCI DSS Compliance Checklist

- [ ] Network security controls implemented
- [ ] Default passwords changed
- [ ] Cardholder data protected
- [ ] Encryption implemented
- [ ] Anti-malware deployed
- [ ] Secure development practices
- [ ] Access restricted to need-to-know
- [ ] Unique user IDs assigned
- [ ] Physical access restricted
- [ ] Audit logs enabled
- [ ] Security testing completed
- [ ] Information security policy published

## Quarterly Security Review Schedule

| Quarter | Activities |
|---------|------------|
| Q1 | External vulnerability scan, access review |
| Q2 | Penetration test, policy review |
| Q3 | Internal vulnerability scan, DR test |
| Q4 | ASV scan, annual assessment |
