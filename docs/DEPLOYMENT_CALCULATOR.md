# YAWL Deployment Calculator

**Time to Complete**: 10 minutes
**Goal**: Get a personalized deployment recommendation based on your requirements

---

## Quick Start

Answer the 7 questions below. Your answers will generate a specific deployment architecture recommendation with implementation steps.

---

## SECTION 1: CASE VOLUME & SCALE

### Question 1: Expected Case Volume

How many **new cases per day** will your system handle once fully deployed?

- [ ] **A** — 0-100 cases/day (< 1 case per minute)
- [ ] **B** — 100-1,000 cases/day (1-10 cases per minute)
- [ ] **C** — 1,000-10,000 cases/day (10-100 cases per minute)
- [ ] **D** — 10,000-100,000 cases/day (100-1,000 cases per minute)
- [ ] **E** — 100,000+ cases/day (1,000+ cases per minute)

**💡 Help**: Count all workflow instances, not just human tasks. Include automated cases.

---

### Question 2: Peak Concurrent Cases

What is your **peak load** — how many cases should be executing simultaneously?

- [ ] **1** — Single case execution (testing, demo, low volume)
- [ ] **10** — Tens of concurrent cases (small team, 50-500 daily cases)
- [ ] **100** — Hundreds of concurrent cases (team of 20-50, 1K-5K daily)
- [ ] **1,000** — Thousands of concurrent cases (enterprise, 5K-50K daily)
- [ ] **10,000+** — Massive scale (mega-enterprise, 50K+ daily)

**💡 Help**: Estimate: (average case duration in minutes) × (cases per minute) ÷ 60

---

## SECTION 2: WORKFLOW CHARACTERISTICS

### Question 3: Case Duration Profile

How long do your typical cases run?

- [ ] **SHORT** — Seconds to minutes (automated, single-shot, no human wait)
- [ ] **MEDIUM** — Minutes to hours (some human input, quick turnaround)
- [ ] **LONG** — Hours to days (human in loop, waiting periods common)
- [ ] **VERY_LONG** — Days to weeks (approval workflows, complex approvals)

**💡 Help**: Think about the longest typical case. Does it sit idle waiting for human action?

---

### Question 4: Human Task Requirements

Does your workflow include human tasks (work items, approvals, assignments)?

- [ ] **NO** — Fully automated, no human intervention
- [ ] **LIGHT** — Few human tasks (< 20% of cases have human steps)
- [ ] **MEDIUM** — Regular human tasks (20-50% of cases need human work)
- [ ] **HEAVY** — Many human tasks (50%+ of cases require human action)
- [ ] **CRITICAL** — Work queue is central (all cases need human approval)

**💡 Help**: Count tasks where a person must actively make a decision or input data.

---

## SECTION 3: INFRASTRUCTURE & CONSTRAINTS

### Question 5: Deployment Environment

Where will your system run?

- [ ] **CLOUD_SERVERLESS** — AWS Lambda, Google Cloud Functions, Azure Functions (auto-scale, no persistent resources)
- [ ] **CLOUD_CONTAINER** — Kubernetes, ECS, GKE (managed Kubernetes or container orchestration)
- [ ] **CLOUD_VM** — EC2, Azure VMs, GCP Compute (virtual machines, you manage patching)
- [ ] **ON_PREMISE** — Your data center (physical or private cloud)
- [ ] **HYBRID** — Mix of on-premise and cloud (need multi-region support)

**💡 Help**: Serverless = no database between invocations. Container = managed DB available. VM = full control.

---

### Question 6: Data Persistence Requirements

How critical is persistence and recovery?

- [ ] **NONE** — No persistence needed (testing, ephemeral cases)
- [ ] **OPTIONAL** — Nice to have, but cases can be lost if server crashes
- [ ] **REQUIRED** — Must recover cases after server restart (standard production)
- [ ] **CRITICAL** — Audit trail required for compliance (healthcare, financial)
- [ ] **IMMUTABLE** — Archival + audit + temporal queries (regulatory, 7+ year retention)

