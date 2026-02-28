# YAWL Architecture Patterns Advisor

**Time to Complete**: 10-12 minutes
**Goal**: Match your requirements to an architecture pattern with detailed implementation guide

---

## How to Use This Guide

Below are **5 targeted questions** about your system requirements. Based on your answers, you'll be matched to one of **6 proven YAWL architecture patterns** used by Fortune 500 companies.

Each pattern includes:
- System diagram
- When to use it
- Trade-offs
- Step-by-step setup guide
- Example deployment configuration
- Cost estimate

---

## SECTION 1: UNDERSTANDING YOUR WORKLOAD

### Question 1: What Type of Work Does YAWL Execute?

Choose the primary type of work in your workflows:

- [ ] **A** — Fast automated tasks (seconds to minutes, no human wait)
- [ ] **B** — Human-centric workflows (people approve/review, cases wait hours/days)
- [ ] **C** — Mixed (some fast automated, some human-driven)
- [ ] **D** — Real-time streaming (continuous data ingestion, immediate processing)
- [ ] **E** — Batch processing (large volumes at scheduled times)

**💡 Help**: "Fast" = complete in seconds. "Human" = idle while waiting for person. "Streaming" = continuous data. "Batch" = 100K cases at 2 AM.

---

### Question 2: How Does Data Flow Through Your System?

How do cases get data?

- [ ] **SYNCHRONOUS** — Caller waits for result (REST API request-response)
- [ ] **ASYNCHRONOUS** — Caller submits, gets notified later (events, webhooks, message queue)
- [ ] **STREAMING** — Continuous input stream (Kafka, event bus)
- [ ] **MIXED** — Some sync, some async

**💡 Help**: REST = synchronous. Webhooks = asynchronous. Message queues = async batch.

---

### Question 3: What are Your Availability Requirements?

How critical is uptime?

- [ ] **DEV_OR_TEST** — Downtime acceptable (testing, development environments)
- [ ] **STANDARD** — High availability preferred (99% uptime, maintenance windows OK)
- [ ] **HIGH** — Must stay up (99.5%, planned maintenance only)
- [ ] **CRITICAL** — Zero downtime required (99.99%, no maintenance windows allowed)

**💡 Help**: Standard = few hours downtime per year is OK. Critical = medical/financial systems.

---

### Question 4: What's Your Geographic Distribution?

Where are your users and data?

- [ ] **SINGLE_REGION** — All in one cloud region or data center
- [ ] **MULTI_REGION_SAME_CONTINENT** — Multiple regions in same continent (EU, US, APAC)
- [ ] **GLOBAL** — Users and/or data in 3+ continents
- [ ] **EDGE** — Need computation at edge locations (CDNs, local offices)

**💡 Help**: Single region = simplest. Multi-region = backup data centers. Global = compliance/latency.

---

### Question 5: Do You Have Strict Data Locality or Compliance Requirements?

Any regulatory constraints?

- [ ] **NONE** — No special requirements
- [ ] **SAME_REGION** — Data must stay in one region (GDPR local data)
- [ ] **ON_PREMISE** — Must run in your data center, not cloud
- [ ] **HYBRID** — Mix of cloud and on-premise
- [ ] **STRICT** — Isolated network, air-gapped from internet
- [ ] **MULTI_TENANT_ISOLATION** — Strict data isolation between customers

**💡 Help**: GDPR = EU data in EU. HIPAA = healthcare data secure. Hybrid = split workload.

---

---

## YOUR PERSONALIZED ARCHITECTURE RECOMMENDATION

Find your matching scenario below. Each includes a diagram, pros/cons, and implementation guide.

---

## 🟢 PATTERN 1: Stateless Cloud-Native (Fastest to Deploy)

**Recommended if you answered:**
- Q1: A (fast automated tasks)
- Q2: Synchronous (REST API)
- Q3: Standard availability
- Q4: Single region
- Q5: None

### Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                    API Gateway                         │
│            (Rate limiting, Auth)                       │
└──────────────────────┬─────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
┌───────▼────────────┐      ┌────────▼────────────┐
│ Stateless Engine   │      │ Stateless Engine    │
│ Pod N1 (Auto-scale)│      │ Pod N2 (Auto-scale) │
│                    │      │                     │
│ No persistent      │      │ No persistent       │
│ state between      │      │ state between       │
│ invocations        │      │ invocations         │
└───────┬────────────┘      └────────┬────────────┘
        │                            │
        └──────────┬─────────────────┘
                   │
        ┌──────────▼───────────┐
        │  PostgreSQL (Cloud)  │
        │  Or use external DB  │
        │  for case history    │
        └─────────────────────┘
```

### Why Choose This Pattern?

- ✅ **Easiest to deploy** — Stateless = auto-scales automatically
- ✅ **Cheapest to run** — Only pay when processing cases
- ✅ **Perfect for AWS Lambda, GCP Functions** — Built for serverless
- ✅ **No persistent state** — Nothing ties up resources between invocations
- ✅ **Horizontal scaling** — Add pods linearly, no coordination needed

### Trade-offs

- ❌ **Short timeout** — Cases must complete in seconds (Lambda: 15 min timeout)
- ❌ **No human tasks** — Can't wait for person to take action
- ❌ **No state persistence between invocations** — Must store results externally
- ❌ **Cold start latency** — First invocation slower (100-500ms)

### When to Use

- **Microservices ETL**: Fast data transformation pipelines
- **API backends**: REST endpoints that process data
- **Scheduled jobs**: Cron-like processing at scale
- **Event processors**: React to events from Kafka/SNS

### Implementation Steps

**1. Choose your cloud platform:**
```
Option A: AWS Lambda + API Gateway
Option B: Google Cloud Functions + Cloud API Gateway
Option C: Azure Functions + API Management
Option D: Kubernetes with stateless engine
```

**2. Deploy stateless engine:**
```bash
# If using Kubernetes:
kubectl apply -f yawl-stateless-deployment.yaml

# If using Lambda:
aws lambda create-function --runtime java21 \
  --handler YawlStatelessHandler \
  --code S3Bucket=my-bucket,S3Key=yawl-stateless.jar
```

**3. Configure for serverless:**
```yaml
# yawl-stateless-config.yaml
engine:
  type: stateless
  timeout_ms: 60000  # 60 seconds
  max_concurrent_cases: 1000
  memory_limit: 1Gi

storage:
  # Use external database or S3 for case history
  type: s3  # or postgresql
  bucket: workflow-results
```

**4. Set up monitoring:**
```bash
# CloudWatch, Datadog, or Prometheus
metrics:
  - case_execution_time
  - cases_per_second
  - error_rate
  - cold_start_latency
```

**5. Load test:**
```bash
# Verify auto-scaling behavior
apache_bench -n 10000 -c 100 https://api.example.com/case
```

### Example Cost

- **Cases processed**: 1M/day
- **Avg execution time**: 2 seconds
- **AWS Lambda**: $0.20 per 1M requests + compute
  - Rough estimate: **$500-1000/month**
- **Database**: Minimal (only storing results)
  - Estimate: **$100-300/month**
- **Total**: **$600-1300/month**

### Links

- [Stateless Engine Tutorial](../tutorials/yawl-stateless-getting-started.md)
- [Serverless Deployment Guide](../how-to/deployment/serverless-deployment.md)
- [AWS Lambda Integration](../how-to/deployment/aws-lambda.md)
- [Performance Tuning Guide](../PERFORMANCE.md)

---

## 🔵 PATTERN 2: Stateful Monolithic (Simplest to Manage)

**Recommended if you answered:**
- Q1: B (human-centric workflows)
- Q2: Asynchronous (webhooks)
- Q3: Standard availability
- Q4: Single region
- Q5: None

### Architecture Diagram

```
┌────────────────────────────────────────────────────┐
│               Load Balancer (nginx)                │
└──────────────────────┬────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
┌───────▼──────────────┐    ┌────────▼──────────────┐
│   YAWL Server #1     │    │   YAWL Server #2      │
│   (Tomcat/Spring)    │    │   (Tomcat/Spring)     │
│                      │    │                       │
│ Stateful with DB     │    │ Stateful with DB      │
│ Replication enabled  │    │ Replication enabled   │
└───────┬──────────────┘    └────────┬──────────────┘
        │                            │
        └──────────┬─────────────────┘
                   │
        ┌──────────▼──────────────┐
        │  PostgreSQL Primary     │
        │  + Read Replica         │
        │  Auto-backup every hour │
        │  Point-in-time restore  │
        └──────────────────────────┘
