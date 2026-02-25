# YAWL Service Level Objectives (SLOs) - v5.2

**Production SLA Commitments**
**Version:** 6.0.0
**Effective Date:** 2026-03-02
**Review Cycle:** Quarterly

---

## Table of Contents

1. [SLO Overview](#1-slo-overview)
2. [Availability SLO](#2-availability-slo)
3. [Latency SLO](#3-latency-slo)
4. [Error Rate SLO](#4-error-rate-slo)
5. [Data Durability SLO](#5-data-durability-slo)
6. [Monitoring & Alerting](#6-monitoring--alerting)
7. [SLO Reporting](#7-slo-reporting)

---

## 1. SLO Overview

### Definition

**Service Level Objective (SLO):** A target value or range of values for a service level that is measured by a Service Level Indicator (SLI).

**Service Level Indicator (SLI):** A carefully defined quantitative measure of some aspect of the level of service provided.

**Error Budget:** The amount of error that is tolerable within a given time period (100% - SLO%).

### SLO Hierarchy

```
SLO (Target) → SLI (Measurement) → Implementation (Monitoring)
```

### YAWL SLO Commitments

| Service Aspect | SLO Target | Error Budget | Time Window |
|----------------|------------|--------------|-------------|
| **Availability** | 99.95% | 0.05% (21.6 min/month) | 30 days rolling |
| **Latency (p95)** | < 500ms | 5% requests allowed > 500ms | 5 minutes |
| **Error Rate** | < 0.1% | 0.1% requests allowed to fail | 5 minutes |
| **Data Durability** | 100% | 0% data loss tolerated | Always |

---

## 2. Availability SLO

### Target

**99.95% uptime** (21.6 minutes downtime per month)

### SLI Definition

```
Availability = (Total Time - Downtime) / Total Time × 100%

Downtime = Time when health check fails continuously for > 60 seconds
```

### Measurement

**Health Check Endpoint:** `/engine/health`

**Success Criteria:**
- HTTP 200 response code
- Response time < 5 seconds
- Response body contains `{"status": "UP"}`

**Exclusions:**
- Planned maintenance windows (announced 7 days in advance)
- User-initiated downtime (e.g., customer requests service pause)
- Force majeure events (natural disasters, provider outages)

### Implementation

#### Prometheus Query

```promql
# Availability over 30 days
(1 - (
  sum(rate(up{job="yawl-engine"}[30d]) == 0)
  /
  sum(rate(up{job="yawl-engine"}[30d]))
)) * 100
```

#### Alerting Rules

```yaml
groups:
  - name: availability
    rules:
      - alert: YawlEngineDown
        expr: up{job="yawl-engine"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "YAWL Engine is down"
          description: "YAWL Engine has been down for > 1 minute"

      - alert: YawlAvailabilitySLOBreach
        expr: |
          (1 - (
            sum(rate(up{job="yawl-engine"}[30d]) == 0)
            /
            sum(rate(up{job="yawl-engine"}[30d]))
          )) * 100 < 99.95
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "YAWL Availability SLO breached"
          description: "30-day availability: {{ $value }}% (target: 99.95%)"
```

### Reporting

**Monthly Availability Report:**
```sql
-- PostgreSQL query for uptime calculation
SELECT
  DATE_TRUNC('month', timestamp) AS month,
  COUNT(*) AS total_checks,
  SUM(CASE WHEN status = 'UP' THEN 1 ELSE 0 END) AS up_checks,
  (SUM(CASE WHEN status = 'UP' THEN 1 ELSE 0 END)::FLOAT / COUNT(*) * 100) AS availability_pct,
  (100 - (SUM(CASE WHEN status = 'UP' THEN 1 ELSE 0 END)::FLOAT / COUNT(*) * 100)) * 43200 AS downtime_minutes
FROM health_check_log
WHERE timestamp >= NOW() - INTERVAL '30 days'
GROUP BY DATE_TRUNC('month', timestamp)
ORDER BY month DESC;
```

---

## 3. Latency SLO

### Target

**p95 latency < 500ms** for 99% of time windows

### SLI Definition

```
p95_latency = 95th percentile of response times for all API calls

Time window = 5 minutes
```

### API Endpoints Covered

| Endpoint | Operation | p95 Target | p99 Target |
|----------|-----------|------------|------------|
| `/engine/api/cases` POST | Create case | < 500ms | < 1000ms |
| `/engine/api/workitems/:id` GET | Checkout work item | < 200ms | < 500ms |
| `/engine/api/specifications` GET | List specifications | < 100ms | < 300ms |
| `/engine/api/cases/:id` GET | Get case details | < 200ms | < 500ms |
| `/engine/api/workitems/:id` PUT | Complete work item | < 300ms | < 800ms |

### Implementation

#### Prometheus Query

```promql
# p95 latency over 5 minutes
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{job="yawl-engine"}[5m])) by (le, uri)
) * 1000
```

#### Alerting Rules

```yaml
groups:
  - name: latency
    rules:
      - alert: YawlHighLatency
        expr: |
          histogram_quantile(0.95,
            sum(rate(http_server_requests_seconds_bucket{job="yawl-engine"}[5m])) by (le, uri)
          ) > 0.5
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "YAWL p95 latency > 500ms"
          description: "p95 latency: {{ $value }}s for {{ $labels.uri }}"

      - alert: YawlCriticalLatency
        expr: |
          histogram_quantile(0.95,
            sum(rate(http_server_requests_seconds_bucket{job="yawl-engine"}[5m])) by (le, uri)
          ) > 2.0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "YAWL p95 latency > 2s (CRITICAL)"
          description: "p95 latency: {{ $value }}s for {{ $labels.uri }}"
```

### Error Budget

**95% of 5-minute windows must meet p95 < 500ms**

**Error Budget Calculation:**
```
Total 5-minute windows in 30 days: 8,640
Allowed violations: 8,640 × 0.05 = 432 windows
```

If > 432 windows have p95 > 500ms, SLO is breached.

---

## 4. Error Rate SLO

### Target

**< 0.1% error rate** (99.9% success rate)

### SLI Definition

```
Error Rate = (Failed Requests / Total Requests) × 100%

Failed Request = HTTP status code 5xx OR unexpected 4xx (exclude 401, 403, 404)
```

### Implementation

#### Prometheus Query

```promql
# Error rate over 5 minutes
(
  sum(rate(http_server_requests_seconds_count{job="yawl-engine",status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{job="yawl-engine"}[5m]))
) * 100
```

#### Alerting Rules

```yaml
groups:
  - name: error_rate
    rules:
      - alert: YawlHighErrorRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{job="yawl-engine",status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count{job="yawl-engine"}[5m]))
          ) * 100 > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "YAWL error rate > 0.1%"
          description: "Error rate: {{ $value }}% (target: < 0.1%)"

      - alert: YawlCriticalErrorRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{job="yawl-engine",status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count{job="yawl-engine"}[5m]))
          ) * 100 > 1.0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "YAWL error rate > 1% (CRITICAL)"
          description: "Error rate: {{ $value }}%"
```

### Error Budget

**Monthly Error Budget:**
```
Total requests per month (estimated): 10,000,000
Allowed failures: 10,000,000 × 0.001 = 10,000 requests
```

If > 10,000 requests fail in a month, SLO is breached.

---

## 5. Data Durability SLO

### Target

**100% data durability** (zero data loss)

### SLI Definition

```
Data Durability = (Cases Persisted Successfully / Cases Created) × 100%

Recovery Point Objective (RPO): 0 minutes (immediate)
Recovery Time Objective (RTO): 15 minutes
```

### Implementation

#### Database Backup Strategy

1. **Continuous WAL Archiving**
   - PostgreSQL Write-Ahead Logging (WAL)
   - Archived to cloud storage every 1 minute
   - Retention: 30 days

2. **Automated Daily Backups**
   - Full database backup at 02:00 UTC
   - Retention: 30 days
   - Backup verification automated

3. **Point-in-Time Recovery**
   - Can restore to any point in last 30 days
   - Recovery granularity: 1 minute

#### Backup Verification

```bash
#!/bin/bash
# Automated backup verification (runs daily)

# Restore backup to test database
gcloud sql instances clone yawl-db yawl-db-test --backup-id $BACKUP_ID

# Verify data integrity
kubectl exec -it postgres-test-0 -- psql -U postgres -d yawl -c "
SELECT COUNT(*) FROM yawl_cases;
SELECT COUNT(*) FROM yawl_workitems;
SELECT MAX(created_at) FROM yawl_cases;
"

# Cleanup test database
gcloud sql instances delete yawl-db-test --quiet
```

#### Alerting Rules

```yaml
groups:
  - name: data_durability
    rules:
      - alert: YawlBackupFailed
        expr: yawl_backup_success == 0
        for: 1h
        labels:
          severity: critical
        annotations:
          summary: "YAWL database backup failed"

      - alert: YawlBackupVerificationFailed
        expr: yawl_backup_verification_success == 0
        for: 1h
        labels:
          severity: critical
        annotations:
          summary: "YAWL backup verification failed"

      - alert: YawlDataLoss
        expr: increase(yawl_database_write_errors_total[5m]) > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "YAWL data write errors detected (potential data loss)"
```

### Reporting

**Monthly Data Durability Report:**
```sql
-- PostgreSQL query for data integrity check
SELECT
  'Cases' AS table_name,
  COUNT(*) AS total_rows,
  COUNT(DISTINCT id) AS unique_ids,
  COUNT(*) - COUNT(DISTINCT id) AS duplicate_rows,
  MIN(created_at) AS oldest_record,
  MAX(created_at) AS newest_record
FROM yawl_cases
UNION ALL
SELECT
  'WorkItems' AS table_name,
  COUNT(*) AS total_rows,
  COUNT(DISTINCT id) AS unique_ids,
  COUNT(*) - COUNT(DISTINCT id) AS duplicate_rows,
  MIN(created_at) AS oldest_record,
  MAX(created_at) AS newest_record
FROM yawl_workitems;
```

---

## 6. Monitoring & Alerting

### Grafana Dashboard

**YAWL Operational Dashboard:**
```json
{
  "dashboard": {
    "title": "YAWL SLO Dashboard",
    "panels": [
      {
        "title": "Availability (30-day)",
        "targets": [{
          "expr": "(1 - (sum(rate(up{job=\"yawl-engine\"}[30d]) == 0) / sum(rate(up{job=\"yawl-engine\"}[30d])))) * 100"
        }],
        "thresholds": [
          {"value": 99.95, "color": "red"},
          {"value": 99.99, "color": "yellow"},
          {"value": 100, "color": "green"}
        ]
      },
      {
        "title": "p95 Latency",
        "targets": [{
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"yawl-engine\"}[5m])) by (le)) * 1000"
        }],
        "thresholds": [
          {"value": 500, "color": "green"},
          {"value": 1000, "color": "yellow"},
          {"value": 2000, "color": "red"}
        ]
      },
      {
        "title": "Error Rate (%)",
        "targets": [{
          "expr": "(sum(rate(http_server_requests_seconds_count{job=\"yawl-engine\",status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count{job=\"yawl-engine\"}[5m]))) * 100"
        }],
        "thresholds": [
          {"value": 0.1, "color": "green"},
          {"value": 0.5, "color": "yellow"},
          {"value": 1.0, "color": "red"}
        ]
      },
      {
        "title": "Active Cases",
        "targets": [{
          "expr": "yawl_engine_active_cases"
        }]
      },
      {
        "title": "Case Creation Rate",
        "targets": [{
          "expr": "rate(yawl_engine_cases_created_total[5m])"
        }]
      },
      {
        "title": "Database Connection Pool",
        "targets": [{
          "expr": "hikaricp_connections_active{pool=\"yawl-pool\"}"
        }, {
          "expr": "hikaricp_connections_max{pool=\"yawl-pool\"}"
        }]
      }
    ]
  }
}
```

### Alert Routing

```yaml
# Alertmanager configuration
route:
  receiver: 'team-yawl'
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 5m
  repeat_interval: 4h

  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true

    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: <PAGERDUTY_SERVICE_KEY>

  - name: 'slack'
    slack_configs:
      - api_url: <SLACK_WEBHOOK_URL>
        channel: '#yawl-alerts'
        title: 'YAWL Alert: {{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'

  - name: 'team-yawl'
    email_configs:
      - to: 'yawl-team@example.com'
```

---

## 7. SLO Reporting

### Monthly SLO Review

**Report Template:**

```markdown
# YAWL SLO Report - [Month YYYY]

## Executive Summary
- Overall SLO Status: [GREEN / YELLOW / RED]
- SLOs Met: X/4
- SLOs Breached: X/4

## Availability
- Target: 99.95%
- Actual: XX.XX%
- Status: [✓ MET / ✗ BREACHED]
- Downtime: XX minutes (budget: 21.6 min)

### Incidents
1. [Date] - [Duration] - [Root Cause]
2. ...

## Latency
- Target: p95 < 500ms
- Actual: p95 = XXXms
- Status: [✓ MET / ✗ BREACHED]

### Slowest Endpoints
1. [Endpoint] - XXXms
2. ...

## Error Rate
- Target: < 0.1%
- Actual: X.XX%
- Status: [✓ MET / ✗ BREACHED]

### Top Errors
1. [Error Type] - XXX occurrences
2. ...

## Data Durability
- Target: 100%
- Actual: 100%
- Status: [✓ MET]
- Backup Success Rate: XX.X%

## Error Budget Status
| SLO | Budget Consumed | Remaining |
|-----|-----------------|-----------|
| Availability | XX% | XX% |
| Latency | XX% | XX% |
| Error Rate | XX% | XX% |

## Action Items
- [ ] [Action 1]
- [ ] [Action 2]

## Recommendations
[Recommendations for next month]
```

### SLO Review Meeting

**Frequency:** Monthly (first week of each month)
**Attendees:** Engineering, Product, Customer Success
**Agenda:**
1. Review SLO metrics (15 min)
2. Discuss breaches and root causes (15 min)
3. Error budget status (10 min)
4. Action items and improvements (20 min)

---

## Appendix: SLO Calculation Scripts

### Python Script for SLO Calculation

```python
#!/usr/bin/env python3
"""
Calculate YAWL SLO compliance from Prometheus metrics
"""
import requests
from datetime import datetime, timedelta

PROMETHEUS_URL = "http://prometheus:9090"

def calculate_availability_slo():
    """Calculate 30-day availability SLO"""
    query = '(1 - (sum(rate(up{job="yawl-engine"}[30d]) == 0) / sum(rate(up{job="yawl-engine"}[30d])))) * 100'
    response = requests.get(f"{PROMETHEUS_URL}/api/v1/query", params={"query": query})
    result = response.json()
    availability = float(result['data']['result'][0]['value'][1])

    print(f"Availability: {availability:.4f}%")
    print(f"Target: 99.95%")
    print(f"Status: {'✓ MET' if availability >= 99.95 else '✗ BREACHED'}")

    downtime_minutes = (100 - availability) / 100 * 43200  # 30 days in minutes
    print(f"Downtime: {downtime_minutes:.2f} minutes (budget: 21.6 min)")

def calculate_latency_slo():
    """Calculate p95 latency SLO"""
    query = 'histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="yawl-engine"}[5m])) by (le)) * 1000'
    response = requests.get(f"{PROMETHEUS_URL}/api/v1/query", params={"query": query})
    result = response.json()
    p95_latency = float(result['data']['result'][0]['value'][1])

    print(f"p95 Latency: {p95_latency:.2f}ms")
    print(f"Target: < 500ms")
    print(f"Status: {'✓ MET' if p95_latency < 500 else '✗ BREACHED'}")

def calculate_error_rate_slo():
    """Calculate error rate SLO"""
    query = '(sum(rate(http_server_requests_seconds_count{job="yawl-engine",status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{job="yawl-engine"}[5m]))) * 100'
    response = requests.get(f"{PROMETHEUS_URL}/api/v1/query", params={"query": query})
    result = response.json()
    error_rate = float(result['data']['result'][0]['value'][1])

    print(f"Error Rate: {error_rate:.4f}%")
    print(f"Target: < 0.1%")
    print(f"Status: {'✓ MET' if error_rate < 0.1 else '✗ BREACHED'}")

if __name__ == "__main__":
    print("YAWL SLO Compliance Report")
    print(f"Generated: {datetime.now().isoformat()}\n")

    print("=== Availability SLO ===")
    calculate_availability_slo()
    print()

    print("=== Latency SLO ===")
    calculate_latency_slo()
    print()

    print("=== Error Rate SLO ===")
    calculate_error_rate_slo()
```

---

**Document Owner:** SRE Team
**Last Updated:** 2026-02-16
**Review Cycle:** Quarterly
**Next Review:** 2026-05-16
