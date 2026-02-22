"""Maven build operations."""

from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.progress import Progress

from yawl_cli.utils import ensure_project_root, run_shell_cmd

console = Console()
build_app = typer.Typer(no_args_is_help=True)


@build_app.command()
def compile(
    module: Optional[str] = typer.Option(None, "--module", "-m", help="Specific module to compile"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Compile YAWL project (fastest feedback)."""
    project_root = ensure_project_root()

    if module:
        cmd = ["bash", "scripts/dx.sh", "-pl", module]
        console.print(f"[bold cyan]Compiling module:[/bold cyan] {module}")
    else:
        cmd = ["bash", "scripts/dx.sh", "compile"]
        console.print("[bold cyan]Running compile phase (dx.sh compile)[/bold cyan]")

    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Compile successful[/bold green]")
    else:
        console.print("[bold red]✗ Compile failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@build_app.command()
def test(
    module: Optional[str] = typer.Option(None, "--module", "-m"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Run tests (unit + integration)."""
    project_root = ensure_project_root()

    if module:
        cmd = ["mvn", "test", "-pl", module]
    else:
        cmd = ["bash", "scripts/dx.sh", "test"]

    console.print("[bold cyan]Running tests[/bold cyan]")
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Tests passed[/bold green]")
    else:
        console.print("[bold red]✗ Tests failed[/bold red]")
        raise typer.Exit(code=exit_code)


@build_app.command()
def validate(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Validate build (checkstyle, spotbugs, pmd)."""
    project_root = ensure_project_root()

    console.print("[bold cyan]Running validation gates[/bold cyan]")
    cmd = ["mvn", "clean", "verify", "-P", "analysis"]

    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Validation passed[/bold green]")
    else:
        console.print("[bold red]✗ Validation failed[/bold red]")
        raise typer.Exit(code=exit_code)


@build_app.command()
def all(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Run full build (compile → test → validate)."""
    project_root = ensure_project_root()

    console.print("[bold cyan]Running full build (all phases)[/bold cyan]")
    cmd = ["bash", "scripts/dx.sh", "all"]

    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Full build successful[/bold green]")
    else:
        console.print("[bold red]✗ Full build failed[/bold red]")
        raise typer.Exit(code=exit_code)


@build_app.command()
def clean() -> None:
    """Clean build artifacts."""
    project_root = ensure_project_root()

    console.print("[bold cyan]Cleaning build artifacts[/bold cyan]")
    cmd = ["mvn", "clean"]

    exit_code, _, _ = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print("[bold green]✓ Clean successful[/bold green]")
    else:
        console.print("[bold red]✗ Clean failed[/bold red]")
        raise typer.Exit(code=exit_code)
