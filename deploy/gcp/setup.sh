#!/usr/bin/env bash
# deploy/gcp/setup.sh — One-time GCP setup for Claude Code autonomous runner
#
# Uses Cloud Source Repositories (CSR) as the code store so the Cloud Run
# service account can clone/push without any GitHub token.
# GitHub ↔ CSR sync is optional (see step 5).
#
# Usage: ./setup.sh <project-id> <region> [github-repo-url]
# Example: ./setup.sh my-project us-central1 https://github.com/seanchatmangpt/yawl.git
set -euo pipefail

: "${1:?Usage: $0 <project-id> <region> [github-repo-url]}"
: "${2:?Usage: $0 <project-id> <region> [github-repo-url]}"

PROJECT_ID="$1"
REGION="$2"
GITHUB_URL="${3:-}"          # optional — if provided, we mirror GitHub → CSR once
CSR_REPO="yawl"
IMAGE_REPO="yawl"
SA_NAME="yawl-claude-runner"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/${IMAGE_REPO}"
# CSR clone URL — authenticated via Workload Identity / gcloud credential helper
CSR_URL="https://source.developers.google.com/p/${PROJECT_ID}/r/${CSR_REPO}"

log_info() { printf '\033[0;34m[INFO]\033[0m %s\n' "$*"; }
log_ok()   { printf '\033[0;32m[ OK ]\033[0m %s\n' "$*"; }
log_warn() { printf '\033[0;33m[WARN]\033[0m %s\n' "$*"; }

log_info "Project: ${PROJECT_ID} | Region: ${REGION}"

# ---------------------------------------------------------------------------
# 1. Set project + enable APIs
# ---------------------------------------------------------------------------
gcloud config set project "${PROJECT_ID}"

log_info "Enabling GCP APIs..."
gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com \
    sourcerepo.googleapis.com \
    iam.googleapis.com \
    cloudbuild.googleapis.com
log_ok "APIs enabled"

# ---------------------------------------------------------------------------
# 2. Cloud Source Repository — GCP-native git, no GitHub token needed
# ---------------------------------------------------------------------------
log_info "Creating Cloud Source Repository: ${CSR_REPO}"
gcloud source repos create "${CSR_REPO}" \
    --project="${PROJECT_ID}" \
    2>/dev/null || log_warn "CSR repo '${CSR_REPO}' already exists — skipping"
log_ok "CSR: ${CSR_URL}"

# ---------------------------------------------------------------------------
# 3. Artifact Registry for Docker images
# ---------------------------------------------------------------------------
log_info "Creating Artifact Registry repository: ${IMAGE_REPO}"
gcloud artifacts repositories create "${IMAGE_REPO}" \
    --repository-format=docker \
    --location="${REGION}" \
    --description="YAWL Claude Code runner images" \
    2>/dev/null || log_warn "Artifact Registry repo already exists — skipping"
log_ok "Registry: ${REGISTRY}"

# ---------------------------------------------------------------------------
# 4. Service Account + IAM
# ---------------------------------------------------------------------------
log_info "Creating service account: ${SA_NAME}"
gcloud iam service-accounts create "${SA_NAME}" \
    --display-name="YAWL Claude Code Runner" \
    --description="Runs Claude Code jobs on Cloud Run; auth to CSR via Workload Identity" \
    2>/dev/null || log_warn "Service account already exists — skipping"

log_info "Granting IAM roles to ${SA_EMAIL}..."
for ROLE in \
    roles/secretmanager.secretAccessor \
    roles/artifactregistry.reader \
    roles/logging.logWriter \
    roles/run.invoker \
    roles/source.writer; do          # source.writer = clone + push to CSR
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="${ROLE}" \
        --quiet
done
log_ok "IAM roles granted (includes source.writer for CSR)"

# ---------------------------------------------------------------------------
# 5. Seed CSR from GitHub (one-time, optional)
#    Subsequent pushes come from the container itself.
# ---------------------------------------------------------------------------
if [[ -n "${GITHUB_URL}" ]]; then
    log_info "Seeding CSR from GitHub: ${GITHUB_URL}"
    TMP_CLONE="$(mktemp -d)"
    trap 'rm -rf "${TMP_CLONE}"' EXIT

    git clone --mirror "${GITHUB_URL}" "${TMP_CLONE}/repo.git"
    cd "${TMP_CLONE}/repo.git"
    git remote add csr "${CSR_URL}"
    # Use application-default credentials (whoever ran setup.sh)
    git config credential.helper "gcloud.sh"
    git push csr --mirror
    cd - >/dev/null
    log_ok "GitHub → CSR mirror complete"
