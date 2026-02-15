# Launch Runbook

**Product:** YAWL Workflow Engine v5.2
**Version:** 1.0
**Last Updated:** 2025-02-13

---

## 1. Overview

This runbook provides step-by-step procedures for launching YAWL Workflow Engine v5.2 on all target cloud marketplaces.

### 1.1 Scope

| Platform | Launch Type | Region(s) |
|----------|-------------|-----------|
| GCP Marketplace | Full Launch | Global |
| AWS Marketplace | Full Launch | Global |
| Azure Marketplace | Full Launch | Global |
| OCI Marketplace | Full Launch | Global |
| IBM Cloud | Full Launch | Global |
| Teradata Marketplace | Full Launch | Global |

### 1.2 Key Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| Launch Commander | | | |
| Technical Lead | | | |
| Marketing Lead | | | |
| Support Lead | | | |
| On-Call Engineer | | | |

---

## 2. Pre-Launch Checklist (T-24 Hours)

### 2.1 Final Validation

- [ ] All sign-off documents approved
  - [ ] Technical Sign-Off
  - [ ] Security Sign-Off
  - [ ] Legal Sign-Off
  - [ ] Marketing Sign-Off
- [ ] All marketplace listings reviewed and approved
- [ ] Pricing verified on all platforms
- [ ] Documentation links validated
- [ ] Support team briefed and ready

### 2.2 Infrastructure Verification

```bash
# Verify container images
docker pull yawl/yawl-engine:5.2.0
docker pull yawl/yawl-resource:5.2.0

# Verify Helm chart
helm pull yawl/yawl-engine --version 5.2.0

# Run validation script
./validation/validation-scripts/validate-all.sh --cloud all
```

### 2.3 Communication Preparation

- [ ] Internal announcement email drafted
- [ ] Customer notification email prepared
- [ ] Press release final and approved
- [ ] Social media content scheduled
- [ ] Blog post scheduled

---

## 3. Launch Day Procedures (T-0)

### 3.1 Launch Timeline

| Time (UTC) | Activity | Owner | Status |
|------------|----------|-------|--------|
| 08:00 | Pre-launch standup meeting | Launch Commander | |
| 08:30 | Final marketplace review | Technical Lead | |
| 09:00 | GCP Marketplace go-live | Platform Team | |
| 09:30 | AWS Marketplace go-live | Platform Team | |
| 10:00 | Azure Marketplace go-live | Platform Team | |
| 10:30 | OCI Marketplace go-live | Platform Team | |
| 11:00 | IBM Cloud go-live | Platform Team | |
| 11:30 | Teradata Marketplace go-live | Platform Team | |
| 12:00 | Verify all listings live | QA Team | |
| 12:30 | Test purchases (all platforms) | QA Team | |
| 13:00 | Press release distribution | Marketing | |
| 13:00 | Social media posts | Marketing | |
| 14:00 | Customer email notification | Marketing | |
| 15:00 | Internal announcement | Launch Commander | |
| 17:00 | End-of-day status check | Launch Commander | |

### 3.2 Platform-Specific Launch Procedures

#### GCP Marketplace Launch

```bash
# Step 1: Verify Partner Center access
gcloud auth login
gcloud config set project yawl-partner-project

# Step 2: Check listing status
gcloud alpha commerce marketplace listings describe yawl-workflow-engine

# Step 3: Update listing to public
gcloud alpha commerce marketplace listings update yawl-workflow-engine \
  --visibility=public

# Step 4: Verify listing is live
curl -s "https://console.cloud.google.com/marketplace/product/yawl-workflow-engine" | grep -q "YAWL"
echo "GCP Marketplace status: $?"
```

#### AWS Marketplace Launch

```bash
# Step 1: Verify AWS Marketplace Management Portal access
aws marketplace-catalog describe-entity \
  --catalog AWSMarketplace \
  --entity-id PRODUCT-ID

# Step 2: Update listing visibility
aws marketplace-catalog start-change-set \
  --catalog AWSMarketplace \
  --change-set '[{
    "ChangeType": "UpdateInformation",
    "Entity": {"Type": "Product@1.0", "Identifier": "PRODUCT-ID"},
    "Details": "{\"Visibility\": \"Public\"}"
  }]'

# Step 3: Verify listing
aws marketplace-catalog describe-entity \
  --catalog AWSMarketplace \
  --entity-id PRODUCT-ID \
  --query "Details"
```

