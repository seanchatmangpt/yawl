#!/usr/bin/env bash
# deploy/gcp/run.sh — Trigger a Claude Code Cloud Run Job execution
#
# Single-shot:
#   ./run.sh <project> <region> "<task>" [branch]
#
# Ralph Wiggum loop:
#   RALPH_MODE=true MAX_ITERATIONS=30 COMPLETION_PROMISE="COMPLETE" \
#     ./run.sh <project> <region> "<task>" [branch]
#
# Pass a PROMPT.md path instead of inline task:
#   RALPH_MODE=true ./run.sh <project> <region> /workspace/repo/PROMPT.md master
set -euo pipefail

: "${1:?Usage: $0 <project-id> <region> <task-or-prompt-path> [branch]}"
: "${2:?Usage: $0 <project-id> <region> <task-or-prompt-path> [branch]}"
: "${3:?Usage: $0 <project-id> <region> <task-or-prompt-path> [branch]}"

PROJECT_ID="$1"
REGION="$2"
TASK="$3"
BRANCH="${4:-master}"
JOB_NAME="yawl-claude-code"

RALPH_MODE="${RALPH_MODE:-false}"
MAX_ITERATIONS="${MAX_ITERATIONS:-20}"
COMPLETION_PROMISE="${COMPLETION_PROMISE:-COMPLETE}"

log_info() { printf '\033[0;34m[INFO]\033[0m %s\n' "$*"; }
log_ok()   { printf '\033[0;32m[ OK ]\033[0m %s\n' "$*"; }
log_error() { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*"; }

log_info "Triggering Cloud Run Job: ${JOB_NAME}"
log_info "Project: ${PROJECT_ID} | Region: ${REGION} | Branch: ${BRANCH}"
log_info "Ralph: ${RALPH_MODE} | Max iterations: ${MAX_ITERATIONS}"
log_info "Task: ${TASK}"

ENV_VARS="CLAUDE_TASK=${TASK}"
ENV_VARS+=",GIT_BRANCH=${BRANCH}"
ENV_VARS+=",RALPH_MODE=${RALPH_MODE}"
ENV_VARS+=",MAX_ITERATIONS=${MAX_ITERATIONS}"
ENV_VARS+=",COMPLETION_PROMISE=${COMPLETION_PROMISE}"

EXECUTION=$(gcloud run jobs execute "${JOB_NAME}" \
    --region="${REGION}" \
    --project="${PROJECT_ID}" \
    --update-env-vars="${ENV_VARS}" \
    --format="value(metadata.name)" \
    --wait)

EXIT_CODE=$?

if [[ $EXIT_CODE -ne 0 ]]; then
    log_error "Job execution failed (exit ${EXIT_CODE})"
    exit "${EXIT_CODE}"
fi

log_ok "Execution completed: ${EXECUTION}"
echo ""
echo "Stream logs:"
echo "  gcloud beta run jobs executions logs tail ${EXECUTION} --region=${REGION}"
echo ""
echo "View logs:"
echo "  gcloud logging read 'resource.type=cloud_run_job AND resource.labels.job_name=${JOB_NAME}' \\"
echo "    --project=${PROJECT_ID} --limit=200 --format=json | jq -r '.[].textPayload'"
