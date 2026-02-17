# YAWL API Migration Guide: v5.x to v6.0.0

**Target Audience**: Integration developers, API consumers, workflow tool builders.

This guide covers the REST API changes introduced in v6.0.0. For engine-level changes
(Jakarta EE migration, Hibernate 6, Maven build), see [MIGRATION-v6.md](../MIGRATION-v6.md).

---

## Overview of Breaking Changes

v6.0.0 introduces five categories of breaking API change:

| Category | Breaking | Impact | Section |
|----------|---------|--------|---------|
| Work item ID type (`string` → `integer`) | Yes | All clients | [1](#1-work-item-id-type-change) |
| Error response format (string → JSON) | Yes | All clients | [2](#2-error-response-format-change) |
| Timestamp format (epoch ms → ISO 8601) | Yes | Clients parsing timestamps | [3](#3-timestamp-format-change) |
| Session handle format (opaque → JWT) | Yes | All clients (one-time re-auth) | [4](#4-session-handle-format-change) |
| Connect credentials (query → body) | Yes | Clients using `?userid=` | [5](#5-connect-credentials-moved-to-body) |

---

## 1. Work Item ID Type Change

### What Changed

`WorkItemRecord.id` was a `string` in v5.x responses. It is now an `integer` (int64 JSON number).

### v5.x Response

```json
{
  "id": "12345",
  "specIdentifier": "ProcurementProcess",
  "status": "Executing"
}
```

### v6.0 Response

```json
{
  "id": 12345,
  "specIdentifier": "ProcurementProcess",
  "status": "Executing"
}
```

### Migration

| Language | v5.x Code | v6.0 Code |
|----------|-----------|-----------|
| Java | `String id = item.getId()` | `long id = item.getId()` |
| TypeScript | `item.id: string` | `item.id: number` |
| Python | `item['id']` (string) | `item['id']` (int) |
| Go | `Id string` | `Id int64` |

**Java migration example:**

```java
// v5.x — parsed as string
WorkItemRecord item = ...;
String itemId = item.getId();  // was String
String url = "/ib/workitems/" + itemId + "/checkout";

// v6.0 — now a long
long itemId = item.getId();  // now long
String url = "/ib/workitems/" + itemId + "/checkout";
// or use the generated client SDK: yawl-api-client-6.0
```

The `itemId` path parameter in URLs (`/ib/workitems/{itemId}`) still accepts both
numeric strings and integers — no URL construction changes are required.

---

## 2. Error Response Format Change

### What Changed

Error responses were plain strings in v5.x. v6.0 uses the `YawlApiError` JSON schema.

### v5.x Error Response Body

```
HTTP 400 Bad Request
Content-Type: text/plain

Failed to checkout work item
```

### v6.0 Error Response Body

```json
HTTP 400 Bad Request
Content-Type: application/json

{
  "error": "Failed to checkout work item ITEM-10042: item is already checked out by user jane.smith",
  "status": 400,
  "code": "YAWL-E-400",
  "requestId": "a3f7-bee2-4109"
}
```

### Migration

**Before (v5.x — reading plain string):**
```java
Response response = target.request().post(entity);
if (response.getStatus() != 200) {
    String errorMsg = response.readEntity(String.class);
    throw new WorkflowException(errorMsg);
}
```

**After (v6.0 — reading YawlApiError):**
```java
Response response = target.request().post(entity);
if (response.getStatus() != 200) {
    YawlApiError error = response.readEntity(YawlApiError.class);
    throw new WorkflowException(error.getError());
    // error.getCode() for programmatic handling
    // error.getRequestId() for log correlation
}
```

**Python migration:**
```python
# v5.x
if response.status_code != 200:
    raise WorkflowError(response.text)

# v6.0
if response.status_code != 200:
    error = response.json()
    raise WorkflowError(error['error'], code=error.get('code'))
```

---

## 3. Timestamp Format Change

### What Changed

Timestamp fields (`enablementTimeMs`, `startTimeMs`, `completionTimeMs`) in `WorkItemRecord`
were epoch millisecond strings in v5.x. v6.0 uses ISO 8601 UTC datetime strings.

### v5.x Timestamps

```json
{
  "enablementTimeMs": "1739800000000",
  "startTimeMs": "1739800300000",
  "completionTimeMs": null
}
```

### v6.0 Timestamps

```json
{
  "enablementTimeMs": "2026-02-17T09:00:00Z",
  "startTimeMs": "2026-02-17T09:05:00Z",
  "completionTimeMs": null
}
```

### Migration

**Java:**
```java
// v5.x
long epochMs = Long.parseLong(item.getEnablementTimeMs());
Instant enabled = Instant.ofEpochMilli(epochMs);

// v6.0
Instant enabled = Instant.parse(item.getEnablementTimeMs());
```

**JavaScript/TypeScript:**
```javascript
// v5.x
const enabled = new Date(parseInt(item.enablementTimeMs));

// v6.0
const enabled = new Date(item.enablementTimeMs);  // ISO 8601 is directly parseable
```

**Python:**
```python
# v5.x
from datetime import datetime
enabled = datetime.fromtimestamp(int(item['enablementTimeMs']) / 1000)

# v6.0
from datetime import datetime, timezone
enabled = datetime.fromisoformat(item['enablementTimeMs'].replace('Z', '+00:00'))
```

---

## 4. Session Handle Format Change

### What Changed

v5.x session handles were opaque base64-encoded blobs managed by the engine's in-memory
session store. v6.0 session handles are HMAC-SHA256 signed JWTs.

The parameter name (`sessionHandle`) is unchanged. Only the value format changes.

### Impact

**v5.x session handles are not valid in v6.0.** After upgrading the engine to v6.0.0,
all clients must re-authenticate via `POST /ib/connect` to obtain a new JWT-format handle.

There is no cross-version session compatibility. This is a one-time migration cost.

### Detection

If a v5.x client sends a v5.x opaque handle to a v6.0 engine:

```json
HTTP 401 Unauthorized
{
  "error": "Session handle format is not valid for YAWL v6.0. Re-authenticate via POST /ib/connect.",
  "status": 401,
  "code": "YAWL-E-AUTH-001"
}
```

### Migration Strategy

1. **On engine upgrade day**: all clients receive `401` on first request (expected)
2. **Client handles `401`**: automatically calls `POST /ib/connect` to get a new handle
3. **Client retries original request**: with the new JWT handle

**Recommended client pattern (any language):**

```
function callWithRetry(request):
    response = callApi(request)
    if response.status == 401 and "YAWL-E-AUTH-001" in response.body:
        sessionHandle = reconnect()
        response = callApi(request.withHandle(sessionHandle))
    return response
```

### JWT Format (informational)

v6.0 session handles are standard JWTs (`header.payload.signature`). Clients should
treat them as opaque strings and not attempt to parse the payload — the contents
are an implementation detail that may change in minor versions.

---

## 5. Connect Credentials Moved to Body

### What Changed

In v5.x, `POST /ib/connect` accepted credentials as either request body JSON or as
query parameters (`?userid=admin&password=YAWL`). The query parameter form was
deprecated as it causes credentials to appear in server access logs.

v6.0 **rejects** query parameter credentials with `400 Bad Request`.

### v5.x (still works but was unsafe)

```bash
# This form is REJECTED in v6.0
curl -X POST "http://localhost:8080/yawl/api/ib/connect?userid=admin&password=YAWL"
```

### v6.0 (required form)

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/connect" \
  -H "Content-Type: application/json" \
  -d '{"userid": "admin", "password": "YAWL"}'
```

### Migration

Update all authentication calls to use the JSON body form. Check load balancer and
API gateway access logs for any credentials that may have been captured in query strings
before upgrading — rotate affected passwords.

---

## New Features Available in v6.0

After migrating the breaking changes above, consider adopting these new capabilities:

### Interface A: Specification Management via REST

Specifications can now be uploaded, validated, and unloaded via REST. Previously this
required direct servlet calls or the YAWL Editor GUI.

```bash
# Upload a specification
curl -X POST "http://localhost:8080/yawl/api/ia/specifications?sessionHandle=$SESSION" \
  -H "Content-Type: application/xml" \
  --data-binary @my-workflow.yawl

# List loaded specifications
curl "http://localhost:8080/yawl/api/ia/specifications?sessionHandle=$SESSION"
```

### Interface E: Webhook Event Subscriptions

Instead of polling for work items, register a webhook to receive real-time events:

```bash
curl -X POST "http://localhost:8080/yawl/api/ie/subscriptions?sessionHandle=$SESSION" \
  -H "Content-Type: application/json" \
  -d '{
    "callbackUrl": "https://my-service.example.com/yawl-events",
    "eventTypes": ["workItemEnabled", "caseCompleted"],
    "specIdentifier": "ProcurementProcess",
    "secret": "my-webhook-signing-secret"
  }'
```

### Work Item Suspension

Work items can now be suspended and resumed:

```bash
# Suspend
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/12345/suspend?sessionHandle=$SESSION"

# Resume
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/12345/resume?sessionHandle=$SESSION"
```

---

## Migration Checklist

Use this checklist when upgrading from v5.x to v6.0:

### Code Changes

- [ ] Update work item ID parsing: `string` → `integer`/`long`
- [ ] Update error handling: read `YawlApiError` JSON instead of plain string
- [ ] Update timestamp parsing: epoch ms strings → ISO 8601 strings
- [ ] Update `POST /ib/connect`: credentials must be in JSON body (not query params)
- [ ] Add `401` re-authentication retry logic for the one-time session format migration

### Testing

- [ ] Test authentication flow with new JWT session handles
- [ ] Test error handling code paths with new `YawlApiError` format
- [ ] Verify timestamp parsing in date display / business logic
- [ ] Test work item ID handling in all URL construction code

### Optional Improvements

- [ ] Adopt Interface A REST endpoints for specification management
- [ ] Register event webhooks instead of polling `/ib/workitems`
- [ ] Use new `suspend`/`resume` endpoints for long-running human tasks
- [ ] Update health monitoring to use `GET /admin/health`

---

## v5.x Endpoint Availability in v6.0

| v5.x Endpoint | v6.0 Status | Notes |
|---------------|------------|-------|
| `POST /ib/connect` | Available (body changes) | Credentials must be in body |
| `POST /ib/disconnect` | Available | |
| `GET /ib/workitems` | Available | `id` field type changed |
| `GET /ib/workitems/{id}` | Available | |
| `POST /ib/workitems/{id}/checkout` | Available | |
| `POST /ib/workitems/{id}/checkin` | Available | |
| `POST /ib/workitems/{id}/complete` | Available | |
| `GET /ib/cases/{id}/workitems` | Available | |
| `GET /ib/cases/{id}` | Available | |
| `POST /ib/cases/{id}/cancel` | Available | |
| `POST /ib/workitems/{id}/start` | **DEPRECATED** | Use `/checkout`; removed in v7.0 |

---

## Getting Help

- **API Reference**: [openapi-v6.yaml](openapi-v6.yaml) (machine-readable)
- **Interactive Docs**: `http://localhost:8080/yawl/api/ui` (after deploying v6.0)
- **Engine Migration Guide**: [../MIGRATION-v6.md](../MIGRATION-v6.md)
- **GitHub Issues**: https://github.com/yawlfoundation/yawl/issues
- **Community Forum**: https://yawlfoundation.github.io/community

---

**Document Version**: 6.0.0
**Last Updated**: 2026-02-17
**Applies To**: YAWL Engine 5.x → 6.0.0 migration
