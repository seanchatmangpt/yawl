# Oracle Cloud Infrastructure (OCI) Marketplace Checklist

**Product:** YAWL Workflow Engine v5.2
**Marketplace:** Oracle Cloud Marketplace
**Last Updated:** 2025-02-13

---

## 1. Publisher Registration

### 1.1 Oracle PartnerNetwork (OPN)

- [ ] Oracle PartnerNetwork membership active
- [ ] Partner level: Silver / Gold / Platinum
- [ ] Cloud Excellence Implementer status (if applicable)
- [ ] Tax documentation submitted
- [ ] Payment information configured

### 1.2 Marketplace Agreement

- [ ] Oracle Cloud Marketplace Distribution Agreement signed
- [ ] Oracle Terms of Use accepted
- [ ] Revenue share terms acknowledged
- [ ] Data Processing Agreement signed (if applicable)

---

## 2. Technical Requirements

### 2.1 Oracle Cloud Infrastructure Images

- [ ] Custom image created in OCI
- [ ] Image published to all required regions
  - [ ] Ashburn (us-ashburn-1)
  - [ ] Phoenix (us-phoenix-1)
  - [ ] Frankfurt (eu-frankfurt-1)
  - [ ] London (eu-london-1)
  - [ ] Amsterdam (eu-amsterdam-1)
  - [ ] Zurich (eu-zurich-1)
  - [ ] Tokyo (ap-tokyo-1)
  - [ ] Seoul (ap-seoul-1)
  - [ ] Sydney (ap-sydney-1)
  - [ ] Mumbai (ap-mumbai-1)
  - [ ] Singapore (ap-singapore-1)
  - [ ] Sao Paulo (sa-saopaulo-1)
  - [ ] Dubai (me-dubai-1)
- [ ] Image OCID: ocid1.image.oc1.._________
- [ ] Image scan completed (0 critical vulnerabilities)

### 2.2 Image Requirements

- [ ] Oracle Linux 8/9 base or Ubuntu 22.04
- [ ] Cloud-init compatible
- [ ] OCI CLI installed (optional)
- [ ] Proper shutdown handling
- [ ] No hardcoded credentials
- [ ] SSH key injection support

### 2.3 Kubernetes (OKE)

- [ ] Helm chart validated for OKE
- [ ] OKE versions tested: 1.28, 1.29
- [ ] OCI VCN Native Pod Networking compatible
- [ ] OCI Load Balancer integration

### 2.4 Terraform (Oracle Resource Manager)

- [ ] Terraform stack prepared
- [ ] Published to Oracle Cloud Marketplace
- [ ] Stack follows Oracle best practices
- [ ] Variables documented
- [ ] Compatible with Resource Manager

---

## 3. OCI Service Integration

### 3.1 Core Services

| Service | Integration Type | Status |
|---------|-----------------|--------|
| Autonomous Database | Database | Pending |
| Base Database Service | Database | Pending |
| MySQL HeatWave | Database | Pending |
| Oracle NoSQL | Database | Pending |
| OCI Object Storage | Object Storage | Pending |
| OCI Streaming | Messaging | Pending |
| OCI Queue | Messaging | Pending |
| OCI Vault | Secrets | Pending |
| OCI Identity | Identity | Pending |
| OCI Monitoring | Metrics | Pending |
| OCI Logging | Logging | Pending |
| OCI Notifications | Alerts | Pending |
| OCI API Gateway | API Management | Pending |

### 3.2 Identity & Access Management

- [ ] IAM policy templates provided
- [ ] Dynamic group definitions
- [ ] Service gateway support documented
- [ ] Instance principal support

### 3.3 Networking

- [ ] VCN deployment supported
- [ ] Private subnet support
- [ ] Service Gateway integration
- [ ] NAT Gateway compatible
- [ ] FastConnect compatible
- [ ] Load Balancer integration

---

## 4. Security Requirements

### 4.1 Security Validation

- [ ] Oracle Cloud Guard scan passed
- [ ] Vulnerability scanning enabled
- [ ] Container registry scan (if applicable)
- [ ] No critical CVEs in image
- [ ] Security Zone compatible

### 4.2 Security Features

- [ ] Cloud Guard integration documented
- [ ] Security Zones compatible
- [ ] OCI Vault for secrets management
- [ ] Customer-managed encryption keys
- [ ] WAF integration documented
- [ ] OCI Bastion compatible

### 4.3 Compliance

- [ ] SOC 2 Type II attestation
- [ ] ISO 27001 certification
- [ ] GDPR compliance documented
- [ ] HIPAA eligibility (if applicable)
- [ ] FedRAMP authorization (if applicable)

---

## 5. Product Listing

### 5.1 Listing Information

