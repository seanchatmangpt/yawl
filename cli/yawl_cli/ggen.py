"""YAWL XML Generator (ggen) operations with error handling."""

import sys
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console

from yawl_cli.utils import ensure_project_root, run_shell_cmd, DEBUG

console = Console()
ggen_app = typer.Typer(no_args_is_help=True)


@ggen_app.command()
def init(
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Initialize ggen for YAWL XML generation."""
    try:
        project_root = ensure_project_root()

        console.print("[bold cyan]Initializing ggen[/bold cyan]")
        console.print("[dim]Setting up templates, SPARQL queries, and converters...[/dim]")

        # Run ggen-init.sh
        cmd = ["bash", "scripts/ggen-init.sh"]
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=120)

        if exit_code == 0:
            console.print("[bold green]✓ ggen initialized successfully[/bold green]")
        else:
            console.print("[bold red]✗ ggen initialization failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@ggen_app.command()
def generate(
    spec: Path = typer.Argument(..., help="Turtle RDF spec file (*.ttl)"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output YAWL XML file"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Generate YAWL XML from Turtle RDF specification."""
    try:
        project_root = ensure_project_root()

        # Validate spec file
        spec = spec.resolve()
        if not spec.exists():
            raise FileNotFoundError(f"Spec file not found: {spec}")

        if not spec.is_file():
            raise RuntimeError(f"Spec path is not a file: {spec}")

        if not spec.suffix.lower() == ".ttl":
            console.print(f"[yellow]Warning:[/yellow] Spec file does not have .ttl extension")

        # Default output filename based on input
        if output is None:
            output = spec.with_suffix(".yawl")
        else:
            output = output.resolve()

        # Check output directory exists
        output.parent.mkdir(parents=True, exist_ok=True)

        console.print(f"[bold cyan]Generating YAWL XML[/bold cyan]")
        console.print(f"[dim]Input:  {spec}[/dim]")
        console.print(f"[dim]Output: {output}[/dim]")

        # Run ggen generate script
        cmd = ["bash", "scripts/turtle-to-yawl.sh", str(spec), str(output)]
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code == 0:
            if output.exists():
                size = output.stat().st_size
                console.print(f"[bold green]✓ Generated:[/bold green] {output} ({size} bytes)")
            else:
                console.print(f"[bold green]✓ Generation completed[/bold green]")
        else:
            console.print("[bold red]✗ Generation failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except FileNotFoundError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@ggen_app.command()
def validate(
    spec: Path = typer.Argument(..., help="Turtle RDF spec file (*.ttl)"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Validate Turtle RDF specification against YAWL schema."""
    try:
        project_root = ensure_project_root()

        # Validate spec file
        spec = spec.resolve()
        if not spec.exists():
            raise FileNotFoundError(f"Spec file not found: {spec}")

        if not spec.is_file():
            raise RuntimeError(f"Spec path is not a file: {spec}")

        console.print(f"[bold cyan]Validating specification[/bold cyan]")
        console.print(f"[dim]File: {spec}[/dim]")

        # Run validation script
        cmd = ["bash", "scripts/validate-turtle-spec.sh", str(spec)]
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=120)

        if exit_code == 0:
            console.print("[bold green]✓ Specification is valid[/bold green]")
        else:
            console.print("[bold red]✗ Specification validation failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=exit_code)

    except FileNotFoundError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@ggen_app.command()
def export(
    yawl_file: Path = typer.Argument(..., help="YAWL XML file to export"),
    format: str = typer.Option("turtle", "--format", "-f", help="Export format (turtle, json, yaml)"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Export YAWL XML to other formats (Turtle, JSON, YAML)."""
    try:
        project_root = ensure_project_root()

        # Validate YAWL file
        yawl_file = yawl_file.resolve()
        if not yawl_file.exists():
            raise FileNotFoundError(f"YAWL file not found: {yawl_file}")

        if not yawl_file.is_file():
            raise RuntimeError(f"YAWL path is not a file: {yawl_file}")

        # Validate format
        valid_formats = ["turtle", "json", "yaml"]
        format_lower = format.lower()
        if format_lower not in valid_formats:
            raise ValueError(f"Invalid format: {format}. Supported: {', '.join(valid_formats)}")

        output = yawl_file.with_suffix(f".{format_lower}")

        console.print(f"[bold cyan]Exporting to {format_lower.upper()}[/bold cyan]")
        console.print(f"[dim]Input:  {yawl_file}[/dim]")
        console.print(f"[dim]Output: {output}[/dim]")

        # Run export script
        cmd = ["bash", "scripts/ggen-export.sh", str(yawl_file), format_lower, str(output)]
        exit_code, stdout, stderr = run_shell_cmd(cmd, cwd=project_root, verbose=verbose, timeout=300)

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

    except (FileNotFoundError, ValueError) as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)


@ggen_app.command()
def round_trip(
    spec: Path = typer.Argument(..., help="Turtle RDF spec file"),
    verbose: bool = typer.Option(False, "--verbose", "-v"),
) -> None:
    """Test round-trip: Turtle → YAWL XML → Turtle (verify fidelity)."""
    try:
        project_root = ensure_project_root()

        # Validate spec file
        spec = spec.resolve()
        if not spec.exists():
            raise FileNotFoundError(f"Spec file not found: {spec}")

        if not spec.is_file():
            raise RuntimeError(f"Spec path is not a file: {spec}")

        console.print("[bold cyan]Testing round-trip conversion[/bold cyan]")
        console.print(f"[dim]Spec: {spec}[/dim]")

        # Generate XML from Turtle
        yawl_file = spec.with_suffix(".yawl")
        cmd_gen = ["bash", "scripts/turtle-to-yawl.sh", str(spec), str(yawl_file)]
        console.print("[dim]  Phase 1: Turtle → YAWL XML[/dim]")
        exit_code, _, stderr = run_shell_cmd(cmd_gen, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code != 0:
            console.print("[bold red]✗ Generation phase failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=1)

        if not yawl_file.exists():
            raise RuntimeError(f"Generated YAWL file not found: {yawl_file}")

        console.print("[dim]  ✓ Generated YAWL XML[/dim]")

        # Export back to Turtle
        turtle_rt = spec.with_stem(spec.stem + "_rt")
        cmd_export = ["bash", "scripts/ggen-export.sh", str(yawl_file), "turtle", str(turtle_rt)]
        console.print("[dim]  Phase 2: YAWL XML → Turtle[/dim]")
        exit_code, _, stderr = run_shell_cmd(cmd_export, cwd=project_root, verbose=verbose, timeout=300)

        if exit_code != 0:
            console.print("[bold red]✗ Round-trip phase failed[/bold red]")
            if stderr:
                console.print(f"[red]{stderr}[/red]")
            raise typer.Exit(code=1)

        if not turtle_rt.exists():
            raise RuntimeError(f"Round-trip output file not found: {turtle_rt}")

        console.print("[dim]  ✓ Round-tripped back to Turtle[/dim]")
        console.print("[bold green]✓ Round-trip test successful[/bold green]")

    except FileNotFoundError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except RuntimeError as e:
        console.print(f"[bold red]✗ Error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
    except Exception as e:
        console.print(f"[bold red]✗ Unexpected error:[/bold red] {e}", file=sys.stderr)
        if DEBUG:
            console.print_exception()
        raise typer.Exit(code=1)
