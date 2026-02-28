# YAWL Authentication Configuration Reference

Complete configuration options for JWT tokens, session management, CSRF protection, and rate limiting.

---

## JWT Configuration

### Overview

JWT (JSON Web Token) tokens are the primary authentication mechanism for the YAWL REST API.

### Configuration Parameters

```yaml
yawl:
  authentication:
    jwt:
      # Signing key (required)
      # Must be at least 256 bits (32 bytes)
      # Set via YAWL_JWT_SECRET env var or yawl.jwt.secret system property
      secret: ${YAWL_JWT_SECRET}

      # Token expiration in hours
      # Default: 24
      # Range: 1-2160 (1 hour - 3 months)
      expiration-hours: 24

      # Algorithm for token signing
      # Options: HS256 (default), HS384, HS512
      algorithm: HS256

      # Issuer claim
      # Default: "yawl-engine"
      issuer: yawl-engine

      # Audience claim
      # Default: "yawl-api"
      audience: yawl-api

      # Enable token refresh
      # Default: true
      enable-refresh: true

      # Refresh token expiration in days
      # Default: 30
      # Range: 1-365
      refresh-token-expiration-days: 30

      # Number of refresh tokens to keep per user
      # Default: 5
      max-refresh-tokens-per-user: 5

      # Clock tolerance for expiry validation (seconds)
      # Default: 60 (1 minute)
      clock-tolerance-seconds: 60
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YAWL_JWT_SECRET` | (required) | Base64-encoded signing key (32+ bytes) |
| `YAWL_JWT_EXPIRATION_HOURS` | 24 | Token lifetime in hours |
| `YAWL_JWT_ALGORITHM` | HS256 | Signing algorithm |
| `YAWL_JWT_ENABLE_REFRESH` | true | Enable refresh tokens |
| `YAWL_JWT_REFRESH_EXPIRATION_DAYS` | 30 | Refresh token lifetime |

### Example

```bash
export YAWL_JWT_SECRET="$(openssl rand -base64 32)"
export YAWL_JWT_EXPIRATION_HOURS=24
export YAWL_JWT_ALGORITHM=HS256
```

---

## Session Storage

### H2 (Embedded Database)

**Best for:** Development, testing

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./yawl-sessions;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: # Leave blank for H2

  jpa:
    hibernate:
      ddl-auto: create-drop  # Development only
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
```

### PostgreSQL

**Best for:** Production

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.example.com:5432/yawl_sessions
    driver-class-name: org.postgresql.Driver
    username: yawl_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate  # Production
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect
        format_sql: true
        show_sql: false
```

### MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://db.example.com:3306/yawl_sessions?useSSL=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: yawl_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

---

## Rate Limiting

### Configuration

```yaml
yawl:
  authentication:
    rate-limiting:
      # Enable rate limiting
      # Default: true
      enabled: true

      # Requests allowed per minute per client
      # Default: 100
      # Range: 10-10000
      requests-per-minute: 100

      # Burst allowance (requests above rate limit)
      # Default: 10
      # Range: 0-1000
      burst-size: 10

      # Storage backend for rate limit counters
      # Options: memory, redis
      # Default: memory
      storage: memory

      # Redis configuration (if storage: redis)
      redis:
        host: localhost
        port: 6379
        password: ${REDIS_PASSWORD}
        database: 0

      # Client identification header
      # Default: X-API-Client-ID
      client-id-header: X-API-Client-ID

      # Fallback client ID if header not provided
      # Options: ip, user, anonymous
      # Default: ip
      fallback-client-id: ip

      # Include paths to rate limit
      # Default: /api/**
      include-paths:
        - /api/**
        - /rest/**

      # Exclude paths from rate limiting
      # Default: none
      exclude-paths:
        - /health
        - /actuator/**
```

### Response Headers

