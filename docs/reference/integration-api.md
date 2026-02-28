# Reference: MCP/A2A Integration API

Complete API reference for YAWL external connectivity and AI agent integration.

---

## MCP Server

### YawlMcpServer

```java
public class YawlMcpServer {
    public YawlMcpServer(YawlMcpServerConfig config);

    /**
     * Register an MCP tool.
     */
    public void registerTool(YawlMcpTool tool) throws Exception;

    /**
     * Register multiple tools.
     */
    public void registerTools(Collection<YawlMcpTool> tools) throws Exception;

    /**
     * Get registered tools.
     */
    public List<YawlMcpTool> getRegisteredTools();

    /**
     * Execute a tool invocation.
     */
    public McpToolOutput executeTool(String toolName, McpToolInput input)
        throws Exception;

    /**
     * Start the MCP server.
     */
    public void start() throws Exception;

    /**
     * Stop the MCP server.
     */
    public void stop() throws Exception;

    public boolean isRunning();
}
```

### YawlMcpServerConfig

```java
public class YawlMcpServerConfig {
    public static Builder builder();

    public String engineBaseUrl();
    public String engineUser();
    public String enginePassword();
    public String mcpServerName();
    public String mcpServerVersion();
    public int port();
    public boolean enableStatelessEngine();
    public boolean enableObservability();
    public String otelEndpoint();

    public CaseOutcomePredictor createCaseOutcomePredictor();
    public PrescriptiveEngine createPrescriptiveEngine();
    public ResourceOptimizer createResourceOptimizer();
    public NaturalLanguageQueryEngine createNaturalLanguageQueryEngine();
}
```

---

## MCP Tools

### YawlMcpTool Interface

```java
public interface YawlMcpTool {
    /**
     * Get the tool name (lowercase, underscores).
     */
    String toolName();

    /**
     * Get human-readable description.
     */
    String toolDescription();

    /**
     * Execute the tool.
     */
    McpToolOutput execute(McpToolInput input) throws Exception;

    /**
     * Get input schema.
     */
    Map<String, String> toolInputSchema();

    /**
     * Optional: Get output schema.
     */
    default Map<String, String> toolOutputSchema() {
        return Map.of();
    }
}
```

### Built-in Tools

#### yawl_list_specifications

```bash
Method: POST
Path: /mcp/tools/yawl_list_specifications
Input: {}
Output: {
  "specifications": [
    {"uri": "OrderApproval", "name": "Order Approval", "version": "1.0"}
  ]
}
```

#### yawl_launch_case

```bash
Method: POST
Path: /mcp/tools/yawl_launch_case
Input: {
  "specURI": "OrderApproval",
  "caseParams": "<order><amount>5000</amount></order>"
}
Output: {
  "caseID": "42",
  "status": "Running",
  "createdAt": "2026-02-28T10:00:00Z"
}
```

#### yawl_get_work_items

```bash
Method: POST
Path: /mcp/tools/yawl_get_work_items
Input: {
  "caseID": "42"  # optional
}
Output: {
  "workItems": [
    {
      "workItemID": "42:ApproveOrder",
      "taskID": "ApproveOrder",
      "status": "Enabled",
      "data": {...}
    }
  ]
}
```

#### yawl_checkout_work_item

```bash
Method: POST
Path: /mcp/tools/yawl_checkout_work_item
Input: {
  "workItemID": "42:ApproveOrder"
}
Output: {
  "workItemID": "42:ApproveOrder",
  "data": {...}
}
```

#### yawl_checkin_work_item

```bash
Method: POST
Path: /mcp/tools/yawl_checkin_work_item
Input: {
  "workItemID": "42:ApproveOrder",
  "outputData": "<ApprovalData><decision>approved</decision></ApprovalData>"
}
Output: {
  "status": "success",
  "workItemID": "42:ApproveOrder"
}
```

#### yawl_get_case_status

