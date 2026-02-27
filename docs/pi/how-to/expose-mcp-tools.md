# How to Expose PI Tools via MCP

`PIToolProvider` exposes four PI capabilities as MCP tools that autonomous agents can call.
This guide shows how to register it with a YAWL MCP server.

---

## The four tools

| Tool name | PI method | Description |
|---|---|---|
| `yawl_pi_predict_risk` | `predictOutcome(caseId)` | Predict case outcome and risk score |
| `yawl_pi_recommend_action` | `recommendActions(caseId, prediction)` | Recommend interventions |
| `yawl_pi_ask` | `ask(request)` | Answer a natural language process question |
| `yawl_pi_prepare_event_data` | `prepareEventData(rawData)` | Convert event log to OCEL2 |

---

## Registering PIToolProvider

```java
import org.yawlfoundation.yawl.pi.mcp.PIToolProvider;
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;

// 1. Build the PI facade (see configure-pi-facade.md)
ProcessIntelligenceFacade facade = buildFacade();

// 2. Create the tool provider
PIToolProvider toolProvider = new PIToolProvider(facade);

// 3. Register with the MCP server
YawlMcpServer mcpServer = new YawlMcpServer();
mcpServer.registerToolProvider(toolProvider);

// 4. Start
mcpServer.start(8080);
```

---

## Tool input/output schemas

### `yawl_pi_predict_risk`

Input:
```json
{
  "caseId": "case-2026-0042"
}
```

Output:
```json
{
  "caseId": "case-2026-0042",
  "completionProbability": 0.73,
  "riskScore": 0.27,
  "primaryRiskFactor": "high task wait time",
  "fromOnnxModel": true,
  "predictedAt": "2026-02-27T14:00:00Z"
}
```

### `yawl_pi_recommend_action`

Input:
```json
{
  "caseId": "case-2026-0042",
  "riskScore": 0.72
}
```

Output:
```json
{
  "actions": [
    {
      "type": "ReallocateResourceAction",
      "workItemId": "credit-check",
      "fromResourceId": "res-bob",
      "toResourceId": "res-alice",
      "expectedImprovementScore": 0.65,
      "rationale": "Resource res-alice has lower average wait time for credit-check"
    },
    {
      "type": "NoOpAction",
      "expectedImprovementScore": 0.0,
      "rationale": "Baseline: no action needed"
    }
  ]
}
```

### `yawl_pi_ask`

Input:
```json
{
  "question": "Which task has the highest average wait time for loan applications?",
  "specificationId": "loan-application/1.0"
}
```

Output:
```json
{
  "answer": "Based on recent events, the credit-check task has the highest average wait time at 8.3 minutes.",
  "sourceFacts": ["credit-check avg wait: 8.3m", "approve-loan avg wait: 2.1m"],
  "groundedInKb": true,
  "llmAvailable": true,
  "responseTimeMs": 340
}
```

### `yawl_pi_prepare_event_data`

Input:
```json
{
  "rawData": "case_id,activity,timestamp\ncase-001,start,2026-02-01T09:00:00Z",
  "format": "csv"
}
```

Output: OCEL2 v2.0 JSON string.

---

## Calling tools from an MCP client

Any MCP-compatible agent or tool caller can invoke these:

```python
# Python MCP client example
import mcp

client = mcp.Client("http://localhost:8080")

result = client.call_tool("yawl_pi_predict_risk", {
    "caseId": "case-2026-0042"
})
print(result["riskScore"])  # 0.27
```

---

## A2A integration via PISkill

For agent-to-agent (A2A) protocol, use `PISkill`:

```java
import org.yawlfoundation.yawl.pi.mcp.PISkill;

PISkill skill = new PISkill(facade);
// Register with your A2A agent framework
a2aAgent.registerSkill("predict", skill.predictSkill());
a2aAgent.registerSkill("recommend", skill.recommendSkill());
a2aAgent.registerSkill("ask", skill.askSkill());
a2aAgent.registerSkill("prepare-event-data", skill.prepareEventDataSkill());
```

---

## Security note

`PIToolProvider` does not enforce authentication. The MCP server or a reverse proxy
should apply API key or JWT validation before requests reach the tool provider.
