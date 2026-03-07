#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# YAWL Claude Code runner — supports single-shot and Ralph Wiggum loop modes
#
# Single-shot (default):
#   CLAUDE_TASK="fix the failing tests"  →  one claude --print, commit, push
#
# Ralph loop (RALPH_MODE=true):
#   Repeats claude --print up to MAX_ITERATIONS times, committing after each
#   iteration that produces changes, stopping early if COMPLETION_PROMISE
#   appears in Claude's output.
#
# Env vars:
#   ANTHROPIC_API_KEY      (required)
#   GIT_REPO_URL           (required)
#   CLAUDE_TASK            (required) — task or path to a PROMPT.md file
#   GIT_BRANCH             (default: master)
#   GIT_TOKEN              (optional HTTPS token)
#   GIT_USER_NAME          (default: Claude Code Bot)
#   GIT_USER_EMAIL         (default: claude-code@gcp.run)
#   RALPH_MODE             (default: false) — enable iterative loop
#   MAX_ITERATIONS         (default: 20)   — ralph loop safety limit
#   COMPLETION_PROMISE     (default: COMPLETE) — exact string signalling done
#   ANTHROPIC_MODEL        (default: claude-sonnet-4-6)
# ---------------------------------------------------------------------------

: "${ANTHROPIC_API_KEY:?ANTHROPIC_API_KEY is required}"
: "${GIT_REPO_URL:?GIT_REPO_URL is required}"
: "${CLAUDE_TASK:?CLAUDE_TASK is required}"

BRANCH="${GIT_BRANCH:-master}"
GIT_USER_NAME="${GIT_USER_NAME:-Claude Code Bot}"
GIT_USER_EMAIL="${GIT_USER_EMAIL:-claude-code@gcp.run}"
RALPH_MODE="${RALPH_MODE:-false}"
MAX_ITERATIONS="${MAX_ITERATIONS:-20}"
COMPLETION_PROMISE="${COMPLETION_PROMISE:-COMPLETE}"
ALLOWED_TOOLS="${ALLOWED_TOOLS:-Read,Edit,Write,Bash,Glob,Grep,Agent}"

log_info()  { printf '[INFO]  %s\n' "$*" >&2; }
log_ok()    { printf '[ OK ]  %s\n' "$*" >&2; }
log_warn()  { printf '[WARN]  %s\n' "$*" >&2; }
log_error() { printf '[ERROR] %s\n' "$*" >&2; }

# ---------------------------------------------------------------------------
# 1. Git setup
# ---------------------------------------------------------------------------
git config --global user.name  "${GIT_USER_NAME}"
git config --global user.email "${GIT_USER_EMAIL}"
git config --global credential.helper store

CLONE_URL="${GIT_REPO_URL}"
if [[ -n "${GIT_TOKEN:-}" ]]; then
    CLONE_URL="${GIT_REPO_URL/https:\/\//https://${GIT_TOKEN}@}"
fi

log_info "Cloning ${GIT_REPO_URL} branch=${BRANCH}"
git clone --depth=50 --branch "${BRANCH}" "${CLONE_URL}" /workspace/repo
cd /workspace/repo
git remote set-url origin "${CLONE_URL}"

# ---------------------------------------------------------------------------
# 2. Resolve task — inline string or path to a PROMPT.md file
# ---------------------------------------------------------------------------
if [[ -f "${CLAUDE_TASK}" ]]; then
    PROMPT="$(cat "${CLAUDE_TASK}")"
    log_info "Loaded prompt from file: ${CLAUDE_TASK}"
else
    PROMPT="${CLAUDE_TASK}"
fi

# ---------------------------------------------------------------------------
# 3. Commit helper — only commits when there are real changes
# ---------------------------------------------------------------------------
commit_changes() {
    local iteration="$1"
    local label="$2"

    if git diff --quiet && git diff --staged --quiet; then
        log_warn "Iteration ${iteration}: no changes — skipping commit"
        return 0
    fi

    local msg
    msg="claude(ralph/${iteration}): ${label:0:60}

Iteration ${iteration}/${MAX_ITERATIONS} via Ralph Wiggum loop.
ANTHROPIC_MODEL: ${ANTHROPIC_MODEL:-claude-sonnet-4-6}
GCP_JOB: ${CLOUD_RUN_JOB:-local}
GCP_EXECUTION: ${CLOUD_RUN_EXECUTION:-local}"

    git add -A
    git commit -m "${msg}"
    git push origin "${BRANCH}"
    log_ok "Iteration ${iteration}: changes committed and pushed"
}

# ---------------------------------------------------------------------------
# 4a. Single-shot mode
# ---------------------------------------------------------------------------
run_single() {
    log_info "Single-shot mode"
    local output
    output=$(claude \
        --print \
        --allowedTools "${ALLOWED_TOOLS}" \
        "${PROMPT}" 2>&1) || {
            log_error "claude exited non-zero"
            echo "${output}"
            exit 1
        }
    echo "${output}"
    commit_changes 1 "${PROMPT}"
    log_ok "Done."
}

# ---------------------------------------------------------------------------
# 4b. Ralph Wiggum loop — fresh context, sequential commits
# ---------------------------------------------------------------------------
run_ralph() {
    log_info "Ralph Wiggum loop: max=${MAX_ITERATIONS} promise='${COMPLETION_PROMISE}'"

    local iteration=0
    local completed=false

    # Append loop context to prompt so Claude knows it's iterating
    local loop_header
    loop_header="$(cat <<'HEADER'
You are running in an autonomous loop (Ralph Wiggum mode).
Each iteration you will see the current state of the codebase.
Read the git log and any previous work before acting.
When the task is fully complete, output exactly: <promise>COMPLETE</promise>
(or whatever completion promise was specified).
Do not stop early; iterate until the task is genuinely done.

---

HEADER
)"
    local full_prompt="${loop_header}${PROMPT}"

    while [[ $iteration -lt $MAX_ITERATIONS ]]; do
        iteration=$(( iteration + 1 ))
        log_info "=== Ralph iteration ${iteration}/${MAX_ITERATIONS} ==="

        local output
        output=$(claude \
            --print \
            --allowedTools "${ALLOWED_TOOLS}" \
            "${full_prompt}" 2>&1) || {
                log_error "Iteration ${iteration}: claude exited non-zero — aborting loop"
                echo "${output}"
                exit 1
            }

        echo "${output}"

        # Commit any work this iteration produced
        commit_changes "${iteration}" "iter-${iteration}: ${PROMPT:0:50}"

        # Check for completion promise
        if echo "${output}" | grep -qF "${COMPLETION_PROMISE}"; then
            log_ok "Completion promise detected after iteration ${iteration}: '${COMPLETION_PROMISE}'"
            completed=true
            break
        fi

        log_info "No completion promise yet — continuing..."
    done

    if [[ "${completed}" == "false" ]]; then
        log_warn "Max iterations (${MAX_ITERATIONS}) reached without completion promise."
        log_warn "Review activity and consider increasing MAX_ITERATIONS."
        exit 1
    fi

    log_ok "Ralph loop complete after ${iteration} iteration(s)."
}

# ---------------------------------------------------------------------------
# 5. Dispatch
# ---------------------------------------------------------------------------
if [[ "${RALPH_MODE}" == "true" ]]; then
    run_ralph
else
    run_single
fi
