# YAWL v6.0.0 Observability Deployment Checklist

Production deployment readiness checklist for observability infrastructure.

## Pre-Deployment Phase

### Code Integration
- [ ] Added `yawl-monitoring` dependency to all YAWL modules
- [ ] Called `OpenTelemetryInitializer.initialize()` at application startup
- [ ] Called `YawlMetrics.initialize(registry)` for metrics setup
- [ ] Implemented `HealthCheckDelegate` interface
- [ ] Created `HealthCheckEndpoint` and registered HTTP servlet/endpoint
- [ ] Added OpenTelemetry tracing to: YEngine, YNetRunner, YActivity, YTask
- [ ] Added metrics recording to: case creation, task execution, queue monitoring
- [ ] Added structured logging to critical paths
- [ ] Implemented correlation ID propagation (MDC)

### Dependencies Verified
- [ ] OpenTelemetry SDK 1.52.0+
- [ ] OpenTelemetry Instrumentation 2.18.1+
- [ ] Micrometer Prometheus 1.16.3+
- [ ] SLF4J 2.0.17+
- [ ] Log4j 2 2.25.3+
- [ ] Jackson 2.19.4+

### Configuration Files Prepared
- [ ] `prometheus-alerts.yml` - Alert rules configured
- [ ] `prometheus-scrape-config.yml` - Scrape targets defined
- [ ] `alertmanager-config.yml` - Alert routing configured
- [ ] `loki-config.yaml` - Log retention policies set
- [ ] Grafana dashboards - Imported and customized

### Build & Testing
- [ ] `mvn clean compile` passes
- [ ] `mvn clean test` passes (including ObservabilityTest)
- [ ] Code quality checks pass (SpotBugs, Checkstyle)
- [ ] Security scan passes (OWASP dependency check)

## Development Environment

### Local Docker Setup
- [ ] docker-compose.yml configured with:
  - [ ] YAWL Engine service
  - [ ] PostgreSQL database
  - [ ] OpenTelemetry Collector
  - [ ] Jaeger (tracing backend)
  - [ ] Prometheus (metrics)
  - [ ] Grafana (dashboards)
  - [ ] Loki (log aggregation)
  - [ ] AlertManager (alerting)

### Local Testing
- [ ] YAWL starts without errors
- [ ] Health endpoints respond correctly
  - [ ] `GET /health/live` returns HTTP 200
  - [ ] `GET /health/ready` returns HTTP 200
  - [ ] `GET /health/startup` returns HTTP 200
- [ ] Prometheus scrapes metrics successfully
  - [ ] Check `http://localhost:9090/targets`
  - [ ] Verify YAWL engine target is UP
- [ ] Jaeger receives traces
  - [ ] Check `http://localhost:16686` for yawl-engine service
  - [ ] Verify case execution traces appear
- [ ] Grafana dashboards display correctly
  - [ ] Import YAWL Overview dashboard
  - [ ] Verify case throughput data
  - [ ] Verify latency percentiles
- [ ] Loki aggregates logs
  - [ ] Query logs in Grafana Explore
  - [ ] Verify JSON structure
  - [ ] Check correlation IDs

### Performance Baseline
- [ ] Record baseline metrics:
  - [ ] Case execution time (P50/P95/P99)
  - [ ] Task throughput (tasks/sec)
  - [ ] Queue depth under normal load
  - [ ] Memory consumption
  - [ ] CPU utilization
- [ ] Verify observability overhead < 5%
  - [ ] Compare throughput with/without tracing
  - [ ] Monitor memory impact

## Staging Environment

### Infrastructure Setup
- [ ] Kubernetes cluster available or staging servers
- [ ] Storage for persistent volumes (Prometheus, Loki, Grafana)
- [ ] Network policies configured
- [ ] TLS certificates ready for HTTPS endpoints

### Deployment
- [ ] Applied Kubernetes manifests:
  - [ ] YAWL Deployment with environment variables
  - [ ] OpenTelemetry Collector Deployment
  - [ ] Prometheus StatefulSet
  - [ ] Grafana Deployment
  - [ ] Loki StatefulSet
  - [ ] AlertManager StatefulSet
  - [ ] ConfigMaps for configurations
  - [ ] Secrets for credentials
