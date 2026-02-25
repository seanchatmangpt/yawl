# Spring Boot 3.4 Migration Guide for YAWL

**YAWL Version:** 5.2 → 6.0.0
**Target:** Spring Boot 3.4.x + Jakarta EE 10
**Strategy:** Hybrid (new services only, preserve existing WAR files)
**Author:** YAWL Architecture Team
**Date:** 2026-02-15

---

## Migration Strategy: Hybrid Approach

**Philosophy:** Spring Boot for **new services**, preserve existing WAR architecture for **core engine**.

### Why Hybrid?

| Component | Strategy | Rationale |
|-----------|----------|-----------|
| **Core Engine** (yawl.war) | Keep Servlet-based | Stable, well-tested, low risk |
| **Resource Service** | Keep Servlet-based | Complex JSF UI, high refactor cost |
| **Worklet Service** | Keep Servlet-based | Tightly coupled to engine |
| **MCP Server** | **NEW: Spring Boot** | Modern, I/O-intensive, benefits from virtual threads |
| **Agent Registry** | **NEW: Spring Boot** | Microservice pattern, cloud-native |
| **A2A Server** | **NEW: Spring Boot** | Protocol server, REST-friendly |
| **Monitoring Service** | **MIGRATE: Spring Boot** | Stateless, UI can be SPA + REST API |

**Benefit:** Incremental modernization without big-bang rewrite.

---

## Phase 1: New Service - MCP Server (Spring Boot)

### Current Architecture (Pure Java)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`

```java
public class YawlMcpServer {
    public static void main(String[] args) {
        YawlMcpServer server = new YawlMcpServer(engineUrl, username, password);
        server.start();  // STDIO-based MCP server
    }
}
```

**Deployment:** Standalone JAR, manual process management.

### Target Architecture (Spring Boot)

**New Module Structure:**

```
yawl-mcp-server/
├── pom.xml                          # Spring Boot parent
├── src/main/java/
│   └── org/yawlfoundation/yawl/mcp/
│       ├── YawlMcpServerApplication.java    # @SpringBootApplication
│       ├── config/
│       │   ├── YawlEngineConfig.java        # @ConfigurationProperties
│       │   └── VirtualThreadConfig.java     # Virtual thread customization
│       ├── controller/
│       │   └── HealthController.java        # Actuator extensions
│       ├── service/
│       │   ├── McpToolService.java          # Business logic
│       │   └── YawlIntegrationService.java  # Interface B client
│       └── metrics/
│           └── McpMetrics.java              # Micrometer metrics
└── src/main/resources/
    ├── application.yml                       # Configuration
    └── application-prod.yml                  # Production overrides
```

### Implementation

#### 1. `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.0</version>
        <relativePath/>
    </parent>

    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-mcp-server</artifactId>
    <version>5.3.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <mcp-sdk.version>0.17.2</mcp-sdk.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-otel</artifactId>
        </dependency>

        <!-- MCP SDK -->
        <dependency>
            <groupId>io.modelcontextprotocol</groupId>
            <artifactId>mcp-java-sdk</artifactId>
            <version>${mcp-sdk.version}</version>
        </dependency>

        <!-- YAWL Dependencies (from existing build) -->
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-engine-core</artifactId>
            <version>5.2.0</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>org.yawlfoundation.yawl.mcp.YawlMcpServerApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 2. `YawlMcpServerApplication.java`

```java
package org.yawlfoundation.yawl.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

@SpringBootApplication
@EnableAsync
public class YawlMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YawlMcpServerApplication.class, args);
    }

    /**
     * Enable virtual threads for all HTTP request handling.
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
```

#### 3. `application.yml`

