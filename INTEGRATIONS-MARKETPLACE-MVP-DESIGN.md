# Integrations Marketplace MVP Design (4-Week Roadmap)

**Status**: Implementation Design
**Date**: 2026-02-21
**Scope**: Z.AI connectors, A2A adapters, MCP tool registration
**Effort**: 4 weeks, 2 engineers, $50K cost

---

## Executive Summary

This MVP enables YAWL skills (A2A skill implementations) to be exposed as **connectors** to external systems via three primary pathways:

1. **Z.AI Connectors** — Skills as tools for Z.AI autonomous agents (MCP-based)
2. **A2A Adapters** — Protocol handlers for agent-to-agent handoffs
3. **MCP Tools** — LLM-native skill invocation (Claude, etc.)

**Key principle (80/20)**: Focus on discovery, registration, and invocation plumbing. Defer OAuth, webhooks, data transformers, and advanced auth to Phase 2.

**Success metrics**:
- 5 working connectors (1 Z.AI + 2 A2A + 2 MCP)
- Discovery API <100ms latency
- 100% skill invocation success rate
- Zero TODO/mock implementations (real code or UnsupportedOperationException)

---

## 1. Integration Types (Core MVP)

### 1.1 Z.AI Connector

**Purpose**: Expose YAWL skills as MCP tools for Z.AI autonomous agents.

**Pattern**:
```
Z.AI Agent
  ↓ (MCP protocol)
YawlMcpServer (stdio or TCP)
  ↓ (queries)
ConnectorRegistry
  ↓ (discovers)
SkillConnector (Z.AI type)
  ↓ (invokes)
A2ASkill (e.g., ApprovalSkill)
```

**Example**: Z.AI agent autonomously approves purchase orders by invoking `skills/approve_po` via MCP.

**Scope (MVP)**:
- Tool schema generation from SkillRequest/SkillResult
- Input validation against JSON Schema
- Idempotency tracking (dedup keys)
- Error handling (transient vs permanent)

**Out of scope**: OAuth, webhook callbacks, async result polling.

---

### 1.2 A2A Adapter

**Purpose**: Enable skill execution via agent-to-agent handoff protocol.

**Pattern**:
```
Peer A2A Agent
  ↓ (A2A message)
YawlA2AServer
  ↓ (routes to adapter)
A2AProtocolAdapter
  ↓ (translates A2A → SkillRequest)
A2ASkill (e.g., PurchaseOrderSkill)
  ↓ (executes)
SkillResult
  ↓ (translates SkillResult → A2A message)
Peer A2A Agent
```

**Example**: External PO approval agent sends A2A message to YAWL. YAWL routes to PurchaseOrderSkill, returns result via A2A.

**Scope (MVP)**:
- A2A message parsing (parts[], context[])
- Skill discovery via agent card (/.well-known/agent.json)
- SkillResult serialization to A2A response
- Correlation ID tracking (request chaining)

**Out of scope**: Multi-agent consensus, conflict resolution, webhook subscriptions.

---

### 1.3 MCP Tool Registration

**Purpose**: Register skills as MCP tools, enabling Claude and other LLMs to call them.

**Pattern**:
```
Claude (or other LLM)
  ↓ (tool call)
MCP Client (Claude SDK)
  ↓ (tool_call message)
YawlMcpServer
  ↓ (tool name)
ConnectorRegistry
  ↓ (lookup)
MCPToolConnector
  ↓ (converts MCP args → SkillRequest)
A2ASkill
  ↓ (executes)
SkillResult → MCP tool result
```

**Example**: Claude asks "Describe workflow InvoiceApproval". MCP tool returns spec metadata.

**Scope (MVP)**:
- Auto-discovery of @MCPTool annotated skills
- Tool schema generation (input/output)
- Tool execution via SkillResult
- Error propagation (MCP error format)

**Out of scope**: Streaming tool results, pagination, advanced filtering.

---

## 2. Core MVP Scope

### 2.1 Connector Registry (Git-Backed YAML)

**Location**: `.integrations/` directory in YAWL repo

**Structure**:
```
.integrations/
├─ README.md                    # Registry guide
├─ connectors.yaml              # Master registry (all connector metadata)
├─ z-ai/
│  ├─ approval-skill.yaml       # Z.AI connector for ApprovalSkill
│  └─ expense-report-skill.yaml
├─ a2a/
│  ├─ purchase-order-skill.yaml # A2A adapter for PurchaseOrderSkill
│  └─ invoice-skill.yaml
└─ mcp/
   ├─ expense-report.yaml       # MCP tool for ExpenseReportSkill
   └─ case-monitor.yaml
```

