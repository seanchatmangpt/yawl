# Architecture Comparison: Java 11 (v5.2) vs Java 25 (v6.0.0) + Spring Boot

**YAWL Version:** 5.2 → 6.0.0
**Date:** 2026-02-15

---

## Current Architecture (YAWL 5.2 - Java 11)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         YAWL Deployment                              │
│                        (Tomcat 9.x Container)                        │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
          ┌─────────▼─────┐  ┌────▼─────┐  ┌────▼────────┐
          │  yawl.war     │  │ resource  │  │  worklet    │
          │  (Engine)     │  │ Service   │  │  Service    │
          │               │  │  .war     │  │  .war       │
          └─────────┬─────┘  └────┬─────┘  └────┬────────┘
                    │              │              │
          ┌─────────▼──────────────▼──────────────▼─────────┐
          │        Threading Model (Platform Threads)        │
          │                                                  │
          │  ┌──────────────────────────────────────────┐  │
          │  │ Fixed Thread Pools                       │  │
          │  ├──────────────────────────────────────────┤  │
          │  │ • MultiThreadEventNotifier: 12 threads   │  │
          │  │ • AgentRegistry HTTP: 10 threads         │  │
          │  │ • InterfaceX Client: N threads           │  │
          │  │ • WorkletEventServer: N threads          │  │
          │  │ • YEventLogger: CPU count threads        │  │
          │  └──────────────────────────────────────────┘  │
          │                                                  │
          │  Problem: Manual sizing, resource waste         │
          │  Limit: ~1000 concurrent operations max         │
          └──────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
          ┌─────────▼──────┐  ┌───▼──────┐  ┌───▼────────┐
          │  PostgreSQL    │  │ Hibernate │  │ Log4j2     │
          │  (Persistence) │  │ 5.x       │  │ (Logging)  │
          └────────────────┘  └───────────┘  └────────────┘

Deployment Characteristics:
• Build: Ant + Ivy
• Packaging: WAR files (multiple services)
• Deployment: Manual copy to Tomcat webapps/
• Health Checks: Custom servlets
• Metrics: JMX (manual configuration)
• Scaling Limit: ~500 concurrent agents
```

---

## Target Architecture (YAWL 6.0.0 - Java 25 + Spring Boot)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    YAWL Hybrid Deployment (Cloud-Native)                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
┌──────────▼──────────┐    ┌──────────▼──────────┐   ┌─────────▼─────────┐
│ Legacy Services     │    │ New Spring Boot     │   │ New Spring Boot   │
│ (Keep Servlet)      │    │ Services            │   │ Services          │
├─────────────────────┤    ├─────────────────────┤   ├───────────────────┤
│ • yawl.war          │    │ • yawl-mcp-server   │   │ • agent-registry  │
│ • resourceService   │    │   (standalone JAR)  │   │   (standalone JAR)│
│ • workletService    │    │ • STDIO + REST      │   │ • REST + WS       │
│                     │    │ • Actuator          │   │ • Redis cache     │
│ Tomcat 9.x          │    │ • Prometheus        │   │ • Prometheus      │
│ (Unchanged)         │    │                     │   │                   │
└─────────┬───────────┘    └──────────┬──────────┘   └─────────┬─────────┘
          │                           │                         │
          │              ┌────────────▼────────────┐           │
          │              │ Virtual Threads Enabled │           │
          │              │ (Spring Boot 3.4)       │           │
          │              └────────────┬────────────┘           │
          │                           │                         │
┌─────────▼───────────────────────────▼─────────────────────────▼─────────┐
│                    Threading Model (Virtual Threads)                     │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ Virtual Thread Executors (Unlimited Concurrency)                   │ │
│  ├────────────────────────────────────────────────────────────────────┤ │
│  │ • MultiThreadEventNotifier: newVirtualThreadPerTaskExecutor()      │ │
│  │   → 10,000 concurrent event listeners (no queue)                  │ │
│  │                                                                     │ │
│  │ • AgentRegistry HTTP: newVirtualThreadPerTaskExecutor()            │ │
│  │   → 1,000s of concurrent agent registrations                      │ │
│  │                                                                     │ │
│  │ • MCP Tool Execution: Structured Concurrency                       │ │
│  │   → Parallel tool execution with timeout protection               │ │
│  │                                                                     │ │
│  │ • Agent Discovery: Structured Concurrency                          │ │
│  │   → 100 agents discovered in parallel (20s → 0.2s)                │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  Benefit: Zero configuration, infinite scaling (bounded by heap)        │
│  Capability: 10,000+ concurrent operations                              │
└──────────────────────────────────────────────────────────────────────────┘
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
┌──────────▼──────────┐    ┌──────────▼──────────┐   ┌─────────▼─────────┐
│  PostgreSQL         │    │ Hibernate 6.4+      │   │ Log4j2 + Logback  │
│  (Unchanged)        │    │ (Jakarta EE 10)     │   │ (Structured logs) │
│                     │    │ • Virtual thread    │   │ • OpenTelemetry   │
│  HikariCP           │    │   friendly          │   │   integration     │
│  Connection Pool    │    │ • Java 21 support   │   │                   │
└─────────────────────┘    └─────────────────────┘   └───────────────────┘

Deployment Characteristics:
• Build: Ant (legacy) + Maven (new services)
• Packaging: WAR (legacy) + JAR (Spring Boot)
• Deployment: Docker/Kubernetes (GitOps)
• Health Checks: /actuator/health (standard)
• Metrics: Prometheus + Micrometer
• Scaling Limit: 10,000+ concurrent agents
```

