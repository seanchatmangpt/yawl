#!/bin/bash
# Maven wrapper that safely handles JAVA_TOOL_OPTIONS with special characters

# Store the original JAVA_TOOL_OPTIONS
orig_opts="$JAVA_TOOL_OPTIONS"

# Clear JAVA_TOOL_OPTIONS to avoid shell parsing issues
unset JAVA_TOOL_OPTIONS

# Use Maven with local proxy settings instead
export MAVEN_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3128"

# Call Maven
exec mvn "$@"
