# YAWL Security Module

**Artifact:** `org.yawlfoundation:yawl-security:6.0.0-Alpha`

## Overview

The YAWL Security Module provides comprehensive security infrastructure for YAWL v6.0.0, including credential management, PKI (Public Key Infrastructure), threat detection, and input validation. This module implements zero-trust architecture principles and enforces security best practices throughout the YAWL platform.

## Core Components

### Credential Management

#### CredentialManager
**Purpose**: Centralized credential access interface for all YAWL components
- **Contract**: All credential access must go through this interface
- **Enforcement**: No hardcoded credentials allowed in production
- **Security**: Prevents credential leakage and enables secure rotation

```java
// Get credential manager instance
CredentialManager cm = CredentialManagerFactory.getInstance();

// Retrieve credentials with proper error handling
try {
    String dbPassword = cm.getCredential(CredentialKey.YAWL_DATABASE_PASSWORD);
    String apiKey = cm.getCredential(CredentialKey.YAWL_API_KEY);
} catch (CredentialUnavailableException e) {
    // Handle credential retrieval failure
}
```

#### CredentialKey
**Purpose**: Enumeration of all available credential types
- **Types**: Database passwords, API keys, JWT secrets, encryption keys
- **Organization**: Logical grouping of related credentials
- **Validation**: Type-safe credential references

#### SecretRotationService
**Purpose**: Automated secret rotation with zero-downtime transitions
- **Features**:
  - Periodic rotation (90 days for API keys, 1 hour for JWTs)
  - Dual-key operation during rotation windows
  - Automatic invalidation of superseded keys
  - Full audit trail of rotation events
  - Graceful degradation on rotation failures

```java
// Rotate API key on schedule
SecretRotationService rotation = SecretRotationService.getInstance();
String newApiKey = rotation.rotateSecret(CredentialKey.YAWL_API_KEY);

// Check credential validity
if (rotation.isSecretValid(CredentialKey.YAWL_API_KEY, currentApiKey)) {
    // API key is valid
}
```

### PKI (Public Key Infrastructure)

#### CertificateManager
**Purpose**: X.509 certificate and keystore management
- **Formats**: JKS and PKCS12 keystore support
- **Operations**: Load, store, and validate certificates
- **Security**: Private key protection and secure keystore handling

```java
// Initialize certificate manager
CertificateManager certMgr = new CertificateManager(
    "/path/to/keystore.jks",
    "keystorePassword",
    "JKS"
);

// Load certificate
X509Certificate cert = certMgr.getCertificate("signing_cert_alias");

// Verify certificate chain
boolean isValid = certMgr.validateCertificateChain(cert);
```

#### DocumentSigner
**Purpose**: Digital signing of workflow specifications and documents
- **Algorithms**: RSA (SHA1withRSA, SHA256withRSA, etc.) and ECDSA support
- **Formats**: Arbitrary bytes, text, and XML content
- **Metadata**: Includes timestamp and certificate information

```java
// Create document signer
DocumentSigner signer = new DocumentSigner(certMgr, "SHA256withRSA");

// Sign YAWL specification
byte[] specXml = loadSpecification();
byte[] signature = signer.signDocument(specXml, "signing_cert_alias");
```

#### SignatureVerifier
**Purpose**: Verify digital signatures and certificate chains
- **Validation**: Signature verification with certificate chain checking
- **Revocation**: CRL and OCSP support for certificate revocation
- **Results**: Detailed verification results with failure reasons

```java
// Create verifier
SignatureVerifier verifier = new SignatureVerifier();

// Verify specification signature
VerificationResult result = verifier.verifySignature(
    specXml,
    signature,
    cert,
    "SHA256withRSA"
);

if (result.isValid()) {
    // Signature is valid
}
```

### Threat Detection & Defense

#### AnomalyDetectionSecurity
**Purpose**: Detect suspicious patterns and potential security threats
- **Monitoring**: Client behavior analysis and request pattern detection
- **Thresholds**: Configurable anomaly detection thresholds
- **Response**: Automatic blocking of suspicious activity

```java
// Initialize anomaly detection
AnomalyDetectionSecurity detector = new AnomalyDetectionSecurity();

// Check request for anomalies
AnomalyLevel level = detector.detectAnomaly(
    clientIp,
    userAgent,
    requestRate,
    unusualActions
);

if (level == AnomalyLevel.SUSPICIOUS) {
    // Trigger additional verification
}
```

#### AttackPatternDetector
**Purpose**: Identify attack patterns and prevent security breaches
- **Patterns**: SQL injection, command injection, DoS attacks
- **Mitigation**: Automatic blocking of malicious requests
- **Reporting**: Detailed attack logging and analysis

```java
// Check for attack patterns
AttackPatternDetector detector = new AttackPatternDetector();

if (detector.detectSqlInjection(requestParameters)) {
    // Block request and log attack
}
```

#### InputValidator
**Purpose**: Validate and sanitize all inputs to prevent injection attacks
- **Validation**: Type checking, length limits, format validation
- **Sanitization**: XSS and injection prevention
- **Exceptions**: Detailed validation errors for debugging

```java
// Validate user input
try {
    String sanitized = InputValidator.validateInput(
        userInput,
        InputType.TEXT,
        maxLength: 1000
    );
    // Use sanitized input
} catch (InputValidationException e) {
    // Handle invalid input
}
```

### API Security

#### ApiKeyRateLimitRegistry
**Purpose**: Rate limiting and API key management
- **Limits**: Configurable rate limits per API key
- **Tracking**: Request counting and time window management
- **Blocking**: Automatic blocking of rate-limited clients