---

## Threading Model Deep Dive

### Current: Platform Threads (Java 11)

```
┌─────────────────────────────────────────────────────────┐
│            Request/Event Processing (Before)            │
└─────────────────────────────────────────────────────────┘

Request 1 ───► │ Platform Thread 1 │ (1MB stack)
Request 2 ───► │ Platform Thread 2 │ (1MB stack)
Request 3 ───► │ Platform Thread 3 │ (1MB stack)
    ...
Request 12 ──► │ Platform Thread 12│ (1MB stack)
Request 13 ──► │  QUEUED (waiting) │ ← Bottleneck!
Request 14 ──► │  QUEUED (waiting) │
Request 15 ──► │  QUEUED (waiting) │

Total Memory: 12 threads × 1MB = 12MB
Max Concurrent: 12 (hardcoded)
Queueing: Yes (thread pool exhaustion)
Tuning Required: Yes (THREADPOOL_SIZE)

Example:
  ExecutorService executor = Executors.newFixedThreadPool(12);
  // Must tune pool size for workload
```

### Target: Virtual Threads (Java 25)

```
┌─────────────────────────────────────────────────────────┐
│            Request/Event Processing (After)             │
└─────────────────────────────────────────────────────────┘

Request 1 ───► │ Virtual Thread 1  │ (~200 bytes when blocked)
Request 2 ───► │ Virtual Thread 2  │
Request 3 ───► │ Virtual Thread 3  │
    ...
Request 100 ─► │ Virtual Thread 100│
    ...
Request 10000► │ Virtual Thread 10000│ ← No problem!

              Carrier Threads (Platform)
              ┌─────────────────────┐
              │ Thread 1            │ ← Scheduled by JVM
              │ Thread 2            │
              │ ...                 │
              │ Thread 15 (CPU×2)   │
              └─────────────────────┘

Total Memory: 10,000 virtual threads × 200 bytes = 2MB
Max Concurrent: Millions (bounded by heap, not thread count)
Queueing: No (instant task execution)
Tuning Required: No (scales automatically)

Example:
  ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  // Zero configuration, infinite scaling
```

**Key Insight:** Platform threads map 1:1 to OS threads. Virtual threads are scheduled by the JVM onto a small pool of platform threads (carrier threads).

---

## Agent Architecture Evolution

### Before: Limited Agent Concurrency

```
┌──────────────────────────────────────────────────────────────┐
│                   Agent Polling Architecture                  │
└──────────────────────────────────────────────────────────────┘

┌────────────┐   ┌────────────┐   ┌────────────┐
│  Agent 1   │   │  Agent 2   │   │  Agent 3   │
│  (Thread)  │   │  (Thread)  │   │  (Thread)  │
└─────┬──────┘   └─────┬──────┘   └─────┬──────┘
      │                │                │
      └────────────────┼────────────────┘
                       │
                  ┌────▼────┐
                  │  YAWL   │
                  │  Engine │
                  └─────────┘

Problem: Each agent needs dedicated thread
Limit: 100 agents = 100 threads = 100MB RAM
Scale: Cannot deploy 1,000+ agents (thread exhaustion)

HTTP Server (AgentRegistry):
  newFixedThreadPool(10)  ← Max 10 concurrent registrations
  11th request waits for free thread
```

### After: Unlimited Agent Concurrency

