#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# emit-structured.sh — Structured output (JSON and YAML) for facts and analysis
#
# Pure bash implementations that work without requiring jq or yq.
# Provides functions for emitting structured data in JSON and YAML formats.
#
# Usage:
#   source lib/emit-structured.sh
#   emit_fact_json "module_count" "42"
#   emit_fact_yaml "module_count" "42"
#   emit_summary_json > output.json
#   emit_summary_yaml > output.yaml
#
# Requirements: Bash 4.0+ (for associative arrays)
# ==========================================================================

# ── Check Bash version for associative array support ─────────────────────────
if [[ "${BASH_VERSINFO[0]}" -lt 4 ]]; then
    echo "ERROR: emit-structured.sh requires Bash 4.0 or later for associative arrays" >&2
    echo "  Current version: ${BASH_VERSION}" >&2
    return 1 2>/dev/null || exit 1
fi

# ── Accumulator for structured data ─────────────────────────────────────────
declare -A STRUCTURED_FACTS=()
declare -a STRUCTURED_DIAGRAMS=()
declare -A STRUCTURED_METADATA=()

# ── JSON Primitive Encoders (Pure Bash) ─────────────────────────────────────

# Encode a string value for JSON (handles escapes)
# Usage: json_encode_string "value with \"quotes\""
_json_encode_string() {
    local s="$1"
    s="${s//\\/\\\\}"       # Escape backslashes first
    s="${s//\"/\\\"}"       # Escape double quotes
    s="${s//$'\n'/\\n}"     # Escape newlines
    s="${s//$'\r'/\\r}"     # Escape carriage returns
    s="${s//$'\t'/\\t}"     # Escape tabs
    printf '%s' "$s"
}

# Encode a value with type detection (string, number, boolean, null)
# Usage: _json_encode_value "42" -> 42 (number)
#        _json_encode_value "hello" -> "hello" (string)
_json_encode_value() {
    local val="$1"

    # Handle null/empty
    if [[ -z "$val" ]]; then
        printf 'null'
        return
    fi

    # Handle booleans
    if [[ "$val" == "true" || "$val" == "false" ]]; then
        printf '%s' "$val"
        return
    fi

    # Handle numbers (integers and decimals)
    if [[ "$val" =~ ^-?[0-9]+$ ]]; then
        printf '%s' "$val"
        return
    fi
    if [[ "$val" =~ ^-?[0-9]+\.[0-9]+$ ]]; then
        printf '%s' "$val"
        return
    fi

    # Everything else is a string
    printf '"%s"' "$(_json_encode_string "$val")"
}

# Encode an array of values to JSON
# Usage: _json_encode_array "item1" "item2" "item3"
_json_encode_array() {
    local items=("$@")
    local first=true

    printf '['
    for item in "${items[@]}"; do
        $first || printf ','
        first=false
        _json_encode_value "$item"
    done
    printf ']'
}

# Encode an associative array to JSON object
# Usage: _json_encode_object "${arr[@]}" (key=value pairs)
_json_encode_object() {
    local pairs=("$@")
    local first=true

    printf '{'
    for pair in "${pairs[@]}"; do
        $first || printf ','
        first=false
        local key="${pair%%=*}"
        local val="${pair#*=}"
        printf '"%s":' "$(_json_encode_string "$key")"
        _json_encode_value "$val"
    done
    printf '}'
}

# ── YAML Primitive Encoders (Pure Bash) ─────────────────────────────────────

