# ADR-013: YAWL Schema Versioning Strategy for v6.0.0

## Status
**ACCEPTED**

## Context

YAWL specifications are XML documents validated against `schema/YAWL_Schema4.0.xsd`.
As the engine evolves, the schema must accommodate:

1. **New language features**: autonomous agent annotations, timer improvements, OAuth resource attributes
2. **Deprecations**: legacy `<decomposition>` attributes, SOAP codelet references
3. **Compatibility**: specifications authored for YAWL 4.x and 5.x must continue to execute

The current approach has no formal versioning contract:
- The XSD file is named `YAWL_Schema4.0.xsd` but has been modified post-4.0
- Specifications embed a `version` attribute but the engine ignores minor/patch components
- There is no documented policy for what changes are backward-compatible

For v6.0.0, the schema gains three new feature areas:
1. **Agent Task Annotations** (`<yawl:agentBinding>`) for autonomous agent assignment
2. **OAuth 2.0 Resource Attributes** for service task authentication
3. **Structured Timer Expressions** (ISO 8601 durations replacing opaque string timers)

Each of these must be introduced without breaking existing specifications.

## Decision

**YAWL v6.0.0 adopts semantic versioning for specification schemas with a strict
backward-compatibility guarantee for minor versions.**

### Schema Versioning Rules

| Change Type | Schema Version Bump | Examples |
|-------------|---------------------|---------|
| New optional elements/attributes | MINOR | New `<agentBinding>` in task |
| New required elements | MAJOR | Mandatory `<version>` in net |
| Renamed elements | MAJOR | `<decomposition>` → `<subNet>` |
| Removed elements | MAJOR | Removal of SOAP codelet support |
| Type restriction tightened | MAJOR | Timer format stricter |
| Type restriction relaxed | MINOR | Timer accepts additional formats |
| Documentation only | PATCH | XSD annotation updates |

### File Naming Convention

```
schema/
  YAWL_Schema6.0.xsd     ← primary schema for v6 (canonical)
  YAWL_Schema5.x.xsd     ← v5 schema retained for compatibility validation
  YAWL_Schema4.0.xsd     ← v4 schema retained read-only
  compat/
    v4-to-v5.xsl          ← XSLT transform: upgrade v4 specs to v5
    v5-to-v6.xsl          ← XSLT transform: upgrade v5 specs to v6
```

### Specification `schemaVersion` Attribute

Every specification document must declare its schema version in the root element:

```xml
<specificationSet xmlns="http://www.yawl-system.com/schema/YAWL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawl-system.com/schema/YAWL
                                      schema/YAWL_Schema6.0.xsd"
                  version="2.1"
                  schemaVersion="6.0">
```

The engine's schema selection logic:

```
schemaVersion < 5.0  → attempt upgrade via v4-to-v5.xsl, then v5-to-v6.xsl
schemaVersion = 5.x  → attempt upgrade via v5-to-v6.xsl
schemaVersion = 6.0  → validate against YAWL_Schema6.0.xsd directly
schemaVersion > 6.x  → reject with clear error (future version)
```

### v6.0 New Schema Elements

#### Agent Task Binding (new optional element)

```xml
<task id="ReviewDocument">
  <name>Expert Document Review</name>
  <!-- new in v6.0 — omitted for non-agent tasks -->
  <agentBinding xmlns="http://www.yawl-system.com/schema/YAWL/v6">
    <agentType>autonomous</agentType>
    <capabilityRequired>document-analysis</capabilityRequired>
    <fallbackToHuman>true</fallbackToHuman>
    <maxRetries>3</maxRetries>
  </agentBinding>
  <!-- ... existing elements ... -->
</task>
```

#### OAuth 2.0 Service Credentials (new optional element on webServiceGateway)

```xml
<webServiceGateway id="InvoiceServiceGateway">
  <authentication type="oauth2">
    <tokenEndpoint>https://auth.example.com/token</tokenEndpoint>
    <clientId>yawl-workflow-engine</clientId>
    <scope>invoice:read invoice:write</scope>
    <!-- client secret resolved from environment variable -->
    <clientSecretRef>INVOICE_SERVICE_CLIENT_SECRET</clientSecretRef>
  </authentication>
</webServiceGateway>
```

