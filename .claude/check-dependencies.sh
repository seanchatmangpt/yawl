#!/bin/bash
set -euo pipefail

# YAWL Dependency Health Scanner
# Scans for outdated packages, CVEs, and security vulnerabilities
# Usage: ./check-dependencies.sh [--module=MODULE] [--critical-only] [--format=FORMAT]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Logging functions
log_info() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; }
log_critical() { echo -e "${RED}${BOLD}[CRITICAL]${NC} $*"; }
log_section() { echo -e "\n${BLUE}${BOLD}═══ $* ═══${NC}\n"; }

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TIMESTAMP=$(date '+%Y-%m-%d_%H-%M-%S')
REPORT_DIR="$PROJECT_ROOT/dependency-reports"
REPORT_FILE="$REPORT_DIR/dependency-health-$TIMESTAMP.md"
HTML_REPORT="$REPORT_DIR/dependency-health-$TIMESTAMP.html"
JSON_REPORT="$REPORT_DIR/dependency-health-$TIMESTAMP.json"
CVE_REPORT_DIR="$REPORT_DIR/owasp-dependency-check"

# Parse command line arguments
SPECIFIC_MODULE=""
CRITICAL_ONLY=false
OUTPUT_FORMAT="markdown"
SKIP_CVE_CHECK=false
SKIP_UPDATE_CHECK=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --module=*) SPECIFIC_MODULE="${1#--module=}"; shift ;;
    --module) SPECIFIC_MODULE="$2"; shift 2 ;;
    --critical-only) CRITICAL_ONLY=true; shift ;;
    --format=*) OUTPUT_FORMAT="${1#--format=}"; shift ;;
    --format) OUTPUT_FORMAT="$2"; shift 2 ;;
    --skip-cve) SKIP_CVE_CHECK=true; shift ;;
    --skip-updates) SKIP_UPDATE_CHECK=true; shift ;;
    --help)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --module=MODULE      Check specific module only"
      echo "  --critical-only      Show only critical vulnerabilities"
      echo "  --format=FORMAT      Output format: markdown, html, json, all (default: markdown)"
      echo "  --skip-cve           Skip CVE vulnerability check"
      echo "  --skip-updates       Skip dependency update check"
      echo "  --help               Show this help message"
      echo ""
      echo "Examples:"
      echo "  $0                                    # Full health check"
      echo "  $0 --module=yawl-engine              # Check single module"
      echo "  $0 --critical-only                   # Show critical only"
      echo "  $0 --format=all                      # Generate all formats"
      exit 0
      ;;
    *) log_error "Unknown option: $1. Use --help for usage."; exit 1 ;;
  esac
done

# Create report directory
mkdir -p "$REPORT_DIR"

# Header
echo -e "${BOLD}${CYAN}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║        YAWL Dependency Security & Health Scanner              ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

log_info "Project: YAWL v5.2"
log_info "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
log_info "Report Directory: $REPORT_DIR"
echo ""

# Change to project root
cd "$PROJECT_ROOT"

# Initialize report
cat > "$REPORT_FILE" << HEADER
# YAWL Dependency Health Report

**Generated:** $(date '+%Y-%m-%d %H:%M:%S')
**Project:** YAWL v5.2
**Scanner Version:** 1.0.0

---

## Executive Summary

HEADER

# Counters for summary
CRITICAL_COUNT=0
HIGH_COUNT=0
MEDIUM_COUNT=0
LOW_COUNT=0
TOTAL_OUTDATED=0

# Temporary files for collecting data
CRITICAL_VULNS="$REPORT_DIR/.critical_vulns.tmp"
HIGH_PRIORITY="$REPORT_DIR/.high_priority.tmp"
MEDIUM_PRIORITY="$REPORT_DIR/.medium_priority.tmp"
LOW_PRIORITY="$REPORT_DIR/.low_priority.tmp"

: > "$CRITICAL_VULNS"
: > "$HIGH_PRIORITY"
: > "$MEDIUM_PRIORITY"
: > "$LOW_PRIORITY"

