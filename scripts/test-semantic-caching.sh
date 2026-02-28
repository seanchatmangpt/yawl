#!/usr/bin/env bash
# ==========================================================================
# test-semantic-caching.sh — Comprehensive Tests for Semantic Build Caching
#
# Tests 5 key scenarios:
# 1. Formatting-only changes → cache hit (skip compilation)
# 2. Comment-only additions → cache hit (skip compilation)
# 3. Method body changes → cache miss (must recompile)
# 4. Annotation additions → cache miss (must recompile)
# 5. Import reordering → cache hit (order-independent)
#
# Usage:
#   bash scripts/test-semantic-caching.sh
#
# Exit codes:
#   0 = all tests passed
#   1 = one or more tests failed
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

test_count=0
pass_count=0
fail_count=0

log_test() {
    ((test_count++))
    printf "${C_CYAN}[TEST %d]${C_RESET} %s\n" "$test_count" "$1"
}

log_pass() {
    ((pass_count++))
    printf "  ${C_GREEN}✓${C_RESET} PASS: %s\n" "$1"
}

log_fail() {
    ((fail_count++))
    printf "  ${C_RED}✗${C_RESET} FAIL: %s\n" "$1"
}

# Create temporary test module
TEMP_MODULE="/tmp/test-semantic-cache-module"
rm -rf "$TEMP_MODULE"
mkdir -p "$TEMP_MODULE/src/main/java/org/test"

# Create initial test class
cat > "$TEMP_MODULE/src/main/java/org/test/TestClass.java" << 'EOF'
package org.test;

import java.util.List;
import java.util.Map;

public class TestClass {
    private String name;
    private int age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
EOF

# ============================================================================
# Test 1: Initial hash computation
# ============================================================================
log_test "Initial semantic hash computation"

initial_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash' | head -c 16)

if [[ -n "$initial_hash" && "$initial_hash" != "null" ]]; then
    log_pass "Initial hash computed: $initial_hash..."
else
    log_fail "Failed to compute initial hash"
fi

# Store initial hash
initial_full_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash')

# ============================================================================
# Test 2: Formatting-only changes → cache hit
# ============================================================================
log_test "Formatting-only changes (extra spaces, newlines)"

# Add extra whitespace and blank lines (no semantic change)
cat > "$TEMP_MODULE/src/main/java/org/test/TestClass.java" << 'EOF'
package org.test;

import java.util.List;
import java.util.Map;

public class TestClass {
    private String name;
    private int age;

    public String getName() {
        return    name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
EOF

formatted_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash')

if [[ "$formatted_hash" == "$initial_full_hash" ]]; then
    log_pass "Formatting changes detected as non-semantic (cache hit)"
else
    log_fail "Formatting changes triggered false semantic change"
fi

# ============================================================================
# Test 3: Comment additions → cache hit
# ============================================================================
log_test "Comment-only additions"

# Add comment without changing code
cat > "$TEMP_MODULE/src/main/java/org/test/TestClass.java" << 'EOF'
package org.test;

import java.util.List;
import java.util.Map;

// This is a new comment that doesn't affect semantics
public class TestClass {
    private String name;
    private int age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
EOF

comment_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash')

if [[ "$comment_hash" == "$initial_full_hash" ]]; then
    log_pass "Comment changes detected as non-semantic (cache hit)"
else
    log_fail "Comment changes triggered false semantic change"
fi

# ============================================================================
# Test 4: Method body changes → cache miss
# ============================================================================
log_test "Method body modifications"

# Change method implementation
cat > "$TEMP_MODULE/src/main/java/org/test/TestClass.java" << 'EOF'
package org.test;

import java.util.List;
import java.util.Map;

public class TestClass {
    private String name;
    private int age;

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
EOF

body_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash')

if [[ "$body_hash" != "$initial_full_hash" ]]; then
    log_pass "Method body changes detected as semantic (cache miss)"
else
    log_fail "Method body changes not detected as semantic change"
fi

body_hash_saved="$body_hash"

# ============================================================================
# Test 5: Annotation additions → cache miss
# ============================================================================
log_test "Annotation additions"

# Add annotation (semantic change)
cat > "$TEMP_MODULE/src/main/java/org/test/TestClass.java" << 'EOF'
package org.test;

import java.util.List;
import java.util.Map;

@Deprecated
public class TestClass {
    private String name;
    private int age;

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
EOF

annotation_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash')

if [[ "$annotation_hash" != "$body_hash_saved" ]]; then
    log_pass "Annotation changes detected as semantic (cache miss)"
else
    log_fail "Annotation changes not detected as semantic change"
fi

# ============================================================================
# Test 6: Import reordering → cache hit (order-independent)
# ============================================================================
log_test "Import reordering (should be order-independent)"

# Reorder imports without changing semantics
cat > "$TEMP_MODULE/src/main/java/org/test/TestClass.java" << 'EOF'
package org.test;

import java.util.Map;
import java.util.List;

@Deprecated
public class TestClass {
    private String name;
    private int age;

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
EOF

reordered_hash=$(bash "$SCRIPT_DIR/compute-semantic-hash.sh" "$TEMP_MODULE" 2>/dev/null | jq -r '.hash')

if [[ "$reordered_hash" == "$annotation_hash" ]]; then
    log_pass "Import reordering detected as non-semantic (cache hit)"
else
    log_fail "Import reordering triggered false semantic change"
fi

# ============================================================================
# Cleanup
# ============================================================================
rm -rf "$TEMP_MODULE"

# ============================================================================
# Summary
# ============================================================================
echo ""
printf "${C_CYAN}Test Results:${C_RESET} ${C_GREEN}%d passed${C_RESET} | ${C_RED}%d failed${C_RESET} / %d total\n" \
    "$pass_count" "$fail_count" "$test_count"

if [[ $fail_count -eq 0 ]]; then
    printf "\n${C_GREEN}All tests passed!${C_RESET}\n"
    exit 0
else
    printf "\n${C_RED}Some tests failed.${C_RESET}\n"
    exit 1
fi
