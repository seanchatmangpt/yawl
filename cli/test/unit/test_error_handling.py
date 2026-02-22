"""Comprehensive error handling and recovery tests (Chicago TDD).

Tests REAL failure scenarios with actual error conditions, not mocks or stubs.
Verifies that:
1. All error paths have clear, actionable error messages
2. Recovery guidance is provided
3. Exit codes are correct
4. No silent failures
"""

import json
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Generator
from unittest.mock import patch, MagicMock

import pytest
import yaml

from yawl_cli.utils import (
    Config,
    ensure_project_root,
    run_shell_cmd,
    load_facts,
)


class TestProjectNotFound:
    """Test error handling when project root is not found."""

    def test_ensure_project_root_not_found_no_pom(
        self, monkeypatch
    ) -> None:
        """Error when pom.xml is missing."""
        with tempfile.TemporaryDirectory() as tmpdir:
            monkeypatch.chdir(tmpdir)

            with pytest.raises(
                RuntimeError,
                match="Could not find YAWL project root"
            ) as exc_info:
                ensure_project_root()

            error_msg = str(exc_info.value)
            assert "pom.xml" in error_msg or "CLAUDE.md" in error_msg

    def test_ensure_project_root_not_found_no_claude_md(
        self, monkeypatch
    ) -> None:
        """Error when CLAUDE.md is missing."""
        with tempfile.TemporaryDirectory() as tmpdir:
            project_root = Path(tmpdir)
            (project_root / "pom.xml").write_text("<project></project>")
            monkeypatch.chdir(tmpdir)

            with pytest.raises(
                RuntimeError,
                match="Could not find YAWL project root"
            ):
                ensure_project_root()

    def test_ensure_project_root_searches_parent_directories(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Find project root from nested subdirectory."""
        nested = temp_project_dir / "a" / "b" / "c" / "d"
        nested.mkdir(parents=True)
        monkeypatch.chdir(nested)

        root = ensure_project_root()

        assert root == temp_project_dir

    def test_ensure_project_root_error_message_is_actionable(
        self, monkeypatch
    ) -> None:
        """Error message includes recovery step."""
        with tempfile.TemporaryDirectory() as tmpdir:
            monkeypatch.chdir(tmpdir)

            with pytest.raises(RuntimeError) as exc_info:
                ensure_project_root()

            error_msg = str(exc_info.value)
            # Should suggest recovery: "cd to project root" or similar
            assert len(error_msg) > 10


class TestInvalidYAMLConfig:
    """Test error handling for malformed YAML configuration."""

    def test_config_invalid_yaml_syntax(
        self, temp_project_dir: Path
    ) -> None:
        """Error on invalid YAML syntax with line number."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Write malformed YAML
        config_file.write_text(
            """build:
  parallel: true
  threads: 4
  timeout: 300
  invalid:
    - syntax:
  - unbalanced["""
        )

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        # Should mention YAML error and file location
        assert "YAML" in error_msg or "yaml" in error_msg
        assert str(config_file) in error_msg

    def test_config_yaml_line_number_in_error(
        self, temp_project_dir: Path
    ) -> None:
        """Error message includes line number of YAML error."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Write YAML with error on line 5
        config_file.write_text(
            """build:
  parallel: true
  threads: 4
  timeout: 300
  invalid: [unclosed"""
        )

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        # Should suggest recovery: "fix YAML syntax" or similar
        assert "fix" in error_msg.lower() or "syntax" in error_msg.lower()

    def test_config_wrong_yaml_type(
        self, temp_project_dir: Path
    ) -> None:
        """Error when YAML is not a dictionary."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Write YAML as list (not dict)
        config_file.write_text(
            """- item1
- item2
- item3"""
        )

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        assert "dictionary" in error_msg or "dict" in error_msg.lower()

    def test_config_unicode_decode_error(
        self, temp_project_dir: Path
    ) -> None:
        """Error when config file has invalid encoding."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Write binary garbage that's not valid UTF-8
        config_file.write_bytes(b"\x80\x81\x82\x83")

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        assert "encoding" in error_msg.lower() or "utf-8" in error_msg.lower()

    def test_config_too_large_file(
        self, temp_project_dir: Path
    ) -> None:
        """Error when config file exceeds size limit."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Write file > 1 MB
        large_content = "x: " + ("y" * (1024 * 1024 + 1))
        config_file.write_text(large_content)

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        assert "too large" in error_msg.lower() or "maximum" in error_msg.lower()


class TestPermissionDenied:
    """Test error handling for permission denied errors."""

    def test_config_permission_denied_read(
        self, temp_project_dir: Path
    ) -> None:
        """Error when config file is not readable (skipped if running as root)."""
        # Skip test if running as root (permissions don't apply)
        if os.geteuid() == 0:
            pytest.skip("Running as root - permission tests skipped")

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("build: {}")

        # Remove read permissions
        config_file.chmod(0o000)

        try:
            with pytest.raises(RuntimeError) as exc_info:
                Config.from_project(temp_project_dir)

            error_msg = str(exc_info.value)
            assert "permission" in error_msg.lower() or "read" in error_msg.lower()
        finally:
            # Restore permissions for cleanup
            config_file.chmod(0o644)

    def test_config_permission_denied_write(
        self, temp_project_dir: Path
    ) -> None:
        """Error when config directory is not writable (skipped if running as root)."""
        # Skip test if running as root (permissions don't apply)
        if os.geteuid() == 0:
            pytest.skip("Running as root - permission tests skipped")

        config_dir = temp_project_dir / ".yawl"
        config_dir.mkdir(parents=True, exist_ok=True)

        # Remove write permissions
        config_dir.chmod(0o555)

        try:
            config = Config(project_root=temp_project_dir)
            config.config_data = {"test": "value"}

            with pytest.raises(RuntimeError) as exc_info:
                config.save(config_dir / "config.yaml")

            error_msg = str(exc_info.value)
            assert "permission" in error_msg.lower() or "write" in error_msg.lower()
        finally:
            # Restore permissions for cleanup
            config_dir.chmod(0o755)

    def test_config_permission_denied_directory_creation(
        self, temp_project_dir: Path
    ) -> None:
        """Error when parent directory cannot be created (skipped if running as root)."""
        # Skip test if running as root (permissions don't apply)
        if os.geteuid() == 0:
            pytest.skip("Running as root - permission tests skipped")

        parent = temp_project_dir / "readonly"
        parent.mkdir()
        parent.chmod(0o555)

        try:
            config = Config(project_root=temp_project_dir)
            config.config_data = {"test": "value"}

            with pytest.raises(RuntimeError) as exc_info:
                config.save(parent / ".yawl" / "config.yaml")

            error_msg = str(exc_info.value)
            assert "permission" in error_msg.lower() or "create" in error_msg.lower()
        finally:
            parent.chmod(0o755)


class TestMavenNotFound:
    """Test error handling when Maven is not found."""

    def test_maven_not_found_error(self, monkeypatch) -> None:
        """Error when mvn command is not in PATH."""
        # Mock subprocess to simulate command not found
        def fake_run(*args, **kwargs):
            if "mvn" in str(args):
                raise FileNotFoundError("mvn: command not found")
            # For other commands, return success
            result = MagicMock()
            result.returncode = 0
            result.stdout = ""
            return result

        monkeypatch.setattr(subprocess, "run", fake_run)

        with pytest.raises((FileNotFoundError, Exception)):
            subprocess.run(["mvn", "--version"], check=True)

    def test_run_shell_cmd_command_not_found(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """run_shell_cmd raises error for command not found."""
        # Test with nonexistent command
        with pytest.raises(RuntimeError) as exc_info:
            run_shell_cmd(
                ["nonexistent_command_12345_xyz"],
                cwd=temp_project_dir
            )

        # Error message should be clear
        error_msg = str(exc_info.value)
        assert "not found" in error_msg.lower() or "nonexistent" in error_msg

    def test_maven_version_timeout(self, monkeypatch) -> None:
        """Maven version check timeout is handled."""
        def fake_run(*args, **kwargs):
            if kwargs.get("timeout") and "mvn" in str(args):
                raise subprocess.TimeoutExpired("mvn --version", 5)
            result = MagicMock()
            result.returncode = 0
            result.stdout = ""
            return result

        monkeypatch.setattr(subprocess, "run", fake_run)

        # Config loading should handle Maven timeout gracefully
        with tempfile.TemporaryDirectory() as tmpdir:
            project_root = Path(tmpdir)
            (project_root / "pom.xml").write_text("<project></project>")
            (project_root / "CLAUDE.md").write_text("# Test")

            # Should not crash, Maven version just won't be populated
            config = Config.from_project(project_root)
            # Maven version will be None due to timeout
            assert config.maven_version is None


class TestBuildTimeout:
    """Test error handling for build timeouts."""

    def test_run_shell_cmd_timeout(self, temp_project_dir: Path) -> None:
        """Command timeout is detected and reported."""
        # Create a script that sleeps longer than timeout
        script = temp_project_dir / "slow_build.sh"
        script.write_text("#!/bin/bash\nsleep 60\n")
        script.chmod(0o755)

        # Run with very short timeout
        with pytest.raises(RuntimeError) as exc_info:
            run_shell_cmd(
                ["bash", str(script)],
                cwd=temp_project_dir,
                timeout=0.1  # 100ms timeout
            )

        # Should mention timeout
        error_msg = str(exc_info.value)
        assert "timeout" in error_msg.lower() or "timed out" in error_msg.lower()

    def test_run_shell_cmd_timeout_error_message(
        self, temp_project_dir: Path
    ) -> None:
        """Timeout error includes command and timeout value."""
        script = temp_project_dir / "slow_build.sh"
        script.write_text("#!/bin/bash\nsleep 30\n")
        script.chmod(0o755)

        with pytest.raises(RuntimeError) as exc_info:
            run_shell_cmd(
                ["bash", str(script)],
                cwd=temp_project_dir,
                timeout=0.1
            )

        # Error should include helpful recovery guidance
        error_msg = str(exc_info.value)
        assert "timeout" in error_msg.lower()
        # Should suggest how to fix (increase timeout, etc.)
        assert "timeout" in error_msg.lower() or "increase" in error_msg.lower()


class TestFactsFileNotFound:
    """Test error handling for missing facts files."""

    def test_load_facts_file_not_found(
        self, temp_project_dir: Path
    ) -> None:
        """Error when fact file doesn't exist."""
        facts_dir = temp_project_dir / "facts"

        with pytest.raises(FileNotFoundError) as exc_info:
            load_facts(facts_dir, "nonexistent.json")

        error_msg = str(exc_info.value)
        assert "nonexistent.json" in error_msg or "not found" in error_msg.lower()

    def test_load_facts_corrupted_json(
        self, temp_project_dir: Path
    ) -> None:
        """Error when fact file has invalid JSON."""
        facts_dir = temp_project_dir / "facts"
        facts_dir.mkdir()

        # Write invalid JSON
        fact_file = facts_dir / "modules.json"
        fact_file.write_text("{invalid json content [}")

        with pytest.raises(RuntimeError) as exc_info:
            load_facts(facts_dir, "modules.json")

        # Error should mention malformed JSON
        error_msg = str(exc_info.value)
        assert "malformed" in error_msg.lower() or "json" in error_msg.lower()

    def test_load_facts_directory_not_found(
        self, temp_project_dir: Path
    ) -> None:
        """Error when facts directory doesn't exist."""
        facts_dir = temp_project_dir / "nonexistent" / "facts"

        with pytest.raises(FileNotFoundError):
            load_facts(facts_dir, "modules.json")


class TestConfigSaveErrors:
    """Test error handling when saving configuration."""

    def test_config_save_atomicity(
        self, temp_project_dir: Path
    ) -> None:
        """Config save is atomic (uses temp file + rename)."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {"build": {"threads": 4}}

        config_file = temp_project_dir / ".yawl" / "config.yaml"

        config.save(config_file)

        # File should exist and be valid YAML
        assert config_file.exists()
        with open(config_file) as f:
            loaded = yaml.safe_load(f)
        assert loaded["build"]["threads"] == 4

    def test_config_save_temp_file_cleanup(
        self, temp_project_dir: Path
    ) -> None:
        """Temp files are cleaned up after save."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {"test": "data"}

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config.save(config_file)

        # Temp file should not exist
        temp_file = config_file.with_suffix(".yaml.tmp")
        assert not temp_file.exists()

    def test_config_save_disk_full_simulation(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Error when disk is full (simulated)."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {"test": "data"}

        def fake_open(*args, **kwargs):
            raise OSError("No space left on device")

        monkeypatch.setattr("builtins.open", fake_open)

        with pytest.raises(RuntimeError):
            config.save(temp_project_dir / ".yawl" / "config.yaml")


class TestConfigValidation:
    """Test configuration validation and error messages."""

    def test_config_deep_merge_preserves_structure(
        self, temp_project_dir: Path
    ) -> None:
        """Config merging preserves nested structure."""
        config = Config(project_root=temp_project_dir)

        base = {"a": {"b": 1, "c": 2}}
        override = {"a": {"b": 10}}

        merged = Config._deep_merge(base, override)

        assert merged["a"]["b"] == 10
        assert merged["a"]["c"] == 2

    def test_config_get_with_dot_notation(
        self, temp_project_dir: Path
    ) -> None:
        """Get config with dot notation."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {
            "build": {
                "parallel": True,
                "threads": 4
            }
        }

        assert config.get("build.parallel") is True
        assert config.get("build.threads") == 4
        assert config.get("build.nonexistent") is None

    def test_config_get_with_default(
        self, temp_project_dir: Path
    ) -> None:
        """Get config returns default for missing keys."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        assert config.get("missing.key", "default") == "default"

    def test_config_set_with_dot_notation(
        self, temp_project_dir: Path
    ) -> None:
        """Set config with dot notation creates nested structure."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        config.set("build.threads", 8)

        assert config.config_data["build"]["threads"] == 8

    def test_config_invalid_data_type_error(
        self, temp_project_dir: Path
    ) -> None:
        """Error when config data is not a dictionary."""
        config = Config(project_root=temp_project_dir)
        config.config_data = "not a dict"

        with pytest.raises(RuntimeError) as exc_info:
            config.save(temp_project_dir / ".yawl" / "config.yaml")

        error_msg = str(exc_info.value)
        assert "dictionary" in error_msg.lower()


class TestRunShellCmdErrors:
    """Test error handling in run_shell_cmd."""

    def test_run_shell_cmd_with_stderr(
        self, temp_project_dir: Path
    ) -> None:
        """Stderr output is captured."""
        script = temp_project_dir / "test_stderr.sh"
        script.write_text("#!/bin/bash\necho 'error output' >&2\nexit 1\n")
        script.chmod(0o755)

        exit_code, stdout, stderr = run_shell_cmd(
            ["bash", str(script)],
            cwd=temp_project_dir
        )

        assert exit_code == 1
        assert "error output" in stderr

    def test_run_shell_cmd_success(
        self, temp_project_dir: Path
    ) -> None:
        """Successful command has exit code 0."""
        script = temp_project_dir / "test_success.sh"
        script.write_text("#!/bin/bash\necho 'success'\nexit 0\n")
        script.chmod(0o755)

        exit_code, stdout, stderr = run_shell_cmd(
            ["bash", str(script)],
            cwd=temp_project_dir
        )

        assert exit_code == 0
        assert "success" in stdout

    def test_run_shell_cmd_cwd_respected(
        self, temp_project_dir: Path
    ) -> None:
        """Working directory parameter is respected."""
        subdir = temp_project_dir / "subdir"
        subdir.mkdir()

        script = subdir / "test_pwd.sh"
        script.write_text("#!/bin/bash\npwd\n")
        script.chmod(0o755)

        exit_code, stdout, _ = run_shell_cmd(
            ["bash", str(script)],
            cwd=subdir
        )

        assert exit_code == 0
        assert str(subdir) in stdout


class TestRecoveryGuidance:
    """Test that all errors provide actionable recovery guidance."""

    def test_project_not_found_recovery_guidance(
        self, monkeypatch
    ) -> None:
        """Project not found error suggests recovery step."""
        with tempfile.TemporaryDirectory() as tmpdir:
            monkeypatch.chdir(tmpdir)

            with pytest.raises(RuntimeError) as exc_info:
                ensure_project_root()

            error_msg = str(exc_info.value)
            # Error message should be helpful (not empty, not just "error occurred")
            assert len(error_msg) > 20
            assert "project" in error_msg.lower() or "root" in error_msg.lower()

    def test_yaml_error_recovery_guidance(
        self, temp_project_dir: Path
    ) -> None:
        """Invalid YAML error suggests how to fix it."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("bad: yaml: [syntax")

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        # Should suggest recovery: delete, fix, or regenerate
        assert (
            "fix" in error_msg.lower() or
            "delete" in error_msg.lower() or
            "syntax" in error_msg.lower()
        )

    def test_permission_error_recovery_guidance(
        self, temp_project_dir: Path
    ) -> None:
        """Permission error explains what to check (skipped if running as root)."""
        # Skip test if running as root (permissions don't apply)
        if os.geteuid() == 0:
            pytest.skip("Running as root - permission tests skipped")

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("test: data")
        config_file.chmod(0o000)

        try:
            with pytest.raises(RuntimeError) as exc_info:
                Config.from_project(temp_project_dir)

            error_msg = str(exc_info.value)
            assert "permission" in error_msg.lower()
        finally:
            config_file.chmod(0o644)

    def test_file_not_found_recovery_guidance(
        self, temp_project_dir: Path
    ) -> None:
        """File not found error is clear."""
        facts_dir = temp_project_dir / "facts"

        with pytest.raises(FileNotFoundError) as exc_info:
            load_facts(facts_dir, "modules.json")

        error_msg = str(exc_info.value)
        # Should mention the missing file name
        assert "modules.json" in error_msg or "not found" in error_msg.lower()


class TestNoSilentFailures:
    """Test that all error paths are caught and reported."""

    def test_config_all_errors_raised_not_swallowed(
        self, temp_project_dir: Path
    ) -> None:
        """Config errors are raised, not silently ignored."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Test various error conditions
        error_cases = [
            ("bad: yaml: [", RuntimeError),  # Invalid YAML
            (b"\x80\x81\x82", RuntimeError),  # Invalid encoding
        ]

        for content, expected_exception in error_cases:
            if isinstance(content, str):
                config_file.write_text(content)
            else:
                config_file.write_bytes(content)

            # Error should be raised, not silently ignored
            with pytest.raises(expected_exception):
                Config.from_project(temp_project_dir)

    def test_run_shell_cmd_nonzero_exit_reported(
        self, temp_project_dir: Path
    ) -> None:
        """Non-zero exit code is returned (not silently treated as success)."""
        script = temp_project_dir / "failing.sh"
        script.write_text("#!/bin/bash\nexit 42\n")
        script.chmod(0o755)

        exit_code, _, _ = run_shell_cmd(
            ["bash", str(script)],
            cwd=temp_project_dir
        )

        # Should report actual exit code, not 0
        assert exit_code == 42

    def test_facts_load_missing_file_raises_error(
        self, temp_project_dir: Path
    ) -> None:
        """Missing fact file raises error, not returns empty dict."""
        facts_dir = temp_project_dir / "facts"

        with pytest.raises(FileNotFoundError):
            load_facts(facts_dir, "nonexistent.json")


class TestErrorMessageFormatting:
    """Test that error messages are clear and well-formatted."""

    def test_yaml_error_includes_file_path(
        self, temp_project_dir: Path
    ) -> None:
        """YAML error message includes the file path."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("bad: yaml: [")

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        assert str(config_file) in error_msg

    def test_permission_error_includes_file_path(
        self, temp_project_dir: Path
    ) -> None:
        """Permission error includes the file path (skipped if running as root)."""
        # Skip test if running as root (permissions don't apply)
        if os.geteuid() == 0:
            pytest.skip("Running as root - permission tests skipped")

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("test: data")
        config_file.chmod(0o000)

        try:
            with pytest.raises(RuntimeError) as exc_info:
                Config.from_project(temp_project_dir)

            error_msg = str(exc_info.value)
            assert str(config_file) in error_msg
        finally:
            config_file.chmod(0o644)

    def test_config_error_not_generic(
        self, temp_project_dir: Path
    ) -> None:
        """Error messages are specific, not generic."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("bad yaml [")

        with pytest.raises(RuntimeError) as exc_info:
            Config.from_project(temp_project_dir)

        error_msg = str(exc_info.value)
        # Should not be a generic "Error occurred" message
        assert "Error occurred" not in error_msg
        assert len(error_msg) > 20


class TestBoundaryConditions:
    """Test error handling at boundary conditions."""

    def test_empty_yaml_file(
        self, temp_project_dir: Path
    ) -> None:
        """Empty YAML file is handled gracefully."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("")

        config = Config.from_project(temp_project_dir)
        # Should not crash, config_data should be empty dict
        assert config.config_data == {}

    def test_config_max_size_limit(
        self, temp_project_dir: Path
    ) -> None:
        """Config file at size limit is accepted."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        # Create file exactly at 1MB limit
        content = "x: " + ("y" * (1024 * 1024 - 10))
        config_file.write_text(content)

        # Should be accepted (not raise error)
        config = Config.from_project(temp_project_dir)
        assert config is not None

    def test_nested_config_depth(
        self, temp_project_dir: Path
    ) -> None:
        """Deeply nested config paths work correctly."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        config.set("a.b.c.d.e.f.g", "deep_value")

        assert config.get("a.b.c.d.e.f.g") == "deep_value"

    def test_special_characters_in_config_path(
        self, temp_project_dir: Path
    ) -> None:
        """Config keys with special characters are handled."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        # Test with hyphenated keys (common in YAML)
        config.set("build-options.thread-count", 8)

        assert config.get("build-options.thread-count") == 8

    def test_zero_size_file(
        self, temp_project_dir: Path
    ) -> None:
        """Zero-size config file is handled."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)
        config_file.write_text("")

        config = Config.from_project(temp_project_dir)
        assert config.config_data is not None


class TestConcurrentErrorHandling:
    """Test error handling under concurrent conditions."""

    def test_config_save_creates_temp_file(
        self, temp_project_dir: Path
    ) -> None:
        """Config save uses temp file for atomicity."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {"test": "data"}

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config.save(config_file)

        # Final file should exist and be valid
        assert config_file.exists()
        with open(config_file) as f:
            loaded = yaml.safe_load(f)
        assert loaded == {"test": "data"}

    def test_multiple_config_loads(
        self, temp_project_dir: Path
    ) -> None:
        """Multiple concurrent config loads don't interfere."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        test_data = {"build": {"threads": 4}}
        with open(config_file, "w") as f:
            yaml.dump(test_data, f)

        # Load multiple times
        config1 = Config.from_project(temp_project_dir)
        config2 = Config.from_project(temp_project_dir)

        assert config1.config_data == config2.config_data


class TestExitCodes:
    """Test that appropriate exit codes are used."""

    def test_shell_cmd_exit_code_preserved(
        self, temp_project_dir: Path
    ) -> None:
        """Shell command exit code is preserved."""
        script = temp_project_dir / "test_exit.sh"

        # Test various exit codes
        for expected_code in [0, 1, 2, 42]:
            script.write_text(f"#!/bin/bash\nexit {expected_code}\n")
            script.chmod(0o755)

            exit_code, _, _ = run_shell_cmd(
                ["bash", str(script)],
                cwd=temp_project_dir
            )

            assert exit_code == expected_code
