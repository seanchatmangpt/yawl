# Rollback Procedure

**Product:** YAWL Workflow Engine v5.2
**Version:** 1.0
**Last Updated:** 2025-02-13

---

## 1. Overview

This document defines the procedures for rolling back YAWL Workflow Engine v5.2 from cloud marketplaces in case of critical issues.

### 1.1 Scope

| Rollback Type | Description | Impact |
|---------------|-------------|--------|
| Partial Rollback | Single marketplace or region | Limited |
| Platform Rollback | All listings on one platform | Moderate |
| Full Rollback | All marketplaces | Major |

### 1.2 Rollback Triggers

| Trigger | Severity | Rollback Type |
|---------|----------|---------------|
| Security vulnerability (critical) | Critical | Full |
| Data loss or corruption | Critical | Full |
| > 20% deployment failures | High | Platform |
| Pricing error | High | Platform |
| Major feature broken | High | Platform |
| Negative customer impact | Medium | Evaluate |
| Performance degradation | Medium | Evaluate |

---

## 2. Rollback Decision Matrix

### 2.1 Decision Criteria

| Condition | Action | Authority |
|-----------|--------|-----------|
| Critical security issue | Immediate rollback | On-Call Lead |
| > 50% error rate | Immediate rollback | On-Call Lead |
| Customer data at risk | Immediate rollback | On-Call Lead |
| 20-50% error rate | Evaluate in 15 min | Technical Lead |
| Pricing incorrect | Hide listing | Product Manager |
| Feature degraded | Monitor 30 min | Technical Lead |

### 2.2 Authority Levels

| Level | Role | Can Authorize |
|-------|------|---------------|
| 1 | On-Call Engineer | Platform rollback (1 platform) |
| 2 | Technical Lead | Multiple platforms |
| 3 | Engineering Manager | Full rollback |
| 4 | VP Engineering | Emergency full rollback |

---

## 3. Pre-Rollback Checklist

### 3.1 Before Initiating Rollback

- [ ] Issue confirmed and documented
- [ ] Severity assessed
- [ ] Rollback scope determined
- [ ] Appropriate authority obtained
- [ ] Communication team notified
- [ ] Support team notified

### 3.2 Information to Collect

| Information | Value |
|-------------|-------|
| Issue description | |
| Time detected | |
| Affected platforms | |
| Affected customers | |
| Error messages | |
| Current status | |

---

## 4. Rollback Procedures

### 4.1 Partial Rollback (Single Platform)

#### GCP Marketplace

```bash
# Step 1: Hide the listing
gcloud alpha commerce marketplace listings update yawl-workflow-engine \
  --visibility=private \
  --project=yawl-partner-project

# Step 2: Verify listing is hidden
gcloud alpha commerce marketplace listings describe yawl-workflow-engine \
  --project=yawl-partner-project \
  --format="value(visibility)"

# Step 3: Notify existing subscribers
# Via GCP Partner Center messaging

# Step 4: Document the rollback
echo "GCP Marketplace rolled back at $(date)" >> /var/log/rollbacks.log
```

#### AWS Marketplace

```bash
# Step 1: Update listing visibility
aws marketplace-catalog start-change-set \
  --catalog AWSMarketplace \
  --change-set '[{
    "ChangeType": "UpdateInformation",
    "Entity": {"Type": "Product@1.0", "Identifier": "PRODUCT-ID"},
    "Details": "{\"Visibility\": \"Limited\"}"
  }]'

# Step 2: Record change set ID for tracking
CHANGE_SET_ID=$(aws marketplace-catalog list-change-sets \
  --catalog AWSMarketplace \
  --filter "EntityId=PRODUCT-ID" \
  --query "Sort[0].ChangeSetId" \
  --output text)

# Step 3: Monitor change status
aws marketplace-catalog describe-change-set \
  --catalog AWSMarketplace \
  --change-set-id $CHANGE_SET_ID

# Step 4: Notify subscribers via AWS Marketplace Management Portal
```

#### Azure Marketplace

```powershell
# Step 1: Connect to Partner Center
Connect-AzAccount

# Step 2: Update offer status to "Private"
# Via Partner Center UI or API
# Navigate to Offer -> Properties -> Visibility -> Private

# Step 3: Verify status
Get-AzMarketplacePrivateStoreOffer -PrivateStoreId STORE-ID -OfferId yawl-workflow-engine

# Step 4: Document the change
Write-Output "Azure Marketplace rolled back at $(Get-Date)"
```

#### OCI Marketplace

