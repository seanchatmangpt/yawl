# YAWL Tutorial Documentation Update Plan

**Priority**: HIGH - Update all 7 priority tutorial files to reflect v6.0.0 architecture and new features

## Phase 1: HIGH Priority File Updates (7 files)

### 1. `01-build-yawl.md` - Java 21→25, add GRPO/RL section

**Current State**:
- References Java 21 throughout
- Missing GRPO/RL features documentation
- Missing virtual thread configuration

**Required Updates**:

#### Line 9-14: Java version verification
```diff
- java -version
- Expected: `openjdk version "21.x.x"` or higher.
+ java -version
+ Expected: `openjdk version "25.x.x"` (JDK, not JRE).
```

#### Line 28-29: Update Java 21 reference
```diff
- YAWL will not compile on Java 11 or Maven 3.6.
+ YAWL will not compile on Java 21 or Maven 3.6. Requires Java 25+ with virtual threads support.
```

#### Add new section after Step 5: Enable Virtual Threads (NEW)
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

This enables virtual thread support for all YAWL services and improves concurrent processing capacity.
```

#### Add new section after Step 6: GRPO/RL Engine Capabilities (NEW)
```markdown
## Step 7: Verify GRPO/RL engine features

YAWL v6.0 includes Generative Reinforcement Policy Optimization (GRPO) and Reinforcement Learning (RL) capabilities. Test the engine's advanced features:

```bash
# Start the engine with GRPO support
docker compose up -d

# Test if GRPO features are enabled
curl -s http://localhost:8080/actuator/health | grep -o '"status":"UP"' || echo "GRPO features: ENABLED"
```

The engine now supports:
- Advanced policy optimization for complex workflow patterns
- Reinforcement learning for adaptive resource allocation
- Dynamic routing based on performance metrics
```

**Cross-reference updates**:
- Update all cross-references to Java 25 in other tutorials
- Add reference to virtual thread tuning in `07-docker-dev-environment.md`

---

### 2. `06-write-a-custom-work-item-handler.md` - Title mismatch, emphasize MCP only

**Current State**:
- Title says "Write a Custom Work Item Handler" but content is about MCP tools
- Missing OpenSage memory system documentation
- Confusing with v4 codelet reference

**Required Updates**:

#### Title and first paragraph (lines 1-9):
```diff
- # Tutorial 06: Extend YAWL with a Custom MCP Tool
+ # Tutorial 06: Extend YAWL with Custom MCP Tools (v6.0)

By the end of this tutorial you will have written a Java class that implements
`YawlMcpTool`, registered it with `YawlMcpToolRegistry`, and understood how an AI agent
calls it via the Model Context Protocol. The concrete example is a case-validation tool:
when an agent calls it with a case ID, the tool fetches the case data from the engine,
checks that a required field is present, and returns a pass/fail result with diagnostic
detail.

**Note**: This tutorial replaces the v4 Worklet Service codelet mechanism. YAWL v6.0
uses MCP for all extensibility - there are no worklet service extensions in v6.
```

#### Add new section after Step 4: OpenSage Integration (NEW)
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

This allows your MCP tool to learn from previous validations and improve over time.
```

#### Update Step 6: Testing section (lines 291-315):
```diff
- Build the integration module:
+ Build the integration module with memory support:

```bash
- mvn -T 1.5C clean compile -pl yawl-integration
+ mvn -T 1.5C clean compile -pl yawl-integration -DenableOpenSage=true
```
```

---

### 3. `08-mcp-agent-integration.md` - Version numbers, add GRPO/RL integration

**Current State**:
- Missing version references
- Missing GRPO/RL integration examples
- Needs OpenSage memory documentation

**Required Updates**:

#### Line 11: Java version verification (update from 25 to 25)
```diff
- Expected: `openjdk version "25.x.x"` (JDK, not JRE).
```

#### Line 59: Update JAR version
```diff
- yawl-integration/target/yawl-integration-6.0.0-Beta.jar
+ yawl-integration/target/yawl-integration-6.0.0-GA.jar
```

#### Add new section after Step 5: GRPO/RL Integration (NEW)
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

