# PRD: YAWL MCP Infrastructure Integration

**Product Requirements Document**
**Version:** 1.0
**Date:** February 15, 2026
**Author:** YAWL Foundation
**Status:** Draft

---

## 1. Executive Summary

YAWL 5.2 already exposes workflows via MCP (`YawlMcpServer`) and connects to external MCP servers (`YawlMcpClient`). This PRD defines the integration of **production MCP servers for Docker, Kubernetes, Terraform, and cloud providers** into YAWL's workflow engine, enabling AI-orchestrated infrastructure operations as first-class workflow tasks.

The goal: a YAWL workflow specification can declare tasks that build Docker images, deploy Helm charts, scale Kubernetes clusters, provision Terraform infrastructure, and query Grafana dashboards -- all through standardized MCP tool calls, with Z.AI intelligence for decision-making at each step.

### Business Value

| Metric | Current State | Target State |
|--------|---------------|--------------|
| Infrastructure operations in workflows | Manual scripts, custom code | MCP tool calls via standard protocol |
| Cloud provider support | 5 clouds (static Terraform/Helm) | 5 clouds + dynamic MCP-driven operations |
| AI-assisted infrastructure decisions | None | Z.AI analyzes metrics, recommends actions |
| Time to add new infrastructure capability | Days (code new integration) | Hours (register MCP server) |
| Workflow-driven GitOps | ArgoCD config only | Full lifecycle via MCP tools |

---

## 2. Problem Statement

YAWL 5.2 has extensive static infrastructure-as-code (Terraform modules, Helm charts, K8s manifests, CI/CD pipelines for 5 clouds). However:

1. **Infrastructure operations are not workflow tasks.** Deploying a Helm chart requires running a script outside the workflow engine. There is no way to model "deploy to staging, wait for health check, promote to production" as a YAWL net.

2. **No runtime infrastructure intelligence.** The engine cannot query Kubernetes cluster state, check container health, or read Grafana dashboards during workflow execution to make dynamic routing decisions.

3. **MCP ecosystem is mature but disconnected.** Production-ready MCP servers exist for Docker (~675 stars), Kubernetes (~1,100 stars), Terraform (~1,200 stars), all major clouds (AWS 8,200+ stars), and Grafana (~2,300 stars). YAWL's `YawlMcpClient` can connect to them but lacks the plumbing to use them as workflow task executors.

4. **No unified tool registry.** Each MCP server exposes different tools. There is no catalog that maps YAWL task types to MCP server capabilities.

---

## 3. Goals and Non-Goals

### Goals

1. **G1:** Register external MCP servers (Docker, K8s, Terraform, cloud, observability) as YAWL custom service endpoints
2. **G2:** Enable YAWL workflow tasks to invoke MCP tools through InterfaceB work items
3. **G3:** Build an MCP Tool Registry that maps tool names to server connections
4. **G4:** Integrate Z.AI for intelligent tool selection and parameter generation
5. **G5:** Provide pre-built YAWL workflow specifications for common infrastructure patterns (build-deploy-verify, blue-green, canary, rollback)
6. **G6:** Support all 5 existing cloud targets (AWS, Azure, GCP, Oracle, IBM) through their respective MCP servers

### Non-Goals

- Building new MCP servers from scratch (use existing ecosystem)
- Replacing Terraform/Helm/ArgoCD (augment them with MCP orchestration)
- Modifying the YAWL engine core (`YEngine.java`, `YNetRunner.java`)
- Supporting MCP servers that require non-Java runtimes in-process (they run as sidecar containers)

---

## 4. Architecture Overview

```
                    ┌──────────────────────────────────────────────┐
                    │              YAWL Engine 5.2                  │
                    │  ┌────────────────────────────────────────┐  │
                    │  │         YNetRunner (unchanged)          │  │
                    │  │   Executes workflow nets as before      │  │
                    │  └──────────┬─────────────────────────────┘  │
                    │             │ work item dispatch              │
                    │  ┌──────────▼─────────────────────────────┐  │
                    │  │     McpTaskExecutionService (NEW)       │  │
                    │  │  - Receives work items tagged [mcp:*]  │  │
                    │  │  - Resolves tool → MCP server           │  │
                    │  │  - Marshals XML data → MCP JSON args   │  │
                    │  │  - Calls MCP server via YawlMcpClient  │  │
                    │  │  - Marshals MCP result → XML output    │  │
                    │  │  - Completes work item via InterfaceB  │  │
                    │  └──────────┬─────────────────────────────┘  │
                    │             │                                 │
                    │  ┌──────────▼─────────────────────────────┐  │
                    │  │       McpToolRegistry (NEW)             │  │
                    │  │  - Tool name → MCP server mapping      │  │
                    │  │  - Server health monitoring             │  │
                    │  │  - Connection pool management           │  │
                    │  │  - Tool capability discovery            │  │
                    │  └──────────┬─────────────────────────────┘  │
                    │             │                                 │
                    │  ┌──────────▼─────────────────────────────┐  │
                    │  │       ZaiToolAdvisor (NEW)              │  │
                    │  │  - Analyzes task context with Z.AI     │  │
                    │  │  - Recommends tools + parameters       │  │
                    │  │  - Validates results before completion │  │
                    │  └─────────────────────────────────────────┘  │
                    └──────────────┬────────────────────────────────┘
                                   │ MCP protocol (JSON-RPC / HTTP)
                    ┌──────────────▼────────────────────────────────┐
                    │           MCP Server Fleet (Sidecars)          │
                    │                                                │
                    │  ┌─────────────┐  ┌──────────────────────┐   │
                    │  │   Docker    │  │    Kubernetes         │   │
                    │  │  MCP Server │  │    MCP Server         │   │
                    │  │ (ckreiling) │  │  (Flux159/containers) │   │
                    │  └─────────────┘  └──────────────────────┘   │
                    │                                                │
                    │  ┌─────────────┐  ┌──────────────────────┐   │
                    │  │  Terraform  │  │     Grafana           │   │
                    │  │  MCP Server │  │    MCP Server         │   │
                    │  │ (HashiCorp) │  │   (grafana/mcp)       │   │
                    │  └─────────────┘  └──────────────────────┘   │
                    │                                                │
                    │  ┌─────────────┐  ┌──────────────────────┐   │
                    │  │  AWS MCP    │  │  GitHub MCP           │   │
                    │  │  Servers    │  │  Server               │   │
                    │  │  (awslabs)  │  │  (github/github-mcp)  │   │
                    │  └─────────────┘  └──────────────────────┘   │
                    │                                                │
                    │  ┌─────────────┐  ┌──────────────────────┐   │
                    │  │  GCP MCP    │  │  Azure MCP            │   │
                    │  │ (googleapis)│  │  (Azure/blue-bridge)  │   │
                    │  └─────────────┘  └──────────────────────┘   │
                    └───────────────────────────────────────────────┘
```

