"""Utility functions for YAWL CLI."""

import json
import os
import subprocess
from pathlib import Path
from typing import Optional, Any, Dict

import yaml
from pydantic import BaseModel
from rich.console import Console

console = Console()


class Config(BaseModel):
    """YAWL project configuration."""

    project_root: Path
    maven_version: Optional[str] = None
    java_home: Optional[str] = None
    branch: Optional[str] = None
    facts_dir: Optional[Path] = None
    config_file: Optional[Path] = None
    config_data: Optional[Dict[str, Any]] = None

    class Config:
        arbitrary_types_allowed = True

    @staticmethod
    def from_project(project_root: Path) -> "Config":
        """Load configuration from project."""
        config = Config(project_root=project_root)

        # Get Java home
        config.java_home = os.environ.get("JAVA_HOME")

        # Get Maven version
        try:
            result = subprocess.run(
                ["mvn", "--version"],
                capture_output=True,
                text=True,
                timeout=5,
            )
            if result.returncode == 0:
                # Extract version from first line: "Apache Maven X.Y.Z"
                lines = result.stdout.split("\n")
                if lines:
                    config.maven_version = lines[0].split()[-1]
        except Exception:
            pass

        # Get git branch
        try:
            result = subprocess.run(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                capture_output=True,
                text=True,
                cwd=project_root,
                timeout=5,
            )
            if result.returncode == 0:
                config.branch = result.stdout.strip()
        except Exception:
            pass

        # Set facts directory
        config.facts_dir = project_root / "docs/v6/latest/facts"

        # Load YAML configuration
        config.load_yaml_config(project_root)

        return config

    def load_yaml_config(self, project_root: Path) -> None:
        """Load YAML configuration from project and user home."""
        config_paths = [
            project_root / ".yawl" / "config.yaml",
            Path.home() / ".yawl" / "config.yaml",
            Path("/etc/yawl/config.yaml"),
        ]

        merged_config = {}

        for config_path in config_paths:
            if config_path.exists():
                try:
                    with open(config_path) as f:
                        file_config = yaml.safe_load(f) or {}
                        # Deep merge configs (later files override earlier)
                        merged_config = self._deep_merge(merged_config, file_config)
                    self.config_file = config_path
                except Exception as e:
                    console.print(f"[yellow]Warning: Could not load {config_path}: {e}[/yellow]")

        self.config_data = merged_config

    @staticmethod
    def _deep_merge(base: Dict, override: Dict) -> Dict:
        """Recursively merge override dict into base dict."""
        for key, value in override.items():
            if key in base and isinstance(base[key], dict) and isinstance(value, dict):
                base[key] = Config._deep_merge(base[key], value)
            else:
                base[key] = value
        return base

    def get(self, key: str, default: Any = None) -> Any:
        """Get config value using dot notation (e.g., 'build.parallel')."""
        if not self.config_data:
            return default

        keys = key.split(".")
        value = self.config_data

        for k in keys:
            if isinstance(value, dict):
                value = value.get(k)
                if value is None:
                    return default
            else:
                return default

        return value

    def set(self, key: str, value: Any) -> None:
        """Set config value using dot notation."""
        if not self.config_data:
            self.config_data = {}

        keys = key.split(".")
        current = self.config_data

        for k in keys[:-1]:
            if k not in current:
                current[k] = {}
            current = current[k]

        current[keys[-1]] = value

    def save(self, config_file: Optional[Path] = None) -> None:
        """Save configuration to YAML file."""
        if config_file is None:
            config_file = self.project_root / ".yawl" / "config.yaml"

        config_file.parent.mkdir(parents=True, exist_ok=True)

        with open(config_file, "w") as f:
            yaml.dump(self.config_data, f, default_flow_style=False, sort_keys=False)

        console.print(f"[bold green]✓[/bold green] Configuration saved to {config_file}")


def ensure_project_root() -> Path:
    """Find and return YAWL project root.

    Searches upwards from current directory for:
    1. pom.xml (Maven project marker)
    2. CLAUDE.md (YAWL project marker)
    """
    current = Path.cwd()

    # Search upwards for project markers
    while current != current.parent:
        if (current / "pom.xml").exists() and (current / "CLAUDE.md").exists():
            return current
        current = current.parent

    raise RuntimeError(
        "Could not find YAWL project root. "
        "Please run from within a YAWL project directory."
    )


def load_facts(facts_dir: Path, fact_name: str) -> dict:
    """Load a fact JSON file.

    Args:
        facts_dir: Path to facts directory
        fact_name: Name of fact file (e.g., 'modules.json')

    Returns:
        Parsed JSON content
    """
    fact_file = facts_dir / fact_name

    if not fact_file.exists():
        raise FileNotFoundError(f"Fact file not found: {fact_file}")

    with open(fact_file) as f:
        return json.load(f)


def run_shell_cmd(
    cmd: list[str],
    cwd: Optional[Path] = None,
    verbose: bool = False,
) -> tuple[int, str, str]:
    """Run a shell command and return exit code, stdout, stderr.

    Args:
        cmd: Command as list
        cwd: Working directory
        verbose: Print command before running

    Returns:
        (exit_code, stdout, stderr)
    """
    if verbose:
        console.print(f"[dim]Running: {' '.join(cmd)}[/dim]")

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        cwd=cwd,
    )

    return result.returncode, result.stdout, result.stderr


def prompt_yes_no(message: str, default: bool = True) -> bool:
    """Prompt user for yes/no response.

    Args:
        message: Question to ask
        default: Default answer if user presses Enter

    Returns:
        True for yes, False for no
    """
    default_text = "Y/n" if default else "y/N"
    prompt_text = f"{message} [{default_text}]: "

    try:
        response = input(prompt_text).strip().lower()
        if response == "":
            return default
        return response in ["y", "yes", "1", "true"]
    except EOFError:
        # Non-interactive mode
        return default


def prompt_choice(message: str, choices: list[str], default: int = 0) -> str:
    """Prompt user to choose from list.

    Args:
        message: Question to ask
        choices: List of choices
        default: Index of default choice

    Returns:
        Selected choice
    """
    console.print(f"\n[bold]{message}[/bold]")
    for i, choice in enumerate(choices):
        marker = "→" if i == default else " "
        console.print(f"  {marker} {i + 1}. {choice}")

    try:
        response = input(f"Enter choice (1-{len(choices)}) [{default + 1}]: ").strip()
        if response == "":
            return choices[default]
        idx = int(response) - 1
        if 0 <= idx < len(choices):
            return choices[idx]
        return choices[default]
    except (ValueError, EOFError):
        return choices[default]
