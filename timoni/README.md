# YAWL Timoni Module

A complete Kubernetes-native Timoni module for deploying YAWL (Yet Another Workflow Language) Workflow Engine with comprehensive validation, production-ready configurations, and multi-environment support.

## Overview

This Timoni module provides:

- **Type-safe Configuration**: Fully validated Cue language schemas with constraints
- **Multi-Environment Support**: Development, Staging, and Production presets
- **Production Ready**: Includes security contexts, pod disruption budgets, resource quotas, and network policies
- **Cloud SQL Integration**: Built-in Google Cloud SQL Proxy sidecar support
- **Comprehensive Logging**: Log4j2 configuration with rotation policies
- **Database Management**: Hibernate/datasource configuration with connection pooling
- **Monitoring Ready**: Prometheus annotations and metrics configuration
- **High Availability**: Pod anti-affinity, health probes, and HPA support

## Module Structure

```
timoni/
├── cue.mod                           # Cue module definition
├── values.cue                        # Base values with validation schemas
├── values-production.cue             # Production environment overrides
├── values-staging.cue                # Staging environment overrides
├── values-development.cue            # Development environment overrides
├── templates/
│   ├── module.cue                   # Main module entry point
│   ├── deployment.cue               # Kubernetes Deployment
│   ├── service.cue                  # Kubernetes Service
│   └── configmap.cue                # ConfigMaps for configuration
└── README.md                         # This file
```

## Requirements

- Timoni 0.10.0 or later
- Kubernetes 1.24+
- Google Cloud SQL (for database)
- kubectl configured with appropriate cluster access

## Installation

### 1. Clone or download the module

```bash
# Already in your repository
cd /home/user/yawl/timoni
```

### 2. Configure Secrets

Before deploying, ensure your database credentials are available as a Kubernetes secret:

```bash
# For production
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='your-secure-password' \
  -n yawl-prod

# For staging
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='your-secure-password' \
  -n yawl-staging

# For development
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='your-secure-password' \
  -n yawl-dev
```

## Usage

### Deploy to Production

```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --runtime-values gcp_project=my-gcp-project
```

### Deploy to Staging

```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-staging.cue \
  --runtime-values gcp_project=my-gcp-project
```

### Deploy to Development

```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-development.cue \
  --runtime-values gcp_project=my-gcp-project
```

### Dry Run (preview changes)

```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --dry-run
```

## Configuration Reference

### Namespace Configuration

```cue
values.namespace: {
  name: "yawl"           // Kubernetes namespace
  create: true           // Create namespace if it doesn't exist
  labels: {...}          // Namespace labels
}
```

### Image Configuration

```cue
values.image: {
  registry: "us-central1-docker.pkg.dev"  // Container registry
  repository: "yawl/yawl"                 // Repository path
  tag: "latest"                           // Image tag
  pullPolicy: "IfNotPresent"              // Image pull policy
}
```

### Scaling Configuration

```cue
values.scaling: {
  replicas: 3
  strategy: {
    type: "RollingUpdate"
    rollingUpdate: {
      maxSurge: 1
      maxUnavailable: 0
    }
  }
  podDisruptionBudget: {
    enabled: true
    minAvailable: 1
  }
}
```

### Database Configuration

```cue
values.database: {
  host: "cloudsql-proxy.yawl.svc.cluster.local"
  port: 5432
  name: "yawl"
  user: "yawl"
  secretName: "yawl-db-secret"
  secretKey: "password"

  pool: {
    minSize: 5
    maxSize: 20
    acquireIncrement: 5
    maxStatements: 50
    idleTestPeriod: 60
    timeout: 300
  }

  batchSize: 20      // JDBC batch size
  fetchSize: 50      // JDBC fetch size
  showSQL: false     // Enable SQL logging
  formatSQL: true    // Format SQL in logs
}
```

### Security Configuration

