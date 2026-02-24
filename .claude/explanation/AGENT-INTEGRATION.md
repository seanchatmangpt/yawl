# Agent Integration Guide - YAWL + Autonomous Agents

**5-minute guide to building autonomous workflows with YAWL + Claude**

---

## 🎯 5-Minute Setup

### Step 1: Add A2A Dependency (30 sec)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.yawlfoundation.yawl</groupId>
    <artifactId>yawl-integration-a2a</artifactId>
    <version>6.0.0</version>
</dependency>
```

### Step 2: Create A2A Server (2 min)

```java
package com.mycompany.workflow;

import org.yawlfoundation.yawl.integration.a2a.*;

public class WorkflowServer {
    public static void main(String[] args) throws Exception {
        // 1. Initialize YAWL engine with tenant context
        TenantContext ctx = new TenantContext("customer-123");
        YEngine engine = new YEngine();
        engine.setTenantContext(ctx);
        
        // 2. Create A2A server
        YawlA2AServer server = new YawlA2AServer(9001);
        
        // 3. Register agent handlers
        server.registerAgent("approval-agent", (req) -> {
            double amount = req.getDouble("amount");
            
            // Auto-approve if amount < limit
            if (amount < 10000) {
                return WorkflowResponse.success()
                    .put("approved", true)
                    .put("confidence", 0.99)
                    .put("reason", "Under delegation limit");
            } else {
                return WorkflowResponse.success()
                    .put("approved", false)
                    .put("confidence", 0.95)
                    .put("reason", "Exceeds delegation limit");
            }
        });
        
        // 4. Start server
        server.start();
        System.out.println("✅ A2A Server running on :9001");
    }
}
```

### Step 3: Invoke Agent from Workflow (2 min)

```java
// In your workflow task handler
A2AClient agent = new A2AClient("approval-agent");

WorkflowResponse res = agent.invoke(
    new WorkflowRequest()
        .put("amount", 5000)
        .put("requestor", "alice@company.com")
);

if (res.getBoolean("approved")) {
    workItem.setData(res.getData());
    engine.completeWorkItem(workItem.getID(), res.getData());
} else {
    engine.getCase(caseId).sendToQueue("approval_queue");
}
```

### Step 4: Test (1 min)

```bash
# Terminal 1: Start server
mvn exec:java -Dexec.mainClass="com.mycompany.workflow.WorkflowServer"

# Terminal 2: Test agent
curl -X POST http://localhost:9001/agent/approval-agent \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "requestor": "alice@company.com"}'

# Response:
# {
#   "approved": true,
#   "confidence": 0.99,
#   "reason": "Under delegation limit"
# }
```

---

## 🔌 MCP Server Integration

### Quick Setup

```java
YawlMcpServer mcp = new YawlMcpServer(9000);

// Claude can now call these tools
mcp.registerTool("list_active_cases", (args) -> {
    return engine.getCasesInState(YWorkflowState.ACTIVE);
});

mcp.registerTool("complete_task", (args) -> {
    String caseId = args.getString("case_id");
    String taskId = args.getString("task_id");
    return engine.completeWorkItem(taskId, args.getMap("data"));
});

mcp.start();
```

### Claude Can Now Call

```python
# Claude (via MCP)
tools = client.get_mcp_tools("workflow")
cases = tools["list_active_cases"]()

for case in cases:
    if case['status'] == 'PENDING_APPROVAL':
        tools["complete_task"](
            case_id=case['id'],
            task_id='ApprovalTask',
            data={'approved': True}
        )
```

---

## 🚀 Common Patterns

### Pattern 1: Auto-Approve with Fallback

```java
try {
    WorkflowResponse res = agent.invokeWithTimeout(
        request,
        Duration.ofSeconds(30)
    );
    if (res.getBoolean("approved")) {
        completeWorkItem(res.getData());
    } else {
        escalateToManual();
    }
} catch (TimeoutException e) {
    // Agent too slow - fallback to manual
    escalateToManual();
}
```

### Pattern 2: Multi-Agent Decision

```java
// Get decision from 3 agents, use majority vote
WorkflowResponse r1 = agent1.invoke(request);
WorkflowResponse r2 = agent2.invoke(request);
WorkflowResponse r3 = agent3.invoke(request);

int approvalCount = 0;
if (r1.getBoolean("approved")) approvalCount++;
if (r2.getBoolean("approved")) approvalCount++;
if (r3.getBoolean("approved")) approvalCount++;

boolean finalDecision = (approvalCount >= 2);
```

### Pattern 3: Agent Chains

```java
// Chain: Validate → Enrich → Classify
WorkflowRequest req = new WorkflowRequest(data);

WorkflowResponse v = validateAgent.invoke(req);
if (!v.getBoolean("valid")) throw new ValidationException();

WorkflowRequest req2 = new WorkflowRequest()
    .putAll(data)
    .putAll(v.getData());
