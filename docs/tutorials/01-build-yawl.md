# Tutorial: Build YAWL from Source

By the end of this tutorial you will have cloned the YAWL repository, run the Observatory to understand the project layout, and compiled all 13 modules successfully with `mvn -T 1.5C clean compile`. You will know what a successful build looks like and where to go next.

---

## Step 1: Verify prerequisites

You need Java 21 or later, Maven 3.9 or later, and Git. Run each command and confirm the version shown meets the minimum.

```bash
java -version
```

Expected: `openjdk version "21.x.x"` or higher.

```bash
mvn -version
```

Expected: `Apache Maven 3.9.x` or higher.

```bash
git --version
```

Expected: `git version 2.x.x` or higher.

If any tool is missing or too old, install it before continuing. YAWL will not compile on Java 11 or Maven 3.6.

---

## Step 2: Clone the repository

Replace `<your-repo-url>` with the HTTPS or SSH URL for your fork or the upstream repository.

```bash
git clone <your-repo-url>
```

Git will create a directory named `yawl` containing the full project.

---

## Step 3: Enter the project directory

```bash
cd yawl
```

All subsequent commands in this tutorial run from this directory.

---

## Step 4: Run the Observatory to understand the project

The Observatory generates machine-readable facts about the project's modules, build order, and known hazards. Reading these facts before building prevents common mistakes.

```bash
bash scripts/observatory/observatory.sh --facts
```

The script writes JSON files into `docs/v6/latest/facts/`. When it finishes it prints a summary line such as:

```
Observatory complete. Status: GREEN. Facts written to docs/v6/latest/facts/
```

YAWL uses a shared-source layout: most modules point their source root at `../src` and use Maven include/exclude filters to claim a package subset. The Observatory records exactly which packages belong to which module in `facts/shared-src.json`. If you skip this step and a module fails to compile, `shared-src.json` is the first place to look.

If you are working offline and the Observatory script requires network access, you can skip it and proceed directly to Step 5. The `facts/` directory may already contain cached output from a previous run.

---

## Step 5: Compile all modules

```bash
mvn -T 1.5C clean compile
```

The `-T 1.5C` flag runs the Maven reactor in parallel using 1.5 threads per CPU core. On a modern laptop this reduces compile time from roughly three minutes to under one minute.

Maven processes modules in reactor order (utilities → elements → authentication → engine → stateless → resourcing → worklet → scheduling → security → integration → monitoring → webapps → control-panel). Each module depends only on modules that appear before it.

If your Maven local repository is already populated from a previous build, add `-o` to compile fully offline:

```bash
mvn -T 1.5C clean compile -o
```

---

## Step 6: Confirm the build succeeded

A successful compile ends with output similar to the following:

```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yawl-parent 6.0.0-SNAPSHOT:
[INFO]
[INFO] yawl-parent ........................................ SUCCESS [  0.3 s]
[INFO] yawl-utilities ..................................... SUCCESS [  8.1 s]
[INFO] yawl-elements ...................................... SUCCESS [  6.4 s]
[INFO] yawl-authentication ................................ SUCCESS [  1.2 s]
[INFO] yawl-engine ........................................ SUCCESS [  9.7 s]
[INFO] yawl-stateless ..................................... SUCCESS [  3.3 s]
[INFO] yawl-resourcing .................................... SUCCESS [  5.8 s]
[INFO] yawl-worklet ....................................... SUCCESS [  2.9 s]
[INFO] yawl-scheduling .................................... SUCCESS [  2.1 s]
[INFO] yawl-security ...................................... SUCCESS [  2.0 s]
[INFO] yawl-integration ................................... SUCCESS [  4.5 s]
[INFO] yawl-monitoring .................................... SUCCESS [  0.9 s]
[INFO] yawl-webapps ....................................... SUCCESS [  0.4 s]
[INFO] yawl-control-panel ................................. SUCCESS [  3.1 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Every module must show `SUCCESS`. If any module shows `FAILURE`, the line immediately above the failure message names the offending source file and the compilation error.

The two most common first-time failures are:
- A Java version below 21 (fix: install Java 21+, set `JAVA_HOME`).
- Missing Maven dependencies due to an empty local repository (fix: remove `-o` and let Maven download them).

---

## What you learned

- How to verify that your local toolchain meets YAWL's minimum requirements.
- How the Observatory generates project-level facts before you build.
- Why YAWL uses a shared-source layout and where the authoritative record of that layout lives.
- How to invoke the parallel compiler and what a complete successful build looks like.

## What next

Continue with [Tutorial 2: Navigate the YAWL Module System](02-understand-the-build.md) to learn how Maven's reactor order, the shared-source strategy, and the Observatory facts fit together. Once you understand the module system, the how-to guides will show you how to run specific test suites, add a new module, and configure deployment targets.
