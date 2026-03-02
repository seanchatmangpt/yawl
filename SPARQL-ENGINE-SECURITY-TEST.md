# SPARQL Engine Security Integration Test

## Overview

Created: `SparqlEngineSecurityIntegrationTest.java` in `test/org/yawlfoundation/yawl/integration/autonomous/marketplace/`

This comprehensive security test validates the security requirements for SPARQL engines used in the YAWL autonomous marketplace.

## Test Methods

### Authentication Tests
1. **`testSparqlQueryWithValidCredentials()`**
   - Tests that SPARQL queries succeed with proper authentication
   - Uses basic authentication with valid credentials
   - Verifies 200 OK response with valid data

2. **`testSparqlQueryWithInvalidCredentials()`**
   - Tests that SPARQL queries are rejected with invalid credentials
   - Uses wrong password to trigger authentication failure
   - Verifies 401 Unauthorized response

3. **`testSparqlQueryWithMissingCredentials()`**
   - Tests that SPARQL queries are rejected without authentication
   - Sends request without Authorization header
   - Verifies 401 Unauthorized response

### Authorization Tests
4. **`testSparqlUpdateRequiresWritePermission()`**
   - Tests that SPARQL UPDATE operations require proper permissions
   - Attempts to execute INSERT DATA with read-only credentials
   - Verifies 403 Forbidden response for write operations

5. **`testSparqlUpdateWithInsufficientCredentials()`**
   - Tests that SPARQL UPDATE is rejected with insufficient credentials
   - Attempts to execute update with invalid authentication
   - Verifies 401 Unauthorized response

### Rate Limiting Tests
6. **`testRateLimitingOnSparqlQueries()`**
   - Tests rate limiting mechanism for SPARQL queries
   - Executes multiple queries concurrently to test throttling
   - Verifies query count is within rate limits

7. **`testRateLimitingRejectsExcessiveConnections()`**
   - Tests rejection of excessive concurrent connections
   - Sends many simultaneous requests to test abuse prevention
   - Verifies some requests are rejected under heavy load

### Multi-Tenant Isolation Tests
8. **`testMultiTenantSparqlIsolation()`**
   - Tests multi-tenant data isolation
   - Executes tenant-specific queries to verify data separation
   - Verifies results contain only appropriate tenant data

9. **`testCrossTenantAccessIsPrevented()`**
   - Tests prevention of cross-tenant access
   - Attempts to access other tenant's data
   - Verifies access is denied or returns empty results

## Implementation Details

### Self-Skip Pattern
- Tests gracefully skip when SPARQL engine is unavailable
- Uses `skipIfUnavailable()` helper method
- Follows existing test patterns in the codebase

### Security Testing Approach
- Uses direct HTTP calls for authentication testing
- Tests both successful and failed authentication scenarios
- Validates HTTP status codes and response bodies
- Implements concurrent testing for rate limiting

### Test Data
- Uses tenant identifiers: "tenant-a" and "tenant-b"
- Includes test credentials: admin/test123 (valid), admin/wrongpass (invalid)
- Implements helper methods for query construction

## Dependencies

- **QLever Instance**: Required for full test execution
  - URL: `http://localhost:7001`
  - Authentication must be enabled
- **Maven Test Runner**: For test execution

## Usage

### Running Individual Tests
```bash
# Run security tests only
mvn -pl yawl-integration -Dtest=SparqlEngineSecurityIntegrationTest test

# Run specific test method
mvn -pl yawl-integration -Dtest=SparqlEngineSecurityIntegrationTest#testSparqlQueryWithValidCredentials test
```

### Running with Docker
```bash
# Start QLever with authentication
docker run -p 7001:7001 qlever/yawl-qlever

# Then run the tests
mvn -pl yawl-integration -Dtest=SparqlEngineSecurityIntegrationTest test
```

## Integration with CI/CD

Tests are tagged with `@Tag("integration")` and will:
- Skip gracefully when QLever is not available
- Provide useful output for debugging
- Only run when dependencies are properly configured

## Security Considerations

1. **Authentication Testing**
   - Tests multiple authentication failure scenarios
   - Validates proper error responses
   - Tests missing credentials handling

2. **Authorization Testing**
   - Tests write permission requirements
   - Validates access control enforcement
   - Tests insufficient credential handling

3. **Rate Limiting**
   - Tests query throttling
   - Validates abuse prevention
   - Tests concurrent request handling

4. **Multi-Tenant Isolation**
   - Tests data separation between tenants
   - Validates cross-tenant access prevention
   - Ensures tenant data privacy

## Test Coverage

- 100% of test methods self-skip when dependencies unavailable
- Comprehensive coverage of authentication scenarios
- Thorough testing of authorization mechanisms
- Rate limiting and abuse prevention validation
- Multi-tenant isolation verification

## Future Enhancements

1. **OAuth2 Integration Testing**
   - Test OAuth2 authentication flows
   - Validate token-based access control
   - Test token refresh mechanisms

2. **Advanced Rate Limiting**
   - Test more sophisticated rate limiting algorithms
   - Implement adaptive rate limiting tests
   - Test burst request handling

3. **Security Headers Testing**
   - Test security headers (CORS, CSP, etc.)
   - Validate HTTPS enforcement
   - Test content security policies

4. **Vulnerability Testing**
   - Test for SQL injection prevention
   - Test for XSS prevention
   - Test for CSRF protection