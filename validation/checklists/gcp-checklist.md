# Google Cloud Platform (GCP) Marketplace Checklist

**Product:** YAWL Workflow Engine v5.2
**Marketplace:** Google Cloud Marketplace
**Last Updated:** 2025-02-13

---

## 1. Partner Registration

### 1.1 Google Partner Account

- [ ] Google Cloud Partner account created
- [ ] Partner profile completed
- [ ] Tax documentation submitted (W-9/W-8BEN)
- [ ] Bank account verified for payouts
- [ ] Two-factor authentication enabled

### 1.2 Marketplace Agreement

- [ ] Google Cloud Marketplace Terms of Service accepted
- [ ] Google Cloud Platform Distribution Agreement signed
- [ ] Revenue share terms acknowledged
- [ ] Support SLA terms accepted

---

## 2. Technical Requirements

### 2.1 Container Requirements

- [ ] Container image published to Google Artifact Registry
- [ ] Image follows Google Container最佳实践
  - [ ] Base image from Google-approved sources
  - [ ] Non-root user configured
  - [ ] HEALTHCHECK instruction present
  - [ ] Signals handled properly (SIGTERM)
  - [ ] No secrets in image layers
- [ ] Image scanning passed (Container Analysis API)
- [ ] Tagged with semantic versioning (v5.2.0)
- [ ] Signed with Cosign/Binary Authorization

### 2.2 Kubernetes Deployment

- [ ] Helm chart published to Artifact Registry
- [ ] Chart follows best practices
  - [ ] values.yaml with sensible defaults
  - [ ] README.md with installation instructions
  - [ ] NOTES.txt for post-install guidance
  - [ ] PodDisruptionBudget defined
  - [ ] NetworkPolicy configured
- [ ] Deployed successfully on GKE Autopilot
- [ ] Deployed successfully on GKE Standard
- [ ] Workload Identity configured

### 2.3 Terraform Module (Optional)

- [ ] Published to Terraform Registry
- [ ] Google Cloud Provider >= 4.0 compatible
- [ ] Variables documented
- [ ] Outputs documented
- [ ] Example configurations provided

---

## 3. Integration Requirements

### 3.1 Google Cloud Services Integration

| Service | Integration Type | Status |
|---------|-----------------|--------|
| Cloud SQL | Database | Pending |
| Cloud Spanner | Database | Pending |
| Memorystore (Redis) | Caching | Pending |
| Cloud Storage | Object Storage | Pending |
| Pub/Sub | Messaging | Pending |
| Secret Manager | Secrets | Pending |
| Cloud KMS | Encryption | Pending |
| Cloud IAM | Identity | Pending |
| Cloud Logging | Logging | Pending |
| Cloud Monitoring | Metrics | Pending |
| Cloud Trace | Tracing | Pending |
| Cloud Armor | WAF | Pending |
| Identity Platform | Auth | Pending |

### 3.2 IAM Permissions

- [ ] Minimal permission principle documented
- [ ] Custom roles defined if needed
- [ ] Service account templates provided
- [ ] Organization policy constraints documented

### 3.3 Networking

- [ ] VPC-native deployment supported
- [ ] Private Google Access supported
- [ ] Private Service Connect compatible
- [ ] Shared VPC compatible
- [ ] IAP (Identity-Aware Proxy) integration documented

---

## 4. Security Requirements

### 4.1 Security Scanner Validation

- [ ] Container Analysis API scan - 0 critical/high vulnerabilities
- [ ] Web Security Scanner - No critical findings
- [ ] Binary Authorization policy compliance

### 4.2 Security Configuration

- [ ] VPC Service Controls compatible
- [ ] Confidential VMs supported (optional)
- [ ] Customer-managed encryption keys (CMEK) supported
- [ ] Binary Authorization attestations generated
- [ ] Security Command Center integration documented

### 4.3 Compliance

- [ ] SOC 2 compliance documentation
- [ ] GDPR data processing documentation
- [ ] Data residency options documented

---

## 5. Listing Requirements

### 5.1 Product Information

| Field | Content | Status |
|-------|---------|--------|
| Product name | YAWL Workflow Engine | Confirmed |
| Short description (100 chars) | Enterprise workflow automation engine | Pending review |
| Long description (2,000 chars) | [Full description] | Pending |
| Category | Developer Tools / Business Applications | Pending |
| Tags | workflow, bpmn, automation, orchestration | Pending |

