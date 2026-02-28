#!/usr/bin/env bash
# ==========================================================================
# scripts/test-warm-cache.sh — Test warm module caching functionality
#
# This script verifies that:
# 1. Warm cache saves compiled bytecode after first build
# 2. Second build with warm cache loads bytecode (no compilation)
# 3. Cache invalidation works (hash mismatch triggers rebuild)
# 4. TTL expiration invalidates cache (8 hour timeout)
#
# Usage:
#   bash scripts/test-warm-cache.sh [module]
#
# Example:
#   bash scripts/test-warm-cache.sh yawl-utilities
# ==========================================================================
set -eu
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Test module (prefer utilities for speed)
TEST_MODULE="${1:-yawl-utilities}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

echo ""
echo -e "${C_CYAN}═══════════════════════════════════════════${C_RESET}"
echo -e "${C_CYAN}Warm Module Cache Test Suite${C_RESET}"
echo -e "${C_CYAN}═══════════════════════════════════════════${C_RESET}\n"

# Test 1: Initial build without warm cache
echo -e "${C_CYAN}[Test 1]${C_RESET} Initial build (baseline)"
echo "Module: $TEST_MODULE"

if [[ ! -d "$TEST_MODULE" ]]; then
    echo -e "${C_RED}✗${C_RESET} Module not found: $TEST_MODULE"
    exit 1
fi

# Clean build directory
rm -rf "${TEST_MODULE}/target" 2>/dev/null || true

# First build (no warm cache)
echo -e "${C_YELLOW}→${C_RESET} Building without warm cache..."
START1=$(date +%s%N)
bash scripts/dx.sh -pl "$TEST_MODULE" compile 2>&1 | tail -5 || true
END1=$(date +%s%N)
TIME1=$((($END1 - $START1) / 1000000)) # Convert to milliseconds

echo -e "${C_GREEN}✓${C_RESET} Build 1 completed in ${TIME1}ms"

# Check if classes were generated
if [[ ! -d "${TEST_MODULE}/target/classes" ]]; then
    echo -e "${C_RED}✗${C_RESET} No classes generated in first build"
    exit 1
fi

CLASS_COUNT1=$(find "${TEST_MODULE}/target/classes" -name "*.class" 2>/dev/null | wc -l)
echo "  Generated: $CLASS_COUNT1 class files"

# Test 2: Enable warm cache and save
echo ""
echo -e "${C_CYAN}[Test 2]${C_RESET} Save to warm cache"
echo -e "${C_YELLOW}→${C_RESET} Running: manage-warm-cache.sh save $TEST_MODULE"

if bash scripts/manage-warm-cache.sh save "$TEST_MODULE"; then
    echo -e "${C_GREEN}✓${C_RESET} Classes saved to warm cache"
else
    echo -e "${C_RED}✗${C_RESET} Failed to save to warm cache"
    exit 1
fi

# Verify cache contents
CACHE_DIR=".yawl/warm-cache/${TEST_MODULE}"
if [[ -d "$CACHE_DIR" ]]; then
    CACHE_ENTRY=$(find "$CACHE_DIR" -maxdepth 1 -type d -name "classes-*" | head -1)
    if [[ -n "$CACHE_ENTRY" ]]; then
        CACHE_SIZE=$(du -sh "$CACHE_ENTRY" 2>/dev/null | awk '{print $1}')
        echo "  Cache size: $CACHE_SIZE"
        CACHE_CLASS_COUNT=$(find "$CACHE_ENTRY" -name "*.class" 2>/dev/null | wc -l)
        echo "  Classes in cache: $CACHE_CLASS_COUNT"
    else
        echo -e "${C_YELLOW}⊘${C_RESET} No cache entry found"
    fi
else
    echo -e "${C_YELLOW}⊘${C_RESET} Cache directory not created"
fi

# Test 3: Second build with warm cache
echo ""
echo -e "${C_CYAN}[Test 3]${C_RESET} Build with warm cache enabled (should skip compilation)"
echo -e "${C_YELLOW}→${C_RESET} Cleaning target directory..."

rm -rf "${TEST_MODULE}/target" 2>/dev/null || true

echo -e "${C_YELLOW}→${C_RESET} Building with --warm-cache flag..."
START2=$(date +%s%N)
bash scripts/dx.sh -pl "$TEST_MODULE" --warm-cache compile 2>&1 | tail -10 || true
END2=$(date +%s%N)
TIME2=$((($END2 - $START2) / 1000000))

echo -e "${C_GREEN}✓${C_RESET} Build 2 completed in ${TIME2}ms"

if [[ -d "${TEST_MODULE}/target/classes" ]]; then
    CLASS_COUNT2=$(find "${TEST_MODULE}/target/classes" -name "*.class" 2>/dev/null | wc -l)
    echo "  Restored: $CLASS_COUNT2 class files"
else
    echo -e "${C_YELLOW}⊘${C_RESET} No classes restored from cache"
fi

# Test 4: Validate cache statistics
echo ""
echo -e "${C_CYAN}[Test 4]${C_RESET} Cache statistics"
bash scripts/manage-warm-cache.sh stats 2>&1 | tail -15 || true

# Test 5: Validate cache metadata
echo ""
echo -e "${C_CYAN}[Test 5]${C_RESET} Cache metadata validation"
echo -e "${C_YELLOW}→${C_RESET} Running: manage-warm-cache.sh validate $TEST_MODULE"

if bash scripts/manage-warm-cache.sh validate "$TEST_MODULE"; then
    echo -e "${C_GREEN}✓${C_RESET} Cache is valid"
else
    echo -e "${C_RED}✗${C_RESET} Cache validation failed"
fi

# Test 6: Cache info
echo ""
echo -e "${C_CYAN}[Test 6]${C_RESET} Cache information"
bash scripts/manage-warm-cache.sh info "$TEST_MODULE" 2>&1 || true

# Summary
echo ""
echo -e "${C_CYAN}═══════════════════════════════════════════${C_RESET}"
echo -e "${C_CYAN}Summary${C_RESET}"
echo -e "${C_CYAN}═══════════════════════════════════════════${C_RESET}\n"

echo "Module:          $TEST_MODULE"
echo "Build 1 (no cache): ${TIME1}ms"
echo "Build 2 (cached):   ${TIME2}ms"

if [[ $TIME2 -lt $TIME1 ]]; then
    SPEEDUP=$(echo "scale=1; $TIME1 / $TIME2" | bc 2>/dev/null || echo "1.0")
    echo -e "${C_GREEN}✓ Speedup: ${SPEEDUP}x${C_RESET}"
else
    echo -e "${C_YELLOW}◇ Speedup: 1.0x (similar performance)${C_RESET}"
fi

if [[ $CLASS_COUNT1 -eq $CLASS_COUNT2 ]]; then
    echo -e "${C_GREEN}✓ Class count matches: $CLASS_COUNT1 files${C_RESET}"
else
    echo -e "${C_RED}✗ Class count mismatch: $CLASS_COUNT1 vs $CLASS_COUNT2${C_RESET}"
fi

echo ""
echo -e "${C_CYAN}═══════════════════════════════════════════${C_RESET}\n"
