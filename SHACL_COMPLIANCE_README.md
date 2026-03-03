# YAWL SHACL Compliance Implementation

This implementation provides complete SHACL (Shapes Constraint Language) compliance validation for YAWL workflow specifications.

## Overview

The SHACL compliance system validates YAWL workflow specifications against a comprehensive set of SHACL shapes to ensure:
- Structural consistency
- Semantic correctness
- Conformance to YAWL patterns and best practices

## Components

### 1. SHACL Shapes (TTL Format)

The system includes four main shape files:

#### `src/main/resources/shacl/yawl-core-shapes.ttl`
- Base constraints for all YAWL elements
- YAWL specification validation
- YAWL net structure validation
- Basic task and flow validation

#### `src/main/resources/shacl/yawl-workflow-shapes.ttl`
- Process task validation
- Service task validation
- Human task validation
- Parameter validation

#### `src/main/resources/shacl/yawl-net-shapes.ttl`
- Detailed net element validation
- Condition validation
- Gateway validation (XOR join/split)
- Flow constraints

#### `src/main/resources/shacl/yawl-element-shapes.ttl`
- Variable and data type validation
- Port and mapping validation
- Delegation rule validation
- Routing constraint validation

### 2. Java Validation Classes

#### Core Interfaces
- `GuardChecker` - Interface for all validation checkers
- `GuardViolation` - Represents a single validation violation
- `GuardReceipt` - Summary of validation results
- `GuardSummary` - Detailed statistics

#### Validators
- `YAWLShaclValidator` - Core SHACL validation engine
- `ShaclValidationChecker` - Integrator for the guard system
- `HyperStandardsValidator` - Orchestrates all validation checkers
- `SHACLValidationCLI` - Command line interface

### 3. Integration Points

#### Validation Pipeline
The system integrates with the YAWL validation pipeline:
```java
// Create validator
HyperStandardsValidator validator = new HyperStandardsValidator();

// Validate a file
GuardReceipt receipt = validator.validateFile(Paths.get("workflow.yawl"));

// Check status
if (receipt.getStatus().equals("GREEN")) {
    // Validation passed
} else {
    // Handle violations
}
```

#### Guard System Integration
The SHACL validator implements the `GuardChecker` interface, allowing it to be used alongside other validation strategies.

## Usage

### Command Line Interface

```bash
# Validate a single file
java org.yawlfoundation.yawl.validation.SHACLValidationCLI workflow.yawl

# Validate a directory
java org.yawlfoundation.yawl.validation.SHACLValidationCLI ./specs/

# Get help
java org.yawlfoundation.yawl.validation.SHACLValidationCLI --help
```

### Programmatic Usage

```java
// Initialize validator
HyperStandardsValidator validator = new HyperStandardsValidator();

// Validate file or directory
Path target = Paths.get("workflow.yawl");
GuardReceipt receipt = validator.validateDirectory(target);

// Process results
if (receipt.getStatus().equals("GREEN")) {
    System.out.println("✓ Validation passed");
} else {
    System.out.println("✗ Validation failed with " +
        receipt.getViolations().size() + " violations");

    // Print violations
    receipt.getViolations().forEach(v -> {
        System.out.println("  - " + v.getPattern() + ": " + v.getContent());
    });
}
```

### Using Individual Checkers

```java
// Use SHACL checker directly
ShaclValidationChecker shaclChecker = new ShaclValidationChecker();
List<GuardViolation> violations = shaclChecker.check(Paths.get("workflow.yawl"));

// Use YAWL validator directly
YAWLShaclValidator yawlValidator = new YAWLShaclValidator();
GuardReceipt receipt = yawlValidator.validateSpecifications(
    Paths.get("specs/")
);
```

## Validation Examples

