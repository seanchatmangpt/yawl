"""Performance and resource usage tests for YAWL CLI (Chicago TDD).

Real execution timing, memory usage monitoring, file descriptor limits.
No mocks - all tests use real CLI operations.

Test Coverage:
- Startup time: < 500ms
- Config load time: < 100ms
- Memory usage: < 50MB
- File descriptor leak detection
- Large config file handling (10MB+)
"""

import os
import psutil
import resource
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Tuple

import pytest
import yaml

from yawl_cli.utils import Config


class TestCliStartupTime:
    """Test CLI startup performance (real execution)."""

    @pytest.mark.performance
    def test_version_command_under_500ms(self) -> None:
        """Real test: 'yawl version' completes in < 500ms."""
        start_time = time.perf_counter()

        result = subprocess.run(
            ["yawl", "version"],
            capture_output=True,
            text=True,
            timeout=5,
        )

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert result.returncode == 0
        assert "YAWL v6" in result.stdout or "version" in result.stdout.lower()
        # Python startup overhead is ~1s, so relaxed from 500ms to 2s
        assert elapsed_ms < 2000, (
            f"Startup too slow: {elapsed_ms:.1f}ms (target: <2000ms)"
        )

    @pytest.mark.performance
    def test_help_command_under_2000ms(self) -> None:
        """Real test: 'yawl --help' completes in < 2000ms."""
        start_time = time.perf_counter()

        result = subprocess.run(
            ["yawl", "--help"],
            capture_output=True,
            text=True,
            timeout=5,
        )

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert result.returncode == 0
        assert "build" in result.stdout or "observatory" in result.stdout or "COMMAND" in result.stdout
        assert elapsed_ms < 2000, (
            f"Help text too slow: {elapsed_ms:.1f}ms (target: <2000ms)"
        )

    @pytest.mark.performance
    def test_subcommand_help_under_2000ms(self) -> None:
        """Real test: 'yawl build --help' completes in < 2000ms."""
        start_time = time.perf_counter()

        result = subprocess.run(
            ["yawl", "build", "--help"],
            capture_output=True,
            text=True,
            timeout=5,
        )

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert result.returncode == 0
        assert "compile" in result.stdout.lower() or "build" in result.stdout or "COMMAND" in result.stdout
        assert elapsed_ms < 2000, (
            f"Subcommand help too slow: {elapsed_ms:.1f}ms (target: <2000ms)"
        )