```

### Why Choose This Pattern?

- ✅ **Simple to understand** — Single monolithic service
- ✅ **Perfect for human workflows** — Case state lives in database
- ✅ **No timeout pressure** — Cases can wait days for human approval
- ✅ **Traditional deployment** — Familiar to most teams
- ✅ **Debugging is easier** — Single point of execution

### Trade-offs

- ❌ **Limited scaling** — Can't scale horizontally beyond 3-5 servers
- ❌ **Shared state** — All instances need access to same database
- ❌ **Database becomes bottleneck** — Slow queries affect all users
- ❌ **Deployment coupling** — Have to deploy all changes together

### When to Use

- **Enterprise workflows**: People approve, review, decide
- **Multi-day cases**: Long-running with human waits
- **Regulated industries**: Audit trail in one place
- **Teams < 50 people**: Organizational fit

### Implementation Steps

**1. Deploy to cloud or on-premise:**
```bash
# Deploy yawl.war to Tomcat (2 instances)
docker-compose up -d yawl-1 yawl-2 postgresql nginx
```

**2. Set up database replication:**
```sql
-- PostgreSQL Primary-Replica setup
-- Primary: Create base backup
pg_basebackup -D /var/lib/postgresql/backup
-- Replica: Restore from backup and enable streaming replication
```

**3. Configure connection pooling:**
```yaml
# src/main/resources/application.yaml
spring:
  datasource:
    url: jdbc:postgresql://db-primary:5432/yawl
    username: yawl
    hikari:
      maximum-pool-size: 40  # Shared across replicas
      minimum-idle: 10
```

**4. Enable clustering:**
```java
// org/yawl/engine/YEngine.java
YEngine engine = new YEngine();
engine.enableClustering(true);
engine.setClusterNodeName("node-1");
engine.setClusterNodes("node-1:9000", "node-2:9000");
```

**5. Set up monitoring:**
```bash
# Monitor database performance
- Connection pool size
- Query latency (p50, p95, p99)
- Lock contention
- Cache hit rate
```

### Example Cost

- **Cloud VM instances** (2x): ~$500/month
- **PostgreSQL managed** (Primary + Replica): ~$400/month
- **Load balancer**: ~$100/month
- **Monitoring**: ~$100/month
- **Total**: ~**$1100/month**

### Links

- [Deployment Architecture](../explanation/deployment-architecture.md)
- [Production Deployment Guide](../how-to/deployment/production.md)
- [Database Configuration](../how-to/deployment/database-setup.md)
- [Clustering Guide](../how-to/clustering-setup.md)

---

## 🟠 PATTERN 3: Hybrid Dual-Engine (Balanced Performance)

**Recommended if you answered:**
- Q1: C (mixed workload)
- Q2: Mixed (sync + async)
- Q3: High availability
- Q4: Single region
- Q5: None

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                  API Gateway / Router                   │
│          (Route by case type)                           │
└──────┬───────────────────────────────────┬──────────────┘
       │                                   │
       │ Fast cases (< 5 min)              │ Slow cases (> 5 min)
       │                                   │
┌──────▼──────────────────────┐   ┌────────▼──────────────┐
│  Stateless Engine            │   │  Stateful Engine      │
│  (Auto-scale 1-20 pods)      │   │  (Fixed 3-5 pods)     │
│                              │   │                       │
│ - No persistent state        │   │ - DB persistence      │
│ - Completes in seconds       │   │ - No time limit       │
│ - Cost-optimized             │   │ - Human-friendly      │
└──────┬──────────────────────┘   └────────┬──────────────┘
       │                                   │
       └──────────────┬────────────────────┘
                      │
          ┌───────────▼──────────┐
          │  PostgreSQL Cluster  │
          │  Primary + 2 Replicas│
          │  Failover enabled    │
          └──────────────────────┘
```

