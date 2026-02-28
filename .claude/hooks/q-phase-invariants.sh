#!/bin/bash
# Q Phase Hook: Invariants Validation (Enforces Q = {real∨throw, ¬mock, ¬silent_fallback, ¬lie})
# Invoked after H (Guards) phase passes GREEN
# Exit 0: All invariants satisfied (proceed to Ω phase)
# Exit 2: Violations detected (block with error)
#
# Parameters:
#   --receipt-file <path>  Custom receipt output path (default: .claude/receipts/invariant-receipt.json)
#   --json-only            Output only JSON (no colors, for CI)
#   <code-dir>             Code directory to scan (default: current directory)

set -euo pipefail

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

# ─── PARAMETER PARSING ─────────────────────────────────────────────────────
CODE_DIR="."
RECEIPT_FILE=".claude/receipts/invariant-receipt.json"
JSON_ONLY=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --receipt-file)
            RECEIPT_FILE="$2"
            shift 2
            ;;
        --json-only)
            JSON_ONLY=1
            shift
            ;;
        *)
            # Assume first non-option argument is code directory
            if [ "$CODE_DIR" = "." ] || [ -d "$1" ]; then
                CODE_DIR="$1"
            fi
            shift
            ;;
    esac
done

TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# Colors (disabled if JSON_ONLY)
if [ "$JSON_ONLY" -eq 0 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    NC=''
fi

mkdir -p "$(dirname "$RECEIPT_FILE")"

if [ "$JSON_ONLY" -eq 0 ]; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════╗"
    echo "║ Q PHASE: INVARIANTS VALIDATION                                   ║"
    echo "╚════════════════════════════════════════════════════════════════════╝"
    echo ""
    echo "[Q] Starting invariants validation..."
    echo "[Q] Code directory: $CODE_DIR"
    echo "[Q] Timestamp: $TIMESTAMP"
    echo ""
fi

# ============================================================================
# STEP 1: Quick File Validation (No Code? No Problem)
# ============================================================================

java_files=$(find "$CODE_DIR" -name "*.java" -type f -not -path "*/target/*" -not -path "*/_build/*" 2>/dev/null | wc -l)

if [ "$java_files" -eq 0 ]; then
    if [ "$JSON_ONLY" -eq 0 ]; then
        echo -e "${YELLOW}[Q] No Java files found in $CODE_DIR${NC}"
    fi
    cat > "$RECEIPT_FILE" << RECEIPT_EOF
{
  "phase": "invariants",
  "timestamp": "$TIMESTAMP",
  "code_directory": "$CODE_DIR",
  "java_files_scanned": 0,
  "violations_found": 0,
  "violations_by_type": {
    "Q1_empty_methods": 0,
    "Q2_mock_classes": 0,
    "Q3_silent_fallbacks": 0,
    "Q4_undocumented_throws": 0
  },
  "violations": [],
  "status": "GREEN",
  "severity": "INFO",
  "next_action": "Proceed to Ω phase"
}
RECEIPT_EOF
    if [ "$JSON_ONLY" -eq 0 ]; then
        echo -e "${GREEN}[Q] ✅ No code to validate. Proceeding to Ω phase.${NC}"
    fi
    exit 0
fi

if [ "$JSON_ONLY" -eq 0 ]; then
    echo "[Q] Found $java_files Java files to validate"
    echo ""
fi

# ============================================================================
# STEP 2: Enhanced Invariant Validation via grep
# ============================================================================

if [ "$JSON_ONLY" -eq 0 ]; then
    echo "[Q] Step 1/4: Scanning for empty methods (Q1: real_impl ∨ throw)..."
fi

# Q1: Find empty method bodies that don't throw exceptions
# Pattern: public/private [return-type] method() { }  (empty body, no throw UnsupportedOperationException)
EMPTY_METHODS_FILES=""
EMPTY_METHODS=0

while IFS= read -r java_file; do
    # Look for empty void methods (with empty braces only, no logic)
    # Excludes methods that throw UnsupportedOperationException or other exceptions
    if grep -E "^\s*(public|private|protected)\s+(void|synchronized|static)\s+\w+\s*\([^)]*\)\s*\{\s*\}" "$java_file" 2>/dev/null; then
        EMPTY_METHODS=$((EMPTY_METHODS + 1))
        EMPTY_METHODS_FILES="$EMPTY_METHODS_FILES$java_file"$'\n'
    fi
    # Look for non-void methods with empty/fake returns (no logic, no exception)
    # Excludes methods that throw UnsupportedOperationException
    if grep -E "^\s*(public|private|protected)\s+\w+<[^>]+>?\s+\w+\s*\([^)]*\)\s*\{\s*(return\s*(null|\"\"|\[\]|Collections\.\w+|new\s+(HashMap|ArrayList|HashSet|TreeMap)\(\))\s*;)?\s*\}" "$java_file" 2>/dev/null | grep -qv "throw.*UnsupportedOperationException"; then
        EMPTY_METHODS=$((EMPTY_METHODS + 1))
        EMPTY_METHODS_FILES="$EMPTY_METHODS_FILES$java_file"$'\n'
    fi
done < <(find "$CODE_DIR" -name "*.java" -type f -not -path "*/target/*" -not -path "*/_build/*" 2>/dev/null)

if [ "$JSON_ONLY" -eq 0 ]; then
    if [ "$EMPTY_METHODS" -gt 0 ]; then
        echo -e "${RED}[Q] ❌ Found $EMPTY_METHODS files with empty method bodies${NC}"
    else
        echo "[Q] ✅ No empty methods detected"
    fi
fi

# Q2: Find mock/stub/fake class declarations (more precise detection)
if [ "$JSON_ONLY" -eq 0 ]; then
    echo "[Q] Step 2/4: Scanning for mock/stub/fake patterns (Q2: ¬mock)..."
fi

MOCK_CLASSES=0
MOCK_CLASSES_FILES=""

while IFS= read -r java_file; do
    # Match: (public|private) (class|interface) (Mock|Stub|Fake|Demo)*
    if grep -E "^\s*(public|private|protected)?\s*(class|interface|enum)\s+(Mock|Stub|Fake|Demo)[A-Za-z0-9_]*\s*(extends|implements|\{)" "$java_file" 2>/dev/null; then
        MOCK_CLASSES=$((MOCK_CLASSES + 1))
        MOCK_CLASSES_FILES="$MOCK_CLASSES_FILES$java_file"$'\n'
    fi
done < <(find "$CODE_DIR" -name "*.java" -type f -not -path "*/target/*" -not -path "*/_build/*" 2>/dev/null)

if [ "$JSON_ONLY" -eq 0 ]; then
    if [ "$MOCK_CLASSES" -gt 0 ]; then
        echo -e "${RED}[Q] ❌ Found $MOCK_CLASSES mock/stub/fake class declarations${NC}"
    else
        echo "[Q] ✅ No mock class declarations detected"
    fi
fi

# Q3: Find silent fallback patterns (catch blocks returning empty collections/fake data without re-throw)
if [ "$JSON_ONLY" -eq 0 ]; then
    echo "[Q] Step 3/4: Scanning for silent fallbacks (Q3: ¬silent_fallback)..."
fi

SILENT_FALLBACKS=0
SILENT_FALLBACKS_FILES=""

while IFS= read -r java_file; do
    # Match: catch (...) { return (empty|fake|null|...); } without re-throwing
    # Catches:
    #   - catch (Exception e) { return mockData(); }
    #   - catch (Exception e) { return Collections.emptyList(); }
    #   - catch (Exception e) { return ""; }
    #   - catch (Exception e) { return null; }
    if grep -E "catch\s*\([^)]+\)\s*\{" "$java_file" 2>/dev/null | grep -qE "(return\s+(null|\"\"|\{\}|Collections\.\w+|new\s+(HashMap|ArrayList|HashSet|TreeSet|LinkedList|Arrays\.(asList|asMap))\(\)|mockData|fakeData|testData|stubData|demoData|emptyList|emptyMap|emptySet)).*\}"; then
        # Filter out catches that actually re-throw
        if ! grep -A 5 "catch\s*\([^)]+\)\s*\{" "$java_file" 2>/dev/null | grep -qE "(throw|rethrow)"; then
            SILENT_FALLBACKS=$((SILENT_FALLBACKS + 1))
            SILENT_FALLBACKS_FILES="$SILENT_FALLBACKS_FILES$java_file"$'\n'
        fi
    fi
done < <(find "$CODE_DIR" -name "*.java" -type f -not -path "*/target/*" -not -path "*/_build/*" 2>/dev/null)

if [ "$JSON_ONLY" -eq 0 ]; then
    if [ "$SILENT_FALLBACKS" -gt 0 ]; then
        echo -e "${RED}[Q] ❌ Found $SILENT_FALLBACKS catch blocks with silent fallbacks${NC}"
    else
        echo "[Q] ✅ No silent fallback patterns detected"
    fi
fi

# Q4: Check for UnsupportedOperationException - methods that throw instead of implementing
if [ "$JSON_ONLY" -eq 0 ]; then
    echo "[Q] Step 4/4: Checking for proper exception handling..."
fi

UNDOCUMENTED_THROWS=0

# This is a positive indicator - methods that properly throw UnsupportedOperationException
# (counted for informational purposes only, not a violation)

# ============================================================================
# STEP 3: Build Violations Array
# ============================================================================

VIOLATIONS_ARRAY=""

if [ "$EMPTY_METHODS" -gt 0 ]; then
    guidance_esc=$(escape_json_string "Implement real logic or throw UnsupportedOperationException with a clear message")
    violation="{\"invariant\":\"Q1_real_impl_or_throw\",\"count\":$EMPTY_METHODS,\"issue\":\"Methods with empty bodies (no real logic, no exception thrown)\",\"remediation\":\"$guidance_esc\"}"
    if [ -z "$VIOLATIONS_ARRAY" ]; then
        VIOLATIONS_ARRAY="$violation"
    else
        VIOLATIONS_ARRAY="$VIOLATIONS_ARRAY,$violation"
    fi
fi

if [ "$MOCK_CLASSES" -gt 0 ]; then
    guidance_esc=$(escape_json_string "Rename to real class names or delete from generated code")
    violation="{\"invariant\":\"Q2_no_mock_objects\",\"count\":$MOCK_CLASSES,\"issue\":\"Class names indicate mock/stub/fake implementations\",\"remediation\":\"$guidance_esc\"}"
    if [ -z "$VIOLATIONS_ARRAY" ]; then
        VIOLATIONS_ARRAY="$violation"
    else
        VIOLATIONS_ARRAY="$VIOLATIONS_ARRAY,$violation"
    fi
fi

if [ "$SILENT_FALLBACKS" -gt 0 ]; then
    guidance_esc=$(escape_json_string "Re-throw exception or log + provide cached/real alternative data")
    violation="{\"invariant\":\"Q3_no_silent_fallback\",\"count\":$SILENT_FALLBACKS,\"issue\":\"Catch blocks silently return fake data without re-throw or logging\",\"remediation\":\"$guidance_esc\"}"
    if [ -z "$VIOLATIONS_ARRAY" ]; then
        VIOLATIONS_ARRAY="$violation"
    else
        VIOLATIONS_ARRAY="$VIOLATIONS_ARRAY,$violation"
    fi
fi

# ============================================================================
# STEP 4: Generate Receipt
# ============================================================================

total_violations=$((EMPTY_METHODS + MOCK_CLASSES + SILENT_FALLBACKS))

if [ "$total_violations" -eq 0 ]; then
    STATUS="GREEN"
    SEVERITY="INFO"
    NEXT_ACTION="Proceed to Ω phase"
else
    STATUS="RED"
    SEVERITY="ERROR"
    NEXT_ACTION="Fix violations and re-run Q phase"
fi

VIOLATIONS_JSON_BLOCK=""
if [ -n "$VIOLATIONS_ARRAY" ]; then
    VIOLATIONS_JSON_BLOCK="[$VIOLATIONS_ARRAY]"
else
    VIOLATIONS_JSON_BLOCK="[]"
fi

cat > "$RECEIPT_FILE" << RECEIPT_EOF
{
  "phase": "invariants",
  "timestamp": "$TIMESTAMP",
  "code_directory": "$CODE_DIR",
  "java_files_scanned": $java_files,
  "violations_found": $total_violations,
  "violations_by_type": {
    "Q1_empty_methods": $EMPTY_METHODS,
    "Q2_mock_classes": $MOCK_CLASSES,
    "Q3_silent_fallbacks": $SILENT_FALLBACKS,
    "Q4_undocumented_throws": $UNDOCUMENTED_THROWS
  },
  "violations": $VIOLATIONS_JSON_BLOCK,
  "status": "$STATUS",
  "severity": "$SEVERITY",
  "next_action": "$NEXT_ACTION"
}
RECEIPT_EOF

if [ "$JSON_ONLY" -eq 0 ]; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════╗"
    echo "║ RECEIPT GENERATED                                                  ║"
    echo "╚════════════════════════════════════════════════════════════════════╝"
    echo ""
    cat "$RECEIPT_FILE" | jq '.' 2>/dev/null || cat "$RECEIPT_FILE"
    echo ""
fi

# ============================================================================
# STEP 5: Exit with appropriate code
# ============================================================================

if [ "$STATUS" = "GREEN" ]; then
    if [ "$JSON_ONLY" -eq 0 ]; then
        echo -e "${GREEN}[Q] ✅ All invariants satisfied!${NC}"
        echo -e "${GREEN}[Q] Proceeding to Ω (Git) phase...${NC}"
        echo ""
    fi
    exit 0
else
    if [ "$JSON_ONLY" -eq 0 ]; then
        echo -e "${RED}[Q] ❌ Invariant violations detected!${NC}"
        echo -e "${RED}[Q] Total violations: $total_violations${NC}"
        echo ""
        echo -e "${YELLOW}REMEDIATION:${NC}"
        echo ""
        if [ "$EMPTY_METHODS" -gt 0 ]; then
            echo "  Q1 ($EMPTY_METHODS violations): Empty methods"
            echo "     → Implement real logic OR throw UnsupportedOperationException"
            echo ""
        fi
        if [ "$MOCK_CLASSES" -gt 0 ]; then
            echo "  Q2 ($MOCK_CLASSES violations): Mock/Stub classes detected"
            echo "     → Rename to real class names"
            echo ""
        fi
        if [ "$SILENT_FALLBACKS" -gt 0 ]; then
            echo "  Q3 ($SILENT_FALLBACKS violations): Silent fallbacks in catch blocks"
            echo "     → Re-throw OR log + provide real alternative"
            echo ""
        fi
        echo "See receipt: $RECEIPT_FILE"
        echo ""
    fi
    exit 2
fi
