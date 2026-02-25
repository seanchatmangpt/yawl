# YAWL v6.0.0 - Data Processing Agreement (DPA)

**Effective Date**: February 20, 2026
**Last Updated**: February 20, 2026
**Applies To**: EU customers and GDPR-regulated data

---

## 1. Introduction

This Data Processing Agreement (DPA) forms part of the YAWL Terms of Service and applies to the processing of personal data by YAWL on behalf of customers. This DPA is compliant with GDPR Articles 28 and 44-49.

---

## 2. Definitions

| Term | Definition |
|------|-----------|
| **GDPR** | Regulation (EU) 2016/679 |
| **Data Controller** | Customer (organization ordering YAWL services) |
| **Data Processor** | YAWL Foundation (service provider) |
| **Personal Data** | Any data relating to identified/identifiable natural person |
| **Processing** | Any operation on personal data (collection, storage, use, deletion) |
| **Data Subject** | Natural person to whom personal data relates |
| **Sub-processor** | Third party processing data on behalf of YAWL |

---

## 3. Scope of Processing

### 3.1 Data Categories

YAWL processes the following personal data categories on behalf of customers:

| Category | Examples | Retention |
|----------|----------|-----------|
| **Identifiers** | User ID, email, username | Duration of engagement |
| **Process Data** | Case variables, document content | As configured (default: 1 year) |
| **System Data** | IP address, user agent, timestamps | 90 days |
| **Audit Data** | Who did what, when (logs) | 7 years |
| **Device Data** | Browser info, device fingerprints | Session duration |

### 3.2 Processing Purposes

YAWL processes personal data for:
1. Delivery of workflow automation services
2. Performance monitoring and optimization
3. Security and fraud prevention
4. Compliance with legal obligations
5. Audit and incident investigation

### 3.3 Duration of Processing

- **Initial Period**: Duration of service subscription
- **Extended**: 30 days after termination (for data deletion/export)
- **Archived**: Legal/regulatory requirements (up to 7 years)

---

## 4. Roles & Responsibilities

### 4.1 Data Controller Responsibilities

The Customer (Data Controller) shall:

- **Lawfulness**: Ensure processing has valid legal basis under GDPR Art. 6
- **Transparency**: Inform data subjects via Privacy Policy
- **Consent**: Obtain consent where required
- **Data Subject Rights**: Implement procedures to handle access/deletion/portability requests
- **Impact Assessments**: Conduct DPIA for high-risk processing
- **Breach Notification**: Notify YAWL of suspected breaches immediately
- **Subprocessor Approval**: Approve YAWL's use of subprocessors

### 4.2 Data Processor Responsibilities

YAWL (Data Processor) shall:

- **Instructions Only**: Process data only per controller's written instructions
- **Confidentiality**: Ensure personnel are bound by confidentiality
- **Security**: Implement technical/organizational security measures
- **Sub-processor Management**: Maintain list of subprocessors, notify of changes
- **Data Subject Requests**: Assist controller in responding to subject rights requests
- **Audit Rights**: Provide audits, certifications (SOC 2, ISO 27001)
- **Data Breach**: Notify controller within 24 hours of discovery
- **Data Return/Deletion**: Upon termination, return or securely delete personal data

---

## 5. Processing Instructions

### 5.1 Authorized Processing

Customer authorizes YAWL to process personal data for:

1. **Service Delivery**:
   - Workflow execution and state management
   - Case data storage and retrieval
   - User authentication and authorization
   - API logging and access auditing

2. **System Operations**:
   - Performance monitoring (anonymized/aggregated)
   - Backup and disaster recovery
   - Security threat detection
   - System maintenance and patching

3. **Legal Compliance**:
   - Audit trail maintenance
   - Regulatory reporting (if required)
   - Law enforcement cooperation (with court order)

### 5.2 Prohibited Processing

