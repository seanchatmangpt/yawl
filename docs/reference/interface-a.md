# Interface A Protocol Reference

## Overview

Interface A is the administrative and process definition API for YAWL, implementing WfMC (Workflow Management Coalition) interfaces 1 and 5:

- **Interface 1**: Process definition tools - specification upload and management
- **Interface 5**: Administration and monitoring - user accounts, services, and engine operations

This interface provides capabilities for:

- Uploading and managing workflow specifications
- User account management (create, update, delete accounts)
- YAWL service registration and removal
- Administrative operations (work item re-announcement, hibernate statistics)
- Build and configuration information

This document describes the complete Interface A protocol for YAWL v6.0.0.

## Protocol Details

| Property | Value |
|----------|-------|
| Base Path | `/ia` |
| Default Format | XML |
| Authentication | Session handle via query parameter (admin privileges required) |
| HTTP Methods | GET, POST (all GETs redirected to POST) |

## Implementation Classes

| Class | Purpose |
|-------|---------|
| `InterfaceA_EngineBasedServer` | Servlet handling POST/GET requests |
| `InterfaceADesign` | WfMC Interface 1 placeholder (YAWL uses YSpecification format) |
| `InterfaceAManagement` | WfMC Interface 5 interface definition |
| `EngineGateway` | Facade delegating to YEngine |

---

## Session Management

### connect

Authenticates an administrator and establishes a session with the engine.

**Request Format:**
```
POST /ia
Content-Type: application/x-www-form-urlencoded

action=connect&userID={userid}&password={password}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "connect" |
| userID | string | Yes | Administrator user identifier |
| password | string | Yes | Administrator password |

**Response (Success):**
```xml
<response>
  <sessionHandle>H4sIAAAAAAAAA...</sessionHandle>
</response>
```

**Response (Failure):**
```xml
<response>
  <failure>Invalid credentials</failure>
</response>
```

**Java Method:** `EngineGateway.connect(String userID, String password, long timeOutSeconds)`

---

### disconnect

Terminates the admin session with the engine.

**Request Format:**
```
POST /ia?action=disconnect&sessionHandle={handle}
```

**Response (Success):**
```xml
<response>
  <success>Session terminated</success>
</response>
```

**Java Method:** `EngineGateway.disconnect(String sessionHandle)`

---

### checkConnection

Validates an admin session handle.

**Request Format:**
```
POST /ia?action=checkConnection&sessionHandle={handle}
```

**Response (Valid):**
```xml
<response>
  <success>Connection valid</success>
</response>
```

**Response (Invalid):**
```xml
<response>
  <failure>Invalid or expired session</failure>
</response>
```

**Java Method:** `EngineGateway.checkConnectionForAdmin(String sessionHandle)`

---

## Specification Management

### upload

Loads a workflow specification into the engine from an XML definition.

**Request Format:**
```
POST /ia
Content-Type: application/x-www-form-urlencoded

action=upload&specXML={xmlContent}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "upload" |
| specXML | string | Yes | XML specification content (URL-encoded) |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>
    <specification>
      <id>ProcurementProcess</id>
      <version>1.0</version>
      <uri>http://example.com/specs/procurement</uri>
    </specification>
  </success>
</response>
```

**Response (Validation Failure):**
```xml
<response>
  <failure>
    <reason>Specification validation failed</reason>
    <errors>
      <error>Task 'ApproveRequest' has invalid split type</error>
      <error>Missing input condition on net 'ProcurementNet'</error>
    </errors>
  </failure>
</response>
```

**Response (Parse Failure):**
```xml
<response>
  <failure>
    <reason>Unable to parse specification XML</reason>
  </failure>
</response>
```

**Java Method:** `EngineGateway.loadSpecification(String specification, String sessionHandle)`

---

### unload

Unloads a specification from the engine.

**Request Format:**
```
POST /ia?action=unload
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "unload" |
| specidentifier | string | Yes | Specification identifier |
| specversion | string | Yes | Specification version |
| specuri | string | Yes | Specification URI |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>Specification unloaded successfully</success>
</response>
```

**Response (Failure - Active Cases):**
```xml
<response>
  <failure>Cannot unload specification with active running cases</failure>
