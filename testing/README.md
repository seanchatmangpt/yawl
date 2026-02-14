# YAWL Comprehensive Test Suite

Complete testing infrastructure for YAWL Workflow Engine across all deployment platforms.

## Overview

This test suite provides comprehensive validation of YAWL deployments including:

- **Infrastructure Tests**: Kubernetes, Docker Compose, Cloud Run, AWS, Azure
- **Deployment Validation**: Multi-platform deployment verification
- **Smoke Tests**: Quick validation tests for critical paths
- **Integration Tests**: End-to-end API, database, and service tests
- **Chaos Engineering**: Resilience testing under failure conditions

## Directory Structure

```
testing/
├── infrastructure-tests.py       # Python infrastructure validation
├── deployment-validation.sh      # Shell script deployment checks
├── smoke-tests.yaml             # Quick smoke test definitions
├── integration-tests/           # Integration test suite
│   ├── conftest.py             # Pytest configuration and fixtures
│   ├── test_api.py             # API endpoint tests
│   ├── test_database.py        # Database connectivity tests
│   ├── test_docker.py          # Docker/Docker Compose tests
│   ├── config.yaml             # Test configuration
│   └── README.md               # Integration tests documentation
├── chaos-engineering-tests/     # Chaos Toolkit experiments
│   ├── experiments.yaml        # Chaos experiments definition
│   ├── settings.yaml           # Chaos Toolkit settings
│   └── README.md               # Chaos engineering documentation
└── README.md                   # This file
```

## Quick Start

### 1. Infrastructure Tests

```bash
# Run all infrastructure tests
python testing/infrastructure-tests.py

# Test specific platform
python testing/infrastructure-tests.py --platform kubernetes
```

### 2. Deployment Validation

```bash
# Validate all platforms
bash testing/deployment-validation.sh

# Validate specific platform
bash testing/deployment-validation.sh --platform kubernetes

# Verbose output
bash testing/deployment-validation.sh --verbose
```

### 3. Smoke Tests

```bash
# Review smoke test definitions
cat testing/smoke-tests.yaml

# Run tests (requires test runner implementation)
pytest testing/smoke-tests.yaml
```

### 4. Integration Tests

```bash
# Install dependencies
pip install -r testing/integration-tests/requirements.txt

# Run all tests
pytest testing/integration-tests/ -v

# Run specific test class
pytest testing/integration-tests/test_api.py::TestResourceService -v

# Run smoke tests only
pytest testing/integration-tests/ -m smoke -v

# Generate HTML report
pytest testing/integration-tests/ --html=report.html --self-contained-html
```

### 5. Chaos Engineering

```bash
# Install Chaos Toolkit
pip install chaostoolkit chaostoolkit-kubernetes

# Run all experiments
chaos run testing/chaos-engineering-tests/experiments.yaml

# Run specific experiment
chaos run testing/chaos-engineering-tests/experiments.yaml --filter pod-restart

# Dry run (preview only)
chaos run testing/chaos-engineering-tests/experiments.yaml --dry
```

## Test Files Description

### infrastructure-tests.py

Comprehensive Python-based infrastructure validation.

**Features**:
- Kubernetes cluster connectivity and pod health
- Docker Compose container status
- Cloud Run service deployment
- Database connectivity
- HTTP health checks
- Resource usage monitoring

**Usage**:
```bash
python testing/infrastructure-tests.py
```

**Output**: Report to `/tmp/yawl-infrastructure-tests-report.txt`

### deployment-validation.sh

Shell script for deployment validation across platforms.

**Platforms**:
- Kubernetes (GKE, EKS, AKS)
- Docker Compose
- Cloud Run
- AWS CloudFormation
- Azure ARM Templates

**Usage**:
```bash
bash testing/deployment-validation.sh --platform kubernetes --verbose
```

**Output**: Report to `/tmp/yawl-deployment-validation-report-*.txt`

### smoke-tests.yaml

Quick validation tests for critical paths.

**Test Suites**:
- Kubernetes smoke tests
- Docker Compose smoke tests
- HTTP health checks
- Database connectivity
- Cloud Run validation
- AWS services
- Azure resources

**Configuration**: YAML format with expected outcomes

### integration-tests/

Comprehensive pytest-based integration tests.

**Test Files**:
- `test_api.py`: REST API endpoint tests
- `test_database.py`: PostgreSQL connectivity and integrity
- `test_docker.py`: Docker and Docker Compose validation
- `conftest.py`: Pytest fixtures and configuration

**Fixtures**:
- `api_client`: REST API client
- `db_connection`: PostgreSQL connection
- `docker_client`: Docker client
- `config`: Test configuration
- `wait_for_service`: Service readiness waiter

**Markers**:
- `@pytest.mark.integration`: Integration tests
- `@pytest.mark.smoke`: Smoke tests
- `@pytest.mark.slow`: Slow running tests
- `@pytest.mark.requires_docker`: Requires Docker
- `@pytest.mark.requires_k8s`: Requires Kubernetes
- `@pytest.mark.requires_db`: Requires database

### chaos-engineering-tests/

Chaos Toolkit experiments for resilience testing.

**Experiments**:
- Pod restart handling
- Node drain recovery
- Resource exhaustion
- Network partitions
- Database unavailability
- Database latency
- Service degradation
- Cascading failures
- Memory leak detection
- Container crash recovery
- Disk space exhaustion
- Recovery time objectives

**Configuration**: YAML-based Chaos Toolkit format

## Environment Variables

### Common

```bash
export BASE_URL=http://localhost:8080
export API_BASE_URL=http://localhost:8080/resourceService
export API_TIMEOUT=30
```