### Why Choose This Pattern?

- ✅ **Best performance** — Each engine type optimized for its workload
- ✅ **Cost-effective** — Stateless auto-scales only when needed
- ✅ **Handles mixed workloads** — Fast + slow cases in same system
- ✅ **Independent scaling** — Stateless and stateful scale independently
- ✅ **Production-proven** — Used by Fortune 500 enterprises

### Trade-offs

- ⚠️ **More complex deployment** — Need both engine types
- ⚠️ **Requires smart routing** — Must classify cases correctly
- ⚠️ **More monitoring** — Two engine types to watch
- ⚠️ **Slightly higher cost** — Run both engines

### When to Use

- **Mixed workloads**: Automated + human workflows
- **Large enterprises**: 5K-100K cases/day
- **Budget-conscious**: Pay only for what you use (stateless scales to zero)
- **SLA-sensitive**: Fast cases need low latency

### Implementation Steps

**1. Deploy both engines:**
```bash
# Stateless on auto-scaling group
kubectl apply -f yawl-stateless-deployment.yaml

# Stateful on fixed replicas
kubectl apply -f yawl-stateful-deployment.yaml
```

**2. Configure engine selection:**
```java
// EngineSelector.java - Route cases to appropriate engine
public YEngine selectEngine(WorkflowCase workflowCase) {
  if (workflowCase.getDuration() < 5 * 60 * 1000) {
    return statelessEngine;  // < 5 minutes: use stateless
  } else {
    return statefulEngine;   // >= 5 minutes: use stateful
  }
}
```

**3. Set up database sharing:**
```yaml
# Both engines read/write same database
stateless_engine:
  database_url: jdbc:postgresql://db:5432/yawl
  persistence_mode: case_results_only  # Only store results

stateful_engine:
  database_url: jdbc:postgresql://db:5432/yawl
  persistence_mode: full  # Store all case state
```

**4. Configure auto-scaling:**
```yaml
# Kubernetes HPA for stateless
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-stateless-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-stateless
  minReplicas: 1
  maxReplicas: 20
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

**5. Monitor both engines:**
```bash
# Metrics to track:
# Stateless: Response time, throughput, cold starts
# Stateful: Connection pool, query latency, lock contention
```

### Example Cost

- **Stateless pods** (avg 3, peak 20): **$800/month**
- **Stateful pods** (fixed 3): **$600/month**
- **PostgreSQL cluster**: **$500/month**
- **Monitoring & tools**: **$200/month**
- **Total**: **~$2100/month** (less than you'd pay for 10x all-stateful)

### Links

- [Dual-Engine Architecture](../explanation/dual-engine-architecture.md)
- [ADR-001: Engine Selection](../explanation/decisions/ADR-001-dual-engine-architecture.md)
- [Engine Selector Implementation](../how-to/engine-selection.md)
- [Kubernetes Deployment](../how-to/deployment/kubernetes.md)

---

## 🔴 PATTERN 4: High-Availability Multi-Region (Enterprise Grade)

**Recommended if you answered:**
- Q1: A, B, or C
- Q2: Any
- Q3: High or critical availability
- Q4: Multi-region same continent
- Q5: None or same-region compliance

### Architecture Diagram

```
┌──────────────────────────────────────────────────────┐
│                 Global Load Balancer                │
│         (Route by region, health checks)            │
└──────┬──────────────────────┬──────────────┬────────┘
       │                      │              │
       │ US-EAST              │ US-WEST      │ EU-WEST
       │                      │              │