</response>
```

**Java Method:** `EngineGateway.unloadSpecification(YSpecificationID specID, String sessionHandle)`

---

### getList

Retrieves the list of all loaded specifications.

**Request Format:**
```
POST /ia?action=getList&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <specifications>
    <specification>
      <id>ProcurementProcess</id>
      <version>1.0</version>
      <uri>http://example.com/specs/procurement</uri>
      <loadstatus>Loaded</loadstatus>
    </specification>
    <specification>
      <id>OrderFulfillment</id>
      <version>2.0</version>
      <uri>http://example.com/specs/orders</uri>
      <loadstatus>Loaded</loadstatus>
    </specification>
  </specifications>
</response>
```

**Java Method:** `EngineGateway.getSpecificationList(String sessionHandle)`

---

## User Account Management

### getAccounts

Retrieves all registered user accounts.

**Request Format:**
```
POST /ia?action=getAccounts&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <users>
    <user>
      <userid>admin</userid>
      <documentation>System Administrator</documentation>
      <lastaccess>2026-02-19T10:30:00Z</lastaccess>
    </user>
    <user>
      <userid>workflowuser</userid>
      <documentation>Workflow Operator</documentation>
      <lastaccess>2026-02-19T09:15:00Z</lastaccess>
    </user>
  </users>
</response>
```

**Java Method:** `EngineGateway.getAccounts(String sessionHandle)`

---

### getClientAccount

Retrieves a specific user account by ID.

**Request Format:**
```
POST /ia?action=getClientAccount&userID={userid}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "getClientAccount" |
| userID | string | Yes | User identifier to retrieve |
| sessionHandle | string | Yes | Valid admin session handle |

**Response:**
```xml
<response>
  <user>
    <userid>workflowuser</userid>
    <documentation>Workflow Operator</documentation>
    <lastaccess>2026-02-19T09:15:00Z</lastaccess>
  </user>
</response>
```

**Response (Not Found):**
```xml
<response>
  <failure>User not found: unknownuser</failure>
</response>
```

**Java Method:** `EngineGateway.getClientAccount(String userID, String sessionHandle)`

---

### createAccount

Creates a new user account.

**Request Format:**
```
POST /ia?action=createAccount
     &userID={userid}
     &password={password}
     &doco={documentation}
     &sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "createAccount" |
| userID | string | Yes | New user identifier |
| password | string | Yes | Initial password |
| doco | string | No | User documentation/description |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>Account created successfully</success>
</response>
```

**Response (Duplicate):**
```xml
<response>
  <failure>Account already exists: existinguser</failure>
</response>
```

**Java Method:** `EngineGateway.createAccount(String userName, String password, String doco, String sessionHandle)`

---

### updateAccount

Updates an existing user account.

**Request Format:**
```
POST /ia?action=updateAccount
     &userID={userid}
     &password={password}
     &doco={documentation}
     &sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "updateAccount" |
| userID | string | Yes | User identifier to update |
| password | string | No | New password (omit to keep existing) |
| doco | string | No | New documentation |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>Account updated successfully</success>
</response>
```

**Java Method:** `EngineGateway.updateAccount(String userName, String password, String doco, String sessionHandle)`

---

### deleteAccount

Deletes a user account.

**Request Format:**
```
POST /ia?action=deleteAccount&userID={userid}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "deleteAccount" |
| userID | string | Yes | User identifier to delete |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>Account deleted successfully</success>
</response>
```

**Response (Not Found):**
```xml
<response>
  <failure>Account not found: unknownuser</failure>
</response>
```

**Java Method:** `EngineGateway.deleteAccount(String userName, String sessionHandle)`

---

### newPassword

Changes the password for the current session user.

**Request Format:**
```
POST /ia?action=newPassword&password={newPassword}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "newPassword" |
| password | string | Yes | New password |
| sessionHandle | string | Yes | Valid session handle |

**Response (Success):**
```xml
<response>
  <success>Password changed successfully</success>
</response>
```

**Java Method:** `EngineGateway.changePassword(String password, String sessionHandle)`

---

### getPassword

Retrieves the password for a user account.

**Request Format:**
```
POST /ia?action=getPassword&userID={userid}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "getPassword" |
| userID | string | Yes | User identifier |
| sessionHandle | string | Yes | Valid admin session handle |

**Response:**
```xml
<response>
  <password>userpassword</password>
</response>
```

**Java Method:** `EngineGateway.getClientPassword(String userID, String sessionHandle)`

---

## YAWL Service Management

### getYAWLServices

Retrieves all registered YAWL services.

