# Marketplace MVP — Engineering Implementation Guide

**Audience**: Backend engineers, RDF specialists
**Scope**: Code structure, integration points, build commands
**Date**: 2026-02-21

---

## Part 1: Getting Started (30 minutes)

### 1.1 Environment Setup

**Prerequisites**:
- Java 21+ (installed)
- Maven 3.8+ (installed)
- Docker & Docker Compose (installed)
- Git (configured with GPG if possible)

**Step 1: Clone & Build**

```bash
cd /home/user/yawl

# Add marketplace module to parent pom.xml
# (if not already added)

# Build all modules including marketplace
mvn clean install -DskipTests -pl yawl-marketplace
```

**Step 2: Start Local Environment**

```bash
cd yawl-marketplace

# Start Jena + PostgreSQL + API
docker-compose up -d

# Verify services
curl http://localhost:3030/  # Jena Fuseki (should see HTML page)
curl http://localhost:8080/actuator/health  # Spring Boot

# View logs
docker-compose logs -f api
```

**Step 3: Load Sample Ontology**

```bash
# From yawl-marketplace directory:
bash scripts/load-sample-data.sh

# Or manually:
curl -X POST http://localhost:8080/api/marketplace/load-ontology \
  -F 'file=@src/main/resources/ontology/yawl-marketplace-ontology.ttl'
```

**Step 4: Run Tests**

```bash
# Unit tests only (fast)
mvn test -pl yawl-marketplace

# Full integration tests (includes Docker tests)
mvn verify -pl yawl-marketplace -P integration-tests

# Run specific test
mvn test -pl yawl-marketplace \
  -Dtest=SPARQLQueryEngineTest#testFindSkillsByInputType
```

---

## Part 2: Project Structure & Module Layout

### 2.1 Directory Tree

