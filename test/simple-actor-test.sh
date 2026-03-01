#!/bin/bash

echo "🧪 Running Simple Actor Pattern Validation Tests"
echo "=================================================="

# Test 1: Clean code should pass
echo ""
echo "Test 1: Clean Actor Code (should pass)"
mkdir -p /tmp/clean-test
cp /Users/sac/yawl/yawl-ggen/src/test/resources/fixtures/actor/clean-actor-code.java /tmp/clean-test/

# Run validator on clean code
java -cp "/Users/sac/yawl/yawl-ggen/target/classes:/Users/sac/yawl/yawl-ggen/target/test-classes:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.12.0/junit-jupiter-api-5.12.0.jar:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-engine/5.12.0/junit-jupiter-engine-5.12.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-core/4.8.0/jena-core-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-arq/4.8.0/jena-arq-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-shacl/4.8.0/jena-shacl-4.8.0.jar:/Users/sac/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar:/Users/sac/.m2/repository/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar" \
  org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
  /tmp/clean-test 2>/dev/null | jq '.status, .summary'

# Test 2: Code with TODO should fail
echo ""
echo "Test 2: Code with TODO (should fail)"
mkdir -p /tmp/todo-test
cat > /tmp/todo-test/WithTodo.java << 'EOF'
public class WithTodo {
    // TODO: implement this method
    public void doWork() {
        // This should trigger H_TODO
    }
}
EOF

# Run validator on code with TODO
java -cp "/Users/sac/yawl/yawl-ggen/target/classes:/Users/sac/yawl/yawl-ggen/target/test-classes:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.12.0/junit-jupiter-api-5.12.0.jar:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-engine/5.12.0/junit-jupiter-engine-5.12.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-core/4.8.0/jena-core-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-arq/4.8.0/jena-arq-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-shacl/4.8.0/jena-shacl-4.8.0.jar:/Users/sac/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar:/Users/sac/.m2/repository/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar" \
  org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
  /tmp/todo-test 2>/dev/null | jq '.status, .summary'

# Test 3: Performance measurement
echo ""
echo "Test 3: Performance Measurement"
echo "--------------------------------"

# Measure time to validate clean code
echo -n "Time to validate clean code: "
time java -cp "/Users/sac/yawl/yawl-ggen/target/classes:/Users/sac/yawl/yawl-ggen/target/test-classes:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.12.0/junit-jupiter-api-5.12.0.jar:/Users/sac/.m2/repository/org/junit/jupiter/junit-jupiter-engine/5.12.0/junit-jupiter-engine-5.12.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-core/4.8.0/jena-core-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-arq/4.8.0/jena-arq-4.8.0.jar:/Users/sac/.m2/repository/org/apache/jena/jena-shacl/4.8.0/jena-shacl-4.8.0.jar:/Users/sac/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar:/Users/sac/.m2/repository/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar" \
  org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
  /tmp/clean-test >/dev/null 2>&1

echo ""
echo "✅ Simple actor validation tests completed"