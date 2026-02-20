# GCP Marketplace Launch - Blocking Issues Checklist
## YAWL v6.0.0 - Critical Path to Market

**Updated**: 2026-02-20 | **Status**: 5 BLOCKING ISSUES IDENTIFIED

---

## BLOCK-1: Multi-Tenancy Isolation
**Severity**: CRITICAL | **Effort**: 1-2 weeks | **Risk**: HIGH

### Problem
- JWT validates tenantId claim but never enforces it at query level
- YEngine returns data without tenant filtering
- Cross-customer data breach possible
- GCP Marketplace will reject single-tenant design

### Files to Modify
1. `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`
   - Add `getCurrentTenantId()` to SecurityContext
   - Filter all queries: `findByIdAndTenantId(id, tenantId)`

2. `/home/user/yawl/src/org/yawlfoundation/yawl/elements/YCase.java`
   - Add `tenantId: String` field
   - Update `@Entity` mapping

3. `/home/user/yawl/src/org/yawlfoundation/yawl/elements/YWorkItem.java`
   - Add `tenantId: String` field
   - Update `@Entity` mapping

4. `/home/user/yawl/src/org/yawlfoundation/yawl/logging/YEventLogger.java`
   - Include `tenantId` in all audit logs

### Tests Required
- [ ] 80%+ line coverage on tenant isolation
- [ ] 70%+ branch coverage on tenant filtering
- [ ] Cross-tenant query returns empty (not other tenant's data)
- [ ] Case launched by tenant A not visible to tenant B

### Definition of Done
- [ ] All YEngine queries include tenant filter
- [ ] Audit logs include tenantId
- [ ] Schema migration tested on staging
- [ ] Multi-tenant integration tests passing
- [ ] Security code review signed off

---

## BLOCK-2: Per-Tenant Resource Quotas
**Severity**: CRITICAL | **Effort**: 1.5 weeks | **Risk**: HIGH

### Problem
- UsageMeter.checkUsageAllowed() exists but is NEVER CALLED
- No enforcement of cases/workitems/storage limits
- Single customer can DoS the service
- Runaway costs: no billing controls

### Files to Create/Modify
1. **New**: `/home/user/yawl/src/org/yawlfoundation/yawl/quota/QuotaManager.java`
   - Track per-tenant usage
   - Check against entitlement limits
   - Throw QuotaExceededException

2. **New**: `/home/user/yawl/src/org/yawlfoundation/yawl/quota/QuotaCheckFilter.java`
   - Servlet filter intercepts API calls
   - Calls QuotaManager.checkQuota() before operation
   - Returns HTTP 429 if quota exceeded

3. `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`
   - Call quotaManager.checkQuota("LAUNCH_CASE") in launchCase()
   - Call quotaManager.checkQuota("EXECUTE_WORKITEM") before execution

4. `/home/user/yawl/billing/gcp/UsageMeter.java`
   - Implement quota metrics retrieval from GCP entitlements
   - Cache entitlement terms (5 min TTL)

### Quota Metrics
- [ ] Cases per month: default 10,000
- [ ] Active cases: default 100
- [ ] Work items per day: default 50,000
- [ ] GCS storage GB: default 100 GB
- [ ] API calls per hour: default 100,000

### Tests Required
- [ ] Launch case at quota limit → success
- [ ] Launch case above quota → HTTP 429
- [ ] Quota reset after month boundary
- [ ] Quota check skipped for admin role (optional)

### Definition of Done
- [ ] QuotaCheckFilter registered in web.xml
- [ ] All main operations check quotas
- [ ] HTTP 429 response includes Retry-After header
- [ ] Entitlements synced from GCP (mock for testing)
- [ ] Integration test suite passes

---

## BLOCK-3: LGPL License Disclosure
**Severity**: HIGH | **Effort**: 1 week | **Risk**: LOW

### Problem
- YAWL depends on LGPL libraries: H2, HSQLDB
- License compliance required for GCP Marketplace
- Must disclose if source modifications are made
- Currently documented in LICENSES.md but incomplete

### Files to Create/Modify
1. **New**: `/home/user/yawl/THIRD-PARTY-LICENSES/` (directory)
   - Copy license texts for all dependencies
   - Include LGPL-2.1.txt and LGPL-3.0.txt

2. **New**: `/home/user/yawl/LGPL-COMPLIANCE-FORM.md`
   - Certify that H2/HSQLDB are dynamically linked (not embedded)
   - State any source modifications to LGPL libraries
   - Include attestation template for customers

3. Update: `/home/user/yawl/marketplace/gcp/solution.yaml`
   - Change: license.name from bare "LGPL-3.0" to "LGPL v3.0 with dynamic linking"
   - Add link: "See LICENSES.md and THIRD-PARTY-LICENSES directory"

4. Update: `/home/user/yawl/LICENSES.md`
   - Add section: "LGPL Compliance Strategy"
   - Explain dynamic linking approach
   - Reference LGPL-COMPLIANCE-FORM.md

### License Texts to Include
- [ ] LGPL-2.1.txt (for H2, HSQLDB)
- [ ] LGPL-3.0.txt (if using YAWL under LGPL 3.0)
- [ ] MPL-2.0.txt (H2 dual license)
- [ ] Apache-2.0.txt (for Apache libraries)
- [ ] MIT.txt (for MIT libraries)
- [ ] Eclipse-Public-License-1.0.txt (for EPL libraries)

### CI/CD Check Required
- [ ] Prevent static linking of LGPL JARs (check build artifacts)
- [ ] Alert if new LGPL dependency introduced
- [ ] Validate all license texts present before release

### Definition of Done
- [ ] THIRD-PARTY-LICENSES directory complete
- [ ] LGPL-COMPLIANCE-FORM signed
- [ ] solution.yaml updated
- [ ] CI/CD checks in place
- [ ] Legal review passed

---

## BLOCK-4: Encryption at Rest
**Severity**: HIGH | **Effort**: 1.5 weeks | **Risk**: MEDIUM

### Problem
- No encryption at rest for Cloud SQL (default Google-managed only)
- No encryption at rest for GCS (default Google-managed only)
- GDPR/HIPAA/PCI-DSS require encryption
- GCP Marketplace customers expect CMEK option

### Files to Create/Modify
1. **New**: `/home/user/yawl/config/application-prod.properties`
   - Add Spring Boot encryption settings
   - Reference GCP Cloud KMS keys

2. **New**: `/home/user/yawl/src/org/yawlfoundation/yawl/security/EncryptionManager.java`
   - Wrapper around GCP Cloud KMS
   - Encrypt/decrypt sensitive fields
   - Handle key rotation

3. Update: `/home/user/yawl/marketplace/gcp/solution.yaml`
   - Add section: `encryption:`
   - Enable Cloud SQL CMEK
   - Enable GCS bucket CMEK
   - Document key rotation (30 days)

4. Update: `/home/user/yawl/config/application-staging.properties`
   - Add test configuration (use default Google-managed keys)
   - Show example CMEK reference

5. **New**: `/home/user/yawl/docs/ENCRYPTION-AT-REST-GUIDE.md`
   - How to configure CMEK
   - Key rotation procedure
   - Compliance mapping (GDPR/HIPAA/PCI-DSS)

### Implementation Steps
- [ ] Create Cloud KMS key ring in GCP project
- [ ] Grant service account permissions to Cloud KMS
- [ ] Configure Cloud SQL to use CMEK
  - [ ] Database instance → Encryption → Customer-managed key
  - [ ] Select KMS key
- [ ] Configure GCS bucket to use CMEK
  - [ ] Bucket → Encryption → Customer-managed key
- [ ] Add EncryptionManager to handle sensitive data
  - [ ] Passwords
  - [ ] API keys
  - [ ] JWT secrets
  - [ ] Database credentials

### Tests Required
- [ ] Data encrypted in Cloud SQL backups
- [ ] Data encrypted in GCS objects
- [ ] Key rotation doesn't break existing data
- [ ] Application can read encrypted data

### Definition of Done
- [ ] CMEK fully configured in production setup
- [ ] Encryption guide published
- [ ] Terraform/infrastructure-as-code updated
- [ ] Security team sign-off
- [ ] Tested in staging environment (2+ weeks)

---

## BLOCK-5: Missing Marketplace Documentation
**Severity**: HIGH | **Effort**: 1 week | **Risk**: LOW

### Problem
- No Privacy Policy (GDPR/CCPA required)
- No SLA (customers need commitments)
- No Data Processing Agreement (GDPR required)
- No Support Policy (GCP Marketplace standard)
- GCP will reject listing without these

### Files to Create
1. **New**: `/home/user/yawl/PRIVACY.md`
   - Data collection practices
   - Retention period (default: 90 days for logs, 7 years for audit)
   - Third-party sharing (none, except GCP services)
   - User rights (access, deletion, export)
   - GDPR/CCPA/CCPA compliance statements

2. **New**: `/home/user/yawl/SLA.md`
   - Availability: 99.5% monthly uptime (99.95% for premium)
   - Response times:
     - P1 (critical): 1 hour
     - P2 (high): 4 hours
     - P3 (medium): 1 business day
     - P4 (low): 5 business days
   - Service credits: 10% credit for P1 SLA breach
   - Exclusions: customer-caused issues, maintenance windows
   - RTO: 4 hours (to restore from backup)
   - RPO: 1 hour (max data loss)

3. **New**: `/home/user/yawl/DPA.md` (Data Processing Agreement)
   - Processor/Controller roles
   - Data processing terms per GDPR Article 28
   - Sub-processor policy
   - Data subject rights
   - Audit rights

4. **New**: `/home/user/yawl/SUPPORT-POLICY.md`
   - Support tiers: Basic (24/5 business hours), Standard (24/7), Premium (24/7 + TAM)
   - Response times per severity
   - Escalation path
   - Communication channels (email, phone, Slack for premium)
   - Support hours
   - Known issues & workarounds

5. Update: `/home/user/yawl/marketplace/gcp/solution.yaml`
   - Add links to new documents
   - Update support contact info

### Template Content

**SLA Example**:
```markdown
# YAWL v6.0.0 Service Level Agreement

## Availability
- Monthly uptime target: 99.5%
- Calculation: (Total Minutes - Down Minutes) / Total Minutes
- Minimum commitment: 99.0%

## Incident Response
| Severity | Example | Response | Resolution |
|----------|---------|----------|-----------|
| P1 | Service down | 1 hour | 4 hours |
| P2 | Degraded performance | 4 hours | 8 hours |
| P3 | Minor issues | 1 day | 3 days |
| P4 | Enhancement request | 5 days | 30 days |

## Service Credits
- 99.5% - 99.0% uptime: 10% service credit
- 99.0% - 95.0% uptime: 25% service credit
- <95.0% uptime: 50% service credit
```

**Privacy Policy Example**:
```markdown
# Privacy Policy

YAWL collects minimal data:
- User credentials (username, hashed password)
- Workflow data (cases, work items, audit logs)
- System metrics (performance, uptime)

Retention:
- Active cases: Duration of workflow + 1 year
- Audit logs: 7 years (for compliance)
- System logs: 90 days

Sharing:
- No sharing with third parties
- GCP services access data for hosting/backup only
- Customer data never used for ML training

User Rights:
- Access: Export all data via API
- Deletion: Delete cases/logs on request
- Portability: Export in standard format
```

### Definition of Done
- [ ] Privacy Policy reviewed by legal
- [ ] SLA signed off by operations team
- [ ] DPA reviewed by data protection officer
- [ ] Support Policy confirmed by support team
- [ ] All docs linked in marketplace listing
- [ ] GCP Marketplace accepts docs

---

## Pre-Launch Validation Checklist

### Code Quality
- [ ] Build passing: `bash scripts/dx.sh all`
- [ ] No HYPER_STANDARDS violations in modified files
- [ ] Tests passing: 80%+ line coverage, 70%+ branch coverage
- [ ] Security scan passing: no critical/high vulnerabilities

### Compliance
- [ ] Multi-tenant isolation: integration tests pass
- [ ] Quota enforcement: 429 responses tested
- [ ] Encryption: CMEK configured in production
- [ ] Audit logging: events appear in Cloud Logging
- [ ] Documentation: all 5 docs complete and reviewed

### GCP Marketplace
- [ ] solution.yaml validated against GCP schema
- [ ] Deployment tested in GCP Marketplace sandbox
- [ ] Pricing metrics defined in solution.yaml
- [ ] Support URL/email responding
- [ ] Documentation links working (404 test)

### Security
- [ ] Penetration test passed (optional but recommended)
- [ ] SSL/TLS certificates valid
- [ ] Firewall rules tested (only needed ports open)
- [ ] Backup/restore tested
- [ ] Disaster recovery runbook validated

---

## Effort Estimation Summary

| Issue | Weeks | Team | Dependencies |
|-------|-------|------|--------------|
| BLOCK-1: Multi-tenancy | 1-2 | Backend (2) | Schema migration testing |
| BLOCK-2: Quotas | 1.5 | Backend (1) | GCP Marketplace API docs |
| BLOCK-3: LGPL | 1 | Infra/Legal (1) | License texts |
| BLOCK-4: Encryption | 1.5 | Security/Backend (2) | Cloud KMS setup |
| BLOCK-5: Docs | 1 | Legal/Product (1) | Template reviews |
| **Total** | **4-6** | **5-7 people** | **2-3 dependencies** |

---

## Next Steps

1. **This Week**: 
   - [ ] Share this checklist with team
   - [ ] Schedule blockers refinement meeting
   - [ ] Create JIRA tickets for each block

2. **Week 1**:
   - [ ] Start BLOCK-1 (multi-tenancy) design
   - [ ] Start BLOCK-2 (quotas) implementation
   - [ ] Request legal resources for BLOCK-5 (docs)

3. **Week 2**:
   - [ ] Complete BLOCK-1 code review
   - [ ] Complete BLOCK-2 implementation
   - [ ] Start BLOCK-3 (LGPL) documentation

4. **Week 3**:
   - [ ] Complete BLOCK-4 (encryption) setup
   - [ ] Integration testing (all blocks)
   - [ ] Final documentation review

5. **Week 4-5**:
   - [ ] GCP Marketplace submission
   - [ ] Await GCP review
   - [ ] Fix feedback if any

---

**Last Updated**: 2026-02-20  
**Prepared By**: YAWL Compliance & Security Team  
**Review Frequency**: Weekly until all blocks resolved
