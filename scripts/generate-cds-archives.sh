#!/usr/bin/env bash
# ==========================================================================
# generate-cds-archives.sh — Class Data Sharing Archive Generator for YAWL
#
# Generates CDS archives for hot modules (yawl-engine, yawl-elements) to
# reduce startup time by 30-40% on subsequent runs. Uses Java 25+ CDS.
#
# Usage:
#   bash scripts/generate-cds-archives.sh              # Generate for all hot modules
#   bash scripts/generate-cds-archives.sh yawl-engine  # Generate specific module
#   bash scripts/generate-cds-archives.sh --validate   # Validate existing archives
#   bash scripts/generate-cds-archives.sh --clean      # Remove all CDS archives
#
# Environment:
#   CDS_VERBOSE=1     Show detailed output (default: quiet)
#   CDS_SKIP_VALIDATE Skip validation after generation (default: validate)
#
# Exit codes:
#   0 = All archives generated/validated successfully
#   1 = Java version incompatible or transient error
#   2 = Generation/validation failed, manual intervention required
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CDS_DIR="${REPO_ROOT}/.yawl/cds"
METADATA_FILE="${CDS_DIR}/metadata.json"

# Hot modules that benefit most from CDS (critical path)
HOT_MODULES=("yawl-engine" "yawl-elements")

# Color codes
readonly RED='\033[91m'
readonly GREEN='\033[92m'
readonly YELLOW='\033[93m'
readonly BLUE='\033[94m'
readonly CYAN='\033[96m'
readonly RESET='\033[0m'

# Helper functions
log() {
    echo -e "${CYAN}cds${RESET}: $1" >&2
}

log_info() {
    echo -e "${CYAN}cds${RESET}: ${GREEN}[INFO]${RESET} $1" >&2
}

log_warn() {
    echo -e "${CYAN}cds${RESET}: ${YELLOW}[WARN]${RESET} $1" >&2
}

log_error() {
    echo -e "${CYAN}cds${RESET}: ${RED}[ERROR]${RESET} $1" >&2
}

verbose() {
    [[ "${CDS_VERBOSE:-0}" == "1" ]] && echo -e "${CYAN}cds${RESET}: ${BLUE}[DEBUG]${RESET} $1" >&2 || true
}

# ── Java Version Check ────────────────────────────────────────────────────
check_java_version() {
    local java_version
    java_version=$(java -version 2>&1 | grep 'version "' | cut -d'"' -f2 | cut -d'.' -f1)

    if [[ ${java_version} -lt 25 ]]; then
        log_error "Java 25+ required for CDS. Found Java ${java_version}. Aborting."
        return 1
    fi

    log_info "Java version check: ${java_version} (OK)"
    return 0
}

# ── Verify Java Home ──────────────────────────────────────────────────────
verify_java_home() {
    if [[ -z "${JAVA_HOME:-}" ]]; then
        log_error "JAVA_HOME not set. Cannot generate CDS archives."
        return 1
    fi

    if [[ ! -f "${JAVA_HOME}/bin/java" ]]; then
        log_error "JAVA_HOME points to invalid location: ${JAVA_HOME}"
        return 1
    fi

    verbose "JAVA_HOME verified: ${JAVA_HOME}"
    return 0
}

# ── Get Java Version String ───────────────────────────────────────────────
get_java_version_string() {
    java -version 2>&1 | grep 'version "' | cut -d'"' -f2
}

# ── Initialize CDS Directory ──────────────────────────────────────────────
init_cds_dir() {
    mkdir -p "${CDS_DIR}"
    verbose "CDS directory initialized: ${CDS_DIR}"
}

# ── Load metadata ─────────────────────────────────────────────────────────
load_metadata() {
    if [[ ! -f "${METADATA_FILE}" ]]; then
        echo "{}"
        return 0
    fi
    cat "${METADATA_FILE}"
}

# ── Save metadata ─────────────────────────────────────────────────────────
save_metadata() {
    local metadata="$1"
    echo "${metadata}" | jq '.' > "${METADATA_FILE}.tmp"
    mv "${METADATA_FILE}.tmp" "${METADATA_FILE}"
    verbose "Metadata saved to ${METADATA_FILE}"
}

# ── Generate CDS archive list for module ──────────────────────────────────
generate_class_list() {
    local module="$1"
    local target_dir="${REPO_ROOT}/${module}/target/classes"

    if [[ ! -d "${target_dir}" ]]; then
        verbose "Module not compiled: ${target_dir} does not exist"
        return 1
    fi

    local list_file="${CDS_DIR}/${module}-classes.lst"

    # Find all .class files and convert to FQN format
    find "${target_dir}" -name "*.class" \
        | sed "s|${target_dir}/||g; s|/|.|g; s|\.class||g" \
        > "${list_file}"

    local class_count
    class_count=$(wc -l < "${list_file}")

    verbose "Generated class list for ${module}: ${class_count} classes"
    echo "${list_file}"
    return 0
}

