# ADR-024: Multi-Cloud Agent Deployment Topology

## Status
**ACCEPTED**

## Context

YAWL v6.0.0 is deployed across AWS, Azure, and GCP (see ADR-009 for the original
multi-cloud strategy). The addition of MCP and A2A agent servers in v5.2/v6.0 creates
new deployment concerns that ADR-009 did not address:

1. **Agent registry federation**: The `AgentRegistry` (port 9090) is in-memory and
   per-node. In a multi-cloud topology, agents registered in AWS cannot be discovered
   by orchestrators running in GCP.

2. **A2A agent discovery cross-region**: A2A clients discover agents via
   `GET /.well-known/agent.json`. In a multi-cloud setup, each region runs its own
   A2A server instances. Cross-region agent discovery requires a global registry or
   DNS-based routing.

3. **MCP session state**: The MCP gateway (SSE transport, ADR-023 Pattern 4) uses
   Redis-backed sessions. In a multi-cloud deployment, session state must either be
   replicated or pinned to a region.

4. **Agent distribution strategy**: Not all agents should run in all regions. Agents
   with cloud-specific capabilities (e.g., an AWS-native document processing agent)
   should be co-located with the relevant cloud infrastructure.

5. **Failover**: If a region loses its A2A server fleet, orchestrating agents in
   other regions must be able to reroute work item processing.

## Decision

### Unified Topology

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Global Layer                                     │
│                                                                         │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │  Global Agent Registry (Federated)                               │  │
│   │  - Technology: CockroachDB (multi-region active-active)          │  │
│   │  - API: same REST interface as AgentRegistry (port 9090)         │  │
│   │  - Replication: synchronous within region, async cross-region    │  │
│   └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│   ┌──────────────────────────────────────────────────────────────────┐  │
│   │  Global DNS (Cloud-agnostic)                                     │  │
│   │  - agents.yawl.cloud → GeoDNS → nearest A2A server cluster      │  │
│   │  - registry.yawl.cloud → Global registry API                    │  │
│   └──────────────────────────────────────────────────────────────────┘  │
└───────────────┬──────────────────┬──────────────────────────────────────┘
                │                  │
     ┌──────────▼──┐          ┌────▼────────┐          ┌────────────────┐
     │  AWS Region  │          │ Azure Region│          │  GCP Region    │
     │  us-east-1   │          │ eastus      │          │  us-central1   │
     └──────────┬───┘          └────┬────────┘          └───────┬────────┘
                │                   │                           │
                └───────────────────┼───────────────────────────┘
                                    │
                 (Each region has identical internal topology)
