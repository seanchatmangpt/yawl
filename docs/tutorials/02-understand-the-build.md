# Tutorial: Navigate the YAWL Module System

By the end of this tutorial you will be able to read the Observatory facts to answer three questions any YAWL contributor regularly needs to answer: which module owns a given source file, what order modules must build in, and which known hazards can break the build. You will also run a single module's tests to confirm your environment is fully working.

This tutorial assumes you have completed [Tutorial 1: Build YAWL from Source](01-build-yawl.md) and that `docs/v6/latest/facts/` contains current Observatory output.

> **v6.0.0-GA Note**: With Java 25 and virtual threads, YAWL leverages Loom-based concurrency improvements. Build with `-T 1.5C` to utilize parallel compilation with virtual thread backing.

---

## Step 1: Read the module list

```bash
cat docs/v6/latest/facts/modules.json | python3 -m json.tool
```

The output lists all 14 Maven modules (including the new `yawl-ggen` GRPO/RL module). For each module you will see four fields that matter:

- `name` — the Maven artifact ID, also the subdirectory name under the repo root.
- `path` — the directory that contains the module's `pom.xml`.
- `source_dir` — where the module's Java sources live. Most modules show `../src`, meaning they share the single top-level `src/` directory.
- `strategy` — one of three values explained in Step 3.

Scan the list and note that `yawl-webapps` is the only module with `"strategy": "standard"` and its own `src/main/java` directory. Every other module either claims all of `../src` filtered by package includes, or claims a single package subtree directly.

---

## Step 2: Find the build order

```bash
cat docs/v6/latest/facts/reactor.json | python3 -m json.tool
```

The `reactor_order` array gives the exact sequence Maven processes modules. The `module_deps` array lists every declared inter-module dependency as a directed edge `from → to`.

Read the dependency graph from the output. `yawl-utilities` has no incoming edges, so it builds first. `yawl-elements` depends on `yawl-utilities`. `yawl-engine` depends on `yawl-elements`. `yawl-stateless` depends on `yawl-elements`, `yawl-engine`, and `yawl-utilities`. Anything downstream of `yawl-engine` — resourcing, worklet, scheduling, integration, monitoring, control-panel — cannot start until the engine artifact is available.

This ordering matters when you change a low-level module: every module that depends on it will recompile. The `-T 1.5C` flag lets Maven parallelize modules whose dependencies are already built, leveraging Java 25's virtual thread support for improved build performance on multicore systems.

---

## Step 3: Understand shared source

```bash
cat docs/v6/latest/facts/shared-src.json | python3 -m json.tool
```

The `shared_roots` array lists every directory that more than one module reads from. Focus on the three `strategy` values you saw in Step 1:

**`full_shared`** — The module sets its source root to `../src` and uses Maven `<includes>` and `<excludes>` filters to select a package subset. For example, `yawl-engine` includes `**/org/yawlfoundation/yawl/engine/**` and excludes the `stateless`, `resourcing`, and `authentication` packages. If you add a new class to `src/org/yawlfoundation/yawl/engine/`, it automatically belongs to `yawl-engine` because of those filters.

**`package_scoped`** — The module points its source root directly at a single package directory inside `../src`. For example, `yawl-authentication` uses `../src/org/yawlfoundation/yawl/authentication` as its root, so it compiles only that package. There are no filters to manage.

**`standard`** — The module owns its own `src/main/java` directory. Only `yawl-webapps` uses this strategy. It is the conventional Maven layout and has no shared-source complexity.

The `ownership_ambiguities` array at the bottom of the file lists the three places where two modules include overlapping paths. These are known, tracked, and harmless in practice, but they explain why you must check `shared-src.json` before deciding which module's `pom.xml` to edit when a resource file is not found at compile time.

---

## Step 4: Check for known hazards

```bash
cat docs/v6/latest/facts/maven-hazards.json | python3 -m json.tool
```

The `hazards` array lists build traps that the Observatory has detected. As of the current run there is one active hazard:

```
H_LOGGING_BRIDGE_CONFLICT: Both log4j-to-slf4j and log4j-slf4j2-impl are present.
Ensure exclusions are in place per module.
```

This means some modules declare conflicting SLF4J bridge dependencies. If you add a new module or dependency and the build starts emitting `SLF4J: Class path contains multiple SLF4J bindings` warnings, return to this file to identify which modules need `<exclusion>` entries.

If the hazards array is empty, no known build traps exist in the current state of the project.

---

## Step 5: Run a single module's tests

You can test any module in isolation without recompiling the entire reactor. This example uses `yawl-utilities`, which is the smallest module with tests and has no upstream dependencies.

```bash
mvn test -pl yawl-utilities
```

Maven will compile and test only that module. At the end of the output you will see a Surefire summary:

```
[INFO] Tests run: N, Failures: 0, Errors: 0, Skipped: 0
```

To run a different module's tests, replace `yawl-utilities` with any module name from Step 1. If the module depends on others (for example `yawl-engine` depends on `yawl-elements`), add the `-am` flag so Maven also builds the dependencies:

```bash
mvn test -pl yawl-engine -am
```

---

## What you learned

- The 13 YAWL modules and the three source strategies they use (`full_shared`, `package_scoped`, `standard`).
- How to read the reactor order to predict recompilation impact when changing a low-level module.
- How `full_shared` modules use include/exclude filters to divide a single `src/` directory among multiple artifacts.
- Where to look when a resource file or class is claimed by the wrong module.
- How to run one module's tests in isolation, with and without building its upstream dependencies.

## What next

With the build working and the module system understood, move on to the how-to guides:

- **How to add a new workflow element** — extends `yawl-elements` using the `full_shared` strategy.
- **How to use GRPO/RL with `yawl-ggen`** — Generate, Run, Optimize workflow patterns using Java 25's advanced features
- **How to run the full test suite with coverage** — `mvn -T 1.5C clean verify` and reading the JaCoCo report.
- **How to deploy to Tomcat** — `docs/DEPLOY-TOMCAT.md` walks through packaging and deployment configuration.
- **How to extend the integration layer** — adding an MCP tool or an A2A agent endpoint in `yawl-integration`.
