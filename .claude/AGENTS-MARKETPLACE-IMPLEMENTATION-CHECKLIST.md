# Agents Marketplace MVP — Implementation Checklist

**Status**: Ready to start development
**Duration**: 4 weeks
**Team**: 2 engineers (1 backend, 1 QA/infrastructure)
**Budget**: $20,000 (labor + contingency)

---

## Week 1: Agent Profile Schema + Metadata Model

**Target**: Define agent format, create 2 reference agents, YAML serialization.

### Deliverables

- [ ] **AgentProfile.java** (record, YAML-serializable)
  - [ ] Record fields: id, name, version, author, description
  - [ ] Capabilities list (AgentCapability)
  - [ ] DeploymentConfig (docker, port, env, resources)
  - [ ] HealthCheckConfig (path, interval, timeout)
  - [ ] AgentMetrics (success rate, latency, uptime, cost)
  - [ ] YAML serialization: `fromYaml()`, `toYaml()`
  - [ ] Validation: `validate()` throws ValidationException

- [ ] **AgentCapability.java** (record)
  - [ ] Record fields: id, skillId, description, params, returns
  - [ ] ParamSpec (type, description, constraints)
  - [ ] Timeout + maxRetries settings
  - [ ] Serializable to/from YAML

- [ ] **ParamSpec.java** (record)
  - [ ] type (number, string, boolean, array, object)
  - [ ] description
  - [ ] defaultValue
  - [ ] required flag
  - [ ] constraints (min, max, pattern, enum)

- [ ] **DeploymentConfig.java** (record)
  - [ ] type (docker, process, remote)
  - [ ] image URL
  - [ ] port number
  - [ ] environment variables
  - [ ] resource requirements (memory, cpu, disk)

- [ ] **AgentProfileRepository.java** (interface)
  - [ ] `loadProfile(agentId): AgentProfile`
  - [ ] `listProfiles(): List<AgentProfile>`
  - [ ] `saveProfile(profile): void`
  - [ ] `deleteProfile(agentId): void`

- [ ] **InMemoryProfileRepository.java** (implementation)
  - [ ] Load profiles from `.agents/agent-profiles/`
  - [ ] Cache in memory
  - [ ] Thread-safe (concurrent reads)

- [ ] **YAML Serialization**
  - [ ] Jackson ObjectMapper with YAML factory
  - [ ] Custom deserializers for constraints
  - [ ] Round-trip fidelity tests

- [ ] **Reference Agents** (2 created)
  - [ ] `.agents/agent-profiles/approval-agent.yaml`
  - [ ] `.agents/agent-profiles/validation-agent.yaml`
  - [ ] Both profiles valid + deserializable

- [ ] **Tests** (>80% coverage)
  - [ ] AgentProfile YAML deserialization (10 tests)
  - [ ] Profile validation (required fields, types) (5 tests)
  - [ ] ParamSpec constraints validation (5 tests)
  - [ ] DeploymentConfig resource parsing (3 tests)
  - [ ] Round-trip serialization (2 tests)

- [ ] **Documentation**
  - [ ] `package-info.java` for `org.yawlfoundation.yawl.integration.marketplace.agent`
  - [ ] Javadoc comments on all public methods
  - [ ] Example YAML in comments

### Acceptance Criteria

- [x] 2 agents registered + profiles valid
- [x] 100% YAML round-trip fidelity (profile deserialize → serialize → deserialize matches)
- [x] Profile loads in <50ms
- [x] All tests green
- [x] No TODO/FIXME in code (H gate)

**Effort**: 1 engineer, 40 hours
**Cost**: $2,500

---

## Week 2: Agent Registry + Discovery API

**Target**: Git-backed registry, SPARQL capability index, REST discovery API.

### Deliverables

- [ ] **AgentRegistry.java** (interface)
  - [ ] `getAgent(id): AgentProfile`
  - [ ] `findAgentsByCapability(skill): List<AgentProfile>`
  - [ ] `findAgentsByTag(tag): List<AgentProfile>`
  - [ ] `getAllAgents(): List<AgentProfile>`
  - [ ] `isHealthy(): boolean`

- [ ] **GitAgentRegistry.java** (implementation)
  - [ ] Clone `.agents/` from Git repo at startup
  - [ ] Watch for changes (git pull on interval, 30s default)
  - [ ] Load all profiles from `.agents/agent-profiles/`
  - [ ] Memory-safe (immutable AgentProfile records)
  - [ ] Thread-safe (ConcurrentHashMap for cache)
  - [ ] Error handling (retry on git failure, fallback to cache)

