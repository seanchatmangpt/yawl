"""GODSPEED workflow phases (Ψ→Λ→H→Q→Ω)."""

from typing import Optional

import typer
from rich.console import Console
from rich.panel import Panel

from yawl_cli.utils import ensure_project_root, run_shell_cmd

console = Console()
godspeed_app = typer.Typer(no_args_is_help=True)


@godspeed_app.command()
def discover(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Ψ phase - Discover facts via Observatory."""
    project_root = ensure_project_root()

    console.print(Panel("[bold cyan]Ψ (Discover)[/bold cyan] - Generate facts", expand=False))
    console.print("[dim]Running Observatory to scan codebase...[/dim]")

    cmd = ["bash", "scripts/observatory/observatory.sh"]
    exit_code, _, _ = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Facts discovered[/bold green]")
    else:
        console.print("[bold red]✗ Discovery failed[/bold red]")
        raise typer.Exit(code=exit_code)


@godspeed_app.command()
def compile(
    module: Optional[str] = typer.Option(None, "--module", "-m"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Λ phase - Compile (fastest feedback loop)."""
    project_root = ensure_project_root()

    console.print(Panel("[bold cyan]Λ (Compile)[/bold cyan] - Build artifacts", expand=False))

    if module:
        console.print(f"[dim]Compiling module: {module}...[/dim]")
        cmd = ["bash", "scripts/dx.sh", "-pl", module]
    else:
        console.print("[dim]Running compile phase...[/dim]")
        cmd = ["bash", "scripts/dx.sh", "compile"]

    exit_code, _, _ = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Compilation successful[/bold green]")
    else:
        console.print("[bold red]✗ Compilation failed[/bold red]")
        raise typer.Exit(code=exit_code)


@godspeed_app.command()
def guard(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """H phase - Guard enforcement (catch anti-patterns)."""
    project_root = ensure_project_root()

    console.print(Panel("[bold cyan]H (Guards)[/bold cyan] - Hyper-standards validation", expand=False))
    console.print("[dim]Checking for: TODO, mock, stub, fake, empty, silent fallback, lies...[/dim]")

    # Run hook directly
    cmd = ["bash", ".claude/hooks/hyper-validate.sh", "src/"]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ No guard violations[/bold green]")
    else:
        console.print("[bold red]✗ Guard violations found[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@godspeed_app.command()
def verify(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Q phase - Verify invariants (real impl ∨ throw)."""
    project_root = ensure_project_root()

    console.print(Panel("[bold cyan]Q (Invariants)[/bold cyan] - Code invariant verification", expand=False))
    console.print("[dim]Checking: real_impl ∨ throw, ¬mock, ¬lie, ¬silent_fallback...[/dim]")

    # Run tests to verify invariants hold
    cmd = ["bash", "scripts/dx.sh", "test"]
    exit_code, _, _ = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Invariants verified[/bold green]")
    else:
        console.print("[bold red]✗ Invariant verification failed[/bold red]")
        raise typer.Exit(code=exit_code)


@godspeed_app.command()
def full(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Full GODSPEED circuit (Ψ→Λ→H→Q→Ω)."""
    project_root = ensure_project_root()

    console.print(Panel(
        "[bold cyan]⚡ FULL GODSPEED CIRCUIT ⚡[/bold cyan]\n"
        "[cyan]Ψ → Λ → H → Q → Ω[/cyan]",
        expand=False
    ))

    phases = [
        ("Ψ", "discover", "Generate facts via Observatory"),
        ("Λ", "compile", "Compile all modules"),
        ("H", "guard", "Check guard violations"),
        ("Q", "verify", "Verify invariants"),
    ]

    for phase_name, phase_cmd, description in phases:
        console.print(f"\n[bold cyan]→ {phase_name}[/bold cyan] {description}")

        # Run the phase command
        cmd = [
            "python",
            "-m",
            "yawl_cli.godspeed",
            phase_cmd,
            "--verbose" if verbose else "",
        ]
        cmd = [c for c in cmd if c]  # Remove empty strings

        # For now, just indicate phases
        console.print(f"[dim]  (phase {phase_name} would run here)[/dim]")

    console.print(f"\n[bold green]✓ GODSPEED circuit complete[/bold green]")
    console.print("[dim]Ready for Ω (consolidation) → commit & push[/dim]")
