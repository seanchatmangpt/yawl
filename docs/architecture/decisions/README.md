# Architecture Decision Records (ADRs)

**YAWL v6.0.0 Architecture Decisions**

This directory contains all architectural decisions for YAWL, documented using the ADR format.
ADRs are the authoritative record of _why_ the system is designed the way it is.

**Machine-readable index**: [ADR-INDEX.json](ADR-INDEX.json) — supports search, filtering, and tooling integration.

---

## ADR Index

### Core Architecture

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-001](ADR-001-dual-engine-architecture.md) | Dual Engine Architecture (Stateful + Stateless) | ACCEPTED | 2026-02-10 | Architecture |
| [ADR-002](ADR-002-singleton-vs-instance-yengine.md) | Singleton vs Instance-based YEngine | ACCEPTED with CAVEATS | 2026-02-10 | Architecture |
| [ADR-021](ADR-021-stateless-vs-stateful-engine-selection.md) | Automatic Engine Selection for v6.0.0 | ACCEPTED | 2026-02-17 | Architecture |

### Build System & Platform

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-003](ADR-003-maven-primary-ant-deprecated.md) | Maven Primary, Ant Deprecated | ACCEPTED | 2026-02-10 | Build System |
| [ADR-004](ADR-004-spring-boot-34-java-25.md) | Spring Boot 3.4 + Java 25 | ACCEPTED | 2026-02-10 | Platform |

### Security & Identity

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-005](ADR-005-spiffe-spire-zero-trust.md) | SPIFFE/SPIRE for Zero-Trust Identity | ACCEPTED | 2026-02-12 | Security |
| [ADR-017](ADR-017-authentication-and-sessions.md) | Authentication and Session Management for v6.0.0 | ACCEPTED | 2026-02-17 | Security |

### Observability & Resilience

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-006](ADR-006-opentelemetry-observability.md) | OpenTelemetry for Observability | ACCEPTED | 2026-02-12 | Observability |
| [ADR-007](ADR-007-repository-pattern-caching.md) | Repository Pattern for Caching | ACCEPTED | 2026-02-13 | Performance |
| [ADR-008](ADR-008-resilience4j-circuit-breaking.md) | Resilience4j for Circuit Breaking | ACCEPTED | 2026-02-13 | Resilience |

### Cloud & Deployment

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-009](ADR-009-multi-cloud-strategy.md) | Multi-Cloud Strategy (GCP/AWS/Azure/Oracle) | ACCEPTED | 2026-02-13 | Cloud |
| [ADR-010](ADR-010-virtual-threads-scalability.md) | Virtual Threads for Scalability | ACCEPTED | 2026-02-14 | Performance |
| [ADR-014](ADR-014-clustering-and-horizontal-scaling.md) | Clustering and Horizontal Scaling Architecture | ACCEPTED | 2026-02-17 | Scalability |
| [ADR-023](ADR-023-mcp-a2a-cicd-deployment.md) | MCP/A2A CI/CD Deployment Architecture | ACCEPTED | 2026-02-18 | Deployment |
| [ADR-024](ADR-024-multi-cloud-agent-deployment.md) | Multi-Cloud Agent Deployment Topology | ACCEPTED | 2026-02-18 | Cloud |

### Security (Agent & Integration)

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-012](ADR-012-a2a-authentication-architecture.md) | A2A Server Authentication Architecture | ACCEPTED | 2026-02-17 | Security |

### API Design & Documentation

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-022](ADR-022-openapi-first-design.md) | OpenAPI-First API Design for v6.0.0 | ACCEPTED | 2026-02-17 | API Design |
| [ADR-016](ADR-016-api-changelog-deprecation-policy.md) | API Changelog and Deprecation Policy | ACCEPTED | 2026-02-17 | API Governance |
| [ADR-018](ADR-018-javadoc-to-openapi-generation.md) | JavaDoc-to-OpenAPI Documentation Generation | ACCEPTED | 2026-02-17 | Documentation |
| [ADR-020](ADR-020-workflow-pattern-library.md) | Workflow Pattern Library Architecture | ACCEPTED | 2026-02-17 | Documentation |

