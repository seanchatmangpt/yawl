"""gregverse - Workflow conversion and export operations."""

import sys
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console

from yawl_cli.utils import ensure_project_root, run_shell_cmd, DEBUG

console = Console()
stderr_console = Console(stderr=True)
gregverse_app = typer.Typer(no_args_is_help=True)


@gregverse_app.command()
def import_workflow(
    file: Path = typer.Argument(..., help="Workflow file to import (XPDL, BPMN, etc.)"),
    format: str = typer.Option("auto", "--format", "-f", help="Source format (auto, xpdl, bpmn, petri)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output YAWL file"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Import workflow from external format (XPDL, BPMN, Petri net)."""
    try:
        project_root = ensure_project_root()

        # Validate input file
        file = file.resolve()
        if not file.exists():
            raise FileNotFoundError(f"Workflow file not found: {file}")

        if not file.is_file():
            raise RuntimeError(f"Workflow path is not a file: {file}")

        # Validate format
        valid_formats = {"auto", "xpdl", "bpmn", "petri"}
        format_lower = format.lower()
        if format_lower not in valid_formats:
            raise ValueError(
                f"Invalid format: {format}\n"
                f"Valid formats: {', '.join(sorted(valid_formats))}"
            )

        if output is None:
            output = file.with_suffix(".yawl")
        else:
            output = output.resolve()

        # Create output directory if needed
        output.parent.mkdir(parents=True, exist_ok=True)

        console.print(f"[bold cyan]Importing workflow[/bold cyan]")
        console.print(f"[dim]Format: {format_lower}[/dim]")
        console.print(f"[dim]Input:  {file}[/dim]")
        console.print(f"[dim]Output: {output}[/dim]")

        cmd = ["bash", "scripts/gregverse-import.sh", str(file), format_lower, str(output)]
        exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code == 0:
            if output.exists():
                size = output.stat().st_size
                console.print(f"[bold green]✓ Imported:[/bold green] {output} ({size} bytes)")
            else:
                console.print(f"[bold green]✓ Import completed[/bold green]")
        else:
            console.print("[bold red]✗ Import failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except FileNotFoundError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except (RuntimeError, ValueError) as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@gregverse_app.command()
def export_workflow(
    yawl_file: Path = typer.Argument(..., help="YAWL XML file to export"),
    format: str = typer.Option("bpmn", "--format", "-f", help="Target format (bpmn, xpdl, petri, json)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Export YAWL workflow to external format (BPMN, XPDL, Petri, JSON)."""
    try:
        project_root = ensure_project_root()

        # Validate YAWL file
        yawl_file = yawl_file.resolve()
        if not yawl_file.exists():
            raise FileNotFoundError(f"YAWL file not found: {yawl_file}")

        if not yawl_file.is_file():
            raise RuntimeError(f"YAWL path is not a file: {yawl_file}")

        # Validate format
        valid_formats = {"bpmn", "xpdl", "petri", "json"}
        format_lower = format.lower()
        if format_lower not in valid_formats:
            raise ValueError(
                f"Invalid format: {format}\n"
                f"Valid formats: {', '.join(sorted(valid_formats))}"
            )

        if output is None:
            output = yawl_file.with_suffix(f".{format_lower}")
        else:
            output = output.resolve()

        # Create output directory if needed
        output.parent.mkdir(parents=True, exist_ok=True)

        console.print(f"[bold cyan]Exporting to {format_lower.upper()}[/bold cyan]")
        console.print(f"[dim]Input:  {yawl_file}[/dim]")
        console.print(f"[dim]Output: {output}[/dim]")

        cmd = ["bash", "scripts/gregverse-export.sh", str(yawl_file), format_lower, str(output)]
        exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code == 0:
            if output.exists():
                size = output.stat().st_size
                console.print(f"[bold green]✓ Exported:[/bold green] {output} ({size} bytes)")
            else:
                console.print(f"[bold green]✓ Export completed[/bold green]")
        else:
            console.print("[bold red]✗ Export failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except FileNotFoundError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except (RuntimeError, ValueError) as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@gregverse_app.command()
def convert(
    input_file: Path = typer.Argument(..., help="Input workflow file"),
    input_format: str = typer.Argument(..., help="Input format (xpdl, bpmn, petri, yawl)"),
    output_format: str = typer.Argument(..., help="Output format (xpdl, bpmn, petri, yawl)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Convert between workflow formats (XPDL ↔ BPMN ↔ Petri ↔ YAWL)."""
    try:
        project_root = ensure_project_root()

        # Validate input file
        input_file = input_file.resolve()
        if not input_file.exists():
            raise FileNotFoundError(f"Input file not found: {input_file}")

        if not input_file.is_file():
            raise RuntimeError(f"Input path is not a file: {input_file}")

        # Validate formats
        valid_formats = {"xpdl", "bpmn", "petri", "yawl"}
        input_fmt_lower = input_format.lower()
        output_fmt_lower = output_format.lower()

        if input_fmt_lower not in valid_formats:
            raise ValueError(
                f"Invalid input format: {input_format}\n"
                f"Valid formats: {', '.join(sorted(valid_formats))}"
            )

        if output_fmt_lower not in valid_formats:
            raise ValueError(
                f"Invalid output format: {output_format}\n"
                f"Valid formats: {', '.join(sorted(valid_formats))}"
            )

        if input_fmt_lower == output_fmt_lower:
            console.print(
                f"[yellow]Warning:[/yellow] Input and output formats are the same ({input_fmt_lower})"
            )

        if output is None:
            output = input_file.with_suffix(f".{output_fmt_lower}")
        else:
            output = output.resolve()

        # Create output directory if needed
        output.parent.mkdir(parents=True, exist_ok=True)

        console.print(f"[bold cyan]Converting {input_fmt_lower.upper()} → {output_fmt_lower.upper()}[/bold cyan]")
        console.print(f"[dim]Input:  {input_file}[/dim]")
        console.print(f"[dim]Output: {output}[/dim]")

        cmd = [
            "bash",
            "scripts/gregverse-convert.sh",
            str(input_file),
            input_fmt_lower,
            output_fmt_lower,
            str(output),
        ]
        exit_code, _, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code == 0:
            if output.exists():
                size = output.stat().st_size
                console.print(f"[bold green]✓ Converted:[/bold green] {output} ({size} bytes)")
            else:
                console.print(f"[bold green]✓ Conversion completed[/bold green]")
        else:
            console.print("[bold red]✗ Conversion failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except FileNotFoundError as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except (RuntimeError, ValueError) as e:
        stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        stderr_console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}")
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
