#!/usr/bin/env bash
# ==========================================================================
# emit-static-analysis.sh — Static Analysis Integration for Observatory
#
# Integrates SpotBugs, PMD, and Checkstyle findings into the observatory
# system for visualization and trend tracking.
#
# Outputs:
#   facts/static-analysis.json - Aggregated findings summary
#   facts/spotbugs-findings.json - SpotBugs bug instances
#   facts/pmd-violations.json - PMD rule violations
#   facts/checkstyle-warnings.json - Checkstyle warnings
#   diagrams/60-code-health-dashboard.mmd - Code health visualization
#   diagrams/61-static-analysis-trends.mmd - Trend visualization
# ==========================================================================

# ── Directory for static analysis reports ─────────────────────────────────
STATIC_ANALYSIS_DIR="${OUT_DIR}/static-analysis"
STATIC_ANALYSIS_HISTORY_DIR="${REPO_ROOT}/docs/v6/static-analysis-history"

# ── Ensure directories exist ──────────────────────────────────────────────
ensure_static_analysis_dirs() {
    mkdir -p "$STATIC_ANALYSIS_DIR" "$STATIC_ANALYSIS_HISTORY_DIR"
}

# ==========================================================================
# SPOTBUGS INTEGRATION
# ==========================================================================

# Parse SpotBugs XML report to JSON
parse_spotbugs_report() {
    local report_file="$1"
    local output_file="$2"

    if [[ ! -f "$report_file" ]]; then
        echo '{"findings": [], "summary": {"total": 0, "by_priority": {}, "by_category": {}}}' > "$output_file"
        return
    fi

    python3 << PYEOF "$report_file" "$output_file"
import xml.etree.ElementTree as ET
import json
import sys

report_file = sys.argv[1]
output_file = sys.argv[2]

try:
    tree = ET.parse(report_file)
    root = tree.getroot()

    findings = []
    by_priority = {"1": 0, "2": 0, "3": 0}
    by_category = {}

    ns = {'fb': 'https://github.com/spotbugs/filter/3.0.0'}

    # Try with namespace, fall back without
    bug_instances = root.findall('.//BugInstance')
    if not bug_instances:
        bug_instances = root.findall('.//{https://github.com/spotbugs/filter/3.0.0}BugInstance')
    if not bug_instances:
        # Try direct children
        bug_instances = root.findall('BugInstance')

    for bug in bug_instances:
        priority = bug.get('priority', '3')
        category = bug.get('category', 'unknown')
        bug_type = bug.get('type', 'unknown')
        message = bug.findtext('ShortMessage') or bug.findtext('LongMessage') or bug_type

        # Get source file
        source_line = bug.find('SourceLine')
        source_file = source_line.get('sourcepath', 'unknown') if source_line is not None else 'unknown'
        start_line = source_line.get('start', '0') if source_line is not None else '0'

        findings.append({
            "type": bug_type,
            "priority": priority,
            "category": category,
            "message": message,
            "source_file": source_file,
            "line": int(start_line) if start_line.isdigit() else 0
        })

        by_priority[priority] = by_priority.get(priority, 0) + 1
        by_category[category] = by_category.get(category, 0) + 1

    result = {
        "findings": findings,
        "summary": {
            "total": len(findings),
            "by_priority": by_priority,
            "by_category": by_category
        }
    }

    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)

except Exception as e:
    result = {"findings": [], "summary": {"total": 0, "by_priority": {}, "by_category": {}}, "error": str(e)}
    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)
PYEOF
}

# Helper: Emit warning for missing static analysis reports
emit_missing_reports_warning() {
    local tool_name="$1"
    local report_pattern="$2"
    local required_command="$3"
    log_warn "${tool_name} reports not found. Run: ${required_command}"
    log_warn "Expected: ${report_pattern}"
}

