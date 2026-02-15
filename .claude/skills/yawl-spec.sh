#!/bin/bash
# YAWL Spec Skill - Claude Code 2026 Best Practices
# Usage: /yawl-spec --template=TYPE [--name=NAME]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="${PROJECT_ROOT}/exampleSpecs"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Spec Skill - Create YAWL workflow specifications

Usage: /yawl-spec [options]

Options:
  --template=TYPE    Workflow template type (required for creation)
  --name=NAME        Specification name
  --validate=FILE    Validate existing specification
  -h, --help         Show this help message

Templates:
  sequence      Sequential task execution (WCP1)
  parallel      Parallel split/sync (WCP2/WCP3)
  choice        Exclusive choice (WCP4)
  loop          Structured loop (WCP21)
  milestone     Milestone pattern (WCP18)
  discriminator Discriminator pattern (WCP9)

Examples:
  /yawl-spec --template=sequence --name=HelloWorld
  /yawl-spec --template=parallel --name=ParallelProcess
  /yawl-spec --validate existing.xml
EOF
}

# Parse arguments
TEMPLATE=""
NAME=""
VALIDATE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --template=*)
            TEMPLATE="${1#*=}"
            shift
            ;;
        --name=*)
            NAME="${1#*=}"
            shift
            ;;
        --validate=*)
            VALIDATE="${1#*=}"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Validate mode
if [[ -n "${VALIDATE}" ]]; then
    echo -e "${BLUE}[yawl-spec] Validating: ${VALIDATE}${NC}"
    "${SCRIPT_DIR}/yawl-validate.sh" "${VALIDATE}"
    exit $?
fi

# Creation mode
if [[ -z "${TEMPLATE}" ]]; then
    echo -e "${RED}[yawl-spec] Template required for creation${NC}"
    print_usage
    exit 1
fi

if [[ -z "${NAME}" ]]; then
    NAME="${TEMPLATE}-workflow"
fi

mkdir -p "${OUTPUT_DIR}"
OUTPUT_FILE="${OUTPUT_DIR}/${NAME}.ywl"

echo -e "${BLUE}[yawl-spec] Creating specification: ${NAME}${NC}"
echo -e "${BLUE}[yawl-spec] Template: ${TEMPLATE}${NC}"
echo -e "${BLUE}[yawl-spec] Output: ${OUTPUT_FILE}${NC}"

# Generate specification based on template
case "${TEMPLATE}" in
    sequence)
        cat > "${OUTPUT_FILE}" << 'SPEC'
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema YAWL_Schema4.0.xsd">
  <specification uri="SequenceWorkflow">
    <metaData>
      <title>Sequence Workflow</title>
      <creator>YAWL Spec Skill</creator>
      <description>Sequential task execution pattern (WCP1)</description>
    </metaData>
    <rootNet ref="SequenceNet"/>
    <decomposition id="SequenceNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start" name="Start">
          <flowsInto>
            <nextElementRef id="TaskA"/>
          </flowsInto>
        </inputCondition>
        <task id="TaskA" name="Task A">
          <flowsInto>
            <nextElementRef id="TaskB"/>
          </flowsInto>
        </task>
        <task id="TaskB" name="Task B">
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
        </task>
        <outputCondition id="end" name="End"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
SPEC
        ;;
    parallel)
        cat > "${OUTPUT_FILE}" << 'SPEC'
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema YAWL_Schema4.0.xsd">
  <specification uri="ParallelWorkflow">
    <metaData>
      <title>Parallel Workflow</title>
      <creator>YAWL Spec Skill</creator>
      <description>Parallel split and synchronization pattern (WCP2/WCP3)</description>
    </metaData>
    <rootNet ref="ParallelNet"/>
    <decomposition id="ParallelNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start" name="Start">
          <flowsInto>
            <nextElementRef id="TaskA"/>
          </flowsInto>
          <flowsInto>
            <nextElementRef id="TaskB"/>
          </flowsInto>
        </inputCondition>
        <task id="TaskA" name="Branch A">
          <flowsInto>
            <nextElementRef id="sync"/>
          </flowsInto>
        </task>
        <task id="TaskB" name="Branch B">
          <flowsInto>
            <nextElementRef id="sync"/>
          </flowsInto>
        </task>
        <condition id="sync" name="Synchronization">
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
        </condition>
        <outputCondition id="end" name="End"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
SPEC
        ;;
    choice)
        cat > "${OUTPUT_FILE}" << 'SPEC'
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema YAWL_Schema4.0.xsd">
  <specification uri="ChoiceWorkflow">
    <metaData>
      <title>Choice Workflow</title>
      <creator>YAWL Spec Skill</creator>
      <description>Exclusive choice pattern (WCP4)</description>
    </metaData>
    <rootNet ref="ChoiceNet"/>
    <decomposition id="ChoiceNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start" name="Start">
          <flowsInto>
            <nextElementRef id="decision"/>
          </flowsInto>
        </inputCondition>
        <condition id="decision" name="Decision">
          <flowsInto>
            <nextElementRef id="TaskA"/>
            <predicate>true</predicate>
          </flowsInto>
          <flowsInto>
            <nextElementRef id="TaskB"/>
            <predicate>false</predicate>
          </flowsInto>
        </condition>
        <task id="TaskA" name="Option A">
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
        </task>
        <task id="TaskB" name="Option B">
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
        </task>
        <outputCondition id="end" name="End"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
SPEC
        ;;
    *)
        echo -e "${RED}[yawl-spec] Unknown template: ${TEMPLATE}${NC}"
        echo "Available templates: sequence, parallel, choice, loop, milestone, discriminator"
        exit 1
        ;;
esac

echo -e "${GREEN}[yawl-spec] Specification created: ${OUTPUT_FILE}${NC}"