```yaml
spring:
  application:
    name: yawl-mcp-server

  threads:
    virtual:
      enabled: true  # Enable virtual threads globally

server:
  port: 8090
  shutdown: graceful  # Allow in-flight requests to complete

# YAWL Engine connection
yawl:
  engine:
    url: ${YAWL_ENGINE_URL:http://localhost:8080/yawl}
    username: ${YAWL_USERNAME:admin}
    password: ${YAWL_PASSWORD:YAWL}
    connection-timeout: 30s
    read-timeout: 60s

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info,threaddump,env
      base-path: /actuator
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${SPRING_PROFILES_ACTIVE:default}
    export:
      prometheus:
        enabled: true

# Logging
logging:
  level:
    org.yawlfoundation.yawl.mcp: INFO
    org.springframework: WARN
    io.modelcontextprotocol: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# Graceful shutdown
spring.lifecycle.timeout-per-shutdown-phase: 30s
```

#### 4. `YawlEngineConfig.java`

```java
package org.yawlfoundation.yawl.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "yawl.engine")
public class YawlEngineConfig {

    private String url;
    private String username;
    private String password;
    private Duration connectionTimeout = Duration.ofSeconds(30);
    private Duration readTimeout = Duration.ofSeconds(60);

    // Getters and setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
```

#### 5. `YawlIntegrationService.java`

```java
package org.yawlfoundation.yawl.mcp.service;

import org.springframework.stereotype.Service;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.mcp.config.YawlEngineConfig;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;

@Service
public class YawlIntegrationService {

    private final YawlEngineConfig config;
    private InterfaceB_EnvironmentBasedClient client;
    private String sessionHandle;

    public YawlIntegrationService(YawlEngineConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void connect() throws Exception {
        String interfaceBUrl = config.getUrl() + "/ib";
        this.client = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);

        this.sessionHandle = client.connect(config.getUsername(), config.getPassword());

        if (sessionHandle == null || sessionHandle.contains("failure")) {
            throw new RuntimeException("Failed to connect to YAWL engine: " + sessionHandle);
        }

        System.out.println("Connected to YAWL engine at " + interfaceBUrl);
    }

    @PreDestroy
    public void disconnect() {
        if (client != null && sessionHandle != null) {
            client.disconnect(sessionHandle);
        }
    }

    public String launchCase(String specId, String caseData) throws Exception {
        return client.launchCase(specId, caseData, sessionHandle);
    }

    public List<WorkItemRecord> getEnabledWorkItems() throws Exception {
        return client.getWorkItemsForService(sessionHandle);
    }

    public String completeWorkItem(String workItemId, String data) throws Exception {
        return client.completeWorkItem(workItemId, data, sessionHandle);
    }

    // Add more Interface B methods as needed
}
```

#### 6. `McpToolService.java`

```java
package org.yawlfoundation.yawl.mcp.service;

import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class McpToolService {

    private final YawlIntegrationService yawlService;

    public McpToolService(YawlIntegrationService yawlService) {
        this.yawlService = yawlService;
    }

    @Timed(value = "mcp.tool.execution", description = "MCP tool execution time")
    public String executeTool(String toolName, Map<String, Object> params)
            throws TimeoutException, InterruptedException {

        // Execute tool with 30-second timeout using structured concurrency
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var task = scope.fork(() -> executeToolUnsafe(toolName, params));

            scope.joinUntil(Instant.now().plus(30, ChronoUnit.SECONDS));

            if (task.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                return task.get();
            } else {
                throw new TimeoutException("Tool execution timeout: " + toolName);
            }
        }
    }

    private String executeToolUnsafe(String toolName, Map<String, Object> params) throws Exception {
        return switch (toolName) {
            case "launchCase" -> {
                String specId = (String) params.get("specId");
                String data = (String) params.getOrDefault("data", "<data/>");
                yield yawlService.launchCase(specId, data);
            }
            case "getWorkItems" -> {
                var items = yawlService.getEnabledWorkItems();
                yield items.toString();  // Simplify for demo
            }
            case "completeWorkItem" -> {
                String itemId = (String) params.get("itemId");
                String data = (String) params.getOrDefault("data", "<data/>");
                yield yawlService.completeWorkItem(itemId, data);
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }
}
```

#### 7. `HealthController.java` (Custom Health Checks)