---

## 5. Target MCP Servers

### 5.1 Docker Operations

| Server | GitHub | Purpose | Priority |
|--------|--------|---------|----------|
| **ckreiling/mcp-server-docker** | 675 stars, Python, GPL-3.0 | Container lifecycle, images, volumes, networks, Compose | **P0** |
| **docker/hub-mcp** | 122 stars, TypeScript, Apache-2.0 | Docker Hub image search, recommendations | P1 |
| **manusa/podman-mcp-server** | 57 stars, Go, Apache-2.0 | Podman + Docker dual-runtime support | P2 |

**Tools to expose in YAWL workflows:**

```
docker.container.create     - Create container from image
docker.container.start      - Start stopped container
docker.container.stop       - Stop running container
docker.container.remove     - Remove container
docker.container.list       - List containers with filters
docker.container.logs       - Fetch container logs
docker.container.inspect    - Get container details
docker.image.build          - Build image from Dockerfile
docker.image.pull           - Pull image from registry
docker.image.push           - Push image to registry
docker.image.list           - List local images
docker.compose.up           - Start Compose stack
docker.compose.down         - Stop Compose stack
docker.compose.ps           - List Compose services
docker.volume.create        - Create named volume
docker.network.create       - Create Docker network
```

### 5.2 Kubernetes Operations

| Server | GitHub | Purpose | Priority |
|--------|--------|---------|----------|
| **Flux159/mcp-server-kubernetes** | 1,100 stars, TypeScript | Full K8s + Helm management | **P0** |
| **containers/kubernetes-mcp-server** | 977 stars, Go (Red Hat) | Native K8s API, OpenShift, multi-cluster | **P0** |
| **alexei-led/k8s-mcp-server** | 191 stars, Python | kubectl + helm + istioctl + argocd bundle | P1 |

**Tools to expose in YAWL workflows:**

```
k8s.pod.list                - List pods (namespace, labels, status)
k8s.pod.get                 - Get pod details
k8s.pod.logs                - Stream pod logs
k8s.pod.exec                - Execute command in pod
k8s.pod.delete              - Delete pod
k8s.deployment.create       - Create deployment
k8s.deployment.scale        - Scale replicas
k8s.deployment.rollout      - Trigger rolling update
k8s.deployment.rollback     - Rollback to previous revision
k8s.deployment.status       - Get rollout status
k8s.service.create          - Create/update service
k8s.service.list            - List services
k8s.namespace.create        - Create namespace
k8s.namespace.list          - List namespaces
k8s.node.list               - List cluster nodes
k8s.node.cordon             - Cordon node (no new pods)
k8s.node.drain              - Drain node for maintenance
k8s.resource.apply          - Apply arbitrary YAML manifest
k8s.resource.get            - Get any K8s resource by kind/name
k8s.resource.delete         - Delete any K8s resource
k8s.events.list             - List cluster/namespace events
k8s.context.switch          - Switch cluster context
helm.install                - Install Helm chart
helm.upgrade                - Upgrade Helm release
helm.rollback               - Rollback Helm release
helm.uninstall              - Uninstall Helm release
helm.list                   - List Helm releases
helm.status                 - Get release status
helm.template               - Render chart templates locally
```

### 5.3 Infrastructure-as-Code

| Server | GitHub | Purpose | Priority |
|--------|--------|---------|----------|
| **hashicorp/terraform-mcp-server** | 1,200 stars, Official | Provider docs, modules, registry | **P0** |
| **@pulumi/mcp-server** | Official Pulumi | Stack management, previews, deploys | P1 |

**Tools to expose in YAWL workflows:**

```
terraform.provider.search   - Search Terraform Registry for providers
terraform.provider.docs     - Get provider resource documentation
terraform.module.search     - Search modules in registry
terraform.module.details    - Get module inputs/outputs/examples
terraform.policy.search     - Search Sentinel policies
terraform.workspace.list    - List TFC/TFE workspaces
terraform.workspace.runs    - Get workspace run history
pulumi.stack.list           - List Pulumi stacks
pulumi.stack.preview        - Preview infrastructure changes
pulumi.stack.deploy         - Deploy infrastructure updates
pulumi.stack.outputs        - Get stack outputs
```

### 5.4 Cloud Provider Operations

| Server | GitHub | Purpose | Priority |
|--------|--------|---------|----------|
| **awslabs/mcp** | 8,200 stars, Official AWS | 20+ AWS service servers | **P0** |
| **googleapis/gcloud-mcp** | Official Google | gcloud CLI via MCP | P1 |
| **Azure/blue-bridge** | Official Microsoft | Azure resource management | P1 |

**Tools to expose in YAWL workflows:**

```
aws.eks.cluster.status      - EKS cluster health
aws.eks.nodegroup.scale     - Scale EKS node groups
aws.rds.instance.status     - RDS database status
aws.s3.object.put           - Upload to S3
aws.lambda.invoke           - Invoke Lambda function
aws.cloudwatch.metrics      - Query CloudWatch metrics
gcp.gke.cluster.status      - GKE cluster health
gcp.cloudsql.instance.status - CloudSQL database status
gcp.gcs.object.upload       - Upload to GCS
azure.aks.cluster.status    - AKS cluster health
azure.cosmosdb.query        - Query Cosmos DB
azure.blob.upload           - Upload to Blob Storage
```

