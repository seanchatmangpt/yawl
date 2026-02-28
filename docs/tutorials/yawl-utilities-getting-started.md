# Getting Started with YAWL Utilities

Learn how to use the foundational utilities library that powers every YAWL module.

## What You'll Learn

By the end of this tutorial, you'll understand:
- YAWL's exception hierarchy and error handling
- How to work with schemas and specification unmarshalling
- Key utility functions for strings, XML, and I/O
- Authentication and logging infrastructure
- Where to find the right helper for common tasks

## Prerequisites

- Java 25 or higher
- Maven 3.9 or later
- Basic familiarity with XML and Java exceptions
- 10-15 minutes

## Step 1: Understanding the Module Structure

YAWL Utilities is the foundation library used by all other YAWL modules. It provides:

```
yawl-utilities/
├── org.yawlfoundation.yawl.util
│   ├── YStringHelper      (String utilities)
│   ├── YXMLHelper         (XML DOM manipulation)
│   ├── YFileUtil          (File I/O)
│   ├── YReflectionHelper  (Reflection utilities)
│   └── YDateTimeHelper    (Date/time utilities)
├── org.yawlfoundation.yawl.exceptions
│   ├── YException         (Base exception class)
│   ├── YConnectivityException
│   ├── YSyntaxException
│   └── ... (10+ custom exceptions)
├── org.yawlfoundation.yawl.schema
│   └── YSchemaVersion     (Schema version management)
├── org.yawlfoundation.yawl.unmarshal
│   └── YSpecificationUnmarshaller
└── org.yawlfoundation.yawl.authentication
    └── YCredential        (Base authentication)
```

## Step 2: Working with YAWL Exceptions

YAWL provides a rich exception hierarchy for domain-specific error handling:

```java
import org.yawlfoundation.yawl.exceptions.*;

// Base exception class
try {
    // Some YAWL operation
} catch (YException e) {
    System.out.println("Error: " + e.getMessage());
    e.printStackTrace();
}

// Specific exception types
try {
    YSpecification spec = loadSpecification("missing.yawl");
} catch (YSyntaxException e) {
    // Specification XML is malformed
    System.out.println("Invalid YAWL syntax: " + e.getMessage());
} catch (YConnectivityException e) {
    // Network or connection issue
    System.out.println("Connection failed: " + e.getMessage());
}

// Create custom exceptions with context
if (taskNotFound) {
    throw new YException("Task 'ReviewApproval' not found in specification");
}
```

### Exception Types

| Exception | When to Catch | Common Causes |
|-----------|---------------|---------------|
| `YSyntaxException` | XML or YAWL grammar errors | Malformed YAWL spec |
| `YConnectivityException` | Network/remote service failures | Engine unreachable |
| `YDataStateException` | Invalid object state | Accessing null fields |
| `YEngineException` | Engine runtime errors | Task execution failure |
| `YTransitionExistsException` | Duplicate state transition | Duplicate task name |
| `YProcessingException` | Process execution errors | Invalid workflow logic |

## Step 3: Handling YAWL Schemas

The schema module manages YAWL specification validation:

```java
import org.yawlfoundation.yawl.schema.*;

// Check current schema version
YSchemaVersion version = YSchemaVersion.getInstance();
System.out.println("YAWL Schema Version: " + version.getSchemaVersion());

// Validate a specification file against the schema
SchemaHandler handler = new SchemaHandler();
String specPath = "my-workflow.yawl";

try {
    boolean valid = handler.validate(specPath);
    if (valid) {
        System.out.println("Specification is valid");
    } else {
        List<String> errors = handler.getErrors();
        for (String error : errors) {
            System.out.println("Validation error: " + error);
        }
    }
} catch (Exception e) {
    System.out.println("Validation failed: " + e.getMessage());
}
```

## Step 4: Unmarshalling YAWL Specifications

Load and parse YAWL specification files:

```java
import org.yawlfoundation.yawl.unmarshal.*;
import org.yawlfoundation.yawl.elements.YSpecification;

// Load from file path
YSpecificationUnmarshaller unmarshaller = new YSpecificationUnmarshaller();
YSpecification spec = unmarshaller.unmarshalSpecifications("hello-world.yawl");

if (spec != null) {
    System.out.println("Specification loaded: " + spec.getSpecificationID());
    System.out.println("Nets: " + spec.getNets().size());
} else {
    System.out.println("Failed to load specification");
}

// Load from XML string
String xmlContent = readFileAsString("workflow.yawl");
YSpecification spec2 = unmarshaller.unmarshallSpecificationFromXML(xmlContent);

// Load from URL
URL specURL = new URL("http://example.com/specs/approval.yawl");
YSpecification spec3 = unmarshaller.unmarshalSpecificationFromURL(specURL);
```

## Step 5: String and XML Utilities

Use helper classes for common text operations:

```java
import org.yawlfoundation.yawl.util.*;
import org.w3c.dom.Document;
import org.jdom2.Document;

// String utilities
String text = "  Hello YAWL World  ";
String trimmed = YStringHelper.trimTail(text);
boolean hasText = YStringHelper.notEmpty(text);

String camelCase = "HelloWorld";
String snakeCase = YStringHelper.toSnakeCase(camelCase);

// XML utilities
String xml = "<root><item>value</item></root>";
Document doc = YXMLHelper.parseXML(xml);

String rootName = YXMLHelper.getRootElementName(doc);
String itemValue = YXMLHelper.getElementValue(doc, "item");

// Format XML for display
String prettyXML = YXMLHelper.formatXML(doc);
System.out.println(prettyXML);

// JDOM utilities
org.jdom2.Document jdom = ...;
String xpath = "//task[@id='ReviewApproval']";
List<org.jdom2.Element> results = YXMLHelper.evaluateXPath(jdom, xpath);
```