```

### Per-Region Topology

Each cloud region contains the following components. The diagram below shows a
single region — AWS us-east-1 is used as the exemplar.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  AWS us-east-1 (replicated identically to Azure eastus, GCP us-central1)│
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Ingress Layer (ALB / Azure App Gateway / GCP GLB)                  │ │
│  │                                                                     │ │
│  │  a2a.yawl.cloud ──► A2A Server cluster (3 replicas)                │ │
│  │  mcp.yawl.cloud ──► MCP Gateway cluster (2 replicas, SSE)          │ │
│  │  api.yawl.cloud ──► YAWL Engine cluster (ADR-014)                  │ │
│  └───────────────────────────────┬─────────────────────────────────────┘ │
│                                  │                                        │
│  ┌───────────────────────────────▼─────────────────────────────────────┐ │
│  │  Agent Execution Layer (Kubernetes Deployments)                     │ │
│  │                                                                     │ │
│  │  yawl-a2a-server  (3 replicas, Deployment)                         │ │
│  │  yawl-mcp-gateway (2 replicas, Deployment, SSE transport)          │ │
│  │  yawl-agent-registry (1 replica, StatefulSet, local coordinator)   │ │
│  │  yawl-engine          (2+ replicas, Deployment, ADR-014)           │ │
│  │                                                                     │ │
│  │  ┌─────────────────────────────────────────────────────────────┐   │ │
│  │  │  Autonomous Agent Pool (variable replicas, Jobs/Deployments) │   │ │
│  │  │  - generic-workflow-agent (cloud-agnostic)                   │   │ │
│  │  │  - aws-document-agent     (AWS-specific: Textract/Bedrock)   │   │ │
│  │  │  - data-processing-agent  (cloud-agnostic)                   │   │ │
│  │  └─────────────────────────────────────────────────────────────┘   │ │
│  └───────────────────────────────┬─────────────────────────────────────┘ │
│                                  │                                        │
│  ┌───────────────────────────────▼─────────────────────────────────────┐ │
│  │  Data Layer                                                         │ │
│  │  - PostgreSQL (RDS Multi-AZ / Azure Flexible / Cloud SQL)          │ │
│  │  - Redis (ElastiCache / Azure Cache / Memorystore) — ADR-014 leases│ │
│  │  - Local AgentRegistry shard (CockroachDB node in region)          │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

### Agent Distribution Strategy

Agents are categorised into three tiers based on their cloud affinity:

**Tier 1: Cloud-Agnostic Agents (deployed in all regions)**

These agents have no cloud-specific dependencies and are deployed in every region
to maximise locality for work item processing.

| Agent Type | Docker Image | Replicas per Region |
|------------|-------------|---------------------|
| `GenericWorkflowAgent` | `yawl-generic-agent` | 2-10 (HPA) |
| `TemplateDecisionAgent` | `yawl-template-agent` | 1-5 (HPA) |
| `PollingDiscoveryAgent` | bundled in generic | N/A |

**Tier 2: Cloud-Native Agents (deployed only in the matching cloud)**

These agents use cloud-provider APIs that are only available in the corresponding
region. They must be co-located with the relevant cloud infrastructure.

| Agent Type | Cloud | Cloud Service Used |
|------------|-------|--------------------|
| `AwsDocumentAgent` | AWS only | Amazon Textract, Bedrock |
| `AzureCognitiveAgent` | Azure only | Azure Form Recognizer, OpenAI |
| `GcpDocumentAgent` | GCP only | Document AI, Vertex AI |

**Tier 3: LLM/ZAI Agents (deployed where API key permits)**

These agents use the Z.AI or other LLM APIs. They are deployed wherever the
`ZAI_API_KEY` secret is provisioned. Typically, one region is designated as
the primary LLM processing region to manage API rate limits.

| Agent Type | Primary Region | Failover |
|------------|---------------|---------|
| `ZaiDecisionAgent` | us-east-1 | eastus (if API permits) |

### Region Failover Protocol

When a region's A2A server fleet becomes unavailable, the following failover
sequence is triggered:

```
┌──────────────────────────────────────────────────────────────────────┐
│  Failover Trigger                                                    │
│                                                                      │
│  1. Health probe: /.well-known/agent.json fails for 3 consecutive   │
│     checks (30-second window)                                        │
│                                                                      │
│  2. GeoDNS weighted routing removes the unhealthy region from       │
│     the rotation. TTL: 30 seconds                                    │
│                                                                      │
│  3. Surviving regions receive increased traffic weight               │
│                                                                      │
│  4. In-progress A2A tasks:                                           │
│     - Tasks in SUBMITTED state: replayed from EventStore (ADR-006)  │
│     - Tasks in WORKING state: client must retry after timeout        │
│     - Tasks in COMPLETED state: already durable, no action          │
│                                                                      │
│  5. Agent registry: surviving regions continue with local shard.    │
│     CockroachDB replication ensures consistent view of agents        │
│     within 500ms of region loss.                                     │
│                                                                      │
│  6. YAWL Engine: cases with lease held by failed region are         │
│     reclaimed after lease TTL (30s, ADR-014).                       │
└──────────────────────────────────────────────────────────────────────┘
```

**Recovery (region comes back online):**

```
1. A2A server passes /.well-known/agent.json probe
2. GeoDNS adds region back with low weight (10%)
3. Agents re-register with local AgentRegistry shard
4. CockroachDB replication catches up (delta sync)
5. GeoDNS weight restored to normal over 5 minutes (gradual ramp)
```

### Agent Registry Federation

The in-memory `AgentRegistry` in the current codebase is replaced in production by
a federated model using CockroachDB as the backing store.

**Federation architecture:**

```
┌─────────────────────────────────────────────────────────────────────┐
│  AgentRegistryClient (each agent uses this to register)             │
│  - POST /agents/register → forwards to local shard                  │
│  - Shard replicates to all regions via CockroachDB consensus        │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
           ┌───────────────┼──────────────────┐
           │               │                  │
  ┌────────▼──────┐  ┌─────▼────────┐  ┌─────▼────────┐
  │  AWS Shard    │  │ Azure Shard  │  │  GCP Shard   │
  │  (primary)    │  │ (replica)    │  │ (replica)    │
  └───────────────┘  └──────────────┘  └──────────────┘
         CockroachDB multi-region (3 nodes, 1 per cloud)
