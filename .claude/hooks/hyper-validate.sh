#!/bin/bash
# Hyper-Advanced Validation Hook for YAWL Fortune 5 Standards
# Runs after Write/Edit tool use to catch ALL forbidden patterns
# Exit 2 = Block and show violations to Claude
#
# Modes:
#   Hook mode (no args):   reads JSON from stdin (Claude Code hooks framework)
#   Batch mode (dirs...):  scans provided directories; PY-6 completeness check
#   Emit mode (--emit-dir): validates compiled output from generator
#
# Parameters:
#   --emit-dir <dir>       Validate compiled output directory
#   --receipt-file <path>  Custom receipt output path (default: .claude/receipts/guard-receipt.json)
#   --json-only            Output only JSON (no colors, for CI)
#   --verbose              Show detailed violation information

set -euo pipefail

# ─── HELPER: Generate fix guidance ──────────────────────────────────────
get_fix_guidance() {
    local pattern="$1"
    case "$pattern" in
        H_TODO)
            echo "Either implement real logic or throw UnsupportedOperationException with a clear message"
            ;;
        H_MOCK)
            echo "Delete mock/stub/fake class or implement real service with actual dependencies"
            ;;
        H_MOCK_CLASS)
            echo "Rename class to real name or delete from production code"
            ;;
        H_STUB)
            echo "Replace empty/placeholder return with real implementation or throw UnsupportedOperationException"
            ;;
        H_EMPTY)
            echo "Implement method body with real logic or throw UnsupportedOperationException"
            ;;
        H_FALLBACK)
            echo "Propagate exception instead of catching and returning fake data"
            ;;
        H_LIE)
            echo "Update code to match documentation or update documentation to match code behavior"
            ;;
        H_SILENT)
            echo "Throw exception instead of logging, or propagate the error to caller"
            ;;
        *)
            echo "Fix guard violation to match FORTUNE 5 standards"
            ;;
    esac
}

# ─── HELPER: Escape JSON strings ────────────────────────────────────────
escape_json_string() {
    local s="$1"
    # Escape backslashes first, then quotes, then control characters
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    s="${s//$'\n'/\\n}"
    s="${s//$'\r'/\\r}"
    s="${s//$'\t'/\\t}"
    echo "$s"
}

# ─── HELPER: Generate summary counts ────────────────────────────────────
generate_summary() {
    local violations_json="$1"
    local h_todo_count=0
    local h_mock_count=0
    local h_mock_class_count=0
    local h_stub_count=0
    local h_empty_count=0
    local h_fallback_count=0
    local h_lie_count=0
    local h_silent_count=0

    h_todo_count=$(echo "$violations_json" | grep -o '"pattern":"H_TODO"' | wc -l)
    h_mock_count=$(echo "$violations_json" | grep -o '"pattern":"H_MOCK"' | wc -l)
    h_mock_class_count=$(echo "$violations_json" | grep -o '"pattern":"H_MOCK_CLASS"' | wc -l)
    h_stub_count=$(echo "$violations_json" | grep -o '"pattern":"H_STUB"' | wc -l)
    h_empty_count=$(echo "$violations_json" | grep -o '"pattern":"H_EMPTY"' | wc -l)
    h_fallback_count=$(echo "$violations_json" | grep -o '"pattern":"H_FALLBACK"' | wc -l)
    h_lie_count=$(echo "$violations_json" | grep -o '"pattern":"H_LIE"' | wc -l)
    h_silent_count=$(echo "$violations_json" | grep -o '"pattern":"H_SILENT"' | wc -l)

    cat <<EOF
    "h_todo_count": $h_todo_count,
    "h_mock_count": $h_mock_count,
    "h_mock_class_count": $h_mock_class_count,
    "h_stub_count": $h_stub_count,
    "h_empty_count": $h_empty_count,
    "h_fallback_count": $h_fallback_count,
    "h_lie_count": $h_lie_count,
    "h_silent_count": $h_silent_count,
    "total_violations": $((h_todo_count + h_mock_count + h_mock_class_count + h_stub_count + h_empty_count + h_fallback_count + h_lie_count + h_silent_count))
EOF
}

