# Model Context Protocol (MCP) Server Design for LLM Tool Use via YAWL
## v1.0 — Exposing Workflows as Callable Tools for AI Assistants

**Document Date**: 2026-02-21
**YAWL Version**: 6.0.0
**MCP Specification**: 2025-11-25 (Official Java SDK v1.0.0-RC1)
**Target Audience**: Enterprise architects, LLM platform engineers, workflow automation specialists

---

## Executive Summary

This document designs an **MCP (Model Context Protocol) server** that exposes YAWL workflows as callable tools for large language models (Claude, ChatGPT, Gemini, Llama, etc.), enabling autonomous agents to orchestrate business processes through natural language.

**Key Deliverables**:
1. MCP Server Architecture (tool registration, schema definition, protocol handling)
2. Workflow-as-Tools Pattern (expose workflows as statically-typed callable tools)
3. LLM Interaction Patterns (sequential calls, streaming, error recovery)
4. Security & Observability (rate limiting, input validation, tracing)
5. Prompt Engineering Guide (how to write effective LLM prompts)
6. PoC: Supply Chain Automation (autonomous agent coordinates vendor selection → PO → compliance check)

---

## Part 1: MCP Server Architecture

### 1.1 Overview

The **Model Context Protocol (MCP)** is a standardized interface for LLMs to call external tools. An MCP server exposes:

```
┌─────────────────────────────────────────────────────────────┐
│ Claude / ChatGPT / Gemini / Local LLM                       │
└────────────────────┬────────────────────────────────────────┘
                     │ MCP Protocol (STDIO/HTTP)
                     │ {"tool": "approval_workflow", "args": {...}}
                     ▼
┌──────────────────────────────────────────────────────────┐
│ YAWL MCP Server (YawlMcpServer)                          │
│                                                          │
│ Tools:        Launch Cases, Complete Tasks, Monitor     │
│ Resources:    Specs, Cases, Work Items (read-only)      │
│ Prompts:      Analysis, Troubleshooting Guides           │
│ Completions:  Auto-suggest task IDs, case IDs           │
│                                                          │
│ Transport: STDIO (local) or HTTP (SSE, production)       │
└────────────────────┬─────────────────────────────────────┘
                     │ REST/XML
                     ▼
┌──────────────────────────────────────────────────────────┐
│ YAWL Engine (Workflow Runtime)                           │
│                                                          │
│ - YWorkflowState (ENABLED, EXECUTING, COMPLETED)         │
│ - YCase (case ID, variables, work items)                 │
│ - YWorkItem (task instance with input/output)            │
│                                                          │
│ InterfaceB: Runtime operations (launch, execute)         │
│ InterfaceA: Design-time operations (load specs)          │
└──────────────────────────────────────────────────────────┘
```

### 1.2 Tool Registration & Schema Definition

**Tools** are callable functions exposed to the LLM. Each tool has:
- **Name** (camelCase, `approval_workflow`)
- **Description** (explain what it does, constraints)
- **Input Schema** (JSON Schema v2020-12, required args, types, constraints)
- **Handler** (code that executes when LLM invokes the tool)

#### Example: Approval Workflow Tool

```json
{
  "name": "approval_workflow",
  "description": "Route purchase request through approval workflow. Returns approval decision, time elapsed, and notes.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "applicant_id": {
        "type": "string",
        "description": "Employee ID requesting approval (e.g., 'emp-12345')"
      },
      "amount": {
        "type": "number",
        "description": "Requested amount in USD (must be > 0)",
        "minimum": 0.01,
        "maximum": 1000000
      },
      "justification": {
        "type": "string",
        "description": "Business reason for request (max 500 chars)",
        "maxLength": 500
      },
      "deadline_hours": {
        "type": "integer",
        "description": "Time limit for approval (1-72 hours, default 24)",
        "minimum": 1,
        "maximum": 72,
        "default": 24
      }
    },
    "required": ["applicant_id", "amount", "justification"]
  }
}
```

#### Tool Handler (Java Implementation)

