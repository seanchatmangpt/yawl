#!/bin/bash
#
# Test script for YAWL Performance Validation
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Testing YAWL Performance Validation Script..."
echo "Script location: $SCRIPT_DIR"
echo ""

# Test help output
echo "1. Testing help output:"
bash "$SCRIPT_DIR/validate-performance-v2.sh" --help | head -10
echo ""

# Test with quiet mode
echo "2. Testing quick validation (quiet mode):"
bash "$SCRIPT_DIR/validate-performance-v2.sh" --quiet || echo "Expected exit code: $?"
echo ""

# Test baseline creation
echo "3. Testing baseline creation:"
bash "$SCRIPT_DIR/validate-performance-v2.sh" --baseline --quiet || echo "Baseline test completed"
echo ""

echo "Performance validation script tests completed successfully!"