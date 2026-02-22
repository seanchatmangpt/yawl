"""Unit tests for YAWL CLI team operations (Chicago TDD)."""

import pytest
from typer.testing import CliRunner

from yawl_cli.team import team_app


class TestTeamCommands:
    """Test team management commands."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_create_team_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Create team command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Team created", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["create", "engine", "schema", "test"])

        assert result.exit_code == 0

    def test_create_team_with_names(self, runner: CliRunner, monkeypatch) -> None:
        """Create team with custom teammate names."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["create", "engine", "schema"])

        assert result.exit_code == 0

    def test_list_teams_command(self, runner: CliRunner, monkeypatch) -> None:
        """List teams command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Team list retrieved", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["list"])

        assert result.exit_code == 0

    def test_resume_team_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Resume team command succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Team resumed", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["resume", "team-id-123"])

        assert result.exit_code == 0

    def test_resume_team_not_found(self, runner: CliRunner, monkeypatch) -> None:
        """Resume team fails when team not found."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Team not found")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["resume", "nonexistent-team"])

        assert result.exit_code == 1

    def test_message_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Message command sends message to teammate."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Message sent", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(
            team_app,
            ["message", "team-id", "teammate-name", "Hello teammate"]
        )

        assert result.exit_code == 0

    def test_status_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Status command gets team status."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Team status retrieved", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["status", "team-id-123"])

        assert result.exit_code == 0

    def test_consolidate_command_success(self, runner: CliRunner, monkeypatch) -> None:
        """Consolidate command finalizes team work."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Team consolidated", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(team_app, ["consolidate", "team-id-123"])

        assert result.exit_code == 0


class TestTeamErrorHandling:
    """Test team command error handling."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_create_team_requires_quantums(self, runner: CliRunner) -> None:
        """Create team requires quantum arguments."""
        result = runner.invoke(team_app, ["create"])

        assert result.exit_code != 0

    def test_resume_team_requires_id(self, runner: CliRunner) -> None:
        """Resume team requires team ID argument."""
        result = runner.invoke(team_app, ["resume"])

        assert result.exit_code != 0

    def test_status_requires_team_id(self, runner: CliRunner) -> None:
        """Status command requires team ID argument."""
        result = runner.invoke(team_app, ["status"])

        assert result.exit_code != 0

    def test_consolidate_requires_team_id(self, runner: CliRunner) -> None:
        """Consolidate command requires team ID argument."""
        result = runner.invoke(team_app, ["consolidate"])

        assert result.exit_code != 0

    def test_message_requires_arguments(self, runner: CliRunner) -> None:
        """Message command requires all arguments."""
        result = runner.invoke(team_app, ["message"])

        assert result.exit_code != 0

    def test_message_communication_timeout(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Message handles communication timeout."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Message timeout: Teammate did not respond within 15 minutes")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(
            team_app,
            ["message", "team-id", "teammate", "status check"]
        )

        assert result.exit_code != 0


class TestTeamWorkflows:
    """Test team-based workflows."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_create_and_list_teams(self, runner: CliRunner, monkeypatch) -> None:
        """Create team then list all teams."""
        def mock_run_shell_cmd(cmd, **kwargs):
            if "create" in cmd:
                return (0, "Team created: team-123", "")
            else:
                return (0, "Team list retrieved", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        # Create team
        result1 = runner.invoke(team_app, ["create", "engine", "schema"])
        assert result1.exit_code == 0

        # List teams
        result2 = runner.invoke(team_app, ["list"])
        assert result2.exit_code == 0

    def test_create_resume_and_consolidate(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Create, resume, and consolidate team workflow."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        # Create team
        result1 = runner.invoke(team_app, ["create", "engine"])
        assert result1.exit_code == 0

        # Resume team
        result2 = runner.invoke(team_app, ["resume", "team-123"])
        assert result2.exit_code == 0

        # Consolidate team
        result3 = runner.invoke(team_app, ["consolidate", "team-123"])
        assert result3.exit_code == 0

    def test_send_message_in_team(self, runner: CliRunner, monkeypatch) -> None:
        """Send message to team member."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Message sent", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(
            team_app,
            ["message", "team-123", "Engineer-A", "Working on engine module"]
        )

        assert result.exit_code == 0

    def test_check_team_status_multiple_times(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Check team status multiple times."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Status: in_progress, 2/3 tasks complete", "")

        import yawl_cli.team
        monkeypatch.setattr(yawl_cli.team, "run_shell_cmd", mock_run_shell_cmd)

        for _ in range(3):
            result = runner.invoke(team_app, ["status", "team-123"])
            assert result.exit_code == 0
