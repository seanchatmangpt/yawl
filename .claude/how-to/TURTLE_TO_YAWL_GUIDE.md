# Turtle to YAWL Conversion Pipeline — User Guide

**Document**: `.claude/TURTLE_TO_YAWL_GUIDE.md`
**Date**: 2026-02-21
**Phase**: Phase 3 Deliverable (XML Generator Team)

---

## Overview

The `turtle-to-yawl.sh` script is the main user-facing entry point for converting **Turtle RDF workflow specifications** into **YAWL XML** format.

This is a complete orchestration pipeline that:

1. **Validates** input Turtle RDF specification for semantic correctness
2. **Stages** the specification in the ontology directory
3. **Generates** YAWL XML using ggen (Graph Generation) and Tera templates
4. **Validates** the output YAWL XML against XSD schema

---

## Quick Start

### Basic Usage

```bash
# Convert a Turtle spec to YAWL
bash scripts/turtle-to-yawl.sh my-workflow.ttl

# With verbose output
bash scripts/turtle-to-yawl.sh my-workflow.ttl --verbose

# Custom output file
bash scripts/turtle-to-yawl.sh spec.ttl --output /tmp/my-workflow.yawl
```

### Expected Output

On success:

```
=====================================
YAWL Turtle-to-YAWL Conversion Pipeline
=====================================

[INFO] Start time: Sat Feb 21 20:17:18 UTC 2026

[INFO] STEP 0: Verifying Prerequisites
[✓] All required scripts found
[✓] Input file accessible: my-workflow.ttl

[INFO] STEP 1: Validate Turtle Specification
[✓] Turtle specification validation passed

[INFO] STEP 2: Copy Specification to Ontology Directory
[✓] Specification copied to: ontology/process.ttl

[INFO] STEP 3: Run ggen Synchronization
[✓] YAWL generation completed

[INFO] STEP 4: Validate YAWL Output
[✓] YAWL output validation passed

=====================================
✓ Pipeline Complete
=====================================

[✓] Turtle → YAWL conversion successful

Summary:
  Input:  my-workflow.ttl
  Output: output/process.yawl

Output file size: 4523 bytes

[INFO] Next steps:
  • Review the generated YAWL at: output/process.yawl
  • Deploy using: bash scripts/deploy.sh output/process.yawl
  • Import into YAWL Editor for further refinement

[INFO] End time: Sat Feb 21 20:17:25 UTC 2026
```

---

## Command-Line Options

### Arguments

```
<spec.ttl>    Path to input Turtle RDF specification file (required)
```

### Flags

| Flag | Purpose | Example |
|------|---------|---------|
| `--verbose` | Enable verbose debug output | `--verbose` |
| `--output <file>` | Override output YAWL path | `--output /tmp/out.yawl` |
| `--help` | Show help message | `--help` |
| `-h` | Show help message | `-h` |

### Environment Variables

| Variable | Effect | Example |
|----------|--------|---------|
| `VERBOSE` | Enable verbose output (1/0) | `VERBOSE=1` |
| `YAWL_OUTPUT` | Override output YAWL file | `YAWL_OUTPUT=/tmp/out.yawl` |
| `GGEN_TEMPLATE` | Override ggen Tera template | `GGEN_TEMPLATE=custom.yawl.tera` |
| `GGEN_VERBOSE` | Enable ggen verbose output | `GGEN_VERBOSE=1` |

---

## Pipeline Stages

### Stage 0: Verify Prerequisites

Checks that all required scripts exist:
- `validate-turtle-spec.sh`
- `ggen-sync.sh`
- `validate-yawl-output.sh`

Also validates that the input file exists and is readable.

**Exit codes**:
- `0` = All prerequisites met
- `2` = Missing file or script

### Stage 1: Validate Turtle Specification

Runs semantic validation on the input Turtle RDF file using SPARQL queries and grep-based checks.

**Checks performed**:
- Turtle syntax correctness
- Namespace declarations (RDF, YAWL, etc.)
- Task ID uniqueness and non-emptiness
- Task names are non-empty
- Split/join balance in control flow
- No orphaned tasks
- Valid split/join types (AND, XOR, OR)

