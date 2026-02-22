#!/usr/bin/env python3
# ==============================================================================
# ggen-wrapper.py — Python-based ggen implementation
#
# Provides ggen-like functionality for YAWL Turtle → YAWL XML generation:
# - Reads Turtle RDF ontologies
# - Executes SPARQL queries
# - Renders Tera/Jinja2 templates
# - Outputs generated artifacts
#
# Designed as a drop-in replacement for the ggen CLI when Rust ggen-cli
# is not available (e.g., on crates.io).
#
# Usage:
#   python3 scripts/ggen-wrapper.py generate \
#       --template templates/workflow.yawl.tera \
#       --input ontology/process.ttl \
#       --output output/process.yawl
#
# Exit codes:
#   0 = success
#   1 = transient error
#   2 = fatal error
# ==============================================================================

import argparse
import json
import logging
import sys
from pathlib import Path
from typing import Optional, Dict, Any

try:
    import rdflib
    from rdflib.namespace import RDF, RDFS, OWL, XSD
except ImportError as e:
    print(f"ERROR: rdflib import failed: {e}", file=sys.stderr)
    print("Install with: pip install rdflib", file=sys.stderr)
    sys.exit(2)
except Exception as e:
    print(f"ERROR: Unexpected error importing rdflib: {e}", file=sys.stderr)
    sys.exit(2)

try:
    from jinja2 import Environment, FileSystemLoader, Template
except ImportError as e:
    print(f"ERROR: jinja2 import failed: {e}", file=sys.stderr)
    print("Install with: pip install jinja2", file=sys.stderr)
    sys.exit(2)
except Exception as e:
    print(f"ERROR: Unexpected error importing jinja2: {e}", file=sys.stderr)
    sys.exit(2)

# ──────────────────────────────────────────────────────────────────────────
# Logging setup
# ──────────────────────────────────────────────────────────────────────────

logging.basicConfig(
    format='[%(levelname)s] %(message)s',
    level=logging.INFO,
    stream=sys.stderr
)
log = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────────────
# Constants
# ──────────────────────────────────────────────────────────────────────────

VERSION = "0.1.0"
YAWL_NS = rdflib.Namespace("http://yawlfoundation.org/yawl#")
YAWL_API_NS = rdflib.Namespace("http://yawlfoundation.org/yawl/api#")
YAWL_PATTERN_NS = rdflib.Namespace("http://yawlfoundation.org/yawl/pattern#")

# ──────────────────────────────────────────────────────────────────────────
# RDF Graph Handler
# ──────────────────────────────────────────────────────────────────────────

class RDFGraphHandler:
    """Manages RDF graph loading and SPARQL querying."""

    def __init__(self, ttl_file: str) -> None:
        """Load Turtle RDF file into graph."""
        self.graph = rdflib.Graph()
        try:
            log.info(f"Loading Turtle RDF: {ttl_file}")
            self.graph.parse(ttl_file, format="turtle")
            log.info(f"Graph loaded: {len(self.graph)} triples")
        except Exception as e:
            log.error(f"Failed to parse Turtle: {e}")
            raise

    def query_select(self, sparql_query: str) -> list:
        """Execute SPARQL SELECT query, return results as list of dicts."""
        try:
            log.debug(f"Executing SPARQL SELECT query")
            results = self.graph.query(sparql_query)
            rows = []
            for row in results:
                # Convert row to dict, using variable names as keys
                row_dict = {}
                for var in results.vars:
                    row_dict[f"?{var}"] = str(row[var]) if row[var] else None
                rows.append(row_dict)
            log.info(f"SPARQL query returned {len(rows)} rows")
            return rows
        except Exception as e:
            log.error(f"SPARQL query failed: {e}")
            raise

    def query_ask(self, sparql_query: str) -> bool:
        """Execute SPARQL ASK query, return boolean result."""
        try:
            results = self.graph.query(sparql_query)
            return bool(results)
        except Exception as e:
            log.error(f"SPARQL ASK query failed: {e}")
            raise

    def query_construct(self, sparql_query: str) -> Any:
        """Execute SPARQL CONSTRUCT query, return enriched graph."""
        try:
            results = self.graph.query(sparql_query)
            # Merge results into graph
            for triple in results:
                self.graph.add(triple)
            return self.graph
        except Exception as e:
            log.error(f"SPARQL CONSTRUCT query failed: {e}")
            raise

