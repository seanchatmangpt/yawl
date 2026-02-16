# YAWL API Client Generation Opportunities

## Executive Summary

YAWL provides multiple well-defined interfaces that are excellent candidates for automated client generation using tools like OpenAPI Generator, AsyncAPI, or custom generators (ggen). This document maps all API interfaces and identifies generation opportunities.

---

## 1. YAWL API Interface Overview

### Interface Architecture

YAWL implements the WfMC (Workflow Management Coalition) interface standards with custom extensions:

```
+------------------+     +------------------+     +------------------+
|   Interface A    |     |   Interface B    |     |   Interface X    |
|  (Management)    |     |    (Client)      |     |   (Events)       |
+------------------+     +------------------+     +------------------+
| - Specification  |     | - Work Items     |     | - Exceptions     |
|   Management     |     | - Case Launch    |     | - Constraints    |
| - User Accounts  |     | - Task Checkout  |     | - Timeouts       |
| - Service Mgmt   |     | - Data Updates   |     | - Cancellations  |
+------------------+     +------------------+     +------------------+
        |                        |                        |
        v                        v                        v
+------------------------------------------------------------------+
|                      EngineGateway                               |
|                   (Unified API Layer)                            |
+------------------------------------------------------------------+
```

---

## 2. Interface A - Administration & Management

**Purpose**: WfMC Interface 5 - Administration + Monitoring

**Location**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceA/`

### Interface Methods

| Method | HTTP Action | Parameters | Returns | Description |
|--------|-------------|------------|---------|-------------|
| `connect` | POST | userID, password | sessionHandle | Authenticate and get session |
| `disconnect` | POST | sessionHandle | success/failure | End session |
| `checkConnection` | POST | sessionHandle | success/failure | Validate session |
| `upload` | POST | specXML, sessionHandle | specID | Upload workflow specification |
| `unload` | POST | specIdentifier, specVersion, specURI | success/failure | Remove specification |
| `getList` | POST | sessionHandle | specList | Get all loaded specifications |
| `getAccounts` | POST | sessionHandle | accountList | List all user accounts |
| `createAccount` | POST | userID, password, doco | success/failure | Create user account |
| `updateAccount` | POST | userID, password, doco | success/failure | Update user account |
| `deleteAccount` | POST | userID | success/failure | Delete user account |
| `getYAWLServices` | POST | sessionHandle | serviceList | List registered services |
| `newYAWLService` | POST | service | success/failure | Register new service |
| `removeYAWLService` | POST | serviceURI | success/failure | Unregister service |
| `reannounceEnabledWorkItems` | POST | sessionHandle | count | Re-announce enabled items |
| `reannounceExecutingWorkItems` | POST | sessionHandle | count | Re-announce executing items |
| `reannounceFiredWorkItems` | POST | sessionHandle | count | Re-announce fired items |

### Data Types

```java
// YSpecificationID - Composite key for specifications
{
  "identifier": "string",    // Specification identifier
  "version": "string",       // Version (e.g., "0.1", "2.0")
  "uri": "string"           // Specification URI
}

// Session Handle - Authentication token
"sessionHandle": "string"    // UUID-based session identifier
```

---

## 3. Interface B - Client & Runtime

**Purpose**: WfMC Interfaces 2+3 - Workflow client applications and invoked applications

**Location**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/`

### Interface Methods

#### Connection Management

| Method | HTTP Action | Parameters | Returns | Description |
|--------|-------------|------------|---------|-------------|
| `connect` | POST | userid, password, encrypt | sessionHandle | Authenticate client |
| `disconnect` | POST | sessionHandle | success/failure | End session |
| `checkConnection` | POST | sessionHandle | success/failure | Validate session |
| `checkIsAdmin` | POST | sessionHandle | success/failure | Check admin status |

#### Work Item Operations