```bash
# Step 1: Contact OCI Partner Support
# OCI may require manual intervention

# Step 2: Submit support ticket
oci support create-ticket \
  --compartment-id $COMPARTMENT_ID \
  --subject "YAWL Marketplace Rollback Request" \
  --description "Requesting visibility change for YAWL Workflow Engine listing"

# Step 3: Follow up with OCI Partner Manager
```

#### IBM Cloud

```bash
# Step 1: Login to IBM Cloud
ibmcloud login

# Step 2: Update catalog entry via Partner Center
# Navigate to https://cloud.ibm.com/partner-center
# Update visibility to Private

# Step 3: Verify via API
ibmcloud catalog entry yawl-workflow-engine
```

#### Teradata

```bash
# Step 1: Contact Teradata Partner Support
# Teradata requires manual intervention for listing changes

# Step 2: Document request
echo "Teradata rollback requested at $(date)" >> /var/log/rollbacks.log
```

### 4.2 Full Rollback (All Platforms)

#### Phase 1: Immediate Actions (0-15 minutes)

```bash
#!/bin/bash
# full-rollback.sh - Execute full marketplace rollback

set -e

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="/var/log/rollback_${TIMESTAMP}.log"

log() {
    echo "[$(date)] $1" | tee -a $LOG_FILE
}

log "=== STARTING FULL ROLLBACK ==="

# Step 1: GCP
log "Rolling back GCP Marketplace..."
gcloud alpha commerce marketplace listings update yawl-workflow-engine \
  --visibility=private \
  --project=yawl-partner-project || log "GCP rollback failed"

# Step 2: AWS
log "Rolling back AWS Marketplace..."
aws marketplace-catalog start-change-set \
  --catalog AWSMarketplace \
  --change-set '[{
    "ChangeType": "UpdateInformation",
    "Entity": {"Type": "Product@1.0", "Identifier": "'${AWS_PRODUCT_ID}'"},
    "Details": "{\"Visibility\": \"Limited\"}"
  }]' || log "AWS rollback failed"

# Step 3: Azure (manual via Partner Center)
log "Azure rollback requires manual action via Partner Center"

# Step 4: OCI (requires support ticket)
log "OCI rollback requires support ticket"

# Step 5: IBM Cloud (manual via Partner Center)
log "IBM Cloud rollback requires manual action via Partner Center"

# Step 6: Teradata (requires support contact)
log "Teradata rollback requires support contact"

log "=== ROLLBACK COMMANDS EXECUTED ==="
log "Manual actions required for: Azure, OCI, IBM, Teradata"
```

#### Phase 2: Communication (15-30 minutes)

```bash
# Step 1: Internal notification
./scripts/notify-internal.sh --message "FULL ROLLBACK INITIATED" --priority critical

# Step 2: Customer notification (if needed)
./scripts/notify-customers.sh --template rollback-notice --platform all

# Step 3: Support team briefing
./scripts/brief-support.sh --issue "Marketplace rollback" --impact "All platforms"

# Step 4: Executive notification
./scripts/notify-executives.sh --status "ROLLBACK IN PROGRESS"
```

#### Phase 3: Verification (30-60 minutes)

```bash
# Step 1: Verify GCP
curl -s "https://console.cloud.google.com/marketplace/product/yawl/yawl-workflow-engine" | grep -q "Not Found" && echo "GCP: Hidden" || echo "GCP: Still visible"

# Step 2: Verify AWS
aws marketplace-catalog describe-entity \
  --catalog AWSMarketplace \
  --entity-id $AWS_PRODUCT_ID \
  --query "Details.Visibility" || echo "AWS: Check manually"

# Step 3: Verify Azure
# Manual check via Azure Portal

# Step 4: Verify other platforms
# Manual verification required
```

---

## 5. Post-Rollback Actions

### 5.1 Immediate Actions

| Action | Owner | Timeline | Status |
|--------|-------|----------|--------|
| Confirm all listings hidden | Tech Lead | +30 min | [ ] |
| Notify affected customers | Support | +1 hour | [ ] |
| Document rollback reason | Tech Lead | +1 hour | [ ] |
| Begin root cause analysis | Engineering | +2 hours | [ ] |

### 5.2 Customer Communication

**Template: Rollback Notification**

```
Subject: Important Update Regarding YAWL Workflow Engine

Dear Customer,

We are writing to inform you of an important update regarding YAWL
Workflow Engine v5.2 on [Platform].

We have temporarily paused new subscriptions while we address
[issue description]. This does not affect existing deployments.

For customers with active deployments:
- Your services will continue to operate normally
- Support remains available 24/7
- We will notify you when new subscriptions resume

For customers evaluating YAWL:
- We apologize for any inconvenience
- Please contact sales@yawl.example.com for alternatives
- We expect to resume new subscriptions within [timeframe]

If you have questions, please contact your account manager or
reach us at support@yawl.example.com.

We appreciate your patience and understanding.

The YAWL Team
```

