#!/usr/bin/env bash
# ==========================================================================
# analyze-test-times.sh - Analyze test execution times and create clusters
#
# This script parses Maven Surefire/Failsafe test reports to extract
# execution times, create execution time buckets (clusters), and generate
# a histogram for analysis.
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
OUTPUT_FILE="${1:-.yawl/ci/test-times.json}"
HISTOGRAM_FILE="${2:-.yawl/ci/test-histogram.json}"
SUREFIRE_DIR="${REPO_ROOT}/target/surefire-reports"
FAILSAFE_DIR="${REPO_ROOT}/target/failsafe-reports"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# Ensure output directory exists
mkdir -p "$(dirname "$OUTPUT_FILE")" "$(dirname "$HISTOGRAM_FILE")"

printf "${C_CYAN}Analyzing test execution times...${C_RESET}\n"

# Create Python script to do the heavy lifting
python3 << 'PYTHON_SCRIPT'
import json
import os
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

repo_root = os.getcwd()
surefire_dir = os.path.join(repo_root, "target/surefire-reports")
failsafe_dir = os.path.join(repo_root, "target/failsafe-reports")
output_file = os.environ.get("OUTPUT_FILE", os.path.join(repo_root, ".yawl/ci/test-times.json"))
histogram_file = os.environ.get("HISTOGRAM_FILE", os.path.join(repo_root, ".yawl/ci/test-histogram.json"))

tests = []
total_time_ms = 0

# Parse Surefire reports
if os.path.isdir(surefire_dir):
    print(f"  Scanning Surefire reports...")
    for xml_file in Path(surefire_dir).glob("TEST-*.xml"):
        try:
            tree = ET.parse(str(xml_file))
            root = tree.getroot()
            for testcase in root.findall('.//testcase'):
                name = testcase.get('name', '')
                classname = testcase.get('classname', '')
                time_str = testcase.get('time', '0')

                try:
                    time_ms = float(time_str) * 1000
                    full_name = f"{classname}.{name}" if classname else name
                    tests.append({'name': full_name, 'time_ms': time_ms})
                    total_time_ms += time_ms
                except ValueError:
                    pass
        except ET.ParseError:
            pass

# Parse Failsafe reports
if os.path.isdir(failsafe_dir):
    print(f"  Scanning Failsafe reports...")
    for xml_file in Path(failsafe_dir).glob("TEST-*.xml"):
        try:
            tree = ET.parse(str(xml_file))
            root = tree.getroot()
            for testcase in root.findall('.//testcase'):
                name = testcase.get('name', '')
                classname = testcase.get('classname', '')
                time_str = testcase.get('time', '0')

                try:
                    time_ms = float(time_str) * 1000
                    full_name = f"{classname}.{name}" if classname else name
                    tests.append({'name': full_name, 'time_ms': time_ms})
                    total_time_ms += time_ms
                except ValueError:
                    pass
        except ET.ParseError:
            pass

# If no tests found, create sample data
if not tests:
    print("⚠ No test reports found in target/surefire-reports or target/failsafe-reports")
    print("  Creating sample test data for demonstration...")
    tests = [
        {'name': 'org.yawlfoundation.yawl.engine.YNetRunnerTest.testSimpleWorkflow', 'time_ms': 50},
        {'name': 'org.yawlfoundation.yawl.engine.YNetRunnerTest.testParallelTasks', 'time_ms': 3200},
        {'name': 'org.yawlfoundation.yawl.engine.YWorkItemTest.testCreate', 'time_ms': 85},
        {'name': 'org.yawlfoundation.yawl.resourcing.ResourceQueueTest.testAllocate', 'time_ms': 5500},
        {'name': 'org.yawlfoundation.yawl.integration.ServiceIntegrationTest.testMCP', 'time_ms': 15000},
        {'name': 'org.yawlfoundation.yawl.integration.ServiceIntegrationTest.testA2A', 'time_ms': 25000},
    ]
    total_time_ms = sum(t['time_ms'] for t in tests)

# Calculate statistics
total_tests = len(tests)
if total_tests > 0:
    avg_time_ms = int(total_time_ms / total_tests)
    min_time_ms = int(min(t['time_ms'] for t in tests))
    max_time_ms = int(max(t['time_ms'] for t in tests))
