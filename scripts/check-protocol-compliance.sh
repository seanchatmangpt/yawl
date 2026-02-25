#!/usr/bin/env bash
# ==========================================================================
# check-protocol-compliance.sh — Protocol Version Compliance Checker
#
# Checks MCP and A2A SDK version compliance against Maven Central and
# local Maven repository. Reports status, feature support, and deprecated
# feature detection.
#
# Usage:
#   bash scripts/check-protocol-compliance.sh           # Human-readable output
#   bash scripts/check-protocol-compliance.sh --json    # JSON output for CI/CD
#   bash scripts/check-protocol-compliance.sh --verbose # Detailed logging
#
# Exit Codes:
#   0 - All checks pass, no updates needed
#   1 - Issues found (outdated versions, deprecated features)
#
# Environment:
#   MAVEN_OPTS    - JVM options for Maven
#   CURL_TIMEOUT  - Timeout for HTTP requests (default: 30)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ───────────────────────────────────────────────────────────
CURL_TIMEOUT="${CURL_TIMEOUT:-30}"
MAVEN_CENTRAL_BASE="https://repo1.maven.org/maven2"
OUTPUT_FORMAT="text"
VERBOSE=0
ISSUES_FOUND=0

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ── Parse Arguments ─────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --json)     OUTPUT_FORMAT="json"; shift ;;
        --verbose|-v) VERBOSE=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown argument: $1"; echo "Use --help for usage."; exit 1 ;;
    esac
done

# ── Logging Functions ───────────────────────────────────────────────────────
log_verbose() {
    if [[ "$VERBOSE" -eq 1 ]]; then
        echo "[VERBOSE] $*" >&2
    fi
}

log_info() {
    if [[ "$OUTPUT_FORMAT" == "text" ]]; then
        echo -e "${CYAN}[INFO]${NC} $*"
    fi
}

log_success() {
    if [[ "$OUTPUT_FORMAT" == "text" ]]; then
        echo -e "${GREEN}[OK]${NC} $*"
    fi
}

log_warning() {
    if [[ "$OUTPUT_FORMAT" == "text" ]]; then
        echo -e "${YELLOW}[WARN]${NC} $*"
    fi
}

log_error() {
    if [[ "$OUTPUT_FORMAT" == "text" ]]; then
        echo -e "${RED}[ERROR]${NC} $*" >&2
    fi
}

log_section() {
    if [[ "$OUTPUT_FORMAT" == "text" ]]; then
        echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
    fi
}

# ── Version Extraction from POM ─────────────────────────────────────────────
extract_version_from_pom() {
    local pom_file="$1"
    local property_name="$2"

    if [[ ! -f "$pom_file" ]]; then
        log_error "POM file not found: $pom_file"
        echo ""
        return 0  # Return 0 to avoid set -e exit; empty string indicates failure
    fi

    # Extract version from <property.name>value</property.name>
    # Portable: use sed instead of grep -P (not available on macOS)
    local version
    version=$(sed -n "s/.*<${property_name}>\([^<]*\)<\/${property_name}>.*/\1/p" "$pom_file" 2>/dev/null | head -1)

    echo "${version:-}"
}