# ─── HELPER: Load guard configuration ───────────────────────────────────────
load_guard_config() {
    local config_file=".ggen/config/guard-config.toml"
    if [ -f "$config_file" ]; then
        # Use a more robust TOML parsing approach
        # Extract exclusions array
        GUARD_EXCLUSIONS=$(sed -n '/^exclusions = \[/,/\]/{
            /^exclusions = \[/d;
            /^\]/d;
            s/^[[:space:]]*["'\'']\([^"'\'']*\)["'\''].*/\1/;
            s/[[:space:]]*,*$//;
            p;
        }' "$config_file" | tr '\n' ' ')

        # Extract always_exclude array
        GUARD_ALWAYS_EXCLUDE=$(sed -n '/^always_exclude = \[/,/\]/{
            /^always_exclude = \[/d;
            /^\]/d;
            s/^[[:space:]]*["'\'']\([^"'\'']*\)["'\''].*/\1/;
            s/[[:space:]]*,*$//;
            p;
        }' "$config_file" | tr '\n' ' ')

        # Extract inclusion_patterns array
        GUARD_INCLUSION_PATTERNS=$(sed -n '/^inclusion_patterns = \[/,/\]/{
            /^inclusion_patterns = \[/d;
            /^\]/d;
            s/^[[:space:]]*["'\'']\([^"'\'']*\)["'\''].*/\1/;
            s/[[:space:]]*,*$//;
            p;
        }' "$config_file" | tr '\n' ' ')

        if [ "${VERBOSE:-0}" -eq 1 ]; then
            echo "[DEBUG] Loaded exclusions: $GUARD_EXCLUSIONS" >&2
            echo "[DEBUG] Loaded always_exclude: $GUARD_ALWAYS_EXCLUDE" >&2
        fi
    else
        # Default exclusions if no config file
        GUARD_EXCLUSIONS="test/fixtures/** docs/templates/** **/src/test/** **/*Test.java test/fixtures/h-guards/ ggen/src/test/resources/fixtures/** yawl-ggen/src/test/resources/fixtures/** */fixtures/*"
        GUARD_ALWAYS_EXCLUDE=""
        GUARD_INCLUSION_PATTERNS="src/**/*.java"

        if [ "${VERBOSE:-0}" -eq 1 ]; then
            echo "[DEBUG] Using default exclusions: $GUARD_EXCLUSIONS" >&2
        fi
    fi
}

# ─── HELPER: Check if file should be excluded ───────────────────────────────
should_exclude_file() {
    local file="$1"

    # Convert to relative path if absolute
    if [[ "$file" =~ ^/ ]]; then
        file="${file#$(pwd)/}"
    fi

    # Remove leading ./ if present for consistent pattern matching
    file="${file#./}"

    # Check exclusions with proper glob pattern matching
    for pattern in $GUARD_EXCLUSIONS; do
        # Convert pattern to regex for matching
        local regex_pattern="${pattern//\*/.*}"
        regex_pattern="${regex_pattern//\?/.}"
        regex_pattern="^${regex_pattern}$"

        if [[ "$file" =~ $regex_pattern ]]; then
            if [ "${VERBOSE:-0}" -eq 1 ]; then
                echo "[DEBUG] Excluded file '$file' matches pattern '$pattern'" >&2
            fi
            return 0
        fi

        # Handle ** patterns recursively
        if [[ "$pattern" == "**" ]]; then
            if [[ "$file" =~ ^[A-Za-z0-9_\./-]+$ ]]; then
                return 0
            fi
        elif [[ "$pattern" == "**/"* ]]; then
            local prefix="${pattern#**/}"
            if [[ "$file" == *"$prefix"* ]]; then
                if [ "${VERBOSE:-0}" -eq 1 ]; then
                    echo "[DEBUG] Excluded file '$file' matches recursive pattern '$pattern'" >&2
                fi
                return 0
            fi
        fi
    done

    # Check always exclude
    for pattern in $GUARD_ALWAYS_EXCLUDE; do
        if [[ "$file" == "$pattern" ]]; then
            return 0
        fi
    done

    return 1
}

# ─── PARAMETER PARSING ─────────────────────────────────────────────────────
EMIT_DIR=""
RECEIPT_FILE=".claude/receipts/guard-receipt.json"
JSON_ONLY=0
VERBOSE=0
VALIDATE_EMIT_MODE=0
REMAINING_ARGS=()

# Load guard configuration
load_guard_config