**Request Format:**
```
POST /ia?action=getYAWLServices&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <services>
    <service>
      <uri>http://localhost:8080/workletService</uri>
      <name>Worklet Service</name>
      <documentation>Exception handling and dynamic process selection</documentation>
    </service>
    <service>
      <uri>http://localhost:8080/resourceService</uri>
      <name>Resource Service</name>
      <documentation>Work item distribution and participant management</documentation>
    </service>
    <service>
      <uri>http://localhost:8080/yawl/ix</uri>
      <name>Interface X Listener</name>
      <documentation>Event notifications</documentation>
    </service>
  </services>
</response>
```

**Java Method:** `EngineGateway.getYAWLServices(String sessionHandle)`

---

### newYAWLService

Registers a new YAWL service with the engine.

**Request Format:**
```
POST /ia?action=newYAWLService&service={serviceXml}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "newYAWLService" |
| service | string | Yes | XML service definition |
| sessionHandle | string | Yes | Valid admin session handle |

**Service XML Format:**
```xml
<YAWLService>
  <uri>http://localhost:8080/customService</uri>
  <name>Custom Service</name>
  <documentation>Custom integration service</documentation>
</YAWLService>
```

**Response (Success):**
```xml
<response>
  <success>Service registered successfully</success>
</response>
```

**Java Method:** `EngineGateway.addYAWLService(String serviceStr, String sessionHandle)`

---

### removeYAWLService

Removes a registered YAWL service.

**Request Format:**
```
POST /ia?action=removeYAWLService&serviceURI={uri}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "removeYAWLService" |
| serviceURI | string | Yes | URI of the service to remove |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>Service removed successfully</success>
</response>
```

**Java Method:** `EngineGateway.removeYAWLService(String serviceURI, String sessionHandle)`

---

## Work Item Re-Announcement

### reannounceEnabledWorkItems

Causes the engine to re-announce all work items in the "enabled" state.

**Request Format:**
```
POST /ia?action=reannounceEnabledWorkItems&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>
    <count>15</count>
    <message>Re-announced 15 enabled work items</message>
  </success>
</response>
```

**Java Method:** `EngineGateway.reannounceEnabledWorkItems(String sessionHandle)`

---

### reannounceExecutingWorkItems

Causes the engine to re-announce all work items in the "executing" state.

**Request Format:**
```
POST /ia?action=reannounceExecutingWorkItems&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>
    <count>8</count>
    <message>Re-announced 8 executing work items</message>
  </success>
</response>
```

**Java Method:** `EngineGateway.reannounceExecutingWorkItems(String sessionHandle)`

---

### reannounceFiredWorkItems

Causes the engine to re-announce all work items in the "fired" state.

**Request Format:**
```
POST /ia?action=reannounceFiredWorkItems&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>
    <count>3</count>
    <message>Re-announced 3 fired work items</message>
  </success>
</response>
```

**Java Method:** `EngineGateway.reannounceFiredWorkItems(String sessionHandle)`

---

### reannounceWorkItem

Causes the engine to re-announce a specific work item.

**Request Format:**
```
POST /ia?action=reannounceWorkItem&id={itemId}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "reannounceWorkItem" |
| id | string | Yes | Work item identifier |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success>Work item re-announced successfully</success>
</response>
```

**Response (Failure):**
```xml
<response>
  <failure>Work item not found: INVALID_ID</failure>
</response>
```

**Java Method:** `EngineGateway.reannounceWorkItem(String itemID, String sessionHandle)`

---

## System Information and Statistics

### getBuildProperties

Retrieves build and version information for the YAWL engine.

**Request Format:**
```
POST /ia?action=getBuildProperties&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <buildProperties>
    <version>6.0.0</version>
    <buildDate>2026-02-01</buildDate>
    <buildNumber>12345</buildNumber>
    <javaVersion>25</javaVersion>
    <springVersion>6.2.0</springVersion>
  </buildProperties>
</response>
```

**Java Method:** `EngineGateway.getBuildProperties(String sessionHandle)`

---

### getExternalDBGateways

Retrieves configured external database gateways.

**Request Format:**
```
POST /ia?action=getExternalDBGateways&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <gateways>
    <gateway>
      <name>ProductionDB</name>
      <type>PostgreSQL</type>
      <connectionString>jdbc:postgresql://db.example.com:5432/yawl</connectionString>
    </gateway>
  </gateways>