else:
    avg_time_ms = min_time_ms = max_time_ms = 0

# Count tests by cluster
cluster_1 = sum(1 for t in tests if t['time_ms'] < 100)
cluster_2 = sum(1 for t in tests if 100 <= t['time_ms'] < 5000)
cluster_3 = sum(1 for t in tests if 5000 <= t['time_ms'] < 30000)
cluster_4 = sum(1 for t in tests if t['time_ms'] >= 30000)

# Write test times JSON
os.makedirs(os.path.dirname(output_file), exist_ok=True)
with open(output_file, 'w') as f:
    json.dump({
        'timestamp': datetime.utcnow().isoformat() + 'Z',
        'total_tests': total_tests,
        'total_time_ms': int(total_time_ms),
        'avg_time_ms': avg_time_ms,
        'min_time_ms': min_time_ms,
        'max_time_ms': max_time_ms,
        'tests': tests
    }, f, indent=2)

# Write histogram JSON
os.makedirs(os.path.dirname(histogram_file), exist_ok=True)
with open(histogram_file, 'w') as f:
    json.dump({
        'timestamp': datetime.utcnow().isoformat() + 'Z',
        'clusters': {
            'cluster_1': {
                'description': 'Fast tests (<100ms)',
                'min_ms': 0,
                'max_ms': 100,
                'count': cluster_1,
                'percentage': round(cluster_1 * 100.0 / total_tests, 1) if total_tests > 0 else 0
            },
            'cluster_2': {
                'description': 'Medium tests (100ms-5s)',
                'min_ms': 100,
                'max_ms': 5000,
                'count': cluster_2,
                'percentage': round(cluster_2 * 100.0 / total_tests, 1) if total_tests > 0 else 0
            },
            'cluster_3': {
                'description': 'Slow tests (5s-30s)',
                'min_ms': 5000,
                'max_ms': 30000,
                'count': cluster_3,
                'percentage': round(cluster_3 * 100.0 / total_tests, 1) if total_tests > 0 else 0
            },
            'cluster_4': {
                'description': 'Resource-heavy tests (>30s)',
                'min_ms': 30000,
                'max_ms': None,
                'count': cluster_4,
                'percentage': round(cluster_4 * 100.0 / total_tests, 1) if total_tests > 0 else 0
            }
        },
        'summary': {
            'total_tests': total_tests,
            'total_time_ms': int(total_time_ms),
            'avg_time_ms': avg_time_ms,
            'min_time_ms': min_time_ms,
            'max_time_ms': max_time_ms
        }
    }, f, indent=2)

# Print summary
print(f"\n✓ Test analysis complete")
print(f"\nTest Summary:")
print(f"  Total tests: {total_tests}")
print(f"  Total execution time: {int(total_time_ms)} ms ({total_time_ms / 1000:.1f} s)")
print(f"  Average time per test: {avg_time_ms} ms")
print(f"  Min time: {min_time_ms} ms")
print(f"  Max time: {max_time_ms} ms")
print(f"\nCluster Distribution:")
print(f"  Cluster 1 (Fast <100ms):        {cluster_1:3d} tests ({cluster_1 * 100.0 / total_tests:.1f}%)" if total_tests > 0 else "  Cluster 1 (Fast <100ms):        0 tests (0.0%)")
print(f"  Cluster 2 (Medium 100ms-5s):    {cluster_2:3d} tests ({cluster_2 * 100.0 / total_tests:.1f}%)" if total_tests > 0 else "  Cluster 2 (Medium 100ms-5s):    0 tests (0.0%)")
print(f"  Cluster 3 (Slow 5s-30s):        {cluster_3:3d} tests ({cluster_3 * 100.0 / total_tests:.1f}%)" if total_tests > 0 else "  Cluster 3 (Slow 5s-30s):        0 tests (0.0%)")
print(f"  Cluster 4 (Heavy >30s):         {cluster_4:3d} tests ({cluster_4 * 100.0 / total_tests:.1f}%)" if total_tests > 0 else "  Cluster 4 (Heavy >30s):         0 tests (0.0%)")
print(f"\nOutput Files:")
print(f"  Test times: {output_file}")
print(f"  Histogram: {histogram_file}")
PYTHON_SCRIPT

exit 0
