#!/bin/bash
# YAWL Secrets Management Script
# Configures secrets across GitHub, Azure, AWS, and GCP
# Version: 5.2

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Required secrets
declare -A REQUIRED_SECRETS=(
    ["DOCKER_USERNAME"]="Docker registry username"
    ["DOCKER_PASSWORD"]="Docker registry password"
    ["ZHIPU_API_KEY"]="Z.AI API key for GLM models"
    ["SONAR_TOKEN"]="SonarQube authentication token"
    ["SLACK_WEBHOOK_URL"]="Slack webhook URL for notifications"
)

# ============================================
# GitHub Secrets Management
# ============================================
configure_github_secrets() {
    log_info "Configuring GitHub Secrets..."

    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) not found. Install from https://cli.github.com/"
        return 1
    fi

    # Check if authenticated
    if ! gh auth status &> /dev/null; then
        log_error "Not authenticated with GitHub. Run: gh auth login"
        return 1
    fi

    local repo_name=$(gh repo view --json nameWithOwner -q .nameWithOwner)
    log_info "Repository: $repo_name"

    for secret_name in "${!REQUIRED_SECRETS[@]}"; do
        local description="${REQUIRED_SECRETS[$secret_name]}"

        if [ -n "${!secret_name:-}" ]; then
            echo "${!secret_name}" | gh secret set "$secret_name" --repo "$repo_name"
            log_info "✓ Set GitHub secret: $secret_name"
        else
            log_warn "⚠ Environment variable $secret_name not set - skipping"
        fi
    done

    # Additional GitHub-specific secrets
    if [ -n "${GITHUB_TOKEN:-}" ]; then
        echo "$GITHUB_TOKEN" | gh secret set GITHUB_TOKEN --repo "$repo_name"
        log_info "✓ Set GitHub secret: GITHUB_TOKEN"
    fi

    log_info "GitHub secrets configured successfully"
}

# ============================================
# AWS Secrets Manager
# ============================================
configure_aws_secrets() {
    log_info "Configuring AWS Secrets Manager..."

    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI not found. Install from https://aws.amazon.com/cli/"
        return 1
    fi

    local region="${AWS_DEFAULT_REGION:-us-east-1}"

    for secret_name in "${!REQUIRED_SECRETS[@]}"; do
        local aws_secret_name="yawl/${secret_name,,}"

        if [ -n "${!secret_name:-}" ]; then
            # Check if secret exists
            if aws secretsmanager describe-secret --secret-id "$aws_secret_name" --region "$region" &> /dev/null; then
                aws secretsmanager update-secret \
                    --secret-id "$aws_secret_name" \
                    --secret-string "${!secret_name}" \
                    --region "$region" &> /dev/null
                log_info "✓ Updated AWS secret: $aws_secret_name"
            else
                aws secretsmanager create-secret \
                    --name "$aws_secret_name" \
                    --description "${REQUIRED_SECRETS[$secret_name]}" \
                    --secret-string "${!secret_name}" \
                    --region "$region" &> /dev/null
                log_info "✓ Created AWS secret: $aws_secret_name"
            fi
        else
            log_warn "⚠ Environment variable $secret_name not set - skipping AWS"
        fi
    done

    # Database password
    if [ -n "${DB_PASSWORD:-}" ]; then
        aws secretsmanager create-secret \
            --name "yawl/database" \
            --description "YAWL database credentials" \
            --secret-string "{\"password\":\"$DB_PASSWORD\",\"username\":\"yawl\"}" \
            --region "$region" &> /dev/null || \
        aws secretsmanager update-secret \
            --secret-id "yawl/database" \
            --secret-string "{\"password\":\"$DB_PASSWORD\",\"username\":\"yawl\"}" \
            --region "$region" &> /dev/null
        log_info "✓ Set AWS database credentials"
    fi

    log_info "AWS Secrets Manager configured successfully"
}

