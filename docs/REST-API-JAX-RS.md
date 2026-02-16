# YAWL REST API with JAX-RS

## Overview

YAWL v5.2 now includes modern REST APIs using Jakarta JAX-RS 3.1 (Jakarta EE 10). These provide cleaner, annotation-based resource definitions alongside the legacy servlet-based interfaces.

## Architecture

### Technology Stack

- **Jakarta RESTful Web Services (JAX-RS)** 3.1.0
- **Jersey** 3.1.5 (Reference Implementation)
- **HK2** 3.0.5 (Dependency Injection)
- **Jackson** 2.18.2 (JSON Processing)

### Dependencies Added

All JAX-RS dependencies are located in `/home/user/yawl/build/3rdParty/lib/`:

```
jakarta.ws.rs-api-3.1.0.jar
jersey-server-3.1.5.jar
jersey-client-3.1.5.jar
jersey-common-3.1.5.jar
jersey-container-servlet-3.1.5.jar
jersey-hk2-3.1.5.jar
jersey-media-json-jackson-3.1.5.jar
hk2-locator-3.0.5.jar
hk2-api-3.0.5.jar
hk2-utils-3.0.5.jar
```

## API Endpoints

All REST endpoints are mounted under `/api`:

### Interface B (Client API) - **FULLY IMPLEMENTED**

Base path: `/api/ib`

#### Session Management

- **POST** `/api/ib/connect` - Connect to engine
  ```json
  Request: {"userid": "admin", "password": "YAWL"}
  Response: {"sessionHandle": "abc123..."}
  ```

- **POST** `/api/ib/disconnect?sessionHandle={handle}` - Disconnect session
  ```json
  Response: {"status": "disconnected"}
  ```

#### Work Item Operations

- **GET** `/api/ib/workitems?sessionHandle={handle}` - Get all live work items
  ```json
  Response: [
    {"id": "item1", "status": "enabled", "caseID": "case1", ...},
    ...
  ]
  ```

- **GET** `/api/ib/workitems/{itemId}?sessionHandle={handle}` - Get specific work item
  ```xml
  Response: <workItem><data>...</data></workItem>
  ```

- **POST** `/api/ib/workitems/{itemId}/checkout?sessionHandle={handle}` - Checkout work item
  ```xml
  Response: <data>...</data>
  ```

- **POST** `/api/ib/workitems/{itemId}/checkin?sessionHandle={handle}` - Check in work item
  ```xml
  Request (Content-Type: application/xml): <data>...</data>
  Response: {"status": "checked-in"}
  ```

- **POST** `/api/ib/workitems/{itemId}/complete?sessionHandle={handle}` - Complete work item
  ```xml
  Request (Content-Type: application/xml): <data>...</data>
  Response: {"status": "completed"}
  ```

#### Case Operations

- **GET** `/api/ib/cases/{caseId}?sessionHandle={handle}` - Get case data
  ```xml
  Response: <case><data>...</data></case>
  ```

- **GET** `/api/ib/cases/{caseId}/workitems?sessionHandle={handle}` - Get work items for case
  ```json
  Response: [{"id": "item1", ...}, ...]
  ```

- **POST** `/api/ib/cases/{caseId}/cancel?sessionHandle={handle}` - Cancel case
  ```json
  Response: {"status": "cancelled"}
  ```

### Interface A (Design API) - **STUB**

Base path: `/api/ia`

Currently throws `UnsupportedOperationException`. Use legacy servlet: `/yawl/ia`

Planned endpoints:
- **POST** `/api/ia/specifications` - Upload specification
- **GET** `/api/ia/specifications` - Get all specifications
- **DELETE** `/api/ia/specifications/{specId}` - Unload specification

### Interface E (Events API) - **STUB**

Base path: `/api/ie`

Currently throws `UnsupportedOperationException`. Use legacy servlet: `/yawl/ie`

