# Agents Marketplace MVP Design — 4-Week Implementation Plan

**Version**: 1.0
**Date**: 2026-02-21
**Scope**: Agent profiles, registry, capability discovery, orchestration templates
**Budget**: 4 weeks, 2 engineers, $50K
**Success Metric**: 5 agents, 100% skill invocation success, <100ms discovery

---

## Executive Summary

The Agents Marketplace enables pre-trained autonomous agents to discover, invoke, and orchestrate each other's skills in YAWL workflows. This MVP focuses on the 80/20 principle: agent profiles (YAML metadata), capability registry (Git-backed), discovery API (SPARQL), and orchestration templates (DAG-based).

**Why YAWL?** Agents invoke skills via A2A protocol (already implemented), workflow types define agent orchestration patterns, case monitoring tracks agent execution.

**Non-goals (Phase 2)**: Fine-tuning, custom training, advanced observability, multi-tenant isolation (use YAWL's tenant context), cost optimization models.

---

## Part 1: Architecture Overview

### 1.1 System Architecture (Diagram)

```
┌─────────────────────────────────────────────────────────────────┐
│ AGENTS MARKETPLACE (NEW)                                         │
│                                                                   │
│ ┌──────────────────────────────────────────────────────────────┐│
│ │ Agent Registry (Git-backed YAML)                             ││
│ │  ├─ .agents/agents.yaml (manifest + profiles)               ││
│ │  ├─ .agents/agent-profiles/ (individual profiles)           ││
│ │  │  ├─ approval-agent.yaml                                 ││
│ │  │  ├─ po-agent.yaml                                       ││
│ │  │  └─ [5 reference agents]                                ││
│ │  └─ .agents/orchestration/ (templates)                      ││
│ │     ├─ approval-workflow.json (DAG)                        ││
│ │     ├─ procurement.json                                    ││
│ │     └─ [3 common patterns]                                 ││
│ └──────────────────────────────────────────────────────────────┘│
│                                                                   │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ Capability Index (In-Memory SPARQL Graph)                 │  │
│ │ ├─ RDF: ?agent skos:broader ?skillType                   │  │
│ │ ├─ Index: Maps skill→agents (inverted, <100ms)          │  │
│ │ └─ Auto-update on git pull                               │  │
│ └────────────────────────────────────────────────────────────┘  │
│                                                                   │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ API Layer (REST/gRPC)                                     │  │
│ │ ├─ POST /agents/discover?capability=approve               │  │
│ │ ├─ GET /agents/{agent-id}/profile                        │  │
│ │ ├─ POST /orchestrate/deploy (DAG → YAWL spec)           │  │
│ │ └─ GET /agents/{agent-id}/health                        │  │
│ └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
        ↓ integrates with ↓                    ↓ invokes via ↓
┌──────────────────────────────────┐  ┌──────────────────────────┐
│ YAWL Workflow Engine             │  │ A2A Protocol             │
│ ├─ Case execution                │  │ ├─ agent.invoke(req)    │
│ ├─ Agent-to-agent handoff        │  │ ├─ Timeout/retry        │
│ └─ Skills Marketplace (existing) │  │ └─ Circuit breaker      │
└──────────────────────────────────┘  └──────────────────────────┘
```

### 1.2 Core Entities

#### AgentProfile (YAML)

```yaml
# .agents/agent-profiles/approval-agent.yaml
apiVersion: agents.marketplace/v1
kind: AgentProfile
metadata:
  id: approval-agent
  name: Approval Agent
  version: 1.0.0
  author: acme-team
  created: 2026-02-21T00:00:00Z

spec:
  description: "Auto-approves expenses under delegation limit"

  # Capabilities this agent can invoke
  capabilities:
    - id: approve-expense
      skillId: yawl.skills.approval.approve
      params:
        amount:
          type: number
          description: Expense amount in USD
        requestor:
          type: string
          description: Employee ID
      returns:
        approved:
          type: boolean
        confidence:
          type: number
          min: 0.0
          max: 1.0
        reason:
          type: string

    - id: escalate-approval
      skillId: yawl.skills.approval.escalate
      params:
        case_id:
          type: string
      returns:
        escalated_to:
          type: string

  # Agent deployment config
  deployment:
    type: docker
    image: acme/approval-agent:1.0.0
    port: 9001
    resources:
      memory: 512Mi
      cpu: 250m
    env:
      - name: DELEGATION_LIMIT
        value: "10000"
      - name: LOG_LEVEL
        value: INFO

  # Health check
  healthCheck:
    path: /health
    interval: 30s
    timeout: 5s

  # SLA / Reputation
  metrics:
    successRate: 0.985  # Success rate from past 100 invocations
    avgLatency: 250     # Milliseconds
    uptime: 0.9998      # Last 30 days
    costPerInvocation: 0.01  # USD
    invocationsThisMonth: 12450
```

#### AgentCapability (In-Registry)

```java
/**
 * Represents a single capability (skill + parameter set) an agent declares.
 */
public record AgentCapability(
    String id,                      // capability-approve-expense
    String skillId,                 // yawl.skills.approval.approve
    String description,
    Map<String, ParamSpec> params,  // Input params
    Map<String, ParamSpec> returns, // Output params
    Duration timeout,               // Max execution time
    int maxRetries                  // Circuit breaker setting
) {}

public record ParamSpec(
    String type,        // number, string, boolean, array, object
    String description,
    Object defaultValue,
    boolean required,
    Map<String, Object> constraints  // min, max, pattern, enum, etc.
) {}
```

#### OrchestrationTemplate (JSON DAG)

```json
{
  "apiVersion": "orchestration/v1",
  "kind": "Template",
  "metadata": {
    "id": "approval-workflow",
    "name": "Approval Workflow",
    "version": "1.0.0",
    "description": "Sequential approval: validate → approve → notify"
  },
  "spec": {
    "pattern": "sequential",
    "agents": [
      {
        "id": "validator",
        "agentId": "validation-agent",
        "capability": "validate-expense",
        "timeout": "10s",
        "retries": 2
      },
      {
        "id": "approver",
        "agentId": "approval-agent",
        "capability": "approve-expense",
        "input": {
          "amount": "$.expense.amount",
          "requestor": "$.request.requestor"
        },
        "timeout": "30s",
        "retries": 1,
        "dependsOn": ["validator"]
      },
      {
        "id": "notifier",
        "agentId": "notification-agent",
        "capability": "send-notification",
        "input": {
          "decision": "$.approver.decision",
          "reason": "$.approver.reason"
        },
        "timeout": "5s",
        "dependsOn": ["approver"]
      }
    ],
    "errorHandling": {
      "onValidationFailure": "escalate",
      "onApprovalFailure": "retry",
      "maxRetries": 3,
      "escalationAgent": "escalation-agent"
    }
  }
}
```

---

## Part 2: 4-Week Roadmap

### Week 1: Agent Profile Schema + Metadata Model

**Goal**: Define agent profile format, build YAML serialization, create 2 reference agents.

#### Deliverables

1. **AgentProfile.java** (record, YAML serializable)
   - Metadata (id, name, version, author, created)
   - Capabilities (list of skill refs)
   - Deployment config (docker, env, resources)
   - Metrics (success rate, latency, uptime, cost)
   - Health check config

2. **AgentCapability.java** (record)
   - Skill reference (skillId)
   - Input/output parameters (ParamSpec)
   - Timeout, retry settings
   - Serializable to/from YAML

3. **AgentProfileRepository.java** (interface)
   - `loadProfile(agentId): AgentProfile`
   - `listProfiles(): List<AgentProfile>`
   - `saveProfile(profile): void`
   - Git-backed implementation (read-only for MVP)

4. **Reference Agents** (2 created this week)
   - `approval-agent.yaml` (auto-approve <limit)
   - `validation-agent.yaml` (basic input validation)

5. **YAML Serialization**
   - Jackson ObjectMapper with YAML factory
   - Round-trip serialization tests

6. **Tests**
   - AgentProfile YAML deserialization (10 test cases)
   - Profile validation (required fields, types)
   - Capability parameter validation

**Effort**: 1 engineer, 5 days (40 hrs)
**Cost**: $2,500
**Acceptance Criteria**:
- 2 agents registered + validated
- 100% YAML round-trip fidelity
- Profile loads in <50ms
- All tests green

---

### Week 2: Agent Registry + Discovery API

**Goal**: Implement Git-backed registry, SPARQL capability index, REST discovery API.

#### Deliverables

1. **AgentRegistry.java** (core interface)
   - `getAgent(id): AgentProfile`
   - `findAgentsByCapability(skill): List<AgentProfile>`
   - `findAgentsByTag(tag): List<AgentProfile>`
   - `getAllAgents(): List<AgentProfile>`

2. **GitAgentRegistry.java** (implementation)
   - Clones `.agents/` from Git repo
   - Watches for changes (git pull on interval)
   - Memory-safe (immutable profiles)
   - Thread-safe (concurrent reads, single write thread for git sync)

3. **CapabilityIndex.java** (SPARQL-based)
   - In-memory RDF graph (Apache Jena)
   - Maps: skill → [agents] (inverted index)
   - Query pattern: `SELECT ?agent WHERE { ?agent skos:broader ?skill }`
   - Auto-rebuild on registry refresh

4. **Discovery API (REST)**
   ```
   POST /agents/discover?capability=approve
   Response: [
     { id: "approval-agent", confidence: 0.99, costPerCall: 0.01 },
     { id: "smart-approval-agent", confidence: 0.98, costPerCall: 0.05 }
   ]

   GET /agents/{id}/profile
   Response: Full AgentProfile

   GET /agents/{id}/health
   Response: { status: "healthy|degraded|offline", latency: 250ms }
   ```

5. **CapabilityMatcher.java** (SPARQL engine)
   - Query semantic skill hierarchy
   - Cache query results (5-min TTL)
   - Rank by success rate + latency

6. **Reference Agents** (3 more created)
   - `po-agent.yaml` (PO creation)
   - `expense-agent.yaml` (expense categorization)
   - `scheduler-agent.yaml` (calendar scheduling)

7. **Tests**
   - Discovery API: 5 capability queries
   - Latency: all <100ms (p99)
   - Git sync + agent availability after update
   - SPARQL index correctness

**Effort**: 1 engineer + 0.5 integrator, 5 days (40 hrs)
**Cost**: $2,500 + $1,250 = $3,750
**Acceptance Criteria**:
- 5 agents registered + discoverable
- Discovery <100ms p99 latency
- Registry auto-updates on git pull
- 98%+ query correctness (SPARQL)

---

### Week 3: Orchestration Template Builder (5 Common Patterns)

**Goal**: Implement template model, DAG compiler, 3 reference orchestrations.

#### Deliverables

1. **OrchestrationTemplate.java** (record, JSON serializable)
   - Metadata (id, name, version)
   - Pattern (sequential, parallel, conditional)
   - Agents (list + dependencies)
   - Error handling (retry, escalation)
   - Input/output schema

2. **TemplateCompiler.java**
   - Validates template DAG (no cycles)
   - Compiles to YAWL workflow spec (YSpecification)
   - Maps agent capabilities → YAWL tasks
   - Generates task data bindings (JSONPath)

3. **DAGValidator.java**
   - Checks for cycles (DFS)
   - Validates agent references exist
   - Type-checks parameter bindings
   - Detects missing dependencies

4. **AgentOrchestratorService.java**
   - `deployTemplate(template): YSpecification`
   - Converts DAG → workflow tasks
   - Creates A2A handoff tasks
   - Wraps agent invocations in error handlers

5. **Three Orchestration Templates** (reference)
   - `sequential.json`: validate → approve → notify
   - `parallel.json`: validate + check-compliance in parallel
   - `conditional.json`: if amount > limit → escalate else → auto-approve

6. **Integration with A2A**
   - Agent tasks invoke via A2AClient (existing)
   - Timeout per agent (from profile)
   - Circuit breaker (existing resilience layer)
   - Error escalation to dead letter queue

7. **Tests**
   - Template compilation (10 test cases)
   - DAG validation (cycles, missing refs, type mismatches)
   - Parameter binding (JSONPath expressions)
   - E2E: deploy template + mock agent invocation

**Effort**: 1 engineer + 0.5 architect, 5 days (40 hrs)
**Cost**: $2,500 + $1,250 = $3,750
**Acceptance Criteria**:
- 3 orchestration templates working
- Template → YAWL spec compilation verified
- All DAG validations passing
- <200ms compile time per template
- End-to-end mock execution succeeds

---

### Week 4: Integration Tests + Agent Lifecycle Management

**Goal**: Full integration testing, agent deployment automation, lifecycle hooks.

#### Deliverables

1. **AgentDeployer.java** (Docker wrapper)
   - `deploy(profile): AgentDeployment`
   - Pulls image, runs container, waits for health check
   - Exposes agent at configured port
   - Auto-rollback on health check failure

2. **AgentDeployment.java** (record)
   - Tracks deployed agent state
   - Container ID, ports, env vars
   - Health status, last checked
   - Rollback command

3. **AgentLifecycleHooks.java**
   - `onAgentRegistered(profile): void`
   - `onAgentDeployed(deployment): void`
   - `onAgentHealthDegraded(agent): void`
   - `onAgentUnregistered(agent): void`
   - Emit events to Observability layer (logs, metrics)

4. **Integration Test Suite** (comprehensive)

   **Test 1**: End-to-end workflow with agent
   - Create approval workflow
   - Deploy agents
   - Execute workflow with agent invocation
   - Verify case completion

   **Test 2**: Agent discovery + invocation
   - Register 5 agents
   - Discover by capability
   - Invoke agent via A2A
   - Assert response correctness

   **Test 3**: Orchestration template deployment
   - Load template from YAML
   - Compile to workflow
   - Deploy agents
   - Execute workflow
   - Verify all tasks completed

   **Test 4**: Error handling
   - Agent timeout → retry → escalate
   - Invalid input → validation error
   - Agent crash → circuit breaker opens
   - Verify fallback behavior

   **Test 5**: Metrics + monitoring
   - Track success rate per agent
   - Monitor latency distribution
   - Alert on degradation
   - Verify metrics published to observability

5. **Docker Images** (reference agents)
   - Dockerfile for approval-agent
   - docker-compose.yml for multi-agent setup
   - Health check endpoints
   - Config via environment variables

6. **Deployment Guide**
   - Manual deployment (docker run)
   - Kubernetes deployment (yaml)
   - Environment variables + secrets
   - Health check + monitoring

7. **Reference 5th Agent**
   - `monitor-agent.yaml` (case health monitoring)
   - Completes integration tests

**Effort**: 1 engineer + 1 QA, 5 days (50 hrs)
**Cost**: $2,500 + $2,000 = $4,500
**Acceptance Criteria**:
- 100% integration test pass rate
- 5 agents fully deployed + operational
- All orchestrations execute successfully
- <100ms discovery latency (p99)
- Zero skill invocation failures

---

## Part 3: Code Structure

### 3.1 Package Layout

```
src/org/yawlfoundation/yawl/integration/marketplace/
├─ agent/
│  ├─ AgentProfile.java (record, YAML-serializable)
│  ├─ AgentCapability.java (record)
│  ├─ AgentContext.java (execution context)
│  └─ package-info.java
├─ registry/
│  ├─ AgentRegistry.java (interface)
│  ├─ GitAgentRegistry.java (implementation)
│  ├─ AgentProfileRepository.java (interface)
│  ├─ InMemoryProfileRepository.java
│  └─ package-info.java
├─ discovery/
│  ├─ CapabilityIndex.java (SPARQL graph)
│  ├─ CapabilityMatcher.java (query engine)
│  ├─ DiscoveryService.java (REST API)
│  ├─ DiscoveryRequest.java (request DTO)
│  ├─ DiscoveryResponse.java (response DTO)
│  └─ package-info.java
├─ orchestration/
│  ├─ OrchestrationTemplate.java (record, JSON)
│  ├─ TemplateCompiler.java (DAG → YAWL)
│  ├─ DAGValidator.java (cycle detection, validation)
│  ├─ AgentOrchestratorService.java (deployment)
│  ├─ TemplateRepository.java (interface)
│  ├─ FileTemplateRepository.java (Git-backed)
│  └─ package-info.java
├─ deployment/
│  ├─ AgentDeployer.java (Docker wrapper)
│  ├─ AgentDeployment.java (record)
│  ├─ DeploymentStatus.java (enum)
│  ├─ HealthChecker.java (agent health)
│  └─ package-info.java
├─ lifecycle/
│  ├─ AgentLifecycleListener.java (interface)
│  ├─ AgentLifecycleHooks.java (default impl)
│  ├─ AgentEvent.java (sealed type)
│  └─ package-info.java
├─ api/
│  ├─ AgentMarketplaceController.java (REST)
│  ├─ DiscoveryController.java (REST)
│  ├─ OrchestratorController.java (REST)
│  └─ package-info.java
├─ metrics/
│  ├─ AgentMetrics.java (success rate, latency)
│  ├─ MetricsCollector.java (interface)
│  └─ package-info.java
└─ package-info.java
```

### 3.2 Key Classes (Pseudocode)

#### AgentProfile (Record)

```java
public record AgentProfile(
    String id,
    String name,
    String version,
    String author,
    String description,
    List<AgentCapability> capabilities,
    DeploymentConfig deployment,
    HealthCheckConfig healthCheck,
    AgentMetrics metrics
) {
    // YAML serialization
    public static AgentProfile fromYaml(String yaml) {
        ObjectMapper mapper = YamlMapper.forProfile();
        return mapper.readValue(yaml, AgentProfile.class);
    }

    public String toYaml() {
        ObjectMapper mapper = YamlMapper.forProfile();
        return mapper.writeValueAsString(this);
    }

    // Validation
    public void validate() throws ValidationException {
        if (id == null || id.isBlank()) throw new ValidationException("id required");
        if (capabilities.isEmpty()) throw new ValidationException(">=1 capability required");
        capabilities.forEach(AgentCapability::validate);
    }
}

public record DeploymentConfig(
    String type,  // docker, process, remote
    String image,
    int port,
    Map<String, String> env,
    ResourceRequirements resources
) {}

public record AgentMetrics(
    double successRate,
    long avgLatency,       // ms
    double uptime,
    double costPerInvocation,
    long invocationsThisMonth
) {}
```

#### GitAgentRegistry (Implementation)

```java
public class GitAgentRegistry implements AgentRegistry {
    private final String repoUrl;
    private final String localPath;  // ~/.yawl/agents-registry/
    private final Map<String, AgentProfile> cache = new ConcurrentHashMap<>();
    private final CapabilityIndex index;
    private volatile boolean healthy = false;

    public GitAgentRegistry(String repoUrl) {
        this.repoUrl = repoUrl;
        this.localPath = System.getProperty("user.home") + "/.yawl/agents-registry";
        this.index = new CapabilityIndex();
        this.init();
    }

    private void init() {
        try {
            // Clone or update repo
            if (Files.notExists(Path.of(localPath))) {
                Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(localPath))
                    .call();
            } else {
                Git.open(new File(localPath))
                    .pull()
                    .call();
            }

            // Load all profiles
            loadProfiles();
            this.healthy = true;
        } catch (Exception e) {
            this.healthy = false;
            throw new RegistryException("Failed to initialize registry", e);
        }
    }

    private void loadProfiles() throws IOException {
        Path profileDir = Path.of(localPath, ".agents", "agent-profiles");
        if (!Files.exists(profileDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(profileDir, "*.yaml")) {
            for (Path path : stream) {
                String yaml = Files.readString(path);
                AgentProfile profile = AgentProfile.fromYaml(yaml);
                profile.validate();
                cache.put(profile.id(), profile);
                index.add(profile);
            }
        }
    }

    @Override
    public AgentProfile getAgent(String id) {
        AgentProfile profile = cache.get(id);
        if (profile == null) {
            throw new AgentNotFoundException("Agent not found: " + id);
        }
        return profile;
    }

    @Override
    public List<AgentProfile> findAgentsByCapability(String skillId) {
        return index.findAgents(skillId);
    }

    @Override
    public List<AgentProfile> getAllAgents() {
        return List.copyOf(cache.values());
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }
}
```

#### CapabilityIndex (SPARQL)

```java
public class CapabilityIndex {
    private final Model rdfModel = ModelFactory.createDefaultModel();
    private final Map<String, List<AgentProfile>> skillIndex = new ConcurrentHashMap<>();

    public void add(AgentProfile profile) {
        Resource agent = rdfModel.createResource(profile.id());

        for (AgentCapability cap : profile.capabilities()) {
            // RDF triple: agent skos:broader skillId
            Property broader = rdfModel.createProperty(
                "http://www.w3.org/2004/02/skos/core#",
                "broader"
            );
            Resource skill = rdfModel.createResource(cap.skillId());
            agent.addProperty(broader, skill);

            // Inverted index: skill → agents
            skillIndex.computeIfAbsent(cap.skillId(), k -> new ArrayList<>())
                .add(profile);
        }
    }

    public List<AgentProfile> findAgents(String skillId) {
        return skillIndex.getOrDefault(skillId, List.of());
    }

    public List<AgentProfile> findAgentsByBroader(String skillId) {
        // SPARQL: agents that declare skillId or parent skill
        String query = """
            SELECT ?agent WHERE {
              ?agent skos:broader ?skill .
              { ?skill = <%s> }
              UNION
              { ?skill skos:narrower* <%s> }
            }
            """.formatted(skillId, skillId);

        Query q = QueryFactory.create(query);
        QueryExecution exec = QueryExecutionFactory.create(q, rdfModel);

        return exec.execSelect().stream()
            .map(qs -> qs.getResource("?agent").getLocalName())
            .map(skillIndex::get)
            .flatMap(List::stream)
            .distinct()
            .toList();
    }
}
```

#### DiscoveryService (REST)

```java
@RestController
@RequestMapping("/agents")
public class DiscoveryController {
    private final AgentRegistry registry;
    private final CapabilityIndex index;

    @PostMapping("/discover")
    public List<AgentDiscoveryResult> discover(
        @RequestParam String capability,
        @RequestParam(required = false) Integer limit
    ) {
        long start = System.nanoTime();

        // Find agents with this capability
        List<AgentProfile> agents = index.findAgents(capability);

        // Rank by success rate, then latency
        var results = agents.stream()
            .map(p -> new AgentDiscoveryResult(
                p.id(),
                p.name(),
                p.metrics().successRate(),
                p.metrics().avgLatency(),
                p.metrics().costPerInvocation()
            ))
            .sorted((a, b) -> {
                int cmp = Double.compare(b.successRate(), a.successRate());
                if (cmp != 0) return cmp;
                return Long.compare(a.avgLatency(), b.avgLatency());
            })
            .limit(limit != null ? limit : 10)
            .toList();

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        if (elapsed > 100) {
            logger.warn("Slow discovery: {} agents found in {}ms", agents.size(), elapsed);
        }

        return results;
    }

    @GetMapping("/{agentId}/profile")
    public AgentProfile getProfile(@PathVariable String agentId) {
        return registry.getAgent(agentId);
    }

    @GetMapping("/{agentId}/health")
    public AgentHealthResponse health(@PathVariable String agentId) {
        AgentProfile profile = registry.getAgent(agentId);
        HealthCheckResult result = healthChecker.check(profile);

        return new AgentHealthResponse(
            result.status(),
            result.latency(),
            result.checkedAt()
        );
    }
}

public record AgentDiscoveryResult(
    String id,
    String name,
    double successRate,
    long avgLatency,
    double costPerInvocation
) {}
```

#### OrchestrationTemplate (Compiler)

```java
public class TemplateCompiler {
    private final AgentRegistry registry;
    private final YSpecificationFactory specFactory;

    public YSpecification compile(OrchestrationTemplate template) throws TemplateCompileException {
        // Validate DAG
        validateDAG(template);

        // Create YAWL specification
        YSpecification spec = specFactory.createSpecification(
            template.metadata().id(),
            template.metadata().version()
        );

        // Create net (workflow)
        YNet net = new YNet(template.metadata().id(), spec);
        spec.addNet(net);

        // Create input/output conditions
        YInputCondition input = new YInputCondition("input_condition", net);
        YOutputCondition output = new YOutputCondition("output_condition", net);
        net.addInputCondition(input);
        net.addOutputCondition(output);

        // Create tasks for each agent in DAG order
        Map<String, YTask> taskMap = new HashMap<>();
        for (AgentRef agent : topoSort(template)) {
            YTask task = createAgentTask(agent, template, net);
            taskMap.put(agent.id(), task);
            net.addNetElement(task);
        }

        // Add flows between tasks (based on dependencies)
        for (AgentRef agent : template.agents()) {
            YTask task = taskMap.get(agent.id());
            for (String depId : agent.dependsOn()) {
                YTask depTask = taskMap.get(depId);
                createFlow(net, depTask, task);
            }
        }

        // Connect input condition to first tasks, output to last tasks
        List<AgentRef> roots = findRootAgents(template);
        List<AgentRef> leaves = findLeafAgents(template);

        for (AgentRef root : roots) {
            createFlow(net, input, taskMap.get(root.id()));
        }

        for (AgentRef leaf : leaves) {
            createFlow(net, taskMap.get(leaf.id()), output);
        }

        return spec;
    }

    private YTask createAgentTask(AgentRef agentRef, OrchestrationTemplate template, YNet net) {
        AgentProfile profile = registry.getAgent(agentRef.agentId());
        AgentCapability cap = findCapability(profile, agentRef.capability());

        YTask task = new YTask(agentRef.id(), net);
        task.setName(agentRef.agentId() + ":" + agentRef.capability());

        // Create custom input/output
        YInputSet input = createInputSet(cap, agentRef);
        task.setInputSet(input);

        // Add A2A handoff to agent
        YDecomposition decomp = new YDecomposition(agentRef.id() + "_decomp");
        decomp.setDecompositionType(YDecomposition.DecompositionType.WEBSERVICE);
        decomp.setCodelet(new AgentHandoffCodelet(
            agentRef.agentId(),
            agentRef.capability(),
            agentRef.timeout(),
            agentRef.retries()
        ));

        spec.addDecomposition(decomp);
        task.setDecomposition(decomp);

        return task;
    }

    private void validateDAG(OrchestrationTemplate template) throws TemplateCompileException {
        Set<String> agentIds = template.agents().stream()
            .map(AgentRef::id)
            .collect(toSet());

        // Check for cycles (DFS)
        for (AgentRef agent : template.agents()) {
            if (hasCycle(agent, new HashSet<>(), template)) {
                throw new TemplateCompileException("Circular dependency detected: " + agent.id());
            }
        }

        // Check all dependencies exist
        for (AgentRef agent : template.agents()) {
            for (String depId : agent.dependsOn()) {
                if (!agentIds.contains(depId)) {
                    throw new TemplateCompileException("Dependency not found: " + depId);
                }
            }
        }

        // Check agents exist in registry
        for (AgentRef agent : template.agents()) {
            AgentProfile profile = registry.getAgent(agent.agentId());
            boolean hasCap = profile.capabilities().stream()
                .anyMatch(c -> c.id().equals(agent.capability()));
            if (!hasCap) {
                throw new TemplateCompileException(
                    "Agent " + agent.agentId() + " missing capability " + agent.capability()
                );
            }
        }
    }
}
```

#### AgentDeployer (Docker)

```java
public class AgentDeployer {
    private final DockerClient docker = DockerClientBuilder.getInstance().build();

    public AgentDeployment deploy(AgentProfile profile) throws DeploymentException {
        try {
            // Pull image
            docker.pullImageCmd(profile.deployment().image())
                .exec(new PullImageResultCallback())
                .awaitCompletion();

            // Create container
            CreateContainerResponse container = docker.createContainerCmd(
                profile.deployment().image()
            )
                .withName(profile.id() + "-" + UUID.randomUUID())
                .withHostConfig(HostConfig.newHostConfig()
                    .withPortBindings(PortBinding.parse("9001:" + profile.deployment().port()))
                    .withMemory(512 * 1024 * 1024)  // 512MB
                    .withMemorySwap(512 * 1024 * 1024)
                    .withCpuShares(250))
                .withEnv(profile.deployment().env().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toArray(String[]::new))
                .exec();

            String containerId = container.getId();

            // Start container
            docker.startContainerCmd(containerId).exec();

            // Wait for health check
            if (!waitForHealthy(profile, 30)) {
                docker.stopContainerCmd(containerId).exec();
                docker.removeContainerCmd(containerId).exec();
                throw new DeploymentException("Agent failed health check");
            }

            return new AgentDeployment(
                profile.id(),
                containerId,
                "localhost",
                profile.deployment().port(),
                DeploymentStatus.RUNNING,
                Instant.now()
            );
        } catch (Exception e) {
            throw new DeploymentException("Failed to deploy agent: " + profile.id(), e);
        }
    }

    private boolean waitForHealthy(AgentProfile profile, int seconds) {
        String healthUrl = "http://localhost:" + profile.deployment().port()
            + profile.healthCheck().path();

        for (int i = 0; i < seconds * 2; i++) {
            try {
                HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(healthUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {
                // Still starting up
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }
}
```

---

## Part 4: Integration Points

### 4.1 Integration with A2A Protocol

Agent capabilities are invoked via existing A2A protocol:

```java
// In TemplateCompiler or WorkflowTask
A2AClient agent = new A2AClient(agentProfile.deployment().endpoint());

WorkflowRequest request = new WorkflowRequest()
    .put("amount", 5000)
    .put("requestor", "alice@company.com");

try {
    WorkflowResponse response = agent.invokeWithTimeout(
        request,
        Duration.ofSeconds(agentRef.timeout())
    );

    if (!response.isSuccess()) {
        escalate(response.error());
    }

    return response.getData();
} catch (TimeoutException e) {
    if (agentRef.retries() > 0) {
        // Retry with exponential backoff
        return retryWithBackoff(agentRef, request);
    } else {
        escalate("Agent timeout after " + agentRef.timeout() + "s");
    }
}
```

### 4.2 Integration with Skills Marketplace

Agent capabilities reference Skills Marketplace:

```yaml
capabilities:
  - id: approve-expense
    skillId: yawl.skills.approval.approve  # Reference to Skills Marketplace
    params:
      amount:
        type: number
    returns:
      approved:
        type: boolean
```

The `skillId` is a reference to the Skills Marketplace (separate system). Discovery works by:
1. Agent declares capability with `skillId`
2. Capability Index maps `skillId` → agents
3. Discovery API returns agents that can handle skill

### 4.3 Integration with Case Monitoring

Agent execution generates case events:

```java
// In agent task completion handler
case.emitEvent(new AgentExecutionEvent(
    caseId,
    agentId,
    capability,
    response.getData(),
    durationMs,
    response.isSuccess()
));

// Case monitor can react to events
// (E.g., escalate if agent fails)
```

---

## Part 5: Success Criteria & Metrics

### 5.1 MVP Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| Agents registered | 5 | Count in registry |
| Discovery latency | <100ms p99 | `time discovery API` across 100 queries |
| Skill invocation success | 100% | Failed invocations / total |
| Template compilation | <200ms | Time to compile complex template |
| Agent health check | <5s | Time to determine agent health |
| Test coverage | >80% | Covered code / total code |
| Documentation | Complete | Package-info.java + REST API docs |

### 5.2 Key Metrics (Tracked per Agent)

- **Success rate**: Successful invocations / total (target: >98%)
- **Average latency**: P50, P99, P999 (target: P99 <500ms)
- **Uptime**: 30-day rolling average (target: >99.9%)
- **Cost per invocation**: USD (informational)
- **Invocations/month**: Load indicator

### 5.3 Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Agent discovery | <100ms | SPARQL index query |
| Template compilation | <200ms | DAG → YAWL spec |
| Agent deployment | <30s | Docker pull + start + health check |
| Profile load from YAML | <50ms | Deserialization + validation |
| Registry refresh (git pull) | <5s | Clone 50-agent profiles |

---

## Part 6: Deliverables & Artifacts

### 6.1 Code Artifacts

1. **Core Classes** (Week 1-4)
   - `AgentProfile.java`, `AgentCapability.java`
   - `AgentRegistry.java`, `GitAgentRegistry.java`
   - `CapabilityIndex.java`, `CapabilityMatcher.java`
   - `OrchestrationTemplate.java`, `TemplateCompiler.java`
   - `AgentDeployer.java`, `AgentDeployment.java`
   - REST controllers for discovery + orchestration
   - Lifecycle hooks + metrics collection

2. **Agent Profiles** (Git-backed YAML)
   - `approval-agent.yaml` (auto-approval)
   - `validation-agent.yaml` (input validation)
   - `po-agent.yaml` (PO creation)
   - `expense-agent.yaml` (categorization)
   - `scheduler-agent.yaml` (scheduling)

3. **Orchestration Templates** (JSON)
   - `sequential.json` (validate → approve → notify)
   - `parallel.json` (parallel validation + compliance)
   - `conditional.json` (if amount > limit → escalate)

4. **Docker Images** (Reference implementations)
   - `approval-agent` Docker image + Dockerfile
   - `docker-compose.yml` for multi-agent setup

### 6.2 Documentation

1. **REST API Documentation**
   - OpenAPI/Swagger for discovery, deployment, orchestration APIs
   - Example requests/responses
   - Error codes + handling

2. **User Guide**
   - How to register new agent
   - How to discover agents by capability
   - How to create orchestration template
   - How to deploy workflow with agents

3. **Developer Guide**
   - Agent profile schema (YAML)
   - AgentCapability format
   - Integration with A2A protocol
   - Metrics collection + monitoring

4. **Architecture Document**
   - Component diagram (agent registry, discovery, orchestration)
   - Data flow (template → workflow execution)
   - Integration points (A2A, Skills Marketplace, Case monitoring)

### 6.3 Test Artifacts

1. **Unit Tests** (>80% coverage)
   - AgentProfile YAML serialization (20 tests)
   - GitAgentRegistry Git sync (15 tests)
   - CapabilityIndex SPARQL queries (20 tests)
   - TemplateCompiler DAG validation (25 tests)
   - AgentDeployer Docker integration (10 tests)

2. **Integration Tests**
   - End-to-end workflow with agent (1 test)
   - Discovery + invocation (1 test)
   - Orchestration deployment (1 test)
   - Error handling (1 test)
   - Metrics + monitoring (1 test)

3. **Performance Tests**
   - Discovery latency under load (100 concurrent queries)
   - Template compilation time (100 complex templates)
   - Registry refresh with 50+ agents

4. **Mock Agents** (for testing)
   - MockApprovalAgent (always approves)
   - MockValidationAgent (always validates)
   - MockTimeoutAgent (simulates timeout)
   - MockFailureAgent (simulates failure)

---

## Part 7: Implementation Risks & Mitigation

### 7.1 Key Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| SPARQL performance (>100ms) | Medium | High | Use inverted index cache, pre-compile queries |
| Git registry sync failures | Low | High | Implement retry + fallback to cached profiles |
| Agent deployment (Docker) complexity | Medium | Medium | Use docker-compose, provide reference images |
| Template DAG cycle detection bugs | Low | High | Extensive unit tests, thorough DFS validation |
| Integration with existing A2A | Low | High | Leverage existing YawlA2AClient (proven) |
| YAML serialization edge cases | Medium | Low | Use Jackson + comprehensive YAML tests |

### 7.2 Contingency Plans

- If SPARQL too slow: Fall back to in-memory inverted index (simpler, faster)
- If Git integration fails: Use file-based registry (load from local directory)
- If Docker deployment complex: Start with manual deployment, add automation in Phase 2
- If DAG compilation takes >200ms: Profile + optimize, or implement incremental compilation

---

## Part 8: Phase 2 Roadmap (Future)

**Out of scope for MVP, but planned for Phase 2:**

1. **Fine-tuning & Custom Training**
   - Agents can be fine-tuned on custom datasets
   - Custom agent creation workflow
   - Model versioning

2. **Advanced Observability**
   - Distributed tracing (OpenTelemetry)
   - Agent debugging + logging
   - Workflow execution replay

3. **Cost Optimization**
   - Cost models per agent
   - Optimization suggestions (faster vs cheaper)
   - Budget enforcement

4. **Multi-Tenant Isolation**
   - Agent registry scoped by tenant
   - Cross-tenant capability sharing policies
   - Tenant-specific SLAs

5. **Agent Federation**
   - Agent-to-agent communication (beyond handoff)
   - Hierarchical agent organization
   - Agent discovery across federated registries

6. **Advanced Scheduling**
   - Agent resource allocation (GPU, memory)
   - Batch execution (queue multiple invocations)
   - Predictive scaling (pre-warm agents)

---

## Part 9: Cost Breakdown

### 9.1 Resource Allocation (4 weeks, 2 engineers)

| Week | Engineer 1 (Backend) | Engineer 2 (QA/Infra) | Cost |
|------|--|--|--|
| Week 1 | Profile schema, YAML serialization (40 hrs) | Setup, reference agents (5 hrs) | $2,500 |
| Week 2 | Registry, discovery API (40 hrs) | API testing, SPARQL validation (5 hrs) | $3,750 |
| Week 3 | Orchestration compiler, DAG validation (40 hrs) | Template testing, reference templates (5 hrs) | $3,750 |
| Week 4 | Deployment automation, hooks (25 hrs) | Integration tests, Docker setup, monitoring (50 hrs) | $4,500 |
| **Total** | **145 hrs** | **65 hrs** | **$14,500** |

### 9.2 Infrastructure Costs

| Item | Quantity | Cost |
|------|----------|------|
| Git hosting (GitLab/GitHub private repo) | 1 | Included in org plan |
| Container registry (Docker Hub) | 5 agent images | $0 (free tier) |
| Dev environment (local Docker) | 2 engineers | $0 |
| **Total Infrastructure** | | **$0** |

### 9.3 Total Budget: $14,500 (labor only)

**Note**: Adding $5,000 contingency + buffer for unforeseen issues → **$20,000 total** for 4-week MVP.

---

## Part 10: Success Measurement (End of Week 4)

### 10.1 Acceptance Tests

```bash
# Test 1: Agent registry loads 5 agents
$ curl http://localhost:8080/agents
Response: 5 agents in JSON

# Test 2: Discovery finds agents by capability
$ curl -X POST "http://localhost:8080/agents/discover?capability=approve"
Response: ["approval-agent", "smart-approval-agent"] (< 100ms)

# Test 3: Template compiles to YAWL spec
$ curl -X POST http://localhost:8080/orchestrate/compile \
  -d @sequential.json
Response: YSpecification (YAWL workflow def)

# Test 4: Workflow executes with agent invocation
$ mvn test -Dtest=AgentWorkflowIntegrationTest
Response: BUILD SUCCESS (all 5 test cases pass)

# Test 5: Agent deployment works
$ docker run -e AGENT_ID=approval-agent approval-agent:1.0.0
Response: Container healthy (passes health check at /health)
```

### 10.2 Final Deliverable Checklist

- [x] 5 agents registered + profiles validated
- [x] Agent registry loads from Git
- [x] Discovery API <100ms p99 latency
- [x] 3 orchestration templates working
- [x] Template compiler generates valid YAWL specs
- [x] Agent deployment via Docker
- [x] A2A integration (agent invocation within workflows)
- [x] Full integration test suite passing
- [x] REST API documentation (OpenAPI)
- [x] Developer guide (YAML format, API patterns)
- [x] Reference Docker images (all 5 agents)
- [x] docker-compose.yml for local testing

---

## Part 11: Decision Records

### 11.1 Why YAML for Agent Profiles?

**Options considered**:
1. YAML (human-readable, Git-friendly) ← **CHOSEN**
2. JSON (programmatic, typed)
3. Protocol Buffers (efficient, generated code)
4. ConfigMaps (Kubernetes-native)

**Decision**: YAML because:
- Highly readable (non-engineers can understand)
- Natural in Git repos + version control friendly
- Jackson YAML support is mature
- Aligns with Kubernetes + Helm conventions

### 11.2 Why Git-Backed Registry?

**Options considered**:
1. Git repo with YAML profiles ← **CHOSEN**
2. Central database (PostgreSQL)
3. REST API registry (external service)
4. In-memory registry (non-persistent)

**Decision**: Git because:
- Audit trail (commit history)
- Simple to set up (no database)
- Familiar to engineers (GitOps style)
- Works offline (clone locally)

### 11.3 Why SPARQL for Capability Index?

**Options considered**:
1. SPARQL (semantic, flexible) ← **CHOSEN**
2. Relational database (normalized)
3. Inverted index (fast, simple)
4. GraphQL (query language)

**Decision**: SPARQL (with inverted index cache) because:
- Handles skill hierarchy (narrower/broader relationships)
- Standard semantic query language
- Mature Jena library
- Can add reasoning later (Phase 2)
- **Caveat**: Cache index for <100ms latency (avoid full SPARQL queries on hot path)

### 11.4 Why DAG Templates (not FSM)?

**Options considered**:
1. Directed acyclic graphs (DAG) ← **CHOSEN**
2. Finite state machines (FSM)
3. BPMN workflows
4. Kubernetes Argo Workflows

**Decision**: DAG because:
- Maps naturally to agent orchestration (agent chains)
- Simple to compile to YAWL workflows
- No state explosion (unlike FSM)
- Standard in ML pipelines + Kubernetes (familiar)

---

## Part 12: References

### 12.1 YAWL Integration Points

- **A2A Protocol**: `/src/org/yawlfoundation/yawl/integration/a2a/` (existing)
- **Agent Info**: `/src/org/yawlfoundation/yawl/integration/a2a/AgentInfo.java`
- **Workflow Engine**: `/src/org/yawlfoundation/yawl/engine/` (YEngine, YWorkItem, YCase)
- **Skills Marketplace**: Separate system (referenced via skillId)

### 12.2 External Libraries

- **YAML**: `com.fasterxml.jackson:jackson-dataformat-yaml`
- **SPARQL**: `org.apache.jena:jena-core` + `jena-querybuilder`
- **RDF**: Apache Jena Model API
- **Docker**: `com.github.docker-java:docker-java`
- **JSON**: Jackson (already in pom.xml)

### 12.3 Documentation

- Agent Profile YAML Schema: This document (Section 1.2)
- Orchestration Template JSON Schema: This document (Section 1.2)
- REST API: OpenAPI/Swagger (to be generated)
- YAWL Workflow Creation: Existing YAWL documentation

---

## Conclusion

This 4-week MVP delivers a production-ready Agents Marketplace with:
- **5 reference agents** (approval, validation, PO, expense, scheduler)
- **Agent registry** (Git-backed, YAML profiles, <100ms discovery)
- **Orchestration templates** (3 common patterns: sequential, parallel, conditional)
- **Full integration** with YAWL A2A protocol + case monitoring
- **Deployment automation** (Docker images + compose)

**Total cost**: $20K (4 weeks, 2 engineers)
**Time to production**: 4 weeks
**Maintenance burden**: Minimal (stateless discovery, Git-backed registry)

Post-MVP, Phase 2 will add fine-tuning, advanced observability, cost optimization, and multi-tenancy. For now, **this MVP is sufficient to demonstrate autonomous agents orchestrating YAWL workflows**.

---

**Document Prepared**: 2026-02-21
**Next Steps**: Present to stakeholders, kickoff Week 1, assign engineers
**Contact**: [Integration Team Lead]
