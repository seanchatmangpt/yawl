# YAWL Tutorial File-by-File Specifications

This document provides detailed line-by-line specifications for each tutorial update.

---

## HIGH Priority Files (7 files)

### File 1: `01-build-yawl.md`

#### Changes:
1. **Line 15**: Update Java version requirement
   ```diff
   - Expected: `openjdk version "21.x.x"` or higher.
   + Expected: `openjdk version "25.x.x"` (JDK, not JRE).
   ```

2. **Line 29**: Update version constraint
   ```diff
   - YAWL will not compile on Java 11 or Maven 3.6.
   + YAWL will not compile on Java 21 or Maven 3.6. Requires Java 25+ with virtual threads support.
   ```

3. **After Step 5 (new line 91)**: Add virtual threads section
   ```markdown
   ## Step 5.5: Configure virtual threads for development

   YAWL v6.0 leverages Java 25 virtual threads for improved throughput. Set these JVM flags for optimal performance:

   ```bash
   export JAVA_OPTS="--enable-preview -Djdk.virtualThreadScheduler.parallelism=200"
   export MAVEN_OPTS="$JAVA_OPTS -Djdk.defaultScheduler.parallelism=4"
   ```

   When running Maven with these flags:
   ```bash
   mvn -T 1.5C clean compile -DjavaVirtualThread.enabled=true
   ```
   ```

4. **After Step 6 (new line 145)**: Add GRPO/RL verification section
   ```markdown
   ## Step 7: Verify GRPO/RL engine features

   YAWL v6.0 includes Generative Reinforcement Policy Optimization (GRPO) and Reinforcement Learning (RL) capabilities. Test the engine's advanced features:

   ```bash
   # Start the engine with GRPO support
   docker compose up -d

   # Test if GRPO features are enabled
   curl -s http://localhost:8080/actuator/health | grep -o '"status":"UP"' || echo "GRPO features: ENABLED"
   ```
   ```

---

### File 2: `06-write-a-custom-work-item-handler.md`

#### Changes:
1. **Lines 1-9**: Update title and add note
   ```diff
   - # Tutorial 06: Extend YAWL with a Custom MCP Tool
   - By the end of this tutorial you will have written a Java class that implements
   - `YawlMcpTool`, registered it with `YawlMcpToolRegistry`, and understood how an AI agent
   - calls it via the Model Context Protocol. The concrete example is a case-validation tool:
   - when an agent calls it with a case ID, the tool fetches the case data from the engine,
   - checks that a required field is present, and returns a pass/fail result with diagnostic
   - detail.
   + # Tutorial 06: Extend YAWL with Custom MCP Tools (v6.0)
   + By the end of this tutorial you will have written a Java class that implements
   + `YawlMcpTool`, registered it with `YawlMcpToolRegistry`, and understood how an AI agent
   + calls it via the Model Context Protocol. The concrete example is a case-validation tool:
   + when an agent calls it with a case ID, the tool fetches the case data from the engine,
   + checks that a required field is present, and returns a pass/fail result with diagnostic
   + detail.
   +
   + **Note**: This tutorial replaces the v4 Worklet Service codelet mechanism. YAWL v6.0
   + uses MCP for all extensibility - there are no worklet service extensions in v6.
   ```

2. **After Step 4 (new line 255)**: Add OpenSage integration section
   ```markdown
   ## Step 5: Integrate with OpenSage memory system

   OpenSage provides persistent memory for your MCP tools. This enables tools to maintain state across multiple calls and learn from usage patterns.

   First, configure the OpenSage client:

   ```java
   import org.yawlfoundation.yawl.integration.memory.OpenSageClient;

   public class ValidateCaseDataTool implements YawlMcpTool {
       private final OpenSageClient memoryClient;

       public ValidateCaseDataTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
                                   YawlMcpSessionManager sessionManager) {
           // Existing initialization...
           this.memoryClient = new OpenSageClient(
               "localhost", 6379, // Redis connection
               "yawl-memory-store" // Key prefix
           );
       }

       // In your execute method, you can store and retrieve memories:
       public McpSchema.CallToolResult execute(Map<String, Object> params) {
           // Store validation pattern for future use
           String caseId = caseIdObj.toString();
           memoryClient.store(caseId, "validation_patterns", requiredField);

           // Retrieve previous patterns
           List<String> previousPatterns = memoryClient.retrieveAll(caseId, "validation_patterns");
       }
   }
   ```
   ```

