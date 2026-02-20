# YAWL v6.0.0 - Service Level Agreement (SLA)

**Effective Date**: February 20, 2026
**Service Provider**: YAWL Foundation / GCP Marketplace
**Service**: YAWL Workflow Engine (Cloud-Hosted)

---

## 1. Service Description

YAWL provides a managed workflow automation platform deployed on Google Cloud Platform with the following capabilities:

- Workflow specification, execution, and monitoring
- Multi-tenant support with complete data isolation
- REST API for third-party integrations
- Real-time monitoring and audit logging
- Automatic backups and disaster recovery

---

## 2. Service Level Objectives (SLOs)

### 2.1 Availability SLO

| Tier | Monthly Uptime | Downtime Budget |
|------|---|---|
| **Standard** | 99.5% | 3.6 hours/month |
| **Premium** | 99.9% | 43 minutes/month |
| **Enterprise** | 99.95% | 22 minutes/month |

**Definition of Uptime**: Percentage of time YAWL API endpoints respond within SLA latency targets without errors, measured from GCP monitoring.

**Excluded from SLO**:
- Scheduled maintenance (announced 7 days in advance)
- Customer-caused incidents (misconfiguration, abuse)
- Third-party services (external APIs, customer systems)
- Force majeure events (natural disasters, wars)
- Attacks exceeding 5 Gbps DDoS threshold

### 2.2 Latency SLO

| Operation | P50 (Median) | P99 (99th Percentile) |
|-----------|---|---|
| **Case Start** | < 100ms | < 500ms |
| **Task Complete** | < 150ms | < 750ms |
| **Query Case List** | < 200ms | < 1000ms |
| **Audit Log Query** | < 500ms | < 2000ms |

Measurements: UTC times, measured from API gateway

### 2.3 Data Durability SLO

- **Recovery Point Objective (RPO)**: < 15 minutes
- **Recovery Time Objective (RTO)**: < 1 hour
- **Backup Frequency**: Hourly incremental snapshots
- **Geographic Redundancy**: Multi-region replication (GCP)
- **Durability Guarantee**: 99.999999999% (11 nines) per GCP Cloud Storage

---

## 3. Support & Response Times

### 3.1 Support Tiers

| Severity | Response Time | Update Frequency | Resolution Time |
|----------|---|---|---|
| **P1 - Critical** | 15 minutes | 30 minutes | 4 hours (target) |
| **P2 - High** | 1 hour | 2 hours | 8 hours (target) |
| **P3 - Medium** | 4 hours | Daily | 2 business days (target) |
| **P4 - Low** | 1 business day | Weekly | 5 business days (target) |

### Severity Definitions

**P1 - Critical**
- Complete service outage
- Data loss or corruption
- Security breach
- Affects all customers

**P2 - High**
- Partial service degradation
- Single customer unable to perform core functions
- API errors affecting integrations
- Performance < 50% of baseline

**P3 - Medium**
- Feature malfunction (workaround exists)
- Non-critical API failures
- Performance degradation (< 50%)
- Documentation issues

**P4 - Low**
- Feature requests
- Cosmetic issues
- Enhancement suggestions

### 3.2 Support Channels

| Channel | Hours | Response |
|---------|-------|----------|
| **Email** | Business hours | Next business day |
| **Slack/Chat** | Business hours | 2 hours |
| **Phone** | Premium/Enterprise only | 30 minutes |
| **Portal** | 24/7 | Email backup |

---

## 4. Credits & Remedies

### 4.1 Service Credit Calculation

If monthly uptime < SLO target, customer receives service credit:

| Uptime | Standard | Premium | Enterprise |
|--------|----------|---------|-----------|
| < 99.0% | 10% | 15% | 20% |
| < 95.0% | 25% | 35% | 50% |
| < 90.0% | 50% | 50% | 100% |

**Credit Terms**:
- Applied to next month's invoice automatically
- Non-cumulative month-to-month
- Maximum credit = 100% of monthly fees
- No cash refunds
- Non-transferable

### 4.2 Credit Request Process

1. Customer submits incident report within 30 days of outage
2. YAWL validates against monitoring data
3. Credit applied within 2 billing cycles

---

## 5. Performance Metrics & Monitoring

### 5.1 Available Metrics

- **Request Rate**: Requests per second (RPS)
- **Error Rate**: % of requests returning 5xx errors
- **Latency**: P50, P95, P99 response times
- **CPU Utilization**: Per tenant resource consumption
- **Storage Growth**: Case data and log volumes
- **Backup Status**: Frequency and success rate

### 5.2 Monitoring & Dashboards

- Real-time dashboards in YAWL Control Panel
- Exportable metrics to customer monitoring systems
- Integration with Google Cloud Monitoring
- Email alerts for threshold violations

---

## 6. Maintenance & Updates

### 6.1 Scheduled Maintenance Windows

- **Windows**: Sundays 02:00-06:00 UTC (4 hours max)
- **Announcement**: 7 days minimum notice
- **Impact**: Read-only mode (queries allowed, no mutations)
- **Frequency**: Up to 1 per month (typically quarterly)

### 6.2 Emergency Patches

- **Security patches**: Deployed immediately (with notice if possible)
- **Critical bugs**: 4-hour deployment window
- **Testing**: Canary deployment (5% traffic) → full rollout

### 6.3 Upgrade Policy

