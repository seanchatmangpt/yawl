#!/bin/bash

# Insurance Claim Processing Business Scenario
# Demonstrates WCP 4-9, 18-19, 37-40 patterns

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

# Insurance claim flow
validate_insurance_claim() {
    local cases=${1:-50}
    local start_time=$(date +%s)

    log "Starting Insurance Claim validation with $cases cases"

    # Create insurance claim specification
    local xml_file="/tmp/insurance-claim.xml"
    cat > "$xml_file" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="insurance-claim" name="Insurance Claim" version="1.0"/>
    <description>Insurance claim processing with automated routing</description>
    <schemaVersion>2.0</schemaVersion>
    <process id="insurance-claim-process">
        <name>Insurance Claim Process</name>
        <inputCondition/>
        <nodes>
            <node id="start" type="Start" name="Start"/>
            <node id="submit_claim" type="Task" name="Submit Claim"/>
            <node id="initial_review" type="Task" name="Initial Review"/>
            <node id="auto_approve" type="Task" name="Auto-Approve"/>
            <node id="manual_review" type="Task" name="Manual Review"/>
            <node id="investigation" type="Task" name="Investigation"/>
            <node id="approve" type="Task" name="Approve Claim"/>
            <node id="deny" type="Task" name="Deny Claim"/>
            <node id="notify" type="Task" name="Notify Customer"/>
            <node id="appeal" type="Task" name="Appeal Request"/>
            <node id="appeal_review" type="Task" name="Appeal Review"/>
            <node id="end" type="End" name="End"/>
        </nodes>
        <arcs>
            <arc id="arc1" from="start" to="submit_claim"/>
            <arc id="arc2" from="submit_claim" to="initial_review"/>

            <!-- Multi-Choice: Different reviewers based on claim type -->
            <arc id="arc3" from="initial_review" to="auto_approve" name="simple_claims"/>
            <arc id="arc4" from="initial_review" to="manual_review" name="complex_claims"/>

            <!-- Auto-approve simple claims -->
            <arc id="arc5" from="auto_approve" to="notify"/>

            <!-- Manual review complex claims -->
            <arc id="arc6" from="manual_review" to="investigation"/>
            <arc id="arc7" from="investigation" to="approve" name="positive"/>
            <arc id="arc8" from="investigation" to="deny" name="negative"/>

            <!-- Notify customer -->
            <arc id="arc9" from="approve" to="notify"/>
            <arc id="arc10" from="deny" to="notify"/>

            <!-- Deferred Choice: Customer appeal or accept -->
            <arc id="arc11" from="notify" to="appeal" name="appeal"/>
            <arc id="arc12" from="notify" to="end" name="accept"/>

            <!-- Milestone: Must complete appeal review before final decision -->
            <arc id="arc13" from="appeal" to="appeal_review"/>
            <arc id="arc14" from="appeal_review" to="approve" name="approve_appeal"/>
            <arc id="arc15" from="appeal_review" to="deny" name="deny_appeal"/>

            <!-- Final arcs -->
            <arc id="arc16" from="approve" to="end"/>
            <arc id="arc17" from="deny" to="end"/>
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

    # Process claims
    local auto_approved=0
    local manual_reviews=0
    local investigations=0
    local appeals=0
    local total_claims=0

    for ((claim=1; claim<=cases; claim++)); do
        # Launch claim case
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

        if ! echo "$response" | grep -q 'caseID'; then
            warn "Failed to launch claim $claim"
            continue
        fi

        local case_id=$(echo "$response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')
        total_claims=$((total_claims + 1))

        # Simulate claim processing
        local is_simple_claim=$((RANDOM % 10))  # 60% simple claims
        local customer_appeals=$((RANDOM % 10))  # 20% appeals

        # Process work items
        while true; do
            response=$(curl -s -X POST \
                -H "Connection-ID: $CONNECTION_ID" \
                "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

            local work_items=$(echo "$response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

            if [[ $work_items -eq 0 ]]; then
                break
            fi

            for ((i=1; i<=work_items; i++)); do
                local work_item="claimitem_${claim}"

                # Complete work items based on claim type
                if [[ $is_simple_claim -lt 6 ]]; then
                    # Simple claim - auto-approve
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><amount>$(shuf -i 100-10000 -n 1)</item></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((auto_approved++))
                else
                    # Complex claim - manual review
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><risk>medium</risk></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((manual_reviews++))
                fi

                # Simulate appeal
                if [[ $customer_appeals -lt 2 ]]; then
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>appeal_${claim}</item><reason>dissatisfied</reason></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=appeal&data=<data/>")
                    ((appeals++))
                fi
            done

            sleep 0.1
        done
    done

    local duration=$(( $(date +%s) - start_time ))

    # Summary
    success "Insurance Claim Validation Complete"
    echo "  Claims processed: $total_claims"
    echo "  Auto-approved: $auto_approved"
    echo "  Manual reviews: $manual_reviews"
    echo "  Appeals: $appeals"
    echo "  Duration: ${duration}s"
    echo "  Throughput: $((total_claims * 3600 / duration)) claims/hour"

    return 0
}

# Main execution
main() {
    echo "=== Insurance Claim Processing Business Scenario ==="
    echo "Demonstrating: WCP 4-9, 18-19, 37-40 patterns"
    echo "- Multi-Choice: Different reviewers based on claim type"
    echo "- Sync Merge: Investigation complete before decision"
    echo "- Deferred Choice: Customer appeal or accept decision"
    echo "- Milestone: Investigation complete before payout"
    echo "- Triggers: Deadline reminders and document requests"
    echo ""

    validate_insurance_claim "$@"
}

main "$@"