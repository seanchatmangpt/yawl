# Amazon Web Services (AWS) Marketplace Checklist

**Product:** YAWL Workflow Engine v5.2
**Marketplace:** AWS Marketplace
**Last Updated:** 2025-02-13

---

## 1. Seller Registration

### 1.1 AWS Seller Account

- [ ] AWS Seller account created
- [ ] Business information completed
- [ ] Tax information submitted (W-9/W-8BEN)
- [ ] Bank account verified for disbursements
- [ ] Two-factor authentication enabled

### 1.2 Marketplace Agreement

- [ ] AWS Marketplace Seller Agreement signed
- [ ] AWS Service Terms accepted
- [ ] Data Processing Addendum signed (if applicable)
- [ ] Revenue share terms acknowledged

---

## 2. Technical Requirements

### 2.1 Amazon Machine Image (AMI)

- [ ] AMI built in all required regions
  - [ ] us-east-1 (N. Virginia)
  - [ ] us-east-2 (Ohio)
  - [ ] us-west-1 (N. California)
  - [ ] us-west-2 (Oregon)
  - [ ] eu-west-1 (Ireland)
  - [ ] eu-west-2 (London)
  - [ ] eu-central-1 (Frankfurt)
  - [ ] ap-northeast-1 (Tokyo)
  - [ ] ap-southeast-1 (Singapore)
  - [ ] ap-southeast-2 (Sydney)
- [ ] AMI ID: ami-_____________
- [ ] AMI scanned by AWS Inspector
- [ ] AMI follows AWS best practices
  - [ ] No hardcoded secrets
  - [ ] SSH key via EC2 key pair
  - [ ] Cloud-init/userdata support
  - [ ] Proper shutdown handling

### 2.2 Container Image (ECS/EKS)

- [ ] Image published to Amazon ECR Public Gallery
- [ ] Public Gallery URL: gallery.ecr.aws/_______/yawl
- [ ] Multi-architecture manifest (amd64, arm64)
- [ ] Image scanning enabled and passed

### 2.3 Kubernetes (EKS)

- [ ] Helm chart validated for EKS
- [ ] EKS version compatibility: 1.28, 1.29, 1.30
- [ ] EKS Add-on submission (optional)
- [ ] AWS Load Balancer Controller compatible
- [ ] EBS CSI Driver compatible

### 2.4 CloudFormation Templates

- [ ] CloudFormation template created
- [ ] Template URL: _____________
- [ ] Template follows AWS best practices
  - [ ] Parameters documented
  - [ ] Outputs defined
  - [ ] Mappings for instance types
  - [ ] Metadata with documentation
- [ ] Tested in all supported regions
- [ ] Nested stacks for complex deployments

---

## 3. AWS Service Integration

### 3.1 Core Services

| Service | Integration Type | Status |
|---------|-----------------|--------|
| RDS (PostgreSQL/MySQL) | Database | Pending |
| Aurora | Database | Pending |
| ElastiCache (Redis) | Caching | Pending |
| S3 | Object Storage | Pending |
| SQS | Messaging | Pending |
| SNS | Notifications | Pending |
| Secrets Manager | Secrets | Pending |
| Parameter Store | Configuration | Pending |
| KMS | Encryption | Pending |
| IAM | Identity | Pending |
| CloudWatch | Logging/Metrics | Pending |
| X-Ray | Tracing | Pending |
| Cognito | Authentication | Pending |

### 3.2 IAM Requirements

- [ ] IAM policy document (least privilege)
- [ ] Service role template provided
- [ ] Instance profile defined
- [ ] Cross-account role (if needed)
- [ ] Permission boundaries documented

### 3.3 Networking

- [ ] VPC deployment supported
- [ ] Public subnet deployment supported
- [ ] Private subnet + NAT deployment supported
- [ ] VPC Peering compatible
- [ ] PrivateLink supported
- [ ] Security group templates provided

---

## 4. Security Requirements

### 4.1 Security Scans

- [ ] Amazon Inspector scan passed
- [ ] Trivy vulnerability scan - 0 critical/high
- [ ] SAST analysis completed
- [ ] Penetration testing completed (with AWS approval)

### 4.2 Security Features

- [ ] AWS WAF integration documented
- [ ] AWS Shield compatible
- [ ] GuardDuty integration documented
- [ ] Security Hub integration
- [ ] AWS Config rules compatibility
- [ ] Macie compatibility (if handling PII)

### 4.3 Compliance

- [ ] AWS Artifact documents reviewed
- [ ] HIPAA eligibility (if applicable)
- [ ] PCI DSS compliance (if applicable)
- [ ] FedRAMP authorization (if applicable)
- [ ] SOC compliance documented

---

## 5. Product Listing

### 5.1 Basic Information

| Field | Content | Status |
|-------|---------|--------|
| Product title | YAWL Workflow Engine | Pending |
| SKU | yawl-workflow-engine-v5 | Pending |
| Short description (350 chars) | [Description] | Pending |
| Long description (5,000 chars) | [Full description] | Pending |
| Categories | Business Software, Developer Tools | Pending |
| Search keywords | workflow, bpmn, automation | Pending |

