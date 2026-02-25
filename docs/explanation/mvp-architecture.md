# YAWL MCP-A2A MVP Architecture

## Executive Summary

This document describes the architecture for an MVP application combining:
- **MCP Client** - Connect to external MCP servers (tools, resources, prompts)
- **MCP Server** - Expose YAWL workflow tools to AI agents
- **A2A Cloud Deployment** - Agent-to-Agent communication for distributed workflows
- **YAWL Workflow Engine** - Core BPM engine with Petri net semantics

---

## 1. System Architecture Overview

### 1.1 High-Level Architecture Diagram

```
                                    ┌─────────────────────────────────────────────────────────────────────┐
                                    │                         CLOUD DEPLOYMENT                            │
                                    │  ┌─────────────────────────────────────────────────────────────┐   │
                                    │  │                    KUBERNETES CLUSTER                        │   │
                                    │  │                                                              │   │
                                    │  │  ┌──────────────────┐      ┌──────────────────────────────┐ │   │
                                    │  │  │   INGRESS/LB     │      │     SERVICE MESH (Istio)     │ │   │
                                    │  │  │  (TLS 1.3)       │      │  - mTLS between services     │ │   │
                                    │  │  └────────┬─────────┘      │  - Traffic management        │ │   │
                                    │  │           │                └──────────┬───────────────────┘ │   │
                                    │  │           │                           │                      │   │
                                    │  │  ┌────────▼────────────────────────────────────────────────┐ │   │
                                    │  │  │                    API GATEWAY                          │ │   │
                                    │  │  │  - JWT validation                                         │ │   │
                                    │  │  │  - Rate limiting (100 req/s per agent)                   │ │   │
                                    │  │  │  - Request routing                                        │ │   │
                                    │  │  └────────┬───────────────────────┬────────────────────────┘ │   │
                                    │  │           │                       │                          │   │
                                    │  │  ┌────────▼────────┐    ┌────────▼─────────┐                 │   │
                                    │  │  │  MCP GATEWAY    │    │   A2A GATEWAY    │                 │   │
                                    │  │  │  (SSE/HTTP)     │    │   (REST)         │                 │   │
                                    │  │  │  :8080          │    │   :8081          │                 │   │
                                    │  │  └────────┬────────┘    └────────┬─────────┘                 │   │
                                    │  │           │                       │                          │   │
                                    │  │  ┌────────▼───────────────────────▼────────────────────────┐ │   │
                                    │  │  │               YAWL INTEGRATION LAYER                     │ │   │
                                    │  │  │  ┌─────────────────┐  ┌─────────────────┐                │ │   │
                                    │  │  │  │  MCP SERVER     │  │  A2A SERVER     │                │ │   │
                                    │  │  │  │  (15 tools)     │  │  (5 skills)     │                │ │   │
                                    │  │  │  │  (6 resources)  │  │  Handoff Proto  │                │ │   │
                                    │  │  │  └────────┬────────┘  └────────┬────────┘                │ │   │
                                    │  │  │           │                     │                          │ │   │
                                    │  │  │  ┌────────▼─────────────────────▼────────────────────────┐│ │   │
                                    │  │  │  │              SESSION MANAGER                            ││ │   │
                                    │  │  │  │  - Connection pooling (max 100 sessions)               ││ │   │
                                    │  │  │  │  - Automatic reconnection                               ││ │   │
                                    │  │  │  │  - Session affinity for handoff                        ││ │   │
                                    │  │  │  └─────────────────────────┬──────────────────────────────┘│ │   │
                                    │  │  └────────────────────────────┼────────────────────────────────┘ │   │
                                    │  │                               │                                  │   │
                                    │  │  ┌────────────────────────────▼────────────────────────────────┐ │   │
                                    │  │  │                    YAWL ENGINE                               │ │   │
                                    │  │  │  ┌───────────────┐  ┌───────────────┐  ┌────────────────┐  │ │   │
                                    │  │  │  │ Interface A   │  │ Interface B   │  │ Interface E    │  │ │   │
                                    │  │  │  │ (Design-time) │  │ (Runtime)     │  │ (Events)       │  │ │   │
                                    │  │  │  └───────────────┘  └───────────────┘  └────────────────┘  │ │   │
                                    │  │  │  ┌───────────────────────────────────────────────────────┐  │ │   │
                                    │  │  │  │                WORKITEM PROCESSOR                       │  │ │   │
                                    │  │  │  │  - Virtual threads per case (10k+ concurrent)          │  │ │   │
                                    │  │  │  │  - Structured concurrency for parallel tasks           │  │ │   │
                                    │  │  │  └───────────────────────────────────────────────────────┘  │ │   │
                                    │  │  └──────────────────────────────────────────────────────────────┘ │   │
                                    │  │                               │                                  │   │
                                    │  │  ┌────────────────────────────▼────────────────────────────────┐ │   │
                                    │  │  │                 DATA LAYER (StatefulSet)                    │ │   │
                                    │  │  │  ┌────────────┐  ┌────────────┐  ┌──────────────────────┐  │ │   │
                                    │  │  │  │ PostgreSQL │  │   Redis    │  │  Object Store (S3)   │  │ │   │
                                    │  │  │  │ (Workflow  │  │  (Cache +  │  │  (Spec storage,      │  │ │   │
                                    │  │  │  │  State)    │  │  Sessions) │  │   logs, artifacts)   │  │ │   │
                                    │  │  │  └────────────┘  └────────────┘  └──────────────────────┘  │ │   │
                                    │  │  └──────────────────────────────────────────────────────────────┘ │   │
                                    │  └──────────────────────────────────────────────────────────────────────┘ │   │
                                    │                                                                              │   │
                                    │  ┌──────────────────────────────────────────────────────────────────────┐ │   │
                                    │  │                      OBSERVABILITY STACK                               │ │   │
                                    │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────────┐ │ │   │
                                    │  │  │ Prometheus │  │  Grafana   │  │  Jaeger    │  │  Loki          │ │ │   │
                                    │  │  │ (Metrics)  │  │ (Dashboards)│  │ (Traces)  │  │  (Logs)        │ │ │   │
                                    │  │  └────────────┘  └────────────┘  └────────────┘  └────────────────┘ │ │   │
                                    │  └──────────────────────────────────────────────────────────────────────┘ │   │
                                    └──────────────────────────────────────────────────────────────────────────────┘
                                    │                                                                              │
                                    │  ┌──────────────────────────────────────────────────────────────────────┐ │   │
                                    │  │                      EXTERNAL AGENTS (Cloud)                           │ │   │
                                    │  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────────┐│ │   │
                                    │  │  │  Claude Agent  │  │  Z.AI Agent    │  │  Custom Enterprise Agent   ││ │   │
                                    │  │  │  (MCP Client)  │  │  (A2A Client)  │  │  (MCP + A2A Client)        ││ │   │
                                    │  │  └───────┬────────┘  └───────┬────────┘  └────────────┬───────────────┘│ │   │
                                    │  │          │                    │                        │                │ │   │
                                    │  │          └────────────────────┴────────────────────────┘                │ │   │
                                    │  │                               │                                        │ │   │
                                    │  │                    A2A/MCP Protocol (HTTPS)                            │ │   │
                                    │  └──────────────────────────────────────────────────────────────────────┘ │   │
                                    └──────────────────────────────────────────────────────────────────────────────┘
                                                              │
                                    ┌─────────────────────────┼─────────────────────────────┐
                                    │                         │                             │
                                    │    MCP CLIENT MODE      │    A2A CLIENT MODE          │    STANDALONE
                                    │                         │                             │
                                    │  Connect to external    │  Connect to remote A2A      │  Local STDIO
                                    │  MCP servers:           │  agents:                    │  transport
                                    │  - filesystem-mcp       │  - Other YAWL instances     │  for CLI tools
                                    │  - postgres-mcp         │  - Claude Code              │
                                    │  - custom tools         │  - Enterprise systems       │
                                    │                         │                             │
                                    └─────────────────────────┴─────────────────────────────┘
```

