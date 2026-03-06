---
name: ralph-loop
version: 1.0.0
description: |
  Self-referential iterative task completion with YAWL validation integration.
  Feeds prompts back to Claude until a completion promise is satisfied.
tags:
  - workflow
  - iterative
  - validation
---

# Ralph Loop - Iterative Task Completion

Initiates a self-referential loop that continues working on a task until a completion promise is satisfied and validation passes.

## Usage

```
/ralph-loop "Task description" --completion-promise "PROMISE" [--max-iterations N]
```

## Parameters

- **Task description** (required): The task to complete
- **--completion-promise** (required): Text that signals task completion
- **--max-iterations** (optional): Maximum loop iterations (default: 50)

## How It Works

1. Claude receives the task and begins work
2. When done, Claude outputs the completion promise
3. Stop-hook intercepts and validates the work
4. If validation fails, prompt is re-injected with errors
5. Loop continues until validation passes or max iterations reached

## YAWL Smart Validation

When in YAWL context:
- Auto-detects YAWL environment (pom.xml + scripts/dx.sh)
- Runs `dx.sh all` for validation
- Skips validation on iteration 1 if baseline is GREEN
- Re-injects validation errors as new prompts

## Examples

```
# Basic usage
/ralph-loop "Fix all guard violations" --completion-promise "COMPLETE"

# With iteration limit
/ralph-loop "Implement feature X" --completion-promise "DONE" --max-iterations 20

# YAWL validation loop
/ralph-loop "Fix failing tests" --completion-promise "ALL_TESTS_PASS"
```

## Related

- `/cancel-ralph` - Cancel an active loop
- `scripts/dx.sh` - YAWL validation pipeline
