"""Configuration management commands."""

from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table

from yawl_cli.utils import ensure_project_root, Config

console = Console()
app = typer.Typer(no_args_is_help=True)


@app.command()
def show(
    verbose: bool = typer.Option(False, "--verbose", "-v", help="Show all details"),
) -> None:
    """Show current configuration."""
    try:
        project_root = ensure_project_root()
        config = Config.from_project(project_root)

        console.print(Panel("[bold cyan]YAWL Configuration[/bold cyan]", expand=False))

        if config.config_file:
            console.print(f"\n[bold]Configuration file:[/bold] {config.config_file}")
        else:
            console.print("\n[yellow]No configuration file loaded[/yellow]")

        # Display configuration structure
        if config.config_data:
            console.print("\n[bold cyan]Settings:[/bold cyan]")
            _print_config_dict(config.config_data)
        else:
            console.print("[yellow]No configuration data loaded[/yellow]")

    except Exception as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        raise typer.Exit(code=1)


@app.command()
def get(
    key: str = typer.Argument(..., help="Configuration key (dot notation, e.g., build.parallel)"),
) -> None:
    """Get configuration value."""
    try:
        project_root = ensure_project_root()
        config = Config.from_project(project_root)

        value = config.get(key)
        if value is None:
            console.print(f"[yellow]Key not found:[/yellow] {key}")
            raise typer.Exit(code=1)

        console.print(f"[bold]{key}:[/bold] {value}")

    except Exception as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        raise typer.Exit(code=1)


@app.command()
def set(
    key: str = typer.Argument(..., help="Configuration key (dot notation)"),
    value: str = typer.Argument(..., help="Configuration value"),
    project: bool = typer.Option(
        True, "--project", "-p", help="Save to project config (default)"
    ),
    user: bool = typer.Option(False, "--user", "-u", help="Save to user home config"),
) -> None:
    """Set configuration value."""
    try:
        project_root = ensure_project_root()
        config = Config.from_project(project_root)

        # Parse value to proper type
        parsed_value: any
        if value.lower() in ("true", "yes", "1"):
            parsed_value = True
        elif value.lower() in ("false", "no", "0"):
            parsed_value = False
        elif value.isdigit():
            parsed_value = int(value)
        else:
            parsed_value = value

        config.set(key, parsed_value)

        # Determine where to save
        if user:
            config_file = Path.home() / ".yawl" / "config.yaml"
        else:
            config_file = project_root / ".yawl" / "config.yaml"

        config.save(config_file)
        console.print(f"[bold green]✓[/bold green] Set {key} = {parsed_value}")
        console.print(f"  Saved to: {config_file}")

    except Exception as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        raise typer.Exit(code=1)


@app.command()
def reset() -> None:
    """Reset configuration to defaults."""
    try:
        project_root = ensure_project_root()

        # Remove project config
        config_file = project_root / ".yawl" / "config.yaml"
        if config_file.exists():
            from rich.prompt import Confirm
            if Confirm.ask(f"Remove {config_file}?"):
                config_file.unlink()
                console.print(f"[bold green]✓[/bold green] Removed {config_file}")
        else:
            console.print(f"[yellow]No config file at[/yellow] {config_file}")

        console.print("[bold green]✓ Configuration reset to defaults[/bold green]")

    except Exception as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        raise typer.Exit(code=1)


@app.command()
def locations() -> None:
    """Show configuration file locations."""
    project_root = Path.cwd()
    try:
        project_root = ensure_project_root()
    except Exception:
        pass

    table = Table(title="Configuration File Locations")
    table.add_column("Location", style="cyan")
    table.add_column("Path", style="magenta")
    table.add_column("Status", style="green")

    locations = [
        ("Project", project_root / ".yawl" / "config.yaml"),
        ("User Home", Path.home() / ".yawl" / "config.yaml"),
        ("System-wide", Path("/etc/yawl/config.yaml")),
    ]

    for name, path in locations:
        status = "[bold green]exists[/bold green]" if path.exists() else "[dim]not found[/dim]"
        table.add_row(name, str(path), status)

    console.print(table)
    console.print(
        "\n[dim]Configuration files are loaded in order (top to bottom).[/dim]"
    )
    console.print("[dim]Later files override earlier ones.[/dim]")


def _print_config_dict(data: dict, indent: int = 0) -> None:
    """Recursively print configuration dictionary."""
    for key, value in data.items():
        prefix = "  " * indent
        if isinstance(value, dict):
            console.print(f"{prefix}[bold]{key}:[/bold]")
            _print_config_dict(value, indent + 1)
        elif isinstance(value, bool):
            console.print(f"{prefix}{key}: [yellow]{str(value)}[/yellow]")
        elif isinstance(value, (int, float)):
            console.print(f"{prefix}{key}: [cyan]{value}[/cyan]")
        elif isinstance(value, list):
            console.print(f"{prefix}{key}: {value}")
        else:
            console.print(f"{prefix}{key}: {value}")


# For backwards compatibility with old config module imports
if __name__ == "__main__":
    app()
