# YAWL MCP-A2A Application Kubernetes Deployment

This directory contains Kubernetes deployment manifests for the YAWL MCP-A2A
Spring Boot application, which provides unified MCP (Model Context Protocol)
server and A2A (Agent-to-Agent) agent capabilities.

## Directory Structure

```
k8s/mcp-a2a-app/
├── base/                          # Base Kubernetes resources
│   ├── namespace.yaml             # Namespace definition
│   ├── serviceaccount.yaml        # ServiceAccount and RBAC
│   ├── configmap.yaml             # Application configuration
│   ├── secrets.yaml               # Secret templates (values injected at deploy)
│   ├── deployment.yaml            # Main application deployment
│   ├── service.yaml               # ClusterIP services
│   ├── hpa.yaml                   # HorizontalPodAutoscaler and PodDisruptionBudget
│   ├── networkpolicy.yaml         # Network security policies
│   ├── podmonitor.yaml            # Prometheus PodMonitor and alerting rules
│   ├── destinationrule.yaml       # Istio service mesh configuration
│   └── kustomization.yaml         # Base kustomization
├── overlays/                      # Environment-specific overlays
│   ├── dev/                       # Development environment
│   ├── staging/                   # Staging environment
│   └── prod/                      # Production environment
├── kustomization.yaml             # Root kustomization
└── README.md                      # This file
```

## Prerequisites

1. **Kubernetes cluster** (v1.28+ recommended)
2. **kubectl** configured with cluster access
3. **Kustomize** (built into kubectl v1.14+)
4. **YAWL engine** deployed and running (in `yawl` namespace)
5. **Istio service mesh** (optional, for mTLS and traffic management)
6. **Prometheus Operator** (optional, for PodMonitor and alerting)

## Quick Start

### 1. Create Required Secrets

Before deploying, create the required secrets with your values:

```bash
# Create namespace first
kubectl apply -f base/namespace.yaml

# Create application secrets
kubectl create secret generic yawl-mcp-a2a-secrets \
  --from-literal=YAWL_PASSWORD='your-yawl-password' \
  -n yawl-mcp-a2a

# Create A2A authentication secrets
kubectl create secret generic a2a-auth-secrets \
  --from-literal=A2A_JWT_SECRET="$(openssl rand -base64 32)" \
  --from-literal=A2A_API_KEY_MASTER="$(openssl rand -hex 32)" \
  -n yawl-mcp-a2a

# Create Z.AI API credentials (optional)
kubectl create secret generic zai-api-credentials \
  --from-literal=ZAI_API_KEY='your-zai-api-key' \
  -n yawl-mcp-a2a
```

### 2. Deploy to Development

```bash
# Using kustomize
kubectl apply -k overlays/dev

# Or using the base directly
kubectl apply -k base
```

### 3. Deploy to Production

```bash
# Using production overlay
kubectl apply -k overlays/prod
```

### 4. Verify Deployment

```bash
# Check deployment status
kubectl get deployments -n yawl-mcp-a2a

# Check pods
kubectl get pods -n yawl-mcp-a2a

# Check services
kubectl get services -n yawl-mcp-a2a

# View logs
kubectl logs -f deployment/yawl-mcp-a2a-app -n yawl-mcp-a2a
```

## Configuration

### Environment Variables

Key configuration options in `configmap.yaml`:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` |
| `YAWL_ENGINE_URL` | YAWL engine URL | `http://yawl-engine.yawl.svc.cluster.local:8080/yawl` |
| `MCP_ENABLED` | Enable MCP server | `true` |
| `MCP_HTTP_PORT` | MCP HTTP port | `8081` |
| `A2A_ENABLED` | Enable A2A agent | `true` |
| `A2A_REST_PORT` | A2A REST port | `8082` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector | `http://otel-collector.observability.svc.cluster.local:4317` |

### Resource Requirements

| Environment | CPU Request | CPU Limit | Memory Request | Memory Limit | Replicas |
|-------------|-------------|-----------|----------------|--------------|----------|
| Development | 250m | 1000m | 512Mi | 1Gi | 1 |
| Staging | 500m | 1500m | 1Gi | 2Gi | 2 |
| Production | 1000m | 4000m | 2Gi | 4Gi | 3-20 |

### Java 25 JVM Options

The deployment is optimized for Java 25 with:

- **Generational ZGC**: `-XX:+UseZGC -XX:ZGenerational=true`
- **Compact Object Headers**: `-XX:+UseCompactObjectHeaders`
- **String Deduplication**: `-XX:+UseStringDeduplication`
- **AOT Cache**: `-XX:+UseAOTCache`
- **Preview Features**: `--enable-preview` (for virtual threads enhancements)

## Services

The application exposes three ClusterIP services:

1. **yawl-mcp-a2a-app** (port 8080): Main HTTP server, Actuator endpoints
2. **yawl-mcp-a2a-mcp** (port 8081): MCP protocol over HTTP
3. **yawl-mcp-a2a-a2a** (port 8082): A2A REST protocol

