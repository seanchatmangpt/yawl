# YAWL Module Dependency Map

This document maps dependencies and relationships between YAWL's 22 core modules, organized by functional layer.

---

## 📊 Visual Module Layer Stack

```
┌────────────────────────────────────────────────────────────────┐
│                    Application Layer                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ yawl-webapps │ yawl-control-panel │ yawl-mcp-a2a      │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
                           ▲
                           │ depends on
                           ▼
┌────────────────────────────────────────────────────────────────┐
│               Deployment & Stateless Layer                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │     yawl-stateless-engine                              │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
                           ▲
                           │ depends on
                           ▼
┌────────────────────────────────────────────────────────────────┐
│                  Advanced Services Layer                        │
│  ┌──────────────┬──────────────────┬──────────────────────┐    │
│  │ yawl-pi      │ yawl-resourcing  │ yawl-integration     │    │
│  └──────────────┴──────────────────┴──────────────────────┘    │
│  ┌──────────────┬──────────────────┬──────────────────────┐    │
│  │ yawl-polyglot│ yawl-ggen        │ yawl-tera-templates  │    │
│  └──────────────┴──────────────────┴──────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
                           ▲
                           │ depends on
                           ▼
┌────────────────────────────────────────────────────────────────┐
│                   Service Modules Layer                         │
│  ┌──────────────┬──────────────────┬──────────────────────┐    │
│  │ yawl-auth    │ yawl-scheduling  │ yawl-monitoring      │    │
│  │              │                  │                      │    │
│  │ yawl-worklet │ yawl-data-model  │                      │    │
│  └──────────────┴──────────────────┴──────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
                           ▲
                           │ depends on
                           ▼
┌────────────────────────────────────────────────────────────────┐
│              Core Foundation Modules Layer                      │
│  ┌──────────────┬──────────────────┬──────────────────────┐    │
│  │ yawl-engine  │ yawl-elements    │ yawl-utilities       │    │
│  │              │                  │                      │    │
│  │ yawl-security│ yawl-benchmark   │                      │    │
│  └──────────────┴──────────────────┴──────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Layer Descriptions

### Layer 1: Core Foundation (5 modules)

**Purpose**: Core YAWL engine and foundational utilities
**Stability**: Very High (stable APIs)
**Maturity**: Production

| Module | Purpose | Key Classes | Learn More |
|--------|---------|------------|-----------|
| **yawl-engine** | Petri net execution engine | `YEngine`, `YNetRunner` | [Tutorial](../tutorials/yawl-engine-getting-started.md) \| [API](../reference/yawl-engine-api.md) \| [Architecture](../explanation/yawl-engine-architecture.md) |
| **yawl-elements** | YAWL specification model | `YSpecification`, `YNet`, `YTask` | [Tutorial](../tutorials/yawl-elements-getting-started.md) \| [API](../reference/yawl-elements-api.md) \| [Domain Model](../explanation/yawl-elements-domain-model.md) |
| **yawl-utilities** | Shared utilities & helpers | Exceptions, schema utils, XML tools | [Tutorial](../tutorials/yawl-utilities-getting-started.md) \| [API](../reference/yawl-utilities-api.md) |
| **yawl-security** | X.509, crypto, digital signatures | Certificate validation, signing | [Tutorial](../tutorials/yawl-security-getting-started.md) \| [Framework](../explanation/yawl-security-framework.md) |
| **yawl-benchmark** | JMH performance testing | Benchmarks for engine & components | [Tutorial](../tutorials/yawl-benchmark-getting-started.md) \| [Configuration](../reference/yawl-benchmark-config.md) |

**Depends on**: Nothing (these are the foundation)
**Used by**: All other modules

---

### Layer 2: Service Modules (4-6 modules)

**Purpose**: Cross-cutting services for authentication, scheduling, monitoring
**Stability**: High (versioned APIs)
**Maturity**: Production

| Module | Purpose | Depends On | Learn More |
|--------|---------|-----------|-----------|
| **yawl-authentication** | JWT, CSRF, certificate auth | yawl-security, yawl-utilities | [Tutorial](../tutorials/yawl-authentication-getting-started.md) \| [Config](../reference/yawl-authentication-config.md) |
| **yawl-scheduling** | Case scheduling, calendar-aware | yawl-engine, yawl-elements | [Tutorial](../tutorials/yawl-scheduling-getting-started.md) \| [Config](../reference/yawl-scheduling-config.md) |
| **yawl-monitoring** | OpenTelemetry, metrics, tracing | yawl-engine, yawl-utilities | [Tutorial](../tutorials/yawl-monitoring-getting-started.md) \| [Architecture](../explanation/yawl-monitoring-architecture.md) |
| **yawl-worklet-service** | Ripple Down Rules, exception handling | yawl-engine, yawl-elements | [Tutorial](../tutorials/yawl-worklet-getting-started.md) \| [Architecture](../explanation/yawl-worklet-architecture.md) |
| **yawl-data-modelling** | Data schema validation, ADRs | yawl-elements, yawl-utilities | [Tutorial](../tutorials/11-data-modelling-bridge.md) \| [Architecture](../explanation/data-modelling-sdk-facade.md) |
| **yawl-resourcing** | Resource allocation, org models | yawl-engine, yawl-elements | [Guide](../how-to/resourcing-org-model.md) \| [Architecture](../explanation/resource-allocation.md) |

**Cross-dependencies**: Service modules may depend on each other (e.g., monitoring depends on scheduling)
**Used by**: Advanced services, deployment modules, and applications

---

### Layer 3: Advanced Services (3-4 modules)

**Purpose**: ML, polyglot execution, code generation, integration
**Stability**: Medium (evolving APIs)
**Maturity**: Production with active development

| Module | Purpose | Depends On | Learn More |
|--------|---------|-----------|-----------|
| **yawl-pi** | Process Intelligence (AutoML, predictions) | yawl-engine, yawl-elements, yawl-resourcing | [Tutorial](../pi/tutorials/01-first-case-prediction.md) \| [Architecture](../explanation/yawl-pi-architecture.md) |
| **yawl-polyglot** | Python, JavaScript, WASM execution | yawl-engine, GraalVM | [Tutorial](../polyglot/tutorials/01-graalpy-getting-started.md) \| [Architecture](../explanation/polyglot-execution-unified.md) |
| **yawl-ggen** | RDF+SPARQL code generation | yawl-elements, yawl-data-modelling | [Tutorial](../tutorials/polyglot-ggen-getting-started.md) \| [Architecture](../explanation/ggen-architecture.md) |
| **yawl-integration** | MCP, A2A, external system integration | yawl-engine, yawl-elements | [Architecture](../explanation/integration-architecture.md) |
| **yawl-tera-templates** | Template engine for code generation | yawl-elements, yawl-ggen | [How-To](../how-to/ggen-tera-templates.md) |

**Cross-dependencies**: Integration depends on both polyglot and PI; ggen uses data-modelling
**Used by**: Applications and deployment modules

---

### Layer 4: Deployment & Stateless (1 module)

**Purpose**: Event-driven, horizontally scalable engine variant
**Stability**: High (stable APIs)
**Maturity**: Production

| Module | Purpose | Depends On | Learn More |
|--------|---------|-----------|-----------|
| **yawl-stateless-engine** | Stateless execution with event store | yawl-engine, yawl-elements, yawl-monitoring | [Tutorial](../tutorials/yawl-stateless-getting-started.md) \| [Architecture](../explanation/stateless-vs-persistent-architecture.md) |

**Note**: Stateless re-exports core engine APIs with event-driven flavor
**Used by**: All deployment scenarios (production, cloud, scaled systems)

---

### Layer 5: Applications (3 modules)

**Purpose**: Deployed web applications and agent integrations
**Stability**: Medium (UI may change)
**Maturity**: Production

| Module | Purpose | Depends On | Learn More |
|--------|---------|-----------|-----------|
| **yawl-webapps** | REST API servlet container | yawl-engine, yawl-authentication, yawl-monitoring | [Tutorial](../tutorials/yawl-webapps-getting-started.md) \| [Config](../reference/webapps-configuration.md) |
| **yawl-control-panel** | Swing admin UI for management | yawl-engine, yawl-elements | [Tutorial](../tutorials/yawl-control-panel-getting-started.md) \| [Config](../reference/control-panel-configuration.md) |
| **yawl-mcp-a2a** | MCP server & A2A agent protocol | yawl-engine, yawl-integration | [Tutorial](../tutorials/yawl-mcp-a2a-getting-started.md) \| [Config](../reference/mcp-a2a-configuration.md) |

**Note**: Applications depend on deployment layer (stateless or persistent)
**Used by**: End users and system administrators

---

## 🔗 Dependency Details by Module

### yawl-engine
```
Depends on:    [none]
Used by:       yawl-elements (extension)
               yawl-scheduling, yawl-monitoring, yawl-worklet-service
               yawl-pi, yawl-stateless-engine
               yawl-webapps, yawl-control-panel
