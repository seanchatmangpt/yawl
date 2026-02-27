# Tutorial: Schema Modelling with DataModellingBridge

By the end of this tutorial you will have built a working Java program that opens a DataModellingBridge, parses an ODCS YAML data contract, imports SQL tables, manages domains, creates architecture decisions, and exports workspaces back to ODCS YAML — all without leaving the JVM or calling any external service.

---

## Prerequisites

- YAWL built locally: `bash scripts/dx.sh -pl yawl-data-modelling` succeeds
- GraalVM JDK 24.1+ at runtime (required for WebAssembly support; Temurin and other OpenJDK distributions do not include wasm-core)
- Maven dependency available in your project

---

## Step 0: Add the Maven dependency

Add the yawl-data-modelling dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-data-modelling</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

Verify the dependency resolves:

```bash
mvn dependency:tree | grep data-modelling
```

Expected output includes `yawl-data-modelling:jar:6.0.0-GA`.

---

## Step 1: Open the bridge

Create a new Java class called `DataModellingDemo.java` in your project:

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;

public class DataModellingDemo {

    public static void main(String[] args) {
        try (DataModellingBridge bridge = new DataModellingBridge()) {
            System.out.println("Bridge opened successfully");
            // All subsequent steps go here
        }
    }
}
```

The `DataModellingBridge` implements `AutoCloseable`, so use a try-with-resources block. By default, the bridge uses a pool size of 1 (suitable for sequential operations). When the block exits, all resources are automatically released.

Compile and run:

```bash
javac -cp target/classes DataModellingDemo.java
java -cp target/classes:. DataModellingDemo
```

Expected output:

```
Bridge opened successfully
```

---

## Step 2: Parse an ODCS data contract

Inside the try block, add code to parse an ODCS YAML specification into a workspace JSON representation:

```java
String yaml = """
    apiVersion: v3.1.0
    kind: DataContract
    name: customers
    owner: analytics-team
    schema:
      fields:
        - name: id
          type: bigint
          description: Customer unique identifier
          required: true
        - name: email
          type: string
          description: Customer email address
          required: true
        - name: created_at
          type: timestamp
          description: Account creation timestamp
    """;

