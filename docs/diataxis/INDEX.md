# Diataxis Master Index — YAWL v6.0.0

Every documentation file belongs to exactly one of these four quadrants. Find what you need by selecting the quadrant that matches your current goal.

---

## Tutorials — Learning by Doing

> You are a **learner**. You want to be guided through a journey that takes you from start to accomplishment.

### Getting Started
| # | Tutorial | What you learn |
|---|----------|----------------|
| 00 | [Quick Start (Users)](../tutorials/quick-start-users.md) | Lightning-fast introduction for end users |
| 01 | [Build YAWL](../tutorials/01-build-yawl.md) | Clone, build, verify the full project compiles |
| 02 | [Understand the Build](../tutorials/02-understand-the-build.md) | Maven multi-module structure, shared-src strategy |
| 03 | [Run Your First Workflow](../tutorials/03-run-your-first-workflow.md) | Launch the engine, deploy a spec, execute a case |

### Core Functionality
| # | Tutorial | What you learn |
|---|----------|----------------|
| 04 | [Write a YAWL Specification](../tutorials/04-write-a-yawl-specification.md) | Design a process with tasks, conditions, flows |
| 05 | [Call the YAWL REST API](../tutorials/05-call-yawl-rest-api.md) | Interact with the engine via HTTP/JSON |
| 06 | [Write a Custom Work Item Handler](../tutorials/06-write-a-custom-work-item-handler.md) | Extend the engine with custom task logic |

### Deployment & Integration
| # | Tutorial | What you learn |
|---|----------|----------------|
| 07 | [Docker Dev Environment](../tutorials/07-docker-dev-environment.md) | Container-based local development setup |
| 08 | [MCP Agent Integration](../tutorials/08-mcp-agent-integration.md) | Connect an AI agent via the MCP server |
| 09 | [Marketplace Quick Start](../tutorials/09-marketplace-quick-start.md) | Deploy YAWL to a cloud marketplace |

### Deployment & Application Modules
| # | Tutorial | What you learn |
|---|----------|----------------|
| DM-01 | [YAWL Stateless Engine Getting Started](../tutorials/yawl-stateless-getting-started.md) | Run stateless (event-driven) engine; deploy workflow; execute cases |
| DM-02 | [YAWL Web Applications Getting Started](../tutorials/yawl-webapps-getting-started.md) | Deploy engine webapp; configure servlet container; call REST API |
| DM-03 | [YAWL Control Panel Getting Started](../tutorials/yawl-control-panel-getting-started.md) | Run Swing admin UI; manage specifications; monitor cases |
| DM-04 | [YAWL MCP/A2A Getting Started](../tutorials/yawl-mcp-a2a-getting-started.md) | Connect AI agents via MCP; enable A2A communication |

### Service Modules
| # | Tutorial | What you learn |
|---|----------|----------------|
| SM-01 | [Authentication Getting Started](../tutorials/yawl-authentication-getting-started.md) | Set up JWT authentication, client credentials, CSRF protection |
| SM-02 | [Scheduling Getting Started](../tutorials/yawl-scheduling-getting-started.md) | Schedule cases, create recurring schedules, calendar-aware execution |
| SM-03 | [Monitoring Getting Started](../tutorials/yawl-monitoring-getting-started.md) | Enable OpenTelemetry tracing, metrics collection, structured logging |
| SM-04 | [Worklet Service Getting Started](../tutorials/yawl-worklet-getting-started.md) | Create Ripple Down Rules, select worklets at runtime, handle exceptions |

### Advanced Topics
| # | Tutorial | What you learn |
|---|----------|----------------|
| 10 | [Getting Started (User Guide)](../tutorials/10-getting-started.md) | End-to-end user perspective walkthrough |
| 11 | [Schema Modelling with DataModellingBridge](../tutorials/11-data-modelling-bridge.md) | Parse ODCS YAML, import SQL, manage domains, create ADRs, export workspaces |
| 12 | [Scale to 1M Cases](../tutorials/11-scale-to-million-cases.md) | Deploy and verify a 1M-case YAWL cluster end-to-end |
| 13 | [Walk All 7 Quality Gates](../tutorials/11-quality-gate-sequence.md) | Run G_compile through G_release in the correct order |
| 14 | [Fix Your First HYPER_STANDARDS Violation](../tutorials/12-fix-hyper-standards-violation.md) | Read violation output, understand H-patterns, apply both fix options |
| 15 | [DMN Decision Service](../tutorials/14-dmn-decision-service.md) | Evaluate DMN decision tables with DataModel schema validation and COLLECT aggregation |

### Process Intelligence (PI)
| # | Tutorial | What you learn |
|---|----------|----------------|
| PI-01 | [First Case Prediction](../pi/tutorials/01-first-case-prediction.md) | Build predictive models for workflow outcomes |
| PI-02 | [Train AutoML Model](../pi/tutorials/02-train-automl-model.md) | Use TPOT2 for automated machine learning |
| PI-03 | [Realtime Adaptive Workflows](../pi/tutorials/03-realtime-adaptive.md) | Deploy live predictions to adapt workflow behavior |
| PI-04 | [Natural Language QA](../pi/tutorials/04-natural-language-qa.md) | Query workflow data using natural language |

### Polyglot Programming
| # | Tutorial | What you learn |
|---|----------|----------------|
| PL-01 | [GraalPy Getting Started](../polyglot/tutorials/01-graalpy-getting-started.md) | Execute Python code within workflows |
| PL-02 | [GraalJS Getting Started](../polyglot/tutorials/02-graaljs-getting-started.md) | Execute JavaScript code within workflows |
| PL-03 | [GraalWASM Getting Started](../polyglot/tutorials/03-graalwasm-getting-started.md) | Execute WebAssembly within workflows |
| PL-04 | [Rust for Process Mining (OCEL2)](../polyglot/tutorials/04-rust4pm-ocel2.md) | Build Rust-based process mining tools for OCEL2 |
| PL-05 | [Code Generation with ggen](../tutorials/polyglot-ggen-getting-started.md) | Generate YAWL specs deterministically via RDF+SPARQL |

