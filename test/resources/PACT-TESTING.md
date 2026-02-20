# YAWL Pact Contract Testing Guide

**Version**: 6.0.0
**Date**: 2026-02-20

## Overview

This guide explains how to run and maintain Pact contract tests for A2A (Agent-to-Agent) and MCP (Model Context Protocol) protocol validation in YAWL.

Pact enables **consumer-driven contract testing**: each consumer (client) defines its expectations of a provider (server), and the provider verifies it satisfies those contracts. This prevents protocol breaking changes.

## Quick Start

### Run All Contract Tests

```bash
# Consumer tests (generates pact files in target/pacts/)
bash scripts/dx.sh test -Dtest=*ConsumerContractTest

# Provider verification (verifies server against generated pacts)
bash scripts/dx.sh test -Dtest=*ProviderContractTest
```

### Run Specific Protocol Tests

```bash
# A2A contract tests only
mvn test -Dtest=A2A*ContractTest -pl yawl-integration

# MCP contract tests only
mvn test -Dtest=Mcp*ContractTest -pl yawl-integration
```

## What Are Contract Tests?

Contract tests verify that two services agree on their API protocol without requiring both to be deployed together:

```
┌─────────────────────────────────────────────┐
│ Consumer (A2A Client)                        │
│ • Defines expectations: "POST /a2a/task"    │
│ • Runs consumer test                        │
│ • Generates pact: a2a-client-a2a-server.json
└─────────────────────────────────────────────┘
                       ↓ (contract)
┌─────────────────────────────────────────────┐
│ Provider (A2A Server)                        │
│ • Loads pact from consumer                  │
│ • Replays each interaction                  │
│ • Verifies responses match                  │
│ • No need to deploy consumer!               │
└─────────────────────────────────────────────┘
```

Benefits:
- **Early Detection**: Find protocol mismatches in CI, not production
- **Independent**: Consumer and provider can be tested separately
- **Documentation**: Pacts are executable API documentation
- **Resilience**: Contract tests validate timeout and error handling

## Pact Files

Generated pact files are stored in `target/pacts/` and committed to version control:

```json
{
  "consumer": { "name": "YawlA2AClient" },
  "provider": { "name": "YawlA2AServer" },
  "interactions": [
    {
      "description": "a request to submit a new task",
      "request": {
        "method": "POST",
        "path": "/a2a/task",
        "headers": { "Content-Type": "application/json" },
        "body": { "taskName": "ProcessOrder", ... }
      },
      "response": {
        "status": 201,
        "headers": { "Content-Type": "application/json" },
        "body": { "taskId": "...", "status": "RECEIVED" }
      }
    }
  ]
}
```

### Pact File Format

- **Consumers**: Test class extends `PactConsumerTestExt`, uses `@Pact` methods
- **Providers**: Test class extends `PactVerificationInvocationContextProvider`, uses `@TestTemplate`
- **Location**: `target/pacts/<consumer>-<provider>.json`
- **Version Control**: Committed to repo to track protocol evolution

## Test Classes

### Consumer Tests

**File**: `test/org/yawlfoundation/yawl/integration/contract/A2AConsumerContractTest.java`

Consumer tests define what the client expects from the server:

```java
@Pact(consumer = CONSUMER_NAME)
RequestResponsePact submitTaskPact(PactDslWithProvider builder) {
    return builder
        .given("A2A server is ready to receive tasks")
        .uponReceiving("a request to submit a new task")
            .path("/a2a/task")
            .method("POST")
            .body(...)
        .willRespondWith()
            .status(201)
            .body(...)
        .toPact();
}

@Test
@PactTestFor(pactMethod = "submitTaskPact")
void consumerCanSubmitTask(MockServer mockServer) throws IOException, InterruptedException {
    // Use MockServer (injected by Pact)
    // Make actual HTTP request
    // Verify response
}
```

Key annotations:
- `@PactTestFor` - Links test to pact method and MockServer port
- `@Pact` - Defines contract; builder uses PactDslWithProvider fluent API
- `MockServer` - Injected HTTP mock server on random port

### Provider Tests

**File**: `test/org/yawlfoundation/yawl/integration/contract/A2AProviderContractTest.java`

Provider tests verify the server satisfies contracts:

```java
@Provider("YawlA2AServer")
@PactFolder("target/pacts")
class A2AProviderContractTest {
    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(...);
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyProviderAgainstAllPacts(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
```

