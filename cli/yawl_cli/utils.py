"""Utility functions for YAWL CLI with production error handling."""

import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional, Any, Dict

import yaml
from pydantic import BaseModel, ConfigDict
from rich.console import Console

console = Console()

# Global debug flag
DEBUG = os.environ.get("YAWL_CLI_DEBUG", "").lower() in ("1", "true", "yes")

# Retry configuration
DEFAULT_RETRIES = 3
DEFAULT_RETRY_DELAY = 1.0  # seconds


class Config(BaseModel):
    """YAWL project configuration."""

    model_config = ConfigDict(arbitrary_types_allowed=True)

    project_root: Path
    maven_version: Optional[str] = None
    java_home: Optional[str] = None
    branch: Optional[str] = None
    facts_dir: Optional[Path] = None
    config_file: Optional[Path] = None
    config_data: Optional[Dict[str, Any]] = None

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
        """Load YAML configuration from project and user home.

        Loads configuration files from multiple locations and merges them.
        Hierarchy (highest priority first): project > user > system.

        Raises:
            RuntimeError: If YAML is invalid or files cannot be read
        """
        # Load in reverse priority order (lowest priority first)
        # so higher priority files override lower priority files
        config_paths = [
            Path("/etc/yawl/config.yaml"),
            Path.home() / ".yawl" / "config.yaml",
            project_root / ".yawl" / "config.yaml",
        ]

        merged_config: Dict[str, Any] = {}

        for config_path in config_paths:
            if not config_path.exists():
                # Config file optional - skip if missing
                continue

            try:
                # Check file permissions before reading
                if not os.access(config_path, os.R_OK):
                    raise PermissionError(f"No read permission for {config_path}")

                with open(config_path, "r", encoding="utf-8") as f:
                    # Check file size (protect against malicious files)
                    file_size = config_path.stat().st_size
                    if file_size > 1024 * 1024:  # 1 MB max for config
                        raise RuntimeError(
                            f"Config file too large ({file_size / 1024 / 1024:.1f} MB). "
                            f"Maximum 1 MB allowed."
                        )

                    file_config = yaml.safe_load(f) or {}
                    if not isinstance(file_config, dict):
                        raise ValueError(
                            f"Config file must be YAML dictionary, got {type(file_config).__name__}"
                        )
                    # Deep merge configs: file_config overrides merged_config
                    merged_config = self._deep_merge(merged_config, file_config)
                self.config_file = config_path

            except yaml.YAMLError as e:
                error_msg = f"Invalid YAML in {config_path}: {e}"
                if hasattr(e, 'problem_mark'):
                    error_msg += f"\nLine {e.problem_mark.line + 1}: {e.problem}"
                error_msg += "\nPlease fix the YAML syntax or delete the file to regenerate."
                raise RuntimeError(error_msg)
            except (OSError, IOError, PermissionError) as e:
                raise RuntimeError(
                    f"Cannot read config file {config_path}: {e}\n"
                    f"Check file permissions and try again."
                )
            except ValueError as e:
                raise RuntimeError(str(e))
            except UnicodeDecodeError as e:
                raise RuntimeError(
                    f"Config file has invalid encoding {config_path}: {e}\n"
                    f"File must be valid UTF-8"
                )

        self.config_data = merged_config

    @staticmethod
    def _deep_merge(base: Dict[str, Any], override: Dict[str, Any]) -> Dict[str, Any]:
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
        current: Any = self.config_data

        for k in keys:
            if isinstance(current, dict):
                current = current.get(k)
                if current is None:
                    return default
            else:
                return default

        return current

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
        """Save configuration to YAML file atomically.

        Writes to a temporary file first, then renames it to ensure atomicity.

        Args:
            config_file: Path to save config to (default: project_root/.yawl/config.yaml)

        Raises:
            RuntimeError: If directory cannot be created or file cannot be written
        """
        if config_file is None:
            config_file = self.project_root / ".yawl" / "config.yaml"

        # Validate config file path is within project
        try:
            config_file = config_file.resolve()
        except (OSError, RuntimeError) as e:
            raise RuntimeError(f"Invalid config file path: {e}")

        # Create parent directory
        try:
            config_file.parent.mkdir(parents=True, exist_ok=True)
        except PermissionError as e:
            raise RuntimeError(
                f"Permission denied creating config directory {config_file.parent}: {e}\n"
                f"Check directory permissions."
            )
        except OSError as e:
            raise RuntimeError(
                f"Cannot create config directory {config_file.parent}: {e}\n"
                f"Check permissions and disk space."
            )

        # Validate data before writing
        if not isinstance(self.config_data, dict):
            raise RuntimeError("Config data must be a dictionary")

        # Write to temp file first, then rename (atomic)
        temp_file = config_file.with_suffix(".yaml.tmp")
        try:
            # Ensure temp file doesn't already exist
            if temp_file.exists():
                try:
                    temp_file.unlink()
                except OSError:
                    pass

            with open(temp_file, "w", encoding="utf-8") as f:
                yaml.dump(
                    self.config_data,
                    f,
                    default_flow_style=False,
                    sort_keys=False,
                    allow_unicode=True
                )

            # Verify temp file was written
            if not temp_file.exists():
                raise RuntimeError("Failed to write temp file")

            # Atomic rename
            temp_file.replace(config_file)
            console.print(f"[bold green]✓[/bold green] Configuration saved to {config_file}")

        except PermissionError as e:
            # Clean up temp file if it exists
            if temp_file.exists():
                try:
                    temp_file.unlink()
                except Exception:
                    pass
            raise RuntimeError(
                f"Permission denied writing to {config_file}: {e}\n"
                f"Check file permissions."
            )
        except (OSError, IOError) as e:
            # Clean up temp file if it exists
            if temp_file.exists():
                try:
                    temp_file.unlink()
                except Exception:
                    pass
            raise RuntimeError(
                f"Cannot write config file {config_file}: {e}\n"
                f"Check file permissions and disk space."
            )