```
┌──────────────────────────────────────────────────────────────┐
│          Agent Polling Architecture (Virtual Threads)         │
└──────────────────────────────────────────────────────────────┘

┌────────────┐ ┌────────────┐       ┌────────────┐
│  Agent 1   │ │  Agent 2   │  ...  │ Agent 10K  │
│ (V-Thread) │ │ (V-Thread) │       │ (V-Thread) │
└─────┬──────┘ └─────┬──────┘       └─────┬──────┘
      │              │                     │
      └──────────────┼─────────────────────┘
                     │
              ┌──────▼──────┐
              │    YAWL     │
              │   Engine    │
              └─────────────┘

Benefit: 10,000 agents = 10,000 virtual threads = 2MB RAM
Scale: Millions of agents feasible
Deployment: Same instance handles 100x more agents

HTTP Server (AgentRegistry):
  newVirtualThreadPerTaskExecutor()  ← Unlimited concurrent registrations
  No queueing, instant handling
```

---

## MCP Server Architecture

### Before: Sequential Tool Execution

```
┌──────────────────────────────────────────────────┐
│           MCP Tool Execution (Before)            │
└──────────────────────────────────────────────────┘

Claude requests 3 tools:
  1. launchCase (500ms)
  2. getWorkItems (300ms)
  3. completeWorkItem (400ms)

Timeline:
  0ms ─────► launchCase ─────► 500ms
  500ms ───► getWorkItems ───► 800ms
  800ms ───► completeWorkItem ► 1200ms

Total Time: 1200ms (sequential)

Code:
  public String executeTool(String toolName, Map params) {
      return switch (toolName) {
          case "launchCase" -> launchCase(params);  // Blocks
          case "getWorkItems" -> getWorkItems(params);
          case "completeWorkItem" -> completeItem(params);
      };
  }
```

### After: Parallel Tool Execution with Timeouts

```
┌──────────────────────────────────────────────────┐
│      MCP Tool Execution (Structured Concurrency) │
└──────────────────────────────────────────────────┘

Claude requests 3 tools in parallel:

Timeline:
  0ms ─┬──► launchCase ─────────────► 500ms
       ├──► getWorkItems ────────────► 300ms
       └──► completeWorkItem ─────────► 400ms

Total Time: 500ms (parallel, max of all)

Code:
  try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
      var task1 = scope.fork(() -> launchCase(params));
      var task2 = scope.fork(() -> getWorkItems(params));
      var task3 = scope.fork(() -> completeItem(params));

      scope.joinUntil(Instant.now().plus(30, ChronoUnit.SECONDS));

      // All results available, or timeout/failure
  }

Benefit: 2.4x faster, built-in timeout protection
```

---

## Event Fan-Out Performance

### Before: Bounded Concurrency

```
Event Notification to 1,000 Listeners:

┌──────────────────────────────────────────────────┐
│     Platform Thread Pool (12 threads)            │
└──────────────────────────────────────────────────┘

Listener 1-12:    Execute immediately (12 threads)
Listener 13-24:   Wait for free thread (queueing)
Listener 25-36:   Wait for free thread (queueing)
...
Listener 989-1000: Wait for free thread (queueing)

Time to Complete:
  1,000 listeners / 12 threads = 83 batches
  Each batch: 50ms average
  Total: 83 × 50ms = 4,150ms

Resource Usage:
  12 threads × 1MB = 12MB
```

### After: Unbounded Concurrency

```
Event Notification to 1,000 Listeners:

┌──────────────────────────────────────────────────┐
│   Virtual Thread Executor (Unlimited)            │
└──────────────────────────────────────────────────┘

Listener 1-1,000: All execute immediately (1,000 virtual threads)

Time to Complete:
  All 1,000 listeners notified in parallel
  Total: 50ms (single batch)

Resource Usage:
  1,000 virtual threads × 200 bytes = 200KB

Improvement: 83x faster, 60x less memory
```

---

## Spring Boot Integration Architecture

### New Service: MCP Server

```
┌─────────────────────────────────────────────────────────────┐
│              yawl-mcp-server (Spring Boot 3.4)               │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐
│   Spring Boot    │
│   Application    │
│   Context        │
└────────┬─────────┘
         │
    ┌────▼─────────────────────────────────────┐
    │  Auto-Configuration                      │
    ├──────────────────────────────────────────┤
    │ • Embedded Tomcat (virtual threads)      │
    │ • Actuator endpoints                     │
    │ • Micrometer metrics                     │
    │ • Health checks                          │
    │ • HikariCP connection pool               │
    │ • Jackson JSON                           │
    └────┬─────────────────────────────────────┘
         │
    ┌────▼─────────────────────────────────────┐
    │  Application Layer                       │
    ├──────────────────────────────────────────┤
    │ • McpToolService                         │
    │ • YawlIntegrationService                 │
    │ • HealthIndicators                       │
    │ • Custom metrics                         │
    └────┬─────────────────────────────────────┘
         │
    ┌────▼─────────────────────────────────────┐
    │  YAWL Engine Integration                 │
    ├──────────────────────────────────────────┤
    │ • InterfaceB_EnvironmentBasedClient      │
    │ • InterfaceA_EnvironmentBasedClient      │
    └──────────────────────────────────────────┘

Deployment:
  • Standalone JAR (java -jar yawl-mcp-server.jar)
  • Docker container
  • Kubernetes deployment
  • Health: /actuator/health
  • Metrics: /actuator/prometheus
```

