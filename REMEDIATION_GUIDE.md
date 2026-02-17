# YAWL V6 Specification Remediation Guide

**Status**: Implementation guide for resolving critical issues
**Target**: Complete V6 compliance
**Timeline**: 2-3 weeks (parallel phases)

---

## PHASE 1: NAMESPACE FIX (1-2 hours)

This is the highest-priority fix. All 12 specifications currently fail validation due to namespace mismatch.

### Step 1.1: Create Namespace Conversion Script

**File**: `/home/user/yawl/bin/migrate-specs-to-v6.sh`

```bash
#!/bin/bash
# Migrate all specifications to V6 namespace

SPECS_DIR="/home/user/yawl/exampleSpecs/xml/Beta2-7"
SCHEMA_URL="http://www.yawlfoundation.org/yawlschema"
SCHEMA_PATH="classpath:///schema/YAWL_Schema4.0.xsd"

for spec in "$SPECS_DIR"/*.xml; do
  echo "Migrating: $spec"
  
  # Backup original
  cp "$spec" "$spec.backup"
  
  # Replace namespace
  sed -i 's|xmlns="http://www.citi.qut.edu.au/yawl"|xmlns="'"$SCHEMA_URL"'"|g' "$spec"
  
  # Replace schema location
  sed -i 's|xsi:schemaLocation=".*"|xsi:schemaLocation="'"$SCHEMA_URL"' '"$SCHEMA_PATH"'"|g' "$spec"
  
  # Validate
  xmllint --schema /home/user/yawl/schema/YAWL_Schema4.0.xsd "$spec" > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "  ✓ Valid"
  else
    echo "  ✗ FAILED - see above"
    # Restore backup
    cp "$spec.backup" "$spec"
  fi
done

echo "Migration complete"
```

### Step 1.2: Before & After Examples

**BEFORE** (Current - FAILS VALIDATION):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.citi.qut.edu.au/yawl"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://www.citi.qut.edu.au/yawl d:/yawl/schema/YAWL_Schema.xsd">
  <specification uri="BarnesAndNoble.xml">
    <!-- content -->
  </specification>
</specificationSet>
```

**AFTER** (V6 Compliant - PASSES VALIDATION):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema classpath:///schema/YAWL_Schema4.0.xsd">
  <specification uri="BarnesAndNoble">
    <!-- content -->
  </specification>
</specificationSet>
```

**Key Changes**:
- `xmlns`: QUT → YAWL Foundation
- `schemaLocation`: Windows path `d:/yawl/schema/YAWL_Schema.xsd` → classpath URL
- `uri`: Remove `.xml` extension (artifact of legacy naming)

### Step 1.3: Manual Verification

After running migration script, verify each spec:

```bash
# Test one specification
xmllint --schema /home/user/yawl/schema/YAWL_Schema4.0.xsd \
  /home/user/yawl/exampleSpecs/xml/Beta2-7/BarnesAndNoble.xml

# Expected output: 
# BarnesAndNoble.xml validates
```

### Step 1.4: Validation Test

Create automated test to ensure namespace compliance:

**File**: `/home/user/yawl/test/org/yawlfoundation/yawl/schema/NamespaceComplianceTest.java`

```java
package org.yawlfoundation.yawl.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class NamespaceComplianceTest {
    
    private static final String V6_NAMESPACE = "http://www.yawlfoundation.org/yawlschema";
    private static final String DEPRECATED_NAMESPACE = "http://www.citi.qut.edu.au/yawl";
    
    @Test
    @DisplayName("All specifications must use V6 namespace")
    void testNamespaceCompliance() throws Exception {
        Path specDir = Paths.get("exampleSpecs/xml/Beta2-7");
        
        List<Path> specs = Files.walk(specDir)
            .filter(p -> p.toString().endsWith(".xml"))
            .collect(Collectors.toList());
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        for (Path spec : specs) {
            Document doc = builder.parse(spec.toFile());
            Element root = doc.getDocumentElement();
            String namespace = root.getNamespaceURI();
            
            if (namespace.equals(DEPRECATED_NAMESPACE)) {
                throw new AssertionError(
                    spec.getFileName() + " uses deprecated namespace: " + namespace
                );
            }
            
            if (!namespace.equals(V6_NAMESPACE)) {
                throw new AssertionError(
                    spec.getFileName() + " uses incorrect namespace: " + namespace
                );
            }
        }
    }
    
    @Test
    @DisplayName("All specifications must reference V6 schema")
    void testSchemaLocationCompliance() throws Exception {
        Path specDir = Paths.get("exampleSpecs/xml/Beta2-7");
        
        Files.walk(specDir)
            .filter(p -> p.toString().endsWith(".xml"))
            .forEach(spec -> {
                try {
                    String content = Files.readString(spec);
                    
                    if (content.contains("d:/")) {
                        throw new AssertionError(
                            spec.getFileName() + " contains Windows path (d:/)"
                        );
                    }
                    
                    if (content.contains("YAWL_Schema.xsd")) {
                        throw new AssertionError(
                            spec.getFileName() + " references legacy YAWL_Schema.xsd"
                        );
                    }
                    
                    if (!content.contains("YAWL_Schema4.0.xsd")) {
                        throw new AssertionError(
                            spec.getFileName() + " doesn't reference YAWL_Schema4.0.xsd"
                        );
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
```

