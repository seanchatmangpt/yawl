# YAWL v6.0.0 - Support Policy

**Effective Date**: February 20, 2026
**Last Updated**: February 20, 2026
**Applies To**: All YAWL customers

---

## 1. Support Overview

YAWL provides comprehensive technical support for workflow automation, integration, and operational issues. Support levels vary by subscription tier, with response times and escalation paths tailored to severity.

---

## 2. Support Tiers & Subscription Levels

### 2.1 Tier Comparison

| Feature | Standard | Premium | Enterprise |
|---------|----------|---------|-----------|
| **Support Channels** | Email | Email + Slack | Email + Slack + Phone |
| **Support Hours** | Business (9-5) | Extended (7am-10pm) | 24/7 |
| **Response Time (P1)** | 4 hours | 1 hour | 15 minutes |
| **Response Time (P2)** | 8 hours | 4 hours | 1 hour |
| **Response Time (P3)** | 1 day | 4 hours | 4 hours |
| **Response Time (P4)** | 3 days | 1 day | 4 hours |
| **Dedicated Support Lead** | — | ✓ | ✓ |
| **Monthly Escalation Review** | — | — | ✓ |
| **Quarterly Business Reviews** | — | — | ✓ |
| **SLA Credits** | 5% | 10% | 15% |
| **Cost/month** | Included | +$500 | +$2,000 |

### 2.2 Support Hours

| Tier | Coverage | Days | Time Zone |
|------|----------|------|-----------|
| **Standard** | Business hours | Mon-Fri | 9am-5pm UTC |
| **Premium** | Extended | Mon-Fri, Sat limited | 7am-10pm UTC |
| **Enterprise** | Round-the-clock | 24/7/365 | Multiple zones |

---

## 3. Severity Levels & Response Times

### 3.1 Severity Definitions

**P1 - CRITICAL** (Production Down)
- Entire YAWL service unavailable
- No workflow executions possible
- Data loss or corruption occurring
- Security breach or vulnerability exploited
- Affects all or multiple customers

**Examples**: Complete API down, database corrupted, auth system failing

**Response**: Within 15 min (Enterprise), 1 hour (Premium), 4 hours (Standard)

---

**P2 - HIGH** (Significant Degradation)
- Major functionality broken
- Single customer cannot execute workflows
- API errors preventing integrations
- Performance < 50% of baseline
- Workaround not readily available

**Examples**: Workflow start failing, task completion times > 5 min, API returning 50% errors

**Response**: Within 1 hour (Enterprise), 4 hours (Premium), 8 hours (Standard)

---

**P3 - MEDIUM** (Moderate Impact)
- Feature malfunction with workaround
- Performance degraded but acceptable
- API returning some errors
- Affects subset of users
- Non-critical operations impacted

**Examples**: Reporting slow, export timing out, occasional error on audit page

**Response**: Within 4 hours (Enterprise/Premium), 1 day (Standard)

---

**P4 - LOW** (Minor/Enhancement)
- Cosmetic issues
- Feature requests
- Documentation unclear
- No functional impact
- Can wait for next release

**Examples**: UI typo, feature suggestion, how-to question

**Response**: Within 4 hours (Enterprise), 1 day (Premium), 3 days (Standard)

---

### 3.2 Severity Assignment

| Symptom | Initial Assignment | Review Required |
|---------|---|---|
| Service 100% down | P1 | Within 30 min |
| Service partial down (>50%) | P2 | Within 1 hour |
| Service degraded (<50%) | P3 | Within 2 hours |
| Single feature broken | P3 | Support assessment |
| Enhancement/question | P4 | Support assessment |

**Right to Re-classify**: YAWL support may adjust severity if:
- Initial severity was inflated
- Impact is lower than claimed
- Workaround is available
- Issue is customer-side configuration

---

## 4. Support Channels

### 4.1 Primary Channels

