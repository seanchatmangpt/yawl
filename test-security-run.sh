#!/bin/bash

# Test runner for SPARQL engine security tests
# IMPORTANT: QLever is an embedded Java/C++ FFI bridge, NOT Docker/HTTP
# Security tests use QLeverEmbeddedSparqlEngine directly

echo "=== Running SparqlEngineSecurityIntegrationTest ==="
echo ""

# QLever is embedded - no Docker/HTTP check needed
echo "QLever: Using embedded FFI engine (in-process)"
echo ""

# Run the security test with Maven
# Tests will use QLeverEmbeddedSparqlEngine directly
if mvn -pl yawl-qlever -Dtest=SparqlEngineSecurityIntegrationTest test 2>&1; then
    echo ""
    echo "✓ Security test completed successfully"
else
    echo ""
    echo "✗ Security test failed"
    echo ""
    echo "NOTE: QLever is an embedded FFI engine, not a Docker HTTP service."
    echo "If tests fail due to missing native library:"
    echo "  1. Build native library: cd yawl-qlever && mvn compile"
    echo "  2. Ensure qlever_java.dylib/dll is in java.library.path"
    exit 1
fi

echo ""
echo "=== Test Methods ==="
echo "The test file contains these test methods:"
echo "- testSparqlQueryWithValidCredentials()"
echo "- testSparqlQueryWithInvalidCredentials()"
echo "- testSparqlQueryWithMissingCredentials()"
echo "- testSparqlUpdateRequiresWritePermission()"
echo "- testRateLimitingOnSparqlQueries()"
echo "- testMultiTenantSparqlIsolation()"
