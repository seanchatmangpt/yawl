#!/bin/bash

# Update the validation script to fix the virtual threads check

# Replace the virtual threads check
sed -i '' 's/UseVirtualThreadPerTaskExecutor/Executors.newVirtualThreadPerTaskExecutor/g' validate-memory-benchmarks.sh

echo "Validation script updated to check for proper virtual thread usage"
