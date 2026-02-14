# AWS Marketplace Requirements for YAWL Workflow Engine

## Executive Summary

This document outlines the complete technical and business requirements for listing YAWL (Yet Another Workflow Language) as a SaaS product on AWS Marketplace. YAWL is a Java-based BPM/Workflow engine with formal foundations, supporting 43+ workflow control-flow patterns.

---

## 1. Business Requirements

### 1.1 Seller Eligibility Requirements

| Requirement | Description | Status |
|------------|-------------|--------|
| AWS Account | Must have an AWS account in good standing | Required |
| Seller Registration | Complete AWS Marketplace seller registration | Required |
| Tax Information | Provide valid tax information for all applicable regions | Required |
| Banking Information | Provide banking details for payment processing | Required |
| Business Verification | Complete business identity verification | Required |

### 1.2 Product Listing Requirements (Effective May 1, 2025)

#### Product Setup Guidelines
- Pricing dimensions cannot be limited to private offers only
- All pricing dimensions must relate to actual software value
- At least one dimension must have price > $0.00 (except Free tier)
- GovCloud products must include "GovCloud" in product title

#### Customer Information Requirements
- SaaS products must be billed entirely through AWS Marketplace dimensions
- Cannot collect customer payment information directly (credit cards, bank accounts)
- Registration page must include email address input field
- Optional fields: Name, ZIP code, Phone, Company information, Product setup preferences
- Must provide English-language view for multi-language support

### 1.3 Product Usage Guidelines
- Customers must be able to create accounts and access web console after subscription
- Existing customers must be able to log in from fulfillment landing page
- Subscription status must be visible within the SaaS application
- Support contact options must be specified on fulfillment landing page
- No redirects to other cloud platforms or off-marketplace upsell services

---

## 2. Technical Architecture Requirements

### 2.1 Hosting Patterns

YAWL supports multiple hosting patterns for AWS Marketplace:

#### Pattern A: Seller-Hosted SaaS (Recommended)
```
+------------------+     +------------------+
|   Seller AWS     |     |   Buyer Access   |
|   Account        |     |                  |
|                  |     |   Web Browser    |
|  +------------+  |     |       |          |
|  | YAWL Engine|<-|-----|-------+          |
|  | (EKS)      |  |     |                  |
|  +------------+  |     +------------------+
|        |         |
|  +-----+-----+   |
|  | RDS       |   |
|  | PostgreSQL|   |
|  +-----------+   |
|        |         |
|  +-----+-----+   |
|  | S3 Bucket |   |
|  | (Persistence)|
|  +-----------+   |
+------------------+
```

#### Pattern B: Buyer-Deployed (SaaS Quick Launch)
- CloudFormation templates deployed to buyer's account
- Seller control plane manages orchestration
- Application plane runs in buyer's account

### 2.2 AWS Services Requirements

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **Amazon EKS** | Container orchestration for YAWL services | Kubernetes 1.28+ |
| **Amazon RDS** | PostgreSQL/MySQL database for workflow state | Multi-AZ, encrypted |
| **Application Load Balancer** | Traffic distribution and SSL termination | HTTPS only |
| **Amazon S3** | Workflow specification storage and logs | Versioning enabled |
| **Amazon SQS** | Message queuing for async processing | Dead-letter queue |
| **Amazon SNS** | Subscription and entitlement notifications | Two topics required |
| **AWS Lambda** | Metering and entitlement handlers | Node.js 20.x / Java 17 |
| **Amazon DynamoDB** | Customer and metering record storage | On-demand capacity |
| **Amazon CloudFront** | CDN for static assets and registration page | Edge-optimized |
| **AWS Secrets Manager** | Database credentials and API keys | Auto-rotation |
| **Amazon CloudWatch** | Logging, metrics, and alarms | Log retention 90 days |
| **AWS CloudTrail** | API audit logging | Multi-region trail |

### 2.3 Network Architecture

#### VPC Configuration
```
VPC (10.0.0.0/16)
+-- Public Subnets (10.0.0.0/20, 10.0.16.0/20)
|   +-- NAT Gateways
|   +-- Application Load Balancer
|
+-- Private Subnets (10.0.64.0/20, 10.0.80.0/20)
|   +-- EKS Worker Nodes
|   +-- YAWL Engine Pods
|
+-- Database Subnets (10.0.128.0/24, 10.0.129.0/24)
    +-- RDS PostgreSQL
```

### 2.4 Security Requirements

#### Identity and Access Management (IAM)
- Service accounts with OIDC federation for EKS pods
- Least privilege access policies
- No hardcoded credentials
- IAM roles for service-to-service communication

#### Encryption
- At rest: AWS KMS with customer-managed keys (CMK)
- In transit: TLS 1.2+ for all communications
- Certificate management via AWS Certificate Manager (ACM)

#### Network Security
- Security groups with minimal required access
- Network ACLs for subnet-level protection
- VPC Flow Logs enabled
- WAF rules for ALB protection

---

## 3. SaaS Integration Requirements

### 3.1 Required API Integrations

Based on pricing model selection, implement the following:

| Pricing Model | Required APIs |
|---------------|---------------|
| SaaS Contracts | `GetEntitlements` (Entitlement Service) |
| SaaS Subscriptions | `BatchMeterUsage` (Metering Service) |
| SaaS Contracts with Consumption | `GetEntitlements` + `BatchMeterUsage` |

### 3.2 Registration Flow

