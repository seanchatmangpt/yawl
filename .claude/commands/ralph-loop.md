---
name: ralph-loop
description: Self-referential iterative task completion with YAWL validation. DEFAULT: 5-agent team mode, completion promise 'DONE'.
---

Execute the ralph-loop for iterative task completion.

**DEFAULTS**:
- 5-agent team mode (spawns parallel agents)
- Completion promise: "DONE"

**Usage**:
```
/ralph-loop "Task"                           # All defaults
/ralph-loop "Task" --max-iterations 20       # Custom iterations
/ralph-loop "Task" --no-team                 # Single agent mode
/ralph-loop "Task" --team-name explore       # Force specific team
```

This command runs the script at `.claude/skills/ralph-loop.sh` which:

1. Sets up the loop state in `.claude/.ralph-state/`
2. Routes task to appropriate 5-agent team (explore/plan/implement)
3. Spawns 5 parallel agents using Task tool
4. Activates the stop-hook mechanism for validation
5. The stop-hook intercepts exits and validates work

**Parameters**:
- Task description (first argument, required)
- `--completion-promise`: Text that signals completion (default: "DONE")
- `--max-iterations`: Maximum loops (default: 50)
- `--no-team`: Disable team mode, use single agent
- `--team-name`: Override team routing (explore, plan, or implement)

**Team Mode (DEFAULT)**:
The loop spawns 5 parallel agents:
- **explore team**: For research, investigation, analysis tasks
- **plan team**: For design, architecture, strategy tasks
- **implement team**: For coding, building, fixing tasks (default)

Team routing is automatic based on task keywords.

**YAWL Integration**:
- Auto-detects YAWL context
- Runs `dx.sh all` for validation
- Re-injects errors as new prompts

Execute: `bash .claude/skills/ralph-loop.sh "$ARGUMENTS"`