# ── Version Comparison (Semantic Versioning) ────────────────────────────────
# Returns: 0 if equal, 1 if v1 > v2, 2 if v1 < v2
compare_versions() {
    local v1="$1"
    local v2="$2"

    # Handle empty versions
    [[ -z "$v1" && -z "$v2" ]] && return 0
    [[ -z "$v1" ]] && return 2
    [[ -z "$v2" ]] && return 1

    # Normalize versions: strip non-numeric suffixes for comparison
    # Handle formats like: 0.17.2, 1.0.0.Alpha2, 2.0.0-rc1
    local v1_clean v2_clean
    v1_clean=$(echo "$v1" | sed 's/[^0-9.]//g' | sed 's/\.$//')
    v2_clean=$(echo "$v2" | sed 's/[^0-9.]//g' | sed 's/\.$//')

    # Split into arrays
    IFS='.' read -ra v1_parts <<< "$v1_clean"
    IFS='.' read -ra v2_parts <<< "$v2_clean"

    # Compare each part
    local max_len=${#v1_parts[@]}
    [[ ${#v2_parts[@]} -gt $max_len ]] && max_len=${#v2_parts[@]}

    for ((i=0; i<max_len; i++)); do
        local p1=${v1_parts[i]:-0}
        local p2=${v2_parts[i]:-0}

        # Remove leading zeros
        p1=$((10#$p1))
        p2=$((10#$p2))

        if [[ $p1 -gt $p2 ]]; then
            return 1
        elif [[ $p1 -lt $p2 ]]; then
            return 2
        fi
    done

    # Numeric parts equal; check for pre-release suffixes
    # Pre-release versions (Alpha, Beta, RC) are considered older than release
    local v1_prerelease v2_prerelease
    v1_prerelease=$(echo "$v1" | grep -oiE '(alpha|beta|rc|snapshot)[0-9]*' | head -1 || true)
    v2_prerelease=$(echo "$v2" | grep -oiE '(alpha|beta|rc|snapshot)[0-9]*' | head -1 || true)

    # If v1 has prerelease and v2 doesn't, v1 < v2
    if [[ -n "$v1_prerelease" && -z "$v2_prerelease" ]]; then
        return 2
    fi
    # If v2 has prerelease and v1 doesn't, v1 > v2
    if [[ -z "$v1_prerelease" && -n "$v2_prerelease" ]]; then
        return 1
    fi

    return 0
}

# ── Get Latest MCP Version from Maven Central ───────────────────────────────
get_latest_mcp_version() {
    local group_path="io/modelcontextprotocol/sdk"
    local artifact="mcp"
    local metadata_url="${MAVEN_CENTRAL_BASE}/${group_path}/${artifact}/maven-metadata.xml"

    log_verbose "Fetching MCP metadata from: $metadata_url"

    local metadata
    metadata=$(curl -sS --connect-timeout "$CURL_TIMEOUT" --max-time "$((CURL_TIMEOUT * 2))" "$metadata_url" 2>/dev/null || echo "")

    if [[ -z "$metadata" ]]; then
        log_warning "Could not fetch MCP metadata from Maven Central (offline?)"
        echo ""
        return 0  # Return 0 to avoid set -e exit; empty string indicates failure
    fi

    # Extract latest version from metadata
    # Portable: use sed instead of grep -P (not available on macOS)
    local latest
    latest=$(echo "$metadata" | sed -n 's/.*<latest>\([^<]*\)<\/latest>.*/\1/p' | head -1 || true)

    if [[ -z "$latest" ]]; then
        # Fallback: get last version in version list
        latest=$(echo "$metadata" | sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' | tail -1 || true)
    fi

    echo "${latest:-}"
}

# ── Get Local A2A Version from Maven Repository ─────────────────────────────
get_local_a2a_version() {
    local m2_path="${HOME}/.m2/repository/io/anthropic"

    log_verbose "Checking local Maven repo for A2A: $m2_path"

    if [[ ! -d "$m2_path" ]]; then
        log_verbose "A2A not found in local Maven repository"
        echo ""
        return 0  # Return 0 to avoid set -e exit; empty string indicates failure
    fi

    # Look for a2a-java-sdk-spec directory
    local spec_path="${m2_path}/a2a-java-sdk-spec"
    if [[ -d "$spec_path" ]]; then
        # Get the highest version directory (exclude maven-metadata files)
        local version
        version=$(ls -1 "$spec_path" 2>/dev/null | grep -v "maven-metadata" | grep -v "\.xml" | sort -V | tail -1 || true)
        if [[ -n "$version" ]]; then
            echo "$version"
            return 0
        fi
    fi

    # Fallback: check other A2A artifacts
    for artifact in a2a-java-sdk-common a2a-java-sdk-server-common; do
        local artifact_path="${m2_path}/${artifact}"
        if [[ -d "$artifact_path" ]]; then
            local version
            version=$(ls -1 "$artifact_path" 2>/dev/null | grep -v "maven-metadata" | grep -v "\.xml" | sort -V | tail -1 || true)
            if [[ -n "$version" ]]; then
                echo "$version"
                return 0
            fi
        fi
    done

    echo ""
}

# ── Check MCP Feature Support ───────────────────────────────────────────────
check_mcp_features() {
    local version="$1"
    local -a features=()
    local -a supported=()
    local -a unsupported=()

    # MCP SDK 0.17.2 features
    features=("tools" "resources" "prompts" "completions" "logging" "stdio_transport")

    for feature in "${features[@]}"; do
        case "$feature" in
            tools|resources|prompts|completions|logging)
                # Core features supported since early versions
                supported+=("$feature")
                ;;
            stdio_transport)
                # stdio transport supported in 0.17.x
                supported+=("$feature")
                ;;
            http_transport|websocket_transport)
                # HTTP/WebSocket transports added in later versions
                unsupported+=("$feature")
                ;;
            *)
                unsupported+=("$feature")
                ;;
        esac
    done

    echo "supported:${supported[*]:-}"
    echo "unsupported:${unsupported[*]:-}"
}

# ── Check A2A Feature Support ───────────────────────────────────────────────
check_a2a_features() {
    local version="$1"
    local -a features=()
    local -a supported=()
    local -a unsupported=()

    # A2A SDK 1.0.0.Alpha2 features
    features=("agent_card" "message_send" "task_management" "rest_transport" "streaming" "push_notifications")

    for feature in "${features[@]}"; do
        case "$feature" in
            agent_card|message_send|task_management|rest_transport)
                # Core A2A features supported in Alpha2
                supported+=("$feature")
                ;;
            streaming|push_notifications)
                # Not yet implemented in Alpha2
                unsupported+=("$feature")
                ;;
            *)
                unsupported+=("$feature")
                ;;
        esac
    done

    echo "supported:${supported[*]:-}"
    echo "unsupported:${unsupported[*]:-}"
}

# ── Check for Deprecated Features ───────────────────────────────────────────
check_deprecated_features() {
    local -a deprecations=()
    local integration_pom="${REPO_ROOT}/yawl-integration/pom.xml"

    # Check for excluded MCP bridge files
    local mcp_bridges=(
        "src/org/yawlfoundation/yawl/integration/mcp/sdk/McpServer.java"
        "src/org/yawlfoundation/yawl/integration/mcp/sdk/McpServerFeatures.java"
        "src/org/yawlfoundation/yawl/integration/mcp/sdk/McpSyncServer.java"
        "src/org/yawlfoundation/yawl/integration/mcp/sdk/McpSyncServerExchange.java"
        "src/org/yawlfoundation/yawl/integration/mcp/sdk/StdioServerTransportProvider.java"
    )

    local excluded_bridges=0
    for bridge in "${mcp_bridges[@]}"; do
        if [[ -f "${REPO_ROOT}/${bridge}" ]]; then
            # Check if excluded in pom.xml
            if grep -q "$(basename "$bridge" .java)" "$integration_pom" 2>/dev/null; then
                ((excluded_bridges++)) || true
            fi
        fi
    done

    if [[ $excluded_bridges -gt 0 ]]; then
        deprecations+=("mcp_bridge_exclusions|${excluded_bridges} files excluded (using official SDK)")
    fi

    # Check for HTTP transport usage issues (MCP SDK 0.17.2 has limited HTTP support)
    if grep -rq "HttpMcpTransport\|McpClient.*http\|WebMvcSseServerTransport" \
        "${REPO_ROOT}/src/org/yawlfoundation/yawl/integration/mcp/" 2>/dev/null; then
        deprecations+=("http_transport_limited|MCP SDK 0.17.2 has limited HTTP transport support")
    fi

    # Check for A2A source exclusions
    if grep -q "exclude>.*a2a" "$integration_pom" 2>/dev/null; then
        deprecations+=("a2a_sdk_excluded|A2A SDK sources excluded pending local Maven install")
    fi

    # Check for ZAI integration exclusions
    if grep -q "exclude>.*zai" "$integration_pom" 2>/dev/null; then
        deprecations+=("zai_excluded|ZAI integration sources excluded (requires A2A SDK)")
    fi

    # Use ||| as delimiter between items
    local result=""
    local first=1
    for item in "${deprecations[@]}"; do
        if [[ $first -eq 1 ]]; then
            result="${item}"
            first=0
        else
            result="${result}|||${item}"
        fi
    done
    echo "$result"
}

# ── Save Compliance Report ──────────────────────────────────────────────────
save_compliance_report() {
    local report_dir="${REPO_ROOT}/docs/v6/latest/compliance"
    local report_file="${report_dir}/protocol-compliance.json"

    mkdir -p "$report_dir" || return 0

    # Build JSON arrays using jq
    local mcp_supported_json mcp_unsupported_json
    local a2a_supported_json a2a_unsupported_json
    local deprecations_json

    # Helper function to create JSON array from space-separated string
    make_json_array() {
        local input="$1"
        if [[ -z "${input// /}" ]]; then
            echo "[]"
        else
            printf '%s' "$input" | tr ' ' '\n' | grep -v '^$' | jq -R . 2>/dev/null | jq -s . 2>/dev/null || echo "[]"
        fi
    }

    mcp_supported_json=$(make_json_array "${MCP_SUPPORTED_FEATURES:-}")
    mcp_unsupported_json=$(make_json_array "${MCP_UNSUPPORTED_FEATURES:-}")
    a2a_supported_json=$(make_json_array "${A2A_SUPPORTED_FEATURES:-}")
    a2a_unsupported_json=$(make_json_array "${A2A_UNSUPPORTED_FEATURES:-}")
    # Deprecations use ||| as item delimiter
    if [[ -z "${DEPRECATIONS:-}" ]]; then
        deprecations_json="[]"
    else
        deprecations_json=$(printf '%s' "${DEPRECATIONS:-}" | sed 's/|||/\n/g' | grep -v '^$' | jq -R . 2>/dev/null | jq -s . 2>/dev/null || echo "[]")
    fi

    local yawl_version
    # Extract project version (not a property, direct element)
    yawl_version=$(sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' "${REPO_ROOT}/pom.xml" 2>/dev/null | head -1 || echo "unknown")
    # If version contains ${, it's a property reference, try to get actual value
    if [[ "$yawl_version" == *'$'* ]]; then
        yawl_version="6.0.0-Beta"  # Fallback to known version
    fi

    local all_current="false"
    [[ "$ISSUES_FOUND" -eq 0 ]] && all_current="true"

    # Build recommendations array
    local recommendations="[]"
    if [[ "$MCP_STATUS" == "OUTDATED" ]]; then
        recommendations=$(echo "$recommendations" | jq --arg v "Update MCP SDK from ${MCP_CURRENT_VERSION} to ${MCP_LATEST_VERSION}" '. + [$v]' 2>/dev/null || echo "[]")
    fi
    if [[ "$A2A_STATUS" == "NOT_INSTALLED" ]]; then
        recommendations=$(echo "$recommendations" | jq --arg v "Install A2A SDK JARs to local Maven repository" '. + [$v]' 2>/dev/null || echo "[]")
    fi

    cat > "$report_file" << REPORT_EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "yawl_version": "${yawl_version}",
  "protocols": {
    "mcp": {
      "groupId": "io.modelcontextprotocol.sdk",
      "artifactId": "mcp",
      "current_version": "${MCP_CURRENT_VERSION:-unknown}",
      "latest_version": "${MCP_LATEST_VERSION:-unknown}",
      "status": "${MCP_STATUS:-unknown}",
      "features": {
        "supported": ${mcp_supported_json},
        "unsupported": ${mcp_unsupported_json}
      }
    },
    "a2a": {
      "groupId": "io.anthropic",
      "artifactId": "a2a-java-sdk-spec",
      "current_version": "${A2A_CURRENT_VERSION:-unknown}",
      "local_version": "${A2A_LOCAL_VERSION:-not_installed}",
      "status": "${A2A_STATUS:-unknown}",
      "on_maven_central": false,
      "features": {
        "supported": ${a2a_supported_json},
        "unsupported": ${a2a_unsupported_json}
      }
    }
  },
  "deprecated_features": ${deprecations_json},
  "issues_found": ${ISSUES_FOUND},
  "summary": {
    "all_current": ${all_current},
    "recommendations": ${recommendations}
  }
}
REPORT_EOF

    log_verbose "Report saved to: $report_file"
}

# ── Output JSON Result ──────────────────────────────────────────────────────
output_json() {
    cat << JSON_EOF
{
  "mcp": {
    "current": "${MCP_CURRENT_VERSION:-unknown}",
    "latest": "${MCP_LATEST_VERSION:-unknown}",
    "status": "${MCP_STATUS:-unknown}",
    "features": {
      "supported": "${MCP_SUPPORTED_FEATURES:-}",
      "unsupported": "${MCP_UNSUPPORTED_FEATURES:-}"
    }
  },
  "a2a": {
    "current": "${A2A_CURRENT_VERSION:-unknown}",
    "local": "${A2A_LOCAL_VERSION:-not_installed}",
    "status": "${A2A_STATUS:-unknown}",
    "features": {
      "supported": "${A2A_SUPPORTED_FEATURES:-}",
      "unsupported": "${A2A_UNSUPPORTED_FEATURES:-}"
    }
  },
  "deprecated_features": "${DEPRECATIONS:-}",
  "issues_found": ${ISSUES_FOUND}
}
JSON_EOF
}

# ── Output Text Result ──────────────────────────────────────────────────────
output_text() {
    echo ""
    echo -e "${BOLD}YAWL Protocol Compliance Report${NC}"
    echo "Generated: $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
    echo ""

    # MCP Section
    log_section "MCP SDK (io.modelcontextprotocol.sdk)"
    echo "  Current Version: ${MCP_CURRENT_VERSION:-unknown}"
    echo "  Latest Version:  ${MCP_LATEST_VERSION:-unknown}"

    case "${MCP_STATUS:-unknown}" in
        CURRENT)   echo -e "  Status:          ${GREEN}CURRENT${NC}" ;;
        OUTDATED)  echo -e "  Status:          ${YELLOW}OUTDATED${NC}" ;;
        AHEAD)     echo -e "  Status:          ${BLUE}AHEAD${NC}" ;;
        UNKNOWN)   echo -e "  Status:          ${CYAN}UNKNOWN${NC}" ;;
        *)         echo -e "  Status:          ${RED}${MCP_STATUS}${NC}" ;;
    esac

    echo ""
    echo "  Supported Features:   ${MCP_SUPPORTED_FEATURES:-none}"
    echo "  Unsupported Features: ${MCP_UNSUPPORTED_FEATURES:-none}"

    # A2A Section
    echo ""
    log_section "A2A SDK (io.anthropic)"
    echo "  Current Version: ${A2A_CURRENT_VERSION:-unknown}"
    echo "  Local Installed: ${A2A_LOCAL_VERSION:-not installed}"
    echo "  Maven Central:   Not available (local JARs only)"

    case "${A2A_STATUS:-unknown}" in
        INSTALLED)  echo -e "  Status:          ${GREEN}INSTALLED${NC}" ;;
        NOT_INSTALLED) echo -e "  Status:          ${YELLOW}NOT INSTALLED${NC}" ;;
        PARTIAL)    echo -e "  Status:          ${YELLOW}PARTIAL${NC}" ;;
        UNKNOWN)    echo -e "  Status:          ${CYAN}UNKNOWN${NC}" ;;
        *)          echo -e "  Status:          ${RED}${A2A_STATUS}${NC}" ;;
    esac

    echo ""
    echo "  Supported Features:   ${A2A_SUPPORTED_FEATURES:-none}"
    echo "  Unsupported Features: ${A2A_UNSUPPORTED_FEATURES:-none}"

    # Deprecations Section
    echo ""
    log_section "Deprecated Features Detection"
    if [[ -n "${DEPRECATIONS:-}" ]]; then
        # Items are separated by ||| and key|value within each item
        while IFS= read -r item; do
            [[ -z "$item" ]] && continue
            if [[ "$item" == *"|"* ]]; then
                local key="${item%%|*}"
                local value="${item#*|}"
                echo -e "  ${YELLOW}[DEPRECATED]${NC} ${key}: ${value}"
            else
                echo -e "  ${YELLOW}[DEPRECATED]${NC} ${item}"
            fi
        done <<< "$(echo "$DEPRECATIONS" | sed 's/|||/\n/g')"
    else
        echo "  No deprecated features detected."
    fi

    # Summary
    echo ""
    log_section "Summary"
    if [[ $ISSUES_FOUND -eq 0 ]]; then
        echo -e "  ${GREEN}All protocol checks passed.${NC}"
        echo "  No updates required."
    else
        echo -e "  ${YELLOW}Issues found: ${ISSUES_FOUND}${NC}"
        if [[ "$MCP_STATUS" == "OUTDATED" ]]; then
            echo -e "  ${YELLOW}Recommendation:${NC} Update MCP SDK from ${MCP_CURRENT_VERSION} to ${MCP_LATEST_VERSION}"
        fi
        if [[ "$A2A_STATUS" == "NOT_INSTALLED" ]]; then
            echo -e "  ${YELLOW}Recommendation:${NC} Install A2A SDK JARs to local Maven repository"
            echo "    See: yawl-integration/pom.xml for install commands"
        fi
    fi

    echo ""
}

