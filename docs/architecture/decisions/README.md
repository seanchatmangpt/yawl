# Architecture Decision Records (ADRs)

**YAWL v5.2 Architecture Decisions**

This directory contains all architectural decisions for YAWL v5.2, documented using the ADR format.

---

## ADR Index

### Core Architecture

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-001](ADR-001-dual-engine-architecture.md) | Dual Engine Architecture (Stateful + Stateless) | ACCEPTED | 2026-02-10 | Architecture |
| [ADR-002](ADR-002-singleton-vs-instance-yengine.md) | Singleton vs Instance-based YEngine | ACCEPTED with CAVEATS | 2026-02-10 | Architecture |
| [ADR-003](ADR-003-maven-primary-ant-deprecated.md) | Maven Primary, Ant Deprecated | ACCEPTED | 2026-02-10 | Build System |
| [ADR-004](ADR-004-spring-boot-34-java-25.md) | Spring Boot 3.4 + Java 25 | ACCEPTED | 2026-02-10 | Platform |
| [ADR-005](ADR-005-spiffe-spire-zero-trust.md) | SPIFFE/SPIRE for Zero-Trust Identity | ACCEPTED | 2026-02-12 | Security |

### Observability & Operations

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

### Migration & Compatibility

| ADR | Title | Status | Date | Category |
|-----|-------|--------|------|----------|
| [ADR-011](ADR-011-jakarta-ee-migration.md) | Jakarta EE 10 Migration Strategy | APPROVED | 2026-02-15 | Migration |

---

## ADR Status Definitions

- **PROPOSED**: Under discussion, not yet decided
- **ACCEPTED**: Approved and in implementation
- **ACCEPTED with CAVEATS**: Approved with conditions or future changes
- **APPROVED**: Approved but not yet implemented
- **DEPRECATED**: No longer recommended
- **SUPERSEDED**: Replaced by another ADR

---

## Quick Reference

### By Category

**Architecture:**
- ADR-001: Dual Engine Architecture
- ADR-002: Singleton vs Instance-based YEngine

**Security:**
- ADR-005: SPIFFE/SPIRE for Zero-Trust Identity

**Performance:**
- ADR-007: Repository Pattern for Caching
- ADR-010: Virtual Threads for Scalability

**Cloud & Deployment:**
- ADR-009: Multi-Cloud Strategy

**Platform & Tools:**
- ADR-003: Maven Primary, Ant Deprecated
- ADR-004: Spring Boot 3.4 + Java 25
- ADR-006: OpenTelemetry for Observability
- ADR-008: Resilience4j for Circuit Breaking

**Migration:**
- ADR-011: Jakarta EE 10 Migration

---

## Reading Order for New Team Members

1. **Start Here:** ADR-001 (Dual Engine Architecture)
2. **Platform:** ADR-004 (Spring Boot + Java 25)
3. **Build System:** ADR-003 (Maven Primary)
4. **Security:** ADR-005 (SPIFFE/SPIRE)
5. **Cloud Deployment:** ADR-009 (Multi-Cloud Strategy)
6. **Performance:** ADR-010 (Virtual Threads)
7. **Observability:** ADR-006 (OpenTelemetry)
8. **Resilience:** ADR-008 (Circuit Breaking)

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
```

---

## Related Documentation

- **Architecture Overview:** `/home/user/yawl/docs/architecture/INDEX.md`
- **Deployment Guides:** `/home/user/yawl/docs/deployment/`
- **Migration Guides:** `/home/user/yawl/docs/migration/`
- **Production Readiness:** `/home/user/yawl/docs/PRODUCTION_READINESS_CHECKLIST.md`

---

## Key Decisions Summary

### Technology Stack
- **Language:** Java 25 (LTS)
- **Framework:** Spring Boot 3.4
- **Build System:** Maven (Ant deprecated)
- **Platform:** Jakarta EE 10
- **Database:** Hibernate 6.5, PostgreSQL 13+

### Architecture Patterns
- **Engines:** Dual (Stateful + Stateless)
- **Dependency Injection:** Instance-based (Singleton deprecated)
- **Caching:** Repository pattern
- **Resilience:** Circuit breaker (Resilience4j)

### Cloud & Security
- **Identity:** SPIFFE/SPIRE
- **Observability:** OpenTelemetry
- **Deployment:** Multi-cloud (GCP, AWS, Azure, Oracle)
- **Scalability:** Virtual threads (Project Loom)

---

## Contact

**Architecture Team:** architecture@yawl.org
**Slack:** #architecture-decisions
**Wiki:** https://wiki.yawl.org/architecture/adrs

---

**Last Updated:** 2026-02-16
**Maintained by:** YAWL Architecture Team
