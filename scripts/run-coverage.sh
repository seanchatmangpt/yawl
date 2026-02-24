#!/bin/bash
# Coverage runner script for YAWL modules
JACOCO_AGENT=/root/.m2/repository/org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar
MODULE="${1}"
DESTFILE="/home/user/yawl/${MODULE}/target/jacoco.exec"

echo "Running coverage for ${MODULE}"
echo "JaCoCo agent: ${JACOCO_AGENT}"
echo "Destfile: ${DESTFILE}"

export JAVA_TOOL_OPTIONS="-javaagent:${JACOCO_AGENT}=destfile=${DESTFILE}"
echo "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}"

mvn -pl "${MODULE}" -am clean verify \
  -P coverage,java25 \
  -Djacoco.skip=true \
  -Dmaven.test.failure.ignore=true
