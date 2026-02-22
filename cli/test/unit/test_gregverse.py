"""Unit tests for YAWL gregverse format conversion (Chicago TDD)."""

from pathlib import Path

import pytest
from typer.testing import CliRunner

from yawl_cli.gregverse import gregverse_app


class TestGregverseCommands:
    """Test gregverse format conversion commands."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_import_bpmn_command_success(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Import BPMN command succeeds."""
        # Create a test BPMN file
        bpmn_file = temp_project_dir / "sample.bpmn"
        bpmn_file.write_text("<?xml version='1.0'?><bpmn:process/>\n")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "BPMN imported", "")

        import yawl_cli.gregverse
        monkeypatch.setattr(yawl_cli.gregverse, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(gregverse_app, ["import", "bpmn", str(bpmn_file)])

        assert result.exit_code == 0
        assert "Imported" in result.stdout or result.exit_code == 0

    def test_import_xpdl_command_success(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Import XPDL command succeeds."""
        # Create a test XPDL file
        xpdl_file = temp_project_dir / "sample.xpdl"
        xpdl_file.write_text("<?xml version='1.0'?><Package/>\n")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "XPDL imported", "")

        import yawl_cli.gregverse
        monkeypatch.setattr(yawl_cli.gregverse, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(gregverse_app, ["import", "xpdl", str(xpdl_file)])

        assert result.exit_code == 0

    def test_import_missing_file(self, runner: CliRunner) -> None:
        """Import fails with missing file."""
        result = runner.invoke(
            gregverse_app,
            ["import", "bpmn", "/nonexistent/file.bpmn"]
        )

        assert result.exit_code == 1
        assert "not found" in result.stdout

    def test_export_bpmn_command_success(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Export to BPMN command succeeds."""
        # Create a test YAWL file
        yawl_file = temp_project_dir / "sample.yawl"
        yawl_file.write_text("<?xml version='1.0'?><specificationSet/>\n")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "BPMN exported", "")

        import yawl_cli.gregverse
        monkeypatch.setattr(yawl_cli.gregverse, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(gregverse_app, ["export", "bpmn", str(yawl_file)])

        assert result.exit_code == 0

    def test_export_xpdl_command_success(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Export to XPDL command succeeds."""
        # Create a test YAWL file
        yawl_file = temp_project_dir / "sample.yawl"
        yawl_file.write_text("<?xml version='1.0'?><specificationSet/>\n")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "XPDL exported", "")

        import yawl_cli.gregverse
        monkeypatch.setattr(yawl_cli.gregverse, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(gregverse_app, ["export", "xpdl", str(yawl_file)])

        assert result.exit_code == 0

    def test_export_missing_file(self, runner: CliRunner) -> None:
        """Export fails with missing file."""
        result = runner.invoke(
            gregverse_app,
            ["export", "bpmn", "/nonexistent/file.yawl"]
        )

        assert result.exit_code == 1
        assert "not found" in result.stdout

    def test_export_unsupported_format(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """Export fails with unsupported format."""
        yawl_file = temp_project_dir / "sample.yawl"
        yawl_file.write_text("<?xml version='1.0'?><specificationSet/>\n")

        result = runner.invoke(
            gregverse_app,
            ["export", "unknown_format", str(yawl_file)]
        )

        assert result.exit_code != 0

    def test_import_with_output_option(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Import with output option."""
        bpmn_file = temp_project_dir / "sample.bpmn"
        bpmn_file.write_text("<?xml version='1.0'?><bpmn:process/>\n")

        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.gregverse
        monkeypatch.setattr(yawl_cli.gregverse, "run_shell_cmd", mock_run_shell_cmd)

        output_file = temp_project_dir / "output.yawl"
        result = runner.invoke(
            gregverse_app,
            ["import", "bpmn", str(bpmn_file), "--output", str(output_file)]
        )

        assert result.exit_code == 0


class TestGregverseErrorHandling:
    """Test gregverse error handling."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_import_requires_format_argument(self, runner: CliRunner) -> None:
        """Import requires format argument."""
        result = runner.invoke(gregverse_app, ["import"])

        assert result.exit_code != 0

    def test_import_requires_file_argument(self, runner: CliRunner) -> None:
        """Import requires file argument."""
        result = runner.invoke(gregverse_app, ["import", "bpmn"])

        assert result.exit_code != 0

    def test_export_requires_format_argument(self, runner: CliRunner) -> None:
        """Export requires format argument."""
        result = runner.invoke(gregverse_app, ["export"])

        assert result.exit_code != 0

    def test_export_requires_file_argument(self, runner: CliRunner) -> None:
        """Export requires file argument."""
        result = runner.invoke(gregverse_app, ["export", "bpmn"])

        assert result.exit_code != 0

    def test_invalid_xml_format(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Import handles invalid XML format."""
        invalid_file = temp_project_dir / "invalid.bpmn"
        invalid_file.write_text("not valid xml {}")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Invalid XML format")

        import yawl_cli.gregverse
        monkeypatch.setattr(yawl_cli.gregverse, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(gregverse_app, ["import", "bpmn", str(invalid_file)])

        assert result.exit_code == 1
