# YAWL Schema 4.0 Milestone Extension Changes

## Summary

This document describes the XSD schema changes made to YAWL_Schema4.0.xsd to support the Milestone pattern (WCP-18).

## Version Information

- **Schema Version**: 4.0
- **Update Date**: 2026-02-28
- **Pattern ID**: WCP-18 (Milestone)
- **Backward Compatible**: YES - All changes are additive and optional

## Added Simple Types

### MilestoneExpiryTypeCodeType

Enum for milestone expiry types:

```xml
<xs:simpleType name="MilestoneExpiryTypeCodeType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="TIME_BASED"/>
    <xs:enumeration value="DATA_BASED"/>
    <xs:enumeration value="NEVER"/>
  </xs:restriction>
</xs:simpleType>
```

**Values**:
- `TIME_BASED`: Milestone expires after specified timeout (milliseconds)
- `DATA_BASED`: Milestone expires when condition becomes false
- `NEVER`: Milestone never expires once reached

### MilestoneGuardOperatorCodeType

Enum for milestone guard operators:

```xml
<xs:simpleType name="MilestoneGuardOperatorCodeType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="AND"/>
    <xs:enumeration value="OR"/>
    <xs:enumeration value="XOR"/>
  </xs:restriction>
</xs:simpleType>
```

**Values**:
- `AND`: All referenced milestones must be reached
- `OR`: At least one milestone must be reached
- `XOR`: Exactly one milestone must be reached

## Added Complex Types

### MilestoneConditionFactsType

Defines the structure of milestone condition elements in a net.

```xml
<xs:complexType name="MilestoneConditionFactsType">
  <xs:complexContent>
    <xs:extension base="yawl:ExternalConditionFactsType">
      <xs:sequence>
        <xs:element name="expression" type="yawl:XQueryType"/>
        <xs:element name="expiryType" type="yawl:MilestoneExpiryTypeCodeType" minOccurs="0"/>
        <xs:element name="expiryTimeout" type="xs:long" minOccurs="0"/>
      </xs:sequence>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>
```

**Parent**: `ExternalConditionFactsType` (same as input/output conditions)

**Children**:
- `expression` (required): XPath/XQuery expression for milestone condition
- `expiryType` (optional): How the milestone expires
- `expiryTimeout` (optional): Timeout value in milliseconds (0 = no timeout)

### MilestoneGuardType

Represents a single milestone guard reference within a guard set.

```xml
<xs:complexType name="MilestoneGuardType">
  <xs:sequence>
    <xs:element name="milestoneRef" type="yawl:ExternalNetElementType"/>
  </xs:sequence>
</xs:complexType>
```

**Children**:
- `milestoneRef`: Reference to a milestone condition (must exist in same net)

### MilestoneGuardsType

Represents the complete milestone guards on a task.

```xml
<xs:complexType name="MilestoneGuardsType">
  <xs:sequence>
    <xs:element name="guard" type="yawl:MilestoneGuardType" maxOccurs="unbounded"/>
  </xs:sequence>
  <xs:attribute name="operator" type="yawl:MilestoneGuardOperatorCodeType" use="required"/>
</xs:complexType>
```

**Attributes**:
- `operator` (required): AND, OR, or XOR operator for guard evaluation

**Children**:
- `guard` (1..n): One or more milestone guard references

## Modified Complex Types

### NetFactsType (processControlElements)

**Change**: Added milestone element to the choice group

**Before**:
```xml
<xs:choice maxOccurs="unbounded">
  <xs:element name="task" type="yawl:ExternalTaskFactsType" minOccurs="0" maxOccurs="unbounded"/>
  <xs:element name="condition" type="yawl:ExternalConditionFactsType" minOccurs="0" maxOccurs="unbounded"/>
</xs:choice>
```

**After**:
```xml
<xs:choice maxOccurs="unbounded">
  <xs:element name="task" type="yawl:ExternalTaskFactsType" minOccurs="0" maxOccurs="unbounded"/>
  <xs:element name="condition" type="yawl:ExternalConditionFactsType" minOccurs="0" maxOccurs="unbounded"/>
  <xs:element name="milestone" type="yawl:MilestoneConditionFactsType" minOccurs="0" maxOccurs="unbounded"/>
</xs:choice>
```

