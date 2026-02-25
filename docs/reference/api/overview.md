# YAWL Engine API Documentation

**Version:** 6.0.0
**Base URL:** `http://localhost:8080/yawl`

## Overview

YAWL (Yet Another Workflow Language) provides comprehensive workflow management capabilities through four primary interfaces:

| Interface | Purpose | Base Path |
|-----------|---------|-----------|
| **Interface A** | Design-time specification management | `/ia` |
| **Interface B** | Runtime case and work item execution | `/ib` |
| **Interface E** | Event logging and process monitoring | `/ie` |
| **Interface X** | Extended exception handling operations | `/ix` |

Additional endpoints:
| Path | Purpose |
|------|---------|
| `/security` | Certificate and signature management |
| `/audit` | Receipt chain and compliance auditing |

## Authentication

All endpoints require a valid `sessionHandle` obtained through the connect endpoint:

```http
POST /ib
Content-Type: application/x-www-form-urlencoded

action=connect&userid=admin&password=YAWL
```

Response: Session handle string (expires after 1 hour of inactivity)

## Quick Start

### 1. Connect

```bash
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect" \
  -d "userid=admin" \
  -d "password=YAWL"
```

### 2. Upload Specification

```bash
curl -X POST "http://localhost:8080/yawl/ia/specifications?sessionHandle={HANDLE}" \
  -H "Content-Type: application/xml" \
  -d @specification.xml
```

### 3. Launch Case

```bash
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=launchCase" \
  -d "specuri=http://example.com/specs/approval" \
  -d "sessionHandle={HANDLE}"
```

### 4. Work with Work Items

```bash
# Get all live work items
curl "http://localhost:8080/yawl/ib?action=getLiveItems&sessionHandle={HANDLE}"

# Check out work item
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=checkout" \
  -d "workItemID={ITEM_ID}" \
  -d "sessionHandle={HANDLE}"

# Check in work item
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=checkin" \
  -d "workItemID={ITEM_ID}" \
  -d "data=<data><result>approved</result></data>" \
  -d "sessionHandle={HANDLE}"
```

## API Reference

### Session Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/ib` | Connect (action=connect) |
| GET | `/ib` | Check connection (action=checkConnection) |
| POST | `/ia` | Disconnect (action=disconnect) |

### Interface A - Specifications

| Method | Path | Description |
|--------|------|-------------|
| POST | `/ia/specifications` | Upload specification |
| GET | `/ia/specifications` | List all specifications |
| DELETE | `/ia/specifications/{specId}` | Unload specification |

### Interface B - Cases

| Method | Path | Description |
|--------|------|-------------|
| POST | `/ib` | Launch case (action=launchCase) |
| GET | `/ib` | Get all running cases (action=getAllRunningCases) |
| GET | `/ib` | Get case state (action=getCaseState) |
| GET | `/ib` | Get case data (action=getCaseData) |
| POST | `/ib` | Cancel case (action=cancelCase) |

### Interface B - Work Items

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ib` | Get all live work items (action=getLiveItems) |
| GET | `/ib` | Get work item (action=getWorkItem) |
| POST | `/ib` | Check out (action=checkout) |
| POST | `/ib` | Check in (action=checkin) |
| POST | `/ib` | Suspend (action=suspend) |
| POST | `/ib` | Unsuspend (action=unsuspend) |
| POST | `/ib` | Rollback (action=rollback) |
| POST | `/ib` | Skip (action=skip) |

### Interface X - Extended Operations

| Method | Path | Description |
|--------|------|-------------|
| POST | `/ix/workitems/{itemId}/cancel` | Cancel with exception |
| POST | `/ix/workitems/{itemId}/suspend` | Suspend work item |
| POST | `/ix/workitems/{itemId}/resume` | Resume work item |
| PUT | `/ix/workitems/{itemId}/data` | Update work item data |
| POST | `/ix/listeners` | Register listener |
| DELETE | `/ix/listeners` | Unregister listener |

### Interface E - Events

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ie/specifications` | Get logged specifications |
| GET | `/ie/specifications/{specKey}/cases` | Get cases for specification |
| GET | `/ie/listeners` | Get event listeners |

### Audit

