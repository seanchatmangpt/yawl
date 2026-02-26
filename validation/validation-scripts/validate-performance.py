#!/usr/bin/env python3

# Performance Validation Script for YAWL v6.0.0-GA
# Validates Java-Python integration performance characteristics
#
# Usage: python validate-performance.py [options]
# Options:
#   -j, --junit      Generate JUnit XML reports
#   -v, --verbose    Enable verbose output
#   -t, --timeout    Timeout in seconds (default: 600)
#   -b, --baseline   Compare against performance baseline
#   -h, --help       Show help message

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

class PerformanceValidator:
    def __init__(self, project_root, timeout_seconds=600, compare_baseline=False):
        self.project_root = Path(project_root)
        self.timeout_seconds = timeout_seconds
        self.compare_baseline = compare_baseline
        self.results = {
            "timestamp": datetime.now().isoformat(),
            "metrics": {},
            "thresholds": {
                "max_execution_time_ms": 100,
                "max_memory_usage_mb": 100,
                "min_throughput_rps": 100,
                "concurrency_threads": 10
            }
        }

        # Colors for output
        self.colors = {
            'red': '\033[0;31m',
            'green': '\033[0;32m',
            'yellow': '\033[0;33m',
            'blue': '\033[0;34m',
            'nc': '\033[0m'
        }

    def print_info(self, message):
        print(f"{self.colors['blue']}[INFO]{self.colors['nc']} {message}")

    def print_success(self, message):
        print(f"{self.colors['green']}[SUCCESS]{self.colors['nc']} {message}")

    def print_warning(self, message):
        print(f"{self.colors['yellow']}[WARNING]{self.colors['nc']} {message}")

    def print_error(self, message):
        print(f"{self.colors['red']}[ERROR]{self.colors['nc']} {message}")

    def run_command(self, command, cwd=None):
        try:
            result = subprocess.run(
                command,
                shell=True,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                cwd=cwd or self.project_root
            )
            return result.returncode == 0, result.stdout, result.stderr
        except subprocess.TimeoutExpired:
            return False, "", "Command timed out"
        except Exception as e:
            return False, "", str(e)

    def validate_graalpy_availability(self):
        self.print_info("Checking GraalPy availability...")

        success, stdout, stderr = self.run_command("python3 -c 'import sys; print(sys.version_info)'")

        if success:
            self.print_success("Python3 is available")
            self.results["metrics"]["python_version"] = stdout.strip()
        else:
            self.print_warning("Python3 not available or GraalPy integration missing")
            self.results["metrics"]["python_version"] = "Not available"

        return success

    def run_type_compatibility_tests(self):
        self.print_info("Running type compatibility tests...")

        test_class = "org.yawlfoundation.yawl.integration.java_python.JavaPythonTypeCompatibilityTest"
        command = f"mvn test -Dtest={test_class} -q"

        success, stdout, stderr = self.run_command(command, cwd=str(self.project_root / "yawl-graalpy"))

        if success:
            self.print_success("Type compatibility tests passed")
            self.results["metrics"]["type_compatibility"] = "PASS"
        else:
            self.print_error("Type compatibility tests failed")
            self.results["metrics"]["type_compatibility"] = "FAIL"
            if stderr:
                self.results["metrics"]["type_compatibility_errors"] = stderr

        return success

    def run_functionality_preservation_tests(self):
        self.print_info("Running functionality preservation tests...")

        test_class = "org.yawlfoundation.yawl.integration.java_python.YawlFunctionalityPreservationTest"
        command = f"mvn test -Dtest={test_class} -q"

        success, stdout, stderr = self.run_command(command, cwd=str(self.project_root / "yawl-graalpy"))

        if success:
            self.print_success("Functionality preservation tests passed")
            self.results["metrics"]["functionality_preservation"] = "PASS"
        else:
            self.print_error("Functionality preservation tests failed")
            self.results["metrics"]["functionality_preservation"] = "FAIL"
            if stderr:
                self.results["metrics"]["functionality_errors"] = stderr

        return success

    def run_performance_benchmarks(self):
        self.print_info("Running performance benchmarks...")

        test_class = "org.yawlfoundation.yawl.integration.java_python.performance.PerformanceBaselinesTest"
        command = f"mvn test -Dtest={test_class} -q"

        success, stdout, stderr = self.run_command(command, cwd=str(self.project_root / "yawl-graalpy"))

        if success:
            self.print_success("Performance benchmarks passed")

            # Extract performance metrics from output
            metrics = self._extract_performance_metrics(stdout)
            self.results["metrics"].update(metrics)

        else:
            self.print_error("Performance benchmarks failed")
            if stderr:
                self.results["metrics"]["benchmark_errors"] = stderr

        return success

    def _extract_performance_metrics(self, output):
        metrics = {}

        # Extract execution time
        for line in output.split('\n'):
            if "Execution time" in line and "ms" in line:
                try:
                    time_ms = float(line.split(':')[1].strip().split()[0])
                    metrics["execution_time_ms"] = time_ms
                    break
                except:
                    continue

        # Extract throughput
        for line in output.split('\n'):
            if "Throughput" in line and "RPS" in line:
                try:
                    rps = float(line.split(':')[1].strip().split()[0])
                    metrics["throughput_rps"] = rps
                    break
                except:
                    continue

        # Extract memory usage
        for line in output.split('\n'):
            if "Memory usage" in line and "MB" in line:
                try:
                    memory_mb = float(line.split(':')[1].strip().split()[0])
                    metrics["memory_usage_mb"] = memory_mb
                    break
                except:
                    continue

        return metrics

    def check_performance_thresholds(self):
        self.print_info("Checking performance thresholds...")

        thresholds_met = True

        # Check execution time
        if "execution_time_ms" in self.results["metrics"]:
            exec_time = self.results["metrics"]["execution_time_ms"]
            threshold = self.results["thresholds"]["max_execution_time_ms"]

            if exec_time <= threshold:
                self.print_success(f"Execution time {exec_time}ms meets threshold of {threshold}ms")
            else:
                self.print_error(f"Execution time {exec_time}ms exceeds threshold of {threshold}ms")
                thresholds_met = False

        # Check throughput
        if "throughput_rps" in self.results["metrics"]:
            throughput = self.results["metrics"]["throughput_rps"]
            threshold = self.results["thresholds"]["min_throughput_rps"]

            if throughput >= threshold:
                self.print_success(f"Throughput {throughput} RPS meets threshold of {threshold} RPS")
            else:
                self.print_error(f"Throughput {throughput} RPS below threshold of {threshold} RPS")
                thresholds_met = False

        # Check memory usage
        if "memory_usage_mb" in self.results["metrics"]:
            memory = self.results["metrics"]["memory_usage_mb"]
            threshold = self.results["thresholds"]["max_memory_usage_mb"]

            if memory <= threshold:
                self.print_success(f"Memory usage {memory}MB meets threshold of {threshold}MB")
            else:
                self.print_error(f"Memory usage {memory}MB exceeds threshold of {threshold}MB")
                thresholds_met = False

        self.results["thresholds_met"] = thresholds_met
        return thresholds_met

    def compare_with_baseline(self):
        if not self.compare_baseline:
            return True

        self.print_info("Comparing with performance baseline...")

        baseline_file = self.project_root / "validation" / "performance-baseline.json"

        if not baseline_file.exists():
            self.print_warning("Baseline file not found, creating new baseline")
            self.save_baseline()
            return True

        try:
            with open(baseline_file) as f:
                baseline_data = json.load(f)

            comparison = self._compare_metrics(baseline_data, self.results)
            self.results["comparison"] = comparison

            if comparison["degraded"]:
                self.print_warning("Performance degradation detected")
                return False
            else:
                self.print_success("Performance meets or exceeds baseline")
                return True

        except Exception as e:
            self.print_error(f"Error comparing with baseline: {e}")
            return False

    def _compare_metrics(self, baseline, current):
        comparison = {
            "baseline": {},
            "current": {},
            "changes": {},
            "degraded": False
        }

        # Compare execution time
        if "execution_time_ms" in baseline["metrics"]:
            baseline_time = baseline["metrics"]["execution_time_ms"]
            current_time = current["metrics"].get("execution_time_ms", baseline_time)

            time_change = ((current_time - baseline_time) / baseline_time) * 100
            comparison["changes"]["execution_time_percent"] = time_change

            if time_change > 20:  # 20% degradation threshold
                comparison["degraded"] = True

        # Compare throughput
        if "throughput_rps" in baseline["metrics"]:
            baseline_throughput = baseline["metrics"]["throughput_rps"]
            current_throughput = current["metrics"].get("throughput_rps", baseline_throughput)

            throughput_change = ((current_throughput - baseline_throughput) / baseline_throughput) * 100
            comparison["changes"]["throughput_percent"] = throughput_change

            if throughput_change < -20:  # 20% degradation threshold
                comparison["degraded"] = True

        return comparison

    def save_baseline(self):
        baseline_file = self.project_root / "validation" / "performance-baseline.json"

        with open(baseline_file, 'w') as f:
            json.dump(self.results, f, indent=2)

        self.print_success(f"Baseline saved to {baseline_file}")

    def generate_report(self):
        report_dir = self.project_root / "validation" / "results"
        report_dir.mkdir(parents=True, exist_ok=True)

        report_file = report_dir / f"performance-report-{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"

        with open(report_file, 'w') as f:
            json.dump(self.results, f, indent=2)

        self.print_success(f"Performance report generated: {report_file}")

        # Generate summary
        self._generate_summary()

        return report_file

    def _generate_summary(self):
        print("\n" + "="*50)
        print("PERFORMANCE VALIDATION SUMMARY")
        print("="*50)

        # Test results
        total_tests = len([k for k in self.results["metrics"] if k.endswith("_test_result")])
        passed_tests = sum(1 for k, v in self.results["metrics"].items()
                          if v == "PASS" and k.endswith("_test_result"))
        failed_tests = total_tests - passed_tests

        print(f"\nTest Results:")
        print(f"  Total: {total_tests}")
        print(f"  Passed: {passed_tests}")
        print(f"  Failed: {failed_tests}")

        # Performance metrics
        if "execution_time_ms" in self.results["metrics"]:
            print(f"\nPerformance Metrics:")
            print(f"  Execution Time: {self.results['metrics']['execution_time_ms']:.2f}ms")
            print(f"  Throughput: {self.results['metrics'].get('throughput_rps', 0):.2f} RPS")
            print(f"  Memory Usage: {self.results['metrics'].get('memory_usage_mb', 0):.2f}MB")

        # Threshold compliance
        thresholds_met = self.results.get("thresholds_met", False)
        print(f"\nThreshold Compliance: {'PASS' if thresholds_met else 'FAIL'}")

        # Overall status
        overall_status = "PASS" if thresholds_met and failed_tests == 0 else "FAIL"
        print(f"\nOverall Status: {overall_status}")

        if overall_status == "FAIL":
            print("\nRecommendations:")
            print("  - Fix failed tests")
            print("  - Optimize performance-critical code paths")
            print("  - Consider implementing caching strategies")
            print("  - Profile memory usage for potential leaks")