```bash
Method: POST
Path: /mcp/tools/yawl_get_case_status
Input: {
  "caseID": "42"
}
Output: {
  "caseID": "42",
  "status": "Running",
  "currentTasks": ["ApproveOrder"],
  "progress": 50,
  "createdAt": "2026-02-28T09:00:00Z"
}
```

#### yawl_cancel_case

```bash
Method: POST
Path: /mcp/tools/yawl_cancel_case
Input: {
  "caseID": "42"
}
Output: {
  "status": "cancelled",
  "caseID": "42"
}
```

---

## Custom Tools

### Creating a Custom Tool

```java
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpTool;
import org.yawlfoundation.yawl.integration.mcp.McpToolInput;
import org.yawlfoundation.yawl.integration.mcp.McpToolOutput;

@Component
public class MyCustomTool implements YawlMcpTool {

    @Override
    public String toolName() {
        return "my_custom_tool";
    }

    @Override
    public String toolDescription() {
        return "My custom workflow operation";
    }

    @Override
    public McpToolOutput execute(McpToolInput input) throws Exception {
        String param1 = input.getParameter("param1", String.class);

        // Your logic here
        String result = doSomething(param1);

        return McpToolOutput.success(Map.of(
            "result", result,
            "timestamp", Instant.now()
        ));
    }

    @Override
    public Map<String, String> toolInputSchema() {
        return Map.of(
            "param1", "string",
            "param2", "number"
        );
    }

    private String doSomething(String input) {
        return "Processed: " + input;
    }
}
```

### McpToolInput

```java
public class McpToolInput {
    public <T> T getParameter(String name, Class<T> type)
        throws IllegalArgumentException;

    public <T> T getParameter(String name, Class<T> type, T defaultValue);

    public boolean hasParameter(String name);

    public Map<String, Object> getAll();
}
```

### McpToolOutput

```java
public class McpToolOutput {
    public static McpToolOutput success(Map<String, Object> data);
    public static McpToolOutput failure(String errorMessage);
    public static McpToolOutput failure(String errorMessage, Throwable cause);

    public boolean isSuccess();
    public Map<String, Object> getData();
    public String getError();
}
```

---

## A2A Protocol

### YawlA2AServer

```java
public class YawlA2AServer {
    public YawlA2AServer(AgentOrchestrator orchestrator);

    /**
     * Register an agent skill.
     */
    public void registerSkill(YawlA2ASkill skill) throws Exception;

    /**
     * Delegate a task to an agent.
     */
    public SkillExecutionResult delegateToAgent(String agentId,
        String skillName, SkillInput input) throws Exception;

    /**
     * Get agent status.
     */
    public AgentStatus getAgentStatus(String agentId);

    public void start() throws Exception;
    public void stop() throws Exception;
}
```

### YawlA2ASkill

```java
public interface YawlA2ASkill {
    /**
     * Skill name.
     */
    String skillName();

    /**
     * Skill description.
     */
    String skillDescription();

    /**
     * Execute the skill.
     */
    SkillOutput execute(SkillInput input) throws Exception;

    /**
     * Input schema.
     */
    Map<String, String> skillInputSchema();

    /**
     * Output schema.
     */
    Map<String, String> skillOutputSchema();
}
```

### AgentOrchestrator

```java
public class AgentOrchestrator {
    public AgentOrchestrator addAgent(String agentId, String agentName);

    public SkillExecutionResult executeSkill(String agentId,
        String skillName, SkillInput input) throws Exception;

    public void defineSequence(String... agentIds);

    public void defineParallel(String... agentIds);

    public List<String> getRegisteredAgents();

    public AgentStatus getStatus(String agentId);
}
```

---

## Observability

### OpenTelemetry Integration

```java
@Configuration
public class OtelConfiguration {

    @Bean
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("yawl-integration");
    }
}
```

### Automatic Tracing

Every MCP tool call and A2A skill execution is automatically traced:

