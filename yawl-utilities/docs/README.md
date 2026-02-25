# YAWL Utilities Module

## Overview

The `yawl-utilities` module provides a comprehensive collection of **shared utility classes** and helper components that are used across the YAWL ecosystem. This module contains fundamental services for string manipulation, XML processing, database connectivity, HTTP communication, and modern Java 25 features.

The utilities are designed to be reusable across different YAWL modules and can be used independently in non-YAWL applications where appropriate.

## Key Features

- **String Manipulation**: Advanced text processing, formatting, and validation
- **XML Processing**: JDOM-based utilities for document handling and XPath queries
- **Database Integration**: Connection pooling and performance monitoring
- **HTTP Communication**: REST client and URL validation utilities
- **Security**: Password encryption and token handling
- **Modern Java 25**: Records, sealed classes, and concurrency patterns
- **Error Handling**: Comprehensive error management and recovery

## Core Components

### String Utilities (`StringUtil`)

**Purpose**: Comprehensive string manipulation and formatting utilities.

**Key Features:**
- Token replacement and formatting
- Date/time manipulation and formatting
- HTML/XML escaping and unescaping
- URL encoding/decoding
- Number formatting and parsing
- Random string generation
- Validation helpers

**Example Usage:**
```java
// Format timestamp
String timestamp = StringUtil.getCurrentTimestamp("yyyy-MM-dd HH:mm:ss");

// Replace tokens in template
String result = StringUtil.replaceTokens(
    "Hello ${name}, your order ${order_id} is ready",
    Map.of("name", "Alice", "order_id", "12345")
);

// Generate random ID
String randomId = RandomStringUtils.randomAlphanumeric(16);
```

### XML Utilities (`JDOMUtil`)

**Purpose**: High-level XML processing using JDOM framework.

**Key Features:**
- Document parsing and serialization
- XPath queries with namespace support
- XML formatting and pretty-printing
- Element manipulation and traversal
- UTF-8 BOM handling
- Schema validation integration

**Example Usage:**
```java
// Parse XML document
String xml = "<workflow><task id='t1'/></workflow>";
Document doc = JDOMUtil.buildDocument(new StringReader(xml));

// Query with XPath
XPathExpression<Element> expr = XPathFactory.instance()
    .prepareExpression("//task", Filters.element());
List<Element> tasks = expr.evaluate(doc);
```

### Database Utilities

**Connection Pooling (`HikariCPConnectionProvider`)**
- High-performance connection pooling with HikariCP
- Automatic connection lifecycle management
- Performance monitoring and metrics
- Retry logic for connection failures

**Database Performance Monitor (`DatabasePerformanceMonitor`)**
- Query execution time tracking
- Connection pool monitoring
- Slow query detection and logging
- Performance metrics collection

### HTTP Utilities

**HTTP Client (`HttpUtil`)**
- REST API client with connection pooling
- JSON and XML response handling
- Authentication support (basic, bearer tokens)
- Retry logic and timeout management
- Request/response logging

**URL Validator (`HttpURLValidator`)**
- Comprehensive URL validation
- Protocol checking (http, https, ftp)
- Reachability testing
- Security validation

### Security Utilities

**Password Encryption**
- Argon2-based password hashing
- BCrypt support for backward compatibility
- Secure random salt generation
- Password strength validation

**Token Handling**
- Secure token generation and validation
- Expiration management
- Encryption support

## Modern Java 25 Features

This module demonstrates modern Java 25 patterns and features:

### Records
- Immutable data structures for API payloads and DTOs
- Automatic method generation (equals, hashCode, toString)
- Compact constructors for validation

**Example:**
```java
public record CaseIdentifier(String caseId, String taskId) {
    public CaseIdentifier {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be null");
        }
        caseId = caseId.trim().toUpperCase();
        taskId = taskId.trim();
    }
}
```

### Virtual Threads
- Per-case virtual thread execution
- Structured concurrency for parallel processing
- Scoped value propagation for context sharing

**Example:**
```java
StructuredTaskScope.ShutdownOnFailure scope =
    new StructuredTaskScope.ShutdownOnFailure();

try (scope) {
    Thread.startVirtualThread(() -> {
        // Process case work items
    });
    scope.join();
}
```

### Sealed Classes
- Exhaustive pattern matching for domain types
- Type-safe event hierarchies
- Compiler-verified completeness

### Pattern Matching
- Exhaustive switch expressions
- Type-safe pattern matching
- Cleaner, more readable code

## Module Dependencies

- **Core Java**: Java 8+ (with Java 25 features for modern components)
- **JDOM**: XML processing framework
- **Apache Commons**: String utilities and random generation
- **HikariCP**: Database connection pooling
- **Argon2**: Password hashing
- **Log4j**: Logging framework

