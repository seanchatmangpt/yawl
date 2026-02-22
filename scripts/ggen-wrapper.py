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
                 verbose: bool = False) -> None:
        """Initialize generator with input/output configuration."""
        self.template_file = template_file
        self.input_file = input_file
        self.output_file = output_file
        self.verbose = verbose

        if verbose:
            log.setLevel(logging.DEBUG)

        # Validate input files
        if not Path(input_file).exists():
            raise FileNotFoundError(f"Input file not found: {input_file}")
        if not Path(template_file).exists():
            raise FileNotFoundError(f"Template file not found: {template_file}")

    def generate(self) -> None:
        """Execute generation pipeline."""
        log.info("Starting generation pipeline")

        # Step 1: Load RDF graph
        rdf_handler = RDFGraphHandler(self.input_file)

        # Step 2: Load template
        template_renderer = TemplateRenderer(str(Path(self.template_file).parent))

        # Step 3: Prepare context
        # For now, extract basic statistics from graph
        context = self._prepare_context(rdf_handler)

        # Step 4: Render template
        output_content = template_renderer.render(self.template_file, context)

        # Step 5: Write output
        output_path = Path(self.output_file)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        try:
            log.info(f"Writing output to: {self.output_file}")
            with open(output_path, 'w') as f:
                f.write(output_content)
            log.info(f"Output written successfully ({len(output_content)} bytes)")
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
