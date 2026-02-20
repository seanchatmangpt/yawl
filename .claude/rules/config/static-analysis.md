---
paths:
  - "checkstyle.xml"
  - "pmd-ruleset.xml"
  - "spotbugs-exclude.xml"
  - "owasp-suppressions.xml"
  - "codecov.yml"
  - ".github/**"
  - "ci-cd/**"
---

# Static Analysis & CI/CD Rules

## Analysis Tools
- **SpotBugs**: Bug pattern detection. Exclusions in `spotbugs-exclude.xml`
- **PMD**: Code style and complexity. Rules in `pmd-ruleset.xml`
- **Checkstyle**: Formatting standards. Config in `checkstyle.xml`
- **OWASP Dependency Check**: CVE scanning. Suppressions in `owasp-suppressions.xml`
- Run all: `mvn clean verify -P analysis`

## Profiles
- `ci` — JaCoCo + SpotBugs (every PR)
- `analysis` — JaCoCo + SpotBugs + Checkstyle + PMD (deep analysis)
- `security` — SBOM + OWASP dependency check
- `prod` — Full validation, fails on CVSS >= 7
- `sonar` — SonarQube push (requires `SONAR_TOKEN`)

## CI/CD Conventions
- GitHub Actions workflows in `.github/workflows/`
- Matrix builds: test across Java versions listed in `validation.conf`
- Cache Maven repository between runs (`~/.m2/repository`)
- Fail fast on first module failure
- Coverage reports upload to Codecov

## Suppression Rules
- Every SpotBugs/PMD/OWASP suppression must have a comment explaining why
- Suppressions are reviewed quarterly — remove stale ones
- Never suppress security findings without a compensating control documented
