#!/bin/bash

# AI/ML Patterns Validation (WCP 60-68)
# ML Pipeline, Rules Engine, Feature Store, Human-AI Handoff, Confidence Threshold

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/aiml"
ENGINE_URL="http://localhost:8080"
MCPS_URL="http://localhost:18081"
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

success() {
    echo "${GREEN}✓${NC} $1"
}

error() {
    echo "${RED}✗${NC} $1"
}

warn() {
    echo "${YELLOW}⚠${NC} $1"
}

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

# AI/ML pattern configurations
declare -A PATTERNS=(
    ["wcp-60-ml-pipeline.yaml"]="ML Pipeline"
    ["wcp-60-rules-engine.yaml"]="Rules Engine"
    ["wcp-61-ml-model.yaml"]="ML Model"
    ["wcp-62-human-ai-handoff.yaml"]="Human-AI Handoff"
    ["wcp-62-preprocessing.yaml"]="Preprocessing"
    ["wcp-63-features.yaml"]="Features"
    ["wcp-63-model-fallback.yaml"]="Model Fallback"
    ["wcp-64-confidence-threshold.yaml"]="Confidence Threshold"
    ["wcp-65-feature-store.yaml"]="Feature Store"
    ["wcp-66-pipeline.yaml"]="Pipeline"
    ["wcp-67-drift-detection.yaml"]="Drift Detection"
    ["wcp-68-auto-retrain.yaml"]="Auto Retrain"
)

# Simulate ML model predictions
simulate_ml_prediction() {
    local input_data="$1"
    local model_type="$2"
    local pattern_name="$3"

    # Simulate model response
    local confidence=$(awk "BEGIN {srand(); print rand() * 0.5 + 0.5}")
    local prediction="class_$(awk "BEGIN {srand(); print int(rand() * 5 + 1)}")"

    log "ML Prediction ($model_type): $prediction ($confidence confidence)"

    # Return JSON response
    echo "{\"prediction\": \"$prediction\", \"confidence\": $confidence}"
}

# Simulate rules engine evaluation
simulate_rules_engine() {
    local facts="$1"
    local rules="$2"
    local pattern_name="$3"

    # Simulate rule evaluation
    local decision=$(echo "$facts" | grep -q "urgent" && echo "approve" || echo "review")
    local confidence=$(echo "$facts" | grep -q "urgent" && echo "0.9" || echo "0.6")

    log "Rules Engine: $decision (confidence: $confidence)"

    echo "{\"decision\": \"$decision\", \"confidence\": $confidence}"
}