| Method | HTTP Action | Parameters | Returns | Description |
|--------|-------------|------------|---------|-------------|
| `checkout` | POST | workItemID, logPredicate | workItemData | Start work item |
| `checkin` | POST | workItemID, data, logPredicate | success/failure | Complete work item |
| `getWorkItem` | POST | workItemID | workItemRecord | Get item details |
| `getLiveItems` | POST | sessionHandle | workItemList | All active items |
| `suspend` | POST | workItemID | workItemRecord | Suspend item |
| `unsuspend` | POST | workItemID | workItemRecord | Resume item |
| `rollback` | POST | workItemID | success/failure | Rollback to enabled |
| `skip` | POST | workItemID | success/failure | Skip item |
| `getChildren` | POST | workItemID | childList | Get child items |
| `createInstance` | POST | workItemID, paramValueForMICreation | workItemRecord | Create multi-instance |

#### Case Operations

| Method | HTTP Action | Parameters | Returns | Description |
|--------|-------------|------------|---------|-------------|
| `launchCase` | POST | specID, caseParams, completionObserver, logData | caseID | Start new case |
| `cancelCase` | POST | caseID | success/failure | Cancel case |
| `getCaseState` | POST | caseID | stateXML | Get case state |
| `getCaseData` | POST | caseID | dataXML | Get case data |
| `getAllRunningCases` | POST | sessionHandle | caseList | All running cases |
| `getCasesForSpecification` | POST | specID | caseList | Cases for spec |
| `exportCaseState` | POST | caseID | stateXML | Export case state |
| `importCases` | POST | xml | caseList | Import cases |

#### Specification Queries

| Method | HTTP Action | Parameters | Returns | Description |
|--------|-------------|------------|---------|-------------|
| `getSpecificationPrototypesList` | POST | sessionHandle | specList | List specs |
| `getSpecification` | POST | specID | specXML | Get spec definition |
| `getSpecificationData` | POST | specID | dataXML | Get spec data |
| `getSpecificationDataSchema` | POST | specID | schemaXML | Get data schema |
| `taskInformation` | POST | specID, taskID | taskInfo | Task details |
| `getMITaskAttributes` | POST | specID, taskID | attributes | Multi-instance attrs |
| `getResourcingSpecs` | POST | specID, taskID | resourcingXML | Resourcing specs |

### WorkItemRecord Structure

```xml
<WorkItemRecord>
  <specIdentifier>string</specIdentifier>
  <specVersion>string</specVersion>
  <specURI>string</specURI>
  <caseID>string</caseID>
  <taskID>string</taskID>
  <taskName>string</taskName>
  <uniqueID>string</uniqueID>
  <status>Enabled|Fired|Executing|Complete|Suspended|Failed</status>
  <resourceStatus>Offered|Allocated|Started|Suspended|Unresourced</resourceStatus>
  <enablementTimeMs>timestamp</enablementTimeMs>
  <firingTimeMs>timestamp</firingTimeMs>
  <startTimeMs>timestamp</startTimeMs>
  <completionTimeMs>timestamp</completionTimeMs>
  <timerTrigger>string</timerTrigger>
  <timerExpiry>timestamp</timerExpiry>
  <codelet>string</codelet>
  <documentation>string</documentation>
  <allowsDynamicCreation>boolean</allowsDynamicCreation>
  <requiresManualResourcing>boolean</requiresManualResourcing>
</WorkItemRecord>
```

---

## 4. Interface X - Exception Events

**Purpose**: Interface between YAWL Engine and Custom YAWL Service for exception handling