while [[ $# -gt 0 ]]; do
    case "$1" in
        --emit-dir)
            EMIT_DIR="$2"
            VALIDATE_EMIT_MODE=1
            shift 2
            ;;
        --receipt-file)
            RECEIPT_FILE="$2"
            shift 2
            ;;
        --json-only)
            JSON_ONLY=1
            shift
            ;;
        --verbose)
            VERBOSE=1
            shift
            ;;
        --validate-emit)
            # Internal mode for recursive call
            EMIT_DIR="$2"
            RECEIPT_FILE="$3"
            JSON_ONLY="${4:-0}"
            VERBOSE="${5:-0}"
            VALIDATE_EMIT_MODE=1
            shift 5
            ;;
        --file-only)
            # Internal per-file mode — handled later, stop batch accumulation
            REMAINING_ARGS=()
            break
            ;;
        *)
            REMAINING_ARGS+=("$1")
            shift
            ;;
    esac
done

# If --emit-dir provided, validate that directory
if [ "$VALIDATE_EMIT_MODE" -eq 1 ] && [ -n "$EMIT_DIR" ]; then

    # Validate emit directory
    if [ ! -d "$EMIT_DIR" ]; then
        mkdir -p "$(dirname "$RECEIPT_FILE")"
        ERROR_MSG="Emit directory not found: $EMIT_DIR"
        ERROR_MSG_ESC=$(escape_json_string "$ERROR_MSG")
        cat > "$RECEIPT_FILE" <<EOF
{
  "phase": "guards",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "emit_directory": "$EMIT_DIR",
  "files_scanned": 0,
  "violations": [],
  "summary": {
    "h_todo_count": 0,
    "h_mock_count": 0,
    "h_mock_class_count": 0,
    "h_stub_count": 0,
    "h_empty_count": 0,
    "h_fallback_count": 0,
    "h_lie_count": 0,
    "h_silent_count": 0,
    "total_violations": 0
  },
  "status": "RED",
  "error_message": "$ERROR_MSG_ESC"
}
EOF
        if [ "$JSON_ONLY" -eq 0 ]; then
            echo "[hyper-validate.sh] ERROR: $ERROR_MSG" >&2
        fi
        exit 2
    fi

    # Scan Java files in emit directory
    FILES_SCANNED=0
    VIOLATION_COUNT=0
    VIOLATIONS_ARRAY=""
    VIOLATIONS_JSON_BLOCK=""

    # Collect all Java files first, applying exclusions
    java_files=()
    _emit_tmp=$(mktemp)
    find "$EMIT_DIR" -name "*.java" -type f 2>/dev/null > "$_emit_tmp" || true
    while IFS= read -r java_file; do
        # Convert to relative path for exclusion checking
        relative_file="${java_file#$(pwd)/}"
        relative_file="${relative_file#./}"

        # Skip excluded files
        if ! should_exclude_file "$relative_file"; then
            java_files+=("$java_file")
        else
            if [ "$VERBOSE" -eq 1 ]; then
                echo "[hyper-validate.sh] Excluded: $relative_file" >&2
            fi
        fi
    done < "$_emit_tmp"
    rm -f "$_emit_tmp"

    # Define patterns once (outside the loop to avoid declare -A issues in subshells)
    # All 7 H-Guard patterns as documented in CLAUDE.md and H-GUARDS-*.md
    declare -A patterns=(
        [H_TODO]='//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|NOTE:.*implement|REVIEW:.*implement|TEMPORARY|@incomplete|@unimplemented|@stub|@mock|@fake|not\s+implemented\s+yet|coming\s+soon|placeholder|for\s+demo|simplified\s+version|basic\s+implementation)'
        [H_MOCK]='(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]'
        [H_MOCK_CLASS]='(class|interface)\s+(Mock|Stub|Fake|Demo)[A-Za-z]*\s+(implements|extends|\{)'
        [H_STUB]='return\s+""\s*;|return\s+0\s*;.*//.*stub|return\s+null\s*;.*//.*(stub|placeholder)'
        [H_EMPTY]='public\s+void\s+\w+\([^)]*\)\s*\{\s*\}'
        [H_FALLBACK]='catch\s*\([^)]+\)\s*\{[^}]*(return\s+(new|mock|fake|test|"[^"]*"|null)|log\.(warn|error).*not\s+implemented)'
        [H_LIE]='(//\s*@return\s+never\s+null.*return\s+null|//\s*@throws.*throw\s+new)'
        [H_SILENT]='log\.(warn|error)\([^)]*"[^"]*not\s+implemented[^"]*"'
    )

    # Process each Java file
    for java_file in "${java_files[@]}"; do
        FILES_SCANNED=$((FILES_SCANNED + 1))

        for pattern_name in "${!patterns[@]}"; do
            pattern_regex="${patterns[$pattern_name]}"

            if matches=$(grep -n -E "$pattern_regex" "$java_file" 2>/dev/null || true); then
                if [ -n "$matches" ]; then
                    while IFS=':' read -r line_num line_content; do
                        VIOLATION_COUNT=$((VIOLATION_COUNT + 1))

                        # Escape content for JSON
                        content_esc=$(escape_json_string "$line_content")
                        guidance=$(get_fix_guidance "$pattern_name")
                        guidance_esc=$(escape_json_string "$guidance")

                        # Add violation to array
                        violation="{\"pattern\":\"$pattern_name\",\"severity\":\"FAIL\",\"file\":\"$java_file\",\"line\":$line_num,\"content\":\"$content_esc\",\"fix_guidance\":\"$guidance_esc\"}"

                        if [ -z "$VIOLATIONS_ARRAY" ]; then
                            VIOLATIONS_ARRAY="$violation"
                        else
                            VIOLATIONS_ARRAY="$VIOLATIONS_ARRAY,$violation"
                        fi
                    done <<< "$matches"
                fi
            fi
        done
    done

    # Determine status
    STATUS="GREEN"
    if [ "$VIOLATION_COUNT" -gt 0 ]; then
        STATUS="RED"
    fi

    # Create receipt directory
    mkdir -p "$(dirname "$RECEIPT_FILE")"

    # Generate receipt JSON with proper structure
    TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    VIOLATIONS_JSON_BLOCK=""
    if [ -n "$VIOLATIONS_ARRAY" ]; then
        VIOLATIONS_JSON_BLOCK="[$VIOLATIONS_ARRAY]"
    else
        VIOLATIONS_JSON_BLOCK="[]"
    fi

    cat > "$RECEIPT_FILE" <<RECEIPT_EOF
{
  "phase": "guards",
  "timestamp": "$TIMESTAMP",
  "emit_directory": "$EMIT_DIR",
  "files_scanned": $FILES_SCANNED,
  "violations": $VIOLATIONS_JSON_BLOCK,
  "summary": {
$(generate_summary "$VIOLATIONS_ARRAY")
  },
  "status": "$STATUS"
}
RECEIPT_EOF

    # Output based on JSON_ONLY flag
    if [ "$JSON_ONLY" -eq 0 ]; then
        if [ "$STATUS" = "GREEN" ]; then
            echo "[hyper-validate.sh] ✅ PASS: No violations found in $FILES_SCANNED files"
        else
            echo "[hyper-validate.sh] ❌ FAIL: Found $VIOLATION_COUNT violations in $FILES_SCANNED files"
            if [ "$VERBOSE" -eq 1 ]; then
                echo ""
                echo "Top violations:"
                if [ -n "$VIOLATIONS_ARRAY" ]; then
                    echo "$VIOLATIONS_ARRAY" | tr ',' '\n' | head -3
                fi
            fi
        fi
        echo ""
        echo "Receipt written to: $RECEIPT_FILE"
    fi

    [ "$STATUS" = "GREEN" ] && exit 0 || exit 2
