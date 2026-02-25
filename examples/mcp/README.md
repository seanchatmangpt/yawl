# YAWL MCP Server — Quickstart Guide

Connect any MCP-compatible AI client (Claude Desktop, Cursor, etc.) to the
YAWL workflow engine via the Model Context Protocol.

## Prerequisites

1. **Java 25+** installed
2. **YAWL Engine** running at `http://localhost:8080/yawl` (or Docker: `docker-compose up`)
3. **YAWL built**: `mvn clean package -DskipTests`

## Setup

Copy `claude_desktop_config.json` to your MCP client config directory and
update the classpath to point to your built YAWL jar:

```bash
# Claude Desktop (macOS)
cp claude_desktop_config.json ~/Library/Application\ Support/Claude/claude_desktop_config.json

# Claude Desktop (Linux)
cp claude_desktop_config.json ~/.config/Claude/claude_desktop_config.json
```

Edit the `args` array to set the correct path to your YAWL jar.

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `YAWL_ENGINE_URL` | Yes | Base URL of YAWL engine |
| `YAWL_USERNAME` | Yes | Admin username |
| `YAWL_PASSWORD` | Yes | Admin password |
| `ZAI_API_KEY` | No | Z.AI API key for AI-powered spec synthesis |

## Worked Examples

### Example 1: Launch and Monitor a Workflow

```
User: Upload the OrderFulfillment spec and launch a case

Assistant uses: yawl_upload_specification, yawl_launch_case

User: What's the status of the case?

Assistant uses: yawl_get_case_status, yawl_get_work_items_for_case

User: Complete the first work item with approved=true

Assistant uses: yawl_checkout_work_item, yawl_checkin_work_item
```

### Example 2: Synthesize a Workflow from a Pattern

```
User: Create a parallel approval workflow where 3 reviewers
      check a document simultaneously, then a final approver signs off.

Assistant uses: yawl_synthesize_from_pattern
  → pattern: "parallel"
  → tasks: ["Review_Security", "Review_Legal", "Review_Technical"]

Then: yawl_upload_specification to deploy the generated YAWL XML
```

### Example 3: Analyze with Process Mining

```
User: Show me the performance metrics for the OrderFulfillment spec

Assistant uses: yawl_pm_performance
  → specIdentifier: "OrderFulfillment"
  → specVersion: "0.1"

User: Are there any process variants?

Assistant uses: yawl_pm_variants
  → specIdentifier: "OrderFulfillment"
  → specVersion: "0.1"
  → topN: 5
```

### Example 4: Self-Optimizing Reactor

```
User: Check the reactor status — is it detecting any drift?

Assistant uses: yawl_reactor_status
  → Returns last 5 cycles with drift detection, mutations proposed

User: Trigger a manual reactor cycle

Assistant uses: yawl_reactor_trigger
  → Runs one optimization cycle, returns metrics and proposed mutation
```

### Example 5: Agent Conscience and Compliance

```
User: Record that I chose to route this case to the fast-track queue

Assistant uses: yawl_publish_decision
  → agentId: "routing-agent-1"
  → taskType: "routing"
  → choiceKey: "fast-track"
  → rationale: "Low complexity case, SLA target met"
  → confidence: 0.85

User: Generate a compliance report for all decisions

Assistant uses: yawl_compliance_report
  → confidenceThreshold: 0.5
```

## Available Tools (12 Blue-Ocean Innovation Tools)

### Process Mining (5 tools)
- `yawl_pm_export_xes` — Export event log to XES format
- `yawl_pm_analyze` — Comprehensive process mining analysis
- `yawl_pm_performance` — Performance metrics (flow time, throughput)
- `yawl_pm_variants` — Process variant discovery
- `yawl_pm_social_network` — Resource interaction analysis

### Reactor (2 tools)
- `yawl_reactor_status` — View last 5 optimization cycles
- `yawl_reactor_trigger` — Manually trigger optimization cycle

### Conscience (4 tools)
- `yawl_publish_decision` — Record agent decision to RDF graph
- `yawl_recall_similar_decisions` — Query past decisions by type
- `yawl_explain_routing` — Audit routing decisions by agent
- `yawl_compliance_report` — Generate compliance analytics

### Pattern Synthesis (1 tool)
- `yawl_synthesize_from_pattern` — Generate YAWL XML from WCP patterns
