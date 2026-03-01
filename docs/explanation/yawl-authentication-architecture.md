# YAWL Authentication Architecture

Understanding JWT tokens, session management, and security design in YAWL v6.0.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Application                       │
│         (Browser, Mobile App, External Service)              │
└────────────────────────┬────────────────────────────────────┘
                         │ 1. POST /auth/token
                         │ (username, password)
                         ↓
┌─────────────────────────────────────────────────────────────┐
│              YAWL Authentication Service                     │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           JWT Manager                                │   │
│  │  • Sign tokens with persistent key                   │   │
│  │  • Validate token signatures                         │   │
│  │  • Handle token expiration                           │   │
│  │  • Manage refresh tokens                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                         ↕                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │        Session Storage (DB)                          │   │
│  │  • H2 (development)                                   │   │
│  │  • PostgreSQL (production)                            │   │
│  │  • MySQL (production)                                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                         ↕                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │     CSRF Protection Filter                           │   │
│  │  • Generate CSRF tokens                              │   │
│  │  • Validate form submissions                         │   │
│  │  • Double-submit cookie pattern                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                         ↕                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │     Rate Limiting Filter                             │   │
│  │  • Per-client request counting                        │   │
│  │  • Memory or Redis backend                            │   │
│  │  • Token-bucket algorithm                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                         ↕                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │     Security Audit Logger                            │   │
│  │  • Log login/logout events                            │   │
│  │  • Track failed attempts                              │   │
│  │  • Database or file storage                           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
                         ↑
                         │ 2. Return access_token
                         │
                    Access Token:
                    eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
                    Valid for 24 hours
```

---

## JWT Token Structure

### Token Anatomy

A JWT consists of three Base64-encoded parts separated by dots:

```
Header.Payload.Signature
```

#### Header

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

- **alg**: Signing algorithm (HS256, HS384, HS512)
- **typ**: Token type (always JWT)

#### Payload (Claims)

```json
{
  "iss": "yawl-engine",
  "sub": "workflow-app",
  "exp": 1740787200,
  "iat": 1740700800,
  "aud": "yawl-api",
  "user_id": "workflow-app",
  "scopes": ["api:read", "api:write"]
}
```

- **iss** (issuer): Always "yawl-engine"
- **sub** (subject): Client ID
- **exp** (expiration): Unix timestamp (default 24 hours from creation)
- **iat** (issued at): Creation time
- **aud** (audience): Always "yawl-api"

#### Signature

```
HMAC-SHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  secret_key
)
```

The signature proves that the token was issued by YAWL and hasn't been tampered with.

### Why Persistent Signing Key?

The signing key must persist across server restarts. Otherwise:

1. **Existing tokens become invalid** — Users lose their sessions
2. **Clients can't trust tokens** — The key changes, invalidating signatures
3. **Distributed systems break** — Each instance would have different keys

Solution: Load the key from environment variables or configuration on startup.

---

## Session Lifecycle

### Step 1: Client Registration

```bash
POST /api/v1/auth/clients
{
  "username": "my-app",
  "password": "secure-password"
}
```

YAWL stores the client in the database with hashed password.

### Step 2: Authentication

```bash
POST /api/v1/auth/token
{
  "username": "my-app",
  "password": "secure-password"
}
```

Flow:
1. Verify password hash matches stored value
2. Check client is active (not disabled/revoked)
3. Create JWT with claims
4. Sign JWT with persistent key
5. Create refresh token
6. Store refresh token in database
7. Return both tokens

### Step 3: API Calls

```bash
GET /api/v1/cases
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Flow:
1. Extract token from Authorization header
2. Verify signature using stored key
3. Check expiration time
4. Validate audience claim
5. Extract client ID from subject
6. Allow request if valid

### Step 4: Token Refresh

When token approaches expiration (before 24 hours):

```bash
POST /api/v1/auth/refresh
{
  "refresh_token": "rt_abc123..."
}
```

