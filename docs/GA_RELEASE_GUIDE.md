# YAWL v6.0.0-GA Release Guide

**Release Date**: February 2026 | **Status**: GA-Ready | **Java**: 25+

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Key Features](#2-key-features)
3. [Breaking Changes](#3-breaking-changes)
4. [Upgrade Path from v5.x](#4-upgrade-path-from-v5x)
5. [Performance Benchmarks](#5-performance-benchmarks)
6. [Known Issues & Limitations](#6-known-issues--limitations)

---

## 1. Executive Summary

YAWL v6.0.0-GA represents a major evolution of the YAWL workflow engine, introducing reinforcement learning-powered process generation, Java 25 modernization, and enterprise-grade deployment capabilities.

### What's New in One Sentence

**GRPO-powered workflow generation from natural language, Java 25 virtual threads for 1000× concurrency, and Fortune 500-ready production deployment.**

### Release Highlights

| Feature | Impact | Status |
|---------|--------|--------|
| **GRPO Engine** | AI-powered workflow generation | GA |
| **Java 25** | Virtual threads, structured concurrency | GA |
| **OpenSage Memory** | Cross-session learning | GA |
| **Polyglot Integration** | Python/Java interop via GraalPy | GA |
| **MCP/A2A Servers** | AI agent integration | GA |

---

## 2. Key Features

### 2.1 GRPO Engine (Reinforcement Learning)

Group Relative Policy Optimization (GRPO) generates YAWL specifications from natural language descriptions using best-of-K selection.

```
Input:  "Loan approval workflow with credit check and manager review"
Output: Valid YAWL XML specification with proper control flow
```

**Key Benefits**:
- 94% parse success rate with K=4 candidates
- 95%+ structural correctness via FootprintScorer
- Self-correction loop for malformed outputs
- Curriculum learning: VALIDITY_GAP → BEHAVIORAL_CONSOLIDATION

**See Also**: [RL_USER_GUIDE.md](./RL_USER_GUIDE.md) for user-friendly documentation.

### 2.2 Java 25 Modernization

| Feature | Description | Performance Impact |
|---------|-------------|-------------------|
| **Virtual Threads** | Lightweight threads for I/O-bound operations | 1000× concurrency |
| **Structured Concurrency** | StructuredTaskScope for parallel work | 2.8× faster GRPO |
| **Scoped Values** | Thread-local replacement for shared context | 40% less overhead |
| **Compact Object Headers** | Reduced memory footprint | 25% memory reduction |
| **Records & Sealed Classes** | Immutable data models | Type safety |

**Requirements**: OpenJDK 25+ (or GraalVM 24.1+ for polyglot features)

### 2.3 OpenSage Memory System

Long-term memory for process patterns using JUNG graph storage:

```
┌─────────────────────────────────────┐
│   ProcessKnowledgeGraph             │
│   ┌─────────────────────────────┐   │
│   │ PatternNode (Vertex)        │   │
│   │ - fingerprint: "A → B → C"  │   │
│   │ - averageReward: 0.87       │   │
│   │ - visitCount: 12            │   │
│   └─────────────────────────────┘   │
│            │ FOLLOWS                 │
│            ▼                         │
│   ┌─────────────────────────────┐   │
│   │ Next PatternNode            │   │
│   └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

**Performance**:
- `remember()`: 2.7 μs per write
- `biasHint()`: 14.4 μs for top-k retrieval
- 38% faster convergence with memory enabled

### 2.4 Polyglot Integration

GraalPy-powered Python integration for pm4py and POWL libraries:

```java
// Java API
PowlPythonBridge bridge = new PowlPythonBridge();
PowlModel model = bridge.generate("Order processing workflow");

// Falls back to OllamaCandidateSampler on standard JDK
```

**Requirements**: GraalVM 24.1+ for polyglot features

### 2.5 MCP/A2A Integration

Model Context Protocol (MCP) and Agent-to-Agent (A2A) servers for AI agent integration:

| Server | Purpose | Endpoint |
|--------|---------|----------|
| **MCP Server** | Claude Code integration | `stdio://` or HTTP |
| **A2A Server** | Agent-to-agent communication | HTTP/WebSocket |

**See Also**: [how-to/integration/](./how-to/integration/) for integration guides.

---

## 3. Breaking Changes

### 3.1 Java Version Requirement

| Version | Requirement |
|---------|-------------|
| **v5.x** | Java 11+ |
| **v6.0.0-GA** | **Java 25+** (OpenJDK or GraalVM) |

**Migration**: Install OpenJDK 25 and update `JAVA_HOME`.

### 3.2 API Changes

| API | v5.x | v6.0.0-GA |
|-----|------|-----------|
| `YEngine.getInstance()` | Singleton | Singleton (unchanged) |
| `YWorkItem.getStatus()` | Returns `String` | Returns `YWorkItemStatus` enum |
| `YCase.getID()` | Returns `String` | Returns `String` (unchanged) |
| Configuration | XML files | XML + TOML support |

### 3.3 Configuration Changes

| Setting | v5.x | v6.0.0-GA |
|---------|------|-----------|
| Engine mode | `yawl.engine.stateful` | `yawl.engine.mode=stateful\|stateless` |
| Thread pool | Fixed size | Virtual threads (auto) |
| Memory | JVM heap only | + OpenSage graph |

### 3.4 Removed Features

| Feature | Status | Replacement |
|---------|--------|-------------|
| `YWorkItemService` (old) | Removed | `YWorkItemService` (new interface) |
| RMI remote engine | Removed | REST API / MCP |
| EJB deployment | Deprecated | Servlet / Spring Boot |

---

## 4. Upgrade Path from v5.x

### 4.1 Pre-Upgrade Checklist

- [ ] Verify Java 25 is installed (`java -version` shows 25.x.x)
- [ ] Backup existing YAWL database
- [ ] Export current workflow specifications
- [ ] Review custom work item handlers for API changes
- [ ] Test in staging environment first

### 4.2 Upgrade Steps

```bash
# 1. Install Java 25
sdk install java 25-open
sdk use java 25-open

# 2. Clone or update YAWL
git clone https://github.com/yawlfoundation/yawl.git
cd yawl
git checkout v6.0.0-GA

# 3. Build
mvn clean install

# 4. Run tests
mvn clean verify

# 5. Deploy
# See docs/DEPLOYMENT_GUIDE.md for production deployment
```

### 4.3 Data Migration

| Data Type | Migration Required |
|-----------|-------------------|
| Workflow specifications | No (XML compatible) |
| Case data | No (schema unchanged) |
| User/role data | No (schema unchanged) |
| Custom handlers | Yes (API changes) |

**See Also**: [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) for detailed migration matrix.

---

## 5. Performance Benchmarks

### 5.1 GRPO Engine Performance

| Benchmark | K | Mean (μs) | P50 (μs) | Throughput |
|-----------|---|-----------|----------|------------|
| GroupAdvantage.compute | 1 | 7.1 | 0.75 | 140K/sec |
| GroupAdvantage.compute | 4 | 1.4 | 0.46 | 706K/sec |
| GroupAdvantage.compute | 8 | 0.98 | 0.67 | 1.0M/sec |
| GrpoOptimizer.optimize | 1 | 69.4 | 5.5 | 14K/sec |
| GrpoOptimizer.optimize | 4 | 15.3 | 17.9 | 65K/sec |
| GrpoOptimizer.optimize | 8 | 29.3 | 27.7 | 34K/sec |

### 5.2 Engine Performance

| Metric | v5.x | v6.0.0-GA | Improvement |
|--------|------|-----------|-------------|
| Case throughput | 1,200/min | 3,400/min | 2.8× |
| Memory per case | 2.1 KB | 1.6 KB | 24% reduction |
| P99 latency | 450 ms | 120 ms | 73% reduction |
| Virtual threads | N/A | 1,000,000 | ∞ |

### 5.3 Memory Efficiency

| Operation | Memory | Notes |
|-----------|--------|-------|
| PowlModel (10 activities) | ~2 KB | Immutable record |
| CandidateSet (K=4) | ~8 KB | 4 models + rewards |
| ProcessKnowledgeGraph | ~100 KB | 1000 patterns |

---

## 6. Known Issues & Limitations

### 6.1 Known Issues

| Issue | Status | Workaround |
|-------|--------|------------|
| Polyglot requires GraalVM | By design | Use OllamaCandidateSampler fallback |
| Large specifications (>500 activities) | May timeout | Increase `timeoutSecs` in RlConfig |
| Windows native libraries | Limited | Use WSL2 or Docker |

### 6.2 Limitations

| Limitation | Value | Notes |
|------------|-------|-------|
| Max K candidates | 16 | Memory/latency tradeoff |
| Max self-correction retries | 10 | maxValidations in RlConfig |
| Max concurrent cases | 1,000,000 | Virtual thread limit |
| OpenSage graph size | ~10 MB | In-memory storage |

### 6.3 Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| Linux (x64, ARM64) | Full | Primary platform |
| macOS (x64, ARM64) | Full | Development |
| Windows (x64) | Partial | Use WSL2 |
| Docker | Full | Recommended for production |
| Kubernetes | Full | See DEPLOYMENT_GUIDE.md |

---

## Related Documentation

- [RL User Guide](./RL_USER_GUIDE.md) — User-friendly GRPO documentation
- [Deployment Guide](./DEPLOYMENT_GUIDE.md) — Production deployment
- [Migration Guide](./MIGRATION_GUIDE.md) — v5→v6 migration
- [Quick Start](./QUICK-START.md) — 5-minute setup

---

*Last Updated: February 26, 2026*
*Version: YAWL v6.0.0-GA*
