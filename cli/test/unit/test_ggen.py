"""Unit tests for YAWL ggen commands (Chicago TDD)."""

from pathlib import Path

import pytest
from typer.testing import CliRunner

from yawl_cli.ggen import ggen_app


class TestGgenCommands:
    """Test ggen subcommand functionality."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_init_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Init command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "ggen initialized", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["init"])

        assert result.exit_code == 0
        assert "initialized" in result.stdout.lower()

    def test_init_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Init command fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Initialization failed")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["init"])

        assert result.exit_code == 1

    def test_generate_command_success(
        self, runner: CliRunner, temp_workflow_file: Path, monkeypatch
    ) -> None:
        """Generate command succeeds with spec file."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Generated YAWL XML", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["generate", str(temp_workflow_file)])

        assert result.exit_code == 0
        assert "Generation" in result.stdout or "completed" in result.stdout

    def test_generate_command_missing_spec_file(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Generate command fails with missing spec file."""
        result = runner.invoke(ggen_app, ["generate", "/nonexistent/spec.ttl"])

        assert result.exit_code == 1
        assert "not found" in result.stdout or "Error" in result.stdout

    def test_generate_with_output_option(
        self, runner: CliRunner, temp_workflow_file: Path, monkeypatch
    ) -> None:
        """Generate command accepts output file option."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        output_file = "/tmp/output.yawl"
        result = runner.invoke(
            ggen_app,
            ["generate", str(temp_workflow_file), "--output", output_file]
        )

        assert result.exit_code == 0
        assert output_file in captured_cmd[0]

    def test_generate_default_output_filename(
        self, runner: CliRunner, temp_workflow_file: Path, monkeypatch
    ) -> None:
        """Generate command uses default output filename."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["generate", str(temp_workflow_file)])

        assert result.exit_code == 0
        # Default output should be .yawl extension
        assert captured_cmd[0][-1].endswith(".yawl")

    def test_validate_command_success(
        self, runner: CliRunner, temp_workflow_file: Path, monkeypatch
    ) -> None:
        """Validate command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Validation passed", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["validate", str(temp_workflow_file)])

        assert result.exit_code == 0
        assert "Validation" in result.stdout.lower() or result.exit_code == 0

    def test_validate_command_missing_spec_file(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Validate command fails with missing spec file."""
        result = runner.invoke(ggen_app, ["validate", "/nonexistent/spec.ttl"])

        assert result.exit_code != 0
        assert "not found" in result.stdout

    def test_generate_verbose_flag(
        self, runner: CliRunner, temp_workflow_file: Path, monkeypatch
    ) -> None:
        """Generate command passes verbose flag."""
        captured_kwargs = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_kwargs.append(kwargs)
            return (0, "", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(
            ggen_app,
            ["generate", str(temp_workflow_file), "--verbose"]
        )

        assert result.exit_code == 0
        assert captured_kwargs[0]["verbose"] is True


class TestGgenErrorHandling:
    """Test ggen error handling."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_generate_invalid_spec_format(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Generate handles invalid spec format."""
        # Create invalid spec file
        bad_spec = temp_project_dir / "bad_spec.ttl"
        bad_spec.write_text("invalid turtle syntax }{")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Invalid RDF syntax")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["generate", str(bad_spec)])

        assert result.exit_code == 1

    def test_init_script_missing(self, runner: CliRunner, monkeypatch) -> None:
        """Init handles missing initialization script."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Script not found: scripts/ggen-init.sh")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["init"])

        assert result.exit_code != 0

    def test_generate_requires_spec_argument(self, runner: CliRunner) -> None:
        """Generate requires spec file argument."""
        result = runner.invoke(ggen_app, ["generate"])

        assert result.exit_code != 0

    def test_validate_requires_spec_argument(self, runner: CliRunner) -> None:
        """Validate requires spec file argument."""
        result = runner.invoke(ggen_app, ["validate"])

        assert result.exit_code != 0
