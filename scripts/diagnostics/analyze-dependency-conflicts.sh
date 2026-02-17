#!/usr/bin/env bash
# ============================================================================
# analyze-dependency-conflicts.sh
# YAWL Dependency Conflict Analyzer
#
# Runs `mvn dependency:tree -Dverbose` across all modules, parses the output,
# and produces a structured report of every version conflict Maven had to
# mediate.  Groups conflicts by artifact, ranks by frequency, and identifies
# the modules responsible for pulling in each conflicting version.
#
# Output:  reports/dependency-conflicts-YYYY-MM-DD.md
#          reports/dependency-conflicts-YYYY-MM-DD.json
#          reports/dependency-conflicts-summary.txt
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="$PROJECT_ROOT/reports"
DATE_STAMP=$(date +%Y-%m-%d)
TREE_RAW="$REPORT_DIR/raw-dependency-tree-${DATE_STAMP}.txt"
CONFLICTS_CSV="$REPORT_DIR/conflicts-${DATE_STAMP}.csv"
REPORT_MD="$REPORT_DIR/dependency-conflicts-${DATE_STAMP}.md"
REPORT_JSON="$REPORT_DIR/dependency-conflicts-${DATE_STAMP}.json"
SUMMARY="$REPORT_DIR/dependency-conflicts-summary.txt"

mkdir -p "$REPORT_DIR"

