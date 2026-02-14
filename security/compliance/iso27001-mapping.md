# ISO 27001:2022 Controls Mapping for YAWL

## Overview

This document maps YAWL security controls to ISO 27001:2022 Annex A controls. ISO 27001 is an international standard for information security management systems (ISMS).

## Control Structure

ISO 27001:2022 Annex A contains 93 controls organized into 4 themes:
- A.5 Organizational controls (37)
- A.6 People controls (8)
- A.7 Physical controls (14)
- A.8 Technological controls (34)

## A.5 Organizational Controls

### A.5.1 Policies for information security

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.1 - Information security policies | Security policy documented | Policy documentation |
| Policy review | Annual review cycle | Review records |
| Policy communication | Employee acknowledgment | Acknowledgment records |

### A.5.2 Information security roles and responsibilities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.2 - Roles and responsibilities | RACI matrix defined | Role definitions |
| Responsibility assignment | Security responsibilities assigned | Job descriptions |
| Authority levels | Decision authority documented | Authority matrix |

### A.5.3 Segregation of duties

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.3 - Segregation of duties | Conflicting duties separated | Role matrix |
| Access separation | Development/production separation | RBAC configuration |
| Authorization limits | Approval limits defined | Authorization policy |

### A.5.4 Management responsibilities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.4 - Management responsibilities | Executive accountability | Organizational chart |
| Resource allocation | Security budget allocated | Budget records |
| Policy enforcement | Compliance monitoring | Audit reports |

### A.5.5 Contact with authorities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.5 - Contact with authorities | Communication procedures | Contact procedures |
| Incident reporting | Regulatory reporting process | Reporting procedures |
| Relationship management | Designated contacts | Contact list |

### A.5.6 Contact with special interest groups

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.6 - Special interest groups | Security community membership | Membership records |
| Threat intelligence | Threat feeds subscribed | Feed subscriptions |
| Knowledge sharing | Industry forums participation | Participation records |

### A.5.7 Threat intelligence

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.7 - Threat intelligence | Threat intelligence program | TI documentation |
| Feed integration | Automated threat feeds | Feed configuration |
| Actionable intelligence | Alert integration | Alert rules |

### A.5.8 Information security in project management

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.8 - Security in projects | Project security requirements | Project methodology |
| Security assessment | Project security reviews | Review records |
| Security milestones | Security checkpoints | Project plans |

### A.5.9 Inventory of information and other associated assets

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.9 - Asset inventory | CMDB maintained | Asset register |
| Asset classification | Classification scheme applied | Classification policy |
| Ownership assignment | Asset owners assigned | Ownership records |

### A.5.10 Acceptable use of information and other associated assets

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.10 - Acceptable use policy | AUP documented | Policy documentation |
| User acknowledgment | Signed acknowledgments | Acknowledgment records |
| Enforcement | Policy enforcement | Violation records |

### A.5.11 Return of assets

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.11 - Return of assets | Return procedures | Exit procedures |
| Data removal | Secure data deletion | Disposal procedures |
| Access revocation | Immediate access removal | Termination procedures |

### A.5.12 Classification of information

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.12 - Information classification | Classification scheme | Classification policy |
| Labeling requirements | Labeling standards | Labeling procedures |
| Handling rules | Classification-based handling | Handling procedures |

### A.5.13 Labelling of information

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.13 - Information labeling | Labeling procedures | Labeling standards |
| Physical labels | Hard copy labeling | Physical security |
| Electronic labels | Metadata labeling | DLP configuration |

### A.5.14 Information transfer

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.14 - Information transfer | Transfer procedures | Transfer policy |
| Secure transmission | Encryption requirements | Encryption policy |
| Acceptable methods | Approved channels | Channel documentation |

### A.5.15 Access control

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.15 - Access control policy | Policy documented | Access policy |
| Access management | Lifecycle management | Access procedures |
| Periodic review | Quarterly reviews | Review records |

### A.5.16 Identity management

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.16 - Identity management | IAM system implemented | IAM documentation |
| Lifecycle management | Create to delete lifecycle | IAM procedures |
| Unique identities | Unique user IDs | User management |

