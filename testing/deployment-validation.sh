#!/bin/bash

################################################################################
# YAWL Deployment Validation Script
#
# Comprehensive deployment validation for multiple platforms:
# - Kubernetes (GKE, EKS, AKS)
# - Docker Compose
# - Docker Swarm
# - Cloud Run / Cloud Functions
# - AWS CloudFormation / Terraform
# - Azure ARM Templates
#
# Usage: ./deployment-validation.sh [--platform PLATFORM] [--verbose]
################################################################################

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="/tmp/yawl-deployment-validation-$(date +%s).log"
REPORT_FILE="/tmp/yawl-deployment-validation-report-$(date +%s).txt"
VERBOSE=false
PLATFORM="${1:-auto}"
PASSED=0
FAILED=0
SKIPPED=0

# Functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $*" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}✓${NC} $*" | tee -a "$LOG_FILE"
    ((PASSED++))
}

error() {
    echo -e "${RED}✗${NC} $*" | tee -a "$LOG_FILE"
    ((FAILED++))
}

warning() {
    echo -e "${YELLOW}⚠${NC} $*" | tee -a "$LOG_FILE"
    ((SKIPPED++))
}

debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*" | tee -a "$LOG_FILE"
    fi
}

run_command() {
    local cmd="$1"
    local description="${2:-}"
    debug "Running: $cmd"

    if output=$(eval "$cmd" 2>&1); then
        debug "Command succeeded: $description"
        echo "$output"
        return 0
    else
        debug "Command failed: $description"
        return 1
    fi
}

# Kubernetes validation functions
validate_kubernetes() {
    log "\n${BLUE}=== KUBERNETES VALIDATION ===${NC}"

    # Check if kubectl is available
    if ! command -v kubectl &> /dev/null; then
        warning "kubectl not found, skipping Kubernetes validation"
        return
    fi

    # Check cluster connectivity
    log "Checking cluster connectivity..."
    if kubectl cluster-info &> /dev/null; then
        success "Kubernetes cluster connectivity"
    else
        error "Cannot connect to Kubernetes cluster"
        return
    fi

    local namespace="${K8S_NAMESPACE:-yawl}"

    # Check namespace
    log "Checking namespace: $namespace"
    if kubectl get namespace "$namespace" &> /dev/null; then
        success "Namespace '$namespace' exists"
    else
        error "Namespace '$namespace' does not exist"
        return
    fi

    # Check deployment
    log "Checking deployment..."
    if kubectl get deployment yawl -n "$namespace" &> /dev/null; then
        success "Deployment 'yawl' exists"

        # Check replicas
        local desired=$(kubectl get deployment yawl -n "$namespace" -o jsonpath='{.spec.replicas}')
        local ready=$(kubectl get deployment yawl -n "$namespace" -o jsonpath='{.status.readyReplicas}')

        if [[ "$ready" -ge 1 ]]; then
            success "Deployment has $ready/$desired replicas ready"
        else
            error "Deployment has $ready/$desired replicas ready"
        fi
    else
        error "Deployment 'yawl' not found"
        return
    fi

    # Check pods
    log "Checking pods..."
    local pod_count=$(kubectl get pods -n "$namespace" -l app=yawl --no-headers 2>/dev/null | wc -l)
    if [[ $pod_count -gt 0 ]]; then
        success "Found $pod_count YAWL pods"

        # Check pod status
        local running=$(kubectl get pods -n "$namespace" -l app=yawl --no-headers 2>/dev/null | grep "Running" | wc -l)
        if [[ $running -eq $pod_count ]]; then
            success "All $pod_count pods are running"
        else
            error "Only $running/$pod_count pods are running"
        fi
    else
        error "No YAWL pods found"
    fi

    # Check services
    log "Checking services..."
    if kubectl get service yawl -n "$namespace" &> /dev/null; then
        success "Service 'yawl' exists"

        local endpoints=$(kubectl get endpoints yawl -n "$namespace" -o jsonpath='{.subsets[0].addresses[*].ip}' 2>/dev/null | wc -w)
        if [[ $endpoints -gt 0 ]]; then
            success "Service has $endpoints endpoints"
        else
            warning "Service has no endpoints"
        fi
    else
        error "Service 'yawl' not found"
    fi

    # Check persistent volumes
    log "Checking persistent volumes..."
    local pvc_count=$(kubectl get pvc -n "$namespace" --no-headers 2>/dev/null | wc -l)
    if [[ $pvc_count -gt 0 ]]; then
        local bound=$(kubectl get pvc -n "$namespace" --no-headers 2>/dev/null | grep "Bound" | wc -l)
        if [[ $bound -eq $pvc_count ]]; then
            success "All $pvc_count PVCs are bound"
        else
            error "$bound/$pvc_count PVCs are bound"
        fi
    fi

    # Check resource limits
    log "Checking resource limits..."
    local limits=$(kubectl get deployment yawl -n "$namespace" -o jsonpath='{.spec.template.spec.containers[0].resources.limits}' 2>/dev/null)
    if [[ ! -z "$limits" && "$limits" != "{}" ]]; then
        success "Resource limits configured"
    else
        warning "No resource limits configured"
    fi

    # Check network policies
    log "Checking network policies..."
    local np_count=$(kubectl get networkpolicy -n "$namespace" --no-headers 2>/dev/null | wc -l)
    if [[ $np_count -gt 0 ]]; then
        success "Found $np_count network policies"
    else
        warning "No network policies found"
    fi

    # Check ingress
    log "Checking ingress..."
    if kubectl get ingress -n "$namespace" &> /dev/null; then
        local ingress_count=$(kubectl get ingress -n "$namespace" --no-headers 2>/dev/null | wc -l)
        success "Found $ingress_count ingress resources"
    fi

    # Check RBAC
    log "Checking RBAC..."
    if kubectl get serviceaccount -n "$namespace" &> /dev/null; then
        success "Service accounts configured"
    fi

    # Check events
    log "Checking recent events..."
    local warning_events=$(kubectl get events -n "$namespace" --sort-by='.lastTimestamp' 2>/dev/null | grep -i "warning" | wc -l)
    if [[ $warning_events -gt 0 ]]; then
        warning "Found $warning_events warning events"
    else
        success "No warning events"
    fi
}

