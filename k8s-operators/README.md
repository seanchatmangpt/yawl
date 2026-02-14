# YAWL Kubernetes Operator

This directory contains the Kubernetes Operator implementation for YAWL (Yet Another Workflow Language) Workflow Engine. The operator enables declarative deployment and lifecycle management of YAWL clusters on Kubernetes.

## Contents

### Core Components

- **`yawl-cluster-crd.yaml`** - Custom Resource Definition for YawlCluster
  - Defines v1alpha1 and v1beta1 API versions
  - Complete specification schema with validation rules
  - Status tracking with phase transitions
  - Additional printer columns for kubectl output

- **`controller.go`** - Main reconciliation controller
  - Implements the operator's reconciliation loop
  - Manages all Kubernetes resources (Deployment, Service, ConfigMap, PVC, Ingress, HPA, NetworkPolicy)
  - Handles cluster lifecycle (create, update, delete)
  - Status updates and condition tracking
  - Event recording and logging

- **`api_types.go`** - Type definitions
  - YawlCluster CRD type definition
  - Complete spec and status structures
  - Nested types for all configuration sections
  - Schema validation annotations

- **`admission-webhooks.go`** - Admission control logic
  - Mutating webhook for setting defaults
  - Validating webhook for enforcing constraints
  - Comprehensive validation rules
  - Update validation logic

### Configuration Files

- **`helm-chart-values.yaml`** - Default values for Helm deployment
  - Operator configuration
  - Webhook settings
  - RBAC configuration
  - Monitoring setup
  - Default cluster specifications
  - ServiceMonitor and PrometheusRule examples

- **`webhook-configuration.yaml`** - Webhook admission controller configurations
  - MutatingWebhookConfiguration
  - ValidatingWebhookConfiguration
  - Webhook service definition

### Documentation

- **`OPERATOR_DOCUMENTATION.md`** - Comprehensive operator documentation
  - Architecture overview
  - Installation instructions (Helm and direct YAML)
  - Usage examples
  - Complete configuration reference
  - Advanced features
  - Troubleshooting guide
  - Development setup

- **`example-cluster.yaml`** - Example cluster manifests
  - Production cluster with PostgreSQL
  - Development cluster with H2
  - High-availability cluster
  - Minimal configuration example
  - Database secret example

## Quick Start

### Prerequisites

- Kubernetes 1.24+
- kubectl v1.24+
- Helm 3.0+ (optional, for Helm installation)
- At least 2 CPU cores and 2GB memory available for operator

### Installation

#### Option 1: Using Helm (Recommended)

```bash
# Add YAWL Helm repository
helm repo add yawl https://charts.yawlfoundation.org
helm repo update

# Create operator namespace
kubectl create namespace yawl-operator-system

# Install the operator
helm install yawl-operator yawl/yawl-operator \
  --namespace yawl-operator-system \
  --values helm-chart-values.yaml
```

#### Option 2: Direct YAML Deployment

```bash
# Apply CRD
kubectl apply -f yawl-cluster-crd.yaml

# Apply RBAC and webhooks
kubectl apply -f webhook-configuration.yaml

# Apply operator deployment (requires operator-deployment.yaml and operator-rbac.yaml)
# These files need to be created based on controller.go
```

### Create Your First Cluster

```bash
# Create namespace for YAWL
kubectl create namespace yawl

# Apply example cluster (adjust configuration as needed)
kubectl apply -f example-cluster.yaml

# Watch cluster creation
kubectl get yawlclusters --namespace yawl --watch

# Check detailed status
kubectl describe yawlcluster production-yawl -n yawl
```

## Architecture

### Operator Pattern

The YAWL operator follows the standard Kubernetes Operator pattern:

1. **CRD (Custom Resource Definition)**: Defines the YawlCluster resource schema
2. **Controller**: Watches YawlCluster resources and reconciles desired vs. actual state
3. **Webhooks**: Validate and mutate cluster specifications
4. **Managed Resources**: Deployment, Service, ConfigMap, PVC, Ingress, HPA, NetworkPolicy