```java
private static McpServerFeatures.SyncToolSpecification createApprovalWorkflowTool(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String sessionHandle) {

    Map<String, Object> props = new LinkedHashMap<>();
    // ... schema definition above ...

    McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
        "object", props, List.of("applicant_id", "amount", "justification"), false, null, null);

    return new McpServerFeatures.SyncToolSpecification(
        McpSchema.Tool.builder()
            .name("approval_workflow")
            .description("Route purchase request through approval workflow...")
            .inputSchema(schema)
            .build(),
        (exchange, args) -> {
            try {
                String applicantId = (String) args.get("applicant_id");
                double amount = ((Number) args.get("amount")).doubleValue();
                String justification = (String) args.get("justification");
                int deadlineHours = ((Number) args.getOrDefault("deadline_hours", 24)).intValue();

                // Validate inputs
                if (applicantId == null || applicantId.isEmpty()) {
                    return new McpSchema.CallToolResult(
                        "Error: applicant_id is required", true);
                }
                if (amount <= 0 || amount > 1_000_000) {
                    return new McpSchema.CallToolResult(
                        "Error: amount must be between 0.01 and 1,000,000", true);
                }
                if (justification == null || justification.length() > 500) {
                    return new McpSchema.CallToolResult(
                        "Error: justification is required and max 500 characters", true);
                }

                // Launch YAWL case with approval specification
                YSpecificationID specId = new YSpecificationID(
                    "ApprovalWorkflow", "1.0", "http://yawl/approval");

                String caseData = String.format(
                    "<data>" +
                    "<applicant_id>%s</applicant_id>" +
                    "<amount>%.2f</amount>" +
                    "<justification>%s</justification>" +
                    "<deadline_hours>%d</deadline_hours>" +
                    "</data>",
                    escapeXml(applicantId), amount, escapeXml(justification), deadlineHours);

                String caseId = interfaceBClient.launchCase(
                    specId, caseData, null, sessionHandle);

                if (caseId == null || caseId.contains("<failure>")) {
                    return new McpSchema.CallToolResult(
                        "Failed to launch approval case: " + caseId, true);
                }

                // Poll for completion with timeout
                long startTime = System.currentTimeMillis();
                long timeoutMs = deadlineHours * 60 * 60 * 1000L;
                String result = pollCaseCompletion(interfaceBClient, caseId,
                    sessionHandle, timeoutMs);

                if (result == null) {
                    return new McpSchema.CallToolResult(
                        "Approval timeout after " + deadlineHours + " hours. Case: " + caseId, true);
                }

                return new McpSchema.CallToolResult(
                    "Approval workflow completed. Result:\n" + result, false);
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                    "Error executing approval workflow: " + e.getMessage(), true);
            }
        }
    );
}
```

### 1.3 Tool Invocation Flow

When an LLM invokes a tool:

```
┌─────────────────────────────────────────────────────┐
│ 1. LLM Decision                                     │
│    "I need to approve this purchase. Let me call    │
│     the approval_workflow tool."                    │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼ MCP Call
┌─────────────────────────────────────────────────────┐
│ 2. Tool Call Message (JSON over STDIO/HTTP)        │
│ {                                                   │
│   "jsonrpc": "2.0",                                │
│   "method": "tools/call",                          │
│   "params": {                                       │
│     "name": "approval_workflow",                    │
│     "arguments": {                                  │
│       "applicant_id": "emp-12345",                  │
│       "amount": 5000,                               │
│       "justification": "Q1 software licenses"       │
│     }                                               │
│   }                                                 │
│ }                                                   │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼ MCP Server Handler
┌─────────────────────────────────────────────────────┐
│ 3. Server Validation                                │
│    - Parse arguments into typed objects             │
│    - Validate constraints (amount > 0, length)      │
│    - Check rate limits, access control              │
│    - Fail fast on validation errors                 │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼ YAWL Integration
┌─────────────────────────────────────────────────────┐
│ 4. Launch YAWL Case                                 │
│    - Create YSpecificationID                        │
│    - Build XML case data from arguments             │
│    - Call InterfaceB.launchCase(...)                │
│    - Get back caseId                                │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼ Polling (Optional)
┌─────────────────────────────────────────────────────┐
│ 5. Wait for Completion (if needed)                  │
│    - Poll caseState() every 5 seconds               │
│    - Timeout after deadline_hours                   │
│    - Extract final variables from case              │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼ Result Formation
┌─────────────────────────────────────────────────────┐
│ 6. Return Result to LLM                             │
│ {                                                   │
│   "jsonrpc": "2.0",                                │
│   "id": 1,                                          │
│   "result": {                                       │
│     "content": [                                    │
│       {                                             │
│         "type": "text",                             │
│         "text": "Approval workflow completed.\n..." │
│       }                                             │
│     ]                                               │
│   }                                                 │
│ }                                                   │
└─────────────────────────────────────────────────────┘
                 │
                 ▼ LLM Response
┌─────────────────────────────────────────────────────┐
│ 7. LLM Interprets Result                            │
│    "The approval has been granted. The purchase     │
│     order was approved by the manager at 10:30am."  │
└─────────────────────────────────────────────────────┘
```

---

## Part 2: Workflow-as-Tools Pattern

This section covers how to expose YAWL workflows as high-level tools that LLMs can invoke.

### 2.1 Pattern: Workflow = Tool

Each YAWL specification becomes a callable tool:

| Workflow | Tool Name | Input | Output |
|----------|-----------|-------|--------|
| ApprovalWorkflow | `approval_workflow` | {applicant, amount, justification} | {approved: bool, decision_time, notes} |
| ProcurementWorkflow | `procurement` | {vendor, items[], deadline} | {po_number, eta, cost} |
| ComplianceCheckWorkflow | `compliance_check` | {transaction_data} | {status: pass\|fail, violations: []} |

### 2.2 Tool Definition Template

Here's a reusable template for workflow-to-tool mapping:

```java
/**
 * Tool: {workflow_name}
 * Description: Route {business_process} workflow
 * Input Schema: See JSON below
 * Output: Structured result with approval/completion status
 */
public record {WorkflowName}ToolInput(
    @JsonProperty("applicant_id") String applicantId,
    @JsonProperty("amount") double amount,
    @JsonProperty("justification") String justification,
    @JsonProperty("deadline_hours") int deadlineHours
) {
    public {WorkflowName}ToolInput {
        if (applicantId == null || applicantId.isEmpty()) {
            throw new IllegalArgumentException("applicant_id required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (deadlineHours < 1 || deadlineHours > 72) {
            throw new IllegalArgumentException("deadline_hours must be 1-72");
        }
    }
}

public record {WorkflowName}ToolOutput(
    @JsonProperty("case_id") String caseId,
    @JsonProperty("status") String status,  // APPROVED, REJECTED, TIMEOUT
    @JsonProperty("decision_time_ms") long decisionTimeMs,
    @JsonProperty("notes") String notes
) {}
```

### 2.3 Example Tools: Supply Chain Workflows

#### Tool 1: Vendor Selection

```java
{
  "name": "select_vendor",
  "description": "Route vendor selection request through procurement workflow. Returns selected vendor, pricing, and lead time.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "category": {
        "type": "string",
        "description": "Product category (e.g., 'software', 'hardware', 'services')",
        "enum": ["software", "hardware", "services", "consulting"]
      },
      "item_description": {
        "type": "string",
        "description": "What we need to procure",
        "minLength": 10,
        "maxLength": 500
      },
      "budget_usd": {
        "type": "number",
        "description": "Budget constraint in USD",
        "minimum": 100
      },
      "timeline_days": {
        "type": "integer",
        "description": "Days until needed (1-365)",
        "minimum": 1,
        "maximum": 365
      }
    },
    "required": ["category", "item_description", "budget_usd", "timeline_days"]
  }
}
```

Handler flow:
1. Validate inputs (category enum, budget > 0, timeline valid)
2. Launch `VendorSelectionWorkflow` case with item details
3. Wait for RFQ → vendor responses → selection task
4. Return: selected vendor, total cost, lead time

#### Tool 2: Compliance Check

```java
{
  "name": "compliance_check",
  "description": "Validate transaction against compliance policies. Returns pass/fail and list of violations if any.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "transaction_id": {
        "type": "string",
        "description": "Unique transaction ID to check"
      },
      "transaction_amount": {
        "type": "number",
        "description": "Amount in transaction (USD)",
        "minimum": 0
      },
      "vendor_country": {
        "type": "string",
        "description": "2-letter country code of vendor (e.g., 'US', 'CN')"
      },
      "sanctioned_entity_check": {
        "type": "boolean",
        "description": "Whether to check against OFAC list (default true)",
        "default": true
      }
    },
    "required": ["transaction_id", "transaction_amount", "vendor_country"]
  }
}
```

Handler flow:
1. Validate transaction data
2. Launch `ComplianceCheckWorkflow` case
3. Wait for automated checks (sanctioned lists, amount thresholds, country restrictions)
4. Return: {status: "pass"|"fail", violations: ["violation1", "violation2"]}

---

## Part 3: LLM Interaction Patterns

### 3.1 Sequential Tool Calls

Most business processes require multiple sequential steps. The LLM orchestrates them:

```
LLM Prompt:
"Process this purchase request:
 1. Route through approval workflow
 2. If approved, select vendor via RFQ
 3. Generate purchase order
 4. Run compliance check on PO

 Request: $50k software license from Acme Corp, needed in 5 days"

LLM Reasoning:
Step 1: I'll call approval_workflow with the amount and justification
Step 2: Once approved, I'll call select_vendor to find the best vendor
Step 3: Then procurement tool to generate PO
Step 4: Finally compliance_check to ensure nothing's blocked

Execution Trace:
┌─────────────────────────────────┐
│ 1. approval_workflow            │  Case launched: case-00123
│    amount=50000                 │  Status: APPROVED (15 min)
│    justification="Q1 licenses"  │
└──────────┬──────────────────────┘
           │ (approved=true)
           ▼
┌─────────────────────────────────┐
│ 2. select_vendor                │  Case launched: case-00124
│    category="software"          │  Selected: VendorTech Inc
│    budget=50000                 │  Price: $48,500
│    timeline=5 days              │  Lead: 2 days
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│ 3. procurement                  │  Case launched: case-00125
│    vendor="VendorTech Inc"      │  PO generated: PO-2026-00456
│    items=[{qty:1, sku:...}]     │  Total: $48,500
│    deadline=5 days              │
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│ 4. compliance_check             │  Case launched: case-00126
│    transaction_id="PO-00456"    │  Status: PASS
│    amount=48500                 │  No violations
│    vendor_country="US"          │
└─────────────────────────────────┘

Final LLM Summary:
"✅ Purchase approved and processed:
  - Approval: ✓ (15 min turnaround)
  - Vendor selected: VendorTech Inc ($48,500)
  - PO generated: PO-2026-00456
  - Compliance: ✓ (no issues)
  - Ready to ship in 2 days"
```

### 3.2 Tool Result Streaming (Long-Running Workflows)

For workflows that take minutes/hours, stream intermediate results:

```java
// Handler for streaming approval workflow
(exchange, args) -> {
    String caseId = launchApprovalCase(...);

    // Immediate response: case launched
    exchange.send(new McpSchema.CallToolResult(
        "Case launched: " + caseId + ". Waiting for approval...",
        false));

    // Stream updates every 10 seconds
    virtualThreadPool.submit(() -> {
        for (int attempt = 0; attempt < 360; attempt++) {  // 60 min timeout
            Thread.sleep(10_000);
            String state = interfaceBClient.getCaseState(caseId, sessionHandle);

            if (state.contains("COMPLETED")) {
                String result = interfaceBClient.getCaseData(caseId, sessionHandle);
                exchange.send(new McpSchema.CallToolResult(
                    "✓ Approval completed:\n" + result, false));
                break;
            } else if (state.contains("FAILED")) {
                exchange.send(new McpSchema.CallToolResult(
                    "✗ Approval failed. State:\n" + state, true));
                break;
            } else {
                // Send progress update (LLM can display to user)
                exchange.send(new McpSchema.CallToolResult(
                    "[Still processing... " + (attempt * 10) + "s elapsed]", false));
            }
        }
    });
}
```

### 3.3 Error Recovery Patterns

LLMs must gracefully handle tool failures:

```python
# Pseudo-code: LLM error handling pattern

def process_purchase_request(amount, vendor):
    # Attempt 1: Normal flow
    try:
        approval = call_tool("approval_workflow", amount=amount)
        if not approval.approved:
            return "Approval denied: " + approval.reason
    except ToolError as e:
        if "timeout" in str(e):
            # Escalate to manual approval
            return "Escalating to manager (approval timeout)"
        elif "rate_limit" in str(e):
            # Wait and retry
            sleep(60)
            approval = call_tool("approval_workflow", amount=amount)
        else:
            # Unknown error - fail fast
            raise

    # Attempt 2: If vendor selection fails, try with alternate vendor
    try:
        vendor_result = call_tool("select_vendor", ...)
    except ToolError as e:
        # Fallback to approved vendor list
        vendor_result = call_tool("select_approved_vendor", category=category)

    # Attempt 3: Compliance check with retry
    max_retries = 3
    for attempt in range(max_retries):
        try:
            compliance = call_tool("compliance_check", transaction_id=po_id)
            if compliance.status == "pass":
                return "✓ Order complete"
            else:
                return "⚠ Compliance issues: " + compliance.violations
        except ToolError as e:
            if attempt < max_retries - 1:
                wait(exponential_backoff(attempt))
            else:
                raise  # Final attempt failed
```

---

## Part 4: Security & Observability

### 4.1 Input Validation

Every tool must validate inputs **at tool entry point**:

```java
private void validateApprovalInput(Map<String, Object> args) {
    // 1. Type checking
    Object applicantIdObj = args.get("applicant_id");
    if (!(applicantIdObj instanceof String)) {
        throw new IllegalArgumentException("applicant_id must be string");
    }
    String applicantId = (String) applicantIdObj;

    // 2. Length/format checking
    if (applicantId.length() < 3 || applicantId.length() > 20) {
        throw new IllegalArgumentException("applicant_id must be 3-20 chars");
    }
    if (!applicantId.matches("^[a-zA-Z0-9-]+$")) {
        throw new IllegalArgumentException("applicant_id format invalid");
    }

    // 3. Range checking
    Object amountObj = args.get("amount");
    if (!(amountObj instanceof Number)) {
        throw new IllegalArgumentException("amount must be number");
    }
    double amount = ((Number) amountObj).doubleValue();
    if (amount < 0.01 || amount > 10_000_000) {
        throw new IllegalArgumentException("amount out of range");
    }

    // 4. XSS prevention (escape XML/JSON special chars)
    String justification = (String) args.get("justification");
    String escapedJustification = escapeXml(justification);
    if (!justification.equals(escapedJustification)) {
        // Log potential XSS attempt
        auditLog.warn("Potential XSS in justification: " + justification);
        // Use escaped version
        args.put("justification", escapedJustification);
    }

    // 5. SQL injection prevention (though we use XML, not SQL)
    // Pattern: deny any argument containing SQL keywords
    String[] sqlKeywords = {"DROP", "DELETE", "INSERT", "UPDATE", "SELECT"};
    for (String keyword : sqlKeywords) {
        if (justification.toUpperCase().contains(keyword)) {
            throw new IllegalArgumentException("Suspicious SQL keywords detected");
        }
    }
}

public static String escapeXml(String input) {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
}
```

### 4.2 Rate Limiting

Prevent abuse by rate-limiting tool calls per LLM / per tenant:

```java
public class RateLimiter {
    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();
    private final long maxCallsPerHour;
    private final long windowMs = 3600_000;  // 1 hour

    public boolean tryConsume(String clientId, String toolName) {
        String key = clientId + ":" + toolName;
        RateBucket bucket = buckets.computeIfAbsent(key, k -> new RateBucket());

        long now = System.currentTimeMillis();
        if (now - bucket.windowStartMs > windowMs) {
            bucket.reset(now);  // New window
        }

        if (bucket.callCount >= maxCallsPerHour) {
            return false;  // Rate limited
        }

        bucket.callCount++;
        auditLog.info(clientId + " called " + toolName +
            " (" + bucket.callCount + "/" + maxCallsPerHour + ")");
        return true;
    }

    record RateBucket(long windowStartMs, long callCount) {
        void reset(long now) {
            this.windowStartMs = now;
            this.callCount = 0;
        }
    }
}

// In tool handler:
if (!rateLimiter.tryConsume(clientId, "approval_workflow")) {
    return new McpSchema.CallToolResult(
        "Rate limit exceeded: max 100 calls/hour per tool", true);
}
```

### 4.3 Output Filtering (Data Privacy)

Redact sensitive data from tool results:

```java
private String redactSensitiveData(String caseData) {
    // Redact: SSN, credit card, password, API keys
    return caseData
        .replaceAll("\\d{3}-\\d{2}-\\d{4}", "XXX-XX-XXXX")  // SSN
        .replaceAll("\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}",
                    "XXXX-XXXX-XXXX-XXXX")  // Credit card
        .replaceAll("(?i)password[\":\\s]*[^\"\\s]+", "password:***")  // Password
        .replaceAll("api[_-]?key[\":\\s]*[^\"\\s]+", "api_key:***");  // API key
}

// In tool result:
String caseData = interfaceBClient.getCaseData(caseId, sessionHandle);
String redactedData = redactSensitiveData(caseData);
return new McpSchema.CallToolResult(redactedData, false);
```

### 4.4 Observability: Logging & Tracing

Track every tool invocation for audit and debugging:

```java
private void logToolInvocation(String toolName, Map<String, Object> args,
                               String caseId, long durationMs, boolean isError) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("timestamp", Instant.now().toString());
    event.put("tool_name", toolName);
    event.put("case_id", caseId);
    event.put("duration_ms", durationMs);
    event.put("status", isError ? "ERROR" : "SUCCESS");
    event.put("arg_keys", args.keySet());  // Don't log values!
    event.put("client_id", ThreadLocal_clientId.get());

    // Structured logging (JSON for ELK/Datadog)
    logger.info("MCP_TOOL_INVOCATION {}", toJson(event));

    // Metrics (Prometheus)
    meterRegistry.timer("mcp.tool.duration",
        Tag.of("tool", toolName),
        Tag.of("status", isError ? "error" : "success"))
        .record(durationMs, TimeUnit.MILLISECONDS);

    // Tracing (OpenTelemetry)
    Span span = tracer.spanBuilder(toolName).start();
    span.setAttribute("tool.name", toolName);
    span.setAttribute("case.id", caseId);
    span.setAttribute("duration_ms", durationMs);
    span.end();
}
```

#### Observability Dashboard

