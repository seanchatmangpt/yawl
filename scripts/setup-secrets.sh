#!/bin/bash
# =============================================================================
# YAWL Production Secrets Setup Script
# =============================================================================
# This script initializes the secrets directory with placeholder files.
# Replace the placeholder values with your actual secrets before deployment.
#
# Usage:
#   ./scripts/setup-secrets.sh
#   # Then edit each file in ./secrets/ with your actual values
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SECRETS_DIR="${PROJECT_ROOT}/secrets"

echo "Setting up YAWL production secrets..."
echo "Secrets directory: ${SECRETS_DIR}"

# Create secrets directory with restricted permissions
mkdir -p "${SECRETS_DIR}"
chmod 700 "${SECRETS_DIR}"

# Generate random passwords if openssl is available
generate_password() {
    if command -v openssl &> /dev/null; then
        openssl rand -base64 32 | tr -d '\n'
    else
        echo "CHANGE_ME_$(date +%s)"
    fi
}

# Database password
if [ ! -f "${SECRETS_DIR}/db_password.txt" ]; then
    echo "$(generate_password)" > "${SECRETS_DIR}/db_password.txt"
    echo "Created: db_password.txt"
else
    echo "Exists: db_password.txt"
fi

# Database root password (for administrative tasks)
if [ ! -f "${SECRETS_DIR}/db_root_password.txt" ]; then
    echo "$(generate_password)" > "${SECRETS_DIR}/db_root_password.txt"
    echo "Created: db_root_password.txt"
else
    echo "Exists: db_root_password.txt"
fi

# JWT signing key (256-bit)
if [ ! -f "${SECRETS_DIR}/jwt_signing_key.txt" ]; then
    if command -v openssl &> /dev/null; then
        openssl rand -hex 32 > "${SECRETS_DIR}/jwt_signing_key.txt"
    else
        echo "$(generate_password)" > "${SECRETS_DIR}/jwt_signing_key.txt"
    fi
    echo "Created: jwt_signing_key.txt"
else
    echo "Exists: jwt_signing_key.txt"
fi

# Grafana admin password
if [ ! -f "${SECRETS_DIR}/grafana_admin_password" ]; then
    echo "$(generate_password)" > "${SECRETS_DIR}/grafana_admin_password"
    echo "Created: grafana_admin_password"
else
    echo "Exists: grafana_admin_password"
fi

# TLS certificate (self-signed placeholder - replace with real cert)
if [ ! -f "${SECRETS_DIR}/tls.crt" ]; then
    if command -v openssl &> /dev/null; then
        # Generate self-signed cert as placeholder
        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout "${SECRETS_DIR}/tls.key" \
            -out "${SECRETS_DIR}/tls.crt" \
            -subj "/C=US/ST=State/L=City/O=YAWL/OU=Production/CN=yawl.local" \
            2>/dev/null
        echo "Created: tls.crt and tls.key (self-signed placeholder)"
    else
        echo "PLACEHOLDER_TLS_CERT" > "${SECRETS_DIR}/tls.crt"
        echo "PLACEHOLDER_TLS_KEY" > "${SECRETS_DIR}/tls.key"
        echo "Created: tls.crt and tls.key (text placeholders)"
    fi
else
    echo "Exists: tls.crt and tls.key"
fi

# Set restrictive permissions on all secret files
chmod 600 "${SECRETS_DIR}"/*.txt "${SECRETS_DIR}"/tls.* "${SECRETS_DIR}"/grafana_* 2>/dev/null || true

echo ""
echo "=========================================="
echo "Secrets setup complete!"
echo "=========================================="
echo ""
echo "IMPORTANT: Review and update all files in ${SECRETS_DIR}/"
echo "           with your actual production secrets before deployment."
echo ""
echo "Files created:"
ls -la "${SECRETS_DIR}/"
echo ""
echo "For production TLS certificates:"
echo "  1. Obtain certificates from your CA or Let's Encrypt"
echo "  2. Replace tls.crt and tls.key with your certificates"
echo "  3. Or configure Traefik with Let's Encrypt (see docker-compose.prod.yml)"
