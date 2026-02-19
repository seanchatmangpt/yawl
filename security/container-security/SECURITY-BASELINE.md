# Docker Security Baseline - YAWL v6.0.0

This document defines the mandatory security baseline for all YAWL Docker images.

## Overview

| Dockerfile | Base Image | User | TLS 1.3 | Health Check | Status |
|------------|------------|------|---------|--------------|--------|
| docker/base/Dockerfile.jdk25 | eclipse-temurin:25-jdk-alpine | dev (1000) | Yes | Yes | Production |
| docker/base/Dockerfile.jre25 | eclipse-temurin:25-jre-alpine | dev (1000) | Yes | Yes | Production |
| docker/production/Dockerfile.engine | eclipse-temurin:25-jre-alpine | dev (1000) | Yes | Yes | Production |
| docker/development/Dockerfile.dev | eclipse-temurin:25-jdk-alpine | yawl (1000) | Yes | Yes | Development |
| scripts/pm4py/Dockerfile.a2a | python:3.13-slim-bookworm | pm4py (1000) | N/A | Yes | Production |
| scripts/pm4py/Dockerfile.mcp | python:3.13-slim-bookworm | pm4py (1000) | N/A | Yes | Production |

## Security Requirements

### 1. Non-Root User Execution (MANDATORY)

All containers MUST run as non-root user.

**Implementation:**
```dockerfile
# Create non-root user with specific UID/GID
RUN addgroup -S yawl --gid 1000 && \
    adduser -S dev --uid 1000 --ingroup yawl --home /home/dev --shell /bin/bash

# Ensure directories are writable
RUN mkdir -p /app/logs /app/data && chown -R dev:yawl /app

# Switch to non-root user (MUST be last USER directive)
USER dev
```

**Validation:**
```bash
# Verify user in running container
docker exec <container> whoami  # Should NOT return 'root'
docker exec <container> id      # Should show uid=1000
```

### 2. TLS 1.3 Enforcement (Java Containers - MANDATORY)

All Java containers MUST disable TLS 1.0, 1.1, and legacy cryptographic algorithms.

**Implementation (JAVA_OPTS):**
```dockerfile
ENV JAVA_OPTS="\
    -Djdk.tls.disabledAlgorithms=SSLv3,TLSv1,TLSv1.1,RC4,MD5,SHA-1,DES,3DES \
    -Djdk.certpath.disabledAlgorithms=MD2,MD5,SHA1,RSA\ keySize\ <3072 \
    -Djdk.jce.disabledAlgorithms=DES,3DES,RC4,Blowfish"
```

**CNSA 2.0 Compliance (Future-Proofing):**
- RSA key sizes: 3072+ bits
- ECDSA curves: P-384, P-521
- AES: 256-bit keys preferred
- Hash: SHA-384+ for signatures

**Validation:**
```bash
# Test TLS configuration
docker exec <container> java -XshowSettings:properties 2>&1 | grep disabledAlgorithms
```

### 3. Health Checks (MANDATORY)

All production containers MUST define HEALTHCHECK.

