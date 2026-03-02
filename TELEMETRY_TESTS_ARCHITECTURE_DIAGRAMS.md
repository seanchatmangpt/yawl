# Multi-Agent Telemetry Tests - Architecture Diagrams & Execution Story

## 1. 5-AGENT ORCHESTRATION ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────────┐
│                  MultiAgentTestOrchestrator                          │
│  (Virtual Thread Executor + CompletableFuture Coordination)         │
└────────────────┬────────────────────────────────────────────────────┘
                 │
    ┌────────────┼────────────┬────────────┬────────────┐
    │            │            │            │            │
    ▼            ▼            ▼            ▼            ▼
┌────────────┐ ┌────────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────────────┐
│   Agent 1  │ │   Agent 2  │ │   Agent 3    │ │   Agent 4  │ │    Agent 5       │
│  Engine    │ │ Stateless  │ │Integration   │ │    A2A     │ │   Autonomous     │
│   Tests    │ │   Tests    │ │    Tests     │ │   Tests    │ │     Tests        │
└─────┬──────┘ └─────┬──────┘ └──────┬───────┘ └─────┬──────┘ └────────┬─────────┘
      │              │               │              │                 │
      │              │               │              │                 │
      ▼              ▼               ▼              ▼                 ▼
    50 tests      35 tests       28 tests       22 tests          18 tests
  Groq: 5K     Groq: 4.2K    OpenAI: 6.8K   Groq: 5.6K      Groq: 8.5K
  tokens        tokens         tokens          tokens          tokens
      │              │               │              │                 │
      └──────────────┼───────────────┼───────────────┼────────────────┘
                     │
        ┌────────────▼────────────┐
        │  TelemetryCollector     │
        │  (Aggregate Metrics)    │
        │                         │
        │ • Token Counts          │
        │ • Concurrency Metrics   │
        │ • Latency Percentiles   │
        │ • Throughput Stats      │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │   AndonMonitor          │
        │  (Alert Enforcement)    │
        │                         │
        │ P0: LLM Gate Check      │
        │ P1: Timeout Detection   │
        │ P2: SLA Breach Alert    │
        │ P3: Informational       │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────────────┐
        │  AggregatedTestResults          │
        │  (Final JSON Report)            │
        │                                 │
        │ Status: GREEN/YELLOW/RED        │
        │ Pass Rate: XX.XX%               │
        │ Total Tokens: XX,XXX            │
        │ Agent Metrics: {...}            │
        └─────────────────────────────────┘
