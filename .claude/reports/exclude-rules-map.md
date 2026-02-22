# YAWL v6.0.0 — Exclude Rules Map

**Generated**: 2026-02-22
**Branch**: `claude/map-exclude-rules-9UicY`
**Purpose**: Single reference cataloguing every exclude/exclusion pattern in the codebase, its location, what it excludes, and why.

## Quick Classification

| Status | Meaning | Count |
|--------|---------|-------|
| **PERMANENT** | By design — generated code, test relaxation, env detection, arch decisions | ~50 |
| **TEMP-DEBT** | Tech debt — pre-existing complexity tracked in COVERAGE_IMPROVEMENT_PLAN.md | ~10 |
| **TEMP-OFFLINE** | Offline Maven cache workaround — re-enable when network/cache available | ~8 |
| **TEMP-LEGACY** | Deprecated code awaiting deletion | 1 |

---

## Layer 1: Version Control (`.gitignore`)

**File**: `/home/user/yawl/.gitignore`

### Build Output — PERMANENT
| Pattern | Why |
|---------|-----|
| `target/`, `*/target/` | Maven compiled output — regenerated every build |
| `*.class`, `*.jar`, `*.war`, `*.ear`, `*.nar` | Compiled bytecode and archives |
| `dependency-reduced-pom.xml` | Shade plugin temp POM |
| `.mvn/wrapper/maven-wrapper.jar` | Downloaded, not checked in |
| `maven_compile.log`, `maven*.log`, `build-log.txt` | Build diagnostic logs |
| `classes/`, `output/`, `temp/`, `build/logs/`, `build/3rdParty/` | Ant-era and temp build dirs |
| `ant_compile.log` | Legacy Ant build logs |
| `javac.*.args` | Incremental compilation state |

**Exception — PERMANENT**:
| Pattern | Why |
|---------|-----|
| `!lib/**/*.jar` | Legacy JARs still needed that predate Maven |

### IDE Configuration — PERMANENT
| Pattern | Why |
|---------|-----|
| `.idea/*` | IntelliJ personal settings (run configs, window layout) |
| `!.idea/codeStyles/` | **Committed**: shared team code style |
| `!.idea/inspectionProfiles/` | **Committed**: shared team inspection rules |
| `*.iml`, `*.ipr`, `*.iws`, `out/` | IDEA per-machine files |
| `.vscode/` | VS Code workspace state |
| `!.vscode/extensions.json` | **Committed**: recommended extensions |
| `!.vscode/settings.json` | **Committed**: shared workspace settings |
| `!.vscode/launch.json` | **Committed**: shared debug configurations |
| `.classpath`, `.project`, `.settings/` | Eclipse per-machine metadata |
| `/nbproject/private/`, `nbactions.xml` | NetBeans private state |

### OS Artifacts — PERMANENT
| Pattern | Why |
|---------|-----|
| `.DS_Store`, `._*`, `.Spotlight-V100` | macOS metadata |
| `Thumbs.db`, `ehthumbs.db`, `desktop.ini` | Windows metadata |

### Security — PERMANENT
| Pattern | Why |
|---------|-----|
| `.env`, `.env.*` | **CRITICAL**: contains credentials, API keys, JWT secrets — NEVER commit |
| `!.env.example`, `!.env.*.example` | **Committed**: shows required variable names without values |
| `*.tfstate`, `*.tfstate.backup` | Terraform state with cloud credentials |
| `*.tfvars` | Terraform variables (may contain secrets) |
| `!*.tfvars.example` | **Committed**: reference for required variables |

### Test Artifacts — PERMANENT
| Pattern | Why |
|---------|-----|
| `test-results/`, `surefire-reports/` | JUnit/Surefire XML results |
| `jacoco.exec`, `jacoco*.xml`, `*.exec` | JaCoCo coverage data |

### Generated Analysis Output — PERMANENT
| Pattern | Why |
|---------|-----|
| `spotbugsXml.xml`, `findbugs.xml` | SpotBugs generated reports |
| `dependency-check-report.*` | OWASP scanner output (may contain CVE details) |
| `docs/v6/latest/` | Observatory auto-generated analysis snapshots |
| `docs/v6/performance-history/` | Generated benchmark history |
| `docs/v6/static-analysis-history/` | Generated static analysis history |
| `performance-reports/*.json`, `performance-reports/*.csv` | Benchmark output |
| `checksums/*.sha256`, `checksums/*.md5` | Computed file hashes |