def ensure_project_root() -> Path:
    """Find and return YAWL project root.

    Searches upwards from current directory for:
    1. pom.xml (Maven project marker)
    2. CLAUDE.md (YAWL project marker)

    Returns:
        Path to project root

    Raises:
        RuntimeError: If project root not found or not accessible
    """
    current = Path.cwd()

    # Validate current directory is accessible
    try:
        _ = current.stat()
    except (OSError, PermissionError) as e:
        raise RuntimeError(
            f"Cannot access current directory: {current}\n"
            f"Error: {e}"
        )

    # Search upwards for project markers
    max_iterations = 100  # Prevent infinite loops
    iterations = 0

    while current != current.parent and iterations < max_iterations:
        iterations += 1
        try:
            pom_exists = (current / "pom.xml").is_file()
            claude_exists = (current / "CLAUDE.md").is_file()

            if pom_exists and claude_exists:
                return current
        except (OSError, PermissionError):
            # Skip directories we can't access
            pass

        current = current.parent

    raise RuntimeError(
        "Could not find YAWL project root.\n"
        "Please run from within a YAWL project directory.\n"
        "YAWL project must contain both: pom.xml and CLAUDE.md"
    )


def load_facts(facts_dir: Path, fact_name: str) -> Dict[str, Any]:
    """Load a fact JSON file with error handling.

    Args:
        facts_dir: Path to facts directory
        fact_name: Name of fact file (e.g., 'modules.json')

    Returns:
        Parsed JSON content

    Raises:
        FileNotFoundError: If facts directory or fact file doesn't exist
        RuntimeError: If JSON is malformed or inaccessible
    """
    # Validate facts directory exists and is accessible
    if not facts_dir.exists():
        raise FileNotFoundError(
            f"Facts directory not found: {facts_dir} - Run: yawl observatory generate"
        )

    if not facts_dir.is_dir():
        raise RuntimeError(
            f"Facts path is not a directory: {facts_dir}"
        )

    try:
        _ = facts_dir.stat()
    except (OSError, PermissionError) as e:
        raise RuntimeError(
            f"Cannot access facts directory {facts_dir}: {e}\n"
            f"Check directory permissions."
        )

    fact_file = facts_dir / fact_name

    if not fact_file.exists():
        available = list(facts_dir.glob("*.json"))
        if available:
            available_names = ", ".join(f.stem for f in sorted(available))
            raise FileNotFoundError(
                f"Fact file not found: {fact_file} - Available facts: {available_names}"
            )
        else:
            raise FileNotFoundError(
                f"Fact file not found: {fact_file} - No facts generated yet. Run: yawl observatory generate"
            )

    if not fact_file.is_file():
        raise RuntimeError(
            f"Fact path is not a file: {fact_file}"
        )

    # Check file size before reading (protect against huge files)
    try:
        file_size = fact_file.stat().st_size
        max_size = 100 * 1024 * 1024  # 100 MB
        if file_size > max_size:
            raise RuntimeError(
                f"Fact file is too large ({file_size / 1024 / 1024:.1f} MB).\n"
                f"Maximum allowed size: {max_size / 1024 / 1024:.0f} MB"
            )
    except (OSError, PermissionError) as e:
        raise RuntimeError(
            f"Cannot stat fact file {fact_file}: {e}\n"
            f"Check file permissions."
        )

    try:
        with open(fact_file, "r", encoding="utf-8") as f:
            content = json.load(f)
            if not isinstance(content, dict):
                raise RuntimeError(f"Expected JSON object, got {type(content).__name__}")
            return content
    except json.JSONDecodeError as e:
        raise RuntimeError(
            f"Malformed JSON in {fact_file} at line {e.lineno}: {e.msg} - Try regenerating facts: yawl observatory generate"
        )
    except (OSError, IOError, PermissionError) as e:
        raise RuntimeError(
            f"Cannot read fact file {fact_file}: {e}\n"
            f"Check file permissions."
        )
    except UnicodeDecodeError as e:
        raise RuntimeError(
            f"Fact file has invalid encoding {fact_file}: {e}\n"
            f"File must be valid UTF-8"
        )