**Location**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceX/`

### Event Types

| Event | Method | Parameters | Description |
|-------|--------|------------|-------------|
| Case Constraint Check | `handleCheckCaseConstraintEvent` | specID, caseID, data, precheck | Validate case constraints |
| Work Item Constraint Check | `handleCheckWorkItemConstraintEvent` | wir, data, precheck | Validate item constraints |
| Work Item Abort | `handleWorkItemAbortException` | wir, caseData | Handle abort exception |
| Timeout | `handleTimeoutEvent` | wir, taskList | Handle timeout event |
| Resource Unavailable | `handleResourceUnavailableException` | resourceID, wir, caseData, primary | Handle missing resource |
| Constraint Violation | `handleConstraintViolationException` | wir, caseData | Handle constraint violation |
| Case Cancellation | `handleCaseCancellationEvent` | caseID | Handle case cancellation |

### Event Notification Codes

```java
NOTIFY_CHECK_CASE_CONSTRAINTS = 0
NOTIFY_CHECK_ITEM_CONSTRAINTS = 1
NOTIFY_WORKITEM_ABORT = 2
NOTIFY_TIMEOUT = 3
NOTIFY_RESOURCE_UNAVAILABLE = 4
NOTIFY_CONSTRAINT_VIOLATION = 5
NOTIFY_CANCELLED_CASE = 6
```

---

## 5. OpenAPI 3.1 Specification Generation

### Proposed OpenAPI Structure

```yaml
openapi: 3.1.0
info:
  title: YAWL Engine API
  version: 5.2.0
  description: |
    YAWL (Yet Another Workflow Language) Engine API
    Based on WfMC Interface Standards with YAWL Extensions

servers:
  - url: http://localhost:8080/yawl
    description: Local development
  - url: https://yawl.example.com/yawl
    description: Production

paths:
  # Interface A - Administration
  /ia:
    post:
      summary: Interface A Administration Endpoint
      requestBody:
        content:
          application/x-www-form-urlencoded:
            schema:
              type: object
              properties:
                action:
                  type: string
                  enum: [connect, disconnect, upload, unload, getList, ...]
                sessionHandle:
                  type: string
                userID:
                  type: string
                password:
                  type: string
                specXML:
                  type: string
      responses:
        '200':
          description: XML Response
          content:
            application/xml:
              schema:
                type: string

  # Interface B - Client Operations
  /ib:
    post:
      summary: Interface B Client Endpoint
      requestBody:
        content:
          application/x-www-form-urlencoded:
            schema:
              type: object
              properties:
                action:
                  type: string
                  enum: [connect, checkout, checkin, launchCase, ...]
                workItemID:
                  type: string
                caseID:
                  type: string
                specidentifier:
                  type: string
                specversion:
                  type: string
                specuri:
                  type: string
                data:
                  type: string
      responses:
        '200':
          description: XML Response

  # Interface X - Event Callbacks
  /ix:
    post:
      summary: Interface X Event Endpoint
      requestBody:
        content:
          application/x-www-form-urlencoded:
            schema:
              type: object
              properties:
                action:
                  type: integer
                  enum: [0, 1, 2, 3, 4, 5, 6]
                workItem:
                  type: string
                  description: WorkItemRecord XML
                caseID:
                  type: string
                specID:
                  type: string
      responses:
        '200':
          description: Event processed

components:
  schemas:
    YSpecificationID:
      type: object
      properties:
        identifier:
          type: string
        version:
          type: string
        uri:
          type: string

    WorkItemRecord:
      type: object
      properties:
        specIdentifier:
          type: string
        specVersion:
          type: string
        specURI:
          type: string
        caseID:
          type: string
        taskID:
          type: string
        taskName:
          type: string
        uniqueID:
          type: string
        status:
          type: string
          enum: [Enabled, Fired, Executing, Complete, Suspended, Failed]
        resourceStatus:
          type: string
          enum: [Offered, Allocated, Started, Suspended, Unresourced]
        enablementTimeMs:
          type: string
        firingTimeMs:
          type: string
        startTimeMs:
          type: string
        completionTimeMs:
          type: string

  securitySchemes:
    sessionAuth:
      type: apiKey
      in: header
      name: sessionHandle
```

---

## 6. AsyncAPI Specification for Events

### Proposed AsyncAPI Structure

```yaml
asyncapi: 3.0.0
info:
  title: YAWL Engine Event API
  version: 5.2.0
  description: Event-driven interface for YAWL workflow events

servers:
  production:
    url: http://yawl.example.com/ix
    protocol: http

