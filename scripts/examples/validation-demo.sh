#!/usr/bin/env bash
# ==========================================================================
# validation-demo.sh — Demonstration of YAWL Validation Orchestration System
#
# This script demonstrates how to use the YAWL validation system with various
# configurations and scenarios.
#
# Usage:
#   bash scripts/examples/validation-demo.sh
#   bash scripts/examples/validation-demo.sh --scenario performance
#   bash scripts/examples/validation-demo.sh --scenario compliance
#   bash scripts/examples/validation-demo.sh --scenario chaos
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CONF_DIR="${REPO_ROOT}/.claude"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# Parse arguments
SCENARIO="all"
VERBOSE=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --scenario)
            SCENARIO="$2"
            shift 2
            ;;
        --verbose|-v)
            VERBOSE=1
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--scenario {all|performance|compliance|chaos}] [--verbose]"
            echo ""
            echo "Scenarios:"
            echo "  all        - Run all validation demos"
            echo "  performance - Focus on performance validation"
            echo "  compliance - Focus on A2A/MCP compliance"
            echo "  chaos      - Focus on chaos and stress testing"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Logging functions
log_info() {
    echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

# Function to check if services are running
check_services() {
    log_info "Checking YAWL services..."

    services=()

    if curl -s -f http://localhost:8080/health > /dev/null 2>&1; then
        services+=("Engine (8080)")
    else
        log_warning "YAWL Engine not running on port 8080"
    fi

    if curl -s -f http://localhost:8081/health > /dev/null 2>&1; then
        services+=("A2A Server (8081)")
    else
        log_warning "A2A Server not running on port 8081"
    fi

    if curl -s -f http://localhost:9090 > /dev/null 2>&1; then
        services+=("MCP Server (9090)")
    else
        log_warning "MCP Server not running on port 9090"
    fi

    if [[ ${#services[@]} -gt 0 ]]; then
        log_success "Services running: ${services[*]}"
    else
        log_error "No YAWL services are running. Please start them first."
        log_info "  bash scripts/start-yawl.sh"
        exit 1
    fi
}

# Function to validate compilation
validate_compilation() {
    log_info "Validating compilation..."

    if bash "${SCRIPT_DIR}/../dx.sh" compile; then
        log_success "Compilation successful"
        return 0
    else
        log_error "Compilation failed"
        return 1
    fi
}

# Function to run compliance validation
validate_compliance() {
    log_info "Running compliance validation..."

    # A2A Compliance
    log_info "Testing A2A compliance..."
    if bash "${SCRIPT_DIR}/../validation/a2a/validate-a2a-compliance.sh" --json 2>/dev/null; then
        log_success "A2A compliance passed"
    else
        log_warning "A2A compliance issues found"
    fi

    # MCP Compliance
    log_info "Testing MCP compliance..."
    if bash "${SCRIPT_DIR}/../validation/mcp/validate-mcp-compliance.sh" --json 2>/dev/null; then
        log_success "MCP compliance passed"
    else
        log_warning "MCP compliance issues found"
    fi
}

# Function to run performance validation
validate_performance() {
    log_info "Running performance validation..."

    # Integration performance testing
    log_info "Testing integration performance..."
    bash "${SCRIPT_DIR}/../validation/validate-integration.sh" \
        --workflow performance \
        --concurrent 50 \
        --duration 60 \
        --json 2>/dev/null || log_warning "Performance tests had issues"

    # Chaos performance impact
    log_info "Testing chaos impact on performance..."
    bash "${SCRIPT_DIR}/../validation/validate-chaos-stress.sh" \
        --scenario application \
        --concurrent 30 \
        --duration 60 \
        --json 2>/dev/null || log_warning "Chaos performance tests had issues"
}

# Function to run chaos validation
validate_chaos() {
    log_info "Running chaos validation..."

    # Network chaos
    log_info "Testing network resilience..."
    bash "${SCRIPT_DIR}/../validation/validate-chaos-stress.sh" \
        --scenario network \
        --duration 60 \
        --threshold 2 \
        --json 2>/dev/null || log_warning "Network chaos tests had issues"

    # Memory chaos
    log_info "Testing memory resilience..."
    bash "${SCRIPT_DIR}/../validation/validate-chaos-stress.sh" \
        --scenario memory \
        --duration 60 \
        --threshold 2 \
        --json 2>/dev/null || log_warning "Memory chaos tests had issues"

    # CPU chaos
    log_info "Testing CPU resilience..."
    bash "${SCRIPT_DIR}/../validation/validate-chaos-stress.sh" \
        --scenario cpu \
        --duration 60 \
        --threshold 2 \
        --json 2>/dev/null || log_warning "CPU chaos tests had issues"
}

# Function to run comprehensive validation
validate_comprehensive() {
    log_info "Running comprehensive validation..."

    # Full orchestration
    bash "${SCRIPT_DIR}/../validate-all.sh" \
        --json \
        --metrics-dir "${REPO_ROOT}/demo-metrics" \
        --concurrent 20 \
        --duration 300 \
        --parallel 2>/dev/null || log_warning "Comprehensive validation had issues"
}

# Function to generate report
generate_report() {
    log_info "Generating demo report..."

    REPORT_DIR="${REPO_ROOT}/demo-results"
    mkdir -p "$REPORT_DIR"

    # Create HTML report
    cat > "$REPORT_DIR/validation-report.html" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>YAWL Validation Demo Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .success { color: green; }
        .warning { color: orange; }
        .error { color: red; }
        pre { background-color: #f5f5f5; padding: 10px; border-radius: 3px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>YAWL Validation Demo Report</h1>
        <p>Generated: $(date)</p>
    </div>

    <div class="section">
        <h2>Validation Summary</h2>
        <p>This report demonstrates the YAWL validation orchestration system.</p>
        <ul>
            <li><strong>A2A Compliance</strong>: Agent-to-Agent protocol validation</li>
            <li><strong>MCP Compliance</strong>: Model Context Protocol validation</li>
            <li><strong>Chaos Testing</strong>: Resilience under adverse conditions</li>
            <li><strong>Performance Testing</strong>: System throughput and response times</li>
        </ul>
    </div>

    <div class="section">
        <h2>Usage Examples</h2>
        <pre><code>
# Quick validation
./scripts/validate-all.sh --fast

# Full validation with parallel execution
./scripts/validate-all.sh --parallel --json

# Chaos testing
./scripts/validation/validate-chaos-stress.sh --scenario network

# Integration testing
./scripts/validation/validate-integration.sh --workflow performance

# Compliance testing
./scripts/validation/a2a/validate-a2a-compliance.sh
./scripts/validation/mcp/validate-mcp-compliance.sh
        </code></pre>
    </div>
</body>
</html>
EOF

    log_success "Report generated: $REPORT_DIR/validation-report.html"
}

# Main demo function
run_demo() {
    log_info "Starting YAWL Validation System Demo"
    echo "=========================================="
    echo ""

    # Check services
    check_services
    echo ""

    # Validate compilation
    validate_compilation
    echo ""

    # Run scenario-specific validation
    case "$SCENARIO" in
        "all")
            log_info "Running all validation scenarios..."
            validate_compliance
            validate_performance
            validate_chaos
            validate_comprehensive
            ;;
        "performance")
            log_info "Running performance validation..."
            validate_performance
            validate_comprehensive
            ;;
        "compliance")
            log_info "Running compliance validation..."
            validate_compliance
            ;;
        "chaos")
            log_info "Running chaos validation..."
            validate_chaos
            validate_comprehensive
            ;;
    esac
    echo ""

    # Generate report
    generate_report
    echo ""

    log_success "Demo completed successfully!"
    log_info "Results available in: ${REPO_ROOT}/demo-results/"
}

# Run the demo
run_demo