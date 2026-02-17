# YAWL Beta to Version 4.0 Migration Guide

## Overview

This document provides guidance for migrating YAWL workflow specifications from Beta 2-7 format to YAWL Version 4.0 format.

## Key Changes from Beta to Version 4.0

### 1. Namespace Change

**Beta Format (Deprecated)**:
```xml
<specificationSet xmlns="http://www.citi.qut.edu.au/yawl"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.citi.qut.edu.au/yawl d:/yawl/schema/YAWL_Schema.xsd">
```

**Version 4.0 Format (Current)**:
```xml
<specificationSet version="4.0"
                  xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema 
                                      http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
```

### 2. Root Element Structure

**Beta Format**:
```xml
<specification uri="MyWorkflow">
  <metaData/>
  <rootNet id="MyNet">
    <!-- Process control elements -->
  </rootNet>
</specification>
```

**Version 4.0 Format**:
```xml
<specification uri="MyWorkflow">
  <metaData/>
  <decomposition id="MyNet" xsi:type="NetFactsType" isRootNet="true">
    <!-- Process control elements -->
  </decomposition>
</specification>
```

### 3. Variable Declarations

**Beta Format**:
```xml
<localVariable name="itemId">
  <type>xs:string</type>
</localVariable>
```

**Version 4.0 Format**:
```xml
<localVariable>
  <index>0</index>
  <name>itemId</name>
  <type>xs:string</type>
</localVariable>
```

**Key Changes**:
- Variables must have an `<index>` element (sequential numbering starting from 0)
- `<name>` and type information must be in separate elements
- Namespace prefixes required for data types

### 4. Resourcing Elements

**Beta Format**:
```xml
<resourcing>
  <offer initiator="user"/>
  <allocation initiator="user"/>
  <start initiator="user"/>
</resourcing>
```

**Version 4.0 Format**:
```xml
<resourcing>
  <offer initiator="user">
    <!-- Optional: distributionSet, familiarParticipant -->
  </offer>
  <allocate initiator="user">
    <!-- Optional: strategy, distributionSet, familiarParticipant -->
  </allocate>
  <start initiator="user"/>
  <!-- Optional: <secondary/>, <privileges/> -->
</resourcing>
```

**Key Changes**:
- `<allocation>` changed to `<allocate>`
- Resourcing elements can have nested configuration elements
- Optional secondary and privilege specifications

### 5. Data Mappings

**Beta Format**:
```xml
<completedMappings>
  <mapping>
    <expression query="/data/isbn"/>
    <mapsTo>isbn</mapsTo>
  </mapping>
</completedMappings>
```

**Version 4.0 Format**:
```xml
<completedMappings>
  <mapping>
    <expression query="/data/isbn"/>
    <mapsTo>isbn</mapsTo>
  </mapping>
</completedMappings>
```

Note: Data mapping syntax remains largely compatible between versions.

## Step-by-Step Migration Process

### Step 1: Update Namespace and Version

1. Replace the old namespace URI:
   - From: `http://www.citi.qut.edu.au/yawl`
   - To: `http://www.yawlfoundation.org/yawlschema`

2. Add or update version attribute: `version="4.0"`

3. Update schema location to: `http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd`

### Step 2: Restructure Root Element

1. Replace `<rootNet>` with `<decomposition>`
2. Add attributes: `xsi:type="NetFactsType" isRootNet="true"`
3. Add the `xsi:` namespace prefix to the root element if not present

### Step 3: Update Variable Declarations

For each `<localVariable>`:
1. Add `<index>N</index>` where N is the ordinal position (0-based)
2. Wrap the name in `<name>` element
3. Ensure type uses proper namespace prefix (e.g., `xs:string` for XML Schema types)
4. Add `<initialValue>` if you have default values

Example transformation:
```xml
<!-- BEFORE -->
<localVariable name="poNumber">
  <type>xs:string</type>
</localVariable>

<!-- AFTER -->
<localVariable>
  <index>0</index>
  <name>poNumber</name>
  <type>xs:string</type>
  <initialValue>PO-DEFAULT</initialValue>
</localVariable>
```

### Step 4: Update Resourcing Elements

For each `<resourcing>` block:
1. Change `<allocation>` to `<allocate>`
2. Add nested elements for distribution and participant management as needed
3. Ensure all required sub-elements are present: `offer`, `allocate`, `start`

### Step 5: Validate Against Schema

Run validation to ensure compliance:
```bash
xmllint --schema schema/YAWL_Schema4.0.xsd your_spec.xml
```

## Complete Migration Example

### Before (Beta Format)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.citi.qut.edu.au/yawl"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.citi.qut.edu.au/yawl YAWL_Schema.xsd">
  <specification uri="OrderProcess">
    <name>Order Processing</name>
    <metaData/>
    <rootNet id="OrderNet">
      <localVariable name="orderId">
        <type>xs:string</type>
      </localVariable>
      <localVariable name="amount">
        <type>xs:decimal</type>
      </localVariable>
      <processControlElements>
        <inputCondition id="start">
          <flowsInto>
            <nextElementRef id="processOrder"/>
          </flowsInto>
        </inputCondition>
        <task id="processOrder">
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </rootNet>
  </specification>
</specificationSet>
```

### After (Version 4.0 Format)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0"
                  xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                                      http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
  <specification uri="OrderProcess">
    <name>Order Processing</name>
    <metaData/>
    <decomposition id="OrderNet" xsi:type="NetFactsType" isRootNet="true">
      <localVariable>
        <index>0</index>
        <name>orderId</name>
        <type>xs:string</type>
      </localVariable>
      <localVariable>
        <index>1</index>
        <name>amount</name>
        <type>xs:decimal</type>
      </localVariable>
      <processControlElements>
        <inputCondition id="start">
          <flowsInto>
            <nextElementRef id="processOrder"/>
          </flowsInto>
        </inputCondition>
        <task id="processOrder">
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
```

## Validation Checklist

- [ ] Namespace updated to `http://www.yawlfoundation.org/yawlschema`
- [ ] Version attribute set to `version="4.0"`
- [ ] Schema location updated to `YAWL_Schema4.0.xsd`
- [ ] `<rootNet>` replaced with `<decomposition xsi:type="NetFactsType" isRootNet="true">`
- [ ] All variables have `<index>` elements
- [ ] Variable names wrapped in `<name>` elements
- [ ] Type declarations use namespace prefixes
- [ ] Resourcing `<allocation>` changed to `<allocate>`
- [ ] All resourcing blocks have `offer`, `allocate`, and `start` elements
- [ ] XML validates against schema: `xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml`

## Common Issues and Solutions

### Issue: "Element is not expected"
**Cause**: Old element names used (e.g., `<allocation>` instead of `<allocate>`)
**Solution**: Check element names against the schema and update accordingly

### Issue: "Attribute is not expected"
**Cause**: Using `isRootNet` on `<rootNet>` instead of `<decomposition>`
**Solution**: Change `<rootNet>` to `<decomposition>` with xsi:type specification

### Issue: "Missing child element"
**Cause**: Variables missing `<index>` or other required elements
**Solution**: Add all required child elements according to schema

### Issue: Type validation errors
**Cause**: Types not using proper namespace prefixes
**Solution**: Use `xs:string`, `xs:decimal`, etc. with the `xs:` namespace prefix

## References

- YAWL Foundation: https://www.yawlfoundation.org
- YAWL Schema 4.0: `/schema/YAWL_Schema4.0.xsd`
- Example V4.0 Specifications: `/exampleSpecs/*.xml`
