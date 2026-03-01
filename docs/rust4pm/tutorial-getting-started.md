# Getting Started with YAWL Rust4PM: Your First Process Discovery

Welcome! In this tutorial, you'll learn how to use the YAWL rust4pm bridge to parse an OCEL2 event log and discover a directly-follows graph. By the end, you'll have a working Java application that connects to Rust-powered process mining.

## What You'll Learn

- How to build and integrate the Rust4PM library with YAWL
- How to load an OCEL2 event log from JSON
- How to analyze an event log and extract a directly-follows graph (DFG)
- How to clean up resources properly using Java's try-with-resources pattern
- How to understand the bridge between Java and Rust in YAWL's process mining stack

**Estimated time**: 10 minutes (plus 2-3 minutes for first-time Rust compilation)

## Prerequisites

Before you start, make sure you have:
- **Java 25+** (check with `java -version`)
- **Maven 3.9+** (check with `mvn -version`)
- **Rust toolchain** (if rebuilding; check with `rustc --version`)
- Access to the YAWL repository at `/home/user/yawl`
- About 500 MB of disk space for the first Rust build

## Step 1: Build the Rust Library

The rust4pm module includes a native Rust library that performs the heavy lifting for process mining. You need to build it once.

Open a terminal and navigate to your YAWL home:

```bash
cd /home/user/yawl
```

Run the build script:

```bash
bash scripts/build-rust4pm.sh
```

You'll see output like this (first build takes 1-2 minutes):

```
Building rust4pm library...
[Compiling] rust4pm...
[Linking] librust4pm.so...
[Done] Library built successfully.
```

Verify the library exists:

```bash
ls -lh rust/target/release/librust4pm.so
```

You should see a file like:

```
-rwxr-xr-x 1 user user 2.8M Feb 28 14:32 /home/user/yawl/rust/target/release/librust4pm.so
```

You've just built your first native bridge! The `.so` file is a shared object library that contains compiled Rust code.

## Step 2: Add the Maven Dependency

The yawl-rust4pm module is already part of the YAWL reactor build. When you compile YAWL, the Java bindings are automatically created.

Verify your project's `pom.xml` includes the dependency (it should be in the parent reactor):

```xml
<dependency>
    <groupId>org.yawlfoundation.yawl</groupId>
    <artifactId>yawl-rust4pm</artifactId>
    <version>${project.version}</version>
</dependency>
```

If you're starting a new project, add this dependency to your `pom.xml`.

Now compile the entire YAWL project to ensure all Java bindings are ready:

```bash
bash scripts/dx.sh all
```

Wait for the build to complete (should be green). You've just compiled the complete YAWL stack including rust4pm!

## Step 3: Write Your First Java Class

Let's create a simple Java application that loads an OCEL2 event log and discovers its process structure.

Create a new file in your project's test directory (or a standalone demo):

**File**: `src/main/java/org/yawlfoundation/yawl/demo/FirstProcessMining.java`

```java
package org.yawlfoundation.yawl.demo;

import org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge;
import org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle;
import org.yawlfoundation.yawl.rust4pm.processmining.ProcessMiningEngine;
import org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph;
import org.yawlfoundation.yawl.rust4pm.model.OcelEvent;
import org.yawlfoundation.yawl.rust4pm.error.ParseException;
import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FirstProcessMining demonstrates how to parse an OCEL2 event log
 * and discover a directly-follows graph using the rust4pm bridge.
 */
public class FirstProcessMining {

    public static void main(String[] args) {
        // Initialize the bridge
        Rust4pmBridge bridge = Rust4pmBridge.getInstance();
        System.out.println("✓ Rust4PM bridge initialized");

        try {
            // Create a sample OCEL2 event log (JSON)
            String ocelJson = createSampleOcelLog();
            System.out.println("✓ Sample OCEL2 log created");

            // Write to a temporary file
            Path tempLogFile = Files.createTempFile("ocel_sample", ".json");
            Files.writeString(tempLogFile, ocelJson);
            System.out.println("✓ Log written to: " + tempLogFile.toAbsolutePath());

            // Create a process mining engine
            ProcessMiningEngine engine = ProcessMiningEngine.createDefault();
            System.out.println("✓ ProcessMiningEngine created");

            // Load the OCEL2 log using try-with-resources for automatic cleanup
            try (OcelLogHandle logHandle = engine.loadOcelLog(tempLogFile)) {
                System.out.println("✓ OCEL2 log loaded successfully");

                // Get basic statistics
                int eventCount = logHandle.getEventCount();
                System.out.println("\n📊 Log Statistics:");
                System.out.println("   Total events: " + eventCount);

                // List all events
                System.out.println("\n📋 Events in log:");
                for (OcelEvent event : logHandle.getAllEvents()) {
                    System.out.println("   - " + event.getId() +
                                     " [" + event.getType() + "] at " +
                                     event.getTimestamp());
                }

                // Discover the directly-follows graph
                DirectlyFollowsGraph dfg = engine.discoverDirectlyFollowsGraph(logHandle);
                System.out.println("\n🔗 Directly-Follows Graph:");
                System.out.println("   Unique activities: " + dfg.getActivities().size());
                System.out.println("   Direct follows: " + dfg.getDirectFollows().size());

                // Print the edge details
                dfg.getDirectFollows().forEach(edge ->
                    System.out.println("   " + edge.getSourceActivity() +
                                     " → " + edge.getTargetActivity() +
                                     " [count: " + edge.getFrequency() + "]")
                );

                System.out.println("\n✓ Discovery complete!");

            } // OcelLogHandle is automatically closed here

            // Clean up the temporary file
            Files.deleteIfExists(tempLogFile);
            System.out.println("✓ Temporary files cleaned up");

        } catch (ParseException e) {
            System.err.println("✗ Failed to parse OCEL2 log: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (ProcessMiningException e) {
            System.err.println("✗ Process mining failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("✗ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates a sample OCEL2 event log with 3 events across 1 object.
     * This represents a simple order lifecycle: place → ship → place again.
     */
    private static String createSampleOcelLog() {
        return """
        {
          "objectTypes": [
            {
              "name": "order",
              "attributes": []
            }
          ],
          "eventTypes": [
            {
              "name": "place",
              "attributes": []
            },
            {
              "name": "ship",
              "attributes": []
            }
          ],
          "objects": [
            {
              "id": "o1",
              "type": "order",
              "attributes": {}
            }
          ],
          "events": [
            {
              "id": "e1",
              "type": "place",
              "time": "2024-01-15T10:00:00Z",
              "attributes": {},
              "relationships": [
                {
                  "objectId": "o1",
                  "qualifier": "main"
                }
              ]
            },
            {
              "id": "e2",
              "type": "ship",
              "time": "2024-01-15T11:00:00Z",
              "attributes": {},
              "relationships": [
                {
                  "objectId": "o1",
                  "qualifier": "main"
                }
              ]
            },
            {
              "id": "e3",
              "type": "place",
              "time": "2024-01-16T09:00:00Z",
              "attributes": {},
              "relationships": [
                {
                  "objectId": "o1",
                  "qualifier": "main"
                }
              ]
            }
          ]
        }
        """;
    }
}
```

