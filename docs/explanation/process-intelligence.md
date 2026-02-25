# Process Intelligence

**Quadrant**: Explanation | **Audience**: Architects, AI Engineers, Product Owners

> **See also**: [Object-Centric Process Mining](object-centric-process-mining.md) —
> the OCED/OCEL 2.0 foundation this document builds on.
> [OCPM Integration How-To](../how-to/integration/ocpm-integration.md) — deploy
> the full PI stack.
> [MCP Process Mining Tools](../reference/mcp-process-mining-tools.md) — tool
> reference for autonomous agents.

---

## The "No AI Without PI" Argument

AI in enterprise settings fails when it lacks grounding in the organization's
actual operational data. Unlike text (general-purpose, static, public), process
data is:

- **Structured** — typed events, object relationships, timing constraints
- **Organization-specific** — your order process ≠ anyone else's order process
- **Highly dynamic** — process behaviour changes faster than LLM training cycles

Large Language Models trained on internet text cannot predict that your invoice
approval takes 4.7 days on average, or that Approver Role B is the bottleneck in
Q4, or that switching the task order reduces cycle time by 23%. This knowledge
lives only in your event logs.

**Process Intelligence (PI)** is van der Aalst's term for the combination of
process-centric, data-driven techniques that provide this grounding. PI connects
raw operational data (source systems) through Object-Centric Process Mining (OCPM)
to generative, predictive, and prescriptive AI.

---

## Three AI Types — Mapped to YAWL

### Generative AI + PI

Generative AI (LLMs, RAG pipelines) can produce summaries, recommendations, and
workflow specifications — but only when grounded in live process data.

**Without PI**: "Approval processes typically take 1-3 days" (from training data,
not your data).

**With PI**: "In OrderProcess v1.2, approval averages 4.7 days. The slowest
quartile (> 8 days) involves Resource Role B exclusively. Recommended: add
parallel approval path for Manager role."

In YAWL, the grounding layer is:

```
OCEL 2.0 export  →  RAG context  →  LLM (Claude / GPT)  →  structured answer
(Ocel2Exporter)     (via MCP)        (MCP client)
```

MCP tool: `yawl_pm_export_xes` returns the live XES event log that a RAG
pipeline embeds as context for the LLM.

---

### Predictive AI + PI

Predictive AI learns a function `f: features → outcome` from historical traces.

**Classic example**: remaining time prediction. Given the activities completed so
far in a case, predict how much longer it will take.

pm4py's machine learning module provides this out of the box once fed OCEL 2.0
from YAWL:

```python
import pm4py

# Load OCEL 2.0 from YAWL
ocel = pm4py.read_ocel2("yawl-export.jsonocel")

# Flatten to case-centric for ML feature extraction
log = pm4py.ocel.ocel_flattening(ocel, object_type="case")

# Extract features for remaining time prediction
from pm4py.algo.transformation.log_to_features import algorithm as log_to_feat
data, feature_names = log_to_feat.apply(log)

# Train remaining-time predictor (scikit-learn, XGBoost, etc.)
# ... standard ML pipeline from here ...
```

In YAWL's A2A skill layer (`ProcessMiningSkill`), the `performance` analysis type
returns flow-time and throughput statistics that a downstream predictive model
can consume as features.

MCP tool: `yawl_pm_performance` exposes avg flow time, throughput, and
activity-level waiting times — the signal inputs for predictive models.

---

### Prescriptive AI + PI

Prescriptive AI recommends or enforces actions that optimize a goal given
constraints.

In YAWL, this is the role of the `GregverseSimulator` orchestrator:

```
YEngine log  →  XES export  →  Rust4PM discovery  →  conformance check
                                    ↓
                           fitness < 0.8?  →  feedback: "Model drifting — review task X"
                           avg flow > 3h?  →  feedback: "Bottleneck at Approve — add resource"
```

The `GregverseSimulator` runs end-to-end:

1. Export live XES log via `EventLogExporter`
2. Send to `Rust4PmClient` for Heuristic Miner discovery (`POST /api/v1/discover`)
3. Compare discovered model to specification via `ConformanceAnalyzer`
4. If `fitness < fitnessCriteria` (default 0.8): emit an optimization suggestion
5. Results cached in `ProcessMiningSession` (immutable Java 25 record)
6. MCP tools surface session results to autonomous agents

MCP tool: `yawl_pm_analyze` triggers the full prescriptive feedback loop and
returns a structured text report with specific recommendations.

---

