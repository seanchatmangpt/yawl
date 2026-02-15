# YAWL Autonomous Agents Architecture

## Overview

YAWL v5.2 introduces a **generic autonomous agent framework** that enables dynamic, self-organizing workflow execution through AI-powered reasoning and service discovery.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    YAWL Engine (Interface B)                │
│                 Enabled Work Items (REST API)               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Poll for work items
                         │
      ┌──────────────────┼──────────────────┐
      │                  │                  │
      ▼                  ▼                  ▼
┌──────────┐      ┌──────────┐      ┌──────────┐
│  Agent A │      │  Agent B │      │  Agent C │
│ (Shipper)│      │(Warehouse)│     │(Customer)│
└────┬─────┘      └────┬─────┘      └────┬─────┘
     │                 │                  │
     │ Discovery       │                  │
     └────────┬────────┴──────────────────┘
              │
              ▼
     ┌─────────────────┐
     │ A2A Protocol    │
     │ (/.well-known/  │
     │  agent.json)    │
     └─────────────────┘
              │
              ▼
     ┌─────────────────┐
     │ Z.AI Reasoning  │
     │ - Eligibility   │
     │ - Decision      │
     │ - Output Gen    │
     └─────────────────┘
```

## Core Components

### 1. GenericPartyAgent

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java`

The main autonomous agent implementation that:
- Polls YAWL Engine for enabled work items
- Discovers other agents via A2A protocol
- Reasons about work item eligibility using configured strategies
- Generates outputs using AI or programmatic logic
- Completes work items automatically

**Key Features:**
- YAML-based configuration (no hardcoded workflows)
- Pluggable strategy pattern for discovery, eligibility, decision making
- HTTP server for A2A discovery endpoint
- Circuit breaker and retry policies for resilience

### 2. Strategy Interfaces

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/strategies/`

- `DiscoveryStrategy`: How to find other agents (static YAML, A2A protocol, DNS-SD)
- `EligibilityReasoner`: Determine if work item matches agent capabilities
- `DecisionReasoner`: Decide which action to take for a work item
- `OutputGenerator`: Produce output data for work item completion

### 3. Configuration Model

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/AgentConfiguration.java`

YAML-driven configuration supporting:
- Agent identity and capabilities
- YAWL Engine connection details
- Discovery mechanism selection
- Z.AI integration settings
- Polling intervals and timeouts

### 4. Resilience Components

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/resilience/`

- `CircuitBreaker`: Prevents cascading failures in distributed systems
- `RetryPolicy`: Configurable retry with exponential backoff

### 5. Registry & Health Monitoring

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/registry/`

- `AgentInfo`: A2A discovery card representation
- `AgentHealthMonitor`: Background health checking for discovered agents

## Workflow Pattern

```
1. Agent starts up and reads YAML configuration
   ↓
2. HTTP server starts on configured port
   ↓
3. Polling loop begins:
   a. Fetch enabled work items from YAWL Engine
   b. For each work item:
      - Run eligibility reasoner (Z.AI or custom)
      - If eligible:
        * Run decision reasoner (what to do?)
        * Generate output (Z.AI or custom)
        * Complete work item via Interface B
   ↓
4. A2A Discovery (parallel):
   - Expose /.well-known/agent.json
   - Discover peer agents (if configured)
   - Monitor peer health
```

## Key Differences from Legacy Implementation

### Legacy (orderfulfillment package - DEPRECATED)

- Hardcoded for specific workflow (order fulfillment)
- Fixed capability descriptors (Shipper, Warehouse, CustomerService)
- Tightly coupled to specific task names
- Limited extensibility

### New (autonomous package)

- **Generic**: Works with ANY workflow specification
- **Configurable**: YAML-driven, no code changes needed
- **Extensible**: Plugin architecture for strategies
- **Resilient**: Circuit breakers, retries, health monitoring
- **AI-Powered**: Z.AI reasoning for eligibility and decisions
- **Discoverable**: A2A protocol support

## Technology Stack

- **Java 11+**: Core language
- **HttpServer** (com.sun.net.httpserver): Lightweight HTTP for A2A
- **SLF4J**: Logging abstraction
- **SnakeYAML**: YAML configuration parsing
- **Z.AI**: AI reasoning via ZHIPU API
- **Interface B**: YAWL Engine client interface

## Configuration Files

Agent configurations are stored in:
- `/home/user/yawl/build/autonomous-config/`

Examples:
- `shipper-agent.yaml`: Shipping operations agent
- `warehouse-agent.yaml`: Warehouse fulfillment agent
- `customer-service-agent.yaml`: Customer service agent

## Deployment

### Docker Compose

See `/home/user/yawl/docker-compose.yml` for production deployment.

### Simulation Mode

See `/home/user/yawl/docker-compose.simulation.yml` for development/testing.

## Extension Points

### Custom Discovery Strategy

Implement `DiscoveryStrategy` interface:

```java
public class MyDiscoveryStrategy implements DiscoveryStrategy {
    @Override
    public List<AgentInfo> discoverAgents() {
        // Your custom discovery logic
    }
}
```

### Custom Eligibility Reasoner

Implement `EligibilityReasoner` interface:

```java
public class MyEligibilityReasoner implements EligibilityReasoner {
    @Override
    public boolean isEligible(WorkItemRecord wir, AgentCapability capability,
                              List<AgentInfo> peers) {
        // Your custom eligibility logic
    }
}
```

### Custom Output Generator

Implement `OutputGenerator` interface:

```java
public class MyOutputGenerator implements OutputGenerator {
    @Override
    public String generateOutput(WorkItemRecord wir, String decision) {
        // Your custom output generation
    }
}
```

## Migration from Legacy

See [migration-guide.md](migration-guide.md) for detailed instructions on migrating from the deprecated `orderfulfillment` package to the new `autonomous` framework.

## API Documentation

See [api-documentation.md](api-documentation.md) for comprehensive interface documentation.

## Further Reading

- [Configuration Guide](configuration-guide.md): YAML configuration reference
- [Migration Guide](migration-guide.md): Upgrading from legacy agents
- [API Documentation](api-documentation.md): Interface specifications
- YAWL Manual: https://yawlfoundation.github.io/
- Z.AI Documentation: https://open.bigmodel.cn/dev/api
