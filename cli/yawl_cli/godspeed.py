"""GODSPEED workflow phases (Ψ→Λ→H→Q→Ω) with error handling."""

import sys
import time
from typing import Optional

import typer
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from yawl_cli.utils import ensure_project_root, run_shell_cmd, DEBUG

console = Console()
stderr_console = Console(stderr=True)
godspeed_app = typer.Typer(no_args_is_help=True)


@godspeed_app.command()
def discover(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Ψ phase - Discover facts via Observatory."""
    try:
        project_root = ensure_project_root()

        console.print(Panel("[bold cyan]Ψ (Discover)[/bold cyan] - Generate facts", expand=False))
        console.print("[dim]Running Observatory to scan codebase...[/dim]")

        cmd = ["bash", "scripts/observatory/observatory.sh"]
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code == 0:
            console.print("[bold green]✓ Facts discovered[/bold green]")
        else:
            console.print("[bold red]✗ Discovery failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@godspeed_app.command()
def compile(
    module: Optional[str] = typer.Option(None, "--module", "-m"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Λ phase - Compile (fastest feedback loop)."""
    try:
        project_root = ensure_project_root()

        console.print(Panel("[bold cyan]Λ (Compile)[/bold cyan] - Build artifacts", expand=False))

        if module:
            console.print(f"[dim]Compiling module: {module}...[/dim]")
            cmd = ["bash", "scripts/dx.sh", "-pl", module]
        else:
            console.print("[dim]Running compile phase...[/dim]")
            cmd = ["bash", "scripts/dx.sh", "compile"]

        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=600)

        if exit_code == 0:
            console.print("[bold green]✓ Compilation successful[/bold green]")
        else:
            console.print("[bold red]✗ Compilation failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@godspeed_app.command()
def guard(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """H phase - Guard enforcement (catch anti-patterns)."""
    try:
        project_root = ensure_project_root()

        console.print(Panel("[bold cyan]H (Guards)[/bold cyan] - Hyper-standards validation", expand=False))
        console.print("[dim]Checking for: TODO, mock, stub, fake, empty, silent fallback, lies...[/dim]")

        # Run hook directly
        cmd = ["bash", ".claude/hooks/hyper-validate.sh", "src/"]
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=120)

        if exit_code == 0:
            console.print("[bold green]✓ No guard violations[/bold green]")
        else:
            console.print("[bold red]✗ Guard violations found[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@godspeed_app.command()
def verify(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Q phase - Verify invariants (real impl ∨ throw)."""
    try:
        project_root = ensure_project_root()

        console.print(Panel("[bold cyan]Q (Invariants)[/bold cyan] - Code invariant verification", expand=False))
        console.print("[dim]Checking: real_impl ∨ throw, ¬mock, ¬lie, ¬silent_fallback...[/dim]")

        # Run tests to verify invariants hold
        cmd = ["bash", "scripts/dx.sh", "test"]
        start_time = time.time()
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=600)
        elapsed = time.time() - start_time

        if exit_code == 0:
            console.print(f"[bold green]✓ Invariants verified[/bold green] ({elapsed:.1f}s)")
        else:
            console.print("[bold red]✗ Invariant verification failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]Verification cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@godspeed_app.command()
def full(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Full GODSPEED circuit (Ψ→Λ→H→Q→Ω)."""
    try:
        project_root = ensure_project_root()

        console.print(Panel(
            "[bold cyan]⚡ FULL GODSPEED CIRCUIT ⚡[/bold cyan]\n"
            "[cyan]Ψ → Λ → H → Q → Ω[/cyan]",
            expand=False
        ))

        phases = [
            ("Ψ", "discover", "Generate facts via Observatory", 300),
            ("Λ", "compile", "Compile all modules", 600),
            ("H", "guard", "Check guard violations", 120),
            ("Q", "verify", "Verify invariants", 600),
        ]

        results = []
        total_start = time.time()

        for phase_name, phase_cmd, description, timeout in phases:
            console.print(f"\n[bold cyan]→ {phase_name}[/bold cyan] {description}")

            try:
                start_time = time.time()
                cmd_list = [
                    "bash",
                    "scripts/observatory/observatory.sh" if phase_cmd == "discover" else None,
                    "bash",
                    "scripts/dx.sh",
                    phase_cmd if phase_cmd != "discover" else None,
                ]
                cmd_list = [c for c in cmd_list if c]

                if phase_cmd == "discover":
                    cmd_list = ["bash", "scripts/observatory/observatory.sh"]
                elif phase_cmd == "compile":
                    cmd_list = ["bash", "scripts/dx.sh", "compile"]
                elif phase_cmd == "guard":
                    cmd_list = ["bash", ".claude/hooks/hyper-validate.sh", "src/"]
                elif phase_cmd == "verify":
                    cmd_list = ["bash", "scripts/dx.sh", "test"]

                exit_code, _, stderr = run_shell_cmd(cmd_list, cwd=project_root, verbose=verbose, timeout=timeout)
                elapsed = time.time() - start_time

                if exit_code == 0:
                    console.print(f"[bold green]✓ {phase_name} passed[/bold green] ({elapsed:.1f}s)")
                    results.append((phase_name, "PASS", elapsed))
                else:
                    console.print(f"[bold red]✗ {phase_name} failed[/bold red]")
                    if stderr:
                        console.print(f"[red]{stderr}[/red]")
                    results.append((phase_name, "FAIL", elapsed))
                    raise typer.Exit(code=exit_code)

            except RuntimeError as e:
                stderr_console.print(f"[bold red]✗ {phase_name} error:[/bold red] {e}")
                results.append((phase_name, "ERROR", 0))
                raise typer.Exit(code=1)

        # Summary
        total_elapsed = time.time() - total_start
        console.print(f"\n[bold cyan]GODSPEED Circuit Summary[/bold cyan]")

        table = Table(title="Phase Results")
        table.add_column("Phase", style="cyan")
        table.add_column("Result", style="green")
        table.add_column("Time", style="yellow")

        for phase, status, elapsed in results:
            status_str = f"[bold green]{status}[/bold green]" if status == "PASS" else f"[bold red]{status}[/bold red]"
            table.add_row(phase, status_str, f"{elapsed:.1f}s")

        console.print(table)

        console.print(f"\n[bold green]✓ GODSPEED circuit complete[/bold green] ({total_elapsed:.1f}s total)")
        console.print("[dim]Ready for Ω (consolidation) → commit & push[/dim]")

    except RuntimeError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except KeyboardInterrupt:
        console.print("\n[yellow]GODSPEED circuit cancelled by user[/yellow]")
        raise typer.Exit(code=130)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