| Method | Path | Description |
|--------|------|-------------|
| GET | `/audit/case/{caseId}/receipts` | Get receipt chain |
| GET | `/audit/case/{caseId}/state` | Get computed state |
| GET | `/audit/case/{caseId}/stats` | Get statistics |
| GET | `/audit/receipt/{receiptId}` | Get single receipt |
| GET | `/audit/verify/{caseId}` | Verify chain integrity |
| GET | `/audit/case/{caseId}/time-range` | Query by time range |
| GET | `/audit/case/{caseId}/admitted` | Get admitted transitions |
| GET | `/audit/case/{caseId}/rejected` | Get rejected transitions |

### Security

| Method | Path | Description |
|--------|------|-------------|
| GET | `/security/health` | Health check |
| GET | `/security/certificates` | List certificates |
| GET | `/security/certificates/{alias}` | Get certificate |
| POST | `/security/certificates/{alias}/sign` | Sign document |
| POST | `/security/certificates/{alias}/verify` | Verify signature |

## Data Types

### SpecificationID

```xml
<specificationid>
  <identifier>approval-workflow</identifier>
  <version>2.0</version>
  <uri>http://example.com/specs/approval</uri>
</specificationid>
```

### WorkItemRecord

```xml
<workItemRecord>
  <id>12.3.4.5:approve_request:Executing</id>
  <caseid>12.3.4.5</caseid>
  <taskid>approve_request</taskid>
  <taskname>Approve Request</taskname>
  <status>Executing</status>
  <resourceStatus>Started</resourceStatus>
  <enablementTimeMs>1708080000000</enablementTimeMs>
  <startTimeMs>1708080100000</startTimeMs>
  <data>...</data>
</workItemRecord>
```

### Work Item Statuses

- `Enabled` - Work item is available for checkout
- `Fired` - Work item has been offered to a service
- `Executing` - Work item is being processed
- `Complete` - Work item has completed
- `Suspended` - Work item is suspended
- `Failed` - Work item has failed
- `Discarded` - Work item was discarded

### Resource Statuses

- `Offered` - Offered to resource
- `Allocated` - Allocated to resource
- `Started` - Started by resource
- `Suspended` - Suspended by resource
- `Unoffered` - Not offered
- `Unresourced` - No resource assigned

## Error Handling

All error responses follow this pattern:

```xml
<failure>Error message describing the issue</failure>
```

HTTP status codes:
- `200` - Success
- `400` - Bad request (missing or invalid parameters)
- `401` - Unauthorized (invalid session handle)
- `404` - Not found
- `500` - Internal server error

## SDKs

### Java SDK

```java
YawlClient client = YawlClient.builder()
    .baseUrl("http://localhost:8080/yawl")
    .build();

String session = client.session()
    .connect("admin", "password")
    .await();

String caseId = client.cases()
    .launch("approval-workflow")
    .withUri("http://example.com/specs/approval")
    .execute(session)
    .await();
```

### Python SDK

```python
import asyncio
from yawl_client import YawlClient

async def main():
    async with YawlClient("http://localhost:8080/yawl") as client:
        session = await client.session.connect("admin", "password")
        case_id = await client.cases.launch(
            spec_id="approval-workflow",
            spec_uri="http://example.com/specs/approval",
            session_handle=session
        )

asyncio.run(main())
```

### JavaScript/TypeScript SDK

```typescript
import { YawlClient } from 'yawl-client';

const client = new YawlClient({ baseUrl: 'http://localhost:8080/yawl' });

const session = await client.session.connect('admin', 'password');
const caseId = await client.cases.launch({
  specUri: 'http://example.com/specs/approval',
  sessionHandle: session
});
```

## Files

| File | Description |
|------|-------------|
| [openapi/openapi.yaml](openapi/openapi.yaml) | OpenAPI 3.0 specification |
| [postman/yawl-api-collection.json](postman/yawl-api-collection.json) | Postman collection |
| [sdks/java/](sdks/java/) | Java SDK source files |
| [sdks/python/](sdks/python/) | Python SDK source files |
| [sdks/javascript/](sdks/javascript/) | JavaScript/TypeScript SDK |

## Related Documentation

- [YAWL Schema 4.0](../../../schema/YAWL_Schema4.0.xsd)
- [Architecture Patterns](../../.claude/ARCHITECTURE-PATTERNS-JAVA25.md)
- [Java 25 Features](../../.claude/JAVA-25-FEATURES.md)
- [Security Checklist](../../.claude/SECURITY-CHECKLIST-JAVA25.md)