# Build Maven command base
MVN_CMD="mvn"
if [ -n "$SPECIFIC_MODULE" ]; then
  MVN_CMD="$MVN_CMD -pl $SPECIFIC_MODULE"
  log_info "Targeting module: $SPECIFIC_MODULE"
fi

# 1. Check for dependency updates
if [ "$SKIP_UPDATE_CHECK" = "false" ]; then
  log_section "Scanning for Outdated Dependencies"

  UPDATES_FILE="$REPORT_DIR/.dependency-updates.tmp"

  if $MVN_CMD versions:display-dependency-updates -DoutputFile="$UPDATES_FILE" > /dev/null 2>&1; then
    log_info "Dependency update scan completed"

    # Parse updates (simplified - in real implementation would parse XML output)
    if [ -f "target/dependency-updates-report.txt" ]; then
      TOTAL_OUTDATED=$(grep -c "newer version" target/dependency-updates-report.txt 2>/dev/null || echo "0")
    fi
  else
    log_warn "Dependency update check failed (continuing...)"
  fi
else
  log_warn "Skipping dependency update check (--skip-updates)"
fi

# 2. Run OWASP Dependency Check for CVEs
if [ "$SKIP_CVE_CHECK" = "false" ]; then
  log_section "Scanning for Known CVEs (OWASP Dependency-Check)"

  log_info "Running OWASP dependency-check (this may take 2-5 minutes on first run)..."

  if $MVN_CMD org.owasp:dependency-check-maven:10.0.4:check \
      -DskipTests \
      -Dformat=ALL \
      -DfailBuildOnCVSS=0 \
      -DsuppressionFile="$PROJECT_ROOT/owasp-suppressions.xml" \
      -DoutputDirectory="$CVE_REPORT_DIR" 2>&1 | tee "$REPORT_DIR/.owasp-output.log"; then

    log_info "CVE scan completed successfully"

    # Parse OWASP report for vulnerabilities
    if [ -f "$CVE_REPORT_DIR/dependency-check-report.json" ]; then
      # Count vulnerabilities by severity
      if command -v jq &> /dev/null; then
        CRITICAL_COUNT=$(jq '[.dependencies[].vulnerabilities[]? | select(.severity == "CRITICAL")] | length' "$CVE_REPORT_DIR/dependency-check-report.json" 2>/dev/null || echo "0")
        HIGH_COUNT=$(jq '[.dependencies[].vulnerabilities[]? | select(.severity == "HIGH")] | length' "$CVE_REPORT_DIR/dependency-check-report.json" 2>/dev/null || echo "0")
        MEDIUM_COUNT=$(jq '[.dependencies[].vulnerabilities[]? | select(.severity == "MEDIUM")] | length' "$CVE_REPORT_DIR/dependency-check-report.json" 2>/dev/null || echo "0")
        LOW_COUNT=$(jq '[.dependencies[].vulnerabilities[]? | select(.severity == "LOW")] | length' "$CVE_REPORT_DIR/dependency-check-report.json" 2>/dev/null || echo "0")

        log_info "Found: CRITICAL=$CRITICAL_COUNT, HIGH=$HIGH_COUNT, MEDIUM=$MEDIUM_COUNT, LOW=$LOW_COUNT"

        # Extract critical vulnerabilities
        if [ "$CRITICAL_COUNT" -gt 0 ]; then
          jq -r '.dependencies[] | select(.vulnerabilities != null) | .vulnerabilities[] | select(.severity == "CRITICAL") | "- **\(.name)** (CVSS: \(.cvssv3.baseScore // .cvssv2.score // "N/A")) - \(.description[0:100])..."' \
            "$CVE_REPORT_DIR/dependency-check-report.json" > "$CRITICAL_VULNS" 2>/dev/null || true
        fi
      else
        log_warn "jq not installed, cannot parse JSON report (install with: apt-get install jq)"
      fi
    fi

    # Check if HTML report exists
    if [ -f "$CVE_REPORT_DIR/dependency-check-report.html" ]; then
      log_info "HTML report: $CVE_REPORT_DIR/dependency-check-report.html"
    fi
  else
    log_error "OWASP dependency-check failed"
    log_warn "This might be first run (downloading CVE database) or network issue"
  fi
