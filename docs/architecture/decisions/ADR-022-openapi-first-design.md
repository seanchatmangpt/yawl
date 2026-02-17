# ADR-022: OpenAPI-First API Design for v6.0.0

## Status
**ACCEPTED**

## Context

YAWL v5.x REST endpoints evolved organically from the servlet-based Interface B/A/X/E
contracts. The result is:

1. **No machine-readable contract.** Clients must parse documentation or reverse-engineer
   the servlet source to discover parameter names, response shapes, and error codes.
2. **Inconsistent content negotiation.** Some endpoints return XML, some JSON, and the
   choice is undocumented.
3. **No versioning strategy.** Breaking changes are invisible to consumers until runtime.
4. **Documentation drift.** Markdown docs (`docs/API-REFERENCE.md`) are maintained
   separately from the code and fall out of sync quickly.
5. **No SDK generation.** Client teams in Python, JavaScript, and Go must hand-craft
   HTTP calls or maintain their own ad-hoc wrappers.

For v6.0.0, YAWL targets broader ecosystem adoption including autonomous agent
integration (MCP, A2A) and third-party workflow toolchains. These integrators require
a stable, machine-readable contract.

## Decision

**YAWL v6.0.0 adopts an OpenAPI-first design process.**

The canonical API contract is `docs/api/openapi-v6.yaml` (OpenAPI 3.1.0). This file is:

- The authoritative source of truth for all REST endpoints
- Validated against the OpenAPI 3.1 JSON Schema on every CI build
- Converted to interactive documentation (Swagger UI, ReDoc) at build time
- Used as input for client SDK generation (Java, Python, TypeScript)

### Process

1. **Design in YAML first.** New endpoints are specified in `openapi-v6.yaml` before
   any Java code is written.
2. **Implement against the contract.** JAX-RS resource classes are annotated to match
   the OpenAPI specification exactly.
3. **Validate on CI.** `mvn verify` includes a Spectral lint step that fails the build
   if the implementation diverges from the spec.
4. **Generate clients.** The `openapi-generator-maven-plugin` produces a Java client
   library published as `yawl-api-client-6.x`.

### Specification Location

```
docs/
  api/
    openapi-v6.yaml          ← canonical contract (this project)
    openapi-v6-internal.yaml ← admin/internal endpoints (not published)
```

### Interface Mapping

| OpenAPI Tag | YAWL Interface | Base Path |
|-------------|----------------|-----------|
| Session     | Interface B    | `/ib`     |
| WorkItems   | Interface B    | `/ib`     |
| Cases       | Interface B    | `/ib`     |
| Specifications | Interface A | `/ia`     |
| Extended    | Interface X    | `/ix`     |
| Events      | Interface E    | `/ie`     |
| Admin       | Internal       | `/admin`  |

### Versioning Strategy

API versions are expressed as `MAJOR.MINOR.PATCH` in the `info.version` field.

- **MAJOR** increments on breaking changes (removed endpoints, incompatible schema changes)
- **MINOR** increments on backward-compatible additions (new endpoints, new optional fields)
- **PATCH** increments on documentation and clarification changes

Breaking changes must be announced in `docs/api/CHANGELOG.md` with migration guidance
at least one minor release before removal. Deprecated endpoints carry an
`x-deprecated-since` extension and are removed in the next major version.

## Consequences

### Positive

1. Single source of truth eliminates documentation drift
2. Interactive documentation available without extra work (Swagger UI served at `/api/ui`)
3. Generated client SDKs reduce integration friction for third parties
4. Spectral linting enforces consistency rules across the whole API surface
5. Contract testing (Pact or Dredd) becomes possible for integration tests

### Negative

1. Schema maintenance overhead: OpenAPI YAML must be updated alongside code changes
2. Generator limitations: some Java idioms (XML/JSON dual response) require workarounds
3. Initial setup cost for CI pipeline integration (~2 engineer-days)

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Spec and code diverge | MEDIUM | HIGH | Spectral CI gate blocks merges if spec is invalid |
| Generator produces unusable client | LOW | MEDIUM | Review generated client against integration tests |
| OpenAPI 3.1 tooling immaturity | LOW | LOW | Fall back to 3.0.3 if critical tools lack 3.1 support |

## Alternatives Considered

### GraphQL
Rejected. YAWL's workflow data model is fundamentally document-oriented (XML specifications,
task data payloads). GraphQL's graph traversal model is a poor fit. Existing Interface B
clients use simple HTTP — requiring a GraphQL client library would be a larger migration.

### gRPC / Protobuf
Rejected for the public API. gRPC would be appropriate for internal engine-to-engine
communication (see ADR-014: Clustering) but is too heavyweight for typical workflow
client integrations.

### AsyncAPI (for events)
Partially adopted. Interface E event subscriptions use webhooks and will be described
in a companion AsyncAPI 3.0 document (`docs/api/asyncapi-v6.yaml`). REST endpoints
for subscription management remain in the OpenAPI spec.

## Related ADRs

- ADR-001: Dual Engine Architecture (both engines expose same OpenAPI contract)
- ADR-004: Spring Boot 3.4 + Java 25 (Jersey JAX-RS implementation)
- ADR-016: API Changelog and Deprecation Policy
- ADR-018: JavaDoc-to-OpenAPI Documentation Generation

## Implementation Notes

### Maven Configuration

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <version>7.10.0</version>
  <executions>
    <execution>
      <id>generate-java-client</id>
      <goals><goal>generate</goal></goals>
      <configuration>
        <inputSpec>${project.basedir}/docs/api/openapi-v6.yaml</inputSpec>
        <generatorName>java</generatorName>
        <library>jersey3</library>
        <outputDir>${project.build.directory}/generated-sources/openapi</outputDir>
        <apiPackage>org.yawlfoundation.yawl.client.api</apiPackage>
        <modelPackage>org.yawlfoundation.yawl.client.model</modelPackage>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Spectral Lint Rule (`.spectral.yaml`)

```yaml
extends: ["spectral:oas"]
rules:
  operation-operationId: error
  operation-tags: error
  info-contact: error
  oas3-schema: error
  no-$ref-siblings: warn
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** IN PROGRESS (v6.0.0)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version (originally drafted as ADR-012, renumbered to ADR-022 to avoid conflict with pre-existing ADR-012: A2A Server Authentication Architecture)
