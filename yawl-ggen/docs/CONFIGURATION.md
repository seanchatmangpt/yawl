# Configuration Guide for yawl-ggen v6.0.0-GA

This guide covers all configuration options for the YAWL Generation Engine, including RL parameters, environment variables, JVM options, Maven profiles, and performance tuning recommendations.

---

## Table of Contents

1. [RlConfig Parameters](#1-rlconfig-parameters)
2. [Environment Variables](#2-environment-variables)
3. [JVM Options](#3-jvm-options)
4. [Maven Profiles](#4-maven-profiles)
5. [Configuration Examples](#5-configuration-examples)
6. [Performance Tuning Guide](#6-performance-tuning-guide)
7. [Advanced Configuration](#7-advanced-configuration)

---

## 1. RlConfig Parameters

The `RlConfig` record controls the Group Relative Policy Optimization (GRPO) RL generation engine.

### Parameter Overview

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| **k** | 1-16 | 4 | Number of candidate POWL models to sample |
| **stage** | VALIDITY_GAP<br>BEHAVIORAL_CONSOLIDATION | VALIDITY_GAP | Curriculum stage for reward function |
| **maxValidations** | 1-10 | 3 | Self-correction retries for parse errors |
| **ollamaBaseUrl** | URL | http://localhost:11434 | Ollama API endpoint |
| **ollamaModel** | String | qwen2.5-coder | Ollama model name |
| **timeoutSecs** | 30-300 | 60 | HTTP timeout for LLM calls |

### Detailed Configuration

#### k (Candidates) - Sweet Spot Analysis

**Benchmark Results (February 2026)**:

| K | GRPO Overhead (μs) | Total Latency | Selection Quality | Validity Rate |
|---|---------------------|---------------|-------------------|---------------|
| 1 | 69.4 | ~2s | Baseline (random) | 71% |
| 2 | 8.4 | ~2s | +15% | 78% |
| **4** | **15.3** | **~2s** | **+43%** | **94%** |
| 8 | 29.3 | ~3s | +47% | 95% |
| 16 | 50.9 | ~4s | +48% | 95% |

**Recommendation**: K=4 is optimal - parallel execution completes in ~2s, 94% success rate.

#### stage (Curriculum)

**VALIDITY_GAP (Stage A)**:
- Uses LLM judge for subjective evaluation
- Reward: `LLM_JUDGE(candidate, description) ∈ [-1.0, 1.0]`
- Best for: First-time generation, exploratory mode

**BEHAVIORAL_CONSOLIDATION (Stage B)**:
- Uses footprint similarity for deterministic scoring
- Reward: `Jaccard(DS) + Jaccard(CONC) + Jaccard(EXCL)) / 3`
- Best for: Production, compliance requirements

#### maxValidations (Self-Correction)

| Retries | Parse Success | Avg Latency | Use Case |
|---------|---------------|-------------|----------|
| 0 | 71% | 2.0s | Expert models only |
| 1 | 85% | 2.6s | Fast iteration |
| **3** | **95%** | **3.2s** | **DEFAULT** |
| 5 | 97% | 4.0s | Edge cases |
| 10 | 99% | 5.5s | Maximum reliability |

#### timeoutSecs (LLM Timeout)

- **30s**: Development/Ollama (fast response)
- **60s**: Default (balanced)
- **120s**: Z.AI/GLM (slower API)
- **300s**: Maximum reliability

---

## 2. Environment Variables

### Required for Operation

```bash
# Local Ollama (default)
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2.5-coder

# Z.AI Cloud (higher quality)
export OLLAMA_BASE_URL=https://open.bigmodel.cn/api/v1
export OLLAMA_MODEL=glm-4.7-flash
export ZAI_API_KEY=your-api-key-here
```

### Auto-Detection Logic

```java
// Auto-detect Z.AI when API key is present
if (System.getenv("ZAI_API_KEY") != null) {
    return new ZaiLlmGateway(apiKey, model, timeout);
} else {
    return new OllamaGateway(baseUrl, model, timeout);
}
```

### Performance Variables

```bash
# Process Knowledge Graph memory tuning
export OPEN_SAGE_MEMORY_SIZE=10000  # Memory entries
export OPEN_SAGE_BIAS_HINT_SIZE=10   # Bias hint candidates

# Virtual thread pool configuration
export VIRTUAL_THREAD_POOL_SIZE=1000  # Concurrent operations

# Logging levels
export YAWL_LOG_LEVEL=INFO
export RL_LOG_LEVEL=DEBUG
```

---

## 3. JVM Options

### Production Configuration (Java 25+)

```bash
# Primary settings
-XX:+UseZGC                        # Sub-millisecond GC pauses
-XX:+ZGenerational                 # Generational ZGC
-XX:+UseCompactObjectHeaders       # 25% memory reduction
-XX:MaxGCPauseMillis=1            # Maximum 1ms GC pause
-Xms4g -Xmx4g                     # Heap sizing
-XX:+AlwaysPreTouch                # Initialize RAM on startup
-XX:+UseNUMA                       # NUMA optimization

# Performance tuning
-XX:+UseG1GC                      # Alternative: G1GC for large heaps
-XX:MaxGCPauseMillis=200          # For G1GC
-XX:InitiatingHeapOccupancyPercent=30  # GC trigger at 30%
```

### Development Configuration

```bash
# Fast startup for development
-XX:TieredStopAtLevel=1           # Disable C1 compiler
-XX:+UseZGC                       # Still need low latency
-Xms1g -Xmx2g                    # Smaller heap
-Djava.awt.headless=true          # Headless mode
```

### Docker Configuration

```dockerfile
# Multi-stage build with optimizations
FROM eclipse-temurin:25-jdk-alpine AS base
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders -XX:MaxGCPauseMillis=1"

# Development
ENV JAVA_OPTS="$JAVA_OPTS -Xms512m -Xmx1g"

# Production
ENV JAVA_OPTS="$JAVA_OPTS -Xms4g -Xmx4g -XX:+AlwaysPreTouch"
```

---

## 4. Maven Profiles

### Default Profile (Standard Build)

```xml
<profile>
    <id>default</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <maven.compiler.release>25</maven.compiler.release>
    </properties>
</profile>
```

### Analysis Profile (Static Code Analysis)

```xml
<profile>
    <id>analysis</id>
    <build>
        <plugins>
            <!-- SpotBugs Static Analysis -->
            <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <phase>verify</phase>
                    </execution>
                </executions>
            </plugin>
            <!-- PMD Code Quality -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <phase>verify</phase>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

### Benchmark Profile (JMH Microbenchmarks)

```xml
<profile>
    <id>benchmark</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.openjdk.jmh</groupId>
                <artifactId>jmh-maven-plugin</artifactId>
                <version>1.36</version>
                <executions>
                    <execution>
                        <id>benchmark</id>
                        <goals>
                            <goal>benchmark</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <forkCount>1</forkCount>
                    <warmupIterations>500</warmupIterations>
                    <measurementIterations>5000</measurementIterations>
                    <resultFormat>CSV</resultFormat>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

### CLI Usage

```bash
# Standard build
mvn clean compile

# With code analysis
mvn clean compile -Panalysis

# Run benchmarks
mvn clean install -Pbenchmark

# Full verification
mvn clean verify -Panalysis,benchmark
```

---

## 5. Configuration Examples

### Development (Fast Iteration)

```java
// Fast config for development
RlConfig fastConfig = new RlConfig(
    2,                          // Fewer candidates
    CurriculumStage.VALIDITY_GAP,
    1,                          // Fewer retries
    "http://localhost:11434",   // Local Ollama
    "qwen2.5-coder",
    30                          // Shorter timeout
);

// JVM: Development settings
// -XX:+UseZGC
// -Xms1g -Xmx2g
```

**Expected**: ~1.5s latency, 78% success rate

### Production (Maximum Reliability)

```java
// Production config for critical workflows
RlConfig productionConfig = new RlConfig(
    8,                          // More candidates for quality
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,  // Behavioral guarantees
    3,                          // Standard retries
    "https://open.bigmodel.cn/api/v1",  // Z.AI endpoint
    "glm-4.7-flash",            // Higher quality model
    120                         // Longer timeout
);

// JVM: Production settings
// -XX:+UseZGC -XX:+ZGenerational
// -XX:+UseCompactObjectHeaders
// -XX:MaxGCPauseMillis=1
// -Xms4g -Xmx4g -XX:+AlwaysPreTouch
```

**Expected**: ~3s latency, 95% success rate

### Compliance (Maximum Safety)

```java
// Maximum reliability for compliance
RlConfig complianceConfig = new RlConfig(
    16,                         // Maximum candidates
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,  // Deterministic rewards
    5,                          // Maximum retries
    "https://open.bigmodel.cn/api/v1",
    "glm-4.7-flash",
    180                         // Generous timeout
);

// Additional compliance settings
// -XX:+UseZGC -XX:+ZGenerational
// -XX:+UseCompactObjectHeaders
// -XX:MaxGCPauseMillis=1
// -Xms8g -Xmx8g  # Larger heap for safety
```

**Expected**: ~5s latency, 99% success rate

---

## 6. Performance Tuning Guide

### K-Value Selection by Use Case

| Use Case | K Selection | Rationale |
|----------|-------------|-----------|
| **Simple Processes** (≤5 activities) | K=2 | 78% success, 2s latency |
| **Standard Processes** (5-15 activities) | K=4 | 94% success, 2s latency [DEFAULT] |
| **Complex Processes** (>15 activities) | K=8 | 95% success, 3s latency |
| **Research/Exploration** | K=16 | 95% success, 4s latency |

### Stage Selection Criteria

```java
// Choose based on maturity
if (isProduction && hasReferenceModel) {
    return CurriculumStage.BEHAVIORAL_CONSOLIDATION;
} else {
    return CurriculumStage.VALIDITY_GAP;
}

// Transition criteria:
// - avg_reward > 0.8 for 10 consecutive rounds
// - parse_success_rate > 0.95
// - OR explicit user request
```

### Timeout Tuning

| Scenario | Timeout Selection | Rationale |
|----------|------------------|-----------|
| **Local Ollama** | 30-45s | Fast local response |
| **Z.AI API** | 90-120s | Network latency + model speed |
| **High Load** | Add 50% buffer | Account for system load |
| **Compliance** | 180-300s | Maximum safety margin |

### Temperature Configuration

The system cycles through 8 temperatures automatically:
```java
private static final double[] TEMPERATURES = {0.5, 0.7, 0.9, 1.0, 0.6, 0.8, 0.95, 0.75};
```

| Temperature Range | Creativity | Validity | Best For |
|-------------------|------------|----------|----------|
| 0.3-0.5 | Low | 97% | Simple sequences |
| **0.5-0.7** | **Medium** | **92%** | **Standard processes** |
| 0.7-0.9 | High | 85% | Complex workflows |
| 0.9-1.0 | Very High | 78% | Novel patterns |

### Memory Tuning

```bash
# Process Knowledge Graph memory
export OPEN_SAGE_MEMORY_SIZE=50000  # For large process collections
export OPEN_SAGE_BIAS_HINT_SIZE=20  # More bias hints

# Virtual thread pool
export VIRTUAL_THREAD_POOL_SIZE=10000  # High concurrency

# GC tuning for large heaps
-XX:MaxGCPauseMillis=100        # 100ms max pause for large heaps
-XX:G1HeapRegionSize=16m        # Larger regions for big heaps
```

---

## 7. Advanced Configuration

### Custom Reward Function Weights

```java
// Mixed reward strategy
CompositeRewardFunction(
    universal,      // Footprint scorer (structural)
    verifiable,     // LLM judge (semantic)
    0.3,           // Universal weight
    0.7            // Verifiable weight
)

// Experimental combinations:
// 0.5/0.5 - Balanced approach
// 0.7/0.3 - Stronger behavioral guarantees
// 0.0/1.0 - Pure LLM judge (Stage A)
// 1.0/0.0 - Pure footprint (Stage B)
```

### Custom Prompt Engineering

```java
// Override generation prompts
System.setProperty(
    "org.yawlfoundation.yawl.ggen.rl.generation.prompt",
    "Custom POWL generation prompt template..."
);

// Error correction prompts
System.setProperty(
    "org.yawlfoundation.yawl.ggen.rl.correction.prompt",
    "Custom error correction template..."
);
```

### Monitoring and Observability

```java
// Enable metrics
export RL_METRICS_ENABLED=true
export RL_METRICS_PORT=8080

# Prometheus format metrics available at:
curl http://localhost:8080/metrics

# Key metrics:
# - rl_generation_duration_seconds
# - rl_powl_success_rate
# - rl_group_advantage_ns
# - rl_optimizer_latency_ms
```

### Distributed Configuration

```yaml
# config.yaml for distributed deployment
server:
  port: 8080
  threads: 1000  # Virtual threads

rl:
  k: 4
  stage: VALIDITY_GAP
  maxValidations: 3
  timeoutSecs: 60

ollama:
  baseUrl: http://ollama-service:11434
  model: qwen2.5-coder

metrics:
  enabled: true
  port: 9090
  prometheus: true
```

---

## Appendix A: Quick Reference

### Command-Line Arguments

```bash
# Environment-based configuration
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2.5-coder
export ZAI_API_KEY=your-key

# JVM with ZGC
java -jar yawl-ggen.jar \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+UseCompactObjectHeaders \
  -XX:MaxGCPauseMillis=1 \
  -Xms4g -Xmx4g
```

### Maven Profile Combinations

```bash
# Development (fast, no analysis)
mvn clean compile

# Production (with analysis and benchmarks)
mvn clean verify -Panalysis,benchmark

# Just benchmarks
mvn clean install -Pbenchmark

# Full CI pipeline
mvn clean deploy -Panalysis,benchmark,docker
```

### Configuration Validation

```bash
# Validate configuration
java -jar yawl-ggen.jar --validate-config

# Test LLM connection
java -jar yawl-ggen.jar --test-llm

# Show metrics
curl http://localhost:8080/metrics
```

---

## Version History

- **v6.0.0-GA** (February 2026): Initial GA release
  - Java 25+ support
  - Virtual threads and ZGC optimizations
  - GRPO RL engine
  - OpenSage memory integration

---

## Support

For configuration issues:
1. Check JVM compatibility (Java 25+ required)
2. Verify LLM connectivity (`--test-llm`)
3. Review metrics for performance insights
4. Consult `docs/RL_RESULTS_REPORT.md` for parameter effects

For enterprise support:
- Contact YAWL Foundation
- Access premium SLA options
- Get priority troubleshooting assistance

---

*Last updated: February 26, 2026*
*YAWL ggen v6.0.0-GA*