# YAWL Interactive Decision Guides

**Purpose**: Make complex YAWL decisions in 10-15 minutes without reading long documentation.

This page indexes **4 complementary decision guides** that help you choose the right path for your YAWL deployment. Each guide is **self-contained** and answers a specific question.

---

## When to Use Each Guide

### 1. 📦 Module Selector (8-10 minutes)

**Question**: "Which YAWL modules do I actually need?"

**Use this if**:
- You're new to YAWL and overwhelmed by 22 modules
- You're unsure whether to add monitoring, polyglot support, or advanced features
- You want to minimize complexity and dependencies
- You need a shopping list of exactly what to download

**Output**: A personalized list of modules with setup time and links to tutorials

**Read**: [MODULE_SELECTOR.md](./MODULE_SELECTOR.md)

**Time**: 8-10 minutes to answer questions, then follow your scenario

---

### 2. 🏗️ Architecture Patterns Advisor (10-12 minutes)

**Question**: "Which architecture pattern fits my system?"

**Use this if**:
- You're choosing between stateless vs stateful engines
- You want to understand single-region vs multi-region deployments
- You're deciding between edge computing, traditional servers, or serverless
- You need deployment diagrams and example configurations
- You want to see pros/cons of each architecture

**Output**: A matched architecture pattern with detailed setup guide, cost estimate, and trade-offs

**Read**: [ARCHITECTURE_PATTERNS_ADVISOR.md](./ARCHITECTURE_PATTERNS_ADVISOR.md)

**Covers**:
- Pattern 1: Stateless Cloud-Native (fastest to deploy)
- Pattern 2: Stateful Monolithic (simplest to manage)
- Pattern 3: Hybrid Dual-Engine (balanced performance)
- Pattern 4: Multi-Region HA (enterprise grade)
- Pattern 5: Edge Computing (ultra-low latency)
- Pattern 6: Hybrid On-Premise (regulatory compliance)

**Time**: 10-12 minutes to answer questions, then pick your pattern

---

### 3. ⚖️ Performance Trade-off Explorer (10-12 minutes)

**Question**: "How do I balance latency, throughput, cost, and consistency?"

**Use this if**:
- You're deciding between low-latency vs high-throughput optimization
- You need to choose between cost and performance
- You want to understand the trade-offs between consistency models
- You're building SLOs and need realistic targets
- You want cost breakdowns for each scenario

**Output**: A performance scenario matched to your constraints, with detailed configuration, metrics, and tuning parameters

**Read**: [PERFORMANCE_TRADEOFFS_EXPLORER.md](./PERFORMANCE_TRADEOFFS_EXPLORER.md)

**Covers**:
- Scenario 1: Real-Time Low Latency (< 100ms P95)
- Scenario 2: High Throughput (millions of cases)
- Scenario 3: Cost-Optimized (minimize spend)
- Scenario 4: Strong Consistency (ACID guarantees)
- Scenario 5: Maximum Availability (99.99% uptime)

**Time**: 10-12 minutes to answer questions, then pick your scenario

---

### 4. 📊 Deployment Calculator (10 minutes)

**Question**: "What's the right deployment architecture for my scale and requirements?"

**Use this if**:
- You're deciding between cloud, on-premise, or hybrid
- You need to estimate monthly cost
- You want to know if you need stateless, stateful, or both engines
- You're scoping implementation effort
- You want to see example architectures for different scales

**Output**: A deployment recommendation with architecture diagram, implementation steps, and cost estimate

**Read**: [DEPLOYMENT_CALCULATOR.md](./DEPLOYMENT_CALCULATOR.md)

**Covers**:
- Scenario A: Lightweight Testing & Development
- Scenario B: Rapid Development with Persistence
- Scenario C: Production with Human Workflows
- Scenario D: Massive Scale (100K+ cases/day)
- And more...

**Time**: 10 minutes to answer questions, then follow your scenario

---

---

## Quick Decision Map

Need to decide on **multiple things**? Use this flow:

```
Are you NEW to YAWL?
├─ YES → Start with Module Selector (pick modules)
│        Then → Deployment Calculator (pick architecture)
│        Then → Architecture Patterns Advisor (detailed setup)
│        Then → Performance Explorer (optimize)
│
└─ NO → What are you deciding?
   ├─ "What modules do I need?" → Module Selector
   ├─ "What architecture should I use?" → Deployment Calculator or Patterns Advisor
   ├─ "How do I handle latency/throughput/cost trade-offs?" → Performance Explorer
   └─ "Everything, I'm redesigning" → Start with Deployment Calculator
```