┌──────▼───────┐       ┌──────▼──────┐    ┌─▼────────┐
│ YAWL Cluster │       │YAWL Cluster │    │ YAWL     │
│ (3 instances)│       │ (2 instances)│   │ Cluster  │
│              │       │              │   │(2-3)     │
│ Auto-scale   │       │ Auto-scale   │   │          │
│ 3-10 pods    │       │ 2-5 pods     │   │          │
└──────┬───────┘       └──────┬──────┘    └─┬────────┘
       │                      │              │
       │ Read traffic          │              │
       │ Replication lag 1-5s  │              │
       │                      │              │
└──────┬──────────────────────┴──────────────┘
       │
    ┌──▼─────────────────────────────────────┐
    │  PostgreSQL Multi-Region Cluster       │
    │  Primary (US-EAST) + Replicas (all)   │
    │  or: Multi-Master replication         │
    │  Bidirectional sync (eventual consist) │
    └────────────────────────────────────────┘
```

### Why Choose This Pattern?

- ✅ **True High Availability** — Loss of entire region doesn't stop service
- ✅ **Disaster recovery** — Data replicated across regions
- ✅ **Compliance ready** — Can meet regional data residency
- ✅ **Reduced latency** — Users connect to nearest region
- ✅ **Zero-downtime upgrades** — Upgrade one region at a time

### Trade-offs

- ❌ **Complex to manage** — 3+ independent clusters
- ❌ **Data consistency challenges** — Multi-region = eventual consistency
- ❌ **Expensive** — Run infrastructure in multiple regions
- ❌ **Operational overhead** — Monitor and manage 3+ clusters
- ❌ **Requires expertise** — Not for beginners

### When to Use

- **Critical systems**: Banks, healthcare, insurance
- **Global companies**: Customers in 2+ continents
- **High availability mandate** — 99.9%+ uptime SLA
- **Disaster recovery requirement** — Quick failover needed

### Implementation Steps

**1. Provision infrastructure in each region:**
```bash
# Region 1: US-EAST
terraform apply -var="region=us-east-1"

# Region 2: US-WEST
terraform apply -var="region=us-west-2"

# Region 3: EU-WEST
terraform apply -var="region=eu-west-1"
```

**2. Set up PostgreSQL multi-region replication:**
```sql
-- Option A: Primary-Replica with logical replication
-- US-EAST is primary, others are replicas

-- Option B: Multi-Master (all can write)
-- Use conflict resolution strategy (last-write-wins, vector clocks)
```

**3. Deploy YAWL to each region:**
```bash
# Region 1
kubectl apply -f yawl-cluster-us-east.yaml

# Region 2
kubectl apply -f yawl-cluster-us-west.yaml

# Region 3
kubectl apply -f yawl-cluster-eu-west.yaml
```

**4. Configure global load balancer:**
```hcl
# Terraform/Cloud CDN config
resource "aws_route53_health_check" "us_east" {
  ip_address = aws_elb.us_east.dns_name
  port = 443
  type = "HTTPS"
  failure_threshold = 3
}

# Route by latency (auto-route to nearest)
resource "aws_route53_record" "yawl" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "yawl.example.com"
  type    = "A"

  set_identifier = "us-east"
  latency_routing_policy {
    region = "us-east-1"
  }
  alias {
    name    = aws_elb.us_east.dns_name
    zone_id = aws_elb.us_east.zone_id
    evaluate_target_health = true
  }
}
```

**5. Monitor replication lag:**
```bash
# Replication lag must be < 5 seconds
watch -n 1 "SELECT EXTRACT(EPOCH FROM (NOW() - pg_last_xact_replay_timestamp()));"
```

**6. Set up failover automation:**
```bash
# Automatic failover promotion (if primary goes down)
pg_ctl promote -D /var/lib/postgresql/replica
```

### Example Cost

- **Infrastructure** (3 regions, 3 instances each): **$6K/month**
- **Database** (3 regions, multi-master): **$3K/month**
- **Data transfer** (cross-region replication): **$2K/month**
- **Monitoring & alerting**: **$500/month**
- **Total**: **~$11.5K/month**

### Links

- [Multi-Region Architecture](../explanation/enterprise-cloud.md)
- [PostgreSQL Replication Setup](../how-to/deployment/postgres-replication.md)
- [Global Load Balancing](../how-to/deployment/global-load-balancer.md)
- [Disaster Recovery Plan](../how-to/disaster-recovery.md)

---

## 🟣 PATTERN 5: Edge Computing (Ultra-Low Latency)

**Recommended if you answered:**
- Q1: A or D (fast/streaming)
- Q2: Synchronous
- Q3: Standard or high
- Q4: Edge locations or global
- Q5: Edge/CDN deployment

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────┐
│              Global CDN / Edge Network                   │
│  (Cloudflare, Fastly, CloudFront edge locations)        │
│                                                          │
│  ┌─────────────────┐  ┌──────────────────┐             │
│  │ YAWL Stateless  │  │ YAWL Stateless   │             │
│  │ POPs (50+)      │  │ POPs (other 50+) │             │
│  │ < 1ms latency   │  │ < 1ms latency    │             │
│  └────────┬────────┘  └────────┬─────────┘             │
└───────────┼──────────────────────┼─────────────────────┘
            │ Cache miss           │
            │ (99%+ hits)          │
            │                      │
        ┌───▼──────────────────────▼────┐
        │   Origin (Central Cloud)       │
        │   - PostgreSQL (cached queries)│
        │   - YAWL for complex logic     │
        │   - Stateless engine           │
        └───────────────────────────────┘
```

