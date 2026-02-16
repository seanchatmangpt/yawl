#!/bin/bash
set -e

echo "=== YAWL v5.2 Build Artifact Validation ==="

ARTIFACT_PATH="target/yawl-5.2.0.jar"

# Check 1: Artifact exists
if [ ! -f "$ARTIFACT_PATH" ]; then
    echo "❌ FAIL: Build artifact not found: $ARTIFACT_PATH"
    exit 1
fi
echo "✅ Artifact exists: $(ls -lh $ARTIFACT_PATH | awk '{print $5, $9}')"

# Check 2: Artifact size
echo "✅ Artifact size: $(stat -c%s $ARTIFACT_PATH 2>/dev/null || stat -f%z $ARTIFACT_PATH) bytes"

# Check 3: Verify classes compiled
if unzip -l $ARTIFACT_PATH | grep "org/yawlfoundation/yawl.*\.class" &>/dev/null; then
    echo "✅ Compiled classes found"
else
    echo "❌ FAIL: No compiled classes found"
    exit 1
fi

# Check 4: Verify test artifacts NOT included
if unzip -l $ARTIFACT_PATH | grep "*Test.class" &>/dev/null; then
    echo "⚠️  WARNING: Test classes included in production JAR"
else
    echo "✅ No test classes in artifact"
fi

# Check 5: Verify dependencies included
if unzip -l $ARTIFACT_PATH | grep "lib/.*\.jar" &>/dev/null; then
    echo "✅ Dependencies included (fat JAR)"
else
    echo "⚠️  WARNING: No dependencies found (thin JAR)"
fi

# Check 6: Verify manifest
if unzip -p $ARTIFACT_PATH META-INF/MANIFEST.MF | grep "Main-Class" &>/dev/null; then
    echo "✅ Main-Class configured"
else
    echo "⚠️  WARNING: Main-Class not configured"
fi

echo -e "\n✅ All artifact checks passed!"