When rate limited, YAWL includes these headers:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1740700860  # Unix timestamp
Retry-After: 30  # Seconds to wait
```

---

## CSRF Protection

### Configuration

```yaml
yawl:
  authentication:
    csrf:
      # Enable CSRF protection
      # Default: true
      enabled: true

      # Token length in bytes
      # Default: 32
      token-length: 32

      # Token validity period in minutes
      # Default: 60
      token-validity-minutes: 60

      # CSRF token header name
      # Default: X-CSRF-Token
      token-header: X-CSRF-Token

      # CSRF token parameter name (for forms)
      # Default: _csrf
      token-parameter: _csrf

      # Cookie configuration
      cookie:
        # Enable CSRF cookie
        # Default: true
        enabled: true

        # Cookie name
        # Default: XSRF-TOKEN
        name: XSRF-TOKEN

        # Secure flag (HTTPS only)
        # Default: true (false in dev)
        secure: true

        # HttpOnly flag (prevent JS access)
        # Default: true
        http-only: true

        # SameSite attribute
        # Options: Strict, Lax, None
        # Default: Strict
        same-site: STRICT

        # Cookie path
        # Default: /
        path: /

        # Cookie domain
        domain: .example.com

        # Max age in seconds
        # Default: 3600
        max-age: 3600

      # Paths requiring CSRF protection
      # Default: all POST/PUT/DELETE
      protected-methods:
        - POST
        - PUT
        - DELETE
        - PATCH

      # Paths to exclude from CSRF protection
      exclude-paths:
        - /api/v1/auth/token
        - /health
        - /webhook/**
```

---

## Audit Logging

### Configuration

```yaml
yawl:
  authentication:
    audit:
      # Enable audit logging
      # Default: true
      enabled: true

      # Log level
      # Options: DEBUG, INFO, WARN, ERROR
      # Default: INFO
      log-level: INFO

      # Storage backend
      # Options: memory, database, file
      # Default: database
      store: database

      # Events to audit
      events:
        # Login attempts
        login: true

        # Logout events
        logout: true

        # Token creation/refresh
        token-operations: true

        # Client registration changes
        client-changes: true

        # Permission changes
        permission-changes: true

        # Failed authentication
        failed-auth: true

      # Retention policy
      retention:
        # Keep audit logs for N days
        # Default: 90
        days: 90

        # Archive old logs
        archive: true

        # Compression for archived logs
        compress: true

      # Sensitive fields to mask in logs
      masked-fields:
        - password
        - secret
        - api_key
        - access_token
        - refresh_token
```

---

## Client Registration

### Configuration

```yaml
yawl:
  authentication:
    clients:
      # Allow client self-registration
      # Default: false
      allow-registration: false

      # Password policy for clients
      password-policy:
        # Minimum password length
        # Default: 12
        min-length: 12

        # Require uppercase
        # Default: true
        require-uppercase: true

        # Require lowercase
        # Default: true
        require-lowercase: true

        # Require digits
        # Default: true
        require-digits: true

        # Require special characters
        # Default: true
        require-special-chars: true

      # Client credentials expiry
      credentials-expiry:
        # Enable credentials expiry
        # Default: false
        enabled: false

        # Expiry period in days
        # Default: 365
        days: 365

        # Warning period in days
        # Default: 30
        warning-days: 30

      # Maximum concurrent sessions per client
      # Default: 5
      max-concurrent-sessions: 5
```

---

## Security Headers

### Configuration

```yaml
yawl:
  authentication:
    security-headers:
      # Strict-Transport-Security
      hsts:
        enabled: true
        max-age: 31536000
        include-subdomains: true
        preload: true

      # Content-Security-Policy
      csp:
        enabled: true
        directive: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"

      # X-Content-Type-Options
      nosniff:
        enabled: true

      # X-Frame-Options
      frame-options:
        enabled: true
        value: DENY

      # X-XSS-Protection
      xss-protection:
        enabled: true

      # Referrer-Policy
      referrer-policy:
        enabled: true
        value: strict-origin-when-cross-origin

      # Permissions-Policy
      permissions-policy:
        enabled: true
        directives:
          - "accelerometer=()"
          - "camera=()"
          - "geolocation=()"
          - "gyroscope=()"
          - "magnetometer=()"
          - "microphone=()"
```

---

## API Endpoints

### Authentication Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/auth/clients` | POST | Register new client |
| `/api/v1/auth/clients` | GET | List clients |
| `/api/v1/auth/clients/{id}` | GET | Get client details |
| `/api/v1/auth/clients/{id}` | PATCH | Update client |
| `/api/v1/auth/clients/{id}` | DELETE | Delete client |
| `/api/v1/auth/token` | POST | Get JWT token |
| `/api/v1/auth/refresh` | POST | Refresh token |
| `/api/v1/auth/revoke` | POST | Revoke token |
| `/api/v1/auth/validate` | GET | Validate token |
| `/api/v1/auth/sessions` | GET | List active sessions |
| `/api/v1/auth/sessions/{id}` | GET | Get session details |
| `/api/v1/auth/sessions/{id}` | DELETE | Logout session |
| `/api/v1/auth/audit` | GET | Query audit logs |

---

## Complete Example

```yaml
# application-production.yaml

server:
  ssl:
    enabled: true
    key-store: /etc/yawl/keystore.p12
    key-store-type: PKCS12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-alias: yawl-server

spring:
  datasource:
    url: jdbc:postgresql://rds.example.com:5432/yawl_auth
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 30
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect

yawl:
  authentication:
    jwt:
      secret: ${YAWL_JWT_SECRET}
      expiration-hours: 24
      refresh-token-expiration-days: 30
      algorithm: HS256

    rate-limiting:
      enabled: true
      requests-per-minute: 100
      storage: redis
      redis:
        host: redis.example.com
        port: 6379
        password: ${REDIS_PASSWORD}

    csrf:
      enabled: true
      cookie:
        secure: true
        http-only: true
        same-site: STRICT

    audit:
      enabled: true
      store: database
      retention:
        days: 365

    security-headers:
      hsts:
        enabled: true
        preload: true
      csp:
        enabled: true
```

---

## Troubleshooting

### Common Configuration Issues

**JWT secret not found**
```
Solution: Set YAWL_JWT_SECRET environment variable
export YAWL_JWT_SECRET="$(openssl rand -base64 32)"
```

**Database connection failed**
```
Solution: Verify JDBC URL, username, password
Check firewall and network connectivity
```

**Rate limiting not working**
```
Solution: If using Redis, verify Redis is running
Check Redis credentials
```

**CSRF token invalid**
```
Solution: Ensure token is in request
Check token expiry (default 60 minutes)
Verify token header matches configuration
```

---

## See Also

- [How-To: Set Up JWT Authentication](../how-to/yawl-authentication-setup.md)
- [Tutorial: Getting Started](../tutorials/yawl-authentication-getting-started.md)
- [Architecture: Authentication Design](../explanation/yawl-authentication-architecture.md)