### Schema Modelling & Code Generation
| # | Tutorial | What you learn |
|---|----------|----------------|
| SC-01 | [Data Modelling Getting Started](../tutorials/11-data-modelling-bridge.md) | Import schemas, validate domains, manage ADRs |
| SC-02 | [DMN Decision Evaluation](../tutorials/14-dmn-decision-service.md) | Evaluate DMN tables with schema validation |

---

## How-To Guides — Accomplishing a Specific Task

> You are a **practitioner**. You have a goal and need directions.

### Setup & Configuration
| Guide | Task |
|-------|------|
| [Developer Guide](../how-to/developer-guide.md) | Set up development environment |
| [Developer Build](../how-to/developer-build.md) | Fast developer build workflow |
| [Development Setup](../how-to/development.md) | Complete development environment |
| [Java 25 Setup](../how-to/java25-setup.md) | Install and configure Java 25 |
| [Contributing](../how-to/contributing.md) | Contribute to YAWL project |

### Configuration
| Guide | Task |
|-------|------|
| [Configure Multi-Tenancy](../how-to/configure-multi-tenancy.md) | Set up isolated tenant environments |
| [Configure Resource Service](../how-to/configure-resource-service.md) | Define participants, roles, org model |
| [Configure SPIFFE](../how-to/configure-spiffe.md) | Enable workload identity with SPIFFE/SVID |
| [Configure Autonomous Agents](../how-to/configure-autonomous-agents.md) | Set up autonomous agent framework |
| [Configure Agents](../how-to/configure-agents.md) | Configure agent behaviors and parameters |
| [Enable Stateless Persistence](../how-to/enable-stateless-persistence.md) | Switch to event-sourced stateless mode |
| [Setup Org Model](../how-to/setup-org-model.md) | Define organizational hierarchy |
| [Configure ZGC & Compact Headers](../how-to/configure-zgc-compact-headers.md) | Enable low-latency GC on engine pods |
| [SPIFFE Configuration](../how-to/spiffe.md) | Enable SPIFFE/SVID zero-trust identity |

### Service Modules (Authentication, Scheduling, Monitoring, Worklets)
| Guide | Task |
|-------|------|
| [Set Up JWT Authentication](../how-to/yawl-authentication-setup.md) | Configure JWT tokens, session storage, rate limiting, CSRF protection |
| [Configure Business Calendars](../how-to/yawl-scheduling-calendars.md) | Define working hours, holidays, multi-timezone schedules |
| [Set Up Distributed Tracing](../how-to/yawl-monitoring-tracing.md) | Configure OpenTelemetry, Jaeger, OTLP export, trace sampling |
| [Implement Worklet Service (Advanced)](../how-to/implement-worklet-service-advanced.md) | Production worklet setup, RDR storage, versioning, monitoring |

### Workflow Patterns & Adaptation
| Guide | Task |
|-------|------|
| [Implement Worklet Service](../how-to/implement-worklet-service.md) | Add runtime workflow adaptation via RDR |
| [Subscribe to Workflow Events](../how-to/subscribe-workflow-events.md) | Register WorkflowEventBus subscribers |
| [Validate Specifications](../how-to/validate-spec.md) | Validate YAWL specification files |

### Deployment
| Guide | Task |
|-------|------|
| [Deployment Overview](../how-to/deployment/overview.md) | Deployment strategy and options |
| [Deployment Guide](../how-to/deployment/guide.md) | Complete deployment procedure |
| [Deploy with Docker](../how-to/deployment/docker.md) | Container-based deployment |
| [Deploy with Docker (Full)](../how-to/deployment/docker-full.md) | Comprehensive Docker setup |
| [Deploy on Jetty](../how-to/deployment/jetty.md) | Embedded Jetty deployment |
| [Deploy on Tomcat](../how-to/deployment/tomcat.md) | Apache Tomcat deployment |
| [Deploy on WildFly](../how-to/deployment/wildfly.md) | JBoss WildFly deployment |
| [Production Deployment](../how-to/deployment/production.md) | Full production process |
| [Production Checklist](../how-to/deployment/production-checklist.md) | Pre-production validation |
| [Deployment Readiness](../how-to/deployment/readiness.md) | Readiness assessment |
| [Validation Environment](../how-to/deployment/validation-env.md) | Validation environment setup |
| [Cloud Runbooks](../how-to/deployment/cloud-runbooks.md) | Cloud deployment runbooks |
| [Playbooks](../how-to/deployment/playbooks.md) | Deployment playbooks |
| [Prerequisites](../how-to/deployment/prerequisites.md) | Pre-deployment requirements |
| [Java 25 Upgrade](../how-to/deployment/java25-upgrade.md) | Migrate from Java 21 to Java 25 |
| [Java 25 Checklist](../how-to/deployment/java25-checklist.md) | Java 25 migration checklist |
| [Java 25 Quick Reference](../how-to/deployment/java25-quickref.md) | Java 25 quick reference |
| [Java Migration](../how-to/deployment/java-migration.md) | Java version migration guide |
| [Spring Boot Migration](../how-to/deployment/spring-boot.md) | Migrate to Spring Boot 3.x |
| [Spring Boot Java 21](../how-to/deployment/spring-boot-java21.md) | Spring Boot on Java 21 |
| [Virtual Threads Deployment](../how-to/deployment/virtual-threads-deploy.md) | Deploy with virtual threads |
| [Virtual Threads](../how-to/deployment/virtual-threads.md) | Configure virtual threads |
| [Kubernetes Actuator](../how-to/deployment/kubernetes-actuator.md) | Kubernetes deployment automation |

