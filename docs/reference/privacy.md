# YAWL v6.0.0 - Privacy Policy

**Effective Date**: February 20, 2026
**Last Updated**: February 20, 2026

---

## 1. Introduction

YAWL (Yet Another Workflow Language) is a workflow automation platform deployed on Google Cloud Platform (GCP). This Privacy Policy explains how YAWL handles, processes, and protects personal data in compliance with GDPR, CCPA, and other applicable regulations.

---

## 2. Data Processing Overview

### Data Controller
- **Organization**: YAWL Foundation
- **Service**: YAWL v6.0.0 Workflow Engine
- **Deployment**: Google Cloud Platform (GCP)
- **Jurisdiction**: EU-US Data Transfer (Standard Contractual Clauses)

### Data Categories Processed

| Data Type | Purpose | Retention | Legal Basis |
|-----------|---------|-----------|-------------|
| **Workflow Definitions** | Execution and auditing | 7 years | Contractual obligation |
| **Case/Process Data** | Runtime execution | Configurable (default: 1 year) | Contractual obligation |
| **User Identifiers** | Authentication & authorization | Duration of engagement | Legitimate interest |
| **Execution Logs** | Monitoring and debugging | 90 days | Legitimate interest |
| **Telemetry Data** | Performance improvement | 30 days aggregated | Legitimate interest |
| **Audit Trails** | Compliance and security | 7 years | Legal obligation |

---

## 3. GDPR Compliance

### 3.1 Legal Basis for Processing

We process personal data under the following legal bases:
- **Contract** (Article 6(1)(b)): Execution of workflow services
- **Legal Obligation** (Article 6(1)(c)): Audit, tax, and regulatory requirements
- **Legitimate Interest** (Article 6(1)(f)): Security, fraud prevention, improvement of services

### 3.2 Data Subject Rights

**You have the right to**:
- **Access** (Art. 15): Request a copy of your personal data
- **Rectification** (Art. 16): Correct inaccurate data
- **Erasure** (Art. 17): Request deletion of your data ("right to be forgotten")
- **Restrict Processing** (Art. 18): Limit how we use your data
- **Data Portability** (Art. 20): Receive your data in machine-readable format
- **Object** (Art. 21): Oppose processing for specific purposes
- **Lodge a Complaint** (Art. 77): Contact your local data protection authority

**To exercise these rights**, contact:
```
YAWL Foundation Data Protection Officer
Email: privacy@yawlfoundation.org
Mail: [HQ Address]
Response Time: Within 30 days (GDPR requirement)
```

### 3.3 Data Transfers (EU to US)

YAWL complies with EU-US data transfer mechanisms:
- **Standard Contractual Clauses (SCCs)** included in GCP Data Processing Agreement
- **Google Cloud Platform Commitments**:
  - SOC 2 Type II certification
  - ISO 27001 certification
  - GDPR Data Processing Agreement (DPA)

---

## 4. Data Retention & Deletion

### Retention Schedule

| Data | Retention Period | Deletion Method |
|------|------------------|-----------------|
| Active case data | Duration of engagement + 1 year | Secure purge (NIST guidelines) |
| Archived cases | 7 years (regulatory requirement) | Encrypted cold storage |
| Execution logs | 90 days | Automated rotation |
| Telemetry | 30 days (aggregated) | Automated purge |
| Audit trails | 7 years | Write-once, read-many (WORM) |
| User session data | Session duration + 14 days | Secure deletion |

### Right to Erasure Exception

We cannot delete data in these circumstances:
- Legal or regulatory requirements (e.g., audit logs)
- Active disputes or litigation
- Outstanding financial obligations
- Customer consent for archival

---

## 5. Security & Data Protection

### 5.1 Technical Safeguards

- **Encryption at Rest**: AES-256 via Google Cloud SQL CMEK (Customer-Managed Encryption Keys)
- **Encryption in Transit**: TLS 1.3 for all data communications
- **Access Control**: Identity-based access (IAM) with multi-factor authentication
- **Audit Logging**: All data access logged and monitored in Cloud Logging
- **Isolation**: Multi-tenant architecture with complete data isolation per customer

### 5.2 Organizational Safeguards

- Employee data access agreements and confidentiality clauses
- Regular security training (annual)
- Background checks for personnel with data access
- Incident response procedures (detected → 72-hour notification)
- Penetration testing (annually by third-party)

### 5.3 Incident Response

**In case of a data breach**:
1. Contained within 1 hour
2. Investigated within 24 hours
3. Authority notified within 72 hours (GDPR Art. 33)
4. Affected individuals notified (if high risk)