# ═══════════════════════════════════════════════════════════════════════════
# MAIN EXECUTION
# ═══════════════════════════════════════════════════════════════════════════

# ── MCP Version Check ───────────────────────────────────────────────────────
log_verbose "Starting MCP version check..."

MCP_CURRENT_VERSION=$(extract_version_from_pom "${REPO_ROOT}/pom.xml" "mcp.version")
MCP_LATEST_VERSION=$(get_latest_mcp_version)

log_verbose "MCP current: ${MCP_CURRENT_VERSION:-unknown}, latest: ${MCP_LATEST_VERSION:-unknown}"

if [[ -n "$MCP_CURRENT_VERSION" && -n "$MCP_LATEST_VERSION" ]]; then
    MCP_CMP_RESULT=0
    compare_versions "$MCP_CURRENT_VERSION" "$MCP_LATEST_VERSION" || MCP_CMP_RESULT=$?
    case $MCP_CMP_RESULT in
        0) MCP_STATUS="CURRENT" ;;
        1) MCP_STATUS="AHEAD" ;;
        2) MCP_STATUS="OUTDATED"; ((ISSUES_FOUND++)) || true ;;
    esac
else
    MCP_STATUS="UNKNOWN"
fi

# Get MCP features
MCP_FEATURES=$(check_mcp_features "${MCP_CURRENT_VERSION:-0}")
MCP_SUPPORTED_FEATURES=$(echo "$MCP_FEATURES" | grep "^supported:" | cut -d: -f2- || echo "")
MCP_UNSUPPORTED_FEATURES=$(echo "$MCP_FEATURES" | grep "^unsupported:" | cut -d: -f2- || echo "")

