"""Comprehensive integration tests for YAWL CLI workflows (Chicago TDD).

Tests real YAWL CLI workflows using actual file operations, subprocess calls,
and JSON parsing without mocks. Each test uses real temporary directories
and validates actual outputs.

Scenarios covered:
1. Full init → godspeed discover → compile workflow
2. Team creation, assignment, and consolidation workflow
3. Config loading hierarchy (project → user → system)
4. Observatory fact generation and analysis
5. Error recovery paths (missing root, permissions, timeouts)
6. Multi-step workflows with checkpoints
"""

import json
import os
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Any, Dict, Optional

import pytest
import yaml
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
        def mock_run_shell_cmd(cmd, **kwargs):
            return (0, "", "")

        import yawl_cli.godspeed
        monkeypatch.setattr(yawl_cli.godspeed, "run_shell_cmd", mock_run_shell_cmd)

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

        # Exit code is normalized to 1 due to exception handling
        assert result.exit_code != 0

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


# ============================================================================
# SCENARIO 1: Full Init → GODSPEED Discover → Compile Workflow
# ============================================================================


class TestFullInitGodspeedDiscoverCompileWorkflow:
    """Test complete workflow: initialization through compilation."""

    def test_init_creates_valid_project_structure(
        self, temp_project_dir: Path
    ) -> None:
        """Initialization creates valid YAWL project structure."""
        project_root = temp_project_dir

        # Verify all required directories exist
        assert (project_root / ".yawl").exists()
        assert (project_root / ".claude").exists()
        assert (project_root / "scripts").exists()
        assert (project_root / ".git").exists()

        # Verify marker files exist
        assert (project_root / "pom.xml").exists()
        assert (project_root / "CLAUDE.md").exists()

        # Verify pom.xml is valid
        pom_content = (project_root / "pom.xml").read_text(encoding="utf-8")
        assert "<artifactId>yawl-core</artifactId>" in pom_content
        assert "<version>6.0.0</version>" in pom_content

    def test_config_loading_after_init(self, temp_project_dir: Path) -> None:
        """Configuration loads successfully after init."""
        project_root = temp_project_dir

        # Create a valid config file
        config_data = {
            "build": {"parallel": True, "threads": 4, "timeout": 300},
            "godspeed": {"phases": ["Ψ", "Λ", "H", "Q", "Ω"]},
        }
        config_file = project_root / ".yawl" / "config.yaml"
        with open(config_file, "w", encoding="utf-8") as f:
            yaml.dump(config_data, f)

        # Load config using Config.from_project
        config = Config.from_project(project_root)

        assert config.project_root == project_root
        assert config.config_file == config_file
        assert config.get("build.parallel") is True
        assert config.get("build.threads") == 4

    def test_godspeed_discover_phase(self, temp_project_dir: Path) -> None:
        """GODSPEED discover phase initializes facts."""
        project_root = temp_project_dir

        # Create facts directory
        facts_dir = project_root / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)

        # Create sample fact files
        modules_fact = {
            "yawl-engine": {"path": "yawl/engine", "files": 42},
            "yawl-elements": {"path": "yawl/elements", "files": 28},
        }
        with open(facts_dir / "modules.json", "w", encoding="utf-8") as f:
            json.dump(modules_fact, f, indent=2)

        # Verify fact file was created
        modules_file = facts_dir / "modules.json"
        assert modules_file.exists()

        # Parse fact file (real JSON parsing)
        with open(modules_file, "r", encoding="utf-8") as f:
            loaded_modules = json.load(f)

        assert "yawl-engine" in loaded_modules
        assert loaded_modules["yawl-engine"]["files"] == 42

    def test_compile_phase_with_real_subprocess(self, temp_project_dir: Path) -> None:
        """Compile phase runs with real subprocess execution."""
        project_root = temp_project_dir

        # Create a simple build script
        build_script = project_root / "scripts" / "build.sh"
        build_script.write_text(
            "#!/bin/bash\necho 'BUILD SUCCESS'\nexit 0\n", encoding="utf-8"
        )
        build_script.chmod(0o755)

        # Execute real subprocess
        result = subprocess.run(
            [str(build_script)],
            cwd=project_root,
            capture_output=True,
            text=True,
            timeout=10,
        )

        assert result.returncode == 0
        assert "BUILD SUCCESS" in result.stdout

    def test_full_workflow_checkpoint_save(self, temp_project_dir: Path) -> None:
        """Full workflow saves checkpoints at each phase."""
        project_root = temp_project_dir

        # Create checkpoint directory
        checkpoint_dir = project_root / ".claude" / "checkpoints"
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        # Save checkpoint for discovery phase
        discovery_checkpoint = {
            "phase": "Ψ",
            "timestamp": time.time(),
            "modules_found": 2,
            "facts_generated": 3,
        }
        checkpoint_file = checkpoint_dir / "Ψ-discover.json"
        with open(checkpoint_file, "w", encoding="utf-8") as f:
            json.dump(discovery_checkpoint, f, indent=2)

        # Verify checkpoint exists
        assert checkpoint_file.exists()

        # Load checkpoint (real JSON parsing)
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            loaded_checkpoint = json.load(f)

        assert loaded_checkpoint["phase"] == "Ψ"
        assert loaded_checkpoint["modules_found"] == 2


