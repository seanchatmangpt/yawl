# Tutorial: YAWL Stateless Engine Getting Started

By the end of this tutorial you will have a running YAWL stateless engine, understand how stateless mode differs from the persistence-based engine, deploy a workflow specification, and execute a case using event-driven execution.

---

## What is Stateless Mode?

The YAWL stateless engine (`YStatelessEngine`) is an event-driven alternative to the traditional persistence-based `YEngine`. Instead of storing case state in a database, it reconstructs state on-the-fly from a stream of events. This enables:

- **Horizontal scalability**: No shared database bottleneck
- **Event sourcing**: Full audit trail of all case changes
- **Cloud-native**: Stateless services in Kubernetes
- **Lambda-friendly**: Execution without persistent storage

---

## Prerequisites

You need the following installed and on your PATH.

```bash
java -version
```

Expected: `openjdk version "25.x.x"`. YAWL stateless engine requires Java 25 or higher.

```bash
mvn -version
```

Expected: `Apache Maven 3.9.x` or higher.

---

## Step 1: Build the YAWL Project

Clone and build YAWL to make the stateless engine library available.

```bash
cd /workspace
git clone https://github.com/yawlfoundation/yawl.git
cd yawl
mvn clean compile -DskipTests -T 1.5C
```

The stateless module builds to `/yawl-stateless/target/yawl-stateless-6.0.0-GA.jar`.

---

## Step 2: Create a Simple Java Application

Create a new Maven project to use the stateless engine:

```bash
mkdir -p ~/yawl-stateless-demo
cd ~/yawl-stateless-demo
cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.example</groupId>
    <artifactId>yawl-stateless-demo</artifactId>
    <version>1.0</version>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-stateless</artifactId>
            <version>6.0.0-GA</version>
        </dependency>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-utilities</artifactId>
            <version>6.0.0-GA</version>
        </dependency>
    </dependencies>
</project>
EOF
```

---

## Step 3: Create a YAWL Specification

Create a simple two-task workflow:

```bash
mkdir -p src/main/resources
cat > src/main/resources/simple-process.yawl << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                  http://www.yawlfoundation.org/yawlschema/YAWL_Schema2.3.xsd">
    <specification uri="SimpleProcess" name="Simple Process">
        <documentation/>
        <metaData/>
        <net id="SimpleNet">
            <!-- Input condition -->
            <localVariable>
                <index>0</index>
                <name>caseID</name>
                <initialValue/>
                <type>string</type>
            </localVariable>

            <inputCondition id="InputCondition">
                <flowInto node="Task1"/>
            </inputCondition>

            <!-- First task -->
            <task id="Task1" name="Process Application">
                <flowInto node="Task2"/>
            </task>

            <!-- Second task -->
            <task id="Task2" name="Review Application">
                <flowInto node="OutputCondition"/>
            </task>

            <!-- Output condition -->
            <outputCondition id="OutputCondition"/>
        </net>

        <layout>
            <!-- Visual layout coordinates (optional) -->
        </layout>
    </specification>
</specificationSet>
EOF
```

---

## Step 4: Load and Execute a Specification

Create a Java application that loads and executes the specification:

```bash
mkdir -p src/main/java/org/example
cat > src/main/java/org/example/StatelessDemo.java << 'EOF'
package org.example;

import org.yawlfoundation.yawl.stateless.engine.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.util.YawlXMLUtils;
import java.io.InputStream;

public class StatelessDemo {
    public static void main(String[] args) throws Exception {
        // Load the specification
        InputStream specStream = StatelessDemo.class
            .getResourceAsStream("/simple-process.yawl");

        String specXML = new String(specStream.readAllBytes());
        YSpecification spec = YawlXMLUtils.unmarshalSpecification(specXML);

        // Create stateless engine
        YStatelessEngine engine = new YStatelessEngine();

        // Start net runner for the first net in the specification
        YNetRunner netRunner = engine.newNetRunner(spec.getNet("SimpleNet"));

        System.out.println("Specification loaded: " + spec.getURI());
        System.out.println("Net started: " + netRunner.getNet().getID());

        // Notify start event to trigger input condition
        netRunner.notifyEngineStartup();

        // Get enabled work items (tasks ready for execution)
        var enabledItems = netRunner.getEnabledWorkItems();
        System.out.println("Enabled work items: " + enabledItems.size());

        enabledItems.forEach(item -> {
            System.out.println("  - " + item.getTaskName() + " (" + item.getID() + ")");
        });

        // Complete first task
        if (!enabledItems.isEmpty()) {
            var firstItem = enabledItems.iterator().next();
            firstItem.setData("<output/>");
            netRunner.completeWorkItem(firstItem.getID(), firstItem.getData());

            System.out.println("\nCompleted: " + firstItem.getTaskName());

            // Get next enabled items
            var nextItems = netRunner.getEnabledWorkItems();
            System.out.println("Next enabled items: " + nextItems.size());
            nextItems.forEach(item -> {
                System.out.println("  - " + item.getTaskName() + " (" + item.getID() + ")");
            });
        }

        System.out.println("\nStateless execution complete!");
    }
}
EOF
```

---

## Step 5: Compile and Run

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.StatelessDemo"
```

Expected output:

```
Specification loaded: SimpleProcess
Net started: SimpleNet
Enabled work items: 1
  - Process Application (...)
Completed: Process Application
Next enabled items: 1
  - Review Application (...)

Stateless execution complete!
```

---

## Key Concepts

### Event-Driven Execution

The stateless engine processes cases purely via event calls:
- No intermediate state persistence
- State is reconstructed from event history
- Perfect for serverless and containerized deployment

### YNetRunner

The `YNetRunner` manages execution of a single net instance:
- `notifyEngineStartup()`: Activates input conditions
- `getEnabledWorkItems()`: Returns tasks ready for execution
- `completeWorkItem()`: Records task completion and triggers next tasks

### Data Flow

Work item data flows through XML:
- Input data is provided when tasks are instantiated
- Output data is set when tasks complete
- Data is validated against type schema

---

## Next Steps

1. **Deploy to Production**: See [how-to/deployment/stateless-deployment.md](../how-to/deployment/stateless-deployment.md)
2. **Add Event Persistence**: See [how-to/enable-stateless-persistence.md](../how-to/enable-stateless-persistence.md)
3. **Integrate with REST**: See [how-to/deployment/stateless-rest-api.md](../how-to/deployment/stateless-rest-api.md)
4. **Understand Architecture**: See [explanation/stateless-architecture.md](../explanation/stateless-architecture.md)

---

## Troubleshooting

### ClassNotFoundException for YStatelessEngine

**Problem**: `java.lang.ClassNotFoundException: org.yawlfoundation.yawl.stateless.engine.YStatelessEngine`

**Solution**: Verify the yawl-stateless JAR is on the classpath:
```bash
mvn dependency:tree | grep yawl-stateless
```

### Specification Unmarshalling Fails

**Problem**: `Error unmarshalling specification: Document is null`

**Solution**: Verify the YAWL specification XML is valid:
```bash
xmllint --noout src/main/resources/simple-process.yawl
```

---

## Further Reading

- **[Stateless Engine Reference](../reference/stateless-engine-reference.md)**: Complete API documentation
- **[Event-Sourced Architecture](../explanation/event-sourced-architecture.md)**: Design patterns and trade-offs
- **[Workflow Patterns](../reference/workflow-patterns.md)**: Common patterns and best practices