**Connector YAML schema**:
```yaml
# Example: .integrations/a2a/purchase-order-skill.yaml
id: purchase_order_skill
name: "Purchase Order Skill"
type: a2a          # enum: z-ai, a2a, mcp
version: "1.0.0"
description: "Handles purchase order approval workflow"

# Skill reference
skill:
  class: "org.yawlfoundation.yawl.integration.a2a.skills.PurchaseOrderSkill"
  permissions: ["po:write", "po:approve"]

# Connector-specific config
config:
  # A2A adapter settings
  handoff_protocol: "v1"
  timeout_seconds: 300

  # Message routing
  correlation_id_header: "X-Correlation-ID"
  idempotency_key_header: "X-Idempotency-Key"

# Capability mapping (how A2A messages map to skill)
capabilities:
  - name: "Approve PO"
    description: "Approve a purchase order"
    skill_method: "execute"
    input_mapping:
      po_number: "$.parts[0].content.po_number"
      approver_id: "$.context.agent_id"
    output_mapping:
      status: "$.status"
      approved_at: "$.created_at"

# Metadata (for skill marketplace reputation)
metadata:
  owner: "procurement-team"
  sla_ms: 5000
  success_rate: 0.99
  last_updated: "2026-02-21T10:30:00Z"
```

**Registry loader** (`ConnectorRegistry` interface):
```java
public interface ConnectorRegistry {
  Optional<ConnectorMetadata> findConnector(String connectorId);
  List<ConnectorMetadata> findByType(ConnectorType type);
  List<ConnectorMetadata> search(String skillName);  // <100ms
  void reload();  // Hot-reload on .integrations/ change
}
```

**Git-backed implementation**:
- Git watches `.integrations/` directory
- On commit, re-parse YAML files
- Cache in-memory (with TTL)
- Hash-check for staleness (SHA256 of connectors.yaml)

---

### 2.2 Five Reference Connectors

Each connector demonstrates a specific integration pattern.

#### Connector 1: Z.AI Approval Skill
**File**: `.integrations/z-ai/approval-skill.yaml`
**Type**: Z.AI connector
**Skill**: ApprovalSkill (existing in yawl/integration/a2a/skills)

**Scope**:
- Exposes SkillRequest/SkillResult as MCP tool schema
- Z.AI agent can invoke: "Approve PO-42"
- Idempotency: Uses idempotency_key to prevent duplicate approvals

**Test**: Z.AI agent autonomously approves 3 POs, verifies case completion

---

#### Connector 2: A2A Purchase Order Skill
**File**: `.integrations/a2a/purchase-order-skill.yaml`
**Type**: A2A adapter
**Skill**: PurchaseOrderSkill (new, minimal)

**Scope**:
- Handles A2A handoff message format (parts[], context)
- Routes message to skill.execute()
- Returns result in A2A response format
- Correlation ID chaining (req → case → result)

**Test**: External agent sends A2A message, verifies case launch

---

#### Connector 3: A2A Invoice Processing
**File**: `.integrations/a2a/invoice-skill.yaml`
**Type**: A2A adapter
**Skill**: InvoiceProcessingSkill (new, minimal)

**Scope**:
- Extracts invoice data from A2A message
- Launches "InvoiceApproval" workflow
- Returns invoice case reference

**Test**: A2A agent submits invoice, verifies workflow start

---

#### Connector 4: MCP Expense Report Tool
**File**: `.integrations/mcp/expense-report.yaml`
**Type**: MCP tool
**Skill**: ExpenseReportSkill (existing or new)

**Scope**:
- Registered as MCP tool: "submit_expense_report"
- Claude can invoke: "Submit expense report for Alice, $500"
- Returns confirmation + case ID

**Test**: Claude invokes tool, verifies result format

---

#### Connector 5: MCP Case Monitor Tool
**File**: `.integrations/mcp/case-monitor.yaml`
**Type**: MCP tool
**Skill**: CaseMonitoringSkill (new, stateless)

**Scope**:
- Read-only tool: "Get case status"
- Takes case_id, returns state + running tasks
- No side effects

**Test**: Claude queries case status, verifies output

---

### 2.3 Discovery API

**Endpoints** (REST-based, in `YawlIntegrationController`):

```
GET /integrations/connectors?type=z-ai
  ↓ 200 OK
  [
    {
      "id": "approval_skill",
      "name": "Approval Skill",
      "type": "z-ai",
      "skill_class": "org.yawlfoundation.yawl.integration.a2a.skills.ApprovalSkill",
      "permissions": ["approvals:execute"]
    }
  ]

GET /integrations/connectors/search?skill=approval
  ↓ 200 OK
  [similar]

GET /integrations/connectors/{connectorId}
  ↓ 200 OK
  {
    "id": "approval_skill",
    "name": "Approval Skill",
    ...full metadata...
  }

GET /integrations/mcp-tools
  ↓ 200 OK
  [MCP tool definitions for Claude integration]
```

