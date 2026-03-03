#!/bin/bash

# Set environment variables
export GROQ_API_KEY="$GROQ_API_KEY"
export GROQ_MODEL="openai/gpt-oss-20b"
export GROQ_MAX_CONCURRENCY="30"

# Try to force Maven to compile the test
echo "Force compiling test sources..."
mvn clean compile test-compile -pl yawl-ggen -DskipTests=false

# Check if test compilation succeeded
if [ -f "target/test-classes/org/yawlfoundation/yawl/ggen/rl/GroqLlmGatewayIntegrationTest.class" ]; then
    echo "Test compilation successful. Running test..."
    # Run the test with java directly
    export JAVA_HOME=/Users/sac/java/jdk-25.0.2/Contents/Home

    # Create a simple test runner
    java -cp "target/classes:target/test-classes:/Users/sac/.m2/repository/org/apache/jena/jena-core/4.8.0/jena-core-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-base/4.8.0/jena-base-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-shaded-guava/4.8.0/jena-shaded-guava-4.8.0.jar:/Users/sac/.m2/repository/org/apache/commons/commons-csv/1.10.0/commons-csv-1.10.0.jar:/Users/sac/.m2/repository/commons-codec/commons-codec/1.18.0/commons-codec-1.18.0.jar:/Users/sac/.m2/repository/org/apache/commons/commons-compress/1.23.0/commons-compress-1.23.0.jar:/Users/sac/.m2/repository/com/github/andrewoma/dexx/collection/0.7/collection-0.7.jar:/Users/sac/.m2/repository/org/apache/jena/jena-iri/4.8.0/jena-iri-4.8.0.jar:/Users/sac/.m2/repository/commons-cli/commons-cli/1.5.0/commons-cli/1.5.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-arq/4.8.0/jena-arq-4.8.0.jar:/Users/sac/.m2/repository/com/github/jsonld-java/jsonld-java/0.13.4/jsonld-java-0.13.4.jar:/Users/sac/.m2/repository/org/apache/httpcomponents/httpclient-cache/4.5.14/httpclient-cache/4.5.14.jar:/Users/sac/.m2/repository/org/apache/httpcomponents/httpclient/4.5.14/httpclient/4.5.14.jar:/Users/sac/.m2/repository/org/apache/httpcomponents/httpcore/4.4.16/httpcore-4.4.16.jar:/Users/sac/.m2/repository/org/slf4j/jcl-over-slf4j/2.0.17/jcl-over-slf4j/2.0.17.jar:/Users/sac/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.19.4/jackson-core-2.19.4.jar:/Users/sac/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.19.4/jackson-databind/2.19.4.jar:/Users/sac/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.19.4/jackson-annotations-2.19.4.jar:/Users/sac/.m2/repository/com/apicatalog/titanium-json-ld/1.3.2/titanium-json-ld-1.3.2.jar:/Users/sac/.m2/repository/org/glassfish/jakarta.json/2.0.1/jakarta.json/2.0.1.jar:/Users/sac/.m2/repository/com/google/protobuf/protobuf-java/3.22.2/protobuf-java/3.22.2.jar:/Users/sac/.m2/repository/org/apache/thrift/libthrift/0.18.1/libthrift/0.18.1.jar:/Users/sac/.m2/repository/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-shacl/4.8.0/jena-shacl/4.8.0.jar:/Users/sac/.m2/repository/com/google/code/gson/gson/2.13.2/gson/2.13.2.jar:/Users/sac/.m2/repository/com/google/errorprone/error_prone_annotations/2.41.0/error_prone_annotations/2.41.0.jar:/Users/sac/.m2/repository/org/slf4j/slf4j-api/2.0.17/slf4j-api/2.0.17.jar:/Users/sac/.m2/repository/jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api/6.1.0.jar:/Users/sac/.m2/repository/org/yawlfoundation/yawl-graalpy/6.0.0-GA/yawl-graalpy-6.0.0-GA.jar:/Users/sac/.m2/repository/org/graalvm/polyglot/polyglot/24.1.2/polyglot/24.1.2.jar:/Users/sac/.m2/repository/org/graalvm/sdk/collections/24.1.2/collections/24.1.2.jar:/Users/sac/.m2/repository/org/graalvm/sdk/nativeimage/24.1.2/nativeimage/24.1.2.jar:/Users/sac/.m2/repository/org/graalvm/sdk/word/24.1.2/word/24.1.2.jar:/Users/sac/.m2/repository/org/apache/commons/commons-pool2/2.12.1/commons-pool2/2.12.1.jar:/Users/sac/.m2/repository/org/jspecify/jspecify/1.0.0/jspecify/1.0.0.jar:/Users/sac/.m2/repository/commons-io/commons-io/2.21.0/commons-io/2.21.0.jar:/Users/sac/.m2/repository/net/sf/jung/jung-api/2.1.1/jung-api/2.1.1.jar:/Users/sac/.m2/repository/com/google/guava/guava/19.0/guava/19.0.jar:/Users/sac/.m2/repository/net/sf/jung/jung-graph-impl/2.1.1/jung-graph-impl/2.1.1.jar:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.12.0/junit-jupiter-api-5.12.0.jar:/Users/sac/.m2/repository/org/opentest4j/opentest4j/1.3.0/opentest4j/1.3.0.jar:/Users/sac/.m2/repository/org/junit/platform/junit-platform-commons/1.12.2/junit-platform-commons/1.12.2.jar:/Users/sac/.m2/repository/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api/1.1.2.jar:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-engine/5.12.0/junit-jupiter-engine/5.12.0.jar:/Users/sac/.m2/repository/org/junit/platform/junit-platform-engine/1.12.2/junit-platform-engine/1.12.2.jar:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-params/5.12.0/junit-jupiter-params/5.12.0.jar" \
        org.junit.jupiter.engine.JupiterTestEngine \
        -p target/test-classes \
        --scan-classpath \
        --include-classname=".*GroqLlmGatewayIntegrationTest.*"
else
    echo "Test compilation failed. Cannot run test."
    exit 1
fi