"""Pytest configuration and shared fixtures for YAWL CLI tests."""

import json
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Generator, Dict, Any

import pytest
import yaml

from yawl_cli.utils import Config


@pytest.fixture
def temp_project_dir() -> Generator[Path, None, None]:
    """Create a temporary YAWL project directory with complete structure.

    This fixture creates a real project directory with all necessary files
    and subdirectories for testing. All paths are real file system objects.
    """
    with tempfile.TemporaryDirectory() as tmpdir:
        project_root = Path(tmpdir)

        # Create project structure with real directories
        (project_root / ".yawl").mkdir(parents=True, exist_ok=True)
        (project_root / "docs" / "v6" / "latest" / "facts").mkdir(
            parents=True, exist_ok=True
        )
        (project_root / ".claude" / "hooks").mkdir(parents=True, exist_ok=True)
        (project_root / ".claude" / "rules").mkdir(parents=True, exist_ok=True)
        (project_root / ".claude" / "reports").mkdir(parents=True, exist_ok=True)

        # Create pom.xml marker (real Maven file)
        (project_root / "pom.xml").write_text(
            """<?xml version="1.0"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.yawl</groupId>
  <artifactId>yawl-core</artifactId>
  <version>6.0.0</version>
</project>
""",
            encoding="utf-8"
        )

        # Create CLAUDE.md marker
        (project_root / "CLAUDE.md").write_text("# YAWL v6.0.0\n", encoding="utf-8")

        # Create basic scripts directory structure (real executable scripts)
        (project_root / "scripts").mkdir(exist_ok=True)
        dx_script = project_root / "scripts" / "dx.sh"
        dx_script.write_text("#!/bin/bash\nexit 0\n", encoding="utf-8")
        dx_script.chmod(0o755)

        observatory_dir = project_root / "scripts" / "observatory"
        observatory_dir.mkdir(exist_ok=True)
        observatory_script = observatory_dir / "observatory.sh"
        observatory_script.write_text("#!/bin/bash\nexit 0\n", encoding="utf-8")
        observatory_script.chmod(0o755)

        # Create src directory structure
        (project_root / "src" / "main" / "java").mkdir(parents=True, exist_ok=True)
        (project_root / "src" / "test" / "java").mkdir(parents=True, exist_ok=True)

        # Create .git directory for git operations
        (project_root / ".git").mkdir(exist_ok=True)

        yield project_root


@pytest.fixture
def valid_config_file(temp_project_dir: Path) -> Path:
    """Create a valid YAML config file."""
    config = {
        "build": {
            "parallel": True,
            "threads": 4,
            "timeout": 300,
        },
        "maven": {
            "profiles": ["analysis"],
        },
        "godspeed": {
            "phases": ["Ψ", "Λ", "H", "Q", "Ω"],
        },
    }
    config_file = temp_project_dir / ".yawl" / "config.yaml"
    with open(config_file, "w") as f:
        yaml.dump(config, f)
    return config_file


@pytest.fixture
def invalid_yaml_file(temp_project_dir: Path) -> Path:
    """Create an invalid YAML file."""
    invalid_file = temp_project_dir / ".yawl" / "invalid.yaml"
    invalid_file.write_text("invalid: yaml: content:\n  - this: is: bad\n    [invalid")
    return invalid_file


@pytest.fixture
def facts_directory(temp_project_dir: Path) -> Path:
    """Create a facts directory with sample fact files."""
    facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
    facts_dir.mkdir(parents=True, exist_ok=True)

    # Create sample fact files
    sample_facts = {
        "modules.json": {
            "yawl-engine": {"path": "yawl/engine", "files": 42},
            "yawl-elements": {"path": "yawl/elements", "files": 28},
        },
        "gates.json": {
            "yawl-engine": {
                "unit": 156,
                "integration": 89,
            },
        },
        "tests.json": {
            "total": 245,
            "coverage": 82.5,
        },
    }

    for fact_name, fact_data in sample_facts.items():
        fact_file = facts_dir / fact_name
        with open(fact_file, "w") as f:
            json.dump(fact_data, f, indent=2)

    return facts_dir


@pytest.fixture
def config_with_yaml(temp_project_dir: Path, valid_config_file: Path) -> Config:
    """Create a Config object with YAML configuration loaded."""
    config = Config.from_project(temp_project_dir)
    return config


@pytest.fixture
def mock_shell_command(monkeypatch) -> dict:
    """Mock shell command execution for testing."""
    command_history = []

    def fake_run(cmd, **kwargs):
        """Fake subprocess.run that records commands."""
        from unittest.mock import Mock

        command_history.append((cmd, kwargs))
        result = Mock()
        result.returncode = 0
        result.stdout = "Command executed successfully"
        result.stderr = ""
        return result

    import subprocess

    monkeypatch.setattr(subprocess, "run", fake_run)
    return {"history": command_history, "fake_run": fake_run}


