# Autonomous Agent Configuration Guide

## Overview

YAWL autonomous agents are configured using YAML files. This guide provides comprehensive documentation of all configuration options.

## Configuration File Location

Store agent configurations in:
```
/home/user/yawl/build/autonomous-config/
```

## Basic Configuration Structure

```yaml
agent:
  id: "unique-agent-identifier"
  name: "Human-Readable Agent Name"
  port: 8081
  capability:
    domain: "logistics"
    skills:
      - "shipping"
      - "route-planning"
    constraints:
      maxWeight: 1000
      regions:
        - "US-WEST"
        - "US-EAST"

engine:
  url: "http://localhost:8080/yawl"
  username: "admin"
  password: "YAWL"
  pollInterval: 5000
  enabledOnly: true

strategies:
  discovery: "a2a"
  eligibility: "zai"
  decision: "zai"
  output: "zai"

zai:
  apiKey: "${ZHIPU_API_KEY}"
  model: "glm-4-flash"
  temperature: 0.1
  maxTokens: 1000

resilience:
  retryAttempts: 3
  retryBackoffMs: 1000
  circuitBreakerThreshold: 5
  circuitBreakerTimeoutMs: 30000

discovery:
  staticAgents:
    - url: "http://warehouse-agent:8082"
    - url: "http://customer-service-agent:8083"
  healthCheckInterval: 60000
```

## Section-by-Section Reference

### `agent` Section

Defines agent identity and capabilities.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `id` | String | Yes | - | Unique identifier (used in A2A discovery) |
| `name` | String | Yes | - | Human-readable name |
| `port` | Integer | Yes | - | HTTP server port for A2A endpoint |
| `capability` | Object | Yes | - | Agent capability descriptor |

#### `capability` Subsection

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `domain` | String | Yes | - | Business domain (e.g., "logistics", "finance") |
| `skills` | List[String] | Yes | - | List of skills this agent possesses |
| `constraints` | Map | No | `{}` | Additional constraints (free-form key-value) |

**Example:**
```yaml
agent:
  id: "shipper-001"
  name: "West Coast Shipper"
  port: 8081
  capability:
    domain: "logistics"
    skills:
      - "ground-shipping"
      - "air-freight"
    constraints:
      maxWeight: 500
      regions: ["CA", "OR", "WA"]
      certifications: ["ISO-9001", "HAZMAT"]
```

### `engine` Section

Configures YAWL Engine connection.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `url` | String | Yes | - | YAWL Engine base URL (e.g., `http://localhost:8080/yawl`) |
| `username` | String | Yes | - | Interface B username |
| `password` | String | Yes | - | Interface B password |
| `pollInterval` | Integer | No | `5000` | Work item polling interval (milliseconds) |
| `enabledOnly` | Boolean | No | `true` | Only fetch enabled work items |

**Environment Variable Support:**
```yaml
engine:
  url: "${YAWL_ENGINE_URL}"
  username: "${YAWL_USERNAME}"
  password: "${YAWL_PASSWORD}"
```

**Example:**
```yaml
engine:
  url: "http://yawl-engine:8080/yawl"
  username: "admin"
  password: "YAWL"
  pollInterval: 3000
  enabledOnly: true
```

### `strategies` Section

Selects strategy implementations for agent behavior.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `discovery` | String | No | `"none"` | Discovery strategy: `"none"`, `"static"`, `"a2a"`, `"dns-sd"` |
| `eligibility` | String | No | `"default"` | Eligibility reasoner: `"default"`, `"zai"`, `"rule-based"` |
| `decision` | String | No | `"default"` | Decision reasoner: `"default"`, `"zai"`, `"simple"` |
| `output` | String | No | `"zai"` | Output generator: `"zai"`, `"template"`, `"passthrough"` |

**Discovery Strategy Options:**

- `none`: No peer discovery
- `static`: Use `discovery.staticAgents` list
- `a2a`: Active A2A protocol discovery (requires peer URLs)
- `dns-sd`: DNS Service Discovery (future)

**Eligibility Reasoner Options:**

- `default`: Match task name to agent skills (simple substring match)
- `zai`: AI-powered reasoning using Z.AI
- `rule-based`: Custom rule engine (requires configuration)

**Decision Reasoner Options:**

- `default`: Always accept eligible work items
- `zai`: AI-powered decision making using Z.AI
- `simple`: First-come-first-served