```

## 2. LLM INTEGRATION FLOW

```
┌─────────────────────────────────────────────────────────────────┐
│                   Test Execution Start                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  ANDON P0 Gate  │
                    │  LLM Availability│
                    └────┬───────────┬─┘
                         │           │
                 ┌───────┘           └────────┐
                 │                            │
         ┌───────▼────────┐         ┌─────────▼──────┐
         │  Groq Check    │         │ OpenAI Check   │
         │  (timeout:30s) │         │ (timeout:30s)  │
         └───┬────────┬───┘         └────┬────────┬──┘
             │        │                  │        │
          ✅│        │❌             ✅│        │❌
     Groq  │        │ Groq Dead   OpenAI│      │OpenAI
     Live  │        │              Live │      │Dead
             │        │                  │        │
        ┌────┴─┬──────┴┐            ┌────┴──┬────┴──┐
        │      │       │            │       │       │
        ▼      ▼       ▼            ▼       ▼       ▼
    Both    Groq   Both         Both    OpenAI  Both
     ✅      ⚠️      🔴           ✅       ⚠️      🔴
   GREEN   YELLOW   RED         GREEN    YELLOW  RED
     │       │       │            │        │      │
     │       │       │            │        │      │
     └───────┴───────┴────────┬───┴────────┴──────┘
                              │
                    ┌─────────▼──────────┐
                    │  Both Down? (P0)   │
                    └────┬────────────┬──┘
                        │            │
                        NO          YES
                        │            │
                 ┌──────▼─────┐  ┌───▼──────────────────┐
                 │  Execute   │  │ ANDON P0 CRITICAL    │
                 │  Tests     │  │ "No LLM Available"   │
                 │            │  │ → throw Exception    │
                 └──┬────┬────┘  │ → HALT immediately   │
                    │    │       └────────────────────┘
          ┌─────────┘    └─────────┐
          │                        │
    ┌─────▼──────┐        ┌────────▼──────┐
    │   Agent 1  │        │   Agent 3     │
    │   Agent 2  │        │ (OpenAI only) │
    │   Agent 4  │        └────────┬───────┘
    │   Agent 5  │                 │
    │ (Groq)     │        ┌────────▼─────────┐
    └─────┬──────┘        │ Fallback to Groq?│
          │               │ (if available)    │
    ┌─────▼────────────────────────────────┐
    │  LLM Request Execution               │
    │  • Generate test scenarios (Groq)    │
    │  • Validate patterns (OpenAI)        │
    │  • Count tokens (input + output)     │
    │  • Record in LLMTokenCounter         │
    └─────┬────────────────────────────────┘
          │
    ┌─────▼────────────────────────────┐
    │  Token Aggregation               │
    │  • Groq Total: 28,700 tokens     │
    │  • OpenAI Total: 12,400 tokens   │
    │  • Combined: 41,100 tokens       │
    └────────────────────────────────┘
```

## 3. CONCURRENCY & EXECUTION FLOW

```
Time →

Agent 1 (Engine)       │████████████████████████│ (8-12 min)
                       │  50 tests, 48 passed   │

Agent 2 (Stateless)    │███████████████████│ (6-10 min)
                       │  35 tests, 34 pass │

Agent 3 (Integration)  │██████████████████████████│ (10-15 min)
                       │  28 tests, 27 passed     │

Agent 4 (A2A)          │████████████████████│ (8-12 min)
                       │  22 tests, 21 pass │

Agent 5 (Autonomous)   │██████████████████████│ (10-15 min)
                       │  18 tests, 17 pass │

═════════════════════════════════════════════════════════
Wall-clock: 5-7 seconds (parallel execution via virtual threads)
═════════════════════════════════════════════════════════

Total: 153 tests, 151 passed (98.7% pass rate)
```

## 4. ANDON ALERT HIERARCHY

```
                    ┌──────────────────┐
                    │  ANDON Monitor   │
                    └────────┬─────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
    ┌────────────┐      ┌──────────┐       ┌───────────┐
    │ P0_CRITICAL│      │ P1_HIGH  │       │ P2_MEDIUM │
    │    STOP    │      │  HALT    │       │  ALERT    │
    └─────┬──────┘      └────┬─────┘       └─────┬─────┘
          │                  │                   │
          │                  │                   │
    ┌─────▼──────────────────▼─────────────────┬─▼────────────┐
    │                                          │              │
    │ Triggers:                                │              │
    │ • No LLM                                 │              │
    │ • Both APIs down                         │              │
    │ • Network unreachable                    │              │
    │                                          │              │
    │ Action:                                  │              │
    │ throw IllegalStateException              │              │
    │ "ANDON P0: No LLM Available"             │              │
    │ EXIT CODE: 1                             │              │
    │                                          │              │
    └──────────────────────────────────────────┴──────────────┘

    P1 Triggers:                        P2 Triggers:
    • Agent timeout >30min              • Pass rate <95%
    • Deadlock detected                 • Latency >2× baseline
    • Memory exhausted                  • Queue depth anomaly
    • Exception in agent                • Throughput drop

    Action: Alert + continue            Action: Record + continue