- [ ] **CapabilityIndex.java** (SPARQL-based)
  - [ ] In-memory RDF graph (Apache Jena Model)
  - [ ] Inverted index: skill → [agents]
  - [ ] Add profiles to index on registry refresh
  - [ ] Query pattern: SPARQL SELECT for agents by skill
  - [ ] Auto-rebuild on registry update

- [ ] **CapabilityMatcher.java** (query engine)
  - [ ] `findAgents(skillId): List<AgentProfile>`
  - [ ] Rank by success rate + latency
  - [ ] Cache query results (5-min TTL)
  - [ ] Handle skill hierarchy (narrower/broader)

- [ ] **Discovery API (REST)**
  - [ ] POST `/agents/discover?capability=approve` → agents by skill
  - [ ] POST `/agents/discover?tag=approval` → agents by tag (optional Week 2)
  - [ ] GET `/agents/{id}/profile` → full profile
  - [ ] GET `/agents/{id}/health` → health status
  - [ ] GET `/agents` → list all agents

- [ ] **DiscoveryService.java** (REST controller)
  - [ ] Discover endpoint (< 100ms latency target)
  - [ ] Profile endpoint
  - [ ] Health endpoint
  - [ ] Error handling (AgentNotFoundException, TimeoutException)

- [ ] **DiscoveryRequest.java** (DTO)
  - [ ] capability (required)
  - [ ] tag (optional)
  - [ ] limit (default 10)

- [ ] **DiscoveryResponse.java** (DTO)
  - [ ] id, name, successRate, avgLatency, costPerInvocation

- [ ] **HealthChecker.java** (agent health)
  - [ ] HTTP GET to agent health endpoint
  - [ ] Timeout (5s)
  - [ ] Cache result (30s)

- [ ] **AgentHealthResponse.java** (DTO)
  - [ ] status (healthy, degraded, offline)
  - [ ] latency (ms)
  - [ ] checkedAt (timestamp)

- [ ] **Reference Agents** (3 more created)
  - [ ] `.agents/agent-profiles/po-agent.yaml`
  - [ ] `.agents/agent-profiles/expense-agent.yaml`
  - [ ] `.agents/agent-profiles/scheduler-agent.yaml`

- [ ] **Registry Manifest**
  - [ ] `.agents/agents.yaml` (index of all agents + versions)

- [ ] **Tests** (>80% coverage)
  - [ ] GitAgentRegistry git clone + pull (5 tests)
  - [ ] CapabilityIndex RDF add + query (10 tests)
  - [ ] CapabilityMatcher ranking (5 tests)
  - [ ] Discovery API latency (100 concurrent requests, verify <100ms p99)
  - [ ] Health check timeout handling (3 tests)
  - [ ] Git sync + agent availability after update (2 tests)

- [ ] **Documentation**
  - [ ] `package-info.java` for registry + discovery packages
  - [ ] Architecture diagram (registry → index → discovery flow)
  - [ ] SPARQL query examples in comments

### Acceptance Criteria

- [x] 5 agents registered + discoverable
- [x] Discovery <100ms p99 latency (measured across 100 queries)
- [x] Registry auto-updates on git pull
- [x] 98%+ SPARQL query correctness
- [x] All tests green
- [x] Health check responds within 5s

**Effort**: 1 engineer + 0.5 integrator, 40 hours + 5 hours
**Cost**: $2,500 + $1,250 = $3,750

---

## Week 3: Orchestration Template Builder

**Target**: Template model, DAG compiler, 3 reference orchestrations.

### Deliverables

- [ ] **OrchestrationTemplate.java** (record, JSON-serializable)
  - [ ] metadata (id, name, version, description)
  - [ ] spec (pattern, agents, errorHandling)
  - [ ] Pattern enum: SEQUENTIAL, PARALLEL, CONDITIONAL
  - [ ] JSON serialization: `fromJson()`, `toJson()`
  - [ ] Validate: no null fields, valid pattern

- [ ] **AgentRef.java** (record, agent reference in template)
  - [ ] id (step identifier)
  - [ ] agentId (reference to agent in registry)
  - [ ] capability (reference to capability within agent)
  - [ ] timeout, retries, dependsOn
  - [ ] input (JSONPath expressions), output

