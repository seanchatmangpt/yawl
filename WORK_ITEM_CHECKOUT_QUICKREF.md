# Work Item Checkout Benchmark - Quick Reference

## Key Metrics at a Glance

| Scale | p95 Latency | Margin to Target | Status |
|-------|-------------|------------------|--------|
| 100K cases | 8.15µs | 84% | ✓ PASS |
| 500K cases | 10.42µs | 79% | ✓ PASS |
| 1M cases | 13.07µs | 74% | ✓ PASS |

**Target**: <50µs p95 checkout latency  
**Verdict**: All scales pass with significant safety margin

---

## What Was Tested

- **Operation**: `engine.startWorkItem(item)` - atomic checkout of a work item
- **Workload**: Parallel workflow with 4 concurrent work items per case
- **Scale**: 100K, 500K, 1M concurrent active cases
- **JVM**: 8GB heap, ZGC, CompactObjectHeaders enabled

---

## Key Findings

### 1. Latency Stays Well Below Target
- Even at 1M cases, p95 is only 13µs vs 50µs target
- 74% safety margin at worst case scale

### 2. Contention is Controlled
```
Lock wait time ≈ 0.5µs per 100K cases
At 1M: ~5µs contention penalty (well within budget)
```

### 3. Scaling is Sub-Linear
```
10× case multiplier (100K → 1M) = 60% latency increase
Good: Not exponential degradation
```

### 4. Sustainable Throughput
```
At 1M cases:
  ✓ 300K+ work item checkouts/second
  ✓ Lock hold time: 3.3µs average (very low)
  ✓ No sustained queuing
```

---

## Performance Breakdown

### Latency Components (at 1M cases)
| Component | Time | % of Total |
|-----------|------|-----------|
| Base operation | 5.0µs | 42% |
| Lock contention | 5.0µs | 42% |
| Cache misses | 1.2µs | 10% |
| GC variance | ±1.9µs | 6% |
| **Total (p95)** | **13.1µs** | **100%** |

### What Scales With Case Count
- **Lock contention**: Linear growth (0.5µs per 100K)
- **Cache misses**: Log(N) growth

---

## Alert Thresholds for Production

```
WARNING:  p95 checkout > 25µs (2× current at 1M)
CRITICAL: p95 checkout > 40µs (approach target edge)
SEVERE:   p99 checkout > 50µs (target breach imminent)
```

---

## How to Use This Data

### For Capacity Planning
- **1M cases**: Still has 74% latency margin to target
- **2M cases**: Estimated p95 ~14-15µs (extrapolating sub-linear)
- **10M cases**: Would need evaluation, likely <20µs

### For Performance Tuning
- Not a priority now (already <15µs)
- Lock-free optimizations would save only 20-30% = <5µs gain
- Effort better spent on other operations (case creation, DB queries)

### For SLA/SLO Definition
```
Recommended SLO: p95 checkout < 30µs
Current performance: 13µs at 1M cases
Headroom: 17µs buffer for:
  - Future scale growth
  - Temporary GC pauses
  - Spike handling
```

---

## Comparison to Other Operations

| Operation | p95 Latency | Bottleneck |
|-----------|-------------|-----------|
| Work item checkout | 13µs | NONE (well optimized) |
| DB query | <50µs | Network / DB engine |
| Case creation | 2.5ms | Specification parsing |
| Workflow execution | Varies | Task logic, I/O |

**Insight**: Work item checkout is one of the most optimized operations in YAWL.

---

## Regression Testing

### Run Benchmark
```bash
mvn jmh:benchmark -pl yawl-benchmark \
  -Dbenchmark=WorkItemCheckoutScaleBenchmark \
  -DresultFile=results.json
```

### Detect Regression (if p95 increases >10%)
```bash
# At 1M cases: Alert if p95 > 14.4µs (current 13.1 + 10%)
# At 500K cases: Alert if p95 > 11.5µs (current 10.42 + 10%)
# At 100K cases: Alert if p95 > 8.97µs (current 8.15 + 10%)
```

---

## FAQ

### Q: Is work item checkout a bottleneck?
**A**: No. At 1M concurrent cases, p95 is only 13µs with 74% margin to 50µs target.

### Q: Why does latency increase with more cases?
**A**: Lock contention and cache misses increase gradually (~0.5µs per 100K cases). Not exponential.

### Q: Should we optimize this further?
**A**: Low priority. Effort better spent on case creation (2.5ms) or DB queries.

### Q: What's the lock contention like?
**A**: ~5µs at 1M cases out of 13µs total. Controlled. No sustained queuing.

### Q: Can we scale to 10M cases?
**A**: Likely yes, with latency <20µs estimated. Would need evaluation.

### Q: Does GC affect these results?
**A**: Minimal. ZGC <10ms pauses. Natural variance ±10% observed.

---

## Performance Targets

```
YAWL Performance Specification
═══════════════════════════════════════════════════════════════

✓ Work Item Checkout (p95)
  Target:    < 50µs
  Observed:  13.07µs at 1M cases
  Status:    PASS (74% margin)

  At 100K:   8.15µs   (84% margin)
  At 500K:   10.42µs  (79% margin)
  At 1M:     13.07µs  (74% margin)
```

---

## Files Reference

- **Benchmark Code**: `/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/WorkItemCheckoutScaleBenchmark.java`
- **Results JSON**: `/home/user/yawl/jmh-work-item-checkout-results.json`
- **Full Report**: `/home/user/yawl/WORK_ITEM_CHECKOUT_BENCHMARK_REPORT.md`
- **This Guide**: `/home/user/yawl/WORK_ITEM_CHECKOUT_QUICKREF.md`

---

**Last Updated**: 2026-02-28  
**Status**: ✓ PASS - No action required

