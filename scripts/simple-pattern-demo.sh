#!/bin/bash
#
# Simple Pattern Demo Runner - For demonstration purposes
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Function to show pattern information
show_pattern_info() {
    local pattern_id="$1"

    case "$pattern_id" in
        WCP-1)
            echo "WCP-1: Sequence Pattern"
            echo "   - Basic sequential execution of tasks"
            echo "   - One task starts after another completes"
            echo "   - Simple workflow: A → B → C"
            ;;
        WCP-2)
            echo "WCP-2: Parallel Split Pattern"
            echo "   - Split execution into parallel branches"
            echo "   - Multiple tasks start simultaneously"
            echo "   - Pattern: A → {B, C}"
            ;;
        WCP-3)
            echo "WCP-3: Synchronization Pattern"
            echo "   - Multiple tasks must complete before continuation"
            echo "   - Wait for all parallel branches to finish"
            echo "   - Pattern: {A, B} → C"
            ;;
        WCP-4)
            echo "WCP-4: Exclusive Choice Pattern"
            echo "   - Select one branch based on condition"
            echo "   - Only one task executes"
            echo "   - Pattern: A → {B, C} (exclusive)"
            ;;
        WCP-5)
            echo "WCP-5: Simple Merge Pattern"
            echo "   - Multiple branches can continue to next task"
            echo "   - Non-deterministic choice"
            echo "   - Pattern: {A, B} → C"
            ;;
        *)
            echo "$pattern_id: Workflow Pattern"
            echo "   - YAWL workflow control-flow pattern"
            echo "   - Part of the 43+ YAWL pattern catalog"
            ;;
    esac
}

# Function to simulate pattern execution
simulate_pattern_execution() {
    local pattern_id="$1"
    local timeout_seconds="${2:-30}"

    print_info "Executing $pattern_id..."

    # Simulate work based on pattern complexity
    case "$pattern_id" in
        WCP-1) sleep 1 ;;
        WCP-2) sleep 2 ;;
        WCP-3) sleep 2 ;;
        WCP-4) sleep 1 ;;
        WCP-5) sleep 1 ;;
        *) sleep 2 ;;
    esac

    # Randomly simulate success/failure for demo
    if (( RANDOM % 10 > 1 )); then  # 90% success rate
        print_status "$pattern_id completed successfully"
        return 0
    else
        print_error "$pattern_id failed (simulated error)"
        return 1
    fi
}

# Function to show demo results
show_results() {
    local total_patterns="$1"
    local successful_patterns="$2"
    local failed_patterns="$3"
    local start_time="$4"
    local end_time="$5"

    echo
    echo "======================================================================"
    print_status "Pattern Demo Execution Summary"
    echo "======================================================================"
    echo
    echo "Total patterns tested: $total_patterns"
    echo "Successful patterns: $successful_patterns"
    echo "Failed patterns: $failed_patterns"
    echo
    echo "Success rate: $(( successful_patterns * 100 / total_patterns ))%"

    if [[ "$failed_patterns" -gt 0 ]]; then
        print_warning "$failed_patterns patterns encountered issues"
        echo
        print_info "Common issues:"
        echo "  - Pattern configuration errors"
        echo "  - Resource constraints"
        echo "  - Timeout conditions"
        echo "  - Validation failures"
    fi

    local duration=$(( end_time - start_time ))
    echo "Total execution time: $duration seconds"

    if [[ "$successful_patterns" -eq "$total_patterns" ]]; then
        print_status "🎉 All patterns executed successfully!"
    else
        print_status "📊 Demo completed with $(( total_patterns - failed_patterns ))/$total_patterns successful patterns"
    fi
}