@pytest.fixture
def temp_workflow_file(temp_project_dir: Path) -> Path:
    """Create a temporary workflow specification file."""
    workflow_file = temp_project_dir / "sample-workflow.ttl"
    workflow_file.write_text(
        """@prefix : <http://example.com/workflow/> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

:SimpleWorkflow a :Workflow ;
    rdfs:label "Simple Workflow"@en ;
    :startingTask :TaskA .

:TaskA a :Task ;
    rdfs:label "Task A"@en ;
    :nextTask :TaskB .

:TaskB a :Task ;
    rdfs:label "Task B"@en ;
    :isEndingTask true .
"""
    )
    return workflow_file


@pytest.fixture(autouse=True)
def reset_project_root(monkeypatch, temp_project_dir: Path) -> None:
    """Reset current working directory to temp project for each test."""
    monkeypatch.chdir(temp_project_dir)


@pytest.fixture(autouse=True)
def mock_console_for_tests(monkeypatch) -> None:
    """Mock Rich console to avoid 'file' parameter issues in test environment."""
    from unittest.mock import MagicMock
    from rich.console import Console

    # Create a real console object but intercept calls to handle 'file' parameter
    real_console = Console()

    class MockConsole:
        def __init__(self):
            self._console = real_console

        def print(self, *args, **kwargs):
            # Remove 'file' parameter if present (Rich doesn't support it in tests)
            kwargs.pop('file', None)
            return self._console.print(*args, **kwargs)

        def __getattr__(self, name):
            # Forward other attributes to real console
            return getattr(self._console, name)

    mock_console = MockConsole()

    # Patch all modules that import console
    for module_name in [
        'yawl_cli.utils',
        'yawl_cli.build',
        'yawl_cli.config_cli',
        'yawl_cli.ggen',
        'yawl_cli.godspeed',
        'yawl_cli.gregverse',
        'yawl_cli.observatory',
        'yawl_cli.godspeed_cli',
    ]:
        try:
            module = __import__(module_name, fromlist=['console'])
            if hasattr(module, 'console'):
                monkeypatch.setattr(f'{module_name}.console', mock_console)
        except (ImportError, AttributeError, SyntaxError):
            # Skip modules with import, attribute, or syntax errors
            pass


@pytest.fixture
def git_initialized_project(temp_project_dir: Path) -> Path:
    """Create a YAWL project with git initialized."""
    subprocess.run(
        ["git", "init"],
        cwd=temp_project_dir,
        capture_output=True,
        text=True,
        timeout=10,
    )
    subprocess.run(
        ["git", "config", "user.email", "test@yawl.local"],
        cwd=temp_project_dir,
        capture_output=True,
        text=True,
        timeout=10,
    )
    subprocess.run(
        ["git", "config", "user.name", "Test User"],
        cwd=temp_project_dir,
        capture_output=True,
        text=True,
        timeout=10,
    )
    return temp_project_dir


@pytest.fixture
def shell_environment(monkeypatch, temp_project_dir: Path) -> Dict[str, Any]:
    """Create a test shell environment with mocked subprocess calls."""
    monkeypatch.chdir(temp_project_dir)
    monkeypatch.setenv("YAWL_PROJECT_ROOT", str(temp_project_dir))
    monkeypatch.setenv("YAWL_CLI_DEBUG", "1")
    return {
        "cwd": temp_project_dir,
        "env": {
            "YAWL_PROJECT_ROOT": str(temp_project_dir),
            "YAWL_CLI_DEBUG": "1",
        },
    }


@pytest.fixture
def real_shell_commands(temp_project_dir: Path) -> Dict[str, Any]:
    """Fixture for testing real shell command execution."""
    return {
        "project_root": temp_project_dir,
        "commands_run": [],
    }


@pytest.fixture
def sample_workflow_files(temp_project_dir: Path) -> Dict[str, Path]:
    """Create sample workflow files for testing."""
    workflows = {}

    # Create simple workflow YAWL file
    simple_workflow = temp_project_dir / "workflows" / "simple.yawl"
    simple_workflow.parent.mkdir(parents=True, exist_ok=True)
    simple_workflow.write_text(
        """<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema http://www.yawlfoundation.org/yawlschema/YAWL_Schema2.1.xsd"
                  name="SimpleWorkflow"
                  version="0.1">
  <specification uri="SimpleWorkflow" name="SimpleWorkflow">
    <metaData>
      <creator>Test User</creator>
      <created>2026-02-22T00:00:00</created>
    </metaData>
    <net id="SimpleNet" name="Simple Net">
      <node id="start">
        <flow source="start" target="task1"/>
      </node>
      <node id="task1">
        <flow source="task1" target="end"/>
      </node>
      <node id="end"/>
    </net>
  </specification>
</specificationSet>""",
        encoding="utf-8",
    )
    workflows["simple"] = simple_workflow

    # Create complex workflow with multiple tasks
    complex_workflow = temp_project_dir / "workflows" / "complex.yawl"
    complex_workflow.write_text(
        """<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema" name="ComplexWorkflow" version="0.1">
  <specification uri="ComplexWorkflow" name="ComplexWorkflow">
    <net id="ComplexNet" name="Complex Net">
      <node id="start"/>
      <node id="task1"/>
      <node id="task2"/>
      <node id="join"/>
      <node id="end"/>
    </net>
  </specification>
</specificationSet>""",
        encoding="utf-8",
    )
    workflows["complex"] = complex_workflow

    return workflows