```

## 5. METRICS COLLECTION PIPELINE

```
┌──────────────────────────────────────────────────────────────┐
│              Agent Test Execution                            │
└────────────┬──────────────────┬──────────────┬──────────────┘
             │                  │              │
             ▼                  ▼              ▼
        ┌────────┐         ┌─────────┐   ┌──────────┐
        │ Tests  │         │   LLM   │   │ Hardware │
        │ Run    │         │  Calls  │   │ Monitor  │
        │ 50     │         │ Groq:5K │   │ Threads: │
        │        │         │ tokens  │   │   16     │
        └───┬────┘         └────┬────┘   └────┬─────┘
            │                   │             │
            │                   │             │
    ┌───────▼─────────┬─────────▼───────┬────▼──────────┐
    │                 │                 │               │
    │     Test        │    LLM Token    │  Concurrency  │
    │    Counts       │     Counter     │    Tracker    │
    │                 │                 │               │
    │ • 50 run        │ • Groq: 5,000   │ • Peak: 16    │
    │ • 48 passed     │ • OpenAI: 0     │ • Avg: 8      │
    │ • 2 failed      │                 │ • Queue: 3    │
    └─────┬───────────┴────────┬────────┴───────┬───────┘
          │                    │                │
          │                    │                │
          └────────────┬───────┴────────┬───────┘
                       │                │
                   ┌───▼────────────────▼──┐
                   │ TelemetryCollector    │
                   │                       │
                   │ • Aggregate counts    │
                   │ • Sum tokens          │
                   │ • Calc percentiles    │
                   │ • Record gauges       │
                   └───┬──────────────┬────┘
                       │              │
                ┌──────▼──┐    ┌──────▼──┐
                │OpenTel  │    │Metrics  │
                │Registry │    │Store    │
                └─────────┘    └─────────┘
```

## 6. FINAL RESULTS JSON STRUCTURE

```json
{
  "test_run_id": "telemetry-2026-03-02T14:32:15Z",
  "timestamp": "2026-03-02T14:32:15Z",
  "status": "GREEN",
  "total_duration_ms": 7500,

  "tests": {
    "total": 153,
    "passed": 151,
    "failed": 2,
    "pass_rate_percent": 98.69
  },

  "tokens": {
    "groq": {
      "total": 28700,
      "agents": {
        "engine-tests": 5000,
        "stateless-tests": 4200,
        "a2a-tests": 5600,
        "autonomous-tests": 8500
      }
    },
    "openai": {
      "total": 12400,
      "agents": {
        "integration-tests": 6800
      }
    },
    "combined": 41100
  },

  "concurrency": {
    "peak_virtual_threads": 128,
    "peak_agents_running": 5,
    "avg_queue_depth": 2.3
  },

  "latency": {
    "p50_ms": 52.1,
    "p95_ms": 142.6,
    "p99_ms": 248.9
  },

  "throughput": {
    "tests_per_second": 176.9,
    "agents_average": 156.4
  },

  "agents": {
    "engine-tests": {
      "tests_run": 50,
      "passed": 48,
      "failed": 2,
      "tokens_groq": 5000,
      "throughput": 250.5
    },
    "stateless-tests": {
      "tests_run": 35,
      "passed": 34,
      "failed": 1,
      "tokens_groq": 4200,
      "throughput": 280.3
    },
    "integration-tests": {
      "tests_run": 28,
      "passed": 27,
      "failed": 1,
      "tokens_openai": 6800,
      "throughput": 156.4
    },
    "a2a-tests": {
      "tests_run": 22,
      "passed": 21,
      "failed": 1,
      "tokens_groq": 5600,
      "throughput": 176.9
    },
    "autonomous-tests": {
      "tests_run": 18,
      "passed": 17,
      "failed": 1,
      "tokens_groq": 8500,
      "throughput": 124.2
    }
  },

  "andon_violations": []
}
```

## 7. EXECUTION SEQUENCE DIAGRAM

```
Orchestrator          LLM Gate            Agent Executor        TelemetryCollector
    │                   │                      │                     │
    ├─ Check LLM Avail──┤                      │                     │
    │                   │                      │                     │
    │  ✓ Groq & OpenAI  │                      │                     │
    │ <─────────────────┤                      │                     │
    │                   │                      │                     │
    ├─ Submit 5 agents──────────────────────┬──┤                     │
    │                   │                  │  │                     │
    │                   │          ┌────────┘  │                     │
    │                   │          │           │                     │
    │                   │      ┌───▼─────┐     │                     │
    │                   │      │ Groq    │     │                     │
    │                   │      │ Request │     │                     │
    │                   │      └───┬─────┘     │                     │
    │                   │          │           │                     │
    │                   │      ┌───▼─────────┐ │                     │
    │                   │      │ Agent exec  │ │                     │
    │                   │      │ 50 tests... │ │                     │
    │                   │      └───┬─────────┘ │                     │
    │                   │          │           │                     │
    │                   │      ┌───▼──────────────────────────────┐  │
    │                   │      │ Collect telemetry               │  │
    │                   │      │ • 48/50 passed                  │  │
    │                   │      │ • 5000 tokens                   │  │
    │                   │      │ • p99 latency: 245ms            │  │
    │                   │      │ • throughput: 250 tests/sec     │  │
    │                   │      └───┬──────────────────────────────┘  │
    │                   │          │                                │
    │                   │          └────────────────────────────────┤
    │                   │                                          │
    │                   │      ┌─ Aggregate all 5 agents ────────┐ │
    │                   │      │ • Total: 153 tests              │ │
    │                   │      │ • Pass: 151 (98.7%)             │ │
    │                   │      │ • Tokens: 41,100                │ │
    │                   │      │ • Status: GREEN                 │ │
    │                   │      └────────────────────────────────┘ │
    │                   │                                          │
    ├─ awaitCompletion──┼──────────────────────────────────────────┤
    │                   │                                          │
    │ ✓ Done                                                        │
    │ <─────────────────────────────────────────────────────────────
    │
    └─ Return Results