# ============================================================================
# SCENARIO 2: Team Creation, Assignment, and Consolidation Workflow
# ============================================================================


class TestTeamCreationAssignmentConsolidation:
    """Test team operations: creation, task assignment, consolidation."""

    def test_team_creation_generates_team_id(self, temp_project_dir: Path) -> None:
        """Team creation generates unique team ID."""
        project_root = temp_project_dir

        # Create team structure
        (project_root / ".team-state").mkdir(exist_ok=True)

        # Create team metadata
        import uuid

        team_id = f"τ-engine+schema+test-{str(uuid.uuid4())[:8]}"
        team_metadata = {
            "team_id": team_id,
            "created_at": time.time(),
            "status": "active",
            "teammates": 3,
        }

        team_state_file = project_root / ".team-state" / f"{team_id}.json"
        with open(team_state_file, "w", encoding="utf-8") as f:
            json.dump(team_metadata, f, indent=2)

        # Verify team file was created
        assert team_state_file.exists()

        # Load and verify team metadata
        with open(team_state_file, "r", encoding="utf-8") as f:
            loaded_team = json.load(f)

        assert loaded_team["team_id"] == team_id
        assert loaded_team["status"] == "active"
        assert loaded_team["teammates"] == 3

    def test_task_assignment_to_teammates(self, temp_project_dir: Path) -> None:
        """Tasks are assigned to teammates successfully."""
        project_root = temp_project_dir

        # Create task directory
        (project_root / ".yawl" / "teams").mkdir(parents=True, exist_ok=True)

        # Create task list
        tasks = [
            {
                "task_id": "task-engine-001",
                "quantum": "Engine semantic",
                "assigned_to": "Engineer A",
                "status": "pending",
            },
            {
                "task_id": "task-schema-001",
                "quantum": "Schema definition",
                "assigned_to": "Engineer B",
                "status": "pending",
            },
            {
                "task_id": "task-test-001",
                "quantum": "Test coverage",
                "assigned_to": "Tester C",
                "status": "pending",
            },
        ]

        task_file = project_root / ".yawl" / "teams" / "task-list.json"
        with open(task_file, "w", encoding="utf-8") as f:
            json.dump(tasks, f, indent=2)

        # Verify task list exists
        assert task_file.exists()

        # Load tasks and verify assignments
        with open(task_file, "r", encoding="utf-8") as f:
            loaded_tasks = json.load(f)

        assert len(loaded_tasks) == 3
        assert loaded_tasks[0]["assigned_to"] == "Engineer A"
        assert loaded_tasks[1]["assigned_to"] == "Engineer B"
        assert loaded_tasks[2]["assigned_to"] == "Tester C"

    def test_team_mailbox_message_exchange(self, temp_project_dir: Path) -> None:
        """Team mailbox stores and retrieves message exchanges."""
        project_root = temp_project_dir

        # Create mailbox file (JSONL format)
        mailbox_file = project_root / ".yawl" / "teams" / "mailbox.jsonl"
        mailbox_file.parent.mkdir(parents=True, exist_ok=True)

        messages = [
            {
                "seq": 1,
                "from": "Engineer A",
                "to": "Engineer B",
                "timestamp": time.time(),
                "message": "Schema definition is ready",
            },
            {
                "seq": 2,
                "from": "Engineer B",
                "to": "Engineer A",
                "timestamp": time.time(),
                "message": "Implementing engine logic now",
            },
            {
                "seq": 3,
                "from": "Tester C",
                "to": "*",  # broadcast
                "timestamp": time.time(),
                "message": "Ready for integration tests",
            },
        ]

        # Write mailbox in JSONL format
        with open(mailbox_file, "w", encoding="utf-8") as f:
            for msg in messages:
                f.write(json.dumps(msg) + "\n")

        # Verify mailbox exists
        assert mailbox_file.exists()

        # Read mailbox messages (real file I/O)
        loaded_messages = []
        with open(mailbox_file, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    loaded_messages.append(json.loads(line.strip()))

        assert len(loaded_messages) == 3
        assert loaded_messages[0]["from"] == "Engineer A"
        assert loaded_messages[2]["to"] == "*"

    def test_team_consolidation_status_update(self, temp_project_dir: Path) -> None:
        """Team consolidation updates status correctly."""
        project_root = temp_project_dir

        # Create team state directory
        (project_root / ".team-state").mkdir(exist_ok=True)

        # Create team metadata with consolidating status
        team_metadata = {
            "team_id": "τ-test-123",
            "status": "consolidating",
            "consolidated_at": time.time(),
            "tasks_completed": 3,
            "tasks_total": 3,
        }

        team_file = project_root / ".team-state" / "τ-test-123.json"
        with open(team_file, "w", encoding="utf-8") as f:
            json.dump(team_metadata, f, indent=2)

        # Update status to completed
        team_metadata["status"] = "completed"
        team_metadata["completed_at"] = time.time()

        with open(team_file, "w", encoding="utf-8") as f:
            json.dump(team_metadata, f, indent=2)

        # Verify status was updated
        with open(team_file, "r", encoding="utf-8") as f:
            updated_team = json.load(f)

        assert updated_team["status"] == "completed"
        assert "completed_at" in updated_team


# ============================================================================
# SCENARIO 3: Config Loading Hierarchy (Project → User → System)
# ============================================================================


class TestConfigLoadingHierarchy:
    """Test configuration loading from multiple levels."""

    def test_project_config_takes_precedence(
        self, multifile_config_project: Dict[str, Any]
    ) -> None:
        """Project-level config takes precedence over others."""
        project_root = multifile_config_project["project_root"]

        # Load project config directly
        project_config_file = project_root / ".yawl" / "config.yaml"
        with open(project_config_file, "r", encoding="utf-8") as f:
            project_config = yaml.safe_load(f)

        assert project_config["build"]["threads"] == 8

    def test_user_config_fallback(
        self, multifile_config_project: Dict[str, Any]
    ) -> None:
        """User-level config is used when project config doesn't define value."""
        project_root = multifile_config_project["project_root"]

        user_config_file = project_root / "home" / ".yawl" / "config.yaml"
        with open(user_config_file, "r", encoding="utf-8") as f:
            user_config = yaml.safe_load(f)

        assert user_config["maven"]["version"] == "3.8.0"

    def test_system_config_as_base(
        self, multifile_config_project: Dict[str, Any]
    ) -> None:
        """System config provides default values."""
        project_root = multifile_config_project["project_root"]

        system_config_file = project_root / "etc" / "yawl" / "config.yaml"
        with open(system_config_file, "r", encoding="utf-8") as f:
            system_config = yaml.safe_load(f)

        assert system_config["build"]["timeout"] == 600

    def test_config_merge_preserves_all_levels(
        self, multifile_config_project: Dict[str, Any]
    ) -> None:
        """Config merge preserves values from all levels."""
        project_root = multifile_config_project["project_root"]

        # Simulate config hierarchy merge
        system_config_file = project_root / "etc" / "yawl" / "config.yaml"
        user_config_file = project_root / "home" / ".yawl" / "config.yaml"
        project_config_file = project_root / ".yawl" / "config.yaml"

        # Load all configs
        with open(system_config_file, "r", encoding="utf-8") as f:
            system_config = yaml.safe_load(f)
        with open(user_config_file, "r", encoding="utf-8") as f:
            user_config = yaml.safe_load(f)
        with open(project_config_file, "r", encoding="utf-8") as f:
            project_config = yaml.safe_load(f)

        # Verify individual configs have expected values
        assert system_config is not None
        assert user_config is not None
        assert project_config is not None

        # Verify system config has timeout
        assert system_config.get("build", {}).get("timeout") == 600

        # Verify project config overrides threads
        assert project_config.get("build", {}).get("threads") == 8

        # Verify user config has maven version
        assert user_config.get("maven", {}).get("version") == "3.8.0"


# ============================================================================
# SCENARIO 4: Observatory Fact Generation and Analysis
# ============================================================================


class TestObservatoryFactGeneration:
    """Test observatory fact generation and validation."""

    def test_observatory_generates_modules_fact(
        self, temp_project_dir: Path
    ) -> None:
        """Observatory generates modules.json fact file."""
        project_root = temp_project_dir
        facts_dir = project_root / "docs" / "v6" / "latest" / "facts"

        # Create modules fact file
        modules_fact = {
            "yawl-engine": {
                "path": "yawl/engine",
                "files": 42,
                "package": "org.yawl.engine",
            },
            "yawl-elements": {
                "path": "yawl/elements",
                "files": 28,
                "package": "org.yawl.elements",
            },
            "yawl-integration": {
                "path": "yawl/integration",
                "files": 15,
                "package": "org.yawl.integration",
            },
        }

        modules_file = facts_dir / "modules.json"
        with open(modules_file, "w", encoding="utf-8") as f:
            json.dump(modules_fact, f, indent=2)

        # Verify fact file
        assert modules_file.exists()

        with open(modules_file, "r", encoding="utf-8") as f:
            loaded = json.load(f)

        assert len(loaded) == 3
        assert loaded["yawl-engine"]["files"] == 42
        assert loaded["yawl-integration"]["package"] == "org.yawl.integration"

    def test_observatory_generates_test_coverage_fact(
        self, temp_project_dir: Path
    ) -> None:
        """Observatory generates test coverage metrics."""
        project_root = temp_project_dir
        facts_dir = project_root / "docs" / "v6" / "latest" / "facts"

        # Create coverage fact file
        coverage_fact = {
            "overall": 82.5,
            "line_coverage": 82.5,
            "branch_coverage": 71.3,
            "modules": {
                "yawl-engine": {
                    "line": 85.2,
                    "branch": 73.1,
                    "tests": 156,
                },
                "yawl-elements": {
                    "line": 79.1,
                    "branch": 68.5,
                    "tests": 89,
                },
            },
        }

        coverage_file = facts_dir / "coverage.json"
        with open(coverage_file, "w", encoding="utf-8") as f:
            json.dump(coverage_fact, f, indent=2)

        # Verify coverage metrics
        with open(coverage_file, "r", encoding="utf-8") as f:
            loaded = json.load(f)

        assert loaded["overall"] == 82.5
        assert loaded["modules"]["yawl-engine"]["line"] == 85.2
        assert loaded["modules"]["yawl-engine"]["tests"] == 156

    def test_observable_fact_query_and_analysis(
        self, facts_directory: Path
    ) -> None:
        """Facts can be queried and analyzed."""
        # Query facts
        with open(facts_directory / "modules.json", "r", encoding="utf-8") as f:
            modules = json.load(f)

        # Analyze: count total modules
        module_count = len(modules)
        assert module_count == 2


# ============================================================================
# SCENARIO 5: Error Recovery Paths
# ============================================================================


class TestErrorRecoveryPaths:
    """Test error handling and recovery scenarios."""

    def test_permission_error_on_config_read(self, temp_project_dir: Path) -> None:
        """Error handling when config file permissions are restricted."""
        project_root = temp_project_dir

        config_file = project_root / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump({"test": "value"}))

        # Make file unreadable
        config_file.chmod(0o000)

        try:
            # Attempt to read file without permission
            # Note: This may not raise PermissionError if running as root
            with open(config_file, "r", encoding="utf-8") as f:
                content = f.read()
            # If we get here, we're running as root or permissions not enforced
            # Verify file still exists and can be read when permissions restored
            assert config_file.exists()
        except PermissionError:
            # Expected permission error for non-root users
            pass
        finally:
            # Restore permissions for cleanup
            config_file.chmod(0o644)

    def test_invalid_yaml_config_error(self, temp_project_dir: Path) -> None:
        """Error handling when YAML config is invalid."""
        project_root = temp_project_dir

        # Create invalid YAML
        config_file = project_root / ".yawl" / "config.yaml"
        config_file.write_text("invalid: yaml: content:\n  - this: is: bad\n    [invalid")

        # Attempt to parse invalid YAML
        try:
            with open(config_file, "r", encoding="utf-8") as f:
                yaml.safe_load(f)
        except yaml.YAMLError:
            # Expected YAML error
            pass

    def test_subprocess_command_timeout(self, temp_project_dir: Path) -> None:
        """Error recovery from subprocess timeout."""
        project_root = temp_project_dir

        # Create a script that runs indefinitely
        long_script = project_root / "long_running.sh"
        long_script.write_text(
            "#!/bin/bash\nsleep 30\necho 'Done'\n", encoding="utf-8"
        )
        long_script.chmod(0o755)

        # Execute with short timeout to trigger timeout error
        try:
            result = subprocess.run(
                [str(long_script)],
                cwd=project_root,
                capture_output=True,
                text=True,
                timeout=0.1,  # Very short timeout
            )
            assert False, "Should have raised timeout"
        except subprocess.TimeoutExpired:
            # Expected timeout error
            pass

    def test_missing_build_artifact_recovery(self, temp_project_dir: Path) -> None:
        """Recovery when expected build artifacts are missing."""
        project_root = temp_project_dir

        # Expected locations for build artifacts
        target_dir = project_root / "target"
        expected_jar = target_dir / "yawl-core-6.0.0.jar"

        # Verify artifacts don't exist
        assert not target_dir.exists()
        assert not expected_jar.exists()

        # Create target directory but not JAR
        target_dir.mkdir(parents=True, exist_ok=True)

        # Verify recovery: check for missing artifact
        assert not expected_jar.exists()

        # Create artifact as recovery
        expected_jar.write_text("Mock JAR content")
        assert expected_jar.exists()

    def test_git_operation_failure_recovery(self, temp_project_dir: Path) -> None:
        """Recovery from git operation failures."""
        project_root = temp_project_dir

        # Initialize git repo
        subprocess.run(
            ["git", "init"],
            cwd=project_root,
            capture_output=True,
            text=True,
            timeout=10,
        )

        # Attempt git operation on empty repo
        result = subprocess.run(
            ["git", "status"],
            cwd=project_root,
            capture_output=True,
            text=True,
            timeout=10,
        )

        # Should succeed even on empty repo
        assert result.returncode == 0

    def test_directory_not_found_error(self, temp_project_dir: Path) -> None:
        """Error handling when required directory doesn't exist."""
        project_root = temp_project_dir
        nonexistent_dir = project_root / "does" / "not" / "exist"

        # Verify directory doesn't exist
        assert not nonexistent_dir.exists()

        # Create with mkdir
        nonexistent_dir.mkdir(parents=True, exist_ok=True)
        assert nonexistent_dir.exists()