### Application Module Deployment
| Guide | Task |
|-------|------|
| [Stateless Engine Deployment](../how-to/deployment/stateless-deployment.md) | Deploy YStatelessEngine to K8s, Docker Compose, cloud platforms |
| [Engine Webapp Configuration](../how-to/deployment/webapps-deployment.md) | Configure and deploy yawl-engine-webapp |
| [Control Panel Setup](../how-to/deployment/control-panel-deployment.md) | Deploy and configure YAWL Control Panel |
| [MCP/A2A Deployment](../how-to/deployment/mcp-a2a-deployment.md) | Deploy MCP/A2A application to production |
| [Stateless Event Store Configuration](../how-to/deployment/stateless-event-store.md) | Configure Kafka, RabbitMQ, or S3 for event sourcing |

### CI/CD
| Guide | Task |
|-------|------|
| [CI/CD Build Setup](../how-to/cicd/build.md) | Configure automated builds |
| [CI/CD Build Migration](../how-to/cicd/build-migration.md) | Migrate CI/CD build configuration |
| [CI/CD Complete Setup](../how-to/cicd/setup.md) | Full pipeline configuration |
| [CI/CD Implementation](../how-to/cicd/implementation.md) | Implement CI/CD pipeline |
| [CI/CD Maven Implementation](../how-to/cicd/maven-implementation.md) | Maven-based CI/CD setup |
| [Integration Testing in CI](../how-to/cicd/integration-testing.md) | Add integration test stage |
| [CI/CD Testing](../how-to/cicd/testing.md) | Configure testing in CI/CD |

### Migration & Upgrade
| Guide | Task |
|-------|------|
| [v5 to v6 Migration](../how-to/migration/v5-to-v6.md) | Upgrade from YAWL 5.x |
| [Migration Guide (v6)](../how-to/migration/v6-guide.md) | Comprehensive v6 migration |
| [v6 Upgrade](../how-to/migration/v6-upgrade.md) | v6 upgrade procedures |
| [v6 Migration](../how-to/migration/v6.md) | Full v6 migration guide |
| [Jakarta EE Migration](../how-to/migration/javax-to-jakarta.md) | javax → jakarta namespace |
| [ORM Migration](../how-to/orm-migration.md) | Database ORM migration |
| [Migration Checklist](../how-to/migration/checklist.md) | Migration verification checklist |
| [Migration Execution Plan](../how-to/migration/execution-plan.md) | Detailed migration execution |
| [Import Checklist](../how-to/migration/import-checklist.md) | Data import checklist |
| [Library Migration](../how-to/migration/library-migration.md) | Migrate library dependencies |
| [Migrate Autonomous Agents](../how-to/migrate-autonomous-agents.md) | Migrate autonomous agents |
| [Migrate to UUID Case IDs](../how-to/migrate-uuid-case-ids.md) | Remove integer counter, enable UUID generation |

### Integration
| Guide | Task |
|-------|------|
| [Integration Overview](../how-to/integration/overview.md) | Integration options overview |
| [Integration Guide](../how-to/integration-guide.md) | General integration guide |
| [Integration Consolidated](../how-to/integration-consolidated.md) | Consolidated integration guide |
| [MCP Server Setup](../how-to/integration/mcp-server.md) | Configure MCP endpoint |
| [A2A Server Setup](../how-to/integration/a2a-server.md) | Configure A2A protocol server |
| [A2A Authentication](../how-to/integration/a2a-auth.md) | Secure A2A with JWT/OAuth |
| [Docker Validation](../how-to/integration/docker-validation.md) | Validate Docker integration |
| [Marketplace Quick Start](../how-to/integration/marketplace-quickstart.md) | Marketplace integration quickstart |
| [OCPM Integration](../how-to/integration/ocpm-integration.md) | Object-centric process mining integration |
| [Marketplace Integration](../how-to/marketplace-integration.md) | Cloud marketplace integration |
| [Marketplace Overview](../how-to/marketplace-overview.md) | Marketplace options overview |
| [Marketplace Usage](../how-to/marketplace-usage.md) | Using marketplace deployments |
| [Marketplace AWS Deploy](../how-to/marketplace/aws-deploy.md) | Deploy to AWS marketplace |
| [Marketplace Azure Deploy](../how-to/marketplace/azure-deploy.md) | Deploy to Azure marketplace |
| [Marketplace GCP Deploy](../how-to/marketplace/gcp-deploy.md) | Deploy to GCP marketplace |
| [Marketplace Agent Integration](../how-to/marketplace/agent-integration.md) | Integrate agents in marketplace |
| [Deploy Autonomous Agents (Docker)](../how-to/deploy-autonomous-agents-docker.md) | Deploy agents in Docker |