**💡 Help**: "Persistence" = storing case state to a database. "Recovery" = restart and find old cases.

---

### Question 7: Budget & Resources

What is your deployment budget and team size?

- [ ] **MINIMAL** — \< \$5K/month, 1-2 people (free tier, shared cloud)
- [ ] **SMALL** — \$5K-20K/month, 3-5 people (modest cloud, small team)
- [ ] **MEDIUM** — \$20K-100K/month, 5-20 people (enterprise cloud tier)
- [ ] **LARGE** — \$100K+/month, 20+ people (multi-region, dedicated resources)
- [ ] **UNCONSTRAINED** — Cost not primary concern (mission-critical, large org)

**💡 Help**: Budget includes cloud compute, database, monitoring, licenses, team salary.

---

## YOUR DEPLOYMENT RECOMMENDATION

**Scroll down to find your matching scenario** based on your answers above.

---

## SCENARIO MATCHER

### **Scenario A: Lightweight Testing & Development**

**Matches if you answered:**
- Q1: A (0-100 cases/day)
- Q2: 1-10 concurrent
- Q5: Cloud serverless OR on-premise single machine
- Q7: Minimal budget

**RECOMMENDATION: Stateless Engine + Local PostgreSQL**

```
Architecture:
┌──────────────┐
│   Your Code  │
│  (REST API)  │
└──────┬───────┘
       │
┌──────v──────────────────────┐
│  YAWL Stateless Engine       │
│  (YStatelessEngine)          │
│  In-memory case state        │
└──────┬──────────────────────┘
       │ (optional)
┌──────v──────────────────────┐
│  PostgreSQL (local or cloud) │
│  Optional persistence        │
└──────────────────────────────┘
```

**Why this works:**
- Stateless engine needs no database to execute cases
- No human work queue to manage
- Zero operational overhead
- Perfect for CI/CD testing

**Implementation Steps:**
1. Add `yawl-stateless` to your Maven pom.xml
2. Create `YStatelessEngine` instance
3. Load your `.yawl` specification
4. Call `executeCase(specification, inputData)` per case
5. Optional: store results to PostgreSQL if needed

**Learn More:**
- [Stateless vs Persistent Architecture](explanation/stateless-vs-persistent-architecture.md)
- [Tutorial: Run Your First Workflow](tutorials/03-run-your-first-workflow.md)

**Estimated Setup Time**: 1-2 hours
**Monthly Cost**: \$0-500 (free tier mostly)

---

### **Scenario B: Rapid Development with Persistence**

**Matches if you answered:**
- Q1: A-B (100-1,000 cases/day)
- Q2: 10-100 concurrent
- Q3: Short to medium duration
- Q4: No or light human tasks
- Q5: Cloud container OR on-premise
- Q7: Small to medium budget

**RECOMMENDATION: Stateful Engine + Managed PostgreSQL + Single Instance**

```
Architecture:
┌─────────────────────────────┐
│  Load Balancer / Reverse    │
│  Proxy (nginx/HAProxy)      │
└────────────┬────────────────┘
             │
┌────────────v────────────────┐
│  YAWL Engine (Tomcat)        │
│  Replicas: 1-2              │
│  Stateful engine with DB    │
└────────────┬────────────────┘
             │
┌────────────v────────────────┐
│  PostgreSQL (Managed)        │
│  Auto-backup, read replicas │
└──────────────────────────────┘
```

**Why this works:**
- Stateful engine survives restarts (database recovery)
- Small replica count keeps costs low
- Managed database handles backups automatically
- Good for teams learning YAWL

**Implementation Steps:**
1. Deploy `yawl.war` to Tomcat (2 instances)
2. Set up managed PostgreSQL (AWS RDS, Azure Database, etc.)
3. Configure `yawl/WEB-INF/web.xml` with DB connection pool
4. Enable clustering if you deploy 2+ instances
5. Set up monitoring with OpenTelemetry

