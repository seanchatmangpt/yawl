# Integration Marketplace MVP - Implementation Checklist

**Project**: YAWL Integrations Marketplace (4-week MVP)
**Start Date**: 2026-02-24 (Week 1 begins)
**Team**: 2 engineers
**Budget**: $50K

---

## Week 1: Connector Schema + Metadata Model

**Goal**: Core data structures, Git registry, schema validation.
**Engineer Lead**: Engineer A
**Days**: 5 days (Mon-Fri)

### Day 1: Data Models (ConnectorMetadata, Enums)

**Tasks**:
- [ ] Create `ConnectorMetadata.java` (Java 25 record)
  - [ ] id, name, type, version, description
  - [ ] skill_class, permissions (Set<String>)
  - [ ] config (Map<String, Object>)
  - [ ] metadata (owner, sla_ms, success_rate, last_updated)
  - [ ] Add @RecordComponent annotations for immutability
  - [ ] Add compact constructor for validation

- [ ] Create `ConnectorType.java` enum
  - [ ] Z_AI, A2A, MCP values
  - [ ] fromString() method (case-insensitive)

- [ ] Create `ConnectorException.java` (extends RuntimeException)
  - [ ] InvalidConnectorException
  - [ ] ConnectorNotFound
  - [ ] RegistryLoadError

**Deliverables**:
- [ ] 3 files compiled, no errors
- [ ] Unit test for ConnectorMetadata (record equality, serialization)

**Time estimate**: 1 day

---

### Day 2: Registry Interface + Git Implementation

**Tasks**:
- [ ] Create `ConnectorRegistry.java` interface
  - [ ] findConnector(String id) → Optional<ConnectorMetadata>
  - [ ] findByType(ConnectorType type) → List<ConnectorMetadata>
  - [ ] search(String skillName) → List<ConnectorMetadata>
  - [ ] list() → List<ConnectorMetadata>
  - [ ] reload() → void
  - [ ] health() → RegistryHealth

- [ ] Create `RegistryHealth.java` record
  - [ ] status (UP, DOWN)
  - [ ] connectors_loaded (int)
  - [ ] last_reload (LocalDateTime)
  - [ ] cache_hit_rate (double)
  - [ ] last_error (Optional<String>)

- [ ] Create `GitBackedConnectorRegistry.java`
  - [ ] Field: `Path integrationsDir = Paths.get(".integrations")`
  - [ ] Field: `Map<String, ConnectorMetadata> cache` (volatile)
  - [ ] Field: `String lastHash` (SHA256 of combined YAML)
  - [ ] Implement findConnector() (cache lookup)
  - [ ] Implement search() (simple string matching on name, skill_class)
  - [ ] Implement reload() (parse all YAML files, update cache)
  - [ ] Implement health() (return cache metrics)

- [ ] Create `@Scheduled` method `pollForChanges()`
  - [ ] Every 5 minutes
  - [ ] Compute SHA256 hash of `.integrations/connectors.yaml` + all connector files
  - [ ] If hash changed, call reload()
  - [ ] Log cache hit/miss stats

**Deliverables**:
- [ ] Registry interface + implementation compiled
- [ ] Unit tests: Registry CRUD operations
- [ ] Integration test: Reload on file change

**Time estimate**: 1.5 days

---

### Day 3: YAML Parsing + Schema Validation

**Tasks**:
- [ ] Add dependencies to `yawl-integration/pom.xml`
  - [ ] org.yaml:snakeyaml:2.0 (YAML parsing)
  - [ ] com.fasterxml.jackson:jackson-databind (JSON validation)
  - [ ] com.github.java-json-tools:json-schema-validator:2.2.14 (schema)

- [ ] Create `ConnectorYamlParser.java`
  - [ ] parseYaml(Path yamlFile) → ConnectorMetadata
  - [ ] Handle YAML parse errors → ConnectorException
  - [ ] Convert YAML types to Java types (String, int, List, Map)

- [ ] Create `ConnectorYamlValidator.java`
  - [ ] Load schema from `classpath:connector-schema.json`
  - [ ] validate(String yamlContent) → List<ValidationError>
  - [ ] validate(ConnectorMetadata metadata) → List<ValidationError>

