# YAWL Pure Java 25 Agent Engine - REST API

## Overview

This document describes the REST API endpoints for the YAWL Pure Java 25 Agent Engine. The engine provides a Spring Boot web application running on port 8080 with JSON-based REST endpoints for managing autonomous agents and work items.

## Architecture

- **Framework**: Spring Boot 3.5.11
- **Java Version**: Java 25+
- **Data Format**: JSON (via Jackson)
- **Server**: Embedded Tomcat on port 8080
- **Health Checks**: Kubernetes-compatible liveness and readiness probes

## Quick Start

```bash
# Launch the engine
java -jar yawl-engine.jar

# Test health endpoint
curl http://localhost:8080/yawl/actuator/health/live

# List agents
curl http://localhost:8080/yawl/agents

# Create agent
curl -X POST http://localhost:8080/yawl/agents \
  -H "Content-Type: application/json" \
  -d '{
    "workflowId": "order-processing",
    "name": "Order Processing Workflow",
    "version": "1.0",
    "description": "Handles order processing",
    "specificationXml": "<yawl>...</yawl>"
  }'
```

## Base URL

All endpoints are prefixed with:
```
http://localhost:8080/yawl
```

## API Endpoints

### Agent Management

#### List All Agents
```
GET /agents
```

**Response** (200 OK):
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "status": "RUNNING",
    "workflowId": "workflow-1",
    "workCount": 42,
    "heartbeatTTL": 55000,
    "uptime": 3600000,
    "registeredAt": "2026-02-28T10:00:00Z",
    "lastHeartbeat": "2026-02-28T10:59:00Z"
  }
]
```

#### Get Single Agent
```
GET /agents/{id}
```

**Path Parameters**:
- `id` (UUID): Agent identifier

**Response** (200 OK):
Returns single AgentDTO

**Response** (404 Not Found):
```json
{
  "error": "Agent not found"
}
```

#### Create New Agent
```
POST /agents
Content-Type: application/json
```

**Request Body**:
```json
{
  "workflowId": "unique-workflow-id",
  "name": "Workflow Display Name",
  "version": "1.0",
  "description": "Workflow description",
  "specificationXml": "<yawl>...</yawl>"
}
```

**Response** (201 Created):
Returns created AgentDTO with assigned UUID

**Response** (400 Bad Request):
Invalid or missing required fields

#### Stop Agent
```
DELETE /agents/{id}
```

**Path Parameters**:
- `id` (UUID): Agent identifier to stop

**Response** (204 No Content):
Agent successfully stopped

**Response** (404 Not Found):
Agent not found

#### List Healthy Agents
```
GET /agents/healthy
```

**Response** (200 OK):
Returns array of healthy (non-expired, non-failed) agents

#### Get Agent Metrics
```
GET /agents/metrics
```

**Response** (200 OK):
```json
{
  "agentCount": 10,
  "healthyAgentCount": 9,
  "queueSize": 25,
  "throughput": 45.5,
  "avgLatency": 234,
  "oldestItemAge": 5000,
  "timestamp": "2026-02-28T11:30:00Z"
}
```

### Work Item Management

#### List Work Items
```
GET /workitems
```

**Query Parameters**:
- `agent` (UUID, optional): Filter by assigned agent
- `page` (int, default 0): Page number (0-based)
- `limit` (int, default 100, max 100): Items per page

**Response** (200 OK):
```json
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440000",
    "taskName": "ProcessPayment",
    "status": "ACTIVE",
    "assignedAgent": "550e8400-e29b-41d4-a716-446655440000",
    "createdTime": "2026-02-28T10:30:00Z",
    "completedTime": null
  }
]
```

#### Get Work Items for Specific Agent
```
GET /workitems?agent={agentId}
```

**Query Parameters**:
- `agent` (UUID, required): Agent identifier

**Response** (200 OK):
Returns array of WorkItemDTOs assigned to the agent

#### Create Work Item
```
POST /workitems
Content-Type: application/json
```

**Request Body**:
```json
{
  "taskName": "ProcessPayment",
  "caseId": "case-12345",
  "payload": "{\"amount\": 100.00, \"currency\": \"USD\"}"
}
```

**Response** (201 Created):
Returns created WorkItemDTO with assigned UUID

**Response** (400 Bad Request):
Invalid or missing taskName

#### Get Work Item Statistics
```
GET /workitems/stats
```

**Response** (200 OK):
```json
{
  "queueSize": 25,
  "totalItems": 1000,
  "completedItems": 975,
  "oldestItemAge": 5000,
  "throughput": "45.20 items/min",
  "timestamp": "2026-02-28T11:30:00Z"
}
```

### Health Checks

#### Liveness Probe
```
GET /actuator/health/live
```

**Purpose**: Kubernetes liveness probe - indicates if JVM is alive

**Response** (200 OK):
```json
{
  "status": "UP",
  "checks": {
    "jvm": "UP",
    "memory": "OK"
  },
  "timestamp": "2026-02-28T11:30:00Z"
}
```

**Response** (500 Internal Server Error):
JVM is not responsive

#### Readiness Probe
```
GET /actuator/health/ready
```

**Purpose**: Kubernetes readiness probe - indicates if engine is ready to accept work

**Response** (200 OK):
```json
{
  "status": "UP",
  "checks": {
    "agents": "READY",
    "agentCount": 5,
    "database": "CONNECTED"
  },
  "timestamp": "2026-02-28T11:30:00Z"
}
```

**Response** (500 Internal Server Error):
Engine is not ready (no agents available, database down, etc.)

#### Overall Health
```
GET /actuator/health
```

**Response** (200 OK):
```json
{
  "status": "UP",
  "checks": {},
  "timestamp": "2026-02-28T11:30:00Z"
}
```

## Data Models

### AgentDTO

Represents an autonomous agent in the workflow engine.

```java
record AgentDTO(
    UUID id,              // Unique agent identifier
    String status,        // RUNNING, IDLE, FAILED
    String workflowId,    // Workflow this agent executes
    long workCount,       // Total work items processed
    long heartbeatTTL,    // Milliseconds until heartbeat expires
    long uptime,          // Milliseconds since registration
    Instant registeredAt, // When agent was registered
    Instant lastHeartbeat // Most recent heartbeat timestamp
)
```

### WorkItemDTO

Represents a unit of work in the queue.

```java
record WorkItemDTO(
    UUID id,              // Unique work item identifier
    String taskName,      // Task being executed
    String status,        // RECEIVED, ENABLED, FIRED, ACTIVE, COMPLETED
    UUID assignedAgent,   // Agent handling this item (nullable)
    Instant createdTime,  // When work item was created
    Instant completedTime // When work item completed (nullable)
)
```

### MetricsDTO

Aggregated engine performance metrics.

```java
record MetricsDTO(
    int agentCount,        // Total agents
    int healthyAgentCount, // Healthy agents
    int queueSize,         // Pending work items
    double throughput,     // Items per minute
    long avgLatency,       // Average processing time in ms
    long oldestItemAge,    // Age of oldest pending item in ms
    Instant timestamp      // When metrics were captured
)
```

### HealthDTO

Health check response.

```java
record HealthDTO(
    String status,           // UP, DOWN, OUT_OF_SERVICE, UNKNOWN
    Map<String, Object> checks, // Component health checks
    Instant timestamp        // When health was checked
)
```

### WorkflowDefDTO

Workflow specification for agent creation.

```java
record WorkflowDefDTO(
    String workflowId,      // Unique workflow identifier
    String name,            // Display name
    String version,         // Version string
    String description,     // Optional description
    String specificationXml // YAWL XML specification
)
```

### WorkItemCreateDTO

Request to create a new work item.

```java
record WorkItemCreateDTO(
    String taskName,  // Task to execute (required)
    String caseId,    // Case identifier (optional)
    String payload    // Work data as JSON string (optional)
)
```

## HTTP Status Codes

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET, successful readiness check |
| 201 | Created | POST successful, resource created |
| 204 | No Content | DELETE successful, no response body |
| 400 | Bad Request | Invalid input (missing fields, validation failed) |
| 404 | Not Found | Resource does not exist |
| 500 | Internal Server Error | Server error, liveness/readiness failed |

## Error Handling

All error responses include an error message:

```json
{
  "error": "Agent not found",
  "timestamp": "2026-02-28T11:30:00Z",
  "status": 404
}
```

## Response Format

All responses are JSON with the following characteristics:

- **Timestamps**: ISO 8601 format (`2026-02-28T11:30:00Z`)
- **UUIDs**: Standard UUID string format (no hyphens removed)
- **Numbers**: Decimal for floating point (throughput), integers for counters
- **Durations**: Milliseconds as long integers
- **Nulls**: Explicitly null for optional fields (e.g., completedTime before completion)

## Kubernetes Integration

The engine provides health endpoints for Kubernetes probes:

```yaml
# In Kubernetes Deployment spec
livenessProbe:
  httpGet:
    path: /yawl/actuator/health/live
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 30

readinessProbe:
  httpGet:
    path: /yawl/actuator/health/ready
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 10
```

## Configuration

Edit `application.properties` to customize:

```properties
server.port=8080                                    # HTTP port
server.servlet.context-path=/yawl                  # Context path
logging.level.root=INFO                            # Logging level
spring.jackson.serialization.indent-output=true    # Pretty JSON
```

## Testing

Run integration tests:

```bash
mvn test -pl yawl-engine -Dtest=*ControllerTest
```

Test categories:
- `AgentControllerTest`: Agent lifecycle operations
- `WorkItemControllerTest`: Work item queue management
- `HealthControllerTest`: Health check endpoints

## Limitations and Future Enhancements

- **Current**: In-memory agent and work item storage (not persistent)
- **Future**: Database-backed persistence via Hibernate
- **Current**: No authentication/authorization
- **Future**: JWT token-based security
- **Current**: Synchronous request/response only
- **Future**: WebSocket support for streaming updates

## Contact & Support

For issues or questions about the REST API, refer to:
- Main documentation: `/yawl/docs/README.md`
- Architecture patterns: `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- Test fixtures: `yawl-engine/src/test/java/org/yawlfoundation/yawl/engine/api/`