Key annotations:
- `@Provider` - Provider name (must match consumer's `providerName`)
- `@PactFolder` - Directory containing pact files
- `@TestTemplate` + `@ExtendWith` - Parameterized test for each interaction

## A2A Protocol Contracts

### Operations

#### 1. Submit Task (POST /a2a/task)

**Purpose**: Client submits a workflow task for execution.

**Request**:
```json
{
  "taskName": "ProcessOrder",
  "workflowId": "order-wf-001",
  "caseId": "case-100",
  "payload": {
    "orderId": "ORD-123",
    "amount": 999.99
  }
}
```

**Response** (201 Created):
```json
{
  "taskId": "TASK-001",
  "status": "RECEIVED",
  "createdAt": "2026-02-20T10:00:00Z"
}
```

**Headers**: Authorization, X-Agent-ID

#### 2. Get Task Status (GET /a2a/task/{id})

**Purpose**: Client retrieves current status of a task.

**Response** (200 OK):
```json
{
  "taskId": "TASK-001",
  "status": "PROCESSING",
  "workflowId": "order-wf-001",
  "caseId": "case-100",
  "updatedAt": "2026-02-20T10:05:00Z"
}
```

#### 3. Update Task (PUT /a2a/task/{id})

**Purpose**: Client updates task with completion status and results.

**Request**:
```json
{
  "status": "COMPLETED",
  "result": {
    "output": "Success",
    "processedItems": 42
  }
}
```

**Response** (200 OK):
```json
{
  "taskId": "TASK-001",
  "status": "COMPLETED",
  "completedAt": "2026-02-20T10:10:00Z"
}
```

## MCP Protocol Contracts

### Operations

#### 1. Initialize (POST /mcp/initialize)

**Purpose**: Client initiates MCP connection and negotiates protocol version.

**Request**:
```json
{
  "protocolVersion": "2024-11-05",
  "clientInfo": {
    "name": "YawlMcpClient",
    "version": "6.0.0"
  }
}
```

**Response** (200 OK):
```json
{
  "protocolVersion": "2024-11-05",
  "serverInfo": {
    "name": "YawlMcpServer",
    "version": "6.0.0"
  },
  "capabilities": ["tools", "resources", "prompts"]
}
```

#### 2. List Tools (GET /mcp/tools/list)

**Purpose**: Client discovers available tools.

**Response** (200 OK):
```json
{
  "tools": [
    {
      "name": "launch_case",
      "description": "Launch a workflow case",
      "inputSchema": {
        "type": "object",
        "properties": {
          "specificationId": { "type": "string" }
        }
      }
    }
  ]
}
```

#### 3. Call Tool (POST /mcp/tools/call)

**Purpose**: Client invokes a workflow tool.

**Request**:
```json
{
  "toolName": "launch_case",
  "arguments": {
    "specificationId": "OrderProcessing",
    "version": "1.0",
    "caseData": {
      "customerId": "CUST-001"
    }
  }
}
```

**Response** (200 OK):
```json
{
  "caseId": "case-100",
  "status": "ACTIVE",
  "timestamp": "2026-02-20T10:00:00Z"
}
```

#### 4. Stream Events (GET /mcp/stream/case/{caseId})

**Purpose**: Client subscribes to case events via Server-Sent Events.

**Response** (200 OK, Content-Type: text/event-stream):
```
data: {"eventType":"TASK_ENABLED","caseId":"case-100"}

data: {"eventType":"TASK_COMPLETED","caseId":"case-100"}

```

## Resilience Patterns in Contracts

Pact tests validate resilience behavior:

### Timeout Handling

```java
@Pact(consumer = CONSUMER_NAME)
RequestResponsePact timeoutRespectedPact(PactDslWithProvider builder) {
    return builder
        .given("MCP server responds quickly")
        .uponReceiving("a request that respects 5s timeout")
            .path("/mcp/tools/call")
        .willRespondWith()
            .status(200)
        .toPact();
}

@Test
void consumerRespectsMcpTimeout(MockServer mockServer) {
    long start = System.currentTimeMillis();
    // Make request
    long duration = System.currentTimeMillis() - start;
    assertTrue(duration < 5000); // Within timeout
}
```

### Circuit Breaker Compliance

```java
@Pact(consumer = CONSUMER_NAME)
RequestResponsePact circuitBreakerPact(PactDslWithProvider builder) {
    return builder
        .given("MCP server temporarily unavailable")
        .uponReceiving("a request during circuit breaker open")
        .willRespondWith()
            .status(503)
            .body(new PactDslJsonBody()
                .stringValue("error", "Service temporarily unavailable")
                .stringValue("retryAfter", "5")
            )
        .toPact();
}
```

## Adding New A2A Contracts

To add a new A2A operation:

1. **Add Pact method** in `A2AConsumerContractTest`:

```java
@Pact(consumer = CONSUMER_NAME)
RequestResponsePact newOperationPact(PactDslWithProvider builder) {
    return builder
        .given("precondition")
        .uponReceiving("description of interaction")
            .path("/a2a/...")
            .method("POST|GET|PUT|DELETE")
            .body(...)
        .willRespondWith()
            .status(200)
            .body(...)
        .toPact();
}
```

2. **Add consumer test**:

```java
@Test
@PactTestFor(pactMethod = "newOperationPact")
void consumerCanCallNewOperation(MockServer mockServer) throws Exception {
    // Create HTTP request
    // Verify response
}
```

3. **Run tests**:

```bash
mvn test -Dtest=A2AConsumerContractTest -pl yawl-integration
```

Pact file is automatically generated in `target/pacts/`.

## Adding New MCP Contracts

Similar to A2A, add methods to `McpConsumerContractTest`:

```java
@Pact(consumer = CONSUMER_NAME)
RequestResponsePact newMcpOperationPact(PactDslWithProvider builder) {
    return builder
        .given("MCP server state")
        .uponReceiving("description")
            .path("/mcp/new-endpoint")
        .willRespondWith()
            .status(200)
        .toPact();
}
```

## Pact Broker Integration (Optional)

If using Pact Broker (SaaS or self-hosted) to manage contracts:

### Environment Setup

```bash
export PACT_BROKER_URL=https://broker.example.com
export PACT_BROKER_TOKEN=<your-api-token>
export PACT_BROKER_PUBLISH=true
export PACT_BROKER_VERIFY=true
```

### Configuration

Add to `pom.xml`:

```xml
<plugin>
  <groupId>au.com.dius</groupId>
  <artifactId>pact-foundation-maven</artifactId>
  <version>4.6.11</version>
  <configuration>
    <brokerUrl>${pact.broker.url}</brokerUrl>
    <brokerToken>${pact.broker.token}</brokerToken>
    <pactDirectory>target/pacts</pactDirectory>
    <consumerVersion>${project.version}</consumerVersion>
  </configuration>
  <executions>
    <execution>
      <phase>verify</phase>
      <goals>
        <goal>publish</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Publish Pacts

```bash
mvn pact:publish
```

### Can-I-Deploy Check

Before merging/releasing, verify compatibility:

```bash
mvn pact:can-i-deploy
```

## Troubleshooting

### Problem: Pact files not generated

**Solution**: Ensure consumer tests ran successfully:
```bash
mvn test -Dtest=*ConsumerContractTest -X
```

Check for test failures in logs. Pacts are only written if tests pass.

### Problem: Provider verification fails with "interaction not found"

**Solution**: Verify:
1. Pact file exists in `target/pacts/`
2. Provider name matches: `@Provider("YawlA2AServer")`
3. Interaction description matches exactly

### Problem: Timeout in contract test

**Solution**: Check MockServer port is accessible:
```java
MockServer mockServer; // Port assigned randomly by Pact
String url = mockServer.getUrl(); // e.g., "http://localhost:12345"
```

Ensure firewall allows localhost on random ports.

## Best Practices

1. **Keep contracts simple**: One interaction per pact method
2. **Use realistic data**: Mock data should match production patterns
3. **Test error cases**: Include 404, 500, 503 interactions
4. **Version protocol**: Document when contracts change
5. **Run both directions**: Consumer tests → Provider verification
6. **Commit pacts**: Version control enables history tracking
7. **Use Broker for CI/CD**: Enable "Can-I-Deploy" checks

## Protocol Evolution

When updating A2A or MCP protocol:

1. **Backward compatible change**: Add new optional fields (no pact update)
2. **Breaking change**: Update all consumer pacts, bump version
3. **Document change**: Add comment to pact method explaining why

Example:

```java
@Pact(consumer = CONSUMER_NAME)
RequestResponsePact submitTaskPactV2(PactDslWithProvider builder) {
    return builder
        // v2: Added optional timeout field for async processing
        .given("A2A server supports async tasks")
        .uponReceiving("a request to submit task with timeout")
            .path("/a2a/task")
            .body(new PactDslJsonBody()
                .stringValue("taskName", "...")
                // v2: New optional field
                .numberValue("timeoutSeconds", 300)
            )
        .toPact();
}
```

## Related Documentation

- **A2A Protocol**: `/docs/a2a-protocol.md`
- **MCP Protocol**: `/docs/mcp-protocol.md`
- **Resilience Patterns**: `/docs/resilience.md`
- **Pact Docs**: https://docs.pact.foundation/