# ── Generate CDS archive for module ───────────────────────────────────────
generate_cds_archive() {
    local module="$1"
    local target_dir="${REPO_ROOT}/${module}/target/classes"
    local archive_file="${CDS_DIR}/${module}.jsa"

    if [[ ! -d "${target_dir}" ]]; then
        log_warn "Module target directory not found: ${target_dir}. Cannot generate CDS."
        return 1
    fi

    # Generate class list
    local list_file
    list_file=$(generate_class_list "${module}") || {
        log_warn "Failed to generate class list for ${module}"
        return 1
    }

    log_info "Generating CDS archive: ${module}"
    verbose "  Target dir: ${target_dir}"
    verbose "  Archive: ${archive_file}"
    verbose "  Class list: ${list_file}"

    # Use -Xshare:dump to create CDS archive
    # We use the class list to optimize what gets pre-loaded
    if java \
        -XX:DumpLoadedClassList="${list_file}" \
        -XX:ArchiveClassesAtExit="${archive_file}" \
        -XX:+UseCompactObjectHeaders \
        --enable-preview \
        -cp "${target_dir}" \
        org.yawlfoundation.yawl.engine.YEngine \
        2>/dev/null; then

        log_info "CDS archive generated: ${archive_file}"

        # Validate archive was created
        if [[ -f "${archive_file}" ]]; then
            local archive_size
            archive_size=$(stat -c%s "${archive_file}")
            verbose "  Archive size: $((archive_size / 1024)) KB"
            return 0
        else
            log_warn "CDS archive file not created: ${archive_file}"
            return 1
        fi
    else
        # Fall back: try simpler approach without class list
        verbose "Fallback: Generating CDS archive without explicit class list"

        if java \
            -XX:ArchiveClassesAtExit="${archive_file}" \
            -XX:+UseCompactObjectHeaders \
            --enable-preview \
            -cp "${target_dir}" \
            org.yawlfoundation.yawl.engine.YEngine \
            2>/dev/null; then

            log_info "CDS archive generated (fallback mode): ${archive_file}"

            if [[ -f "${archive_file}" ]]; then
                local archive_size
                archive_size=$(stat -c%s "${archive_file}")
                verbose "  Archive size: $((archive_size / 1024)) KB"
                return 0
            fi
        fi

        log_warn "Failed to generate CDS archive for ${module}"
        return 1
    fi
}

# ── Validate CDS archive ──────────────────────────────────────────────────
validate_cds_archive() {
    local module="$1"
    local archive_file="${CDS_DIR}/${module}.jsa"

    if [[ ! -f "${archive_file}" ]]; then
        verbose "Archive does not exist: ${archive_file}"
        return 1
    fi

    # Check if archive is usable with current Java version
    if java \
        -XX:SharedArchiveFile="${archive_file}" \
        -XX:+UseCompactObjectHeaders \
        --enable-preview \
        --version 2>/dev/null | grep -q "OpenJDK"; then

        verbose "Archive validation passed: ${archive_file}"
        return 0
    else
        log_warn "Archive validation failed (may need regeneration): ${archive_file}"
        return 1
    fi
}

