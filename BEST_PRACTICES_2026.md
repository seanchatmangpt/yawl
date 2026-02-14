# MCP and A2A Integration Best Practices (2026)

This document contains bleeding-edge best practices for integrating Model Context Protocol (MCP) and Agent-to-Agent (A2A) protocols with YAWL, based on February 2026 research and implementation patterns.

## Table of Contents

- [MCP Best Practices](#mcp-best-practices)
- [A2A Best Practices](#a2a-best-practices)
- [Security Patterns](#security-patterns)
- [Resilience Patterns](#resilience-patterns)
- [Performance Optimization](#performance-optimization)
- [Observability](#observability)
- [Production Deployment](#production-deployment)

---

## MCP Best Practices

### Architecture Patterns (2026)

**Reactive Streams with Sync Facade**
```
┌─────────────────────────────────────┐
│   Client/Server Layer               │
│   (McpClient/McpServer)             │
├─────────────────────────────────────┤
│   Session Layer (McpSession)        │
│   Sync/Async Operations             │
├─────────────────────────────────────┤
│   Transport Layer                   │
│   STDIO | SSE | Streamable HTTP     │
├─────────────────────────────────────┤
│   JSON Serialization (Jackson)      │
└─────────────────────────────────────┘
```

### Tool Definition Best Practices

**✅ DO:**
- Use `verb_resource` naming (e.g., `start_workflow`, `get_status`)
- Keep schemas flat, avoid deep nesting
- Provide detailed parameter descriptions
- Include examples for each tool
- Version tools explicitly (v1, v2)
- Support semantic versioning

**❌ DON'T:**
- Use abbreviations or ambiguous terms
- Deeply nest schema objects
- Skip required field validation
- Omit documentation
- Break backward compatibility

**Example:**
```java
@Tool(
    name = "start_workflow",  // snake_case
    description = "Initiates a new YAWL workflow instance from a specification",
    version = "v1"
)
public ToolResult startWorkflow(
    @Parameter(description = "Unique identifier of the workflow specification")
    String workflowId,

    @Parameter(description = "Initial data parameters for the workflow")
    Map<String, Object> inputData
) {
    // Implementation
}
```

### Resource Management

**Three Core Primitives:**
1. **Tools** - Model-controlled actions (doing)
2. **Resources** - Application-driven context (knowledge)
3. **Prompts** - Reusable templates (consistency)

**Resource Pattern:**
```java
@Resource(
    uri = "yawl://workflows/{workflowId}",
    name = "Workflow Instance Details",
    description = "Structured context for workflow execution",
    cacheTTL = Duration.ofMinutes(15)  // 15-minute self-cleaning cache
)
public ResourceContent getWorkflow(@PathVariable String workflowId) {
    WorkflowContext context = WorkflowContext.builder()
        .currentState(workflowService.getState(workflowId))
        .availableActions(workflowService.getAvailableActions(workflowId))
        .executionHistory(workflowService.getHistory(workflowId, 10))
        .metadata(workflowService.getMetadata(workflowId))
        .build();

    return ResourceContent.json(context);
}
```

### Transport Selection (2026)

| Transport | Use Case | Pros | Cons |
|-----------|----------|------|------|
| **Streamable HTTP** | Production (recommended) | Modern, SSE streaming, firewall-friendly | Newer spec |
| **STDIO** | Local process communication | Lowest latency, simple | Local only |
| **gRPC** | High-performance microservices | Fast, efficient, bi-directional | Complex setup |

**Recommendation:** Start with Streamable HTTP for production, STDIO for development.

### Security (OAuth 2.1)

**Critical Requirements:**
1. ✅ OAuth 2.1 with PKCE (recommended)
2. ✅ TLS 1.3 for all communications
3. ✅ Explicit user consent before tool invocation
4. ✅ Fine-grained authorization (read vs write scopes)
5. ✅ Token isolation (never share between servers)
6. ✅ Short-lived tokens
7. ✅ Managed secrets (vault, not inline configs)
8. ✅ Input/output validation

**Token Validation:**
```java
@Bean
public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withJwkSetUri(jwkSetUri)
        .build();

    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        new JwtClaimValidator<>("aud", aud ->
            aud.contains("mcp-server-yawl"))
    ));

    return decoder;
}
```

### Context Engineering (2026 Paradigm)

**Shift:** From "prompt engineering" to "context engineering"

Context becomes modular and addressable:
```java
@Resource(uri = "workflow://context/{caseId}")
public class WorkflowContextResource {
    public ResourceContent getContext(String caseId) {
        return ResourceContent.json(
            WorkflowContext.builder()
                .currentState(...)
                .availableTransitions(...)
                .executionHistory(...)
                .metadata(...)
                .build()
        );
    }
}
```

### Latest Features (2025-11-25 Spec)

1. **Asynchronous Tasks** (Experimental)
   - Return task handle immediately
   - Client polls for completion

2. **Client ID Metadata Documents (CIMD)**
   - Simplified client registration
   - Eliminates Dynamic Client Registration complexity

3. **Enhanced OAuth Support**
   - OpenID Connect Discovery 1.0
   - Enterprise-Managed Authorization

4. **Standardized Tool Naming (SEP-986)**
   - Single canonical format
   - Consistent casing/namespace conventions

---

## A2A Best Practices

### Architecture Patterns

**Hub-and-Spoke (Recommended for YAWL)**
```
┌─────────────┐
│  Central    │
│Orchestrator ├──→ Agent 1
│   (YAWL)    ├──→ Agent 2
└─────────────┘
    ├──→ Agent 3
    └──→ Agent 4
```
- Simplified debugging
- Strong consistency
- Proven pattern (Northwestern Mutual)
- Best for: Compliance-heavy workflows

**Mesh Architecture**
```
Agent 1 ←──→ Agent 2
   ↕           ↕
Agent 3 ←──→ Agent 4
```
- Resilient to failure
- Direct agent communication
- Best for: Distributed systems

### AgentCard Design

**Well-Known URI Pattern (Recommended):**
```
https://{agent-domain}/.well-known/agent-card.json
```

**AgentCard Best Practices:**
```java
AgentCard card = AgentCard.builder()
    // Basic info
    .name("YAWL Workflow Engine")
    .version("5.2")
    .serviceUrl("https://yawl.example.com")

    // Capabilities
    .capabilities(Capabilities.builder()
        .streaming(true)
        .pushNotifications(true)
        .stateTransitionHistory(true)
        .inputModalities(Arrays.asList("application/json", "application/xml"))
        .outputModalities(Arrays.asList("application/json"))
        .build())

    // Skills with detailed schemas
    .skills(Arrays.asList(
        Skill.builder()
            .id("startWorkflow")
            .inputSchema(jsonSchema)
            .outputSchema(jsonSchema)
            .examples(examples)
            .build()
    ))

    // Multi-layered security
    .securitySchemes(Arrays.asList(
        oauth2Scheme,
        apiKeyScheme,
        mutualTLSScheme
    ))

    // Domain-specific extensions
    .extensions(Arrays.asList(
        yawlWorkflowExtension
    ))

    .build();
```

### AgentExecutor Pattern (2026)

**Modern Implementation:**
```java
public class YawlAgentExecutor extends AgentExecutor {

    // Pattern 1: Request/Response
    @Override
    public async void onMessageSend(RequestContext context, EventQueue queue) {
        Result result = executeSkill(context.getSkill(), context.getMessage());
        queue.sendMessage(Message.builder()
            .content(result.toJson())
            .build());
    }

    // Pattern 2: Streaming (SSE)
    @Override
    public async void onMessageStream(RequestContext context, EventQueue queue) {
        // Create task
        Task task = createTask(context);
        taskStore.save(task);
        queue.sendTask(task);

        // Stream progress updates
        executeWithProgress(context, new ProgressListener() {
            public void onProgress(int percent) {
                queue.sendStatusUpdate(
                    TaskStatusUpdateEvent.builder()
                        .taskId(task.getId())
                        .status(TaskStatus.RUNNING)
                        .progress(percent)
                        .build()
                );
            }

            public void onComplete(Result result) {
                queue.sendStatusUpdate(
                    TaskStatusUpdateEvent.builder()
                        .taskId(task.getId())
                        .status(TaskStatus.COMPLETED)
                        .final(true)
                        .result(result)
                        .build()
                );
            }
        });
    }

    // Pattern 3: Cancellation
    @Override
    public async void onCancel(RequestContext context, EventQueue queue) {
        String taskId = context.getTaskId();
        Task task = taskStore.get(taskId);

        // Validate context
        if (!context.getContextId().equals(task.getContextId())) {
            queue.sendError(Error.builder()
                .code("CONTEXT_MISMATCH")
                .build());
            return;
        }

        // Cancel task
        cancelTask(taskId);
        queue.sendStatusUpdate(
            TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .status(TaskStatus.CANCELLED)
                .final(true)
                .build()
        );
    }

    // Pattern 4: Resubscription (for reconnections)
    @Override
    public async void onResubscribe(RequestContext context, EventQueue queue) {
        String taskId = context.getTaskId();
        Task task = taskStore.get(taskId);

        // Resend current state
        queue.sendTask(task);

        // Resume streaming if still running
        if (task.getStatus() == TaskStatus.RUNNING) {
            resumeStreaming(task, queue);
        }
    }
}
```

### Transport Protocol Selection

**Comparison:**
| Protocol | Best For | Performance | Compatibility |
|----------|----------|-------------|---------------|
| JSON-RPC 2.0 | New implementations | Medium | Excellent |
| gRPC | High-performance | High | Good |
| HTTP+JSON | RESTful environments | Medium | Excellent |

**Multi-Transport Support:**
```java
// Support all three transports
A2AServer jsonRpc = builder().withJsonRpcTransport(8080).build();
A2AServer grpc = builder().withGrpcTransport(9090).build();
A2AServer http = builder().withHttpJsonTransport(8081).build();
```

### Discovery Mechanisms

**Three Approaches:**

1. **Well-Known URI** (Recommended for public agents)
   ```java
   AgentCard card = fetchAgentCard(
       "https://agent.example.com/.well-known/agent-card.json"
   );
   ```

2. **Registry-Based**
   ```java
   List<AgentCard> agents = registry.search(
       AgentQuery.builder()
           .skill("documentProcessing")
           .tag("production")
           .build()
   );
   ```

3. **Static/Proprietary**
   ```java
   AgentCard card = loadFromConfig("agents.yaml");
   ```

---

## Security Patterns

### Authentication Workflow (3-Step Pattern)

1. **Discovery of Requirements**
   ```java
   AgentCard card = fetchAgentCard(agentUrl);
   SecurityScheme authScheme = card.getSecuritySchemes().get(0);
   ```

2. **Credential Acquisition** (Out-of-Band)
   ```java
   if ("oauth2".equals(authScheme.getType())) {
       accessToken = obtainOAuth2Token(authScheme);
   }
   ```

3. **Credential Transmission**
   ```java
   client = new ClientBuilder()
       .withAuthenticator(new BearerTokenAuthenticator(accessToken))
       .build();
   ```

### Role-Based Access Control (RBAC)

```java
@Override
public boolean authorize(RequestContext context, String skill) {
    String userId = context.getAuthentication().getUserId();
    Set<String> roles = getUserRoles(userId);

    Map<String, Set<String>> skillPermissions = Map.of(
        "startWorkflow", Set.of("workflow_user", "workflow_admin"),
        "cancelWorkflow", Set.of("workflow_admin"),
        "getWorkflowStatus", Set.of("workflow_user", "workflow_admin", "viewer")
    );

    Set<String> requiredRoles = skillPermissions.get(skill);
    return roles.stream().anyMatch(requiredRoles::contains);
}
```

### Security Checklist

- [ ] TLS 1.3 for all communications
- [ ] OAuth 2.1 with PKCE
- [ ] Token validation (audience, issuer, expiration)
- [ ] Fine-grained authorization (RBAC)
- [ ] Input validation and sanitization
- [ ] Output encoding
- [ ] Security audit logging
- [ ] Rate limiting
- [ ] Secret management (vault)
- [ ] Regular security audits

---

## Resilience Patterns

### Circuit Breaker Pattern

```java
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)           // Open after 50% failures
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        return CircuitBreakerRegistry.of(config);
    }
}
```

### Retry Strategy (Exponential Backoff)

```java
private long calculateBackoffDelay(long baseDelay, int attempt, String retryAfter) {
    // Respect server's Retry-After header
    if (retryAfter != null) {
        return parseRetryAfter(retryAfter);
    }

    // Exponential backoff: baseDelay * 2^attempt
    long exponentialDelay = baseDelay * (long) Math.pow(2, attempt - 1);

    // Add jitter to prevent thundering herd
    long jitter = (long) (Math.random() * exponentialDelay * 0.1);

    // Cap at maximum delay
    return Math.min(exponentialDelay + jitter, maxDelay);
}
```

### Error Classification

**Transient Errors (Retry):**
- `TIMEOUT`
- `NETWORK_ERROR`
- `SERVICE_UNAVAILABLE`
- `TOO_MANY_REQUESTS`
- `TEMPORARY_FAILURE`

**Fatal Errors (Don't Retry):**
- `INVALID_CREDENTIALS`
- `PERMISSION_DENIED`
- `INVALID_REQUEST`
- `NOT_FOUND`
- `CONTEXT_MISMATCH`

### Long-Running Task Recovery

```java
// Resubscribe after connection loss
public void resubscribeToTask(String taskId) {
    Task task = taskStore.get(taskId);

    if (task == null || task.isFinal()) {
        return;  // Nothing to resubscribe to
    }

    try {
        client.resubscribe(taskId, new StreamListener() {
            public void onTask(Task resumedTask) {
                taskStore.update(resumedTask);
            }

            public void onStatusUpdate(TaskStatusUpdateEvent event) {
                taskStore.updateStatus(taskId, event);
            }
        });
    } catch (Exception e) {
        // Schedule retry
        scheduleResubscription(taskId, Duration.ofSeconds(5));
    }
}
```

---

## Performance Optimization

### Transport Optimization

**gRPC for Performance (2026):**
- Eliminates translation layers
- Significant performance boost over HTTP
- Reduces latency and improves throughput

**Connection Pooling:**
```java
McpServer server = McpServer.builder()
    .transport(StreamableHttpTransport.builder()
        .maxConnections(100)
        .connectionTimeout(Duration.ofSeconds(5))
        .build())
    .build();
```

### Caching Strategy

**15-Minute Self-Cleaning Cache:**
```java
@Bean
public Cache resourceCache() {
    return CaffeineCache.builder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(15))
        .recordStats()
        .build();
}
```

### Virtual Threads (Java 21+)

```java
// Massive concurrency with virtual threads
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

CompletableFuture<Result> future = CompletableFuture.supplyAsync(
    () -> executeWorkflow(workflowId),
    executor
);
```

### Batch Operations

```java
// Batch multiple tool calls
public List<ToolResult> batchExecute(List<ToolRequest> requests) {
    return requests.parallelStream()
        .map(this::executeTool)
        .collect(Collectors.toList());
}
```

### Location Optimization

- Physical proximity matters
- US data centers: 100-300ms lower latency vs EU/Asia
- Consider multi-region deployments

---

## Observability

### Metrics (Prometheus)

```java
@Aspect
@Component
public class MetricsAspect {

    @Around("@annotation(tool)")
    public Object measureToolInvocation(ProceedingJoinPoint pjp, Tool tool) {
        Timer.Sample sample = Timer.start(registry);

        try {
            Object result = pjp.proceed();

            sample.stop(Timer.builder("mcp.tool.invocation")
                .tag("tool", tool.name())
                .tag("status", "success")
                .register(registry));

            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("mcp.tool.invocation")
                .tag("tool", tool.name())
                .tag("status", "error")
                .tag("error", e.getClass().getSimpleName())
                .register(registry));
            throw e;
        }
    }
}
```

### Distributed Tracing (OpenTelemetry)

```java
@Bean
public Tracer tracer() {
    return OpenTelemetry.getGlobalTracerProvider()
        .get("yawl-integration", "5.2");
}

// Trace tool invocations
Span span = tracer.spanBuilder("tool.invoke")
    .setAttribute("tool.name", toolName)
    .startSpan();

try (Scope scope = span.makeCurrent()) {
    // Execute tool
} finally {
    span.end();
}
```

### Structured Logging

```java
logger.info("Tool invoked",
    kv("tool", toolName),
    kv("user", userId),
    kv("duration", duration),
    kv("status", "success")
);
```

### Audit Logging

```java
public void auditToolInvocation(Authentication auth, String tool, ToolRequest request) {
    auditLog.info(
        "user={} tool={} request={} timestamp={}",
        auth.getName(),
        tool,
        sanitize(request),
        Instant.now()
    );
}
```

---

## Production Deployment

### Kubernetes Configuration

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-integration
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
      - name: yawl-server
        image: yawl-integration:5.2
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-integration-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: yawl-integration
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### Health Checks

```java
@Component
public class McpHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        if (mcpServer.isRunning()) {
            return Health.up()
                .withDetail("server", "running")
                .withDetail("tools", mcpServer.getTools().size())
                .withDetail("uptime", mcpServer.getUptime())
                .build();
        } else {
            return Health.down()
                .withDetail("server", "stopped")
                .build();
        }
    }
}
```

### Configuration Management

```yaml
# application.yml
mcp:
  server:
    name: yawl-mcp-server
    version: 5.2.0
    port: 3000
    transport: streamable-http

  security:
    oauth2:
      issuer-uri: https://auth.example.com
      audience: mcp-server-yawl
      required-scopes:
        - mcp.tools
        - mcp.resources

  performance:
    connection-pool-size: 100
    request-timeout: 30s
    cache-ttl: 15m

  resilience:
    circuit-breaker:
      failure-rate-threshold: 50
      wait-duration: 30s
    retry:
      max-attempts: 3
      backoff-delay: 2s

a2a:
  server:
    name: YAWL Workflow Engine
    version: 5.2
    json-rpc-port: 8080
    grpc-port: 9090

  security:
    oauth2:
      issuer-uri: https://auth.example.com
      audience: a2a-server-yawl
```

---

## Quick Reference

### MCP Tool Pattern
```java
@Tool(name = "verb_resource", version = "v1")
public ToolResult action(@Parameter String param) {
    return ToolResult.success(data);
}
```

### A2A Skill Pattern
```java
AgentCard.builder()
    .skills(List.of(
        Skill.builder()
            .id("actionName")
            .inputSchema(jsonSchema)
            .outputSchema(jsonSchema)
            .build()
    ))
    .build();
```

### Security Pattern
```java
OAuth2 + TLS 1.3 + RBAC + Input Validation
```

### Resilience Pattern
```java
Circuit Breaker + Retry + Timeout + Fallback
```

### Observability Pattern
```java
Metrics + Tracing + Logging + Audit
```

---

## References

- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-11-25)
- [Agent2Agent Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [MCP Best Practices](https://modelcontextprotocol.info/docs/best-practices/)
- [A2A Protocol Documentation](https://a2a-protocol.org/latest/)
- [Spring AI MCP Integration](https://www.baeldung.com/spring-ai-model-context-protocol-mcp)
- [Resilience4j Documentation](https://resilience4j.readme.io/)

---

**Last Updated:** February 14, 2026
**YAWL Version:** 5.2
**Protocol Versions:** MCP 2025-11-25, A2A v1.0.0
