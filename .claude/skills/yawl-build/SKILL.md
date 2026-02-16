---
name: yawl-build
description: Build YAWL project using Ant
disable-model-invocation: true
user-invocable: true
allowed-tools: Bash(ant *)
---

# YAWL Build Skill

Build the YAWL project using Apache Ant.

## Usage

```
/yawl-build [target]
```

## Targets

- `compile` - Compile source code (default, ~18 seconds)
- `buildAll` - Full build: compile + web + libraries (~2 minutes)
- `clean` - Remove build artifacts
- `buildWebApps` - Build web applications only

## Current Build Status

Branch: !`git branch --show-current`
Last commit: !`git log -1 --oneline`
Uncommitted changes: !`git status --short | wc -l` files

## Execution

```bash
cd "$CLAUDE_PROJECT_DIR"
ant -f build/build.xml ${ARGUMENTS:-compile}
```

## Success Criteria

- Exit code 0
- No compilation errors
- JAR files created in output/
