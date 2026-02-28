# Getting Started with YAWL Authentication

Learn how to set up JWT-based authentication, manage client sessions, and protect your YAWL workflows.

## What You'll Learn

In this tutorial, you'll:
1. Configure JWT authentication with a persistent signing key
2. Create your first client application
3. Obtain and validate authentication tokens
4. Enable CSRF protection for web applications
5. Test authentication end-to-end

**Time to complete:** 20 minutes
**Prerequisites:** YAWL v6.0+ running, `curl` or Postman for API testing

---

## Step 1: Configure JWT Signing Key

Before you can issue JWTs, set a persistent signing key that survives server restarts.

### Using Environment Variable

```bash
export YAWL_JWT_SECRET="your-secure-random-key-at-least-32-bytes-long"
```

### Using System Property

```bash
java -Dyawl.jwt.secret="your-secure-random-key" -jar yawl-engine-6.0.0-GA.jar
```

### Using application.yaml (Spring Boot)

```yaml
yawl:
  authentication:
    jwt:
      secret: ${YAWL_JWT_SECRET:change-me-in-production}
      expiration-hours: 24
```

**Important:** In production, generate a cryptographically secure key:

```bash
# Generate a secure 256-bit key (Base64 encoded)
openssl rand -base64 32
```

---

## Step 2: Register Your First Client Application

Client applications authenticate with a username and password pair. Register a client via the YAWL API:

### Using curl

```bash
# Register a new client
curl -X POST http://localhost:8080/yawl/api/v1/auth/clients \
  -H "Content-Type: application/json" \
  -d '{
    "username": "my-app",
    "password": "secure-app-password",
    "description": "My workflow application"
  }'
```

### Expected Response

```json
{
  "clientId": "my-app",
  "status": "REGISTERED",
  "created": "2026-02-28T10:30:00Z",
  "description": "My workflow application"
}
```

---

## Step 3: Obtain an Authentication Token

Exchange client credentials for a JWT token:

```bash
curl -X POST http://localhost:8080/yawl/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'username=my-app&password=secure-app-password'
```

### Response

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "refresh_token": "rt_abc123..."
}
```

Save the `access_token` for subsequent API calls.

---

## Step 4: Use Your Token

Include the token in the `Authorization` header for all YAWL API requests:

```bash
# List deployed specifications
curl -X GET http://localhost:8080/yawl/api/v1/specifications \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Step 5: Enable CSRF Protection for Web Forms

If your application uses HTML forms (not API calls), enable CSRF protection:

### In web.xml

```xml
<filter>
  <filter-name>CsrfProtectionFilter</filter-name>
  <filter-class>org.yawlfoundation.yawl.authentication.CsrfProtectionFilter</filter-class>
</filter>

<filter-mapping>
  <filter-name>CsrfProtectionFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
```

### In Your HTML Form

```html
<form method="POST" action="/yawl/api/v1/cases">
  <!-- Add the CSRF token as a hidden field -->
  <input type="hidden" name="X-CSRF-Token" value="${csrfToken}" />

  <input type="text" name="caseId" placeholder="Case ID" />
  <button type="submit">Start Case</button>
</form>
```

---

## Step 6: Test Rate Limiting

By default, clients are rate-limited to 100 requests per minute:

```bash
# This will succeed
for i in {1..10}; do
  curl http://localhost:8080/yawl/api/v1/specifications \
    -H "Authorization: Bearer $TOKEN"
done

# After exceeding limit:
# HTTP 429 Too Many Requests
```

---

## Complete Example: Java Client

Here's a minimal Java client using the YAWL authentication module:

```java
import org.yawlfoundation.yawl.authentication.*;
import java.util.*;

public class YawlClientExample {
    public static void main(String[] args) throws Exception {
        // Step 1: Authenticate
        String token = authenticateClient("my-app", "secure-app-password");
        System.out.println("Token: " + token);

        // Step 2: Use token in subsequent calls
        // (token is passed to YEngine REST client)
    }

    private static String authenticateClient(String username, String password) {
        // In your application:
        // 1. Call /auth/token endpoint with credentials
        // 2. Extract access_token from response
        // 3. Store token in request headers for subsequent calls
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
    }
}
```

---

## Troubleshooting

### JWT signing key not configured

**Error:** `IllegalStateException: JWT signing key not configured`

**Solution:** Set `YAWL_JWT_SECRET` environment variable or `yawl.jwt.secret` system property.

### Token expires after 24 hours

**Expected behavior:** Tokens expire by default after 24 hours.

**Solution:** Use refresh tokens to obtain a new token without re-authenticating:

```bash
curl -X POST http://localhost:8080/yawl/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "rt_abc123..."}'
```

### CSRF token mismatch in form submission

**Error:** `HTTP 403 Forbidden: CSRF token validation failed`

**Solution:** Ensure the hidden `X-CSRF-Token` field matches the one returned by `CsrfTokenManager`.

---

## What's Next?

- **[Authentication Configuration Reference](../reference/yawl-authentication-config.md)** — All JWT and session options
- **[How-To: Set Up SPIFFE Identity](../how-to/configure-spiffe.md)** — Zero-trust workload identity
- **[How-To: Multi-Tenant Authentication](../how-to/configure-multi-tenancy.md)** — Isolate tenants
- **[Architecture: Authentication Design](../explanation/yawl-authentication-architecture.md)** — Under the hood

---

## Quick Reference

| Task | Command |
|------|---------|
| Set JWT secret | `export YAWL_JWT_SECRET=<key>` |
| Register client | `POST /api/v1/auth/clients` |
| Get token | `POST /api/v1/auth/token` |
| Refresh token | `POST /api/v1/auth/refresh` |
| Use token | `Authorization: Bearer <token>` |
| List sessions | `GET /api/v1/auth/sessions` |

---

**Next:** [How-To: Configure CSRF Protection](../how-to/yawl-authentication-csrf.md)
