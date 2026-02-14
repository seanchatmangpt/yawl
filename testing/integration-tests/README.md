# YAWL Integration Tests

Comprehensive integration tests for YAWL Workflow Engine across multiple deployment platforms.

## Overview

The integration test suite includes:

- **API Tests** (`test_api.py`): REST API endpoint validation
- **Database Tests** (`test_database.py`): PostgreSQL database connectivity and integrity
- **Docker Tests** (`test_docker.py`): Docker and Docker Compose validation
- **Kubernetes Tests** (in `test_k8s.py`): Kubernetes cluster validation
- **Configuration** (`conftest.py`): Pytest fixtures and shared configuration

## Prerequisites

```bash
# Core testing framework
pip install pytest pytest-cov pytest-timeout pytest-xdist

# API testing
pip install requests

# Database testing
pip install psycopg2-binary

# Docker testing
pip install docker

# Kubernetes testing
pip install kubernetes

# YAML support
pip install pyyaml

# Reporting
pip install pytest-html pytest-json-report
```

## Configuration

Create `config.yaml` in the integration-tests directory:

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

backup_dir: /var/lib/postgresql/backups
```

Or set environment variables:

```bash
export BASE_URL=http://localhost:8080
export API_BASE_URL=http://localhost:8080/resourceService
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=postgres
export DB_PASSWORD=yawl-secure-password
export DB_NAME=yawl
export K8S_NAMESPACE=yawl
```

## Running Tests

### All Tests

```bash
pytest -v
```

### Specific Test Class

```bash
pytest -v integration-tests/test_api.py::TestResourceService
```

### Specific Test

```bash
pytest -v integration-tests/test_api.py::TestResourceService::test_resource_service_health
```

### By Marker

```bash
# Smoke tests only
pytest -v -m smoke

# Integration tests
pytest -v -m integration

# Tests requiring Docker
pytest -v -m requires_docker

# Tests requiring database
pytest -v -m requires_db

# Tests requiring Kubernetes
pytest -v -m requires_k8s

# Skip slow tests
pytest -v -m "not slow"
```

### Parallel Execution

```bash
# Run tests in parallel with 4 workers
pytest -v -n 4
```

### With Coverage Report

```bash
pytest -v --cov=. --cov-report=html --cov-report=term
```

### With JUnit Report

```bash
pytest -v --junit-xml=results.xml
```

### With HTML Report

```bash
pytest -v --html=report.html --self-contained-html
```

## Test Organization

### API Tests

Tests for REST API endpoints including:
- Health checks
- Workflow operations
- Task operations
- Error handling
- Performance
- Content negotiation
- Authentication
- API versioning
- Rate limiting

### Database Tests

Tests for PostgreSQL database including:
- Connectivity
- Schema validation
- Data integrity
- Performance metrics
- Backup capabilities
- Connection settings

### Docker Tests

Tests for Docker Compose deployments including:
- Environment validation
- Compose configuration
- Service definitions
- Container health
- Networking
- Volume management

## Markers

Available pytest markers:

- `@pytest.mark.integration`: Integration tests
- `@pytest.mark.smoke`: Smoke tests (quick validation)
- `@pytest.mark.slow`: Slow running tests
- `@pytest.mark.requires_docker`: Requires Docker
- `@pytest.mark.requires_k8s`: Requires Kubernetes
- `@pytest.mark.requires_db`: Requires database

## Fixtures

### Configuration Fixture

```python
@pytest.fixture(scope="session")
def config():
    """Load test configuration"""
```

### API Client Fixture

```python
@pytest.fixture
def api_client(config, http_session):
    """Create API client for testing"""
```

### Database Connection Fixture

```python
@pytest.fixture
def db_connection(config):
    """Create database connection"""
```

### Docker Client Fixture

```python
@pytest.fixture(scope="session")
def docker_client():
    """Get Docker client"""
```

### Wait for Service Fixture

```python
@pytest.fixture
def wait_for_service(config, http_session):
    """Wait for service to be ready"""
```

## Examples

### Testing API Endpoint

```python
def test_workflow_list(api_client):
    response = api_client.get("/workflows")
    assert response.status_code == 200
    workflows = response.json()
    assert isinstance(workflows, list)
```

### Testing Database Query

```python
def test_database_table_count(db_connection):
    cursor = db_connection.cursor()
    cursor.execute("SELECT COUNT(*) FROM workflows;")
    result = cursor.fetchone()
    cursor.close()
    assert result[0] >= 0
```

### Testing with Retry Logic

```python
@pytest.fixture
def retry_service(config, http_session):
    def _retry(endpoint, max_attempts=3):
        for attempt in range(max_attempts):
            try:
                return http_session.get(
                    f"{config['base_url']}{endpoint}"
                )
            except Exception:
                if attempt == max_attempts - 1:
                    raise
    return _retry
```

## Continuous Integration

### GitHub Actions

```yaml
name: Integration Tests

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
        with:
          python-version: '3.10'
      - run: pip install -r requirements-test.txt
      - run: pytest -v --cov --junit-xml=results.xml
      - uses: actions/upload-artifact@v2
        if: always()
        with:
          name: test-results
          path: results.xml
```

## Troubleshooting

### Connection Refused

If you get "Connection refused" errors:

1. Check if the service is running:
   ```bash
   docker-compose ps
   ```

2. Wait for service to be ready:
   ```bash
   pytest -v integration-tests/test_api.py::TestResourceService::test_resource_service_health
   ```

### Database Connection Failed

If database tests fail:

1. Verify credentials in environment variables:
   ```bash
   echo $DB_HOST $DB_PORT $DB_USER $DB_NAME
   ```

2. Test connection manually:
   ```bash
   psql -h $DB_HOST -U $DB_USER -d $DB_NAME
   ```

### Docker Tests Skipped

If Docker tests are skipped:

1. Check Docker is running:
   ```bash
   docker ps
   ```

2. Check Docker Compose file:
   ```bash
   cat /home/user/yawl/docker-compose.yml
   ```

### Kubernetes Tests Skipped

If Kubernetes tests are skipped:

1. Check cluster connectivity:
   ```bash
   kubectl cluster-info
   ```

2. Check namespace:
   ```bash
   kubectl get namespace yawl
   ```

## Performance Benchmarks

Expected response times:
- Health endpoint: < 100ms
- API endpoints: < 2s
- Database queries: < 1s

## Extending Tests

To add new tests:

1. Create new test file in `integration-tests/`
2. Import fixtures from `conftest.py`
3. Add pytest markers
4. Use existing fixtures or create new ones

Example:

```python
import pytest

@pytest.mark.integration
class TestNewFeature:
    def test_something(self, api_client):
        response = api_client.get("/endpoint")
        assert response.status_code == 200
```

## References

- [Pytest Documentation](https://docs.pytest.org/)
- [Requests Documentation](https://requests.readthedocs.io/)
- [psycopg2 Documentation](https://www.psycopg.org/)
- [Docker SDK for Python](https://docker-py.readthedocs.io/)
- [Kubernetes Python Client](https://github.com/kubernetes-client/python)
