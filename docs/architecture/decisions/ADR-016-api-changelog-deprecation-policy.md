# ADR-016: API Changelog and Deprecation Policy

## Status
**ACCEPTED**

## Context

YAWL v5.x has no formal API deprecation lifecycle. Breaking changes have been introduced
without notice, causing integration failures for downstream systems. The `docs/API-REFERENCE.md`
was last updated with v5.2 content but predates several changes to Interface B.

For v6.0.0, the API surface is significantly expanded (Interface A, X, E now fully
implemented as REST endpoints). With multiple external integrators (MCP servers, A2A
agents, enterprise ERP systems), the cost of unannounced breaking changes is high.

A clear deprecation policy with versioned changelog is required to:
1. Give integrators predictable migration timelines
2. Allow the engine team to safely evolve the API
3. Maintain trust with the YAWL open-source community

## Decision

**YAWL v6.0.0 adopts a formal three-phase API deprecation lifecycle with a guaranteed
minimum 12-month notice period before any breaking change becomes effective.**

### Deprecation Lifecycle

```
Phase 1: DEPRECATED (announcement)
    → Endpoint/field still works, returns Deprecation header
    → Documented in CHANGELOG.md with migration guidance
    → Minimum 1 minor release (typically 3 months)

Phase 2: SUNSET-WARNED (approaching removal)
    → Endpoint/field still works
    → Returns both Deprecation and Sunset headers
    → Removal date is fixed and published
    → Minimum 2 minor releases from DEPRECATED

Phase 3: REMOVED
    → Endpoint removed or field absent from responses
    → Returns 410 Gone if client calls removed endpoint
    → Breaking change logged in CHANGELOG.md under major version
```

Total minimum time from deprecation announcement to removal: **12 months** for
external-facing endpoints. Internal/admin endpoints: **6 months**.

### Response Headers

Deprecated endpoints include the following HTTP response headers:

```http
Deprecation: Mon, 17 Feb 2026 00:00:00 GMT
Sunset: Mon, 17 Feb 2027 00:00:00 GMT
Link: <https://docs.yawl.org/api/migration/v6>; rel="deprecation"
```

The `Deprecation` header (RFC 8594) communicates when the deprecation was announced.
The `Sunset` header (RFC 8594) communicates when the endpoint will be removed.
The `Link` header points to migration documentation.

### OpenAPI Deprecation Markers

Deprecated endpoints and fields are marked in `openapi-v6.yaml`:

```yaml
/ib/workitems/{itemId}/start:
  post:
    deprecated: true
    x-deprecated-since: "6.1.0"
    x-sunset-version: "7.0.0"
    x-migration: "/ib/workitems/{itemId}/checkout"
    summary: "[DEPRECATED] Use /checkout instead"
    description: |
      **DEPRECATED since v6.1.0.** Use `POST /ib/workitems/{itemId}/checkout` instead.
      This endpoint will be removed in v7.0.0 (estimated 2027-Q1).
```

### Changelog Format

