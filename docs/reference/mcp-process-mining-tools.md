# MCP Process Mining Tools Reference

**Quadrant**: Reference | **Audience**: Developers, AI Agent Engineers

These tools are exposed by `YawlMcpServer` and defined in
`YawlProcessMiningToolSpecifications`. They require the pm4py Python bridge to be
configured — see [OCPM Integration How-To](../how-to/integration/ocpm-integration.md).

> **Prerequisite**: All tools return `PM_UNAVAILABLE` until `ProcessMiningFacade`
> is wired into `YawlProcessMiningToolSpecifications`. See [Part C of the how-to
> guide](../how-to/integration/ocpm-integration.md#part-c--wire-mcp-tools-unblock-pm_unavailable).

---

## Tool Summary

| Tool | Purpose | Required Parameters |
|------|---------|---------------------|
| [`yawl_pm_export_xes`](#yawl_pm_export_xes) | Export event log to XES XML | `specIdentifier` |
| [`yawl_pm_analyze`](#yawl_pm_analyze) | Full analysis (performance + variants + social) | `specIdentifier` |
| [`yawl_pm_performance`](#yawl_pm_performance) | Performance metrics (flow time, throughput) | `specIdentifier` |
| [`yawl_pm_variants`](#yawl_pm_variants) | Process variant discovery, ranked by frequency | `specIdentifier` |
| [`yawl_pm_social_network`](#yawl_pm_social_network) | Resource handover-of-work analysis | `specIdentifier` |

---

## yawl_pm_export_xes

Exports a workflow specification's complete event log to XES (eXtensible Event
Stream, IEEE 5679-2016) format. Use this to feed external tools such as ProM,
pm4py, Celonis, or any RAG pipeline.

### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `specIdentifier` | string | **yes** | — | Workflow specification identifier |
| `specVersion` | string | no | `"0.1"` | Specification version |
| `specUri` | string | no | same as identifier | Specification URI |
| `withData` | string | no | `"false"` | Include work item data attributes (`"true"` / `"false"`) |

### Returns

XES XML string containing all traces for the specification. Each trace corresponds
to one case; each event to one work item lifecycle event.

### Example

```json
{
  "tool": "yawl_pm_export_xes",
  "arguments": {
    "specIdentifier": "OrderProcess",
    "specVersion": "1.0",
    "withData": "false"
  }
}
```

Response (truncated):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<log xes.version="2.0" xmlns="http://www.xes-standard.org/">
  <trace>
    <string key="concept:name" value="case-001"/>
    <event>
      <string key="concept:name" value="Submit"/>
      <string key="lifecycle:transition" value="complete"/>
      <date key="time:timestamp" value="2026-02-25T09:00:00Z"/>
    </event>
    ...
  </trace>
</log>
```

### Notes

- `withData: "true"` significantly increases response size for data-heavy workflows.
- Use the response directly with `pm4py.parse_process_tree(xes_str)` or write to
  a `.xes` file.
- Backed by `EventLogExporter` (Interface E).

---

## yawl_pm_analyze

Runs the comprehensive process mining pipeline: performance metrics, top process
variants, and resource interaction (social network) analysis. Equivalent to calling
`ProcessMiningFacade.analyze(...)` with all analysis types enabled.

### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `specIdentifier` | string | **yes** | — | Workflow specification identifier |
| `specVersion` | string | no | `"0.1"` | Specification version |
| `specUri` | string | no | same as identifier | Specification URI |
| `withData` | string | no | `"false"` | Include data attributes in event log |

### Returns

Formatted text summary containing:
- Case count and time range
- Average flow time (ms and human-readable)
- Throughput (cases per hour)
- Top 5 process variants by frequency
- Top 3 resource handover pairs

### Example

```json
{
  "tool": "yawl_pm_analyze",
  "arguments": {
    "specIdentifier": "OrderProcess"
  }
}
```

Response:
```
Process Mining Report: OrderProcess
Generated: 2026-02-25T14:23:00Z

PERFORMANCE
  Cases analysed: 847
  Avg flow time:  4h 37m
  Throughput:     12.3 cases/hour

TOP VARIANTS
  1. Submit → Approve → Process → Deliver  (612 cases, 72.3%)
  2. Submit → Reject                        (180 cases, 21.3%)
  3. Submit → Approve → Escalate → Process  (55 cases, 6.5%)

SOCIAL NETWORK (top handovers)
  Clerk → Manager: 847 handovers
  Manager → Processor: 667 handovers
  Processor → Clerk: 55 handovers
```

---

## yawl_pm_performance

Analyses performance metrics for a workflow specification without running full
variant or social-network analysis. Faster than `yawl_pm_analyze` for quick
throughput and flow-time dashboards.

### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `specIdentifier` | string | **yes** | — | Workflow specification identifier |
| `specVersion` | string | no | `"0.1"` | Specification version |
| `specUri` | string | no | same as identifier | Specification URI |
| `withData` | string | no | `"false"` | Include data attributes in event log |

### Returns

Formatted performance statistics including:
- Average, minimum, and maximum flow time
- Throughput (cases per hour)
- Activity execution counts
- Average waiting time between consecutive activities

### Example

```json
{
  "tool": "yawl_pm_performance",
  "arguments": {
    "specIdentifier": "InvoiceApproval",
    "specVersion": "2.0"
  }
}
```

Response:
```
Performance Analysis: InvoiceApproval v2.0
Generated: 2026-02-25T14:25:00Z

FLOW TIME
  Average: 3h 12m (11,520,000 ms)
  Minimum: 8m
  Maximum: 6d 4h

THROUGHPUT
  8.7 cases/hour

ACTIVITY COUNTS
  Submit Invoice:  1,203
  Validate:        1,203
  Approve:           987
  Reject:            216
  Process Payment:   987

WAITING TIMES (avg)
  Submit → Validate:  2m 10s
  Validate → Approve: 2h 45m   ← potential bottleneck
  Approve → Payment:  12m
```

### Notes

- Backed by `PerformanceAnalyzer`.
- For bottleneck detection (highest waiting time), inspect the `WAITING TIMES` section.
- For automated bottleneck alerting, use `ProcessMiningFacade.analyzePerformance()`
  directly in Java and compare against thresholds.

---

## yawl_pm_variants

Discovers and ranks process variants (unique activity sequences) in a
specification's event log. Returns the top-N variants sorted by frequency.

### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `specIdentifier` | string | **yes** | — | Workflow specification identifier |
| `specVersion` | string | no | `"0.1"` | Specification version |
| `specUri` | string | no | same as identifier | Specification URI |
| `topN` | string | no | `"10"` | Number of top variants to return |

### Returns

Ranked list of process variants with case count and percentage.

### Example

```json
{
  "tool": "yawl_pm_variants",
  "arguments": {
    "specIdentifier": "LoanApproval",
    "topN": "5"
  }
}
```

Response:
```
Process Variants: LoanApproval (top 5 of 23 total)
Generated: 2026-02-25T14:26:00Z

Variant 1 (61.2%, 734 cases):
  Receive → Verify → Approve → Disburse

Variant 2 (18.4%, 221 cases):
  Receive → Verify → Request Documents → Verify → Approve → Disburse

Variant 3 (12.1%, 145 cases):
  Receive → Verify → Reject

Variant 4 (5.2%, 62 cases):
  Receive → Verify → Approve → Escalate → Approve → Disburse

Variant 5 (3.1%, 37 cases):
  Receive → Reject
```

### Notes

- Backed by `ProcessVariantAnalyzer`.
- Variants are computed from completed cases only (open cases are excluded).
- High variant count (> 50 variants) may indicate under-specified workflow or data
  quality issues in the event log.

---

## yawl_pm_social_network

Analyses resource interaction patterns using handover-of-work analysis: which
resources transfer work to which others, and how often.

### Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `specIdentifier` | string | **yes** | — | Workflow specification identifier |
| `specVersion` | string | no | `"0.1"` | Specification version |
| `specUri` | string | no | same as identifier | Specification URI |
| `withData` | string | no | `"false"` | Include data attributes in event log |

### Returns

Formatted social network analysis including:
- Top handover-of-work pairs (who transfers to whom)
- Workload distribution per resource/role
- Most central resource (highest handover count)

### Example

```json
{
  "tool": "yawl_pm_social_network",
  "arguments": {
    "specIdentifier": "ClaimProcessing"
  }
}
```

Response:
```
Social Network Analysis: ClaimProcessing
Generated: 2026-02-25T14:27:00Z

TOP HANDOVERS (work transferred between resources)
  Intake Clerk → Assessor:    1,203 transfers
  Assessor → Senior Assessor: 412 transfers
  Senior Assessor → Manager:  89 transfers

WORKLOAD DISTRIBUTION
  Intake Clerk:     1,203 tasks (24.4%)
  Assessor:         2,891 tasks (58.7%)
  Senior Assessor:  557 tasks  (11.3%)
  Manager:          278 tasks   (5.6%)

MOST CENTRAL RESOURCE: Assessor (highest total handover volume: 3,303)
```

### Notes

- Backed by `SocialNetworkAnalyzer`.
- Resource identifiers come from YAWL's resourcing service (roles and participants).
- High centralisation around one resource is a single-point-of-failure risk.

---

## Error Responses

All tools return `isError: true` with a descriptive message for these conditions:

| Condition | Message |
|-----------|---------|
| pm4py bridge not configured | `"Process mining requires the pm4py Python bridge which is not available..."` |
| Unknown specification | `"Specification not found: <specId>"` |
| No cases in log | `"No completed cases found for specification <specId>"` |
| Rust4PM unreachable | `"Rust4PM server unreachable at <url>: <cause>"` |
| Engine connection failure | `"Cannot connect to YAWL engine: <cause>"` |

---

## Related

- [OCPM Integration How-To](../how-to/integration/ocpm-integration.md) — deploy
  and configure the pm4py bridge
- [Process Intelligence](../explanation/process-intelligence.md) — how these tools
  fit into the PI architecture
- [Object-Centric Process Mining](../explanation/object-centric-process-mining.md) —
  OCEL 2.0 and OCPM concepts
- [MCP Tools Reference](mcp-tools.md) — all YAWL MCP tools (workflow operations)
- Source: `src/.../integration/mcp/spec/YawlProcessMiningToolSpecifications.java`
