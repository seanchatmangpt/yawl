# YAWL Timoni Module - Complete Index

## Module Overview

Complete Timoni/Cue module for deploying YAWL Workflow Engine to Kubernetes with production-ready configurations, multi-environment support, and comprehensive validation.

**Location**: `/home/user/yawl/timoni/`

**Status**: Production-ready

**Last Updated**: February 14, 2025

## File Structure

```
timoni/
├── cue.mod                              # Cue module metadata
├── README.md                            # Quick start guide
├── INDEX.md                             # This file
├── ARCHITECTURE.md                      # Design and architecture details
├── EXAMPLES.md                          # Usage examples and patterns
├── deploy.sh                            # Deployment automation script
├── timoni.cue                           # Timoni bundle definition
│
├── values.cue                           # Base configuration with schemas
├── values-production.cue                # Production environment overrides
├── values-staging.cue                   # Staging environment overrides
├── values-development.cue               # Development environment overrides
│
└── templates/
    ├── module.cue                       # Main module entry point
    ├── deployment.cue                   # Kubernetes Deployment resource
    ├── service.cue                      # Kubernetes Service resources
    └── configmap.cue                    # Kubernetes ConfigMap resources
```

## Quick Start

### 1. Prerequisites

```bash
# Install required tools
brew install timoni kubectl  # macOS
# or
apt-get install timoni kubectl  # Linux

# Verify installation
timoni version
kubectl version --client
```

### 2. Setup

```bash
# Navigate to module
cd /home/user/yawl/timoni

# Create namespace and secrets
kubectl create namespace yawl-prod
kubectl create secret generic yawl-db-credentials \
  --from-literal=password='YOUR_DB_PASSWORD' \
  -n yawl-prod
```

### 3. Deploy

```bash
# Production deployment
./deploy.sh production apply

# Staging deployment
./deploy.sh staging apply

# Development deployment
./deploy.sh development apply
```

### 4. Verify

```bash
# Check deployment status
kubectl get pods -n yawl-prod
kubectl get services -n yawl-prod
kubectl logs -n yawl-prod -l app=yawl -f
```

## Documentation Files

### README.md
- **Purpose**: Complete user guide and reference
- **Contents**:
  - Overview and features
  - Installation and setup
  - Configuration reference
  - Environment presets
  - Kubernetes resources created
  - Health checks and monitoring
  - Security details
  - Troubleshooting guide
  - Performance tuning
  - Customization examples
- **Audience**: Operators, DevOps engineers
- **Length**: Comprehensive (full reference)

### ARCHITECTURE.md
- **Purpose**: Technical design documentation
- **Contents**:
  - System architecture and layers
  - Configuration hierarchy
  - Module components breakdown
  - Cue language features explained
  - Design patterns used
  - Validation strategy
  - Scalability design
  - Security architecture
  - Monitoring and observability
  - Disaster recovery approach
  - Implementation best practices
  - Future enhancements
- **Audience**: Architects, developers, technical leads
- **Length**: Deep dive (technical details)

### EXAMPLES.md
- **Purpose**: Practical usage examples and patterns
- **Contents**:
  - Basic deployment walkthrough
  - Environment-specific deployment commands
  - Advanced configuration examples
    - Custom image registry
    - Custom resource allocation
    - Multiple Cloud SQL instances
    - Debug mode configurations
    - JVM tuning examples
    - Node pool configuration
  - Troubleshooting procedures
  - CI/CD integration examples
    - GitHub Actions
    - GitLab CI
    - Jenkins Pipeline
  - Rollback procedures
  - Upgrade procedures
  - Performance testing
- **Audience**: DevOps engineers, SREs, platform teams
- **Length**: Practical recipes (copy-paste ready)

### INDEX.md (this file)
- **Purpose**: Navigation and orientation guide
- **Contents**: File descriptions, quick reference, key facts
- **Audience**: All users (starting point)

## Core Configuration Files

### values.cue
**Size**: ~600 lines | **Type**: Schema Definitions

**Contains**:
- Type definitions for all configuration options
- Validation constraints and rules
- Default values for all settings
- Conditional logic for environment presets

**Key Schemas**:
- `#Version` - Version configuration
- `#Image` - Container image configuration
- `#Resources` - CPU/memory requests and limits
- `#Database` - Database connection and pooling
- `#Probe` - Health check configuration
- `#Logging` - Logging and file rotation
- `#Scaling` - Replica and scaling settings
- `#Affinity` - Pod placement rules
- `#SecurityContext` - Security policies
- `#CloudSQLProxy` - Cloud SQL proxy settings
- `#Network` - Service and networking
- `#JVM` - Java/JVM configuration
- `#Namespace` - Namespace configuration

**Validation Examples**:
```cue
batchSize: int & >=1 & <=50 | *20
gcType: "G1GC" | "ConcMarkSweepGC" | "ParallelGC" | *"G1GC"
```

### values-production.cue
**Size**: ~200 lines | **Type**: Production Environment Overrides

