# Diataxis Master Index — YAWL v6.0.0

Every documentation file belongs to exactly one of these four quadrants. Find what you need by selecting the quadrant that matches your current goal.

---

## Tutorials — Learning by Doing

> You are a **learner**. You want to be guided through a journey that takes you from start to accomplishment.

| # | Tutorial | What you learn |
|---|----------|----------------|
| 01 | [Build YAWL](../tutorials/01-build-yawl.md) | Clone, build, verify the full project compiles |
| 02 | [Understand the Build](../tutorials/02-understand-the-build.md) | Maven multi-module structure, shared-src strategy |
| 03 | [Run Your First Workflow](../tutorials/03-run-your-first-workflow.md) | Launch the engine, deploy a spec, execute a case |
| 04 | [Write a YAWL Specification](../tutorials/04-write-a-yawl-specification.md) | Design a process with tasks, conditions, flows |
| 05 | [Call the YAWL REST API](../tutorials/05-call-yawl-rest-api.md) | Interact with the engine via HTTP/JSON |
| 06 | [Write a Custom Work Item Handler](../tutorials/06-write-a-custom-work-item-handler.md) | Extend the engine with custom task logic |
| 07 | [Docker Dev Environment](../tutorials/07-docker-dev-environment.md) | Container-based local development setup |
| 08 | [MCP Agent Integration](../tutorials/08-mcp-agent-integration.md) | Connect an AI agent via the MCP server |
| 09 | [Marketplace Quick Start](../tutorials/09-marketplace-quick-start.md) | Deploy YAWL to a cloud marketplace |
| 10 | [Getting Started (User Guide)](../tutorials/10-getting-started.md) | End-to-end user perspective walkthrough |

---

## How-To Guides — Accomplishing a Specific Task

> You are a **practitioner**. You have a goal and need directions.

### Configuration
| Guide | Task |
|-------|------|
| [Configure Multi-Tenancy](../how-to/configure-multi-tenancy.md) | Set up isolated tenant environments |
| [Configure Resource Service](../how-to/configure-resource-service.md) | Define participants, roles, org model |
| [Configure SPIFFE](../how-to/configure-spiffe.md) | Enable workload identity with SPIFFE/SVID |
| [Enable Stateless Persistence](../how-to/enable-stateless-persistence.md) | Switch to event-sourced stateless mode |
| [Setup Org Model](../how-to/setup-org-model.md) | Define organizational hierarchy |

### Workflow Patterns
| Guide | Task |
|-------|------|
| [Implement Worklet Service](../how-to/implement-worklet-service.md) | Add runtime workflow adaptation via RDR |

### Deployment
| Guide | Task |
|-------|------|
| [Deploy with Docker](../how-to/deployment/docker.md) | Container-based deployment |
| [Deploy on Jetty](../how-to/deployment/jetty.md) | Embedded Jetty deployment |
| [Deploy on Tomcat](../how-to/deployment/tomcat.md) | Apache Tomcat deployment |
| [Deploy on WildFly](../how-to/deployment/wildfly.md) | JBoss WildFly deployment |
| [Production Deployment](../how-to/deployment/production.md) | Full production process |
| [Java 25 Upgrade](../how-to/deployment/java25-upgrade.md) | Migrate from Java 21 to Java 25 |
| [Spring Boot Migration](../how-to/deployment/spring-boot.md) | Migrate to Spring Boot 3.x |

### CI/CD
| Guide | Task |
|-------|------|
| [CI/CD Build Setup](../how-to/cicd/build.md) | Configure automated builds |
| [CI/CD Complete Setup](../how-to/cicd/setup.md) | Full pipeline configuration |
| [Integration Testing in CI](../how-to/cicd/integration-testing.md) | Add integration test stage |

### Migration
| Guide | Task |
|-------|------|
| [v5 to v6 Migration](../how-to/migration/v5-to-v6.md) | Upgrade from YAWL 5.x |
| [Jakarta EE Migration](../how-to/migration/javax-to-jakarta.md) | javax → jakarta namespace |
| [ORM Migration](../how-to/orm-migration.md) | Database ORM migration |

