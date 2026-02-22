#!/usr/bin/env python3
"""
YAWL v6 Unified CLI - ONE MEGA GODSPEED
Wraps: Maven, Observatory, GODSPEED (Ψ→Λ→H→Q→Ω), ggen, gregverse, teams

Entry point: yawl <command> <subcommand> [options]

Features:
- GODSPEED workflow automation (Ψ → Λ → H → Q → Ω phases)
- Interactive mode for all major commands
- Configuration management (.yawl/config.yaml + ~/.yawl/config.yaml)
- Maven integration (compile, test, validate)
- Observatory fact generation
- Code generation (ggen)
- Workflow conversion (gregverse)
- Team operations (experimental)

Author: YAWL Team
Version: 6.0.0
License: Apache 2.0
"""

import os
import sys
import traceback
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.prompt import Confirm, Prompt

from yawl_cli.build import build_app
from yawl_cli.observatory import observatory_app
from yawl_cli.godspeed import godspeed_app
from yawl_cli.ggen import ggen_app
from yawl_cli.gregverse import gregverse_app
from yawl_cli.team import team_app
from yawl_cli import config_cli
from yawl_cli.utils import ensure_project_root, Config, DEBUG

console = Console()

# Create main app
app = typer.Typer(
    name="yawl",
    help="YAWL v6 Unified CLI - GODSPEED Workflow Orchestrator",
    rich_markup_mode="rich",
    no_args_is_help=True,
)

# Register subcommand apps
app.add_typer(build_app, name="build", help="Maven build operations")
app.add_typer(observatory_app, name="observatory", help="YAWL Observatory - fact generation")
app.add_typer(godspeed_app, name="godspeed", help="GODSPEED workflow (Ψ→Λ→H→Q→Ω)")
app.add_typer(ggen_app, name="ggen", help="YAWL XML Generator")
app.add_typer(gregverse_app, name="gregverse", help="Workflow conversion & export")
app.add_typer(team_app, name="team", help="Team operations (experimental)")
app.add_typer(config_cli.app, name="config", help="Configuration management")


@app.command()
def version(
    verbose: bool = typer.Option(
        False, "--verbose", "-v", help="Show detailed environment info"
    ),
) -> None:
    """Show YAWL CLI version and environment."""
    banner = """
    ╔══════════════════════════════════════════╗
    ║  YAWL v6.0.0 - CLI GODSPEED             ║
    ║  One Mega CLI for Everything             ║
    ║  Maven + Observatory + ggen + gregverse  ║
    ╚══════════════════════════════════════════╝
    """
    console.print(banner)
    console.print("[bold cyan]Version:[/bold cyan] 6.0.0")
    console.print("[bold cyan]Python:[/bold cyan]", sys.version.split()[0])

    try:
        project_root = ensure_project_root()
        console.print("[bold cyan]Project root:[/bold cyan]", str(project_root))

        config = Config.from_project(project_root)
        console.print("[bold cyan]JAVA_HOME:[/bold cyan]", os.environ.get("JAVA_HOME", "not set"))
        console.print("[bold cyan]Maven:[/bold cyan]", config.maven_version or "unknown")

        if verbose:
            console.print("\n[bold cyan]Environment:[/bold cyan]")
            console.print(f"  Git branch: {config.branch or 'unknown'}")
            console.print(f"  Facts dir: {config.facts_dir}")
            if config.config_file:
                console.print(f"  Config file: {config.config_file}")
    except RuntimeError as e:
        console.print(f"[yellow]Warning:[/yellow] {e}")
        if DEBUG:
            console.print_exception()
    except Exception as e:
        console.print(f"[yellow]Warning:[/yellow] {e}")
        if DEBUG:
            console.print_exception()