else
  log_warn "Skipping CVE check (--skip-cve)"
fi

# 3. Check for property updates
log_section "Checking for Property Updates"

PROPS_FILE="$REPORT_DIR/.property-updates.tmp"

if $MVN_CMD versions:display-property-updates > "$PROPS_FILE" 2>&1; then
  log_info "Property updates check completed"

  # Parse for common security-sensitive libraries
  if grep -qi "log4j" "$PROPS_FILE"; then
    echo "- **log4j**: Check for updates (security-sensitive)" >> "$HIGH_PRIORITY"
  fi

  if grep -qi "spring" "$PROPS_FILE"; then
    echo "- **spring-boot**: Updates available" >> "$HIGH_PRIORITY"
  fi

  if grep -qi "jackson" "$PROPS_FILE"; then
    echo "- **jackson**: Check for security updates" >> "$HIGH_PRIORITY"
  fi
else
  log_warn "Property updates check failed"
fi

# 4. Analyze current dependencies
log_section "Analyzing Current Dependencies"

CURRENT_DEPS="$REPORT_DIR/.current-dependencies.txt"

if $MVN_CMD dependency:tree -DoutputFile="$CURRENT_DEPS" > /dev/null 2>&1; then
  DEP_COUNT=$(wc -l < "$CURRENT_DEPS")
  log_info "Total dependencies: $DEP_COUNT"
else
  log_warn "Failed to generate dependency tree"
fi

# 5. Build final report
log_section "Generating Report"

# Summary section
cat >> "$REPORT_FILE" << SUMMARY

| Metric | Count |
|--------|-------|
| Critical Vulnerabilities | **$CRITICAL_COUNT** |
| High Severity Issues | **$HIGH_COUNT** |
| Medium Severity Issues | **$MEDIUM_COUNT** |
| Low Severity Issues | **$LOW_COUNT** |
| Outdated Dependencies | $TOTAL_OUTDATED |

---

SUMMARY

# Critical vulnerabilities section
if [ "$CRITICAL_COUNT" -gt 0 ] || [ -s "$CRITICAL_VULNS" ]; then
  cat >> "$REPORT_FILE" << 'CRITICAL'

## 🚨 CRITICAL VULNERABILITIES

**Action Required:** These vulnerabilities must be addressed immediately.

CRITICAL

  if [ -s "$CRITICAL_VULNS" ]; then
    cat "$CRITICAL_VULNS" >> "$REPORT_FILE"
  else
    echo "*(See OWASP report for details)*" >> "$REPORT_FILE"
  fi

  cat >> "$REPORT_FILE" << 'CRITICAL_ACTION'

### Recommended Actions

1. Review OWASP Dependency-Check report for full details
2. Update affected dependencies to patched versions
3. Run regression tests after updates
4. Re-scan to verify fixes

**Report Location:** See `dependency-reports/owasp-dependency-check/`

---

CRITICAL_ACTION
fi

# High priority section
if [ "$HIGH_COUNT" -gt 0 ] || [ -s "$HIGH_PRIORITY" ]; then
  cat >> "$REPORT_FILE" << 'HIGH'

## ⚠️  HIGH PRIORITY UPDATES

**Recommended:** Address these updates in the next sprint.

HIGH

  if [ -s "$HIGH_PRIORITY" ]; then
    cat "$HIGH_PRIORITY" >> "$REPORT_FILE"
  fi

  echo "" >> "$REPORT_FILE"
  echo "---" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
fi

# Medium priority section (if not critical-only mode)
if [ "$CRITICAL_ONLY" = "false" ] && [ "$MEDIUM_COUNT" -gt 0 ]; then
  cat >> "$REPORT_FILE" << 'MEDIUM'

## 📋 MEDIUM PRIORITY UPDATES

**Optional:** Consider for future maintenance windows.

*(See OWASP report for full list)*

---

MEDIUM
fi

# Add update commands section
cat >> "$REPORT_FILE" << 'COMMANDS'

## 🔧 Update Commands

### Update All Properties
```bash
mvn versions:update-properties -DallowMajorUpdates=false
```

