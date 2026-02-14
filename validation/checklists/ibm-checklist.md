# IBM Cloud Marketplace Checklist

**Product:** YAWL Workflow Engine v5.2
**Marketplace:** IBM Cloud Catalog / IBM Partner Plus
**Last Updated:** 2025-02-13

---

## 1. Publisher Registration

### 1.1 IBM Partner Plus

- [ ] IBM Partner Plus membership active
- [ ] Partner tier: Partner / Advanced / Premier
- [ ] Business profile completed
- [ ] Tax documentation submitted
- [ ] Payout configuration complete

### 1.2 Marketplace Agreement

- [ ] IBM Cloud Marketplace Agreement signed
- [ ] IBM Digital Provider Agreement accepted
- [ ] Revenue share terms acknowledged
- [ ] IBM Privacy Statement compliance confirmed

---

## 2. Technical Requirements

### 2.1 IBM Cloud Infrastructure

- [ ] Virtual Server Image prepared
- [ ] Image format: VHD or qcow2
- [ ] Published to IBM Cloud regions
  - [ ] US South (Dallas)
  - [ ] US East (Washington DC)
  - [ ] EU Great Britain (London)
  - [ ] EU Germany (Frankfurt)
  - [ ] Asia Pacific Tokyo
  - [ ] Asia Pacific Sydney
  - [ ] Asia Pacific Osaka
  - [ ] Canada Montreal
  - [ ] Brazil Sao Paulo

### 2.2 Container Support (IBM Cloud Kubernetes Service / Red Hat OpenShift)

- [ ] Container image published to IBM Container Registry
- [ ] Registry: icr.io/_______/yawl
- [ ] Red Hat OpenShift certified (if applicable)
- [ ] IBM Cloud Kubernetes Service validated
- [ ] Helm chart compatible with OpenShift
- [ ] Security Context Constraints (SCC) compatible

### 2.3 SaaS Integration (if applicable)

- [ ] IBM Cloud SaaS integration configured
- [ ] IBMid authentication support
- [ ] App ID integration documented
- [ ] Metering API integration

---

## 3. IBM Service Integration

### 3.1 Core Services

| Service | Integration Type | Status |
|---------|-----------------|--------|
| IBM Cloud Databases for PostgreSQL | Database | Pending |
| IBM Cloud Databases for MongoDB | Database | Pending |
| IBM Cloud Databases for Redis | Caching | Pending |
| IBM Cloud Object Storage | Object Storage | Pending |
| IBM Event Streams | Messaging | Pending |
| IBM Cloud Secret Manager | Secrets | Pending |
| IBM Key Protect | Encryption | Pending |
| IBM Cloud IAM | Identity | Pending |
| IBM Log Analysis | Logging | Pending |
| IBM Cloud Monitoring | Metrics | Pending |
| IBM Cloud Internet Services | CDN/WAF | Pending |

### 3.2 Identity & Access Management

- [ ] IBM Cloud IAM integration documented
- [ ] Service ID configuration provided
- [ ] Access group templates
- [ ] API key management documented

### 3.3 Networking

- [ ] VPC deployment supported
- [ ] Classic infrastructure compatible (if applicable)
- [ ] IBM Cloud Load Balancer integration
- [ ] Direct Link compatible
- [ ] VPN support documented

---

## 4. Security Requirements

### 4.1 Security Validation

- [ ] IBM Vulnerability Advisor scan passed
- [ ] Container image scan clean
- [ ] No critical/high vulnerabilities
- [ ] SAST analysis completed
- [ ] Penetration testing completed (if required)

### 4.2 Security Features

- [ ] IBM Security Advisor integration documented
- [ ] Key Protect / Hyper Protect Crypto Services compatible
- [ ] Secret Manager integration documented
- [ ] IBM Cloud Internet Services (WAF/DDoS) compatible
- [ ] Security and Compliance Center integration

### 4.3 Compliance

- [ ] SOC 2 Type II attestation
- [ ] ISO 27001 certification
- [ ] GDPR compliance documented
- [ ] HIPAA eligibility (if applicable)
- [ ] IBM compliance framework alignment

---

## 5. Product Listing

### 5.1 Listing Information

| Field | Content | Status |
|-------|---------|--------|
| Product name | YAWL Workflow Engine | Pending |
| Short description | Enterprise workflow automation | Pending |
| Long description (4,000 chars) | [Full description] | Pending |
| Category | Developer Tools / Business Applications | Pending |
| Tags | workflow, bpmn, automation | Pending |
| Version | 5.2.0 | Pending |

### 5.2 Pricing Model

| Model | Status |
|-------|--------|
| Metered (usage-based) | Pending |
| Reserved (subscription) | Pending |
| BYOL | Pending |

**Pricing Tiers:**

| Profile | vCPU | Memory | Hourly Rate |
|---------|------|--------|-------------|
| bx2-2x8 | 2 | 8 GB | $0.___ |
| bx2-4x16 | 4 | 16 GB | $0.___ |
| bx2-8x32 | 8 | 32 GB | $0.___ |
| cx2-4x8 | 4 | 8 GB | $0.___ |
| mx2-4x32 | 4 | 32 GB | $0.___ |

