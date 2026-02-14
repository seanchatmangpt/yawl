"""
YAWL Integration Tests - Pytest Configuration
Shared fixtures and configuration for integration tests
"""

import os
import sys
import pytest
import logging
import docker
import requests
from typing import Generator, Optional
import time
import yaml

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@pytest.fixture(scope="session")
def config():
    """Load test configuration"""
    config_file = os.path.join(os.path.dirname(__file__), "config.yaml")

    if os.path.exists(config_file):
        with open(config_file, 'r') as f:
            return yaml.safe_load(f) or {}

    return {
        "base_url": os.getenv("BASE_URL", "http://localhost:8080"),
        "api_base_url": os.getenv("API_BASE_URL", "http://localhost:8080/resourceService"),
        "timeout": int(os.getenv("API_TIMEOUT", "30")),
        "database": {
            "host": os.getenv("DB_HOST", "localhost"),
            "port": int(os.getenv("DB_PORT", "5432")),
            "user": os.getenv("DB_USER", "postgres"),
            "password": os.getenv("DB_PASSWORD", "yawl-secure-password"),
            "database": os.getenv("DB_NAME", "yawl")
        },
        "docker": {
            "compose_file": os.getenv("COMPOSE_FILE", "/home/user/yawl/docker-compose.yml")
        },
        "kubernetes": {
            "namespace": os.getenv("K8S_NAMESPACE", "yawl")
        }
    }


@pytest.fixture(scope="session")
def docker_client() -> Optional[docker.DockerClient]:
    """Get Docker client"""
    try:
        client = docker.from_env()
        client.ping()
        logger.info("Docker client connected")
        return client
    except Exception as e:
        logger.warning(f"Docker not available: {e}")
        return None


@pytest.fixture(scope="session")
def http_session():
    """Create requests session with default timeout"""
    session = requests.Session()
    session.timeout = 30
    yield session
    session.close()


@pytest.fixture
def api_client(config, http_session):
    """Create API client for testing"""
    class APIClient:
        def __init__(self, base_url: str, session: requests.Session, timeout: int = 30):
            self.base_url = base_url
            self.session = session
            self.timeout = timeout

        def get(self, endpoint: str, **kwargs):
            url = f"{self.base_url}{endpoint}"
            return self.session.get(url, timeout=self.timeout, **kwargs)

        def post(self, endpoint: str, **kwargs):
            url = f"{self.base_url}{endpoint}"
            return self.session.post(url, timeout=self.timeout, **kwargs)

        def put(self, endpoint: str, **kwargs):
            url = f"{self.base_url}{endpoint}"
            return self.session.put(url, timeout=self.timeout, **kwargs)

        def delete(self, endpoint: str, **kwargs):
            url = f"{self.base_url}{endpoint}"
            return self.session.delete(url, timeout=self.timeout, **kwargs)

    return APIClient(config["api_base_url"], http_session, config["timeout"])


@pytest.fixture
def db_connection(config):
    """Create database connection"""
    try:
        import psycopg2

        conn = psycopg2.connect(
            host=config["database"]["host"],
            port=config["database"]["port"],
            user=config["database"]["user"],
            password=config["database"]["password"],
            database=config["database"]["database"]
        )
        yield conn
        conn.close()
    except ImportError:
        logger.warning("psycopg2 not installed, skipping database tests")
        yield None
    except Exception as e:
        logger.error(f"Database connection failed: {e}")
        yield None


@pytest.fixture
def wait_for_service(config, http_session):
    """Wait for service to be ready"""
    def _wait(endpoint: str = "/resourceService/", max_attempts: int = 30, delay: int = 1):
        url = f"{config['base_url']}{endpoint}"

        for attempt in range(max_attempts):
            try:
                response = http_session.get(url, timeout=5)
                if response.status_code < 500:
                    logger.info(f"Service ready after {attempt} attempts")
                    return True
            except requests.exceptions.RequestException:
                pass

            if attempt < max_attempts - 1:
                time.sleep(delay)

        return False

    return _wait


@pytest.fixture(scope="session", autouse=True)
def setup_test_environment():
    """Setup test environment"""
    logger.info("Setting up test environment")

    # Create test output directory
    os.makedirs("/tmp/yawl-integration-tests", exist_ok=True)

    yield

    logger.info("Tearing down test environment")


def pytest_configure(config):
    """Configure pytest"""
    config.addinivalue_line("markers", "integration: Integration tests")
    config.addinivalue_line("markers", "slow: Slow running tests")
    config.addinivalue_line("markers", "requires_docker: Requires Docker")
    config.addinivalue_line("markers", "requires_k8s: Requires Kubernetes")
    config.addinivalue_line("markers", "requires_db: Requires database")
    config.addinivalue_line("markers", "smoke: Smoke tests")


def pytest_collection_modifyitems(config, items):
    """Modify test collection"""
    for item in items:
        # Auto-mark integration tests
        if "integration" in str(item.fspath):
            item.add_marker(pytest.mark.integration)


@pytest.fixture(scope="session")
def test_data():
    """Provide test data"""
    return {
        "workflow": {
            "name": "TestWorkflow",
            "description": "Test workflow for integration tests"
        },
        "task": {
            "name": "TestTask",
            "description": "Test task for integration tests"
        },
        "user": {
            "username": "testuser",
            "email": "test@example.com"
        }
    }
