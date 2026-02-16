# YAWL v5.2 (Yet Another Workflow Language)

**Modern Java Enterprise BPM/Workflow Engine**

Status: **ACTIVE** | Java: **25** | Framework: **Jakarta EE 10** | Build: **Ant**

[YAWL](https://yawlfoundation.github.io) is a production-grade BPM/Workflow system, based on rigorous Petri net semantics. It handles complex data transformations, full integration with organizational resources, and external Web Services through a service-oriented architecture.

**New in v5.2:**
- Java 25 support with virtual threads and modern concurrency
- Jakarta EE 10 migration (complete)
- JUnit 5 testing framework
- Hibernate 6 persistence layer
- A2A (Agent-to-Agent) integration protocol
- MCP (Model Context Protocol) support for AI-enhanced workflows
- Enhanced REST API with JAX-RS
- Stateless engine for cloud-native deployment

### Major Features
YAWL offers these distinctive features:

* the most powerful process specification language for capturing control-flow dependencies and resourcing requirements.
* native data handling using XML Schema, XPath and XQuery.
* a formal foundation that makes its specifications unambiguous and allows automated verification.
* a service-oriented architecture that provides an environment that can easily be tuned to specific needs.
* YAWL has been developed independent from any commercial interests. It simply aims to be the most powerful language for process specification.
* For its expressiveness, YAWL offers relatively few constructs (compare this e.g. to BPMN!).
* YAWL offers unique support for exception handling, both those that were and those that were not anticipated at design time.
* YAWL offers unique support for dynamic workflow through the Worklets approach. Workflows can thus evolve over time to meet new and changing requirements.
* YAWL aims to be straightforward to deploy. It offers a number of automatic installers and an intuitive graphical design environment.
* YAWL's architecture is Service-oriented and hence one can replace existing components with one's own or extend the environment with newly developed components.
* The YAWL environments supports the automated generation of forms. This is particularly useful for rapid prototyping purposes.
* Tasks in YAWL can be mapped to human participants, Web Services, external applications or to Java classes.
* Through the C-YAWL approach a theory has been developed for the configuration of YAWL models. For more information on process configuration visit [www.processconfiguration.com]
* Simulation support is offered through a link with the [ProM](https://www.processmining.org) environment. Through this environment it is also possible to conduct post-execution analysis of YAWL processes (e.g. in order to identify bottlenecks).

### Other Features
* completely rewritten Process Editor (v5.0+)
* Auto Update + Install/Uninstall of selected components
* delayed case starting
* support for passing files as data
* support for non-human resources
* support for interprocess communication
* calendar service and scheduling capabilities
* task documentation facility
* revised logging format and exporting to OpenXES
* integration with external applications
* custom forms
* sophisticated verification support
* Web service communication
* Highly configurable and extensible

---

## Quick Start

### Requirements
- **Java 25+** (with virtual threads support)
- **Ant 1.10.x** (build system)
- **Jakarta EE 10 compatible application server** (Tomcat 10.1+, Jetty 11+, or Docker)

### Build

```bash
cd /home/user/yawl

# Compile source code
ant compile

# Run full build (compile + tests + validation)
ant buildAll

# Run unit tests
ant unitTest
```

### Documentation
Start with the [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) for comprehensive guides:
- New to YAWL? Read [README.md](#) and [CLAUDE.md](CLAUDE.md)
- Build issues? Check [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md)
- Integration setup? See [INTEGRATION_README.md](INTEGRATION_README.md)
- Testing? Review [docs/JUNIT5_QUICK_REFERENCE.md](docs/JUNIT5_QUICK_REFERENCE.md)

---

## System Requirements

### Java 25 Features Utilized
YAWL v5.2 leverages modern Java features:
- **Virtual Threads** - Enhanced concurrency for workflow execution
- **Pattern Matching** - Cleaner control-flow logic in engine code
- **Record Classes** - Immutable workflow data structures
- **Sealed Classes** - Type-safe workflow element hierarchy
- **Text Blocks** - Multi-line YAML/XML configurations

### Framework Stack
| Component | Version | Role |
|-----------|---------|------|
| Java | 25+ | Language runtime |
| Jakarta EE | 10+ | Enterprise APIs |
| Jakarta Servlet | 6.0+ | Web request handling |
| Jakarta Faces | 4.0+ | Web UI framework |
| Hibernate | 6.x | ORM/Persistence |
| JUnit | 5.x | Testing framework |
| Ant | 1.10+ | Build automation |

### Deployment Targets
- **Tomcat 10.1+** (Jakarta EE 10 certified)
- **Jetty 11+** (Jakarta EE 10 support)
- **Docker containers** (cloud-native deployment)
- **Kubernetes** (stateless engine)

---

## Java 25 & Jakarta EE 10 Migration

### Completed in v5.2
- Servlet API migration: `javax.servlet.*` → `jakarta.servlet.*`
- JSF migration: `javax.faces.*` → `jakarta.faces.*`
- Hibernate 5 → 6 persistence layer
- JUnit 4 → JUnit 5 test framework
- JSP → XHTML/Facelets templates
- All 210+ dependencies upgraded for Jakarta EE 10

### Migration Guides
- [JAKARTA_SERVLET_MIGRATION.md](JAKARTA_SERVLET_MIGRATION.md) - Servlet changes
- [JSP_XHTML_MIGRATION.md](JSP_XHTML_MIGRATION.md) - Template migration
- [JUNIT5_MIGRATION_SUMMARY.md](JUNIT5_MIGRATION_SUMMARY.md) - Test framework
- [MIGRATION_VERIFICATION.md](MIGRATION_VERIFICATION.md) - Verification checklist

---

## Integration Capabilities

### A2A (Agent-to-Agent) Protocol
Enable multi-agent orchestration with YAWL as the orchestrator:
- JSON-RPC 2.0 over HTTP
- gRPC protocol support
- Agent capability discovery
- Workflow task delegation to external agents

### MCP (Model Context Protocol)
Expose YAWL workflows to AI models:
- Claude, GPT-4, and compatible LLMs
- Workflows as callable tools
- Workflow context via MCP resources
- AI-enhanced decision making in processes

**See:** [INTEGRATION_README.md](INTEGRATION_README.md) for setup details

---

## API Endpoints

### REST API (JAX-RS)
YAWL exposes comprehensive REST endpoints for workflow management:
- Workflow specification management
- Case creation and monitoring
- Work item handling
- Resource and user management
- Integration endpoints for A2A/MCP

**See:** [docs/REST-API-JAX-RS.md](docs/REST-API-JAX-RS.md) for API reference

---

## Project Structure

```
yawl/
├── src/org/yawlfoundation/yawl/       # Source code (Java 25)
│   ├── engine/                         # YEngine & core workflow logic
│   ├── elements/                       # YSpecification & workflow elements
│   ├── stateless/                      # YStatelessEngine for cloud
│   ├── integration/                    # A2A and MCP integration
│   ├── util/                           # Utility classes
│   └── persistence/                    # Hibernate 6 ORM layer
├── test/org/yawlfoundation/yawl/      # JUnit 5 test suite
├── build/                              # Ant build configuration
│   ├── build.xml                       # Master build script
│   ├── build.properties                # Build properties
│   └── 3rdParty/lib/                   # 210+ JAR dependencies
├── docs/                               # API & deployment documentation
├── schema/                             # YAWL specification schemas
└── web/                                # Web UI (Jakarta Faces 4.0)
```

---

## Key Entry Points

### YAWL Engine
- `org.yawlfoundation.yawl.engine.YEngine` - Main stateful engine
- `org.yawlfoundation.yawl.stateless.YStatelessEngine` - Cloud-native variant
- `org.yawlfoundation.yawl.elements.YSpecification` - Workflow definitions

### Integration
- `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer` - A2A protocol server
- `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer` - MCP protocol server

### REST API
- JAX-RS endpoints in `org.yawlfoundation.yawl.rest.*` packages
- OpenAPI 3.0 schema available at `/api/openapi.yaml`

---

## Quality & Testing

### Test Coverage
- **JUnit 5** framework with comprehensive test suite
- **80%+ code coverage** target (tracked in CI)
- **Chicago TDD style** (integration tests, real database)
- **No mocks in production** code (Fortune 5 standards)

### Code Standards
- **HYPER_STANDARDS** enforcement (14 anti-patterns blocked)
- Java 25 best practices via `.claude/BEST-PRACTICES-2026.md`
- No TODO/FIXME comments, stubs, or placeholder code
- Real implementations or `UnsupportedOperationException`

**See:** [.claude/BEST-PRACTICES-2026.md](.claude/BEST-PRACTICES-2026.md) for standards

---

## Deployment

### Build Validation
Before deployment, run full validation:
```bash
ant compile && ant unitTest
```

### Quick Deployment (Tomcat)
```bash
# Build WAR files
ant buildAll

# Deploy to Tomcat 10.1+
cp build/artifacts/yawl*.war $CATALINA_HOME/webapps/
```

### Emergency Rollback
See [docs/ROLLBACK-PROCEDURES.md](docs/ROLLBACK-PROCEDURES.md) for:
- Quick rollback (5 minutes)
- Database recovery procedures
- Configuration restoration
- Data verification

---

## Documentation Hub

This project includes comprehensive documentation:

**Start Here:**
- [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) - Master documentation portal
- [CLAUDE.md](CLAUDE.md) - Development workflow & agent system

**By Role:**
- **Developers:** [.claude/BEST-PRACTICES-2026.md](.claude/BEST-PRACTICES-2026.md), [docs/JUNIT5_QUICK_REFERENCE.md](docs/JUNIT5_QUICK_REFERENCE.md)
- **DevOps:** [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md), [docs/ROLLBACK-PROCEDURES.md](docs/ROLLBACK-PROCEDURES.md)
- **Integrators:** [INTEGRATION_README.md](INTEGRATION_README.md), [docs/REST-API-JAX-RS.md](docs/REST-API-JAX-RS.md)

---

## License

YAWL is released under the LGPL license. See `license.txt` for details.

---

## Support & Resources

- **Official Site:** [https://yawlfoundation.github.io](https://yawlfoundation.github.io)
- **Documentation:** [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md)
- **Build Issues:** [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md)
- **Integration Help:** [INTEGRATION_README.md](INTEGRATION_README.md)

**Version:** 5.2
**Java:** 25+
**Framework:** Jakarta EE 10
**Status:** Production Ready
