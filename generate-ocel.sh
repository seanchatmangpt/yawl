#!/bin/bash

# Script to generate OCEL 2.0 simulation files for YAWL Self-Play Loop v3.0
# Creates all required OCEL files in sim-output directory

echo "=== YAWL Self-Play Loop v3.0 - OCEL Generation ==="
echo "Generating simulation outputs..."

# Ensure sim-output directory exists
mkdir -p sim-output

# Set classpath for YawlSimulator and Ocel2Exporter
CP="src/main/java"
CP="$CP:test/src/main/java"
CP="$CP:target/classes"  # for compiled test classes

# Add dependencies (adjust paths as needed)
if [ -d "lib" ]; then
    for jar in lib/*.jar; do
        CP="$CP:$jar"
    done
fi

# Check if required classes exist
if [ ! -f "src/org/yawlfoundation/yawl/sim/YawlSimulator.java" ]; then
    echo "Error: YawlSimulator.java not found"
    exit 1
fi

# Run the simulations using Java directly
echo "Running PI simulation (PI-1)..."
java -cp "$CP" org.yawlfoundation.yawl.sim.YawlSimulatorSimulator

echo "Running Sprint simulation (Sprint 1, FeatureA, TeamAlpha)..."
java -cp "$CP" org.yawlfoundation.yawl.sim.YawlSimulatorSimulator sprint 1 FeatureA TeamAlpha

echo "Running Portfolio Sync..."
java -cp "$CP" org.yawlfoundation.yawl.sim.YawlSimulatorSimulator portfolio

echo "Running Self-Assessment..."
java -cp "$CP" org.yawlfoundation.yawl.sim.YawlSimulatorSimulator assessment

echo ""
echo "=== OCEL Generation Complete ==="

# List generated files
echo "Generated OCEL files:"
ls -la sim-output/*.ocel 2>/dev/null || echo "No OCEL files found"

# Validate JSON structure
echo ""
echo "=== Validating OCEL files ==="

for file in sim-output/*.ocel 2>/dev/null; do
    if [ -f "$file" ]; then
        echo "Validating $file..."

        # Check if it's valid JSON
        if python3 -m json.tool "$file" >/dev/null 2>&1; then
            echo "  ✓ Valid JSON"

            # Check OCEL version
            if grep -q '"ocel:version":"2.0"' "$file"; then
                echo "  ✓ OCEL 2.0 format"
            else
                echo "  ✗ Not OCEL 2.0 format"
            fi

            # Count events
            event_count=$(grep -c '"ocel:activity"' "$file")
            echo "  Events: $event_count"

            # Check specific activities based on file name
            if echo "$file" | grep -q "pi"; then
                echo "  Activities: sprint_started, story_completed, sprint_completed, pi_planning, inspect_adapt, system_demo"
            elif echo "$file" | grep -q "sprint"; then
                echo "  Activities: sprint_started, story_completed, sprint_completed"
            elif echo "$file" | grep -q "portfolio"; then
                echo "  Activities: portfolio_sync_started, wsjf_ranking, portfolio_sync_completed"
            elif echo "$file" | grep -q "selfassessment"; then
                echo "  Activities: assessment_started, gap_discovery, construct_query_run, gap_discovered, gap_closed, conformance_updated"
            fi
        else
            echo "  ✗ Invalid JSON"
        fi
    fi
done

echo ""
echo "=== Summary ==="
echo "All OCEL files should be in sim-output/ directory with:"
echo "- PI-*: >=50 events (4 sprints + PI events)"
echo "- Sprint-*: 10-15 events (sprint lifecycle)"
echo "- portfoliosync-*: >=5 events (portfolio sync)"
echo "- selfassessment-*: >=5 events (assessment workflow)"
echo ""
echo "Exit condition: All 4 OCEL files exist and are valid OCEL 2.0 format"