#### Azure Marketplace Launch

```powershell
# Step 1: Connect to Partner Center
Connect-AzAccount

# Step 2: Verify offer status
Get-AzMarketplacePrivateStoreOffer -PrivateStoreId STORE-ID -OfferId yawl-workflow-engine

# Step 3: Update offer to live
# Via Partner Center UI or API

# Step 4: Verify listing
Invoke-WebRequest -Uri "https://azuremarketplace.microsoft.com/en-us/marketplace/apps/yawl.yawl-workflow-engine"
```

#### OCI Marketplace Launch

```bash
# Step 1: Verify OCI CLI configuration
oci os ns get

# Step 2: Check listing via OCI Console
# https://cloud.oracle.com/marketplace

# Step 3: Contact OCI Partner team to activate listing
# (OCI may require manual activation by Oracle team)
```

#### IBM Cloud Launch

```bash
# Step 1: Login to IBM Cloud
ibmcloud login

# Step 2: Verify catalog entry
ibmcloud catalog search yawl-workflow-engine

# Step 3: Update visibility via Partner Center
# https://cloud.ibm.com/partner-center
```

#### Teradata Marketplace Launch

```bash
# Step 1: Contact Teradata Partner team
# Teradata may require manual activation

# Step 2: Verify listing
# https://www.teradata.com/Products/Software-Partners
```

### 3.3 Post-Live Verification

For each platform:

```bash
# Test deployment from marketplace
# Step 1: Create new test project/account
# Step 2: Deploy from marketplace listing
# Step 3: Verify health endpoint
curl https://test-deployment.example.com/health

# Step 4: Run smoke tests
./tests/smoke-test.sh --platform gcp --environment test

# Step 5: Verify monitoring
# Check dashboards for the new deployment
```

---

## 4. Test Purchase Procedures

### 4.1 Test Account Setup

| Platform | Test Account | Notes |
|----------|--------------|-------|
| GCP | test-gcp@example.com | Internal testing account |
| AWS | test-aws@example.com | AWS testing account |
| Azure | test-azure@example.com | Azure testing account |
| OCI | test-oci@example.com | OCI testing account |
| IBM | test-ibm@example.com | IBM testing account |
| Teradata | test-td@example.com | Teradata testing account |

### 4.2 Test Purchase Flow

1. **Subscribe to listing**
   - Navigate to marketplace listing
   - Click "Subscribe" or "Get it now"
   - Select pricing tier
   - Configure deployment

2. **Verify deployment**
   - Check deployment status
   - Verify resource creation
   - Access application UI

3. **Verify billing**
   - Confirm subscription active
   - Verify metering (if applicable)

4. **Test cancellation** (if applicable)
   - Unsubscribe from listing
   - Verify resource cleanup

---

## 5. Monitoring During Launch

### 5.1 Key Metrics to Monitor

| Metric | Threshold | Alert |
|--------|-----------|-------|
| Deployment Success Rate | > 95% | < 90% |
| API Error Rate | < 1% | > 5% |
| Response Time P99 | < 500ms | > 1000ms |
| Support Tickets | Baseline | > 2x baseline |
| Marketplace Reviews | N/A | < 3 stars |

### 5.2 Monitoring Dashboard

Access real-time dashboards at:

- GCP: https://console.cloud.google.com/monitoring
- AWS: https://console.aws.amazon.com/cloudwatch
- Azure: https://portal.azure.com/#blade/Microsoft_Azure_Monitoring
- Internal: https://monitoring.yawl.internal

### 5.3 Alert Response

| Alert Type | Response Time | Escalation |
|------------|---------------|------------|
| Critical | 5 minutes | On-call -> Lead -> Manager |
| High | 15 minutes | On-call -> Lead |
| Medium | 1 hour | On-call |

---

## 6. Rollback Procedures

### 6.1 Partial Rollback (Single Platform)

```bash
# Step 1: Identify affected platform
PLATFORM="gcp"  # or aws, azure, etc.

# Step 2: Hide marketplace listing
# Via marketplace management console

# Step 3: Communicate status
./scripts/notify-stakeholders.sh --platform $PLATFORM --status "rolled-back"

# Step 4: Investigate and fix
./scripts/investigate-issue.sh --platform $PLATFORM
```

### 6.2 Full Rollback (All Platforms)

