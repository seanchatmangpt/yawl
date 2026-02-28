#!/bin/bash
# Generate A/B/E/X interface types facts

source "$(dirname "$0")/common.sh"

# Find interface files
INTERFACE_FILES=()
A_INTERFACES=()
B_INTERFACES=()
E_INTERFACES=()
X_INTERFACES=()

while IFS= read -r -d '' file; do
    INTERFACE_FILES+=("$file")
    BASENAME=$(basename "$file" | tr '[:lower:]' '[:upper:]')

    # Classify interfaces by naming pattern
    if [[ "$BASENAME" == *"A"* ]] && [[ "$BASENAME" == *"INTERFACE"* ]]; then
        A_INTERFACES+=("$file")
    elif [[ "$BASENAME" == *"B"* ]] && [[ "$BASENAME" == *"INTERFACE"* ]]; then
        B_INTERFACES+=("$file")
    elif [[ "$BASENAME" == *"E"* ]] && [[ "$BASENAME" == *"INTERFACE"* ]]; then
        E_INTERFACES+=("$file")
    elif [[ "$BASENAME" == *"X"* ]] && [[ "$BASENAME" == *"INTERFACE"* ]]; then
        X_INTERFACES+=("$file")
    fi
done < <(find . -name "*Interface*.java" -type f -print0)

# Analyze interfaces
INTERFACE_ANALYSIS=()
for file in "${INTERFACE_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract interface name
        INTERFACE_NAME=$(grep -o "interface.*" "$file" | head -1 | sed 's/interface *//' | cut -d' ' -f1)

        # Get method count
        METHOD_COUNT=$(grep -c "public.*(" "$file" 2>/dev/null || echo 0)

        # Get method signatures
        METHODS=$(grep -o "public.*;" "$file" | while read -r method; do
            echo "{\"signature\": \"$method\"}"
        done)

        # Get parent interfaces
        PARENTS=$(grep -o "extends.*" "$file" | sed 's/extends *//' | tr ',' '\n' | sed 's/ //g' | jq -R 'split("\n") | map(select(. != "")) | .[]')

        # Determine interface type
        TYPE="UNKNOWN"
        BASENAME=$(basename "$file" | tr '[:lower:]' '[:upper:]')
        if [[ "$BASENAME" == *"A"* ]]; then
            TYPE="INTERFACE_A"
        elif [[ "$BASENAME" == *"B"* ]]; then
            TYPE="INTERFACE_B"
        elif [[ "$BASENAME" == *"E"* ]]; then
            TYPE="INTERFACE_E"
        elif [[ "$BASENAME" == *"X"* ]]; then
            TYPE="INTERFACE_X"
        fi

        INTERFACE_DATA=$(jq -n --arg file "$file" \
            --arg name "$INTERFACE_NAME" \
            --argjson methods "$METHOD_COUNT" \
            --argjson signatures "$METHODS" \
            --argjson parents "$PARENTS" \
            --arg type "$TYPE" \
            '{
                file_path: $file,
                interface_name: $name,
                method_count: $methods,
                method_signatures: $signatures,
                parent_interfaces: $parents,
                interface_type: $type
            }')

        INTERFACE_ANALYSIS+=("$INTERFACE_DATA")
    fi
done

# Build interfaces data
INTERFACES_DATA=$(jq -n --argjson total_interfaces "${#INTERFACE_FILES[@]}" \
    --argjson interfaces_a "${#A_INTERFACES[@]}" \
    --argjson interfaces_b "${#B_INTERFACES[@]}" \
    --argjson interfaces_e "${#E_INTERFACES[@]}" \
    --argjson interfaces_x "${#X_INTERFACES[@]}" \
    --argjson analysis "$(printf '%s\n' "${INTERFACE_ANALYSIS[@]}" | jq -s '.[]')" \
    '{
        total_interfaces: $total_interfaces,
        interface_type_a: $interfaces_a,
        interface_type_b: $interfaces_b,
        interface_type_e: $interfaces_e,
        interface_type_x: $interfaces_x,
        interface_analysis: $analysis,
        interface_distribution: {
            "A": $interfaces_a,
            "B": $interfaces_b,
            "E": $interfaces_e,
            "X": $interfaces_x
        },
        dominant_interface_type: (
            if ($interfaces_a > $interfaces_b and $interfaces_a > $interfaces_e and $interfaces_a > $interfaces_x) then "A"
            elif ($interfaces_b > $interfaces_e and $interfaces_b > $interfaces_x) then "B"
            elif ($interfaces_e > $interfaces_x) then "E"
            else "X"
            end
        )
    }')

FINAL_JSON=$(generate_base "generate-interfaces-facts.sh" "$INTERFACES_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/interfaces.json"
clean_fact "$FACTS_DIR/interfaces.json"

echo "Generated interfaces.json with ${#INTERFACE_FILES[@]} total interfaces"
exit 0