channels:
  case/constraint/check:
    description: Case constraint validation events
    publish:
      message:
        name: CaseConstraintEvent
        payload:
          type: object
          properties:
            specID:
              $ref: '#/components/schemas/YSpecificationID'
            caseID:
              type: string
            data:
              type: string
            precheck:
              type: boolean

  workitem/constraint/check:
    description: Work item constraint validation events
    publish:
      message:
        name: WorkItemConstraintEvent
        payload:
          type: object
          properties:
            workItem:
              $ref: '#/components/schemas/WorkItemRecord'
            data:
              type: string
            precheck:
              type: boolean

  workitem/abort:
    description: Work item abort events
    publish:
      message:
        name: WorkItemAbortEvent
        payload:
          type: object
          properties:
            workItem:
              $ref: '#/components/schemas/WorkItemRecord'

  workitem/timeout:
    description: Work item timeout events
    publish:
      message:
        name: TimeoutEvent
        payload:
          type: object
          properties:
            workItem:
              $ref: '#/components/schemas/WorkItemRecord'
            taskList:
              type: array
              items:
                type: string

  resource/unavailable:
    description: Resource unavailable events
    publish:
      message:
        name: ResourceUnavailableEvent
        payload:
          type: object
          properties:
            resourceID:
              type: string
            workItem:
              $ref: '#/components/schemas/WorkItemRecord'
            caseData:
              type: string
            primary:
              type: boolean

  case/cancel:
    description: Case cancellation events
    publish:
      message:
        name: CaseCancellationEvent
        payload:
          type: object
          properties:
            caseID:
              type: string

components:
  schemas:
    YSpecificationID:
      type: object
      properties:
        identifier:
          type: string
        version:
          type: string
        uri:
          type: string

    WorkItemRecord:
      type: object
      properties:
        specIdentifier:
          type: string
        caseID:
          type: string
        taskID:
          type: string
        status:
          type: string
        resourceStatus:
          type: string
```

---

## 7. Client Generation Targets

### 7.1 Java Client (Retrofit/Feign)

**Recommended Generator**: OpenAPI Generator `java` library

**Target Frameworks**:
- **Retrofit2** - Type-safe HTTP client for Android/Java
- **OpenFeign** - Declarative REST client (Spring Cloud)
- **Jersey** - JAX-RS reference implementation

**Example Retrofit Interface**:

```java
public interface YawlInterfaceAApi {
    @FormUrlEncoded
    @POST("/yawl/ia")
    Call<String> connect(
        @Field("action") String action,
        @Field("userID") String userID,
        @Field("password") String password
    );

    @FormUrlEncoded
    @POST("/yawl/ia")
    Call<String> uploadSpecification(
        @Field("action") String action,
        @Field("specXML") String specXML,
        @Field("sessionHandle") String sessionHandle
    );
}

public interface YawlInterfaceBApi {
    @FormUrlEncoded
    @POST("/yawl/ib")
    Call<String> launchCase(
        @Field("action") String action,
        @Field("specidentifier") String specIdentifier,
        @Field("specversion") String specVersion,
        @Field("specuri") String specUri,
        @Field("caseParams") String caseParams,
        @Field("sessionHandle") String sessionHandle
    );

    @FormUrlEncoded
    @POST("/yawl/ib")
    Call<String> checkout(
        @Field("action") String action,
        @Field("workItemID") String workItemID,
        @Field("sessionHandle") String sessionHandle
    );
}
```

### 7.2 Python Client (requests/httpx)

**Recommended Generator**: OpenAPI Generator `python` library

**Target Libraries**:
- **requests** - Simple HTTP library
- **httpx** - Modern async HTTP client
- **aiohttp** - Async HTTP client/server

**Example Python Client**:

```python
from dataclasses import dataclass
from typing import Optional
import requests

@dataclass
class YSpecificationID:
    identifier: str
    version: str
    uri: str