</response>
```

**Java Method:** `EngineGateway.getExternalDBGateways(String sessionHandle)`

---

### setHibernateStatisticsEnabled

Enables or disables Hibernate statistics collection.

**Request Format:**
```
POST /ia?action=setHibernateStatisticsEnabled&enabled={true|false}&sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "setHibernateStatisticsEnabled" |
| enabled | string | Yes | "true" or "false" |
| sessionHandle | string | Yes | Valid admin session handle |

**Response (Success):**
```xml
<response>
  <success/>
</response>
```

**Response (Invalid Parameter):**
```xml
<response>
  <failure>Invalid parameter value 'enabled'</failure>
</response>
```

**Java Method:** `EngineGateway.setHibernateStatisticsEnabled(boolean enabled, String sessionHandle)`

---

### isHibernateStatisticsEnabled

Checks if Hibernate statistics collection is enabled.

**Request Format:**
```
POST /ia?action=isHibernateStatisticsEnabled&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <enabled>true</enabled>
</response>
```

**Java Method:** `EngineGateway.isHibernateStatisticsEnabled(String sessionHandle)`

---

### getHibernateStatistics

Retrieves Hibernate performance statistics.

**Request Format:**
```
POST /ia?action=getHibernateStatistics&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <hibernateStatistics>
    <sessionOpenCount>1523</sessionOpenCount>
    <sessionCloseCount>1520</sessionCloseCount>
    <transactionCount>1540</transactionCount>
    <queryExecutionCount>4521</queryExecutionCount>
    <entityLoadCount>2890</entityLoadCount>
    <entityUpdateCount>156</entityUpdateCount>
    <entityInsertCount>423</entityInsertCount>
    <entityDeleteCount>89</entityDeleteCount>
    <collectionLoadCount>521</collectionLoadCount>
    <secondLevelCacheHitCount>1250</secondLevelCacheHitCount>
    <secondLevelCacheMissCount>340</secondLevelCacheMissCount>
  </hibernateStatistics>
</response>
```

**Java Method:** `EngineGateway.getHibernateStatistics(String sessionHandle)`

---

## Redundant Mode Operations

### promote

Promotes the engine from redundant (standby) mode to active mode.

**Request Format:**
```
POST /ia?action=promote&sessionHandle={handle}
```

**Response (Success):**
```xml
<response>
  <success>Engine promoted to active mode</success>
</response>
```

**Java Method:** `EngineGateway.promote(String sessionHandle)`

---

### demote

Demotes the engine from active mode to redundant (standby) mode.

**Request Format:**
```
POST /ia?action=demote&sessionHandle={handle}
```

**Response (Success):**
```xml
<response>
  <success>Engine demoted to redundant mode</success>
</response>
```

**Java Method:** `EngineGateway.demote(String sessionHandle)`

---

## curl Examples

### Connect and Get Session Handle

```bash
# Connect as administrator
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=connect" \
  -d "userID=admin" \
  -d "password=YAWL"

# Extract session handle from response
SESSION_HANDLE=$(curl -s -X POST "http://localhost:8080/yawl/ia" \
  -d "action=connect" \
  -d "userID=admin" \
  -d "password=YAWL" | xmllint --xpath "//sessionHandle/text()" -)
```

### Upload Specification

```bash
# Upload a workflow specification
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=upload" \
  -d "sessionHandle=${SESSION_HANDLE}" \
  --data-urlencode "specXML=$(cat /path/to/specification.xml)"
```

### List All Specifications

```bash
# Get list of loaded specifications
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=getList" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

### Create User Account

```bash
# Create a new user account
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=createAccount" \
  -d "userID=newuser" \
  -d "password=secret123" \
  -d "doco=New%20Workflow%20User" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

### Get All User Accounts

```bash
# List all user accounts
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=getAccounts" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

### Register YAWL Service

```bash
# Register a new YAWL service
SERVICE_XML='<YAWLService><uri>http://localhost:8080/customService</uri><name>Custom Service</name><documentation>Integration service</documentation></YAWLService>'

curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=newYAWLService" \
  -d "sessionHandle=${SESSION_HANDLE}" \
  --data-urlencode "service=${SERVICE_XML}"
```

### Re-announce Work Items

```bash
# Re-announce all enabled work items
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=reannounceEnabledWorkItems" \
  -d "sessionHandle=${SESSION_HANDLE}"

# Re-announce specific work item
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=reannounceWorkItem" \
  -d "id=WORKITEM_123" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