- [ ] Create `ValidationError.java` record
  - [ ] path (JSONPointer)
  - [ ] message (violation description)
  - [ ] severity (ERROR, WARNING)

**Deliverables**:
- [ ] Maven build succeeds (dependencies added)
- [ ] Unit tests: YAML parsing + validation
- [ ] Test data: 3 sample connector YAMLs in src/test/resources/

**Time estimate**: 1.5 days

---

### Day 4: Spring Integration + Configuration

**Tasks**:
- [ ] Create `ConnectorIntegrationConfig.java` (Spring @Configuration)
  - [ ] @Bean ConnectorRegistry connectorRegistry()
  - [ ] @Bean ConnectorYamlValidator validator()
  - [ ] @Bean ConnectorYamlParser parser()
  - [ ] Initialize registry with `.integrations/` path

- [ ] Create `ConnectorProperties.java` (Spring @ConfigurationProperties)
  - [ ] registryPath (default: ".integrations")
  - [ ] cacheTtlSeconds (default: 300)
  - [ ] pollIntervalSeconds (default: 300)
  - [ ] strict mode (fail on validation error, default: true)

- [ ] Create `IntegrationException.java` (@ControllerAdvice error handler)
  - [ ] Handle ConnectorException → 400 Bad Request
  - [ ] Handle ConnectorNotFound → 404 Not Found
  - [ ] Handle RegistryLoadError → 503 Service Unavailable
  - [ ] Return JSON error response with correlation ID

- [ ] Create `ConnectorRegistry.reload()` as REST endpoint
  - [ ] POST /integrations/reload
  - [ ] Force immediate reload of registry
  - [ ] Return RegistryHealth

- [ ] Add to Spring test context
  - [ ] Create test application properties
  - [ ] Use in-memory mock registry for unit tests

**Deliverables**:
- [ ] Spring component scan finds all beans
- [ ] Integration test: Spring context loads correctly
- [ ] Test: POST /integrations/reload returns 200 OK

**Time estimate**: 1.5 days

---

### Day 5: Integration Tests + Documentation

**Tasks**:
- [ ] Create `ConnectorRegistryTest.java`
  - [ ] Test findConnector(id) → found
  - [ ] Test findConnector(id) → not found
  - [ ] Test findByType(Z_AI) → correct subset
  - [ ] Test search("approval") → finds matching connectors
  - [ ] Test cache invalidation on reload()

- [ ] Create `ConnectorYamlValidatorTest.java`
  - [ ] Test valid YAML → no errors
  - [ ] Test invalid type → validation error
  - [ ] Test missing required fields → validation errors
  - [ ] Test incorrect version format → validation error

- [ ] Create `.integrations/connectors.yaml` (master registry)
  - [ ] Auto-generated from all connector YAML files
  - [ ] Or manually created for reference

- [ ] Create `.integrations/.gitignore`
  - [ ] Exclude `connectors.yaml` (auto-generated)
  - [ ] Track `connector-schema.json` and individual YAML files

- [ ] Update documentation
  - [ ] .integrations/README.md (add Week 1 progress)
  - [ ] Create architecture diagram (registry + Git integration)

- [ ] Run full test suite
  - [ ] `mvn test -Dtest=*RegistryTest*`
  - [ ] `mvn test -Dtest=*ValidatorTest*`
  - [ ] Goal: ≥80% line coverage in connector module

**Deliverables**:
- [ ] All tests pass
- [ ] Build succeeds (mvn clean verify)
- [ ] Code review checklist completed

**Time estimate**: 1.5 days

---

## Week 1 Validation Gate (End of Week)

**Criteria**:
- [ ] All 3 deliverable files compile without errors
- [ ] Unit + integration tests pass (≥80% coverage)
- [ ] Registry can load sample YAML files
- [ ] Cache hits/misses tracked correctly
- [ ] Schema validation catches invalid connectors
- [ ] No TODO/mock implementations (real code or throw)

**Demo**:
- [ ] Show ConnectorRegistry loading 3 test connectors
- [ ] Show POST /integrations/reload returns health
- [ ] Show schema validation rejecting invalid YAML
- [ ] Show cache statistics (hits, misses, hit_rate)

**Sign-off**: Lead reviews + approves architecture

---