```java
package org.yawlfoundation.yawl.mcp.controller;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.mcp.service.YawlIntegrationService;

@Component
public class YawlEngineHealthIndicator implements HealthIndicator {

    private final YawlIntegrationService yawlService;

    public YawlEngineHealthIndicator(YawlIntegrationService yawlService) {
        this.yawlService = yawlService;
    }

    @Override
    public Health health() {
        try {
            // Simple health check: try to get work items
            yawlService.getEnabledWorkItems();
            return Health.up()
                .withDetail("yawl-engine", "Connected")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("yawl-engine", "Disconnected")
                .withException(e)
                .build();
        }
    }
}
```

### Deployment

#### Build and Run

```bash
# Build JAR
mvn clean package

# Run locally
java -jar target/yawl-mcp-server-5.3.0-SNAPSHOT.jar

# Run with profile
java -jar target/yawl-mcp-server-5.3.0-SNAPSHOT.jar \
     --spring.profiles.active=prod

# Override config
java -jar target/yawl-mcp-server-5.3.0-SNAPSHOT.jar \
     --yawl.engine.url=https://yawl.prod.example.com/yawl \
     --yawl.engine.username=admin \
     --yawl.engine.password=secret
```

#### Docker

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/yawl-mcp-server-*.jar app.jar

EXPOSE 8090

# Enable virtual threads explicitly (though Spring Boot 3.4 does this)
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build image
docker build -t yawl/mcp-server:5.3.0 .

# Run container
docker run -d \
  -p 8090:8090 \
  -e YAWL_ENGINE_URL=http://yawl-engine:8080/yawl \
  -e YAWL_USERNAME=admin \
  -e YAWL_PASSWORD=YAWL \
  --name yawl-mcp-server \
  yawl/mcp-server:5.3.0
```

#### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-mcp-server
spec:
  replicas: 2
  selector:
    matchLabels:
      app: yawl-mcp-server
  template:
    metadata:
      labels:
        app: yawl-mcp-server
    spec:
      containers:
      - name: mcp-server
        image: yawl/mcp-server:5.3.0
        ports:
        - containerPort: 8090
        env:
        - name: YAWL_ENGINE_URL
          value: "http://yawl-engine:8080/yawl"
        - name: YAWL_USERNAME
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: username
        - name: YAWL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8090
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8090
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: yawl-mcp-server
spec:
  selector:
    app: yawl-mcp-server
  ports:
  - protocol: TCP
    port: 8090
    targetPort: 8090
  type: ClusterIP
```

### Monitoring

#### Prometheus Metrics

```bash
# Scrape endpoint
curl http://localhost:8090/actuator/prometheus

# Example metrics:
# mcp_tool_execution_seconds_count{toolName="launchCase"} 42
# mcp_tool_execution_seconds_sum{toolName="launchCase"} 12.5
# jvm_threads_live{} 15  # Platform threads (virtual threads not counted)
# http_server_requests_seconds_count{uri="/actuator/health"} 100
```

#### Grafana Dashboard

```json
{
  "dashboard": {
    "title": "YAWL MCP Server",
    "panels": [
      {
        "title": "Tool Execution Rate",
        "targets": [
          {
            "expr": "rate(mcp_tool_execution_seconds_count[5m])"
          }
        ]
      },
      {
        "title": "Tool Execution Duration (p95)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, mcp_tool_execution_seconds_bucket)"
          }
        ]
      },
      {
        "title": "JVM Heap Usage",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{area=\"heap\"}"
          }
        ]
      }
    ]
  }
}
```

---

## Phase 2: New Service - Agent Registry (Spring Boot)

### Features

- RESTful API for agent registration
- WebSocket support for real-time agent updates
- Redis cache for fast agent lookup
- Horizontal scaling with database-backed registry

### Dependencies

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### Configuration

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2s
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/yawl_agents
    username: ${DB_USERNAME:yawl}
    password: ${DB_PASSWORD:yawl}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