**Exit codes**:
- `0` = Specification is valid
- `2` = Validation failed (errors reported)

**Example output**:
```
[INFO] Validating Turtle syntax...
[INFO] Validating namespace declarations...
[INFO] Validating task IDs...
[INFO] Validating task names...
[INFO] Validating split/join balance...
[✓] Turtle specification validation passed
```

### Stage 2: Copy Specification to Ontology Directory

Copies the input Turtle spec to `ontology/process.ttl` for processing by ggen.

**Actions**:
- Creates `ontology/` directory if needed
- Copies input file to `ontology/process.ttl`
- Verifies copy was successful

**Exit codes**:
- `0` = Copy succeeded
- `2` = Copy failed or verification failed

### Stage 3: Run ggen Synchronization

Generates YAWL XML from the Turtle RDF specification using the ggen graph generation system and Tera template engine.

**Process**:
1. Reads `ontology/process.ttl` (RDF specification)
2. Executes SPARQL queries to extract workflow structure
3. Merges results into `templates/workflow.yawl.tera` Tera template
4. Renders final YAWL XML to `output/process.yawl`

**Exit codes**:
- `0` = Generation succeeded
- `1` = Transient error (network issue, retry may help)
- `2` = Fatal error (bad input, unsupported format)

**Environment passed to ggen**:
- `GGEN_VERBOSE` (from caller or default 0)

### Stage 4: Validate YAWL Output

Validates the generated YAWL XML file for structural correctness.

**Checks performed**:
- Well-formed XML structure
- Required YAWL elements present
- XSD schema compliance (if XSD available)
- No stray text nodes outside elements
- YAWL specification structure

**Exit codes**:
- `0` = YAWL output is valid
- `2` = Validation failed (errors reported)

**Example output**:
```
[INFO] Validating generated YAWL file...
[DEBUG] File: output/process.yawl
[✓] YAWL output validation passed
```

---

## Exit Codes

| Code | Meaning | Action |
|------|---------|--------|
| `0` | **Success** | YAWL generated and validated. Check `output/process.yawl` |
| `1` | **Transient error** | Network issue or temporary problem. Retry may help. |
| `2` | **Fatal error** | Bad input, validation failure, missing file. Check logs and fix inputs. |

---

## Input File Requirements

### Turtle RDF Format

Your input file must be valid Turtle RDF with proper YAWL semantics:

```turtle
@prefix : <http://example.org/workflow/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix yawls: <http://yawl.org/yawl-specification/> .

:MyWorkflow a yawls:Specification ;
    yawls:specificationID "MyWorkflow"^^xsd:string ;
    yawls:hasNet :MyNet .

:MyNet a yawls:Net ;
    yawls:hasElement :Task1 ;
    yawls:hasElement :Task2 ;
    yawls:hasFlow :Flow1 .

:Task1 a yawls:Task ;
    yawls:taskID "Task1"^^xsd:string ;
    yawls:taskName "Process Item"^^xsd:string .

:Task2 a yawls:Task ;
    yawls:taskID "Task2"^^xsd:string ;
    yawls:taskName "Review"^^xsd:string .

:Flow1 a yawls:Flow ;
    yawls:fromElement :Task1 ;
    yawls:toElement :Task2 .
```

### Validation Requirements

The script validates:

1. **Syntax**: Valid Turtle RDF syntax
2. **Namespaces**: Proper RDF, YAWL namespace declarations
3. **Structure**: Tasks, conditions, flows properly defined
4. **Control Flow**: Splits/joins balanced, flows connected
5. **Semantics**: Task IDs unique, names non-empty, types valid

See `validate-turtle-spec.sh` for detailed validation rules.

---

## Output Files

### Primary Output: `output/process.yawl`

The generated YAWL XML specification. Ready for:
- Import into YAWL Editor
- Deployment via `scripts/deploy.sh`
- Further manual refinement

Example structure:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema"
               specificationID="MyWorkflow"
               version="1.0">
  <net id="MyNet">
    <localVariable>
      <!-- Variables -->
    </localVariable>
    <task id="Task1">
      <!-- Task definition -->
    </task>
    <!-- More tasks and flows -->
  </net>