```

### yawl-elements
```
Depends on:    yawl-engine, yawl-utilities
Used by:       yawl-scheduling, yawl-worklet-service, yawl-data-modelling
               yawl-pi, yawl-ggen, yawl-tera-templates
               yawl-stateless-engine, yawl-webapps, yawl-control-panel
```

### yawl-utilities
```
Depends on:    [none]
Used by:       yawl-engine, yawl-elements, yawl-security
               All service modules, advanced services, applications
```

### yawl-security
```
Depends on:    yawl-utilities
Used by:       yawl-authentication, yawl-monitoring
               All TLS/crypto-requiring modules
```

### yawl-benchmark
```
Depends on:    yawl-engine, yawl-elements, yawl-utilities
Used by:       [optional] Performance analysis
```

### yawl-authentication
```
Depends on:    yawl-security, yawl-utilities
Used by:       yawl-webapps, yawl-control-panel, yawl-mcp-a2a
               All security-requiring modules
```

### yawl-scheduling
```
Depends on:    yawl-engine, yawl-elements, yawl-utilities
Used by:       yawl-stateless-engine
```

### yawl-monitoring
```
Depends on:    yawl-engine, yawl-security, yawl-utilities
Used by:       All deployment scenarios (stateless, webapps)
```

### yawl-worklet-service
```
Depends on:    yawl-engine, yawl-elements
Used by:       Engine extensions, workflow adaptation
```

### yawl-data-modelling
```
Depends on:    yawl-elements, yawl-utilities
Used by:       yawl-ggen, yawl-tera-templates
```

### yawl-resourcing
```
Depends on:    yawl-engine, yawl-elements, yawl-utilities
Used by:       yawl-pi (resource-aware predictions)
```

### yawl-pi
```
Depends on:    yawl-engine, yawl-elements, yawl-resourcing
Used by:       yawl-stateless-engine (for adaptive workflows)
               yawl-webapps, yawl-mcp-a2a (for predictions)