- [ ] **TemplateCompiler.java**
  - [ ] `compile(template): YSpecification`
  - [ ] Validates DAG (no cycles)
  - [ ] Converts agents → YAWL tasks
  - [ ] Creates input/output conditions
  - [ ] Generates task data bindings (JSONPath)
  - [ ] Creates flows between tasks (from dependencies)
  - [ ] Wraps agent invocations in error handlers

- [ ] **DAGValidator.java**
  - [ ] `validateDAG(template): void` (throws exception if invalid)
  - [ ] Detect cycles (DFS)
  - [ ] Validate agent references exist in registry
  - [ ] Type-check parameter bindings (JSONPath)
  - [ ] Detect missing dependencies
  - [ ] Ensure agents are leaf nodes if no dependents

- [ ] **AgentTask.java** (custom YAWL task for agents)
  - [ ] Wraps agent invocation
  - [ ] Timeout + retry logic
  - [ ] Error escalation
  - [ ] Metrics collection

- [ ] **AgentOrchestratorService.java**
  - [ ] `deployTemplate(template): YSpecification`
  - [ ] Calls TemplateCompiler
  - [ ] Validates result
  - [ ] Returns deployable YAWL spec

- [ ] **TemplateRepository.java** (interface)
  - [ ] `load(templateId): OrchestrationTemplate`
  - [ ] `listTemplates(): List<OrchestrationTemplate>`
  - [ ] `save(template): void`

- [ ] **FileTemplateRepository.java** (implementation)
  - [ ] Load templates from `.agents/orchestration/*.json`
  - [ ] Watch for changes
  - [ ] Cache in memory

- [ ] **Three Orchestration Templates** (reference)
  - [ ] `.agents/orchestration/sequential.json` (validate → approve → notify)
  - [ ] `.agents/orchestration/parallel.json` (validate + compliance in parallel)
  - [ ] `.agents/orchestration/conditional.json` (if amount > limit → escalate)

- [ ] **Tests** (>80% coverage)
  - [ ] Template JSON deserialization (5 tests)
  - [ ] DAG validation: cycles, missing refs, type mismatches (10 tests)
  - [ ] Compilation: agents → YAWL tasks (5 tests)
  - [ ] Parameter binding: JSONPath expressions (5 tests)
  - [ ] E2E: template deployment + mock agent invocation (3 tests)
  - [ ] Latency: <200ms per template (benchmark test)

- [ ] **Documentation**
  - [ ] `package-info.java` for orchestration package
  - [ ] Template schema documentation (in code)
  - [ ] Example templates with comments

### Acceptance Criteria

- [x] 3 orchestration templates compile + deploy
- [x] Template → YAWL spec compilation verified correct
- [x] All DAG validations passing
- [x] <200ms compile time per template
- [x] E2E mock execution succeeds
- [x] All tests green
- [x] No TODO/FIXME in code

**Effort**: 1 engineer + 0.5 architect, 40 hours + 5 hours
**Cost**: $2,500 + $1,250 = $3,750

---

## Week 4: Integration Tests + Lifecycle Management

**Target**: Full integration testing, agent deployment, lifecycle hooks.

### Deliverables

- [ ] **AgentDeployer.java** (Docker wrapper)
  - [ ] `deploy(profile): AgentDeployment`
  - [ ] Pull image, create container, start
  - [ ] Wait for health check (30s timeout)
  - [ ] Verify port accessible
  - [ ] Auto-rollback on failure
  - [ ] Return AgentDeployment record

- [ ] **AgentDeployment.java** (record)
  - [ ] agentId, containerId, host, port
  - [ ] status (RUNNING, STOPPED, FAILED)
  - [ ] deployedAt (timestamp)
  - [ ] healthStatus

- [ ] **DeploymentStatus.java** (enum)
  - [ ] RUNNING, STOPPED, FAILED, DEGRADED

- [ ] **AgentLifecycleListener.java** (interface)
  - [ ] `onAgentRegistered(profile): void`
  - [ ] `onAgentDeployed(deployment): void`
  - [ ] `onAgentHealthDegraded(agent): void`
  - [ ] `onAgentUnregistered(agent): void`

- [ ] **AgentLifecycleHooks.java** (default impl)
  - [ ] Implement listener interface
  - [ ] Emit events to logging
  - [ ] Emit metrics
  - [ ] Call external hooks (webhooks, optional)