```
yawl-marketplace/
│
├── pom.xml
│   ├── <parent>yawl-parent</parent>
│   ├── <artifactId>yawl-marketplace</artifactId>
│   ├── <dependencies>
│   │   ├── org.apache.jena:jena-core:5.0.0
│   │   ├── org.apache.jena:jena-arq:5.0.0
│   │   ├── org.apache.jena:jena-tdb2:5.0.0
│   │   ├── org.eclipse.jgit:org.eclipse.jgit:6.8.0
│   │   ├── org.springframework.boot:spring-boot-starter-web:3.2.2
│   │   └── org.postgresql:postgresql:42.7.1
│   └── <build>
│       └── <plugin>spring-boot-maven-plugin</plugin>
│
├── docker-compose.yml
│   ├── jena (port 3030)
│   ├── postgres (port 5432)
│   └── api (port 8080, built from Dockerfile.api)
│
├── Dockerfile.api
│   └── FROM eclipse-temurin:21-jdk
│       RUN ENTRYPOINT ["java", "-jar", "yawl-marketplace.jar"]
│
├── src/main/java/org/yawlfoundation/yawl/marketplace/
│   ├── config/
│   │   ├── RDFConfiguration.java      # Jena + Fuseki setup
│   │   ├── DatabaseConfiguration.java # PostgreSQL setup
│   │   └── SecurityConfiguration.java # OAuth2 + JWT
│   │
│   ├── RDFGraphRepository.java        # Core: RDF access layer
│   ├── SPARQLQueryEngine.java         # Core: 15 queries
│   ├── GitSyncService.java            # Core: version control
│   ├── BillingCalculator.java         # Core: pricing
│   │
│   ├── rest/
│   │   ├── SkillController.java
│   │   │   ├── GET /api/skills
│   │   │   ├── GET /api/skills/{id}
│   │   │   ├── POST /api/skills
│   │   │   └── GET /api/skills/search?q=...
│   │   ├── IntegrationController.java
│   │   ├── AgentController.java
│   │   ├── DataSourceController.java
│   │   ├── DiscoveryController.java
│   │   │   ├── GET /api/discover/chain/{skillId}
│   │   │   ├── GET /api/discover/cost/{skillId}
│   │   │   └── GET /api/marketplace-stats
│   │   ├── BillingController.java
│   │   └── AuditController.java
│   │
│   ├── service/
│   │   ├── MarketplaceEntityService.java   # CRUD for entities
│   │   ├── DiscoveryService.java           # High-level discovery logic
│   │   ├── GovernanceService.java          # Versioning, breaking changes
│   │   └── UsageEventService.java          # Track billing events
│   │
│   ├── entity/
│   │   ├── Skill.java (JPA entity)
│   │   ├── Integration.java (JPA entity)
│   │   ├── Agent.java (JPA entity)
│   │   ├── DataSource.java (JPA entity)
│   │   ├── Publisher.java (JPA entity)
│   │   ├── UsageEvent.java (JPA entity)
│   │   └── AuditEvent.java (JPA entity, immutable)
│   │
│   ├── dto/
│   │   ├── SkillDTO.java
│   │   ├── DependencyChainDTO.java
│   │   ├── MarketplaceStatsDTO.java
│   │   ├── MonthlyChargeDTO.java
│   │   └── AuditEntryDTO.java
│   │
│   ├── exception/
│   │   ├── EntityNotFoundException.java
│   │   ├── InvalidSPARQLQueryException.java
│   │   ├── GitSyncException.java
│   │   └── BillingException.java
│   │
│   └── MarketplaceApplication.java # Spring Boot main class
│
├── src/main/resources/
│   ├── ontology/
│   │   ├── yawl-marketplace-ontology.ttl (550 lines, core schema)
│   │   └── example-graph.ttl (200 lines, sample data)
│   ├── application.properties
│   ├── logback-spring.xml
│   └── db/migration/ (Flyway SQL migrations)
│       ├── V1__init_schema.sql
│       └── V2__add_audit_table.sql
│
├── src/test/java/org/yawlfoundation/yawl/marketplace/
│   ├── RDFGraphRepositoryTest.java
│   │   ├── testInitializeRepository()
│   │   ├── testAddEntity()
│   │   ├── testAddCrossMarketplaceLink()
│   │   └── testCountEntities()
│   │
│   ├── SPARQLQueryEngineTest.java
│   │   ├── testFindSkillsByInputType()
│   │   ├── testFindSkillsUsingIntegration()
│   │   ├── testFindAgentsByCapability()
│   │   ├── testFindDataSourcesBySchema()
│   │   ├── testFindDependencyChains()
│   │   ├── testAnalyzeCost()
│   │   ├── testGetMarketplaceStats()
│   │   └── testPerformance() (ensure <500ms p95)
│   │
│   ├── GitSyncServiceTest.java
│   │   ├── testInitializeRepository()
│   │   ├── testCommitChanges()
│   │   ├── testPullLatest()
│   │   ├── testGetAuditTrail()
│   │   └── testRevertToCommit()
│   │
│   ├── BillingCalculatorTest.java
│   │   ├── testCalculateMonthlyCharge()
│   │   ├── testApplyTierDiscount()
│   │   └── testMultipleUsageEvents()
│   │
│   ├── EndToEndIntegrationTest.java
│   │   ├── testCreateSkillWithDependencies()
│   │   ├── testDiscoveryCrossMicroservices()
│   │   ├── testBillingEndToEnd()
│   │   ├── testGitHistoryComplete()
│   │   └── testSPARQLLatencyUnder500ms()
│   │
│   └── SPARQLPerformanceTest.java
│       └── Run 100 queries, measure p50/p95/p99 latency
│
├── docs/
│   ├── API.md (REST + GraphQL endpoints)
│   ├── ONTOLOGY.md (RDF schema reference)
│   ├── QUERIES.md (Example SPARQL queries)
│   ├── DEPLOYMENT.md (Kubernetes + cloud setup)
│   └── TROUBLESHOOTING.md (Common issues & fixes)
│
├── scripts/
│   ├── load-sample-data.sh (POST example Turtle to Jena)
│   ├── backup-rdf-store.sh (Tar TDB2 directory)
│   ├── cleanup-old-commits.sh (Archive git commits >6mo)
│   └── generate-invoice.sh (Generate monthly invoices)
│
└── .github/workflows/
    └── marketplace-mvp.yml (CI/CD pipeline)
```

### 2.2 Key Classes (Reference)

**RDFGraphRepository.java** (70 lines, core):

```java
@Repository
public class RDFGraphRepository {
    private Dataset dataset;  // Jena TDB2
    private Model model;

    public void initialize() throws IOException {
        // Initialize TDB2, load ontology
    }

    public void addEntity(MarketplaceEntity entity) {
        // Add RDF resource to graph
    }

    public void addCrossMarketplaceLink(String sourceUri, String linkType, String targetUri) {
        // Link across Skill→Integration, Agent→Data, etc.
    }

    public MarketplaceEntity getEntity(String uri) {
        // Retrieve entity by URI
    }

    public long countEntities(String entityType) {
        // COUNT query (see SPARQLQueryEngine)
    }
}
```