### Infrastructure State — PERMANENT
| Pattern | Why |
|---------|-----|
| `docker-volumes/` | Docker persistent volume data |
| `helm/*/charts/`, `helm/*/Chart.lock` | Downloaded Helm chart dependencies |
| `.terraform/` | Downloaded Terraform provider plugins |

### Schema — PERMANENT (with important exception)
| Pattern | Why |
|---------|-----|
| `exampleSpecs/xml/*.xsd` | Generated from YAWL spec definitions — derived, not source |
| `src/org/yawlfoundation/yawl/unmarshal/*.xsd` | Generated XSD — derived |
| NOT excluded: `schema/YAWL_Schema*.xsd` | **Official YAWL schemas — source of truth, committed** |

### Runtime Artifacts — PERMANENT
| Pattern | Why |
|---------|-----|
| `logs/`, `*.log` | Application runtime logs |
| `build-performance.log`, `build-timing-*.log` | Generated perf measurements |
| `node_modules/`, `__pycache__/`, `*.pyc` | Language package caches |
| `*.bak`, `*.backup`, `*.orig` | Editor backup files |
| `*.swp`, `*.swo`, `*~` | vim/emacs swap/temp files |

### Observatory Rust Build — PERMANENT
**File**: `/home/user/yawl/scripts/observatory/.gitignore`

| Pattern | Why |
|---------|-----|
| `target/` | Cargo compiled Rust binaries |
| `!src/bin/`, `!src/bin/*.rs` | **Explicit inclusion**: overrides root `.gitignore` `bin/` exclusion; Rust hook sources MUST be committed |

---

## Layer 2: Maven Build Profiles (`pom.xml`)

**File**: `/home/user/yawl/pom.xml`

### Surefire Test Group Excludes — PERMANENT

These define the test taxonomy. All permanent — the groups exist by intentional design.

| Profile | `<excludedGroups>` | Why | Activation |
|---------|-------------------|-----|-----------|
| `fast` | `integration,docker,containers` | Default CI — no Docker daemon required | **Default (activeByDefault)** |
| `agent-dx` | `integration,docker,containers,slow` | AI agent DX loop — max speed, skip all I/O-heavy tests | Manual |
| `quick-test` | `integration,docker,containers,slow,chaos` | Unit-only, ~10s total, `@Tag("unit")` only | Manual |
| `integration-test` | `containers,slow` | H2 integration, no Docker required | Manual |
| `smoke` | `docker,containers,slow` | Smoke tests — no Docker, no chaos | Manual |

**Test tag taxonomy** (from pom.xml comments, lines 2753–2758):
- `unit` (~131 tests): pure in-memory, no I/O, no DB
- `integration` (~53 tests): real engine/DB via H2, no Docker
- `slow` (~19 tests): perf benchmarks, ArchUnit scans, chaos
- `docker` (~3 tests): testcontainers (requires Docker daemon)
- `chaos` (~2 tests): network/failure injection tests

### Test Class Excludes — PERMANENT

**Location**: `pom.xml` lines 2905–2910 (under `integration-test` profile Surefire config)

| Excluded Class | Why |
|----------------|-----|
| `**/EngineTestSuite.java` | Suite runner re-executes the same classes — causes double-counting in Surefire's own discovery |
| `**/PatternMatchingTestSuite.java` | Same reason |

### Agent-DX Profile: Skipped Quality Tools — PERMANENT

**Location**: `pom.xml` lines 2710–2737

The `agent-dx` profile skips ALL quality checks to maximise compile-test speed:

| Skipped | Property | Why |
|---------|----------|-----|
| JaCoCo | `jacoco.skip=true` | No bytecode instrumentation overhead (saves 15–25%) |
| Checkstyle | `checkstyle.skip=true` | No style checking |
| SpotBugs | `spotbugs.skip=true` | No static analysis |
| PMD | `pmd.skip=true` | No static analysis |
| Enforcer | `enforcer.skip=true` | No dependency/environment rules |
| Javadoc | `maven.javadoc.skip=true` | No doc JAR generation |
| Source | `maven.source.skip=true` | No source JAR generation |

Also sets `surefire.threadCount=8` for maximum parallel test execution.