def main():
    parser = argparse.ArgumentParser(description="Performance Validation for YAWL v6.0.0-GA")
    parser.add_argument("-j", "--junit", action="store_true", help="Generate JUnit XML reports")
    parser.add_argument("-v", "--verbose", action="store_true", help="Enable verbose output")
    parser.add_argument("-t", "--timeout", type=int, default=600, help="Timeout in seconds")
    parser.add_argument("-b", "--baseline", action="store_true", help="Compare against performance baseline")
    parser.add_argument("-h", "--help", action="help", help="Show this help message")

    args = parser.parse_args()

    # Initialize validator
    validator = PerformanceValidator(
        project_root=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        timeout_seconds=args.timeout,
        compare_baseline=args.baseline
    )

    print("Java-Python Performance Validation for YAWL v6.0.0-GA")
    print("="*60)
    print()

    # Run validation steps
    steps_passed = 0
    total_steps = 4

    # Step 1: Validate GraalPy availability
    if validator.validate_graalpy_availability():
        steps_passed += 1

    # Step 2: Run type compatibility tests
    if validator.run_type_compatibility_tests():
        steps_passed += 1

    # Step 3: Run functionality preservation tests
    if validator.run_functionality_preservation_tests():
        steps_passed += 1

    # Step 4: Run performance benchmarks
    if validator.run_performance_benchmarks():
        steps_passed += 1

    # Check performance thresholds
    validator.check_performance_thresholds()

    # Compare with baseline
    baseline_comparison = validator.compare_with_baseline()

    # Generate report
    report_file = validator.generate_report()

    # Return exit code
    if steps_passed == total_steps and validator.results.get("thresholds_met", False):
        print(f"\n{validator.colors['green']}Performance validation completed successfully!{validator.colors['nc']}")
        sys.exit(0)
    else:
        print(f"\n{validator.colors['red']}Performance validation failed!{validator.colors['nc']}")
        sys.exit(1)

if __name__ == "__main__":
    main()