def run_shell_cmd(
    cmd: list[str],
    cwd: Optional[Path] = None,
    verbose: bool = False,
    timeout: Optional[int] = None,
    retries: int = 0,
    retry_delay: float = DEFAULT_RETRY_DELAY,
) -> tuple[int, str, str]:
    """Run a shell command with error handling and timeout support.

    Args:
        cmd: Command as list
        cwd: Working directory
        verbose: Print command before running
        timeout: Timeout in seconds (default: 600 for build commands, 30 for others)
        retries: Number of times to retry on transient failure (default: 0)
        retry_delay: Delay in seconds between retries (default: 1.0)

    Returns:
        (exit_code, stdout, stderr)

    Raises:
        RuntimeError: On timeout or command not found
    """
    if not cmd or not cmd[0]:
        raise ValueError("Command cannot be empty")

    # Validate command list contains only strings
    if not all(isinstance(arg, str) for arg in cmd):
        raise ValueError("All command arguments must be strings")

    if verbose or DEBUG:
        console.print(f"[dim]Running: {' '.join(cmd)}[/dim]")

    # Default timeout based on command
    if timeout is None:
        if "mvn" in cmd or "dx.sh" in cmd or "build" in cmd[0]:
            timeout = 600  # 10 minutes for builds
        else:
            timeout = 120  # 2 minutes for other commands

    # Validate timeout
    if timeout <= 0:
        raise ValueError("Timeout must be positive")

    attempt = 0
    last_error = None

    while attempt <= retries:
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                cwd=cwd,
                timeout=timeout,
            )
            return result.returncode, result.stdout, result.stderr

        except subprocess.TimeoutExpired as e:
            last_error = RuntimeError(
                f"Command timed out after {timeout} seconds: {' '.join(cmd)}\n"
                f"Increase timeout with: --timeout {timeout + 300}"
            )
            if attempt < retries:
                if verbose or DEBUG:
                    console.print(f"[yellow]Timeout, retrying ({attempt + 1}/{retries})...[/yellow]")
                attempt += 1
                time.sleep(retry_delay)
            else:
                raise last_error

        except FileNotFoundError:
            # Command not found - don't retry this
            cmd_name = cmd[0]
            if "mvn" in cmd_name:
                raise RuntimeError(
                    f"Maven not found: {cmd_name}\n"
                    f"Install Maven: sudo apt install maven (Ubuntu) or brew install maven (macOS)"
                )
            elif "bash" in cmd_name or cmd_name.endswith(".sh"):
                raise RuntimeError(
                    f"Script not found: {cmd_name}\n"
                    f"Check that you're in a YAWL project directory."
                )
            else:
                raise RuntimeError(
                    f"Command not found: {cmd_name}\n"
                    f"Install the required tool and try again."
                )

        except OSError as e:
            raise RuntimeError(
                f"Cannot execute command {cmd[0]}: {e}\n"
                f"Check command availability and permissions."
            )

        except KeyboardInterrupt:
            raise RuntimeError(
                f"Command interrupted by user: {' '.join(cmd)}"
            )

    # Should not reach here, but raise last error if we do
    if last_error:
        raise last_error
    raise RuntimeError("Command execution failed: no result produced")


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