- [ ] Verified resource requests/limits are set:
  - [ ] YAWL: 1GB min/4GB max heap
  - [ ] Prometheus: 512MB min/2GB max
  - [ ] Grafana: 256MB min/1GB max
  - [ ] Loki: 512MB min/2GB max

### Health & Probes
- [ ] Liveness probe configured (10s interval, 3 failures)
- [ ] Readiness probe configured (5s interval, 3 failures)
- [ ] Startup probe configured (1s interval, 30 failures)
- [ ] Verified probes are working:
  - [ ] `kubectl logs -f <pod>` for startup probe
  - [ ] `kubectl describe pod <pod>` shows probe status
  - [ ] Manual requests to /health/* endpoints

### Data Verification
- [ ] Prometheus collecting metrics
  - [ ] `http://prometheus:9090` accessible
  - [ ] YAWL engine metrics visible
  - [ ] Query `yawl_case_created_total` returns data
- [ ] Traces reaching Jaeger
  - [ ] `http://jaeger:16686` accessible
  - [ ] Case execution traces visible
  - [ ] Trace latency matches expectations
- [ ] Logs aggregating in Loki
  - [ ] Loki API responding
  - [ ] JSON logs properly formatted
  - [ ] LogQL queries return results
- [ ] Grafana dashboards functional
  - [ ] Import YAWL Overview dashboard
  - [ ] Verify all panels showing data
  - [ ] Test dashboard variables/filters

### Alerting
- [ ] AlertManager receiving alerts
- [ ] Slack integration tested:
  - [ ] Test alert sent to #yawl-alerts
  - [ ] Verify formatting and details
- [ ] PagerDuty integration tested:
  - [ ] Fire CRITICAL alert
  - [ ] Verify PagerDuty incident created
  - [ ] Test escalation policy
- [ ] Email notifications tested (if configured)

### Documentation
- [ ] Runbooks written for common issues:
  - [ ] High queue depth procedure
  - [ ] Case latency spike response
  - [ ] No workers situation
  - [ ] Database connection exhaustion
- [ ] On-call escalation documented
- [ ] Team trained on dashboards

## Production Deployment

### Pre-Deployment Review
- [ ] Architecture review completed
- [ ] Security review completed
  - [ ] No secrets in code
  - [ ] TLS configured for endpoints
  - [ ] Access controls verified
- [ ] Performance review completed
  - [ ] Observability overhead < 5% acceptable
  - [ ] No memory leaks in traces
  - [ ] Storage requirements calculated
- [ ] Capacity planning completed
  - [ ] Prometheus storage: 2GB/month expected
  - [ ] Loki storage: 500MB/month expected
  - [ ] Jaeger storage (if using backend)
  - [ ] Grafana storage (dashboards, annotations)

### Deployment Execution
- [ ] Applied Kubernetes manifests to production
  - [ ] YAWL deployment with prod labels
  - [ ] Monitoring stack deployment
  - [ ] ConfigMaps and Secrets created
- [ ] Verified cluster has sufficient resources:
  - [ ] `kubectl top nodes` shows capacity
  - [ ] PersistentVolumes available and sized
  - [ ] Network bandwidth sufficient
- [ ] Verified initial deployment status:
  - [ ] All pods running: `kubectl get pods`
  - [ ] No pending pods
  - [ ] No crashloop pods
  - [ ] All health checks passing

### Smoke Tests (First 15 minutes)
- [ ] YAWL engine responding to requests
- [ ] Health endpoints returning healthy status
- [ ] Prometheus collecting metrics (check targets)
- [ ] Jaeger receiving traces (check service list)
- [ ] Loki aggregating logs (test query)
- [ ] Grafana dashboards loading (check YAWL Overview)
- [ ] No errors in component logs
- [ ] No memory/CPU spikes

### Production Validation (First hour)
- [ ] Create test cases, monitor metrics:
  - [ ] yawl_case_created_total increasing
  - [ ] yawl_case_duration_seconds populated
  - [ ] yawl_task_executed_total increasing
- [ ] Verify traces for test cases:
  - [ ] Find traces in Jaeger
  - [ ] Verify span hierarchy correct
  - [ ] Check latency measurements
- [ ] Verify logs for test cases:
  - [ ] JSON format correct
  - [ ] Correlation IDs present
  - [ ] All log levels present
- [ ] Verify alerts functioning:
  - [ ] Test CRITICAL alert (simulate condition)
  - [ ] Verify Slack notification received
  - [ ] Verify PagerDuty incident created
- [ ] Monitor resource consumption:
  - [ ] Memory steady state reached
  - [ ] CPU utilization normal
  - [ ] Disk I/O normal
  - [ ] Network I/O normal

### Baseline Metrics (End of day 1)
- [ ] Record key metrics:
  - [ ] Cases processed (rate)
  - [ ] Average case duration
  - [ ] P95/P99 latencies
  - [ ] Error rate
  - [ ] Queue depth patterns
  - [ ] Worker utilization
- [ ] Compare with staging baseline
- [ ] Identify any anomalies
- [ ] Document SLO baselines

## Post-Deployment Monitoring

### Week 1 Monitoring
- [ ] Daily review of dashboards
- [ ] Check alert trending (should be low)
- [ ] Verify data retention policies working
- [ ] Monitor storage growth:
  - [ ] Prometheus disk usage
  - [ ] Loki disk usage
  - [ ] Database size
- [ ] Identify optimization opportunities

### Week 2-4 Monitoring
- [ ] Confirm SLO metrics are stable
- [ ] Validate alerting thresholds appropriate
  - [ ] Adjust false positive rates
  - [ ] Refine alert conditions
- [ ] Ensure runbooks are accurate
- [ ] Update escalation policies if needed

### Ongoing Operations
- [ ] Daily alert review (fix root causes)
- [ ] Weekly metrics review (identify trends)
- [ ] Monthly performance analysis
  - [ ] Review SLO compliance
  - [ ] Analyze slow activities
  - [ ] Plan optimizations
- [ ] Quarterly infrastructure review
  - [ ] Storage capacity planning
  - [ ] Backup verification
  - [ ] Disaster recovery testing

## Rollback Plan

### If Issues Detected
1. [ ] Pause all observability exports
   ```bash
   kubectl set env deployment/yawl-engine \
     OTEL_EXPORTER_OTLP_ENDPOINT=""
   ```
2. [ ] Stop health probe failures from causing restarts
   ```bash
   kubectl patch deployment yawl-engine --patch '
   {"spec": {"template": {"spec": {"containers": [
     {"name": "yawl", "livenessProbe": null}
   ]}}}}
   ```
3. [ ] Verify application continues running
4. [ ] Investigate root cause
5. [ ] Restore once fixed

### Complete Rollback
- [ ] Delete observability components
  ```bash
  kubectl delete -f observability/
  ```
- [ ] Verify application still operational
- [ ] Review and fix before retry

## Sign-Off

### Development Team
- [ ] Code integration reviewed by lead
- [ ] Tests passing and coverage acceptable
- [ ] Security requirements met

### QA Team
- [ ] Staging validation complete
- [ ] All test scenarios passed
- [ ] Performance baseline acceptable
- [ ] Documentation complete

### Operations Team
- [ ] Production deployment plan reviewed
- [ ] Runbooks prepared
- [ ] Escalation policies configured
- [ ] Monitoring dashboards functional

### Management
- [ ] Business case approved
- [ ] SLOs defined and agreed
- [ ] Cost/benefit analysis acceptable
- [ ] Go/no-go decision made

## Post-Deployment Sign-Off

**Deployed by:** _________________ **Date:** _________

**Verified by (Ops):** _________________ **Date:** _________

**Approved by (Manager):** _________________ **Date:** _________

## Notes

```
[Space for deployment notes, issues encountered, and resolutions]
```
