# Plan: MCP v1 Protocol Compliance Rewrite

## Context

YAWL v6.0.0/v6.0 implements MCP (Model Context Protocol) for AI tool integration. This plan focuses **exclusively on rewriting MCP support for v1** to achieve full compliance with the latest standards.

**Current State:**
- **MCP**: Currently uses SDK 0.17.2, latest is 0.17.1 (no v1 yet)
- **Build**: Maven excludes MCP tests from compilation (pom.xml lines 273-379)
- **Validation**: Existing MCP compliance script but missing schema validation and comprehensive testing
- **Latest SDK**: v0.17.1 with protocol version 2025-06-18 support

**Critical Issues:**
1. MCP SDK 0.17.2 → 0.17.1 upgrade (no v1 available yet)
2. MCP tests excluded from build (pom.xml lines 373-378)
3. No JSON schema validation for MCP protocol messages
4. Missing comprehensive test coverage (>80%)
5. Need STDIO transport validation
6. Module restructuring in v0.13.0 requires updates

---

## Implementation Strategy

### Phase 1: MCP SDK 0.17.1 Upgrade (Critical)

#### 1.1 Update Dependencies
Update pom.xml property and dependencies:

```xml
<!-- Update version property -->
<mcp.version>0.17.1</mcp.version>

<!-- Update dependencies - remove mcp-json-jackson2, add mcp-json-jackson3 -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-core</artifactId>
    <version>${mcp.version}</version>
</dependency>
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-json-jackson3</artifactId>
    <version>${mcp.version}</version>
</dependency>
<!-- Remove mcp dependency (it's now an umbrella module) -->
<!-- Remove mcp-json dependency (it's now an interface) -->
<!-- Remove mcp-json-jackson2 dependency (use jackson3 instead) -->
```

#### 1.2 Rewrite YawlMcpServer.java
- Update to use MCP 0.17.1 API changes
- Implement proper JSON-RPC 2.0 handling
- Add error codes: -32700 (Parse Error), -32600 (Invalid Request), -32601 (Method Not Found)
- Ensure STDIO transport compatibility
- Add structured logging with MCP notifications
- Update to use new module structure (mcp-core, mcp-json-jackson3)

#### 1.3 Update Tool Specifications
- Migrate all 15 tools to use new API from v0.13.0+
- Update CallToolResult to use Object instead of Map<String,Object> for structuredContent
- Add proper input/output validation
- Implement tool execution timeouts
- Add progress notifications for long-running operations
- Use builder pattern instead of deprecated constructors

#### 1.4 Update Resource and Prompt Providers
- Update to use new resource template API from v0.13.0
- Implement proper list/read semantics
- Add pagination support for large resources
- Implement prompt templating
- Update to use resources/templates/list instead of resources/list

### Phase 2: MCP Schema Validation (High Priority)

#### 2.1 JSON Schema Creation
Create schemas in `schemas/mcp/`:
- `mcp-jsonrpc.schema.json` - JSON-RPC 2.0 envelope
- `mcp-initialize-request.schema.json` - Initialize handshake request
- `mcp-initialize-response.schema.json` - Initialize handshake response
- `mcp-tool-call-request.schema.json` - Tool call request format
- `mcp-tool-call-response.schema.json` - Tool call response format
- `mcp-resource-read.schema.json` - Resource read request/response
- `mcp-error.schema.json` - JSON-RPC error codes (-32700, -32600, etc.)

#### 2.2 MCP Protocol Validation
- JSON-RPC 2.0 message validation
- Proper error code handling
- Request/response format validation
- Notification message validation

### Phase 3: Validation Infrastructure (High Priority)

#### 3.1 JSON Schema Validator
Create `scripts/validation/lib/json-schema-validator.sh`:
- Support `check-jsonschema` or `ajv-cli`
- `validate_json(document, schema)` function
- Integration with test runners

#### 3.2 MCP Stress Testing
Create validation scripts:
- `validate-mcp-concurrent.sh` - Concurrent tool calls
- `validate-mcp-throughput.sh` - Performance under load
- `validate-mcp-timeouts.sh` - Timeout handling