#### Structured Timer (backward-compatible extension)

```xml
<!-- existing format (still valid) -->
<timer trigger="OnEnabled" expiry="PT1H"/>

<!-- new structured form (v6.0) -->
<timer trigger="OnEnabled">
  <duration>PT1H</duration>
  <onExpiry>escalate</onExpiry>
  <escalationTask>EscalateToManager</escalationTask>
</timer>
```

### Upgrade Tool

A CLI upgrade tool is provided:

```bash
# Upgrade a v5 specification to v6
java -jar yawl-tools.jar upgrade-spec \
  --input procurement-v5.yawl \
  --output procurement-v6.yawl \
  --source-schema 5.2 \
  --target-schema 6.0

# Validate against schema
xmllint --schema schema/YAWL_Schema6.0.xsd procurement-v6.yawl
```

## Consequences

### Positive

1. Clear contract: integrators know exactly what schema version a specification requires
2. Upgrade path is automated for v4 and v5 specifications
3. New v6 features (agents, OAuth) are additive — zero impact on existing specs
4. Schema version in specification enables forward-compatible engine behaviour

### Negative

1. Two XSD files to maintain during transition (v5 and v6)
2. XSLT upgrade transforms require maintenance alongside schema evolution
3. Schema version attribute (`schemaVersion`) is new — v4/v5 specs that omit it must
   be handled by the engine's defaulting logic

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| XSLT transform produces invalid v6 spec | MEDIUM | HIGH | Automated test corpus of 50+ spec upgrade round-trips |
| Schema drift between XSD and engine validation | LOW | HIGH | CI validates all test specs against XSD |
| Third-party authoring tools break on new elements | MEDIUM | MEDIUM | New elements are optional; existing specs unaffected |

## Alternatives Considered

### JSON Schema for Specifications
Rejected. YAWL specifications are inherently XML (Petri net + YAWL extension elements).
XML Schema with namespacing provides the appropriate extension mechanism. Converting to
JSON would be a v7.0 consideration requiring full community consultation.

### Schema Registry (Confluent-style)
Considered for managing schema evolution at runtime. Rejected as over-engineered for the
current scale; file-based schema selection is sufficient. Revisit if YAWL moves to
event-driven architecture (see ADR-015).

### Single Schema with `xs:any` Extensions
Rejected. Using `xs:any` for new elements undermines the validation guarantees that make
YAWL specifications reliable. Explicit new elements in a versioned schema are preferable.

## Related ADRs

- ADR-001: Dual Engine Architecture (both engines use the same schema)
- ADR-011: Jakarta EE Migration (namespace changes affect schema targeting)
- ADR-022: OpenAPI-First Design (API schemas for REST, YAWL Schema for specifications)
- ADR-019: Agent Framework Architecture (defines `<agentBinding>` semantics)

## Implementation Notes

### XSD Extension Pattern

New elements are added in a child namespace to avoid collisions with the base YAWL namespace:

```xml
<xs:schema
  targetNamespace="http://www.yawl-system.com/schema/YAWL"
  xmlns:yawl6="http://www.yawl-system.com/schema/YAWL/v6"
  elementFormDefault="qualified">

  <!-- v6 extensions imported from separate namespace -->
  <xs:import namespace="http://www.yawl-system.com/schema/YAWL/v6"
             schemaLocation="YAWL_Schema6_extensions.xsd"/>
</xs:schema>
```

### Engine Schema Selection

```java
// org.yawlfoundation.yawl.schema.YawlSchemaSelector
public Path selectSchema(String schemaVersion) {
    YSpecVersion version = YSpecVersion.parse(schemaVersion);
    if (version.getMajor() >= 6) {
        return Path.of("schema/YAWL_Schema6.0.xsd");
    } else if (version.getMajor() == 5) {
        return Path.of("schema/YAWL_Schema5.x.xsd");
    } else if (version.getMajor() == 4) {
        return Path.of("schema/YAWL_Schema4.0.xsd");
    }
    throw new UnsupportedOperationException(
        "Schema version " + schemaVersion + " is not supported by this engine version"
    );
}
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** IN PROGRESS (v6.0.0)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version
