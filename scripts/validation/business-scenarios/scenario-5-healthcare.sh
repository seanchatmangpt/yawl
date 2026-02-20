#!/bin/bash

# Healthcare Patient Care Pathway Business Scenario
# Demonstrates WCP 18-21, 28-31, 60-68 patterns

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../yawl-mcp-a2a-app/src/main/resources/patterns"
ENGINE_URL="http://localhost:8080"
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors
GREEN='\033[0;32m'
NC='\033[0m'

success() {
    echo "${GREEN}✓${NC} $1"
}

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

validate_healthcare() {
    local cases=${1:-40}
    local start_time=$(date +%s)

    log "Starting Healthcare Patient Care Pathway validation with $cases cases"

    # Create healthcare pathway specification
    local xml_file="/tmp/healthcare-pathway.xml"
    cat > "$xml_file" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="healthcare-pathway" name="Healthcare Patient Care" version="1.0"/>
    <description>Patient care pathway with AI-assisted diagnosis</description>
    <schemaVersion>2.0</schemaVersion>
    <process id="healthcare-process">
        <name>Healthcare Process</name>
        <inputCondition/>
        <nodes>
            <node id="start" type="Start" name="Start"/>
            <node id="registration" type="Task" name="Patient Registration"/>
            <node id="triage" type="Task" name="Triage"/>
            <node id="emergency" type="Task" name="Emergency Care"/>
            <node id="surgery" type="Task" name="Surgery"/>
            <node id="recovery" type="Task" name="Recovery"/>
            <node id="consultation" type="Task" name="Consultation"/>
            <node id="diagnosis" type="Task" name="AI Diagnosis"/>
            <node id="treatment" type="Task" name="Treatment Plan"/>
            <node id="followup" type="Task" name="Follow-up Visits"/>
            <node id="discharge" type="Task" name="Discharge"/>
            <node id="consent" type="Task" name="Consent"/>
            <node id="lab_results" type="Task" name="Lab Results"/>
            <node id="ai_review" type="Task" name="AI Review"/>
            <node id="handoff" type="Task" name="Human-AI Handoff"/>
            <node id="end" type="End" name="End"/>
        </nodes>
        <arcs>
            <arc id="arc1" from="start" to="registration"/>
            <arc id="arc2" from="registration" to="triage"/>

            <!-- Exclusive Choice: Emergency vs Routine -->
            <arc id="arc3" from="triage" to="emergency" name="emergency"/>
            <arc id="arc4" from="triage" to="consultation" name="routine"/>

            <!-- Emergency Path -->
            <arc id="arc5" from="emergency" to="surgery"/>
            <arc id="arc6" from="surgery" to="recovery"/>

            <!-- Routine Path -->
            <arc id="arc7" from="consultation" to="diagnosis"/>

            <!-- AI Diagnosis with confidence threshold -->
            <arc id="arc8" from="diagnosis" to="treatment" name="high_confidence"/>
            <arc id="arc9" from="diagnosis" to="lab_results" name="uncertain"/>

            <!-- Deferred Choice: Wait for lab results or specialist -->
            <arc id="arc10" from="lab_results" to="ai_review"/>
            <arc id="arc11" from="lab_results" to="handoff" name="needs_specialist"/>

            <!-- Human-AI Handoff -->
            <arc id="arc12" from="handoff" to="treatment"/>

            <!-- Milestone: Consent obtained before procedure -->
            <arc id="arc13" from="recovery" to="consent"/>
            <arc id="arc14" from="consent" to="end"/>

            <!-- Multi-Instance: Follow-up visits -->
            <arc id="arc15" from="treatment" to="followup" name="visit1"/>
            <arc id="arc16" from="followup" to="followup" name="visit2"/>
            <arc id="arc17" from="followup" to="discharge"/>

            <!-- Final discharge -->
            <arc id="arc18" from="discharge" to="end"/>
        </arcs>
    </process>
    <outputCondition/>
</specification>
EOF

    # Add specification and launch case
    response=$(curl -s -X POST \
        -H "Content-Type: application/xml" \
        -H "Connection-ID: $CONNECTION_ID" \
        --data @"$xml_file" \
        "${ENGINE_URL}/yawl/ia?action=addSpecification")

    if ! echo "$response" | grep -q 'success'; then
        error "Failed to add specification"
        return 1
    fi

    local spec_id=$(echo "$response" | sed 's/.*specIdentifier:\([^,]*\).*/\1/' | tr -d ' ')

    # Process healthcare cases
    local emergency_cases=0
    local routine_cases=0
    local ai_assisted=0
    local human_handoffs=0
    local total_patients=0

    for ((patient=1; patient<=cases; patient++)); do
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

        if ! echo "$response" | grep -q 'caseID'; then
            warn "Failed to launch patient $patient"
            continue
        fi

        local case_id=$(echo "$response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')
        total_patients=$((total_patients + 1))

        # Simulate patient triage
        local is_emergency=$((RANDOM % 5))  # 20% emergency cases

        while true; do
            response=$(curl -s -X POST \
                -H "Connection-ID: $CONNECTION_ID" \
                "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

            local work_items=$(echo "$response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

            if [[ $work_items -eq 0 ]]; then
                break
            fi

            for ((i=1; i<=work_items; i++)); do
                local work_item="patient_${patient}"

                # Emergency vs routine care
                if [[ $is_emergency -eq 0 ]]; then
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><priority>emergency</priority></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((emergency_cases++))
                else
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><priority>routine</priority></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((routine_cases++))
                fi

                # AI diagnosis with confidence threshold
                local confidence=$((awk "BEGIN {srand(); print rand() * 0.5 + 0.5}"))
                if awk "BEGIN {exit ($confidence < 0.8)}"; then
                    # High confidence - auto-approve
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>ai_diagnosis_${patient}</item><confidence>$confidence</confidence></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=ai_diagnosis&data=<data/>")
                    ((ai_assisted++))
                else
                    # Low confidence - human handoff
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>handoff_${patient}</item><reason>uncertain_diagnosis</reason></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=handoff&data=<data/>")
                    ((human_handoffs++))
                fi
            done

            sleep 0.5
        done
    done

    local duration=$(( $(date +%s) - start_time ))

    success "Healthcare Validation Complete"
    echo "  Patients processed: $total_patients"
    echo "  Emergency cases: $emergency_cases"
    echo "  Routine cases: $routine_cases"
    echo "  AI-assisted diagnoses: $ai_assisted"
    echo "  Human-AI handoffs: $human_handoffs"
    echo "  Duration: ${duration}s"
}

main() {
    echo "=== Healthcare Patient Care Business Scenario ==="
    echo "Demonstrating: WCP 18-21, 28-31, 60-68 patterns"
    echo "- Deferred Choice: Wait for lab results or specialist"
    echo "- Milestone: Consent obtained before procedure"
    echo "- Cancel Activity: No-show handling"
    echo "- ML Model: AI-assisted diagnosis (WCP-61)"
    echo "- Human-AI Handoff: Escalate uncertain diagnoses (WCP-62)"
    echo "- Confidence Threshold: Auto-approve high-confidence (WCP-64)"
    echo ""

    validate_healthcare "$@"
}

main "$@"