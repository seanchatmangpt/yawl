# IBM Cloud Marketplace Requirements for YAWL Workflow Engine

## Executive Summary

This document outlines the complete technical and business requirements for listing YAWL (Yet Another Workflow Language) as a SaaS product on IBM Cloud Marketplace. YAWL is a Java-based BPM/Workflow engine with formal foundations, supporting 43+ workflow control-flow patterns.

---

## 1. Business Requirements

### 1.1 Seller Eligibility Requirements

| Requirement | Description | Status |
|------------|-------------|--------|
| IBM Cloud Account | Must have an IBM Cloud account in good standing | Required |
| PartnerWorld Registration | Complete IBM PartnerWorld registration | Required |
| Tax Information | Provide valid tax information for all applicable regions | Required |
| Banking Information | Provide banking details for payment processing | Required |
| Business Verification | Complete business identity verification | Required |
| EULA Acceptance | Accept IBM Cloud Marketplace Provider Agreement | Required |

### 1.2 Product Listing Requirements

#### Product Setup Guidelines
- All pricing dimensions must relate to actual software value
- At least one dimension must have price > $0.00 (except Free tier)
- Product must support IBM Cloud IAM authentication
- Must provide English-language support (multi-language optional)

#### Customer Information Requirements
- SaaS products must be billed entirely through IBM Cloud Marketplace
- Cannot collect customer payment information directly
- Registration page must include email address input field
- Optional fields: Name, Company, Phone, Product setup preferences
- Must integrate with IBM Cloud Identity for SSO

### 1.3 Product Usage Guidelines
- Customers must be able to create accounts and access web console after subscription
- Existing customers must be able to log in from fulfillment landing page
- Subscription status must be visible within the SaaS application
- Support contact options must be specified on fulfillment landing page
- No redirects to other cloud platforms or off-marketplace upsell services

---

## 2. Technical Architecture Requirements

### 2.1 Hosting Patterns

YAWL supports multiple hosting patterns for IBM Cloud Marketplace:

#### Pattern A: Seller-Hosted SaaS (Recommended)
```
+------------------+     +------------------+
|   Seller IBM     |     |   Buyer Access   |
|   Cloud Account  |     |                  |
|                  |     |   Web Browser    |
|  +------------+  |     |       |          |
|  | YAWL Engine|<-|-----|-------+          |
|  | (IKS)      |  |     |                  |
|  +------------+  |     +------------------+
|        |         |
|  +-----+-----+   |
|  | Databases |   |
|  | PostgreSQL|   |
|  +-----------+   |
|        |         |
|  +-----+-----+   |
|  | COS Bucket|   |
|  | (Persistence)|
|  +-----------+   |
+------------------+
```

#### Pattern B: IBM Cloud SaaS (Red Hat OpenShift)
- Deploy on Red Hat OpenShift on IBM Cloud
- Leverage IBM Cloud Operator for service binding
- Use IBM Cloud Secrets Manager for credentials

### 2.2 IBM Cloud Services Requirements

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **IBM Kubernetes Service (IKS)** | Container orchestration for YAWL services | Kubernetes 1.28+, 3+ worker nodes |
| **Databases for PostgreSQL** | Workflow state persistence | Standard plan, 2-4 members, 4-16 GB RAM |
| **IBM Cloud Load Balancer** | Traffic distribution and SSL termination | HTTPS with IBM-managed certificates |
| **Cloud Object Storage (COS)** | Workflow specification storage and logs | Standard tier, 99.99% durability |
| **IBM Cloud Monitoring** | Observability and alerting | Sysdig-based monitoring |
| **IBM Cloud Log Analysis** | Centralized logging | 30-day retention |
| **IBM Key Protect** | Key management for encryption | HSM-backed keys |
| **IBM Cloud Secrets Manager** | Database credentials and API keys | Auto-rotation enabled |
| **IBM Cloud Internet Services** | CDN, WAF, DDoS protection | Edge-optimized |
| **IBM Cloud Activity Tracker** | API audit logging | 7-day retention (upgradeable) |
| **IBM Cloud Container Registry** | Private container images | Vulnerability scanning |

### 2.3 Network Architecture

#### VPC Configuration
```
IBM Cloud VPC (10.0.0.0/16)
+-- Public Gateways
|   +-- VPC Public Gateway (for NAT)
|
+-- Public Subnets (10.0.0.0/20, 10.0.16.0/20)
|   +-- IBM Cloud Load Balancer
|
+-- Private Subnets (10.0.64.0/20, 10.0.80.0/20)
|   +-- IKS Worker Nodes
|   +-- YAWL Engine Pods
|
+-- Database Subnets (10.0.128.0/24, 10.0.129.0/24)
    +-- Databases for PostgreSQL
```

### 2.4 Security Requirements

#### Identity and Access Management (IAM)
- IBM Cloud IAM service-to-service authentication
- Service IDs with API keys for automated access
- Least privilege access policies
- No hardcoded credentials
- Resource groups for logical organization

