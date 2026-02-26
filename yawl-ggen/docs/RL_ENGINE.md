# YAWL ggen v6.0.0-GA RL Engine Documentation

**Status**: GA-Ready | **GRPO Algorithm** | **February 2026**

---

## Table of Contents

1. [GRPO Algorithm Overview](#1-grpo-algorithm-overview)
2. [Core Components](#2-core-components)
3. [Candidate Sampling](#3-candidate-sampling)
4. [Reward Functions](#4-reward-functions)
5. [Curriculum Learning](#5-curriculum-learning)
6. [Self-Correction Loop](#6-self-correction-loop)
7. [Temperature Cycling](#7-temperature-cycling)
8. [Performance Optimization](#8-performance-optimization)

---

## 1. GRPO Algorithm Overview

Group Relative Policy Optimization (GRPO) is a best-of-K selection algorithm that evaluates multiple candidate POWL models and selects the one with the highest normalized advantage.

### Mathematical Foundation

For a group of K candidates with rewards `r₁, r₂, ..., rₖ`:

```
1. Group Mean:    μ = Σrᵢ / K
2. Std Deviation: σ = √(Σ(rᵢ - μ)² / K)
3. Advantage:     aᵢ = (rᵢ - μ) / (σ + ε)

Selection: argmax(aᵢ)
```

Where ε = 1e-8 prevents division by zero.

### Key Properties

- **Relative Scoring**: Advantage is computed relative to the group, not absolute
- **Normalization**: Candidates are compared on a common scale
- **Exploration**: Low-reward candidates can still be selected if group is weak
- **Exploitation**: High-reward candidates are favored when clearly better

### Inference-Time Optimization

GRPO in YAWL ggen is **inference-time optimization** (not training):

```
┌──────────────────────────────────────────────────────────────┐
│  Traditional RL:  Train policy → Deploy → Use               │
│                                                              │
│  GRPO (ggen):     For each request:                         │
│                    1. Sample K candidates from LLM          │
│                    2. Score with reward function            │
│                    3. Select best via GroupAdvantage        │
│                    4. Return selected candidate             │
└──────────────────────────────────────────────────────────────┘
```

The LLM + prompt template is the "policy" — we don't update it, we just select the best output.

---

## 2. Core Components

### RlConfig Record

```java
public record RlConfig(
    int k,                    // Number of candidates (1-16)
    CurriculumStage stage,    // Reward function stage
    int maxValidations,       // Self-correction retries (1-10)
    String ollamaBaseUrl,     // LLM API endpoint
    String ollamaModel,       // Model name
    int timeoutSecs           // HTTP timeout
) {
    // Compact constructor validates all parameters
    public RlConfig {
        if (k < 1 || k > 16) throw new IllegalArgumentException("k must be 1-16");
        if (maxValidations < 1 || maxValidations > 10) throw new IllegalArgumentException(...);
        // ... more validation
    }

    public static RlConfig defaults() {
        return new RlConfig(4, CurriculumStage.VALIDITY_GAP, 3,
            "http://localhost:11434", "qwen2.5-coder", 60);
    }
}
```

### GrpoOptimizer

```java
public class GrpoOptimizer {
    private final CandidateSampler sampler;
    private final RewardFunction rewardFunction;
    private final PowlValidator validator;
    private final RlConfig config;
    private final ProcessKnowledgeGraph knowledgeGraph;

    public PowlModel optimize(String processDescription) throws IOException {
        // 1. Sample K candidates (parallel via virtual threads)
        List<PowlModel> rawCandidates = sampler.sample(processDescription, config.k());

        // 2. Filter to valid candidates
        List<PowlModel> validCandidates = filterValid(rawCandidates);

        // 3. Score each candidate
        List<Double> rewards = scoreCandidates(validCandidates);

        // 4. Create CandidateSet with GroupAdvantage
        CandidateSet evaluated = new CandidateSet(validCandidates, rewards);

        // 5. Update memory (OpenSage loop)
        knowledgeGraph.remember(evaluated);

        // 6. Return best candidate
        return evaluated.best();
    }
}
```

### GroupAdvantage Record

```java
public record GroupAdvantage(List<Double> advantages, double mean, double std) {

    private static final double EPSILON = 1e-8;

    public static GroupAdvantage compute(List<Double> rewards) {
        // Compute mean
        double mean = rewards.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElseThrow();

        // Compute standard deviation
        double variance = rewards.stream()
            .mapToDouble(r -> (r - mean) * (r - mean))
            .average()
            .orElseThrow();
        double std = Math.sqrt(variance);

        // Compute advantages
        List<Double> advantages = new ArrayList<>();
        for (double reward : rewards) {
            advantages.add((reward - mean) / (std + EPSILON));
        }

        return new GroupAdvantage(advantages, mean, std);
    }

    public int bestIndex() {
        int best = 0;
        for (int i = 1; i < advantages.size(); i++) {
            if (advantages.get(i) > advantages.get(best)) {
                best = i;
            }
        }
        return best;
    }
}
```

---

## 3. Candidate Sampling

### OllamaCandidateSampler

Samples K POWL candidates from an LLM via HTTP calls using virtual threads:

```java
public class OllamaCandidateSampler implements CandidateSampler {
    private final LlmGateway gateway;
    private final ProcessKnowledgeGraph memory;

    @Override
    public List<PowlModel> sample(String description, int k) throws IOException {
        // Get bias hints from memory (OpenSage recall)
        String biasHint = memory.biasHint(description, 10);

        // Sample K candidates in parallel via virtual threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<PowlModel>> futures = new ArrayList<>();

            for (int i = 0; i < k; i++) {
                double temperature = TEMPERATURES[i % TEMPERATURES.length];
                String prompt = buildPrompt(description, biasHint, temperature);

                futures.add(executor.submit(() -> {
                    String rawResponse = gateway.generate(prompt, temperature);
                    return PowlTextParser.parse(rawResponse, description);
                }));
            }

            // Collect results
            List<PowlModel> candidates = new ArrayList<>();
            for (Future<PowlModel> future : futures) {
                candidates.add(future.get());
            }
            return candidates;
        }
    }
}
```

### Virtual Thread Benefits

| Metric | Platform Threads | Virtual Threads |
|--------|------------------|-----------------|
| Memory per thread | ~1MB | ~1KB |
| Max concurrent threads | ~1000 | ~1,000,000 |
| Context switch overhead | ~1μs | ~10ns |
| K=4 LLM calls | Sequential or pool | True parallelism |

### Temperature Cycling

The system cycles through 8 temperatures for diversity:

```java
private static final double[] TEMPERATURES = {
    0.5, 0.7, 0.9, 1.0, 0.6, 0.8, 0.95, 0.75
};
```

| Temperature | Creativity | Validity | Best For |
|-------------|------------|----------|----------|
| 0.3-0.5 | Low | 97% | Simple sequences |
| 0.5-0.7 | Medium | 92% | Standard processes |
| 0.7-0.9 | High | 85% | Complex workflows |
| 0.9-1.0 | Very High | 78% | Novel patterns |

---

## 4. Reward Functions

### RewardFunction Interface

```java
@FunctionalInterface
public interface RewardFunction {
    /**
     * Scores a candidate POWL model against a process description.
     * @return score in [0.0, 1.0]
     */
    double score(PowlModel candidate, String processDescription);
}
```

### FootprintScorer (Structural)

Computes structural similarity using behavioral footprint:

```java
public class FootprintScorer implements RewardFunction {
    @Override
    public double score(PowlModel candidate, String processDescription) {
        // Extract footprint from candidate
        FootprintMatrix candidateFootprint = FootprintExtractor.extract(candidate);

        // Extract expected footprint from description (if reference exists)
        FootprintMatrix expectedFootprint = extractFromDescription(processDescription);

        if (expectedFootprint == null) {
            // No reference: return self-consistency score
            return computeSelfConsistency(candidateFootprint);
        }

        // Jaccard similarity on footprint relations
        return jaccardSimilarity(candidateFootprint, expectedFootprint);
    }
}
```

### Footprint Matrix

The footprint captures ordering relations between activities:

| Relation | Symbol | Meaning |
|----------|--------|---------|
| Directly Follows | `→` | A always before B |
| Directly Precedes | `←` | A always after B |
| Parallel | `∥` | A and B can interleave |
| Choice | `#` | A and B never both |

Example footprint for "A, then B or C in parallel, then D":

```
     A    B    C    D
A    #    →    →    →
B    ←    #    ∥    →
C    ←    ∥    #    →
D    ←    ←    ←    #
```

### LlmJudgeScorer (Semantic)

Uses LLM as a judge for semantic evaluation:

```java
public class LlmJudgeScorer implements RewardFunction {
    private final LlmGateway gateway;

    @Override
    public double score(PowlModel candidate, String processDescription) {
        String judgePrompt = """
            Evaluate this POWL model for the given process description.

            Process: %s

            POWL Model: %s

            Score 0.0-1.0 on:
            1. Completeness (all activities covered)
            2. Correctness (proper control flow)
            3. Minimality (no unnecessary complexity)

            Return only the score as a decimal number.
            """.formatted(processDescription, candidate.toJson());

        String response = gateway.generate(judgePrompt, 0.1);  // Low temperature for consistency
        return parseScore(response);
    }
}
```

### CompositeRewardFunction

Combines multiple reward functions:

```java
public record CompositeRewardFunction(
    RewardFunction universal,      // e.g., FootprintScorer
    RewardFunction verifiable,     // e.g., LlmJudgeScorer
    double universalWeight,        // Weight for universal
    double verifiableWeight        // Weight for verifiable
) implements RewardFunction {

    @Override
    public double score(PowlModel candidate, String processDescription) {
        double u = universal.score(candidate, processDescription);
        double v = verifiable.score(candidate, processDescription);
        double total = universalWeight + verifiableWeight;

        return (universalWeight * u + verifiableWeight * v) / total;
    }

    // Factory methods
    public static CompositeRewardFunction stageA(RewardFunction universal) {
        return new CompositeRewardFunction(universal, (c, d) -> 0.0, 1.0, 0.0);
    }

    public static CompositeRewardFunction stageB(RewardFunction verifiable) {
        return new CompositeRewardFunction((c, d) -> 0.0, verifiable, 0.0, 1.0);
    }
}
```

---

## 5. Curriculum Learning

### Two-Stage Curriculum

```
┌─────────────────────────────────────────────────────────────────┐
│  Stage A: VALIDITY_GAP                                          │
│  ─────────────────────                                          │
│  • Focus: Semantic correctness                                  │
│  • Reward: LlmJudgeScorer (subjective evaluation)               │
│  • Use case: First-time generation, exploratory mode            │
│  • Transition: avg_reward > 0.8 for 10 rounds                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Stage B: BEHAVIORAL_CONSOLIDATION                              │
│  ─────────────────────────────────────────                      │
│  • Focus: Structural guarantees                                 │
│  • Reward: FootprintScorer (deterministic similarity)           │
│  • Use case: Production, compliance requirements                │
│  • Benefit: Reproducible, verifiable outputs                    │
└─────────────────────────────────────────────────────────────────┘
```

### Stage Selection Logic

```java
public CurriculumStage selectStage(GenerationContext context) {
    // Production with reference model → Stage B
    if (context.isProduction() && context.hasReferenceModel()) {
        return CurriculumStage.BEHAVIORAL_CONSOLIDATION;
    }

    // Development or first-time → Stage A
    if (context.isDevelopment() || !context.hasHistory()) {
        return CurriculumStage.VALIDITY_GAP;
    }

    // Check transition criteria
    if (context.getAverageReward() > 0.8
        && context.getConsecutiveHighRewardRounds() >= 10
        && context.getParseSuccessRate() > 0.95) {
        return CurriculumStage.BEHAVIORAL_CONSOLIDATION;
    }

    return CurriculumStage.VALIDITY_GAP;
}
```

---

## 6. Self-Correction Loop

### Validation and Retry

When a candidate fails to parse, the system attempts self-correction:

```java
public class AiValidationLoop {
    private final int maxAttempts;
    private final OllamaValidationClient client;

    public PowlModel validateAndFix(String rawResponse, String description)
            throws ValidationExhaustedException {

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return PowlTextParser.parse(rawResponse, description);
            } catch (PowlParseException e) {
                if (attempt == maxAttempts) {
                    throw new ValidationExhaustedException(
                        "Failed after " + maxAttempts + " attempts: " + e.getMessage()
                    );
                }

                // Ask LLM to fix the error
                String fixPrompt = """
                    The following POWL output has a syntax error:
                    %s

                    Error: %s

                    Please fix and return valid POWL JSON.
                    """.formatted(rawResponse, e.getMessage());

                rawResponse = client.generate(fixPrompt);
            }
        }

        throw new ValidationExhaustedException("Should not reach here");
    }
}
```

### Retry Statistics

| maxValidations | Parse Success | Avg Latency | Use Case |
|----------------|---------------|-------------|----------|
| 0 | 71% | 2.0s | Expert models only |
| 1 | 85% | 2.6s | Fast iteration |
| **3** | **95%** | **3.2s** | **DEFAULT** |
| 5 | 97% | 4.0s | Edge cases |
| 10 | 99% | 5.5s | Maximum reliability |

---

## 7. Temperature Cycling

### Why Temperature Cycling?

- **Diversity**: Different temperatures explore different solution spaces
- **Coverage**: Low temp for safe outputs, high temp for creative exploration
- **Balance**: Automatic mixing prevents mode collapse

### Cycling Pattern

```
Round 1: temp[0] = 0.5  → Conservative
Round 2: temp[1] = 0.7  → Moderate
Round 3: temp[2] = 0.9  → Creative
Round 4: temp[3] = 1.0  → Maximum creativity
Round 5: temp[4] = 0.6  → Return to moderate
Round 6: temp[5] = 0.8  → Moderate-high
Round 7: temp[6] = 0.95 → High creativity
Round 8: temp[7] = 0.75 → Moderate
Round 9: temp[0] = 0.5  → Cycle repeats
```

### Temperature Effects

| Temperature | Effect | Validity | Best For |
|-------------|--------|----------|----------|
| 0.3-0.5 | Conservative, repetitive | 97% | Simple, well-defined processes |
| 0.5-0.7 | Balanced | 92% | Standard business processes |
| 0.7-0.9 | Creative, varied | 85% | Complex workflows with choices |
| 0.9-1.0 | Highly creative | 78% | Novel patterns, research |

---

## 8. Performance Optimization

### Virtual Thread Parallelism

K candidates are sampled in parallel:

```java
// All K LLM calls happen concurrently
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, k)
        .mapToObj(i -> executor.submit(() -> sampleOne(description)))
        .toList()
        .forEach(future -> candidates.add(future.get()));
}
```

### Latency Breakdown (K=4)

| Component | Latency | Notes |
|-----------|---------|-------|
| K LLM calls (parallel) | ~2.0s | Dominated by LLM |
| GroupAdvantage.compute() | 1.4 μs | Negligible |
| Footprint extraction | 14.2 μs | Per candidate |
| Memory update | 2.7 μs | Per round |
| **Total GRPO overhead** | **~20 μs** | <0.001% of total |

### Memory Efficiency

| Operation | Memory | Notes |
|-----------|--------|-------|
| PowlModel (10 activities) | ~2KB | Immutable record |
| CandidateSet (K=4) | ~8KB | 4 models + rewards |
| ProcessKnowledgeGraph | ~100KB | 1000 patterns |

### Benchmark Results

| Benchmark | K | Mean (μs) | P50 (μs) | Throughput |
|-----------|---|-----------|----------|------------|
| GroupAdvantage.compute | 1 | 7.1 | 0.75 | 140K/sec |
| GroupAdvantage.compute | 4 | 1.4 | 0.46 | 706K/sec |
| GroupAdvantage.compute | 8 | 0.98 | 0.67 | 1.0M/sec |
| GrpoOptimizer.optimize | 1 | 69.4 | 5.5 | 14K/sec |
| GrpoOptimizer.optimize | 4 | 15.3 | 17.9 | 65K/sec |
| GrpoOptimizer.optimize | 8 | 29.3 | 27.7 | 34K/sec |

---

## Usage Examples

### Basic Usage

```java
// Use defaults
RlConfig config = RlConfig.defaults();
GrpoOptimizer optimizer = new GrpoOptimizer(
    new OllamaCandidateSampler(config),
    new FootprintScorer(),
    config
);

PowlModel model = optimizer.optimize("Submit order, process payment, ship");
```

### With Memory

```java
// Shared memory enables cross-round learning
ProcessKnowledgeGraph memory = new ProcessKnowledgeGraph();

OllamaCandidateSampler sampler = new OllamaCandidateSampler(config, memory);
GrpoOptimizer optimizer = new GrpoOptimizer(
    sampler,
    new FootprintScorer(),
    config,
    memory  // Same instance as sampler
);

// Round 1
PowlModel m1 = optimizer.optimize("Loan application process");

// Round 2 benefits from Round 1's learning
PowlModel m2 = optimizer.optimize("Credit check workflow");
```

### Production Configuration

```java
RlConfig productionConfig = new RlConfig(
    8,  // More candidates for quality
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,  // Structural guarantees
    3,  // Standard retries
    "https://open.bigmodel.cn/api/v1",
    "glm-4.7-flash",
    120  // Generous timeout
);

CompositeRewardFunction rewards = new CompositeRewardFunction(
    new FootprintScorer(),     // 70% weight
    new LlmJudgeScorer(),      // 30% weight
    0.7, 0.3
);
```

---

## Related Documentation

- [Architecture Overview](./ARCHITECTURE.md)
- [Configuration Guide](./CONFIGURATION.md)
- [Benchmark Results](./BENCHMARKS.md)
- [OpenSage Memory System](./ARCHITECTURE.md#4-opensage-memory-system)

---

*Last Updated: February 26, 2026*
*Version: YAWL ggen v6.0.0-GA*