### Database

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=postgres
export DB_PASSWORD=yawl-secure-password
export DB_NAME=yawl
```

### Kubernetes

```bash
export K8S_NAMESPACE=yawl
```

### Google Cloud

```bash
export GCP_PROJECT_ID=your-project-id
export GCP_REGION=us-central1
```

### AWS

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=123456789
```

### Azure

```bash
export AZURE_RESOURCE_GROUP=yawl-rg
export AZURE_SUBSCRIPTION_ID=your-subscription-id
```

## Configuration Files

### integration-tests/config.yaml

```yaml
base_url: http://localhost:8080
api_base_url: http://localhost:8080/resourceService
timeout: 30
max_response_time: 2.0

database:
  host: localhost
  port: 5432
  user: postgres
  password: yawl-secure-password
  database: yawl

docker:
  compose_file: /home/user/yawl/docker-compose.yml

kubernetes:
  namespace: yawl
```

### chaos-engineering-tests/settings.yaml

```yaml
transports:
  http:
    timeout: 30
    max_retries: 3

kubernetes:
  context: "minikube"
  namespace: "yawl"

logging:
  level: INFO
  format: json
```

## Running Tests in CI/CD

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
          POSTGRES_PASSWORD: password
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-python@v2
      - run: pip install -r testing/requirements-test.txt
      - run: python testing/infrastructure-tests.py
      - run: pytest testing/integration-tests/ -v --cov
```

### GitLab CI

```yaml
test:
  image: python:3.10
  services:
    - postgres:14-alpine
  before_script:
    - pip install -r testing/requirements-test.txt
  script:
    - python testing/infrastructure-tests.py
    - pytest testing/integration-tests/ -v
```

### Jenkins

```groovy
stage('Test') {
  steps {
    sh 'python testing/infrastructure-tests.py'
    sh 'pytest testing/integration-tests/ -v --junit-xml=results.xml'
  }
  post {
    always {
      junit 'results.xml'
    }
  }
}
```

## Test Execution Flow

### Recommended Order

1. **Infrastructure Tests** (first)
   - Validate platform-specific infrastructure
   - Quick checks: 2-5 minutes

2. **Deployment Validation** (second)
   - Verify deployment correctness
   - Medium checks: 5-10 minutes

3. **Smoke Tests** (third)
   - Quick validation of critical paths
   - Fast checks: 2-5 minutes

4. **Integration Tests** (fourth)
   - End-to-end functionality
   - Moderate checks: 10-30 minutes

5. **Chaos Engineering** (fifth, optional)
   - Resilience under failure
   - Extended checks: 30+ minutes

## Troubleshooting

### Tests Can't Connect to Service

```bash
# Check if service is running
docker-compose ps

# Wait for service to be ready
sleep 10

# Check logs
docker-compose logs yawl
```

### Database Connection Fails

```bash
# Verify credentials
echo "Host: $DB_HOST, Port: $DB_PORT, User: $DB_USER"

# Test connection
psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "SELECT 1;"

# Check database service
docker-compose ps postgres
```

### Docker Tests Skipped

```bash
# Verify Docker is running
docker ps

# Check Docker socket
ls -la /var/run/docker.sock

# Test Docker connection
docker version
```

### Kubernetes Tests Skipped

```bash
# Check cluster connectivity
kubectl cluster-info

# Verify namespace
kubectl get namespace yawl

# Check deployment
kubectl get deployment yawl -n yawl
```

## Performance Baselines

### Expected Test Execution Times

- Infrastructure Tests: 2-5 minutes
- Deployment Validation: 5-10 minutes
- Smoke Tests: 2-5 minutes
- Integration Tests: 10-30 minutes
- Chaos Engineering: 30-60+ minutes

### Expected Performance Metrics

- Health endpoint response: < 100ms
- API endpoints: < 2s
- Database queries: < 1s
- Pod restart: < 30s
- Service recovery: < 2 minutes

## Test Coverage

- **Infrastructure**: 100% of deployment platforms
- **APIs**: ~80% of endpoints
- **Database**: Schema, integrity, performance
- **Resilience**: 10+ failure scenarios
- **Integration**: End-to-end workflows

## Extending Tests

### Add New API Test

```python
# testing/integration-tests/test_custom.py
import pytest

@pytest.mark.integration
class TestCustomAPI:
    def test_new_endpoint(self, api_client):
        response = api_client.get("/custom/endpoint")
        assert response.status_code == 200
```

### Add New Chaos Experiment

```yaml
# testing/chaos-engineering-tests/experiments.yaml
- name: my-experiment
  enabled: true
  description: "My custom experiment"
  steps:
    - type: action
      name: custom-action
      provider:
        type: shell
        command: "echo 'test'"
```

## Best Practices

1. **Run tests regularly**: Daily or with each deployment
2. **Monitor metrics**: Track test results over time
3. **Fix failures quickly**: Don't let failed tests pile up
4. **Document findings**: Record what each test validates
5. **Iterate**: Refine tests based on real failures
6. **Automate**: Integrate into CI/CD pipeline
7. **Baseline**: Establish normal behavior before testing chaos

## References

- [Pytest Documentation](https://docs.pytest.org/)
- [Chaos Toolkit](https://docs.chaostoolkit.org/)
- [Docker Compose](https://docs.docker.com/compose/)
- [Kubernetes Testing](https://kubernetes.io/docs/tasks/debug-application-cluster/)
- [YAWL Foundation](https://www.yawlfoundation.org/)

## Support

For issues or questions:

1. Check README files in specific test directories
2. Review test code comments
3. Check YAWL Foundation documentation
4. Contact YAWL support team

## License

YAWL is distributed under the GNU LGPL 3.0 License. See LICENSE.txt for details.
