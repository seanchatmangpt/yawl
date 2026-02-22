# YAWL v6.0.0 SPR | A = μ(O) | drift(A) → 0 | Java25+Maven+JUnit+XML/XSD
Priority: H ≻ Q ≻ Ψ ≻ Λ ≻ Ω | Flow: Ψ→Λ→H→Q→Ω | No gate skip | Hook=autopilot

[Ψ Observatory — 100× token efficiency over grep]
facts/*.json ≈ 50 tokens each; grep ≈ 5000 tokens — always use facts, never raw grep
Key: modules.json | gates.json | reactor.json | shared-src.json | tests.json | dual-family.json
Stale? → bash scripts/observatory/observatory.sh | Verify: receipts/observatory.json SHA256

[Λ Build — compile ≺ test ≺ validate ≺ deploy]
dx.sh compile (fastest) | dx.sh -pl <module> (one module) | dx.sh all (pre-commit gate)
No commit until dx.sh all GREEN | Web env: Maven proxy auto-active 127.0.0.1:3128

[H Guards — hyper-validate.sh, exit 2 on any violation]
H = ¬{TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
ONLY valid: real implementation ∨ throw UnsupportedOperationException — zero exceptions

[Q Invariants — non-negotiable laws]
real_impl ∨ throw | ¬mock | ¬silent_catch | code ≡ docs ≡ signature — all must hold
"for now"/"later"/"placeholder" → UnsupportedOperationException unconditionally

[Quantum Singularity — one axis per commit]
Pick ONE: Toolchain | Dependency | Schema | Engine semantic | MCP/A2A | Resourcing
Multi-axis = drift ↑ | Split scope or spawn team before crossing two axes simultaneously

[Ω Git — zero-force policy]
Never --force | Never amend pushed commits | git add <specific-files> (never git add .)
Branch: claude/<desc>-<sessionId> | git push -u origin <branch> | One logical change/commit

[Channels]
emit (modify freely): src/ | test/ | schema/ | .claude/
⊗ (ask user first before touching): root/ | docs/ | *.md

[τ Teams vs μ Subagents vs Single Session]
τ: N∈{2..5} orthogonal quantums; teammates message+iterate; 3-5×cost; DEFAULT for N≥2
μ: N independent report-only tasks; within-session; ~$C+summaries; no inter-agent messaging
Single: one quantum; ~$C; fastest; use when no collaboration benefit exists
Pre-team: bash .claude/hooks/team-recommendation.sh "task" | file conflicts: shared-src.json
τ teammate circuit: Ψ(claim task) → Λ(local DX) → H(hook) → Q(real impl) → message → Ω(done)

[Γ Architecture — entry points and domains]
Stateful: YEngine | YNetRunner | YWorkItem | YSpecification
Stateless: YStatelessEngine | YCaseMonitor | YCaseImporter | YCaseExporter
Integration: YawlMcpServer (MCP) | YawlA2AServer (A2A)
89 packages, each with package-info.java — read package-info first, always

[R Rules — 17 path-scoped files in .claude/rules/, auto-activate on first file touch]
teams/ | build/ | schema/ | engine/ | integration/ | java25/ | elements/ | testing/ | security/
Quantum→Rule: schema→schema/xsd-validation.md | engine→engine/workflow-patterns.md | MCP→integration/mcp-a2a-conventions.md

[μ(O) Subagent Types — .claude/agents/]
Generic: engineer | reviewer | validator
YAWL: yawl-architect | yawl-engineer | yawl-integrator | yawl-reviewer | yawl-tester | yawl-validator | yawl-production-validator | yawl-performance-benchmarker
Task(a₁..aₙ) in single_message | max 5 parallel | results summarized back to lead

[PreToolUse Checklist — run before ANY tool call]
Ψ: facts stale? | N≥2 quantums? → team-recommendation.sh | H: hook blocks? | Q: real impl? | Ω: emit channel?
PostToolUse: hook green → proceed | hook blocked → fix real code → NEW commit (never amend)

[STOP Conditions — halt+re-anchor; any violation = drift ↑]
Module unknown for code? → modules.json | >3 files for 1 answer? → observatory.sh
N≥2 quanta ambiguous? → team-recommendation.sh | Team >5 teammates? → split into phases
Hook blocked Write/Edit? → fix real violation, no workarounds | Context >70%? → checkpoint+batch
Ω uncertain (emit vs ⊗)? → ask user before touching | "for now" temptation? → throw UnsupportedOperationException