### 1.2 Core Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Stateless Services** | MCP Server and A2A Server are stateless; session state in Redis |
| **Horizontal Scaling** | Multiple engine replicas with shared database |
| **Fail-Fast** | Missing dependencies cause immediate startup failure |
| **Zero Trust** | mTLS + JWT for all service-to-service communication |
| **Observability First** | OTEL traces, Prometheus metrics, structured logging |

---

## 2. Module Structure

### 2.1 Maven Multi-Module Layout

```
yawl-mvp/
├── pom.xml                              # Parent POM with dependency management
├── yawl-engine/                         # Core YAWL engine (existing)
│   ├── src/main/java/.../engine/
│   └── src/main/java/.../elements/
├── yawl-interfaces/                     # Interface definitions (existing)
│   ├── src/main/java/.../interfce/
│   │   ├── interfaceA/                  # Design-time operations
│   │   ├── interfaceB/                  # Runtime operations
│   │   └── interfaceE/                  # Event notifications
├── yawl-integration/                    # Integration layer (new)
│   ├── pom.xml
│   ├── yawl-mcp-server/                 # MCP Server module
│   │   ├── src/main/java/.../integration/mcp/
│   │   │   ├── server/                  # YawlMcpServer, capabilities
│   │   │   ├── spec/                    # Tool/Resource/Prompt specs
│   │   │   ├── spring/                  # Spring integration
│   │   │   └── zai/                     # Z.AI function service
│   │   └── src/test/java/.../integration/mcp/
│   ├── yawl-mcp-client/                 # MCP Client module
│   │   ├── src/main/java/.../integration/mcp/client/
│   │   │   ├── YawlMcpClient.java       # STDIO/SSE client
│   │   │   ├── McpServerPool.java       # Connection pooling
│   │   │   └── McpToolInvoker.java      # Tool invocation facade
│   │   └── src/test/java/.../integration/mcp/client/
│   ├── yawl-a2a-server/                 # A2A Server module
│   │   ├── src/main/java/.../integration/a2a/
│   │   │   ├── YawlA2AServer.java       # HTTP REST server
│   │   │   ├── auth/                    # Authentication providers
│   │   │   ├── handoff/                 # Handoff protocol
│   │   │   └── validation/              # Schema validation
│   │   └── src/test/java/.../integration/a2a/
│   └── yawl-a2a-client/                 # A2A Client module
│       ├── src/main/java/.../integration/a2a/client/
│       │   ├── YawlA2AClient.java       # A2A REST client
│       │   └── AgentDiscovery.java      # Agent card discovery
│       └── src/test/java/.../integration/a2a/client/
├── yawl-deployment/                     # Deployment configurations
│   ├── docker/
│   │   ├── Dockerfile.engine            # Engine container
│   │   ├── Dockerfile.mcp               # MCP Server container
│   │   ├── Dockerfile.a2a               # A2A Server container
│   │   └── docker-compose.yml           # Local development
│   └── k8s/
│       ├── base/
│       │   ├── deployment-engine.yaml
│       │   ├── deployment-mcp.yaml
│       │   ├── deployment-a2a.yaml
│       │   ├── service-mcp.yaml
│       │   ├── service-a2a.yaml
│       │   └── configmap.yaml
│       └── overlays/
│           ├── dev/
│           ├── staging/
│           └── production/
└── docs/
    ├── MVP_ARCHITECTURE.md              # This document
    ├── API_REFERENCE.md
    └── DEPLOYMENT_GUIDE.md
```