### Why Choose This Pattern?

- ✅ **Sub-millisecond latency** — Computation at edge location
- ✅ **Global scale** — 50+ points-of-presence worldwide
- ✅ **Cheap compute** — Edge functions cheaper than origin
- ✅ **DDoS protected** — CDN protects from attacks
- ✅ **Built-in caching** — Reduce load on origin

### Trade-offs

- ❌ **Stateless only** — No persistent state at edge
- ❌ **Limited memory** — Edge functions have smaller footprint
- ❌ **Vendor lock-in** — Cloudflare/Fastly proprietary APIs
- ❌ **Cold start** — Functions spawn on demand
- ❌ **Debugging harder** — Distributed across 50+ locations

### When to Use

- **Real-time APIs**: < 100ms latency critical
- **Global content** — Users worldwide expect local speeds
- **IoT/sensor data** — Process at collection point
- **Price-sensitive** — Micro-transactions, low margins

### Implementation Steps

**1. Deploy to Cloudflare Workers (example):**
```javascript
// wrangler.toml
[env.production]
name = "yawl-edge"
main = "src/index.ts"
compatibility_date = "2026-02-28"

[env.production.vars]
YAWL_ORIGIN = "https://origin.example.com"
DATABASE_URL = "https://db-cache.example.com"
```

**2. Write edge function:**
```typescript
// src/index.ts
export default async (request: Request, env: Env) => {
  // Cache in Cloudflare KV
  const cache = caches.default;
  const cached = await cache.match(request);
  if (cached) return cached;

  // Call origin for miss
  const response = await fetch(env.YAWL_ORIGIN, {
    method: request.method,
    body: request.body,
  });

  // Cache for 1 minute
  const cacheControl = new Response(response.clone(), {
    headers: {
      'Cache-Control': 'public, max-age=60',
    },
  });

  await cache.put(request, cacheControl);
  return response;
};
```

**3. Configure origin failover:**
```toml
# Cloudflare configuration
[edge_routing]
origin_error_code = 500
failover_origins = [
  "https://origin-us.example.com",
  "https://origin-eu.example.com",
]
failover_timeout = 5000  # ms
```

**4. Set up KV cache for state:**
```typescript
// Cache case state at edge for < 1s queries
const caseState = await env.KV_STORE.get(
  `case:${caseId}`,
  'json'
);

if (!caseState) {
  // Fetch from origin
  const data = await fetch(`${ORIGIN}/case/${caseId}`);
  await env.KV_STORE.put(
    `case:${caseId}`,
    data,
    { expirationTtl: 5 }  // 5 seconds
  );
}
```

### Example Cost

- **Cloudflare Workers**: **$20/month** (includes 10M requests)
- **KV storage**: **$0.50/GB** (~$50/month for 100GB)
- **Origin capacity** (shared, minimal): **$500/month**
- **Total**: **~$570/month** (very cost-effective)

