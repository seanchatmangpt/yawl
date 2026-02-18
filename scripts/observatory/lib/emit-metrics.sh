#!/usr/bin/env bash
#
# emit-metrics.sh - Metrics collection and emission library
#
# Provides functions for collecting and emitting performance metrics
# in OpenTelemetry-compatible format. Supports timers, counters,
# gauges, and histograms.
#
# Usage:
#   source lib/emit-metrics.sh
#   start_timer "operation_duration"
#   # ... do work ...
#   end_timer "operation_duration"
#   emit_counter "requests_total" 1
#   emit_gauge "memory_bytes" 1048576
#

set -euo pipefail

# Configuration defaults
declare -g METRICS_OUTPUT="${METRICS_OUTPUT:-stdout}"
declare -g METRICS_NAMESPACE="${METRICS_NAMESPACE:-yawl}"
declare -g METRICS_SERVICE="${METRICS_SERVICE:-observatory}"
declare -g METRICS_FORMAT="${METRICS_FORMAT:-otel}"  # otel or prometheus
declare -g METRICS_ENDPOINT="${METRICS_ENDPOINT:-http://localhost:4318/v1/metrics}"
declare -g METRICS_LABELS="${METRICS_LABELS:-}"
declare -g METRICS_FILE="${METRICS_FILE:-/tmp/yawl-metrics.log}"

# Timer storage - associative array for start times (nanoseconds)
declare -gA METRICS_TIMERS=()

# Histogram bucket storage
declare -gA METRICS_HISTOGRAM_BUCKETS=()
declare -gA METRICS_HISTOGRAM_COUNTS=()
declare -gA METRICS_HISTOGRAM_SUMS=()

# Counter storage for aggregation
declare -gA METRICS_COUNTERS=()

# Gauge storage
declare -gA METRICS_GAUGES=()

#
# Get current timestamp in nanoseconds since epoch
#
# Returns: nanoseconds timestamp
#
_get_timestamp_ns() {
    # Use date with nanoseconds if available, otherwise milliseconds
    if date +%s%N &>/dev/null; then
        date +%s%N
    else
        echo "$(date +%s)000000000"
    fi
}

#
# Get current timestamp in ISO 8601 format with nanoseconds
#
# Returns: ISO 8601 timestamp
#
_get_timestamp_iso() {
    date -u +"%Y-%m-%dT%H:%M:%S.%NZ"
}

#
# Get current Unix timestamp in seconds
#
# Returns: Unix timestamp
#
_get_timestamp_unix() {
    date +%s
}

#
# Build labels string in the specified format
#
# Arguments:
#   $@ - Additional labels as key=value pairs
#
# Returns: Formatted labels string
#
_build_labels() {
    local labels="${METRICS_LABELS}"
    local additional_labels="$*"

    if [[ -n "${additional_labels}" ]]; then
        if [[ -n "${labels}" ]]; then
            labels="${labels},${additional_labels}"
        else
            labels="${additional_labels}"
        fi
    fi

    if [[ "${METRICS_FORMAT}" == "prometheus" ]]; then
        if [[ -n "${labels}" ]]; then
            echo "{${labels}}"
        else
            echo ""
        fi
    else
        # OpenTelemetry format
        if [[ -n "${labels}" ]]; then
            echo "[${labels}]"
        else
            echo ""
        fi
    fi
}

#
# Parse labels into attribute format for OpenTelemetry
#
# Arguments:
#   $1 - Labels string (key=value,key2=value2)
#
# Returns: JSON-like attribute string
#
_parse_labels_to_attrs() {
    local labels="$1"
    local attrs=""

    if [[ -z "${labels}" ]]; then
        echo ""
        return
    fi

    local first=true
    IFS=',' read -ra pairs <<< "${labels}"
    for pair in "${pairs[@]}"; do
        local key="${pair%%=*}"
        local value="${pair#*=}"

        if [[ "${first}" == "true" ]]; then
            first=false
        else
            attrs+=","
        fi

        attrs+="\"${key}\":\"${value}\""
    done

    echo "${attrs}"
}

#
# Emit a metric line to the configured output
#
# Arguments:
#   $1 - Metric line to emit
#
_emit_line() {
    local line="$1"

    case "${METRICS_OUTPUT}" in
        stdout)
            echo "${line}"
            ;;
        stderr)
            echo "${line}" >&2
            ;;
        file)
            echo "${line}" >> "${METRICS_FILE}"
            ;;
        http)
            _emit_http "${line}"
            ;;
        *)
            echo "${line}"
            ;;
    esac
}

