# ADR-023: MCP/A2A CI/CD Deployment Architecture

## Status
**ACCEPTED**

## Context

YAWL v5.2 ships `YawlMcpServer` (STDIO-transport MCP server) and `YawlA2AServer`
(HTTP REST A2A server), but neither has an automated CI/CD deployment path. The
current state:

- `YawlMcpServer.main()` is launched manually with environment variables
- `YawlA2AServer.main()` binds to a hard-coded port (default 8081) with no
  orchestration or health-check integration
- The `AgentRegistry` runs on port 9090 with no lifecycle management in CI
- No container images exist for either server
- No Kubernetes manifests exist for MCP or A2A workloads

Gaps identified in the existing `CICD_V6_ARCHITECTURE.md`:

1. **MCP server deployment**: MCP uses STDIO transport — how does a CI/CD pipeline
   spawn and manage a STDIO process for integration tests?
2. **A2A server deployment**: A2A uses HTTP — it needs a container, health checks,
   and ingress in CI and production
3. **Agent registry synchronisation**: The in-memory `AgentRegistry` (port 9090) is
   not replicated; CI/CD must seed and verify it
4. **Secret injection**: `YAWL_ENGINE_URL`, `YAWL_PASSWORD`, `A2A_JWT_SECRET`,
   `A2A_API_KEY_MASTER`, and `ZAI_API_KEY` must flow from Vault through CI into
   container environment variables

This ADR defines how MCP servers are launched in CI/CD, how A2A agents are
orchestrated as container workloads, and the integration points with the existing
GitOps pipeline (ArgoCD + Helm).

## Decision

### Pattern 1: MCP Server as Sidecar in CI/CD Pipelines

The MCP server uses STDIO transport — it is fundamentally a process that communicates
over its standard input/output streams. This is incompatible with network-addressed
containers. The correct deployment model for CI/CD is a **managed child process**
launched by the test harness.

**CI/CD integration (GitHub Actions / GitLab CI):**

```
┌──────────────────────────────────────────────────────────────────────────┐
│  CI Job: integration-test-mcp                                            │
│                                                                          │
│  Step 1: Start YAWL Engine (Docker service)                              │
│    docker run -d -p 8080:8080 yawl-engine:$VERSION                       │
│    Wait for /admin/health → HTTP 200                                     │
│                                                                          │
│  Step 2: Launch MCP Server as managed child process                      │
│    java -cp yawl.jar YawlMcpServer &                                     │
│    (YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD from CI secrets)       │
│    PID recorded in $MCP_PID; stdin/stdout piped to test client           │
│                                                                          │
│  Step 3: Run MCP integration tests                                       │
│    MCP client sends JSON-RPC over stdin/stdout of PID $MCP_PID           │
│    Tests: list_tools, call_tool, list_resources, read_resource           │
│                                                                          │
│  Step 4: Tear down                                                       │
│    kill $MCP_PID; docker stop yawl-engine                                │
└──────────────────────────────────────────────────────────────────────────┘
```

**GitHub Actions job fragment:**

```yaml
jobs:
  mcp-integration:
    runs-on: ubuntu-latest
    services:
      yawl-engine:
        image: ghcr.io/yawlfoundation/yawl-engine:${{ env.YAWL_VERSION }}
        ports: ["8080:8080"]
        options: --health-cmd="curl -f http://localhost:8080/admin/health" --health-interval=5s
    env:
      YAWL_ENGINE_URL: http://localhost:8080/yawl
      YAWL_USERNAME: ${{ secrets.YAWL_CI_USERNAME }}
      YAWL_PASSWORD: ${{ secrets.YAWL_CI_PASSWORD }}
    steps:
      - name: Launch MCP server process
        run: |
          java -cp target/yawl-5.2.jar \
            org.yawlfoundation.yawl.integration.mcp.YawlMcpServer &
          echo $! > /tmp/mcp.pid
          sleep 3  # wait for engine connection

      - name: Run MCP integration tests
        run: mvn -pl test -Dtest.group=mcp-integration verify

      - name: Stop MCP server
        if: always()
        run: kill $(cat /tmp/mcp.pid) || true
```

**Why STDIO, not HTTP for MCP?** The MCP Java SDK 0.17.2 `StdioServerTransportProvider`
communicates over process stdin/stdout. This is the standard MCP transport for local
tool use by AI clients (Claude Desktop, Cursor, etc.). A network-transport variant
would require the `SseServerTransportProvider` — see Pattern 4 (Production MCP Gateway)
for the production alternative.

