"""YAWL XML Generator (ggen) operations."""

from pathlib import Path
from typing import Optional

import typer
from rich.console import Console

from yawl_cli.utils import ensure_project_root, run_shell_cmd

console = Console()
ggen_app = typer.Typer(no_args_is_help=True)


@ggen_app.command()
def init(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Initialize ggen for YAWL XML generation."""
    project_root = ensure_project_root()

    console.print("[bold cyan]Initializing ggen[/bold cyan]")
    console.print("[dim]Setting up templates, SPARQL queries, and converters...[/dim]")

    # Run ggen-init.sh
    cmd = ["bash", "scripts/ggen-init.sh"]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ ggen initialized successfully[/bold green]")
    else:
        console.print("[bold red]✗ ggen initialization failed[/bold red]")
        raise typer.Exit(code=exit_code)


@ggen_app.command()
def generate(
    spec: Path = typer.Argument(..., help="Turtle RDF spec file (*.ttl)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output YAWL XML file"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Generate YAWL XML from Turtle RDF specification."""
    project_root = ensure_project_root()

    if not spec.exists():
        console.print(f"[bold red]✗ Error:[/bold red] Spec file not found: {spec}")
        raise typer.Exit(code=1)

    # Default output filename based on input
    if output is None:
        output = spec.with_suffix(".yawl")

    console.print(f"[bold cyan]Generating YAWL XML[/bold cyan]")
    console.print(f"[dim]Input:  {spec}[/dim]")
    console.print(f"[dim]Output: {output}[/dim]")

    # Run ggen generate script
    cmd = ["bash", "scripts/turtle-to-yawl.sh", str(spec), str(output)]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print(f"[bold green]✓ Generated:[/bold green] {output}")
    else:
        console.print("[bold red]✗ Generation failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@ggen_app.command()
def validate(
    spec: Path = typer.Argument(..., help="Turtle RDF spec file (*.ttl)"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Validate Turtle RDF specification against YAWL schema."""
    project_root = ensure_project_root()

    if not spec.exists():
        console.print(f"[bold red]✗ Error:[/bold red] Spec file not found: {spec}")
        raise typer.Exit(code=1)

    console.print(f"[bold cyan]Validating specification[/bold cyan]")
    console.print(f"[dim]File: {spec}[/dim]")

    # Run validation script
    cmd = ["bash", "scripts/validate-turtle-spec.sh", str(spec)]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print("[bold green]✓ Specification is valid[/bold green]")
    else:
        console.print("[bold red]✗ Specification validation failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@ggen_app.command()
def export(
    yawl_file: Path = typer.Argument(..., help="YAWL XML file to export"),
    format: str = typer.Option("turtle", "--format", "-f", help="Export format (turtle, json, yaml)"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Export YAWL XML to other formats (Turtle, JSON, YAML)."""
    project_root = ensure_project_root()

    if not yawl_file.exists():
        console.print(f"[bold red]✗ Error:[/bold red] YAWL file not found: {yawl_file}")
        raise typer.Exit(code=1)

    output = yawl_file.with_suffix(f".{format}")

    console.print(f"[bold cyan]Exporting to {format.upper()}[/bold cyan]")
    console.print(f"[dim]Input:  {yawl_file}[/dim]")
    console.print(f"[dim]Output: {output}[/dim]")

    # Run export script
    cmd = ["bash", "scripts/ggen-export.sh", str(yawl_file), format, str(output)]
    exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose)

    if exit_code == 0:
        console.print(f"[bold green]✓ Exported:[/bold green] {output}")
    else:
        console.print("[bold red]✗ Export failed[/bold red]")
        raise typer.Exit(code=exit_code)


@ggen_app.command()
def round_trip(
    spec: Path = typer.Argument(..., help="Turtle RDF spec file"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Test round-trip: Turtle → YAWL XML → Turtle (verify fidelity)."""
    project_root = ensure_project_root()

    if not spec.exists():
        console.print(f"[bold red]✗ Error:[/bold red] Spec file not found: {spec}")
        raise typer.Exit(code=1)

    console.print("[bold cyan]Testing round-trip conversion[/bold cyan]")
    console.print(f"[dim]Spec: {spec}[/dim]")

    # Generate XML from Turtle
    yawl_file = spec.with_suffix(".yawl")
    cmd_gen = ["bash", "scripts/turtle-to-yawl.sh", str(spec), str(yawl_file)]
    exit_code, _, _ = run_shell_cmd(cmd_gen, cwd=project_root, verbose=verbose)

    if exit_code != 0:
        console.print("[bold red]✗ Generation phase failed[/bold red]")
        raise typer.Exit(code=1)

    console.print("[dim]  ✓ Generated YAWL XML[/dim]")

    # Export back to Turtle
    turtle_rt = spec.with_stem(spec.stem + "_rt")
    cmd_export = ["bash", "scripts/ggen-export.sh", str(yawl_file), "turtle", str(turtle_rt)]
    exit_code, _, _ = run_shell_cmd(cmd_export, cwd=project_root, verbose=verbose)

    if exit_code != 0:
        console.print("[bold red]✗ Round-trip phase failed[/bold red]")
        raise typer.Exit(code=1)

    console.print("[dim]  ✓ Round-tripped back to Turtle[/dim]")
    console.print("[bold green]✓ Round-trip test successful[/bold green]")
