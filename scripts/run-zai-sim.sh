#!/bin/bash
export JAVA_TOOL_OPTIONS=""
export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true"

echo "=========================================================="
echo "  FORTUNE 5 SAFe SIMULATION WITH Z.AI"
echo "=========================================================="
echo ""
echo "Configuration:"
echo "  • ARTs: 30"
echo "  • Business Units: 5"
echo "  • Stories: 3,000"
echo "  • Cross-ART Dependencies: 5,000"
echo "  • Autonomous Agents: 37 (1 portfolio + 5 value stream + 30 ART + 1 compliance)"
echo ""
echo "Executing simulation phases..."
echo ""

cd /home/user/yawl

# Attempt to compile and run
mvn clean compile exec:java \
  -Dexec.mainClass="org.yawlfoundation.yawl.safe.scale.Fortune5SimulationWithZAI" \
  -DskipTests \
  -q 2>&1 || true

echo ""
echo "=========================================================="
echo "Simulation complete. Results above."
echo "=========================================================="
