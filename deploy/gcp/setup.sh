#!/usr/bin/env bash
# deploy/gcp/setup.sh — One-time GCP setup for Claude Code autonomous runner
# Usage: ./setup.sh <project-id> <region>
# Example: ./setup.sh my-gcp-project us-central1
set -euo pipefail

: "${1:?Usage: $0 <project-id> <region>}"
: "${2:?Usage: $0 <project-id> <region>}"

PROJECT_ID="$1"
REGION="$2"
IMAGE_REPO="yawl"
SA_NAME="yawl-claude-runner"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/${IMAGE_REPO}"

log_info() { printf '\033[0;34m[INFO]\033[0m %s\n' "$*"; }
log_ok()   { printf '\033[0;32m[ OK ]\033[0m %s\n' "$*"; }
log_warn() { printf '\033[0;33m[WARN]\033[0m %s\n' "$*"; }

log_info "Setting up GCP project: ${PROJECT_ID} region: ${REGION}"

# 1. Set active project
gcloud config set project "${PROJECT_ID}"

# 2. Enable required APIs
log_info "Enabling GCP APIs..."
gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com \
    iam.googleapis.com \
    cloudbuild.googleapis.com

log_ok "APIs enabled"

# 3. Create Artifact Registry repo for Docker images
log_info "Creating Artifact Registry repository: ${IMAGE_REPO}"
gcloud artifacts repositories create "${IMAGE_REPO}" \
    --repository-format=docker \
    --location="${REGION}" \
    --description="YAWL Claude Code runner images" \
    2>/dev/null || log_warn "Repository already exists — skipping"

log_ok "Artifact Registry: ${REGISTRY}"

# 4. Create Service Account
log_info "Creating service account: ${SA_NAME}"
gcloud iam service-accounts create "${SA_NAME}" \
    --display-name="YAWL Claude Code Runner" \
    --description="Runs Claude Code jobs autonomously on Cloud Run" \
    2>/dev/null || log_warn "Service account already exists — skipping"

# 5. Grant permissions to service account
log_info "Granting IAM roles to ${SA_EMAIL}..."
for ROLE in \
    roles/secretmanager.secretAccessor \
    roles/artifactregistry.reader \
    roles/logging.logWriter \
    roles/run.invoker; do
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="${ROLE}" \
        --quiet
done
log_ok "IAM roles granted"

# 6. Store secrets in Secret Manager
log_info "Setting up secrets in Secret Manager..."

create_or_update_secret() {
    local SECRET_NAME="$1"
    local PROMPT="$2"
    local SECRET_VALUE

    if gcloud secrets describe "${SECRET_NAME}" --project="${PROJECT_ID}" &>/dev/null; then
        log_warn "Secret '${SECRET_NAME}' already exists. Enter new value to update, or press Enter to keep."
    else
        echo "Creating secret: ${SECRET_NAME}"
    fi

    read -r -s -p "${PROMPT}: " SECRET_VALUE
    echo

    if [[ -z "${SECRET_VALUE}" ]]; then
        log_warn "Skipping empty value for ${SECRET_NAME}"
        return
    fi

    echo -n "${SECRET_VALUE}" | gcloud secrets create "${SECRET_NAME}" \
        --data-file=- \
        --project="${PROJECT_ID}" \
        2>/dev/null || \
    echo -n "${SECRET_VALUE}" | gcloud secrets versions add "${SECRET_NAME}" \
        --data-file=- \
        --project="${PROJECT_ID}"

    # Grant service account access to this secret
    gcloud secrets add-iam-policy-binding "${SECRET_NAME}" \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="roles/secretmanager.secretAccessor" \
        --project="${PROJECT_ID}" \
        --quiet

    log_ok "Secret '${SECRET_NAME}' stored and accessible by service account"
}

create_or_update_secret "anthropic-api-key" "Enter your Anthropic API key (sk-ant-...)"
create_or_update_secret "github-token"      "Enter your GitHub personal access token (ghp_...)"

# 7. Configure Docker auth for Artifact Registry
log_info "Configuring Docker for Artifact Registry..."
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
log_ok "Docker configured"

# 8. Build and push the Claude Code image
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../../docker/claude-code"
IMAGE_TAG="${REGISTRY}/claude-code:latest"

log_info "Building Docker image: ${IMAGE_TAG}"
docker build -t "${IMAGE_TAG}" "${DOCKER_DIR}"

log_info "Pushing image to Artifact Registry..."
docker push "${IMAGE_TAG}"
log_ok "Image pushed: ${IMAGE_TAG}"

# 9. Create the Cloud Run Job (replace placeholders in template)
log_info "Creating Cloud Run Job: yawl-claude-code"
JOB_YAML="${SCRIPT_DIR}/cloud-run-job.yaml"
RENDERED=$(sed \
    -e "s|PROJECT_ID|${PROJECT_ID}|g" \
    -e "s|REGION|${REGION}|g" \
    "${JOB_YAML}")

echo "${RENDERED}" | gcloud run jobs replace - \
    --region="${REGION}" \
    2>/dev/null || \
echo "${RENDERED}" | gcloud run jobs create yawl-claude-code \
    --region="${REGION}" \
    --source - 2>/dev/null || true

# Simpler: just use gcloud run jobs create/update with flags
gcloud run jobs create yawl-claude-code \
    --region="${REGION}" \
    --image="${IMAGE_TAG}" \
    --service-account="${SA_EMAIL}" \
    --memory=8Gi \
    --cpu=4 \
    --max-retries=1 \
    --task-timeout=3600 \
    --set-env-vars="GIT_REPO_URL=https://github.com/seanchatmangpt/yawl.git,GIT_BRANCH=master,GIT_USER_NAME=Claude Code GCP Bot,GIT_USER_EMAIL=claude-code@gcp.run,ANTHROPIC_MODEL=claude-sonnet-4-6,CLAUDE_CODE_REMOTE=true" \
    --set-secrets="ANTHROPIC_API_KEY=anthropic-api-key:latest,GIT_TOKEN=github-token:latest" \
    2>/dev/null || \
gcloud run jobs update yawl-claude-code \
    --region="${REGION}" \
    --image="${IMAGE_TAG}" \
    --service-account="${SA_EMAIL}" \
    --memory=8Gi \
    --cpu=4 \
    --max-retries=1 \
    --task-timeout=3600 \
    --set-env-vars="GIT_REPO_URL=https://github.com/seanchatmangpt/yawl.git,GIT_BRANCH=master,GIT_USER_NAME=Claude Code GCP Bot,GIT_USER_EMAIL=claude-code@gcp.run,ANTHROPIC_MODEL=claude-sonnet-4-6,CLAUDE_CODE_REMOTE=true" \
    --set-secrets="ANTHROPIC_API_KEY=anthropic-api-key:latest,GIT_TOKEN=github-token:latest"

log_ok "Cloud Run Job created/updated"

echo ""
echo "=============================================="
echo "  Setup complete!"
echo "  Run a task with:"
echo "  ./run.sh \"${PROJECT_ID}\" \"${REGION}\" \"your task here\""
echo "=============================================="