### Persistence & Data

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-015](ADR-015-persistence-layer-v6.md) | Persistence Layer Architecture for v6.0.0 | ACCEPTED | 2026-02-17 | Persistence |

### Schema & Specification

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-013](ADR-013-schema-versioning-strategy.md) | YAWL Schema Versioning Strategy for v6.0.0 | ACCEPTED | 2026-02-17 | Schema |

### Agent Architecture

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-019](ADR-019-autonomous-agent-framework.md) | Autonomous Agent Framework Architecture | ACCEPTED | 2026-02-17 | Agent Architecture |
| [ADR-025](ADR-025-agent-coordination-protocol.md) | Agent Coordination Protocol and Conflict Resolution | ACCEPTED | 2026-02-18 | Agent Architecture |

### Migration & Compatibility

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-011](ADR-011-jakarta-ee-migration.md) | Jakarta EE 10 Migration Strategy | APPROVED | 2026-02-15 | Migration |

---

## ADR Status Definitions

| Status | Meaning |
|--------|---------|
| **PROPOSED** | Under discussion, not yet decided |
| **ACCEPTED** | Approved and in implementation |
| **ACCEPTED with CAVEATS** | Approved with conditions or future changes |
| **APPROVED** | Approved but not yet implemented |
| **IN PROGRESS** | Actively being implemented |
| **DEPRECATED** | No longer recommended; see superseding ADR |
| **SUPERSEDED** | Replaced by another ADR (links to replacement) |

---

## Quick Reference

### By Category

**Architecture:**
- ADR-001: Dual Engine Architecture
- ADR-002: Singleton vs Instance-based YEngine
- ADR-021: Automatic Engine Selection (v6.0)

**Security:**
- ADR-005: SPIFFE/SPIRE for Zero-Trust Identity
- ADR-012: A2A Server Authentication Architecture (v6.0)
- ADR-017: JWT Authentication and Session Management (v6.0)

**Performance:**
- ADR-007: Repository Pattern for Caching
- ADR-010: Virtual Threads for Scalability

**Cloud & Deployment:**
- ADR-009: Multi-Cloud Strategy
- ADR-014: Clustering and Horizontal Scaling (v6.1)
- ADR-023: MCP/A2A CI/CD Deployment Architecture (v6.0)
- ADR-024: Multi-Cloud Agent Deployment Topology (v6.1)

**API Design:**
- ADR-022: OpenAPI-First Design (v6.0)
- ADR-016: API Deprecation Policy (v6.0)
- ADR-018: JavaDoc-to-OpenAPI Generation (v6.0)

**Persistence:**
- ADR-015: Persistence Layer — HikariCP, Flyway, Envers, Multi-Tenancy (v6.0)

**Schema:**
- ADR-013: Schema Versioning Strategy (v6.0)

**Agent Architecture:**
- ADR-019: Autonomous Agent Framework (v6.0)
- ADR-025: Agent Coordination Protocol and Conflict Resolution (v6.0/v6.1)

**Documentation:**
- ADR-020: Workflow Pattern Library (v6.0)

**Platform & Tools:**
- ADR-003: Maven Primary, Ant Deprecated
- ADR-004: Spring Boot 3.4 + Java 25
- ADR-006: OpenTelemetry for Observability
- ADR-008: Resilience4j for Circuit Breaking

**Migration:**
- ADR-011: Jakarta EE 10 Migration

---

## Reading Order for New Team Members

**v5.x baseline (ADRs 001-011):**
1. ADR-001: Dual Engine Architecture
2. ADR-004: Spring Boot + Java 25
3. ADR-003: Maven Primary
4. ADR-005: SPIFFE/SPIRE
5. ADR-009: Multi-Cloud Strategy
6. ADR-010: Virtual Threads
7. ADR-006: OpenTelemetry
8. ADR-008: Circuit Breaking

