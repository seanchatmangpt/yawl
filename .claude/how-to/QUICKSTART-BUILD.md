# YAWL Build Quick Start Guide

**2-minute setup to 80% benefit with 20% effort**

## Essential Commands

### Agent DX Fast Loop (Preferred for Development)

```bash
# Compile changed modules only (~3-5s)
bash scripts/dx.sh compile

# Auto-detect changed modules, compile + test (~5-15s)
bash scripts/dx.sh

# All modules, compile + test (~30-60s)
bash scripts/dx.sh all

# Target specific module
bash scripts/dx.sh -pl yawl-engine
```

### Standard Maven Commands

```bash
# Fast parallel compile (~45s)
mvn -T 1.5C clean compile

# Full build with tests (~90s)
mvn -T 1.5C clean package

# With code coverage (~120s)
mvn -T 1.5C clean package jacoco:check

# With static analysis (~180s)
mvn -T 1.5C clean verify spotbugs:check pmd:check

# Skip tests (fast) (~60s)
mvn -T 1.5C clean package -DskipTests
```

### ggen Build Orchestration

```bash
# Full pipeline: generate → compile → test → validate
bash scripts/ggen-build.sh --phase lambda

# Individual phases
bash scripts/ggen-build.sh --phase compile   # Fast feedback
bash scripts/ggen-build.sh --phase test      # Assumes compiled
bash scripts/ggen-build.sh --phase validate   # Static analysis
```

## Module Reference

| Module | Purpose |
|--------|---------|
| `yawl-utilities` | Common utilities (auth, logging, schema, util, exceptions) |
| `yawl-elements` | Workflow element definitions, engine core interfaces |
| `yawl-engine` | Core stateful engine, swingWorklist, YEventLogger |
| `yawl-stateless` | Stateless engine variant |
| `yawl-resourcing` | Resource/user management |
| `yawl-worklet` | Worklet services |
| `yawl-scheduling` | Scheduling services |
| `yawl-security` | Security layer |
| `yawl-integration` | MCP/A2A integrations |
| `yawl-monitoring` | Observability services |
| `yawl-webapps` | Web applications |
| `yawl-control-panel` | Admin control panel |

## Build Configuration

### Parallel Builds (Add to .mvn/maven.config)
```
-T 1.5C
-B
--no-transfer-progress
```

### Test Parallelization (Add to surefire plugin config)
```xml
<parallel>methods</parallel>
<threadCount>1.5C</threadCount>
```

### Code Coverage (jacoco plugin)
```xml
<execution>
    <id>report</id>
    <phase>test</phase>
    <goals>
        <goal>report</goal>
    </goals>
</execution>
```

## Common Workflows

### Before Commit
```bash
bash scripts/dx.sh all           # Full validation
git add <specific-files>        # Never: git add .
git commit -m "feat: description"
```

### Fast Iteration
```bash
bash scripts/dx.sh compile && bash scripts/dx.sh test
```

## Performance Expectations

### Before Optimization
- Clean build: ~180s
- Incremental: ~90s
- Tests: ~60s (sequential)

### After Optimization
- Clean build: ~90s (parallel)
- Incremental: ~30s (parallel)
- Tests: ~30-45s (parallel with 1.5C threads)

## Version Matrix (Feb 2026)

| Tool | Version |
|------|---------|
| Maven | 4.0.0+ |
| Java | 25+ |
| JUnit 5 | 5.14.0 |
| JaCoCo | 0.8.15 |
| SpotBugs | 4.8.2 |
| PMD | 6.52.0 |
| Maven Compiler | 3.15.0 |
| Surefire | 3.5.4 |

## Troubleshooting

### "Tests fail when running parallel"
Add `@Execution(ExecutionMode.SAME_THREAD)` to non-thread-safe tests:
```java
@Execution(ExecutionMode.SAME_THREAD)
class NonThreadSafeTest { }
```

### "Out of memory with parallel tests"
Reduce thread count or increase heap:
```bash
mvn -T 1C test  # Use 1 thread per core
# OR
MAVEN_OPTS="-Xmx2g" mvn -T 1.5C test
```

## Quick Links

- **Full Maven Guide**: `Maven_QUICK_START.md`
- **ggen Build**: `GGEN-BUILD-QUICK-REFERENCE.md`
- **DX Scripts**: `DX-CHEATSHEET.md`

---

**Last Updated**: February 22, 2026  
**Status**: Production Ready