# Main function
main() {
    # Parse arguments
    local patterns=()
    local category=""
    local all_patterns=false
    local format="console"
    local output_file=""
    local timeout_seconds=30
    local parallel=true

    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                cat << EOF
Simple Pattern Demo Runner - YAWL Workflow Pattern Simulation

This script simulates the execution of YAWL workflow patterns for demonstration purposes.

Usage: $0 [OPTIONS]

Options:
  --pattern PATTERNS    Specific pattern IDs (comma-separated)
                       Example: --pattern "WCP-1,WCP-2,WCP-3"

  --category CATEGORY   Pattern category (BASIC, BRANCHING, etc.)
                       Currently supports: BASIC

  --all               Run all basic patterns (WCP-1 through WCP-5)

  --format FORMAT     Output format: console, json
                       (default: console)

  --output PATH       Output file path (default: stdout)

  --timeout SECONDS   Execution timeout per pattern (default: 30)

  --sequential        Disable parallel execution

  -h, --help         Show this help message

Examples:
  $0                                        # Run basic patterns
  $0 --pattern WCP-1                       # Run WCP-1 only
  $0 --pattern WCP-1,WCP-2,WCP-3          # Run multiple patterns
  $0 --all                                 # Run all basic patterns
  $0 --format json                         # JSON output
  $0 --all --output demo-results.json     # Save to file

Available Patterns:
  WCP-1: Sequence
  WCP-2: Parallel Split
  WCP-3: Synchronization
  WCP-4: Exclusive Choice
  WCP-5: Simple Merge

Categories:
  BASIC: Basic workflow patterns (WCP-1 through WCP-5)
EOF
                exit 0
                ;;
            --pattern)
                shift
                IFS=',' read -ra patterns <<< "$1"
                ;;
            --category)
                shift
                category="$1"
                ;;
            --all)
                all_patterns=true
                ;;
            --format)
                shift
                format="$1"
                ;;
            --output)
                shift
                output_file="$1"
                ;;
            --timeout)
                shift
                timeout_seconds="$1"
                ;;
            --sequential)
                parallel=false
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
        shift
    done

    # Determine patterns to run
    if [[ "$all_patterns" == true ]]; then
        patterns=(WCP-1 WCP-2 WCP-3 WCP-4 WCP-5)
    elif [[ -n "$category" ]]; then
        case "$category" in
            BASIC)
                patterns=(WCP-1 WCP-2 WCP-3 WCP-4 WCP-5)
                ;;
            *)
                print_error "Unknown category: $category"
                echo "Available categories: BASIC"
                exit 1
                ;;
        esac
    elif [[ ${#patterns[@]} -eq 0 ]]; then
        # Default: basic patterns
        patterns=(WCP-1 WCP-2 WCP-3 WCP-4 WCP-5)
    fi

    # Validate patterns
    for pattern in "${patterns[@]}"; do
        if [[ ! "$pattern" =~ ^WCP-[1-9][0-9]*$ ]]; then
            print_error "Invalid pattern ID: $pattern"
            exit 1
        fi
    done

    echo "======================================================================"
    print_info "        YAWL Pattern Demo Runner - Simulation Mode"
    echo "======================================================================"
    echo

    # Show what will be run
    if [[ "$all_patterns" == true ]]; then
        print_info "Running all basic patterns"
    elif [[ -n "$category" ]]; then
        print_info "Running patterns in category: $category"
    else
        print_info "Running specific patterns"
    fi

    echo "Patterns to execute:"
    for pattern in "${patterns[@]}"; do
        echo "  - $pattern"
        show_pattern_info "$pattern"
    done

    echo "Execution mode: $([[ "$parallel" == true ]] && echo "parallel" || echo "sequential")"
    echo "Timeout per pattern: $timeout_seconds seconds"
    echo "Output format: $format"
    echo

    if [[ -n "$output_file" ]]; then
        print_info "Output will be saved to: $output_file"
    fi

    # Skip confirmation for automated execution

    # Execute patterns
    local start_time=$(date +%s)
    local successful=0
    local failed=0

    if [[ "$parallel" == true ]]; then
        # Parallel execution using background processes
        local pids=()

        for pattern in "${patterns[@]}"; do
            {
                if simulate_pattern_execution "$pattern" "$timeout_seconds"; then
                    echo "SUCCESS:$pattern" > "/tmp/pattern_result_$pattern"
                else
                    echo "FAILED:$pattern" > "/tmp/pattern_result_$pattern"
                fi
            } &
            pids+=($!)
        done

        # Wait for all processes
        for pid in "${pids[@]}"; do
            wait "$pid"
        done

        # Collect results
        for pattern in "${patterns[@]}"; do
            if [[ -f "/tmp/pattern_result_$pattern" ]]; then
                local result=$(cat "/tmp/pattern_result_$pattern")
                case "$result" in
                    SUCCESS:*)
                        ((successful++))
                        ;;
                    FAILED:*)
                        ((failed++))
                        ;;
                esac
            fi
        done
    else
        # Sequential execution
        for pattern in "${patterns[@]}"; do
            if simulate_pattern_execution "$pattern" "$timeout_seconds"; then
                ((successful++))
            else
                ((failed++))
            fi
        done
    fi

    local end_time=$(date +%s)

    # Generate output
    if [[ "$format" == "json" ]]; then
        generate_json_output patterns $successful $failed $start_time $end_time
    else
        show_results "${#patterns[@]}" $successful $failed $start_time $end_time
    fi

    # Cleanup temporary files
    for pattern in "${patterns[@]}"; do
        rm -f "/tmp/pattern_result_$pattern"
    done
}

# Generate JSON output
generate_json_output() {
    local patterns_var_name="$1"
    # Get the array reference
    local patterns_array
    eval "patterns_array=(\"\${${patterns_var_name}[@]}\")"

    shift
    local successful="$1"
    local failed="$2"
    local start_time="$3"
    local end_time="$4"

    # Calculate execution time
    local execution_time=$((end_time - start_time))

    # Calculate success rate
    local total_patterns=${#patterns_array[@]}
    local success_rate=$(( successful * 100 / total_patterns ))

    local output="{"
    output+="\"version\": \"1.0.0\","
    output+="\"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
    output+="\"totalPatterns\": $total_patterns,"
    output+="\"successfulPatterns\": $successful,"
    output+="\"failedPatterns\": $failed,"
    output+="\"executionTimeSeconds\": $execution_time,"
    output+="\"results\": ["

    for i in "${!patterns_array[@]}"; do
        output+="{"
        output+="\"patternId\": \"${patterns_array[i]}\","
        output+="\"status\": \"$([[ $i -lt $successful ]] && echo "SUCCESS" || echo "FAILED")\""
        output+="}"
        if [[ $i -lt $((total_patterns - 1)) ]]; then
            output+=","
        fi
    done

    output+="],"
    output+="\"successRate\": $success_rate"
    output+="}"

    if [[ -n "$output_file" ]]; then
        echo "$output" > "$output_file"
        print_status "JSON output saved to: $output_file"
    else
        echo "$output"
    fi
}

# Run main function
main "$@"