---

## Comparison: When to Use Each Guide

| Decision | Module Selector | Deployment Calc | Patterns Advisor | Performance Explorer |
|----------|---|---|---|---|
| Which modules do I need? | ✅ Primary | ⚠️ Secondary | ❌ Not covered | ❌ Not covered |
| What deployment architecture? | ❌ Not covered | ✅ Primary | ✅ Primary | ⚠️ Secondary |
| Scale from 1K to 1M cases/day? | ❌ Not covered | ✅ Primary | ⚠️ Secondary | ✅ Primary |
| Latency vs throughput trade-offs? | ❌ Not covered | ❌ Not covered | ⚠️ Secondary | ✅ Primary |
| Cost estimate? | ⚠️ Setup time | ✅ Monthly cost | ✅ Cost estimate | ✅ Cost breakdown |
| What does it cost to run? | ⚠️ Setup time | ✅ $X/month | ✅ $X/month | ✅ $X/month |
| Step-by-step setup guide? | ✅ Links to tutorials | ✅ Implementation steps | ✅ Detailed steps | ✅ Configuration examples |

---

## Getting Started: Choose Your Path

### Path 1: I'm Starting a New Project

1. **[Deployment Calculator](./DEPLOYMENT_CALCULATOR.md)** (10 min)
   - Answer 7 questions about your scale, requirements, budget
   - Get a specific deployment architecture
   - See cost estimate

2. **[Module Selector](./MODULE_SELECTOR.md)** (10 min)
   - Based on your deployment, find which modules you need
   - Get setup time and tutorial links
   - Know exactly what to download

3. **[Architecture Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md)** (10 min)
   - Understand your architecture pattern deeply
   - Review pros/cons and trade-offs
   - Follow detailed implementation steps

4. **[Performance Trade-off Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md)** (optional, 10 min)
   - Fine-tune performance based on your constraints
   - Choose between latency, throughput, cost, consistency
   - Get tuning parameters

**Total time**: ~30-40 minutes to have a complete plan

---

### Path 2: I'm Optimizing an Existing System

1. **[Performance Trade-off Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md)** (10 min)
   - Identify your primary constraint (latency/throughput/cost/consistency)
   - See the matching scenario
   - Get tuning parameters for your scenario

2. **[Architecture Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md)** (optional, 10 min)
   - Understand if your current pattern is optimal
   - Review alternative patterns
   - See cost/performance comparisons

**Total time**: ~10-20 minutes

---

### Path 3: I Have a Specific Question

| Question | Guide |
|----------|-------|
| Which modules should I use? | [Module Selector](./MODULE_SELECTOR.md) |
| How much will it cost? | [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) or [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md) |
| How should I deploy it? | [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) or [Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md) |
| How do I handle scale? | [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) (for volume) or [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md) (for throughput tuning) |
| Latency vs cost trade-off? | [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md) |
| Stateless vs stateful? | [Architecture Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md) |
| Single-region vs multi-region? | [Architecture Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md) |

---

---

## Key Concepts Explained

### Deployment Architecture

**What it is**: The topology of YAWL servers, databases, caches, and load balancers

**Where to learn**: [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) or [Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md)

**Examples**:
- Single stateful server + PostgreSQL (simple, limited scale)
- Kubernetes cluster with auto-scaling (cloud-native)
- Multi-region active-active (enterprise HA)

---

### Performance Optimization

**What it is**: Tuning for latency, throughput, cost, or consistency

**Where to learn**: [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md)

**Key trade-off**:
- Low latency (< 100ms) = expensive, limited scale
- High throughput (1M cases/day) = slower responses, expensive storage
- Low cost ($500/month) = acceptable latency, limited throughput
- Strong consistency (ACID) = slower writes, limited scale
- **You pick 2-3 of these. You can't have all.**

---

### Modules

**What it is**: Maven modules that provide specific features

**Where to learn**: [Module Selector](./MODULE_SELECTOR.md)

**Examples**:
- `yawl-engine` — The workflow execution engine (required)
- `yawl-stateless-engine` — Serverless variant of engine
- `yawl-monitoring` — Metrics, tracing, dashboards
- `yawl-resourcing` — Work queue and task assignment
- `yawl-integration` — REST API and external service integration

