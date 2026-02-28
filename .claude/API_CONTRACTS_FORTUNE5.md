# Fortune 5 Enterprise Integration API Contracts

**Date**: 2026-02-28
**Version**: 1.0.0
**Status**: Production Specification
**Scope**: REST/GraphQL API contracts for YAWL ↔ external systems

---

## TABLE OF CONTENTS

1. [Overview](#overview)
2. [Portfolio Query API](#portfolio-query-api)
3. [ART Status API (Real-Time)](#art-status-api-real-time)
4. [Work Item Completion API](#work-item-completion-api)
5. [Dependency Management API](#dependency-management-api)
6. [Metrics Streaming API](#metrics-streaming-api)
7. [Financial Posting API](#financial-posting-api)
8. [Incident Correlation API](#incident-correlation-api)
9. [Error Handling](#error-handling)
10. [Rate Limiting & Retry Policy](#rate-limiting--retry-policy)

---

## OVERVIEW

All APIs follow **REST principles** with JSON request/response bodies. Authentication uses **Bearer tokens** from environment-based session handles.

**Base URL**: `https://yawl.company.com/api/v1`

**Global Headers**:

```http
Authorization: Bearer {sessionHandle}
Accept: application/json
Content-Type: application/json
X-Request-ID: {uuid}  # For tracing
X-Correlation-ID: {uuid}  # Cross-system tracing
```

**Session Management**:

- Sessions obtained via YAWL A2A protocol (Agent-to-Agent)
- Session handles expire after 24 hours or on logout
- Virtual threads inherit session context automatically

---

## PORTFOLIO QUERY API

**Purpose**: Retrieve all cases for a given ART with real-time status, earned value, and risk flags.

**Endpoint**: `GET /portfolio/cases`

### Request

```http
GET /api/v1/portfolio/cases?artId=ART-ABC&status=in_progress&includeRisks=true
Host: yawl.company.com
Authorization: Bearer session-abc123
Accept: application/json
X-Request-ID: req-12345
```

**Query Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `artId` | string | Yes | ART identifier (e.g., "ART-ABC") |
| `status` | string | No | Filter by case status (pending, in_progress, completed, failed) |
| `includeRisks` | boolean | No | Include risk flags (default: false) |
| `limit` | integer | No | Max results (default: 100, max: 1000) |
| `offset` | integer | No | Pagination offset (default: 0) |

### Response (200 OK)

```json
{
  "artId": "ART-ABC",
  "artName": "Predictability Engine",
  "cases": [
    {
      "caseId": "case-12345",
      "title": "Implement real-time fraud detection",
      "status": "in_progress",
      "priority": "critical",
      "jiraEpicId": "PROJ-1234",
      "storyPoints": 34,
      "assignedTo": "engineering.team@company.com",
      "startDate": "2026-02-18",
      "estimatedCompletionDate": "2026-03-18",
      "actualCompletionDate": null,
      "workItems": [
        {
          "workItemId": "wi-456",
          "title": "Design data flow",
          "status": "completed",
          "completedDate": "2026-02-25",
          "assignee": "john.doe@company.com",
          "estimatedDuration": "3 days",
          "actualDuration": "2.5 days"
        },
        {
          "workItemId": "wi-457",
          "title": "Implement backend service",
          "status": "in_progress",
          "completedDate": null,
          "assignee": "jane.smith@company.com",
          "estimatedDuration": "10 days",
          "actualDuration": "2 days elapsed"
        }
      ],
      "linkedIncidents": [
        {
          "incidentId": "INC-98765",
          "severity": "critical",
          "relatedCaseIds": ["case-12345"],
          "createdDate": "2026-02-27"
        }
      ],
      "riskFlags": [
        "external_dependency_blocked",
        "team_capacity_constraint"
      ],
      "financialMetrics": {
        "plannedValue": 34000,
        "earnedValue": 12000,
        "actualCost": 10500,
        "costPerformanceIndex": 1.14,
        "schedulePerformanceIndex": 0.65
      },
      "lastUpdated": "2026-02-28T14:35:00Z"
    }
  ],
  "summary": {
    "artId": "ART-ABC",
    "totalCases": 8,
    "inProgressCount": 3,
    "completedCount": 5,
    "failedCount": 0,
    "earnedValueSum": 125000,
    "plannedValueSum": 184000,
    "actualCostSum": 118750,
    "portfolioCpiSum": 1.05,
    "portfolioDuration": "43 days"
  },
  "pagination": {
    "limit": 100,
    "offset": 0,
    "totalCount": 8
  },
  "timestamp": "2026-02-28T14:35:00Z"
}
```

### Error Responses

**400 Bad Request** — Invalid ART ID or query parameters

```json
{
  "error": "INVALID_ART_ID",
  "message": "ART ID 'ART-INVALID' does not exist in portfolio",
  "timestamp": "2026-02-28T14:35:01Z",
  "requestId": "req-12345"
}
```

**401 Unauthorized** — Session expired

```json
{
  "error": "UNAUTHORIZED",
  "message": "Session token expired. Please re-authenticate.",
  "timestamp": "2026-02-28T14:35:01Z"
}
```

**503 Service Unavailable** — External system down

```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Jira Align service is currently unavailable. Retry in 60 seconds.",
  "retryAfter": 60,
  "timestamp": "2026-02-28T14:35:01Z"
}
```

---

## ART STATUS API (REAL-TIME)

**Purpose**: Get live ART health metrics with WebSocket support for streaming updates.

**Endpoint**: `GET /art/{artId}/health`

### Request (HTTP)

```http
GET /api/v1/art/ART-ABC/health?includeRisks=true&format=json
Host: yawl.company.com
Authorization: Bearer session-abc123
Accept: application/json
X-Request-ID: req-12345
```

### Response (200 OK)

```json
{
  "artId": "ART-ABC",
  "artName": "Predictability Engine",
  "healthScore": 8.4,
  "healthStatus": "healthy",
  "healthTrend": "stable",
  "metrics": {
    "velocity": {
      "current": 43,
      "lastSprint": 40,
      "lastSixSprints": [38, 41, 39, 42, 40, 43],
      "trend": "stable",
      "forecastedNext": 42
    },
    "wipCount": {
      "current": 6,
      "limit": 10,
      "trend": "stable"
    },
    "cycleTime": {
      "mean": 4.2,
      "median": 4.0,
      "p95": 7.1,
      "trend": "improving"
    },
    "teamCapacity": {
      "allocatedPercentage": 92.5,
      "availableHours": 12,
      "totalHours": 160
    },
    "deliveryPredictability": {
      "lastFourSprints": 0.95,
      "trend": "improving"
    }
  },
  "risks": [
    {
      "riskId": "risk-001",
      "title": "External API dependency delay",
      "severity": "critical",
      "affectedCases": ["case-12345"],
      "affectedPoints": 34,
      "discoveredDate": "2026-02-27",
      "targetResolutionDate": "2026-03-10",
      "recommendedAction": "Escalate to platform team for priority override",
      "status": "open"
    },
    {
      "riskId": "risk-002",
      "title": "Team member absence next week",
      "severity": "high",
      "affectedCases": ["case-12346", "case-12347"],
      "affectedPoints": 21,
      "discoveredDate": "2026-02-20",
      "targetResolutionDate": "2026-03-07",
      "recommendedAction": "Redistribute work or plan buffer sprint",
      "status": "mitigating"
    }
  ],
  "lastSync": "2026-02-28T14:35:00Z",
  "nextSync": "2026-02-28T14:36:00Z"
}
```

### WebSocket Upgrade (Streaming)

**Request**:

```http
GET /api/v1/art/ART-ABC/health/stream
Host: yawl.company.com
Authorization: Bearer session-abc123
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==
Sec-WebSocket-Version: 13
```

**Response** (101 Switching Protocols):

```
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: HSmrc0sMlYUkAGmm5OPpG2HaGWk=
```

**Stream Messages** (every 10s or on state change):

```json
{
  "type": "health_update",
  "artId": "ART-ABC",
  "healthScore": 8.4,
  "healthStatus": "healthy",
  "wipCount": 6,
  "velocity": 43,
  "affectedCases": [],
  "newRisks": [],
  "resolvedRisks": [],
  "timestamp": "2026-02-28T14:35:10Z"
}
```

```json
{
  "type": "risk_escalated",
  "artId": "ART-ABC",
  "riskId": "risk-003",
  "title": "Critical incident in production",
  "severity": "critical",
  "affectedCases": ["case-12345"],
  "affectedPoints": 34,
  "timestamp": "2026-02-28T14:36:45Z"
}
```

---

## WORK ITEM COMPLETION API

**Purpose**: Mark a work item as complete and trigger downstream YAWL transitions.

**Endpoint**: `POST /work-items/{workItemId}/complete`

### Request

```http
POST /api/v1/work-items/wi-456/complete
Host: yawl.company.com
Authorization: Bearer session-abc123
Content-Type: application/json
X-Request-ID: req-12345

{
  "caseId": "case-12345",
  "workItemId": "wi-456",
  "status": "completed",
  "outputData": {
    "reviewApprovedBy": "alice.smith@company.com",
    "commitsIncluded": [
      "abc123def456",
      "xyz789uvw012"
    ],
    "coverageImproved": true,
    "codeReviewComments": "LGTM - well-tested implementation"
  },
  "completionTimestamp": "2026-02-28T14:35:00Z",
  "estimatedDuration": "4 days",
  "actualDuration": "3.5 days"
}
```

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `caseId` | string | Yes | YAWL case ID |
| `workItemId` | string | Yes | YAWL work item ID |
| `status` | string | Yes | Completion status (completed, skipped, delegated) |
| `outputData` | object | No | Task-specific output data (preserved for audit) |
| `completionTimestamp` | ISO8601 | No | Timestamp of completion (server time used if omitted) |
| `estimatedDuration` | string | No | Original estimate (e.g., "4 days") |
| `actualDuration` | string | No | Actual duration (e.g., "3.5 days") |

### Response (200 OK)

```json
{
  "workItemId": "wi-456",
  "caseId": "case-12345",
  "status": "completed",
  "completedAt": "2026-02-28T14:35:01Z",
  "nextWorkItems": [
    {
      "workItemId": "wi-457",
      "title": "Deploy to staging",
      "assignee": "devops.team@company.com",
      "estimatedDuration": "2 hours",
      "priority": "high"
    }
  ],
  "caseProgress": {
    "completedTasks": 5,
    "totalTasks": 7,
    "progressPercentage": 71.4,
    "estimatedRemainingDuration": "3 days"
  },
  "caseState": {
    "caseId": "case-12345",
    "status": "in_progress",
    "earnedValue": 12000,
    "completionPercentage": 71.4
  },
  "eventFired": [
    {
      "eventType": "work_item_completed",
      "timestamp": "2026-02-28T14:35:01Z",
      "subscribers": 3
    },
    {
      "eventType": "case_progress_updated",
      "timestamp": "2026-02-28T14:35:01Z",
      "subscribers": 5
    }
  ],
  "timestamp": "2026-02-28T14:35:01Z"
}
```

### Workflow Consequences

```
Work item completed
  ├─ Next work items unblocked (async, <50ms)
  ├─ Case progress recalculated
  ├─ Dashboard subscribers notified (WebSocket)
  ├─ Metrics updated (velocity, cycle time)
  ├─ If case completes:
  │   ├─ GL entry posted to Oracle (async)
  │   ├─ Jira epic marked "Done"
  │   ├─ Customer notified (if applicable)
  │   └─ Audit trail sealed
  └─ Event published to event store (immutable)
```

### Error Responses

**400 Bad Request** — Invalid state transition

```json
{
  "error": "INVALID_STATE_TRANSITION",
  "message": "Cannot complete work item wi-456: current status is 'blocked'. Unblock first.",
  "currentStatus": "blocked",
  "allowedTransitions": ["unblock", "delegate", "skip"],
  "timestamp": "2026-02-28T14:35:01Z"
}
```

**409 Conflict** — Concurrent modification

```json
{
  "error": "CONCURRENT_MODIFICATION",
  "message": "Work item wi-456 was modified by another user. Refresh and retry.",
  "lastModifiedBy": "bob.jones@company.com",
  "lastModifiedAt": "2026-02-28T14:34:50Z",
  "timestamp": "2026-02-28T14:35:01Z"
}
```

---

## DEPENDENCY MANAGEMENT API

**Purpose**: Identify and manage cross-case and cross-ART dependencies.

**Endpoint**: `GET /cases/{caseId}/dependencies`

### Request

```http
GET /api/v1/cases/case-12345/dependencies?includeExternal=true
Host: yawl.company.com
Authorization: Bearer session-abc123
Accept: application/json
```

### Response (200 OK)

```json
{
  "caseId": "case-12345",
  "title": "Implement real-time fraud detection",
  "dependencies": {
    "blockers": [
      {
        "blockingCaseId": "case-99999",
        "blockingArtId": "ART-XYZ",
        "blockingCaseTitle": "Platform API upgrade",
        "reason": "Platform API not ready",
        "severity": "critical",
        "estimatedUnblockDate": "2026-03-10",
        "expectedImpactHours": 20,
        "ownerEmail": "platform.team@company.com"
      }
    ],
    "dependents": [
      {
        "dependentCaseId": "case-54321",
        "dependentArtId": "ART-ABC",
        "dependentCaseTitle": "Fraud dashboard UI",
        "reason": "Awaits real-time fraud module",
        "waitingSince": "2026-02-20",
        "expectedWaitDays": 8,
        "ownerEmail": "ui.team@company.com"
      }
    ],
    "externalDependencies": [
      {
        "systemName": "ServiceNow CMDB",
        "resourceName": "Production K8s cluster approval",
        "approvalStatus": "pending",
        "approverEmail": "infra-lead@company.com",
        "requestedDate": "2026-02-25",
        "expectedApprovalDate": "2026-03-05"
      },
      {
        "systemName": "Salesforce",
        "resourceName": "Customer data export (GDPR compliance)",
        "approvalStatus": "approved",
        "approverEmail": "data.governance@company.com",
        "requestedDate": "2026-02-20",
        "approvedDate": "2026-02-22"
      }
    ]
  },
  "dependencyGraph": {
    "nodes": [
      {
        "caseId": "case-12345",
        "title": "Implement real-time fraud detection",
        "status": "in_progress"
      }
    ],
    "edges": [
      {
        "from": "case-99999",
        "to": "case-12345",
        "type": "blocks",
        "weight": 1.0
      }
    ]
  },
  "criticalPathLength": 18,
  "criticalPathDuration": "18 days",
  "timestamp": "2026-02-28T14:35:00Z"
}
```

---

## METRICS STREAMING API

**Purpose**: Real-time metrics feed for dashboards and BI tools.

**Endpoint**: `GET /metrics/stream`

### WebSocket Request

```http
GET /api/v1/metrics/stream?namespace=portfolio&namespace=art&includeAll=false
Host: yawl.company.com
Authorization: Bearer session-abc123
Upgrade: websocket
Connection: Upgrade
```

### Stream Message Format

```json
{
  "type": "metric_update",
  "namespace": "portfolio",
  "name": "earned_value_total",
  "value": 145000,
  "unit": "USD",
  "previousValue": 142000,
  "change": 3000,
  "changePercentage": 2.11,
  "timestamp": "2026-02-28T14:35:10Z",
  "tags": {
    "artId": "ART-ABC",
    "quarter": "Q1-2026",
    "fiscalYear": "2026"
  }
}
```

**Supported Metrics**:

| Namespace | Metric | Unit | Update Frequency | Source |
|-----------|--------|------|------------------|--------|
| portfolio | earned_value_total | USD | Per case completion | GL posting |
| portfolio | planned_value_total | USD | Per sprint start | Jira Align |
| portfolio | budget_variance | USD | Daily | Oracle GL |
| art | velocity_current | points | Per sprint end | Azure DevOps |
| art | health_score | 0-10 | Real-time | YAWL engine |
| art | wip_count | count | Real-time | YAWL engine |
| case | cycle_time_mean | days | Hourly | Event store |
| case | cycle_time_p95 | days | Hourly | Event store |
| incident | open_count | count | Real-time | ServiceNow webhook |
| incident | critical_count | count | Real-time | ServiceNow webhook |
| customer | nps_score | -100 to 100 | Per feedback | Salesforce |
| customer | satisfaction_trend | -1 to 1 | Daily | Salesforce |

---

## FINANCIAL POSTING API

**Purpose**: Synchronize case completion to GL posting in Oracle/SAP.

**Endpoint**: `POST /financial/post-gl-entry`

### Request

```http
POST /api/v1/financial/post-gl-entry
Host: yawl.company.com
Authorization: Bearer session-abc123
Content-Type: application/json
X-Idempotency-Key: case-12345-gl-posting

{
  "caseId": "case-12345",
  "projectId": "PRJ-12345",
  "costCenter": "CC-7890",
  "account": "6110",
  "accountDescription": "Project Labor",
  "debitAmount": 2000.00,
  "creditAmount": 0.00,
  "currency": "USD",
  "narration": "Project management work - case case-12345",
  "referenceDocument": "YAWL-case-12345",
  "postingDate": "2026-02-28",
  "effectiveDate": "2026-02-28",
  "departmentCode": "ENG",
  "costElementId": "CE-001"
}
```

**Request Body**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `caseId` | string | Yes | YAWL case ID (used for idempotency) |
| `projectId` | string | Yes | SAP/Oracle project ID |
| `costCenter` | string | Yes | Cost center for allocation |
| `account` | string | Yes | GL account number (e.g., "6110") |
| `debitAmount` | decimal | Yes | Debit amount (>0) |
| `creditAmount` | decimal | Yes | Credit amount (>0) |
| `currency` | string | Yes | Currency code (USD, EUR, etc.) |
| `narration` | string | Yes | GL line description |
| `referenceDocument` | string | No | External reference (YAWL caseId used if omitted) |
| `postingDate` | ISO8601 | Yes | Date to post (typically today) |

### Response (201 Created)

```json
{
  "glNumber": "GL-2026-002847",
  "documentNumber": "DOC-2026-0847",
  "postingStatus": "posted",
  "caseId": "case-12345",
  "projectId": "PRJ-12345",
  "postingDate": "2026-02-28",
  "postedTimestamp": "2026-02-28T14:35:12Z",
  "totalAmount": 2000.00,
  "balanceVerification": {
    "debitTotal": 2000.00,
    "creditTotal": 2000.00,
    "balanced": true
  },
  "auditTrail": {
    "postedBy": "yawl-automation@company.com",
    "systemSource": "YAWL",
    "sourceVersion": "6.0.0"
  }
}
```

### Idempotency

Use `X-Idempotency-Key` header (typically `caseId` + `-gl-posting`) to ensure GL entry is posted exactly once. Retries with the same key return the original response (200 OK) without double-posting.

### Error Responses

**400 Bad Request** — Invalid GL account

```json
{
  "error": "INVALID_GL_ACCOUNT",
  "message": "GL account 6110 does not exist in chart of accounts",
  "account": "6110",
  "validAccounts": ["6100", "6120", "6130"],
  "timestamp": "2026-02-28T14:35:12Z"
}
```

**422 Unprocessable Entity** — Debit/credit out of balance

```json
{
  "error": "UNBALANCED_ENTRY",
  "message": "GL entry must have equal debit and credit amounts",
  "debitTotal": 2000.00,
  "creditTotal": 1800.00,
  "difference": 200.00,
  "timestamp": "2026-02-28T14:35:12Z"
}
```

---

## INCIDENT CORRELATION API

**Purpose**: Link ServiceNow incidents to YAWL cases for impact analysis.

**Endpoint**: `POST /incidents/correlate`

### Request

```http
POST /api/v1/incidents/correlate
Host: yawl.company.com
Authorization: Bearer session-abc123
Content-Type: application/json

{
  "incidentId": "INC-98765",
  "shortDescription": "Fraud detection API returning 503 errors",
  "severity": "critical",
  "createdDate": "2026-02-28T14:30:00Z",
  "detectedAffectedCases": [
    {
      "caseId": "case-12345",
      "impact": "blocked",
      "estimatedRecoveryTime": "2 hours"
    }
  ]
}
```

### Response (201 Created)

```json
{
  "incidentId": "INC-98765",
  "correlatedCases": [
    {
      "caseId": "case-12345",
      "title": "Implement real-time fraud detection",
      "status": "in_progress",
      "impactType": "blocked",
      "estimatedRecoveryTime": "2 hours",
      "linkedAt": "2026-02-28T14:31:00Z"
    }
  ],
  "correlationConfidence": 0.98,
  "escalationTriggered": true,
  "escalatedTo": ["product-lead@company.com"],
  "timestamp": "2026-02-28T14:31:00Z"
}
```

---

## ERROR HANDLING

### Global Error Response Format

All errors return a consistent JSON structure with HTTP status code:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "details": {
    "field": "optional details"
  },
  "timestamp": "ISO8601",
  "requestId": "uuid",
  "correlationId": "uuid (if cross-system)"
}
```

### HTTP Status Codes

| Status | Meaning | Action |
|--------|---------|--------|
| 200 | Success | Retry not needed |
| 201 | Created | Resource created successfully |
| 202 | Accepted | Async operation queued |
| 400 | Bad Request | Fix request and retry |
| 401 | Unauthorized | Re-authenticate |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource does not exist |
| 409 | Conflict | Concurrent modification; retry with fresh data |
| 422 | Unprocessable Entity | Validation failed; fix and retry |
| 429 | Too Many Requests | Rate limited; wait and retry |
| 500 | Internal Error | Transient; retry with backoff |
| 503 | Service Unavailable | External system down; retry later |
| 504 | Gateway Timeout | Request timed out; retry |

---

## RATE LIMITING & RETRY POLICY

### Rate Limits (Per API Key)

| Endpoint | Limit | Window |
|----------|-------|--------|
| Portfolio Query | 1000 | 1 minute |
| ART Status | 10000 | 1 minute |
| Work Item Completion | 5000 | 1 minute |
| GL Posting | 100 | 1 minute |

### Response Headers

```http
X-RateLimit-Limit: 10000
X-RateLimit-Remaining: 9850
X-RateLimit-Reset: 1709142600
Retry-After: 60
```

### Retry Strategy

**Exponential Backoff** with jitter:

```
Attempt 1: 100ms
Attempt 2: 200ms + jitter
Attempt 3: 400ms + jitter
Attempt 4: 800ms + jitter
Max: 4 attempts (3.2s total)
```

**Java Example**:

```java
public <T> T withRetry(Callable<T> operation, int maxAttempts) {
    long delayMs = 100;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return operation.call();
        } catch (RetryableException e) {
            if (attempt == maxAttempts) throw e;

            long jitter = (long) (Math.random() * delayMs);
            Thread.sleep(delayMs + jitter);
            delayMs *= 2;
        }
    }
    throw new RuntimeException("Unreachable");
}
```

---

**Document Version**: 1.0.0
**Last Updated**: 2026-02-28
**Status**: Production Ready