### Unload Specification

```bash
# Unload a specification
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=unload" \
  -d "specidentifier=ProcurementProcess" \
  -d "specversion=1.0" \
  -d "specuri=http://example.com/specs/procurement" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

### Get Hibernate Statistics

```bash
# Enable Hibernate statistics
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=setHibernateStatisticsEnabled" \
  -d "enabled=true" \
  -d "sessionHandle=${SESSION_HANDLE}"

# Get statistics
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=getHibernateStatistics" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

### Disconnect

```bash
# End the session
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=disconnect" \
  -d "sessionHandle=${SESSION_HANDLE}"
```

---

## Error Handling

All Interface A errors follow the same response format:

**Error Response:**
```xml
<response>
  <failure>Error message describing the failure</failure>
</response>
```

### Common Error Conditions

| Condition | HTTP Status | Response |
|-----------|-------------|----------|
| Invalid session | 200 | `<failure>Invalid session handle</failure>` |
| Missing parameters | 200 | `<failure>Missing required parameter: X</failure>` |
| Invalid action | 200 | `<failure><reason>Invalid action or exception was thrown.</reason></failure>` |
| Redundant mode | 200 | `<failure>Engine is in redundant mode, unable to process requests</failure>` |
| Persistence failure | 500 | HTTP 500 with message "Database persistence failure detected" |
| Unauthorized access | 200 | `<failure>Admin privileges required</failure>` |

### Redundant Mode Restrictions

When the engine is in redundant (standby) mode, most Interface A operations are blocked except for:

- `connect`
- `disconnect`
- `checkConnection`
- `promote`

Attempting other operations in redundant mode returns:
```xml
<response>
  <failure>Engine is in redundant mode, unable to process requests</failure>
</response>
```

---

## Java API Reference

### InterfaceAManagement Interface

```java
public interface InterfaceAManagement {
    // Observer registration
    void registerInterfaceAClient(InterfaceAManagementObserver observer);

    // Specification management
    List<YSpecificationID> addSpecifications(String specificationStr, boolean ignoreErrors,
                                             YVerificationHandler errorMessages)
        throws JDOMException, IOException, YPersistenceException;
    boolean loadSpecification(YSpecification spec);
    void unloadSpecification(YSpecificationID specID)
        throws YStateException, YPersistenceException;
    Set<YSpecificationID> getLoadedSpecificationIDs() throws YPersistenceException;

    // Specification queries
    YSpecification getLatestSpecification(String id);
    YSpecification getSpecification(YSpecificationID specID);
    YSpecification getSpecificationForCase(YIdentifier caseID);
    YSpecification getProcessDefinition(YSpecificationID specID);

    // Case management
    Set<YIdentifier> getCasesForSpecification(YSpecificationID specID);
    YIdentifier getCaseID(String caseIDStr) throws YPersistenceException;
    String getStateTextForCase(YIdentifier caseID) throws YPersistenceException;
    String getStateForCase(YIdentifier caseID) throws YPersistenceException;
    void cancelCase(YIdentifier id) throws YPersistenceException, YEngineStateException;
    void suspendCase(YIdentifier id) throws YPersistenceException, YStateException;
    void resumeCase(YIdentifier id) throws YPersistenceException, YStateException;

    // User management
    Set getUsers();
    YExternalClient getExternalClient(String name);
    boolean addExternalClient(YExternalClient client) throws YPersistenceException;

    // Service management
    YAWLServiceReference getRegisteredYawlService(String yawlServiceID);
    void addYawlService(YAWLServiceReference yawlService) throws YPersistenceException;
    YAWLServiceReference removeYawlService(String serviceURI) throws YPersistenceException;

    // Work item re-announcement
    int reannounceEnabledWorkItems() throws YStateException;
    int reannounceExecutingWorkItems() throws YStateException;
    int reannounceFiredWorkItems() throws YStateException;
    void reannounceWorkItem(YWorkItem workItem) throws YStateException;

    // Engine status
    String getLoadStatus(YSpecificationID specID);
    void setEngineStatus(YEngine.Status engineStatus);
    YEngine.Status getEngineStatus();

    // Utilities
    void storeObject(Object object) throws YPersistenceException;
    void dump();
    AnnouncementContext getAnnouncementContext();
    YAnnouncer getAnnouncer();
}
```

---

## Code Examples

