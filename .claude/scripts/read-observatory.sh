#!/usr/bin/env bash
# read-observatory.sh — Read cached Observatory facts and display current project state
#
# This script reads the cached Observatory data and presents it in a readable format
# for Claude Code to understand the current state of the project.
#
# Usage:
#   ./scripts/read-observatory.sh          # Full summary
#   ./scripts/read-observatory.sh facts   # Show facts only
#   ./scripts/read-observatory.sh diagrams # Show diagrams only
#   ./scripts/read-observatory.sh index   # Show INDEX.md
#

set -euo pipefail

# Output directory
OBSERVATORY_DIR="docs/v6/latest"

# Check if Observatory directory exists
if [ ! -d "${OBSERVATORY_DIR}" ]; then
    echo "❌ Observatory data not found. Run 'bash scripts/observatory/observatory.sh' first."
    exit 1
fi

# Function to show receipt info
show_receipt() {
    local receipt_file="${OBSERVATORY_DIR}/receipts/observatory.json"
    if [ -f "${receipt_file}" ]; then
        echo "📊 Observatory Receipt"
        echo "====================="
        echo "Status: $(jq -r '.status // "N/A"' "${receipt_file}")"
        echo "Run ID: $(jq -r '.run_id // "N/A"' "${receipt_file}")"
        echo "Commit: $(jq -r '.repo.git.commit // "N/A"' "${receipt_file}")"
        echo "Refusals: $(jq -r '.refusals // 0' "${receipt_file}")"
        echo "Warnings: $(jq -r '.warnings // 0' "${receipt_file}")"
        echo ""
    fi
}

# Function to show INDEX.md
show_index() {
    local index_file="${OBSERVATORY_DIR}/INDEX.md"
    if [ -f "${index_file}" ]; then
        echo "📋 Observatory Index"
        echo "=================="
        head -n 10 "${index_file}"
        echo ""
        echo "Available files:"
        grep "^- " "${index_file}" | head -n 20 | sed 's/^/- /'
        if [ $(grep -c "^- " "${index_file}") -gt 20 ]; then
            echo "   ... and $(($(grep -c "^- " "${index_file}") - 20)) more files"
        fi
        echo ""
    fi
}

# Function to show facts summary
show_facts() {
    local facts_dir="${OBSERVATORY_DIR}/facts"
    if [ ! -d "${facts_dir}" ]; then
        echo "❌ No facts directory found"
        return
    fi

    echo "📊 Observatory Facts Summary"
    echo "=========================="
    echo "Total fact files: $(ls -1 "${facts_dir}"/*.json 2>/dev/null | wc -l)"
    echo ""

    # Show key facts
    echo "Key Facts:"
    echo "---------"
    if [ -f "${facts_dir}/modules.json" ]; then
        MODULES=$(jq '.modules // [] | length' "${facts_dir}/modules.json")
        echo "• Modules: ${MODULES}"
    fi

    if [ -f "${facts_dir}/reactor.json" ]; then
        PHASES=$(jq '.phases // [] | length' "${facts_dir}/reactor.json")
        echo "• Build phases: ${PHASES}"
    fi

    if [ -f "${facts_dir}/tests.json" ]; then
        TOTAL_TESTS=$(jq '.summary.total_test_files // 0' "${facts_dir}/tests.json")
        JUNIT5=$(jq '.summary.junit5_count // 0' "${facts_dir}/tests.json")
        JUNIT4=$(jq '.summary.junit4_count // 0' "${facts_dir}/tests.json")
        echo "• Tests: ${TOTAL_TESTS} total (${JUNIT5} JUnit5, ${JUNIT4} JUnit4)"
    fi

    if [ -f "${facts_dir}/integration.json" ]; then
        MCP=$(jq '.mcp_enabled // false' "${facts_dir}/integration.json")
        A2A=$(jq '.a2a_enabled // false' "${facts_dir}/integration.json")
        echo "• Integration: MCP=${MCP}, A2A=${A2A}"
    fi

    if [ -f "${facts_dir}/dual-family.json" ]; then
        STATEFUL=$(jq '.stateful // 0' "${facts_dir}/dual-family.json")
        STATELESS=$(jq '.stateless // 0' "${facts_dir}/dual-family.json")
        echo "• Engine family: Stateful=${STATEFUL}, Stateless=${STATELESS}"
    fi

    echo ""
    echo "All fact files:"
    ls -1 "${facts_dir}"/*.json | sed 's|^.*/||' | sed 's|\.json||' | sort | sed 's/^/• /'
    echo ""
}

# Function to show diagrams summary
show_diagrams() {
    local diagrams_dir="${OBSERVATORY_DIR}/diagrams"
    if [ ! -d "${diagrams_dir}" ]; then
        echo "❌ No diagrams directory found"
        return
    fi

    echo "📊 Observatory Diagrams Summary"
    echo "==============================="
    echo "Total diagram files: $(ls -1 "${diagrams_dir}"/*.mmd 2>/dev/null | wc -l)"
    echo ""

    echo "Available diagrams:"
    ls -1 "${diagrams_dir}"/*.mmd | sed 's|^.*/||' | sed 's|\.mmd||' | sort | sed 's/^/• /'
    echo ""
}

# Main logic
case "${1:-full}" in
    facts)
        show_receipt
        show_facts
        ;;
    diagrams)
        show_diagrams
        ;;
    index)
        show_index
        ;;
    *)
        show_receipt
        show_index
        show_facts
        show_diagrams
        ;;
esac