@dataclass
class WorkItemRecord:
    spec_identifier: str
    spec_version: str
    spec_uri: str
    case_id: str
    task_id: str
    status: str
    resource_status: str

class YawlClient:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.session_handle: Optional[str] = None

    def connect(self, user_id: str, password: str) -> str:
        response = requests.post(
            f"{self.base_url}/ib",
            data={
                "action": "connect",
                "userid": user_id,
                "password": password
            }
        )
        self.session_handle = response.text
        return self.session_handle

    def launch_case(
        self,
        spec_id: YSpecificationID,
        case_params: Optional[str] = None
    ) -> str:
        response = requests.post(
            f"{self.base_url}/ib",
            data={
                "action": "launchCase",
                "specidentifier": spec_id.identifier,
                "specversion": spec_id.version,
                "specuri": spec_id.uri,
                "caseParams": case_params,
                "sessionHandle": self.session_handle
            }
        )
        return response.text

    def checkout(self, work_item_id: str) -> str:
        response = requests.post(
            f"{self.base_url}/ib",
            data={
                "action": "checkout",
                "workItemID": work_item_id,
                "sessionHandle": self.session_handle
            }
        )
        return response.text
```

### 7.3 TypeScript/JavaScript Client

**Recommended Generator**: OpenAPI Generator `typescript-axios` or `typescript-fetch`

**Target Libraries**:
- **axios** - Promise-based HTTP client
- **fetch** - Native browser/Node.js API
- **ky** - Tiny fetch wrapper

**Example TypeScript Client**:

```typescript
interface YSpecificationID {
  identifier: string;
  version: string;
  uri: string;
}

interface WorkItemRecord {
  specIdentifier: string;
  specVersion: string;
  specURI: string;
  caseID: string;
  taskID: string;
  status: 'Enabled' | 'Fired' | 'Executing' | 'Complete' | 'Suspended' | 'Failed';
  resourceStatus: 'Offered' | 'Allocated' | 'Started' | 'Suspended' | 'Unresourced';
}

class YawlClient {
  private baseUrl: string;
  private sessionHandle: string | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  async connect(userId: string, password: string): Promise<string> {
    const params = new URLSearchParams({
      action: 'connect',
      userid: userId,
      password: password
    });

    const response = await fetch(`${this.baseUrl}/ib`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params
    });

    this.sessionHandle = await response.text();
    return this.sessionHandle;
  }

  async launchCase(specId: YSpecificationID, caseParams?: string): Promise<string> {
    const params = new URLSearchParams({
      action: 'launchCase',
      specidentifier: specId.identifier,
      specversion: specId.version,
      specuri: specId.uri,
      sessionHandle: this.sessionHandle!
    });

    if (caseParams) {
      params.append('caseParams', caseParams);
    }

    const response = await fetch(`${this.baseUrl}/ib`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params
    });

    return response.text();
  }
}
```

### 7.4 Go Client

**Recommended Generator**: OpenAPI Generator `go` library

**Example Go Client**:

```go
package yawl

import (
    "net/http"
    "net/url"
    "strings"
)

type YSpecificationID struct {
    Identifier string `json:"identifier"`
    Version    string `json:"version"`
    URI        string `json:"uri"`
}

type Client struct {
    BaseURL       string
    SessionHandle string
    HTTPClient    *http.Client
}

func NewClient(baseURL string) *Client {
    return &Client{
        BaseURL:    baseURL,
        HTTPClient: &http.Client{},
    }
}

func (c *Client) Connect(userID, password string) (string, error) {
    data := url.Values{}
    data.Set("action", "connect")
    data.Set("userid", userID)
    data.Set("password", password)

    resp, err := c.HTTPClient.Post(
        c.BaseURL+"/ib",
        "application/x-www-form-urlencoded",
        strings.NewReader(data.Encode()),
    )
    if err != nil {
        return "", err
    }
    defer resp.Body.Close()

    buf := new(strings.Builder)
    buf.ReadFrom(resp.Body)
    c.SessionHandle = buf.String()
    return c.SessionHandle, nil
}

