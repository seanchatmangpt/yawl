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
- Validate with: `xmllint --noout --schema schema/YAWL_Schema4.0.xsd spec.xml`
- Schema validation in Java: `YSchemaVersion` + `javax.xml.validation.Validator`

## Schema Versioning
- v1.0–v3.0: Legacy, read-only (do not modify; engine must still load them)
- v4.0: Current production schema — extend via `schema/extensions/` only
- New elements go in extension schemas — never add to `YAWL_Schema4.0.xsd` core
- Backward compatibility rule: any valid v3.0 spec must load and execute in v6.0 engine

## Specification Structure

Every YAWL specification is XML conforming to `YAWL_Schema4.0.xsd`:

```xml
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  version="4.0">
  <specification uri="unique/spec/uri">
    <metaData>...</metaData>
    <decomposition id="root-net" isRootNet="true" xsi:type="NetFactsType">
      <processControlElements>
        <inputCondition id="start"/>
        <task id="Task1">
          <name>Do Something</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <joinType code="xor"/>
          <splitType code="and"/>
          <decomposesTo id="Task1Decomp"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
    <decomposition id="Task1Decomp" xsi:type="WebServiceGatewayFactsType">
      ...
    </decomposition>
  </specification>
</specificationSet>
```

**Required fields**: `uri` (unique), `isRootNet="true"` on exactly one decomposition, `inputCondition`, `outputCondition`.

## Extension Schema Pattern
```xml
<!-- schema/extensions/AgentBinding.xsd -->
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://www.yawlfoundation.org/yawlschema/extensions/agent">
  <xs:element name="agentBinding">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="agentId" type="xs:string"/>
        <xs:element name="capability" type="xs:string"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

Extension schemas import (not modify) the base schema.

## Validation in Code
```java
// CORRECT — validate before loading
YSchemaVersion version = YSchemaVersion.fromSpec(specXml);
SchemaHandler handler = new SchemaHandler(version);
List<String> errors = handler.validateSpecification(specXml);
if (!errors.isEmpty()) {
    throw new YSpecificationValidationException(errors);
}

// VIOLATION — load without validation
YSpecification spec = unmarshaller.unmarshal(specXml);  // no schema check
```

## Example Specs
- `exampleSpecs/` contains reference workflow specifications used by engine tests
- Load in tests via: `YMarshal.unmarshalSpecifications(new File("exampleSpecs/MakeWebPurchase.xml"))`
- Never modify existing example specs without updating the tests that reference them
- Add new example specs for new workflow patterns — they serve as both docs and regression tests

## XSD Authoring Rules
- Use `xs:string` for IDs; never `xs:ID` (YAWL IDs are not XML-unique within a document)
- All new elements must have `minOccurs`/`maxOccurs` explicitly set
- Enumerations for bounded sets (split/join types: `xor`, `and`, `or`)
- Documentation annotations required on every new complex type (`xs:annotation/xs:documentation`)
