# YAWL MCP Server Integration Guide

Model Context Protocol (MCP) server for YAWL v6.0.0, enabling AI assistants to interact with YAWL workflow operations through the official MCP Java SDK 0.18.0.

## Overview

The YAWL MCP Server exposes 15 workflow tools, 3 static resources, 3 resource templates, 4 prompts, and 3 completions. All operations are backed by real YAWL engine calls via InterfaceB (runtime) and InterfaceA (design-time).

**Transport**: STDIO (Standard I/O)
**SDK**: Official MCP Java SDK 0.18.0
**Server Version**: 5.2.0

## Quick Start

### Environment Variables

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=your_password  # See SECURITY.md for credential management
```

### Starting the Server

```bash
java -cp "yawl-engine.jar:yawl-integration.jar:mcp-sdk.jar" \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

Expected output:
```
Starting YAWL MCP Server v5.2.0
Engine URL: http://localhost:8080/yawl
Transport: STDIO (official MCP SDK 0.18.0)
Connected to YAWL engine (session established)
YAWL MCP Server v5.2.0 started on STDIO transport
Capabilities: 15 tools, 3 resources, 3 resource templates, 4 prompts, 3 completions, logging
```

## Tools (15)

### Case Management Tools

#### 1. `yawl_launch_case`

Launch a new YAWL workflow case from a loaded specification.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `specIdentifier` | string | Yes | Workflow specification identifier |
| `specVersion` | string | No | Specification version (default: 0.1) |
| `specUri` | string | No | Specification URI (default: same as identifier) |
| `caseData` | string | No | XML case input data |

**Example**:
```json
{
  "specIdentifier": "OrderProcessing",
  "specVersion": "1.0",
  "caseData": "<data><orderId>12345</orderId><customerId>CUST-001</customerId></data>"
}
```

**Response**: Case ID of the launched workflow instance.

---

#### 2. `yawl_get_case_status`

Get the current status and state of a running workflow case.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `caseId` | string | Yes | The case ID to query |

**Example**:
```json
{
  "caseId": "42.0"
}
```

---

#### 3. `yawl_cancel_case`

Cancel a running workflow case and terminate all active work items.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `caseId` | string | Yes | The case ID to cancel |

---

#### 4. `yawl_suspend_case`

Suspend a running case by suspending all its active work items.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `caseId` | string | Yes | The case ID to suspend |

---

#### 5. `yawl_resume_case`

Resume a previously suspended case by unsuspending all its work items.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `caseId` | string | Yes | The case ID to resume |

---

#### 6. `yawl_get_case_data`

Get the current data variables and values of a running case.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `caseId` | string | Yes | The case ID to query |

---

#### 7. `yawl_get_running_cases`

Get all currently running workflow cases from the engine.

**Parameters**: None

---

### Work Item Management Tools

#### 8. `yawl_get_work_items`

Get all live work items across all running cases.

**Parameters**: None

**Response**: List of work items with ID, case ID, task ID, status, and specification info.

---

#### 9. `yawl_get_work_items_for_case`

Get all active work items for a specific case.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `caseId` | string | Yes | The case ID to query |

---

#### 10. `yawl_checkout_work_item`

Check out a work item to claim ownership and begin execution.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `workItemId` | string | Yes | Work item ID (format: caseID:taskID) |

**Note**: Work item must be in `enabled` or `fired` state.

---

#### 11. `yawl_checkin_work_item`

Check in (complete) a work item with output data.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `workItemId` | string | Yes | The work item ID to complete |
| `outputData` | string | No | XML output data |

**Example**:
```json
{
  "workItemId": "42.0:ReviewOrder",
  "outputData": "<data><approved>true</approved><reviewerNotes>Looks good</reviewerNotes></data>"
}
```

---

#### 12. `yawl_skip_work_item`

Skip a work item if the task allows skipping.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `workItemId` | string | Yes | The work item ID to skip |

---

### Specification Management Tools

#### 13. `yawl_list_specifications`

List all workflow specifications currently loaded in the engine.

**Parameters**: None

**Response**: List of specifications with identifier, version, URI, name, and status.

---

#### 14. `yawl_get_specification`

Get the full XML definition of a workflow specification.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `specIdentifier` | string | Yes | Specification identifier |
| `specVersion` | string | No | Version (default: 0.1) |
| `specUri` | string | No | URI (default: same as identifier) |

---

#### 15. `yawl_upload_specification`

Upload a YAWL specification XML to the engine.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `specXml` | string | Yes | Complete YAWL specification XML |

---

### Optional: Natural Language Tool

#### 16. `yawl_natural_language` (requires Z.AI SDK)

Process natural language requests using Z.AI function calling.

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `query` | string | Yes | Natural language request |

**Example queries**:
- "List all running workflows"
- "Launch OrderProcessing with customer ID 12345"
- "Cancel case 42.0"
- "Complete the review work item for case 42.0"

**Note**: Requires `ZAI_API_KEY` environment variable and Z.AI SDK dependency.

---

## Resources (3 Static)

### `yawl://specifications`

All loaded workflow specifications.

**Format**: `application/json`
**Content**: Array of specifications with identifier, version, URI, name, status, documentation, rootNetId.

---

### `yawl://cases`

All running workflow cases.

**Format**: `application/json`
**Content**: Running cases XML wrapped in JSON.

---

### `yawl://workitems`

All live work items across all cases.

**Format**: `application/json`
**Content**: Array of work items with id, caseId, taskId, status, specIdentifier, specUri, enablementTimeMs, startTimeMs.