---

## PHASE 2: SCHEMA CLEANUP (30 minutes)

Archive deprecated schemas to reduce confusion and technical debt.

### Step 2.1: Create Archive Directory

```bash
mkdir -p /home/user/yawl/schema/deprecated/
```

### Step 2.2: Move Legacy Schemas

```bash
cd /home/user/yawl/schema/

# Archive legacy versions
mv YAWL_Schema.xsd deprecated/
mv YAWL_Schema2.0.xsd deprecated/
mv YAWL_Schema2.1.xsd deprecated/
mv YAWL_Schema2.2.xsd deprecated/
mv YAWL_Schema3.0.xsd deprecated/

# Archive beta versions
mv YAWL_SchemaBeta3.xsd deprecated/
mv YAWL_SchemaBeta4.xsd deprecated/
mv YAWL_SchemaBeta6.xsd deprecated/
mv YAWL_SchemaBeta7.1.xsd deprecated/

# Keep ONLY v4.0 active
ls -la  # Verify only YAWL_Schema4.0.xsd remains
```

### Step 2.3: Create Deprecation Notice

**File**: `/home/user/yawl/schema/README.md`

```markdown
# YAWL Schema Files

## Current Version (V6.0)

### Active Schema
- **YAWL_Schema4.0.xsd** - Production schema for YAWL v6.0 and later

All new specifications MUST use:
```xml
xmlns="http://www.yawlfoundation.org/yawlschema"
xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema classpath:///schema/YAWL_Schema4.0.xsd"
```

## Legacy Schemas (Deprecated)

For historical reference only. Archived in `/deprecated/` directory.

| Schema | Version | Status | Use Case |
|--------|---------|--------|----------|
| YAWL_Schema.xsd | Ancient | DEPRECATED | Legacy QUT era |
| YAWL_Schema2.0.xsd | v2.0 | DEPRECATED | Historical reference |
| YAWL_Schema2.1.xsd | v2.1 | DEPRECATED | Historical reference |
| YAWL_Schema2.2.xsd | v2.2 | DEPRECATED | Historical reference |
| YAWL_Schema3.0.xsd | v3.0 | DEPRECATED | Migration source |
| YAWL_SchemaBeta3.xsd | Beta 3 | DEPRECATED | Pre-release |
| YAWL_SchemaBeta4.xsd | Beta 4 | DEPRECATED | Pre-release |
| YAWL_SchemaBeta6.xsd | Beta 6 | DEPRECATED | Pre-release |
| YAWL_SchemaBeta7.1.xsd | Beta 7.1 | DEPRECATED | Pre-release |

To access legacy schemas: `cat deprecated/YAWL_Schema3.0.xsd`

## Migration Guide

To upgrade specifications from v3.0 to v4.0:
1. See `SCHEMA_MIGRATION_V4.0.md` in docs directory
2. Update namespace to `http://www.yawlfoundation.org/yawlschema`
3. Update schema location to `YAWL_Schema4.0.xsd`
4. Run schema validation: `xmllint --schema YAWL_Schema4.0.xsd spec.xml`
```

---

## PHASE 3: PATH PORTABILITY (1 hour)

Replace Windows-specific paths with cross-platform classpath URLs.

### Step 3.1: Schema Validator Implementation

**File**: `/home/user/yawl/src/org/yawlfoundation/yawl/elements/YSpecificationUnmarshaller.java`

```java
package org.yawlfoundation.yawl.elements;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import org.xml.sax.SAXException;

