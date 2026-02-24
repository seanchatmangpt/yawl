# YAWL Security Quick Start Guide

**Production-grade security for YAWL v6.0.0**

## Dependency Health Checker

### Quick Commands

```bash
# Full scan with all reports
./.claude/check-dependencies.sh --format=all

# Quick check (no CVE scan)
./.claude/check-dependencies.sh --skip-cve

# Critical only
./.claude/check-dependencies.sh --critical-only

# Specific module
./.claude/check-dependencies.sh --module=yawl-engine
```

### Exit Codes

| Code | Meaning | CI/CD Action |
|------|---------|--------------|
| 0 | No critical issues | Continue |
| 1 | High severity | Warn |
| 2 | Critical vulnerabilities | Block |

### Severity Levels

- **CRITICAL** (9.0-10.0): Fix within 24 hours
- **HIGH** (7.0-8.9): Fix within 1 week
- **MEDIUM** (4.0-6.9): Fix within 1 month
- **LOW** (0.1-3.9): Fix when convenient

## Certificate Pinning for Z.AI

### Implementation

Certificate pinning prevents MITM attacks on Z.AI API connections.

#### How It Works
1. Server sends certificate with public key
2. Extract public key, compute SHA-256 hash, Base64 encode
3. Compare to hardcoded list in ZaiHttpClient
4. ✓ Match = Allow connection | ✗ No match = Throw SSLPeerUnverifiedException

#### Configuration
```java
// In ZaiHttpClient.java
private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
    "sha256/L9CowLk96O4M3HMZX/dxC1m/zJJYdQG9xUakwRV8yb4=",  // Primary
    "sha256/mK87OJ3fZtIf7ZS0Eq6/5qG3H9nM2cL8wX5dP1nO9q0="   // Backup
);
```

#### Certificate Rotation
When Z.AI rotates their certificate:
1. Get new pin: `openssl s_client -connect api.z.ai:443 | ...`
2. Add to backup pins list
3. Deploy code with both pins
4. After rotation, remove old pin, promote backup pin

#### Security Monitoring
Watch for these log messages:
```
[ZAI-PIN] Certificate pin validated successfully        ← Good
[ZAI-PIN] Certificate pin NOT in accepted list          ← Alert! Possible MITM
[ZAI-PIN] Attempting fallback validation...            ← Certificate rotation?
```

### Testing Certificate Pinning

```bash
# Run all pinning tests
mvn test -pl yawl-integration \
  -Dtest=PinnedTrustManagerTest,ZaiHttpClientCertificatePinningTest

# Run specific test class
mvn test -pl yawl-integration \
  -Dtest=PinnedTrustManagerTest#InitializationTests
```

## HYPER_STANDARDS Validation

### Pre-Commit Hook (5-Point Checklist)

The hook blocks 5 anti-patterns:

#### 1. NO DEFERRED WORK
```java
// ❌ FORBIDDEN
// TODO: implement this method
public void doWork() { }

// ✅ REQUIRED
public void doWork() {
    throw new UnsupportedOperationException(
        "doWork requires DatabaseConnection to be configured"
    );
}
```

#### 2. NO MOCKS
```java
// ❌ FORBIDDEN
public String getMockData() { return "fake data"; }

// ✅ REQUIRED
public String getData() {
    return repository.fetch(); // Real implementation
    // OR throw with implementation guide
}
```

#### 3. NO STUBS
```java
// ❌ FORBIDDEN
public List<Item> getItems() { return new ArrayList<>(); }

// ✅ REQUIRED
public List<Item> getItems() {
    return repository.findAll(); // May legitimately be empty
    // OR throw if truly unimplemented
}
```

#### 4. NO SILENT FALLBACKS
```java
// ❌ FORBIDDEN
public Data fetchFromApi() {
    try { return api.call(); }
    catch (Exception e) { return DEFAULT_DATA; }
}

// ✅ REQUIRED
public Data fetchFromApi() {
    try { return api.call(); }
    catch (ApiException e) {
        throw new RuntimeException("API failed", e);
    }
}
```

#### 5. NO LIES (Code = Documentation)
```java
// ❌ FORBIDDEN
/** Validates input */ public boolean validate(String data) { return true; }

// ✅ REQUIRED
/** Validates input */ public boolean validate(String data) {
    return validator.validate(data); // Real validation
}
```

### Running Q Phase Invariants

```bash
# Run Q phase verification
yawl godspeed verify --verbose

# Run specific invariant check
yawl godspeed verify --invariant Q_REAL_IMPL_OR_THROW

# Save detailed report
yawl godspeed verify --report json > invariant-report.json
```

## Quality Gates

### Active Gates
| Gate | Command | Threshold |
|------|---------|-----------|
| Enforcer | `mvn validate` | Java 25+ |
| Compile | `mvn compile` | Must succeed |
| Unit Tests | `mvn test` | 100% pass |
| JaCoCo Coverage | `mvn verify` | 50% line / 40% branch |
| SpotBugs | `mvn verify -P ci` | 0 high-priority bugs |
| PMD | `mvn verify -P analysis` | 0 violations |
| OWASP Dep-Check | `mvn verify -P security-audit` | CVSS < 7 |

### Risk Levels

- **RED**: Block commit entirely
- **YELLOW**: Allow commit but require fix before next release
- **GREEN**: No action required

## Security Properties Reference

| Component | Status | Details |
|-----------|--------|---------|
| Certificate Pinning | ✓ | Z.AI API protected from MITM |
| Dependency Scanning | ✓ | OWASP integration, CVE monitoring |
| Code Validation | ✓ | HYPER_STANDARDS enforced |
| Exception Handling | ✓ | No silent fallbacks |

## Production Checklist

Before deployment:

### Dependencies
- [ ] Run `./.claude/check-dependencies.sh --format=all`
- [ ] No critical vulnerabilities
- [ ] Dependencies updated quarterly
- [ ] OWASP Dependency Check completed

### Certificate Pinning
- [ ] Z.AI pins verified
- [ ] Primary and backup pins in place
- [ ] Rotation procedure documented
- [ ] Security logs monitored for pin failures

### Code Quality
- [ ] Pre-commit hook passes
- [ ] Q phase invariants GREEN
- [ ] No mock/stub/fake implementations
- [ ] All exceptions properly propagated

## Quick Links

- **Full Security Guide**: `.claude/HYPER_STANDARDS.md`
- **Dependency Checker**: `.claude/check-dependencies.sh`
- **Certificate Pinning**: `CERTIFICATE_PINNING_QUICK_REF.md`
- **Q Phase Invariants**: `Q-PHASE-QUICK-REFERENCE.md`

---

**Last Updated**: February 22, 2026  
**Status**: Production Ready