@app.command()
def init(
    interactive: bool = typer.Option(
        False,
        "--interactive",
        "-i",
        help="Interactive setup wizard (recommended)",
    ),
    force: bool = typer.Option(
        False,
        "--force",
        "-f",
        help="Force recreate configuration",
    ),
) -> None:
    """Initialize YAWL project for GODSPEED workflow."""
    try:
        project_root = ensure_project_root()
        console.print(f"[bold green]✓[/bold green] Project root: {project_root}")

        config = Config.from_project(project_root)
        console.print(f"[bold green]✓[/bold green] Config loaded")

        # Create .yawl directory for CLI state
        yawl_dir = project_root / ".yawl"
        try:
            yawl_dir.mkdir(exist_ok=True, parents=True)
            console.print(f"[bold green]✓[/bold green] Created .yawl directory")
        except OSError as e:
            raise RuntimeError(
                f"Cannot create .yawl directory: {e}\n"
                f"Check directory permissions."
            )

        # Create cli subdirectory
        cli_dir = yawl_dir / "cli"
        try:
            cli_dir.mkdir(exist_ok=True, parents=True)
            console.print(f"[bold green]✓[/bold green] Created CLI state directory")
        except OSError as e:
            raise RuntimeError(
                f"Cannot create CLI state directory: {e}\n"
                f"Check directory permissions."
            )

        # Initialize configuration
        config_file = yawl_dir / "config.yaml"
        config_exists = config_file.exists()

        if interactive or not config_exists or force:
            _init_interactive(config, yawl_dir, force=force)
        else:
            console.print(
                f"[bold green]✨ YAWL CLI initialized[/bold green] (config exists at {config_file})"
            )
            console.print("\nRun [bold]yawl init --interactive[/bold] to customize settings.")

    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


def _init_interactive(config: Config, yawl_dir: Path, force: bool = False) -> None:
    """Interactive initialization wizard."""
    console.print("\n[bold cyan]Welcome to YAWL CLI Setup![/bold cyan]\n")

    if force:
        console.print("[yellow]Recreating configuration...[/yellow]\n")
    else:
        console.print("Answer a few questions to configure YAWL CLI:\n")

    # Build section
    default_module = Prompt.ask(
        "Default build module (e.g., yawl-engine)",
        default=config.get("build.default_module", "yawl-engine"),
    )
    parallel = Confirm.ask(
        "Enable parallel compilation?",
        default=config.get("build.parallel", True),
    )
    threads = Prompt.ask(
        "Number of threads for parallel builds",
        default=str(config.get("build.threads", 8)),
    )

    # Test section
    test_pattern = Prompt.ask(
        "Test pattern (glob)",
        default=config.get("test.pattern", "**/*Test.java"),
    )
    coverage = Prompt.ask(
        "Minimum test coverage (%)",
        default=str(config.get("test.coverage_minimum", 80)),
    )

    # Observatory section
    refresh_interval = Prompt.ask(
        "Observatory fact refresh interval (minutes)",
        default=str(config.get("observatory.refresh_interval_minutes", 30)),
    )
    auto_refresh = Confirm.ask(
        "Enable auto-refresh of facts?",
        default=config.get("observatory.auto_refresh", True),
    )

    # GODSPEED section
    fail_fast = Confirm.ask(
        "Fail fast (stop at first error)?",
        default=config.get("godspeed.fail_fast", True),
    )

    # Team section
    max_agents = Prompt.ask(
        "Maximum agents for team operations (2-5)",
        default=str(config.get("team.max_agents", 3)),
    )

    # Output section
    output_format = Prompt.ask(
        "Output format (table, json, yaml)",
        default=config.get("output.format", "table"),
    )

    # Build new config
    new_config = {
        "project": {
            "name": config.project_root.name,
            "version": "6.0.0",
        },
        "build": {
            "default_module": default_module,
            "parallel": parallel,
            "threads": int(threads),
            "timeout_seconds": 600,
        },
        "test": {
            "pattern": test_pattern,
            "coverage_minimum": int(coverage),
            "fail_fast": config.get("test.fail_fast", False),
        },
        "observatory": {
            "facts_dir": "docs/v6/latest/facts",
            "refresh_interval_minutes": int(refresh_interval),
            "auto_refresh": auto_refresh,
        },
        "godspeed": {
            "phases": ["discover", "compile", "guard", "verify"],
            "fail_fast": fail_fast,
            "verbose": False,
        },
        "team": {
            "max_agents": int(max_agents),
            "heartbeat_interval_seconds": 60,
            "timeout_minutes": 120,
        },
        "output": {
            "format": output_format,
            "verbose": False,
            "color": True,
        },
    }

    config.config_data = new_config
    config.save(yawl_dir / "config.yaml")

    console.print(f"\n[bold green]✓ Configuration saved[/bold green] to {yawl_dir / 'config.yaml'}")
    console.print("[bold green]✨ YAWL CLI initialized successfully![/bold green]")


