# How-To: Validate Data Models with yawl-data-modelling

This guide shows you how to use the **DataModellingBridge** to import schemas, validate data contracts, and export domain models for use in YAWL workflows.

---

## Task 1: Import and Validate an SQL Database Schema

### Goal

Read an SQL database schema and validate it against ODCS (Open Data Contracts Standard) specifications.

### Setup

Create a sample database schema file `database.sql`:

```sql
CREATE TABLE customers (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id INT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT NOT NULL,
    amount DECIMAL(10, 2),
    status VARCHAR(50),
    created_at TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE order_items (
    id INT PRIMARY KEY AUTO_INCREMENT,
    order_id INT NOT NULL,
    product_name VARCHAR(255),
    quantity INT,
    unit_price DECIMAL(10, 2),
    FOREIGN KEY (order_id) REFERENCES orders(id)
);
```

### Java Implementation

```java
import org.yawlfoundation.yawl.datamodelling.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ValidateDatabaseSchema {
    public static void main(String[] args) throws Exception {
        try (DataModellingBridge bridge = new DataModellingBridge()) {
            // Step 1: Read SQL schema
            String sqlSchema = Files.readString(Path.of("database.sql"));

            // Step 2: Import SQL schema
            String importResult = bridge.importSql(sqlSchema);
            System.out.println("Import result: " + importResult);

            // Step 3: Parse the imported schema
            String workspace = bridge.parseSqlToWorkspace(sqlSchema);
            System.out.println("Workspace JSON:\n" + workspace);

            // Step 4: Validate the workspace
            String validationReport = bridge.validateWorkspace(workspace);
            System.out.println("Validation report:\n" + validationReport);

            // Step 5: Export to ODCS YAML
            String odcsYaml = bridge.exportToOdcs(workspace);
            Files.writeString(Path.of("schema-export.yaml"), odcsYaml);
            System.out.println("Exported to schema-export.yaml");
        }
    }
}
```

### Expected Output

```
Import result: {"status": "success", "tables_imported": 3}
Workspace JSON:
{
  "tables": [
    {
      "name": "customers",
      "columns": [
        {"name": "id", "type": "integer", "primary_key": true},
        {"name": "name", "type": "string", "required": true},
        {"name": "email", "type": "string", "unique": true},
        {"name": "created_at", "type": "timestamp"}
      ]
    },
    {
      "name": "orders",
      "columns": [...],
      "relationships": [
        {
          "name": "customer_id",
          "references": "customers.id",
          "type": "foreign_key"
        }
      ]
    }
  ]
}

Validation report:
{
  "status": "valid",
  "issues": [],
  "statistics": {
    "tables": 3,
    "columns": 11,
    "relationships": 2
  }
}
```

---

## Task 2: Create a Domain Model with ODCS

### Goal

Create a structured domain model using ODCS specifications and validate relationships.

### ODCS Input File: customer-domain.yaml

```yaml
apiVersion: v1
kind: DataModel
name: CustomerDomain
version: "1.0.0"

metadata:
  owner: Sales Team
  description: Customer and order management domain
  created: 2026-02-28

tables:
  - name: customers
    description: Customer master data
    columns:
      - name: id
        type: integer
        required: true
        primary: true
      - name: name
        type: string
        required: true
        maxLength: 255
      - name: email
        type: string
        unique: true
        pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
      - name: customer_tier
        type: enum
        values: [gold, silver, bronze]
        default: silver

  - name: orders
    description: Customer orders
    columns:
      - name: id
        type: integer
        required: true
        primary: true
      - name: customer_id
        type: integer
        required: true
        references:
          table: customers
          column: id
      - name: order_date
        type: timestamp
        required: true
      - name: total_amount
        type: decimal
        precision: 10
        scale: 2
      - name: status
        type: enum
        values: [pending, confirmed, shipped, delivered, cancelled]
        default: pending

relationships:
  - name: customer_orders
    from:
      table: customers
      cardinality: "1"
    to:
      table: orders
      cardinality: "*"
    description: A customer can have many orders

validationRules:
  - name: order_total_positive
    rule: "total_amount > 0"
    severity: error
  - name: order_date_not_future
    rule: "order_date <= NOW()"
    severity: warning
```

### Java Implementation

```java
import org.yawlfoundation.yawl.datamodelling.*;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class CreateDomainModel {
    public static void main(String[] args) throws Exception {
        try (DataModellingBridge bridge = new DataModellingBridge()) {
            // Step 1: Read ODCS YAML
            String odcsYaml = Files.readString(Path.of("customer-domain.yaml"));

            // Step 2: Parse ODCS to workspace
            String workspace = bridge.parseOdcsToWorkspace(odcsYaml);
            System.out.println("Parsed workspace from ODCS");

            // Step 3: Validate the domain model
            String validationResult = bridge.validateWorkspace(workspace);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(validationResult));

            // Step 4: Extract tables and relationships
            String tables = bridge.extractTables(workspace);
            String relationships = bridge.extractRelationships(workspace);

            System.out.println("Tables: " + tables);
            System.out.println("Relationships: " + relationships);

            // Step 5: Export validation rules to JSON
            String rules = bridge.extractValidationRules(workspace);
            Files.writeString(Path.of("validation-rules.json"), rules);
        }
    }
}
```

---

## Task 3: Manage Domains and Create Architecture Decision Records (ADRs)

### Goal

Organize multiple data models into domains and document architectural decisions.

### Java Implementation

