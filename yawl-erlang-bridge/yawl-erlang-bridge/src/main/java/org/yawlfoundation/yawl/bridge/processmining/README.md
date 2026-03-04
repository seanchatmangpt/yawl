# Process Mining JNI Bridge

This package provides Java JNI wrapper classes for the Rust-based process mining library. All process mining logic is implemented in Rust and called through JNI interfaces.

## Components

### Core Classes

- **XesImporter**: Imports XES event log files into native format
- **AlphaMiner**: Discovers process models using Alpha++ algorithm
- **ConformanceChecker**: Checks conformance between event logs and models
- **ProcessMiningFactory**: Factory for creating component instances

### Data Classes

- **EventLogHandle**: Handle to an imported event log (native pointer)
- **PetriNet**: Represents a discovered Petri net in PNML format
- **ConformanceResult**: Result of conformance checking with fitness score
- **ProcessMiningException**: Exception for JNI-related errors

## Usage Example

```java
import org.yawlfoundation.yawl.bridge.processmining.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProcessMiningDemo {
    public static void main(String[] args) {
        try {
            // Initialize components
            XesImporter importer = ProcessMiningFactory.getXesImporter();
            AlphaMiner miner = ProcessMiningFactory.getAlphaMiner();
            ConformanceChecker checker = ProcessMiningFactory.getConformanceChecker();

            // Import XES file
            Path xesFile = Paths.get("event_log.xes");
            EventLogHandle eventLog = importer.importXesFile(xesFile);

            // Discover process model
            PetriNet petriNet = miner.discover(eventLog);

            // Check conformance
            ConformanceResult result = checker.check(eventLog, petriNet);

            System.out.println("Fitness: " + result.getFitness());

        } catch (ProcessMiningException e) {
            e.printStackTrace();
        }
    }
}
```

## Native Library Requirements

The Java code expects a native library named `libyawl_process_mining.so` (Linux) or `yawl_process_mining.dll` (Windows) to be available in the library path.

### Loading the Library

```java
static {
    System.loadLibrary("yawl_process_mining");
}
```

## Error Handling

All JNI methods throw `ProcessMiningException` on failure:

```java
try {
    EventLogHandle log = importer.importXesFile(path);
} catch (ProcessMiningException e) {
    System.err.println("Import failed: " + e.getMessage());
}
```

## Validation

All classes perform input validation:

- File existence and format checks
- Handle validity checks
- XML structure validation for PNML
- Fitness value range validation

## Thread Safety

The classes are designed to be thread-safe:

- Factory uses double-checked locking
- Data classes are immutable
- Handles are thread-safe once created