func (c *Client) LaunchCase(specID YSpecificationID, caseParams string) (string, error) {
    data := url.Values{}
    data.Set("action", "launchCase")
    data.Set("specidentifier", specID.Identifier)
    data.Set("specversion", specID.Version)
    data.Set("specuri", specID.URI)
    data.Set("caseParams", caseParams)
    data.Set("sessionHandle", c.SessionHandle)

    resp, err := c.HTTPClient.Post(
        c.BaseURL+"/ib",
        "application/x-www-form-urlencoded",
        strings.NewReader(data.Encode()),
    )
    if err != nil {
        return "", err
    }
    defer resp.Body.Close()

    buf := new(strings.Builder)
    buf.ReadFrom(resp.Body)
    return buf.String(), nil
}
```

### 7.5 Rust Client

**Recommended Generator**: OpenAPI Generator `rust` library

**Example Rust Client**:

```rust
use reqwest::{Client, header};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Serialize, Deserialize)]
pub struct YSpecificationID {
    pub identifier: String,
    pub version: String,
    pub uri: String,
}

pub struct YawlClient {
    base_url: String,
    session_handle: Option<String>,
    client: Client,
}

impl YawlClient {
    pub fn new(base_url: &str) -> Self {
        YawlClient {
            base_url: base_url.to_string(),
            session_handle: None,
            client: Client::new(),
        }
    }

    pub async fn connect(&mut self, user_id: &str, password: &str) -> Result<String, reqwest::Error> {
        let mut params = HashMap::new();
        params.insert("action", "connect");
        params.insert("userid", user_id);
        params.insert("password", password);

        let response = self.client
            .post(format!("{}/ib", self.base_url))
            .form(&params)
            .send()
            .await?;

        let handle = response.text().await?;
        self.session_handle = Some(handle.clone());
        Ok(handle)
    }

    pub async fn launch_case(
        &self,
        spec_id: &YSpecificationID,
        case_params: Option<&str>,
    ) -> Result<String, reqwest::Error> {
        let mut params = HashMap::new();
        params.insert("action", "launchCase");
        params.insert("specidentifier", &spec_id.identifier);
        params.insert("specversion", &spec_id.version);
        params.insert("specuri", &spec_id.uri);
        params.insert("sessionHandle", self.session_handle.as_ref().unwrap());

        if let Some(params_str) = case_params {
            params.insert("caseParams", params_str);
        }

        let response = self.client
            .post(format!("{}/ib", self.base_url))
            .form(&params)
            .send()
            .await?;

        response.text().await
    }
}
```

---

## 8. SDK Features Implementation

### 8.1 Authentication Handling

**Current Implementation**: Session-based authentication via `sessionHandle`

```java
// Current: Session handle passed with each request
String sessionHandle = engine.connect(userID, password, interval);
engine.launchCase(specID, params, observer, sessionHandle);

// Proposed SDK Enhancement: Automatic session management
public class YawlSdkClient {
    private String sessionHandle;
    private Instant sessionExpiry;

    public void ensureAuthenticated() {
        if (sessionHandle == null || Instant.now().isAfter(sessionExpiry)) {
            reconnect();
        }
    }

    private void reconnect() {
        this.sessionHandle = engine.connect(userID, password, timeout);
        this.sessionExpiry = Instant.now().plusSeconds(sessionTimeout);
    }
}
```

### 8.2 Retry Logic

```java
public class RetryPolicy {
    private int maxRetries = 3;
    private Duration initialDelay = Duration.ofMillis(100);
    private double backoffMultiplier = 2.0;

    public <T> T executeWithRetry(Supplier<T> operation) {
        int attempts = 0;
        Duration delay = initialDelay;

        while (attempts < maxRetries) {
            try {
                return operation.get();
            } catch (TransientException e) {
                attempts++;
                if (attempts >= maxRetries) {
                    throw e;
                }
                Thread.sleep(delay.toMillis());
                delay = delay.multipliedBy((long) backoffMultiplier);
            }
        }
        throw new RuntimeException("Max retries exceeded");
    }
}
```

### 8.3 Rate Limiting

```java
public class RateLimiter {
    private final RateLimiter rateLimiter = RateLimiter.create(10.0); // 10 req/sec