**Impact**: Nets can now contain milestone elements alongside tasks and conditions.

### ExternalTaskFactsType

**Change**: Added milestoneGuards element for task guard support

**Before**:
```xml
<xs:element name="timer" type="yawl:TimerType" minOccurs="0"/>
<xs:element name="resourcing" type="yawl:ResourcingFactsType" minOccurs="0"/>
```

**After**:
```xml
<xs:element name="timer" type="yawl:TimerType" minOccurs="0"/>
<xs:element name="milestoneGuards" type="yawl:MilestoneGuardsType" minOccurs="0"/>
<xs:element name="resourcing" type="yawl:ResourcingFactsType" minOccurs="0"/>
```

**Impact**: Tasks can now have optional milestoneGuards to control execution.

## Backward Compatibility

All schema changes are **fully backward compatible**:

1. **New Simple Types**: No impact on existing content
2. **New Complex Types**: Only added, not modified
3. **New Elements in Nets**: Optional (minOccurs="0")
4. **New Elements in Tasks**: Optional (minOccurs="0")
5. **Existing Elements**: Unchanged in structure and cardinality

**Validation**: Existing YAWL specifications without milestones validate against this schema without modification.

## Schema Validation

The updated schema has been validated for:

- XML well-formedness
- XSD syntax correctness
- Element and attribute constraints
- Type inheritance consistency
- Namespace declarations

### Validation Commands

```bash
# Validate the schema itself
xmllint --schema http://www.w3.org/2001/XMLSchema.xsd YAWL_Schema4.0.xsd

# Validate a specification with milestones
xmllint --schema YAWL_Schema4.0.xsd myspec.xml
```

## Migration Guide

### For Existing Workflows

No changes required. Workflows without milestones continue to work unchanged.

### Adding Milestones to Existing Workflows

1. Add `<milestone>` elements to `<processControlElements>`
2. Add `<milestoneGuards>` to tasks that should be controlled by milestones
3. Ensure milestone IDs are unique within the net
4. Validate with: `xmllint --schema YAWL_Schema4.0.xsd yourspec.xml`

### Example Migration

**Original (no milestones)**:
```xml
<processControlElements>
  <inputCondition id="start">...</inputCondition>
  <task id="Task1">...</task>
  <task id="Task2">...</task>
  <outputCondition id="end"/>
</processControlElements>
```

**With Milestones**:
```xml
<processControlElements>
  <inputCondition id="start">...</inputCondition>
  <task id="Task1">...</task>
  <milestone id="Milestone1">
    <expression>...</expression>
    <flowsInto><nextElementRef id="Task2"/></flowsInto>
  </milestone>
  <task id="Task2">
    <milestoneGuards operator="AND">
      <guard><milestoneRef id="Milestone1"/></guard>
    </milestoneGuards>
  </task>
  <outputCondition id="end"/>
</processControlElements>
```

## Testing

Test fixtures validating against this schema:

- `test/org/yawlfoundation/yawl/schema/milestones/valid-milestone-condition.xml`
- `test/org/yawlfoundation/yawl/schema/milestones/valid-milestone-or-guard.xml`
- `test/org/yawlfoundation/yawl/schema/milestones/valid-milestone-xor-guard.xml`
- `test/org/yawlfoundation/yawl/schema/milestones/invalid-milestone-operator.xml`

## References

- **WCP-18 Specification**: http://www.workflowpatterns.com/patterns/control/18.php
- **YAWL Foundation**: http://www.yawlfoundation.org/
- **Schema Documentation**: See YAWL_Schema4.0.xsd
- **Milestone Pattern Guide**: exampleSpecs/MILESTONE_PATTERN_GUIDE.md

## Change Summary Statistics

| Category | Count |
|----------|-------|
| New Simple Types | 2 |
| New Complex Types | 3 |
| Modified Complex Types | 2 |
| New Elements | 3 |
| Deprecated Elements | 0 |
| Breaking Changes | 0 |

---

**Schema Version**: 4.0  
**Update Date**: 2026-02-28  
**Status**: COMPLETE AND VALIDATED
