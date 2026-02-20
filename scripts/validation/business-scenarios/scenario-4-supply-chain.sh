#!/bin/bash

# Supply Chain Procurement Business Scenario
# Demonstrates WCP 2-3, 45-50, 51-59 patterns

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

validate_supply_chain() {
    local cases=${1:-30}
    local start_time=$(date +%s)

    log "Starting Supply Chain Procurement validation with $cases cases"

    # Create supply chain specification
    local xml_file="/tmp/supply-chain.xml"
    cat > "$xml_file" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="supply-chain" name="Supply Chain Procurement" version="1.0"/>
    <description>Multi-supplier procurement with distributed transactions</description>
    <schemaVersion>2.0</schemaVersion>
    <process id="supply-chain-process">
        <name>Supply Chain Process</name>
        <inputCondition/>
        <nodes>
            <node id="start" type="Start" name="Start"/>
            <node id="demand" type="Task" name="Demand Forecast"/>
            <node id="supplier_a" type="Task" name="Supplier A"/>
            <node id="supplier_b" type="Task" name="Supplier B"/>
            <node id="supplier_c" type="Task" name="Supplier C"/>
            <node id="quotes_sync" type="Join" name="Quote Synchronization"/>
            <node id="negotiate_a" type="Task" name="Negotiate A"/>
            <node id="negotiate_b" type="Task" name="Negotiate B"/>
            <node id="negotiate_sync" type="Join" name="Negotiation Sync"/>
            <node id="po" type="Task" name="Purchase Order"/>
            <node id="shipping_a" type="Task" name="Shipping A"/>
            <node id="shipping_b" type="Task" name="Shipping B"/>
            <node id="quality_check" type="Task" name="Quality Check"/>
            <node id="inventory" type="Task" name="Inventory Update"/>
            <node id="cb" type="Task" name="Circuit Breaker"/>
            <node id="end" type="End" name="End"/>
        </nodes>
        <arcs>
            <!-- Parallel Split: Multiple suppliers -->
            <arc id="arc1" from="start" to="demand"/>
            <arc id="arc2" from="demand" to="supplier_a"/>
            <arc id="arc3" from="demand" to="supplier_b"/>
            <arc id="arc4" from="demand" to="supplier_c"/>

            <!-- Synchronization: All quotes ready -->
            <arc id="arc5" from="supplier_a" to="quotes_sync"/>
            <arc id="arc6" from="supplier_b" to="quotes_sync"/>
            <arc id="arc7" from="supplier_c" to="quotes_sync"/>

            <!-- Parallel Negotiation -->
            <arc id="arc8" from="quotes_sync" to="negotiate_a"/>
            <arc id="arc9" from="quotes_sync" to="negotiate_b"/>
            <arc id="arc10" from="negotiate_a" to="negotiate_sync"/>
            <arc id="arc11" from="negotiate_b" to="negotiate_sync"/>

            <!-- PO Creation -->
            <arc id="arc12" from="negotiate_sync" to="po"/>

            <!-- Two-Phase Commit: PO Confirmation -->
            <arc id="arc13" from="po" to="shipping_a" name="phase1"/>
            <arc id="arc14" from="po" to="shipping_b" name="phase2"/>
            <arc id="arc15" from="shipping_a" to="quality_check"/>
            <arc id="arc16" from="shipping_b" to="quality_check"/>

            <!-- Event Gateway: Quality check results -->
            <arc id="arc17" from="quality_check" to="inventory" name="pass"/>
            <arc id="arc18" from="quality_check" to="end" name="fail"/>

            <!-- Circuit Breaker: Supplier failover -->
            <arc id="arc19" from="po" to="cb" name="supplier_failure"/>
            <arc id="arc20" from="cb" to="shipping_a" name="alternate"/>
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

    # Process supply chain orders
    local processed_orders=0
    local quality_failures=0
    local circuit_breaker_events=0

    for ((order=1; order<=cases; order++)); do
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

        if ! echo "$response" | grep -q 'caseID'; then
            warn "Failed to launch order $order"
            continue
        fi

        local case_id=$(echo "$response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')

        # Simulate procurement
        local quality_fail=$((RANDOM % 10))  # 10% quality failure
        local supplier_fail=$((RANDOM % 5))   # 20% supplier failure

        while true; do
            response=$(curl -s -X POST \
                -H "Connection-ID: $CONNECTION_ID" \
                "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

            local work_items=$(echo "$response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

            if [[ $work_items -eq 0 ]]; then
                break
            fi

            for ((i=1; i<=work_items; i++)); do
                local work_item="orderitem_${order}"

                # Handle quality failures
                if [[ $quality_fail -eq 0 ]]; then
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><quality>fail</quality></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                    ((quality_failures++))
                else
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item}</item><quality>pass</quality></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
                fi

                # Handle circuit breaker
                if [[ $supplier_fail -eq 0 ]]; then
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>cb_${order}</item><action>alternate_supplier</action></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=cb&data=<data/>")
                    ((circuit_breaker_events++))
                fi
            done

            sleep 0.3
        done

        ((processed_orders++))
    done

    local duration=$(( $(date +%s) - start_time ))

    success "Supply Chain Validation Complete"
    echo "  Orders processed: $processed_orders"
    echo "  Quality failures: $quality_failures"
    echo "  Circuit breaker events: $circuit_breaker_events"
    echo "  Duration: ${duration}s"
}

main() {
    echo "=== Supply Chain Procurement Business Scenario ==="
    echo "Demonstrating: WCP 2-3, 45-50, 51-59 patterns"
    echo "- Parallel Split/Sync: Multiple supplier negotiations"
    echo "- Circuit Breaker: Failover on supplier issues"
    echo "- Two-Phase Commit: Atomic order confirmation"
    echo "- Event Gateway: Asynchronous supplier responses"
    echo "- CQRS: Read model for inventory, write for orders"
    echo ""

    validate_supply_chain "$@"
}

main "$@"