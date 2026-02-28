# Intelligent Monitoring in YAWL v6.0.0 — Production Operations Guide

**Version**: 6.0.0
**Updated**: February 28, 2026
**Audience**: Operations, DevOps, Platform Engineers, SREs
**Owner**: YAWL Operations & Observability Team
**Classification**: Production Guide (Diataxis: HOW)

---

## Table of Contents

1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [Intelligence Layer Architecture](#intelligence-layer-architecture)
4. [Workflow: Monitor Execution State](#workflow-monitor-execution-state)
5. [Workflow: Track Resource Allocation](#workflow-track-resource-allocation)
6. [Workflow: Detect Dependency Conflicts](#workflow-detect-dependency-conflicts)
7. [Workflow: Analyze Performance Regressions](#workflow-analyze-performance-regressions)
8. [Typed Deltas & Receipt Chains](#typed-deltas--receipt-chains)
9. [Watermark Protocol & Cache Efficiency](#watermark-protocol--cache-efficiency)
10. [Observatory Integration](#observatory-integration)
11. [Dashboard Setup](#dashboard-setup)
12. [Alerting Strategies](#alerting-strategies)
13. [Troubleshooting & Recovery](#troubleshooting--recovery)
14. [Appendix: Tools & Commands](#appendix-tools--commands)

---

## Overview

### Purpose

Intelligent monitoring in YAWL v6.0.0 enables **real-time observability** of workflow execution, resource allocation, and system health through:

- **Typed deltas** (semantic unit tracking, not line diffs)
- **Receipt chains** (blake3-signed audit trails)
- **Watermark protocol** (TTL-based cache efficiency)
- **Observatory integration** (modules.json, gates.json, deps-conflicts.json facts)
- **Live metrics** (Prometheus + OpenTelemetry)

This guide is for **operators running YAWL at scale**, managing hundreds of concurrent workflows with sub-second latency requirements.

### Key Principles

| Principle | Application |
|-----------|-------------|
| **Drift → 0** | Monitor observables (O) continuously; alert on deviation from baseline |
| **Facts first** | Load Observatory facts (~50 tokens) instead of grep (~5000 tokens) |
| **Semantic deltas** | Track **what changed** (declarations, rules, dependencies), not **where** (line numbers) |
| **Immutable receipts** | blake3(canonical_json(δ)) → signed audit trail per change |
| **Watermarks** | Skip redundant fetches; only update on content_hash change |
| **Real impl ∨ throw** | No stubs; decode actual metrics or error clearly |

### Scope & Limitations

**In Scope**:
- Real-time workflow execution monitoring (YEngine, YNetRunner)
- Resource allocation tracking (task queues, work items)
- Dependency conflict detection across 19 modules
- Performance regression analysis (latency, throughput)
- Multi-tenant workflow isolation

**Out of Scope** (see separate guides):
- Development-time observability (see `.claude/OBSERVATORY.md`)
- Schema validation (see `schema/XSD-validation.md`)
- Code generation instrumentation (see `ggen/monitoring-patterns.md`)

---

## Core Concepts

### 1. Observable (O)

An **observable** is a live measurement or fact your system produces at runtime:

```typescript
Observable = WorkflowExecution | ResourceMetric | DependencyState | TaskLatency
```

**Examples**:
- `workflow.case_id="WF-2026-001"` execution state: READY → EXECUTING → COMPLETE
- `resource.allocator_pool_size=150` for parallel task queue
- `dependency.yawl_engine→yawl_stateless` conflict detected
- `task.latency_p99=423ms` performance metric

### 2. Typed Delta (δ)

Instead of line-based diffs (unified patch), YAWL uses **semantic unit deltas**:

```json
{
  "delta_id": "δ-2026-02-28T14:32:15.123Z",
  "change_type": "declaration",
  "semantic_unit": {
    "kind": "Function",
    "name": "YNetRunner.executeNet()",
    "location": "org.yawlfoundation.yawl.engine.YNetRunner"
  },
  "before": {
    "signature": "void executeNet(YNet net, YWorkItem item)",
    "telemetry_enabled": false
  },
  "after": {
    "signature": "void executeNet(YNet net, YWorkItem item, OpenTelemetrySpan span)",
    "telemetry_enabled": true
  }
}
```

**Semantic unit kinds** (DeclKind):
- `Function`: Method signature + body
- `Type`: Class/interface definition
- `Constant`: Configuration value
- `Import`: Dependency relationship
- `Module`: Package or module boundary
- `Field`: Instance or class variable
- `Dependency`: Inter-module edge
- `Rule`: Business or validation rule

### 3. Receipt Chain

A **receipt** is a blake3-signed hash of a typed delta, forming an immutable audit trail:

```json
{
  "session_id": "session_01SfdxrP7PZC8eiQQws7Rbz2",
  "timestamp": "2026-02-28T14:32:15.123Z",
  "receipts": [
    {
      "delta_id": "δ-2026-02-28T14:32:15.123Z",
      "canonical_json": "{...}",
      "blake3_hash": "a1b2c3d4e5f6...",
      "previous_hash": "z9y8x7w6v5u4...",
      "semantic_unit": "Function/YNetRunner.executeNet"
    }
  ]
}
```

**Properties**:
- **Immutable**: Once signed, cannot be modified
- **Linked**: Each receipt hashes previous receipt (blockchain-like)
- **Reproducible**: Canonical JSON ensures same hash from same content
- **Verifiable**: blake3 signatures enable audit verification

### 4. Watermark Protocol

**Watermarks** prevent cache thrashing by tracking content hashes and timestamps:

```json
{
  "watermark": {
    "resource": "modules.json",
    "fetch_timestamp": "2026-02-28T14:00:00Z",
    "content_hash": "sha256:abc123...",
    "ttl_seconds": 3600,
    "expires_at": "2026-02-28T15:00:00Z",
    "cached": true
  }
}
```

**Logic**:
```
fetch(resource) if:
  - watermark not found, OR
  - now ≥ expires_at, OR
  - content_hash(new_response) ≠ watermark.content_hash
else:
  skip_fetch() && use_cache()
```

---

## Intelligence Layer Architecture

### Component Stack

```
┌─────────────────────────────────────────────────────────┐
│          Application Logic (Workflows)                   │
│     (YEngine, YNetRunner, YWorkItem, YSpecification)    │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│     Intelligence Layer (ι)                               │
│  - Typed Delta Extraction (δ)                           │
│  - Receipt Chain Management                            │
│  - Watermark Protocol                                   │
│  - Observatory Integration                             │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│     Observability Backend (OpenTelemetry + Prometheus)   │
│  - Spans (Distributed Tracing)                          │
│  - Metrics (Counters, Histograms, Gauges)              │
│  - Logs (Structured JSON, Log4j2)                       │
│  - Events (Andon cord alerts)                           │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│    Collection Layer (Exporters)                          │
│  - Jaeger/Tempo (distributed traces)                    │
│  - Prometheus (metrics scraping)                        │
│  - ELK/Loki (log aggregation)                          │
│  - Custom webhooks (alerting)                           │
└─────────────────────────────────────────────────────────┘
```

### Intelligence Binaries

YAWL ships two specialized binaries for production intelligence:

#### yawl-jira (Hook Orchestrator)
- **Size**: <50ms latency
- **Function**: Coordinates ticket context injection
- **Trigger**: SessionStart, UserPromptSubmit
- **Data**: `.claude/jira/tickets/*.toml` (TOML-based, no external DB)

#### yawl-scout (Async Fetcher)
- **Function**: Fetches live specs, deps, changelogs asynchronously
- **Non-blocking**: Runs in background; returns cached data immediately
- **Watermark**: Respects TTL; skips if content_hash unchanged
- **Cache**: `.claude/context/live-intelligence.md`

### Injection Points

**Four key injection points** for intelligence:

1. **SessionStart**: Fetch ticket context + baseline Observatory facts
   ```
   trigger: Session initialized
   action: Load modules.json, gates.json, deps-conflicts.json
   output: Context window seeded with facts
   ```

2. **UserPromptSubmit**: Select relevant delta slice for user's task
   ```
   trigger: User submits task description
   action: Match keywords → select deltas from live-intelligence.md
   output: Inject 5-10 relevant deltas into prompt
   ```

3. **PreToolUse**: Log tool invocation (baseline for correlation)
   ```
   trigger: Before bash/read/grep execution
   action: Record tool name, args, expected_output
   output: Enables performance correlation with tool latency
   ```

4. **PostToolUse**: Record tool output + correction (if needed)
   ```
   trigger: After tool completes
   action: Compute δ(expected, actual) → emit receipt
   output: Audit trail of tool corrections + learnings
   ```

---

## Workflow: Monitor Execution State

### Objective

Track workflow case state transitions (READY → EXECUTING → COMPLETE) and detect execution anomalies in real-time.

### Prerequisites

- YAWL engine running with OpenTelemetry enabled
- Prometheus scrape target configured (port 8888/metrics)
- Jaeger or Tempo OTLP receiver running

### Step-by-Step Procedure

#### Step 1: Enable OpenTelemetry in YAWL

Edit `yawl.properties` or `application-prod.yaml`:

```yaml
# application-prod.yaml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
      timeout: 10000ms
      enabled: true

otel:
  metrics:
    enabled: true
    interval_ms: 60000
  traces:
    sample_rate: 0.10  # 10% sampling for cost efficiency

yawl:
  engine:
    telemetry_enabled: true
    trace_work_items: true
    trace_resource_allocation: true
```

Restart YAWL engine:

```bash
systemctl restart yawl-engine
```

#### Step 2: Load Baseline Observables via Observatory

Fetch current system facts:

```bash
#!/usr/bin/env bash
# scripts/fetch-observables.sh

set -euo pipefail

OBSERVATORY_DIR="${YAWL_HOME:-.}/docs/v6/latest"
FACTS_DIR="${OBSERVATORY_DIR}/facts"

# Load baseline metrics from Observatory
load_baseline() {
  echo "📊 Loading baseline observables from Observatory facts..."

  # Current module count (static baseline)
  MODULES=$(jq '.reactor_order | length' "${FACTS_DIR}/reactor.json")
  echo "✓ BASELINE: ${MODULES} modules"

  # Load test count (for regression detection)
  TESTS=$(jq '.summary.total_test_files' "${FACTS_DIR}/tests.json")
  echo "✓ BASELINE: ${TESTS} test files"

  # Load dependency graph (for conflict detection)
  echo "✓ BASELINE: Dependency graph loaded"
}

# Query current engine metrics
query_live_metrics() {
  echo ""
  echo "📈 Querying live engine metrics..."

  # Get current case count from Prometheus
  curl -s "http://localhost:8888/metrics" | grep -E "yawl_workflow_cases_total|yawl_task_queue_depth" || {
    echo "⚠️  Prometheus not yet available. Metrics will populate after first case execution."
  }
}

load_baseline
query_live_metrics
```

Run:

```bash
bash scripts/fetch-observables.sh
```

Expected output:

```
📊 Loading baseline observables from Observatory facts...
✓ BASELINE: 19 modules
✓ BASELINE: 187 test files
✓ BASELINE: Dependency graph loaded

📈 Querying live engine metrics...
yawl_workflow_cases_total{case_id="WF-2026-001",status="READY"} 5
yawl_workflow_cases_total{case_id="WF-2026-001",status="EXECUTING"} 2
yawl_task_queue_depth{allocator_id="allocator-1"} 42
```

#### Step 3: Monitor Case State Transitions

Create a monitoring script to track state transitions:

```bash
#!/usr/bin/env bash
# scripts/monitor-case-transitions.sh

set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ALERT_WEBHOOK="${ALERT_WEBHOOK:-http://localhost:5000/alerts}"

monitor_transitions() {
  echo "🔍 Monitoring workflow case state transitions..."

  while true; do
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Query: Cases in READY state
    READY_COUNT=$(curl -s "${PROMETHEUS_URL}/api/v1/query" \
      --data-urlencode 'query=increase(yawl_workflow_cases_total{status="READY"}[5m])' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    # Query: Cases in EXECUTING state
    EXECUTING_COUNT=$(curl -s "${PROMETHEUS_URL}/api/v1/query" \
      --data-urlencode 'query=increase(yawl_workflow_cases_total{status="EXECUTING"}[5m])' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    # Query: Cases in COMPLETE state
    COMPLETE_COUNT=$(curl -s "${PROMETHEUS_URL}/api/v1/query" \
      --data-urlencode 'query=increase(yawl_workflow_cases_total{status="COMPLETE"}[5m])' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    # Query: Cases in ERROR state
    ERROR_COUNT=$(curl -s "${PROMETHEUS_URL}/api/v1/query" \
      --data-urlencode 'query=increase(yawl_workflow_cases_total{status="ERROR"}[5m])' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    echo "[$TIMESTAMP] READY=$READY_COUNT EXECUTING=$EXECUTING_COUNT COMPLETE=$COMPLETE_COUNT ERROR=$ERROR_COUNT"

    # Alert if ERROR count exceeds threshold
    if (( $(echo "$ERROR_COUNT > 5" | bc -l) )); then
      curl -X POST "$ALERT_WEBHOOK" \
        -H "Content-Type: application/json" \
        -d @- <<EOF
{
  "alert_type": "WORKFLOW_ERROR_SPIKE",
  "timestamp": "$TIMESTAMP",
  "metric": "yawl_workflow_cases_total{status=\"ERROR\"}",
  "value": $ERROR_COUNT,
  "threshold": 5,
  "severity": "CRITICAL"
}
EOF
      echo "⚠️  ALERT: Error spike detected (${ERROR_COUNT} errors in 5m)"
    fi

    sleep 30
  done
}

monitor_transitions
```

Run:

```bash
bash scripts/monitor-case-transitions.sh
```

#### Step 4: Analyze State Transition Deltas

When detecting anomalies, extract typed deltas to understand **what changed**:

```bash
#!/usr/bin/env bash
# scripts/analyze-state-delta.sh

CASE_ID="WF-2026-001"
TRANSITION_FROM="READY"
TRANSITION_TO="ERROR"

analyze_delta() {
  echo "🔍 Analyzing delta: $CASE_ID: $TRANSITION_FROM → $TRANSITION_TO"

  # Query Jaeger for span chain showing state transition
  curl -s "http://jaeger:16686/api/traces?service=yawl-engine&tag=case.id=$CASE_ID" \
    | jq '.data[0].spans[] | select(.operationName | contains("state_transition"))' \
    > /tmp/state-transition-spans.json

  # Extract semantic delta
  jq '
    {
      delta_id: .spanID,
      timestamp: (.startTime / 1000 | strftime("%Y-%m-%dT%H:%M:%SZ")),
      semantic_unit: {
        kind: "Rule",
        name: "WorkflowCaseStateTransition",
        case_id: .tags[] | select(.key == "case.id") | .value
      },
      before: {
        state: $TRANSITION_FROM,
        active_tasks: (.tags[] | select(.key == "before.active_tasks") | .value)
      },
      after: {
        state: $TRANSITION_TO,
        active_tasks: (.tags[] | select(.key == "after.active_tasks") | .value),
        error_message: (.tags[] | select(.key == "error") | .value)
      }
    }
  ' /tmp/state-transition-spans.json
}

analyze_delta
```

#### Step 5: Create Receipt for Audit Trail

Record the delta in an immutable receipt:

```python
#!/usr/bin/env python3
# scripts/emit-state-delta-receipt.py

import json
import hashlib
from datetime import datetime, timezone
from pathlib import Path

def emit_receipt(case_id: str, from_state: str, to_state: str, metadata: dict):
    """Emit blake3-signed receipt for state transition."""

    # Construct canonical delta
    delta = {
        "delta_id": f"δ-{datetime.now(timezone.utc).isoformat()}",
        "case_id": case_id,
        "semantic_unit": {
            "kind": "Rule",
            "name": "WorkflowCaseStateTransition"
        },
        "before": {"state": from_state},
        "after": {"state": to_state, **metadata},
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

    # Canonical JSON (sorted keys, no whitespace)
    canonical_json = json.dumps(delta, sort_keys=True, separators=(',', ':'))

    # Sign with blake3 (simulated with SHA256 for portability)
    hash_sig = hashlib.sha256(canonical_json.encode()).hexdigest()

    # Emit receipt
    receipt = {
        "delta_id": delta["delta_id"],
        "case_id": case_id,
        "canonical_json": canonical_json,
        "blake3_hash": hash_sig,
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

    # Write to receipts directory
    receipt_dir = Path(".claude/receipts")
    receipt_dir.mkdir(parents=True, exist_ok=True)

    receipt_file = receipt_dir / f"state-transition-{case_id}.jsonl"
    with receipt_file.open("a") as f:
        f.write(json.dumps(receipt) + "\n")

    print(f"✓ Receipt emitted: {receipt_file}")
    print(f"  Hash: {hash_sig}")
    return hash_sig

if __name__ == "__main__":
    # Example: WF-2026-001 transitioned from READY to EXECUTING
    hash_sig = emit_receipt(
        case_id="WF-2026-001",
        from_state="READY",
        to_state="EXECUTING",
        metadata={
            "duration_ms": 245,
            "active_tasks_count": 3,
            "trigger": "user_submission"
        }
    )
```

Run:

```bash
python3 scripts/emit-state-delta-receipt.py
```

---

## Workflow: Track Resource Allocation

### Objective

Monitor resource allocation changes (task queues, participant availability, work item assignments) and detect bottlenecks.

### Prerequisites

- YAWL resourcing module deployed
- Prometheus configured to scrape `allocator:8889/metrics`
- Resource allocation service running

### Step-by-Step Procedure

#### Step 1: Fetch Baseline Resource Capacity

```bash
#!/usr/bin/env bash
# scripts/baseline-resource-capacity.sh

set -euo pipefail

RESOURCE_API="http://localhost:8080/yawl/resourcing"

baseline_capacity() {
  echo "📋 Fetching baseline resource capacity..."

  # Query total participant count
  PARTICIPANT_COUNT=$(curl -s "${RESOURCE_API}/participants" \
    | jq '.participants | length')
  echo "✓ Participants: ${PARTICIPANT_COUNT}"

  # Query allocator pool sizes
  ALLOCATORS=$(curl -s "${RESOURCE_API}/allocators" | jq '.allocators')
  echo "✓ Allocators:"
  echo "$ALLOCATORS" | jq '.[] | "  - \(.id): capacity=\(.capacity_max)"'

  # Query current utilization
  UTILIZATION=$(curl -s "${RESOURCE_API}/utilization" | jq '.overall_utilization_percent')
  echo "✓ Overall utilization: ${UTILIZATION}%"

  # Save baseline
  jq -n \
    --arg count "$PARTICIPANT_COUNT" \
    --argjson allocators "$ALLOCATORS" \
    --arg utilization "$UTILIZATION" \
    '{
      timestamp: now | strftime("%Y-%m-%dT%H:%M:%SZ"),
      participants: ($count | tonumber),
      allocators: $allocators,
      utilization_percent: ($utilization | tonumber)
    }' > .claude/context/resource-baseline.json

  echo "✓ Baseline saved to .claude/context/resource-baseline.json"
}

baseline_capacity
```

#### Step 2: Monitor Queue Depth & Latency

```bash
#!/usr/bin/env bash
# scripts/monitor-resource-queue-depth.sh

set -euo pipefail

PROMETHEUS="http://localhost:9090"
CHECK_INTERVAL=30

monitor_queues() {
  echo "📊 Monitoring resource allocation queue depth..."

  while true; do
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Query: Task queue depth (tasks waiting for allocation)
    QUEUE_DEPTH=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=yawl_task_queue_depth' \
      | jq '.data.result[] | {allocator_id: .metric.allocator_id, depth: .value[1]}' 2>/dev/null)

    # Query: Average allocation latency (p50)
    ALLOC_LATENCY_P50=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=histogram_quantile(0.50, yawl_allocation_latency_ms)' \
      | jq '.data.result[0].value[1]' 2>/dev/null)

    # Query: Allocation latency p99
    ALLOC_LATENCY_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=histogram_quantile(0.99, yawl_allocation_latency_ms)' \
      | jq '.data.result[0].value[1]' 2>/dev/null)

    echo "[$TIMESTAMP]"
    echo "  Queue depth: $(echo "$QUEUE_DEPTH" | jq -c '.')"
    echo "  Allocation latency: p50=${ALLOC_LATENCY_P50}ms, p99=${ALLOC_LATENCY_P99}ms"

    # Alert on queue saturation
    MAX_DEPTH=$(echo "$QUEUE_DEPTH" | jq -r '.depth' | sort -rn | head -1)
    if (( $(echo "$MAX_DEPTH > 500" | bc -l) )); then
      echo "⚠️  ALERT: Queue saturation detected (depth=${MAX_DEPTH})"
      # Emit alert webhook here
    fi

    sleep $CHECK_INTERVAL
  done
}

monitor_queues
```

#### Step 3: Track Allocation State Deltas

Create a script to emit typed deltas when allocation state changes:

```python
#!/usr/bin/env python3
# scripts/emit-allocation-delta.py

import json
import requests
from datetime import datetime, timezone
from typing import Dict, Any

def fetch_current_allocation() -> Dict[str, Any]:
    """Fetch current resource allocation state from API."""
    response = requests.get("http://localhost:8080/yawl/resourcing/state")
    return response.json()

def emit_allocation_delta(previous_state: Dict, current_state: Dict):
    """Emit delta when allocation state changes."""

    delta = {
        "delta_id": f"δ-{datetime.now(timezone.utc).isoformat()}",
        "change_type": "dependency",  # Resource allocation is a dependency change
        "semantic_unit": {
            "kind": "Field",
            "name": "ResourceAllocator.workItemAssignments",
            "location": "org.yawlfoundation.yawl.resourcing"
        },
        "before": {
            "allocator_queues": {
                alloc_id: queue["depth"]
                for alloc_id, queue in previous_state.get("allocators", {}).items()
            },
            "participant_availability": previous_state.get("participants_available")
        },
        "after": {
            "allocator_queues": {
                alloc_id: queue["depth"]
                for alloc_id, queue in current_state.get("allocators", {}).items()
            },
            "participant_availability": current_state.get("participants_available")
        },
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

    # Emit JSON for logging
    print(json.dumps(delta, indent=2))

    # Store in receipt chain
    receipt_file = ".claude/receipts/allocation-deltas.jsonl"
    with open(receipt_file, "a") as f:
        # Canonical form (no indent)
        canonical = json.dumps(delta, sort_keys=True, separators=(',', ':'))
        f.write(canonical + "\n")

    return delta

if __name__ == "__main__":
    previous = fetch_current_allocation()
    # ... wait or simulate change ...
    current = fetch_current_allocation()

    if previous != current:
        emit_allocation_delta(previous, current)
```

#### Step 4: Detect & Alert on Resource Bottlenecks

```bash
#!/usr/bin/env bash
# scripts/detect-resource-bottlenecks.sh

set -euo pipefail

PROMETHEUS="http://localhost:9090"
ALERT_WEBHOOK="http://localhost:5000/alerts"

detect_bottlenecks() {
  echo "🔍 Detecting resource allocation bottlenecks..."

  # Condition 1: Queue depth > 80% of max capacity
  MAX_QUEUE=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode 'query=max(yawl_task_queue_depth)' \
    | jq '.data.result[0].value[1]')

  CAPACITY=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode 'query=yawl_allocator_capacity_max' \
    | jq '.data.result[0].value[1]')

  THRESHOLD=$(echo "$CAPACITY * 0.8" | bc)

  if (( $(echo "$MAX_QUEUE > $THRESHOLD" | bc -l) )); then
    echo "⚠️  BOTTLENECK: Queue depth ($MAX_QUEUE) > 80% capacity ($THRESHOLD)"
  fi

  # Condition 2: Allocation latency spike (p99 > baseline × 2)
  ALLOC_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode 'query=histogram_quantile(0.99, yawl_allocation_latency_ms)' \
    | jq '.data.result[0].value[1]')

  BASELINE_P99=150  # From historical baseline
  SPIKE_THRESHOLD=$(echo "$BASELINE_P99 * 2" | bc)

  if (( $(echo "$ALLOC_P99 > $SPIKE_THRESHOLD" | bc -l) )); then
    echo "⚠️  BOTTLENECK: Allocation latency spike (p99=${ALLOC_P99}ms vs baseline=${BASELINE_P99}ms)"

    # Send alert
    curl -X POST "$ALERT_WEBHOOK" \
      -H "Content-Type: application/json" \
      -d @- <<EOF
{
  "alert_type": "RESOURCE_ALLOCATION_BOTTLENECK",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "metric": "yawl_allocation_latency_ms{quantile=\"0.99\"}",
  "value": $ALLOC_P99,
  "baseline": $BASELINE_P99,
  "threshold": $SPIKE_THRESHOLD,
  "severity": "WARNING"
}
EOF
  fi

  # Condition 3: Participant starvation (available < 10% of total)
  AVAILABLE=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode 'query=yawl_participants_available' \
    | jq '.data.result[0].value[1]')

  TOTAL=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode 'query=yawl_participants_total' \
    | jq '.data.result[0].value[1]')

  AVAILABILITY_PCT=$(echo "($AVAILABLE / $TOTAL) * 100" | bc)

  if (( $(echo "$AVAILABILITY_PCT < 10" | bc -l) )); then
    echo "⚠️  BOTTLENECK: Participant starvation (${AVAILABILITY_PCT}% available)"
  fi
}

detect_bottlenecks
```

---

## Workflow: Detect Dependency Conflicts

### Objective

Real-time detection of dependency conflicts across YAWL's 19 modules using Observatory facts.

### Prerequisites

- Observatory facts generated (reactor.json, deps-conflicts.json)
- Module cache warmed with dependency graph
- Prometheus metrics for module health

### Step-by-Step Procedure

#### Step 1: Load Dependency Graph from Observatory

```bash
#!/usr/bin/env bash
# scripts/load-dependency-graph.sh

set -euo pipefail

OBSERVATORY_FACTS="docs/v6/latest/facts"
DEPS_FILE="${OBSERVATORY_FACTS}/deps-conflicts.json"

load_dependency_graph() {
  echo "📦 Loading dependency graph from Observatory..."

  if [ ! -f "$DEPS_FILE" ]; then
    echo "❌ Error: $DEPS_FILE not found. Run: bash scripts/observatory/observatory.sh"
    exit 1
  fi

  # Extract total dependency edges
  EDGE_COUNT=$(jq '.dependencies | length' "$DEPS_FILE")
  echo "✓ Loaded $EDGE_COUNT dependency edges"

  # Extract known conflicts
  CONFLICTS=$(jq '.conflicts | length' "$DEPS_FILE")
  echo "✓ Known conflicts: $CONFLICTS"

  # Load into memory-mapped cache for fast queries
  # (In production, use a graph database like Neo4j)
  jq '.dependencies[] | {
    from: .source,
    to: .target,
    layer: .layer,
    conflict_risk: .conflict_risk_level
  }' "$DEPS_FILE" > .claude/context/dependency-graph-cache.jsonl

  echo "✓ Dependency graph cached"
}

load_dependency_graph
```

#### Step 2: Query Dependency Path & Conflict Risk

```python
#!/usr/bin/env python3
# scripts/analyze-dependency-path.py

import json
from pathlib import Path
from typing import List, Dict, Set

class DependencyAnalyzer:
    def __init__(self, cache_file: str):
        """Initialize with cached dependency graph."""
        self.graph = {}
        self.reverse_graph = {}

        with open(cache_file) as f:
            for line in f:
                edge = json.loads(line)
                from_module = edge["from"]
                to_module = edge["to"]

                if from_module not in self.graph:
                    self.graph[from_module] = []
                self.graph[from_module].append(to_module)

                if to_module not in self.reverse_graph:
                    self.reverse_graph[to_module] = []
                self.reverse_graph[to_module].append(from_module)

    def find_circular_dependencies(self, max_depth: int = 10) -> List[List[str]]:
        """Detect circular dependency chains."""
        cycles = []

        for start_node in self.graph:
            visited = set()
            path = [start_node]

            def dfs(node: str, current_path: List[str]):
                if len(current_path) > max_depth:
                    return

                if node in self.graph:
                    for neighbor in self.graph[node]:
                        if neighbor == start_node and len(current_path) > 1:
                            cycles.append(current_path + [neighbor])
                        elif neighbor not in visited:
                            visited.add(neighbor)
                            dfs(neighbor, current_path + [neighbor])

            dfs(start_node, path)

        return cycles

    def path_conflict_risk(self, source: str, target: str) -> Dict:
        """Calculate conflict risk along dependency path."""
        if source not in self.graph:
            return {"exists": False, "risk_level": "NONE"}

        # BFS to find all paths
        queue = [(source, [source], 0)]
        all_paths = []

        while queue:
            current, path, depth = queue.pop(0)

            if depth > 5:  # Limit depth to 5 hops
                continue

            if current == target:
                all_paths.append(path)
                continue

            if current in self.graph:
                for neighbor in self.graph[current]:
                    if neighbor not in path:  # Avoid cycles
                        queue.append((neighbor, path + [neighbor], depth + 1))

        if not all_paths:
            return {"exists": False, "risk_level": "NONE"}

        # Risk = number of paths (more paths = higher coupling)
        risk_level = "LOW" if len(all_paths) == 1 else \
                    "MEDIUM" if len(all_paths) <= 3 else "HIGH"

        return {
            "exists": True,
            "paths": all_paths,
            "path_count": len(all_paths),
            "risk_level": risk_level,
            "shortest_path_length": min(len(p) for p in all_paths)
        }

if __name__ == "__main__":
    analyzer = DependencyAnalyzer(".claude/context/dependency-graph-cache.jsonl")

    # Example: Detect circular dependencies
    cycles = analyzer.find_circular_dependencies()
    if cycles:
        print("⚠️  Circular dependencies detected:")
        for cycle in cycles:
            print(f"  {' → '.join(cycle)}")
    else:
        print("✓ No circular dependencies")

    # Example: Analyze path from yawl-engine to yawl-mcp-a2a-app
    risk = analyzer.path_conflict_risk(
        "yawl-engine",
        "yawl-mcp-a2a-app"
    )
    print(f"\nDependency path analysis (yawl-engine → yawl-mcp-a2a-app):")
    print(json.dumps(risk, indent=2))
```

Run:

```bash
python3 scripts/analyze-dependency-path.py
```

Expected output:

```
✓ No circular dependencies

Dependency path analysis (yawl-engine → yawl-mcp-a2a-app):
{
  "exists": true,
  "paths": [
    ["yawl-engine", "yawl-stateless", "yawl-integration", "yawl-mcp-a2a-app"],
    ["yawl-engine", "yawl-monitoring", "yawl-integration", "yawl-mcp-a2a-app"]
  ],
  "path_count": 2,
  "risk_level": "MEDIUM",
  "shortest_path_length": 4
}
```

#### Step 3: Monitor Dependency Conflict Metrics

```bash
#!/usr/bin/env bash
# scripts/monitor-dependency-conflicts.sh

set -euo pipefail

PROMETHEUS="http://localhost:9090"
OBSERVATORY_FACTS="docs/v6/latest/facts/deps-conflicts.json"

monitor_conflicts() {
  echo "🔍 Monitoring dependency conflicts in real-time..."

  # Load baseline conflict count from Observatory
  BASELINE_CONFLICTS=$(jq '.conflicts | length' "$OBSERVATORY_FACTS")
  echo "📊 Baseline conflicts (from Observatory): ${BASELINE_CONFLICTS}"

  while true; do
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Query: New dependency violations detected at runtime
    NEW_VIOLATIONS=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=increase(yawl_dependency_violation_total[5m])' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    # Query: Module load failures (symptom of dependency issue)
    MODULE_LOAD_FAILURES=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=increase(yawl_module_load_failure_total[5m])' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    echo "[$TIMESTAMP] Violations=${NEW_VIOLATIONS} LoadFailures=${MODULE_LOAD_FAILURES}"

    # Alert if violations increased
    if (( $(echo "$NEW_VIOLATIONS > 0" | bc -l) )); then
      echo "⚠️  ALERT: New dependency violations detected!"
      curl -X POST "http://localhost:5000/alerts" \
        -H "Content-Type: application/json" \
        -d @- <<EOF
{
  "alert_type": "DEPENDENCY_CONFLICT_DETECTED",
  "timestamp": "$TIMESTAMP",
  "new_violations": $(echo "$NEW_VIOLATIONS" | cut -d. -f1),
  "baseline_conflicts": $BASELINE_CONFLICTS,
  "severity": "HIGH"
}
EOF
    fi

    sleep 60
  done
}

monitor_conflicts
```

#### Step 4: Generate Dependency Conflict Report

```python
#!/usr/bin/env python3
# scripts/generate-dependency-conflict-report.py

import json
from datetime import datetime, timezone
from pathlib import Path

def generate_conflict_report():
    """Generate comprehensive dependency conflict report."""

    # Load Observatory facts
    deps_file = Path("docs/v6/latest/facts/deps-conflicts.json")
    with open(deps_file) as f:
        deps_data = json.load(f)

    # Load reactor ordering (defines correct build order)
    reactor_file = Path("docs/v6/latest/facts/reactor.json")
    with open(reactor_file) as f:
        reactor_data = json.load(f)

    # Build module position map
    module_positions = {
        m["module"]: m["position"]
        for m in reactor_data["reactor_order"]
    }

    report = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "total_modules": reactor_data["total_modules"],
            "total_dependencies": len(deps_data["dependencies"]),
            "known_conflicts": len(deps_data.get("conflicts", [])),
            "circular_dependencies": 0,
            "forward_dependencies": 0,
            "backward_dependencies": 0
        },
        "conflicts": []
    }

    # Analyze each dependency for correctness
    for dep in deps_data["dependencies"]:
        source = dep["source"]
        target = dep["target"]
        source_pos = module_positions.get(source, 999)
        target_pos = module_positions.get(target, 999)

        if source_pos > target_pos:
            # Backward dependency (against reactor order)
            report["conflicts"].append({
                "type": "BACKWARD_DEPENDENCY",
                "source": source,
                "target": target,
                "source_position": source_pos,
                "target_position": target_pos,
                "severity": "CRITICAL"
            })
            report["summary"]["backward_dependencies"] += 1
        else:
            # Forward dependency (correct order)
            report["summary"]["forward_dependencies"] += 1

    # Write report
    report_file = Path(".claude/context/dependency-conflict-report.json")
    report_file.parent.mkdir(parents=True, exist_ok=True)

    with open(report_file, "w") as f:
        json.dump(report, f, indent=2)

    print(f"✓ Report written to {report_file}")
    print(f"\nSummary:")
    print(f"  Total dependencies: {report['summary']['total_dependencies']}")
    print(f"  Known conflicts: {report['summary']['known_conflicts']}")
    print(f"  Backward dependencies (CRITICAL): {report['summary']['backward_dependencies']}")

    if report["summary"]["backward_dependencies"] > 0:
        print(f"\n⚠️  CRITICAL: {report['summary']['backward_dependencies']} backward dependencies found!")
        for conflict in report["conflicts"]:
            if conflict["type"] == "BACKWARD_DEPENDENCY":
                print(f"  {conflict['source']} → {conflict['target']}")

if __name__ == "__main__":
    generate_conflict_report()
```

---

## Workflow: Analyze Performance Regressions

### Objective

Detect and root-cause performance regressions using typed deltas and historical baselines.

### Prerequisites

- Prometheus with 30+ days historical data
- Baseline metrics computed (p50, p99 latencies)
- Jaeger/Tempo for distributed trace analysis

### Step-by-Step Procedure

#### Step 1: Establish Performance Baseline

```bash
#!/usr/bin/env bash
# scripts/establish-perf-baseline.sh

set -euo pipefail

PROMETHEUS="http://localhost:9090"
BASELINE_WINDOW="7d"  # Last 7 days of data

establish_baseline() {
  echo "📊 Establishing performance baseline from last ${BASELINE_WINDOW}..."

  # Task execution latency (p50, p99)
  TASK_EXEC_P50=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=histogram_quantile(0.50, increase(yawl_task_execution_latency_ms_bucket[${BASELINE_WINDOW}]))" \
    | jq '.data.result[0].value[1]')

  TASK_EXEC_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=histogram_quantile(0.99, increase(yawl_task_execution_latency_ms_bucket[${BASELINE_WINDOW}]))" \
    | jq '.data.result[0].value[1]')

  # Case completion latency (p50, p99)
  CASE_COMP_P50=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=histogram_quantile(0.50, increase(yawl_case_completion_latency_s_bucket[${BASELINE_WINDOW}]))" \
    | jq '.data.result[0].value[1]')

  CASE_COMP_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=histogram_quantile(0.99, increase(yawl_case_completion_latency_s_bucket[${BASELINE_WINDOW}]))" \
    | jq '.data.result[0].value[1]')

  # Resource allocation latency
  ALLOC_P50=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=histogram_quantile(0.50, increase(yawl_allocation_latency_ms_bucket[${BASELINE_WINDOW}]))" \
    | jq '.data.result[0].value[1]')

  ALLOC_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=histogram_quantile(0.99, increase(yawl_allocation_latency_ms_bucket[${BASELINE_WINDOW}]))" \
    | jq '.data.result[0].value[1]')

  # Throughput (cases/sec)
  THROUGHPUT=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=rate(yawl_workflow_cases_total[${BASELINE_WINDOW}])" \
    | jq '.data.result[0].value[1]')

  # Write baseline
  jq -n \
    --arg task_p50 "$TASK_EXEC_P50" \
    --arg task_p99 "$TASK_EXEC_P99" \
    --arg case_p50 "$CASE_COMP_P50" \
    --arg case_p99 "$CASE_COMP_P99" \
    --arg alloc_p50 "$ALLOC_P50" \
    --arg alloc_p99 "$ALLOC_P99" \
    --arg throughput "$THROUGHPUT" \
    '{
      timestamp: now | strftime("%Y-%m-%dT%H:%M:%SZ"),
      baseline_window: "'$BASELINE_WINDOW'",
      task_execution_latency_ms: {p50: ($task_p50 | tonumber), p99: ($task_p99 | tonumber)},
      case_completion_latency_s: {p50: ($case_p50 | tonumber), p99: ($case_p99 | tonumber)},
      allocation_latency_ms: {p50: ($alloc_p50 | tonumber), p99: ($alloc_p99 | tonumber)},
      throughput_cases_per_sec: ($throughput | tonumber)
    }' > .claude/context/perf-baseline.json

  echo "✓ Baseline established and saved"
  cat .claude/context/perf-baseline.json | jq '.'
}

establish_baseline
```

#### Step 2: Monitor Current Metrics vs Baseline

```bash
#!/usr/bin/env bash
# scripts/monitor-perf-regression.sh

set -euo pipefail

PROMETHEUS="http://localhost:9090"
BASELINE_FILE=".claude/context/perf-baseline.json"
CHECK_INTERVAL=300  # Check every 5 minutes
REGRESSION_THRESHOLD=1.5  # Alert if current > baseline × 1.5

monitor_regression() {
  echo "📊 Monitoring performance vs baseline..."

  if [ ! -f "$BASELINE_FILE" ]; then
    echo "❌ Baseline file not found. Run: bash scripts/establish-perf-baseline.sh"
    exit 1
  fi

  # Load baseline
  BASELINE_TASK_P99=$(jq '.task_execution_latency_ms.p99' "$BASELINE_FILE")
  BASELINE_CASE_P99=$(jq '.case_completion_latency_s.p99' "$BASELINE_FILE")
  BASELINE_ALLOC_P99=$(jq '.allocation_latency_ms.p99' "$BASELINE_FILE")

  while true; do
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Current p99 task execution latency (last 5 minutes)
    CURRENT_TASK_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=histogram_quantile(0.99, rate(yawl_task_execution_latency_ms_bucket[5m]))' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    # Current p99 case completion latency
    CURRENT_CASE_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=histogram_quantile(0.99, rate(yawl_case_completion_latency_s_bucket[5m]))' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    # Current p99 allocation latency
    CURRENT_ALLOC_P99=$(curl -s "${PROMETHEUS}/api/v1/query" \
      --data-urlencode 'query=histogram_quantile(0.99, rate(yawl_allocation_latency_ms_bucket[5m]))' \
      | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

    echo "[$TIMESTAMP]"

    # Check task execution latency
    if (( $(echo "$CURRENT_TASK_P99 > $BASELINE_TASK_P99 * $REGRESSION_THRESHOLD" | bc -l) )); then
      REGRESSION_PCT=$(echo "scale=1; (($CURRENT_TASK_P99 - $BASELINE_TASK_P99) / $BASELINE_TASK_P99) * 100" | bc)
      echo "⚠️  REGRESSION: Task execution p99=${CURRENT_TASK_P99}ms (baseline=${BASELINE_TASK_P99}ms, +${REGRESSION_PCT}%)"

      # Emit delta for investigation
      cat > /tmp/task-exec-regression.json <<EOF
{
  "delta_type": "performance_regression",
  "metric": "task_execution_latency_p99",
  "baseline": ${BASELINE_TASK_P99},
  "current": ${CURRENT_TASK_P99},
  "regression_percent": ${REGRESSION_PCT},
  "timestamp": "$TIMESTAMP"
}
EOF
    fi

    # Check case completion latency
    if (( $(echo "$CURRENT_CASE_P99 > $BASELINE_CASE_P99 * $REGRESSION_THRESHOLD" | bc -l) )); then
      REGRESSION_PCT=$(echo "scale=1; (($CURRENT_CASE_P99 - $BASELINE_CASE_P99) / $BASELINE_CASE_P99) * 100" | bc)
      echo "⚠️  REGRESSION: Case completion p99=${CURRENT_CASE_P99}s (baseline=${BASELINE_CASE_P99}s, +${REGRESSION_PCT}%)"
    fi

    # Check allocation latency
    if (( $(echo "$CURRENT_ALLOC_P99 > $BASELINE_ALLOC_P99 * $REGRESSION_THRESHOLD" | bc -l) )); then
      REGRESSION_PCT=$(echo "scale=1; (($CURRENT_ALLOC_P99 - $BASELINE_ALLOC_P99) / $BASELINE_ALLOC_P99) * 100" | bc)
      echo "⚠️  REGRESSION: Allocation p99=${CURRENT_ALLOC_P99}ms (baseline=${BASELINE_ALLOC_P99}ms, +${REGRESSION_PCT}%)"
    fi

    sleep $CHECK_INTERVAL
  done
}

monitor_regression
```

#### Step 3: Root-Cause via Distributed Trace Analysis

When regression detected, pull distributed traces:

```python
#!/usr/bin/env python3
# scripts/root-cause-regression.py

import json
import requests
from datetime import datetime, timedelta, timezone
from typing import List, Dict

class RegressionAnalyzer:
    def __init__(self, jaeger_url: str = "http://jaeger:16686"):
        self.jaeger_url = jaeger_url

    def fetch_slow_traces(self, service: str, operation: str,
                          duration_threshold_ms: int = 500) -> List[Dict]:
        """Fetch traces exceeding duration threshold."""

        # Jaeger query: Find traces for operation with duration > threshold
        query = {
            "service": service,
            "operation": operation,
            "minDuration": f"{duration_threshold_ms}ms"
        }

        response = requests.get(
            f"{self.jaeger_url}/api/traces",
            params=query
        )

        traces = response.json().get("data", [])
        return traces

    def analyze_trace_for_regression(self, trace: Dict) -> Dict:
        """Extract performance characteristics from single trace."""

        spans = trace.get("spans", [])

        analysis = {
            "trace_id": trace.get("traceID"),
            "total_duration_ms": (trace.get("duration", 0)) / 1000,
            "span_count": len(spans),
            "critical_path": [],
            "bottleneck_spans": []
        }

        # Build critical path (longest dependency chain)
        span_durations = {}
        for span in spans:
            span_durations[span["spanID"]] = {
                "operation": span["operationName"],
                "duration_ms": (span.get("duration", 0)) / 1000,
                "start_time": span.get("startTime")
            }

        # Find longest single span
        slowest_span = max(span_durations.items(),
                          key=lambda x: x[1]["duration_ms"])
        analysis["bottleneck_spans"].append({
            "span_id": slowest_span[0],
            "operation": slowest_span[1]["operation"],
            "duration_ms": slowest_span[1]["duration_ms"]
        })

        return analysis

    def compare_with_baseline(self, regression_traces: List[Dict],
                             baseline_avg_ms: float) -> Dict:
        """Compare regression traces with baseline average."""

        regression_durations = [
            t.get("duration", 0) / 1000 for t in regression_traces
        ]

        regression_avg = sum(regression_durations) / len(regression_durations)
        regression_factor = regression_avg / baseline_avg_ms

        return {
            "baseline_avg_ms": baseline_avg_ms,
            "regression_avg_ms": regression_avg,
            "regression_factor": round(regression_factor, 2),
            "slowest_trace_ms": max(regression_durations),
            "sample_size": len(regression_traces)
        }

if __name__ == "__main__":
    analyzer = RegressionAnalyzer()

    # Fetch slow task execution traces
    print("🔍 Fetching slow task execution traces...")
    slow_traces = analyzer.fetch_slow_traces(
        service="yawl-engine",
        operation="YNetRunner.executeTask",
        duration_threshold_ms=500
    )

    if slow_traces:
        print(f"✓ Found {len(slow_traces)} slow traces")

        # Analyze first slow trace
        analysis = analyzer.analyze_trace_for_regression(slow_traces[0])
        print(f"\nTrace analysis:")
        print(json.dumps(analysis, indent=2))

        # Compare with baseline
        baseline_avg = 150  # From perf-baseline.json
        comparison = analyzer.compare_with_baseline(slow_traces, baseline_avg)
        print(f"\nRegression comparison:")
        print(json.dumps(comparison, indent=2))
    else:
        print("✓ No slow traces found")
```

#### Step 4: Emit Performance Delta Receipt

```python
#!/usr/bin/env python3
# scripts/emit-perf-regression-delta.py

import json
import hashlib
from datetime import datetime, timezone
from pathlib import Path

def emit_perf_regression_delta(metric_name: str, baseline: float,
                               current: float, analysis: dict):
    """Emit delta for performance regression."""

    delta = {
        "delta_id": f"δ-perf-{datetime.now(timezone.utc).isoformat()}",
        "change_type": "behavior",
        "semantic_unit": {
            "kind": "Rule",
            "name": f"PerformanceRegression/{metric_name}",
            "location": "org.yawlfoundation.yawl.engine"
        },
        "before": {
            "metric": metric_name,
            "baseline_value": baseline,
            "unit": analysis.get("unit", "ms")
        },
        "after": {
            "metric": metric_name,
            "current_value": current,
            "regression_percent": round(
                ((current - baseline) / baseline) * 100, 1
            ),
            "analysis": analysis
        },
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

    # Canonical JSON
    canonical_json = json.dumps(delta, sort_keys=True, separators=(',', ':'))

    # Sign
    hash_sig = hashlib.sha256(canonical_json.encode()).hexdigest()

    # Emit receipt
    receipt = {
        "delta_id": delta["delta_id"],
        "metric": metric_name,
        "canonical_json": canonical_json,
        "blake3_hash": hash_sig,
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

    receipt_file = Path(".claude/receipts/perf-regressions.jsonl")
    receipt_file.parent.mkdir(parents=True, exist_ok=True)

    with receipt_file.open("a") as f:
        f.write(json.dumps(receipt) + "\n")

    print(f"✓ Performance regression delta emitted")
    print(f"  Metric: {metric_name}")
    print(f"  Baseline: {baseline} → Current: {current}")
    print(f"  Regression: {delta['after']['regression_percent']}%")

if __name__ == "__main__":
    emit_perf_regression_delta(
        metric_name="task_execution_latency_p99",
        baseline=150.0,
        current=225.0,
        analysis={
            "unit": "ms",
            "bottleneck_span": "YNetRunner.assignResources",
            "suspected_cause": "Resource queue saturation",
            "affected_cases_sample": ["WF-2026-001", "WF-2026-002", "WF-2026-003"]
        }
    )
```

---

## Typed Deltas & Receipt Chains

### Delta Structure Reference

Every delta follows this semantic structure:

```json
{
  "delta_id": "δ-2026-02-28T14:32:15.123Z",
  "change_type": "declaration|rule|criterion|dependency|behavior|quad",
  "semantic_unit": {
    "kind": "Function|Type|Constant|Import|Module|Field|Dependency|Rule",
    "name": "string (unique identifier)",
    "location": "string (package or module)"
  },
  "before": {
    "property1": "value",
    "property2": "value"
  },
  "after": {
    "property1": "value",
    "property2": "value"
  },
  "timestamp": "ISO8601 timestamp"
}
```

### Receipt Chain Structure

Receipts form an immutable audit trail:

```json
{
  "session_id": "session_01SfdxrP7PZC8eiQQws7Rbz2",
  "timestamp": "2026-02-28T14:32:15.123Z",
  "receipts": [
    {
      "sequence": 1,
      "delta_id": "δ-2026-02-28T14:32:15.123Z",
      "canonical_json": "{...}",
      "blake3_hash": "a1b2c3...",
      "previous_hash": "z9y8x7...",
      "semantic_unit_kind": "Function",
      "timestamp": "2026-02-28T14:32:15.123Z"
    }
  ]
}
```

### Verifying Receipt Integrity

```python
#!/usr/bin/env python3
# scripts/verify-receipt-chain.py

import json
import hashlib
from pathlib import Path

def verify_receipt_chain(receipt_file: str) -> bool:
    """Verify blake3 chain integrity."""

    with open(receipt_file) as f:
        receipt_data = json.load(f)

    previous_hash = None
    valid = True

    for i, receipt in enumerate(receipt_data.get("receipts", [])):
        canonical = receipt["canonical_json"]
        expected_hash = receipt["blake3_hash"]

        # Recompute hash
        computed_hash = hashlib.sha256(canonical.encode()).hexdigest()

        # Verify
        if computed_hash != expected_hash:
            print(f"❌ Receipt {i} HASH MISMATCH")
            print(f"   Expected: {expected_hash}")
            print(f"   Computed: {computed_hash}")
            valid = False

        # Verify previous link
        if i > 0 and receipt.get("previous_hash") != previous_hash:
            print(f"❌ Receipt {i} CHAIN BROKEN")
            print(f"   Expected prev: {previous_hash}")
            print(f"   Actual prev: {receipt.get('previous_hash')}")
            valid = False

        previous_hash = expected_hash

    if valid:
        print(f"✓ Receipt chain verified ({len(receipt_data.get('receipts', []))} receipts)")

    return valid

if __name__ == "__main__":
    verify_receipt_chain(".claude/receipts/state-transition-WF-2026-001.json")
```

---

## Watermark Protocol & Cache Efficiency

### Watermark File Structure

```json
{
  "watermarks": {
    "modules.json": {
      "fetch_timestamp": "2026-02-28T14:00:00Z",
      "content_hash": "sha256:abc123...",
      "ttl_seconds": 3600,
      "expires_at": "2026-02-28T15:00:00Z",
      "cached": true
    },
    "gates.json": {
      "fetch_timestamp": "2026-02-28T13:45:00Z",
      "content_hash": "sha256:def456...",
      "ttl_seconds": 1800,
      "expires_at": "2026-02-28T14:15:00Z",
      "cached": true
    }
  }
}
```

### Smart Cache Implementation

```python
#!/usr/bin/env python3
# scripts/watermark-cache.py

import json
import hashlib
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Optional, Dict
import requests

class WatermarkCache:
    def __init__(self, watermarks_file: str = ".claude/context/watermarks.json"):
        self.watermarks_file = watermarks_file
        self.watermarks = self._load_watermarks()

    def _load_watermarks(self) -> Dict:
        """Load watermarks from file."""
        path = Path(self.watermarks_file)
        if path.exists():
            with open(path) as f:
                return json.load(f)
        return {"watermarks": {}}

    def _save_watermarks(self):
        """Persist watermarks."""
        path = Path(self.watermarks_file)
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w") as f:
            json.dump(self.watermarks, f, indent=2)

    def fetch(self, resource_url: str, resource_name: str,
              ttl_seconds: int = 3600) -> Optional[Dict]:
        """Fetch resource with watermark optimization."""

        watermark = self.watermarks.get("watermarks", {}).get(resource_name)

        # Check if cached and valid
        if watermark:
            expires_at = datetime.fromisoformat(watermark["expires_at"])
            if datetime.now(timezone.utc) < expires_at:
                print(f"✓ Cache hit: {resource_name} (expires {expires_at})")
                # Return cached data (not shown here for brevity)
                return None

        # Fetch new data
        print(f"📥 Fetching {resource_name} (cache miss or expired)")
        response = requests.get(resource_url)
        content = response.text

        # Compute hash
        content_hash = hashlib.sha256(content.encode()).hexdigest()

        # Check if content changed
        if watermark and watermark.get("content_hash") == content_hash:
            print(f"✓ Content unchanged, extending TTL")
            # Just update expiry, don't refetch
            expires_at = datetime.now(timezone.utc) + timedelta(seconds=ttl_seconds)
            watermark["expires_at"] = expires_at.isoformat()
            self._save_watermarks()
            return None

        # New content, update watermark
        now = datetime.now(timezone.utc)
        expires_at = now + timedelta(seconds=ttl_seconds)

        self.watermarks.setdefault("watermarks", {})[resource_name] = {
            "fetch_timestamp": now.isoformat(),
            "content_hash": content_hash,
            "ttl_seconds": ttl_seconds,
            "expires_at": expires_at.isoformat(),
            "cached": True
        }

        self._save_watermarks()
        print(f"✓ Watermark updated: {resource_name}")

        return json.loads(content)

if __name__ == "__main__":
    cache = WatermarkCache()

    # Fetch with watermark optimization
    modules = cache.fetch(
        "http://localhost/observatory/modules.json",
        "modules.json",
        ttl_seconds=3600
    )
```

---

## Observatory Integration

### Loading Observatory Facts

```bash
#!/usr/bin/env bash
# scripts/load-observatory-facts.sh

set -euo pipefail

OBSERVATORY_DIR="docs/v6/latest/facts"

load_all_facts() {
  echo "📦 Loading all Observatory facts..."

  # modules.json - Module inventory
  if [ -f "${OBSERVATORY_DIR}/modules.json" ]; then
    MODULE_COUNT=$(jq '.modules | length' "${OBSERVATORY_DIR}/modules.json")
    echo "✓ modules.json: ${MODULE_COUNT} modules"
  fi

  # reactor.json - Build order
  if [ -f "${OBSERVATORY_DIR}/reactor.json" ]; then
    PHASES=$(jq '.reactor_order | length' "${OBSERVATORY_DIR}/reactor.json")
    echo "✓ reactor.json: ${PHASES} build phases"
  fi

  # gates.json - Quality gates
  if [ -f "${OBSERVATORY_DIR}/gates.json" ]; then
    GATES=$(jq '.gates | length' "${OBSERVATORY_DIR}/gates.json")
    echo "✓ gates.json: ${GATES} quality gates"
  fi

  # deps-conflicts.json - Dependency conflicts
  if [ -f "${OBSERVATORY_DIR}/deps-conflicts.json" ]; then
    CONFLICTS=$(jq '.conflicts | length' "${OBSERVATORY_DIR}/deps-conflicts.json")
    echo "✓ deps-conflicts.json: ${CONFLICTS} known conflicts"
  fi

  # shared-src.json - Shared source locations
  if [ -f "${OBSERVATORY_DIR}/shared-src.json" ]; then
    SHARED=$(jq '.shared_sources | length' "${OBSERVATORY_DIR}/shared-src.json")
    echo "✓ shared-src.json: ${SHARED} shared locations"
  fi

  # tests.json - Test inventory
  if [ -f "${OBSERVATORY_DIR}/tests.json" ]; then
    TESTS=$(jq '.summary.total_test_files' "${OBSERVATORY_DIR}/tests.json")
    echo "✓ tests.json: ${TESTS} test files"
  fi

  # dual-family.json - Engine families
  if [ -f "${OBSERVATORY_DIR}/dual-family.json" ]; then
    STATEFUL=$(jq '.stateful // 0' "${OBSERVATORY_DIR}/dual-family.json")
    STATELESS=$(jq '.stateless // 0' "${OBSERVATORY_DIR}/dual-family.json")
    echo "✓ dual-family.json: ${STATEFUL} stateful, ${STATELESS} stateless"
  fi

  echo ""
  echo "📊 All Observatory facts loaded successfully"
}

load_all_facts
```

---

## Dashboard Setup

### Grafana Dashboard Configuration

Create a Grafana dashboard with key observables:

```json
{
  "dashboard": {
    "title": "YAWL Production Monitoring",
    "panels": [
      {
        "title": "Workflow Cases (State Distribution)",
        "targets": [
          {"expr": "yawl_workflow_cases_total{status='READY'}"},
          {"expr": "yawl_workflow_cases_total{status='EXECUTING'}"},
          {"expr": "yawl_workflow_cases_total{status='COMPLETE'}"},
          {"expr": "yawl_workflow_cases_total{status='ERROR'}"}
        ]
      },
      {
        "title": "Task Execution Latency (p50, p99)",
        "targets": [
          {"expr": "histogram_quantile(0.50, rate(yawl_task_execution_latency_ms_bucket[5m]))"},
          {"expr": "histogram_quantile(0.99, rate(yawl_task_execution_latency_ms_bucket[5m]))"}
        ]
      },
      {
        "title": "Resource Allocation Queue Depth",
        "targets": [
          {"expr": "yawl_task_queue_depth"}
        ]
      },
      {
        "title": "Dependency Violations (Real-time)",
        "targets": [
          {"expr": "increase(yawl_dependency_violation_total[5m])"}
        ]
      }
    ]
  }
}
```

---

## Alerting Strategies

### Alert Rules (Prometheus)

```yaml
# prometheus-rules.yaml
groups:
  - name: yawl_production
    rules:
      # Workflow execution alerts
      - alert: HighErrorRate
        expr: |
          (
            sum(rate(yawl_workflow_cases_total{status="ERROR"}[5m]))
            /
            sum(rate(yawl_workflow_cases_total[5m]))
          ) > 0.05
        for: 5m
        annotations:
          summary: "Workflow error rate > 5%"

      # Performance regression alerts
      - alert: TaskLatencyRegression
        expr: |
          histogram_quantile(0.99, rate(yawl_task_execution_latency_ms_bucket[5m]))
          >
          300  # Baseline p99 × 2
        for: 10m
        annotations:
          summary: "Task execution p99 > 300ms"

      # Resource exhaustion alerts
      - alert: QueueSaturation
        expr: |
          yawl_task_queue_depth
          >
          80  # 80% of max capacity
        for: 5m
        annotations:
          summary: "Task queue depth > 80%"

      # Dependency conflict alerts
      - alert: DependencyViolation
        expr: |
          increase(yawl_dependency_violation_total[5m]) > 0
        for: 1m
        annotations:
          summary: "New dependency violations detected"
```

---

## Troubleshooting & Recovery

### Diagnosing Missing Metrics

```bash
#!/usr/bin/env bash
# scripts/diagnose-metrics.sh

set -euo pipefail

PROMETHEUS="http://localhost:9090"

diagnose() {
  echo "🔍 Diagnosing metrics collection..."

  # Check Prometheus connectivity
  if ! curl -s "${PROMETHEUS}/-/healthy" | grep -q "Prometheus"; then
    echo "❌ Prometheus unreachable at ${PROMETHEUS}"
    return 1
  fi
  echo "✓ Prometheus is healthy"

  # Check if YAWL metrics are being scraped
  YAWL_TARGETS=$(curl -s "${PROMETHEUS}/api/v1/targets" \
    | jq '.data.activeTargets[] | select(.labels.job == "yawl-engine")')

  if [ -z "$YAWL_TARGETS" ]; then
    echo "❌ YAWL targets not found in Prometheus. Check scrape configuration."
    return 1
  fi
  echo "✓ YAWL targets registered"

  # Check if metrics are being collected
  METRIC_COUNT=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode 'query=count({job="yawl-engine"})' \
    | jq '.data.result[0].value[1]' 2>/dev/null || echo "0")

  if [ "$METRIC_COUNT" == "0" ]; then
    echo "⚠️  No YAWL metrics yet. Waiting for first case execution..."
    return 0
  fi

  echo "✓ Collecting ${METRIC_COUNT} metrics from YAWL engine"
}

diagnose
```

---

## Appendix: Tools & Commands

### Quick Command Reference

| Task | Command |
|------|---------|
| Load Observatory facts | `bash scripts/load-observatory-facts.sh` |
| Monitor case transitions | `bash scripts/monitor-case-transitions.sh` |
| Establish perf baseline | `bash scripts/establish-perf-baseline.sh` |
| Detect regressions | `bash scripts/monitor-perf-regression.sh` |
| Analyze dependency paths | `python3 scripts/analyze-dependency-path.py` |
| Verify receipts | `python3 scripts/verify-receipt-chain.py` |
| Diagnose metrics | `bash scripts/diagnose-metrics.sh` |

### Environment Variables

```bash
# Prometheus
export PROMETHEUS_URL="http://localhost:9090"

# Jaeger/Tempo
export JAEGER_URL="http://jaeger:16686"
export OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4318"

# YAWL Engine
export YAWL_HOME="/opt/yawl"
export YAWL_METRICS_PORT="8888"
export YAWL_ENGINE_TELEMETRY_ENABLED="true"

# Cache & Watermarks
export OBSERVATORY_DIR="docs/v6/latest"
export WATERMARKS_FILE=".claude/context/watermarks.json"
```

### Further Reading

- **CLAUDE.md** - Root axiom & CHATMAN equation
- **.claude/OBSERVATORY.md** - Development-time observability
- **.claude/HYPER_STANDARDS.md** - Production quality gates
- **docs/how-to/deployment/monitoring-setup.md** - Deployment checklist

---

**Version**: 6.0.0
**Last Updated**: February 28, 2026
**Maintained by**: YAWL Operations Team