```
1. Customer subscribes on AWS Marketplace
2. Redirect to registration URL with x-amzn-marketplace-token
3. Call ResolveCustomer API to exchange token for CustomerIdentifier
4. Call GetEntitlements API to verify subscription/entitlement
5. Create customer account in YAWL system
6. Persist: CustomerIdentifier, CustomerAWSAccountId, ProductCode
7. Display first-use experience / web console
```

### 3.3 Entitlement Monitoring

Subscribe to SNS topics for real-time updates:

**Entitlement Topic:** `arn:aws:sns:us-east-1:<account>:aws-mp-entitlement-notification-<product-code>`

| Event | Action |
|-------|--------|
| `entitlement-updated` | Call GetEntitlements, update customer store |

**Subscription Topic:** `arn:aws:sns:us-east-1:<account>:aws-mp-subscription-notification-<product-code>`

| Event | Action |
|-------|--------|
| `subscribe-success` | Enable customer access |
| `unsubscribe-pending` | Send final metering records |
| `unsubscribe-success` | Revoke access, archive data |
| `subscribe-fail` | Do not enable resources |

---

## 4. YAWL-Specific Requirements

### 4.1 Service Components

| Component | Description | Container Image |
|-----------|-------------|-----------------|
| **YAWL Engine** | Core workflow execution engine | yawl/engine:5.2 |
| **Resource Service** | Human/non-human resource management | yawl/resource-service:5.2 |
| **Worklet Service** | Dynamic process adaptation | yawl/worklet-service:5.2 |
| **Monitor Service** | Process monitoring dashboard | yawl/monitor-service:5.2 |
| **Scheduling Service** | Calendar-based scheduling | yawl/scheduling-service:5.2 |
| **Cost Service** | Cost tracking and reporting | yawl/cost-service:5.2 |

### 4.2 Database Schema

YAWL requires the following RDS configuration:

```yaml
Database Engine: PostgreSQL 15.x or MySQL 8.x
Instance Class: db.r6g.xlarge (minimum for production)
Storage: 100 GB GP3 (auto-scaling enabled)
Multi-AZ: Yes
Backup Retention: 30 days
Encryption: AWS KMS
```

### 4.3 Persistence Storage

S3 bucket structure for YAWL specifications:

```
s3://yawl-tenant-{account-id}/
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

### 5.2 Metering Schedule

- Frequency: Every hour (required by AWS Marketplace)
- Batch size: Up to 100 records per BatchMeterUsage call
- Timestamp: Must be within 1 hour of current time
- Zero reporting: Send metering records even with 0 usage

### 5.3 Metering Record Format

```json
{
  "customerIdentifier": "ifAPi5AcF3",
  "dimension": "workflow_executions",
  "quantity": 150,
  "timestamp": "2025-01-15T10:00:00Z"
}
```

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

### 6.2 Data Residency

Support for data residency requirements:
- US Regions: us-east-1, us-west-2
- EU Regions: eu-west-1, eu-central-1
- APAC Regions: ap-southeast-1, ap-northeast-1

---

## 7. Support and SLA Requirements

### 7.1 Support Tiers

| Tier | Response Time | Channels |
|------|---------------|----------|
| Basic | 24 hours | Email |
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

- [ ] ResolveCustomer API returns valid CustomerIdentifier
- [ ] GetEntitlements API returns correct entitlement data
- [ ] BatchMeterUsage API accepts metering records
- [ ] SNS notifications received for all subscription events
- [ ] Customer registration flow completes successfully
- [ ] Subscription status visible in application
- [ ] Access revocation works for unsubscribed users
- [ ] Hourly metering job runs without errors

### 8.2 Integration Testing

Test accounts must be allowlisted by AWS Marketplace Seller Operations.
Contact: aws-marketplace-seller-ops@amazon.com

---

## 9. Launch Checklist

### 9.1 Pre-Submission

- [ ] Complete seller registration
- [ ] Prepare product logo (192x192, 64x64)
- [ ] Write product description (2000 chars max)
- [ ] Create screenshots (up to 10, 1280x800 min)
- [ ] Prepare pricing dimensions
- [ ] Create architecture diagram (not public)
- [ ] Complete integration testing

### 9.2 Submission

- [ ] Submit product via AWS Marketplace Management Portal
- [ ] Provide registration URL
- [ ] Upload CloudFormation templates (if SaaS Quick Launch)
- [ ] Set pricing for all regions
- [ ] Complete legal review
- [ ] Submit for AWS review (5-7 business days)

### 9.3 Post-Launch

- [ ] Monitor subscription metrics
- [ ] Review metering accuracy
- [ ] Collect customer feedback
- [ ] Maintain security compliance
- [ ] Update product documentation

---

## 10. References

- [AWS Marketplace SaaS Guidelines](https://docs.aws.amazon.com/marketplace/latest/userguide/saas-guidelines.html)
- [AWS Marketplace Seller Guide](https://docs.aws.amazon.com/marketplace/latest/userguide/)
- [AWS Well-Architected Framework - Security Pillar](https://docs.aws.amazon.com/wellarchitected/latest/security-pillar/)
- [AWS Marketplace Serverless SaaS Integration](https://github.com/aws-samples/aws-marketplace-serverless-saas-integration)
- [YAWL Documentation](https://yawlfoundation.github.io/yawl/)

---

*Document Version: 1.0*
*Last Updated: February 2025*
*Next Review: May 2025*
