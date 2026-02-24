#!/usr/bin/env bash
# ==========================================================================
# coverage-report.sh — Coverage ASCII Dashboard
#
# Reads docs/v6/latest/facts/coverage.json and displays an ASCII table.
# Also shows progress toward Q2 2026 targets.
#
# Usage: bash scripts/coverage-report.sh
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COVERAGE_JSON="${REPO_ROOT}/docs/v6/latest/facts/coverage.json"

if [ ! -f "${COVERAGE_JSON}" ]; then
    echo "ERROR: Coverage fact not found. Run:"
    echo "  mvn test && bash scripts/observatory/observatory.sh --facts"
    exit 1
fi

line_pct=$(jq -r '.aggregate.line_pct // 0' "${COVERAGE_JSON}")
branch_pct=$(jq -r '.aggregate.branch_pct // 0' "${COVERAGE_JSON}")
target_line=$(jq -r '.aggregate.target_line_pct // 50' "${COVERAGE_JSON}")
target_branch=$(jq -r '.aggregate.target_branch_pct // 40' "${COVERAGE_JSON}")
generated_at=$(jq -r '.generated_at // "unknown"' "${COVERAGE_JSON}")

meets_line=$(awk "BEGIN {print ($line_pct >= $target_line) ? \"OK\" : \"NO\"}")
meets_branch=$(awk "BEGIN {print ($branch_pct >= $target_branch) ? \"OK\" : \"NO\"}")

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║          YAWL Coverage Dashboard — Q2 2026 Targets          ║"
echo "╠══════════════════════════════════════════════════════════════╣"
printf "║  AGGREGATE  Line: %5s%% [%s] (target %s%%)  Branch: %5s%% [%s] (target %s%%)\n" \
    "$line_pct" "$meets_line" "$target_line" "$branch_pct" "$meets_branch" "$target_branch"
echo "╠═══════════════════════════════╦══════════╦══════════════════╣"
echo "║ Module                        ║  Line %  ║  Status          ║"
echo "╠═══════════════════════════════╬══════════╬══════════════════╣"

jq -r '.modules[] | [.module, (.line_pct // "0"), (.status // "?")] | @tsv' "${COVERAGE_JSON}" | \
while IFS=$'\t' read -r name lp status; do
    name_short="${name:0:29}"
    if [[ "$status" == "no_report" ]]; then
        printf "║ %-29s ║   N/A    ║  not measured    ║\n" "$name_short"
    else
        bar_len=$(awk "BEGIN {print int($lp/10)}")
        bar=$(printf '%0.s#' $(seq 1 "$bar_len") 2>/dev/null || true)
        dots=$(printf '%0.s.' $(seq 1 $((10 - bar_len))) 2>/dev/null || true)
        printf "║ %-29s ║ %6.1f%%  ║ %s%s  ║\n" "$name_short" "$lp" "$bar" "$dots"
    fi
done

echo "╚═══════════════════════════════╩══════════╩══════════════════╝"
echo ""
echo "Generated: ${generated_at}"
echo "Update: mvn test && bash scripts/observatory/observatory.sh --facts"
echo ""