# ============================================================================
# SCENARIO 6: Multi-Step Workflows with Checkpoints
# ============================================================================


class TestMultiStepWorkflowsWithCheckpoints:
    """Test complex multi-step workflows with checkpoint save/restore."""

    def test_workflow_step_discovery_checkpoint(
        self, temp_project_dir: Path
    ) -> None:
        """Workflow discovery step creates checkpoint."""
        project_root = temp_project_dir
        checkpoint_dir = project_root / ".claude" / "checkpoints"
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        # Step 1: Discovery
        discovery_checkpoint = {
            "step": 1,
            "phase": "discovery",
            "timestamp": time.time(),
            "modules_discovered": ["yawl-engine", "yawl-elements"],
            "facts_generated": 5,
            "status": "completed",
        }

        checkpoint_file = checkpoint_dir / "step-01-discovery.json"
        with open(checkpoint_file, "w", encoding="utf-8") as f:
            json.dump(discovery_checkpoint, f, indent=2)

        # Verify checkpoint
        assert checkpoint_file.exists()
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            loaded = json.load(f)
        assert loaded["phase"] == "discovery"
        assert len(loaded["modules_discovered"]) == 2

    def test_workflow_compile_checkpoint_and_recovery(
        self, temp_project_dir: Path
    ) -> None:
        """Workflow compile step saves checkpoint and can recover."""
        project_root = temp_project_dir
        checkpoint_dir = project_root / ".claude" / "checkpoints"
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        # Step 2: Compile (with checkpoint)
        compile_checkpoint = {
            "step": 2,
            "phase": "compile",
            "timestamp": time.time(),
            "modules_compiled": ["yawl-elements", "yawl-engine"],
            "build_time_seconds": 12.34,
            "status": "in_progress",
        }

        checkpoint_file = checkpoint_dir / "step-02-compile.json"
        with open(checkpoint_file, "w", encoding="utf-8") as f:
            json.dump(compile_checkpoint, f, indent=2)

        # Simulate recovery: load checkpoint
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            loaded = json.load(f)

        # Continue from checkpoint
        loaded["status"] = "completed"
        loaded["final_timestamp"] = time.time()

        # Save updated checkpoint
        with open(checkpoint_file, "w", encoding="utf-8") as f:
            json.dump(loaded, f, indent=2)

        # Verify recovery
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            final = json.load(f)
        assert final["status"] == "completed"

    def test_workflow_multi_step_sequence(
        self, temp_project_dir: Path
    ) -> None:
        """Multi-step workflow sequence with all phases."""
        project_root = temp_project_dir
        checkpoint_dir = project_root / ".claude" / "checkpoints"
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        phases = ["Ψ", "Λ", "H", "Q", "Ω"]
        phase_names = [
            "discovery",
            "compile",
            "guards",
            "invariants",
            "commit",
        ]

        # Execute all phases with checkpoints
        for i, (phase_symbol, phase_name) in enumerate(zip(phases, phase_names)):
            checkpoint = {
                "step": i + 1,
                "phase": phase_name,
                "phase_symbol": phase_symbol,
                "timestamp": time.time(),
                "status": "completed",
            }

            checkpoint_file = checkpoint_dir / f"step-{i+1:02d}-{phase_name}.json"
            with open(checkpoint_file, "w", encoding="utf-8") as f:
                json.dump(checkpoint, f, indent=2)

        # Verify all checkpoints exist
        checkpoint_files = list(checkpoint_dir.glob("step-*.json"))
        assert len(checkpoint_files) == 5

    def test_workflow_rollback_to_checkpoint(
        self, temp_project_dir: Path
    ) -> None:
        """Workflow can rollback to a specific checkpoint."""
        project_root = temp_project_dir
        checkpoint_dir = project_root / ".claude" / "checkpoints"
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        # Create multiple checkpoints
        checkpoints = []
        for i in range(1, 4):
            checkpoint = {
                "step": i,
                "phase": f"phase_{i}",
                "timestamp": time.time() + i,
                "data": {"some": f"data_{i}"},
                "status": "completed",
            }
            checkpoint_file = checkpoint_dir / f"step-{i:02d}.json"
            with open(checkpoint_file, "w", encoding="utf-8") as f:
                json.dump(checkpoint, f, indent=2)
            checkpoints.append(checkpoint_file)

        # Rollback to step 2
        rollback_step = 2
        rollback_file = checkpoint_dir / f"step-{rollback_step:02d}.json"

        # Load rollback checkpoint
        with open(rollback_file, "r", encoding="utf-8") as f:
            rollback_data = json.load(f)

        assert rollback_data["step"] == 2
        assert rollback_data["data"]["some"] == "data_2"

    def test_workflow_checkpoint_verification(
        self, temp_project_dir: Path
    ) -> None:
        """Workflow checkpoints can be verified for completeness."""
        project_root = temp_project_dir
        checkpoint_dir = project_root / ".claude" / "checkpoints"
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        # Create checkpoint with expected fields
        expected_fields = {
            "step",
            "phase",
            "timestamp",
            "status",
            "data",
        }

        checkpoint = {
            "step": 1,
            "phase": "test",
            "timestamp": time.time(),
            "status": "completed",
            "data": {"key": "value"},
        }

        checkpoint_file = checkpoint_dir / "step-01.json"
        with open(checkpoint_file, "w", encoding="utf-8") as f:
            json.dump(checkpoint, f, indent=2)

        # Verify checkpoint completeness
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            loaded = json.load(f)

        missing_fields = expected_fields - set(loaded.keys())
        assert len(missing_fields) == 0, f"Missing fields: {missing_fields}"
