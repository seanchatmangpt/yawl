# YAWL FAQ & Common Issues

Quick answers to the most common YAWL questions. If you don't see your issue here, check the [full documentation index](./diataxis/INDEX.md) or [troubleshooting guide](./how-to/troubleshooting.md).

---

## Getting Started

### Q: How do I get YAWL running on my machine?

**A:** Three options:

1. **5 minute quickstart** (users): [Quick Start (Users)](./tutorials/quick-start-users.md)
2. **30 minute developer setup**: [Build YAWL](./tutorials/01-build-yawl.md) + [Run Your First Workflow](./tutorials/03-run-your-first-workflow.md)
3. **Docker (production-ready)**: [Docker Dev Environment](./tutorials/07-docker-dev-environment.md)

### Q: Do I need to compile YAWL myself?

**A:** Only if you're developing or need bleeding-edge changes. For most use cases, use pre-built containers from the [Marketplace](./tutorials/09-marketplace-quick-start.md).

### Q: What Java version do I need?

**A:**
- **Java 25** (recommended, with virtual threads)
- Java 21+ (minimum)
- See [ADR-004: Spring Boot 3.4 & Java 25](./explanation/decisions/ADR-004-spring-boot-34-java-25.md)

### Q: Where do I get documentation for a specific module?

**A:** Use the [Module Dependency Map](./MODULE_DEPENDENCY_MAP.md) to find your module, then follow the links:
- **Tutorial** (learn by doing)
- **How-To Guide** (accomplish a task)
- **Reference** (look up facts)
- **Explanation** (understand design)

---

## Development

### Q: How do I call the YAWL engine from code?

**A:**
1. **REST API** (recommended for integrations): [Call the YAWL REST API](./tutorials/05-call-yawl-rest-api.md)
2. **Java API** (for embedded use): [YEngine API Reference](./reference/yawl-engine-api.md)
3. **See examples**: [REST API How-To](./how-to/calling-rest-api.md)

### Q: How do I extend YAWL with custom logic?

**A:**
- **Custom Work Item Handler**: [Write a Custom Work Item Handler](./tutorials/06-write-a-custom-work-item-handler.md)
- **External task handler**: [Implement Task Handler](./how-to/implement-task-handler.md)
- **Custom service**: [Extend with Custom Service](./how-to/extend-with-custom-service.md)

### Q: Can I use Python or JavaScript in workflows?

**A:** **Yes!** YAWL has native polyglot support via GraalVM:
- **Python**: [GraalPy Getting Started](./polyglot/tutorials/01-graalpy-getting-started.md)
- **JavaScript**: [GraalJS Getting Started](./polyglot/tutorials/02-graaljs-getting-started.md)
- **WebAssembly**: [GraalWASM Getting Started](./polyglot/tutorials/03-graalwasm-getting-started.md)

### Q: How do I generate YAWL specifications programmatically?

**A:** Use code generation:
- **ggen (RDF+SPARQL)**: [Code Generation with ggen](./tutorials/polyglot-ggen-getting-started.md)
- **Tera templates**: [Template Engine for Code Generation](./how-to/ggen-tera-templates.md)

### Q: How do I test my workflows?

**A:**
- **Unit tests**: [Testing Guide](./how-to/testing.md)
- **Integration tests**: [Integration Testing](./how-to/testing/integration.md)
- **Performance tests**: [Performance Benchmarking](./tutorials/yawl-benchmark-getting-started.md)

### Q: What's the difference between stateful and stateless execution?

**A:** See [Stateless vs Persistent Architecture](./explanation/stateless-vs-persistent-architecture.md) for detailed comparison. **TL;DR**: Stateless is for cloud/horizontal scaling, stateful for traditional deployment.

---

## Deployment & Operations

### Q: How do I deploy YAWL to production?

**A:**
1. **Choose deployment type**:
   - **Cloud / Kubernetes**: [Stateless Engine Getting Started](./tutorials/yawl-stateless-getting-started.md)
   - **Traditional server**: [Web Applications Getting Started](./tutorials/yawl-webapps-getting-started.md)
   - **Managed platform**: [Marketplace Quick Start](./tutorials/09-marketplace-quick-start.md)
2. **Then follow**: [Production Deployment Guide](./how-to/deployment/stateless-deployment.md)

### Q: How do I scale YAWL to handle millions of cases?

**A:** [Scale to 1M Cases](./tutorials/11-scale-to-million-cases.md) — Complete guide covering:
- Cluster configuration
- Database sharding
- Connection pooling
- Performance tuning
- Load testing

### Q: How do I monitor running workflows?

**A:** [Monitoring Getting Started](./tutorials/yawl-monitoring-getting-started.md) — Covers:
- OpenTelemetry tracing
- Prometheus metrics
- Structured logging
- Alert configuration

### Q: How do I set up authentication and authorization?

**A:** [Authentication Getting Started](./tutorials/yawl-authentication-getting-started.md) — Covers:
- JWT token auth
- CSRF protection
- Certificate-based auth
- Role-based access control

### Q: How do I ensure zero-trust security?

