# YAWL v6.0.0 | A := μ(O) | ∂drift(A)/∂t → 0 | Σ = {Java25, Maven, JUnit, XML/XSD} | O ⊨ PetriNet+BPM

## Π (Priority Lattice — resolve conflicts by argmax over ordered set)
⊤ := H ≻ Q ≻ Ψ ≻ Λ ≻ Ω | ∀conflict(x,y): resolve := argmax_{i∈⊤}(⊤[i] ∋ {x,y})
Circuit: Ψ ∘ Λ ∘ H ∘ Q ∘ Ω | □(¬skip(gate_i)) ∀i | hook ≡ autopilot ∈ {PreToolUse, PostToolUse}
Uncertainty → stop ∧ re-read(⊤) | ¬proceed without gate resolution

## Ψ := Observatory(O) | I(Ψ)/I(grep) = 100 | token_cost(facts) ≪ token_cost(search)
∀f ∈ facts/*.json: cost(f) ≈ 50T | cost(grep_equiv) ≈ 5000T | ∴ ∀query: use(f) ∧ ¬grep
F := {modules.json, gates.json, reactor.json, shared-src.json, tests.json, dual-family.json, deps-conflicts.json, maven-hazards.json}
∀f ∈ F: stale(f) → exec(scripts/observatory/observatory.sh) | integrity: SHA256(f) ≡ receipts/observatory.json
|files_explored| > 3 for single answer → STOP ∧ run(observatory.sh) | facts ≺ exploration

## Λ := Build | partial_order: compile ≺ test ≺ validate ≺ deploy | ∀pre-commit: □(β(*) = GREEN)
β₀ := dx.sh compile | β(m) := dx.sh -pl m | β(*) := dx.sh all | β ∈ {β₀, β(m), β(*)}
¬∃commit: β(*) ≠ GREEN | web(CLAUDE_CODE_REMOTE=true) → proxy(127.0.0.1:3128) auto-active
static_analysis: mvn clean verify -P analysis | ∀module m: β(m) ≺ β(*) in DX loop

## H := Guards | enforcer: hyper-validate.sh | exit_code(H_violation) = 2 | H ∩ emit = ∅
H_set := {TODO, FIXME, XXX, HACK, LATER, mock, stub, fake, empty_return, silent_fallback, lie, placeholder}
∀c ∈ emit: H_set ∩ c = ∅ ∨ c ≡ throw(UnsupportedOperationException) | ¬∃ middle ground
hook: Write|Edit → scan(H_set) | blocked → fix_real(c) ∧ ¬workaround | 14-point detection regex

## Q := Invariants | ∀m ∈ methods: (real_impl(m) ∨ throws(UOE,m)) ∧ ¬mock(m) ∧ ¬silent_catch(m)
∀m: code(m) ≡ docs(m) ≡ signature(m) | deferred_work(m) → UOE(m) | ¬∃"for now" ∈ codebase
¬silent_fallback: catch(e){return fake} → prohibited | ¬lie: contract(m) ≡ impl(m) | real ∨ throw

## κ (Quantum Singularity) | ∀commit: |{axis(commit)}| = 1 | drift(A) ∝ |axes(commit)| − 1
κ ∈ {Toolchain, Dependency, Schema, Engine_semantic, MCP_A2A, Resourcing} | |κ_per_commit| = 1
|axes| ≥ 2 → split_scope ∨ spawn(τ) | ¬proceed with multi-axis in single session/commit
pick(κ) → read(Ψ.facts/modules.json) → read(R[κ]) → DX_loop → Ω

## Ω := Git | P(force_push) = 0 | P(amend_pushed_commit) = 0 | ∀stage: git_add(files) ≠ git_add(.)
branch_pattern := "claude/" ++ desc ++ "-" ++ sessionId | push: git push -u origin branch
∀commit: |logical_changes| = 1 | ∀f ∈ emit: modify_freely | ∀f ∈ ⊗: confirm(user) ∧ ¬assume
session_url ∈ commit_msg | ∀push_fail(network): retry(n) with backoff(2ⁿs), n ∈ {1,2,3,4}

## C (Channels) | emit := {src/, test/, schema/, .claude/} | ⊗ := {root/, docs/, *.md}
emit ∩ ⊗ = ∅ | f ∈ emit → free | f ∈ ⊗ → ask_user | f ∉ emit ∪ ⊗ → ask_user
PostWrite/Edit: hook_green → commit | hook_blocked → fix_real → NEW_commit (¬amend)

## τ ⊕ μ ⊕ ∅ (Agent Scaling — choose exactly one)
τ(N): N ∈ [2,5]∩ℤ, ∀i≠j: orthogonal(κᵢ,κⱼ), messaging+iteration, cost ≈ [3C,5C]; DEFAULT iff N≥2
μ(N): ∀tasks: independent ∧ report_only ∧ ¬messaging, within-session, cost ≈ C + Σᵢsummary(i)
∅: |κ|=1, cost ≈ C, fastest; use iff ¬∃collaboration_benefit ∧ ¬∃parallel_value
validate(τ): team-recommendation.sh "task" | ∀tm: shared-src.json → file_conflicts(tmᵢ ∩ tmⱼ) = ∅
τ_circuit(tmᵢ): Ψ(claim_task) ∘ β(mᵢ) ∘ H(hook) ∘ Q(real_impl) ∘ msg(teammates) ∘ Ω(mark_done)
τ_error: idle(30min) → msg(tm) | crash(5min) → checkpoint ∧ reassign | DX_fail → fix ∧ retry
τ_consolidation: β(*) ∧ H_clear → git_add(∪ᵢfiles(tmᵢ)) → atomic_commit → push

## Γ := Architecture | domains(O) = {engine, elements, stateless, integration, schema, test}
Stateful: YEngine | YNetRunner | YWorkItem | YSpecification → entry_points(E_stateful)
Stateless: YStatelessEngine | YCaseMonitor | YCaseImporter | YCaseExporter → entry_points(E_stateless)
Integration: YawlMcpServer(MCP) | YawlA2AServer(A2A) → entry_points(E_integration)
∀p ∈ packages: |p| = 89 ∧ package-info.java(p) ≠ ∅ | read(package-info) ≺ read(any_source)
Interfaces: A(design) | B(client) | E(events) | X(extended) | each enforces contract(Ω)

## R := Rules | |R| = 17 | path_match(f, Rᵢ) → auto_activate(Rᵢ) | ¬duplicate_in(CLAUDE.md)
R_domains = {teams/, build/, schema/, engine/, integration/, java25/, elements/, testing/, security/, observability/, scripts/, docker/, config/, resourcing/}
κ→Rᵢ: Toolchain→build/dx-workflow.md | Dependency→config/static-analysis.md | Schema→schema/xsd-validation.md
κ→Rᵢ: Engine→engine/workflow-patterns.md | MCP_A2A→integration/mcp-a2a-conventions.md | Resourcing→resourcing/resource-allocation.md
κ→Rᵢ: Security→security/crypto-and-tls.md | Testing→testing/chicago-tdd.md | Java25→java25/modern-java.md

## μ(O) := Subagents | .claude/agents/ | Task(a₁,...,aₙ) ∈ single_msg | ∀n: n ≤ 5
μ_generic := {engineer, reviewer, validator}
μ_yawl := {yawl-architect, yawl-engineer, yawl-integrator, yawl-reviewer, yawl-tester, yawl-validator, yawl-production-validator, yawl-performance-benchmarker}
μ_all := μ_generic ∪ μ_yawl | ∀aᵢ ∈ μ_all: isolated_context ∧ summary(result) → lead

## ℂ (PreToolUse) | □(checklist) before ∀tool_call ∈ {Bash, Task, Read, Glob, Grep, Write, Edit}
Ψ_gate: stale(facts)? → observatory.sh | N≥2κ? → team-recommendation.sh → τ ∨ μ ∨ ∅
H_gate: H_set ∩ content ≠ ∅? → fix_real | Q_gate: real_impl(m) ∨ throws(UOE,m)?
Ω_gate: f ∈ emit? → free | f ∈ ⊗? → confirm(user) | sessionId set? → branch_pattern valid?
PostToolUse: hook=green → proceed | hook=blocked → fix_real → NEW_commit (¬amend ¬workaround)

## Σ (STOP Conditions) | ∀s ∈ Σ: triggered(s) → halt ∧ re-anchor | ¬violation ⇒ drift(A) ↑
∄module(code) → read(Ψ.facts/modules.json) | |files_searched| > 3 → observatory.sh
N≥2κ ∧ ambiguous → team-recommendation.sh | |τ_teammates| > 5 → split_into_sequential_phases
hook_blocked(Write|Edit) → fix_real_violation ∧ ¬workaround | ctx_usage > 70% → checkpoint ∧ batch
f ∈ ⊗ ∧ uncertain → ask(user) ∧ ¬assume | deferred_work → throw(UnsupportedOperationException)
τ: idle_tm > 30min → msg ∧ await(5min) | task > 2hr → msg_status | msg_timeout > 15min → URGENT
