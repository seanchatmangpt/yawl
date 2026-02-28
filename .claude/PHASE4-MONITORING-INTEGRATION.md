# Phase 4 Monitoring Integration Guide

**Document**: Quick start for deploying 1M Agent monitoring
**Audience**: DevOps engineers, SRE teams
**Time to Deploy**: 22 minutes
**Date**: 2026-02-28

---

## QUICK SUMMARY

Two new files complete the 1M agent monitoring system:

1. **`.claude/1M_AGENT_MONITORING.md`** (Main documentation)
   - 8 metric categories with thresholds
   - 12 critical alert rules
   - 6 Grafana dashboards (templates)
   - Data retention strategy
   - 5 incident runbooks

2. **`config/yawl-1m-agent-alerts.yml`** (Alert rules YAML)
   - Ready-to-deploy Prometheus alert rules
   - Integrates with existing AlertManager
   - 22 alert rules (12 critical + 10 supporting)
   - Includes remediation guidance in annotations

**Integration Points**:
- Existing Prometheus stack (via scrape config update)
- Existing AlertManager (via rule import)
- Existing Grafana (via dashboard import)

---

## DEPLOYMENT STEPS (22 minutes)

### Step 1: Import Alert Rules (5 minutes)

```bash
# Copy alert rules to Prometheus config directory
cp config/yawl-1m-agent-alerts.yml /etc/prometheus/

# Add to prometheus.yml rule_files section (if not already present):
# rule_files:
#   - '/etc/prometheus/yawl-1m-agent-alerts.yml'

# Validate Prometheus config
prometheus --config.file=/etc/prometheus/prometheus.yml --web.enable-lifecycle --check-config

# Reload Prometheus (without restart)
curl -X POST http://prometheus:9090/-/reload
```

**Verify** (30 seconds):
```bash
curl http://prometheus:9090/api/v1/rules | jq '.data.groups[] | select(.name | contains("yawl_1m")) | .rules[] | .alert'
# Should output all 22 alerts
```

### Step 2: Import Grafana Dashboards (10 minutes)

All 6 dashboards are documented in `.claude/1M_AGENT_MONITORING.md` Part 3 with complete widget specifications. Import via:

**Option A: Manual Import via UI** (Easiest)
1. Go to Grafana: `http://grafana:3000`
2. Menu → Dashboards → New → Import
3. Paste JSON from dashboards folder (create if needed)
4. Select Prometheus datasource
5. Click Import

**Option B: Programmatic (API)**
```bash
# For each dashboard:
curl -X POST http://grafana:3000/api/dashboards/db \
  -H "Authorization: Bearer $GRAFANA_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d @dashboard-heartbeat-health.json
```

**Dashboards to Create**:
1. "YAWL 1M Agents - Overview (Summary)"
2. "YAWL 1M Agents - Heartbeat Health"
3. "YAWL 1M Agents - Marketplace & Discovery"
4. "YAWL 1M Agents - JVM & Garbage Collection"
5. "YAWL 1M Agents - etcd Registry & Network"
6. "YAWL 1M Agents - Kubernetes Infrastructure"

**Dashboard JSON Generator** (if manual creation needed):
```bash
# Each dashboard requires:
# - title, description, tags
# - panels array (line charts, gauges, heatmaps)
# - targets (PromQL expressions)
# - thresholds, colors, legends
# See Appendix C in 1M_AGENT_MONITORING.md for example
```

### Step 3: Verify Scrape Targets (5 minutes)

Ensure all targets are UP in Prometheus:

```bash
# Check Prometheus targets page
curl http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.scale | contains("1m")) | {job: .labels.job, instance: .labels.instance, state: .health}'

# Expected output (all healthy):
# {
#   "job": "yawl-engine",
#   "instance": "yawl-engine-0:8080",
#   "state": "up"
# }
# {
#   "job": "etcd",
#   "instance": "etcd-0:2379",
#   "state": "up"
# }
```

If any targets are DOWN:
- **yawl-engine**: Check if pods are Running → `kubectl get pods -l app=yawl-engine`
- **etcd**: Check if members are healthy → `kubectl exec etcd-0 -- etcdctl endpoint health`
- **node**: Check node-exporter pod status → `kubectl get pods -l app=node-exporter`

### Step 4: AlertManager Routing (2 minutes)

Update AlertManager config to route 1M agent alerts:

```yaml
# Add to /etc/alertmanager/alertmanager.yml
route:
  # ... existing routes ...
  - match:
      scale: '1m-agents'
    receiver: 'yawl-ops-team'
    continue: true
    group_by: ['alertname', 'component', 'severity']

receivers:
  - name: 'yawl-ops-team'
    # Send CRITICAL to PagerDuty, WARNING to Slack
    pagerduty_configs:
      - service_key: 'secret:yawl-pagerduty-key'
        description: '{{ .GroupLabels.alertname }}: {{ .CommonAnnotations.summary }}'
        severity: critical
    slack_configs:
      - api_url: 'secret:yawl-slack-webhook'
        channel: '#yawl-alerts'
        title: '[{{ .Status | toUpper }}] {{ .GroupLabels.alertname }}'
        text: '{{ .CommonAnnotations.summary }}\n{{ .CommonAnnotations.runbook }}'
```