**Implementation:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD ["/app/healthcheck.sh"]
```

**Three-Tier Health Check Strategy:**
1. **Tier 1**: Spring Boot Actuator liveness endpoint (preferred)
2. **Tier 2**: TCP port connectivity
3. **Tier 3**: JVM process existence

### 4. Minimal Base Images (MANDATORY)

Use Alpine or slim variants to minimize attack surface.

**Approved Base Images:**
- `eclipse-temurin:25-jdk-alpine` (Java 25 JDK)
- `eclipse-temurin:25-jre-alpine` (Java 25 JRE)
- `python:3.13-slim-bookworm` (Python 3.13)
- `eclipse-temurin:25-jdk` (Full JDK - development only)

**Forbidden Base Images:**
- `latest` tag (unpinned versions)
- `ubuntu`, `debian` (unless slim)
- `centos`, `rhel` (use UBI minimal)

### 5. No Hardcoded Secrets (MANDATORY)

Secrets MUST NOT be embedded in Dockerfiles or image layers.

**Detection Patterns:**
- Password assignments: `password = "value"`
- API keys: `api_key = "value"`
- AWS keys: `AKIA...`
- Private keys: `-----BEGIN PRIVATE KEY-----`

**Secrets Management:**
- Environment variables (Kubernetes secrets)
- HashiCorp Vault
- Docker secrets (Swarm)
- Mounted secret files

### 6. OCI Image Labels (REQUIRED)

All images MUST include OCI standard labels.

**Required Labels:**
```dockerfile
LABEL org.opencontainers.image.title="YAWL Engine"
LABEL org.opencontainers.image.description="Production-ready YAWL workflow engine"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.vendor="YAWL Foundation"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.revision="${VCS_REF}"
LABEL org.opencontainers.image.source="https://github.com/yawlfoundation/yawl"
LABEL org.opencontainers.image.licenses="LGPL-3.0"
```

### 7. Multi-Stage Builds (Production - REQUIRED)

Production images MUST use multi-stage builds to minimize final image size.

**Pattern:**
```dockerfile
# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build
# ... build steps ...

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
COPY --from=builder --chown=dev:yawl /build/target/*.jar /app/
USER dev
```

### 8. File Ownership (REQUIRED)

All files copied into the image MUST have proper ownership.

**Implementation:**
```dockerfile
# Option 1: COPY with --chown
COPY --chown=dev:yawl target/*.jar /app/

# Option 2: Explicit chown
COPY target/*.jar /app/
RUN chown -R dev:yawl /app
```

### 9. No Sudo (REQUIRED)

Containers running as non-root MUST NOT use sudo.

**Correct Pattern:**
```dockerfile
# Install packages as root BEFORE switching user
RUN apk add --no-cache curl bash

# Create user and switch
USER dev
# No sudo usage after this point
```

### 10. Security Context (Kubernetes - REQUIRED)

When deploying to Kubernetes, apply security contexts.

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
```

## Security Scanning

### Tools

| Tool | Purpose | Configuration |
|------|---------|---------------|
| Trivy | Container vulnerability scanning | `security/container-security/trivy-scan.yaml` |
| Hadolint | Dockerfile linting | `security/container-security/hadolint.yaml` |
| Grype | Alternative vulnerability scanner | `security/container-security/grype-scan.yaml` |

### Running Scans

```bash
# Full Docker security validation
bash scripts/docker-security-validate.sh --scan --report

# Quick validation (no external scanners)
bash scripts/docker-security-validate.sh --quick

# Hadolint only
hadolint --config security/container-security/hadolint.yaml docker/production/Dockerfile.engine

# Trivy filesystem scan
trivy fs --config security/container-security/trivy-scan.yaml .

# Trivy image scan (after build)
trivy image --config security/container-security/trivy-scan.yaml yawl-engine:6.0.0
```

## Validation Checklist

Before deploying any container image, verify:

- [ ] Non-root user configured (USER directive present, not root)
- [ ] TLS 1.3 enforced (Java containers: jdk.tls.disabledAlgorithms set)
- [ ] Health check defined (HEALTHCHECK directive)
- [ ] No hardcoded secrets (scan with detect-secrets, gitleaks)
- [ ] OCI labels present (org.opencontainers.image.*)
- [ ] Minimal base image (Alpine or slim)
- [ ] Multi-stage build (production images)
- [ ] File ownership set (COPY --chown or RUN chown)
- [ ] No sudo usage
- [ ] Security scan passed (Trivy: 0 CRITICAL/HIGH)

## Remediation Priority

| Severity | Timeline | Examples |
|----------|----------|----------|
| CRITICAL | Immediate | Running as root, hardcoded secrets, TLS 1.0 |
| HIGH | Within 24 hours | Missing health check, known CVEs |
| MEDIUM | Within 1 week | Missing OCI labels, non-minimal base |
| LOW | Next release | Documentation updates |

## References

- [CIS Docker Benchmark v1.5.0](https://www.cisecurity.org/benchmark/docker)
- [OWASP Docker Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html)
- [NIST SP 800-190](https://csrc.nist.gov/publications/detail/sp/800-190/final)
- [CNSA 2.0 Requirements](https://www.nsa.gov/ia/programs/suiteb_cryptography/)
- [OCI Image Spec](https://github.com/opencontainers/image-spec)

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-02-18 | Initial baseline for YAWL v6.0.0 |

---

*Last updated: 2026-02-18*
*Document owner: YAWL Security Team*
