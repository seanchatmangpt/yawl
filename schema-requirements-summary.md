# YAWL Schema Requirements Summary

Based on YAWL_Schema4.0.xsd validation, here are the key element ordering requirements:

## 1. SpecificationSetFactsType Required Attributes
- **version="4.0"** (required) - Must be exactly "4.0"

## 2. YAWLSpecificationFactsType Child Elements Order
Mandatory sequence:
1. **name** (optional, Type: NameType)
2. **documentation** (optional, Type: DocumentationType)
3. **metaData** (required, Type: MetaDataType) - Dublin Core metadata
4. **Any XML Schema elements** (optional, namespace="http://www.w3.org/2001/XMLSchema")
5. **decomposition** (required, maxOccurs=unbounded, Type: DecompositionType)
   - Contains all workflow nets
6. **importedNet** (optional, maxOccurs=unbounded, Type: anyURI)

## 3. NetFactsType Structure
**Attributes:**
- **isRootNet** (required, boolean)

**Child Elements Order:**
1. **name** (optional, inherited from DecompositionFactsType)
2. **documentation** (optional, inherited from DecompositionFactsType)
3. **inputParam** (optional, maxOccurs=unbounded, Type: InputParameterFactsType)
4. **outputParam** (optional, maxOccurs=unbounded, Type: OutputParameterFactsType)
5. **logPredicate** (optional, Type: LogPredicateFactsType)
6. **localVariable** (optional, maxOccurs=unbounded, Type: VariableFactsType)
7. **processControlElements** (required complex type)
   - Contains:
     - **inputCondition** (required, Type: ExternalConditionFactsType)
     - **choice** of:
       - **task** (optional, maxOccurs=unbounded, Type: ExternalTaskFactsType)
       - **condition** (optional, maxOccurs=unbounded, Type: ExternalConditionFactsType)
     - **outputCondition** (required, Type: OutputConditionFactsType)
8. **externalDataGateway** (optional, Type: NameType)

## 4. ExternalTaskFactsType Child Element Order
Inherits from ExternalNetElementFactsType (name, documentation, flowsInto), then:

1. **join** (required, Type: ControlTypeType) - AND/OR/XOR join control
2. **split** (required, Type: ControlTypeType) - AND/OR/XOR split control
3. **defaultConfiguration** (optional, Type: ConfigurationType)
4. **configuration** (optional, Type: ConfigurationType)
5. **removesTokens** (optional, maxOccurs=unbounded, Type: ExternalNetElementType)
6. **removesTokensFromFlow** (optional, maxOccurs=unbounded, Type: RemovesTokensFromFlowType)
7. **startingMappings** (optional, Type: VarMappingSetType)
8. **completedMappings** (optional, Type: VarMappingSetType)
9. **enablementMappings** (optional, Type: VarMappingSetType)
10. **timer** (optional, Type: TimerType)
11. **resourcing** (optional, Type: ResourcingFactsType)
12. **customForm** (optional, Type: anyURI)
13. **decomposesTo** (optional, Type: DecompositionType)

## 5. InputParameterFactsType Structure
Extends VariableBaseType:
1. **index** (required, integer)
2. **documentation** (optional, string)
3. **choice**:
   - **name** + optional **type** + optional **namespace** OR
   - **element** (NCName)
4. **choice** (optional):
   - **initialValue** (string) OR
   - **mandatory** (boolean)
5. **logPredicate** (optional, Type: LogPredicateFactsType)

## 6. ExternalConditionFactsType Structure
Simple extension of ExternalNetElementFactsType:
- Contains: name, documentation, flowsInto (inherited from ExternalNetElementFactsType)

## Additional Important Constraints

### Variable Ordering Rules
- Variables must be unique across inputParam and localVariable
- Cannot have duplicate variable names in same scope

### Decomposition Rules
- Each decomposition must have unique @id
- Foreign key references must point to valid decomposition IDs
- Net elements must have unique IDs within each net

### Flow Relationships
- All flowsInto elements must reference valid element IDs
- removesTokens and removesTokensFromFlow must reference valid element IDs
- Default flows must be properly marked

### Configuration Elements
- Configuration elements (join, nofi, rem, split) must follow defined order
- Configuration ports must be properly typed (InputPortConfigType/OutputPortConfigType)

### Resourcing Structure
- Must contain: offer, allocate, start in that order
- Optional: secondary, privileges
- privilege element limited to max 7 occurrences

### Timer Structure
- Either:
  1. **netparam** (simple string), OR
  2. **trigger** + choice of:
     - **expiry** (long), OR
     - **duration** (duration) OR **durationparams** (TimerDurationFactsType)
- Optional: **workdays** (boolean)

## Key Validation Points
1. All required elements must be present
2. Element order must be strictly followed
3. Attribute values must conform to enumerated types
4. ID references must be valid and unique
5. Variable and parameter names must be unique within scope
6. Version must be exactly "4.0"
7. Dublin Core metadata structure must be maintained
8. Complex choice elements must follow proper ordering constraints
