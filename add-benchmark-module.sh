#!/bin/bash

# Add yawl-benchmark module to parent POM
sed -i.bak '/<module>yawl-mcp-a2a-app<\/module>/a\
        <module>yawl-benchmark</module>' pom.xml

echo "Added yawl-benchmark module to pom.xml"
