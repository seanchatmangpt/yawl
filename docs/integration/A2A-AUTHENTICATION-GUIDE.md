# A2A Server Authentication Guide

**Version**: YAWL v5.2 | **Date**: 2026-02-18 | **ADR**: [ADR-012](../architecture/decisions/ADR-012-a2a-authentication-architecture.md)

---

## Overview

The YAWL A2A (Agent-to-Agent) Server (port 8081) requires authentication for all workflow operations. This guide covers the three supported authentication schemes and how to configure them.

## Quick Reference

| Scheme | Use Case | Header | Security Level |
|--------|----------|--------|----------------|
| mTLS/SPIFFE | Service-to-service (Kubernetes) | TLS client cert | Highest |
| JWT Bearer | External clients, agents | `Authorization: Bearer <token>` | High |
| API Key | CI/CD, operational scripts | `X-API-Key: <key>` | Medium |

---

## Permission Model

All authentication schemes map to a permission set:

| Permission | Operations |
|------------|------------|
| `workflow:launch` | POST `/` skill `launch_workflow` |
| `workflow:query` | POST `/` skills `query_workflows`, GET `/tasks/{id}` |
| `workflow:cancel` | POST `/tasks/{id}/cancel`, skill `cancel_workflow` |
| `workitem:manage` | POST `/` skill `manage_workitems` |
| `*` (wildcard) | All operations |

---

## Scheme 1: mTLS / SPIFFE Authentication

**Best for**: Kubernetes deployments, service mesh environments

### How It Works

1. Client presents X.509 certificate during TLS handshake
2. Server extracts SPIFFE URI from `SubjectAltName` extension
3. Trust domain is validated against `A2A_SPIFFE_TRUST_DOMAIN`
4. Workload path is mapped to permissions

### Configuration

**Server-side (Kubernetes)**:

```yaml
# In your deployment manifest
env:
  - name: A2A_SPIFFE_TRUST_DOMAIN
    value: "yawl.cloud"
  - name: SPIFFE_ENDPOINT_SOCKET
    value: "unix:///run/spire/sockets/agent.sock"
```

**SPIFFE ID Format**:
```
spiffe://yawl.cloud/workload/engine
spiffe://yawl.cloud/workload/agent/document-processor
spiffe://yawl.cloud/workload/monitor/dashboard
```

**Default Path-to-Permission Mapping**:

| Path Prefix | Permissions |
|-------------|-------------|
| `/engine` | `*` (full access) |
| `/agent` | `workflow:launch, workflow:query, workflow:cancel, workitem:manage` |
| `/monitor` | `workflow:query` (read-only) |

### Client Example

```bash
# With SPIRE-injected certificate (automatic in Kubernetes)
curl https://a2a.yawl.cloud:8081/ \
  --cacert /run/spire/certs/ca.crt \
  --cert /run/spire/certs/svid.crt \
  --key /run/spire/certs/svid.key
```

---

## Scheme 2: JWT Bearer Token (HS256)

**Best for**: External clients, agent services, web applications

### How It Works

1. Client includes JWT in `Authorization: Bearer` header
2. Server verifies HMAC-SHA256 signature against `A2A_JWT_SECRET`
3. Audience must be `yawl-a2a`
4. Permissions extracted from `scope` or `permissions` claim

### Token Structure

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "agent-document-processor",
    "aud": "yawl-a2a",
    "iss": "yawl-auth-service",
    "exp": 1708300000,
    "iat": 1708296400,
    "scope": "workflow:launch workflow:query workitem:manage"
  }
}
```

### Server Configuration

```bash
# Generate secret (256-bit minimum)
export A2A_JWT_SECRET=$(openssl rand -base64 32)

# Optional: configure issuer validation
export A2A_JWT_ISSUER=yawl-auth-service

# Start server
java -cp target/yawl.jar org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
```

### Token Generation (Server-Side)

```java
// Using JwtAuthenticationProvider
String token = JwtAuthenticationProvider.issueToken(
    "agent-document-processor",           // subject
    Set.of("workflow:launch", "workflow:query", "workitem:manage"),  // permissions
    Duration.ofHours(1)                   // expiry
);
```

### Token Generation (CLI)

```bash
# Using openssl and jjwt (simplified)
cat << 'EOF' | python3
import jwt
import time

secret = "your-base64-encoded-secret"
payload = {
    "sub": "my-agent",
    "aud": "yawl-a2a",
    "iat": int(time.time()),
    "exp": int(time.time()) + 3600,
    "scope": "workflow:launch workflow:query"
}

token = jwt.encode(payload, secret, algorithm="HS256")
print(f"Authorization: Bearer {token}")
EOF
```

### Client Example

```bash
# Launch a workflow
curl -X POST https://a2a.yawl.cloud:8081/ \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "skill": "launch_workflow",
    "params": {
      "specification_id": "InvoiceProcessing-1.0",
      "case_data": {"invoice_id": "12345"}
    }
  }'