### 2.2 Module Dependency Graph

```
                        ┌─────────────────┐
                        │   yawl-engine   │
                        │  (StatefulSet)  │
                        └────────┬────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
              ▼                  ▼                  ▼
    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
    │ yawl-mcp-server │  │ yawl-a2a-server │  │ yawl-interfaces │
    │ (Deployment)    │  │ (Deployment)    │  │ (Library)       │
    └────────┬────────┘  └────────┬────────┘  └─────────────────┘
             │                    │
             │    ┌───────────────┴───────────────┐
             │    │                               │
             ▼    ▼                               ▼
    ┌─────────────────┐                  ┌─────────────────┐
    │ yawl-mcp-client │                  │ yawl-a2a-client │
    │ (Library)       │                  │ (Library)       │
    └─────────────────┘                  └─────────────────┘
```

---

## 3. Key Components

### 3.1 MCP Server (`YawlMcpServer`)

**Responsibilities:**
- Expose YAWL workflow operations as MCP tools
- Provide read-only access to specifications, cases, work items as resources
- Support prompt templates for workflow analysis
- STDIO transport for local CLI tools, SSE transport for cloud deployment

**Exposed Capabilities:**

| Category | Tools (15) | Resources (6) | Prompts (4) |
|----------|------------|---------------|-------------|
| **Case Management** | launch_case, cancel_case, suspend_case, resume_case | yawl://cases, yawl://cases/{id} | workflow_analysis |
| **Work Item Ops** | get_workitems, checkout_workitem, checkin_workitem, skip_workitem | yawl://workitems, yawl://workitems/{id} | task_completion_guide |
| **Specification** | list_specifications, upload_specification, unload_specification | yawl://specifications | workflow_design_review |
| **Query** | get_case_state, get_specification_data, get_running_cases | yawl://cases/{id}/data | case_troubleshooting |

**Configuration:**
```yaml
yawl:
  mcp:
    enabled: true
    engine-url: ${YAWL_ENGINE_URL:http://localhost:8080/yawl}
    username: ${YAWL_USERNAME:admin}
    password: ${YAWL_PASSWORD}          # Required, no default
    transport: stdio                    # stdio | sse
    sse:
      port: 8080
      path: /mcp/sse
    connection:
      retry-attempts: 3
      retry-delay-ms: 1000
      timeout-ms: 30000
```

### 3.2 MCP Client (`YawlMcpClient`)

**Responsibilities:**
- Connect to external MCP servers via STDIO or SSE
- Invoke tools from YAWL workflows (e.g., call filesystem tools)
- Read resources for workflow data enrichment
- Connection pooling and automatic reconnection

**Usage Pattern in Workflows:**
```java
// In a YAWL custom service task
public class McpInvokerService {
    private final McpToolInvoker toolInvoker;

    public String callExternalTool(String serverId, String tool, Map<String, Object> args) {
        return toolInvoker.invoke(serverId, tool, args);
    }
}
```

**Connection Pool Configuration:**
```yaml
yawl:
  mcp-client:
    servers:
      - id: filesystem
        transport: stdio
        command: npx
        args: ["-y", "@modelcontextprotocol/server-filesystem", "/data"]
        pool-size: 5
      - id: postgres
        transport: sse
        url: https://postgres-mcp.example.com/sse
        pool-size: 10
        auth:
          type: bearer
          token: ${POSTGRES_MCP_TOKEN}
```

### 3.3 A2A Server (`YawlA2AServer`)

**Responsibilities:**
- Expose YAWL as an A2A agent over HTTP REST
- Agent card discovery at `/.well-known/agent.json`
- Message-based workflow invocation
- Secure handoff protocol for work item transfer

**Exposed Skills:**

| Skill ID | Description | Required Permission |
|----------|-------------|---------------------|
| `launch_workflow` | Launch a workflow case from specification | `workflow:launch` |
| `query_workflows` | List specifications and running cases | `workflow:query` |
| `manage_workitems` | Get, checkout, and complete work items | `workitem:manage` |
| `cancel_workflow` | Cancel a running workflow case | `workflow:cancel` |
| `handoff_workitem` | Transfer work item to another agent | `workitem:manage` |

**API Endpoints:**