| Field | Content | Status |
|-------|---------|--------|
| Application name | YAWL Workflow Engine | Pending |
| Short description | Enterprise workflow automation | Pending |
| Long description (4,000 chars) | [Full description] | Pending |
| Categories | Business Process, Developer Tools | Pending |
| Keywords | workflow, bpmn, automation | Pending |
| Version | 5.2.0 | Pending |

### 5.2 Pricing Model

| Model | Status |
|-------|--------|
| Hourly (metered) | Pending |
| Monthly subscription | Pending |
| Annual subscription | Pending |
| BYOL | Pending |

**Pricing Tiers:**

| Shape | OCPU | Memory | Hourly Rate |
|-------|------|--------|-------------|
| VM.Standard2.2 | 2 | 30 GB | $0.___ |
| VM.Standard2.4 | 4 | 60 GB | $0.___ |
| VM.Standard2.8 | 8 | 120 GB | $0.___ |
| VM.Standard3.Flex | 4-64 | 64 GB+ | $0.___ |

- [ ] Free tier available (___ days)
- [ ] Volume discounts defined

### 5.3 Visual Assets

| Asset | Requirements | Status |
|-------|--------------|--------|
| Logo (small) | 64x64 PNG | Pending |
| Logo (large) | 256x256 PNG | Pending |
| Icon | 128x128 PNG | Pending |
| Screenshots | Up to 10, 1024x768+ | Pending |
| Video | MP4/YouTube | Pending |

### 5.4 Documentation Links

- [ ] Getting Started Guide
- [ ] User Guide
- [ ] API Reference
- [ ] Release Notes
- [ ] Support Information
- [ ] Privacy Policy
- [ ] EULA

---

## 6. Oracle-Specific Requirements

### 6.1 Oracle Marketplace Policies

- [ ] Compatible with Oracle Cloud pricing
- [ ] No conflicting Oracle products
- [ ] Oracle customer data handling compliant
- [ ] Oracle support escalation documented

### 6.2 Oracle Integration Certification

- [ ] Integration with Oracle SaaS tested (if applicable)
- [ ] Oracle Identity Cloud Service compatible
- [ ] Oracle Integration Cloud compatible

### 6.3 Oracle Partner Incentives

- [ ] Eligible for Oracle partner incentives
- [ ] Listed in Oracle solutions catalog
- [ ] Oracle sales team briefed

---

## 7. Testing Requirements

### 7.1 Deployment Testing

| Test | Shape | Region | Result |
|------|-------|--------|--------|
| Fresh deployment | VM.Standard2.4 | Ashburn | |
| Scale deployment | VM.Standard2.8 | Phoenix | |
| Multi-region | VM.Standard2.4 | Frankfurt | |
| OKE deployment | OKE cluster | Ashburn | |
| Upgrade from 5.1 | VM.Standard2.4 | Ashburn | |

### 7.2 Performance Testing

| Shape | Metric | Value |
|-------|--------|-------|
| VM.Standard2.2 | Startup time | ___ sec |
| VM.Standard2.4 | Throughput | ___ tps |
| VM.Standard2.8 | Throughput | ___ tps |
| VM.Standard2.8 | Latency P99 | ___ ms |

### 7.3 Compatibility Testing

- [ ] Oracle Linux 8
- [ ] Oracle Linux 9
- [ ] Ubuntu 22.04
- [ ] OKE 1.28, 1.29

---

## 8. Support Requirements

### 8.1 Support Tiers

| Tier | Response Time | Channels | Price |
|------|--------------|----------|-------|
| Standard | 24 hours | Email, Portal | Included |
| Premium | 8 hours | Email, Phone | $___/month |
| Enterprise | 4 hours | All + Dedicated | $___/month |

### 8.2 Oracle Support Integration

- [ ] Oracle support escalation path defined
- [ ] Joint support procedures documented
- [ ] Support ticket integration (if applicable)

---

## 9. Launch Checklist

### Pre-Launch

- [ ] Listing submitted to Oracle review
- [ ] Technical validation completed
- [ ] Legal documentation approved
- [ ] Pricing finalized
- [ ] Support team trained

### Launch Day

- [ ] Listing approved and published
- [ ] Test deployment successful
- [ ] Oracle partner team notified
- [ ] Marketing announcement ready

### Post-Launch

- [ ] Usage monitoring active
- [ ] Customer feedback tracked
- [ ] Oracle sales enablement ongoing
- [ ] Quarterly business review scheduled

---

## 10. Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Engineering Lead | | | |
| Security Lead | | | |
| Product Manager | | | |
| Oracle Partner Manager | | | |

---

## Appendix: OCI-Specific Resources

- [Oracle Cloud Marketplace Publisher Guide](https://docs.oracle.com/en-us/iaas/Content/home.htm)
- [OCI Documentation](https://docs.oracle.com/en-us/iaas/Content/home.htm)
- [Oracle PartnerNetwork](https://partner.oracle.com/)
- [OCI Security Guide](https://docs.oracle.com/en-us/iaas/Content/Security/Concepts/security.htm)
