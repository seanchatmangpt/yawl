# YAWL Scaling Guide

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This guide covers scaling strategies for YAWL Workflow Engine, including horizontal and vertical scaling, auto-scaling configuration, and performance optimization.

---

## 2. Scaling Strategies

### 2.1 Horizontal vs Vertical Scaling

| Strategy | Description | Use Case | Downtime |
|----------|-------------|----------|----------|
| **Horizontal** | Add more pod replicas | High throughput, HA | None |
| **Vertical** | Increase pod resources | Memory-intensive workloads | Rolling restart |
| **Database** | Add read replicas | Read-heavy workloads | None |
| **Cache** | Increase Redis shards | Cache-heavy workloads | Minimal |

### 2.2 Recommended Scaling Approach

```
                    Load Increase
                         |
         +---------------+---------------+
         |               |               |
    +----v----+     +----v----+     +----v----+
    | Scale   |     | Scale   |     | Scale   |
    | Engine  |     | DB      |     | Redis   |
    | Pods    |     | Read    |     | Cluster |
    | (HPA)   |     | Replicas|     |         |
    +---------+     +---------+     +---------+
```

---

## 3. Horizontal Pod Autoscaling

### 3.1 Engine HPA Configuration

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

    # Custom metric: Active cases
    - type: Pods
      pods:
        metric:
          name: yawl_cases_active
        target:
          type: AverageValue
          averageValue: "1000"

    # Custom metric: Pending work items
    - type: Pods
      pods:
        metric:
          name: yawl_workitems_pending
        target:
          type: AverageValue
          averageValue: "500"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
        - type: Pods
          value: 4
          periodSeconds: 60
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 25
          periodSeconds: 120
```

### 3.2 Resource Service HPA

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-resource-hpa
  namespace: yawl
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-resource-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 75
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### 3.3 Verify HPA

```bash
# Check HPA status
kubectl get hpa -n yawl

# Describe HPA
kubectl describe hpa yawl-engine-hpa -n yawl

# Watch HPA in action
kubectl get hpa -n yawl -w
```

---

## 4. Vertical Pod Autoscaling

### 4.1 VPA Configuration

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
    updateMode: "Auto"  # or "Off" for recommendations only
  resourcePolicy:
    containerPolicies:
      - containerName: yawl-engine
        minAllowed:
          cpu: 500m
          memory: 1Gi
        maxAllowed:
          cpu: 4000m
          memory: 8Gi
        controlledResources: ["cpu", "memory"]
```

### 4.2 VPA Recommendations

```bash
# Get VPA recommendations
kubectl describe vpa yawl-engine-vpa -n yawl

# Output example:
# Recommendation:
#   Container Recommendations:
#     Container Name:  yawl-engine
#     Lower Bound:
#       Cpu:     500m
#       Memory:  2Gi
#     Target:
#       Cpu:     1000m
#       Memory:  3Gi
#     Uncapped Target:
#       Cpu:     1000m
#       Memory:  3Gi
#     Upper Bound:
#       Cpu:     3000m
#       Memory:  6Gi
```

---

## 5. Database Scaling

### 5.1 PostgreSQL Read Replicas

**AWS RDS:**

```bash
# Create read replica
aws rds create-db-instance-read-replica \
  --db-instance-identifier yawl-postgres-replica-1 \
  --source-db-instance-identifier yawl-postgres

# Create second replica in different AZ
aws rds create-db-instance-read-replica \
  --db-instance-identifier yawl-postgres-replica-2 \
  --source-db-instance-identifier yawl-postgres \
  --availability-zone us-east-1b
```

**Google Cloud SQL:**

```bash
# Create read replica
gcloud sql instances create yawl-postgres-replica \
  --master-instance-name=yawl-postgres \
  --region=us-central1
```

### 5.2 Connection Pooling with PgBouncer

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pgbouncer
  namespace: yawl
spec:
  replicas: 2
  selector:
    matchLabels:
      app: pgbouncer
  template:
    spec:
      containers:
        - name: pgbouncer
          image: edoburu/pgbouncer:latest
          env:
            - name: DATABASE_URL
              value: "postgres://yawl_admin:password@yawl-postgres:5432/yawl"
            - name: POOL_MODE
              value: "transaction"
            - name: MAX_CLIENT_CONN
              value: "1000"
            - name: DEFAULT_POOL_SIZE
              value: "50"
          ports:
            - containerPort: 6432
---
apiVersion: v1
kind: Service
metadata:
  name: pgbouncer
  namespace: yawl
spec:
  selector:
    app: pgbouncer
  ports:
    - port: 6432
      targetPort: 6432
```

### 5.3 Database Sizing Guide

| Workload | CPU | Memory | Storage | Connections |
|----------|-----|--------|---------|-------------|
| Light | 2 vCPU | 4 GB | 100 GB | 100 |
| Medium | 4 vCPU | 16 GB | 500 GB | 300 |
| Heavy | 8 vCPU | 32 GB | 1 TB | 500 |
| Enterprise | 16+ vCPU | 64+ GB | 2+ TB | 1000+ |

---

## 6. Redis Scaling

### 6.1 Redis Cluster Mode

```yaml
# Redis cluster configuration
apiVersion: databases.spotahome.com/v1
kind: RedisFailover
metadata:
  name: yawl-redis
  namespace: yawl
spec:
  sentinel:
    replicas: 3
    resources:
      requests:
        cpu: 100m
        memory: 128Mi
  redis:
    replicas: 3
    resources:
      requests:
        cpu: 500m
        memory: 2Gi
      limits:
        cpu: 1000m
        memory: 4Gi
    storage:
      keepAfterDeletion: true
      persistentVolumeClaim:
        spec:
          accessModes:
            - ReadWriteOnce
          resources:
            requests:
              storage: 10Gi