`docs/api/CHANGELOG.md` follows the [Keep a Changelog](https://keepachangelog.com/) format:

```markdown
## [Unreleased]

## [6.1.0] - 2026-05-17
### Added
- `GET /ia/specifications/{specId}/validate` — dry-run specification validation
- `POST /ie/subscriptions` — webhook event subscriptions

### Deprecated
- `POST /ib/workitems/{itemId}/start` — use `/checkout` instead (removal: v7.0.0)
- `sessionHandle` query parameter — moving to `Authorization: Bearer` header (removal: v7.0.0)

### Fixed
- `GET /ib/workitems` now returns `enablementTimeMs` in ISO 8601 format (was epoch millis)

## [6.0.0] - 2026-02-17
### Breaking Changes (from v5.x)
- Interface A endpoints now at `/ia/*` (was servlet-only, no REST path)
- Interface X endpoints now at `/ix/*` (was servlet-only)
- Interface E endpoints now at `/ie/*` (was servlet-only)
- `WorkItemRecord.id` is now `integer` (was `string` in v5.x JSON)
- Error responses now use `YawlApiError` schema (was plain string)
```

### Version Support Matrix

| API Version | Status | Supported Until |
|-------------|--------|----------------|
| v6.0.0      | CURRENT | EOL when v7.0.0 releases + 12 months |
| v5.2.x      | MAINTAINED | 2027-02-17 (12 months from v6 GA) |
| v5.1.x      | END OF LIFE | 2026-03-01 |
| v4.x and below | UNSUPPORTED | No longer supported |

### Breaking Change Definition

A change is **breaking** if it requires consuming code to be modified. Examples:

| Breaking | Examples |
|---------|---------|
| Yes | Remove an endpoint |
| Yes | Remove a required request field |
| Yes | Remove a response field |
| Yes | Change a response field type (string → integer) |
| Yes | Change an HTTP method or path |
| Yes | Make an optional field required |
| No | Add a new endpoint |
| No | Add a new optional request field |
| No | Add a new response field |
| No | Change HTTP status 200 → 201 for creation |
| No | Add a new error code to the error catalogue |

### Communication Channels

Breaking changes and deprecations are announced via:
1. `CHANGELOG.md` update with PR merged to `main`
2. GitHub Releases tag with release notes
3. `yawl-api-announce` mailing list (for registered API consumers)
4. `Deprecation` HTTP response header from the first release containing the deprecation

## Consequences

### Positive

1. Integrators can plan migrations with predictable timelines
2. Engine team can safely evolve the API without fear of invisible breakage
3. `Deprecation` headers enable automated tooling (API monitoring, client SDK generation)
4. Version support matrix clarifies which engine versions receive security fixes

### Negative

1. 12-month deprecation period slows the pace of API cleanup
2. Maintaining deprecated endpoints for a full year adds implementation complexity
3. CHANGELOG maintenance overhead (must be updated with every API change)

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Team forgets to add Deprecation headers | MEDIUM | MEDIUM | Spectral lint rule checks that all `deprecated: true` endpoints have `x-deprecated-since` |
| Changelog falls out of sync | MEDIUM | LOW | CI step validates CHANGELOG has entry for every version tag |
| 12-month window is too long for security-critical removals | LOW | HIGH | Security exceptions can bypass policy with Architecture Team approval and 30-day notice |

## Alternatives Considered

### 6-Month Deprecation Window
Rejected. Enterprise integrators (ERP systems, BPM platforms) typically require
6+ months for their own release cycle. A 6-month window would force them to break.

### No Formal Policy (status quo)
Rejected. The current ad-hoc approach has already caused integration failures and
erosion of community trust. A documented policy, even if imperfectly followed,
is strictly better.

### Semantic Versioning for the Entire Engine
The engine already uses semantic versioning. This ADR extends the same principle to
the API surface with explicit consumer-facing documentation.

## Related ADRs

- ADR-022: OpenAPI-First Design (machine-readable deprecation markers)
- ADR-013: Schema Versioning Strategy (parallel approach for XSD schema)

## Implementation Notes

### Jersey Filter for Deprecation Headers

```java
// org.yawlfoundation.yawl.rest.filter.DeprecationHeaderFilter
@Provider
public class DeprecationHeaderFilter implements ContainerResponseFilter {

    private static final Map<String, DeprecationInfo> DEPRECATED_PATHS = Map.of(
        "/ib/workitems/{itemId}/start",
        new DeprecationInfo("2026-05-17", "2027-05-17",
            "https://docs.yawl.org/api/migration/checkout")
    );

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext resp) {
        String path = req.getUriInfo().getPath();
        DEPRECATED_PATHS.entrySet().stream()
            .filter(e -> pathMatches(path, e.getKey()))
            .findFirst()
            .ifPresent(e -> {
                DeprecationInfo info = e.getValue();
                resp.getHeaders().add("Deprecation", info.since());
                resp.getHeaders().add("Sunset", info.sunset());
                resp.getHeaders().add("Link",
                    String.format("<%s>; rel=\"deprecation\"", info.migrationUrl()));
            });
    }
}
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** ACCEPTED — applies from v6.0.0 GA
**Review Date:** 2027-02-17 (annual review)

---

**Revision History:**
- 2026-02-17: Initial version
