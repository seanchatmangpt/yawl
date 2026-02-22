"""Pytest configuration and shared fixtures for YAWL CLI tests."""

import json
import tempfile
from pathlib import Path
from typing import Generator

import pytest
import yaml

from yawl_cli.utils import Config


@pytest.fixture
def temp_project_dir() -> Generator[Path, None, None]:
    """Create a temporary YAWL project directory with minimal structure."""
    with tempfile.TemporaryDirectory() as tmpdir:
        project_root = Path(tmpdir)

        # Create project structure
        (project_root / ".yawl").mkdir(parents=True, exist_ok=True)
        (project_root / "docs" / "v6" / "latest" / "facts").mkdir(
            parents=True, exist_ok=True
        )
        (project_root / ".claude" / "hooks").mkdir(parents=True, exist_ok=True)

        # Create pom.xml marker
        (project_root / "pom.xml").write_text(
            """<?xml version="1.0"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.yawl</groupId>
  <artifactId>yawl-core</artifactId>
  <version>6.0.0</version>
</project>
"""
        )

        # Create CLAUDE.md marker
        (project_root / "CLAUDE.md").write_text("# YAWL v6.0.0\n")

        # Create basic scripts directory structure
        (project_root / "scripts").mkdir(exist_ok=True)
        (project_root / "scripts" / "dx.sh").touch()
        (project_root / "scripts" / "observatory").mkdir(exist_ok=True)
        (project_root / "scripts" / "observatory" / "observatory.sh").touch()

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