**Latency SLA**: <100ms (cached, in-memory)

**Caching strategy**:
- Git-based change detection (file hash)
- TTL: 5 min or on commit hook
- Fallback: Re-parse YAML on cache miss

---

### 2.4 Basic Testing Framework

**Test structure**:
```
yawl-integration/src/test/
├─ java/org/yawlfoundation/yawl/integration/
│  ├─ connector/
│  │  ├─ ConnectorRegistryTest.java          # Registry CRUD
│  │  ├─ Z2AIConnectorTest.java              # Z.AI mapping
│  │  ├─ A2AProtocolAdapterTest.java         # A2A handoff
│  │  └─ MCPToolRegistrarTest.java           # MCP registration
│  └─ e2e/
│     ├─ Z2AIConnectorIntegrationTest.java   # Full Z.AI flow
│     ├─ A2AAdapterIntegrationTest.java      # Full A2A flow
│     └─ MCPToolIntegrationTest.java         # Full MCP flow
└─ resources/
   └─ integration/
      ├─ test-connector.yaml
      └─ mock-z-ai-agent.json
```

**Test approach**:
- Unit tests: Registry parsing, schema mapping
- Mock tests: Fake Z.AI agent, A2A server
- Integration tests: Real skill execution
- No external dependencies (Z.AI SDK not required for testing)

---

## 3. Tech Stack

### 3.1 Connector Definition: YAML Schema

**Why YAML?**
- Human-readable, version-controllable (Git)
- Standardized for cloud-native tooling
- Easy to validate (JSON Schema)

**Schema validation**:
```java
// ConnectorYamlValidator.java
public class ConnectorYamlValidator {
  private static final String SCHEMA_PATH =
    "classpath:connector-schema.json";

  public List<ValidationError> validate(String yamlContent) {
    // Parse YAML → JSON
    // Validate against schema
    // Return errors or empty list
  }
}
```

**Connector YAML schema** (`.integrations/connector-schema.json`):
- Defines required fields (id, name, type, skill)
- Enum validation for type (z-ai, a2a, mcp)
- Capability mapping schema
- Metadata constraints

---

### 3.2 Registry: Git Repository + In-Memory Cache

**Registry interface**:
```java
public interface ConnectorRegistry {
  // Query by ID (single)
  Optional<ConnectorMetadata> findConnector(String connectorId);

  // Query by type
  List<ConnectorMetadata> findByType(ConnectorType type);

  // Search by skill name (full-text)
  List<ConnectorMetadata> search(String skillName);

  // Get all connectors
  List<ConnectorMetadata> list();

  // Reload from Git (on commit hook)
  void reload();

  // Health check (returns staleness metrics)
  RegistryHealth health();
}

public record ConnectorMetadata(
  String id,
  String name,
  ConnectorType type,
  String skillClass,
  Set<String> permissions,
  Map<String, Object> config,
  LocalDateTime lastUpdated,
  MetadataStats stats  // reputation: success_rate, sla_ms, owner
) {}

public enum ConnectorType { Z_AI, A2A, MCP }
```

**Git-backed implementation**:
```java
public class GitBackedConnectorRegistry implements ConnectorRegistry {
  private final Path integrationsDir = Paths.get(".integrations");
  private volatile Map<String, ConnectorMetadata> cache;
  private volatile String lastHash;  // SHA256 of combined YAML

  @Scheduled(fixedRate = 300000)  // Every 5 min
  public void pollForChanges() {
    String currentHash = computeHash(integrationsDir);
    if (!currentHash.equals(lastHash)) {
      reload();
      lastHash = currentHash;
    }
  }

  public void reload() {
    Path connectorYaml = integrationsDir.resolve("connectors.yaml");
    Map<String, ConnectorMetadata> loaded = parseYaml(connectorYaml);
    cache = loaded;
  }

  public Optional<ConnectorMetadata> findConnector(String id) {
    return Optional.ofNullable(cache.get(id));
  }
}
```

**Post-commit hook** (`.git/hooks/post-commit`):
```bash
#!/bin/bash
# Reload registry on .integrations/ change
if git diff HEAD~1 --name-only | grep -q '^\.integrations/'; then
  curl -X POST http://localhost:8080/integrations/reload
fi
```

---

### 3.3 A2A Adapter: Lightweight Spring Component

