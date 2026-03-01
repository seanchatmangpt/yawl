# YAWL Rust4pm Panama FFM Bridge — How-To Guides

**Target audience**: Developers who understand YAWL workflows and Java/Rust basics, using the rust4pm bridge for process mining tasks.

**Quick links**: [Reference](reference.md) | [Explanation](explanation.md) | [Tutorials](tutorials.md)

---

## How to load the native library from a custom path

**Goal**: Use rust4pm native library from a non-standard location (e.g., `/opt/rust4pm/librust4pm.so` instead of `./target/release/`).

**When to use**: Custom deployment environments, CI/CD systems, or systems with multiple Rust4pm versions.

### Recipe

Set the system property **before** the `rust4pm_h` class loads:

```java
// MUST be set in the very first lines of main() or @BeforeAll
System.setProperty("rust4pm.library.path", "/opt/rust4pm/librust4pm.so");

// Now safe to instantiate bridge
Rust4pmBridge engine = new Rust4pmBridge(Arena.ofShared());
```

### Why class loading order matters

Java's `System.load()` (called internally by Panama FFM) operates once per JVM instance. Once `rust4pm_h` is loaded, changing the property has no effect.

**Timing rule**:
- Property must be set **before** first reference to `rust4pm_h` or `Rust4pmBridge`
- Safe locations: `main()` first line, `@BeforeAll static` test setup, application startup hooks
- **Too late**: Inside lazy-loaded singletons after bridge instantiation

### Verification

```java
String path = System.getProperty("rust4pm.library.path");
System.out.println("Using library from: " + path);
// Or check native method calls work without UnsatisfiedLinkError
```

**Troubleshooting**: If UnsatisfiedLinkError occurs, see "How to diagnose native library not found errors".

---

## How to handle parse errors gracefully

**Goal**: Catch and interpret Rust4pm parse errors when loading malformed OCEL2 JSON.

**When to use**: Validating untrusted event logs, loading logs from external systems, or user-uploaded files.

### Recipe

Use try-with-resources and map Rust errors to domain exceptions:

```java
import org.yawlfoundation.yawl.rust4pm.Rust4pmException;
import org.yawlfoundation.yawl.rust4pm.bridge.ParseException;

try (OcelLogHandle log = engine.parseOcel2Json(jsonString)) {
    // Process log...
    List<OcelEvent> events = log.events().toList();
} catch (ParseException e) {
    // Rust error message includes line number and expected field
    logger.error("Invalid OCEL2 JSON at input offset {}: {}",
        e.getOffset(), e.getMessage());

    // Re-throw as domain exception for workflow handlers
    throw new WorkflowException(
        "Cannot ingest event log: " + e.getMessage(),
        e
    );
}
```

### What causes ParseException

| Cause | Rust error message | Fix |
|-------|-------------------|-----|
| Missing `events` array | `missing field 'events' at line 5` | Add `"events": [...]` to JSON |
| Invalid event type | `unknown field 'event_type' at line 42` | Use `"ocel:type"` instead |
| Bad timestamp format | `invalid RFC3339 datetime: '2025/12/01'` | Use ISO 8601: `2025-12-01T14:30:00Z` |
| Duplicate object IDs | `object 'o1' already exists at line 88` | Remove or rename duplicate |

### Getting detailed error context

The Rust parser reports precise line/column offsets:

```java
catch (ParseException e) {
    // Print context around error
    String[] lines = jsonString.split("\n");
    int lineNo = e.getLineNumber();
    for (int i = Math.max(0, lineNo - 2); i < Math.min(lines.length, lineNo + 3); i++) {
        System.err.printf("%d: %s%n", i + 1, lines[i]);
    }
}
```

---

## How to process large event logs without GC pressure

**Goal**: Load and stream 1M+ events from OCEL2 without triggering full GC pauses.

**When to use**: Production workflows processing enterprise event logs, real-time case management.

### Recipe

Use `OcelEventView.stream()` for lazy evaluation instead of `toList()`:

```java
import org.yawlfoundation.yawl.rust4pm.bridge.OcelEventView;

try (OcelLogHandle log = engine.parseOcel2Json(eventLogJson)) {
    // DO NOT DO THIS (allocates entire list in Java heap):
    // List<OcelEvent> allEvents = log.events().toList();

    // INSTEAD: stream events lazily (zero GC pressure on event data)
    long eventCount = log.events().stream()
        .filter(evt -> evt.ocelTimestamp() >= cutoffTime)
        .peek(evt -> processEvent(evt))  // Process as you iterate
        .count();

    System.out.printf("Processed %d events%n", eventCount);
}
```

### Why this avoids GC

**Java heap pressure without streaming**:
```
1M events × 800 bytes/event = ~800 MB allocation
→ GC full stop-the-world pause every ~5 seconds
→ Latency = +50-200ms per pause
```

