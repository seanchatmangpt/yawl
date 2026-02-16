#!/bin/bash

################################################################################
# YAWL v5.2 Health Check Script
#
# Purpose: Comprehensive health validation during deployment or rollback
# Usage: ./scripts/health-check.sh [--verbose] [--wait-for-startup]
# Exit Codes: 0 = all checks passed, 1 = warnings, 2 = critical failure
################################################################################

set -uo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
readonly CATALINA_HOME="${CATALINA_HOME:-/opt/apache-tomcat-10.1.13}"

# Flags
VERBOSE=false
WAIT_FOR_STARTUP=false
STARTUP_TIMEOUT=120

# Color codes
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# Test results
CHECKS_PASSED=0
CHECKS_FAILED=0
CHECKS_WARNING=0

################################################################################
# Logging
################################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*"
    ((CHECKS_PASSED++))
}

log_warning() {
    echo -e "${YELLOW}[!]${NC} $*"
    ((CHECKS_WARNING++))
}

log_error() {
    echo -e "${RED}[✗]${NC} $*" >&2
    ((CHECKS_FAILED++))
}

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*"
    fi
}

################################################################################
# Argument Parsing
################################################################################

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --verbose)
                VERBOSE=true
                shift
                ;;
            --wait-for-startup)
                WAIT_FOR_STARTUP=true
                shift
                ;;
            --startup-timeout)
                STARTUP_TIMEOUT="$2"
                shift 2
                ;;
            -h|--help)
                cat << EOF
Usage: $0 [OPTIONS]

OPTIONS:
    --verbose              Detailed output for each check
    --wait-for-startup     Wait for Tomcat to become responsive (up to 120s)
    --startup-timeout N    Custom startup timeout in seconds (default: 120)
    -h, --help             Show this help message

EXAMPLES:
    $0                          # Run all checks
    $0 --verbose                # Detailed output
    $0 --wait-for-startup       # Wait for Tomcat readiness
EOF
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 2
                ;;
        esac
    done
}

################################################################################
# Tomcat Process Checks
################################################################################

check_tomcat_running() {
    log_info "Checking Tomcat process..."

    if pgrep -f "org.apache.catalina.startup.Bootstrap" > /dev/null 2>&1; then
        local pid=$(pgrep -f "org.apache.catalina.startup.Bootstrap")
        log_success "Tomcat running (PID: $pid)"
        return 0
    else
        log_error "Tomcat not running"
        return 1
    fi
}

check_tomcat_port() {
    log_info "Checking Tomcat port binding..."

    if netstat -tlnp 2>/dev/null | grep -q ":8080.*LISTEN"; then
        log_success "Tomcat listening on port 8080"
        return 0
    elif nc -zv localhost 8080 &>/dev/null; then
        log_success "Tomcat port 8080 accessible"
        return 0
    else
        log_error "Tomcat not listening on port 8080"
        return 1
    fi
}

check_jvm_memory() {
    log_info "Checking JVM memory allocation..."

    if ! pgrep -f "org.apache.catalina.startup.Bootstrap" > /dev/null 2>&1; then
        log_warning "Tomcat not running (skipping memory check)"
        return 0
    fi

    local pid=$(pgrep -f "org.apache.catalina.startup.Bootstrap")
    local mem_info=$(ps aux | grep "$pid" | grep -v grep | awk '{print $6}')

    if [[ -n "$mem_info" ]]; then
        local mem_mb=$((mem_info / 1024))
        log_debug "JVM memory usage: ${mem_mb}MB"

        if [[ $mem_mb -lt 200 ]]; then
            log_warning "JVM memory low: ${mem_mb}MB"
            return 1
        elif [[ $mem_mb -gt 2000 ]]; then
            log_warning "JVM memory high: ${mem_mb}MB (possible leak?)"
            return 1
        else
            log_success "JVM memory normal: ${mem_mb}MB"
            return 0
        fi
    else
        log_warning "Could not determine JVM memory"
        return 0
    fi
}

################################################################################
# Startup Readiness
################################################################################