| Method | Path | Auth Required | Description |
|--------|------|---------------|-------------|
| GET | `/.well-known/agent.json` | No | Agent card discovery |
| POST | `/` | Yes | Send A2A message |
| GET | `/tasks/{id}` | Yes | Get task status |
| POST | `/tasks/{id}/cancel` | Yes | Cancel a task |
| POST | `/handoff` | Yes | Handoff work item |

### 3.4 A2A Client (`YawlA2AClient`)

**Responsibilities:**
- Connect to remote A2A agents
- Send workflow commands to other YAWL instances
- Discover agent capabilities via agent cards
- Handle streaming task events

**Usage Pattern:**
```java
// Invoke a remote YAWL instance
YawlA2AClient remoteYawl = new YawlA2AClient("https://yawl-prod.example.com");
remoteYawl.connect();

// Send a workflow launch command
String response = remoteYawl.sendMessage(
    "Launch the OrderProcessing workflow with customer ID 12345"
);

// Query available skills
List<AgentSkill> skills = remoteYawl.getSkills();
```

### 3.5 Handoff Protocol (`HandoffProtocol`)

**Purpose:** Secure work item transfer between autonomous agents when an agent cannot complete a task.

**Protocol Sequence:**
```
Agent A (Source)                YAWL Engine                Agent B (Target)
      │                              │                              │
      │ 1. checkout_workitem         │                              │
      │─────────────────────────────>│                              │
      │                              │                              │
      │ 2. Cannot complete task      │                              │
      │    (determination)           │                              │
      │                              │                              │
      │ 3. Generate handoff token    │                              │
      │    (JWT with 60s TTL)        │                              │
      │                              │                              │
      │ 4. POST /handoff             │                              │
      │────────────────────────────────────────────────────────────>│
      │    {token, workItemId,       │                              │
      │     fromAgent, toAgent}      │                              │
      │                              │                              │
      │                              │ 5. Validate token            │
      │                              │<─────────────────────────────│
      │                              │                              │
      │ 6. Rollback checkout         │                              │
      │<─────────────────────────────│                              │
      │                              │                              │
      │                              │ 7. Checkout workitem         │
      │                              │<─────────────────────────────│
      │                              │                              │
      │                              │ 8. Complete workitem         │
      │                              │<─────────────────────────────│
      │                              │                              │
```

**Token Structure (JWT Claims):**
```json
{
  "sub": "handoff",
  "workItemId": "WI-42",
  "fromAgent": "agent-approval-1",
  "toAgent": "agent-specialist-2",
  "engineSession": "<session-handle>",
  "exp": 1740000060,
  "iat": 1740000000
}
```

---

## 4. Security Architecture

### 4.1 Authentication Chain

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AUTHENTICATION FLOW                                 │
│                                                                              │
│  Request ──► canHandle()? ──► authenticate() ──► Principal ──► Authorize    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                    CompositeAuthenticationProvider                       ││
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         ││
│  │  │ 1. SPIFFE/mTLS  │─►│ 2. JWT Bearer   │─►│ 3. API Key      │         ││
│  │  │ (Service mesh)  │  │ (External APIs) │  │ (Tooling)       │         ││
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘         ││
│  │                          │                                               ││
│  │                          ▼                                               ││
│  │  ┌─────────────────────────────────────────────────────────────────┐   ││
│  │  │ 4. Handoff Token (JWT for agent-to-agent work item transfer)    │   ││
│  │  └─────────────────────────────────────────────────────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Authorization Model

**Permissions (RBAC):**

| Permission | Description | Granted To |
|------------|-------------|------------|
| `workflow:launch` | Launch new workflow cases | Service accounts, Admin |
| `workflow:query` | Query specifications and cases | All authenticated users |
| `workflow:cancel` | Cancel running cases | Admin, Operations |
| `workitem:manage` | Checkout and complete work items | Agents, Workers |
| `admin:all` | Full administrative access | Admin only |

**JWT Claims Structure:**
```json
{
  "sub": "agent-approval-1",
  "iss": "yawl-iam.example.com",
  "aud": "yawl-a2a",
  "exp": 1740003600,
  "iat": 1740000000,
  "permissions": ["workflow:launch", "workitem:manage"],
  "agent_id": "agent-approval-1",
  "tenant_id": "tenant-001"
}
```

### 4.3 Network Security

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NETWORK SECURITY ZONES                             │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ PUBLIC ZONE (DMZ)                                                      │  │
│  │  - Ingress Load Balancer (TLS 1.3 termination)                        │  │
│  │  - WAF rules (OWASP Top 10)                                           │  │
│  │  - DDoS protection                                                    │  │
│  └────────────────────────────────┬──────────────────────────────────────┘  │
│                                   │                                         │
│  ┌────────────────────────────────▼──────────────────────────────────────┐  │
│  │ APPLICATION ZONE                                                       │  │
│  │  - API Gateway (JWT validation)                                       │  │
│  │  - MCP Server pods                                                    │  │
│  │  - A2A Server pods                                                    │  │
│  │  - Service mesh (mTLS between pods)                                   │  │
│  └────────────────────────────────┬──────────────────────────────────────┘  │
│                                   │                                         │
│  ┌────────────────────────────────▼──────────────────────────────────────┐  │
│  │ DATA ZONE                                                              │  │
│  │  - YAWL Engine (StatefulSet)                                          │  │
│  │  - PostgreSQL (Private subnets)                                       │  │
│  │  - Redis (Private subnets)                                            │  │
│  │  - Object Storage (Private endpoints)                                 │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Secret Management