## Configuration

### Database Connection Pool
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mysql://localhost:3306/yawl");
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);
```

### HTTP Client
```java
HttpConfig config = new HttpConfig()
    .setBaseUrl("https://api.example.com")
    .setTimeout(5000)
    .setRetries(3)
    .setEnableLogging(true);
```

## Performance Characteristics

### String Processing
- **Token Replacement**: O(n) where n is string length
- **Date Formatting**: < 1ms per operation
- **HTML Escaping**: O(n) with minimal overhead

### XML Processing
- **Document Parsing**: Depends on document size
- **XPath Queries**: O(n) for document traversal
- **Serialization**: O(n) for output generation

### Database Operations
- **Connection Acquisition**: < 10ms (with pool)
- **Query Execution**: Varies by complexity
- **Connection Pool**: Bounded by pool size

### HTTP Operations
- **Request Time**: Depends on network latency
- **Retries**: Configurable with exponential backoff
- **Connection Pool**: Reuses HTTP connections

## Testing

The module includes comprehensive test coverage with 9 test classes:

### Record Tests
- Record compact constructor validation
- Record auto-generated method verification
- Record serialization/deserialization
- Performance comparison with traditional classes

### Concurrency Tests
- Virtual thread performance
- Structured concurrency patterns
- Scoped value propagation
- Thread safety validation

### Performance Tests
- Database connection pool benchmarks
- XML processing performance
- String utility operation timing
- Memory usage analysis

**Testing Framework:**
- JUnit 5 with modern annotations
- Chicago TDD methodology
- Parameterized tests for edge cases
- Performance benchmarks

## Usage Examples

### Basic String Processing
```java
// Format a message with dynamic content
String message = StringUtil.formatMessage(
    "Case {0} completed in {1}ms",
    caseId, executionTime
);

// Validate input strings
if (StringUtil.isNotEmpty(input) &&
    StringUtil.isValidEmail(email)) {
    // Process input
}
```

### XML Processing Workflow
```java
// Parse and process YAWL specification
YSpecification spec = unmarshalSpecification(specXML);
Document doc = JDOMUtil.buildDocument(new StringReader(specXML));

// Extract workflow elements
XPathExpression<Element> expr = XPathFactory.instance()
    .prepareExpression("//YTask", Filters.element());
List<Element> tasks = expr.evaluate(doc);
```

### Database Operations
```java
// Get database connection
Connection conn = connectionProvider.getConnection();
try {
    // Execute query
    List<Case> cases = databaseService.queryCases(conn, "status = 'pending'");
} finally {
    connectionProvider.closeConnection(conn);
}
```

### Modern Java Features
```java
// Use records for immutable data
WorkItem item = new WorkItem(
    "case-123",
    "task-456",
    "pending",
    Instant.now(),
    Map.of("priority", "high")
);

// Pattern matching for events
switch (event) {
    case CaseStartedEvent ce -> handleCaseStart(ce);
    case WorkItemEvent we -> handleWorkItem(we);
    case TimerEvent te -> handleTimer(te);
}
```

## Integration Patterns

### Microservice Communication
```java
// REST API integration
ApiResponse response = HttpUtil.postJson(
    "https://service.com/api/cases",
    caseData,
    authToken
);
```

### Event Processing
```java
// Event-driven architecture
caseEventListener.add(new CaseEventHandler());
// Events processed asynchronously via virtual threads
```

### Database Transaction Management
```java
// Transactional operations
databaseService.executeTransaction(conn -> {
    // Update case status
    updateCaseStatus(conn, caseId, "completed");
    // Log audit trail
    logAuditTrail(conn, caseId, "completed");
    return null;
});
```

## Best Practices

1. **String Utilities**: Use for dynamic text formatting, avoid concatenation in loops
2. **XML Processing**: Cache compiled XPath expressions for better performance
3. **Database**: Always use connection pooling, implement proper error handling
4. **HTTP**: Implement retry logic, set appropriate timeouts
5. **Modern Java**: Prefer records for immutable data, use pattern matching when possible

## Migration Considerations

- **Java Version**: Gradual migration from traditional classes to records
- **Thread Model**: Consider virtual threads for I/O-bound operations
- **Error Handling**: Leverage sealed classes for type-safe error handling
- **Performance**: Profile before adopting modern features

## Version Information

- **Current Version**: 6.0.0
- **Java Requirements**: Java 8+ (Java 25 for modern features)
- **Thread Safety**: Appropriate for each utility class
- **Serialization**: Java Serializable where applicable

---

*For detailed API documentation, see the Javadoc in the `src/org/yawlfoundation/yawl/util/` package.*