**A:** [ADR-005: SPIFFE/SPIRE Zero-Trust Architecture](./explanation/decisions/ADR-005-spiffe-spire-zero-trust.md) — Also see [Security Framework](./explanation/yawl-security-framework.md)

### Q: What's the best way to backup and recover?

**A:** [Disaster Recovery & Backup](./how-to/operations/disaster-recovery.md) — Covers:
- Database backup strategies
- Case state recovery
- Point-in-time restoration
- Testing recovery procedures

---

## Workflow Design

### Q: How do I write a YAWL workflow specification?

**A:** [Write a YAWL Specification](./tutorials/04-write-a-yawl-specification.md) — Step-by-step guide covering:
- Tasks, conditions, flows
- Decompositions (sub-workflows)
- Control flow patterns
- Common mistakes to avoid

### Q: What are the control flow patterns YAWL supports?

**A:** [Workflow Patterns Reference](./reference/workflow-patterns.md) — Complete reference for:
- Sequence, parallel, choice, synchronization
- Advanced patterns (deferred choice, multiple instances)
- Cancellation and exception handling

### Q: How do I handle exceptions and errors in workflows?

**A:** [Exception Handling in Workflows](./how-to/handle-exceptions.md) — Covers:
- Try-catch style exception handling
- Worklet service for dynamic exception handling
- Ripple Down Rules (RDR)
- Recovery patterns

### Q: How do I create recurring workflows or scheduled cases?

**A:** [Scheduling Getting Started](./tutorials/yawl-scheduling-getting-started.md) — Covers:
- One-time scheduling
- Recurring schedules (cron-style)
- Calendar-aware execution
- Time zone handling

### Q: How do I model data in my workflows?

**A:** [Data Modelling Getting Started](./tutorials/11-data-modelling-bridge.md) — Covers:
- Data schema validation
- Attribute-based rules (ADRs)
- Data types and constraints
- Schema versioning

---

## AI & Advanced Features

### Q: Can I add machine learning predictions to workflows?

**A:** **Yes!** See [Process Intelligence (PI) Tutorials](./pi/tutorials/):
1. [First Case Prediction](./pi/tutorials/01-first-case-prediction.md) — Start here
2. [Train AutoML Model](./pi/tutorials/02-train-automl-model.md) — TPOT2
3. [Realtime Adaptive Workflows](./pi/tutorials/03-realtime-adaptive.md) — Deploy predictions
4. [Natural Language QA](./pi/tutorials/04-natural-language-qa.md) — Query workflows in plain English

### Q: How do I connect an AI agent to YAWL?

**A:** [MCP Agent Integration](./tutorials/08-mcp-agent-integration.md) — Covers:
- Model Context Protocol (MCP)
- Tool definitions
- Agent authentication
- Real-time case monitoring

### Q: Can YAWL recommend the next action?