fi

# ─── PY-6: Batch mode — scan directories and assert completeness (FM14, FM16) ─
# Usage: bash .claude/hooks/hyper-validate.sh src/ test/ yawl/
if [ "${#REMAINING_ARGS[@]}" -gt 0 ]; then
    set -- "${REMAINING_ARGS[@]}"
    REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m'

    echo "[hyper-validate.sh] Batch mode: scanning $*"

    # Count Java files that will be scanned
    SCANNED=$(find "$@" -name "*.java" 2>/dev/null | wc -l)

    # Attempt to get expected count from Observatory receipt
    OBS_RECEIPT="${REPO_ROOT}/.claude/receipts/observatory.json"
    EXPECTED=0
    if [ -f "$OBS_RECEIPT" ] && command -v jq &>/dev/null; then
        EXPECTED=$(jq -r '.total_java_files // .java_file_count // 0' "$OBS_RECEIPT" 2>/dev/null || echo 0)
    fi

    # PY-6: completeness assertion (must cover ≥90% of known Java files)
    if [ "$EXPECTED" -gt 0 ] && [ "$SCANNED" -lt "$(( EXPECTED * 90 / 100 ))" ]; then
        echo -e "${RED}[FAIL] PY-6 SCAN INCOMPLETE: found $SCANNED Java files, Observatory expects ≥ $EXPECTED${NC}" >&2
        echo "       Add missing source directories to the scan arguments." >&2
        echo "       Expected: $EXPECTED files | Scanned: $SCANNED files ($(( SCANNED * 100 / EXPECTED ))%)" >&2
        exit 2
    elif [ "$EXPECTED" -gt 0 ]; then
        echo -e "${GREEN}[PASS] PY-6 scan completeness: $SCANNED / $EXPECTED files ($(( SCANNED * 100 / EXPECTED ))%)${NC}"
    else
        echo -e "${YELLOW}[WARN] PY-6 Observatory receipt not found — skipping completeness check${NC}"
        echo "       Run: bash scripts/observatory/observatory.sh  to generate facts"
    fi

    # Run individual file checks across all found Java files
    BATCH_FAILURES=0
    JAVA_FILES_TMP=$(mktemp)
    find "$@" -name "*.java" 2>/dev/null > "$JAVA_FILES_TMP" || true
    while IFS= read -r java_file; do
        result=$(FILE="$java_file" TOOL="batch" bash "${BASH_SOURCE[0]}" --file-only "$java_file" 2>&1) || {
            echo "$result" >&2
            BATCH_FAILURES=$((BATCH_FAILURES + 1))
        }
    done < "$JAVA_FILES_TMP"
    rm -f "$JAVA_FILES_TMP"

    if [ "$BATCH_FAILURES" -gt 0 ]; then
        if [ "$JSON_ONLY" -eq 0 ]; then
            echo -e "${RED}[FAIL] Batch scan: $BATCH_FAILURES file(s) with violations${NC}" >&2
        fi
        exit 2
    else
        if [ "$JSON_ONLY" -eq 0 ]; then
            echo -e "${GREEN}[PASS] Batch scan: no violations in $SCANNED files${NC}"
        fi
        exit 0
    fi