### Reconciliation Loop

```
User creates/updates YawlCluster
         ↓
Webhook validation & mutation
         ↓
Controller reconciliation
         ↓
Create/Update managed Kubernetes resources
         ↓
Monitor status & update conditions
         ↓
Event recording
         ↓
Requeue for continuous reconciliation
```

### Resource Ownership

The operator uses Kubernetes owner references to maintain proper resource hierarchy:

```
YawlCluster
├── Deployment
│   ├── ReplicaSet
│   └── Pod
├── Service
├── ConfigMap
├── PersistentVolumeClaim (data)
├── PersistentVolumeClaim (logs)
├── Ingress
├── NetworkPolicy
└── HorizontalPodAutoscaler
```

## Key Features

### Lifecycle Management
- **Creation**: Automatic provisioning of all required resources
- **Updates**: Rolling updates with zero downtime
- **Deletion**: Graceful cleanup with finalizers

### Configuration
- **Database**: PostgreSQL, MySQL, or embedded H2
- **Persistence**: Configurable storage classes and sizes
- **Resources**: CPU and memory requests/limits
- **JVM**: Customizable heap size, GC type, and options
- **Networking**: Service, Ingress, and NetworkPolicy configuration
- **Security**: RBAC, TLS, security contexts

### High Availability
- Pod anti-affinity for distribution across nodes
- Configurable replica counts and autoscaling
- Topology spread constraints
- Node affinity and tolerations

### Monitoring & Observability
- Prometheus metrics integration
- Structured JSON logging
- Jaeger distributed tracing support
- Health checks (liveness and readiness probes)

### Webhooks
- **Mutating Webhook**: Sets sensible defaults for missing fields
- **Validating Webhook**: Enforces constraints and validates specifications

## Usage Examples

### Basic Cluster

```yaml
apiVersion: yawl.io/v1beta1
kind: YawlCluster
metadata:
  name: my-yawl
  namespace: yawl
spec:
  size: 3
  version: "5.2"
  database:
    type: postgresql
    host: postgres.example.com
    port: 5432
```

### Production Cluster

```yaml
apiVersion: yawl.io/v1beta1
kind: YawlCluster
metadata:
  name: prod-yawl
  namespace: yawl
spec:
  size: 5
  version: "5.2"

  database:
    type: postgresql
    host: cloudsql-proxy.yawl.svc
    port: 5432
    sslEnabled: true
    connectionPoolSize: 50

  persistence:
    enabled: true
    storageClass: fast-ssd
    size: 50Gi

  resources:
    requests:
      cpu: "2"
      memory: "4Gi"
    limits:
      cpu: "4"
      memory: "8Gi"

  highAvailability:
    enabled: true
    podAntiAffinity: required

  scaling:
    enabled: true
    minReplicas: 3
    maxReplicas: 10
    targetCPUUtilization: 70

  monitoring:
    prometheus:
      enabled: true
    logging:
      enabled: true
      format: json
```

See `example-cluster.yaml` for more examples.

## Configuration Reference

### Core Spec Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `size` | integer | Yes | Number of YAWL replicas (1-100) |
| `version` | string | No | YAWL version (default: "5.2") |
| `image` | object | No | Container image settings |
| `database` | object | Yes | Database configuration |
| `persistence` | object | No | Storage configuration |
| `resources` | object | No | Resource requests/limits |
| `jvm` | object | No | JVM configuration |
| `networking` | object | No | Network settings |
| `security` | object | No | Security configuration |
| `highAvailability` | object | No | HA settings |
| `monitoring` | object | No | Monitoring configuration |
| `scaling` | object | No | Autoscaling settings |
| `backup` | object | No | Backup configuration |

### Database Configuration

| Field | Type | Default | Options |
|-------|------|---------|---------|
| `type` | string | postgresql | postgresql, mysql, h2 |
| `host` | string | - | Database hostname |
| `port` | integer | 5432/3306 | Database port |
| `name` | string | yawl | Database name |
| `connectionPoolSize` | integer | 20 | 5-100 |
| `sslEnabled` | boolean | false | Enable SSL |