### A.5.17 Authentication information

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.17 - Authentication info | Password management | Password policy |
| Secret management | Vault implementation | Vault configuration |
| Initial authentication | Secure provisioning | Provisioning procedures |

### A.5.18 Access rights

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.18 - Access rights | Least privilege | RBAC configuration |
| Authorization process | Approval workflow | Authorization records |
| Privileged access | PAM implementation | PAM configuration |

### A.5.19 Information security in supplier relationships

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.19 - Supplier security | Vendor assessment | Vendor policy |
| Security requirements | Contractual requirements | Contract templates |
| Ongoing monitoring | Vendor monitoring | Monitoring records |

### A.5.20 Addressing information security within supplier agreements

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.20 - Supplier agreements | Security in contracts | Contract clauses |
| SLA requirements | Security SLAs | SLA documentation |
| Right to audit | Audit clauses | Audit rights |

### A.5.21 Managing information security in the ICT supply chain

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.21 - ICT supply chain | Supply chain security | Supply chain policy |
| Supplier assessment | Third-party assessment | Assessment records |
| Dependency management | Dependency scanning | SBOM generation |

### A.5.22 Monitoring, review and change management of supplier services

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.22 - Supplier monitoring | Ongoing assessment | Monitoring procedures |
| Change notification | Supplier change process | Change management |
| Performance review | SLA monitoring | Performance reports |

### A.5.23 Information security for use of cloud services

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.23 - Cloud security | Cloud security policy | Cloud policy |
| Provider selection | Provider assessment | Assessment records |
| Shared responsibility | Responsibility matrix | RACI documentation |

### A.5.24 Information security incident management planning and preparation

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.24 - Incident planning | IR plan documented | IR playbook |
| Response capability | Incident response team | Team roster |
| Tools and resources | IR tooling | Tool inventory |

### A.5.25 Assessment and decision on information security events

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.25 - Event assessment | Event classification | Classification matrix |
| Triage process | Triage procedures | Triage documentation |
| Escalation paths | Escalation procedures | Escalation matrix |

### A.5.26 Response to information security incidents

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.26 - Incident response | Response procedures | IR procedures |
| Containment | Containment procedures | Containment playbook |
| Evidence preservation | Forensic procedures | Forensic guidelines |

### A.5.27 Learning from information security incidents

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.27 - Lessons learned | Post-incident review | PIR procedures |
| Improvement actions | Corrective actions | Action tracking |
| Knowledge base | Incident database | Incident records |

### A.5.28 Collection of evidence

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.28 - Evidence collection | Forensic procedures | Forensic procedures |
| Chain of custody | Evidence handling | Custody procedures |
| Admissibility | Legal requirements | Legal guidelines |

### A.5.29 Information security during disruption

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.29 - Business continuity | BC planning | BCP documentation |
| Critical processes | Process prioritization | Priority matrix |
| Recovery procedures | Recovery plans | Recovery documentation |

### A.5.30 ICT readiness for business continuity

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.30 - ICT continuity | ICT resilience | Continuity planning |
| Redundancy | System redundancy | Architecture docs |
| Testing | BC testing | Test records |

### A.5.31 Legal, statutory, regulatory and contractual requirements

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.31 - Legal requirements | Compliance register | Compliance matrix |
| Requirement tracking | Requirement mapping | Mapping documentation |
| Compliance monitoring | Ongoing compliance | Compliance reports |

### A.5.32 Intellectual property rights

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.32 - IP rights | IP policy | IP documentation |
| License management | License tracking | License inventory |
| Compliance | License compliance | Compliance records |

### A.5.33 Protection of records

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.33 - Record protection | Records management | Retention policy |
| Retention periods | Retention schedule | Schedule documentation |
| Secure storage | Secure archival | Storage configuration |

### A.5.34 Privacy and protection of PII

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.34 - Privacy protection | Privacy policy | Privacy documentation |
| PII inventory | PII mapping | Data inventory |
| Data subject rights | Rights procedures | Rights documentation |

### A.5.35 Independent review of information security

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.35 - Independent review | Internal audit | Audit schedule |
| External assessment | Third-party review | Assessment reports |
| Management review | Annual review | Review records |