String workspace = bridge.parseOdcsYaml(yaml);
System.out.println("Parsed workspace JSON (first 120 chars):");
System.out.println(workspace.substring(0, Math.min(120, workspace.length())) + "...");
```

The bridge's `parseOdcsYaml()` method returns a JSON string representation of the workspace. This JSON can be passed to subsequent operations or stored for later use.

Expected output (JSON structure, exact content varies):

```
Parsed workspace JSON (first 120 chars):
{"id":"550e8400-e29b-41d4-a716-446655440000","name":"customers","owner":"analytics-team","tables":[{"id":"...
```

---

## Step 3: Import a SQL CREATE TABLE statement

Add code to import SQL DDL into a workspace:

```java
String sql = """
    CREATE TABLE orders (
        id BIGINT PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        total DECIMAL(10, 2),
        status VARCHAR(50),
        created_at TIMESTAMP
    );
    """;

String sqlWorkspace = bridge.importFromSql(sql, "postgres");
System.out.println("SQL import result (first 120 chars):");
System.out.println(sqlWorkspace.substring(0, Math.min(120, sqlWorkspace.length())) + "...");
```

The `importFromSql()` method accepts SQL dialect names: `"postgres"`, `"mysql"`, `"sqlite"`, `"generic"`, or `"databricks"`. The returned workspace JSON includes a table definition extracted from the CREATE TABLE statement.

Expected output:

```
SQL import result (first 120 chars):
{"id":"550e8400-e29b-41d4-a716-446655440001","tables":[{"id":"550e8400-e29b-41d4-a716-446655440002","name":"...
```

---

## Step 4: Create a workspace and add a domain to it

Create a new workspace and a business domain, then associate them:

```java
// Create an empty workspace
String myWorkspace = bridge.createWorkspace("data-platform", "platform-team");
System.out.println("Created workspace: " +
    myWorkspace.substring(0, Math.min(100, myWorkspace.length())) + "...");

// Create a domain
String domain = bridge.createDomain("sales");
System.out.println("Created domain: " +
    domain.substring(0, Math.min(100, domain.length())) + "...");

// Add domain to workspace
// Extract the domain ID from the domain JSON (in practice, parse the JSON)
// For this tutorial, we use a sample UUID
String domainId = "550e8400-e29b-41d4-a716-446655440003";
String updatedWorkspace = bridge.addDomainToWorkspace(myWorkspace, domainId, "sales");
System.out.println("Workspace now contains domain: " + updatedWorkspace.contains("sales"));
```

Each domain is a logical grouping of tables and systems. The `addDomainToWorkspace()` method returns an updated workspace JSON that includes the domain reference.

Expected output:

```
Created workspace: {"id":"550e8400-e29b-41d4-a716-446655440004","name":"data-platform","owner":"platform-team"...
Created domain: {"id":"550e8400-e29b-41d4-a716-446655440003","name":"sales","tables":[],"systems":[],"created"...
Workspace now contains domain: true
```

---

## Step 5: Create an Architecture Decision Record (ADR)

Generate an MADR-compliant decision record and export it to YAML:

```java
String decision = bridge.createDecision(
    1,
    "Use PostgreSQL for primary storage",
    "The platform needs a reliable ACID-compliant database for workflow state " +
    "and analytics. Data grows at 5GB/month with 500 concurrent users.",
    "Adopt PostgreSQL 16 with read replicas for horizontal scaling. " +
    "Use pgBouncer connection pooling and WAL archival to S3.",
    "Platform Team"
);

String decisionYaml = bridge.exportDecisionToYaml(decision);
System.out.println("ADR YAML export:");
System.out.println(decisionYaml);
```

The bridge creates a decision record with a sequential number, title, problem context, chosen solution, and author. The `exportDecisionToYaml()` method produces YAML suitable for storing in version control.

Expected output (MADR format):

```yaml
# ADR-0001: Use PostgreSQL for primary storage

Date: 2026-02-27

## Status
Accepted

## Context
The platform needs a reliable ACID-compliant database for workflow state and analytics. Data grows at 5GB/month with 500 concurrent users.

## Decision
Adopt PostgreSQL 16 with read replicas for horizontal scaling. Use pgBouncer connection pooling and WAL archival to S3.

## Consequences
- Positive: ACID guarantees, mature tooling, community support
- Negative: Operational overhead for replication management
- Risk: Data loss if replication lag not monitored

Author: Platform Team
Date: 2026-02-27
```

---

## Step 6: Export the workspace back to ODCS YAML

Convert the workspace JSON back to ODCS YAML format:

```java
String odcsYaml = bridge.exportOdcsYamlV2(workspace);
System.out.println("Exported ODCS YAML:");
System.out.println(odcsYaml);
```

The `exportOdcsYamlV2()` method converts any workspace JSON into ODCS v3.1.0 YAML — suitable for sharing with data governance teams, publishing to a data contract registry, or storing in git.

Expected output (truncated for readability):

```yaml
apiVersion: v3.1.0
kind: DataContract
metadata:
  name: customers
  owner: analytics-team
  version: "1.0"
  description: Customer master data
schema:
  fields:
    - name: id
      type: bigint
      description: Customer unique identifier
      required: true
    - name: email
      type: string
      description: Customer email address
      required: true
    - name: created_at
      type: timestamp
      description: Account creation timestamp
```

---

## Complete program

Here is the full working example with all steps assembled:

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;

public class DataModellingDemo {

    public static void main(String[] args) {
        try (DataModellingBridge bridge = new DataModellingBridge()) {
            System.out.println("=== DataModellingBridge Tutorial ===\n");

            // Step 2: Parse ODCS YAML
            String yaml = """
                apiVersion: v3.1.0
                kind: DataContract
                name: customers
                owner: analytics-team
                schema:
                  fields:
                    - name: id
                      type: bigint
                    - name: email
                      type: string
                    - name: created_at
                      type: timestamp
                """;
            String workspace = bridge.parseOdcsYaml(yaml);
            System.out.println("Step 2: Parsed ODCS YAML\n");

            // Step 3: Import SQL
            String sql = """
                CREATE TABLE orders (
                    id BIGINT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    total DECIMAL(10, 2),
                    status VARCHAR(50)
                );
                """;
            String sqlWorkspace = bridge.importFromSql(sql, "postgres");
            System.out.println("Step 3: Imported SQL\n");

            // Step 4: Create workspace and domain
            String myWorkspace = bridge.createWorkspace("data-platform", "platform-team");
            String domain = bridge.createDomain("sales");
            String domainId = "550e8400-e29b-41d4-a716-446655440003";
            String updatedWorkspace = bridge.addDomainToWorkspace(
                myWorkspace, domainId, "sales");
            System.out.println("Step 4: Created workspace and domain\n");

            // Step 5: Create ADR
            String decision = bridge.createDecision(
                1,
                "Use PostgreSQL for primary storage",
                "The platform needs a reliable ACID-compliant database.",
                "Adopt PostgreSQL 16 with read replicas for horizontal scaling.",
                "Platform Team"
            );
            String decisionYaml = bridge.exportDecisionToYaml(decision);
            System.out.println("Step 5: Created Architecture Decision Record");
            System.out.println(decisionYaml);
            System.out.println();

            // Step 6: Export to ODCS YAML
            String odcsYaml = bridge.exportOdcsYamlV2(workspace);
            System.out.println("Step 6: Exported workspace to ODCS YAML");
            System.out.println(odcsYaml);
            System.out.println();

            System.out.println("=== Tutorial Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

Compile and run:

```bash
javac -cp target/classes DataModellingDemo.java
java -cp target/classes:. DataModellingDemo
```

Expected flow:

1. Bridge opens (logs to DEBUG if logging is configured)
2. ODCS YAML is parsed into JSON
3. SQL CREATE TABLE is imported
4. Workspace and domain are created and linked
5. Architecture Decision Record is generated in YAML format
6. Workspace is exported back to ODCS YAML
7. Bridge closes and releases all resources

---

## You have now

- Created a Java program that uses DataModellingBridge as an `AutoCloseable` resource
- Parsed ODCS v3.1.0 YAML into workspace JSON
- Imported SQL DDL from multiple database dialects
- Created and managed business domains within a workspace
- Generated MADR-compliant architecture decision records
- Exported a workspace back to ODCS YAML format
- Run all of this computation **entirely inside the JVM** — no external services, no network calls, no API latency. All schema operations execute inside `data_modelling_wasm_bg.wasm`, a Rust-compiled WebAssembly module, using GraalVM's polyglot engine.

---

## What next

- **Working with existing workspaces**: Load workspace YAML with `parseWorkspaceYaml()` and add relationships with `addRelationshipToWorkspace()`
- **Import other formats**: Use `importFromAvro()`, `importFromJsonSchema()`, `importFromProtobuf()`, or `importFromCads()` to work with different schema languages
- **Knowledge base**: Document design decisions and domain knowledge with `createKnowledgeArticle()` and `createKnowledgeIndex()`
- **Advanced validation**: Check for circular dependencies with `checkCircularDependency()` and naming conflicts with `detectNamingConflicts()`
- **Explanation Guide** (`docs/explanations/data-modelling-architecture.md`) — understand how the bridge works, what ODCS is, and when to use each import/export method
