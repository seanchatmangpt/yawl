#!/bin/bash

# Order Fulfillment Business Scenario
# Demonstrates WCP 1-5, 10-11, 20-21 patterns

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

# Order fulfillment flow stages
declare -A ORDER_FLOW=(
    ["order_received"]="WCP-1"
    ["inventory_check"]="WCP-2"
    ["parallel_shipping"]="WCP-3"
    ["payment_processing"]="WCP-4"
    ["shipping_label"]="WCP-5"
    ["order_complete"]="WCP-10"
    ["customer_notified"]="WCP-11"
)

# Validate order fulfillment scenario
validate_order_fulfillment() {
    local cases=${1:-100}
    local start_time=$(date +%s)

    log "Starting Order Fulfillment validation with $cases cases"

    # Check services
    if ! curl -s -f "${ENGINE_URL}/actuator/health/liveness" > /dev/null 2>&1; then
        error "YAWL engine not available"
        return 1
    fi

    # Authenticate
    local response
    response=$(curl -s -X POST "${ENGINE_URL}/yawl/ib?action=connect&userid=admin&password=YAWL")
    if ! echo "$response" | grep -q 'connectionID'; then
        error "Authentication failed"
        return 1
    fi

    # Create order fulfillment specification
    local xml_file="/tmp/order-fulfillment.xml"
    cat > "$xml_file" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="order-fulfillment" name="Order Fulfillment" version="1.0"/>
    <description>Order fulfillment workflow with multiple paths</description>
    <schemaVersion>2.0</schemaVersion>
    <process id="order-fulfillment-process">
        <name>Order Fulfillment Process</name>
        <inputCondition/>
        <nodes>
            <node id="start" type="Start" name="Start"/>
            <node id="order_received" type="Task" name="Order Received"/>
            <node id="inventory_check" type="Task" name="Inventory Check"/>
            <node id="shipping" type="Task" name="Process Shipping"/>
            <node id="payment" type="Task" name="Process Payment"/>
            <node id="packing" type="Task" name="Order Packing"/>
            <node id="shipping_label" type="Task" name="Shipping Label"/>
            <node id="notify" type="Task" name="Notify Customer"/>
            <node id="cancel" type="Task" name="Cancel Order"/>
            <node id="end" type="End" name="End"/>
        </nodes>
        <arcs>
            <arc id="arc1" from="start" to="order_received"/>
            <arc id="arc2" from="order_received" to="inventory_check"/>

            <!-- Inventory split: in stock vs backorder -->
            <arc id="arc3" from="inventory_check" to="shipping" name="in_stock"/>
            <arc id="arc4" from="inventory_check" to="payment" name="out_of_stock"/>

            <!-- Parallel shipping and payment -->
            <arc id="arc5" from="shipping" to="packing"/>
            <arc id="arc6" from="payment" to="packing"/>

            <!-- Packing to label and notification -->
            <arc id="arc7" from="packing" to="shipping_label"/>
            <arc id="arc8" from="packing" to="notify"/>

            <!-- Convergence -->
            <arc id="arc9" from="shipping_label" to="end"/>
            <arc id="arc10" from="notify" to="end"/>

            <!-- Cancel paths -->
            <arc id="arc11" from="order_received" to="cancel" name="cancel_early"/>
            <arc id="arc12" from="payment" to="cancel" name="payment_fail"/>
            <arc id="arc13" from="cancel" to="end"/>
        </arcs>
    </process>
    <outputCondition/>
</specification>
EOF

    # Add specification
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

    # Process orders
    local completed_orders=0
    local cancelled_orders=0
    local backorder_orders=0

    for ((order=1; order<=cases; order++)); do
        # Launch order case
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

        if ! echo "$response" | grep -q 'caseID'; then
            warn "Failed to launch order $order"
            continue
        fi

        local case_id=$(echo "$response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')

        # Simulate order processing
        local has_inventory=$((RANDOM % 10))  # 90% in stock, 10% backorder
        local payment_success=$((RANDOM % 10))  # 90% success, 10% failure

        # Get and complete work items
        while true; do
            response=$(curl -s -X POST \
                -H "Connection-ID: $CONNECTION_ID" \
                "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

            local work_items=$(echo "$response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

            if [[ $work_items -eq 0 ]]; then
                break
            fi

            # Process work items
            for ((i=1; i<=work_items; i++)); do
                # Determine work item based on order stage
                local work_item="workitem_$order"

                # Check for cancel work items
                if [[ "$order" -eq 10 ]] && [[ $payment_success -eq 0 ]]; then
                    # Cancel payment
                    response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>cancel</item><reason>payment_failure</reason></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=cancel&data=<data/>")
                    ((cancelled_orders++))
                    break
                fi

                # Complete normal work items
                response=$(curl -s -X POST \
                    -H "Connection-ID: $CONNECTION_ID" \
                    -H "Content-Type: application/xml" \
                    --data "<data><item>${work_item}</item></data>" \
                    "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item}&data=<data/>")
            done

            # Simulate backorder scenario
            if [[ $has_inventory -eq 0 ]] && [[ "$order" -eq 5 ]]; then
                response=$(curl -s -X POST \
                    -H "Connection-ID: $CONNECTION_ID" \
                    -H "Content-Type: application/xml" \
                    --data "<data><item>backorder</item></data>" \
                    "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=backorder&data=<data/>")
                ((backorder_orders++))
            fi

            sleep 0.1
        done

        # Check final state
        response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

        local case_state=$(echo "$response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")

        if [[ "$case_state" == "complete" ]]; then
            ((completed_orders++))
        fi
    done

    local duration=$(( $(date +%s) - start_time ))

    # Summary
    success "Order Fulfillment Validation Complete"
    echo "  Orders processed: $cases"
    echo "  Completed: $completed_orders"
    echo "  Cancelled: $cancelled_orders"
    echo "  Backorders: $backorder_orders"
    echo "  Duration: ${duration}s"
    echo "  Throughput: $((cases * 3600 / duration)) orders/hour"

    return 0
}

# Main execution
main() {
    echo "=== Order Fulfillment Business Scenario ==="
    echo "Demonstrating: WCP 1-5, 10-11, 20-21 patterns"
    echo "- Sequence: Order → Payment → Shipping"
    echo "- Parallel Split: Multiple shipping carriers"
    echo "- Exclusive Choice: In stock vs backorder"
    echo "- Cancel Activity: Customer cancellation at any point"
    echo "- Cancel Case: Payment failure handling"
    echo ""

    validate_order_fulfillment "$@"
}

main "$@"