---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/integration/**"
  - "*/src/test/java/org/yawlfoundation/yawl/integration/**"
  - "**/yawl-integration/**"
  - "**/yawl-mcp-a2a-app/**"
---

# MCP & A2A Integration Rules

## MCP Server (15 Tools)
- Tools: yawl_launch_case, yawl_cancel_case, yawl_get_case_status, yawl_list_specifications, yawl_get_specification, yawl_upload_specification, yawl_get_work_items, yawl_get_work_items_for_case, yawl_checkout_work_item, yawl_checkin_work_item, yawl_get_running_cases, yawl_get_case_data, yawl_suspend_case, yawl_resume_case, yawl_skip_work_item
- Resources: 3 static (yawl://specifications, yawl://cases, yawl://workitems) + 3 templates (yawl://cases/{caseId}, yawl://cases/{caseId}/data, yawl://workitems/{workItemId})
- Prompts: 4 (workflow_analysis, task_completion_guide, troubleshooting_guide, design_review)
- Completions: 3 (workflow_analysis, task_completion_guide, case_resource)
- Protocol version: 2025-11-25 | SDK: 0.18.1 (official v1.0.0 GA not yet released)
- Transport: STDIO (recommended) via StdioServerTransportProvider
- Entry: `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer`
- Session handle: Dynamic via Supplier<String> to support session refresh on expiry

## A2A Server (6 Skills)
- Skills: introspect, generate, build, test, commit, upgrade
- Entry: `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer`
- Virtual thread variant: `VirtualThreadYawlA2AServer` (1000 agents = ~1MB vs 2GB)
- Authentication: JWT tokens with 60-second TTL for handoff

## Z.AI Bridge
- Requires `ZHIPU_API_KEY` environment variable
- HTTP transport via `HttpZaiMcpBridge`
- GLM-4 model integration
- Fail fast if API key missing (no silent fallback)

## Protocol Rules
- All API payloads use Java records (immutable)
- Virtual threads for all I/O-bound operations
- ScopedValue for workflow context propagation
- Handoff protocol uses JWT with automatic retry (max 3, exponential backoff)