### 5.3 Internal Communication

**Template: Rollback Status Update**

```
Subject: [STATUS] YAWL Marketplace Rollback - [Time]

ROLLBACK STATUS: [IN PROGRESS / COMPLETE]

Affected Platforms: [List]
Reason: [Brief description]
Current Status: [Status]

Completed Actions:
- [x] Action 1
- [x] Action 2
- [ ] Action 3 (in progress)

Pending Actions:
- [ ] Action 4
- [ ] Action 5

Next Update: [Time]

Launch Commander: [Name]
```

---

## 6. Recovery Procedures

### 6.1 Issue Resolution

| Step | Action | Owner | Timeline |
|------|--------|-------|----------|
| 1 | Root cause analysis | Engineering | +4 hours |
| 2 | Fix development | Engineering | +24 hours |
| 3 | Fix testing | QA | +6 hours |
| 4 | Security review | Security | +4 hours |
| 5 | Staging deployment | DevOps | +2 hours |
| 6 | Production deployment | DevOps | +2 hours |
| 7 | Verification | QA | +2 hours |

### 6.2 Re-Launch Checklist

- [ ] Root cause identified and fixed
- [ ] Fix deployed and verified
- [ ] Security review complete
- [ ] Documentation updated
- [ ] Customer communication prepared
- [ ] Support team briefed
- [ ] Monitoring enhanced
- [ ] Sign-off obtained

### 6.3 Re-Launch Procedure

```bash
# Step 1: Update listings to public
# Follow platform-specific procedures in reverse

# Step 2: Verify listings are live
./scripts/verify-all-listings.sh

# Step 3: Test purchases
./scripts/test-purchases.sh --all-platforms

# Step 4: Notify customers of re-launch
./scripts/notify-customers.sh --template relaunch-notice

# Step 5: Update internal teams
./scripts/notify-internal.sh --message "RE-LAUNCH COMPLETE"
```

---

## 7. Rollback Log

### 7.1 Log Format

```
[YYYY-MM-DD HH:MM:SS] [SEVERITY] [PLATFORM] Message
```

### 7.2 Required Log Entries

| Event | Log Entry |
|-------|-----------|
| Rollback initiated | `ROLLBACK START: [reason]` |
| Platform hidden | `[PLATFORM] HIDDEN: [method]` |
| Verification complete | `[PLATFORM] VERIFIED: [status]` |
| Customer notified | `CUSTOMER NOTIFICATION: [count] customers` |
| Rollback complete | `ROLLBACK COMPLETE: [duration]` |

### 7.3 Log Retention

- Rollback logs retained for 1 year
- Stored in: `/var/log/rollbacks/`
- Backed up to: `s3://yawl-logs/rollbacks/`

---

## 8. Testing Rollback Procedures

### 8.1 Rollback Drill Schedule

| Drill Type | Frequency | Participants |
|------------|-----------|--------------|
| Partial rollback | Quarterly | On-call team |
| Full rollback | Annually | All teams |
| Communication drill | Bi-annually | Marketing, Support |

### 8.2 Drill Checklist

- [ ] Drill scenario defined
- [ ] Test environment prepared
- [ ] Participants briefed
- [ ] Drill executed
- [ ] Results documented
- [ ] Procedures updated

---

## 9. Contacts

### 9.1 Internal Escalation

| Level | Role | Contact |
|-------|------|---------|
| L1 | On-Call Engineer | [phone/pager] |
| L2 | Technical Lead | [phone] |
| L3 | Engineering Manager | [phone] |
| L4 | VP Engineering | [phone] |

### 9.2 Platform Support

| Platform | Support Channel | Priority |
|----------|-----------------|----------|
| GCP | Partner Support | High |
| AWS | Seller Support | High |
| Azure | Partner Center | High |
| OCI | OCI Support | High |
| IBM | IBM Support | High |
| Teradata | Partner Support | High |

---

## 10. Appendix

### 10.1 Rollback Command Reference

| Platform | Hide Command | Show Command |
|----------|--------------|--------------|
| GCP | `visibility=private` | `visibility=public` |
| AWS | `Visibility=Limited` | `Visibility=Public` |
| Azure | Partner Center UI | Partner Center UI |
| OCI | Support ticket | Support ticket |
| IBM | Partner Center UI | Partner Center UI |
| Teradata | Support contact | Support contact |

### 10.2 Communication Templates Location

- `/templates/rollback/customer-notification.txt`
- `/templates/rollback/internal-status.txt`
- `/templates/rollback/relaunch-notice.txt`

### 10.3 Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-02-13 | | Initial version |