fi

# ─── File-only mode (internal batch helper) ───────────────────────────────────
if [ "${1:-}" = "--file-only" ]; then
    FILE="${2:-}"
    TOOL="batch"
    if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then exit 0; fi
else
    # ─── Hook mode: read JSON from stdin ─────────────────────────────────────
    # Check for jq availability
    if ! command -v jq &>/dev/null; then
        echo "[hyper-validate.sh] WARNING: jq not found, skipping validation" >&2
        exit 0
    fi
fi

# Colors for terminal output (may be already set in batch preamble — safe to re-assign)
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Parse hook input (hook mode only; --file-only mode already set FILE and TOOL above)
if [ "${1:-}" != "--file-only" ]; then
    INPUT=$(cat)
    FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
    TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
fi

# Only validate Java source files in src/
if [[ ! "$FILE" =~ \.java$ ]]; then
    exit 0
fi

# Convert to relative path for exclusion checking
if [[ "$FILE" =~ ^/ ]]; then
    relative_file="${FILE#$(pwd)/}"
else
    relative_file="$FILE"
fi

# Skip validation for excluded files using guard configuration
if should_exclude_file "$relative_file"; then
    if [ "${VERBOSE:-0}" -eq 1 ]; then
        echo "[hyper-validate.sh] Excluded: $relative_file" >&2
    fi
    exit 0
fi

# Skip validation for deprecated orderfulfillment package (legacy code)
if [[ "$relative_file" =~ /orderfulfillment/ ]]; then
    exit 0
fi

# Check if file exists and is readable
if [[ ! -f "$FILE" ]]; then
    exit 0
fi

VIOLATIONS=()
VIOLATION_LINES=()

# === CHECK 1: TODO-LIKE MARKERS (Deferred Work) ===
# Catches: TODO, FIXME, XXX, HACK, LATER, FUTURE, NOTE:...implement, etc.
TODO_PATTERN='//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|NOTE:.*implement|REVIEW:.*implement|TEMPORARY|@incomplete|@unimplemented|@stub|@mock|@fake|not\s+implemented\s+yet|coming\s+soon|placeholder|for\s+demo|simplified\s+version|basic\s+implementation)'