### 5.2 Pricing Models

**Option 1: AMI Pricing (Hourly)**

| Instance Type | Hourly Rate | Monthly Est. |
|---------------|-------------|--------------|
| t3.medium | $0.___ | $___ |
| t3.large | $0.___ | $___ |
| m5.large | $0.___ | $___ |
| m5.xlarge | $0.___ | $___ |
| c5.large | $0.___ | $___ |

**Option 2: Container Pricing**

| Dimension | Rate |
|-----------|------|
| Per vCPU/hour | $0.___ |
| Per GB/hour | $0.___ |

**Option 3: SaaS Pricing**

| Tier | Monthly | Annual |
|------|---------|--------|
| Starter | $___ | $___ |
| Professional | $___ | $___ |
| Enterprise | $___ | $___ |

- [ ] Free tier/trial available (___ days)
- [ ] Annual commitment discount (___%)
- [ ] Volume pricing tiers defined

### 5.3 Visual Assets

| Asset | Requirements | Status |
|-------|--------------|--------|
| Product logo | 240x240 PNG, < 500KB | Pending |
| Hero image | 1300x350 PNG/JPG | Pending |
| Screenshots | Up to 15, 1280x1024+ | Pending |
| Video | YouTube link | Pending |

### 5.4 Support Information

- [ ] Support URL provided
- [ ] Support contact email
- [ ] Support phone (enterprise tier)
- [ ] SLA documentation link

---

## 6. Delivery Methods

### 6.1 AMI Delivery

- [ ] Single AMI delivery configured
- [ ] 1-Click deployment tested
- [ ] Upgrade from previous version tested
- [ ] Instance family support documented

### 6.2 Container Delivery

- [ ] ECR Public Gallery listing created
- [ ] Container deployment tested on ECS
- [ ] Container deployment tested on EKS
- [ ] Fargate compatibility verified

### 6.3 SaaS Delivery (if applicable)

- [ ] SaaS subscription configured
- [ ] AWS SaaS Boost integration (optional)
- [ ] Tenant isolation verified
- [ ] Metering integration tested

---

## 7. Testing Requirements

### 7.1 Deployment Testing

| Test | Result | Notes |
|------|--------|-------|
| 1-Click deployment | | |
| CloudFormation deployment | | |
| EKS deployment | | |
| ECS deployment | | |
| Upgrade from v5.1 | | |
| Cross-region deployment | | |

### 7.2 Performance Benchmarks

| Instance Type | Metric | Value |
|---------------|--------|-------|
| t3.large | Startup time | ___ sec |
| t3.large | Throughput | ___ tps |
| m5.xlarge | Throughput | ___ tps |
| m5.xlarge | Latency P99 | ___ ms |

### 7.3 Compatibility Testing

- [ ] Amazon Linux 2023
- [ ] Ubuntu 22.04 LTS
- [ ] RHEL 8/9
- [ ] Windows Server 2022 (if applicable)

---

## 8. Billing & Metering

### 8.1 Usage Metering

- [ ] Metering records format validated
- [ ] Hourly metering tested
- [ ] Usage dimension mapping defined
- [ ] Metering API integration tested

### 8.2 Entitlement Management

- [ ] Entitlement API integration tested
- [ ] Subscription state handling verified
- [ ] Grace period handling documented

---

## 9. AWS Well-Architected Review

### 9.1 Framework Pillars

| Pillar | Score | Notes |
|--------|-------|-------|
| Operational Excellence | /100 | |
| Security | /100 | |
| Reliability | /100 | |
| Performance Efficiency | /100 | |
| Cost Optimization | /100 | |
| Sustainability | /100 | |

### 9.2 Review Status

- [ ] Self-review completed
- [ ] AWS Partner review (if applicable)
- [ ] Improvement items documented

---

## 10. Launch Checklist

### Pre-Launch (2 weeks)

- [ ] Product listing draft submitted
- [ ] AWS Seller Operations review requested
- [ ] Pricing finalized
- [ ] Support documentation published
- [ ] Marketing assets approved

### Launch Day

- [ ] Listing status: Live
- [ ] Test purchase completed
- [ ] Deployment verified
- [ ] Support team alerted
- [ ] Analytics tracking enabled

### Post-Launch (30 days)

- [ ] Daily deployment monitoring
- [ ] Customer feedback review
- [ ] Issue tracking and resolution
- [ ] Usage analytics review
- [ ] Customer review solicitation

---

## 11. Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Engineering Lead | | | |
| Security Lead | | | |
| Product Manager | | | |
| AWS Partner Manager | | | |

---

## Appendix: AWS-Specific Resources

- [AWS Marketplace Seller Guide](https://docs.aws.amazon.com/marketplace/latest/userguide/marketplace-sellers.html)
- [AMI Best Practices](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AMIs.html)
- [EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