YAWL shall NOT:
- Process personal data for marketing/profiling (of customers' customers)
- Sell or license personal data to third parties
- Use personal data for product development without anonymization
- Process for purposes outside the scope of service delivery

### 5.3 Changes to Instructions

- **Customer-Initiated**: Customer may modify instructions with 30 days' notice
- **YAWL-Initiated**: Only with controller's prior written consent
- **Compliance-Required**: YAWL may adjust if required by law

---

## 6. Subprocessors & Data Transfers

### 6.1 Authorized Subprocessors

YAWL uses the following subprocessors for personal data processing:

| Subprocessor | Purpose | Location | Agreement |
|---|---|---|---|
| **Google Cloud Platform** | Infrastructure hosting | US (GCP regions) | GCP DPA + SCCs |
| **Cloud SQL** | Database operations | US | GCP DPA + CMEK |
| **Cloud Storage** | File/backup storage | US | GCP DPA |
| **Cloud Logging** | Audit logging | US | GCP DPA |
| **Datadog** | Monitoring (aggregated) | US | Data Processing Agreement |

### 6.2 Subprocessor Authorization

- **Original List**: Current subprocessors listed above (approved)
- **New Subprocessors**: YAWL provides 30 days' notice before adding
- **Objection Right**: Customer may object within 15 days; if unresolved, may terminate

### 6.3 International Data Transfers (EU ↔ US)

**Legal Mechanism**: Standard Contractual Clauses (SCCs) + supplementary measures

**GCP Commitments** (Schrems II compliance):
- Resistance to government surveillance requests
- Data encryption with customer-managed keys (CMEK)
- Data segregation by jurisdiction
- Commitment to challenge unlawful government access

**Customer Options**:
- **Default**: EU data processed in GCP US regions (with SCCs)
- **Enhanced**: Use EU-only regions (GCP Europe-West1, Europe-West4)
- **CMEK**: Enable customer-managed encryption keys (in EU)

**Transfer Impact Assessment**: YAWL maintains Transfer Impact Assessment (TIA) available on request.

---

## 7. Data Subject Rights Support

### 7.1 Right of Access (GDPR Art. 15)

**Customer Request**: Data subject requests copy of their personal data
**YAWL's Role**:
- Retrieve data from systems within 10 business days
- Provide to controller in structured format
- Controller responds directly to data subject

**Available Data**:
- Case/process data (in workflow format)
- Audit logs (what actions affected the data subject)
- System logs (minimal PII)
- Excluded: Metadata, backup files, aggregate analytics

### 7.2 Right to Rectification (GDPR Art. 16)

**Customer Request**: Correct inaccurate data
**YAWL's Role**:
- Provide tools for customer to modify data
- Or assist customer in corrections
- Support versioning/audit trail

### 7.3 Right to Erasure (GDPR Art. 17 - "Right to be Forgotten")

**Customer Request**: Delete data subject's personal data
**YAWL's Role**:
- Delete from active systems within 30 days
- Remove from backups on next scheduled rotation
- Retain audit trail (minimal: "deleted on DATE by USER_ID")
- Not required if: Legal obligation, active contract, legitimate interest established

**Limitations**:
- Cannot delete archived data (legal requirement)
- Cannot delete if disputes/litigation ongoing
- Cannot delete financial records

### 7.4 Right to Data Portability (GDPR Art. 20)

**Customer Request**: Receive data in machine-readable format
**YAWL's Role**:
- Export case data in XML/JSON within 10 days
- Structured, commonly-used format
- Directly to data subject if technically feasible
- No fee for export

### 7.5 Right to Object (GDPR Art. 21)

**Customer Request**: Stop processing for specific purposes
**YAWL's Role**:
- Halt processing for non-essential purposes
- Maintain for service delivery (contractual obligation)
- Maintain for legal requirements

### 7.6 Rights Related to Automated Decision-Making (GDPR Art. 22)

YAWL does NOT perform:
- Automated decision-making affecting data subjects
- Profiling with legal/significant effect
- Sole reliance on automated processing

---

## 8. Security Measures

### 8.1 Technical Safeguards

YAWL implements the following technical measures:

**Encryption**:
- At Rest: AES-256 encryption via Google Cloud SQL CMEK
- In Transit: TLS 1.3 for all data communications
- Key Management: Secure key rotation (annual)

**Access Control**:
- Identity-based access (IAM)
- Multi-factor authentication (MFA)
- Role-based access control (RBAC)
- Principle of least privilege
- Log all access attempts

**Network Security**:
- Private network by default (VPC)
- DDoS protection (GCP native)
- WAF (Web Application Firewall) enabled
- No public internet access without authorization

**Data Isolation**:
- Multi-tenant architecture
- Complete isolation between customers
- No data mixing or leakage
- Separate encryption keys per customer

**Backup & Recovery**:
- Automated hourly snapshots
- Geographic redundancy (3+ regions)
- 30-day retention, tested monthly
- Air-gap backup (offline copy)

### 8.2 Organizational Safeguards

YAWL implements the following organizational measures:

**Personnel**:
- Background checks for all staff
- Confidentiality agreements (NDA)
- Annual data protection training
- Role-based access (only as needed)

**Incident Response**:
- 24/7 security operations center
- Incident response plan (documented)
- Breach notification (within 24 hours)
- Post-incident review & remediation

**Vulnerability Management**:
- Monthly penetration testing
- Automated vulnerability scanning
- 48-hour patch SLA for critical
- Annual third-party security audit

**Monitoring & Audit**:
- Real-time security monitoring
- Cloud Logging for all API calls
- Automated alerts for anomalies
- Quarterly access reviews

### 8.3 Standard of Care

YAWL's security measures meet or exceed:
- ISO 27001 standards
- NIST Cybersecurity Framework
- CIS Benchmarks
- SOC 2 Type II requirements

---

## 9. Audit & Compliance Verification

### 9.1 Customer Audit Rights

Customer may:

1. **Request Evidence**:
   - SOC 2 Type II report (annual)
   - ISO 27001 certification
   - Penetration test results (executive summary)
   - Incident reports (if related to customer data)

2. **On-Site Audit**: Upon request (with 30 days' notice)
   - Reasonable frequency (annual)
   - Scope limited to data protection
   - Cost borne by customer
   - NDA required for auditors

3. **Regulatory Audit**: Support data protection authority audits

### 9.2 Documentation & Certification

YAWL maintains:
- **Record of Processing Activities** (Art. 30) - updated quarterly
- **Privacy by Design Documentation** - for all systems
- **Data Protection Impact Assessments** (DPIA) - for new processing
- **Breach Register** - tracked incidents

### 9.3 Compliance Certifications

- **SOC 2 Type II**: Annual attestation (current year available)
- **ISO 27001**: Certification # [GCP-inherited]
- **GDPR DPA**: This document + GCP DPA
- **Schrems II**: TIA + supplementary measures documented

---

## 10. Data Breach Notification

### 10.1 Breach Detection & Response

| Phase | Timeline | Action |
|-------|----------|--------|
| **Detection** | Real-time | YAWL detects unauthorized access/disclosure |
| **Initial Response** | < 1 hour | Isolation of affected systems, preserve evidence |
| **Investigation** | < 24 hours | Determine scope, impact, root cause |
| **Notification** | < 24 hours | Contact customer with breach details |

### 10.2 Breach Information Provided

YAWL shall notify customer of:
- Nature and scope of breach
- Personal data categories affected
- Individuals likely affected (if known)
- Likely consequences
- Measures taken to mitigate
- Contact person for more information

### 10.3 Customer Notification Responsibility

Customer (as controller) is responsible for:
- Notifying data subjects within 72 hours (if high risk)
- Notifying regulators (if required)
- Public disclosure (if required)
- YAWL provides support for customer notifications

---

## 11. Data Return & Deletion

### 11.1 End of Service

Upon termination of service agreement, YAWL shall:

**Within 30 Days**:
- Provide complete data export in XML/JSON format
- At no additional cost
- Assistance with data import to new system
- Deletion of active personal data
- Deletion from backup systems within next rotation

**After 30 Days**:
- Permanently delete all personal data
- Secure deletion (NIST guidelines)
- Certification of deletion provided
- Exception: Audit logs (minimal format) retained 7 years

### 11.2 Data Retention Options

Customer may request:
- **Immediate Deletion**: Purge within 24 hours (backup loss acceptable)
- **Delayed Deletion**: Standard 30-day window
- **Extended Retention**: For legal/regulatory reasons (max 7 years)

---

## 12. Sub-processor Changes

### 12.1 Notification Process

YAWL shall:
1. **Announce**: Provide 30 days' notice of new subprocessor
2. **Information**: Name, location, data types processed, purpose
3. **Opportunity to Object**: Customer may object within 15 days
4. **Unresolved Objection**: Customer may terminate without penalty

### 12.2 Current Subprocessor List

[See Section 6.1 above]

### 12.3 Subprocessor Oversight

YAWL ensures all subprocessors:
- Sign Data Processing Agreements
- Maintain equivalent security measures
- Comply with EU/US transfer restrictions
- Are subject to audit rights

---

## 13. DPA Amendments & Addenda

### 13.1 Special Requirements

Customers in regulated sectors may request addenda:

**Healthcare** (HIPAA):
- Business Associate Agreement (BAA)
- Additional security requirements
- Breach notification SLA
- Audit trail retention (10 years)

**Finance** (PCI-DSS, SOX):
- Enhanced encryption requirements
- Financial data segregation
- Regulatory audit support
- Compliance attestations

**Government** (FedRAMP):
- Government systems authorization
- Security assessment requirements
- Incident response procedures

### 13.2 Amendment Process

- **Requested By**: Customer or regulator
- **Review Period**: 15 days
- **Negotiation**: Good-faith discussion
- **Implementation**: Effective upon signature

---

## 14. Governing Law & Dispute Resolution

### 14.1 Governing Law

This DPA is governed by:
- **Primary**: Laws of Customer's jurisdiction (if EU: GDPR)
- **Supplementary**: Laws of service delivery location
- **International**: GDPR and Standard Contractual Clauses

### 14.2 Dispute Resolution

1. **Informal Resolution** (30 days)
   - Good-faith negotiation between data protection officers

2. **Mediation** (60 days)
   - Non-binding mediation by independent mediator

3. **Regulatory Escalation**
   - Customer may file complaint with data protection authority
   - YAWL cooperates with regulatory investigations

4. **Legal Action**
   - GDPR claims per Article 82 (controller & processor liability)
   - Jurisdiction: Customer's country (if EU)

---

## 15. Definitions Specific to DPA

| Term | Definition |
|------|-----------|
| **Standard Contractual Clauses (SCCs)** | EU-approved transfer mechanism (Commission Decision 2021/914) |
| **Schrems II** | ECJ ruling requiring supplementary measures for EU-US transfers |
| **DPIA** | Data Protection Impact Assessment (Art. 35) |
| **DPO** | Data Protection Officer |
| **Lawful Basis** | Legal foundation for processing (Art. 6: contract, consent, legal obligation, vital interest, public task, legitimate interest) |
| **High-Risk Processing** | Processing requiring DPIA (e.g., large-scale, automated decision-making, sensitive data) |

---

## 16. Contact Information

**YAWL Data Protection Officer (DPO)**
- Email: dpo@yawlfoundation.org
- Response SLA: 48 business hours
- Subject Access Requests: GDPR Art. 15-22
- Breach Notifications: GDPR Art. 33

**Regulatory Authorities** (for complaints):
- **Germany**: Bundesdatenschutzbeamte (BfDI)
- **France**: CNIL
- **Ireland**: Data Protection Commission
- **EU**: Local Data Protection Authority

---

## 17. Annexes

### Annex A: Standard Contractual Clauses (SCCs)

[GCP provides SCCs in their DPA; reference below]

**For EU-US transfers**, the following SCCs apply:
- Module One: Controller → Processor
- Module Three: Processor → Subprocessor
- Supplementary measures per Schrems II

**Reference**: GCP Data Processing Amendment
- URL: https://cloud.google.com/terms/data-processing-terms
- Version: Current (as updated)

### Annex B: Subprocessor List

[See Section 6.1; updated quarterly]

### Annex C: Security Measures Summary

[GCP Cloud Security Whitepaper + YAWL-specific measures]

---

**Document Status**: APPROVED
**Version**: 1.0
**Last Review**: February 20, 2026
**Next Review**: February 20, 2027
**Classification**: Confidential (customers only)

---

**GDPR Compliance Statement**:

> YAWL complies with GDPR requirements for data processing as a Processor (Art. 28) and respects all Data Subject Rights (Arts. 15-22). This DPA establishes the legal framework for lawful processing under GDPR and EU-US transfer mechanisms (SCCs + supplementary measures).