## Step 6: File I/O Utilities

Handle file operations safely:

```java
import org.yawlfoundation.yawl.util.*;

// Read file as string
String content = YFileUtil.readFileToString("workflow.yawl");

// Write string to file
YFileUtil.writeStringToFile(content, "output.yawl", "UTF-8");

// Create temp file
File tempFile = YFileUtil.createTempFile("yawl", ".xml");
tempFile.deleteOnExit();

// Get file extension
String path = "/home/user/workflow.yawl";
String ext = YFileUtil.getFileExtension(path);  // "yawl"

// Check if file is readable
boolean canRead = YFileUtil.isReadable("spec.yawl");
```

## Step 7: Authentication Helpers

Use credential management for engine connections:

```java
import org.yawlfoundation.yawl.authentication.*;
import io.jsonwebtoken.*;

// Create JWT-based credentials
String token = Jwts.builder()
    .setSubject("user@example.com")
    .signWith(SignatureAlgorithm.HS256, secretKey)
    .compact();

YCredential credential = new YCredential();
credential.setUserID("user@example.com");
credential.setPassword(token);  // or actual password

// Validate credentials
boolean isValid = validateCredential(credential);

// Extract claims from JWT
String userId = Jwts.parser()
    .setSigningKey(secretKey)
    .parseClaimsJws(token)
    .getBody()
    .getSubject();
```

## Key Takeaways

1. **Exceptions** provide domain-specific error handling
2. **Schema validation** ensures your specifications are well-formed
3. **Unmarshalling** loads YAWL specs from various sources
4. **Utility classes** reduce boilerplate for common operations
5. **Authentication** infrastructure supports multiple credential types

## Common Patterns

### Loading and Validating a Specification

```java
public YSpecification loadAndValidateSpec(String path) throws YException {
    // Step 1: Validate against schema
    SchemaHandler schema = new SchemaHandler();
    if (!schema.validate(path)) {
        throw new YSyntaxException(
            "Schema validation failed: " + schema.getErrors()
        );
    }

    // Step 2: Unmarshal to object model
    YSpecificationUnmarshaller unmarshaller = new YSpecificationUnmarshaller();
    YSpecification spec = unmarshaller.unmarshalSpecifications(path);

    if (spec == null) {
        throw new YSyntaxException("Failed to parse specification");
    }

    // Step 3: Verify specification
    if (!spec.isValid()) {
        throw new YException("Specification is not sound");
    }

    return spec;
}
```

### Processing Specification XML

```java
public void analyzeSpecification(YSpecification spec) {
    for (YNet net : spec.getNets()) {
        String netXML = net.toXML();

        // Parse with JDOM
        Document doc = YXMLHelper.parseXML(netXML);

        // Extract task names using XPath
        String xpath = "//task/name";
        List<Element> taskNames = YXMLHelper.evaluateXPath(doc, xpath);

        for (Element nameElem : taskNames) {
            System.out.println("Task: " + nameElem.getText());
        }
    }
}
```

### Safe Error Handling

```java
public void executeWorkflow(String specPath) {
    try {
        YSpecification spec = loadAndValidateSpec(specPath);
        // ... execute specification

    } catch (YSyntaxException e) {
        // Handle specification format errors
        logger.error("Invalid YAWL specification format", e);
        notifyUser("Your workflow specification has syntax errors. " +
                   "Check the XML format and try again.");

    } catch (YConnectivityException e) {
        // Handle network errors
        logger.error("Cannot reach YAWL engine", e);
        notifyUser("Cannot connect to workflow engine. Check your connection.");

    } catch (YException e) {
        // Catch-all for other YAWL errors
        logger.error("Workflow execution failed", e);
        notifyUser("Workflow execution failed: " + e.getMessage());

    } catch (Exception e) {
        // Non-YAWL errors
        logger.error("Unexpected error", e);
        throw new RuntimeException("Unexpected error occurred", e);
    }
}
```

## Troubleshooting

**"Schema validation failed":**
- Verify YAWL namespace in your specification
- Check that all required elements are present
- Ensure XML is well-formed (valid opening/closing tags)

**"Failed to parse specification":**
- Confirm the file exists and is readable
- Check for encoding issues (should be UTF-8)
- Validate XML structure before unmarshalling

**"ClassNotFoundException for exception":**
- Verify yawl-utilities is in your classpath
- Check that the exception import is correct
- Ensure you're using the right exception type

## Next Steps

- Explore [YAWL Schema Validation](../reference/yawl-schema.md) for advanced schema handling
- Learn [Specification Versioning](../how-to/validate-spec.md)
- Set up [Custom Exception Handling](../reference/error-codes.md)
- Build [Workflow Specifications](./yawl-elements-getting-started.md) programmatically

## API Quick Reference

| Class | Common Methods | Purpose |
|-------|----------------|---------|
| `YStringHelper` | `trimTail()`, `notEmpty()` | String operations |
| `YXMLHelper` | `parseXML()`, `evaluateXPath()` | XML DOM manipulation |
| `YFileUtil` | `readFileToString()`, `writeStringToFile()` | File I/O |
| `YSchemaVersion` | `validate()`, `getSchemaVersion()` | Schema management |
| `YSpecificationUnmarshaller` | `unmarshalSpecifications()` | Spec loading |
| `YException` | Constructor with message | Base exception |

---

**Next step:** Use these utilities to [Run Your First Workflow](./yawl-engine-getting-started.md).
