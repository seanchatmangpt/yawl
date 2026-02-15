# Azure Marketplace Requirements for YAWL Workflow Engine

## Executive Summary

This document outlines the complete technical and business requirements for listing YAWL (Yet Another Workflow Language) as a SaaS and container offer on Azure Marketplace. YAWL is a Java-based BPM/Workflow engine with formal foundations, supporting 43+ workflow control-flow patterns.

---

## 1. Business Requirements

### 1.1 Publisher Eligibility Requirements

| Requirement | Description | Status |
|------------|-------------|--------|
| Microsoft Partner Network | Active MPN membership | Required |
| Commercial Marketplace | Enrolled in Azure Commercial Marketplace | Required |
| Tax Information | Valid tax information for all applicable regions | Required |
| Payout Account | Bank account for payment processing | Required |
| Company Verification | Complete business identity verification | Required |

### 1.2 Product Listing Requirements

#### Offer Types Supported for YAWL

| Offer Type | Use Case | Recommendation |
|------------|----------|----------------|
| Azure Container | Kubernetes-based deployment | Primary |
| SaaS | Fully managed service | Secondary |
| Virtual Machine | IaaS deployment | Optional |

#### Product Setup Guidelines
- Product name must be unique across Azure Marketplace
- Minimum 500 characters for description
- Maximum 10 screenshots (1280x720 minimum)
- Privacy policy URL required
- Legal terms URL required
- Support documentation required

### 1.3 Customer Information Requirements
- Must provide registration/landing page
- Support Azure AD single sign-on (recommended)
- Customer must be able to manage subscription
- No redirects to other cloud platforms

---

## 2. Technical Architecture Requirements

### 2.1 Hosting Patterns

#### Pattern A: Azure Container Offer (Recommended)
```
+------------------+     +------------------+
|   Seller Azure   |     |   Buyer Access   |
|   Tenant         |     |                  |
|                  |     |   Web Browser    |
|  +------------+  |     |       |          |
|  | AKS Cluster|<-|-----|-------+          |
|  | (YAWL)     |  |     |                  |
|  +------------+  |     +------------------+
|        |         |
|  +-----+-----+   |
|  | Azure DB  |   |
|  | PostgreSQL|   |
|  +-----------+   |
|        |         |
|  +-----+-----+   |
|  | Blob      |   |
|  | Storage   |   |
|  +-----------+   |
+------------------+
```

#### Pattern B: Customer-Deployed (ARM Templates)
- ARM templates deployed to customer's subscription
- Seller manages control plane
- Application runs in customer's Azure environment

### 2.2 Azure Services Requirements

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **Azure Kubernetes Service (AKS)** | Container orchestration | Kubernetes 1.28+ |
| **Azure Database for PostgreSQL** | Workflow state persistence | Flexible Server, Zone-redundant |
| **Azure Blob Storage** | Specification storage and logs | RA-GRS redundancy |
| **Azure Load Balancer** | Traffic distribution | Standard SKU |
| **Azure Monitor** | Logging, metrics, and alerts | Log Analytics workspace |
| **Azure Key Vault** | Secrets and certificate management | RBAC authorization |
| **Azure Container Registry** | Container image storage | Premium SKU |
| **Microsoft Entra ID** | Identity and access management | Azure AD integration |
| **Azure Virtual Network** | Network isolation | VNet with private subnets |
| **Azure Private Link** | Private connectivity | For database and storage |
| **Azure Application Gateway** | Web application firewall | WAF v2 SKU |

### 2.3 Network Architecture

#### VNet Configuration
```
VNet (10.0.0.0/16)
+-- AzureFirewallSubnet (10.0.0.0/26)
|   +-- Azure Firewall
|
+-- GatewaySubnet (10.0.1.0/27)
|   +-- VPN/ExpressRoute Gateway (optional)
|
+-- aks-subnet (10.0.64.0/18)
|   +-- AKS System Node Pool
|   +-- AKS User Node Pool
|
+-- private-endpoints-subnet (10.0.128.0/24)
|   +-- PostgreSQL Private Endpoint
|   +-- Storage Private Endpoint
|   +-- Container Registry Private Endpoint
|
+-- appgw-subnet (10.0.192.0/24)
    +-- Application Gateway
```

### 2.4 Security Requirements

#### Identity and Access Management
- Managed Identities for Azure resources
- Azure RBAC with least privilege principle
- Microsoft Entra ID integration for user authentication
- Conditional Access policies for administrative access

#### Encryption
- At rest: Azure Disk Encryption with Platform-managed keys or Customer-managed keys
- In transit: TLS 1.2+ for all communications
- Certificate management via Azure Key Vault

#### Network Security
- Network Security Groups on all subnets
- Azure Firewall for egress control
- Private Endpoints for PaaS services
- DDoS Protection Standard (recommended)

---

## 3. Container Offer Requirements

### 3.1 Technical Assets

| Asset | Requirement | YAWL Implementation |
|-------|-------------|---------------------|
| Container Images | Must be in Azure Container Registry | yawl/engine, yawl/resource-service, etc. |
| ARM Templates | Main deployment template + nested templates | main.json, aks.json, postgresql.json, storage.json |
| Helm Charts | Optional but recommended | helm/yawl chart |
| Kubernetes Manifests | Deployment, Service, Ingress definitions | Included in ARM templates |