@app.command()
def status(
    verbose: bool = typer.Option(
        False, "--verbose", "-v", help="Show detailed status"
    ),
) -> None:
    """Show YAWL project status and latest facts."""
    try:
        project_root = ensure_project_root()

        console.print(
            Panel(
                "[bold cyan]YAWL Project Status[/bold cyan]",
                expand=False,
            )
        )

        config = Config.from_project(project_root)

        # Check Observatory facts
        facts_dir = project_root / "docs/v6/latest/facts"
        if facts_dir.exists():
            try:
                facts = list(facts_dir.glob("*.json"))
                console.print(f"[bold]Observatory facts:[/bold] {len(facts)} files")
                if facts:
                    newest = max(facts, key=lambda p: p.stat().st_mtime)
                    import datetime
                    mtime = datetime.datetime.fromtimestamp(newest.stat().st_mtime)
                    console.print(f"  [dim]Latest:[/dim] {newest.name} ({mtime})")
            except (OSError, ValueError) as e:
                console.print(f"[yellow]Warning:[/yellow] Could not read facts: {e}")
        else:
            console.print("[yellow]Observatory facts: not generated yet[/yellow]")

        # Check git branch
        if config.branch:
            console.print(f"[bold]Git branch:[/bold] {config.branch}")
        else:
            console.print("[yellow]Git branch: unknown[/yellow]")

        # Check configuration
        if config.config_file:
            console.print(f"[bold]Config file:[/bold] {config.config_file}")
        else:
            console.print("[yellow]No configuration file loaded[/yellow]")

        console.print("[bold green]✓ Status check complete[/bold green]")

        if verbose:
            console.print("\n[bold cyan]Configuration Summary:[/bold cyan]")
            if config.config_data:
                for section, values in config.config_data.items():
                    console.print(f"  [bold]{section}:[/bold]")
                    if isinstance(values, dict):
                        for key, val in values.items():
                            console.print(f"    {key}: {val}")
                    else:
                        console.print(f"    {values}")

    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@app.callback()
def main(
    verbose: bool = typer.Option(False, "--verbose", "-v", help="Verbose output"),
    quiet: bool = typer.Option(False, "--quiet", "-q", help="Suppress output"),
    debug: bool = typer.Option(False, "--debug", help="Debug mode (very verbose, stack traces)"),
) -> None:
    """YAWL v6 Unified CLI - GODSPEED Workflow Orchestrator.

    One mega CLI to rule them all:
    - Maven build operations (compile, test, validate)
    - Observatory fact generation (Ψ phase)
    - GODSPEED workflow phases (Ψ→Λ→H→Q→Ω)
    - ggen XML generator
    - gregverse workflow conversion
    - Team operations (experimental multi-agent coordination)

    Quick start:
      yawl init --interactive    # Setup wizard
      yawl godspeed full         # Run full GODSPEED circuit
      yawl build all             # Full build + test + validate
      yawl status                # Check project status

    See docs/GODSPEED_CLI_GUIDE.md for complete documentation.
    """
    # Set debug environment variable for utils
    if debug:
        os.environ["YAWL_CLI_DEBUG"] = "1"
        console.print("[dim]Debug mode enabled[/dim]")


if __name__ == "__main__":
    app()