wait_for_startup() {
    if [[ "$WAIT_FOR_STARTUP" != "true" ]]; then
        return 0
    fi

    log_info "Waiting for Tomcat startup (up to ${STARTUP_TIMEOUT}s)..."

    local elapsed=0
    while [[ $elapsed -lt $STARTUP_TIMEOUT ]]; do
        if curl -s http://localhost:8080/yawl/ib > /dev/null 2>&1; then
            log_success "Tomcat startup complete (${elapsed}s)"
            return 0
        fi

        sleep 2
        ((elapsed += 2))
        echo -ne "\rWaiting... ${elapsed}s"
    done

    echo ""
    log_error "Tomcat startup timeout after ${STARTUP_TIMEOUT}s"
    return 1
}

################################################################################
# REST API Checks
################################################################################

check_rest_api_health() {
    log_info "Checking REST API health endpoint..."

    local response=$(curl -s -w "\n%{http_code}" http://localhost:8080/yawl/ib)
    local http_code=$(echo "$response" | tail -1)

    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "302" ]]; then
        log_success "REST API health: HTTP $http_code"
        return 0
    else
        log_error "REST API health failed: HTTP $http_code"
        return 1
    fi
}

check_rest_api_cases() {
    log_info "Checking REST API cases endpoint..."

    local response=$(curl -s -w "\n%{http_code}" http://localhost:8080/yawl/api/cases)
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | head -1)

    if [[ "$http_code" == "200" ]]; then
        # Try to parse JSON
        if echo "$body" | grep -q '"cases"' 2>/dev/null; then
            local case_count=$(echo "$body" | grep -o '"case_id"' | wc -l)
            log_success "Cases endpoint OK (HTTP 200, $case_count cases)"
            return 0
        else
            log_warning "Cases endpoint returned 200 but unexpected format"
            return 0
        fi
    elif [[ "$http_code" == "401" ]] || [[ "$http_code" == "403" ]]; then
        log_warning "Cases endpoint requires authentication (HTTP $http_code) - expected"
        return 0
    else
        log_error "Cases endpoint failed: HTTP $http_code"
        return 1
    fi
}

check_rest_api_swagger() {
    log_info "Checking REST API documentation..."

    local response=$(curl -s -w "\n%{http_code}" http://localhost:8080/yawl/api-docs)
    local http_code=$(echo "$response" | tail -1)

    if [[ "$http_code" == "200" ]]; then
        log_success "API documentation available (HTTP 200)"
        return 0
    else
        log_warning "API documentation not available (HTTP $http_code)"
        return 0
    fi
}

################################################################################
# Database Checks
################################################################################

check_database_postgresql() {
    log_info "Checking PostgreSQL database..."

    if ! command -v psql &>/dev/null; then
        log_debug "psql not installed (skipping)"
        return 0
    fi

    if ! psql -U postgres -d postgres -c "SELECT 1" &>/dev/null; then
        log_warning "PostgreSQL not accessible"
        return 0
    fi

    # Check YAWL database
    if psql -U postgres -d yawl -c "SELECT 1" &>/dev/null; then
        local table_count=$(psql -U postgres -d yawl -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null || echo "0")
        log_success "PostgreSQL ready ($table_count tables)"
        return 0
    else
        log_warning "YAWL database not found"
        return 0
    fi
}

check_database_connectivity() {
    log_info "Checking database connectivity from application..."

    local response=$(curl -s -w "\n%{http_code}" "http://localhost:8080/yawl/admin/db-test" 2>/dev/null || echo "404")
    local http_code=$(echo "$response" | tail -1)

    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "201" ]]; then
        log_success "Database connectivity verified (HTTP $http_code)"
        return 0
    else
        log_debug "Database test endpoint not available (HTTP $http_code)"
        return 0
    fi
}

################################################################################
# Configuration Checks
################################################################################