### 3.2 ARM Template Requirements

```json
{
  "$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
  "contentVersion": "1.0.0.0",
  "parameters": {
    "location": {
      "type": "string",
      "defaultValue": "[resourceGroup().location]",
      "metadata": {
        "description": "Azure region for deployment"
      }
    }
  },
  "resources": [...]
}
```

### 3.3 Container Image Requirements

- Base images from approved sources (Microsoft, Ubuntu, Alpine, Distroless)
- No critical vulnerabilities
- Must include required labels
- Semantic versioning for tags

Required labels:
```dockerfile
LABEL org.opencontainers.image.source="https://github.com/yawlfoundation/yawl"
LABEL org.opencontainers.image.description="YAWL Workflow Engine"
LABEL org.opencontainers.image.licenses="LGPL-3.0"
LABEL org.opencontainers.image.vendor="YAWL Foundation"
LABEL org.opencontainers.image.version="5.2.0"
```

---

## 4. Azure-Specific Integration Requirements

### 4.1 Azure Kubernetes Service (AKS)

| Setting | Value | Notes |
|---------|-------|-------|
| Kubernetes Version | 1.28+ | Current stable |
| Network Plugin | Azure CNI | Advanced networking |
| Network Policy | Calico or Azure | Network isolation |
| Load Balancer SKU | Standard | Required for production |
| Monitoring | Azure Monitor for containers | Enabled |
| Azure AD Integration | Managed | RBAC with Azure AD |
| Private Cluster | Enabled | For production |
| Auto-scaling | Enabled | 2-10 nodes |

### 4.2 Azure Database for PostgreSQL

| Setting | Value | Notes |
|---------|-------|-------|
| Version | PostgreSQL 15 | Latest stable |
| SKU | Standard_D4s_v3 | Minimum production |
| Storage | 100 GB | Auto-grow enabled |
| Backup | Geo-redundant | 7-35 days retention |
| High Availability | Zone-redundant | 99.99% SLA |
| SSL | Enforced | TLS 1.2+ |
| Private Endpoint | Enabled | Network isolation |

### 4.3 Azure Blob Storage

| Setting | Value | Notes |
|---------|-------|-------|
| Account Kind | StorageV2 | General purpose v2 |
| Replication | RA-GRS | Read-access geo-redundant |
| Access Tier | Hot | For frequent access |
| HTTPS | Enabled only | TLS 1.2+ |
| Min TLS Version | TLS1_2 | Required |
| Public Access | Disabled | Private endpoints |

### 4.4 Azure Monitor Integration

```yaml
# Required metrics
- azure.container.kube_pod_status_ready
- azure.container.kube_deployment_status_replicas
- azure.dbforpostgresql.cpu_percent
- azure.dbforpostgresql.storage_percent
- azure.network.loadbalancer_dip_availability
```

---

## 5. Metering and Billing Integration

### 5.1 Metering Dimensions for YAWL

| Dimension | Unit | Description |
|-----------|------|-------------|
| `workflow_executions` | Count | Number of workflow instances executed |
| `active_users` | Count | Concurrent active users |
| `data_processed_gb` | GB | Total data processed through workflows |
| `api_calls` | Count | API requests to YAWL interfaces |
| `worklet_invocations` | Count | Dynamic worklet rule executions |

### 5.2 Metering API Integration

```python
# Azure Marketplace Metering API example
import requests
from azure.identity import DefaultAzureCredential

def emit_usage_event(meter_id: str, quantity: float, dimension: str):
    credential = DefaultAzureCredential()
    token = credential.get_token("https://marketplaceapi.microsoft.com/.default")

    headers = {
        "Authorization": f"Bearer {token.token}",
        "Content-Type": "application/json",
        "x-ms-requestid": str(uuid.uuid4()),
    }

    payload = {
        "resourceId": meter_id,
        "quantity": quantity,
        "dimension": dimension,
        "effectiveStartTime": datetime.utcnow().isoformat(),
        "planId": "professional"
    }

    response = requests.post(
        f"https://marketplaceapi.microsoft.com/api/usageEvent",
        headers=headers,
        json=payload
    )
    return response.json()
```

### 5.3 SaaS Fulfillment API

Required endpoints for SaaS offer:
- `POST /subscriptions/resolve` - Resolve subscription token
- `GET /subscriptions/{subscriptionId}` - Get subscription details
- `PATCH /subscriptions/{subscriptionId}` - Update subscription
- `DELETE /subscriptions/{subscriptionId}` - Cancel subscription
- `POST /subscriptions/{subscriptionId}/activate` - Activate subscription

---

## 6. Compliance and Certification

### 6.1 Required Certifications

| Certification | Status | Notes |
|---------------|--------|-------|
| ISO 27001 | Required | Information security |
| SOC 2 Type II | Required | Security controls |
| GDPR | Required | EU data protection |
| HIPAA | Optional | Healthcare |
| PCI DSS | Optional | Payment processing |

