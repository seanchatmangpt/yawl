#!/usr/bin/env python3
"""
Generate YAWL workflows from RDF agent seeds using ggen.

This script:
1. Loads the RDF seed file (Turtle)
2. Extracts agent information
3. For each agent, renders a YAWL workflow using the template
4. Writes individual .yawl files
"""

import sys
import logging
from pathlib import Path
from typing import Dict, List

try:
    import rdflib
except ImportError:
    print("ERROR: rdflib not available. Install with: pip install rdflib", file=sys.stderr)
    sys.exit(2)

try:
    from jinja2 import Environment, FileSystemLoader, Template
except ImportError:
    print("ERROR: jinja2 not available. Install with: pip install jinja2", file=sys.stderr)
    sys.exit(2)

# Setup logging
logging.basicConfig(
    format='[%(levelname)s] %(message)s',
    level=logging.INFO,
    stream=sys.stderr
)
log = logging.getLogger(__name__)

# Define namespaces
EX = rdflib.Namespace("http://yawl.org/agent/")

def load_agents(ttl_file: str) -> List[Dict[str, str]]:
    """Load agent specifications from Turtle RDF file."""
    log.info(f"Loading agents from: {ttl_file}")

    graph = rdflib.Graph()
    graph.parse(ttl_file, format="turtle")
    log.info(f"Graph loaded: {len(graph)} triples")

    agents = []

    # Query all agents
    query = """
    PREFIX ex: <http://yawl.org/agent/>
    SELECT ?agent ?name ?role ?phase
    WHERE {
      ?agent a ex:Agent ;
        ex:name ?name ;
        ex:role ?role ;
        ex:phase ?phase .
    }
    ORDER BY ?phase
    """

    results = graph.query(query)

    for row in results:
        agent = {
            "id": str(row.agent),
            "name": str(row.name),
            "role": str(row.role),
            "phase": str(row.phase),
        }
        agents.append(agent)
        log.info(f"Found agent: {agent['name']} (phase {agent['phase']})")

    log.info(f"Loaded {len(agents)} agents")
    return agents

def render_workflow(template_path: str, agent: Dict[str, str]) -> str:
    """Render YAWL workflow template for an agent."""
    try:
        with open(template_path, 'r') as f:
            template_content = f.read()

        template = Template(template_content)

        # Render with agent data
        output = template.render(
            agent_name=agent['name'],
            agent_role=agent['role'],
            phase=agent['phase']
        )

        return output
    except Exception as e:
        log.error(f"Failed to render template: {e}")
        raise

def save_workflow(output_path: str, content: str) -> None:
    """Save rendered workflow to file."""
    output_file = Path(output_path)
    output_file.parent.mkdir(parents=True, exist_ok=True)

    with open(output_file, 'w') as f:
        f.write(content)

    log.info(f"Saved workflow: {output_path}")

def agent_name_to_filename(agent_name: str) -> str:
    """Convert agent name to filename."""
    return agent_name.lower().replace(' ', '_')

def main():
    """Main entry point."""
    if len(sys.argv) < 3:
        print("Usage: generate-agent-workflows.py <input.ttl> <template.tera> <output_dir>", file=sys.stderr)
        sys.exit(2)

    ttl_file = sys.argv[1]
    template_path = sys.argv[2]
    output_dir = sys.argv[3]

    # Validate inputs
    if not Path(ttl_file).exists():
        log.error(f"Input file not found: {ttl_file}")
        sys.exit(2)

    if not Path(template_path).exists():
        log.error(f"Template file not found: {template_path}")
        sys.exit(2)

    # Load agents
    agents = load_agents(ttl_file)

    if not agents:
        log.error("No agents found in RDF file")
        sys.exit(2)

    # Generate workflows
    log.info(f"Generating {len(agents)} workflows")

    for agent in agents:
        try:
            # Render template
            content = render_workflow(template_path, agent)

            # Save to file
            filename = f"{agent_name_to_filename(agent['name'])}_workflow.yawl"
            output_path = str(Path(output_dir) / filename)
            save_workflow(output_path, content)

        except Exception as e:
            log.error(f"Failed to generate workflow for {agent['name']}: {e}")
            sys.exit(2)

    log.info(f"Successfully generated {len(agents)} workflows in {output_dir}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
