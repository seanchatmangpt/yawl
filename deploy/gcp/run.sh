#!/usr/bin/env bash
# deploy/gcp/run.sh — Trigger a Claude Code Cloud Run Job execution
# Usage: ./run.sh <project-id> <region> "<task>" [branch]
# Example: ./run.sh my-project us-central1 "Fix the failing YNetRunner unit tests" master
set -euo pipefail

: "${1:?Usage: $0 <project-id> <region> <task> [branch]}"
: "${2:?Usage: $0 <project-id> <region> <task> [branch]}"
: "${3:?Usage: $0 <project-id> <region> <task> [branch]}"

PROJECT_ID="$1"
REGION="$2"
TASK="$3"
BRANCH="${4:-master}"
JOB_NAME="yawl-claude-code"

log_info() { printf '\033[0;34m[INFO]\033[0m %s\n' "$*"; }
log_ok()   { printf '\033[0;32m[ OK ]\033[0m %s\n' "$*"; }
log_error() { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*"; }

log_info "Triggering Cloud Run Job: ${JOB_NAME}"
log_info "Project: ${PROJECT_ID} | Region: ${REGION} | Branch: ${BRANCH}"
log_info "Task: ${TASK}"

# Execute the job with the task and branch injected as env vars
EXECUTION=$(gcloud run jobs execute "${JOB_NAME}" \
    --region="${REGION}" \
    --project="${PROJECT_ID}" \
    --update-env-vars="CLAUDE_TASK=${TASK},GIT_BRANCH=${BRANCH}" \
    --format="value(metadata.name)" \
    --wait)

EXIT_CODE=$?

if [[ $EXIT_CODE -ne 0 ]]; then
    log_error "Job execution failed (exit ${EXIT_CODE})"
    exit "${EXIT_CODE}"
fi

log_ok "Execution completed: ${EXECUTION}"
echo ""
echo "View logs:"
echo "  gcloud logging read 'resource.type=cloud_run_job AND resource.labels.job_name=${JOB_NAME}' \\"
echo "    --project=${PROJECT_ID} --limit=100 --format=json | jq -r '.[].textPayload'"
echo ""
echo "Stream logs (while running):"
echo "  gcloud beta run jobs executions logs tail ${EXECUTION} --region=${REGION}"