Planned endpoints:
- **POST** `/api/ie/subscriptions` - Subscribe to events
- **GET** `/api/ie/subscriptions` - Get subscriptions
- **DELETE** `/api/ie/subscriptions/{id}` - Unsubscribe

### Interface X (Extended API) - **STUB**

Base path: `/api/ix`

Currently throws `UnsupportedOperationException`. Use legacy servlet: `/yawl/ix`

Planned endpoints:
- **POST** `/api/ix/workitems/{itemId}/exceptions` - Handle exception
- **POST** `/api/ix/workitems/{itemId}/suspend` - Suspend work item
- **POST** `/api/ix/workitems/{itemId}/resume` - Resume work item

## Implementation

### Package Structure

```
org.yawlfoundation.yawl.engine.interfce.rest/
├── YawlRestApplication.java          # JAX-RS application config (@ApplicationPath)
├── InterfaceBRestResource.java       # Interface B endpoints (IMPLEMENTED)
├── InterfaceARestResource.java       # Interface A endpoints (stub)
├── InterfaceERestResource.java       # Interface E endpoints (stub)
├── InterfaceXRestResource.java       # Interface X endpoints (stub)
├── YawlExceptionMapper.java          # Exception → JSON error mapper
└── package-info.java                 # Package documentation
```

### Key Classes

#### YawlRestApplication
```java
@ApplicationPath("/api")
public class YawlRestApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        // Registers all REST resources
    }
}
```

#### InterfaceBRestResource
```java
@Path("/ib")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InterfaceBRestResource {
    private final InterfaceBInterop _ibInterop;

    @POST
    @Path("/connect")
    public Response connect(String credentials) {
        // Real implementation using InterfaceBInterop
    }
}
```

#### YawlExceptionMapper
```java
@Provider
public class YawlExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        // Maps exceptions to JSON error responses
    }
}
```

## Configuration

### build.xml Changes

Added JAX-RS dependencies to compilation classpath:

```xml
<!-- JAX-RS / Jakarta REST -->
<pathelement location="${lib.dir}/${jakarta.ws.rs.api}"/>
<pathelement location="${lib.dir}/${jersey.server}"/>
<pathelement location="${lib.dir}/${jersey.client}"/>
<pathelement location="${lib.dir}/${jersey.common}"/>
<pathelement location="${lib.dir}/${jersey.servlet}"/>
<pathelement location="${lib.dir}/${jersey.hk2}"/>
<pathelement location="${lib.dir}/${jersey.jackson}"/>
<pathelement location="${lib.dir}/${hk2.locator}"/>
<pathelement location="${lib.dir}/${hk2.api}"/>
<pathelement location="${lib.dir}/${hk2.utils}"/>
```

### web.xml Configuration

To enable JAX-RS in a YAWL web application, add to `web.xml`:

```xml
<servlet>
    <servlet-name>JerseyServlet</servlet-name>
    <servlet-class>org.glassfish.jersey.servlet.ServletContainer</servlet-class>
    <init-param>
        <param-name>jakarta.ws.rs.Application</param-name>
        <param-value>org.yawlfoundation.yawl.engine.interfce.rest.YawlRestApplication</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
</servlet>

<servlet-mapping>
    <servlet-name>JerseyServlet</servlet-name>
    <url-pattern>/api/*</url-pattern>
</servlet-mapping>
```

## Migration Strategy

### Backward Compatibility

Legacy servlet-based interfaces remain functional:
- Legacy: `/yawl/ib`, `/yawl/ia`, `/yawl/ie`, `/yawl/ix`
- New: `/yawl/api/ib`, `/yawl/api/ia`, `/yawl/api/ie`, `/yawl/api/ix`

### Gradual Migration

1. **Phase 1** (Current): Interface B fully implemented via JAX-RS
2. **Phase 2** (Future): Implement Interface A endpoints
3. **Phase 3** (Future): Implement Interface E endpoints
4. **Phase 4** (Future): Implement Interface X endpoints
5. **Phase 5** (Future): Deprecate legacy servlets