# ============================================
# Azure Key Vault
# ============================================
configure_azure_secrets() {
    log_info "Configuring Azure Key Vault..."

    if ! command -v az &> /dev/null; then
        log_error "Azure CLI not found. Install from https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
        return 1
    fi

    local vault_name="${AZURE_KEY_VAULT_NAME:-yawl-keyvault}"

    # Check if logged in
    if ! az account show &> /dev/null; then
        log_error "Not authenticated with Azure. Run: az login"
        return 1
    fi

    for secret_name in "${!REQUIRED_SECRETS[@]}"; do
        local azure_secret_name="${secret_name//_/-}"

        if [ -n "${!secret_name:-}" ]; then
            az keyvault secret set \
                --vault-name "$vault_name" \
                --name "$azure_secret_name" \
                --value "${!secret_name}" &> /dev/null
            log_info "✓ Set Azure Key Vault secret: $azure_secret_name"
        else
            log_warn "⚠ Environment variable $secret_name not set - skipping Azure"
        fi
    done

    log_info "Azure Key Vault configured successfully"
}

# ============================================
# GCP Secret Manager
# ============================================
configure_gcp_secrets() {
    log_info "Configuring GCP Secret Manager..."

    if ! command -v gcloud &> /dev/null; then
        log_error "gcloud CLI not found. Install from https://cloud.google.com/sdk/docs/install"
        return 1
    fi

    local project_id="${GCP_PROJECT_ID:-$(gcloud config get-value project)}"

    if [ -z "$project_id" ]; then
        log_error "GCP project ID not set. Set GCP_PROJECT_ID or run: gcloud config set project PROJECT_ID"
        return 1
    fi

    for secret_name in "${!REQUIRED_SECRETS[@]}"; do
        local gcp_secret_name="${secret_name//_/-}"

        if [ -n "${!secret_name:-}" ]; then
            # Check if secret exists
            if gcloud secrets describe "$gcp_secret_name" --project="$project_id" &> /dev/null; then
                echo -n "${!secret_name}" | gcloud secrets versions add "$gcp_secret_name" \
                    --data-file=- --project="$project_id" &> /dev/null
                log_info "✓ Updated GCP secret: $gcp_secret_name"
            else
                echo -n "${!secret_name}" | gcloud secrets create "$gcp_secret_name" \
                    --data-file=- \
                    --replication-policy="automatic" \
                    --project="$project_id" &> /dev/null
                log_info "✓ Created GCP secret: $gcp_secret_name"
            fi
        else
            log_warn "⚠ Environment variable $secret_name not set - skipping GCP"
        fi
    done

    log_info "GCP Secret Manager configured successfully"
}

# ============================================
# Validation
# ============================================
validate_secrets() {
    log_info "Validating required secrets..."

    local missing_count=0

    for secret_name in "${!REQUIRED_SECRETS[@]}"; do
        if [ -z "${!secret_name:-}" ]; then
            log_error "✗ Missing: $secret_name - ${REQUIRED_SECRETS[$secret_name]}"
            ((missing_count++))
        else
            log_info "✓ Found: $secret_name"
        fi
    done

    if [ $missing_count -gt 0 ]; then
        log_error "$missing_count required secrets missing"
        return 1
    fi

    log_info "All required secrets present"
    return 0
}

# ============================================
# Pre-commit Hook
# ============================================
install_precommit_hook() {
    log_info "Installing pre-commit hook to prevent secret commits..."

    local hook_file="$PROJECT_ROOT/.git/hooks/pre-commit"

    cat > "$hook_file" << 'EOF'
#!/bin/bash
# Pre-commit hook to prevent accidental secret commits

PATTERNS=(
    "ZHIPU_API_KEY.*=.*[a-zA-Z0-9]{20,}"
    "password.*=.*['\"][^'\"]{8,}"
    "api[_-]?key.*=.*['\"][a-zA-Z0-9]{20,}"
    "secret.*=.*['\"][a-zA-Z0-9]{20,}"
    "token.*=.*['\"][a-zA-Z0-9]{20,}"
    "-----BEGIN (RSA |DSA |EC )?PRIVATE KEY-----"
)

FILES=$(git diff --cached --name-only --diff-filter=ACM)

for file in $FILES; do
    for pattern in "${PATTERNS[@]}"; do
        if grep -E "$pattern" "$file" &> /dev/null; then
            echo "ERROR: Potential secret detected in $file"
            echo "Pattern matched: $pattern"
            echo ""
            echo "If this is a false positive, add to .gitignore or use git-crypt"
            exit 1
        fi
    done
done

exit 0
EOF

    chmod +x "$hook_file"
    log_info "✓ Pre-commit hook installed"
}