### Operations & Management
| Guide | Task |
|-------|------|
| [CLI Operations](../how-to/cli-operations.md) | GODSPEED CLI operations |
| [Operations Guide](../how-to/operations/guide.md) | Operational procedures |
| [Operations Upgrade](../how-to/operations/upgrade.md) | Upgrade operations |
| [Disaster Recovery](../how-to/operations/disaster-recovery.md) | Recover from system failure |
| [Scaling](../how-to/operations/scaling.md) | Scale under load |
| [Tune HPA for Cases](../how-to/operations/tune-hpa-for-cases.md) | Right-size Kubernetes autoscaling |
| [Resilience Overview](../how-to/resilience/overview.md) | Resilience patterns overview |
| [Resilience Quick Start](../how-to/resilience/quick-start.md) | Quick start for resilience |
| [Resilience Operations](../how-to/resilience/operations.md) | Resilience operational guidance |
| [Release Checklist](../how-to/release-checklist.md) | Pre-release validation steps |
| [Rollback Procedures](../how-to/rollback.md) | Safely roll back a deployment |
| [Recovery Procedures](../how-to/recovery-procedures.md) | System recovery procedures |
| [Troubleshooting](../how-to/troubleshooting.md) | General troubleshooting guide |
| [Troubleshoot Agents](../how-to/troubleshoot-agents.md) | Debug agent issues |

### Security
| Guide | Task |
|-------|------|
| [Security Testing](../how-to/security/testing.md) | Run security test suite |

### Build & Test
| Guide | Task |
|-------|------|
| [Add a Maven Module](../how-to/build/add-maven-module.md) | Register a new module in the correct reactor position |
| [Target a Module Build](../how-to/build/target-module-build.md) | Use dx.sh -pl to build one module and its dependencies |
| [Run Release Validation](../how-to/build/run-release-validation.md) | Run all six poka-yoke checks before a release tag |
| [Measure Test Coverage](../how-to/build/measure-test-coverage.md) | Generate JaCoCo aggregate report and verify ≥55% threshold |
| [Fix H-Guard Violations at Scale](../how-to/build/fix-h-guard-violations.md) | Batch remediation of HYPER_STANDARDS violations by severity |
| [Testing Guide](../how-to/testing.md) | Test strategy and execution |
| [Performance Testing](../how-to/performance-testing.md) | Performance test setup and execution |
| [Quick Test](../how-to/quick-test.md) | Fast local testing |

### 1M Cases Support
| Guide | Task |
|-------|------|
| [Implement Custom Case Registry](../how-to/implement-custom-case-registry.md) | Plug in Redis-backed GlobalCaseRegistry |

### Schema Modelling & DMN
| Guide | Task |
|-------|------|
| [Import Schema Formats](../how-to/import-schema-formats.md) | Import SQL (5 dialects), Avro, JSON Schema, Protobuf, CADS, ODPS, BPMN, DMN, OpenAPI |
| [Manage Decision Records & Knowledge Base](../how-to/manage-knowledge-and-decisions.md) | Create/export MADR decision records, KB articles, and Excalidraw sketches with indexes |
| [Validate Data Schemas](../how-to/validate-data-schemas.md) | Validate ODPS, table/column names, data types, circular dependencies, naming conflicts |
| [Evaluate DMN Decisions](../how-to/evaluate-dmn-decisions.md) | Parse DMN XML, build DataModel schema, evaluate with COLLECT aggregation |
| [Data Modelling Schema Validation](../how-to/data-modelling-schema-validation.md) | Import SQL, create ODCS domains, manage ADRs, validate against schema in workflows |
| [Write Tera Templates for Code Generation](../how-to/ggen-tera-templates.md) | Create YAWL specs, service stubs, and config files from RDF via Tera templates |

### Export & Pipeline
| Guide | Task |
|-------|------|
| [Export Pipeline](../how-to/export-pipeline.md) | Export workflow data and results |

### Process Intelligence
| Guide | Task |
|-------|------|
| [PI Configuration](../pi/how-to/configure-pi-facade.md) | Configure process intelligence facade |
| [Ingest Event Log](../pi/how-to/ingest-event-log.md) | Load event logs for analysis |
| [Add Adaptation Rule](../pi/how-to/add-adaptation-rule.md) | Add runtime adaptation rules |
| [Add Constraint Rule](../pi/how-to/add-constraint-rule.md) | Add workflow constraints |
| [Expose MCP Tools](../pi/how-to/expose-mcp-tools.md) | Expose PI tools via MCP |
| [Optimize Resources](../pi/how-to/optimize-resources.md) | Optimize resource allocation |
| [Register ONNX Model](../pi/how-to/register-onnx-model.md) | Register ONNX ML models |
| [Setup TPOT2 Python](../pi/how-to/setup-tpot2-python.md) | Configure TPOT2 for AutoML |

### Polyglot Programming
| Guide | Task |
|-------|------|
| [Polyglot Configuration](../polyglot/how-to/configure-sandbox.md) | Configure polyglot sandbox |
| [Execute JS in Workflow](../polyglot/how-to/execute-js-in-workflow-task.md) | Run JavaScript in tasks |
| [Execute Python Script](../polyglot/how-to/execute-python-script.md) | Run Python in tasks |
| [Load WASM from Classpath](../polyglot/how-to/load-wasm-from-classpath.md) | Load WebAssembly files |
| [Pass Java Objects to Script](../polyglot/how-to/pass-java-objects-to-script.md) | Interop Java and scripts |
| [Share Context Pool](../polyglot/how-to/share-context-pool.md) | Pool polyglot contexts |

### FAQ & GODSPEED
| Guide | Task |
|-------|------|
| [FAQ](../how-to/faq.md) | Frequently asked questions |
| [GODSPEED CLI](../how-to/godspeed-cli.md) | GODSPEED command line interface |
| [Validation Orchestration](../how-to/validation-orchestration.md) | Orchestrate multi-phase validation |

---

## Reference — Accurate Technical Information

> You are a **practitioner**. You need to look up a specific fact quickly.