# Encode a value for YAML with proper quoting
# Usage: _yaml_encode_value "value with: special chars"
_yaml_encode_value() {
    local val="$1"
    local indent="${2:-0}"

    # Handle null/empty
    if [[ -z "$val" ]]; then
        printf 'null'
        return
    fi

    # Handle booleans
    if [[ "$val" == "true" || "$val" == "false" ]]; then
        printf '%s' "$val"
        return
    fi

    # Handle numbers
    if [[ "$val" =~ ^-?[0-9]+$ ]] || [[ "$val" =~ ^-?[0-9]+\.[0-9]+$ ]]; then
        printf '%s' "$val"
        return
    fi

    # Check if quoting is needed (contains special YAML chars)
    local yaml_special='":{}[],&*#?|<>!%@'
    local needs_quote=false

    # Check for special characters
    local i char
    for ((i=0; i<${#val}; i++)); do
        char="${val:$i:1}"
        if [[ "$yaml_special" == *"$char"* ]]; then
            needs_quote=true
            break
        fi
    done

    # Check for leading digit or whitespace
    if [[ "$val" =~ ^[0-9] ]] || [[ "$val" =~ ^[[:space:]] ]] || [[ "$val" =~ [[:space:]]$ ]]; then
        needs_quote=true
    fi

    if $needs_quote; then
        # Use double quotes and escape as needed
        val="${val//\\/\\\\}"
        val="${val//\"/\\\"}"
        printf '"%s"' "$val"
    else
        printf '%s' "$val"
    fi
}

# Encode an array of values to YAML
# Usage: _yaml_encode_array "item1" "item2" "item3" --indent 2
_yaml_encode_array() {
    local -a items=()
    local indent=0
    local i

    # Parse arguments (items followed by optional --indent N)
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --indent)
                indent="$2"
                shift 2
                ;;
            *)
                items+=("$1")
                shift
                ;;
        esac
    done

    local indent_str=""
    for ((i=0; i<indent; i++)); do
        indent_str+="  "
    done

    for item in "${items[@]}"; do
        printf '\n%s- %s' "$indent_str" "$(_yaml_encode_value "$item")"
    done
}

# ── Public API: Fact Emission ───────────────────────────────────────────────

# Register a fact for later emission
# Usage: register_fact "key" "value" [type]
register_fact() {
    local key="$1"
    local value="$2"
    local type="${3:-auto}"  # auto, string, number, boolean

    STRUCTURED_FACTS["${key}"]="${type}:${value}"
}

# Register metadata about the analysis run
# Usage: register_metadata "run_id" "20250217T120000Z"
register_metadata() {
    local key="$1"
    local value="$2"
    STRUCTURED_METADATA["${key}"]="$value"
}

# Register a diagram for summary emission
# Usage: register_diagram "name" "path" "type"
register_diagram() {
    local name="$1"
    local path="$2"
    local dtype="${3:-mmd}"
    STRUCTURED_DIAGRAMS+=("name=${name}|path=${path}|type=${dtype}")
}

# Emit a single fact as JSON
# Usage: emit_fact_json "key" "value"
# Output: {"key": "value"}
emit_fact_json() {
    local key="$1"
    local value="$2"

    printf '{\n'
    printf '  "%s": %s\n' "$(_json_encode_string "$key")" "$(_json_encode_value "$value")"
    printf '}\n'
}

# Emit a single fact as YAML
# Usage: emit_fact_yaml "key" "value"
# Output: key: value
emit_fact_yaml() {
    local key="$1"
    local value="$2"

    printf '%s: %s\n' "$key" "$(_yaml_encode_value "$value")"
}

# Emit multiple facts as a JSON object
# Usage: emit_facts_json "key1=value1" "key2=value2" ...
emit_facts_json() {
    local pairs=("$@")
    local first=true

    printf '{\n'
    for pair in "${pairs[@]}"; do
        local key="${pair%%=*}"
        local val="${pair#*=}"

        $first || printf ',\n'
        first=false
        printf '  "%s": %s' "$(_json_encode_string "$key")" "$(_json_encode_value "$val")"
    done
    printf '\n}\n'
}

# Emit multiple facts as YAML
# Usage: emit_facts_yaml "key1=value1" "key2=value2" ...
emit_facts_yaml() {
    local pairs=("$@")

    for pair in "${pairs[@]}"; do
        local key="${pair%%=*}"
        local val="${pair#*=}"
        printf '%s: %s\n' "$key" "$(_yaml_encode_value "$val")"
    done
}

# ── Public API: Diagram Info Emission ───────────────────────────────────────

# Emit diagram information as JSON
# Usage: emit_diagram_json "name" "path" ["type" ["description"]]
emit_diagram_json() {
    local name="$1"
    local path="$2"
    local dtype="${3:-mmd}"
    local description="${4:-}"

    printf '{\n'
    printf '  "name": %s,\n' "$(_json_encode_value "$name")"
    printf '  "path": %s,\n' "$(_json_encode_value "$path")"
    printf '  "type": %s' "$(_json_encode_value "$dtype")"

    if [[ -n "$description" ]]; then
        printf ',\n  "description": %s' "$(_json_encode_value "$description")"
    fi
    printf '\n}\n'
}

# Emit diagram information as YAML
# Usage: emit_diagram_yaml "name" "path" ["type" ["description"]]
emit_diagram_yaml() {
    local name="$1"
    local path="$2"
    local dtype="${3:-mmd}"
    local description="${4:-}"

    printf 'name: %s\n' "$(_yaml_encode_value "$name")"
    printf 'path: %s\n' "$(_yaml_encode_value "$path")"
    printf 'type: %s\n' "$(_yaml_encode_value "$dtype")"
    if [[ -n "$description" ]]; then
        printf 'description: %s\n' "$(_yaml_encode_value "$description")"
    fi
}

# ── Public API: Full Summary Emission ───────────────────────────────────────

# Emit a complete analysis summary as JSON
# Includes metadata, facts, and diagrams
# Usage: emit_summary_json [title]
emit_summary_json() {
    local title="${1:-YAWL Observatory Analysis}"

    local first=true

    printf '{\n'
    printf '  "title": %s,\n' "$(_json_encode_value "$title")"

    # Metadata section
    printf '  "metadata": {\n'
    first=true
    for key in "${!STRUCTURED_METADATA[@]}"; do
        $first || printf ',\n'
        first=false
        printf '    "%s": %s' "$(_json_encode_string "$key")" "$(_json_encode_value "${STRUCTURED_METADATA[$key]}")"
    done
    printf '\n  },\n'

    # Facts section
    printf '  "facts": {\n'
    first=true
    for key in "${!STRUCTURED_FACTS[@]}"; do
        local entry="${STRUCTURED_FACTS[$key]}"
        local type="${entry%%:*}"
        local val="${entry#*:}"

        $first || printf ',\n'
        first=false
        printf '    "%s": %s' "$(_json_encode_string "$key")" "$(_json_encode_value "$val")"
    done
    printf '\n  },\n'

    # Diagrams section
    printf '  "diagrams": [\n'
    first=true
    for entry in "${STRUCTURED_DIAGRAMS[@]}"; do
        local name path dtype
        name=$(echo "$entry" | sed 's/.*name=\([^|]*\).*/\1/')
        path=$(echo "$entry" | sed 's/.*path=\([^|]*\).*/\1/')
        dtype=$(echo "$entry" | sed 's/.*type=\([^|]*\).*/\1/')

        $first || printf ',\n'
        first=false
        printf '    {\n'
        printf '      "name": %s,\n' "$(_json_encode_value "$name")"
        printf '      "path": %s,\n' "$(_json_encode_value "$path")"
        printf '      "type": %s\n' "$(_json_encode_value "$dtype")"
        printf '    }'
    done
    printf '\n  ],\n'

    # Summary counts
    printf '  "counts": {\n'
    printf '    "facts": %d,\n' "${#STRUCTURED_FACTS[@]}"
    printf '    "diagrams": %d,\n' "${#STRUCTURED_DIAGRAMS[@]}"
    printf '    "metadata_fields": %d\n' "${#STRUCTURED_METADATA[@]}"
    printf '  }\n'

    printf '}\n'
}

# Emit a complete analysis summary as YAML
# Usage: emit_summary_yaml [title]
emit_summary_yaml() {
    local title="${1:-YAWL Observatory Analysis}"

    printf 'title: %s\n' "$(_yaml_encode_value "$title")"
    printf '\n'

    # Metadata section
    printf 'metadata:\n'
    for key in "${!STRUCTURED_METADATA[@]}"; do
        printf '  %s: %s\n' "$key" "$(_yaml_encode_value "${STRUCTURED_METADATA[$key]}")"
    done

    # Facts section
    printf '\nfacts:\n'
    for key in "${!STRUCTURED_FACTS[@]}"; do
        local entry="${STRUCTURED_FACTS[$key]}"
        local val="${entry#*:}"
        printf '  %s: %s\n' "$key" "$(_yaml_encode_value "$val")"
    done

    # Diagrams section
    printf '\ndiagrams:\n'
    for entry in "${STRUCTURED_DIAGRAMS[@]}"; do
        local name path dtype
        name=$(echo "$entry" | sed 's/.*name=\([^|]*\).*/\1/')
        path=$(echo "$entry" | sed 's/.*path=\([^|]*\).*/\1/')
        dtype=$(echo "$entry" | sed 's/.*type=\([^|]*\).*/\1/')

        printf '  - name: %s\n' "$(_yaml_encode_value "$name")"
        printf '    path: %s\n' "$(_yaml_encode_value "$path")"
        printf '    type: %s\n' "$(_yaml_encode_value "$dtype")"
    done

    # Summary counts
    printf '\ncounts:\n'
    printf '  facts: %d\n' "${#STRUCTURED_FACTS[@]}"
    printf '  diagrams: %d\n' "${#STRUCTURED_DIAGRAMS[@]}"
    printf '  metadata_fields: %d\n' "${#STRUCTURED_METADATA[@]}"
}

# ── Public API: Write to File ───────────────────────────────────────────────

# Write facts JSON to a file
# Usage: emit_facts_to_json_file "output.json" "key1=value1" "key2=value2"
emit_facts_to_json_file() {
    local outfile="$1"
    shift
    local pairs=("$@")

    emit_facts_json "${pairs[@]}" > "$outfile"
    echo "$outfile"
}

# Write facts YAML to a file
# Usage: emit_facts_to_yaml_file "output.yaml" "key1=value1" "key2=value2"
emit_facts_to_yaml_file() {
    local outfile="$1"
    shift
    local pairs=("$@")

    emit_facts_yaml "${pairs[@]}" > "$outfile"
    echo "$outfile"
}

# Write summary JSON to a file
# Usage: emit_summary_to_json_file "output.json" [title]
emit_summary_to_json_file() {
    local outfile="$1"
    local title="${2:-YAWL Observatory Analysis}"

    emit_summary_json "$title" > "$outfile"
    echo "$outfile"
}

# Write summary YAML to a file
# Usage: emit_summary_to_yaml_file "output.yaml" [title]
emit_summary_to_yaml_file() {
    local outfile="$1"
    local title="${2:-YAWL Observatory Analysis}"

    emit_summary_yaml "$title" > "$outfile"
    echo "$outfile"
}

# ── Utility: Clear Accumulators ─────────────────────────────────────────────

# Clear all accumulated structured data
# Usage: clear_structured_data
clear_structured_data() {
    STRUCTURED_FACTS=()
    STRUCTURED_DIAGRAMS=()
    STRUCTURED_METADATA=()
}

# ── Utility: JSON to YAML Conversion (Simple) ───────────────────────────────

# Convert simple JSON to YAML (handles basic structures)
# Note: This is a simple implementation for flat JSON objects
# Usage: json_to_yaml_simple '{"key": "value", "number": 42}'
json_to_yaml_simple() {
    local json="$1"

    # Remove outer braces and whitespace
    json="${json#\{}"
    json="${json%\}}"
    json="${json//[[:space:]]+/ }"

    # Split by commas (simple approach, doesn't handle nested structures)
    local IFS=','
    local -a pairs=($json)

    for pair in "${pairs[@]}"; do
        # Extract key and value
        local key val
        key=$(echo "$pair" | sed 's/.*"\([^"]*\)"[[:space:]]*:[[:space:]]*\(.*\)/\1/')
        val=$(echo "$pair" | sed 's/.*"\([^"]*\)"[[:space:]]*:[[:space:]]*\(.*\)/\2/')

        # Clean up value (remove quotes if string)
        val="${val#\"}"
        val="${val%\"}"

        printf '%s: %s\n' "$key" "$val"
    done
}

# ── Utility: Validate JSON Structure (Simple) ────────────────────────────────

# Simple JSON validation (checks balanced braces/brackets)
# Usage: validate_json_simple '{"key": "value"}' && echo "valid"
validate_json_simple() {
    local json="$1"
    local brace_count=0
    local bracket_count=0
    local in_string=false
    local prev_char=""
    local len=${#json}
    local i char

    for ((i=0; i<len; i++)); do
        char="${json:$i:1}"

        if [[ "$prev_char" != "\\" ]]; then
            if [[ "$char" == '"' ]]; then
                in_string=$(! $in_string && echo true || echo false)
            fi
        fi

        if [[ "$in_string" != "true" ]]; then
            case "$char" in
                '{') ((brace_count++)) ;;
                '}') ((brace_count--)) ;;
                '[') ((bracket_count++)) ;;
                ']') ((bracket_count--)) ;;
            esac
        fi

        # Early exit on negative counts (unbalanced)
        [[ $brace_count -lt 0 ]] && return 1
        [[ $bracket_count -lt 0 ]] && return 1

        prev_char="$char"
    done

    # Final validation
    [[ $brace_count -eq 0 && $bracket_count -eq 0 ]]
}

