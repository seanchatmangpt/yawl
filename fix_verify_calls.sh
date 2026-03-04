#!/bin/bash

# Script to fix malformed verify call replacements in SLOAlertManagerTest.java

FILE="/Users/sac/yawl/test/org/yawlfoundation/yawl/observability/SLOAlertManagerTest.java"

# Fix malformed lines - replace method calls with proper assertions
sed -i '' 's/testAndonCord\.getAlertCount(AndonCord\.Severity\.P1_HIGH)(anyString(), any())/testAndonCord.getAlertCount(AndonCord.Severity.P1_HIGH)/g' "$FILE"
sed -i '' 's/testAndonCord\.getAlertCount(AndonCord\.Severity\.P1_HIGH)(eq.*, any())/testAndonCord.getAlertCount(AndonCord.Severity.P1_HIGH)/g' "$FILE"

echo "Fixed malformed verify call replacements in $FILE"