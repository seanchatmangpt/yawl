# YAWL AGI Orchestration - Architecture Documentation

**Version**: 5.2
**Date**: February 2026

---

## Overview

This directory contains comprehensive architecture documentation for the YAWL AGI Orchestration Platform, including C4 model diagrams, deployment architectures, and AGI swarm coordination patterns.

---

## Documentation Structure

```
docs/architecture/
├── README.md                           # This file
├── C4-DIAGRAMS.md                      # C4 diagrams index and overview
├── diagrams/
│   ├── plantuml/                       # PlantUML source files
│   │   ├── c4-level1-context.puml      # System context diagram
│   │   ├── c4-level2-containers.puml   # Container architecture
│   │   ├── c4-level3-engine.puml       # Engine components
│   │   ├── c4-level3-mcp.puml          # MCP server components
│   │   ├── c4-level3-a2a.puml          # A2A coordination components
│   │   ├── c4-level4-code.puml         # Code structure (classes)
│   │   ├── deployment-docker.puml      # Docker Compose deployment
│   │   └── agi-patterns.puml           # AGI orchestration patterns
│   ├── mermaid/                        # Mermaid diagram sources (future)
│   └── rendered/                       # Rendered SVG/PNG outputs
└── testing/
    └── DOCKER_COMPOSE_TEST_STRATEGY.md # Production testing strategy
```

---

## Quick Start

### Viewing Diagrams

#### Option 1: VS Code with PlantUML Extension

```bash
# Install PlantUML extension for VS Code
code --install-extension jebbs.plantuml

# Open any .puml file and use preview
```

#### Option 2: PlantUML CLI

```bash
# Install PlantUML
brew install plantuml  # macOS
apt-get install plantuml  # Ubuntu

# Render all diagrams to SVG
plantuml -tsvg docs/architecture/diagrams/plantuml/*.puml -o ../rendered/

# Render to PNG
plantuml -tpng docs/architecture/diagrams/plantuml/*.puml -o ../rendered/
```

#### Option 3: Online PlantUML Server