**Preset Values**:
- Replicas: 5 (with HPA to 15)
- CPU: 2 req / 4 limit
- Memory: 4Gi req / 8Gi limit
- Logging: WARN level, 500MB rotation, 60-day retention
- Database pool: 100 max connections
- Full security and monitoring enabled
- Pod anti-affinity: enforced
- Pod disruption budget: min 2 available

### values-staging.cue
**Size**: ~190 lines | **Type**: Staging Environment Overrides

**Preset Values**:
- Replicas: 3
- CPU: 1000m req / 2 limit
- Memory: 2Gi req / 4Gi limit
- Logging: INFO level, 200MB rotation, 7-day retention
- Database pool: 50 max connections
- Pod anti-affinity: preferred
- Pod disruption budget: min 1 available

### values-development.cue
**Size**: ~180 lines | **Type**: Development Environment Overrides

**Preset Values**:
- Replicas: 1
- CPU: 250m req / 1 limit
- Memory: 512Mi req / 1Gi limit
- Logging: DEBUG level, no file rotation
- Database pool: 10 max connections
- Security: relaxed for debugging
- Health probes: disabled
- Pod disruption budget: disabled

## Template Files

### templates/module.cue
**Size**: ~400 lines | **Type**: Main Module Orchestrator

**Generates**:
1. Namespace (if enabled)
2. ServiceAccount
3. Role and RoleBinding (RBAC)
4. Deployment (YAWL + Cloud SQL Proxy)
5. Service and HeadlessService
6. 6 ConfigMaps
7. PodDisruptionBudget (HA)
8. NetworkPolicy
9. HorizontalPodAutoscaler (production)
10. ResourceQuota

**Key Logic**:
- Conditional namespace creation
- RBAC configuration with minimal permissions
- Resource bundling into single manifest
- Optional resource inclusion based on environment

### templates/deployment.cue
**Size**: ~350 lines | **Type**: Kubernetes Deployment

**Defines**:
- Deployment specification with rolling updates
- YAWL main container with:
  - Full environment configuration
  - Resource requests/limits
  - Three health probes (startup, liveness, readiness)
  - Security context
  - Volume mounts
- Cloud SQL Proxy sidecar container
- Pod affinity and anti-affinity rules
- Security context for pod
- Lifecycle hooks for graceful shutdown

**Notable Features**:
- Comprehensive environment variables
- Configurable health probe thresholds
- Flexible affinity configuration
- Conditional sidecar inclusion

### templates/service.cue
**Size**: ~90 lines | **Type**: Kubernetes Services

**Defines**:
1. **Primary Service**:
   - Type: LoadBalancer (production) or ClusterIP (dev)
   - Session affinity: ClientIP with 3-hour timeout
   - Dual ports: HTTP (80) and HTTPS (443)
   - Prometheus annotations
   - Cloud-specific optimizations

2. **Headless Service**:
   - For DNS resolution
   - Returns all pod IPs
   - Useful for StatefulSet integration

### templates/configmap.cue
**Size**: ~300 lines | **Type**: Configuration Maps

**Generates 6 ConfigMaps**:

1. **yawl-config**: Application settings
   - Environment, log level, thread counts
   - Database parameters
   - JVM options

2. **yawl-logging-config**: Log4j2 XML configuration
   - Console and file appenders
   - Rotation policies
   - Per-logger settings

3. **yawl-datasource-config**: Hibernate/Database settings
   - Driver and dialect
   - Connection pooling
   - SQL tuning parameters

4. **yawl-tomcat-config**: Tomcat configuration
   - NIO connector settings
   - Thread pool configuration
   - Access logging

5. **yawl-environment-{env}**: Environment-specific overrides
   - Caching, profiling, batch size
   - Security settings

6. **yawl-app-properties**: YAWL application properties
   - Application metadata
   - Cache and metrics settings
   - Security enforcement

## Deployment Automation

### deploy.sh
**Size**: ~400 lines | **Type**: Bash Deployment Script

**Functions**:
- `check_prerequisites`: Verify timoni, kubectl, cluster access
- `create_namespace`: Create target namespace
- `setup_secrets`: Verify database credentials
- `validate_config`: Validate Cue configuration
- `show_plan`: Display deployment plan (dry-run)
- `apply_deployment`: Apply deployment to cluster
- `check_rollout`: Monitor rollout completion
- `show_deployment_info`: Display pod and service info
- `delete_deployment`: Delete deployment with confirmation
- `show_logs`: Stream pod logs

**Usage**:
```bash
./deploy.sh [environment] [action]
# Examples:
./deploy.sh production apply
./deploy.sh staging plan
./deploy.sh development logs
./deploy.sh production delete
```

**Actions**:
- `apply`: Apply deployment (default)
- `plan`: Show what would be deployed
- `validate`: Validate configuration
- `status`: Show current deployment status
- `logs`: Stream pod logs
- `delete`: Remove deployment

## Key Statistics

### Lines of Code
- **Cue Configuration**: ~2,500 lines
- **Documentation**: ~2,000 lines
- **Scripts**: ~400 lines
- **Total**: ~4,900 lines

### Resources Generated
- **Kubernetes Objects**: 12 types
- **ConfigMaps**: 6 instances
- **Total Manifests**: 20+ resource definitions

