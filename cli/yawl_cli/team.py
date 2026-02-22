"""Team operations (experimental) - coordinate multi-agent work."""

from typing import Optional

import typer
from rich.console import Console
from rich.table import Table

from yawl_cli.utils import ensure_project_root, run_shell_cmd

console = Console()
team_app = typer.Typer(no_args_is_help=True)


@team_app.command()
def create(
    name: str = typer.Argument(..., help="Team name"),
    quantums: str = typer.Option(..., "--quantums", "-q", help="Comma-separated quantum names"),
    agents: int = typer.Option(2, "--agents", "-a", help="Number of agents (2-5)"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Create a new team for parallel multi-quantum work."""
    project_root = ensure_project_root()

    if agents < 2 or agents > 5:
        console.print("[bold red]✗ Error:[/bold red] Agent count must be 2-5")
        raise typer.Exit(code=1)

    quantum_list = [q.strip() for q in quantums.split(",")]

    console.print(f"[bold cyan]Creating team: {name}[/bold cyan]")
    console.print(f"[dim]Quantums: {', '.join(quantum_list)}[/dim]")
    console.print(f"[dim]Agents: {agents}[/dim]")

    # Run team creation script
    cmd = [
        "bash",
        ".claude/hooks/team-create.sh",
        name,
        ",".join(quantum_list),
        str(agents),
    ]

    exit_code, stdout, _ = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Team created[/bold green]")
        if stdout:
            console.print(f"[dim]{stdout}[/dim]")
    else:
        console.print("[bold red]✗ Team creation failed[/bold red]")
        raise typer.Exit(code=exit_code)


@team_app.command()
def list() -> None:
    """List active and completed teams."""
    project_root = ensure_project_root()

    # Check for team state directory
    team_dir = project_root / ".team-state"

    if not team_dir.exists():
        console.print("[yellow]No teams created yet[/yellow]")
        return

    # List team directories
    teams = sorted([d for d in team_dir.iterdir() if d.is_dir()])

    if not teams:
        console.print("[yellow]No teams found[/yellow]")
        return

    table = Table(title="Teams")
    table.add_column("Team ID", style="cyan")
    table.add_column("Status", style="green")
    table.add_column("Agents", style="yellow")
    table.add_column("Tasks", style="magenta")

    for team_path in teams:
        team_id = team_path.name
        # Would read metadata.json here
        table.add_row(team_id, "active", "3", "5")

    console.print(table)


@team_app.command()
def resume(
    team_id: str = typer.Argument(..., help="Team ID to resume"),
) -> None:
    """Resume an existing team session."""
    project_root = ensure_project_root()

    console.print(f"[bold cyan]Resuming team: {team_id}[/bold cyan]")

    # Run team resume script
    cmd = ["bash", ".claude/hooks/team-resume.sh", team_id]

    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print("[bold green]✓ Team resumed[/bold green]")
        if stdout:
            console.print(f"[dim]{stdout}[/dim]")
    else:
        console.print("[bold red]✗ Resume failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@team_app.command()
def status(
    team_id: Optional[str] = typer.Argument(None, help="Team ID (optional)"),
) -> None:
    """Show team status and progress."""
    project_root = ensure_project_root()

    if team_id:
        console.print(f"[bold cyan]Team status: {team_id}[/bold cyan]")
    else:
        console.print("[bold cyan]Team status[/bold cyan]")

    # Would read team state and display status
    console.print("[dim](Team status reporting under development)[/dim]")


@team_app.command()
def message(
    team_id: str = typer.Argument(..., help="Team ID"),
    agent: str = typer.Argument(..., help="Agent name"),
    text: str = typer.Argument(..., help="Message text"),
) -> None:
    """Send a message to a team agent."""
    project_root = ensure_project_root()

    console.print(f"[bold cyan]Sending message to {team_id}/{agent}[/bold cyan]")
    console.print(f"[dim]{text}[/dim]")

    # Run message script
    cmd = [
        "bash",
        ".claude/hooks/team-message.sh",
        team_id,
        agent,
        text,
    ]

    exit_code, _, _ = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print("[bold green]✓ Message sent[/bold green]")
    else:
        console.print("[bold red]✗ Send failed[/bold red]")
        raise typer.Exit(code=exit_code)


@team_app.command()
def consolidate(
    team_id: str = typer.Argument(..., help="Team ID"),
    message: str = typer.Option(..., "--message", "-m", help="Commit message"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Consolidate team work (compile, validate, commit)."""
    project_root = ensure_project_root()

    console.print(f"[bold cyan]Consolidating team: {team_id}[/bold cyan]")
    console.print("[dim]Running: Compile → Validate → Commit[/dim]")

    # Run consolidation script
    cmd = [
        "bash",
        ".claude/hooks/team-consolidate.sh",
        team_id,
        "--message",
        message,
    ]
    if verbose:
        cmd.append("--verbose")

    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Team consolidated and committed[/bold green]")
    else:
        console.print("[bold red]✗ Consolidation failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)