**Learn More:**
- [Deployment Architecture](explanation/deployment-architecture.md)
- [Production Deployment How-To](how-to/deployment/production.md)

**Estimated Setup Time**: 4-8 hours
**Monthly Cost**: \$2K-8K (compute + database + monitoring)

---

### **Scenario C: Production with Human Workflows**

**Matches if you answered:**
- Q1: B-D (1,000-100,000 cases/day)
- Q2: 100-1,000 concurrent
- Q3: Medium to long duration
- Q4: Medium to heavy human tasks
- Q5: Cloud container (Kubernetes)
- Q7: Medium to large budget

**RECOMMENDATION: Dual-Engine Architecture with Auto-Scaling**

```
Architecture:
┌────────────────────────────────────────┐
│  API Gateway / Load Balancer           │
│  (Route based on case profile)          │
└──────┬─────────────────────┬───────────┘
       │                     │
┌──────v──────────┐   ┌──────v──────────┐
│  Stateless Engine│   │  Stateful Engine │
│  Auto-scale 2-20│   │  Fixed 3-5 reps  │
│  (< 5 min cases)│   │  (long cases)    │
└──────┬──────────┘   └──────┬───────────┘
       │                     │
       └──────────┬──────────┘
                  │
       ┌──────────v──────────────┐
       │  PostgreSQL Cluster      │
       │  Primary + 2 read replicas
       │  Auto-failover enabled   │
       └──────────────────────────┘

       ┌──────────────────────────┐
       │  Redis (Sessions/Cache)  │
       │  Sentinel mode enabled   │
       └──────────────────────────┘
```

**Why this works:**
- Stateless auto-scales for short automated cases (cost efficient)
- Stateful handles human tasks (no timeout pressure)
- Automatic engine selection routes cases properly
- Full recovery on failure

**Implementation Steps:**
1. Deploy both `yawl.war` (stateful, 3-5 replicas) and stateless engine (auto-scale group)
2. Set up `EngineSelector` in `yawl-integration` to route cases automatically
3. Deploy PostgreSQL cluster with failover (managed services preferred)
4. Configure Redis for session clustering
5. Enable HPA (Horizontal Pod Autoscaler) for stateless replicas
6. Set up Prometheus + Grafana for monitoring

**Learn More:**
- [Dual-Engine Architecture](explanation/dual-engine-architecture.md)
- [ADR-001: Engine Selection Decision](explanation/decisions/ADR-001-dual-engine-architecture.md)
- [Kubernetes Deployment Guide](how-to/deployment/kubernetes.md)

**Estimated Setup Time**: 2-3 weeks (first time)
**Monthly Cost**: \$15K-50K (varies by region and scale)

---

### **Scenario D: Massive Scale (100K+ Cases/Day)**

**Matches if you answered:**
- Q1: D-E (10,000+ cases/day)
- Q2: 1,000+ concurrent
- Q5: Cloud container (Kubernetes, multi-region)
- Q7: Large to unconstrained budget

**RECOMMENDATION: Event-Sourced Stateless Engine + Event Store + CQRS**

```
Architecture:
┌─────────────────────────────────────────────┐
│  Global Load Balancer (multi-region)        │
└─────┬───────────────────────────────┬───────┘
      │                               │
┌─────v──────────────┐       ┌────────v─────────────┐
│  Region 1 (US)     │       │  Region 2 (EU)       │
│  Stateless Cluster │       │  Stateless Cluster   │
│  Auto-scale 50-200 │       │  Auto-scale 50-200   │
└─────┬──────────────┘       └────────┬─────────────┘
      │                               │
      └─────────────┬─────────────────┘
                    │
       ┌────────────v──────────────┐
       │  Event Store (Kafka/PubSub)│
       │  Partition by case ID      │
       └────────────┬───────────────┘
                    │
       ┌────────────v──────────────┐
       │  PostgreSQL Read Replicas  │
       │  (CQRS read model)         │
       │  Multi-region replication  │
       └────────────────────────────┘
```

