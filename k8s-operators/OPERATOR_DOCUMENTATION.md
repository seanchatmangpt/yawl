# YAWL Kubernetes Operator Documentation

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Installation](#installation)
4. [Usage](#usage)
5. [Configuration](#configuration)
6. [Advanced Features](#advanced-features)
7. [Troubleshooting](#troubleshooting)
8. [Development](#development)

## Overview

The YAWL Kubernetes Operator is a cloud-native implementation of the YAWL (Yet Another Workflow Language) Workflow Engine designed to manage YAWL cluster deployments on Kubernetes. The operator follows the Kubernetes Operator pattern to provide declarative configuration and lifecycle management of YAWL clusters.

### Key Features

- **Declarative Configuration**: Define YAWL clusters using Kubernetes CRD (Custom Resource Definition)
- **Automated Lifecycle Management**: Create, update, and delete clusters with a single manifest
- **High Availability**: Built-in support for pod anti-affinity, topology spread constraints, and distributed deployments
- **Database Management**: Automatic configuration of PostgreSQL, MySQL, or H2 databases
- **Persistence**: Automatic PVC creation for data and logs
- **Networking**: Ingress, NetworkPolicy, and Service management
- **Autoscaling**: Horizontal Pod Autoscaling (HPA) with configurable metrics
- **Security**: RBAC, TLS, security contexts, and pod security policies
- **Monitoring**: Prometheus metrics, structured logging, and Jaeger tracing support
- **Admission Webhooks**: Mutating and validating webhooks for cluster specifications

## Architecture

### Operator Components

```
┌─────────────────────────────────────────────────────────┐
│           YAWL Kubernetes Operator                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Controller Manager                              │  │
│  │  ├─ YawlCluster Reconciler                      │  │
│  │  ├─ Event Recorder                              │  │
│  │  └─ Leader Election                             │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Admission Webhooks                              │  │
│  │  ├─ Mutating Webhook (defaults)                 │  │
│  │  └─ Validating Webhook (constraints)            │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Reconciliation Loop                             │  │
│  │  ├─ Watch YawlCluster Resources                 │  │
│  │  ├─ Create/Update Kubernetes Objects            │  │
│  │  ├─ Monitor Status & Conditions                 │  │
│  │  └─ Handle Errors & Retries                     │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
         ↓                          ↓                   ↓
    ┌─────────────┐          ┌──────────────┐    ┌──────────┐
    │ YawlCluster │          │ Deployment   │    │ Services │
    │    CRD      │          │ ConfigMap    │    │ Ingress  │
    │             │          │ PVC          │    │ Network  │
    └─────────────┘          │ HPA          │    │ Policy   │
                             └──────────────┘    └──────────┘
```

### Resource Ownership Chain

```
YawlCluster (custom resource)
  ├── Deployment (YAWL application)
  │   ├── ReplicaSet
  │   └── Pod
  ├── Service (ClusterIP/LoadBalancer/NodePort)
  ├── Ingress (if enabled)
  ├── NetworkPolicy (if enabled)
  ├── HorizontalPodAutoscaler (if enabled)
  ├── ConfigMap (cluster configuration)
  ├── PersistentVolumeClaim (data)
  └── PersistentVolumeClaim (logs, if enabled)
```

## Installation

### Prerequisites

- Kubernetes 1.24+
- kubectl configured to access your cluster
- Helm 3.0+ (for Helm installation)
- At least 2 CPU cores and 2GB memory available for operator

### Installation Methods

#### Method 1: Helm Installation (Recommended)

1. Add the YAWL Helm repository:
```bash
helm repo add yawl https://charts.yawlfoundation.org
helm repo update
```

2. Create the operator namespace:
```bash
kubectl create namespace yawl-operator-system
```

3. Install the operator:
```bash
helm install yawl-operator yawl/yawl-operator \
  --namespace yawl-operator-system \
  --values helm-chart-values.yaml
```

4. Verify installation:
```bash
kubectl get deployments -n yawl-operator-system
kubectl get crd | grep yawl
```

#### Method 2: Direct YAML Deployment

1. Apply the CRD:
```bash
kubectl apply -f yawl-cluster-crd.yaml
```

2. Apply the operator manifests:
```bash
kubectl apply -f operator-rbac.yaml
kubectl apply -f operator-deployment.yaml
kubectl apply -f admission-webhooks-config.yaml
```

3. Verify the operator is running:
```bash
kubectl get pods -n yawl-operator-system
```

### Installation Verification

```bash
# Check CRD is registered
kubectl get crd yawlclusters.yawl.io

# Check operator pod is running
kubectl get pods -n yawl-operator-system

# Check webhook configurations
kubectl get validatingwebhookconfigurations | grep yawl
kubectl get mutatingwebhookconfigurations | grep yawl
```

## Usage

### Creating a Basic YAWL Cluster

Create a file `yawl-cluster.yaml`:

```yaml
apiVersion: yawl.io/v1beta1
kind: YawlCluster
metadata:
  name: production-yawl
  namespace: yawl
spec:
  size: 3
  version: "5.2"

  database:
    type: postgresql
    host: postgres.example.com
    port: 5432
    name: yawl
    username: yawl
    passwordSecret:
      name: db-credentials
      key: password
    sslEnabled: true

  persistence:
    enabled: true
    storageClass: fast-ssd
    size: 20Gi

  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2
      memory: 2Gi

  networking:
    service:
      type: LoadBalancer
      port: 8080
    ingress:
      enabled: true
      className: nginx
      hosts:
        - host: yawl.example.com
          paths:
            - path: /
              pathType: Prefix

  scaling:
    enabled: true
    minReplicas: 2
    maxReplicas: 10
    targetCPUUtilization: 70
```

Apply the cluster:
```bash
kubectl apply -f yawl-cluster.yaml
```

### Checking Cluster Status

```bash
# List all YAWL clusters
kubectl get yawlclusters

# Get detailed cluster info
kubectl get yawlclusters production-yawl -o yaml

# Watch cluster status
kubectl get yawlclusters production-yawl --watch

# Get cluster events
kubectl describe yawlcluster production-yawl

# Check logs
kubectl logs -n yawl-operator-system -l app=yawl-operator -f
```

### Updating a Cluster

Edit the cluster specification:
```bash
kubectl edit yawlcluster production-yawl
```

The operator will detect changes and reconcile the cluster.

### Deleting a Cluster

```bash
# Graceful deletion
kubectl delete yawlcluster production-yawl

# Force deletion (if stuck)
kubectl delete yawlcluster production-yawl --grace-period=0 --force
```

## Configuration

### YawlCluster Specification

#### Basic Configuration

```yaml
spec:
  # Number of YAWL replicas (1-100)
  size: 3

  # YAWL version
  version: "5.2"

  # Container image settings
  image:
    repository: yawlfoundation/yawl
    tag: "5.2"
    pullPolicy: IfNotPresent
```

#### Database Configuration

```yaml
  database:
    # Database type: postgresql, mysql, or h2
    type: postgresql

    # Database host (not needed for h2)
    host: postgres.default.svc.cluster.local

    # Database port
    port: 5432

    # Database name
    name: yawl

    # Database username
    username: yawl

    # Reference to secret containing password
    passwordSecret:
      name: db-password-secret
      key: password

    # Enable SSL connections
    sslEnabled: true

    # Connection pool size (5-100)
    connectionPoolSize: 20
```

#### Persistence Configuration

```yaml
  persistence:
    # Enable persistence
    enabled: true

    # Storage class name
    storageClass: standard

    # Data volume size
    size: 10Gi

    # Enable logs persistence
    logsPersistence: true

    # Logs volume size
    logsSize: 5Gi
```

#### JVM Configuration

```yaml
  jvm:
    # JVM heap size
    heapSize: "1024m"

    # Initial heap size
    initialHeapSize: "512m"

    # Garbage collector type: G1GC, ParallelGC, CMS
    gcType: G1GC

    # Max GC pause time (milliseconds)
    gcPauseTarget: 200

    # Additional JVM options
    additionalOptions: "-XX:+UnlockDiagnosticVMOptions"
```

#### Networking Configuration

```yaml
  networking:
    # Service configuration
    service:
      type: LoadBalancer  # ClusterIP, LoadBalancer, NodePort
      port: 8080
      targetPort: 8080
      nodePort: 30080    # For NodePort services

    # Ingress configuration
    ingress:
      enabled: true
      className: nginx
      hosts:
        - host: yawl.example.com
          paths:
            - path: /
              pathType: Prefix
      tls:
        - secretName: yawl-tls
          hosts:
            - yawl.example.com

    # Network policy
    networkPolicy:
      enabled: true
```

#### Scaling Configuration

```yaml
  scaling:
    # Enable autoscaling
    enabled: true

    # Minimum replicas
    minReplicas: 1

    # Maximum replicas
    maxReplicas: 10

    # Target CPU utilization percentage
    targetCPUUtilization: 70

    # Target memory utilization percentage
    targetMemoryUtilization: 80
```

#### Security Configuration

```yaml
  security:
    # TLS settings
    tls:
      enabled: false
      certSecret: tls-cert
      keySecret: tls-key

    # RBAC settings
    rbac:
      enabled: true
      roleArn: "arn:aws:iam::ACCOUNT:role/yawl-operator"  # AWS IRSA

    # Security context
    securityContext:
      runAsNonRoot: true
      runAsUser: 1000
      fsGroup: 1000
      allowPrivilegeEscalation: false
```

#### Monitoring Configuration

```yaml
  monitoring:
    # Prometheus metrics
    prometheus:
      enabled: true
      port: 9090
      interval: "30s"
      path: "/metrics"

    # Logging
    logging:
      enabled: true
      level: INFO  # DEBUG, INFO, WARN, ERROR
      format: json  # text or json

    # Jaeger tracing
    jaeger:
      enabled: false
      endpoint: "http://jaeger-collector:14268/api/traces"
```

## Advanced Features

### High Availability Configuration

```yaml
apiVersion: yawl.io/v1beta1
kind: YawlCluster
metadata:
  name: ha-yawl-cluster
  namespace: yawl
spec:
  size: 5

  highAvailability:
    enabled: true
    podAntiAffinity: required  # required or preferred

    affinity:
      podAntiAffinity:
        requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
                - key: app
                  operator: In
                  values:
                    - yawl
            topologyKey: kubernetes.io/hostname

    tolerations:
      - key: dedicated
        operator: Equal
        value: yawl
        effect: NoSchedule
```

### Backup Configuration

```yaml
  backup:
    enabled: true
    schedule: "0 2 * * *"  # 2 AM daily
    retention: 7
    destination: "s3://yawl-backups/"
```

### Resource Limits with Requests

```yaml
  resources:
    requests:
      cpu: "1"
      memory: "2Gi"
    limits:
      cpu: "4"
      memory: "4Gi"
```

### Custom Monitoring

```yaml
  monitoring:
    prometheus:
      enabled: true
      port: 9090
      interval: "15s"
      path: "/actuator/prometheus"

    logging:
      enabled: true
      level: DEBUG
      format: json

    jaeger:
      enabled: true
      endpoint: "http://jaeger-agent:6831"
```

## Troubleshooting

### Cluster Not Reaching Ready State

1. Check the cluster status:
```bash
kubectl describe yawlcluster production-yawl
```

2. Check pod status:
```bash
kubectl get pods -n yawl
kubectl logs -n yawl <pod-name>
```

3. Check operator logs:
```bash
kubectl logs -n yawl-operator-system -l app=yawl-operator
```

### Database Connection Issues

1. Verify database secret exists:
```bash
kubectl get secret -n yawl db-credentials
```

2. Test database connectivity:
```bash
kubectl run -it --rm debug --image=postgres:14 -- \
  psql -h postgres.example.com -U yawl -d yawl
```

3. Check ConfigMap:
```bash
kubectl get configmap -n yawl
kubectl describe configmap production-yawl-config -n yawl
```

### Persistent Volume Issues

1. Check PVC status:
```bash
kubectl get pvc -n yawl
kubectl describe pvc production-yawl-data -n yawl
```

2. Check storage class:
```bash
kubectl get storageclass
kubectl describe storageclass fast-ssd
```

### Autoscaling Issues

1. Check HPA status:
```bash
kubectl get hpa -n yawl
kubectl describe hpa production-yawl-hpa -n yawl
```

2. Check metrics:
```bash
kubectl get --raw /apis/custom.metrics.k8s.io/v1beta1/nodes
```

### Webhook Issues

1. Check webhook configurations:
```bash
kubectl get validatingwebhookconfigurations | grep yawl
kubectl describe validatingwebhookconfig yawl-validating-webhook
```

2. Check webhook service:
```bash
kubectl get svc -n yawl-operator-system
kubectl get endpoints -n yawl-operator-system
```

## Development

### Building the Operator

Prerequisites:
- Go 1.21+
- Docker
- Make

Build steps:
```bash
# Clone repository
git clone https://github.com/yawlfoundation/yawl-operator.git
cd yawl-operator

# Install dependencies
go mod download

# Build operator
make build

# Build Docker image
make docker-build

# Push image to registry
make docker-push
```

### Running the Operator Locally

```bash
# Set up local cluster
make kind-create

# Install CRDs
make install

# Run operator
make run
```

### Testing

```bash
# Run unit tests
make test

# Run integration tests
make test-integration

# Generate test coverage
make coverage
```

### Code Generation

```bash
# Generate CRD manifests
make manifests

# Generate client code
make generate
```

## Best Practices

1. **Resource Requests**: Always set appropriate resource requests and limits
2. **Database Backups**: Enable database backups for production clusters
3. **Monitoring**: Enable Prometheus metrics and set up alerting
4. **Network Policies**: Enable NetworkPolicy for security
5. **High Availability**: Use pod anti-affinity and multiple replicas
6. **Security Context**: Always run as non-root user
7. **Persistence**: Enable persistence in production environments
8. **Ingress**: Use Ingress with TLS for external access
9. **RBAC**: Configure appropriate RBAC for multi-tenant environments
10. **Logging**: Use structured logging (JSON format) for easy parsing

## Support and Documentation

- **Issue Tracker**: https://github.com/yawlfoundation/yawl-operator/issues
- **Documentation**: https://docs.yawlfoundation.org/operator
- **Community**: https://www.yawlfoundation.org/community

## License

This operator is licensed under the Apache License 2.0. See LICENSE file for details.