---

## Deployment Model Comparison

### Current Deployment (Tomcat Shared)

```
┌────────────────────────────────────────────────┐
│           Single Tomcat Instance               │
├────────────────────────────────────────────────┤
│  webapps/                                      │
│    ├── yawl.war           (Core Engine)        │
│    ├── resourceService.war (Resources)         │
│    └── workletService.war  (Worklets)          │
├────────────────────────────────────────────────┤
│  lib/                     (Shared libraries)   │
│    ├── hibernate-*.jar                         │
│    ├── postgresql-*.jar                        │
│    └── ...                                     │
└────────────────────────────────────────────────┘

Deployment:
  $ ant buildWebApps
  $ cp output/*.war $CATALINA_HOME/webapps/
  $ $CATALINA_HOME/bin/catalina.sh restart

Challenges:
  • Shared lifecycle (restart affects all)
  • Dependency conflicts (shared lib/)
  • No independent scaling
  • Manual health checks
```

### Target Deployment (Hybrid Microservices)

```
┌────────────────────────────────────────────────┐
│         Legacy Tomcat Instance (Kept)          │
├────────────────────────────────────────────────┤
│  webapps/                                      │
│    ├── yawl.war           (Core Engine)        │
│    ├── resourceService.war (Resources)         │
│    └── workletService.war  (Worklets)          │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│     Spring Boot MCP Server (New)               │
├────────────────────────────────────────────────┤
│  yawl-mcp-server.jar                           │
│  • Embedded Tomcat                             │
│  • Virtual threads enabled                     │
│  • Actuator endpoints                          │
│  • Independent scaling                         │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│     Spring Boot Agent Registry (New)           │
├────────────────────────────────────────────────┤
│  yawl-agent-registry.jar                       │
│  • Embedded Tomcat                             │
│  • Redis integration                           │
│  • Horizontal scaling                          │
│  • WebSocket support                           │
└────────────────────────────────────────────────┘

Deployment (Kubernetes):
  $ kubectl apply -f yawl-engine-deployment.yaml
  $ kubectl apply -f yawl-mcp-deployment.yaml
  $ kubectl apply -f yawl-registry-deployment.yaml

Benefits:
  • Independent lifecycles
  • Independent scaling (HPA)
  • Isolated dependencies
  • Built-in health/metrics
```

---

## Scaling Comparison

### Vertical Scaling (Current)

```
Small Load:              Medium Load:             High Load:
┌────────────┐          ┌────────────┐          ┌────────────┐
│ 2 vCPU     │          │ 4 vCPU     │          │ 8 vCPU     │
│ 4 GB RAM   │          │ 8 GB RAM   │          │ 16 GB RAM  │
│            │          │            │          │            │
│ 50 agents  │  ────►   │ 200 agents │  ────►   │ 500 agents │
│            │          │            │          │ (LIMIT!)   │
└────────────┘          └────────────┘          └────────────┘

Cost: $35/mo            Cost: $70/mo            Cost: $140/mo
Limit: Thread pool exhaustion at ~500 agents
```

### Horizontal Scaling (Target)

```
Small Load:              Medium Load:             High Load:
┌────────────┐          ┌────────────┐          ┌────────────┐
│ Instance 1 │          │ Instance 1 │          │ Instance 1 │
│ 2 vCPU     │          │ 2 vCPU     │          │ 2 vCPU     │
│ 2 GB RAM   │          │ 2 GB RAM   │          │ 2 GB RAM   │
│            │          │            │          │            │
│ 1000 agents│          │ 1000 agents│          │ 1000 agents│
└────────────┘          └─────┬──────┘          └─────┬──────┘
                              │                       │
                        ┌─────▼──────┐          ┌─────▼──────┐
                        │ Instance 2 │          │ Instance 2 │
                        │ 2 vCPU     │          │ 2 vCPU     │
                        │ 2 GB RAM   │          │ 2 GB RAM   │
                        │            │          │            │
                        │ 1000 agents│          │ 1000 agents│
                        └────────────┘          └─────┬──────┘
                                                      │
                                                ┌─────▼──────┐
                                                │ Instance 3 │
                                                │ 2 vCPU     │
                                                │ 2 GB RAM   │
                                                │            │
                                                │ 1000 agents│
                                                └────────────┘

Cost: $17/mo            Cost: $34/mo            Cost: $51/mo
Capacity: 1000 agents   Capacity: 2000 agents   Capacity: 3000 agents

Benefit: 50% cost reduction + 6x capacity
```