### Supported Configurations
- **Environments**: 3 presets (dev, staging, prod)
- **Kubernetes Versions**: 1.24+
- **Cloud Providers**: GCP (Cloud SQL), AWS, Azure
- **Container Runtimes**: Docker, containerd, CRI-O

### Configuration Options
- **Top-level Config Items**: 10
- **Database Settings**: 15
- **Security Options**: 20
- **Scalability Parameters**: 8
- **Monitoring Options**: 5

## Environment Comparison

| Aspect | Development | Staging | Production |
|--------|-------------|---------|-----------|
| Replicas | 1 | 3 | 5 (→15 with HPA) |
| CPU Request | 250m | 1000m | 2000m |
| Memory Request | 512Mi | 2Gi | 4Gi |
| Log Level | DEBUG | INFO | WARN |
| DB Pool Size | 10 | 50 | 100 |
| Pod Anti-Affinity | No | Preferred | Enforced |
| PDB Enabled | No | Yes (1) | Yes (2) |
| HPA Enabled | No | No | Yes |
| Security Relaxed | Yes | No | No |
| Health Probes | Disabled | Enabled | Enabled |

## Common Tasks

### Deploy to Production
```bash
./deploy.sh production apply
```

### Check Deployment Status
```bash
kubectl get pods -n yawl-prod
kubectl describe deployment yawl -n yawl-prod
```

### View Application Logs
```bash
kubectl logs -n yawl-prod -l app=yawl -f
```

### Update Configuration
```bash
# Edit values file
vim values-production.cue

# Apply changes
timoni bundle apply yawl \
  --values values.cue \
  --values values-production.cue \
  --namespace yawl-prod
```

### Scale Deployment
```bash
# Manual scaling
kubectl scale deployment yawl -n yawl-prod --replicas=7

# Or update values.cue and reapply
```

### Rollback Deployment
```bash
# Kubernetes rollout
kubectl rollout undo deployment/yawl -n yawl-prod

# Or reapply previous values configuration
```

### Check Resource Usage
```bash
kubectl top pods -n yawl-prod -l app=yawl
kubectl describe resourcequota -n yawl-prod
```

## Validation Features

### Schema Validation
- Type checking (int, string, bool)
- Range constraints (e.g., `int & >=1 & <=50`)
- Enum validation (e.g., environment choices)
- Required vs optional fields
- Default value handling

### Logical Validation
- Environment-specific preset enforcement
- Consistency checks (e.g., min ≤ max)
- Conditional requirement rules
- Cross-field validation

### Runtime Validation
- Cue compiler validation before deployment
- Kubernetes API validation
- Health check validation post-deployment

## Security Features

### Network Security
- NetworkPolicy with ingress/egress rules
- LoadBalancer with health checks
- Session affinity for consistency

### Pod Security
- Non-root user execution (UID 1000)
- Security context with restricted capabilities
- Read-only root filesystem (where applicable)

### RBAC
- ServiceAccount per deployment
- Role with minimal permissions
- RoleBinding for namespace isolation

### Secrets Management
- Kubernetes Secrets for credentials
- No hardcoded sensitive data
- Support for external secret managers

## Monitoring Integration

### Prometheus Annotations
```yaml
prometheus.io/scrape: "true"
prometheus.io/port: "8080"
prometheus.io/path: "/metrics"
```

### Health Endpoints
- **Startup Probe**: Validates application startup (300s)
- **Liveness Probe**: Verifies app is running (120s initial)
- **Readiness Probe**: Checks app is ready (60s initial)

### Metrics Available
- Pod CPU and memory usage
- Application metrics (custom)
- Database connection pool metrics
- Kubernetes event logs

## Reference Links

### Documentation
- [README.md](./README.md) - User guide and reference
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Technical design
- [EXAMPLES.md](./EXAMPLES.md) - Usage examples

### External Resources
- [Cue Language Docs](https://cuelang.org/)
- [Timoni Docs](https://timoni.sh/)
- [Kubernetes Docs](https://kubernetes.io/docs/)
- [YAWL Project](https://yawlfoundation.org/)
- [Google Cloud SQL](https://cloud.google.com/sql/)

## Support and Contribution

### Troubleshooting
See EXAMPLES.md → Troubleshooting section for common issues and solutions.

### Extension
See ARCHITECTURE.md → Future Enhancements for planned additions.

### Best Practices
See ARCHITECTURE.md → Implementation Best Practices for recommended approaches.

## Module Metadata

- **Module Name**: yawl.io/timoni
- **Cue Version**: 0.10.0+
- **Kubernetes**: 1.24+
- **Created**: February 14, 2025
- **Status**: Production-Ready
- **License**: Same as YAWL project

## Getting Help

1. **Quick Questions**: See README.md
2. **Usage Patterns**: See EXAMPLES.md
3. **Technical Details**: See ARCHITECTURE.md
4. **Deployment Script Help**: `./deploy.sh` (no arguments shows help)
5. **Kubernetes Issues**: Check `kubectl get events -n namespace`
6. **Configuration Issues**: Run `timoni bundle apply --dry-run`

---

**Last Updated**: February 14, 2025
**Maintained By**: YAWL Community
**Version**: 0.1.0