**A:** Yes, via [Prescriptive Engine](./explanation/yawl-pi-architecture.md#prescriptive-engine) — Part of Process Intelligence

### Q: How do I extract insights from completed cases?

**A:** [Process Mining & Analysis](./how-to/process-mining.md) — Covers:
- Case outcome analysis
- Bottleneck detection
- Process conformance
- Pattern discovery

---

## Troubleshooting

### Q: My workflow is stuck. How do I debug?

**A:** [Debugging Stuck Workflows](./how-to/troubleshooting.md#stuck-workflows) — Covers:
- Checking case status
- Viewing work item states
- Analyzing logs
- Common blocking conditions

### Q: Cases are executing slowly. How do I optimize?

**A:** [Performance Optimization](./how-to/performance-optimization.md) — Covers:
- Database query optimization
- Connection pooling
- Cache configuration
- Hardware scaling

### Q: I'm seeing memory leaks. What should I do?

**A:** [Memory Leak Investigation](./how-to/troubleshooting.md#memory-issues) — Covers:
- Heap dump analysis
- Memory profiling
- Common leak sources
- Prevention strategies

### Q: Authentication is failing. How do I fix it?

**A:** [Authentication Troubleshooting](./how-to/troubleshooting.md#authentication) — Covers:
- Token issues
- Certificate problems
- CORS configuration
- Debug logging

### Q: My build is failing. How do I diagnose?

**A:** [Build Troubleshooting](./how-to/troubleshooting.md#build-issues) — Also see [Understand the Build](./tutorials/02-understand-the-build.md)

### Q: I'm getting a HYPER_STANDARDS violation. How do I fix it?

**A:** [Fix Your First HYPER_STANDARDS Violation](./tutorials/12-fix-hyper-standards-violation.md) — Covers:
- Understanding the violation
- H-pattern patterns (TODO, mock, stub, silent fallback, etc.)
- Both fix options
- Prevention strategies

---

## Integration & Interoperability

### Q: How do I integrate YAWL with an external system?

**A:** [Integration Patterns](./how-to/integration-patterns.md) — Covers:
- HTTP callbacks
- Message queues (RabbitMQ, Kafka)
- Service adapters
- Event streaming

### Q: Can YAWL interoperate with other workflow engines?

**A:** Yes, via standard formats:
- **BPMN 2.0**: [BPMN Integration](./how-to/bpmn-integration.md)
- **OCEL2**: [Process Mining (OCEL2)](./polyglot/tutorials/04-rust4pm-ocel2.md)
- **OpenAPI**: [API-First Design](./explanation/decisions/ADR-012-openapi-first-design.md)

### Q: How do I subscribe to workflow events?

**A:** [Event Subscription](./how-to/subscribe-workflow-events.md) — Covers:
- Interface E (event interface)
- Event filtering
- Real-time notifications
- Programmatic access

---

## Configuration & Administration

### Q: How do I configure YAWL?

**A:** [Configuration Reference](./reference/configuration.md) — Complete reference for:
- Engine settings
- Database configuration
- Authentication
- Monitoring
- Deployment options

### Q: What environment variables does YAWL use?

**A:** [Environment Variables Reference](./reference/environment-variables.md)

### Q: How do I view and manage running cases?

**A:** Use the **Control Panel** (Swing UI): [Control Panel Getting Started](./tutorials/yawl-control-panel-getting-started.md)

### Q: How do I upgrade to a new YAWL version?

**A:** [Upgrade Guide](./how-to/operations/upgrade.md) — Covers:
- Pre-upgrade checklist
- Database migration
- Configuration updates
- Testing the upgrade

---

## Architecture & Design

### Q: What's the overall architecture of YAWL?

**A:** [Dual Engine Architecture](./explanation/dual-engine-architecture.md) — Also see:
- [Engine Architecture](./explanation/yawl-engine-architecture.md)
- [Stateless vs Persistent](./explanation/stateless-vs-persistent-architecture.md)

### Q: How does YAWL execute workflows?

**A:** [Engine Execution](./explanation/yawl-engine-architecture.md#execution-model)

### Q: What design decisions have been made?

**A:** [Architecture Decision Records](./explanation/decisions/) — 30+ ADRs covering:
- Technology choices (Java 25, Spring Boot 3.4, etc.)
- Security (SPIFFE/SPIRE, zero-trust)
- Scalability (virtual threads, clustering)
- Integration (MCP, A2A)

---

## Contributing & Support

### Q: How can I contribute to YAWL?

**A:** [Contributing Guide](./how-to/contributing.md) — Also:
- [YAWL GitHub Repository](https://github.com/seanchatmangpt/yawl)
- [Report Issues](https://github.com/seanchatmangpt/yawl/issues)
- [Community Discussion](https://github.com/seanchatmangpt/yawl/discussions)

### Q: Where can I get help?

**A:**
- **Documentation**: You're reading it! Try [Getting Started Paths](./GETTING_STARTED_PATHS.md)
- **GitHub Issues**: [Ask a question](https://github.com/seanchatmangpt/yawl/issues)
- **Community Forum**: [GitHub Discussions](https://github.com/seanchatmangpt/yawl/discussions)
- **Email**: [YAWL Team](mailto:yawl@yawl-engine.org)

### Q: Is YAWL actively maintained?

**A:** **Yes!** YAWL is production-ready and actively developed. Current version: **v6.0.0** (February 2026)

---

## Quick Reference

### Essential Commands

```bash
# Build YAWL
bash scripts/dx.sh compile    # Quick compile check
bash scripts/dx.sh all        # Full pre-commit validation

# Run tests
bash scripts/dx.sh test       # Run all tests
mvn clean test -pl <module>   # Test single module

# Performance analysis
mvn clean verify -P analysis  # SpotBugs + PMD checks

# Docker
docker build -t yawl:latest .
docker run -p 8080:8080 yawl:latest
```

### Key Files

| File | Purpose |
|------|---------|
| [GETTING_STARTED_PATHS.md](./GETTING_STARTED_PATHS.md) | Choose your path by role |
| [MODULE_DEPENDENCY_MAP.md](./MODULE_DEPENDENCY_MAP.md) | Understand module relationships |
| [diataxis/INDEX.md](./diataxis/INDEX.md) | Complete documentation index |
| [how-to/troubleshooting.md](./how-to/troubleshooting.md) | Detailed troubleshooting guide |
| [reference/configuration.md](./reference/configuration.md) | Configuration reference |
| [explanation/decisions/](./explanation/decisions/) | Architecture decision records |

---

## Still Not Found?

Try these approaches:

1. **Search**: Use Ctrl+F to search this page
2. **Browse by role**: [Getting Started Paths](./GETTING_STARTED_PATHS.md)
3. **Browse by module**: [Module Dependency Map](./MODULE_DEPENDENCY_MAP.md)
4. **Browse by quadrant**: [Diataxis Master Index](./diataxis/INDEX.md)
5. **Check tutorials**: [All Tutorials](./diataxis/INDEX.md#tutorials--learning-by-doing)
6. **Check how-to guides**: [All How-To Guides](./diataxis/INDEX.md#how-to-guides--accomplishing-a-specific-task)
7. **Ask community**: [GitHub Discussions](https://github.com/seanchatmangpt/yawl/discussions)

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 with comprehensive Diataxis documentation*
