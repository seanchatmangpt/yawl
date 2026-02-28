#!/usr/bin/env bash
# ==========================================================================
# build-test-impact-graph.sh — Test Impact Graph Builder
#
# Fast analysis of test dependencies with bidirectional mapping
#
# Output: .yawl/cache/test-impact-graph.json
#
# Usage:
#   bash scripts/build-test-impact-graph.sh
#   bash scripts/build-test-impact-graph.sh --force --verbose
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

CACHE_DIR="${REPO_ROOT}/.yawl/cache"
GRAPH_FILE="${CACHE_DIR}/test-impact-graph.json"
METADATA_FILE="${CACHE_DIR}/metadata.json"
TEMP_GRAPH="${CACHE_DIR}/.impact-graph.tmp.json"
TEMP_MAPPING="${CACHE_DIR}/.mapping.tmp"

FORCE_REBUILD="${IMPACT_FORCE:-0}"
VERBOSE="${IMPACT_VERBOSE:-0}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --force)   FORCE_REBUILD=1; shift ;;
        --verbose) VERBOSE=1; shift ;;
        -h|--help)
            echo "Usage: bash scripts/build-test-impact-graph.sh [--force] [--verbose]"
            exit 0 ;;
        *)         echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# Check if rebuild needed
should_rebuild() {
    [[ "$FORCE_REBUILD" == "1" ]] && return 0
    [[ ! -f "$GRAPH_FILE" ]] && return 0

    local now=$(date +%s)
    local mtime=$(stat -c %Y "$GRAPH_FILE" 2>/dev/null || echo "$now")
    local age=$((now - mtime))
    [[ $age -gt 86400 ]] && return 0  # older than 24h

    return 1
}

if ! should_rebuild; then
    [[ "$VERBOSE" == "1" ]] && echo "Impact graph is fresh, skipping rebuild"
    exit 0
fi

mkdir -p "$CACHE_DIR"

echo "Building test impact graph..."
[[ "$VERBOSE" == "1" ]] && echo "Scanning test files..."

# Use Python for JSON generation (much simpler and more reliable)
python3 << 'EOFPYTHON'
import json
import os
import re
from pathlib import Path
from datetime import datetime

# Configuration
repo_root = os.getcwd()
cache_dir = os.path.join(repo_root, '.yawl', 'cache')
graph_file = os.path.join(cache_dir, 'test-impact-graph.json')

test_to_source = {}
source_to_tests = {}

# Find all test files
test_files = []
for root, dirs, files in os.walk(repo_root):
    # Skip target directories and hidden dirs
    dirs[:] = [d for d in dirs if not d.startswith('.') and d != 'target']

    for file in files:
        if file.endswith('Test.java') and 'src/test/java' in root:
            test_files.append(os.path.join(root, file))

test_files.sort()
print(f"Found {len(test_files)} test files")

# Analyze each test file
for test_file in test_files:
    try:
        with open(test_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Extract package
        pkg_match = re.search(r'^\s*package\s+([\w.]+)\s*;', content, re.MULTILINE)
        if not pkg_match:
            continue

        package = pkg_match.group(1)
        class_name = os.path.basename(test_file)[:-5]  # Remove .java
        test_class = f"{package}.{class_name}"

        # Extract production class imports (org.yawlfoundation.yawl.* but not test packages)
        imports = set()
        for line in content.split('\n'):
            if re.match(r'^\s*import\s+', line):
                # Match: import org.yawlfoundation.yawl.something;
                match = re.search(r'import\s+(org\.yawlfoundation\.yawl\.[^;]+);', line)
                if match:
                    import_name = match.group(1)
                    # Skip test packages
                    if '.test.' not in import_name:
                        imports.add(import_name)

        if imports:
            test_to_source[test_class] = sorted(list(imports))

            # Build reverse mapping
            for imp in imports:
                if imp not in source_to_tests:
                    source_to_tests[imp] = []
                source_to_tests[imp].append(test_class)

    except Exception as e:
        print(f"Warning: Could not analyze {test_file}: {e}", file=__import__('sys').stderr)
        continue

# Sort source_to_tests values
for key in source_to_tests:
    source_to_tests[key].sort()

# Build final JSON
java_version = "unknown"
mvn_version = "unknown"

try:
    import subprocess
    java_version = subprocess.check_output(['java', '-version'], 
                                          stderr=subprocess.STDOUT, 
                                          text=True).split('\n')[0]
except:
    pass

try:
    import subprocess
    mvn_version = subprocess.check_output(['mvn', '--version'], 
                                         text=True).split('\n')[0]
except:
    pass

result = {
    "version": "1.0",
    "generated_at": datetime.utcnow().isoformat() + "Z",
    "metadata": {
        "test_files_analyzed": len(test_files),
        "java_version": java_version,
        "maven_version": mvn_version
    },
    "test_to_source": test_to_source,
    "source_to_tests": source_to_tests
}

# Write to temp file, then atomic move
temp_file = os.path.join(cache_dir, '.impact-graph.tmp.json')
with open(temp_file, 'w') as f:
    json.dump(result, f, indent=2)

# Rename
import shutil
shutil.move(temp_file, graph_file)

# Write metadata
metadata = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "java_version": java_version,
    "maven_version": mvn_version,
    "graph_file": graph_file,
    "cache_valid_hours": 24
}

metadata_file = os.path.join(cache_dir, 'metadata.json')
with open(metadata_file, 'w') as f:
    json.dump(metadata, f, indent=2)

print(f"Successfully generated: {graph_file}")
print(f"  Test files analyzed: {len(test_files)}")
print(f"  Production classes: {len(source_to_tests)}")

EOFPYTHON

exit 0