```

**Capability-based discovery across regions:**

```
GET registry.yawl.cloud/agents/by-capability?domain=document-analysis&region=prefer-local
```

The `prefer-local` parameter instructs the registry to return agents in the same
region first, then fallback to other regions if no local agents are available.

### MCP Session State Distribution

MCP gateway sessions (SSE transport) are pinned to a region for their lifetime.
Cross-region session state is not replicated because SSE streams are stateful TCP
connections. When a region fails, active MCP sessions are terminated and clients
must reconnect to the next available region.

**Session routing:**

```
mcp.yawl.cloud
    │
    ├── Session cookie: mcp-region=us-east-1 → route to AWS
    ├── Session cookie: mcp-region=eastus    → route to Azure
    └── No session cookie                   → GeoDNS to nearest region
```

**Redis session TTL:** 24 hours (configurable via `yawl.mcp.session.ttl`).
Sessions idle for more than TTL are invalidated; the AI client must reconnect.

### Helm Values per Cloud

Each cloud has a cloud-specific Helm values file that overrides the base chart:

```yaml
# values-aws.yaml
cloud:
  provider: aws
  region: us-east-1
  agentTiers:
    cloudNative:
      enabled: true
      agents:
        - name: aws-document-agent
          image: ghcr.io/yawlfoundation/yawl-aws-agent:{{ .Values.image.tag }}
          replicas: 2
          env:
            - name: AWS_REGION
              value: us-east-1
            - name: BEDROCK_MODEL_ID
              value: anthropic.claude-3-5-sonnet-20241022-v2:0

database:
  engine: postgresql
  endpoint: ${RDS_ENDPOINT}  # injected by External Secrets from Vault

redis:
  endpoint: ${ELASTICACHE_ENDPOINT}

registry:
  cockroachdb:
    endpoint: ${COCKROACHDB_ENDPOINT}
    region: aws-us-east-1
```

```yaml
# values-azure.yaml
cloud:
  provider: azure
  region: eastus
  agentTiers:
    cloudNative:
      enabled: true
      agents:
        - name: azure-cognitive-agent
          image: ghcr.io/yawlfoundation/yawl-azure-agent:{{ .Values.image.tag }}
          replicas: 2
          env:
            - name: AZURE_FORM_RECOGNIZER_ENDPOINT
              valueFrom:
                secretKeyRef:
                  name: azure-cognitive-secrets
                  key: endpoint

database:
  engine: postgresql
  endpoint: ${AZURE_FLEXIBLE_SERVER_ENDPOINT}

redis:
  endpoint: ${AZURE_CACHE_ENDPOINT}

registry:
  cockroachdb:
    endpoint: ${COCKROACHDB_ENDPOINT}
    region: azure-eastus
