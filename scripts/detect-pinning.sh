#!/usr/bin/env bash
set -euo pipefail
# Virtual Thread Pinning Detection Script

echo "=== Virtual Thread Pinning Detection ==="
echo ""

REPORT_FILE="PINNING_DETECTION_REPORT.md"

# Initialize report
cat > "${REPORT_FILE}" <<'EOF'
# Virtual Thread Pinning Detection Report

Generated: $(date)
Session: https://claude.ai/code/session_01Jy5VPT7aRfKskDEcYVSXDq

EOF

# Count synchronized blocks
echo "1. Scanning for synchronized blocks..."
SYNC_BLOCKS=$(grep -r "synchronized\s*(" src/ --include="*.java" -n 2>/dev/null | wc -l)
echo "   Found: ${SYNC_BLOCKS} synchronized blocks"

# Count synchronized methods
echo "2. Scanning for synchronized methods..."
SYNC_METHODS=$(grep -rE "^\s*(public|private|protected)\s+synchronized" src/ --include="*.java" -n 2>/dev/null | wc -l)
echo "   Found: ${SYNC_METHODS} synchronized methods"

# Count wait/notify usage
echo "3. Scanning for wait/notify/notifyAll usage..."
WAIT_NOTIFY=$(grep -rE "\.wait\(\)|\.notify\(\)|\.notifyAll\(\)" src/ --include="*.java" -l 2>/dev/null | wc -l)
echo "   Found: ${WAIT_NOTIFY} files using wait/notify"

# Count virtual thread usage
echo "4. Scanning for virtual thread usage..."
VT_USAGE=$(grep -r "newVirtualThreadPerTaskExecutor" src/ --include="*.java" -l 2>/dev/null | wc -l)
echo "   Found: ${VT_USAGE} files using virtual threads"

# Append to report
cat >> "${REPORT_FILE}" <<EOF

## Summary Statistics

| Metric | Count | Status |
|--------|-------|--------|
| Synchronized blocks | ${SYNC_BLOCKS} | $([ ${SYNC_BLOCKS} -gt 0 ] && echo "⚠️ Potential pinning" || echo "✅ Good") |
| Synchronized methods | ${SYNC_METHODS} | $([ ${SYNC_METHODS} -gt 0 ] && echo "⚠️ Potential pinning" || echo "✅ Good") |
| wait/notify files | ${WAIT_NOTIFY} | $([ ${WAIT_NOTIFY} -gt 0 ] && echo "⚠️ Causes pinning" || echo "✅ Good") |
| Virtual thread services | ${VT_USAGE} | ✅ Good |

## Detailed Findings

EOF

# Check critical files
echo "5. Analyzing critical files..."

echo "### YEngine synchronized blocks" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
grep -n "synchronized\s*(" src/org/yawlfoundation/yawl/engine/YEngine.java 2>/dev/null | head -20 >> "${REPORT_FILE}" || echo "None found" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

echo "### YWorkItem synchronized blocks" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
grep -n "synchronized\s*(" src/org/yawlfoundation/yawl/engine/YWorkItem.java 2>/dev/null >> "${REPORT_FILE}" || echo "None found" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

echo "### YNetRunner synchronized blocks" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
grep -n "synchronized\s*(" src/org/yawlfoundation/yawl/engine/YNetRunner.java 2>/dev/null >> "${REPORT_FILE}" || echo "None found" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

# Files with wait/notify
echo "### Files using wait/notify (HIGH PRIORITY)" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
grep -rE "\.wait\(\)|\.notify\(\)|\.notifyAll\(\)" src/ --include="*.java" -l 2>/dev/null >> "${REPORT_FILE}" || echo "None found" >> "${REPORT_FILE}"
echo '```' >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

# Final recommendation
TOTAL_ISSUES=$((SYNC_BLOCKS + SYNC_METHODS + WAIT_NOTIFY))

cat >> "${REPORT_FILE}" <<EOF

## Overall Status

Total potential pinning issues: **${TOTAL_ISSUES}**

EOF

if [ ${TOTAL_ISSUES} -gt 50 ]; then
    echo "❌ **CRITICAL**: Many pinning issues detected - immediate action required" >> "${REPORT_FILE}"
    echo "❌ CRITICAL - ${TOTAL_ISSUES} potential pinning issues found"
    echo "   See ${REPORT_FILE} for details"
    exit 1
elif [ ${TOTAL_ISSUES} -gt 0 ]; then
    echo "⚠️ **WARNING**: Some pinning issues detected - review recommended" >> "${REPORT_FILE}"
    echo "⚠️ WARNING - ${TOTAL_ISSUES} potential pinning issues found"
    echo "   See ${REPORT_FILE} for details"
    exit 0
else
    echo "✅ **SUCCESS**: No pinning issues detected" >> "${REPORT_FILE}"
    echo "✅ SUCCESS - No pinning issues detected"
    exit 0
fi
