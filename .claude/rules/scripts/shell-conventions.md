---
paths:
  - "scripts/**"
  - ".claude/hooks/**"
  - "**/*.sh"
---

# Shell Script Rules

## Standards
- Shebang: `#!/usr/bin/env bash`
- `set -euo pipefail` at top of every script
- All scripts must be executable (`chmod +x`)
- Use `"$variable"` (quoted) — never bare `$variable`
- Use `[[ ]]` for conditionals, not `[ ]`

## Hook Scripts (.claude/hooks/)
- Exit codes: `0` = pass, `1` = warning, `2` = BLOCK (stops tool execution)
- Must complete within timeout (hyper-validate: 30s, session-start: 120s)
- Read file path from `$TOOL_INPUT` JSON (jq for parsing)
- Scope checks: only validate relevant file types (skip non-Java for hyper-validate)

## Build Scripts (scripts/)
- `dx.sh` is the primary agent build loop — prefer over raw Maven commands
- All scripts should support `--help` flag
- Use `CLAUDE_PROJECT_DIR` env var for project root (set by session-start hook)
- Print timing information on completion
- Exit non-zero on any failure (fail-fast)

## Observatory Scripts (scripts/observatory/)
- Emit functions: `emit_X()` pattern in `lib/emit-facts.sh`
- Output to `docs/v6/latest/facts/` as JSON
- Generate receipts with SHA256 hashes
- Must be idempotent (safe to re-run)
