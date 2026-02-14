// YAWL Timoni Module Architecture and Design

# YAWL Timoni Module Architecture

This document describes the architecture, design decisions, and implementation details of the YAWL Timoni module.

## Overview

The YAWL Timoni module is a complete, production-ready Kubernetes deployment template written in the Cue configuration language. It provides:

- **Type-safe configuration** with comprehensive validation
- **Multi-environment support** (development, staging, production)
- **Cloud-native design** with security, scalability, and observability built-in
- **Infrastructure as Code** principles for reproducible deployments

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                  Deployment Automation                       │
│  (deploy.sh, GitHub Actions, GitLab CI, Jenkins)            │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│                  Timoni CLI                                  │
│  (Bundle management, validation, reconciliation)            │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│                 Cue Language                                 │
│         (Configuration language and validation)             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ values.cue (Base schemas and constraints)           │    │
│  │ values-{env}.cue (Environment-specific overrides)   │    │
│  │ templates/*.cue (Resource templates)                │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│              Kubernetes API Manifests                        │
│  (YAML output from Cue templates)                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ • Namespaces      • Services        • ConfigMaps    │    │
│  │ • Deployments     • NetworkPolicies • RBAC         │    │
│  │ • ServiceAccount  • PDB             • ResourceQuota │    │
│  │ • HPA             • Roles & Bindings                │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│          Kubernetes Cluster (GKE/EKS/AKS)                   │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ yawl-prod/yawl-staging/yawl-dev Namespace          │    │
│  │                                                      │    │
│  │ Pod [yawl container] ← LoadBalancer Service        │    │
│  │ Pod [yawl container]     (Port 80/443)             │    │
│  │ Pod [yawl container]                               │    │
│  │      └─ cloud-sql-proxy sidecar                    │    │
│  │         (CloudSQL connection)                      │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│                  External Services                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ • Google Cloud SQL (PostgreSQL)                    │    │
│  │ • Google Cloud Load Balancer                       │    │
│  │ • Cloud IAM / Workload Identity                    │    │
│  │ • Cloud Monitoring / Prometheus                    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## Configuration Hierarchy

The configuration follows a layered approach for maximum flexibility:

```
Base Configuration (values.cue)
         ↓ [defaults + schema]
Environment Overrides (values-{env}.cue)
         ↓ [production-specific settings]
Runtime Values (--values flags)
         ↓ [command-line overrides]
Final Configuration → Kubernetes Resources
```

### Layer 1: Base Configuration (values.cue)

Defines:
- **Schemas**: Type definitions for all configuration options
- **Validation**: Constraints and rules (e.g., `int & >=1 & <=50`)
- **Default values**: Sensible defaults for all fields
- **Conditional logic**: Environment-specific defaults

Example:
```cue
#Database: {
  batchSize: int & >=1 & <=50 | *20  // Range constraint with default
  pool: {
    maxSize: int | *20
  }
}
```

### Layer 2: Environment Overrides

Three preset configurations:

**Production** (`values-production.cue`):
- 5 replicas (HPA scales to 15)
- High resource requests/limits
- Full security and monitoring enabled
- Aggressive pod anti-affinity

**Staging** (`values-staging.cue`):
- 3 replicas
- Moderate resource requests/limits
- Pod anti-affinity enabled
- Balanced for testing

**Development** (`values-development.cue`):
- 1 replica
- Minimal resources
- Security relaxed for debugging
- Health probes disabled

### Layer 3: Runtime Overrides

Applied via `--values` flag for ad-hoc customization:
```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --values my-custom-overrides.cue  # Runtime overrides
```

## Module Components

### 1. Deployment Template (templates/deployment.cue)

Generates Kubernetes Deployment with:

**Main Container**:
- YAWL application with full configuration
- Environment variables for database, logging, JVM
- Health probes (startup, liveness, readiness)
- Security context (non-root, no privileges)
- Resource requests/limits
- Volume mounts

**Sidecar Containers**:
- Cloud SQL Proxy for database connectivity
- Separate security context and resource limits

**Affinity Policies**:
- Pod anti-affinity (preferred or required)
- Node affinity for specific node pools

**Volumes**:
- EmptyDir for logs and temp storage
- Size limits for safety

### 2. Service Template (templates/service.cue)

Generates:

**Primary Service**:
- Type: LoadBalancer (production) or ClusterIP (development)
- Session affinity: ClientIP (3-hour timeout)
- Dual ports: HTTP (80) and HTTPS (443)
- Health check node port for external LBs

**Headless Service**:
- For DNS name resolution
- For StatefulSet integration (future)
- Returns all pod IPs

### 3. ConfigMap Templates (templates/configmap.cue)

Generates six ConfigMaps:

**yawl-config**:
```
- YAWL_ENV, LOG_LEVEL
- Tomcat thread settings
- JVM options
- Database connection parameters
```

**yawl-logging-config**:
```
- Log4j2.xml with console and file appenders
- Rotation policies (time-based + size-based)
- Per-logger configuration
```

**yawl-datasource-config**:
```
- Hibernate dialect and driver
- Connection pool settings
- Batch and fetch sizes
```

**yawl-tomcat-config**:
```
- Tomcat NIO connector settings
- Performance tuning parameters
- Access log configuration
```

**yawl-environment-{env}**:
```
- Environment-specific settings
- Profile, profiling, caching, batch size
```

**yawl-app-properties**:
```
- Application metadata
- Security settings
- Performance settings
```

### 4. Module Entry Point (templates/module.cue)

Orchestrates all resources:

**Namespace Resources**:
- Namespace (if create=true)

**RBAC**:
- ServiceAccount
- Role (permissions for pods)
- RoleBinding

**Deployment & Services**:
- Deployment (from deployment.cue)
- Service (from service.cue)
- HeadlessService (from service.cue)

**Configuration**:
- All 6 ConfigMaps

**Advanced Features**:
- PodDisruptionBudget (HA enforcement)
- NetworkPolicy (network access control)
- HorizontalPodAutoscaler (production only)
- ResourceQuota (namespace limits)

## Cue Language Features Used

### 1. Type Definitions and Constraints

```cue
#Database: {
  batchSize: int & >=1 & <=50 | *20  // Range constraint
  timeout: int | *300                 // Default value
  showSQL: bool | *false              // Boolean with default
}
```

### 2. Conditional Logic

```cue
if values.environment == "production" {
  values.scaling.replicas: 3
  values.resources.requests.cpu: "1000m"
}
```

### 3. Field Interpolation

```cue
image: {
  reference: "\(registry)/\(repository):\(tag)"
}
```

### 4. List Comprehension

```cue
args: [
  for instance in values.cloudSQLProxy.instances {
    instance
  },
  if values.cloudSQLProxy.useHTTPHealthCheck {
    "--use-http-health-check"
  },
]
```

### 5. Kubernetes API Types

```cue
import (
  appsv1 "k8s.io/api/apps/v1"
  corev1 "k8s.io/api/core/v1"
  "k8s.io/apimachinery/pkg/api/resource"
)

#Deployment: appsv1.#Deployment & {
  // Full type-safe Kubernetes API
}
```

## Design Patterns

### 1. Schema Definition Pattern

Define reusable schemas for common configuration blocks:

```cue
#Image: {
  registry: string | *"us-central1-docker.pkg.dev"
  repository: string
  tag: string | *"latest"
  pullPolicy: "Always" | "IfNotPresent" | "Never" | *"IfNotPresent"
  reference: "\(registry)/\(repository):\(tag)"
}

#Resources: {
  requests: {...}
  limits: {...}
}
```

### 2. Conditional Override Pattern

Apply environment-specific overrides:

```cue
values: {...defaults...}

if values.environment == "production" {
  values: {...production overrides...}
}

if values.environment == "development" {
  values: {...development overrides...}
}
```

### 3. Validation Pattern

Enforce constraints at the schema level:

```cue
#Probe: {
  initialDelaySeconds: int | *30
  periodSeconds: int | *10
  failureThreshold: int & >=1 & <=10 | *3
  // Compiler enforces these constraints
}
```

### 4. Optional Sidecar Pattern

Conditionally include sidecars:

```cue
containers: [
  {...main container...},
  if values.cloudSQLProxy.enabled {
    {...sidecar container...}
  },
]
```

## Validation Strategy

### Schema-Level Validation

Types and constraints enforced by Cue compiler:

```cue
resources.limits.cpu: string & =~"^[0-9]+[m]?$"  // Regex validation
batchSize: int & >=1 & <=50                       // Range validation
environment: "production" | "staging" | "development"  // Enum validation
```

### Logical Validation

Business rules enforced in conditions:

```cue
if values.environment == "production" {
  // Production must have at least 3 replicas
  scaling.replicas: int & >=3
}
```

### Runtime Validation

Timoni validates before deployment:

```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --dry-run  # Validates but doesn't apply
```

## Scalability Considerations

### Horizontal Scaling

**HorizontalPodAutoscaler** (production):
- Scales from min (replicas) to max (replicas × 3)
- Triggers on 70% CPU or 80% memory usage
- Fast scale-up (30% growth per 15s)
- Slow scale-down (50% reduction per 60s)

### Vertical Scaling

Adjust resource requests/limits:
- Production: 2 CPU / 4Gi → 4 CPU / 8Gi
- Automatic HPA response to resource changes

### Database Scaling

Connection pool sizing:
- Development: 10 max connections
- Production: 100 max connections
- Configurable per environment

## Security Architecture

### Network Security

1. **Ingress**:
   - LoadBalancer service on ports 80/443
   - Session affinity for state consistency

2. **Egress**:
   - DNS to kube-system (port 53)
   - Database to CloudSQL (port 5432)
   - External services (port 80/443)

3. **NetworkPolicy**:
   ```
   Ingress ← Same namespace | Same labels
   Egress → DNS | Database | External
   ```

### Pod Security

1. **SecurityContext**:
   - Non-root user (UID 1000)
   - Read-only root filesystem (where applicable)
   - Dropped all capabilities

2. **RBAC**:
   - Minimal permissions (read pods, configmaps, secrets)
   - No cluster-wide permissions

3. **Service Account**:
   - Namespace-scoped
   - Workload Identity enabled for Cloud SQL access

### Image Security

1. **Registry**:
   - Private GCP Artifact Registry
   - Image signing and verification

2. **Scanning**:
   - Vulnerability scanning enabled
   - Only deploy approved images

## Monitoring and Observability

### Prometheus Integration

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/metrics"
```

### Health Checks

- **Startup**: 300s max, checks `/resourceService/`
- **Liveness**: 120s initial delay, 30s interval
- **Readiness**: 60s initial delay, 10s interval

### Logging

- **Application**: Log4j2 configured in ConfigMap
- **Infrastructure**: Kubernetes events and pod logs
- **Database**: SQL logging optional (development/staging)

### Metrics

- Pod CPU/Memory (via metrics-server)
- Custom application metrics (Prometheus)
- Database connection pool metrics

## Disaster Recovery

### Backup & Restore

1. **Configuration**:
   - All in Cue files (Git version control)
   - Encrypted secrets in Kubernetes

2. **Data**:
   - Cloud SQL automatic backups
   - Point-in-time recovery enabled

### High Availability

1. **Pod Redundancy**:
   - Multiple replicas (3-5)
   - Pod disruption budget (min available)
   - Session affinity for state

2. **Database**:
   - Cloud SQL High Availability
   - Automatic failover to replica

3. **Load Distribution**:
   - LoadBalancer distributes traffic
   - Round-robin across healthy pods

## Cost Optimization

### Resource Efficiency

- Development: Minimal (1 replica, 512Mi memory)
- Staging: Moderate (3 replicas, 2Gi memory)
- Production: Robust (5 replicas, 4Gi memory)

### HPA Cost Control

- Scales up to 15 replicas during peaks
- Scales down during low usage
- Saves costs while maintaining availability

### Node Optimization

- Node affinity for cheaper node pools (if available)
- Bin packing via Kubernetes scheduler
- Spot instances support (GKE preemptible nodes)

## Implementation Best Practices

### 1. Immutable Infrastructure

- No manual changes to running deployments
- All changes via Cue configuration
- Git as source of truth

### 2. Configuration as Code

- Cue provides type safety
- Version control for all configurations
- Code review before deployment

### 3. Progressive Deployment

- Development → Staging → Production
- Validate at each stage
- Rollback capability maintained

### 4. Secrets Management

- Kubernetes Secrets for sensitive data
- Never hardcoded in Cue
- Use external secret management (Google Secret Manager)

### 5. Documentation

- Architecture documentation (this file)
- README for usage
- EXAMPLES for common tasks
- Code comments for complex logic

## Future Enhancements

### Potential Extensions

1. **StatefulSet Support**: For stateful deployments
2. **Ingress Configuration**: Advanced routing rules
3. **ServiceMonitor**: Direct Prometheus integration
4. **PersistentVolumes**: For data persistence
5. **BackupPolicy**: Automated backup scheduling
6. **VirtualService/DestinationRule**: Istio integration
7. **MultipleDatabases**: Support for read replicas
8. **CertManager**: Automated TLS certificates

### Extensibility Points

- Add new environment presets
- Create specialized values overlays
- Build custom validation rules
- Extend with additional Kubernetes resources

## References

- [Cue Language Documentation](https://cuelang.org/)
- [Timoni Documentation](https://timoni.sh/)
- [Kubernetes Architecture](https://kubernetes.io/docs/concepts/architecture/)
- [Google Cloud SQL](https://cloud.google.com/sql/docs/)
- [YAWL Workflow Engine](https://yawlfoundation.org/)

## Conclusion

The YAWL Timoni module demonstrates a production-ready, type-safe approach to Kubernetes deployments using the Cue configuration language. It balances flexibility with safety, providing sensible defaults while allowing extensive customization for different environments and use cases.