**Output Generator Options:**

- `zai`: AI generates output XML based on input data and task schema
- `template`: Use predefined XML templates
- `passthrough`: Copy input to output (for simple tasks)

**Example:**
```yaml
strategies:
  discovery: "a2a"
  eligibility: "zai"
  decision: "zai"
  output: "zai"
```

### `zai` Section

Z.AI configuration (required if any strategy uses `"zai"`).

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `apiKey` | String | Yes | - | ZHIPU API key (supports `${ZHIPU_API_KEY}`) |
| `model` | String | No | `"glm-4-flash"` | Z.AI model name |
| `temperature` | Float | No | `0.1` | Sampling temperature (0.0-1.0) |
| `maxTokens` | Integer | No | `1000` | Maximum response tokens |
| `timeout` | Integer | No | `30000` | API timeout (milliseconds) |

**Available Models:**
- `glm-4-flash`: Fast, lightweight (recommended)
- `glm-4`: Standard reasoning model
- `glm-4-plus`: Advanced reasoning

**Example:**
```yaml
zai:
  apiKey: "${ZHIPU_API_KEY}"
  model: "glm-4-flash"
  temperature: 0.1
  maxTokens: 1000
  timeout: 30000
```

### `resilience` Section

Configures fault tolerance mechanisms.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `retryAttempts` | Integer | No | `3` | Number of retry attempts |
| `retryBackoffMs` | Integer | No | `1000` | Initial backoff (exponential) |
| `circuitBreakerThreshold` | Integer | No | `5` | Failures before opening circuit |
| `circuitBreakerTimeoutMs` | Integer | No | `30000` | Circuit breaker timeout (milliseconds) |

**Retry Behavior:**
- Attempt 1: Immediate
- Attempt 2: Wait `retryBackoffMs` (e.g., 1000ms)
- Attempt 3: Wait `retryBackoffMs * 2` (e.g., 2000ms)
- Attempt N: Wait `retryBackoffMs * 2^(N-2)`

**Circuit Breaker States:**
- **CLOSED**: Normal operation
- **OPEN**: Fail fast (no requests sent)
- **HALF-OPEN**: Test with single request after timeout

**Example:**
```yaml
resilience:
  retryAttempts: 3
  retryBackoffMs: 1000
  circuitBreakerThreshold: 5
  circuitBreakerTimeoutMs: 30000
```

### `discovery` Section

Configures peer agent discovery (when `strategies.discovery` is not `"none"`).

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `staticAgents` | List[Object] | No | `[]` | Static list of peer agent URLs |
| `healthCheckInterval` | Integer | No | `60000` | Health check interval (milliseconds) |

**Static Agent Entry:**
```yaml
- url: "http://agent-hostname:port"
  id: "optional-agent-id"
  name: "optional-agent-name"
```

**Example:**
```yaml
discovery:
  staticAgents:
    - url: "http://warehouse-agent:8082"
      id: "warehouse-001"
      name: "Primary Warehouse"
    - url: "http://customer-service-agent:8083"
      id: "cs-001"
      name: "Customer Service"
  healthCheckInterval: 60000
```

## Complete Examples

### Example 1: Shipper Agent (Full Z.AI Integration)

```yaml
agent:
  id: "shipper-west-001"
  name: "West Coast Shipper"
  port: 8081
  capability:
    domain: "logistics"
    skills:
      - "ground-shipping"
      - "air-freight"
      - "route-optimization"
    constraints:
      maxWeight: 500
      regions: ["CA", "OR", "WA"]

engine:
  url: "http://yawl-engine:8080/yawl"
  username: "admin"
  password: "YAWL"
  pollInterval: 5000
  enabledOnly: true

strategies:
  discovery: "a2a"
  eligibility: "zai"
  decision: "zai"
  output: "zai"

zai:
  apiKey: "${ZHIPU_API_KEY}"
  model: "glm-4-flash"
  temperature: 0.1
  maxTokens: 1000

resilience:
  retryAttempts: 3
  retryBackoffMs: 1000
  circuitBreakerThreshold: 5
  circuitBreakerTimeoutMs: 30000

discovery:
  staticAgents:
    - url: "http://warehouse-agent:8082"
    - url: "http://customer-service-agent:8083"
  healthCheckInterval: 60000
```

