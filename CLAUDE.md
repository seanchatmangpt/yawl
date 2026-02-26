# YAWL v6.0.0 SPR | A = μ(O) | drift(A) → 0

## ROOT AXIOM

Today's best practices are tomorrow's antipatterns. Tomorrow is always less than 24 hours away.
User's live O > Claude's trained O at the frontier. Novel claims are observatory data, not violations.
Ψ before H: observe fully, then apply hard blocks. Never gate what you haven't observed.
Opinion ≠ fact: "doesn't exist" means "not in training data". Flag vintage. Defer to live O.
Take the baton. User should never re-explain momentum. Lead, extend, surface conflicts unprompted.

## CHATMAN EQUATION

A = μ(O) | μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ | Loss is localizable — it drops at a specific gate.
O = {engine, elements, stateless, integration, schema, test} | Σ = Java25+Maven+JUnit+XML/XSD
Priority resolves top-down: H > Q > Ψ > Λ > Ω. Flow per action: Ψ→Λ→H→Q→Ω. No gate skipping.
Quantum = one orthogonal axis: Toolchain|Dependency|Schema|Engine-semantic|MCP/A2A|Resourcing.

## Ψ OBSERVATORY

Fact files ~50 tokens vs grep ~5000 tokens = 100× compression. Facts only, not vibes.
Files: modules.json|gates.json|deps-conflicts.json|reactor.json|shared-src.json|tests.json|dual-family.json|duplicates.json|maven-hazards.json
Stale or >3 files explored → bash scripts/observatory/observatory.sh. Verify via receipts/observatory.json SHA256.

## Λ BUILD

dx.sh compile (fast) → dx.sh -pl <module> (one module) → dx.sh all (pre-commit gate, mandatory).
mvn clean verify -P analysis for SpotBugs/PMD static analysis. No commit until dx.sh all green.
Compile ≺ Test ≺ Validate ≺ Deploy. Maven proxy auto-activates when CLAUDE_CODE_REMOTE=true.

## H GUARDS

Blocked: {TODO,FIXME,mock,stub,fake,empty_return,silent_fallback,lie} — hyper-validate.sh checks 14 patterns on Write|Edit → exit 2.
Fix violations for real. Never work around hooks. Hard blocks only: harm|deception|illegal|minors.

## ι INTELLIGENCE