```
Span: yawl.mcp.tool_execution
├─ Attributes:
│  ├─ tool.name: "yawl_launch_case"
│  ├─ tool.duration_ms: 150
│  ├─ tool.status: "success"
│  └─ engine.url: "http://localhost:8080/yawl"
└─ Events:
   ├─ tool_started
   ├─ engine_request_sent
   └─ tool_completed
```

---

## SPIFFE/SVID Integration

```java
@Configuration
public class SpiffeConfiguration {

    @Bean
    public SpiffeProvider spiffeProvider(SpiffeConfig config) {
        return new SpiffeProvider(config);
    }

    @Bean
    public WorkloadApiClient workloadApiClient(SpiffeProvider provider) {
        return new WorkloadApiClient(provider);
    }
}

@RestController
public class SpiffeEnabledEndpoint {

    private final WorkloadApiClient workloadApiClient;

    @GetMapping("/secure")
    public String secureEndpoint() {
        // Workload identity automatically injected into mTLS
        return "Authenticated via SPIFFE/SVID";
    }
}
```

---

## Deduplication

```java
public interface DeduplicationFilter {
    /**
     * Check if a request has already been processed.
     */
    boolean isDuplicate(String idempotencyKey) throws Exception;

    /**
     * Mark a request as processed.
     */
    void markProcessed(String idempotencyKey, Object result) throws Exception;

    /**
     * Get cached result of a previous request.
     */
    <T> T getCachedResult(String idempotencyKey, Class<T> resultType)
        throws Exception;
}
```

### Usage

```java
@Component
public class IdempotentMcpTool implements YawlMcpTool {

    private final DeduplicationFilter dedup;

    @Override
    public McpToolOutput execute(McpToolInput input) throws Exception {
        String idempotencyKey = input.getParameter("idempotency_key", String.class);

        // Check if already processed
        if (dedup.isDuplicate(idempotencyKey)) {
            return McpToolOutput.success(dedup.getCachedResult(idempotencyKey, Map.class));
        }

        // Execute operation
        Map<String, Object> result = executeOperation(input);

        // Mark as processed
        dedup.markProcessed(idempotencyKey, result);

        return McpToolOutput.success(result);
    }
}
```

---

## Health & Status Endpoints

### MCP Server Health

```bash
GET /mcp/health

{
  "status": "UP",
  "components": {
    "mcp-server": {"status": "UP"},
    "engine-connection": {"status": "UP"},
    "tool-registry": {"status": "UP", "toolCount": 10}
  }
}
```

### A2A Agent Status

```bash
GET /a2a/agents/{agentId}/status

{
  "agentId": "order_reviewer",
  "status": "READY",
  "skills": ["compliance_review", "order_validation"],
  "lastHeartbeat": "2026-02-28T10:05:00Z",
  "executionCount": 42
}
```

---

## Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `integration.mcp.tool_invocation` | Counter | Tool calls |
| `integration.mcp.tool_duration` | Timer | Tool execution time |
| `integration.mcp.tool_errors` | Counter | Tool failures |
| `integration.a2a.skill_execution` | Counter | Skill delegations |
| `integration.a2a.agent_utilization` | Gauge | Agent workload % |
| `integration.dedup.cache_hits` | Counter | Idempotency cache hits |

---

## Exception Handling

```java
public class IntegrationException extends Exception {
    public IntegrationException(String message);
    public IntegrationException(String message, Throwable cause);

    public IntegrationExceptionType getType();
    public String getContext();
}

public enum IntegrationExceptionType {
    ENGINE_UNREACHABLE,
    TOOL_NOT_FOUND,
    INVALID_INPUT,
    SKILL_FAILED,
    TIMEOUT,
    AUTHENTICATION_FAILED,
    UNKNOWN
}
```

---

## See Also

- **Tutorial:** `docs/tutorials/integration-getting-started.md`
- **How-To:** `docs/how-to/configure-agents.md`
- **Explanation:** `docs/explanation/agent-coordination.md`
