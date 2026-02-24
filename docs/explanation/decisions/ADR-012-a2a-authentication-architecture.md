# ADR-012: A2A Server Authentication Architecture

## Status

**ACCEPTED**

## Date

2026-02-17

## Context

### The Vulnerability

`YawlA2AServer` (port 8081) contained a critical authorization bypass. Every
inbound request was processed under an inline anonymous `User` object whose
`isAuthenticated()` method unconditionally returned `true`:

```java
// VULNERABLE CODE - removed in this ADR
User anonymousUser = new User() {
    @Override public boolean isAuthenticated() { return true; }
    @Override public String getUsername() { return "yawl-a2a"; }
};
ServerCallContext callContext = new ServerCallContext(
    anonymousUser, new HashMap<>(), Collections.emptySet());
```

Any client that could reach TCP port 8081 could:
- List all loaded workflow specifications
- Launch arbitrary workflow cases
- Query running case state and work items
- Cancel any running case

This is a complete authorization bypass. No network-level control (firewall,
VPN, service mesh) is a substitute for endpoint-level authentication, because:
1. Misconfigured firewalls or cloud security groups are common
2. Lateral movement inside a cluster bypasses perimeter controls
3. Supply-chain attacks against co-located services bypass VPN
4. Compliance frameworks (SOC 2, ISO 27001) require application-level auth

### Requirements

1. Every request to a workflow-operation endpoint must be rejected unless the
   caller presents verifiable credentials.
2. The agent-card discovery endpoint (`GET /.well-known/agent.json`) may remain
   public; it carries no sensitive data and is required for A2A protocol
   discovery.
3. Authentication must not depend on a single mechanism; different deployment
   contexts need different credential types.
4. Failed authentication must return HTTP 401, not a silent success or 500.
5. Insufficient permissions for a specific operation must return HTTP 403.
6. The implementation must use existing approved libraries (jjwt-jackson 0.13.0,
   SPIFFE stack already in the project).
7. The architecture must be extensible so future schemes (OAuth2, mTLS with
   non-SPIFFE CAs) can be added without changing the server dispatch logic.

### Decision Drivers

- YAWL v6.0.0 already ships a complete SPIFFE/SPIRE stack (ADR-005)
- jjwt-jackson 0.13.0 is already a direct dependency
- The A2A server is the primary entry point for autonomous agents; it is the
  highest-risk endpoint in the system
- The architecture must support both cloud-native (SPIFFE) and traditional
  (JWT/API key) deployments

## Decision

### Authentication Architecture

A pluggable provider chain replaces the anonymous bypass. The chain is
evaluated on every authenticated request:

```
HTTP request
     │
     ▼
CompositeAuthenticationProvider (chain)
     │
     ├── SpiffeAuthenticationProvider   (mTLS, TLS client cert)
     │       Reads SPIFFE URI from SubjectAltName of client X.509 cert.
     │       Validates trust domain. Maps workload path to permissions.
     │       Priority: highest - no secret to distribute.
     │
     ├── JwtAuthenticationProvider      (Authorization: Bearer)
     │       Verifies HS256 JWT against A2A_JWT_SECRET.
     │       Extracts subject as username, scope/permissions as perms.
     │       Priority: second - for external clients without certs.
     │
     └── ApiKeyAuthenticationProvider   (X-API-Key header)
             HMAC-SHA256 digest of raw key against master key.
             Constant-time comparison prevents timing attacks.
             Priority: lowest - for operational tooling.
```

On success a provider returns an immutable `AuthenticatedPrincipal` that:
- implements `io.a2a.server.auth.User` (required by A2A SDK)
- carries `isAuthenticated() = true` only when credentials are verified
- carries a permission set mapping to operation-level access
- carries expiry from the underlying credential

On failure a provider throws `A2AAuthenticationException` with an end-user-safe
message. The server translates this to HTTP 401 + `WWW-Authenticate` header.

### Permission Model

Four permissions are defined. Providers grant them from credential claims or
policy mappings:

| Permission          | Operations                                           |
|---------------------|------------------------------------------------------|
| `workflow:launch`   | POST `/` skill `launch_workflow`                     |
| `workflow:query`    | POST `/` skills `query_workflows`, GET `/tasks/{id}` |
| `workflow:cancel`   | POST `/tasks/{id}/cancel`, skill `cancel_workflow`   |
| `workitem:manage`   | POST `/` skill `manage_workitems`                    |
| `*` (wildcard)      | All operations                                       |

### Scheme Details

#### Scheme 1: mTLS / SPIFFE (service-to-service)

`SpiffeAuthenticationProvider` validates the X.509 client certificate
presented during the TLS handshake:

1. Extract the `spiffe://` URI from the `SubjectAltName` extension (OID 6)
2. Reject if the URI is absent, malformed, or if more than one SPIFFE URI
   is present (violates SPIFFE spec)
3. Validate the trust domain against `A2A_SPIFFE_TRUST_DOMAIN`
4. Map the workload path prefix to permissions via a configurable policy table