# ── Self-Test Function ──────────────────────────────────────────────────────

# Run self-tests for the structured output library
# Usage: test_emit_structured
test_emit_structured() {
    echo "=== emit-structured.sh Self-Test ==="
    echo ""

    # Test 1: JSON encoding
    echo "Test 1: JSON String Encoding"
    local encoded
    encoded=$(_json_encode_string 'Hello "World" with\backslash')
    echo "  Input:  Hello \"World\" with\\backslash"
    echo "  Output: $encoded"
    echo ""

    # Test 2: JSON value encoding
    echo "Test 2: JSON Value Encoding"
    echo "  String: $(emit_fact_json "name" "test-value")"
    echo "  Number: $(emit_fact_json "count" "42")"
    echo "  Boolean: $(emit_fact_json "active" "true")"
    echo ""

    # Test 3: YAML encoding
    echo "Test 3: YAML Fact Emission"
    emit_fact_yaml "name" "test-value"
    emit_fact_yaml "count" "42"
    emit_fact_yaml "has_special:chars" "true"
    echo ""

    # Test 4: Multiple facts
    echo "Test 4: Multiple Facts JSON"
    emit_facts_json "module_count=15" "test_count=89" "status=passing"
    echo ""

    # Test 5: Multiple facts YAML
    echo "Test 5: Multiple Facts YAML"
    emit_facts_yaml "module_count=15" "test_count=89" "status=passing"
    echo ""

    # Test 6: Diagram info
    echo "Test 6: Diagram Info"
    emit_diagram_json "architecture" "diagrams/arch.mmd" "mermaid" "System architecture"
    echo ""

    # Test 7: Summary with accumulated data
    echo "Test 7: Full Summary"
    clear_structured_data
    register_metadata "run_id" "20250217T120000Z"
    register_metadata "branch" "main"
    register_fact "modules" "15"
    register_fact "tests" "89"
    register_diagram "architecture" "diagrams/arch.mmd" "mermaid"
    register_diagram "sequence" "diagrams/seq.mmd" "mermaid"

    echo "--- JSON Summary ---"
    emit_summary_json "Test Analysis"
    echo ""

    echo "--- YAML Summary ---"
    emit_summary_yaml "Test Analysis"
    echo ""

    # Test 8: JSON validation
    echo "Test 8: JSON Validation"
    local test_json='{"key": "value", "number": 42}'
    if validate_json_simple "$test_json"; then
        echo "  '$test_json' -> VALID"
    else
        echo "  '$test_json' -> INVALID"
    fi

    test_json='{"key": "value", "nested": {"broken"'
    if validate_json_simple "$test_json"; then
        echo "  '$test_json' -> VALID (unexpected)"
    else
        echo "  '$test_json' -> INVALID (expected)"
    fi
    echo ""

    echo "=== All Tests Complete ==="
}
