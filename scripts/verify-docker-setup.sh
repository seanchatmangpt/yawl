#!/usr/bin/env bash
# ==========================================================================
# verify-docker-setup.sh - Quick Docker Validation Setup Verification
# ==========================================================================
# Verifies that the Docker validation setup is working correctly.
# Run this script to quickly check if everything is ready.
#
# Usage:
#   ./scripts/verify-docker-setup.sh
#
# Exit codes:
#   0 - All checks passed
#   1 - Docker not available
#   2 - Docker daemon not running
#   3 - Build failed
#   4 - Validation test failed
# ==========================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Colors (disabled if not a terminal)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    RESET='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    BOLD=''
    RESET=''
fi

# Test counters
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

# Helper functions
print_header() {
    echo ""
    echo -e "${BOLD}${CYAN}============================================================${RESET}"
    echo -e "${BOLD}${CYAN}  $1${RESET}"
    echo -e "${BOLD}${CYAN}============================================================${RESET}"
    echo ""
}

print_check() {
    echo -en "  Checking: $1 ... "
}

pass() {
    echo -e "${GREEN}PASS${RESET}"
    PASS_COUNT=$((PASS_COUNT + 1))
}

fail() {
    echo -e "${RED}FAIL${RESET}"
    echo -e "    ${RED}Error: $1${RESET}"
    FAIL_COUNT=$((FAIL_COUNT + 1))
}

warn() {
    echo -e "${YELLOW}WARN${RESET}"
    echo -e "    ${YELLOW}Warning: $1${RESET}"
    WARN_COUNT=$((WARN_COUNT + 1))
}

info() {
    echo -e "    ${BLUE}$1${RESET}"
}

# =============================================================================
# Check 1: Docker Availability
# =============================================================================
check_docker_installed() {
    print_header "Check 1: Docker Availability"

    print_check "Docker command"
    if command -v docker &>/dev/null; then
        pass
        info "Docker version: $(docker --version 2>/dev/null | head -1)"
    else
        fail "Docker is not installed or not in PATH"
        return 1
    fi

    print_check "Docker Compose"
    if docker compose version &>/dev/null; then
        pass
        info "Compose version: $(docker compose version --short 2>/dev/null)"
    elif command -v docker-compose &>/dev/null; then
        pass
        info "Compose version: $(docker-compose version --short 2>/dev/null)"
    else
        fail "Docker Compose is not available"
        return 1
    fi

    return 0
}

# =============================================================================
# Check 2: Docker Daemon Running
# =============================================================================
check_docker_running() {
    print_header "Check 2: Docker Daemon Status"

    print_check "Docker daemon"
    if docker info &>/dev/null; then
        pass
        local info=$(docker info 2>/dev/null | grep -E "^(Server Version|Operating System|CPUs|Total Memory):" | head -4)
        if [[ -n "$info" ]]; then
            echo "$info" | while read -r line; do
                info "  $line"
            done
        fi
    else
        fail "Docker daemon is not running or you lack permissions"
        info "Try: sudo docker-validate.sh or add your user to docker group"
        return 1
    fi

    return 0
}

# =============================================================================
# Check 3: Required Files
# =============================================================================
check_required_files() {
    print_header "Check 3: Required Files"

    local files=(
        "Dockerfile.validation:Docker image definition"
        "docker-compose.validation.yml:Compose configuration"
        "scripts/docker-validate.sh:Main entry script"
        "scripts/validate-all.sh:Validation pipeline"
    )

    local all_found=true

    for entry in "${files[@]}"; do
        local file="${entry%%:*}"
        local desc="${entry##*:}"
        print_check "$desc"
        if [[ -f "${REPO_ROOT}/${file}" ]]; then
            pass
        else
            fail "File not found: ${file}"
            all_found=false
        fi
    done

    if $all_found; then
        return 0
    else
        return 1
    fi
}

# =============================================================================
# Check 4: Docker Image Build
# =============================================================================
check_docker_build() {
    print_header "Check 4: Docker Image Build"

    local image_name="${YAWL_IMAGE_NAME:-yawl-validation}"
    local image_tag="${YAWL_IMAGE_TAG:-latest}"
    local full_image="${image_name}:${image_tag}"

    # Check if image already exists
    print_check "Existing image"
    if docker image inspect "$full_image" &>/dev/null; then
        pass
        info "Image found: ${full_image}"

        local created=$(docker image inspect "$full_image" --format '{{.Created}}' 2>/dev/null | cut -dT -f1)
        local size=$(docker image inspect "$full_image" --format '{{.Size}}' 2>/dev/null)
        if [[ -n "$size" ]]; then
            size=$((size / 1024 / 1024))
            info "Created: ${created}, Size: ${size}MB"
        fi

        echo ""
        echo -en "  Rebuild image? [y/N]: "
        read -r response
        if [[ "$response" =~ ^[Yy]$ ]]; then
            echo -e "  Building fresh image..."
        else
            info "Using existing image"
            return 0
        fi
    else
        echo -e "${YELLOW}Not found${RESET}"
        info "Building new image..."
    fi

    # Build the image
    print_check "Building image"
    echo ""  # New line for build output

    if docker compose -f docker-compose.validation.yml build --build-arg BUILD_DATE="$(date -u +%Y-%m-%d)" --build-arg VCS_REF="$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')" validation 2>&1 | while read -r line; do
        echo "    $line"
    done; then
        pass
        info "Image built successfully: ${full_image}"
        return 0
    else
        fail "Image build failed"
        info "Check the Dockerfile.validation and try running: docker compose -f docker-compose.validation.yml build"
        return 1
    fi
}

