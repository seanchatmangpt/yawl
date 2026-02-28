# yawl-data-modelling API Reference

**Module**: `yawl-data-modelling` | **Version**: 6.0.0-GA | **Package**: `org.yawlfoundation.yawl.datamodelling`

The **data-modelling** module provides a thin Java facade over the **data-modelling-sdk** WebAssembly (Rust). It exposes 70+ schema operations for ODCS, OpenAPI, BPMN, and DMN import-export without reimplementing logic in Java.

---

## Core Classes

### DataModellingBridge

Main entry point for all data modelling operations. Manages the WebAssembly execution context and WASM module lifecycle.

```java
public class DataModellingBridge implements AutoCloseable {
    /**
     * Create a bridge with default configuration
     * Uses GraalVM JS+WASM polyglot context
     */
    public DataModellingBridge() throws IOException;

    /**
     * Create a bridge with custom configuration
     */
    public DataModellingBridge(DataModellingConfig config) throws IOException;

    // ============== ODCS Operations ==============

    /**
     * Parse ODCS YAML data contract to workspace JSON
     * @param odcsYaml ODCS YAML specification
     * @return workspace JSON representation
     */
    public String parseOdcsToWorkspace(String odcsYaml);

    /**
     * Export workspace to ODCS YAML
     */
    public String exportToOdcs(String workspace);

    /**
     * Validate ODCS specification
     */
    public String validateOdcs(String odcsYaml);

    // ============== SQL Import ==============

    /**
     * Import SQL DDL statements to workspace
     * @param sqlDdl SQL CREATE TABLE statements
     * @return workspace JSON
     */
    public String importSql(String sqlDdl);

    /**
     * Parse SQL schema to workspace (alias for importSql)
     */
    public String parseSqlToWorkspace(String sqlDdl);

    /**
     * Export workspace to SQL DDL
     */
    public String exportToSql(String workspace);

    // ============== OpenAPI Integration ==============

    /**
     * Import OpenAPI 3.0 specification
     */
    public String importOpenApi(String openApiYaml);

    /**
     * Export workspace to OpenAPI
     */
    public String exportToOpenApi(String workspace);

    /**
     * Validate OpenAPI specification
     */
    public String validateOpenApi(String openApiYaml);

    // ============== BPMN Integration ==============

    /**
     * Import BPMN 2.0 specification
     */
    public String importBpmn(String bpmnXml);

    /**
     * Export data model to BPMN data objects
     */
    public String exportToBpmn(String workspace);

    // ============== DMN Integration ==============

    /**
     * Import DMN 1.3 decision tables
     */
    public String importDmn(String dmnXml);

    /**
     * Export data model to DMN
     */
    public String exportToDmn(String workspace);

    /**
     * Map DMN input/output to data model columns
     */
    public String mapDmnToDataModel(String dmnXml, String workspace);

    // ============== Workspace Management ==============

    /**
     * Validate workspace integrity
     * Checks table references, column types, relationship constraints
     * @return validation report JSON
     */
    public String validateWorkspace(String workspace);

    /**
     * Get workspace statistics
     */
    public String getWorkspaceStats(String workspace);

    /**
     * Transform workspace (e.g., normalize names, add defaults)
     */
    public String transformWorkspace(String workspace, String transformRules);

    // ============== Table Operations ==============

    /**
     * Extract all tables from workspace
     */
    public String extractTables(String workspace);

    /**
     * Get table schema by name
     */
    public String getTable(String workspace, String tableName);

    /**
     * Add table to workspace
     */
    public String addTable(String workspace, String tableJson);

    /**
     * Rename table
     */
    public String renameTable(String workspace, String oldName, String newName);

    /**
     * Delete table
     */
    public String deleteTable(String workspace, String tableName);

    // ============== Column Operations ==============

    /**
     * Extract all columns from workspace
     */
    public String extractColumns(String workspace);

    /**
     * Get columns for specific table
     */
    public String getTableColumns(String workspace, String tableName);

    /**
     * Add column to table
     */
    public String addColumn(String workspace, String tableName, String columnJson);

    /**
     * Modify column (type, constraints, etc.)
     */
    public String modifyColumn(String workspace, String tableName,
                                String columnName, String columnJson);

    /**
     * Delete column
     */
    public String deleteColumn(String workspace, String tableName,
                                String columnName);

    // ============== Relationship Management ==============

    /**
     * Extract all relationships from workspace
     */
    public String extractRelationships(String workspace);

    /**
     * Add foreign key relationship
     */
    public String addRelationship(String workspace, String relationshipJson);

    /**
     * Delete relationship
     */
    public String deleteRelationship(String workspace, String relationshipName);

    /**
     * Validate referential integrity
     */
    public String validateReferentialIntegrity(String workspace);

    // ============== Validation Rules ==============

    /**
     * Extract all validation rules from workspace
     */
    public String extractValidationRules(String workspace);

    /**
     * Add validation rule (e.g., column constraints)
     */
    public String addValidationRule(String workspace, String ruleJson);

    /**
     * Validate data against workspace schema
     * @param data JSON data to validate
     * @param workspace the data model schema
     * @param tableName table to validate against
     * @return validation report
     */
    public String validateData(String data, String workspace,
                                String tableName);

    // ============== Domain Management ==============

    /**
     * Create a domain (logical grouping of tables)
     */
    public String createDomain(String domainId, String domainJson);

    /**
     * Get domain by ID
     */
    public String getDomain(String domainId);

    /**
     * Update domain
     */
    public String updateDomain(String domainId, String domainJson);

    /**
     * Delete domain
     */
    public String deleteDomain(String domainId);

    /**
     * List all domains
     */
    public String listDomains();

    /**
     * Export all domains
     */
    public String exportAllDomains();

    // ============== Architecture Decision Records (ADRs) ==============

    /**
     * Create an ADR
     * @param adrId unique identifier
     * @param adrJson ADR content with title, context, decision, consequences
     * @return ADR ID
     */
    public String createAdr(String adrId, String adrJson);

    /**
     * Get ADR
     */
    public String getAdr(String adrId);

    /**
     * List ADRs for a domain
     */
    public String listAdrs(String domainId);

    /**
     * Link ADR to domain
     */
    public String linkAdrToDomain(String domainId, String adrId);

    // ============== Sketches (Visual Diagrams) ==============

    /**
     * Create a sketch (e.g., ER diagram)
     */
    public String createSketch(String sketchId, String sketchJson);

    /**
     * Export workspace as ER diagram
     */
    public String exportAsErDiagram(String workspace);

    /**
     * Export as entity-relationship diagram image
     */
    public byte[] exportAsDiagramImage(String workspace, String imageFormat);

    // ============== Knowledge Base ==============

    /**
     * Store domain knowledge (business rules, glossary)
     */
    public String storeKnowledge(String domainId, String knowledgeJson);

    /**
     * Query knowledge base
     */
    public String queryKnowledge(String query);

    /**
     * Get glossary
     */
    public String getGlossary(String domainId);

    // ============== Lifecycle & Resource Management ==============

    /**
     * Close the bridge and release WebAssembly resources
     */
    @Override
    public void close() throws IOException;
}
```