# ──────────────────────────────────────────────────────────────────────────
# Template Renderer
# ──────────────────────────────────────────────────────────────────────────

class TemplateRenderer:
    """Renders Tera/Jinja2 templates with query data."""

    def __init__(self, template_dir: Optional[str] = None) -> None:
        """Initialize Jinja2 environment."""
        if template_dir:
            self.env = Environment(loader=FileSystemLoader(template_dir))
        else:
            self.env = Environment(loader=FileSystemLoader("."))

    def render(self, template_path: str, context: Dict[str, Any]) -> str:
        """Render template with given context."""
        try:
            log.info(f"Loading template: {template_path}")

            # Try to load from template directory first
            try:
                template = self.env.get_template(Path(template_path).name)
            except:
                # Fall back to direct file loading
                with open(template_path, 'r') as f:
                    template_content = f.read()
                template = self.env.from_string(template_content)

            log.info(f"Rendering template with {len(context)} context variables")
            result = template.render(context)
            log.info(f"Template rendered successfully ({len(result)} bytes)")
            return result
        except Exception as e:
            log.error(f"Template rendering failed: {e}")
            raise

# ──────────────────────────────────────────────────────────────────────────
# SPARQL Query Loader
# ──────────────────────────────────────────────────────────────────────────

class SPARQLQueryLoader:
    """Loads and manages SPARQL queries from files."""

    @staticmethod
    def load_query_file(query_path: str) -> str:
        """Load SPARQL query from file."""
        try:
            log.info(f"Loading SPARQL query: {query_path}")
            with open(query_path, 'r') as f:
                query = f.read()
            log.debug(f"Query loaded ({len(query)} bytes)")
            return query
        except FileNotFoundError:
            log.error(f"Query file not found: {query_path}")
            raise
        except Exception as e:
            log.error(f"Failed to load query: {e}")
            raise

    @staticmethod
    def load_query_string(query_str: str) -> str:
        """Return query string as-is."""
        return query_str

# ──────────────────────────────────────────────────────────────────────────
# Generator (Main Orchestrator)
# ──────────────────────────────────────────────────────────────────────────

