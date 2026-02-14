# Performance Test Plan

**Product:** YAWL Workflow Engine v5.2
**Version:** 1.0
**Last Updated:** 2025-02-13

---

## 1. Executive Summary

This performance test plan defines the strategy and execution approach for validating YAWL Workflow Engine's performance characteristics across all cloud marketplace deployments.

### 1.1 Objectives

- Establish performance baselines for marketplace certification
- Validate scalability under expected production loads
- Identify performance bottlenecks and optimization opportunities
- Ensure SLA compliance under various load conditions
- Validate autoscaling behavior

### 1.2 Performance Targets

| Metric | Target | Threshold |
|--------|--------|-----------|
| API Response Time (P50) | < 100ms | < 200ms |
| API Response Time (P99) | < 500ms | < 1000ms |
| Workflow Launch Time | < 2s | < 5s |
| Throughput | > 500 RPS | > 200 RPS |
| Concurrent Workflows | > 10,000 | > 5,000 |
| Error Rate | < 0.1% | < 1% |
| CPU Utilization | < 70% | < 85% |
| Memory Utilization | < 75% | < 90% |

---

## 2. Test Environment

### 2.1 Infrastructure Configuration

#### Standard Configuration

```yaml
nodes: 3
instance_type: n1-standard-4 / m5.xlarge / Standard_D4s_v3
cpu: 4 vCPUs
memory: 16 GB
storage: 100 GB SSD
database: Managed (db.r5.large equivalent)
```

#### High Performance Configuration

```yaml
nodes: 6
instance_type: n1-standard-8 / m5.2xlarge / Standard_D8s_v3
cpu: 8 vCPUs
memory: 32 GB
storage: 200 GB SSD
database: Managed (db.r5.xlarge equivalent)
```

### 2.2 Test Data

| Data Type | Volume | Description |
|-----------|--------|-------------|
| Specifications | 100 | Various complexity levels |
| Active Workflows | Up to 10,000 | Running instances |
| Work Items | Up to 50,000 | Pending/active items |
| Users | 1,000 | Concurrent users |

---

## 3. Performance Test Types

### 3.1 Load Testing

**Objective:** Validate system behavior under expected load

| Test ID | Scenario | Virtual Users | Duration | Target RPS |
|---------|----------|---------------|----------|------------|
| LOAD-001 | Light load | 50 | 30 min | 100 |
| LOAD-002 | Normal load | 200 | 1 hour | 500 |
| LOAD-003 | Heavy load | 500 | 1 hour | 1000 |
| LOAD-004 | Peak load | 1000 | 30 min | 2000 |

### 3.2 Stress Testing

**Objective:** Find system breaking points

| Test ID | Scenario | Starting Users | Increment | Target |
|---------|----------|----------------|-----------|--------|
| STR-001 | Gradual increase | 100 | +100/5min | Failure |
| STR-002 | Sudden spike | 100 -> 1000 | Immediate | Recovery |
| STR-003 | Sustained stress | 800 | 2 hours | Stability |

### 3.3 Soak/Endurance Testing

**Objective:** Validate long-term stability

| Test ID | Duration | Load Level | Success Criteria |
|---------|----------|------------|------------------|
| SOAK-001 | 4 hours | 50% peak | No memory leak |
| SOAK-002 | 12 hours | 30% peak | Stable response |
| SOAK-003 | 24 hours | 20% peak | No degradation |

### 3.4 Spike Testing

**Objective:** Test sudden load changes

| Test ID | Baseline | Spike | Duration | Success Criteria |
|---------|----------|-------|----------|------------------|
| SPK-001 | 100 RPS | 500 RPS | 5 min | Recovery < 30s |
| SPK-002 | 200 RPS | 1000 RPS | 2 min | No errors |
| SPK-003 | 50 RPS | 2000 RPS | 1 min | Graceful handling |

### 3.5 Scalability Testing

**Objective:** Validate horizontal scaling

| Test ID | Starting Pods | Target Pods | Trigger | Success Criteria |
|---------|---------------|-------------|---------|------------------|
| SCL-001 | 2 | 5 | CPU > 70% | Scale in < 2 min |
| SCL-002 | 3 | 10 | RPS > 1000 | All pods healthy |
| SCL-003 | 10 | 3 | Load decrease | Scale down smooth |

---

## 4. Test Scenarios

### 4.1 API Performance Tests

#### Workflow Operations

| Test ID | Operation | Method | Endpoint | Payload |
|---------|-----------|--------|----------|---------|
| API-001 | Upload specification | POST | /api/specifications | XML spec |
| API-002 | Launch workflow | POST | /api/cases | Case data |
| API-003 | Get workflow status | GET | /api/cases/{id} | - |
| API-004 | List work items | GET | /api/workitems | - |
| API-005 | Complete work item | PUT | /api/workitems/{id} | Completion data |
| API-006 | Cancel workflow | DELETE | /api/cases/{id} | - |
| API-007 | Get logs | GET | /api/cases/{id}/logs | - |
| API-008 | Health check | GET | /health | - |