validate_pattern() {
    local pattern_file="$1"
    local pattern_name="$2"
    local start_time=$(date +%s)

    log "Validating: $pattern_name ($pattern_file)"

    # Convert to XML
    local xml_file
    xml_file="${SCRIPT_DIR}/../../tmp/$(basename "$pattern_file" .xml).xml"
    mkdir -p "$(dirname "$xml_file")"

    if ! "${SCRIPT_DIR}/yaml-to-xml.sh" "$PATTERNS_DIR/$pattern_file" "$(dirname "$xml_file")"; then
        error "Failed to convert YAML to XML"
        return 1
    fi

    # Add specification
    local spec_response
    spec_response=$(curl -s -X POST \
        -H "Content-Type: application/xml" \
        -H "Connection-ID: $CONNECTION_ID" \
        --data @"$xml_file" \
        "${ENGINE_URL}/yawl/ia?action=addSpecification")

    if ! echo "$spec_response" | grep -q 'success'; then
        error "Failed to add specification"
        return 1
    fi

    # Launch case
    local spec_id=$(echo "$spec_response" | sed 's/.*specIdentifier:\([^,]*\).*/\1/' | tr -d ' ')
    local case_response
    case_response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

    if ! echo "$case_response" | grep -q 'caseID'; then
        error "Failed to launch case"
        return 1
    fi

    local case_id=$(echo "$case_response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')

    local total_work_items=0
    local completed_work_items=0
    local ml_predictions=0
    local rules_evaluations=0
    case_completed=false

    # Process AI/ML patterns
    for iteration in {1..30}; do
        # Get work items
        local work_items_response
        work_items_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

        local current_items
        current_items=$(echo "$work_items_response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

        if [[ $current_items -gt 0 ]]; then
            # Process work items with AI/ML logic
            for ((i=1; i<=current_items; i++)); do
                local work_item_id="aiml_workitem_${iteration}_${i}"

                # Simulate AI processing based on pattern type
                case "$pattern_name" in
                    *ML*Pipeline*|*Prediction*)
                        # ML prediction
                        local input="{\"data\": \"sample_data_${iteration}\", \"timestamp\": \"$(date -Iseconds)\"}"
                        local prediction
                        prediction=$(simulate_ml_prediction "$input" "classifier" "$pattern_name")
                        ml_predictions=$((ml_predictions + 1))

                        # Determine if AI confidence is high enough
                        local confidence=$(echo "$prediction" | jq '.confidence' 2>/dev/null || echo "0.5")
                        if awk "BEGIN {exit ($confidence < 0.7)}"; then
                            # High confidence - auto-approve
                            log "AI auto-approved (confidence: $confidence)"
                        else
                            # Low confidence - require human review
                            warn "Low confidence detected ($confidence) - requiring human review"
                        fi
                        ;;
                    *Rule*Engine*)
                        # Rules evaluation
                        local facts="{\"priority\": \"high\", \"category\": \"${pattern_name}\"}"
                        local rules="priority > 0 && category != null"
                        local decision
                        decision=$(simulate_rules_engine "$facts" "$rules" "$pattern_name")
                        rules_evaluations=$((rules_evaluations + 1))

                        local rule_decision=$(echo "$decision" | jq '.decision' 2>/dev/null || echo "unknown")
                        log "Rules decision: $rule_decision"
                        ;;
                    *Human*AI*Handoff*|*Human*Review*)
                        # Human-AI collaboration
                        local confidence=$(awk "BEGIN {srand(); print rand() * 0.3 + 0.7}")
                        if awk "BEGIN {exit ($confidence < 0.9)}"; then
                            log "AI can handle autonomously (confidence: $confidence)"
                        else
                            log "Escalating to human review (confidence: $confidence)"
                            sleep 2  # Simulate human review time
                        fi
                        ;;
                    *Confidence*Threshold*)
                        # Confidence-based routing
                        local threshold=0.8
                        local confidence=$(awk "BEGIN {srand(); print rand()}")
                        if awk "BEGIN {exit ($confidence >= $threshold)}"; then
                            log "Above threshold ($confidence >= $threshold) - proceeding"
                        else
                            log "Below threshold ($confidence < $threshold) - retry or reject"
                        fi
                        ;;
                    *)
                        # Default AI processing
                        log "Processing with AI model"
                        ;;
                esac

                # Complete the work item
                local work_item_response
                work_item_response=$(curl -s -X POST \
                    -H "Connection-ID: $CONNECTION_ID" \
                    -H "Content-Type: application/xml" \
                    --data "<data><item>${work_item_id}</item><ai_processed>true</ai_processed></data>" \
                    "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item_id}&data=<data/>")

                if echo "$work_item_response" | grep -q 'success'; then
                    ((completed_work_items++))
                fi
            done
        else
            # No work items - simulate background AI processing
            case "$pattern_name" in
                *Feature*Store*)
                    # Feature store updates
                    log "Updating feature store"
                    sleep 1
                    ;;
                *Auto*ML*)
                    # AutoML training
                    log "Running AutoML training iteration $iteration"
                    sleep 2
                    ;;
                *)
                    # Background processing
                    log "AI background processing"
                    sleep 1
                    ;;
            esac
        fi

        ((total_work_items += current_items))

        # Check case state
        local state_response
        state_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

        local case_state=$(echo "$state_response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")

        if [[ "$case_state" == "complete" ]]; then
            case_completed=true
            break
        fi

        sleep 2  # Longer delay for AI/ML processing
    done

    local duration=$(( $(date +%s) - start_time ))

    # Final validation
    if [[ "$case_completed" == true ]]; then
        success "$pattern_name (${duration}s) - $completed_work_items/$total_work_items work items, $ml_predictions ML predictions, $rules_evaluations rules evaluations"
        return 0
    else
        error "$pattern_name - Failed (state: $case_state, ML predictions: $ml_predictions)"
        return 1
    fi
}

# Main execution
main() {
    echo "=== Validating AI/ML Patterns (WCP 60-68) ==="
    echo ""

    local total=0
    local passed=0
    local failed=0

    # Note about AI/ML simulation
    warn "AI/ML patterns use simulated models for validation"
    echo ""

    for pattern_file in "${!PATTERNS[@]}"; do
        if [[ -f "$PATTERNS_DIR/$pattern_file" ]]; then
            ((total++))
            if validate_pattern "$pattern_file" "${PATTERNS[$pattern_file]}"; then
                ((passed++))
            else
                ((failed++))
            fi
        else
            warn "Pattern file not found: $pattern_file"
        fi
    done

    echo ""
    echo "=== Summary ==="
    echo "Total patterns: $total"
    echo "Passed: $passed"
    echo "Failed: $failed"
    if [[ $total -gt 0 ]]; then
        echo "Success rate: $(( (passed * 100) / total ))%"
    else
        echo "Success rate: N/A (no patterns found)"
    fi

    if [[ $failed -eq 0 ]] && [[ $total -gt 0 ]]; then
        success "All AI/ML patterns validated successfully"
        exit 0
    elif [[ $total -eq 0 ]]; then
        error "No patterns found to validate"
        exit 1
    else
        error "$failed patterns failed validation"
        exit 1
    fi
}

main "$@"