## Health Checks

The application uses Spring Boot Actuator health endpoints:

- **Liveness**: `/actuator/health/liveness` - Container restart if unhealthy
- **Readiness**: `/actuator/health/readiness` - Remove from service if not ready
- **Startup**: Extended startup probe for JVM warmup (up to 3 minutes)

## Scaling

### Horizontal Pod Autoscaler

The HPA automatically scales based on:

- CPU utilization (target: 70%)
- Memory utilization (target: 80%)

Scale range: 2-10 replicas (production: 3-20)

### Manual Scaling

```bash
kubectl scale deployment yawl-mcp-a2a-app --replicas=5 -n yawl-mcp-a2a
```

## Service Mesh (Istio)

When Istio is installed:

- **mTLS**: Enabled between services
- **Traffic Policy**: Connection pooling, circuit breaking, outlier detection
- **Canary Deployments**: Support via VirtualService routing

To enable canary deployments, add the `x-canary: true` header to requests.

## Monitoring

### Prometheus Metrics

Metrics are exposed at `/actuator/prometheus` on port 8080.

Key metric prefixes:

- `jvm_*` - JVM metrics (memory, GC, threads)
- `http_*` - HTTP request metrics
- `process_*` - Process metrics
- `yawl_*` - YAWL-specific metrics
- `mcp_*` - MCP protocol metrics
- `a2a_*` - A2A protocol metrics
- `otel_*` - OpenTelemetry metrics

### Alerting Rules

Pre-configured alerts:

| Alert | Condition | Severity |
|-------|-----------|----------|
| `YawlMcpA2aHighErrorRate` | Error rate > 10% | Warning |
| `YawlMcpA2aPodNotReady` | Pod not ready > 10m | Warning |
| `YawlMcpA2aHighMemory` | Heap > 85% | Warning |
| `YawlMcpA2aHighCpu` | CPU > 1.5 cores | Warning |

## Network Policy

Network policies restrict traffic:

- **Ingress**: From yawl, yawl-mcp-a2a, observability, and istio-system namespaces
- **Egress**: To DNS, YAWL engine, agent registry, and OpenTelemetry collector

## Troubleshooting

### Common Issues

1. **Pod stuck in Pending**
   ```bash
   kubectl describe pod <pod-name> -n yawl-mcp-a2a
   ```

2. **Application not connecting to YAWL engine**
   ```bash
   # Check YAWL engine is running
   kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine

   # Test connectivity from pod
   kubectl exec -it <pod-name> -n yawl-mcp-a2a -- curl -v http://yawl-engine.yawl:8080/yawl/ib
   ```

3. **High memory usage**
   ```bash
   # Check JVM memory
   kubectl exec -it <pod-name> -n yawl-mcp-a2a -- curl localhost:8080/actuator/metrics/jvm.memory.used
   ```

### Logs

```bash
# Application logs
kubectl logs -f deployment/yawl-mcp-a2a-app -n yawl-mcp-a2a

# Init container logs
kubectl logs -f deployment/yawl-mcp-a2a-app -n yawl-mcp-a2a -c wait-for-engine

# All containers
kubectl logs -f deployment/yawl-mcp-a2a-app -n yawl-mcp-a2a --all-containers
```

## CI/CD Integration

### Image Placeholder

The deployment uses `__MCP_A2A_IMAGE__` as a placeholder. Replace in your CI pipeline:

```bash
# Using sed
sed -i "s|__MCP_A2A_IMAGE__|your-registry/yawl-mcp-a2a-app:v1.0.0|g" base/deployment.yaml

# Using kustomize
kustomize edit set image __MCP_A2A_IMAGE__=your-registry/yawl-mcp-a2a-app:v1.0.0
```

### GitOps with ArgoCD

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: yawl-mcp-a2a-app
spec:
  source:
    repoURL: https://github.com/your-org/yawl
    path: k8s/mcp-a2a-app/overlays/prod
    targetRevision: main
  destination:
    server: https://kubernetes.default.svc
    namespace: yawl-mcp-a2a
```

## Security Considerations

1. **Secrets**: Never commit plaintext secrets. Use External Secrets Operator or Sealed Secrets.
2. **Network Policies**: Enable for production deployments.
3. **RBAC**: Minimal permissions with dedicated ServiceAccount.
4. **Pod Security**: Runs as non-root with read-only filesystem.
5. **mTLS**: Enable Istio mTLS for service-to-service communication.

## Related Documentation

- [YAWL Engine Deployment](../base/README.md)
- [MCP Server Deployment](../agents/mcp-server-deployment.yaml)
- [A2A Server Deployment](../agents/a2a-server-deployment.yaml)
- [Java 25 Features](../../../.claude/JAVA-25-FEATURES.md)
- [Architecture Patterns](../../../.claude/ARCHITECTURE-PATTERNS-JAVA25.md)
