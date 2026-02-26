# YAWL ggen v6.0.0-GA Getting Started Guide

**Status**: GA-Ready | **Java 25+** | **February 2026**

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start](#2-quick-start)
3. [Basic Usage](#3-basic-usage)
4. [Configuration](#4-configuration)
5. [Examples](#5-examples)
6. [Troubleshooting](#6-troubleshooting)

---

## 1. Prerequisites

### System Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Java JDK** | 25+ | Required for virtual threads |
| **Maven** | 3.8+ | Build system |
| **Ollama** | 0.1.27+ | Local LLM (optional) |
| **RAM** | 4GB+ | 8GB+ recommended |
| **CPU** | 2 cores | 4+ cores recommended |

### Verify Java Version

```bash
java --version
# Expected: java 25.0.x or higher
```

### Install Ollama (Optional - for local LLM)

```bash
# macOS/Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Pull a model
ollama pull qwen2.5-coder
```

### Environment Variables

```bash
# Optional: Configure Ollama
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2.5-coder

# Optional: Use Z.AI instead
export ZAI_API_KEY=your-api-key-here
```

---

## 2. Quick Start

### Build the Module

```bash
# Clone the repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Compile the module
bash scripts/dx.sh -pl yawl-ggen

# Run tests
bash scripts/dx.sh -pl yawl-ggen test
```

### Run a Simple Generation

```bash
# Using the CLI
java -jar yawl-ggen/target/yawl-ggen.jar \
  --description "Order processing: receive, validate, ship" \
  --output output.yawl

# Or via Maven
mvn -pl yawl-ggen exec:java \
  -Dexec.args="--description 'Order processing workflow' --output output.yawl"
```

### Expected Output

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
  <nets>
    <net id="net-1">
      <inputCondition id="input"/>
      <tasks>
        <task id="receive_order">
          <name>Receive Order</name>
          <flowsInto>
            <nextElementRef id="validate_order"/>
          </flowsInto>
        </task>
        <!-- ... more tasks ... -->
      </tasks>
      <outputCondition id="output"/>
    </net>
  </nets>
</specification>
```

---

## 3. Basic Usage

### Java API

```java
import org.yawlfoundation.yawl.ggen.rl.*;
import org.yawlfoundation.yawl.ggen.rl.scoring.*;
import org.yawlfoundation.yawl.ggen.powl.*;
import org.yawlfoundation.yawl.ggen.memory.*;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // 1. Create configuration
        RlConfig config = RlConfig.defaults();

        // 2. Create memory system (optional but recommended)
        ProcessKnowledgeGraph memory = new ProcessKnowledgeGraph();

        // 3. Create sampler with memory
        OllamaCandidateSampler sampler = new OllamaCandidateSampler(config, memory);

        // 4. Create reward function
        RewardFunction rewards = new CompositeRewardFunction(
            new FootprintScorer(),    // Structural scoring
            new LlmJudgeScorer(),     // Semantic scoring
            0.5, 0.5                  // Balanced weights
        );

        // 5. Create optimizer with memory
        GrpoOptimizer optimizer = new GrpoOptimizer(
            sampler, rewards, config, memory
        );

        // 6. Generate model
        String description = """
            Loan application process:
            1. Customer submits application
            2. Bank reviews credit score
            3. If approved, send offer
            4. If rejected, send denial letter
            """;

        PowlModel model = optimizer.optimize(description);

        // 7. Convert to YAWL
        YawlSpecification yawl = new PowlToYawlConverter().convert(model);

        // 8. Validate
        ValidationReport report = new PowlValidator().validate(model);
        if (!report.valid()) {
            System.err.println("Validation errors: " + report.errors());
        }

        // 9. Output
        System.out.println(yawl.toXml());
    }
}
```

### REST API

Start the service:

```bash
# Build and run the web application
mvn -pl yawl-ggen package
java -jar yawl-ggen/target/yawl-ggen.war
```

Submit a job:

```bash
# Submit conversion job
curl -X POST http://localhost:8080/api/v1/process/convert \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Invoice approval: submit, review, approve or reject",
    "format": "yawl",
    "rlConfig": {
      "k": 4,
      "stage": "VALIDITY_GAP"
    }
  }'

# Response: {"jobId": "job-123", "status": "QUEUED", ...}

# Poll for status
curl http://localhost:8080/api/v1/process/jobs/job-123

# Response: {"jobId": "job-123", "status": "COMPLETE", "outputContent": "..."}
```

---

## 4. Configuration

### Development (Fast Iteration)

```java
RlConfig devConfig = new RlConfig(
    2,                          // K=2 for speed
    CurriculumStage.VALIDITY_GAP,
    1,                          // Minimal retries
    "http://localhost:11434",
    "qwen2.5-coder",
    30                          // Short timeout
);
```

**Expected**: ~1.5s latency, 78% success rate

### Production (Balanced)

```java
RlConfig prodConfig = new RlConfig(
    4,                          // K=4 optimal
    CurriculumStage.VALIDITY_GAP,
    3,                          // Standard retries
    "http://localhost:11434",
    "qwen2.5-coder",
    60                          // 1-minute timeout
);
```

**Expected**: ~2s latency, 94% success rate

### Compliance (Maximum Reliability)

```java
RlConfig complianceConfig = new RlConfig(
    8,                          // K=8 for quality
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,
    5,                          // More retries
    "https://open.bigmodel.cn/api/v1",
    "glm-4.7-flash",
    180                         // Generous timeout
);
```

**Expected**: ~3s latency, 95% success rate

---

## 5. Examples

### Example 1: Sequential Process

```java
String description = "Simple order process: place order, process payment, ship";

PowlModel model = optimizer.optimize(description);
// Expected output: SEQ(place_order, process_payment, ship)
```

### Example 2: Exclusive Choice

```java
String description = """
    Loan approval:
    - Submit application
    - Check credit score
    - If score > 700: approve
    - If score <= 700: reject
    """;

PowlModel model = optimizer.optimize(description);
// Expected output: SEQ(submit, check_credit, XOR(approve, reject))
```

### Example 3: Parallel Execution

```java
String description = """
    Order fulfillment:
    - Process order in parallel:
      * Charge credit card
      * Reserve inventory
      * Generate invoice
    - Ship when all complete
    """;

PowlModel model = optimizer.optimize(description);
// Expected output: SEQ(AND(charge, reserve, generate), ship)
```

### Example 4: Loop

```java
String description = """
    Review process:
    - Submit document
    - Review cycle:
      * Reviewer checks document
      * If changes needed, send back for revision
      * Repeat until approved
    - Finalize
    """;

PowlModel model = optimizer.optimize(description);
// Expected output: SEQ(submit, LOOP(review, revise), finalize)
```

### Example 5: Complex Workflow

```java
String description = """
    E-commerce order:
    1. Customer places order
    2. System checks inventory in parallel with payment processing
    3. If both succeed:
       - Pack items
       - Ship order
    4. If inventory fails: notify customer, cancel
    5. If payment fails: notify customer, retry or cancel
    """;

PowlModel model = optimizer.optimize(description);
// Complex structure with XOR/AND combinations
```

---

## 6. Troubleshooting

### Common Issues

#### 1. "Connection refused" to Ollama

```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# If not, start it
ollama serve
```

#### 2. "Java 25 required" error

```bash
# Check Java version
java --version

# Install Java 25+ from:
# https://adoptium.net/temurin/releases/?version=25
```

#### 3. "Out of memory" errors

```bash
# Increase heap size
java -Xmx4g -jar yawl-ggen.jar

# Or use JVM flags for production
java -XX:+UseZGC -Xms4g -Xmx4g -jar yawl-ggen.jar
```

#### 4. Low success rate (<80%)

- Increase K to 4 or 8
- Increase maxValidations to 3-5
- Try a different model (e.g., qwen2.5-coder)
- Check your description is clear and specific

#### 5. Timeout errors

```java
// Increase timeout in config
RlConfig config = new RlConfig(
    4, CurriculumStage.VALIDITY_GAP, 3,
    "http://localhost:11434",
    "qwen2.5-coder",
    120  // Increase from 60 to 120 seconds
);
```

### Logging

Enable debug logging:

```bash
# Enable RL debug logging
export RL_LOG_LEVEL=DEBUG

# Or via Java system property
java -Drl.log.level=DEBUG -jar yawl-ggen.jar
```

### Getting Help

1. Check the [documentation](./)
2. Review [benchmark results](./BENCHMARKS.md) for performance expectations
3. Consult [configuration guide](./CONFIGURATION.md) for tuning
4. Open an issue on GitHub

---

## Next Steps

- Read the [Architecture Overview](./ARCHITECTURE.md)
- Learn about the [RL Engine](./RL_ENGINE.md)
- Explore [API Reference](./API_REFERENCE.md)
- Check [Configuration options](./CONFIGURATION.md)

---

*Last Updated: February 26, 2026*
*Version: YAWL ggen v6.0.0-GA*
