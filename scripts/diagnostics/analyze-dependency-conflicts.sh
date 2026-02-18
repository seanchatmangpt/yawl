#!/bin/bash
#
# Analyze Maven dependency conflicts from dependency tree output
# Captures groupId:artifactId:version:scope and reports conflicts with scope info
#
# Usage:
#   ./analyze-dependency-conflicts.sh [dependency-tree-file]
#   mvn dependency:tree -Dverbose | ./analyze-dependency-conflicts.sh -
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Input file (default to stdin)
INPUT_FILE="${1:--}"

# Temporary file for processing
TEMP_FILE=$(mktemp)
trap "rm -f $TEMP_FILE" EXIT

# Read input into temp file
if [[ "$INPUT_FILE" == "-" ]]; then
    cat > "$TEMP_FILE"
else
    cp "$INPUT_FILE" "$TEMP_FILE"
fi

echo -e "${YELLOW}=== Maven Dependency Conflict Analysis ===${NC}"
echo ""

# Regex pattern explanation:
# Format: (groupId:artifactId:type:version:scope - omitted for conflict with version)
# Captures: group:artifact, type, current_version, current_scope, other_version
# The scope is captured from the current dependency (before "omitted for conflict")
# Note: Maven doesn't include scope for the conflicting version in "omitted for conflict" messages
#
# Example input: |  \- (jakarta.transaction:jakarta.transaction-api:jar:1.3.3:compile - omitted for conflict with 2.0.1)
# Pattern breakdown:
#   \(([a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+)   - Opening paren and capture groupId:artifactId
#   :([a-zA-Z0-9.-]+)                      - Type (jar, war, pom, etc.)
#   :([0-9a-zA-Z.-]+)                      - Version
#   :([a-zA-Z]+)                           - Scope (compile, test, runtime, provided, etc.)
#   [^)]*omitted for conflict with          - Text between scope and conflict marker
#   ([0-9a-zA-Z.-]+)\)                     - Conflicting version and closing paren

CONFLICT_PATTERN='\(([a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+):([a-zA-Z0-9.-]+):([0-9a-zA-Z._-]+):([a-zA-Z]+)[^)]*omitted for conflict with ([0-9a-zA-Z._-]+)\)'

# Extract and parse conflicts
echo -e "${YELLOW}Detected Conflicts:${NC}"
echo ""

# Temporary file for conflict output
CONFLICT_OUTPUT=$(mktemp)
trap "rm -f $TEMP_FILE $CONFLICT_OUTPUT" EXIT

# Process conflicts - capture group:artifact:type:version:scope and conflicting version
# The pattern captures:
#   \1 = groupId:artifactId
#   \2 = type (jar, war, pom, etc.)
#   \3 = current_version
#   \4 = current_scope
#   \5 = conflicting_version
while IFS= read -r line; do
    if [[ "$line" =~ $CONFLICT_PATTERN ]]; then
        GROUP_ARTIFACT="${BASH_REMATCH[1]}"
        DEP_TYPE="${BASH_REMATCH[2]}"
        CURRENT_VERSION="${BASH_REMATCH[3]}"
        CURRENT_SCOPE="${BASH_REMATCH[4]}"
        CONFLICTING_VERSION="${BASH_REMATCH[5]}"

        # Output in format: group:artifact:type:version:scope conflicts with version:scope
        # Note: Maven's "omitted for conflict with" only reports version, not scope
        # We indicate this by showing the known scope vs unknown scope
        echo -e "  ${RED}conflict:${NC} ${GROUP_ARTIFACT}:${DEP_TYPE}:${CURRENT_VERSION}:${CURRENT_SCOPE} conflicts with ${CONFLICTING_VERSION}:<scope-unknown>"
    fi
done < "$TEMP_FILE" | sort -u > "$CONFLICT_OUTPUT"

# Display conflicts and count
cat "$CONFLICT_OUTPUT"
CONFLICT_COUNT=$(wc -l < "$CONFLICT_OUTPUT" | tr -d ' ')

echo ""
echo -e "${YELLOW}Summary:${NC}"
echo "  Total unique conflicts: $CONFLICT_COUNT"
echo ""

# Additional analysis: Find duplicates with different scopes
echo -e "${YELLOW}Dependencies with Multiple Scope Declarations:${NC}"
echo ""

# Pattern for any dependency declaration (not just conflicts)
# Captures: groupId:artifactId:version:scope
DEP_PATTERN='([a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+):[a-zA-Z0-9.-]+:([0-9a-zA-Z.-]+):([a-zA-Z]+)'

# Extract all dependencies with scope
grep -oE '[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[a-zA-Z0-9.-]+:[0-9a-zA-Z.-]+:[a-zA-Z]+' "$TEMP_FILE" 2>/dev/null | \
    sort | uniq -c | sort -rn | \
    while read -r count dep; do
        if [[ $count -gt 1 ]]; then
            echo -e "  ${YELLOW}$count occurrences:${NC} $dep"
        fi
    done

echo ""

# Group conflicts by artifact for easier review
echo -e "${YELLOW}Conflicts Grouped by Artifact:${NC}"
echo ""

# Use awk for grouping (compatible with older bash versions)
# Extract: groupId:artifactId, version:scope, conflicting_version
while IFS= read -r line; do
    if [[ "$line" =~ $CONFLICT_PATTERN ]]; then
        GROUP_ARTIFACT="${BASH_REMATCH[1]}"
        CURRENT_VERSION="${BASH_REMATCH[3]}"
        CURRENT_SCOPE="${BASH_REMATCH[4]}"
        CONFLICTING_VERSION="${BASH_REMATCH[5]}"
        echo "${GROUP_ARTIFACT}|${CURRENT_VERSION}:${CURRENT_SCOPE}|${CONFLICTING_VERSION}"
    fi
done < "$TEMP_FILE" | sort -u | awk -F'|' '
{
    artifact = $1
    version_scope = $2
    conflicting = $3
    value = version_scope " vs " conflicting

    if (artifact in conflicts) {
        if (index(conflicts[artifact], value) == 0) {
            conflicts[artifact] = conflicts[artifact] ", " value
        }
    } else {
        conflicts[artifact] = value
        artifacts[++n] = artifact
    }
}
END {
    for (i = 1; i <= n; i++) {
        printf "  \033[0;32m%s\033[0m\n", artifacts[i]
        printf "    %s\n\n", conflicts[artifacts[i]]
    }
}'

echo -e "${GREEN}Analysis complete.${NC}"
