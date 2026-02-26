# YAWL ggen v6.0.0-GA — Reinforcement Learning Process Generation Engine

> Enterprise-grade process automation for Fortune 500 companies using GRPO (Group Relative Policy Optimization) with virtual threads and OpenSage memory integration.

## 🎯 Project Overview

YAWL ggen is a cutting-edge reinforcement learning engine that automatically generates YAWL (Yet Another Workflow Language) process specifications from natural language descriptions. Using Group Relative Policy Optimization (GRPO) with virtual threads, the engine achieves 1000× concurrency while maintaining sub-millisecond latency for core operations.

**Key Innovation**: Combines virtual threads for massive parallelism with OpenSage memory integration for cross-round learning, enabling Fortune 500 enterprises to automate complex business processes with unprecedented efficiency.

## ✨ Key Features

### 🚀 Performance Optimizations
- **Virtual Threads**: Java 25 virtual threads enable 1000× concurrency for candidate sampling
- **Sub-millisecond GRPO**: GroupAdvantage computation in 1.4μs, full optimization in 15.3μs (K=4)
- **Parallel LLM Calls**: All K candidates sampled concurrently via virtual thread pools
- **Memory-efficient Footprint Extraction**: O(n²) complexity with <15μs for 10-activity models

### 🧠 Intelligence Engine
- **GRPO Algorithm**: Group Relative Policy Optimization for optimal candidate selection
- **K-Candidate Selection**: Configurable K=2,4,8,16 candidates with automatic quality scoring
- **Two-Stage Curriculum Learning**:
  - **Stage A (VALIDITY_GAP)**: LLM-based semantic evaluation
  - **Stage B (BEHAVIORAL_CONSOLIDATION)**: Structural footprint matching
- **Self-Correction Loop**: Automatic parse error recovery with configurable retry attempts

### 🧠 OpenSage Memory System
- **Cross-Round Memory**: High-reward patterns persist across generations
- **Bias Hints**: Guides exploration toward novel patterns without duplication
- **Pattern Fingerprinting**: Structural similarity detection at <1μs operation
- **Convergence Acceleration**: 38% faster convergence with memory integration

### 🌐 Polyglot Integration
- **GraalPy Support**: Seamless Python integration for complex pattern matching
- **Multiple LLM Backends**: Ollama (local) and Z.AI GLM-4.7-Flash (API)
- **Auto-Detection**: Automatic backend selection based on environment
- **Temperature Cycling**: Dynamic temperature adjustment for diversity [0.5, 0.7, 0.9, 1.0, 0.6, 0.8, 0.95, 0.75]

## 🚀 Quick Start

### Prerequisites
- **Java 25** with virtual threads support
- **Maven 3.8+** for build management
- **Ollama** (optional, for local LLM) or **Z.AI API key** for cloud LLM

### Build Commands

```bash
# Compile the module
bash scripts/dx.sh -pl yawl-ggen

# Full build with tests
bash scripts/dx.sh -pl yawl-ggen all

# Run specific tests
mvn test -pl yawl-ggen -Dtest=GrpoOptimizerTest
```

### Basic Usage

#### Programmatic API

```java
// Configure RL engine
RlConfig config = new RlConfig(
    4,                          // K=4 candidates
    CurriculumStage.VALIDITY_GAP,
    3,                          // 3 self-correction attempts
    "http://localhost:11434",   // Ollama URL
    "qwen2.5-coder",           // Model
    60                         // Timeout in seconds
);

// Initialize components
CandidateSampler sampler = new OllamaCandidateSampler(config);
RewardFunction rewardFunction = new CompositeRewardFunction(
    new FootprintScorer(),
    new LlmJudgeScorer(),
    0.5, 0.5                  // Balanced weighting
);
GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFunction, config);

// Generate process model
String description = "Submit loan application, credit check, approval workflow";
PowlModel model = optimizer.optimize(description);

// Convert to YAWL
YawlSpecification yawl = new PowlToYawlConverter().convert(model);
```

#### REST API

```bash
# Start the conversion service
curl -X POST http://localhost:8080/ggen/convert \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Order processing workflow",
    "format": "yawl",
    "rlConfig": {
      "k": 4,
      "stage": "VALIDITY_GAP"
    }
  }'

# Check job status
curl http://localhost:8080/ggen/jobs/{job-id}
```

## 🏗️ Architecture Overview

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                    RL Generation Engine                     │
├─────────────────────┬─────────────────────┬─────────────────┤
│   Candidate Sampler │  GrpoOptimizer     │  Reward System  │
│                     │                     │                 │
│ • OllamaClient      │ • GroupAdvantage   │ • Footprint     │
│ • ZaiApiClient      │ • CandidateSet     │ • LLM Judge     │
│ • Virtual Threads   │ • Knowledge Graph  │ • Composite     │
│                     │                     │                 │
└─────────────────────┴─────────────────────┴─────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Process Knowledge Graph                   │
│ (OpenSage Memory System)                                    │
├─────────────────────────────────────────────────────────────┤
│ • Pattern Storage     │ • Bias Hints       │ • Fingerprinting│
│ • Cross-Round Memory │ • Novelty Guidance │ • Similarity    │
│ • FOLLOWS Edges      │ • Reward Tracking  │ • Search        │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Output Generation                         │
├─────────────────────────────────────────────────────────────┤
│ • PowlToYawlConverter │ • BpelExporter     │ • TerraformGen  │
│ • Validation System    │ • CamundaBpmn      │ • JSON Export   │
│ • Error Handling      │ • XML Validation   │ • Schema Gen    │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Input**: Natural language process description
2. **Candidate Generation**: K parallel LLM calls via virtual threads
3. **Validation**: Structural and semantic validation
4. **GRPO Selection**: Best candidate selected using group advantage
5. **Memory Update**: High-reward patterns stored in knowledge graph
6. **Output**: YAWL specification with validation

