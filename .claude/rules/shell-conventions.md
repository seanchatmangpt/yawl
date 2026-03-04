# Shell Scripting Conventions — YAWL Fortune 5 Standards

**Auto-activates for**: `scripts/**`, `.claude/hooks/**`, `**/*.sh`

---

## Required Patterns

| Pattern | Requirement | Reason |
|---------|-------------|--------|
| Shebang | `#!/usr/bin/env bash` | Portability across systems |
| Safety | `set -euo pipefail` | Fail fast on errors, undefined vars, pipe failures |
| Conditionals | `[[ ]]` not `[ ]` | Bash built-in, safer, more features |
| Quoting | `"$variable"` always | Prevent word splitting and glob expansion |
| Functions | `local` for variables | Prevent scope pollution |

---

## Exit Codes

| Code | Meaning | Action |
|------|---------|--------|
| 0 | GREEN | Success, proceed |
| 1 | Transient | Retry may help (IO, network) |
| 2 | RED | Fatal error, block and fix |

---

## Cross-Platform Compatibility

### Platform Detection

```bash
case "$(uname -s)" in
    Linux)  # Linux-specific paths ;;
    Darwin) # macOS-specific paths ;;
    *)      echo "Unsupported platform"; exit 1 ;;
esac
```

### Common Cross-Platform Issues

| Issue | Linux | macOS | Solution |
|-------|-------|-------|----------|
| `sha256sum` | ✓ | ✗ (use `shasum -a 256`) | Function wrapper |
| `sed -i` | `sed -i ''` | `sed -i ''` | Use temp file |
| `date +%s%N` | ✓ | ✗ (no nanoseconds) | `perl -MTime::HiRes` |
| `readlink -f` | ✓ | ✗ | `realpath` or `greadlink` |

### sha256sum Fallback (macOS)

```bash
if ! command -v sha256sum &>/dev/null; then
    sha256sum() { shasum -a 256 "$@"; }
    export -f sha256sum
fi
```

---

## Hook Integration

Shell scripts in `.claude/hooks/` must:

1. **Complete within timeout** (default: 30s)
2. **Read input from stdin** (JSON from Claude Code)
3. **Emit errors to stderr** (shown to user)
4. **Exit 0 (pass) or 2 (block)**

### Hook Template

```bash
#!/usr/bin/env bash
set -euo pipefail

# Parse JSON input from Claude Code
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Validate file exists
[[ -f "$FILE" ]] || exit 0

# Run checks
VIOLATIONS=()

if ! grep -q "required_pattern" "$FILE"; then
    VIOLATIONS+=("Missing required pattern")
fi

# Report violations
if [[ ${#VIOLATIONS[@]} -gt 0 ]]; then
    echo "❌ Violations found:" >&2
    for v in "${VIOLATIONS[@]}"; do
        echo "  - $v" >&2
    done
    exit 2
fi

exit 0
```

---

## Temp Files

Always use PID-namespaced paths to avoid collisions:

```bash
# Create temp directory
TMP_DIR="${TMPDIR:-/tmp}/script-${$}"
mkdir -p "${TMP_DIR}"

# Cleanup on exit
cleanup() {
    rm -rf "${TMP_DIR}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Use temp files
temp_file="${TMP_DIR}/output.txt"
```

---

## Logging

```bash
LOG_LEVEL="${LOG_LEVEL:-INFO}"

log_info() {
    echo "[INFO] $*" >&2
}

log_debug() {
    [[ "$LOG_LEVEL" == "DEBUG" ]] && echo "[DEBUG] $*" >&2
}

log_error() {
    echo "[ERROR] $*" >&2
}
```

---

## Bash Version Compatibility

Associative arrays require Bash 4+. Check and degrade gracefully:

```bash
BASH_MAJOR="${BASH_VERSINFO[0]:-0}"
if [[ "$BASH_MAJOR" -lt 4 ]]; then
    echo "[WARN] Bash $BASH_MAJOR detected. Bash 4+ required for full features." >&2
    # Disable features requiring associative arrays
    ADVANCED_FEATURES=0
fi
```

---

## Forbidden Patterns (H-Guards)

| Pattern | Code | Fix |
|---------|------|-----|
| `# TODO:` | H_TODO | Implement or remove |
| `# FIXME:` | H_TODO | Fix the issue |
| `[ ... -a/-o ... ]` | H_SHELL_DEPRECATED | Use `[[ && ]]` or `[[ \|\| ]]` |
| Wrong/missing shebang | H_SHELL_SHEBANG | Use `#!/usr/bin/env bash` |
| Missing `set -euo pipefail` | H_SHELL_SAFETY | Add `set -euo pipefail` |
| Unquoted `$var` | H_SHELL_UNSAFE | Quote: `"$var"` |

**Note**: Simple `[ -f "$file" ]` tests are allowed. Only `[ ]` with `-a`/`-o` operators is deprecated.

---

## Best Practices

### 1. Use Arrays for Lists

```bash
# Good
MODULES=("yawl-engine" "yawl-elements" "yawl-utilities")
for mod in "${MODULES[@]}"; do
    echo "Building $mod"
done

# Bad (word splitting)
MODULES="yawl-engine yawl-elements yawl-utilities"
for mod in $MODULES; do  # Unquoted!
    echo "Building $mod"
done
```

### 2. Prefer `[[ ]]` for Tests

```bash
# Good
if [[ -f "$file" && -r "$file" ]]; then
    cat "$file"
fi

# Bad
if [ -f "$file" -a -r "$file" ]; then
    cat "$file"
fi
```

### 3. Use `printf` for Format Strings

```bash
# Good
printf "Processing %s (%d files)\n" "$module" "$count"

# Bad
echo "Processing $module ($count files)"
```

### 4. Check Command Availability

```bash
if command -v mvnd &>/dev/null; then
    MVN_CMD="mvnd"
else
    MVN_CMD="mvn"
fi
```

---

## Validation

Run the shell-validate.sh hook manually:

```bash
bash .claude/hooks/shell-validate.sh < <(echo '{"tool_input":{"file_path":"scripts/dx.sh"}}')
```

---

## Reference

- **HYPER_STANDARDS.md**: Overall YAWL quality standards
- **observatory.sh**: Example of production shell script
- **dx.sh**: Example of complex shell script with phases