check_hibernate_properties() {
    log_info "Checking Hibernate configuration..."

    local hibernate_file="$PROJECT_ROOT/build/properties/hibernate.properties"

    if [[ ! -f "$hibernate_file" ]]; then
        log_warning "Hibernate properties file not found"
        return 0
    fi

    # Check critical properties
    local missing_props=0

    if ! grep -q "^hibernate.dialect" "$hibernate_file"; then
        log_warning "Missing: hibernate.dialect"
        ((missing_props++))
    fi

    if ! grep -q "^jdbc.url\|^hibernate.connection.url" "$hibernate_file"; then
        log_warning "Missing: JDBC URL"
        ((missing_props++))
    fi

    if ! grep -q "^jdbc.user\|^hibernate.connection.username" "$hibernate_file"; then
        log_warning "Missing: JDBC username"
        ((missing_props++))
    fi

    if [[ $missing_props -eq 0 ]]; then
        log_success "Hibernate configuration valid"
        return 0
    else
        log_error "Hibernate configuration incomplete ($missing_props missing properties)"
        return 1
    fi
}

check_log_configuration() {
    log_info "Checking logging configuration..."

    local log_config="$PROJECT_ROOT/build/properties/log4j2.xml"

    if [[ ! -f "$log_config" ]]; then
        log_warning "Log4j2 configuration not found"
        return 0
    fi

    if grep -q "appender\|logger" "$log_config"; then
        log_success "Log configuration present"
        return 0
    else
        log_warning "Log configuration may be incomplete"
        return 0
    fi
}

################################################################################
# Application Logs Checks
################################################################################

check_tomcat_logs() {
    log_info "Checking Tomcat logs for errors..."

    if [[ ! -f "$CATALINA_HOME/logs/catalina.out" ]]; then
        log_debug "Tomcat log file not found"
        return 0
    fi

    local error_count=$(grep -c "ERROR\|Exception\|SEVERE" "$CATALINA_HOME/logs/catalina.out" 2>/dev/null || echo "0")
    local recent_errors=$(tail -100 "$CATALINA_HOME/logs/catalina.out" | grep -c "ERROR\|Exception" || echo "0")

    if [[ $recent_errors -gt 5 ]]; then
        log_error "Many recent errors detected (last 100 lines): $recent_errors"
        if [[ "$VERBOSE" == "true" ]]; then
            tail -20 "$CATALINA_HOME/logs/catalina.out" | grep "ERROR\|Exception" || true
        fi
        return 1
    elif [[ $recent_errors -gt 0 ]]; then
        log_warning "Some errors in recent logs: $recent_errors"
        return 0
    else
        log_success "Tomcat logs clean"
        return 0
    fi
}

check_application_startup() {
    log_info "Checking application startup messages..."

    if [[ ! -f "$CATALINA_HOME/logs/catalina.out" ]]; then
        return 0
    fi

    # Look for successful startup indicators
    if grep -q "Server startup\|Application ready\|Started successfully" "$CATALINA_HOME/logs/catalina.out"; then
        log_success "Application startup messages found"
        return 0
    else
        log_debug "Application startup messages not found (may not be logged)"
        return 0
    fi
}

################################################################################
# Performance Checks
################################################################################

check_cpu_usage() {
    log_info "Checking CPU usage..."

    if ! pgrep -f "org.apache.catalina.startup.Bootstrap" > /dev/null 2>&1; then
        log_warning "Tomcat not running"
        return 0
    fi

    local pid=$(pgrep -f "org.apache.catalina.startup.Bootstrap")
    local cpu=$(ps aux | grep "$pid" | grep -v grep | awk '{print $3}')

    log_debug "CPU usage: ${cpu}%"

    if (( $(echo "$cpu > 80" | bc -l) )); then
        log_warning "High CPU usage: ${cpu}%"
        return 1
    else
        log_success "CPU usage normal: ${cpu}%"
        return 0
    fi
}

check_disk_space() {
    log_info "Checking disk space..."

    local available_gb=$(df "$PROJECT_ROOT" | awk 'NR==2 {printf "%.1f", $4 / 1024 / 1024}')

    if (( $(echo "$available_gb < 1" | bc -l) )); then
        log_error "Low disk space: ${available_gb}GB available"
        return 1
    elif (( $(echo "$available_gb < 2" | bc -l) )); then
        log_warning "Disk space low: ${available_gb}GB available"
        return 0
    else
        log_success "Disk space adequate: ${available_gb}GB available"
        return 0
    fi
}