# Docker Compose validation functions
validate_docker_compose() {
    log "\n${BLUE}=== DOCKER COMPOSE VALIDATION ===${NC}"

    # Check if docker is available
    if ! command -v docker &> /dev/null; then
        warning "Docker not found, skipping Docker validation"
        return
    fi

    if ! command -v docker-compose &> /dev/null; then
        warning "docker-compose not found, skipping Docker Compose validation"
        return
    fi

    local compose_file="${COMPOSE_FILE:-/home/user/yawl/docker-compose.yml}"

    # Check compose file exists
    if [[ ! -f "$compose_file" ]]; then
        warning "docker-compose.yml not found at $compose_file"
        return
    fi
    success "docker-compose.yml found"

    # Validate compose file syntax
    log "Validating compose file syntax..."
    if docker-compose -f "$compose_file" config > /dev/null 2>&1; then
        success "docker-compose.yml is valid"
    else
        error "docker-compose.yml has syntax errors"
        return
    fi

    # Check services
    log "Checking services..."
    local services=$(docker-compose -f "$compose_file" config --services 2>/dev/null)
    local service_count=$(echo "$services" | wc -w)
    success "Found $service_count services"

    # Check running containers
    log "Checking running containers..."
    if docker-compose -f "$compose_file" ps > /dev/null 2>&1; then
        local running=$(docker-compose -f "$compose_file" ps -q 2>/dev/null | wc -l)
        success "Found $running running containers"
    else
        error "Cannot get container status"
    fi

    # Check container health
    log "Checking container health..."
    docker-compose -f "$compose_file" ps 2>/dev/null | while read -r line; do
        if [[ "$line" == *"unhealthy"* ]]; then
            error "Unhealthy container detected: $line"
        elif [[ "$line" == *"healthy"* ]]; then
            debug "Healthy container: $line"
        fi
    done || true

    # Check volume status
    log "Checking volumes..."
    local volumes=$(docker-compose -f "$compose_file" config --format json 2>/dev/null | grep -o '"volumes"' | wc -l)
    if [[ $volumes -gt 0 ]]; then
        success "Volumes configured"
    fi

    # Check networks
    log "Checking networks..."
    local networks=$(docker-compose -f "$compose_file" config --format json 2>/dev/null | grep -o '"networks"' | wc -l)
    if [[ $networks -gt 0 ]]; then
        success "Networks configured"
    fi
}

