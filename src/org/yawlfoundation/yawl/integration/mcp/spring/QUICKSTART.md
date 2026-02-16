# YAWL Spring MCP Integration - Quick Start Guide

## 5-Minute Setup

### Prerequisites
- Java 21+
- YAWL engine running at `http://localhost:8080/yawl`
- Maven 3.6+

### Step 1: Run the Example Application

```bash
# Set environment variables
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL

# Optional: Enable Z.AI natural language
export ZAI_API_KEY=your-api-key

# Run the application
cd /home/user/yawl
java -cp target/yawl-5.2.jar \
  org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSpringApplication
```

### Step 2: Verify MCP Server is Running

You should see output like:
```
YAWL MCP Spring Server started successfully!
Total tools: 16 (1 custom)
Total resources: 4 (1 custom)
Total resource templates: 3 (0 custom)
YAWL MCP Server is now running. Press Ctrl+C to stop.
```

### Step 3: Test with MCP Client

Connect any MCP client to the STDIO transport and call tools:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "yawl_list_specifications",
    "arguments": {}
  }
}
```

## Creating a Custom Tool

### Simple Example

```java
package com.mycompany.yawl.tools;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;

import java.util.Map;

@Component
public class HelloWorldTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    public HelloWorldTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
                          YawlMcpSessionManager sessionManager) {
        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getName() {
        return "hello_yawl";
    }

    @Override
    public String getDescription() {
        return "Test tool that queries YAWL engine status";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return new McpSchema.JsonSchema(
            "object",
            Map.of(),  // No parameters
            List.of(), // No required parameters
            false, null, null
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> params) {
        try {
            // Real YAWL engine call
            String sessionHandle = sessionManager.getSessionHandle();
            List specs = interfaceBClient.getSpecificationList(sessionHandle);

            return new McpSchema.CallToolResult(
                "Hello from YAWL! Found " + specs.size() + " specifications.",
                false
            );
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                "Error: " + e.getMessage(),
                true
            );
        }
    }
}
```

### Build and Run

```bash
# Compile
mvn compile

# Run with your custom tool
java -cp target/classes:target/dependency/* \
  org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSpringApplication
```

Your custom tool `hello_yawl` will be automatically discovered and registered!

## Creating a Custom Resource

### Simple Example

```java
package com.mycompany.yawl.resources;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpResource;

@Component
public class StatusResource implements YawlMcpResource {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    public StatusResource(InterfaceB_EnvironmentBasedClient interfaceBClient,
                          YawlMcpSessionManager sessionManager) {
        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getUri() {
        return "yawl://status";
    }

    @Override
    public String getName() {
        return "YAWL Engine Status";
    }

    @Override
    public String getDescription() {
        return "Current status of the YAWL engine";
    }

    @Override
    public McpSchema.ReadResourceResult read(String uri) {
        try {
            String sessionHandle = sessionManager.getSessionHandle();
            boolean connected = sessionManager.isConnected();

            String json = "{\"connected\":" + connected +
                         ",\"sessionValid\":" + sessionManager.isSessionValid() + "}";

            return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(uri, "application/json", json)
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read status: " + e.getMessage(), e);
        }
    }
}
```

## Spring Boot Application (Recommended)

For production use, create a proper Spring Boot application:

### 1. Create Application Class

```java
package com.mycompany.yawl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.yawlfoundation.yawl.integration.mcp.spring.EnableYawlMcp;

@SpringBootApplication
@EnableYawlMcp
public class MyYawlMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyYawlMcpApplication.class, args);
    }
}
```

### 2. Create application.yml

```yaml
spring:
  application:
    name: my-yawl-mcp

yawl:
  mcp:
    enabled: true
    engine-url: ${YAWL_ENGINE_URL:http://localhost:8080/yawl}
    username: ${YAWL_USERNAME:admin}
    password: ${YAWL_PASSWORD:YAWL}
    transport: stdio
```

### 3. Run with Spring Boot

```bash
mvn spring-boot:run
```

## Configuration Options

### Environment Variables (Recommended)

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
export ZAI_API_KEY=your-zhipu-api-key
```

### application.yml (Alternative)

```yaml
yawl:
  mcp:
    engine-url: http://localhost:8080/yawl
    username: admin
    password: YAWL
    transport: stdio

    connection:
      retry-attempts: 3
      retry-delay-ms: 1000
      timeout-ms: 5000

    zai:
      enabled: true
      api-key: your-zhipu-api-key
```

### Command Line Arguments

```bash
java -jar yawl-5.2.jar \
  --yawl.mcp.engine-url=http://localhost:8080/yawl \
  --yawl.mcp.username=admin \
  --yawl.mcp.password=YAWL
```

## Troubleshooting

### Connection Failed

```
Failed to connect to YAWL engine after 3 attempts
```

**Solution**: Verify YAWL engine is running:
```bash
curl http://localhost:8080/yawl/ia
```

### Session Invalid

```
Not connected to YAWL engine and reconnection failed
```

**Solution**: Check credentials and engine URL:
```bash
# Test connection manually
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
```

### Tool Not Found

**Solution**: Ensure your tool class is:
1. Annotated with `@Component`
2. In a package scanned by Spring
3. Implements `YawlMcpTool` interface

### Z.AI Not Working

```
Z.AI integration enabled but no API key provided
```

**Solution**: Set API key:
```bash
export ZAI_API_KEY=your-api-key
# or
export ZHIPU_API_KEY=your-api-key
```

## Next Steps

1. **Read the Design Doc**: See `SPRING_AI_MCP_DESIGN.md` for architecture details
2. **Explore Examples**: Check `tools/LaunchCaseTool.java` and `resources/SpecificationsResource.java`
3. **Add Custom Tools**: Implement `YawlMcpTool` and add `@Component`
4. **Add Custom Resources**: Implement `YawlMcpResource` and add `@Component`
5. **Configure Monitoring**: Add Spring Actuator for health checks
6. **Deploy to Production**: Use profiles in `application.yml`

## Common Use Cases

### Use Case 1: Custom Approval Tool

```java
@Component
public class ApprovalTool implements YawlMcpTool {
    // Inject dependencies
    // Implement interface
    // Add business logic for approvals
}
```

### Use Case 2: Audit Trail Resource

```java
@Component
public class AuditTrailResource implements YawlMcpResource {
    @Override
    public String getUri() { return "yawl://cases/{caseId}/audit"; }

    @Override
    public boolean isTemplate() { return true; }

    // Implement read() to return case audit trail
}
```

### Use Case 3: Custom Workflow Launcher

```java
@Component
public class QuickLaunchTool implements YawlMcpTool {
    // Simplified case launching with defaults
    // Business-specific validation
    // Integration with external systems
}
```

## Support

- **Documentation**: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/`
- **Examples**: `tools/` and `resources/` packages
- **Design**: `SPRING_AI_MCP_DESIGN.md`
- **Standards**: `/home/user/yawl/CLAUDE.md`

---

**Ready to build?** Start with the HelloWorldTool example above and extend from there!