### 5.5 Observability

| Server | GitHub | Purpose | Priority |
|--------|--------|---------|----------|
| **grafana/mcp-grafana** | 2,300 stars, Official | Dashboards, datasources, incidents, alerts | **P0** |

**Tools to expose in YAWL workflows:**

```
grafana.dashboard.search    - Search dashboards
grafana.dashboard.get       - Get dashboard panels/data
grafana.datasource.query    - Query any Grafana datasource
grafana.alert.list          - List firing alerts
grafana.alert.silence       - Silence an alert
grafana.incident.create     - Create incident
grafana.incident.update     - Update incident status
```

### 5.6 Source Control & CI/CD

| Server | GitHub | Purpose | Priority |
|--------|--------|---------|----------|
| **github/github-mcp-server** | Official GitHub | Repos, PRs, issues, Actions | P1 |

**Tools to expose in YAWL workflows:**

```
github.pr.create            - Create pull request
github.pr.merge             - Merge pull request
github.pr.review            - Add PR review
github.issue.create         - Create issue
github.issue.close          - Close issue
github.actions.trigger      - Trigger workflow run
github.actions.status       - Get workflow run status
github.actions.cancel       - Cancel workflow run
github.release.create       - Create release
```

---

## 6. New Java Components

### 6.1 McpToolRegistry

**Package:** `org.yawlfoundation.yawl.integration.mcp`
**File:** `McpToolRegistry.java`

Maintains a registry of MCP servers and their available tools.

```
Responsibilities:
- Load MCP server configurations from YAML/XML config file
- Discover available tools from each connected server (tools/list)
- Map canonical tool names (e.g., "docker.container.start") to server connections
- Monitor server health with periodic heartbeat
- Manage connection pool (reuse YawlMcpClient instances)
- Provide tool lookup by name, category, or capability
- Support hot-reload of server configurations

Configuration source: mcp-servers.xml (in YAWL engine config directory)

Key methods:
  registerServer(String name, String transport, String endpoint, Map<String,String> auth)
  discoverTools(String serverName) -> List<McpToolDescriptor>
  resolveServer(String toolName) -> McpServerConnection
  getToolDescriptor(String toolName) -> McpToolDescriptor
  listToolsByCategory(String category) -> List<McpToolDescriptor>
  healthCheck() -> Map<String, ServerStatus>
  reload()
```

### 6.2 McpTaskExecutionService

**Package:** `org.yawlfoundation.yawl.integration.mcp`
**File:** `McpTaskExecutionService.java`

Implements YAWL InterfaceB custom service that executes MCP tools as workflow tasks.

```
Responsibilities:
- Register as YAWL custom service via InterfaceB
- Receive work items tagged with MCP tool references
- Extract tool name and parameters from work item XML data
- Resolve tool to MCP server via McpToolRegistry
- Marshal YAWL XML parameters to MCP JSON arguments
- Execute MCP tool call via YawlMcpClient
- Marshal MCP JSON result back to YAWL XML output
- Complete work item with output data via InterfaceB
- Handle timeouts, retries, and error propagation
- Log all tool invocations for audit trail

Work item convention:
  Task decomposition variable "mcp_tool" = tool name
  Task decomposition variable "mcp_args" = JSON arguments
  Task output variable "mcp_result" = JSON result
  Task output variable "mcp_status" = success/failure
  Task output variable "mcp_error" = error message (if failed)

Key methods:
  handleEnabledWorkItemEvent(WorkItemRecord wir)
  executeMcpTool(String toolName, String argsJson) -> McpToolResult
  marshalXmlToJson(String xmlData, McpToolDescriptor tool) -> String
  marshalJsonToXml(String jsonResult) -> String
```

### 6.3 ZaiToolAdvisor

**Package:** `org.yawlfoundation.yawl.integration.mcp`
**File:** `ZaiToolAdvisor.java`

Uses Z.AI to provide intelligent tool selection and parameter generation.

```
Responsibilities:
- Given a task description, recommend which MCP tool to use
- Given a tool and partial parameters, generate complete parameters
- Validate MCP tool results against expected outcomes
- Suggest remediation actions when tools fail
- Provide natural language summaries of tool execution results

Key methods:
  recommendTool(String taskDescription, List<McpToolDescriptor> available) -> ToolRecommendation
  generateParameters(String toolName, String context, Map<String,String> partialArgs) -> String
  validateResult(String toolName, String result, String expectedOutcome) -> ValidationResult
  suggestRemediation(String toolName, String error, String context) -> String
  summarizeExecution(String toolName, String args, String result) -> String
```

### 6.4 McpToolDescriptor

**Package:** `org.yawlfoundation.yawl.integration.mcp`
**File:** `McpToolDescriptor.java`

Data class describing an MCP tool's capabilities.

```
Fields:
  String name               - Canonical name (e.g., "docker.container.start")
  String serverName         - MCP server that provides this tool
  String description        - Human-readable description
  String category           - Category (docker, kubernetes, terraform, cloud, observability)
  Map<String,ParameterSpec> inputSchema  - Input parameter specifications
  Map<String,ParameterSpec> outputSchema - Output field specifications
  boolean destructive       - Whether tool modifies state
  boolean requiresConfirmation - Whether human approval is needed
  Duration timeout          - Expected execution timeout
```

### 6.5 McpServerConnection

**Package:** `org.yawlfoundation.yawl.integration.mcp`
**File:** `McpServerConnection.java`

Manages a connection to a single MCP server.

```
Fields:
  String name               - Server name
  String transport          - "stdio" | "sse" | "streamable-http"
  String endpoint           - URL or command
  Map<String,String> auth   - Authentication parameters
  YawlMcpClient client      - Underlying MCP client
  ServerStatus status       - CONNECTED | DISCONNECTED | ERROR
  Instant lastHealthCheck   - Last successful health check
  List<McpToolDescriptor> tools - Discovered tools

Key methods:
  connect()
  disconnect()
  callTool(String toolName, String argsJson) -> String
  healthCheck() -> boolean
  refreshTools()
```

