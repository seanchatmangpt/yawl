# QLever Embedded Test Examples

This directory contains comprehensive test examples demonstrating the usage of QLever Embedded within the YAWL workflow engine.

## Overview

The test examples cover all major aspects of QLever integration:

1. **Basic Index Loading and Query Execution** - Fundamental usage patterns
2. **SELECT Query with JSON Output** - Data retrieval with JSON parsing
3. **CONSTRUCT Query with Turtle Output** - RDF graph construction
4. **Error Handling Patterns** - Robust error management
5. **Multiple Output Formats** - CSV, TSV, XML, and JSON support
6. **Triple Count and Metadata** - Index analysis and statistics
7. **Resource Cleanup** - Proper resource management
8. **Integration Tests** - YAWL-specific workflow scenarios

## Test Files

### QLeverExamplesTest.java
Location: `src/test/java/org/yawlfoundation/yawl/qlever/QLeverExamplesTest`

Core test examples demonstrating fundamental QLever usage patterns:

#### Basic Query Execution
```java
@Test
@DisplayName("Basic Index Loading and Query Execution")
void testBasicQueryExecution() throws Exception {
    // Start QLever server
    // Load index
    // Execute simple query
    // Process results
    // Clean up resources
}
```

#### SELECT Query with JSON Output
```java
@Test
@DisplayName("SELECT Query with JSON Output")
void testSelectQueryWithJsonOutput() throws Exception {
    // SPARQL SELECT query
    // Execute query
    // Parse JSON results
    // Process rows and columns
}
```

#### CONSTRUCT Query with Turtle Output
```java
@Test
@DisplayName("CONSTRUCT Query with Turtle Output")
void testConstructQueryWithTurtleOutput() throws Exception {
    // SPARQL CONSTRUCT query
    // Execute with Turtle format
    // Validate Turtle syntax
    // Process RDF triples
}
```

### QLeverIntegrationTest.java
Location: `src/test/java/org/yawlfoundation/yawl/qlever/QLeverIntegrationTest`

Integration tests for YAWL-specific scenarios:

#### Active Work Items Query
```java
@Test
@DisplayName("Query Active Work Items")
void testQueryActiveWorkItems() throws Exception {
    // Find pending work items
    // Get assignments and priorities
    // Filter by status
    // Return formatted results
}
```

#### Workflow Case Lifecycle Analysis
```java
@Test
@DisplayName("Workflow Case Lifecycle Analysis")
void testWorkflowCaseLifecycle() throws Exception {
    // Track case states over time
    // Calculate duration metrics
    // Group by status
    // Generate statistics
}
```

#### Workload Analysis
```java
@Test
@DisplayName("Workload Analysis by Department")
void testWorkloadAnalysisByDepartment() throws Exception {
    // Count work items by department
    // Analyze priority distribution
    // Calculate user workload
    // Generate insights
}
```

### QLeverTestUtils.java
Location: `src/test/java/org/yawlfoundation/yawl/qlever/QLeverTestUtils`

Utility class with helper methods for testing:

#### Test Data Generation
```java
// Generate YAWL workflow data
String data = QLeverTestUtils.generateYawlWorkflowData();

// Generate performance test data
String perfData = QLeverTestUtils.generatePerformanceTestData();
```

#### Query Validation
```java
// Validate query result structure
QLeverTestUtils.validateSparqlResult(result);
QLeverTestUtils.validateParsedResult(parser);
```

#### Performance Benchmarking
```java
// Benchmark query performance
Map<String, Long> metrics = QLeverTestUtils.benchmarkQuery(engine, query, iterations);

// Format results
String report = QLeverTestUtils.formatBenchmarkResults(metrics);
```

## Running the Tests

### Using Maven
```bash
# Run all QLever tests
mvn test -Dtest=QLeverExamplesTest

# Run integration tests only
mvn test -Dtest=QLeverIntegrationTest

# Run with balanced profile
mvn -P balanced test -Dtest=QLeverExamplesTest

# Run with stress testing
mvn -P stress-test test -Dtest=QLeverExamplesTest
```

### Using the Test Runner Script
```bash
# Display test examples (no compilation required)
./test-runner.sh

# Shows all test patterns with example code
```

## Test Patterns

### 1. Engine Initialization Pattern
```java
@BeforeEach
void setUp() throws Exception {
    QLeverEngineConfiguration config = new QLeverEngineConfiguration();
    config.setServerPort(0); // Random port
    config.setIndexDir(createTestIndexDir());
    config.setDataDir(createTestDataDir());

    qleverEngine = new QLeverEngine(config);
    qleverEngine.start();

    // Wait for server to be ready
    Thread.sleep(1000);
}
```

### 2. Resource Cleanup Pattern
```java
@AfterEach
void tearDown() {
    if (qleverEngine != null && qleverEngine.isRunning()) {
        qleverEngine.stop();
    }
    cleanupTestDirectories();
}
```

