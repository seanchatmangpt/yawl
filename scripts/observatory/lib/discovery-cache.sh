#!/usr/bin/env bash
# ==========================================================================
# discovery-cache.sh — Batched File Discovery for Observatory
#
# Caches file discovery results to avoid redundant find operations.
# All functions return cached results after first call.
#
# Usage:
#   source lib/discovery-cache.sh
#   java_files=$(get_all_java_files)
#   pom_files=$(get_all_pom_files)
# ==========================================================================

# ── Cache variables ────────────────────────────────────────────────────────
_DISCOVERY_JAVA_FILES=""
_DISCOVERY_POM_FILES=""
_DISCOVERY_TEST_FILES=""
_DISCOVERY_RESOURCE_FILES=""

# ── Get all Java source files (cached) ─────────────────────────────────────
get_all_java_files() {
    if [[ -z "$_DISCOVERY_JAVA_FILES" ]]; then
        _DISCOVERY_JAVA_FILES=$(find "$REPO_ROOT" -name "*.java" -type f 2>/dev/null | grep -v target | grep -v node_modules)
    fi
    echo "$_DISCOVERY_JAVA_FILES"
}

# ── Get all POM files (cached) ─────────────────────────────────────────────
get_all_pom_files() {
    if [[ -z "$_DISCOVERY_POM_FILES" ]]; then
        _DISCOVERY_POM_FILES=$(find "$REPO_ROOT" -name "pom.xml" -type f 2>/dev/null | grep -v target)
    fi
    echo "$_DISCOVERY_POM_FILES"
}

# ── Get all test files (cached) ────────────────────────────────────────────
get_all_test_files() {
    if [[ -z "$_DISCOVERY_TEST_FILES" ]]; then
        _DISCOVERY_TEST_FILES=$(find "$REPO_ROOT" -path "*/test/*" -name "*.java" -type f 2>/dev/null | grep -v target)
    fi
    echo "$_DISCOVERY_TEST_FILES"
}

# ── Get all resource files (cached) ────────────────────────────────────────
get_all_resource_files() {
    if [[ -z "$_DISCOVERY_RESOURCE_FILES" ]]; then
        _DISCOVERY_RESOURCE_FILES=$(find "$REPO_ROOT" -path "*/resources/*" -type f 2>/dev/null | grep -v target | grep -v node_modules)
    fi
    echo "$_DISCOVERY_RESOURCE_FILES"
}

# ── Count files by extension in a directory ────────────────────────────────
count_files_by_ext() {
    local dir="$1"
    local ext="$2"
    find "$dir" -name "*.${ext}" -type f 2>/dev/null | grep -v target | wc -l | tr -d ' '
}

# ── Parallel discovery - warm all caches at once ───────────────────────────
parallel_discover_all() {
    log_info "Warming discovery caches..."

    # Run all discovery in parallel using background jobs
    (
        _DISCOVERY_JAVA_FILES=$(find "$REPO_ROOT" -name "*.java" -type f 2>/dev/null | grep -v target | grep -v node_modules)
        echo "$_DISCOVERY_JAVA_FILES" > /tmp/yawl-discovery-java-$$
    ) &
    PID_JAVA=$!

    (
        _DISCOVERY_POM_FILES=$(find "$REPO_ROOT" -name "pom.xml" -type f 2>/dev/null | grep -v target)
        echo "$_DISCOVERY_POM_FILES" > /tmp/yawl-discovery-pom-$$
    ) &
    PID_POM=$!

    (
        _DISCOVERY_TEST_FILES=$(find "$REPO_ROOT" -path "*/test/*" -name "*.java" -type f 2>/dev/null | grep -v target)
        echo "$_DISCOVERY_TEST_FILES" > /tmp/yawl-discovery-test-$$
    ) &
    PID_TEST=$!

    (
        _DISCOVERY_RESOURCE_FILES=$(find "$REPO_ROOT" -path "*/resources/*" -type f 2>/dev/null | grep -v target | grep -v node_modules)
        echo "$_DISCOVERY_RESOURCE_FILES" > /tmp/yawl-discovery-resource-$$
    ) &
    PID_RESOURCE=$!

    # Wait for all background jobs
    wait $PID_JAVA $PID_POM $PID_TEST $PID_RESOURCE

    # Read cached results
    [[ -f /tmp/yawl-discovery-java-$$ ]] && _DISCOVERY_JAVA_FILES=$(cat /tmp/yawl-discovery-java-$$)
    [[ -f /tmp/yawl-discovery-pom-$$ ]] && _DISCOVERY_POM_FILES=$(cat /tmp/yawl-discovery-pom-$$)
    [[ -f /tmp/yawl-discovery-test-$$ ]] && _DISCOVERY_TEST_FILES=$(cat /tmp/yawl-discovery-test-$$)
    [[ -f /tmp/yawl-discovery-resource-$$ ]] && _DISCOVERY_RESOURCE_FILES=$(cat /tmp/yawl-discovery-resource-$$)

    # Cleanup temp files
    rm -f /tmp/yawl-discovery-java-$$ /tmp/yawl-discovery-pom-$$ /tmp/yawl-discovery-test-$$ /tmp/yawl-discovery-resource-$$

    local java_count pom_count test_count
    java_count=$(echo "$_DISCOVERY_JAVA_FILES" | grep -c . 2>/dev/null || echo "0")
    pom_count=$(echo "$_DISCOVERY_POM_FILES" | grep -c . 2>/dev/null || echo "0")
    test_count=$(echo "$_DISCOVERY_TEST_FILES" | grep -c . 2>/dev/null || echo "0")

    log_ok "Discovery cache warmed: ${java_count} Java, ${pom_count} POMs, ${test_count} tests"
}

# ── Clear all caches ───────────────────────────────────────────────────────
clear_discovery_cache() {
    _DISCOVERY_JAVA_FILES=""
    _DISCOVERY_POM_FILES=""
    _DISCOVERY_TEST_FILES=""
    _DISCOVERY_RESOURCE_FILES=""
}