- [ ] Free tier / Lite plan available (___ days)
- [ ] Volume discounts defined

### 5.3 Visual Assets

| Asset | Requirements | Status |
|-------|--------------|--------|
| Product icon | 128x128 PNG | Pending |
| Logo | 240x240 PNG | Pending |
| Hero image | 1280x800 PNG/JPG | Pending |
| Screenshots | Up to 10, 1024x768+ | Pending |
| Video | MP4/YouTube | Pending |

### 5.4 Documentation

- [ ] Getting Started Guide
- [ ] Architecture Guide
- [ ] API Reference
- [ ] Release Notes
- [ ] Support Information
- [ ] Privacy Policy
- [ ] Terms of Use

---

## 6. Red Hat OpenShift Integration (Optional but Recommended)

### 6.1 OpenShift Certification

- [ ] OpenShift certification initiated
- [ ] OpenShift Container Platform 4.x tested
- [ ] OpenShift Dedicated tested
- [ ] Red Hat OpenShift Service on AWS (ROSA) compatible
- [ ] IBM Cloud OpenShift tested

### 6.2 OpenShift Requirements

- [ ] Helm Operator created (optional)
- [ ] OpenShift templates provided
- [ ] Security Context Constraints documented
- [ ] Route/Ingress configurations
- [ ] ImageStream support

---

## 7. IBM Watson/AI Integration (Optional)

### 7.1 Watson Services

- [ ] Watson Assistant integration (if applicable)
- [ ] Watson Discovery integration (if applicable)
- [ ] Watson Language Translator (if applicable)
- [ ] Watson Machine Learning (if applicable)

### 7.2 AI/ML Features

- [ ] IBM Cloud Object Storage for model storage
- [ ] Watson OpenScale integration (if applicable)
- [ ] AutoAI compatibility (if applicable)

---

## 8. Testing Requirements

### 8.1 Deployment Testing

| Test | Environment | Region | Result |
|------|-------------|--------|--------|
| VPC deployment | KVM | US South | |
| OpenShift deployment | OCP 4.x | US South | |
| IKS deployment | IKS 1.28 | US South | |
| Classic deployment | Classic | US South | |
| Multi-region | VPC | EU Germany | |

### 8.2 Performance Testing

| Profile | Metric | Value |
|---------|--------|-------|
| bx2-2x8 | Startup time | ___ sec |
| bx2-4x16 | Throughput | ___ tps |
| bx2-8x32 | Throughput | ___ tps |
| bx2-8x32 | Latency P99 | ___ ms |

### 8.3 Compatibility Testing

- [ ] IBM Cloud Kubernetes Service 1.27, 1.28, 1.29
- [ ] Red Hat OpenShift 4.13, 4.14, 4.15
- [ ] Ubuntu 22.04
- [ ] Red Hat Enterprise Linux 8/9

---

## 9. Support Requirements

### 9.1 Support Tiers

| Tier | Response Time | Channels | Price |
|------|--------------|----------|-------|
| Basic | 24 hours | Portal, Email | Included |
| Advanced | 8 hours | Portal, Email, Chat | $___/month |
| Premium | 4 hours | All + Phone | $___/month |
| Enterprise | 1 hour | All + Dedicated TAM | $___/month |

### 9.2 IBM Support Integration

- [ ] IBM support escalation path defined
- [ ] Joint support procedures documented
- [ ] Support portal integration (if applicable)

---

## 10. IBM Partner Plus Benefits

### 10.1 Partner Benefits

- [ ] Partner badge earned
- [ ] Co-marketing funds available
- [ ] Technical enablement completed
- [ ] Sales enablement completed

### 10.2 IBM Seller Engagement

- [ ] Listed in IBM seller toolkit
- [ ] IBM sales team briefed
- [ ] Joint opportunity process defined
- [ ] Referral fee structure agreed

---

## 11. Launch Checklist

### Pre-Launch

- [ ] Product listing submitted
- [ ] Technical validation completed
- [ ] Legal review approved
- [ ] Pricing entered in system
- [ ] Support team ready

### Launch Day

- [ ] Listing published
- [ ] Test purchase completed
- [ ] Deployment verified
- [ ] IBM partner team notified
- [ ] Marketing announcement ready

### Post-Launch

- [ ] Usage monitoring active
- [ ] Customer feedback collected
- [ ] Partner scorecard reviewed
- [ ] IBM sales enablement ongoing

---

## 12. Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Engineering Lead | | | |
| Security Lead | | | |
| Product Manager | | | |
| IBM Partner Manager | | | |

---

## Appendix: IBM Cloud Resources

- [IBM Cloud Catalog Provider Guide](https://cloud.ibm.com/docs/catalog?topic=catalog-getting-started)
- [IBM Cloud Provider Toolkit](https://cloud.ibm.com/docs/ibm-cloud-provider-for-terraform)
- [OpenShift Certification](https://connect.redhat.com/en/technologies/openshift-certified)
- [IBM Partner Plus](https://www.ibm.com/partnerplus)