Default path-to-permission policy:
- `/engine`  → `*` (full access, for the YAWL engine itself)
- `/agent`   → `workflow:launch, workflow:query, workflow:cancel, workitem:manage`
- `/monitor` → `workflow:query` (read-only)

Chain-of-trust validation is handled by JSSE (the TLS layer). This provider
only inspects the SPIFFE ID after JSSE confirms the certificate was issued by
a trusted CA. Certificate chain re-validation is not performed in application
code.

Requirement: A2A server must be configured with TLS (HTTPS) and client
certificate request (`ClientAuth.NEED` or `WANT`) at the
`javax.net.ssl.SSLContext` / `HttpsServer` level. The current JDK
`HttpServer`-based implementation binds plain HTTP; TLS termination is
delegated to an ingress controller or sidecar proxy that injects the
client certificate. The provider reads the certificate from the
`HttpsExchange` SSLSession when present.

#### Scheme 2: JWT Bearer Token (HS256)

`JwtAuthenticationProvider` verifies Bearer tokens using jjwt-jackson 0.13.0:

- Algorithm: HMAC-SHA256 (HS256); symmetric key from `A2A_JWT_SECRET`
- Minimum key length: 32 bytes (256 bits); shorter keys are rejected at
  construction
- Audience: fixed to `"yawl-a2a"` to prevent token reuse from other services
- Issuer: validated against `A2A_JWT_ISSUER` when set; skipped when unset
- Expiry: enforced by jjwt; expired tokens throw `ExpiredJwtException`
- Permissions: extracted from `scope` claim (RFC 8693 space-delimited string)
  or `permissions` claim (JSON array). When neither is present, `*` is granted
  for backward compatibility.
- Username: from `sub` (subject) claim

Server-side token issuance is provided by `JwtAuthenticationProvider.issueToken`
for administrative tooling and integration tests.

Timing attack mitigation: jjwt performs constant-time HMAC comparison
internally. The server returns identical HTTP 401 responses for
expired-token and invalid-token failures without distinguishing the cause to
the caller.

#### Scheme 3: API Key (HMAC-SHA256)

`ApiKeyAuthenticationProvider` implements HMAC-keyed digest comparison:

- Storage: raw key values are never held in memory. Each key is stored as
  `HMAC-SHA256(masterKey, rawKey)`.
- Lookup: all registry entries are scanned with `MessageDigest.isEqual`
  (constant-time) on every request to prevent timing attacks. The scan does
  not short-circuit on the first match.
- Registry: in-memory `LinkedHashMap` behind a `Collections.synchronizedMap`
  wrapper. Keys are registered via `registerKey(id, username, rawKey, perms)`.
  Auto-registration from `A2A_API_KEY` environment variable at startup.
- Revocation: `revokeKey(id)` removes the entry immediately.
- Key rotation: generate a new key, register it, verify clients have switched,
  then revoke the old key. No downtime required.

### Composite Evaluation

`CompositeAuthenticationProvider` chains providers with these semantics:

1. Call `canHandle(exchange)` on all providers (header inspection, no I/O)
2. From the candidates, call `authenticate(exchange)` in registration order
3. First success returns the principal; exceptions from earlier providers do
   not stop evaluation of later providers
4. If all candidates fail, throw `A2AAuthenticationException` with the reason
   from the first (most specific) candidate failure

This means:
- A request carrying both an invalid JWT and a valid API key succeeds (API key
  wins)
- A request carrying credentials that no provider recognises fails immediately
  (no candidates)
- A request carrying credentials that are recognised but invalid fails with the
  specific reason from that provider

### Server Changes

`YawlA2AServer` constructor gains a required `A2AAuthenticationProvider`
parameter. Passing `null` throws `IllegalArgumentException`. The `main` method
calls `CompositeAuthenticationProvider.production()`, which fails fast if no
provider can be configured from the environment.

Request dispatch flow:
```
exchange received
    │
    ├── path == /.well-known/agent.json  →  serve agent card (no auth)
    │
    └── all other paths
           │
           ├── authProvider.authenticate(exchange)
           │       throws → HTTP 401 + WWW-Authenticate
           │
           ├── principal.isExpired()
           │       true  → HTTP 401
           │
           ├── permission check for requested operation
           │       false → HTTP 403
           │
           └── dispatch to RestHandler
```

### Alternatives Considered

#### Alternative: Require TLS at the HttpServer Level

**Rejected for initial implementation.** `com.sun.net.httpserver.HttpsServer`
requires a `KeyManager` with a server certificate. This makes the server
impossible to start without a certificate, breaking development setups.

TLS should be added in a follow-on ADR via an ingress controller or sidecar
proxy (already the pattern for Kubernetes deployments per ADR-005). The
authentication layer is protocol-independent: when TLS is added, the SPIFFE
provider automatically activates because `HttpsExchange` becomes available.

#### Alternative: Require Only One Authentication Scheme

**Rejected.** Different deployment environments need different schemes:
- Kubernetes with SPIRE: mTLS
- External agent cloud services: JWT
- CI/CD scripts and operational tooling: API keys