</specification>
```

### Intermediate File: `ontology/process.ttl`

Copy of input Turtle spec, kept for reference and traceability.

---

## Examples

### Example 1: Simple Workflow

```bash
$ bash scripts/turtle-to-yawl.sh examples/simple.ttl
```

Input: `examples/simple.ttl` (3 tasks, linear flow)
Output: `output/process.yawl` (YAWL-formatted specification)

### Example 2: Complex Workflow with Splits

```bash
$ bash scripts/turtle-to-yawl.sh specs/order-fulfillment.ttl --verbose
```

Input: `specs/order-fulfillment.ttl` (10+ tasks with AND/XOR splits)
Output: `output/process.yawl` (validated YAWL specification)
Behavior: Verbose output showing all validation steps

### Example 3: Custom Output Path

```bash
$ bash scripts/turtle-to-yawl.sh workflow.ttl --output /tmp/my-process.yawl
```

Input: `workflow.ttl`
Output: `/tmp/my-process.yawl` (custom location)

### Example 4: With Environment Variables

```bash
$ VERBOSE=1 YAWL_OUTPUT=/srv/yawl/specs/prod.yawl bash scripts/turtle-to-yawl.sh input.ttl
```

Sets verbose output and custom output path via environment.

---

## Troubleshooting

### Error: "Input Turtle specification not found"

**Cause**: File path is incorrect or file doesn't exist.

**Solution**:
```bash
# Check file exists
ls -la my-workflow.ttl

# Use absolute path if needed
bash scripts/turtle-to-yawl.sh /full/path/to/spec.ttl
```

### Error: "Turtle validation failed"

**Cause**: Input Turtle RDF has syntax or semantic errors.

**Solution**:
1. Run validation step separately to see specific errors:
   ```bash
   bash scripts/validate-turtle-spec.sh my-workflow.ttl
   ```
2. Check Turtle syntax (proper namespace declarations, etc.)
3. Ensure YAWL semantic requirements are met (unique task IDs, etc.)

### Error: "ggen synchronization failed"

**Cause**: Template or ggen configuration issue.

**Solution**:
1. Run with verbose flag:
   ```bash
   bash scripts/turtle-to-yawl.sh spec.ttl --verbose
   ```
2. Check ggen logs
3. Verify `templates/workflow.yawl.tera` exists and is valid

### Error: "YAWL validation failed"

**Cause**: Generated YAWL XML is malformed or incomplete.

**Solution**:
1. Check the generated file:
   ```bash
   cat output/process.yawl | head -30
   ```
2. Run YAWL validation separately:
   ```bash
   bash scripts/validate-yawl-output.sh output/process.yawl
   ```
3. Look for XML structure issues (unclosed tags, invalid elements)

### Exit Code 1 (Transient Error)

**Cause**: Temporary issue (network, resource unavailable).

**Solution**: Retry the command:
```bash
bash scripts/turtle-to-yawl.sh spec.ttl
```

### Verbose Output Disabled

**To enable verbose output**:

Option 1: Use `--verbose` flag
```bash
bash scripts/turtle-to-yawl.sh spec.ttl --verbose
```

Option 2: Set environment variable
```bash
VERBOSE=1 bash scripts/turtle-to-yawl.sh spec.ttl
```

---

## Integration with Other Scripts

### Deploying Generated YAWL

```bash
# Generate YAWL
bash scripts/turtle-to-yawl.sh spec.ttl

# Deploy to YAWL server
bash scripts/deploy.sh output/process.yawl
```

### Testing Generated YAWL

```bash
# Generate YAWL
bash scripts/turtle-to-yawl.sh spec.ttl

# Run tests
bash scripts/run-integration-tests.sh output/process.yawl
```

### Batch Processing Multiple Specs

```bash
#!/bin/bash
# Convert all Turtle specs to YAWL