### Integration
| Guide | Task |
|-------|------|
| [MCP Server Setup](../how-to/integration/mcp-server.md) | Configure MCP endpoint |
| [A2A Server Setup](../how-to/integration/a2a-server.md) | Configure A2A protocol server |
| [A2A Authentication](../how-to/integration/a2a-auth.md) | Secure A2A with JWT/OAuth |

### Operations
| Guide | Task |
|-------|------|
| [Disaster Recovery](../how-to/operations/disaster-recovery.md) | Recover from system failure |
| [Scaling](../how-to/operations/scaling.md) | Scale under load |
| [Release Checklist](../how-to/release-checklist.md) | Pre-release validation steps |
| [Rollback Procedures](../how-to/rollback.md) | Safely roll back a deployment |

### Security
| Guide | Task |
|-------|------|
| [Security Testing](../how-to/security/testing.md) | Run security test suite |

---

## Reference — Accurate Technical Information

> You are a **practitioner**. You need to look up a specific fact quickly.

### Engine & Schema
| Reference | Contents |
|-----------|----------|
| [Configuration](../reference/configuration.md) | All configuration properties |
| [Environment Variables](../reference/environment-variables.md) | ENV var reference |
| [Error Codes](../reference/error-codes.md) | All error codes with descriptions |
| [YAWL Schema](../reference/yawl-schema.md) | XSD schema reference |
| [Implementation Status](../reference/implementation-status.md) | Feature completion matrix |

### Interfaces
| Reference | Contents |
|-----------|----------|
| [Interface B](../reference/interface-b.md) | Engine ↔ client protocol |
| [Interface E](../reference/interface-e.md) | Event subscription protocol |
| [Interface X](../reference/interface-x.md) | Extended engine interface |

### Observability & Patterns
| Reference | Contents |
|-----------|----------|
| [Observatory Facts](../reference/observatory-facts.md) | Module topology, dependencies, test counts |
| [Workflow Patterns](../reference/workflow-patterns.md) | WfMC/van der Aalst pattern catalog |
| [Performance Baselines](../reference/performance-baselines.md) | Benchmark results by version |
| [Capacity Planning](../reference/capacity-planning.md) | Resource sizing guidelines |

### API
| Reference | Contents |
|-----------|----------|
| [REST API Reference](../reference/api-reference.md) | Full REST API surface |
| [REST API Configuration](../reference/rest-api-config.md) | JAX-RS configuration |
| [MCP Tools](../reference/mcp-tools.md) | MCP tool definitions |

### Build & Infrastructure
| Reference | Contents |
|-----------|----------|
| [Maven Quick Reference](../reference/maven-quick-reference.md) | Common Maven commands |
| [Maven Module Structure](../reference/maven-module-structure.md) | Module graph |
| [JUnit 5 Quick Reference](../reference/junit5.md) | Test annotation cheat sheet |
| [Virtual Threads](../reference/virtual-threads.md) | Java 21+ virtual thread APIs |
| [Quality Standards](../reference/quality/) | Architecture tests, shell tests |
| [SLOs](../reference/slos/) | Service-level objectives |
| [Patterns Catalog](../reference/patterns/) | Reusable workflow pattern definitions |

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

### Architecture
| Explanation | What it addresses |
|-------------|-------------------|
| [Dual-Engine Architecture](../explanation/dual-engine-architecture.md) | Stateful + stateless engine design |
| [Interface Architecture](../explanation/interface-architecture.md) | A/B/E/X interface layering |
| [Shared-Src Build Strategy](../explanation/shared-src-build-strategy.md) | Why shared-src exists and how it works |
| [Worklet Service](../explanation/worklet-service.md) | RDR-based runtime adaptation |

### Decisions (ADRs)
| ADR | Decision |
|-----|----------|
| [decisions/](../explanation/decisions/) | All Architecture Decision Records |

---

## Archive — Historical Session Artifacts

> You are **auditing the past**. These are implementation summaries and session deliverables, kept for reference but not maintained.

See [../archive/index.md](../archive/index.md) for the full list.

---

*This index is generated and maintained as part of the Diataxis restructure. To add a document, place it in the correct quadrant directory and add a row to the relevant table above.*