else
    log_warn "No GitHub URL provided — CSR is empty."
    log_warn "Push your repo manually:"
    log_warn "  git remote add csr ${CSR_URL}"
    log_warn "  git push csr --all"
fi

# ---------------------------------------------------------------------------
# 6. Secret Manager — only ANTHROPIC_API_KEY needed now (no GitHub token)
# ---------------------------------------------------------------------------
log_info "Setting up Secret Manager..."

store_secret() {
    local NAME="$1" PROMPT_TEXT="$2"
    if gcloud secrets describe "${NAME}" --project="${PROJECT_ID}" &>/dev/null; then
        log_warn "Secret '${NAME}' exists. Press Enter to keep current value."
    fi
    local VAL
    read -r -s -p "${PROMPT_TEXT}: " VAL; echo
    [[ -z "${VAL}" ]] && { log_warn "Skipping empty value for ${NAME}"; return; }

    echo -n "${VAL}" | gcloud secrets create "${NAME}" --data-file=- \
        --project="${PROJECT_ID}" 2>/dev/null || \
    echo -n "${VAL}" | gcloud secrets versions add "${NAME}" --data-file=- \
        --project="${PROJECT_ID}"

    gcloud secrets add-iam-policy-binding "${NAME}" \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="roles/secretmanager.secretAccessor" \
        --project="${PROJECT_ID}" --quiet
    log_ok "Secret '${NAME}' stored"
}

store_secret "anthropic-api-key" "Enter your Anthropic API key (sk-ant-...)"
# GitHub token only needed if you want the container to push back to GitHub too.
# For CSR-only iteration it is not required.

# ---------------------------------------------------------------------------
# 7. Build + push Docker image
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../../docker/claude-code"
IMAGE_TAG="${REGISTRY}/claude-code:latest"

log_info "Configuring Docker for Artifact Registry..."
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

log_info "Building image: ${IMAGE_TAG}"
docker build -t "${IMAGE_TAG}" "${DOCKER_DIR}"
docker push "${IMAGE_TAG}"
log_ok "Image pushed: ${IMAGE_TAG}"

# ---------------------------------------------------------------------------
# 8. Cloud Run Job — GIT_REPO_URL points at CSR, no GIT_TOKEN needed
# ---------------------------------------------------------------------------
log_info "Creating/updating Cloud Run Job: yawl-claude-code"

JOB_ENV="GIT_REPO_URL=${CSR_URL}"
JOB_ENV+=",GIT_BRANCH=master"
JOB_ENV+=",GIT_USER_NAME=Claude Code GCP Bot"
JOB_ENV+=",GIT_USER_EMAIL=claude-code@gcp.run"
JOB_ENV+=",ANTHROPIC_MODEL=claude-sonnet-4-6"
JOB_ENV+=",CLAUDE_CODE_REMOTE=true"
JOB_ENV+=",USE_WORKLOAD_IDENTITY=true"

gcloud run jobs create yawl-claude-code \
    --region="${REGION}" \
    --image="${IMAGE_TAG}" \
    --service-account="${SA_EMAIL}" \
    --memory=8Gi \
    --cpu=4 \
    --max-retries=1 \
    --task-timeout=3600 \
    --set-env-vars="${JOB_ENV}" \
    --set-secrets="ANTHROPIC_API_KEY=anthropic-api-key:latest" \
    2>/dev/null || \
gcloud run jobs update yawl-claude-code \
    --region="${REGION}" \
    --image="${IMAGE_TAG}" \
    --service-account="${SA_EMAIL}" \
    --memory=8Gi \
    --cpu=4 \
    --max-retries=1 \
    --task-timeout=3600 \
    --set-env-vars="${JOB_ENV}" \
    --set-secrets="ANTHROPIC_API_KEY=anthropic-api-key:latest"

log_ok "Cloud Run Job created/updated"

echo ""
echo "================================================"
echo "  Setup complete!"
echo ""
echo "  CSR URL : ${CSR_URL}"
echo "  Image   : ${IMAGE_TAG}"
echo "  Job     : yawl-claude-code (${REGION})"
echo ""
echo "  Single-shot:"
echo "    ./run.sh ${PROJECT_ID} ${REGION} \"fix the tests\""
echo ""
echo "  Ralph loop:"
echo "    RALPH_MODE=true MAX_ITERATIONS=30 \\"
echo "      ./run.sh ${PROJECT_ID} ${REGION} \"\$(cat PROMPT.md)\""
echo "================================================"