- [ ] **Integration Test Suite** (comprehensive)

  - [ ] **Test 1: End-to-end workflow with agent**
    - [ ] Create approval workflow spec
    - [ ] Deploy approval-agent
    - [ ] Execute workflow with agent invocation
    - [ ] Verify case completion + output
    - [ ] Assert agent was invoked

  - [ ] **Test 2: Agent discovery + invocation**
    - [ ] Register 5 agents in registry
    - [ ] Discover agents by capability
    - [ ] Invoke agent via A2AClient
    - [ ] Verify response correctness
    - [ ] Check latency <100ms

  - [ ] **Test 3: Orchestration template deployment**
    - [ ] Load template from YAML
    - [ ] Compile to YAWL workflow spec
    - [ ] Deploy agents via docker-compose
    - [ ] Execute workflow
    - [ ] Verify all agents completed tasks

  - [ ] **Test 4: Error handling**
    - [ ] Agent timeout → retry → escalate
    - [ ] Invalid input → validation error + escalate
    - [ ] Agent crash → circuit breaker opens
    - [ ] Verify fallback behavior

  - [ ] **Test 5: Metrics + monitoring**
    - [ ] Track success rate per agent
    - [ ] Monitor latency distribution
    - [ ] Alert on degradation
    - [ ] Verify metrics published

  - [ ] **Test 6: Stress test (100 concurrent requests)**
    - [ ] Discovery latency under load
    - [ ] Verify <100ms p99 maintained
    - [ ] Check circuit breaker behavior

- [ ] **Docker Images** (reference agents)
  - [ ] Dockerfile for approval-agent
    - [ ] Base image: openjdk:21
    - [ ] COPY agent JAR
    - [ ] EXPOSE 9001
    - [ ] HEALTHCHECK /health
  - [ ] Docker images for remaining 4 agents (stubs for MVP)
  - [ ] docker-compose.yml for local 5-agent setup

- [ ] **Mock Agents** (for testing)
  - [ ] MockApprovalAgent (always approves)
  - [ ] MockValidationAgent (always validates)
  - [ ] MockTimeoutAgent (simulates timeout)
  - [ ] MockFailureAgent (simulates failure)

- [ ] **Reference 5th Agent**
  - [ ] `.agents/agent-profiles/monitor-agent.yaml`
  - [ ] Case health monitoring capability

- [ ] **Agent Deployment Guide**
  - [ ] Manual deployment (docker run)
  - [ ] docker-compose deployment
  - [ ] Kubernetes deployment (optional Week 4)
  - [ ] Environment variables + secrets
  - [ ] Health check verification
  - [ ] Monitoring + logging setup

- [ ] **Documentation**
  - [ ] `package-info.java` for deployment + lifecycle packages
  - [ ] Deployment guide (Markdown)
  - [ ] Troubleshooting guide (common issues)
  - [ ] Example docker-compose.yml with comments

- [ ] **REST API Documentation**
  - [ ] OpenAPI/Swagger spec generated
  - [ ] Example requests/responses
  - [ ] Error codes + meanings
  - [ ] Rate limiting (if implemented)

- [ ] **Tests** (>80% coverage, >500 lines)
  - [ ] AgentDeployer Docker integration (10 tests)
  - [ ] HealthChecker timeout + retry (5 tests)
  - [ ] Lifecycle hooks + event emission (5 tests)
  - [ ] Integration tests 1-6 above (6 tests)
  - [ ] Stress test latency + circuit breaker (2 tests)
  - [ ] Docker image correctness (3 tests)
  - [ ] Total: 31+ integration tests

### Acceptance Criteria

- [x] 100% integration test pass rate
- [x] 5 agents fully deployed + operational
- [x] All orchestrations execute successfully
- [x] <100ms discovery latency p99 (sustained under load)
- [x] Zero skill invocation failures (100% success in tests)
- [x] Agent health checks passing
- [x] docker-compose works out-of-box
- [x] All tests green
- [x] Stress test passes (100 concurrent requests)

**Effort**: 1 engineer + 1 QA, 25 hours + 50 hours
**Cost**: $2,500 + $2,000 = $4,500

---

## Overall Completion Checklist