### Engine & Schema
| Reference | Contents |
|-----------|----------|
| [Configuration](../reference/configuration.md) | All configuration properties |
| [Configuration Verification](../reference/configuration-verification.md) | Validate configuration settings |
| [Environment Variables](../reference/environment-variables.md) | ENV var reference |
| [Error Codes](../reference/error-codes.md) | All error codes with descriptions |
| [YAWL Schema](../reference/yawl-schema.md) | XSD schema reference |
| [Implementation Status](../reference/implementation-status.md) | Feature completion matrix |
| [Stateless Engine API](../reference/stateless-engine-reference.md) | YStatelessEngine, YNetRunner, YWorkItem API reference |
| [Stateless Engine Configuration](../reference/stateless-engine-config.md) | YStatelessEngine configuration properties |
| [Engine Webapp Configuration](../reference/webapps-configuration.md) | yawl-engine-webapp deployment properties |
| [Control Panel Configuration](../reference/control-panel-configuration.md) | Control Panel settings and preferences |
| [MCP/A2A Configuration](../reference/mcp-a2a-configuration.md) | MCP and A2A server configuration |

### Interfaces
| Reference | Contents |
|-----------|----------|
| [Interface B](../reference/interface-b.md) | Engine ↔ client protocol |
| [Interface E](../reference/interface-e.md) | Event subscription protocol |
| [Interface X](../reference/interface-x.md) | Extended engine interface |

### API Documentation
| Reference | Contents |
|-----------|----------|
| [REST API Reference](../reference/api-reference.md) | Full REST API surface |
| [REST API Overview](../reference/api/overview.md) | API overview and structure |
| [API Changelog](../reference/api/api-changelog.md) | API changes and deprecations |
| [Migration (5.x to 6)](../reference/api/migration-5x-to-6.md) | API migration guide |
| [REST API JAX-RS Configuration](../reference/rest-api-jax-rs.md) | JAX-RS configuration details |
| [REST API Configuration](../reference/rest-api-config.md) | REST endpoint configuration |
| [Autonomous Agents API](../reference/autonomous-agents-api.md) | Agent framework API |
| [A2A Server Reference](../reference/a2a-server.md) | Agent-to-Agent protocol reference |
| [MCP Tools](../reference/mcp-tools.md) | MCP tool definitions |
| [MCP Server](../reference/mcp-server.md) | MCP server configuration |
| [MCP Process Mining Tools](../reference/mcp-process-mining-tools.md) | Process mining MCP tools |

### Observability & Patterns
| Reference | Contents |
|-----------|----------|
| [Observatory Facts](../reference/observatory-facts.md) | Module topology, dependencies, test counts |
| [Workflow Patterns](../reference/workflow-patterns.md) | WfMC/van der Aalst pattern catalog |
| [Patterns Catalog](../reference/patterns/) | Reusable workflow pattern definitions |
| [Performance Baselines](../reference/performance-baselines.md) | Benchmark results by version |
| [Performance Targets](../reference/performance-targets.md) | Target performance metrics |
| [Capacity Planning](../reference/capacity-planning.md) | Resource sizing guidelines |
| [Metrics](../reference/metrics.md) | Observability metrics |

### 1M Cases Support
| Reference | Contents |
|-----------|----------|
| [SPI Interfaces](../reference/spi-million-cases.md) | WorkflowEventBus, GlobalCaseRegistry, RunnerEvictionStore contracts |
| [JEP Index](../reference/java25-jep-index.md) | All Java 25 JEPs used (ScopedValue, FFM, ZGC, JEP 491) |
| [Capacity Planning (1M)](../reference/capacity-planning-1m.md) | Node sizing, heap/off-heap math, throughput model |

### Schema Modelling & DMN
| Reference | Contents |
|-----------|----------|
| [DataModellingBridge API](../reference/data-modelling-api.md) | DataModellingBridge: ODCS, SQL, OpenAPI, BPMN, DMN import-export; domain management; ADRs |
| [Data Modelling Bridge (Legacy)](../reference/data-modelling-bridge.md) | All 50+ methods: schema import, export, conversion, workspace, domain, decisions, KB, sketches, validation |
| [DMN Decision Service API](../reference/dmn-decision-service.md) | DmnDecisionService, DataModel, DmnTable, DmnColumn, DmnRelationship, DmnCollectAggregation, EndpointCardinality |
| [ggen API Reference](../reference/ggen-api.md) | RdfKnowledgeBase, SparqlQueryExecutor, TeraTemplateRenderer, CodeGenerator, PnmlSynthesizer, validation |

### Build & Infrastructure
| Reference | Contents |
|-----------|----------|
| [Maven Quick Reference](../reference/maven-quick-reference.md) | Common Maven commands |
| [Maven Commands](../reference/maven-commands.md) | Detailed Maven command reference |
| [Maven Module Structure](../reference/maven-module-structure.md) | Module graph |
| [Maven Modules](../reference/maven-modules.md) | Complete module reference |
| [Maven Module Dependencies](../reference/maven-module-dependencies.md) | Inter-module dependencies |
| [JUnit 5 Quick Reference](../reference/junit5.md) | Test annotation cheat sheet |
| [Virtual Threads](../reference/virtual-threads.md) | Java 21+ virtual thread APIs |
| [Test Strategy](../reference/test-strategy.md) | Testing methodology |
| [Quality Standards](../reference/quality-standards.md) | Code quality standards |
| [Quality Tests](../reference/quality/) | Architecture and shell tests |
| [Architecture Tests](../reference/quality/ARCHITECTURE-TESTS.md) | Architectural constraint tests |
| [Shell Tests](../reference/quality/SHELL-TESTS.md) | Shell script validation |
| [SLOs](../reference/slos/) | Service-level objectives |
| [SLO Reference](../reference/slos/YAWL_SLO.md) | YAWL service level objectives |
| [Build Sequences](../reference/build-sequences.md) | 19-module canonical order, parallel groups, critical path, -pl lists |
| [HYPER_STANDARDS Patterns](../reference/hyper-standards.md) | All 14 forbidden patterns: regex, severity, example, fix |
| [Quality Gates](../reference/quality-gates.md) | G_compile through G_release: predicates, thresholds, commands |
| [dx.sh CLI Reference](../reference/dx-sh.md) | All flags, env vars, exit codes, module ordering |
| [FMEA Risk Table](../reference/fmea-risk-table.md) | 16 failure modes with S/O/D/RPN and mitigations |

