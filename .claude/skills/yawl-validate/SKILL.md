---
name: yawl-validate
description: Validate YAWL XML specifications against XSD schema
disable-model-invocation: true
user-invocable: true
allowed-tools: Bash(xmllint *)
---

# YAWL Validate Skill

Validate YAWL specification XML files against the YAWL Schema 4.0 XSD.

## Usage

```
/yawl-validate [spec-file.xml]
```

If no file specified, validates all *.xml files in current directory.

## What It Does

1. Locates YAWL XSD schema (schema/YAWL_Schema4.0.xsd)
2. Runs xmllint validation against XML specification
3. Reports schema violations with line numbers
4. Verifies well-formedness and schema compliance

## Execution

```bash
cd "$CLAUDE_PROJECT_DIR"
SPEC_FILE="${ARGUMENTS:-*.xml}"
xmllint --schema schema/YAWL_Schema4.0.xsd "$SPEC_FILE"
```

## Success Criteria

- XML is well-formed
- XML validates against YAWL Schema 4.0
- No schema violations
- Exit code 0

## Common Issues

- Missing required elements (inputCondition, outputCondition)
- Invalid task decomposition references
- Malformed data definitions
- Invalid flow connections