################################################################################
# Web Application Checks
################################################################################

check_web_application() {
    log_info "Checking web application..."

    local response=$(curl -s -w "\n%{http_code}" http://localhost:8080/yawl-web/)
    local http_code=$(echo "$response" | tail -1)

    if [[ "$http_code" == "200" ]]; then
        log_success "Web application responsive (HTTP 200)"
        return 0
    elif [[ "$http_code" == "301" ]] || [[ "$http_code" == "302" ]]; then
        log_success "Web application responding (HTTP $http_code redirect)"
        return 0
    else
        log_warning "Web application returned HTTP $http_code"
        return 0
    fi
}

################################################################################
# Stateless Engine Checks
################################################################################

check_stateless_engine() {
    log_info "Checking stateless engine..."

    local response=$(curl -s -w "\n%{http_code}" http://localhost:8080/yawl-stateless/health)
    local http_code=$(echo "$response" | tail -1)

    if [[ "$http_code" == "200" ]]; then
        log_success "Stateless engine responding (HTTP 200)"
        return 0
    elif [[ "$http_code" == "404" ]]; then
        log_debug "Stateless engine not deployed"
        return 0
    else
        log_warning "Stateless engine returned HTTP $http_code"
        return 0
    fi
}

################################################################################
# Report Generation
################################################################################

print_summary() {
    echo ""
    echo "============================================================"
    echo "HEALTH CHECK SUMMARY"
    echo "============================================================"
    echo ""
    echo -e "Checks Passed:   ${GREEN}$CHECKS_PASSED${NC}"
    echo -e "Checks Failed:   ${RED}$CHECKS_FAILED${NC}"
    echo -e "Warnings:        ${YELLOW}$CHECKS_WARNING${NC}"
    echo ""

    local total=$((CHECKS_PASSED + CHECKS_FAILED + CHECKS_WARNING))
    local success_rate=$((CHECKS_PASSED * 100 / total))

    if [[ $CHECKS_FAILED -eq 0 ]] && [[ $CHECKS_WARNING -le 2 ]]; then
        echo -e "${GREEN}Status: HEALTHY (${success_rate}% passed)${NC}"
        echo ""
        echo "The application is ready for use."
    elif [[ $CHECKS_FAILED -eq 0 ]]; then
        echo -e "${YELLOW}Status: DEGRADED (${success_rate}% passed, minor issues)${NC}"
        echo ""
        echo "The application is functional but may have minor issues."
        echo "Review warnings above."
    else
        echo -e "${RED}Status: UNHEALTHY (${success_rate}% passed)${NC}"
        echo ""
        echo "Critical issues detected. Do not use in production."
        echo "Troubleshooting steps:"
        echo "1. Check Tomcat logs: tail -f $CATALINA_HOME/logs/catalina.out"
        echo "2. Verify database: psql -U postgres -d yawl -c 'SELECT 1;'"
        echo "3. Restart service: systemctl restart yawl-app"
    fi

    echo ""
    echo "============================================================"
}

################################################################################
# Main Execution
################################################################################

main() {
    log_info "YAWL v5.2 Health Check"
    echo ""

    # Wait for startup if requested
    wait_for_startup || {
        log_error "Failed to wait for startup"
        CHECKS_FAILED=1
    }

    # Tomcat checks
    check_tomcat_running
    check_tomcat_port
    check_jvm_memory

    # API checks
    check_rest_api_health
    check_rest_api_cases
    check_rest_api_swagger

    # Database checks
    check_database_postgresql
    check_database_connectivity

    # Configuration checks
    check_hibernate_properties
    check_log_configuration

    # Log checks
    check_tomcat_logs
    check_application_startup

    # Performance checks
    check_cpu_usage
    check_disk_space

    # Application checks
    check_web_application
    check_stateless_engine

    # Print summary
    print_summary

    # Exit with appropriate code
    if [[ $CHECKS_FAILED -gt 0 ]]; then
        exit 2
    elif [[ $CHECKS_WARNING -gt 0 ]]; then
        exit 1
    else
        exit 0
    fi
}

parse_arguments "$@"
main