**SPARQLQueryEngine.java** (800 lines, core):

```java
@Service
public class SPARQLQueryEngine {
    // 15-20 pre-written queries:

    public List<SkillDTO> findSkillsByInputType(String inputType) { }
    public List<SkillDTO> findSkillsUsingIntegration(String integrationId) { }
    public List<AgentDTO> findAgentsByCapability(String capabilityName) { }
    public List<DataSourceDTO> findDataSourcesBySchema(String schemaPattern) { }
    public List<DependencyChainDTO> findDependencyChains(String skillId) { }
    public SkillCostAnalysisDTO analyzeCost(String skillId, int monthlyInvocations) { }
    public MarketplaceStatsDTO getMarketplaceStats() { }

    // ... more queries

    private <T> List<T> executeQuery(String sparql, ResultProcessor<T> processor) {
        // Common execution logic
    }
}
```

**GitSyncService.java** (300 lines, core):

```java
@Service
public class GitSyncService {
    private Git git;
    private RDFGraphRepository rdfRepository;

    public void initialize(String gitUrl) throws IOException {
        // Clone/open git repo
    }

    public CommitResult commitChanges(String entityId, String entityType,
                                      String changeDescription, String author) {
        // 1. Serialize RDF
        // 2. Write Turtle file
        // 3. git add + commit
        // 4. git push
    }

    public PullResult pullLatest() throws IOException {
        // git pull + reload RDF
    }

    public List<AuditEntry> getAuditTrail(String entityId) {
        // git log for entity file
    }
}
```

**BillingCalculator.java** (200 lines, core):

```java
@Service
public class BillingCalculator {
    private static final double SKILL_CALL_COST = 0.001;
    private static final double INTEGRATION_CALL_COST = 0.001;
    private static final double AGENT_CALL_COST = 0.05;
    private static final double DATA_PER_GB_COST = 0.10;

    public MonthlyChargeDTO calculateMonthlyCharge(String organizationId,
                                                   BillingPeriod period) {
        // 1. Get usage events
        // 2. Sum by type
        // 3. Apply discounts
        // 4. Return total
    }
}
```

---

## Part 3: Integration Points

### 3.1 Skills Marketplace Integration

**When a skill is published** (existing skills marketplace):

```java
// In SkillsMarketplaceService (existing code)
public void publishSkill(SkillDefinition skillDef) {
    // Existing logic: save to skills DB, etc.

    // NEW: Add to RDF graph
    MarketplaceEntity skillEntity = new MarketplaceEntity(
        "Skill",
        skillDef.getId(),
        skillDef.getName(),
        skillDef.getVersion()
    );
    rdfGraphRepository.addEntity(skillEntity);

    // NEW: Commit to Git
    gitSyncService.commitChanges(
        skillDef.getId(),
        "Skill",
        "Published skill: " + skillDef.getName(),
        getCurrentUser().getName(),
        getCurrentUser().getEmail()
    );
}
```

### 3.2 Integrations Marketplace Integration

**When an integration is registered**:

```java
// In IntegrationsMarketplaceService (existing code)
public void registerIntegration(IntegrationDefinition integDef) {
    // Existing logic

    // NEW: Add to RDF + link to skills using it
    MarketplaceEntity integEntity = new MarketplaceEntity(
        "Integration",
        integDef.getId(),
        integDef.getName(),
        integDef.getVersion()
    );
    rdfGraphRepository.addEntity(integEntity);

    // NEW: Link all skills that use this integration
    // (by scanning skill metadata for "uses_integration_id")
    List<String> dependentSkillIds = findSkillsUsingIntegration(integDef.getId());
    for (String skillId : dependentSkillIds) {
        rdfGraphRepository.addCrossMarketplaceLink(
            "http://yawl-marketplace.org/skill/" + skillId,
            "skillUsesIntegration",
            "http://yawl-marketplace.org/integration/" + integDef.getId()
        );
    }

    // NEW: Commit
    gitSyncService.commitChanges(
        integDef.getId(),
        "Integration",
        "Registered integration: " + integDef.getName(),
        getCurrentUser().getName(),
        getCurrentUser().getEmail()
    );
}
```

### 3.3 Agents Marketplace Integration

**When an agent is registered**:

```java
// In AgentsMarketplaceService (existing code)
public void registerAgent(AgentDefinition agentDef) {
    // Existing logic

    // NEW: Add to RDF
    MarketplaceEntity agentEntity = new MarketplaceEntity(
        "Agent",
        agentDef.getId(),
        agentDef.getName(),
        agentDef.getVersion()
    );
    rdfGraphRepository.addEntity(agentEntity);

    // NEW: Link to capabilities
    for (AgentCapability cap : agentDef.getCapabilities()) {
        // Store capability in RDF
        rdfGraphRepository.addCapability(agentDef.getId(), cap);
    }

    // NEW: Link to tools/integrations used
    for (String integrationId : agentDef.getRequiredIntegrations()) {
        rdfGraphRepository.addCrossMarketplaceLink(
            "http://yawl-marketplace.org/agent/" + agentDef.getId(),
            "agentUsesIntegration",
            "http://yawl-marketplace.org/integration/" + integrationId
        );
    }

    // NEW: Commit
    gitSyncService.commitChanges(
        agentDef.getId(),
        "Agent",
        "Registered agent: " + agentDef.getName(),
        getCurrentUser().getName(),
        getCurrentUser().getEmail()
    );
}
```

### 3.4 Data Marketplace Integration

**When a data source is published**:

```java
// In DataMarketplaceService (existing code)
public void publishDataSource(DataSourceDefinition dataDef) {
    // Existing logic

    // NEW: Add to RDF
    MarketplaceEntity dataEntity = new MarketplaceEntity(
        "DataSource",
        dataDef.getId(),
        dataDef.getName(),
        dataDef.getVersion()
    );
    rdfGraphRepository.addEntity(dataEntity);

    // NEW: Link to consumers (skills/agents using this data)
    List<String> consumerIds = findConsumersOfData(dataDef.getId());
    for (String consumerId : consumerIds) {
        String consumerType = getEntityType(consumerId);  // Skill or Agent
        String property = consumerType.equals("Skill") ?
            "skillConsumesData" : "agentConsumesData";

        rdfGraphRepository.addCrossMarketplaceLink(
            "http://yawl-marketplace.org/" + consumerType.toLowerCase() + "/" + consumerId,
            property,
            "http://yawl-marketplace.org/datasource/" + dataDef.getId()
        );
    }

    // NEW: Commit
    gitSyncService.commitChanges(
        dataDef.getId(),
        "DataSource",
        "Published data source: " + dataDef.getName(),
        getCurrentUser().getName(),
        getCurrentUser().getEmail()
    );
}
```

### 3.5 Usage Tracking (Billing)

**On every skill invocation** (hook into existing execution):

```java
// In YNetRunner or task executor (existing code)
public void executeTask(YWorkItem workItem) throws Exception {
    // Existing execution logic

    // NEW: Track usage for billing
    UsageEvent event = new UsageEvent(
        workItem.getWorkflowID(),  // skill ID
        getCurrentUser().getOrganization(),  // publisher
        1,  // invocation count
        System.currentTimeMillis()
    );
    usageEventService.recordEvent(event);

    // If this skill uses integrations/agents, track those too
    // (via RDF graph lookup + automatic billing)
}
```

---

## Part 4: Build & Test Commands

### 4.1 Development Workflow

```bash
# Start day
docker-compose up -d

# Code changes
vim src/main/java/.../SomeService.java

# Compile (fast)
mvn compile -pl yawl-marketplace

# Unit test specific class
mvn test -pl yawl-marketplace -Dtest=SPARQLQueryEngineTest

# Run API locally
mvn spring-boot:run -pl yawl-marketplace

# In another terminal, test endpoint
curl http://localhost:8080/api/skills

# End day: commit
git add -A
git commit -m "[marketplace] Add new discovery query"
```

### 4.2 Pre-Push Checklist

```bash
# Run ALL tests (30 seconds on modern laptop)
mvn verify -pl yawl-marketplace

# Check code quality
mvn spotbugs:check -pl yawl-marketplace  # No security issues
mvn pmd:check -pl yawl-marketplace       # No design issues

# Measure SPARQL latency
mvn test -pl yawl-marketplace -Dtest=SPARQLPerformanceTest
# Check output: p95 latency should be <500ms

# Verify git commit has correct format
git log -1 --pretty=format:"%H %s"
# Should be: [marketplace] Description

# Push
git push -u origin feature/marketplace-...
```

### 4.3 CI/CD Pipeline

```bash
# Locally simulate CI pipeline
bash scripts/ci-simulate.sh

# Check: compile, test, coverage, spotbugs, pmd
# Should see: "BUILD SUCCESS" or exit with error

# If success, ready to merge
```