```java
// Check API key rate limit
if (rateLimitRegistry.allowRequest(apiKey, endpoint)) {
    // Process request
} else {
    // Rate limit exceeded
}
```

#### IdempotencyKeyStore
**Purpose**: Prevent duplicate requests with idempotency keys
- **Duplicate Prevention**: Store processed idempotency keys
- **TTL**: Configurable key expiration
- **Performance**: Efficient caching with response caching

```java
// Process with idempotency key
String idempotencyKey = generateIdempotencyKey();
CachedResponse response = idempotencyStore.getOrProcess(key, () -> {
    return processRequest();
});
```

## Security Best Practices

### 1. Zero Trust Architecture
- **Never trust**: All inputs must be validated
- **Always verify**: Check credentials and permissions for every request
- **Principle of least privilege**: Grant minimum necessary access

### 2. Credential Security
- **No hardcoded credentials**: All credentials from CredentialManager
- **Regular rotation**: Automated secret rotation on defined schedules
- **Secure storage**: Credentials never stored in plaintext

### 3. PKI Security
- **Strong algorithms**: Use modern cryptographic algorithms (SHA-256, ECDSA)
- **Certificate validation**: Always validate certificate chains and revocation status
- **Private key protection**: Private keys never exposed or transmitted

### 4. Input Validation
- **Defense in depth**: Validate at multiple layers (API, workflow, database)
- **Type safety**: Use proper data types and enforce constraints
- **Sanitization**: Remove or escape dangerous characters

### 5. Monitoring & Detection
- **Anomaly detection**: Monitor for unusual behavior patterns
- **Attack prevention**: Automatically detect and block common attacks
- **Audit logging**: Log all security-relevant events

## Configuration

### Environment Variables
```bash
# Credential Manager configuration
YAWL_CREDENTIAL_PROVIDER=vault
YAWL_VAULT_URL=https://vault.example.com
YAWL_VAULT_TOKEN=secret-token

# PKI configuration
YAWL_KEYSTORE_PATH=/path/to/keystore.jks
YAWL_KEYSTORE_PASSWORD=secure-password
YAWL_CERT_ALIAS=signing_cert

# Security settings
YAWL_MAX_REQUEST_RATE=1000
YAWL_ANOMALY_THRESHOLD=10
YAWL_IDEMPOTENCY_TTL=3600
```

### Java Properties
```properties
# Security configuration
yawl.security.credential.provider=vault
yawl.security.pki.keystore.type=JKS
yawl.security.attack.max.attempts=5
yawl.security.input.max.length=10000
```

## Dependencies

### External Dependencies
- **Bouncycastle 1.77**: Core cryptographic operations
- **Bouncycastle Mail**: S/MIME and CMS operations
- **Bouncycastle PKIX**: X.509, CRL, and OCSP support
- **commons-lang3**: Utility libraries
- **commons-io**: File I/O operations
- **log4j-api + log4j-core**: Logging framework

## Performance Considerations

### Optimizations
- **Caching**: Cache frequently accessed certificates and credentials
- **Batch operations**: Process multiple security operations in bulk
- **Lazy loading**: Load certificates only when needed
- **Connection pooling**: Reuse database and network connections

### Memory Management
- **Certificate caching**: Limit the number of cached certificates
- **Key storage**: Ensure proper cleanup of sensitive data
- **Log rotation**: Implement log rotation to prevent memory issues

## Testing

### Security Testing
The module includes comprehensive security tests:
- **TestAnomalyDetectionSecurity**: Anomaly detection validation
- **TestAttackPatternDetector**: Attack pattern detection
- **TestInputValidator**: Input validation and sanitization
- **TestIdempotencyKeyStore**: Idempotency key management
- **TestSafeErrorResponseBuilder**: Secure error handling

### Integration Testing
- **SecurityFixesTest**: Security fix validation
- **SqlInjectionProtectionTest**: SQL injection prevention
- **CommandInjectionProtectionTest**: Command injection prevention
- **XssProtectionTest**: Cross-site scripting prevention

## Troubleshooting

### Common Issues

**Credential Not Available**
- Verify credential provider configuration
- Check network connectivity to vault/secure storage
- Validate authentication tokens and permissions

**Certificate Validation Fails**
- Check certificate expiration dates
- Verify certificate chain configuration
- Ensure CRL/OCSP endpoints are accessible

**Attack Detection False Positives**
- Adjust anomaly detection thresholds
- Review client behavior patterns
- Fine-tune rule sets for specific environments

**Performance Issues**
- Monitor security operation timing
- Check caching effectiveness
- Optimize database queries for security operations

## Compliance & Standards

### Security Standards
- **OWASP Top 10**: Protection against common web vulnerabilities
- **PCI DSS**: Payment card industry security standards
- **NIST SP 800-53**: Federal security standards
- **ISO 27001**: Information security management

### Auditing
- **Security logging**: All security events logged with proper timestamps
- **Access control**: Detailed access logging for all operations
- **Change tracking**: Audit trail for security configuration changes
- **Compliance reporting**: Generate compliance reports for standards

## Future Enhancements

### Planned Features
- **HSM Support**: Hardware Security Module integration for key storage
- **PKCS#11 Support**: Integration with PKCS#11 compliant devices
- **OAuth 2.0/OpenID Connect**: Modern authentication protocols
- **JWT Validation**: Enhanced JWT validation and claims verification
- **Security Metrics**: Comprehensive security monitoring and metrics

### Technology Updates
- **Bouncycastle 2.x**: Migration to latest Bouncycastle version
- **ECDSA Default**: Switch to ECDSA as default cryptographic algorithm
- **TLS 1.3**: Upgrade to latest TLS protocol version