# ADR-018: JavaDoc-to-OpenAPI Documentation Generation

## Status
**ACCEPTED**

## Context

YAWL v6.0.0 adopts OpenAPI-first design (ADR-012): the YAML spec is the authoritative
contract. However, the JAX-RS resource classes contain significant implementation-level
documentation that should be surfaced without requiring manual duplication in the YAML.

Two sources of documentation truth currently exist:
1. `docs/api/openapi-v6.yaml` — API contract (user-facing)
2. JAX-RS resource classes (e.g., `InterfaceBResource.java`) — implementation notes,
   parameter constraints, exception conditions

The challenge is keeping these two documents in sync as both evolve. Manually maintaining
both is error-prone. The build needs to be the single gate that detects divergence.

Additionally, YAWL's internal service layer (engine core, net runners, data binding) has
sparse JavaDoc. For a codebase targeting enterprise adoption, this documentation gap is
a barrier to contributors and integration teams.

## Decision

**YAWL v6.0.0 adopts a two-track documentation generation strategy:**

**Track 1 — API Documentation**: OpenAPI YAML is the source of truth. A Maven plugin
generates Swagger UI and ReDoc HTML at build time. JAX-RS annotations are cross-validated
against the OpenAPI spec by a custom annotation processor (not used as the primary source).

**Track 2 — Javadoc**: A comprehensive JavaDoc generation pass runs during `mvn verify`,
producing HTML documentation for all public APIs in the `org.yawlfoundation.yawl.*` packages.
A Javadoc quality gate (doclet) fails the build if public methods lack `@param`, `@return`,
and `@throws` documentation.

### Track 1: API Documentation Pipeline

```
docs/api/openapi-v6.yaml
    │
    ├── Spectral lint (CI gate, fails on errors)
    │
    ├── openapi-generator → Java client SDK (published to Maven Central)
    │
    ├── redoc-cli → docs/api/generated/index.html (ReDoc single-page)
    │
    └── swagger-ui-dist → docs/api/generated/swagger/ (interactive Swagger UI)
```

The Maven build generates documentation during the `prepare-package` phase:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.10.0</version>
    <executions>
        <!-- HTML documentation -->
        <execution>
            <id>generate-html-docs</id>
            <goals><goal>generate</goal></goals>
            <phase>prepare-package</phase>
            <configuration>
                <inputSpec>${project.basedir}/docs/api/openapi-v6.yaml</inputSpec>
                <generatorName>html2</generatorName>
                <outputDir>${project.build.directory}/api-docs</outputDir>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Track 2: Javadoc Quality Gate

A custom Javadoc doclet (`YawlDocQualityDoclet`) inspects every public method and:
1. Fails the build if `@param` tags are missing for any parameter
2. Fails the build if `@return` is missing on non-void public methods
3. Fails the build if `@throws` is missing for checked exceptions in the signature
4. Emits warnings (not failures) for missing `@throws` on runtime exceptions
5. Skips generated code packages (e.g., `org.yawlfoundation.yawl.client.*`)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.11.2</version>
    <executions>
        <execution>
            <id>javadoc-quality-gate</id>
            <phase>verify</phase>
            <goals><goal>javadoc</goal></goals>
            <configuration>
                <doclet>org.yawlfoundation.yawl.tools.YawlDocQualityDoclet</doclet>
                <docletArtifact>
                    <groupId>org.yawlfoundation.yawl</groupId>
                    <artifactId>yawl-doc-tools</artifactId>
                    <version>${project.version}</version>
                </docletArtifact>
                <failOnWarnings>false</failOnWarnings>
                <failOnError>true</failOnError>
                <excludePackageNames>
                    org.yawlfoundation.yawl.client.*,
                    org.yawlfoundation.yawl.generated.*
                </excludePackageNames>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Cross-Validation: JAX-RS vs OpenAPI

A custom annotation processor (`YawlOpenApiValidator`) runs during compilation and:

1. Discovers all `@Path` annotated methods in JAX-RS resource classes
2. Resolves the corresponding `operationId` from the class's `@OpenApiOperation` annotation
3. Validates that the resolved operation exists in `openapi-v6.yaml`
4. Fails compilation if a JAX-RS endpoint has no corresponding OpenAPI operation

```java
// Custom annotation on each JAX-RS resource method
@OpenApiOperation("getWorkItem")  // must match operationId in openapi-v6.yaml
@GET
@Path("/workitems/{itemId}")
public Response getWorkItem(@PathParam("itemId") String itemId,
                            @QueryParam("sessionHandle") String sessionHandle) {
    // implementation
}
```

This one-way validation (JAX-RS → OpenAPI) catches cases where new endpoints are added
in code without updating the specification.

### Documentation Hosting

Generated documentation is packaged into the engine WAR/JAR and served at:

| URL | Content |
|-----|---------|
| `/api/ui` | Swagger UI (interactive) |
| `/api/docs` | ReDoc (read-only, clean) |
| `/api/spec` | Raw OpenAPI YAML download |
| `/api/javadoc` | Javadoc HTML (admin access only) |

## Consequences

### Positive

1. OpenAPI YAML remains the single authoritative source for the API contract
2. JAX-RS annotation processor catches specification drift at compile time
3. Javadoc quality gate enforces documentation standards across the codebase
4. Generated Swagger UI / ReDoc eliminates the need for separate documentation deployments
5. Client SDK generation from the same spec reduces client integration errors

### Negative

1. `@OpenApiOperation` annotation on every JAX-RS method adds minor verbosity
2. Javadoc quality gate will initially fail on poorly documented legacy code —
   requires a one-time documentation pass before the gate is enabled
3. `redoc-cli` requires Node.js in the build environment (Docker build image handles this)

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Spectral rules too strict, blocking valid extensions | LOW | LOW | Spectral config is version-controlled; rules are reviewed before merge |
| Javadoc gate blocks valid code lacking docs | HIGH (initially) | MEDIUM | Gate enabled progressively: warn-only for first 30 days, then error |
| OpenAPI generator produces broken client code | LOW | MEDIUM | Generated client validated by integration test suite on every build |

## Alternatives Considered

### Springdoc-OpenAPI (annotation-driven spec generation)
Springdoc-OpenAPI generates the OpenAPI spec from Spring MVC annotations at runtime.
Rejected because YAWL uses JAX-RS (Jersey), not Spring MVC. Additionally, annotation-driven
generation produces specs that diverge from carefully designed hand-authored specs — the
OpenAPI-first principle (ADR-012) requires the YAML to be the source of truth.

### OpenAPI-to-JAX-RS Code Generation
The inverse approach: generate JAX-RS stubs from the OpenAPI YAML, then implement the
stubs. This would guarantee zero divergence between spec and implementation. Deferred
to v6.1 as it requires significant refactoring of existing resource classes.

### MkDocs or Docusaurus for Documentation Portal
A separate documentation portal (MkDocs, Docusaurus) could host all documentation
including ADRs, guides, and API reference. Considered for future work. For v6.0.0,
embedding docs in the engine WAR is sufficient and simpler to deploy.

## Related ADRs

- ADR-022: OpenAPI-First Design (establishes the OpenAPI YAML as source of truth)
- ADR-016: API Changelog and Deprecation Policy (CHANGELOG.md maintained alongside JavaDoc)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** PLANNED (v6.0.0 documentation tooling sprint)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version