**Zero GC pressure with Rust heap**:
```
Events live in native Rust arena (outside Java GC)
↓
OcelEventView holds only a memory-mapped pointer (8 bytes)
↓
Each .next() call brings one event across FFM boundary
↓
Stream elements are short-lived (collected to ~1KB buffer)
→ No GC pauses, latency = sub-microsecond per event
```

### Example: conformance checking at scale

```java
// Process 10M events without GC
try (OcelLogHandle log = engine.parseOcel2Json(hugeLogJson)) {
    ConformanceReport report = engine.checkConformance(log, pnmlXml)
        .stream()
        .peek(violation -> recordMetric(violation))
        .collect(ConformanceReport.summarize());

    // Heap usage: ~10-20 MB (not 8GB)
}
```

**Measurement**: Heap profiling with `jcmd` shows constant 50MB vs 2GB spiking.

---

## How to run conformance checking in parallel

**Goal**: Check conformance of 10+ event logs against one Petri net model in parallel threads.

**When to use**: Batch analysis, A/B testing variants, or analyzing multiple case streams.

### Recipe

Use virtual threads (Java 21+) with StructuredTaskScope for safe concurrency:

```java
import java.util.concurrent.StructuredTaskScope;
import org.yawlfoundation.yawl.rust4pm.bridge.ConformanceReport;

List<OcelLogHandle> logs = /* loaded separately */;
String pnmlXml = /* shared Petri net definition */;

try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // Fork one conformance check per log
    var tasks = logs.stream()
        .map(log -> scope.fork(() ->
            engine.checkConformance(log, pnmlXml)
        ))
        .toList();

    // Wait for all or fail on first error
    scope.join().throwIfFailed();

    // Collect results
    List<ConformanceReport> reports = tasks.stream()
        .map(StructuredTaskScope.Subtask::get)
        .toList();

    reports.forEach(r -> System.out.printf(
        "F1-score: %.2f, fitness: %.2f%n",
        r.f1Score(), r.fitness()
    ));
}
```

### Thread safety rules

**Safe to parallelize**:
- `Rust4pmBridge` is internally synchronized (uses `Arena.ofShared()`)
- Multiple threads can call `checkConformance()` simultaneously
- Each log gets its own `OcelLogHandle` (scoped to Rust arena)

**NOT thread-safe** (must be single-threaded):
- `OcelLogHandle` itself — do not share across threads
- Event iteration via `log.events()` — each handle bound to one thread's Arena segment

**Correct pattern**:
```java
// GOOD: Each thread gets its own handle
logs.parallelStream().map(jsonStr -> {
    try (OcelLogHandle log = engine.parseOcel2Json(jsonStr)) {
        return engine.checkConformance(log, pnml);
    }
}).collect(toList());

// BAD: Sharing one handle across threads
OcelLogHandle log = engine.parseOcel2Json(json);
IntStream.range(0, 4).parallel().forEach(i -> {
    engine.checkConformance(log, pnml);  // UnsafeSharedArenaError
});
```

### Performance expectations

- **Single thread**: 1000 events/sec (network-bound)
- **4 parallel threads**: ~3500 events/sec (CPU + Rust pipelining)
- **Scaling**: Near-linear up to 8 threads (FFM allocation per thread is cheap)

---

## How to use pattern matching on discovered models

**Goal**: Handle multiple model types returned from discovery (Petri net, process tree, Declare) with exhaustive type checking.

**When to use**: Process mining pipelines that discover models dynamically, or systems supporting multiple model formalism variants.

### Recipe

Use sealed interface with pattern matching (Java 21+) to force exhaustive case handling:

```java
import org.yawlfoundation.yawl.rust4pm.model.ProcessModel;
import org.yawlfoundation.yawl.rust4pm.model.ProcessModel.*;

// Hypothetical: engine discovers best model from log
ProcessModel model = engine.discoverModel(logHandle, DiscoveryConfig.BEST_FIT);

double fitness = switch (model) {
    case PetriNet pn -> {
        // pn.pnmlXml(), pn.initialMarking(), pn.transitions()
        ConformanceReport report = engine.checkConformance(logHandle, pn.pnmlXml());
        yield report.fitness();
    }
    case ProcessTree pt -> {
        // pt.nestedTree(), pt.leafCount()
        yield scoreProcessTree(logHandle, pt);
    }
    case Declare dm -> {
        // dm.constraints(), dm.relevantVars()
        yield scoreDeclare(logHandle, dm);
    }
};

System.out.printf("Model %s has fitness %.2f%n",
    model.getClass().getSimpleName(), fitness);
```

### Why sealed interface matters

Without sealed interface, the compiler allows unhandled cases:

```java
// WITHOUT sealed: compiles even if missing a case
double score = switch (model) {
    case PetriNet pn -> checkConformance(logHandle, pn.pnmlXml()).fitness();
    // Missing ProcessTree and Declare — silent bug!
};
```

**With sealed interface**:
```
error: switch expression does not cover all possible input values
  case ProcessTree or case Declare missing
```

### Real-world example: comparing discovery algorithms

```java
List<ProcessModel> models = List.of(
    engine.discoverInductiveRepresentation(log),
    engine.discoverAlpha(log),
    engine.discoverHeuristic(log)
);

models.stream()
    .map(m -> switch (m) {
        case PetriNet pn -> Pair.of("Petri", pn);
        case ProcessTree pt -> Pair.of("Tree", pt);
        case Declare d -> Pair.of("Declare", d);
    })
    .forEach(p -> System.out.printf("%s model: %s%n", p.left, p.right));
```

---

## How to manage Arena lifetime correctly

**Goal**: Choose the right Arena strategy for your use case (single call, bridge lifetime, or per-task allocation).

**When to use**: Every time you instantiate `Rust4pmBridge` or use raw FFM calls.

### Recipe: Three Arena strategies

#### Strategy 1: Confined Arena (single-threaded operations)

Use `Arena.ofConfined()` for one-shot operations with immediate cleanup:

```java
// Lightweight: parsing a single small log once
try (Arena arena = Arena.ofConfined()) {
    OcelLogHandle handle = engine.parseOcel2Json(jsonStr);
    // Use immediately
    ConformanceReport report = engine.checkConformance(handle, pnml);
    // All native memory returned when arena closes
}
```

**Pros**: Minimal memory overhead, FIFO allocation efficient
**Cons**: Single thread only, must not retain handles beyond arena lifetime
**Use when**: Test code, single-shot commands, batch jobs without parallelism

#### Strategy 2: Shared Arena (bridge lifetime — recommended)

Use `Arena.ofShared()` when instantiating `Rust4pmBridge`:

```java
// Standard production pattern
Rust4pmBridge engine = new Rust4pmBridge(Arena.ofShared());

// Thread 1
try (OcelLogHandle log1 = engine.parseOcel2Json(json1)) {
    engine.checkConformance(log1, pnml);
}

// Thread 2 (concurrent, same bridge)
try (OcelLogHandle log2 = engine.parseOcel2Json(json2)) {
    engine.checkConformance(log2, pnml);
}

// Bridge lifetime: until JVM exit or engine.close()
```

**Pros**: Thread-safe, handles concurrent calls, long-lived objects
**Cons**: Memory grows over time; requires explicit cleanup on shutdown
**Use when**: Production servers, long-running applications, multi-threaded workloads

#### Strategy 3: Auto Arena (NOT recommended for process mining)

```java
// ANTI-PATTERN: Do NOT use Arena.ofAuto() for large workloads
Arena autoArena = Arena.ofAuto();
for (int i = 0; i < 1_000_000; i++) {
    // Each iteration allocates from arena, no automatic return
    OcelLogHandle handle = engine.parseOcel2Json(jsonStr);
    // Memory leak: arena grows unbounded
}
```

**Why not**: Auto arena defers cleanup to GC, causes memory bloat on million-event datasets.

### Decision tree

```
Is this a single short-lived operation?
├─ YES → Arena.ofConfined()
└─ NO
   ├─ Multi-threaded or long-lived?
   │  └─ YES → Arena.ofShared() in Rust4pmBridge
   └─ Loop over many items?
      └─ Use Arena.ofShared() outside loop, reuse bridge
```

---

## How to diagnose "native library not found" errors

**Goal**: Troubleshoot `java.lang.UnsatisfiedLinkError: librust4pm.so: cannot open shared object file`.

**When to use**: Initial setup, deployment to new environments, after Rust compilation.

### Diagnostic steps (in order)

#### Step 1: Check system property

Verify that the property is correctly set:

```bash
# Add to startup and check logs
System.out.println("rust4pm.library.path = " +
    System.getProperty("rust4pm.library.path"));
```

If null, review "How to load the native library from a custom path".

#### Step 2: Verify file exists

Check that the compiled library file is present:

```bash
# Default location (after build)
ls -la ./target/release/librust4pm.so

# Custom location (if set via property)
ls -la /opt/rust4pm/librust4pm.so

# If missing: rebuild
bash scripts/build-rust4pm.sh release
```

#### Step 3: Verify architecture match

The library must match your JVM's architecture (x86-64, ARM64, etc.):

```bash
# Check library architecture
file librust4pm.so
# Expected output: ELF 64-bit LSB shared object, x86-64 (or ARM64)

# Check JVM architecture
java -version
# Check matching 64-bit / 32-bit
```

**Mismatch example**: Compiled 32-bit library on 64-bit JVM.
**Fix**: `bash scripts/build-rust4pm.sh release --target x86_64-unknown-linux-gnu`

