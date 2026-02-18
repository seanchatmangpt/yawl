# YAWL DX Cheatsheet

Quick reference for Developer Experience commands.

---

## Fastest Feedback Loop

```bash
bash scripts/dx.sh compile   # ~15s - compile changed modules
bash scripts/dx.sh test      # ~30s - test changed modules
bash scripts/dx.sh all       # ~90s - full build
```

---

## Environment Variables

| Variable | Values | Default | Description |
|----------|--------|---------|-------------|
| `DX_VERBOSE` | 0, 1 | 0 | Show Maven output |
| `DX_CLEAN` | 0, 1 | 0 | Force clean build |
| `DX_OFFLINE` | 0, 1, auto | auto | Offline mode |
| `DX_FAIL_AT` | fast, end | fast | Fail strategy |

---

## Build Scripts

| Script | Command | Time | Purpose |
|--------|---------|------|---------|
| `dx.sh` | `compile` | ~15s | Compile changed |
| `dx.sh` | `test` | ~30s | Test changed |
| `dx.sh` | `all` | ~90s | Full build |
| `dx.sh` | `-pl mod` | varies | Specific module |
| `dx-status.sh` | - | instant | Health check |
| `dx-lint.sh` | - | ~20s | SpotBugs check |
| `dx-cache.sh` | `save` | ~5s | Cache artifacts |
| `dx-cache.sh` | `restore` | ~5s | Restore artifacts |
| `dx-cache.sh` | `status` | instant | Cache info |
| `dx-benchmark.sh` | `all` | ~90s | Track performance |
| `dx-benchmark.sh` | `trend` | instant | View history |
| `dx-security-scan.sh` | - | ~60s | Full scan |
| `dx-security-scan.sh` | `--fast` | ~30s | Cached scan |

---

## Test Scripts

| Script | Command | Purpose |
|--------|---------|---------|
| `test-watch.sh` | - | Watch mode |
| `test-watch.sh` | `-m module` | Watch module |
| `test-analyze-flaky.sh` | `N` | Detect flaky (N runs) |
| `dx-test-single.sh` | `TestClass` | Run single class |
| `dx-test-single.sh` | `Class#method` | Run single method |

---

## Docker Scripts

| Script | Command | Purpose |
|--------|---------|---------|
| `dev-up.sh` | - | Start dev environment |
| `dev-up.sh` | `--build` | Rebuild and start |
| `dev-down.sh` | - | Stop containers |
| `dev-down.sh` | `--clean` | Stop + remove volumes |
| `dev-logs.sh` | - | Tail logs |
| `dx-docker-test.sh` | - | Test in container |
| `dx-docker-test.sh` | `--shell` | Interactive shell |

---

## Maven Commands

| Command | Time | Purpose |
|---------|------|---------|
| `mvn -T 1.5C compile` | ~45s | Parallel compile |
| `mvn -T 1.5C test` | ~90s | Parallel tests |
| `mvn -T 1.5C package` | ~90s | Full build |
| `mvn -P agent-dx test` | ~60s | Fast profile |
| `mvn -P analysis verify` | ~180s | Static analysis |
| `mvn clean` | ~5s | Clean artifacts |

---

## Quick Diagnostics

```bash
# Check build health
bash scripts/dx-status.sh

# Check cache
bash scripts/dx-cache.sh status

# View benchmark trend
bash scripts/dx-benchmark.sh trend

# Quick lint
bash scripts/dx-lint.sh
```

---

## Module List

```
yawl-utilities      # Core utilities
yawl-elements       # Domain model
yawl-authentication # Auth providers
yawl-engine         # Workflow engine
yawl-stateless      # Stateless engine
yawl-resourcing     # Resource service
yawl-scheduling     # Scheduling service
yawl-security       # Security layer
yawl-integration    # MCP/A2A integration
yawl-monitoring     # Observability
yawl-webapps        # Web applications
yawl-control-panel  # Admin console
```

---

## Common Workflows

### Before Commit
```bash
bash scripts/dx.sh all
```

### Fast Iteration
```bash
bash scripts/dx.sh compile && bash scripts/dx.sh test
```

### End of Session
```bash
bash scripts/dx-cache.sh save
```

### Start of Session
```bash
bash scripts/dx-cache.sh restore
bash scripts/dx-status.sh
```

### Debug Test Failure
```bash
bash scripts/dx-test-single.sh FailingTest
```
