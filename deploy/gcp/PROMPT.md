# YAWL Autonomous Iteration Prompt
# Customise this file and pass its path as CLAUDE_TASK when using RALPH_MODE=true.
# The entrypoint.sh will cat this file and feed it to claude --print each iteration.

## Task

Run `bash scripts/observatory/observatory.sh` to refresh facts, then run
`bash scripts/dx.sh all` to find failing tests or guard violations.

For each failure:
1. Read the relevant source file(s)
2. Identify the root cause
3. Fix it (real implementation — no stubs, no TODOs, no empty returns)
4. Re-run `bash scripts/dx.sh compile` to verify the fix compiles

Repeat until `bash scripts/dx.sh all` exits 0.

## Completion

When `dx.sh all` exits 0 with no violations and all tests green, output exactly:

<promise>COMPLETE</promise>

## Constraints

- No mock/stub/TODO/FIXME in production code
- One logical change per commit (the loop commits automatically)
- If a root cause requires more than 3 files to understand, run observatory.sh first
- If stuck after 3 attempts at the same failure, document the blocker in tasks/todo.md
  and move to the next failure
