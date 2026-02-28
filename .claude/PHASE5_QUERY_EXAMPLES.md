# Phase 5 Query Examples & Patterns

## Quick Reference: Common Queries

### 1. Owner-Based Filtering

#### Find all tables owned by a team
```java
List<DataModellingTable> dataTeamTables = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByOwner("data-team")
    .getTables();
```

#### Find tables by multiple owners
```java
List<DataModellingTable> shared = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByOwner("data-team")
    .getTables();

List<DataModellingTable> analyticsOwned = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByOwner("analytics-team")
    .getTables();

// Alternative: Combined in one query
List<DataModellingTable> allOwned = workspace.getTables().stream()
    .filter(AdvancedFiltering.byOwners(
        Arrays.asList("data-team", "analytics-team")
    ))
    .toList();
```

---

### 2. Medallion Layer Queries

#### Find all gold-layer (analytics-ready) tables
```java
List<DataModellingTable> goldTables = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByMedallionLayer("gold")
    .getTables();
```

#### Group tables by medallion layer
```java
Map<String, List<DataModellingTable>> byLayer = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getTablesByMedallionLayer();

System.out.println("Bronze: " + byLayer.get("bronze").size());
System.out.println("Silver: " + byLayer.get("silver").size());
System.out.println("Gold: " + byLayer.get("gold").size());
```

#### Check data quality progression
```java
// Count tables in each layer
Map<String, List<DataModellingTable>> byLayer = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getTablesByMedallionLayer();

int bronze = byLayer.getOrDefault("bronze", List.of()).size();
int silver = byLayer.getOrDefault("silver", List.of()).size();
int gold = byLayer.getOrDefault("gold", List.of()).size();

System.out.println(String.format(
    "Data quality: Bronze=%d→Silver=%d→Gold=%d (progression: %d→%d→%d)",
    bronze, silver, gold,
    bronze / Math.max(1, bronze),
    silver / Math.max(1, bronze),
    gold / Math.max(1, bronze)
));
```

---

### 3. Tag-Based Queries (Data Governance)

#### Find all sensitive tables
```java
List<DataModellingTable> sensitive = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByTag("sensitive")
    .getTables();

sensitive.forEach(t -> System.out.println(
    "Table: " + t.getName() + ", Owner: " + t.getOwner()
));
```

#### Find PII-regulated tables
```java
List<DataModellingTable> pii = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByTag("pii")
    .getTables();

pii.forEach(t -> {
    System.out.println("PII Table: " + t.getName());
    System.out.println("  Owner: " + t.getOwner());
    System.out.println("  SLA: " + t.getSlaTerm());
    System.out.println("  Contacts: " + t.getContactDetails());
});
```

#### Find tables with ALL required tags (AND logic)
```java
// Only tables with BOTH "sensitive" AND "pii" tags
List<DataModellingTable> regulated = workspace.getTables().stream()
    .filter(AdvancedFiltering.byAllTags(
        Arrays.asList("sensitive", "pii")
    ))
    .toList();
```

#### Find tables with ANY of multiple tags (OR logic)
```java
// Tables marked as sensitive, pii, OR financial
List<DataModellingTable> restricted = workspace.getTables().stream()
    .filter(AdvancedFiltering.byTags(
        Arrays.asList("sensitive", "pii", "financial")
    ))
    .toList();
```

#### Find public tables that are NOT sensitive
```java
List<DataModellingTable> publicClear = workspace.getTables().stream()
    .filter(AdvancedFiltering.and(
        AdvancedFiltering.byTag("public"),
        AdvancedFiltering.byTagNot("sensitive")
    ))
    .toList();
```

---

### 4. Infrastructure Type Queries

#### Find all data warehouse tables
```java
List<DataModellingTable> warehouse = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .filterTablesByInfrastructureType("warehouse")
    .getTables();
```