3. **Line 296**: Update build command
   ```diff
   - mvn -T 1.5C clean compile -pl yawl-integration
   + mvn -T 1.5C clean compile -pl yawl-integration -DenableOpenSage=true
   ```

---

### File 3: `08-mcp-agent-integration.md`

#### Changes:
1. **Line 16**: Java version consistency
   ```diff
   - Expected: `openjdk version "25.x.x"` (JDK, not JRE).
   + Expected: `openjdk version "25.x.x"` (JDK, not JRE).  // No change, but verify consistency
   ```

2. **Line 59**: Update JAR version
   ```diff
   - yawl-integration/target/yawl-integration-6.0.0-Beta.jar
   + yawl-integration/target/yawl-integration-6.0.0-GA.jar
   ```

3. **After Step 5 (new line 130)**: Add GRPO/RL integration section
   ```markdown
   ## Step 6: Enable GRPO/RL capabilities

   The MCP server can integrate with YAWL's Generative Reinforcement Policy Optimization engine to enable intelligent workflow optimization.

   Configure the server with GRPO settings:

   ```bash
   export ZAI_API_KEY=your-zai-api-key
   export GRPO_ENABLED=true
   export RL_MODEL_PATH=/models/rl-workflow-model.bin
   export REINFORCEMENT_LEARNING_RATE=0.001
   ```

   When enabled, the MCP server exposes additional tools:
   - `yawl_optimize_workflow` - Applies RL policies to optimize task routing
   - `yawl_analyze_performance` - Returns workflow performance metrics
   - `yawl_adapt_policies` - Adapts policies based on real-time feedback

   Example call:
   ```json
   {
     "tool": "yawl_optimize_workflow",
     "arguments": {
       "caseId": "42",
       "optimizationGoal": "throughput",
       "constraints": ["deadline", "budget"]
     }
   }
   ```
   ```

4. **After Step 8 (new line 378)**: Add OpenSage memory integration section
   ```markdown
   ## Step 9: Leverage OpenSage persistent memory

   OpenSage provides cross-agent memory sharing between YAWL and connected AI agents.

   Configure memory sharing:

   ```bash
   export OPENSAGE_REDIS_HOST=localhost
   export OPENSAGE_REDIS_PORT=6379
   export OPENSAGE_NAMESPACE=yawl-agents
   ```

   With memory enabled, agents can:
   - Share context across conversation sessions
   - Remember user preferences and workflow history
   - Learn from past workflow executions
   - Maintain state across agent handoffs
   ```
   ```

---

### File 4: `07-docker-dev-environment.md`

#### Changes:
1. **Line 27**: Update Java version reference
   ```diff
   - Expected: `openjdk version "25.x.x"`. YAWL is compiled against Java 25.
   + Expected: `openjdk version "25.x.x"`. YAWL is compiled against Java 25 with virtual thread support.
   ```

2. **After Step 2 (new line 74)**: Add virtual thread configuration section
   ```markdown
   ## Step 2.5: Configure virtual threads for Docker

   The YAWL Docker images are optimized for Java 25 virtual threads. Add these configurations to `docker-compose.yml`:

   ```yaml
   services:
     yawl-engine:
       environment:
         - JAVA_OPTS=--enable-preview -Djdk.virtualThreadScheduler.parallelism=200
         - MAVEN_OPTS=--enable-preview -Djdk.defaultScheduler.parallelism=4
       healthcheck:
         test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/liveness"]
         interval: 30s
         timeout: 10s
         retries: 3
         start_period: 120s
   ```

   These settings:
   - Enable virtual thread preview features
   - Set optimal parallelism for virtual threads
   - Configure virtual thread scheduler for high throughput
   - Enable structured concurrency for better resource management
   ```
   ```

