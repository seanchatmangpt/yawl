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

> There are no proper Diataxis tutorials in this project yet. The closest candidate is `docs/user-guide/getting-started.md`, which has tutorial intent but mixes in conceptual explanation and reference tables. It needs restructuring before it qualifies.

| File | Notes |
|---|---|
| `docs/user-guide/getting-started.md` | Partial — introduces concepts but does not guide a learner through completing a working workflow from scratch. Needs a concrete learning outcome (e.g., "run your first YAWL case in 10 minutes"). |
| `docs/resilience/QUICK_START.md` | Partial — scoped to resilience configuration; could become a tutorial with a clearer learning goal. |
| `docs/QUICK-START.md` | Partial — lists steps but skips explanations for why, mixes setup options; needs restructuring. |

### Gap assessment

**Critical gap.** No tutorial exists that walks a new user through the full beginner journey: install YAWL, load a sample specification, start a case, complete a work item, and observe the result. This is the single most important documentation gap for user adoption. The `docs/tutorials/` directory has been created as the target location.

**Recommended tutorials to write:**
1. `docs/tutorials/01-run-your-first-workflow.md` — Install, start engine, run the built-in demo spec
2. `docs/tutorials/02-write-a-simple-specification.md` — Create a two-task sequential workflow in XML
3. `docs/tutorials/03-deploy-to-docker.md` — Container-based first deployment end-to-end

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

### Gap assessment

**Well covered.** Deployment and migration tasks are thoroughly documented. The main gaps are:

- No how-to for loading and running a workflow specification via the REST API (programmatic case management)
- No how-to for configuring resource allocation rules and organisational models
- No how-to for integrating a custom work item handler
- No how-to for configuring SPIFFE/SPIRE zero-trust networking (the doc `docs/SPIFFE_INTEGRATION_GUIDE.md` exists but reads as explanation, not steps)

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

### Gap assessment

**Good coverage of APIs and architecture decisions. Key gaps:**

- No schema reference for the YAWL XML specification format (XSD documentation exists at `schema/YAWL_Schema4.0.xsd` but no human-readable reference page)
- No error code catalogue (error messages returned by the engine are undocumented in reference form)
- No configuration property reference (all available `yawl.properties` keys, their types, defaults, and valid ranges)
- No environment variable reference for Docker/Kubernetes deployments

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

### Gap assessment

**Moderately covered for architecture, weak for core workflow concepts. Key gaps:**

- No explanation of YAWL's Petri net foundations and why Petri nets were chosen as the semantic basis
- No explanation of the 36 workflow patterns — what they are, why they matter, how YAWL implements each
- No explanation of the case lifecycle — how a workflow case progresses through states and why
- No explanation of the worklet service — what it is, why it enables dynamic adaptation
- No explanation of the Interface A / B / E / X architecture — what each interface does and why the split exists

---

## Cross-Quadrant Navigation

### By topic

| Topic | Tutorials | How-to | Reference | Explanation |
|---|---|---|---|---|
| Deployment | *(missing)* | `DEPLOY-*.md`, `DEPLOYMENT-CHECKLIST.md` | `docs/deployment/java25-quick-reference.md` | `docs/deployment/architecture-comparison.md` |
| Build | *(missing)* | `docs/BUILD.md` | `docs/MAVEN_MODULE_STRUCTURE.md` | `docs/architecture/decisions/ADR-003-*.md` |
| REST API | *(missing)* | *(missing)* | `docs/API-REFERENCE.md`, `docs/REST-API-JAX-RS.md` | `docs/INTEGRATION_GUIDE_CONSOLIDATED.md` |
| Migration | *(missing)* | `docs/MIGRATION-v6.md`, `docs/migration/MIGRATION_EXECUTION_PLAN.md` | `docs/api/MIGRATION-5x-to-6.md` | `docs/architecture/decisions/ADR-004-*.md` |
| Engine architecture | *(missing)* | *(missing)* | `docs/v6/latest/facts/dual-family.json` | `docs/architecture/decisions/ADR-001-*.md`, `ADR-021-*.md` |
| Performance | *(missing)* | `docs/performance/PERFORMANCE_TESTING_GUIDE.md` | `docs/performance/PERFORMANCE_BASELINES.md`, `docs/BENCHMARK-METRICS.md` | `docs/performance/README.md`, `docs/SCALING_AND_OBSERVABILITY_GUIDE.md` |
| Security | *(missing)* | `docs/deployment/runbooks/SECURITY_RUNBOOK.md` | `docs/security/compliance-matrix.md` | `docs/security/security-overview.md`, `docs/SPIFFE_INTEGRATION.md` |
| Autonomous agents | *(missing)* | `docs/autonomous-agents/docker-deployment-guide.md` | `docs/autonomous-agents/api-documentation.md` | `docs/autonomous-agents/README.md` |

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
| Tutorials | **Critical gap** — no proper tutorials exist | Write a "run your first workflow" tutorial immediately |
| How-to | Well covered for deployment; gaps in API usage and custom integration | Add REST API how-tos and custom handler integration guide |
| Reference | Good API and ADR coverage; gaps in config properties and error codes | Add configuration property reference and error code catalogue |
| Explanation | Good for architecture decisions; gaps in core Petri net and workflow concepts | Add Petri net foundations and Interface A/B/E/X explanation |

---

*Last updated: 2026-02-18. Reflects 236 markdown files across docs/ and .claude/.*
