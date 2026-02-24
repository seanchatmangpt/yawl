# YAWL v6.0.0 Documentation Index

**Version**: 6.0.0 | **Updated**: 2026-02-24 | **Framework**: [Diataxis](https://diataxis.fr/)

Documentation is organized into four Diataxis quadrants. For the full cross-quadrant map, see [diataxis/INDEX.md](diataxis/INDEX.md).

---

## Quick Navigation

| Audience | Start Here |
|----------|------------|
| **New Developer** | [tutorials/01-build-yawl.md](tutorials/01-build-yawl.md) |
| **Contributor** | [how-to/contributing.md](how-to/contributing.md) |
| **Operator** | [how-to/deployment/production.md](how-to/deployment/production.md) |
| **Architect** | [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md) |
| **Claude Code User** | [CLAUDE.md](../CLAUDE.md) |

---

## Documentation Quadrants

### Tutorials — [tutorials/index.md](tutorials/index.md)

| Tutorial | Description |
|----------|-------------|
| [01-build-yawl.md](tutorials/01-build-yawl.md) | Clone, build, and verify YAWL |
| [02-understand-the-build.md](tutorials/02-understand-the-build.md) | Maven multi-module structure |
| [03-run-your-first-workflow.md](tutorials/03-run-your-first-workflow.md) | Launch engine, deploy spec, run case |
| [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) | Design a YAWL workflow |
| [05-call-yawl-rest-api.md](tutorials/05-call-yawl-rest-api.md) | Drive workflows via REST |
| [06-write-a-custom-work-item-handler.md](tutorials/06-write-a-custom-work-item-handler.md) | Custom task execution |
| [07-docker-dev-environment.md](tutorials/07-docker-dev-environment.md) | Containerized development |
| [08-mcp-agent-integration.md](tutorials/08-mcp-agent-integration.md) | AI agent via MCP |
| [09-marketplace-quick-start.md](tutorials/09-marketplace-quick-start.md) | Cloud marketplace deployment |
| [10-getting-started.md](tutorials/10-getting-started.md) | End-user walkthrough |

### How-To Guides — [how-to/index.md](how-to/index.md)

| Category | Key Guides |
|----------|-----------|
| Configuration | [configure-multi-tenancy.md](how-to/configure-multi-tenancy.md) · [configure-spiffe.md](how-to/configure-spiffe.md) · [enable-stateless-persistence.md](how-to/enable-stateless-persistence.md) |
| Deployment | [deployment/](how-to/deployment/) — Docker, Jetty, Tomcat, WildFly, Java 25 |
| CI/CD | [cicd/](how-to/cicd/) — build pipeline, Maven, testing |
| Migration | [migration/](how-to/migration/) — v5→v6, Jakarta EE, library |
| Integration | [integration/](how-to/integration/) — MCP, A2A, marketplace |
| Operations | [operations/](how-to/operations/) — scaling, disaster recovery, upgrade |
| Development | [contributing.md](how-to/contributing.md) · [testing.md](how-to/testing.md) · [troubleshooting.md](how-to/troubleshooting.md) |

### Reference — [reference/index.md](reference/index.md)

| Category | Key Refs |
|----------|---------|
| Engine & Schema | [configuration.md](reference/configuration.md) · [error-codes.md](reference/error-codes.md) · [yawl-schema.md](reference/yawl-schema.md) |
| Interfaces | [interface-b.md](reference/interface-b.md) · [interface-e.md](reference/interface-e.md) · [interface-x.md](reference/interface-x.md) |
| API | [api-reference.md](reference/api-reference.md) · [api/](reference/api/) · [mcp-tools.md](reference/mcp-tools.md) |
| Patterns | [workflow-patterns.md](reference/workflow-patterns.md) · [patterns/](reference/patterns/) |
| Build | [maven-quick-reference.md](reference/maven-quick-reference.md) · [maven-module-structure.md](reference/maven-module-structure.md) |

### Explanation — [explanation/index.md](explanation/index.md)

| Category | Key Explanations |
|----------|----------------|
| Core Concepts | [petri-net-foundations.md](explanation/petri-net-foundations.md) · [case-lifecycle.md](explanation/case-lifecycle.md) · [or-join-semantics.md](explanation/or-join-semantics.md) |
| Architecture | [dual-engine-architecture.md](explanation/dual-engine-architecture.md) · [interface-architecture.md](explanation/interface-architecture.md) · [shared-src-build-strategy.md](explanation/shared-src-build-strategy.md) |
| Integration | [autonomous-agents.md](explanation/autonomous-agents.md) · [mcp-llm-design.md](explanation/mcp-llm-design.md) |
| ADRs | [decisions/](explanation/decisions/) — 30+ Architecture Decision Records |

---

## Codebase Facts (Observatory)

| Fact File | What it answers |
|-----------|----------------|
| [v6/latest/INDEX.md](v6/latest/INDEX.md) | Full observatory manifest |
| [v6/latest/facts/modules.json](v6/latest/facts/modules.json) | Module inventory |
| [v6/latest/facts/reactor.json](v6/latest/facts/reactor.json) | Maven reactor build order |
| [v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md) | How to run and interpret observatory |

---

## Architecture

| Resource | Contents |
|----------|---------|
| [explanation/decisions/](explanation/decisions/) | All Architecture Decision Records |
| [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md) | Full architecture analysis |
| [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md) | Quality definition of done |

---

## Claude Code Integration

| Resource | Contents |
|----------|---------|
| [../CLAUDE.md](../CLAUDE.md) | Claude Code project instructions |
| [../CHANGELOG.md](../CHANGELOG.md) | Version history |
| [../README.md](../README.md) | Project root README |
| [diataxis/INDEX.md](diataxis/INDEX.md) | Master 4-quadrant doc index |

---

## Operations Quick Reference

```bash
# Build
bash scripts/dx.sh compile          # Fast compile check
bash scripts/dx.sh all              # Full pre-commit gate (mandatory before commit)
bash scripts/dx.sh -pl <module>     # Single-module build

# Analysis
mvn clean verify -P analysis        # SpotBugs + PMD

# Observatory
bash scripts/observatory/observatory.sh   # Regenerate codebase facts
```

---

## Archive

Session deliverables and historical reports: [archive/index.md](archive/index.md)