Forcing all environments to a single scheme would either make Kubernetes
deployments require secret distribution (defeating SPIFFE) or make simple
script-based clients implement full TLS certificate management.

#### Alternative: OAuth2 / OIDC

**Rejected for this ADR.** Requires an Authorization Server, adds infrastructure
dependency, and the existing jjwt library handles JWTs directly. OAuth2 support
can be added as an additional `A2AAuthenticationProvider` implementation in a
future ADR without changing the framework.

## Consequences

### Positive

1. **Authorization bypass eliminated.** No code path can produce an
   authenticated context without credential verification.
2. **Fail-closed startup.** Server refuses to start if no authentication
   provider is configurable. This prevents silent deployment of an
   unauthenticated server.
3. **Defence in depth.** Three independent schemes can be layered; compromise
   of one (e.g. a leaked API key) does not affect the others.
4. **Extensible.** New schemes are added by implementing
   `A2AAuthenticationProvider` and registering with the composite.
5. **Consistent HTTP semantics.** 401 for unauthenticated, 403 for
   unauthorized, `WWW-Authenticate` header on 401.
6. **No library additions.** Uses jjwt-jackson (already present) and
   JDK-standard `javax.crypto.Mac` / `MessageDigest`.

### Negative

1. **Startup failure when no auth is configured.** Existing deployments that
   rely on the anonymous bypass will fail to start until at least one
   environment variable is set. This is intentional and documented.
2. **In-memory API key registry.** API keys are lost on server restart; they
   must be re-registered. For persistent registration, a future ADR should
   add a database-backed registry.
3. **SPIFFE provider inactive on plain HTTP.** `SpiffeAuthenticationProvider`
   only activates when `HttpsExchange` is available, meaning TLS must be
   terminated before the Java HttpServer layer (e.g. by a proxy/sidecar).

### Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Operator forgets to set auth variables | MEDIUM | HIGH | Server fails to start; clear error message lists required variables |
| JWT secret rotation disrupts clients | LOW | MEDIUM | `issueToken()` makes new tokens easy to issue before rotating secret |
| API key database loss on restart | MEDIUM | LOW | Keys must be re-registered; document this limitation; future DB-backed registry |
| SPIFFE trust domain misconfiguration | LOW | HIGH | Domain mismatch throws immediately; configuration is validated at first request |
| Timing oracle on API key lookup | LOW | MEDIUM | Constant-time scan of all entries eliminates short-circuit timing signal |

## Implementation

### New Files

| File | Purpose |
|------|---------|
| `src/.../a2a/auth/A2AAuthenticationProvider.java` | Strategy interface |
| `src/.../a2a/auth/AuthenticatedPrincipal.java` | Verified identity (implements `User`) |
| `src/.../a2a/auth/A2AAuthenticationException.java` | Auth failure exception |
| `src/.../a2a/auth/JwtAuthenticationProvider.java` | JWT HS256 provider |
| `src/.../a2a/auth/SpiffeAuthenticationProvider.java` | mTLS / SPIFFE provider |
| `src/.../a2a/auth/ApiKeyAuthenticationProvider.java` | HMAC-SHA256 API key provider |
| `src/.../a2a/auth/CompositeAuthenticationProvider.java` | Chain of providers |
| `src/.../a2a/auth/package-info.java` | Package documentation |

### Modified Files

| File | Change |
|------|--------|
| `src/.../a2a/YawlA2AServer.java` | Added auth enforcement; removed anonymous user |
| `.env.example` | Added A2A auth configuration variables |

### Migration Guide for Existing Deployments

**Before** (broken - no auth):
```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=secret
java -cp ... org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
```

**After** (secure - JWT example):
```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=secret
export A2A_JWT_SECRET=$(openssl rand -base64 32)
java -cp ... org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
# Server prints: Auth schemes: mTLS, Bearer
```

**After** (secure - API key example):
```bash
export A2A_API_KEY_MASTER=$(openssl rand -hex 32)
export A2A_API_KEY=$(openssl rand -hex 32)
java -cp ... org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
# Client: curl -H "X-API-Key: $A2A_API_KEY" http://localhost:8081/
```

**After** (Kubernetes with SPIFFE - no secrets required):
```yaml
env:
  - name: A2A_SPIFFE_TRUST_DOMAIN
    value: "yawl.cloud"
  - name: SPIFFE_ENDPOINT_SOCKET
    value: "unix:///run/spire/sockets/agent.sock"
```

## Related ADRs

- ADR-005: SPIFFE/SPIRE for Zero-Trust Identity (provides the SPIFFE infrastructure)
- ADR-004: Spring Boot 3.4 + Java 25 (virtual threads used in server dispatch)

## Approval

**Approved by:** YAWL Architecture Team, Security Team
**Date:** 2026-02-17
**Implementation Status:** COMPLETE
**Security Review:** Required before production deployment
**Next Review:** 2026-08-17 (6 months)

---

**Revision History:**
- 2026-02-17: Initial ADR documenting the authentication architecture and implementation