#
# Emit metrics via HTTP to OpenTelemetry collector
#
# Arguments:
#   $1 - Metric data
#
_emit_http() {
    local data="$1"

    if command -v curl &>/dev/null; then
        curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "${data}" \
            "${METRICS_ENDPOINT}" &>/dev/null || true
    fi
}

#
# Start a timer for measuring duration
#
# Arguments:
#   $1 - metric_name - Name of the timer metric
#   $@ - Optional labels as key=value pairs
#
# Example:
#   start_timer "database_query_duration" "table=users"
#
start_timer() {
    local metric_name="$1"
    shift
    local labels="$*"
    local timer_key="${metric_name}"

    if [[ -n "${labels}" ]]; then
        timer_key="${metric_name}:${labels}"
    fi

    METRICS_TIMERS["${timer_key}"]=$(_get_timestamp_ns)
}

#
# End a timer and emit the duration metric
#
# Arguments:
#   $1 - metric_name - Name of the timer metric
#   $2 - Optional unit (default: ms) - s, ms, us, ns
#   $@ - Additional labels as key=value pairs
#
# Returns: Duration value in specified unit
#
# Example:
#   duration=$(end_timer "database_query_duration" "ms" "table=users")
#
end_timer() {
    local metric_name="$1"
    local unit="${2:-ms}"
    shift 2 2>/dev/null || shift $#
    local labels="$*"
    local timer_key="${metric_name}"

    if [[ -n "${labels}" ]]; then
        timer_key="${metric_name}:${labels}"
    fi

    if [[ -z "${METRICS_TIMERS[${timer_key}]:-}" ]]; then
        echo "0"
        return 1
    fi

    local start_ns="${METRICS_TIMERS[${timer_key}]}"
    local end_ns=$(_get_timestamp_ns)
    local duration_ns=$((end_ns - start_ns))
    local duration_value

    # Convert to requested unit
    case "${unit}" in
        s)
            duration_value=$((duration_ns / 1000000000))
            ;;
        ms)
            duration_value=$((duration_ns / 1000000))
            ;;
        us)
            duration_value=$((duration_ns / 1000))
            ;;
        ns)
            duration_value="${duration_ns}"
            ;;
        *)
            duration_value=$((duration_ns / 1000000))
            unit="ms"
            ;;
    esac

    # Clear the timer
    unset "METRICS_TIMERS[${timer_key}]"

    # Emit the metric
    _emit_timer_metric "${metric_name}" "${duration_value}" "${unit}" "${labels}"

    echo "${duration_value}"
}

#
# Internal: Emit a timer/duration metric
#
# Arguments:
#   $1 - Metric name
#   $2 - Duration value
#   $3 - Unit
#   $4 - Labels
#
_emit_timer_metric() {
    local metric_name="$1"
    local value="$2"
    local unit="$3"
    local labels="$4"
    local timestamp=$(_get_timestamp_iso)
    local full_name="${METRICS_NAMESPACE}_${metric_name}"

    if [[ "${METRICS_FORMAT}" == "prometheus" ]]; then
        local label_str=""
        if [[ -n "${labels}" ]]; then
            label_str="{${labels}}"
        fi
        _emit_line "# TYPE ${full_name} gauge"
        _emit_line "# UNIT ${full_name} ${unit}"
        _emit_line "${full_name}${label_str} ${value}"
    else
        # OpenTelemetry format
        local attrs=$(_parse_labels_to_attrs "${labels}")
        local attr_str=""
        if [[ -n "${attrs}" ]]; then
            attr_str=", \"attributes\": {${attrs}}"
        fi

        local json='{
  "resourceMetrics": [{
    "resource": {
      "attributes": [{"key": "service.name", "value": {"stringValue": "'"${METRICS_SERVICE}"'"}}]
    },
    "scopeMetrics": [{
      "metrics": [{
        "name": "'"${full_name}"'",
        "unit": "'"${unit}"'",
        "gauge": {
          "dataPoints": [{
            "timeUnixNano": "'"$(date +%s)000000000"'",
            "asDouble": '"${value}"'
            '"${attr_str}"'
          }]
        }
      }]
    }]
  }]
}'
        _emit_line "${json}"
    fi
}

