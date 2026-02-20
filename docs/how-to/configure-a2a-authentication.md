# How to Configure A2A Authentication

## Problem Statement

The YAWL Agent-to-Agent (A2A) server exposes workflow capabilities to AI agents and external services over HTTP on port 8081. Every request must carry verifiable credentials before any workflow operation is executed. This guide explains how to configure the three supported authentication schemes:

1. **SPIFFE mTLS** — Mutual TLS with X.509 SVIDs (service-to-service)
2. **JWT Bearer** — JSON Web Tokens with HMAC-SHA256 (external clients)
3. **API Key** — Static keys with HMAC-SHA256 digests (operational tooling)

## Prerequisites

- YAWL A2A server deployed (`YawlA2AServer` or `VirtualThreadYawlA2AServer`)
- Java 25 runtime with JCE (Java Cryptography Extension)
- For SPIFFE: SPIRE Agent running with socket at `/run/spire/sockets/agent.sock`
- For JWT: Secret key of at least 32 characters
- For API Key: Master key of at least 16 characters

## Authentication Architecture

The A2A server uses a composite authentication pattern where multiple providers are chained in priority order:

```
HTTP Request
      |
      v
CompositeAuthenticationProvider
      |
      +-- [1] SpiffeAuthenticationProvider (mTLS)
      |        Validates TLS client certificate SPIFFE ID
      |
      +-- [2] JwtAuthenticationProvider (Bearer)
      |        Validates Authorization: Bearer <token>
      |
      +-- [3] ApiKeyAuthenticationProvider (ApiKey)
      |        Validates X-API-Key: <key>
      |
      +-- [4] HandoffTokenAuthenticationProvider
               Validates X-Handoff-Token for agent handoffs
      |
      v
AuthenticatedPrincipal or A2AAuthenticationException (HTTP 401)
```

Each provider's `canHandle()` method determines if it can process the request. The first successful authentication wins.

## Option 1: SPIFFE mTLS Authentication (Recommended for Service-to-Service)

SPIFFE provides zero-trust identity through X.509 SVIDs. Use this for internal service-to-service communication.

### How It Works

1. Client presents TLS certificate containing SPIFFE ID in Subject Alternative Name (URI type)
2. Server validates certificate chain (delegated to TLS layer)
3. Server extracts SPIFFE ID and checks trust domain
4. Server maps SPIFFE workload path to permission set

### Configuration

**Environment Variables:**

```bash
# Trust domain to accept (default: yawl.cloud)
export A2A_SPIFFE_TRUST_DOMAIN="yawl.cloud"
```

**Default Permission Policy:**

| SPIFFE Path | Permissions |
|-------------|-------------|
| `/engine` | Full access (`*`) |
| `/agent` | `workflow:launch`, `workflow:query`, `workflow:cancel`, `workitem:manage` |
| `/monitor` | `workflow:query` only |

**Client Configuration:**

Configure your HTTP client to use mTLS with the SPIFFE SVID:

```java
import org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadApiClient;
import org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadIdentity;
import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// Fetch SVID from SPIRE Agent
SpiffeWorkloadIdentity identity = SpiffeWorkloadApiClient.fetchX509Identity();

// Create mTLS client
SSLContext sslContext = identity.createSSLContext();
HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)
    .version(HttpClient.Version.HTTP_1_1)
    .build();

// Make authenticated request
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://yawl-a2a.example.com:8081/"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

**Server-Side Configuration:**

The server must be configured for mTLS:

```java
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

public HttpsServer createMtlsServer(int port, KeyStore serverKeystore, KeyStore trustStore)
        throws Exception {

    // Initialize key manager for server certificate
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(serverKeystore, "password".toCharArray());

    // Initialize trust manager for client certificate validation
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    // Create SSL context
    SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    // Create HTTPS server with client auth required
    HttpsServer server = HttpsServer.create(new InetSocketAddress(port), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
        @Override
        public void configure(HttpsParameters params) {
            params.setNeedClientAuth(true);  // Require client certificate
            params.setSSLParameters(sslContext.getDefaultSSLParameters());
        }
    });

    return server;
}
```

### Verify SPIFFE Authentication

```bash
# Test mTLS with SPIRE-issued SVID
curl --cert /run/spire/sockets/svid.pem \
     --key /run/spire/sockets/svid-key.pem \
     --cacert /run/spire/sockets/bundle.pem \
     https://localhost:8081/.well-known/agent.json