#### Concurrent Operation Mix

| Operation | Percentage |
|-----------|------------|
| Launch workflow | 20% |
| Get workflow status | 35% |
| List work items | 20% |
| Complete work item | 20% |
| Other operations | 5% |

### 4.2 Workflow Execution Tests

#### Simple Sequential Workflow

```
Start -> Task A -> Task B -> Task C -> End
```

| Metric | Target |
|--------|--------|
| Launch time | < 500ms |
| Task completion | < 200ms |
| Total execution | < 5s |

#### Parallel Workflow

```
Start -> Fork -> Task A
            -> Task B
            -> Task C
            -> Task D
      -> Join -> End
```

| Metric | Target |
|--------|--------|
| Launch time | < 1s |
| Parallel task completion | < 3s |
| Total execution | < 10s |

#### Complex Workflow

```
Start -> XOR Gateway -> Path A (3 tasks)
                   -> Path B (2 tasks)
                   -> Path C (4 tasks)
      -> Merge -> End
```

| Metric | Target |
|--------|--------|
| Launch time | < 2s |
| Conditional evaluation | < 100ms |
| Total execution | < 30s |

### 4.3 Database Performance Tests

| Test ID | Operation | Volume | Target Time |
|---------|-----------|--------|-------------|
| DB-001 | Insert workflow | 1 record | < 50ms |
| DB-002 | Batch insert work items | 100 records | < 500ms |
| DB-003 | Query active workflows | 10,000 records | < 200ms |
| DB-004 | Complex join query | 5 tables | < 500ms |
| DB-005 | Update workflow state | 1 record | < 30ms |
| DB-006 | Concurrent writes | 100 threads | No deadlocks |

---

## 5. Measurement Metrics

### 5.1 Response Time Metrics

| Metric | Description | Collection Method |
|--------|-------------|-------------------|
| P50 (Median) | 50th percentile response time | Load testing tool |
| P90 | 90th percentile response time | Load testing tool |
| P95 | 95th percentile response time | Load testing tool |
| P99 | 99th percentile response time | Load testing tool |
| Max | Maximum response time | Load testing tool |
| Mean | Average response time | Load testing tool |

### 5.2 Throughput Metrics

| Metric | Description | Collection Method |
|--------|-------------|-------------------|
| RPS | Requests per second | Load testing tool |
| TPS | Transactions per second | Application metrics |
| Workflows/min | Workflow completions per minute | Application metrics |
| Work items/sec | Work item completions per second | Application metrics |

### 5.3 Resource Metrics

| Metric | Description | Collection Method |
|--------|-------------|-------------------|
| CPU % | CPU utilization | Cloud monitoring |
| Memory % | Memory utilization | Cloud monitoring |
| Disk I/O | Read/write operations | Cloud monitoring |
| Network I/O | Bytes in/out | Cloud monitoring |
| DB Connections | Active connections | Database metrics |
| Thread Count | Active threads | JVM/Application metrics |

### 5.4 Error Metrics

| Metric | Description | Collection Method |
|--------|-------------|-------------------|
| Error Rate | % of failed requests | Load testing tool |
| HTTP 4xx | Client error count | Access logs |
| HTTP 5xx | Server error count | Access logs |
| Timeout Rate | Request timeout % | Load testing tool |

---

## 6. Tools and Infrastructure

### 6.1 Load Testing Tools

| Tool | Purpose | License |
|------|---------|---------|
| k6 | Primary load testing | Open Source |
| Locust | Python-based load testing | Open Source |
| JMeter | Complex scenarios | Open Source |
| Vegeta | HTTP load testing | Open Source |
| Gatling | High-performance testing | Open Source |

### 6.2 Monitoring Tools

| Tool | Purpose |
|------|---------|
| Prometheus | Metrics collection |
| Grafana | Visualization |
| Cloud-specific monitoring | Platform metrics |
| APM (DataDog/New Relic) | Application profiling |

### 6.3 Analysis Tools

| Tool | Purpose |
|------|---------|
| JMeter Plugins | Result analysis |
| k6 Cloud/Dashboard | Test visualization |
| Custom scripts | Data processing |

---

## 7. Test Execution

### 7.1 Pre-Test Checklist

- [ ] Test environment provisioned and healthy
- [ ] Test data loaded and verified
- [ ] Monitoring dashboards configured
- [ ] Load generators validated
- [ ] Baseline metrics captured
- [ ] Test scenarios reviewed

### 7.2 Execution Steps

1. **Environment Validation**
   - Health check all services
   - Verify database connectivity
   - Confirm monitoring is active

