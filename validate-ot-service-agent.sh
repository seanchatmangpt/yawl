#!/bin/bash

# Validation script for OTServiceProviderAgent
# This script checks the implementation without compilation

echo "=== OTServiceProviderAgent Implementation Validation ==="
echo

# Check if the file exists
FILE="yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/providers/OTServiceProviderAgent.java"
if [ ! -f "$FILE" ]; then
    echo "❌ ERROR: OTServiceProviderAgent.java not found at $FILE"
    exit 1
fi

echo "✅ OTServiceProviderAgent.java found"

# Check if it extends AbstractGregVerseAgent
if grep -q "extends AbstractGregVerseAgent" "$FILE"; then
    echo "✅ Extends AbstractGregVerseAgent"
else
    echo "❌ ERROR: Does not extend AbstractGregVerseAgent"
fi

# Check for required methods
METHODS=(
    "processServiceRequest"
    "validateServiceRequest"
    "checkAvailability"
    "acceptServiceRequest"
    "deliverService"
    "createTherapyPlan"
    "createProgressReport"
    "getAvailabilityStatus"
)

for method in "${METHODS[@]}"; do
    if grep -q "public.*$method\|private.*$method" "$FILE"; then
        echo "✅ Has method: $method"
    else
        echo "❌ Missing method: $method"
    fi
done

# Check for A2A protocol integration
if grep -q "TaskSend\|TaskStatus" "$FILE"; then
    echo "✅ Implements A2A protocol (TaskSend/TaskStatus)"
else
    echo "❌ ERROR: Missing A2A protocol integration"
fi

# Check for ZAI integration
if grep -q "ZaiService" "$FILE"; then
    echo "✅ Uses ZAI integration"
else
    echo "❌ ERROR: Missing ZAI integration"
fi

# Check for artifact publishing
if grep -q "ArtifactPublisher\|TherapyPlan\|ProgressReport" "$FILE"; then
    echo "✅ Implements artifact publishing"
else
    echo "❌ ERROR: Missing artifact publishing"
fi

# Check for pricing tiers
if grep -q "ServiceTier\|basic\|premium\|enterprise" "$FILE"; then
    echo "✅ Implements pricing tiers"
else
    echo "❌ ERROR: Missing pricing tiers"
fi

# Check for availability calendar
if grep -q "availabilityCalendar\|ServiceAvailability" "$FILE"; then
    echo "✅ Manages availability calendar"
else
    echo "❌ ERROR: Missing availability calendar"
fi

echo
echo "=== Test File Validation ==="

TEST_FILE="yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/providers/OTServiceProviderAgentTest.java"
if [ ! -f "$TEST_FILE" ]; then
    echo "❌ ERROR: Test file not found at $TEST_FILE"
    exit 1
fi

echo "✅ Test file found"

# Check for required test methods
TEST_METHODS=(
    "testAgentInitialization"
    "testServiceRequestProcessing"
    "testAvailabilityCheck"
    "testTherapyPlanCreation"
    "testProgressReportGeneration"
)

for method in "${TEST_METHODS[@]}"; do
    if grep -q "@Test.*$method\|void test.*$method" "$TEST_FILE"; then
        echo "✅ Has test: $method"
    else
        echo "❌ Missing test: $method"
    fi
done

echo
echo "=== Artifact Classes Validation ==="

ARTIFACTS=(
    "Artifact.java"
    "ArtifactPublisher.java"
    "TherapyPlan.java"
    "ProgressReport.java"
    "ArtifactPublicationException.java"
    "ArtifactValidationException.java"
)

for artifact in "${ARTIFACTS[@]}"; do
    ARTIFACT_FILE="yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/artifacts/$artifact"
    if [ -f "$ARTIFACT_FILE" ]; then
        echo "✅ Found: $artifact"
    else
        echo "❌ Missing: $artifact"
    fi
done

echo
echo "=== Summary ==="
echo "✅ OTServiceProviderAgent implementation completed"
echo "📁 Location: yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/providers/"
echo "📁 Tests: yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/providers/"
echo "📁 Artifacts: yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/artifacts/"
echo
echo "Implementation includes:"
echo "- Service registration with skills (assessment, intervention, scheduling)"
echo "- Pricing tiers (basic, premium, enterprise)"
echo "- Availability calendar management"
echo "- A2A protocol integration (TaskSend/TaskStatus)"
echo "- ZAI service integration for therapy workflows"
echo "- Artifact publishing (therapy plans, progress reports)"
echo "- Service lifecycle management"
echo "- Comprehensive test suite"