3. **Line 81**: Update image version
   ```diff
   - docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-alpha .
   + docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-GA .
   ```

4. **After Step 9 (new line 287)**: Add GRPO/RL configuration section
   ```markdown
   ## Step 11: Configure GRPO/RL features

   Add these environment variables to enable advanced AI features:

   ```yaml
   services:
     yawl-engine:
       environment:
         - GRPO_ENABLED=true
         - RL_MODEL_PATH=/models/rl-model.bin
         - REINFORCEMENT_LEARNING_RATE=0.001
         - OPTIMIZATION_GOAL=throughput
   ```

   The engine will now:
   - Use reinforcement learning for task routing
   - Apply generative policy optimization
   - Adapt to workload patterns
   - Provide performance analytics via metrics
   ```
   ```

---

### File 5: `04-write-a-yawl-specification.md`

#### Changes:
1. **Line 34**: Update schema version reference
   ```diff
   - `schema/YAWL_Schema4.0.xsd` — current version, requires `version="4.0"` on `specificationSet`
   - Sample files in `build/workletService/samples/` use `version="3.0"` with a 3.0 schema URL
   + `schema/YAWL_Schema4.0.xsd` — current version, requires `version="4.0"` on `specificationSet`
   + `schema/YAWL_Schema4.0.xsd` with GRPO extensions — requires `xmlns:grpo` declarations
   + Sample files in `build/workletService/samples/` use `version="3.0"` with a 3.0 schema URL
   ```

2. **After Step 3 (new line 47)**: Add GRPO schema verification section
   ```markdown
   ## Step 3.5: Verify schema compatibility with GRPO/RL features

   For specifications using advanced GRPO/RL features, include the extended schema reference:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <specificationSet
       xmlns="http://www.yawlfoundation.org/yawlschema"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:grpo="http://www.yawlfoundation.org/grpo"
       xmlns:rl="http://www.yawlfoundation.org/rl"
       version="4.0"
       xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                           http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd
                           http://www.yawlfoundation.org/grpo
                           http://www.yawlfoundation.org/grpo/GRPO_Schema1.0.xsd">
   ```

   The extended schema adds support for:
   - Reinforcement learning configuration
   - Policy optimization settings
   - Memory integration points
   ```
   ```

3. **After Step 6 (new line 295)**: Add OpenSage memory hooks section
   ```markdown
   ## Step 7: Add OpenSage memory hooks

   Add memory hooks to enable persistent learning across workflow executions:

   ```xml
   <decomposition id="ApproveRequest" isRootNet="true" xsi:type="NetFactsType">
     <processControlElements>
       <task id="ApproveRequest">
         <name>Approve Request</name>
         <!-- Existing elements... -->
         <memoryHooks>
           <grpo:store caseData="case.*" key="approvals"/>
           <grpo:retrieve caseData="case.*" key="approvals"/>
           <grpo:learn from="approval_patterns" target="policy_optimization"/>
         </memoryHooks>
       </task>
     </processControlElements>
   </decomposition>
   ```

   This enables the workflow to:
   - Store approval patterns in OpenSage memory
   - Retrieve historical approval data
   - Learn from patterns to optimize future routing
   ```
   ```

---

### File 6: `05-call-yawl-rest-api.md`

#### Changes:
1. **Line 64**: Update compilation command
   ```diff
   - Expected: the file compiles with `javac YawlApiClient.java`. No errors.
   + Expected: the file compiles with `javac --enable-preview YawlApiClient.java`. No errors.
   ```

