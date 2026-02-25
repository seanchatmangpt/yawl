#!/bin/bash
# Hyper-Advanced Validation Hook for YAWL Fortune 5 Standards
# Runs after Write/Edit tool use to catch ALL forbidden patterns
# Exit 2 = Block and show violations to Claude

set -euo pipefail

# Check for jq availability
if ! command -v jq &>/dev/null; then
    echo "[hyper-validate.sh] WARNING: jq not found, skipping validation" >&2
    exit 0
fi

# Colors for terminal output
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Parse hook input
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')

# Only validate Java source files in src/
if [[ ! "$FILE" =~ \.java$ ]] || [[ ! "$FILE" =~ ^/.*/(src|test)/ ]]; then
    exit 0
fi

# Skip validation for deprecated orderfulfillment package (legacy code)
if [[ "$FILE" =~ /orderfulfillment/ ]]; then
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