## Week 2: Z.AI Connector Template + 2 Examples

**Goal**: Z.AI tool invocation, idempotency, MCP schema generation.
**Engineer Lead**: Engineer B
**Days**: 5 days

### Day 6: Z.AIConnectorAdapter + Schema Generator

**Tasks**:
- [ ] Create `Z2AIConnectorAdapter.java`
  - [ ] Implements Z.AI connector pattern
  - [ ] Maps SkillRequest/SkillResult to MCP tool schema
  - [ ] Handles idempotency keys (dedup on retry)

- [ ] Create `ToolSchemaGenerator.java`
  - [ ] generateInputSchema(Class<? extends A2ASkill>) → JSONSchema
  - [ ] generateOutputSchema(Class<? extends A2ASkill>) → JSONSchema
  - [ ] Use Java introspection on SkillRequest/SkillResult
  - [ ] Support @JsonProperty annotations for field mapping

**Deliverables**:
- [ ] 2 files, compiles without errors
- [ ] Unit tests: schema generation from skills

**Time estimate**: 1 day

---

### Day 7: MCPToolRegistrar + Auto-Discovery

**Tasks**:
- [ ] Create `@MCPTool` annotation
  - [ ] name, description, inputSchemaJson, outputSchemaJson

- [ ] Create `MCPToolRegistrar.java`
  - [ ] @PostConstruct method
  - [ ] Scan classpath for @MCPTool classes
  - [ ] Auto-generate schema if not provided
  - [ ] Register with YawlMcpServer

- [ ] Implement classpath scanning
  - [ ] Use Spring's ClassPathScanningCandidateComponentProvider
  - [ ] Filter for classes with @MCPTool
  - [ ] Load each skill class

- [ ] Create `MCPToolRegistrarConfig.java`
  - [ ] Spring @Configuration
  - [ ] @Bean MCPToolRegistrar
  - [ ] Inject YawlMcpServer

**Deliverables**:
- [ ] 3 files, compiles
- [ ] Unit tests: MCP tool registration (mock server)
- [ ] Integration test: Registrar finds @MCPTool classes

**Time estimate**: 1 day

---

### Day 8: ApprovalSkill + ExpenseReportSkill

**Tasks**:
- [ ] Create `ApprovalSkill.java` (new, minimal)
  - [ ] Add @MCPTool annotation
  - [ ] Implement execute(SkillRequest)
  - [ ] Real logic: Check permissions, launch workflow
  - [ ] Return case_id on success

- [ ] Add @MCPTool to existing `ExpenseReportSkill.java`
  - [ ] Annotation with name, description

- [ ] Create `.integrations/z-ai/approval-skill.yaml`
  - [ ] type: z-ai
  - [ ] skill: ApprovalSkill
  - [ ] capabilities mapping

- [ ] Create `.integrations/z-ai/expense-report-skill.yaml`
  - [ ] type: z-ai
  - [ ] skill: ExpenseReportSkill
  - [ ] capabilities mapping

**Deliverables**:
- [ ] 2 skills + 2 YAMLs
- [ ] ConnectorRegistry can load both
- [ ] MCPToolRegistrar discovers both

**Time estimate**: 1 day

---

### Day 9: Integration Tests

**Tasks**:
- [ ] Create `Z2AIConnectorTest.java`
  - [ ] Test schema generation from skill
  - [ ] Test idempotency key tracking
  - [ ] Test error classification

- [ ] Create `MCPToolRegistrarTest.java`
  - [ ] Test classpath scanning
  - [ ] Test registration with mock server
  - [ ] Test schema auto-generation

- [ ] Create `Z2AIConnectorIntegrationTest.java`
  - [ ] Mock Z.AI agent (JSON payloads)
  - [ ] Invoke skill via MCP path
  - [ ] Verify response format
  - [ ] Test retry with same idempotency key

- [ ] Create mock Z.AI agent
  - [ ] `src/test/resources/mock-z-ai-approval-message.json`
  - [ ] Mock MCP messages

**Deliverables**:
- [ ] All tests pass
- [ ] ≥80% coverage for Week 2 code
- [ ] E2E test: Mock agent invokes real skill

**Time estimate**: 1.5 days

---

### Day 10: Documentation + Code Review