# ── A2A Version Check ───────────────────────────────────────────────────────
log_verbose "Starting A2A version check..."

A2A_CURRENT_VERSION=$(extract_version_from_pom "${REPO_ROOT}/pom.xml" "a2a.version")
A2A_LOCAL_VERSION=$(get_local_a2a_version)

log_verbose "A2A current: ${A2A_CURRENT_VERSION:-unknown}, local: ${A2A_LOCAL_VERSION:-not installed}"

if [[ -n "$A2A_LOCAL_VERSION" ]]; then
    if [[ "$A2A_LOCAL_VERSION" == "$A2A_CURRENT_VERSION" ]]; then
        A2A_STATUS="INSTALLED"
    else
        A2A_STATUS="PARTIAL"
        ((ISSUES_FOUND++)) || true
    fi
else
    A2A_STATUS="NOT_INSTALLED"
    ((ISSUES_FOUND++)) || true
fi

# Get A2A features
A2A_FEATURES=$(check_a2a_features "${A2A_CURRENT_VERSION:-0}")
A2A_SUPPORTED_FEATURES=$(echo "$A2A_FEATURES" | grep "^supported:" | cut -d: -f2- || echo "")
A2A_UNSUPPORTED_FEATURES=$(echo "$A2A_FEATURES" | grep "^unsupported:" | cut -d: -f2- || echo "")

# ── Deprecated Features Check ───────────────────────────────────────────────
log_verbose "Checking for deprecated features..."
DEPRECATIONS=$(check_deprecated_features)

# Count deprecation warnings as issues if they affect functionality
if echo "$DEPRECATIONS" | grep -q "a2a_sdk_excluded"; then
    : # Already counted via A2A_STATUS
fi

# ── Output Results ──────────────────────────────────────────────────────────
if [[ "$OUTPUT_FORMAT" == "json" ]]; then
    output_json
else
    output_text
fi

# ── Save Report ─────────────────────────────────────────────────────────────
save_compliance_report

# ── Exit with appropriate code ──────────────────────────────────────────────
exit $ISSUES_FOUND
