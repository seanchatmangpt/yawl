# YAWL Docker Validation Setup

Quick reference for running YAWL validations in Docker containers.

## Quick Start

```bash
# Run all validations (compile, test, analysis, observatory)
./scripts/docker-validate.sh

# Fast validation (compile + test only, ~2-3 minutes)
./scripts/docker-validate.sh fast

# Interactive shell for debugging
./scripts/docker-validate.sh shell
```

## Commands

| Command | Description | Time |
|---------|-------------|------|
| `(default)` | All validations | ~5-10 min |
| `fast` | Compile + test only | ~2-3 min |
| `compile` | Compile only | ~1 min |
| `test` | Test only | ~1-2 min |
| `analysis` | SpotBugs, PMD, Checkstyle | ~2-3 min |
| `observatory` | Generate facts/diagrams | ~30 sec |
| `ci` | Full CI pipeline | ~10 min |
| `shell` | Interactive container | - |
| `build` | Build image only | ~2 min |
| `clean` | Remove containers | - |

## Generated Outputs

All outputs are mounted to your host filesystem:

| Directory | Contents |
|-----------|----------|
| `target/` | Build artifacts (JARs, classes) |
| `docs/v6/latest/` | Observatory outputs |
| `docs/v6/latest/facts/` | JSON fact files (modules, tests, etc.) |
| `docs/v6/latest/diagrams/` | Mermaid diagram files |
| `docs/v6/latest/receipts/` | SHA256 receipts for verification |
| `reports/` | Static analysis reports |

## Docker Compose Profiles

For more control, use docker-compose directly:

```bash
# Build image
docker compose -f docker-compose.validation.yml build

# Run specific services
docker compose -f docker-compose.validation.yml run --rm validation ./scripts/dx.sh all
docker compose -f docker-compose.validation.yml --profile observatory run observatory
docker compose -f docker-compose.validation.yml --profile analysis run analysis
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YAWL_IMAGE_NAME` | `yawl-validation` | Docker image name |
| `YAWL_IMAGE_TAG` | `latest` | Docker image tag |
| `BUILD_DATE` | current date | Build date label |
| `VCS_REF` | git commit | Git reference label |
| `DX_VERBOSE` | `0` | Show Maven output |
| `MAVEN_OPTS` | `-Xmx2g` | Maven JVM options |

## Troubleshooting

### Permission denied
```bash
# Add your user to docker group
sudo usermod -aG docker $USER
# Log out and back in
```

### Clean slate
```bash
# Remove all containers, volumes, and images
./scripts/docker-validate.sh clean --all
./scripts/docker-validate.sh build
```

### Memory issues
```bash
# Increase Docker memory limit in Docker Desktop
# Or set MAVEN_OPTS:
MAVEN_OPTS="-Xmx4g" ./scripts/docker-validate.sh
```

### View container logs
```bash
docker compose -f docker-compose.validation.yml logs
```

## Files

| File | Purpose |
|------|---------|
| `Dockerfile.validation` | Multi-tool validation image |
| `docker-compose.validation.yml` | Service definitions |
| `scripts/docker-validate.sh` | Main entry point |
| `scripts/validate-all.sh` | Internal validation script |

## Integration with CI/CD

```yaml
# GitHub Actions example
- name: Build Validation Image
  run: ./scripts/docker-validate.sh build

- name: Run Tests
  run: ./scripts/docker-validate.sh test

- name: Generate Observatory
  run: ./scripts/docker-validate.sh observatory

- name: Upload Artifacts
  uses: actions/upload-artifact@v4
  with:
    name: observatory-outputs
    path: docs/v6/latest/
```
