"""Integration tests for YAWL CLI workflows (Chicago TDD)."""

from pathlib import Path

import pytest
from typer.testing import CliRunner

from yawl_cli.build import build_app
from yawl_cli.godspeed import godspeed_app
from yawl_cli.observatory import observatory_app
from yawl_cli.utils import Config, ensure_project_root


class TestCLIWorkflows:
    """Test end-to-end CLI workflows."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_config_loads_from_project(self, temp_project_dir: Path) -> None:
        """Configuration loads from project directory."""
        config = Config.from_project(temp_project_dir)

        assert config.project_root == temp_project_dir
        assert config.facts_dir == temp_project_dir / "docs/v6/latest/facts"

    def test_project_root_detection(self, temp_project_dir: Path, monkeypatch) -> None:
        """Project root detection works from within project."""
        monkeypatch.chdir(temp_project_dir)

        root = ensure_project_root()

        assert root == temp_project_dir

    def test_config_dot_notation_access(self, config_with_yaml: Config) -> None:
        """Configuration supports dot notation access."""
        parallel = config_with_yaml.get("build.parallel")
        threads = config_with_yaml.get("build.threads")

        assert parallel is True
        assert threads == 4

    def test_facts_loading_workflow(self, facts_directory: Path) -> None:
        """Facts can be loaded and accessed."""
        from yawl_cli.utils import load_facts

        modules = load_facts(facts_directory, "modules.json")

        assert isinstance(modules, dict)
        assert "yawl-engine" in modules


class TestBuildWorkflow:
    """Test build command workflow."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_build_compile_command(self, runner: CliRunner, monkeypatch) -> None:
        """Build compile command executes."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "Build successful", "")

        import yawl_cli.build
        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code == 0

    def test_build_all_command(self, runner: CliRunner, monkeypatch) -> None:
        """Build all command runs full sequence."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.build
        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["all"])

        assert result.exit_code == 0

    def test_build_with_timeout(self, runner: CliRunner, monkeypatch) -> None:
        """Build command respects timeout option."""
        captured_kwargs = []

        def mock_run_shell_cmd(cmd, **kwargs):
            captured_kwargs.append(kwargs)
            return (0, "", "")

        import yawl_cli.build
        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile", "--timeout", "1200"])

        assert result.exit_code == 0
        assert captured_kwargs[0]["timeout"] == 1200


class TestObservatoryWorkflow:
    """Test observatory workflow."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_observatory_generate_workflow(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Observatory generate workflow."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.observatory
        monkeypatch.setattr(yawl_cli.observatory, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(observatory_app, ["generate"])

        assert result.exit_code == 0

    def test_observatory_list_workflow(self, runner: CliRunner) -> None:
        """Observatory list facts workflow."""
        result = runner.invoke(observatory_app, ["list-facts"])

        # Will show "No facts found" or list facts
        assert result.exit_code == 0

    def test_observatory_search_workflow(self, runner: CliRunner) -> None:
        """Observatory search workflow."""
        result = runner.invoke(observatory_app, ["search", "test-pattern"])

        assert result.exit_code == 0


class TestGodspeedWorkflow:
    """Test GODSPEED phase workflow."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_godspeed_discover_phase(self, runner: CliRunner, monkeypatch) -> None:
        """GODSPEED discover phase workflow."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["discover"])

        assert result.exit_code == 0

    def test_godspeed_compile_phase(self, runner: CliRunner, monkeypatch) -> None:
        """GODSPEED compile phase workflow."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["compile"])

        assert result.exit_code == 0

    def test_godspeed_guard_phase(self, runner: CliRunner, monkeypatch) -> None:
        """GODSPEED guard phase workflow."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["guard"])

        assert result.exit_code == 0

    def test_godspeed_verify_phase(self, runner: CliRunner, monkeypatch) -> None:
        """GODSPEED verify phase workflow."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["verify"])

        assert result.exit_code == 0

    def test_godspeed_full_circuit(self, runner: CliRunner, monkeypatch) -> None:
        """GODSPEED full circuit workflow."""
        result = runner.invoke(godspeed_app, ["full"])

        assert result.exit_code == 0


class TestErrorScenarios:
    """Test CLI error scenarios."""

    @pytest.fixture
    def runner(self) -> CliRunner:
        """Create a Typer CLI runner."""
        return CliRunner()

    def test_build_failure_propagates_exit_code(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """Build failure returns correct exit code."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (1, "", "Build failed")

        import yawl_cli.build
        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code == 1

    def test_godspeed_phase_failure_propagates(
        self, runner: CliRunner, monkeypatch
    ) -> None:
        """GODSPEED phase failure propagates."""
        def mock_run_shell_cmd(cmd, **kwargs):
            return (2, "", "Guard violations")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(godspeed_app, ["guard"])

        assert result.exit_code == 2

    def test_missing_script_error(self, runner: CliRunner, monkeypatch) -> None:
        """Missing script shows helpful error."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Script not found: scripts/dx.sh")

        import yawl_cli.build
        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code != 0

    def test_timeout_error_handling(self, runner: CliRunner, monkeypatch) -> None:
        """Timeout error is handled."""
        def mock_run_shell_cmd(cmd, **kwargs):
            raise RuntimeError("Command timed out after 600 seconds")

        import yawl_cli.build
        monkeypatch.setattr(yawl_cli.build, "run_shell_cmd", mock_run_shell_cmd)

        result = runner.invoke(build_app, ["compile"])

        assert result.exit_code != 0


class TestConfigurationPrecedence:
    """Test configuration loading precedence."""

    def test_config_get_with_defaults(self, config_with_yaml: Config) -> None:
        """Config get uses defaults correctly."""
        value = config_with_yaml.get("nonexistent.key", default="default_value")

        assert value == "default_value"

    def test_config_set_and_get(self, config_with_yaml: Config) -> None:
        """Config set and get work together."""
        config_with_yaml.set("test.key", "test_value")
        value = config_with_yaml.get("test.key")

        assert value == "test_value"

    def test_config_save_and_reload(self, temp_project_dir: Path) -> None:
        """Config can be saved and reloaded."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {"test": {"value": 42}}

        config.save()

        # Reload
        config2 = Config.from_project(temp_project_dir)
        assert config2.get("test.value") == 42