### Dependencies & Quick Reference
| Reference | Contents |
|-----------|----------|
| [Dependencies](../reference/dependencies.md) | Project dependency tree |
| [Quick Reference](../reference/quick-reference.md) | Cheat sheet of common tasks |
| [Quick Dependency Reference](../reference/quick-dependency.md) | Quick dependency lookup |

### Security & Compliance
| Reference | Contents |
|-----------|----------|
| [Security Overview](../reference/security-overview.md) | Security architecture overview |
| [Security Policy](../reference/security-policy.md) | Security and vulnerability policy |
| [Security Compliance](../reference/security-compliance.md) | Compliance requirements |
| [Privacy](../reference/privacy.md) | Privacy policy and data handling |
| [Support Policy](../reference/support-policy.md) | Support and maintenance policy |

### Administration
| Reference | Contents |
|-----------|----------|
| [Actuator](../reference/actuator.md) | Spring Boot Actuator endpoints |
| [Actuator Quick Reference](../reference/actuator-quickref.md) | Actuator quick reference |
| [SLA](../reference/sla.md) | Service level agreements |
| [Changes](../reference/changes.md) | Version changes and release notes |

### Data Formats & Monitoring
| Reference | Contents |
|-----------|----------|
| [DPA](../reference/dpa.md) | Data processing agreement |

---

## Explanation — Understanding Concepts & Decisions

> You are a **learner or practitioner**. You want to understand the why, not the how.

### Core Concepts
| Explanation | What it addresses |
|-------------|-------------------|
| [Petri Net Foundations](../explanation/petri-net-foundations.md) | Formal basis for YAWL workflow nets |
| [Case Lifecycle](../explanation/case-lifecycle.md) | How a workflow case is born, runs, and dies |
| [OR-Join Semantics](../explanation/or-join-semantics.md) | Non-local synchronisation semantics |
| [Multi-Instance Tasks](../explanation/multi-instance-tasks.md) | Parallel execution of task instances |
| [Execution Profiles](../explanation/execution-profiles.md) | Deferred, continuous, and persistent modes |

### Architecture & Design
| Explanation | What it addresses |
|-------------|-------------------|
| [v6 Architecture](../explanation/v6-architecture.md) | YAWL v6 overall architecture |
| [Dual-Engine Architecture](../explanation/dual-engine-architecture.md) | Stateful + stateless engine design |
| [Stateless vs Persistent Architecture](../explanation/stateless-vs-persistent-architecture.md) | Compare stateless and persistent execution models |
| [Interface Architecture](../explanation/interface-architecture.md) | A/B/E/X interface layering |
| [Shared-Src Build Strategy](../explanation/shared-src-build-strategy.md) | Why shared-src exists and how it works |
| [Maven-First Architecture](../explanation/maven-first-architecture.md) | Maven-centric build philosophy |
| [Deployment Architecture](../explanation/deployment-architecture.md) | Multi-tier deployment patterns |
| [CI/CD Architecture](../explanation/cicd-architecture.md) | Continuous integration and deployment |
| [Architecture Comparison](../explanation/architecture-comparison.md) | Compare YAWL with other engines |
| [Architecture Refactoring](../explanation/architecture-refactoring.md) | Refactoring patterns and practices |
| [Event-Sourced Architecture](../explanation/event-sourced-architecture.md) | Event sourcing patterns for stateless engine |
| [Stateless Engine Design](../explanation/stateless-engine-design.md) | Design patterns for event-driven execution |
| [MCP/A2A Architecture](../explanation/mcp-a2a-architecture.md) | AI agent integration architecture |
| [Control Panel Architecture](../explanation/control-panel-architecture.md) | Swing-based admin UI design |

### Workflow Execution & Adaptation
| Explanation | What it addresses |
|-------------|-------------------|
| [Worklet Service](../explanation/worklet-service.md) | RDR-based runtime adaptation |
| [Workflow Patterns](../reference/workflow-patterns.md) | WfMC/van der Aalst pattern catalog |

### Data & Schema
| Explanation | What it addresses |
|-------------|-------------------|
| [Data Modelling WASM Architecture](../explanation/data-modelling-wasm-architecture.md) | GraalJS+WASM polyglot, wasm-bindgen ES modules, JSON pass-through, Web API polyfills |
| [DMN GraalWASM Engine](../explanation/dmn-graalwasm-engine.md) | FEEL evaluation via WASM, DmnDecisionService layering, COLLECT aggregation delegation |
| [Export Architecture](../explanation/export-architecture.md) | Data export capabilities and design |

### Integration & Agents
| Explanation | What it addresses |
|-------------|-------------------|
| [Autonomous Agents](../explanation/autonomous-agents.md) | Autonomous agent framework |
| [Agent Coordination](../explanation/agent-coordination.md) | Multi-agent coordination patterns |
| [MCP LLM Design](../explanation/mcp-llm-design.md) | MCP and LLM integration design |
| [Spring AI MCP](../explanation/spring-ai-mcp.md) | Spring AI with MCP integration |
| [API Client Generation](../explanation/api-client-generation.md) | Auto-generate API clients |