2. **Baseline Capture**
   - Run light load (50 RPS) for 10 minutes
   - Record baseline metrics
   - Verify no anomalies

3. **Test Execution**
   - Execute test according to schedule
   - Monitor in real-time
   - Capture all metrics

4. **Post-Test Analysis**
   - Export results
   - Compare against targets
   - Identify bottlenecks

### 7.3 Execution Schedule

| Day | Test Type | Duration |
|-----|-----------|----------|
| Day 1 | Baseline, Light Load | 4 hours |
| Day 2 | Normal Load, Heavy Load | 6 hours |
| Day 3 | Stress Tests | 4 hours |
| Day 4 | Soak Test Start (24h) | - |
| Day 5 | Soak Test End, Spike Tests | 6 hours |
| Day 6 | Scalability Tests | 4 hours |
| Day 7 | Analysis and Reporting | 8 hours |

---

## 8. Acceptance Criteria

### 8.1 Performance Gates

| Metric | Pass | Warning | Fail |
|--------|------|---------|------|
| P99 Response Time | < 500ms | 500-1000ms | > 1000ms |
| Throughput | > 500 RPS | 200-500 RPS | < 200 RPS |
| Error Rate | < 0.1% | 0.1-1% | > 1% |
| CPU Utilization | < 70% | 70-85% | > 85% |
| Memory Utilization | < 75% | 75-90% | > 90% |

### 8.2 Scalability Gates

| Metric | Pass | Fail |
|--------|------|------|
| Scale-up time | < 2 minutes | > 5 minutes |
| Scale-down time | < 5 minutes | > 10 minutes |
| Performance during scale | No degradation | > 20% degradation |
| Max pods reached | >= 10 | < 5 |

### 8.3 Stability Gates

| Metric | Pass | Fail |
|--------|------|------|
| Memory leak | None detected | Leak confirmed |
| Response time drift | < 10% increase | > 20% increase |
| Error accumulation | None | Errors increase |

---

## 9. Reporting

### 9.1 Test Report Contents

1. Executive Summary
2. Test Environment Details
3. Test Execution Summary
4. Performance Results
   - Response time graphs
   - Throughput graphs
   - Resource utilization
5. Comparison to Targets
6. Bottleneck Analysis
7. Recommendations
8. Appendices (raw data)

### 9.2 Report Templates

```
Test Report: [Test ID]
Date: [Date]
Environment: [Configuration]
Duration: [Duration]

Summary:
- Peak RPS achieved: [value]
- P99 response time: [value]
- Error rate: [value]

Results:
[PASS/WARN/FAIL] - Response Time
[PASS/WARN/FAIL] - Throughput
[PASS/WARN/FAIL] - Error Rate
[PASS/WARN/FAIL] - Resource Utilization

Recommendations:
- [Recommendation 1]
- [Recommendation 2]
```

---

## 10. Risk Mitigation

### 10.1 Test Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Environment instability | Medium | High | Pre-test validation, backup environment |
| Data corruption | Low | High | Use isolated test data, backups |
| Tool failure | Low | Medium | Multiple tools available |
| Monitoring gaps | Medium | Medium | Comprehensive monitoring setup |

### 10.2 Rollback Plan

1. Stop load test immediately if:
   - Error rate > 5%
   - System becomes unresponsive
   - Data corruption detected

2. Recovery steps:
   - Stop load generators
   - Verify system stability
   - Clear test data if corrupted
   - Restart services if needed

---

## 11. Approvals

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Performance Lead | | | |
| QA Lead | | | |
| Engineering Lead | | | |
| Product Manager | | | |

---

## Appendix A: k6 Test Script Template

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up
    { duration: '5m', target: 100 },  // Steady state
    { duration: '2m', target: 200 },  // Ramp up
    { duration: '5m', target: 200 },  // Steady state
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<500'], // 99% under 500ms
    http_req_failed: ['rate<0.01'],   // Error rate < 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function() {
  // Health check
  const healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, { 'health check passed': (r) => r.status === 200 });

  // Get work items
  const itemsRes = http.get(`${BASE_URL}/api/workitems`);
  check(itemsRes, {
    'work items status is 200': (r) => r.status === 200,
    'work items response time < 200ms': (r) => r.timings.duration < 200,
  });

  sleep(1);
}
```

## Appendix B: Locust Test Script Template

```python
from locust import HttpUser, task, between

class YawlUser(HttpUser):
    wait_time = between(1, 3)

    @task(3)
    def get_workitems(self):
        self.client.get("/api/workitems")

    @task(2)
    def get_cases(self):
        self.client.get("/api/cases")

    @task(1)
    def launch_workflow(self):
        self.client.post("/api/cases", json={
            "specificationId": "test-spec",
            "data": {"input": "test"}
        })
```
