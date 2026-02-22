# YAWL Agents Marketplace — Repository

**Agent registry for autonomous agents** that YAWL workflows can discover and invoke.

## Quick Start

```bash
# Discover agents by capability
curl -X POST "http://localhost:8080/agents/discover?capability=approve"

# Get agent profile
curl "http://localhost:8080/agents/approval-agent/profile"

# Deploy orchestration template
curl -X POST "http://localhost:8080/orchestrate/deploy" \
  -d @orchestration/approval-workflow.json
```

## Directory Structure

```
.agents/
├─ agents.yaml                    # Registry manifest (6 agents, 3 templates)
├─ agent-profiles/                # Individual agent definitions (YAML)
│  ├─ approval-agent.yaml         # Auto-approve <limit
│  ├─ validation-agent.yaml       # Input validation
│  ├─ po-agent.yaml               # Purchase order creation
│  ├─ expense-agent.yaml          # Expense categorization
│  ├─ scheduler-agent.yaml        # Calendar scheduling
│  └─ monitor-agent.yaml          # Case health monitoring
└─ orchestration/                 # Workflow templates (JSON)
   ├─ approval-workflow.json      # Sequential: validate → approve → notify
   ├─ parallel-workflow.json      # Parallel: validate + compliance
   └─ conditional-workflow.json   # Conditional: if amount > limit → escalate
```

## Files

### Registry

**`agents.yaml`** — Master registry
- Index of all agents + versions
- Index of all templates
- Status tracking (active, degraded, offline)

### Agent Profiles (YAML)

Each agent declares:
- **Metadata**: id, name, version, author
- **Capabilities**: What skills agent can invoke (with params)
- **Deployment**: Docker image, port, resources
- **Health Check**: HTTP endpoint + interval
- **Metrics**: Success rate, latency, uptime, cost

### Orchestration Templates (JSON)

Each template defines:
- **Pattern**: sequential, parallel, or conditional
- **Agents**: Ordered list with dependencies
- **Data Bindings**: JSONPath expressions for data flow
- **Error Handling**: Timeout, retry, escalation rules
- **Monitoring**: Metrics + alerts

## Adding a New Agent

### 1. Create Profile

```bash
cp agent-profiles/approval-agent.yaml agent-profiles/my-agent.yaml
# Edit: replace id, name, capabilities, deployment
```

### 2. Add to Manifest

```bash
# Edit agents.yaml, add:
- id: my-agent
  name: My Agent
  version: 1.0.0
  capabilities: [my-capability]
  profile: agent-profiles/my-agent.yaml
```

### 3. Commit

```bash
git add agent-profiles/my-agent.yaml agents.yaml
git commit -m "Register my-agent"
git push origin main
```

### 4. Verify

```bash
curl "http://localhost:8080/agents/my-agent/profile"
curl "http://localhost:8080/agents/discover?capability=my-capability"
```

## Agent Profile Schema

See `agent-profiles/approval-agent.yaml` for complete example.

**Key Fields**:

```yaml
apiVersion: agents.marketplace/v1
kind: AgentProfile

metadata:
  id: agent-id
  name: Agent Name
  version: 1.0.0
  author: team-name

spec:
  description: "What this agent does"

  # What agent can invoke
  capabilities:
    - id: capability-id
      skillId: yawl.skills.category.action  # Reference to Skills Marketplace
      params:
        param_name:
          type: number|string|boolean|array|object
          required: true|false
      returns:
        result_name:
          type: ...
      timeout: 30s
      maxRetries: 2

  # Docker deployment
  deployment:
    type: docker
    image: registry.com/agent:1.0.0
    port: 9001
    environment:
      - name: CONFIG_VAR
        value: "value"
    resources:
      memory: 512Mi
      cpu: 250m

  # Health check
  healthCheck:
    path: /health
    interval: 30s
    timeout: 5s

  # Performance metrics
  metrics:
    successRate: 0.95
    avgLatency: 250
    uptime: 0.99
    costPerInvocation: 0.01
```

## Template Schema

See `orchestration/approval-workflow.json` for complete example.

**Key Fields**:

```json
{
  "apiVersion": "orchestration/v1",
  "kind": "OrchestrationTemplate",
  "metadata": {
    "id": "template-id",
    "name": "Template Name"
  },
  "spec": {
    "pattern": "sequential|parallel|conditional",
    "agents": [
      {
        "id": "step1",
        "agentId": "approval-agent",
        "capability": "approve-expense",
        "timeout": "10s",
        "retries": 2,
        "dependsOn": [],
        "input": {
          "param": "$.path.to.value"
        }
      }
    ]
  }
}
```

## Documentation

For complete design and implementation guide, see:

- **`AGENTS-MARKETPLACE-README.md`** — Overview and navigation
- **`AGENTS-MARKETPLACE-QUICK-START.md`** — 5-minute guide (5 tasks)
- **`AGENTS-MARKETPLACE-MVP-DESIGN.md`** — Full technical spec (12K words)
- **`AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md`** — Week-by-week tasks
- **`AGENTS-MARKETPLACE-API-SPEC.md`** — REST API reference

All documents in `.claude/` directory.

## REST API Endpoints

```
# Discover agents
POST /agents/discover?capability=approve

# Get profile
GET /agents/{agentId}/profile

# Check health
GET /agents/{agentId}/health

# Get metrics
GET /agents/{agentId}/metrics

# Deploy template
POST /orchestrate/deploy

# Compile template
POST /orchestrate/compile

# Get template status
GET /orchestrate/{templateId}

# List all templates
GET /orchestrate
```

See `AGENTS-MARKETPLACE-API-SPEC.md` for full request/response examples.

## Integration

Agents are invoked via YAWL A2A Protocol:

```java
A2AClient agent = new A2AClient("approval-agent");
WorkflowResponse res = agent.invokeWithTimeout(request, Duration.ofSeconds(30));
boolean approved = res.getBoolean("approved");
```

See `AGENT-INTEGRATION.md` for full patterns.

## Status

This is a **Git-backed registry**. The source of truth is:
- `.agents/agents.yaml` (manifest)
- `.agents/agent-profiles/*.yaml` (profiles)
- `.agents/orchestration/*.json` (templates)

The registry auto-syncs every 30 seconds from Git.

## Support

- **Quick questions**: See `AGENTS-MARKETPLACE-QUICK-START.md`
- **API questions**: See `AGENTS-MARKETPLACE-API-SPEC.md`
- **Implementation**: See `AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md`
- **Full design**: See `AGENTS-MARKETPLACE-MVP-DESIGN.md`

---

**Created**: 2026-02-21
**Status**: MVP ready for development
**Next**: Follow `AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md` Week 1