### Links

- [Edge Computing Architecture](../explanation/edge-computing-architecture.md)
- [Cloudflare Workers Deployment](../how-to/deployment/cloudflare-workers.md)
- [Performance Optimization](../PERFORMANCE.md)

---

## 🟡 PATTERN 6: Hybrid On-Premise + Cloud (Regulatory Compliance)

**Recommended if you answered:**
- Q1: Any
- Q2: Any
- Q3: High or critical
- Q4: Single region
- Q5: Hybrid or on-premise with cloud backup

### Architecture Diagram

```
┌─────────────────────────────────────┐
│     On-Premise Data Center          │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │  YAWL Cluster (3-5 instances)   │ │
│ │  - Stateful                     │ │
│ │  - All data stays on-premise    │ │
│ └─────────────────┬───────────────┘ │
│                   │                 │
│ ┌─────────────────▼───────────────┐ │
│ │  PostgreSQL Primary              │ │
│ │  (On-premise backup)             │ │
│ └──────────────────────────────────┘ │
└──────────────────┬──────────────────┘
                   │ Encrypted replication
                   │ (read-only, nightly)
                   │
┌──────────────────▼──────────────────┐
│     AWS/Azure Cloud (Backup)        │
│                                     │
│  - PostgreSQL Replica (standby)     │
│  - Read-only for audits             │
│  - Disaster recovery (if needed)    │
│  - Encrypted, minimal access        │
└─────────────────────────────────────┘
```

### Why Choose This Pattern?

- ✅ **Compliance** — Data stays in your facility by default
- ✅ **Security** — Control physical access to hardware
- ✅ **Disaster recovery** — Cloud backup for emergencies
- ✅ **Audit-friendly** — All production data on-premise
- ✅ **Hybrid flexibility** — Use cloud only for backups/read-only

### Trade-offs

- ⚠️ **Capital cost** — Buy/maintain on-premise hardware
- ⚠️ **Operational burden** — You manage patches, backups, security
- ⚠️ **Limited scalability** — Bounded by on-premise hardware
- ⚠️ **No cloud auto-scaling** — Must pre-provision capacity
- ❌ **Network dependency** — Replication requires reliable link to cloud

### When to Use

- **Healthcare (HIPAA)** — Patient data must stay local
- **Financial services** — Regulatory data residency
- **Government** — Classified data, air-gapped networks
- **Legacy integration** — Existing on-premise systems

### Implementation Steps

**1. Set up on-premise infrastructure:**
```bash
# Install Tomcat, PostgreSQL on-premise
docker-compose -f docker-compose.on-premise.yaml up -d
```

**2. Configure primary database:**
```sql
-- PostgreSQL on-premise: primary
-- Enable WAL archiving for replication
ALTER SYSTEM SET wal_level = logical;
SELECT pg_ctl_reload_conf();
```

**3. Set up read-only cloud replica:**
```bash
# Cloud-side: Create standby from on-premise backup
pg_basebackup -h on-premise-ip -D /var/lib/postgresql/data
# Enable recovery mode (read-only)
touch /var/lib/postgresql/recovery.signal
```

**4. Configure encrypted replication:**
```yaml
# On-premise postgres
replication:
  primary_conninfo: "host=on-premise.local user=replication"
  primary_slot_name: "cloud_replica"

# Encrypt connection
ssl: on
ssl_cert_file: /etc/postgresql/server.crt
ssl_key_file: /etc/postgresql/server.key

# Restrict to cloud IP
pg_hba.conf: "hostssl replication cloud.example.com md5"
```

**5. Set up replication schedule:**
```bash
# Cron job: Run every night
0 2 * * * pg_basebackup -h on-premise.local -D /backup/daily
```

**6. Test disaster recovery:**
```bash
# Monthly: Simulate on-premise failure
# Verify can promote cloud replica
pg_ctl promote -D /var/lib/postgresql/data
```

### Example Cost