---

## 7. MCP Server Configuration

### 7.1 Configuration File: `mcp-servers.xml`

Located in the YAWL engine configuration directory, this file declares which MCP servers are available.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mcp-servers>

  <!-- Docker Operations -->
  <server name="docker" transport="stdio" enabled="true">
    <command>uvx mcp-server-docker</command>
    <category>docker</category>
    <env>
      <var name="DOCKER_HOST" value="unix:///var/run/docker.sock"/>
    </env>
    <healthCheck intervalSeconds="60"/>
    <timeout seconds="120"/>
  </server>

  <!-- Docker Hub -->
  <server name="docker-hub" transport="stdio" enabled="true">
    <command>npx @docker/hub-mcp</command>
    <category>docker</category>
    <env>
      <var name="DOCKER_HUB_TOKEN" from="env"/>
    </env>
  </server>

  <!-- Kubernetes (TypeScript - Flux159) -->
  <server name="kubernetes" transport="stdio" enabled="true">
    <command>npx mcp-server-kubernetes</command>
    <category>kubernetes</category>
    <env>
      <var name="KUBECONFIG" value="/etc/yawl/kubeconfig"/>
    </env>
    <healthCheck intervalSeconds="30"/>
    <timeout seconds="300"/>
  </server>

  <!-- Kubernetes (Go - Red Hat / containers org) -->
  <server name="kubernetes-native" transport="stdio" enabled="false">
    <command>kubernetes-mcp-server</command>
    <category>kubernetes</category>
    <args>--read-only=false --kubeconfig=/etc/yawl/kubeconfig</args>
  </server>

  <!-- Multi-tool K8s (kubectl + helm + istioctl + argocd) -->
  <server name="k8s-multi" transport="streamable-http" enabled="false">
    <endpoint>http://k8s-mcp:8080/mcp</endpoint>
    <category>kubernetes</category>
    <docker>
      <image>ghcr.io/alexei-led/k8s-mcp-server:latest</image>
      <volumes>/etc/yawl/kubeconfig:/home/appuser/.kube/config:ro</volumes>
    </docker>
  </server>

  <!-- Terraform (HashiCorp Official) -->
  <server name="terraform" transport="stdio" enabled="true">
    <command>npx @hashicorp/terraform-mcp-server</command>
    <category>terraform</category>
    <env>
      <var name="TFC_TOKEN" from="env"/>
    </env>
    <timeout seconds="60"/>
  </server>

  <!-- Pulumi -->
  <server name="pulumi" transport="stdio" enabled="false">
    <command>npx @pulumi/mcp-server</command>
    <category>terraform</category>
    <env>
      <var name="PULUMI_ACCESS_TOKEN" from="env"/>
    </env>
  </server>

  <!-- AWS (Core MCP Server) -->
  <server name="aws" transport="stdio" enabled="true">
    <command>uvx awslabs.core-mcp-server</command>
    <category>cloud</category>
    <env>
      <var name="AWS_REGION" value="us-east-1"/>
      <var name="AWS_PROFILE" from="env"/>
    </env>
    <timeout seconds="180"/>
  </server>

  <!-- AWS EKS -->
  <server name="aws-eks" transport="stdio" enabled="false">
    <command>uvx awslabs.eks-mcp-server</command>
    <category>cloud</category>
    <env>
      <var name="AWS_REGION" from="env"/>
    </env>
  </server>

  <!-- GCP -->
  <server name="gcp" transport="stdio" enabled="false">
    <command>npx @googleapis/gcloud-mcp</command>
    <category>cloud</category>
    <env>
      <var name="GOOGLE_APPLICATION_CREDENTIALS" value="/etc/yawl/gcp-key.json"/>
    </env>
  </server>

  <!-- Azure -->
  <server name="azure" transport="stdio" enabled="false">
    <command>npx @azure/blue-bridge-mcp</command>
    <category>cloud</category>
    <env>
      <var name="AZURE_SUBSCRIPTION_ID" from="env"/>
    </env>
  </server>

  <!-- Grafana (Official) -->
  <server name="grafana" transport="stdio" enabled="true">
    <command>grafana-mcp-server</command>
    <category>observability</category>
    <env>
      <var name="GRAFANA_URL" value="http://grafana:3000"/>
      <var name="GRAFANA_API_KEY" from="env"/>
    </env>
    <args>--transport stdio</args>
    <timeout seconds="60"/>
  </server>

  <!-- GitHub (Official) -->
  <server name="github" transport="stdio" enabled="true">
    <command>npx @github/mcp-server</command>
    <category>cicd</category>
    <env>
      <var name="GITHUB_TOKEN" from="env"/>
    </env>
  </server>

</mcp-servers>
```

### 7.2 Docker Compose Extension

Add MCP server sidecars to `docker-compose.yml`:

```yaml
services:
  # MCP Server sidecars (profile: mcp)
  mcp-docker:
    image: python:3.12-slim
    command: pip install mcp-server-docker && mcp-server-docker --transport sse --port 3001
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    ports:
      - "3001:3001"
    profiles: ["mcp"]

  mcp-kubernetes:
    image: node:22-slim
    command: npx mcp-server-kubernetes
    volumes:
      - ${KUBECONFIG:-~/.kube/config}:/home/node/.kube/config:ro
    profiles: ["mcp"]

  mcp-k8s-multi:
    image: ghcr.io/alexei-led/k8s-mcp-server:latest
    volumes:
      - ${KUBECONFIG:-~/.kube/config}:/home/appuser/.kube/config:ro
    ports:
      - "3002:8080"
    profiles: ["mcp"]

  mcp-grafana:
    image: grafana/mcp-grafana:latest
    environment:
      - GRAFANA_URL=http://grafana:3000
      - GRAFANA_API_KEY=${GRAFANA_API_KEY}
    profiles: ["mcp", "observability"]