```

### Response Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Parse response |
| 401 | Invalid/expired token | Refresh token and retry |
| 403 | Insufficient permissions | Request elevated permissions |

---

## Scheme 3: API Key (HMAC-SHA256)

**Best for**: CI/CD pipelines, operational scripts, testing

### How It Works

1. Client includes key in `X-API-Key` header
2. Server computes HMAC-SHA256(masterKey, providedKey)
3. Constant-time comparison against registered keys
4. No raw keys stored in memory

### Server Configuration

```bash
# Generate master key (controls all API keys)
export A2A_API_KEY_MASTER=$(openssl rand -hex 32)

# Auto-register an initial key at startup
export A2A_API_KEY=$(openssl rand -hex 32)

# Start server
java -cp target/yawl.jar org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
```

### Registering Additional Keys

```java
// Programmatically (in server startup)
ApiKeyAuthenticationProvider provider = new ApiKeyAuthenticationProvider(masterKey);
provider.registerKey(
    "ci-pipeline-key",                    // key ID
    "ci-pipeline",                        // username
    rawKey,                               // the raw key value
    Set.of("workflow:launch", "workflow:query")  // permissions
);
```

### Client Example

```bash
# Query available workflows
curl -X POST https://a2a.yawl.cloud:8081/ \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "skill": "query_workflows",
    "params": {}
  }'
```

### Key Rotation

```bash
# 1. Generate new key
NEW_KEY=$(openssl rand -hex 32)

# 2. Register new key (both keys work simultaneously)
# (Server-side: provider.registerKey("new-key-id", ...))

# 3. Update clients to use new key

# 4. Revoke old key
# (Server-side: provider.revokeKey("old-key-id"))
```

---

## Composite Authentication

The server supports multiple schemes simultaneously. Credentials are evaluated in order:

```
1. mTLS/SPIFFE (if TLS client cert present)
2. JWT Bearer (if Authorization header present)
3. API Key (if X-API-Key header present)
```

**Priority**: First valid authentication wins.

**Example**: A request with both a valid JWT and a valid API key succeeds via JWT (evaluated first).

---

## Error Responses

### 401 Unauthorized

```json
{
  "error": "authentication_failed",
  "message": "JWT token expired",
  "www_authenticate": "Bearer realm=\"yawl-a2a\", error=\"invalid_token\", error_description=\"Token expired\""
}
```

### 403 Forbidden

```json
{
  "error": "insufficient_permissions",
  "message": "Operation 'workflow:cancel' requires permission 'workflow:cancel'",
  "required_permissions": ["workflow:cancel"],
  "granted_permissions": ["workflow:query"]
}
```

---

## Security Best Practices

### JWT Tokens

1. **Short expiry**: Use 1-hour expiry maximum for production
2. **Secure storage**: Never log tokens; use secrets management
3. **Rotate secrets**: Rotate `A2A_JWT_SECRET` quarterly
4. **Audience restriction**: Always set `aud: "yawl-a2a"`

### API Keys

1. **Scoped permissions**: Grant minimum required permissions
2. **Unique per client**: One key per service/pipeline
3. **Regular rotation**: Rotate keys every 90 days
4. **Audit logging**: Log all key usage for compliance

### mTLS/SPIFFE

1. **Trust domain isolation**: Use unique trust domains per environment
2. **Workload identity**: Use descriptive workload paths
3. **Certificate rotation**: SPIRE handles automatic rotation (default 24h)

---

## Migration Guide

### From No Authentication (v5.1)

**Before** (v5.1 - vulnerable):
```bash
# No authentication required
curl http://localhost:8081/
```

**After** (v5.2 - secure):
```bash
# Must provide credentials
export A2A_JWT_SECRET=$(openssl rand -base64 32)
java -cp ... org.yawlfoundation.yawl.integration.a2a.YawlA2AServer

# Client must authenticate
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/
```

---

## Troubleshooting

### Server Fails to Start

**Error**: `No authentication provider configured`

**Solution**: Set at least one authentication environment variable:
```bash
export A2A_JWT_SECRET=$(openssl rand -base64 32)
# OR
export A2A_API_KEY_MASTER=$(openssl rand -hex 32)
export A2A_API_KEY=$(openssl rand -hex 32)
# OR (Kubernetes)
export A2A_SPIFFE_TRUST_DOMAIN=yawl.cloud
```

### Token Validation Fails

**Check**:
1. Token not expired (`exp` claim)
2. Audience is `yawl-a2a`
3. Secret matches between issuer and server
4. Algorithm is HS256

### API Key Fails

**Check**:
1. Key was registered before request
2. `A2A_API_KEY_MASTER` is consistent across restarts
3. Key format is hex or base64 (no whitespace)

---

## Related Documentation

- [ADR-012: A2A Authentication Architecture](../architecture/decisions/ADR-012-a2a-authentication-architecture.md)
- [ADR-005: SPIFFE/SPIRE Zero-Trust](../architecture/decisions/ADR-005-spiffe-spire-zero-trust.md)
- [SECURITY-CHECKLIST-JAVA25.md](../../.claude/SECURITY-CHECKLIST-JAVA25.md)

---

**Last Updated**: 2026-02-18
**Maintainer**: YAWL Security Team
