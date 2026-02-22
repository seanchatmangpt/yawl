"""gregverse - Workflow conversion and export operations."""

from pathlib import Path
from typing import Optional

import typer
from rich.console import Console

from yawl_cli.utils import ensure_project_root, run_shell_cmd

console = Console()
gregverse_app = typer.Typer(no_args_is_help=True)


@gregverse_app.command()
def import_workflow(
    file: Path = typer.Argument(..., help="Workflow file to import (XPDL, BPMN, etc.)"),
    format: str = typer.Option("auto", "--format", "-f", help="Source format (auto, xpdl, bpmn, petri)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output YAWL file"),
) -> None:
    """Import workflow from external format (XPDL, BPMN, Petri net)."""
    project_root = ensure_project_root()

    if not file.exists():
        console.print(f"[bold red]✗ Error:[/bold red] File not found: {file}")
        raise typer.Exit(code=1)

    if output is None:
        output = file.with_suffix(".yawl")

    console.print(f"[bold cyan]Importing workflow[/bold cyan]")
    console.print(f"[dim]Format: {format}[/dim]")
    console.print(f"[dim]Input:  {file}[/dim]")
    console.print(f"[dim]Output: {output}[/dim]")

    cmd = ["bash", "scripts/gregverse-import.sh", str(file), format, str(output)]
    exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print(f"[bold green]✓ Imported:[/bold green] {output}")
    else:
        console.print("[bold red]✗ Import failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@gregverse_app.command()
def export_workflow(
    yawl_file: Path = typer.Argument(..., help="YAWL XML file to export"),
    format: str = typer.Option("bpmn", "--format", "-f", help="Target format (bpmn, xpdl, petri, json)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o"),
) -> None:
    """Export YAWL workflow to external format (BPMN, XPDL, Petri, JSON)."""
    project_root = ensure_project_root()

    if not yawl_file.exists():
        console.print(f"[bold red]✗ Error:[/bold red] File not found: {yawl_file}")
        raise typer.Exit(code=1)

    if output is None:
        output = yawl_file.with_suffix(f".{format}")

    console.print(f"[bold cyan]Exporting to {format.upper()}[/bold cyan]")
    console.print(f"[dim]Input:  {yawl_file}[/dim]")
    console.print(f"[dim]Output: {output}[/dim]")

    cmd = ["bash", "scripts/gregverse-export.sh", str(yawl_file), format, str(output)]
    exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print(f"[bold green]✓ Exported:[/bold green] {output}")
    else:
        console.print("[bold red]✗ Export failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)


@gregverse_app.command()
def convert(
    input_file: Path = typer.Argument(..., help="Input workflow file"),
    input_format: str = typer.Argument(..., help="Input format (xpdl, bpmn, petri, yawl)"),
    output_format: str = typer.Argument(..., help="Output format (xpdl, bpmn, petri, yawl)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o"),
) -> None:
    """Convert between workflow formats (XPDL ↔ BPMN ↔ Petri ↔ YAWL)."""
    project_root = ensure_project_root()

    if not input_file.exists():
        console.print(f"[bold red]✗ Error:[/bold red] File not found: {input_file}")
        raise typer.Exit(code=1)

    if output is None:
        output = input_file.with_suffix(f".{output_format}")

    console.print(f"[bold cyan]Converting {input_format.upper()} → {output_format.upper()}[/bold cyan]")
    console.print(f"[dim]Input:  {input_file}[/dim]")
    console.print(f"[dim]Output: {output}[/dim]")

    cmd = [
        "bash",
        "scripts/gregverse-convert.sh",
        str(input_file),
        input_format,
        output_format,
        str(output),
    ]
    exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root)

    if exit_code == 0:
        console.print(f"[bold green]✓ Converted:[/bold green] {output}")
    else:
        console.print("[bold red]✗ Conversion failed[/bold red]")
        if stderr:
            console.print(f"[red]{stderr}[/red]")
        raise typer.Exit(code=exit_code)
