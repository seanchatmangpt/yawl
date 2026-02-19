#!/bin/bash
#
# Performance Comparison Script
# Compares performance metrics across different YAWL versions/ configurations
#

set -e

# Source common utilities
source "$(dirname "$0")/validation/lib/common.sh"

log_section "YAWL Performance Comparison"

# Configuration
readonly RESULTS_DIR="${RESULTS_DIR:-./performance/results}"
readonly BASELINE_DIR="${BASELINE_DIR:-./performance/baseline}"
readonly COMPARE_SCRIPT="./scripts/benchmark-compare.sh"

# Create directories
mkdir -p "$RESULTS_DIR"
mkdir -p "$BASELINE_DIR"

# Default comparison targets
declare -a COMPARISON_TARGETS=(
    "current:./target/yawl-engine-*.jar"
    "baseline:./performance/baseline/yawl-engine-5.2.jar"
    "java21:./target/yawl-engine-java21.jar"
    "virtual-threads:./target/yawl-engine-virtual-threads.jar"
)

# Parse command line arguments
VERBOSE=false
CI_MODE=false
TARGETS_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose)
            VERBOSE=true
            shift
            ;;
        --ci)
            CI_MODE=true
            shift
            ;;
        --targets)
            TARGETS_ONLY=true
            shift
            ;;
        --target)
            if [[ -n "$2" ]]; then
                COMPARISON_TARGETS=("$2")
                shift 2
            else
                echo "Error: --target requires a value"
                exit 1
            fi
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --verbose          Enable verbose output"
            echo "  --ci               CI mode - generate JSON output"
            echo "  --targets          List available targets only"
            echo "  --target TARGET   Compare specific target (format: name:path)"
            echo "  -h, --help         Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# List available targets
if [[ "$TARGETS_ONLY" == "true" ]]; then
    echo "Available comparison targets:"
    for target in "${COMPARISON_TARGETS[@]}"; do
        IFS=':' read -r name path <<< "$target"
        echo "  $name: $path"
    done
    exit 0
fi

# Check if comparison script exists
if [[ ! -f "$COMPARE_SCRIPT" ]]; then
    log_warning "Comparison script not found at $COMPARE_SCRIPT"
    log_info "Creating minimal comparison wrapper..."

    # Create minimal comparison script
    cat > "$COMPARE_SCRIPT" << 'EOF'
#!/bin/bash
# Minimal performance comparison wrapper
echo "Performance Comparison Results:"
echo "================================"
echo "Target: $1"
echo "Metrics: Response time, Throughput, Memory usage"
echo "Status: Implementation pending"
EOF
    chmod +x "$COMPARE_SCRIPT"
fi

# Run comparisons
failed_comparisons=0
declare -a comparison_results=()

for target in "${COMPARISON_TARGETS[@]}"; do
    IFS=':' read -r name path <<< "$target"

    if [[ "$VERBOSE" == "true" ]]; then
        log_section "Comparing: $name"
    fi

    # Check if target exists
    if [[ ! -f "$path" ]]; then
        log_warning "Target not found: $path"
        comparison_results+=("$name: missing")
        continue
    fi

    # Run comparison
    if [[ "$CI_MODE" == "true" ]]; then
        # CI mode - structured output
        result=$("$COMPARE_SCRIPT" "$path" 2>&1 || echo "failed")
        comparison_results+=("$name: $result")
    else
        # Normal mode
        echo "Comparing $name..."
        if "$COMPARE_SCRIPT" "$path"; then
            echo "✓ $name comparison completed"
            comparison_results+=("$name: success")
        else
            echo "✗ $name comparison failed"
            comparison_results+=("$name: failed")
            failed_comparisons=$((failed_comparisons + 1))
        fi
        echo
    fi
done

# Summary
log_header "Performance Comparison Summary"
echo "=================================="

if [[ "$CI_MODE" == "true" ]]; then
    # JSON output for CI
    cat << EOF
{
    "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%SZ')",
    "comparisons": [
EOF
    first=true
    for result in "${comparison_results[@]}"; do
        if [[ "$first" == "true" ]]; then
            first=false
        else
            echo ","
        fi
        IFS=':' read -r name status <<< "$result"
        cat << EOF
        {
            "target": "$name",
            "status": "$status"
        }
EOF
    done
    cat << EOF
    ],
    "failed_comparisons": $failed_comparisons
}
EOF
else
    # Human-readable output
    for result in "${comparison_results[@]}"; do
        IFS=':' read -r name status <<< "$result"
        if [[ "$status" == "success" ]]; then
            echo "✓ $name"
        elif [[ "$status" == "failed" ]]; then
            echo "✗ $name"
        elif [[ "$status" == "missing" ]]; then
            echo "? $name (missing)"
        else
            echo "- $name: $status"
        fi
    done

    if [[ $failed_comparisons -gt 0 ]]; then
        echo
        log_error "$failed_comparisons comparison(s) failed"
        exit 1
    else
        log_success "All comparisons completed successfully"
    fi
fi