### 6.2 Azure Compliance

| Compliance Offering | Status | Notes |
|---------------------|--------|-------|
| Azure Security Center | Required | Secure score monitoring |
| Azure Policy | Required | Compliance enforcement |
| Microsoft Defender | Recommended | Threat protection |
| Azure Sentinel | Optional | SIEM integration |

### 6.3 Data Residency

Supported Azure regions for data residency:
- Americas: East US, West US 2, Central US
- Europe: West Europe, North Europe, France Central, Germany West Central
- Asia Pacific: East Asia, Southeast Asia, Japan East, Australia East

---

## 7. Support and SLA Requirements

### 7.1 Support Tiers

| Tier | Response Time | Coverage | Channels |
|------|---------------|----------|----------|
| Basic | 72 hours | Business hours | Email |
| Standard | 24 hours | 24x5 | Email, Web portal |
| Premium | 4 hours | 24x7 | Email, Web, Phone |
| Enterprise | 1 hour | 24x7 | All channels + dedicated |

### 7.2 SLA Commitments

- Availability: 99.9% monthly uptime (Standard), 99.99% (Premium)
- Planned maintenance: 4 hours/month maximum
- Data backup: Daily with 30-day retention
- Recovery Point Objective (RPO): 1 hour
- Recovery Time Objective (RTO): 4 hours

---

## 8. Testing Requirements

### 8.1 Pre-Launch Testing Checklist

- [ ] Container images pushed to Azure Container Registry
- [ ] ARM templates validated with test deployments
- [ ] AKS deployment tested with Helm chart
- [ ] PostgreSQL connectivity verified
- [ ] Blob Storage integration verified
- [ ] Azure AD authentication tested
- [ ] Metering API integration tested
- [ ] SaaS fulfillment webhooks tested
- [ ] Application Gateway WAF rules validated
- [ ] End-to-end workflow execution tested

### 8.2 Azure-Specific Testing

- Test in multiple Azure regions
- Verify zone-redundant failover
- Test auto-scaling behavior
- Validate backup/restore procedures
- Test disaster recovery scenarios

---

## 9. Launch Checklist

### 9.1 Pre-Submission

- [ ] Complete Microsoft Partner registration
- [ ] Create commercial marketplace account
- [ ] Prepare product logo (216x216, 48x48)
- [ ] Write product description (500+ chars)
- [ ] Create screenshots (up to 10, 1280x720 min)
- [ ] Prepare pricing plans
- [ ] Create architecture diagram
- [ ] Complete integration testing
- [ ] Document support procedures

### 9.2 Technical Validation

- [ ] ARM templates pass validation
- [ ] Container images scanned for vulnerabilities
- [ ] Kubernetes manifests tested
- [ ] Azure AD integration verified
- [ ] Metering API tested
- [ ] Private endpoint connectivity verified

### 9.3 Submission

- [ ] Submit offer via Partner Center
- [ ] Provide technical configuration
- [ ] Set pricing for all regions
- [ ] Complete legal review
- [ ] Submit for Microsoft review (5-10 business days)

### 9.4 Post-Launch

- [ ] Monitor subscription metrics
- [ ] Review metering accuracy
- [ ] Collect customer feedback
- [ ] Maintain security compliance
- [ ] Update product documentation

---

## 10. Azure Well-Architected Framework Alignment

### 10.1 Reliability Pillar

- Multi-zone deployment with availability zones
- Geo-redundant backup storage
- Auto-scaling for traffic spikes
- Health probes for load balancer
- Circuit breaker patterns in application

### 10.2 Security Pillar

- Managed identities for all Azure resources
- Private endpoints for PaaS services
- Azure Firewall for egress control
- Azure Key Vault for secrets
- Microsoft Defender for containers

### 10.3 Cost Optimization Pillar

- Reserved instances for steady workloads
- Spot instances for non-critical workloads
- Auto-scaling to match demand
- Storage tiering for log archival
- Regular cost reviews

### 10.4 Operational Excellence Pillar

- Infrastructure as Code (ARM templates, Bicep)
- Azure Monitor for observability
- Azure DevOps for CI/CD
- Automated deployment pipelines
- Regular backup testing

### 10.5 Performance Efficiency Pillar

- AKS node pool autoscaling
- Azure CDN for static content
- Read replicas for database (if needed)
- Connection pooling
- Caching strategies

---

## 11. References

- [Azure Marketplace Documentation](https://docs.microsoft.com/azure/marketplace/)
- [Azure Container Offer Guide](https://docs.microsoft.com/azure/marketplace/create-azure-container-offer)
- [Azure Kubernetes Service Best Practices](https://docs.microsoft.com/azure/aks/best-practices)
- [Azure Well-Architected Framework](https://docs.microsoft.com/azure/architecture/framework/)
- [Azure Security Best Practices](https://docs.microsoft.com/azure/security/fundamentals/best-practices-and-patterns)
- [YAWL Documentation](https://yawlfoundation.github.io/yawl/)

---

*Document Version: 1.0*
*Last Updated: February 2025*
*Next Review: May 2025*
