# YAWL Teams Quick Reference

---

## Decision Tree (Simplified)

```
Does task have N ∈ {2,3,4,5} orthogonal quantums?
├─ YES + need messaging/iteration? → TEAM (τ)
├─ YES + independent work only? → SUBAGENTS (μ)
├─ YES + >5 teammates? → SPLIT into phases
├─ NO → SINGLE SESSION
└─ UNSURE → bash .claude/hooks/team-recommendation.sh "task"
```

---

## Key Timeout Values

| Timeout Type | Duration | Action |
|--------------|----------|--------|
| **Teammate idle** | 30 min | Message, wait 5 min → reassign |
| **Task completion** | 2 hours | Extend +1 hour or split → reassign |
| **Critical message** | 15 min | Resend [URGENT], wait 5 min → crash |
| **Heartbeat fresh** | <10 min | Online (can message) |
| **Heartbeat stale** | 10-30 min | Idle (send wake-up) |
| **Heartbeat dead** | >30 min | Offline (reassign) |
| **ZOMBIE mode** | >5 min | Auto-checkpoint, continue locally |
| **Resume timeout** | 20 sec total | Auto-detect failure |

---

## Recovery Actions Summary

### Immediate Actions (<5 min)
1. **Message timeout**: Resend with [URGENT] prefix
2. **Teammate offline**: Reassign task (6.2.1 protocol)
3. **Hook violation**: Message teammate to fix in 10 min
4. **Circular dep**: Break tie via task ordering

### Medium Actions (5-30 min)
1. **Task timeout**: Split into subtasks or reassign
2. **Lead DX fail**: Fix incompatibility, retry consolidation
3. **Network partition**: Retry message or reassign
4. **Teammate crash**: Load checkpoint, reassign

### Complex Actions (>30 min)
1. **Unrecoverable failure**: Post-mortem + retry/rollback
2. **Partial consolidation**: Abort or retry with fixes
3. **Clock skew**: Switch to sequence-based ordering

---

## Team Patterns Quick Reference

### Pattern 1: Engine Investigation (3 engineers)
- **Use**: Debug complex issues (e.g., YNetRunner deadlock)
- **Flow**: State machine → concurrency → guard logic
- **Cost**: $3-4C | **ROI**: 2-3× faster convergence

### Pattern 2: Schema + Implementation (2 engineers)
- **Use**: Cross-layer changes (e.g., add SLA tracking)
- **Flow**: Schema defines → implements → validates
- **Cost**: $2-3C | **ROI**: Avoids iterations

### Pattern 3: Code Review by Concern (3 reviewers)
- **Use**: Parallel review (security + performance + testing)
- **Flow**: Independent review → synthesize findings
- **Cost**: $2-3C | **ROI**: Catches cross-cutting issues

### Pattern 4: Observability (2 engineers)
- **Use**: Add metrics/tracing to YAWL engine
- **Flow**: Design schema → integrate → validate
- **Cost**: $2-3C | **ROI**: Production debugging

### Pattern 5: Performance Optimization (2-3 engineers)
- **Use**: Critical subsystem optimization
- **Flow**: Profile → optimize → test → validate
- **Cost**: $3-4C | **ROI**: 3× throughput gain

### Pattern 6: Autonomous Agent Integration (2-3 engineers)
- **Use**: Add MCP/A2A endpoints
- **Flow**: Design schema → implement → test protocol
- **Cost**: $3-4C | **ROI**: Real-time case monitoring

---

## STOP Conditions (HALT immediately)

| Condition | Action | Resolution |
|-----------|--------|-----------|
| **Circular dependency** | Halt formation | Lead breaks tie |
| **Teammate timeout** (30 min) | Message, wait 5 min | Reassign if no response |
| **Task timeout** (2+ hours) | Message status | Reassign if no response |
| **Message timeout** (15 min) | Resend [URGENT] | Crash if still no |
| **Lead DX fails** | Halt consolidation | Fix incompatibility |
| **Hook violation** | Halt build | Fix locally |
| **Teammate crash** (>5 min) | Halt task | Reassign or save work |
| **Message delivery fail** | Detect via gap | Resend + ACK |
| **Unrecoverable failure** | Halt team | Post-mortem |

---

## Message Protocol

### Lead → Teammate
```json
{
  "sequence": 42,
  "timestamp": "2026-02-20T14:25:45Z",
  "from": "lead",
  "to": "tm_1",
  "payload": "task description",
  "timeout": "15min"
}
```

### Teammate → Lead (ACK)
```json
{
  "ack_sequence": 42,
  "status": "received|in_progress|complete"
}
```

### Key Rules
- **Ordering**: FIFO per teammate (sequence numbers)
- **Lost messages**: Resend after 30 sec no ACK
- **Duplicates**: Silent ACK if already processed
- **Idempotency**: Messages should be safe to re-execute

---

## Session Resumption Commands

```bash
# Resume specific team
claude ... --resume-team τ-engine+schema+test-iDs6b

# List available teams
claude ... --list-teams

# Check if team is alive
claude ... --probe-team τ-engine+schema+test-iDs6b
```

### Resume Timeline (<20 sec total)
1. Load metadata (0.5s)
2. Verify team status (1s)
3. Validate lead session (1s)
4. Probe teammates (2-10s)
5. Load mailbox (2s)
6. Show summary (1s)

---

## Quick Checklist

### Before Team Formation
- [ ] Run team-recommendation.sh hook
- [ ] 2-5 teammates only
- [ ] Each task ≥30 min
- [ ] No file conflicts
- [ ] Facts current via observatory

### During Team Execution
- [ ] Send regular status messages
- [ ] Run local dx.sh + hook before declaring GREEN
- [ ] Share API contracts in messaging phase
- [ ] Monitor teammate heartbeats

### After Team Failure
- [ ] Preserve state with checkpoint commit
- [ ] Document root cause
- [ ] Decide retry vs rollback
- [ ] Update metrics for tuning

---

## Glossary

- **Team ID**: UUID (τ-quantums-sessionId)
- **Quantum**: Work domain (engine, schema, test, etc.)
- **Teammate**: Collaborating agent (1 per quantum)
- **Task**: Atomic work unit per teammate
- **Mailbox**: Append-only message log (JSONL)
- **Checkpoint**: Snapshot of team state
- **ZOMBIE mode**: Continue work when lead offline
- **Consolidation**: Lead final phase (compile + commit)
