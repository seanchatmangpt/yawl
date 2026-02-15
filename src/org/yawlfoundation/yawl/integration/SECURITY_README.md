# YAWL Security Manager - MCP & A2A Integration

Enterprise-grade security implementation for YAWL's Model Context Protocol (MCP) and Agent-to-Agent (A2A) integration.

## Overview

The YAWL Security Manager provides comprehensive authentication, authorization, and security features for external clients and agents accessing the YAWL workflow engine through MCP and A2A protocols.

## Components

### 1. YawlSecurityManager (`YawlSecurityManager.java`)

Core security manager implementing all security features:

- **API Key Authentication** - Secure token-based client authentication
- **JWT Token Management** - Generation and validation of JSON Web Tokens
- **Session Management** - Integration with YAWL's existing session infrastructure
- **Role-Based Access Control (RBAC)** - Fine-grained permission checking
- **Encryption** - AES-256-GCM for sensitive data protection
- **Request Signing** - HMAC-SHA256 for tamper protection
- **Rate Limiting** - Per-client request throttling
- **Audit Logging** - Comprehensive security event tracking
- **TLS/SSL Configuration** - Secure transport layer setup

### 2. YawlMcpSecurityMiddleware (`mcp/YawlMcpSecurityMiddleware.java`)

Security layer for MCP server integration:

- Authenticates MCP clients using API keys
- Authorizes MCP tool calls based on RBAC
- Maps MCP tools to YAWL permissions
- Validates request signatures
- Enforces rate limits

### 3. YawlA2ASecurityMiddleware (`a2a/YawlA2ASecurityMiddleware.java`)

Security layer for A2A server integration:

- Authenticates A2A agents using API keys or JWT
- Authorizes A2A operations based on RBAC
- Signs and verifies agent requests/responses
- Encrypts/decrypts sensitive data in transit
- Enforces rate limits per agent

## Security Features

### API Key Authentication

API keys provide the primary authentication mechanism:

```java
YawlSecurityManager security = new YawlSecurityManager();

// Create API key for a client
Set<String> roles = new HashSet<>(Arrays.asList("operator", "designer"));
String apiKey = security.createApiKey("client-123", roles, 365); // Expires in 365 days

// Store this key securely and provide to the client
// Client uses it to authenticate:
String sessionHandle = security.authenticateWithApiKey(apiKey);
```

**Features:**
- Cryptographically secure random generation
- SHA-256 hashing for storage
- Expiration support
- Role assignment
- Revocation capability

### JWT Token Generation

JWT tokens enable stateless authentication after initial login:

```java
// Generate JWT after API key authentication
JwtToken jwt = security.generateJwtToken(sessionHandle);
String tokenString = jwt.getToken();

// Client includes token in subsequent requests
// Validate token:
JwtToken validated = security.validateJwtToken(tokenString);
if (validated != null && !validated.isExpired()) {
    String subject = validated.getSubject();
    Set<String> roles = validated.getRoles();
    // Process request
}
```

**Features:**
- HMAC-SHA256 signing
- Configurable expiration (default: 1 hour)
- Embedded role information
- Session binding

### Role-Based Access Control (RBAC)

Four predefined roles with granular permissions:

| Role | Permissions |
|------|-------------|
| **admin** | All permissions (complete system access) |
| **operator** | Launch cases, execute tasks, view status |
| **designer** | Upload/unload specs, view cases and specs |
| **viewer** | View specs, cases, and task data (read-only) |

**Available Permissions:**
- `LAUNCH_CASE` - Launch new workflow instances
- `GET_CASE_STATUS` - Query case execution state
- `CANCEL_CASE` - Terminate running cases
- `UPLOAD_SPEC` - Deploy new workflow specifications
- `UNLOAD_SPEC` - Remove workflow specifications
- `LIST_SPECS` - List available specifications
- `LIST_CASES` - List running cases
- `EXECUTE_TASK` - Execute workflow tasks
- `VIEW_TASK_DATA` - View task input/output data
- `MANAGE_USERS` - Manage user accounts
- `MANAGE_ROLES` - Manage roles and permissions
- `VIEW_AUDIT_LOG` - Access security audit logs