```

A successful response confirms mTLS is working.

## Option 2: JWT Bearer Authentication (External Clients)

JWT Bearer tokens are suitable for external clients and service accounts that cannot use mTLS.

### How It Works

1. Client includes `Authorization: Bearer <token>` header
2. Server validates token signature with shared secret (HMAC-SHA256)
3. Server extracts claims: `sub` (subject), `scope` or `permissions`
4. Server maps scopes to permission set

### Configuration

**Environment Variables:**

```bash
# Required: HMAC-SHA256 signing key (minimum 32 characters)
export A2A_JWT_SECRET="$(openssl rand -base64 32)"

# Optional: Expected issuer claim
export A2A_JWT_ISSUER="yawl-auth"
```

**Generating Tokens:**

Use `JwtAuthenticationProvider.issueToken()` for administrative token generation:

```java
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import java.util.List;

JwtAuthenticationProvider provider = JwtAuthenticationProvider.fromEnvironment();

// Issue token for an agent
String token = provider.issueToken(
    "agent-order-processor",                        // subject
    List.of("workflow:launch", "workitem:manage"), // scopes
    3600_000L                                       // 1 hour validity
);

System.out.println("Bearer token: " + token);
```

**Token Claims:**

| Claim | Required | Description |
|-------|----------|-------------|
| `sub` | Yes | Principal identifier (username/agent ID) |
| `aud` | Yes | Must contain `yawl-a2a` |
| `iss` | If configured | Must match `A2A_JWT_ISSUER` |
| `exp` | Recommended | Expiration timestamp |
| `iat` | Optional | Issued-at timestamp |
| `scope` | Recommended | Space-separated permissions |
| `permissions` | Alternative | JSON array of permissions |

**Permission Constants:**

```java
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;

// Available permissions
AuthenticatedPrincipal.PERM_ALL               // "*" - full access
AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH   // "workflow:launch"
AuthenticatedPrincipal.PERM_WORKFLOW_QUERY    // "workflow:query"
AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL   // "workflow:cancel"
AuthenticatedPrincipal.PERM_WORKITEM_MANAGE   // "workitem:manage"
```

**Client Request Example:**

```bash
# Using curl with JWT
curl -X POST https://yawl-a2a.example.com:8081/ \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","method":"launch_workflow","params":{...},"id":1}'
```

### Verify JWT Authentication

```bash
# Decode and inspect token at jwt.io
# Verify claims: sub, aud (yawl-a2a), scope/permissions

# Test authentication
curl -v https://localhost:8081/ \
     -H "Authorization: Bearer $TOKEN" 2>&1 | grep "< HTTP"
# Expected: HTTP/1.1 200 OK (not 401)
```

## Option 3: API Key Authentication (Operational Tooling)

API keys are simple static credentials suitable for operational tooling and scripts.

### How It Works

1. Client includes `X-API-Key: <key>` header
2. Server computes HMAC-SHA256 digest of the key
3. Server compares digest with stored value (constant-time comparison)
4. Server returns principal with associated permissions

### Configuration

**Environment Variables:**

```bash
# Required: Master key for HMAC digests (minimum 16 characters)
export A2A_API_KEY_MASTER="$(openssl rand -hex 32)"

# Optional: Auto-register a default key
export A2A_API_KEY="my-secure-api-key-change-me"
```

**Programmatic Key Registration:**

For multi-tenant deployments, register keys programmatically:

```java
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import java.util.Set;

ApiKeyAuthenticationProvider provider = ApiKeyAuthenticationProvider.fromEnvironment();

// Register a key for monitoring tools (read-only)
provider.registerKey(
    "monitor-1",                                    // key ID (for revocation)
    "monitoring-dashboard",                         // principal name
    "sk_live_monitor_secret_key_xyz",               // raw key value
    Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
);

// Register a key for automation (full access)
provider.registerKey(
    "automation-1",
    "ci-cd-pipeline",
    "sk_live_automation_secret_key_abc",
    Set.of(AuthenticatedPrincipal.PERM_ALL)
);

// Check registered key count
int keyCount = provider.registeredKeyCount();
```

**Key Revocation:**

```java
// Revoke a key immediately
boolean revoked = provider.revokeKey("automation-1");
```

**Client Request Example:**

```bash
# Using curl with API key
curl -X POST https://yawl-a2a.example.com:8081/ \
     -H "X-API-Key: sk_live_monitor_secret_key_xyz" \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","method":"query_workflows","params":{},"id":1}'