```
┌─────────────────────────────────────────────────────┐
│ YAWL MCP Observability Dashboard (Grafana)          │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Tool Call Success Rate (24h):                       │
│ approval_workflow:  98.2% (5,234 calls)             │
│ select_vendor:      99.1% (2,103 calls)             │
│ procurement:        96.8% (1,876 calls)             │
│                                                     │
│ Average Latency (p95):                              │
│ approval_workflow:  24.5 seconds                    │
│ select_vendor:      12.3 seconds                    │
│ procurement:        18.7 seconds                    │
│                                                     │
│ Rate Limit Hits:    12 (0.1%)                       │
│ Validation Errors:  34 (0.2%)                       │
│ Timeout Errors:     5 (0.04%)                       │
│                                                     │
│ Top Callers:                                        │
│ claude-3-opus:      4,200 calls (78%)               │
│ gpt-4:              950 calls (18%)                 │
│ llama-2:            84 calls (4%)                   │
└─────────────────────────────────────────────────────┘
```

---

## Part 5: Prompt Engineering for LLM Tool Use

### 5.1 System Prompt Template

Instruct the LLM how to use YAWL tools effectively:

```
You are an autonomous supply chain coordinator. You have access to
YAWL workflow tools to orchestrate business processes.

## Available Tools

### approval_workflow
Routes purchase requests through an approval workflow.
- Use when: A purchase needs authorization
- Key constraint: Max $10M per request, timeout after 24h
- Result interpretation: "approved" means you can proceed to vendor selection

### select_vendor
Finds the best vendor via RFQ workflow.
- Use when: You have an approved budget
- Inputs: category, item description, budget, timeline
- Result interpretation: Returns vendor name, price, lead time

### procurement
Generates purchase order from vendor selection.
- Use when: Vendor is selected and budget is approved
- Inputs: vendor name, items, deadline
- Result interpretation: Returns PO number, ETA, total cost

### compliance_check
Validates transaction against compliance policies.
- Use when: Before finalizing any transaction
- Inputs: transaction ID, amount, vendor country
- Result interpretation: Pass/fail with violation details

## Decision Rules

1. **Always get approval first** before committing to vendor
2. **Always run compliance check last** before finalizing
3. **Handle timeout errors** by escalating to manual approval
4. **Handle rate limits** by waiting 60 seconds and retrying
5. **Provide transparency**: Explain each step to the user

## Response Format

Format your response like this:
```
I'll process your request in 4 steps:
1. **Approval**: [What you're requesting]
2. **Vendor Selection**: [What you're looking for]
3. **Purchase Order**: [PO details]
4. **Compliance**: [Validation]

Executing...

✓ Step 1 complete: [Result]
✓ Step 2 complete: [Result]
...

Final Summary: [Outcome and next steps]
```
```

### 5.2 Few-Shot Examples in System Prompt

Show the LLM examples of correct tool usage:

```
## Examples

### Example 1: Simple approval request

User: "Please approve a $5k software license purchase"

I'll route this through our approval workflow:

<approval_workflow>
applicant_id: "emp-12345"
amount: 5000
justification: "Software license - Q1 budget"
deadline_hours: 24
</approval_workflow>

✓ Approved in 15 minutes. PO can now be generated.

---

### Example 2: Complex procurement flow

User: "I need to procure a server for $25k within 2 weeks"

I'll handle this end-to-end:

1. <approval_workflow>
   applicant_id: "emp-67890"
   amount: 25000
   justification: "High-performance server for data center"
   deadline_hours: 48
   </approval_workflow>

   ✓ Approved

2. <select_vendor>
   category: "hardware"
   item_description: "High-performance server, 2 x E5-2680v4 CPU, 512GB RAM"
   budget_usd: 25000
   timeline_days: 14
   </select_vendor>

   ✓ Selected: TechVendor Inc ($24,500, 5-day lead)

3. <procurement>
   vendor: "TechVendor Inc"
   items: [{"sku": "SRV-48GB", "qty": 1}]
   deadline: "2026-03-07"
   </procurement>

   ✓ PO-2026-00789 generated

4. <compliance_check>
   transaction_id: "PO-2026-00789"
   transaction_amount: 24500
   vendor_country: "US"
   </compliance_check>

   ✓ Compliance passed - no violations

**Summary**: Server procurement approved, ordered, and cleared for payment.
Delivery: 5 days. Cost: $24,500 (under budget).
```

### 5.3 Error Handling Guidance

Tell the LLM how to handle tool failures:

```
## Error Handling

### Timeout (tool takes >60 seconds)
If a workflow times out:
1. Acknowledge the timeout: "This is taking longer than expected"
2. Provide case ID: "You can check status with case ID: {id}"
3. Offer manual escalation: "I'm escalating to a manager for manual review"
4. Don't retry immediately - wait 60 seconds

### Rate Limit (>100 calls/hour)
If rate-limited:
1. Apologize: "I'm processing too many requests"
2. Wait: "Let me try again in a moment"
3. Retry after 60 seconds

### Validation Error
If input validation fails:
1. Explain the problem: "Amount must be between $0.01 and $10M"
2. Ask for clarification: "Can you adjust the amount?"
3. Don't retry with same invalid input

### Workflow Error (case fails in engine)
If the case fails:
1. Report the error: "The approval workflow encountered an error"
2. Suggest manual intervention: "This needs manual review"
3. Don't retry automatically - ask the user
```

---

## Part 6: Proof of Concept - Supply Chain Automation

### 6.1 PoC Overview

**Scenario**: Autonomous agent coordinates vendor selection → PO generation → compliance validation for a hardware purchase.

**Actors**:
- **User**: Requests 10 laptops within 10 days
- **LLM (Claude)**: Orchestrates the workflow via MCP tools
- **YAWL Engine**: Executes approval, vendor RFQ, procurement, compliance checks

**Expected Flow**:
```
User: "Order 10 Dell XPS 13 laptops, budget $30k, needed in 10 days"
  ↓
Claude: "I'll handle this step-by-step..."
  ↓
[1] approval_workflow(amount=30000, justification="10x Dell XPS 13") → APPROVED
  ↓
[2] select_vendor(category=hardware, ..., budget=30000) → Vendor: TechCorp ($29,900)
  ↓
[3] procurement(vendor=TechCorp, items=[{sku:XPS13, qty:10}]) → PO-00234
  ↓
[4] compliance_check(transaction_id=PO-00234, amount=29900) → PASS
  ↓
Result: "✓ Order complete. 10 laptops on order from TechCorp.
         Delivery: 8 days. Total: $29,900. Compliance cleared."
```

### 6.2 PoC Setup Instructions

#### Step 1: Start YAWL Engine

```bash
# Terminal 1: Start YAWL Engine
cd /home/user/yawl
bash scripts/dx.sh compile
mvn -pl yawl-engine tomcat7:run
# Engine ready at http://localhost:8080/yawl
```

#### Step 2: Deploy PoC Workflows

Upload three YAWL specifications to the engine:

```bash
# Terminal 2: Upload workflows
curl -X POST http://localhost:8080/yawl/api/ia/specifications \
  -u admin:YAWL \
  -H "Content-Type: application/xml" \
  -d @approval-workflow.yawl

curl -X POST http://localhost:8080/yawl/api/ia/specifications \
  -u admin:YAWL \
  -H "Content-Type: application/xml" \
  -d @vendor-selection-workflow.yawl

curl -X POST http://localhost:8080/yawl/api/ia/specifications \
  -u admin:YAWL \
  -H "Content-Type: application/xml" \
  -d @compliance-workflow.yawl
```

#### Step 3: Start YAWL MCP Server

```bash
# Terminal 3: Start MCP Server
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL

cd /home/user/yawl
java -cp target/yawl.jar \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

#### Step 4: Configure Claude Desktop

Edit `~/.config/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "yawl-poc": {
      "command": "java",
      "args": [
        "-cp", "/home/user/yawl/target/yawl.jar",
        "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"
      ],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl",
        "YAWL_USERNAME": "admin",
        "YAWL_PASSWORD": "YAWL"
      }
    }
  }
}
```

#### Step 5: Run PoC in Claude

Restart Claude Desktop, then start a new conversation:

```
User: I need to procure 10 Dell XPS 13 laptops.
      Budget: $30k max, delivery within 10 days.
      Please handle the full process end-to-end.

