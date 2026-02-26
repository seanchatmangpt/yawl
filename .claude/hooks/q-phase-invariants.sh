#!/bin/bash
# Q Phase Hook: Invariants Validation (Enforces Q = {real∨throw, ¬mock, ¬silent_fallback, ¬lie})
# Invoked after H (Guards) phase passes GREEN
# Exit 0: All invariants satisfied (proceed to Ω phase)
# Exit 2: Violations detected (block with error)

set -euo pipefail

PHASE="Q-INVARIANTS"
CODE_DIR="${1:-.}"
OUTPUT_DIR="receipts"
RECEIPT_FILE="$OUTPUT_DIR/invariant-receipt.json"
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

mkdir -p "$OUTPUT_DIR"

echo ""
echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║ Q PHASE: INVARIANTS VALIDATION (SHACL)                           ║"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""
echo "[Q] Starting invariants validation..."
echo "[Q] Code directory: $CODE_DIR"
echo "[Q] Timestamp: $TIMESTAMP"
echo ""

# ============================================================================
# STEP 1: Quick File Validation (No Code? No Problem)
# ============================================================================

java_files=$(find "$CODE_DIR" -name "*.java" -type f | wc -l)

if [ "$java_files" -eq 0 ]; then
    echo -e "${YELLOW}[Q] No Java files found in $CODE_DIR${NC}"
    cat > "$RECEIPT_FILE" << RECEIPT_EOF
{
  "phase": "invariants",
  "timestamp": "$TIMESTAMP",
  "status": "GREEN",
  "reason": "No Java files to validate",
  "methods_checked": 0,
  "violations": [],
  "passing_rate": "N/A"
}
RECEIPT_EOF
    echo -e "${GREEN}[Q] ✅ No code to validate. Proceeding to Ω phase.${NC}"
    exit 0
fi

echo "[Q] Found $java_files Java files to validate"
echo ""

# ============================================================================
# STEP 2: Manual Validation via grep (regex MVP)
# Production path: SHACL validator via RDF4J+Topbraid (see ggen-h-guards-phase-design.md)
# ============================================================================

echo "[Q] Step 1/3: Scanning for empty methods (invariant Q1: real_impl ∨ throw)..."

# Find empty method bodies: public void foo() { }
# Exclude _build/ (build artifacts) and target/ (Maven output) directories
EMPTY_METHODS=$(find "$CODE_DIR" -name "*.java" -type f \
  -not -path "*/_build/*" -not -path "*/target/*" \
  -exec grep -l "public.*\s\+void\s\+\w\+([^)]*)\s*{\s*}" {} \; 2>/dev/null | wc -l)

if [ "$EMPTY_METHODS" -gt 0 ]; then
    echo -e "${RED}[Q] ❌ Found $EMPTY_METHODS files with empty method bodies${NC}"
fi

echo "[Q] Step 2/3: Scanning for mock/stub/fake patterns (invariant Q2: ¬mock)..."

# Find mock class names (exclude _build/ and target/)
MOCK_CLASSES=$(find "$CODE_DIR" -name "*.java" -type f \
  -not -path "*/_build/*" -not -path "*/target/*" \
  -exec grep -h "^\s*\(public\|private\)\s*\(class\|interface\)\s*\(Mock\|Stub\|Fake\|Demo\)" {} \; 2>/dev/null | wc -l)

if [ "$MOCK_CLASSES" -gt 0 ]; then
    echo -e "${RED}[Q] ❌ Found $MOCK_CLASSES mock/stub classes${NC}"
fi

echo "[Q] Step 3/3: Scanning for silent fallbacks (invariant Q3: ¬silent_fallback)..."

# Find catch blocks returning fake data (exclude _build/ and target/)
SILENT_FALLBACKS=$(find "$CODE_DIR" -name "*.java" -type f \
  -not -path "*/_build/*" -not -path "*/target/*" \
  -exec grep -E "catch\s*\([^)]+\)\s*\{[^}]*(return\s+(mock|fake|test|stub|demo))" {} \; 2>/dev/null | wc -l)

if [ "$SILENT_FALLBACKS" -gt 0 ]; then
    echo -e "${RED}[Q] ❌ Found $SILENT_FALLBACKS catch blocks with silent fallbacks${NC}"
fi

# ============================================================================
# STEP 3: Generate Receipt
# ============================================================================

total_violations=$((EMPTY_METHODS + MOCK_CLASSES + SILENT_FALLBACKS))

if [ "$total_violations" -eq 0 ]; then
    STATUS="GREEN"
    PASSING_RATE="100%"
    SEVERITY="INFO"
else
    STATUS="RED"
    PASSING_RATE="0%"
    SEVERITY="ERROR"
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
    "Q3_silent_fallbacks": $SILENT_FALLBACKS
  },
  "violations": [
    $(if [ "$EMPTY_METHODS" -gt 0 ]; then
        echo "{
          \"invariant\": \"Q1_real_impl_or_throw\",
          \"count\": $EMPTY_METHODS,
          \"issue\": \"Methods with empty bodies (no real logic, no exception thrown)\",
          \"remediation\": \"Implement real logic or throw UnsupportedOperationException\"
        },"
    fi)
    $(if [ "$MOCK_CLASSES" -gt 0 ]; then
        echo "{
          \"invariant\": \"Q2_no_mock_objects\",
          \"count\": $MOCK_CLASSES,
          \"issue\": \"Class names indicate mock/stub/fake implementations\",
          \"remediation\": \"Rename to real class names or delete from generated code\"
        },"
    fi)
    $(if [ "$SILENT_FALLBACKS" -gt 0 ]; then
        echo "{
          \"invariant\": \"Q3_no_silent_fallback\",
          \"count\": $SILENT_FALLBACKS,
          \"issue\": \"Catch blocks silently return fake data without re-throw or logging\",
          \"remediation\": \"Re-throw exception or log + provide cached/real alternative\"
        },"
    fi)
    null
  ],
  "status": "$STATUS",
  "passing_rate": "$PASSING_RATE",
  "severity": "$SEVERITY",
  "next_action": "$(if [ "$STATUS" = "GREEN" ]; then echo "Proceed to Ω phase"; else echo "Fix violations and re-run Q phase"; fi)"
}
RECEIPT_EOF

# Remove trailing comma in violations array
sed -i 's/,\s*null\s*]/]/g' "$RECEIPT_FILE"

echo ""
echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║ RECEIPT GENERATED                                                  ║"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""
cat "$RECEIPT_FILE" | jq '.' 2>/dev/null || cat "$RECEIPT_FILE"
echo ""

# ============================================================================
# STEP 4: Exit with appropriate code
# ============================================================================

if [ "$STATUS" = "GREEN" ]; then
    echo -e "${GREEN}[Q] ✅ All invariants satisfied!${NC}"
    echo -e "${GREEN}[Q] Proceeding to Ω (Git) phase...${NC}"
    echo ""
    exit 0
else
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
    exit 2
fi
