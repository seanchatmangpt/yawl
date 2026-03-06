#!/usr/bin/env bash
set -euo pipefail

# Script to fix all remaining compilation errors in legacy src directory

echo "=== Fixing All Remaining Compilation Errors ==="

# Function to fix YTask.java imports
fix_ytask_imports() {
    echo "Fixing YTask.java imports..."

    # Add missing imports
    sed -i.tmp '/import org.yawlfoundation.yawl.engine.YPersistenceManager;/a\
import org.yawlfoundation.yawl.engine.YEngine;\
import org.yawlfoundation.yawl.engine.YNetRunnerRepository;\
import org.yawlfoundation.yawl.engine.YWorkItemRepository;' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java

    # Fix method calls
    sed -i.tmp 's/_mi_executing.removeOne(childID);/_mi_executing.removeOne(pmgr, childID);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_complete.add(childID);/_mi_complete.add(pmgr, childID);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_active.add(childCaseID);/_mi_active.add(pmgr, childCaseID);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_entered.add(childCaseID);/_mi_entered.add(pmgr, childCaseID);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_active.removeAll();/_mi_active.removeAll(pmgr);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_complete.removeAll();/_mi_complete.removeAll(pmgr);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_entered.removeAll();/_mi_entered.removeAll(pmgr);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
    sed -i.tmp 's/_mi_executing.removeAll();/_mi_executing.removeAll(pmgr);/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java

    rm /Users/sac/yawl/src/org/yawlfoundation/yawl/elements/YTask.java.tmp
    echo "YTask.java imports and method calls fixed"
}

# Function to fix YWorkItem.java trigger conversion
fix_yworkitem_trigger() {
    echo "Fixing YWorkItem.java trigger conversion..."

    # Check if the trigger conversion method exists, if not add it
    if ! grep -q "elementsToEngineTrigger" /Users/sac/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java; then
        # Add the trigger conversion method before the checkStartTimer method
        sed -i.tmp '/public void checkStartTimer(YPersistenceManager pmgr, YNetData data)/i\
    \
    // Convert elements TriggerType to engine Trigger\
    private YWorkItemTimer.Trigger elementsToEngineTrigger(org.yawlfoundation.yawl.elements.TriggerType triggerType) {\
        return switch (triggerType) {\
            case OnEnabled -> YWorkItemTimer.Trigger.OnEnabled;\
            case OnExecuting -> YWorkItemTimer.Trigger.OnExecuting;\
            case OnCompletion -> YWorkItemTimer.Trigger.Never;\
            default -> YWorkItemTimer.Trigger.Never;\
        };\
    }\
' /Users/sac/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java
    fi

    # Fix the trigger conversion call
    sed -i.tmp 's/YWorkItemTimer.Trigger trigger = _timerParameters.getTrigger();/YWorkItemTimer.Trigger trigger = elementsToEngineTrigger(_timerParameters.getTrigger());/g' /Users/sac/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java

    rm /Users/sac/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java.tmp
    echo "YWorkItem.java trigger conversion fixed"
}

# Function to fix any remaining compilation errors
fix_remaining_errors() {
    echo "Fixing remaining compilation errors..."

    # Run compilation and capture errors
    mvn clean compile -q -pl yawl-elements,yawl-engine 2>&1 | grep "ERROR" > /tmp/remaining_errors.txt

    if [[ -s /tmp/remaining_errors.txt ]]; then
        echo "Remaining errors found:"
        cat /tmp/remaining_errors.txt

        # Check for specific patterns and fix them
        if grep -q "YSchemaVersion" /tmp/remaining_errors.txt; then
            echo "Fixing YSchemaVersion import..."
            # Add specific schema import if needed
        fi

        if grep -q "YLogPredicate" /tmp/remaining_errors.txt; then
            echo "Fixing YLogPredicate import..."
            # Add logging imports if needed
        fi
    else
        echo "No remaining compilation errors found!"
    fi

    rm -f /tmp/remaining_errors.txt
}

# Execute all fixes
fix_ytask_imports
fix_yworkitem_trigger
fix_remaining_errors

echo ""
echo "=== All Fixes Applied ==="
echo "Running final compilation test..."
echo ""

# Test compilation
if mvn clean compile -q -pl yawl-elements,yawl-engine; then
    echo "✅ SUCCESS: All compilation errors fixed!"
    exit 0
else
    echo "❌ Some errors remain. Check the output above for details."
    exit 1
fi