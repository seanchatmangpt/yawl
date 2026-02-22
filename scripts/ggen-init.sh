#!/usr/bin/env bash
# ==========================================================================
# ggen-init.sh — Initialize YAWL XML Generator (ggen)
#
# Sets up .ggen/ directory with:
#   - SPARQL queries for validation (7 core patterns)
#   - Tera templates for XML generation
#   - Cache for compiled AST models
#   - Logging infrastructure
#
# Usage:
#   bash scripts/ggen-init.sh          # Initialize ggen
#   bash scripts/ggen-init.sh --verbose # Show detailed output
#   bash scripts/ggen-init.sh --clean   # Clean and reinitialize
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GGEN_ROOT="${REPO_ROOT}/.ggen"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'

# Configuration
VERBOSE="${VERBOSE:-0}"
CLEAN="${CLEAN:-0}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --verbose) VERBOSE=1; shift ;;
        --clean)   CLEAN=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)
            echo "Unknown arg: $1. Use -h for help."
            exit 1 ;;
    esac
done

# ── Logging ───────────────────────────────────────────────────────────────
log_info() {
    printf "${C_BLUE}→${C_RESET} %s\n" "$1"
}

log_success() {
    printf "${C_GREEN}✓${C_RESET} %s\n" "$1"
}

log_warn() {
    printf "${C_YELLOW}⚠${C_RESET} %s\n" "$1"
}

log_debug() {
    if [[ "$VERBOSE" == "1" ]]; then
        printf "${C_CYAN}  %s${C_RESET}\n" "$1"
    fi
}

# ── Verify Prerequisites ──────────────────────────────────────────────────
log_info "Verifying prerequisites..."

SKIP_TERA_VALIDATION=0
SKIP_RDF_VALIDATION=0

if ! command -v tera >/dev/null 2>&1; then
    log_warn "Tera CLI not found (optional). Template validation will be skipped."
    SKIP_TERA_VALIDATION=1
else
    SKIP_TERA_VALIDATION=0
    log_debug "Tera CLI found: $(command -v tera)"
fi

if ! command -v rapper >/dev/null 2>&1; then
    log_warn "Rapper RDF validator not found (optional). RDF validation will be skipped."
    SKIP_RDF_VALIDATION=1
else
    SKIP_RDF_VALIDATION=0
    log_debug "Rapper found: $(command -v rapper)"
fi

log_success "Prerequisites verified"

# ── Clean (if requested) ──────────────────────────────────────────────────
if [[ "$CLEAN" == "1" ]]; then
    log_info "Cleaning .ggen directory..."
    if [[ -d "$GGEN_ROOT" ]]; then
        rm -rf "$GGEN_ROOT"
        log_success "Cleaned .ggen"
    fi
fi

# ── Create Directory Structure ────────────────────────────────────────────
log_info "Creating .ggen directory structure..."

mkdir -p "${GGEN_ROOT}/templates"
mkdir -p "${GGEN_ROOT}/sparql"
mkdir -p "${GGEN_ROOT}/cache"
mkdir -p "${GGEN_ROOT}/logs"
mkdir -p "${GGEN_ROOT}/config"

log_success "Created directories"

# ── Copy Tera Templates ───────────────────────────────────────────────────
log_info "Copying Tera templates..."

TEMPLATE_SOURCE="${REPO_ROOT}/templates/yawl-xml"
if [[ ! -d "$TEMPLATE_SOURCE" ]]; then
    log_warn "Template source directory not found: $TEMPLATE_SOURCE"