```

### REST Controller

```java
package org.yawlfoundation.yawl.registry.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.yawlfoundation.yawl.registry.service.AgentRegistryService;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRegistryService registryService;

    public AgentController(AgentRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/register")
    public ResponseEntity<AgentInfo> register(@RequestBody AgentInfo agentInfo) {
        AgentInfo registered = registryService.register(agentInfo);
        return ResponseEntity.ok(registered);
    }

    @GetMapping
    public List<AgentInfo> getAllAgents() {
        return registryService.getAllAgents();
    }

    @GetMapping("/by-capability")
    public List<AgentInfo> getByCapability(@RequestParam String domain) {
        return registryService.findByCapability(domain);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        registryService.unregister(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String id) {
        registryService.updateHeartbeat(id);
        return ResponseEntity.ok().build();
    }
}
```

### Service Layer with Redis Cache

```java
package org.yawlfoundation.yawl.registry.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.registry.repository.AgentRepository;

import java.util.List;

@Service
public class AgentRegistryService {

    private final AgentRepository repository;

    public AgentRegistryService(AgentRepository repository) {
        this.repository = repository;
    }

    @CacheEvict(value = "agents", allEntries = true)
    public AgentInfo register(AgentInfo agentInfo) {
        return repository.save(agentInfo);
    }

    @Cacheable(value = "agents")
    public List<AgentInfo> getAllAgents() {
        return repository.findAll();
    }

    @Cacheable(value = "agents", key = "#domain")
    public List<AgentInfo> findByCapability(String domain) {
        return repository.findByCapabilityDomain(domain);
    }

    @CacheEvict(value = "agents", allEntries = true)
    public void unregister(String id) {
        repository.deleteById(id);
    }

    @CacheEvict(value = "agents", allEntries = true)
    public void updateHeartbeat(String id) {
        repository.updateHeartbeat(id);
    }
}
```

---

## Phase 3: Migrate Existing Service - Monitoring Service

### Current Architecture (JSF + Servlets)

```
monitorService.war
├── WEB-INF/
│   ├── web.xml                  # Servlet 2.4 config
│   ├── faces-config.xml         # JSF configuration
│   └── classes/
│       └── org/yawlfoundation/yawl/monitor/
│           └── jsf/             # JSF backing beans
```

**Challenge:** JSF is tightly coupled to Java EE.

### Target Architecture (Spring Boot + React SPA)

```
yawl-monitoring-service/
├── backend/                      # Spring Boot REST API
│   ├── src/main/java/
│   │   └── org/yawlfoundation/yawl/monitor/
│   │       ├── MonitoringApplication.java
│   │       ├── controller/
│   │       │   ├── CaseController.java
│   │       │   └── WorkItemController.java
│   │       └── service/
│   │           └── MonitoringService.java
│   └── pom.xml
└── frontend/                     # React SPA
    ├── src/
    │   ├── components/
    │   │   ├── CaseList.jsx
    │   │   └── WorkItemTable.jsx
    │   └── App.jsx
    └── package.json
```

### REST API Example

```java
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final MonitoringService service;

    public CaseController(MonitoringService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CaseInfo> getCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.getCases(PageRequest.of(page, size));
    }

    @GetMapping("/{caseId}")
    public CaseInfo getCaseDetails(@PathVariable String caseId) {
        return service.getCaseDetails(caseId);
    }

    @GetMapping("/{caseId}/workitems")
    public List<WorkItemInfo> getWorkItems(@PathVariable String caseId) {
        return service.getWorkItems(caseId);
    }
}
```

### Frontend (React)

```javascript
// src/components/CaseList.jsx
import React, { useEffect, useState } from 'react';

