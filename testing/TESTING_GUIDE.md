# YAWL Complete Testing Guide

Comprehensive guide for running the YAWL test suite across all deployment platforms.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Detailed Testing](#detailed-testing)
3. [Platform-Specific Instructions](#platform-specific-instructions)
4. [Continuous Integration](#continuous-integration)
5. [Advanced Usage](#advanced-usage)
6. [Troubleshooting](#troubleshooting)

## Quick Start

### Prerequisites

```bash
# Python 3.7+
python --version

# Docker (for Docker Compose tests)
docker --version
docker-compose --version

# kubectl (for Kubernetes tests)
kubectl version

# Git (already available)
git --version
```

### Basic Setup

```bash
# Navigate to repository
cd /home/user/yawl

# Install Python dependencies
pip install -r testing/integration-tests/requirements.txt

# Install Chaos Toolkit (optional)
pip install chaostoolkit chaostoolkit-kubernetes
```

### Run All Tests (15-30 minutes)

```bash
# 1. Infrastructure tests (2-5 min)
python testing/infrastructure-tests.py

# 2. Deployment validation (5-10 min)
bash testing/deployment-validation.sh

# 3. Integration tests (10-30 min)
pytest testing/integration-tests/ -v

# 4. Chaos tests (optional, 30+ min)
chaos run testing/chaos-engineering-tests/experiments.yaml
```

## Detailed Testing

### 1. Infrastructure Tests (infrastructure-tests.py)

Validates platform infrastructure components.

#### Features

- Kubernetes: Cluster connectivity, pod health, deployments, services, volumes
- Docker Compose: Container status, health checks, networking
- Cloud Run: Service deployment, URL accessibility
- Database: Connection, schema, performance metrics
- Health Checks: HTTP endpoints, response times

#### Basic Usage

```bash
# Run all infrastructure tests
python testing/infrastructure-tests.py

# Run with verbose output
python testing/infrastructure-tests.py -v

# Test specific platform
python testing/infrastructure-tests.py --platform kubernetes
```

#### Output

Report saved to: `/tmp/yawl-infrastructure-tests-report.txt`

```
================================================================================
YAWL INFRASTRUCTURE TEST REPORT
Generated: 2024-02-14 10:30:45
================================================================================

SUMMARY
--------
Total Tests:   24
Passed:        23 (95.8%)
Failed:        1 (4.2%)
Total Duration: 45.23s

RESULTS BY PLATFORM
--------
KUBERNETES
  Tests: 8, Passed: 8, Failed: 0
  ✓ PASS: Cluster Connectivity (0.45s)
  ✓ PASS: Namespace Exists (0.32s)
  ...
```

#### Environment Variables

```bash
# Kubernetes
export K8S_NAMESPACE=yawl

# Database
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=postgres
export DB_PASSWORD=yawl-secure-password
export DB_NAME=yawl

# Cloud Run
export GCP_PROJECT_ID=your-project-id
export GCP_REGION=us-central1
```

### 2. Deployment Validation (deployment-validation.sh)

Shell script for comprehensive deployment validation.

#### Supported Platforms

- Kubernetes (GKE, EKS, AKS)
- Docker Compose
- Cloud Run
- AWS CloudFormation
- Azure ARM Templates

#### Basic Usage

```bash
# Validate all available platforms
bash testing/deployment-validation.sh

# Validate specific platform
bash testing/deployment-validation.sh --platform kubernetes
bash testing/deployment-validation.sh --platform docker-compose
bash testing/deployment-validation.sh --platform cloud-run

# Verbose output for debugging
bash testing/deployment-validation.sh --verbose

# Get help
bash testing/deployment-validation.sh --help
```

#### Output

Report saved to: `/tmp/yawl-deployment-validation-report-*.txt`

```
========================================
YAWL DEPLOYMENT VALIDATION
========================================
Platform: auto
Verbose: false
Log file: /tmp/yawl-deployment-validation-2024-02-14.log

=== KUBERNETES VALIDATION ===
✓ Kubernetes cluster connectivity
✓ Namespace 'yawl' exists
✓ Deployment 'yawl' exists
✓ Deployment has 3/3 replicas ready
✓ Found 3 YAWL pods
✓ All 3 pods are running
...
```

### 3. Smoke Tests (smoke-tests.yaml)

Quick validation tests for critical paths.

#### Test Suites

- Kubernetes smoke tests (7 tests)
- Docker Compose smoke tests (5 tests)
- HTTP health checks (3 tests)
- Database smoke tests (4 tests)
- Cloud Run smoke tests (3 tests)
- AWS smoke tests (3 tests)
- Azure smoke tests (3 tests)

#### Review Tests

```bash
# View smoke test definitions
cat testing/smoke-tests.yaml

# View Kubernetes tests
grep -A 20 "kubernetes:" testing/smoke-tests.yaml

# View Docker tests
grep -A 20 "docker_compose:" testing/smoke-tests.yaml
```

### 4. Integration Tests (testing/integration-tests/)

End-to-end integration tests using pytest.

#### Test Files

- `test_api.py`: REST API endpoints
- `test_database.py`: PostgreSQL operations
- `test_docker.py`: Docker operations
- `conftest.py`: Pytest fixtures

#### Installation

```bash
# Install test dependencies
cd testing/integration-tests
pip install -r requirements.txt
```

#### Configuration

```bash
# Create/edit config.yaml
cp config.yaml.template config.yaml
vi config.yaml
```

Or use environment variables:

```bash
export BASE_URL=http://localhost:8080
export DB_HOST=localhost
export DB_USER=postgres
export DB_PASSWORD=yawl-secure-password
```

#### Running Tests

```bash
# All tests
pytest testing/integration-tests/ -v

# Specific test file
pytest testing/integration-tests/test_api.py -v

# Specific test class
pytest testing/integration-tests/test_api.py::TestResourceService -v

# Specific test
pytest testing/integration-tests/test_api.py::TestResourceService::test_resource_service_health -v

# Smoke tests only
pytest testing/integration-tests/ -m smoke -v

# Skip slow tests
pytest testing/integration-tests/ -m "not slow" -v

# Run in parallel (4 workers)
pytest testing/integration-tests/ -v -n 4

# Generate HTML report
pytest testing/integration-tests/ -v --html=report.html --self-contained-html

# Generate coverage report
pytest testing/integration-tests/ -v --cov=. --cov-report=html

# Show print statements
pytest testing/integration-tests/ -v -s

# Exit on first failure
pytest testing/integration-tests/ -v -x

# Run with markers
pytest testing/integration-tests/ -v -m "integration and not slow"
```

#### Available Markers

```bash
# Integration tests
pytest -m integration

# Smoke tests (quick)
pytest -m smoke

# Slow tests
pytest -m slow

# Requires Docker
pytest -m requires_docker

# Requires Kubernetes
pytest -m requires_k8s

# Requires database
pytest -m requires_db
```

#### Output

```
testing/integration-tests/test_api.py::TestResourceService::test_resource_service_health PASSED [  0%]
testing/integration-tests/test_api.py::TestResourceService::test_resource_service_available PASSED [  1%]
testing/integration-tests/test_database.py::TestDatabaseConnectivity::test_database_connection PASSED [  5%]
...

============================== 45 passed in 23.45s ===============================
```

### 5. Chaos Engineering Tests (testing/chaos-engineering-tests/)

Resilience testing using Chaos Toolkit.

#### Installation

```bash
# Install Chaos Toolkit
pip install chaostoolkit chaostoolkit-kubernetes chaostoolkit-http

# Verify installation
chaos --version
```

#### Configuration

```bash
# Review experiments
cat testing/chaos-engineering-tests/experiments.yaml

# Review settings
cat testing/chaos-engineering-tests/settings.yaml
```

#### Running Experiments

```bash
# All experiments
chaos run testing/chaos-engineering-tests/experiments.yaml

# Specific experiment
chaos run testing/chaos-engineering-tests/experiments.yaml --filter pod-restart

# Dry run (preview)
chaos run testing/chaos-engineering-tests/experiments.yaml --dry

# With custom settings
chaos run testing/chaos-engineering-tests/experiments.yaml --settings testing/chaos-engineering-tests/settings.yaml

# View results
cat /tmp/yawl-chaos-report.json
open /tmp/yawl-chaos-report.html
```

#### Experiments

- `pod-restart`: Pod restart handling
- `node-drain`: Node drain recovery
- `resource-exhaustion`: Resource exhaustion handling
- `network-partition`: Network partition handling
- `database-unavailable`: Database failure recovery
- `database-latency`: High latency handling
- `service-degradation`: Graceful degradation
- `cascading-failure`: Cascading failure handling
- `memory-leak-detection`: Memory leak detection
- `docker-compose-container-crash`: Container crash recovery
- `disk-space-exhaustion`: Disk space handling
- `recovery-time-objective`: RTO validation

## Platform-Specific Instructions

### Kubernetes (GKE, EKS, AKS)

#### Prerequisites

```bash
# Check cluster connectivity
kubectl cluster-info

# Check YAWL deployment
kubectl get deployment yawl -n yawl
kubectl get pods -n yawl
```

#### Run Tests

```bash
# Infrastructure tests
python testing/infrastructure-tests.py

# Deployment validation
bash testing/deployment-validation.sh --platform kubernetes

# Integration tests
pytest testing/integration-tests/ -m requires_k8s -v

# Chaos experiments
chaos run testing/chaos-engineering-tests/experiments.yaml \
  --filter "pod-restart\|node-drain\|resource-exhaustion"
```

#### Common Issues

```bash
# Connection refused
kubectl cluster-info

# Pods not running
kubectl get pods -n yawl
kubectl describe pod -n yawl

# Check logs
kubectl logs -n yawl deployment/yawl
```

### Docker Compose

#### Prerequisites

```bash
# Check docker service
docker ps
docker-compose --version

# Check compose file
ls /home/user/yawl/docker-compose.yml

# Start services
docker-compose -f /home/user/yawl/docker-compose.yml up -d
```

#### Run Tests

```bash
# Infrastructure tests
python testing/infrastructure-tests.py

# Deployment validation
bash testing/deployment-validation.sh --platform docker-compose

# Integration tests
pytest testing/integration-tests/ -m requires_docker -v

# Chaos experiments
chaos run testing/chaos-engineering-tests/experiments.yaml \
  --filter "docker-compose\|disk-space"
```

#### Common Issues

```bash
# Containers not running
docker-compose -f /home/user/yawl/docker-compose.yml ps

# Check logs
docker-compose -f /home/user/yawl/docker-compose.yml logs yawl

# Restart services
docker-compose -f /home/user/yawl/docker-compose.yml restart
```

### Cloud Run

#### Prerequisites

```bash
# Check gcloud CLI
gcloud --version

# Set project
gcloud config set project PROJECT_ID

# Check Cloud Run service
gcloud run services describe yawl-workflow --region=us-central1
```

#### Run Tests

```bash
# Set environment
export GCP_PROJECT_ID=your-project-id
export GCP_REGION=us-central1

# Deployment validation
bash testing/deployment-validation.sh --platform cloud-run

# Infrastructure tests (includes Cloud Run)
python testing/infrastructure-tests.py
```

### AWS

#### Prerequisites

```bash
# Check AWS CLI
aws --version

# Set region
export AWS_REGION=us-east-1

# Check CloudFormation stack
aws cloudformation describe-stacks --stack-name yawl
```

#### Run Tests

```bash
# Deployment validation
bash testing/deployment-validation.sh --platform aws

# Infrastructure tests
python testing/infrastructure-tests.py
```

### Azure

#### Prerequisites

```bash
# Check Azure CLI
az --version

# Set subscription
az account set --subscription SUBSCRIPTION_ID

# Check resources
az group show --name yawl-rg
```

#### Run Tests

```bash
# Set environment
export AZURE_RESOURCE_GROUP=yawl-rg

# Deployment validation
bash testing/deployment-validation.sh --platform auto

# Infrastructure tests
python testing/infrastructure-tests.py
```

## Continuous Integration

### GitHub Actions

```yaml
name: YAWL Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:14-alpine
        env:
          POSTGRES_DB: yawl
          POSTGRES_PASSWORD: yawl-secure-password
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-python@v2
        with:
          python-version: '3.10'

      - name: Install dependencies
        run: |
          pip install -r testing/integration-tests/requirements.txt
          pip install chaostoolkit chaostoolkit-kubernetes

      - name: Infrastructure tests
        run: python testing/infrastructure-tests.py

      - name: Deployment validation
        run: bash testing/deployment-validation.sh

      - name: Integration tests
        run: pytest testing/integration-tests/ -v --cov --junit-xml=results.xml

      - name: Upload results
        if: always()
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: |
            results.xml
            /tmp/yawl-infrastructure-tests-report.txt
            /tmp/yawl-deployment-validation-report-*.txt
```

### GitLab CI

```yaml
test:
  image: python:3.10
  services:
    - postgres:14-alpine
  variables:
    POSTGRES_DB: yawl
    POSTGRES_PASSWORD: yawl-secure-password
    DB_HOST: postgres
  before_script:
    - pip install -r testing/integration-tests/requirements.txt
  script:
    - python testing/infrastructure-tests.py
    - bash testing/deployment-validation.sh
    - pytest testing/integration-tests/ -v --junit-xml=results.xml
  artifacts:
    reports:
      junit: results.xml
```

## Advanced Usage

### Custom Test Configuration

```bash
# Create custom config
cat > testing/integration-tests/config.custom.yaml << EOF
base_url: https://yawl.example.com
api_base_url: https://yawl.example.com/resourceService
timeout: 60
database:
  host: db.example.com
  port: 5432
  user: produser
  password: ${DB_PASSWORD}
EOF

# Run with custom config
export CONFIG_FILE=testing/integration-tests/config.custom.yaml
pytest testing/integration-tests/ -v
```

### Custom Chaos Experiments

```bash
# Create custom experiment
cat > testing/chaos-engineering-tests/custom-experiment.yaml << EOF
version: 1.0.0
title: Custom YAWL Experiment
description: My custom resilience test

experiments:
  - name: custom-test
    description: Custom test scenario
    steps:
      - type: action
        name: test-action
        provider:
          type: shell
          command: "echo 'Testing'"
EOF

# Run custom experiment
chaos run testing/chaos-engineering-tests/custom-experiment.yaml
```

### Parallel Test Execution

```bash
# Run tests in parallel (4 workers)
pytest testing/integration-tests/ -v -n 4

# Run with specific distribution strategy
pytest testing/integration-tests/ -v -n auto

# Run with load balancing
pytest testing/integration-tests/ -v -n 4 --dist loadscope
```

### Test Filtering

```bash
# Run only API tests
pytest testing/integration-tests/test_api.py -v

# Run only database tests
pytest testing/integration-tests/test_database.py -v

# Run only certain test classes
pytest testing/integration-tests/ -v -k "TestResourceService"

# Skip certain tests
pytest testing/integration-tests/ -v --ignore=testing/integration-tests/test_docker.py

# Run with specific node ID
pytest testing/integration-tests/test_api.py::TestResourceService::test_resource_service_health
```

## Troubleshooting

### Connection Refused

```bash
# Check if services are running
docker ps
docker-compose ps

# Check YAWL logs
docker-compose logs yawl

# Wait for service to be ready
sleep 10
curl http://localhost:8080/resourceService/
```

### Database Connection Failed

```bash
# Verify credentials
echo $DB_HOST $DB_PORT $DB_USER $DB_NAME

# Test connection
psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "SELECT 1;"

# Check PostgreSQL service
docker-compose ps postgres

# View PostgreSQL logs
docker-compose logs postgres
```

### Docker Not Available

```bash
# Check Docker service
docker ps

# Start Docker
sudo systemctl start docker

# Check Docker socket
ls -la /var/run/docker.sock

# Check Docker group membership
groups

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker
```

### Kubernetes Connection Issues

```bash
# Check cluster connectivity
kubectl cluster-info

# Check context
kubectl config current-context

# Set correct context
kubectl config use-context my-cluster

# Check namespace
kubectl get namespace yawl

# Check YAWL deployment
kubectl get deployment yawl -n yawl

# View pod logs
kubectl logs -n yawl pod/yawl-xxxxx
```

### Pytest Issues

```bash
# Verbose output
pytest testing/integration-tests/ -vv

# Show print statements
pytest testing/integration-tests/ -s

# Show local variables on failure
pytest testing/integration-tests/ -l

# Full traceback
pytest testing/integration-tests/ --tb=long

# Stop on first failure
pytest testing/integration-tests/ -x

# Clear cache
pytest --cache-clear
```

### Chaos Toolkit Issues

```bash
# Verbose logging
chaos run experiments.yaml -vv

# Dry run
chaos run experiments.yaml --dry

# Check logs
cat /tmp/yawl-chaos-tests.log

# Debug output
chaos run experiments.yaml --debug
```

## Performance Baselines

### Test Execution Times

| Test Type | Duration | Platform |
|-----------|----------|----------|
| Infrastructure | 2-5 min | All |
| Deployment Validation | 5-10 min | All |
| Smoke Tests | 2-5 min | All |
| Integration (API) | 5-10 min | All |
| Integration (Database) | 3-5 min | Docker/K8s |
| Integration (Docker) | 2-3 min | Docker |
| Chaos Experiments | 30-60 min | K8s/Docker |
| **Total** | **60-120 min** | **All** |

### Expected Metrics

- Health endpoint response: < 100ms
- API endpoints: < 2 seconds
- Database queries: < 1 second
- Pod restart: < 30 seconds
- Service recovery: < 2 minutes

## References

- [YAWL Documentation](https://docs.yawlfoundation.org/)
- [Pytest Guide](https://docs.pytest.org/)
- [Chaos Toolkit Docs](https://docs.chaostoolkit.org/)
- [Docker Compose Docs](https://docs.docker.com/compose/)
- [Kubernetes Docs](https://kubernetes.io/docs/)

## Support

- Check README files in test directories
- Review test code comments
- Consult YAWL Foundation support
- Check CI/CD logs for failures
