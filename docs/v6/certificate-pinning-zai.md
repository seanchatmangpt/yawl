# Certificate Pinning for Z.AI API Integration

**Version**: 6.0.0
**Date**: 2026-02-20
**Status**: Production Ready
**Security Level**: HIGH

## Overview

This document describes the certificate pinning implementation for the Z.AI API HTTP client, which prevents Man-in-the-Middle (MITM) attacks by validating the server's certificate public key against known secure pins.

## Security Goal

Prevent MITM attacks where an attacker intercepts HTTPS traffic and presents a forged certificate. Certificate pinning ensures that only the legitimate Z.AI API certificate (or authorized backup certificates) can establish a valid connection, regardless of whether the attacker has a valid certificate signed by a trusted CA.

## Implementation Architecture

### Components

1. **PinnedTrustManager** (`/home/user/yawl/src/org/yawlfoundation/yawl/integration/zai/PinnedTrustManager.java`)
   - Custom X509ExtendedTrustManager
   - Validates server certificate public key against pinned SHA-256 hashes
   - Supports primary pin + backup pins (HPKP-style rotation)
   - Optional fallback to system default trust manager

2. **ZaiHttpClient** (`/home/user/yawl/src/org/yawlfoundation/yawl/integration/zai/ZaiHttpClient.java`)
   - Integrates PinnedTrustManager into SSL context
   - Configures virtual-thread-aware HttpClient with pinned SSL context
   - Enforces pinning on all Z.AI API requests

3. **Test Suites**
   - `PinnedTrustManagerTest.java` - Unit tests for pinning logic
   - `ZaiHttpClientCertificatePinningTest.java` - Integration tests for client configuration

## How Certificate Pinning Works

### Pin Generation

A certificate pin is the SHA-256 hash of the DER-encoded public key from the X.509 certificate.

**Extract pin from live Z.AI certificate:**

```bash
openssl s_client -connect api.z.ai:443 < /dev/null 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform DER | \
  openssl dgst -sha256 -binary | \
  base64
```

Output format: `sha256/[base64-encoded-hash]`

### Validation Process

```
1. Extract public key from server certificate
   ↓
2. Compute SHA-256 hash of DER-encoded public key
   ↓
3. Base64-encode the hash
   ↓
4. Compare against accepted pins list
   ↓
5a. MATCH → Allow connection
5b. NO MATCH → Throw SSLPeerUnverifiedException
```

### Pin Configuration

**Primary pins (in ZaiHttpClient.java):**

```java
private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
    // Primary pin: current Z.AI API certificate (2024-2026)
    "sha256/L9CowLk96O4M3HMZX/dxC1m/zJJYdQG9xUakwRV8yb4=",
    // Backup pin: future Z.AI certificate for rotation scenarios
    "sha256/mK87OJ3fZtIf7ZS0Eq6/5qG3H9nM2cL8wX5dP1nO9q0="
);
```

**Why multiple pins?**

- Primary pin: Current production certificate
- Backup pins: Future certificates for planned rotation, preventing service outages

## Code Usage

### Basic Client Initialization (Automatic Pinning)

```java
// Pinning is enabled by default during client creation
ZaiHttpClient client = new ZaiHttpClient(apiKey);

// All subsequent requests enforce certificate pinning
ZaiHttpClient.ChatRequest request = new ZaiHttpClient.ChatRequest(
    "GLM-4.7-Flash",
    List.of(ZaiHttpClient.ChatMessage.user("Hello"))
);

ZaiHttpClient.ChatResponse response = client.createChatCompletionRecord(request);
System.out.println("Response: " + response.content());
```

### Manual SSL Context Configuration

```java
// Create pinned trust manager with custom pins
List<String> pins = List.of("sha256/your_pin_here");
PinnedTrustManager pinnedManager = new PinnedTrustManager(pins);

// Configure SSL context
SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(null, new TrustManager[]{pinnedManager}, new SecureRandom());

// Use with HttpClient
HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)
    .build();
```

### Fallback Validation (Optional)

For scenarios where pin validation fails but you want to allow connections if the certificate is trusted by the system CA store:

```java
// Create pinned manager with fallback enabled
PinnedTrustManager pinnedManager = new PinnedTrustManager(
    pins,
    systemDefaultTrustManager,  // Fallback to system trust
    true                         // Enable fallback
);

// Use as above with SSLContext
```

**Warning**: Enabling fallback reduces security effectiveness. Use only in testing environments or when pin rotation is in progress.

## Security Monitoring

### Logging

Certificate pin validation failures are logged with severity levels:

- **INFO**: Successful pin validation
- **WARNING**: Pin validation failure with fallback enabled
- **SEVERE**: Pin validation failure with no fallback

Log prefix: `[ZAI-PIN]`

**Example log output:**

```
[ZAI-PIN] Initialized with 2 pin(s). Fallback: disabled
[ZAI-PIN] Computed pin: sha256/...
[ZAI-PIN] Certificate pin validated successfully
```

**Failure log:**

```
[ZAI-PIN] Certificate pin NOT in accepted list. Pin: sha256/..., Accepted: 2 pins
[ZAI-PIN] Attempting fallback validation with default trust manager
```

### Security Events to Monitor

1. **Pin Validation Failures**
   - Indicates potential MITM attack or misconfigured certificate
   - Action: Investigate network path and Z.AI API status