---

## Part 5: Debugging & Troubleshooting

### 5.1 SPARQL Query Debugging

**Problem**: Query returns 0 results

```bash
# 1. Check if RDF graph has data
curl 'http://localhost:3030/federation/sparql?query=SELECT+(COUNT(*)%20AS%20%3Fcount)%20WHERE+%7B%3Fs+%3Fp+%3Fo%7D'

# 2. Run simpler query first
curl 'http://localhost:3030/federation/sparql?query=SELECT+*+WHERE+%7B%3Fs+rdf:type+%3Fo%7D+LIMIT+10'

# 3. Check ontology loaded
curl 'http://localhost:3030/federation/sparql?query=SELECT+*+WHERE+%7B%3Fx+rdf:type+:Skill+%7D+LIMIT+1'

# 4. Check entity exists
curl -H "Accept: application/rdf+xml" http://localhost:3030/federation?uri=http://yawl-marketplace.org/skill/skill-approval-v1

# 5. View Jena admin UI
open http://localhost:3030/
# Click "Manage Datasets" → "/federation" → "Info"
```

### 5.2 Git Sync Debugging

```bash
# 1. Check git repo state
cd yawl-marketplace
git status
git log --oneline -5

# 2. Check Turtle file syntax
ttl=$(cat <file>.ttl)
curl -X POST http://localhost:3030/federation \
  -d "$ttl" \
  -H "Content-Type: text/turtle"
# Should return 201 if valid

# 3. Check git config
git config --list | grep user.

# 4. Verify signed commits
git log -1 --show-signature

# 5. If push fails
git push -u origin --force  # DANGEROUS, use with caution
```

### 5.3 Billing Debugging

```bash
# 1. Check usage events recorded
psql -h localhost -U admin -d marketplace -c \
  "SELECT * FROM usage_events WHERE organization_id='acme' LIMIT 10;"

# 2. Verify billing calculation
mvn test -pl yawl-marketplace \
  -Dtest=BillingCalculatorTest#testCalculateMonthlyCharge

# 3. Manual calculation
# Usage: 42 skill calls @ $0.001 = $0.042
# Plus discounts for tier
curl -X POST http://localhost:8080/api/billing/calculate \
  -d '{"organization": "acme", "month": "2026-02"}' \
  -H "Content-Type: application/json"
```

---

## Part 6: Common Code Patterns

### 6.1 Adding a New Query

```java
// File: SPARQLQueryEngine.java

public List<SkillDTO> findSkillsByNewCriteria(String criteria) {
    String sparql = "" +
        "PREFIX : <http://yawl-marketplace.org/ontology#> " +
        "SELECT ?skill ?name ?version " +
        "WHERE { " +
        "  ?skill a :Skill ; " +
        "    :skill_name ?name ; " +
        "    :version ?version ; " +
        "    :some_property '" + criteria + "' . " +
        "} " +
        "ORDER BY DESC(?version) ";

    return executeQuery(sparql, (row) -> {
        return new SkillDTO(
            row.getResource("skill").getURI(),
            row.getLiteral("name").getString(),
            row.getLiteral("version").getString(),
            null
        );
    });
}

// File: SPARQLQueryEngineTest.java

@Test
public void testFindSkillsByNewCriteria() {
    // 1. Setup: load test RDF
    setupTestData();

    // 2. Execute
    List<SkillDTO> results = queryEngine.findSkillsByNewCriteria("test-criteria");

    // 3. Assert
    assertEquals(2, results.size());
    assertEquals("Skill1", results.get(0).name);
}
```

### 6.2 Adding an Integration Link

```java
// File: SomeMarketplaceService.java

public void linkEntityToData(String entityId, String dataSourceId) {
    rdfGraphRepository.addCrossMarketplaceLink(
        "http://yawl-marketplace.org/skill/" + entityId,
        "skillConsumesData",
        "http://yawl-marketplace.org/datasource/" + dataSourceId
    );

    // Commit to git
    gitSyncService.commitChanges(
        entityId,
        "Skill",
        "Linked to data source: " + dataSourceId,
        getCurrentUser().getName(),
        getCurrentUser().getEmail()
    );
}
```

### 6.3 Adding a REST Endpoint

