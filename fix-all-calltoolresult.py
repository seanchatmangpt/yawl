#!/usr/bin/env python3

import re

# Read the file
with open('src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java', 'r') as f:
    content = f.read()

# Pattern 1: Simple string constructor
pattern1 = r'return new McpSchema\.CallToolResult\(\s*"([^"]*)",\s*(true|false)\s*\);'
replacement1 = r'''return McpSchema.CallToolResult.builder()
    .addContent(McpSchema.TextContent.builder()
        .type("text")
        .text(r"\1")
        .build())
    . isError(\2)
    .build();'''

content = re.sub(pattern1, replacement1, content)

# Pattern 2: List.of(TextContent) constructor - this one is actually correct, but let's verify
pattern2 = r'return new McpSchema\.CallToolResult\(\s*List\.of\(new McpSchema\.TextContent\("([^"]*)"\)\),\s*(true|false),\s*null,\s*Map\.of\(\)\s*\);'
replacement2 = r'''return McpSchema.CallToolResult.builder()
    .addContent(McpSchema.TextContent.builder()
        .type("text")
        .text(r"\1")
        .build())
    . isError(\2)
    .build();'''

content = re.sub(pattern2, replacement2, content)

# Pattern 3: StringBuilder constructor
pattern3 = r'return new McpSchema\.CallToolResult\(sb\.toString\(\),\s*(true|false)\s*\);'
replacement3 = r'''return McpSchema.CallToolResult.builder()
    .addContent(McpSchema.TextContent.builder()
        .type("text")
        .text(sb.toString())
        .build())
    . isError(\1)
    .build();'''

content = re.sub(pattern3, replacement3, content)

# Write back
with open('src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java', 'w') as f:
    f.write(content)

print("Fixed all CallToolResult constructors")