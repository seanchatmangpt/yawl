#!/bin/bash
set -euo pipefail

# YAWL Build Performance Tracking System
# Wraps Maven builds with detailed timing and performance analysis

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PERF_FILE="${PROJECT_ROOT}/build-performance.json"
TEMP_LOG="${PROJECT_ROOT}/.build-timer-output.log"

timestamp() {
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

human_time() {
    local seconds=$1
    if (( $(echo "$seconds < 1" | bc -l) )); then
        echo "${seconds}s"
    elif (( $(echo "$seconds < 60" | bc -l) )); then
        printf "%.1fs" "$seconds"
    else
        local mins=$(echo "$seconds / 60" | bc)
        local secs=$(echo "$seconds % 60" | bc)
        printf "%dm %.1fs" "$mins" "$secs"
    fi
}

detect_parallel_threads() {
    local maven_args="$*"
    if [[ "$maven_args" =~ -T[[:space:]]*([0-9]+C?) ]]; then
        local thread_spec="${BASH_REMATCH[1]}"
        if [[ "$thread_spec" =~ ([0-9]+)C ]]; then
            local cores=$(nproc)
            local multiplier="${BASH_REMATCH[1]}"
            echo $((cores * multiplier))
        else
            echo "$thread_spec"
        fi
    else
        echo 1
    fi
}

check_cache_status() {
    if [[ -d "${PROJECT_ROOT}/target" ]] && [[ -n "$(find "${PROJECT_ROOT}/target" -type f -mmin -60 2>/dev/null)" ]]; then
        echo "true"
    else
        echo "false"
    fi
}

extract_module_times() {
    local log_file="$1"
    local -A module_times

    while IFS= read -r line; do
        if [[ "$line" =~ \[INFO\]\ ([a-zA-Z0-9_\ -]+)\ \.+\ SUCCESS\ \[\ +([0-9.]+)\ s\] ]]; then
            local module="${BASH_REMATCH[1]}"
            local time="${BASH_REMATCH[2]}"
            module="${module// /_}"
            module="${module//-/_}"
            module_times["$module"]="$time"
        elif [[ "$line" =~ \[INFO\]\ ([a-zA-Z0-9_\ -]+)\ \.+\ SUCCESS\ \[\ *([0-9]+):([0-9]+)\ min\] ]]; then
            local module="${BASH_REMATCH[1]}"
            local mins="${BASH_REMATCH[2]}"
            local secs="${BASH_REMATCH[3]}"
            module="${module// /_}"
            module="${module//-/_}"
            local time=$(echo "$mins * 60 + $secs" | bc)
            module_times["$module"]="$time"
        fi
    done < "$log_file"

    for module in "${!module_times[@]}"; do
        echo "$module:${module_times[$module]}"
    done
}

generate_json_report() {
    local timestamp="$1"
    local command="$2"
    local total_time="$3"
    local parallel_threads="$4"
    local cache_hit="$5"
    shift 5
    local -a module_data=("$@")

    local json="{\n"
    json+="  \"timestamp\": \"$timestamp\",\n"
    json+="  \"build_command\": \"mvn $command\",\n"
    json+="  \"total_time_seconds\": $total_time,\n"
    json+="  \"modules\": {\n"

    local first=true
    for entry in "${module_data[@]}"; do
        IFS=':' read -r module time <<< "$entry"
        if [[ "$first" == "true" ]]; then
            first=false
        else
            json+=",\n"
        fi
        json+="    \"$module\": $time"
    done

    json+="\n  },\n"
    json+="  \"cache_hit\": $cache_hit,\n"
    json+="  \"parallel_threads\": $parallel_threads\n"
    json+="}"

    echo -e "$json"
}

append_to_history() {
    local new_entry="$1"

    if [[ -f "$PERF_FILE" ]]; then
        local existing=$(cat "$PERF_FILE")
        if [[ "$existing" =~ ^\[ ]]; then
            local content=$(echo "$existing" | sed '$s/]$//')
            echo -e "${content},\n${new_entry}\n]" > "$PERF_FILE"
        else
            echo -e "[\n${existing},\n${new_entry}\n]" > "$PERF_FILE"
        fi
    else
        echo -e "[\n${new_entry}\n]" > "$PERF_FILE"
    fi
}

display_summary() {
    local wall_clock_time="$1"
    shift
    local -a module_data=("$@")

    echo ""
    echo "========================================"
    echo "  Build Performance Summary"
    echo "========================================"
    echo ""
    printf "%-35s %10s %8s\n" "Module" "Time" "Percent"
    echo "----------------------------------------"

    declare -A modules_map
    local total_module_time=0
    for entry in "${module_data[@]}"; do
        IFS=':' read -r module time <<< "$entry"
        modules_map["$module"]="$time"
        total_module_time=$(echo "$total_module_time + $time" | bc)
    done

    for module in $(for m in "${!modules_map[@]}"; do echo "$m:${modules_map[$m]}"; done | sort -t: -k2 -rn); do
        IFS=':' read -r mod_name mod_time <<< "$module"
        local percent=$(echo "scale=2; ($mod_time / $total_module_time) * 100" | bc)
        printf "%-35s %9.3fs %7.2f%%\n" "$mod_name" "$mod_time" "$percent"
    done

    echo "----------------------------------------"
    printf "%-35s %9.3fs\n" "Total CPU Time (all modules)" "$total_module_time"
    printf "%-35s %9.3fs\n" "Wall Clock Time" "$wall_clock_time"

    if (( $(echo "$total_module_time > $wall_clock_time" | bc -l) )); then
        local speedup=$(echo "scale=2; $total_module_time / $wall_clock_time" | bc)
        printf "%-35s %9.2fx\n" "Parallel Speedup" "$speedup"
    fi

    echo "========================================"
    echo ""
    echo "Performance data saved to: $PERF_FILE"
}

main() {
    if [[ $# -eq 0 ]]; then
        echo "Usage: $0 <maven-goals> [maven-options]"
        echo ""
        echo "Examples:"
        echo "  $0 clean test"
        echo "  $0 compile -T 1C"
        echo "  $0 clean install -DskipTests"
        echo ""
        echo "Tracks build timing per module and stores in build-performance.json"
        exit 1
    fi

    local maven_args="$*"
    local start_timestamp=$(timestamp)
    local parallel_threads=$(detect_parallel_threads "$maven_args")
    local cache_hit=$(check_cache_status)

    echo "========================================"
    echo "  YAWL Build Timer"
    echo "========================================"
    echo "Command: mvn $maven_args"
    echo "Parallel threads: $parallel_threads"
    echo "Cache available: $cache_hit"
    echo "Start time: $start_timestamp"
    echo "========================================"
    echo ""

    local start_seconds=$(date +%s.%N)

    cd "$PROJECT_ROOT"

    if mvn $maven_args 2>&1 | tee "$TEMP_LOG"; then
        local end_seconds=$(date +%s.%N)
        local total_time=$(echo "$end_seconds - $start_seconds" | bc)

        local -a module_times=()
        while IFS= read -r entry; do
            module_times+=("$entry")
        done < <(extract_module_times "$TEMP_LOG")

        if [[ ${#module_times[@]} -eq 0 ]]; then
            echo ""
            echo "Warning: No module timing data found in build output"
            echo "Total build time: $(human_time "$total_time")"
        else
            display_summary "$total_time" "${module_times[@]}"

            local json_report=$(generate_json_report "$start_timestamp" "$maven_args" "$total_time" "$parallel_threads" "$cache_hit" "${module_times[@]}")
            append_to_history "$json_report"
        fi

        rm -f "$TEMP_LOG"
        exit 0
    else
        local end_seconds=$(date +%s.%N)
        local total_time=$(echo "$end_seconds - $start_seconds" | bc)

        echo ""
        echo "========================================"
        echo "  Build Failed"
        echo "========================================"
        echo "Time elapsed: $(human_time "$total_time")"
        echo ""

        rm -f "$TEMP_LOG"
        exit 1
    fi
}

main "$@"