### Resource Configuration

| Field | Type | Default |
|-------|------|---------|
| `requests.cpu` | string | 500m |
| `requests.memory` | string | 1Gi |
| `limits.cpu` | string | 2 |
| `limits.memory` | string | 2Gi |

### JVM Configuration

| Field | Type | Default |
|-------|------|---------|
| `heapSize` | string | 1024m |
| `initialHeapSize` | string | 512m |
| `gcType` | string | G1GC |
| `gcPauseTarget` | integer | 200ms |

## Admission Webhooks

### Mutating Webhook

Sets default values for fields not explicitly specified:
- Resource requests and limits
- JVM heap size and GC settings
- Network service type and ports
- Scaling parameters
- Security context
- Database connection pool size

### Validating Webhook

Enforces constraints:
- Size must be 1-100
- Database type must be valid (postgresql, mysql, h2)
- Port numbers must be valid (1-65535)
- Scaling: minReplicas ≤ maxReplicas
- CPU/memory requests ≤ limits
- NodePort range (30000-32767) when service type is NodePort
- Prevents changing database type after creation

## Status and Conditions

The operator tracks cluster status with phases and conditions:

### Phases
- **Pending**: Initial state
- **Creating**: Resources are being created
- **Running**: Cluster is fully operational
- **Updating**: Cluster is being updated
- **Degraded**: Some replicas are not ready
- **Failed**: Cluster encountered an error
- **Terminating**: Cluster is being deleted

### Conditions
- **Ready**: Indicates if cluster is ready to accept traffic
- **DatabaseConnected**: Indicates database connectivity
- **PodsReady**: Indicates if all pod replicas are ready

## Troubleshooting

### Check Cluster Status
```bash
kubectl get yawlclusters
kubectl describe yawlcluster my-yawl -n yawl
kubectl get yawlcluster my-yawl -o yaml
```

### View Operator Logs
```bash
kubectl logs -n yawl-operator-system -l app=yawl-operator -f
```

### Check Pod Status
```bash
kubectl get pods -n yawl
kubectl logs -n yawl <pod-name>
```

### Common Issues

1. **Cluster stuck in Creating phase**
   - Check operator logs for errors
   - Verify database connectivity
   - Check persistent volume claims status

2. **Database connection failures**
   - Verify database secret exists
   - Check database host/port/credentials
   - Test connectivity from a pod

3. **Persistent volume issues**
   - Check storage class availability
   - Verify PVC status
   - Check node storage capacity

4. **Webhook errors**
   - Check webhook service availability
   - Verify webhook certificate
   - Check webhook logs

## Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/yawlfoundation/yawl-operator.git
cd yawl-operator

# Build
make build

# Docker image
make docker-build

# Run locally
make run

# Tests
make test
```

### Project Structure

```
yawl-operator/
├── api/
│   └── v1beta1/
│       ├── yawlcluster_types.go
│       └── groupversion_info.go
├── controllers/
│   ├── yawlcluster_controller.go
│   └── webhook_server.go
├── config/
│   ├── crd/
│   ├── rbac/
│   └── webhook/
├── hack/
│   └── boilerplate.go.txt
├── test/
│   └── e2e/
├── main.go
├── Dockerfile
├── Makefile
└── go.mod
```

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This operator is licensed under the Apache License 2.0. See the LICENSE file for details.

## Support

- **Documentation**: See `OPERATOR_DOCUMENTATION.md`
- **Issues**: https://github.com/yawlfoundation/yawl-operator/issues
- **Community**: https://www.yawlfoundation.org/community
- **Email**: support@yawlfoundation.org

## Version Information

- **Operator Version**: 1.0.0
- **Supported Kubernetes**: 1.24+
- **YAWL Versions**: 5.0+
- **API Version**: v1beta1 (stable)

---

For detailed information, see `OPERATOR_DOCUMENTATION.md` and `example-cluster.yaml`.
