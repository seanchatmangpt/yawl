# YAWL Dependency Health Checker

Comprehensive security scanner for detecting outdated dependencies and CVE vulnerabilities in YAWL.

## Quick Start

```bash
# Full health check (recommended)
./.claude/check-dependencies.sh

# Skip CVE check (faster, for quick updates check only)
./.claude/check-dependencies.sh --skip-cve

# Check specific module
./.claude/check-dependencies.sh --module=yawl-engine

# Show only critical vulnerabilities
./.claude/check-dependencies.sh --critical-only

# Generate all report formats (markdown, HTML, JSON)
./.claude/check-dependencies.sh --format=all
```

## What It Does

1. **Dependency Update Check** - Scans for newer versions of all dependencies
2. **CVE Scanning** - Uses OWASP Dependency-Check to find known vulnerabilities
3. **Property Updates** - Checks for updates to version properties in pom.xml
4. **Dependency Tree** - Generates full dependency tree for analysis
5. **Report Generation** - Creates detailed reports with actionable recommendations

## Output

### Reports Generated

- **Markdown Report:** `dependency-reports/dependency-health-TIMESTAMP.md`
- **OWASP HTML Report:** `dependency-reports/owasp-dependency-check/dependency-check-report.html`
- **JSON Report:** `dependency-reports/dependency-health-TIMESTAMP.json` (with --format=json)
- **HTML Report:** `dependency-reports/dependency-health-TIMESTAMP.html` (with --format=html)

### Exit Codes

- **0:** No critical vulnerabilities
- **1:** High severity issues found (warning)
- **2:** Critical vulnerabilities detected (action required)

## Command Line Options

```
--module=MODULE      Check specific module only (e.g., yawl-engine)
--critical-only      Show only critical vulnerabilities
--format=FORMAT      Output format: markdown, html, json, all (default: markdown)
--skip-cve           Skip CVE vulnerability check (faster)
--skip-updates       Skip dependency update check
--help               Show help message
```

## Examples

### Weekly Security Scan
```bash
# Run full scan with all reports
./.claude/check-dependencies.sh --format=all
```

### Quick Check Before Deployment
```bash
# Fast check for critical issues only
./.claude/check-dependencies.sh --critical-only --skip-updates
```

### Module-Specific Check
```bash
# Check only engine module
./.claude/check-dependencies.sh --module=yawl-engine
```

### CI/CD Integration
```bash
# Fail build on critical vulnerabilities
if ! ./.claude/check-dependencies.sh --critical-only; then
  echo "Critical vulnerabilities detected!"
  exit 1
fi
```

## Understanding the Report

### Severity Levels

- **CRITICAL (CVSS 9.0-10.0):** Immediate action required, update within 24 hours
- **HIGH (CVSS 7.0-8.9):** Update within current sprint (1 week)
- **MEDIUM (CVSS 4.0-6.9):** Plan for next maintenance window (1 month)
- **LOW (CVSS 0.1-3.9):** Update when convenient (next release)

### Report Sections

1. **Executive Summary** - Quick overview of findings
2. **Critical Vulnerabilities** - CVEs requiring immediate action
3. **High Priority Updates** - Security and stability updates
4. **Medium Priority Updates** - Bug fixes and improvements
5. **Update Commands** - Copy-paste commands to fix issues
6. **Detailed Reports** - Links to full OWASP reports

## Updating Dependencies

### Update Specific Dependency

```bash
# Update log4j (security critical)
mvn versions:update-properties -DincludeProperties=log4j.version

# Update Spring Boot
mvn versions:update-properties -DincludeProperties=spring-boot.version

# Update Jackson
mvn versions:update-properties -DincludeProperties=jackson.version
```

### Update All Dependencies

```bash
# Update all (minor versions only, safer)
mvn versions:update-properties -DallowMajorUpdates=false

# Update all (interactive, review each change)
mvn versions:use-latest-versions
```

