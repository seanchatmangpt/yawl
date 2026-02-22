# Z.AI Integration Test Suite

This comprehensive test suite validates the integration between YAWL workflow engine and Z.AI intelligent service.

## Test Structure

The test suite is organized into nested test classes:

### Service Initialization Tests
- Verifies proper initialization with API key
- Tests connection to ZAI service
- Validates available models

### XML Generation Tests
- Generates simple workflow specifications
- Creates complex workflows with conditional branches
- Validates AI-generated XML structure

### XML Validation Tests
- Validates XML against YAWL Schema 4.0
- Tests workflow instantiation from XML
- Verifies error handling for invalid XML

### Workflow Context Tests
- Analyzes workflow context and suggests actions
- Makes workflow decisions with multiple options
- Tests contextual understanding

### Data Processing Tests
- Transforms data using AI rules
- Extracts structured information from text
- Validates workflow data against rules

### Documentation Tests
- Generates workflow documentation from specifications
- Validates documentation completeness

### Error Handling Tests
- Handles API timeouts gracefully
- Tests invalid model handling
- Validates state maintenance across requests

### End-to-End Tests
- Complete workflow generation and execution
- Integration of all components

## Prerequisites

To run the API tests, set the `ZAI_API_KEY` environment variable:
```bash
export ZAI_API_KEY=your_api_key_here
```

## Running Tests

### With Maven (when dependencies are resolved)
```bash
mvn test -pl yawl-integration -Dtest=ZaiIntegrationTest
```

### Using the test runner
```bash
cd test/zai
javac TestRunner.java
java TestRunner
```

## Test Features

### Chicago TDD Methodology
- All tests use real ZAI API calls when available
- No mocks in production code
- Comprehensive error handling
- Graceful degradation when API is unavailable

### XML Validation
- Tests use YAWL Schema 4.0 for validation
- Validates both simple and complex workflow specifications
- Tests workflow instantiation from AI-generated XML

### Data Processing
- Demonstrates AI-powered data transformation
- Shows information extraction capabilities
- Validates data against business rules

### Error Handling
- Tests timeout scenarios
- Handles API unavailability gracefully
- Maintains state across requests

## Integration Points

1. **YAWL Schema Validation** - XML validation against schema/YAWL_Schema4.0.xsd
2. **YMarshal Unmarshalling** - Parse XML to YSpecification objects
3. **ZAI Service Integration** - AI-powered workflow generation and analysis
4. **Workflow Engine** - End-to-end workflow execution

## Test Coverage

- Service initialization and connection
- XML generation and validation
- Workflow instantiation
- Data transformation and extraction
- Context analysis and decision making
- Error handling and edge cases
- End-to-end integration

## Notes

- Tests use JUnit 5 with modern annotations
- Follows YAWL coding conventions
- Comprehensive documentation and comments
- Designed for maintainability and extensibility