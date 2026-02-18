# /yawl-security Skill

Security validation and Java 25 compliance checking for YAWL production deployments.

---

## Overview

Comprehensive security validation including OWASP compliance, cryptography standards, and Java 25 security requirements.

**Invoked by**: `yawl-production-validator`, `yawl-reviewer`
**Requires**: Java 25 environment, Maven, build artifacts
**Output**: Security reports, compliance checklists, remediation guidance

---

## Commands

### `/yawl-security --check`

Quick security validation (5-10 min).

```bash
/yawl-security --check
/yawl-security --check --level=basic
```

**Validates**:
- ✅ No Security Manager usage
- ✅ TLS 1.3 configuration
- ✅ No deprecated cryptographic algorithms
- ✅ Build success
- ✅ All tests passing

### `/yawl-security --sbom`

Generate Software Bill of Materials for supply chain security.

```bash
/yawl-security --sbom
/yawl-security --sbom --scan
```

**Actions**:
1. Generate SBOM: `mvn cyclonedx:makeBom`
2. Scan dependencies: `grype sbom.json --fail-on critical`
3. Generate report with CVE details

**Output**:
- `target/bom.json` - Complete SBOM
- `security/sbom-report.txt` - Scan results
- List of vulnerable dependencies (if any)

### `/yawl-security --compliance`

Full compliance validation against standards.

```bash
/yawl-security --compliance
/yawl-security --compliance --level=strict
/yawl-security --compliance --report
```

**Validates Against**:
- ✅ OWASP Top 10:2025
- ✅ CNSA cryptography standards
- ✅ NIST Cybersecurity Framework
- ✅ CWE Top 25
- ✅ YAWL HYPER_STANDARDS

**Output**:
- Compliance matrix
- Violations with remediation guidance
- Risk assessment

### `/yawl-security --tls-check`

Validate TLS/SSL configuration.

```bash
/yawl-security --tls-check
/yawl-security --tls-check --level=strict
```

**Checks**:
- ✅ TLS 1.3 enabled
- ✅ TLS 1.2 disabled in production
- ✅ Strong cipher suites only
- ✅ Certificate validity
- ✅ Keystore/truststore configuration
- ✅ mTLS setup for service-to-service

**Configuration Tested**:
```properties
jdk.tls.disabledAlgorithms=TLSv1,TLSv1.1,TLSv1.2
jdk.certpath.disabledAlgorithms=MD5,SHA1,RSA keySize < 3072
jdk.jce.disabledAlgorithms=DES,3DES,RC4,Blowfish
```

### `/yawl-security --deprecated-api`

Scan for deprecated Java APIs requiring removal.

```bash
/yawl-security --deprecated-api
/yawl-security --deprecated-api --for-removal
```

**Runs**:
- `jdeprscan --for-removal build/libs/yawl.jar`
- Generates removal timeline
- Recommends replacements

**Critical for Java 25**:
- ❌ Security Manager (removed in JDK 24+)
- ❌ javax.* namespaces (use jakarta.*)
- ❌ Weak cryptographic algorithms

### `/yawl-security --crypto-audit`

Detailed cryptography compliance audit.

```bash
/yawl-security --crypto-audit
/yawl-security --crypto-audit --report
```

**Validates**:
- ✅ RSA: Minimum 3072 bits (CNSA compliant)
- ✅ ECDSA: P-256, P-384, P-521 curves only
- ✅ Symmetric: AES-GCM only (no CBC, no DES)
- ✅ Hash: SHA-256+, no MD5/SHA-1
- ✅ Key derivation: HKDF, Argon2 (no PBKDF2 alone)
- ✅ Certificate: X.509 v3, minimum 3072-bit

---

## Security Levels

### Basic
```bash
/yawl-security --check --level=basic
```
- Build validation
- No Security Manager
- TLS 1.3 enabled

### Standard (Default)
```bash
/yawl-security --check --level=standard
/yawl-security --check  # defaults to standard
```
- All basic checks
- SBOM scan
- Deprecated API scan
- Certificate validation

### Strict
```bash
/yawl-security --check --level=strict
```
- All standard checks
- Full compliance matrix
- Cryptography audit
- mTLS enforcement
- Supply chain deep scan

