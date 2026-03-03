#!/usr/bin/env bash
#
# Generate test RDF datasets for SPARQL benchmarks
#

set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
OUTPUT_DIR="datasets"
DATASET_SIZE="medium"

# Error handling function
die() {
    echo -e "${RED}Error: $1${NC}" >&2
    exit 1
}

# Show help function
show_help() {
    cat << EOF
Usage: $0 [--help] [--output-dir DIR] [--size SIZE]

Generate test RDF datasets for SPARQL benchmarks.

Options:
  --help         Show this help message and exit
  --output-dir DIR  Directory to save datasets (default: datasets)
  --size SIZE    Size of datasets: small|medium|large (default: medium)

Environment variables:
  OUTPUT_DIR     Directory to save datasets (overrides --output-dir)
  DATASET_SIZE  Size of datasets (overrides --size)

Examples:
  $0                          # Generate datasets in datasets/ directory
  $0 --output-dir my-data    # Generate datasets in my-data/ directory
  $0 --size large             # Generate large datasets
  $0 --output-dir my-data --size small
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --help)
            show_help
            exit 0
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --size)
            DATASET_SIZE="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}Error: Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# Override with environment variables if set
OUTPUT_DIR="${OUTPUT_DIR:-datasets}"
DATASET_SIZE="${DATASET_SIZE:-medium}"

# Validate dataset size
case "$DATASET_SIZE" in
    small|medium|large)
        ;;
    *)
        die "Invalid dataset size: $DATASET_SIZE. Must be small, medium, or large"
        ;;
esac

echo -e "${YELLOW}Generating test datasets for SPARQL benchmarks...${NC}"
echo -e "${YELLOW}Output directory: ${NC}$OUTPUT_DIR"
echo -e "${YELLOW}Dataset size: ${NC}$DATASET_SIZE"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build the benchmark module
echo -e "${YELLOW}Building benchmark module...${NC"
mvn -pl yawl-benchmark clean install -q

# Check if build succeeded
if [[ $? -ne 0 ]]; then
    die "Failed to build benchmark module"
fi

# Run the dataset generator
echo -e "${YELLOW}Running RdfDataGenerator...${NC}"
java -cp yawl-benchmark/target/classes:yawl-benchmark/target/dependency/* \
     org.yawlfoundation.yawl.benchmark.sparql.RdfDataGenerator "$DATASET_SIZE" "$OUTPUT_DIR"

echo -e "${GREEN}Done! Generated datasets in $OUTPUT_DIR/ directory:${NC}"
ls -lh "$OUTPUT_DIR"

echo -e "\n${YELLOW}Next step: Load datasets into your SPARQL engine:${NC}"
echo "1. For QLever HTTP: Import $OUTPUT_DIR/*.ttl"
echo "2. For Oxigraph: Use loadTurtle() method"
echo "3. Run benchmarks: ./scripts/benchmark-qlever.sh"
