#!/bin/bash

# YAWL Build Script with Actor Validation
# Integrates actor validation into the standard YAWL build process

set -e

# Configuration
ACTOR_VALIDATION_ENABLED=${ACTOR_VALIDATION_ENABLED:-true}
VALIDATION_REPORT_DIR="target/validation-reports"
ACTOR_REPORT_DIR="${VALIDATION_REPORT_DIR}/actor-validation"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Initialize validation report directory
init_validation_reports() {
    log_info "Initializing validation reports directory"
    mkdir -p "${ACTOR_REPORT_DIR}"
    echo "Build Timestamp: $(date)" > "${ACTOR_REPORT_DIR}/build-info.txt"
    echo "YAWL Version: $(cat pom.xml | grep -oP '<version>\K[0-9]+\.[0-9]+\.[0-9]+(?=</version>)')" >> "${ACTOR_REPORT_DIR}/build-info.txt"
}

# Run actor validation
run_actor_validation() {
    if [[ "${ACTOR_VALIDATION_ENABLED}" != "true" ]]; then
        log_warn "Actor validation is disabled"
        return 0
    fi

    log_info "Running actor validation..."

    # Store start time
    local start_time=$(date +%s)

    # Run actor validation using Maven plugin
    if mvn validate -P actor-validation; then
        local exit_code=$?
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))

        # Generate summary
        local violation_count=$(find "${ACTOR_REPORT_DIR}" -name "*-report.json" -exec cat {} \; | grep -c "violations" || echo 0)

        echo "Validation Results:" >> "${ACTOR_REPORT_DIR}/summary.txt"
        echo "=================" >> "${ACTOR_REPORT_DIR}/summary.txt"
        echo "Duration: ${duration} seconds" >> "${ACTOR_REPORT_DIR}/summary.txt"
        echo "Violations Found: ${violation_count}" >> "${ACTOR_REPORT_DIR}/summary.txt"
        echo "Status: ${violation_count} > 0 ? 'FAILED' : 'PASSED'" >> "${ACTOR_REPORT_DIR}/summary.txt"

        if [[ ${violation_count} -gt 0 ]]; then
            log_error "Actor validation completed with ${violation_count} violations"
            log_error "See report: ${ACTOR_REPORT_DIR}/"
            return 1
        else
            log_info "Actor validation completed successfully"
            return 0
        fi
    else
        log_error "Actor validation failed"
        return 1
    fi
}

# Compile code
compile_code() {
    log_info "Compiling YAWL code..."

    # Compile with actor validation dependencies
    mvn clean compile -P actor-validation -DskipTests

    if [[ $? -eq 0 ]]; then
        log_info "Code compilation completed successfully"
        return 0
    else
        log_error "Code compilation failed"
        return 1
    fi
}

# Run tests
run_tests() {
    log_info "Running tests..."

    # Run tests including actor validation tests
    mvn test -P actor-validation

    if [[ $? -eq 0 ]]; then
        log_info "All tests passed"
        return 0
    else
        log_error "Some tests failed"
        return 1
    fi
}

# Generate final report
generate_final_report() {
    log_info "Generating final validation report..."

    # Create comprehensive report
    local final_report="${VALIDATION_REPORT_DIR}/final-report.html"

    cat > "${final_report}" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>YAWL Build Validation Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .summary { background: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .success { background: #4CAF50; color: white; }
        .failure { background: #f44336; color: white; }
        .warning { background: #ff9800; color: white; }
        .section { margin: 20px 0; }
        pre { background: #f4f4f4; padding: 10px; border-radius: 3px; overflow-x: auto; }
    </style>
</head>
<body>
    <h1>YAWL Build Validation Report</h1>
    <p>Generated: $(date)</p>

    <div class="summary">
        <h2>Build Summary</h2>
        <table border="1" style="width: 100%; border-collapse: collapse;">
            <tr>
                <th>Phase</th>
                <th>Status</th>
                <th>Details</th>
            </tr>
            <tr>
                <td>Actor Validation</td>
                <td id="actor-status">Checking...</td>
                <td><a href="actor-validation/report.html">View Report</a></td>
            </tr>
            <tr>
                <td>Code Compilation</td>
                <td id="compile-status">Checking...</td>
                <td>-</td>
            </tr>
            <tr>
                <td>Tests</td>
                <td id="test-status">Checking...</td>
                <td><a href="../test-results/index.html">View Results</a></td>
            </tr>
        </table>
    </div>

    <div class="section">
        <h2>Actor Validation Details</h2>
        <div id="actor-details">
            <!-- Content will be inserted by JavaScript -->
        </div>
    </div>

    <script>
        // Read actor validation report
        fetch('actor-validation/report.json')
            .then(response => response.json())
            .then(data => {
                const status = data.status === 'GREEN' ? 'success' : 'failure';
                document.getElementById('actor-status').innerHTML = '<span class="' + status + '">' + (data.status === 'GREEN' ? 'PASSED' : 'FAILED') + '</span>';

                let details = '<h3>Validation Results</h3>';
                details += '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
                document.getElementById('actor-details').innerHTML = details;
            })
            .catch(error => {
                document.getElementById('actor-status').innerHTML = '<span class="failure">ERROR</span>';
                document.getElementById('actor-details').innerHTML = '<p>Error loading actor validation report: ' + error.message + '</p>';
            });
    </script>
</body>
</html>
EOF

    log_info "Final report generated: ${final_report}"
}

# Main build process
main() {
    log_info "Starting YAWL build with actor validation..."

    # Initialize
    init_validation_reports

    # Build phases
    phases=(
        "actor_validation:run_actor_validation"
        "compile:compile_code"
        "test:run_tests"
        "report:generate_final_report"
    )

    local overall_success=true
    local phase_results=()

    # Execute each phase
    for phase_entry in "${phases[@]}"; do
        IFS=':' read -r phase_name phase_command <<< "$phase_entry"

        log_info "=== Phase: ${phase_name} ==="

        if eval "$phase_command"; then
            phase_results+=("${phase_name}:SUCCESS")
            log_info "Phase ${phase_name} completed successfully"
        else
            phase_results+=("${phase_name}:FAILED")
            overall_success=false
            log_error "Phase ${phase_name} failed"

            # Continue unless it's actor validation (can't continue with violations)
            if [[ "$phase_name" == "actor_validation" ]]; then
                break
            fi
        fi
    done

    # Final summary
    log_info "=== Build Summary ==="
    for result in "${phase_results[@]}"; do
        IFS=':' read -r phase status <<< "$result"
        if [[ "$status" == "SUCCESS" ]]; then
            log_info "✅ ${phase}: PASSED"
        else
            log_error "❌ ${phase}: FAILED"
        fi
    done

    if [[ "$overall_success" == "true" ]]; then
        log_info "🎉 Build completed successfully with actor validation"
        exit 0
    else
        log_error "💥 Build failed. Check reports in ${VALIDATION_REPORT_DIR}/"
        exit 1
    fi
}

# Execute main function
main "$@"