---

## Pre-Deployment Workflow

### Week 7-8: Security Phase (Phase 4)

```bash
# 1. Check compliance (10 min)
/yawl-security --compliance

# 2. Generate SBOM and scan (15 min)
/yawl-security --sbom --scan

# 3. Validate TLS configuration (5 min)
/yawl-security --tls-check --level=strict

# 4. Audit cryptography (10 min)
/yawl-security --crypto-audit --report

# 5. Remove deprecated APIs (varies)
/yawl-security --deprecated-api --for-removal
# Fix any issues, rebuild

# 6. Final pre-deployment check (5 min)
/yawl-security --compliance --level=strict --report
```

### Production Deployment

```bash
# 1. Security checkpoint
/yawl-security --check --level=strict

# 2. Compliance verification
/yawl-security --compliance --report

# 3. Supply chain validation
/yawl-security --sbom --scan

# 4. Final sign-off
/yawl-security --compliance --level=strict
```

---

## Required JVM Flags

Enforce security at runtime:

```bash
# TLS Configuration
-Djdk.tls.disabledAlgorithms="TLSv1,TLSv1.1,TLSv1.2"

# Certificate Path Validation
-Djdk.certpath.disabledAlgorithms="MD5,SHA1,RSA keySize < 3072"

# Cryptographic Algorithms
-Djdk.jce.disabledAlgorithms="DES,3DES,RC4,Blowfish"

# Keystore/Truststore (if using certificates)
-Djavax.net.ssl.keyStore=/path/to/keystore.jks
-Djavax.net.ssl.keyStorePassword="${KEYSTORE_PASSWORD}"
-Djavax.net.ssl.trustStore=/path/to/truststore.jks
-Djavax.net.ssl.trustStorePassword="${TRUSTSTORE_PASSWORD}"
```

---

## Integration with Agents

**yawl-production-validator**:
- Run `--check` before deployment
- Run `--compliance` for sign-off
- Monitor `--sbom` for supply chain threats

**yawl-reviewer**:
- Run `--deprecated-api` in code reviews
- Check cryptography with `--crypto-audit`
- Validate TLS with `--tls-check`

**yawl-engineer**:
- Fix deprecated APIs found by `--deprecated-api`
- Ensure compliance during development

---

## Related Documentation

- **[SECURITY-CHECKLIST-JAVA25.md](../../SECURITY-CHECKLIST-JAVA25.md)** - Detailed compliance checklist
- **[BUILD-PERFORMANCE.md](../../BUILD-PERFORMANCE.md)** - Build security automation
- **[JAVA-25-FEATURES.md](../../JAVA-25-FEATURES.md)** - Security APIs section
- **[HYPER_STANDARDS.md](../../HYPER_STANDARDS.md)** - Code quality enforcement

---

## Output Examples

### Compliance Report
```
✅ COMPLIANCE REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

OWASP Top 10:2025 Status
├─ A01: Broken Access Control: PASS (Spring Security configured)
├─ A03: Supply Chain: PASS (SBOM generated, no CVEs)
├─ A05: Security Misconfiguration: PASS (hardened JVM flags)
├─ A07: Authentication: PASS (TLS 1.3 enforced)
└─ A09: Logging: PASS (security events logged)

CNSA Cryptography Status
├─ RSA Keys: 3072-bit minimum: PASS
├─ ECDSA: P-256, P-384, P-521 only: PASS
├─ Symmetric: AES-GCM only: PASS
└─ Hash: SHA-256+: PASS

Overall: ✅ COMPLIANT
```

### SBOM Scan Results
```
📦 SUPPLY CHAIN SECURITY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Total Dependencies: 47
Vulnerabilities: 0 CRITICAL, 0 HIGH, 2 MEDIUM, 5 LOW

Medium Severity (2):
├─ log4j 2.14.1 (patch available: 2.17.0)
└─ commons-io 2.6 (patch available: 2.11.0)

Recommendation: Update to patched versions
```

---

**Last Updated**: 2026-02-17
**Java Version**: 25+
**Standards**: OWASP 2025, CNSA, NIST, CWE, HYPER_STANDARDS
