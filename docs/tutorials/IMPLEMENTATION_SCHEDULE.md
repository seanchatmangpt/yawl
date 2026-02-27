# YAWL Tutorial Implementation Schedule

## Implementation Sequence (Prioritized)

### Phase 1: Critical Updates (Hours 1-3)

#### 1. `01-build-yawl.md` - HIGH Priority (45 min)

**Target**: Update Java references and add GRPO/RL section

**Specific Edits**:

1. **Line 15**: Java version update
   ```diff
   - Expected: `openjdk version "21.x.x"` or higher.
   + Expected: `openjdk version "25.x.x"` (JDK, not JRE).
   ```

2. **Line 29**: Version constraint update
   ```diff
   - YAWL will not compile on Java 11 or Maven 3.6.
   + YAWL will not compile on Java 21 or Maven 3.6. Requires Java 25+ with virtual threads support.
   ```

3. **Add after Step 5 (new section)**: Virtual threads configuration
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

4. **Add after Step 6 (new section)**: GRPO/RL verification
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

**Dependencies**: None
**Cross-references**: Update all Java version references in other tutorials

---

#### 2. `06-write-a-custom-work-item-handler.md` - HIGH Priority (45 min)

**Target**: Fix title mismatch and emphasize MCP only

**Specific Edits**:

1. **Title and intro (lines 1-9)**: Update title and clarify v6 changes
   ```diff
   - # Tutorial 06: Extend YAWL with a Custom MCP Tool
   + # Tutorial 06: Extend YAWL with Custom MCP Tools (v6.0)

   By the end of this tutorial you will have written a Java class that implements
   `YawlMcpTool`, registered it with `YawlMcpToolRegistry`, and understood how an AI agent
   calls it via the Model Context Protocol. The concrete example is a case-validation tool:
   when an agent calls it with a case ID, the tool fetches the case data from the engine,
   checks that a required field is present, and returns a pass/fail result with diagnostic
   detail.

   + **Note**: This tutorial replaces the v4 Worklet Service codelet mechanism. YAWL v6.0
   + uses MCP for all extensibility - there are no worklet service extensions in v6.
   ```

2. **Add after Step 4 (new section)**: OpenSage integration
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

3. **Update Step 6 (line 296)**: Build command with OpenSage
   ```diff
   - mvn -T 1.5C clean compile -pl yawl-integration
   + mvn -T 1.5C clean compile -pl yawl-integration -DenableOpenSage=true
   ```

**Dependencies**: Updates to 08-mcp-agent-integration.md
**Cross-references**: Reference new OpenSage documentation

---

#### 3. `08-mcp-agent-integration.md` - HIGH Priority (60 min)

**Target**: Update version numbers and add GRPO/RL integration

**Specific Edits**:

1. **Line 16**: Java version consistency
   ```diff
   - Expected: `openjdk version "25.x.x"` (JDK, not JRE).
   ```

2. **Line 59**: Update JAR version
   ```diff
   - yawl-integration/target/yawl-integration-6.0.0-Beta.jar
   + yawl-integration/target/yawl-integration-6.0.0-GA.jar
   ```

3. **Add after Step 5 (new section)**: GRPO/RL integration
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

4. **Add after Step 8 (new section)**: OpenSage memory integration
   ```markdown
   ## Step 9: Leverage OpenSage persistent memory

   OpenSage provides cross-agent memory sharing between YAWL and connected AI agents.

   Configure memory sharing:

   ```bash
   export OPENSAGE_REDIS_HOST=localhost
   export OPENSAGE_REDIS_PORT=6379
   export OPENSAGE_NAMESPACE=yawl-agents
   ```
   ```

**Dependencies**: Depends on 01-build-yawl.md updates
**Cross-references**: Link to OpenSage documentation

---

### Phase 2: Remaining Priority Updates (Hours 3-5)

#### 4. `07-docker-dev-environment.md` - HIGH Priority (30 min)

**Target**: Add virtual thread tuning details

**Specific Edits**:

1. **Add after Step 2 (new section)**: Virtual thread configuration
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
   ```

2. **Line 81**: Update image version
   ```diff
   - docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-alpha .
   + docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-GA .
   ```

3. **Add after Step 9 (new section)**: GRPO/RL configuration
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
   ```

**Dependencies**: Depends on 01-build-yawl.md updates

---

#### 5. `04-write-a-yawl-specification.md` - MEDIUM Priority (25 min)

**Target**: Add schema verification and GRPO extensions

**Specific Edits**:

1. **Add after Step 3 (new section)**: GRPO schema verification
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
   ```

2. **Add after Step 6 (new section)**: OpenSage memory hooks
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
   ```

**Dependencies**: None

---

#### 6. `05-call-yawl-rest-api.md` - MEDIUM Priority (25 min)

**Target**: Update Java version reference and add GRPO endpoints

**Specific Edits**:

1. **Line 64**: Update compilation command
   ```diff
   - Expected: the file compiles with `javac YawlApiClient.java`. No errors.
   + Expected: the file compiles with `javac --enable-preview YawlApiClient.java`. No errors.
   ```

2. **Add after Step 12 (new section)**: GRPO/RL endpoints
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
   ```
   ```

**Dependencies**: None

---

#### 7. `02-understand-the-build.md` - MEDIUM Priority (20 min)

**Target**: Add virtual thread and GRPO build features

**Specific Edits**:

1. **Add after Step 5 (new section)**: Virtual thread testing
   ```markdown
   ## Step 6: Test virtual thread performance

   YAWL v6.0 uses virtual threads for improved concurrency. Test that virtual threads are working:

   ```bash
   # Test with virtual thread support
   mvn test -pl yawl-engine -DjavaVirtualThread.enabled=true
   ```
   ```

2. **Add after Step 5 (new section)**: GRPO build configuration
   ```markdown
   ## Step 7: Configure GRPO/RL build

   Add these profiles to enable AI features:

   ```bash
   # Build with GRPO/RL support
   mvn clean package -Pgrpo,rl -DenableOpenSage=true
   ```
   ```

**Dependencies**: Depends on 01-build-yawl.md updates

---

### Phase 3: New Tutorial Files (Hours 5-6)

#### 8. `11-grpo-rl-workflows.md` - NEW (30 min)

**Outline**:
```markdown
# Tutorial: Advanced GRPO/RL Workflows

By the end of this tutorial you will have configured and tested YAWL's advanced AI features including reinforcement learning models, policy optimization, and performance analytics.

## Prerequisites
- Tutorial 8 completed: MCP Agent Integration
- YAWL engine with GRPO/RL support
- Z.AI API key for model training

## Step 1: Configure RL Models
- Model selection and training
- Hyperparameter optimization
- Performance metrics

## Step 2: Set Optimization Goals
- Throughput vs latency tradeoffs
- Constraint-based optimization
- Multi-objective optimization

## Step 3: Monitor Performance
- Real-time metrics collection
- Performance analytics
- Adaptive learning loops
```

#### 9. `12-opensage-memory-system.md` - NEW (30 min)

**Outline**:
```markdown
# Tutorial: OpenSage Memory System

By the end of this tutorial you will have implemented persistent memory for MCP tools, enabling cross-agent state sharing and learning from historical data.

## Prerequisites
- Tutorial 6 completed: Custom MCP Tools
- Redis server for memory storage
- YAWL engine with OpenSage support

## Step 1: Configure OpenSage
- Redis connection setup
- Namespace configuration
- Memory schema definition

## Step 2: Implement Memory Operations
- Store and retrieve patterns
- Cross-agent memory sharing
- Memory expiration strategies

## Step 3: Learning Patterns
- Pattern recognition algorithms
- Memory-based decision making
- Continuous learning loops
```

#### 10. `13-virtual-threads-performance.md` - NEW (30 min)

**Outline**:
```markdown
# Tutorial: Virtual Thread Performance Optimization

By the end of this tutorial you will have optimized YAWL's virtual thread configuration for maximum throughput and minimal resource usage.

## Prerequisites
- Tutorial 1 completed: Build YAWL from Source
- Java 25+ with virtual thread support
- Performance monitoring tools

## Step 1: Thread Pool Configuration
- Virtual thread parallelism settings
- Scheduler optimization
- Resource limits

## Step 2: Performance Tuning
- Throughput benchmarking
- Memory usage optimization
- Contention analysis

## Step 3: Advanced Features
- Structured concurrency
- Virtual thread pinning
- Resource management
```

---

## Implementation Checklist

### Before Starting:
- [ ] Run `bash scripts/observatory/observatory.sh --facts` to ensure facts are current
- [ ] Verify all Docker images are built with latest changes
- [ ] Test GRPO/RL endpoints are accessible
- [ ] Confirm OpenSage memory system is operational

### During Implementation:
- [ ] Update all Java version references consistently
- [ ] Ensure all code examples compile successfully
- [ ] Update cross-references between tutorials
- [ ] Verify all new sections add value without duplication

### After Implementation:
- [ ] Run full tutorial test suite with updated examples
- [ ] Verify GRPO/RL features work as documented
- [ ] Test virtual thread configurations
- [ ] Validate OpenSage integration points
- [ ] Check all cross-links between tutorials

## Success Metrics

1. **All 7 priority files updated** with accurate version references
2. **3 new tutorial files created** covering missing features
3. **All code examples tested** and working
4. **Cross-references consistent** across all tutorials
5. **Documentation reflects v6.0-GA capabilities**

**Total Estimated Time**: 6 hours
**Critical Path**: 01-build-yawl.md → 08-mcp-agent-integration.md → 06-write-a-custom-work-item-handler.md