```

### 6.2 Redis Memory Sizing

| Use Case | Memory | Shards | Replicas |
|----------|--------|--------|----------|
| Small | 2 GB | 1 | 1 |
| Medium | 8 GB | 3 | 1 |
| Large | 32 GB | 6 | 2 |
| Enterprise | 128+ GB | 12+ | 2 |

---

## 7. Cluster Autoscaling

### 7.1 Cluster Autoscaler Configuration

```yaml
# AWS EKS Cluster Autoscaler
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cluster-autoscaler
  namespace: kube-system
spec:
  template:
    spec:
      containers:
        - name: cluster-autoscaler
          image: k8s.gcr.io/autoscaling/cluster-autoscaler:v1.29.0
          command:
            - ./cluster-autoscaler
            - --v=4
            - --stderrthreshold=info
            - --cloud-provider=aws
            - --skip-nodes-with-local-storage=false
            - --expander=least-waste
            - --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled,k8s.io/cluster-autoscaler/yawl-cluster
          resources:
            limits:
              cpu: 100m
              memory: 600Mi
            requests:
              cpu: 100m
              memory: 600Mi
```

### 7.2 Node Group Configuration

**AWS EKS Managed Node Group:**

```bash
# Create node group with autoscaling
eksctl create nodegroup \
  --cluster yawl-cluster \
  --name yawl-workers \
  --node-type m6i.xlarge \
  --nodes 3 \
  --nodes-min 3 \
  --nodes-max 20 \
  --node-volume-size 100
```

---

## 8. Performance Tuning

### 8.1 JVM Tuning

```yaml
# Engine JVM options
env:
  - name: JAVA_OPTS
    value: >
      -Xms2g
      -Xmx4g
      -XX:+UseG1GC
      -XX:MaxGCPauseMillis=200
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/heapdumps
      -XX:+UseStringDeduplication
      -Djava.security.egd=file:/dev/./urandom
```

### 8.2 Hibernate Optimization

```yaml
# Hibernate configuration
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          batch_versioned_data: true
        order_inserts: true
        order_updates: true
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.redis.cache.RedisRegionFactory
```

### 8.3 Connection Pool Tuning

```yaml
# HikariCP configuration
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      idle-timeout: 300000
      connection-timeout: 30000
      max-lifetime: 1200000
      leak-detection-threshold: 60000
```

---

## 9. Monitoring Scaling

### 9.1 Grafana Dashboard

Import dashboard with panels for:
- Pod count over time
- CPU/Memory utilization
- Request rate per pod
- Database connection pool usage
- Redis hit rate

### 9.2 Scaling Alerts

```yaml
groups:
  - name: scaling
    rules:
      - alert: HighCPUUtilization
        expr: |
          sum(rate(container_cpu_usage_seconds_total{namespace="yawl",container="yawl-engine"}[5m])) /
          sum(kube_pod_container_resource_limits{namespace="yawl",container="yawl-engine",resource="cpu"})
          > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: High CPU utilization in YAWL engine
          description: "CPU utilization is above 80% for 5 minutes"

      - alert: HPAAtMaxReplicas
        expr: |
          kube_horizontalpodautoscaler_status_current_replicas{namespace="yawl"}
          ==
          kube_horizontalpodautoscaler_spec_max_replicas{namespace="yawl"}
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: HPA at maximum replicas
          description: "HPA {{ $labels.horizontalpodautoscaler }} is at max replicas"
```

---

## 10. Capacity Planning

### 10.1 Throughput Targets

| Metric | Target | Formula |
|--------|--------|---------|
| Cases/hour | 10,000 | Cases = Pods x Throughput_per_pod |
| Work items/hour | 50,000 | Work items = Cases x Avg_tasks_per_case |
| Concurrent users | 500 | Users = Active_sessions / Session_duration |

### 10.2 Sizing Calculator

```
Required Pods = (Target_RPS / Single_Pod_RPS) x Safety_Factor

Where:
- Target_RPS = Expected requests per second
- Single_Pod_RPS = ~100-200 RPS per pod (with 2 vCPU)
- Safety_Factor = 1.5 for production

Example:
- Target: 1000 RPS
- Single pod: 150 RPS
- Safety: 1.5
- Required pods: (1000 / 150) x 1.5 = 10 pods
```

---

## 11. Troubleshooting Scaling Issues

### 11.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| HPA not scaling | Replicas stay at min | Check metrics server, resource requests |
| OOM kills | Pods restart frequently | Increase memory limits, tune JVM |
| DB connection exhaustion | Connection timeout errors | Add PgBouncer, increase pool size |
| Redis evictions | Cache miss rate high | Increase Redis memory, tune TTL |

### 11.2 Diagnostic Commands

```bash
# Check resource usage
kubectl top pods -n yawl

# Check HPA details
kubectl describe hpa -n yawl

# Check events
kubectl get events -n yawl --sort-by='.lastTimestamp'

# Check node resources
kubectl describe nodes | grep -A 5 "Allocated resources"
```

---

## 12. Best Practices

1. **Set appropriate resource requests/limits** for accurate scaling
2. **Use HPA with custom metrics** for workload-aware scaling
3. **Enable cluster autoscaler** for node-level scaling
4. **Monitor scaling events** and tune thresholds
5. **Test scaling** under load before production
6. **Document scaling procedures** for on-call teams
7. **Set up alerts** for scaling anomalies
8. **Review VPA recommendations** periodically

---

## 13. Next Steps

- [Disaster Recovery Guide](disaster-recovery.md)
- [Upgrade Guide](upgrade-guide.md)
- [Security Overview](../security/security-overview.md)
