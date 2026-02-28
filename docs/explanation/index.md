# Explanation Index

Understanding-oriented content that illuminates the context, concepts, and reasons behind YAWL's design. Explanation docs answer "why" — they are not tutorials or how-to guides.

---

## Core Workflow Concepts

| Explanation | What it addresses |
|-------------|-------------------|
| [petri-net-foundations.md](petri-net-foundations.md) | Formal basis for YAWL workflow nets — places, transitions, tokens |
| [case-lifecycle.md](case-lifecycle.md) | How a workflow case is created, executed, and completed |
| [or-join-semantics.md](or-join-semantics.md) | Non-local OR-join synchronisation semantics and why they are hard |
| [multi-instance-tasks.md](multi-instance-tasks.md) | Parallel execution of multiple task instances |
| [execution-profiles.md](execution-profiles.md) | Deferred, continuous, and persistent execution profiles |

## Engine Architecture

| Explanation | What it addresses |
|-------------|-------------------|
| [dual-engine-architecture.md](dual-engine-architecture.md) | Why YAWL has both a stateful and a stateless engine |
| [interface-architecture.md](interface-architecture.md) | A/B/E/X interface layering and responsibilities |
| [shared-src-build-strategy.md](shared-src-build-strategy.md) | Why shared-src exists and how the build uses it |
| [worklet-service.md](worklet-service.md) | RDR-based runtime workflow adaptation |
| [deployment-architecture.md](deployment-architecture.md) | Deployment topology and component placement |
| [architecture-comparison.md](architecture-comparison.md) | Architecture options and trade-offs |

## Integration & External Connectivity

| Explanation | What it addresses |
|-------------|-------------------|
| [autonomous-agents.md](autonomous-agents.md) | How autonomous agents integrate with YAWL |
| [agent-coordination.md](agent-coordination.md) | Multi-agent coordination patterns |
| [data-marketplace.md](data-marketplace.md) | Data marketplace architecture |
| [mcp-llm-design.md](mcp-llm-design.md) | MCP server design for LLM tool-calling |
| [mcp-research.md](mcp-research.md) | MCP protocol research and evaluation |
| [spring-ai-mcp.md](spring-ai-mcp.md) | Spring AI + MCP integration design |
| [spiffe.md](spiffe.md) | SPIFFE/SVID workload identity integration |

## Build & CI/CD

| Explanation | What it addresses |
|-------------|-------------------|
| [godspeed-methodology.md](godspeed-methodology.md) | The five-phase quality circuit (Ψ→Λ→H→Q→Ω) and loss localization |
| [chatman-equation.md](chatman-equation.md) | Agent quality model: A = μ(O) and why observation drives artifact quality |
| [h-guards-philosophy.md](h-guards-philosophy.md) | Why H-Guards block 7 deception patterns at write time |
| [cicd-architecture.md](cicd-architecture.md) | CI/CD pipeline architecture |
| [maven-first-architecture.md](maven-first-architecture.md) | Maven-first transition rationale |
| [performance-overview.md](performance-overview.md) | Performance characteristics and design |

## Marketplace & Strategy

| Explanation | What it addresses |
|-------------|-------------------|
| [marketplace/](marketplace/) | Cloud marketplace architectures (AWS, Azure, GCP, IBM, Oracle) |
| [marketplace-architecture.md](marketplace-architecture.md) | Marketplace integration architecture |
| [marketplace-architecture-root.md](marketplace-architecture-root.md) | High-level marketplace strategy |
| [cloud-marketplace-ggen.md](cloud-marketplace-ggen.md) | ggen + cloud marketplace architecture |
| [enterprise-cloud.md](enterprise-cloud.md) | Enterprise cloud architecture assessment |
| [multi-cloud-analysis.md](multi-cloud-analysis.md) | Multi-cloud strategy analysis |

## Code Generation & Tooling

| Explanation | What it addresses |
|-------------|-------------------|
| [ggen-examples.md](ggen-examples.md) | ggen code generation examples |
| [ggen-use-cases.md](ggen-use-cases.md) | ggen use cases and scenarios |
| [export-architecture.md](export-architecture.md) | Multi-format export architecture |
| [export-research.md](export-research.md) | Export format research |
| [export-competitive.md](export-competitive.md) | Competitive analysis for export formats |
| [api-client-generation.md](api-client-generation.md) | API client generation opportunities |
| [process-mining.md](process-mining.md) | Process mining enhancement plan |
| [tdd-manifesto.md](tdd-manifesto.md) | TDD principles and practices |
| [temporal-anomaly-sentinel.md](temporal-anomaly-sentinel.md) | Temporal anomaly detection |
| [observatory-improvements.md](observatory-improvements.md) | Observatory enhancement proposals |

## Version & Evolution

| Explanation | What it addresses |
|-------------|-------------------|
| [v6-architecture.md](v6-architecture.md) | v6 architecture overview |
| [v6-roadmap.md](v6-roadmap.md) | v6 release roadmap |
| [v6-upgrade-patterns.md](v6-upgrade-patterns.md) | v6 upgrade patterns |
| [roadmap.md](roadmap.md) | YAWL product roadmap |
| [mvp-architecture.md](mvp-architecture.md) | MVP architecture decisions |
| [architecture-refactoring.md](architecture-refactoring.md) | Architecture refactoring patterns |

## Architecture Decision Records (ADRs)

All ADRs are in [decisions/](decisions/). They document the why behind specific technical choices and are permanent records.

| Range | Topics |
|-------|--------|
| ADR-001 to ADR-010 | Engine architecture, build system, infrastructure |
| ADR-011 to ADR-020 | Jakarta EE, OpenAPI, persistence, authentication |
| ADR-021 to ADR-030 | Engine selection, deployment, Java 25 patterns |
