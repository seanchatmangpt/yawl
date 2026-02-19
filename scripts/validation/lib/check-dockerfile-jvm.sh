#!/bin/bash
#
# Dockerfile JVM Flags Validation Script
# Checks Dockerfiles for required JVM flags and Java 25 features
#

# Source common utilities
source "$(dirname "$0")/common.sh"

log_section "Checking Dockerfile JVM Flags"

# Required JVM flags for Java 25
readonly REQUIRED_FLAGS=(
    "-XX:+UseContainerSupport"
    "-XX:+UseZGC"
    "-XX:+ZGenerational"
    "-XX:MaxRAMPercentage=75.0"
    "-XX:+UseCompactObjectHeaders"
)

# Production-specific flags (for Dockerfile.prod, Dockerfile.engine)
readonly PROD_FLAGS=(
    "-XX:+UseStringDeduplication"
    "-XX:+UseAOTCache"
)

# Files to check
declare -a dockerfiles
while IFS= read -r -d '' file; do
    dockerfiles+=("$file")
done < <(find . -name "Dockerfile*" -type f ! -path "./docker/base/*" ! -path "./node_modules/*" -print0)

# Check function
check_dockerfile() {
    local file=$1
    local filename=$(basename "$file")
    local required_flags=("${REQUIRED_FLAGS[@]}")

    # Add production flags if it's a production Dockerfile
    if [[ "$filename" == "Dockerfile" || "$filename" == "Dockerfile.prod" || "$filename" == "Dockerfile.engine" ]]; then
        required_flags+=("${PROD_FLAGS[@]}")
    fi

    local missing_flags=()
    local found_flags=()

    # Read Dockerfile content
    local content
    content=$(cat "$file" 2>/dev/null || echo "")

    # Check each required flag
    for flag in "${required_flags[@]}"; do
        if echo "$content" | grep -q "$flag"; then
            found_flags+=("$flag")
        else
            missing_flags+=("$flag")
        fi
    done

    # Log results
    if [[ ${#missing_flags[@]} -eq 0 ]]; then
        log_test "PASS" "All required JVM flags found in $filename" "dockerfile-jvm-$filename"
        return 0
    else
        log_test "FAIL" "Missing flags: ${missing_flags[*]} in $filename" "dockerfile-jvm-$filename"
        return 1
    fi
}

# Check all Dockerfiles
all_passed=true
for file in "${dockerfiles[@]}"; do
    if ! check_dockerfile "$file"; then
        all_passed=false
    fi
done

# Additional validation
log_section "Additional JVM Checks"

# Check for deprecated flags
deprecated_flags=(
    "-XX:+UseG1GC"
    "-XX:+UseParallelGC"
    "-XX:+UseSerialGC"
    "-XX:+UseConcMarkSweepGC"
    "-XX:+UseParallelOldGC"
)

for file in "${dockerfiles[@]}"; do
    local content
    content=$(cat "$file" 2>/dev/null || echo "")
    local deprecated_found=()

    for flag in "${deprecated_flags[@]}"; do
        if echo "$content" | grep -q "$flag"; then
            deprecated_found+=("$flag")
        fi
    done

    if [[ ${#deprecated_found[@]} -gt 0 ]]; then
        log_test "WARN" "Deprecated flags found: ${deprecated_found[*]}" "dockerfile-deprecated-$(basename "$file")"
    fi
done

# Check for Java 25 specific features
for file in "${dockerfiles[@]}"; do
    local content
    content=$(cat "$file" 2>/dev/null || echo "")

    # Check for virtual threads configuration
    if echo "$content" | grep -q "virtualThread" || echo "$content" | grep -q "jdk.virtualThread"; then
        log_test "PASS" "Virtual threads configured in $(basename "$file")" "dockerfile-virtualthreads-$(basename "$file")"
    else
        # Warning only, not required for all Dockerfiles
        log_test "WARN" "Virtual threads not configured in $(basename "$file")" "dockerfile-virtualthreads-$(basename "$file")"
    fi
done

# Final result
if [[ "$all_passed" == "true" ]]; then
    log_success "All Dockerfiles have required JVM flags"
else
    log_error "Some Dockerfiles are missing required JVM flags"
    exit 1
fi

# Output JSON if requested
if [[ "$1" == "--json" ]]; then
    output_json "dockerfile-jvm-results.json"
fi