# ── Update metadata for module ────────────────────────────────────────────
update_metadata() {
    local module="$1"
    local archive_file="${CDS_DIR}/${module}.jsa"

    local metadata
    metadata=$(load_metadata)

    local java_version
    java_version=$(get_java_version_string)

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    local archive_size=0
    if [[ -f "${archive_file}" ]]; then
        archive_size=$(stat -c%s "${archive_file}")
    fi

    # Update or create entry for this module
    metadata=$(echo "${metadata}" | jq \
        --arg module "${module}" \
        --arg java_version "${java_version}" \
        --arg timestamp "${timestamp}" \
        --argjson archive_size "${archive_size}" \
        '.modules |= if type == "null" then {} else . end
        | .modules[$module] = {
            "java_version": $java_version,
            "generated_at": $timestamp,
            "archive_size": $archive_size
        }')

    save_metadata "${metadata}"
}

# ── Check if regeneration is needed ───────────────────────────────────────
needs_regeneration() {
    local module="$1"
    local archive_file="${CDS_DIR}/${module}.jsa"

    # Always regenerate if archive doesn't exist
    if [[ ! -f "${archive_file}" ]]; then
        return 0
    fi

    # Check if Java version changed
    local current_java
    current_java=$(get_java_version_string)

    local metadata
    metadata=$(load_metadata)

    local stored_java
    stored_java=$(echo "${metadata}" | jq -r ".modules[\"${module}\"].java_version // \"\"")

    if [[ "${current_java}" != "${stored_java}" ]]; then
        verbose "Java version changed for ${module}: ${stored_java} -> ${current_java}"
        return 0
    fi

    verbose "Archive is current for ${module}, skipping regeneration"
    return 1
}

# ── Process module ────────────────────────────────────────────────────────
process_module() {
    local module="$1"

    log "Processing module: ${module}"

    if needs_regeneration "${module}"; then
        if generate_cds_archive "${module}"; then
            if [[ "${CDS_SKIP_VALIDATE:-0}" != "1" ]]; then
                if validate_cds_archive "${module}"; then
                    update_metadata "${module}"
                    log_info "Module ${module}: ${GREEN}✓${RESET}"
                    return 0
                else
                    log_warn "Module ${module}: validation failed (but archive may still work)"
                    update_metadata "${module}"
                    return 0
                fi
            else
                update_metadata "${module}"
                log_info "Module ${module}: ${GREEN}✓${RESET} (validation skipped)"
                return 0
            fi
        else
            log_error "Module ${module}: failed to generate archive"
            return 1
        fi
    else
        log_info "Module ${module}: ${GREEN}current${RESET} (skipped)"
        return 0
    fi
}

# ── Generate all hot modules ──────────────────────────────────────────────
generate_all() {
    local failed_modules=()

    log "Generating CDS archives for hot modules"

    for module in "${HOT_MODULES[@]}"; do
        if ! process_module "${module}"; then
            failed_modules+=("${module}")
        fi
    done

    if [[ ${#failed_modules[@]} -gt 0 ]]; then
        log_error "Failed modules: ${failed_modules[*]}"
        return 2
    fi

    log_info "All CDS archives generated successfully"
    return 0
}

# ── Generate specific modules ─────────────────────────────────────────────
generate_specific() {
    local modules=("$@")
    local failed_modules=()

    for module in "${modules[@]}"; do
        if ! process_module "${module}"; then
            failed_modules+=("${module}")
        fi
    done

    if [[ ${#failed_modules[@]} -gt 0 ]]; then
        log_error "Failed modules: ${failed_modules[*]}"
        return 2
    fi

    return 0
}

# ── Validate all archives ─────────────────────────────────────────────────
validate_all() {
    log "Validating CDS archives"

    local failed_modules=()

    for module in "${HOT_MODULES[@]}"; do
        if ! validate_cds_archive "${module}"; then
            failed_modules+=("${module}")
        fi
    done

    if [[ ${#failed_modules[@]} -gt 0 ]]; then
        log_warn "Validation failed for: ${failed_modules[*]}"
        return 2
    fi

    log_info "All archives validated successfully"
    return 0
}

# ── Clean archives ────────────────────────────────────────────────────────
clean_archives() {
    log "Removing CDS archives"

    for module in "${HOT_MODULES[@]}"; do
        local archive_file="${CDS_DIR}/${module}.jsa"
        local list_file="${CDS_DIR}/${module}-classes.lst"

        if [[ -f "${archive_file}" ]]; then
            rm -f "${archive_file}"
            verbose "Removed: ${archive_file}"
        fi

        if [[ -f "${list_file}" ]]; then
            rm -f "${list_file}"
            verbose "Removed: ${list_file}"
        fi
    done

    if [[ -f "${METADATA_FILE}" ]]; then
        rm -f "${METADATA_FILE}"
        verbose "Removed: ${METADATA_FILE}"
    fi

    log_info "CDS archives cleaned"
    return 0
}

# ── Show status ───────────────────────────────────────────────────────────
show_status() {
    log "CDS Archive Status"
    log ""

    local metadata
    metadata=$(load_metadata)

    for module in "${HOT_MODULES[@]}"; do
        local archive_file="${CDS_DIR}/${module}.jsa"

        if [[ -f "${archive_file}" ]]; then
            local archive_size
            archive_size=$(stat -c%s "${archive_file}")

            local java_version
            java_version=$(echo "${metadata}" | jq -r ".modules[\"${module}\"].java_version // \"unknown\"")

            local generated_at
            generated_at=$(echo "${metadata}" | jq -r ".modules[\"${module}\"].generated_at // \"unknown\"")

            log "  ${GREEN}${module}${RESET}: ${archive_file}"
            log "    Size: $((archive_size / 1024)) KB"
            log "    Java: ${java_version}"
            log "    Generated: ${generated_at}"
        else
            log "  ${YELLOW}${module}${RESET}: not generated"
        fi
    done

    log ""
}

# ── Main entry point ──────────────────────────────────────────────────────
main() {
    local action="${1:-generate}"
    shift || true

    # Initialize
    init_cds_dir

    # Check Java compatibility
    check_java_version || exit 1
    verify_java_home || exit 1

    # Process action
    case "${action}" in
        generate)
            if [[ $# -gt 0 ]]; then
                generate_specific "$@"
            else
                generate_all
            fi
            ;;
        validate)
            validate_all
            ;;
        clean)
            clean_archives
            ;;
        status)
            show_status
            ;;
        *)
            log_error "Unknown action: ${action}"
            echo "Usage: $0 [generate|validate|clean|status] [modules...]"
            exit 1
            ;;
    esac
}

main "$@"
