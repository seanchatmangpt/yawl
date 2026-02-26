# YAWL Validation Report Generator

A comprehensive bash script that aggregates test results from all validation phases and generates detailed reports in multiple formats.

## Overview

This script collects data from:
- EUnit test results
- Common Test results
- Code coverage reports
- Static analysis (Dialyzer)
- Build performance metrics

It generates reports in HTML, Markdown, or JSON format with quality gate summaries and recommendations.

## Features

- **Multi-format output**: HTML, Markdown, JSON
- **Comprehensive metrics**: Test results, coverage, performance, security
- **Quality gates**: Automated pass/fail checks
- **Recommendations**: Actionable feedback for improvements
- **CI/CD ready**: Suitable for automated pipelines

## Usage

### Basic Usage

```bash
# Generate HTML report in default location
./scripts/generate-validation-report.sh

# Generate Markdown report
./scripts/generate-validation-report.sh --format md

# Generate HTML report in custom directory
./scripts/generate-validation-report.sh --format html --output ./my-reports
```

### Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--format` | Output format: html, pdf, md | `html` |
| `--output` | Output directory for reports | `reports` |
| `-h, --help` | Show help message | - |

### Environment Variables

| Variable | Description | Default |
|---------|-------------|---------|
| `REBAR3_CMD` | Path to rebar3 | `rebar3` |
| `MVN_CMD` | Path to mvn | `mvn` |
| `JUNIT_DIR` | JUnit XML results directory | `_build/test/logs` |
| `COVERAGE_DIR` | Coverage report directory | `_build/cover` |
| `REPORTS_DIR` | Default reports directory | `reports` |

## Output Files

### HTML Format
- `validation-report.html` - Interactive HTML report with charts and styling
- `validation-report.json` - Structured JSON data
- `quality-gates.yml` - Quality gate configuration

### Markdown Format
- `validation-report.md` - Markdown formatted report
- `validation-report.json` - Structured JSON data
- `quality-gates.yml` - Quality gate configuration

### PDF Format
- `validation-report.html` - HTML report for conversion
- `validation-report.json` - Structured JSON data
- `quality-gates.yml` - Quality gate configuration
- `validation-report.pdf` - PDF (requires wkhtmltopdf)

## Report Sections

### Executive Summary
- Total tests executed
- Success rate percentage
- Code coverage percentage
- Key metrics overview

### Test Results by Phase
- EUnit test results
- Common Test results
- Detailed breakdown of failures and errors
- Test execution times

### Performance Metrics
- Build time
- Number of compiled modules
- Test execution time

### Code Coverage
- Covered lines
- Total lines
- Coverage percentage

### Security & Static Analysis
- Dialyzer analysis results
- TODO/FIXME items
- Deprecated functions
- Security warnings

### Quality Gate Status
- Test success rate gate (≥ 80%)
- Code coverage gate (≥ 80%)
- Critical errors gate (0)
- Overall pass/fail status

### Recommendations
- Specific actions based on test results
- Coverage improvement suggestions
- Security fixes needed
- Technical debt reduction

## Quality Gates

The script enforces the following quality gates:

| Gate | Target | Action if Failed |
|------|--------|------------------|
| Test Success Rate | ≥ 80% | Add more tests, fix failures |
| Code Coverage | ≥ 80% | Increase test coverage |
| Critical Errors | 0 | Fix blocking test errors |
| Security Issues | 0 | Address security warnings |

## Integration with CI/CD

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh 'rebar3 compile'
                sh 'rebar3 eunit'
                sh 'rebar3 ct'
                sh 'rebar3 cover'
            }
        }
        stage('Generate Report') {
            steps {
                sh './scripts/generate-validation-report.sh --format html'
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'reports',
                    reportFiles: 'validation-report.html',
                    reportName: 'YAWL Validation Report'
                ])
            }
        }
        stage('Quality Gate Check') {
            steps {
                script {
                    def qualityGates = readYaml file: 'reports/quality-gates.yml'
                    if (qualityGates.summary.overall_status == 'fail') {
                        error 'Quality gates failed!'
                    }
                }
            }
        }
    }
}
```

### GitHub Actions Example

```yaml
name: Validation Report
on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2

    - name: Setup Erlang
      uses: erlang Actions/setup-erlang@v1
      with:
        otp-version: 28

    - name: Install Dependencies
      run: rebar3 deps

    - name: Compile
      run: rebar3 compile

    - name: Run Tests
      run: |
        rebar3 eunit
        rebar3 ct
        rebar3 cover

    - name: Generate Report
      run: ./scripts/generate-validation-report.sh --format html

    - name: Upload Report
      uses: actions/upload-artifact@v2
      with:
        name: validation-report
        path: reports/
```

## Requirements

- bash (version 4.0+)
- xmlstarlet (for XML parsing)
- rebar3 (Erlang build tool)
- Optional: wkhtmltopdf (for PDF generation)

## Troubleshooting

### Common Issues

1. **XML parsing errors**
   - Ensure JUnit XML files exist in `JUNIT_DIR`
   - Check XML files are well-formed

2. **Missing test results**
   - Run tests before generating report
   - Verify test output directories exist

3. **Coverage data missing**
   - Ensure coverage report generated with `rebar3 cover`
   - Check `COVERAGE_DIR` path is correct

4. **Permission errors**
   - Ensure output directory is writable
   - Run script with appropriate permissions

### Debug Mode

Enable verbose logging by setting `set -x` in the script or running with `bash -x`.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Submit a pull request

## License

This script is part of the YAWL project and follows the same license terms.