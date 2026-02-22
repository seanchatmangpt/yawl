# YAWL Integrations Marketplace

**Status**: MVP Phase (4-week implementation)
**Version**: 0.1.0
**Last Updated**: 2026-02-21

---

## Overview

The Integrations Marketplace enables YAWL skills to be exposed as connectors to external systems:

- **Z.AI Connectors** — Skills as tools for Z.AI autonomous agents (MCP-based)
- **A2A Adapters** — Agent-to-agent protocol handlers for cross-org handoffs
- **MCP Tools** — LLM-native skill invocation (Claude, Gemini, etc.)

Each connector is a YAML manifest that describes how to invoke a skill through a specific protocol.

---

## Registry Structure

```
.integrations/
├─ README.md                          # This file
├─ connector-schema.json              # JSON Schema for validation
├─ connectors.yaml                    # Master registry (auto-generated)
├─ z-ai/                              # Z.AI connectors (MCP tools)
│  ├─ approval-skill.yaml
│  ├─ expense-report-skill.yaml
│  └─ ...
├─ a2a/                               # A2A adapters (agent handoff)
│  ├─ purchase-order-skill.yaml
│  ├─ invoice-skill.yaml
│  └─ ...
└─ mcp/                               # MCP tools (Claude, etc.)
   ├─ expense-report.yaml
   ├─ case-monitor.yaml
   └─ ...
```

---

## Connector YAML Format

Each connector is a YAML file with the following structure:

```yaml
# Required: Unique connector identifier
id: approval_skill

# Required: Human-readable name
name: "Approval Skill"

# Required: Connector type
# Enum: z-ai, a2a, mcp
type: a2a

# Required: Connector version (semantic versioning)
version: "1.0.0"

# Required: Description of what this connector does
description: >
  Handles approval workflows for purchase orders,
  invoices, and expense reports.

# Required: Reference to the skill implementation
skill:
  # Canonical Java class name
  class: "org.yawlfoundation.yawl.integration.a2a.skills.ApprovalSkill"

  # Required permissions to execute this skill
  permissions:
    - "approvals:read"
    - "approvals:write"

# Connector-specific configuration
config:
  # A2A adapter settings
  handoff_protocol: "v1"
  timeout_seconds: 300

  # Message routing
  correlation_id_header: "X-Correlation-ID"
  idempotency_key_header: "X-Idempotency-Key"

# Capability mapping (how external systems invoke the skill)
capabilities:
  - name: "Approve PO"
    description: "Approve a purchase order"

    # Which skill method to call
    skill_method: "execute"

    # Map input: JSONPath from external system → SkillRequest parameter
    input_mapping:
      po_number: "$.parts[0].content.po_number"
      approver_id: "$.context.agent_id"
      approval_reason: "$.parts[0].content.reason"

    # Map output: SkillResult field → JSONPath in external response
    output_mapping:
      status: "$.status"
      case_id: "$.parameters.case_id"
      approved_at: "$.created_at"

# Metadata for skill marketplace
metadata:
  owner: "procurement-team"
  sla_ms: 5000          # Target execution time (milliseconds)
  success_rate: 0.99    # Historical success rate (0-1)
  last_updated: "2026-02-21T10:30:00Z"
```

---

## Connector Types

### Z.AI Connector

Exposes a skill as an MCP tool for Z.AI autonomous agents.

**Example**:
```yaml
type: z-ai
skill:
  class: "org.yawlfoundation.yawl.integration.a2a.skills.ApprovalSkill"
  permissions: ["approvals:write"]
```

**What happens**:
1. Z.AI agent discovers tool via MCP
2. Z.AI calls tool with arguments
3. ConnectorRegistry maps MCP args → SkillRequest
4. ApprovalSkill.execute() runs
5. SkillResult serialized back to MCP format

**File location**: `.integrations/z-ai/{skill-name}.yaml`

---

### A2A Adapter

Enables skill execution via agent-to-agent protocol.

**Example**:
```yaml
type: a2a
skill:
  class: "org.yawlfoundation.yawl.integration.a2a.skills.PurchaseOrderSkill"
  permissions: ["po:write"]

config:
  handoff_protocol: "v1"
  correlation_id_header: "X-Correlation-ID"

capabilities:
  - name: "Launch PO Workflow"
    input_mapping:
      po_number: "$.parts[0].content.po_number"
    output_mapping:
      case_id: "$.parameters.case_id"
```

**What happens**:
1. Peer A2A agent sends message to YawlA2AServer
2. A2AProtocolAdapter translates A2A message → SkillRequest
3. Skill executes
4. SkillResult translated back to A2A message
5. Response sent to peer agent