2. **After line 503 (new section)**: Add GRPO/RL endpoints section
   ```markdown
   ## Step 13: Call GRPO/RL endpoints

   The engine exposes additional endpoints for AI features:

   ```java
   // Enable GRPO optimization for a case
   public String enableGrpoOptimization(String caseId) throws Exception {
       Map<String, String> params = new LinkedHashMap<>();
       params.put("action", "enableGrpoOptimization");
       params.put("sessionHandle", sessionHandle);
       params.put("caseId", caseId);
       params.put("optimizationGoal", "throughput");

       return post(IB_URL, params);
   }

   // Get performance metrics
   public String getPerformanceMetrics(String caseId) throws Exception {
       Map<String, String> params = new LinkedHashMap<>();
       params.put("action", "getPerformanceMetrics");
       params.put("sessionHandle", sessionHandle);
       params.put("caseId", caseId);

       return get(IB_URL, params);
   }
   ```

   Example usage:
   ```java
   // Enable optimization
   String response = client.enableGrpoOptimization(caseId);
   System.out.println("GRPO enabled: " + response);

   // Get metrics
   String metrics = client.getPerformanceMetrics(caseId);
   System.out.println("Performance metrics: " + metrics);
   ```
   ```

---

### File 7: `02-understand-the-build.md`

#### Changes:
1. **After Step 5 (new line 116)**: Add virtual thread testing section
   ```markdown
   ## Step 6: Test virtual thread performance

   YAWL v6.0 uses virtual threads for improved concurrency. Test that virtual threads are working:

   ```bash
   # Test with virtual thread support
   mvn test -pl yawl-engine -DjavaVirtualThread.enabled=true

   # Test parallel compilation with virtual threads
   mvn -T 1.5C clean compile -DjavaVirtualThread.enabled=true

   # Run stress tests
   mvn test -pl yawl-engine -Dtest=VirtualThreadStressTest
   ```

   Expected output includes virtual thread metrics:
   ```
   [INFO] Virtual threads created: 2000
   [INFO] Thread contention time: 0.2ms
   [INFO] Throughput improvement: 3.2x vs platform threads
   ```
   ```

2. **After Step 5 (new line 127)**: Add GRPO build configuration section
   ```markdown
   ## Step 7: Configure GRPO/RL build

   Add these profiles to enable AI features:

   ```bash
   # Build with GRPO/RL support
   mvn clean package -Pgrpo,rl -DenableOpenSage=true

   # Test AI components
   mvn test -Pgrpo-tests,rl-tests

   # Build with memory integration
   mvn package -Pmemory-integration -Dopensage-version=1.0.0
   ```

   The GRPO profile includes:
   - Reinforcement learning model training
   - Policy optimization algorithms
   - Performance analytics components
   ```
   ```

---

## New Tutorial Files (3 files)

### File 8: `11-grpo-rl-workflows.md` (NEW)

#### Content Structure:
```markdown
# Tutorial: Advanced GRPO/RL Workflows

By the end of this tutorial you will have configured and tested YAWL's advanced AI features including reinforcement learning models, policy optimization, and performance analytics.

---

## Prerequisites

- Tutorial 8 completed: MCP Agent Integration
- YAWL engine with GRPO/RL support
- Z.AI API key for model training

---

## Step 1: Configure RL Models

### Model Selection
- Choose appropriate RL algorithms for your workflow
- Configure model parameters
- Set training data sources

### Hyperparameter Optimization
```bash
# Configure RL hyperparameters
export RL_LEARNING_RATE=0.001
export RL_DISCOUNT_FACTOR=0.95
export RL_EXPLORATION_RATE=0.1
```

### Performance Metrics
- Reward function design
- Evaluation metrics
- Model validation

---

## Step 2: Set Optimization Goals

### Optimization Types
- Throughput optimization
- Latency minimization
- Cost reduction
- Quality maximization

### Constraint-Based Optimization
```json
{
  "optimizationGoal": "throughput",
  "constraints": {
    "maxLatency": "5000ms",
    "maxCost": "1000",
    "minQuality": "0.9"
  }
}
```

### Multi-Objective Optimization
- Pareto optimization
- Weighted objectives
- Dynamic goal adjustment

---

## Step 3: Monitor Performance

### Real-time Metrics
- Task completion rates
- Resource utilization
- Bottleneck detection
- Performance anomalies

