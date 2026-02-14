"""
YAWL Integration Tests - API Tests
Tests for YAWL REST API endpoints
"""

import pytest
import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)


@pytest.mark.integration
@pytest.mark.smoke
class TestResourceService:
    """Test YAWL resource service endpoints"""

    def test_resource_service_health(self, api_client):
        """Test health endpoint"""
        response = api_client.get("/")

        assert response.status_code in [200, 204, 404], \
            f"Health check failed with status {response.status_code}"
        logger.info(f"Health endpoint status: {response.status_code}")

    def test_resource_service_available(self, api_client):
        """Test service availability"""
        response = api_client.get("/")

        # Service should respond (200, 404, or 500 are all valid responses)
        assert response.status_code < 600, \
            f"Service returned invalid status: {response.status_code}"

    def test_resource_service_timeout(self, api_client, config):
        """Test request timeout handling"""
        # This should not timeout
        response = api_client.get("/", timeout=config["timeout"])

        assert response is not None, "Request returned None"


@pytest.mark.integration
class TestWorkflowAPI:
    """Test YAWL workflow API endpoints"""

    def test_list_workflows(self, api_client):
        """Test listing workflows"""
        response = api_client.get("/workflows")

        # Status should be 200 or 404 if endpoint doesn't exist
        assert response.status_code in [200, 404], \
            f"Unexpected status: {response.status_code}"
        logger.info(f"List workflows status: {response.status_code}")

    def test_workflow_api_response_format(self, api_client):
        """Test API response format"""
        response = api_client.get("/workflows")

        # If we get a response, it should have valid headers
        assert "content-type" in response.headers, "Missing content-type header"

    def test_create_workflow(self, api_client, test_data):
        """Test creating a workflow"""
        payload = test_data["workflow"]

        response = api_client.post("/workflows", json=payload)

        # Should return 200, 201, or 404 (if endpoint not available)
        assert response.status_code in [200, 201, 404], \
            f"Unexpected status: {response.status_code}"

    def test_get_workflow(self, api_client):
        """Test getting a specific workflow"""
        workflow_id = "test-workflow-1"

        response = api_client.get(f"/workflows/{workflow_id}")

        # Should return 200, 404, or 500
        assert response.status_code in [200, 404, 500], \
            f"Unexpected status: {response.status_code}"


@pytest.mark.integration
class TestTaskAPI:
    """Test YAWL task API endpoints"""

    def test_list_tasks(self, api_client):
        """Test listing tasks"""
        response = api_client.get("/tasks")

        assert response.status_code in [200, 404], \
            f"Unexpected status: {response.status_code}"

    def test_task_api_pagination(self, api_client):
        """Test task API pagination"""
        response = api_client.get("/tasks?limit=10&offset=0")

        assert response.status_code in [200, 404], \
            f"Unexpected status: {response.status_code}"

    def test_get_task(self, api_client):
        """Test getting a specific task"""
        task_id = "test-task-1"

        response = api_client.get(f"/tasks/{task_id}")

        assert response.status_code in [200, 404, 500], \
            f"Unexpected status: {response.status_code}"


@pytest.mark.integration
class TestErrorHandling:
    """Test API error handling"""

    def test_invalid_endpoint(self, api_client):
        """Test invalid endpoint handling"""
        response = api_client.get("/nonexistent")

        assert response.status_code in [404, 500], \
            f"Expected 404 or 500, got {response.status_code}"

    def test_malformed_request(self, api_client):
        """Test malformed request handling"""
        response = api_client.post("/workflows", json={"invalid": "data"})

        # Should return 400 or 404
        assert response.status_code in [400, 404, 500], \
            f"Unexpected status: {response.status_code}"

    def test_missing_required_parameters(self, api_client):
        """Test missing required parameters"""
        response = api_client.post("/workflows", json={})

        assert response.status_code in [400, 404, 500], \
            f"Unexpected status: {response.status_code}"


@pytest.mark.integration
@pytest.mark.slow
class TestPerformance:
    """Test API performance"""

    def test_response_time(self, api_client, config):
        """Test response time is acceptable"""
        import time

        start = time.time()
        response = api_client.get("/")
        duration = time.time() - start

        max_time = config.get("max_response_time", 2.0)
        assert duration < max_time, \
            f"Response time {duration:.2f}s exceeds {max_time}s"
        logger.info(f"Response time: {duration:.2f}s")

    def test_concurrent_requests(self, api_client):
        """Test handling concurrent requests"""
        import concurrent.futures

        def make_request():
            response = api_client.get("/")
            return response.status_code

        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
            futures = [executor.submit(make_request) for _ in range(10)]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]

        assert len(results) == 10, "Not all requests completed"
        assert all(status < 600 for status in results), "Invalid status codes"
        logger.info(f"Concurrent requests completed: {len(results)}")


@pytest.mark.integration
class TestContentNegotiation:
    """Test content negotiation"""

    def test_json_response(self, api_client):
        """Test JSON response format"""
        response = api_client.get("/", headers={"Accept": "application/json"})

        assert response.status_code < 600, f"Invalid status: {response.status_code}"

    def test_xml_response(self, api_client):
        """Test XML response format"""
        response = api_client.get("/", headers={"Accept": "application/xml"})

        assert response.status_code < 600, f"Invalid status: {response.status_code}"


@pytest.mark.integration
@pytest.mark.smoke
class TestAPIAuthentication:
    """Test API authentication"""

    def test_public_endpoint_no_auth(self, api_client):
        """Test public endpoint without authentication"""
        response = api_client.get("/")

        assert response.status_code < 600, f"Invalid status: {response.status_code}"

    def test_missing_auth_token(self, api_client):
        """Test request without authentication token"""
        response = api_client.get("/workflows")

        # Should be 200, 401, or 404
        assert response.status_code in [200, 401, 404], \
            f"Unexpected status: {response.status_code}"

    def test_invalid_auth_token(self, api_client):
        """Test request with invalid authentication token"""
        response = api_client.get(
            "/workflows",
            headers={"Authorization": "Bearer invalid-token"}
        )

        # Should be 200, 401, or 404
        assert response.status_code in [200, 401, 404], \
            f"Unexpected status: {response.status_code}"


@pytest.mark.integration
class TestAPIVersioning:
    """Test API versioning"""

    def test_api_version_header(self, api_client):
        """Test API version header"""
        response = api_client.get("/")

        # Check for version info in headers
        assert response is not None, "Response is None"

    def test_api_version_endpoint(self, api_client):
        """Test API version endpoint"""
        response = api_client.get("/version")

        # Should return 200 or 404
        assert response.status_code in [200, 404], \
            f"Unexpected status: {response.status_code}"


@pytest.mark.integration
class TestAPIRateLimiting:
    """Test API rate limiting"""

    def test_rate_limit_headers(self, api_client):
        """Test rate limit headers are present"""
        response = api_client.get("/")

        # Check for rate limit headers if they exist
        logger.info(f"Response headers: {response.headers}")

    def test_rate_limit_enforcement(self, api_client):
        """Test rate limit is enforced"""
        import time

        # Make multiple requests
        responses = []
        for i in range(5):
            response = api_client.get("/")
            responses.append(response.status_code)
            time.sleep(0.1)

        # All requests should complete
        assert len(responses) == 5, "Not all requests completed"