@pytest.fixture
def multifile_config_project(temp_project_dir: Path, monkeypatch) -> Dict[str, Any]:
    """Create a project with multiple config files at different levels."""
    # System-level config
    system_config_dir = temp_project_dir / "etc" / "yawl"
    system_config_dir.mkdir(parents=True, exist_ok=True)
    system_config = system_config_dir / "config.yaml"
    system_config.write_text(
        yaml.dump(
            {
                "build": {"timeout": 600},
                "test": {"parallel": True},
            }
        ),
        encoding="utf-8",
    )

    # User-level config
    user_config_dir = temp_project_dir / "home" / ".yawl"
    user_config_dir.mkdir(parents=True, exist_ok=True)
    user_config = user_config_dir / "config.yaml"
    user_config.write_text(
        yaml.dump(
            {
                "build": {"threads": 4},
                "maven": {"version": "3.8.0"},
            }
        ),
        encoding="utf-8",
    )

    # Project-level config
    project_config_dir = temp_project_dir / ".yawl"
    project_config_dir.mkdir(parents=True, exist_ok=True)
    project_config = project_config_dir / "config.yaml"
    project_config.write_text(
        yaml.dump(
            {
                "build": {"threads": 8, "parallel": False},
            }
        ),
        encoding="utf-8",
    )

    return {
        "system_config": system_config,
        "user_config": user_config,
        "project_config": project_config,
        "project_root": temp_project_dir,
    }


@pytest.fixture
def shell_command_results() -> Dict[str, Any]:
    """Fixture for capturing shell command execution results."""
    return {
        "executed": [],
        "stdout": [],
        "stderr": [],
        "return_codes": [],
    }


@pytest.fixture
def temp_maven_project(temp_project_dir: Path) -> Path:
    """Create a temporary Maven project structure."""
    # Create Maven directory structure
    (temp_project_dir / "src" / "main" / "java" / "org" / "yawl").mkdir(
        parents=True, exist_ok=True
    )
    (temp_project_dir / "src" / "test" / "java" / "org" / "yawl").mkdir(
        parents=True, exist_ok=True
    )
    (temp_project_dir / "target").mkdir(exist_ok=True)

    # Create sample Java files
    main_java = (
        temp_project_dir / "src" / "main" / "java" / "org" / "yawl" / "YawlApp.java"
    )
    main_java.write_text(
        """
package org.yawl;

public class YawlApp {
    public static void main(String[] args) {
        System.out.println("YAWL CLI");
    }
}
""",
        encoding="utf-8",
    )

    # Create pom.xml
    pom_file = temp_project_dir / "pom.xml"
    pom_file.write_text(
        """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.yawl</groupId>
  <artifactId>yawl-cli</artifactId>
  <version>6.0.0</version>
  <name>YAWL CLI</name>
</project>""",
        encoding="utf-8",
    )

    return temp_project_dir


@pytest.fixture
def facts_with_details(facts_directory: Path) -> Dict[str, Path]:
    """Create detailed fact files for observatory testing."""
    details = {
        "modules": facts_directory / "modules.json",
        "gates": facts_directory / "gates.json",
        "tests": facts_directory / "tests.json",
        "coverage": facts_directory / "coverage.json",
        "dependencies": facts_directory / "dependencies.json",
    }

    # Add coverage facts
    coverage_data = {
        "overall": 82.5,
        "modules": {
            "yawl-engine": 85.2,
            "yawl-elements": 79.1,
            "yawl-integration": 81.0,
        },
    }
    with open(details["coverage"], "w") as f:
        json.dump(coverage_data, f, indent=2)

    # Add dependencies facts
    dependencies_data = {
        "direct": {
            "junit:junit": "4.13.2",
            "org.yaml:snakeyaml": "2.0",
        },
        "transitive": {
            "javax.servlet:servlet-api": "2.5",
        },
        "total": 127,
    }
    with open(details["dependencies"], "w") as f:
        json.dump(dependencies_data, f, indent=2)

    return details


@pytest.fixture
def large_config_data() -> Dict[str, Any]:
    """Fixture with large configuration data for stress testing."""
    config = {}
    for i in range(100):
        config[f"section_{i}"] = {
            f"key_{j}": f"value_{i}_{j}" for j in range(50)
        }
    return config


@pytest.fixture
def concurrent_file_access(temp_project_dir: Path) -> Dict[str, Any]:
    """Fixture for testing concurrent file access patterns."""
    return {
        "project_root": temp_project_dir,
        "config_file": temp_project_dir / ".yawl" / "config.yaml",
        "lock_file": temp_project_dir / ".yawl" / "config.lock",
        "temp_dir": temp_project_dir / ".yawl" / "tmp",
    }