#
# Emit a counter metric (monotonically increasing value)
#
# Arguments:
#   $1 - metric_name - Name of the counter metric
#   $2 - value - Counter value to add
#   $@ - Optional labels as key=value pairs
#
# Example:
#   emit_counter "http_requests_total" 1 "method=GET" "status=200"
#
emit_counter() {
    local metric_name="$1"
    local value="${2:-1}"
    shift 2 2>/dev/null || shift $#
    local labels="$*"
    local timestamp=$(_get_timestamp_unix)
    local full_name="${METRICS_NAMESPACE}_${metric_name}"

    # Track counter for aggregation
    local counter_key="${metric_name}:${labels}"
    local current="${METRICS_COUNTERS[${counter_key}]:-0}"
    METRICS_COUNTERS[${counter_key}]=$((current + value))

    if [[ "${METRICS_FORMAT}" == "prometheus" ]]; then
        local label_str=""
        if [[ -n "${labels}" ]]; then
            label_str="{${labels}}"
        fi
        _emit_line "# TYPE ${full_name} counter"
        _emit_line "${full_name}${label_str} ${METRICS_COUNTERS[${counter_key}]}"
    else
        # OpenTelemetry format
        local attrs=$(_parse_labels_to_attrs "${labels}")
        local attr_str=""
        if [[ -n "${attrs}" ]]; then
            attr_str=", \"attributes\": {${attrs}}"
        fi

        local json='{
  "resourceMetrics": [{
    "resource": {
      "attributes": [{"key": "service.name", "value": {"stringValue": "'"${METRICS_SERVICE}"'"}}]
    },
    "scopeMetrics": [{
      "metrics": [{
        "name": "'"${full_name}"'",
        "unit": "1",
        "sum": {
          "aggregationTemporality": 2,
          "isMonotonic": true,
          "dataPoints": [{
            "timeUnixNano": "'"${timestamp}000000000"'",
            "asDouble": '"${value}"'
            '"${attr_str}"'
          }]
        }
      }]
    }]
  }]
}'
        _emit_line "${json}"
    fi
}

#
# Emit a gauge metric (point-in-time value that can go up or down)
#
# Arguments:
#   $1 - metric_name - Name of the gauge metric
#   $2 - value - Current gauge value
#   $@ - Optional labels as key=value pairs
#
# Example:
#   emit_gauge "memory_usage_bytes" 1048576 "heap=old"
#
emit_gauge() {
    local metric_name="$1"
    local value="$2"
    shift 2 2>/dev/null || shift $#
    local labels="$*"
    local timestamp=$(_get_timestamp_unix)
    local full_name="${METRICS_NAMESPACE}_${metric_name}"

    # Track gauge for reference
    local gauge_key="${metric_name}:${labels}"
    METRICS_GAUGES[${gauge_key}]="${value}"

    if [[ "${METRICS_FORMAT}" == "prometheus" ]]; then
        local label_str=""
        if [[ -n "${labels}" ]]; then
            label_str="{${labels}}"
        fi
        _emit_line "# TYPE ${full_name} gauge"
        _emit_line "${full_name}${label_str} ${value}"
    else
        # OpenTelemetry format
        local attrs=$(_parse_labels_to_attrs "${labels}")
        local attr_str=""
        if [[ -n "${attrs}" ]]; then
            attr_str=", \"attributes\": {${attrs}}"
        fi

        local json='{
  "resourceMetrics": [{
    "resource": {
      "attributes": [{"key": "service.name", "value": {"stringValue": "'"${METRICS_SERVICE}"'"}}]
    },
    "scopeMetrics": [{
      "metrics": [{
        "name": "'"${full_name}"'",
        "gauge": {
          "dataPoints": [{
            "timeUnixNano": "'"${timestamp}000000000"'",
            "asDouble": '"${value}"'
            '"${attr_str}"'
          }]
        }
      }]
    }]
  }]
}'
        _emit_line "${json}"
    fi
}

