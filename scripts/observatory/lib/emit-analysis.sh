#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# emit-analysis.sh — Shell Script Static Analysis Library for Observatory
#
# Provides static analysis functions for shell scripts, integrating
# ShellCheck and other analysis tools with the observatory system.
#
# Functions:
#   run_shellcheck file         - Run shellcheck and emit results
#   analyze_script_complexity   - Calculate cyclomatic complexity
#   check_bash_compatibility    - Check for bash 3.2 compatibility issues
#   emit_analysis_summary       - Output analysis summary as facts
#
# Outputs:
#   facts/shell-analysis.json   - Aggregated shell script analysis
#   facts/shellcheck-findings.json - ShellCheck warnings/errors
#   facts/script-complexity.json - Complexity metrics per script
# ==========================================================================

# Source utilities if not already loaded
if [[ -z "${FACTS_DIR:-}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "${SCRIPT_DIR}/util.sh"
fi

# ── Directory for shell analysis artifacts ───────────────────────────────────
SHELL_ANALYSIS_DIR="${OUT_DIR}/shell-analysis"
SHELL_ANALYSIS_HISTORY_DIR="${REPO_ROOT}/docs/v6/shell-analysis-history"

# ── Accumulators for analysis results ────────────────────────────────────────
declare -a SHELLCHECK_FINDINGS=()
declare -a COMPLEXITY_RESULTS=()
declare -a COMPATIBILITY_ISSUES=()
declare -A SCRIPT_METRICS=()

# ==========================================================================
# SHELLCHECK INTEGRATION
# ==========================================================================

# Check if shellcheck is available
has_shellcheck() {
    command -v shellcheck >/dev/null 2>&1
}

# Run shellcheck on a single file and return JSON results
# Arguments:
#   $1 - Path to shell script file
# Returns:
#   JSON array of findings to stdout
run_shellcheck() {
    local file="$1"
    local op_start
    op_start=$(epoch_ms)

    if [[ ! -f "$file" ]]; then
        log_warn "File not found: $file"
        echo '[]'
        return 1
    fi

    if ! has_shellcheck; then
        log_warn "shellcheck not installed. Install with: brew install shellcheck"
        echo '[]'
        return 1
    fi

    # Run shellcheck with JSON output format
    # -e SC1090,SC1091: Disable "Can't follow non-constant source" warnings
    # -x: Follow external sources (but with caution)
    local shellcheck_output
    shellcheck_output=$(shellcheck -f json -e SC1090,SC1091 "$file" 2>/dev/null || true)

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "run_shellcheck" "$op_elapsed"

    # If no output, return empty array
    if [[ -z "$shellcheck_output" ]]; then
        echo '[]'
        return 0
    fi

    # Normalize the JSON output - pass data via environment and file as argument
    python3 -c '
import json
import os
import sys

try:
    shellcheck_output = os.environ.get("SHELLCHECK_OUTPUT", "[]")
    findings = json.loads(shellcheck_output) if shellcheck_output else []
except json.JSONDecodeError:
    findings = []

for f in findings:
    f["source_file"] = sys.argv[1]

print(json.dumps(findings))
' "$file" SHELLCHECK_OUTPUT="$shellcheck_output"
}

# Run shellcheck on multiple files and aggregate results
emit_shellcheck_findings() {
    local out="$FACTS_DIR/shellcheck-findings.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/shellcheck-findings.json ..."

    ensure_analysis_dirs

    if ! has_shellcheck; then
        log_warn "shellcheck not installed. Run: brew install shellcheck"
        echo '{"findings": [], "summary": {"total": 0, "by_severity": {}, "by_code": {}}, "status": "shellcheck_not_installed"}' > "$out"
        return 0
    fi

    # Find all shell scripts in the repository
    local -a scripts=()
    while IFS= read -r script; do
        [[ -n "$script" ]] && scripts+=("$script")
    done < <(find "$REPO_ROOT" \
        \( -name "*.sh" -o -name "*.bash" \) \
        -type f \
        ! -path "*/node_modules/*" \
        ! -path "*/.git/*" \
        ! -path "*/target/*" \
        2>/dev/null)

    if [[ ${#scripts[@]} -eq 0 ]]; then
        log_warn "No shell scripts found in repository"
        echo '{"findings": [], "summary": {"total": 0, "by_severity": {}, "by_code": {}}, "status": "no_scripts"}' > "$out"
        return 0
    fi

    local total_findings=0
    local -A severity_counts=()
    local -A code_counts=()

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "shellcheck_version": "%s",\n' "$(shellcheck --version 2>/dev/null | head -1 | awk '{print $2}')"
        printf '  "scripts_analyzed": %d,\n' "${#scripts[@]}"
        printf '  "findings": [\n'

        local first_finding=true
        for script in "${scripts[@]}"; do
            local rel_path="${script#"$REPO_ROOT"/}"
            local findings
            findings=$(run_shellcheck "$script")

            if [[ "$findings" != "[]" ]]; then
                # Process and output findings
                python3 << PROCESS_EOF "$findings" "$rel_path" "$first_finding"
import json
import sys

findings = json.loads(sys.argv[1])
first = sys.argv[3] == 'True'

for f in findings:
    if not first:
        print(',')
    first = False
    f['source_file'] = sys.argv[2]
    print('    ' + json.dumps(f), end='')
PROCESS_EOF
                first_finding=false

                # Count findings
                local count
                count=$(python3 -c "import json; print(len(json.loads('''$findings''')))" 2>/dev/null || echo "0")
                total_findings=$((total_findings + count))
            fi
        done

        printf '\n  ],\n'
        printf '  "summary": {\n'
        printf '    "total": %d,\n' "$total_findings"
        printf '    "scripts_with_issues": %d,\n' "$(python3 -c "
import json
with open('$out.tmp', 'r') as f:
    data = json.load(f)
print(sum(1 for s in data.get('scripts', []) if s.get('findings', 0) > 0))
" 2>/dev/null || echo "0")"
        printf '    "by_severity": {},\n'
        printf '    "by_code": {}\n'
        printf '  }\n'
        printf '}\n'
    } > "${out}.tmp"

    # Post-process to fill in summary statistics
    python3 << SUMMARY_EOF "${out}.tmp" "$out"
import json

with open(sys.argv[1], 'r') as f:
    data = json.load(f)

severity_counts = {}
code_counts = {}

for finding in data.get('findings', []):
    level = finding.get('level', 'unknown')
    code = finding.get('code', 'unknown')
    severity_counts[level] = severity_counts.get(level, 0) + 1
    code_counts[code] = code_counts.get(code, 0) + 1

data['summary']['by_severity'] = severity_counts
data['summary']['by_code'] = code_counts

with open(sys.argv[2], 'w') as f:
    json.dump(data, f, indent=2)
SUMMARY_EOF

    rm -f "${out}.tmp"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_shellcheck_findings" "$op_elapsed"

    log_ok "ShellCheck findings: ${total_findings} issues in ${#scripts[@]} scripts"
}

# ==========================================================================
# CYCLOMATIC COMPLEXITY ANALYSIS
# ==========================================================================

# Analyze cyclomatic complexity of a shell script
# Complexity is calculated based on:
# - Number of functions
# - Number of conditionals (if, case, etc.)
# - Number of loops (for, while, until)
# - Nesting depth
# Arguments:
#   $1 - Path to shell script file
# Returns:
#   JSON object with complexity metrics
analyze_script_complexity() {
    local file="$1"
    local op_start
    op_start=$(epoch_ms)

    if [[ ! -f "$file" ]]; then
        log_warn "File not found: $file"
        echo '{"error": "file_not_found"}'
        return 1
    fi

    # Use a temporary file for the Python script for better portability
    local py_script
    py_script=$(mktemp)
    cat > "$py_script" << 'PYSCRIPT'
import re
import json
import sys

file_path = sys.argv[1]

try:
    with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
        content = f.read()
        lines = content.split('\n')
except Exception as e:
    print(json.dumps({"error": str(e)}))
    sys.exit(0)

# Count various complexity factors
function_count = len(re.findall(r'^\s*(function\s+\w+|[\w_]+\s*\(\))', content, re.MULTILINE))

# Conditionals
if_count = len(re.findall(r'\bif\b', content))
case_count = len(re.findall(r'\bcase\b', content))

# Loops
for_count = len(re.findall(r'\bfor\b', content))
while_count = len(re.findall(r'\bwhile\b', content))
until_count = len(re.findall(r'\buntil\b', content))

# Logical operators that add complexity
and_count = len(re.findall(r'&&', content))
or_count = len(re.findall(r'\|\|', content))

# Calculate nesting depth
def max_nesting_depth(lines):
    max_depth = 0
    current_depth = 0
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('#'):
            continue
        if re.search(r'\b(if|case|for|while|until|function)\b', line):
            current_depth += 1
            max_depth = max(max_depth, current_depth)
        if re.search(r'\b(fi|esac|done)\b', line) or (stripped == '}' and current_depth > 0):
            current_depth = max(0, current_depth - 1)
    return max_depth

nesting_depth = max_nesting_depth(lines)

total_lines = len(lines)
non_empty_lines = len([l for l in lines if l.strip() and not l.strip().startswith('#')])
comment_lines = len([l for l in lines if l.strip().startswith('#')])

cyclomatic = 1
cyclomatic += if_count
cyclomatic += case_count
cyclomatic += for_count
cyclomatic += while_count
cyclomatic += until_count
cyclomatic += and_count
cyclomatic += or_count

if cyclomatic <= 10:
    rating = "LOW"
elif cyclomatic <= 20:
    rating = "MEDIUM"
elif cyclomatic <= 40:
    rating = "HIGH"
else:
    rating = "VERY_HIGH"

result = {
    "file": file_path,
    "metrics": {
        "cyclomatic_complexity": cyclomatic,
        "complexity_rating": rating,
        "nesting_depth": nesting_depth,
        "function_count": function_count,
        "decision_points": {
            "if_statements": if_count,
            "case_statements": case_count,
            "loops": for_count + while_count + until_count,
            "logical_operators": and_count + or_count
        },
        "lines": {
            "total": total_lines,
            "code": non_empty_lines,
            "comments": comment_lines
        }
    }
}

print(json.dumps(result))
PYSCRIPT

    python3 "$py_script" "$file"
    rm -f "$py_script"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "analyze_script_complexity" "$op_elapsed"
}

# Emit complexity metrics for all shell scripts
emit_script_complexity() {
    local out="$FACTS_DIR/script-complexity.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/script-complexity.json ..."

    ensure_analysis_dirs

    # Find all shell scripts
    local -a scripts=()
    while IFS= read -r script; do
        [[ -n "$script" ]] && scripts+=("$script")
    done < <(find "$REPO_ROOT" \
        \( -name "*.sh" -o -name "*.bash" \) \
        -type f \
        ! -path "*/node_modules/*" \
        ! -path "*/.git/*" \
        ! -path "*/target/*" \
        2>/dev/null)

    if [[ ${#scripts[@]} -eq 0 ]]; then
        echo '{"scripts": [], "summary": {"total_scripts": 0, "avg_complexity": 0, "high_complexity_count": 0}}' > "$out"
        return 0
    fi

    local total_complexity=0
    local high_complexity_count=0
    local -a script_results=()

    for script in "${scripts[@]}"; do
        local rel_path="${script#"$REPO_ROOT"/}"
        local result
        result=$(analyze_script_complexity "$script")

        if [[ -n "$result" && "$result" != '{"error": "file_not_found"}' ]]; then
            script_results+=("$result")

            local complexity
            complexity=$(python3 -c "import json; print(json.loads('''$result''').get('metrics', {}).get('cyclomatic_complexity', 0))" 2>/dev/null || echo "0")
            total_complexity=$((total_complexity + complexity))

            if [[ "$complexity" -gt 20 ]]; then
                high_complexity_count=$((high_complexity_count + 1))
            fi
        fi
    done

    local avg_complexity=0
    if [[ ${#script_results[@]} -gt 0 ]]; then
        avg_complexity=$((total_complexity / ${#script_results[@]}))
    fi

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "scripts": [\n'

        local first=true
        for result in "${script_results[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$result"
        done

        printf '\n  ],\n'
        printf '  "summary": {\n'
        printf '    "total_scripts": %d,\n' "${#script_results[@]}"
        printf '    "total_complexity": %d,\n' "$total_complexity"
        printf '    "avg_complexity": %d,\n' "$avg_complexity"
        printf '    "high_complexity_count": %d,\n' "$high_complexity_count"
        printf '    "complexity_threshold": 20\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_script_complexity" "$op_elapsed"

    log_ok "Script complexity: avg=${avg_complexity}, high_complexity=${high_complexity_count}"
}

# ==========================================================================
# BASH COMPATIBILITY CHECK
# ==========================================================================

# Check for bash 3.2 compatibility issues (macOS default)
# Arguments:
#   $1 - Path to shell script file
# Returns:
#   JSON array of compatibility issues
check_bash_compatibility() {
    local file="$1"
    local op_start
    op_start=$(epoch_ms)

    if [[ ! -f "$file" ]]; then
        log_warn "File not found: $file"
        echo '[]'
        return 1
    fi

    # Use a temporary file for the Python script for better portability
    local py_script
    py_script=$(mktemp)
    cat > "$py_script" << 'PYSCRIPT'
import re
import json
import sys

file_path = sys.argv[1]
issues = []

try:
    with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
        lines = f.readlines()
except Exception as e:
    print(json.dumps([{"error": str(e)}]))
    sys.exit(0)

# Patterns that are incompatible with bash 3.2
compat_patterns = [
    (r'declare\s+-A\s+', "Associative arrays require bash 4.0+", "bash4"),
    (r'declare\s+-A$', "Associative arrays require bash 4.0+", "bash4"),
    (r'\bmapfile\b', "mapfile requires bash 4.0+", "bash4"),
    (r'\breadarray\b', "readarray requires bash 4.0+", "bash4"),
    (r'globstar', "globstar option requires bash 4.0+", "bash4"),
    (r'shopt\s+-s\s+globstar', "globstar option requires bash 4.0+", "bash4"),
    (r'\bcoproc\b', "coproc requires bash 4.0+", "bash4"),
    (r'for\s+\w+\s+in\s+\$\{?\w+\}?\[@\]', "Array iteration syntax may require bash 4.3+", "bash4.3"),
    (r'declare\s+-n\s+', "nameref requires bash 4.3+", "bash4.3"),
    (r'local\s+-n\s+', "nameref requires bash 4.3+", "bash4.3"),
    (r'\[\[\s+-v\s+\w+\s*\]\]', "-v test requires bash 4.2+", "bash4.2"),
    (r'extglob', "extglob behavior differs in bash 3.2", "extglob"),
    (r'<\([^)]+\)', "Process substitution may have issues in bash 3.2", "procsub"),
    (r'=\~\s*\[\[', "Regex operator behavior differs in bash 3.2", "regex"),
    (r'=\~.*\\d', "Using \\d in regex requires quoting in bash 3.2", "regex"),
]

for line_num, line in enumerate(lines, 1):
    for pattern, message, category in compat_patterns:
        if re.search(pattern, line):
            issues.append({
                "line": line_num,
                "category": category,
                "message": message,
                "content": line.strip()[:100],
                "source_file": file_path
            })

if lines and lines[0].startswith('#!'):
    shebang = lines[0].strip()
    if 'bash' in shebang:
        if 'bash-4' in shebang or 'bash/4' in shebang:
            issues.insert(0, {
                "line": 1,
                "category": "shebang",
                "message": "Script explicitly requires bash 4.x",
                "content": shebang,
                "source_file": file_path
            })

print(json.dumps(issues))
PYSCRIPT

    python3 "$py_script" "$file"
    rm -f "$py_script"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "check_bash_compatibility" "$op_elapsed"
}

# Emit compatibility issues for all shell scripts
emit_bash_compatibility() {
    local out="$FACTS_DIR/bash-compatibility.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/bash-compatibility.json ..."

    ensure_analysis_dirs

    # Find all shell scripts
    local -a scripts=()
    while IFS= read -r script; do
        [[ -n "$script" ]] && scripts+=("$script")
    done < <(find "$REPO_ROOT" \
        \( -name "*.sh" -o -name "*.bash" \) \
        -type f \
        ! -path "*/node_modules/*" \
        ! -path "*/.git/*" \
        ! -path "*/target/*" \
        2>/dev/null)

    if [[ ${#scripts[@]} -eq 0 ]]; then
        echo '{"issues": [], "summary": {"total_issues": 0, "by_category": {}}, "status": "no_scripts"}' > "$out"
        return 0
    fi

    local -a all_issues=()

    for script in "${scripts[@]}"; do
        local issues
        issues=$(check_bash_compatibility "$script")
        if [[ -n "$issues" && "$issues" != "[]" ]]; then
            all_issues+=("$issues")
        fi
    done

    # Combine all issues
    python3 << COMBINE_EOF "${all_issues[@]}"
import json
import sys

all_issues = []
for arg in sys.argv[1:]:
    try:
        issues = json.loads(arg)
        all_issues.extend(issues)
    except json.JSONDecodeError:
        pass

# Count by category
by_category = {}
for issue in all_issues:
    cat = issue.get('category', 'unknown')
    by_category[cat] = by_category.get(cat, 0) + 1

result = {
    "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "target_bash_version": "3.2",
    "scripts_checked": ${#scripts[@]},
    "issues": all_issues,
    "summary": {
        "total_issues": len(all_issues),
        "by_category": by_category
    }
}

print(json.dumps(result, indent=2))
COMBINE_EOF

    # Save to file
    python3 << COMBINE_EOF "${all_issues[@]}" > "$out"
import json
import sys
from datetime import datetime

all_issues = []
for arg in sys.argv[1:]:
    try:
        issues = json.loads(arg)
        all_issues.extend(issues)
    except json.JSONDecodeError:
        pass

by_category = {}
for issue in all_issues:
    cat = issue.get('category', 'unknown')
    by_category[cat] = by_category.get(cat, 0) + 1

result = {
    "generated_at": datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ'),
    "target_bash_version": "3.2",
    "scripts_checked": ${#scripts[@]},
    "issues": all_issues,
    "summary": {
        "total_issues": len(all_issues),
        "by_category": by_category
    }
}

print(json.dumps(result, indent=2))
COMBINE_EOF

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_bash_compatibility" "$op_elapsed"

    local issue_count=${#all_issues[@]}
    log_ok "Bash compatibility: ${issue_count} potential issues found"
}

# ==========================================================================
# ANALYSIS SUMMARY
# ==========================================================================

# Ensure analysis directories exist
ensure_analysis_dirs() {
    mkdir -p "$SHELL_ANALYSIS_DIR" "$SHELL_ANALYSIS_HISTORY_DIR"
}

# Emit aggregated shell analysis summary
emit_analysis_summary() {
    local out="$FACTS_DIR/shell-analysis.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/shell-analysis.json (aggregated summary) ..."

    ensure_analysis_dirs

    # Load counts from individual reports
    local shellcheck_count=0 complexity_high=0 compat_count=0
    local total_scripts=0 avg_complexity=0

    if [[ -f "$FACTS_DIR/shellcheck-findings.json" ]]; then
        shellcheck_count=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/shellcheck-findings.json')).get('summary', {}).get('total', 0))" 2>/dev/null || echo "0")
        total_scripts=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/shellcheck-findings.json')).get('scripts_analyzed', 0))" 2>/dev/null || echo "0")
    fi

    if [[ -f "$FACTS_DIR/script-complexity.json" ]]; then
        complexity_high=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/script-complexity.json')).get('summary', {}).get('high_complexity_count', 0))" 2>/dev/null || echo "0")
        avg_complexity=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/script-complexity.json')).get('summary', {}).get('avg_complexity', 0))" 2>/dev/null || echo "0")
    fi

    if [[ -f "$FACTS_DIR/bash-compatibility.json" ]]; then
        compat_count=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/bash-compatibility.json')).get('summary', {}).get('total_issues', 0))" 2>/dev/null || echo "0")
    fi

    # Calculate shell script health score (0-100)
    # Penalties: shellcheck errors = 2 pts each, high complexity = 5 pts each, compat issues = 3 pts each
    local penalty=$((shellcheck_count * 2 + complexity_high * 5 + compat_count * 3))
    local health_score=$((100 - penalty))
    [[ $health_score -lt 0 ]] && health_score=0

    # Determine health status
    local health_status="GREEN"
    [[ $health_score -lt 80 ]] && health_status="YELLOW"
    [[ $health_score -lt 60 ]] && health_status="RED"

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "branch": "%s",\n' "$(git_branch)"
        printf '  "health_score": %d,\n' "$health_score"
        printf '  "health_status": "%s",\n' "$health_status"
        printf '  "total_scripts": %d,\n' "$total_scripts"
        printf '  "metrics": {\n'
        printf '    "shellcheck": {\n'
        printf '      "total_issues": %d,\n' "$shellcheck_count"
        printf '      "status": "%s"\n' "$([[ $shellcheck_count -eq 0 ]] && echo "clean" || echo "issues_found")"
        printf '    },\n'
        printf '    "complexity": {\n'
        printf '      "average": %d,\n' "$avg_complexity"
        printf '      "high_complexity_scripts": %d,\n' "$complexity_high"
        printf '      "status": "%s"\n' "$([[ $complexity_high -eq 0 ]] && echo "clean" || echo "review_needed")"
        printf '    },\n'
        printf '    "compatibility": {\n'
        printf '      "bash_32_issues": %d,\n' "$compat_count"
        printf '      "status": "%s"\n' "$([[ $compat_count -eq 0 ]] && echo "compatible" || echo "review_needed")"
        printf '    }\n'
        printf '  },\n'
        printf '  "recommendations": [\n'

        # Generate recommendations
        local first_rec=true
        if [[ $shellcheck_count -gt 0 ]]; then
            printf '    {"tool": "shellcheck", "priority": "high", "action": "Fix %d shellcheck warnings for better script quality"}' "$shellcheck_count"
            first_rec=false
        fi
        if [[ $complexity_high -gt 0 ]]; then
            $first_rec || printf ',\n'
            printf '    {"tool": "complexity", "priority": "medium", "action": "Refactor %d high-complexity scripts for maintainability"}' "$complexity_high"
            first_rec=false
        fi
        if [[ $compat_count -gt 0 ]]; then
            $first_rec || printf ',\n'
            printf '    {"tool": "compatibility", "priority": "medium", "action": "Review %d bash 3.2 compatibility issues for macOS support"}' "$compat_count"
            first_rec=false
        fi

        printf '\n  ]\n'
        printf '}\n'
    } > "$out"

    # Store in history
    local history_file="$SHELL_ANALYSIS_HISTORY_DIR/history.jsonl"
    {
        printf '{"timestamp": "%s", "commit": "%s", "health_score": %d, "shellcheck": %d, "high_complexity": %d, "compat_issues": %d}\n' \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(git_commit)" "$health_score" "$shellcheck_count" "$complexity_high" "$compat_count"
    } >> "$history_file"

    # Trim history to last 100 entries
    if [[ -f "$history_file" ]]; then
        local lines
        lines=$(wc -l < "$history_file")
        if [[ "$lines" -gt 100 ]]; then
            tail -n 100 "$history_file" > "${history_file}.tmp"
            mv "${history_file}.tmp" "$history_file"
        fi
    fi

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_analysis_summary" "$op_elapsed"

    log_ok "Shell analysis summary: health_score=${health_score} status=${health_status}"
}

# ==========================================================================
# MAIN DISPATCHER
# ==========================================================================

# Emit all shell analysis facts
emit_shell_analysis_facts() {
    local op_start
    op_start=$(epoch_ms)

    ensure_analysis_dirs

    emit_shellcheck_findings
    emit_script_complexity
    emit_bash_compatibility
    emit_analysis_summary

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_shell_analysis_facts" "$op_elapsed"
}

# Run analysis on a single script (for quick checks)
analyze_single_script() {
    local script="$1"

    if [[ ! -f "$script" ]]; then
        log_error "Script not found: $script"
        return 1
    fi

    log_info "Analyzing: $script"

    echo ""
    echo "=== ShellCheck Results ==="
    run_shellcheck "$script" | python3 -c "
import json, sys
findings = json.loads(sys.stdin.read())
if not findings:
    print('No issues found.')
else:
    for f in findings:
        print(f\"Line {f.get('line', '?')}: [{f.get('level', '?')}] SC{f.get('code', '?')}: {f.get('message', '')}\")"

    echo ""
    echo "=== Complexity Analysis ==="
    analyze_script_complexity "$script" | python3 -c "
import json, sys
data = json.loads(sys.stdin.read())
m = data.get('metrics', {})
print(f\"Cyclomatic Complexity: {m.get('cyclomatic_complexity', '?')} ({m.get('complexity_rating', '?')})\")
print(f\"Nesting Depth: {m.get('nesting_depth', '?')}\")
print(f\"Functions: {m.get('function_count', '?')}\")
print(f\"Lines: {m.get('lines', {}).get('code', '?')} code, {m.get('lines', {}).get('comments', '?')} comments\")"

    echo ""
    echo "=== Bash 3.2 Compatibility ==="
    check_bash_compatibility "$script" | python3 -c "
import json, sys
issues = json.loads(sys.stdin.read())
if not issues:
    print('Compatible with bash 3.2.')
else:
    for i in issues:
        print(f\"Line {i.get('line', '?')}: [{i.get('category', '?')}] {i.get('message', '')}\")"
}

# Export functions for external use
export -f run_shellcheck
export -f analyze_script_complexity
export -f check_bash_compatibility
export -f emit_analysis_summary
