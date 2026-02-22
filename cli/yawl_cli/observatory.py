"""Observatory fact generation and analysis."""

import json
import sys
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.table import Table

from yawl_cli.utils import ensure_project_root, run_shell_cmd, load_facts

console = Console()
stderr_console = Console(stderr=True)
observatory_app = typer.Typer(no_args_is_help=True)


@observatory_app.command()
def generate(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Generate Observatory facts (Ψ phase)."""
    project_root = ensure_project_root()

    console.print("[bold cyan]Generating Observatory facts[/bold cyan]")
    console.print("[dim]This scans your codebase and generates 14 fact files...[/dim]")

    cmd = ["bash", "scripts/observatory/observatory.sh"]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Facts generated successfully[/bold green]")
        # Show summary
        facts_dir = project_root / "docs/v6/latest/facts"
        if facts_dir.exists():
            facts = list(facts_dir.glob("*.json"))
            console.print(f"[bold cyan]{len(facts)}[/bold cyan] fact files created")
    else:
        console.print("[bold red]✗ Fact generation failed[/bold red]")
        raise typer.Exit(code=exit_code)


@observatory_app.command()
def show(
    fact: str = typer.Argument(..., help="Fact name (e.g., modules, gates, tests)"),
) -> None:
    """Display a fact file (compact view)."""
    project_root = ensure_project_root()
    facts_dir = project_root / "docs/v6/latest/facts"

    try:
        data = load_facts(facts_dir, f"{fact}.json")
        console.print_json(data=data)
    except FileNotFoundError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}")
        raise typer.Exit(code=1)


@observatory_app.command()
def list_facts() -> None:
    """List available facts."""
    project_root = ensure_project_root()
    facts_dir = project_root / "docs/v6/latest/facts"

    if not facts_dir.exists():
        console.print("[yellow]No facts generated yet. Run: yawl observatory generate[/yellow]")
        return

    facts = sorted(facts_dir.glob("*.json"))
    if not facts:
        console.print("[yellow]No facts found[/yellow]")
        return

    table = Table(title="Available Facts")
    table.add_column("Fact File", style="cyan")
    table.add_column("Size", style="green")
    table.add_column("Modified", style="dim")

    for fact_file in facts:
        stat = fact_file.stat()
        size_kb = stat.st_size / 1024
        table.add_row(
            fact_file.name,
            f"{size_kb:.1f} KB",
            "[dim]recently[/dim]" if stat.st_mtime else "unknown",
        )

    console.print(table)


@observatory_app.command()
def search(
    pattern: str = typer.Argument(..., help="Search pattern (e.g., 'YNetRunner')"),
) -> None:
    """Search facts for a pattern."""
    project_root = ensure_project_root()
    facts_dir = project_root / "docs/v6/latest/facts"

    if not facts_dir.exists():
        console.print("[yellow]No facts generated yet[/yellow]")
        return

    found = []
    for fact_file in facts_dir.glob("*.json"):
        try:
            with open(fact_file) as f:
                content = f.read()
                if pattern in content:
                    found.append(fact_file.name)
        except (OSError, UnicodeDecodeError) as e:
            stderr_console.print(f"[yellow]Warning:[/yellow] Error reading {fact_file.name}: {e}")

    if found:
        console.print(f"[bold green]Found in {len(found)} fact file(s):[/bold green]")
        for name in found:
            console.print(f"  • {name}")
    else:
        console.print(f"[yellow]Pattern '{pattern}' not found in facts[/yellow]")


@observatory_app.command()
def refresh() -> None:
    """Refresh facts (incremental cache)."""
    project_root = ensure_project_root()

    console.print("[bold cyan]Refreshing Observatory facts[/bold cyan]")
    console.print("[dim]Using incremental cache (only changed files scanned)...[/dim]")

    cmd = ["bash", "scripts/observatory/observatory.sh"]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print("[bold green]✓ Facts refreshed successfully[/bold green]")
    else:
        console.print("[bold red]✗ Refresh failed[/bold red]")
        raise typer.Exit(code=exit_code)
