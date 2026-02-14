# Event Horizon Cloud — Deployment & Operations Guide

## Quick Start (5 minutes)

### 1. GCP Marketplace

Visit https://console.cloud.google.com/marketplace and search **"Event Horizon Cloud"**.

### 2. Select Plan

- **Starter** ($299/mo): Dev + light prod
- **Professional** ($999/mo): Production + MCP Gateway + A2A Mesh
- **Enterprise** ($2999/mo): Mission-critical + 24/7 support + SLA 99.99%

### 3. Configure

- **Region**: Choose primary (us-central1, us-east1, europe-west1, asia-southeast1)
- **Multi-Region**: Professional/Enterprise only
- **Integrations**: Enable GitHub, Slack, Jira connectors

### 4. Deploy

Click **"Deploy"**. Deployment completes in 10–15 minutes.

### 5. Access

1. **YAWL Console**: https://console.{caseId}.eventhorizoncloud.io
2. **API Key**: Available in [Settings](https://console.eventhorizoncloud.io/settings/api-keys)
3. **Docs**: https://docs.eventhorizoncloud.io

---

## Architecture

### Regional Deployment

```
┌─────────────────────────────────────────────────┐
│            GCP Project (yours)                  │
│  ┌───────────────────────────────────────────┐  │
│  │  Region: us-central1 (primary)            │  │
│  │  ┌─────────────────────────────────────┐  │  │
│  │  │  GKE Cluster (yawl-prod)            │  │  │
│  │  │  ┌──────────────────────────────┐   │  │  │
│  │  │  │  Pods (3-10 replicas)        │   │  │  │
│  │  │  │  - Event Horizon Workflow    │   │  │  │
│  │  │  │  - Event Horizon Runtime     │   │  │  │
│  │  │  │  - Event Horizon Gateway     │   │  │  │
│  │  │  │  - Event Horizon Mesh        │   │  │  │
│  │  │  └──────────────────────────────┘   │  │  │
│  │  ├─ Cloud SQL (PostgreSQL 14, HA)     │  │  │
│  │  ├─ Redis Cluster (5-25GB, HA)        │  │  │
│  │  ├─ Cloud Storage (backups)           │  │  │
│  │  └─ Cloud Monitoring (Prometheus)     │  │  │
│  │  └─ Cloud Logging (ELK)               │  │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  (Professional/Enterprise)                      │
│  ┌───────────────────────────────────────────┐  │
│  │ Region: europe-west1 (read replica)      │  │
│  │ - Georeplicated database                 │  │
│  │ - Cache layer                            │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
        ↓
  ┌────────────────────────────────┐
  │  API Gateway (Load Balanced)   │
  │  - MCP Gateway (100-1000 rps)  │
  │  - A2A Mesh (WebSocket)        │
  │  - REST API                    │
  └────────────────────────────────┘
        ↓
  ┌─ - - - - - - - - - - - - - - ─┐
  │  Your Agents / Applications   │
  │  (via MCP or A2A)             │
  └─ - - - - - - - - - - - - - - ─┘
```

### High Availability

**Professional Plan**:
- 3 Tomcat replicas (auto-scale to 10)
- Regional PostgreSQL with failover
- Managed load balancer
- 99.9% SLA

**Enterprise Plan**:
- 5+ Tomcat replicas (auto-scale to 20)
- Multi-region PostgreSQL replication
- Multi-region load balancing
- Canary deployments + automated rollback
- 99.99% SLA

---

## Deployment Parameters

### Core Configuration

| Parameter | Starter | Professional | Enterprise |
|-----------|---------|--------------|-----------|
| Regions | 1 | 1-2 | 2+ |
| Tomcat Replicas | 1 | 3-10 | 5-20 |
| PostgreSQL | Single-AZ | Regional HA | Multi-region |
| Redis | 2GB | 5GB | 25GB+ |
| Backups | 7 days | 30 days | 90 days |
| SLA | 99.5% | 99.9% | 99.99% |

### Customization

Access https://console.eventhorizoncloud.io/deployment/settings to adjust:

- **CPU/Memory allocation** per replica
- **Auto-scaling thresholds** (CPU, memory)
- **Database storage** and backup retention
- **Cache size** and TTL
- **MCP rate limits** and concurrency
- **Mesh subscription queues** and priorities
- **Alerting policies** and channels

---

## Scaling & Performance

### Auto-Scaling Behavior

```
CPU Utilization

100% ├─ SCALE UP (add replica)
      │
 70%  ├─────── SCALE DOWN (remove replica)
      │
      └─ Time
```

- **Scale Up Trigger**: 70% CPU for 60 seconds
- **Scale Down Trigger**: 30% CPU for 300 seconds
- **Min Replicas**: Starter=1, Professional=3, Enterprise=5
- **Max Replicas**: Starter=2, Professional=10, Enterprise=20

### Load Testing

Before production, test with:

```bash
# Clone load test suite
git clone https://github.com/eventhorizoncloud/load-tests.git

# Configure for your plan
export HORIZON_API_KEY="your_api_key"
export HORIZON_REGION="us-central1"
export HORIZON_PLAN="professional"

# Run baseline test
./load-tests/run.sh

# Expected throughput:
# - Starter: 50 cases/sec
# - Professional: 500 cases/sec
# - Enterprise: 5000 cases/sec
```

---

## API Endpoints

### Regional URLs

Replace `{region}` with your region (us-central1, us-east1, europe-west1, asia-southeast1).

| Service | Endpoint | Protocol |
|---------|----------|----------|
| **REST API** | `https://api.{region}.eventhorizoncloud.io/v1` | HTTPS |
| **MCP Gateway** | `https://gateway.{region}.eventhorizoncloud.io/mcp` | JSON-RPC 2.0 |
| **A2A Mesh** | `wss://mesh.{region}.eventhorizoncloud.io/a2a` | WebSocket |
| **Receipts Ledger** | `https://receipts.{region}.eventhorizoncloud.io/ledger` | HTTPS |
| **Console** | `https://console.eventhorizoncloud.io` | HTTPS |

### Example

```bash
# List cases
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://api.us-central1.eventhorizoncloud.io/v1/cases

# MCP Gateway discovery
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://gateway.us-central1.eventhorizoncloud.io/mcp/tools/list

# A2A Mesh registration (WebSocket)
wscat -H "Authorization: Bearer YOUR_API_KEY" \
  wss://mesh.us-central1.eventhorizoncloud.io/a2a
```

---

## Integrations

### GitHub Connector

Automatically sync pull requests and issues as YAWL cases.

**Setup**:
1. Go to https://console.eventhorizoncloud.io/integrations/github
2. Authorize with your GitHub organization
3. Map repositories to YAWL specifications
4. Enable webhook auto-triggering

**Example**: PR → Code Review Workflow
```
PR opened → Start "code-review" case
Reviewers assigned → Enable "code-review" task
Review approved → Complete task → Merge PR
```

### Slack Connector

Post YAWL task notifications and handle approvals.

**Setup**:
1. Go to https://console.eventhorizoncloud.io/integrations/slack
2. Connect your Slack workspace
3. Choose channels for notifications
4. Enable interactive approvals

**Example**: Notification
```
📋 Order Approval Required
Order ID: ORD-2025-001
Amount: $5,000
[Approve] [Reject]
```

### Jira Connector

Route Jira issues to YAWL workflows.

**Setup**:
1. Go to https://console.eventhorizoncloud.io/integrations/jira
2. Enter Jira Cloud domain and API token
3. Map issue types to YAWL specifications
4. Enable auto-transition

**Example**: Issue → Support Workflow
```
Jira issue created → Start "customer-support" case
Support task completed → Jira issue transitioned to "Done"
Case closed → Jira issue resolved
```

---

## Monitoring & Alerting

### Dashboards

Access https://console.eventhorizoncloud.io/monitoring

**System Metrics**:
- Pod CPU, memory, network I/O
- Database queries, connections, replication lag
- Cache hit/miss rates
- Load balancer request counts and latencies

**Application Metrics**:
- Cases created/completed
- Task throughput (tasks/sec)
- Error rates
- MCP Gateway: requests/sec, latency percentiles
- A2A Mesh: agent count, task acceptance rate

**Business Metrics**:
- Cost (per case, per day)
- SLA compliance (uptime, latency)
- Receipt ledger size

### Alerts (Enterprise)

Automatically triggered:
- CPU > 85% for 5 min → Scale up
- Database connections > 80% → Alert
- Error rate > 1% → Critical alert
- SLA violation → Incident created
- Failed deployment → Rollback

Configure in https://console.eventhorizoncloud.io/alerts

---

## Backup & Disaster Recovery

### Automatic Backups

| Plan | Frequency | Retention |
|------|-----------|-----------|
| Starter | Daily | 7 days |
| Professional | Every 6 hours | 30 days |
| Enterprise | Every 1 hour | 90 days |

### Point-in-Time Recovery

Restore to any timestamp within retention window:

```bash
# List backups
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://api.us-central1.eventhorizoncloud.io/v1/backups

# Initiate restore
curl -X POST \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"timestamp":"2025-02-14T10:00:00Z"}' \
  https://api.us-central1.eventhorizoncloud.io/v1/backups/restore
```

### Multi-Region Replication (Enterprise)

PostgreSQL continuously replicated to standby region. RTO < 1 minute, RPO < 5 seconds.

---

## Security

### Network

- **TLS 1.3+** for all connections
- **VPC Service Controls** integration
- **Private GKE** with Workload Identity
- **Cloud SQL Private IP** (no public endpoint)
- **VPN** access for enterprise customers

### Data

- **Encryption at Rest**: AES-256 (Cloud Storage, Cloud SQL)
- **Encryption in Transit**: TLS 1.3
- **Key Management**: Google Cloud Key Management Service (KMS)
- **Customer-Managed Keys (CMEK)**: Enterprise only

### Access Control

- **OAuth 2.0** authentication
- **RBAC**: Owner, Admin, Operator, Viewer roles
- **Audit Logging**: All API calls, deployments, configuration changes
- **Secrets**: Stored in Google Secret Manager, never logged

### Compliance

- ✅ SOC 2 Type II
- ✅ ISO 27001
- ✅ GDPR compliant
- ✅ HIPAA available (Enterprise)

---

## Cost Management

### Pricing Model

**Base Tier** (covers compute, storage, backups):
- Starter: $299/month
- Professional: $999/month
- Enterprise: $2999/month

**Add-Ons** (optional, consumption-based):
- Work Tokens: $0.0001 per token (compute offloading)
- API calls beyond limit: $0.10 per 1000 calls
- Egress data: $0.12 per GB

### Cost Calculator

https://eventhorizoncloud.io/pricing

### Optimizing Cost

1. **Right-size replicas**: Use auto-scaling; don't over-provision
2. **Use Starter for dev**: Professional/Enterprise only for production
3. **Compress case data**: Reduce database storage
4. **Offload compute**: Use Work Tokens instead of higher tier
5. **Archive old cases**: Move historical data to Cloud Storage

---

## Support & SLA

### Support Hours & Response Times

| Plan | Hours | Email | Phone |
|------|-------|-------|-------|
| Starter | Business hours (UTC) | 4 hours | — |
| Professional | 24/5 (Mon-Fri) | 2 hours | — |
| Enterprise | 24/7 | 15 min | 24/7 |

### SLA Terms

| Plan | Uptime | Latency (p99) |
|------|--------|--------------|
| Starter | 99.5% | 500ms |
| Professional | 99.9% | 200ms |
| Enterprise | 99.99% | 100ms |

**SLA Credit**: If uptime < target, receive credit (% of monthly fee):
- 99.0–target: 10% credit
- 95.0–99.0: 25% credit
- < 95.0: 100% credit

---

## Getting Help

- **Documentation**: https://docs.eventhorizoncloud.io
- **Community**: #event-horizon (slack)
- **Support Portal**: https://support.eventhorizoncloud.io
- **Status Page**: https://status.eventhorizoncloud.io
- **Email**: support@eventhorizoncloud.io
- **Phone** (Enterprise): +1-844-EVENT-CLOUD

---

## Next Steps

1. ✅ Deploy Event Horizon Cloud
2. ✅ Create your first YAWL specification
3. ✅ Enable MCP Gateway or A2A Mesh
4. ✅ Connect GitHub, Slack, or Jira
5. ✅ Monitor dashboard and alerts
6. ✅ Scale as needed

**Welcome to the future of enterprise coordination.** 🌌