### 3. Try-With-Resources Pattern
```java
try (QLeverEngine engine = new QLeverEngine(config)) {
    engine.start();
    QLeverQueryResult result = engine.executeSparqlQuery(query);
    // Process results
} // Engine automatically stopped
```

### 4. Error Handling Pattern
```java
try {
    QLeverQueryResult result = qleverEngine.executeSparqlQuery(query);
    if (result.getStatusCode() != 200) {
        // Handle error
        logger.error("Query failed: {}", result.getResponse());
    }
} catch (QLeverException e) {
    logger.error("QLever error: {}", e.getMessage());
    // Handle exception
}
```

### 5. Batch Query Pattern
```java
// Execute multiple queries concurrently
ExecutorService executor = Executors.newFixedThreadPool(4);
List<Future<QLeverQueryResult>> futures = new ArrayList<>();

for (String query : queries) {
    futures.add(executor.submit(() ->
        qleverEngine.executeSparqlQuery(query)));
}

// Collect results
for (Future<QLeverQueryResult> future : futures) {
    QLeverQueryResult result = future.get();
    // Process result
}
```

### 6. Pagination Pattern
```java
int pageSize = 100;
List<Map<String, Object>> allResults = new ArrayList<>();

for (int page = 0; page < totalPages; page++) {
    String query = String.format(
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o } " +
        "LIMIT %d OFFSET %d", pageSize, page * pageSize);

    QLeverQueryResult result = qleverEngine.executeSparqlQuery(query);
    // Process page results
}
```

## Test Data

### YAWL Workflow Data
The tests include sample YAWL workflow data with:

- **Work Items**: Tasks with status, priority, and assignments
- **Cases**: Workflow cases with lifecycle tracking
- **Users**: Users with departments and roles
- **Events**: Workflow events for auditing and analysis

### Performance Test Data
For stress testing, the framework can generate:

- 1,000 workflow cases
- 5,000 work items
- 10 users
- Various status and priority combinations

## Common SPARQL Queries

### Count Queries
```sparql
SELECT (COUNT(*) AS ?total) WHERE { ?s ?p ?o }
SELECT (COUNT(DISTINCT ?s) AS ?subjects) WHERE { ?s ?p ?o }
SELECT ?status (COUNT(*) AS ?count) WHERE { ?workitem yawl:status ?status } GROUP BY ?status
```

### Pattern Queries
```sparql
# Find work items by status
SELECT ?workitem ?name WHERE { ?workitem a yawl:WorkItem ; yawl:name ?name ; yawl:status "pending" }

# Find case assignments
SELECT ?case ?user ?name WHERE { ?case yawl:assignedTo ?user ; yawl:name ?name }

# Find overdue work items
SELECT ?workitem ?name ?due WHERE { ?workitem yawl:name ?name ; yawl:due ?due . FILTER(?due < NOW()) }
```

### Analytics Queries
```sparql
# Workload by department
SELECT ?dept (COUNT(?workitem) AS ?workload) WHERE { ?user yawl:department ?dept ; yawl:assignedTo ?workitem } GROUP BY ?dept

# Case duration analysis
SELECT ?case ?duration WHERE { ?case yawl:created ?start ; yawl:completed ?end . BIND(?end - ?start AS ?duration) }
```

## Best Practices

### 1. Resource Management
- Always use try-with-resources or try-finally
- Ensure engine is stopped after use
- Clean up temporary files

### 2. Error Handling
- Check response codes
- Handle timeouts gracefully
- Validate query syntax before execution

### 3. Performance
- Use appropriate output formats
- Implement pagination for large results
- Consider concurrent execution for batch queries

### 4. Testing
- Include both positive and negative test cases
- Test boundary conditions
- Validate query results thoroughly

### 5. YAWL Integration
- Use YAWL-specific predicates and namespaces
- Model workflow relationships correctly
- Consider case lifecycle states

## Troubleshooting

### Common Issues

1. **Engine won't start**
   - Check port availability
   - Verify index and data directories exist
   - Ensure native libraries are available

2. **Query fails with syntax error**
   - Validate SPARQL syntax
   - Check predicate and namespace validity
   - Ensure data exists for the query pattern

3. **Memory issues with large results**
   - Implement pagination
   - Use streaming pattern
   - Consider batch processing

4. **Timeout errors**
   - Increase timeout in configuration
   - Optimize query performance
   - Use LIMIT clauses

### Debug Tips

- Enable verbose logging
- Use the test runner script to see patterns
- Check response contents for error messages
- Validate test data generation

## Contributing

When adding new test examples:

1. Follow the existing pattern structure
2. Include comprehensive comments
3. Add both positive and negative test cases
4. Document any special requirements
5. Ensure proper resource cleanup
6. Update this README with new patterns

## License

These test examples are part of the YAWL Foundation project and follow the same license terms as the main YAWL project.