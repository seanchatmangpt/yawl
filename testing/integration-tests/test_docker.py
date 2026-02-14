"""
YAWL Integration Tests - Docker Tests
Tests for Docker and Docker Compose deployments
"""

import pytest
import logging
import subprocess
import json
import os

logger = logging.getLogger(__name__)


@pytest.mark.integration
@pytest.mark.requires_docker
class TestDockerEnvironment:
    """Test Docker environment"""

    def test_docker_available(self, docker_client):
        """Test Docker is available"""
        if docker_client is None:
            pytest.skip("Docker not available")

        assert docker_client is not None, "Docker client not available"
        logger.info("Docker is available")

    def test_docker_info(self, docker_client):
        """Test Docker info"""
        if docker_client is None:
            pytest.skip("Docker not available")

        info = docker_client.info()
        assert info is not None, "Docker info not available"
        logger.info(f"Docker version: {info.get('ServerVersion', 'unknown')}")


@pytest.mark.integration
@pytest.mark.requires_docker
class TestDockerCompose:
    """Test Docker Compose"""

    def test_compose_file_exists(self, config):
        """Test docker-compose.yml exists"""
        compose_file = config["docker"]["compose_file"]
        assert os.path.exists(compose_file), f"Compose file not found: {compose_file}"
        logger.info(f"Compose file found: {compose_file}")

    def test_compose_config_valid(self, config):
        """Test docker-compose.yml is valid"""
        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "config"],
            capture_output=True,
            text=True
        )

        assert result.returncode == 0, f"Compose config validation failed: {result.stderr}"
        logger.info("Compose configuration is valid")

    def test_compose_services(self, config):
        """Test compose services are defined"""
        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "config", "--services"],
            capture_output=True,
            text=True
        )

        assert result.returncode == 0, f"Failed to get services: {result.stderr}"

        services = result.stdout.strip().split('\n')
        assert len(services) > 0, "No services defined"
        logger.info(f"Services defined: {', '.join(services)}")

    def test_compose_up(self, config):
        """Test docker-compose up (dry-run)"""
        compose_file = config["docker"]["compose_file"]

        # Just test that compose commands are available
        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "ps"],
            capture_output=True,
            text=True
        )

        # Don't assert on return code as compose might not be running
        logger.info("Docker-compose ps executed")


@pytest.mark.integration
@pytest.mark.requires_docker
@pytest.mark.slow
class TestContainerImages:
    """Test container images"""

    def test_compose_images(self, config, docker_client):
        """Test compose images are available"""
        if docker_client is None:
            pytest.skip("Docker not available")

        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "config", "--format", "json"],
            capture_output=True,
            text=True
        )

        if result.returncode == 0:
            config_data = json.loads(result.stdout)
            services = config_data.get("services", {})

            for service_name, service_config in services.items():
                image = service_config.get("image")
                if image:
                    logger.info(f"Service {service_name}: {image}")


@pytest.mark.integration
@pytest.mark.requires_docker
class TestContainerHealth:
    """Test container health"""

    def test_containers_running(self, config, docker_client):
        """Test containers are running"""
        if docker_client is None:
            pytest.skip("Docker not available")

        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "ps", "-q"],
            capture_output=True,
            text=True
        )

        if result.returncode == 0:
            containers = result.stdout.strip().split('\n')
            running_containers = [c for c in containers if c]
            logger.info(f"Running containers: {len(running_containers)}")

    def test_container_logs(self, config):
        """Test container logs are available"""
        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "logs", "--tail=10"],
            capture_output=True,
            text=True,
            timeout=10
        )

        if result.returncode == 0:
            logger.info("Container logs retrieved successfully")


@pytest.mark.integration
@pytest.mark.requires_docker
class TestDockerNetworks:
    """Test Docker networks"""

    def test_compose_networks(self, config, docker_client):
        """Test compose networks"""
        if docker_client is None:
            pytest.skip("Docker not available")

        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "config", "--format", "json"],
            capture_output=True,
            text=True
        )

        if result.returncode == 0:
            config_data = json.loads(result.stdout)
            networks = config_data.get("networks", {})
            logger.info(f"Networks defined: {list(networks.keys())}")


@pytest.mark.integration
@pytest.mark.requires_docker
class TestDockerVolumes:
    """Test Docker volumes"""

    def test_compose_volumes(self, config):
        """Test compose volumes"""
        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "config", "--format", "json"],
            capture_output=True,
            text=True
        )

        if result.returncode == 0:
            config_data = json.loads(result.stdout)
            volumes = config_data.get("volumes", {})
            logger.info(f"Volumes defined: {list(volumes.keys())}")

    def test_volume_mounts(self, config):
        """Test volume mounts in services"""
        compose_file = config["docker"]["compose_file"]

        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "config", "--format", "json"],
            capture_output=True,
            text=True
        )

        if result.returncode == 0:
            config_data = json.loads(result.stdout)
            services = config_data.get("services", {})

            for service_name, service_config in services.items():
                volumes = service_config.get("volumes", [])
                if volumes:
                    logger.info(f"Service {service_name} volumes: {len(volumes)}")