# Cloud Run validation functions
validate_cloud_run() {
    log "\n${BLUE}=== CLOUD RUN VALIDATION ===${NC}"

    # Check if gcloud is available
    if ! command -v gcloud &> /dev/null; then
        warning "gcloud CLI not found, skipping Cloud Run validation"
        return
    fi

    local project_id="${GCP_PROJECT_ID:-}"
    local region="${GCP_REGION:-us-central1}"
    local service_name="${CLOUD_RUN_SERVICE:-yawl-workflow}"

    if [[ -z "$project_id" ]]; then
        warning "GCP_PROJECT_ID not set, skipping Cloud Run validation"
        return
    fi

    # Check service exists
    log "Checking Cloud Run service..."
    if gcloud run services describe "$service_name" --region="$region" --project="$project_id" &> /dev/null; then
        success "Cloud Run service '$service_name' exists"
    else
        warning "Cloud Run service '$service_name' not found"
        return
    fi

    # Get service details
    log "Retrieving service details..."
    local service_url=$(gcloud run services describe "$service_name" --region="$region" --project="$project_id" --format='value(status.url)' 2>/dev/null)

    if [[ ! -z "$service_url" ]]; then
        success "Service URL: $service_url"

        # Check service accessibility
        log "Checking service accessibility..."
        if curl -s -o /dev/null -w "%{http_code}" "$service_url" | grep -q "200\|404\|500"; then
            success "Service is accessible"
        else
            error "Service is not accessible"
        fi
    fi

    # Check service traffic
    log "Checking service traffic configuration..."
    local traffic=$(gcloud run services describe "$service_name" --region="$region" --project="$project_id" --format='value(status.traffic[0].percent)' 2>/dev/null)
    if [[ ! -z "$traffic" ]]; then
        success "Traffic split configured: $traffic%"
    fi

    # Check revisions
    log "Checking service revisions..."
    local revision_count=$(gcloud run revisions list --service="$service_name" --region="$region" --project="$project_id" --format='value(metadata.name)' 2>/dev/null | wc -l)
    success "Found $revision_count revisions"
}

# HTTP health checks
validate_health_checks() {
    log "\n${BLUE}=== HEALTH CHECKS ===${NC}"

    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        warning "curl not found, skipping health checks"
        return
    fi

    local endpoints=(
        "http://localhost:8080/resourceService/"
    )

    for endpoint in "${endpoints[@]}"; do
        log "Checking endpoint: $endpoint"

        if curl -s -m 10 -o /dev/null -w "%{http_code}" "$endpoint" &>/dev/null; then
            local status=$(curl -s -m 10 -o /dev/null -w "%{http_code}" "$endpoint" 2>/dev/null)

            if [[ "$status" == "200" || "$status" == "204" ]]; then
                success "Endpoint is healthy (HTTP $status)"
            else
                warning "Endpoint returned HTTP $status"
            fi
        else
            warning "Cannot reach endpoint: $endpoint"
        fi
    done
}

# Database validation
validate_database() {
    log "\n${BLUE}=== DATABASE VALIDATION ===${NC}"

    # Check if psql is available
    if ! command -v psql &> /dev/null; then
        warning "psql not found, skipping database validation"
        return
    fi

    local db_host="${DB_HOST:-localhost}"
    local db_port="${DB_PORT:-5432}"
    local db_user="${DB_USER:-postgres}"
    local db_name="${DB_NAME:-yawl}"

    # Test connection
    log "Testing database connection..."
    if PGPASSWORD="${DB_PASSWORD:-yawl-secure-password}" psql -h "$db_host" -p "$db_port" -U "$db_user" -d "$db_name" -c "SELECT 1;" &>/dev/null; then
        success "Database connection successful"

        # Check table count
        log "Checking tables..."
        local table_count=$(PGPASSWORD="${DB_PASSWORD:-yawl-secure-password}" psql -h "$db_host" -p "$db_port" -U "$db_user" -d "$db_name" -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null || echo "0")

        if [[ "$table_count" -gt 0 ]]; then
            success "Found $table_count tables"
        else
            warning "No tables found in database"
        fi

        # Check database size
        log "Checking database size..."
        local db_size=$(PGPASSWORD="${DB_PASSWORD:-yawl-secure-password}" psql -h "$db_host" -p "$db_port" -U "$db_user" -d "$db_name" -t -c "SELECT pg_size_pretty(pg_database_size(current_database()));" 2>/dev/null)
        success "Database size: $db_size"
    else
        warning "Cannot connect to database at $db_host:$db_port"
    fi
}

# System resource checks
validate_system_resources() {
    log "\n${BLUE}=== SYSTEM RESOURCES ===${NC}"

    # Check available disk space
    log "Checking disk space..."
    local available=$(df /home/user/yawl | tail -1 | awk '{print $4}')
    if [[ $available -gt 1000000 ]]; then
        success "Sufficient disk space available: $(numfmt --to=iec $available 2>/dev/null || echo "$available KB")"
    else
        error "Low disk space: $(numfmt --to=iec $available 2>/dev/null || echo "$available KB")"
    fi

    # Check available memory
    if command -v free &> /dev/null; then
        log "Checking available memory..."
        local available_mem=$(free -k | grep Mem: | awk '{print $7}')
        if [[ $available_mem -gt 1000000 ]]; then
            success "Sufficient memory available: $(numfmt --to=iec $available_mem 2>/dev/null || echo "$available_mem KB")"
        else
            warning "Low available memory: $(numfmt --to=iec $available_mem 2>/dev/null || echo "$available_mem KB")"
        fi
    fi

    # Check CPU count
    if command -v nproc &> /dev/null; then
        log "Checking CPU count..."
        local cpu_count=$(nproc)
        if [[ $cpu_count -ge 2 ]]; then
            success "CPU count: $cpu_count"
        else
            warning "CPU count: $cpu_count (minimum recommended: 2)"
        fi
    fi
}

