# YAWL Integration Test Quick Reference

## One-Liner Commands

```bash
# Default: dev profile, H2 database
bash scripts/test-integration.sh

# Production: PostgreSQL
bash scripts/test-integration.sh --profile prod

# Debug: keep services, show logs
bash scripts/test-integration.sh --no-cleanup --verbose

# Specific module
bash scripts/test-integration.sh --modules yawl-integration

# Help
bash scripts/test-integration.sh --help
```

## Typical Workflow

```bash
# 1. Quick local check (dev, fast)
bash scripts/test-integration.sh

# 2. Full production validation (slow, realistic)
bash scripts/test-integration.sh --profile prod

# 3. If tests fail, debug with services running
bash scripts/test-integration.sh --no-cleanup --verbose
docker-compose -f docker-compose.yml logs -f yawl-engine

# 4. Manual cleanup when done
docker-compose -f docker-compose.yml down --volumes
```

## Exit Codes

| 0 | ✓ Success — safe to push |
| 1 | ✗ Test failed — check output |
| 2 | ✗ Missing dependency — install tools |
| 3 | ✗ Docker error — restart daemon |
| 4 | ✗ Service timeout — increase --timeout |
| 5 | ✗ Build failed — fix compilation |

## Requirements

- Docker 20.10+: `docker --version`
- docker-compose 2.0+: `docker-compose --version`
- Maven 3.9+: `mvn --version`
- Java 25+: `java -version`
- 4GB disk space

## Troubleshooting

| Problem | Command |
|---------|---------|
| Docker daemon not running | `sudo systemctl start docker` |
| Permission denied | `sudo usermod -aG docker $USER` (then relogin) |
| Port 8080 in use | `lsof -i :8080` to find process |
| Services stuck | `docker-compose -f docker-compose.yml down --volumes` |
| Out of disk | `docker volume prune -f` |
| Build fails | `bash scripts/test-integration.sh --verbose` |

## Full Script Location

`/home/user/yawl/scripts/test-integration.sh` (488 lines, v1.0.0)

## Documentation

Full guide: `/home/user/yawl/docs/TEST_INTEGRATION_GUIDE.md`

## Profiles

| Profile | Database | Speed | Use Case |
|---------|----------|-------|----------|
| dev | H2 | Fast (~185s) | Local, quick validation |
| prod | PostgreSQL | Slower (~275s) | Realistic testing |