class Generator:
    """Main orchestrator for Turtle → Template → Output generation."""

    def __init__(self, template_file: str, input_file: str, output_file: str,
                 sparql_file: Optional[str] = None, verbose: bool = False) -> None:
        """Initialize generator with input/output configuration."""
        self.template_file = template_file
        self.input_file = input_file
        self.output_file = output_file
        self.sparql_file = sparql_file
        self.verbose = verbose

        if verbose:
            log.setLevel(logging.DEBUG)

        # Validate input files
        if not Path(input_file).exists():
            raise FileNotFoundError(f"Input file not found: {input_file}")
        if not Path(template_file).exists():
            raise FileNotFoundError(f"Template file not found: {template_file}")
        if sparql_file and not Path(sparql_file).exists():
            raise FileNotFoundError(f"SPARQL query file not found: {sparql_file}")

    def generate(self) -> None:
        """Execute generation pipeline."""
        log.info("Starting generation pipeline")

        # Step 1: Load RDF graph
        rdf_handler = RDFGraphHandler(self.input_file)

        # Step 2: Load template
        template_renderer = TemplateRenderer(str(Path(self.template_file).parent))

        # Step 3a: If SPARQL query provided, use looped generation
        if self.sparql_file:
            self._generate_with_sparql_loop(rdf_handler, template_renderer)
        else:
            # Step 3b: Prepare context for single generation
            context = self._prepare_context(rdf_handler)

            # Step 4: Render template
            output_content = template_renderer.render(self.template_file, context)

            # Step 5: Write output
            self._write_output(self.output_file, output_content)

    def _generate_with_sparql_loop(self, rdf_handler: RDFGraphHandler,
                                    template_renderer: TemplateRenderer) -> None:
        """Generate multiple outputs using SPARQL query looping."""
        log.info(f"SPARQL-driven generation mode")

        # Load SPARQL query
        sparql_query = SPARQLQueryLoader.load_query_file(self.sparql_file)
        log.info(f"Loaded SPARQL query ({len(sparql_query)} bytes)")

        # Execute SPARQL query
        query_results = rdf_handler.query_select(sparql_query)
        log.info(f"SPARQL query returned {len(query_results)} result rows")

        if not query_results:
            log.warning("SPARQL query returned no results")
            return

        # Ensure output is a directory
        output_dir = Path(self.output_file)
        output_dir.mkdir(parents=True, exist_ok=True)

        # For each result row, render template
        for idx, row in enumerate(query_results):
            # Prepare context from this row
            context = self._prepare_sparql_context(row)

            # Render template
            output_content = template_renderer.render(self.template_file, context)

            # Determine output filename from context
            filename = context.get("agent_name", f"agent_{idx}").replace(" ", "_").lower()
            output_file = output_dir / f"{filename}_workflow.yawl"

            # Write output
            self._write_output(str(output_file), output_content)

    def _prepare_sparql_context(self, sparql_row: Dict[str, Any]) -> Dict[str, Any]:
        """Prepare template context from SPARQL result row."""
        # Remove '?' prefix from SPARQL variable names
        context = {}
        for key, value in sparql_row.items():
            clean_key = key.lstrip('?')
            context[clean_key] = value

        # Add standard metadata
        context["generator"] = "ggen-wrapper"
        context["version"] = VERSION

        return context

    def _write_output(self, output_file: str, content: str) -> None:
        """Write content to output file."""
        output_path = Path(output_file)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        try:
            log.info(f"Writing output to: {output_file}")
            with open(output_path, 'w') as f:
                f.write(content)
            log.info(f"Output written successfully ({len(content)} bytes)")
        except Exception as e:
            log.error(f"Failed to write output: {e}")
            raise

    def _prepare_context(self, rdf_handler: RDFGraphHandler) -> Dict[str, Any]:
        """Prepare template context from RDF graph."""
        context = {
            "generator": "ggen-wrapper",
            "version": VERSION,
            "graph": rdf_handler.graph,
            "graph_size": len(rdf_handler.graph),
            "tasks": [],
            "flows": [],
        }

        # Extract YAWL tasks
        try:
            for task in rdf_handler.graph.subjects(RDF.type, YAWL_NS.Task):
                task_info = {
                    "id": str(task),
                    "name": str(rdf_handler.graph.value(task, RDFS.label) or ""),
                }
                context["tasks"].append(task_info)
            log.info(f"Extracted {len(context['tasks'])} tasks from graph")
        except Exception as e:
            log.warning(f"Failed to extract tasks: {e}")

        return context

# ──────────────────────────────────────────────────────────────────────────
# CLI Interface
# ──────────────────────────────────────────────────────────────────────────

def main() -> int:
    """Main entry point."""
    # Handle --version first (before full argument parsing)
    if "--version" in sys.argv:
        print(f"ggen {VERSION} (Python wrapper implementation)")
        return 0

    parser = argparse.ArgumentParser(
        prog="ggen",
        description="Graph Generation CLI (Python implementation)"
    )

    # Subcommands
    subparsers = parser.add_subparsers(dest="command", help="Command to execute")

    # 'generate' subcommand
    gen_parser = subparsers.add_parser("generate", help="Generate from RDF + template")
    gen_parser.add_argument("--template", required=True, help="Tera/Jinja2 template file")
    gen_parser.add_argument("--input", required=True, help="RDF Turtle input file")
    gen_parser.add_argument("--output", required=True, help="Output file path (directory for SPARQL looped generation)")
    gen_parser.add_argument("--sparql", required=False, help="SPARQL query file for looped generation")
    gen_parser.add_argument("--verbose", action="store_true", help="Enable verbose output")

    # Parse args
    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 2

    if args.command == "generate":
        try:
            gen = Generator(
                template_file=args.template,
                input_file=args.input,
                output_file=args.output,
                sparql_file=getattr(args, 'sparql', None),
                verbose=args.verbose
            )
            gen.generate()
            return 0
        except FileNotFoundError as e:
            log.error(f"File error: {e}")
            return 2
        except Exception as e:
            log.error(f"Generation failed: {e}")
            return 2

    return 0

if __name__ == "__main__":
    sys.exit(main())