---

---

## Common Scenarios

### Scenario: Startup Building MVP

1. **Cost is critical**
2. **Team is small** (2-5 people)
3. **Cases are fast** (seconds to minutes)
4. **Scale is low** (1K-10K cases/day)

**Recommended path**:
- [Module Selector](./MODULE_SELECTOR.md) → Scenario 1 (Simple Execution)
- [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) → Scenario A or B
- [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md) → Scenario 3 (Cost-Optimized)

**Result**: Stateless engine on AWS Lambda (~$500/month), minimal modules

---

### Scenario: Enterprise Workflow System

1. **Human tasks are critical** (work queue, approvals)
2. **Cases are long-running** (hours to days)
3. **Consistency matters** (audit trail)
4. **Availability is high SLA** (99.5%+)

**Recommended path**:
- [Module Selector](./MODULE_SELECTOR.md) → Scenario 6 (Enterprise)
- [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) → Scenario C
- [Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md) → Pattern 4 or 2
- [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md) → Scenario 4 or 5

**Result**: Stateful cluster with resourcing, multi-region HA ($10K-15K/month)

---

### Scenario: Real-Time API Backend

1. **Latency is critical** (< 100ms)
2. **Throughput is high** (50K cases/sec)
3. **No human tasks**
4. **Cost is moderate**

**Recommended path**:
- [Module Selector](./MODULE_SELECTOR.md) → Scenario 2 (Polyglot/Integration)
- [Deployment Calculator](./DEPLOYMENT_CALCULATOR.md) → Scenario C or D
- [Patterns Advisor](./ARCHITECTURE_PATTERNS_ADVISOR.md) → Pattern 1 or 3
- [Performance Explorer](./PERFORMANCE_TRADEOFFS_EXPLORER.md) → Scenario 1

**Result**: Stateless engine on Kubernetes with auto-scaling ($3-8K/month)

---

---

## Tips for Using These Guides

### Tip 1: Answer Honestly

The guides ask about your constraints, not your ideal scenario. If cost is tight, say so. If latency doesn't matter, say so. The recommendations are based on your actual constraints.

### Tip 2: Start Simple, Add Complexity Later

You can always add modules, migrate to a more complex architecture, or optimize performance later. Start with the simplest scenario that meets your needs.

### Tip 3: Use the Guides Together

The guides are complementary:
- Module Selector tells you what to build
- Deployment Calculator tells you where to run it
- Patterns Advisor tells you how to structure it
- Performance Explorer tells you how to tune it

### Tip 4: Check the Trade-offs

Each scenario lists trade-offs (what you give up). Make sure the trade-offs are acceptable for your use case.

### Tip 5: Cost Estimates Are Rough

Actual costs depend on your cloud provider, region, and usage patterns. Use the estimates for comparison and budgeting, not exact planning.

### Tip 6: Follow the Links

Each guide has links to detailed tutorials, how-to guides, and reference docs. If you need more detail, follow the links.

---

## Feedback & Updates

These guides are living documents. If you:
- Find the guides confusing
- Disagree with a recommendation
- Have a scenario not covered
- Found a better configuration

Please open an issue or contribute a PR. Help us make these guides better.

---

## Index of All Decision Guides

1. **[MODULE_SELECTOR.md](./MODULE_SELECTOR.md)** — Pick which modules you need (8-10 min)
2. **[DEPLOYMENT_CALCULATOR.md](./DEPLOYMENT_CALCULATOR.md)** — Pick your deployment architecture (10 min)
3. **[ARCHITECTURE_PATTERNS_ADVISOR.md](./ARCHITECTURE_PATTERNS_ADVISOR.md)** — Detailed architecture patterns (10-12 min)
4. **[PERFORMANCE_TRADEOFFS_EXPLORER.md](./PERFORMANCE_TRADEOFFS_EXPLORER.md)** — Optimize latency/throughput/cost (10-12 min)

---

## Next Steps

1. **Pick a guide** from above based on what you're deciding
2. **Answer the questions** (should take 10 minutes)
3. **Find your scenario** in the results
4. **Follow the implementation steps** or check the links
5. **Build and deploy** your YAWL system

Good luck! And remember: these guides are here to help you make smart decisions without getting lost in the docs.