- **Breaking changes**: 30 days notice + migration support
- **Deprecations**: 6 months notice before removal
- **New versions**: Released quarterly with deprecation cycle
- **Version support**: Current + 2 previous versions

---

## 7. Security & Compliance

### 7.1 Security Standards

- **Encryption at Rest**: AES-256 (CMEK)
- **Encryption in Transit**: TLS 1.3
- **Access Control**: Identity-based access (IAM + MFA)
- **Audit Logging**: All operations logged, retention 7 years
- **Penetration Testing**: Annual third-party assessment
- **Vulnerability Management**: Monthly scanning, 48-hour patching for critical

### 7.2 Compliance Certifications

- **SOC 2 Type II**: Annual attestation
- **ISO 27001**: Inherited from GCP
- **GDPR**: Data Processing Agreement included
- **HIPAA**: Available for healthcare customers (BAA required)
- **FedRAMP**: Planned for Q3 2026

---

## 8. Disaster Recovery & Business Continuity

### 8.1 Backup & Recovery

- **Automated Backups**: Hourly snapshots, 30-day retention
- **Point-in-Time Recovery**: Any point within last 30 days
- **Multi-Region Replication**: Data replicated across 3+ GCP regions
- **Testing**: Recovery drills quarterly

### 8.2 Failover Procedure

| Event | Detection | Failover | Validation |
|-------|-----------|----------|-----------|
| Single zone failure | < 1 min | < 5 min | < 10 min |
| Region failure | < 5 min | < 30 min | < 60 min |
| Complete outage | Manual | < 2 hours | < 4 hours |

### 8.3 RTO/RPO Targets

- **Recovery Time Objective (RTO)**: < 1 hour for complete restoration
- **Recovery Point Objective (RPO)**: < 15 minutes data loss
- **Backup Frequency**: Hourly incremental, daily full
- **Retention**: 30 days online, 1 year cold storage

---

## 9. Exclusions & Limitations

### NOT Covered by SLA

1. **Customer Responsibilities**:
   - Misconfigurations of customer applications
   - Incorrect API usage or abuse
   - Customer network/infrastructure failures

2. **Third-Party Services**:
   - External databases (not managed by YAWL)
   - Customer integrations
   - Third-party APIs (Slack, Teams, etc.)

3. **Events Beyond Control**:
   - Natural disasters
   - Wars, terrorism, civil unrest
   - Government actions
   - Pandemic/epidemic
   - Massive solar events

4. **Intentional Acts**:
   - Security attacks exceeding 5 Gbps threshold
   - DDoS attacks (after mitigation triggered)
   - Ransomware (after initial containment)

---

## 10. Service Termination & Data

### 10.1 Termination Procedures

| Scenario | Notice | Data Retention | Recovery |
|----------|--------|---|---|
| Customer-initiated | None | 30 days | Customer export available |
| Non-payment | 15 days | Suspended access | 60-day grace period |
| Breach of TOS | Immediate | 24 hours | Emergency export available |
| Contract expiration | Automatic | None | Final export at termination |

### 10.2 Data Export & Portability

- Full case/workflow data export in XML/JSON
- Audit logs exportable to Cloud Storage
- Customer retains all data at termination (30-day window)
- Export bandwidth: Included in SLA

---

## 11. Limitation of Liability

### 11.1 Liability Caps

| Category | Limit |
|----------|-------|
| **Direct Damages** | 12 months of service fees |
| **Indirect/Consequential** | $0 (excluded) |
| **Data Loss** | 12 months of service fees |
| **Customer's Indemnification** | As per Master Service Agreement |

### 11.2 Exceptions to Liability Caps

- Breaches of confidentiality
- Intellectual property infringement
- Data processing violations (GDPR)
- Indemnification obligations

---

## 12. SLA Escalation & Dispute Resolution

### Escalation Process

1. **Initial Contact**: Support team investigates (4 hours)
2. **Manager Review**: Escalated if not resolved (8 hours)
3. **Executive Escalation**: Director involvement (24 hours)
4. **Formal Mediation**: Per Master Service Agreement

### Dispute Resolution

- **Informal Resolution**: 30 days good-faith negotiation
- **Mediation**: Non-binding mediation (60 days)
- **Arbitration**: Binding arbitration per MSA terms
- **Jurisdiction**: As specified in Master Service Agreement

---

## 13. Reporting & Transparency

### 13.1 Status Dashboard

- Public status page: `status.yawlfoundation.org`
- Real-time system health
- Incident timeline
- Performance metrics
- 5-minute update frequency during incidents

### 13.2 Monthly Performance Reports

- Available in Control Panel
- Includes:
  - Uptime percentage (by tier)
  - Incident summary
  - Performance metrics
  - Security events
  - Credit calculations

---

## 14. Changes to SLA

- **Updates**: Posted with 30-day notice
- **Degradation**: Only via mutual written agreement
- **Improvements**: Effective immediately
- **Annual Review**: Every January 1st

---

## A. Contact & Escalation

**SLA Questions**
- Email: sla@yawlfoundation.org
- Phone: +1-XXX-XXX-XXXX (Premium/Enterprise)

**Executive Escalation** (P1 only)
- VP of Operations: ops@yawlfoundation.org
- CTO: cto@yawlfoundation.org

**Formal Disputes**
- Legal: legal@yawlfoundation.org

---

**Document Status**: APPROVED
**Version**: 1.0
**Classification**: Public
**Review Date**: February 2027