```

### Security Considerations

- API keys do not expire; use revocation for rotation
- Raw keys are never stored; only HMAC digests are retained
- Timing-attack resistant comparison prevents key enumeration
- Use different keys for different clients/principals for auditability

### Verify API Key Authentication

```bash
# Test with registered key
curl -v https://localhost:8081/ \
     -H "X-API-Key: $API_KEY" 2>&1 | grep "< HTTP"
# Expected: HTTP/1.1 200 OK (not 401)
```

## Composite Provider Configuration

The recommended production setup uses all three providers:

```java
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationProvider;

// Production stack (evaluated in order)
CompositeAuthenticationProvider authProvider = CompositeAuthenticationProvider.production();

// Inspect configured providers
for (A2AAuthenticationProvider provider : authProvider.getProviders()) {
    System.out.println("Provider: " + provider.scheme());
}
```

**Evaluation Order:**

1. **SPIFFE mTLS** — Checked first for service-to-service calls
2. **JWT Bearer** — Checked for external clients with tokens
3. **API Key** — Checked for operational tooling
4. **Handoff Token** — Always available for agent-to-agent work item transfer

**Failure Handling:**

- If no provider `canHandle()` the request: HTTP 401 with list of supported schemes
- If all candidates fail: HTTP 401 with aggregated failure reasons
- Provider-specific exceptions are hidden to prevent scheme enumeration

## Kubernetes Deployment

### Secret Configuration

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: yawl-a2a-secrets
  namespace: yawl
type: Opaque
stringData:
  # JWT configuration
  A2A_JWT_SECRET: "your-256-bit-secret-minimum-32-characters-here"
  A2A_JWT_ISSUER: "yawl-auth"

  # API key configuration
  A2A_API_KEY_MASTER: "0123456789abcdef0123456789abcdef"
  A2A_API_KEY: "my-production-api-key"

  # SPIFFE trust domain
  A2A_SPIFFE_TRUST_DOMAIN: "yawl.cloud"
```

### Deployment Configuration

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-a2a-server
spec:
  template:
    spec:
      containers:
        - name: yawl-a2a
          image: yawl/engine:6.0.0
          envFrom:
            - secretRef:
                name: yawl-a2a-secrets
          volumeMounts:
            - name: spire-socket
              mountPath: /run/spire/sockets
              readOnly: true
      volumes:
        - name: spire-socket
          hostPath:
            path: /run/spire/sockets
            type: Directory
```

## Troubleshooting

### "No authentication credentials found in the request"

**Cause:** No provider recognized the request format.

**Solution:** Include credentials in one of these formats:
- `Authorization: Bearer <token>`
- `X-API-Key: <key>`
- mTLS client certificate

### "A2A_JWT_SECRET must be at least 32 bytes"

**Cause:** JWT secret is too short for HMAC-SHA256.

**Solution:** Generate a proper secret:
```bash
export A2A_JWT_SECRET="$(openssl rand -base64 32)"
```

### "Bearer token has expired"

**Cause:** JWT `exp` claim is in the past.

**Solution:** Obtain a new token with a future expiration.

### "SPIFFE ID trust domain is not trusted"

**Cause:** Client certificate SPIFFE ID has wrong trust domain.

**Solution:** Ensure both client and server use the same `A2A_SPIFFE_TRUST_DOMAIN`.

### "No API keys are configured on this server"

**Cause:** API key provider has no registered keys.

**Solution:** Set `A2A_API_KEY` environment variable or call `registerKey()` programmatically.

### HTTP 401 with no specific error message

**Cause:** All authentication providers failed.

**Solution:** Check server logs for detailed failure reasons. Enable DEBUG logging:
```properties
logging.level.org.yawlfoundation.yawl.integration.a2a.auth=DEBUG
```

## Related Documentation

- [Deploy to Kubernetes](/docs/how-to/deploy-to-kubernetes.md) — Kubernetes deployment guide
- [Configure SPIFFE/SPIRE](/docs/how-to/configure-spiffe.md) — SPIFFE zero-trust identity setup
- `src/org/yawlfoundation/yawl/integration/a2a/auth/package-info.java` — Authentication package overview
- `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java` — A2A server implementation
