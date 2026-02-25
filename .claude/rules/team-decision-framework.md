# Team Decision Framework — When and How to Use Teams

**Auto-activates for**: `teams/**` paths
**Full reference**: `.claude/rules/TEAMS-GUIDE.md`

---

## Decision Tree

```
Does task have N ∈ {2,3,4,5} orthogonal quantums?
├─ YES + teammates need to message/iterate? → TEAM (τ)
├─ YES + quantums are purely independent?   → SUBAGENTS (μ)
├─ YES + team size > 5?                     → SPLIT into phases
├─ NO (single quantum)?                     → SINGLE SESSION
└─ UNSURE? → bash .claude/hooks/team-recommendation.sh "task"
```

---

## YAWL Team Patterns

| Pattern | Teammates | Use Case | Cost | ROI |
|---------|-----------|----------|------|-----|
| **Engine Investigation** | 3 | YNetRunner deadlock debugging | $3-4C | 2-3× faster |
| **Schema + Implementation** | 2 | Add workflow attribute (SLA) | $2-3C | Avoids back-and-forth |
| **Code Review by Concern** | 3 | Security + perf + testing | $2-3C | Cross-cutting issues |
| **Observability** | 2 | Add metrics to YNetRunner | $2-3C | Production debugging |
| **Performance Optimization** | 2-3 | Task queue tuning | $3-4C | 3× throughput |
| **Agent Integration** | 2-3 | MCP/A2A endpoint | $3-4C | Real-time monitoring |

---

## Anti-Patterns (Teams are WRONG here)

| Anti-Pattern | Problem | Solution |
|--------------|---------|----------|
| Single quantum | 3-5× cost, no benefit | Single session |
| No inter-team messaging | Just parallel isolation | Subagents |
| >5 teammates | Coordination overhead | Split into 2-3 phases |
| <30 min per task | Setup cost > task | Combine into single session |
| Same-file edits | No MVCC, overwrites | Refactor ownership |
| Tightly coupled logic | Teammates block each other | Reorder tasks |

---

## Formation Checklist

- [ ] Run `team-recommendation.sh` hook
- [ ] N teammates ∈ {2,3,4,5}
- [ ] Each task ≥30 min
- [ ] No file conflicts: check `Ψ.facts/shared-src.json`
- [ ] Teammates will message/iterate (not just run independently)
- [ ] Facts current: `bash scripts/observatory/observatory.sh`
- [ ] Team feature enabled: `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`

---

## Key Timeouts

| Timeout | Duration | Action |
|---------|----------|--------|
| Teammate idle | 30 min | Message, wait 5 min → reassign |
| Task completion | 2 hours | Extend +1h or split |
| Critical message | 15 min | Resend [URGENT] → crash |
| Lead offline → ZOMBIE | >5 min | Auto-checkpoint, continue locally |

---

## STOP Conditions

- Circular dependency → halt, lead breaks tie via ordering
- Teammate idle >30 min → message, reassign if no response
- Lead DX fails → halt consolidation, fix incompatibility
- Hook violation mid-task → teammate fixes locally before GREEN

**Full error recovery protocol**: `.claude/rules/TEAMS-GUIDE.md#error-recovery`
**Session resumption protocol**: `.claude/rules/TEAMS-GUIDE.md#session-resumption`
