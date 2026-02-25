# YAWL Capacity Planning Guide

## Overview

This guide helps you plan infrastructure capacity for YAWL deployments based on performance baselines and expected workload.

## Quick Reference

| Workload Profile | Engine Instances | Database | Memory/Instance | CPU Cores |
|------------------|------------------|----------|-----------------|-----------|
| Small            | 1                | Single   | 4 GB            | 2         |
| Medium           | 3                | Shared   | 4 GB            | 4         |
| Large            | 5-10             | Clustered| 8 GB            | 8         |
| Enterprise       | 10+              | HA Cluster| 16 GB          | 16        |

## Workload Estimation

### Calculate Expected Load

**Formula**:
```
Peak Requests/Second = (Active Users × Actions/Hour) / 3600
```

**Example**:
- 100 active users
- 50 actions per user per hour
- Peak RPS = (100 × 50) / 3600 = 1.4 req/sec

**Add 3x safety margin**: 1.4 × 3 = **4.2 req/sec**

### Case Complexity

**Simple Case** (< 10 tasks):
- Latency: ~100ms
- Memory: ~200KB per active case
- Throughput: 150 cases/sec per instance

**Medium Case** (10-50 tasks):
- Latency: ~300ms
- Memory: ~500KB per active case
- Throughput: 100 cases/sec per instance

**Complex Case** (50+ tasks):
- Latency: ~800ms
- Memory: ~1MB per active case
- Throughput: 50 cases/sec per instance

## Sizing Calculations

### Single Engine Instance Capacity

**Based on Baselines**:
- **Sustained throughput**: 50 cases/sec
- **Burst throughput**: 150 cases/sec (1 minute)
- **Concurrent cases**: 1,000 active
- **Work items**: 10,000 active
- **Memory**: 4GB heap recommended

**Formula**:
```
Instances = ceil(Peak_RPS / Sustained_Throughput_Per_Instance)
```

**Example**:
- Peak: 200 req/sec
- Per instance: 50 req/sec
- Instances needed: ceil(200 / 50) = **4 instances**

---

### Memory Sizing

**Per Engine Instance**:
```
Heap = Base + (Active_Cases × Memory_Per_Case) × 1.5

Base = 1 GB (engine overhead)
Memory_Per_Case = 374 KB (from baseline)
Safety_Factor = 1.5
```

**Example**:
- 1000 active cases
- Heap = 1 GB + (1000 × 374 KB) × 1.5
- Heap = 1 GB + 561 MB = **1.6 GB**
- **Recommended**: 4 GB (2.5x safety margin)

**Total Server RAM**:
```
RAM = (Heap × 2) + OS_Overhead

OS_Overhead = 2 GB (recommended)
```

**Example**:
- Heap: 4 GB
- RAM = (4 GB × 2) + 2 GB = **10 GB**
- **Provision**: 16 GB server

---

### Database Sizing

**Connection Pool**:
```
Pool_Size = (Engine_Instances × Threads_Per_Instance) × 0.3

Threads_Per_Instance = 200 (default)
```

**Example**:
- 5 engine instances
- Pool = (5 × 200) × 0.3 = **300 connections**

**Database Resources**:
```
# PostgreSQL
max_connections = 400  (Pool_Size + 100 buffer)
shared_buffers = 2GB   (25% of RAM for 8GB server)
effective_cache_size = 6GB  (75% of RAM)
work_mem = 16MB
maintenance_work_mem = 512MB
```

**Storage**:
```
Storage = (Cases_Per_Day × Retention_Days × Case_Size) × 1.5

Case_Size = 10 KB (average, depends on data)
Safety_Factor = 1.5
```

**Example**:
- 10,000 cases/day
- 90 day retention
- Storage = (10,000 × 90 × 10 KB) × 1.5
- Storage = 13.5 GB
- **Provision**: 50 GB (allows growth)

---

## Deployment Scenarios

### Scenario 1: Small Organization
**Profile**:
- 50 users
- 100 cases/day
- 30 day retention

**Infrastructure**:
```
Engine:    1 instance × 4 GB × 2 cores
Database:  PostgreSQL × 8 GB × 2 cores
Load Bal:  Not required
Storage:   50 GB

Total Cost: ~$150/month (cloud)
```

---

### Scenario 2: Medium Organization
**Profile**:
- 200 users
- 1,000 cases/day
- 90 day retention

**Infrastructure**:
```
Engine:    3 instances × 4 GB × 4 cores
Database:  PostgreSQL HA × 16 GB × 4 cores
Load Bal:  Application LB
Storage:   200 GB SSD

Total Cost: ~$800/month (cloud)
```

---

### Scenario 3: Large Enterprise
**Profile**:
- 1,000 users
- 10,000 cases/day
- 365 day retention

**Infrastructure**:
```
Engine:    10 instances × 8 GB × 8 cores
Database:  PostgreSQL Cluster (3 nodes) × 32 GB × 8 cores
Load Bal:  Application LB with WAF
Cache:     Redis Cluster (3 nodes) × 8 GB
Storage:   1 TB SSD

Total Cost: ~$5,000/month (cloud)
```

---

### Scenario 4: High Availability
**Profile**:
- 24/7 operation
- 99.95% uptime SLA
- Multi-region