#### 3.3 Master Orchestrator Update
Update `scripts/validation/validate-all.sh`:
```bash
declare -A SUITES=(
    ["mcp-compliance"]="${SCRIPT_DIR}/mcp/validate-mcp-compliance.sh"
    ["mcp-schema"]="${SCRIPT_DIR}/mcp/validate-mcp-schema.sh"
    ["mcp-stdio"]="${SCRIPT_DIR}/mcp/validate-mcp-stdio.sh"
    ["mcp-concurrent"]="${SCRIPT_DIR}/mcp/validate-mcp-concurrent.sh"
    ["mcp-throughput"]="${SCRIPT_DIR}/mcp/validate-mcp-throughput.sh"
)
```

### Phase 4: Java Test Implementation (High Priority)

#### 4.1 MCP Integration Tests
Create `test/org/yawlfoundation/yawl/integration/mcp/`:
- `McpToolIntegrationTest.java` - 15 tools with live engine
- `YawlResourceProviderTest.java` - 6 resources validation
- `YawlPromptProviderTest.java` - 4 prompts validation
- `McpStdioTransportTest.java` - STDIO transport validation


### Phase 5: Enable Build and CI/CD (Medium Priority)

#### 5.1 Update pom.xml
Remove exclusions for MCP:
```xml
<!-- Remove these excludes -->
<exclude>**/mcp/**</exclude>
<testExclude>**/mcp/**</testExclude>
```

#### 5.2 GitHub Workflow
Add validation jobs:
- Schema validation
- Integration tests
- Stress testing
- Chaos testing

---

## Critical Files Reference

### Files to Create (18 new)
```
schemas/mcp/
├── mcp-jsonrpc.schema.json
├── mcp-initialize-request.schema.json
├── mcp-initialize-response.schema.json
├── mcp-tool-call-request.schema.json
├── mcp-tool-call-response.schema.json
├── mcp-resource-read.schema.json
└── mcp-error.schema.json

scripts/validation/
├── mcp/
│   ├── validate-mcp-schema.sh
│   ├── validate-mcp-stdio.sh
│   ├── validate-mcp-concurrent.sh
│   └── validate-mcp-throughput.sh
├── lib/
│   ├── json-schema-validator.sh
│   └── validation-helpers.sh
└── integration/
    └── validation-helpers.sh

test/org/yawlfoundation/yawl/integration/mcp/
├── McpToolIntegrationTest.java
├── YawlResourceProviderTest.java
├── YawlPromptProviderTest.java
└── McpStdioTransportTest.java
```

### Files to Modify (5 files)
| File | Changes |
|------|---------|
| `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java` | MCP v1 API update |
| `src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java` | v1 tool specs |
| `pom.xml` | Remove MCP exclusions, add v1 SDK |
| `scripts/validation/validate-all.sh` | Add new validation suites |
| `test/.../mcp/McpTestSuite.java` | Include new tests |

---

## Success Criteria

| Category | Requirement | Validation |
|----------|-------------|------------|
| **MCP 0.17.1** | All 15 tools with 0.17.1 SDK | McpToolIntegrationTest passes |
| **JSON Schema** | All messages validate | Schema tests pass |
| **STDIO Transport** | Message exchange | McpStdioTransportTest passes |
| **Concurrent Performance** | 50+ concurrent calls | Concurrent validation passes |
| **Module Structure** | Proper dependency on mcp-core | Maven resolves correctly |
| **Coverage** | 80%+ on MCP packages | JaCoCo report |
| **Build** | MCP tests enabled | Maven compiles all tests |

---

## Verification Commands

```bash
# 1. Run all validations
bash scripts/validation/validate-all.sh

# 2. Test MCP compliance
bash scripts/validation/mcp/validate-mcp-compliance.sh

# 3. Run Java tests
mvn -T 1.5C test -Dtest="**/mcp/**/*Test"

# 5. Build without exclusions
mvn clean compile
```

---

## Risk Mitigation

1. **Backward Compatibility**: Maintain compatibility layer during MCP v1 migration
2. **Test Isolation**: Use embedded H2 for integration tests
3. **Performance**: Monitor with OTEL, implement timeouts
4. **Security**: Validate all inputs, implement proper auth
5. **Rollback**: Keep v0.17.2 dependencies temporarily

---

## Timeline

- **Week 1**: MCP v1 rewrite, JSON schemas
- **Week 2**: A2A validation, stress tests
- **Week 3**: Java tests, CI/CD integration
- **Week 4**: Performance optimization, documentation

**Total**: ~4 weeks for 100% compliance