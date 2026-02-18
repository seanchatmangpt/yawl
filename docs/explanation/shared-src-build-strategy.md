# The Shared-Source Build Strategy

YAWL v6 has 13 Maven modules but only one monolithic source directory. Every new contributor who opens the project for the first time asks the same question: why does `yawl-elements/pom.xml` set `<sourceDirectory>../src</sourceDirectory>` and point into a sibling directory? This document explains the decision, the mechanism, and the tradeoffs. If you want step-by-step instructions for adding a new package to the build, see the how-to guide (Developer Quickstart in `.claude/DEVELOPER-QUICKSTART.md`).

---

## The Problem It Solves

YAWL has been in continuous development for over 20 years. Through most of that time, the build system was Apache Ant with a single `src/` directory at the project root. Ant does not care about directory layout in the way Maven does — you point it at a source tree and it compiles everything. The result is 100,000+ lines of production code arranged by Java package under `src/org/yawlfoundation/yawl/`, with no sub-project boundaries.

Maven's conventional model expects each module to own its own `src/main/java` directory. If YAWL had adopted this convention during the 2025 migration to Maven, the project would have had to physically move tens of thousands of source files into per-module directories, rewriting `import` statements, updating Hibernate mapping files, adjusting test resource paths, and invalidating 20 years of `git blame` history. That was not a risk the team was willing to take while also migrating to Java 25, Spring Boot 3.4, and JUnit 5.

The shared-src strategy threads this needle: Maven modules exist for dependency management and artifact production, but the source code stays where it always was.

---

## How It Works

Each module's `pom.xml` overrides Maven's default source directory with:

```xml
<build>
    <sourceDirectory>../src</sourceDirectory>
    <testSourceDirectory>../test</testSourceDirectory>
</build>
```

The `../src` path is relative to the module directory, so from `yawl-elements/` it resolves to the root-level `src/` tree. Every module reads from the same physical directory.