### Classpath Dependency Excludes — PERMANENT

**Location**: `pom.xml` lines 1382–1385, 2894–2896

| Excluded | Context | Why |
|----------|---------|-----|
| `org.apache.logging.log4j:log4j-to-slf4j` | Global default + `smoke` profile Surefire | Conflicts with `log4j-slf4j2-impl` already included — two SLF4J bridges on classpath cause `SLF4J: Class path contains multiple SLF4J bindings` |

### OWASP Dependency-Check Excludes — PERMANENT

**Location**: `pom.xml` lines 1726–1730

| Pattern | Why |
|---------|-----|
| `(?i).*test.*\.jar$` | Test-scope JARs — not deployed to production, risk accepted |
| `(?i).*test.*\.war$` | Same |

### ErrorProne Excluded Path — PERMANENT

**Location**: `pom.xml` line 2323 (compiler args)

```
-Xep:ExcludedPath:(^|.*/)(target/|build/).*
```

| Excluded | Why |
|----------|-----|
| `target/`, `build/` directories | Build output — ErrorProne runs on source only |

### Version Upgrade Excludes — PERMANENT

**Location**: `pom.xml` line 1539

```xml
<!-- Exclude alpha/beta/RC from upgrade suggestions by default -->
```

Versions Maven plugin excludes pre-release versions from upgrade recommendations.

---

## Layer 3: Module-Level Excludes — `yawl-monitoring/pom.xml`

**File**: `/home/user/yawl/yawl-monitoring/pom.xml`

These are the most significant temporary excludes — all exist because of **offline Maven cache gaps** in the CI/proxy environment.

### Compiler Source Excludes — MIXED

| Excluded | Status | Why |
|----------|--------|-----|
| `**/observability/OpenTelemetryInitializer.java` | TEMP-OFFLINE | Uses OpenTelemetry SDK internal APIs not on this module's classpath |
| `**/observability/OpenTelemetryConfig.java` | TEMP-OFFLINE | Requires `opentelemetry-exporter-prometheus:1.52.0-alpha` POM not in local Maven cache |
| `**/autonomous/observability/**` | TEMP-DEBT | Duplicate logger field compilation error — disabled pending fix |
| `**/*.xsd`, `**/*.xml`, `**/*.properties` | PERMANENT | Config/schema files — not Java source to compile |

### Test Compile Excludes — PERMANENT

28 package patterns from other modules are excluded from yawl-monitoring test compilation:
```
org/yawlfoundation/yawl/authentication/**
org/yawlfoundation/yawl/engine/**
org/yawlfoundation/yawl/integration/a2a/**
... (25 more)
```

**Why**: Shared source path issue — other modules' test classes were being picked up by this module's test compiler. Module-scoped test compilation requires explicit exclusion.

### Surefire Test Excludes — TEMP-OFFLINE

| Excluded | Why |
|----------|-----|
| `**/integration/observability/**` | Requires OkHttp HTTP client for network calls — not available offline |
| `**/observability/OpenTelemetryConfigTest.java` | References `OpenTelemetryConfig` excluded from compilation |
| `**/observability/ObservabilityTest.java` | Depends on `io.micrometer.prometheus` — not in local Maven cache |

### Commented-Out Dependencies — TEMP-OFFLINE

| Dependency | Why Disabled |
|------------|-------------|
| `opentelemetry-instrumentation-api:2.18.1` | Transitive POM not in local Maven cache; offline builds fail |
| `micrometer-registry-prometheus` | Requires `prometheus-metrics-exposition-textformats:1.4.3` — not cached |
| `opentelemetry-exporter-prometheus` | Version mismatch: project uses OTEL 1.52.0, exporter needs 1.59.0 |

### Spring Boot Actuator Transitive Exclusions — PERMANENT

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-actuator</artifactId>
    <exclusions>
        <exclusion>log4j-to-slf4j</exclusion>
        <exclusion>micrometer-observation</exclusion>
        <exclusion>micrometer-commons</exclusion>
    </exclusions>
