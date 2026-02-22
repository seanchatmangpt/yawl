# Integration Marketplace MVP - Project Summary

**Project Name**: YAWL Integrations Marketplace
**Phase**: MVP (Phase 1 of 2)
**Duration**: 4 weeks
**Team**: 2 engineers (Engineer A, Engineer B)
**Budget**: $50K
**Start Date**: 2026-02-24 (Week 1)

---

## Executive Summary

The Integration Marketplace MVP enables YAWL skills (workflow capabilities) to be **automatically discovered and invoked** by three classes of external systems:

1. **Z.AI Agents** — Autonomous agents that discover skills as MCP tools and invoke them
2. **Peer A2A Agents** — Other workflow systems that send agent-to-agent handoff messages
3. **LLMs** (Claude, Gemini, etc.) — Large language models that call skills as native tools

**Key Innovation**: Skills are published once (as Java classes), then automatically exposed through Z.AI, A2A, and MCP protocols via **connector manifests** (YAML files).

**Scope (MVP - 80/20 focus)**:
- Connector registry (Git-backed)
- Z.AI connector adapter + 2 reference skills
- A2A protocol adapter + 2 reference skills
- MCP tool registration + 1 reference skill
- Discovery API (<100ms latency)
- Basic testing framework

**Deferred to Phase 2**:
- OAuth credential management
- Webhook callbacks for async results
- Real-time reputation scoring (Kafka stream)
- Connector marketplace UI

---

## Deliverables (18 Java Classes + 7 Config Files + 12 Tests)

### Core Java Classes (Week 1-4)

**Connector Core** (6 classes):
1. `ConnectorMetadata.java` — Record defining connector structure
2. `ConnectorRegistry.java` — Interface for connector lookup
3. `GitBackedConnectorRegistry.java` — Git-based implementation
4. `ConnectorYamlValidator.java` — YAML schema validation
5. `ConnectorType.java` — Enum (Z_AI, A2A, MCP)
6. `ConnectorException.java` — Exception hierarchy

**Z.AI Adapter** (3 classes):
7. `Z2AIConnectorAdapter.java` — MCP tool mapping
8. `ToolSchemaGenerator.java` — JSON schema auto-generation
9. `MCPToolRegistrar.java` — Auto-discovery of @MCPTool

**A2A Adapter** (4 classes):
10. `A2AProtocolAdapter.java` — Interface
11. `DefaultA2AProtocolAdapter.java` — Implementation
12. `CapabilityMapper.java` — JSONPath-based mapping
13. `JsonPathMapper.java` — JSONPath utility

**REST API** (2 classes):
14. `IntegrationController.java` — Discovery endpoints
15. `IntegrationEventPublisher.java` — Metrics publishing

**Skills** (5 new/modified classes):
16. `ApprovalSkill.java` — New, Z.AI example
17. `PurchaseOrderSkill.java` — New, A2A example
18. `InvoiceProcessingSkill.java` — New, A2A example
19. `ExpenseReportSkill.java` — Existing, add @MCPTool
20. `CaseMonitoringSkill.java` — New, MCP example

**Spring Configuration** (2 classes):
21. `ConnectorIntegrationConfig.java` — Spring beans
22. `ConnectorProperties.java` — Spring properties

### Configuration Files (Week 1)

**Schema**:
1. `.integrations/connector-schema.json` — JSON Schema for YAML validation

**Documentation**:
2. `.integrations/README.md` — Registry usage guide
3. `.integrations/QUICK-START.md` — 5-minute startup

### Connector YAML Files (Week 2-4)

**Z.AI connectors** (2 files, Week 2):
4. `.integrations/z-ai/approval-skill.yaml`
5. `.integrations/z-ai/expense-report-skill.yaml`

**A2A adapters** (2 files, Week 3):
6. `.integrations/a2a/purchase-order-skill.yaml`
7. `.integrations/a2a/invoice-skill.yaml`

**MCP tools** (2 files, Week 4):
8. `.integrations/mcp/case-monitor.yaml`
9. `.integrations/mcp/expense-report.yaml`

### Test Classes (12 test files)

**Unit tests**:
1. `ConnectorRegistryTest.java` — Registry CRUD
2. `ConnectorYamlValidatorTest.java` — YAML validation
3. `Z2AIConnectorTest.java` — Schema generation
4. `MCPToolRegistrarTest.java` — Tool registration
5. `A2AProtocolAdapterTest.java` — Message parsing
6. `CapabilityMapperTest.java` — JSONPath mapping
7. `IntegrationControllerTest.java` — REST endpoints