#### Encryption
- At rest: IBM Key Protect with customer-managed keys
- In transit: TLS 1.2+ for all communications
- Certificate management via IBM Cloud Certificate Manager

#### Network Security
- Security groups with minimal required access
- Network ACLs for subnet-level protection
- VPC flow logs enabled
- IBM Cloud Internet Services WAF rules

---

## 3. SaaS Integration Requirements

### 3.1 IBM Cloud SaaS Subscription API

| Operation | Endpoint | Purpose |
|-----------|----------|---------|
| Provision | POST /v2/resources | Create service instance |
| Deprovision | DELETE /v2/resources/:id | Delete service instance |
| Update | PATCH /v2/resources/:id | Update service instance |
| Bind | PUT /v2/resources/:id/service_bindings | Bind to application |
| Unbind | DELETE /v2/resources/:id/service_bindings/:bid | Unbind from application |

### 3.2 Registration Flow

```
1. Customer subscribes on IBM Cloud Marketplace
2. IBM Cloud invokes provisioning API callback
3. Create YAWL tenant instance
4. Generate IBM Cloud IAM service ID
5. Create customer account in YAWL system
6. Persist: InstanceID, Region, PlanID, OrganizationID
7. Return dashboard URL
8. Customer accesses YAWL via SSO
```

### 3.3 Open Service Broker API Implementation

YAWL must implement Open Service Broker API (OSBAPI) v2.15+:

```yaml
# Service Catalog
services:
  - id: yawl-workflow-engine
    name: yawl-workflow-engine
    description: Enterprise BPM/Workflow Engine with 43+ control-flow patterns
    bindable: true
    tags:
      - bpm
      - workflow
      - process-automation
    metadata:
      displayName: YAWL Workflow Engine
      imageUrl: https://yawl-assets.cos.cloud/object/logo.png
      longDescription: YAWL is a powerful BPM/Workflow engine...
      providerDisplayName: YAWL Foundation
      documentationUrl: https://yawlfoundation.github.io/yawl/
      supportUrl: https://yawlfoundation.github.io/yawl/support
    plans:
      - id: basic-plan
        name: basic
        description: Basic tier for small teams
        free: false
        schemas:
          service_instance:
            create:
              parameters:
                $schema: http://json-schema.org/draft-04/schema#
                type: object
                properties:
                  admin_email:
                    type: string
                    description: Admin email for YAWL instance
                  region:
                    type: string
                    enum: [us-south, eu-de, jp-tok]
```

### 3.4 IAM Integration

```yaml
# IBM Cloud IAM Policy Template
{
  "type": "access",
  "subjects": [
    {
      "attributes": [
        {
          "name": "accountId",
          "value": "{{ACCOUNT_ID}}"
        },
        {
          "name": "resourceType",
          "value": "service-instance"
        },
        {
          "name": "resource",
          "value": "{{INSTANCE_ID}}"
        }
      ]
    }
  ],
  "roles": [
    {
      "role_id": "crn:v1:bluemix:public:iam::::role:Viewer"
    }
  ],
  "resources": [
    {
      "attributes": [
        {
          "name": "accountId",
          "value": "{{ACCOUNT_ID}}"
        },
        {
          "name": "serviceName",
          "value": "yawl-workflow-engine"
        }
      ]
    }
  ]
}
```

---

## 4. YAWL-Specific Requirements

### 4.1 Service Components

| Component | Description | Container Image |
|-----------|-------------|-----------------|
| **YAWL Engine** | Core workflow execution engine | icr.io/yawl/engine:5.2 |
| **Resource Service** | Human/non-human resource management | icr.io/yawl/resource-service:5.2 |
| **Worklet Service** | Dynamic process adaptation | icr.io/yawl/worklet-service:5.2 |
| **Monitor Service** | Process monitoring dashboard | icr.io/yawl/monitor-service:5.2 |
| **Scheduling Service** | Calendar-based scheduling | icr.io/yawl/scheduling-service:5.2 |
| **Cost Service** | Cost tracking and reporting | icr.io/yawl/cost-service:5.2 |

### 4.2 Database Schema

YAWL requires the following Databases for PostgreSQL configuration:

```yaml
Service: Databases for PostgreSQL
Plan: Standard
Members: 3 (for high availability)
Memory: 4 GB per member
Disk: 100 GB (auto-scaling enabled)
Version: PostgreSQL 15
Encryption: IBM Key Protect
Backup: Daily with 30-day retention
```

### 4.3 Persistence Storage

Cloud Object Storage bucket structure for YAWL specifications:

```
cos://yawl-tenant-{account-id}/
  +-- specifications/
  |   +-- {workflow-id}.ywl
  +-- logs/
  |   +-- execution/
  |   +-- audit/
  +-- exports/
  +-- worklets/
```

---

## 5. Metering Requirements

### 5.1 Metering Dimensions for YAWL

| Dimension | Unit | Description |
|-----------|------|-------------|
| `workflow_executions` | Count | Number of workflow instances executed |
| `active_users` | Count | Concurrent active users |
| `data_processed_gb` | GB | Total data processed through workflows |
| `api_calls` | Count | API requests to YAWL interfaces |
| `worklet_invocations` | Count | Dynamic worklet rule executions |