### Process Intelligence & Marketplace
| Explanation | What it addresses |
|-------------|-------------------|
| [Process Intelligence](../explanation/process-intelligence.md) | Process intelligence capabilities |
| [Process Mining](../explanation/process-mining.md) | Process mining techniques and tools |
| [Object-Centric Process Mining](../explanation/object-centric-process-mining.md) | Object-centric event logs (OCEL) |
| [Marketplace Architecture](../explanation/marketplace-architecture.md) | Cloud marketplace deployment |
| [Marketplace Architecture Root](../explanation/marketplace-architecture-root.md) | Root marketplace design |
| [Cloud Marketplace GGen](../explanation/cloud-marketplace-ggen.md) | GGen in cloud marketplace |
| [Marketplace AWS](../explanation/marketplace/aws.md) | AWS marketplace specifics |
| [Marketplace Azure](../explanation/marketplace/azure.md) | Azure marketplace specifics |
| [Marketplace GCP](../explanation/marketplace/gcp.md) | GCP marketplace specifics |
| [Marketplace GCP Integration](../explanation/marketplace/gcp-integration.md) | GCP integration details |
| [Marketplace IBM](../explanation/marketplace/ibm.md) | IBM marketplace specifics |
| [Marketplace Oracle](../explanation/marketplace/oracle.md) | Oracle marketplace specifics |
| [Marketplace Teradata](../explanation/marketplace/teradata.md) | Teradata marketplace specifics |

### Polyglot & Emerging Technologies
| Explanation | What it addresses |
|-------------|-------------------|
| [Polyglot Execution Model](../explanation/polyglot-execution-unified.md) | Unified GraalVM polyglot execution across Python, JavaScript, WebAssembly |
| [Polyglot Architecture](../polyglot/explanation/polyglot-architecture.md) | Multi-language execution |
| [Type Marshalling](../polyglot/explanation/type-marshalling.md) | Cross-language type conversion |
| [WASM Binding Strategies](../polyglot/explanation/wasm-binding-strategies.md) | WebAssembly integration patterns |
| [When to Use Which](../polyglot/explanation/when-to-use-which.md) | Choose the right polyglot language |

### Code Generation & Schema
| Explanation | What it addresses |
|-------------|-------------------|
| [ggen Architecture](../explanation/ggen-architecture.md) | Deterministic code generation via RDF+SPARQL+Tera, traceability, process mining synthesis |
| [Data Modelling SDK as Facade](../explanation/data-modelling-sdk-facade.md) | Why data-modelling-sdk is Rust, not Java; single source of truth across languages |

### Build System & Quality
| Explanation | What it addresses |
|-------------|-------------------|
| [Why Reactor Ordering Matters](../explanation/reactor-ordering.md) | Topological ordering, FM7, parallelism, critical path |
| [Why H-Guards Exist](../explanation/h-guards-philosophy.md) | Zero-tolerance philosophy, Jidoka, the two legal outcomes |
| [The Chatman Equation: A = μ(O)](../explanation/chatman-equation.md) | How observation drives artifact quality; the 5-gate pipeline |
| [TDD Manifesto](../explanation/tdd-manifesto.md) | Test-driven development philosophy |

### Process Intelligence (PI) Architecture
| Explanation | What it addresses |
|-------------|-------------------|
| [PI Architecture](../pi/explanation/architecture.md) | Process intelligence system architecture |
| [PI Connections](../pi/explanation/6-pi-connections.md) | PI system connections and interfaces |
| [OCEL2 Standard](../pi/explanation/ocel2-standard.md) | Object-centric event log standard |
| [Co-located AutoML](../pi/explanation/co-located-automl.md) | Embedded machine learning |

### Advanced Topics
| Explanation | What it addresses |
|-------------|-------------------|
| [Multi-Cloud Strategy](../explanation/multi-cloud-analysis.md) | Multi-cloud deployment strategy |
| [Enterprise Cloud](../explanation/enterprise-cloud.md) | Enterprise cloud deployment |
| [Temporal Anomaly Sentinel](../explanation/temporal-anomaly-sentinel.md) | Time-series anomaly detection |
| [Observatory Improvements](../explanation/observatory-improvements.md) | Codebase metrics improvements |
| [Data Marketplace](../explanation/data-marketplace.md) | Data marketplace design |
| [Export Research](../explanation/export-research.md) | Export feature research |
| [Export Competitive](../explanation/export-competitive.md) | Competitive export analysis |
| [GGen Examples](../explanation/ggen-examples.md) | Code generation examples |
| [GGen Use Cases](../explanation/ggen-use-cases.md) | Code generation use cases |

### Performance & Scalability
| Explanation | What it addresses |
|-------------|-------------------|
| [Performance Overview](../explanation/performance-overview.md) | Performance architecture overview |
| [Roadmap](../explanation/roadmap.md) | Project roadmap |
| [v6 Roadmap](../explanation/v6-roadmap.md) | YAWL v6 roadmap |
| [v6 Upgrade Patterns](../explanation/v6-upgrade-patterns.md) | v6 upgrade guidance |

### 1M Cases Support
| Explanation | What it addresses |
|-------------|-------------------|
| [Why ScopedValue?](../explanation/why-scoped-values.md) | ScopedValue vs ThreadLocal on virtual threads |
| [Flow API Event Bus](../explanation/flow-api-event-bus.md) | Why Flow API by default, not Kafka |
| [Off-Heap Runner Store](../explanation/offheap-runner-store.md) | Why store snapshots off-heap via FFM API |
| [1M Case Architecture](../explanation/million-case-architecture.md) | How 5 Java 25 features compose to reach 1M |
| [MVP Architecture](../explanation/mvp-architecture.md) | Minimum viable product architecture |

