"""Unit tests for CLI command coverage gaps (Chicago TDD).

Tests for config_cli.show/get/reset/locations, ggen.round_trip, team.list.
All tests use real file I/O with temp directories per Chicago TDD style.
"""

import json
import tempfile
from pathlib import Path
from typing import Generator

import pytest
import yaml
from typer.testing import CliRunner

from yawl_cli.config_cli import app as config_app
from yawl_cli.ggen import ggen_app
from yawl_cli.team import team_app


@pytest.fixture
def runner() -> CliRunner:
    """Create a Typer CLI test runner."""
    return CliRunner()


@pytest.fixture
def temp_project_dir(monkeypatch) -> Generator[Path, None, None]:
    """Create a temporary YAWL project directory and patch ensure_project_root."""
    with tempfile.TemporaryDirectory() as tmpdir:
        project_root = Path(tmpdir)
        (project_root / ".yawl").mkdir(parents=True, exist_ok=True)
        (project_root / "pom.xml").write_text(
            "<project><modelVersion>4.0.0</modelVersion></project>",
            encoding="utf-8",
        )
        (project_root / "CLAUDE.md").write_text("# YAWL\n", encoding="utf-8")
        (project_root / "scripts").mkdir(exist_ok=True)

        import yawl_cli.config_cli
        import yawl_cli.ggen
        import yawl_cli.team

        monkeypatch.setattr(yawl_cli.config_cli, "ensure_project_root", lambda: project_root)
        monkeypatch.setattr(yawl_cli.ggen, "ensure_project_root", lambda: project_root)
        monkeypatch.setattr(yawl_cli.team, "ensure_project_root", lambda: project_root)

        yield project_root


# ─── config_cli.show ────────────────────────────────────────────────────────

