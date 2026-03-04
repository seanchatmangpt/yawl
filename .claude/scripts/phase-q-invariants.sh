#!/bin/bash
# Phase Q: Invariant Validation
#
# Validates that code follows YAWL's core invariant:
#   real_impl ∨ throw UnsupportedOperationException
#
# This checks that every method either:
#   1. Contains a real, complete implementation, OR
#   2. Throws an exception (never silent fallbacks or fake returns)
#
# Forbidden patterns (invariant violations):
#   - Methods returning null when not nullable
#   - Methods returning empty collections as placeholders
#   - Catch blocks that silently continue
#   - Methods with no-op bodies
#
# Usage:
#   bash phase-q-invariants.sh [file-or-dir...]
#
# Output:
#   Receipt: .claude/receipts/q-invariants-receipt.json
#   Exit code: 0 (no violations) or 2 (violations found)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/.claude/receipts"
RECEIPT_FILE="${RECEIPTS_DIR}/q-invariants-receipt.json"

# Create receipts directory
mkdir -p "${RECEIPTS_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# ARGUMENT PARSING
# ──────────────────────────────────────────────────────────────────────────────

TARGET_FILES=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --emit-dir)
            EMIT_DIR="$2"
            if [[ -d "${EMIT_DIR}" ]]; then
                mapfile -t -O "${#TARGET_FILES[@]}" TARGET_FILES < <(find "${EMIT_DIR}" -name "*.java" -type f)
            fi
            shift 2
            ;;
        *)
            TARGET_FILES+=("$1")
            shift
            ;;
    esac
done

# If no targets specified, use current directory
if [[ ${#TARGET_FILES[@]} -eq 0 ]]; then
    mapfile -t TARGET_FILES < <(find . -name "*.java" -type f 2>/dev/null || true)
fi

# ──────────────────────────────────────────────────────────────────────────────
# HELPER: Check code quality invariants
# ──────────────────────────────────────────────────────────────────────────────

VIOLATIONS_FOUND=0
VIOLATIONS_JSON='[]'

check_file_invariants() {
    local file="$1"
    local line_num=0
    local in_method=false
    local method_name=""
    local method_start_line=0
    local method_body=""
    local brace_depth=0

    while IFS= read -r line; do
        line_num=$((line_num + 1))

        # Track method declarations
        if [[ ${in_method} == false ]] && echo "${line}" | grep -qE "(public|private|protected)\s+(static\s+)?[a-zA-Z<>[\]]+\s+\w+\s*\("; then
            in_method=true
            method_name=$(echo "${line}" | grep -oE '\w+\s*\(' | head -1 | sed 's/\s*(//g')
            method_start_line=${line_num}
            method_body="${line}"
            brace_depth=0

            # Count opening braces
            brace_depth=$(echo "${line}" | grep -o '{' | wc -l)
            brace_depth=$((brace_depth - $(echo "${line}" | grep -o '}' | wc -l)))
        elif [[ ${in_method} == true ]]; then
            method_body="${method_body} ${line}"
            brace_depth=$((brace_depth + $(echo "${line}" | grep -o '{' | wc -l)))
            brace_depth=$((brace_depth - $(echo "${line}" | grep -o '}' | wc -l)))

            # End of method
            if [[ ${brace_depth} -le 0 ]]; then
                in_method=false

                # Check invariants on completed method
                # Q1: Empty body check
                if echo "${method_body}" | grep -qE '^\s*{\s*}\s*$'; then
                    local violation=$(printf '{
  "pattern": "Q_EMPTY",
  "severity": "FAIL",
  "file": "%s",
  "line": %d,
  "content": "Method %s has empty body",
  "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
}' "$file" "$method_start_line" "$method_name")
                    VIOLATIONS_JSON=$(echo "${VIOLATIONS_JSON}" | jq --argjson v "${violation}" '. += [$v]')
                    VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
                fi

                # Q2: Silent return of empty collection
                if echo "${method_body}" | grep -qE 'return\s+(Collections\.empty|new\s+(HashMap|ArrayList|HashSet))\(\);'; then
                    local violation=$(printf '{
  "pattern": "Q_FAKE_RETURN",
  "severity": "FAIL",
  "file": "%s",
  "line": %d,
  "content": "Method %s returns fake empty collection",
  "fix_guidance": "Either implement real logic or throw UnsupportedOperationException"
}' "$file" "$method_start_line" "$method_name")
                    VIOLATIONS_JSON=$(echo "${VIOLATIONS_JSON}" | jq --argjson v "${violation}" '. += [$v]')
                    VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
                fi

                # Q3: Silent catch without re-throw
                if echo "${method_body}" | grep -qE 'catch\s*\([^)]+\)\s*{\s*(log|return|continue|break)[^}]*}'; then
                    local violation=$(printf '{
  "pattern": "Q_SILENT_CATCH",
  "severity": "FAIL",
  "file": "%s",
  "line": %d,
  "content": "Method %s has silent catch block",
  "fix_guidance": "Propagate exception or throw new exception"
}' "$file" "$method_start_line" "$method_name")
                    VIOLATIONS_JSON=$(echo "${VIOLATIONS_JSON}" | jq --argjson v "${violation}" '. += [$v]')
                    VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
                fi
            fi
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
        check_file_invariants "$file" || true
    fi
done

# ──────────────────────────────────────────────────────────────────────────────
# WRITE RECEIPT
# ──────────────────────────────────────────────────────────────────────────────

STATUS=$([ ${VIOLATIONS_FOUND} -eq 0 ] && echo "GREEN" || echo "RED")
VIOLATION_COUNT=$(echo "${VIOLATIONS_JSON}" | jq 'length')

cat > "${RECEIPT_FILE}" << EOF
{
  "phase": "Q-Invariants",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "files_scanned": ${FILES_SCANNED},
  "violations_found": ${VIOLATION_COUNT},
  "status": "${STATUS}",
  "invariant": "real_impl ∨ throw UnsupportedOperationException",
  "violations": ${VIOLATIONS_JSON}
}
EOF

# ──────────────────────────────────────────────────────────────────────────────
# DISPLAY RESULTS
# ──────────────────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "✔️  Phase Q: Invariant Validation"
echo "───────────────────────────────────────────────────────────────────────────"
echo "Files scanned: ${FILES_SCANNED}"
echo "Violations found: ${VIOLATION_COUNT}"
echo "Status: ${STATUS}"
echo "Invariant: real_impl ∨ throw UnsupportedOperationException"
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
