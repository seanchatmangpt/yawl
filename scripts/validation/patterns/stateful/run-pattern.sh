#!/bin/bash

# Run a single YAWL workflow pattern (stateful with PostgreSQL)
# Usage: bash scripts/validation/patterns/stateful/run-pattern.sh <pattern_id>

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

# Check pattern ID
if [ $# -ne 1 ]; then
    log_error "Usage: $0 <pattern_id>"
    exit 1
fi

PATTERN_ID="$1"
PATTERN_FUNC="test_${PATTERN_ID//-/_}_stateful"

log_section "YAWL Pattern Validation - Stateful"
echo "Testing pattern: $PATTERN_ID"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Check if pattern function exists
if ! declare -f "$PATTERN_FUNC" > /dev/null; then
    log_error "Pattern function not found: $PATTERN_FUNC"
    yawl_disconnect
    db_cleanup_test_data
    exit 1
fi

# Run pattern test
start_time=$(date +%s.%3N)

if $PATTERN_FUNC; then
    duration=$(get_duration "$start_time")
    result="{\"pattern\": \"$PATTERN_ID\", \"status\": \"PASS\", \"duration_ms\": $(echo "$duration * 1000" | bc), \"database_verified\": true}"
else
    duration=$(get_duration "$start_time")
    result="{\"pattern\": \"$PATTERN_ID\", \"status\": \"FAIL\", \"duration_ms\": $(echo "$duration * 1000" | bc), \"database_verified\": false}"
fi

# Cleanup
yawl_disconnect
db_cleanup_test_data

# Output JSON
echo "$result"

# Return appropriate exit code
if echo "$result" | grep -q '"status": "FAIL"'; then
    exit 1
else
    exit 0
fi