```cue
values.securityContext: {
  podSecurityContext: {
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 1000
    seccompProfile: {
      type: "RuntimeDefault"
    }
  }
  containerSecurityContext: {
    allowPrivilegeEscalation: false
    readOnlyRootFilesystem: false
    capabilities: {
      drop: ["ALL"]
    }
  }
}
```

### Resource Configuration

```cue
values.resources: {
  requests: {
    cpu: "500m"
    memory: "1Gi"
  }
  limits: {
    cpu: "2"
    memory: "2Gi"
  }
}
```

## Environment Presets

### Production

Optimized for high availability and performance:

- **Replicas**: 5 (customizable)
- **Resources**: 2000m CPU / 4Gi Memory (requests), 4 CPU / 8Gi Memory (limits)
- **Logging**: WARN level, 500MB file rotation with 60-day retention
- **Database**: 100 connection pool size, optimized for throughput
- **Pod Anti-Affinity**: Enforced (spreads across nodes)
- **HPA**: Enabled (scales up to 15 replicas)
- **Pod Disruption Budget**: Enforced (minimum 2 available)

### Staging

Balanced configuration for QA and pre-production testing:

- **Replicas**: 3
- **Resources**: 1000m CPU / 2Gi Memory (requests), 2 CPU / 4Gi Memory (limits)
- **Logging**: INFO level, 200MB file rotation with 7-day retention
- **Database**: 50 connection pool size
- **Pod Anti-Affinity**: Preferred
- **Pod Disruption Budget**: Enabled (minimum 1 available)

### Development

Minimal resource usage for local development:

- **Replicas**: 1
- **Resources**: 250m CPU / 512Mi Memory (requests), 1 CPU / 1Gi Memory (limits)
- **Logging**: DEBUG level, no file rotation
- **Database**: 10 connection pool size
- **Security**: Relaxed (debugging enabled)
- **Probes**: Disabled (faster iteration)
- **Pod Disruption Budget**: Disabled

## Kubernetes Resources Created

The module generates the following Kubernetes resources:

1. **Namespace** - Isolated namespace for YAWL
2. **ServiceAccount** - Identity for YAWL pods
3. **Role** - Permissions for YAWL pods
4. **RoleBinding** - Binds role to service account
5. **Deployment** - Main YAWL application with Cloud SQL Proxy sidecar
6. **Service** - LoadBalancer service for external access
7. **HeadlessService** - For DNS lookups and internal communication
8. **ConfigMaps** - Application configuration (6 total):
   - `yawl-config` - Main application settings
   - `yawl-logging-config` - Log4j2 configuration
   - `yawl-datasource-config` - Database connection settings
   - `yawl-tomcat-config` - Tomcat performance tuning
   - `yawl-environment-{env}` - Environment-specific settings
   - `yawl-app-properties` - YAWL application properties
9. **PodDisruptionBudget** - High availability policy
10. **NetworkPolicy** - Network access control
11. **HorizontalPodAutoscaler** - Auto-scaling (production only)
12. **ResourceQuota** - Namespace resource limits

## Health Checks

The deployment includes three probe types:

### Startup Probe
- Ensures the application has time to initialize
- Allows up to 30 attempts with 10-second intervals (300s total)

### Liveness Probe
- Checks if the application is still running
- Path: `/resourceService/`
- Production: 120s initial delay, 30s interval, 5 failures
- Development: Disabled

### Readiness Probe
- Checks if the application is ready to receive traffic
- Path: `/resourceService/`
- Production: 60s initial delay, 10s interval, 5 failures
- Development: Disabled

## Validations

The `values.cue` file includes comprehensive validations:

### Resource Quantities
```cue
batchSize: int & >=1 & <=50      // 1-50 range
fetchSize: int & >=1 & <=100     // 1-100 range
```

### Enumerated Types
```cue
pullPolicy: "Always" | "IfNotPresent" | "Never"
environment: "development" | "staging" | "production"
```