### 5.2 IBM Cloud Usage Reporting

```json
{
  "resource_instance_id": "crn:v1:bluemix:public:yawl-workflow-engine:us-south:a/abc123::",
  "plan_id": "basic-plan",
  "region": "us-south",
  "start": "2025-01-15T00:00:00Z",
  "end": "2025-01-15T01:00:00Z",
  "usage": [
    {
      "metric": "workflow_executions",
      "quantity": 150,
      "unit": "COUNT"
    },
    {
      "metric": "active_users",
      "quantity": 5,
      "unit": "COUNT"
    }
  ]
}
```

### 5.3 Metering Schedule

- Frequency: Every hour (IBM Cloud requirement)
- Reporting endpoint: IBM Cloud Usage Metering API
- Timestamp: Must be within 1 hour of current time
- Zero reporting: Send metering records even with 0 usage

---

## 6. Compliance and Certification

### 6.1 Required Certifications

| Certification | Status | Notes |
|---------------|--------|-------|
| SOC 2 Type II | Required | Annual renewal |
| ISO 27001 | Recommended | International customers |
| GDPR | Required | EU customers |
| HIPAA | Optional | Healthcare workloads |
| PCI DSS | Optional | Financial workloads |
| IBM Cloud Security | Required | IBM Cloud security review |

### 6.2 Data Residency

Support for data residency requirements:
- US Regions: us-south (Dallas), us-east (Washington DC)
- EU Regions: eu-de (Frankfurt), eu-gb (London)
- APAC Regions: jp-tok (Tokyo), au-syd (Sydney)

### 6.3 IBM Cloud Framework Compliance

- FedRAMP (for US Government)
- IRAP (for Australian Government)
- ISO 27017 (Cloud Security)
- ISO 27018 (Cloud Privacy)

---

## 7. Support and SLA Requirements

### 7.1 Support Tiers

| Tier | Response Time | Channels |
|------|---------------|----------|
| Basic | 24 hours | Email, IBM Cloud Support Center |
| Business | 4 hours | Email, Chat |
| Enterprise | 1 hour | Email, Chat, Phone |

### 7.2 SLA Commitments

- Availability: 99.9% monthly uptime
- Planned maintenance: 4 hours/month maximum
- Data backup: Daily with 30-day retention
- Recovery Point Objective (RPO): 1 hour
- Recovery Time Objective (RTO): 4 hours

---

## 8. Testing Requirements

### 8.1 Pre-Launch Testing Checklist

- [ ] Open Service Broker API catalog endpoint returns valid response
- [ ] Provisioning flow creates service instance successfully
- [ ] Deprovisioning flow removes all resources
- [ ] Service binding creates credentials correctly
- [ ] IBM Cloud IAM integration works for SSO
- [ ] Usage metering reports to IBM Cloud successfully
- [ ] Dashboard URL accessible after provisioning
- [ ] Subscription status visible in application

### 8.2 Integration Testing

Test accounts must be configured in IBM Cloud Marketplace staging environment.
Contact: cloudmarketplace@us.ibm.com

---

## 9. Launch Checklist

### 9.1 Pre-Submission

- [ ] Complete IBM PartnerWorld registration
- [ ] Prepare product logo (128x128, 64x64)
- [ ] Write product description (2000 chars max)
- [ ] Create screenshots (up to 10, 1280x800 min)
- [ ] Prepare pricing dimensions
- [ ] Create architecture diagram (not public)
- [ ] Complete integration testing
- [ ] Implement Open Service Broker API

### 9.2 Submission

- [ ] Submit product via IBM Cloud Provider Workbench
- [ ] Provide service broker URL
- [ ] Upload Helm charts (for OpenShift deployment)
- [ ] Set pricing for all regions
- [ ] Complete legal review
- [ ] Submit for IBM review (5-10 business days)

### 9.3 Post-Launch

- [ ] Monitor subscription metrics
- [ ] Review metering accuracy
- [ ] Collect customer feedback
- [ ] Maintain security compliance
- [ ] Update product documentation

---

## 10. References

- [IBM Cloud Marketplace Provider Guide](https://cloud.ibm.com/docs/marketplace?topic=marketplace-getting-started)
- [Open Service Broker API Specification](https://github.com/openservicebrokerapi/servicebroker/blob/master/spec.md)
- [IBM Cloud SaaS Subscription API](https://cloud.ibm.com/docs/account?topic=account-bp_integration)
- [IBM Cloud IAM Integration](https://cloud.ibm.com/docs/account?topic=account-iamoverview)
- [IBM Cloud Well-Architected Framework](https://cloud.ibm.com/docs/well-architected?topic=well-architected-well-architected)
- [YAWL Documentation](https://yawlfoundation.github.io/yawl/)

---

*Document Version: 1.0*
*Last Updated: February 2025*
*Next Review: May 2025*