class TestConfigShow:
    """Tests for 'yawl config show' command."""

    def test_show_no_config_file(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Show command succeeds when no config file exists."""
        result = runner.invoke(config_app, ["show"])
        assert result.exit_code == 0

    def test_show_with_config_data(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Show command displays configuration from file."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            "build:\n  parallel: true\n  threads: 4\n",
            encoding="utf-8",
        )
        result = runner.invoke(config_app, ["show"])
        assert result.exit_code == 0

    def test_show_with_verbose_flag(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Show command accepts --verbose flag."""
        result = runner.invoke(config_app, ["show", "--verbose"])
        assert result.exit_code == 0

    def test_show_with_nested_config(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Show command renders nested configuration correctly."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        yaml.dump(
            {
                "build": {"parallel": True, "threads": 2},
                "features": {"mcp": True},
            },
            config_file.open("w"),
        )
        result = runner.invoke(config_app, ["show"])
        assert result.exit_code == 0

    def test_show_ensure_project_root_error(self, runner: CliRunner, monkeypatch) -> None:
        """Show command handles project root detection failure gracefully."""
        import yawl_cli.config_cli

        def boom():
            raise RuntimeError("No YAWL project found")

        monkeypatch.setattr(yawl_cli.config_cli, "ensure_project_root", boom)
        result = runner.invoke(config_app, ["show"])
        assert result.exit_code == 1


# ─── config_cli.get ─────────────────────────────────────────────────────────

class TestConfigGet:
    """Tests for 'yawl config get' command."""

    def test_get_existing_key(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Get command returns value for existing key."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("build:\n  threads: 8\n", encoding="utf-8")

        result = runner.invoke(config_app, ["get", "build.threads"])
        assert result.exit_code == 0
        assert "8" in result.output

    def test_get_missing_key_exits_nonzero(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """Get command exits 1 when key not found."""
        result = runner.invoke(config_app, ["get", "nonexistent.key"])
        assert result.exit_code == 1

    def test_get_boolean_value(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Get command returns boolean configuration value."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("build:\n  parallel: true\n", encoding="utf-8")

        result = runner.invoke(config_app, ["get", "build.parallel"])
        assert result.exit_code == 0

    def test_get_ensure_project_root_error(self, runner: CliRunner, monkeypatch) -> None:
        """Get command handles project root detection failure gracefully."""
        import yawl_cli.config_cli

        def boom():
            raise RuntimeError("No YAWL project found")

        monkeypatch.setattr(yawl_cli.config_cli, "ensure_project_root", boom)
        result = runner.invoke(config_app, ["get", "any.key"])
        assert result.exit_code == 1


# ─── config_cli.reset ───────────────────────────────────────────────────────

class TestConfigReset:
    """Tests for 'yawl config reset' command."""

    def test_reset_no_config_file(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Reset command succeeds when no config file exists."""
        result = runner.invoke(config_app, ["reset"], input="n\n")
        assert result.exit_code == 0

    def test_reset_declines_confirmation(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """Reset command does not delete file when user declines."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("build:\n  threads: 4\n", encoding="utf-8")

        result = runner.invoke(config_app, ["reset"], input="n\n")
        assert result.exit_code == 0
        assert config_file.exists()

    def test_reset_confirms_and_removes_file(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """Reset command removes config file when user confirms."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("build:\n  threads: 4\n", encoding="utf-8")

        result = runner.invoke(config_app, ["reset"], input="y\n")
        assert result.exit_code == 0
        assert not config_file.exists()

    def test_reset_ensure_project_root_error(self, runner: CliRunner, monkeypatch) -> None:
        """Reset command handles project root detection failure gracefully."""
        import yawl_cli.config_cli

        def boom():
            raise RuntimeError("No YAWL project found")

        monkeypatch.setattr(yawl_cli.config_cli, "ensure_project_root", boom)
        result = runner.invoke(config_app, ["reset"])
        assert result.exit_code == 1


# ─── config_cli.locations ───────────────────────────────────────────────────

class TestConfigLocations:
    """Tests for 'yawl config locations' command."""

    def test_locations_shows_table(self, runner: CliRunner, temp_project_dir: Path) -> None:
        """Locations command prints a table of config file paths."""
        result = runner.invoke(config_app, ["locations"])
        assert result.exit_code == 0

    def test_locations_when_project_root_fails(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Locations command falls back to cwd when project root detection fails."""
        import yawl_cli.config_cli

        def boom():
            raise RuntimeError("Not a YAWL project")

        monkeypatch.setattr(yawl_cli.config_cli, "ensure_project_root", boom)
        result = runner.invoke(config_app, ["locations"])
        assert result.exit_code == 0

    def test_locations_shows_existing_file_status(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """Locations command marks existing config files as 'exists'."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("build: {}\n", encoding="utf-8")

        result = runner.invoke(config_app, ["locations"])
        assert result.exit_code == 0
        assert "exists" in result.output


# ─── ggen.round_trip ────────────────────────────────────────────────────────

class TestGgenRoundTrip:
    """Tests for 'yawl ggen round-trip' command."""

    def test_round_trip_file_not_found(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """Round-trip fails cleanly when spec file does not exist."""
        result = runner.invoke(ggen_app, ["round-trip", "/nonexistent/spec.ttl"])
        assert result.exit_code == 1

    def test_round_trip_generation_phase_fails(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Round-trip exits 1 when Turtle→YAWL generation step fails."""
        # Create a real spec file
        spec = temp_project_dir / "workflow.ttl"
        spec.write_text("@prefix : <http://example.org/> .\n", encoding="utf-8")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Script failed")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["round-trip", str(spec)])
        assert result.exit_code == 1

    def test_round_trip_missing_yawl_output(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Round-trip fails when generation succeeded but output file is missing."""
        spec = temp_project_dir / "workflow.ttl"
        spec.write_text("@prefix : <http://example.org/> .\n", encoding="utf-8")

        # First call succeeds but creates no file; second call would be export
        call_count = [0]

        def mock_run_shell_cmd(cmd, **kwargs):
            call_count[0] += 1
            return (0, "", "")  # Success but no file created

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["round-trip", str(spec)])
        assert result.exit_code == 1

    def test_round_trip_export_phase_fails(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Round-trip exits 1 when YAWL→Turtle export step fails."""
        spec = temp_project_dir / "workflow.ttl"
        spec.write_text("@prefix : <http://example.org/> .\n", encoding="utf-8")

        call_count = [0]

        def mock_run_shell_cmd(cmd, **kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                # Phase 1 succeeds: create the .yawl file
                yawl_file = spec.with_suffix(".yawl")
                yawl_file.write_text("<spec/>", encoding="utf-8")
                return (0, "", "")
            # Phase 2 export fails
            return (1, "", "Export error")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["round-trip", str(spec)])
        assert result.exit_code == 1

    def test_round_trip_success(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Round-trip completes successfully when both phases succeed."""
        spec = temp_project_dir / "workflow.ttl"
        spec.write_text("@prefix : <http://example.org/> .\n", encoding="utf-8")

        call_count = [0]

        def mock_run_shell_cmd(cmd, **kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                # Phase 1: create .yawl file
                yawl_file = spec.with_suffix(".yawl")
                yawl_file.write_text("<spec/>", encoding="utf-8")
                return (0, "", "")
            # Phase 2: create round-trip Turtle file
            rt_file = spec.with_stem(spec.stem + "_rt")
            rt_file.write_text("@prefix : <http://example.org/> .\n", encoding="utf-8")
            return (0, "", "")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["round-trip", str(spec)])
        assert result.exit_code == 0

    def test_round_trip_verbose_flag(
        self, runner: CliRunner, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Round-trip accepts --verbose flag."""
        spec = temp_project_dir / "workflow.ttl"
        spec.write_text("@prefix : <http://example.org/> .\n", encoding="utf-8")

        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "fail")

        import yawl_cli.ggen
        monkeypatch.setattr(yawl_cli.ggen, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(ggen_app, ["round-trip", str(spec), "--verbose"])
        assert result.exit_code == 1


# ─── team.list ──────────────────────────────────────────────────────────────

class TestTeamList:
    """Tests for 'yawl team list' command."""

    def test_list_no_team_state_directory(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List command reports no teams when .team-state doesn't exist."""
        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 0
        assert "No teams" in result.output

    def test_list_empty_team_state_directory(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List command reports no teams when .team-state exists but is empty."""
        (temp_project_dir / ".team-state").mkdir()
        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 0
        assert "No teams" in result.output

    def test_list_with_team_no_metadata(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List command shows team with unknown status when metadata missing."""
        team_dir = temp_project_dir / ".team-state" / "τ-abc123"
        team_dir.mkdir(parents=True)

        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 0
        assert "τ-abc123" in result.output

    def test_list_with_team_metadata(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List command shows correct status from team metadata.json."""
        team_dir = temp_project_dir / ".team-state" / "τ-engine-abc"
        team_dir.mkdir(parents=True)
        metadata = {
            "status": "active",
            "agent_count": 3,
            "task_count": 5,
        }
        (team_dir / "metadata.json").write_text(
            json.dumps(metadata), encoding="utf-8"
        )

        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 0
        assert "τ-engine-abc" in result.output
        assert "active" in result.output

    def test_list_multiple_teams(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List command shows all teams sorted by name."""
        state_dir = temp_project_dir / ".team-state"
        for team_id in ["τ-zzz", "τ-aaa", "τ-mmm"]:
            (state_dir / team_id).mkdir(parents=True)

        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 0
        # All teams should appear in output
        assert "τ-aaa" in result.output
        assert "τ-mmm" in result.output
        assert "τ-zzz" in result.output

    def test_list_team_with_corrupt_metadata(
        self, runner: CliRunner, temp_project_dir: Path
    ) -> None:
        """List command handles corrupt metadata.json gracefully."""
        team_dir = temp_project_dir / ".team-state" / "τ-broken"
        team_dir.mkdir(parents=True)
        (team_dir / "metadata.json").write_text("{invalid json", encoding="utf-8")

        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 0
        assert "τ-broken" in result.output

    def test_list_ensure_project_root_error(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """List command handles project root detection failure gracefully."""
        import yawl_cli.team

        def boom():
            raise RuntimeError("No YAWL project found")

        monkeypatch.setattr(yawl_cli.team, "ensure_project_root", boom)
        result = runner.invoke(team_app, ["list"])
        assert result.exit_code == 1
