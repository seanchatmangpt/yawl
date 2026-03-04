#!/bin/bash
# hyper-validate.sh - H (Guards) phase validation for HYPER_STANDARDS compliance
# Blocks TODO, mock, stub, empty, fallback, lie, silent patterns

set -e

echo "Running H (Guards) phase validation..."
echo "========================================"

VIOLATIONS=0
CHECKED_FILES=0

# Find all Java files newer than .git/index to check only new/modified files
if [ -f .git/index ]; then
    FILES=$(find src test yawl-ggen -name "*.java" -newer .git/index 2>/dev/null | head -50)
else
    FILES=$(find src test yawl-ggen -name "*.java" 2>/dev/null | head -50)
fi

if [ -z "$FILES" ]; then
    echo "No Java files found to validate"
    exit 0
fi

echo "Checking files:"
for f in $FILES; do
    echo "  - $f"
done
echo

# Check each file for guard violations
for f in $FILES; do
    if [ -f "$f" ]; then
        CHECKED_FILES=$((CHECKED_FILES + 1))
        
        # H_TODO: Deferred work markers
        if grep -qE "\/\/\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)" "$f"; then
            echo "❌ H_TODO violation in $f"
            grep -nE "\/\/\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)" "$f"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
        
        # H_MOCK: Mock method/class names
        # H_MOCK: Mock method/class names (not in comments)
        if grep -vE "^s*//" "$f" | grep -qE "publics+.*(Mock|Stub|Fake|Demo)[A-Za-z]" || 
           grep -vE "^s*//" "$f" | grep -qE "(mock|stub|fake|demo)[A-Za-z]s*[:=]"; then
            echo "❌ H_MOCK violation in $f"
            grep -nE "publics+.*(Mock|Stub|Fake|Demo)[A-Za-z]|(mock|stub|fake|demo)[A-Za-z]s*[:=]" "$f"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
        
        # H_STUB: Empty/placeholder returns
        if grep -qE "return\s+\"\";\|return\s+0;\|return\s+null;" "$f"; then
            # More specific check for stub returns with context
            if grep -qE "return\s+\"\";.*\/\/.*stub\|return\s+null;.*\/\/.*stub\|return\s+0;.*\/\/.*stub" "$f"; then
                echo "❌ H_STUB violation in $f"
                grep -nE "return\s+\"\";.*\/\/.*stub\|return\s+null;.*\/\/.*stub\|return\s+0;.*\/\/.*stub" "$f"
                VIOLATIONS=$((VIOLATIONS + 1))
            fi
        fi
        
        # H_EMPTY: No-op method bodies (void methods with empty braces)
        if grep -qE "void\s+\w+\s*\([^)]*\)\s*\{\s*\}" "$f"; then
            echo "❌ H_EMPTY violation in $f"
            grep -nE "void\s+\w+\s*\([^)]*\)\s*\{\s*\}" "$f"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
        
        # H_FALLBACK: Silent degradation catch blocks
        if grep -qE "catch\s*\(.*\)\s*\{.*return.*fake.*\}" "$f"; then
            echo "❌ H_FALLBACK violation in $f"
            grep -nE "catch\s*\(.*\)\s*\{.*return.*fake.*\}" "$f"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
        
        # H_SILENT: Log instead of throw
        if grep -qE "log\.(warn|error).*\"[^\"]*not\s+implemented[^\"]*\"" "$f"; then
            echo "❌ H_SILENT violation in $f"
            grep -nE "log\.(warn|error).*\"[^\"]*not\s+implemented[^\"]*\"" "$f"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
        
        # H_LIE: Code doesn't match documentation (basic check)
        if grep -qE "\/\*\*.*@return.*never.*null.*\*\/.*return\s+null" "$f"; then
            echo "❌ H_LIE violation in $f"
            grep -nE "\/\*\*.*@return.*never.*null.*\*\/.*return\s+null" "$f"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
    fi
done

echo
echo "========================================"
echo "H (Guards) Phase Results:"
echo "Files checked: $CHECKED_FILES"
echo "Violations found: $VIOLATIONS"

if [ $VIOLATIONS -gt 0 ]; then
    echo
    echo "❌ FAIL: $VIOLATIONS guard violations detected"
    echo
    echo "Correct patterns instead of violations:"
    echo "  • Instead of TODO: throw new UnsupportedOperationException()"
    echo "  • Instead of mock: Delete mock classes or implement real services"
    echo "  • Instead of empty: Implement real logic or throw exception"
    echo "  • Instead of fallback: Propagate exceptions instead of faking data"
    echo "  • Instead of silent: Throw exception instead of logging"
    echo
    echo "Fix violations and re-run: ./scripts/hyper-validate.sh"
    exit 2
else
    echo
    echo "✅ PASS: No guard violations found"
    exit 0
fi
