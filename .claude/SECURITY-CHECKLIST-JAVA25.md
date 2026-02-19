# Java 25 Security Checklist for YAWL v6.0.0

**Date**: Feb 2026 | **Version**: 1.0 | **Status**: Production Guidelines

---

## 🧭 Navigation

**Related Documentation**:
- **[JAVA-25-FEATURES.md](JAVA-25-FEATURES.md)** - Feature overview
- **[ARCHITECTURE-PATTERNS-JAVA25.md](ARCHITECTURE-PATTERNS-JAVA25.md)** - Implementation details
- **[BUILD-PERFORMANCE.md](BUILD-PERFORMANCE.md)** - Build security automation
- **[BEST-PRACTICES-2026.md](BEST-PRACTICES-2026.md)** - Security best practices (Part 2-4)
- **[HYPER_STANDARDS.md](HYPER_STANDARDS.md)** - Code quality enforcement
- **[../CLAUDE.md](../CLAUDE.md)** - Project guardrails (H section)

**Checklist Sections**:
- ✅ [Section 1: Mandatory Requirements](#section-1-mandatory-security-requirements) (Pre-deployment)
- ✅ [Section 2: Code Quality](#section-2-code-quality--standards-compliance) (All commits)
- ✅ [Section 3: Deployment Config](#section-3-deployment-configuration) (Production)
- ✅ [Section 4: Monitoring](#section-4-monitoring--incident-response) (Runtime)
- ✅ [Section 5: Compliance](#section-5-compliance-frameworks) (Frameworks)

---

## Pre-Deployment Security Validation

**CRITICAL**: All items in Section 1 must be ✅ before production deployment.

### Section 1: Mandatory Security Requirements

#### 1.1 Patch Level & Update
- [ ] Running Java 25.0.2 or later (check: `java -version`)
- [ ] Quarterly updates applied (Jan, Apr, Jul, Oct)
- [ ] JVM flags: `-XX:+UseG1GC` or approved GC for your deployment
- [ ] Compact object headers enabled: `-XX:+UseCompactObjectHeaders`

#### 1.2 Deprecated API Usage
```bash
# Run this before every release
jdeprscan --for-removal build/libs/yawl.jar
```
- [ ] Output is empty (no deprecated APIs)
- [ ] No use of removed `SecurityManager`
- [ ] No use of removed `java.applet` APIs
- [ ] No use of `javax.rmi.activation`

#### 1.3 Transport Layer Security
- [ ] TLS 1.3 enforced in production (via `-Djdk.tls.disabledAlgorithms="TLSv1,TLSv1.1,TLSv1.2"`)
- [ ] TLS 1.2 only permitted for legacy backend systems with explicit approval
- [ ] Cipher suites hardened: `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256`, `TLS_CHACHA20_POLY1305_SHA256`
- [ ] Certificate pinning enabled for sensitive API calls (see `.claude/CERTIFICATE-PINNING.md`)
- [ ] mTLS configured for service-to-service communication

#### 1.4 Cryptography Standards
- [ ] RSA keys: 3072-bit minimum (CNSA compliance)
- [ ] ECDSA curves: P-256, P-384, P-521 only (no weaker curves)
- [ ] Symmetric encryption: AES-GCM only (no AES-CBC, no DES, no 3DES)
- [ ] Key derivation: HKDF or Argon2 (no MD5, no SHA-1)
- [ ] Disabled weak ciphers in `jdk.certpath.disabledAlgorithms` JVM property

```bash
# Verify crypto configuration
java -XshowSettings:properties 2>&1 | grep -i "jdk.certpath.disabledAlgorithms"
java -XshowSettings:properties 2>&1 | grep -i "jdk.tls.disabledAlgorithms"
```

#### 1.5 Access Control (No Security Manager)
- [ ] Removed all `System.setSecurityManager()` calls
- [ ] Removed all permission checks via `SecurityManager`
- [ ] Implemented Spring Security or custom RBAC (see `.claude/RBAC-IMPLEMENTATION.md`)
- [ ] Replaced `PermissionCollection` with explicit authorization checks
- [ ] No use of `SubjectDelegationPermission` (deprecated)

**Code Search**:
```bash
grep -r "SecurityManager" src/main/java/
grep -r "Permission" src/main/java/org/yawlfoundation/yawl/engine/
# Should return only explicit permission classes, not java.lang.SecurityManager
```

#### 1.6 Input Validation & Parameterized Queries
- [ ] All SQL queries use `PreparedStatement` with parameter binding
- [ ] No string concatenation in SQL queries
- [ ] No XXE (XML External Entity) vulnerabilities in JDOM parsing
- [ ] No code injection via XPath expressions (Saxon)

**Code Review**:
```java
// ❌ BAD
String query = "SELECT * FROM cases WHERE id=" + caseID;

// ✅ GOOD
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM cases WHERE id = ?"
);
stmt.setString(1, caseID);
```

#### 1.7 No Serialization of Untrusted Data
- [ ] Removed all `ObjectInputStream.readObject()` calls on untrusted input
- [ ] All API payloads deserialized via JSON (use `ObjectMapper`) instead of Java serialization
- [ ] No use of `enableDefaultTyping()` in Jackson ObjectMapper
- [ ] No use of `readValue()` with generic `Object.class`

**Code Search**:
```bash
grep -r "ObjectInputStream" src/main/java/
grep -r "enableDefaultTyping" src/main/java/
# Both should be empty or only in test code
```

#### 1.8 Dependency Security (SBOM + Scanning)
- [ ] Generated SBOM: `mvn cyclonedx:makeBom`
- [ ] SBOM scanned with Grype or OSV-Scanner
- [ ] No known CVEs in dependencies (threshold: CRITICAL only)
- [ ] Dependency versions pinned (no version ranges like `[1.0,)`)

```bash
# Generate SBOM
mvn cyclonedx:makeBom

# Scan for vulnerabilities
grype sbom target/bom.json --fail-on critical

# Or use OSV
osv-scanner --sbom=target/bom.json
```

#### 1.9 Secure Logging (No Sensitive Data)
- [ ] No passwords logged (even in DEBUG level)
- [ ] No API keys or tokens logged
- [ ] No personally identifiable information (PII) logged
- [ ] All logs marked with correlation IDs for auditing

**Code Review**:
```java
// ❌ BAD
logger.debug("User login: {} with password {}", username, password);

// ✅ GOOD
logger.debug("User login initiated for {}", username);
logger.info("Authentication attempt for user {} at {}", username, timestamp);
```

#### 1.10 Exception Handling (No Information Leakage)
- [ ] Stack traces not exposed to end users
- [ ] Generic error messages returned to API clients
- [ ] Full error details logged server-side with correlation ID
- [ ] No SQL error details in exception messages

**Code Review**:
```java
// ❌ BAD
try {
    database.query(sql);
} catch (SQLException e) {
    response.sendError(500, e.getMessage());  // Leaks DB structure
}

// ✅ GOOD
try {
    database.query(sql);
} catch (SQLException e) {
    logger.error("Database query failed [correlationId={}]: {}",
                 correlationId, e.getMessage(), e);
    response.sendError(500, "An error occurred. Contact support with ID: " + correlationId);
}
```

---

## Section 2: Code Quality & Standards Compliance

### 2.1 Static Analysis Requirements
```bash
# Before commit
mvn clean verify -P analysis
```

- [ ] SpotBugs: Zero HIGH/CRITICAL issues
- [ ] PMD: Zero rule violations in critical set
- [ ] SonarQube: Code coverage ≥ 75% (line), ≥ 80% (class)
- [ ] Error Prone: Zero warnings
- [ ] Checkstyle: Zero violations

### 2.2 No Anti-Patterns (Guards)
- [ ] No TODO, FIXME, HACK comments left in production code
- [ ] No mock/stub/fake methods (`mockFetch()`, `stubValidate()`)
- [ ] No empty method bodies (`{ }`)
- [ ] No conditional mocks (`if (isTestMode) return mockData()`)
- [ ] No silent fallbacks (`catch (Exception e) { return null; }`)
- [ ] All exceptions logged and rethrown, never silently caught

**See**: `.claude/HYPER_STANDARDS.md` for full list of 14 forbidden patterns.

### 2.3 Real Implementation or UnsupportedOperationException

Every public method must either:

✅ **Option 1**: Implement real functionality
```java
public WorkItem getWorkItem(String id) {
    return repository.findById(id)
        .orElseThrow(() -> new WorkItemNotFoundException(id));
}
```

✅ **Option 2**: Throw `UnsupportedOperationException` with clear message
```java
@Deprecated(since="5.2", forRemoval=true)
public void legacyMethod() {
    throw new UnsupportedOperationException(
        "Use newMethod() instead. Removal planned for YAWL v6.0"
    );
}
```

❌ **Never**: Mock/stub/placeholder implementations

---

## Section 3: Deployment Configuration

### 3.1 JVM Security Flags

**Add to production startup script**:

```bash
#!/bin/bash

JAVA_OPTS=""

# Java 25 core settings
JAVA_OPTS="${JAVA_OPTS} -Xms4g -Xmx8g"
JAVA_OPTS="${JAVA_OPTS} -XX:+AlwaysPreTouch"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseCompactObjectHeaders"

# GC selection (choose one)
# Option A: G1GC (default, balanced)
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
JAVA_OPTS="${JAVA_OPTS} -XX:MaxGCPauseMillis=200"

# Option B: ZGC (ultra-low latency, large heaps)
# JAVA_OPTS="${JAVA_OPTS} -XX:+UseZGC"

# Security: TLS configuration
JAVA_OPTS="${JAVA_OPTS} -Djdk.tls.disabledAlgorithms=\"TLSv1,TLSv1.1,TLSv1.2\""
JAVA_OPTS="${JAVA_OPTS} -Djdk.certpath.disabledAlgorithms=\"MD5,SHA1,RSA keySize < 3072\""
JAVA_OPTS="${JAVA_OPTS} -Djdk.jce.disabledAlgorithms=\"DES,3DES,RC4,Blowfish\""

# Security: No Security Manager (removed in Java 24+)
# DO NOT use: -Djava.security.manager

# Monitoring: Flight Recorder (production-safe)
JAVA_OPTS="${JAVA_OPTS} -XX:StartFlightRecording=filename=app.jfr,dumponexit=true,settings=profile"

# Module system (future)
# JAVA_OPTS="${JAVA_OPTS} -XX:+UnlockDiagnosticVMOptions"

export JAVA_OPTS

java ${JAVA_OPTS} -jar yawl-engine.jar
```

### 3.2 Keystore & Truststore Management

**Generate server keystore** (TLS server certificate):

```bash
# Generate 3072-bit RSA key (CNSA compliant)
keytool -genkeypair \
  -alias yawl-server \
  -keyalg RSA \
  -keysize 3072 \
  -validity 365 \
  -keystore keystore.jks \
  -storepass "$(openssl rand -base64 32)" \
  -keypass "$(openssl rand -base64 32)" \
  -dname "CN=yawl.example.com,O=Organization,L=City,ST=State,C=US"
```

**Generate truststore** (CA certificates for mutual TLS):

```bash
# Import trusted CA certificates
keytool -import \
  -alias myca \
  -file ca-certificate.pem \
  -keystore truststore.jks \
  -storepass "$(openssl rand -base64 32)" \
  -noprompt
```

**Use in application**:

```bash
-Djavax.net.ssl.keyStore=/path/to/keystore.jks
-Djavax.net.ssl.keyStorePassword="${KEYSTORE_PASSWORD}"
-Djavax.net.ssl.trustStore=/path/to/truststore.jks
-Djavax.net.ssl.trustStorePassword="${TRUSTSTORE_PASSWORD}"
```

### 3.3 Container Image Security

**Dockerfile best practices**:

```dockerfile
# Use base image with Java 25 + OpenJDK
FROM eclipse-temurin:25-jdk-alpine

# Non-root user
RUN addgroup -S yawl && adduser -S yawl -G yawl
USER yawl

# Copy JAR with SBOM
COPY target/yawl-engine.jar /app/yawl-engine.jar
COPY target/bom.json /app/bom.json

# Scan image at build time
# docker run -v /var/run/docker.sock:/var/run/docker.sock \
#   aquasec/trivy image yawl:latest

ENTRYPOINT ["java", \
  "-Xms4g", "-Xmx8g", \
  "-XX:+UseCompactObjectHeaders", \
  "-XX:+UseG1GC", \
  "-Djdk.tls.disabledAlgorithms=TLSv1,TLSv1.1,TLSv1.2", \
  "-jar", "/app/yawl-engine.jar"]
```

---

## Section 4: Monitoring & Incident Response

### 4.1 Security Event Logging

Enable Java Flight Recorder (JFR) for security monitoring:

```bash
# In production, collect continuous profile
java -XX:StartFlightRecording=filename=/var/log/yawl/app.jfr,\
dumponexit=true,\
settings=profile,\
duration=0 \
  -jar yawl-engine.jar
```

**Monitor these events**:
- TLS handshake failures
- Certificate validation errors
- Authentication failures
- Authorization denials
- SQL exceptions (potential injection attempts)
- Deserialization exceptions

### 4.2 Alerting Rules

Create alerts for:

```
IF jdk.tls.SSLHandshakeFailed > 10/min THEN alert("TLS failures", severity=HIGH)
IF java.security.AccessControlException > 5/min THEN alert("Authz failures", severity=MEDIUM)
IF java.io.StreamCorruptedException > 1/min THEN alert("Serialization attacks?", severity=HIGH)
```

---

## Section 5: Compliance Frameworks

### 5.1 OWASP Top 10 Alignment

| OWASP 2025 | YAWL Mitigation | Evidence |
|------------|-----------------|----------|
| A01: Broken Access Control | Spring Security + custom RBAC | `.claude/RBAC-IMPLEMENTATION.md` |
| A03: Software Supply Chain | SBOM + Grype scanning | `mvn cyclonedx:makeBom` |
| A05: Security Misconfiguration | Hardened JVM flags | Section 3.1 above |
| A07: Authentication Failures | No security manager; explicit auth | Code review in Section 2 |
| A09: Security Logging | Correlation IDs, no PII | `.claude/LOGGING-GUIDELINES.md` |
| A10: Exceptional Condition Handling | No exception leakage | Section 2.3 above |

### 5.2 CWE Coverage

- CWE-20 (Improper Input Validation): ✅ Parameterized queries, input validation
- CWE-89 (SQL Injection): ✅ PreparedStatement only
- CWE-502 (Java Deserialization): ✅ JSON only for untrusted input
- CWE-611 (XXE): ✅ JDOM XXE prevention
- CWE-276 (Incorrect Default Permissions): ✅ Explicit authorization checks

---

## Pre-Production Checklist

### Final Validation Before Go-Live

```bash
# 1. Dependency audit
mvn cyclonedx:makeBom
grype sbom target/bom.json --fail-on critical

# 2. Deprecated API scan
jdeprscan --for-removal build/libs/yawl.jar

# 3. Static analysis
mvn clean verify -P analysis

# 4. Build JAR for deployment
mvn clean package

# 5. Verify keystore
keytool -list -v -keystore keystore.jks

# 6. JVM flags validation
echo $JAVA_OPTS | grep -E "UseG1GC|UseCompactObjectHeaders|tls.disabledAlgorithms"

# 7. Configuration review
cat config/application.properties | grep -i "security\|ssl\|tls\|crypto"

# 8. Load test with security tooling enabled
java -XX:StartFlightRecording=... -jar yawl-engine.jar &
# Monitor: jdk.tls.SSLHandshakeFailed, java.security events
```

---

## Quarterly Security Updates

- [ ] **January**: Patch Tuesday (4th Tuesday)
- [ ] **April**: Patch Tuesday
- [ ] **July**: Patch Tuesday
- [ ] **October**: Patch Tuesday
- [ ] [ ] **After each patch**: Re-run SBOM scan, static analysis, retest

---

## References

- **Secure Coding Guidelines**: https://www.oracle.com/java/technologies/javase/seccodeguide.html
- **JVM Security Properties**: https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
- **OWASP Top 10:2025**: https://owasp.org/Top10/
- **NIST Cybersecurity Framework**: https://csrc.nist.gov/projects/cybersecurity-framework
- **CWE Top 25**: https://cwe.mitre.org/top25/
- **Java Cryptography Architecture**: https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html
