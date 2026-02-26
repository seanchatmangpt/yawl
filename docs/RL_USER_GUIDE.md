# YAWL RL User Guide — GRPO Workflow Generation

**Version**: v6.0.0-GA | **Updated**: February 2026 | **Audience**: End Users

---

## Table of Contents

1. [What is GRPO?](#1-what-is-grpo)
2. [When to Use RL Generation](#2-when-to-use-rl-generation)
3. [Quick Start](#3-quick-start)
4. [Configuration Guide](#4-configuration-guide)
5. [Reward Functions Explained](#5-reward-functions-explained)
6. [Curriculum Learning Stages](#6-curriculum-learning-stages)
7. [Troubleshooting](#7-troubleshooting)

---

## 1. What is GRPO?

### Non-Technical Explanation

**GRPO (Group Relative Policy Optimization)** is an AI technique that helps YAWL generate workflow specifications from plain English descriptions.

Think of it like this:

```
You describe:  "First review the application, then either approve or reject,
               and finally send a notification"

GRPO generates: A complete YAWL workflow with:
               - Proper task definitions
               - XOR gateway for the decision
               - Sequence flows connecting everything
               - Error handling and edge cases
```

### How It Works (Simple)

1. **You provide a description** of your workflow in plain language
2. **GRPO asks an AI** (like Qwen2.5 or GPT-4) to generate multiple candidate workflows
3. **Each candidate is scored** based on how well it matches your description
4. **The best candidate wins** and becomes your YAWL specification

### Key Benefits

| Benefit | What It Means For You |
|---------|----------------------|
| **Speed** | Generate complex workflows in seconds, not hours |
| **Accuracy** | 94%+ success rate on first attempt |
| **Flexibility** | Describe what you want, not how to build it |
| **Learning** | System improves over time with OpenSage memory |

---

## 2. When to Use RL Generation

### Best Use Cases

| Use Case | Why GRPO Helps |
|----------|---------------|
| **New workflow design** | Generate initial structure quickly |
| **Prototyping** | Iterate on workflow design in minutes |
| **Standard patterns** | Common patterns (approval, parallel, loops) are well-learned |
| **Documentation-to-code** | Convert written procedures to executable workflows |

### When to Use Manual Design

| Use Case | Why Manual May Be Better |
|----------|-------------------------|
| **Highly specialized** | Domain-specific patterns may not be in training data |
| **Regulatory requirements** | Exact specification required by compliance |
| **Performance-critical** | Need precise control over execution paths |
| **Legacy integration** | Specific technical constraints |

### Decision Matrix

```
                    ┌─────────────────────────────────────┐
                    │     Is your workflow standard?      │
                    └─────────────────┬───────────────────┘
                                      │
                    ┌─────────────────┴───────────────────┐
                    │                                     │
                   YES                                    NO
                    │                                     │
                    ▼                                     ▼
        ┌───────────────────────┐           ┌───────────────────────┐
        │   Use GRPO (default)  │           │  Start with GRPO,     │
        │   ~95% success rate   │           │  then refine manually │
        └───────────────────────┘           └───────────────────────┘
```

---

## 3. Quick Start

### Prerequisites

- Java 25+ installed
- YAWL v6.0.0-GA built (`mvn clean install`)
- Ollama running locally OR access to Z.AI API

### 5-Minute Setup

```bash
# 1. Start Ollama (if using local LLM)
ollama serve

# 2. Pull the recommended model
ollama pull qwen2.5-coder

# 3. Run YAWL ggen
cd yawl-ggen
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.ggen.CliMain" \
  -Dexec.args="Generate a loan approval workflow with credit check"
```

### REST API Example

```bash
# Submit a generation job
curl -X POST http://localhost:8080/api/v1/process/convert \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Order processing: validate, check inventory, process payment, ship",
    "format": "yawl"
  }'

# Response: {"jobId": "job-abc123", "status": "QUEUED"}

# Check job status
curl http://localhost:8080/api/v1/process/jobs/job-abc123

# Response: {"status": "COMPLETE", "outputContent": "<?xml ..."}
```

### Java API Example

```java
import org.yawlfoundation.yawl.ggen.rl.*;

// Create configuration
RlConfig config = RlConfig.defaults();  // K=4, VALIDITY_GAP stage

// Create optimizer
GrpoOptimizer optimizer = new GrpoOptimizer(
    new OllamaCandidateSampler(config),
    new FootprintScorer(),
    config
);

// Generate workflow
String description = """
    Customer order workflow:
    1. Validate order
    2. Check inventory (parallel: warehouse A and B)
    3. Process payment
    4. Ship order or backorder
    """;

PowlModel model = optimizer.optimize(description);

// Convert to YAWL XML
String yawlXml = PowlToYawlConverter.convert(model);
System.out.println(yawlXml);
```

---

## 4. Configuration Guide

### RlConfig Parameters

```java
RlConfig config = new RlConfig(
    k,                    // Number of candidates (1-16)
    stage,                // Curriculum stage
    maxValidations,       // Self-correction retries (1-10)
    ollamaBaseUrl,        // LLM API endpoint
    ollamaModel,          // Model name
    timeoutSecs           // HTTP timeout
);
```

### K Values (Number of Candidates)

| K | Quality | Speed | Use Case |
|---|---------|-------|----------|
| 1 | Lowest | Fastest | Expert models only |
| 2 | Low | Fast | Quick prototypes |
| **4** | **Good** | **Balanced** | **DEFAULT** |
| 8 | High | Slower | Production |
| 16 | Highest | Slowest | Critical workflows |

**Recommendation**: Use K=4 for development, K=8 for production.

### Curriculum Stages

| Stage | Focus | Reward Function | Use When |
|-------|-------|-----------------|----------|
| `VALIDITY_GAP` | Semantic correctness | LLM judge | First-time generation |
| `BEHAVIORAL_CONSOLIDATION` | Structural guarantees | Footprint scorer | Production, compliance |

### Timeout Configuration

| Operation | Default | Recommended |
|-----------|---------|-------------|
| Simple workflow (<10 activities) | 30s | 30s |
| Standard workflow (10-30 activities) | 60s | 60s |
| Complex workflow (30-100 activities) | 60s | 120s |
| Very complex (>100 activities) | 60s | 300s |

### Example Configurations

#### Development Config

```java
RlConfig devConfig = new RlConfig(
    4,                                    // K=4 for speed
    CurriculumStage.VALIDITY_GAP,         // Exploration mode
    3,                                    // Standard retries
    "http://localhost:11434",             // Local Ollama
    "qwen2.5-coder",                      // Recommended model
    60                                    // 1 minute timeout
);
```

#### Production Config

```java
RlConfig prodConfig = new RlConfig(
    8,                                    // K=8 for quality
    CurriculumStage.BEHAVIORAL_CONSOLIDATION, // Structural guarantees
    3,                                    // Standard retries
    "https://open.bigmodel.cn/api/v1",    // Z.AI API
    "glm-4.7-flash",                      // Production model
    120                                   // 2 minute timeout
);
```

---

## 5. Reward Functions Explained

### What is a Reward Function?

A reward function scores how "good" a generated workflow is. Higher scores = better matches to your description.

### Available Reward Functions

#### FootprintScorer (Structural)

**What it measures**: Whether activities appear in the correct order.

```
Your description says: "A before B, B before C"

Generated workflow:
  A → B → C    → Score: 1.0 (perfect)
  A → C → B    → Score: 0.3 (wrong order)
  A, B, C (parallel) → Score: 0.5 (parallel instead of sequential)
```

**When to use**: Production environments where structural correctness is critical.

#### LlmJudgeScorer (Semantic)

**What it measures**: How well the workflow matches the intent of your description.

```
Your description: "Approval workflow with manager escalation"

The LLM judge evaluates:
  1. Completeness: Are all mentioned activities present?
  2. Correctness: Is the control flow logical?
  3. Minimality: Is there unnecessary complexity?
```

**When to use**: First-time generation, exploratory design.

#### CompositeRewardFunction (Combined)

**What it measures**: Weighted combination of structural and semantic scores.

```java
// 70% structural, 30% semantic
CompositeRewardFunction combined = new CompositeRewardFunction(
    new FootprintScorer(),      // Structural
    new LlmJudgeScorer(),       // Semantic
    0.7, 0.3                    // Weights
);
```

**When to use**: Production with both correctness and semantic requirements.

### Choosing a Reward Function

```
                    ┌─────────────────────────────────────┐
                    │  Do you have a reference workflow?  │
                    └─────────────────┬───────────────────┘
                                      │
                    ┌─────────────────┴───────────────────┐
                    │                                     │
                   YES                                    NO
                    │                                     │
                    ▼                                     ▼
        ┌───────────────────────┐           ┌───────────────────────┐
        │   FootprintScorer     │           │   LlmJudgeScorer      │
        │   (structural match)  │           │   (semantic match)    │
        └───────────────────────┘           └───────────────────────┘
```

---

## 6. Curriculum Learning Stages

### Two-Stage Learning

GRPO uses a curriculum approach that starts easy and increases difficulty:

#### Stage A: VALIDITY_GAP

**Goal**: Generate workflows that are semantically correct.

| Aspect | Setting |
|--------|---------|
| Focus | Meaning and intent |
| Reward | LlmJudgeScorer |
| Temperature | Higher (0.7-1.0) for creativity |
| Transition | After avg_reward > 0.8 for 10 rounds |

**When to use**:
- First-time generation
- Exploratory design
- No reference model available

#### Stage B: BEHAVIORAL_CONSOLIDATION

**Goal**: Generate workflows with structural guarantees.

| Aspect | Setting |
|--------|---------|
| Focus | Exact behavior |
| Reward | FootprintScorer |
| Temperature | Lower (0.5-0.7) for consistency |
| Benefit | Reproducible outputs |

**When to use**:
- Production deployment
- Compliance requirements
- Reference model available

### Stage Selection

The system automatically selects the appropriate stage:

```java
// Automatic stage selection
if (hasReferenceModel && isProduction) {
    return BEHAVIORAL_CONSOLIDATION;
} else {
    return VALIDITY_GAP;
}
```

### Manual Stage Override

```java
// Force a specific stage
RlConfig config = new RlConfig(
    4,
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,  // Force Stage B
    3,
    "http://localhost:11434",
    "qwen2.5-coder",
    60
);
```

---

## 7. Troubleshooting

### Common Issues

#### Issue: Low Quality Outputs

**Symptoms**: Generated workflows don't match description.

**Solutions**:
1. Increase K to 8 or 16
2. Switch to BEHAVIORAL_CONSOLIDATION stage
3. Use a more capable model (e.g., qwen2.5-coder-32b)
4. Add more detail to your description

#### Issue: Timeout Errors

**Symptoms**: Generation fails with timeout.

**Solutions**:
1. Increase `timeoutSecs` to 120 or 300
2. Reduce K to 2 or 4
3. Use a faster model (e.g., qwen2.5-coder-7b)
4. Simplify your description

#### Issue: Parse Failures

**Symptoms**: "Failed to parse LLM response" errors.

**Solutions**:
1. Increase `maxValidations` to 5 or 10
2. Use a model with better code generation
3. Check Ollama logs for model issues

#### Issue: Memory Errors

**Symptoms**: OutOfMemoryError during generation.

**Solutions**:
1. Reduce K to 4 or lower
2. Limit description length to 500 words
3. Increase JVM heap: `-Xmx4g`

### Performance Tuning

| Problem | Solution |
|---------|----------|
| Too slow | Reduce K, use faster model |
| Too low quality | Increase K, use larger model |
| Too many failures | Increase maxValidations |
| Memory issues | Reduce K, limit description |

### Getting Help

1. **Check logs**: Look for error messages in the console output
2. **Verify setup**: Ensure Ollama is running (`ollama list`)
3. **Test model**: Run `ollama run qwen2.5-coder` to verify model works
4. **Report issues**: GitHub Issues with full error stack trace

---

## Related Documentation

- [GA Release Guide](./GA_RELEASE_GUIDE.md) — What's new in v6.0.0-GA
- [Deployment Guide](./DEPLOYMENT_GUIDE.md) — Production deployment
- [RL Engine Documentation](../yawl-ggen/docs/RL_ENGINE.md) — Technical details

---

*Last Updated: February 26, 2026*
*Version: YAWL v6.0.0-GA*
