#!/usr/bin/env python3
"""
YAWL v6 Unified CLI - ONE MEGA GODSPEED
Wraps: Maven, Observatory, GODSPEED (Ψ→Λ→H→Q→Ω), ggen, gregverse, teams

Entry point: yawl <command> <subcommand> [options]
"""

import os
import sys
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax

from yawl_cli.build import build_app
from yawl_cli.observatory import observatory_app
from yawl_cli.godspeed import godspeed_app
from yawl_cli.ggen import ggen_app
from yawl_cli.gregverse import gregverse_app
from yawl_cli.team import team_app
from yawl_cli.utils import ensure_project_root, Config

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


@app.command()
def version() -> None:
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
    except Exception as e:
        console.print(f"[yellow]Warning:[/yellow] {e}")


@app.command()
def init() -> None:
    """Initialize YAWL project for GODSPEED workflow."""
    try:
        project_root = ensure_project_root()
        console.print(f"[bold green]✓[/bold green] Project root: {project_root}")

        config = Config.from_project(project_root)
        console.print(f"[bold green]✓[/bold green] Config loaded")

        # Create .yawl directory for CLI state
        yawl_dir = project_root / ".yawl"
        yawl_dir.mkdir(exist_ok=True)
        console.print(f"[bold green]✓[/bold green] Created .yawl directory")

        # Create cli subdirectory
        cli_dir = yawl_dir / "cli"
        cli_dir.mkdir(exist_ok=True)
        console.print(f"[bold green]✓[/bold green] Created CLI state directory")

        console.print("[bold green]✨ YAWL CLI initialized successfully[/bold green]")

    except Exception as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        raise typer.Exit(code=1)


@app.command()
def status() -> None:
    """Show YAWL project status and latest facts."""
    try:
        project_root = ensure_project_root()

        console.print(Panel(
            "[bold cyan]YAWL Project Status[/bold cyan]",
            expand=False
        ))

        config = Config.from_project(project_root)

        # Check Observatory facts
        facts_dir = project_root / "docs/v6/latest/facts"
        if facts_dir.exists():
            facts = list(facts_dir.glob("*.json"))
            console.print(f"[bold]Observatory facts:[/bold] {len(facts)} files")
            if facts:
                newest = max(facts, key=lambda p: p.stat().st_mtime)
                console.print(f"  [dim]Latest:[/dim] {newest.name}")
        else:
            console.print("[yellow]Observatory facts: not generated yet[/yellow]")

        # Check git branch
        import subprocess
        try:
            branch = subprocess.check_output(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                cwd=project_root,
                text=True
            ).strip()
            console.print(f"[bold]Git branch:[/bold] {branch}")
        except Exception:
            console.print("[yellow]Git branch: unknown[/yellow]")

        console.print("[bold green]✓ Status check complete[/bold green]")

    except Exception as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        raise typer.Exit(code=1)


@app.callback()
def main(
    verbose: bool = typer.Option(False, "--verbose", "-v", help="Verbose output"),
    quiet: bool = typer.Option(False, "--quiet", "-q", help="Suppress output"),
) -> None:
    """YAWL v6 Unified CLI - GODSPEED Workflow Orchestrator.

    One mega CLI to rule them all:
    - Maven build operations
    - Observatory fact generation
    - GODSPEED phases (Ψ→Λ→H→Q→Ω)
    - ggen XML generator
    - gregverse workflow conversion
    - Team operations (experimental)
    """
    if verbose:
        console.print("[dim]Verbose mode enabled[/dim]")
    if quiet:
        console.print("[dim]Quiet mode enabled[/dim]")


if __name__ == "__main__":
    app()