function CaseList() {
  const [cases, setCases] = useState([]);

  useEffect(() => {
    fetch('/api/cases')
      .then(res => res.json())
      .then(data => setCases(data.content));
  }, []);

  return (
    <table>
      <thead>
        <tr>
          <th>Case ID</th>
          <th>Specification</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {cases.map(c => (
          <tr key={c.caseId}>
            <td>{c.caseId}</td>
            <td>{c.specId}</td>
            <td>{c.status}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default CaseList;
```

---

## Comparison: Before vs. After

### Development Experience

| Aspect | Servlet + Ant | Spring Boot + Maven |
|--------|---------------|---------------------|
| **Build** | `ant buildWebApps` | `mvn spring-boot:run` |
| **Deploy** | Copy WAR to Tomcat | `java -jar app.jar` |
| **Config** | `web.xml`, `build.properties` | `application.yml` |
| **Testing** | Manual deployment | Embedded Tomcat, instant feedback |
| **Dependencies** | Ivy (manual) | Maven Central (auto) |

### Operational Experience

| Aspect | Servlet + Tomcat | Spring Boot |
|--------|------------------|-------------|
| **Metrics** | JMX (manual) | Actuator (built-in) |
| **Health Checks** | Custom servlet | `/actuator/health` |
| **Logging** | Log4j2 XML | Logback YAML + correlation IDs |
| **Graceful Shutdown** | Manual | Built-in |
| **Kubernetes** | Custom probes | Readiness/liveness built-in |

---

## Migration Checklist

### Phase 1: New Services (Low Risk)
- [ ] Create `yawl-mcp-server` Spring Boot module
- [ ] Implement REST endpoints for MCP tools
- [ ] Add Actuator health checks
- [ ] Add Prometheus metrics
- [ ] Deploy to staging
- [ ] Load test (before/after comparison)
- [ ] Deploy to production

### Phase 2: Agent Registry (Medium Risk)
- [ ] Create `yawl-agent-registry` Spring Boot module
- [ ] Implement REST API for agent CRUD
- [ ] Add Redis caching layer
- [ ] Add WebSocket support for real-time updates
- [ ] Migrate existing agents to new registry
- [ ] Deploy with canary strategy

### Phase 3: Monitoring Service (High Risk - Optional)
- [ ] Create React SPA frontend
- [ ] Create Spring Boot backend with REST API
- [ ] Migrate JSF views to React components
- [ ] Add authentication/authorization
- [ ] A/B test old vs. new UI
- [ ] Gradual rollout

---

## Best Practices

### 1. Configuration Management

**Use environment variables for secrets:**

```yaml
# application.yml
yawl:
  engine:
    password: ${YAWL_PASSWORD}  # Required env var
```

**Use profiles for environments:**

```bash
# Development
java -jar app.jar --spring.profiles.active=dev

# Production
java -jar app.jar --spring.profiles.active=prod
```

### 2. Graceful Shutdown

```yaml
# application.yml
server:
  shutdown: graceful

spring.lifecycle.timeout-per-shutdown-phase: 30s
```

**What this does:**
- Stop accepting new requests
- Wait for in-flight requests to complete (up to 30s)
- Shutdown cleanly

### 3. Observability

**Always expose these actuator endpoints:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
```

**Add custom metrics:**

```java
@Timed(value = "yawl.case.launch", description = "Case launch duration")
public String launchCase(String specId, String data) {
    // ...
}
```

### 4. Security

**Use Spring Security for authentication:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic();
        return http.build();
    }
}
```

---

## Rollback Strategy

### If Migration Fails

1. **Keep old WAR deployable:** Don't delete Servlet code
2. **Feature flags:** Deploy both, route traffic via config
3. **Database backward compatibility:** New schema must support old code

**Example feature flag:**

```yaml
# application.yml
features:
  use-new-mcp-server: ${USE_NEW_MCP:false}
```

```java
@Service
public class McpRouterService {

    @Value("${features.use-new-mcp-server}")
    private boolean useNewServer;

    public String executeTool(String toolName, Map<String, Object> params) {
        if (useNewServer) {
            return newMcpService.execute(toolName, params);
        } else {
            return legacyMcpService.execute(toolName, params);
        }
    }
}
```

---

## Conclusion

Spring Boot 3.4 modernizes YAWL's infrastructure without requiring a full rewrite. The hybrid approach:

- **Preserves stability** of core engine
- **Modernizes** new services (MCP, agents)
- **Enables** cloud-native deployment patterns
- **Simplifies** operations with built-in observability

**Next Steps:**
1. Start with MCP server (new service, low risk)
2. Validate virtual thread performance gains
3. Iterate based on operational feedback
4. Gradually expand to other services

---

**References:**
- [Spring Boot 3.4 Documentation](https://docs.spring.io/spring-boot/reference/)
- [Spring Boot with Virtual Threads](https://spring.io/blog/2022/10/11/embracing-virtual-threads)
- [Actuator Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Micrometer Metrics](https://micrometer.io/docs)