**v6.0 additions (ADRs 012-025):**
9. ADR-012: A2A Server Authentication
10. ADR-022: OpenAPI-First Design
11. ADR-013: Schema Versioning
12. ADR-015: Persistence Layer
13. ADR-017: JWT Authentication
14. ADR-019: Autonomous Agent Framework
15. ADR-021: Engine Selection
16. ADR-023: MCP/A2A CI/CD Deployment
17. ADR-025: Agent Coordination Protocol
18. ADR-014: Clustering (planned for v6.1)
19. ADR-024: Multi-Cloud Agent Deployment (planned for v6.1)
20. ADR-016: Deprecation Policy
21. ADR-018: Doc Generation
22. ADR-020: Pattern Library

---

## ADR Template

When creating new ADRs, use this structure:

```markdown
# ADR-XXX: [Title]

## Status
[PROPOSED | ACCEPTED | DEPRECATED | SUPERSEDED]

## Context
[Why are we making this decision? What problem are we solving?]

## Decision
[What did we decide? How will we implement it?]

## Consequences
### Positive
[Benefits of this decision]

### Negative
[Drawbacks and trade-offs]

### Risks
[Potential problems and mitigations]

## Alternatives Considered
[What other options did we evaluate?]

## Related ADRs
[Links to related decisions]

## Implementation Notes
[Practical guidance for implementation]

## Approval
**Approved by:** [Name/Team]
**Date:** [YYYY-MM-DD]
**Implementation Status:** [Status]
**Review Date:** [YYYY-MM-DD]
```

The machine-readable `ADR-INDEX.json` must be updated whenever a new ADR is added
or an existing ADR's status changes.

---

## Related Documentation

- **Architecture Overview:** `/home/user/yawl/docs/architecture/INDEX.md`
- **API Specification:** `/home/user/yawl/docs/api/openapi-v6.yaml`
- **API Changelog:** `/home/user/yawl/docs/api/CHANGELOG.md`
- **Migration Guide:** `/home/user/yawl/docs/api/MIGRATION-5x-to-6.md`
- **Pattern Library:** `/home/user/yawl/docs/patterns/README.md`
- **Production Readiness:** `/home/user/yawl/docs/PRODUCTION_READINESS_CHECKLIST.md`
- **ADR Machine Index:** `ADR-INDEX.json`
- **Architecture Patterns (Java 25):** `/home/user/yawl/.claude/ARCHITECTURE-PATTERNS-JAVA25.md`

---

## Key Decisions Summary

### Technology Stack
- **Language:** Java 25 (LTS, preview features enabled)
- **Framework:** Spring Boot 3.4+
- **Build System:** Maven (Ant deprecated)
- **Platform:** Jakarta EE 10
- **Database:** Hibernate 6.6, PostgreSQL 13+, Flyway migrations

### Architecture Patterns
- **Engines:** Dual (Stateful + Stateless), automatic selection in v6.0
- **Dependency Injection:** Instance-based (Singleton deprecated)
- **Caching:** Repository pattern (Caffeine)
- **Resilience:** Circuit breaker (Resilience4j)
- **Clustering:** Redis lease protocol (v6.1)
- **Agent Registry:** CockroachDB federation (v6.1, multi-cloud)

### API Design
- **Contract:** OpenAPI 3.1.0 (docs/api/openapi-v6.yaml)
- **Authentication:** JWT HMAC-SHA256 session handles
- **Deprecation:** 12-month notice period, RFC 8594 headers

### Cloud & Security
- **Service Identity:** SPIFFE/SPIRE
- **User Auth:** JWT sessions
- **Observability:** OpenTelemetry
- **Deployment:** Multi-cloud (GCP, AWS, Azure, Oracle)
- **Scalability:** Virtual threads (Project Loom) + clustering (v6.1)

### Agent & Integration
- **MCP:** STDIO transport (local), SSE transport (cloud) via YawlMcpServer
- **A2A:** HTTP REST via YawlA2AServer, authenticated with JWT/SPIFFE/API key
- **Agent Coordination:** Partition strategy, handoff protocol, conflict resolution
- **Claude SDK:** MCP tool use (Path 1) + A2A orchestration (Path 2)

---

## Contact

**Architecture Team:** architecture@yawl.org
**Slack:** #architecture-decisions
**Wiki:** https://wiki.yawl.org/architecture/adrs

---

**Last Updated:** 2026-02-18
**Maintained by:** YAWL Architecture Team