**Integration tests**:
8. `Z2AIConnectorIntegrationTest.java` — Full Z.AI flow
9. `A2AAdapterIntegrationTest.java` — Full A2A flow
10. `MCPToolIntegrationTest.java` — Full MCP flow
11. `DiscoveryAPITest.java` — Latency benchmark
12. `E2EIntegrationTest.java` — All 5 connectors end-to-end

---

## 4-Week Roadmap

### Week 1: Connector Schema + Registry (Engineer A, 5 days)

| Day | Task | Deliverable |
|-----|------|-------------|
| 1 | ConnectorMetadata, ConnectorType, exceptions | 3 classes |
| 2 | Registry interface + GitBackedConnectorRegistry | 2 classes, unit tests |
| 3 | YAML parsing + schema validation | 3 classes, dependencies |
| 4 | Spring integration + error handling | 3 classes, REST endpoint |
| 5 | Integration tests + documentation | Tests pass, ≥80% coverage |

**Output**: Git-backed registry loads connector YAML files, schema validates them.

**Validation**: Demonstrate registry loading 3 test connectors + health endpoint.

---

### Week 2: Z.AI Connector + 2 Examples (Engineer B, 5 days)

| Day | Task | Deliverable |
|-----|------|-------------|
| 6 | Z.AIConnectorAdapter + ToolSchemaGenerator | 2 classes |
| 7 | @MCPTool annotation + MCPToolRegistrar | 3 classes |
| 8 | ApprovalSkill + ExpenseReportSkill + 2 YAMLs | 2 skills, 2 YAMLs |
| 9 | Integration tests (mock Z.AI agent) | Tests pass, e2e works |
| 10 | Documentation + code review | Documentation complete |

**Output**: Z.AI agents can discover + invoke 2 reference skills via MCP.

**Validation**: Mock Z.AI agent invokes ApprovalSkill with idempotency key, no duplicate on retry.

---

### Week 3: A2A Adapter + 2 Examples (Engineer A, 5 days)

| Day | Task | Deliverable |
|-----|------|-------------|
| 11 | A2AProtocolAdapter + CapabilityMapper | 4 classes |
| 12 | PurchaseOrderSkill + InvoiceProcessingSkill + 2 YAMLs | 2 skills, 2 YAMLs |
| 13 | Integration tests (mock A2A server) | Tests pass, e2e works |
| 14 | YawlA2AServer integration + correlation ID | Integration complete |
| 15 | Documentation + code review | Documentation complete |

**Output**: A2A agents can send handoff messages, YAWL routes to skills via adapter, correlation ID tracked.

**Validation**: A2A agent sends PO message, case created, correlation ID chained request→case→response.

---

### Week 4: MCP Tools + Discovery API (Engineer B, 5 days)

| Day | Task | Deliverable |
|-----|------|-------------|
| 16 | REST discovery endpoints (GET /integrations/connectors) | 2 classes, 7 endpoints |
| 17 | CaseMonitoringSkill + 2 MCP YAMLs | 1 skill, 2 YAMLs |
| 18 | Full e2e tests + latency benchmark | Tests pass, <100ms P50 |
| 19 | Documentation + final testing | All docs complete |
| 20 | Demo + handoff | Live demo, runbook |

**Output**: Discovery API lists all 5 connectors, Claude can invoke MCP tools, latency <100ms.

**Validation**: Live demo: GET /integrations/connectors returns all 5, invoke each connector type.

---

## Integration Points (How Connectors Fit)

### 1. How Connectors Use InterfaceB

```
Skill (e.g., ApprovalSkill)
  ↓ .execute(SkillRequest)
YawlEngineAdapter (InterfaceB)
  ↓
YStatelessEngine.launchCase(specId, caseData)
  ↓
YEngine (stateful execution)
```

**No new InterfaceB methods needed** — MVP reuses existing API.

### 2. How Connectors Leverage Existing Auth (JWT)

```
A2A Agent
  ↓ Bearer token
YawlA2AServer
  ↓ JwtAuthenticationProvider.validateToken()
AuthenticatedPrincipal (agent_id, permissions)
  ↓ passed to skill
Skill.execute() checks permissions
```

**No auth layer changes** — MVP reuses existing JWT validation.

