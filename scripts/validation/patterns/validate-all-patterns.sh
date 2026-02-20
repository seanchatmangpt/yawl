#!/bin/bash

# YAWL v5.2 Pattern Validation - Main Orchestrator
# Validates all 43+ workflow control-flow patterns through Docker Compose

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_COMPOSE_FILE="${SCRIPT_DIR}/../../docker-compose.yml"
PATTERNS_DIR="${SCRIPT_DIR}/../../yawl-mcp-a2a-app/src/main/resources/patterns"
REPORTS_DIR="${SCRIPT_DIR}/../../reports"
ENGINE_URL="http://localhost:8080"
ENGINE_MGMT_URL="http://localhost:9090"
TIMEOUT=300
CATEGORY=${1:-all}
PATTERN_FILTER=${2:-}

# Initialize logging
LOG_FILE="${REPORTS_DIR}/pattern-validation-$(date +%Y%m%d-%H%M%S).log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE") 2>&1

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    log "${RED}ERROR:${NC} $1"
    exit 1
}

warn() {
    log "${YELLOW}WARNING:${NC} $1"
}

success() {
    log "${GREEN}SUCCESS:${NC} $1"
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --category)
                CATEGORY="$2"
                shift 2
                ;;
            --pattern)
                PATTERN_FILTER="$2"
                shift 2
                ;;
            --timeout)
                TIMEOUT="$2"
                shift 2
                ;;
            --help)
                echo "Usage: $0 [OPTIONS]"
                echo "Options:"
                echo "  --category CATEGORY    Run specific category (basic, branching, multiinstance, statebased, cancellation, extended, eventdriven, aiml, all)"
                echo "  --pattern PATTERN       Run specific pattern (e.g., WCP-1)"
                echo "  --timeout SECONDS      Timeout for validation (default: 300)"
                echo "  --help                Show this help message"
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                ;;
        esac
    done
}

# Check Docker and Docker Compose
check_docker() {
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed"
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        error "Docker Compose is not installed"
    fi
}

# Start Docker services
start_docker_services() {
    log "Starting Docker services..."

    if [[ "$CATEGORY" == "all" ]] || [[ "$CATEGORY" == "basic" ]] || [[ "$CATEGORY" == "branching" ]] || \
       [[ "$CATEGORY" == "multiinstance" ]] || [[ "$CATEGORY" == "statebased" ]] || \
       [[ "$CATEGORY" == "cancellation" ]] || [[ "$CATEGORY" == "extended" ]]; then
        # Start main YAWL engine
        if docker compose -f "$DOCKER_COMPOSE_FILE" up -d yawl-engine; then
            success "YAWL engine started"
        else
            error "Failed to start YAWL engine"
        fi
    fi

    if [[ "$CATEGORY" == "all" ]] || [[ "$CATEGORY" == "eventdriven" ]] || [[ "$CATEGORY" == "aiml" ]]; then
        # Start MCP-A2A for event-driven and AI patterns
        if docker compose -f "$DOCKER_COMPOSE_FILE" up -d yawl-mcp-a2a; then
            success "MCP-A2A services started"
        else
            error "Failed to start MCP-A2A services"
        fi
    fi
}

# Wait for engine health
wait_for_engine() {
    log "Waiting for engine health..."

    local max_attempts=$((TIMEOUT / 5))
    local attempts=0

    while [[ $attempts -lt $max_attempts ]]; do
        if curl -s "${ENGINE_MGMT_URL}/actuator/health/liveness" | grep -q '"UP"'; then
            success "Engine is healthy"
            return 0
        fi
        sleep 5
        ((attempts++))
    done

    error "Engine health check failed after ${TIMEOUT}s"
}

# Authenticate with YAWL engine
authenticate() {
    log "Authenticating with YAWL engine..."

    local response
    response=$(curl -s -X POST "${ENGINE_URL}/yawl/ib?action=connect&userid=admin&password=YAWL")

    if echo "$response" | grep -q 'connectionID.*admin'; then
        success "Authentication successful"
        # Extract connection ID
        CONNECTION_ID=$(echo "$response" | sed 's/.*connectionID:\([^,]*\).*/\1/' | tr -d ' ')
        export CONNECTION_ID
        return 0
    else
        error "Authentication failed"
    fi
}

# Get pattern files based on category
get_pattern_files() {
    local category="$1"
    local pattern_files=()

    case $category in
        basic)
            pattern_files=($(find "$PATTERNS_DIR/controlflow" -name "wcp-[1-5]-*.yaml" | sort))
            ;;
        branching)
            pattern_files=($(find "$PATTERNS_DIR/branching" -name "wcp-[6-11]-*.yaml" | sort))
            ;;
        multiinstance)
            pattern_files=($(find "$PATTERNS_DIR/multiinstance" -name "wcp-*.yaml" | grep -E "(12|13|14|15|16|17|24|26|27)" | sort))
            ;;
        statebased)
            pattern_files=($(find "$PATTERNS_DIR/statebased" -name "wcp-*.yaml" | grep -E "(18|19|20|21|32|33|34|35)" | sort))
            ;;
        cancellation)
            pattern_files=($(find "$PATTERNS_DIR/controlflow" -name "wcp-cancel-*.yaml" | sort))
            pattern_files+=($(find "$PATTERNS_DIR/controlflow" -name "wcp-[2][5].yaml" | sort))
            ;;
        extended)
            pattern_files=($(find "$PATTERNS_DIR/extended" -name "wcp-*.yaml" | grep -E "(41|42|43|44|45|46|47|48|49|50)" | sort))
            ;;
        eventdriven)
            pattern_files=($(find "$PATTERNS_DIR/eventdriven" -name "wcp-*.yaml" | sort))
            ;;
        aiml)
            pattern_files=($(find "$PATTERNS_DIR/aiml" -name "wcp-*.yaml" | sort))
            ;;
        *)
            # Get all patterns
            pattern_files=($(find "$PATTERNS_DIR" -name "*.yaml" | sort))
            ;;
    esac

    # Apply pattern filter if specified
    if [[ -n "$PATTERN_FILTER" ]]; then
        local filtered=()
        for file in "${pattern_files[@]}"; do
            if [[ "$file" =~ $PATTERN_FILTER ]]; then
                filtered+=("$file")
            fi
        done
        pattern_files=("${filtered[@]}")
    fi

    echo "${pattern_files[@]}"
}

