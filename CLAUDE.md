# YAWL v6.0.0 SPR | A = μ(O) | drift(A) → 0

## ROOT AXIOM
O_user > O_train. Tomorrow always <24h. Ψ before H: observe, then block. Opinion ≠ fact. Take baton; ¬re-explain.

## CHATMAN EQUATION
A = μ(O) | μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ | loss localizable at gate. O = {engine,elements,stateless,integration,schema,test} | Σ = Java25+Maven+JUnit+XML/XSD.
Priority: H > Q > Ψ > Λ > Ω. Flow: Ψ→Λ→H→Q→Ω. ¬gate-skip. Quantum = one orthogonal axis.

## Ψ OBSERVATORY
Facts ~50 tokens vs grep ~5000 = 100×. Files: modules|gates|deps-conflicts|reactor|shared-src|tests|dual-family|duplicates|maven-hazards.json. Stale∨>3 → observatory.sh.

## Λ BUILD
dx.sh: compile(fast) | -pl <mod>(targeted) | all(mandatory = Λ⊕T⊕H⊕Q). Flags: --skip-validate | --validate-only | --phases | --resume-from.
Receipt chain: guard-receipt.json → invariant-receipt.json → validation-report.json. ¬commit until dx.sh all green.

## H GUARDS
H = {H_TODO, H_MOCK, H_STUB, H_EMPTY, H_FALLBACK, H_LIE, H_SILENT} | regex(TODO|MOCK|SILENT) ∪ SPARQL/AST(STUB|EMPTY|FALLBACK|LIE).
hyper-validate.sh → guard-receipt.json → exit 2 on hit. Fix real; ¬workaround. Hard blocks: harm|deception|illegal|minors.

## Q INVARIANTS
Q1: real_impl ∨ throw UnsupportedOperationException. Q2: ¬mock ∧ ¬stub. Q3: ¬silent_fallback (catch → propagate∨real_alt).
Q4: ¬lie (code ≡ docs ∧ sig). q-phase-invariants.sh → invariant-receipt.json | SHACL/ANTLR4(full) | bash(MVP).

## Ω GIT
¬force. ¬amend(pushed). add <specific-files> only. Branch: claude/<desc>-<sessionId>. emit={src/,test/,schema/,.claude/} | ⊗={root,docs/,*.md} ask.

## τ TEAMS + μ AGENTS + Γ ARCHITECTURE
Teams: N ∈ {2..5}, 200K ctx. Idle>30min→crash | task>2h→reassign | msg>15min→[URGENT]. PostTeam: dx.sh all → atomic. ref: TEAMS-GUIDE.md.
Agents: engineer|validator|architect|integrator|reviewer|tester|prod-val|perf-bench (.claude/agents/).
Entry: YEngine|YStatelessEngine|YSpecification|YawlMcpServer|YawlA2AServer. 89 packages → package-info.java first.

## ι INTELLIGENCE
δ(A,B) = Vec<Delta> | receipt = blake3(canonical_json(δ)) → receipts/. scout→live-intelligence.md | inject→UserPromptSubmit.
¬line_diff ∧ ¬patch. DeclKind ∈ {Fn,Type,Const,Import,Module,Field}. Tickets: .claude/jira/tickets/*.toml.

## R RULES (auto-activate by path)
teams/**→TEAMS-GUIDE | pom.xml→dx-workflow+maven-modules | schema/**|*.xsd→xsd-validation | **/*.java→modern-java
**/test/**→chicago-tdd | scripts/**→shell-conventions | yawl/engine/**→workflow-patterns+interfaces | Dockerfile*→container

## φ WORKFLOW | π LIFECYCLE | κ PRINCIPLES
plan(t) iff |steps|≥3∨arch; sideways→replan; ¬push-through. done⟺proved∧tests_green∧approved. σ=offload(research∪explore).
plan→implement→track→explain(Δ)→lessons. |Δcode|→min; root_cause; ¬temp_fix; ¬bugs. Unsure emit vs ⊗ → ask.

## STOP | SKILLS | REFS
Unknown→modules.json | >3files→observatory.sh | ctx>70%→checkpoint | "for now"→throw UnsupportedOperationException.
/yawl-build|/yawl-test|/yawl-validate|/yawl-deploy|/yawl-review|/yawl-integrate|/yawl-spec|/yawl-pattern
.claude/HYPER_STANDARDS.md | .claude/OBSERVATORY.md | .claude/ARCHITECTURE-PATTERNS-JAVA25.md
GODSPEED ✈️ | Ψ→Λ→H→Q→Ω | Compile ≺ H-Guards ≺ Q-Invariants ≺ Git | drift(A) → 0
