# Agents Marketplace — Quick Start Guide

**For**: Developers building agents, workflows that use agents, and integrations.
**Time**: 5-10 minutes to understand. 1-2 hours to implement first agent.

---

## What is the Agents Marketplace?

A registry of pre-trained autonomous agents that YAWL workflows can invoke to complete tasks.

**Example**: Expense approval workflow automatically approves expenses <$10K via `approval-agent`, escalates larger expenses.

```
YAWL Workflow (case execution)
    ↓
  Task: "Approve Expense"
    ↓
  Agent: approval-agent (invoked via A2A protocol)
    ↓
  Decision: { approved: true, confidence: 0.99, reason: "Under limit" }
    ↓
  Workflow continues (task complete)
```

---

## Core Concepts

### Agent Profile
A YAML file declaring what an agent can do.

```yaml
apiVersion: agents.marketplace/v1
kind: AgentProfile
metadata:
  id: approval-agent
  name: Approval Agent
  version: 1.0.0

spec:
  capabilities:
    - id: approve-expense
      skillId: yawl.skills.approval.approve
      params:
        amount: number
        requestor: string
      returns:
        approved: boolean
        confidence: number
```

**Location**: `.agents/agent-profiles/{agent-id}.yaml`

### Capability
A specific skill an agent can perform.

```yaml
capabilities:
  - id: approve-expense          # Unique ID
    skillId: yawl.skills.approval.approve  # References Skills Marketplace
    params:                       # Input parameters
      amount: number
      requestor: string
    returns:                      # Output parameters
      approved: boolean
    timeout: 10s                  # Max execution time
    maxRetries: 2                 # Circuit breaker
```

### Orchestration Template
A JSON DAG defining how agents work together.

```json
{
  "apiVersion": "orchestration/v1",
  "kind": "OrchestrationTemplate",
  "metadata": { "id": "approval-workflow" },
  "spec": {
    "pattern": "sequential",
    "agents": [
      { "id": "validator", "agentId": "validation-agent", ... },
      { "id": "approver", "agentId": "approval-agent", "dependsOn": ["validator"] },
      { "id": "notifier", "agentId": "notification-agent", "dependsOn": ["approver"] }
    ]
  }
}
```

**Patterns**: `sequential` (chain), `parallel` (concurrent), `conditional` (if/else).

---

## Task 1: Register a New Agent (15 min)

### Step 1: Create Agent Profile YAML

Create `.agents/agent-profiles/my-agent.yaml`:

```yaml
---
apiVersion: agents.marketplace/v1
kind: AgentProfile

metadata:
  id: my-agent
  name: My Agent
  version: 1.0.0
  author: my-team

spec:
  description: "Brief description of what agent does"

  capabilities:
    - id: my-capability
      skillId: yawl.skills.my.skill
      description: "What this capability does"
      params:
        param1:
          type: string
          description: "Input parameter"
          required: true
        param2:
          type: number
          required: false
      returns:
        result:
          type: boolean
        message:
          type: string
      timeout: 30s
      maxRetries: 2

  deployment:
    type: docker
    image: my-registry/my-agent:1.0.0
    port: 9001
    environment:
      - name: CONFIG_VAR
        value: "default"
    resources:
      memory: 512Mi
      cpu: 250m

  healthCheck:
    path: /health
    interval: 30s
    timeout: 5s

  metrics:
    successRate: 0.95
    avgLatency: 500
    uptime: 0.99
    costPerInvocation: 0.05
```

### Step 2: Register in Manifest

Edit `.agents/agents.yaml` and add entry:

```yaml
agents:
  - id: my-agent
    name: My Agent
    version: 1.0.0
    status: active
    author: my-team
    capabilities:
      - my-capability
    description: "Brief description"
    profile: agent-profiles/my-agent.yaml
```

### Step 3: Commit to Git

```bash
git add .agents/agent-profiles/my-agent.yaml
git add .agents/agents.yaml
git commit -m "Register my-agent"
git push origin main
```

### Step 4: Verify Registration

```bash
# List all agents
curl http://localhost:8080/agents

# Get agent profile
curl http://localhost:8080/agents/my-agent/profile

# Check agent health
curl http://localhost:8080/agents/my-agent/health
```

**Done!** Agent is now discoverable.

---

## Task 2: Discover Agents by Capability (5 min)

### Discover