</dependency>
```

| Excluded Transitive | Why |
|--------------------|-----|
| `log4j-to-slf4j` | Same SLF4J bridge conflict as global |
| `micrometer-observation` | Unwanted instrumentation overhead for optional Spring support |
| `micrometer-commons` | Not needed, reduces classpath bloat |

### Classpath Log4j Exclude — PERMANENT

Surefire `<classpathDependencyExclude>`: `org.apache.logging.log4j:log4j-to-slf4j` — same reason as global.

---

## Layer 4: SpotBugs Exclusions

### Root Exclude Filter (`spotbugs-exclude.xml`) — PERMANENT unless noted

**File**: `/home/user/yawl/spotbugs-exclude.xml`

#### Java 25 / Language Feature False Positives — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| `BC_VACUOUS_INSTANCEOF`, `BC_UNCONFIRMED_CAST`, `BC_UNCONFIRMED_CAST_OF_RETURN_VALUE` | Inner/anon classes (`.*\$.*`) | Java 25 record patterns and pattern matching for switch — SpotBugs doesn't fully model new semantics |

#### Generated Code — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| ALL | `ObjectFactory`, `package-info`, `jaxb.*` classes | JAXB-generated — not maintained manually |
| ALL | `$$_hibernate_.*` classes | Hibernate proxy classes — generated at runtime |
| ALL | `.*Test$.*` inner classes | JUnit-generated test runner inner classes |

#### Type System False Positives — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| `BC_UNCONFIRMED_CAST` | `yawl.elements.*` | Generics with type erasure — pre-Java25 API pattern, safe in practice |
| `BC_UNCONFIRMED_CAST` | `yawl.engine.*` | Same |
| `NP_NULL_ON_SOME_PATH`, `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` | `yawl.stateless.*` | Optional usage patterns — false positives with Optional chaining |

#### Java Record/Builder False Positives — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| `SE_NO_SERIALVERSIONID` | `*Record*`, `*$*Builder*` | Java records don't need `serialVersionUID`; builders intentionally non-serializable |
| `RV_RETURN_VALUE_IGNORED`, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | Methods named `builder`, `with*`, `set*` | Fluent builder API — return values are chained, not ignored in practice |
| `MS_SHOULD_BE_FINAL`, `MS_PKGPROTECT` | `YAWL*` element classes | Framework registration statics — must be mutable for lifecycle management |

#### Domain Model Intentional Design — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| `EQ_DOESNT_OVERRIDE_EQUALS`, `EQ_CHECK_FOR_OPERAND_NOT_COMPATIBLE_WITH_THIS` | `YAWL*` elements | Identity semantics intentional — YAWL elements use object identity, not value equality |
| `SE_INNER_CLASS`, `SIC_INNER_SHOULD_BE_STATIC` | Anonymous inner classes (`$\d+`) | Expected pattern for callbacks/listeners |

#### Engine Patterns — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| `NP_LOAD_OF_KNOWN_NULL_VALUE` | `YPersistenceManager` | Hibernate session patterns — known-safe null checks for lazy loading |
| `DE_MIGHT_IGNORE`, `REC_CATCH_EXCEPTION` | `yawl.engine.*` | Workflow engine exception patterns — intentional broad catches for error propagation |
| `IS2_INCONSISTENT_SYNC`, `VO_VOLATILE_REFERENCE_TO_ARRAY` | `yawl.engine.gui.*` | GUI code — single-thread EDT, not designed for thread safety |
| `JLM_JSR166_UTILCONCURRENT_MONITORENTER` | `yawl.resourcing.*` | `synchronized` blocks on work queues — intentional, correct usage |
| `XXE_SAXPARSER`, `XXE_XMLREADER` | `yawl.schema.*` | XML processing with known-safe schemas — external entity injection risk accepted for internal use |

#### Global Suppressions — PERMANENT (accepted risk)
| Bug Pattern | Why |
|------------|-----|
| `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` | Returns internal mutable representation — accepted; defensive copying would degrade performance |
| `DM_DEFAULT_ENCODING` | Default charset usage — not critical for this server-side codebase |
| `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` | Dynamic SQL in engine — Hibernate-managed, accepted pattern |
| `THROWS_METHOD_THROWS_CLAUSE_THROWABLE` | Legacy API pattern — not modifiable without breaking API |

#### Test Relaxation — PERMANENT
| Bug Pattern | Scope | Why |
|------------|-------|-----|
| `RV_RETURN_VALUE_IGNORED`, `NP_NULL_ON_SOME_PATH`, `DE_MIGHT_IGNORE` | `*Test(s)?(Suite)?` | Test utility classes — relaxed rules acceptable |

### Quality Profile Exclude (`quality/spotbugs/exclude.xml`) — PERMANENT

**File**: `/home/user/yawl/quality/spotbugs/exclude.xml`

| Excluded | Why |
|----------|-----|
| `*.test.*` package | Test code |
| `*.generated.*` package | Generated code |
| `*.build.*` package | Build tooling |
| `EI_EXPOSE_REP` globally | Same as root filter |
| `EI_EXPOSE_REP2` globally | Same |
| `DM_DEFAULT_ENCODING` globally | Same |
| `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` globally | Same |
| `THROWS_METHOD_THROWS_CLAUSE_THROWABLE` globally | Same |

---

## Layer 5: PMD Exclusions

### Root PMD Exclusions (`pmd-exclusions.properties`) — TEMP-DEBT

**File**: `/home/user/yawl/pmd-exclusions.properties`

All entries are temporary — tracked in `COVERAGE_IMPROVEMENT_PLAN.md` for Q3 refactoring.

| File | Excluded PMD Rules | Why | Status |
|------|-------------------|-----|--------|
| `YEngine.java` | `CyclomaticComplexity`, `NcssCount`, `ExcessiveClassLength`, `TooManyMethods`, `TooManyFields` | 1800+ line central Petri net coordinator; complexity inherent to formalism | TEMP-DEBT (Q3 decomposition planned) |
| `YNetRunner.java` | `CyclomaticComplexity`, `NcssCount` | Switch-heavy state machine — unavoidable for Petri net firing semantics | TEMP-DEBT (Q3 planned) |
| `YPersistenceManager.java` | `ExcessiveParameterList`, `TooManyMethods` | Hibernate Session facade — API signatures constrained by Hibernate | TEMP-DEBT |
| `YWorkItemRepository.java` | `TooManyFields` | In-memory marking — field count bounded by YAWL specification itself | TEMP-DEBT |
| `YSpecification.java` | `TooManyFields`, `TooManyMethods` | Mirrors YAWL schema structure mandated by spec | TEMP-DEBT |

**Governance**: `pmd-exclusions.properties` header states: "Each exclusion MUST have a paired comment. Undocumented exclusions will be removed by the HYPER_STANDARDS reviewer."

---

## Layer 6: Checkstyle Suppressions

### Root Suppressions (`checkstyle-suppressions.xml`) — MIXED

**File**: `/home/user/yawl/checkstyle-suppressions.xml`

#### Generated Code — PERMANENT
| Files | Rules | Why |
|-------|-------|-----|
| `ObjectFactory.java` | ALL | JAXB-generated |
| `package-info.java` | `MissingJavadocType` | No class body to document |
| `$$_hibernate_.*` | ALL | Hibernate proxies |
| `yawl-elements/**/(ObjectFactory|*Adapter).java` | ALL | XML binding generated classes |

#### Test Code — PERMANENT
| Files | Rules | Why |
|-------|-------|-----|
| `*Test.java` | `MissingJavadocType`, `MissingJavadocMethod`, `MagicNumber`, `MethodLength`, `JavadocParagraph` | Test code — relaxed style acceptable |
| `*TestSuite.java`, `*TestCase.java` | `MissingJavadocType`, `MissingJavadocMethod` | Same |

#### Engine Complexity — TEMP-DEBT
| Files | Rules | Why | Status |
|-------|-------|-----|--------|
| `YEngine.java` | `MethodLength`, `CyclomaticComplexity`, `FileLength` | 1800+ lines; decomposition scheduled | TEMP-DEBT |
| `YNetRunner.java` | `MethodLength`, `CyclomaticComplexity` | Petri net execution complexity | TEMP-DEBT |
| `YWorkItemRepository.java` | `MethodLength`, `CyclomaticComplexity` | Complex state management | TEMP-DEBT |
| `YPersistenceManager.java` | `MethodLength`, `CyclomaticComplexity` | Hibernate-constrained API | TEMP-DEBT |

#### Legacy Module — TEMP-DEBT
| Files | Rules | Why | Status |
|-------|-------|-----|--------|
| `yawl-resourcing/src/**` | `ImportOrder` | Pre-Jakarta namespace — full review scheduled Q3 | TEMP-DEBT (Q3) |

#### Domain Design — PERMANENT
| Files | Rules | Why |
|-------|-------|-----|
| `*Constants.java`, `*Defaults.java` | `MagicNumber` | Constants files — magic numbers ARE the content |
| `*Builder.java` | `ParameterNumber` | Builder pattern legitimately exceeds 7-parameter limit |

### Quality Suppressions (`quality/checkstyle/suppressions.xml`) — MIXED

**File**: `/home/user/yawl/quality/checkstyle/suppressions.xml`

| Files | Rules | Why |
|-------|-------|-----|
| `[\\/]test[\\/].*` | ALL | Test code |
| `[\\/]generated[\\/].*` | ALL | Generated code |
| `[\\/]build[\\/].*` | ALL | Build tooling |
| `[\\/]schema[\\/].*\.xml` | `LineLength` | Long XML config lines are acceptable (schema files are verbose) |
| `YNetRunner.java`, `YSpecification.java` | `MethodLength` | Complex workflow logic |
| `yawl/integration/**` | `ParameterNumber` | Complex constructors in integration layer |

---

## Layer 7: Hook Exclusions (`.claude/hooks/`)

### `hyper-validate.sh` — TEMP-LEGACY

**File**: `/home/user/yawl/.claude/hooks/hyper-validate.sh`

```bash
if [[ "$FILE" =~ /orderfulfillment/ ]]; then
    exit 0
fi
```

| Excluded | Why | Status |
|----------|-----|--------|
| `orderfulfillment/**` package | Legacy deprecated integration — predates GODSPEED, contains H-guard violations, not being maintained | TEMP-LEGACY: delete when package is removed |

### `session-start.sh` — PERMANENT

```bash
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
    exit 0
fi
```

| Excluded | Why | Status |
|----------|-----|--------|
| Maven proxy setup in local environment | Egress proxy only needed in Claude Code Web (remote); local dev has direct Maven access | PERMANENT (environment auto-detection) |

### `pre-commit-validation.sh` — PERMANENT

```bash
if [[ "${SKIP_VALIDATION:-0}" == "1" ]]; then
    exit 0
fi
```

| Excluded | Why | Status |
|----------|-----|--------|
| Full pre-commit validation when `SKIP_VALIDATION=1` | Emergency escape hatch for hotfixes where full validation cycle is not feasible | PERMANENT (emergency override, logged) |

---

## Layer 8: Mutation Testing — PIT (`quality/mutation-testing/pitest-config.xml`)

**File**: `/home/user/yawl/quality/mutation-testing/pitest-config.xml`

### Packages NOT Targeted (implicit exclusion)

Not in `<targetClasses>`:
- `yawl.resourcing.*` — not in scope for current mutation cycle
- `yawl.scheduling.*` — not in scope
- `yawl.logging.*` — not in scope

### Explicitly Excluded Classes — PERMANENT

| Excluded | Why |
|----------|-----|
| `yawl.swingWorklist.*` | Legacy Swing UI — headless CI cannot run GUI tests |
| `yawl.procletService.*` | Legacy proclet service — not maintained |
| `yawl.smsModule.*` | SMS integration — external service, not unit-testable |
| `yawl.twitterService.*` | Twitter integration — deprecated API (Twitter/X API changes) |
| `yawl.mailSender.*`, `yawl.mailService.*` | Email — external SMTP dependency, not unit-testable |
| `*ObjectFactory*` | JAXB-generated code — no mutation value |
| `*package-info*` | No executable code |

### Excluded Tests — PERMANENT

| Excluded | Why |
|----------|-----|
| `yawl.performance.*` | Performance tests — not correctness tests, not suitable for mutation |
| `yawl.build.*`, `yawl.deployment.*`, `yawl.database.*` | Infrastructure code — not business logic |

---

## Layer 9: Security Scanning (`quality/security-scanning/`)

### Trivy CVE Ignore (`quality/security-scanning/trivy-ignore.txt`) — HEALTHY

**File**: `/home/user/yawl/quality/security-scanning/trivy-ignore.txt`

**Currently EMPTY** — no CVE suppressions active.

**Governance**: Format requires `CVE-ID [expiry-date] # reason` with maximum 90-day expiry. Entries without expiry/rationale are rejected by audit script.

**Status**: This is correct — all CVEs must be remediated or explicitly approved. The empty state is the desired state.

---

## Layer 10: `scripts/dx.sh` — Implicit Module Exclusions

**File**: `/home/user/yawl/scripts/dx.sh`

The `ALL_MODULES` array defines which modules the fast build loop covers:

```bash
ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)
```

Modules **not in dx.sh** (implicitly excluded):

| Excluded Module | Why |
|----------------|-----|
| `yawl-worklet` | Removed from `pom.xml` — no longer exists in Maven reactor |
| `yawl-benchmark` | Performance benchmarking — not core, separate lifecycle |
| `yawl-mcp-a2a-app` | Deployment artifact — not a development-loop module |
| `yawl-processmining-service` | Separate concern — has its own lifecycle |

**Status**: All PERMANENT — match the current Maven reactor structure.

---

## Summary: Which Excludes to Revisit

### High-Value Cleanup Opportunities

1. **`yawl-monitoring` offline workarounds** (`TEMP-OFFLINE`): When Maven proxy/cache is fully populated, re-enable:
   - `OpenTelemetryConfig.java` and `OpenTelemetryInitializer.java` compilation
   - `micrometer-registry-prometheus` dependency
   - `opentelemetry-instrumentation-api` dependency
   - 3 test classes (`OpenTelemetryConfigTest`, `ObservabilityTest`, integration tests)

2. **`autonomous/observability/**` bug** (`TEMP-DEBT`): Fix duplicate logger field in this package and remove compiler exclusion.

3. **Engine complexity** (`TEMP-DEBT`, Q3 target per `COVERAGE_IMPROVEMENT_PLAN.md`): Decomposing `YEngine.java` (1800+ lines) would allow removing suppressions in:
   - `pmd-exclusions.properties` (5 rules for `YEngine`)
   - `checkstyle-suppressions.xml` (`MethodLength`, `CyclomaticComplexity`, `FileLength`)
   - `spotbugs-exclude.xml` (broad `yawl.engine.*` patterns)

4. **`orderfulfillment` package** (`TEMP-LEGACY`): Delete the package → remove the `hyper-validate.sh` hook bypass.

5. **`yawl-resourcing` import order** (`TEMP-DEBT`, Q3): Full Jakarta namespace migration → remove checkstyle `ImportOrder` suppression.

### Do Not Touch (Permanently Correct)

- Generated code suppressions (JAXB, Hibernate proxies) — will always be needed
- Test code relaxations — intentional, test code has different quality standards
- Java 25 false positives in SpotBugs — inherent to SpotBugs tooling
- `.gitignore` patterns for secrets, build artifacts, OS files — standard hygiene
- Test group taxonomy (`fast`, `quick-test`, etc.) — core DX design
- `EngineTestSuite`/`PatternMatchingTestSuite` Surefire exclusion — prevents double-counting
- `log4j-to-slf4j` classpath exclude — Log4j2/SLF4J wiring conflict
- PIT legacy service excludes (`swingWorklist`, `smsModule`, etc.) — deprecated, untestable
- Trivy CVE ignore being empty — correct security posture

---

## File Index

| File | Layer | Rule Count |
|------|-------|-----------|
| `.gitignore` | Version Control | ~45 patterns |
| `pom.xml` | Maven Profiles & Global | ~15 rules |
| `yawl-monitoring/pom.xml` | Module-Level | ~35 rules (incl. 28-package test-compile list) |
| `spotbugs-exclude.xml` | SpotBugs (root) | ~20 match blocks |
| `quality/spotbugs/exclude.xml` | SpotBugs (quality profile) | 8 match blocks |
| `pmd-exclusions.properties` | PMD | 5 class exclusions |
| `checkstyle-suppressions.xml` | Checkstyle (root) | ~15 suppressions |
| `quality/checkstyle/suppressions.xml` | Checkstyle (quality profile) | 7 suppressions |
| `.claude/hooks/hyper-validate.sh` | Hook | 1 package bypass |
| `.claude/hooks/session-start.sh` | Hook | 1 env condition |
| `.claude/hooks/pre-commit-validation.sh` | Hook | 1 escape hatch |
| `quality/mutation-testing/pitest-config.xml` | PIT Mutation | ~10 class/package exclusions |
| `quality/security-scanning/trivy-ignore.txt` | Trivy CVE | 0 (empty — correct) |
| `scripts/dx.sh` | Build Loop | 4 implicit module omissions |