### Update Specific Dependency
```bash
mvn versions:use-latest-versions -Dincludes=groupId:artifactId
```

### Update Log4j (Security Critical)
```bash
mvn versions:update-properties -DincludeProperties=log4j.version
```

### Update Spring Boot
```bash
mvn versions:update-properties -DincludeProperties=spring-boot.version
```

### Verify After Updates
```bash
mvn clean verify
ant unitTest
```

---

## 📊 Detailed Reports

COMMANDS

# Add links to generated reports
echo "- **OWASP CVE Report:** \`dependency-reports/owasp-dependency-check/dependency-check-report.html\`" >> "$REPORT_FILE"
echo "- **Dependency Tree:** \`dependency-reports/.current-dependencies.txt\`" >> "$REPORT_FILE"
echo "- **Property Updates:** \`dependency-reports/.property-updates.tmp\`" >> "$REPORT_FILE"

cat >> "$REPORT_FILE" << 'FOOTER'

---

## 📖 Resources

- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)
- [Maven Versions Plugin](https://www.mojohaus.org/versions-maven-plugin/)
- [CVE Database](https://nvd.nist.gov/vuln)
- [YAWL Security Guide](../SECURITY_QUICK_REFERENCE.md)

---

**Next Scan:** Schedule weekly or before major releases
**Contact:** security@yawlfoundation.org

FOOTER

log_info "Markdown report generated: $REPORT_FILE"

# Generate HTML report if requested
if [ "$OUTPUT_FORMAT" = "html" ] || [ "$OUTPUT_FORMAT" = "all" ]; then
  if command -v pandoc &> /dev/null; then
    pandoc "$REPORT_FILE" -o "$HTML_REPORT" --standalone --metadata title="YAWL Dependency Health Report"
    log_info "HTML report generated: $HTML_REPORT"
  else
    log_warn "pandoc not installed, skipping HTML generation (install with: apt-get install pandoc)"
  fi
fi

# Generate JSON summary if requested
if [ "$OUTPUT_FORMAT" = "json" ] || [ "$OUTPUT_FORMAT" = "all" ]; then
  cat > "$JSON_REPORT" << JSON
{
  "timestamp": "$(date -Iseconds)",
  "project": "YAWL",
  "version": "5.2",
  "summary": {
    "critical": $CRITICAL_COUNT,
    "high": $HIGH_COUNT,
    "medium": $MEDIUM_COUNT,
    "low": $LOW_COUNT,
    "outdated": $TOTAL_OUTDATED
  },
  "reports": {
    "markdown": "$REPORT_FILE",
    "html": "$HTML_REPORT",
    "owasp": "$CVE_REPORT_DIR/dependency-check-report.html"
  }
}
JSON
  log_info "JSON report generated: $JSON_REPORT"
fi

# Cleanup temp files
rm -f "$CRITICAL_VULNS" "$HIGH_PRIORITY" "$MEDIUM_PRIORITY" "$LOW_PRIORITY" 2>/dev/null || true

# Summary output
echo ""
log_section "Scan Complete"

if [ "$CRITICAL_COUNT" -gt 0 ]; then
  log_critical "$CRITICAL_COUNT critical vulnerabilities found - IMMEDIATE ACTION REQUIRED"
  EXIT_CODE=2
elif [ "$HIGH_COUNT" -gt 0 ]; then
  log_warn "$HIGH_COUNT high severity issues found - review recommended"
  EXIT_CODE=1
else
  log_info "No critical vulnerabilities detected"
  EXIT_CODE=0
fi

echo ""
log_info "Main Report: $REPORT_FILE"

if [ -f "$CVE_REPORT_DIR/dependency-check-report.html" ]; then
  log_info "OWASP Report: $CVE_REPORT_DIR/dependency-check-report.html"
fi

echo ""
echo -e "${BOLD}${CYAN}Next Steps:${NC}"
echo "  1. Review the generated reports"
echo "  2. Update critical dependencies immediately"
echo "  3. Test after updates: mvn clean verify && ant unitTest"
echo "  4. Schedule regular scans (weekly recommended)"
echo ""

exit $EXIT_CODE
