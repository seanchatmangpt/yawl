# dx.sh CLI Reference

**Quadrant**: Reference | **Source of truth**: `scripts/dx.sh`

Fast, incremental build-test loop for code agents. Detects which modules have uncommitted changes and builds only those (plus their transitive dependencies). Skips JaCoCo, Javadoc, static analysis, and integration tests. Fails fast on first module failure.

---

## Synopsis

```
bash scripts/dx.sh [PHASE] [SCOPE] [-pl MODULE_LIST]
```

---

## Positional Arguments

| Argument | Description |
|----------|-------------|
| *(none)* | Compile + test changed modules |
| `compile` | Compile only, changed modules |
| `test` | Test only, changed modules (assumes already compiled) |
| `all` | Compile + test ALL 19 modules |
| `compile all` | Compile ALL modules |
| `test all` | Test ALL modules |
| `-pl mod1,mod2` | Compile + test explicit module list |

### Combining arguments

```bash
bash scripts/dx.sh compile         # compile changed
bash scripts/dx.sh compile all     # compile everything
bash scripts/dx.sh test            # test changed (assumes compiled)
bash scripts/dx.sh test all        # test everything
bash scripts/dx.sh -pl yawl-engine # compile + test one module
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DX_OFFLINE` | `auto` | `1` = force offline, `0` = force online, `auto` = detect from local repo |
| `DX_FAIL_AT` | *(fast)* | `end` = accumulate all failures before reporting; default stops on first failure |
| `DX_VERBOSE` | `0` | `1` = show full Maven output; default is quiet |
| `DX_CLEAN` | `0` | `1` = run `clean` phase before compile; default is incremental |
| `CLAUDE_CODE_REMOTE` | `false` | `true` = exclude `yawl-pi` and `yawl-mcp-a2a-app` (heavy ML deps) |

### Examples

```bash
# Show all Maven output:
DX_VERBOSE=1 bash scripts/dx.sh

# Force clean rebuild of everything:
DX_CLEAN=1 bash scripts/dx.sh compile all

# Collect all failures (don't stop at first):
DX_FAIL_AT=end bash scripts/dx.sh all

# Force offline (no network):
DX_OFFLINE=1 bash scripts/dx.sh
```

---

## Changed-Module Detection

When called without `all` or `-pl`, `dx.sh` runs `git diff` to find modified files and maps them to modules:

```
git diff HEAD          (unstaged changes)
git diff --cached      (staged changes)
git ls-files --others  (untracked files)
```

Each changed file is matched against the module prefix. A change to `pom.xml` or `.mvn/` triggers a full `all` build.

**Example**: Editing `yawl-engine/src/main/java/…/YEngine.java` triggers compilation of `yawl-engine` only (not `yawl-stateless` or downstream modules, unless they are also changed).

---

## Module Ordering

`dx.sh` maintains the canonical topological order internally:

```bash
ALL_MODULES=(
    # Layer 0 — Foundation (parallel)
    yawl-utilities yawl-security yawl-graalpy yawl-graaljs
    # Layer 1 — First consumers (parallel)
    yawl-elements yawl-ggen yawl-graalwasm
    # Layer 2 — Core engine
    yawl-engine
    # Layer 3 — Engine extension
    yawl-stateless
    # Layer 4 — Services (parallel)
    yawl-authentication yawl-scheduling yawl-monitoring
    yawl-worklet yawl-control-panel yawl-integration yawl-webapps
    # Layer 5 — Advanced services (parallel)
    yawl-pi yawl-resourcing
    # Layer 6 — Top-level application
    yawl-mcp-a2a-app
)
```

This is identical to the canonical order in `docs/v6/diagrams/facts/reactor.json`. The order is authoritative — it was fixed in the 2026-02-27 commit that resolved FM7 (yawl-authentication was incorrectly listed before yawl-engine).

---

## Maven Profile Used

`dx.sh` activates the `agent-dx` Maven profile, which:
- Skips JaCoCo instrumentation
- Skips Javadoc generation
- Skips integration tests (`failsafe`)
- Skips static analysis (SpotBugs, PMD)
- Uses Surefire with forked JVM for fast unit tests

For full analysis, use `mvn clean verify -P analysis` directly.

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | All modules compiled and all tests passed |
| `1` | Maven build failure (compile error or test failure) |
| `2` | H-guard violation in a written file (from hook, not dx.sh itself) |

---

## Java Version Enforcement

`dx.sh` enforces Java 25 regardless of the shell's current `JAVA_HOME`. If Temurin 25 is installed at `/usr/lib/jvm/temurin-25-jdk-amd64`, the script overrides `JAVA_HOME` automatically before invoking Maven.

---

## Remote Environment Behavior

When `CLAUDE_CODE_REMOTE=true` (set by the session-start hook in ephemeral environments):

- `yawl-pi` is excluded (contains 89 MB `onnxruntime` artifact)
- `yawl-mcp-a2a-app` is excluded (transitively depends on `yawl-pi`)
- All other 17 modules build normally

The local Maven proxy (`127.0.0.1:3128`) is auto-configured in remote environments via `scripts/session-start.sh`.

---

## mvnd Support

If `mvnd` (Maven Daemon) is installed, `dx.sh` uses it automatically for faster incremental builds. `mvnd` maintains a warm JVM between invocations, reducing startup overhead from ~3s to ~0.5s.

---

## Targeted Builds with `-pl`

Use `-pl` to build a specific module and exactly the transitive dependencies you choose:

```bash
# Build yawl-engine and only its deps:
bash scripts/dx.sh -pl yawl-utilities,yawl-elements,yawl-engine

# Alternatively, let Maven resolve upstream (-am = also-make):
mvn -pl yawl-engine -am compile
```

Minimal `-pl` lists for each module: see [Build Sequences Reference](build-sequences.md).

---

## Pre-Commit Gate

`dx.sh all` is the mandatory pre-commit gate. A commit must not be pushed until this exits 0:

```bash
bash scripts/dx.sh all
# If green → git add <specific-files> → git commit
```

Running only `dx.sh` (changed modules) is sufficient for iterating during development, but `all` is required before committing.

---

## See Also

- [Build Sequences Reference](build-sequences.md) — module ordering and `-pl` lists
- [Quality Gates Reference](quality-gates.md) — what dx.sh verifies
- [How-To: Target a Module Build](../how-to/build/target-module-build.md)
- [How-To: Add a Maven Module](../how-to/build/add-maven-module.md)
