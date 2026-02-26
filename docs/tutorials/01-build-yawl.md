# Tutorial: Build YAWL from Source

By the end of this tutorial you will have cloned the YAWL repository, run the Observatory to understand the project layout, and compiled all 13 modules successfully with `mvn -T 1.5C clean compile`. You will know what a successful build looks like and where to go next.

---

## Step 1: Verify prerequisites

You need Java 25 or later, Maven 3.9 or later, and Git. Run each command and confirm the version shown meets the minimum.

```bash
java -version
```

Expected: `openjdk version "25.x.x"` or higher.

```bash
mvn -version
```

Expected: `Apache Maven 3.9.x` or higher.

```bash
git --version
```

Expected: `git version 2.x.x` or higher.

If any tool is missing or too old, install it before continuing. YAWL will not compile on Java 21 or Maven 3.6.

## v6.0.0-GA New Features

This version includes two major new features available in GA release:

### 1. GRPO Integration (Group Relative Policy Optimization)
YAWL v6.0.0-GA integrates GRPO for AI agent workflow optimization. The system now supports:
- Policy gradient optimization for multi-agent coordination
- Reinforcement learning for workflow routing decisions
- Automatic policy fine-tuning based on execution traces

### 2. OpenSage Memory System
The OpenSage memory system provides:
- Persistent agent context across workflow sessions
- Long-term memory for workflow patterns and decisions
- Memory retrieval for similar workflow cases
- Personalization based on historical interaction patterns

To enable these features, ensure you have:
```bash
# GRPO requires Python 3.11+ with PyTorch
python3 --version  # Should be 3.11.x or higher

# OpenSage requires additional configuration
# See GA_RELEASE_GUIDE.md for setup instructions
```

See [GA_RELEASE_GUIDE.md](../../GA_RELEASE_GUIDE.md) for detailed configuration.

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

**Note:** For v6.0.0-GA, compile with `-T 1.5C` flag to enable parallel builds with optimal thread utilization:
```bash
mvn -T 1.5C clean compile
```

The `-T 1.5C` flag runs the Maven reactor in parallel using 1.5 threads per CPU core. On a modern laptop this reduces compile time from roughly three minutes to under one minute.

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
[INFO] BUILD SUCCESS - YAWL v6.0.0-GA
[INFO] ------------------------------------------------------------------------
```

Every module must show `SUCCESS`. If any module shows `FAILURE`, the line immediately above the failure message names the offending source file and the compilation error.

The two most common first-time failures are:
- A Java version below 25 (fix: install Java 25+, set `JAVA_HOME`).
- Missing Maven dependencies due to an empty local repository (fix: remove `-o` and let Maven download them).

For v6.0.0-GA, ensure all Java modules target Java 25 bytecode. If compilation fails with unsupported class versions, verify your JDK is correctly set to Java 25.

---

## What you learned

- How to verify that your local toolchain meets YAWL's minimum requirements.
- How the Observatory generates project-level facts before you build.
- Why YAWL uses a shared-source layout and where the authoritative record of that layout lives.
- How to invoke the parallel compiler and what a complete successful build looks like.

## What next

Continue with [Tutorial 2: Navigate the YAWL Module System](02-understand-the-build.md) to learn how Maven's reactor order, the shared-source strategy, and the Observatory facts fit together. Once you understand the module system, the how-to guides will show you how to run specific test suites, add a new module, and configure deployment targets.

For v6.0.0-GA, see the [GA_RELEASE_GUIDE.md](../../GA_RELEASE_GUIDE.md) for:
- GRPO and OpenSage feature activation
- Production deployment patterns
- Migration from v5.x to v6.0.0-GA