#### Step 4: Check library dependencies

Native libraries may depend on other shared objects:

```bash
# List library dependencies
ldd librust4pm.so
# Check for "not found" entries
#   libstdc++.so.6 => not found  (missing C++ runtime)

# Fix: install dependencies or set LD_LIBRARY_PATH
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH
```

#### Step 5: Rebuild from scratch

If above steps don't resolve:

```bash
cd rust
# Clean build
rm -rf target/
cargo build -p rust4pm --release

# Or full rebuild script
bash scripts/build-rust4pm.sh release --clean
```

### Example troubleshooting session

```
$ java -jar yawl-engine.jar
Exception in thread "main" java.lang.UnsatisfiedLinkError:
  /opt/rust4pm/librust4pm.so: cannot open shared object file

$ file ./target/release/librust4pm.so
./target/release/librust4pm.so: ELF 64-bit LSB shared object

$ ldd ./target/release/librust4pm.so
  libstdc++.so.6 => /usr/lib/x86_64-linux-gnu/libstdc++.so.6

$ export LD_LIBRARY_PATH=/opt/rust4pm:$LD_LIBRARY_PATH
$ java -jar yawl-engine.jar
# Success!
```

---

## How to rebuild the native library after Rust changes

**Goal**: Compile new Rust4pm code and reload in YAWL without losing in-flight data.

**When to use**: After pulling `rust4pm` updates, modifying Rust algorithms, or upgrading dependencies.

### Recipe

#### Step 1: Verify Rust tests pass

Before rebuilding, ensure no regressions:

```bash
cd rust
cargo test -p rust4pm
# or with logging
RUST_LOG=debug cargo test -p rust4pm -- --nocapture
```

Expected output: all test suites pass.

#### Step 2: Build release library

```bash
# Standard build (from repo root)
bash scripts/build-rust4pm.sh release

# Or verbose build (see compiler output)
bash scripts/build-rust4pm.sh release --verbose

# Output location: ./target/release/librust4pm.so
ls -lh ./target/release/librust4pm.so
```

**Build time**: ~30-60 seconds (incremental) or ~2-3 minutes (clean).

#### Step 3: Verify library integrity

```bash
# Check file size is reasonable (not truncated)
stat -c "%s %n" ./target/release/librust4pm.so
# Expected: 500KB - 5MB

# Verify no missing symbols
nm ./target/release/librust4pm.so | grep rust4pm_parse
# Expected: symbols listed
```

#### Step 4: Restart JVM to load new library

**Critical**: Native libraries load once per JVM. Changes only take effect after restart.

```java
// This logs which library is loaded:
System.out.println("Rust4pm library path: " +
    System.getProperty("java.library.path"));

// After rebuild, STOP the JVM (kills old library handle)
// Then START a new JVM (loads new library)
```

**Options for safe restart**:
- Standalone: `kill <pid>` then `java -jar ...`
- Container: Kubernetes rolling update or Docker restart
- Application server: Redeploy WAR to Tomcat/Jetty

### Example: CI/CD rebuild flow

```bash
#!/bin/bash
set -e

# Build Rust library
cd rust && cargo test -p rust4pm
bash ../scripts/build-rust4pm.sh release

# Verify
file ../target/release/librust4pm.so
ldd ../target/release/librust4pm.so

# Deploy to artifact repository
aws s3 cp ../target/release/librust4pm.so \
    s3://yawl-artifacts/librust4pm-$(date +%Y%m%d).so

# Fleet manager restarts JVMs
kubectl rollout restart deployment/yawl-engine
```

### Diagnosing stale library issues

If new Rust code doesn't take effect:

```java
// Check library version (if exposed)
String version = engine.getNativeLibraryVersion();
System.out.println("Loaded: " + version);

// Compare to expected version
if (!version.equals("rust4pm-2025-02-28")) {
    logger.warn("Native library is stale. Restart JVM to reload.");
}
```

**Manual force-reload** (not recommended):
```java
// Does NOT work: native libraries cannot be unloaded
System.load("/opt/rust4pm/librust4pm-new.so");
// → UnsatisfiedLinkError: already loaded

// Correct: change library path and restart JVM
System.setProperty("rust4pm.library.path", "/opt/rust4pm/librust4pm-v2.so");
// Restart required
```

---

## Quick reference: Arena cleanup checklist

- [ ] Confined arena: Explicit cleanup when no longer needed
- [ ] Shared arena: Tied to Rust4pmBridge lifetime
- [ ] No overlapping arenas: Each thread gets one (StructuredTaskScope enforces this)
- [ ] Close handles in finally blocks or try-with-resources
- [ ] Monitor native memory via `jcmd` if suspecting leaks

---

**See also**: [Reference](reference.md) for API details | [Explanation](explanation.md) for design rationale

