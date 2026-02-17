# YAWL REST API Changelog

All notable changes to the YAWL REST API are documented here.
This file follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the deprecation policy defined in [ADR-016](../architecture/decisions/ADR-016-api-changelog-deprecation-policy.md).

**Deprecation Notice Period**: 12 months minimum before removal of any external-facing endpoint.
**Breaking Change Header**: All breaking changes are marked with **[BREAKING]**.

---

## [Unreleased]

### Planned for v6.1.0
- Bearer token authentication (`Authorization: Bearer {jwt}`) as alternative to `?sessionHandle`
- `GET /admin/patterns` — pattern library search API
- `GET /admin/patterns/{id}/template` — download pattern template
- AsyncAPI 3.0 specification for Interface E events
- `POST /ia/specifications/{specId}/validate` — dry-run validation endpoint

---

## [6.0.0] - 2026-02-17

YAWL v6.0.0 is a major release. Interface A, X, and E are now fully implemented as
REST endpoints alongside the Interface B endpoints available since v5.2.

### Added

**Interface A — Specification Management (new in v6.0.0)**
- `GET /ia/specifications` — list all loaded specifications
- `POST /ia/specifications` — upload and load a specification
- `GET /ia/specifications/{specId}` — get specification metadata
- `DELETE /ia/specifications/{specId}` — unload a specification

**Interface B — Work Item Operations (new endpoints)**
- `POST /ib/workitems/{itemId}/suspend` — suspend an executing work item
- `POST /ib/workitems/{itemId}/resume` — resume a suspended work item
- `GET /ib/session/check` — validate session handle and retrieve session metadata
- `POST /ib/cases` — launch a new case instance (was previously undocumented)
- `GET /ib/cases/{caseId}` — get case data and status
- `POST /ib/cases/{caseId}/cancel` — cancel a running case

**Interface X — Extended Operations (new in v6.0.0)**
- `POST /ix/workitems/{itemId}/exception` — raise or handle a work item exception
- `POST /ix/cases/{caseId}/compensate` — trigger compensating transaction

**Interface E — Event Subscriptions (new in v6.0.0)**
- `GET /ie/subscriptions` — list active subscriptions
- `POST /ie/subscriptions` — subscribe to workflow events via webhook
- `DELETE /ie/subscriptions/{subscriptionId}` — unsubscribe

**Admin Endpoints (new in v6.0.0)**
- `GET /admin/health` — engine health check (no authentication required)
- `GET /admin/metrics` — Prometheus metrics endpoint

**Response Enhancements**
- All responses now include `X-YAWL-API-Version` header
- `CaseLaunchResponse` includes `engineUsed` field indicating stateful/stateless selection
- `WorkItemRecord` includes new `timerTrigger` and `timerExpiry` fields

### Changed

**[BREAKING]** `WorkItemRecord.id` type changed from `string` to `integer` (int64).
In v5.x, `id` was serialised as a string representation of the Hibernate long primary key.
v6.0 serialises it as a JSON number. Update client parsing code accordingly.

**[BREAKING]** Error responses changed from plain `string` body to `YawlApiError` JSON object.
```
# v5.x response body (plain string)
"Failed to checkout work item"

# v6.0 response body (YawlApiError)
{"error": "Failed to checkout work item", "status": 400, "code": "YAWL-E-400"}
```

**[BREAKING]** `enablementTimeMs`, `startTimeMs`, `completionTimeMs` fields in `WorkItemRecord`
changed from epoch millisecond strings (e.g., `"1739800000000"`) to ISO 8601 datetime strings
(e.g., `"2026-02-17T09:00:00Z"`). Clients using epoch numeric parsing must update to ISO 8601.

**[BREAKING]** `POST /ib/connect` no longer accepts credentials as query parameters.
Credentials must be in the JSON request body. Query parameter form (`?userid=&password=`)
returns `400 Bad Request`.

**Session handle format changed**: Session handles are now JWT tokens
(`xxxxx.yyyyy.zzzzz` format) rather than opaque base64 blobs. The parameter name
`sessionHandle` is unchanged. v5.x session handles are not valid in v6.0 — clients
must re-authenticate after upgrade.

### Deprecated (removal in v7.0.0)

The following are deprecated since v6.0.0 and will be removed in v7.0.0.
Deprecated endpoints return `Deprecation` and `Sunset` HTTP headers.

| Deprecated Endpoint/Field | Replacement | Sunset Date |
|--------------------------|-------------|-------------|
| `POST /ib/workitems/{id}/start` | `POST /ib/workitems/{id}/checkout` | v7.0.0 (est. 2027-Q1) |
| `WorkItemRecord.specURI` field | `WorkItemRecord.specIdentifier` | v7.0.0 |
| `allowsDynamicCreation` string field | `allowsDynamicCreation` boolean field | v7.0.0 |
| `requiresManualResourcing` string field | `requiresManualResourcing` boolean field | v7.0.0 |

### Removed

The following endpoints were removed in v6.0.0. They were not present in the v5.2
REST API (they were servlet-only); they are listed here for completeness.

- Legacy `InterfaceA_EngineBasedServer` servlet paths — replaced by `/ia/*` REST paths
- Legacy `InterfaceX_EngineSideServer` servlet paths — replaced by `/ix/*` REST paths
- `YLogGateway` servlet paths — replaced by `/ie/*` REST paths

### Security

- Session handles are now HMAC-SHA256 signed JWTs (ADR-017)
- All endpoints enforce role-based access control (`workflow-user`, `spec-designer`, `engine-admin`)
- Passwords rejected from query parameters (were logged in v5.x access logs)

---

## [5.2.0] - 2026-02-15

Last v5.x release. Establishes the v5.x baseline for migration purposes.

### Available Endpoints (v5.2 baseline)

**Interface B — Fully Implemented**
- `POST /ib/connect`
- `POST /ib/disconnect`
- `GET /ib/workitems`
- `GET /ib/workitems/{itemId}`
- `POST /ib/workitems/{itemId}/checkout`
- `POST /ib/workitems/{itemId}/checkin`
- `POST /ib/workitems/{itemId}/complete`
- `GET /ib/cases/{caseId}/workitems`
- `GET /ib/cases/{caseId}`
- `POST /ib/cases/{caseId}/cancel`

**Interface A, X, E — Not Implemented as REST**
These interfaces were only available via servlet paths in v5.2.
They are fully implemented as REST endpoints in v6.0.0.

---

## Migration Guides

| Migration | Guide |
|-----------|-------|
| v5.x → v6.0 | [MIGRATION-5x-to-6.md](MIGRATION-5x-to-6.md) |
| v4.x → v5.x | [MIGRATION-v6.md](../MIGRATION-v6.md) (historical) |

---

## API Version Support Matrix

| API Version | Engine Version | Status | Supported Until |
|-------------|----------------|--------|----------------|
| v6.0 | 6.0.0+ | CURRENT | v7.0 GA + 12 months |
| v5.2 | 5.2.x | MAINTAINED | 2027-02-17 |
| v5.1 | 5.1.x | END OF LIFE | 2026-03-01 |
| v4.x | 4.x | UNSUPPORTED | No longer supported |

---

**Changelog Version**: 6.0.0
**Last Updated**: 2026-02-17
**Policy Reference**: [ADR-016](../architecture/decisions/ADR-016-api-changelog-deprecation-policy.md)
