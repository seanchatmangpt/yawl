#!/bin/bash

# Create a simple Java compilation and execution setup for WCP tests
cd /Users/sac/cre/vendors/yawl

echo "Setting up isolated test environment..."

# Create temporary directory for test compilation
mkdir -p wcp-test-temp/classes
cd wcp-test-temp

# Copy only the test file we need
cp ../yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/wcp/WcpBusinessPatterns37to43Test.java .

# Copy the verifier class (needed by tests)
cp ../yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/mcp/a2a/example/WorkflowSoundnessVerifier.java .

# Create a simple manifest
cat > Manifest.txt << EOF
Manifest-Version: 1.0
Main-Class: org.yawlfoundation.yawl.mcp.a2a.wcp.WcpBusinessPatterns37to43Test
Class-Path: .
EOF

# Try to compile using javac directly (if Maven fails)
echo "Attempting direct compilation with javac..."

# Create a simplified classpath
find ../yawl-mcp-a2a-app/src/main/java -name "*.java" | head -5 > sources.txt
find ../yawl-mcp-a2a-app/src/test/java -name "*.java" | grep -E "(WcpBusinessPatterns37to43Test|WorkflowSoundnessVerifier)" >> sources.txt

if javac -cp ".:../yawl-mcp-a2a-app/target/classes" WcpBusinessPatterns37to43Test.java WorkflowSoundnessVerifier.java 2>/dev/null; then
    echo "Compilation successful with javac"
    echo "Tests compiled but execution requires JUnit framework"
else
    echo "Compilation failed. The test suite requires full Maven compilation."
fi

# Clean up
cd ..
rm -rf wcp-test-temp