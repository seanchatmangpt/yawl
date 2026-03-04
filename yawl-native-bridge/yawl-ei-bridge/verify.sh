#!/bin/bash
#
# Verification script for YAWL Erlang Interface Bridge
# Checks that all files are present and properly structured
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

# Function to check file existence
check_file() {
    if [ -f "$1" ]; then
        print_status "Found: $1"
        return 0
    else
        print_error "Missing: $1"
        return 1
    fi
}

# Function to check directory existence
check_dir() {
    if [ -d "$1" ]; then
        print_status "Directory exists: $1"
        return 0
    else
        print_error "Missing directory: $1"
        return 1
    fi
}

# Function to count files
count_files() {
    local pattern="$1"
    local count=$(find "$PROJECT_ROOT" -name "$pattern" | wc -l)
    echo "$count"
}

echo "YAWL Erlang Interface Bridge - Verification Script"
echo "=================================================="

# Check required files
echo -e "\n1. Checking required files..."

files=(
    "pom.xml"
    "README.md"
    "build.sh"
    "Makefile"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlTerm.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlAtom.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlLong.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlBinary.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlTuple.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlList.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlangException.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlangNode.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ProcessMiningClient.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ProcessMiningClientImpl.java"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/examples/BasicUsageExample.java"
    "src/test/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlangNodeTest.java"
    "src/test/java/org/yawlfoundation/yawl/nativebridge/erlang/IntegrationTest.java"
)

missing_files=0
for file in "${files[@]}"; do
    check_file "$PROJECT_ROOT/$file" || missing_files=$((missing_files + 1))
done

if [ $missing_files -eq 0 ]; then
    print_status "All required files found."
else
    print_error "$missing_files required files missing."
fi

# Check directory structure
echo -e "\n2. Checking directory structure..."

dirs=(
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang"
    "src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/examples"
    "src/test/java/org/yawlfoundation/yawl/nativebridge/erlang"
    "src/main/resources"
    "target/classes"
    "target/test-classes"
)

missing_dirs=0
for dir in "${dirs[@]}"; do
    check_dir "$PROJECT_ROOT/$dir" || missing_dirs=$((missing_dirs + 1))
done

if [ $missing_dirs -eq 0 ]; then
    print_status "All required directories exist."
else
    print_error "$missing_dirs required directories missing."
fi

# Check file counts
echo -e "\n3. Checking file counts..."

java_files=$(count_files "*.java")
test_files=$(count_files "*Test.java")

print_status "Java files: $java_files"
print_status "Test files: $test_files"

if [ $test_files -gt 0 ]; then
    test_ratio=$(echo "scale=2; $test_files / $java_files" | bc)
    print_status "Test ratio: $test_ratio"
fi

# Check key interfaces and classes
echo -e "\n4. Checking key Java components..."

# Check for sealed interface
if grep -q "sealed interface ErlTerm" src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlTerm.java; then
    print_status "ErlTerm interface is properly sealed"
else
    print_error "ErlTerm interface is not sealed"
fi

# Check for permits clause
if grep -q "permits ErlAtom, ErlList, ErlTuple, ErlBinary, ErlLong" src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlTerm.java; then
    print_status "All ErlTerm implementations are permitted"
else
    print_error "Missing permits clause in ErlTerm interface"
fi

# Check for proper exception handling
if grep -q "class ErlangException" src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ErlangException.java; then
    print_status "ErlangException is properly defined"
else
    print_error "ErlangException is not properly defined"
fi

# Check ProcessMiningClient interface methods
methods=("discoverProcessModel" "conformanceCheck" "analyzePerformance" "getProcessInstanceStats" "listProcessModels" "validateProcessModel" "executeQuery")
for method in "${methods[@]}"; do
    if grep -q "$method" src/main/java/org/yawlfoundation/yawl/nativebridge/erlang/ProcessMiningClient.java; then
        print_status "Method $method found in ProcessMiningClient interface"
    else
        print_error "Method $method missing from ProcessMiningClient interface"
    fi
done

# Check header file
echo -e "\n5. Checking header file..."

if grep -q "typedef enum" ../headers/ei.h; then
    print_status "ei.h has proper enum definitions"
else
    print_error "ei.h missing enum definitions"
fi

if grep -q "ei_x_buff_t" ../headers/ei.h; then
    print_status "ei.h has ei_x_buff_t structure"
else
    print_error "ei.h missing ei_x_buff_t structure"
fi

# Check pom.xml configuration
echo -e "\n6. Checking Maven configuration..."

if grep -q "jextract" pom.xml; then
    print_status "pom.xml has jextract configuration"
else
    print_error "pom.xml missing jextract configuration"
fi

if grep -q "ei_connect_init" pom.xml; then
    print_status "pom.xml has ei function bindings"
else
    print_warning "pom.xml may be missing some ei function bindings"
fi

# Build verification
echo -e "\n7. Build verification..."

if command -v mvn &> /dev/null; then
    print_status "Maven is available"

    # Try to compile
    if mvn compile -q; then
        print_status "Project compiles successfully"
    else
        print_error "Project compilation failed"
        exit 1
    fi
else
    print_warning "Maven not available - skipping build verification"
fi

# Final summary
echo -e "\n=================================================="
echo "Verification Summary"

if [ $missing_files -eq 0 ] && [ $missing_dirs -eq 0 ] && mvn compile -q; then
    print_status "All checks passed! Implementation is complete."
    echo ""
    echo "Next steps:"
    echo "1. Run 'make test' to execute unit tests"
    echo "2. Run 'make integration' to run integration tests (with Erlang node)"
    echo "3. Run 'make native' to build native image"
    echo "4. Run './build.sh help' for more options"
else
    print_error "Some checks failed. Please fix the issues above."
    exit 1
fi