Flow:
1. Validate refresh token exists in database
2. Check it's marked as valid (not revoked)
3. Check it hasn't expired (30 days default)
4. Create new access token
5. Optionally rotate refresh token
6. Return new tokens

### Step 5: Logout

```bash
POST /api/v1/auth/logout
```

Flow:
1. Revoke refresh token in database
2. Invalidate all sessions for client
3. Return success

---

## Design Decisions

### Why HMAC-SHA256 Instead of RSA?

| Aspect | HMAC-SHA256 | RSA |
|--------|-------------|-----|
| **Complexity** | Simple | Complex |
| **Performance** | Fast | Slower |
| **Infrastructure** | Single key | Public/private keys |
| **Use Case** | Microservice-internal auth | Federation/OAuth |

**Decision**: HMAC-SHA256 for internal YAWL API. If you need federation, use a gateway with RSA keys.

### Why Store Sessions in Database?

| Option | Pros | Cons |
|--------|------|------|
| **JWT Only** | Stateless | Can't revoke tokens immediately |
| **Database** | Revocation, audit trail | State management overhead |

**Decision**: Database-backed because revocation and audit trails are critical for security.

### Why Refresh Tokens?

**Problem**: Access tokens valid for 24 hours = long attack window if stolen.

**Solution**:
- Access tokens valid only 1 hour
- Refresh tokens (HTTP-only cookies) valid 30 days
- Use refresh token to get new access token
- If refresh token is stolen, only 30 days of access

### Rate Limiting Strategy

**Token-bucket algorithm**:
- Bucket fills at 100 requests/minute rate
- Burst allowance of 10 requests
- Clients can spike but are throttled over time
- Per-client limiting (not global)

**Example**:
- Normal: 2 req/sec (100/min) → smooth
- Spike: 5 req/sec for 2 sec (burst) → allowed
- Sustained 5 req/sec → throttled to 2 req/sec

---

## Security Considerations

### Signing Key Management

**In Development:**
```bash
export YAWL_JWT_SECRET="development-key-not-secure"
```

**In Production:**
```bash
# Option 1: AWS Secrets Manager
export YAWL_JWT_SECRET=$(aws secretsmanager get-secret-value --secret-id yawl-jwt --query SecretString --output text)

# Option 2: HashiCorp Vault
export YAWL_JWT_SECRET=$(vault kv get -field=secret secret/yawl/jwt)

# Option 3: Kubernetes Secret
export YAWL_JWT_SECRET=$(kubectl get secret yawl-jwt -o jsonpath='{.data.secret}' | base64 -d)
```

### HTTPS Required

Always use HTTPS in production. JWT tokens in HTTP can be intercepted.

```bash
# Development (HTTP OK)
http://localhost:8080/api/v1/auth/token

# Production (HTTPS mandatory)
https://api.example.com/api/v1/auth/token
```

### CSRF Protection

For web forms (HTML + JavaScript), CSRF protection is essential:

1. Server generates token → sends in Set-Cookie
2. JavaScript reads token from cookie → sends in header
3. Server validates header matches cookie
4. Attacker can't forge token (can't read cookie due to SOP)

---

## Virtual Thread Integration

YAWL uses virtual threads for scalability:

```java
// Session validation runs on virtual thread
Thread.ofVirtual()
    .name("auth-" + clientId)
    .start(() -> {
        validateToken(token);
        processRequest();
    });
```

Benefits:
- No thread pool exhaustion
- Thousands of concurrent sessions
- Automatic cleanup

---

## Related Architecture

- **Authentication** ← (you are here)
- **[Scheduling](yawl-scheduling-architecture.md)** — Calendar-aware timers
- **[Monitoring](yawl-monitoring-architecture.md)** — Trace auth events
- **[Worklets](yawl-worklet-architecture.md)** — RDR for adaptive workflows

---

## See Also

- [How-To: Set Up JWT Authentication](../how-to/yawl-authentication-setup.md)
- [Reference: Configuration Options](../reference/yawl-authentication-config.md)
- [Tutorial: Getting Started](../tutorials/yawl-authentication-getting-started.md)