### Code Quality
- [ ] All new code passes SpotBugs analysis
- [ ] All new code passes Checkstyle (80 char lines, naming conventions)
- [ ] >80% test coverage (measured via JaCoCo)
- [ ] Zero TODO/FIXME comments (H gate enforcement)
- [ ] No silent failures or mock implementations (Q gate enforcement)
- [ ] Javadoc on all public classes/methods

### Build & CI/CD
- [ ] `bash scripts/dx.sh compile` passes (Marketplace module only)
- [ ] `bash scripts/dx.sh -pl yawl-integration` passes (full integration module)
- [ ] `bash scripts/dx.sh all` passes (all modules)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No failing checks in `.claude/hooks/hyper-validate.sh`

### Documentation
- [ ] README in `.agents/` directory
- [ ] `AGENTS-MARKETPLACE-MVP-DESIGN.md` (architectural spec)
- [ ] `AGENTS-MARKETPLACE-QUICK-START.md` (user guide)
- [ ] OpenAPI/Swagger spec (REST API)
- [ ] `package-info.java` for all new packages
- [ ] Inline Javadoc comments (non-trivial logic)

### Artifacts
- [ ] 5 agent profiles (YAML) in `.agents/agent-profiles/`
- [ ] `.agents/agents.yaml` (registry manifest)
- [ ] 3 orchestration templates (JSON) in `.agents/orchestration/`
- [ ] Dockerfile + docker-compose.yml
- [ ] Docker images pushed to registry (or tar files)

### Testing
- [ ] Unit tests: >200 test cases
- [ ] Integration tests: >30 test cases
- [ ] Coverage: >80% per module
- [ ] Performance tests: discovery <100ms p99
- [ ] Stress tests: 100 concurrent requests, no degradation

### Git & Commits
- [ ] All changes committed with meaningful messages
- [ ] Session URL in commit messages
- [ ] Specific files (not `git add .`)
- [ ] Linear history (no force push)

---

## Risk Mitigation Checklist

### Week 1 Risks
- [ ] **YAML parsing failures**: Use comprehensive test cases (edge cases in numbers, strings)
- [ ] **Validation overly strict**: Only enforce required + type checks (be lenient)

### Week 2 Risks
- [ ] **Git registry clone failures**: Implement retry + fallback to cache
- [ ] **SPARQL slowness**: Pre-build inverted index, cache queries (not full SPARQL)
- [ ] **Discovery >100ms**: Load-test with 100+ concurrent queries, profile code paths

### Week 3 Risks
- [ ] **DAG cycle detection bugs**: Extensive unit tests + visual inspection
- [ ] **Template compilation complexity**: Start simple (sequential), add patterns incrementally
- [ ] **JSONPath binding errors**: Test with real workflow data examples

### Week 4 Risks
- [ ] **Docker deployment variability**: Provide reference Dockerfile + test locally
- [ ] **Integration test flakiness**: Use testcontainers for Docker cleanup
- [ ] **Performance regression**: Run benchmarks each week, compare to baseline

---

## Sign-Off Criteria (End of Week 4)

**Lead Engineer**:
- [ ] All code reviewed + approved
- [ ] All tests passing
- [ ] Zero high-severity issues
- [ ] Ready for production deployment

**QA/Infrastructure**:
- [ ] All integration tests passing
- [ ] Performance targets met (<100ms discovery)
- [ ] Docker images built + tested
- [ ] Deployment guide reviewed

**Stakeholders**:
- [ ] 5 agents working as expected
- [ ] Discovery API performant
- [ ] Orchestration templates deployable
- [ ] Ready for Phase 2 (fine-tuning, advanced observability)

---

## Post-MVP Cleanup

After sign-off, before Phase 2:

- [ ] Review code for simplifications
- [ ] Remove mock implementations (if any, per Q gate)
- [ ] Add performance metrics/dashboards
- [ ] Document known limitations (in release notes)
- [ ] Plan Phase 2 roadmap (fine-tuning, multi-tenancy, cost optimization)

---

## Summary

**Total effort**: 145 hours backend + 65 hours QA/infra = 210 hours (2.5 engineers full-time, 4 weeks)
**Total cost**: $14,500 labor + $5,500 contingency = $20,000
**Deliverables**: 5 agents, 3 templates, registry, discovery API, 500+ tests, full documentation
**Success metrics**: All 6 acceptance criteria met by end of Week 4

**Next: Assign engineers + kickoff Week 1. Go build!**
