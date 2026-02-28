#!/bin/bash
# Generate Maven hazards facts (SNAPSHOT deps, version mismatches)

source "$(dirname "$0")/common.sh"

# Find all pom.xml files
POM_FILES=()
while IFS= read -r -d '' pom; do
    POM_FILES+=("$pom")
done < <(find . -name "pom.xml" -type f -print0)

# Initialize hazards arrays
SNAPSHOT_DEPS=()
VERSION_MISMATCHES=()
PLUGIN_VERSION_MISMATCHES=()

# Analyze each pom file
for pom in "${POM_FILES[@]}"; do
    POM_DIR=$(dirname "$pom")

    # Check for SNAPSHOT dependencies
    SNAPSHOTS=$(grep -E '<version>.*SNAPSHOT</version>' "$pom" | \
        sed 's/.*<artifactId>\([^<]*\)<\/artifactId>.*<version>.*SNAPSHOT<\/version>/\1/g' | \
        while read -r artifact; do
            if [ -n "$artifact" ]; then
                PARENT_ARTIFACT=$(grep -E '<artifactId>' "$pom" | head -1 | sed 's/.*<artifactId>\([^<]*\)<.*/\1/')
                echo "{\"dependency\": \"$artifact\", \"pom\": \"$pom\", \"parent\": \"$PARENT_ARTIFACT\"}"
            fi
        done)

    if [ -n "$SNAPSHOTS" ]; then
        SNAPSHOT_DEPS+=("$SNAPSHOTS")
    fi

    # Check version inconsistencies in dependencies
    DEPS=$(grep -E '<groupId>.*</groupId>\s*<artifactId>.*</artifactId>\s*<version>' "$pom" | \
        sed 's/.*<groupId>\([^<]*\)<\/groupId>\s*<artifactId>\([^<]*\)<\/artifactId>\s*<version>\([^<]*\)<.*/{"group":"\1","artifact":"\2","version":"\3","pom":"'$pom'"}' | \
        jq -R 'fromjson? // .' | jq -s '.[]')

    if [ -n "$DEPS" ]; then
        # Find version mismatches
        MISMATCHES=$(echo "$DEPS" | jq -r '.[] | select(.artifact | endswith("common") or endswith("util") or endswith("core")) | select(.pom != "'"$pom"'") | .artifact + " has version " + .version' 2>/dev/null || echo "")

        if [ -n "$MISMATCHES" ]; then
            VERSION_MISMATCHES+=("$MISMATCHES")
        fi
    fi

    # Check plugin version consistency
    PLUGINS=$(grep -E '<plugin>' "$pom" -A 3 | \
        grep -E '(groupId|artifactId|version)' | \
        sed 's/.*<\(groupId\|artifactId\|version\)>\([^<]*\)<.*/{"type":"\1","value":"\2"}' | \
        jq -R 'fromjson? // .' | jq -s '.[]' | jq -R 'select(.type == "artifactId")' | \
        jq -R 'gsub("  "; "")' | sed 's/^"//' | sed 's/"$//' | while read plugin; do
            if [ -n "$plugin" ]; then
                # Find same plugin in other poms with different versions
                grep -r "<artifactId>$plugin</artifactId>" "${POM_FILES[@]}" | \
                    grep -v "$pom" | \
                    grep -v '<version>.*</version>' | \
                    grep -A 2 '<artifactId>' | \
                    grep '<version>' | \
                    while read -r line; do
                        VERSION=$(echo "$line" | sed 's/.*<version>\([^<]*\)<.*/\1/')
                        OTHER_POM=$(echo "$line" | sed 's/.*<artifactId>\([^<]*\)<\/artifactId>.*<artifactId>/\1/' | sed 's/\(<artifactId>\)/\n\1/' | grep '<artifactId>' | head -1 | sed 's/.*<artifactId>\([^<]*\)<.*/\1/')
                        echo "{\"plugin\": \"$plugin\", \"version\": \"$VERSION\", \"other_pom\": \"$OTHER_POM\", \"this_pom\": \"$pom\"}"
                    done
            fi
        done)

    if [ -n "$PLUGINS" ]; then
        PLUGIN_VERSION_MISMATCHES+=("$PLUGINS")
    fi
done

# Build hazards data
HAZARDS_DATA=$(jq -n --argjson snapshots "$(printf '%s\n' "${SNAPSHOT_DEPS[@]}" | jq -R 'fromjson? // .')" \
    --argjson mismatches "$(printf '%s\n' "${VERSION_MISMATCHES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson plugin_mismatches "$(printf '%s\n' "${PLUGIN_VERSION_MISMATCHES[@]}" | jq -R 'fromjson? // .')" \
    --argjson total_poms "${#POM_FILES[@]}" \
    '{
        pom_files_count: $total_poms,
        snapshot_dependencies: $snapshots,
        version_mismatches: $mismatches,
        plugin_version_mismatches: $plugin_mismatches,
        snapshot_count: ($snapshots | length),
        total_hazards: ($snapshots | length) + ($mismatches | length) + ($plugin_mismatches | length)
    }')

FINAL_JSON=$(generate_base "generate-maven-hazards-facts.sh" "$HAZARDS_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/maven-hazards.json"
clean_fact "$FACTS_DIR/maven-hazards.json"

echo "Generated maven-hazards.json with ${#SNAPSHOT_DEPS[@]} snapshot dependencies and ${#VERSION_MISMATCHES[@]} version mismatches"
exit 0