```

### 7.3 Kubernetes Deployment

Add MCP server sidecar containers to the YAWL engine deployment:

```yaml
# k8s/base/deployments/mcp-sidecar-patch.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
spec:
  template:
    spec:
      containers:
        - name: mcp-kubernetes
          image: ghcr.io/alexei-led/k8s-mcp-server:latest
          ports:
            - containerPort: 8080
              name: mcp-k8s
          volumeMounts:
            - name: kubeconfig
              mountPath: /home/appuser/.kube/config
              subPath: config
              readOnly: true
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi

        - name: mcp-grafana
          image: grafana/mcp-grafana:latest
          env:
            - name: GRAFANA_URL
              valueFrom:
                configMapKeyRef:
                  name: yawl-config
                  key: GRAFANA_URL
            - name: GRAFANA_API_KEY
              valueFrom:
                secretKeyRef:
                  name: yawl-secrets
                  key: grafana-api-key
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 256Mi
```

---

## 8. Workflow Specification Patterns

### 8.1 Docker Build-and-Deploy Workflow

A YAWL specification that builds a Docker image, pushes it, and deploys via Kubernetes.

```
Net: DockerBuildDeploy
  ├── [condition] i_start
  ├── [task] BuildImage
  │     mcp_tool: docker.image.build
  │     mcp_args: {"context": "./", "dockerfile": "Dockerfile.engine", "tag": "${IMAGE_TAG}"}
  ├── [task] ScanImage
  │     mcp_tool: docker.image.inspect
  │     mcp_args: {"image": "${IMAGE_TAG}"}
  ├── [task] PushImage
  │     mcp_tool: docker.image.push
  │     mcp_args: {"image": "${IMAGE_TAG}", "registry": "${REGISTRY}"}
  ├── [task] DeployToK8s
  │     mcp_tool: helm.upgrade
  │     mcp_args: {"release": "yawl", "chart": "./helm/yawl", "set": {"image.tag": "${IMAGE_TAG}"}}
  ├── [task] VerifyDeployment
  │     mcp_tool: k8s.deployment.status
  │     mcp_args: {"name": "yawl-engine", "namespace": "yawl"}
  ├── [condition] o_end
  │
  Variables:
    IMAGE_TAG: string (input)
    REGISTRY: string (input)
    build_result: string (output of BuildImage)
    deploy_status: string (output of VerifyDeployment)
```

### 8.2 Blue-Green Deployment Workflow

```
Net: BlueGreenDeploy
  ├── [condition] i_start
  ├── [task] GetCurrentColor
  │     mcp_tool: k8s.service.get
  │     mcp_args: {"name": "yawl-engine", "namespace": "yawl"}
  ├── [XOR split] DetermineTargetColor
  │     → Blue path (if current is green)
  │     → Green path (if current is blue)
  ├── [task] DeployToInactive
  │     mcp_tool: helm.upgrade
  │     mcp_args: {"release": "yawl-${TARGET_COLOR}", ...}
  ├── [task] RunSmokeTests
  │     mcp_tool: k8s.pod.exec
  │     mcp_args: {"pod": "smoke-test-runner", "command": ["./smoke-test.sh"]}
  ├── [XOR split] SmokeTestResult
  │     → Pass: SwitchTraffic
  │     → Fail: Rollback
  ├── [task] SwitchTraffic
  │     mcp_tool: k8s.service.update
  │     mcp_args: {"name": "yawl-engine", "selector": {"color": "${TARGET_COLOR}"}}
  ├── [task] VerifyLive
  │     mcp_tool: grafana.datasource.query
  │     mcp_args: {"datasource": "prometheus", "query": "up{job='yawl-engine'}"}
  ├── [task] ScaleDownOld
  │     mcp_tool: k8s.deployment.scale
  │     mcp_args: {"name": "yawl-${OLD_COLOR}", "replicas": 0}
  ├── [task] Rollback
  │     mcp_tool: helm.rollback
  │     mcp_args: {"release": "yawl-${TARGET_COLOR}"}
  ├── [condition] o_end
```

### 8.3 Multi-Cloud Provisioning Workflow

```
Net: MultiCloudProvision
  ├── [condition] i_start
  ├── [AND split] ProvisionAllClouds (parallel)
  │     ├── [task] ProvisionAWS
  │     │     mcp_tool: aws.eks.create_cluster  (or terraform.workspace.run)
  │     ├── [task] ProvisionGCP
  │     │     mcp_tool: gcp.gke.create_cluster
  │     ├── [task] ProvisionAzure
  │     │     mcp_tool: azure.aks.create_cluster
  ├── [AND join] AllProvisioned
  ├── [AND split] DeployToAllClusters (parallel)
  │     ├── [task] DeployToEKS
  │     │     mcp_tool: helm.install
  │     │     mcp_args: {"release": "yawl", "values": "values-aws.yaml"}
  │     ├── [task] DeployToGKE
  │     │     mcp_tool: helm.install
  │     │     mcp_args: {"release": "yawl", "values": "values-gcp.yaml"}
  │     ├── [task] DeployToAKS
  │     │     mcp_tool: helm.install
  │     │     mcp_args: {"release": "yawl", "values": "values-azure.yaml"}
  ├── [AND join] AllDeployed
  ├── [task] ConfigureFederation
  │     mcp_tool: k8s.resource.apply
  │     mcp_args: {"manifest": "k8s/federation/multi-cluster-federation.yaml"}
  ├── [condition] o_end