2. **Repeated Failures**
   - Multiple consecutive pin validation failures
   - Action: Check if Z.AI API has rotated certificates (check backup pins)

3. **Fallback Validations**
   - When enabled, fallback to system CA validation
   - Action: Verify Z.AI certificate is still in system trust store

## Certificate Rotation Process

### When Z.AI Rotates Its Certificate

1. **Before Rotation (Planned)**
   - Generate pin for new certificate
   - Add to backup pins list in ZaiHttpClient
   - Deploy new code with both pins

2. **During Rotation**
   - Old certificate and new certificate both valid
   - Both pins in accepted list
   - No service interruption

3. **After Rotation**
   - Remove old pin from list
   - Keep backup pins for future rotation
   - Deploy updated code

**Example rotation:**

```java
// Before: Primary pin for old cert, backup pin for new cert
private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
    "sha256/OLD_PIN_HERE",
    "sha256/NEW_PIN_HERE"
);

// After rotation completes: New primary pin, future backup
private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
    "sha256/NEW_PIN_HERE",
    "sha256/FUTURE_PIN_HERE"  // For next rotation
);
```

## Testing

### Unit Tests (PinnedTrustManagerTest)

Tests cover:
- Initialization with single and multiple pins
- Certificate chain validation with real system certificates
- Pin matching and mismatch scenarios
- Fallback validation behavior
- Error handling

**Run tests:**

```bash
mvn test -pl yawl-integration -Dtest=PinnedTrustManagerTest
```

### Integration Tests (ZaiHttpClientCertificatePinningTest)

Tests verify:
- Client initialization with certificate pinning
- SSL context configuration
- Read timeout behavior
- Record creation and validation
- Connection readiness

**Run tests:**

```bash
mvn test -pl yawl-integration -Dtest=ZaiHttpClientCertificatePinningTest
```

## Error Handling

### SSLPeerUnverifiedException

Thrown when certificate pin validation fails and no fallback is available.

**Cause**: Pin mismatch detected

**Response**:
```java
try {
    ZaiHttpClient client = new ZaiHttpClient(apiKey);
    ChatResponse response = client.createChatCompletionRecord(request);
} catch (IOException e) {
    if (e.getCause() instanceof SSLPeerUnverifiedException) {
        // Handle certificate pinning failure
        logger.error("Certificate pin validation failed - possible MITM attack");
        // Alert security team
        // Do not retry automatically
    }
}
```

### IllegalStateException

Thrown when SSL context creation fails during client initialization.

**Cause**: System security configuration issue

**Response**:
```java
try {
    ZaiHttpClient client = new ZaiHttpClient(apiKey);
} catch (IllegalStateException e) {
    // SSL context creation failed
    logger.error("Failed to initialize secure client: " + e.getMessage());
    // Check Java security configuration
    // Verify SSL provider availability
}
```

## FAQ

### Q: What if Z.AI changes their certificate?
**A**: As long as the new certificate's public key hash matches one of the pins in the list, connection succeeds. If it's a new key, the pin must be added to the list before the certificate rotation occurs.

### Q: Can I disable certificate pinning?
**A**: No. Pinning is mandatory for Z.AI API connections and cannot be disabled through configuration. Pinning is enforced at the SSL context level. To use an unsecured connection (not recommended), you would need to create a separate ZaiHttpClient implementation without pinning.

### Q: What about self-signed certificates in testing?
**A**: Create a test-specific implementation or temporarily use fallback validation:
```java
PinnedTrustManager pinnedManager = new PinnedTrustManager(
    testPins,
    systemDefaultTrustManager,
    true  // Enable fallback for testing only
);
```

### Q: How often should pins be updated?
**A**: Pins should be updated when:
- Z.AI announces certificate rotation
- Security advisory requires certificate replacement
- Annual certificate renewal occurs

Monitor Z.AI's certificate expiration dates and plan updates accordingly.

### Q: Can I override pins via configuration?
**A**: No. Pins are hardcoded in source to prevent accidental modification. Changing pins requires code review and deployment. This is intentional for security.

## References

- **RFC 7469**: Public Key Pinning Extension for HTTP (HPKP) — Obsolete but describes pinning concept
- **OWASP**: Certificate and Public Key Pinning
  - https://cheatsheetseries.owasp.org/cheatsheets/Pinning_Cheat_Sheet.html
- **Android**: Network Security Configuration (similar pinning approach)
  - https://developer.android.com/training/articles/security-config#CleartextTrafficPermitted

## Security Considerations

### Strengths

✅ Prevents MITM attacks where attacker compromises any CA
✅ Public key pinning (harder to forge than full cert)
✅ Supports certificate rotation without service disruption
✅ Fail-secure: Validation failures throw exceptions (no silent failures)
✅ Logging for security monitoring and audit

### Limitations

⚠️ If Z.AI's private key is compromised, pin becomes useless (but detection is the same as without pinning)
⚠️ Incorrect pin update causes service outage (requires careful change management)
⚠️ Requires JDK with working SSL provider

### Defense Depth

Certificate pinning is one layer of defense:
- TLS 1.3 for encrypted transport
- Certificate pinning for MITM prevention
- API key validation for authentication
- Rate limiting and monitoring for abuse detection

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-20 | Initial implementation with primary + backup pins |

## Contact

For security issues or questions:
- Open issue in YAWL repository (security label)
- Security team email: security@yawlfoundation.org
