# YAWL REST API Configuration Guide

## Overview

The YAWL Engine v5.2 includes modern JAX-RS REST APIs that provide JSON-based HTTP endpoints for all YAWL interfaces. This guide explains how the REST API is configured in web.xml files and deployed.

## Architecture

The REST API is built using:
- **JAX-RS 3.1** (Jakarta RESTful Web Services)
- **Jersey 3.1.5** (JAX-RS implementation)
- **Jackson 2.18.2** (JSON serialization)
- **Jakarta Servlet API 6.0**

## Web.xml Configuration

### 1. Main Engine Configuration

File: `/home/user/yawl/build/engine/web.xml`

#### Jersey Servlet

The Jersey servlet container hosts all REST resources:

```xml
<servlet>
    <servlet-name>JerseyServlet</servlet-name>
    <description>
        JAX-RS servlet for modern REST API endpoints.
        Provides JSON-based REST APIs for all YAWL interfaces (A, B, E, X).
    </description>
    <servlet-class>org.glassfish.jersey.servlet.ServletContainer</servlet-class>
    <init-param>
        <param-name>jakarta.ws.rs.Application</param-name>
        <param-value>org.yawlfoundation.yawl.engine.interfce.rest.YawlRestApplication</param-value>
    </init-param>
    <init-param>
        <param-name>jersey.config.server.provider.packages</param-name>
        <param-value>org.yawlfoundation.yawl.engine.interfce.rest</param-value>
    </init-param>
    <load-on-startup>5</load-on-startup>
</servlet>

<servlet-mapping>
    <servlet-name>JerseyServlet</servlet-name>
    <url-pattern>/api/*</url-pattern>
</servlet-mapping>
```

**Configuration Parameters:**

- `jakarta.ws.rs.Application`: Points to the JAX-RS Application class that registers all REST resources
- `jersey.config.server.provider.packages`: Package to scan for REST resources
- `load-on-startup`: 5 (loads after Interface A, B, X, E servlets)
- `url-pattern`: `/api/*` (all REST endpoints are under /api)

#### CORS Filter

Enables cross-origin requests for web browser access:

```xml
<filter>
    <filter-name>CorsFilter</filter-name>
    <filter-class>org.yawlfoundation.yawl.engine.interfce.rest.CorsFilter</filter-class>
</filter>

<filter-mapping>
    <filter-name>CorsFilter</filter-name>
    <url-pattern>/api/*</url-pattern>
</filter-mapping>
```

**Features:**
- Allows cross-origin requests from any domain (configurable)
- Supports standard HTTP methods (GET, POST, PUT, DELETE, OPTIONS)
- Handles preflight OPTIONS requests
- Configurable via init parameters

### 2. CORS Configuration Options

The CORS filter supports optional configuration via init parameters:

```xml
<filter>
    <filter-name>CorsFilter</filter-name>
    <filter-class>org.yawlfoundation.yawl.engine.interfce.rest.CorsFilter</filter-class>
    <init-param>
        <param-name>allowedOrigins</param-name>
        <param-value>https://app.example.com,https://admin.example.com</param-value>
    </init-param>
    <init-param>
        <param-name>allowedMethods</param-name>
        <param-value>GET, POST, PUT, DELETE, OPTIONS</param-value>
    </init-param>
    <init-param>
        <param-name>allowedHeaders</param-name>
        <param-value>Content-Type, Authorization, X-Session-Handle</param-value>
    </init-param>
    <init-param>
        <param-name>allowCredentials</param-name>
        <param-value>true</param-value>
    </init-param>
</filter>
```

**Default Values (if not configured):**
- `allowedOrigins`: `*` (any origin - for development)
- `allowedMethods`: `GET, POST, PUT, DELETE, OPTIONS, HEAD`
- `allowedHeaders`: `Content-Type, Authorization, X-Session-Handle, X-Requested-With`
- `exposedHeaders`: `X-Session-Handle, Location`
- `maxAge`: `3600` (1 hour preflight cache)
- `allowCredentials`: `true`

**Production Recommendation:** Always configure specific allowed origins in production to restrict access to trusted domains.

## REST Resource Classes

The REST API is implemented in the following classes:

| Class | Path | Description |
|-------|------|-------------|
| `YawlRestApplication` | `/api` | JAX-RS Application configuration |
| `InterfaceBRestResource` | `/api/ib` | Client API (work items, cases) |
| `InterfaceARestResource` | `/api/ia` | Design API (specifications) |
| `InterfaceERestResource` | `/api/ie` | Events API (subscriptions, logs) |
| `InterfaceXRestResource` | `/api/ix` | Extended API (exceptions, status) |
| `YawlExceptionMapper` | - | Exception to HTTP response mapper |
| `CorsFilter` | - | CORS support filter |

All classes are in package: `org.yawlfoundation.yawl.engine.interfce.rest`

## URL Mapping Structure

### Legacy Servlet Interfaces

The existing servlet-based interfaces remain unchanged:

- `/yawl/ib/*` - Interface B Servlet (InterfaceB_EngineBasedServer)
- `/yawl/ia/*` - Interface A Servlet (InterfaceA_EngineBasedServer)
- `/yawl/ix/*` - Interface X Servlet (InterfaceX_EngineSideServer)
- `/yawl/logGateway` - Interface E Gateway (YLogGateway)

### New REST API Endpoints

The REST API adds modern JSON endpoints under `/api`:

- `/yawl/api/ib/*` - Interface B REST (work items, cases)
- `/yawl/api/ia/*` - Interface A REST (specifications)
- `/yawl/api/ie/*` - Interface E REST (events, subscriptions)
- `/yawl/api/ix/*` - Interface X REST (exceptions, monitoring)

**Coexistence:** Both interfaces are available simultaneously. Legacy integrations continue to work while new applications can use the REST API.

## Deployment

### WAR File Structure

When the engine WAR is built, it includes:

```
yawl.war
├── WEB-INF/
│   ├── web.xml                    # Servlet configuration
│   ├── classes/
│   │   └── org/yawlfoundation/yawl/
│   │       └── engine/interfce/rest/
│   │           ├── YawlRestApplication.class
│   │           ├── InterfaceBRestResource.class
│   │           ├── InterfaceARestResource.class
│   │           ├── InterfaceERestResource.class
│   │           ├── InterfaceXRestResource.class
│   │           ├── YawlExceptionMapper.class
│   │           └── CorsFilter.class
│   └── lib/
│       ├── jersey-*.jar
│       ├── jakarta.ws.rs-api-*.jar
│       ├── jakarta.servlet-api-*.jar
│       └── jackson-*.jar
├── api-docs.html                  # REST API documentation
└── (other web resources)
```

### Build Process

The web.xml configuration is applied during the build:

```bash
# Full build (includes web.xml in WAR)
ant buildAll

# Deploy to Tomcat
cp output/yawl.war $CATALINA_HOME/webapps/
```

The `buildAll` target:
1. Compiles all Java sources (including REST classes)
2. Copies web.xml from `/build/engine/web.xml` to WAR
3. Packages JAX-RS dependencies into WEB-INF/lib
4. Creates deployable yawl.war in output/

### Tomcat Deployment

After deployment to Tomcat, the REST API is available at:

```
http://localhost:8080/yawl/api/
```

Verify deployment:

```bash
# Check API documentation
curl http://localhost:8080/yawl/api-docs.html

# Test REST endpoint (should return 401 or JSON, not 404)
curl -X GET http://localhost:8080/yawl/api/ib/workitems
```

## Testing Configuration

### 1. Verify web.xml Syntax

```bash
xmllint --noout /home/user/yawl/build/engine/web.xml
```

### 2. Validate Against Servlet Schema

```bash
xmllint --schema https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd \
        /home/user/yawl/build/engine/web.xml --noout
```

### 3. Test REST API

```bash
# Connect to engine
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"userid":"admin","password":"YAWL"}'

# Expected response:
# {"sessionHandle":"S-xxx","userid":"admin","expiry":"..."}

# List work items
curl -X GET "http://localhost:8080/yawl/api/ib/workitems?sessionHandle=S-xxx"
```

### 4. Test CORS

```bash
# Preflight request
curl -X OPTIONS http://localhost:8080/yawl/api/ib/workitems \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: GET" \
  -v

# Should see CORS headers in response:
# Access-Control-Allow-Origin: http://localhost:3000
# Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
```

## Security Considerations

### 1. Production CORS Configuration

For production, always restrict allowed origins:

```xml
<init-param>
    <param-name>allowedOrigins</param-name>
    <param-value>https://workflow.example.com,https://admin.example.com</param-value>
</init-param>
```

### 2. Authentication

The REST API uses the same session-based authentication as legacy interfaces:

1. POST `/api/ib/connect` with credentials
2. Receive session handle in response
3. Include session handle in all subsequent requests

Session handles are validated by the engine and expire based on configuration.

### 3. HTTPS

For production deployments, always use HTTPS:

```xml
<security-constraint>
    <web-resource-collection>
        <web-resource-name>REST API</web-resource-name>
        <url-pattern>/api/*</url-pattern>
    </web-resource-collection>
    <user-data-constraint>
        <transport-guarantee>CONFIDENTIAL</transport-guarantee>
    </user-data-constraint>
</security-constraint>
```

### 4. Rate Limiting

Consider adding rate limiting for public-facing APIs using servlet filters or reverse proxy (nginx, Apache).

## Troubleshooting

### 404 Not Found on /api/* endpoints

**Cause:** Jersey servlet not loaded or URL pattern not mapped.

**Solution:**
1. Check web.xml has Jersey servlet definition
2. Verify servlet-mapping for `/api/*`
3. Check Tomcat logs for servlet initialization errors
4. Ensure Jersey JARs are in WEB-INF/lib

### CORS Errors in Browser

**Cause:** CORS filter not configured or origins not allowed.

**Solution:**
1. Check browser console for specific CORS error
2. Verify CorsFilter is mapped to `/api/*`
3. Check allowed origins in filter configuration
4. Test with browser DevTools Network tab

### 500 Internal Server Error

**Cause:** REST resource class initialization failure.

**Solution:**
1. Check Tomcat logs for stack traces
2. Verify all dependencies are in classpath
3. Check YawlRestApplication.getClasses() returns valid classes
4. Ensure engine is properly initialized before REST calls

### No JSON Response

**Cause:** Jackson JSON provider not registered.

**Solution:**
1. Ensure Jackson JARs are in WEB-INF/lib
2. Jersey should auto-discover Jackson provider
3. Check for JSON provider registration errors in logs

## API Documentation

An HTML documentation page is available at:

```
http://localhost:8080/yawl/api-docs.html
```

This page provides:
- Complete endpoint reference for all interfaces
- Request/response examples
- Authentication guide
- cURL examples
- Error handling information

File location: `/home/user/yawl/build/engine/api-docs.html`

## Migration from Legacy to REST

### Comparison Table

| Feature | Legacy Servlet | REST API |
|---------|---------------|----------|
| Protocol | HTTP POST with form parameters | HTTP with JSON |
| Content Type | application/x-www-form-urlencoded | application/json |
| Authentication | sessionHandle parameter | Query param or header |
| Response Format | XML or plain text | JSON |
| Error Handling | XML error messages | HTTP status codes + JSON |
| Documentation | Javadoc | OpenAPI/Swagger ready |

### Example Migration

**Legacy Interface B (checkout work item):**
```bash
curl -X POST http://localhost:8080/yawl/ib \
  -d "action=checkOut" \
  -d "workItemID=1234" \
  -d "sessionHandle=S-xxx"
```

**REST API (checkout work item):**
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/1234/checkout?sessionHandle=S-xxx" \
  -H "Content-Type: application/json"
```

**Benefits:**
- More intuitive URL structure
- Standard HTTP methods (GET, POST, PUT, DELETE)
- JSON responses easier to parse
- Better error handling with HTTP status codes
- CORS support for browser clients

## References

- [Jakarta Servlet 6.0 Specification](https://jakarta.ee/specifications/servlet/6.0/)
- [JAX-RS 3.1 Specification](https://jakarta.ee/specifications/restful-ws/3.1/)
- [Jersey 3.x Documentation](https://eclipse-ee4j.github.io/jersey/)
- [YAWL Manual](http://yawlfoundation.org/manual)
- [REST API Documentation](http://localhost:8080/yawl/api-docs.html)

## Change History

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-02-16 | 5.2 | Agent a757323 + Configuration Agent | Initial REST API implementation and web.xml configuration |

---

**YAWL Foundation** | Copyright 2004-2026 | [yawlfoundation.org](http://yawlfoundation.org)
