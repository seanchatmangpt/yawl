# Microsoft Azure Marketplace Checklist

**Product:** YAWL Workflow Engine v5.2
**Marketplace:** Azure Marketplace / Microsoft AppSource
**Last Updated:** 2025-02-13

---

## 1. Publisher Registration

### 1.1 Microsoft Partner Network

- [ ] Microsoft Partner Network account created
- [ ] Partner Center access granted
- [ ] Company profile completed
- [ ] Tax profile submitted (W-9/W-8BEN)
- [ ] Payout account configured

### 1.2 Marketplace Agreement

- [ ] Microsoft Azure Marketplace Publisher Agreement signed
- [ ] Microsoft Business and Commerce Agreement accepted
- [ ] Revenue share terms acknowledged
- [ ] Data Processing Agreement signed (if applicable)

---

## 2. Technical Requirements

### 2.1 Virtual Machine Image

- [ ] VHD image prepared
  - [ ] Fixed disk format (not dynamic)
  - [ ] Size: ___ GB
  - [ ] Generalized (sysprep for Windows / waagent for Linux)
- [ ] Published to Azure Compute Gallery
- [ ] Image version: 5.2.0
- [ ] Supported OS versions documented
  - [ ] Ubuntu 22.04 LTS
  - [ ] Red Hat Enterprise Linux 8/9
  - [ ] Windows Server 2022 (if applicable)

### 2.2 Azure Kubernetes Service (AKS)

- [ ] Helm chart validated for AKS
- [ ] AKS versions tested: 1.28, 1.29, 1.30
- [ ] Azure AD Workload Identity compatible
- [ ] Azure CNI compatible
- [ ] kubenet compatible

### 2.3 Azure Application (Solution Template)

- [ ] mainTemplate.json created
- [ ] createUiDefinition.json created
- [ ] Template follows Azure best practices
  - [ ] Parameters validated
  - [ ] Secure parameters marked
  - [ ] Outputs defined
  - [ ] Resource dependencies correct
- [ ] View definition (if applicable)

---

## 3. Azure Service Integration

### 3.1 Core Services

| Service | Integration Type | Status |
|---------|-----------------|--------|
| Azure SQL Database | Database | Pending |
| Azure Database for PostgreSQL | Database | Pending |
| Azure Database for MySQL | Database | Pending |
| Azure Cosmos DB | Database | Pending |
| Azure Cache for Redis | Caching | Pending |
| Azure Blob Storage | Object Storage | Pending |
| Azure Service Bus | Messaging | Pending |
| Azure Event Hubs | Event Streaming | Pending |
| Azure Key Vault | Secrets | Pending |
| Azure Managed Identity | Identity | Pending |
| Azure Monitor | Logging/Metrics | Pending |
| Azure Application Insights | APM | Pending |
| Azure AD B2C | Authentication | Pending |

### 3.2 Managed Identity Support

- [ ] System-assigned managed identity supported
- [ ] User-assigned managed identity supported
- [ ] Azure AD Workload Identity for AKS
- [ ] Role assignments documented

### 3.3 Networking

- [ ] VNet deployment supported
- [ ] Private Endpoint compatible
- [ ] Azure Firewall compatible
- [ ] Azure Front Door integration documented
- [ ] Private Link compatible

---

## 4. Security Requirements

### 4.1 Security Validation

- [ ] Azure Security Center recommendations addressed
- [ ] Microsoft Defender for Cloud scan passed
- [ ] Container vulnerability scan (if applicable)
- [ ] Static analysis (Application Insights)
- [ ] No critical vulnerabilities in dependencies

### 4.2 Security Features

- [ ] Azure Key Vault integration documented
- [ ] Azure Policy compatibility verified
- [ ] Azure Blueprints compatible
- [ ] Microsoft Defender integration
- [ ] Azure Sentinel integration (optional)

### 4.3 Compliance

- [ ] Azure compliance documentation provided
- [ ] GDPR compliance documented
- [ ] HIPAA attestation (if applicable)
- [ ] ISO 27001 certification status
- [ ] SOC 2 Type II attestation

### 4.4 Azure Security Baseline

- [ ] Security baseline documentation published
- [ ] Secure score impact documented
- [ ] Recommended security configurations

---

## 5. Offer Configuration

### 5.1 Offer Type

Select offer type(s):
- [ ] Virtual Machine
- [ ] Azure Application (Solution Template)
- [ ] Azure Application (Managed Application)
- [ ] Container (Azure Container Apps / AKS)
- [ ] SaaS

### 5.2 Offer Setup

| Field | Content | Status |
|-------|---------|--------|
| Offer ID | yawl-workflow-engine | Pending |
| Offer name | YAWL Workflow Engine | Pending |
| Publisher ID | _______ | Pending |
| Preview audience | _______ | Configured |

### 5.3 Listing Details

| Field | Limit | Content | Status |
|-------|-------|---------|--------|
| Name | 50 chars | YAWL Workflow Engine | Pending |
| Short summary | 100 chars | Enterprise workflow automation | Pending |
| Description | 3,000 chars | [Full description] | Pending |
| Getting started | 3,000 chars | [Instructions] | Pending |
| Search keywords | 3 | workflow, bpmn, automation | Pending |