if matches=$(grep -n -E "$TODO_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ DEFERRED WORK MARKERS (TODO-like comments)")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 2: MOCK/STUB METHOD NAMES ===
# Catches: mockFetch(), getMockData(), stubValidation(), demoResponse()
# Note: test, temp, sample are legitimate prefixes and not flagged
MOCK_NAME_PATTERN='(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]'

if matches=$(grep -n -E "$MOCK_NAME_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ MOCK/STUB PATTERNS in method/variable names")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 3: MOCK CLASS NAMES ===
# Catches: class MockService, class FakeRepository
MOCK_CLASS_PATTERN='(class|interface)\s+(Mock|Stub|Fake|Demo)[A-Za-z]*\s+(implements|extends|\{)'

if matches=$(grep -n -E "$MOCK_CLASS_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ MOCK/STUB CLASS names")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 4: MOCK MODE FLAGS ===
# Catches: boolean useMockData = true, static final boolean MOCK_MODE = true
MOCK_FLAG_PATTERN='(is|use|enable|allow)(Mock|Fake|Demo|Stub)(Mode|Data|ing)\s*='

if matches=$(grep -n -E "$MOCK_FLAG_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ MOCK MODE FLAGS detected")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 5: EMPTY STRING RETURNS (Stubs) ===
# Catches: return ""; (likely stub)
EMPTY_STRING_PATTERN='return\s+""\s*;'

if matches=$(grep -n -E "$EMPTY_STRING_PATTERN" "$FILE" 2>/dev/null); then
    # Filter out legitimate cases (error messages, defaults with context)
    if ! echo "$matches" | grep -q -E '(error|message|default.*config|empty.*valid)'; then
        VIOLATIONS+=("❌ EMPTY STRING RETURNS (possible stub)")
        VIOLATION_LINES+=("$matches")
    fi
fi

# === CHECK 6: SUSPICIOUS NULL RETURNS WITH STUB COMMENTS ===
# Catches: return null; // stub, return null; // TODO, etc.
NULL_STUB_PATTERN='return\s+null\s*;.*//\s*(stub|todo|placeholder|not\s+implemented|temporary)'

if matches=$(grep -n -E -i "$NULL_STUB_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ NULL RETURNS with stub-like comments")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 7: NO-OP METHOD BODIES (Stubs) ===
# Catches: public void method() { }
NOOP_PATTERN='public\s+void\s+\w+\([^)]*\)\s*\{\s*\}'

if matches=$(grep -n -E "$NOOP_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ EMPTY METHOD BODIES (no-op stubs)")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 8: PLACEHOLDER CONSTANTS ===
# Catches: DUMMY_CONFIG = ..., PLACEHOLDER_VALUE = ...
# Note: DEFAULT, TEST, SAMPLE, TEMP are legitimate prefixes and not flagged
PLACEHOLDER_CONST_PATTERN='(DUMMY|PLACEHOLDER|MOCK|FAKE)_[A-Z_]+\s*='

if matches=$(grep -n -E "$PLACEHOLDER_CONST_PATTERN" "$FILE" 2>/dev/null); then
    # Filter out legitimate defaults (like DEFAULT_TIMEOUT, DEFAULT_PORT)
    if echo "$matches" | grep -E -v '(DEFAULT_(TIMEOUT|PORT|HOST|BUFFER|SIZE|LIMIT))'; then
        VIOLATIONS+=("❌ PLACEHOLDER CONSTANTS detected")
        VIOLATION_LINES+=("$matches")
    fi
fi

# === CHECK 9: SILENT FALLBACK PATTERNS ===
# Catches: catch (Exception e) { return mockData(); }
# Catches: catch (Exception e) { log.warn("..."); return fake; }
FALLBACK_PATTERN='catch\s*\([^)]+\)\s*\{[^}]*(return\s+(new|mock|fake|test|"[^"]*"|null)|log\.(warn|error).*not\s+implemented)'

if matches=$(grep -n -E "$FALLBACK_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ SILENT FALLBACK to fake data in catch block")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 10: CONDITIONAL MOCK BEHAVIOR ===
# Catches: if (isTestMode) return mockData();
# Catches: if (sdk == null) return fakeResponse();
CONDITIONAL_MOCK_PATTERN='if\s*\([^)]*\)\s*return\s+(mock|fake|test|sample|demo)[A-Z][a-zA-Z]*\(\)'