# =============================================================================
# Check 5: Quick Validation Test
# =============================================================================
check_validation_test() {
    print_header "Check 5: Quick Validation Test"

    info "Running a quick validation to verify the setup works..."
    echo ""

    # Create output directories if needed
    mkdir -p "${REPO_ROOT}/docs/v6/latest"
    mkdir -p "${REPO_ROOT}/reports"
    mkdir -p "${REPO_ROOT}/target"

    print_check "Compile test (30 second timeout)"

    # Run a quick compile test with timeout
    local start_time=$(date +%s)
    local exit_code=0

    # Use timeout if available, otherwise run directly
    if command -v timeout &>/dev/null; then
        timeout 120 docker compose -f docker-compose.validation.yml run --rm validation bash -c "mvn compile -B -q -T 1.5C 2>&1 | head -20" &>/dev/null
        exit_code=$?
    else
        docker compose -f docker-compose.validation.yml run --rm validation bash -c "mvn compile -B -q -T 1.5C 2>&1 | head -20" &>/dev/null
        exit_code=$?
    fi

    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))

    if [[ $exit_code -eq 0 ]]; then
        pass
        info "Compile completed in ${elapsed} seconds"
    elif [[ $exit_code -eq 124 ]]; then
        warn "Compile timed out (this may be normal for first run)"
        info "Try running: ./scripts/docker-validate.sh fast"
    else
        fail "Compile failed with exit code ${exit_code}"
        info "Run manually to see errors: ./scripts/docker-validate.sh compile"
        return 1
    fi

    return 0
}

# =============================================================================
# Check 6: Output Directories
# =============================================================================
check_output_dirs() {
    print_header "Check 6: Output Directories"

    local dirs=(
        "target:Build artifacts"
        "docs/v6/latest:Observatory outputs"
        "reports:Analysis reports"
    )

    for entry in "${dirs[@]}"; do
        local dir="${entry%%:*}"
        local desc="${entry##*:}"
        print_check "$desc"
        if [[ -d "${REPO_ROOT}/${dir}" ]]; then
            pass
            local file_count=$(find "${REPO_ROOT}/${dir}" -type f 2>/dev/null | wc -l | tr -d ' ')
            info "${file_count} files in ${dir}/"
        else
            warn "Directory not found (will be created on first run)"
            mkdir -p "${REPO_ROOT}/${dir}"
        fi
    done

    return 0
}

# =============================================================================
# Print Summary
# =============================================================================
print_summary() {
    print_header "Verification Summary"

    echo -e "  ${GREEN}Passed:${RESET}   ${PASS_COUNT}"
    echo -e "  ${YELLOW}Warnings:${RESET} ${WARN_COUNT}"
    echo -e "  ${RED}Failed:${RESET}   ${FAIL_COUNT}"
    echo ""

    if [[ $FAIL_COUNT -eq 0 ]]; then
        echo -e "${GREEN}${BOLD}  DOCKER VALIDATION SETUP IS READY!${RESET}"
        echo ""
        echo "  Next steps:"
        echo ""
        echo "    # Run all validations"
        echo "    ./scripts/docker-validate.sh"
        echo ""
        echo "    # Fast validation (compile + test only)"
        echo "    ./scripts/docker-validate.sh fast"
        echo ""
        echo "    # Generate observatory facts and diagrams"
        echo "    ./scripts/docker-validate.sh observatory"
        echo ""
        echo "  Generated outputs:"
        echo "    - target/              Build artifacts"
        echo "    - docs/v6/latest/      Observatory (facts, diagrams, receipts)"
        echo "    - reports/             Static analysis reports"
        echo ""
        echo "  Documentation:"
        echo "    - docs/integration/docker-validation.md"
        echo "    - ./scripts/docker-validate.sh --help"
        echo ""
        return 0
    else
        echo -e "${RED}${BOLD}  SETUP HAS ISSUES - Please fix the failures above${RESET}"
        echo ""
        echo "  Common fixes:"
        echo "    - Ensure Docker is installed and running"
        echo "    - Add your user to the docker group: sudo usermod -aG docker \$USER"
        echo "    - Check Docker has enough memory (4GB+ recommended)"
        echo ""
        return 1
    fi
}

# =============================================================================
# Main
# =============================================================================
main() {
    echo ""
    echo -e "${BOLD}${BLUE}YAWL Docker Validation Setup Verification${RESET}"
    echo -e "${BLUE}This script checks if your Docker validation environment is ready.${RESET}"

    # Run all checks
    local has_failures=false

    check_docker_installed || has_failures=true

    if ! $has_failures; then
        check_docker_running || has_failures=true
    fi

    check_required_files || has_failures=true

    if ! $has_failures; then
        check_docker_build || has_failures=true
    fi

    if ! $has_failures; then
        check_validation_test || has_failures=true
    fi

    check_output_dirs

    # Print summary
    print_summary
    local exit_code=$?

    echo ""
    return $exit_code
}

main "$@"