**Permission Checking:**
```java
if (security.checkPermission(sessionHandle, Permission.LAUNCH_CASE)) {
    // Client has permission to launch cases
}

if (security.checkCaseAccess(sessionHandle, caseId)) {
    // Client can access this specific case
}
```

### Request Signing & Verification

HMAC-SHA256 request signing prevents tampering:

```java
// Sign a request (server-side)
String payload = "{\"operation\":\"launch_case\",\"data\":\"...\"}";
String signature = security.signRequest(payload, secretKey);

// Client includes signature in headers
// Server verifies:
boolean valid = security.verifyRequestSignature(payload, signature, secretKey);
if (valid) {
    // Process request
}
```

### Data Encryption

AES-256-GCM encryption for sensitive data:

```java
// Encrypt sensitive credentials
String encryptedData = security.encryptData(plaintext, password);

// Decrypt on the other end
String plaintext = security.decryptData(encryptedData, password);
```

**Features:**
- AES-256-GCM authenticated encryption
- PBKDF2 key derivation (65536 iterations)
- Random IV per encryption
- Integrity protection via GCM tags

### Rate Limiting

Automatic per-client rate limiting:

```java
if (security.checkRateLimit(clientId)) {
    // Allow request
} else {
    // Reject - rate limit exceeded
}
```

**Default Limits:**
- 60 requests per minute per client
- Sliding window algorithm
- Automatic cleanup of expired timestamps

### TLS/SSL Configuration

Secure transport layer configuration:

```java
// Apply TLS configuration from environment variables
security.applyTlsConfiguration();

// Configuration reads from:
// - YAWL_KEYSTORE_PATH
// - YAWL_KEYSTORE_PASSWORD
// - YAWL_TRUSTSTORE_PATH
// - YAWL_TRUSTSTORE_PASSWORD

// Enforces TLS 1.2 and 1.3 only
```

### Audit Logging

All security events are logged to YAWL's audit database:

- Authentication successes/failures
- Authorization decisions
- API key creation/revocation
- JWT generation/validation
- Rate limit violations
- Session creation/expiration

Audit events integrate with YAWL's existing `YAuditEvent` infrastructure and are persisted via Hibernate.

## Integration Examples

### MCP Server with Security

```java
// Initialize MCP server with security
YawlMcpSecurityMiddleware security = new YawlMcpSecurityMiddleware();

// Authenticate incoming MCP client
String apiKey = extractApiKeyFromRequest(request);
String sessionHandle = security.authenticate(apiKey);

if (sessionHandle == null) {
    return errorResponse("Authentication failed");
}

// Authorize tool call
String toolName = "launch_case";
String caseId = extractCaseIdFromRequest(request);

if (!security.authorizeToolCall(sessionHandle, toolName, caseId)) {
    return errorResponse("Authorization failed");
}

// Execute tool (authorized)
String result = executeTool(toolName, request);
return successResponse(result);
```

### A2A Server with Security

```java
// Initialize A2A server with security
YawlA2ASecurityMiddleware security = new YawlA2ASecurityMiddleware();
security.setRequireSignedRequests(true); // Enforce request signing

// Authenticate agent
String agentId = extractAgentIdFromRequest(request);
String apiKey = extractApiKeyFromRequest(request);
String sessionHandle = security.authenticateAgent(apiKey, agentId);

if (sessionHandle == null) {
    return errorResponse("Agent authentication failed");
}

// Verify request signature
String payload = extractPayload(request);
String signature = extractSignature(request);

if (!security.verifySignedRequest(payload, signature, sessionHandle)) {
    return errorResponse("Invalid request signature");
}

// Authorize operation
String operation = "workflow.launch";
if (!security.authorizeOperation(sessionHandle, operation)) {
    return errorResponse("Operation not authorized");
}

// Execute operation (authorized)
String result = executeOperation(operation, payload);

// Sign response
String responsePayload = createResponsePayload(result);
String responseSignature = security.signResponse(responsePayload, sessionHandle);

return signedResponse(responsePayload, responseSignature);
```

## Environment Configuration

### Required Environment Variables

None required - system works with defaults. Optional configuration:

```bash
# YAWL Engine Connection (already configured)
export YAWL_ENGINE_URL="http://localhost:8080/yawl"
export YAWL_USERNAME="admin"
export YAWL_PASSWORD="YAWL"

# JWT Configuration
export YAWL_JWT_SECRET="your-secret-key-here"  # Random if not set

# Request Signing
export YAWL_MCP_SIGNING_KEY="mcp-signing-secret"
export YAWL_A2A_SIGNING_KEY="a2a-signing-secret"

# Data Encryption
export YAWL_A2A_ENCRYPTION_KEY="encryption-secret"

# TLS/SSL Configuration
export YAWL_KEYSTORE_PATH="/path/to/keystore.jks"
export YAWL_KEYSTORE_PASSWORD="keystore-password"
export YAWL_TRUSTSTORE_PATH="/path/to/truststore.jks"
export YAWL_TRUSTSTORE_PASSWORD="truststore-password"
```

### TLS/SSL Certificate Setup

For production deployment with TLS:

```bash
# Generate keystore with self-signed certificate (development)
keytool -genkeypair -alias yawl \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -keystore keystore.jks \
  -storepass changeit \
  -dname "CN=localhost,OU=YAWL,O=YAWL Foundation,C=AU"

# Export certificate
keytool -exportcert -alias yawl \
  -keystore keystore.jks \
  -storepass changeit \
  -file yawl-cert.pem

# Create truststore
keytool -importcert -alias yawl \
  -file yawl-cert.pem \
  -keystore truststore.jks \
  -storepass changeit \
  -noprompt

# Configure YAWL
export YAWL_KEYSTORE_PATH="$(pwd)/keystore.jks"
export YAWL_KEYSTORE_PASSWORD="changeit"
export YAWL_TRUSTSTORE_PATH="$(pwd)/truststore.jks"
export YAWL_TRUSTSTORE_PASSWORD="changeit"
```

## Client Setup Guide

### Creating API Keys for Clients

```java
// As admin, create API key for a new client
YawlSecurityManager security = new YawlSecurityManager();

// First, create the client in YAWL engine
YExternalClient client = new YExternalClient("client-123", hashedPassword, "MCP Client");
YEngine.getInstance().addClient(client);

// Create API key with appropriate roles
Set<String> roles = new HashSet<>(Arrays.asList("operator"));
String apiKey = security.createApiKey("client-123", roles, 365);

// Securely provide this API key to the client
// Store it in their configuration or environment variables
System.out.println("API Key for client-123: " + apiKey);
// Example: "kJ8h3Dk2mN9pL4qR7sT1vW6xY0zA5bC8eD3fG9hI2jK4lM7nO1pQ6rS8tU0vX3yZ"
```

### Client-Side API Key Usage (MCP)

```bash
# Client stores API key in environment
export YAWL_API_KEY="kJ8h3Dk2mN9pL4qR7sT1vW6xY0zA5bC8eD3fG9hI2jK4lM7nO1pQ6rS8tU0vX3yZ"

# Client connects to MCP server
curl -X POST http://localhost:3000/mcp \
  -H "Authorization: Bearer $YAWL_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

### Client-Side API Key Usage (A2A)

```java
// A2A agent configuration
String apiKey = System.getenv("YAWL_API_KEY");

// Authenticate with YAWL A2A server
HttpClient client = HttpClient.newHttpClient();
HttpRequest authRequest = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/a2a/authenticate"))
    .header("X-API-Key", apiKey)
    .POST(HttpRequest.BodyPublishers.noBody())
    .build();

HttpResponse<String> authResponse = client.send(authRequest,
    HttpResponse.BodyHandlers.ofString());

// Extract session handle or JWT token from response
String token = parseTokenFromResponse(authResponse.body());

// Use token for subsequent requests
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/a2a/workflow/launch"))
    .header("Authorization", "Bearer " + token)
    .POST(HttpRequest.BodyPublishers.ofString(payload))
    .build();
```

## Security Best Practices

### Production Deployment Checklist

- [ ] **Enable security** - Never disable in production
- [ ] **Configure TLS** - Use valid certificates, not self-signed
- [ ] **Set JWT secret** - Use strong random secret (32+ chars)
- [ ] **Configure signing keys** - Unique secrets for MCP and A2A
- [ ] **Enable request signing** - Set `requireSignedRequests=true` for A2A
- [ ] **Review rate limits** - Adjust for expected load
- [ ] **Monitor audit logs** - Set up alerts for suspicious activity
- [ ] **Rotate API keys** - Establish key rotation policy
- [ ] **Principle of least privilege** - Assign minimal required roles
- [ ] **Secure key storage** - Use vault/secrets manager for API keys

### Key Rotation

```java
// Revoke old API key
security.revokeApiKey("client-123");

