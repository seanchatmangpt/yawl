# YAWL Example Specifications

This directory contains example YAWL workflow specifications demonstrating various workflow patterns and YAWL features.

## YAWL 4.0 Compliant Specifications

The following specifications are fully compliant with YAWL Schema 4.0 and can be validated against `schema/YAWL_Schema4.0.xsd`.

### SimplePurchaseOrder.xml
- **Pattern**: Sequential workflow with basic task flow
- **Use Case**: Simple purchase order creation and approval
- **Elements**: 
  - InputCondition (start)
  - Task: Create Purchase Order (XOR join, AND split)
  - Task: Approve Purchase Order (XOR join, AND split)
  - OutputCondition (end)
- **Features Demonstrated**:
  - Basic task sequencing
  - XOR joins and AND splits
  - Simple linear workflow

**Validation**: `xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/SimplePurchaseOrder.xml`

### DocumentProcessing.xml
- **Pattern**: Conditional routing with decision points
- **Use Case**: Document review and approval workflow with rejection path
- **Elements**:
  - InputCondition (start)
  - Task: Receive Document (AND split)
  - Task: Review Document (XOR split with two outputs)
  - Task: Approve Document (one path)
  - Task: Reject Document (alternative path)
  - Condition elements for routing convergence
  - OutputCondition (end)
- **Features Demonstrated**:
  - XOR conditional split (if/else routing)
  - Conditional predicates for routing decisions
  - Multiple flow convergence
  - Condition elements for process flow merging

**Validation**: `xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/DocumentProcessing.xml`

### ParallelProcessing.xml
- **Pattern**: Parallel execution with synchronization
- **Use Case**: Parallel task execution that must be synchronized
- **Elements**:
  - InputCondition (start)
  - Task: Initialize Process (AND split)
  - Task: Parallel Task 1 (part of AND split)
  - Task: Parallel Task 2 (part of AND split)
  - Task: Parallel Task 3 (part of AND split)
  - Condition: Synchronize (AND-join point)
  - Task: Complete Process (AND join for synchronization)
  - OutputCondition (end)
- **Features Demonstrated**:
  - AND split for parallel task creation
  - AND join for synchronization
  - Multiple parallel paths
  - Synchronization patterns for parallel completion

**Validation**: `xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/ParallelProcessing.xml`

## Legacy Specifications (Beta 2-7)

The following specifications are historical examples from YAWL Beta 2-7. While they have been updated with the correct YAWL Foundation namespace and version attributes, they use a different XML structure (rootNet) that is incompatible with YAWL 4.0 schema validation.

### Location: exampleSpecs/xml/Beta2-7/

#### BarnesAndNoble.xml & BarnesAndNoble(Beta4).xml
- **Purpose**: Web service invocation example
- **Features**: Service invocation to retrieve book prices

#### MakeMusic.xml, MakeRecordings(Beta3).xml, MakeRecordings(Beta4).xml
- **Purpose**: Music recording workflow demonstration
- **Features**: Multi-task recording process with resource management

#### ResourceExample.xml
- **Purpose**: Resource allocation demonstration
- **Features**: Resource management and task assignment

#### SMSInvoker.xml
- **Purpose**: SMS service integration example
- **Features**: External service invocation

#### StockQuote.xml
- **Purpose**: Stock quote retrieval workflow
- **Features**: Service invocation and data handling

#### Timer.xml
- **Purpose**: Timer and delay demonstration
- **Features**: Time-based workflow control

#### makeTrip1.xml, makeTrip2.xml, makeTrip3.xml
- **Purpose**: Travel planning workflow (multiple versions)
- **Features**: Complex workflow with multiple decision points

### Namespace Updates Applied

All legacy specifications have been updated:
- **Old namespace**: `http://www.citi.qut.edu.au/yawl` (deprecated)
- **New namespace**: `http://www.yawlfoundation.org/yawlschema` (YAWL Foundation)
- **Version attribute**: Set to `version="4.0"`
- **Schema location**: Updated to point to `YAWL_Schema4.0.xsd`

### Note on Legacy Format

These specifications use the Beta-era XML structure with `rootNet` elements directly under `specification`. The YAWL 4.0 schema requires specifications to use `decomposition` elements with `xsi:type="NetFactsType"` declarations.

To migrate legacy specifications to YAWL 4.0 format:
1. Replace `<rootNet>` with `<decomposition xsi:type="NetFactsType" isRootNet="true">`
2. Update variable declarations to use `index` and proper type elements
3. Restructure resourcing elements to use `offer`, `allocate`, and `start`
4. Ensure all control elements follow the new schema structure

## Schema Validation

All specifications in this directory follow the YAWL XML Schema 4.0 specification located at `schema/YAWL_Schema4.0.xsd`.

### Validate All Specifications

```bash
# Validate new V4.0-compliant specs
xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/*.xml

# Validate legacy specs (namespace updated)
xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/xml/Beta2-7/*.xml
```

### Check Well-Formedness

```bash
# Check XML structure without schema validation
xmllint --noout exampleSpecs/*.xml
```

## Validation Report

As of 2026-02-17:
- **V4.0-Compliant Specifications**: 3/3 passing schema validation
- **Legacy Specifications Updated**: 12/12 with corrected namespace and version
- **All Files**: Well-formed XML verified

## References

- **YAWL Schema 4.0**: `schema/YAWL_Schema4.0.xsd`
- **YAWL Foundation**: https://www.yawlfoundation.org
- **YAWL Documentation**: https://www.yawlfoundation.org/documentation/