| Secret | Storage | Rotation Policy |
|--------|---------|-----------------|
| `YAWL_PASSWORD` | Kubernetes Secret + Vault | 90 days |
| `A2A_JWT_SECRET` | Vault (dynamic) | 24 hours |
| `A2A_API_KEY_MASTER` | Vault | 30 days |
| Database credentials | Vault (dynamic) | 1 hour (dynamic) |
| TLS certificates | cert-manager | 90 days |

---

## 5. Data Flow Patterns

### 5.1 MCP Tool Invocation Flow

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│ Claude  │     │   MCP   │     │   MCP   │     │  YAWL   │     │  YAWL   │
│ Desktop │     │ Client  │     │ Server  │     │ Session │     │ Engine  │
└────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘
     │               │               │               │               │
     │ 1. User asks  │               │               │               │
     │    "Launch    │               │               │               │
     │    workflow"  │               │               │               │
     │──────────────>│               │               │               │
     │               │               │               │               │
     │               │ 2. MCP        │               │               │
     │               │    initialize │               │               │
     │               │──────────────>│               │               │
     │               │               │               │               │
     │               │ 3. tools/call │               │               │
     │               │    launch_case│               │               │
     │               │──────────────>│               │               │
     │               │               │               │               │
     │               │               │ 4. Get session│               │
     │               │               │──────────────>│               │
     │               │               │               │               │
     │               │               │               │ 5. launchCase │
     │               │               │               │──────────────>│
     │               │               │               │               │
     │               │               │               │ 6. Case ID    │
     │               │               │               │<──────────────│
     │               │               │               │               │
     │               │               │ 7. Result     │               │
     │               │               │<──────────────│               │
     │               │               │               │               │
     │               │ 8. Tool result│               │               │
     │               │<──────────────│               │               │
     │               │               │               │               │
     │ 9. Response   │               │               │               │
     │<──────────────│               │               │               │
```

### 5.2 A2A Agent Communication Flow

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│ Claude  │     │   A2A   │     │   A2A   │     │   YAWL  │     │  YAWL   │
│ Code    │     │ Client  │     │ Server  │     │ Adapter │     │ Engine  │
└────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘
     │               │               │               │               │
     │ 1. Get Agent  │               │               │               │
     │    Card       │               │               │               │
     │──────────────>│               │               │               │
     │               │               │               │               │
     │               │ 2. GET        │               │               │
     │               │    /.well-known/agent.json    │               │
     │               │──────────────>│               │               │
     │               │               │               │               │
     │               │ 3. AgentCard  │               │               │
     │               │<──────────────│               │               │
     │               │               │               │               │
     │ 4. Skills +   │               │               │               │
     │    Capabilities               │               │               │
     │<──────────────│               │               │               │
     │               │               │               │               │
     │ 5. Send message               │               │               │
     │    "Launch workflow X"        │               │               │
     │──────────────>│               │               │               │
     │               │               │               │               │
     │               │ 6. POST /     │               │               │
     │               │    (with JWT) │               │               │
     │               │──────────────>│               │               │
     │               │               │               │               │
     │               │               │ 7. Validate   │               │
     │               │               │    JWT        │               │
     │               │               │               │               │
     │               │               │ 8. Execute    │               │
     │               │               │    AgentExecutor              │
     │               │               │──────────────>│               │
     │               │               │               │               │
     │               │               │               │ 9. InterfaceB │
     │               │               │               │    operations │
     │               │               │               │──────────────>│
     │               │               │               │               │
     │               │               │               │ 10. Result    │
     │               │               │               │<──────────────│
     │               │               │               │               │
     │               │               │ 11. Agent     │               │
     │               │               │     message   │               │
     │               │               │<──────────────│               │
     │               │               │               │               │
     │               │ 12. Task      │               │               │
     │               │     complete  │               │               │
     │               │<──────────────│               │               │
     │               │               │               │               │
     │ 13. Response  │               │               │               │
     │<──────────────│               │               │               │
```

### 5.3 Handoff Flow (Agent-to-Agent Transfer)

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Agent A     │    │  YAWL A2A    │    │  YAWL A2A    │    │  Agent B     │
│  (Source)    │    │  Server      │    │  Engine      │    │  (Target)    │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │                   │
       │ 1. Checkout WI-42 │                   │                   │
       │──────────────────>│                   │                   │
       │                   │──────────────────>│                   │
       │                   │                   │                   │
       │ 2. Cannot complete│                   │                   │
       │    (business rule)                   │                   │
       │                   │                   │                   │
       │ 3. Generate token │                   │                   │
       │    (HandoffProtocol)                 │                   │
       │                   │                   │                   │
       │ 4. POST /handoff   │                   │                   │
       │    {token, WI-42} │                   │                   │
       │──────────────────>│                   │                   │
       │                   │                   │                   │
       │                   │ 5. Validate token │                   │
       │                   │──────────────────>│                   │
       │                   │                   │                   │
       │                   │ 6. Rollback       │                   │
       │<──────────────────│<──────────────────│                   │
       │                   │                   │                   │
       │                   │ 7. Notify Agent B │                   │
       │                   │───────────────────────────────────────>
       │                   │                   │                   │
       │                   │                   │ 8. Checkout WI-42 │
       │                   │                   │<──────────────────│
       │                   │                   │                   │
       │                   │                   │ 9. Complete WI-42 │
       │                   │                   │<──────────────────│
       │                   │                   │                   │
       │ 10. Handoff ACK   │                   │                   │
       │<──────────────────│<───────────────────────────────────────
       │                   │                   │                   │