```java
import org.yawlfoundation.yawl.datamodelling.*;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;

public class ManageDomainsWithAdr {
    public static void main(String[] args) throws Exception {
        try (DataModellingBridge bridge = new DataModellingBridge()) {
            // Step 1: Create domains
            String salesDomain = """
                {
                  "name": "Sales Domain",
                  "tables": ["customers", "orders", "order_items"],
                  "owner": "Sales Team"
                }
                """;

            String inventoryDomain = """
                {
                  "name": "Inventory Domain",
                  "tables": ["products", "stock_levels", "warehouses"],
                  "owner": "Logistics Team"
                }
                """;

            // Step 2: Register domains
            String domain1Id = bridge.createDomain("sales", salesDomain);
            String domain2Id = bridge.createDomain("inventory", inventoryDomain);
            System.out.println("Created domains: " + domain1Id + ", " + domain2Id);

            // Step 3: Create ADR (Architecture Decision Record)
            String adr = """
                {
                  "id": "ADR-001",
                  "title": "Use domains to organize data models",
                  "status": "accepted",
                  "context": "Multiple data models need to be organized by business domain",
                  "decision": "Organize data models into Sales, Inventory, and Fulfillment domains",
                  "consequences": [
                    "Domain boundaries are explicit",
                    "Cross-domain relationships require explicit mappings",
                    "Each domain can have independent validation rules"
                  ],
                  "date": "${timestamp}"
                }
                """;

            String adrId = bridge.createAdr("sales-domain-organization", adr);
            System.out.println("Created ADR: " + adrId);

            // Step 4: Link ADR to domain
            bridge.linkAdrToDomain(domain1Id, adrId);
            System.out.println("Linked ADR to domain");

            // Step 5: Export all domains
            String export = bridge.exportAllDomains();
            Files.writeString(Path.of("domains-export.json"), export);
            System.out.println("Exported domains to domains-export.json");
        }
    }
}
```

---

## Task 4: Validate Data Against Schema in YAWL Workflow

### Goal

Use data model validation within a YAWL task to ensure incoming data matches the schema.

### Java Workflow Task Implementation

```java
import org.yawlfoundation.yawl.core.YWorkItem;
import org.yawlfoundation.yawl.datamodelling.*;

public class DataValidationTaskHandler {
    private DataModellingBridge bridge;

    public DataValidationTaskHandler() throws Exception {
        this.bridge = new DataModellingBridge();
    }

    public void handleWorkItem(YWorkItem workItem) throws Exception {
        // Step 1: Get incoming data from workflow
        String incomingData = workItem.getDataVariable("customer_data");
        System.out.println("Received: " + incomingData);

        // Step 2: Load domain model
        String domainModel = bridge.getDomain("sales");

        // Step 3: Validate incoming data against model
        String validationResult = bridge.validateData(
            incomingData,
            domainModel,
            "customers"  // table name
        );

        // Step 4: Parse validation result
        if (validationResult.contains("\"status\": \"valid\"")) {
            System.out.println("Data validation passed");
            workItem.setDataVariable("validation_status", "passed");
        } else {
            System.out.println("Data validation failed: " + validationResult);
            workItem.setDataVariable("validation_status", "failed");
            workItem.setDataVariable("validation_errors", validationResult);
        }
    }

    public void shutdown() throws Exception {
        bridge.close();
    }
}
```

### YAWL Specification Using Validation

```xml
<specification>
  <net id="DataValidationNet">
    <task id="ReceiveCustomerData">
      <name>Receive Customer Data</name>
      <flowsInto>
        <nextElementRef id="ValidateData" />
      </flowsInto>
    </task>

    <task id="ValidateData">
      <name>Validate Data</name>
      <decomposesTo>DataValidationTaskHandler</decomposesTo>
      <flowsInto>
        <nextElementRef id="CheckValidation" />
      </flowsInto>
    </task>

    <condition id="CheckValidation">
      <name>Valid?</name>
      <flowsInto>
        <nextElementRef id="ProcessCustomer" />
        <isDefaultFlow>false</isDefaultFlow>
        <predicate>validation_status = 'passed'</predicate>
      </flowsInto>
      <flowsInto>
        <nextElementRef id="RejectData" />
        <isDefaultFlow>true</isDefaultFlow>
      </flowsInto>
    </condition>

    <task id="ProcessCustomer">
      <name>Process Valid Customer</name>
      <flowsInto>
        <nextElementRef id="End" />
      </flowsInto>
    </task>

    <task id="RejectData">
      <name>Reject Invalid Data</name>
      <flowsInto>
        <nextElementRef id="End" />
      </flowsInto>
    </task>
  </net>
</specification>
```

---

## Best Practices

### 1. Version Your Domain Models

```java
String versionedDomain = """
    {
      "version": "2.0.0",
      "prev_version": "1.0.0",
      "changes": [
        "Added email_verified column to customers",
        "Renamed order_status to status"
      ]
    }
    """;
```

### 2. Document Relationships Explicitly

```java
String relationship = """
    {
      "name": "customer_orders",
      "cardinality": "1:*",
      "from": {"table": "customers", "key": "id"},
      "to": {"table": "orders", "key": "customer_id"},
      "constraints": {
        "cascade_delete": false,
        "cascade_update": true
      }
    }
    """;
```

### 3. Separate Domain Models from Application Models

Keep domain models focused on business data structure, separate from application-specific concerns.

---

## Next Steps

- [How-To: Evaluate DMN Decisions with DataModel Validation](evaluate-dmn-decisions.md)
- [Reference: DataModellingBridge API](../reference/data-modelling-api.md)
