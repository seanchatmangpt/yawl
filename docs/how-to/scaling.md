# YAWL v6.0.0 Scaling and Observability Guide
**Enterprise Performance Optimization**  
**Version**: 5.2  
**Date**: 2026-02-15

---

## Table of Contents

1. [Horizontal Scaling Strategy](#1-horizontal-scaling-strategy)
2. [Vertical Scaling Guidelines](#2-vertical-scaling-guidelines)
3. [Connection Pool Optimization](#3-connection-pool-optimization)
4. [Resource Limit Tuning](#4-resource-limit-tuning)
5. [Observability Architecture](#5-observability-architecture)
6. [Metrics and Alerting](#6-metrics-and-alerting)
7. [Distributed Tracing](#7-distributed-tracing)
8. [Log Aggregation](#8-log-aggregation)
9. [Performance Benchmarking](#9-performance-benchmarking)

---

## 1. Horizontal Scaling Strategy

### 1.1 Autoscaling Configuration

**Horizontal Pod Autoscaler (HPA)**:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine-hpa
  namespace: yawl
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 2
  maxReplicas: 20
  metrics:
    # CPU-based scaling
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    
    # Memory-based scaling
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    
    # Custom metric: Request latency
    - type: Pods
      pods:
        metric:
          name: yawl_request_duration_seconds_p95
        target:
          type: AverageValue
          averageValue: "500m"  # 500ms
    
    # Custom metric: Active work items
    - type: Pods
      pods:
        metric:
          name: yawl_active_workitems
        target:
          type: AverageValue
          averageValue: "100"  # 100 work items per pod
  
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
        - type: Pods
          value: 2
          periodSeconds: 60
      selectPolicy: Max
    
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
      selectPolicy: Min
```

**Deployment**:
```bash
# Deploy HPA
kubectl apply -f hpa.yaml

# Verify metrics
kubectl get hpa -n yawl
kubectl describe hpa yawl-engine-hpa -n yawl

# Test autoscaling
kubectl run -it --rm load-generator --image=busybox -- /bin/sh -c \
  "while true; do wget -q -O- http://yawl-engine:8080/engine/ia; done"
```

---

### 1.2 Multi-Cluster Scaling

**Federation Architecture**:

```
┌─────────────────────────────────────────────────────┐
│              Global Load Balancer                    │
│         (GCP GLB / AWS Route 53 / Azure FD)          │
└─────────────────┬───────────────────────────────────┘
                  │
        ┌─────────┼─────────┐
        │         │         │
   ┌────▼────┐ ┌──▼─────┐ ┌▼────────┐
   │ GKE     │ │ EKS    │ │ AKS     │
   │ (US-C1) │ │ (US-E1)│ │ (EU-W1) │
   └────┬────┘ └───┬────┘ └─┬───────┘
        │          │         │
   ┌────▼────┐ ┌───▼────┐ ┌─▼───────┐
   │ Engine  │ │ Engine │ │ Engine  │
   │ 3 pods  │ │ 3 pods │ │ 3 pods  │
   └────┬────┘ └───┬────┘ └─┬───────┘
        │          │         │
   ┌────▼────┐ ┌───▼────┐ ┌─▼───────┐
   │ Cloud   │ │ RDS    │ │ Azure   │
   │ SQL     │ │ Postgres│ │ Postgres│
   └─────────┘ └────────┘ └─────────┘
```

**Configuration**:
```bash
# Install Kubernetes Federation v2
git clone https://github.com/kubernetes-sigs/kubefed
cd kubefed
./scripts/deploy-kubefed.sh

# Join clusters
kubefedctl join gke-us-central1 --cluster-context gke-us-central1 --host-cluster-context federation
kubefedctl join eks-us-east1 --cluster-context eks-us-east1 --host-cluster-context federation
kubefedctl join aks-eu-west1 --cluster-context aks-eu-west1 --host-cluster-context federation

# Deploy federated YAWL
kubectl apply -f - <<EOF
apiVersion: types.kubefed.io/v1beta1
kind: FederatedDeployment
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  template:
    metadata:
      labels:
        app: yawl-engine
    spec:
      replicas: 3
      selector:
        matchLabels:
          app: yawl-engine
      template:
        metadata:
          labels:
            app: yawl-engine
        spec:
          containers:
            - name: yawl-engine
              image: yawl/engine:5.2
  placement:
    clusters:
      - name: gke-us-central1
      - name: eks-us-east1
      - name: aks-eu-west1
  overrides:
    - clusterName: gke-us-central1
      clusterOverrides:
        - path: "/spec/replicas"
          value: 5
    - clusterName: eks-us-east1
      clusterOverrides:
        - path: "/spec/replicas"
          value: 3
    - clusterName: aks-eu-west1
      clusterOverrides:
        - path: "/spec/replicas"
          value: 2
EOF
```

---

## 2. Vertical Scaling Guidelines

### 2.1 Resource Sizing Matrix

| Workload Profile | vCPU | Memory | Replicas | Max RPS | Max Cases/hr |
|------------------|------|--------|----------|---------|--------------|
| **Development** | 0.5 | 512Mi | 1 | 10 | 100 |
| **Staging** | 1.0 | 1Gi | 2 | 50 | 500 |
| **Production (Small)** | 2.0 | 2Gi | 3 | 200 | 2,000 |
| **Production (Medium)** | 4.0 | 4Gi | 5 | 500 | 5,000 |
| **Production (Large)** | 8.0 | 8Gi | 10 | 1,000 | 10,000 |
| **Enterprise** | 16.0 | 16Gi | 20 | 2,000 | 20,000 |

**Deployment Example** (Medium):
```yaml
resources:
  requests:
    cpu: "4000m"      # 4 vCPU guaranteed
    memory: "4Gi"     # 4 GiB guaranteed
  limits:
    cpu: "8000m"      # 8 vCPU burst
    memory: "8Gi"     # 8 GiB max
```

---

### 2.2 JVM Heap Tuning

**Formula**: `Xmx = 75% of container memory limit`

```bash
# For 2Gi container:
JAVA_OPTS="-Xms512m -Xmx1536m"  # Xmx = 0.75 * 2048Mi = 1536Mi

# For 4Gi container:
JAVA_OPTS="-Xms1g -Xmx3g"

# For 8Gi container:
JAVA_OPTS="-Xms2g -Xmx6g"

# Full production settings:
JAVA_OPTS="-Xms2g \
  -Xmx6g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:G1ReservePercent=20 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch \
  -XX:MaxMetaspaceSize=512m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/opt/yawl/logs/heapdump.hprof \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8"
```

**ConfigMap Update**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  JAVA_OPTS: "-Xms2g -Xmx6g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

---

### 2.3 Vertical Pod Autoscaler (VPA)

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: yawl-engine-vpa
  namespace: yawl
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  updatePolicy:
    updateMode: "Auto"  # or "Initial" or "Off"
  resourcePolicy:
    containerPolicies:
      - containerName: yawl-engine
        minAllowed:
          cpu: 500m
          memory: 1Gi
        maxAllowed:
          cpu: 8000m
          memory: 16Gi
        controlledResources: ["cpu", "memory"]
```

**Deployment**:
```bash
# Install VPA
git clone https://github.com/kubernetes/autoscaler.git
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh

# Apply VPA
kubectl apply -f vpa.yaml

# Check recommendations
kubectl describe vpa yawl-engine-vpa -n yawl
```

---

## 3. Connection Pool Optimization

### 3.1 HikariCP Best Practices

**Configuration File**: `/home/user/yawl/database/connection-pooling/hikaricp/hikaricp.properties`

**Optimal Pool Sizing**:
```properties
# Formula: connections = (cores * 2) + effective_spindle_count
# For PostgreSQL on SSD: (4 * 2) + 1 = 9
# YAWL uses 20 to handle burst traffic

# Pool size per pod
maximumPoolSize=20
minimumIdle=5

# With 5 pods: 5 * 20 = 100 total connections
# Database max_connections should be >= 150 (buffer for admin)
```

**Connection Lifecycle**:
```properties
# Connection timeouts
connectionTimeout=30000       # 30s to acquire (prevents indefinite wait)
idleTimeout=600000            # 10min idle before eviction
maxLifetime=1800000           # 30min max lifetime (forces rotation)

# Validation
connectionTestQuery=SELECT 1  # Lightweight health check
validationTimeout=5000        # 5s to validate

# Leak detection
leakDetectionThreshold=60000  # Log if connection held > 60s
```

**Performance Tuning**:
```properties
# Prepared statement caching
cachePrepStmts=true
prepStmtCacheSize=250         # Cache 250 statements per connection
prepStmtCacheSqlLimit=2048    # Cache SQL strings up to 2KB

# Connection optimization
useServerPrepStmts=true       # Use server-side prepared statements
rewriteBatchedStatements=true # Batch INSERT/UPDATE for performance
cacheResultSetMetadata=true   # Cache metadata
cacheServerConfiguration=true # Cache server config
elideSetAutoCommits=true      # Skip redundant autocommit calls
maintainTimeStats=false       # Disable time tracking (faster)
```

**Monitoring**:
```properties
# JMX monitoring
registerMbeans=true

# Micrometer metrics
metricsTrackerFactory=com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory

# Health check interval
keepaliveTime=120000  # 2min keepalive to detect broken connections
```

---

### 3.2 Database Configuration

**PostgreSQL Tuning** (`postgresql.conf`):

```ini
# Connection settings
max_connections = 200          # Total allowed (5 pods * 20 + buffer)
superuser_reserved_connections = 3

# Memory settings (for 16GB RAM)
shared_buffers = 4GB           # 25% of RAM
effective_cache_size = 12GB    # 75% of RAM
work_mem = 64MB                # For sorting/hashing
maintenance_work_mem = 512MB   # For VACUUM/INDEX

# WAL settings
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 2GB

# Query planner
random_page_cost = 1.1         # For SSD
effective_io_concurrency = 200 # For SSD

# Logging
log_min_duration_statement = 1000  # Log queries > 1s
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on
log_temp_files = 0
```

**Deployment**:
```bash
# GCP Cloud SQL
gcloud sql instances patch yawl-db \
  --database-flags max_connections=200,shared_buffers=4194304KB

# AWS RDS
aws rds modify-db-parameter-group \
  --db-parameter-group-name yawl-params \
  --parameters "ParameterName=max_connections,ParameterValue=200,ApplyMethod=immediate"

# Azure Database
az postgres flexible-server parameter set \
  --resource-group yawl-prod \
  --server-name yawl-db \
  --name max_connections \
  --value 200
```

---

### 3.3 Connection Pool Monitoring

**Prometheus Metrics**:
```prometheus
# Active connections
hikaricp_active_connections{pool="YAWLConnectionPool"} 15

# Idle connections
hikaricp_idle_connections{pool="YAWLConnectionPool"} 5

# Pending connections (waiting for connection)
hikaricp_pending_threads{pool="YAWLConnectionPool"} 0

# Connection acquisition time
hikaricp_connection_acquire_seconds_sum{pool="YAWLConnectionPool"} 1.234
hikaricp_connection_acquire_seconds_count{pool="YAWLConnectionPool"} 1000

# Connection usage time
hikaricp_connection_usage_seconds_sum{pool="YAWLConnectionPool"} 45.678
hikaricp_connection_usage_seconds_count{pool="YAWLConnectionPool"} 1000
```

**Grafana Dashboard** (example panel):
```json
{
  "title": "HikariCP Connection Pool",
  "targets": [
    {
      "expr": "hikaricp_active_connections{pool=\"YAWLConnectionPool\"}",
      "legendFormat": "Active"
    },
    {
      "expr": "hikaricp_idle_connections{pool=\"YAWLConnectionPool\"}",
      "legendFormat": "Idle"
    },
    {
      "expr": "hikaricp_pending_threads{pool=\"YAWLConnectionPool\"}",
      "legendFormat": "Pending"
    }
  ],
  "yaxes": [
    {
      "label": "Connections",
      "min": 0,
      "max": 20
    }
  ]
}
```

**Alerting Rules**:
```yaml
groups:
  - name: hikaricp
    rules:
      - alert: ConnectionPoolExhausted
        expr: hikaricp_pending_threads > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Connection pool exhausted"
          description: "{{ $value }} threads waiting for connections"
      
      - alert: HighConnectionUsage
        expr: hikaricp_active_connections / hikaricp_connections > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Connection pool usage > 80%"
          description: "{{ $value | humanizePercentage }} connections in use"
      
      - alert: SlowConnectionAcquisition
        expr: rate(hikaricp_connection_acquire_seconds_sum[5m]) / rate(hikaricp_connection_acquire_seconds_count[5m]) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Slow connection acquisition"
          description: "Average acquisition time: {{ $value | humanizeDuration }}"
```

---

## 4. Resource Limit Tuning

### 4.1 CPU Throttling Analysis

**Symptoms**:
- High CPU utilization but low throughput
- Container restart with "CFS throttling" logs
- Increased latency under load

**Diagnosis**:
```bash
# Check CPU throttling
kubectl get pod -n yawl yawl-engine-xxx -o jsonpath='{.spec.containers[0].resources}'

# View metrics
kubectl top pod -n yawl yawl-engine-xxx

# Check cgroup stats
kubectl exec -n yawl yawl-engine-xxx -- cat /sys/fs/cgroup/cpu/cpu.stat
```

**Resolution**:
```yaml
# Increase CPU limits
resources:
  requests:
    cpu: "2000m"    # Guaranteed
  limits:
    cpu: "4000m"    # Burst (2x request)

# Or remove limits (not recommended in production)
resources:
  requests:
    cpu: "2000m"
  # No limits = unlimited burst
```

---

### 4.2 Memory OOMKilled Prevention

**Symptoms**:
- Pod restarts with exit code 137
- Logs show "java.lang.OutOfMemoryError"

**Diagnosis**:
```bash
# Check OOMKilled events
kubectl get events -n yawl --sort-by='.lastTimestamp' | grep OOMKilled

# Check memory usage
kubectl top pod -n yawl yawl-engine-xxx

# Analyze heap dump
kubectl cp yawl/yawl-engine-xxx:/opt/yawl/logs/heapdump.hprof ./heapdump.hprof
jhat heapdump.hprof
```

**Resolution**:
```yaml
# Ensure Xmx < memory limit
resources:
  limits:
    memory: "4Gi"
env:
  - name: JAVA_OPTS
    value: "-Xmx3g"  # 75% of 4Gi = 3Gi

# Add memory request
resources:
  requests:
    memory: "4Gi"    # Guaranteed allocation
  limits:
    memory: "4Gi"    # Prevent OOM
```

---

### 4.3 Quality of Service (QoS)

**Guaranteed QoS** (best for production):
```yaml
resources:
  requests:
    cpu: "2000m"
    memory: "4Gi"
  limits:
    cpu: "2000m"     # Same as request
    memory: "4Gi"    # Same as request
# Result: Guaranteed QoS (never evicted unless node runs out of memory)
```

**Burstable QoS** (flexible):
```yaml
resources:
  requests:
    cpu: "1000m"
    memory: "2Gi"
  limits:
    cpu: "4000m"     # Higher than request
    memory: "4Gi"    # Higher than request
# Result: Burstable QoS (evicted if node under pressure)
```

**BestEffort QoS** (not recommended):
```yaml
# No requests or limits
# Result: BestEffort QoS (first to be evicted)
```

---

## 5. Observability Architecture

### 5.1 Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────┐
│                   YAWL Observability                     │
└─────────────┬───────────────────────────┬───────────────┘
              │                           │
     ┌────────▼────────┐       ┌──────────▼──────────┐
     │  Metrics        │       │  Traces             │
     │  (Prometheus)   │       │  (Jaeger/Tempo)     │
     │                 │       │                     │
     │  - Counters     │       │  - Spans            │
     │  - Histograms   │       │  - Context          │
     │  - Gauges       │       │  - Attributes       │
     └────────┬────────┘       └──────────┬──────────┘
              │                           │
              │         ┌─────────────────┼─────────┐
              │         │                 │         │
     ┌────────▼─────────▼──┐    ┌─────────▼─────────▼────┐
     │  Logs               │    │  Unified Observability  │
     │  (Loki/CloudWatch)  │    │  (Grafana)              │
     │                     │    │                         │
     │  - Structured JSON  │    │  - Dashboards           │
     │  - Log aggregation  │    │  - Alerts               │
     │  - Log correlation  │    │  - Correlation          │
     └─────────────────────┘    └─────────────────────────┘
```

---

### 5.2 Instrumentation Points

**Engine Execution**:
```java
// /home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java
public class YEngine {
    private static final MetricsCollector metrics = new MetricsCollector();
    
    public void startCase(YSpecification spec) {
        long start = System.currentTimeMillis();
        try (AgentSpan span = AgentTracer.span("engine.start_case", "YEngine", spec.getID())) {
            span.setAttribute("spec_id", spec.getID());
            
            // ... case creation logic ...
            
            metrics.incrementCounter("yawl_cases_started_total", 
                Map.of("spec_id", spec.getID()));
            
            long duration = System.currentTimeMillis() - start;
            metrics.recordDuration("yawl_case_start_duration_seconds", duration, 
                Map.of("spec_id", spec.getID()));
        }
    }
}
```

**Agent Operations**:
```java
// /home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/agents/BaseAgent.java
public abstract class BaseAgent {
    public void executeTask(YWorkItem workItem) {
        try (AgentSpan span = AgentTracer.span("agent.execute", getName(), workItem.getID())) {
            span.setAttribute("domain", getDomain());
            span.setAttribute("task_id", workItem.getTaskID());
            
            // ... execution logic ...
            
            metrics.incrementCounter("agent_tasks_completed_total",
                Map.of("agent", getName(), "domain", getDomain()));
        }
    }
}
```

---

## 6. Metrics and Alerting

### 6.1 Golden Signals (SRE)

**Latency**:
```prometheus
# p50, p95, p99 request latency
histogram_quantile(0.50, sum(rate(yawl_request_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.95, sum(rate(yawl_request_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.99, sum(rate(yawl_request_duration_seconds_bucket[5m])) by (le))
```

**Traffic**:
```prometheus
# Requests per second
sum(rate(yawl_requests_total[5m]))

# By endpoint
sum(rate(yawl_requests_total[5m])) by (endpoint)
```

**Errors**:
```prometheus
# Error rate
sum(rate(yawl_requests_total{status=~"5.."}[5m])) / sum(rate(yawl_requests_total[5m]))

# Error count
sum(increase(yawl_errors_total[5m]))
```

**Saturation**:
```prometheus
# CPU saturation
sum(rate(container_cpu_usage_seconds_total{pod=~"yawl-engine-.*"}[5m])) / 
sum(kube_pod_container_resource_limits_cpu_cores{pod=~"yawl-engine-.*"})

# Memory saturation
sum(container_memory_working_set_bytes{pod=~"yawl-engine-.*"}) / 
sum(kube_pod_container_resource_limits_memory_bytes{pod=~"yawl-engine-.*"})
```

---

### 6.2 SLO-Based Alerting

**Service Level Indicators (SLIs)**:
- **Availability**: 99.9% uptime
- **Latency**: p95 < 500ms
- **Error Rate**: < 0.1%

**Service Level Objectives (SLOs)**:
```yaml
groups:
  - name: yawl_slos
    interval: 1m
    rules:
      # Availability SLO (99.9%)
      - record: yawl:availability:ratio
        expr: |
          sum(rate(yawl_requests_total{status!~"5.."}[5m])) /
          sum(rate(yawl_requests_total[5m]))
      
      - alert: SLOAvailabilityBreach
        expr: yawl:availability:ratio < 0.999
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Availability SLO breach"
          description: "Availability: {{ $value | humanizePercentage }}"
      
      # Latency SLO (p95 < 500ms)
      - record: yawl:latency:p95
        expr: |
          histogram_quantile(0.95, 
            sum(rate(yawl_request_duration_seconds_bucket[5m])) by (le))
      
      - alert: SLOLatencyBreach
        expr: yawl:latency:p95 > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Latency SLO breach"
          description: "p95 latency: {{ $value | humanizeDuration }}"
      
      # Error rate SLO (< 0.1%)
      - record: yawl:error_rate:ratio
        expr: |
          sum(rate(yawl_requests_total{status=~"5.."}[5m])) /
          sum(rate(yawl_requests_total[5m]))
      
      - alert: SLOErrorRateBreach
        expr: yawl:error_rate:ratio > 0.001
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Error rate SLO breach"
          description: "Error rate: {{ $value | humanizePercentage }}"
```

---

## 7. Distributed Tracing

### 7.1 Trace Context Propagation

**W3C Trace Context** (HTTP headers):
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
tracestate: yawl=agent:OrderAgent,domain:Ordering
```

**Implementation**:
```java
// /home/user/yawl/src/org/yawlfoundation/yawl/integration/orderfulfillment/AgentTracer.java
public class AgentTracer {
    public static AgentSpan span(String name, String agent, String workItemId) {
        // Extract parent trace context from HTTP headers
        String traceparent = ThreadLocalContext.getTraceparent();
        
        // Create child span
        AgentSpan span = BUILDER.start(name, agent, workItemId);
        
        // Inject trace context into outgoing requests
        HttpRequest request = HttpRequest.newBuilder()
            .header("traceparent", span.getTraceContext())
            .build();
        
        return span;
    }
}
```

---

### 7.2 Jaeger Deployment

```bash
# Deploy Jaeger operator
kubectl create namespace observability
kubectl apply -f https://github.com/jaegertracing/jaeger-operator/releases/download/v1.50.0/jaeger-operator.yaml -n observability

# Deploy Jaeger instance
kubectl apply -f - <<EOF
apiVersion: jaegertracing.io/v1
kind: Jaeger
metadata:
  name: yawl-jaeger
  namespace: observability
spec:
  strategy: production
  storage:
    type: elasticsearch
    elasticsearch:
      nodeCount: 3
      resources:
        requests:
          cpu: 1
          memory: 4Gi
        limits:
          cpu: 2
          memory: 8Gi
  ingress:
    enabled: true
    annotations:
      kubernetes.io/ingress.class: nginx
  query:
    replicas: 2
  collector:
    replicas: 2
    resources:
      limits:
        cpu: 1
        memory: 2Gi
EOF

# Access Jaeger UI
kubectl port-forward -n observability svc/yawl-jaeger-query 16686:16686
# http://localhost:16686
```

---

## 8. Log Aggregation

### 8.1 Fluent Bit Deployment

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: kube-system
data:
  fluent-bit.conf: |
    [SERVICE]
        Flush         5
        Daemon        off
        Log_Level     info
        Parsers_File  parsers.conf
    
    [INPUT]
        Name              tail
        Path              /var/log/containers/yawl-*.log
        Parser            docker
        Tag               kube.*
        Refresh_Interval  5
    
    [FILTER]
        Name                kubernetes
        Match               kube.*
        Kube_URL            https://kubernetes.default.svc:443
        Kube_CA_File        /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        Kube_Token_File     /var/run/secrets/kubernetes.io/serviceaccount/token
        Merge_Log           On
        K8S-Logging.Parser  On
        K8S-Logging.Exclude Off
    
    [FILTER]
        Name    parser
        Match   kube.*
        Key_Name log
        Parser  json
    
    [OUTPUT]
        Name   loki
        Match  kube.*
        Host   loki.observability.svc.cluster.local
        Port   3100
        Labels job=fluentbit
  
  parsers.conf: |
    [PARSER]
        Name   json
        Format json
        Time_Key @t
        Time_Format %Y-%m-%dT%H:%M:%S.%LZ
---
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluent-bit
  namespace: kube-system
spec:
  selector:
    matchLabels:
      app: fluent-bit
  template:
    metadata:
      labels:
        app: fluent-bit
    spec:
      serviceAccountName: fluent-bit
      containers:
        - name: fluent-bit
          image: fluent/fluent-bit:2.1
          volumeMounts:
            - name: config
              mountPath: /fluent-bit/etc/
            - name: varlog
              mountPath: /var/log
            - name: varlibdockercontainers
              mountPath: /var/lib/docker/containers
              readOnly: true
      volumes:
        - name: config
          configMap:
            name: fluent-bit-config
        - name: varlog
          hostPath:
            path: /var/log
        - name: varlibdockercontainers
          hostPath:
            path: /var/lib/docker/containers
```

---

## 9. Performance Benchmarking

### 9.1 Load Testing with k6

```javascript
// loadtest.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up to 100 users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 200 },  // Ramp up to 200 users
    { duration: '5m', target: 200 },  // Stay at 200 users
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests < 500ms
    http_req_failed: ['rate<0.01'],     // Error rate < 1%
  },
};

export default function () {
  const payload = `
    <specification>
      <id>OrderFulfillment</id>
      <name>Order Fulfillment</name>
    </specification>
  `;
  
  const params = {
    headers: {
      'Content-Type': 'application/xml',
      'Authorization': 'Bearer ' + __ENV.YAWL_TOKEN,
    },
  };
  
  const res = http.post('https://yawl.example.com/engine/ia', payload, params);
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  sleep(1);
}
```

**Run Load Test**:
```bash
# Install k6
curl -L https://github.com/grafana/k6/releases/download/v0.47.0/k6-v0.47.0-linux-amd64.tar.gz | tar xvz
sudo mv k6-v0.47.0-linux-amd64/k6 /usr/local/bin/

# Run test
export YAWL_TOKEN="your-token-here"
k6 run loadtest.js

# Run with Prometheus output
k6 run --out experimental-prometheus-rw loadtest.js
```

---

### 9.2 Baseline Performance Metrics

**Target Benchmarks**:

| Metric | Target | Acceptable | Unacceptable |
|--------|--------|------------|--------------|
| Engine startup | < 60s | < 90s | > 90s |
| Case creation (p50) | < 200ms | < 300ms | > 500ms |
| Case creation (p95) | < 500ms | < 700ms | > 1000ms |
| Work item checkout (p50) | < 100ms | < 150ms | > 200ms |
| Work item checkout (p95) | < 200ms | < 300ms | > 500ms |
| Database queries (p95) | < 50ms | < 100ms | > 200ms |
| CPU utilization (avg) | < 50% | < 70% | > 80% |
| Memory utilization (avg) | < 60% | < 80% | > 90% |
| Connection pool usage | < 60% | < 80% | > 90% |

**Validation Script**:
```bash
#!/bin/bash
# benchmark.sh

echo "=== YAWL Performance Benchmark ==="

# 1. Engine startup
START=$(date +%s)
kubectl rollout restart deployment/yawl-engine -n yawl
kubectl rollout status deployment/yawl-engine -n yawl
END=$(date +%s)
STARTUP_TIME=$((END - START))
echo "Engine startup: ${STARTUP_TIME}s"

# 2. Case creation latency
for i in {1..100}; do
  curl -w "%{time_total}\n" -o /dev/null -s \
    -X POST https://yawl.example.com/engine/ia \
    -H "Authorization: Bearer $TOKEN" \
    -d @test-spec.xml
done | sort -n | awk '
  BEGIN { sum=0; count=0; }
  { values[count++]=$1; sum+=$1; }
  END {
    print "p50: " values[int(count*0.50)] "s";
    print "p95: " values[int(count*0.95)] "s";
    print "p99: " values[int(count*0.99)] "s";
    print "avg: " sum/count "s";
  }
'

# 3. Resource utilization
kubectl top pods -n yawl
```

---

**Document Owner**: Performance Engineering Team  
**Last Updated**: 2026-02-15  
**Review Cycle**: Monthly
