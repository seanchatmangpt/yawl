# YAWL v6.0.0 – SBOM Generation Guide
## Software Bill of Materials (CycloneDX + SPDX)

### Overview

YAWL generates SBOMs at two levels:
1. **Source-level SBOM** – from `pom.xml` via CycloneDX Maven plugin (Java + transitive deps)
2. **Container-level SBOM** – from built Docker image via Trivy (OS packages + JARs)

Both formats are produced: **CycloneDX 1.4 JSON** and **SPDX 2.3 JSON**.

---

### 1. Source-Level SBOM (Maven)

```bash
# Generate CycloneDX SBOM from Maven (all modules)
mvn org.cyclonedx:cyclonedx-maven-plugin:2.8.0:makeAggregateBom \
    -DschemaVersion=1.4 \
    -DincludeTestScope=false \
    -DincludeBomSerialNumber=true \
    -DoutputFormat=json \
    -DoutputName=yawl-engine-6.0.0.cdx

# Output: target/yawl-engine-6.0.0.cdx.json

# Generate SPDX SBOM
mvn org.spdx:spdx-maven-plugin:0.7.4:createSPDX \
    -DspdxFile=target/yawl-engine-6.0.0.spdx.json

# Output: target/yawl-engine-6.0.0.spdx.json
```

### 2. Container-Level SBOM (Trivy)

```bash
# Build the image first
docker build -f Dockerfile.v6 \
    --build-arg VERSION=6.0.0 \
    --build-arg BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ) \
    --build-arg VCS_REF=$(git rev-parse --short HEAD) \
    -t yawl/engine:6.0.0 .

# Generate CycloneDX SBOM from image
trivy image \
    --format cyclonedx \
    --output sbom/yawl-engine-6.0.0-container.cdx.json \
    yawl/engine:6.0.0

# Generate SPDX SBOM from image
trivy image \
    --format spdx-json \
    --output sbom/yawl-engine-6.0.0-container.spdx.json \
    yawl/engine:6.0.0

# Generate SBOM from local filesystem (source scan)
trivy fs \
    --format cyclonedx \
    --output sbom/yawl-source-6.0.0.cdx.json \
    --scanners vuln,license \
    .
```

### 3. Image Signing (cosign)

```bash
# Prerequisites: cosign v2.x, COSIGN_KEY or keyless (Sigstore)

# Sign with long-lived key (offline environments)
cosign sign --key cosign.key \
    --annotations "org.opencontainers.image.version=6.0.0" \
    --annotations "org.opencontainers.image.source=https://github.com/yawlfoundation/yawl" \
    yawl/engine:6.0.0

# Sign keyless (GitHub Actions OIDC - preferred for CI)
COSIGN_EXPERIMENTAL=1 cosign sign \
    --identity-token=$(cat $ACTIONS_ID_TOKEN_REQUEST_TOKEN) \
    yawl/engine:6.0.0

# Attach SBOM to signed image
cosign attach sbom --sbom sbom/yawl-engine-6.0.0-container.cdx.json \
    yawl/engine:6.0.0

# Verify signature
cosign verify \
    --key cosign.pub \
    yawl/engine:6.0.0

# Verify with policy (subject must match)
cosign verify \
    --certificate-identity=https://github.com/yawlfoundation/yawl/.github/workflows/release.yml@refs/tags/v6.0.0 \
    --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
    yawl/engine:6.0.0
```

### 4. Admission Controller Verification (Kyverno)

The Kyverno policy `yawl-require-signed-images` (in
`security/kubernetes-security/kyverno-policies/image-signing.yaml`) verifies
cosign signatures before pods are admitted to the `yawl` namespace.

```yaml
# Inline Kyverno policy excerpt
verifyImages:
  - imageReferences: ["yawl/*:*"]
    attestors:
      - count: 1
        entries:
          - keys:
              publicKeys: |-
                -----BEGIN PUBLIC KEY-----
                <cosign.pub contents>
                -----END PUBLIC KEY-----
```

### 5. SBOM Validation

```bash
# Validate CycloneDX SBOM schema
docker run --rm -v $(pwd)/sbom:/sbom \
    cyclonedx/cyclonedx-cli validate \
    --input-file /sbom/yawl-engine-6.0.0-container.cdx.json \
    --input-format json \
    --input-version v1_4

# Check for known vulnerabilities in SBOM
grype sbom:sbom/yawl-engine-6.0.0-container.cdx.json \
    --fail-on high
```

### 6. SBOM Storage

SBOMs are stored alongside container images in the OCI registry:

```
registry.example.com/yawl/engine:6.0.0
registry.example.com/yawl/engine:6.0.0.sbom    ← CycloneDX SBOM
registry.example.com/yawl/engine:6.0.0.att     ← cosign attestation
```

### Verification Checklist

- [ ] CycloneDX SBOM generated for all modules
- [ ] SPDX SBOM generated for container image
- [ ] No GPL/AGPL license conflicts detected
- [ ] All HIGH/CRITICAL CVEs triaged or mitigated
- [ ] Image signed with cosign (keyless preferred)
- [ ] SBOM attached to OCI registry
- [ ] Kyverno admission policy enforcing signature verification