**Tasks**:
- [ ] Update .integrations/README.md
  - [ ] Document Z.AI connector pattern
  - [ ] Add @MCPTool annotation examples

- [ ] Create developer guide
  - [ ] How to create a Z.AI connector
  - [ ] Best practices for idempotency

- [ ] Run full test suite
  - [ ] mvn clean verify (all tests pass)
  - [ ] Jacoco coverage report
  - [ ] No H violations (grep for TODO, mock, stub)

- [ ] Code review
  - [ ] Architecture review with lead
  - [ ] Pair review of 2 complex functions

**Deliverables**:
- [ ] All tests pass
- [ ] Coverage ≥80%
- [ ] Code review approved

**Time estimate**: 1 day

---

## Week 2 Validation Gate

**Criteria**:
- [ ] Z.AIConnectorAdapter + ToolSchemaGenerator work
- [ ] @MCPTool annotation + auto-discovery functional
- [ ] 2 reference skills (ApprovalSkill, ExpenseReportSkill)
- [ ] 2 reference YAMLs in .integrations/z-ai/
- [ ] Mock Z.AI agent can invoke skills
- [ ] Idempotency key prevents duplicates
- [ ] All tests pass, ≥80% coverage

**Demo**:
- [ ] Show schema generation from skill
- [ ] Show MCP tool registration
- [ ] Show mock agent invoking skill twice with same idempotency key
- [ ] Verify only 1 case created (dedup worked)

---

## Week 3: A2A Adapter Pattern + 2 Examples

**Goal**: Agent-to-agent protocol, correlation ID chaining, input/output mapping.
**Engineer Lead**: Engineer A (returns from Week 1)
**Days**: 5 days

### Day 11: A2AProtocolAdapter + Capability Mapper

**Tasks**:
- [ ] Create `A2AProtocolAdapter.java` interface
  - [ ] toSkillRequest(A2AMessage, skill) → SkillRequest
  - [ ] toA2AResponse(SkillResult, originalMessage) → A2AMessage

- [ ] Create `DefaultA2AProtocolAdapter.java`
  - [ ] Implement message parsing (parts[], context)
  - [ ] Extract text/structured data
  - [ ] Preserve correlation ID

- [ ] Create `CapabilityMapper.java`
  - [ ] Apply input_mapping (JSONPath)
  - [ ] Map external data → SkillRequest parameters
  - [ ] Apply output_mapping (JSONPath reverse)
  - [ ] Map SkillResult → A2A response

- [ ] Create `JsonPathMapper.java`
  - [ ] Utility for JSONPath evaluation
  - [ ] Handle missing paths gracefully
  - [ ] Support nested objects/arrays

**Deliverables**:
- [ ] 4 files, compiles
- [ ] Unit tests: message parsing, mapping
- [ ] Test data: A2A message examples

**Time estimate**: 1.5 days

---

### Day 12: PurchaseOrderSkill + InvoiceProcessingSkill

**Tasks**:
- [ ] Create `PurchaseOrderSkill.java` (new)
  - [ ] Implement execute(SkillRequest)
  - [ ] Extract po_number, approver_id from request
  - [ ] Call InterfaceB to launch case
  - [ ] Return case_id

- [ ] Create `InvoiceProcessingSkill.java` (new)
  - [ ] Implement execute(SkillRequest)
  - [ ] Extract invoice_data from request
  - [ ] Call InterfaceB to launch case
  - [ ] Return case_id + created_at

- [ ] Create `.integrations/a2a/purchase-order-skill.yaml`
  - [ ] type: a2a
  - [ ] skill: PurchaseOrderSkill
  - [ ] capabilities + input/output mapping

- [ ] Create `.integrations/a2a/invoice-skill.yaml`
  - [ ] type: a2a
  - [ ] skill: InvoiceProcessingSkill
  - [ ] capabilities + input/output mapping

**Deliverables**:
- [ ] 2 skills + 2 YAMLs
- [ ] ConnectorRegistry can load both
- [ ] Real skill logic (no mocks)

**Time estimate**: 1 day

---

### Day 13: A2A Adapter Integration Tests

**Tasks**:
- [ ] Create `A2AProtocolAdapterTest.java`
  - [ ] Test message parsing
  - [ ] Test capability mapping
  - [ ] Test correlation ID preservation