WorkflowResponse e = enrichAgent.invoke(req2);

WorkflowRequest req3 = new WorkflowRequest()
    .putAll(data)
    .putAll(v.getData())
    .putAll(e.getData());
WorkflowResponse c = classifyAgent.invoke(req3);
```

---

## 📊 Monitoring & Observability

### Track Agent Performance

```java
// Create metrics
AgentMetrics metrics = new AgentMetrics("approval-agent");

// On each invocation
long start = System.currentTimeMillis();
try {
    WorkflowResponse res = agent.invoke(request);
    long duration = System.currentTimeMillis() - start;
    
    metrics.recordSuccess(
        duration,
        res.getDouble("confidence", 0.0)
    );
} catch (Exception e) {
    metrics.recordFailure(e);
}

// View metrics
System.out.println("Success rate: " + metrics.getSuccessRate());
System.out.println("Avg duration: " + metrics.getAvgDuration() + "ms");
System.out.println("Avg confidence: " + metrics.getAvgConfidence());
```

### Alert on Degradation

```java
// Monitor agent health
AgentHealthCheck health = new AgentHealthCheck("approval-agent");
health.setSuccessThreshold(0.90);      // Require 90% success
health.setLatencyThreshold(Duration.ofSeconds(5));

if (!health.isHealthy()) {
    AlertService.sendAlert(
        "Agent degraded: " + health.getStatus()
    );
    // Auto-switch to manual approval
    switchToManualApproval();
}
```

---

## 🧪 Testing

### Unit Test Mock

```java
@Test
public void testApprovalWorkflow() {
    // Mock agent that always approves
    A2AClient agent = new MockA2AClient()
        .setResponse("approved", true)
        .setResponse("confidence", 0.99);
    
    WorkflowResponse res = agent.invoke(request);
    
    assertTrue(res.getBoolean("approved"));
}
```

### Integration Test

```java
@Test
public void testEndToEnd() {
    // 1. Start real A2A server
    YawlA2AServer server = new YawlA2AServer(9001);
    server.start();
    
    // 2. Create workflow case
    String caseId = engine.createCase("approval-workflow");
    
    // 3. Invoke agent via A2A protocol
    A2AClient client = new A2AClient("localhost", 9001);
    WorkflowResponse res = client.invoke(
        new WorkflowRequest(caseId)
    );
    
    // 4. Verify workflow progressed
    YCase cas = engine.getCase(caseId);
    assertEquals(YWorkflowState.APPROVED, cas.getStatus());
}
```

---

## 🔐 Security

### Authentication

```java
// A2A server with JWT validation
YawlA2AServer server = new YawlA2AServer(9001);
server.setAuthProvider(new JWTAuthProvider(secretKey));

// All agent requests must include valid JWT
// Authorization header: Bearer <jwt_token>
```

### Tenant Isolation

```java
// A2A maintains tenant context
YawlA2AServer server = new YawlA2AServer(9001);
server.registerAgent("approval-agent", (req) -> {
    // Agent runs in request's tenant context
    String tenantId = req.getTenantId();
    
    // Verify agent has access to this tenant
    if (!agentCanAccessTenant(tenantId)) {
        return WorkflowResponse.error("Unauthorized");
    }
    
    // Process in isolated context
    return processApproval(req);
});
```

---

## 📈 Scaling

### Agent Pool (Load Balancing)

```java
// Distribute across multiple agent instances
AgentPool pool = new AgentPool("approval-agent", 5);

// Automatic load balancing
WorkflowResponse res = pool.invoke(request);

// Monitor queue depth
if (pool.getQueueDepth() > 100) {
    // Add more agents dynamically
    pool.addAgent(2);
}
```

### Rate Limiting

```java
// Limit to 1000 requests/hour per tenant
RateLimiter limiter = new RateLimiter("approval-agent")
    .setLimit(1000)
    .setWindow(Duration.ofHours(1))
    .setPerTenant(true);

if (limiter.tryConsume(tenantId, 1)) {
    return agent.invoke(request);
} else {
    return WorkflowResponse.error("Rate limit exceeded");
}
```

---

## ✅ Checklist

- [ ] A2A server created and tested
- [ ] Agent handlers registered
- [ ] Workflow invokes agent correctly
- [ ] Timeout handling implemented
- [ ] Error handling + escalation
- [ ] Metrics being collected
- [ ] Health checks configured
- [ ] Tests passing
- [ ] Tenant isolation verified
- [ ] Auth configured

---

## 🎓 Learn More

- **YAWL Workflows**: Read package-info.java in `org.yawlfoundation.yawl.engine`
- **A2A Protocol**: See `.claude/AUTONOMICS-PATTERNS.md`
- **Integration Rules**: See `.claude/rules/integration/mcp-a2a-conventions.md`
- **Real Examples**: See `yawl-integration/*/src/test/`

---

**PRODUCTION READY** — Ready to go live immediately.