```

## Consequences

### Positive

1. Cloud-agnostic agents achieve multi-region redundancy with zero configuration
   change — the Helm chart deploys them in every region automatically
2. Cloud-native agents are co-located with their cloud services, minimising API
   latency and eliminating cross-cloud egress costs
3. GeoDNS routing delivers A2A requests to the nearest region, reducing p99 latency
   by 40-60% compared to a single-region deployment
4. CockroachDB federation eliminates the single-region AgentRegistry SPOF
5. Region failover is automatic for stateless components (A2A server, MCP gateway);
   engine cases recover within the lease TTL (ADR-014: 30 seconds)

### Negative

1. CockroachDB is an additional infrastructure dependency — the team must operate
   a 3-node cluster across clouds
2. MCP sessions cannot migrate between regions — AI clients must handle reconnection
3. Cloud-native agents in a failed region are unavailable until the region recovers;
   there is no cross-cloud equivalent for cloud-specific APIs (e.g., Textract)
4. GeoDNS adds complexity: DNS TTL of 30s means up to 30s of stale routing after
   a region failure

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| CockroachDB cross-region split-brain | LOW | HIGH | Raft consensus requires quorum (2 of 3 nodes); 1-region failure still has quorum |
| GeoDNS misconfiguration routes to unhealthy region | LOW | MEDIUM | Health-check anchored routing; fallback to round-robin on all-unhealthy |
| Cloud-native agent stranded work items on region failure | MEDIUM | MEDIUM | Work items reclaimed by engine after `<agentBinding>` timeout; human fallback (ADR-019) |
| CockroachDB egress costs (cross-cloud replication) | MEDIUM | LOW | Estimated < $200/month at 1000 agent registrations/day |

## Alternatives Considered

### etcd Federation
etcd provides distributed key-value storage used by Kubernetes. Using etcd directly
for agent registration was considered. Rejected because etcd is designed for small
datasets (< 8GB) and its API is not compatible with the existing REST interface of
`AgentRegistry`. CockroachDB presents a SQL interface that is easier to query with
the existing Java JDBC stack.

### Consul Service Mesh
HashiCorp Consul provides service registry and health checking across cloud providers.
Considered as a replacement for both the `AgentRegistry` and GeoDNS. Rejected because
Consul requires a dedicated agent sidecar per pod (increases resource usage) and adds
operational complexity that is not justified for the agent registration use case. The
existing `AgentRegistry` REST API is simpler and already implemented.

### Single Primary Region with Read Replicas
Run the `AgentRegistry` only in AWS us-east-1, with Azure and GCP agents registering
across the WAN. Rejected because a WAN registration round-trip of 80-120ms for every
agent heartbeat (every 30s per `AgentHealthMonitor`) would cause false positive
health-check failures under network degradation.

## Related ADRs

- ADR-005: SPIFFE/SPIRE (workload identity across clouds, SVID for cross-cloud A2A auth)
- ADR-008: Resilience4j (circuit breakers protecting cross-cloud API calls)
- ADR-014: Clustering (Redis lease protocol within a region)
- ADR-019: Autonomous Agent Framework (agent types this topology deploys)
- ADR-023: MCP/A2A CI/CD Deployment (how these deployments are built and tested)
- ADR-025: Agent Coordination Protocol (how agents in this topology coordinate)

## Implementation Notes

### Enabling Federated Registry in Production

The `AgentRegistryClient` is already implemented in
`org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient`.
Switching from the local in-memory `AgentRegistry` to the federated CockroachDB-backed
registry requires only changing the endpoint URL:

```properties
# application-production.properties
yawl.agent.registry.url=https://registry.yawl.cloud
yawl.agent.registry.region=aws-us-east-1
yawl.agent.registry.prefer-local=true
```

The `AgentRegistryClient` already reads `AGENT_REGISTRY_URL` from the environment.
No code changes are required in agent implementations.

### Health Check Endpoint for GeoDNS

The A2A server's `/.well-known/agent.json` endpoint serves as the GeoDNS health probe.
This is not ideal — a purpose-built health endpoint should be added to `YawlA2AServer`:

```
GET /health/live  → HTTP 200 if process is alive
GET /health/ready → HTTP 200 if YAWL engine connection is established
```

The GeoDNS probe should target `/health/ready` to ensure failed engine connections
remove the region from rotation. See ADR-023 for the YAWL Engine health probe design.

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-18
**Implementation Status:** PLANNED (v6.1.0)
**Review Date:** 2026-08-18

---

**Revision History:**
- 2026-02-18: Initial version