## Usage Examples

### cURL Examples

#### Connect to Engine
```bash
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"userid":"admin","password":"YAWL"}'
```

#### Get Work Items
```bash
curl -X GET "http://localhost:8080/yawl/api/ib/workitems?sessionHandle=abc123"
```

#### Checkout Work Item
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/item-123/checkout?sessionHandle=abc123"
```

#### Complete Work Item
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/item-123/complete?sessionHandle=abc123" \
  -H "Content-Type: application/xml" \
  -d '<data><field1>value1</field1></data>'
```

### Java Client Example

```java
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;

Client client = ClientBuilder.newClient();
WebTarget target = client.target("http://localhost:8080/yawl/api/ib");

// Connect
Response connectResp = target.path("connect")
    .request(MediaType.APPLICATION_JSON)
    .post(Entity.json("{\"userid\":\"admin\",\"password\":\"YAWL\"}"));
String sessionHandle = connectResp.readEntity(Map.class).get("sessionHandle");

// Get work items
Response itemsResp = target.path("workitems")
    .queryParam("sessionHandle", sessionHandle)
    .request(MediaType.APPLICATION_JSON)
    .get();
List<WorkItemRecord> items = itemsResp.readEntity(new GenericType<List<WorkItemRecord>>(){});

// Checkout work item
Response checkoutResp = target.path("workitems/{itemId}/checkout")
    .resolveTemplate("itemId", "item-123")
    .queryParam("sessionHandle", sessionHandle)
    .request(MediaType.APPLICATION_XML)
    .post(Entity.text(""));
String workItemData = checkoutResp.readEntity(String.class);

// Complete work item
String completionData = "<data><field1>value1</field1></data>";
Response completeResp = target.path("workitems/{itemId}/complete")
    .resolveTemplate("itemId", "item-123")
    .queryParam("sessionHandle", sessionHandle)
    .request(MediaType.APPLICATION_JSON)
    .post(Entity.xml(completionData));
```

## Error Handling

All errors return JSON with consistent format:

```json
{
  "error": "ErrorType",
  "message": "Detailed error message"
}
```

HTTP Status Codes:
- **200 OK** - Success
- **400 Bad Request** - Invalid parameters
- **401 Unauthorized** - Invalid session
- **404 Not Found** - Resource not found
- **500 Internal Server Error** - Server error

## Testing

### Integration Tests

```bash
# Connect
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"userid":"admin","password":"YAWL"}'

# Expected: {"sessionHandle":"..."}
```

### Unit Tests

Create JUnit tests for each REST resource:

```java
@Test
public void testConnect() {
    InterfaceBRestResource resource = new InterfaceBRestResource();
    Response response = resource.connect("{\"userid\":\"admin\",\"password\":\"YAWL\"}");
    assertEquals(200, response.getStatus());
}
```

## Future Enhancements

1. **OpenAPI/Swagger Documentation** - Auto-generate API docs
2. **OAuth2 Authentication** - Replace session handles with JWT tokens
3. **Rate Limiting** - Prevent API abuse
4. **WebSockets** - Real-time event notifications
5. **GraphQL Support** - Alternative to REST for complex queries
6. **API Versioning** - `/api/v1/ib`, `/api/v2/ib`

## References

- [Jakarta RESTful Web Services](https://jakarta.ee/specifications/restful-ws/3.1/)
- [Jersey Documentation](https://eclipse-ee4j.github.io/jersey/)
- [JAX-RS Tutorial](https://docs.oracle.com/javaee/7/tutorial/jaxrs.htm)

## Authors

- Michael Adams - YAWL Foundation
- Implementation Date: February 16, 2026
- YAWL Version: 5.2

## License

YAWL is licensed under the GNU Lesser General Public License v3.0.
