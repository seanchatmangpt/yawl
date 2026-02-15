#!/bin/bash
# Demonstration of YAWL MCP Prompt Provider
# Shows all 7 prompt types with example outputs

echo "========================================="
echo "YAWL MCP Prompt Provider Demonstration"
echo "========================================="
echo ""
echo "This demonstrates the 7 context-aware prompts for AI workflow assistance:"
echo ""
echo "1. workflow-design      - Help design YAWL specifications"
echo "2. case-debugging       - Debug running workflow cases"
echo "3. data-mapping         - Map data between tasks"
echo "4. exception-handling   - Handle workflow exceptions"
echo "5. resource-allocation  - Optimize resource allocation"
echo "6. process-optimization - Improve workflow efficiency"
echo "7. task-completion      - Guide task completion"
echo ""
echo "All prompts integrate with real YAWL Engine state."
echo "No mock data. No placeholders. Production-ready."
echo ""
echo "========================================="
echo ""

# Compile if needed
if [ ! -f "classes/org/yawlfoundation/yawl/integration/mcp/YawlMcpPromptProvider.class" ]; then
    echo "Compiling YawlMcpPromptProvider..."
    javac -d classes -cp "classes:build/3rdParty/lib/*" -sourcepath src \
        src/org/yawlfoundation/yawl/integration/mcp/YawlMcpPromptProvider.java
    echo ""
fi

# Run the demonstration
echo "Running prompt provider test..."
echo ""
java -cp "classes:build/3rdParty/lib/*" \
    org.yawlfoundation.yawl.integration.mcp.YawlMcpPromptProvider

echo ""
echo "========================================="
echo "For MCP server integration, see:"
echo "  src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java"
echo "========================================="