---

## Data Model Types

### Workspace JSON Structure

```json
{
  "name": "string",
  "version": "semver",
  "metadata": {
    "owner": "string",
    "created": "ISO8601 timestamp",
    "description": "string"
  },
  "tables": [
    {
      "name": "string",
      "description": "string",
      "columns": [
        {
          "name": "string",
          "type": "string (integer|string|float|decimal|boolean|timestamp|date|time)",
          "required": "boolean",
          "unique": "boolean",
          "primary": "boolean",
          "references": {
            "table": "string",
            "column": "string"
          },
          "default": "any",
          "maxLength": "integer",
          "pattern": "regex",
          "constraints": ["string"]
        }
      ]
    }
  ],
  "relationships": [
    {
      "name": "string",
      "from": {
        "table": "string",
        "cardinality": "1|*"
      },
      "to": {
        "table": "string",
        "cardinality": "1|*"
      },
      "constraints": {
        "cascade_delete": "boolean",
        "cascade_update": "boolean"
      }
    }
  ],
  "validationRules": [
    {
      "name": "string",
      "rule": "string (SQL-like expression)",
      "severity": "error|warning",
      "table": "string (optional)"
    }
  ]
}
```

### ODCS YAML Format

```yaml
apiVersion: v1
kind: DataModel
name: YourDomain
version: "1.0.0"
metadata:
  owner: Team
  description: Domain description
  created: 2026-02-28

tables:
  - name: table_name
    description: Table description
    columns:
      - name: column_name
        type: integer|string|decimal|timestamp
        required: true|false
        primary: true|false
        unique: true|false

relationships:
  - name: relationship_name
    from:
      table: source_table
      cardinality: "1"
    to:
      table: target_table
      cardinality: "*"

validationRules:
  - name: rule_name
    rule: "expression"
    severity: error|warning
```

---

## Configuration

### DataModellingConfig

```java
public class DataModellingConfig {
    public int getPoolSize();                    // Context pool size
    public String getSdkVersion();              // data-modelling-sdk version
    public boolean isValidateOnImport();        // Validate on import
    public String getOutputFormat();            // Default output format
    public Map<String, String> getCustomMappings();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder poolSize(int size);
        public Builder validateOnImport(boolean validate);
        public Builder outputFormat(String format);
        public DataModellingConfig build();
    }
}
```

---

## Exceptions

```java
// Import/export failures
public class DataModellingException extends Exception {}

// Validation failures
public class ValidationException extends DataModellingException {}

// Schema incompatibility
public class SchemaException extends DataModellingException {}

// Format not supported
public class UnsupportedFormatException extends DataModellingException {}

// WASM execution failures
public class WasmExecutionException extends DataModellingException {}
```

---

## Example Usage

```java
import org.yawlfoundation.yawl.datamodelling.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataModellingExample {
    public static void main(String[] args) throws Exception {
        try (DataModellingBridge bridge = new DataModellingBridge()) {
            // Import SQL schema
            String sqlDdl = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255))";
            String workspace = bridge.parseSqlToWorkspace(sqlDdl);

            // Validate
            String validation = bridge.validateWorkspace(workspace);
            System.out.println(validation);

            // Export to ODCS
            String odcsYaml = bridge.exportToOdcs(workspace);
            Files.writeString(Path.of("schema.yaml"), odcsYaml);

            // Validate data
            String data = "{\"id\": 123, \"name\": \"John\"}";
            String result = bridge.validateData(data, workspace, "users");
            System.out.println(result);
        }
    }
}
```

---

## See Also

- [Tutorial: Data Modelling Getting Started](../tutorials/11-data-modelling-bridge.md)
- [How-To: Validate Data Models](../how-to/data-modelling-schema-validation.md)
- [Explanation: Data Modelling WASM Architecture](../explanation/data-modelling-wasm-architecture.md)
- [SDK Source: data-modelling-sdk](https://github.com/OffeneDatenmodellierung/data-modelling-sdk)
