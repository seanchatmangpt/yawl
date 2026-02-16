# Dependency Health Checker - Quick Reference

## One-Line Commands

```bash
# Full scan with all reports
./.claude/check-dependencies.sh --format=all

# Quick check (no CVE scan)
./.claude/check-dependencies.sh --skip-cve

# Critical only
./.claude/check-dependencies.sh --critical-only

# Specific module
./.claude/check-dependencies.sh --module=yawl-engine

# Help
./.claude/check-dependencies.sh --help
```

## Exit Codes

| Code | Meaning | CI/CD Action |
|------|---------|--------------|
| 0 | No critical issues | Continue |
| 1 | High severity | Warn |
| 2 | Critical vulnerabilities | Block |

## Common Updates

```bash
# Update log4j (security)
mvn versions:update-properties -DincludeProperties=log4j.version

# Update Spring Boot
mvn versions:update-properties -DincludeProperties=spring-boot.version

# Update Jackson
mvn versions:update-properties -DincludeProperties=jackson.version

# Update all (minor only)
mvn versions:update-properties -DallowMajorUpdates=false

# Verify
mvn clean verify && ant unitTest
```

## Report Locations

```bash
# Main report
dependency-reports/dependency-health-YYYY-MM-DD_HH-MM-SS.md

# OWASP HTML
dependency-reports/owasp-dependency-check/dependency-check-report.html

# JSON summary
dependency-reports/dependency-health-YYYY-MM-DD_HH-MM-SS.json
```

## CI/CD Integration

```bash
# Fail on critical
./.claude/check-dependencies.sh --critical-only || exit 1

# Warn on high
./.claude/check-dependencies.sh && echo "OK" || echo "WARNING"

# JSON for parsing
./.claude/check-dependencies.sh --format=json
```

## Scheduled Scans

```bash
# Crontab (weekly Monday 2 AM)
0 2 * * 1 cd /path/to/yawl && ./.claude/check-dependencies.sh --format=all

# GitHub Actions (copy to .github/workflows/)
cp .claude/dependency-check-workflow.yml .github/workflows/
```

## Severity Levels

- **CRITICAL** (9.0-10.0): Fix within 24 hours
- **HIGH** (7.0-8.9): Fix within 1 week
- **MEDIUM** (4.0-6.9): Fix within 1 month
- **LOW** (0.1-3.9): Fix when convenient

## Troubleshooting

```bash
# Install jq
apt-get install jq

# Install pandoc
apt-get install pandoc

# Check Maven
mvn --version

# Clean reports
rm -rf dependency-reports/
```

## Full Documentation

- User Guide: `.claude/README-DEPENDENCY-CHECK.md`
- Implementation: `.claude/DEPENDENCY_CHECK_IMPLEMENTATION.md`
- Report Template: `.claude/DEPENDENCY_HEALTH.md`