// Generate new API key
String newApiKey = security.createApiKey("client-123", roles, 365);

// Notify client to update their configuration
notifyClient("client-123", newApiKey);
```

### Monitoring and Alerts

Monitor these security events in audit logs:

- Failed authentication attempts (potential brute force)
- Rate limit violations (potential DoS)
- Permission denied events (potential privilege escalation)
- Invalid signatures (potential tampering)
- Expired token usage (client misconfiguration)

## Architecture Integration

### Integration with Existing YAWL Components

The security manager integrates seamlessly with YAWL's existing infrastructure:

1. **YSessionCache** - Leverages YAWL's session management
2. **YExternalClient** - Uses existing client authentication
3. **YAuditEvent** - Writes to YAWL's audit log
4. **HibernateEngine** - Persists security events
5. **UserPrivileges** - Can extend for fine-grained permissions

### Security Flow Diagram

```
┌─────────────┐
│  MCP/A2A    │
│   Client    │
└─────┬───────┘
      │
      │ 1. Present API Key
      ▼
┌─────────────────────────────────────────┐
│  YawlSecurityManager                    │
│  ┌───────────────────────────────────┐  │
│  │ authenticateWithApiKey()          │  │
│  │  - Hash API key                   │  │
│  │  - Lookup in apiKeys map          │  │
│  │  - Validate expiration            │  │
│  │  - Create YSession via YEngine    │  │
│  └───────────────────────────────────┘  │
└─────────────┬───────────────────────────┘
              │
              │ 2. Return sessionHandle
              ▼
┌─────────────────────────────────────────┐
│  Client stores session                  │
└─────────────┬───────────────────────────┘
              │
              │ 3. Request operation
              ▼
┌─────────────────────────────────────────┐
│  YawlSecurityManager                    │
│  ┌───────────────────────────────────┐  │
│  │ checkRateLimit()                  │  │
│  │  - Track request timestamps       │  │
│  │  - Enforce limits                 │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ checkPermission()                 │  │
│  │  - Get session from YSessionCache│  │
│  │  - Lookup client roles            │  │
│  │  - Check ROLE_PERMISSIONS map     │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ checkCaseAccess()                 │  │
│  │  - Verify case ownership          │  │
│  │  - Check case permissions         │  │
│  └───────────────────────────────────┘  │
└─────────────┬───────────────────────────┘
              │
              │ 4. Return authorized/denied
              ▼
┌─────────────────────────────────────────┐
│  Execute YAWL operation                 │
└─────────────────────────────────────────┘
```

## Performance Considerations

- **Caching** - Sessions cached in `YSessionCache` (concurrent hash map)
- **Rate Limiting** - O(1) amortized via sliding window
- **Permission Checks** - O(1) role lookup via hash maps
- **Encryption** - Hardware AES acceleration when available
- **Audit Logging** - Asynchronous writes via HibernateEngine

## Security Guarantees

This implementation provides:

✅ **Authentication** - Cryptographically secure API key validation
✅ **Authorization** - Role-based access control with permission checking
✅ **Confidentiality** - AES-256-GCM encryption for sensitive data
✅ **Integrity** - HMAC-SHA256 signing for tamper detection
✅ **Non-repudiation** - Comprehensive audit logging
✅ **Availability** - Rate limiting prevents DoS attacks
✅ **Accountability** - All actions tracked to authenticated identity

## Testing

See test files (when created):
- `test/org/yawlfoundation/yawl/integration/TestYawlSecurityManager.java`
- `test/org/yawlfoundation/yawl/integration/mcp/TestYawlMcpSecurity.java`
- `test/org/yawlfoundation/yawl/integration/a2a/TestYawlA2ASecurity.java`

## License

Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
Licensed under GNU Lesser General Public License.

## Support

For security issues, contact: security@yawlfoundation.org
For general questions: support@yawlfoundation.org
Documentation: https://yawlfoundation.github.io/
