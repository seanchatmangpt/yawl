# YAWL v6.0.0 Documentation Hub

Documentation is organized by the [Diataxis](https://diataxis.fr/) framework into four quadrants, each serving a different reader goal. Start at [diataxis/INDEX.md](diataxis/INDEX.md) for the full cross-quadrant map.

---

## Quick Navigation

| Goal | Start Here |
|------|------------|
| First time — set up dev environment | [tutorials/01-build-yawl.md](tutorials/01-build-yawl.md) |
| Run a workflow end-to-end | [tutorials/03-run-your-first-workflow.md](tutorials/03-run-your-first-workflow.md) |
| Connect an AI agent via MCP | [tutorials/08-mcp-agent-integration.md](tutorials/08-mcp-agent-integration.md) |
| Contribute code | [how-to/contributing.md](how-to/contributing.md) |
| Deploy to production | [how-to/deployment/production.md](how-to/deployment/production.md) |
| Scale to 1M cases | [diataxis/INDEX.md#1m-cases-support-v600](diataxis/INDEX.md#1m-cases-support-v600) |
| Migrate from v5 to v6 | [how-to/migration/v5-to-v6.md](how-to/migration/v5-to-v6.md) |
| Debug a problem | [how-to/troubleshooting.md](how-to/troubleshooting.md) |
| Look up configuration | [reference/configuration.md](reference/configuration.md) |
| Look up API | [reference/api-reference.md](reference/api-reference.md) |
| Understand the engine | [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md) |
| Browse all ADRs | [explanation/decisions/](explanation/decisions/) |
| Codebase topology | [v6/latest/INDEX.md](v6/latest/INDEX.md) |
| All docs by type | [diataxis/INDEX.md](diataxis/INDEX.md) |

---

## By Role

| Role | Key Documents |
|------|---------------|
| **New Developer** | [tutorials/01-build-yawl.md](tutorials/01-build-yawl.md) → [how-to/contributing.md](how-to/contributing.md) → [how-to/testing.md](how-to/testing.md) |
| **Project Lead** | [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md) → [reference/configuration-verification.md](reference/configuration-verification.md) → [v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md) |
| **QA / Test Engineer** | [how-to/testing.md](how-to/testing.md) → [how-to/troubleshooting.md](how-to/troubleshooting.md) → [reference/error-codes.md](reference/error-codes.md) |
| **DevOps / CI Engineer** | [how-to/cicd/setup.md](how-to/cicd/setup.md) → [how-to/deployment/production.md](how-to/deployment/production.md) → [v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md) |
| **Architect** | [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md) → [explanation/decisions/](explanation/decisions/) → [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md) |

---

## Documentation Quadrants

### Tutorials — Learning by Doing

Step-by-step lessons where completing the lesson is the goal.

| # | Tutorial |
|---|----------|
| 01 | [Build YAWL](tutorials/01-build-yawl.md) |
| 02 | [Understand the Build](tutorials/02-understand-the-build.md) |
| 03 | [Run Your First Workflow](tutorials/03-run-your-first-workflow.md) |
| 04 | [Write a YAWL Specification](tutorials/04-write-a-yawl-specification.md) |
| 05 | [Call the REST API](tutorials/05-call-yawl-rest-api.md) |
| 06 | [Custom Work Item Handler](tutorials/06-write-a-custom-work-item-handler.md) |
| 07 | [Docker Dev Environment](tutorials/07-docker-dev-environment.md) |
| 08 | [MCP Agent Integration](tutorials/08-mcp-agent-integration.md) |
| 11 | [Scale to 1M Cases](tutorials/11-scale-to-million-cases.md) |

### How-To Guides — Accomplishing a Task

Task-oriented guides for practitioners who know what they want to achieve.

**Configuration** — [configure-multi-tenancy.md](how-to/configure-multi-tenancy.md) · [configure-resource-service.md](how-to/configure-resource-service.md) · [configure-spiffe.md](how-to/configure-spiffe.md) · [enable-stateless-persistence.md](how-to/enable-stateless-persistence.md)

**1M Cases** — [configure-zgc-compact-headers.md](how-to/configure-zgc-compact-headers.md) · [implement-custom-case-registry.md](how-to/implement-custom-case-registry.md) · [subscribe-workflow-events.md](how-to/subscribe-workflow-events.md) · [migrate-uuid-case-ids.md](how-to/migrate-uuid-case-ids.md) · [operations/tune-hpa-for-cases.md](how-to/operations/tune-hpa-for-cases.md)

**Deployment** — [deployment/](how-to/deployment/) — Docker, Jetty, Tomcat, WildFly, production, Java 25 upgrade

**CI/CD** — [cicd/](how-to/cicd/) — build pipeline, testing integration, Maven

**Migration** — [migration/](how-to/migration/) — v5→v6, Jakarta EE, library migrations

**Integration** — [integration/](how-to/integration/) — MCP, A2A, marketplace, Docker validation

**Operations** — [operations/](how-to/operations/) — disaster recovery, scaling, upgrade

**Security** — [security/testing.md](how-to/security/testing.md)

### Reference — Accurate Technical Information

Information-oriented content for practitioners who need to look up a fact.

**Engine & Schema** — [reference/configuration.md](reference/configuration.md) · [reference/environment-variables.md](reference/environment-variables.md) · [reference/error-codes.md](reference/error-codes.md) · [reference/yawl-schema.md](reference/yawl-schema.md)

**Interfaces** — [reference/interface-b.md](reference/interface-b.md) · [reference/interface-e.md](reference/interface-e.md) · [reference/interface-x.md](reference/interface-x.md)

**API** — [reference/api-reference.md](reference/api-reference.md) · [reference/api/](reference/api/)

**Patterns** — [reference/patterns/](reference/patterns/) · [reference/workflow-patterns.md](reference/workflow-patterns.md)

### Explanation — Understanding Concepts

Understanding-oriented content that illuminates why, not just how.

**Core Concepts** — [explanation/petri-net-foundations.md](explanation/petri-net-foundations.md) · [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md) · [explanation/case-lifecycle.md](explanation/case-lifecycle.md) · [explanation/or-join-semantics.md](explanation/or-join-semantics.md)

**Decisions (ADRs)** — [explanation/decisions/](explanation/decisions/) — 30+ Architecture Decision Records

---

## Observatory (v6/latest/)

The Observatory auto-generates machine-readable facts about the codebase. Read facts instead of grepping source (100× cheaper).

| Fact File | Contents |
|-----------|----------|
| [v6/latest/INDEX.md](v6/latest/INDEX.md) | Full observatory manifest |
| [v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md) | How to run and interpret observatory output |

---

## Archive

Historical session reports and implementation summaries are in [archive/](archive/). These are kept for audit trails but are not maintained.

---

## Build Quick Reference

```bash
bash scripts/dx.sh compile    # Fast compile check
bash scripts/dx.sh all        # Full pre-commit gate (mandatory)
bash scripts/dx.sh -pl <mod>  # Single-module build
mvn clean verify -P analysis  # SpotBugs + PMD analysis
```

HYPER_STANDARDS: No TODO/FIXME/mock/stub in committed code. Real implementation or `throw new UnsupportedOperationException(...)`.