### 5.2 Pricing

- [ ] Pricing model selected (PAYG / Subscription / BYOL)
- [ ] Pricing tier(s) defined
- [ ] Free trial available (14 days recommended)
- [ ] Cost estimator provided
- [ ] Commitment discounts (if applicable)

| Tier | vCPUs | Memory | Monthly Price |
|------|-------|--------|---------------|
| Starter | 2 | 4 GB | $___ |
| Standard | 4 | 8 GB | $___ |
| Enterprise | 8 | 16 GB | $___ |

### 5.3 Visual Assets

| Asset | Specs | Status |
|-------|-------|--------|
| Product icon | 128x128 PNG, < 50KB | Pending |
| Hero banner | 2560x1440 PNG/JPG | Pending |
| Screenshots | Up to 10, 1280x800 minimum | Pending |
| Video | YouTube link, 2-5 minutes | Pending |

### 5.4 Documentation Links

- [ ] Getting Started Guide
- [ ] Architecture documentation
- [ ] API reference
- [ ] Support information
- [ ] Privacy policy
- [ ] Terms of service

---

## 6. Testing Requirements

### 6.1 Integration Testing

- [ ] Single-click deployment tested
- [ ] Upgrade path tested (v5.1 -> v5.2)
- [ ] Rollback tested
- [ ] Multi-region deployment tested
- [ ] High availability deployment tested

### 6.2 Performance Testing

| Test | Configuration | Result | Pass/Fail |
|------|--------------|--------|-----------|
| Startup time | n1-standard-4 | ___ sec | |
| Throughput | n1-standard-4 | ___ tps | |
| Latency P99 | n1-standard-4 | ___ ms | |
| Max scale | 100 nodes | ___ tps | |

### 6.3 Compatibility Testing

- [ ] GKE version: 1.28, 1.29, 1.30
- [ ] GKE Autopilot
- [ ] GKE Standard
- [ ] Compute Engine (standalone)
- [ ] Cloud Run (if applicable)

---

## 7. Support Requirements

### 7.1 Support Tiers

| Tier | Response Time | Channels | Price |
|------|--------------|----------|-------|
| Basic | 24 hours | Email | Included |
| Developer | 8 hours | Email, Chat | $___/month |
| Production | 4 hours | Email, Chat, Phone | $___/month |
| Enterprise | 1 hour | All + Dedicated TAM | $___/month |

### 7.2 Support Documentation

- [ ] Support escalation path defined
- [ ] Support contact information provided
- [ ] Issue resolution SLAs documented
- [ ] Customer self-service portal available

---

## 8. Launch Checklist

### Pre-Launch (2 weeks before)

- [ ] Final listing review with Google TAM
- [ ] Pricing confirmed and entered
- [ ] All assets uploaded and approved
- [ ] Documentation links validated
- [ ] Support team briefed

### Launch Day

- [ ] Listing status changed to "Public"
- [ ] Deployment tested by QA team
- [ ] Monitoring alerts configured
- [ ] Support team on standby
- [ ] Marketing announcement ready

### Post-Launch (First 30 days)

- [ ] Daily monitoring of deployment success rate
- [ ] Customer feedback collected
- [ ] Issue resolution < 24 hours
- [ ] Usage metrics tracked
- [ ] Review solicitation started

---

## 9. GCP-Specific Notes

### Billing Integration

- [ ] Usage metering via Google Cloud Billing API
- [ ] Entitlement management configured
- [ ] Procurement API integration tested

### Recommended Configurations

**Minimum Production Deployment:**
```yaml
nodeCount: 3
machineType: n1-standard-4
diskSize: 100GB
region: us-central1
```

**High Availability Deployment:**
```yaml
nodeCount: 5
machineType: n1-standard-8
diskSize: 200GB
regions:
  - us-central1
  - us-east1
```

---

## 10. Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Engineering Lead | | | |
| Security Lead | | | |
| Product Manager | | | |
| Google TAM | | | |

---

## Appendix: Useful Links

- [Google Cloud Marketplace Partner Portal](https://console.cloud.google.com/partner)
- [Container Analysis Documentation](https://cloud.google.com/container-analysis)
- [GKE Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Marketplace Solutions Documentation](https://cloud.google.com/marketplace/docs)
