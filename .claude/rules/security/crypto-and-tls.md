---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/authentication/**"
  - "*/src/main/java/org/yawlfoundation/yawl/security/**"
  - "**/application.yml"
  - "**/application.yaml"
  - "**/application.properties"
  - "**/Dockerfile*"
---

# Security Rules

## Cryptography
- Required: AES-GCM, RSA-3072+, ECDSA
- Forbidden: MD5, SHA-1, DES, 3DES, RC4, Blowfish
- Password hashing: Argon2 (argon2-jvm dependency available)
- Key derivation: HKDF or PBKDF2 with SHA-256+

## TLS
- TLS 1.3 only in production
- Disable TLS 1.2 via `jdk.tls.disabledAlgorithms=SSLv3,TLSv1,TLSv1.1`
- CNSA-compliant cipher suites only

## Authentication
- No Security Manager (removed JDK 24+)
- Use Spring Security or custom RBAC
- JWT tokens for A2A handoff (60-second TTL)
- API keys for MCP/A2A service authentication

## Secrets
- Never hardcode credentials in source
- Use `_FILE` suffix env vars for Docker secrets (`YAWL_PASSWORD_FILE`)
- SBOM generation: `mvn cyclonedx:makeBom`

## Container Security
- Run as non-root user (UID 1000, dev:yawl)
- Set memory/CPU limits in orchestration
- Network policies restrict inter-service communication
