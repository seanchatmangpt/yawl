#!/usr/bin/env python3

import re

# Read the file
with open('src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java', 'r') as f:
    content = f.read()

# Pattern to match old CallToolResult constructor calls
old_pattern = r'return new McpSchema\.CallToolResult\(\s*"([^"]*)",\s*(true|false)\s*\);'

# Replacement function
def replacement(match):
    text = match.group(1)
    is_error = match.group(2)

    # Create the new builder pattern
    new_code = f'''return McpSchema.CallToolResult.builder()
    .addContent(McpSchema.TextContent.builder()
        .type("text")
        .text({repr(text)})
        .build())
    . isError({is_error})
    .build();'''

    return new_code

# Replace all occurrences
new_content = re.sub(old_pattern, replacement, content)

# Write back
with open('src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java', 'w') as f:
    f.write(new_content)

print(f"Replaced {len(re.findall(old_pattern, content))} CallToolResult constructor calls")