for spec in specs/*.ttl; do
    echo "Processing: $spec"
    bash scripts/turtle-to-yawl.sh "$spec" || {
        echo "Failed to convert: $spec"
        exit 2
    }
done

echo "All workflows converted successfully"
```

---

## Implementation Details

### Script Architecture

```
turtle-to-yawl.sh (Main Orchestrator)
├── parse_args()           — Parse command-line arguments
├── verify_prerequisites() — Check required scripts exist
├── step_validate_turtle()     — Call validate-turtle-spec.sh
├── step_copy_to_ontology()    — Copy to ontology/process.ttl
├── step_ggen_sync()           — Call ggen-sync.sh
├── step_validate_yawl()       — Call validate-yawl-output.sh
└── print_summary()        — Display results
```

### Called Scripts

| Script | Purpose | Invoked By |
|--------|---------|-----------|
| `validate-turtle-spec.sh` | Semantic validation of input Turtle | Stage 1 |
| `ggen-sync.sh` | YAWL XML generation via ggen | Stage 3 |
| `validate-yawl-output.sh` | XSD validation of output YAWL | Stage 4 |

### Error Handling

- **set -euo pipefail**: Exit on first error, strict variable checking
- **trap cleanup EXIT**: Auto-cleanup on exit
- **Exit codes**: Propagated from child scripts
- **Error messages**: Prefixed with `[ERROR]` and sent to stderr

---

## Configuration Files

### Required Directories

| Directory | Purpose | Created By |
|-----------|---------|-----------|
| `ontology/` | Staging area for Turtle specs | Script (auto-created) |
| `output/` | Output YAWL files | Script (auto-created) |
| `templates/` | Tera templates for code generation | Manual setup |

### Configuration Files

| File | Purpose |
|------|---------|
| `templates/workflow.yawl.tera` | Tera template for YAWL XML generation |
| `.specify/yawl-ontology.ttl` | YAWL RDF ontology definition |
| `.specify/yawl-shapes.ttl` | SHACL shapes for validation |

---

## Performance Characteristics

| Aspect | Typical Value | Notes |
|--------|---------------|-------|
| **Time to validate** | < 1 second | SPARQL/grep-based |
| **Time to generate YAWL** | 1-5 seconds | Depends on workflow size |
| **Time to validate output** | < 1 second | XSD validation |
| **Total pipeline time** | 2-10 seconds | For typical workflows |
| **Output file size** | 5-50 KB | For simple workflows |

---

## Related Documentation

- **YAWL Specification**: `.claude/rules/schema/xsd-validation.md`
- **Pipeline Components**: Individual script headers
- **RDF/Turtle Guide**: [W3C Turtle Specification](https://www.w3.org/TR/turtle/)
- **YAWL Foundation**: [YAWL Editor](http://www.yawlfoundation.org/)

---

## FAQ

### Q: Can I edit the Turtle spec after generation?

**A**: Yes! Modify the input `.ttl` file and re-run the pipeline. The script overwrites previous output.

### Q: What if ggen is not installed?

**A**: The `ggen-sync.sh` script checks for ggen. If missing, it will error with exit code 2. Install ggen before running.

### Q: Can I use custom Tera templates?

**A**: Yes! Set the environment variable:
```bash
GGEN_TEMPLATE=/path/to/custom.yawl.tera bash scripts/turtle-to-yawl.sh spec.ttl
```

### Q: Where are logs stored?

**A**: Logs are printed to stdout/stderr in real-time. To save to file:
```bash
bash scripts/turtle-to-yawl.sh spec.ttl > pipeline.log 2>&1
```

### Q: What's the difference between `--verbose` and `VERBOSE=1`?

**A**: They're equivalent. Both enable debug output. Use whichever is more convenient.

### Q: Can I run multiple conversions in parallel?

**A**: Yes, but use different output paths to avoid conflicts:
```bash
bash scripts/turtle-to-yawl.sh spec1.ttl --output /tmp/out1.yawl &
bash scripts/turtle-to-yawl.sh spec2.ttl --output /tmp/out2.yawl &
wait
```

---

## Support & Issues

For issues or feature requests related to the conversion pipeline:

1. Check the troubleshooting section above
2. Run with `--verbose` to get detailed diagnostics
3. Check individual script logs
4. Review input Turtle spec for semantic correctness

---

**Last Updated**: 2026-02-21
**Status**: Production Ready
**Maintainer**: YAWL XML Generator Team