- **On-premise hardware**: **$50K initial** (amortized $500/month)
- **Licensing** (Tomcat, Java, OS): **$200/month**
- **Cloud backup** (minimal): **$200/month**
- **Total monthly**: **~$900/month** (hardware amortized)

### Links

- [Hybrid Deployment Guide](../explanation/enterprise-cloud.md)
- [On-Premise Setup](../how-to/deployment/on-premise-setup.md)
- [Disaster Recovery Plan](../how-to/disaster-recovery.md)
- [Compliance & Audit](../how-to/compliance-audit.md)

---

---

## Pattern Selection Quick Reference

| Pattern | Best For | Complexity | Cost | Setup Time |
|---------|----------|-----------|------|-----------|
| **1. Stateless Cloud** | Fast automated | Low | $700/mo | 2-3 hrs |
| **2. Stateful Monolith** | Human workflows | Low | $1.1K/mo | 4-6 hrs |
| **3. Hybrid Dual-Engine** | Mixed workloads | Medium | $2.1K/mo | 8-12 hrs |
| **4. Multi-Region HA** | Enterprise scale | High | $11.5K/mo | 2-3 wks |
| **5. Edge Computing** | Ultra-low latency | Medium | $570/mo | 6-8 hrs |
| **6. Hybrid On-Premise** | Compliance | Medium | $900/mo | 1-2 wks |

---

## 🎯 Decision Flowchart

```
Start
  │
  ├─ Automated cases < 5 min?
  │   ├─ YES + Single region → PATTERN 1 (Stateless Cloud)
  │   └─ NO → Continue
  │
  ├─ Human tasks / Multi-day?
  │   ├─ YES + Single region → PATTERN 2 (Monolith)
  │   └─ NO → Continue
  │
  ├─ Mixed fast + slow?
  │   ├─ YES + Auto-scale needed → PATTERN 3 (Dual-Engine)
  │   └─ NO → Continue
  │
  ├─ Multi-region + HA required?
  │   ├─ YES → PATTERN 4 (Multi-Region HA)
  │   └─ NO → Continue
  │
  ├─ Need < 100ms latency globally?
  │   ├─ YES → PATTERN 5 (Edge Computing)
  │   └─ NO → Continue
  │
  └─ Need on-premise + compliance?
      ├─ YES → PATTERN 6 (Hybrid On-Premise)
      └─ NO → Go back, re-answer questions
```

---

## ⚠️ Common Mistakes

### "We'll start with Multi-Region HA"
❌ **Problem**: Over-engineered. Cost 10x Pattern 1. Complexity without need.
✅ **Solution**: Start with Pattern 1 or 2. Migrate to Pattern 4 when traffic justifies it.

### "We'll use stateful engine for everything"
❌ **Problem**: Doesn't scale. One database = bottleneck.
✅ **Solution**: Use Pattern 3 (Dual-Engine) for mixed workloads.

### "We'll put everything on-premise"
❌ **Problem**: No disaster recovery. High operational cost.
✅ **Solution**: Use Pattern 6 (Hybrid) for compliance + backup.

### "We don't need monitoring in dev"
❌ **Problem**: Production disasters undetectable. SLAs broken silently.
✅ **Solution**: Same monitoring setup as production, just one box.

---

## 📚 Learn More

- **[Dual-Engine Architecture Deep Dive](../explanation/dual-engine-architecture.md)**
- **[Deployment Architecture Explanation](../explanation/deployment-architecture.md)**
- **[ADR-001: Engine Selection](../explanation/decisions/ADR-001-dual-engine-architecture.md)**
- **[Performance Tuning Guide](../PERFORMANCE.md)**
- **[Deployment Calculator](./DEPLOYMENT_CALCULATOR.md)** — Another way to choose

---

## 🆘 Still Confused?

1. **Find your pattern** above (1-6) that matches your answers
2. **Read the "When to Use" section** — Does it fit?
3. **Follow the implementation steps** — Concrete actions
4. **Check the links** — Detailed setup guides
5. **Cost estimate** — Budget check

**Still need help?** See [FAQ & Common Issues](./FAQ_AND_COMMON_ISSUES.md)