## YAWL's Full PI Stack

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SOURCE SYSTEMS                               │
│   YAWL Engine (YEngine)  +  Resourcing Service  +  Worklet Service  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │  YWorkItem events (completed, failed...)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          OCPM LAYER                                 │
│                                                                     │
│  EventLogExporter ──► XES XML (IEEE 5679-2016)                      │
│  Ocel2Exporter    ──► OCEL 2.0 JSON (.jsonocel)                     │
│  Rust4PmClient    ──► Rust4PM REST (10-40× faster analysis)         │
│                                                                     │
│  ProcessMiningFacade (orchestrates all the above)                   │
│  ProcessMiningSession (durable, immutable Java 25 record)           │
│  GregverseSimulator (discovery → conformance → prescriptive loop)   │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                 ┌─────────────┼─────────────┐
                 ▼             ▼             ▼
┌──────────────────┐ ┌──────────────┐ ┌───────────────────────────┐
│  MCP Tools       │ │  A2A Skills  │ │  Python Bridge (pm4py)    │
│  (5 tools via    │ │  ProcessMining│ │  mcp_server.py (STDIO)    │
│  YawlMcpServer)  │ │  Skill        │ │  a2a_agent.py (HTTP:9092) │
└────────┬─────────┘ └──────┬───────┘ └────────────┬──────────────┘
         │                  │                       │
         └──────────────────┴───────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           AI LAYER                                  │
│                                                                     │
│  Generative AI  ──  RAG over OCEL 2.0 + LLM (Claude, GPT)          │
│  Predictive AI  ──  pm4py ML features → remaining-time models       │
│  Prescriptive AI ─  Rust4PM conformance → optimization suggestions  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Key Components — Quick Reference

| Component | Type | Role in PI |
|-----------|------|------------|
| `EventLogExporter` | Java | Exports XES (generative AI grounding) |
| `Ocel2Exporter` | Java | Exports OCEL 2.0 (OCPM-native grounding) |
| `Rust4PmClient` | Java | HTTP bridge to Rust4PM (high-perf discovery) |
| `ConformanceAnalyzer` | Java | Fitness / precision (prescriptive feedback) |
| `PerformanceAnalyzer` | Java | Flow time, throughput (predictive features) |
| `ProcessMiningFacade` | Java | Orchestrates full pipeline (single entry point) |
| `ProcessMiningSession` | Java record | Durable, immutable result cache |
| `GregverseSimulator` | Java | End-to-end prescriptive loop |
| `pm4py_backend.py` | Python | Discovery, conformance, performance (pm4py) |
| `mcp_server.py` | Python | FastMCP server — exposes 3 pm4py tools |
| `a2a_agent.py` | Python | A2A HTTP server (port 9092) — 3 skills |
| `ProcessMiningSkill` | Java | A2A skill (5 analysis types) |
| `YawlProcessMiningToolSpecifications` | Java | 5 MCP tools (requires pm4py bridge) |

---

## Retrieval-Augmented Generation with YAWL's OCEL 2.0

RAG grounds LLM responses in retrieved process facts. YAWL's OCEL 2.0 export is
the retrieval source. A minimal pattern:

```python
import pm4py, anthropic

# 1. Retrieve live OCEL 2.0 from YAWL MCP tool
#    (yawl_pm_export_xes or direct Ocel2Exporter call)
ocel_json = get_from_yawl_mcp("yawl_pm_export_xes", spec="OrderProcess")

# 2. Compute summary statistics (retrieval)
ocel = pm4py.read_ocel2_from_string(ocel_json)
stats = pm4py.get_event_attributes(ocel)   # activity frequencies, timestamps

# 3. Inject into LLM prompt (augmented generation)
client = anthropic.Anthropic()
response = client.messages.create(
    model="claude-sonnet-4-6",
    messages=[{
        "role": "user",
        "content": f"Given this process data:\n{stats}\n\nWhat is the main bottleneck?"
    }]
)
```

This is the "No AI Without PI" pattern in code: the LLM answer is grounded in
*your* organization's live process data, not generic training knowledge.

---

## Deployment Checklist

- [ ] Rust4PM server running (`RUST4PM_SERVER_URL` set)
- [ ] pm4py Python bridge started (`scripts/pm4py/mcp_server.py` or `a2a_agent.py`)
- [ ] `YawlProcessMiningToolSpecifications` wired to `ProcessMiningFacade`
- [ ] Interface E enabled on YAWL engine (for `EventLogExporter`)
- [ ] OCEL 2.0 export verified: `facade.analyze(specId, net, true).ocelJson()` non-empty

---

## Further Reading

- [Object-Centric Process Mining](object-centric-process-mining.md) — OCPM
  foundations and OCEL 2.0 format
- [OCPM Integration How-To](../how-to/integration/ocpm-integration.md) — step-by-step
  deployment guide
- [MCP Process Mining Tools](../reference/mcp-process-mining-tools.md) — tool
  reference
- [Autonomous Agents](autonomous-agents.md) — how AI agents use YAWL's MCP tools
- [MCP LLM Design](mcp-llm-design.md) — architectural rationale for MCP integration
- [Process Mining Enhancement Plan](process-mining.md) — roadmap for additional
  algorithms and streaming