if matches=$(grep -n -E "$CONDITIONAL_MOCK_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ CONDITIONAL MOCK BEHAVIOR")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 11: GETORDEFAULT WITH SUSPICIOUS DEFAULTS ===
# Catches: .getOrDefault(key, "test_value")
GETORDEFAULT_PATTERN='\.getOrDefault\([^,]+,\s*"(test|mock|fake|default|sample|placeholder)'

if matches=$(grep -n -E "$GETORDEFAULT_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ getOrDefault() with suspicious fake default value")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 12: EARLY RETURN THAT SKIPS ALL LOGIC ===
# Catches: if (true) return; (skip all logic)
EARLY_RETURN_PATTERN='if\s*\(true\)\s*return\s*;'

if matches=$(grep -n -E "$EARLY_RETURN_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ EARLY RETURN skips all logic (if true return)")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 13: LOG INSTEAD OF THROW ===
# Catches: log.warn("not implemented"); (silent failure)
LOG_INSTEAD_THROW_PATTERN='log\.(warn|error)\([^)]*"[^"]*not\s+implemented[^"]*"'

if matches=$(grep -n -E "$LOG_INSTEAD_THROW_PATTERN" "$FILE" 2>/dev/null); then
    VIOLATIONS+=("❌ LOG.WARN instead of throwing exception")
    VIOLATION_LINES+=("$matches")
fi

# === CHECK 14: MOCK FRAMEWORK IMPORTS (should never be in src/) ===
if [[ "$FILE" =~ /src/ ]] && ! [[ "$FILE" =~ /test/ ]]; then
    MOCK_IMPORT_PATTERN='import\s+(org\.mockito|org\.easymock|org\.jmock|org\.powermock)'

    if matches=$(grep -n -E "$MOCK_IMPORT_PATTERN" "$FILE" 2>/dev/null); then
        VIOLATIONS+=("❌ MOCK FRAMEWORK imports in src/ (should only be in test/)")
        VIOLATION_LINES+=("$matches")
    fi
fi

# === REPORT VIOLATIONS ===
if [ ${#VIOLATIONS[@]} -gt 0 ]; then
    echo "" >&2
    echo -e "${RED}╔═══════════════════════════════════════════════════════════════════╗${NC}" >&2
    echo -e "${RED}║  🚨 FORTUNE 5 STANDARDS VIOLATION DETECTED                       ║${NC}" >&2
    echo -e "${RED}╚═══════════════════════════════════════════════════════════════════╝${NC}" >&2
    echo "" >&2
    echo -e "${YELLOW}File: $FILE${NC}" >&2
    echo -e "${YELLOW}Tool: $TOOL${NC}" >&2
    echo "" >&2

    for i in "${!VIOLATIONS[@]}"; do
        echo -e "${RED}${VIOLATIONS[$i]}${NC}" >&2
        echo "${VIOLATION_LINES[$i]}" | head -3 >&2
        echo "" >&2
    done

    echo -e "${YELLOW}This code violates CLAUDE.md MANDATORY CODING STANDARDS:${NC}" >&2
    echo "" >&2
    echo "  1. NO DEFERRED WORK - No TODO/FIXME/XXX/HACK" >&2
    echo "  2. NO MOCKS - No mock/stub/fake behavior" >&2
    echo "  3. NO STUBS - No empty/placeholder implementations" >&2
    echo "  4. NO FALLBACKS - No silent degradation to fake data" >&2
    echo "  5. NO LIES - Code must do what it claims" >&2
    echo "" >&2
    echo "You MUST either:" >&2
    echo "  ✅ Implement the REAL version (with real dependencies)" >&2
    echo "  ✅ Throw UnsupportedOperationException with clear message" >&2
    echo "  ❌ NEVER write mock/stub/placeholder code" >&2
    echo "" >&2
    echo "See: CLAUDE.md lines 13-101 for detailed standards" >&2
    echo "See: .claude/HYPER_STANDARDS.md for comprehensive examples" >&2
    echo "" >&2

    exit 2  # Exit 2 = Block tool and show stderr to Claude
fi

# All checks passed
exit 0