#
# Emit a histogram metric (distribution of values)
#
# Arguments:
#   $1 - metric_name - Name of the histogram metric
#   $2 - value - Value to record in the histogram
#   $3 - Optional buckets (comma-separated boundaries, e.g., "0.1,0.5,1,5,10")
#   $@ - Additional labels as key=value pairs
#
# Example:
#   emit_histogram "request_duration_seconds" 0.234 "0.1,0.5,1,5,10" "endpoint=/api/users"
#
emit_histogram() {
    local metric_name="$1"
    local value="$2"
    local buckets="${3:-0.005,0.01,0.025,0.05,0.1,0.25,0.5,1,2.5,5,10}"
    shift 3 2>/dev/null || shift $#
    local labels="$*"
    local timestamp=$(_get_timestamp_unix)
    local full_name="${METRICS_NAMESPACE}_${metric_name}"

    # Parse buckets
    IFS=',' read -ra bucket_bounds <<< "${buckets}"

    # Initialize histogram state if needed
    local hist_key="${metric_name}:${labels}"
    if [[ -z "${METRICS_HISTOGRAM_COUNTS[${hist_key}]:-}" ]]; then
        METRICS_HISTOGRAM_COUNTS[${hist_key}]="0"
        METRICS_HISTOGRAM_SUMS[${hist_key}]="0"
    fi

    # Update count and sum
    local count="${METRICS_HISTOGRAM_COUNTS[${hist_key}]}"
    local sum="${METRICS_HISTOGRAM_SUMS[${hist_key}]}"

    count=$((count + 1))
    # Use awk for floating point addition
    sum=$(awk "BEGIN {printf \"%.6f\", ${sum} + ${value}}")

    METRICS_HISTOGRAM_COUNTS[${hist_key}]="${count}"
    METRICS_HISTOGRAM_SUMS[${hist_key}]="${sum}"

    # Count values in each bucket
    local bucket_counts=()
    local cumulative=0
    for bound in "${bucket_bounds[@]}"; do
        # Check if value falls in this bucket
        local in_bucket
        in_bucket=$(awk "BEGIN {print (${value} <= ${bound}) ? 1 : 0}")
        if [[ "${in_bucket}" -eq 1 ]]; then
            cumulative=$((cumulative + 1))
        fi
        bucket_counts+=("${cumulative}")
    done

    if [[ "${METRICS_FORMAT}" == "prometheus" ]]; then
        local label_str=""
        local label_str_le=""
        if [[ -n "${labels}" ]]; then
            label_str="{${labels}}"
            label_str_le="{${labels},le="
        else
            label_str_le="{le="
        fi

        _emit_line "# TYPE ${full_name} histogram"

        # Emit bucket counts
        for i in "${!bucket_bounds[@]}"; do
            _emit_line "${full_name}${label_str_le}${bucket_bounds[$i]}} ${bucket_counts[$i]}"
        done
        # +Inf bucket
        _emit_line "${full_name}${label_str_le}+Inf} ${count}"

        # Emit sum and count
        _emit_line "${full_name}_sum${label_str} ${sum}"
        _emit_line "${full_name}_count${label_str} ${count}"
    else
        # OpenTelemetry format
        local attrs=$(_parse_labels_to_attrs "${labels}")
        local attr_str=""
        if [[ -n "${attrs}" ]]; then
            attr_str=", \"attributes\": {${attrs}}"
        fi

        # Build bucket boundaries and counts arrays
        local bounds_json=""
        local counts_json=""
        for bound in "${bucket_bounds[@]}"; do
            if [[ -n "${bounds_json}" ]]; then
                bounds_json+=", "
            fi
            bounds_json+="${bound}"
        done
        for count_val in "${bucket_counts[@]}"; do
            if [[ -n "${counts_json}" ]]; then
                counts_json+=", "
            fi
            counts_json+="${count_val}"
        done

        local json='{
  "resourceMetrics": [{
    "resource": {
      "attributes": [{"key": "service.name", "value": {"stringValue": "'"${METRICS_SERVICE}"'"}}]
    },
    "scopeMetrics": [{
      "metrics": [{
        "name": "'"${full_name}"'",
        "histogram": {
          "aggregationTemporality": 2,
          "dataPoints": [{
            "timeUnixNano": "'"${timestamp}000000000"'",
            "count": "'"${count}"'",
            "sum": '"${sum}"',
            "bucketCounts": ['"${counts_json}"', '"${count}"'],
            "explicitBounds": ['"${bounds_json}"']
            '"${attr_str}"'
          }]
        }
      }]
    }]
  }]
}'
        _emit_line "${json}"
    fi
}

