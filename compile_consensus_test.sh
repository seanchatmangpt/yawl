#!/bin/bash
set -eu

# Classpath includes source directories and dependencies

# Compile consensus test files using Java 25
export JAVA_HOME=/Users/sac/java/jdk-25.0.2/Contents/Home

# Classpath includes source directories and dependencies
CP="src/main/java:test/*
    /Users/sac/yawl/yawl-integration/target/classes
    /Users/sac/yawl/yawl-engine/target/classes
    /Users/sac/yawl/yawl-elements/target/classes
    /Users/sac/yawl/yawl-stateless/target/classes
    /Users/sac/yawl/yawl-utilities/target/classes"

# Convert to actual paths
CP=$(echo $CP | tr '\n' ' ')

# Create directories for output
mkdir -p target/test-classes

# Compile
echo "Compiling consensus test files..."
javac -cp "$CP" -d target/test-classes -sourcepath src/main/java:src/test/java \
    src/test/java/org/yawlfoundation/yawl/consensus/ConsensusEngineTest.java \
    src/org/yawlfoundation/yawl/consensus/RaftConsensusWithPersistence.java || {
        echo "Compilation failed"
        exit 1
    }

echo "Compilation successful!"