### 3. How Connectors Feed Metrics

```
Skill invocation
  ↓ success/failure + duration
IntegrationEventPublisher
  ↓
Prometheus counter (connector_invocation)
  ↓ (Phase 2: Kafka → DataMarketplaceService)
```

**MVP scope**: Local Prometheus metrics. Phase 2 adds Kafka + data marketplace.

### 4. How Connectors Participate in Reputation

```
ConnectorMetadata (from YAML)
  ├─ owner
  ├─ sla_ms
  ├─ success_rate
  └─ last_updated
  ↓ (Phase 2: Real-time aggregation from metrics)
```

**MVP scope**: Schema prepared, display in API. Phase 2 adds real-time updates.

---

## Tech Stack Highlights

### Languages & Frameworks
- **Java 25**: Records, sealed classes, pattern matching, virtual threads, scoped values
- **Spring Boot 3**: Auto-configuration, context management
- **Spring Data**: Optional JPA (deferred to Phase 2)

### Libraries
- **SnakeYAML 2.0**: YAML parsing
- **Jackson**: JSON serialization
- **JSON Schema Validator**: YAML schema validation
- **JSONPath**: JPath for input/output mapping

### Data Storage
- **Git**: Connector YAML version control (source of truth)
- **In-memory cache**: For fast discovery (<100ms)
- **File hash**: Staleness detection (SHA256)

### Protocols
- **MCP (Model Context Protocol)**: Z.AI tool invocation
- **A2A (Agent-to-Agent)**: Peer workflow handoff
- **REST API**: Discovery endpoints
- **JWT**: Authentication (existing)

---

## Success Metrics (End of Week 4)

| Metric | Target | Method |
|--------|--------|--------|
| **5 working connectors** | All 5 deploy, no errors | Integration tests (e2e) |
| **Discovery API latency** | P50 <100ms, P99 <500ms | JMH benchmark |
| **Skill success rate** | 100% (happy path) | Unit + integration tests |
| **Idempotency** | Same key → same result | Retry test (Z.AI) |
| **Correlation chaining** | Tracked end-to-end | Message trace test (A2A) |
| **MCP invocation** | Claude can call tools | MCP client test |
| **Test coverage** | ≥80% | Jacoco report |
| **Zero TODOs/mocks** | 100% real code | Grep + automated checks |
| **Code review sign-off** | 100% | Lead approval |
| **Documentation** | Complete | README + guides done |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ External Systems                                                 │
├─────────────────────────────────────────────────────────────────┤
│ Z.AI Agents (MCP)  │  A2A Agents (REST)  │  LLMs (Claude, etc.) │
└──────┬──────────────┬────────────────────┬───────────────────────┘
       │              │                    │
       ↓              ↓                    ↓
┌──────────────────────────────────────────────────────────────────┐
│ YAWL Integration Layer                                           │
├──────────────────────────────────────────────────────────────────┤
│  YawlMcpServer     │  YawlA2AServer     │  IntegrationController │
│  (MCP stdio)       │  (REST on 8081)    │  (Discovery REST)      │
└──────┬──────────────┬────────────────────┬───────────────────────┘
       │              │                    │
       ├──────────────┼────────────────────┘
       │              │
       ↓              ↓
┌──────────────────────────────────────────┐
│ Connector Adapters                       │
├──────────────────────────────────────────┤
│ Z.AIConnectorAdapter                    │
│ DefaultA2AProtocolAdapter               │
│ MCPToolRegistrar                        │
└──────┬──────────────────────────────────┬┘
       │                                  │
       └──────────────┬───────────────────┘
                      ↓
            ┌─────────────────────┐
            │ ConnectorRegistry   │
            │ (Git-backed cache)  │
            └──────────┬──────────┘
                       │
       ┌───────────────┼───────────────┐
       ↓               ↓               ↓
 A2ASkill           A2ASkill       A2ASkill
 (Approval)       (Purchase       (Invoice
                   Order)        Processing)
       │               │               │
       └───────────────┼───────────────┘
                       ↓
            ┌─────────────────────┐
            │ InterfaceB          │
            │ (YawlEngineAdapter) │
            └──────────┬──────────┘
                       ↓
       ┌───────────────────────────────┐
       │ YEngine / YStatelessEngine     │
       │ (YAWL Workflow Execution)     │
       └───────────────────────────────┘