```bash
# Find agents that can "approve"
curl -X POST "http://localhost:8080/agents/discover?capability=approve"

# Response:
[
  {
    "id": "approval-agent",
    "name": "Approval Agent",
    "successRate": 0.985,
    "avgLatency": 250,
    "costPerInvocation": 0.01
  }
]
```

### Use in Code

```java
DiscoveryService discovery = new DiscoveryService(registry, index);

List<AgentDiscoveryResult> agents = discovery.discoverByCapability("approve");
// agents = [approval-agent, smart-approval-agent, ... ]

// Pick top agent (best success rate)
AgentProfile bestAgent = registry.getAgent(agents.get(0).id());
```

---

## Task 3: Create Orchestration Template (20 min)

### Create Template JSON

Create `.agents/orchestration/my-workflow.json`:

```json
{
  "apiVersion": "orchestration/v1",
  "kind": "OrchestrationTemplate",
  "metadata": {
    "id": "my-workflow",
    "name": "My Workflow",
    "version": "1.0.0",
    "description": "Workflow orchestrating my agents"
  },
  "spec": {
    "pattern": "sequential",
    "agents": [
      {
        "id": "step1",
        "agentId": "my-agent",
        "capability": "my-capability",
        "timeout": "10s",
        "retries": 2,
        "dependsOn": [],
        "input": {
          "param1": "$.request.data"
        },
        "output": {
          "result": "$.result",
          "message": "$.message"
        }
      },
      {
        "id": "step2",
        "agentId": "another-agent",
        "capability": "another-capability",
        "timeout": "5s",
        "retries": 1,
        "dependsOn": ["step1"],
        "input": {
          "input_data": "$.step1.result"
        }
      }
    ],
    "errorHandling": {
      "strategy": "escalate-on-first-failure",
      "validationFailure": {
        "action": "escalate",
        "escalateToAgent": "escalation-agent"
      }
    }
  }
}
```

### Deploy Template

```bash
# Compile template to YAWL workflow
curl -X POST http://localhost:8080/orchestrate/compile \
  -H "Content-Type: application/json" \
  -d @my-workflow.json

# Response: YSpecification (YAWL workflow definition)
# Can now execute via YEngine
```

### Use in Code

```java
TemplateRepository templates = new FileTemplateRepository(".agents/orchestration");
OrchestrationTemplate template = templates.load("my-workflow");

TemplateCompiler compiler = new TemplateCompiler(registry);
YSpecification spec = compiler.compile(template);

// Deploy workflow
YEngine engine = new YEngine();
engine.loadSpecification(spec);
String caseId = engine.createCase(spec.getID());
engine.startCase(caseId);
```

---

## Task 4: Invoke Agent from Workflow (10 min)

### Option A: Manual A2A Invocation

```java
// In your workflow task handler
A2AClient agent = new A2AClient("approval-agent");

WorkflowRequest request = new WorkflowRequest()
    .put("amount", 5000)
    .put("requestor", "alice@company.com");

try {
    WorkflowResponse response = agent.invokeWithTimeout(
        request,
        Duration.ofSeconds(30)
    );

    if (response.isSuccess()) {
        boolean approved = response.getBoolean("approved");
        double confidence = response.getDouble("confidence");
        String reason = response.getString("reason");

        if (approved) {
            workItem.setData(response.getData());
            engine.completeWorkItem(workItem.getID(), response.getData());
        } else {
            engine.getCase(caseId).sendToQueue("escalation_queue");
        }
    } else {
        throw new WorkflowException("Agent failed: " + response.error());
    }
} catch (TimeoutException e) {
    // Agent timeout - escalate
    engine.getCase(caseId).sendToQueue("escalation_queue");
}
```

### Option B: Via Orchestration Template (Automatic)

When you deploy a template via `TemplateCompiler.compile()`, agent invocations are automatically wrapped:

```java
// Agent invocation is handled by compiled workflow
// No manual A2A code needed
```

---

## Task 5: Monitor Agent Metrics (10 min)

### View Metrics

```bash
# Get agent success rate, latency, uptime
curl http://localhost:8080/agents/approval-agent/metrics

# Response:
{
  "id": "approval-agent",
  "successRate": 0.985,
  "avgLatency": 250,
  "p50Latency": 180,
  "p99Latency": 450,
  "uptime": 0.9998,
  "invocationsThisMonth": 12450,
  "costPerInvocation": 0.01
}
```

### Track Metrics in Code