```

### 8.4 Self-Healing Infrastructure Workflow

```
Net: InfrastructureSelfHealing
  ├── [condition] i_start
  ├── [task] MonitorAlerts
  │     mcp_tool: grafana.alert.list
  │     mcp_args: {"state": "firing"}
  ├── [XOR split] AlertType
  │     → PodCrashLoop: HandlePodFailure
  │     → HighLatency: HandleScaling
  │     → DiskPressure: HandleStorage
  │     → NodeNotReady: HandleNode
  ├── [task] HandlePodFailure
  │     mcp_tool: k8s.pod.delete
  │     mcp_args: {"name": "${FAILING_POD}", "namespace": "yawl"}
  ├── [task] HandleScaling
  │     mcp_tool: k8s.deployment.scale
  │     mcp_args: {"name": "${DEPLOYMENT}", "replicas": "${CURRENT + 2}"}
  ├── [task] HandleStorage
  │     mcp_tool: k8s.resource.apply
  │     mcp_args: {"manifest": "expand-pvc.yaml"}
  ├── [task] HandleNode
  │     mcp_tool: k8s.node.cordon
  │     mcp_args: {"name": "${NODE_NAME}"}
  ├── [task] AISuggestRemediation
  │     Uses ZaiToolAdvisor to analyze alert context
  │     and recommend next action
  ├── [task] VerifyResolution
  │     mcp_tool: grafana.alert.list
  │     mcp_args: {"state": "firing"}
  ├── [XOR split] Resolved?
  │     → Yes: SilenceAlert
  │     → No: EscalateToHuman
  ├── [condition] o_end
```

### 8.5 GitOps Release Pipeline

```
Net: GitOpsRelease
  ├── [condition] i_start
  ├── [task] CreateReleaseBranch
  │     mcp_tool: github.branch.create
  │     mcp_args: {"repo": "yawl", "branch": "release/${VERSION}"}
  ├── [task] RunTests
  │     mcp_tool: github.actions.trigger
  │     mcp_args: {"workflow": "build.yaml", "ref": "release/${VERSION}"}
  ├── [task] WaitForCI
  │     mcp_tool: github.actions.status
  │     mcp_args: {"run_id": "${CI_RUN_ID}"}
  ├── [task] CreatePR
  │     mcp_tool: github.pr.create
  │     mcp_args: {"title": "Release ${VERSION}", "base": "main", "head": "release/${VERSION}"}
  ├── [task] BuildAndPush
  │     mcp_tool: docker.image.build
  │     mcp_args: {"tag": "yawl/engine:${VERSION}"}
  ├── [task] UpdateArgoCD
  │     mcp_tool: k8s.resource.apply
  │     mcp_args: {"manifest": "k8s/gitops/argocd-application.yaml"}
  ├── [task] MonitorRollout
  │     mcp_tool: grafana.dashboard.get
  │     mcp_args: {"uid": "yawl-operational"}
  ├── [task] CreateGitHubRelease
  │     mcp_tool: github.release.create
  │     mcp_args: {"tag": "v${VERSION}", "name": "YAWL ${VERSION}"}
  ├── [condition] o_end
```

---

## 9. Data Flow: YAWL XML to MCP JSON

### 9.1 Work Item to MCP Tool Call

```
YAWL Work Item XML:
<data>
  <mcp_tool>helm.upgrade</mcp_tool>
  <mcp_args>
    <release>yawl</release>
    <chart>./helm/yawl</chart>
    <namespace>yawl</namespace>
    <set>
      <image_tag>5.2.1</image_tag>
      <replicas>3</replicas>
    </set>
    <wait>true</wait>
    <timeout>300</timeout>
  </mcp_args>
</data>

↓ McpTaskExecutionService.marshalXmlToJson() ↓

MCP Tool Call JSON:
{
  "name": "helm_upgrade",
  "arguments": {
    "release": "yawl",
    "chart": "./helm/yawl",
    "namespace": "yawl",
    "set": {
      "image.tag": "5.2.1",
      "replicas": "3"
    },
    "wait": true,
    "timeout": 300
  }
}
```

### 9.2 MCP Result to Work Item Output

```
MCP Tool Result JSON:
{
  "content": [{
    "type": "text",
    "text": "{\"name\":\"yawl\",\"namespace\":\"yawl\",\"revision\":12,
              \"status\":\"deployed\",\"app_version\":\"5.2.1\"}"
  }]
}

↓ McpTaskExecutionService.marshalJsonToXml() ↓

YAWL Work Item Output XML:
<data>
  <mcp_result>
    <name>yawl</name>
    <namespace>yawl</namespace>
    <revision>12</revision>
    <status>deployed</status>
    <app_version>5.2.1</app_version>
  </mcp_result>
  <mcp_status>success</mcp_status>
</data>
```

---

## 10. Security Requirements

### 10.1 Authentication & Authorization

| MCP Server | Auth Method | Secret Storage |
|------------|-------------|----------------|
| Docker | Docker socket mount | Volume mount |
| Kubernetes | kubeconfig / ServiceAccount | K8s Secret |
| Terraform | TFC_TOKEN | K8s Secret / Vault |
| AWS | IAM Role / OIDC | IRSA (EKS) |
| GCP | Service Account JSON | K8s Secret / Workload Identity |
| Azure | Managed Identity / SPN | K8s Secret / Key Vault |
| Grafana | API Key | K8s Secret |
| GitHub | PAT / GitHub App | K8s Secret |

### 10.2 Tool Execution Policies

```xml
<execution-policies>
  <!-- Destructive tools require human confirmation -->
  <policy tool-pattern="*.delete" requireConfirmation="true"/>
  <policy tool-pattern="k8s.node.drain" requireConfirmation="true"/>
  <policy tool-pattern="helm.uninstall" requireConfirmation="true"/>
  <policy tool-pattern="k8s.deployment.scale" maxReplicas="20"/>

  <!-- Read-only tools auto-approve -->
  <policy tool-pattern="*.list" requireConfirmation="false"/>
  <policy tool-pattern="*.get" requireConfirmation="false"/>
  <policy tool-pattern="*.status" requireConfirmation="false"/>
  <policy tool-pattern="grafana.*" requireConfirmation="false"/>

  <!-- Namespace restrictions -->
  <policy tool-pattern="k8s.*" allowedNamespaces="yawl,yawl-staging"/>
  <policy tool-pattern="k8s.*" deniedNamespaces="kube-system,kube-public"/>

  <!-- Rate limiting -->
  <policy tool-pattern="docker.image.build" maxCallsPerHour="20"/>
  <policy tool-pattern="cloud.*" maxCallsPerMinute="10"/>