### Verify After Updates

```bash
# Compile and test
mvn clean verify

# Run YAWL unit tests
ant unitTest

# Re-scan to verify fixes
./.claude/check-dependencies.sh
```

## Scheduled Scanning

### Weekly Cron Job

Add to crontab:
```bash
# Every Monday at 2 AM
0 2 * * 1 cd /path/to/yawl && ./.claude/check-dependencies.sh --format=all
```

### GitHub Actions

Create `.github/workflows/dependency-check.yml`:
```yaml
name: Weekly Dependency Scan
on:
  schedule:
    - cron: '0 2 * * 1'
  workflow_dispatch:

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - run: ./.claude/check-dependencies.sh --format=all
      - uses: actions/upload-artifact@v4
        with:
          name: dependency-reports
          path: dependency-reports/
```

## Troubleshooting

### First Run is Slow

OWASP Dependency-Check downloads CVE database on first run (2-5 minutes). Subsequent runs are much faster.

### jq Not Found

Install jq for JSON parsing:
```bash
apt-get install jq   # Debian/Ubuntu
brew install jq      # macOS
```

### pandoc Not Found

Install pandoc for HTML report generation:
```bash
apt-get install pandoc   # Debian/Ubuntu
brew install pandoc      # macOS
```

### Maven Not Found

Ensure Maven 3.9+ is installed:
```bash
mvn --version
```

### Permission Denied

Make script executable:
```bash
chmod +x ./.claude/check-dependencies.sh
```

## Best Practices

1. **Run Weekly** - Schedule automated scans every Monday
2. **Before Releases** - Always scan before deploying to production
3. **After Updates** - Re-scan after applying security patches
4. **Monitor Critical** - Set up alerts for critical vulnerabilities
5. **Document Changes** - Keep changelog of dependency updates

## Integration Points

### Pre-Commit Hook

Add to `.git/hooks/pre-commit`:
```bash
#!/bin/bash
# Block commits if critical vulnerabilities exist
if [ -f .claude/check-dependencies.sh ]; then
  if ! .claude/check-dependencies.sh --critical-only --skip-updates; then
    echo "Critical vulnerabilities detected! Fix before committing."
    exit 1
  fi
fi
```

### CI/CD Pipeline

```bash
# In your CI script
./.claude/check-dependencies.sh --format=json
if [ $? -eq 2 ]; then
  echo "Blocking deployment due to critical vulnerabilities"
  exit 1
fi
```

### Slack Notifications

```bash
# Send report to Slack
REPORT=$(cat dependency-reports/dependency-health-*.md | head -50)
curl -X POST $SLACK_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d "{\"text\":\"Weekly Dependency Scan:\n\`\`\`$REPORT\`\`\`\"}"
```

## Security Response Workflow

1. **Critical Vulnerability Detected**
   - Create incident ticket
   - Notify security team
   - Create emergency branch

2. **Update Dependencies**
   - Run update commands from report
   - Review changelog for breaking changes
   - Update related dependencies

3. **Test Changes**
   - mvn clean verify
   - ant unitTest
   - Run integration tests

4. **Deploy Fix**
   - Create PR with security label
   - Fast-track review
   - Deploy to staging
   - Verify fix, deploy to production

5. **Verify Resolution**
   - Re-run dependency check
   - Confirm vulnerability cleared
   - Update documentation

## Resources

- **OWASP Dependency-Check:** https://owasp.org/www-project-dependency-check/
- **Maven Versions Plugin:** https://www.mojohaus.org/versions-maven-plugin/
- **CVE Database:** https://nvd.nist.gov/vuln
- **YAWL Security:** ../SECURITY_QUICK_REFERENCE.md

## Support

- **Questions:** Open issue in YAWL repository
- **Security Issues:** security@yawlfoundation.org
- **Tool Bugs:** Create issue with `dependency-check` label

---

**Version:** 1.0.0
**Last Updated:** 2026-02-16