### Pattern 2: A2A Server as Kubernetes Deployment

The A2A server (`YawlA2AServer`) binds to an HTTP port and is suitable for container
deployment. Each A2A server instance runs as a standalone pod with its own
`/.well-known/agent.json` discovery endpoint.

**Container structure:**

```dockerfile
# Dockerfile.a2a (generated by CI pipeline)
FROM eclipse-temurin:25-jre-alpine
LABEL org.opencontainers.image.title="yawl-a2a-server"
LABEL org.opencontainers.image.version="${BUILD_VERSION}"

COPY target/yawl-5.2.jar /app/yawl.jar

# A2A server configuration (all secrets injected at runtime)
ENV YAWL_ENGINE_URL=""
ENV YAWL_USERNAME=""
ENV YAWL_PASSWORD=""
ENV A2A_PORT="8081"
ENV A2A_JWT_SECRET=""
ENV A2A_API_KEY_MASTER=""
ENV A2A_SPIFFE_TRUST_DOMAIN="yawl.cloud"

# Compact object headers + virtual thread optimisation
ENTRYPOINT ["java", \
  "-XX:+UseCompactObjectHeaders", \
  "-XX:+UseZGC", \
  "-Xmx512m", \
  "-cp", "/app/yawl.jar", \
  "org.yawlfoundation.yawl.integration.a2a.YawlA2AServer"]

EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s \
  CMD wget -qO- http://localhost:8081/.well-known/agent.json || exit 1
```

**Kubernetes Deployment manifest (Helm-generated):**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-a2a-server
  labels:
    app.kubernetes.io/name: yawl-a2a-server
    app.kubernetes.io/version: "{{ .Values.image.tag }}"
spec:
  replicas: {{ .Values.a2a.replicas }}
  selector:
    matchLabels:
      app: yawl-a2a-server
  template:
    metadata:
      labels:
        app: yawl-a2a-server
      annotations:
        # SPIFFE SVID injection via SPIRE agent (ADR-005)
        spiffe.io/spiffeid: "spiffe://yawl.cloud/ns/workflow/a2a-server"
    spec:
      containers:
        - name: a2a-server
          image: "{{ .Values.image.registry }}/yawl-a2a-server:{{ .Values.image.tag }}"
          ports:
            - containerPort: 8081
              name: http-a2a
          envFrom:
            - secretRef:
                name: yawl-engine-credentials
          env:
            - name: A2A_PORT
              value: "8081"
            - name: A2A_SPIFFE_TRUST_DOMAIN
              value: "{{ .Values.spiffe.trustDomain }}"
          envFrom:
            - secretRef:
                name: yawl-a2a-auth-secrets
          readinessProbe:
            httpGet:
              path: /.well-known/agent.json
              port: 8081
            initialDelaySeconds: 15
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /.well-known/agent.json
              port: 8081
            failureThreshold: 3
            periodSeconds: 15
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
            limits:
              cpu: 1000m
              memory: 512Mi
```

### Pattern 3: Agent Registry as Shared Infrastructure Service

The `AgentRegistry` (port 9090) is a stateful service — it holds registrations from
all autonomous agents on the node. In CI/CD it must be seeded before integration
tests and torn down after.

**CI/CD registry lifecycle:**

```
┌─────────────────────────────────────────────────────────────────────┐
│  Pipeline Phase: pre-integration-setup                               │
│                                                                      │
│  1. Start AgentRegistry (port 9090) as Docker sidecar               │
│  2. Wait for GET /agents → HTTP 200                                  │
│  3. Register test agents via POST /agents/register                   │
│  4. Verify registration: GET /agents → count == expected             │
│                                                                      │
│  Pipeline Phase: integration-test                                    │
│                                                                      │
│  5. Execute agent integration tests                                  │
│  6. Assert agents discover and complete workflow work items          │
│                                                                      │
│  Pipeline Phase: post-integration-teardown                           │
│                                                                      │
│  7. GET /agents (capture final state for test report)               │
│  8. Stop AgentRegistry container                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**In production** (Kubernetes), the AgentRegistry runs as a StatefulSet with a
persistent volume for registration state snapshots — see ADR-024 for the multi-cloud
deployment.

### Pattern 4: Production MCP Gateway (SSE Transport)