**Infrastructure**:
```
Region 1:
  Engine:    5 instances × 8 GB × 8 cores
  Database:  PostgreSQL HA (primary)
  Cache:     Redis Cluster

Region 2:
  Engine:    5 instances × 8 GB × 8 cores
  Database:  PostgreSQL HA (replica)
  Cache:     Redis Cluster

Global:
  Load Bal:  Global traffic manager
  CDN:       Static content delivery
  Monitoring: Multi-region monitoring

Total Cost: ~$12,000/month (cloud)
```

---

## Growth Planning

### Horizontal Scaling Triggers

**Add Engine Instance When**:
- CPU > 70% sustained (15 min)
- Memory > 80% sustained (15 min)
- Latency p95 > 500ms (5 min)
- Success rate < 99.5%

**Scale Database When**:
- Connection pool > 80% utilized
- Query latency p95 > 100ms
- CPU > 60% sustained
- I/O wait > 20%

### Vertical Scaling Triggers

**Increase Instance Size When**:
- Frequent GC (> 5% time in GC)
- Memory pressure (heap > 85%)
- CPU bottleneck (all cores > 90%)

### Auto-Scaling Configuration

**Kubernetes HPA**:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

## Cost Optimization

### Right-Sizing

**Over-Provisioned Signs**:
- CPU < 30% average
- Memory < 50% average
- Database connections < 30%

**Action**: Reduce instance size or count

**Under-Provisioned Signs**:
- CPU > 80% average
- Memory > 85% average
- Latency degradation

**Action**: Increase capacity immediately

### Reserved Instances

**Savings**:
- 1 year: ~30% discount
- 3 year: ~50% discount

**Recommendation**: Reserve baseline capacity, use on-demand for peaks

### Spot Instances

**Use For**:
- Non-critical batch processing
- Development/testing environments
- Background tasks

**Not For**:
- Production engines
- Primary databases
- Critical services

---

## Monitoring and Alerts

### Key Metrics

**Engine Metrics**:
```
# Latency
yawl.case.launch.latency.p95 < 500ms
yawl.workitem.completion.latency.p95 < 200ms

# Throughput
yawl.case.throughput > 50/sec

# Success Rate
yawl.case.success_rate > 99%

# Resource Usage
jvm.memory.heap.used < 80%
jvm.gc.pause.time.p95 < 200ms
system.cpu.usage < 70%
```

**Database Metrics**:
```
postgresql.connections.active < 80% of max
postgresql.query.latency.p95 < 100ms
postgresql.replication.lag < 1s
```

### Alert Thresholds

**Critical** (Page On-Call):
- Success rate < 95%
- p95 latency > 2000ms
- Database down
- Engine instances < min_healthy

**Warning** (Email Team):
- Success rate < 99%
- p95 latency > 1000ms
- CPU > 80%
- Memory > 85%

**Info** (Dashboard Only):
- CPU > 70%
- Memory > 75%
- Throughput < baseline

---

## Performance Testing for Capacity

### Load Test Scenarios

**Baseline**:
```bash
# Current load
./scripts/run-performance-tests.sh --load-only
```

**2x Growth**:
```bash
# Double the concurrent users
# Adjust LoadTestSuite parameters
```

**5x Growth**:
```bash
# 5x concurrent users
# Verify degradation patterns
```

### Stress Testing

**Find Breaking Point**:
```java
// Gradually increase load until failure
for (int users = 50; users <= 500; users += 50) {
    runLoadTest(users, 60);
    if (successRate < 90%) {
        System.out.println("Breaking point: " + users + " users");
        break;
    }
}
```

---

## Migration Planning

### From Single Instance

**Current State**:
- 1 engine instance
- Single database

**Target State**:
- 3 engine instances
- Database with replicas

**Migration Steps**:
1. Deploy new database replica
2. Configure database replication
3. Deploy 2 additional engine instances
4. Configure load balancer
5. Gradually shift traffic
6. Monitor and validate
7. Decommission old setup

**Downtime**: Near-zero (blue/green deployment)

---

## Disaster Recovery

### Capacity for DR

**Active-Passive**:
```
Production:  5 instances × 8 GB
DR Site:     2 instances × 8 GB (minimal)

Cost: Production + 40% for DR
```

**Active-Active**:
```
Region 1:    5 instances × 8 GB
Region 2:    5 instances × 8 GB

Cost: 2× Production
Benefit: Zero downtime
```

### RTO/RPO Planning

**RTO (Recovery Time Objective)**:
- Tier 1 (Critical): < 1 hour
- Tier 2 (Important): < 4 hours
- Tier 3 (Standard): < 24 hours

**RPO (Recovery Point Objective)**:
- Tier 1: < 5 minutes (continuous replication)
- Tier 2: < 1 hour (hourly backups)
- Tier 3: < 24 hours (daily backups)

---

## References

- [Performance Baselines](PERFORMANCE_BASELINES.md)
- [Performance Testing Guide](PERFORMANCE_TESTING_GUIDE.md)
- [JVM Tuning](JVM_TUNING.md)
- [Database Optimization](DATABASE_OPTIMIZATION.md)

---

**Last Updated**: 2026-02-16
**Version**: 5.2.0