Claude: I'll coordinate the procurement using our automated workflow system.
        Let me handle this in 4 steps:

        1. Route through approval workflow
        2. Find best vendor via RFQ
        3. Generate purchase order
        4. Validate compliance

        Executing...

        [Uses MCP tools to orchestrate YAWL workflows]
```

### 6.3 PoC Workflow Definitions

#### ApprovalWorkflow.yawl (Simplified)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<yawl:specification xmlns:yawl="http://www.yawlfoundation.org/yawlschema">
  <yawl:name>ApprovalWorkflow</yawl:name>
  <yawl:netFactoryType>http://www.yawlfoundation.org/yawl/factory</yawl:netFactoryType>

  <yawl:processControlElements>
    <yawl:startCondition id="InputCondition"/>
    <yawl:task id="GetApproval">
      <yawl:name>Get Manager Approval</yawl:name>
      <yawl:flowsInto>
        <yawl:nextElementRef id="CheckApprovalDecision"/>
      </yawl:flowsInto>
      <yawl:decomposesTo>GetApprovalSubNet</yawl:decomposesTo>
    </yawl:task>
    <yawl:exclusiveGateway id="CheckApprovalDecision">
      <yawl:flowsInto>
        <yawl:nextElementRef id="Approved"/>
        <yawl:predicate yawl:language="xpath">$approved = true()</yawl:predicate>
      </yawl:flowsInto>
      <yawl:flowsInto>
        <yawl:nextElementRef id="Denied"/>
        <yawl:predicate yawl:language="xpath">$approved = false()</yawl:predicate>
      </yawl:flowsInto>
    </yawl:exclusiveGateway>
    <yawl:task id="Approved">
      <yawl:flowsInto>
        <yawl:nextElementRef id="OutputCondition"/>
      </yawl:flowsInto>
    </yawl:task>
    <yawl:task id="Denied">
      <yawl:flowsInto>
        <yawl:nextElementRef id="OutputCondition"/>
      </yawl:flowsInto>
    </yawl:task>
    <yawl:outputCondition id="OutputCondition"/>
  </yawl:processControlElements>

  <yawl:data>
    <yawl:variable name="applicant_id">
      <yawl:type>xs:string</yawl:type>
    </yawl:variable>
    <yawl:variable name="amount">
      <yawl:type>xs:decimal</yawl:type>
    </yawl:variable>
    <yawl:variable name="justification">
      <yawl:type>xs:string</yawl:type>
    </yawl:variable>
    <yawl:variable name="approved">
      <yawl:type>xs:boolean</yawl:type>
      <yawl:initialValue>false</yawl:initialValue>
    </yawl:variable>
  </yawl:data>
</yawl:specification>
```