For production deployment where AI clients connect to YAWL over a network (not STDIO),
the MCP server is deployed with SSE (Server-Sent Events) transport behind an API
gateway. This requires the `SseServerTransportProvider` from the MCP SDK.

**Production MCP topology:**

```
┌────────────────────────────────────────────────────────────────┐
│  AI Client (Claude, GPT, custom agent)                         │
└─────────────────────┬──────────────────────────────────────────┘
                      │ HTTPS + Bearer token
                      │
┌─────────────────────▼──────────────────────────────────────────┐
│  API Gateway (Kong / AWS API Gateway / Traefik)                │
│  - Rate limiting: 10 req/min for tool calls (critical tier)    │
│  - JWT validation (ADR-017)                                    │
│  - TLS termination (TLS 1.3 only per security checklist)       │
└─────────────────────┬──────────────────────────────────────────┘
                      │ HTTP (internal)
                      │
┌─────────────────────▼──────────────────────────────────────────┐
│  yawl-mcp-gateway (Kubernetes Deployment, 2+ replicas)         │
│  - SSE transport: GET /mcp/sse (client→server events)          │
│  - POST /mcp/messages (client→server messages)                 │
│  - Session management: Redis-backed (ADR-014)                  │
│  - Metrics: Prometheus /metrics endpoint                       │
└─────────────────────┬──────────────────────────────────────────┘
                      │ Interface B (HTTP)
                      │
┌─────────────────────▼──────────────────────────────────────────┐
│  YAWL Engine cluster (ADR-014)                                 │
└────────────────────────────────────────────────────────────────┘
```

The STDIO transport (`YawlMcpServer.main()`) remains the deployment model for
local Claude Desktop / Cursor integration. The SSE transport is a separate
deployable for cloud-hosted AI agent orchestration.

### Pattern 5: Secret Injection Flow

All credentials flow from HashiCorp Vault through the CI/CD pipeline via the
Kubernetes External Secrets Operator, never embedded in manifests or images.

```
┌──────────────────────────────────────────────────────────────────────┐
│  HashiCorp Vault                                                     │
│  secret/yawl/engine: {url, username, password}                       │
│  secret/yawl/a2a: {jwt_secret, api_key_master, spiffe_domain}        │
│  secret/yawl/mcp: {zai_api_key}                                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            │ OIDC workload identity (ADR-005)
                            │
┌───────────────────────────▼──────────────────────────────────────────┐
│  External Secrets Operator                                           │
│  ExternalSecret → Kubernetes Secret → Pod env injection              │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
       ┌────────────────────┼────────────────────────┐
       │                    │                        │
┌──────▼──────┐     ┌──────▼──────┐         ┌──────▼──────┐
│ A2A Server  │     │ MCP Gateway │         │Agent Registry│
│ Pod env     │     │ Pod env     │         │ Pod env     │
└─────────────┘     └─────────────┘         └─────────────┘
```

**Environment variable naming convention:**

| Secret | Variable | Target |
|--------|----------|--------|
| Engine URL | `YAWL_ENGINE_URL` | All servers |
| Engine credentials | `YAWL_USERNAME`, `YAWL_PASSWORD` | All servers |
| A2A JWT key | `A2A_JWT_SECRET` | A2A Server |
| A2A API key | `A2A_API_KEY_MASTER`, `A2A_API_KEY` | A2A Server |
| SPIFFE domain | `A2A_SPIFFE_TRUST_DOMAIN` | A2A Server |
| Z.AI API key | `ZAI_API_KEY` | MCP Server, A2A Server |

### CI/CD Stage Ordering

The following stage ordering is enforced by the pipeline DAG. No stage may run
until its predecessors reach `SUCCESS`.

```
compile
    │
    ▼
unit-test ──────────────────────────────────────────┐
    │                                               │
    ▼                                               ▼
build-images                                   security-scan
    │                                               │
    ▼                                               │
push-images ────────────────────────────────────────┘
    │
    ▼
start-ci-services          ← YAWL Engine + AgentRegistry + A2A Server
    │
    ▼
seed-agent-registry        ← POST /agents/register for each test agent
    │
    ▼
integration-test           ← MCP process tests + A2A HTTP tests
    │
    ▼
teardown-ci-services
    │
    ▼
performance-test           ← Optional, only on develop/main branches
    │
    ▼
deploy-staging             ← ArgoCD sync (staging environment)
    │
    ▼
deploy-production          ← ArgoCD sync (production, manual gate)
```

## Consequences

### Positive