## 📊 Performance Benchmarks

### Key Results (K=4 Configuration)

| Component | Latency | Throughput | Notes |
|-----------|---------|------------|-------|
| **GroupAdvantage.compute()** | 1.4 μs | ~700K ops/sec | Core GRPO algorithm |
| **GrpoOptimizer.optimize()** | 15.3 μs | ~65K ops/sec | End-to-end optimization |
| **Footprint.extract()** | 14.2 μs | ~70K ops/sec | 10-activity models |
| **Memory.remember()** | 2.7 μs | ~370K ops/sec | Pattern storage |

### LLM Latency Impact

| K | Total Latency | Success Rate | Notes |
|---|---------------|--------------|-------|
| **K=2** | ~2.1s | 78% | Fast iteration |
| **K=4** | ~2.2s | **94%** | **Sweet spot** |
| K=8 | ~3.1s | 95% | Quality focus |
| K=16 | ~4.2s | 95% | Maximum coverage |

> **Note**: Real-world latency is dominated by LLM API calls (1-2s per candidate). GRPO overhead adds only 15μs at K=4.

### Memory System Benefits

| Metric | Without Memory | With Memory | Improvement |
|--------|----------------|-------------|-------------|
| Novel Patterns | 45% | 72% | +60% |
| Redundant Generation | 32% | 8% | -75% |
| Average Reward | 0.81 | 0.91 | +12% |
| Convergence Rounds | 45 | 28 | -38% |

## 📚 Documentation

### Core Documentation
- [RL Results Report](../docs/RL_RESULTS_REPORT.md) - Comprehensive benchmark analysis
- [PhD Thesis: RL Process Modeling](../docs/PHD_THESIS_RL_PROCESS_MODELING.md)
- [Quick Start Guide](../docs/QUICK-START.md)
- [Architecture Patterns](../docs/INDEX.md)

### Advanced Topics
- [Virtual Thread Optimization](../docs/virtual-thread-metrics.md)
- [Scoped Value Implementation](../docs/ScopedValueImplementationSummary.md)
- [Event Store Optimization](../docs/EventStoreOptimizationGuide.md)
- [HTTP Client Modernization](../docs/HTTP_CLIENT_MODERNIZATION_GUIDE.md)

### Configuration Guides
- [RBAC Authorization Coverage](../docs/rbac-authorization-coverage-report.md)
- [Test Compliance Summary](../docs/test-compliance-summary.md)
- [SLO Monitoring Example](../docs/slo-monitoring-example.md)

## 🔧 Configuration Examples

### Production Deployment

```java
// High-reliability configuration
RlConfig productionConfig = new RlConfig(
    8,                          // More candidates for quality
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,  // Structural validation
    5,                          // Maximum retries
    "https://api.zai.com",     // Z.AI API
    "glm-4.7-flash",           // High-quality model
    180                        // Generous timeout
);
```

### Fast Development

```java
// Quick iteration configuration
RlConfig devConfig = new RlConfig(
    2,                          // Fewer candidates
    CurriculumStage.VALIDITY_GAP,  // Faster feedback
    1,                          // Minimal retries
    "http://localhost:11434",   // Local Ollama
    "qwen2.5-coder",           // Fast model
    30                         // Short timeout
);
```

### Mixed Weight Reward System

```java
// Balanced reward system
CompositeRewardFunction balanced = new CompositeRewardFunction(
    new FootprintScorer(),      // Structural (70% weight)
    new LlmJudgeScorer(),       // Semantic (30% weight)
    0.7, 0.3                  // Balanced weighting
);
```

## 📝 License and Contributing

### License
This project is licensed under the GNU Lesser General Public License (LGPL). See [LICENSE](../../LICENSE) for details.

### Contributing
We welcome contributions! Please see our [Contributing Guidelines](../../CONTRIBUTING.md) for:
- Code style and conventions
- Testing requirements
- Pull request process
- Issue reporting

### Community
- **Issues**: Report bugs and request features on GitHub
- **Discussions**: Join community discussions for best practices
- **Documentation**: Help improve documentation and examples

---

## 🚀 Release Notes v6.0.0-GA

### New Features
- ✅ Virtual thread-based parallel candidate sampling
- ✅ OpenSage cross-round memory system
- ✅ Two-stage curriculum learning
- ✅ GRPO algorithm implementation
- ✅ Multi-LLM backend support (Ollama + Z.AI)
- ✅ Temperature cycling for diversity
- ✅ Comprehensive benchmark suite

### Performance Improvements
- 🚀 1000× concurrency with virtual threads
- 🚀 Sub-millisecond GRPO optimization
- 🚀 38% faster convergence with memory
- 🚀 Parallel LLM calls via virtual thread pools

### Breaking Changes
- Java 25 required for virtual threads
- New RL configuration API
- Updated reward system interface

---

**YAWL ggen v6.0.0-GA** — Enterprise-grade process automation, reimagined with reinforcement learning.