```java
AgentMetrics metrics = new AgentMetrics("approval-agent");

long start = System.currentTimeMillis();
try {
    WorkflowResponse res = agent.invoke(request);
    long duration = System.currentTimeMillis() - start;

    metrics.recordSuccess(duration, res.getDouble("confidence", 0.0));
} catch (Exception e) {
    metrics.recordFailure(e);
}

// View collected metrics
System.out.println("Success rate: " + metrics.getSuccessRate());
System.out.println("Avg latency: " + metrics.getAvgDuration() + "ms");
```

---

## Reference Agents (MVP)

| Agent | Capability | Skill | Use Case |
|-------|-----------|-------|----------|
| `approval-agent` | approve-expense | yawl.skills.approval.approve | Auto-approve <limit |
| `validation-agent` | validate-input | yawl.skills.validation.validate | Input validation |
| `po-agent` | create-po | yawl.skills.procurement.create_po | Purchase order creation |
| `expense-agent` | categorize-expense | yawl.skills.expense.categorize | Expense categorization |
| `scheduler-agent` | schedule-meeting | yawl.skills.calendar.schedule | Meeting scheduling |
| `monitor-agent` | monitor-case-health | yawl.skills.monitoring.monitor | Case health monitoring |

---

## Common Patterns

### Pattern 1: Auto-Approve with Fallback

```yaml
agents:
  - id: approver
    agentId: approval-agent
    capability: approve-expense
    timeout: 30s
    errorHandling:
      onTimeout: escalate
      onFailure: escalate
      escalateToAgent: escalation-agent
```

### Pattern 2: Validation → Approval Chain

```json
{
  "pattern": "sequential",
  "agents": [
    {
      "id": "validate",
      "agentId": "validation-agent",
      "dependsOn": []
    },
    {
      "id": "approve",
      "agentId": "approval-agent",
      "dependsOn": ["validate"],
      "input": {
        "amount": "$.validate.amount"
      }
    }
  ]
}
```

### Pattern 3: Parallel Checks

```json
{
  "pattern": "parallel",
  "agents": [
    {
      "id": "validate",
      "agentId": "validation-agent",
      "dependsOn": []
    },
    {
      "id": "check-compliance",
      "agentId": "compliance-agent",
      "dependsOn": []
    }
  ]
}
```

---

## Troubleshooting

### Agent Not Found

```
Error: Agent not found: my-agent

Solution:
1. Check .agents/agents.yaml has entry
2. Verify YAML is valid: yamllint .agents/agent-profiles/my-agent.yaml
3. Git push changes: git push origin main
4. Wait 30s for registry refresh
```

### Discovery Timeout (>100ms)

```
Error: Discovery query took 250ms (target <100ms)

Solution:
1. Check SPARQL index is cached (not running fresh query)
2. Profile CapabilityIndex.findAgents() to identify slow SPARQL
3. Consider pre-building inverted index for hot skills
```

### Agent Health Check Failing

```
Error: Agent offline: health check failed

Solution:
1. Check agent container is running: docker ps | grep my-agent
2. Check health endpoint: curl http://localhost:9001/health
3. View agent logs: docker logs my-agent
4. Verify port mapping: docker inspect my-agent
```

### Template Compilation Error

```
Error: Circular dependency detected: step1 → step2 → step1

Solution:
1. Review template JSON: check dependsOn fields
2. Ensure agents form DAG (no cycles)
3. Use TemplateValidator to find issues
```

---

## Next Steps

1. **Register first agent** → Follow Task 1
2. **Deploy reference workflow** → Use approval-workflow.json as example
3. **Test agent discovery** → Try `curl .../agents/discover?capability=approve`
4. **Build custom workflow** → Create your own orchestration template
5. **Monitor metrics** → Track agent success rate + latency

---

## Documentation

- **Agent Profiles**: `.agents/agent-profiles/` (YAML examples)
- **Templates**: `.agents/orchestration/` (JSON examples)
- **Full Design**: `.claude/AGENTS-MARKETPLACE-MVP-DESIGN.md`
- **API Reference**: Generated OpenAPI/Swagger (coming Week 2)

---

## Support

For questions or issues:
- **Agent registration**: See `.agents/agent-profiles/approval-agent.yaml` for full example
- **Template syntax**: See `.agents/orchestration/approval-workflow.json` for full example
- **Integration help**: Contact Integration Team (integration@acme.com)

**Status**: MVP ready for development. Go build agents!