**Adapter interface**:
```java
public interface A2AProtocolAdapter {
  /**
   * Translate A2A message to SkillRequest.
   * @param message A2A message (parts[], context)
   * @param skill target skill
   * @return SkillRequest with mapped parameters
   */
  SkillRequest toSkillRequest(A2AMessage message, A2ASkill skill);

  /**
   * Translate SkillResult back to A2A response.
   * @param result skill execution result
   * @param originalMessage A2A message (for correlation ID)
   * @return A2A response message
   */
  A2AMessage toA2AResponse(SkillResult result, A2AMessage originalMessage);
}
```

**Implementation**:
```java
@Component
public class DefaultA2AProtocolAdapter implements A2AProtocolAdapter {
  private final ConnectorRegistry registry;

  public SkillRequest toSkillRequest(A2AMessage msg, A2ASkill skill) {
    // 1. Extract text/structured parts from message
    String textContent = msg.parts().stream()
      .filter(p -> "text".equals(p.type()))
      .map(Part::content)
      .findFirst()
      .orElse("");

    Map<String, Object> structuredData = msg.parts().stream()
      .filter(p -> "structured".equals(p.type()))
      .map(p -> (Map<String, Object>) parseJson(p.content()))
      .findFirst()
      .orElse(Map.of());

    // 2. Apply connector's input_mapping (JSONPath)
    ConnectorMetadata connector = registry.findConnector(
      deriveConnectorId(skill)
    ).orElseThrow();

    Map<String, Object> mappedParams = applyInputMapping(
      connector.config().get("capabilities"),
      structuredData
    );

    // 3. Build SkillRequest
    return new SkillRequest(
      msg.context().requestId(),
      skill.getId(),
      mappedParams
    );
  }

  public A2AMessage toA2AResponse(SkillResult result,
                                   A2AMessage originalMessage) {
    // Preserve correlation ID, wrap result in A2A format
    return new A2AMessage(
      UUID.randomUUID().toString(),
      Instant.now(),
      List.of(new Part("structured", "application/json",
        toJson(result))),
      new Context(
        "yawl-engine",
        originalMessage.context().requestId(),
        originalMessage.context().correlationId()
      )
    );
  }
}
```

**Spring configuration**:
```java
@Configuration
public class A2AIntegrationConfig {
  @Bean
  public ConnectorRegistry connectorRegistry() {
    return new GitBackedConnectorRegistry();
  }

  @Bean
  public A2AProtocolAdapter protocolAdapter(ConnectorRegistry registry) {
    return new DefaultA2AProtocolAdapter(registry);
  }

  @Bean
  public IntegrationEventPublisher eventPublisher() {
    return new IntegrationEventPublisher();  // For metrics
  }
}
```

---

### 3.4 MCP Tool Registrar: Auto-Discovery

**Annotation**:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MCPTool {
  /**
   * Tool name (e.g., "submit_expense_report")
   */
  String name();

  /**
   * Tool description for Claude
   */
  String description();

  /**
   * Input JSON schema (optional; auto-generated from SkillRequest if not provided)
   */
  String inputSchemaJson() default "";

  /**
   * Output JSON schema (optional; auto-generated from SkillResult if not provided)
   */
  String outputSchemaJson() default "";
}
```

**Example skill**:
```java
@MCPTool(
  name = "submit_expense_report",
  description = "Submit an expense report for reimbursement"
)
public class ExpenseReportSkill implements A2ASkill {
  // Existing skill implementation

  @Override
  public String getId() { return "submit_expense_report"; }

  @Override
  public SkillResult execute(SkillRequest request) {
    // Real implementation
    return new SkillResult(true, Map.of("case_id", "E-12345"));
  }
}
```

**Registrar** (auto-discovery at startup):
```java
@Component
public class MCPToolRegistrar {
  private final ConnectorRegistry registry;
  private final YawlMcpServer mcpServer;

