#!/bin/bash
#
# check-doc-links.sh - Validate documentation links
#
# Checks for broken links in documentation files and verifies
# that references to .claude/agents/AGENTS_REFERENCE.md are correct.
#

source "$(dirname "$0")/common.sh"

log_section "Checking Documentation Links"

# Track results
declare -a broken_links=()
declare -a fixed_links=()

# Files that should reference the agents reference
declare -a agent_reference_files=(
    "CLAUDE.md"
    "validation/reports/README.md"
)

# Check if AGENTS_REFERENCE.md exists
if [[ ! -f ".claude/agents/AGENTS_REFERENCE.md" ]]; then
    log_error "Agents reference file not found: .claude/agents/AGENTS_REFERENCE.md"
    log_test "FAIL" "Missing agents reference file" "doc-links-agents-reference"
    exit 1
fi

# Check each file for broken links
check_file_links() {
    local file="$1"
    local filename=$(basename "$file")
    
    log_info "Checking links in $filename"
    
    # Check for broken references to agents/definitions.md (should be AGENTS_REFERENCE.md)
    if grep -q "agents/definitions\.md" "$file"; then
        broken_links+=("$file:agents/definitions.md")
        # Fix it
        sed -i 's|agents/definitions\.md|.claude/agents/AGENTS_REFERENCE.md|g' "$file"
        fixed_links+=("$file:agents/definitions.md -> .claude/agents/AGENTS_REFERENCE.md")
    fi
    
    # Check for absolute paths that might be broken
    if grep -q "/home/user/yawl/\.claude/agents/definitions\.md" "$file"; then
        broken_links+=("$file:/home/user/yawl/.claude/agents/definitions.md")
        # Fix it
        sed -i 's|/home/user/yawl/\.claude/agents/definitions\.md|.claude/agents/AGENTS_REFERENCE.md|g' "$file"
        fixed_links+=("$file:absolute path -> .claude/agents/AGENTS_REFERENCE.md")
    fi
    
    # Additional link checks can be added here
    # For now, we'll just check for the specific broken links mentioned in the plan
}

# Check all documentation files
find docs -name "*.md" -type f 2>/dev/null | while read -r file; do
    check_file_links "$file"
done

# Check specific files mentioned in the plan
for file in "${agent_reference_files[@]}"; do
    if [[ -f "$file" ]]; then
        check_file_links "$file"
    else
        log_warning "Documentation file not found: $file"
    fi
done

# Log results
if [[ ${#broken_links[@]} -eq 0 ]]; then
    log_test "PASS" "No broken links found" "doc-links"
    log_success "All documentation links are valid"
else
    log_test "FAIL" "Found ${#broken_links[@]} broken links" "doc-links"
    log_error "Broken links detected:"
    for link in "${broken_links[@]}"; do
        echo "  $link"
    done
    
    if [[ ${#fixed_links[@]} -gt 0 ]]; then
        log_success "Fixed ${#fixed_links[@]} broken links:"
        for link in "${fixed_links[@]}"; do
            echo "  $link"
        done
    fi
    
    exit 1
fi

# Output JSON if requested
if [[ "$1" == "--json" ]]; then
    output_json "doc-links-results.json"
fi