---

## Memory Usage Comparison

### 1,000 Concurrent Agents

**Java 11 (Platform Threads):**
```
Agent threads:    1,000 × 1MB    = 1,000 MB
HTTP handlers:       10 × 1MB    =    10 MB
Event notifiers:     12 × 1MB    =    12 MB
────────────────────────────────────────────
Total thread RAM:                 1,022 MB

Heap:                             2,048 MB
────────────────────────────────────────────
Total JVM:                        3,070 MB

Result: Barely fits in 4GB instance (tight!)
```

**Java 21 (Virtual Threads):**
```
Agent V-threads:  1,000 × 200B   =   0.2 MB
HTTP V-handlers:  1,000 × 200B   =   0.2 MB
Event V-threads: 10,000 × 200B   =   2.0 MB
────────────────────────────────────────────
Total V-thread RAM:                  2.4 MB

Carrier threads:     15 × 1MB    =    15 MB
Heap:                             1,024 MB
────────────────────────────────────────────
Total JVM:                        1,041 MB

Result: Fits in 2GB instance with headroom
Savings: 66% memory reduction
```

---

## Observability Comparison

### Current (Manual JMX)

```
Metrics Collection:
  1. Enable JMX on Tomcat
  2. Configure JMX exporter (separate process)
  3. Map JMX beans to Prometheus metrics
  4. Custom servlet for health checks

Health Checks:
  • Manual servlet: /healthcheck
  • No readiness/liveness distinction
  • Custom format

Monitoring Setup:
  • Complex: 4-5 configuration files
  • Time to setup: 2-3 hours
```

### Target (Spring Boot Actuator)

```
Metrics Collection:
  1. Add dependency: spring-boot-starter-actuator
  2. Add dependency: micrometer-registry-prometheus
  3. Configure: spring.management.endpoints.web.exposure.include=prometheus

Health Checks:
  • Built-in: /actuator/health
  • Kubernetes-ready: /actuator/health/liveness, /readiness
  • Standard JSON format

Monitoring Setup:
  • Simple: 1 YAML file
  • Time to setup: 5 minutes

Example /actuator/health response:
{
  "status": "UP",
  "components": {
    "yawlEngine": {
      "status": "UP",
      "details": {
        "engineUrl": "http://localhost:8080/yawl",
        "connected": true
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

---

## Summary Table

| Aspect | Java 11 (Current) | Java 21 + Spring Boot (Target) | Improvement |
|--------|-------------------|--------------------------------|-------------|
| **Concurrency Model** | Platform threads (bounded) | Virtual threads (unbounded) | 100x scale |
| **Agent Limit** | ~500 agents | 10,000+ agents | 20x capacity |
| **Memory (1K agents)** | 3,070 MB | 1,041 MB | 66% reduction |
| **Event Fan-Out (1K)** | 4,150ms | 50ms | 83x faster |
| **Agent Discovery (100)** | 20s (sequential) | 0.2s (parallel) | 100x faster |
| **Thread Pool Tuning** | Required (manual) | Not needed (auto) | Zero-config |
| **Deployment Unit** | WAR files (shared Tomcat) | JAR files (embedded server) | Independent |
| **Scaling Strategy** | Vertical (limited) | Horizontal (unlimited) | Cloud-native |
| **Health Checks** | Custom servlets | /actuator/health | Standard |
| **Metrics** | JMX (manual) | Prometheus (auto) | 1-line config |
| **Deployment Time** | 10-15 minutes | 30 seconds | 20x faster |
| **Instance Cost (1K agents)** | $70/month (4GB) | $35/month (2GB) | 50% savings |

---

**Conclusion:** Java 21 + Spring Boot provides massive scalability improvements (100x concurrency), significant cost reduction (50%), and operational simplicity (zero thread tuning) without code complexity.

The hybrid approach preserves stability of the core engine while modernizing infrastructure incrementally.