- [ ] Create `CapabilityMapperTest.java`
  - [ ] Test input_mapping with JSONPath
  - [ ] Test output_mapping reverse
  - [ ] Test missing fields (error handling)

- [ ] Create `A2AAdapterIntegrationTest.java`
  - [ ] Mock A2A server
  - [ ] Send PO approval message
  - [ ] Verify case launched
  - [ ] Verify correlation ID in response

- [ ] Create mock A2A messages
  - [ ] `src/test/resources/mock-a2a-po-message.json`
  - [ ] `src/test/resources/mock-a2a-invoice-message.json`

**Deliverables**:
- [ ] All tests pass
- [ ] ≥80% coverage for adapter code
- [ ] E2E: Mock A2A agent → case creation

**Time estimate**: 1.5 days

---

### Day 14: Integration with YawlA2AServer

**Tasks**:
- [ ] Update `YawlA2AServer` to use A2AProtocolAdapter
  - [ ] In message handler, route to adapter
  - [ ] adapter.toSkillRequest() → skill execution
  - [ ] adapter.toA2AResponse() → client response

- [ ] Register A2AProtocolAdapter in Spring context
  - [ ] @Bean in ConnectorIntegrationConfig
  - [ ] Inject into YawlA2AServer

- [ ] Add correlation ID header support
  - [ ] Extract from incoming message
  - [ ] Propagate to SkillRequest
  - [ ] Include in response

- [ ] Create integration test
  - [ ] Real YawlA2AServer + adapter
  - [ ] Real skill execution
  - [ ] Verify full flow

**Deliverables**:
- [ ] YawlA2AServer updated
- [ ] Integration test passes
- [ ] Correlation ID tracked end-to-end

**Time estimate**: 1 day

---

### Day 15: Documentation + Testing

**Tasks**:
- [ ] Create `.integrations/a2a/example-input-mapping.md`
  - [ ] Document capability mapping format
  - [ ] Examples of JSONPath expressions

- [ ] Update .integrations/README.md
  - [ ] A2A adapter pattern
  - [ ] Correlation ID best practices

- [ ] Run full test suite
  - [ ] mvn clean verify
  - [ ] ≥80% coverage
  - [ ] No H violations

- [ ] Code review
  - [ ] Architecture validation
  - [ ] Pair review of complex adapter logic

**Deliverables**:
- [ ] All tests pass
- [ ] Documentation complete
- [ ] Code review approved

**Time estimate**: 1 day

---

## Week 3 Validation Gate

**Criteria**:
- [ ] A2AProtocolAdapter + capability mapping functional
- [ ] 2 reference skills (PurchaseOrderSkill, InvoiceProcessingSkill)
- [ ] 2 reference YAMLs in .integrations/a2a/
- [ ] Correlation ID preserved end-to-end
- [ ] Input/output mapping via JSONPath
- [ ] YawlA2AServer uses adapter
- [ ] All tests pass, ≥80% coverage

**Demo**:
- [ ] Show A2A message flow
- [ ] Show capability mapping transformation
- [ ] Show correlation ID in request/response
- [ ] Verify case created with mapped parameters

---

## Week 4: MCP Tool Registration + Discovery API

**Goal**: Discovery endpoints, MCP tool integration, end-to-end testing.
**Engineer Lead**: Engineer B
**Days**: 5 days

### Day 16: REST Discovery Endpoints

**Tasks**:
- [ ] Create `IntegrationController.java`
  - [ ] GET /integrations/connectors
  - [ ] GET /integrations/connectors?type=mcp
  - [ ] GET /integrations/connectors/search?skill=approval
  - [ ] GET /integrations/connectors/{id}
  - [ ] GET /integrations/mcp-tools
  - [ ] POST /integrations/reload
  - [ ] GET /integrations/health

- [ ] Implement response DTOs
  - [ ] ConnectorResponse (id, name, type, permissions, metadata)
  - [ ] DiscoveryResponse (List<ConnectorResponse>)
  - [ ] HealthResponse (status, connectors_loaded, last_reload, cache_hit_rate)

- [ ] Add performance tracking
  - [ ] Measure latency per endpoint
  - [ ] Log slow queries (>100ms)