**File location**: `.integrations/a2a/{skill-name}.yaml`

---

### MCP Tool

Registers skill as MCP tool, enabling Claude and other LLMs to call it.

**Example**:
```yaml
type: mcp
skill:
  class: "org.yawlfoundation.yawl.integration.a2a.skills.CaseMonitoringSkill"
  permissions: ["cases:read"]
```

**What happens**:
1. Claude requests available tools from MCP server
2. MCPToolRegistrar discovers @MCPTool annotated skill
3. Tool schema auto-generated from SkillRequest/SkillResult
4. Claude calls tool with arguments
5. MCP server invokes skill, returns result

**File location**: `.integrations/mcp/{skill-name}.yaml`

---

## Discovery API

### List All Connectors

```bash
curl http://localhost:8080/integrations/connectors
```

**Response** (200 OK):
```json
[
  {
    "id": "approval_skill",
    "name": "Approval Skill",
    "type": "a2a",
    "skill_class": "org.yawlfoundation.yawl.integration.a2a.skills.ApprovalSkill",
    "permissions": ["approvals:write"]
  },
  ...
]
```

### Filter by Type

```bash
curl 'http://localhost:8080/integrations/connectors?type=z-ai'
```

### Search by Skill Name

```bash
curl 'http://localhost:8080/integrations/connectors/search?skill=approval'
```

### Get Single Connector

```bash
curl http://localhost:8080/integrations/connectors/approval_skill
```

**Response** (200 OK):
```json
{
  "id": "approval_skill",
  "name": "Approval Skill",
  "type": "a2a",
  "version": "1.0.0",
  "description": "Handles approval workflows...",
  "skill_class": "org.yawlfoundation.yawl.integration.a2a.skills.ApprovalSkill",
  "permissions": ["approvals:write"],
  "capabilities": [
    {
      "name": "Approve PO",
      "description": "Approve a purchase order"
    }
  ],
  "metadata": {
    "owner": "procurement-team",
    "sla_ms": 5000,
    "success_rate": 0.99
  }
}
```

### Get MCP Tools (For Claude)

```bash
curl http://localhost:8080/integrations/mcp-tools
```

**Response** (200 OK, MCP format):
```json
[
  {
    "name": "submit_expense_report",
    "description": "Submit an expense report for reimbursement",
    "inputSchema": { ... JSON Schema ... },
    "outputSchema": { ... JSON Schema ... }
  },
  ...
]
```

---

## Creating a New Connector

### Step 1: Implement the Skill

Create a Java class implementing `A2ASkill`:

```java
package org.yawlfoundation.yawl.integration.a2a.skills;

import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;

public class MyCustomSkill implements A2ASkill {
  @Override
  public String getId() { return "my_custom_skill"; }

  @Override
  public String getName() { return "My Custom Skill"; }

  @Override
  public String getDescription() {
    return "Does something useful";
  }

  @Override
  public Set<String> getRequiredPermissions() {
    return Set.of("custom:execute");
  }

  @Override
  public SkillResult execute(SkillRequest request) {
    // Real implementation
    return new SkillResult(true, Map.of("result", "success"));
  }
}
```

### Step 2: Register as MCP Tool (Optional)

Add `@MCPTool` annotation if you want Claude to use it:

```java
import org.yawlfoundation.yawl.integration.connector.MCPTool;

@MCPTool(
  name = "my_custom_tool",
  description = "Does something useful for Claude"
)
public class MyCustomSkill implements A2ASkill {
  // ... implementation
}
```

### Step 3: Create Connector YAML

Create `.integrations/{type}/{skill-id}.yaml`:

```yaml
id: my_custom_skill
name: "My Custom Skill"
type: a2a                    # or: z-ai, mcp
version: "1.0.0"
description: "Does something useful"

skill:
  class: "org.yawlfoundation.yawl.integration.a2a.skills.MyCustomSkill"
  permissions:
    - "custom:execute"

config:
  handoff_protocol: "v1"
  timeout_seconds: 300

capabilities:
  - name: "Execute Custom Action"
    skill_method: "execute"
    input_mapping:
      param1: "$.parts[0].content.param1"
    output_mapping:
      result: "$.parameters.result"

metadata:
  owner: "your-team"
  sla_ms: 5000
  success_rate: 1.0
  last_updated: "2026-02-21T00:00:00Z"
```

### Step 4: Test

Run integration tests to verify:

```bash
mvn test -Dtest=*SkillIntegrationTest
```

### Step 5: Commit