# ── colour helpers (disabled in CI / piped output) ──────────────────────────
if [[ -t 1 ]]; then
    RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[0;33m'
    CYN='\033[0;36m'; BLD='\033[1m'; RST='\033[0m'
else
    RED=''; GRN=''; YLW=''; CYN=''; BLD=''; RST=''
fi

info()  { echo -e "${CYN}[INFO]${RST}  $*"; }
warn()  { echo -e "${YLW}[WARN]${RST}  $*"; }
err()   { echo -e "${RED}[ERR]${RST}   $*" >&2; }
ok()    { echo -e "${GRN}[OK]${RST}    $*"; }

# ── Step 1: Generate verbose dependency tree ────────────────────────────────
info "Generating verbose dependency tree (this takes 60-90 seconds)..."

cd "$PROJECT_ROOT"
if ! mvn -B -ntp dependency:tree \
        -Dverbose \
        -DoutputType=text \
        -DoutputFile="$TREE_RAW" \
        -DappendOutput=true \
        > "$REPORT_DIR/mvn-dep-tree.log" 2>&1; then
    warn "mvn dependency:tree returned non-zero; partial results may be available."
    warn "Check $REPORT_DIR/mvn-dep-tree.log for details."
fi

if [[ ! -s "$TREE_RAW" ]]; then
    # Fallback: aggregate per-module output files
    info "Aggregating per-module dependency tree files..."
    : > "$TREE_RAW"
    for mod_dir in "$PROJECT_ROOT"/yawl-*/; do
        mod_name=$(basename "$mod_dir")
        tree_file="$mod_dir/target/dependency-tree.txt"
        if [[ -f "$tree_file" ]]; then
            echo "=== MODULE: $mod_name ===" >> "$TREE_RAW"
            cat "$tree_file" >> "$TREE_RAW"
            echo "" >> "$TREE_RAW"
        fi
    done
fi

if [[ ! -s "$TREE_RAW" ]]; then
    # Second fallback: capture stdout directly
    info "Using stdout capture fallback..."
    mvn -B -ntp dependency:tree -Dverbose 2>/dev/null > "$TREE_RAW" || true
fi

TOTAL_LINES=$(wc -l < "$TREE_RAW")
info "Dependency tree: $TOTAL_LINES lines"

# ── Step 2: Extract conflict lines ─────────────────────────────────────────
info "Extracting conflicts (omitted for conflict, version managed)..."

# Lines matching "omitted for conflict" or "version managed from"
CONFLICT_PATTERN="omitted for conflict|version managed from|managed from"

CONFLICT_COUNT=$(grep -ciE "$CONFLICT_PATTERN" "$TREE_RAW" 2>/dev/null || echo 0)
info "Found $CONFLICT_COUNT conflict mediation lines"

# ── Step 3: Parse and structure conflicts ───────────────────────────────────
info "Parsing conflict details..."

# Build CSV: artifact,requested_version,resolved_version,module,conflict_type
echo "artifact,requested_version,resolved_version,module,conflict_type" > "$CONFLICTS_CSV"

current_module="unknown"
while IFS= read -r line; do
    # Track module boundaries
    if [[ "$line" =~ ===\ MODULE:\ ([a-zA-Z0-9_-]+) ]]; then
        current_module="${BASH_REMATCH[1]}"
        continue
    fi
    # Detect module from artifact root line (e.g., "org.yawlfoundation:yawl-engine:jar:6.0.0-Alpha")
    if [[ "$line" =~ org\.yawlfoundation:([a-zA-Z0-9_-]+):.*:6\.0\.0 ]]; then
        current_module="${BASH_REMATCH[1]}"
        continue
    fi

    # Pattern 1: "omitted for conflict with X.Y.Z"
    # e.g., "(commons-io:commons-io:jar:2.11.0 - omitted for conflict with 2.21.0)"
    if [[ "$line" =~ \(([a-zA-Z0-9._-]+):([a-zA-Z0-9._-]+):[a-z]+:([0-9a-zA-Z._-]+)\ -\ omitted\ for\ conflict\ with\ ([0-9a-zA-Z._-]+)\) ]]; then
        group="${BASH_REMATCH[1]}"
        artifact="${BASH_REMATCH[2]}"
        requested="${BASH_REMATCH[3]}"
        resolved="${BASH_REMATCH[4]}"
        echo "${group}:${artifact},${requested},${resolved},${current_module},conflict" >> "$CONFLICTS_CSV"
        continue
    fi

    # Pattern 2: "version managed from X.Y.Z"
    # e.g., "commons-codec:commons-codec:jar:1.15 (version managed from 1.11)"
    if [[ "$line" =~ ([a-zA-Z0-9._-]+):([a-zA-Z0-9._-]+):[a-z]+:([0-9a-zA-Z._-]+).*\(version\ managed\ from\ ([0-9a-zA-Z._-]+)\) ]]; then
        group="${BASH_REMATCH[1]}"
        artifact="${BASH_REMATCH[2]}"
        resolved="${BASH_REMATCH[3]}"
        requested="${BASH_REMATCH[4]}"
        echo "${group}:${artifact},${requested},${resolved},${current_module},managed" >> "$CONFLICTS_CSV"
        continue
    fi

    # Pattern 3: "managed from X.Y.Z" (alternate form)
    if [[ "$line" =~ ([a-zA-Z0-9._-]+):([a-zA-Z0-9._-]+):[a-z]+:([0-9a-zA-Z._-]+).*managed\ from\ ([0-9a-zA-Z._-]+) ]]; then
        group="${BASH_REMATCH[1]}"
        artifact="${BASH_REMATCH[2]}"
        resolved="${BASH_REMATCH[3]}"
        requested="${BASH_REMATCH[4]}"
        echo "${group}:${artifact},${requested},${resolved},${current_module},managed" >> "$CONFLICTS_CSV"
        continue
    fi
done < "$TREE_RAW"

CSV_LINES=$(($(wc -l < "$CONFLICTS_CSV") - 1))
info "Parsed $CSV_LINES structured conflict entries"

# ── Step 4: Generate ranked summary ────────────────────────────────────────
info "Generating conflict ranking..."

{
    echo "============================================================================"
    echo "YAWL DEPENDENCY CONFLICT ANALYSIS - $DATE_STAMP"
    echo "============================================================================"
    echo ""
    echo "Total conflict mediation lines in tree: $CONFLICT_COUNT"
    echo "Parsed structured entries:              $CSV_LINES"
    echo ""
    echo "── TOP 30 MOST-CONFLICTED ARTIFACTS ──────────────────────────────────────"
    echo ""
    echo "  Count  Artifact"
    echo "  -----  --------"

    if [[ $CSV_LINES -gt 0 ]]; then
        tail -n +2 "$CONFLICTS_CSV" \
            | cut -d',' -f1 \
            | sort \
            | uniq -c \
            | sort -rn \
            | head -30 \
            | while read -r count artifact; do
                printf "  %5d  %s\n" "$count" "$artifact"
            done
    fi

    echo ""
    echo "── CONFLICT TYPES BREAKDOWN ──────────────────────────────────────────────"
    echo ""
    if [[ $CSV_LINES -gt 0 ]]; then
        tail -n +2 "$CONFLICTS_CSV" \
            | cut -d',' -f5 \
            | sort \
            | uniq -c \
            | sort -rn \
            | while read -r count ctype; do
                printf "  %5d  %s\n" "$count" "$ctype"
            done
    fi

    echo ""
    echo "── CONFLICTS PER MODULE ──────────────────────────────────────────────────"
    echo ""
    if [[ $CSV_LINES -gt 0 ]]; then
        tail -n +2 "$CONFLICTS_CSV" \
            | cut -d',' -f4 \
            | sort \
            | uniq -c \
            | sort -rn \
            | while read -r count mod; do
                printf "  %5d  %s\n" "$count" "$mod"
            done
    fi

    echo ""
    echo "── VERSION SPREAD (artifacts with 3+ distinct requested versions) ────────"
    echo ""
    if [[ $CSV_LINES -gt 0 ]]; then
        tail -n +2 "$CONFLICTS_CSV" \
            | awk -F',' '{print $1","$2}' \
            | sort -u \
            | cut -d',' -f1 \
            | sort \
            | uniq -c \
            | sort -rn \
            | awk '$1 >= 3 {printf "  %5d versions  %s\n", $1, $2}'
    fi

} > "$SUMMARY"

cat "$SUMMARY"

# ── Step 5: Generate Markdown report ───────────────────────────────────────
info "Generating Markdown report..."

{
    echo "# YAWL Dependency Conflict Analysis"
    echo ""
    echo "**Date:** $DATE_STAMP"
    echo "**Total conflicts requiring mediation:** $CONFLICT_COUNT"
    echo "**Parsed structured entries:** $CSV_LINES"
    echo ""
    echo "## Summary"
    echo ""
    echo "Maven dependency mediation resolved **$CONFLICT_COUNT** version conflicts across"
    echo "13 modules. This report identifies which artifacts cause the most conflicts,"
    echo "which modules are most affected, and where version spread is highest."
    echo ""
    echo "## Top Conflicted Artifacts"
    echo ""
    echo "| Rank | Artifact | Conflicts | Versions Seen |"
    echo "|------|----------|-----------|---------------|"

    if [[ $CSV_LINES -gt 0 ]]; then
        rank=0
        tail -n +2 "$CONFLICTS_CSV" \
            | cut -d',' -f1 \
            | sort \
            | uniq -c \
            | sort -rn \
            | head -30 \
            | while read -r count artifact; do
                rank=$((rank + 1))
                versions=$(tail -n +2 "$CONFLICTS_CSV" \
                    | grep "^${artifact}," \
                    | cut -d',' -f2,3 \
                    | tr ',' '\n' \
                    | sort -uV \
                    | tr '\n' ' ')
                echo "| $rank | \`$artifact\` | $count | $versions |"
            done
    fi

    echo ""
    echo "## Conflicts by Module"
    echo ""
    echo "| Module | Conflict Count | Primary Culprits |"
    echo "|--------|---------------|-------------------|"

    if [[ $CSV_LINES -gt 0 ]]; then
        tail -n +2 "$CONFLICTS_CSV" \
            | cut -d',' -f4 \
            | sort \
            | uniq -c \
            | sort -rn \
            | while read -r count mod; do
                top_culprits=$(tail -n +2 "$CONFLICTS_CSV" \
                    | grep ",${mod}," \
                    | cut -d',' -f1 \
                    | sort \
                    | uniq -c \
                    | sort -rn \
                    | head -3 \
                    | awk '{print $2}' \
                    | tr '\n' ', ' \
                    | sed 's/,$//')
                echo "| \`$mod\` | $count | $top_culprits |"
            done
    fi

    echo ""
    echo "## Conflict Type Distribution"
    echo ""
    echo "| Type | Count | Description |"
    echo "|------|-------|-------------|"
    echo "| \`conflict\` | $(tail -n +2 "$CONFLICTS_CSV" 2>/dev/null | grep -c ',conflict$' || echo 0) | Transitive dependency pulled different version; Maven chose nearest/first |"
    echo "| \`managed\` | $(tail -n +2 "$CONFLICTS_CSV" 2>/dev/null | grep -c ',managed$' || echo 0) | \`dependencyManagement\` in parent POM forced a specific version |"
    echo ""
    echo "## Remediation Priority"
    echo ""
    echo "Artifacts should be addressed in order of conflict count. For each:"
    echo ""
    echo "1. **Already managed** (type=managed): Parent POM is correctly pinning. No action needed."
    echo "2. **Not managed** (type=conflict): Add to parent \`<dependencyManagement>\` to pin version."
    echo "3. **High version spread**: Multiple incompatible versions pulled transitively."
    echo "   Consider adding \`<exclusions>\` on the dependency that pulls the wrong version."
    echo ""
    echo "## Files Generated"
    echo ""
    echo "| File | Description |"
    echo "|------|-------------|"
    echo "| \`reports/raw-dependency-tree-${DATE_STAMP}.txt\` | Full verbose dependency tree |"
    echo "| \`reports/conflicts-${DATE_STAMP}.csv\` | Structured conflict data (CSV) |"
    echo "| \`reports/dependency-conflicts-${DATE_STAMP}.json\` | Machine-readable conflict data |"
    echo "| \`reports/dependency-conflicts-summary.txt\` | Console-friendly summary |"

} > "$REPORT_MD"

# ── Step 6: Generate JSON report ───────────────────────────────────────────
info "Generating JSON report..."

{
    echo "{"
    echo "  \"date\": \"$DATE_STAMP\","
    echo "  \"total_conflict_lines\": $CONFLICT_COUNT,"
    echo "  \"parsed_entries\": $CSV_LINES,"
    echo "  \"artifacts\": ["

    if [[ $CSV_LINES -gt 0 ]]; then
        first=true
        tail -n +2 "$CONFLICTS_CSV" \
            | cut -d',' -f1 \
            | sort -u \
            | while read -r artifact; do
                count=$(tail -n +2 "$CONFLICTS_CSV" | grep -c "^${artifact}," || echo 0)
                versions=$(tail -n +2 "$CONFLICTS_CSV" \
                    | grep "^${artifact}," \
                    | cut -d',' -f2,3 \
                    | tr ',' '\n' \
                    | sort -uV \
                    | tr '\n' ',' \
                    | sed 's/,$//')
                modules=$(tail -n +2 "$CONFLICTS_CSV" \
                    | grep "^${artifact}," \
                    | cut -d',' -f4 \
                    | sort -u \
                    | tr '\n' ',' \
                    | sed 's/,$//')

                if [[ "$first" == "true" ]]; then
                    first=false
                else
                    echo ","
                fi
                printf '    {"artifact": "%s", "conflict_count": %d, "versions": [%s], "modules": [%s]}' \
                    "$artifact" "$count" \
                    "$(echo "$versions" | sed 's/\([^,]*\)/"\1"/g')" \
                    "$(echo "$modules" | sed 's/\([^,]*\)/"\1"/g')"
            done
    fi

    echo ""
    echo "  ]"
    echo "}"
} > "$REPORT_JSON"

# ── Done ────────────────────────────────────────────────────────────────────
echo ""
ok "Reports generated in $REPORT_DIR/"
ok "  - $REPORT_MD"
ok "  - $REPORT_JSON"
ok "  - $SUMMARY"
ok "  - $CONFLICTS_CSV"
echo ""
info "Total conflicts requiring mediation: ${BLD}$CONFLICT_COUNT${RST}"