- [ ] Add authentication (JWT)
  - [ ] Verify JWT token on all endpoints
  - [ ] Extract agent_id from token

**Deliverables**:
- [ ] REST controller compiles
- [ ] Unit tests: endpoint logic
- [ ] Integration tests: actual HTTP requests

**Time estimate**: 1.5 days

---

### Day 17: CaseMonitoringSkill + MCP Tool YAML

**Tasks**:
- [ ] Create `CaseMonitoringSkill.java` (new)
  - [ ] Add @MCPTool annotation
  - [ ] Implement execute(SkillRequest)
  - [ ] Read-only: Get case status, running tasks
  - [ ] Use InterfaceB to fetch case info
  - [ ] Return structured response (state, tasks, progress)

- [ ] Create `.integrations/mcp/case-monitor.yaml`
  - [ ] type: mcp
  - [ ] skill: CaseMonitoringSkill
  - [ ] capabilities (Get Case Status)

- [ ] Create `.integrations/mcp/expense-report.yaml`
  - [ ] type: mcp
  - [ ] skill: ExpenseReportSkill
  - [ ] capabilities (Submit Expense Report)

- [ ] Verify MCPToolRegistrar discovers both
  - [ ] Test @PostConstruct runs
  - [ ] Both tools registered with server

**Deliverables**:
- [ ] 1 skill + 2 YAMLs
- [ ] Both discoverable via discovery API
- [ ] MCP tool schemas generated

**Time estimate**: 1 day

---

### Day 18: Full End-to-End Integration Tests

**Tasks**:
- [ ] Create `IntegrationControllerTest.java`
  - [ ] Test GET /integrations/connectors
  - [ ] Test GET /integrations/connectors?type=mcp
  - [ ] Test GET /integrations/connectors/search?skill=approval
  - [ ] Test GET /integrations/health
  - [ ] Verify latency <100ms

- [ ] Create `MCPToolIntegrationTest.java`
  - [ ] Mock Claude client
  - [ ] Test MCP tool discovery
  - [ ] Test tool invocation (case-monitor, expense-report)
  - [ ] Verify results

- [ ] Create full e2e test
  - [ ] Load all 5 connector YAMLs
  - [ ] Verify all in registry
  - [ ] Invoke each via respective protocol (Z.AI, A2A, MCP)
  - [ ] Verify case creation/completion

- [ ] Create `DiscoveryAPITest.java`
  - [ ] JMH benchmark for discovery latency
  - [ ] Target: P50 <100ms, P99 <500ms
  - [ ] Measure on warm cache + cold cache

**Deliverables**:
- [ ] All tests pass
- [ ] Latency benchmark shows <100ms
- [ ] E2E: All 5 connectors work

**Time estimate**: 1.5 days

---

### Day 19: Documentation + Polish

**Tasks**:
- [ ] Create `.integrations/README.md` complete section
  - [ ] Discovery API endpoints
  - [ ] Example curl requests
  - [ ] Response formats

- [ ] Create `.integrations/QUICK-START.md`
  - [ ] 5-minute guide to discovering + invoking connector

- [ ] Update `INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md`
  - [ ] Add actual API response examples
  - [ ] Add performance metrics (latency, cache hit rate)

- [ ] Run full test suite
  - [ ] mvn clean verify (all tests pass)
  - [ ] Jacoco coverage report (≥80%)
  - [ ] No H violations

- [ ] Final code review + sign-off
  - [ ] Lead approves all deliverables
  - [ ] QA checklist completed

**Deliverables**:
- [ ] Complete documentation
- [ ] All tests pass
- [ ] Code review approved
- [ ] Ready for production

**Time estimate**: 1 day

---

### Day 20: Demo + Handoff

**Tasks**:
- [ ] Prepare demo script
  - [ ] Show discovery API in action
  - [ ] Show all 5 connectors working
  - [ ] Show Z.AI → case, A2A → case, MCP → read-only
  - [ ] Show metrics (latency, cache, success rate)

- [ ] Create runbook
  - [ ] How to deploy integrations
  - [ ] How to add new connectors
  - [ ] How to troubleshoot

