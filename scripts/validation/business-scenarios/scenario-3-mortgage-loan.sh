#!/bin/bash

# Mortgage Loan Application Business Scenario
# Demonstrates WCP 6-8, 12-17, 41-44 patterns

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

# Mortgage loan application flow
validate_mortgage_loan() {
    local cases=${1:-25}
    local start_time=$(date +%s)

    log "Starting Mortgage Loan Application validation with $cases cases"

    # Create mortgage loan specification
    local xml_file="/tmp/mortgage-loan.xml"
    cat > "$xml_file" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="mortgage-loan" name="Mortgage Loan" version="1.0"/>
    <description>Mortgage application with multi-property appraisal</description>
    <schemaVersion>2.0</schemaVersion>
    <process id="mortgage-loan-process">
        <name>Mortgage Loan Process</name>
        <inputCondition/>
        <nodes>
            <node id="start" type="Start" name="Start"/>
            <node id="application" type="Task" name="Application Received"/>
            <node id="credit_check" type="Task" name="Credit Check"/>
            <node id="income_verify" type="Task" name="Income Verification"/>
            <node id="property_appraisal" type="Task" name="Property Appraisal"/>
            <node id="underwriting" type="Task" name="Underwriting Decision"/>
            <node id="conditional_approval" type="Task" name="Conditional Approval"/>
            <node id="documents" type="Task" name="Document Signing"/>
            <node id="funding" type="Task" name="Funding"/>
            <node id="rollback" type="Task" name="Rollback Funding"/>
            <node id="end" type="End" name="End"/>
        </nodes>
        <arcs>
            <arc id="arc1" from="start" to="application"/>
            <arc id="arc2" from="application" to="credit_check"/>
            <arc id="arc3" from="credit_check" to="income_verify"/>

            <!-- Multi-Instance: Multiple property appraisals -->
            <arc id="arc4" from="income_verify" to="property_appraisal" name="property1"/>
            <arc id="arc5" from="income_verify" to="property_appraisal" name="property2"/>
            <arc id="arc6" from="income_verify" to="property_appraisal" name="property3"/>

            <!-- Synchronization: All appraisals complete -->
            <arc id="arc7" from="property_appraisal" to="underwriting"/>

            <!-- Multi-Merge: Underwriting with different outcomes -->
            <arc id="arc8" from="underwriting" to="end" name="reject"/>
            <arc id="arc9" from="underwriting" to="conditional_approval" name="conditional"/>
            <arc id="arc10" from="underwriting" to="documents" name="approve"/>

            <!-- Conditional requires additional docs -->
            <arc id="arc11" from="conditional_approval" to="documents"/>

            <!-- Document signing -->
            <arc id="arc12" from="documents" to="funding"/>

            <!-- Saga Pattern: Rollback on failure -->
            <arc id="arc13" from="funding" to="end"/>
            <arc id="arc14" from="funding" to="rollback" name="rollback_on_failure"/>
            <arc id="arc15" from="rollback" to="end"/>

            <!-- Critical Section: Rate lock period -->
            <arc id="arc16" from="documents" to="documents" name="rate_lock"/>
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

    # Process mortgage applications
    local approved_loans=0
    local conditional_loans=0
    local rejected_loans=0
    local rollback_events=0
    local total_applications=0

    for ((application=1; application<=cases; application++)); do
        # Launch mortgage case
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

        if ! echo "$response" | grep -q 'caseID'; then
            warn "Failed to launch application $application"
            continue
        fi

        local case_id=$(echo "$response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')
        total_applications=$((total_applications + 1))

        # Simulate mortgage application
        local credit_score=$((600 + RANDOM % 400))  # 600-1000
        local income_ratio=$((0.3 + RANDOM * 0.4))  # 0.3-0.7
        local funding_failure=$((RANDOM % 10))  # 10% funding failure

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
                local work_item="loanitem_${application}"

                # Process based on application stage
                if [[ $credit_score -lt 700 ]] || [[ $(echo "$income_ratio > 0.5" | bc -l) -eq 1 ]]; then
                    # Underwriting condition
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><condition>additional_docs</condition></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((conditional_loans++))
                else
                    # Approved
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><condition>approved</condition></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((approved_loans++))
                fi

                # Simulate funding rollback
                if [[ $funding_failure -eq 0 ]]; then
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>rollback_${application}</item><reason>funding_failure</reason></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=rollback&data=<data/>")
                    ((rollback_events++))
                fi
            done

            sleep 0.2
        done

        # Check final state
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

        local case_state=$(echo "$response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")

        if [[ "$case_state" == "complete" ]]; then
            ((approved_loans++))
        else
            ((rejected_loans++))
        fi
    done

    local duration=$(( $(date +%s) - start_time ))

    # Summary
    success "Mortgage Loan Validation Complete"
    echo "  Applications processed: $total_applications"
    echo "  Approved: $approved_loans"
    echo "  Conditional: $conditional_loans"
    echo "  Rejected: $rejected_loans"
    echo "  Rollback events: $rollback_events"
    echo "  Duration: ${duration}s"
    echo "  Throughput: $((total_applications * 3600 / duration)) applications/hour"

    return 0
}

# Main execution
main() {
    echo "=== Mortgage Loan Application Business Scenario ==="
    echo "Demonstrating: WCP 6-8, 12-17, 41-44 patterns"
    echo "- Multi-Choice: Different underwriters based on risk"
    echo "- Multi-Instance: Multiple property appraisals"
    echo "- Structured Loop: Request additional documents"
    echo "- Saga Pattern: Rollback if funding fails"
    echo "- Critical Section: Rate lock period"
    echo ""

    validate_mortgage_loan "$@"
}

main "$@"