#
# Emit a summary metric (similar to histogram but calculates quantiles)
# Note: For simplicity, this uses a sliding window approach
#
# Arguments:
#   $1 - metric_name - Name of the summary metric
#   $2 - value - Value to record
#   $@ - Optional labels as key=value pairs
#
# Example:
#   emit_summary "request_latency_seconds" 0.234 "endpoint=/api"
#
emit_summary() {
    local metric_name="$1"
    local value="$2"
    shift 2 2>/dev/null || shift $#
    local labels="$*"
    local timestamp=$(_get_timestamp_unix)
    local full_name="${METRICS_NAMESPACE}_${metric_name}"

    # For summary, we emit as a simple gauge with quantile approximation
    # A full implementation would maintain a streaming quantile data structure
    if [[ "${METRICS_FORMAT}" == "prometheus" ]]; then
        local label_str=""
        if [[ -n "${labels}" ]]; then
            label_str="{${labels}}"
        fi
        _emit_line "# TYPE ${full_name} summary"
        _emit_line "${full_name}${label_str} ${value}"
    else
        # OpenTelemetry doesn't have summary, use gauge
        emit_gauge "${metric_name}" "${value}" "${labels}"
    fi
}

#
# Flush all pending metrics
#
# This function forces emission of any buffered metrics
#
flush_metrics() {
    # Emit any remaining timers as errors (unmatched start_timer)
    for timer_key in "${!METRICS_TIMERS[@]}"; do
        echo "Warning: Unmatched timer: ${timer_key}" >&2
    done

    # Clear all storage
    METRICS_TIMERS=()
    METRICS_COUNTERS=()
    METRICS_GAUGES=()
    METRICS_HISTOGRAM_BUCKETS=()
    METRICS_HISTOGRAM_COUNTS=()
    METRICS_HISTOGRAM_SUMS=()
}

#
# Get current counter value
#
# Arguments:
#   $1 - metric_name - Name of the counter
#   $@ - Optional labels
#
# Returns: Current counter value
#
get_counter() {
    local metric_name="$1"
    shift
    local labels="$*"
    local counter_key="${metric_name}:${labels}"
    echo "${METRICS_COUNTERS[${counter_key}]:-0}"
}

#
# Get current gauge value
#
# Arguments:
#   $1 - metric_name - Name of the gauge
#   $@ - Optional labels
#
# Returns: Current gauge value
#
get_gauge() {
    local metric_name="$1"
    shift
    local labels="$*"
    local gauge_key="${metric_name}:${labels}"
    echo "${METRICS_GAUGES[${gauge_key}]:-0}"
}

#
# Set default labels for all metrics
#
# Arguments:
#   $@ - Labels as key=value pairs (comma-separated or space-separated)
#
# Example:
#   set_default_labels "service=yawl,version=6.0"
#
set_default_labels() {
    local labels="$*"
    # Replace spaces with commas if needed
    METRICS_LABELS="${labels// /,}"
}

#
# Configure metrics output
#
# Arguments:
#   $1 - output - stdout, stderr, file, or http
#   $2 - Additional config (file path or endpoint URL)
#
configure_metrics() {
    local output="$1"
    local config="${2:-}"

    METRICS_OUTPUT="${output}"

    case "${output}" in
        file)
            METRICS_FILE="${config:-${METRICS_FILE}}"
            # Ensure directory exists
            mkdir -p "$(dirname "${METRICS_FILE}")" 2>/dev/null || true
            ;;
        http)
            METRICS_ENDPOINT="${config:-${METRICS_ENDPOINT}}"
            ;;
    esac
}

#
# Set metrics format
#
# Arguments:
#   $1 - format - otel or prometheus
#
set_metrics_format() {
    local format="$1"
    if [[ "${format}" == "otel" || "${format}" == "prometheus" ]]; then
        METRICS_FORMAT="${format}"
    else
        echo "Error: Unknown format '${format}'. Use 'otel' or 'prometheus'." >&2
        return 1
    fi
}

#
# Set service name for metrics
#
# Arguments:
#   $1 - service_name - Name of the service emitting metrics
#
set_metrics_service() {
    local service_name="$1"
    METRICS_SERVICE="${service_name}"
}

#
# Set namespace for metrics
#
# Arguments:
#   $1 - namespace - Namespace prefix for all metrics
#
set_metrics_namespace() {
    local namespace="$1"
    METRICS_NAMESPACE="${namespace}"
}

# Export functions for use in subshells
export -f start_timer end_timer emit_counter emit_gauge emit_histogram
export -f flush_metrics get_counter get_gauge set_default_labels
export -f configure_metrics set_metrics_format set_metrics_service set_metrics_namespace
export -f _get_timestamp_ns _get_timestamp_iso _get_timestamp_unix
export -f _build_labels _parse_labels_to_attrs _emit_line _emit_http _emit_timer_metric