    public <T> T executeWithRateLimit(Supplier<T> operation) {
        rateLimiter.acquire();
        return operation.get();
    }
}
```

### 8.4 Error Handling

```java
public class YawlException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;

    public static YawlException fromResponse(String xmlResponse) {
        // Parse <failure> elements from XML response
        if (xmlResponse.contains("<failure>")) {
            String message = extractFailureMessage(xmlResponse);
            return new YawlException("YAWL_ERROR", message);
        }
        return null;
    }
}

// Usage
try {
    String result = client.launchCase(specID, params);
    if (result.contains("<failure>")) {
        throw YawlException.fromResponse(result);
    }
} catch (YawlException e) {
    logger.error("YAWL operation failed: {}", e.getErrorMessage());
}
```

---

## 9. Generation Implementation Strategy

### Phase 1: OpenAPI Specification Creation

1. **Extract API definitions** from:
   - `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceA/InterfaceA_EngineBasedServer.java`
   - `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceB_EngineBasedServer.java`
   - `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/EngineGateway.java`

2. **Generate OpenAPI 3.1 spec** using:
   - Custom parser for action-based POST endpoints
   - Schema extraction from Java types (YSpecificationID, WorkItemRecord)
   - Response format documentation

### Phase 2: AsyncAPI Specification for Events

1. **Map event handlers** from:
   - `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceX/InterfaceX_Service.java`

2. **Generate AsyncAPI 3.0 spec** with:
   - Channel definitions per event type
   - Message schemas for each event
   - HTTP webhook binding (current implementation uses POST)

### Phase 3: Client Generation

Using OpenAPI Generator:

```bash
# Java (Retrofit2)
openapi-generator-cli generate \
  -i yawl-openapi.yaml \
  -g java \
  -o clients/java-retrofit \
  --library retrofit2

# Python
openapi-generator-cli generate \
  -i yawl-openapi.yaml \
  -g python \
  -o clients/python

# TypeScript (axios)
openapi-generator-cli generate \
  -i yawl-openapi.yaml \
  -g typescript-axios \
  -o clients/typescript

# Go
openapi-generator-cli generate \
  -i yawl-openapi.yaml \
  -g go \
  -o clients/go

# Rust
openapi-generator-cli generate \
  -i yawl-openapi.yaml \
  -g rust \
  -o clients/rust
```

### Phase 4: Custom ggen Integration

For advanced generation using ggen:

```bash
# Generate from Java interfaces directly
ggen openapi --source src/org/yawlfoundation/yawl/engine/interfce/ \
             --output docs/openapi/yawl-api.yaml \
             --format openapi-3.1

# Generate client SDKs
ggen client --spec docs/openapi/yawl-api.yaml \
            --language java \
            --library retrofit2 \
            --output clients/java

ggen client --spec docs/openapi/yawl-api.yaml \
            --language python \
            --library httpx \
            --output clients/python
```

---

## 10. GraphQL Schema Generation (Future)

### Proposed GraphQL Schema

```graphql
type Query {
  "Get specification by ID"
  specification(id: YSpecificationIDInput!): Specification

  "List all specifications"
  specifications: [Specification!]!

  "Get work item by ID"
  workItem(id: ID!): WorkItem

  "Get all work items with optional filters"
  workItems(status: WorkItemStatus, caseId: ID): [WorkItem!]!

  "Get case by ID"
  case(id: ID!): Case

  "Get all running cases"
  runningCases: [Case!]!
}