### A.5.36 Compliance with policies for information security

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.36 - Policy compliance | Compliance monitoring | Monitoring configuration |
| Non-compliance handling | Remediation process | Remediation records |
| Metrics | Compliance metrics | Dashboard |

### A.5.37 Documented operating procedures

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 5.37 - Operating procedures | Procedure documentation | Procedure library |
| Change control | Document control | Version control |
| Access to procedures | Procedure access | Access control |

## A.6 People Controls

### A.6.1 Screening

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.1 - Background verification | Pre-employment screening | Screening procedures |
| Verification criteria | Screening requirements | Screening policy |
| Re-screening | Periodic verification | Re-screening schedule |

### A.6.2 Terms and conditions of employment

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.2 - Employment terms | Security clauses in contracts | Employment contracts |
| Post-employment | Post-termination obligations | Exit procedures |
| Responsibilities | Employee responsibilities | Job descriptions |

### A.6.3 Information security awareness, education and training

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.3 - Security training | Training program | Training curriculum |
| Role-based training | Specialized training | Training matrix |
| Awareness campaigns | Regular awareness | Campaign records |

### A.6.4 Disciplinary process

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.4 - Disciplinary procedure | Policy documented | Disciplinary policy |
| Escalation process | Escalation matrix | Escalation procedures |
| Documentation | Incident documentation | Case records |

### A.6.5 Responsibilities after termination

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.5 - Post-termination | Exit obligations | Exit procedures |
| Asset return | Return process | Return records |
| Access termination | Access revocation | Termination checklist |

### A.6.6 Confidentiality or non-disclosure agreements

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.6 - NDA requirements | NDA policy | NDA documentation |
| Agreement signing | Signed NDAs | NDA records |
| Ongoing obligations | Continuing duties | Obligation documentation |

### A.6.7 Remote working

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.7 - Remote work | Remote work policy | Policy documentation |
| Security controls | Remote access controls | VPN/Access configuration |
| Training | Remote work training | Training records |

### A.6.8 Information security event reporting

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 6.8 - Event reporting | Reporting procedures | Reporting policy |
| Reporting channels | Multiple channels | Channel documentation |
| No blame culture | Non-punitive reporting | Culture documentation |

## A.7 Physical Controls

### A.7.1 Physical security perimeters

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.1 - Physical perimeters | Physical security | Facility security |
| Access points | Controlled entry | Entry procedures |
| Monitoring | CCTV surveillance | Surveillance records |

### A.7.2 Physical entry

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.2 - Physical entry | Access control | Badge system |
| Visitor management | Visitor procedures | Visitor logs |
| Authorization | Entry authorization | Authorization records |

### A.7.3 Securing offices, rooms and facilities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.3 - Secure areas | Physical security | Area designation |
| Access restrictions | Limited access | Access control |
| Security measures | Additional controls | Security measures |

### A.7.4 Physical security monitoring

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.4 - Monitoring | Surveillance systems | CCTV coverage |
| Intrusion detection | Alarm systems | Alarm configuration |
| Patrols | Security patrols | Patrol records |

### A.7.5 Protecting against physical threats

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.5 - Physical threats | Threat protection | Risk assessment |
| Environmental controls | HVAC, fire suppression | Environmental controls |
| Natural disasters | DR planning | DR documentation |

### A.7.6 Working in secure areas

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.6 - Secure area work | Work procedures | Procedure documentation |
| Restrictions | Activity restrictions | Restriction policy |
| Supervision | Monitoring | Supervision records |

### A.7.7 Clear desk and clear screen

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.7 - Clear desk | Policy documented | Clear desk policy |
| Screen locking | Automatic lockout | Screen lock config |
| Storage | Secure storage | Storage provision |

### A.7.8 Equipment siting and protection

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.8 - Equipment siting | Proper placement | Site documentation |
| Environmental protection | Environmental controls | Control measures |
| Power supply | UPS/generators | Power infrastructure |

### A.7.9 Security of assets off-premises

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.9 - Off-premises | Off-site policy | Policy documentation |
| Authorization | Approval required | Authorization records |
| Protection | Safeguards | Protection measures |

### A.7.10 Storage media

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.10 - Media handling | Media policy | Policy documentation |
| Storage requirements | Secure storage | Storage procedures |
| Disposal | Secure destruction | Disposal procedures |

