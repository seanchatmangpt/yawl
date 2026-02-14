# YAWL Kubernetes Operator - Design Pattern & Architecture

## Executive Summary

This document describes the complete design pattern and architecture of the YAWL Kubernetes Operator, which implements cloud-native deployment and lifecycle management for YAWL Workflow Engine clusters on Kubernetes.

## Table of Contents

1. [Overview](#overview)
2. [Operator Pattern](#operator-pattern)
3. [Architecture](#architecture)
4. [CRD Design](#crd-design)
5. [Controller Implementation](#controller-implementation)
6. [Webhook Strategy](#webhook-strategy)
7. [Resource Management](#resource-management)
8. [State Machine](#state-machine)
9. [Error Handling](#error-handling)
10. [Scaling Strategy](#scaling-strategy)

## Overview

### What is the YAWL Kubernetes Operator?

The YAWL Kubernetes Operator is a software application that uses Kubernetes' extensibility mechanisms to manage YAWL cluster deployments. It follows the Kubernetes Operator pattern to provide:

- **Declarative Infrastructure**: Define clusters using Kubernetes manifests
- **Automated Lifecycle**: Automatic creation, updating, and deletion
- **Operational Intelligence**: Health monitoring, scaling, and recovery
- **Enterprise Features**: HA, security, monitoring, backup

### Key Benefits

| Benefit | Description |
|---------|-------------|
| **Native K8s Experience** | Use kubectl and standard Kubernetes tools |
| **Infrastructure as Code** | Version control your cluster definitions |
| **Automation** | Reduce manual operational overhead |
| **Reliability** | Built-in health checks and recovery |
| **Scalability** | Easy horizontal scaling |
| **Multi-tenancy** | Run multiple clusters in same K8s cluster |

## Operator Pattern

### Pattern Overview

The Kubernetes Operator pattern extends Kubernetes with application-specific knowledge. It combines three elements:

```
┌─────────────────────────────────────────────────────┐
│           Kubernetes Operator Pattern               │
├─────────────────────────────────────────────────────┤
│                                                     │
│  1. CRD (Custom Resource Definition)               │
│     └─ Defines the cluster API (YawlCluster)      │
│                                                     │
│  2. Controller                                      │
│     └─ Watches resources, reconciles state         │
│                                                     │
│  3. Webhooks (Admission Controllers)               │
│     └─ Validate & mutate specifications            │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Pattern Benefits for YAWL

1. **Domain Knowledge**: Operator understands YAWL's requirements
2. **Automation**: Repeatable, reliable deployments
3. **Self-Healing**: Detects and corrects issues
4. **Observability**: Native Kubernetes integration
5. **Community**: Follows established Kubernetes patterns

## Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Kubernetes API Server                      │
│                    (stores YawlCluster)                       │
└──────────────────────────────────────────────────────────────┘
                            ↑
                            │ Watch
                            │
┌──────────────────────────────────────────────────────────────┐
│              YAWL Operator Controller                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Reconciliation Loop:                                        │
│  1. Watch YawlCluster resources                             │
│  2. Fetch desired spec                                       │
│  3. Fetch current state                                      │
│  4. Compare and calculate changes                            │
│  5. Create/Update/Delete resources                          │
│  6. Update status & conditions                              │
│  7. Requeue for continuous reconciliation                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
                            │
                            │ Create/Update/Delete
                            ↓
    ┌─────────────────────────────────────────────────────┐
    │       Kubernetes Resources (Managed)                 │
    ├─────────────────────────────────────────────────────┤
    │                                                     │
    │  ├─ Deployment (YAWL Pods)                        │
    │  ├─ Service (Network Access)                      │
    │  ├─ ConfigMap (Configuration)                     │
    │  ├─ PersistentVolumeClaim (Storage)              │
    │  ├─ Ingress (External Access)                     │
    │  ├─ NetworkPolicy (Security)                      │
    │  ├─ HorizontalPodAutoscaler (Scaling)            │
    │  └─ ServiceAccount & RBAC                         │
    │                                                     │
    └─────────────────────────────────────────────────────┘
                            ↓
    ┌─────────────────────────────────────────────────────┐
    │         YAWL Cluster (Running)                      │
    │  Multiple pods running YAWL application            │
    └─────────────────────────────────────────────────────┘
```

### Component Interaction

```
User Action                 Kubernetes                    Operator
    │                           │                            │
    ├──kubectl apply───────────→├─ Store YawlCluster        │
    │                           │                            │
    │                           ├──Watch notification───────→│
    │                           │                            │
    │                           │←──Webhook validation───────┤
    │                           │  (validate & mutate)       │
    │                           │                            │
    │                           │←──Create resources────────┤
    │                           │  (Deployment, Service, ...)
    │                           │                            │
    │                           │←──Update status───────────┤
    │                           │  (Phase: Creating→Running)
    │                           │                            │
    │←──kubectl get ────────────┤  (returns status)          │
    │  yawlclusters            │                            │
```

## CRD Design

### YawlCluster CRD Structure

The YawlCluster CRD defines the complete specification for a YAWL cluster deployment.

#### API Versions

The CRD supports two versions:
- **v1alpha1**: Initial version, served but not stored
- **v1beta1**: Current stable version, served and stored

This allows for future API evolution while maintaining backward compatibility.

#### Spec Structure

```
YawlClusterSpec
├── size (integer, 1-100)
├── version (string)
├── image (ImageSpec)
│   ├── repository
│   ├── tag
│   ├── pullPolicy
│   └── pullSecrets
├── database (DatabaseSpec)
│   ├── type (postgresql/mysql/h2)
│   ├── host
│   ├── port
│   ├── credentials
│   └── connectionPoolSize
├── persistence (PersistenceSpec)
│   ├── enabled
│   ├── storageClass
│   ├── size
│   └── logsPersistence
├── resources (ResourcesSpec)
│   ├── requests (CPU, Memory)
│   └── limits (CPU, Memory)
├── jvm (JVMSpec)
│   ├── heapSize
│   ├── gcType
│   └── additionalOptions
├── networking (NetworkingSpec)
│   ├── service (ServiceSpec)
│   ├── ingress (IngressSpec)
│   └── networkPolicy (NetworkPolicySpec)
├── security (SecuritySpec)
│   ├── tls
│   ├── rbac
│   └── securityContext
├── highAvailability (HighAvailabilitySpec)
│   ├── enabled
│   ├── podAntiAffinity
│   ├── affinity
│   └── tolerations
├── monitoring (MonitoringSpec)
│   ├── prometheus
│   ├── logging
│   └── jaeger
├── scaling (ScalingSpec)
│   ├── enabled
│   ├── minReplicas
│   ├── maxReplicas
│   └── targetUtilization
└── backup (BackupSpec)
    ├── enabled
    ├── schedule
    └── destination
```

#### Status Structure

```
YawlClusterStatus
├── phase (Pending/Creating/Running/Updating/Degraded/Failed/Terminating)
├── conditions (array)
│   ├── Ready
│   ├── DatabaseConnected
│   └── PodsReady
├── replicas (current count)
├── readyReplicas (ready count)
├── updatedReplicas (updated count)
├── observedGeneration (for detecting stale status)
├── lastUpdateTime
├── clusterVersion
├── endpoint
├── databaseStatus
└── errors (error messages)
```

### Schema Validation

The CRD includes OpenAPI schema validation:

```yaml
# Example validation rules
- Size must be 1-100
- Version format: X.Y or X.Y.Z
- Database port: 1-65535
- Connection pool: 5-100
- CPU/Memory requests cannot exceed limits
- NodePort range: 30000-32767
- minReplicas ≤ maxReplicas
- GC pause target: 10-1000ms
```

## Controller Implementation

### Reconciliation Loop

The controller's main function is the reconciliation loop, which runs continuously:

```go
func (r *YawlClusterReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
    // 1. Fetch YawlCluster resource
    yawlCluster := &YawlCluster{}
    r.Get(ctx, req.NamespacedName, yawlCluster)

    // 2. Handle deletion (if marked for deletion)
    if yawlCluster.DeletionTimestamp != nil {
        return r.handleDeletion(ctx, yawlCluster)
    }

    // 3. Add finalizer if needed
    if !containsFinalizer(yawlCluster) {
        addFinalizer(yawlCluster)
    }

    // 4. Validate specification
    if err := r.validateClusterSpec(yawlCluster); err != nil {
        r.updateStatusFailed(ctx, yawlCluster, err)
        return ctrl.Result{RequeueAfter: 30s}, nil
    }

    // 5. Reconcile all owned resources
    r.reconcileNamespace(ctx, yawlCluster)
    r.reconcileServiceAccount(ctx, yawlCluster)
    r.reconcileConfigMap(ctx, yawlCluster)
    r.reconcilePVC(ctx, yawlCluster)
    r.reconcileDeployment(ctx, yawlCluster)
    r.reconcileService(ctx, yawlCluster)
    r.reconcileIngress(ctx, yawlCluster)
    r.reconcileNetworkPolicy(ctx, yawlCluster)
    r.reconcileHPA(ctx, yawlCluster)

    // 6. Update status
    r.updateStatus(ctx, yawlCluster, phases.Running)

    // 7. Requeue for continuous reconciliation
    return ctrl.Result{RequeueAfter: 30s}, nil
}
```

### Reconciliation Process

```
Input: YawlCluster resource
    ↓
[1] Validate specification
    ├─ Size constraints
    ├─ Database configuration
    ├─ Resource limits
    └─ Update rules
    ↓ Valid
[2] Reconcile Namespace
    ├─ Create if missing
    └─ Label appropriately
    ↓
[3] Reconcile ServiceAccount & RBAC
    ├─ Create ServiceAccount
    └─ Create ClusterRole & RoleBinding
    ↓
[4] Reconcile ConfigMap
    └─ Create configuration from spec
    ↓
[5] Reconcile PersistentVolumeClaims
    ├─ Data PVC
    └─ Logs PVC (if enabled)
    ↓
[6] Reconcile Deployment
    ├─ Create or update
    ├─ Set resources, affinity, probes
    └─ Configure init containers
    ↓
[7] Reconcile Service
    ├─ ClusterIP, LoadBalancer, or NodePort
    └─ Configure ports
    ↓
[8] Reconcile Ingress (if enabled)
    ├─ Create hosts
    ├─ Configure TLS
    └─ Set routing rules
    ↓
[9] Reconcile NetworkPolicy (if enabled)
    ├─ Ingress rules
    └─ Egress rules
    ↓
[10] Reconcile HPA (if enabled)
    ├─ Set min/max replicas
    ├─ Configure metrics
    └─ Set scale limits
    ↓
[11] Update Status
    ├─ Phase: Running
    ├─ Conditions: Ready
    ├─ Replicas count
    └─ Endpoint information
    ↓
Output: Status updated, resources synced
```

### Resource Ownership Chain

The operator uses Kubernetes owner references to maintain proper resource hierarchy:

```go
// Set controller reference
controllerutil.SetControllerReference(yawlCluster, deployment, scheme)

// This creates:
// YawlCluster owns → Deployment owns → ReplicaSet owns → Pod
```

Benefits:
- Automatic garbage collection
- Proper hierarchy in kubectl describe
- No resource conflicts or conflicts
- Clear ownership model

### Finalizer Pattern

The operator uses finalizers to ensure graceful deletion:

```go
const finalizerName = "yawlcluster.yawl.io/finalizer"

// On creation: Add finalizer
if !containsFinalizer(yawlCluster) {
    addFinalizer(yawlCluster)
}

// On deletion: Execute cleanup before removing finalizer
if isDeletionMarked(yawlCluster) {
    // Clean up external resources
    performCleanup(ctx, yawlCluster)

    // Remove finalizer (allows K8s to delete)
    removeFinalizer(yawlCluster)
}
```

## Webhook Strategy

### Webhooks Overview

The operator uses two types of admission webhooks:

#### 1. Mutating Webhook

**Purpose**: Set default values and normalize specifications

**When it runs**: On CREATE and UPDATE

**Actions**:
```
Input: YawlCluster with missing fields
    ↓
[1] Set image defaults
    └─ repository: yawlfoundation/yawl
       tag: spec.version
       pullPolicy: IfNotPresent
    ↓
[2] Set database defaults
    └─ port: 5432 (postgresql) or 3306 (mysql)
       connectionPoolSize: 20
    ↓
[3] Set resource defaults
    └─ requests: 500m CPU, 1Gi memory
       limits: 2 CPU, 2Gi memory
    ↓
[4] Set JVM defaults
    └─ heapSize: 1024m
       gcType: G1GC
    ↓
[5] Set networking defaults
    └─ service.port: 8080
       service.type: ClusterIP
    ↓
[6] Set scaling defaults
    └─ enabled: true
       minReplicas: 1
       maxReplicas: 10
    ↓
Output: YawlCluster with all fields populated
```

**Benefits**:
- Users specify only required fields
- Consistent defaults across clusters
- Reduced manifest complexity

#### 2. Validating Webhook

**Purpose**: Enforce business rules and constraints

**When it runs**: On CREATE and UPDATE

**Validations**:

```
Input: YawlCluster specification
    ↓
[1] Size validation
    ├─ 1 ≤ size ≤ 100
    └─ Reject if outside range
    ↓
[2] Version validation
    ├─ Format: X.Y or X.Y.Z
    └─ Reject if invalid format
    ↓
[3] Database validation
    ├─ Type: postgresql, mysql, or h2
    ├─ Port: 1-65535
    ├─ Host required for external databases
    └─ Reject if constraints violated
    ↓
[4] Resource validation
    ├─ CPU request ≤ CPU limit
    ├─ Memory request ≤ memory limit
    └─ Reject if violated
    ↓
[5] Scaling validation
    ├─ minReplicas ≤ maxReplicas
    ├─ CPU target: 1-100%
    ├─ Memory target: 1-100%
    └─ Reject if violated
    ↓
[6] Update validation (on UPDATE only)
    ├─ Database type cannot change
    ├─ Warn on significant scale-down
    └─ Reject breaking changes
    ↓
[7] Network validation
    ├─ Service port: 1-65535
    ├─ NodePort: 30000-32767 (if NodePort type)
    └─ Reject if invalid
    ↓
Output: Accept or reject the request
```

**Benefits**:
- Prevent invalid configurations
- Early error detection
- User guidance via error messages
- Enforce business rules

### Webhook Architecture

```
┌─────────────────────────────────────────┐
│     Kubernetes API Server               │
│   (handles admission requests)          │
└─────────────────────────────────────────┘
            ↓
┌─────────────────────────────────────────┐
│  Mutating Admission Controllers         │
│  (runs first)                           │
│  └─ Normalizes specifications           │
│     Sets defaults                       │
└─────────────────────────────────────────┘
            ↓
┌─────────────────────────────────────────┐
│  Validating Admission Controllers       │
│  (runs second)                          │
│  └─ Enforces constraints                │
│     Rejects invalid specs               │
└─────────────────────────────────────────┘
            ↓
    Accept or Reject
```

## Resource Management

### Deployment Management

The operator creates and manages a Kubernetes Deployment for YAWL:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: <cluster-name>
  ownerReferences:  # Links back to YawlCluster
  - apiVersion: yawl.io/v1beta1
    kind: YawlCluster
    name: <cluster-name>

spec:
  replicas: <size>  # From YawlCluster.spec.size
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0

  template:
    spec:
      containers:
      - name: yawl
        image: <repository>:<tag>
        resources:
          requests:
            cpu: <from spec.resources.requests.cpu>
            memory: <from spec.resources.requests.memory>
          limits:
            cpu: <from spec.resources.limits.cpu>
            memory: <from spec.resources.limits.memory>

        env:
        - name: YAWL_DB_TYPE
          valueFrom:
            configMapKeyRef:
              name: <cluster-name>-config
              key: database.type
        # ... more environment variables

        volumeMounts:
        - name: data
          mountPath: /data
        - name: logs
          mountPath: /logs

        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30

        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10

      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - yawl
              topologyKey: kubernetes.io/hostname

      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: <cluster-name>-data
      - name: logs
        persistentVolumeClaim:
          claimName: <cluster-name>-logs
```

### Service Management

The operator creates appropriate service types:

```
YawlCluster networking.service.type
    ↓
    ├─ ClusterIP → Internal service
    │   └─ Access within cluster only
    │
    ├─ LoadBalancer → External service
    │   └─ Cloud provider provision LB
    │
    └─ NodePort → External service
        └─ Accessible on node port
```

### ConfigMap Management

Configuration is stored in ConfigMap:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: <cluster-name>-config
  ownerReferences:
  - apiVersion: yawl.io/v1beta1
    kind: YawlCluster
    name: <cluster-name>

data:
  database.type: postgresql
  database.host: postgres.example.com
  database.port: "5432"
  database.name: yawl
  jvm.heap.size: "1024m"
  jvm.gc.type: G1GC
```

### PVC Management

Data persistence through PersistentVolumeClaims:

```
YawlCluster
└── persistence.enabled = true
    ├── Data PVC
    │   └── size: 10Gi
    │       storageClass: standard
    │
    └── Logs PVC (if enabled)
        └── size: 5Gi
            storageClass: standard
```

## State Machine

### Cluster Lifecycle

```
                     ┌─────────────────┐
                     │     Pending     │
                     │ (initial state) │
                     └────────┬────────┘
                              │
                         user apply
                              │
                              ↓
                     ┌─────────────────┐
                     │    Creating     │
                     │ (reconciling)   │
                     └────────┬────────┘
                              │
                    all resources ready
                              │
                              ↓
                     ┌─────────────────┐
                     │    Running      │ ←──────────┐
                     │ (stable state)  │           │
                     └────────┬────────┘           │
                              │                   │
                              ├─ user update ────┤
                              │                   │
                              ├─ reconcile error ─┐
                              │                  │
                              ↓                  │
                     ┌─────────────────┐         │
                     │    Updating     │─────────┤
                     │ (changes applied)        │
                     └────────┬────────┘         │
                              │                 │
                    ↓ successful ↓ error        │
                    ↓           ↓               │
              ┌─────────────────────────┐      │
              │    Running (restored)   │──────┘
              └─────────────────────────┘
                              │
                        pods not ready
                              │
                              ↓
                     ┌─────────────────┐
                     │   Degraded      │
                     │ (partial outage)│
                     └────────┬────────┘
                              │
                    ↓ pods recovered ↓ permanent error
                    ↓                ↓
              ┌─────────────────────────┐
              │    Running (recovered)  │
              └────────┬────────────────┘
                       │
                   user delete
                       │
                       ↓
            ┌──────────────────────┐
            │   Terminating        │
            │ (cleanup in progress)│
            └──────────┬───────────┘
                       │
              cleanup completed
                       │
                       ↓
            Resource deleted from API
```

### Phase Transitions

| From | To | Trigger | Action |
|------|----|---------| -------|
| Pending | Creating | Validation passed | Begin reconciliation |
| Creating | Running | All resources ready | Mark as ready |
| Running | Updating | User update detected | Apply changes |
| Updating | Running | Update completed successfully | Resume operation |
| Running | Degraded | Pods not ready | Flag reduced capacity |
| Degraded | Running | Pods recovered | Resume operation |
| Running → (any) | Failed | Unrecoverable error | Report error |
| (any) | Terminating | User delete | Initiate cleanup |
| Terminating | Deleted | Cleanup complete | Remove finalizer |

## Error Handling

### Error Scenarios and Recovery

#### 1. Database Connection Failure

```
Detection:
  └─ Deployment pod fails to connect to database

Handling:
  ├─ Retry connection with exponential backoff
  ├─ Update status.databaseStatus to "Disconnected"
  ├─ Emit event: "DatabaseConnectionFailed"
  └─ Continue retrying until successful

Recovery:
  └─ Once database is reachable, pods reconnect automatically
```

#### 2. Resource Quota Exceeded

```
Detection:
  └─ API server rejects resource creation due to quota

Handling:
  ├─ Catch error in reconciliation
  ├─ Update status.phase to "Failed"
  ├─ Add error message to status.errors
  ├─ Emit warning event
  └─ Requeue with extended backoff

Action Required:
  └─ User must increase quota or reduce resource requests
```

#### 3. PVC Binding Failure

```
Detection:
  └─ PVC remains "Pending" for extended duration

Handling:
  ├─ Monitor PVC status
  ├─ Update status.phase to "Degraded"
  ├─ Check storage class availability
  └─ Emit diagnostic event

Troubleshooting:
  ├─ Verify storage class exists
  ├─ Check storage capacity
  └─ Examine PVC events
```

#### 4. Pod Startup Failure

```
Detection:
  └─ Pod reaches CrashLoopBackOff state

Handling:
  ├─ Kubernetes automatically restarts pod
  ├─ After N failures, mark pod as unhealthy
  ├─ Operator detects unhealthy replica
  ├─ Update status.readyReplicas
  └─ Status transitions to "Degraded"

Investigation:
  ├─ Check pod logs
  ├─ Review environment variables
  └─ Verify database connectivity
```

### Error Propagation

```
Controller Error
    ↓
├─ Update YawlCluster.status.errors
│   └─ Add error message
│
├─ Update YawlCluster.status.phase
│   └─ Degraded or Failed
│
├─ Emit Kubernetes event
│   └─ EventType: Warning or Error
│
├─ Log error
│   └─ Structured log with context
│
└─ Requeue reconciliation
    └─ Exponential backoff retry
```

## Scaling Strategy

### Horizontal Pod Autoscaling

The operator creates an HPA resource if scaling is enabled:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: <cluster-name>-hpa
  ownerReferences:
  - apiVersion: yawl.io/v1beta1
    kind: YawlCluster

spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: <cluster-name>

  minReplicas: <spec.scaling.minReplicas>
  maxReplicas: <spec.scaling.maxReplicas>

  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: <spec.scaling.targetCPUUtilization>

  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: <spec.scaling.targetMemoryUtilization>
```

### Scaling Process

```
┌──────────────────────────────┐
│  Collect metrics (every 15s) │
│  ├─ Current CPU usage        │
│  └─ Current memory usage     │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│  Calculate desired replicas  │
│  using formulas:             │
│  ├─ desiredReplicas_cpu =    │
│  │   ceil(current / target)  │
│  └─ desiredReplicas_mem =    │
│      ceil(current / target)  │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│  Take maximum of metrics:    │
│  desired = max(cpu, memory)  │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│  Clamp to min/max:           │
│  desired = clamp(            │
│    desired,                  │
│    minReplicas,              │
│    maxReplicas               │
│  )                           │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│  If desired ≠ current:       │
│  └─ Scale up or down         │
│     (with cooldown period)   │
└──────────────────────────────┘
```

### Manual vs Automatic Scaling

```
Manual Scaling:
  └─ kubectl scale yawlcluster prod-yawl --replicas=5
     └─ Directly updates Deployment.spec.replicas

Automatic Scaling:
  └─ HPA monitors metrics and adjusts replicas
     ├─ If CPU > targetCPUUtilization
     │  └─ Scale up (up to maxReplicas)
     └─ If CPU < targetCPUUtilization
        └─ Scale down (down to minReplicas)
```

## Best Practices

### 1. Resource Configuration

```yaml
# DO: Set appropriate requests and limits
spec:
  resources:
    requests:
      cpu: 1
      memory: 2Gi
    limits:
      cpu: 4
      memory: 4Gi

# DON'T: Leave resources unspecified
spec: {}  # Bad - no guarantees on scheduling
```

### 2. Database Configuration

```yaml
# DO: Use external database for production
spec:
  database:
    type: postgresql
    host: cloudsql-proxy.default.svc
    port: 5432
    sslEnabled: true

# DON'T: Use H2 for production
spec:
  database:
    type: h2  # Not suitable for production
```

### 3. High Availability

```yaml
# DO: Enable HA for production
spec:
  size: 3
  highAvailability:
    enabled: true
    podAntiAffinity: required

# DON'T: Single replica in production
spec:
  size: 1  # No fault tolerance
```

### 4. Persistence

```yaml
# DO: Enable persistence
spec:
  persistence:
    enabled: true
    storageClass: fast-ssd
    size: 50Gi

# DON'T: Disable persistence
spec:
  persistence:
    enabled: false  # Data loss on restart
```

### 5. Monitoring

```yaml
# DO: Enable monitoring
spec:
  monitoring:
    prometheus:
      enabled: true
    logging:
      enabled: true
      format: json

# DON'T: Disable monitoring
spec:
  monitoring:
    prometheus:
      enabled: false  # Lost visibility
```

## Summary

The YAWL Kubernetes Operator provides a production-ready, cloud-native deployment platform for YAWL Workflow Engine clusters. By following the Kubernetes Operator pattern, it enables:

- **Infrastructure as Code**: Define clusters declaratively
- **Automation**: Reduce manual operations
- **Reliability**: Built-in health and recovery
- **Scalability**: Easy horizontal and vertical scaling
- **Observability**: Native monitoring integration
- **Security**: RBAC, TLS, network policies

The operator is designed to be:
- **User-friendly**: Simple API with sensible defaults
- **Robust**: Comprehensive error handling and recovery
- **Extensible**: Multiple configuration options
- **Production-ready**: Enterprise features and best practices

---

**For detailed usage instructions, see [README.md](README.md) and [OPERATOR_DOCUMENTATION.md](OPERATOR_DOCUMENTATION.md)**