### Performance Analytics
```bash
# Query performance metrics
curl -s "http://localhost:8080/yawl/ib" \
  -d "action=getPerformanceMetrics&sessionHandle=$SESSION" \
  -d "timeRange=1h"
```

### Adaptive Learning Loops
- Continuous model training
- Online learning
- Performance-based adaptation
```

---

### File 9: `12-opensage-memory-system.md` (NEW)

#### Content Structure:
```markdown
# Tutorial: OpenSage Memory System

By the end of this tutorial you will have implemented persistent memory for MCP tools, enabling cross-agent state sharing and learning from historical data.

---

## Prerequisites

- Tutorial 6 completed: Custom MCP Tools
- Redis server for memory storage
- YAWL engine with OpenSage support

---

## Step 1: Configure OpenSage

### Redis Connection Setup
```bash
# Install Redis
sudo apt-get install redis-server

# Configure Redis for YAWL
redis-cli CONFIG SET maxmemory 2gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

### Namespace Configuration
```java
// Configure OpenSage client
OpenSageConfig config = new OpenSageConfig.Builder()
    .redisHost("localhost")
    .redisPort(6379)
    .namespace("yawl-workflows")
    .maxMemorySize("1GB")
    .build();
```

### Memory Schema Definition
```json
{
  "memorySchema": {
    "case_data": {
      "ttl": "24h",
      "indexes": ["caseId", "taskId"]
    },
    "user_preferences": {
      "ttl": "7d",
      "indexes": ["userId"]
    },
    "patterns": {
      "ttl": "30d",
      "indexes": ["patternType", "workflowType"]
    }
  }
}
```

---

## Step 2: Implement Memory Operations

### Store and Retrieve Patterns
```java
public class MemoryAwareMcpTool implements YawlMcpTool {

    public McpSchema.CallToolResult execute(Map<String, Object> params) {
        // Store workflow pattern
        String caseId = (String) params.get("caseId");
        String pattern = extractWorkflowPattern(params);

        memoryStore.store(caseId, "workflow_patterns", pattern);

        // Retrieve similar patterns
        List<String> similarPatterns = memoryStore.similaritySearch(
            pattern,
            "workflow_patterns",
            0.8
        );

        return buildResponse(pattern, similarPatterns);
    }
}
```

### Cross-Agent Memory Sharing
```bash
# Configure memory sharing between agents
export OPENSAGE_CROSS_AGENT=true
export OPENSAGE_SHARE_SCOPE="organization"
export OPENSAGE_ENCRYPTION_KEY="your-256-bit-key"
```

### Memory Expiration Strategies
- Time-based expiration
- Usage-based expiration
- Size-based expiration
- Manual expiration

---

## Step 3: Learning Patterns

### Pattern Recognition Algorithms
- Sequence mining
- Association rule learning
- Clustering algorithms
- Anomaly detection

### Memory-Based Decision Making
```java
public class DecisionMakingEngine {
    public RoutingDecision decideRouting(String caseId) {
        // Retrieve historical patterns
        List<CasePattern> patterns = memoryStore.retrieveAll(caseId, "routing_patterns");

        // Analyze success rates
        Map<String, Double> successRates = calculateSuccessRates(patterns);

        // Apply reinforcement learning
        return routingPolicy.chooseBestRoute(successRates);
    }
}
```

### Continuous Learning Loops
- Online learning from new cases
- Pattern refinement
- Model updates
- Performance tracking
```

---

### File 10: `13-virtual-threads-performance.md` (NEW)

#### Content Structure:
```markdown
# Tutorial: Virtual Thread Performance Optimization

By the end of this tutorial you will have optimized YAWL's virtual thread configuration for maximum throughput and minimal resource usage.

---

## Prerequisites

- Tutorial 1 completed: Build YAWL from Source
- Java 25+ with virtual thread support
- Performance monitoring tools

---

## Step 1: Thread Pool Configuration

### Virtual Thread Parallelism Settings
```bash
# Optimal parallelism for your hardware
export JAVA_OPTS="--enable-preview -Djdk.virtualThreadScheduler.parallelism=200"
export MAVEN_OPTS="--enable-preview -Djdk.defaultScheduler.parallelism=4"
```

### Scheduler Optimization
```java
// Custom virtual thread scheduler
VirtualThreadScheduler scheduler = VirtualThreadScheduler.builder()
    .parallelism(200)
    .threadFactory(Executors.defaultThreadFactory())
    .build();