# Discover and parse SpotBugs reports from Maven build
# Phase 1: Emit WARNING instead of REFUSAL when reports are missing
emit_spotbugs_findings() {
    local out="$FACTS_DIR/spotbugs-findings.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/spotbugs-findings.json ..."

    ensure_static_analysis_dirs

    # Find SpotBugs XML reports in target directories - EXACT locations only
    local -a spotbugs_reports=()
    while IFS= read -r report; do
        [[ -n "$report" ]] && spotbugs_reports+=("$report")
    done < <(find "$REPO_ROOT" -path "*/target/spotbugsXml.xml" -type f 2>/dev/null)

    # Phase 1: Emit WARNING instead of REFUSAL when reports are missing
    if [[ ${#spotbugs_reports[@]} -eq 0 ]]; then
        emit_missing_reports_warning "SpotBugs" "*/target/spotbugsXml.xml" "mvn -T 1.5C clean verify -P analysis"
        # Write warning state - data available but reports need generation
        echo '{"findings": [], "summary": {"total": 0}, "status": "REPORTS_NOT_GENERATED", "required_command": "mvn -T 1.5C clean verify -P analysis", "health_impact": "unknown"}' > "$out"
        local op_elapsed=$(( $(epoch_ms) - op_start ))
        record_operation "emit_spotbugs_findings" "$op_elapsed"
        return 0
    fi

    if [[ ${#spotbugs_reports[@]} -eq 0 ]]; then
        # No reports found, check if we should run SpotBugs
        log_warn "No SpotBugs reports found. Run: mvn -P ci spotbugs:spotbugs"
        echo '{"findings": [], "summary": {"total": 0, "by_priority": {}, "by_category": {}}, "status": "no_reports"}' > "$out"
    else
        # Parse each report and aggregate
        local combined_findings="[]"
        local total_count=0
        declare -A combined_priority
        declare -A combined_category

        for report in "${spotbugs_reports[@]}"; do
            local module_name
            module_name=$(echo "$report" | sed 's|.*/\([^/]*\)/target/.*|\1|')
            local temp_out="/tmp/spotbugs-$$-${module_name}.json"
            parse_spotbugs_report "$report" "$temp_out"

            if [[ -f "$temp_out" ]]; then
                # Extract module from path
                python3 << MOD_EOF "$temp_out" "$module_name"
import json
import sys

with open(sys.argv[1]) as f:
    data = json.load(f)

for finding in data.get('findings', []):
    finding['module'] = sys.argv[2]

with open(sys.argv[1], 'w') as f:
    json.dump(data, f, indent=2)
MOD_EOF

                # Merge findings
                local count
                count=$(python3 -c "import json; print(len(json.load(open('$temp_out')).get('findings', [])))" 2>/dev/null || echo "0")
                total_count=$((total_count + count))
            fi
            rm -f "$temp_out"
        done

        # Generate aggregated output
        {
            printf '{\n'
            printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
            printf '  "reports_analyzed": %d,\n' "${#spotbugs_reports[@]}"
            printf '  "findings": [\n'

            local first_finding=true
            for report in "${spotbugs_reports[@]}"; do
                local module_name
                module_name=$(echo "$report" | sed 's|.*/\([^/]*\)/target/.*|\1|')
                local temp_out="/tmp/spotbugs-$$-${module_name}.json"
                parse_spotbugs_report "$report" "$temp_out"

                if [[ -f "$temp_out" ]]; then
                    python3 << AGG_EOF "$temp_out" "$first_finding"
import json
import sys

with open(sys.argv[1]) as f:
    data = json.load(f)

first = sys.argv[2] == 'True'
for finding in data.get('findings', []):
    if not first:
        print(',')
    first = False
    finding['module'] = sys.argv[1].split('-$$-')[-1].replace('.json', '') if '-$$-' in sys.argv[1] else 'unknown'
    print('    ' + json.dumps(finding), end='')
AGG_EOF
                    first_finding=false
                fi
                rm -f "$temp_out"
            done

            printf '\n  ],\n'
            printf '  "summary": {\n'
            printf '    "total": %d,\n' "$total_count"
            printf '    "by_priority": {"1": 0, "2": 0, "3": 0},\n'
            printf '    "by_category": {}\n'
            printf '  }\n'
            printf '}\n'
        } > "$out"
    fi

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_spotbugs_findings" "$op_elapsed"
}

# ==========================================================================
# PMD INTEGRATION
# ==========================================================================

# Parse PMD XML report to JSON
parse_pmd_report() {
    local report_file="$1"
    local output_file="$2"

    if [[ ! -f "$report_file" ]]; then
        echo '{"violations": [], "summary": {"total": 0, "by_priority": {}, "by_rule": {}}}' > "$output_file"
        return
    fi

    python3 << PYEOF "$report_file" "$output_file"
import xml.etree.ElementTree as ET
import json
import sys

report_file = sys.argv[1]
output_file = sys.argv[2]

try:
    tree = ET.parse(report_file)
    root = tree.getroot()

    violations = []
    by_priority = {}
    by_rule = {}

    # PMD structure: <pmd> -> <file> -> <violation>
    for file_elem in root.findall('.//file'):
        file_path = file_elem.get('name', 'unknown')

        for violation in file_elem.findall('violation'):
            rule = violation.get('rule', 'unknown')
            priority = violation.get('priority', '5')
            ruleset = violation.get('ruleset', 'unknown')
            begin_line = violation.get('beginline', '0')
            message = violation.text or ''

            violations.append({
                "rule": rule,
                "priority": int(priority) if priority.isdigit() else 5,
                "ruleset": ruleset,
                "message": message.strip(),
                "source_file": file_path,
                "line": int(begin_line) if begin_line.isdigit() else 0
            })

            by_priority[priority] = by_priority.get(priority, 0) + 1
            by_rule[rule] = by_rule.get(rule, 0) + 1

    result = {
        "violations": violations,
        "summary": {
            "total": len(violations),
            "by_priority": by_priority,
            "by_rule": by_rule
        }
    }

    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)

except Exception as e:
    result = {"violations": [], "summary": {"total": 0, "by_priority": {}, "by_rule": {}}, "error": str(e)}
    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)
PYEOF
}

# Discover and parse PMD reports
# Phase 1: Emit WARNING instead of REFUSAL when reports are missing
emit_pmd_violations() {
    local out="$FACTS_DIR/pmd-violations.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/pmd-violations.json ..."

    ensure_static_analysis_dirs

    # Find PMD XML reports - EXACT locations only
    local -a pmd_reports=()
    while IFS= read -r report; do
        [[ -n "$report" ]] && pmd_reports+=("$report")
    done < <(find "$REPO_ROOT" -path "*/target/pmd.xml" -type f 2>/dev/null)

    # Phase 1: Emit WARNING instead of REFUSAL when reports are missing
    if [[ ${#pmd_reports[@]} -eq 0 ]]; then
        emit_missing_reports_warning "PMD" "*/target/pmd.xml" "mvn -T 1.5C clean verify -P analysis"
        # Write warning state - data available but reports need generation
        echo '{"violations": [], "summary": {"total": 0}, "status": "REPORTS_NOT_GENERATED", "required_command": "mvn -T 1.5C clean verify -P analysis", "health_impact": "unknown"}' > "$out"
        local op_elapsed=$(( $(epoch_ms) - op_start ))
        record_operation "emit_pmd_violations" "$op_elapsed"
        return 0
    fi

    # Aggregate all PMD reports
    local total_count=0
    local all_violations=""

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "reports_analyzed": %d,\n' "${#pmd_reports[@]}"
        printf '  "violations": [\n'

        local first=true
        for report in "${pmd_reports[@]}"; do
            local module_name
            module_name=$(echo "$report" | sed 's|.*/\([^/]*\)/target/.*|\1|')
            local temp_out="/tmp/pmd-$$-${module_name}.json"
            parse_pmd_report "$report" "$temp_out"

            if [[ -f "$temp_out" ]]; then
                # Add module and output
                python3 << PMD_EOF "$temp_out" "$module_name" "$first"
import json
import sys

with open(sys.argv[1]) as f:
    data = json.load(f)

first = sys.argv[2] == 'True'
for v in data.get('violations', []):
    if not first:
        print(',')
    first = False
    v['module'] = sys.argv[3]
    print('    ' + json.dumps(v), end='')
PMD_EOF
                first=false
                local count
                count=$(python3 -c "import json; print(len(json.load(open('$temp_out')).get('violations', [])))" 2>/dev/null || echo "0")
                total_count=$((total_count + count))
            fi
            rm -f "$temp_out"
        done

        printf '\n  ],\n'
        printf '  "summary": {\n'
        printf '    "total": %d\n' "$total_count"
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_pmd_violations" "$op_elapsed"
}

# ==========================================================================
# CHECKSTYLE INTEGRATION
# ==========================================================================

# Parse Checkstyle XML report to JSON
parse_checkstyle_report() {
    local report_file="$1"
    local output_file="$2"

    if [[ ! -f "$report_file" ]]; then
        echo '{"warnings": [], "summary": {"total": 0, "by_severity": {}, "by_check": {}}}' > "$output_file"
        return
    fi

    python3 << PYEOF "$report_file" "$output_file"
import xml.etree.ElementTree as ET
import json
import sys

report_file = sys.argv[1]
output_file = sys.argv[2]

try:
    tree = ET.parse(report_file)
    root = tree.getroot()

    warnings = []
    by_severity = {}
    by_check = {}

    # Checkstyle structure: <checkstyle> -> <file> -> <error>
    for file_elem in root.findall('.//file'):
        file_path = file_elem.get('name', 'unknown')

        for error in file_elem.findall('error'):
            check = error.get('source', 'unknown')
            severity = error.get('severity', 'warning')
            line = error.get('line', '0')
            column = error.get('column', '0')
            message = error.get('message', '')

            warnings.append({
                "check": check,
                "severity": severity,
                "message": message,
                "source_file": file_path,
                "line": int(line) if line.isdigit() else 0,
                "column": int(column) if column.isdigit() else 0
            })

            by_severity[severity] = by_severity.get(severity, 0) + 1
            by_check[check] = by_check.get(check, 0) + 1

    result = {
        "warnings": warnings,
        "summary": {
            "total": len(warnings),
            "by_severity": by_severity,
            "by_check": by_check
        }
    }

    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)

except Exception as e:
    result = {"warnings": [], "summary": {"total": 0, "by_severity": {}, "by_check": {}}, "error": str(e)}
    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)
PYEOF
}

# Discover and parse Checkstyle reports
# Phase 1: Emit WARNING instead of REFUSAL when reports are missing
emit_checkstyle_warnings() {
    local out="$FACTS_DIR/checkstyle-warnings.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/checkstyle-warnings.json ..."

    ensure_static_analysis_dirs

    # Find Checkstyle XML reports - EXACT locations only
    local -a checkstyle_reports=()
    while IFS= read -r report; do
        [[ -n "$report" ]] && checkstyle_reports+=("$report")
    done < <(find "$REPO_ROOT" -path "*/target/checkstyle-result.xml" -type f 2>/dev/null)

    # Phase 1: Emit WARNING instead of REFUSAL when reports are missing
    if [[ ${#checkstyle_reports[@]} -eq 0 ]]; then
        emit_missing_reports_warning "Checkstyle" "*/target/checkstyle-result.xml" "mvn -T 1.5C clean verify -P analysis"
        # Write warning state - data available but reports need generation
        echo '{"warnings": [], "summary": {"total": 0}, "status": "REPORTS_NOT_GENERATED", "required_command": "mvn -T 1.5C clean verify -P analysis", "health_impact": "unknown"}' > "$out"
        local op_elapsed=$(( $(epoch_ms) - op_start ))
        record_operation "emit_checkstyle_warnings" "$op_elapsed"
        return 0
    fi

    local total_count=0

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "reports_analyzed": %d,\n' "${#checkstyle_reports[@]}"
        printf '  "warnings": [\n'

        local first=true
        for report in "${checkstyle_reports[@]}"; do
            local module_name
            module_name=$(echo "$report" | sed 's|.*/\([^/]*\)/target/.*|\1|')
            local temp_out="/tmp/checkstyle-$$-${module_name}.json"
            parse_checkstyle_report "$report" "$temp_out"

            if [[ -f "$temp_out" ]]; then
                python3 << CS_EOF "$temp_out" "$module_name" "$first"
import json
import sys

with open(sys.argv[1]) as f:
    data = json.load(f)

first = sys.argv[2] == 'True'
for w in data.get('warnings', []):
    if not first:
        print(',')
    first = False
    w['module'] = sys.argv[3]
    print('    ' + json.dumps(w), end='')
CS_EOF
                first=false
                local count
                count=$(python3 -c "import json; print(len(json.load(open('$temp_out')).get('warnings', [])))" 2>/dev/null || echo "0")
                total_count=$((total_count + count))
            fi
            rm -f "$temp_out"
        done

        printf '\n  ],\n'
        printf '  "summary": {\n'
        printf '    "total": %d\n' "$total_count"
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_checkstyle_warnings" "$op_elapsed"
}

# ==========================================================================
# AGGREGATED STATIC ANALYSIS SUMMARY
# ==========================================================================

emit_static_analysis_summary() {
    local out="$FACTS_DIR/static-analysis.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/static-analysis.json (aggregated summary) ..."

    ensure_static_analysis_dirs

    # Load counts from individual reports
    local spotbugs_count=0 pmd_count=0 checkstyle_count=0
    local spotbugs_status="no_reports" pmd_status="no_reports" checkstyle_status="no_reports"

    if [[ -f "$FACTS_DIR/spotbugs-findings.json" ]]; then
        spotbugs_count=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/spotbugs-findings.json')).get('summary', {}).get('total', 0))" 2>/dev/null || echo "0")
        spotbugs_status="available"
    fi

    if [[ -f "$FACTS_DIR/pmd-violations.json" ]]; then
        pmd_count=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/pmd-violations.json')).get('summary', {}).get('total', 0))" 2>/dev/null || echo "0")
        pmd_status="available"
    fi

    if [[ -f "$FACTS_DIR/checkstyle-warnings.json" ]]; then
        checkstyle_count=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/checkstyle-warnings.json')).get('summary', {}).get('total', 0))" 2>/dev/null || echo "0")
        checkstyle_status="available"
    fi

    local total_issues=$((spotbugs_count + pmd_count + checkstyle_count))

    # Determine overall health score (0-100)
    # Formula: 100 - (total_issues * weight), minimum 0
    # Weight: SpotBugs=3, PMD=2, Checkstyle=1
    local weighted_issues=$((spotbugs_count * 3 + pmd_count * 2 + checkstyle_count * 1))
    local health_score=$((100 - weighted_issues))
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
        printf '  "total_issues": %d,\n' "$total_issues"
        printf '  "tools": {\n'
        printf '    "spotbugs": {\n'
        printf '      "status": "%s",\n' "$spotbugs_status"
        printf '      "total_findings": %d\n' "$spotbugs_count"
        printf '    },\n'
        printf '    "pmd": {\n'
        printf '      "status": "%s",\n' "$pmd_status"
        printf '      "total_violations": %d\n' "$pmd_count"
        printf '    },\n'
        printf '    "checkstyle": {\n'
        printf '      "status": "%s",\n' "$checkstyle_status"
        printf '      "total_warnings": %d\n' "$checkstyle_count"
        printf '    }\n'
        printf '  },\n'
        printf '  "recommendations": [\n'

        # Generate recommendations based on findings
        local first_rec=true
        if [[ $spotbugs_count -gt 0 ]]; then
            printf '    {"tool": "spotbugs", "priority": "high", "action": "Review %d potential bugs, especially P1/P2 priority items"}' "$spotbugs_count"
            first_rec=false
        fi
        if [[ $pmd_count -gt 0 ]]; then
            $first_rec || printf ',\n'
            printf '    {"tool": "pmd", "priority": "medium", "action": "Address %d code quality violations"}' "$pmd_count"
            first_rec=false
        fi
        if [[ $checkstyle_count -gt 0 ]]; then
            $first_rec || printf ',\n'
            printf '    {"tool": "checkstyle", "priority": "low", "action": "Fix %d style warnings for consistency"}' "$checkstyle_count"
            first_rec=false
        fi

        printf '\n  ]\n'
        printf '}\n'
    } > "$out"

    # Store in history for trend tracking
    local history_file="$STATIC_ANALYSIS_HISTORY_DIR/history.jsonl"
    {
        printf '{"timestamp": "%s", "commit": "%s", "health_score": %d, "spotbugs": %d, "pmd": %d, "checkstyle": %d}\n' \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(git_commit)" "$health_score" "$spotbugs_count" "$pmd_count" "$checkstyle_count"
    } >> "$history_file"

    # Keep last 100 history entries
    if [[ -f "$history_file" ]]; then
        local lines
        lines=$(wc -l < "$history_file")
        if [[ "$lines" -gt 100 ]]; then
            tail -n 100 "$history_file" > "${history_file}.tmp"
            mv "${history_file}.tmp" "$history_file"
        fi
    fi

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_static_analysis_summary" "$op_elapsed"

    log_ok "Static analysis summary: health_score=${health_score} total_issues=${total_issues}"
}

# ==========================================================================
# CODE HEALTH DASHBOARD DIAGRAM
# ==========================================================================

emit_code_health_dashboard_diagram() {
    local out="$DIAGRAMS_DIR/60-code-health-dashboard.mmd"
    log_info "Emitting diagrams/60-code-health-dashboard.mmd ..."

    # Load data from facts
    local health_score=100 health_status="GREEN"
    local spotbugs_count=0 pmd_count=0 checkstyle_count=0

    if [[ -f "$FACTS_DIR/static-analysis.json" ]]; then
        health_score=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/static-analysis.json')).get('health_score', 100))" 2>/dev/null || echo "100")
        health_status=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/static-analysis.json')).get('health_status', 'GREEN'))" 2>/dev/null || echo "GREEN")
        spotbugs_count=$(python3 -c "import json; d=json.load(open('$FACTS_DIR/static-analysis.json')); print(d.get('tools', {}).get('spotbugs', {}).get('total_findings', 0))" 2>/dev/null || echo "0")
        pmd_count=$(python3 -c "import json; d=json.load(open('$FACTS_DIR/static-analysis.json')); print(d.get('tools', {}).get('pmd', {}).get('total_violations', 0))" 2>/dev/null || echo "0")
        checkstyle_count=$(python3 -c "import json; d=json.load(open('$FACTS_DIR/static-analysis.json')); print(d.get('tools', {}).get('checkstyle', {}).get('total_warnings', 0))" 2>/dev/null || echo "0")
    fi

    # Determine colors based on status
    local score_class="green_score"
    local status_class="green_status"
    [[ "$health_status" == "YELLOW" ]] && score_class="yellow_score" && status_class="yellow_status"
    [[ "$health_status" == "RED" ]] && score_class="red_score" && status_class="red_status"

    local spotbugs_class="tool_ok" pmd_class="tool_ok" checkstyle_class="tool_ok"
    [[ $spotbugs_count -gt 0 ]] && spotbugs_class="tool_warning"
    [[ $spotbugs_count -gt 10 ]] && spotbugs_class="tool_error"
    [[ $pmd_count -gt 0 ]] && pmd_class="tool_warning"
    [[ $pmd_count -gt 20 ]] && pmd_class="tool_error"
    [[ $checkstyle_count -gt 0 ]] && checkstyle_class="tool_warning"
    [[ $checkstyle_count -gt 50 ]] && checkstyle_class="tool_error"

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#2d3748' } } }%%"
        echo "graph TD"
        echo "    classDef green_score fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef yellow_score fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef red_score fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef green_status fill:#c6f6d5,stroke:#38a169,color:#2d3748"
        echo "    classDef yellow_status fill:#fefcbf,stroke:#d69e2e,color:#2d3748"
        echo "    classDef red_status fill:#fed7d7,stroke:#e53e3e,color:#2d3748"
        echo "    classDef tool_ok fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef tool_warning fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef tool_error fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef header fill:#4a5568,stroke:#2d3748,color:#fff"
        echo ""
        echo "    HEADER[\"YAWL V6 Code Health Dashboard<br/>$(date -u +%Y-%m-%d)\"]:::header"
        echo ""
        echo "    subgraph ScorePanel[\"Health Score\"]"
        echo "        HEALTH_SCORE[\"${health_score}/100\"]:::${score_class}"
        echo "        HEALTH_STATUS[\"${health_status}\"]:::${status_class}"
        echo "    end"
        echo ""
        echo "    subgraph AnalysisTools[\"Static Analysis Tools\"]"
        echo "        SPOTBUGS[\"SpotBugs<br/>${spotbugs_count} findings\"]:::${spotbugs_class}"
        echo "        PMD[\"PMD<br/>${pmd_count} violations\"]:::${pmd_class}"
        echo "        CHECKSTYLE[\"Checkstyle<br/>${checkstyle_count} warnings\"]:::${checkstyle_class}"
        echo "    end"
        echo ""
        echo "    subgraph HealthMetrics[\"Health Indicators\"]"
        echo "        M_BUGS[\"Bug Detection\"]"
        echo "        M_QUALITY[\"Code Quality\"]"
        echo "        M_STYLE[\"Style Compliance\"]"
        echo "    end"
        echo ""
        echo "    HEADER --> ScorePanel"
        echo "    HEADER --> AnalysisTools"
        echo ""
        echo "    SPOTBUGS --> M_BUGS"
        echo "    PMD --> M_QUALITY"
        echo "    CHECKSTYLE --> M_STYLE"
        echo ""
        echo "    M_BUGS -.->|contributes| HEALTH_SCORE"
        echo "    M_QUALITY -.->|contributes| HEALTH_SCORE"
        echo "    M_STYLE -.->|contributes| HEALTH_SCORE"
        echo ""
        echo "    %% Legend"
        echo "    subgraph Legend[\"Legend\"]"
        echo "        L_OK[\"0 issues\"]:::tool_ok"
        echo "        L_WARN[\"1-10 issues\"]:::tool_warning"
        echo "        L_ERR[\">10 issues\"]:::tool_error"
        echo "    end"
    } > "$out"
}

# ==========================================================================
# TREND VISUALIZATION DIAGRAM
# ==========================================================================

emit_static_analysis_trends_diagram() {
    local out="$DIAGRAMS_DIR/61-static-analysis-trends.mmd"
    log_info "Emitting diagrams/61-static-analysis-trends.mmd ..."

    local history_file="$STATIC_ANALYSIS_HISTORY_DIR/history.jsonl"

    if [[ ! -f "$history_file" ]]; then
        {
            echo "%%{ init: { 'theme': 'base' } }%%"
            echo "graph LR"
            echo "    NO_DATA[\"No trend data available yet.<br/>Run observatory multiple times to build history.\"]"
        } > "$out"
        return
    fi

    # Extract last 10 data points for trend visualization
    local trend_data
    trend_data=$(python3 << TREND_EOF "$history_file"
import json
import sys

with open(sys.argv[1]) as f:
    lines = [json.loads(line) for line in f if line.strip()]

# Get last 10 entries
recent = lines[-10:] if len(lines) >= 10 else lines

for entry in recent:
    print(f"{entry.get('commit', 'unknown')[:7]}|{entry.get('health_score', 0)}|{entry.get('spotbugs', 0)}|{entry.get('pmd', 0)}|{entry.get('checkstyle', 0)}")
TREND_EOF
)

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph LR"
        echo "    classDef good fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef warning fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef bad fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef header fill:#4a5568,stroke:#2d3748,color:#fff"
        echo ""
        echo "    HEADER[\"Static Analysis Trends<br/>Last 10 Commits\"]:::header"
        echo ""
        echo "    subgraph Timeline[\"Code Health Over Time\"]"

        local idx=0
        local prev_score=""
        while IFS='|' read -r commit score spotbugs pmd checkstyle; do
            [[ -z "$commit" ]] && continue
            local node_class="good"
            [[ "$score" -lt 80 ]] && node_class="warning"
            [[ "$score" -lt 60 ]] && node_class="bad"

            echo "        T${idx}[\"${commit}<br/>Score: ${score}<br/>SB:${spotbugs} PMD:${pmd} CS:${checkstyle}\"]:::${node_class}"

            # Draw arrow to previous
            if [[ -n "$prev_score" ]]; then
                local prev_idx=$((idx - 1))
                local trend_arrow="--> "
                [[ "$score" -lt "$prev_score" ]] && trend_arrow="-.->|worse| "
                [[ "$score" -gt "$prev_score" ]] && trend_arrow="-.->|better| "
                echo "        T${prev_idx} ${trend_arrow}T${idx}"
            fi

            prev_score="$score"
            idx=$((idx + 1))
        done <<< "$trend_data"

        echo "    end"
        echo ""
        echo "    HEADER --> Timeline"
        echo ""
        echo "    %% Summary statistics"
        local avg_score
        avg_score=$(echo "$trend_data" | awk -F'|' '{sum+=$2; count++} END {print int(sum/count)}')
        echo "    subgraph Stats[\"Trend Statistics\"]"
        echo "        AVG[\"Average Score: ${avg_score}\"]"
        echo "    end"
    } > "$out"
}

# ==========================================================================
# MAIN DISPATCHER
# ==========================================================================

emit_static_analysis_facts() {
    local op_start
    op_start=$(epoch_ms)

    emit_spotbugs_findings
    emit_pmd_violations
    emit_checkstyle_warnings
    emit_static_analysis_summary

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_static_analysis_facts" "$op_elapsed"
}

emit_static_analysis_diagrams() {
    local op_start
    op_start=$(epoch_ms)

    emit_code_health_dashboard_diagram
    emit_static_analysis_trends_diagram

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_static_analysis_diagrams" "$op_elapsed"
}
