# YAWL v6.0.0-GA Benchmark CI/CD Integration

This document provides integration guidelines for incorporating benchmark orchestration into CI/CD pipelines.

## Overview

The benchmark orchestration scripts can be integrated into existing CI/CD workflows to provide automated performance testing, regression detection, and quality gates.

## Integration Options

### 1. GitHub Actions Integration

```yaml
# .github/workflows/benchmark.yml
name: YAWL Benchmark Suite

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Set up Java 25
      uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'

    - name: Cache Maven dependencies
      uses: actions/cache@v4
      with:
        path: ~/.m2/repository
        key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-maven-

    - name: Run CI Profile Benchmarks
      run: |
        chmod +x benchmark-scripts/run-benchmarks.sh
        ./benchmark-scripts/run-benchmarks.sh ci \
          --report-json \
          --baseline-comparison \
          --parallel 4

    - name: Process Results
      run: |
        chmod +x benchmark-scripts/process-results.sh
        ./benchmark-scripts/process-results.sh \
          --results-dir benchmark-results \
          --format html

    - name: Upload Results
      uses: actions/upload-artifact@v4
      with:
        name: benchmark-results
        path: benchmark-results/

    - name: Performance Quality Gate
      run: |
        # Check for performance regressions
        if grep -q "regressionsDetected.*true" benchmark-results/*/regression-processed.json; then
          echo "Performance regressions detected!"
          exit 1
        fi
```

### 2. Jenkins Pipeline Integration

```groovy
// Jenkinsfile
pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/temurin-25-jdk-amd64'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Setup') {
            steps {
                sh 'chmod +x benchmark-scripts/*.sh'
                sh './benchmark-scripts/dx.sh compile all'
            }
        }

        stage('Benchmark CI Profile') {
            steps {
                sh '''
                    ./benchmark-scripts/run-benchmarks.sh ci \
                      --report-json \
                      --baseline-comparison \
                      --parallel 4
                '''
            }
        }

        stage('Process Results') {
            steps {
                sh '''
                    ./benchmark-scripts/process-results.sh \
                      --results-dir benchmark-results \
                      --format html
                '''
            }
        }

        stage('Performance Gate') {
            steps {
                script {
                    def regressionFile = sh(
                        script: 'find benchmark-results -name "regression-processed.json" -exec grep -l "regressionsDetected.*true" {} \\;',
                        returnStdout: true
                    ).trim()

                    if (regressionFile) {
                        error("Performance regressions detected in: ${regressionFile}")
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'benchmark-results/**/*', fingerprint: true
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'benchmark-results',
                reportFiles: 'comprehensive-report.html',
                reportName: 'Benchmark Report'
            ])
        }

        failure {
            emailext (
                subject: "YAWL Benchmark Failed: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                body: """
                    <h3>YAWL Benchmark Failed</h3>
                    <p>Job: ${env.JOB_NAME}</p>
                    <p>Build: ${env.BUILD_NUMBER}</p>
                    <p>URL: ${env.BUILD_URL}</p>
                """,
                to: 'team@yawlfoundation.org'
            )
        }
    }
}
```

### 3. GitLab CI Integration

```yaml
# .gitlab-ci.yml
stages:
  - build
  - benchmark
  - report

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"

benchmark_ci:
  stage: benchmark
  image: maven:3.8.6-openjdk-25
  cache:
    paths:
      - .m2/repository/
  before_script:
    - chmod +x benchmark-scripts/*.sh
  script:
    - ./benchmark-scripts/dx.sh compile all
    - ./benchmark-scripts/run-benchmarks.sh ci \
        --report-json \
        --baseline-comparison \
        --parallel 4
    - ./benchmark-scripts/process-results.sh \
        --results-dir benchmark-results \
        --format html
  artifacts:
    paths:
      - benchmark-results/
    reports:
      junit: benchmark-results/*/surefire-reports/*.xml
  rules:
    - if: $CI_COMMIT_BRANCH == "main"

benchmark_production:
  stage: benchmark
  image: maven:3.8.6-openjdk-25
  cache:
    paths:
      - .m2/repository/
  before_script:
    - chmod +x benchmark-scripts/*.sh
  script:
    - ./benchmark-scripts/dx.sh compile all
    - ./benchmark-scripts/run-benchmarks.sh production \
        --report-json \
        --baseline-comparison \
        --parallel 8
    - ./benchmark-scripts/process-results.sh \
        --results-dir benchmark-results \
        --format html
  artifacts:
    paths:
      - benchmark-results/
    expire_in: 1 week
  rules:
    - if: $CI_COMMIT_BRANCH == "main"
```

## Quality Gates Configuration

### Performance Thresholds

Create `.github/workflows/quality-gates.yml` for automated quality checks:

```yaml
name: Performance Quality Gates

on:
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  quality-gate:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Run Regression Tests
      run: |
        chmod +x benchmark-scripts/run-regression-tests.sh
        ./benchmark-scripts/run-regression-tests.sh \
          --baseline-comparison \
          --thresholds strict

    - name: Check Thresholds
      run: |
        if [[ -f "benchmark-results/*/threshold-analysis.json" ]]; then
          violations=$(grep -c "overall_status.*fail" benchmark-results/*/threshold-analysis.json || echo "0")
          if [[ $violations -gt 0 ]]; then
            echo "Performance quality gate failed!"
            exit 1
          fi
        fi
```