/**
 * Unmarshalls XML specification strings to YSpecification objects.
 * Includes cross-platform schema validation using classpath resources.
 */
public class YSpecificationUnmarshaller {
    
    private static final String SCHEMA_RESOURCE = "/schema/YAWL_Schema4.0.xsd";
    private static volatile Schema cachedSchema;
    
    /**
     * Validates and unmarshalls XML specification string to YSpecification.
     * Schema is loaded from classpath (works on all platforms).
     * 
     * @param specificationXml XML string to validate and parse
     * @return YSpecification object
     * @throws IllegalArgumentException if XML is invalid per schema
     * @throws IOException if schema resource cannot be loaded
     */
    public static YSpecification unmarshall(String specificationXml) 
            throws IOException, SAXException {
        
        // Validate against schema first
        validateAgainstSchema(specificationXml);
        
        // Parse XML
        return parseXml(specificationXml);
    }
    
    /**
     * Validates XML string against YAWL_Schema4.0.xsd.
     * Uses classpath-relative schema location (cross-platform).
     * 
     * @param xml XML string to validate
     * @throws SAXException if XML does not conform to schema
     * @throws IOException if schema cannot be loaded
     */
    private static void validateAgainstSchema(String xml) 
            throws SAXException, IOException {
        
        Schema schema = getSchema();
        Validator validator = schema.newValidator();
        
        try {
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            throw new IllegalArgumentException(
                "Specification does not conform to YAWL_Schema4.0.xsd:\n" + 
                e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Gets or creates cached schema instance.
     * Schema is loaded from classpath, works on all platforms.
     * 
     * @return Schema instance
     * @throws IOException if schema cannot be loaded
     * @throws SAXException if schema is invalid
     */
    private static Schema getSchema() throws SAXException, IOException {
        if (cachedSchema == null) {
            synchronized (YSpecificationUnmarshaller.class) {
                if (cachedSchema == null) {
                    cachedSchema = loadSchema();
                }
            }
        }
        return cachedSchema;
    }
    
    /**
     * Loads YAWL schema from classpath resource.
     * Works on Windows, Linux, macOS, Docker, and CI/CD agents.
     * 
     * @return Compiled schema
     * @throws IOException if resource not found
     * @throws SAXException if schema is invalid
     */
    private static Schema loadSchema() throws SAXException, IOException {
        try (InputStream schemaStream = 
                YSpecificationUnmarshaller.class
                    .getResourceAsStream(SCHEMA_RESOURCE)) {
            
            if (schemaStream == null) {
                throw new IOException(
                    "Schema resource not found on classpath: " + SCHEMA_RESOURCE + "\n" +
                    "Expected location: target/classes/schema/YAWL_Schema4.0.xsd\n" +
                    "Or in JAR: jar:file:yawl.jar!/schema/YAWL_Schema4.0.xsd"
                );
            }
            
            SchemaFactory factory = SchemaFactory.newInstance(
                XMLConstants.W3C_XML_SCHEMA_NS_URI
            );
            
            return factory.newSchema(
                new StreamSource(schemaStream)
            );
        }
    }
    
    /**
     * Parses validated XML into YSpecification object.
     * 
     * @param xml XML string (already validated)
     * @return YSpecification
     */
    private static YSpecification parseXml(String xml) {
        // Implementation uses existing unmarshalling logic
        // (unchanged from current codebase)
        return YSpecification.unmarshalFromXML(xml);
    }
}
```

### Step 3.2: Update POM Configuration

**File**: `/home/user/yawl/yawl-elements/pom.xml`

Add resource configuration to include schema in JAR:

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <includes>
                <include>**/*.properties</include>
                <include>**/*.xsd</include>
                <include>**/*.xml</include>
            </includes>
        </resource>
    </resources>
</build>
```

Create directory and copy schema:

```bash
mkdir -p /home/user/yawl/yawl-elements/src/main/resources/schema
cp /home/user/yawl/schema/YAWL_Schema4.0.xsd \
   /home/user/yawl/yawl-elements/src/main/resources/schema/
```

### Step 3.3: Specification Path Test

**File**: `/home/user/yawl/test/org/yawlfoundation/yawl/schema/PathPortabilityTest.java`

```java
package org.yawlfoundation.yawl.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class PathPortabilityTest {
    
    @Test
    @DisplayName("No specifications should contain Windows-specific paths")
    void testNoWindowsPaths() throws Exception {
        Path specDir = Paths.get("exampleSpecs/xml");
        
        List<Path> problematicSpecs = Files.walk(specDir)
            .filter(p -> p.toString().endsWith(".xml"))
            .filter(p -> {
                try {
                    String content = Files.readString(p);
                    return content.contains("d:/") || 
                           content.contains("D:/") ||
                           content.matches(".*[A-Z]:\\\\.*");
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.toList());
        
        if (!problematicSpecs.isEmpty()) {
            String errors = problematicSpecs.stream()
                .map(p -> "  - " + p.getFileName())
                .collect(Collectors.joining("\n"));
            
            throw new AssertionError(
                "Specifications contain Windows-specific paths:\n" + errors
            );
        }
    }
    
    @Test
    @DisplayName("All specifications should use classpath schema locations")
    void testClasspathSchemaLocations() throws Exception {
        Path specDir = Paths.get("exampleSpecs/xml");
        
        List<Path> nonCompliant = Files.walk(specDir)
            .filter(p -> p.toString().endsWith(".xml"))
            .filter(p -> {
                try {
                    String content = Files.readString(p);
                    return !content.contains("classpath://");
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.toList());
        
        if (!nonCompliant.isEmpty()) {
            String errors = nonCompliant.stream()
                .map(p -> "  - " + p.getFileName())
                .collect(Collectors.joining("\n"));
            
            throw new AssertionError(
                "Specifications don't use classpath schema locations:\n" + errors
            );
        }
    }
}
```

---

## PHASE 4: EXAMPLES & TESTS (4-6 hours)

Create V6-native specification examples and validation tests.

### Step 4.1: Simple Sequential Workflow Example

**File**: `/home/user/yawl/exampleSpecs/v6-workflows/simple-sequential-workflow.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema classpath:///schema/YAWL_Schema4.0.xsd">
    
    <specification uri="simple-sequential-workflow" name="Simple Sequential Workflow">
        <documentation>
            Demonstrates basic YAWL v6 workflow pattern.
            A simple sequence: Input → Process → Output
        </documentation>
        
        <rootNet id="SimpleSequentialNet">
            <localVariable name="orderData">
                <type>xs:string</type>
            </localVariable>
            
            <processControlElements>
                <!-- Input condition -->
                <inputCondition id="start">
                    <name>Start</name>
                    <flowsInto>
                        <nextElementRef id="receiveOrder"/>
                    </flowsInto>
                </inputCondition>
                
                <!-- First task -->
                <task id="receiveOrder">
                    <name>Receive Order</name>
                    <documentation>Accepts incoming order</documentation>
                    <flowsInto>
                        <nextElementRef id="processOrder"/>
                    </flowsInto>
                    <join code="xor"/>
                    <split code="and"/>
                    <completedMappings>
                        <mapping>
                            <expression query="/data/orderData"/>
                            <mapsTo>orderData</mapsTo>
                        </mapping>
                    </completedMappings>
                    <decomposesTo id="ReceiveOrderDecomp"/>
                </task>
                
                <!-- Second task -->
                <task id="processOrder">
                    <name>Process Order</name>
                    <documentation>Processes the received order</documentation>
                    <flowsInto>
                        <nextElementRef id="shipOrder"/>
                    </flowsInto>
                    <join code="xor"/>
                    <split code="and"/>
                    <startingMappings>
                        <mapping>
                            <expression query="/data/orderData"/>
                            <mapsTo>orderData</mapsTo>
                        </mapping>
                    </startingMappings>
                    <completedMappings>
                        <mapping>
                            <expression query="/data/orderData"/>
                            <mapsTo>orderData</mapsTo>
                        </mapping>
                    </completedMappings>
                    <decomposesTo id="ProcessOrderDecomp"/>
                </task>
                
                <!-- Third task -->
                <task id="shipOrder">
                    <name>Ship Order</name>
                    <documentation>Prepares order for shipment</documentation>
                    <flowsInto>
                        <nextElementRef id="end"/>
                    </flowsInto>
                    <join code="xor"/>
                    <split code="and"/>
                    <startingMappings>
                        <mapping>
                            <expression query="/data/orderData"/>
                            <mapsTo>orderData</mapsTo>
                        </mapping>
                    </startingMappings>
                    <decomposesTo id="ShipOrderDecomp"/>
                </task>
                
                <!-- Output condition -->
                <outputCondition id="end">
                    <name>End</name>
                </outputCondition>
            </processControlElements>
        </rootNet>
        
        <!-- Task decompositions -->
        <decomposition id="ReceiveOrderDecomp" xsi:type="NetFactsType">
            <name>Receive Order Subprocess</name>
            <outputExpression query="/data/orderData"/>
            <outputParam name="orderData">
                <type>xs:string</type>
                <mandatory/>
            </outputParam>
        </decomposition>
        
        <decomposition id="ProcessOrderDecomp" xsi:type="NetFactsType">
            <name>Process Order Subprocess</name>
            <inputParam name="orderData">
                <type>xs:string</type>
                <mandatory/>
            </inputParam>
            <outputExpression query="/data/orderData"/>
            <outputParam name="orderData">
                <type>xs:string</type>
                <mandatory/>
            </outputParam>
        </decomposition>
        
        <decomposition id="ShipOrderDecomp" xsi:type="NetFactsType">
            <name>Ship Order Subprocess</name>
            <inputParam name="orderData">
                <type>xs:string</type>
                <mandatory/>
            </inputParam>
        </decomposition>
    </specification>
</specificationSet>
```

### Step 4.2: Schema Validation Test

**File**: `/home/user/yawl/test/org/yawlfoundation/yawl/schema/SpecificationSchemaValidationTest.java`

```java
package org.yawlfoundation.yawl.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class SpecificationSchemaValidationTest {
    
    private static Schema schema;
    
    @BeforeAll
    static void setupSchema() throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(
            XMLConstants.W3C_XML_SCHEMA_NS_URI
        );
        schema = factory.newSchema(
            new File("schema/YAWL_Schema4.0.xsd")
        );
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("allSpecifications")
    @DisplayName("All specifications validate against YAWL_Schema4.0.xsd")
    void testSpecificationValidatesAgainstSchema(String specName, Path spec) 
            throws Exception {
        
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(spec.toFile()));
        // Implicitly passes if no exception thrown
    }
    
    static Stream<Object[]> allSpecifications() throws IOException {
        Path exampleDir = Paths.get("exampleSpecs/xml");
        Path v6Dir = Paths.get("exampleSpecs/v6-workflows");
        
        return Stream.concat(
            // Example specifications
            Files.walk(exampleDir)
                .filter(p -> p.toString().endsWith(".xml"))
                .filter(p -> !p.toString().contains("deprecated"))
                .map(p -> new Object[]{p.getFileName().toString(), p}),
            
            // V6 workflow examples
            Files.walk(v6Dir)
                .filter(p -> p.toString().endsWith(".xml"))
                .map(p -> new Object[]{"v6/" + p.getFileName().toString(), p})
        );
    }
}
```

---

## PHASE 5: DOCUMENTATION (3 hours)

Fix schema metadata and create migration guide.

### Step 5.1: Fix Schema Version

**File**: `/home/user/yawl/schema/YAWL_Schema4.0.xsd` (Line 25)

**BEFORE**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           xmlns:yawl="http://www.yawlfoundation.org/yawlschema"
           targetNamespace="http://www.yawlfoundation.org/yawlschema"
           elementFormDefault="qualified"
           attributeFormDefault="unqualified"
           version="3.0">  <!-- WRONG -->
```

**AFTER**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           xmlns:yawl="http://www.yawlfoundation.org/yawlschema"
           targetNamespace="http://www.yawlfoundation.org/yawlschema"
           elementFormDefault="qualified"
           attributeFormDefault="unqualified"
           version="4.0">  <!-- CORRECT -->
```

### Step 5.2: Create Migration Guide

**File**: `/home/user/yawl/docs/SCHEMA_MIGRATION_V4.0.md`

```markdown
# YAWL Schema Migration Guide: v3.0 → v4.0

**Version**: v4.0 (YAWL v6.0+)
**Last Updated**: 2026-02-17

## Overview

YAWL_Schema4.0.xsd is the production schema for YAWL v6.0 and later.
This guide helps migrate specifications from v3.0 to v4.0.

## Key Changes

### 1. Namespace Migration

**v3.0 (Deprecated)**:
```xml
xmlns="http://www.citi.qut.edu.au/yawl"
```

**v4.0 (Current)**:
```xml
xmlns="http://www.yawlfoundation.org/yawlschema"
```

All elements must use the YAWL Foundation namespace.

### 2. Schema Location

**v3.0 (Deprecated)**:
```xml
xsi:schemaLocation="http://www.citi.qut.edu.au/yawl d:/yawl/schema/YAWL_Schema3.0.xsd"
```

**v4.0 (Current)**:
```xml
xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema classpath:///schema/YAWL_Schema4.0.xsd"
```

Use `classpath:///` for cross-platform compatibility.

### 3. Element Changes

| Element | v3.0 | v4.0 | Notes |
|---------|------|------|-------|
| specification[@uri] | BarnesAndNoble.xml | BarnesAndNoble | Remove .xml extension |
| yawlService | qut:yawlService | yawl:yawlService | Namespace prefix updated |
| decomposition | xsi:type varies | xsi:type="NetFactsType" | Use explicit types |

## Migration Checklist

- [ ] Update xmlns attribute to YAWL Foundation namespace
- [ ] Update xsi:schemaLocation to use classpath URL
- [ ] Remove .xml extension from specification[@uri]
- [ ] Update yawlService namespace prefix
- [ ] Run schema validation: `xmllint --schema YAWL_Schema4.0.xsd spec.xml`
- [ ] Test specification loads in YAWL engine
- [ ] Run test suite to verify behavior unchanged

## Validation

After migration, validate your specification:

```bash
xmllint --schema schema/YAWL_Schema4.0.xsd yourspec.xml
```

Expected output:
```
yourspec.xml validates
```

If validation fails, check:
1. Namespace is correct: `http://www.yawlfoundation.org/yawlschema`
2. All elements use correct namespace prefix
3. Required attributes are present
4. Element nesting is valid

## Support

For migration issues, see:
- SPEC_VALIDATION_REPORT.md - Detailed validation issues
- schema/README.md - Schema documentation
- YSpecification.java - Specification unmarshalling code
```

---

## IMPLEMENTATION CHECKLIST

### Phase 1: Namespace (Priority 1)
- [ ] Create migration script
- [ ] Test on one specification
- [ ] Update all 12 specifications
- [ ] Verify with xmllint
- [ ] Run NamespaceComplianceTest

### Phase 2: Schema Cleanup (Priority 2)
- [ ] Create /schema/deprecated/ directory
- [ ] Archive 9 legacy schemas
- [ ] Create /schema/README.md
- [ ] Verify only v4.0 active

### Phase 3: Path Portability (Priority 3)
- [ ] Update YSpecificationUnmarshaller
- [ ] Copy schema to resources
- [ ] Update pom.xml
- [ ] Run PathPortabilityTest

### Phase 4: Examples & Tests (Priority 4)
- [ ] Create simple-sequential-workflow.xml
- [ ] Create parallel-execution-workflow.xml (template)
- [ ] Create conditional-branching-workflow.xml (template)
- [ ] Create SpecificationSchemaValidationTest
- [ ] Verify all tests pass

### Phase 5: Documentation (Priority 5)
- [ ] Fix schema version metadata
- [ ] Create SCHEMA_MIGRATION_V4.0.md
- [ ] Add xs:documentation elements
- [ ] Verify documentation completeness

---

## SUCCESS CRITERIA

All phases complete when:

1. **Schema Compliance**: 100% (12/12 specs validate)
2. **Namespace Compliance**: 100% (no deprecated namespaces)
3. **Path Portability**: 100% (no Windows-specific paths)
4. **V6 Examples**: 3-5 native examples created
5. **Test Coverage**: Schema validation tests passing
6. **Documentation**: Migration guide complete

---

**Timeline**: 2-3 weeks (parallel phases)
**Effort**: ~10-12 hours development time
**Result**: Production-ready V6 specification infrastructure