### 5.4 Pricing

**Plan Structure:**

| Plan ID | Plan Name | Model |
|---------|-----------|-------|
| basic | Basic | Usage-based |
| standard | Standard | Usage-based |
| premium | Premium | Usage-based |

**VM Pricing (Hourly):**

| Plan | 2 vCPU | 4 vCPU | 8 vCPU | 16 vCPU |
|------|--------|--------|--------|---------|
| Basic | $___ | $___ | $___ | $___ |
| Standard | $___ | $___ | $___ | $___ |
| Premium | $___ | $___ | $___ | $___ |

**SaaS Pricing:**

| Plan | Monthly | Annual | triennial |
|------|---------|--------|-----------|
| Starter | $___ | $___ | $___ |
| Professional | $___ | $___ | $___ |
| Enterprise | $___ | $___ | $___ |

- [ ] Free trial configured (___ days)
- [ ] Private plan for enterprise customers
- [ ] BYOL option available

### 5.5 Visual Assets

| Asset | Requirements | Status |
|-------|--------------|--------|
| Logo (small) | 48x48 PNG | Pending |
| Logo (medium) | 90x90 PNG | Pending |
| Logo (large) | 216x216 PNG | Pending |
| Logo (wide) | 255x115 PNG | Pending |
| Hero image | 815x290 PNG/JPG | Pending |
| Screenshots | Up to 5, 1280x720+ | Pending |
| Video | YouTube/MP4 | Pending |

---

## 6. Technical Configuration

### 6.1 Technical Assets

- [ ] VM image published to staging
- [ ] Package version: 5.2.0
- [ ] Supported regions selected
  - [ ] East US
  - [ ] East US 2
  - [ ] West US 2
  - [ ] West US 3
  - [ ] Central US
  - [ ] North Europe
  - [ ] West Europe
  - [ ] UK South
  - [ ] France Central
  - [ ] Germany West Central
  - [ ] Southeast Asia
  - [ ] East Asia
  - [ ] Australia East
  - [ ] Japan East

### 6.2 Properties

- [ ] Software version: 5.2.0
- [ ] Release date: _______
- [ ] Release notes URL
- [ ] Privacy policy URL
- [ ] Legal terms URL
- [ ] Support URL
- [ ] Engineering contact

---

## 7. Testing Requirements

### 7.1 Certification Testing

- [ ] Azure certification test passed
- [ ] AppSource certification (if applicable)
- [ ] Technical validation complete
- [ ] Policy compliance verified

### 7.2 Deployment Testing

| Test | Environment | Result |
|------|-------------|--------|
| Fresh deployment | Staging | |
| Upgrade deployment | Staging | |
| Cross-region deployment | Staging | |
| Scale-out deployment | Staging | |
| AKS deployment | Staging | |

### 7.3 Performance Testing

| Instance Type | Metric | Value |
|---------------|--------|-------|
| Standard_D2s_v3 | Startup | ___ sec |
| Standard_D4s_v3 | Throughput | ___ tps |
| Standard_D8s_v3 | Throughput | ___ tps |
| Standard_D8s_v3 | Latency P99 | ___ ms |

---

## 8. Co-Sell & IP Co-Sell

### 8.1 Co-Sell Ready Requirements

- [ ] Solution reviewed by Microsoft
- [ ] Customer references (3 minimum)
- [ ] Sales deck prepared
- [ ] Demo environment available
- [ ] Partner economics documented

### 8.2 IP Co-Sell Eligibility

- [ ] Azure IP Co-sell eligible
- [ ] Solution added to OCP Catalog
- [ ] Microsoft sales team trained
- [ ] Incentive structure defined

---

## 9. Launch Checklist

### Pre-Launch

- [ ] Offer draft completed in Partner Center
- [ ] All visual assets uploaded
- [ ] Pricing validated
- [ ] Technical review requested
- [ ] Legal review completed
- [ ] Marketing review completed

### Go-Live

- [ ] Offer published to production
- [ ] Test purchase completed
- [ ] Deployment verified
- [ ] Support team notified
- [ ] Microsoft Partner team notified

### Post-Launch

- [ ] Usage analytics monitored
- [ ] Customer feedback tracked
- [ ] Lead routing configured
- [ ] Co-sell activities started

---

## 10. Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Engineering Lead | | | |
| Security Lead | | | |
| Product Manager | | | |
| Microsoft Partner Manager | | | |

---

## Appendix: Azure-Specific Resources

- [Partner Center Documentation](https://docs.microsoft.com/azure/marketplace/)
- [Azure Marketplace Publisher Guide](https://docs.microsoft.com/azure/marketplace/marketplace-publishers-guide)
- [Commercial Marketplace Certification Policies](https://docs.microsoft.com/legal/marketplace/certification-policies)
- [Azure Well-Architected Framework](https://docs.microsoft.com/azure/architecture/framework/)