```

## 8. EXECUTION SUCCESS PATH

```
START
  │
  ├─► Environment Check
  │   ├─ Java 25 ✓
  │   ├─ Maven ✓
  │   └─ Virtual Threads ✓
  │
  ├─► ANDON P0 Gate
  │   ├─ Groq API: UP ✓
  │   ├─ OpenAI API: UP ✓
  │   └─ Proceed? YES ✓
  │
  ├─► Spawn 5 Agents (Virtual Threads)
  │   ├─ Agent 1 (Engine) .................... [████████] 8-12 min
  │   ├─ Agent 2 (Stateless) ................ [███████] 6-10 min
  │   ├─ Agent 3 (Integration) ............. [██████████] 10-15 min
  │   ├─ Agent 4 (A2A) ...................... [████████] 8-12 min
  │   └─ Agent 5 (Autonomous) .............. [█████████] 10-15 min
  │
  ├─► Wait for Completion (5-7 sec wall-clock)
  │   └─ CompletableFuture.allOf() ✓
  │
  ├─► Collect Telemetry
  │   ├─ Test Counts: 153 total, 151 passed ✓
  │   ├─ Token Counts: 41,100 total ✓
  │   ├─ Concurrency: 128 peak threads ✓
  │   ├─ Latency: p50/p95/p99 ✓
  │   └─ Throughput: 156.4 tests/sec ✓
  │
  ├─► ANDON Violation Check
  │   └─ No violations ✓
  │
  ├─► Generate Report
  │   ├─ Status: GREEN ✓
  │   ├─ Pass Rate: 98.69% ✓
  │   └─ JSON Serialization ✓
  │
  └─► SUCCESS
      Result: AggregatedTestResults
      Status: GREEN (151/153 passed, 98.69%)
      Tokens: 41,100 (Groq: 28.7K, OpenAI: 12.4K)
```

---

This maps the complete architecture, LLM flow, ANDON gates, metrics collection, and execution sequence for the multi-agent telemetry test framework.