- [ ] Handoff documentation
  - [ ] Week 1-4 summary
  - [ ] Phase 2 prep (OAuth, webhooks, metrics)

- [ ] Retrospective
  - [ ] What went well
  - [ ] What to improve next phase

**Deliverables**:
- [ ] Live demo (successful)
- [ ] Runbook + handoff docs
- [ ] Retrospective notes

**Time estimate**: 1 day

---

## Week 4 Validation Gate

**Criteria**:
- [ ] Discovery API endpoints operational
- [ ] MCP tool registration + auto-discovery functional
- [ ] 1 reference skill (CaseMonitoringSkill)
- [ ] 2 reference MCP YAMLs (expense-report, case-monitor)
- [ ] All 5 connectors (Z.AI×2, A2A×2, MCP×2) work end-to-end
- [ ] Discovery API latency <100ms (P50)
- [ ] All tests pass, ≥80% coverage

**Demo**:
- [ ] Show GET /integrations/connectors (all 5 listed)
- [ ] Show GET /integrations/mcp-tools (Claude format)
- [ ] Show GET /integrations/health (cache metrics)
- [ ] Measure discovery latency (JMH benchmark)
- [ ] Show 5 complete invocations (each connector type)

---

## Post-MVP Checklist (Phase 2 Preview)

**Not in MVP, but schema prepared for Phase 2**:

- [ ] OAuth flow
  - [ ] Connector credential management
  - [ ] Per-skill API keys

- [ ] Webhooks
  - [ ] Async result callbacks
  - [ ] Connector subscription management

- [ ] Data Transformers
  - [ ] Skill output → marketplace format
  - [ ] Real-time metrics aggregation

- [ ] Advanced Auth
  - [ ] SPIFFE integration
  - [ ] Fine-grained permission checks

- [ ] Real-time Reputation
  - [ ] Kafka event stream
  - [ ] Success rate aggregation
  - [ ] SLA tracking

- [ ] Connector Marketplace UI
  - [ ] Search + filter
  - [ ] Rating + reviews
  - [ ] Installation wizard

---

## Success Metrics (End of Week 4)

| Metric | Target | Actual |
|--------|--------|--------|
| **Connectors implemented** | 5 | __ |
| **Discovery API latency (P50)** | <100ms | __ ms |
| **Skill invocation success rate** | 100% (happy path) | __% |
| **Test coverage** | ≥80% | __% |
| **Code review sign-offs** | 100% | __% |
| **Zero TODO/mock code** | 100% | __% |

---

## Daily Standup Template

**Date**: [Date]
**Engineer**: [Name]
**Week**: [Week #]

**Yesterday**: [Completed tasks]
**Today**: [Planned tasks]
**Blockers**: [Any issues?]
**Notes**: [Anything else?]

---

## Code Quality Standards (GODSPEED)

**H Gate** (No H violations):
- No TODO, FIXME, mock, stub, fake
- No empty returns, silent fallbacks, lies

**Q Gate** (Real impl or throw):
- All code executes real logic OR throws UnsupportedOperationException
- No silent failures

**Λ Gate** (Build discipline):
- `mvn clean verify` passes before commit
- ≥80% test coverage (Jacoco)
- No compilation warnings

**Ω Gate** (Git discipline):
- Specific files in commits (never `git add .`)
- One logical change per commit
- Session URL in commit message

---

## Deployment Checklist

**Before deploying to prod**:

- [ ] All tests pass locally (mvn clean verify)
- [ ] Code reviewed and approved
- [ ] No H violations detected
- [ ] Coverage ≥80%
- [ ] Documentation complete
- [ ] Runbook written
- [ ] Schema version frozen (INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md v1.0)

**Deployment steps**:

```bash
# 1. Merge to main
git checkout main
git pull origin main
git merge --no-ff feature/integrations-marketplace

# 2. Tag release
git tag -a v0.1.0-connectors -m "MVP: Connector registry, Z.AI, A2A, MCP"

# 3. Push
git push origin main
git push origin v0.1.0-connectors

# 4. Build artifact
mvn clean package -DskipTests=false

# 5. Deploy
# (Kubernetes, Docker, etc.)
```

---

**Status**: Ready for implementation
**Last Updated**: 2026-02-21
**Approved By**: [Lead engineer]