The `maven-compiler-plugin` then uses `<includes>` and `<excludes>` filters to select only the packages that belong to each module. For example, `yawl-elements` includes the elements and a handful of engine base types:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/org/yawlfoundation/yawl/elements/**</include>
            <include>**/org/yawlfoundation/yawl/engine/YPersistenceManager*</include>
            <include>**/org/yawlfoundation/yawl/engine/YEngine*</include>
            <!-- ... -->
        </includes>
        <excludes>
            <exclude>**/*.xsd</exclude>
            <exclude>**/*.xml</exclude>
            <exclude>**/org/yawlfoundation/yawl/engine/**</exclude>
            <!-- ... -->
        </excludes>
    </configuration>
</plugin>
```

The `maven-surefire-plugin` receives parallel include/exclude lists to ensure that only the tests belonging to the module are selected when `mvn test` runs on a specific module. This matters because the shared `test/` directory contains tests for every package.

---

## The Three Source Root Types

The 13 modules fall into three patterns, recorded in `docs/v6/latest/facts/shared-src.json`:

### 1. Full Shared (`../src`)

Five modules point directly at the root `src/` directory and rely entirely on include/exclude patterns to carve out their slice: `yawl-utilities`, `yawl-elements`, `yawl-engine`, `yawl-stateless`, and `yawl-security`. These are the core modules whose packages are deeply intertwined within the original monolith and could not be cleanly extracted into subdirectories without significant refactoring.

Each of these modules sees all 736 source files in the directory (the full-shared count in `modules.json` reflects the total visible to the compiler before filtering, not the files actually compiled). The include/exclude configuration in the pom is the sole enforcement mechanism for module boundaries.

### 2. Package-Scoped (separate subdirectory)

Three modules have a clean enough package boundary that their source was extracted into a subdirectory of `src/`:

- `yawl-monitoring` points to `../src/org/yawlfoundation/yawl/observability`
- `yawl-authentication` points to `../src/org/yawlfoundation/yawl/authentication`
- `yawl-integration` points to `../src/org/yawlfoundation/yawl/integration`

These modules use `<sourceDirectory>` to point at their own package directory. There is no ambiguity about which files they own: every `.java` file under that subtree belongs to them. This is the cleanest arrangement and is what a conventional Maven project would look like. New packages added to YAWL should aim for this pattern when possible.

### 3. Package-Scoped Shared (full shared root, narrow includes)

The remaining modules (`yawl-resourcing`, `yawl-worklet`, `yawl-scheduling`, `yawl-control-panel`) use `<sourceDirectory>../src</sourceDirectory>` but with narrow include patterns that match only their package. Functionally this is equivalent to the package-scoped strategy, but it was simpler to configure than moving files. These could be migrated to the package-scoped strategy in a future cleanup.

---

## Ownership Ambiguities

The shared-src strategy introduces a class of problem that conventional Maven projects cannot have: two modules can include the same file. The Observatory (`docs/v6/latest/facts/shared-src.json`) currently tracks three such ambiguities:

| Path | Claimed by | Reason |
|------|-----------|--------|
| `../src` | `yawl-utilities`, `yawl-engine` | Overlapping include: `**/org/yawlfoundation/yawl/unmarshal/**/*.xml` |
| `../src` | `yawl-utilities`, `yawl-engine` | Overlapping include: `**/*.xml` |
| `../src` | `yawl-elements`, `yawl-engine` | Overlapping include: `**/org/yawlfoundation/yawl/elements/**/*.xml` |

These three ambiguities are medium severity. They do not cause compilation failures because Java source files are deduped (compiling the same `.java` file twice into the same class is idempotent). They do create maintenance risk: if a developer modifies a file that appears in two modules' includes, they may be surprised that a change to `yawl-utilities` is also compiled into `yawl-engine`.

The FMEA table in the Observatory Index rates FM1 (Shared Source Path Confusion) at RPN 216 — the highest risk in the project. The mitigation is the `shared-src.json` fact file and the `15-shared-src-map.mmd` Mermaid diagram, which give a precise, machine-readable account of which modules own which packages.

---

## Tradeoffs

The shared-src strategy is a pragmatic choice, not an ideal one. Understanding the tradeoffs helps when evaluating future changes.

**What it preserves:**
- Git history is intact. `git log -- src/org/yawlfoundation/yawl/engine/YEngine.java` traces the full 20-year lineage without any file-move interruptions.
- The Ant build still works for teams that have not migrated. `src/` is exactly where Ant expects it.
- No risk of merge conflicts caused by mass file moves. Every feature branch touching this area during the Maven migration remained mergeable.

**What it costs:**
- IDE configuration is non-trivial. IntelliJ IDEA and Eclipse both have trouble with a project where multiple modules declare the same source root. The Developer Quickstart describes the workaround, but it is extra setup that conventional Maven projects do not require.
- The include/exclude lists in each pom are maintenance items. When a new Java package is added under `src/`, the responsible developer must also update the include list in the correct module's pom. Forgetting this step results in the new code being invisible to Maven — it compiles under Ant but not Maven. This has caused build failures in the past.
- Static analysis tools (SpotBugs, PMD, Checkstyle) run against each module's compiled slice. A false-positive can appear to originate in a module that did not write the code. Reading SpotBugs output requires knowing which module "owns" a file, which means consulting `shared-src.json`.
- The ambiguity risk (FM1 above) requires the Observatory to be consulted before modifying include patterns. Ad-hoc edits to a module pom that broaden an include pattern can silently pull in files from another module's domain.

---

## When to Add a New Package

This document explains the strategy. For the step-by-step procedure — including which pom to edit, how to choose between full-shared and package-scoped, and how to validate the change — see `.claude/DEVELOPER-QUICKSTART.md`.

The short answer: if the new package has no dependency on `yawl-engine` or `yawl-elements` internals, prefer the package-scoped strategy (create a subdirectory under `src/org/yawlfoundation/yawl/` and point `<sourceDirectory>` at it). If the package is tightly coupled to existing shared-root modules, add include patterns to the appropriate module's pom and update `shared-src.json` via the Observatory refresh script.

---

## Why Not Restructure?

The question comes up regularly: why not just move the source files into per-module `src/main/java` directories and be done with it?

The honest answer is cost versus benefit. Moving 100,000+ lines of source across 13 modules is a large-scale, high-risk operation. It would:

- Break every open branch and in-flight pull request on the day it lands.
- Invalidate IDE project files for every developer on the team.
- Break the Ant build, which is still used by two downstream integrators who have not migrated.
- Require updating 89 `package-info.java` files, all Hibernate mapping file paths, and XML resource references baked into test code.
- Produce a massive, hard-to-review commit that touches every file in the project.

For a project of YAWL's age, git history continuity has genuine value — it is the only way to understand why a design decision was made a decade ago. A mass file move severs that continuity for the moved files.

The v7.0 roadmap includes a proper module restructuring as part of adopting the Java Module System (`module-info.java`). At that point, the physical restructuring and the architectural restructuring will be the same operation, making the cost worthwhile. Until then, the shared-src strategy keeps the Maven build working without destroying what came before.