  @PostConstruct
  public void registerTools() {
    // Scan classpath for @MCPTool
    List<Class<?>> toolClasses = scanClasspath("@MCPTool");

    for (Class<?> toolClass : toolClasses) {
      MCPTool annotation = toolClass.getAnnotation(MCPTool.class);

      // Auto-generate schema from SkillRequest/SkillResult
      JSONSchema inputSchema = generateSchema(
        extractSkillRequestType(toolClass)
      );
      JSONSchema outputSchema = generateSchema(
        extractSkillResultType(toolClass)
      );

      // Register with MCP server
      mcpServer.registerTool(
        annotation.name(),
        annotation.description(),
        inputSchema,
        outputSchema
      );

      // Create registry entry
      registry.register(new ConnectorMetadata(
        annotation.name(),
        annotation.name(),
        ConnectorType.MCP,
        toolClass.getCanonicalName(),
        Set.of(),
        Map.of(),
        LocalDateTime.now(),
        new MetadataStats(1.0, 5000, "system")
      ));
    }
  }
}
```

---

## 4. Four-Week Roadmap

### Week 1: Connector Schema + Metadata Model

**Goal**: Core data structures and Git registry.

**Deliverables**:

1. **ConnectorMetadata record** (Java 25 record)
   - id, name, type, skill_class, permissions
   - config (Map<String, Object>)
   - metadata_stats (success_rate, sla_ms, owner, last_updated)

2. **Connector YAML schema** (JSON Schema)
   - Required fields, enum validation
   - Input/output mapping schema
   - Deployed to `.integrations/connector-schema.json`

3. **ConnectorRegistry interface + GitBackedConnectorRegistry**
   - findConnector(id), findByType(), search()
   - Git polling (5 min) + hash-based change detection
   - In-memory cache with TTL

4. **Unit tests** (50% code coverage)
   - Registry CRUD operations
   - YAML parsing + validation
   - Cache invalidation

**Effort**: 1 engineer, 5 days

**Deliverable files**:
```
src/org/yawlfoundation/yawl/integration/connector/
├─ ConnectorMetadata.java
├─ ConnectorRegistry.java
├─ GitBackedConnectorRegistry.java
├─ ConnectorYamlValidator.java
└─ ConnectorType.java

src/test/java/org/yawlfoundation/yawl/integration/connector/
├─ ConnectorRegistryTest.java
└─ ConnectorYamlValidatorTest.java

.integrations/
├─ connector-schema.json
└─ README.md
```

---

### Week 2: Z.AI Connector Template + 2 Examples

**Goal**: Z.AI tool invocation from MCP, idempotency handling.

**Deliverables**:

1. **Z.AIConnectorAdapter**
   - Maps SkillRequest/SkillResult to MCP tool schema
   - Generates tool schema from Java introspection
   - Handles idempotency keys (dedup on retry)
   - Error classification (transient vs permanent)

2. **MCPToolRegistrar + Auto-Discovery**
   - @MCPTool annotation
   - Scan classpath for tools
   - Register with YawlMcpServer

3. **Z.AI Approval Skill** (new minimal skill)
   - Approves POs (hardcoded logic for MVP)
   - Returns SkillResult with case_id

4. **Z.AI Expense Report Skill** (existing, wrapped)
   - Existing ExpenseReportSkill as MCP tool
   - Input schema: expense_amount, employee_id
   - Output: case_id, status

5. **Integration tests**
   - Mock Z.AI agent (JSON payloads)
   - Verify idempotency (same key → same result)
   - Test error handling

**Effort**: 1 engineer, 5 days

**Deliverable files**:
```
src/org/yawlfoundation/yawl/integration/connector/
├─ Z2AIConnectorAdapter.java
├─ MCPToolRegistrar.java
├─ MCPToolRegistrarConfig.java
└─ ToolSchemaGenerator.java

src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ ApprovalSkill.java  (existing, add @MCPTool)
└─ ExpenseReportSkill.java  (existing, add @MCPTool)

.integrations/
├─ z-ai/approval-skill.yaml
└─ z-ai/expense-report-skill.yaml

src/test/java/org/yawlfoundation/yawl/integration/connector/
├─ Z2AIConnectorTest.java
├─ MCPToolRegistrarTest.java
└─ e2e/Z2AIConnectorIntegrationTest.java

src/test/resources/integration/
└─ mock-z-ai-approval-message.json
```

---

### Week 3: A2A Adapter Pattern + 2 Examples

**Goal**: Agent-to-agent protocol handling, correlation ID chaining.

**Deliverables**:

1. **A2AProtocolAdapter**
   - toSkillRequest(A2AMessage, skill) → SkillRequest
   - toA2AResponse(SkillResult, originalMessage) → A2AMessage
   - Input/output mapping via JSONPath (connector config)
   - Correlation ID preservation

2. **PurchaseOrderSkill** (new minimal skill)
   - Executes PO approval workflow
   - Input: po_number, approver_id (from A2A context)
   - Output: case_id, status

3. **InvoiceProcessingSkill** (new minimal skill)
   - Launches invoice workflow
   - Input: invoice_data (from A2A message)
   - Output: case_id, created_at

4. **Connector YAML files** (A2A)
   - purchase-order-skill.yaml (capability mapping)
   - invoice-skill.yaml (capability mapping)

5. **Integration tests**
   - Mock A2A server (message format validation)
   - Test correlation ID chaining (request → case → response)
   - Test input/output mapping

**Effort**: 1 engineer, 5 days

**Deliverable files**:
```
src/org/yawlfoundation/yawl/integration/connector/
├─ A2AProtocolAdapter.java
├─ DefaultA2AProtocolAdapter.java
├─ CapabilityMapper.java
└─ JsonPathMapper.java