| Channel | Response | Availability | Use For |
|---------|----------|---|---|
| **Email** | Next business day | 24/7 submission | Non-urgent, detailed issues |
| **Slack** (Premium+) | 2 hours | Business hours | Quick questions, status checks |
| **Phone** (Enterprise) | Immediate | 24/7 | P1 issues, critical calls |
| **Portal** (All) | Within 1 day | 24/7 | Ticket tracking, documentation |

### 4.2 Contact Information

**General Support**
- Email: support@yawlfoundation.org
- Portal: https://support.yawlfoundation.org
- Phone: +1-XXX-YYYY-ZZZZ (Enterprise only)

**Account/Billing Questions**
- Email: accounts@yawlfoundation.org
- Response: Within 1 business day

**Escalation** (P1/P2 unresolved)
- Manager: escalation@yawlfoundation.org
- Director: director@yawlfoundation.org (Enterprise)

**Security Issues**
- Email: security@yawlfoundation.org
- GPG Key: https://yawlfoundation.org/security.gpg
- Response: Within 24 hours

---

## 5. Support Request Process

### 5.1 Creating a Support Request

**Required Information** (all requests):
- Your YAWL account/customer ID
- Affected user(s) or systems
- Issue description (detailed)
- Steps to reproduce (if applicable)
- Expected vs. actual behavior
- Severity assessment (your opinion)
- Any error messages or logs

**Recommended** (helps us faster):
- Screenshots or screen recordings
- Log files (attachment or paste)
- Workflow/case IDs affected
- Network trace (tcpdump, Wireshark)
- Recent configuration changes

### 5.2 Support Ticket Lifecycle

```
┌─────────────────────────────────────────────┐
│ CUSTOMER SUBMITS TICKET                     │
│ - Via email, portal, Slack, phone           │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│ SUPPORT ACKNOWLEDGES                        │
│ - Auto-response with ticket # and ETA       │
│ - Assigned to support engineer              │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│ INVESTIGATION & TROUBLESHOOTING             │
│ - Engineer reviews logs, reproduces issue   │
│ - May request additional information        │
│ - Works toward root cause diagnosis         │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│ SOLUTION OFFERED                            │
│ - Options: Code fix, config change, etc.    │
│ - For reproducible issues: fix provided     │
│ - For unclear: additional testing required  │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│ CUSTOMER CONFIRMS RESOLUTION                │
│ - Issue resolved? Yes → close, No → reopen  │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│ CLOSED TICKET                               │
│ - RCA document provided (P1/P2 only)        │
│ - Survey sent (satisfaction feedback)       │
│ - Searchable in knowledge base              │
└─────────────────────────────────────────────┘
```

### 5.3 Escalation Path

**Level 1** (Initial Support Engineer)
- Troubleshooting
- Documentation lookup
- Common issues resolution
- Average resolution time: 4-8 hours

**Level 2** (Senior Support Engineer)
- Complex technical issues
- Code-level debugging
- System interaction issues
- Average resolution time: 8-24 hours

**Level 3** (Support Manager)
- Critical issues unresolved after 4 hours
- Multiple system impact
- Escalated by customer request
- Average resolution time: 2-4 hours

**Level 4** (Engineering Team)
- Product bugs requiring code changes
- Design-level issues
- New feature requests
- Timeline: Depends on fix complexity

---

## 6. Expected Response & Resolution Times

### 6.1 Response SLA

| Tier | P1 | P2 | P3 | P4 |
|------|----|----|----|----|
| **Enterprise** | 15 min | 1 hour | 4 hours | 4 hours |
| **Premium** | 1 hour | 4 hours | 4 hours | 1 day |
| **Standard** | 4 hours | 8 hours | 1 day | 3 days |

**Response SLA** = Time from ticket submission to first meaningful response

### 6.2 Resolution SLA (Target, not guaranteed)

| Severity | Enterprise | Premium | Standard |
|----------|----------|---------|----------|
| **P1** | 4 hours | 8 hours | 24 hours |
| **P2** | 8 hours | 1 day | 2 days |
| **P3** | 2 days | 3 days | 5 days |
| **P4** | 5+ days | 7+ days | 14+ days |