### A.7.11 Supporting utilities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.11 - Utilities | Utility management | Utility documentation |
| Power protection | UPS/generators | Power systems |
| HVAC | Climate control | HVAC documentation |

### A.7.12 Cabling security

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.12 - Cabling | Cable management | Cabling standards |
| Physical protection | Conduit/trays | Physical measures |
| Labeling | Cable identification | Labeling standards |

### A.7.13 Equipment maintenance

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.13 - Maintenance | Maintenance program | Maintenance schedule |
| Authorized personnel | Qualified staff | Qualification records |
| Records | Maintenance logs | Maintenance records |

### A.7.14 Secure disposal or re-use of equipment

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 7.14 - Disposal | Disposal procedures | Disposal policy |
| Data removal | Secure wiping | Wiping procedures |
| Certification | Disposal certificates | Certificate records |

## A.8 Technological Controls

### A.8.1 User endpoint devices

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.1 - Endpoint devices | Device management | Device policy |
| Encryption | Full disk encryption | Encryption config |
| Patching | Automated updates | Patch management |

### A.8.2 Privileged access rights

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.2 - Privileged access | PAM implementation | PAM configuration |
| Just-in-time access | JIT provisioning | JIT procedures |
| Session recording | Activity recording | Recording config |

### A.8.3 Information access restriction

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.3 - Access restriction | RBAC implementation | RBAC configuration |
| Least privilege | Minimal permissions | Permission matrix |
| Access reviews | Periodic reviews | Review records |

### A.8.4 Access to source code

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.4 - Source code access | Repository controls | Repository config |
| Code review | Mandatory reviews | Review procedures |
| Branch protection | Protected branches | Branch policies |

### A.8.5 Secure authentication

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.5 - Authentication | MFA implementation | MFA configuration |
| Password policy | Strong passwords | Password policy |
| Session management | Secure sessions | Session config |

### A.8.6 Capacity management

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.6 - Capacity | Resource monitoring | Monitoring config |
| Forecasting | Capacity planning | Planning documentation |
| Auto-scaling | Automatic scaling | Scaling configuration |

### A.8.7 Protection against malware

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.7 - Malware protection | Endpoint protection | AV configuration |
| Container scanning | Image scanning | Scanner configuration |
| Network protection | IDS/IPS | Network security |

### A.8.8 Management of technical vulnerabilities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.8 - Vulnerability mgmt | Vulnerability scanning | Scanner config |
| Patching | Timely patching | Patch procedures |
| Risk acceptance | Exception process | Exception records |

### A.8.9 Configuration management

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.9 - Configuration | IaC implementation | IaC codebase |
| Baselines | Hardened baselines | Baseline documentation |
| Change control | Version control | Change procedures |

### A.8.10 Information deletion

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.10 - Data deletion | Deletion procedures | Deletion policy |
| Secure erasure | Cryptographic erasure | Erasure methods |
| Verification | Deletion confirmation | Verification records |

### A.8.11 Data masking

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.11 - Data masking | Masking implementation | Masking config |
| Non-production | Test data masking | Masking procedures |
| De-identification | Anonymization | Anonymization methods |

### A.8.12 Data leakage prevention

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.12 - DLP | DLP implementation | DLP configuration |
| Monitoring | Content inspection | Inspection rules |
| Response | Alert handling | Response procedures |

### A.8.13 Information backup

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.13 - Backups | Backup implementation | Backup config |
| Encryption | Encrypted backups | Encryption config |
| Testing | Restore testing | Test records |

### A.8.14 Redundancy of information processing facilities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.14 - Redundancy | HA implementation | Architecture docs |
| Failover | Automatic failover | Failover config |
| DR site | Secondary site | DR documentation |

### A.8.15 Logging

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.15 - Logging | Centralized logging | Logging config |
| Log retention | Retention policy | Retention config |
| Log protection | Log integrity | Integrity controls |

### A.8.16 Monitoring activities

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.16 - Monitoring | SIEM implementation | SIEM configuration |
| Alerting | Automated alerts | Alert rules |
| Anomaly detection | Behavioral analysis | Detection rules |