**Why this works:**
- Stateless scales horizontally to any load
- Event store provides true audit trail + replay capability
- CQRS separates writes from reads for massive throughput
- Multi-region automatic failover

**Implementation Steps:**
1. Deploy stateless engine auto-scaled across 2+ regions
2. Set up Kafka or Google Cloud Pub/Sub as event store
3. Implement event sourcing in `yawl-stateless` (or use event store plugin)
4. Set up PostgreSQL read replicas per region
5. Implement CQRS read layer for analytics and historical queries
6. Deploy monitoring across all regions with centralized logging

**Learn More:**
- [Million Case Architecture](explanation/million-case-architecture.md)
- [Tutorial: Scale to 1M Cases](tutorials/11-scale-to-million-cases.md)
- [Event Store Optimization](docs/EventStoreOptimizationGuide.md)

**Estimated Setup Time**: 4-8 weeks (complex, needs experienced team)
**Monthly Cost**: \$100K+ (proportional to case volume)

---

### **Scenario E: Compliance & Long-Term Archival**

**Matches if you answered:**
- Q4: Heavy or critical human tasks
- Q6: Critical or immutable persistence
- Q7: Medium to unconstrained budget

**RECOMMENDATION: Stateful Engine + PostgreSQL + Archival Store**

```
Architecture:
┌─────────────────────────────────────────┐
│  YAWL Engine with Audit Trail           │
│  Replicas: 3-5 (high availability)      │
└─────┬───────────────────────────┬───────┘
      │                           │
┌─────v──────────────┐   ┌────────v──────────┐
│  PostgreSQL (Hot)  │   │  Archive Store    │
│  Current cases,    │   │  (S3/Blob Store)  │
│  2-3 year retention│   │  7+ year retention│
└─────┬──────────────┘   └────────┬──────────┘
      │                           │
      └─────────────┬─────────────┘
                    │
       ┌────────────v──────────────┐
       │  Archival Script (monthly) │
       │  Moves old cases to cold   │
       │  storage with encryption   │
       └────────────────────────────┘
```

**Why this works:**
- Stateful engine with full audit trail for every state change
- PostgreSQL for live case management (fast queries)
- Cloud archival for long-term retention (compliance)
- Encrypted, immutable historical records

**Implementation Steps:**
1. Deploy stateful `yawl.war` with 3-5 replicas for high availability
2. Configure PostgreSQL for 2-3 year live data retention
3. Enable audit logging in `yawl-engine` configuration
4. Set up monthly archival job: query old completed cases → archive to S3/blob store
5. Encrypt archived data with customer-managed KMS keys
6. Implement access controls and audit logging for archive reads
7. Set up compliance reporting dashboards

**Learn More:**
- [ADR-011: Data Retention & Compliance](explanation/decisions/ADR-011-compliance-architecture.md)
- [Deployment for Regulated Industries](how-to/deployment/regulated-industries.md)

**Estimated Setup Time**: 3-4 weeks (first time)
**Monthly Cost**: \$10K-30K (compute + database + archival)

---

### **Scenario F: AI Agent Integration (MCP/A2A)**

**Matches if you answered:**
- Q1: Variable (MCP tools can handle any volume)
- Q3: Short (typically seconds)
- Q4: No human tasks (agents handle everything)
- Q5: Cloud container (any platform)

**RECOMMENDATION: Stateless Engine + MCP/A2A Server**

```
Architecture:
┌─────────────────────────────┐
│  AI Agent / LLM             │
│  (Claude, GPT, etc.)        │
└────────────┬────────────────┘
             │ MCP / A2A Protocol
┌────────────v────────────────────────┐
│  YAWL MCP/A2A Server                 │
│  (yawl-mcp-a2a-app)                 │
└────────────┬────────────────────────┘
             │
┌────────────v────────────────────────┐
│  YAWL Stateless Engine               │
│  (YStatelessEngine)                  │
│  Single-shot case execution          │
└────────────┬────────────────────────┘
             │
┌────────────v────────────────────────┐
│  Optional: PostgreSQL (for history)  │
│  Only if agent needs to query past   │
└──────────────────────────────────────┘
```