```

---

## 6. Deployment Configuration

### 6.1 Docker Compose (Development)

```yaml
# docker-compose.yml
version: '3.8'

services:
  yawl-engine:
    build:
      context: .
      dockerfile: yawl-deployment/docker/Dockerfile.engine
    ports:
      - "8080:8080"
    environment:
      - YAWL_DB_URL=jdbc:postgresql://postgres:5432/yawl
      - YAWL_DB_USER=${YAWL_DB_USER}
      - YAWL_DB_PASSWORD=${YAWL_DB_PASSWORD}
      - YAWL_SESSION_TIMEOUT=3600
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/yawl/ib/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G

  yawl-mcp-server:
    build:
      context: .
      dockerfile: yawl-deployment/docker/Dockerfile.mcp
    ports:
      - "8082:8080"
    environment:
      - YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
      - YAWL_USERNAME=${YAWL_USERNAME}
      - YAWL_PASSWORD=${YAWL_PASSWORD}
      - MCP_TRANSPORT=sse
      - MCP_SSE_PORT=8080
    depends_on:
      yawl-engine:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M

  yawl-a2a-server:
    build:
      context: .
      dockerfile: yawl-deployment/docker/Dockerfile.a2a
    ports:
      - "8081:8081"
    environment:
      - YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
      - YAWL_USERNAME=${YAWL_USERNAME}
      - YAWL_PASSWORD=${YAWL_PASSWORD}
      - A2A_PORT=8081
      - A2A_JWT_SECRET=${A2A_JWT_SECRET}
      - A2A_JWT_ISSUER=yawl-a2a-dev
    depends_on:
      yawl-engine:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M

  postgres:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=yawl
      - POSTGRES_USER=${YAWL_DB_USER}
      - POSTGRES_PASSWORD=${YAWL_DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${YAWL_DB_USER} -d yawl"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  redis_data:
```

### 6.2 Kubernetes Deployment (Production)

```yaml
# k8s/overlays/production/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: yawl-production

resources:
  - ../../base
  - network-policy.yaml
  - pod-disruption-budget.yaml
  - horizontal-pod-autoscaler.yaml

configMapGenerator:
  - name: yawl-config
    behavior: merge
    literals:
      - YAWL_SESSION_TIMEOUT=3600
      - MCP_TRANSPORT=sse
      - A2A_JWT_ISSUER=yawl-a2a.prod.example.com

secretGenerator:
  - name: yawl-secrets
    type: Opaque
    behavior: merge
    literals:
      # These should be sealed in production
      # Use sealed-secrets or external-secrets-operator
      - YAWL_PASSWORD=REDACTED
      - A2A_JWT_SECRET=REDACTED

images:
  - name: yawl-engine
    newTag: v6.0.0
  - name: yawl-mcp-server
    newTag: v6.0.0
  - name: yawl-a2a-server
    newTag: v6.0.0

patches:
  - target:
      kind: Deployment
      name: yawl-engine
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 3
      - op: add
        path: /spec/template/spec/containers/0/resources
        value:
          limits:
            cpu: "2"
            memory: "4Gi"
          requests:
            cpu: "1"
            memory: "2Gi"
```

```yaml
# k8s/overlays/production/horizontal-pod-autoscaler.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-mcp-server-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-mcp-server
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-a2a-server-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-a2a-server
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

## 7. API Reference

### 7.1 MCP Server Tools

| Tool | Input Schema | Output |
|------|--------------|--------|
| `launch_case` | `{specId, version?, uri?, caseData?}` | `{caseId, status}` |
| `cancel_case` | `{caseId}` | `{success, message}` |
| `get_case_state` | `{caseId}` | `{state, netElements}` |
| `get_workitems` | `{caseId?, status?}` | `{workItems: [...]}` |
| `checkout_workitem` | `{workItemId}` | `{workItem, data}` |
| `checkin_workitem` | `{workItemId, outputData, logPredicate?}` | `{success}` |
| `list_specifications` | `{}` | `{specifications: [...]}` |
| `upload_specification` | `{specXml, specId?, version?}` | `{success, specId}` |
| `get_specification_data` | `{specId, version?}` | `{specification: {...}}` |

### 7.2 A2A Server Endpoints

#### Agent Card Discovery
```http
GET /.well-known/agent.json HTTP/1.1
Host: yawl-a2a.example.com

Response 200:
{
  "name": "YAWL Workflow Engine",
  "description": "YAWL BPM engine agent for workflow management",
  "version": "6.0.0",
  "provider": {
    "organization": "YAWL Foundation",
    "url": "https://yawlfoundation.github.io"
  },
  "capabilities": {
    "streaming": false,
    "pushNotifications": false
  },
  "skills": [
    {
      "id": "launch_workflow",
      "name": "Launch Workflow",
      "description": "Launch a new workflow case",
      "tags": ["workflow", "bpm", "launch"]
    }
  ]
}
```

#### Send Message
```http
POST / HTTP/1.1
Host: yawl-a2a.example.com
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "send",
  "params": {
    "message": {
      "role": "user",
      "parts": [
        {"type": "text", "text": "Launch the OrderProcessing workflow"}
      ]
    }
  },
  "id": "req-001"
}

Response 200:
{
  "jsonrpc": "2.0",
  "result": {
    "task": {
      "id": "task-001",
      "status": {"state": "completed"},
      "message": {
        "role": "agent",
        "parts": [
          {"type": "text", "text": "Workflow launched. Case ID: 42"}
        ]
      }
    }
  },
  "id": "req-001"
}
```

#### Handoff Work Item
```http
POST /handoff HTTP/1.1
Host: yawl-a2a.example.com
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "workItemId": "WI-42",
  "fromAgent": "agent-approval-1",
  "toAgent": "agent-specialist-2",
  "token": "<handoff-jwt-token>",
  "context": {
    "reason": "Requires specialist approval",
    "priority": "high"
  }
}

Response 200:
{
  "status": "accepted",
  "workItemId": "WI-42",
  "targetAgent": "agent-specialist-2",
  "message": "Handoff initiated. Target agent can now checkout."
}
```

---

## 8. Performance Considerations

### 8.1 Scalability Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Concurrent Cases | 10,000+ | Virtual threads per case |
| Tool Invocations/sec | 1,000+ | Per MCP server pod |
| A2A Messages/sec | 500+ | Per A2A server pod |
| Session Pool Size | 100 | Per engine instance |
| Case State Query | <50ms | P99 latency |
| Work Item Checkout | <100ms | P99 latency |

### 8.2 Optimization Strategies

**Virtual Threads (Java 25):**
```java
// YawlMcpServer.java - Virtual thread for shutdown hook
Runtime.getRuntime().addShutdownHook(
    Thread.ofVirtual().unstarted(() -> {
        server.stop();
    })
);
```

**Connection Pooling:**
```yaml
# Session pool configuration
yawl:
  session-pool:
    max-size: 100
    idle-timeout-ms: 300000
    validation-interval-ms: 60000
```

**Caching Strategy:**
| Cache | TTL | Storage |
|-------|-----|---------|
| Agent Cards | 5 min | Redis |
| Specification List | 1 min | Redis |
| Session Handles | Session timeout | Redis |
| Tool Schemas | 1 hour | In-memory |

### 8.3 Resource Limits

| Component | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----------|-------------|-----------|----------------|--------------|
| YAWL Engine | 1 core | 2 cores | 2 GB | 4 GB |
| MCP Server | 100m | 500m | 256 MB | 512 MB |
| A2A Server | 100m | 500m | 256 MB | 512 MB |
| PostgreSQL | 1 core | 2 cores | 2 GB | 4 GB |
| Redis | 100m | 250m | 256 MB | 512 MB |

---

## 9. Monitoring and Observability

### 9.1 Metrics (Prometheus)

**MCP Server Metrics:**
```yaml
# Custom metrics exposed at /actuator/prometheus
yawl_mcp_tool_invocations_total{tool="launch_case",status="success"}
yawl_mcp_tool_invocations_total{tool="launch_case",status="error"}
yawl_mcp_tool_duration_seconds{tool="launch_case",quantile="0.99"}
yawl_mcp_active_connections
yawl_mcp_session_pool_size
yawl_mcp_session_pool_available
```

**A2A Server Metrics:**
```yaml
yawl_a2a_messages_total{skill="launch_workflow",status="success"}
yawl_a2a_message_duration_seconds{skill="launch_workflow",quantile="0.99"}
yawl_a2a_handoff_total{status="success"}
yawl_a2a_handoff_total{status="failed"}
yawl_a2a_auth_failures_total{scheme="jwt"}
yawl_a2a_active_tasks
```

**Engine Metrics:**
```yaml
yawl_engine_cases_total{status="running"}
yawl_engine_cases_total{status="completed"}
yawl_engine_cases_total{status="cancelled"}
yawl_engine_workitems_total{status="enabled"}
yawl_engine_workitems_total{status="executing"}
yawl_engine_session_handles_active
```

### 9.2 Tracing (OpenTelemetry)

**Trace Spans:**
```
mcp.tool.invocation
  ├── yawl.session.get
  ├── yawl.interfaceB.launchCase
  │   ├── yawl.specification.load
  │   ├── yawl.case.create
  │   └── yawl.netrunner.start
  └── mcp.tool.response
```

**A2A Trace:**
```
a2a.message.receive
  ├── a2a.auth.validate
  ├── a2a.agent.executor
  │   ├── yawl.session.ensure
  │   ├── yawl.interfaceB.operation
  │   └── yawl.response.build
  └── a2a.message.response
```

### 9.3 Logging (Structured)

**Log Format (JSON):**
```json
{
  "timestamp": "2026-02-19T10:30:00.000Z",
  "level": "INFO",
  "logger": "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer",
  "message": "Tool invocation completed",
  "traceId": "abc123",
  "spanId": "def456",
  "context": {
    "tool": "launch_case",
    "specId": "OrderProcessing",
    "caseId": "42",
    "durationMs": 150
  }
}
```

### 9.4 Alerts (Alertmanager)

```yaml
groups:
  - name: yawl-mvp
    rules:
      - alert: YawlEngineDown
        expr: up{job="yawl-engine"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "YAWL Engine is down"
          description: "YAWL Engine instance {{ $labels.instance }} is not responding"

      - alert: HighToolLatency
        expr: histogram_quantile(0.99, rate(yawl_mcp_tool_duration_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High MCP tool latency"
          description: "P99 latency for tool {{ $labels.tool }} is {{ $value }}s"

      - alert: HandoffFailures
        expr: rate(yawl_a2a_handoff_total{status="failed"}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High handoff failure rate"
          description: "Handoff failures are occurring at {{ $value }}/s"

      - alert: SessionPoolExhausted
        expr: yawl_mcp_session_pool_available == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Session pool exhausted"
          description: "No available sessions in the pool"
```

### 9.5 Dashboards (Grafana)

**Dashboard Panels:**

1. **Overview Dashboard**
   - Total cases (running/completed/cancelled)
   - Active work items by status
   - Request rate (MCP + A2A)
   - Error rate (4xx/5xx)

2. **MCP Server Dashboard**
   - Tool invocation rate by tool
   - P50/P95/P99 latency per tool
   - Active connections
   - Session pool utilization

3. **A2A Server Dashboard**
   - Message rate by skill
   - Authentication success/failure rate
   - Handoff success rate
   - Active tasks

4. **Engine Dashboard**
   - Case throughput
   - Work item distribution
   - Database connection pool
   - JVM metrics (heap, GC)

---

## 10. Implementation Roadmap

### Phase 1: Core Integration (Week 1-2)

| Task | Deliverable | Owner |
|------|-------------|-------|
| MCP Server enhancements | HTTP/SSE transport support | Integration Team |
| A2A Server hardening | Production auth stack | Security Team |
| Session Manager | Connection pooling | Engine Team |
| Docker images | Multi-arch images | DevOps Team |

### Phase 2: Cloud Deployment (Week 3-4)

| Task | Deliverable | Owner |
|------|-------------|-------|
| Kubernetes manifests | Base + overlays | DevOps Team |
| Helm chart | yawl-mvp chart | DevOps Team |
| CI/CD pipeline | GitHub Actions | DevOps Team |
| Secret management | Vault integration | Security Team |

### Phase 3: Observability (Week 5-6)

| Task | Deliverable | Owner |
|------|-------------|-------|
| OTEL instrumentation | Traces + metrics | Platform Team |
| Grafana dashboards | 4 dashboards | SRE Team |
| Alert rules | Alertmanager config | SRE Team |
| Log aggregation | Loki + Grafana | SRE Team |

### Phase 4: Production Readiness (Week 7-8)

| Task | Deliverable | Owner |
|------|-------------|-------|
| Load testing | k6 test suite | QA Team |
| Security audit | Penetration test | Security Team |
| Documentation | Runbooks + API docs | All Teams |
| DR testing | Failover validation | SRE Team |

---

## 11. Appendix

### A. Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `YAWL_ENGINE_URL` | Yes | - | YAWL engine base URL |
| `YAWL_USERNAME` | Yes | - | YAWL admin username |
| `YAWL_PASSWORD` | Yes | - | YAWL admin password |
| `A2A_PORT` | No | 8081 | A2A server port |
| `A2A_JWT_SECRET` | Yes* | - | JWT signing key (min 32 chars) |
| `A2A_JWT_ISSUER` | No | yawl-a2a | JWT issuer claim |
| `A2A_API_KEY_MASTER` | Yes* | - | API key master secret |
| `A2A_API_KEY` | No | - | Default API key |
| `A2A_SPIFFE_TRUST_DOMAIN` | No | yawl.cloud | SPIFFE trust domain |
| `MCP_TRANSPORT` | No | stdio | MCP transport (stdio/sse) |
| `MCP_SSE_PORT` | No | 8080 | MCP SSE port |
| `ZAI_API_KEY` | No | - | Z.AI API key for function service |

*At least one authentication method required

### B. Dependency Versions

| Dependency | Version | Purpose |
|------------|---------|---------|
| MCP Java SDK | 1.0.0-RC1 | MCP protocol implementation |
| A2A Java SDK | 1.0.0 | A2A protocol implementation |
| Spring Boot | 3.4.x | Application framework |
| PostgreSQL | 16.x | Workflow state storage |
| Redis | 7.x | Session cache |
| JJWT | 0.12.x | JWT handling |
| Jackson | 2.18.x | JSON serialization |
| OTEL | 1.44.x | Telemetry |
| Log4j2 | 2.24.x | Logging |

### C. Security Checklist

- [ ] TLS 1.3 enforced on all endpoints
- [ ] JWT tokens have short expiry (1 hour max)
- [ ] API keys rotated every 30 days
- [ ] Database credentials rotated dynamically
- [ ] mTLS enabled in service mesh
- [ ] Network policies restrict pod-to-pod communication
- [ ] Secrets stored in Vault (not Kubernetes Secrets)
- [ ] RBAC permissions follow least privilege
- [ ] Audit logging enabled for all operations
- [ ] Rate limiting configured at API gateway
- [ ] WAF rules active on ingress
- [ ] Container images scanned for vulnerabilities
- [ ] SBOM generated for all deployments

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-19 | Architecture Team | Initial architecture design |

---

**End of Document**