#### Find tables by multiple infrastructure types
```java
List<DataModellingTable> cloudData = workspace.getTables().stream()
    .filter(AdvancedFiltering.byInfrastructureTypes(
        Arrays.asList("warehouse", "lake", "bigquery")
    ))
    .toList();
```

#### Group tables by infrastructure
```java
Map<String, List<DataModellingTable>> byInfra = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getTablesByInfrastructure();

byInfra.forEach((infraType, tables) -> {
    System.out.println(infraType + ":");
    tables.forEach(t -> System.out.println("  - " + t.getName()));
});
```

#### Count tables by infrastructure for capacity planning
```java
Map<String, List<DataModellingTable>> byInfra = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getTablesByInfrastructure();

byInfra.forEach((infraType, tables) -> {
    long totalColumns = tables.stream()
        .mapToLong(t -> t.getColumns() != null ? t.getColumns().size() : 0)
        .sum();
    System.out.println(String.format(
        "%s: %d tables, ~%d columns",
        infraType, tables.size(), totalColumns
    ));
});
```

---

### 5. Relationship & Dependency Queries

#### Find direct data consumers of a table
```java
String customersId = workspace.getTableByName("customers").getId();

List<DataModellingTable> consumers = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getDataConsumers(customersId);

System.out.println("Tables that consume 'customers':");
consumers.forEach(t -> System.out.println("  - " + t.getName()));
```

#### Find all relationships for a table
```java
String ordersId = workspace.getTableByName("orders").getId();

List<DataModellingRelationship> relationships = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getRelationshipsForTable(ordersId);

relationships.forEach(rel -> System.out.println(
    "Relationship: " + rel.getLabel() +
    " (" + rel.getRelationshipType() + ")"
));
```

#### Find all upstream data dependencies
```java
String analyticsId = workspace.getTableByName("analytics_summary").getId();

Set<String> upstream = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getTransitiveDependencies(analyticsId);

System.out.println("Tables that feed into 'analytics_summary':");
upstream.forEach(id -> {
    DataModellingTable t = workspace.getTableById(id);
    System.out.println("  - " + t.getName() + " (" + t.getOwner() + ")");
});
```

#### Build a lineage report
```java
String tableId = workspace.getTableByName("customer_analytics").getId();

Map<String, Object> lineage = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getDataLineageReport(tableId);

System.out.println("=== Data Lineage Report: " + lineage.get("tableName") + " ===");
System.out.println("Upstream dependencies (" +
    ((List<?>) lineage.get("upstreamDependencies")).size() + "):");
((List<?>) lineage.get("upstreamDependencies")).forEach(dep ->
    System.out.println("  ← " + dep)
);
System.out.println("Downstream dependents (" +
    ((List<?>) lineage.get("downstreamDependents")).size() + "):");
((List<?>) lineage.get("downstreamDependents")).forEach(dep ->
    System.out.println("  → " + dep)
);
```

---

### 6. Impact Analysis

#### What tables are affected if we change table X?
```java
String customersId = workspace.getTableByName("customers").getId();

List<DataModellingTable> impacted = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getImpactAnalysis(customersId);

System.out.println("Impact of modifying 'customers':");
System.out.println("  Directly affected: " +
    DataModellingQueryBuilder.forWorkspace(workspace)
        .getDataConsumers(customersId).size() + " tables");
System.out.println("  Transitively affected: " + impacted.size() + " tables");

impacted.forEach(t -> System.out.println(
    "  - " + t.getName() + " (Owner: " + t.getOwner() + ", Layer: " +
    t.getMedallionLayer() + ")"
));
```

#### Prioritize affected tables by layer
```java
String sourceTableId = workspace.getTableByName("raw_orders").getId();
List<DataModellingTable> impacted = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getImpactAnalysis(sourceTableId);

Map<String, List<DataModellingTable>> byLayer = impacted.stream()
    .collect(Collectors.groupingBy(
        t -> t.getMedallionLayer() != null ? t.getMedallionLayer() : "unknown"
    ));

System.out.println("Impact analysis by layer:");
byLayer.forEach((layer, tables) ->
    System.out.println("  " + layer + ": " + tables.size() + " tables")
);
```