**Why this works:**
- Stateless engine maps cleanly to MCP tool model (input → output)
- No human work queue (agents automate everything)
- MCP protocol handles agent communication
- Lightweight and cost-effective

**Implementation Steps:**
1. Deploy `yawl-mcp-a2a-app` container
2. Expose MCP/A2A endpoints (standard ports)
3. Configure with your `.yawl` specifications
4. Document available workflows as MCP tools
5. Optional: add PostgreSQL if agent needs historical queries
6. Test with `curl` or Claude API

**Learn More:**
- [MCP Agent Integration Tutorial](tutorials/08-mcp-agent-integration.md)
- [MCP/A2A Architecture](explanation/integration-architecture.md)
- [Autonomous Agents](explanation/autonomous-agents.md)

**Estimated Setup Time**: 2-4 hours
**Monthly Cost**: \$500-2K (just compute, no DB unless needed)

---

## DECISION TREE AT A GLANCE

```
START: What's your case volume?
  │
  ├─ < 100/day
  │  └─ Human tasks? NO → Scenario A (Stateless)
  │                 YES → Scenario B (Single stateful)
  │
  ├─ 100-10K/day
  │  ├─ Compliance needed? YES → Scenario E (Archival)
  │  └─ Cloud Kubernetes? YES → Scenario C (Dual engine)
  │                     NO → Scenario B (Single stateful)
  │
  ├─ 10K-100K/day
  │  └─ Kubernetes? YES → Scenario C (Dual engine, scaled)
  │
  ├─ 100K+/day
  │  └─ Scenario D (Event-sourced, multi-region)
  │
  └─ AI agents?
     └─ Scenario F (MCP/A2A)
```

---

## NEXT STEPS

1. **Find your scenario above** — Read the full description
2. **Review the architecture diagram** — Understand components
3. **Click "Learn More" links** — Go deeper into documentation
4. **Start with tutorials** — Step-by-step implementation
5. **Ask questions** — Community forum or GitHub issues

---

## Common Follow-Up Questions

**Q: Can I start small and upgrade?**
A: Yes! Scenarios B → C → D is a natural upgrade path. Start with a single stateful instance, add stateless when you need to scale, then add event sourcing at 50K+ cases/day.

**Q: What if my answers don't fit one scenario?**
A: You likely need a **hybrid** approach. Example: Scenario B for core workflows + Scenario F for agent integration. Read multiple scenarios and combine.

**Q: How do I estimate my case volume?**
A: Talk to your business stakeholders. Get: average daily cases (likely 9am-5pm only) + peak day multiplier. If they say "We're not sure," assume 1,000 cases/day initially.

**Q: Should I use stateful or stateless?**
A: **Stateful** (YEngine) if cases run > 5 minutes OR involve human tasks.
**Stateless** (YStatelessEngine) if cases complete in seconds AND are fully automated.
**Both** (Scenario C) if you have a mix.

**Q: What about on-premise?**
A: Scenarios A-B work great on-premise. Scenario C requires Kubernetes or equivalent (OpenShift, DC/OS). Scenario D is AWS/Azure/GCP native.

---

## CONFIDENCE LEVELS

- **Scenario A, B, F**: High confidence — well-tested patterns
- **Scenario C**: High confidence — mature production setup
- **Scenario D**: Medium confidence — complex, requires expertise
- **Scenario E**: High confidence — established compliance pattern

---

**Last Updated**: 2026-02-28
**Version**: 1.0
**Questions?** See [FAQ & Common Issues](FAQ_AND_COMMON_ISSUES.md) or post to GitHub Discussions
