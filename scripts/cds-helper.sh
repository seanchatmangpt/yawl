#!/usr/bin/env bash
# ==========================================================================
# cds-helper.sh — CDS Integration Helper for dx.sh
#
# Provides utilities for managing CDS archives during the build process:
# - Validate existing archives
# - Generate missing archives
# - Provide CDS flags for Maven builds
#
# This script is called by dx.sh and other build automation.
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CDS_DIR="${REPO_ROOT}/.yawl/cds"

# Hot modules that use CDS
HOT_MODULES=("yawl-engine" "yawl-elements")

# ── Get CDS flags for Maven ───────────────────────────────────────────────
# Outputs space-separated JVM flags to use with Maven
# If archives exist and are valid, includes -XX:SharedArchiveFile flags
get_cds_flags() {
    local flags=""

    for module in "${HOT_MODULES[@]}"; do
        local archive_file="${CDS_DIR}/${module}.jsa"

        # Only add flag if archive exists and file is > 1KB (reasonable size)
        if [[ -f "${archive_file}" ]]; then
            local size
            size=$(stat -c%s "${archive_file}" 2>/dev/null || echo 0)

            if [[ ${size} -gt 1024 ]]; then
                flags="${flags} -XX:SharedArchiveFile=${archive_file}"
            fi
        fi
    done

    echo "${flags}" | sed 's/^ //'
}

# ── Check if CDS should be used ───────────────────────────────────────────
# Returns 0 if at least one archive exists and is valid
should_use_cds() {
    for module in "${HOT_MODULES[@]}"; do
        local archive_file="${CDS_DIR}/${module}.jsa"

        if [[ -f "${archive_file}" ]]; then
            local size
            size=$(stat -c%s "${archive_file}" 2>/dev/null || echo 0)

            if [[ ${size} -gt 1024 ]]; then
                return 0
            fi
        fi
    done

    return 1
}

# ── Validate all CDS archives ─────────────────────────────────────────────
# Returns 0 if all archives are valid, 1 if any are invalid
validate_cds() {
    [[ -d "${CDS_DIR}" ]] || return 0  # No CDS dir = no archives to validate

    for module in "${HOT_MODULES[@]}"; do
        local archive_file="${CDS_DIR}/${module}.jsa"

        if [[ -f "${archive_file}" ]]; then
            # Try to use archive with Java to verify it's valid
            if ! java \
                -XX:SharedArchiveFile="${archive_file}" \
                -XX:+UseCompactObjectHeaders \
                --enable-preview \
                --version 2>/dev/null | grep -q "OpenJDK"; then

                # Archive is invalid, might need regeneration
                return 1
            fi
        fi
    done

    return 0
}

# ── Report CDS status ─────────────────────────────────────────────────────
report_cds_status() {
    echo "CDS Status:"

    if ! should_use_cds; then
        echo "  Status: No valid CDS archives found"
        echo "  Action: Run 'bash scripts/generate-cds-archives.sh' to generate"
        return 1
    fi

    echo "  Status: CDS archives available"

    for module in "${HOT_MODULES[@]}"; do
        local archive_file="${CDS_DIR}/${module}.jsa"

        if [[ -f "${archive_file}" ]]; then
            local size
            size=$(stat -c%s "${archive_file}")
            echo "    - ${module}: $(( size / 1024 )) KB"
        fi
    done

    return 0
}

# ── Auto-generate CDS if needed ───────────────────────────────────────────
auto_generate_cds() {
    local force="${1:-0}"

    # Check if any hot module was freshly compiled
    local need_regenerate=0

    for module in "${HOT_MODULES[@]}"; do
        local target_dir="${REPO_ROOT}/${module}/target/classes"
        local archive_file="${CDS_DIR}/${module}.jsa"

        # Force regenerate if requested
        if [[ "${force}" == "1" ]]; then
            need_regenerate=1
            break
        fi

        # Check if module was compiled after CDS was generated
        if [[ -d "${target_dir}" ]] && [[ -f "${archive_file}" ]]; then
            if [[ "${target_dir}" -nt "${archive_file}" ]]; then
                need_regenerate=1
                break
            fi
        elif [[ -d "${target_dir}" ]] && [[ ! -f "${archive_file}" ]]; then
            need_regenerate=1
            break
        fi
    done

    if [[ "${need_regenerate}" == "1" ]]; then
        echo "Regenerating CDS archives (modules compiled after CDS generation)..."
        bash "${SCRIPT_DIR}/generate-cds-archives.sh" generate
        return $?
    fi

    return 0
}

# ── Main dispatcher ───────────────────────────────────────────────────────
main() {
    local command="${1:-help}"

    case "${command}" in
        flags)
            get_cds_flags
            ;;
        should-use)
            should_use_cds
            ;;
        validate)
            validate_cds
            ;;
        status)
            report_cds_status
            ;;
        auto-generate)
            auto_generate_cds "${2:-0}"
            ;;
        *)
            echo "Usage: $0 {flags|should-use|validate|status|auto-generate}"
            echo ""
            echo "Commands:"
            echo "  flags              Print CDS JVM flags for Maven"
            echo "  should-use         Check if CDS archives exist (exit 0 if yes)"
            echo "  validate           Validate all CDS archives"
            echo "  status             Report CDS status"
            echo "  auto-generate [1]  Regenerate CDS if modules changed (1=force)"
            exit 1
            ;;
    esac
}

main "$@"
