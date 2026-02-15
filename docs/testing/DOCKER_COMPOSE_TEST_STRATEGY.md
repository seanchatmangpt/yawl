# Docker Compose Testing Strategy
## Production-Hardened YAWL AGI Orchestration Platform

**Version**: 5.2
**Date**: February 2026
**Status**: Fortune 5 Production Standard

---

## Executive Summary

This document defines the comprehensive testing strategy for the YAWL AGI Orchestration Platform using Docker Compose. The strategy ensures production-readiness through multi-layered validation, from unit tests to chaos engineering, all executable in containerized environments.

### Testing Philosophy

Based on **Chicago School TDD** (Detroit School):
- ✅ Test **real integrations**, not mocks
- ✅ **Collaboration tests**, not isolation
- ✅ **End-to-end confidence**, not unit test theater
- ✅ **Production-like environments**, not test doubles

Based on **Toyota Production System** (Jidoka):
- ✅ **Stop the line** when tests fail
- ✅ **Fail fast**, explicit exceptions
- ✅ **Built-in quality**, not inspection
- ✅ **Andon cord** = CI/CD pipeline gates

---

## Table of Contents

1. [Test Pyramid](#test-pyramid)
2. [Docker Compose Test Environments](#docker-compose-test-environments)
3. [Test Categories](#test-categories)
   - 3.1 [Integration Tests](#31-integration-tests)
   - 3.2 [End-to-End Tests](#32-end-to-end-tests)
   - 3.3 [Performance Tests](#33-performance-tests)
   - 3.4 [Security Tests](#34-security-tests)
   - 3.5 [Chaos Engineering](#35-chaos-engineering)
   - 3.6 [Compliance Tests](#36-compliance-tests)
4. [Test Execution](#test-execution)
5. [Continuous Integration](#continuous-integration)
6. [Test Metrics & KPIs](#test-metrics--kpis)
7. [Production Readiness Criteria](#production-readiness-criteria)

---

## Test Pyramid

```
                    ┌─────────────────┐
                    │  Chaos Eng (5%) │  ← Production-like failure injection
                    ├─────────────────┤
                   │  E2E Tests (10%)  │  ← Full workflow scenarios
                   ├───────────────────┤
                  │ Integration (30%)  │  ← Real services, real data
                  ├────────────────────┤
                 │   Component (40%)    │  ← Real classes, real logic
                 ├──────────────────────┤
                │   Unit Tests (15%)     │  ← Core algorithms only
                └────────────────────────┘
```

**Key Difference from Traditional Pyramid**:
- **NO mocks/stubs** - All tests use real services via Docker Compose
- **Integration tests dominate** - Most value in testing real integrations
- **Chaos engineering included** - Production resilience validation

---

## Docker Compose Test Environments

### Environment Matrix

| Environment | Purpose | Containers | Data | Duration |
|-------------|---------|-----------|------|----------|
| `test-unit` | Unit tests | None (in-memory) | Fixtures | < 5 min |
| `test-integration` | Integration tests | Core services only | Test DB | < 15 min |
| `test-e2e` | End-to-end | Full stack | Production-like | < 30 min |
| `test-performance` | Load/stress tests | Full stack + load generators | Large dataset | < 2 hours |
| `test-security` | Security scans | Full stack + scanners | Varied | < 1 hour |
| `test-chaos` | Chaos engineering | Full stack + chaos tools | Production clone | < 4 hours |

### Docker Compose Files

```
docker/
├── docker-compose.yml                  # Production configuration
├── docker-compose.test-integration.yml # Integration test stack
├── docker-compose.test-e2e.yml         # E2E test stack
├── docker-compose.test-performance.yml # Performance test stack
├── docker-compose.test-security.yml    # Security test stack
└── docker-compose.test-chaos.yml       # Chaos engineering stack
```

---

## Test Categories

### 3.1 Integration Tests

**Objective**: Validate real interactions between YAWL components and external services.

#### Test Suites

##### 3.1.1 Database Integration Tests

**Scope**: YAWL Engine ↔ PostgreSQL persistence

**Docker Compose**: `test-integration`

**Services**: `yawl-engine`, `yawl-postgres`

**Test Plan**:

| Test Case | Validation | Acceptance Criteria |
|-----------|------------|---------------------|
| IT-DB-001 | Specification persistence | Spec uploaded, retrieved, matches exactly |
| IT-DB-002 | Case lifecycle persistence | Case created → suspended → resumed → completed |
| IT-DB-003 | Work item state transitions | Enabled → Started → Completed (with data) |
| IT-DB-004 | Transaction rollback | Failed completion → no partial state saved |
| IT-DB-005 | Concurrent access | 100 threads create cases, no conflicts |
| IT-DB-006 | Database failover | PostgreSQL restart mid-transaction → recovery |
| IT-DB-007 | Large workflow graphs | Spec with 1000+ tasks persists correctly |
| IT-DB-008 | Audit log integrity | All events logged, no gaps, correct ordering |

**Docker Compose Snippet**:
```yaml
services:
  yawl-postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: yawl_test
      POSTGRES_USER: yawl_test
      POSTGRES_PASSWORD: test_password
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "yawl_test"]
      interval: 5s
      timeout: 5s
      retries: 5

  yawl-engine:
    build: ../../
    depends_on:
      yawl-postgres:
        condition: service_healthy
    environment:
      YAWL_DB_URL: jdbc:postgresql://yawl-postgres:5432/yawl_test
      YAWL_DB_USER: yawl_test
      YAWL_DB_PASSWORD: test_password
    volumes:
      - ../../test/integration:/tests
    command: >
      bash -c "
        ant -f /opt/yawl/build/build.xml compile &&
        java -cp /opt/yawl/classes:/opt/yawl/lib/* \
             org.junit.runner.JUnitCore \
             org.yawlfoundation.yawl.integration.DatabaseIntegrationTest
      "
```

**Success Criteria**:
- ✅ All 8 test cases pass
- ✅ Zero database errors in logs
- ✅ Transaction commit/rollback works correctly
- ✅ No connection leaks (max connections not exceeded)

---

##### 3.1.2 Cache Integration Tests

**Scope**: YAWL Engine ↔ Redis caching

**Docker Compose**: `test-integration`

**Services**: `yawl-engine`, `yawl-redis`

**Test Plan**:

| Test Case | Validation | Acceptance Criteria |
|-----------|------------|---------------------|
| IT-CACHE-001 | Work item caching | Hot items retrieved from cache (< 5ms) |
| IT-CACHE-002 | Cache invalidation | Completed item removed from cache |
| IT-CACHE-003 | Distributed locking | 100 threads acquire lock, no race conditions |
| IT-CACHE-004 | Cache eviction | LRU eviction works under memory pressure |
| IT-CACHE-005 | Redis failover | Redis restart → cache rebuilds correctly |
| IT-CACHE-006 | Session state | User sessions persist across engine restarts |

**Success Criteria**:
- ✅ Cache hit rate > 80% for active work items
- ✅ Lock acquisition latency < 10ms (p99)
- ✅ Zero race conditions under high concurrency
- ✅ Graceful degradation if Redis unavailable

---

##### 3.1.3 MCP Integration Tests

**Scope**: MCP Server ↔ YAWL Engine ↔ Vector DB

**Docker Compose**: `test-integration`

**Services**: `yawl-mcp-server`, `yawl-engine`, `yawl-vector-db`

**Test Plan**:

| Test Case | Validation | Acceptance Criteria |
|-----------|------------|---------------------|
| IT-MCP-001 | Prompt generation | Generate prompt for work item, includes context |
| IT-MCP-002 | Capability matching | Match agents to tasks by embedding similarity |
| IT-MCP-003 | RAG retrieval | Retrieve relevant past workflows from vector DB |
| IT-MCP-004 | Tool invocation | Agent invokes launchWorkflow tool → case created |
| IT-MCP-005 | Resource retrieval | Agent fetches spec via MCP resource URI |
| IT-MCP-006 | Context persistence | Agent context saved to vector DB, retrievable |
| IT-MCP-007 | Prompt optimization | Prompts formatted correctly for Claude/GPT-4 |

**Success Criteria**:
- ✅ Prompts generated in < 100ms
- ✅ Capability matching accuracy > 90%
- ✅ RAG retrieval latency < 200ms
- ✅ Tool invocations execute successfully

---

##### 3.1.4 A2A Integration Tests

**Scope**: A2A Server ↔ YAWL Engine ↔ Kafka

**Docker Compose**: `test-integration`

**Services**: `yawl-a2a-server`, `yawl-engine`, `yawl-kafka`, `yawl-zookeeper`

**Test Plan**:

| Test Case | Validation | Acceptance Criteria |
|-----------|------------|---------------------|
| IT-A2A-001 | Agent registration | Agent registers, appears in directory |
| IT-A2A-002 | Task negotiation | Contract Net protocol completes successfully |
| IT-A2A-003 | Consensus voting | Raft consensus reaches agreement |
| IT-A2A-004 | Message routing | Messages routed to correct agents |
| IT-A2A-005 | Kafka integration | Events published/consumed correctly |
| IT-A2A-006 | Agent failover | Failed agent detected, task reassigned |
| IT-A2A-007 | Swarm coordination | 10 agents coordinate on workflow |

**Success Criteria**:
- ✅ Agent registration latency < 50ms
- ✅ Consensus reached in < 500ms (5 agents)
- ✅ Message delivery 100% reliable
- ✅ Failover detection in < 5 seconds

---

##### 3.1.5 Event Notification Integration Tests

**Scope**: Event Notifier ↔ YAWL Engine ↔ Kafka

**Docker Compose**: `test-integration`

**Services**: `yawl-event-notifier`, `yawl-engine`, `yawl-kafka`

**Test Plan**:

| Test Case | Validation | Acceptance Criteria |
|-----------|------------|---------------------|
| IT-EVENT-001 | WebSocket streaming | Events streamed to WebSocket clients |
| IT-EVENT-002 | SSE streaming | Events streamed via Server-Sent Events |
| IT-EVENT-003 | HTTP polling | Clients poll for events, receive updates |
| IT-EVENT-004 | Event ordering | Events delivered in correct order |
| IT-EVENT-005 | Fan-out | 1000 clients receive same event |
| IT-EVENT-006 | Client reconnection | Clients reconnect after disconnect, no loss |
| IT-EVENT-007 | Backpressure | Slow clients don't block fast clients |

**Success Criteria**:
- ✅ Event delivery latency < 100ms (p99)
- ✅ Support 1000+ concurrent connections
- ✅ Zero message loss on reconnection
- ✅ Backpressure handled gracefully

---

### 3.2 End-to-End Tests

**Objective**: Validate complete business scenarios from agent request to workflow completion.

#### Test Suites

##### 3.2.1 Order Fulfillment Workflow

**Scope**: Complete order fulfillment with AI agents

**Docker Compose**: `test-e2e`

**Services**: Full stack (all containers)

**Test Scenario**:

```
1. Agent A (via MCP) requests available workflows
2. MCP Server returns order fulfillment spec
3. Agent A launches new case with order data
4. YAWL Engine creates case, enables InventoryCheck task
5. Event Notifier broadcasts WORKITEM_ENABLED event
6. Agent B (inventory specialist) receives event via WebSocket
7. Agent B starts work item, analyzes inventory
8. Agent B completes work item with inventory status
9. Engine enables parallel tasks: ProcessPayment, ArrangeShipping
10. Agent C (payment) and Agent D (shipping) execute in parallel
11. Both complete, AND-join synchronizes
12. Exception: Payment fails
13. Worklet Service substitutes retry workflow
14. Agent C retries payment, succeeds
15. Engine enables ShipOrder task
16. Swarm of agents (A2A) vote on shipping method via consensus
17. Consensus reached, optimal shipping selected
18. Agent E executes shipping
19. Case completes, final event broadcast
20. Process mining DB logs complete trace
```

**Test Plan**:

| Test Case | Validation | Acceptance Criteria |
|-----------|------------|---------------------|
| E2E-001 | Happy path | Order completes end-to-end, all agents succeed |
| E2E-002 | Payment exception | Worklet substitution handles payment failure |
| E2E-003 | Inventory shortage | Correct exception handling, human escalation |
| E2E-004 | Parallel execution | Payment & shipping truly parallel (timing) |
| E2E-005 | Consensus voting | Agents reach consensus on shipping method |
| E2E-006 | Event delivery | All agents receive all relevant events |
| E2E-007 | Data flow | Order data flows correctly through workflow |
| E2E-008 | Audit trail | Complete event log in process mining DB |
| E2E-009 | Agent failover | Agent failure mid-task → reassignment works |
| E2E-010 | Case cancellation | Cancel mid-flight → all agents notified |

**Docker Compose Snippet**:
```yaml
services:
  # ... all services from docker-compose.yml ...

  e2e-test-orchestrator:
    build:
      context: ../../
      dockerfile: test/e2e/Dockerfile
    depends_on:
      yawl-engine:
        condition: service_healthy
      yawl-mcp-server:
        condition: service_healthy
      yawl-a2a-server:
        condition: service_healthy
    environment:
      YAWL_ENGINE_URL: http://yawl-engine:8080
      MCP_SERVER_URL: http://yawl-mcp-server:8081
      A2A_SERVER_URL: http://yawl-a2a-server:8082
    volumes:
      - ../../test/e2e:/tests
    command: >
      python3 /tests/run_e2e_tests.py \
              --scenario order_fulfillment \
              --agents 5 \
              --iterations 10 \
              --report /tests/reports/e2e_results.json
```

**Success Criteria**:
- ✅ All 10 test cases pass
- ✅ End-to-end latency < 5 seconds
- ✅ Zero data corruption
- ✅ Complete audit trail captured

---

### 3.3 Performance Tests

**Objective**: Validate system performance under load, identify bottlenecks, ensure scalability.

#### Test Suites

##### 3.3.1 Load Testing

**Scope**: Sustained load with realistic usage patterns

**Docker Compose**: `test-performance`

**Services**: Full stack + load generators

**Test Plan**:

| Test Case | Load Profile | Acceptance Criteria |
|-----------|--------------|---------------------|
| PERF-LOAD-001 | 100 concurrent cases | p99 latency < 2s, success rate 100% |
| PERF-LOAD-002 | 1000 concurrent agents | Task allocation latency < 100ms |
| PERF-LOAD-003 | 10,000 work items/min | Throughput sustained for 1 hour |
| PERF-LOAD-004 | 100 MB/s event streaming | Event delivery latency < 200ms |
| PERF-LOAD-005 | Database contention | 500 concurrent DB writes, no deadlocks |
| PERF-LOAD-006 | Cache hit rate | > 80% cache hit under sustained load |

**Load Generator Setup**:
```yaml
services:
  load-generator:
    image: grafana/k6:latest
    volumes:
      - ../../test/performance/load_tests:/scripts
    environment:
      YAWL_ENGINE_URL: http://yawl-engine:8080
    command: >
      run /scripts/load_test.js \
          --vus 1000 \
          --duration 1h \
          --out influxdb=http://influxdb:8086/k6
```

**k6 Load Test Script** (`load_test.js`):
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '5m', target: 100 },  // Ramp-up
    { duration: '50m', target: 1000 }, // Sustained load
    { duration: '5m', target: 0 },     // Ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(99)<2000'], // 99% < 2s
    'http_req_failed': ['rate<0.01'],    // < 1% errors
  },
};

export default function () {
  // Launch workflow case
  let launchRes = http.post(
    `${__ENV.YAWL_ENGINE_URL}/yawl/ib/launchCase`,
    JSON.stringify({
      specificationID: 'OrderFulfillment',
      caseData: generateOrderData(),
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(launchRes, {
    'case launched': (r) => r.status === 200,
    'caseID returned': (r) => r.json('caseID') !== null,
  });

  let caseID = launchRes.json('caseID');

  // Query work items
  let workItemsRes = http.get(
    `${__ENV.YAWL_ENGINE_URL}/yawl/ib/getWorkItems?caseID=${caseID}`
  );

  check(workItemsRes, {
    'work items retrieved': (r) => r.status === 200,
  });

  sleep(1);
}

function generateOrderData() {
  return {
    orderID: `ORD-${Math.random().toString(36).substr(2, 9)}`,
    items: [
      { sku: 'ABC123', quantity: Math.floor(Math.random() * 10) + 1 },
    ],
    customer: { id: `CUST-${Math.floor(Math.random() * 10000)}` },
  };
}
```

**Success Criteria**:
- ✅ All load tests pass thresholds
- ✅ No resource exhaustion (CPU, memory, connections)
- ✅ Linear scalability up to 1000 agents
- ✅ Database connection pool stable

---

##### 3.3.2 Stress Testing

**Scope**: Push system beyond limits to find breaking point

**Test Plan**:

| Test Case | Stress Condition | Acceptance Criteria |
|-----------|-----------------|---------------------|
| PERF-STRESS-001 | 10,000 concurrent cases | Identify max throughput |
| PERF-STRESS-002 | 1M work items in DB | Query performance degradation < 20% |
| PERF-STRESS-003 | 100,000 agents | Agent registry handles load |
| PERF-STRESS-004 | Network saturation | Event streaming degrades gracefully |
| PERF-STRESS-005 | Memory exhaustion | OOM handling, no crashes |

**Success Criteria**:
- ✅ Breaking point identified and documented
- ✅ Graceful degradation (no crashes)
- ✅ Recovery after stress removal
- ✅ Clear scaling guidance produced

---

##### 3.3.3 Soak Testing

**Scope**: Run at moderate load for extended period to detect leaks

**Test Plan**:

| Test Case | Duration | Acceptance Criteria |
|-----------|----------|---------------------|
| PERF-SOAK-001 | 48 hours | No memory leaks (heap stable) |
| PERF-SOAK-002 | 48 hours | No connection leaks (pool stable) |
| PERF-SOAK-003 | 48 hours | No file descriptor leaks |
| PERF-SOAK-004 | 48 hours | No thread leaks (thread count stable) |
| PERF-SOAK-005 | 48 hours | Performance doesn't degrade over time |

**Monitoring**:
```yaml
services:
  prometheus:
    image: prom/prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=7d'

  grafana:
    image: grafana/grafana
    environment:
      GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH: /var/lib/grafana/dashboards/soak-test.json
    volumes:
      - ./dashboards:/var/lib/grafana/dashboards
```

**Success Criteria**:
- ✅ Memory usage stable (< 5% increase over 48h)
- ✅ Connection pool size stable
- ✅ Thread count stable
- ✅ Latency remains constant

---

### 3.4 Security Tests

**Objective**: Validate security controls, identify vulnerabilities, ensure compliance.

#### Test Suites

##### 3.4.1 Authentication & Authorization Tests

**Test Plan**:

| Test Case | Attack Vector | Acceptance Criteria |
|-----------|---------------|---------------------|
| SEC-AUTH-001 | Unauthenticated access | All endpoints require auth |
| SEC-AUTH-002 | Weak credentials | Strong password policy enforced |
| SEC-AUTH-003 | Token expiration | JWT tokens expire after 1 hour |
| SEC-AUTH-004 | Role-based access | Agents can't access admin endpoints |
| SEC-AUTH-005 | Session hijacking | Session tokens rotated, secure cookies |

---

##### 3.4.2 Input Validation Tests

**Test Plan**:

| Test Case | Attack Vector | Acceptance Criteria |
|-----------|---------------|---------------------|
| SEC-INPUT-001 | XML injection | XML parser rejects malicious payloads |
| SEC-INPUT-002 | XPath injection | XPath evaluator sanitizes inputs |
| SEC-INPUT-003 | SQL injection | Parameterized queries, no injection |
| SEC-INPUT-004 | XSS | HTML sanitization in web console |
| SEC-INPUT-005 | File upload attacks | File type validation, size limits |

---

##### 3.4.3 Dependency Scanning

**Docker Compose**:
```yaml
services:
  trivy-scanner:
    image: aquasec/trivy:latest
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: >
      image --severity HIGH,CRITICAL \
            --exit-code 1 \
            yawl-engine:latest
```

**Success Criteria**:
- ✅ Zero HIGH/CRITICAL vulnerabilities
- ✅ All dependencies up-to-date
- ✅ SBOM (Software Bill of Materials) generated

---

### 3.5 Chaos Engineering

**Objective**: Validate resilience to production failures.

#### Test Suites

##### 3.5.1 Network Chaos

**Tool**: Pumba (container chaos tool)

**Docker Compose**:
```yaml
services:
  pumba:
    image: gaiaadm/pumba
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: >
      pumba netem \
            --duration 5m \
            --tc-image gaiadocker/iproute2 \
            delay --time 1000 \
            re2:yawl-.*
```

**Test Plan**:

| Test Case | Chaos Injection | Acceptance Criteria |
|-----------|----------------|---------------------|
| CHAOS-NET-001 | 1000ms latency | System remains functional, degrades gracefully |
| CHAOS-NET-002 | 10% packet loss | Retries successful, no data loss |
| CHAOS-NET-003 | Network partition | Split-brain prevented, quorum maintained |
| CHAOS-NET-004 | DNS failure | Service discovery fails over to IP |

---

##### 3.5.2 Container Chaos

**Test Plan**:

| Test Case | Chaos Injection | Acceptance Criteria |
|-----------|----------------|---------------------|
| CHAOS-CONT-001 | Kill yawl-engine | Restart recovery, state restored from DB |
| CHAOS-CONT-002 | Kill yawl-postgres | Connection pool retries, failover if replica |
| CHAOS-CONT-003 | Kill yawl-redis | Cache rebuilds, no data loss |
| CHAOS-CONT-004 | Kill yawl-kafka | Event buffer holds, delivery resumes |

**Pumba Command**:
```bash
pumba kill --signal SIGKILL re2:yawl-engine
```

**Success Criteria**:
- ✅ Service restarts automatically (Docker restart policy)
- ✅ State recovered from persistent storage
- ✅ No in-flight transactions lost
- ✅ Clients receive error, retry successfully

---

##### 3.5.3 Resource Chaos

**Test Plan**:

| Test Case | Chaos Injection | Acceptance Criteria |
|-----------|----------------|---------------------|
| CHAOS-RES-001 | CPU throttle to 10% | Degraded performance, no crashes |
| CHAOS-RES-002 | Memory limit to 512MB | OOM killer doesn't trigger, GC works |
| CHAOS-RES-003 | Disk I/O limit | Throughput reduced, no corruption |

**Pumba Command**:
```bash
pumba stress --stress-cpu 10 yawl-engine
```

---

### 3.6 Compliance Tests

**Objective**: Validate regulatory compliance (GDPR, SOC2, HIPAA if applicable).

**Test Plan**:

| Test Case | Requirement | Acceptance Criteria |
|-----------|-------------|---------------------|
| COMP-001 | Data encryption at rest | PostgreSQL TDE enabled |
| COMP-002 | Data encryption in transit | TLS 1.3 enforced |
| COMP-003 | Audit logging | All access logged, tamper-proof |
| COMP-004 | Data retention | PII purged after retention period |
| COMP-005 | Right to erasure | GDPR deletion requests honored |

---

## Test Execution

### Commands

```bash
# Integration tests
docker-compose -f docker/docker-compose.test-integration.yml up --abort-on-container-exit

# E2E tests
docker-compose -f docker/docker-compose.test-e2e.yml up --abort-on-container-exit

# Performance tests
docker-compose -f docker/docker-compose.test-performance.yml up

# Security tests
docker-compose -f docker/docker-compose.test-security.yml up --abort-on-container-exit

# Chaos tests
docker-compose -f docker/docker-compose.test-chaos.yml up
```

### CI/CD Integration

**GitHub Actions** (`.github/workflows/test-suite.yml`):
```yaml
name: Production Test Suite

on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run integration tests
        run: |
          docker-compose -f docker/docker-compose.test-integration.yml up \
            --abort-on-container-exit \
            --exit-code-from integration-test-runner

  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run E2E tests
        run: |
          docker-compose -f docker/docker-compose.test-e2e.yml up \
            --abort-on-container-exit \
            --exit-code-from e2e-test-orchestrator

  performance-tests:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      - name: Run performance tests
        run: |
          docker-compose -f docker/docker-compose.test-performance.yml up -d
          docker-compose logs -f load-generator
          docker-compose down
```

---

## Test Metrics & KPIs

| Metric | Target | Measurement |
|--------|--------|-------------|
| Test Coverage | > 80% (integration + E2E) | JaCoCo, SonarQube |
| Test Pass Rate | 100% | CI/CD pipeline |
| Integration Test Duration | < 15 minutes | CI/CD pipeline |
| E2E Test Duration | < 30 minutes | CI/CD pipeline |
| Performance Regression | < 5% degradation | K6 trend analysis |
| Security Vulnerabilities | 0 HIGH/CRITICAL | Trivy scans |
| Chaos Recovery Time | < 2 minutes | Pumba experiments |

---

## Production Readiness Criteria

### Checklist

- [ ] ✅ All integration tests pass (100%)
- [ ] ✅ All E2E tests pass (100%)
- [ ] ✅ Performance tests meet SLA (p99 < 2s)
- [ ] ✅ Zero HIGH/CRITICAL security vulnerabilities
- [ ] ✅ Chaos tests pass (resilience validated)
- [ ] ✅ Soak test stable for 48 hours
- [ ] ✅ Monitoring and alerting configured
- [ ] ✅ Disaster recovery tested
- [ ] ✅ Compliance requirements met
- [ ] ✅ Documentation complete

### Sign-Off

**Engineering**: ___________________
**Security**: ___________________
**Compliance**: ___________________
**Operations**: ___________________

---

**Last Updated**: February 2026
**Next Review**: Quarterly
