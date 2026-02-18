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
