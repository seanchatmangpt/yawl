---
title: "Generate Workflows with GRPO"
version: "v6.0.0-GA"
lastUpdated: "2026-02-21"
---

# Generate Workflows with GRPO

## What is GRPO?

GRPO (Generalist Reward Policy Optimization) is a reinforcement learning approach integrated into YAWL v6.0.0-GA that enables automatic workflow generation based on natural language specifications. Instead of manually designing workflows, you can describe what you want to accomplish, and GRPO will generate an optimized YAWL workflow.

Key benefits of GRPO:
- **Natural Language to Workflow**: Convert requirements directly to executable workflows
- **Optimized Design**: RL-generated workflows follow best practices for your domain
- **Adaptive Learning**: Improves generation quality over time through OpenSage memory
- **Multiple Implementation Strategies**: Chooses the most appropriate control-flow patterns

## Prerequisites

### Required Software

1. **Ollama** (for RL agent)
   ```bash
   # Install Ollama
   curl -fsSL https://ollama.com/install.sh | sh

   # Pull required models
   ollama pull llama3.2
   ollama pull nomic-embed-text
   ```

2. **Java 25** (JDK)
   ```bash
   # Install Java 25
   # macOS with Homebrew:
   brew install openjdk@25

   # Verify installation
   java -version
   # Expected output: openjdk version "25.0.1" 2026-02-01
   ```

3. **YAWL v6.0.0-GA**
   ```bash
   # Download and extract YAWL distribution
   wget https://github.com/yawl/yawl/releases/download/v6.0.0-GA/yawl-distribution-v6.0.0-GA.zip
   unzip yawl-distribution-v6.0.0-GA.zip
   cd yawl-distribution-v6.0.0-GA
   ```

### Configuration

Create `grpo-config.properties`:
```properties
# GRPO Configuration
ollama.host=localhost
ollama.port=11434
ollama.model=llama3.2
ollama.embed.model=nomic-embed-text

# RL Parameters
grpo.k=16
grpo.stages=6
grpo.temperature=0.7
grpo.max.iterations=100

# Memory Storage
memory.storage.type=sqlite
memory.storage.path=./opensage.db
memory.patterns=true
memory.retention=90d
```

## Quick Start: Generate Your First Workflow

### Using the Command Line Interface

1. **Start YAWL Engine**
   ```bash
   ./bin/start-yawl.sh
   ```

2. **Generate a Simple Approval Workflow**
   ```bash
   curl -X POST http://localhost:8080/api/v1/grpo/generate \
     -H "Content-Type: application/json" \
     -d '{
       "specification": "Create a purchase approval workflow with amounts under $1000 needing manager approval, amounts over $1000 needing director approval",
       "domain": "procurement",
       "constraints": ["must audit all approvals", "auto-reject after 5 days"]
     }'
   ```

3. **Response Example**
   ```json
   {
     "workflowId": "purchase-approval-2026-02-21-001",
     "specification": {
       "name": "Purchase Approval",
       "description": "Automated purchase approval workflow",
       "version": "1.0"
     },
     "generatedAt": "2026-02-21T10:30:15Z",
     "yawlXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<yawl:specification ...>",
     "confidence": 0.92,
     "patternsUsed": ["ExclusiveChoice", "Cancellation", "Synchronization"]
   }
   ```

### Using the Java API

```java
import org.yawl.engine.GRPOWorkflowGenerator;
import org.yawl.engine.domain.WorkflowSpecification;

public class GRPOQuickStart {
    public static void main(String[] args) throws Exception {
        // Initialize generator
        GRPOWorkflowGenerator generator = new GRPOWorkflowGenerator(
            "config/grpo-config.properties"
        );

        // Define workflow requirements
        String specification = """
            Create a customer onboarding workflow with:
            - Data collection phase (form submission)
            - Identity verification step
            - Credit check for high-value customers
            - Welcome email upon approval
            - Reject after 7 days if not completed
        """;

        // Generate workflow
        WorkflowSpecification workflow = generator.generate(
            specification,
            "customer-onboarding",
            Set.of("financial", "compliance")
        );

        // Save to file
        workflow.saveToFile("generated-workflows/customer-onboarding.yawl");

        System.out.println("Generated workflow: " + workflow.getId());
        System.out.println("Confidence score: " + workflow.getConfidence());
    }
}
```

## Understanding the Generation Process

### RL Agent Workflow

The GRPO generation process follows this sequence:

1. **Input Processing**: Natural language → semantic representation
   - Uses Llama 3.2 for understanding requirements
   - Extracts key entities, actions, and constraints

2. **Pattern Matching**: Query OpenSage memory for similar workflows
   - Semantic similarity using Nomic embeddings
   - Retrieve successful patterns from past generations

3. **Policy Optimization**: Generate multiple candidates
   - K=16 parallel generation attempts
   - Evaluate each using reward function
   - Select highest-scoring implementation

4. **Validation & Refinement**: Ensure correctness
   - Check YAWL schema validity
   - Verify pattern completeness
   - Optimize for performance

5. **Memory Storage**: Learn from generation
   - Store successful patterns
   - Update reward weights
   - Improve future generations

### Reward Function Design

The RL agent optimizes for multiple objectives:

```java
public class WorkflowRewardFunction {
    // Pattern completeness score
    public double patternCompleteness(Set<ControlFlowPattern> patterns) {
        // Reward for using appropriate patterns
        // Penalize missing required patterns
    }

    // Performance optimization
    public double performanceScore(WorkflowSpecification workflow) {
        // Reward for low complexity
        // Reward for parallel execution potential
        // Penalize excessive joins/splits
    }

    // Domain adherence
    public double domainAdherence(WorkflowSpecification workflow,
                                  String domain) {
        // Reward for domain-specific best practices
        // Penalize anti-patterns
    }

    // Maintainability
    public double maintainabilityScore(WorkflowSpecification workflow) {
        // Reward for clear naming
        // Reward for modularity
        // Penalize excessive complexity
    }
}
```

## Configuring GRPO Parameters

### Key Parameters Explained

1. **K (Exploration)**
   - Controls parallel generation attempts
   - Higher K = more exploration, slower generation
   - Range: 4-32 (default: 16)

   ```properties
   # grpo.k=16 (balanced exploration)
   # grpo.k=4 (fast, focused generation)
   # grpo.k=32 (comprehensive, slow)
   ```

2. **Stages (Refinement Iterations)**
   - Number of optimization iterations
   - Higher stages = better quality, slower generation
   - Range: 3-10 (default: 6)

   ```properties
   # grpo.stages=6 (good balance)
   # grpo.stages=3 (fast, rough)
   # grpo.stages=10 (high quality, slow)
   ```

3. **Temperature (Creativity)**
   - Controls randomness in generation
   - Lower temperature = more deterministic
   - Range: 0.1-2.0 (default: 0.7)

   ```properties
   # grpo.temperature=0.3 (conservative)
   # grpo.temperature=0.7 (balanced)
   # grpo.temperature=1.5 (creative)
   ```

### Advanced Configuration

```properties
# Resource Constraints
grpo.max.generation.time=300s
grpo.max.tokens=32000
grpo.batch.size=8

# Model Selection
ollama.model=llama3.2:latest
ollama.embed.model=nomic-embed-text:v1

# Reward Function Weights
reward.pattern.weight=0.4
reward.performance.weight=0.3
reward.domain.weight=0.2
reward.maintainability.weight=0.1

# Memory Configuration
memory.pattern.retention=180d
memory.performance.metrics=true
memory.embeddings.dimension=768
```

## Troubleshooting Common Issues

### Issue 1: Generation Takes Too Long

**Problem**: GRPO generation exceeds expected time
```bash
# Check generation progress
curl http://localhost:8080/api/v1/grpo/status/{generationId}

# Configure faster generation
grpo.k=8          # Reduce parallel attempts
grpo.stages=3     # Fewer refinement stages
grpo.temperature=0.5  # More deterministic
```

### Issue 2: Low Confidence Scores

**Problem**: Generated workflows have confidence < 0.8
```json
// Query OpenSage for similar patterns
curl -X POST http://localhost:8080/api/v1/memory/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "purchase approval workflow",
    "domain": "procurement",
    "limit": 10
  }'
```

**Solutions**:
- Increase training data in memory
- Adjust reward function weights
- Use more specific domain language
- Break complex requirements into smaller workflows

### Issue 3: Model Connection Errors

**Problem**: Ollama connection refused

```bash
# Check Ollama status
ollama list

# Restart Ollama if needed
pkill ollama
ollama serve &

# Verify connection
curl http://localhost:11434/api/tags
```

### Issue 4: Memory Storage Issues

**Problem**: OpenSage memory corruption or slow queries

```bash
# Check database integrity
sqlite3 opensage.db "PRAGMA integrity_check;"

# Optimize database
sqlite3 opensage.db "VACUUM;"
sqlite3 opensage.db "ANALYZE;"

# Compact memory
curl -X POST http://localhost:8080/api/v1/memory/compact \
  -H "Content-Type: application/json"
```

## Performance Optimization

### Batch Generation

```java
// Generate multiple workflows efficiently
List<String> specifications = List.of(
    "Simple approval workflow",
    "Complex multi-step process",
    "Parallel execution pattern"
);

GRPOWorkflowGenerator generator = new GRPOWorkflowGenerator(config);
List<WorkflowSpecification> workflows =
    generator.batchGenerate(specifications, "hr-domain");
```

### Caching Results

```java
// Enable caching for repeated specifications
GRPOWorkflowGenerator generator = new GRPOWorkflowGenerator(config);
generator.enableCaching(true);

// Check cache before generation
WorkflowSpecification cached =
    generator.getCachedSpecification("employee-onboarding");
if (cached != null) {
    return cached;
}
```

## Best Practices

1. **Start Simple**: Begin with basic workflows before complex ones
2. **Iterate**: Generate multiple versions and compare
3. **Validate**: Always test generated workflows with test data
4. **Monitor**: Track generation success rates and confidence scores
5. **Update Memory**: Feed successful patterns back into memory

## Next Steps

- Learn more about reinforcement learning in [RL_USER_GUIDE.md](../reference/rl-user-guide.md)
- Configure OpenSage memory for your domain in [12-opensage-memory.md](./12-opensage-memory.md)
- Optimize performance with virtual threads in [13-virtual-threads-performance.md](./13-virtual-threads-performance.md)
- Explore advanced workflow patterns in [workflow-patterns.md](../reference/workflow-patterns.md)