### Java Client - Specification Management

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class InterfaceAClient {

    private static final String BASE_URL = "http://localhost:8080/yawl/ia";
    private final HttpClient client = HttpClient.newHttpClient();
    private String sessionHandle;

    public void connect(String userID, String password) throws Exception {
        String body = "action=connect&userID=%s&password=%s".formatted(userID, password);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        // Parse session handle from XML response
        sessionHandle = parseSessionHandle(response.body());
    }

    public void uploadSpecification(Path specFile) throws Exception {
        String specXML = Files.readString(specFile);

        String body = "action=upload&sessionHandle=%s&specXML=%s".formatted(
            sessionHandle,
            URLEncoder.encode(specXML, StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        System.out.println("Upload response: " + response.body());
    }

    public void createUserAccount(String userID, String password, String description)
            throws Exception {
        String body = "action=createAccount&userID=%s&password=%s&doco=%s&sessionHandle=%s"
            .formatted(userID, password, description, sessionHandle);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void disconnect() throws Exception {
        String body = "action=disconnect&sessionHandle=%s".formatted(sessionHandle);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
        sessionHandle = null;
    }

    private String parseSessionHandle(String xml) {
        // Simple extraction - use proper XML parser in production
        int start = xml.indexOf("<sessionHandle>") + 15;
        int end = xml.indexOf("</sessionHandle>");
        return xml.substring(start, end);
    }
}
```

### Python Client - Administration

```python
import requests
from typing import Optional, List, Dict
from urllib.parse import urlencode

class InterfaceAClient:
    BASE_URL = "http://localhost:8080/yawl/ia"

    def __init__(self):
        self.session_handle: Optional[str] = None
        self.session = requests.Session()

    def connect(self, user_id: str, password: str) -> bool:
        """Authenticate as administrator."""
        data = {
            "action": "connect",
            "userID": user_id,
            "password": password
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()

        # Parse session handle from XML
        if "<sessionHandle>" in response.text:
            start = response.text.find("<sessionHandle>") + 15
            end = response.text.find("</sessionHandle>")
            self.session_handle = response.text[start:end]
            return True
        return False

    def upload_specification(self, spec_xml: str) -> str:
        """Upload a workflow specification."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        data = {
            "action": "upload",
            "specXML": spec_xml,
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def get_specification_list(self) -> str:
        """Get list of loaded specifications."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        data = {
            "action": "getList",
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def create_account(self, user_id: str, password: str,
                       documentation: str = "") -> str:
        """Create a new user account."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        data = {
            "action": "createAccount",
            "userID": user_id,
            "password": password,
            "doco": documentation,
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def get_accounts(self) -> str:
        """Get all user accounts."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        data = {
            "action": "getAccounts",
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def register_service(self, uri: str, name: str,
                         documentation: str = "") -> str:
        """Register a YAWL service."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        service_xml = f"""<YAWLService>
            <uri>{uri}</uri>
            <name>{name}</name>
            <documentation>{documentation}</documentation>
        </YAWLService>"""

        data = {
            "action": "newYAWLService",
            "service": service_xml,
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def reannounce_enabled_work_items(self) -> str:
        """Re-announce all enabled work items."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        data = {
            "action": "reannounceEnabledWorkItems",
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def unload_specification(self, spec_identifier: str,
                             spec_version: str, spec_uri: str) -> str:
        """Unload a specification from the engine."""
        if not self.session_handle:
            raise RuntimeError("Not connected")

        data = {
            "action": "unload",
            "specidentifier": spec_identifier,
            "specversion": spec_version,
            "specuri": spec_uri,
            "sessionHandle": self.session_handle
        }
        response = self.session.post(self.BASE_URL, data=data)
        response.raise_for_status()
        return response.text

    def disconnect(self) -> None:
        """End the admin session."""
        if self.session_handle:
            data = {
                "action": "disconnect",
                "sessionHandle": self.session_handle
            }
            self.session.post(self.BASE_URL, data=data)
            self.session_handle = None
```

---

## Integration Patterns

### Specification Deployment Pipeline

```java
public class SpecificationDeployer {

    private final InterfaceAClient adminClient;
    private final InterfaceBClient workClient;

    public void deploySpecification(Path specFile, String version) throws Exception {
        // 1. Connect as administrator
        adminClient.connect("admin", "adminPassword");

        try {
            // 2. Validate specification XML
            String specXml = Files.readString(specFile);
            if (!validateSpecification(specXml)) {
                throw new IllegalArgumentException("Invalid specification");
            }

            // 3. Upload specification
            String uploadResult = adminClient.uploadSpecification(specXml);
            if (uploadResult.contains("<failure>")) {
                throw new RuntimeException("Upload failed: " + uploadResult);
            }

            // 4. Verify specification loaded
            String specList = adminClient.getSpecificationList();
            if (!specList.contains(version)) {
                throw new RuntimeException("Specification not loaded");
            }

            // 5. Optionally launch a test case
            // ...

            logger.info("Specification deployed successfully: {}", version);
        } finally {
            adminClient.disconnect();
        }
    }
}
```

### User Provisioning Service

```java
public class UserProvisioningService {

    private final InterfaceAClient adminClient;

    public void provisionUser(String userId, String email, String role) {
        adminClient.connect("admin", "adminPassword");

        try {
            // Generate random password
            String password = generateSecurePassword();

            // Create account
            String description = "%s - %s".formatted(role, email);
            adminClient.createUserAccount(userId, password, description);

            // Send welcome email with credentials
            sendWelcomeEmail(email, userId, password);

            logger.info("User provisioned: {}", userId);
        } finally {
            adminClient.disconnect();
        }
    }

    public void deprovisionUser(String userId) {
        adminClient.connect("admin", "adminPassword");

        try {
            adminClient.deleteAccount(userId);
            logger.info("User deprovisioned: {}", userId);
        } finally {
            adminClient.disconnect();
        }
    }
}
```

---

## Action Reference Summary

| Action | Purpose | Required Parameters |
|--------|---------|---------------------|
| `connect` | Authenticate admin session | `userID`, `password` |
| `disconnect` | End admin session | `sessionHandle` |
| `checkConnection` | Validate session | `sessionHandle` |
| `upload` | Load specification | `specXML`, `sessionHandle` |
| `unload` | Remove specification | `specidentifier`, `specversion`, `specuri`, `sessionHandle` |
| `getList` | List specifications | `sessionHandle` |
| `getAccounts` | List user accounts | `sessionHandle` |
| `getClientAccount` | Get user account | `userID`, `sessionHandle` |
| `createAccount` | Create user | `userID`, `password`, `sessionHandle` |
| `updateAccount` | Update user | `userID`, `sessionHandle` |
| `deleteAccount` | Delete user | `userID`, `sessionHandle` |
| `newPassword` | Change password | `password`, `sessionHandle` |
| `getPassword` | Get user password | `userID`, `sessionHandle` |
| `getYAWLServices` | List services | `sessionHandle` |
| `newYAWLService` | Register service | `service`, `sessionHandle` |
| `removeYAWLService` | Remove service | `serviceURI`, `sessionHandle` |
| `reannounceEnabledWorkItems` | Re-announce enabled | `sessionHandle` |
| `reannounceExecutingWorkItems` | Re-announce executing | `sessionHandle` |
| `reannounceFiredWorkItems` | Re-announce fired | `sessionHandle` |
| `reannounceWorkItem` | Re-announce item | `id`, `sessionHandle` |
| `getBuildProperties` | Build info | `sessionHandle` |
| `getExternalDBGateways` | DB gateways | `sessionHandle` |
| `setHibernateStatisticsEnabled` | Toggle stats | `enabled`, `sessionHandle` |
| `isHibernateStatisticsEnabled` | Check stats | `sessionHandle` |
| `getHibernateStatistics` | Get stats | `sessionHandle` |
| `promote` | Activate engine | `sessionHandle` |
| `demote` | Standby engine | `sessionHandle` |

---

## References

- **InterfaceA_EngineBasedServer**: `src/org/yawlfoundation/yawl/engine/interfce/interfaceA/InterfaceA_EngineBasedServer.java`
- **InterfaceAManagement**: `src/org/yawlfoundation/yawl/engine/interfce/interfaceA/InterfaceAManagement.java`
- **InterfaceADesign**: `src/org/yawlfoundation/yawl/engine/interfce/interfaceA/InterfaceADesign.java`
- **EngineGateway**: `src/org/yawlfoundation/yawl/engine/interfce/EngineGateway.java`
- **YEngine**: `src/org/yawlfoundation/yawl/engine/YEngine.java`

---

**Document Version**: 1.0
**YAWL Version**: 6.0.0
**Last Updated**: 2026-02-19