---

## 6. Data Processors & Subprocessors

### Primary Data Processors

| Service | Location | Purpose | Agreement |
|---------|----------|---------|-----------|
| **Google Cloud Platform** | US (GCP region) | Infrastructure hosting | GCP DPA |
| **Cloud SQL (MySQL)** | US | Database operations | GCP DPA + CMEK |
| **Cloud Storage (GCS)** | US | File storage | GCP DPA + encryption |
| **Cloud Logging** | US | Audit and telemetry | GCP DPA |

### Sub-processor Changes

We notify customers of sub-processor changes at least 30 days in advance via:
- Email notification to account contact
- In-service announcement
- Updated Privacy Policy

---

## 7. International Data Transfers

### EU to US Transfer Mechanisms

**Schrems II Compliance** (ECJ-2020-311):
- Standard Contractual Clauses (SCCs) with supplementary measures
- GCP commitments to resist unreasonable government access
- Customer data segregation by jurisdiction
- Optional EU-region deployment (GCP Europe-West1)

### Transfer Limitations

Customers in regulated sectors (finance, healthcare) must:
- Enable CMEK encryption with EU-based key management
- Deploy YAWL in EU regions (europe-west1, europe-west4)
- Sign Data Processing Agreement addendum

---

## 8. Third-Party Services

### Third-Party Links

This Privacy Policy does not cover third-party services linked from YAWL interfaces:
- Customer's own systems
- External databases
- Third-party APIs (Slack, Teams, etc.)

For third-party privacy, review their respective privacy policies.

---

## 9. Children's Privacy

YAWL is not directed to individuals under 16 years old. We do not knowingly collect data from children. If we become aware of such data, we will delete it immediately.

---

## 10. California Privacy Rights (CCPA/CPRA)

### CCPA Rights

- **Right to Know**: Request what personal data is collected
- **Right to Delete**: Request deletion of personal data
- **Right to Opt-Out**: Opt-out of sale/sharing of personal data
- **Right to Correct**: Request correction of inaccurate data
- **Right to Limit Use**: Limit use to service delivery

### CCPA Requests

Contact: `privacy@yawlfoundation.org`

We will:
- Verify your identity
- Respond within 45 days (CCPA deadline)
- Not discriminate for exercising CCPA rights

---

## 11. Policy Changes

We may update this Privacy Policy. Significant changes will be:
- Announced via email (15 days notice)
- Updated on this page with "Last Updated" date

Continued use of YAWL after changes constitutes acceptance.

---

## 12. Contact Information

### Questions About Privacy

**Data Protection Officer (DPO)**
- Email: privacy@yawlfoundation.org
- Response SLA: 48 business hours

**EU Representative** (per GDPR Art. 27)
- Address: [EU Representative Address]

**Regulatory Complaints**
- EU: Contact your local data protection authority
- CA: California Attorney General
- US: Consumer Review Board

---

## A. Appendix: Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                     CUSTOMER APPLICATION                      │
│                   (Your Workflow System)                       │
└────────────────────┬─────────────────────────────────────────┘
                     │ HTTPS/TLS 1.3
                     ↓
┌──────────────────────────────────────────────────────────────┐
│                    YAWL Engine (GCP)                           │
│  ┌────────────────┬──────────────┬──────────────┐             │
│  │  API Gateway   │  YEngine     │  Multi-Tenant                │
│  │  (TLS)         │  (Isolated)  │  Isolation   │             │
│  └────────────────┴──────────────┴──────────────┘             │
└────────┬────────────┬────────────┬────────────┬───────────────┘
         │            │            │            │
      [IAM]       [Logging]   [Database]   [Storage]
         │            │            │            │
         ↓            ↓            ↓            ↓
    ┌─────────┬──────────────┬──────────────┬─────────────┐
    │ Cloud   │ Cloud        │ Cloud SQL    │ Cloud       │
    │ KMS     │ Logging      │ (CMEK)       │ Storage     │
    │ (Key    │ (Audit)      │ (AES-256)    │ (AES-256)   │
    │ Mgmt)   │              │              │             │
    └─────────┴──────────────┴──────────────┴─────────────┘
         │            │            │            │
         └────────────┴────────────┴────────────┘
                      │
                      ↓
         [GCP Standard Controls]
          • ISO 27001 certified
          • SOC 2 Type II
          • HIPAA eligible
```

---

**Document Status**: APPROVED
**Version**: 1.0
**Classification**: Public
