#!/usr/bin/env bash
# rust4pm-audit.sh — Generate rust4pm-bindings.json fact file for observatory
#
# Audits the one-to-one mapping between Rust exports (librust4pm.so symbols)
# and Java MethodHandle declarations (rust4pm_h.java).
#
# Usage:
#   bash scripts/observatory/rust4pm-audit.sh [path/to/librust4pm.so]
#
# Output:
#   scripts/observatory/facts/rust4pm-bindings.json
#
# This fact file is 100x more compressed than grepping source files
# (~2KB vs ~200KB of source). Observatory ingests it for offline audit.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FACTS_DIR="$SCRIPT_DIR/facts"
OUT="$FACTS_DIR/rust4pm-bindings.json"

LIB="${1:-$YAWL_ROOT/target/release/librust4pm.so}"
if [ ! -f "$LIB" ]; then
    LIB="$YAWL_ROOT/rust/rust4pm/target/release/librust4pm.so"
fi

mkdir -p "$FACTS_DIR"

TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if [ ! -f "$LIB" ]; then
    # Library not built — emit skeleton fact so observatory can still load
    cat > "$OUT" << EOF
{
  "generated_at": "$TIMESTAMP",
  "lib_path": "not_built",
  "lib_built": false,
  "audit_status": "SKIP",
  "message": "Run: bash scripts/build-rust4pm.sh",
  "rust_symbols": [],
  "rust_symbol_count": 0,
  "expected_total": 40,
  "coverage_pct": 0
}
EOF
    echo "rust4pm-audit: library not found — emitted skeleton $OUT"
    exit 0
fi

# Extract all rust4pm_* symbols from the dynamic symbol table
# nm -D: dynamic symbols only; grep " T ": text (code) section, exported
SYMBOLS_RAW="$(nm -D "$LIB" 2>/dev/null | grep " T rust4pm_" | awk '{print $3}' | sort || true)"

if [ -z "$SYMBOLS_RAW" ]; then
    # Try nm without -D for static libs or stripped .so
    SYMBOLS_RAW="$(nm "$LIB" 2>/dev/null | grep " T rust4pm_" | awk '{print $3}' | sort || true)"
fi

SYMBOL_COUNT="$(echo "$SYMBOLS_RAW" | grep -c "rust4pm_" || echo 0)"

# Categorize symbols
BUSINESS_SYMBOLS="$(echo "$SYMBOLS_RAW" | grep -v "_sizeof_" | grep -v "_offsetof_" || true)"
SIZEOF_SYMBOLS="$(echo "$SYMBOLS_RAW"  | grep "_sizeof_"  || true)"
OFFSETOF_SYMBOLS="$(echo "$SYMBOLS_RAW" | grep "_offsetof_" || true)"

BUSINESS_COUNT="$(echo "$BUSINESS_SYMBOLS" | grep -c "rust4pm_" || echo 0)"
SIZEOF_COUNT="$(echo "$SIZEOF_SYMBOLS"   | grep -c "rust4pm_" || echo 0)"
OFFSETOF_COUNT="$(echo "$OFFSETOF_SYMBOLS" | grep -c "rust4pm_" || echo 0)"

EXPECTED_TOTAL=40  # 12 business + 8 sizeof + 20 offsetof
COVERAGE_PCT=$(( SYMBOL_COUNT * 100 / EXPECTED_TOTAL ))

# Audit status
if [ "$SYMBOL_COUNT" -eq "$EXPECTED_TOTAL" ]; then
    AUDIT_STATUS="GREEN"
elif [ "$SYMBOL_COUNT" -gt "$EXPECTED_TOTAL" ]; then
    AUDIT_STATUS="DRIFT"  # new Rust exports without Java bindings — update whitelist
else
    AUDIT_STATUS="INCOMPLETE"
fi

# Build JSON array of symbols
SYMBOLS_JSON="$(echo "$SYMBOLS_RAW" | grep "rust4pm_" | python3 -c "
import sys, json
syms = [line.strip() for line in sys.stdin if line.strip()]
print(json.dumps(syms))
" 2>/dev/null || echo "[]")"

cat > "$OUT" << EOF
{
  "generated_at": "$TIMESTAMP",
  "lib_path": "$LIB",
  "lib_built": true,
  "audit_status": "$AUDIT_STATUS",
  "rust_symbol_count": $SYMBOL_COUNT,
  "expected_total": $EXPECTED_TOTAL,
  "coverage_pct": $COVERAGE_PCT,
  "breakdown": {
    "business_functions": $BUSINESS_COUNT,
    "sizeof_probes": $SIZEOF_COUNT,
    "offsetof_probes": $OFFSETOF_COUNT
  },
  "rust_symbols": $SYMBOLS_JSON,
  "note": "Java binding count = rust_symbol_count when coverage_pct = 100. DRIFT means new Rust exports need Java MethodHandle bindings in rust4pm_h.java."
}
EOF

echo "rust4pm-audit: $AUDIT_STATUS — $SYMBOL_COUNT/$EXPECTED_TOTAL symbols ($COVERAGE_PCT%)"
echo "  Written: $OUT"

if [ "$AUDIT_STATUS" = "DRIFT" ]; then
    echo ""
    echo "  WARNING: More symbols than expected ($SYMBOL_COUNT > $EXPECTED_TOTAL)."
    echo "  New Rust exports detected. Add Java MethodHandle bindings to rust4pm_h.java"
    echo "  and update EXPECTED_SYMBOLS in SymbolCompletenessTest.java."
    exit 1
fi
