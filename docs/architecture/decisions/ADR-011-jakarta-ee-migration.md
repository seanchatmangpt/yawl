# ADR-011: Jakarta EE 10 Migration Strategy

## Status

**APPROVED**

## Date

2026-02-15

## Context

YAWL v5.x and earlier use the `javax.*` namespace for Java EE APIs (servlet, persistence,
transaction, validation, etc.). Spring Boot 3.x requires Jakarta EE 10, which uses the
`jakarta.*` namespace. This namespace shift affects:

1. **Servlet API**: `javax.servlet` -> `jakarta.servlet`
2. **Persistence API**: `javax.persistence` -> `jakarta.persistence`
3. **Transaction API**: `javax.transaction` -> `jakarta.transaction`
4. **Validation API**: `javax.validation` -> `jakarta.validation`
5. **Annotation API**: `javax.annotation` -> `jakarta.annotation`
6. **Inject API**: `javax.inject` -> `jakarta.inject`

The migration is required for:
- Spring Boot 3.x compatibility (ADR-004)
- Tomcat 10+ / Jetty 11+ deployment
- WildFly 27+ / Open Liberty 23+ deployment
- Future-proofing for Jakarta EE 11

### Scope

| Module | javax.* Usage | Impact |
|--------|---------------|--------|
| yawl-engine | javax.servlet, javax.persistence | HIGH |
| yawl-stateless | javax.persistence | MEDIUM |
| Interface A/B/X/E | javax.servlet | HIGH |
| resourcing | javax.persistence | MEDIUM |
| authentication | javax.servlet | HIGH |

### Decision Drivers

- Spring Boot 3.4 is the baseline for v6.0.0 (ADR-004)
- All major servlet containers require Jakarta EE 10+
- The migration is one-time; no rollback path exists
- External integrations may still require javax.* bridges

## Decision

### Migration Strategy: Clean Cutover

YAWL v6.0.0 performs a clean cutover to Jakarta EE 10. All `javax.*` imports are replaced
with `jakarta.*` equivalents. No dual-stack support is provided.

### Migration Steps

1. **Automated Import Replacement**: Use OpenRewrite recipe `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`
2. **Servlet API**: Replace `javax.servlet.*` with `jakarta.servlet.*`
3. **Persistence API**: Replace `javax.persistence.*` with `jakarta.persistence.*`
4. **Transaction API**: Replace `javax.transaction.*` with `jakarta.transaction.*`
5. **Validation API**: Replace `javax.validation.*` with `jakarta.validation.*`
6. **Dependency Updates**: Update all Maven dependencies to Jakarta EE 10 versions

### Maven Dependency Updates

```xml
<!-- Before (javax) -->
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <version>4.0.1</version>
</dependency>

<!-- After (jakarta) -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
</dependency>
```

### OpenRewrite Configuration

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>5.23.0</version>
    <configuration>
        <activeRecipes>
            <recipe>org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta</recipe>
        </activeRecipes>
    </configuration>
</plugin>
```

### Execution Command

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
    -Drewrite.activeRecipes=org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta
```

### Breaking Changes

| Change | Impact | Migration |
|--------|--------|-----------|
| `javax.servlet.*` -> `jakarta.servlet.*` | All servlets | Automated |
| `javax.persistence.*` -> `jakarta.persistence.*` | All JPA code | Automated |
| `javax.transaction.*` -> `jakarta.transaction.*` | Transaction management | Automated |
| `javax.validation.*` -> `jakarta.validation.*` | Bean validation | Automated |
| `@Resource` annotation package change | Resource injection | Automated |

## Consequences

### Positive

1. **Spring Boot 3.x compatibility**: Enables modern Spring Boot features
2. **Container compatibility**: Deploys to Tomcat 10+, Jetty 11+, WildFly 27+
3. **Future-proof**: Aligned with Jakarta EE roadmap
4. **Clean codebase**: No dual-stack complexity

### Negative

1. **Breaking change**: Existing deployments on older containers require migration
2. **Third-party libraries**: Some older libraries may still use javax.* and require shading
3. **Plugin compatibility**: Some Maven plugins may need updates

### Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Incomplete import replacement | LOW | HIGH | OpenRewrite automates; IDE inspections verify |
| Third-party library incompatibility | MEDIUM | MEDIUM | Test all integrations; shade if needed |
| Deployment to legacy containers | LOW | HIGH | Document minimum container versions |

## Alternatives Considered

### Alternative: Dual-Stack Support

**Rejected.** Supporting both `javax.*` and `jakarta.*` would require maintaining
separate module sets or complex shading, increasing maintenance burden without
long-term value.

### Alternative: Delay Migration

**Rejected.** Spring Boot 3.x is required for v6.0.0 features (virtual threads,
native compilation hints). Delaying Jakarta migration blocks the entire platform
upgrade.

## Related ADRs

- ADR-004: Spring Boot 3.4 + Java 25 (requires Jakarta EE 10)
- ADR-015: Persistence Layer Architecture (uses jakarta.persistence)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-15
**Implementation Status:** COMPLETE
**Review Date:** 2026-08-15

---

**Revision History:**
- 2026-02-15: Initial ADR documenting Jakarta EE 10 migration strategy