1. STDIO-transport MCP tests are fully automated without requiring network changes
   to the MCP SDK
2. A2A server has a clean container boundary, health checks, and resource limits
   from the first deployment
3. Secret injection from Vault eliminates secrets from CI logs, manifests, and images
4. The stage DAG ensures integration tests never run against an unseeded registry
5. Production MCP gateway (SSE) supports multiple concurrent AI client connections
   without process-per-client overhead

### Negative

1. STDIO MCP tests require careful PID management in CI — process leaks if the test
   job is cancelled mid-execution
2. The SSE-transport MCP gateway requires additional implementation work (the SDK
   supports SSE but `YawlMcpServer` currently only configures STDIO transport)
3. The AgentRegistry must be seeded with test data for each CI run — seed scripts
   must be kept in sync with the actual agent configurations

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| MCP STDIO process leak in cancelled CI | MEDIUM | LOW | Use `always()` teardown step with `pkill -f YawlMcpServer` |
| A2A health-check race on cold start | LOW | MEDIUM | `initialDelaySeconds: 15` covers engine connection latency |
| Vault unavailability blocks deployments | LOW | HIGH | Vault HA cluster with 3 replicas per region |
| Stage ordering violated by parallel branches | LOW | HIGH | Enforce via `needs:` in GitHub Actions / `depends:` in GitLab CI |

## Alternatives Considered

### Run MCP Server as HTTP-transport in CI
Would require `SseServerTransportProvider` and an HTTP endpoint in CI. Rejected for
the basic integration test scenario because it introduces an extra transport layer.
STDIO is the canonical transport for local testing with the official SDK.

### Use Testcontainers for A2A Server
Testcontainers provides programmatic Docker management from JUnit tests. Considered
for the A2A server startup/teardown. Rejected in favour of the CI service container
approach because CI service containers have native health-check integration and do
not require Testcontainers as a build dependency.

### Embed AgentRegistry in Engine WAR
Rejected. The AgentRegistry is an independent operational component — embedding it
in the engine WAR couples the engine startup/shutdown to agent lifecycle and prevents
independent scaling.

## Related ADRs

- ADR-005: SPIFFE/SPIRE (workload identity for Vault OIDC)
- ADR-014: Clustering (Redis-backed session management for SSE MCP gateway)
- ADR-017: Authentication (JWT validation in API gateway)
- ADR-019: Autonomous Agent Framework (agent lifecycle this pipeline deploys)
- ADR-024: Multi-Cloud Agent Deployment Topology (cloud-specific deployment details)

## Implementation Notes

### MCP Process Test Helper

```java
// src/test/java/org/yawlfoundation/yawl/integration/mcp/McpProcessTestHelper.java
public final class McpProcessTestHelper implements AutoCloseable {

    private final Process mcpProcess;

    public McpProcessTestHelper(String yawlEngineUrl,
                                String username,
                                String password) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", "target/yawl.jar",
            "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"
        );
        pb.environment().put("YAWL_ENGINE_URL", yawlEngineUrl);
        pb.environment().put("YAWL_USERNAME", username);
        pb.environment().put("YAWL_PASSWORD", password);
        pb.redirectErrorStream(false);
        this.mcpProcess = pb.start();

        // Wait for server-ready signal on stderr
        waitForReadySignal(mcpProcess.errorStream());
    }

    public OutputStream getServerInput()  { return mcpProcess.getOutputStream(); }
    public InputStream  getServerOutput() { return mcpProcess.getInputStream(); }

    @Override
    public void close() {
        mcpProcess.destroy();
    }

    private void waitForReadySignal(InputStream stderr) throws IOException {
        // Read stderr until "YAWL MCP Server v..." line appears
        // Throws IOException if process exits before ready signal
        try (var reader = new BufferedReader(new InputStreamReader(stderr))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("started on STDIO transport")) {
                    return;
                }
            }
        }
        throw new IOException("MCP server failed to start");
    }
}
```

### ArgoCD Application for A2A Server

```yaml
# gitops/apps/yawl-a2a-server.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: yawl-a2a-server
  namespace: argocd
spec:
  project: yawl-workflow
  source:
    repoURL: https://github.com/yawlfoundation/yawl-helm-charts
    targetRevision: HEAD
    path: charts/yawl-a2a-server
    helm:
      valueFiles:
        - values-production.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: yawl-workflow
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-18
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-18

---

**Revision History:**
- 2026-02-18: Initial version
