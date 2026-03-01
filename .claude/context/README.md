# Claude Code Context Isolation

Prevents conflicts between local and web Claude Code sessions.

## Problem

When using both Claude Code locally and Claude Code Web, both sessions may try to modify the same files:
- `pom.xml` - Adding/removing modules
- `.mvn/jvm.config` - JVM configuration
- `.mvn/maven-build-cache-remote-config.xml` - Cache settings

This causes merge conflicts and broken builds.

## Solution

Context isolation via environment variables and Maven profiles.

## Usage

### For Claude Code Web Sessions

Set environment variables before starting:

```bash
export CLAUDE_CODE_CONTEXT=web
export CLAUDE_SKIP_MAVEN_CONFIG=true
```

Or add to your Claude Code Web configuration.

### For Local Sessions

Default behavior (no configuration needed):
- Full access to all files
- Can modify Maven configuration

### For CI/CD

```bash
export CLAUDE_CODE_CONTEXT=ci
```

CI has full access for releases.

## Maven Profiles

Instead of modifying `pom.xml`, use profiles:

```bash
# Build with DSPy module
mvn compile -P dspy

# Build all experimental modules
mvn compile -P experimental

# Build everything
mvn compile -P all-modules
```

## Files

| File | Purpose |
|------|---------|
| `.claude/context/environment.toml` | Configuration for context isolation |
| `.claude/hooks/context-protection.sh` | Hook to block protected file modifications |
| `.gitattributes` | Merge strategies for protected files |
| `pom.xml` (profiles section) | Optional module profiles |

## Protected Files (Web Context)

When `CLAUDE_CODE_CONTEXT=web`, these files are protected:
- `pom.xml`
- `.mvn/jvm.config`
- `.mvn/maven-build-cache-remote-config.xml`
- `yawl-dspy/pom.xml`
- `yawl-engine/pom.xml`
- `yawl-graalpy/pom.xml`

## Environment Variables

| Variable | Values | Default | Purpose |
|----------|--------|---------|---------|
| `CLAUDE_CODE_CONTEXT` | `local`, `web`, `ci` | `local` | Session context |
| `CLAUDE_SKIP_MAVEN_CONFIG` | `true`, `false` | `false` | Skip Maven config changes |
| `CLAUDE_SKIP_PROTECTION` | `true`, `false` | `false` | Bypass all protection |

## Bypass Protection

If you need to modify a protected file from web context:

```bash
export CLAUDE_SKIP_PROTECTION=true
```

Or for a single command:

```bash
CLAUDE_SKIP_PROTECTION=true mvn compile
```
