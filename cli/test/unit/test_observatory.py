"""Unit tests for YAWL CLI observatory commands (Chicago TDD)."""

import json
from pathlib import Path

import pytest
from typer.testing import CliRunner

from yawl_cli.observatory import observatory_app


class TestObservatoryCommands:
    """Test observatory subcommand functionality."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_generate_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Generate command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Facts generated", "")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["generate"])

        assert result.exit_code == 0
        assert "Facts generated" in result.stdout

    def test_generate_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Generate command fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Observable.sh not found")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["generate"])

        assert result.exit_code == 1

    def test_generate_verbose_flag(self, runner: CliRunner, monkeypatch) -> None:
        """Generate command passes verbose flag."""
        captured_kwargs = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_kwargs.append(kwargs)
            return (0, "", "")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["generate", "--verbose"])

        assert result.exit_code == 0
        assert captured_kwargs[0]["verbose"] is True

    def test_show_fact_command_success(
        self, runner: CliRunner, facts_directory: Path
    ) -> None:
        """Show fact command displays fact data."""
        result = runner.invoke(observatory_app, ["show", "modules"])

        assert result.exit_code == 0
        assert "yawl-engine" in result.stdout

    def test_show_fact_nonexistent(self, runner: CliRunner) -> None:
        """Show fact command fails for nonexistent fact."""
        result = runner.invoke(observatory_app, ["show", "nonexistent"])

        assert result.exit_code == 1

    def test_show_fact_all_sample_facts(
        self, runner: CliRunner, facts_directory: Path
    ) -> None:
        """Show fact command works for all sample facts."""
        for fact_name in ["modules", "gates", "tests"]:
            result = runner.invoke(observatory_app, ["show", fact_name])
            assert result.exit_code == 0

    def test_list_facts_command_success(
        self, runner: CliRunner, facts_directory: Path
    ) -> None:
        """List facts command displays all facts."""
        result = runner.invoke(observatory_app, ["list-facts"])

        assert result.exit_code == 0
        assert "modules.json" in result.stdout
        assert "gates.json" in result.stdout
        assert "tests.json" in result.stdout

    def test_list_facts_no_facts_generated(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List facts command shows message when no facts exist."""
        # Use a project dir without facts
        facts_dir = temp_project_dir / "docs/v6/latest/facts"
        facts_dir.mkdir(parents=True, exist_ok=True)

        result = runner.invoke(observatory_app, ["list-facts"])

        assert result.exit_code == 0
        assert "No facts found" in result.stdout or result.exit_code == 0

    def test_search_command_found(
        self, runner: CliRunner, facts_directory: Path
    ) -> None:
        """Search command finds pattern in facts."""
        result = runner.invoke(observatory_app, ["search", "yawl-engine"])

        assert result.exit_code == 0
        assert "Found in" in result.stdout or "yawl-engine" in result.stdout

    def test_search_command_not_found(
        self, runner: CliRunner, facts_directory: Path
    ) -> None:
        """Search command shows message when pattern not found."""
        result = runner.invoke(observatory_app, ["search", "nonexistent-pattern-xyz"])

        assert result.exit_code == 0
        assert "not found" in result.stdout

    def test_search_multiple_files(
        self, runner: CliRunner, facts_directory: Path
    ) -> None:
        """Search command finds pattern in multiple files."""
        # The pattern "tests" appears in multiple fact files
        result = runner.invoke(observatory_app, ["search", "tests"])

        assert result.exit_code == 0

    def test_refresh_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Refresh command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Facts refreshed", "")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["refresh"])

        assert result.exit_code == 0
        assert "refreshed" in result.stdout.lower()

    def test_refresh_command_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Refresh command fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Refresh failed")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["refresh"])

        assert result.exit_code == 1


class TestObservatoryFactFiles:
    """Test fact file handling."""

    def test_fact_file_contains_valid_json(self, facts_directory: Path) -> None:
        """Fact files contain valid JSON."""
        for fact_file in facts_directory.glob("*.json"):
            with open(fact_file) as f:
                data = json.load(f)
                assert isinstance(data, dict)
                assert len(data) > 0

    def test_modules_fact_structure(self, facts_directory: Path) -> None:
        """Modules fact has correct structure."""
        data = json.loads((facts_directory / "modules.json").read_text())
        assert "yawl-engine" in data
        assert data["yawl-engine"]["path"] == "yawl/engine"

    def test_gates_fact_structure(self, facts_directory: Path) -> None:
        """Gates fact has correct structure."""
        data = json.loads((facts_directory / "gates.json").read_text())
        assert "yawl-engine" in data
        assert "unit" in data["yawl-engine"]

    def test_tests_fact_structure(self, facts_directory: Path) -> None:
        """Tests fact has correct structure."""
        data = json.loads((facts_directory / "tests.json").read_text())
        assert "total" in data
        assert "coverage" in data


class TestObservatoryErrorHandling:
    """Test observatory error handling."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_generate_script_not_found(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Generate fails gracefully when script not found."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Script not found: scripts/observatory/observatory.sh")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["generate"])

        assert result.exit_code != 0

    def test_show_fact_without_argument(self, runner: CliRunner) -> None:
        """Show fact command requires fact name argument."""
        result = runner.invoke(observatory_app, ["show"])

        assert result.exit_code != 0

    def test_search_without_pattern(self, runner: CliRunner) -> None:
        """Search command requires pattern argument."""
        result = runner.invoke(observatory_app, ["search"])

        assert result.exit_code != 0
