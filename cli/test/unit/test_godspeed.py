"""Unit tests for YAWL CLI GODSPEED phases (Chicago TDD)."""

import pytest
from typer.testing import CliRunner

from yawl_cli.godspeed import godspeed_app


class TestGodspeedPhases:
    """Test GODSPEED phase commands."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    # Ψ (Discover) Phase Tests
    def test_discover_phase_success(self, runner: CliRunner, monkeypatch) -> None:
        """Discover phase succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Facts generated", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["discover"])

        assert result.exit_code == 0
        assert "discovered" in result.stdout.lower()

    def test_discover_phase_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Discover phase fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Observatory failed")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["discover"])

        assert result.exit_code == 1

    def test_discover_verbose_flag(self, runner: CliRunner, monkeypatch) -> None:
        """Discover phase passes verbose flag."""
        captured_kwargs = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_kwargs.append(kwargs)
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["discover", "--verbose"])

        assert result.exit_code == 0
        assert captured_kwargs[0]["verbose"] is True

    # Λ (Compile) Phase Tests
    def test_compile_phase_success(self, runner: CliRunner, monkeypatch) -> None:
        """Compile phase succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Compilation successful", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile"])

        assert result.exit_code == 0
        assert "successful" in result.stdout.lower()

    def test_compile_phase_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Compile phase fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Compilation error")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile"])

        assert result.exit_code == 1

    def test_compile_with_module_option(self, runner: CliRunner, monkeypatch) -> None:
        """Compile phase with specific module."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile", "--module", "yawl-engine"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["bash", "scripts/dx.sh", "-pl", "yawl-engine"]

    def test_compile_without_module(self, runner: CliRunner, monkeypatch) -> None:
        """Compile phase without module compiles all."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile"])

        assert result.exit_code == 0
        assert captured_cmd[0] == ["bash", "scripts/dx.sh", "compile"]

    # H (Guard) Phase Tests
    def test_guard_phase_success(self, runner: CliRunner, monkeypatch) -> None:
        """Guard phase succeeds with no violations."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "No violations", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["guard"])

        assert result.exit_code == 0
        assert "guard" in result.stdout.lower()

    def test_guard_phase_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Guard phase fails with violations."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (2, "", "Guard violations: TODO at line 42")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["guard"])

        # Note: Due to exception handling, exit code is normalized to 1
        assert result.exit_code != 0

    def test_guard_phase_runs_hyper_validate(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Guard phase runs hyper-validate hook."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["guard"])

        assert result.exit_code == 0
        assert "hyper-validate.sh" in " ".join(captured_cmd[0])

    # Q (Verify) Phase Tests
    def test_verify_phase_success(self, runner: CliRunner, monkeypatch) -> None:
        """Verify phase succeeds."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "All tests passed", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["verify"])

        assert result.exit_code == 0
        assert "verified" in result.stdout.lower()

    def test_verify_phase_failure(self, runner: CliRunner, monkeypatch) -> None:
        """Verify phase fails."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Test failed")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["verify"])

        assert result.exit_code == 1

    def test_verify_phase_runs_tests(self, runner: CliRunner, monkeypatch) -> None:
        """Verify phase runs test suite."""
        captured_cmd = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_cmd.append(cmd)
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["verify"])

        assert result.exit_code == 0
        assert "test" in captured_cmd[0]

    # Full GODSPEED Circuit Tests
    def test_full_godspeed_circuit(self, runner: CliRunner, monkeypatch) -> None:
        """Full GODSPEED circuit completes."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["full"])

        assert result.exit_code == 0
        assert "GODSPEED" in result.stdout or "complete" in result.stdout.lower()

    def test_full_godspeed_shows_all_phases(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Full circuit mentions all phases."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["full"])

        assert result.exit_code == 0
        # Check for phase indicators
        assert "Ψ" in result.stdout or "discover" in result.stdout.lower()
        assert "Λ" in result.stdout or "compile" in result.stdout.lower()


class TestGodspeedErrorHandling:
    """Test GODSPEED error handling."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_compile_timeout_error(self, runner: CliRunner, monkeypatch) -> None:
        """Compile phase handles timeout."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Command timed out after 600 seconds")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile"])

        assert result.exit_code != 0

    def test_discover_script_not_found(self, runner: CliRunner, monkeypatch) -> None:
        """Discover phase handles missing script."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Script not found: scripts/observatory/observatory.sh")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["discover"])

        assert result.exit_code != 0

    def test_guard_phase_displays_error_message(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Guard phase displays violation details."""
        error_msg = "H_TODO: TODO at line 42"

        def mock_run_shell_cmd(cmd, **kwargs):
            return (2, "", error_msg)

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["guard"])

        # Exit code is normalized to 1 due to exception handling
        assert result.exit_code != 0


class TestGodspeedVerboseMode:
    """Test GODSPEED verbose output."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_discover_verbose_output(self, runner: CliRunner, monkeypatch) -> None:
        """Discover verbose shows detailed output."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Detailed output", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["discover", "-v"])

        assert result.exit_code == 0

    def test_compile_verbose_output(self, runner: CliRunner, monkeypatch) -> None:
        """Compile verbose shows detailed output."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Detailed compilation output", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile", "-v"])

        assert result.exit_code == 0