```

---

## File Structure (Post-MVP)

```
/home/user/yawl/
├─ src/org/yawlfoundation/yawl/integration/connector/     [NEW]
│  ├─ ConnectorMetadata.java
│  ├─ ConnectorRegistry.java
│  ├─ GitBackedConnectorRegistry.java
│  ├─ ConnectorYamlValidator.java
│  ├─ ConnectorYamlParser.java
│  ├─ ConnectorType.java
│  ├─ ConnectorException.java
│  ├─ Z2AIConnectorAdapter.java
│  ├─ ToolSchemaGenerator.java
│  ├─ MCPToolRegistrar.java
│  ├─ A2AProtocolAdapter.java
│  ├─ DefaultA2AProtocolAdapter.java
│  ├─ CapabilityMapper.java
│  ├─ JsonPathMapper.java
│  └─ IntegrationEventPublisher.java
├─ src/org/yawlfoundation/yawl/integration/
│  ├─ IntegrationController.java              [NEW]
│  └─ config/
│     ├─ ConnectorIntegrationConfig.java       [NEW]
│     └─ ConnectorProperties.java              [NEW]
├─ src/org/yawlfoundation/yawl/integration/a2a/skills/
│  ├─ ApprovalSkill.java                       [NEW]
│  ├─ PurchaseOrderSkill.java                  [NEW]
│  ├─ InvoiceProcessingSkill.java              [NEW]
│  ├─ CaseMonitoringSkill.java                 [NEW]
│  └─ ExpenseReportSkill.java                  [MODIFIED: add @MCPTool]
├─ .integrations/                              [NEW directory]
│  ├─ README.md
│  ├─ QUICK-START.md
│  ├─ connector-schema.json
│  ├─ z-ai/
│  │  ├─ approval-skill.yaml
│  │  └─ expense-report-skill.yaml
│  ├─ a2a/
│  │  ├─ purchase-order-skill.yaml
│  │  └─ invoice-skill.yaml
│  └─ mcp/
│     ├─ case-monitor.yaml
│     └─ expense-report.yaml
├─ src/test/java/org/yawlfoundation/yawl/integration/connector/
│  ├─ ConnectorRegistryTest.java
│  ├─ ConnectorYamlValidatorTest.java
│  ├─ Z2AIConnectorTest.java
│  ├─ MCPToolRegistrarTest.java
│  ├─ A2AProtocolAdapterTest.java
│  ├─ CapabilityMapperTest.java
│  ├─ IntegrationControllerTest.java
│  ├─ e2e/
│  │  ├─ Z2AIConnectorIntegrationTest.java
│  │  ├─ A2AAdapterIntegrationTest.java
│  │  ├─ MCPToolIntegrationTest.java
│  │  ├─ DiscoveryAPITest.java
│  │  └─ E2EIntegrationTest.java
│  └─ resources/
│     └─ integration/
│        ├─ test-connector.yaml
│        ├─ mock-z-ai-approval-message.json
│        └─ mock-a2a-po-message.json
│
├─ INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md      [NEW]
├─ INTEGRATIONS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md [NEW]
└─ INTEGRATIONS-MARKETPLACE-MVP-SUMMARY.md     [NEW - this file]
```

---

## Phase 2 Preview (Deferred Features)

**Not in MVP, but architecture designed to support**:

1. **OAuth Credential Management**
   - Connector registration form
   - Secure credential storage (Vault)
   - Per-skill API keys

2. **Webhook Callbacks**
   - Async result notifications
   - Subscription management
   - Retry logic for failed deliveries

3. **Real-time Reputation Scoring**
   - Kafka stream: integration.events
   - Real-time success rate aggregation
   - SLA tracking + alerts

4. **Data Transformers**
   - Skill output → marketplace format
   - Schema mapping framework
   - Validation + error handling

5. **Advanced Authentication**
   - SPIFFE identity integration
   - Fine-grained permission checks (per connector)
   - Audit logging

6. **Connector Marketplace UI**
   - Search + filter connectors
   - 5-star ratings + reviews
   - Installation wizard
   - Connector versioning

---

## Reference Documentation

**Key Design Documents**:
- `INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md` — Full architectural design
- `INTEGRATIONS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md` — Day-by-day tasks
- `.integrations/README.md` — Registry usage guide
- `.integrations/QUICK-START.md` — 5-minute startup

**Related Docs**:
- `Z.AI-YAWL-INTEGRATION-DESIGN.md` — Z.AI agent lifecycle
- `A2A_PROTOCOL_RESEARCH.md` — Agent-to-agent protocol spec
- `MCP_LLM_TOOL_DESIGN.md` — MCP tool integration

**YAWL Architecture**:
- `src/org/yawlfoundation/yawl/integration/a2a/skills/A2ASkill.java` — Skill interface
- `src/org/yawlfoundation/yawl/engine/interfaceB/` — InterfaceB API
- `src/org/yawlfoundation/yawl/integration/a2a/auth/` — Auth stack

---

## Quality Standards (GODSPEED)

**All deliverables must meet**:

- **H (Guards)**: Zero TODO, mock, stub, fake, empty_return, lie
- **Q (Invariants)**: Real impl OR throw UnsupportedOperationException (no silent fallback)
- **Λ (Build)**: `mvn clean verify` passes, ≥80% test coverage
- **Ω (Git)**: Specific files in commits, one logical change per commit, session URL in message

---

## Deployment Instructions

**Prerequisites**:
- Java 25+
- Maven 3.8+
- Git (with post-commit hook support)
- YawlA2AServer + YawlMcpServer running

**Build**:
```bash
mvn clean verify
```

**Deploy**:
```bash
# Push to main
git push origin feature/integrations-marketplace:main

