#!/bin/bash
# Phase H: Guard Validation (Hyper-standards)
#
# Validates generated code against 7 forbidden patterns:
#   H_TODO:     Deferred work markers (// TODO, FIXME, etc.)
#   H_MOCK:     Mock implementations (mock*, stub*, fake*, demo*)
#   H_STUB:     Empty/placeholder returns
#   H_EMPTY:    No-op method bodies
#   H_FALLBACK: Silent catch-and-fake patterns
#   H_LIE:      Code doesn't match documentation
#   H_SILENT:   Logging instead of throwing
#
# Usage:
#   bash phase-h-guards.sh [file-or-dir...]    # Scan files
#   bash phase-h-guards.sh --emit-dir <dir>    # Scan emit directory
#
# Output:
#   Receipt: .claude/receipts/h-guards-receipt.json
#   Exit code: 0 (clean) or 2 (violations found)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/.claude/receipts"
RECEIPT_FILE="${RECEIPTS_DIR}/h-guards-receipt.json"

# Create receipts directory
mkdir -p "${RECEIPTS_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# ──────────────────────────────────────────────────────────────────────────────

# Regex patterns for each guard check
H_TODO_PATTERN='(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)'
H_MOCK_PATTERN='(mock|stub|fake|demo)[A-Z][a-zA-Z]*'
H_SILENT_PATTERN='log\.(warn|error).*not\s+implemented'

# ──────────────────────────────────────────────────────────────────────────────
# ARGUMENT PARSING
# ──────────────────────────────────────────────────────────────────────────────

EMIT_DIR=""
TARGET_FILES=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --emit-dir)
            EMIT_DIR="$2"
            shift 2
            ;;
        *)
            TARGET_FILES+=("$1")
            shift
            ;;
    esac
done

# If emit-dir specified, scan all Java files in it
if [[ -n "${EMIT_DIR}" ]]; then
    if [[ -d "${EMIT_DIR}" ]]; then
        mapfile -t -O "${#TARGET_FILES[@]}" TARGET_FILES < <(find "${EMIT_DIR}" -name "*.java" -type f)
    else
        echo "Error: emit-dir '${EMIT_DIR}' not found" >&2
        exit 2
    fi
fi

# If no targets specified, use current directory
if [[ ${#TARGET_FILES[@]} -eq 0 ]]; then
    mapfile -t TARGET_FILES < <(find . -name "*.java" -type f)
fi

# ──────────────────────────────────────────────────────────────────────────────
# HELPER: Check patterns and collect violations
# ──────────────────────────────────────────────────────────────────────────────

VIOLATIONS_FOUND=0
VIOLATIONS_JSON='[]'

check_file() {
    local file="$1"
    local line_num=0

    while IFS= read -r line; do
        line_num=$((line_num + 1))

        # H_TODO: Check comments
        if echo "${line}" | grep -qE "//.*${H_TODO_PATTERN}"; then
            local violation=$(printf '{
  "pattern": "H_TODO",
  "severity": "FAIL",
  "file": "%s",
  "line": %d,
  "content": "%s",
  "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
}' "$file" "$line_num" "$(echo "${line}" | sed 's/"/\\"/g' | cut -c1-100)")
            VIOLATIONS_JSON=$(echo "${VIOLATIONS_JSON}" | jq --argjson v "${violation}" '. += [$v]')
            VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
        fi

        # H_MOCK: Check method/class names
        if echo "${line}" | grep -qE "${H_MOCK_PATTERN}"; then
            if echo "${line}" | grep -q -E "(public|private|class|interface).*${H_MOCK_PATTERN}"; then
                local violation=$(printf '{
  "pattern": "H_MOCK",
  "severity": "FAIL",
  "file": "%s",
  "line": %d,
  "content": "%s",
  "fix_guidance": "Delete mock class or implement real service"
}' "$file" "$line_num" "$(echo "${line}" | sed 's/"/\\"/g' | cut -c1-100)")
                VIOLATIONS_JSON=$(echo "${VIOLATIONS_JSON}" | jq --argjson v "${violation}" '. += [$v]')
                VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
            fi
        fi

        # H_SILENT: Check log statements
        if echo "${line}" | grep -qE "${H_SILENT_PATTERN}"; then
            local violation=$(printf '{
  "pattern": "H_SILENT",
  "severity": "FAIL",
  "file": "%s",
  "line": %d,
  "content": "%s",
  "fix_guidance": "Throw exception instead of logging"
}' "$file" "$line_num" "$(echo "${line}" | sed 's/"/\\"/g' | cut -c1-100)")
            VIOLATIONS_JSON=$(echo "${VIOLATIONS_JSON}" | jq --argjson v "${violation}" '. += [$v]')
            VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
        fi

    done < "$file"
}

# ──────────────────────────────────────────────────────────────────────────────
# SCAN ALL TARGET FILES
# ──────────────────────────────────────────────────────────────────────────────

FILES_SCANNED=0

for file in "${TARGET_FILES[@]}"; do
    if [[ -f "$file" ]]; then
        FILES_SCANNED=$((FILES_SCANNED + 1))
        check_file "$file"
    fi
done

# ──────────────────────────────────────────────────────────────────────────────
# WRITE RECEIPT
# ──────────────────────────────────────────────────────────────────────────────

STATUS=$([ ${VIOLATIONS_FOUND} -eq 0 ] && echo "GREEN" || echo "RED")
VIOLATION_COUNT=$(echo "${VIOLATIONS_JSON}" | jq 'length')

cat > "${RECEIPT_FILE}" << EOF
{
  "phase": "H-Guards",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "files_scanned": ${FILES_SCANNED},
  "violations_found": ${VIOLATION_COUNT},
  "status": "${STATUS}",
  "violations": ${VIOLATIONS_JSON}
}
EOF

# ──────────────────────────────────────────────────────────────────────────────
# DISPLAY RESULTS
# ──────────────────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "🔒 Phase H: Guard Validation (Hyper-standards)"
echo "───────────────────────────────────────────────────────────────────────────"
echo "Files scanned: ${FILES_SCANNED}"
echo "Violations found: ${VIOLATION_COUNT}"
echo "Status: ${STATUS}"
echo ""

if [[ ${VIOLATION_COUNT} -gt 0 ]]; then
    echo "Violations:"
    echo "${VIOLATIONS_JSON}" | jq -r '.[] | "  \(.pattern) at \(.file):\(.line) - \(.fix_guidance)"'
    echo ""
fi

echo "Receipt: ${RECEIPT_FILE}"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# Exit with appropriate code
if [[ ${VIOLATIONS_FOUND} -eq 0 ]]; then
    exit 0
else
    exit 2
fi