### Example 2: Warehouse Agent (Simple Rule-Based)

```yaml
agent:
  id: "warehouse-primary"
  name: "Primary Warehouse"
  port: 8082
  capability:
    domain: "warehouse"
    skills:
      - "inventory-management"
      - "picking"
      - "packing"
    constraints:
      location: "Los Angeles"
      capacity: 10000

engine:
  url: "${YAWL_ENGINE_URL}"
  username: "${YAWL_USERNAME}"
  password: "${YAWL_PASSWORD}"
  pollInterval: 3000

strategies:
  discovery: "static"
  eligibility: "default"
  decision: "simple"
  output: "template"

resilience:
  retryAttempts: 5
  retryBackoffMs: 500
  circuitBreakerThreshold: 10
  circuitBreakerTimeoutMs: 60000

discovery:
  staticAgents:
    - url: "http://shipper-west-001:8081"
```

### Example 3: Customer Service Agent (Minimal Configuration)

```yaml
agent:
  id: "cs-001"
  name: "Customer Service"
  port: 8083
  capability:
    domain: "customer-service"
    skills:
      - "order-inquiry"
      - "complaint-handling"

engine:
  url: "http://localhost:8080/yawl"
  username: "admin"
  password: "YAWL"

strategies:
  discovery: "none"
  eligibility: "default"
  decision: "default"
  output: "passthrough"
```

## Environment Variables

All configuration values support environment variable substitution using `${VAR_NAME}` syntax:

```yaml
engine:
  url: "${YAWL_ENGINE_URL}"
  username: "${YAWL_USERNAME}"
  password: "${YAWL_PASSWORD}"

zai:
  apiKey: "${ZHIPU_API_KEY}"
```

**Required Environment Variables:**
- `ZHIPU_API_KEY`: Required if using Z.AI strategies
- `YAWL_ENGINE_URL`: Optional (can hardcode in YAML)
- `YAWL_USERNAME`: Optional (can hardcode in YAML)
- `YAWL_PASSWORD`: Optional (can hardcode in YAML)

## Validation

The configuration loader validates:
- All required fields are present
- Port numbers are in valid range (1-65535)
- URLs are well-formed
- Strategy names are recognized
- Z.AI config present if Z.AI strategies selected

**Error Example:**
```
Configuration error: Missing required field 'agent.id'
Configuration error: Invalid strategy 'eligibility': unknown-strategy
Configuration error: Z.AI strategy selected but zai.apiKey not configured
```

## Loading Configuration

### From Java

```java
AgentConfiguration config = AgentConfiguration.fromYaml(
    new File("/path/to/config.yaml")
);
```

### From Docker

Mount configuration as volume:
```yaml
services:
  shipper-agent:
    image: yawl/autonomous-agent:5.2
    volumes:
      - ./config/shipper-agent.yaml:/config/agent.yaml
    environment:
      - ZHIPU_API_KEY=${ZHIPU_API_KEY}
```

## Troubleshooting

### Issue: Agent not finding work items

**Check:**
- `engine.url` is correct
- `engine.username` and `engine.password` are valid
- YAWL Engine is running and accessible
- Work items are actually enabled

### Issue: Z.AI strategies failing

**Check:**
- `ZHIPU_API_KEY` environment variable is set
- API key is valid
- Network access to ZHIPU API (api.bigmodel.cn)
- `zai.timeout` is sufficient (try increasing)

### Issue: Peer discovery not working

**Check:**
- `strategies.discovery` is set to `"a2a"` or `"static"`
- Peer agent URLs are correct and accessible
- Peer agents have started their HTTP servers
- Network connectivity between agents

## Best Practices

1. **Use environment variables** for sensitive data (passwords, API keys)
2. **Start with minimal config** and add complexity as needed
3. **Test with `strategies.discovery: "none"` first** to verify basic functionality
4. **Use longer `pollInterval`** for production (reduces load on engine)
5. **Configure circuit breakers** to prevent cascading failures
6. **Monitor health check intervals** - don't poll peers too frequently

## Schema Reference

Full JSON Schema available at:
```
/home/user/yawl/schema/autonomous-agent-config-schema.json
```

## Further Reading

- [API Documentation](api-documentation.md): Interface specifications
- [Migration Guide](migration-guide.md): Upgrading from legacy agents
- [README](README.md): Architecture overview