```java
// File: DiscoveryController.java

@RestController
@RequestMapping("/api/discover")
public class DiscoveryController {

    @GetMapping("/cost/{skillId}")
    public ResponseEntity<SkillCostAnalysisDTO> analyzeCost(
        @PathVariable String skillId,
        @RequestParam(defaultValue = "1000") int monthlyInvocations
    ) {
        try {
            SkillCostAnalysisDTO result = sparqlQueryEngine.analyzeCost(
                skillId,
                monthlyInvocations
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

// File: DiscoveryControllerTest.java

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DiscoveryControllerTest {

    @Test
    public void testAnalyzeCost() {
        ResponseEntity<SkillCostAnalysisDTO> response =
            restTemplate.getForEntity("/api/discover/cost/skill-approval-v1",
                                      SkillCostAnalysisDTO.class);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().totalCost > 0);
    }
}
```

---

## Part 7: Performance Tuning

### 7.1 Jena Indexing

```bash
# In Dockerfile.api or application startup:

# Enable Jena indexing
java -Djena.query.algebra.optimization=on \
     -Djena.query.algebra.rewriter=true \
     -jar yawl-marketplace.jar

# Monitor index size
du -sh ./data/rdf-store

# Rebuild indexes (if slow)
jena.admin --reset-dataset /federation
```

### 7.2 PostgreSQL Optimization

```sql
-- In init-db.sql or migrations:

-- Index on organization_id (for billing queries)
CREATE INDEX idx_usage_events_org
  ON usage_events(organization_id, timestamp);

-- Index on entity_id (for audit trail)
CREATE INDEX idx_audit_events_entity
  ON audit_events(entity_id, timestamp);

-- Check index usage
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

### 7.3 SPARQL Query Caching

```java
// File: SPARQLQueryEngine.java

@Service
public class SPARQLQueryEngine {
    private final Map<String, Object> queryCache =
        new ConcurrentHashMap<>();

    public List<SkillDTO> findSkillsByInputType(String inputType) {
        String cacheKey = "skills_input_" + inputType;

        // Check cache (TTL: 5 minutes)
        Object cached = queryCache.get(cacheKey);
        if (cached instanceof List &&
            cacheMetadata.get(cacheKey).isStillValid()) {
            return (List<SkillDTO>) cached;
        }

        // Execute query
        List<SkillDTO> results = executeQuery(...);

        // Store in cache
        queryCache.put(cacheKey, results);
        cacheMetadata.put(cacheKey, new CacheEntry(now(), ttl = 5min));

        return results;
    }
}
```

---

## Part 8: Code Review Checklist

When reviewing PRs for marketplace module:

- [ ] SPARQL queries use proper namespace prefixes?
- [ ] Query performance tested (<500ms)?
- [ ] Git commit format: `[marketplace] description`?
- [ ] Cross-marketplace links verified in RDF?
- [ ] Tests cover happy path + error cases?
- [ ] Billing calculations verified against manual calc?
- [ ] No hardcoded URIs (use constants)?
- [ ] Error handling complete (not swallowing exceptions)?
- [ ] Documentation updated?
- [ ] No merge conflicts with main branch?

---

## Part 9: Useful Commands Reference

```bash
# Build & test
mvn clean install -pl yawl-marketplace
mvn test -pl yawl-marketplace
mvn verify -pl yawl-marketplace

# Docker operations
docker-compose up -d
docker-compose down
docker-compose logs -f api
docker exec -it marketplace-jena bash

# Git operations
git log --oneline -- yawl-marketplace/
git diff HEAD~1 -- yawl-marketplace/
git checkout feature-branch -- yawl-marketplace/

# Database operations
psql -h localhost -U admin -d marketplace
sqlite3 ./data/rdf-store/tdb2-store.db

# SPARQL endpoint
curl 'http://localhost:3030/federation/sparql?query=...'

# API testing
curl http://localhost:8080/api/skills
curl http://localhost:8080/api/marketplace-stats
curl -X POST http://localhost:8080/api/billing/calculate -d '...'

# Performance testing
ab -n 100 -c 10 http://localhost:8080/api/skills
mvn test -pl yawl-marketplace -Dtest=SPARQLPerformanceTest

# Monitoring
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```

---

## Summary

By following this guide, you should be able to:

1. **Set up** the development environment in 30 minutes
2. **Understand** the project structure and module organization
3. **Integrate** with existing YAWL marketplaces
4. **Write** new SPARQL queries and REST endpoints
5. **Debug** RDF, Git, and billing issues
6. **Optimize** performance
7. **Review** code changes

For questions, refer to the full implementation guide: `MARKETPLACE-MVP-IMPLEMENTATION.md`

**Happy coding!**
