# YAWL Authentication Module

## Overview

The `yawl-authentication` module provides comprehensive authentication and authorization services for the YAWL workflow engine. It manages session creation, authentication, and maintenance for both custom services and external applications, ensuring secure access to YAWL engine resources.

## Purpose

This module enables secure integration of external applications with the YAWL engine through:

- **Session Management**: Creation and maintenance of authentication sessions
- **Client Authentication**: Verification of external service credentials
- **Security Features**: JWT token management, CSRF protection, rate limiting
- **Audit Logging**: Security event tracking and monitoring
- **Session Caching**: Efficient session state management

## Key Classes and Interfaces

### Core Session Management

| Class | Purpose |
|-------|---------|
| `YAbstractSession` | Base class for all authentication sessions |
| `YSession` | Session for internal YAWL services |
| `YExternalSession` | Session for external applications |
| `YServiceSession` | Session for YAWL service references |
| `YSessionTimer` | Session timeout and expiration management |

### Client Authentication

| Class | Purpose |
|-------|---------|
| `YClient` | Base class for all authenticated clients |
| `YExternalClient` | External application client implementation |
| `ISessionCache` | Interface for session storage and retrieval |
| `YSessionCache` | Concrete session cache implementation |

### Security Components

| Class | Purpose |
|-------|---------|
| `JwtManager` | JWT token generation and validation |
| `CsrfTokenManager` | Cross-site request forgery protection |
| `CsrfProtectionFilter` | CSRF protection filter implementation |
| `RateLimitFilter` | API rate limiting mechanism |
| `SecurityAuditLogger` | Security event logging and audit trail |

### Authentication Flow

1. **Client Authentication**: External clients provide credentials
2. **Session Creation**: Upon successful authentication, a session is created
3. **Token Management**: JWT tokens are issued for subsequent requests
4. **Session Maintenance**: Sessions are monitored for timeouts
5. **Authorization**: Requests are validated against session tokens

## Dependencies

### Internal Dependencies
- `yawl-engine`: Core YAWL engine functionality
- `yawl-elements`: YAWL specification and workflow elements
- `yawl-stateless`: Stateless engine components

### External Dependencies
- `jakarta.persistence`: JPA annotations for entity mapping
- `io.jsonwebtoken`: JWT token processing
- `org.apache.logging.log4j`: Logging framework
- `org.jdom2`: XML processing

## Usage Examples

### Basic Client Authentication

```java
// Create a client with credentials
YExternalClient client = new YExternalClient();
client.setUserName("service-app");
client.setPassword("secure-password");

// Create a session
YSession session = new YSession(client, 3600); // 1 hour timeout

// Check connection
boolean connected = sessionCache.connect(
    "service-app",
    "secure-password",
    3600
);
```

### JWT Token Management

```java
// Generate a JWT token
String token = JwtManager.generateToken("service-app", "service-role");

// Validate a token
boolean isValid = JwtManager.validateToken(token);

// Extract claims from token
Claims claims = JwtManager.parseClaims(token);
```

### Session Cache Operations

```java
// Connect and get session handle
String handle = sessionCache.connect("client", "password", 3600);

// Check if session is active
boolean isActive = sessionCache.checkConnection(handle);

// Get session details
YAbstractSession session = sessionCache.getSession(handle);

// Disconnect session
sessionCache.disconnect(handle);
```

### CSRF Protection

```java
// Generate CSRF token
String csrfToken = CsrfTokenManager.generateToken();

// Validate CSRF token
boolean isValid = CsrfTokenManager.validateToken(csrfToken);
```

## Configuration

### JWT Configuration

Set the JWT signing key via:
- System property: `-Dyawl.jwt.secret=<your-secret>`
- Environment variable: `YAWL_JWT_SECRET=<your-secret>`

**Security Note**: The secret key should be at least 256 bits (32 bytes) and stored securely.

### Session Configuration

- Default timeout: 3600 seconds (1 hour)
- Cache size: Configurable via `YSessionCache`
- Cleanup interval: Configurable via `YSessionTimer`

## Security Considerations

1. **Password Management**: Store passwords securely using appropriate hashing
2. **Token Security**: Use HTTPS in production to protect JWT tokens
3. **Session Security**: Implement proper session timeout mechanisms
4. **Rate Limiting**: Protect against brute force attacks with rate limiting
5. **Audit Logging**: Maintain comprehensive security event logs

## Thread Safety

- All public methods are thread-safe
- Session cache operations use concurrent collections
- JWT operations are synchronized for key access
- Security filters are designed for concurrent HTTP requests

## Performance

- Session cache operations are O(1) complexity
- JWT validation uses efficient cryptographic operations
- Rate limiting uses sliding window algorithm
- Audit logging uses async logging for minimal performance impact

## Extension Points

The module provides several extension points for custom implementations:

1. **Custom Session Cache**: Implement `ISessionCache` interface
2. **Custom Authentication**: Extend `YClient` class
3. **Custom Security Filters**: Implement Jakarta Filter interface
4. **Custom JWT Handlers**: Extend `JwtManager` class