src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ PurchaseOrderSkill.java
└─ InvoiceProcessingSkill.java

.integrations/
├─ a2a/purchase-order-skill.yaml
└─ a2a/invoice-skill.yaml

src/test/java/org/yawlfoundation/yawl/integration/connector/
├─ A2AProtocolAdapterTest.java
├─ CapabilityMapperTest.java
└─ e2e/A2AAdapterIntegrationTest.java

src/test/resources/integration/
└─ mock-a2a-po-message.json
```

---

### Week 4: MCP Tool Registration + Discovery API

**Goal**: Claude integration, discovery endpoints, end-to-end testing.

**Deliverables**:

1. **Discovery API** (REST endpoints in IntegrationController)
   - GET /integrations/connectors?type=mcp
   - GET /integrations/connectors/search?skill=expense
   - GET /integrations/connectors/{id}
   - GET /integrations/mcp-tools (for Claude)

2. **CaseMonitoringSkill** (new, read-only)
   - MCP tool: "get_case_status"
   - Input: case_id
   - Output: state, running_tasks, progress

3. **Connector YAML** (MCP tools)
   - case-monitor.yaml
   - expense-report.yaml (moved from Week 2)

4. **Integration tests** (full end-to-end)
   - Test all 5 connectors (Z.AI, A2A×2, MCP×2)
   - Discovery API latency (<100ms)
   - Case completion verification

5. **Documentation**
   - .integrations/README.md (registry usage guide)
   - Connector development guide (for Phase 2)

**Effort**: 1 engineer, 5 days

**Deliverable files**:
```
src/org/yawlfoundation/yawl/integration/
├─ IntegrationController.java  (REST endpoints)
├─ IntegrationEventPublisher.java  (metrics)
└─ config/
   └─ ConnectorIntegrationConfig.java

src/org/yawlfoundation/yawl/integration/a2a/skills/
└─ CaseMonitoringSkill.java

.integrations/
├─ mcp/case-monitor.yaml
├─ mcp/expense-report.yaml
└─ README.md

src/test/java/org/yawlfoundation/yawl/integration/
├─ IntegrationControllerTest.java
└─ e2e/
   ├─ MCPToolIntegrationTest.java
   └─ DiscoveryAPITest.java
```

---

## 5. Integration Points

### 5.1 How Connectors Use YAWL InterfaceB

**InterfaceB** is the A2A protocol layer (engine adapter):

```
SkillConnector
  ↓ (invokes)
A2ASkill.execute(SkillRequest)
  ↓ (uses)
InterfaceB (YawlEngineAdapter)
  ↓ (calls)
YStatelessEngine (stateless case operations)
  OR
YEngine (stateful workflow execution)
```

**Example**: PurchaseOrderSkill.execute() calls:
```java
public SkillResult execute(SkillRequest request) {
  // Extract PO number from request
  String poNumber = (String) request.parameters().get("po_number");

  // Use InterfaceB to launch case
  YawlEngineAdapter engine = // injected
  String caseId = engine.launchCase(
    "PurchaseOrderApproval",
    Map.of("po_number", poNumber)
  );

  // Return result
  return new SkillResult(true, Map.of("case_id", caseId));
}
```

**InterfaceB methods used**:
- launchCase(specId, caseData) → case_id
- getCase(caseId) → YCase
- getWorkItems(caseId) → [YWorkItem]
- completeWorkItem(itemId, data) → result

**No new InterfaceB methods needed** (MVP uses existing API).

---

### 5.2 How Connectors Leverage Existing Auth (JWT)

**JWT flow** (existing, already in YawlA2AServer):

```
1. A2A agent calls YawlA2AServer with Bearer token
2. JwtAuthenticationProvider.validateToken(token)
3. Token claims → AuthenticatedPrincipal (agent_id, permissions)
4. Skill executes under principal's permissions
```

**Connector gains access to principal**:
```java
@Component
public class DefaultA2AProtocolAdapter {
  private final AuthenticationContext authContext;