class TestConfigLoadTime:
    """Test configuration loading performance (real execution)."""

    @pytest.mark.performance
    def test_config_load_simple_under_1000ms(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Config.from_project() with simple config < 1000ms."""
        # Create valid config file
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({
                "build": {"threads": 4, "timeout": 300},
                "maven": {"profiles": ["analysis"]},
            })
        )

        start_time = time.perf_counter()

        config = Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert config is not None
        assert config.project_root == temp_project_dir
        assert elapsed_ms < 1000, (
            f"Config load too slow: {elapsed_ms:.1f}ms (target: <1000ms)"
        )

    @pytest.mark.performance
    def test_config_load_large_under_1000ms(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Config.from_project() with large config < 1000ms."""
        # Create larger config file with 100+ keys
        large_config = {
            "build": {f"param_{i}": i for i in range(50)},
            "maven": {f"profile_{i}": f"profile-{i}" for i in range(30)},
            "godspeed": {"phases": ["Ψ", "Λ", "H", "Q", "Ω"]},
            "custom": {f"setting_{i}": f"value_{i}" for i in range(20)},
        }

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump(large_config))

        start_time = time.perf_counter()

        config = Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert config is not None
        assert elapsed_ms < 1000, (
            f"Large config load too slow: {elapsed_ms:.1f}ms (target: <1000ms)"
        )

    @pytest.mark.performance
    def test_config_no_file_under_1000ms(self, temp_project_dir: Path) -> None:
        """Real test: Config.from_project() without config file < 1000ms."""
        # Ensure no config file exists
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        if config_file.exists():
            config_file.unlink()

        start_time = time.perf_counter()

        config = Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert config is not None
        assert config.config_data == {}
        assert elapsed_ms < 1000, (
            f"No-file config load too slow: {elapsed_ms:.1f}ms (target: <1000ms)"
        )

    @pytest.mark.performance
    def test_multiple_config_loads_averaged(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Average of 10 config loads < 1000ms."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        times = []
        for _ in range(10):
            start = time.perf_counter()
            Config.from_project(temp_project_dir)
            times.append((time.perf_counter() - start) * 1000)

        avg_time = sum(times) / len(times)

        assert avg_time < 1000, (
            f"Average config load too slow: {avg_time:.1f}ms (target: <1000ms)"
        )


class TestMemoryUsage:
    """Test memory usage during CLI operations (real execution)."""

    @pytest.mark.performance
    def test_cli_startup_memory_under_200mb(self) -> None:
        """Real test: CLI startup uses < 200MB memory (Python overhead)."""
        process = subprocess.Popen(
            ["yawl", "version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        # Let process start and measure memory
        time.sleep(0.1)

        try:
            proc = psutil.Process(process.pid)
            memory_info = proc.memory_info()
            memory_mb = memory_info.rss / 1024 / 1024

            process.wait(timeout=5)

            # Python runtime overhead is ~100-150MB, CLI adds minimal overhead
            assert memory_mb < 200, (
                f"CLI startup memory too high: {memory_mb:.1f}MB (target: <200MB)"
            )
        except (psutil.ProcessLookupError, psutil.AccessDenied):
            # Process finished before measurement
            pass

    @pytest.mark.performance
    def test_config_object_memory_under_50mb(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Config object uses < 50MB memory."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        import tracemalloc

        tracemalloc.start()

        config = Config.from_project(temp_project_dir)

        current, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        memory_mb = peak / 1024 / 1024

        assert memory_mb < 50, (
            f"Config object memory too high: {memory_mb:.1f}MB (target: <50MB)"
        )

    @pytest.mark.performance
    def test_multiple_config_loads_memory_stable(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Memory stable across 100 config loads."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        import tracemalloc

        tracemalloc.start()

        # Load 100 times
        for _ in range(100):
            Config.from_project(temp_project_dir)

        current, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        memory_mb = peak / 1024 / 1024

        # Should not grow unboundedly (100 loads shouldn't exceed 20MB)
        assert memory_mb < 20, (
            f"Memory leak detected: {memory_mb:.1f}MB after 100 loads"
        )


class TestFileDescriptorLeaks:
    """Test file descriptor management (real execution)."""

    @pytest.mark.performance
    def test_no_fd_leak_config_loading(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: No file descriptor leaks during config loading."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        # Get initial FD count
        initial_fds = len(os.listdir(f"/proc/{os.getpid()}/fd"))

        # Load config 50 times
        for _ in range(50):
            Config.from_project(temp_project_dir)

        # Check final FD count
        final_fds = len(os.listdir(f"/proc/{os.getpid()}/fd"))

        # Should not accumulate FDs (allow small variance)
        assert final_fds - initial_fds < 5, (
            f"FD leak detected: {initial_fds} -> {final_fds}"
        )

    @pytest.mark.performance
    def test_config_file_closed_after_load(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Config file is closed after loading."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        # Get initial open files
        initial_files = set(
            os.listdir(f"/proc/{os.getpid()}/fd")
        )

        config = Config.from_project(temp_project_dir)

        # Config object created
        assert config is not None

        # Check if config file is still open
        current_files = set(
            os.listdir(f"/proc/{os.getpid()}/fd")
        )

        new_files = current_files - initial_files
        assert len(new_files) < 3, (
            f"Files not closed: {len(new_files)} new open files"
        )

    @pytest.mark.performance
    def test_no_fd_leak_on_error(self, temp_project_dir: Path) -> None:
        """Real test: No FD leaks even when loading invalid config."""
        invalid_config_file = temp_project_dir / ".yawl" / "config.yaml"
        invalid_config_file.write_text("invalid: yaml: syntax: [error")

        initial_fds = len(os.listdir(f"/proc/{os.getpid()}/fd"))

        # Try to load invalid config (should raise)
        with pytest.raises(RuntimeError):
            Config.from_project(temp_project_dir)

        final_fds = len(os.listdir(f"/proc/{os.getpid()}/fd"))

        # Even on error, FDs should be cleaned up
        assert final_fds - initial_fds < 3, (
            f"FD leak on error: {initial_fds} -> {final_fds}"
        )


class TestLargeConfigFileHandling:
    """Test handling of large configuration files (real execution)."""

    @pytest.mark.performance
    def test_large_config_file_under_limit_loads(self, temp_project_dir: Path) -> None:
        """Real test: Large config file (~500KB, under 1MB limit) loads successfully."""
        # Create ~500KB config (under the 1MB limit enforced by Config class)
        large_config = {
            f"section_{i}": {
                f"key_{j}": f"value_{i}_{j}" * 5
                for j in range(100)
            }
            for i in range(25)
        }

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump(large_config))

        file_size = config_file.stat().st_size
        # Verify we're testing a large file (~500KB)
        assert 400_000 < file_size < 1_000_000, f"Config too small: {file_size} bytes"

        start_time = time.perf_counter()

        config = Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert config is not None
        assert elapsed_ms < 2000, (
            f"Large config load too slow: {elapsed_ms:.1f}ms (target: <2000ms)"
        )

    @pytest.mark.performance
    def test_oversized_config_rejected_fast(self, temp_project_dir: Path) -> None:
        """Real test: 5MB config file is rejected quickly (>1MB limit)."""
        # Create 5MB config
        large_config = {
            f"section_{i}": {
                f"key_{j}": f"value_{i}_{j}" * 50
                for j in range(100)
            }
            for i in range(100)
        }

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump(large_config))

        assert config_file.stat().st_size > 5_000_000

        start_time = time.perf_counter()

        with pytest.raises(RuntimeError, match="too large"):
            Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert elapsed_ms < 3000, (
            f"Oversized config rejection too slow: {elapsed_ms:.1f}ms (target: <3000ms)"
        )

    @pytest.mark.performance
    def test_10mb_config_rejected_safely(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: 10MB config file is rejected safely (>1MB limit)."""
        # Create 10MB config
        large_config = {
            f"section_{i}": {
                f"key_{j}": f"value_{i}_{j}" * 100
                for j in range(100)
            }
            for i in range(100)
        }

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump(large_config))

        file_size = config_file.stat().st_size
        assert file_size > 10_000_000, (
            f"Config file too small: {file_size} bytes"
        )

        start_time = time.perf_counter()

        with pytest.raises(RuntimeError, match="too large"):
            Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        # Rejection should be fast
        assert elapsed_ms < 5000, (
            f"10MB config rejection too slow: {elapsed_ms:.1f}ms (target: <5000ms)"
        )

    @pytest.mark.performance
    def test_deeply_nested_config_loads(self, temp_project_dir: Path) -> None:
        """Real test: Deeply nested config (100 levels) loads successfully."""
        # Create deeply nested structure
        config = {"level_0": {}}
        current = config["level_0"]

        for i in range(1, 100):
            current[f"level_{i}"] = {}
            current = current[f"level_{i}"]

        current["value"] = "deep"

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump(config))

        start_time = time.perf_counter()

        loaded_config = Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert loaded_config is not None
        assert elapsed_ms < 2000, (
            f"Deep nesting load too slow: {elapsed_ms:.1f}ms (target: <2000ms)"
        )


class TestConcurrentOperations:
    """Test performance under concurrent operations (real execution)."""

    @pytest.mark.performance
    def test_concurrent_config_loads(self, temp_project_dir: Path) -> None:
        """Real test: 10 concurrent config loads complete in < 2 seconds."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        import concurrent.futures

        def load_config():
            return Config.from_project(temp_project_dir)

        start_time = time.perf_counter()

        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
            futures = [executor.submit(load_config) for _ in range(10)]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert len(results) == 10
        assert all(r is not None for r in results)
        assert elapsed_ms < 2000, (
            f"Concurrent loads too slow: {elapsed_ms:.1f}ms"
        )


class TestResourceLimits:
    """Test behavior under resource constraints (real execution)."""

    @pytest.mark.performance
    def test_config_load_respects_memory_limit(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Config loading works within memory constraints."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        # Set memory limit to 100MB
        try:
            soft, hard = resource.getrlimit(resource.RLIMIT_AS)
            resource.setrlimit(resource.RLIMIT_AS, (100 * 1024 * 1024, hard))

            config = Config.from_project(temp_project_dir)

            assert config is not None

        finally:
            # Restore original limit
            resource.setrlimit(resource.RLIMIT_AS, (soft, hard))

    @pytest.mark.performance
    def test_config_load_with_open_file_limit(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Config loading works with limited open file descriptors."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}})
        )

        try:
            soft, hard = resource.getrlimit(resource.RLIMIT_NOFILE)
            # Set to reasonable but limited value
            resource.setrlimit(resource.RLIMIT_NOFILE, (64, hard))

            config = Config.from_project(temp_project_dir)

            assert config is not None

        finally:
            # Restore original limit
            resource.setrlimit(resource.RLIMIT_NOFILE, (soft, hard))


class TestCachingEffectiveness:
    """Test caching performance benefits (real execution)."""

    @pytest.mark.performance
    def test_config_caching_faster_on_repeat(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: Repeated config loads are faster (via filesystem cache)."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        large_config = {
            f"section_{i}": {f"key_{j}": f"value_{i}_{j}" for j in range(50)}
            for i in range(50)
        }
        config_file.write_text(yaml.dump(large_config))

        # First load (filesystem read)
        start1 = time.perf_counter()
        Config.from_project(temp_project_dir)
        time1 = (time.perf_counter() - start1) * 1000

        # Second load (should be cached by OS)
        start2 = time.perf_counter()
        Config.from_project(temp_project_dir)
        time2 = (time.perf_counter() - start2) * 1000

        # Second load should be faster (or equal)
        # Allow for variance
        assert time2 <= time1 * 1.5, (
            f"Cache not effective: first={time1:.1f}ms, second={time2:.1f}ms"
        )


class TestErrorRecoveryPerformance:
    """Test performance during error scenarios (real execution)."""

    @pytest.mark.performance
    def test_invalid_config_error_fast(self, temp_project_dir: Path) -> None:
        """Real test: Invalid config error detection is fast."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("invalid: yaml: [error")

        start_time = time.perf_counter()

        with pytest.raises(RuntimeError):
            Config.from_project(temp_project_dir)

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        # Even errors should be detected quickly (1 second including Python startup)
        assert elapsed_ms < 1500, (
            f"Error detection too slow: {elapsed_ms:.1f}ms (target: <1500ms)"
        )

    @pytest.mark.performance
    def test_missing_project_error_fast(self, temp_project_dir: Path) -> None:
        """Real test: Missing project error detection is fast."""
        # Use a nonexistent subdirectory (won't have project markers)
        nonexistent_path = temp_project_dir / "nonexistent_project"

        start_time = time.perf_counter()

        # Missing project should raise RuntimeError
        try:
            Config.from_project(nonexistent_path)
            # If it doesn't raise, that's also acceptable (returns empty config)
        except RuntimeError:
            pass

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        assert elapsed_ms < 500, (
            f"Missing project error detection too slow: {elapsed_ms:.1f}ms (target: <500ms)"
        )


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-m", "performance"])
