# yawl-security

**Artifact:** `org.yawlfoundation:yawl-security:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Public Key Infrastructure (PKI) and digital signature module:

- X.509 certificate creation, parsing, and management
- Digital signing and verification of YAWL workflow specifications
- Certificate Revocation List (CRL) and OCSP support via Bouncycastle PKIX
- S/MIME and CMS operations via Bouncycastle Mail
- Cryptographically enforced specification integrity

**This module has no YAWL module dependencies** — it is a fully self-contained
cryptographic library that other modules may optionally integrate.

## Internal Dependencies

None.

## Key Third-Party Dependencies

| Artifact | Version | Purpose |
|----------|---------|---------|
| `bcprov-jdk18on` | `1.77` | Bouncycastle core cryptographic provider |
| `bcmail-jdk18on` | `1.77` | Bouncycastle S/MIME and CMS operations |
| `bcpkix-jdk18on` | `1.77` | Bouncycastle PKIX (X.509, CRL, OCSP) |
| `commons-lang3` | — | Utilities |
| `commons-io` | — | File I/O for certificate files |
| `log4j-api` + `log4j-core` | — | Logging |

> **Note:** Bouncycastle versions are pinned directly in this POM at `1.77` and are **not**
> managed by the parent BOM. No other YAWL module uses Bouncycastle.

Test dependencies: JUnit 4, JUnit 5 Jupiter.

## Build Configuration Notes

- **Source directory:** `../src`; compiler filter scoped to `security/**` only
- **Test directory:** `../test`

## Quick Build

```bash
mvn -pl yawl-security clean package
```

## Test Coverage

**No tests exist** at module scope. The test directory `../test/org/yawlfoundation/yawl/security/` does not exist.

JUnit 4 and JUnit 5 are declared as test dependencies but no test classes have been written yet.

Coverage gaps (entire module):
- X.509 certificate generation and parsing — no unit tests
- Digital signature creation and verification — no unit tests
- CRL / OCSP revocation checks — no unit tests
- Key store loading and PKCS#12 round-trip — no unit tests

## Roadmap

- **Certificate generation tests** — add `TestX509CertificateGenerator` covering RSA-2048, RSA-3072, and ECDSA-P256 key pairs; verify subject DN and validity period
- **Signature round-trip tests** — add `TestDigitalSignature` covering sign → serialise → verify for YAWL specification XML payloads
- **CRL validation tests** — add `TestCRLValidator` with a self-signed test CA and a synthetic revocation list
- **Bouncycastle 2.x migration** — evaluate upgrade from `1.77` to Bouncycastle 2.x (`bcprov-jdk18on` → `bcprov-lts8on`) once JDK 18+ baseline is confirmed
- **PKCS#11 / HSM support** — add a `PKCS11KeyStoreProvider` that delegates key operations to a hardware security module via the JDK PKCS#11 provider
- **ECDSA default keys** — move away from RSA as the default key type; use ECDSA P-256 for new certificate generation in line with NIST SP 800-186
- **Add to parent BOM** — pin Bouncycastle version in the parent BOM's `<dependencyManagement>` so other modules can optionally use it without version drift
