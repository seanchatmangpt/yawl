# YAWL v6 Documentation — Diataxis Master Index

The Diataxis framework (https://diataxis.fr/) organises documentation into four quadrants, each serving a distinct reader need. Mixing quadrants creates documents that satisfy no reader well. This index maps every significant doc in this project to its correct quadrant, identifies gaps, and provides the single entry point for navigating the 236 markdown files in `docs/`.

---

## What is Diataxis?

| Quadrant | Reader's question | Oriented toward | Analogy |
|---|---|---|---|
| **Tutorials** | "Teach me how to do this" | Learning | A cooking class |
| **How-to guides** | "Show me how to accomplish X" | Task completion | A recipe |
| **Reference** | "What is the exact value / API?" | Information lookup | A dictionary |
| **Explanation** | "Why does it work this way?" | Understanding | An essay |

The key distinction: tutorials teach skills through guided practice; how-to guides get a job done; reference is consulted, not read; explanation is read to build mental models.

---

## Quadrant 1 — Tutorials

**What belongs here:** Step-by-step lessons that guide a beginner through building or doing something for the first time, producing a concrete result. Every step is prescriptive ("type this, press enter"), no prior knowledge is assumed, and the reader finishes with a new capability. Background explanation is deferred; options and alternatives are avoided.

**What this is NOT:** How-to guides (those assume you know what you want), reference, or conceptual overviews.

### Existing docs that qualify

| File | Description | Audience / when to use |
|---|---|---|
| `docs/user-guide/getting-started.md` | Introduces YAWL concepts and initial setup steps — partial tutorial intent, mixes in conceptual explanation and reference tables; needs restructuring. | New users exploring YAWL for the first time |
| `docs/resilience/QUICK_START.md` | Scoped to resilience configuration; could become a tutorial with a clearer learning goal. | Operators enabling resilience features |
| `docs/QUICK-START.md` | Lists setup steps but skips explanations for why and mixes setup options; needs restructuring. | Anyone needing a quick first-run path |

### New tutorials added 2026-02-18

| File | Description | Audience / when to use |
|---|---|---|
| [`docs/tutorials/03-run-your-first-workflow.md`](../tutorials/03-run-your-first-workflow.md) | Load a specification, launch a case, claim and complete a work item, confirm the case finished — full end-to-end run via HTTP. | Developers who have built the engine and want to see it execute a real workflow |
| [`docs/tutorials/04-write-a-yawl-specification.md`](../tutorials/04-write-a-yawl-specification.md) | Author a minimal YAWL workflow specification from scratch in XML, validate it against the schema, and load it into the engine. | Developers writing workflow definitions for the first time |
| [`docs/tutorials/05-call-yawl-rest-api.md`](../tutorials/05-call-yawl-rest-api.md) | Write Java code using `java.net.http.HttpClient` to connect to the engine, load a spec, launch a case, poll for work items, check one out, and complete it. | Java developers integrating YAWL programmatically |
| [`docs/tutorials/06-write-a-custom-work-item-handler.md`](../tutorials/06-write-a-custom-work-item-handler.md) | Implement `YawlMcpTool`, register it with `YawlMcpToolRegistry`, and understand how an AI agent calls it via the Model Context Protocol. | Developers extending YAWL with custom automation or AI tools |
| [`docs/tutorials/07-docker-dev-environment.md`](../tutorials/07-docker-dev-environment.md) | Stand up a complete YAWL engine stack locally using Docker Compose, make a source change, rebuild the image, and run integration tests against the live stack. | Developers who want a containerised local development loop |
| [`docs/tutorials/08-mcp-agent-integration.md`](../tutorials/08-mcp-agent-integration.md) | Run the YAWL MCP server, connect a Claude agent via the Model Context Protocol, and launch a workflow case through a natural-language request. | AI engineers integrating language model agents with YAWL |

### Gap assessment

**Substantially improved.** Six concrete tutorials now cover the core beginner journey (run a workflow, write a spec, call the API, extend the engine, Docker dev setup, and MCP agent integration). Remaining gaps:

- Tutorial 01 (build from source) and Tutorial 02 (write a simple specification from zero) are referenced by the new tutorials but not yet in the index as complete entries — verify they exist at `docs/tutorials/01-build-yawl.md` and `docs/tutorials/02-write-a-simple-specification.md`.
- No tutorial for deploying to a production servlet container with a real database.

---

## Quadrant 2 — How-to Guides

**What belongs here:** Goal-oriented instructions for a reader who knows what they want to achieve and needs the steps to do it. These assume competence; they do not explain the underlying concepts. They are a series of numbered steps or clearly sequenced commands. The reader should be able to follow them without reading the whole document.

**What this is NOT:** Tutorials (those teach skills), reference (this has prose steps, not data tables), or explanation (no "why" content).

### Existing docs that qualify

| File | Task accomplished |
|---|---|
| `docs/DEPLOY-DOCKER.md` | Deploy YAWL using Docker and Docker Compose |
| `docs/DEPLOY-JETTY.md` | Deploy YAWL WAR to Jetty server |
| `docs/DEPLOY-TOMCAT.md` | Deploy YAWL WAR to Apache Tomcat |
| `docs/DEPLOY-WILDFLY.md` | Deploy YAWL WAR to WildFly / JBoss |
| `docs/DEPLOYMENT-CHECKLIST.md` | Pre-deployment verification checklist |
| `docs/PRODUCTION_DEPLOYMENT_CHECKLIST.md` | Production-specific deployment gate checks |
| `docs/BUILD.md` | Build YAWL from source using Maven |
| `docs/MIGRATION-v6.md` | Migrate a running YAWL v5.x instance to v6 |
| `docs/migration/MIGRATION_EXECUTION_PLAN.md` | Execute the v6 migration plan step by step |
| `docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md` | Migrate javax.* imports to jakarta.* |
| `docs/ROLLBACK-PROCEDURES.md` | Roll back a failed deployment |
| `docs/TROUBLESHOOTING.md` | Diagnose and fix common build and runtime errors |
| `docs/TROUBLESHOOTING-GUIDE.md` | Extended troubleshooting for operations teams |
| `docs/deployment/java25-upgrade-guide.md` | Upgrade the JVM to Java 25 |
| `docs/deployment/spring-boot-migration-guide.md` | Migrate to Spring Boot 3.4 |
| `docs/deployment/VIRTUAL_THREAD_DEPLOYMENT_GUIDE.md` | Enable virtual threads in production |
| `docs/deployment/runbooks/INCIDENT_RESPONSE_RUNBOOK.md` | Respond to production incidents |
| `docs/deployment/runbooks/SECURITY_RUNBOOK.md` | Execute security incident procedures |
| `docs/autonomous-agents/docker-deployment-guide.md` | Deploy autonomous agents via Docker |
| `docs/autonomous-agents/configuration-guide.md` | Configure the autonomous agent framework |
| `docs/marketplace/aws/deployment-guide.md` | Deploy YAWL on AWS Marketplace |
| `docs/marketplace/azure/deployment-guide.md` | Deploy YAWL on Azure Marketplace |
| `docs/marketplace/gcp/deployment-guide.md` | Deploy YAWL on Google Cloud Marketplace |
| `docs/CICD_IMPLEMENTATION_GUIDE.md` | Set up CI/CD pipelines for YAWL |
| `docs/CONTRIBUTING.md` | Contribute code changes to the project |
| `.claude/DEVELOPER-QUICKSTART.md` | Set up a local development environment |
| `docs/operations/upgrade-guide.md` | Upgrade a production YAWL installation |
| `docs/operations/disaster-recovery.md` | Execute disaster recovery procedures |
| `docs/LIBRARY-MIGRATION-GUIDE.md` | Migrate third-party library dependencies |
| `docs/BUILD_SYSTEM_MIGRATION_GUIDE.md` | Migrate from Ant to Maven build system |
| `docs/ORM_MIGRATION_GUIDE.md` | Migrate the ORM layer |

### New how-to guides added 2026-02-18

| File | Task accomplished | Audience / when to use |
|---|---|---|
| [`docs/how-to/configure-resource-service.md`](../how-to/configure-resource-service.md) | Understand what the v4 Resource Service was and how to achieve equivalent resource allocation in YAWL v6 (which has no standalone resource service). | Operators and developers migrating from YAWL v4 who expect a resource service |
| [`docs/how-to/configure-spiffe.md`](../how-to/configure-spiffe.md) | Configure SPIFFE/SPIRE zero-trust identity for YAWL engine and services on Kubernetes or bare-metal. | Security engineers enabling workload identity for YAWL deployments |
| [`docs/how-to/configure-multi-tenancy.md`](../how-to/configure-multi-tenancy.md) | Configure schema-per-tenant or instance-per-tenant isolation in YAWL v6 with PostgreSQL and Flyway. | Platform engineers deploying YAWL as a shared multi-tenant service |
| [`docs/how-to/enable-stateless-persistence.md`](../how-to/enable-stateless-persistence.md) | Enable persistence for the stateless engine by exporting case state to an external store and restoring it via `unloadCase()` / `restoreCase()`. | Developers using `YStatelessEngine` who need durable case state |
| [`docs/how-to/implement-worklet-service.md`](../how-to/implement-worklet-service.md) | Implement dynamic task routing in YAWL v6 (the v4 Worklet Service equivalent) using a custom MCP tool on `YawlMcpServer`. | Developers who need conditional workflow routing equivalent to the v4 Worklet Service |
| [`docs/how-to/setup-org-model.md`](../how-to/setup-org-model.md) | Understand what the v4 organisational model provided and how to manage participant access in YAWL v6 (which has no org model implementation). | Operators and developers migrating v4 resource assignments to v6 |

### Gap assessment

**Well covered.** Deployment and migration tasks are thoroughly documented. The new guides address the previously identified gaps around resource configuration, SPIFFE integration, multi-tenancy, and worklet routing. Remaining gap: no how-to for monitoring YAWL with Prometheus/Grafana in production.

---

## Quadrant 3 — Reference

**What belongs here:** Technical descriptions of the system's machinery: APIs, configuration keys, module structures, schemas, data formats, error codes. Written for accuracy and completeness, consulted rather than read linearly. Structure mirrors the system itself (alphabetical, by module, by endpoint). Contains facts, not instructions or prose argument.

**What this is NOT:** How-to guides (those have steps), tutorials (those teach), or explanation (those argue).

### Existing docs that qualify

| File | What it describes |
|---|---|
| `docs/API-REFERENCE.md` | Full REST API endpoint catalogue with request/response schemas |
| `docs/REST-API-JAX-RS.md` | JAX-RS implementation reference and annotation contracts |
| `docs/REST-API-Configuration.md` | REST API configuration properties |
| `docs/QUICK-REFERENCE.md` | One-page cheat sheet of key commands and properties |
| `docs/JUNIT5_QUICK_REFERENCE.md` | JUnit 5 annotation and assertion reference |
| `docs/VIRTUAL_THREADS_QUICK_REFERENCE.md` | Virtual threads API and configuration reference |
| `docs/deployment/java25-quick-reference.md` | Java 25 JVM flag and feature reference |
| `docs/MAVEN_MODULE_STRUCTURE.md` | Canonical list of all Maven modules and their purposes |
| `docs/MAVEN_MODULE_DEPENDENCIES.md` | Module dependency graph and resolution order |
| `docs/CHANGES.md` | Changelog — version-by-version change record |
| `docs/api/CHANGELOG.md` | API-specific changelog |
| `docs/DEPENDENCY_AUDIT_2026-02-16.md` | Point-in-time dependency versions and vulnerability status |
| `docs/MISSING_DEPENDENCIES_2026-02-16.md` | Known missing or unresolved dependency report |
| `docs/slos/YAWL_SLO.md` | Service Level Objectives — numeric targets and definitions |
| `docs/performance/PERFORMANCE_BASELINES.md` | Established performance baselines for regression detection |
| `docs/performance/CAPACITY_PLANNING.md` | Capacity sizing tables and formulas |
| `docs/BENCHMARK-METRICS.md` | Benchmark measurement results and conditions |
| `docs/VIRTUAL_THREADS_BENCHMARKS.md` | Virtual threads benchmark data |
| `docs/security/compliance-matrix.md` | Security control compliance status matrix |
| `docs/architecture/decisions/ADR-001-dual-engine-architecture.md` | Decision record: dual-engine design |
| `docs/architecture/decisions/ADR-002-singleton-vs-instance-yengine.md` | Decision record: YEngine singleton |
| `docs/architecture/decisions/ADR-003-maven-primary-ant-deprecated.md` | Decision record: Maven as primary build |
| `docs/architecture/decisions/ADR-004-spring-boot-34-java-25.md` | Decision record: Spring Boot 3.4 + Java 25 |
| `docs/architecture/decisions/ADR-005-spiffe-spire-zero-trust.md` | Decision record: SPIFFE/SPIRE zero-trust |
| `docs/architecture/decisions/ADR-012-a2a-authentication-architecture.md` | Decision record: A2A authentication |
| `docs/architecture/decisions/ADR-012-openapi-first-design.md` | Decision record: OpenAPI-first design |
| `docs/architecture/decisions/ADR-013-schema-versioning-strategy.md` | Decision record: schema versioning |
| `docs/architecture/decisions/ADR-014-clustering-and-horizontal-scaling.md` | Decision record: clustering strategy |
| `docs/architecture/decisions/ADR-015-persistence-layer-v6.md` | Decision record: v6 persistence layer |
| `docs/architecture/decisions/ADR-016-api-changelog-deprecation-policy.md` | Decision record: deprecation policy |
| `docs/architecture/decisions/ADR-017-authentication-and-sessions.md` | Decision record: authentication model |
| `docs/architecture/decisions/ADR-018-javadoc-to-openapi-generation.md` | Decision record: Javadoc to OpenAPI |
| `docs/architecture/decisions/ADR-019-autonomous-agent-framework.md` | Decision record: autonomous agent framework |
| `docs/architecture/decisions/ADR-020-workflow-pattern-library.md` | Decision record: workflow pattern library |
| `docs/architecture/decisions/ADR-021-stateless-vs-stateful-engine-selection.md` | Decision record: engine selection criteria |
| `docs/architecture/decisions/ADR-022-openapi-first-design.md` | Decision record: OpenAPI strategy |
| `docs/v6/latest/facts/modules.json` | Observatory: canonical module list (machine-readable) |
| `docs/v6/latest/facts/reactor.json` | Observatory: Maven reactor build order |
| `docs/v6/latest/facts/shared-src.json` | Observatory: source file ownership by module |
| `docs/v6/latest/facts/tests.json` | Observatory: test counts and scopes per module |
| `docs/v6/latest/facts/dual-family.json` | Observatory: stateful vs stateless engine mapping |
| `docs/v6/latest/facts/duplicates.json` | Observatory: duplicate FQCN report |
| `docs/v6/latest/facts/gates.json` | Observatory: quality gate definitions and states |
| `docs/v6/latest/facts/deps-conflicts.json` | Observatory: dependency version conflict report |
| `docs/v6/latest/facts/coverage.json` | Observatory: test coverage data |
| `docs/v6/latest/facts/maven-hazards.json` | Observatory: broken or hazardous Maven artifacts |

### New reference docs added 2026-02-18

| File | What it describes | Audience / when to use |
|---|---|---|
| [`docs/reference/yawl-schema.md`](../reference/yawl-schema.md) | Human-readable reference for every element and attribute in the YAWL XML specification format (`YAWL_Schema4.0.xsd`). | Agents and developers authoring or inspecting `.yawl` specification files |
| [`docs/reference/error-codes.md`](../reference/error-codes.md) | Catalogue of every exception class thrown by the YAWL engine, XML failure responses from the REST and servlet APIs, and resolution steps for common error conditions. | Developers debugging engine logs or handling exceptions in custom services |
| [`docs/reference/configuration.md`](../reference/configuration.md) | Complete lookup table for all YAWL configuration properties across `application.yml`, `yawl.properties`, and related configuration sources, with types, defaults, and valid ranges. | Developers and operators configuring or deploying the engine |
| [`docs/reference/environment-variables.md`](../reference/environment-variables.md) | Complete lookup table for all environment variables used by YAWL Docker and Kubernetes deployments, sourced from `.env.example` and `docker-compose.yml`. | Engineers writing Kubernetes manifests, Docker Compose files, or debugging container deployments |
| [`docs/reference/workflow-patterns.md`](../reference/workflow-patterns.md) | Quick-reference table for all workflow patterns (from the WF Patterns library) and their YAWL implementation status, XML constructs, and relevant source classes. | Developers designing specifications or implementing pattern support |
| [`docs/reference/implementation-status.md`](../reference/implementation-status.md) | Records the precise delta between what YAWL documentation describes and what is verifiably implemented in Java source code and covered by tests as of 2026-02-18. | Anyone reading any YAWL doc who needs to know whether the described feature actually works |

### Gap assessment

**All previously identified gaps now addressed.** YAWL schema, error codes, configuration properties, environment variables, and workflow patterns are all documented in reference form. The new `implementation-status.md` provides a ground-truth cross-reference for the entire doc set.

---

## Quadrant 4 — Explanation

**What belongs here:** Discursive prose that builds understanding of how and why the system works the way it does. Covers design rationale, architectural tradeoffs, conceptual models, historical context. Written to be read, not consulted. Deliberately avoids step-by-step instructions. Answers "why" rather than "how".

**What this is NOT:** How-to guides (those have steps), reference (factual lookup), or tutorials (those teach by doing).

### Existing docs that qualify

| File | Concept explained |
|---|---|
| `docs/architecture/decisions/ADR-001-dual-engine-architecture.md` | Why YAWL has both stateful and stateless engines; the forces that drove the split |
| `docs/architecture/decisions/ADR-003-maven-primary-ant-deprecated.md` | Why Ant was deprecated; the rationale for Maven as primary build system |
| `docs/architecture/decisions/ADR-021-stateless-vs-stateful-engine-selection.md` | When to choose stateless vs stateful; the selection criteria and tradeoffs |
| `docs/ARCHITECTURE-REFACTORING-PATTERNS.md` | Refactoring patterns applicable to the YAWL codebase; design reasoning |
| `docs/SCALING_AND_OBSERVABILITY_GUIDE.md` | Horizontal vs vertical scaling strategy; observability architecture rationale |
| `docs/performance/README.md` | Performance design philosophy; what drives YAWL's performance characteristics |
| `docs/performance/PERFORMANCE_TESTING_GUIDE.md` | How and why performance is measured the way it is |
| `docs/deployment/architecture-comparison.md` | Comparison of deployment topologies; tradeoffs of each |
| `docs/deployment/enterprise-cloud-architecture-assessment.md` | Enterprise cloud readiness analysis; architectural assessment |
| `docs/autonomous-agents/README.md` | Conceptual overview of the autonomous agent framework and its design |
| `docs/THESIS_Autonomous_Workflow_Agents.md` | Academic foundation for autonomous workflow agent design |
| `docs/security/security-overview.md` | Security architecture and threat model rationale |
| `docs/SPIFFE_INTEGRATION.md` | Why SPIFFE/SPIRE for zero-trust; the design rationale |
| `docs/INTEGRATION_GUIDE_CONSOLIDATED.md` | How YAWL integrates with external systems; architectural patterns |
| `docs/patterns/README.md` | Workflow patterns library — why 36 WF patterns and how they map to YAWL constructs |
| `docs/pasadena-tdd-manifesto.md` | Design philosophy behind test-driven development in this project |
| `docs/multi-cloud/project-analysis.md` | Analysis of multi-cloud deployment options and their architectural implications |
| `docs/ggen-use-cases-summary.md` | Explanation of ggen automation use cases and design approach |
| `docs/operations/scaling-guide.md` | Scaling design rationale; when and how to scale each component |

### New explanation docs added 2026-02-18

| File | Concept explained | Audience / when to use |
|---|---|---|
| [`docs/explanation/petri-net-foundations.md`](../explanation/petri-net-foundations.md) | Why YAWL chose Petri nets as its semantic basis, how places/transitions/tokens map to Java classes, and why YAWLnets extend plain Petri nets to support all 43 workflow patterns. | Developers and architects who need to reason about workflow correctness, soundness, or deadlock |
| [`docs/explanation/case-lifecycle.md`](../explanation/case-lifecycle.md) | The two interlocked state machines (case lifecycle and work item lifecycle) managed by `YNetRunner` and `YWorkItem`, and which method causes each state transition. | Developers modifying engine behaviour, debugging stuck cases, or implementing new services |
| [`docs/explanation/interface-architecture.md`](../explanation/interface-architecture.md) | What each of YAWL's four named interfaces (A, B, E, X) does, the direction of communication, and why the split exists — the mandatory first step before writing any integration code. | Every developer writing external integrations, custom services, or administrative tools |
| [`docs/explanation/worklet-service.md`](../explanation/worklet-service.md) | What the YAWL v4 Worklet Service was (RDR-based dynamic task substitution), why it is not available in v6, and what the v6 replacement pattern is. | Developers migrating v4 workflows that relied on the Worklet Service |
| [`docs/explanation/execution-profiles.md`](../explanation/execution-profiles.md) | How the decomposition type, `externalInteraction` mode, service reference, and codelet together form a task's execution profile and determine whether work items go to a human worklist, a named YAWL service, or an inline automated function. | Developers configuring task routing or implementing custom decomposition types |
| [`docs/explanation/multi-instance-tasks.md`](../explanation/multi-instance-tasks.md) | How multi-instance tasks create N parallel work item copies from a single token, the four cardinality parameters, and when to use MI tasks vs explicit AND-split/join patterns. | Developers designing specifications that process collections in parallel |
| [`docs/explanation/or-join-semantics.md`](../explanation/or-join-semantics.md) | Why YAWL's OR-join requires exhaustive reachability analysis (not a simple token counter), how the engine computes it, and the performance implications that distinguish YAWL from most other workflow formalisms. | Developers designing nets with OR-joins or debugging cases that appear stuck at a join |

### Gap assessment

**All previously identified core conceptual gaps are now addressed.** Petri net foundations, case lifecycle, Interface A/B/E/X, worklet service history, and advanced constructs (MI tasks, OR-join) are all explained. Remaining area: no explanation of the YAWL security model evolution from v4 (Security Service) to v6 (Spring Security / RBAC).

---

## Cross-Quadrant Navigation

### By topic

| Topic | Tutorials | How-to | Reference | Explanation |
|---|---|---|---|---|
| Deployment | `docs/tutorials/07-docker-dev-environment.md` | `DEPLOY-*.md`, `DEPLOYMENT-CHECKLIST.md` | `docs/deployment/java25-quick-reference.md` | `docs/deployment/architecture-comparison.md` |
| Build | *(see Tutorial 01)* | `docs/BUILD.md` | `docs/MAVEN_MODULE_STRUCTURE.md` | `docs/architecture/decisions/ADR-003-*.md` |
| REST API | `docs/tutorials/05-call-yawl-rest-api.md` | *(see Tutorial 05)* | `docs/API-REFERENCE.md`, `docs/REST-API-JAX-RS.md` | `docs/INTEGRATION_GUIDE_CONSOLIDATED.md` |
| Migration | *(missing)* | `docs/MIGRATION-v6.md`, `docs/migration/MIGRATION_EXECUTION_PLAN.md` | `docs/api/MIGRATION-5x-to-6.md` | `docs/architecture/decisions/ADR-004-*.md` |
| Engine architecture | *(missing)* | *(missing)* | `docs/v6/latest/facts/dual-family.json` | `docs/architecture/decisions/ADR-001-*.md`, `ADR-021-*.md` |
| Performance | *(missing)* | `docs/performance/PERFORMANCE_TESTING_GUIDE.md` | `docs/performance/PERFORMANCE_BASELINES.md`, `docs/BENCHMARK-METRICS.md` | `docs/performance/README.md`, `docs/SCALING_AND_OBSERVABILITY_GUIDE.md` |
| Security | *(missing)* | `docs/deployment/runbooks/SECURITY_RUNBOOK.md` | `docs/security/compliance-matrix.md` | `docs/security/security-overview.md`, `docs/SPIFFE_INTEGRATION.md` |
| Autonomous agents | `docs/tutorials/08-mcp-agent-integration.md` | `docs/autonomous-agents/docker-deployment-guide.md` | `docs/autonomous-agents/api-documentation.md` | `docs/autonomous-agents/README.md` |
| Workflow patterns | `docs/tutorials/04-write-a-yawl-specification.md` | *(missing)* | `docs/reference/workflow-patterns.md` | `docs/explanation/petri-net-foundations.md`, `docs/patterns/README.md` |
| Resource / org model | *(missing)* | `docs/how-to/configure-resource-service.md`, `docs/how-to/setup-org-model.md` | *(missing)* | *(missing)* |
| Multi-tenancy | *(missing)* | `docs/how-to/configure-multi-tenancy.md` | `docs/reference/configuration.md` | *(missing)* |
| Zero-trust / SPIFFE | *(missing)* | `docs/how-to/configure-spiffe.md` | `docs/reference/environment-variables.md` | `docs/SPIFFE_INTEGRATION.md` |
| Case / work item lifecycle | *(missing)* | *(missing)* | `docs/reference/error-codes.md` | `docs/explanation/case-lifecycle.md` |
| OR-join / MI tasks | *(missing)* | *(missing)* | `docs/reference/yawl-schema.md` | `docs/explanation/or-join-semantics.md`, `docs/explanation/multi-instance-tasks.md` |
| Configuration | *(missing)* | *(missing)* | `docs/reference/configuration.md`, `docs/reference/environment-variables.md` | *(missing)* |

---

## Archive and Process Docs (not Diataxis)

The following directories contain internal working documents, validation reports, and process artifacts that are not user-facing documentation and do not belong in any Diataxis quadrant. They are preserved for audit trail purposes.

| Directory | Contents |
|---|---|
| `docs/archive/2026-02-16/` | Point-in-time validation reports and session artifacts |
| `docs/CICD_*.md` | CI/CD implementation session notes (partially how-to, partially status) |
| `docs/PROMPT_CLOSE_TEST_GAPS.md` | Internal agent prompt — not documentation |
| `docs/plans/` | Internal planning documents |
| `docs/DEVELOPER_DOCUMENTATION_SUMMARY.md` | Summary report, not user-facing |
| `docs/MAVEN_BUILD_VALIDATION.md` | Build validation report, not user-facing |
| `docs/TEST_EXECUTION_SUMMARY.md` | Test run reports, not user-facing |
| `docs/validation/` | Validation artifacts |

---

## Summary of Gaps

| Quadrant | Status | Priority gap |
|---|---|---|
| Tutorials | **Good coverage** — six new tutorials added covering the core beginner journey | Add Tutorial 01 (build from source) and Tutorial 02 (write a simple spec) to this index once verified as complete |
| How-to | **Well covered** — deployment, migration, resource config, SPIFFE, multi-tenancy, and worklet routing all documented | Add monitoring/observability how-to (Prometheus, Grafana, OpenTelemetry) |
| Reference | **All gaps addressed** — schema, error codes, configuration, environment variables, workflow patterns, and implementation status now documented | Keep `implementation-status.md` updated with each new feature implementation |
| Explanation | **Core concepts now covered** — Petri nets, case lifecycle, interfaces, worklet service, execution profiles, MI tasks, OR-join all explained | Add explanation of the YAWL security model evolution (v4 Security Service to v6 Spring Security/RBAC) |

---

## Implementation Status

> **Before trusting any doc in this repository, check [`docs/reference/implementation-status.md`](../reference/implementation-status.md).**

This file records the precise delta between what YAWL documentation describes (including ADRs, Diataxis explanation pages, and tutorial content) and what is verifiably implemented in Java source code and covered by tests as of 2026-02-18. YAWL documentation may describe aspirational features that are not yet implemented. The implementation-status file is the authoritative ground truth for what actually works.

Key findings (see the file for full detail):
- The stateless engine (`YStatelessEngine`) is implemented and tested.
- The MCP server (`YawlMcpServer`) is implemented.
- The Resource Service, Worklet Service, and organisational model are NOT implemented in v6 (they existed in v4).
- Multi-tenancy configuration requires PostgreSQL and is partially implemented.

---

*Last updated: 2026-02-18. Reflects all Diataxis-quadrant documentation added in the 2026-02-18 session, plus the 236 pre-existing markdown files across docs/ and .claude/.*