```

### yawl-polyglot
```
Depends on:    yawl-engine, yawl-utilities (GraalVM integration)
Used by:       Custom work items, service implementations
```

### yawl-ggen
```
Depends on:    yawl-elements, yawl-data-modelling, yawl-tera-templates
Used by:       Code generation workflows, schema management
```

### yawl-integration
```
Depends on:    yawl-engine, yawl-elements, yawl-polyglot
Used by:       yawl-mcp-a2a, autonomous agent systems
```

### yawl-tera-templates
```
Depends on:    yawl-elements, yawl-ggen
Used by:       Code generation, template-based workflow creation
```

### yawl-stateless-engine
```
Depends on:    yawl-engine, yawl-elements, yawl-scheduling, yawl-monitoring
Used by:       Cloud deployments, horizontally-scaled clusters
```

### yawl-webapps
```
Depends on:    yawl-engine, yawl-elements, yawl-authentication, yawl-monitoring
Used by:       End users (REST API & web interface)
```

### yawl-control-panel
```
Depends on:    yawl-engine, yawl-elements, yawl-authentication
Used by:       Administrators (Swing UI)
```

### yawl-mcp-a2a
```
Depends on:    yawl-engine, yawl-integration, yawl-authentication
Used by:       Autonomous agents, external systems
```

---

## 🚀 Common Dependency Patterns

### Pattern 1: I want to use the engine
```
yawl-utilities
    ↓
yawl-engine
    ↓
[Your custom code]
```

### Pattern 2: I want to build a workflow
```
yawl-utilities → yawl-engine → yawl-elements → yawl-data-modelling → yawl-ggen
```

### Pattern 3: I want to deploy for production
```
yawl-engine → yawl-elements → yawl-stateless-engine
    ↓
yawl-monitoring → yawl-scheduling → yawl-authentication
    ↓
[yawl-webapps OR yawl-mcp-a2a]
```

### Pattern 4: I want AI-powered workflows
```
yawl-engine → yawl-elements → yawl-pi
    ↓
[adaptive decisions OR resource allocation]
```

### Pattern 5: I want polyglot execution
```
yawl-engine → yawl-polyglot → [Python/JavaScript/WASM code]
```

---

## 📋 Import Order for Development

If you're building a feature that touches multiple modules, respect this order:

1. **Foundation**: yawl-utilities (if needed)
2. **Core**: yawl-engine, yawl-elements
3. **Services**: yawl-security, yawl-authentication, yawl-monitoring, etc.
4. **Advanced**: yawl-pi, yawl-polyglot, yawl-integration
5. **Deployment**: yawl-stateless-engine
6. **Applications**: yawl-webapps, yawl-control-panel, yawl-mcp-a2a

Never import from a higher layer into a lower layer.

---

## 📚 Learn More

- **Full Module Index**: [Diataxis Index](./diataxis/INDEX.md)
- **Module Getting Started Guides**:
  - [Core Foundation](./tutorials/yawl-engine-getting-started.md)
  - [Service Modules](./diataxis/INDEX.md?section=SM)
  - [Advanced Services](./diataxis/INDEX.md?section=PI)
  - [Deployment](./tutorials/yawl-stateless-getting-started.md)
- **Architecture Decision Records**: [ADRs](./explanation/decisions/)

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 — 22 modules organized in 5 functional layers*
