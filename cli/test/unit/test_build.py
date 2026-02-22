"""Unit tests for YAWL CLI build commands (Chicago TDD)."""

from unittest.mock import Mock, patch

import pytest
import typer
from typer.testing import CliRunner

from yawl_cli.build import build_app


class TestBuildCommands:
    """Test build subcommand functionality."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_compile_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Compile command succeeds."""
        # Mock run_shell_cmd to simulate successful compile
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Compiling...", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code == 0

    def test_compile_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Compile command fails with non-zero exit code."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Compilation failed")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code == 1

    def test_compile_with_module_option(self, runner: CliRunner, monkeypatch) -> None:
        """Compile command with specific module."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile", "--module", "yawl-engine"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["bash", "scripts/dx.sh", "-pl", "yawl-engine"]

    def test_compile_without_module_option(self, runner: CliRunner, monkeypatch) -> None:
        """Compile command without module compiles all."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["bash", "scripts/dx.sh", "compile"]

    def test_compile_verbose_flag(self, runner: CliRunner, monkeypatch) -> None:
        """Compile command passes verbose flag."""
        captured_kwargs = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_kwargs.append(kwargs)
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile", "--verbose"])

        assert result.exit_code == 0
        assert captured_kwargs[0]["verbose"] is True

    def test_test_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Test command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Tests passed", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["test"])

        assert result.exit_code == 0
        assert "Tests passed" in result.stdout

    def test_test_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Test command fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Test failed")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["test"])

        assert result.exit_code == 1

    def test_test_with_module_option(self, runner: CliRunner, monkeypatch) -> None:
        """Test command with specific module."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["test", "--module", "yawl-engine"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["mvn", "test", "-pl", "yawl-engine"]

    def test_validate_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Validate command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Validation passed", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["validate"])

        assert result.exit_code == 0

    def test_validate_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Validate command fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (2, "", "CheckStyle violations found")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["validate"])

        assert result.exit_code == 2

    def test_all_command_runs_full_build(self, runner: CliRunner, monkeypatch) -> None:
        """All command runs full build sequence."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["all"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["bash", "scripts/dx.sh", "all"]

    def test_all_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """All command fails if any phase fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Full build failed")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["all"])

        assert result.exit_code == 1

    def test_clean_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Clean command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["clean"])

        assert result.exit_code == 0
        assert "Clean successful" in result.stdout

    def test_clean_command_runs_maven_clean(self, runner: CliRunner, monkeypatch) -> None:
        """Clean command runs 'mvn clean'."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["clean"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["mvn", "clean"]

    def test_clean_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Clean command fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Clean failed")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["clean"])

        assert result.exit_code == 1


class TestBuildErrorHandling:
    """Test build command error handling."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_compile_shows_error_output(self, runner: CliRunner, monkeypatch) -> None:
        """Compile displays error output on failure."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "ERROR: Java not found")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code == 1
        assert "Java not found" in result.stdout

    def test_test_command_shows_output(self, runner: CliRunner, monkeypatch) -> None:
        """Test command displays test output."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "45 tests passed", "")

        import yawl_cli.build

        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["test"])

        assert result.exit_code == 0
        assert "tests passed" in result.stdout.lower() or "Tests passed" in result.stdout