Visit [plantuml.com/plantuml](http://www.plantuml.com/plantuml/) and paste diagram content.

#### Option 4: Structurizr Lite (Recommended for Interactive Exploration)

```bash
# Run Structurizr Lite with Docker
docker run -it --rm -p 8080:8080 \
  -v $(pwd)/docs/architecture:/usr/local/structurizr \
  structurizr/lite

# Open browser: http://localhost:8080
```

---

## Diagram Index

### C4 Level 1: System Context

**File**: `diagrams/plantuml/c4-level1-context.puml`

**Description**: Shows how YAWL fits in the AGI ecosystem, including AI agents, human users, and external systems.

**Key Elements**:
- YAWL Engine (core system)
- AGI Swarm, LLM-based Agents, Specialist Agents (primary users)
- MCP Clients, A2A Network (integration systems)
- Vector databases, knowledge graphs, monitoring systems

**Blue Ocean Innovations Highlighted**:
- First formal BPM for AGI swarms
- Petri net semantics for agent coordination
- MCP/A2A native integration

**View**: Use PlantUML viewer or render to image

---

### C4 Level 2: Container Diagram

**File**: `diagrams/plantuml/c4-level2-containers.puml`

**Description**: Runtime containers and their interactions in the YAWL platform.

**Key Containers**:
- **YAWL Engine**: Core workflow orchestration (Java 21, Tomcat)
- **MCP Server**: AI agent integration via Model Context Protocol
- **A2A Server**: Agent-to-Agent coordination for swarms
- **Event Notifier**: Real-time event streaming (WebSocket/SSE)
- **Resource Service**: Agent allocation and management
- **Worklet Service**: Dynamic workflow adaptation

**Data Stores**:
- PostgreSQL: Workflow database
- Redis: Cache and distributed locks
- Vector DB (Pinecone/Weaviate): Agent memory
- ClickHouse: Process mining database

**Infrastructure**:
- API Gateway (Kong/Nginx)
- Message Queue (Kafka)
- Monitoring (Prometheus/Grafana)

---

### C4 Level 3: Component Diagrams

#### Engine Components

**File**: `diagrams/plantuml/c4-level3-engine.puml`

**Description**: Internal components of the YAWL Engine container.

**Key Components**:
- **Interface B**: Client operations for agents (launch cases, complete work items)
- **YEngine**: Main orchestrator, manages workflow lifecycle
- **YNetRunner**: Executes workflow nets, manages token flow
- **YTaskManager**: Work item lifecycle management
- **YPersistenceManager**: Hibernate-based persistence
- **YEventDispatcher**: Event notification system

---

#### MCP Server Components

**File**: `diagrams/plantuml/c4-level3-mcp.puml`

**Description**: Model Context Protocol integration components.

**Key Components**:
- **YawlMcpPromptProvider**: Context-aware prompt generation (core innovation)
- **CapabilityMatcher**: ML-based agent-to-task matching
- **TaskRecommender**: Recommendation engine for optimal task allocation
- **RAG Engine**: Retrieval-Augmented Generation for context enhancement
- **Memory Client**: Vector database integration for agent memory

**Blue Ocean**: First BPM system to generate AI prompts from workflow state!

---

#### A2A Coordination Components

**File**: `diagrams/plantuml/c4-level3-a2a.puml`

**Description**: Agent-to-Agent coordination server components.

**Key Components**:
- **TaskNegotiationEngine**: Contract Net Protocol, auctions
- **ConsensusEngine**: Raft/Paxos for swarm decisions
- **AgentRegistry**: Service discovery for agents
- **SwarmOptimizer**: Particle Swarm Optimization for task allocation
- **CollectiveLearning**: Federated learning across agent swarm

**Blue Ocean**: Formal workflow engine as AGI swarm coordinator!

---

### C4 Level 4: Code Structure

**File**: `diagrams/plantuml/c4-level4-code.puml`

**Description**: Key classes and their relationships.

**Key Classes**:
- `YEngine`: Main engine with Interface A/B/E/X implementations
- `YNetRunner`: Workflow net executor
- `YWorkItem`: Unit of work for agents
- `YawlMcpPromptProvider`: AI prompt generator
- `A2AServer`: Agent coordination server
- `YawlEventNotifier`: Real-time event streaming

**Package Structure**:
- `org.yawlfoundation.yawl.engine`: Core engine
- `org.yawlfoundation.yawl.elements`: Workflow element model
- `org.yawlfoundation.yawl.integration.mcp`: MCP integration
- `org.yawlfoundation.yawl.integration.a2a`: A2A coordination

---

### Deployment: Docker Compose

**File**: `diagrams/plantuml/deployment-docker.puml`

**Description**: Docker Compose deployment architecture for dev/test/production.

**Key Features**:
- All containers with health checks
- Volume mounts for persistence
- Environment configuration
- Port mappings
- Service dependencies
- Network isolation

**Docker Compose Services**:
- Core: yawl-engine, yawl-mcp-server, yawl-a2a-server, yawl-event-notifier
- Data: yawl-postgres, yawl-redis, yawl-kafka, yawl-vector-db, yawl-clickhouse
- Infrastructure: yawl-api-gateway, yawl-web-console
- Observability: prometheus, grafana, jaeger, elasticsearch, logstash, kibana

**Commands**:
```bash
# Start full stack
docker-compose up -d

# Scale engines
docker-compose up -d --scale yawl-engine=3

# View logs
docker-compose logs -f yawl-engine
```

---

### AGI Orchestration Patterns

**File**: `diagrams/plantuml/agi-patterns.puml`

**Description**: Shows how YAWL workflow patterns map to AGI swarm coordination.

**Pattern Categories**:

1. **Control Flow → Swarm Coordination**
   - Sequence → Sequential agent execution
   - Parallel Split → Fork-Join pattern
   - Synchronization → Barrier sync
   - Exclusive Choice → Leader decision
   - Multi-Choice → Swarm voting
   - OR-Join → Partial synchronization

2. **Resource Patterns → Agent Allocation**
   - Role-Based → Capability matching
   - Work Distribution → Task marketplace (auctions)
   - Retention → Agent affinity
   - Load Balancing → Swarm load balancing

3. **Data Patterns → Agent Communication**
   - Task Data → Structured I/O
   - Case Data → Shared memory (vector DB)
   - Environment Data → Knowledge graphs
   - Triggers → Event-driven agents

4. **Exception Patterns → Fault Tolerance**
   - Retry → Agent retry with backoff
   - Compensation → Saga pattern
   - Escalation → Human-in-the-loop
   - Substitution → Agent failover

**Blue Ocean Innovations** (Novel AGI patterns):
- 🧠 **Collective Intelligence**: Federated learning across swarm
- 🎯 **Dynamic Re-Planning**: Agents modify workflows at runtime
- 🤝 **Emergent Coordination**: Self-organizing agents
- 🔮 **Predictive Allocation**: ML-based task routing
- 🌊 **Context-Aware Prompting**: MCP prompt generation
- 🎪 **Multi-Agent Debate**: Consensus through deliberation

---

## Testing Documentation

### Production Testing Strategy

**File**: `../testing/DOCKER_COMPOSE_TEST_STRATEGY.md`

**Description**: Comprehensive testing strategy for production hardening using Docker Compose.

**Test Categories**:
1. **Integration Tests**: Real service interactions (DB, cache, MCP, A2A, events)
2. **End-to-End Tests**: Complete business scenarios (order fulfillment)
3. **Performance Tests**: Load, stress, soak testing with k6
4. **Security Tests**: Auth, input validation, dependency scanning
5. **Chaos Engineering**: Network chaos, container kills, resource limits
6. **Compliance Tests**: GDPR, SOC2, audit logging

**Key Principles**:
- ✅ Chicago School TDD (real integrations, no mocks)
- ✅ Toyota Production System (fail fast, built-in quality)
- ✅ Fortune 5 standards (production-grade testing)

---

## Architectural Principles

### Fortune 5 Production Standards

1. **No Mock Implementations**
   - All integrations use real services
   - Docker Compose provides production-like environment
   - Fail explicitly if dependencies unavailable

2. **No TODO/FIXME Patterns**
   - All code is production-ready
   - Throw `UnsupportedOperationException` with implementation guide if incomplete
   - No deferred work markers

3. **Real Event Streaming**
   - WebSocket, SSE, HTTP polling
   - Thread-safe concurrent delivery
   - Guaranteed ordering and delivery

4. **Formal Verification**
   - Petri net semantics for correctness
   - Soundness, deadlock-freedom, livelock-freedom
   - Process mining validates actual behavior

### Blue Ocean Innovations

1. **YAWL as AGI Orchestration Layer**
   - First formal BPM for AI agent coordination
   - Uncontested market space
   - Category creation: "Formal Workflow Orchestration for Multi-Agent AI"

2. **MCP/A2A Integration**
   - Model Context Protocol for AI agents
   - Agent-to-Agent coordination primitives
   - Real-time agent communication

3. **Process Mining for AI Behavior**
   - Log agent interactions
   - Discover emergent coordination patterns
   - Formalize as reusable YAWL patterns

---

## Academic Foundation

### Based on van der Aalst et al.

This architecture follows the formal methods of **Wil van der Aalst** and the YAWL research team:

- **Petri Net Semantics**: Formal mathematical foundation
- **Workflow Patterns**: Comprehensive pattern catalog
- **Process Mining**: Discovering actual behavior from event logs
- **Soundness Verification**: Provable correctness properties

**Key Publications**:
- van der Aalst, W.M.P. & ter Hofstede, A.H.M. (2005). "YAWL: Yet Another Workflow Language"
- van der Aalst, W.M.P. (2016). "Process Mining: Data Science in Action"
- van der Aalst, W.M.P. (2013). "Business Process Management: A Comprehensive Survey"

### PhD Thesis Foundation

These diagrams form the basis for a PhD thesis on:

**Title**: *Formal Workflow Orchestration for Multi-Agent AI Systems: Extending YAWL for AGI Swarm Coordination*

**Research Questions**:
1. How can formal workflow languages orchestrate autonomous AI agents?
2. What new coordination patterns emerge in AGI swarms?
3. How does process mining validate AI agent behavior?
4. What are the theoretical foundations for agent-aware BPM?

**Contributions**:
- Formal semantics for AI agent coordination
- MCP integration architecture for BPM systems
- Process mining techniques for multi-agent systems
- Production-grade implementation and evaluation

---

## Contributing

### Adding New Diagrams

1. Create `.puml` file in appropriate directory
2. Follow C4 model conventions
3. Include notes explaining innovations
4. Render to verify correctness
5. Update this README with description

### Diagram Conventions

- Use C4-PlantUML standard library
- Include legends and notes
- Highlight blue ocean innovations
- Show Fortune 5 production features
- Reference formal semantics where applicable

---

## Related Documentation

- [CLAUDE.md](../../CLAUDE.md) - Development standards and guidelines
- [INTEGRATION_GUIDE.md](../../INTEGRATION_GUIDE.md) - MCP/A2A integration
- [Docker Compose Test Strategy](../testing/DOCKER_COMPOSE_TEST_STRATEGY.md) - Production testing

---

## Questions?

For questions about architecture or diagrams:
- Review [CLAUDE.md](../../CLAUDE.md) for coding standards
- Check [C4-DIAGRAMS.md](./C4-DIAGRAMS.md) for diagram index
- See PlantUML documentation: https://plantuml.com

---

**Last Updated**: February 2026
**Maintained By**: YAWL AGI Orchestration Team
