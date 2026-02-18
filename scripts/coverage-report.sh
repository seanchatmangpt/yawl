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

python3 - "${COVERAGE_JSON}" << 'PYEOF'
import sys, json

data = json.load(open(sys.argv[1]))
agg = data.get("aggregate", {})
modules = data.get("modules", [])

print()
print("╔══════════════════════════════════════════════════════════════╗")
print("║          YAWL Coverage Dashboard — Q2 2026 Targets          ║")
print("╠══════════════════════════════════════════════════════════════╣")

# Aggregate row
line_pct = agg.get("line_pct", 0)
branch_pct = agg.get("branch_pct", 0)
target_line = agg.get("target_line_pct", 50)
target_branch = agg.get("target_branch_pct", 40)

meets_line = "OK" if float(line_pct) >= target_line else "NO"
meets_branch = "OK" if float(branch_pct) >= target_branch else "NO"

print(f"║  AGGREGATE  Line: {line_pct:>5}% [{meets_line}] (target {target_line}%)  Branch: {branch_pct:>5}% [{meets_branch}] (target {target_branch}%)")
print("╠═══════════════════════════════╦══════════╦══════════════════╣")
print("║ Module                        ║  Line %  ║  Status          ║")
print("╠═══════════════════════════════╬══════════╬══════════════════╣")

for m in modules:
    name = m["module"][:29].ljust(29)
    status = m.get("status", "?")
    if status == "no_report":
        print(f"║ {name} ║   N/A    ║  not measured    ║")
    else:
        lp = float(m.get("line_pct", 0))
        bar_len = int(lp / 10)
        bar = "#" * bar_len + "." * (10 - bar_len)
        print(f"║ {name} ║ {lp:>6.1f}%  ║ {bar}  ║")

print("╚═══════════════════════════════╩══════════╩══════════════════╝")
print()
print(f"Generated: {data.get('generated_at', 'unknown')}")
print("Update: mvn test && bash scripts/observatory/observatory.sh --facts")
print()
PYEOF
