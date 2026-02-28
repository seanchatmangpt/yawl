# How-To: Set Up JWT Authentication

Configure YAWL with persistent JWT tokens and session management for production deployments.

## Prerequisites

- YAWL v6.0+ installed and running
- Access to environment configuration
- HTTP client for testing (curl, Postman, or similar)

---

## Task 1: Generate a Secure JWT Signing Key

### Option A: Using OpenSSL

```bash
# Generate 256-bit (32-byte) random key, Base64 encoded
openssl rand -base64 32
# Output: aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789ABCD=
```

### Option B: Using Java

```bash
java -cp /path/to/jjwt-api-0.x.x.jar \
  -c "import java.util.Base64; import java.security.SecureRandom; \
      SecureRandom r = new SecureRandom(); \
      byte[] key = new byte[32]; \
      r.nextBytes(key); \
      System.out.println(Base64.getEncoder().encodeToString(key));"
```

### Option C: Using /dev/urandom

```bash
# Generate 32 random bytes and Base64 encode
dd if=/dev/urandom bs=32 count=1 2>/dev/null | base64
```

**Store this key securely** — it's sensitive cryptographic material.

---

## Task 2: Configure the JWT Manager

### Configuration Method A: Environment Variable (Recommended)

```bash
# Set in .env or in your container environment
export YAWL_JWT_SECRET="your-base64-encoded-secret-key-here"

# Verify it's set
echo $YAWL_JWT_SECRET
```

### Configuration Method B: System Property

```bash
# Pass to JVM at startup
java -Dyawl.jwt.secret="your-base64-encoded-secret-key" \
     -jar yawl-engine-6.0.0-GA.jar
```

### Configuration Method C: Spring Boot application.yaml

```yaml
yawl:
  authentication:
    jwt:
      secret: ${YAWL_JWT_SECRET:change-me-in-production}
      expiration-hours: 24
      refresh-token-expiration-days: 30
      algorithm: "HS256"  # HMAC with SHA-256
```

### Configuration Method D: Docker Environment

```bash
docker run -d \
  -e YAWL_JWT_SECRET="your-base64-encoded-secret-key" \
  -p 8080:8080 \
  yawlfoundation/yawl:6.0.0-GA
```

---

## Task 3: Set Up Session Storage

By default, YAWL uses an embedded H2 database for sessions. For production, use PostgreSQL or MySQL.

### Option A: H2 (Default)

No configuration needed. Sessions are stored in `./yawl-sessions.db`

### Option B: PostgreSQL

Add to `application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.example.com:5432/yawl_sessions
    username: yawl_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate  # Don't auto-create in production
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect
        format_sql: true
        use_sql_comments: true

yawl:
  authentication:
    session-store: database
```

### Option C: MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://db.example.com:3306/yawl_sessions
    username: yawl_user
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

---

## Task 4: Create Client Credentials

Register client applications that will authenticate with YAWL.

### Create a Client via API

```bash
curl -X POST http://localhost:8080/yawl/api/v1/auth/clients \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "username": "workflow-app",
    "password": "app-secure-password-change-me",
    "description": "Main workflow application"
  }'
```

### Create Multiple Clients via Script

```bash
#!/bin/bash

ADMIN_TOKEN="your-admin-token"
YAWL_URL="http://localhost:8080"

declare -a clients=(
  "integration-service:service-password"
  "mobile-app:mobile-password"
  "batch-processor:batch-password"
)

for client_pair in "${clients[@]}"; do
  IFS=':' read -r username password <<< "$client_pair"

  curl -X POST $YAWL_URL/yawl/api/v1/auth/clients \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{
      \"username\": \"$username\",
      \"password\": \"$password\",
      \"description\": \"Auto-created client\"
    }"

  echo "Created client: $username"
done
```

---

## Task 5: Generate and Manage Tokens

### Obtain a Token

```bash
# Exchange credentials for JWT
TOKEN=$(curl -s -X POST http://localhost:8080/yawl/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'username=workflow-app&password=app-secure-password-change-me' | \
  jq -r '.access_token')

echo "Token: $TOKEN"
```

### Verify Token Validity

```bash
# Decode JWT (without verification)
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq .

# Output:
# {
#   "iss": "yawl-engine",
#   "sub": "workflow-app",
#   "exp": 1740787200,
#   "iat": 1740700800,
#   "aud": "yawl-api"
# }
```

### Refresh a Token

When a token is about to expire (before the 24-hour window), use the refresh token:

```bash
curl -X POST http://localhost:8080/yawl/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "rt_abc123..."}'
```

---

## Task 6: Enable Rate Limiting

Rate limiting prevents abuse by restricting clients to N requests per minute.

### Configure Rate Limits

```yaml
yawl:
  authentication:
    rate-limiting:
      enabled: true
      requests-per-minute: 100
      burst-size: 10
      storage: memory  # or 'redis'
```

### Check Rate Limit Status

```bash
# Make a request and check headers
curl -v http://localhost:8080/yawl/api/v1/specifications \
  -H "Authorization: Bearer $TOKEN" 2>&1 | grep -i "ratelimit"

# Output:
# X-RateLimit-Limit: 100
# X-RateLimit-Remaining: 99
# X-RateLimit-Reset: 1740700860
```

### Handle Rate Limit Errors

When rate limit is exceeded, YAWL returns HTTP 429:

```bash
curl http://localhost:8080/yawl/api/v1/specifications \
  -H "Authorization: Bearer $TOKEN"

# HTTP/1.1 429 Too Many Requests
# Retry-After: 30
# {
#   "error": "RATE_LIMIT_EXCEEDED",
#   "message": "100 requests per minute limit exceeded",
#   "retryAfter": 30
# }
```