# Configuration validation
validate_configuration() {
    log "\n${BLUE}=== CONFIGURATION VALIDATION ===${NC}"

    # Check Kubernetes manifests
    if [[ -d "/home/user/yawl/k8s" ]]; then
        log "Validating Kubernetes manifests..."
        for manifest in /home/user/yawl/k8s/*.yaml; do
            if [[ -f "$manifest" ]]; then
                debug "Checking $manifest"
                if grep -q "^apiVersion:" "$manifest"; then
                    success "Valid manifest: $(basename $manifest)"
                else
                    error "Invalid manifest: $(basename $manifest)"
                fi
            fi
        done
    fi

    # Check Helm charts
    if [[ -d "/home/user/yawl/helm" ]]; then
        log "Validating Helm chart..."
        if [[ -f "/home/user/yawl/helm/Chart.yaml" ]]; then
            success "Helm Chart.yaml found"
        else
            error "Helm Chart.yaml not found"
        fi
    fi

    # Check Docker configuration
    if [[ -f "/home/user/yawl/Dockerfile" ]]; then
        log "Found Dockerfile"
        success "Dockerfile exists"
    fi
}

# Generate report
generate_report() {
    local total=$((PASSED + FAILED + SKIPPED))
    local pass_percent=$((PASSED * 100 / total))

    {
        echo "================================================================================"
        echo "YAWL DEPLOYMENT VALIDATION REPORT"
        echo "Generated: $(date)"
        echo "================================================================================"
        echo ""
        echo "SUMMARY"
        echo "--------"
        echo "Total Checks:  $total"
        echo "Passed:        $PASSED ($pass_percent%)"
        echo "Failed:        $FAILED"
        echo "Skipped:       $SKIPPED"
        echo ""
        echo "STATUS: $([ $FAILED -eq 0 ] && echo 'PASSED' || echo 'FAILED')"
        echo "================================================================================"
    } | tee "$REPORT_FILE"

    log "\n${BLUE}Report saved to: $REPORT_FILE${NC}"
}

# Main execution
main() {
    log "${BLUE}========================================${NC}"
    log "${BLUE}YAWL DEPLOYMENT VALIDATION${NC}"
    log "${BLUE}========================================${NC}"
    log "Platform: $PLATFORM"
    log "Verbose: $VERBOSE"
    log "Log file: $LOG_FILE"

    case "${PLATFORM,,}" in
        kubernetes|k8s)
            validate_kubernetes
            ;;
        docker-compose|compose)
            validate_docker_compose
            ;;
        cloud-run)
            validate_cloud_run
            ;;
        auto)
            validate_kubernetes
            validate_docker_compose
            validate_cloud_run
            ;;
        *)
            error "Unknown platform: $PLATFORM"
            exit 1
            ;;
    esac

    validate_health_checks
    validate_database
    validate_system_resources
    validate_configuration

    echo ""
    generate_report
    echo ""

    if [[ $FAILED -eq 0 ]]; then
        log "${GREEN}✓ Deployment validation PASSED${NC}"
        exit 0
    else
        log "${RED}✗ Deployment validation FAILED${NC}"
        exit 1
    fi
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --platform)
            PLATFORM="$2"
            shift 2
            ;;
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --help|-h)
            cat << EOF
Usage: $0 [OPTIONS]

Options:
    --platform PLATFORM    Target platform (kubernetes, docker-compose, cloud-run, auto)
    --verbose, -v         Enable verbose output
    --help, -h            Show this help message

Environment Variables:
    K8S_NAMESPACE          Kubernetes namespace (default: yawl)
    GCP_PROJECT_ID         GCP project ID
    GCP_REGION             GCP region (default: us-central1)
    CLOUD_RUN_SERVICE      Cloud Run service name (default: yawl-workflow)
    DB_HOST                Database host (default: localhost)
    DB_PORT                Database port (default: 5432)
    DB_USER                Database user (default: postgres)
    DB_NAME                Database name (default: yawl)
    DB_PASSWORD            Database password

EOF
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            exit 1
            ;;
    esac
done

main