**Resolution SLA** = Target, not guaranteed (depends on issue complexity)

### 6.3 Status Updates

YAWL commits to:
- **P1**: Updates every 30 minutes
- **P2**: Updates every 2 hours
- **P3**: Updates daily
- **P4**: Updates weekly

---

## 7. Support Exclusions & Limitations

### NOT Covered by Support

1. **Customer Code & Systems**
   - Issues in customer's own code/integrations
   - Customer's database problems
   - Customer's network configuration
   - Third-party vendor issues

2. **Unsupported Configurations**
   - Outdated browser versions (< 2 versions back)
   - Unsupported databases (use Cloud SQL)
   - Custom-modified YAWL installations
   - End-of-life product versions

3. **Pre-Sales & Administrative**
   - Sales inquiries (→ sales@yawlfoundation.org)
   - License/contract questions (→ accounts@)
   - Training requests (separate service)
   - Architectural consulting (paid services)

4. **Cosmetic Issues**
   - UI text corrections
   - Minor formatting issues
   - Font/color preferences

### Limited Support for

- **Third-party Integrations** (Slack, Teams, etc.)
  - We support YAWL's end; third-party issues → their support
  - May collaborate to debug integration problems

- **Custom Development**
  - Custom workflows: Best-effort assistance
  - Custom code: Requires Professional Services engagement

- **Legacy Versions** (> 2 versions old)
  - Security fixes only
  - Limited features
  - Upgrade recommended

---

## 8. Knowledge & Documentation

### 8.1 Self-Service Resources

YAWL provides free self-service options:

**Knowledge Base**
- URL: https://docs.yawlfoundation.org
- Searchable FAQ, troubleshooting guides
- Updated quarterly

**Community Forum**
- Community-driven Q&A
- Moderated, searchable
- Response within 2 business days (best effort)

**Video Tutorials**
- Getting started guides
- Integration examples
- Workflow design patterns

**API Documentation**
- Complete REST API reference
- OpenAPI/Swagger spec
- Code examples (Python, Java, Node.js)

### 8.2 Learning & Training

**Included** (with subscription):
- Onboarding session (30 min, Standard tier)
- Email-based guidance (all tiers)
- Documentation access

**Available** (separate fees):
- Live training workshops ($2,000-$5,000)
- Certification programs ($500)
- Custom training (per-day consulting)

---

## 9. Support Incident Management

### 9.1 Incident Severity

**Severity 1** (Service Down)
- Immediate escalation
- War room activated
- All resources mobilized

**Severity 2** (Significant Impact)
- Escalation to manager
- Dedicated engineer assigned
- Status updates every 2 hours

**Severity 3** (Moderate Impact)
- Standard support flow
- Status updates daily

### 9.2 Incident Communication

**During Incident**:
- Status page updated every 15 min
- Email updates to affected customers
- Slack/phone for Enterprise customers
- Transparent root cause explanation

**Post-Incident**:
- Root Cause Analysis (RCA) within 2 days
- Preventive measures documented
- Customer debriefing (if requested)

---

## 10. Support Reviews & Escalation

### 10.1 Periodic Reviews

**Monthly** (Premium tier):
- Review of open/resolved tickets
- Trends and common issues
- Performance metrics

**Quarterly** (Enterprise tier):
- Business review meeting
- Roadmap alignment
- SLA performance review
- Feedback & suggestions

### 10.2 Escalation Criteria

**Automatic escalation if**:
- P1 unresolved after 4 hours
- P2 unresolved after 8 hours
- P3 unresolved after 2 days
- Same issue submitted 3+ times
- Customer requests escalation

**Escalation path**:
1. Support Manager (within 30 min)
2. Engineering Director (within 1 hour)
3. VP/CTO (within 2 hours, if P1)

---

## 11. SLA Credits & Remedies

### 11.1 Credit Policy

If response SLA missed, customer receives credit:

| Miss | Credit |
|------|--------|
| 1st miss in month | None (courtesy) |
| 2nd miss | 10% month credit |
| 3rd+ miss | 25% month credit |

**Max credit**: 100% of support fees in single month

### 11.2 Claiming Credits

- Auto-applied at month-end
- No claim needed
- Visible in invoice
- Applied to next month

---

## 12. Professional Services

### 12.1 Additional Services Available

| Service | Cost | Duration |
|---------|------|----------|
| **Custom Development** | $150/hour | Per engagement |
| **Migration Services** | $5,000-$50,000 | 2-8 weeks |
| **Architecture Review** | $3,000-$10,000 | 1-2 weeks |
| **Training Workshop** | $2,000-$5,000 | 1-3 days |
| **Performance Tuning** | $5,000-$20,000 | 1-4 weeks |

### 12.2 Engagement Process

1. Scoping meeting (1-2 hours, free)
2. Statement of work (SOW) prepared
3. Resource allocation
4. Kick-off meeting
5. Regular status reviews

---

## 13. Feedback & Satisfaction

### 13.1 Feedback Channels

- **Post-ticket Survey**: Sent after ticket close (1-minute)
- **NPS Survey**: Quarterly (Net Promoter Score)
- **Annual Review**: For Enterprise customers
- **Direct Contact**: Always welcome at escalation@

### 13.2 Quality Assurance

- **QA Reviews**: 10% of tickets audited
- **First Call Resolution**: Target 60%
- **Customer Satisfaction**: Target 90%+ (4.5+/5 rating)
- **Improvement Plan**: Quarterly adjustments

---

## 14. End-of-Life & Version Support

### 14.1 Version Support Timeline

| Version | Status | Support Level | End Date |
|---------|--------|---|---|
| **Current (6.0)** | GA | Full support | 6.2 release + 12 mo |
| **6.1** (when released) | LTS | Full support | 24+ months |
| **6.2** (when released) | LTS | Limited support | 12 months after 6.3 |
| **< 6.0** | EOL | Security only | 12 months after release |

**Support Levels**:
- **Full**: Bug fixes, patches, new features (if applicable)
- **Limited**: Critical bugs, security fixes
- **Security**: Security fixes only
- **EOL**: No support

### 14.2 Migration Assistance

For customers on EOL versions:
- Free migration planning consultation
- Discounted Professional Services (15% off)
- Dedicated migration engineer (Enterprise)

---

## 15. Corporate/Government Support

### 15.1 Specialized Support Options

**Government (FedRAMP)**
- Security clearance requirements
- Compliance monitoring
- Dedicated support team
- Custom SLA

**Healthcare (HIPAA)**
- BAA (Business Associate Agreement)
- Compliance audits
- HIPAA-trained support staff
- Incident response procedures

**Finance (PCI-DSS/SOX)**
- Enhanced encryption
- Regulatory audit support
- Compliance attestations

---

## 16. Support Contact Reference

**Email Support**
- General: support@yawlfoundation.org
- Escalation: escalation@yawlfoundation.org
- Security: security@yawlfoundation.org
- Billing: accounts@yawlfoundation.org

**Phone** (Enterprise only)
- Main: +1-XXX-YYYY-ZZZZ
- Escalation: +1-XXX-YYYY-ZZZZ (extension 9)

**Online Portal**
- URL: https://support.yawlfoundation.org
- Account required (use YAWL login)

**Status Page**
- URL: https://status.yawlfoundation.org
- Real-time system status
- Incident history

---

## 17. Policy Changes

- **Updates**: Annual (January 1st)
- **Significant Changes**: 30 days' notice via email
- **Continued use**: Implies acceptance of updated policy

---

**Document Status**: APPROVED
**Version**: 1.0
**Classification**: Public
**Review Date**: February 2027

---

**Support Commitment**:

> YAWL is committed to resolving your issues quickly and thoroughly. Our support team works 24/7 (Enterprise) to ensure your workflow automation success. Thank you for choosing YAWL!