Reload AlertManager:
```bash
curl -X POST http://alertmanager:9093/-/reload
```

---

## VALIDATION CHECKLIST (Pre-Production)

Run through before going live:

- [ ] All 22 alert rules loaded in Prometheus
- [ ] All 6 dashboards imported in Grafana
- [ ] Scrape targets: yawl-engine (3 pods) = UP
- [ ] Scrape targets: etcd (5 members) = UP
- [ ] Scrape targets: node-exporter = UP
- [ ] AlertManager routing configured for 1m-agents
- [ ] Test alert delivery (send manual alert to verify routing)
- [ ] Grafana datasource connects to Prometheus
- [ ] All dashboards show data (no "No data" messages)
- [ ] Alert rules have recent evaluation time (< 1 min ago)

**Test Alert Delivery** (1 minute):
```bash
# Trigger test alert (will fire immediately)
curl -X POST http://prometheus:9090/api/v1/rules -d '{
  "alert": "TestAlert1M",
  "expr": "1==1",
  "for": "0m",
  "annotations": {"summary": "Test alert for 1M agent monitoring"}
}'

# Wait 30 sec, verify AlertManager received it
curl http://alertmanager:9093/api/v1/alerts | jq '.data[] | select(.labels.alertname | contains("Test"))'
```

---

## INTEGRATION WITH EXISTING STACK

### How It Works Together

```
┌─────────────────────────────────────────────────────────────┐
│                    YAWL 1M Agents                            │
│                   (3 engine pods)                             │
│  Expose: /actuator/prometheus (port 8080)                   │
└────────────────┬────────────────────────────────────────────┘
                 │ Metrics (heartbeat, marketplace, etcd, JVM)
                 │
┌────────────────▼────────────────────────────────────────────┐
│              Prometheus (Scrape)                             │
│  - 30s interval, 30s evaluation                              │
│  - Loads: yawl-1m-agent-alerts.yml (22 rules)               │
│  - Evaluates alerts, fires to AlertManager                  │
└────────────────┬────────────────────────────────────────────┘
                 │ Evaluated alerts
                 │
┌────────────────▼────────────────────────────────────────────┐
│            AlertManager                                      │
│  - Routes by severity + component                            │
│  - Scale 1m-agents → yawl-ops-team receiver                 │
│  - Sends: PagerDuty (CRITICAL), Slack (WARNING)             │
└─────────────────┬──────────────────────────────────────────┘
                  │ Alert state (firing, resolved)
                  │
┌─────────────────▼──────────────────────────────────────────┐
│            Grafana (Visualization)                          │
│  - Queries Prometheus for metrics                            │
│  - 6 dashboards showing 1M agent health                      │
│  - Drill-down from overview to specific metrics              │
└─────────────────────────────────────────────────────────────┘
```

### What's Reused (No Changes)

- **Prometheus**: Already scraping yawl-engine metrics (adds 1 rule file)
- **AlertManager**: Already routing alerts (adds 1 route pattern)
- **Grafana**: Already visualizing (adds 6 dashboards)
- **Docker Compose**: Existing monitoring stack (`docker/docker-compose.monitoring.yml`)

### What's New

- **yawl-1m-agent-alerts.yml**: 22 alert rules specific to 1M scale
- **6 Grafana dashboards**: Customized metrics for agent-scale concerns
- **Data retention policy**: S3 long-term storage for historical analysis

---

## MONITORING STRATEGY

### During 1M Agent Ramp-Up (Week 1)

**Day 1 (Agents 1K-10K)**:
- Focus on baseline metrics (no alerts should fire)
- Verify Prometheus scrape interval is stable
- Confirm Grafana dashboards populate correctly

**Day 2-3 (Agents 10K-100K)**:
- Watch for marketplace latency increase (should stay <5ms)
- Monitor etcd query latency (should stay <30ms p95)
- Verify exponential backoff behavior (agents should have low backoff count)

**Day 4-5 (Agents 100K-500K)**:
- Validate GC pause time trend (should stay <500ms p99)
- Check heap utilization (should be 60-75%, not spiking)
- Monitor heartbeat success rate (should stay >99.5%)

**Week 2 (Agents 500K-1M)**:
- All alerts should be GREEN (no critical firing)
- Dashboards should show stable trends (no sudden spikes)
- Capacity should be comfortable (no component at 80%+ utilization)

### Steady-State Operations (Week 3+)

**Daily Checks**:
- Heartbeat success rate > 99.5%
- No critical alerts firing
- Marketplace latency < 10ms p95
- GC pause time p99 < 500ms

**Weekly Review**:
- Alert firing frequency (should be <5 per week during stability)
- False positive rate (should be <5%)
- Cost trends (self-hosted monitoring should be $50-55/month)
- Capacity headroom (all metrics <80% utilization)