---

## Part 7: Implementation Reference

### 7.1 YawlMcpServer Existing Implementation

The YAWL codebase already includes a production-ready MCP server at:
`src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`

**Key facts**:
- 15 built-in tools (launch case, complete task, query case, etc.)
- 6 resources (specifications, cases, work items)
- Full MCP 2025-11-25 specification compliance
- STDIO transport (HTTP via SSE transport provider)
- Structured logging via McpLoggingHandler
- Zero stubs/mocks (all tools execute real YAWL operations)

### 7.2 Adding Custom Workflow Tools

To add a custom workflow as a tool:

```java
// 1. Create tool specification
private static McpServerFeatures.SyncToolSpecification createCustomWorkflowTool(...) {
    Map<String, Object> props = new LinkedHashMap<>();
    // Define input schema

    return new McpServerFeatures.SyncToolSpecification(
        McpSchema.Tool.builder()
            .name("my_custom_workflow")
            .description("Description of what workflow does")
            .inputSchema(schema)
            .build(),
        (exchange, args) -> {
            try {
                // Validate inputs
                // Launch YAWL case
                // Poll for completion
                // Extract and return results
            } catch (Exception e) {
                return new McpSchema.CallToolResult(error, true);
            }
        }
    );
}

// 2. Register in YawlToolSpecifications.createAll()
tools.add(createCustomWorkflowTool(interfaceBClient, sessionHandle));
```

### 7.3 Deployment

**Production deployment** (HTTP transport):

```bash
export MCP_TRANSPORT=sse
export MCP_PORT=3000
export YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=${YAWL_PASSWORD}

java -cp target/yawl.jar \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

**Docker Compose**:

```yaml
version: '3.8'
services:
  yawl-mcp-server:
    image: yawl-mcp-server:6.0.0
    environment:
      YAWL_ENGINE_URL: http://yawl-engine:8080/yawl
      YAWL_USERNAME: admin
      YAWL_PASSWORD: ${YAWL_PASSWORD}
      MCP_TRANSPORT: sse
      MCP_PORT: 3000
    ports:
      - "3000:3000"
    depends_on:
      - yawl-engine
```

---

## Summary

This document provides a complete blueprint for exposing YAWL workflows as LLM tools via MCP:

1. **Architecture**: Tool registration, schema definition, STDIO/HTTP transport
2. **Patterns**: Workflow-as-tool, sequential calls, error recovery
3. **Security**: Input validation, rate limiting, output filtering
4. **Observability**: Structured logging, metrics, distributed tracing
5. **Engineering**: System prompts, few-shot examples, error handling
6. **PoC**: End-to-end supply chain automation (approval → vendor → PO → compliance)

The YAWL MCP server is **production-ready** and can be extended with custom workflow tools following the patterns documented here.

**Next Steps**:
1. Deploy the PoC workflows to your YAWL engine
2. Start the MCP server
3. Connect Claude Desktop (or other LLM client)
4. Test the supply chain automation scenario
5. Extend with custom workflows for your domain

---

**Document Reference**:
- `.claude/MCP-QUICKSTART.md` — Quick setup guide
- `.claude/AGENT-INTEGRATION.md` — A2A (Agent-to-Agent) patterns
- `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java` — Server implementation
- MCP Spec: https://spec.modelcontextprotocol.io/
