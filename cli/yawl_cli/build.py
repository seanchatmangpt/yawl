"""Maven build operations with production error handling."""

import sys
import time
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.progress import Progress, SpinnerColumn, BarColumn, TimeRemainingColumn
from rich.panel import Panel

from yawl_cli.utils import ensure_project_root, run_shell_cmd, DEBUG

console = Console()
stderr_console = Console(stderr=True)
build_app = typer.Typer(no_args_is_help=True)


@build_app.command()
def compile(
    module: Optional[str] = typer.Option(None, "--module", "-m", help="Specific module to compile"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
    timeout: int = typer.Option(600, "--timeout", help="Timeout in seconds"),
    dry_run: bool = typer.Option(False, "--dry-run", help="Show command without executing"),
) -> None:
    """Compile YAWL project (fastest feedback)."""
    try:
        project_root = ensure_project_root()

        if module:
            cmd = ["bash", "scripts/dx.sh", "-pl", module]
            console.print(f"[bold cyan]Compiling module:[/bold cyan] {module}")
        else:
            cmd = ["bash", "scripts/dx.sh", "compile"]
            console.print("[bold cyan]Running compile phase (dx.sh compile)[/bold cyan]")

        if dry_run:
            console.print(f"\n[yellow]DRY RUN:[/yellow] {' '.join(cmd)}")
            console.print("[dim]No changes will be made[/dim]")
            return

        start_time = time.time()
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=timeout)
        elapsed = time.time() - start_time

        if exit_code == 0:
            console.print(f"[bold green]✓ Compile successful[/bold green] ({elapsed:.1f}s)")
        else:
            console.print("[bold red]✗ Compile failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            if stdout and DEBUG:
                console.print(f"[dim]{stdout}[/dim]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]Compilation cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@build_app.command()
def test(
    module: Optional[str] = typer.Option(None, "--module", "-m"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
    timeout: int = typer.Option(600, "--timeout", help="Timeout in seconds"),
    dry_run: bool = typer.Option(False, "--dry-run", help="Show command without executing"),
) -> None:
    """Run tests (unit + integration)."""
    try:
        project_root = ensure_project_root()

        if module:
            cmd = ["mvn", "test", "-pl", module]
            console.print(f"[bold cyan]Running tests in:[/bold cyan] {module}")
        else:
            cmd = ["bash", "scripts/dx.sh", "test"]
            console.print("[bold cyan]Running all tests[/bold cyan]")

        if dry_run:
            console.print(f"\n[yellow]DRY RUN:[/yellow] {' '.join(cmd)}")
            console.print("[dim]No changes will be made[/dim]")
            return

        start_time = time.time()
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=timeout)
        elapsed = time.time() - start_time

        if exit_code == 0:
            console.print(f"[bold green]✓ Tests passed[/bold green] ({elapsed:.1f}s)")
        else:
            console.print("[bold red]✗ Tests failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]Tests cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@build_app.command()
def validate(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
    timeout: int = typer.Option(900, "--timeout", help="Timeout in seconds"),
    dry_run: bool = typer.Option(False, "--dry-run", help="Show command without executing"),
) -> None:
    """Validate build (checkstyle, spotbugs, pmd)."""
    try:
        project_root = ensure_project_root()

        console.print("[bold cyan]Running validation gates[/bold cyan]")
        console.print("[dim]Checks: checkstyle, spotbugs, pmd[/dim]")
        cmd = ["mvn", "clean", "verify", "-P", "analysis"]

        if dry_run:
            console.print(f"\n[yellow]DRY RUN:[/yellow] {' '.join(cmd)}")
            console.print("[dim]No changes will be made[/dim]")
            return

        start_time = time.time()
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=timeout)
        elapsed = time.time() - start_time

        if exit_code == 0:
            console.print(f"[bold green]✓ Validation passed[/bold green] ({elapsed:.1f}s)")
        else:
            console.print("[bold red]✗ Validation failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]Validation cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@build_app.command()
def all(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
    timeout: int = typer.Option(1800, "--timeout", help="Timeout in seconds"),
    dry_run: bool = typer.Option(False, "--dry-run", help="Show command without executing"),
) -> None:
    """Run full build (compile → test → validate)."""
    try:
        project_root = ensure_project_root()

        console.print(Panel(
            "[bold cyan]Running full build[/bold cyan]\n[dim]Phases: compile → test → validate[/dim]",
            expand=False
        ))
        cmd = ["bash", "scripts/dx.sh", "all"]

        if dry_run:
            console.print(f"\n[yellow]DRY RUN:[/yellow] {' '.join(cmd)}")
            console.print("[dim]No changes will be made[/dim]")
            return

        start_time = time.time()
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=timeout)
        elapsed = time.time() - start_time

        if exit_code == 0:
            console.print(f"[bold green]✓ Full build successful[/bold green] ({elapsed:.1f}s)")
        else:
            console.print("[bold red]✗ Full build failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]Build cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@build_app.command()
def clean(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
    dry_run: bool = typer.Option(False, "--dry-run", help="Show command without executing"),
) -> None:
    """Clean build artifacts."""
    try:
        project_root = ensure_project_root()

        console.print("[bold cyan]Cleaning build artifacts[/bold cyan]")
        cmd = ["mvn", "clean"]

        if dry_run:
            console.print(f"\n[yellow]DRY RUN:[/yellow] {' '.join(cmd)}")
            console.print("[dim]No changes will be made[/dim]")
            return

        exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code == 0:
            console.print("[bold green]✓ Clean successful[/bold green]")
        else:
            console.print("[bold red]✗ Clean failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]Clean cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