---

### 7. Complex Filtering (Boolean Logic)

#### Find gold-layer, publicly available, non-sensitive tables
```java
List<DataModellingTable> publicGoldTables = workspace.getTables().stream()
    .filter(AdvancedFiltering.andAll(
        AdvancedFiltering.byMedallionLayer("gold"),
        AdvancedFiltering.byTag("public"),
        AdvancedFiltering.byTagNot("sensitive")
    ))
    .toList();

System.out.println("Public, non-sensitive, gold-layer tables: " +
    publicGoldTables.size());
```

#### Find critical production tables
```java
// Critical = (owned by data-team OR analytics-team) AND (tagged critical)
// AND (gold layer OR silver layer)
List<DataModellingTable> critical = workspace.getTables().stream()
    .filter(AdvancedFiltering.andAll(
        AdvancedFiltering.byOwners(
            Arrays.asList("data-team", "analytics-team")
        ),
        AdvancedFiltering.byTag("critical"),
        AdvancedFiltering.orAny(
            AdvancedFiltering.byMedallionLayer("gold"),
            AdvancedFiltering.byMedallionLayer("silver")
        )
    ))
    .toList();

System.out.println("Critical production tables: " + critical.size());
critical.forEach(t -> System.out.println("  - " + t.getName()));
```

---

### 8. Cycle Detection & Validation

#### Detect circular dependencies
```java
boolean hasCycles = DataModellingQueryBuilder.forWorkspace(workspace)
    .hasCyclicDependencies();

if (hasCycles) {
    List<String> cycle = DataModellingQueryBuilder.forWorkspace(workspace)
        .detectCyclePath();

    System.err.println("ALERT: Circular dependency detected!");
    System.err.println("Cycle path: " + cycle);

    // Resolve: Break the cycle by removing one relationship
    System.err.println("\nRecommended action: Break relationship from " +
        cycle.get(cycle.size() - 1) + " → " + cycle.get(0));
} else {
    System.out.println("✓ No circular dependencies detected");
}
```

---

### 9. Metadata Export & Documentation

#### Generate complete table inventory with metadata
```java
workspace.getTables().forEach(table -> {
    Map<String, Object> metadata = DataModellingQueryBuilder
        .forWorkspace(workspace)
        .getTableMetadataSummary(table.getId());

    System.out.println(String.format(
        "Table: %s | Owner: %s | Layer: %s | Infra: %s | Columns: %d | Relations: %d",
        metadata.get("name"),
        metadata.get("owner"),
        metadata.get("medallionLayer"),
        metadata.get("infrastructureType"),
        metadata.get("columnCount"),
        metadata.get("relationshipCount")
    ));
});
```

#### Data dictionary generation
```java
List<DataModellingTable> tables = DataModellingQueryBuilder
    .forWorkspace(workspace)
    .getTables();

System.out.println("# Data Dictionary\n");
tables.forEach(table -> {
    Map<String, Object> meta = DataModellingQueryBuilder
        .forWorkspace(workspace)
        .getTableMetadataSummary(table.getId());

    System.out.println("## " + table.getName());
    System.out.println("**Business Name**: " + table.getBusinessName());
    System.out.println("**Owner**: " + meta.get("owner"));
    System.out.println("**Layer**: " + meta.get("medallionLayer"));
    System.out.println("**Infrastructure**: " + meta.get("infrastructureType"));
    System.out.println("**Columns**: " + meta.get("columnCount"));
    System.out.println("**Relationships**: " + meta.get("relationshipCount"));
    System.out.println();
});
```

---

### 10. Domain-Level Operations