</execution-policies>
```

### 10.3 Audit Trail

Every MCP tool invocation is logged with:
- Timestamp
- YAWL case ID and work item ID
- Tool name and arguments (with secrets redacted)
- MCP server name and endpoint
- Result status and truncated output
- Execution duration
- User/role that initiated the workflow

---

## 11. Deployment Phases

### Phase 1: Foundation (Weeks 1-3)

**Deliverables:**
1. `McpToolRegistry` - Server configuration and tool discovery
2. `McpServerConnection` - Connection lifecycle management
3. `McpToolDescriptor` - Tool metadata model
4. `mcp-servers.xml` - Configuration file format and loader
5. Unit tests for all new classes

**Acceptance Criteria:**
- Can load `mcp-servers.xml` and connect to at least Docker and Kubernetes MCP servers
- Can discover tools from connected servers
- Can call a tool and receive a result
- Health check detects server failures

### Phase 2: Workflow Integration (Weeks 4-6)

**Deliverables:**
1. `McpTaskExecutionService` - InterfaceB custom service
2. XML-to-JSON and JSON-to-XML marshalers
3. Work item convention (`mcp_tool`, `mcp_args`, `mcp_result`)
4. Docker Compose sidecar configuration
5. Integration tests with real Docker and K8s MCP servers

**Acceptance Criteria:**
- A YAWL workflow can execute `docker.container.list` as a task and receive results
- A YAWL workflow can execute `k8s.pod.list` as a task and receive results
- Error handling propagates MCP failures as YAWL task failures
- Timeout handling works correctly

### Phase 3: Intelligence Layer (Weeks 7-9)

**Deliverables:**
1. `ZaiToolAdvisor` - Z.AI powered tool recommendation
2. AI-assisted parameter generation
3. Result validation and remediation suggestions
4. Pre-built workflow specifications (all 5 patterns from Section 8)

**Acceptance Criteria:**
- Given "deploy the latest build to staging", Z.AI recommends `helm.upgrade` with correct parameters
- Given a failed deployment, Z.AI suggests specific remediation steps
- All 5 workflow patterns can be loaded and executed (with MCP servers running)

### Phase 4: Multi-Cloud & Production (Weeks 10-12)

**Deliverables:**
1. Cloud provider MCP server integration (AWS, GCP, Azure)
2. Grafana observability integration
3. GitHub CI/CD integration
4. Kubernetes sidecar deployment manifests
5. Helm chart updates with MCP server sidecars
6. Execution policies and audit logging
7. Documentation and operator guide

**Acceptance Criteria:**
- Full end-to-end: commit triggers GitHub Actions, builds Docker image, deploys to K8s via Helm, verifies via Grafana - all orchestrated by a single YAWL workflow
- Multi-cloud deployment workflow provisions and deploys to 2+ clouds
- Self-healing workflow detects Grafana alert and remediates automatically
- All destructive operations require confirmation
- Complete audit trail for all MCP tool invocations

---

## 12. Dependencies

### External MCP Servers (Runtime)

| Server | Install Method | Version |
|--------|---------------|---------|
| mcp-server-docker | `pip install mcp-server-docker` | latest |
| mcp-server-kubernetes | `npm install -g mcp-server-kubernetes` | >=2.9.0 |
| kubernetes-mcp-server | Binary download / `go install` | latest |
| terraform-mcp-server | `npm install -g @hashicorp/terraform-mcp-server` | >=0.4.0 |
| mcp-grafana | Binary download / `go install` | latest |
| github-mcp-server | `npm install -g @github/mcp-server` | latest |
| awslabs MCP servers | `pip install awslabs.core-mcp-server` | latest |

### Java Dependencies (Build Time)

| Dependency | Purpose | Version |
|------------|---------|---------|
| JDOM2 | XML processing (existing) | 2.x |
| Jackson | JSON processing (existing) | 2.x |
| Log4J2 | Logging (existing) | 2.x |

No new Java dependencies required. MCP communication uses the existing `YawlMcpClient` HTTP/stdio transport.

### Infrastructure

| Component | Purpose |
|-----------|---------|
| Docker socket | Required for Docker MCP server |
| kubeconfig | Required for Kubernetes MCP servers |
| Node.js 18+ | Runtime for TypeScript MCP servers |
| Python 3.10+ | Runtime for Python MCP servers |
| Go 1.21+ | Runtime for Go MCP servers (or use pre-built binaries) |

---

## 13. Metrics & Success Criteria

| Metric | Target |
|--------|--------|
| MCP servers connectable from YAWL | 8+ (Docker, K8s x2, Terraform, AWS, GCP, Azure, Grafana, GitHub) |
| MCP tools discoverable | 50+ across all servers |
| Workflow patterns implemented | 5 (build-deploy, blue-green, multi-cloud, self-healing, gitops) |
| Tool call latency overhead | <200ms above native MCP call |
| Tool call success rate | >99% (excluding infrastructure failures) |
| Z.AI tool recommendation accuracy | >80% correct tool selection |
| Audit log coverage | 100% of MCP tool invocations |

---

## 14. Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| MCP server API breaking changes | Medium | Medium | Pin server versions, test in CI |
| MCP server process crashes | High | Low | Health checks, auto-restart, fallback servers |
| Docker socket security exposure | High | Low | Read-only mode, namespace restrictions, pod security |
| Z.AI API rate limits | Medium | Medium | Caching, batch requests, fallback to manual params |
| XML-JSON marshaling edge cases | Medium | High | Comprehensive test suite, schema validation |
| Network latency to MCP sidecars | Low | Medium | Local stdio transport preferred over HTTP |
| Kubernetes RBAC too restrictive | Medium | Medium | Documented minimum permissions, test in CI |

---

## 15. Open Questions

1. **MCP Transport Preference:** Should MCP servers run as stdio subprocesses (simpler, faster) or HTTP sidecars (independent lifecycle, language-agnostic)? Recommendation: stdio for single-node, HTTP sidecars for Kubernetes.

2. **Tool Naming Convention:** Should YAWL use the native MCP tool names (e.g., `list_pods`) or canonical names (e.g., `k8s.pod.list`)? Recommendation: canonical names with mapping layer in McpToolRegistry.

3. **Confirmation UI:** For destructive operations requiring human approval, should the confirmation go through YAWL's resource service work queue or a separate approval channel? Recommendation: YAWL resource service (keeps everything in the workflow).

4. **Multi-Cluster Context:** When workflows target multiple Kubernetes clusters, should each cluster have its own MCP server instance or should context switching happen within a single server? Recommendation: one MCP server per cluster for isolation.

5. **MCP Server Lifecycle:** Should MCP servers be long-running (started with the engine) or on-demand (started per workflow execution)? Recommendation: long-running with connection pooling.

---

## Appendix A: External MCP Server Ecosystem Reference

### Docker MCP Servers

| Server | Stars | Language | License | Key Feature |
|--------|-------|----------|---------|-------------|
| [ckreiling/mcp-server-docker](https://github.com/ckreiling/mcp-server-docker) | ~675 | Python | GPL-3.0 | Full container lifecycle, Compose |
| [QuantGeekDev/docker-mcp](https://github.com/QuantGeekDev/docker-mcp) | ~448 | Python | MIT | Compose stack deployment |
| [docker/hub-mcp](https://github.com/docker/hub-mcp) | ~122 | TypeScript | Apache-2.0 | Docker Hub search, AI recommendations |
| [manusa/podman-mcp-server](https://github.com/manusa/podman-mcp-server) | ~57 | Go | Apache-2.0 | Dual Podman+Docker runtime |

### Kubernetes MCP Servers

| Server | Stars | Language | License | Key Feature |
|--------|-------|----------|---------|-------------|
| [Flux159/mcp-server-kubernetes](https://github.com/Flux159/mcp-server-kubernetes) | ~1,100 | TypeScript | - | Full K8s + Helm, pod cleanup |
| [containers/kubernetes-mcp-server](https://github.com/containers/kubernetes-mcp-server) | ~977 | Go | - | Native K8s API, OpenShift, Red Hat |
| [rohitg00/kubectl-mcp-server](https://github.com/rohitg00/kubectl-mcp-server) | ~784 | Python/TS | - | 253 tools, multi-cluster |
| [alexei-led/k8s-mcp-server](https://github.com/alexei-led/k8s-mcp-server) | ~191 | Python | MIT | kubectl+helm+istioctl+argocd |
| [awslabs/mcp (EKS)](https://awslabs.github.io/mcp/servers/eks-mcp-server) | - | - | Apache-2.0 | EKS-specific, AWS official |

### Infrastructure-as-Code MCP Servers

| Server | Stars | Language | License | Key Feature |
|--------|-------|----------|---------|-------------|
| [hashicorp/terraform-mcp-server](https://github.com/hashicorp/terraform-mcp-server) | ~1,200 | - | MPL-2.0 | Provider docs, modules, policies |
| [@pulumi/mcp-server](https://www.pulumi.com/docs/iac/guides/ai-integration/mcp-server/) | - | - | Apache-2.0 | Stack mgmt, preview, deploy |

### Cloud Provider MCP Servers

| Server | Stars | Language | License | Key Feature |
|--------|-------|----------|---------|-------------|
| [awslabs/mcp](https://github.com/awslabs/mcp) | ~8,200 | Python | Apache-2.0 | 20+ AWS service servers |
| [googleapis/gcloud-mcp](https://github.com/googleapis/gcloud-mcp) | - | - | - | gcloud CLI via MCP |
| [Azure/blue-bridge](https://github.com/Azure/blue-bridge) | - | - | - | Azure resource management |

### Observability & CI/CD MCP Servers

| Server | Stars | Language | License | Key Feature |
|--------|-------|----------|---------|-------------|
| [grafana/mcp-grafana](https://github.com/grafana/mcp-grafana) | ~2,300 | Go | Apache-2.0 | Dashboards, alerts, incidents |
| [github/github-mcp-server](https://github.com/github/github-mcp-server) | - | - | - | PRs, issues, Actions, releases |

### Registries & Catalogs

| Resource | URL | Description |
|----------|-----|-------------|
| Official MCP Registry | [registry.modelcontextprotocol.io](https://registry.modelcontextprotocol.io/) | Community-owned registry |
| Docker MCP Catalog | [hub.docker.com/mcp](https://hub.docker.com/mcp) | 270+ servers, Docker Desktop integrated |
| MCP Reference Servers | [github.com/modelcontextprotocol/servers](https://github.com/modelcontextprotocol/servers) | Official examples |

---

## Appendix B: Existing YAWL Infrastructure Inventory

| Category | Files | Maturity |
|----------|-------|----------|
| MCP Integration (Server/Client) | 2 Java classes | Framework (needs MCP SDK) |
| A2A Integration (Server/Client) | 2 Java classes | Framework (needs A2A SDK) |
| Z.AI Integration | 3 Java classes | Production ready |
| Dockerfiles | 14+ files | Production ready |
| Docker Compose | 1 file (12 services) | Production ready |
| K8s Base Manifests | 13+ files | Production ready |
| K8s Operator (CRDs) | 5 files | Alpha (v1alpha1) |
| K8s Advanced (GitOps/Serverless/Federation) | 4 files | Prototype |
| Helm Charts | 41 templates, 6 values files | Production ready |
| Terraform Modules | 5 cloud modules + shared | Production ready |
| CI/CD Pipelines | 7 platforms | Production ready |
| Security Policies | 20+ files | Production ready |
| Observability | 15+ files | Production ready |
| Workflow Specs (infra) | 3 XML specs | Prototype |

---

*End of PRD*