### Decisions (ADRs)
| ADR | Decision |
|-----|----------|
| [ADR-001: Dual Engine Architecture](../explanation/decisions/ADR-001-dual-engine-architecture.md) | Stateful and stateless engine design |
| [ADR-002: YEngine Singleton vs Instance](../explanation/decisions/ADR-002-singleton-vs-instance-yengine.md) | Engine instantiation pattern |
| [ADR-003: Maven Primary, Ant Deprecated](../explanation/decisions/ADR-003-maven-primary-ant-deprecated.md) | Build tool selection |
| [ADR-004: Spring Boot 3.4 & Java 25](../explanation/decisions/ADR-004-spring-boot-34-java-25.md) | Spring and Java version strategy |
| [ADR-005: SPIFFE/SPIRE Zero-Trust](../explanation/decisions/ADR-005-spiffe-spire-zero-trust.md) | Workload identity security |
| [ADR-006: OpenTelemetry Observability](../explanation/decisions/ADR-006-opentelemetry-observability.md) | Observability platform |
| [ADR-007: Repository Caching](../explanation/decisions/ADR-007-repository-caching.md) | Build artifact caching |
| [ADR-008: Resilience4j Circuit Breaking](../explanation/decisions/ADR-008-resilience4j-circuit-breaking.md) | Resilience patterns |
| [ADR-009: Multi-Cloud Strategy](../explanation/decisions/ADR-009-multi-cloud-strategy.md) | Multi-cloud deployment |
| [ADR-010: Virtual Threads Scalability](../explanation/decisions/ADR-010-virtual-threads-scalability.md) | Virtual thread adoption |
| [ADR-011: Jakarta EE Migration](../explanation/decisions/ADR-011-jakarta-ee-migration.md) | javax to jakarta migration |
| [ADR-012: A2A Authentication Architecture](../explanation/decisions/ADR-012-a2a-authentication-architecture.md) | Agent-to-agent security |
| [ADR-012: OpenAPI-First Design](../explanation/decisions/ADR-012-openapi-first-design.md) | API specification first |
| [ADR-013: Schema Versioning Strategy](../explanation/decisions/ADR-013-schema-versioning-strategy.md) | Schema versioning approach |
| [ADR-014: Clustering & Horizontal Scaling](../explanation/decisions/ADR-014-clustering-and-horizontal-scaling.md) | Distributed deployment |
| [ADR-015: Persistence Layer v6](../explanation/decisions/ADR-015-persistence-layer-v6.md) | Data persistence design |
| [ADR-016: API Changelog & Deprecation](../explanation/decisions/ADR-016-api-changelog-deprecation-policy.md) | API evolution policy |
| [ADR-017: Authentication & Sessions](../explanation/decisions/ADR-017-authentication-and-sessions.md) | User authentication |
| [ADR-018: Javadoc to OpenAPI](../explanation/decisions/ADR-018-javadoc-to-openapi-generation.md) | API documentation generation |
| [ADR-019: Autonomous Agent Framework](../explanation/decisions/ADR-019-autonomous-agent-framework.md) | Autonomous agents |
| [ADR-020: Workflow Pattern Library](../explanation/decisions/ADR-020-workflow-pattern-library.md) | Pattern library design |
| [ADR-021: Stateless vs Stateful Selection](../explanation/decisions/ADR-021-stateless-vs-stateful-engine-selection.md) | Engine mode selection |
| [ADR-022: OpenAPI-First Design](../explanation/decisions/ADR-022-openapi-first-design.md) | API-first approach |
| [ADR-023: MCP/A2A CI/CD Deployment](../explanation/decisions/ADR-023-mcp-a2a-cicd-deployment.md) | Agent deployment |
| [ADR-024: Multi-Cloud Agent Deployment](../explanation/decisions/ADR-024-multi-cloud-agent-deployment.md) | Agent cloud strategy |
| [ADR-025: Agent Coordination Protocol](../explanation/decisions/ADR-025-agent-coordination-protocol.md) | Multi-agent communication |
| [ADR-025: Implementation](../explanation/decisions/ADR-025-IMPLEMENTATION.md) | Implementation details |
| [ADR-025: Summary](../explanation/decisions/ADR-025-summary.md) | Summary of findings |
| [ADR-026: GCP Marketplace Petri Net](../explanation/decisions/ADR-026-gcp-marketplace-petri-net.md) | GCP marketplace design |
| [ADR-026: Sealed Class Hierarchy](../explanation/decisions/ADR-026-sealed-class-hierarchy.md) | Sealed class patterns |
| [ADR-026: Sealed Classes Pattern](../explanation/decisions/ADR-026-sealed-classes-pattern.md) | Java sealed classes |
| [ADR-027: Records for Immutable Data](../explanation/decisions/ADR-027-records-for-immutable-data.md) | Java records usage |
| [ADR-027: Records Immutable Data](../explanation/decisions/ADR-027-records-immutable-data.md) | Immutable data design |
| [ADR-028: Virtual Threads Strategy](../explanation/decisions/ADR-028-virtual-threads-strategy.md) | Virtual thread adoption |
| [ADR-029: Structured Concurrency](../explanation/decisions/ADR-029-structured-concurrency-patterns.md) | Concurrency patterns |
| [ADR-030: Scoped Values Context](../explanation/decisions/ADR-030-scoped-values-context.md) | ScopedValue usage |

---

## Archive — Historical Session Artifacts

> You are **auditing the past**. These are implementation summaries and session deliverables, kept for reference but not maintained.

See [../archive/index.md](../archive/index.md) for the full list.

---

*This index is generated and maintained as part of the Diataxis restructure. To add a document, place it in the correct quadrant directory and add a row to the relevant table above.*
