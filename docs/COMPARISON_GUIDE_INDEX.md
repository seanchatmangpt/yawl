# YAWL v6 Comparison & Planning Guides — Master Index

**Status**: Complete | **Last Updated**: February 2026 | **5-Document Suite**

This index provides navigation through the comprehensive comparison and planning guides for YAWL v6.0.0.

---

## Quick Navigation

### By Use Case

**I want to deploy YAWL today**
1. Read [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md#2-persistent-engine-yengine) (2.1-2.5)
2. Check [Performance Matrix](PERFORMANCE_MATRIX.md#2-deployment-option-comparison) (section 2)
3. Follow [Migration Planner](MIGRATION_PLANNER.md#2-pre-migration-checklist-week-0) (if from v5)

**I need to scale from 100 to 1K cases/sec**
1. Start with [Scaling Decisions](SCALING_DECISIONS.md#2-current-state-assessment)
2. Diagnose bottleneck [Section 3](SCALING_DECISIONS.md#3-bottleneck-diagnosis--solutions)
3. Implement solution with recommended config

**I'm upgrading from v5 to v6**
1. Read [Feature Matrix](FEATURE_MATRIX.md#8-breaking-changes-v5--v6) for breaking changes
2. Follow [Migration Planner](MIGRATION_PLANNER.md) step-by-step (10-20 days)
3. Use [Scaling Decisions](SCALING_DECISIONS.md) for performance tuning after migration

**I need to choose: Persistent vs Stateless**
1. [Architecture Trade-Offs Section 4](ARCHITECTURE_TRADEOFFS.md#4-persistent-vs-stateless-complete-analysis)
2. Compare [Performance Matrix Section 2.1 vs 2.2](PERFORMANCE_MATRIX.md#21-single-instance-persistent-stateful)
3. See decision tree [Architecture Trade-Offs Section 8](ARCHITECTURE_TRADEOFFS.md#8-decision-framework-visual)

**I want real-world scenarios with numbers**
→ [Performance Matrix Section 3](PERFORMANCE_MATRIX.md#3-real-world-scenarios--recommendations) — 3 complete examples

---

## Document Overview

### 1. Performance Comparison Matrix
**File**: `/home/user/yawl/docs/PERFORMANCE_MATRIX.md` (300+ lines)

**What it covers**:
- Deployment option comparison (stateful vs stateless vs cloud)
- Real metrics: throughput, latency, memory, cost, complexity
- Scenarios at 1K, 100K, and 1M cases
- Detailed cost breakdown
- Monitoring dashboard setup
- Java 25 optimization impact

**Key sections**:
- Section 1: Executive summary (1-page overview)
- Section 2: Detailed 4-option comparison (100 lines each)
- Section 3: Real-world scenarios with costs ($3-5K to $50K+)
- Section 4: Comparison table (all metrics)
- Section 5: Decision tree (diagnostic)

**Use this document when**:
✓ You need to pick a deployment option
✓ You want to know costs (CapEx + OpEx)
✓ You're sizing infrastructure
✓ You need to present to stakeholders
✓ You want to compare latency/throughput

---

### 2. Feature Support Matrix
**File**: `/home/user/yawl/docs/FEATURE_MATRIX.md` (400+ lines)

**What it covers**:
- Module dependency map (20+ modules, 6 layers)
- Feature support by engine (Persistent vs Stateless)
- Database compatibility matrix
- Java version support (17, 21, 25)
- Breaking changes v5→v6
- Version support timeline
- Feature roadmap (v6.1, v6.2, v6.3)

**Key sections**:
- Section 2: Module map (shows what depends on what)
- Section 3: Module feature support (4 modules × 3 dimensions)
- Section 4: Feature capability matrix (20 features × 3 engines)
- Section 6: Database compatibility + performance
- Section 8: Breaking changes + migration path

**Use this document when**:
✓ You need to know what features are available
✓ You're migrating custom code
✓ You need database compatibility info
✓ You want to understand module dependencies
✓ You're evaluating Java version support

---

### 3. Migration Planner
**File**: `/home/user/yawl/docs/MIGRATION_PLANNER.md` (500+ lines)

**What it covers**:
- Complete v5→v6 migration guide (step-by-step)
- 6 phases over 2-6 weeks
- Custom code migration patterns
- Database schema changes
- Cutover procedure (with scripts)
- Rollback procedures
- Common issues & solutions
- Gantt chart timeline

**Key sections**:
- Section 2: Pre-migration assessment
- Section 3: Phase 1 - Environment setup (Days 1-3)
- Section 4: Phase 2 - Schema migration (Days 4-5)
- Section 5: Phase 3 - Custom code migration (Days 6-10)
- Section 6: Phase 4 - Configuration migration (Days 11-12)
- Section 7-8: Testing & production (Days 13-20)
- Section 9: Rollback procedures

**Use this document when**:
✓ You're actively migrating from v5
✓ You need step-by-step procedures
✓ You want cutover scripts
✓ You need troubleshooting help
✓ You're estimating migration effort/cost

---

### 4. Scaling Decision Tree
**File**: `/home/user/yawl/docs/SCALING_DECISIONS.md` (400+ lines)

**What it covers**:
- Decision tree for diagnosing bottlenecks
- 5 bottleneck types (CPU, DB, Memory, Disk, Network)
- Solutions with code examples
- Capacity planning examples
- Monitoring dashboard setup
- Java 25 quick wins
- Database tuning (PostgreSQL)
- Load balancer configuration

**Key sections**:
- Section 1: Quick decision tree (start here)
- Section 2: Baseline measurement (scripts)
- Section 3: Bottleneck diagnosis (detailed solutions)
  - 3.1: CPU bottleneck (3 sub-types)
  - 3.2: Database bottleneck (5 solutions)
  - 3.3: Memory bottleneck
  - 3.4: Disk I/O
  - 3.5: Network
- Section 4: Scaling strategies
- Section 5: Capacity planning examples

**Use this document when**:
✓ Performance is degrading
✓ You need to scale up
✓ You want to optimize before scaling
✓ You're setting up monitoring
✓ You need to reduce costs

---

### 5. Architecture Trade-Offs Guide
**File**: `/home/user/yawl/docs/ARCHITECTURE_TRADEOFFS.md` (500+ lines)

**What it covers**:
- Persistent vs Stateless (deep comparison)
- Single instance vs Clustered vs Cloud
- Monolithic vs Microservices (when relevant)
- Decision matrix for each dimension
- Pros/cons for each option
- Configuration examples
- Migration paths between architectures

**Key sections**:
- Section 1: Persistent vs Stateless decision matrix
- Section 2: Persistent engine (when to choose, advantages, disadvantages)
- Section 3: Stateless engine (when to choose, advantages, disadvantages)
- Section 4: Hybrid architecture (mixed workload)
- Section 5: Single vs Clustered vs Cloud
- Section 6: Monolithic vs Microservices
- Section 7: Comparison summary matrix
- Section 8: Visual decision framework
- Section 9: Migration paths between architectures

**Use this document when**:
✓ You need to choose between engines
✓ You're evaluating scale-up options
✓ You want to understand trade-offs
✓ You're planning future architecture
✓ You need to explain choices to team/execs

---

## Document Cross-References

### Persistent vs Stateless

| Question | Document | Section |
|----------|----------|---------|
| **Which is faster?** | Performance Matrix | 2.1 vs 2.2 |
| **Which costs less?** | Performance Matrix | Section 3 (scenarios) |
| **Which is easier?** | Architecture Trade-Offs | Section 2-3 |
| **Pros/cons of each?** | Architecture Trade-Offs | 2.3-2.4, 3.3-3.4 |
| **How do I migrate?** | Architecture Trade-Offs | Section 9 |

### Database Questions

| Question | Document | Section |
|----------|----------|---------|
| **What databases work?** | Feature Matrix | 7 (database compatibility) |
| **How do I migrate schema?** | Migration Planner | Section 4 |
| **Performance tuning?** | Scaling Decisions | 3.2 (database bottleneck) |
| **Which DB is fastest?** | Performance Matrix | Section 3 (real scenarios) |
| **How do I scale DB?** | Scaling Decisions | 4.2 (database scaling strategies) |

### Scaling Questions

| Question | Document | Section |
|----------|----------|---------|
| **Where's the bottleneck?** | Scaling Decisions | Section 1 (decision tree) |
| **How do I scale to 10x load?** | Scaling Decisions | Section 5 (capacity planning) |
| **What's my actual throughput?** | Scaling Decisions | Section 2 (measurement) |
| **Should I scale up or out?** | Performance Matrix | Section 4 |
| **What will it cost?** | Performance Matrix | Section 3 (real scenarios) |

### Migration Questions

| Question | Document | Section |
|----------|----------|---------|
| **How long will migration take?** | Migration Planner | Section 2-11 |
| **What breaks in v6?** | Feature Matrix | Section 8 |
| **How do I update custom code?** | Migration Planner | Section 5 |
| **How do I migrate data?** | Migration Planner | Section 4 |
| **How do I test before cutover?** | Migration Planner | Section 7 |

---

## Reading Paths by Role

### System Administrator / DevOps

**Day 1**: [Performance Matrix](PERFORMANCE_MATRIX.md)
- Sections 1-2: Understand deployment options
- Section 3: Real scenarios (costs, sizing)

**Day 2**: [Scaling Decisions](SCALING_DECISIONS.md)
- Section 1: Decision tree (diagnose problems)
- Section 3: Solutions (implement fixes)

**Day 3**: [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md)
- Sections 5-6: Scaling options (single → clustered → cloud)
- Section 7: Choose your target state

**Time**: 3 hours reading + implementation

---

### Java Developer / Tech Lead

**Day 1**: [Feature Matrix](FEATURE_MATRIX.md)
- Section 4-5: What works where
- Section 8: Breaking changes (v5→v6)

**Day 2**: [Migration Planner](MIGRATION_PLANNER.md)
- Section 5: Custom code migration
- Section 6: Configuration migration

**Day 3**: [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md)
- Section 2-3: Engine capabilities
- Section 5: Persistence/scalability decisions

**Time**: 3 hours reading + code updates

---

### Database Administrator

**Day 1**: [Feature Matrix](FEATURE_MATRIX.md)
- Section 7: Database compatibility
- Section 6: Version support

**Day 2**: [Scaling Decisions](SCALING_DECISIONS.md)
- Section 3.2: Database bottleneck diagnosis
- Section 4.2: Database scaling strategies

**Day 3**: [Migration Planner](MIGRATION_PLANNER.md)
- Section 4: Schema migration
- Section 10: Post-migration optimization

**Time**: 3 hours reading + schema planning

---

### Project Manager / Executive Sponsor

**Quick**: [Performance Matrix](PERFORMANCE_MATRIX.md)
- Section 1: Executive summary (1 page)
- Section 3: Real scenarios with costs (3 examples)
- Section 5: Decision tree (quick pick)

**Planning**: [Migration Planner](MIGRATION_PLANNER.md)
- Section 2: Complexity assessment
- Section 11: Gantt chart timeline
- Section 13: Team sizing & costs

**Time**: 1 hour reading + planning

---

## Key Metrics Reference

### Throughput Targets

| Deployment | Throughput | Notes |
|-----------|-----------|-------|
| Single Persistent | 100-300/sec | Database bottleneck |
| Clustered Persistent (3 nodes) | 300-1K/sec | Still DB-limited |
| Stateless (3 instances) | 1K-5K/sec | Linear scaling |
| Stateless (50 instances) | 5K-50K/sec | Cloud autoscaling |

### Latency Targets

| Deployment | P50 | P99 | Notes |
|-----------|-----|-----|-------|
| Single Persistent | 50ms | 50-200ms | Varies with load |
| Clustered Persistent | 75ms | 150-300ms | Replication overhead |
| Stateless | 15ms | 10-100ms | Message queue latency |
| Cloud Stateless | 20-40ms | 100-200ms | Region dependent |

### Cost Estimates (Annual)

| Deployment | 1K Cases | 100K Cases | 1M Cases |
|-----------|---------|-----------|---------|
| Single Persistent | $5K | $15K | N/A |
| Clustered (3 nodes) | $20K | $50K | N/A |
| Stateless (auto-scale) | $10K | $40K | $150K |
| Cloud Stateless | $8K | $35K | $100K |

---

## Common Workflows

### Workflow 1: "My system is slow, what do I do?"

1. Open [Scaling Decisions](SCALING_DECISIONS.md#1-quick-decision-tree)
2. Follow decision tree (section 1)
3. Identify bottleneck (section 3)
4. Implement solution from matching section
5. Monitor improvement with dashboard (section 7)

**Time**: 30 min - 2 hours

---

### Workflow 2: "Should we migrate from v5?"

1. Check [Migration Planner](MIGRATION_PLANNER.md#2-pre-migration-checklist-week-0) section 2
2. Estimate effort & timeline (section 2)
3. Review breaking changes [Feature Matrix](FEATURE_MATRIX.md#8-breaking-changes-v5--v6)
4. Calculate cost/benefit vs new features
5. If yes, follow [Migration Planner](MIGRATION_PLANNER.md) phases

**Time**: 2-4 hours assessment + 2-6 weeks migration

---

### Workflow 3: "Persistent or Stateless?"

1. [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md) section 1 (decision matrix)
2. Compare pros/cons (sections 2-3)
3. Check real performance (Performance Matrix sections 2.1 vs 2.2)
4. Review costs (Performance Matrix section 3)
5. Follow decision framework (Architecture Trade-Offs section 8)

**Time**: 30 min - 1 hour

---

### Workflow 4: "Planning production deployment"

1. [Performance Matrix](PERFORMANCE_MATRIX.md) section 1-2 (choose deployment)
2. [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md) section 5-6 (HA/scaling)
3. [Scaling Decisions](SCALING_DECISIONS.md) section 7 (monitoring setup)
4. [Feature Matrix](FEATURE_MATRIX.md) sections 3-4 (module selection)
5. Document choices, proceed to implementation

**Time**: 2-3 hours planning

---

## Summary Checklist

Use this checklist to ensure you've reviewed all relevant documentation:

**Before Deployment**:
- [ ] Read relevant section of [Performance Matrix](PERFORMANCE_MATRIX.md)
- [ ] Choose architecture using [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md)
- [ ] Review feature support in [Feature Matrix](FEATURE_MATRIX.md)
- [ ] Plan scaling approach with [Scaling Decisions](SCALING_DECISIONS.md)

**Before Migration (if from v5)**:
- [ ] Review breaking changes [Feature Matrix](FEATURE_MATRIX.md#8-breaking-changes-v5--v6)
- [ ] Assess effort [Migration Planner](MIGRATION_PLANNER.md#2-pre-migration-checklist-week-0)
- [ ] Plan team & timeline [Migration Planner](MIGRATION_PLANNER.md#6-phase-4-production-deployment)
- [ ] Prepare rollback [Migration Planner](MIGRATION_PLANNER.md#64-rollback-procedure)

**After Deployment**:
- [ ] Set up monitoring [Scaling Decisions](SCALING_DECISIONS.md#7-monitoring-during-scaling)
- [ ] Capture baseline [Scaling Decisions](SCALING_DECISIONS.md#21-measure-your-baseline)
- [ ] Identify optimization opportunities [Scaling Decisions](SCALING_DECISIONS.md#section-3)
- [ ] Plan next phase [Architecture Trade-Offs](ARCHITECTURE_TRADEOFFS.md#9-migration-paths)

---

## Document Statistics

| Document | Lines | Sections | Tables | Code Examples | Diagrams |
|----------|-------|----------|--------|---------------|----------|
| Performance Matrix | 300+ | 9 | 12 | 4 | 8 |
| Feature Matrix | 400+ | 12 | 15 | 2 | 6 |
| Migration Planner | 500+ | 14 | 8 | 20+ | 5 |
| Scaling Decisions | 400+ | 8 | 10 | 15+ | 12 |
| Architecture Trade-Offs | 500+ | 10 | 8 | 12 | 10 |
| **Total** | **2100+** | **53** | **53** | **63+** | **41** |

---

## Support & Contact

**Questions about these guides?**
- Check the table of contents in each document
- Use the "When to use" sections
- Follow the quick decision trees
- See worked examples in "Real-World Scenarios"

**Need more detailed help?**
- Refer to main documentation: `/home/user/yawl/docs/README.md`
- Check architecture patterns: `/home/user/yawl/.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- Review performance details: `/home/user/yawl/.claude/reference/PERFORMANCE-OPTIMIZATIONS.md`

---

**Start with the Quick Decision Tree, then jump to the relevant section. Most questions answered within 1-2 pages.**

**Last updated**: February 28, 2026
**YAWL version**: v6.0.0
**Java target**: Java 25 (optimized)