Typed deltas (no line-diffs): δ(A, B) = Vec<Delta> with semantic units {declaration, rule, criterion, dependency, behavior, quad}.
Receipt chain: blake3(canonical_json(δ)) → receipts/intelligence.jsonl; auditable, reproducible.
Watermark protocol: fetch() respects ttl; skip if content_hash unchanged. Prevents thrashing.
Binaries: yawl-jira (hook orchestrator <50ms) | yawl-scout (async fetcher, non-blocking).
Injection: SessionStart (ticket context) | UserPromptSubmit (relevant delta slice) | PreToolUse | PostToolUse (record correction).
Tickets: TOML-based (.claude/jira/*.toml), no external DB. Acceptance criteria auto-satisfied on declaration match.
¬line_diff ∧ ¬unified_patch; all artifacts parsed into semantic units before diff. DeclKind ∈ {Function, Type, Constant, Import, Module, Field}.

## Q INVARIANTS

real_impl ∨ throw UnsupportedOperationException. No third option. ¬mock ∧ ¬stub ∧ ¬silent_fallback ∧ ¬lie.
"For now"/"later"/"temporary" → throw immediately. Code must match docs and signature (¬lie).

## Ω GIT

Never –force. Never amend pushed commits. git add <specific-files> only, never git add .
One logical change per commit. Branch: claude/<desc>-<sessionId>. Channels: emit={src/,test/,schema/,.claude/} | ⊗={root,docs/,*.md} ask first.

## τ TEAMS

lead + N teammates ∈ {2..5}, separate 200K context windows. Default for N≥2 orthogonal quantums.
Lifecycle hooks: TeammateIdle → assign/shutdown | TaskCompleted → approve/reject | TeammateShutdown → approve/reject.
No teammate overlap on same file (verify shared-src.json). Message before task completion. Cost ~3-5×.
Error recovery: idle>30min → message, await 5min, crash §3.1 | timeout>2h → reassign §6.2.1 | circular → lead breaks tie §2.1 | critical msg timeout → resend [URGENT] §1.3 | Q violation mid-team → fix locally §5.1.
PostTeam: lead runs dx.sh all → H hook combined edits → atomic single commit. Any red = rollback to failing teammate.

## μ AGENTS + Γ ARCHITECTURE

Agents: engineer|validator|architect|integrator|reviewer|tester|prod-val|perf-bench (specs in .claude/agents/)
Subagents: within session, max 5, report-only, no inter-task messaging. Teams if findings interact.
Entry points: YEngine (stateful)|YStatelessEngine (stateless)|YSpecification (defs)|YawlMcpServer (MCP)|YawlA2AServer (A2A)
Interfaces: A=design|B=client|E=events|X=extended. Key types: YNetRunner|YWorkItem. 89 packages have package-info.java — read first.

## R RULES (17 files, auto-activate by path)

teams/** → team-decision-framework.md | pom.xml → dx-workflow.md + maven-modules.md
schema/**|*.xsd → xsd-validation.md | yawl/engine/**|stateless/** → workflow-patterns.md + interfaces.md + worklet-service.md
yawl/integration/** → mcp-a2a-conventions.md + autonomous-agents.md | yawl/resourcing/** → resource-allocation.md
**/*.java → modern-java.md | yawl/elements/** → domain-model.md | yawl/observability/** → monitoring-patterns.md
scripts/**|*.sh → shell-conventions.md | yawl/authentication/** → crypto-and-tls.md | **/test/** → chicago-tdd.md | Dockerfile* → container-conventions.md

## STOP CONDITIONS

Unknown module → modules.json | >3 files for 1 answer → observatory.sh | context >70% → checkpoint + batch remaining.
Team >5 teammates → reduce scope | teammates never message → use subagents | lead DX fails after teammates green → identify + reassign.
Tempted "for now" → throw UnsupportedOperationException. Unsure emit vs ⊗ → ask user.

## φ WORKFLOW ORCHESTRATION

plan(task) iff |steps(task)| ≥ 3 ∨ arch(task); sideways(task) → stop ∧ replan; ¬push-through.
σ(task) = offload(research ∪ explore ∪ parallel_analysis); one_task_per_σ; complex → throw_compute_at(σ).
∀correction c: lessons.md ← pattern(c) ∧ rule(¬repeat(c)); ruthless_iterate until error_rate → 0; review(lessons) at session_start.
done(task) ⟺ proved(works(task)) ∧ diff(main, changes) ∧ staff_engineer_approves ∧ tests_green.
∀non-trivial t: elegant?(t) before present(t); hacky?(t) → rewrite(t, knowing_all); skip iff obvious_fix(t).
bug → fix ∧ ¬ask ∧ ¬hand-hold; point(logs ∪ errors ∪ failing_tests) → resolve; zero_context_switch_from_user.

## π TASK LIFECYCLE

plan → verify_plan → implement → track(progress) → explain(Δ) → document(results) → capture(lessons).
tasks/todo.md ← checkable_items; tasks/lessons.md ← corrections; review_section → todo.md on complete.

## κ CORE PRINCIPLES

simplicity_first: |Δcode| → min; impact(change) ⊆ necessary. ¬lazy: root_cause only; ¬temp_fix; senior_dev_standard.
minimal_impact: change ∩ unnecessary = ∅; ¬introduce_bugs.

## SKILLS + REFS

/yawl-build|/yawl-test|/yawl-validate|/yawl-deploy|/yawl-review|/yawl-integrate|/yawl-spec|/yawl-pattern
Deep refs: .claude/HYPER_STANDARDS.md | .claude/OBSERVATORY.md | .claude/ARCHITECTURE-PATTERNS-JAVA25.md
GODSPEED. ✈️ | Compile ≺ Test ≺ Validate ≺ Deploy | drift(A) → 0