### Threshold Configuration File

Create `thresholds.json` for defining performance thresholds:

```json
{
    "thresholds": {
        "hard_limit_cpu": 90,
        "hard_limit_memory": 80,
        "hard_limit_disk": 95,
        "hard_limit_throughput": 1000,
        "hard_limit_latency": 5000,
        "hard_limit_error_rate": 0.05,
        "soft_limit_cpu": 70,
        "soft_limit_memory": 60,
        "soft_limit_disk": 80,
        "soft_limit_throughput": 500,
        "soft_limit_latency": 3000,
        "soft_limit_error_rate": 0.01
    },
    "alert_channels": ["console", "log"],
    "notification_settings": {
        "email": {
            "enabled": true,
            "recipients": ["team@yawlfoundation.org"],
            "on_failure": true
        },
        "slack": {
            "enabled": true,
            "webhook_url": "${{ secrets.SLACK_WEBHOOK }}",
            "channel": "#performance-alerts"
        }
    }
}
```

## Environment-Specific Configurations

### Development Environment

```bash
# .env.development
BENCHMARK_PROFILE=development
PARALLEL_JOBS=2
JMH_THREADS=1
MONITORING_INTERVAL=60
ENABLE_ALERTS=false
```

### Staging Environment

```bash
# .env.staging
BENCHMARK_PROFILE=ci
PARALLEL_JOBS=4
JMH_THREADS=2
MONITORING_INTERVAL=30
ENABLE_ALERTS=true
ALERT_CHANNELS=["console", "log"]
```

### Production Environment

```bash
# .env.production
BENCHMARK_PROFILE=production
PARALLEL_JOBS=8
JMH_THREADS=4
MONITORING_INTERVAL=15
ENABLE_ALERTS=true
ALERT_CHANNELS=["console", "log", "email", "slack"]
THRESHOLD_LEVEL="strict"
```

## Integration Best Practices

### 1. Gradual Rollout Strategy

```yaml
# Example: Canary deployment with benchmarks
stages:
  - benchmark_development
  - benchmark_staging
  - benchmark_production
  - deploy_canary
  - benchmark_canary
  - deploy_full
```

### 2. Historical Baseline Management

```bash
# Scripts to manage historical baselines
./benchmark-scripts/manage-baselines.sh --create
./benchmark-scripts/manage-baselines.sh --compare --baseline-id 20240201-120000
```

### 3. Performance Regression Detection

```bash
# Automated regression detection in CI
./benchmark-scripts/run-regression-tests.sh \
  --baseline-comparison \
  --trend-analysis \
  --regression-detection
```

### 4. Cost Optimization

```yaml
# Configure different resource levels for different environments
matrix:
  environment: [dev, staging, production]
  parallel_jobs: [2, 4, 8]
  jmh_forks: [1, 2, 3]
```

## Monitoring and Alerting Integration

### Prometheus Metrics

```yaml
# Add to Kubernetes deployment
env:
  - name: ENABLE_METRICS
    value: "true"
  - name: METRICS_PORT
    value: "9090"
```

### Grafana Dashboards

```json
{
  "dashboard": {
    "title": "YAWL Benchmark Metrics",
    "panels": [
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [{
          "expr": "yawl_benchmark_response_time_ms",
          "legendFormat": "{{benchmark}}"
        }]
      }
    ]
  }
}
```

## Troubleshooting Common Issues

### 1. Memory Issues in CI

```bash
# Increase Maven memory for large benchmark suites
export MAVEN_OPTS="-Xmx4g -XX:+UseZGC -XX:+UseCompactObjectHeaders"
```

### 2. Test Flakiness

```bash
# Configure retry mechanism
./benchmark-scripts/run-benchmarks.sh ci \
  --max-retries 3 \
  --retry-delay 30 \
  --fail-fast false
```

### 3. Large Result Artifacts

```bash
# Compress large results
find benchmark-results -name "*.json" | xargs gzip
```

## Example: Complete CI/CD Workflow

```yaml
name: YAWL Complete CI/CD Pipeline

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Run unit tests
      run: ./benchmark-scripts/dx.sh test

  benchmark_ci:
    runs-on: ubuntu-latest
    needs: test
    steps:
    - uses: actions/checkout@v4
    - name: Run CI benchmarks
      run: |
        ./benchmark-scripts/run-benchmarks.sh ci \
          --report-all \
          --parallel 4
    - name: Upload results
      uses: actions/upload-artifact@v4

  benchmark_production:
    runs-on: ubuntu-latest
    needs: test
    if: github.ref == 'refs/heads/main'
    steps:
    - uses: actions/checkout@v4
    - name: Run production benchmarks
      run: |
        ./benchmark-scripts/run-benchmarks.sh production \
          --report-all \
          --parallel 8
    - name: Check quality gates
      run: |
        ./benchmark-scripts/process-results.sh --threshold-detection
        if grep -q "threshold_violations" benchmark-results/*/threshold-analysis.json; then
          exit 1
        fi

  deploy:
    runs-on: ubuntu-latest
    needs: [benchmark_ci, benchmark_production]
    if: github.ref == 'refs/heads/main'
    steps:
    - uses: actions/checkout@v4
    - name: Deploy to production
      run: echo "Deploying to production..."
```

This comprehensive integration ensures that performance testing is automated and part of the standard development workflow, helping catch performance issues early and maintaining system quality.