type Mutation {
  "Connect to engine"
  connect(userId: String!, password: String!): Session!

  "Disconnect from engine"
  disconnect(sessionHandle: String!): Boolean!

  "Launch a new case"
  launchCase(
    specId: YSpecificationIDInput!
    caseParams: String
    completionObserver: String
  ): Case!

  "Cancel a case"
  cancelCase(caseId: ID!): Boolean!

  "Checkout work item"
  checkout(workItemId: ID!): WorkItem!

  "Checkin work item"
  checkin(workItemId: ID!, data: String!): WorkItem!

  "Upload specification"
  uploadSpecification(specXml: String!): Specification!
}

type Subscription {
  "Subscribe to case events"
  caseEvents(caseId: ID): CaseEvent!

  "Subscribe to work item events"
  workItemEvents(workItemId: ID): WorkItemEvent!

  "Subscribe to exception events"
  exceptionEvents: ExceptionEvent!
}

input YSpecificationIDInput {
  identifier: String!
  version: String!
  uri: String!
}

type Specification {
  id: YSpecificationID!
  name: String
  documentation: String
  tasks: [Task!]!
  dataSchema: String
}

type Task {
  id: ID!
  name: String
  documentation: String
  resourcing: String
}

type WorkItem {
  id: ID!
  specId: YSpecificationID!
  caseId: ID!
  taskId: ID!
  status: WorkItemStatus!
  resourceStatus: ResourceStatus!
  data: String
  enablementTime: String
  startTime: String
  completionTime: String
}

type Case {
  id: ID!
  specId: YSpecificationID!
  state: String!
  data: String
  workItems: [WorkItem!]!
}

type Session {
  handle: String!
  expiresAt: String!
}

enum WorkItemStatus {
  ENABLED
  FIRED
  EXECUTING
  COMPLETE
  SUSPENDED
  FAILED
  DISCARDED
}

enum ResourceStatus {
  OFFERED
  ALLOCATED
  STARTED
  SUSPENDED
  UNRESOURCED
}

union CaseEvent = CaseStarted | CaseCompleted | CaseCancelled

union WorkItemEvent = WorkItemEnabled | WorkItemStarted | WorkItemCompleted

type ExceptionEvent {
  type: ExceptionType!
  workItem: WorkItem
  caseId: ID
  data: String
}

enum ExceptionType {
  CONSTRAINT_VIOLATION
  TIMEOUT
  RESOURCE_UNAVAILABLE
  WORKITEM_ABORT
  CASE_CANCELLATION
}
```

---

## 11. Summary and Recommendations

### Immediate Opportunities

1. **OpenAPI 3.1 Specification**: Generate comprehensive API documentation from existing Java interfaces
2. **Multi-language SDKs**: Generate Java, Python, TypeScript, Go, and Rust clients
3. **AsyncAPI for Events**: Document event-driven Interface X

### Strategic Benefits

| Benefit | Impact |
|---------|--------|
| Reduced Integration Time | Pre-generated clients eliminate boilerplate |
| Type Safety | Strongly-typed SDKs catch errors at compile time |
| Documentation Sync | OpenAPI spec serves as single source of truth |
| Ecosystem Growth | Easy client generation attracts more adopters |
| Versioning Support | OpenAPI enables API versioning strategies |

### Recommended Priority Order

1. **High Priority**: Python SDK (data science/AI integration)
2. **High Priority**: TypeScript SDK (modern web applications)
3. **Medium Priority**: Java SDK (enterprise integration)
4. **Medium Priority**: Go SDK (microservices/cloud-native)
5. **Low Priority**: Rust SDK (high-performance edge cases)

### Implementation Checklist

- [ ] Extract complete OpenAPI 3.1 specification
- [ ] Generate AsyncAPI 3.0 for Interface X events
- [ ] Create Python client SDK with httpx
- [ ] Create TypeScript client SDK with axios
- [ ] Create Java client SDK with Retrofit2
- [ ] Add retry logic and rate limiting
- [ ] Add comprehensive error handling
- [ ] Create integration tests for all SDKs
- [ ] Publish SDKs to package registries (PyPI, npm, Maven, crates.io)