# Tag release
git tag -a v0.1.0-connectors -m "MVP: Connector registry"
git push origin v0.1.0-connectors

# Package
mvn package -DskipTests=false
```

**Verify**:
```bash
curl http://localhost:8080/integrations/health
# Should return: { "status": "UP", "connectors_loaded": 5, ... }
```

---

## Key Contacts

- **Lead Engineer**: [Name] (overall architecture, code review)
- **Engineer A**: [Name] (Weeks 1, 3 — Registry, A2A)
- **Engineer B**: [Name] (Weeks 2, 4 — Z.AI, MCP, Discovery)
- **Product Owner**: [Name] (requirements, demo feedback)
- **DevOps**: [Name] (deployment, monitoring)

---

## Budget Breakdown

| Category | Hours | Cost |
|----------|-------|------|
| Design + Architecture | 40 | $4K |
| Implementation (160 hours) | 160 | $32K |
| Testing + QA | 40 | $4K |
| Documentation | 20 | $2K |
| Code Review + Sign-off | 20 | $4K |
| **Total** | **280** | **$50K** |

---

## Timeline

```
Week 1 (Feb 24-28): Connector Schema + Registry
  └─ Mon: Data models
  └─ Tue: Git implementation
  └─ Wed: YAML + validation
  └─ Thu: Spring config
  └─ Fri: Tests + review
  └─ Validation gate: Registry loads 3 connectors ✓

Week 2 (Mar 3-7): Z.AI Connectors
  └─ Tue: Z.AI adapter + schema generator
  └─ Wed: @MCPTool + registrar
  └─ Thu: 2 reference skills
  └─ Fri: Integration tests
  └─ Validation gate: Mock Z.AI invokes skill ✓

Week 3 (Mar 10-14): A2A Adapters
  └─ Tue: A2A protocol adapter
  └─ Wed: 2 reference skills
  └─ Thu: Integration tests
  └─ Fri: YawlA2AServer integration
  └─ Validation gate: A2A agent sends message → case created ✓

Week 4 (Mar 17-21): MCP Tools + Discovery
  └─ Tue: Discovery REST API
  └─ Wed: CaseMonitoringSkill + MCP YAMLs
  └─ Thu: Full e2e tests
  └─ Fri: Demo + handoff
  └─ Validation gate: All 5 connectors work end-to-end ✓
```

---

## Sign-Off Checklist

**Lead Engineer**:
- [ ] Architecture reviewed and approved
- [ ] Code quality meets GODSPEED standards
- [ ] All tests pass (≥80% coverage)
- [ ] No H/Q violations

**Product Owner**:
- [ ] All requirements met
- [ ] Demo successful
- [ ] Documentation complete

**DevOps**:
- [ ] Deployment process validated
- [ ] Monitoring configured
- [ ] Runbook reviewed

**QA**:
- [ ] Test coverage confirmed
- [ ] Integration tests pass
- [ ] Performance benchmarks met

---

**Status**: Ready for implementation
**Last Updated**: 2026-02-21
**Next Milestone**: Week 1 validation gate (Feb 28)

