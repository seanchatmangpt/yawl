# YAWL v6 Validation System

A comprehensive validation suite for all 43+ YAWL workflow control-flow patterns, designed by Dr. Wil van der Aalst. This system validates pattern correctness through Docker Compose shell scripts without writing any Java code.

## Table of Contents

- [Quick Start](#quick-start)
- [Architecture Overview](#architecture-overview)
- [Business Scenarios](#business-scenarios)
- [Script Reference](#script-reference)
- [Usage Examples](#usage-examples)
- [Output Reports](#output-reports)
- [API Endpoints](#api-endpoints)
- [Docker Services](#docker-services)
- [Error Handling](#error-handling)
- [Performance Metrics](#performance-metrics)
- [Best Practices](#best-practices)
- [Integration Notes](#integration-notes)

---

## Quick Start

### Demo Mode (For Dr. van der Aalst)

```bash
# Quick demonstration of all business scenarios
cd scripts/validation
./business-scenarios/demo-for-van-der-aalst.sh

# Run all scenarios with reduced cases
./business-scenarios/run-all-scenarios.sh --demo
```

### Full Validation

```bash
# Validate all patterns
./patterns/validate-all-patterns.sh

# Validate specific category
./patterns/validate-all-patterns.sh --category basic

# Validate single pattern
./patterns/validate-all-patterns.sh --pattern WCP-1

# Run full master validation (parallel)
bash validate-all.sh

# Run quick validation (skip chaos/stress tests)
bash validate-all.sh --quick
```

### 80/20 Demo Mode

```bash
# 80% coverage with 20% of test cases - fast feedback
bash validate-80-20.sh
```

---

## Architecture Overview

### Directory Structure

```
scripts/validation/
├── patterns/              # Pattern validation scripts
│   ├── stateful/          # Stateful engine tests
│   ├── stateless/         # Stateless engine tests
│   ├── validate-*.sh      # Category-specific validators
│   ├── engine-health.sh   # Engine readiness check
│   └── pattern-executor.sh # Single pattern executor
├── business-scenarios/    # Real-world business workflows
│   ├── demo-for-van-der-aalst.sh
│   ├── run-all-scenarios.sh
│   └── scenario-*.sh      # Individual scenarios
├── a2a/                   # Agent-to-Agent protocol tests
│   ├── tests/             # A2A test suites
│   ├── lib/               # A2A test utilities
│   └── validate-*.sh      # A2A validators
├── mcp/                   # Model Context Protocol tests
│   ├── tests/             # MCP test suites
│   ├── lib/               # MCP test utilities
│   └── validate-*.sh      # MCP validators
├── docker/                # Docker orchestration
│   ├── start-stateful.sh  # Start stateful engine + PostgreSQL
│   ├── start-stateless.sh # Start stateless engine
│   └── stop-all.sh        # Stop all containers
├── lib/                   # Shared libraries
│   └── output-aggregation.sh # JSON/JUnit output aggregation
├── reports/               # Generated reports
├── validate-all.sh        # Master orchestrator (parallel)
├── validate-dx.sh         # DX workflow validation
├── validate-observatory.sh # Observatory fact validation
├── validate-release.sh    # Release readiness check
└── validate-chaos*.sh     # Chaos engineering tests
```

### Pattern Categories (43+ Patterns)

| Category | Patterns | Count | Business Value |
|----------|----------|-------|----------------|
| **Basic Control Flow** | WCP 1-5 | 5 | Foundation: Sequence, Parallel Split, Synchronization, Exclusive Choice, Simple Merge |
| **Advanced Branching** | WCP 6-11 | 6 | Complex decisions: Multi-Choice, Structured Synchronizing Merge, Structured Loop |
| **Multi-Instance** | WCP 12-17, 24, 26-27 | 8 | Parallel processing without synchronization |
| **State-Based** | WCP 18-21, 32-35 | 8 | State management: Deferred Choice, Interleaved Routing, Milestone |
| **Cancellation** | WCP 22-23, 25, 29-31 | 6 | Error handling: Cancel Activity, Cancel Case, Cancel Region |
| **Extended** | WCP 41-50 | 10 | Enterprise: Saga, Circuit Breaker, Two-Phase Commit, CQRS |
| **Event-Driven** | WCP 37-40, 51-59 | 13 | Async: Event Gateway, Message Start/Intermediate/End Events |
| **AI/ML** | WCP 60-68 | 9 | AI integration: ML Model, Human-AI Handoff, Confidence Threshold |

### Validation Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Master Orchestrator                       │
│                    (validate-all.sh)                         │
├─────────────┬─────────────┬─────────────┬──────────────────┤
│    Core     │     A2A     │     MCP     │     Chaos        │
├─────────────┼─────────────┼─────────────┼──────────────────┤
│ • docs      │ • protocol  │ • stdio     │ • network        │
│ • observ    │ • auth      │ • tools     │ • CPU stress     │
│ • perf      │ • skills    │ • schema    │ • memory stress  │
│ • release   │ • handoff   │             │ • resilience     │
│             │ • schema    │             │                  │
│             │ • rates     │             │                  │
├─────────────┴─────────────┴─────────────┴──────────────────┤
│              Output Aggregation (JSON + JUnit)               │
└─────────────────────────────────────────────────────────────┘
```

---

## Business Scenarios

### Scenario Coverage Matrix

| Scenario | Basic | Branch | Multi-I | State | Cancel | Extended | Event | AI/ML | Total |
|----------|:-----:|:------:|:-------:|:-----:|:------:|:--------:|:-----:|:-----:|:-----:|
| Order Fulfillment | 5 | 2 | - | 2 | 1 | - | - | - | 10 |
| Insurance Claim | 2 | 5 | - | 2 | - | - | 4 | - | 13 |
| Mortgage Loan | 1 | 2 | 6 | - | - | 4 | - | - | 13 |
| Supply Chain | 2 | - | - | - | - | 6 | 6 | - | 14 |
| Healthcare | - | - | - | 4 | 2 | - | - | 5 | 11 |
| **Total Unique** | 8 | 7 | 6 | 6 | 3 | 8 | 8 | 5 | **43+** |

### Business Scenarios Detail

| # | Scenario | Patterns | Business Value | Key Features |
|---|----------|----------|----------------|--------------|
| 1 | **Order Fulfillment** | WCP 1-5, 10-11, 20-21 | Order-to-delivery automation with real-time tracking | Sequence, Parallel Split, Cancel, Loop |
| 2 | **Insurance Claim** | WCP 4-9, 18-19, 37-40 | 80% reduction in manual claims review | Multi-Choice, Deferred Choice, Milestone, Triggers |
| 3 | **Mortgage Loan** | WCP 6-8, 12-17, 41-44 | Compliance automation, streamlined processing | Multi-Instance, Saga, Critical Section |
| 4 | **Supply Chain** | WCP 2-3, 45-50, 51-59 | Resilient supply chain with failover | Circuit Breaker, Two-Phase Commit, Event Gateway, CQRS |
| 5 | **Healthcare** | WCP 18-21, 28-31, 60-68 | AI-assisted diagnosis, improved outcomes | Deferred Choice, ML Model, Human-AI Handoff, Confidence Threshold |

---

## Script Reference

### Master Validation Scripts

| Script | Purpose | Options |
|--------|---------|---------|
| `validate-all.sh` | Master orchestrator with parallel execution | `--parallel`, `--sequential`, `--fail-fast`, `--no-fail-fast`, `--quick`, `--a2a-only`, `--mcp-only`, `--format {json\|junit\|both}` |
| `validate-80-20.sh` | Fast feedback with 80% coverage, 20% cases | `--cases N`, `--timeout SECONDS` |
| `validate-dx.sh` | DX workflow validation (compile, test, verify) | `--module NAME`, `--all` |
| `validate-observatory.sh` | Observatory fact freshness validation | `--refresh`, `--verify-hashes` |
| `validate-release.sh` | Complete release readiness check | `--version VERSION`, `--skip-tests` |
| `validate-documentation.sh` | Package-info, markdown links, XSD schemas | `--fix-links` |
| `validate-performance-baselines.sh` | Build time, observatory runtime | `--baseline-file PATH` |

### Pattern Validation Scripts

| Script | Purpose | Patterns |
|--------|---------|----------|
| `patterns/validate-all-patterns.sh` | Main orchestrator for all patterns | All 43+ |
| `patterns/validate-basic.sh` | Basic control flow | WCP 1-5 |
| `patterns/validate-branching.sh` | Advanced branching | WCP 6-11 |
| `patterns/validate-multiinstance.sh` | Multi-instance patterns | WCP 12-17, 24, 26-27 |
| `patterns/validate-statebased.sh` | State-based patterns | WCP 18-21, 32-35 |
| `patterns/validate-cancellation.sh` | Cancellation patterns | WCP 22-23, 25, 29-31 |
| `patterns/validate-extended.sh` | Enterprise patterns | WCP 41-50 |
| `patterns/validate-eventdriven.sh` | Event-driven patterns | WCP 37-40, 51-59 |
| `patterns/validate-aiml.sh` | AI/ML integration patterns | WCP 60-68 |

### Utility Scripts

| Script | Purpose |
|--------|---------|
| `patterns/pattern-executor.sh` | Execute single pattern validation |
| `patterns/engine-health.sh` | Check engine readiness with timeout |
| `patterns/yaml-to-xml.sh` | Convert YAML spec to YAWL XML |
| `patterns/generate-report.sh` | Generate JSON/HTML reports |
| `lib/output-aggregation.sh` | JSON/JUnit output aggregation library |

### Business Scenario Scripts

| Script | Purpose | Default Cases |
|--------|---------|---------------|
| `business-scenarios/run-all-scenarios.sh` | Run all 5 business scenarios | Auto |
| `business-scenarios/demo-for-van-der-aalst.sh` | Quick demo for demonstrations | 10-20 |
| `business-scenarios/scenario-1-order-fulfillment.sh` | E-commerce order processing | 100 |
| `business-scenarios/scenario-2-insurance-claim.sh` | Insurance claim workflow | 50 |
| `business-scenarios/scenario-3-mortgage-loan.sh` | Mortgage application | 25 |
| `business-scenarios/scenario-4-supply-chain.sh` | Supply chain procurement | 30 |
| `business-scenarios/scenario-5-healthcare.sh` | Patient care pathway | 40 |

### A2A (Agent-to-Agent) Validation Scripts

| Script | Purpose |
|--------|---------|
| `a2a/validate-a2a-compliance.sh` | Full A2A protocol compliance |
| `a2a/validate-a2a-rate-limits.sh` | Rate limiting and throttling |
| `a2a/validate-agent-card-schema.sh` | Agent card schema validation |
| `a2a/tests/test-protocol.sh` | Protocol message validation |
| `a2a/tests/test-authentication-providers.sh` | Auth provider tests (SPIFFE, JWT, API Key, OAuth2) |
| `a2a/tests/test-skills-validation.sh` | Skill definition validation |
| `a2a/tests/test-skills.sh` | Skill execution tests |
| `a2a/tests/test-handoff-protocol.sh` | JWT handoff token tests |
| `a2a/tests/test-handoff.sh` | Handoff execution tests |
| `a2a/tests/test-performance-benchmark.sh` | A2A performance benchmarks |

### MCP (Model Context Protocol) Validation Scripts

| Script | Purpose |
|--------|---------|
| `mcp/validate-mcp-compliance.sh` | Full MCP protocol compliance |
| `mcp/validate-mcp-schema.sh` | MCP schema validation |
| `mcp/validate-mcp-stdio.sh` | STDIO transport and protocol handshake |
| `mcp/tests/test-tools.sh` | Tool registration, invocation, responses |
| `mcp/tests/test-protocol-handshake.sh` | Protocol handshake tests |

### Docker Orchestration Scripts

| Script | Purpose |
|--------|---------|
| `docker/start-stateful.sh` | Start stateful YAWL engine with PostgreSQL |
| `docker/start-stateless.sh` | Start stateless YAWL engine |
| `docker/stop-all.sh` | Stop all containers |

### Chaos Engineering Scripts

| Script | Purpose |
|--------|---------|
| `validate-chaos.sh` | Chaotic failure injection and recovery |
| `validate-chaos-stress.sh` | Combined chaos + stress testing |
| `validate-concurrent-stress.sh` | Concurrent load testing with virtual threads |

---

## Usage Examples

### Basic Pattern Validation

```bash
# Check engine health before validation
./patterns/engine-health.sh --timeout 60

# Validate all basic patterns
./patterns/validate-basic.sh

# Validate with custom case count
./patterns/validate-basic.sh --cases 50

# Validate specific category
./patterns/validate-all-patterns.sh --category branching

# Validate single pattern
./patterns/validate-all-patterns.sh --pattern WCP-1
```

### Business Scenario Validation

```bash
# Run all scenarios (full validation)
./business-scenarios/run-all-scenarios.sh

# Run with specific case count
./business-scenarios/run-all-scenarios.sh --cases 100

# Demo mode (reduced cases for quick verification)
./business-scenarios/run-all-scenarios.sh --demo

# Run single scenario
./business-scenarios/scenario-1-order-fulfillment.sh --cases 50
```

### Pattern-Specific Validation

```bash
# Convert YAML spec to YAWL XML
./patterns/yaml-to-xml.sh patterns/controlflow/wcp-1-sequence.yaml

# Execute single pattern with verbose output
./patterns/pattern-executor.sh WCP-1 patterns/controlflow/wcp-1-sequence.yaml

# Generate comprehensive report
./patterns/generate-report.sh --format html --output reports/custom-report.html
```

### Master Validation (Parallel)

```bash
# Run all validations in parallel (default)
bash validate-all.sh

# Run sequentially with fail-fast
bash validate-all.sh --sequential --fail-fast

# JSON output only for CI
bash validate-all.sh --format json --no-fail-fast

# Quick validation (skip chaos/stress)
bash validate-all.sh --quick

# A2A only during A2A development
bash validate-all.sh --a2a-only

# MCP only during MCP development
bash validate-all.sh --mcp-only

# Custom output directory
bash validate-all.sh --output-dir /tmp/validation-results
```

### Docker Services

```bash
# Start stateful engine (with PostgreSQL persistence)
./docker/start-stateful.sh

# Start stateless engine (in-memory)
./docker/start-stateless.sh

# Stop all containers
./docker/stop-all.sh
```

### 80/20 Fast Feedback

```bash
# Quick validation with 80% coverage
bash validate-80-20.sh

# With custom timeout
bash validate-80-20.sh --timeout 120
```

---

## Output Reports

### Report Locations

| Report | Location | Format |
|--------|----------|--------|
| Pattern Validation JSON | `reports/pattern-validation-report.json` | JSON |
| Pattern Validation HTML | `reports/pattern-validation-report.html` | HTML |
| Business Scenario JSON | `reports/business-scenario-validation-report.json` | JSON |
| Master Validation JSON | `docs/validation/validation-report.json` | JSON |
| Master Validation JUnit | `docs/validation/validation-report.xml` | XML |
| Master Validation Summary | `docs/validation/validation-report.md` | Markdown |

### Sample JSON Report

```json
{
  "timestamp": "2026-02-20T10:40:00Z",
  "engine_version": "6.0.0-alpha",
  "suite": "master-validation",
  "total": 15,
  "passed": 14,
  "failed": 0,
  "skipped": 1,
  "warnings": 2,
  "results": [
    {
      "name": "documentation",
      "status": "PASS",
      "message": "All checks passed",
      "duration_ms": 5432
    }
  ],
  "summary": {
    "total_patterns": 43,
    "passed": 43,
    "failed": 0,
    "success_rate": 100.0
  },
  "business_scenarios": {
    "order_fulfillment": {
      "patterns_validated": ["WCP-1", "WCP-2", "WCP-3"],
      "cases_completed": 100,
      "status": "passed"
    }
  }
}
```

### Sample JUnit XML Report

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="master-validation" tests="15" failures="0" skipped="1" time="45.2" timestamp="2026-02-20T10:40:00Z">
    <properties>
      <property name="passed" value="14"/>
      <property name="warnings" value="2"/>
      <property name="commit" value="fd788c7"/>
    </properties>
    <testcase name="documentation" classname="master-validation" time="5.432"/>
    <testcase name="observatory" classname="master-validation" time="3.210"/>
  </testsuite>
</testsuites>
```

---

## API Endpoints

### Engine Management

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/actuator/health/liveness` | GET | Health check (liveness probe) |
| `/actuator/health/readiness` | GET | Readiness probe |
| `/yawl/ib?action=connect` | POST | Authentication, returns connectionID |

### Workflow Operations (Interface B)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/yawl/ia?action=addSpecification` | POST | Upload workflow specification |
| `/yawl/ib?action=launchCase` | POST | Launch new case instance |
| `/yawl/ib?action=getCaseState` | POST | Get current case state |
| `/yawl/ib?action=getWorkItems` | POST | Get available work items |
| `/yawl/ib?action=checkout` | POST | Check out work item for processing |
| `/yawl/ib?action=checkin` | POST | Complete and check in work item |
| `/yawl/ib?action=cancelCase` | POST | Cancel running case |
| `/yawl/ib?action=disconnect` | POST | End session |

### MCP/A2A Endpoints

| Endpoint | Port | Purpose |
|----------|------|---------|
| `/mcp/v1/*` | 18081 | MCP protocol endpoints |
| `/a2a/v1/*` | 18082 | A2A protocol endpoints |
| `/agent/card` | 18082 | Agent discovery card |

---

## Docker Services

### Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose                            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │  yawl-engine    │  │  yawl-engine    │                   │
│  │  (stateful)     │  │  (stateless)    │                   │
│  │  :8080 API      │  │  :8180 API      │                   │
│  │  :9090 mgmt     │  │  :9190 mgmt     │                   │
│  └────────┬────────┘  └─────────────────┘                   │
│           │                                                  │
│  ┌────────▼────────┐                                        │
│  │   PostgreSQL    │                                        │
│  │   :5432         │                                        │
│  └─────────────────┘                                        │
│                                                              │
│  ┌─────────────────┐                                        │
│  │  yawl-mcp-a2a   │                                        │
│  │  :18080 mgmt    │                                        │
│  │  :18081 MCP     │                                        │
│  │  :18082 A2A     │                                        │
│  └─────────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

### Service Ports

| Service | Port | Purpose | Profile |
|---------|------|---------|---------|
| `yawl-engine` | 8080 | Main workflow engine (stateful) | production |
| `yawl-engine` | 9090 | Management/Actuator endpoint | production |
| `yawl-engine-prod` | 8080 | Production engine with PostgreSQL | production |
| `postgres` | 5432 | PostgreSQL database | production |
| `yawl-engine-stateless` | 8180 | Stateless engine | stateless |
| `yawl-mcp-a2a` | 18080 | Spring Boot management | default |
| `yawl-mcp-a2a` | 18081 | MCP endpoint | default |
| `yawl-mcp-a2a` | 18082 | A2A REST endpoint | default |

### Starting Services

```bash
# Start all services
docker compose up -d

# Start with production profile (stateful + PostgreSQL)
docker compose --profile production up -d

# Start only stateless engine
docker compose --profile stateless up -d

# Start only MCP/A2A
docker compose up -d yawl-mcp-a2a
```

---

## Error Handling

### Common Issues and Solutions

| Issue | Symptoms | Solution |
|-------|----------|----------|
| **Engine not ready** | Timeout waiting for health check | `./patterns/engine-health.sh --timeout 300` |
| **Authentication failed** | Invalid connectionID error | Verify credentials: `admin/YAWL` |
| **Pattern not found** | 404 on specification upload | Check path: `find yawl-mcp-a2a-app/src/main/resources/patterns -name "*.yaml"` |
| **Port already in use** | Bind error on startup | `lsof -i :8080` then kill process |
| **PostgreSQL not ready** | Connection refused | Wait longer or check container logs |
| **Out of memory** | Container crashes | Increase Docker memory limit |
| **Docker daemon not running** | Cannot connect to Docker | Start Docker Desktop or dockerd |

### Error Codes

| Exit Code | Meaning | Action |
|-----------|---------|--------|
| 0 | All validations passed | Proceed with deployment |
| 1 | One or more validations failed | Review failures, fix issues |
| 2 | Warnings only (no failures) | Review warnings, proceed if acceptable |
| 3+ | System error | Check logs, environment issues |

### Debugging Commands

```bash
# Check container status
docker compose ps

# View engine logs
docker compose logs -f yawl-engine

# Check container health
docker inspect yawl-engine --format='{{.State.Health.Status}}'

# Test API connectivity
curl -s http://localhost:8080/actuator/health/liveness | jq

# Check pattern files
ls -la yawl-mcp-a2a-app/src/main/resources/patterns/
```

---

## Performance Metrics

### Tracked Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| `pattern_execution_time_ms` | Time to validate single pattern | < 5000ms |
| `case_throughput_per_hour` | Cases completed per hour | > 1000 |
| `work_item_completion_rate` | Percentage of work items completed | 100% |
| `http_request_count` | Total API requests made | N/A |
| `memory_usage_mb` | Peak memory during validation | < 512MB |
| `engine_startup_time_ms` | Time for engine to become healthy | < 30000ms |

### Baseline Files

| File | Purpose |
|------|---------|
| `docs/v6/performance-history/performance-history.jsonl` | Historical performance data |
| `reports/performance-baseline.json` | Current baseline measurements |

### Performance Testing

```bash
# Run performance baseline validation
bash validate-performance-baselines.sh

# Run concurrent stress test
bash validate-concurrent-stress.sh --concurrency 100

# Run chaos engineering tests
bash validate-chaos-stress.sh --duration 300
```

---

## Best Practices

### Development Workflow

1. **Always start with engine health check**
   ```bash
   ./patterns/engine-health.sh --timeout 60
   ```

2. **Use category validation for pattern groups**
   ```bash
   ./patterns/validate-all-patterns.sh --category basic
   ```

3. **Use demo mode for quick verification during development**
   ```bash
   ./business-scenarios/run-all-scenarios.sh --demo
   ```

4. **Generate reports for analysis**
   ```bash
   ./patterns/generate-report.sh --format html
   ```

### CI/CD Integration

1. **Use JSON output for parsing**
   ```bash
   bash validate-all.sh --format json --output-dir $CI_ARTIFACTS
   ```

2. **Use JUnit XML for test reporting**
   ```bash
   bash validate-all.sh --format junit
   # Publish validation-report.xml to test reporter
   ```

3. **Fail-fast for quick feedback**
   ```bash
   bash validate-all.sh --fail-fast --quick
   ```

4. **80/20 for PR validation**
   ```bash
   bash validate-80-20.sh  # Fast feedback on PRs
   ```

### Production Validation

1. **Run full validation before release**
   ```bash
   bash validate-all.sh  # All suites including chaos
   ```

2. **Verify release readiness**
   ```bash
   bash validate-release.sh --version 6.0.0
   ```

3. **Check observatory facts are fresh**
   ```bash
   bash validate-observatory.sh --refresh --verify-hashes
   ```

---

## Integration Notes

### Process Mining Integration

The validation system generates process mining-compatible event logs:

```json
{
  "case_id": "order-12345",
  "activity": "PaymentProcessed",
  "timestamp": "2026-02-20T10:40:00Z",
  "resource": "payment-service",
  "transition": "complete"
}
```

### Monitoring Integration

Health and metrics endpoints:

- **Prometheus**: `/actuator/prometheus`
- **Health**: `/actuator/health`
- **Metrics**: `/actuator/metrics`

### CI/CD Pipeline Integration

```yaml
# .github/workflows/validation.yml
- name: Run Validation
  run: |
    bash scripts/validation/validate-all.sh --format junit --output-dir reports
- name: Publish Test Results
  uses: EnricoMi/publish-unit-test-result-action@v2
  with:
    files: reports/validation-report.xml
```

### Output Aggregation Library

The `lib/output-aggregation.sh` library provides functions for aggregating results:

```bash
source scripts/validation/lib/output-aggregation.sh

# Initialize
agg_init "my-validation" "./output"

# Add results
agg_add_result "test-1" "PASS" "All good" 1234
agg_add_result "test-2" "FAIL" "Assertion failed" 5678

# Output
agg_output_json "./report.json"
agg_output_junit "./report.xml"
agg_output_summary
```

---

## Maintainer

This validation system is designed and maintained to support Dr. Wil van der Aalst's work in YAWL and Process Mining.

## License

This validation system is part of the YAWL v6 project and follows the same license terms.