---

## Task 7: Set Up CSRF Protection for Forms

For web applications using HTML forms (not API requests), configure CSRF protection.

### Add Filter to web.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

  <!-- CSRF Protection Filter -->
  <filter>
    <filter-name>CsrfProtectionFilter</filter-name>
    <filter-class>org.yawlfoundation.yawl.authentication.CsrfProtectionFilter</filter-class>
    <init-param>
      <param-name>excludePaths</param-name>
      <param-value>/health,/api/v1/auth/token</param-value>
    </init-param>
  </filter>

  <filter-mapping>
    <filter-name>CsrfProtectionFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>

</web-app>
```

### Generate CSRF Token in JSP

```jsp
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%
  String csrfToken = new org.yawlfoundation.yawl.authentication.CsrfTokenManager()
    .generateToken(request);
  request.setAttribute("csrfToken", csrfToken);
%>

<form method="POST" action="/yawl/api/v1/cases">
  <input type="hidden" name="X-CSRF-Token" value="${csrfToken}" />
  <input type="text" name="specId" />
  <button type="submit">Start Case</button>
</form>
```

### Add Token to Form Submission (JavaScript)

```javascript
document.querySelectorAll('form').forEach(form => {
  // Get CSRF token from meta tag or cookie
  const token = document.querySelector('meta[name="csrf-token"]')?.content;

  if (token) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'X-CSRF-Token';
    input.value = token;
    form.appendChild(input);
  }
});
```

---

## Task 8: Audit and Monitor Authentication

### Enable Security Audit Logging

```yaml
yawl:
  authentication:
    audit:
      enabled: true
      log-level: INFO
      store: database  # Persist audit logs to DB
```

### View Audit Logs

```bash
# Get login/logout events for a user
curl -X GET 'http://localhost:8080/yawl/api/v1/auth/audit?userId=workflow-app' \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .

# Output:
# {
#   "events": [
#     {
#       "timestamp": "2026-02-28T14:00:00Z",
#       "event": "LOGIN",
#       "userId": "workflow-app",
#       "ipAddress": "192.168.1.100",
#       "status": "SUCCESS"
#     },
#     {
#       "timestamp": "2026-02-28T15:30:00Z",
#       "event": "LOGOUT",
#       "userId": "workflow-app",
#       "ipAddress": "192.168.1.100",
#       "status": "SUCCESS"
#     }
#   ]
# }
```

### Monitor Failed Authentication Attempts

```bash
# Get failed login attempts
curl -X GET 'http://localhost:8080/yawl/api/v1/auth/audit?status=FAILED&event=LOGIN' \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

---

## Task 9: Production Checklist

Before deploying to production:

- [ ] JWT secret is 32+ bytes and stored securely (not in code)
- [ ] Session database is configured (PostgreSQL/MySQL recommended)
- [ ] Client credentials are strong (>16 characters)
- [ ] Token expiration is set appropriately (24 hours default)
- [ ] Rate limiting is enabled and tuned
- [ ] CSRF protection is enabled for web forms
- [ ] Audit logging is enabled
- [ ] HTTPS is configured (JWT tokens must be sent over encrypted connections)
- [ ] Firewall restricts access to `/auth/` endpoints
- [ ] Monitor `/api/v1/auth/audit` for suspicious activity

### Example Production Config

```yaml
yawl:
  authentication:
    jwt:
      secret: ${YAWL_JWT_SECRET}  # Set via secure vault
      expiration-hours: 24
      refresh-token-expiration-days: 30
    rate-limiting:
      enabled: true
      requests-per-minute: 100
      storage: redis  # Use Redis for distributed rate limiting
    audit:
      enabled: true
      store: database
    csrf:
      enabled: true
      cookie-secure: true
      cookie-http-only: true
      cookie-same-site: STRICT

spring:
  datasource:
    url: jdbc:postgresql://db.production.internal:5432/yawl_sessions
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Never auto-create in production

server:
  ssl:
    enabled: true
    key-store: /etc/yawl/keystore.p12
    key-store-type: PKCS12
    key-store-password: ${KEYSTORE_PASSWORD}
```

---

## Troubleshooting

### JWT Secret Not Found

**Error:** `IllegalStateException: JWT signing key not configured`

**Fix:**
```bash
# Verify environment variable is set
echo $YAWL_JWT_SECRET

# If empty, set it
export YAWL_JWT_SECRET="$(openssl rand -base64 32)"
```

### Token Expires Immediately

**Issue:** Token shows `exp` in the past

**Fix:** Check server time synchronization:
```bash
# Verify system time
date
ntpstat  # Check NTP sync
```

### Rate Limit Not Working

**Issue:** No rate limiting despite enabled: true

**Fix:** Check Redis connectivity (if using Redis):
```bash
redis-cli ping
# Expected output: PONG
```

### CSRF Token Validation Fails

**Issue:** Form submission returns 403 Forbidden

**Fix:** Ensure token is in request:
```bash
# Verify token is included in POST data
curl -X POST -d "X-CSRF-Token=$CSRF_TOKEN" http://localhost:8080/...
```

---

## What's Next?

- **[Authentication Configuration Reference](../reference/yawl-authentication-config.md)** — All options
- **[How-To: Configure SPIFFE Identity](../how-to/configure-spiffe.md)** — Zero-trust auth
- **[How-To: Multi-Tenant Authentication](../how-to/configure-multi-tenancy.md)** — Tenant isolation
- **[Architecture: Authentication Design](../explanation/yawl-authentication-architecture.md)** — Deep dive

---

**Return to:** [Tutorial: Getting Started](../tutorials/yawl-authentication-getting-started.md)