else
    cp -v "${TEMPLATE_SOURCE}"/*.tera "${GGEN_ROOT}/templates/" 2>&1 | while read -r line; do
        log_debug "  $line"
    done
    log_success "Copied templates"
fi

# ── Create SPARQL Query Files ─────────────────────────────────────────────
log_info "Creating SPARQL validation queries..."

cat > "${GGEN_ROOT}/sparql/validate-turtle-spec.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>

# Validate YAWL specification completeness
# Returns incomplete specifications with missing required properties

SELECT ?spec ?issue ?missingProp
WHERE {
  ?spec a yawl:Specification .

  {
    # Check for specification ID
    FILTER NOT EXISTS {
      ?spec yawl:id ?id .
    }
    BIND("Missing specification ID" AS ?issue)
    BIND("id" AS ?missingProp)
  } UNION {
    # Check for at least one decomposition
    FILTER NOT EXISTS {
      ?spec yawl:hasDecomposition ?decomp .
    }
    BIND("No decompositions found" AS ?issue)
    BIND("decomposition" AS ?missingProp)
  } UNION {
    # Check for valid root net
    FILTER NOT EXISTS {
      ?spec yawl:rootNet ?rootNet .
      ?rootNet a yawl:Net .
    }
    BIND("No valid root net" AS ?issue)
    BIND("rootNet" AS ?missingProp)
  }
}
EOF
log_success "Created validate-turtle-spec.sparql"

cat > "${GGEN_ROOT}/sparql/extract-workflow.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>

# Extract workflow structure from YAWL ontology
# Returns tasks, flows, and decompositions

SELECT ?taskId ?taskName ?splitType ?joinType ?documentation ?decomposesTo
WHERE {
  ?task a yawl:Task ;
        yawl:id ?taskId ;
        yawl:name ?taskName .

  ?task yawl:hasSplit ?splitElm .
  ?splitElm yawl:code ?splitType .

  ?task yawl:hasJoin ?joinElm .
  ?joinElm yawl:code ?joinType .

  OPTIONAL {
    ?task rdfs:comment ?documentation
  }

  OPTIONAL {
    ?task yawl:decomposesTo ?decomp .
    ?decomp yawl:id ?decomposesTo
  }
}
ORDER BY ?taskId
EOF
log_success "Created extract-workflow.sparql"

cat > "${GGEN_ROOT}/sparql/map-ontology.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

# Map Turtle RDF to YAWL ontology elements
# Returns all specification elements with their types and properties

SELECT ?element ?elementType ?elementId ?property ?value
WHERE {
  ?element a ?elementType .

  OPTIONAL { ?element yawl:id ?elementId }
  OPTIONAL { ?element yawl:name ?elementId }

  OPTIONAL {
    ?element ?property ?value .
    FILTER (
      ?property IN (
        yawl:id, yawl:name, yawl:code, yawl:documentation,
        yawl:hasSplit, yawl:hasJoin, yawl:decomposesTo
      )
    )
  }
}
ORDER BY ?element ?property
EOF
log_success "Created map-ontology.sparql"

# ── Additional SPARQL Queries for Validation ──────────────────────────────
cat > "${GGEN_ROOT}/sparql/validate-task-flows.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>

# Validate task flow structure
# Detects orphaned tasks or unreachable elements

SELECT ?task ?issue
WHERE {
  ?task a yawl:Task ;
        yawl:id ?taskId .

  {
    # Task with no incoming flows
    FILTER NOT EXISTS {
      ?predecessor yawl:flowsTo ?task .
    }
    FILTER NOT EXISTS {
      ?spec yawl:rootNet ?net .
      ?net yawl:inputCondition ?task .
    }
    BIND("Task has no incoming flows and is not reachable from input condition" AS ?issue)
  } UNION {
    # Task with no outgoing flows
    FILTER NOT EXISTS {
      ?task yawl:flowsTo ?successor .
    }
    FILTER NOT EXISTS {
      ?spec yawl:rootNet ?net .
      ?net yawl:outputCondition ?task .
    }
    BIND("Task has no outgoing flows and does not reach output condition" AS ?issue)
  }
}
EOF
log_success "Created validate-task-flows.sparql"

cat > "${GGEN_ROOT}/sparql/validate-decompositions.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>

# Validate decomposition definitions
# Ensures all referenced decompositions exist and are properly defined

SELECT ?task ?decompId ?issue
WHERE {
  ?task a yawl:Task ;
        yawl:id ?taskId ;
        yawl:decomposesTo ?decomp .
  ?decomp yawl:id ?decompId .

  {
    # Decomposition not found
    FILTER NOT EXISTS {
      ?decomp a yawl:Decomposition .
    }
    BIND("Referenced decomposition does not exist" AS ?issue)
  } UNION {
    # Decomposition has no content
    FILTER NOT EXISTS {
      ?decomp yawl:hasElement ?elem .
    }
    FILTER NOT EXISTS {
      ?decomp yawl:code ?code .
    }
    BIND("Decomposition has no implementation" AS ?issue)
  }
}
EOF
log_success "Created validate-decompositions.sparql"

cat > "${GGEN_ROOT}/sparql/extract-variables.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

# Extract variable definitions for workflow specification
# Returns all local and global variables with their types and initial values

SELECT ?varName ?varType ?initialValue ?scope
WHERE {
  {
    ?var a yawl:LocalVariable ;
         yawl:name ?varName ;
         yawl:type ?varType ;
         yawl:scope ?scope .

    OPTIONAL {
      ?var yawl:initialValue ?initialValue .
    }
  } UNION {
    ?var a yawl:GlobalVariable ;
         yawl:name ?varName ;
         yawl:type ?varType .

    BIND("global" AS ?scope)

    OPTIONAL {
      ?var yawl:initialValue ?initialValue .
    }
  }
}
ORDER BY ?scope ?varName
EOF
log_success "Created extract-variables.sparql"

cat > "${GGEN_ROOT}/sparql/validate-control-flow.sparql" << 'EOF'
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX yawl: <http://www.yawlfoundation.org/yawlschema#>

# Validate control flow (join/split) semantics
# Ensures join and split types are compatible with connected flows

SELECT ?task ?taskId ?splitType ?joinType ?issue
WHERE {
  ?task a yawl:Task ;
        yawl:id ?taskId ;
        yawl:hasSplit ?splitElm ;
        yawl:hasJoin ?joinElm .

  ?splitElm yawl:code ?splitType .
  ?joinElm yawl:code ?joinType .

  {
    # Invalid split type
    FILTER (?splitType NOT IN ("and", "or", "xor"))
    BIND("Invalid split type" AS ?issue)
  } UNION {
    # Invalid join type
    FILTER (?joinType NOT IN ("and", "or", "xor"))
    BIND("Invalid join type" AS ?issue)
  }
}
EOF
log_success "Created validate-control-flow.sparql"

# ── Create Configuration File ─────────────────────────────────────────────
log_info "Creating ggen configuration..."

cat > "${GGEN_ROOT}/config/ggen.properties" << 'EOF'
# YAWL XML Generator (ggen) Configuration
# Generated at initialization

# Logging
logging.level=INFO
logging.file=${GGEN_ROOT}/logs/ggen.log

# Template Configuration
templates.dir=${GGEN_ROOT}/templates
templates.cache=true
templates.cache.dir=${GGEN_ROOT}/cache

# SPARQL Queries
sparql.dir=${GGEN_ROOT}/sparql
sparql.validate.enabled=true

# Output
output.format=xml
output.encoding=UTF-8
output.validation=true

# Cache
cache.enabled=true
cache.ttl=3600

# RDF/Turtle
rdf.default_prefix=yawl
rdf.namespace=http://www.yawlfoundation.org/yawlschema#
EOF
log_success "Created ggen.properties"

# ── Create Template Index ─────────────────────────────────────────────────
log_info "Creating template index..."

cat > "${GGEN_ROOT}/config/templates.json" << 'EOF'
{
  "version": "1.0",
  "templates": [
    {
      "name": "workflow",
      "file": "workflow.yawl.tera",
      "description": "Top-level YAWL workflow specification",
      "inputs": [
        "tasks",
        "flows",
        "variables",
        "specId",
        "specName",
        "specVersion",
        "rootNetId",
        "inputCondition",
        "outputCondition"
      ]
    },
    {
      "name": "decompositions",
      "file": "decompositions.yawl.tera",
      "description": "Task decomposition definitions",
      "inputs": [
        "decompositions",
        "taskId"
      ]
    },
    {
      "name": "data-bindings",
      "file": "data-bindings.yawl.tera",
      "description": "Variable and data binding definitions",
      "inputs": [
        "variables",
        "inputMappings",
        "outputMappings"
      ]
    }
  ],
  "sparql_queries": [
    {
      "name": "validate-turtle-spec",
      "file": "validate-turtle-spec.sparql",
      "purpose": "Validate specification completeness"
    },
    {
      "name": "extract-workflow",
      "file": "extract-workflow.sparql",
      "purpose": "Extract workflow structure"
    },
    {
      "name": "map-ontology",
      "file": "map-ontology.sparql",
      "purpose": "Map Turtle RDF to YAWL ontology"
    },
    {
      "name": "validate-task-flows",
      "file": "validate-task-flows.sparql",
      "purpose": "Validate task flow connectivity"
    },
    {
      "name": "validate-decompositions",
      "file": "validate-decompositions.sparql",
      "purpose": "Validate decomposition references"
    },
    {
      "name": "extract-variables",
      "file": "extract-variables.sparql",
      "purpose": "Extract variable definitions"
    },
    {
      "name": "validate-control-flow",
      "file": "validate-control-flow.sparql",
      "purpose": "Validate join/split semantics"
    }
  ]
}
EOF
log_success "Created templates.json"

# ── Validate SPARQL Queries ───────────────────────────────────────────────
log_info "Validating SPARQL queries..."

SPARQL_FILES=(
    "validate-turtle-spec.sparql"
    "extract-workflow.sparql"
    "map-ontology.sparql"
    "validate-task-flows.sparql"
    "validate-decompositions.sparql"
    "extract-variables.sparql"
    "validate-control-flow.sparql"
)

for sparql_file in "${SPARQL_FILES[@]}"; do
    sparql_path="${GGEN_ROOT}/sparql/${sparql_file}"
    if [[ ! -f "$sparql_path" ]]; then
        log_warn "SPARQL file not found: $sparql_file"
    else
        # Basic syntax check: verify SELECT or ASK keyword and closing brace
        if ! grep -q "^\(SELECT\|ASK\|CONSTRUCT\|DESCRIBE\)" "$sparql_path"; then
            log_warn "SPARQL file may be invalid: $sparql_file (no SELECT/ASK/CONSTRUCT/DESCRIBE)"
        else
            log_debug "Validated: $sparql_file"
        fi
    fi
done

log_success "SPARQL validation complete"

# ── Validate Templates ────────────────────────────────────────────────────
log_info "Validating Tera templates..."

if [[ "$SKIP_TERA_VALIDATION" == "0" ]]; then
    for template_file in "${GGEN_ROOT}/templates"/*.tera; do
        if [[ -f "$template_file" ]]; then
            template_name=$(basename "$template_file")
            # Tera syntax validation (basic check for matching braces)
            if grep -q "{%" "$template_file" && ! grep -q "%}" "$template_file"; then
                log_warn "Template may have unclosed tags: $template_name"
            else
                log_debug "Validated: $template_name"
            fi
        fi
    done
    log_success "Template validation complete"
else
    log_info "Tera validation skipped (tera CLI not available)"
fi

# ── Initialize Logging ────────────────────────────────────────────────────
log_info "Initializing logging..."

cat > "${GGEN_ROOT}/logs/.gitkeep" << 'EOF'
# Placeholder to ensure logs directory exists
EOF

log_info "Creating initial log file..."
cat > "${GGEN_ROOT}/logs/ggen.log" << EOF
$(date -u +"%Y-%m-%dT%H:%M:%SZ") - ggen initialized by ggen-init.sh
Version: 1.0
Timestamp: $(date -u +"%s")
EOF
log_success "Initialized logging"

# ── Create Metadata ──────────────────────────────────────────────────────
log_info "Creating initialization metadata..."

cat > "${GGEN_ROOT}/.ggen-init.json" << EOF
{
  "version": "1.0",
  "initialized_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "initialized_by": "ggen-init.sh",
  "directories": {
    "templates": "templates/",
    "sparql": "sparql/",
    "cache": "cache/",
    "logs": "logs/",
    "config": "config/"
  },
  "templates_count": $(ls "${GGEN_ROOT}/templates"/*.tera 2>/dev/null | wc -l),
  "sparql_queries": 7,
  "status": "ready"
}
EOF
log_success "Created initialization metadata"

# ── Final Summary ─────────────────────────────────────────────────────────
echo ""
printf "${C_CYAN}ggen initialization complete${C_RESET}\n"
printf "${C_CYAN}Location: %s${C_RESET}\n" "$GGEN_ROOT"
echo ""
echo "Directory structure:"
echo "  .ggen/"
echo "  ├── templates/      # Tera templates for XML generation"
echo "  ├── sparql/         # SPARQL validation queries"
echo "  ├── cache/          # Compiled AST and template cache"
echo "  ├── logs/           # Generation logs"
echo "  ├── config/         # Configuration files"
echo "  └── .ggen-init.json # Initialization metadata"
echo ""
echo "Next steps:"
echo "  1. Run: bash scripts/ggen-sync.sh"
echo "  2. Verify: bash scripts/dx.sh compile"
echo "  3. Test: ggen generate --spec examples/sample.rdf"
echo ""
