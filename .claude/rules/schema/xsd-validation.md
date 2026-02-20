---
paths:
  - "schema/**"
  - "exampleSpecs/**"
  - "**/*.xsd"
  - "**/*.ywl"
  - "**/*.yawl"
---

# Schema & Specification Rules

## Current Schema
- **YAWL_Schema4.0.xsd** is the active version — all new specs must validate against it
- Extensions in `schema/extensions/`: AgentBinding, Coordination, Integration, Standalone, Validation
- Validate: `xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml`

## Schema Versioning
- v1.0–v3.0: Legacy, read-only (do not modify)
- v4.0: Current production schema
- New elements go in extension schemas under `schema/extensions/`
- Never break backward compatibility with existing v4.0 specs

## Specification Conventions
- All specs must be well-formed XML
- Required root element: `<specificationSet>` with namespace `http://www.yawlfoundation.org/yawlschema`
- Every `<specification>` needs a unique URI attribute
- Task decompositions reference net-level decompositions by ID

## Example Specs
- `exampleSpecs/` contains reference workflow specifications
- Use these as templates when creating new test specifications
- Do not modify existing example specs without updating their tests