### Conditional Logic
```cue
// Production-specific settings
if values.environment == "production" {
  values.scaling.replicas: 3      // Minimum 3 replicas
  values.affinity.podAntiAffinity.enabled: true
}
```

## Cloud SQL Integration

The module includes a Cloud SQL Proxy sidecar container that:

1. Authenticates to Google Cloud SQL using Workload Identity
2. Proxies database connections from port 5432
3. Uses HTTP health checks for reliability
4. Runs with minimal security privileges

### Configuration

```cue
values.cloudSQLProxy: {
  enabled: true
  instances: [
    "my-gcp-project:us-central1:yawl-postgres-prod=tcp:5432"
  ]
  privateIP: true
  useHTTPHealthCheck: true
}
```

## Monitoring and Observability

The service includes Prometheus annotations:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/metrics"
```

Enable monitoring by configuring Prometheus to scrape these endpoints.

## Security

### Pod Security Policy Compliance

The module enforces:

- Non-root user execution (UID 1000)
- Security context with `RunAsNonRoot`
- Dropped all Linux capabilities
- SecComp profile
- Read-only root filesystem (when applicable)

### Network Policy

Includes network policies that:

- Allow ingress only from within the namespace
- Allow DNS egress to kube-system
- Allow database access on port 5432
- Allow external traffic on ports 80/443

### RBAC

Creates minimal RBAC with permissions for:

- Reading pods and logs
- Reading ConfigMaps and Secrets
- Creating and managing batch jobs

## Troubleshooting

### Check Pod Status

```bash
kubectl get pods -n yawl-prod
kubectl describe pod <pod-name> -n yawl-prod
kubectl logs <pod-name> -n yawl-prod
```

### View Generated Manifests

```bash
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --dry-run --output yaml
```

### Database Connectivity Issues

```bash
# Check Cloud SQL Proxy logs
kubectl logs <pod-name> -c cloud-sql-proxy -n yawl-prod

# Verify database credentials
kubectl get secret yawl-db-credentials -n yawl-prod -o jsonpath='{.data.password}' | base64 -d
```

### Health Check Failures

```bash
# Test the health endpoint
kubectl exec <pod-name> -c yawl -n yawl-prod -- \
  curl -v http://localhost:8080/resourceService/
```

## Performance Tuning

### JVM Garbage Collection

Tune GC settings via `values.jvm`:

```cue
jvm: {
  gcType: "G1GC"           // Garbage collector type
  gcPauseTarget: 200       // Target pause time (ms)
  initialHeap: "1024m"     // Initial heap size
  maxHeap: "2048m"         // Maximum heap size
}
```

### Connection Pooling

Adjust database connection pool:

```cue
database.pool: {
  minSize: 5               // Minimum connections
  maxSize: 20              // Maximum connections
  acquireIncrement: 5      // Connections per acquisition
  timeout: 300             // Connection timeout (s)
}
```

### Tomcat Threading

Configure Tomcat thread pool:

```cue
logging: {
  tomcatThreadsMin: 10
  tomcatThreadsMax: 250
}
```

## Customization

### Add Custom ConfigMaps

Edit `templates/configmap.cue` to add new ConfigMaps:

```cue
#CustomConfigMap: corev1.#ConfigMap & {
  // ... definition
}
customConfigMap: #CustomConfigMap
```

Then update `templates/module.cue` to include it in `objects`.

### Add Custom Resources

Create new template files in `templates/` directory and import them in `module.cue`.

## Contributing

To extend this module:

1. Follow Cue language conventions
2. Add validation constraints
3. Document configuration options
4. Test with all environment presets
5. Update README with changes

## References

- [YAWL Foundation](https://yawlfoundation.org/)
- [Timoni Documentation](https://timoni.sh/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Google Cloud SQL](https://cloud.google.com/sql)
- [Cue Language](https://cuelang.org/)

## License

Same as YAWL project (see LICENSE in repository root)