  public SkillRequest toSkillRequest(A2AMessage msg, A2ASkill skill) {
    // Current principal (from JWT)
    AuthenticatedPrincipal principal = authContext.getPrincipal();

    // Skill checks permissions
    if (!skill.canExecute(principal.permissions())) {
      throw new A2AException("Insufficient permissions");
    }

    // Continue...
  }
}
```

**No changes to auth layer** (MVP reuses existing JWT validation).

---

### 5.3 How Connectors Feed Metrics to Data Marketplace

**Metrics collected per connector invocation**:

```java
public record IntegrationMetric(
  String connectorId,
  String skillId,
  ConnectorType type,
  boolean success,
  long durationMs,
  String errorCode,  // null if success
  LocalDateTime timestamp
) {}
```

**Publisher**:
```java
@Component
public class IntegrationEventPublisher {
  private final MetricsRegistry metricsRegistry;

  public void publishConnectorInvocation(IntegrationMetric metric) {
    // Publish to metrics system (Prometheus counter)
    metricsRegistry.counter(
      "connector.invocation",
      Tags.of(
        "connector_id", metric.connectorId(),
        "type", metric.type().name(),
        "status", metric.success() ? "success" : "failure"
      )
    ).increment();

    // For Phase 2: Send to data marketplace
  }
}
```

**Metrics pipeline** (Phase 2):
```
IntegrationEventPublisher
  ↓ (publishes to)
Kafka topic: integration.events
  ↓ (consumed by)
DataMarketplaceService
  ↓ (aggregates)
ConnectorReputation (success_rate, sla_ms, owner)
```

**MVP scope**: Metrics published to local Prometheus only. Phase 2 adds Kafka + data marketplace integration.

---

### 5.4 How Connectors Participate in Skill Reputation

**Reputation model** (Phase 2, but schema prepared in MVP):

```java
public record ConnectorReputation(
  String connectorId,
  double successRate,    // % of invocations that succeeded
  long slaMs,           // avg execution time
  LocalDateTime lastUpdated
) {}
```

**Metadata includes reputation** (from connector YAML):

```yaml
metadata:
  owner: "procurement-team"
  sla_ms: 5000
  success_rate: 0.99
  last_updated: "2026-02-21T10:30:00Z"
```

**Discovery API returns reputation**:
```java
GET /integrations/connectors/purchase_order_skill
↓ 200 OK
{
  "id": "purchase_order_skill",
  "name": "Purchase Order Skill",
  "type": "a2a",
  ...
  "reputation": {
    "success_rate": 0.99,
    "sla_ms": 5000,
    "last_updated": "2026-02-21T10:30:00Z"
  }
}
```

**MVP scope**: Reputation schema + display in API. Phase 2 adds Kafka event stream + real-time aggregation.

---

## 6. Code Artifacts (Deliverables Summary)

### Java Classes (18 new/modified)

```
Connector Core:
  src/org/yawlfoundation/yawl/integration/connector/
    ├─ ConnectorMetadata.java (record)
    ├─ ConnectorRegistry.java (interface)
    ├─ GitBackedConnectorRegistry.java
    ├─ ConnectorYamlValidator.java
    ├─ ConnectorType.java (enum)
    ├─ ConnectorIntegrationConfig.java (Spring)
    └─ IntegrationException.java

Z.AI Adapter:
  src/org/yawlfoundation/yawl/integration/connector/
    ├─ Z2AIConnectorAdapter.java
    ├─ ToolSchemaGenerator.java
    └─ MCPToolRegistrar.java

A2A Adapter:
  src/org/yawlfoundation/yawl/integration/connector/
    ├─ A2AProtocolAdapter.java (interface)
    ├─ DefaultA2AProtocolAdapter.java
    ├─ CapabilityMapper.java
    └─ JsonPathMapper.java

REST API:
  src/org/yawlfoundation/yawl/integration/
    ├─ IntegrationController.java
    └─ IntegrationEventPublisher.java

Skills (New/Modified):
  src/org/yawlfoundation/yawl/integration/a2a/skills/
    ├─ ApprovalSkill.java (@MCPTool annotation added)
    ├─ ExpenseReportSkill.java (@MCPTool annotation added)
    ├─ PurchaseOrderSkill.java (new)
    ├─ InvoiceProcessingSkill.java (new)
    └─ CaseMonitoringSkill.java (new)
