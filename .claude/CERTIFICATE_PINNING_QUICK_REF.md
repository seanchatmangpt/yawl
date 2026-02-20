# Certificate Pinning - Quick Reference

## What Was Added

Certificate pinning for Z.AI API HTTPS connections to prevent MITM attacks.

## Key Files

| File | Lines | Purpose |
|------|-------|---------|
| PinnedTrustManager.java | 320 | Custom X509ExtendedTrustManager validating SHA-256 public key pins |
| ZaiHttpClient.java | +114 | Integration: automatic SSL context with pinning |
| PinnedTrustManagerTest.java | 423 | Unit tests using real system certificates |
| ZaiHttpClientCertificatePinningTest.java | 323 | Integration tests for client configuration |
| docs/v6/certificate-pinning-zai.md | 358 | Production security documentation |

## How It Works (30-Second Version)

1. Server sends certificate with public key
2. Extract public key, compute SHA-256 hash, Base64 encode
3. Compare to hardcoded list in ZaiHttpClient
4. ✓ Match = Allow connection | ✗ No match = Throw SSLPeerUnverifiedException

## Pinning Configuration

```java
// In ZaiHttpClient.java (lines 63-68)
private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
    "sha256/L9CowLk96O4M3HMZX/dxC1m/zJJYdQG9xUakwRV8yb4=",  // Primary
    "sha256/mK87OJ3fZtIf7ZS0Eq6/5qG3H9nM2cL8wX5dP1nO9q0="   // Backup
);
```

## Using ZaiHttpClient (Pinning is Automatic)

```java
// Create client - pinning enabled by default
ZaiHttpClient client = new ZaiHttpClient(apiKey);

// All connections enforce certificate pinning
ChatResponse response = client.createChatCompletionRecord(request);
```

## Updating Certificate Pins

**When Z.AI rotates their certificate:**

1. Get new certificate pin: `openssl s_client -connect api.z.ai:443 | ...`
2. Add to backup pins list in ZaiHttpClient
3. Deploy code with both pins
4. After rotation, remove old pin, promote backup pin

**Example**: Before rotation:
```java
List.of(
    "sha256/OLD_PIN",   // Current
    "sha256/NEW_PIN"    // New (pre-rotated)
)
```

After rotation:
```java
List.of(
    "sha256/NEW_PIN",      // Now current
    "sha256/FUTURE_PIN"    // For next rotation
)
```

## Security Monitoring

**Log messages to watch for:**

```
[ZAI-PIN] Initialized with 2 pin(s). Fallback: disabled
[ZAI-PIN] Certificate pin validated successfully        ← Good
[ZAI-PIN] Certificate pin NOT in accepted list          ← Alert! Possible MITM
[ZAI-PIN] Attempting fallback validation...            ← Certificate rotation?
```

**Alert on**:
- Multiple SEVERE logs (pin failures) → Possible attack
- Certificate rotation issues
- SSL context creation errors

## Testing

```bash
# Run all pinning tests
mvn test -pl yawl-integration \
  -Dtest=PinnedTrustManagerTest,ZaiHttpClientCertificatePinningTest

# Run specific test class
mvn test -pl yawl-integration \
  -Dtest=PinnedTrustManagerTest#InitializationTests
```

## Error Handling

```java
try {
    ZaiHttpClient client = new ZaiHttpClient(apiKey);
    response = client.createChatCompletionRecord(request);
} catch (IOException e) {
    if (e.getCause() instanceof SSLPeerUnverifiedException) {
        // Pin validation failed - possible MITM attack
        logger.severe("Certificate validation failed: " + e.getMessage());
        // Alert security team
        // Do NOT retry automatically
    }
}
```

## FAQ

**Q: Will pinning affect my existing code?**
A: No. Pinning is enabled automatically. No code changes needed.

**Q: What if Z.AI certificate expires?**
A: Update pins before expiration. Follow the rotation process.

**Q: Can I disable pinning?**
A: No. Pinning is mandatory for security.

**Q: What about testing with self-signed certs?**
A: Use PinnedTrustManager with fallback enabled:
```java
PinnedTrustManager tm = new PinnedTrustManager(
    testPins, systemDefaultTrustManager, true);  // true = enable fallback
```

**Q: How are pins validated?**
A: Public key pins are hardcoded in source. This prevents accidental changes.

## Implementation Details

### Pinning Process (checkServerTrusted)

```
Input: X509Certificate[] chain, String authType
  ↓
1. Check chain is not null/empty
  ↓
2. Extract public key from cert[0]
  ↓
3. Compute: SHA-256(DER-encoded-public-key)
  ↓
4. Base64 encode: sha256/[hash]
  ↓
5. Compare against acceptedPins list
  ↓
6a. MATCH → Log success, return normally
6b. NO MATCH → Try fallback (if enabled)
6c. FALLBACK FAILS → Throw SSLPeerUnverifiedException
```

### Classes & Methods

**PinnedTrustManager**:
- `checkServerTrusted()` - Primary validation (public key pin)
- `checkClientTrusted()` - Delegates to default trust manager
- `getAcceptedIssuers()` - Delegates to default trust manager
- Socket/Engine variants - Delegate to main checkServerTrusted

**ZaiHttpClient**:
- `createHttpClientWithPinning()` - Configure HttpClient with SSL context
- `createPinnedSslContext()` - Create SSL context with PinnedTrustManager
- `getDefaultTrustManager()` - Load system default (for fallback)

## Security Properties

| Property | Status | Details |
|----------|--------|---------|
| MITM Prevention | ✓ | Certificates must match pin |
| Rotation Support | ✓ | Multiple pins enable rotation |
| Fail-Secure | ✓ | Exceptions on validation failure |
| Logging | ✓ | All events logged with [ZAI-PIN] |
| No Configuration | ✓ | Pins hardcoded in source |
| Virtual Threads | ✓ | Works with Java 21+ |

## Performance

- **Overhead**: Minimal - public key extraction only on SSL handshake
- **Caching**: No caching needed - validation per connection startup
- **Virtual Threads**: Compatible with Java 25 virtual threads

## References

- Implementation: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/zai/`
- Tests: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/zai/`
- Documentation: `/home/user/yawl/docs/v6/certificate-pinning-zai.md`

## Contact

For questions about certificate pinning:
1. See `/home/user/yawl/docs/v6/certificate-pinning-zai.md` (comprehensive guide)
2. Check test files for usage examples
3. Review Javadoc in PinnedTrustManager.java