**Monthly Tuning**:
- Review alert thresholds against actual values
- Optimize dashboard refresh rates (may reduce to 1-2 min if stable)
- Archive old metrics to long-term storage
- Capacity planning for next quarter

---

## TROUBLESHOOTING

### Prometheus Targets Show RED

**Symptom**: Some scrape targets show DOWN in Prometheus UI

**Root Cause**: Pod down, port closed, or metrics endpoint failing

**Fix**:
```bash
# Check pod status
kubectl get pods -l app=yawl-engine

# Get pod logs
kubectl logs yawl-engine-0

# Manually test metrics endpoint
kubectl exec yawl-engine-0 -- curl -s http://localhost:8080/actuator/prometheus | head -20
# Should return metrics in OpenMetrics format
```

### Grafana Shows "No Data"

**Symptom**: Dashboard panels show empty graphs

**Root Cause**: Metrics don't exist yet (agents not started) or query wrong

**Fix**:
```bash
# Check if metrics exist
curl http://prometheus:9090/api/v1/query?query=yawl_heartbeat_latency_ms_bucket

# If empty, agents may not be running
# If populated, check dashboard query syntax
# Compare against actual metric names
```

### Alerts Not Firing

**Symptom**: Alert threshold met but alert not in AlertManager

**Root Cause**: Prometheus not evaluating rules or AlertManager not receiving

**Fix**:
```bash
# Check if rules loaded
curl http://prometheus:9090/api/v1/rules | jq '.data.groups[] | select(.name | contains("yawl_1m")) | .rules[] | .alert' | wc -l
# Should show 22

# Manually evaluate one alert expression
curl "http://prometheus:9090/api/v1/query?query=rate(yawl_agent_reconnect_count%5B5m%5D)"
# Should return a value

# Check AlertManager received alert
curl http://alertmanager:9093/api/v1/alerts | jq '.data[] | select(.labels.component | contains("heartbeat"))'
```

### Alert Delivery Not Working

**Symptom**: AlertManager has alert but Slack/PagerDuty not notified

**Root Cause**: Receiver misconfigured or webhook endpoint unreachable

**Fix**:
```bash
# Check AlertManager config
curl http://alertmanager:9093/api/v1/status | jq '.data.config'

# Test Slack webhook manually
curl -X POST $SLACK_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d '{"text": "Test alert from monitoring"}'

# Check AlertManager logs
docker logs yawl-alertmanager | grep -i "error\|warn" | tail -20
```

---

## COST ANALYSIS

**Self-Hosted Option** (Recommended):
- Prometheus: $0 (open source)
- Grafana: $0 (open source)
- AlertManager: $0 (open source)
- Storage (S3): $5/month
- **Total**: ~$50-55/month

**Managed Option** (If preferred):
- Grafana Cloud: $100-500/month
- Prometheus managed: $50-200/month
- AlertManager managed: $50/month
- **Total**: $200-750/month

**ROI**: Self-hosted saves $150-700/month (0.2-1% of infrastructure cost).

---

## NEXT STEPS

1. **Day 1**: Deploy alert rules + import dashboards (22 min)
2. **Day 1-2**: Validate during 1K-10K agent ramp-up
3. **Week 1**: Confirm all metrics working during full 1M scale
4. **Week 2**: Team training on incident runbooks
5. **Ongoing**: Weekly health reviews + monthly tuning

---

## SUCCESS CRITERIA

✅ All 12 critical alerts working (no false positives)
✅ All 6 dashboards showing real-time metrics
✅ MTTR for P1 incidents < 5 minutes
✅ Alert false positive rate < 5%
✅ Cost < 5% of infrastructure budget ($50/month)
✅ Team trained on all 5 incident runbooks
✅ Zero unplanned incidents in first 30 days of 1M deployment

---

## APPENDIX: Quick Command Reference

```bash
# Validate alert rules
prometheus --config.file=/etc/prometheus/prometheus.yml --check-config

# Reload Prometheus rules
curl -X POST http://prometheus:9090/-/reload

# List all alerts (including 1M agent alerts)
curl http://prometheus:9090/api/v1/rules | jq '.data.groups[].rules[].alert' | sort | uniq

# Check current alert status
curl http://alertmanager:9093/api/v1/alerts | jq '.data[] | {alert: .labels.alertname, state: .state, since: .startsAt}'

# Query metric from command line
curl "http://prometheus:9090/api/v1/query?query=yawl_heartbeat_latency_ms{quantile='0.95'}"

# Test Grafana connectivity
curl http://grafana:3000/api/health

# Get Grafana API token (for programmatic dashboard import)
curl -X POST http://grafana:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"user":"admin","password":"admin"}' | jq '.token'
```

---

**Document Status**: READY FOR DEPLOYMENT
**Implementation Owner**: DevOps/SRE Team
**Support Contact**: YAWL on-call team