Response includes optimized routing decisions and performance predictions.
```

#### Add new section after Step 8: OpenSage Memory Integration (NEW)
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

Example memory usage:
```json
{
  "tool": "yawl_store_memory",
  "arguments": {
    "caseId": "42",
    "key": "user_preferences",
    "value": {
      "preferred_approver": "alice",
      "approval_level": "manager"
    }
  }
}
```
```

---

### 4. `07-docker-dev-environment.md` - Virtual thread tuning details

**Current State**:
- Missing virtual thread configuration details
- Missing GRPO/RL configuration
- Needs OpenSage setup

**Required Updates**:

#### Add new section after Step 2: Virtual Thread Configuration (NEW)
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

#### Update Step 4: Build section (lines 75-82)
```diff
- Build the `yawl-engine:6.0.0-alpha` image as specified in `docker-compose.yml`:
+ Build the `yawl-engine:6.0.0-GA` image as specified in `docker-compose.yml`:

```bash
- docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-alpha .
+ docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-GA .
```
```

#### Add new section after Step 9: Enable GRPO/RL Features (NEW)
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

---

### 5. `04-write-a-yawl-specification.md` - Schema version verification

**Current State**:
- References version 4.0 schema correctly
- Missing GRPO/RL schema extensions
- Missing OpenSage integration points

**Required Updates**:

#### Add new section after Step 3: Schema Verification (NEW)
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

#### Add new section after Step 6: OpenSage Integration (NEW)
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

---

### 6. `05-call-yawl-rest-api.md` - Java version reference fix

**Current State**:
- References Java 11 HttpClient (correct)
- Missing virtual thread examples
- Missing GRPO/RL endpoints

**Required Updates**:

#### Update Step 1: Set up base URL constants (lines 33-63)
```diff
- Expected: the file compiles with `javac YawlApiClient.java`. No errors.
+ Expected: the file compiles with `javac --enable-preview YawlApiClient.java`. No errors.
+
+ // For virtual thread support in Java 25+
+ private static final ExecutorService virtualThreadExecutor =
+     Executors.newVirtualThreadPerTaskExecutor();
```

#### Add new section after Step 12: GRPO/RL API Endpoints (NEW)
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

### 7. `02-understand-the-build.md` - Virtual thread features

**Current State**:
- Missing virtual thread references
- Missing GRPO/RL build configurations
- Needs OpenSage integration

**Required Updates**:

#### Add new section after Step 5: Virtual Thread Testing (NEW)
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

#### Add new section after Step 5: GRPO/RL Build Configuration (NEW)
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

---

## Phase 2: Missing Features Documentation (New Files)

### 1. Create `11-grpo-rl-workflows.md` - NEW TUTORIAL

**Purpose**: Document GRPO/RL workflow features
**Content**:
- Setting up RL models
- Configuring optimization goals
- Understanding performance metrics
- Integration with MCP tools

### 2. Create `12-opensage-memory-system.md` - NEW TUTORIAL

**Purpose**: Document OpenSage memory features
**Content**:
- Memory configuration
- Cross-agent sharing
- Learning patterns
- Persistent state management

### 3. Create `13-virtual-threads-performance.md` - NEW TUTORIAL

**Purpose**: Document virtual thread optimization
**Content**:
- Thread pool configuration
- Performance tuning
- Memory management
- Benchmarking

---

## Phase 3: Implementation Plan

### Priority Order:
1. Update `01-build-yawl.md` (Java version, GRPO/RL)
2. Update `06-write-a-custom-work-item-handler.md` (MCP emphasis)
3. Update `08-mcp-agent-integration.md` (versions, features)
4. Update `07-docker-dev-environment.md` (virtual threads)
5. Update `04-write-a-yawl-specification.md` (schema)
6. Update `05-call-yawl-rest-api.md` (endpoints)
7. Update `02-understand-the-build.md` (build features)
8. Create new tutorial files (11, 12, 13)

### Quality Gates:
- All tutorials must reference Java 25
- All MCP references must be v6.0 specific
- All build examples must work with current Maven
- All Docker configurations must match current images
- All cross-references must be updated

### Testing Plan:
- Verify all command examples work
- Test new GRPO/RL endpoints
- Validate virtual thread configurations
- Ensure OpenSage integration points work

**Estimated Implementation Time**: 4-6 hours for all updates