```bash
git add .integrations/a2a/my_custom_skill.yaml
git add src/org/yawlfoundation/yawl/integration/a2a/skills/MyCustomSkill.java
git commit -m "Add MyCustomSkill connector"
```

**Note**: Registry auto-reloads on commit via post-commit hook.

---

## Best Practices

### 1. Idempotency for Z.AI

Z.AI may retry failed tool invocations. Ensure your skill is idempotent:

```java
@Override
public SkillResult execute(SkillRequest request) {
  String idempotencyKey = (String) request.parameters().get("idempotency_key");

  // Check if we've already processed this key
  Optional<SkillResult> cached = idempotencyCache.get(idempotencyKey);
  if (cached.isPresent()) {
    return cached.get();  // Return same result
  }

  // Execute the real action
  SkillResult result = performAction(request.parameters());

  // Cache for future retries
  idempotencyCache.put(idempotencyKey, result);

  return result;
}
```

### 2. Correlation IDs for A2A

Preserve correlation IDs for request chaining:

```java
@Override
public SkillResult execute(SkillRequest request) {
  String correlationId = (String) request.parameters().get("correlation_id");

  // Log with correlation ID
  logger.info("Executing skill {}", request.skillId(),
    MDC.put("correlation_id", correlationId));

  // All downstream calls inherit correlation ID
  // (via ScopedValue in Java 25)
  return performAction(request.parameters());
}
```

### 3. Permission Checks

Always verify permissions before executing:

```java
@Override
public SkillResult execute(SkillRequest request) {
  Set<String> callerPermissions = request.authenticatedPrincipal()
    .permissions();

  if (!canExecute(callerPermissions)) {
    return new SkillResult(false,
      Map.of("error", "Insufficient permissions"));
  }

  // Safe to execute
  return performAction(request.parameters());
}
```

### 4. Timeout Handling

Set reasonable timeouts in connector YAML:

```yaml
config:
  timeout_seconds: 300  # 5 minutes max
```

Skill should fail if operation takes longer:

```java
@Override
public SkillResult execute(SkillRequest request) {
  try {
    return performActionWithTimeout(300, TimeUnit.SECONDS);
  } catch (TimeoutException e) {
    return new SkillResult(false,
      Map.of("error", "Operation timeout"));
  }
}
```

### 5. Error Classification

Return proper error codes for retry logic:

```java
// Transient error (safe to retry)
new SkillResult(false,
  Map.of("error", "Temporary database unavailable"))
  .withErrorCode("TRANSIENT_FAILURE");

// Permanent error (don't retry)
new SkillResult(false,
  Map.of("error", "Invalid workflow specification"))
  .withErrorCode("PERMANENT_FAILURE");
```

---

## Troubleshooting

### Connector Not Discovered

1. Check connector YAML syntax:
   ```bash
   cat .integrations/a2a/your-skill.yaml | yamllint -
   ```

2. Validate against schema:
   ```bash
   curl -X POST http://localhost:8080/integrations/validate \
     -H "Content-Type: application/yaml" \
     -d @.integrations/a2a/your-skill.yaml
   ```

3. Check registry logs:
   ```bash
   grep "ConnectorRegistry" logs/application.log | tail -20
   ```

### Skill Execution Failed

1. Check skill logs:
   ```bash
   grep "execute" logs/application.log | tail -20
   ```

2. Verify permissions:
   ```bash
   curl 'http://localhost:8080/integrations/connectors/{id}' | jq '.permissions'
   ```

3. Test skill directly (unit test):
   ```bash
   mvn test -Dtest={SkillClass}Test
   ```

### Cache Not Updating

Registry caches connectors for 5 minutes. Force reload:

```bash
curl -X POST http://localhost:8080/integrations/reload
```

Or commit a change to `.integrations/` (triggers post-commit hook).

---

## Registry Metrics

Monitor registry health:

```bash
curl http://localhost:8080/integrations/health
```

**Response**:
```json
{
  "status": "UP",
  "connectors_loaded": 5,
  "last_reload": "2026-02-21T14:30:00Z",
  "cache_hit_rate": 0.95,
  "discovery_api_latency_ms": 45
}
```

---

## Phase 2 Features (Not in MVP)

- OAuth credential management
- Webhook callbacks for async results
- Real-time reputation scoring
- Connector marketplace UI
- Advanced filtering (by tag, owner, sla, etc.)

---

## Reference

- Design: `INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md`
- Z.AI integration: `Z.AI-YAWL-INTEGRATION-DESIGN.md`
- A2A protocol: `A2A_PROTOCOL_RESEARCH.md`
- MCP tools: `MCP_LLM_TOOL_DESIGN.md`