You've just written a complete Java application that bridges to Rust! The code demonstrates:
- Initializing the Rust4PM bridge
- Creating a `ProcessMiningEngine`
- Loading an OCEL2 event log
- Querying basic statistics
- Discovering a directly-follows graph
- Proper resource cleanup with try-with-resources

## Step 4: Run It

Before running, ensure the Rust library is accessible. There are two options:

### Option A: Set the Library Path (Recommended for development)

```bash
cd /home/user/yawl
export RUST4PM_LIBRARY_PATH=/home/user/yawl/rust/target/release
mvn clean compile exec:java \
  -Dexec.mainClass="org.yawlfoundation.yawl.demo.FirstProcessMining" \
  -Drust4pm.library.path=$RUST4PM_LIBRARY_PATH
```

### Option B: Copy the Library (For deployment)

```bash
cp /home/user/yawl/rust/target/release/librust4pm.so /usr/local/lib/
sudo ldconfig
```

Then run normally:

```bash
mvn clean compile exec:java \
  -Dexec.mainClass="org.yawlfoundation.yawl.demo.FirstProcessMining"
```

### Expected Output

When successful, you'll see:

```
✓ Rust4PM bridge initialized
✓ Sample OCEL2 log created
✓ Log written to: /tmp/ocel_sample1234567.json
✓ ProcessMiningEngine created
✓ OCEL2 log loaded successfully

📊 Log Statistics:
   Total events: 3

📋 Events in log:
   - e1 [place] at 2024-01-15T10:00:00Z
   - e2 [ship] at 2024-01-15T11:00:00Z
   - e3 [place] at 2024-01-16T09:00:00Z

🔗 Directly-Follows Graph:
   Unique activities: 2
   Direct follows: 2
   place → ship [count: 1]
   ship → place [count: 1]

✓ Discovery complete!
✓ Temporary files cleaned up
```

You've just successfully analyzed a process! The directly-follows graph shows that in this log:
- The activity `place` can be followed by `ship`
- The activity `ship` can be followed by `place`

These patterns form the foundation of process mining—understanding how activities relate to each other.

## Step 5: Clean Up Resources

Notice the `try-with-resources` statement in the code:

```java
try (OcelLogHandle logHandle = engine.loadOcelLog(tempLogFile)) {
    // Use logHandle here
    // ...
} // logHandle is automatically closed here
```

This pattern is **critical** for rust4pm because:
- The `OcelLogHandle` holds a reference to a Rust object
- When Java's garbage collector cleans up, it notifies Rust to release memory
- The try-with-resources block ensures this happens immediately, not "someday"

If you forget the try-with-resources, memory will leak. Rust will keep allocating buffers, and eventually your JVM will run out of heap space.

**Always use try-with-resources** with objects that implement `AutoCloseable`.

## What's Next?

You've completed your first process mining journey! Here's where to go from here:

- **How-to Guides**: See [`how-to-guides.md`](how-to-guides.md) for recipes on:
  - Loading large OCEL2 files
  - Filtering events by time range
  - Discovering more complex process models (Petri nets, heuristic nets)
  - Exporting results to PNML format

- **API Reference**: Check [`reference-api.md`](reference-api.md) for complete documentation:
  - `ProcessMiningEngine` configuration options
  - All available discovery algorithms
  - Performance tuning parameters

- **Troubleshooting**: If you hit issues, see [`troubleshooting.md`](troubleshooting.md) for:
  - Library loading errors
  - Memory management best practices
  - Common OCEL2 validation issues

- **Architecture Deep Dive**: Ready for the details? Read [`architecture.md`](architecture.md) to understand:
  - How the Java-Rust FFI works
  - The Panama bridge design
  - Performance characteristics and benchmarks

## Summary

In this tutorial, you:

1. ✓ Built the native Rust library
2. ✓ Added the Maven dependency to your project
3. ✓ Created a complete Java application using rust4pm
4. ✓ Ran your first process discovery
5. ✓ Learned proper resource cleanup patterns

The rust4pm bridge unlocks Rust's speed and safety for YAWL's process mining stack. You're now ready to build real applications on top of it!

Happy mining! 🚀
