#!/bin/bash

# Script to fix CallToolResult constructor calls to use builder pattern

file="src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java"

# Pattern 1: 4-argument constructor -> Builder
sed -i '' 's/new McpSchema\.CallToolResult(\s*List\.of(\s*new McpSchema\.TextContent([^)]+)\s*),\s*([^,]+),\s*null,\s*Map\.of())/McpSchema.CallToolResult.builder()\
    .content(List.of(McpSchema.TextContent.builder()\
        .type("text")\
        .text\1\
        .build()))\
    .isError(\2)\
    .build()/g' "$file"

# Pattern 2: 2-argument constructor -> Builder (string first, boolean second)
sed -i '' 's/new McpSchema\.CallToolResult(\s*"([^"]+)",\s*([^,]+))/McpSchema.CallToolResult.builder()\
    .content(List.of(McpSchema.TextContent.builder()\
        .type("text")\
        .text\1\
        .build()))\
    .isError(\2)\
    .build()/g' "$file"

# Pattern 3: 2-argument constructor -> Builder (string first, boolean second)
sed -i '' 's/new McpSchema\.CallToolResult(\s*([^)]+),\s*([^,]+))/McpSchema.CallToolResult.builder()\
    .content(List.of(McpSchema.TextContent.builder()\
        .type("text")\
        .text\1\
        .build()))\
    .isError(\2)\
    .build()/g' "$file"

echo "Fixed CallToolResult constructor calls in $file"