```

### Configuration Files

```
.integrations/
├─ connector-schema.json (JSON Schema for validation)
├─ connectors.yaml (master registry, auto-generated or manual)
├─ z-ai/
│  ├─ approval-skill.yaml
│  └─ expense-report-skill.yaml
├─ a2a/
│  ├─ purchase-order-skill.yaml
│  └─ invoice-skill.yaml
├─ mcp/
│  ├─ expense-report.yaml
│  └─ case-monitor.yaml
└─ README.md (registry usage guide)
```

### Test Classes (12 new)

```
src/test/java/org/yawlfoundation/yawl/integration/connector/
├─ ConnectorRegistryTest.java
├─ ConnectorYamlValidatorTest.java
├─ Z2AIConnectorTest.java
├─ MCPToolRegistrarTest.java
├─ A2AProtocolAdapterTest.java
├─ CapabilityMapperTest.java
├─ IntegrationControllerTest.java
└─ e2e/
   ├─ Z2AIConnectorIntegrationTest.java
   ├─ A2AAdapterIntegrationTest.java
   ├─ MCPToolIntegrationTest.java
   └─ DiscoveryAPITest.java

src/test/resources/integration/
├─ test-connector.yaml
├─ mock-z-ai-approval-message.json
└─ mock-a2a-po-message.json
```

---

## 7. Success Criteria

| Criterion | Target | Verification |
|-----------|--------|--------------|
| **5 working connectors** | All deploy without errors | Unit + integration tests pass |
| **Discovery API <100ms** | P50 latency <100ms | JMH benchmark |
| **100% skill success rate** | No failed invocations in happy path | Integration tests (5 scenarios) |
| **Z.AI idempotency** | Same key → same result | Retry test (mock agent) |
| **A2A correlation chaining** | Request → case → response | Message tracing test |
| **MCP tool invocation** | Claude can call tool | MCP client test |
| **Zero TODOs/mocks** | All code is real | Grep for H violations |
| **Git-backed registry** | Hot reload on .integrations/ commit | Commit hook test |
| **Test coverage** | ≥80% (integration module) | Jacoco report |

---

## 8. Dependencies & Risks

### Dependencies

**Existing (already available)**:
- YawlA2AServer (A2A protocol)
- YawlMcpServer (MCP transport)
- InterfaceB / YStatelessEngine (skill execution)
- JwtAuthenticationProvider (authentication)

**New (need to add)**:
- YAML parsing: `org.yaml:snakeyaml:2.0` (standard, no Z.AI SDK)
- JSON Path: `com.jayway.jsonpath:json-path:2.8.0` (lightweight)
- Java 25 features: Records, sealed classes, pattern matching (no new deps)

**Risk**: Z.AI SDK not required for MVP (use mocks in tests).

### Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Git polling inefficient | Low | Med (high CPU) | Use file system watch instead (Java 25 WatchService) |
| YAML parsing errors | Med | Low (fall back to cache) | Schema validation + unit tests |
| A2A message format drift | Med | Med (breaking change) | Version negotiation in agent card |
| JWT token expiry during long task | Low | Low (fail + retry) | Inherit JWT from original request |
| Schema generation from reflection | Med | Low (manual fallback) | Annotated schema in connector.yaml |

---

## 9. Phase 2 Preview (Out of MVP Scope)

- **OAuth flow**: External agent registration + credential management
- **Webhooks**: Async result callbacks (case completion events)
- **Data transformers**: Skill output → marketplace format
- **Advanced auth**: SPIFFE, API keys per skill
- **Real-time metrics**: Kafka stream → connector reputation
- **Connector marketplace UI**: Discover + rate connectors

---

## 10. Reference Specifications

- **Z.AI Integration Design**: Z.AI-YAWL-INTEGRATION-DESIGN.md
- **A2A Protocol Research**: A2A_PROTOCOL_RESEARCH.md
- **MCP Tool Design**: MCP_LLM_TOOL_DESIGN.md
- **YAWL InterfaceB**: src/org/yawlfoundation/yawl/engine/interfaceB/
- **A2A Skill Base**: src/org/yawlfoundation/yawl/integration/a2a/skills/A2ASkill.java
- **Auth Stack**: src/org/yawlfoundation/yawl/integration/a2a/auth/

---

## 11. Glossary

| Term | Definition |
|------|-----------|
| **Connector** | YAML manifest + metadata mapping skill to external system (Z.AI, A2A, MCP) |
| **Skill** | Java class implementing A2ASkill (execute method, permissions) |
| **Registry** | Git-backed in-memory cache of connector YAML files |
| **Adapter** | Protocol translator (A2AMessage ↔ SkillRequest, MCP schema ↔ Java) |
| **Handoff** | A2A agent-to-agent message passing (correlation ID chained) |
| **Idempotency** | Retrying same request returns same result (dedup key based) |
| **Reputation** | Connector's success_rate, sla_ms, owner (for marketplace ranking) |

---

**Next Step**: Begin Week 1 implementation (Connector schema + GitBackedConnectorRegistry).
**Review**: Weekly checkpoint demos every Friday.