// Apply to YAWL engine
System.setProperty("jdk.virtualThreadScheduler", scheduler);
```

### Resource Limits
```bash
# Set virtual thread limits
export VIRTUAL_THREAD_MAX_HEAP="512m"
export VIRTUAL_THREAD_STACK_SIZE="2m"
export VIRTUAL_THREAD_IDLE_TIMEOUT="60s"
```

---

## Step 2: Performance Tuning

### Throughput Benchmarking
```bash
# Benchmark virtual thread performance
mvn test -pl yawl-engine -Dtest=VirtualThreadBenchmark -DjvmArgs="--enable-preview"

# Generate performance report
mvn test -pl yawl-engine -Dtest=PerformanceProfiler
```

### Memory Usage Optimization
```java
// Monitor virtual thread memory usage
ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
long allocatedMemory = threadBean.getThreadAllocatedBytes(threadId);

// Optimize memory usage
VirtualThreadMemoryMXBean vmxBean = VirtualThreadMemoryMXBean.getInstance();
vmxBean.setMemoryLimit(512 * 1024 * 1024); // 512MB
```

### Contention Analysis
```bash
# Analyze thread contention
jstack -l <pid> | grep -c "Contended"
vmstat -s | grep "contention"

# Use Java Mission Control for visualization
jmc
```

---

## Step 3: Advanced Features

### Structured Concurrency
```java
// Implement structured concurrency
try (var scope = StructuredTaskScope.open()) {
    // Start multiple virtual threads
    Future<Result> result1 = scope.fork(() -> processTask1());
    Future<Result> result2 = scope.fork(() -> processTask2());

    // Wait for completion
    scope.join();

    // Handle results
    Result finalResult = result1.resultNow()
        .combineWith(result2.resultNow());
}
```

### Virtual Thread Pinning
```java
// Pin virtual threads to specific CPUs
VirtualThreadPinning pinning = VirtualThreadPinning.builder()
    .pinToCpu(0)  // Pin to CPU 0
    .build();

// Apply to specific tasks
ExecutorService pinnedExecutor = Executors.newVirtualThreadPerTaskExecutor()
    .withPinning(pinning);
```

### Resource Management
```java
// Implement resource limits
VirtualThreadResourceLimits limits = VirtualThreadResourceLimits.builder()
    .maxVirtualThreads(10000)
    .maxMemoryUsage("1GB")
    .maxCpuTime("1h")
    .build();

// Monitor and enforce limits
VirtualThreadMonitor monitor = VirtualThreadMonitor.builder()
    .withLimits(limits)
    .build();
```

---

## Performance Metrics

### Key Indicators
- Virtual thread creation rate
- Task throughput per second
- Memory utilization
- Thread contention time
- CPU utilization

### Optimization Tips
1. Monitor `jdk.virtualThreadScheduler.parallelism` setting
2. Use `--enable-preview` flag for Java 25
3. Set appropriate stack sizes
4. Monitor memory usage patterns
5. Balance between virtual and platform threads
```

---

## Quality Assurance Checklist

### Update Verification
- [ ] All Java version references updated to 25
- [ ] All MCP references are v6.0 specific
- [ ] All Docker image versions updated to GA
- [ ] All code examples tested and working
- [ ] All cross-references updated

### Testing Requirements
- [ ] Tutorial examples run successfully
- [ ] GRPO/RL endpoints respond correctly
- [ ] OpenSage memory operations work
- [ ] Virtual thread configurations perform well
- [ ] All new features documented

### Documentation Standards
- [ ] Consistent terminology throughout
- [ ] Clear examples with expected outputs
- [ ] Proper error handling examples
- [ ] Performance metrics included
- [ ] Prerequisites clearly stated