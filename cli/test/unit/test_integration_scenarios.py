"""Comprehensive integration tests for YAWL CLI (Chicago TDD, Real Objects).

End-to-end workflows, real file I/O, subprocess calls, multi-step scenarios.
"""

import json
import subprocess
from pathlib import Path
from typing import Any, Dict

import pytest
import yaml

from yawl_cli.utils import Config, ensure_project_root, load_facts, run_shell_cmd


class TestProjectInitialization:
    """Test project initialization workflows."""

    def test_initialize_project_structure(self, temp_project_dir: Path) -> None:
        """Initialize complete YAWL project structure."""
        # Verify marker files exist
        assert (temp_project_dir / "pom.xml").exists()
        assert (temp_project_dir / "CLAUDE.md").exists()

        # Verify required directories
        assert (temp_project_dir / ".yawl").exists()
        assert (temp_project_dir / "scripts").exists()
        assert (temp_project_dir / "src" / "main" / "java").exists()
        assert (temp_project_dir / "src" / "test" / "java").exists()

    def test_initialize_config_files(self, temp_project_dir: Path) -> None:
        """Initialize default config files."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {
            "build": {"threads": 8, "parallel": True},
            "maven": {"profiles": ["analysis"]},
        }
        config.save()

        config_file = temp_project_dir / ".yawl" / "config.yaml"
        assert config_file.exists()

        # Reload and verify
        config2 = Config.from_project(temp_project_dir)
        assert config2.get("build.threads") == 8
        assert config2.get("build.parallel") is True

    def test_initialize_facts_directory(self, temp_project_dir: Path) -> None:
        """Initialize facts directory structure."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)

        # Create sample fact files
        modules = {
            "yawl-engine": {"path": "yawl/engine", "files": 42},
            "yawl-elements": {"path": "yawl/elements", "files": 28},
        }
        with open(facts_dir / "modules.json", "w") as f:
            json.dump(modules, f)

        # Verify facts can be loaded
        loaded = load_facts(facts_dir, "modules.json")
        assert loaded["yawl-engine"]["path"] == "yawl/engine"

    def test_project_root_detection_workflow(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Detect project root from nested subdirectory."""
        # Create nested directory structure
        deep_dir = temp_project_dir / "a" / "b" / "c" / "d"
        deep_dir.mkdir(parents=True)

        # Change to deep directory
        monkeypatch.chdir(deep_dir)

        # Project root should be found
        root = ensure_project_root()

        assert root == temp_project_dir


class TestConfigurationWorkflows:
    """Test configuration management workflows."""

    def test_load_merge_save_config(self, temp_project_dir: Path) -> None:
        """Load, modify, and save configuration."""
        # Create initial config
        initial_config = {"build": {"threads": 4}, "test": {"enabled": True}}
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.parent.mkdir(parents=True, exist_ok=True)

        with open(config_file, "w") as f:
            yaml.dump(initial_config, f)

        # Load and modify
        config = Config.from_project(temp_project_dir)
        assert config.get("build.threads") == 4

        config.set("build.threads", 8)
        config.set("build.parallel", True)
        config.save()

        # Reload and verify changes persisted
        config2 = Config.from_project(temp_project_dir)
        assert config2.get("build.threads") == 8
        assert config2.get("build.parallel") is True

    def test_deep_config_merge_hierarchy(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Test configuration hierarchy merging."""
        # Create project config
        project_config = {
            "build": {"threads": 8, "parallel": False},
            "maven": {"profiles": ["analysis"]},
        }
        project_file = temp_project_dir / ".yawl" / "config.yaml"
        project_file.parent.mkdir(parents=True, exist_ok=True)

        with open(project_file, "w") as f:
            yaml.dump(project_config, f)

        # Load config
        config = Config.from_project(temp_project_dir)

        # Verify merged configuration
        assert config.get("build.threads") == 8
        assert config.get("build.parallel") is False
        assert config.get("maven.profiles") == ["analysis"]

    def test_config_with_many_nested_levels(self, temp_project_dir: Path) -> None:
        """Handle deeply nested configuration."""
        data = {
            "level1": {
                "level2": {
                    "level3": {
                        "level4": {
                            "level5": {
                                "value": "deep"
                            }
                        }
                    }
                }
            }
        }
        config = Config(project_root=temp_project_dir)
        config.config_data = data
        config.save()

        # Reload and verify
        config2 = Config.from_project(temp_project_dir)
        assert config2.get("level1.level2.level3.level4.level5.value") == "deep"

    def test_config_dot_notation_get_set_cycle(self, temp_project_dir: Path) -> None:
        """Get and set values using dot notation."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        # Set nested values
        config.set("module.engine.threads", 8)
        config.set("module.engine.timeout", 600)
        config.set("module.test.coverage", 85)

        # Verify nested structure created
        assert config.get("module.engine.threads") == 8
        assert config.get("module.engine.timeout") == 600
        assert config.get("module.test.coverage") == 85

        # Verify raw structure
        assert "module" in config.config_data
        assert "engine" in config.config_data["module"]
        assert "test" in config.config_data["module"]


class TestFactsLoadingWorkflow:
    """Test facts loading and caching workflows."""

    def test_load_all_fact_files(self, facts_directory: Path) -> None:
        """Load multiple fact files."""
        modules = load_facts(facts_directory, "modules.json")
        gates = load_facts(facts_directory, "gates.json")
        tests = load_facts(facts_directory, "tests.json")

        assert isinstance(modules, dict)
        assert isinstance(gates, dict)
        assert isinstance(tests, dict)

        assert "yawl-engine" in modules
        assert "total" in tests

    def test_facts_provide_project_context(self, temp_project_dir: Path) -> None:
        """Facts provide complete project context."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)

        # Create comprehensive facts
        modules = {
            "yawl-engine": {"path": "yawl/engine", "files": 42},
            "yawl-elements": {"path": "yawl/elements", "files": 28},
            "yawl-integration": {"path": "yawl/integration", "files": 15},
        }
        gates = {
            "yawl-engine": {"unit": 156, "integration": 89},
            "yawl-elements": {"unit": 95, "integration": 12},
        }
        tests = {"total": 352, "coverage": 82.5}

        with open(facts_dir / "modules.json", "w") as f:
            json.dump(modules, f)
        with open(facts_dir / "gates.json", "w") as f:
            json.dump(gates, f)
        with open(facts_dir / "tests.json", "w") as f:
            json.dump(tests, f)

        # Load and verify complete context
        modules_data = load_facts(facts_dir, "modules.json")
        assert len(modules_data) == 3

        gates_data = load_facts(facts_dir, "gates.json")
        assert "yawl-engine" in gates_data

        tests_data = load_facts(facts_dir, "tests.json")
        assert tests_data["total"] == 352


class TestShellCommandWorkflows:
    """Test shell command execution workflows."""

    def test_create_build_directory_structure(self, temp_project_dir: Path) -> None:
        """Create Maven build directory structure using shell."""
        # Create src directories
        exit_code, _, _ = run_shell_cmd(
            ["mkdir", "-p", "src/main/java/org/yawl"],
            cwd=temp_project_dir,
        )
        assert exit_code == 0

        # Create test directories
        exit_code, _, _ = run_shell_cmd(
            ["mkdir", "-p", "src/test/java/org/yawl"],
            cwd=temp_project_dir,
        )
        assert exit_code == 0

        # Create target directory
        exit_code, _, _ = run_shell_cmd(
            ["mkdir", "-p", "target"],
            cwd=temp_project_dir,
        )
        assert exit_code == 0

        # Verify structure
        assert (temp_project_dir / "src" / "main" / "java" / "org" / "yawl").exists()
        assert (temp_project_dir / "src" / "test" / "java" / "org" / "yawl").exists()
        assert (temp_project_dir / "target").exists()

    def test_find_files_workflow(self, temp_project_dir: Path) -> None:
        """Find files using shell command."""
        # Create test files
        (temp_project_dir / "file1.java").write_text("// Java file")
        (temp_project_dir / "file2.java").write_text("// Java file")
        (temp_project_dir / "readme.txt").write_text("README")

        # Find Java files
        exit_code, stdout, _ = run_shell_cmd(
            ["find", ".", "-name", "*.java"],
            cwd=temp_project_dir,
        )

        assert exit_code == 0
        assert "file1.java" in stdout
        assert "file2.java" in stdout
        assert "readme.txt" not in stdout

    def test_count_lines_in_source_files(self, temp_project_dir: Path) -> None:
        """Count lines in source files."""
        java_file = temp_project_dir / "Test.java"
        java_file.write_text("class Test {\n  public void method() {\n    // code\n  }\n}\n")

        # Count lines
        exit_code, stdout, _ = run_shell_cmd(
            ["wc", "-l", "Test.java"],
            cwd=temp_project_dir,
        )

        assert exit_code == 0
        assert "5" in stdout


class TestGitIntegration:
    """Test git integration workflows."""

    def test_initialize_git_repository(self, git_initialized_project: Path) -> None:
        """Initialize git repository."""
        git_dir = git_initialized_project / ".git"
        assert git_dir.exists()
        assert git_dir.is_dir()

    def test_git_status_in_project(self, git_initialized_project: Path) -> None:
        """Check git status in project."""
        # Create a file
        test_file = git_initialized_project / "test.txt"
        test_file.write_text("test content")

        # Run git status
        exit_code, stdout, _ = run_shell_cmd(
            ["git", "status", "--short"],
            cwd=git_initialized_project,
        )

        assert exit_code == 0
        assert "test.txt" in stdout

    def test_git_add_commit_workflow(self, git_initialized_project: Path) -> None:
        """Add and commit files to git."""
        # Create a file
        test_file = git_initialized_project / "test.txt"
        test_file.write_text("test content")

        # Add file
        exit_code, _, _ = run_shell_cmd(
            ["git", "add", "test.txt"],
            cwd=git_initialized_project,
        )
        assert exit_code == 0

        # Commit
        exit_code, _, _ = run_shell_cmd(
            ["git", "commit", "-m", "Initial commit"],
            cwd=git_initialized_project,
        )
        assert exit_code == 0

    def test_git_branch_detection(self, git_initialized_project: Path) -> None:
        """Detect current git branch."""
        exit_code, stdout, _ = run_shell_cmd(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=git_initialized_project,
        )

        assert exit_code == 0
        # Default branch is usually 'master' or 'main'
        assert "master" in stdout or "main" in stdout


class TestEndToEndWorkflows:
    """Test complete end-to-end workflows."""

    def test_complete_project_setup_workflow(self, temp_project_dir: Path) -> None:
        """Complete workflow: init → config → facts → verify."""
        # Step 1: Create project structure
        assert (temp_project_dir / "pom.xml").exists()
        assert (temp_project_dir / "CLAUDE.md").exists()

        # Step 2: Load config
        config = Config.from_project(temp_project_dir)
        assert config.project_root == temp_project_dir

        # Step 3: Initialize facts directory
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)

        # Step 4: Create sample facts
        modules = {"yawl-engine": {"path": "yawl/engine"}}
        with open(facts_dir / "modules.json", "w") as f:
            json.dump(modules, f)

        # Step 5: Load and verify facts
        loaded = load_facts(facts_dir, "modules.json")
        assert "yawl-engine" in loaded

        # Step 6: Save updated config
        config.set("facts.directory", str(facts_dir))
        config.save()

        # Step 7: Reload and verify
        config2 = Config.from_project(temp_project_dir)
        assert config2.get("facts.directory") == str(facts_dir)

    def test_configuration_update_workflow(self, temp_project_dir: Path) -> None:
        """Workflow: create → load → modify → save → reload."""
        # Create initial config
        config1 = Config(project_root=temp_project_dir)
        config1.config_data = {
            "build": {"threads": 4},
            "maven": {"version": "3.8.0"},
        }
        config1.save()

        # Load and verify
        config2 = Config.from_project(temp_project_dir)
        assert config2.get("build.threads") == 4

        # Modify
        config2.set("build.threads", 8)
        config2.set("build.parallel", True)
        config2.set("test.coverage_target", 85)
        config2.save()

        # Reload and verify all changes persisted
        config3 = Config.from_project(temp_project_dir)
        assert config3.get("build.threads") == 8
        assert config3.get("build.parallel") is True
        assert config3.get("test.coverage_target") == 85
        assert config3.get("maven.version") == "3.8.0"  # Original preserved

    def test_multistep_build_workflow(self, temp_project_dir: Path) -> None:
        """Workflow: prepare → compile → test → package."""
        # Step 1: Prepare (create directories)
        exit_code, _, _ = run_shell_cmd(
            ["mkdir", "-p", "target/classes", "target/test-classes"],
            cwd=temp_project_dir,
        )
        assert exit_code == 0

        # Step 2: Create Java files
        (temp_project_dir / "Test.java").write_text("class Test {}")

        # Step 3: Verify files exist
        exit_code, stdout, _ = run_shell_cmd(
            ["find", ".", "-name", "*.java"],
            cwd=temp_project_dir,
        )
        assert exit_code == 0
        assert "Test.java" in stdout

        # Step 4: Count source files
        exit_code, stdout, _ = run_shell_cmd(
            ["sh", "-c", "find . -name '*.java' | wc -l"],
            cwd=temp_project_dir,
        )
        assert exit_code == 0
        assert "1" in stdout.strip()

    def test_complex_configuration_scenario(self, temp_project_dir: Path) -> None:
        """Complex scenario: multiple config levels, facts, and workflow."""
        # Create multi-level config
        config = Config(project_root=temp_project_dir)
        config.config_data = {
            "build": {
                "default": {"threads": 4, "timeout": 300},
                "fast": {"threads": 8, "timeout": 600},
                "slow": {"threads": 2, "timeout": 1800},
            },
            "modules": {
                "engine": {"enabled": True, "tests": True},
                "elements": {"enabled": True, "tests": False},
            },
            "integration": {
                "mcp": {"enabled": True, "version": "1.0.0"},
                "a2a": {"enabled": False},
            },
        }
        config.save()

        # Reload and verify complex structure
        config2 = Config.from_project(temp_project_dir)

        assert config2.get("build.default.threads") == 4
        assert config2.get("build.fast.threads") == 8
        assert config2.get("build.slow.timeout") == 1800

        assert config2.get("modules.engine.enabled") is True
        assert config2.get("modules.elements.tests") is False

        assert config2.get("integration.mcp.enabled") is True
        assert config2.get("integration.a2a.enabled") is False

        # Update specific section
        config2.set("build.fast.threads", 16)
        config2.save()

        # Verify update persisted
        config3 = Config.from_project(temp_project_dir)
        assert config3.get("build.fast.threads") == 16
        assert config3.get("build.default.threads") == 4  # Other sections unchanged