### Valid YAWL Specification
```turtle
@prefix yawl: <http://www.yawlfoundation.org/yawl#> .

<#workflow> a yawl:YAWLSpecification ;
    yawl:hasSpecificationVersion "2.0" ;
    yawl:hasYAWLNet <#net> .

<#net> a yawl:YAWLNet ;
    yawl:hasInputCondition <#start> ;
    yawl:hasOutputCondition <#end> ;
    yawl:hasYAWLTask <#task1> .

<#start> a yawl:Condition ;
    yawl:hasName "start" .

<#task1> a yawl:Task ;
    yawl:hasName "task1" ;
    yawl:hasInputFlow <#flow1> ;
    yawl:hasOutputFlow <#flow2> .
```

### Invalid YAWL Specification
```turtle
@prefix yawl: <http://www.yawlfoundation.org/yawl#> .

<#workflow> a yawl:YAWLSpecification ;
    yawl:hasSpecificationVersion "2.0" ;
    yawl:hasYAWLNet <#net> .

<#net> a yawl:YAWLNet ;
    yawl:hasOutputCondition <#end> ;
    yawl:hasYAWLTask <#task1> .
    # Missing input condition - violation!
```

## Exit Codes

- **0** - Validation passed (GREEN status)
- **1** - Validation failed (RED status)
- **2** - Error occurred (exception, file not found, etc.)

## Error Reporting

### GuardViolation Format
Each violation includes:
- `pattern` - Validation pattern name (e.g., "SHACL_VALIDATION")
- `severity` - "FAIL" or "WARN"
- `file` - File path
- `line` - Line number (approximate)
- `content` - Specific violation message
- `fixGuidance` - Instructions for fixing the issue
- `timestamp` - When the violation was detected

### GuardReceipt Summary
Provides overall validation results:
- `phase` - Validation phase name
- `timestamp` - When validation was performed
- `filesScanned` - Number of files processed
- `status` - "GREEN" or "RED"
- `errorMessage` - Error message if RED
- `summary` - Detailed statistics by pattern type

## Test Coverage

The implementation includes comprehensive unit tests:
- `SHACLValidatorTest.java` - Full test suite
- Tests for valid/invalid specifications
- Tests for file/directory validation
- Tests for interface compliance
- Tests for error handling

## Integration with Build Process

The SHACL validator can be integrated into the YAWL build process:

### Maven Integration
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <mainClass>org.yawlfoundation.yawl.validation.SHACLValidationCLI</mainClass>
        <args>
            <arg>${project.basedir}/src/main/resources</arg>
        </args>
    </configuration>
</plugin>
```

### Ant Integration
```xml
<target name="validate-shacl">
    <java classpathref="classpath" classname="org.yawlfoundation.yawl.validation.SHACLValidationCLI">
        <arg value="src/main/resources"/>
    </java>
</target>
```

## Dependencies

The implementation requires:
- Apache Jena for SHACL processing
- YAWL validation interfaces (provided)
- Java 8+

## Performance Considerations

- SHACL validation is performed in-memory for optimal performance
- Large files are processed in streaming mode when possible
- Shape files are cached after initial load
- Validation can be run in parallel for multiple files

## Extensibility

### Adding New Shapes
1. Create new TTL shape file in `src/main/resources/shacl/`
2. Add shape loading logic to `YAWLShaclValidator`
3. Update `loadShapesModel()` method

### Adding New Checkers
1. Implement `GuardChecker` interface
2. Register checker in `HyperStandardsValidator`
3. Add appropriate tests

### Custom Validation Logic
- Extend existing shapes with custom constraints
- Implement custom checkers for specific validation rules
- Override violation handling in custom validators

## Troubleshooting

### Common Issues

1. **File not found errors**
   - Check file paths and extensions
   - Ensure files are in supported formats (.yawl, .ttl, .xml, .java)

2. **SHACL validation errors**
   - Verify shape files are properly formatted
   - Check focus node paths in constraints
   - Validate RDF syntax

3. **Class loading errors**
   - Ensure all dependencies are in classpath
   - Check Java version compatibility

### Debug Mode
Enable verbose output by setting system property:
```bash
java -Dorg.yawlfoundation.yawl.validation.debug=true \
     org.yawlfoundation.yawl.validation.SHACLValidationCLI workflow.yawl
```

## License

This implementation is part of the YAWL project and is licensed under the GNU Lesser General Public License.