```bash
# Step 1: Halt all launches
./scripts/emergency-halt.sh

# Step 2: Hide all marketplace listings
./scripts/hide-all-listings.sh

# Step 3: Communicate to stakeholders
./scripts/notify-all-stakeholders.sh --status "rolled-back"

# Step 4: Execute rollback procedure
# See rollback-procedure.md for detailed steps
```

### 6.3 Rollback Decision Matrix

| Condition | Action |
|-----------|--------|
| Single platform deployment failure | Rollback single platform |
| Security vulnerability discovered | Full rollback |
| Pricing error | Rollback affected platform(s) |
| Performance degradation | Monitor, decide in 30 min |
| Negative customer feedback | Assess severity, partial rollback |

---

## 7. Communication Templates

### 7.1 Internal Launch Announcement

```
Subject: YAWL Workflow Engine v5.2 - Multi-Cloud Marketplace Launch

Team,

I'm excited to announce that YAWL Workflow Engine v5.2 is now live on
all major cloud marketplaces:

- Google Cloud Marketplace
- AWS Marketplace
- Azure Marketplace
- Oracle Cloud Marketplace
- IBM Cloud Marketplace
- Teradata Marketplace

Key highlights:
- [Feature 1]
- [Feature 2]
- [Feature 3]

Please direct any customer inquiries to the support team.

Congratulations to everyone who contributed to this launch!

[Launch Commander]
```

### 7.2 Customer Notification

```
Subject: YAWL Workflow Engine v5.2 Now Available on [Platform]

Dear Customer,

We're pleased to announce that YAWL Workflow Engine v5.2 is now
available on [Platform] Marketplace.

What's new in v5.2:
- [Enhancement 1]
- [Enhancement 2]
- [Enhancement 3]

Getting started is easy:
1. Visit [Marketplace Link]
2. Click "Subscribe"
3. Follow the setup wizard

For questions or assistance, contact support@yawl.example.com.

Best regards,
The YAWL Team
```

### 7.3 Rollback Notification

```
Subject: [URGENT] YAWL Marketplace Launch - Temporary Hold

Team,

Due to [issue description], we have temporarily paused the marketplace
launch for [platform/all platforms].

Current status: [status]
Impact: [impact assessment]
Next update: [time]

Please hold any external communications until further notice.

[Launch Commander]
```

---

## 8. Post-Launch Activities

### 8.1 Day 1 (Launch Day)

- [ ] All platforms verified live
- [ ] Test purchases completed
- [ ] Press release distributed
- [ ] Social media posted
- [ ] Customer emails sent
- [ ] Internal announcement made
- [ ] Monitoring dashboards active
- [ ] Support team on standby

### 8.2 Day 2-3

- [ ] Review marketplace analytics
- [ ] Address initial customer feedback
- [ ] Monitor deployment success rates
- [ ] Begin post-launch retrospective prep

### 8.3 Week 1

- [ ] Conduct post-launch retrospective
- [ ] Document lessons learned
- [ ] Address any outstanding issues
- [ ] Update runbook based on experience
- [ ] Review and respond to marketplace reviews

---

## 9. Emergency Contacts

### 9.1 Internal Escalation

| Level | Role | Contact | Response Time |
|-------|------|---------|---------------|
| L1 | On-Call Engineer | [phone] | 5 min |
| L2 | Technical Lead | [phone] | 15 min |
| L3 | Engineering Manager | [phone] | 30 min |
| L4 | VP Engineering | [phone] | 1 hour |

### 9.2 Platform Support

| Platform | Support Channel | Response Time |
|----------|-----------------|---------------|
| GCP | Partner Support Portal | 24 hours |
| AWS | AWS Seller Support | 24 hours |
| Azure | Partner Center Support | 24 hours |
| OCI | OCI Support Portal | 24 hours |
| IBM | IBM Support | 24 hours |
| Teradata | Partner Support | 24 hours |

---

## 10. Appendix

### 10.1 Environment Variables

```bash
export YAWL_VERSION="5.2.0"
export LAUNCH_DATE="2025-02-XX"
export DRY_RUN="false"  # Set to true for rehearsal
```

### 10.2 Useful Commands

```bash
# Check all platform statuses
./scripts/check-platform-status.sh --all

# Verify container images
./scripts/verify-images.sh --version 5.2.0

# Generate launch report
./scripts/generate-launch-report.sh --date $(date +%Y-%m-%d)
```

### 10.3 Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-02-13 | | Initial version |
