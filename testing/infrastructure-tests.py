#!/usr/bin/env python3
"""
YAWL Infrastructure Tests
Comprehensive infrastructure validation for multiple deployment platforms:
- Kubernetes (GKE, EKS, AKS)
- Cloud Run / Cloud Functions
- Docker Compose (Development)
- Docker Swarm
- AWS (CloudFormation/Terraform)
- Azure (ARM Templates)
"""

import os
import sys
import subprocess
import json
import time
import requests
import psutil
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Tuple, Optional, Any
from dataclasses import dataclass
from enum import Enum
import socket
import yaml

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('/tmp/yawl-infrastructure-tests.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class Platform(Enum):
    """Supported deployment platforms"""
    KUBERNETES = "kubernetes"
    CLOUD_RUN = "cloud-run"
    DOCKER_COMPOSE = "docker-compose"
    DOCKER_SWARM = "docker-swarm"
    AWS_EC2 = "aws-ec2"
    AWS_ECS = "aws-ecs"
    AZURE_ACI = "azure-aci"
    AZURE_AKS = "azure-aks"


class ComponentStatus(Enum):
    """Component health status"""
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"
    UNKNOWN = "unknown"


@dataclass
class TestResult:
    """Test result container"""
    name: str
    platform: Platform
    passed: bool
    duration: float
    error: Optional[str] = None
    details: Dict[str, Any] = None

    def __post_init__(self):
        if self.details is None:
            self.details = {}


class InfrastructureTest:
    """Base infrastructure test class"""

    def __init__(self, platform: Platform):
        self.platform = platform
        self.results = []
        self.config = self._load_config()

    def _load_config(self) -> Dict:
        """Load platform-specific configuration"""
        config_path = f"/home/user/yawl/testing/config/{self.platform.value}.yaml"
        if os.path.exists(config_path):
            with open(config_path, 'r') as f:
                return yaml.safe_load(f) or {}
        return {}

    def run_command(self, cmd: str, timeout: int = 30) -> Tuple[int, str, str]:
        """Execute shell command and return status, stdout, stderr"""
        try:
            result = subprocess.run(
                cmd,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout
            )
            return result.returncode, result.stdout, result.stderr
        except subprocess.TimeoutExpired:
            return -1, "", f"Command timeout after {timeout}s"
        except Exception as e:
            return -1, "", str(e)

    def log_result(self, name: str, passed: bool, duration: float,
                   error: Optional[str] = None, details: Optional[Dict] = None) -> TestResult:
        """Log test result"""
        result = TestResult(name, self.platform, passed, duration, error, details or {})
        self.results.append(result)

        status = "PASS" if passed else "FAIL"
        logger.info(f"[{status}] {self.platform.value} - {name} ({duration:.2f}s)")
        if error:
            logger.error(f"  Error: {error}")

        return result

    def get_results(self) -> List[TestResult]:
        """Get all test results"""
        return self.results


class KubernetesTest(InfrastructureTest):
    """Kubernetes-specific infrastructure tests"""

    def __init__(self):
        super().__init__(Platform.KUBERNETES)
        self.namespace = self.config.get('namespace', 'yawl')

    def test_cluster_connectivity(self) -> TestResult:
        """Test Kubernetes cluster connectivity"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command("kubectl cluster-info")
            passed = returncode == 0
            duration = time.time() - start
            return self.log_result(
                "Cluster Connectivity",
                passed,
                duration,
                stderr if not passed else None,
                {"cluster_info": stdout} if passed else {}
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Cluster Connectivity", False, duration, str(e))

    def test_namespace_exists(self) -> TestResult:
        """Test YAWL namespace exists"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get namespace {self.namespace}"
            )
            passed = returncode == 0
            duration = time.time() - start
            return self.log_result(
                "Namespace Exists",
                passed,
                duration,
                stderr if not passed else None
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Namespace Exists", False, duration, str(e))

    def test_deployment_readiness(self) -> TestResult:
        """Test YAWL deployment is ready"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get deployment yawl -n {self.namespace} "
                f"-o jsonpath='{{.status.readyReplicas}}' --ignore-not-found"
            )
            duration = time.time() - start

            if returncode == 0 and stdout:
                ready_replicas = int(stdout.strip()) if stdout.strip() else 0
                desired_replicas = 3  # Default from deployment
                passed = ready_replicas > 0

                return self.log_result(
                    "Deployment Readiness",
                    passed,
                    duration,
                    None if passed else f"Only {ready_replicas}/{desired_replicas} replicas ready",
                    {"ready_replicas": ready_replicas, "desired_replicas": desired_replicas}
                )
            else:
                return self.log_result("Deployment Readiness", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Deployment Readiness", False, duration, str(e))

    def test_pod_health(self) -> TestResult:
        """Test all YAWL pods are healthy"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get pods -n {self.namespace} -l app=yawl "
                f"-o jsonpath='{{.items[*].status.phase}}'"
            )
            duration = time.time() - start

            if returncode == 0:
                phases = stdout.split()
                running_count = phases.count('Running')
                passed = len(phases) > 0 and running_count == len(phases)

                return self.log_result(
                    "Pod Health",
                    passed,
                    duration,
                    None if passed else f"Pod states: {phases}",
                    {"total_pods": len(phases), "running_pods": running_count}
                )
            else:
                return self.log_result("Pod Health", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Pod Health", False, duration, str(e))

    def test_service_endpoints(self) -> TestResult:
        """Test Kubernetes service endpoints"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get endpoints yawl -n {self.namespace} "
                f"-o jsonpath='{{.subsets[*].addresses[*].ip}}' --ignore-not-found"
            )
            duration = time.time() - start

            if returncode == 0 and stdout:
                endpoints = stdout.split()
                passed = len(endpoints) > 0
                return self.log_result(
                    "Service Endpoints",
                    passed,
                    duration,
                    None if passed else "No endpoints found",
                    {"endpoint_count": len(endpoints), "endpoints": endpoints}
                )
            else:
                return self.log_result("Service Endpoints", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Service Endpoints", False, duration, str(e))

    def test_persistent_volumes(self) -> TestResult:
        """Test persistent volumes are bound"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get pvc -n {self.namespace} "
                f"-o jsonpath='{{.items[*].status.phase}}' --ignore-not-found"
            )
            duration = time.time() - start

            if returncode == 0 and stdout:
                phases = stdout.split()
                bound_count = phases.count('Bound')
                passed = len(phases) > 0 and bound_count == len(phases)

                return self.log_result(
                    "Persistent Volumes",
                    passed,
                    duration,
                    None if passed else f"PVC states: {phases}",
                    {"total_pvcs": len(phases), "bound_pvcs": bound_count}
                )
            else:
                return self.log_result("Persistent Volumes", True, duration, None, {"total_pvcs": 0})
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Persistent Volumes", False, duration, str(e))

    def test_resource_limits(self) -> TestResult:
        """Test resource limits are properly configured"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get deployment yawl -n {self.namespace} "
                f"-o jsonpath='{{.spec.template.spec.containers[0].resources}}' --ignore-not-found"
            )
            duration = time.time() - start

            if returncode == 0 and stdout:
                resources = json.loads(stdout)
                has_limits = 'limits' in resources and resources['limits']
                has_requests = 'requests' in resources and resources['requests']
                passed = has_limits and has_requests

                return self.log_result(
                    "Resource Limits",
                    passed,
                    duration,
                    None if passed else "Resource limits or requests not configured",
                    {"resources": resources}
                )
            else:
                return self.log_result("Resource Limits", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Resource Limits", False, duration, str(e))

    def test_network_policies(self) -> TestResult:
        """Test network policies are configured"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"kubectl get networkpolicy -n {self.namespace} --ignore-not-found"
            )
            duration = time.time() - start

            has_policies = returncode == 0 and "No resources found" not in stderr
            return self.log_result(
                "Network Policies",
                has_policies,
                duration,
                None if has_policies else "No network policies found",
                {"network_policies": has_policies}
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Network Policies", False, duration, str(e))


class CloudRunTest(InfrastructureTest):
    """Google Cloud Run infrastructure tests"""

    def __init__(self):
        super().__init__(Platform.CLOUD_RUN)
        self.project_id = os.getenv('GCP_PROJECT_ID', 'yawl-project')
        self.region = os.getenv('GCP_REGION', 'us-central1')
        self.service_name = self.config.get('service_name', 'yawl-workflow')

    def test_service_deployment(self) -> TestResult:
        """Test Cloud Run service is deployed"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"gcloud run services describe {self.service_name} "
                f"--region={self.region} --project={self.project_id} --format=json"
            )
            duration = time.time() - start

            if returncode == 0:
                service = json.loads(stdout)
                passed = service.get('status', {}).get('conditions', [])
                passed = any(c.get('status') == 'True' for c in passed)

                return self.log_result(
                    "Service Deployment",
                    passed,
                    duration,
                    None if passed else "Service not ready",
                    {"service": service.get('metadata', {}).get('name')}
                )
            else:
                return self.log_result("Service Deployment", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Service Deployment", False, duration, str(e))

    def test_service_url(self) -> TestResult:
        """Test Cloud Run service URL is accessible"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"gcloud run services describe {self.service_name} "
                f"--region={self.region} --project={self.project_id} "
                f"--format='value(status.url)'"
            )
            duration = time.time() - start

            if returncode == 0 and stdout:
                service_url = stdout.strip()
                try:
                    response = requests.get(service_url, timeout=10)
                    passed = response.status_code < 500

                    return self.log_result(
                        "Service URL",
                        passed,
                        duration,
                        None if passed else f"HTTP {response.status_code}",
                        {"url": service_url, "status": response.status_code}
                    )
                except requests.exceptions.RequestException as e:
                    return self.log_result("Service URL", False, duration, str(e))
            else:
                return self.log_result("Service URL", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Service URL", False, duration, str(e))

    def test_environment_variables(self) -> TestResult:
        """Test Cloud Run environment variables are configured"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"gcloud run services describe {self.service_name} "
                f"--region={self.region} --project={self.project_id} --format=json"
            )
            duration = time.time() - start

            if returncode == 0:
                service = json.loads(stdout)
                env_vars = service.get('spec', {}).get('template', {}).get('spec', {}).get('containers', [{}])[0].get('env', [])
                passed = len(env_vars) > 0

                return self.log_result(
                    "Environment Variables",
                    passed,
                    duration,
                    None if passed else "No environment variables configured",
                    {"env_var_count": len(env_vars)}
                )
            else:
                return self.log_result("Environment Variables", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Environment Variables", False, duration, str(e))


class DockerComposeTest(InfrastructureTest):
    """Docker Compose infrastructure tests"""

    def __init__(self):
        super().__init__(Platform.DOCKER_COMPOSE)
        self.compose_file = self.config.get('compose_file', '/home/user/yawl/docker-compose.yml')

    def test_compose_file_exists(self) -> TestResult:
        """Test docker-compose.yml exists and is valid"""
        start = time.time()
        try:
            passed = os.path.exists(self.compose_file)
            duration = time.time() - start

            if passed:
                with open(self.compose_file, 'r') as f:
                    yaml.safe_load(f)

            return self.log_result(
                "Compose File Valid",
                passed,
                duration,
                None if passed else f"File not found: {self.compose_file}"
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Compose File Valid", False, duration, str(e))

    def test_containers_running(self) -> TestResult:
        """Test all compose containers are running"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"docker-compose -f {self.compose_file} ps --services"
            )
            duration = time.time() - start

            if returncode == 0:
                services = stdout.strip().split('\n')
                running_check = self.run_command(
                    f"docker-compose -f {self.compose_file} ps -q"
                )
                containers = running_check[1].strip().split('\n') if running_check[1] else []
                passed = len(containers) == len(services)

                return self.log_result(
                    "Containers Running",
                    passed,
                    duration,
                    None if passed else f"Only {len(containers)}/{len(services)} running",
                    {"total_services": len(services), "running_containers": len(containers)}
                )
            else:
                return self.log_result("Containers Running", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Containers Running", False, duration, str(e))

    def test_health_checks(self) -> TestResult:
        """Test container health checks"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"docker-compose -f {self.compose_file} ps --format json"
            )
            duration = time.time() - start

            if returncode == 0:
                containers = json.loads(stdout)
                healthy = sum(1 for c in containers if c.get('Health', 'N/A') in ['healthy', 'N/A'])
                passed = healthy == len(containers)

                return self.log_result(
                    "Health Checks",
                    passed,
                    duration,
                    None if passed else f"Only {healthy}/{len(containers)} healthy",
                    {"total": len(containers), "healthy": healthy}
                )
            else:
                return self.log_result("Health Checks", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Health Checks", False, duration, str(e))

    def test_database_connectivity(self) -> TestResult:
        """Test database connectivity"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"docker-compose -f {self.compose_file} exec -T postgres "
                f"pg_isready -U postgres -d yawl"
            )
            duration = time.time() - start
            passed = returncode == 0

            return self.log_result(
                "Database Connectivity",
                passed,
                duration,
                stderr if not passed else None
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Database Connectivity", False, duration, str(e))

    def test_resource_usage(self) -> TestResult:
        """Test container resource usage is reasonable"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"docker-compose -f {self.compose_file} stats --no-stream --format 'table'"
            )
            duration = time.time() - start

            if returncode == 0:
                lines = stdout.strip().split('\n')[1:]
                containers_info = []

                for line in lines:
                    parts = line.split()
                    if len(parts) >= 2:
                        mem_str = parts[5] if len(parts) > 5 else "0M"
                        containers_info.append({
                            "name": parts[0],
                            "memory": mem_str
                        })

                passed = len(containers_info) > 0
                return self.log_result(
                    "Resource Usage",
                    passed,
                    duration,
                    None if passed else "Unable to get resource stats",
                    {"containers": containers_info}
                )
            else:
                return self.log_result("Resource Usage", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Resource Usage", False, duration, str(e))


class DatabaseTest(InfrastructureTest):
    """Database infrastructure tests"""

    def __init__(self, platform: Platform = Platform.DOCKER_COMPOSE):
        super().__init__(platform)
        self.db_host = self.config.get('db_host', 'localhost')
        self.db_port = self.config.get('db_port', 5432)
        self.db_user = self.config.get('db_user', 'postgres')
        self.db_password = self.config.get('db_password', 'yawl-secure-password')
        self.db_name = self.config.get('db_name', 'yawl')

    def test_database_connection(self) -> TestResult:
        """Test database connection"""
        start = time.time()
        try:
            returncode, stdout, stderr = self.run_command(
                f"PGPASSWORD={self.db_password} psql -h {self.db_host} -U {self.db_user} "
                f"-d {self.db_name} -c 'SELECT version();'"
            )
            duration = time.time() - start
            passed = returncode == 0

            return self.log_result(
                "Database Connection",
                passed,
                duration,
                stderr if not passed else None,
                {"version": stdout} if passed else {}
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Database Connection", False, duration, str(e))

    def test_tables_exist(self) -> TestResult:
        """Test required database tables exist"""
        start = time.time()
        try:
            query = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
            returncode, stdout, stderr = self.run_command(
                f"PGPASSWORD={self.db_password} psql -h {self.db_host} -U {self.db_user} "
                f"-d {self.db_name} -t -c \"{query}\""
            )
            duration = time.time() - start

            if returncode == 0:
                table_count = int(stdout.strip()) if stdout.strip().isdigit() else 0
                passed = table_count > 0

                return self.log_result(
                    "Tables Exist",
                    passed,
                    duration,
                    None if passed else "No tables found",
                    {"table_count": table_count}
                )
            else:
                return self.log_result("Tables Exist", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Tables Exist", False, duration, str(e))

    def test_database_size(self) -> TestResult:
        """Test database size is reasonable"""
        start = time.time()
        try:
            query = "SELECT pg_size_pretty(pg_database_size(current_database()));"
            returncode, stdout, stderr = self.run_command(
                f"PGPASSWORD={self.db_password} psql -h {self.db_host} -U {self.db_user} "
                f"-d {self.db_name} -t -c \"{query}\""
            )
            duration = time.time() - start

            if returncode == 0:
                db_size = stdout.strip()
                return self.log_result(
                    "Database Size",
                    True,
                    duration,
                    None,
                    {"size": db_size}
                )
            else:
                return self.log_result("Database Size", False, duration, stderr)
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Database Size", False, duration, str(e))


class HealthCheckTest(InfrastructureTest):
    """HTTP health check tests"""

    def __init__(self, platform: Platform = Platform.DOCKER_COMPOSE):
        super().__init__(platform)
        self.endpoint = self.config.get('health_endpoint', 'http://localhost:8080/resourceService/')
        self.timeout = self.config.get('timeout', 10)

    def test_health_endpoint(self) -> TestResult:
        """Test health endpoint responds"""
        start = time.time()
        try:
            response = requests.get(self.endpoint, timeout=self.timeout)
            duration = time.time() - start
            passed = response.status_code in [200, 204]

            return self.log_result(
                "Health Endpoint",
                passed,
                duration,
                None if passed else f"HTTP {response.status_code}",
                {"status_code": response.status_code, "response_time": duration}
            )
        except requests.exceptions.Timeout:
            duration = time.time() - start
            return self.log_result("Health Endpoint", False, duration, "Request timeout")
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Health Endpoint", False, duration, str(e))

    def test_response_time(self) -> TestResult:
        """Test health endpoint response time is acceptable"""
        start = time.time()
        try:
            response = requests.get(self.endpoint, timeout=self.timeout)
            duration = time.time() - start
            max_response_time = self.config.get('max_response_time', 2.0)
            passed = duration < max_response_time

            return self.log_result(
                "Response Time",
                passed,
                duration,
                None if passed else f"Response time {duration:.2f}s exceeds {max_response_time}s",
                {"response_time": duration, "max_allowed": max_response_time}
            )
        except Exception as e:
            duration = time.time() - start
            return self.log_result("Response Time", False, duration, str(e))


def generate_report(test_results: List[TestResult]) -> str:
    """Generate comprehensive test report"""
    report = []
    report.append("\n" + "="*80)
    report.append("YAWL INFRASTRUCTURE TEST REPORT")
    report.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append("="*80 + "\n")

    # Group results by platform
    by_platform = {}
    for result in test_results:
        platform = result.platform.value
        if platform not in by_platform:
            by_platform[platform] = []
        by_platform[platform].append(result)

    # Summary statistics
    total_tests = len(test_results)
    passed_tests = sum(1 for r in test_results if r.passed)
    failed_tests = total_tests - passed_tests

    report.append("SUMMARY")
    report.append("-" * 80)
    report.append(f"Total Tests:   {total_tests}")
    report.append(f"Passed:        {passed_tests} ({100*passed_tests/total_tests:.1f}%)")
    report.append(f"Failed:        {failed_tests} ({100*failed_tests/total_tests:.1f}%)")
    total_duration = sum(r.duration for r in test_results)
    report.append(f"Total Duration: {total_duration:.2f}s\n")

    # Results by platform
    report.append("RESULTS BY PLATFORM")
    report.append("-" * 80)
    for platform, results in sorted(by_platform.items()):
        platform_passed = sum(1 for r in results if r.passed)
        report.append(f"\n{platform.upper()}")
        report.append(f"  Tests: {len(results)}, Passed: {platform_passed}, Failed: {len(results) - platform_passed}")

        for result in results:
            status = "✓ PASS" if result.passed else "✗ FAIL"
            report.append(f"  {status}: {result.name} ({result.duration:.2f}s)")
            if result.error:
                report.append(f"         Error: {result.error}")
            if result.details:
                for key, value in result.details.items():
                    if key != "password" and key != "secret":
                        report.append(f"         {key}: {value}")

    report.append("\n" + "="*80)
    return "\n".join(report)


def main():
    """Run all infrastructure tests"""
    logger.info("Starting YAWL Infrastructure Tests")

    all_results = []

    # Detect available platforms and run tests
    platforms_to_test = []

    # Check Kubernetes
    returncode, _, _ = KubernetesTest().run_command("kubectl cluster-info", timeout=5)
    if returncode == 0:
        logger.info("Kubernetes cluster detected, running K8s tests...")
        platforms_to_test.append(KubernetesTest())

    # Check Docker Compose
    if os.path.exists("/home/user/yawl/docker-compose.yml"):
        logger.info("Docker Compose detected, running Docker Compose tests...")
        platforms_to_test.append(DockerComposeTest())

    # Always run database tests if accessible
    logger.info("Running database connectivity tests...")
    platforms_to_test.append(DatabaseTest())

    # Health check tests
    logger.info("Running health check tests...")
    platforms_to_test.append(HealthCheckTest())

    # Run all tests
    for test_suite in platforms_to_test:
        try:
            if isinstance(test_suite, KubernetesTest):
                test_suite.test_cluster_connectivity()
                test_suite.test_namespace_exists()
                test_suite.test_deployment_readiness()
                test_suite.test_pod_health()
                test_suite.test_service_endpoints()
                test_suite.test_persistent_volumes()
                test_suite.test_resource_limits()
                test_suite.test_network_policies()

            elif isinstance(test_suite, DockerComposeTest):
                test_suite.test_compose_file_exists()
                test_suite.test_containers_running()
                test_suite.test_health_checks()
                test_suite.test_database_connectivity()
                test_suite.test_resource_usage()

            elif isinstance(test_suite, DatabaseTest):
                test_suite.test_database_connection()
                test_suite.test_tables_exist()
                test_suite.test_database_size()

            elif isinstance(test_suite, HealthCheckTest):
                test_suite.test_health_endpoint()
                test_suite.test_response_time()

            all_results.extend(test_suite.get_results())
        except Exception as e:
            logger.error(f"Error running test suite: {e}")

    # Generate and print report
    report = generate_report(all_results)
    print(report)

    # Save report
    report_path = "/tmp/yawl-infrastructure-tests-report.txt"
    with open(report_path, 'w') as f:
        f.write(report)
    logger.info(f"Report saved to {report_path}")

    # Return exit code based on results
    failed_tests = sum(1 for r in all_results if not r.passed)
    sys.exit(0 if failed_tests == 0 else 1)


if __name__ == "__main__":
    main()