### A.8.17 Clock synchronization

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.17 - Time sync | NTP implementation | NTP configuration |
| Time source | Trusted time source | Source documentation |
| Accuracy | Sync verification | Verification records |

### A.8.18 Use of privileged utility programs

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.18 - Utilities | Utility controls | Utility policy |
| Authorization | Need-to-use basis | Authorization records |
| Logging | Usage logging | Log records |

### A.8.19 Installation of software on operational systems

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.19 - Software install | Change management | Change procedures |
| Authorization | Approval required | Approval records |
| Inventory | Software inventory | Inventory records |

### A.8.20 Networks security

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.20 - Network security | Network controls | Network policies |
| Segmentation | Network segmentation | Segmentation config |
| Monitoring | Network monitoring | Monitoring config |

### A.8.21 Security of network services

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.21 - Network services | Service security | Service configuration |
| Agreements | SLA requirements | SLA documentation |
| Monitoring | Service monitoring | Monitoring records |

### A.8.22 Segregation of networks

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.22 - Network segregation | VLANs/subnets | Segregation config |
| Access control | Inter-zone controls | Access controls |
| Monitoring | Traffic monitoring | Monitoring config |

### A.8.23 Web filtering

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.23 - Web filtering | Proxy implementation | Proxy configuration |
| Category blocking | Content filtering | Filter rules |
| Logging | Access logging | Log records |

### A.8.24 Use of cryptography

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.24 - Cryptography | Encryption policy | Encryption policy |
| Key management | Key lifecycle | Key management |
| Algorithm selection | Approved algorithms | Algorithm standards |

### A.8.25 Secure development life cycle

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.25 - SDLC | Secure development | SDLC documentation |
| Security gates | Phase reviews | Gate checklist |
| Training | Developer training | Training records |

### A.8.26 Application security requirements

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.26 - App security | Security requirements | Requirements docs |
| Threat modeling | Threat analysis | Threat model docs |
| Secure design | Design principles | Design standards |

### A.8.27 Secure system architecture and engineering principles

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.27 - Architecture | Secure architecture | Architecture docs |
| Design patterns | Security patterns | Pattern library |
| Zero trust | Zero trust model | ZT implementation |

### A.8.28 Secure coding

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.28 - Secure coding | Coding standards | Coding guidelines |
| Code review | Peer review | Review records |
| SAST | Static analysis | SAST configuration |

### A.8.29 Security testing in development and acceptance

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.29 - Security testing | Testing program | Test documentation |
| DAST | Dynamic testing | DAST configuration |
| Penetration testing | Third-party testing | Pen test reports |

### A.8.30 Outsourced development

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.30 - Outsourced dev | Vendor management | Vendor policy |
| Security requirements | Contractual terms | Contract clauses |
| Code review | Third-party review | Review procedures |

### A.8.31 Separation of development, test and production environments

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.31 - Environment separation | Environment isolation | Environment config |
| Access control | Environment access | Access controls |
| Data separation | No production data | Data handling |

### A.8.32 Change management

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.32 - Change mgmt | Change process | Change procedures |
| Authorization | Change approval | Approval records |
| Rollback | Rollback procedures | Rollback capability |

### A.8.33 Test information

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.33 - Test data | Test data management | Data procedures |
| Production data | No production data in test | Data policy |
| Sanitization | Data cleansing | Sanitization procedures |

### A.8.34 Protection of information systems during audit testing

| Control | YAWL Implementation | Evidence |
|---------|---------------------|----------|
| 8.34 - Audit protection | Audit procedures | Audit procedures |
| Access control | Audit access | Access controls |
| Monitoring | Audit monitoring | Monitoring records |

## Statement of Applicability Template

| Control | Applicability | Justification | Implementation Status |
|---------|---------------|---------------|----------------------|
| 5.1 | Applicable | Core requirement | Implemented |
| 5.2 | Applicable | Core requirement | Implemented |
| ... | ... | ... | ... |

## ISMS Documentation Requirements

- [ ] Information Security Policy
- [ ] Scope of ISMS
- [ ] Risk Assessment Methodology
- [ ] Risk Treatment Plan
- [ ] Statement of Applicability
- [ ] Procedures and Controls
- [ ] Records of Activities
- [ ] Internal Audit Results
- [ ] Management Review Results
