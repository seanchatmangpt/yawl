# Autonomous Agents & Skills Marketplace in YAWL v6.0.0

**Quadrant**: Explanation | **Concept**: Agent architecture, skill framework, and marketplace patterns

This document explains YAWL's autonomous agent framework and skills marketplace — how agents discover and compose capabilities, when to use single agents vs. teams, and how agents integrate with the broader GODSPEED quality pipeline.

---

## Table of Contents

1. [Overview](#overview)
2. [Agent Architecture](#agent-architecture)
3. [Skills Framework](#skills-framework)
4. [Marketplace Patterns](#marketplace-patterns)
5. [Integration with Teams](#integration-with-teams)
6. [MCP/A2A Integration](#mcpa2a-integration)
7. [Decision Tree: When to Use Agents](#decision-tree-when-to-use-agents)
8. [Real Examples](#real-examples)

---

## Overview

### What Are Autonomous Agents?

**Autonomous agents** in YAWL v6.0.0 are AI-powered software components that:

1. **Self-organize**: Discover work items from the YAWL Engine without explicit task assignment
2. **Reason about eligibility**: Determine if they can perform a task using configured strategies or AI reasoning (Claude, Z.AI)
3. **Make decisions**: Decide the appropriate action for a work item autonomously
4. **Coordinate peer-to-peer**: Discover other agents via A2A (Agent-to-Agent) protocol and share context
5. **Execute without human intervention**: Complete work items through Interface B (YAWL client API)

**Example workflow**:
```
YAWL Engine creates work item "Approve Customer Order"
                            ↓
       Shipper Agent polls enabled work items
                            ↓
       Shipper Agent evaluates: "Can I approve orders?"
                            ↓
       Shipper Agent discovers Warehouse Agent via A2A
                            ↓
       Shipper Agent reasons: "This is a large order (>$10k)
                               Warehouse has inventory. Approve it."
                            ↓
       Shipper Agent completes work item via Interface B
```

### Skills as Composable Units

A **skill** is a discrete, versioned capability that an agent can perform. Examples:

- **DataValidation**: Verify input data against schema
- **DocumentClassification**: Categorize documents by type (invoice, PO, receipt)
- **InventoryCheck**: Query warehouse system and return stock levels
- **DecisionReasoning**: Use Claude to reason about complex business rules
- **CostCalculation**: Compute shipping costs based on weight, distance, carrier

Skills are:
- **Composable**: Agents chain multiple skills together (Classify → Validate → Calculate → Decide)
- **Versioned**: Each skill has semantic versioning (DataValidation v2.1.0 is backward-compatible with v2.0.x)
- **Isolated**: Failures in one skill don't cascade (circuit breakers, fallbacks)
- **Discoverable**: Listed in skill registry with contract, authentication, and SLA
- **Metered**: Usage and cost tracked per skill per agent

### The Marketplace Concept

The **Skills Marketplace** is a federation of skill providers:

1. **Community Skills**: Open-source, maintained by YAWL community (validation, formatting, classification)
2. **Enterprise Skills**: Licensed, SLA-backed (compliance checks, audit logging, PII masking)
3. **Custom Skills**: Internal to your organization (domain-specific rules, legacy system integrations)
4. **Vendor Skills**: Third-party providers (Stripe, Slack, Datadog, ServiceNow)

Agents **discover skills at runtime** through:
- Local skill registry (`~/.yawl-skills/registry.json`)
- Remote registry (Artifactory, Maven Central, CRATES/PyPI)
- A2A protocol (peer agents advertise their skills in `/.well-known/agent.json`)

**Cost-benefit of marketplace**:
- Reduce development time: Reuse validated skills instead of building from scratch
- Lower risk: Community skills vetted by multiple teams
- Standardize patterns: All agents use the same validation logic
- Enable ecosystem: External partners can contribute skills

---

## Agent Architecture

### Agent Lifecycle: Creation → Skill Binding → Execution

```
┌─────────────────────────────────────────────────────────────────┐
│                      AGENT LIFECYCLE                             │
└─────────────────────────────────────────────────────────────────┘

PHASE 1: CREATION (Agent Boot)
  ├─ Load YAML configuration (agent identity, capabilities, YAWL endpoint)
  ├─ Resolve skill dependencies (registry lookup, SemVer matching)
  ├─ Initialize resilience components (circuit breaker, retry policy)
  ├─ Start HTTP server for A2A discovery (/.well-known/agent.json)
  └─ Log: "AgentName startup complete. Ready to poll YAWL."

PHASE 2: DISCOVERY (Continuous)
  ├─ Poll YAWL Engine Interface B: "GET /cases/{case}/workitems?state=enabled"
  ├─ For each enabled work item:
  │  ├─ Extract: task name, input data, case context
  │  └─ Queue for eligibility evaluation
  ├─ Discover peer agents (A2A protocol)
  │  ├─ Query /.well-known/agent.json from known peers
  │  ├─ Extract advertised skills and SLA
  │  └─ Update local peer registry
  └─ Sleep N seconds, then repeat

PHASE 3: ELIGIBILITY (Per Work Item)
  ├─ Run EligibilityReasoner:
  │  ├─ Programmatic: Check if task name matches agent capability
  │  └─ AI-powered: Use Claude to reason: "Should I handle this?"
  ├─ Input: work item, agent capability descriptor, peer agents
  ├─ Output: boolean (eligible) + confidence score
  └─ If NOT eligible: Return work item to YAWL queue

PHASE 4: SKILL COMPOSITION (If Eligible)
  ├─ Load skill execution plan from YAML:
  │  └─ "ApproveOrder": [validate → check-inventory → calculate-cost → decide]
  ├─ Execute skills sequentially or in parallel:
  │  ├─ Skill 1: validate(data) → success or exception
  │  ├─ Skill 2: check-inventory(product-id) → stock level
  │  ├─ Skill 3: calculate-cost(weight, distance) → price
  │  └─ Skill 4: decide(data, stock, cost) → APPROVE/REJECT
  ├─ Monitor execution:
  │  ├─ Circuit breaker: Skip skill if too many failures
  │  ├─ Retry: Exponential backoff (1s, 2s, 4s, 8s max)
  │  └─ Timeout: Abort skill if exceeds SLA
  └─ Collect outputs in decision context

PHASE 5: OUTPUT GENERATION (Decision → String)
  ├─ Run OutputGenerator:
  │  ├─ Programmatic: Format outputs as JSON/XML
  │  └─ AI-powered: Use Claude to generate natural language explanations
  ├─ Example output:
  │  └─ { "decision": "APPROVED", "reason": "Inventory available,
  │       cost within budget", "approver_id": "agent-shipper-01" }
  └─ Serialize to work item input variable

PHASE 6: COMPLETION (Interface B)
  ├─ POST /cases/{case}/workitems/{item}/complete
  ├─ Attach output data
  ├─ Mark work item as COMPLETE
  └─ Log: "Work item {name} completed by {agent}. Elapsed: {time}ms"

PHASE 7: FEEDBACK & RESILIENCE
  ├─ Log execution metrics:
  │  ├─ Skill execution time per step
  │  ├─ Success/failure rate by skill
  │  └─ Agent throughput (items/minute)
  ├─ Update circuit breaker state:
  │  ├─ Track failure ratio per skill
  │  ├─ Open circuit if failure_rate > threshold (10% default)
  │  └─ Half-open after timeout (30s default)
  ├─ Publish to observability (Prometheus, Datadog, etc.)
  └─ Continue to PHASE 2 (next polling cycle)
```

### Built-in Agent Types

YAWL v6.0.0 provides eight specialized agent types, each with preloaded skills and domain expertise:

| Agent | Expertise | Typical Skills | Use Cases |
|-------|-----------|---|---|
| **yawl-engineer** | Engine development | Code generation, testing, compilation | Implement workflow features, optimization |
| **yawl-validator** | Spec/code validation | Schema validation, standards checking, coverage analysis | Validate XSD, enforce HYPER_STANDARDS |
| **yawl-architect** | System design | Interface contracts, ADRs, topology design | Design new services, document decisions |
| **yawl-integrator** | External connectivity | MCP endpoints, A2A protocol, webhook handling | Integrate external systems, build APIs |
| **yawl-reviewer** | Code review | Style enforcement, security scanning, test review | Review PRs, ensure quality gates |
| **yawl-tester** | Quality assurance | Test generation, coverage analysis, flakiness detection | Write integration tests, verify edge cases |
| **yawl-prod-validator** | Production readiness | Deployment validation, load testing, SLA enforcement | Verify production readiness, gate deployment |
| **yawl-perf-benchmarker** | Performance analysis | Profiling, throughput measurement, optimization | Benchmark critical paths, identify bottlenecks |

**Capability matrix** (what each agent can do):

```
                        Code  Schema  Spec   Review  Test   Perf  Devops Security
yawl-engineer            ✓✓    ✓      ✓      ✓      ✓
yawl-validator           ✓     ✓✓     ✓✓            ✓      ✓              ✓
yawl-architect           ✓     ✓      ✓      ✓             ✓      ✓       ✓
yawl-integrator          ✓     ✓      ✓             ✓      ✓      ✓       ✓✓
yawl-reviewer            ✓     ✓      ✓      ✓✓     ✓      ✓             ✓
yawl-tester              ✓     ✓      ✓      ✓      ✓✓     ✓             ✓
yawl-prod-validator      ✓     ✓      ✓      ✓      ✓      ✓✓    ✓✓      ✓
yawl-perf-benchmarker    ✓           ✓             ✓      ✓✓    ✓
```

### Custom Agent Development

You can create domain-specific agents by:

1. **Define capabilities**: What types of work items can this agent handle?
   ```yaml
   # my-shipper-agent.yaml
   name: ShipperAgent
   capabilities:
     - name: ProcessOrder
       description: "Approve and route customer orders"
     - name: ValidateInventory
       description: "Check warehouse stock levels"
   ```

2. **Implement strategies**: Override default decision logic
   ```java
   public class ShippingEligibilityReasoner implements EligibilityReasoner {
       @Override
       public boolean isEligible(WorkItemRecord wir, AgentCapability cap,
                                 List<AgentInfo> peers) {
           // Custom logic: "I'm only eligible if I'm in the same region"
           String orderRegion = wir.getInputData().get("region");
           String myRegion = config.get("agent.region");
           return orderRegion.equals(myRegion);
       }
   }
   ```

3. **Compose skills**: Chain capabilities together
   ```yaml
   executionPlans:
     ProcessOrder:
       steps:
         - skill: ValidateOrder
           input: workitem.data
         - skill: CheckInventory
           input: previous.productIds
         - skill: CalculateShipping
           input: previous.approved_items
         - skill: DecideApproval
           input: previous
   ```

4. **Register in marketplace**: Publish to Maven Central, Artifactory, or local registry

### Agent Context and Isolation

Each agent maintains **isolated execution context** to prevent cross-contamination:

**Per-agent context**:
- **Configuration**: YAML file with identity, YAWL endpoint, skill list, timeouts
- **State**: Heartbeat timestamp, circuit breaker status per skill, peer registry cache
- **Credentials**: OAuth tokens, API keys (stored in agent-specific vault)
- **Metrics**: Execution time histograms, success/failure counters, latency percentiles

**Isolation mechanisms**:
1. **Process boundaries**: Each agent runs in separate JVM (or container)
2. **Network isolation**: Private subnets, firewall rules, no peer-to-peer direct access
3. **Skill sandboxing**: Java security manager limits classpath, network, file access
4. **Circuit breaker per skill**: Failed skill doesn't affect other agents' access to that skill
5. **Timeout isolation**: Long-running skill doesn't block other agents (async execution)

**Context inheritance in Teams** (see Section 5):
- When multiple agents collaborate in a Team, context is **shared asynchronously** via message passing
- No direct memory sharing; all context propagation through JSON messages
- Lead agent consolidates context from all teammates before final GODSPEED phase

---

## Skills Framework

### Skill Interface and Contract

A **skill** implements a standardized contract:

```java
/**
 * Core Skill interface - all skills implement this contract.
 * Skills are versioned, metered, and composable.
 */
public interface Skill {
    /**
     * Execute this skill with input parameters.
     * @param input Map of parameter name → value
     * @return SkillResult with success status and output data
     * @throws SkillException on validation failure, timeout, or fault
     */
    SkillResult execute(Map<String, Object> input) throws SkillException;

    /**
     * Metadata about this skill for discovery and composition.
     */
    SkillMetadata metadata();

    /**
     * Circuit breaker for resilience.
     */
    CircuitBreaker circuitBreaker();
}

public record SkillMetadata(
    String name,                           // e.g. "ValidateInvoice"
    String version,                        // e.g. "2.1.0" (SemVer)
    String description,
    List<SkillParameter> parameters,       // Input schema
    SkillParameter returns,                // Output schema
    long timeoutMillis,                    // Max execution time
    double costPerInvocation,              // Metering: $ per call
    List<String> requiredSkills,           // Dependencies
    String provider,                       // "community", "enterprise", "custom"
    boolean isAsync                        // Can run in background?
) {}

public record SkillParameter(
    String name,
    String type,                           // "string", "number", "object", etc.
    String description,
    boolean required,
    Object defaultValue
) {}

public record SkillResult(
    boolean success,
    Map<String, Object> output,            // Skill's computed values
    long executionTimeMillis,
    SkillException error                   // null if success=true
) {}
```

**Example skill implementation**:

```java
public class ValidateInvoiceSkill implements Skill {
    private final SkillMetadata metadata = new SkillMetadata(
        "ValidateInvoice",
        "2.1.0",
        "Validate invoice against business rules",
        List.of(
            new SkillParameter("invoiceData", "object", "Invoice JSON", true, null),
            new SkillParameter("maxAmount", "number", "Max allowed invoice amount", false, 10000.0)
        ),
        new SkillParameter("validation", "object",
            "{ isValid: boolean, violations: string[] }", true, null),
        5000,  // 5 second timeout
        0.01,  // 1 cent per invocation
        List.of(),  // No dependencies
        "community",
        false   // Synchronous execution
    );

    @Override
    public SkillResult execute(Map<String, Object> input) throws SkillException {
        Map<String, Object> invoice = (Map<String, Object>) input.get("invoiceData");
        double maxAmount = ((Number) input.getOrDefault("maxAmount", 10000.0)).doubleValue();

        List<String> violations = new ArrayList<>();
        double amount = ((Number) invoice.get("amount")).doubleValue();

        if (amount > maxAmount) {
            violations.add("Amount exceeds maximum: " + amount);
        }
        if (!invoice.containsKey("invoiceId") ||
            ((String) invoice.get("invoiceId")).isEmpty()) {
            violations.add("Missing required field: invoiceId");
        }

        return new SkillResult(
            violations.isEmpty(),
            Map.of("isValid", violations.isEmpty(), "violations", violations),
            System.currentTimeMillis(),  // Elapsed time
            violations.isEmpty() ? null : new SkillException("Validation failed")
        );
    }

    @Override
    public SkillMetadata metadata() {
        return metadata;
    }

    @Override
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker("ValidateInvoice",
            failureThreshold = 10,    // Open after 10 failures
            timeout = 30000);         // Half-open after 30 seconds
    }
}
```

### Skill Discovery and Registration

Skills are discovered through a **multi-layer registry**:

```
┌─────────────────────────────────────────────────────────────┐
│                    SKILL DISCOVERY CHAIN                     │
└─────────────────────────────────────────────────────────────┘

Layer 1: Local Cache (~/.yawl-skills/)
  ├─ registry.json (local skills, updated hourly)
  ├─ ValidateInvoice:2.1.0 (installed skills)
  └─ Hit rate: 95%+ for cached skills

Layer 2: Organization Registry (Internal Maven/Artifactory)
  ├─ REST API: GET /api/v1/skills?q=validate&version=2.1.0
  ├─ Response: { name, version, jar_url, cost, sla }
  └─ TTL: 1 hour (watermark protocol prevents thrashing)

Layer 3: Remote Registry (Maven Central, Crates, PyPI)
  ├─ Maven Central: org.yawlfoundation:yawl-skills-community
  ├─ Fetch: Skill JAR + metadata.json
  └─ Verify: SHA-256 checksum, GPG signature

Layer 4: A2A Protocol (Peer Agents)
  ├─ GET https://peer-agent:8080/.well-known/agent.json
  ├─ Response: { name, skills: [ { name, version, url } ] }
  └─ Cached: 30 minutes (with health checks every 5 min)

FALLBACK: Throw SkillNotFoundException if not found
```

**Registry lookup algorithm**:

```java
public Skill findSkill(String name, String versionRange) throws SkillNotFoundException {
    // 1. Check local cache first (fastest)
    Skill cached = localRegistry.get(name, versionRange);
    if (cached != null && !cached.isStale()) {
        return cached;
    }

    // 2. Query organization registry (internal Maven)
    SkillArtifact artifact = orgRegistry.query(name, versionRange);
    if (artifact != null) {
        Skill skill = loadJar(artifact.url());
        localRegistry.cache(skill);
        return skill;
    }

    // 3. Query remote registries in parallel (Maven, Crates, etc.)
    List<Skill> remoteResults = remoteRegistries.parallel()
        .map(registry -> registry.query(name, versionRange))
        .filter(Objects::nonNull)
        .toList();
    if (!remoteResults.isEmpty()) {
        Skill skill = remoteResults.get(0);  // First match wins
        localRegistry.cache(skill);
        return skill;
    }

    // 4. Ask peer agents via A2A
    for (AgentInfo peer : peerAgents) {
        Optional<Skill> peerSkill = peer.querySkill(name, versionRange);
        if (peerSkill.isPresent()) {
            return peerSkill.get();
        }
    }

    throw new SkillNotFoundException(
        "Skill %s:%s not found in any registry".formatted(name, versionRange)
    );
}
```

### Skill Composition Patterns

**Pattern 1: Linear Pipeline** (sequential execution)

```yaml
# Typical: Validate → Enrich → Classify → Decide

executionPlan:
  ProcessClaim:
    strategy: sequential
    steps:
      - id: validate
        skill: ValidateClaimData
        input:
          claimData: "{{ workitem.input.claim }}"

      - id: enrich
        skill: EnrichWithHistorical
        input:
          claimId: "{{ validate.output.claim_id }}"
          customerId: "{{ workitem.input.customer_id }}"

      - id: classify
        skill: ClassifyClaimType
        input:
          claimText: "{{ enrich.output.claim_text }}"
          claimHistory: "{{ enrich.output.history }}"

      - id: decide
        skill: DecideApprovalAI
        input:
          claimType: "{{ classify.output.type }}"
          amount: "{{ validate.output.amount }}"
          history: "{{ enrich.output.history }}"
```

**Execution flow**: Validate → (wait) → Enrich → (wait) → Classify → (wait) → Decide
**Latency**: Sum of all step durations
**Failure mode**: Stop at first failure, return error

**Pattern 2: Parallel Branches** (fan-out, fan-in)

```yaml
executionPlan:
  ProcessShipment:
    strategy: parallel
    steps:
      - id: validate_address
        skill: ValidateAddress
        input:
          address: "{{ workitem.input.delivery_address }}"

      - id: check_inventory
        skill: CheckInventory
        input:
          items: "{{ workitem.input.items }}"

      - id: calculate_cost
        skill: CalculateShippingCost
        input:
          weight: "{{ workitem.input.weight }}"
          origin: "{{ workitem.input.origin }}"
          destination: "{{ workitem.input.destination }}"

    # All three steps run in parallel
    # Wait for all to complete (or timeout at 30 seconds)
    timeout: 30000
    failureMode: "failFast"  # Or "collectAll"

    - id: consolidate
      skill: ConsolidateDecision
      input:
        address_valid: "{{ validate_address.output.valid }}"
        inventory_status: "{{ check_inventory.output.status }}"
        cost: "{{ calculate_cost.output.total_cost }}"
      # This step depends on all parallel steps completing
      dependsOn: [validate_address, check_inventory, calculate_cost]
```

**Execution flow**:
```
Validate ┐
Inventory┤→ (wait all) → Consolidate
Cost     ┘
```
**Latency**: Max of parallel steps (not sum)
**Failure mode**: Configurable (fail-fast or collect all failures)

**Pattern 3: Conditional Branching** (decision tree)

```yaml
executionPlan:
  ApproveOrder:
    strategy: conditional
    steps:
      - id: validate
        skill: ValidateOrder
        input: "{{ workitem.input }}"

      # Conditional branch based on order amount
      - id: check_amount
        condition: "{{ validate.output.amount > 5000 }}"
        branches:
          high_value:
            steps:
              - id: hl_approval
                skill: RequireManagerApproval
              - id: hl_document
                skill: CreateAuditLog

          standard:
            steps:
              - id: std_approval
                skill: AutoApprove

      - id: notify
        skill: SendNotification
        input:
          decision: "{{ check_amount.output.decision }}"
```

**Execution flow**: Validate → (branch based on amount) → Approval → Notify
**Latency**: Validate + chosen branch (not all branches)
**Failure mode**: If branch fails, no alternative

### Skill Versioning and Compatibility

Skills follow **semantic versioning**: `MAJOR.MINOR.PATCH`

```
ValidateInvoice:2.1.0
                │││
                ││└─ PATCH (2.1.0 → 2.1.1): Bug fixes, internal optimization
                │└── MINOR (2.0.0 → 2.1.0): New optional parameters, expanded output
                └─── MAJOR (1.x.x → 2.0.0): Breaking changes to input/output

Breaking changes trigger MAJOR version bump:
  ✗ Changing parameter type: (amount: string) → (amount: number)
  ✗ Removing optional parameter without default
  ✗ Changing output schema (removing fields)
  ✓ Adding optional input parameter (with default)
  ✓ Adding optional output field
  ✓ Optimizing internal algorithm (no contract change)
```

**Version selection in YAML** (flexible ranges):

```yaml
executionPlan:
  MyPlan:
    steps:
      - skill: ValidateInvoice:2.1.0        # Exact: exactly this version
      - skill: ValidateInvoice:2.1.x        # Patch-compatible: any 2.1.z
      - skill: ValidateInvoice:2.x.x        # Minor-compatible: any 2.y.z
      - skill: ValidateInvoice:>=2.0.0      # Any version 2.0.0 or higher
      - skill: ValidateInvoice:2.0.0||2.1.0 # Either 2.0.0 or 2.1.0
```

**Backward compatibility guarantee**:
- Skills with same MAJOR version are guaranteed compatible
- If you upgrade ValidateInvoice 2.0.0 → 2.1.0, execution plans don't change
- Skill author promises: No breaking input/output changes within MAJOR version

---

## Marketplace Patterns

### Community Skills

**What**: Open-source skills maintained by YAWL community and published to Maven Central

**Examples**:
- `ValidateInvoice`: Check invoice against standard business rules
- `FormatJSON`: Pretty-print JSON, validate structure
- `SendEmail`: Send notifications via SMTP
- `ParseCSV`: Load CSV files, detect schemas
- `CalculateChecksum`: MD5/SHA-256 hashing for audit

**Characteristics**:
- Free to use (open-source license, typically Apache 2.0 or MIT)
- No SLA (best effort)
- Community-vetted (code review by YAWL maintainers)
- Fast updates (new features proposed via GitHub issues, merged in 1-2 weeks)
- GitHub repo: `yawlfoundation/yawl-skills-community`

**Publishing a community skill**:

```bash
# 1. Create skill in YAWL repo
src/org/yawlfoundation/yawl/skills/ValidateInvoice.java

# 2. Add unit tests (80%+ coverage required)
test/.../ValidateInvoiceTest.java

# 3. Document in registry
skills-registry.json: { "name": "ValidateInvoice", "provider": "community" }

# 4. Build and release to Maven Central via GitHub Actions
bash scripts/release-skill.sh ValidateInvoice 2.1.0
  → org.yawlfoundation:yawl-skills-community:2.1.0
```

### Enterprise Skills

**What**: Licensed skills with SLA guarantees, maintained by Ashok Paul or YAWL consulting partners

**Examples**:
- `ComplianceCheckFINRA`: Regulatory compliance validation for financial workflows
- `PII_Masker`: PII detection and redaction (GDPR-compliant)
- `AuditLogger`: Write immutable audit logs to blockchain/S3
- `DynamicPricing`: ML-based price optimization using historical data
- `AdvancedReasoning`: Claude 3 Opus integration with custom prompt engineering

**Characteristics**:
- Licensed (paid per invocation or annual subscription)
- SLA-backed (99.9% uptime, <100ms latency)
- Enterprise security (TLS 1.3, OAuth, API key rotation)
- Dedicated support (email, Slack, phone)
- Audit trail (logging, monitoring, compliance reporting)
- Closed source (proprietary algorithms, vendor lock-in)

**Pricing model**:

```yaml
enterprise_skills:
  ComplianceCheckFINRA:
    base_cost: $0.05 per invocation
    volume_discounts:
      10k/month: 10% off ($0.045)
      100k/month: 25% off ($0.0375)
      1M/month: 40% off ($0.03)
    sla:
      uptime: 99.9%
      latency_p99: 100ms
      availability: 24/7/365

  PII_Masker:
    annual_license: $10,000
    includes: 1M invocations/month
    overage: $0.01 per additional invocation
```

**Accessing enterprise skills**:

```yaml
# 1. Purchase license via https://marketplace.yawlfoundation.org
# 2. Get license key: yawl-enterprise-2025-abc123def456

# 3. Configure in agent YAML
agent:
  name: ComplianceAgent
  license_key: yawl-enterprise-2025-abc123def456
  skills:
    - name: ComplianceCheckFINRA
      version: 1.0.0
      provider: enterprise
      endpoint: https://enterprise-skills-api.yawlfoundation.org
```

### Custom Skill Development

**When to build custom skills**:
- Domain-specific business logic (market rates, internal pricing rules)
- Integration with legacy systems (old databases, COBOL services)
- Proprietary algorithms (ML models, trade secrets)
- Internal-only capabilities (not for external marketplace)

**Development workflow**:

```
┌─────────────────────────────────────────────────────────────┐
│                  CUSTOM SKILL DEVELOPMENT                    │
└─────────────────────────────────────────────────────────────┘

Step 1: Bootstrap skill project
  $ mvn archetype:generate \
    -DarchetypeGroupId=org.yawlfoundation \
    -DarchetypeArtifactId=yawl-skill-archetype \
    -DartifactId=my-domain-skill

Step 2: Implement Skill interface
  src/main/java/.../MyDomainSkill.java
  ├─ execute(input) → SkillResult
  ├─ metadata() → SkillMetadata
  └─ circuitBreaker() → CircuitBreaker

Step 3: Write tests (80%+ coverage)
  test/.../MyDomainSkillTest.java
  ├─ Happy path: Valid input → expected output
  ├─ Error path: Invalid input → SkillException
  ├─ Timeout: Skill execution times out gracefully
  └─ Circuit breaker: Opens after N failures

Step 4: Document
  README.md with:
  ├─ What the skill does
  ├─ Input/output schema examples
  ├─ Dependencies (other skills required)
  ├─ Cost per invocation
  └─ Known limitations

Step 5: Build and register
  mvn clean package

  # Option A: Publish to internal Artifactory
  mvn deploy \
    -DrepositoryId=yawl-internal \
    -Durl=https://artifactory.example.com/yawl-skills

  # Option B: Install locally
  mvn install
  # ~/.yawl-skills/registry.json: Add entry for MyDomainSkill:1.0.0

Step 6: Test in agent
  # Reference in execution plan
  executionPlan:
    ProcessOrder:
      - skill: MyDomainSkill:1.0.0
        input: "{{ workitem.input }}"
```

**Example custom skill** (legacy system integration):

```java
/**
 * Skill to integrate with legacy AS/400 system for pricing.
 * Converts modern JSON input to COBOL-compatible fixed-width format,
 * sends via MQ Series, parses response.
 */
public class LegacyPricingSkill implements Skill {
    private final MQSeriesClient mqClient;
    private final CobolTranslator translator;

    public LegacyPricingSkill() {
        this.mqClient = new MQSeriesClient("localhost:1414/AS400.PRICING");
        this.translator = new CobolTranslator();
    }

    @Override
    public SkillResult execute(Map<String, Object> input) throws SkillException {
        try {
            // 1. Convert JSON to fixed-width COBOL format
            String productId = (String) input.get("product_id");
            int quantity = ((Number) input.get("quantity")).intValue();

            String cobolRequest = translator.toCobol(
                new PricingRequest(productId, quantity, LocalDate.now())
            );

            // 2. Send to legacy system via MQ
            String cobolResponse = mqClient.sendSync(cobolRequest,
                timeout = 5000  // 5 second timeout
            );

            // 3. Parse COBOL response back to JSON
            PricingResponse response = translator.fromCobol(cobolResponse);

            return new SkillResult(
                true,
                Map.of(
                    "unit_price", response.unitPrice(),
                    "discount", response.discount(),
                    "total", response.total()
                ),
                System.currentTimeMillis(),
                null
            );
        } catch (MQException | TimeoutException e) {
            return new SkillResult(
                false,
                Map.of(),
                System.currentTimeMillis(),
                new SkillException("Legacy system unavailable: " + e.getMessage(), e)
            );
        }
    }

    @Override
    public SkillMetadata metadata() {
        return new SkillMetadata(
            "LegacyPricing",
            "1.0.0",
            "Pricing integration with AS/400 legacy system",
            List.of(
                new SkillParameter("product_id", "string", "Product SKU", true, null),
                new SkillParameter("quantity", "number", "Order quantity", true, null)
            ),
            new SkillParameter("pricing", "object",
                "{ unit_price: number, discount: number, total: number }",
                true, null),
            5000,  // 5 second timeout (legacy system can be slow)
            0.02,  // 2 cents per invocation
            List.of(),
            "custom",
            false
        );
    }

    @Override
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker("LegacyPricing",
            failureThreshold = 5,
            successThreshold = 3,
            timeout = 60000);  // Wait 60 seconds before trying again
    }
}
```

### Skill Isolation and Sandboxing

Each skill runs in isolated context to prevent:
- **Information leakage**: Skill A's secrets don't leak to Skill B
- **DoS attacks**: Runaway skill doesn't consume all memory/CPU
- **Cross-contamination**: Exception in Skill A doesn't crash Skill B

**Isolation mechanisms**:

```
┌─────────────────────────────────────────────────────────────┐
│                   SKILL SANDBOX LAYERS                       │
└─────────────────────────────────────────────────────────────┘

Layer 1: Process/Container Isolation
  ├─ Each skill JAR runs in separate ClassLoader
  ├─ JVM SecurityManager restricts:
  │  ├─ Reflection (can't inspect other skills)
  │  ├─ File I/O (read/write only /tmp/skill-{id}/)
  │  ├─ Network (outbound only to skill's configured endpoints)
  │  └─ System properties (skill can't read YAWL secrets)
  └─ Memory limit: 512MB per skill process

Layer 2: Execution Context
  ├─ Thread pool per skill (1-4 threads max)
  ├─ Timeout enforcement: Thread.interrupt() if skill exceeds deadline
  ├─ No shared state between skill invocations
  └─ Fresh ClassLoader instance per skill version change

Layer 3: Data Isolation
  ├─ Input data: Copied (deep clone) before passing to skill
  ├─ Output data: Validated against schema before returning
  ├─ Secret injection: Only credentials in execution plan are passed
  └─ Audit logging: All input/output logged for compliance

Layer 4: Crash Recovery
  ├─ Skill process dies → Agent detects (heartbeat timeout)
  ├─ Circuit breaker opens (failFast on all future invocations)
  ├─ Alert: OpsTeam notified of persistent failures
  ├─ Fallback: If configured, use alternative skill
  └─ Recovery: Manual restart required for critical skills
```

**Example: Sandbox configuration**:

```yaml
skillSandbox:
  defaultMemoryLimit: 512M
  defaultThreadPoolSize: 2
  defaultTimeout: 10000  # 10 seconds

  skills:
    LegacyPricing:
      memoryLimit: 1G          # Legacy system needs more memory
      timeout: 30000           # Slower legacy system
      allowedOutbound:
        - "localhost:1414"     # MQ Series queue manager
        - "10.0.1.100:3306"    # MySQL database

    AdvancedReasoning:
      timeout: 120000          # Claude API calls can be slow
      allowedOutbound:
        - "api.anthropic.com"
      secrets:
        - ANTHROPIC_API_KEY    # Injected at runtime

    ValidateInvoice:
      memoryLimit: 128M        # Light-weight skill
      timeout: 1000            # Should complete quickly
```

---

## Integration with Teams (τ)

### How Autonomous Agents Coordinate in Teams

While individual agents work independently, **Teams enable coordinated multi-agent work** on complex problems. This section explains how agents fit into the Teams framework.

**Single agent** (μ, subagent pattern):
```
Lead Session
  └─ Subagent A: Isolated investigation
     (report-only, no inter-task messaging)
  └─ Subagent B: Isolated investigation
  └─ Subagent C: Isolated investigation
  [Lead consolidates findings]
```

**Team of agents** (τ, full collaboration):
```
Lead Session
  ├─ Teammate A: Engine investigation
  │  ├─ Share: "Thread dump shows X"
  │  └─ Receive: "My thread dump shows Y"
  ├─ Teammate B: Concurrency analysis
  │  ├─ Share: "Race condition in lock acquisition"
  │  └─ Receive: "State machine also has ordering issue"
  └─ Teammate C: Guard logic review
     ├─ Share: "If-then chain has gap"
     └─ Receive: "That gap is in race window identified by B"
  [Lead synthesizes all findings → single root cause]
```

### Message-Passing Patterns

When agents collaborate in a Team, they communicate **asynchronously via typed messages**:

```java
// Message format (FIFO per teammate)
record Message(
    int sequence,                    // 1, 2, 3, ... (for ordering)
    Instant timestamp,
    String from,                     // "lead" or "tm_1", "tm_2", ...
    String to,                       // Recipient team member
    MessageKind kind,                // TASK, STATUS, FINDING, REQUEST, ACK
    Map<String, Object> payload      // Structured data
) {}

// Message kinds in Teams
enum MessageKind {
    TASK,        // Lead assigns work to teammate
    STATUS,      // Teammate reports progress
    FINDING,     // Teammate shares intermediate discovery
    REQUEST,     // One teammate asks another for information
    ACK          // Confirmation that message was received
}
```

**Example message exchange** (Engine investigation team):

```
[LEAD → TM_A] Sequence 1 (TASK)
{
  "task": "Investigate state machine transitions",
  "work_item": "yawl-2025-deadlock",
  "deadline": "2026-02-28T15:00Z",
  "context": { "jvm_pid": 12345, "thread_dump_url": "..." }
}

[TM_A → LEAD] Sequence 1 (ACK)
{
  "ack_sequence": 1,
  "status": "received",
  "estimated_completion": "2026-02-28T14:30Z"
}

[TM_A → LEAD] Sequence 2 (FINDING)
{
  "finding": "State transitions are ordered correctly in YNetRunner.transitions()",
  "evidence": "Source inspection shows synchronized block prevents reordering"
}

[TM_A → TM_B] Sequence 3 (REQUEST)
{
  "question": "Do your thread dumps show any stuck locks in your race window?",
  "context": "I ruled out state machine ordering"
}

[TM_B → TM_A] Sequence 3 (ACK + FINDING)
{
  "ack_sequence": 3,
  "finding": "Yes, deadlock lock 0x7fff2000 held by thread-45 but thread-12 waiting"
}

[LEAD → TM_A, TM_B, TM_C] Sequence 4 (REQUEST)
{
  "question": "Synthesize your findings into single root cause",
  "deadline": "2026-02-28T15:05Z"
}
```

### Shared Context in Teams

In a Team, agents share context **asynchronously and explicitly**:

**What's NOT shared** (isolation principle):
- Agent state (circuit breaker status, cached registries)
- Process memory (skills don't interfere with each other)
- Secrets (API keys remain agent-specific)

**What IS shared** (via messages):
- **Task definition**: What problem are we solving?
- **Work items**: YAWL cases, enabled tasks, input data
- **Findings**: Intermediate discoveries ("I ruled out X", "I found Y")
- **API contracts**: "My schema change requires these output fields"
- **Decisions**: "We should retry with these parameters"

**Example shared context** (Schema + Implementation team):

```
Lead: "Add SLA tracking to workflow execution"

[LEAD → SCHEMA_OWNER] TASK
{
  "task": "Define SLA element in YAWL_Schema4.0.xsd",
  "requirements": [
    "deadline: ISO 8601 datetime",
    "escalation: enum(NONE, NOTIFY, FAIL)",
    "breach_action: string"
  ]
}

[SCHEMA_OWNER → LEAD] FINDING
{
  "finding": "SLA element defined",
  "schema_snippet": {
    "sla": {
      "deadline": "string (ISO 8601)",
      "escalation": "enum [NONE, NOTIFY, FAIL]",
      "breach_action": "string"
    }
  }
}

[ENGINE_DEV → SCHEMA_OWNER] REQUEST
{
  "question": "Should escalation be extensible beyond NONE/NOTIFY/FAIL?"
}

[SCHEMA_OWNER → ENGINE_DEV] FINDING
{
  "finding": "Made extensible",
  "updated_schema": "escalation: string (default NONE)",
  "valid_values": "NONE | NOTIFY | FAIL | CUSTOM"
}

[ENGINE_DEV → LEAD] STATUS
{
  "status": "in_progress",
  "implementation": "YNetRunner.evaluateSLA() method 80% complete"
}

[LEAD → ENGINE_DEV, TESTER] REQUEST
{
  "question": "What test fixtures do we need for SLA breach scenarios?"
}

[TESTER → LEAD] FINDING
{
  "finding": "Fixtures designed",
  "test_cases": [
    "sla_active → breach → notification sent",
    "sla_active → breach → action triggered",
    "multiple_sla_per_task → only first breach matters"
  ]
}
```

### Cost Implications (3-5×)

Teams introduce 3-5× cost multiplier vs. single agent, due to:

1. **Context window cost**: 3-4 agents × 200K tokens each = 600K-800K tokens
2. **Synchronization overhead**: Time waiting for teammate responses
3. **Consolidation phase**: Lead re-reads all findings + runs `dx.sh all`

**When ROI justifies cost**:

| Scenario | Single Agent | Team | ROI | Cost/Quality |
|----------|---|---|---|---|
| **Add feature** (1-2 hours) | 1h = $C | 3 agents × 45min = 3C | 2.67× faster? No | 0.33 (not worth it) |
| **Debug deadlock** (2-3 hours) | 2.5h = $C | 3 agents × 45min = 3C | 2-3× faster | 1.5-2× (borderline) |
| **Complex refactor** (4-6 hours) | 5h = $C | 3 agents × 1h + 20min consolidate = 3.3C | 2-2.5× faster | 1.0-1.5 (worth it) |
| **Architecture review** (6+ hours) | 6h = $C | 4 agents × 1.5h = 4C | 2-3× faster | 1.5× (worth it) |

**Cost formula**:
```
cost_single = T_total × $rate_per_hour

cost_team = Σ(T_per_agent × $rate) + consolidation_cost
         = N × (T_total / N + overhead) × $rate
         = (T_total + N × overhead) × $rate
         = T_total × $rate + N × overhead × $rate
         = cost_single + overhead_multiplier

overhead_multiplier ≈ 0.5 to 1.0 (context setup, synchronization)
Total_multiplier = 1 + overhead_multiplier ≈ 1.5 to 2.0 per agent
With N agents: cost_team ≈ N × cost_single (for N ≥ 2)
```

**When to use Teams**:
- ✓ Competing hypotheses (multiple independent investigations)
- ✓ Cross-layer changes (schema, engine, test, integration orthogonal)
- ✓ Code review by concern (security, perf, coverage separate reviewers)
- ✓ Research + implementation (concurrent, not blocking each other)

**When NOT to use Teams**:
- ✗ Single quantum task (single agent faster and cheaper)
- ✗ Sequential dependencies (agent B needs output from agent A)
- ✗ <30 min per agent (setup cost > task time)
- ✗ >5 agents (coordination overhead exceeds benefit)

---

## MCP/A2A Integration

### How Agents Connect to External Systems

Agents connect to external systems through two complementary protocols:

**MCP (Model Context Protocol)** - Agent as client to Claude LLM:
```
Agent
  → "I need to reason about this work item"
  → Claude (with MCP tools)
      → Can call: Jira API, Slack API, GitHub API
      → Returns: Findings, decisions, recommendations
```

**A2A (Agent-to-Agent Protocol)** - Peer agents discovering each other:
```
Agent A
  → "/.well-known/agent.json"
  → Discovers Agent B
  → Can ask: "Do you have a Pricing skill?"
  → Agent B: "Yes, Pricing:2.1.0"
```

### Claude Desktop Integration

Agents can expose their **skills as MCP tools** to Claude Desktop:

```json
{
  "name": "yawl-agent-shipper",
  "description": "Shipping operations autonomous agent",
  "type": "agent",
  "mcp_endpoint": "http://localhost:8080/mcp",
  "tools": [
    {
      "name": "ApproveOrder",
      "description": "Evaluate and approve customer order",
      "input_schema": {
        "type": "object",
        "properties": {
          "order_id": { "type": "string" },
          "amount": { "type": "number" }
        }
      }
    },
    {
      "name": "CheckInventory",
      "description": "Query warehouse stock levels",
      "input_schema": {
        "type": "object",
        "properties": {
          "product_id": { "type": "string" }
        }
      }
    }
  ]
}
```

**Usage in Claude Desktop**:

```
User: "Can you check if we have inventory for product SKU-12345 and
       approve the order if stock > 100 units?"

Claude (using MCP):
  1. Call CheckInventory(product_id: "SKU-12345")
  2. Returns: { stock: 250 }
  3. Call ApproveOrder(order_id: "ORDER-789", amount: 5000)
  4. Returns: { approved: true, approver: "agent-shipper-01" }
  5. Respond: "Order approved. Stock available (250 units)."
```

### Protocol Considerations

**MCP benefits**:
- ✓ Leverage Claude's reasoning for complex decisions
- ✓ Chain multiple tools together (one agent → multiple systems)
- ✓ Natural language interface (no code needed to invoke skills)
- ✗ Latency: Claude API calls add 1-3 seconds
- ✗ Cost: $0.003 per 1K tokens (adds up with high volume)

**A2A benefits**:
- ✓ Low latency: Direct peer-to-peer calls
- ✓ No external dependency: Doesn't require Claude
- ✓ High throughput: Agents can call each other 1000x/sec
- ✗ No reasoning: Just lookup ("do you have this skill?")
- ✗ Discovery overhead: Must query each peer agent

**When to use each**:
- Use **MCP** for: Complex decisions, one-off user requests, "reasoning about tradeoffs"
- Use **A2A** for: High-volume work item processing, "does anyone have a cheaper shipper?"

### Authentication Patterns

Agents authenticate to external systems using **managed credentials**:

```yaml
# Agent YAML configuration
agent:
  name: ShippingAgent
  credentials:
    # OAuth 2.0 for YAWL Engine
    yawl_oauth:
      client_id: "${YAWL_CLIENT_ID}"
      client_secret: "${YAWL_CLIENT_SECRET}"
      token_endpoint: "https://yawl.example.com/oauth/token"

    # API key for external service
    stripe:
      api_key: "${STRIPE_API_KEY}"
      version: "2024-11"

    # Certificate-based auth (mTLS)
    warehouse_system:
      cert_path: "/etc/yawl/certs/agent-shipper.pem"
      key_path: "/etc/yawl/certs/agent-shipper-key.pem"
      ca_cert: "/etc/yawl/certs/ca.pem"

    # Claude API (for MCP)
    anthropic:
      api_key: "${ANTHROPIC_API_KEY}"
      model: "claude-opus-4-6"
```

**Credential injection at runtime**:

```java
public class AuthenticatedSkill implements Skill {
    private final Map<String, String> credentials;

    // Credentials injected by agent, never in source code
    public AuthenticatedSkill(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    @Override
    public SkillResult execute(Map<String, Object> input) throws SkillException {
        // Use credentials from injected map
        String stripeKey = credentials.get("stripe.api_key");
        Stripe.api_key = stripeKey;

        // Make API call
        Charge charge = Charge.create(...);

        return new SkillResult(true, Map.of("charge_id", charge.getId()), ...);
    }
}
```

---

## Decision Tree: When to Use Agents

### Single Agent vs. Team

```
Does your task involve N ≥ 2 orthogonal work domains?
├─ NO (single domain)
│  └─ Use SINGLE AGENT
│     Cost: ~$C | Time: 1-3 hours
│     Example: "Implement YNetRunner optimization"
│
├─ YES (multiple domains) + teammates need to message/iterate?
│  └─ Use TEAM (τ)
│     Cost: ~3-5C | Time: 45 min wall-clock + consolidation
│     Example: "Add SLA tracking: schema + engine + test + integration"
│     See: teams-framework.md for detailed patterns
│
└─ YES (multiple domains) + work is independent?
   └─ Use SUBAGENTS (μ)
      Cost: ~2C | Time: max of parallel durations (not sum)
      Example: "Validate schema AND test coverage AND code review"
      No messaging between subagents (report-only)
```

### Autonomous vs. Interactive

```
Is the agent operating on live YAWL Engine work items?
├─ YES, autonomous processing
│  ├─ High-volume work items (>100/minute)?
│  │  └─ Use A2A protocol (direct peer-to-peer)
│  │     Low latency, high throughput, no Claude required
│  │
│  └─ Complex reasoning required?
│     └─ Use MCP + Claude integration
│        Claude decides: "Should I approve this order?"
│        +1-3 seconds latency, but better decision quality
│
└─ NO, driven by user requests
   ├─ Reasoning-heavy ("analyze competitive landscape")?
   │  └─ Use Claude directly (via Claude Desktop or API)
   │     No agent needed, Claude is the reasoner
   │
   └─ Work item execution ("approve order")?
      └─ Use agent + skill composition
         Agent handles polling + eligibility + skill orchestration
```

### Skill Composition Opportunities

```
Does your workflow need to chain multiple operations?
├─ NO (single operation)
│  └─ Call skill directly
│     AgentConfig: skills: [ValidateInvoice:2.1.0]
│
├─ YES, operations are independent
│  └─ Parallel skill composition
│     agentConfig:
│       executionPlan:
│         - parallel:
│           - ValidateInvoice
│           - CheckInventory
│           - CalculateCost
│
└─ YES, operations depend on each other
   └─ Sequential skill composition
      agentConfig:
        executionPlan:
          - step: Validate
          - step: Enrich (depends on Validate.output)
          - step: Classify (depends on Enrich.output)
          - step: Decide (depends on Classify.output)
```

---

## Real Examples

### YawlScout Agent (Intelligence Gathering)

**Purpose**: Autonomously fetch specs, dependencies, changelogs at SessionStart

**Skills composed**:
1. **FetchSpecification**: Download workflow spec from repository
2. **ParseXSD**: Extract schema version, elements, constraints
3. **FetchDependencies**: Get Maven dependency tree
4. **FetchChangelog**: Extract git history for changed files
5. **SummarizeFindings**: Generate markdown intelligence summary

**Execution plan**:

```yaml
agent:
  name: YawlScout
  description: "Autonomous intelligence gatherer for Claude sessions"

  capabilities:
    - name: GatherIntelligence
      description: "Fetch and summarize workflow context"

  executionPlan:
    GatherIntelligence:
      strategy: sequential

      steps:
        - id: fetch_spec
          skill: FetchSpecification:1.0.0
          input:
            repo_url: "https://github.com/user/yawl"
            file_path: "src/exampleSpecs/SampleWorkflow.ywl"

        - id: parse_schema
          skill: ParseXSD:1.0.0
          input:
            schema_url: "{{ fetch_spec.output.schema_url }}"

        - id: fetch_deps
          skill: FetchDependencies:1.0.0
          input:
            pom_url: "https://github.com/user/yawl/pom.xml"

        - id: fetch_changelog
          skill: FetchChangelog:1.0.0
          input:
            repo_url: "https://github.com/user/yawl"
            files: "{{ fetch_spec.output.modified_files }}"
            limit: 10  # Last 10 commits

        - id: summarize
          skill: SummarizeFindings:1.0.0
          input:
            spec: "{{ fetch_spec.output }}"
            schema: "{{ parse_schema.output }}"
            dependencies: "{{ fetch_deps.output }}"
            changelog: "{{ fetch_changelog.output }}"
```

**Output**: Markdown intelligence summary injected into session context

```markdown
# YAWL Session Intelligence (Generated by YawlScout)

## Spec Summary
- Workflow: SampleWorkflow
- Elements: 12 tasks, 3 OR-joins
- Last modified: 2026-02-28 (2 days ago)

## Dependencies
- yawl-engine: 6.0.0 (latest)
- jakarta-ee: 11
- java: 25

## Recent Changes
- Commit abc123: Add SLA tracking to workflow
- Commit def456: Optimize YNetRunner lock contention
- ...
```

### YawlJira Agent (Ticket Management)

**Purpose**: Autonomously manage JIRA tickets in response to workflow events

**Skills composed**:
1. **FetchTicket**: Get JIRA ticket details
2. **ClassifyIssue**: Categorize (bug, feature, task, chore)
3. **EstimateEffort**: Predict story points based on description
4. **AssignTicket**: Assign to appropriate team member
5. **UpdateStatus**: Transition ticket state

**Execution plan**:

```yaml
agent:
  name: YawlJira
  description: "Autonomous JIRA ticket lifecycle manager"

  executionPlan:
    ProcessNewTicket:
      strategy: sequential

      steps:
        - id: fetch
          skill: FetchTicket:1.0.0
          input:
            ticket_key: "{{ workitem.input.ticket_key }}"
            jira_url: "https://jira.example.com"

        - id: classify
          skill: ClassifyIssue:2.0.0
          input:
            title: "{{ fetch.output.summary }}"
            description: "{{ fetch.output.description }}"
            labels: "{{ fetch.output.labels }}"

        - id: estimate
          skill: EstimateEffort:1.5.0
          input:
            issue_type: "{{ classify.output.type }}"
            description: "{{ fetch.output.description }}"
            complexity_factors: "{{ classify.output.factors }}"

        - id: assign
          skill: AssignTicket:1.0.0
          input:
            issue_type: "{{ classify.output.type }}"
            story_points: "{{ estimate.output.points }}"
            team_expertise: "{{ classify.output.required_skills }}"
            available_team: "{{ config.team_availability }}"

        - id: update
          skill: UpdateStatus:1.0.0
          input:
            ticket_key: "{{ fetch.output.key }}"
            new_status: "IN_PROGRESS"
            assignee: "{{ assign.output.assigned_to }}"
            story_points: "{{ estimate.output.points }}"
```

**Output**: Ticket automatically classified, estimated, assigned, and moved to IN_PROGRESS

### Custom Domain-Specific Agent Example

**ClaimProcessingAgent**: Insurance claims workflow

```yaml
agent:
  name: ClaimProcessingAgent
  description: "Autonomous claim assessment and routing"

  capabilities:
    - name: ProcessClaim
      description: "Evaluate claim and decide approval"

    - name: RequestMoreInfo
      description: "Ask customer for additional information"

  executionPlan:
    ProcessClaim:
      strategy: sequential

      steps:
        - id: validate
          skill: ValidateClaimData:3.2.0
          input:
            claim: "{{ workitem.input.claim }}"
            schema_version: "insurance-claims:2024-01"

        - id: enrich
          skill: EnrichWithHistory:2.0.0
          input:
            customer_id: "{{ workitem.input.customer_id }}"
            claim_amount: "{{ validate.output.amount }}"

        - id: classify
          skill: ClassifyClaimType:1.5.0
          input:
            claim_type: "{{ validate.output.type }}"  # Auto, Home, Health
            amount: "{{ validate.output.amount }}"
            history: "{{ enrich.output.claim_history }}"

        - id: decide_ai
          skill: DecideApprovalAI:2.0.0
          # Uses Claude Opus for complex reasoning
          input:
            claim_type: "{{ classify.output.type }}"
            amount: "{{ validate.output.amount }}"
            customer_history: "{{ enrich.output.history }}"
            policy_coverage: "{{ enrich.output.coverage }}"
            recent_claims: "{{ enrich.output.recent }}"

        - id: notify
          skill: SendNotification:1.0.0
          input:
            customer_id: "{{ workitem.input.customer_id }}"
            decision: "{{ decide_ai.output.decision }}"  # APPROVED, DENIED, MORE_INFO
            reason: "{{ decide_ai.output.reasoning }}"
            appeal_url: "{{ config.appeal_endpoint }}"

    RequestMoreInfo:
      strategy: sequential
      steps:
        - id: draft_request
          skill: DraftInfoRequest:1.0.0
          input:
            claim_id: "{{ workitem.input.claim_id }}"
            missing_fields: "{{ classify.output.missing }}"

        - id: send_request
          skill: SendEmail:1.0.0
          input:
            to: "{{ enrich.output.customer_email }}"
            subject: "Additional Information Needed"
            body: "{{ draft_request.output.email_body }}"
            deadline: "{{ config.response_deadline }}"
```

**Workflow execution**:
```
1. Claim submitted to YAWL Engine
2. ClaimProcessingAgent polls for enabled "ProcessClaim" work item
3. Agent runs skill composition:
   - ValidateClaimData → Check format/schema ✓
   - EnrichWithHistory → Fetch customer history ✓
   - ClassifyClaimType → Auto, Home, Health? ✓
   - DecideApprovalAI → Claude reasons: APPROVED ✓
   - SendNotification → Notify customer ✓
4. Agent completes work item (YAWL moves to next task)
5. Customer receives decision notification

Total time: ~5 seconds (validation) + ~3 seconds (Claude API) = ~8 seconds
No human intervention required.
```

---

## Summary Table: Agent Patterns

| Pattern | Use Case | Agents | Skills | Latency | Cost | ROI |
|---------|----------|--------|--------|---------|------|-----|
| **Single agent** | Implement feature | 1 | 1-5 | minutes | $C | High (always worth it) |
| **Agent team** | Debug complex issue | 3 | shared | 45min wall-clock | 3C | 2-3× faster |
| **Subagents** | Parallel validation | 3 | independent | max(times) | 2C | 1.5× faster |
| **MCP + Agent** | AI-driven decision | 1 | many | +1-3sec | $C + Claude | Complex reasoning |
| **A2A peers** | High-volume routing | N | shared | <100ms | $C | Throughput |
| **Custom domain** | Specific workflow | 1 | domain | varies | $2-5C | Domain efficiency |

---

## Key Takeaways

1. **Autonomous agents** bring self-organization to YAWL workflows through skill composition and peer discovery
2. **Skills** are versioned, metered, composable units that agents chain together
3. **Marketplace** reduces development time through reusable community, enterprise, and custom skills
4. **Teams** enable multi-agent collaboration when competing hypotheses or cross-layer work require parallelism
5. **MCP/A2A** integration connects agents to external systems and enables reasoning via Claude
6. **Cost-benefit** of agents and teams must justify 3-5× context multiplier through 2-3× wall-clock speedup
7. **Isolation** and **sandboxing** prevent skill failures from cascading

---

## References

- [teams-framework.md](teams-framework.md) — Multi-agent collaboration patterns
- [autonomous-agents.md](autonomous-agents.md) — Agent implementation details
- [godspeed-methodology.md](godspeed-methodology.md) — Quality pipeline integrating agents
- [agent-coordination.md](agent-coordination.md) — Deeper coordination patterns
- YAWL Manual: https://yawlfoundation.github.io/
- MCP Spec: https://modelcontextprotocol.io/
- A2A Protocol: Draft specification in `.claude/integration/a2a-protocol.md`