# Validate single pattern
validate_pattern() {
    local pattern_file="$1"
    local pattern_name=$(basename "$pattern_file" | sed 's/\.yaml$//')
    local start_time=$(date +%s)

    log "Validating pattern: $pattern_name"

    # Check if pattern executor script exists
    if [[ ! -f "${SCRIPT_DIR}/pattern-executor.sh" ]]; then
        error "Pattern executor script not found: ${SCRIPT_DIR}/pattern-executor.sh"
    fi

    # Execute pattern validation
    local result
    result=$("${SCRIPT_DIR}/pattern-executor.sh" "$pattern_name" "$pattern_file")

    local exit_code=$?
    local duration=$(( $(date +%s) - start_time ))

    if [[ $exit_code -eq 0 ]]; then
        echo "  ${GREEN}✓${NC} $pattern_name (${duration}s)"
        return 0
    else
        echo "  ${RED}✗${NC} $pattern_name (${duration}s) - $result"
        return 1
    fi
}

# Generate validation report
generate_report() {
    local start_time="$1"
    local total_patterns="$2"
    local passed_patterns="$3"
    local failed_patterns="$4"

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    local report_file="${REPORTS_DIR}/pattern-validation-report.json"

    cat > "$report_file" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "engine_version": "6.0.0-alpha",
  "total_patterns": $total_patterns,
  "passed": $passed_patterns,
  "failed": $failed_patterns,
  "duration_seconds": $duration,
  "patterns": [
EOF

    # TODO: Add individual pattern results
    # For now, just add a placeholder
    cat >> "$report_file" << EOF
    {
      "id": "ALL",
      "name": "All Patterns",
      "status": "$([[ $failed_patterns -eq 0 ]] && echo "passed" || echo "failed")",
      "duration_ms": $((duration * 1000))
    }
  ],
  "summary": {
    "success_rate": $(awk "BEGIN {printf \"%.2f\", ($passed_patterns / $total_patterns) * 100}"),
    "status": "$([[ $failed_patterns -eq 0 ]] && echo "ALL_PASSED" || echo "SOME_FAILED")",
    "message": "$([[ $failed_patterns -eq 0 ]] && echo "All patterns validated successfully" || echo "$failed_patterns patterns failed validation")"
  }
}
EOF

    success "Report generated: $report_file"
}

# Cleanup Docker services
cleanup() {
    log "Cleaning up Docker services..."

    if [[ -n "${DOCKER_COMPOSE_FILE:-}" ]]; then
        docker compose -f "$DOCKER_COMPOSE_FILE" down 2>/dev/null || true
    fi

    # Remove dangling containers
    docker ps --filter "name=yawl" --format "{{.ID}}" | xargs -r docker rm -f 2>/dev/null || true
}

# Main execution
main() {
    local total_patterns=0
    local passed_patterns=0
    local failed_patterns=0
    local start_time=$(date +%s)

    log "=== YAWL v5.2 Pattern Validation ==="
    log "Category: $CATEGORY"
    log "Filter: ${PATTERN_FILTER:-none}"
    log "Timeout: ${TIMEOUT}s"
    log ""

    # Parse arguments
    parse_args "$@"

    # Check prerequisites
    check_docker

    # Start services
    start_docker_services

    # Wait for engine
    wait_for_engine

    # Authenticate
    authenticate

    # Get patterns to validate
    local pattern_files
    pattern_files=($(get_pattern_files "$CATEGORY"))
    total_patterns=${#pattern_files[@]}

    if [[ $total_patterns -eq 0 ]]; then
        warn "No patterns found for category: $CATEGORY"
        cleanup
        exit 0
    fi

    log "Found $total_patterns patterns to validate"
    log ""

    # Validate each pattern
    for pattern_file in "${pattern_files[@]}"; do
        if validate_pattern "$pattern_file"; then
            ((passed_patterns++))
        else
            ((failed_patterns++))
        fi
        echo ""
    done

    # Generate report
    generate_report "$start_time" "$total_patterns" "$passed_patterns" "$failed_patterns"

    # Cleanup
    cleanup

    # Summary
    log ""
    log "=== Validation Summary ==="
    log "Total patterns: $total_patterns"
    log "Passed: $passed_patterns"
    log "Failed: $failed_patterns"
    log "Success rate: $(awk "BEGIN {printf \"%.2f\", ($passed_patterns / $total_patterns) * 100}")%"
    log ""

    if [[ $failed_patterns -eq 0 ]]; then
        success "All patterns validated successfully!"
        exit 0
    else
        error "$failed_patterns patterns failed validation"
        exit 1
    fi
}

# Set trap for cleanup
trap cleanup EXIT

# Run main
main "$@"