#### Manage system connections within a domain
```java
DomainOperations ops = new DomainOperations(workspace);

String customerDomainId = workspace.getDomains().get(0).getId();

// Define system connections
ops.addSystemConnection(customerDomainId, "crm", "data-warehouse");
ops.addSystemConnection(customerDomainId, "data-warehouse", "analytics");

// Retrieve all systems in domain
Set<String> systems = ops.getSystemsForDomain(customerDomainId);
System.out.println("Systems in customer domain: " + systems);
```

#### Asset dependency tracking
```java
DomainOperations ops = new DomainOperations(workspace);

String asset1Id = workspace.getDomains().get(0).getAssets().get(0).getId();
String asset2Id = workspace.getDomains().get(0).getAssets().get(1).getId();

// Create dependency
ops.addAssetDependency(asset1Id, asset2Id);

// Query transitive dependencies
Set<String> allDeps = ops.getTransitiveDependencies(asset1Id);
System.out.println("Asset dependencies: " + allDeps.size() + " assets");
```

#### Detect circular dependencies within domain
```java
DomainOperations ops = new DomainOperations(workspace);
String domainId = workspace.getDomains().get(0).getId();

if (ops.hasCyclicDependenciesInDomain(domainId)) {
    List<String> cycle = ops.detectCyclePathInDomain(domainId);
    System.out.println("Circular dependency in domain: " + cycle);
} else {
    System.out.println("Domain is acyclic");
}
```

---

## Performance Tips

### 1. Reuse QueryBuilder for Multiple Queries
```java
DataModellingQueryBuilder builder = DataModellingQueryBuilder.forWorkspace(workspace);

// Reuse builder for multiple queries
List<DataModellingTable> goldTables = builder
    .filterTablesByMedallionLayer("gold")
    .getTables();

List<DataModellingTable> silverTables = DataModellingQueryBuilder
    .forWorkspace(workspace)  // New builder needed for different filters
    .filterTablesByMedallionLayer("silver")
    .getTables();
```

### 2. Cache Workspace Object
```java
// Instead of calling queryBuilder() repeatedly:
DataModellingWorkspace workspace = converter.fromJson(workspaceJson);

// Use the same workspace object for all queries
DataModellingQueryBuilder q1 = DataModellingQueryBuilder.forWorkspace(workspace);
DataModellingQueryBuilder q2 = DataModellingQueryBuilder.forWorkspace(workspace);
```

### 3. Stream Processing for Large Results
```java
// For workspaces with thousands of tables
workspace.getTables().parallelStream()
    .filter(AdvancedFiltering.byOwner("data-team"))
    .filter(AdvancedFiltering.byMedallionLayer("gold"))
    .collect(Collectors.toList());
```

---

## Integration with DataModellingBridge

### Querying from JSON Strings
```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    String workspaceJson = bridge.parseOdcsYaml(yamlContent);

    // Direct JSON-to-JSON queries
    String silverTablesJson = bridge.filterTablesByMedallionLayer(
        workspaceJson,
        "silver"
    );

    // Impact analysis
    String impactJson = bridge.getImpactAnalysis(workspaceJson, tableId);

    // Lineage report
    String lineageJson = bridge.getDataLineageReport(workspaceJson, tableId);
}
```

---

## Error Handling

### Safe Query Patterns
```java
// Safe: null-safe filtering
List<DataModellingTable> filtered = workspace.getTables().stream()
    .filter(t -> t.getOwner() != null && t.getOwner().equals("data-team"))
    .toList();

// Results are immutable - cannot accidentally modify
List<DataModellingTable> results = builder.getTables();
// results.add(...); // UnsupportedOperationException

// Handle empty results
List<DataModellingTable> results = builder
    .filterTablesByMedallionLayer("nonexistent")
    .getTables();

if (results.isEmpty()) {
    System.out.println("No tables in that layer");
}
```

---

## Session Reference
https://claude.ai/code/session_01TtGL3HuTXQpN2uUz9NDhSi