# ============================================
# Secret Rotation Documentation
# ============================================
generate_rotation_docs() {
    log_info "Generating secret rotation documentation..."

    cat > "$PROJECT_ROOT/docs/SECRET_ROTATION.md" << 'EOF'
# YAWL Secret Rotation Procedures

## Overview
This document outlines procedures for rotating secrets in all environments.

## Rotation Schedule
- **API Keys**: Every 90 days
- **Database Passwords**: Every 60 days
- **Service Tokens**: Every 30 days
- **SSL Certificates**: 30 days before expiration

## Rotation Procedures

### 1. Z.AI API Key (ZHIPU_API_KEY)
```bash
# Generate new key from Z.AI console
# Update in all environments
export ZHIPU_API_KEY="new_key_here"
./ci-cd/scripts/secrets-management.sh --platform all

# Test new key
curl -H "Authorization: Bearer $ZHIPU_API_KEY" \
  https://open.bigmodel.cn/api/paas/v4/models
```

### 2. Docker Registry Credentials
```bash
# Generate new token from Docker Hub
export DOCKER_USERNAME="username"
export DOCKER_PASSWORD="new_token"
./ci-cd/scripts/secrets-management.sh --platform github,aws

# Test login
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
```

### 3. Database Password
```bash
# Generate secure password
export DB_PASSWORD=$(openssl rand -base64 32)

# Update in databases
psql -h db-host -U postgres -c "ALTER USER yawl PASSWORD '$DB_PASSWORD';"

# Update in secret managers
./ci-cd/scripts/secrets-management.sh --platform all
```

### 4. SonarQube Token
```bash
# Generate new token from SonarQube console
export SONAR_TOKEN="new_token_here"
./ci-cd/scripts/secrets-management.sh --platform all

# Revoke old token in SonarQube UI
```

### 5. Slack Webhook URL
```bash
# Generate new webhook from Slack
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/xxx"
./ci-cd/scripts/secrets-management.sh --platform all

# Disable old webhook in Slack
```

## Emergency Rotation
If a secret is compromised:

1. **Immediate**: Rotate the secret immediately
2. **Audit**: Check logs for unauthorized access
3. **Notify**: Alert security team
4. **Document**: Record incident in security log

## Verification
After rotation, verify:
```bash
# Run integration tests
./ci-cd/scripts/verify-secrets.sh

# Check all pipelines pass
# Monitor for authentication errors
```
EOF

    log_info "✓ Secret rotation documentation generated"
}

# ============================================
# Main
# ============================================
main() {
    log_info "YAWL Secrets Management"
    log_info "======================="

    local platform="${1:-all}"

    case "$platform" in
        github)
            validate_secrets && configure_github_secrets
            ;;
        aws)
            validate_secrets && configure_aws_secrets
            ;;
        azure)
            validate_secrets && configure_azure_secrets
            ;;
        gcp)
            validate_secrets && configure_gcp_secrets
            ;;
        all)
            if validate_secrets; then
                configure_github_secrets || log_warn "GitHub configuration failed"
                configure_aws_secrets || log_warn "AWS configuration failed"
                configure_azure_secrets || log_warn "Azure configuration failed"
                configure_gcp_secrets || log_warn "GCP configuration failed"
            fi
            ;;
        validate)
            validate_secrets
            ;;
        install-hook)
            install_precommit_hook
            ;;
        docs)
            generate_rotation_docs
            ;;
        *)
            echo "Usage: $0 {github|aws|azure|gcp|all|validate|install-hook|docs}"
            exit 1
            ;;
    esac

    install_precommit_hook
    generate_rotation_docs

    log_info "✓ Secrets management complete"
}

main "$@"
