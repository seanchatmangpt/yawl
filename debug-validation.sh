#!/bin/bash

# Debug script for YAWL database validation

DB_CONTAINER="yawl-postgres"
DB_NAME="yawl"
DB_USER="yawl"

db_query() {
    local sql="$1"
    echo "DEBUG: Executing: $sql"
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$sql"
}

# Test persistence with debug
echo "=== Testing Database Persistence ==="

test_case_id="TEST-PERSISTENCE-$(date +%s)"
echo "Test case ID: $test_case_id"

# Insert
db_query "INSERT INTO yawl_case (case_id, specification_id, status) VALUES ('$test_case_id', 'TEST-SPEC', 'active');"

# Verify exists
result=$(db_query "SELECT COUNT(*) FROM yawl_case WHERE case_id = '$test_case_id';")
echo "Count result: '$result'"

if [ "$result" -gt 0 ] 2>/dev/null; then
    echo "✅ Case exists"
else
    echo "❌ Case not found"
fi

# Check status
status=$(db_query "SELECT status FROM yawl_case WHERE case_id = '$test_case_id';")
echo "Status: '$status'"

# Clean up
db_query "DELETE FROM yawl_case WHERE case_id = '$test_case_id';"