---

## Resource Templates (3 Parameterized)

### `yawl://cases/{caseId}`

Case state and work items for a specific case.

**Example**: `yawl://cases/42.0`

---

### `yawl://cases/{caseId}/data`

Case variable data for a specific case.

**Example**: `yawl://cases/42.0/data`

---

### `yawl://workitems/{workItemId}`

Detailed information about a specific work item.

**Example**: `yawl://workitems/42.0:ReviewOrder`

---

## Prompts (4)

### 1. `workflow_analysis`

Analyze a YAWL workflow specification and provide recommendations.

**Arguments**:
| Name | Required | Description |
|------|----------|-------------|
| `spec_identifier` | Yes | Specification identifier |
| `analysis_type` | No | Focus: performance, correctness, optimization, or general |

---

### 2. `task_completion_guide`

Generate step-by-step guidance for completing a work item.

**Arguments**:
| Name | Required | Description |
|------|----------|-------------|
| `work_item_id` | Yes | Work item ID |
| `context` | No | Additional task context |

---

### 3. `case_troubleshooting`

Diagnose issues with a workflow case and suggest resolutions.

**Arguments**:
| Name | Required | Description |
|------|----------|-------------|
| `case_id` | Yes | Case ID |
| `symptom` | No | Observed issue description |

---

### 4. `workflow_design_review`

Review a specification for YAWL best practices compliance.

**Arguments**:
| Name | Required | Description |
|------|----------|-------------|
| `spec_identifier` | Yes | Specification identifier |

---

## Completions (3)

Auto-complete support for:

1. **workflow_analysis prompt**: Spec identifiers
2. **task_completion_guide prompt**: Work item IDs
3. **yawl://cases/{caseId} resource**: Case IDs

---

## Spring Integration

### Configuration Properties

```yaml
# application.yml
yawl:
  mcp:
    enabled: true
    engine-url: http://localhost:8080/yawl
    username: ${YAWL_USERNAME:admin}
    password: ${YAWL_PASSWORD}  # required - no default
    transport: stdio
    http:
      enabled: false
      port: 8081
      path: /mcp
    zai:
      enabled: true
      api-key: ${ZAI_API_KEY}
    connection:
      retry-attempts: 3
      retry-delay-ms: 1000
      timeout-ms: 5000
```

### Enable MCP in Spring Boot

```java
@SpringBootApplication
@EnableYawlMcp
public class YawlMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(YawlMcpApplication.class, args);
    }
}
```

### Custom Tool Registration

```java
@Configuration
public class CustomToolConfig {
    @Bean
    public LaunchCaseTool launchCaseTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            YawlMcpSessionManager sessionManager) {
        return new LaunchCaseTool(interfaceBClient, sessionManager);
    }
}
```

---

## Troubleshooting

### Server Fails to Start

**Error**: `Failed to connect to YAWL engine`

**Solutions**:
1. Verify YAWL engine is running at the configured URL
2. Check credentials are correct
3. Ensure network connectivity between MCP server and engine

---

### Tool Returns Failure

**Error**: `<failure>Unable to complete operation</failure>`

**Solutions**:
1. Check YAWL engine logs for detailed errors
2. Verify session handle is still valid (server auto-reconnects)
3. Validate input parameters match expected formats

---

### Work Item Checkout Fails

**Error**: `Failed to check out work item`

**Causes**:
- Work item not in `enabled` or `fired` state
- Work item already checked out by another user
- Invalid work item ID format

---

### Z.AI Natural Language Not Available

**Error**: `Z.AI integration requires the Z.AI SDK`

**Solutions**:
1. Add Z.AI SDK dependency to `yawl-integration/pom.xml`
2. Set `ZAI_API_KEY` or `ZHIPU_API_KEY` environment variable
3. Provide concrete implementation in `ZaiFunctionService`

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     MCP Client (AI Assistant)                │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ STDIO Transport
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     YawlMcpServer                            │
│  ┌─────────────┐  ┌───────────────┐  ┌──────────────────┐   │
│  │ 15 Tools    │  │ 6 Resources   │  │ 4 Prompts        │   │
│  │             │  │ (3 static +   │  │                  │   │
│  │ - Cases     │  │  3 templates) │  │ + 3 Completions  │   │
│  │ - WorkItems │  │               │  │                  │   │
│  │ - Specs     │  │               │  │                  │   │
│  └─────────────┘  └───────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP (Interface A/B)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     YAWL Engine                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ InterfaceA   │  │ InterfaceB   │  │ Workflow State   │   │
│  │ (Design-time)│  │ (Runtime)    │  │                  │   │
│  └──────────────┘  └──────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Security Considerations

1. **Credentials**: Never hardcode passwords. Use environment variables.
2. **Network**: MCP server communicates with YAWL engine over HTTP. Use HTTPS in production.
3. **Transport**: STDIO transport is inherently local - no network exposure.
4. **Session Management**: Server maintains a single authenticated session to the engine.

---

## Version Compatibility

| YAWL MCP Server | MCP SDK | YAWL Engine | Java |
|-----------------|---------|-------------|------|
| 5.2.0 | 0.17.2 | 5.2+ | 21+ |
| 6.0.0 | 0.18.0 (v1) | 6.0+ | 25+ |

---

## References

- [MCP Specification](https://modelcontextprotocol.io/)
- [YAWL Documentation](../README.md)
- [Interface B API](../api/README.md)
- [Security Checklist](../SECURITY-CHECKLIST-JAVA25.md)
