#!/usr/bin/env bash
set -euo pipefail

# Validate required env vars
: "${ANTHROPIC_API_KEY:?ANTHROPIC_API_KEY is required}"
: "${GIT_REPO_URL:?GIT_REPO_URL is required (e.g. https://github.com/org/yawl.git)}"
: "${CLAUDE_TASK:?CLAUDE_TASK is required - the task for Claude to execute}"

BRANCH="${GIT_BRANCH:-master}"
GIT_USER_NAME="${GIT_USER_NAME:-Claude Code Bot}"
GIT_USER_EMAIL="${GIT_USER_EMAIL:-claude-code@gcp.run}"

log_info() { echo "[INFO] $*" >&2; }
log_error() { echo "[ERROR] $*" >&2; }

# Configure git
git config --global user.name "${GIT_USER_NAME}"
git config --global user.email "${GIT_USER_EMAIL}"
git config --global credential.helper store

# Clone repo (use token if provided)
CLONE_URL="${GIT_REPO_URL}"
if [[ -n "${GIT_TOKEN:-}" ]]; then
    # Inject token into HTTPS URL: https://token@github.com/org/repo.git
    CLONE_URL="${GIT_REPO_URL/https:\/\//https://${GIT_TOKEN}@}"
fi

log_info "Cloning ${GIT_REPO_URL} branch=${BRANCH}"
git clone --depth=50 --branch "${BRANCH}" "${CLONE_URL}" /workspace/repo
cd /workspace/repo

# Set upstream tracking
git remote set-url origin "${CLONE_URL}"

log_info "Running Claude Code with task: ${CLAUDE_TASK}"

# Run Claude Code in non-interactive (print) mode
# --allowedTools limits surface area; adjust as needed for your tasks
claude \
    --print \
    --allowedTools "Read,Edit,Write,Bash,Glob,Grep" \
    "${CLAUDE_TASK}"

EXIT_CODE=$?

if [[ $EXIT_CODE -ne 0 ]]; then
    log_error "Claude Code exited with code ${EXIT_CODE}"
    exit "${EXIT_CODE}"
fi

# Check for changes
if git diff --quiet && git diff --staged --quiet; then
    log_info "No changes made by Claude Code — nothing to commit"
    exit 0
fi

# Commit and push changes
COMMIT_MSG="claude(gcp): ${CLAUDE_TASK:0:72}

Autonomous iteration via Cloud Run Job.
ANTHROPIC_MODEL: ${ANTHROPIC_MODEL:-claude-sonnet-4-6}
GCP_JOB: ${CLOUD_RUN_JOB:-local}
GCP_EXECUTION: ${CLOUD_RUN_EXECUTION:-local}"

git add -A
git commit -m "${COMMIT_MSG}"

log_info "Pushing to origin/${BRANCH}"
git push origin "${BRANCH}"

log_info "Done."
