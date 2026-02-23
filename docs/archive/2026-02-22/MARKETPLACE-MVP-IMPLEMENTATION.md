# Unified Marketplace Platform — 4-Week MVP Implementation Design

**Document Type**: Implementation Architecture & Roadmap
**Audience**: Platform Architects, Backend Engineers, DevOps
**Date**: 2026-02-21
**Status**: Ready for Engineering Review

---

## Executive Summary

This document designs a **4-week MVP** for a unified RDF-backed marketplace platform connecting four YAWL marketplaces (skills, integrations, agents, data). The MVP uses the **80/20 principle** to deliver core value quickly:

- **Unified RDF Graph** (single source of truth for all marketplaces)
- **SPARQL Discovery Engine** (15-20 pre-written queries)
- **Git-backed versioning** (audit trail + change history)
- **Basic Billing Model** (pay-per-invocation + tier-based data access)
- **Governance Framework** (change notifications via GitHub)

**Key Metrics**:
- 1000+ entities in RDF graph by week 4
- SPARQL queries <500ms (p95)
- Cross-marketplace linking enabled
- Git history shows all changes (immutable audit trail)
- Billing API calculates charges correctly

**Team**: 3 engineers (RDF expert, platform lead, billing engineer)
**Cost**: $75K
**Success Criteria**: MVP validates platform feasibility + $0→$10K MRR (pilot customers)

---

## Part 1: Unified RDF Graph Architecture

### 1.1 YAWL Marketplace Ontology (Turtle Format)

**File**: `/yawl/schema/marketplace/yawl-marketplace-ontology.ttl` (550 lines)

```turtle
@prefix : <http://yawl-marketplace.org/ontology#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

# ===== ONTOLOGY METADATA =====
@prefix : <http://yawl-marketplace.org/ontology#> .

:YAWLMarketplaceOntology a owl:Ontology ;
    dcterms:title "YAWL Unified Marketplace Ontology" ;
    dcterms:version "1.0.0" ;
    dcterms:issued "2026-02-21"^^xsd:date ;
    dcterms:description "Semantic model for unified YAWL skill, integration, agent, and data marketplaces" ;
    owl:imports <http://xmlns.com/foaf/0.1/> ;
    owl:imports <http://purl.org/dc/terms/> ;
    rdfs:seeAlso <http://yawl-marketplace.org/schema> .

# ===== CORE CLASSES =====

:Marketplace a owl:Class ;
    rdfs:label "Marketplace"@en ;
    rdfs:comment "A YAWL marketplace (skills, integrations, agents, data)" ;
    owl:equivalentClass [
        owl:oneOf (:SkillsMarketplace :IntegrationsMarketplace :AgentsMarketplace :DataMarketplace)
    ] .

:SkillsMarketplace a owl:NamedIndividual, :Marketplace ;
    rdfs:label "Skills Marketplace" ;
    dcterms:description "Reusable YAWL task implementations and workflows" ;
    :hasPrimaryEntity :Skill ;
    :marketplace_id "skills" ;
    :supports_versioning true ;
    :default_tier "free" .

:IntegrationsMarketplace a owl:NamedIndividual, :Marketplace ;
    rdfs:label "Integrations Marketplace" ;
    dcterms:description "Connectors to external systems (SAP, Salesforce, etc.)" ;
    :hasPrimaryEntity :Integration ;
    :marketplace_id "integrations" ;
    :supports_versioning true ;
    :default_tier "premium" .

:AgentsMarketplace a owl:NamedIndividual, :Marketplace ;
    rdfs:label "Agents Marketplace" ;
    dcterms:description "Autonomous agents and AI capabilities for workflows" ;
    :hasPrimaryEntity :Agent ;
    :marketplace_id "agents" ;
    :supports_versioning true ;
    :requires_approval true ;
    :default_tier "enterprise" .

:DataMarketplace a owl:NamedIndividual, :Marketplace ;
    rdfs:label "Data Marketplace" ;
    dcterms:description "Data sources, metrics, and analytics datasets" ;
    :hasPrimaryEntity :DataSource ;
    :marketplace_id "data" ;
    :supports_versioning false ;
    :access_model "metered" ;
    :default_tier "premium" .

# ===== SKILL ENTITY =====

:Skill a owl:Class ;
    rdfs:label "Skill"@en ;
    rdfs:comment "A reusable YAWL task or workflow" ;
    rdfs:subClassOf :MarketplaceEntity ;
    owl:disjointWith :Integration, :Agent, :DataSource .

:Skill
    owl:hasProperty [
        owl:onProperty :skill_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :skill_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :skill_description ;
        owl:maxCardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :yawl_xml ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :version ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :input_schema ;
        owl:cardinality 1 ;
        owl:range :DataSchema
    ] ;
    owl:hasProperty [
        owl:onProperty :output_schema ;
        owl:cardinality 1 ;
        owl:range :DataSchema
    ] ;
    owl:hasProperty [
        owl:onProperty :author ;
        owl:cardinality 1 ;
        owl:range :Publisher
    ] ;
    owl:hasProperty [
        owl:onProperty :tags ;
        owl:minCardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :published_at ;
        owl:cardinality 1 ;
        owl:range xsd:dateTime
    ] ;
    owl:hasProperty [
        owl:onProperty :usage_count ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] ;
    owl:hasProperty [
        owl:onProperty :rating ;
        owl:maxCardinality 1 ;
        owl:range xsd:decimal ;
        owl:minInclusive "0.0"^^xsd:decimal ;
        owl:maxInclusive "5.0"^^xsd:decimal
    ] .

# ===== INTEGRATION ENTITY =====

:Integration a owl:Class ;
    rdfs:label "Integration"@en ;
    rdfs:comment "Connector to external system (SAP, Salesforce, etc.)" ;
    rdfs:subClassOf :MarketplaceEntity ;
    owl:disjointWith :Skill, :Agent, :DataSource .

:Integration
    owl:hasProperty [
        owl:onProperty :integration_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :integration_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :target_system ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :mcp_endpoint ;
        owl:cardinality 1 ;
        owl:range xsd:anyURI
    ] ;
    owl:hasProperty [
        owl:onProperty :authentication_type ;
        owl:cardinality 1 ;
        owl:range [:OAuth2, :JWT, :BasicAuth, :APIKey]
    ] ;
    owl:hasProperty [
        owl:onProperty :supported_operations ;
        owl:minCardinality 1 ;
        owl:range [:Read, :Write, :Create, :Update, :Delete]
    ] ;
    owl:hasProperty [
        owl:onProperty :rate_limit ;
        owl:cardinality 1 ;
        owl:range :RateLimit
    ] ;
    owl:hasProperty [
        owl:onProperty :sla_uptime ;
        owl:cardinality 1 ;
        owl:range xsd:decimal
    ] ;
    owl:hasProperty [
        owl:onProperty :cost_per_call ;
        owl:cardinality 1 ;
        owl:range xsd:decimal
    ] .

# ===== AGENT ENTITY =====

:Agent a owl:Class ;
    rdfs:label "Agent"@en ;
    rdfs:comment "Autonomous agent with capabilities" ;
    rdfs:subClassOf :MarketplaceEntity ;
    owl:disjointWith :Skill, :Integration, :DataSource .

:Agent
    owl:hasProperty [
        owl:onProperty :agent_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :agent_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :model ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :capabilities ;
        owl:minCardinality 1 ;
        owl:range :Capability
    ] ;
    owl:hasProperty [
        owl:onProperty :tools ;
        owl:minCardinality 1 ;
        owl:range :Tool
    ] ;
    owl:hasProperty [
        owl:onProperty :cost_per_invocation ;
        owl:cardinality 1 ;
        owl:range xsd:decimal
    ] ;
    owl:hasProperty [
        owl:onProperty :context_window ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] ;
    owl:hasProperty [
        owl:onProperty :latency_p95 ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] .

# ===== DATA SOURCE ENTITY =====

:DataSource a owl:Class ;
    rdfs:label "Data Source"@en ;
    rdfs:comment "Data source, metrics, or analytics dataset" ;
    rdfs:subClassOf :MarketplaceEntity ;
    owl:disjointWith :Skill, :Integration, :Agent .

:DataSource
    owl:hasProperty [
        owl:onProperty :datasource_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :datasource_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :data_type ;
        owl:cardinality 1 ;
        owl:range [:Metrics, :Analytics, :Dataset, :Stream]
    ] ;
    owl:hasProperty [
        owl:onProperty :schema ;
        owl:cardinality 1 ;
        owl:range :DataSchema
    ] ;
    owl:hasProperty [
        owl:onProperty :update_frequency ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :cost_per_gb ;
        owl:cardinality 1 ;
        owl:range xsd:decimal
    ] ;
    owl:hasProperty [
        owl:onProperty :retention_days ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] ;
    owl:hasProperty [
        owl:onProperty :query_engine ;
        owl:cardinality 1 ;
        owl:range [:SQL, :SPARQL, :REST, :Kafka]
    ] .

# ===== MARKETPLACE ENTITY (Base Class) =====

:MarketplaceEntity a owl:Class ;
    rdfs:label "Marketplace Entity"@en ;
    rdfs:comment "Base class for all marketplace items" .

:MarketplaceEntity
    owl:hasProperty [
        owl:onProperty :entity_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :marketplace ;
        owl:cardinality 1 ;
        owl:range :Marketplace
    ] ;
    owl:hasProperty [
        owl:onProperty :publisher ;
        owl:cardinality 1 ;
        owl:range :Publisher
    ] ;
    owl:hasProperty [
        owl:onProperty :version ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :published_at ;
        owl:cardinality 1 ;
        owl:range xsd:dateTime
    ] ;
    owl:hasProperty [
        owl:onProperty :updated_at ;
        owl:cardinality 1 ;
        owl:range xsd:dateTime
    ] ;
    owl:hasProperty [
        owl:onProperty :access_level ;
        owl:cardinality 1 ;
        owl:range [:Public, :Private, :Restricted]
    ] ;
    owl:hasProperty [
        owl:onProperty :documentation_url ;
        owl:maxCardinality 1 ;
        owl:range xsd:anyURI
    ] ;
    owl:hasProperty [
        owl:onProperty :git_commit ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] .

# ===== PUBLISHER ENTITY =====

:Publisher a owl:Class ;
    rdfs:label "Publisher"@en ;
    rdfs:comment "Organization publishing marketplace items" ;
    owl:equivalentClass foaf:Organization .

:Publisher
    owl:hasProperty [
        owl:onProperty :publisher_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :publisher_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :contact_email ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :published_items ;
        owl:minCardinality 0 ;
        owl:range :MarketplaceEntity
    ] ;
    owl:hasProperty [
        owl:onProperty :tier ;
        owl:cardinality 1 ;
        owl:range [:Free, :Premium, :Enterprise]
    ] .

# ===== LINKING ENTITIES (Cross-Marketplace) =====

:SkillUsesIntegration a owl:ObjectProperty ;
    rdfs:label "Skill Uses Integration"@en ;
    rdfs:domain :Skill ;
    rdfs:range :Integration ;
    rdfs:comment "A skill depends on an integration" .

:SkillUsesAgent a owl:ObjectProperty ;
    rdfs:label "Skill Uses Agent"@en ;
    rdfs:domain :Skill ;
    rdfs:range :Agent ;
    rdfs:comment "A skill invokes an agent" .

:AgentUsesIntegration a owl:ObjectProperty ;
    rdfs:label "Agent Uses Integration"@en ;
    rdfs:domain :Agent ;
    rdfs:range :Integration ;
    rdfs:comment "An agent calls external systems" .

:AgentConsumesData a owl:ObjectProperty ;
    rdfs:label "Agent Consumes Data"@en ;
    rdfs:domain :Agent ;
    rdfs:range :DataSource ;
    rdfs:comment "An agent queries data" .

:SkillConsumesData a owl:ObjectProperty ;
    rdfs:label "Skill Consumes Data"@en ;
    rdfs:domain :Skill ;
    rdfs:range :DataSource ;
    rdfs:comment "A skill reads data" .

# ===== BILLING & USAGE =====

:Usage a owl:Class ;
    rdfs:label "Usage"@en ;
    rdfs:comment "Billing event for an entity" .

:Usage
    owl:hasProperty [
        owl:onProperty :usage_id ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :entity ;
        owl:cardinality 1 ;
        owl:range :MarketplaceEntity
    ] ;
    owl:hasProperty [
        owl:onProperty :publisher ;
        owl:cardinality 1 ;
        owl:range :Publisher
    ] ;
    owl:hasProperty [
        owl:onProperty :invocation_count ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] ;
    owl:hasProperty [
        owl:onProperty :data_consumed_gb ;
        owl:maxCardinality 1 ;
        owl:range xsd:decimal
    ] ;
    owl:hasProperty [
        owl:onProperty :timestamp ;
        owl:cardinality 1 ;
        owl:range xsd:dateTime
    ] ;
    owl:hasProperty [
        owl:onProperty :amount_charged ;
        owl:cardinality 1 ;
        owl:range xsd:decimal
    ] .

# ===== SCHEMA & TYPE DEFINITIONS =====

:DataSchema a owl:Class ;
    rdfs:label "Data Schema"@en ;
    rdfs:comment "JSON Schema or XML Schema definition" .

:DataSchema
    owl:hasProperty [
        owl:onProperty :schema_type ;
        owl:cardinality 1 ;
        owl:range [:JSONSchema, :XMLSchema, :Avro]
    ] ;
    owl:hasProperty [
        owl:onProperty :schema_uri ;
        owl:cardinality 1 ;
        owl:range xsd:anyURI
    ] ;
    owl:hasProperty [
        owl:onProperty :version ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] .

:Capability a owl:Class ;
    rdfs:label "Capability"@en ;
    rdfs:comment "What an agent can do" .

:Capability
    owl:hasProperty [
        owl:onProperty :capability_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :input_schema ;
        owl:cardinality 1 ;
        owl:range :DataSchema
    ] ;
    owl:hasProperty [
        owl:onProperty :output_schema ;
        owl:cardinality 1 ;
        owl:range :DataSchema
    ] .

:Tool a owl:Class ;
    rdfs:label "Tool"@en ;
    rdfs:comment "Tool available to an agent" .

:Tool
    owl:hasProperty [
        owl:onProperty :tool_name ;
        owl:cardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :tool_description ;
        owl:maxCardinality 1 ;
        owl:range xsd:string
    ] ;
    owl:hasProperty [
        owl:onProperty :tool_uri ;
        owl:cardinality 1 ;
        owl:range xsd:anyURI
    ] .

:RateLimit a owl:Class ;
    rdfs:label "Rate Limit"@en ;
    rdfs:comment "Rate limiting for an integration" .

:RateLimit
    owl:hasProperty [
        owl:onProperty :requests_per_minute ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] ;
    owl:hasProperty [
        owl:onProperty :requests_per_day ;
        owl:cardinality 1 ;
        owl:range xsd:integer
    ] .
```

### 1.2 RDF Graph Instance Example

**File**: `/yawl/schema/marketplace/example-graph.ttl` (200 lines)

This file demonstrates concrete RDF instances showing cross-marketplace linking:

```turtle
@prefix : <http://yawl-marketplace.org/ontology#> .
@prefix example: <http://yawl-marketplace.org/example#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

# ===== SKILL EXAMPLE: ApprovalTask =====

example:ApprovalSkill a :Skill ;
    :skill_id "skill-approval-v1" ;
    :skill_name "Approval Task" ;
    :skill_description "Generic approval workflow with escalation" ;
    :version "1.0.0" ;
    :marketplace :SkillsMarketplace ;
    :publisher example:AcmeCorp ;
    :author example:AcmeCorp ;
    :published_at "2026-02-01T10:00:00Z"^^xsd:dateTime ;
    :updated_at "2026-02-15T14:30:00Z"^^xsd:dateTime ;
    :access_level :Public ;
    :usage_count 1250 ;
    :rating "4.8"^^xsd:decimal ;
    :tags "approval", "workflow", "bpm", "escalation" ;
    :yawl_xml "<specification><!-- YAWL XML here --></specification>" ;
    :input_schema example:ApprovalInputSchema ;
    :output_schema example:ApprovalOutputSchema ;
    :git_commit "a1b2c3d4e5f6" ;
    # Links to other marketplaces
    :skillUsesIntegration example:SlackIntegration ;
    :skillUsesIntegration example:EmailIntegration ;
    :skillUsesAgent example:ApprovalDecisionAgent ;
    :skillConsumesData example:UserAuditLog .

example:ApprovalInputSchema a :DataSchema ;
    :schema_type :JSONSchema ;
    :schema_uri <http://schemas.yawl-marketplace.org/approval-input.json> ;
    :version "1.0" .

example:ApprovalOutputSchema a :DataSchema ;
    :schema_type :JSONSchema ;
    :schema_uri <http://schemas.yawl-marketplace.org/approval-output.json> ;
    :version "1.0" .

# ===== INTEGRATION EXAMPLE: SlackIntegration =====

example:SlackIntegration a :Integration ;
    :integration_id "integration-slack-v2" ;
    :integration_name "Slack Integration" ;
    :target_system "Slack" ;
    :version "2.0.0" ;
    :marketplace :IntegrationsMarketplace ;
    :publisher example:SlackTeam ;
    :published_at "2026-01-15T09:00:00Z"^^xsd:dateTime ;
    :access_level :Public ;
    :mcp_endpoint "https://api.slack.com/mcp" ;
    :authentication_type :OAuth2 ;
    :supported_operations :Read, :Write ;
    :rate_limit example:SlackRateLimit ;
    :sla_uptime "99.95"^^xsd:decimal ;
    :cost_per_call "0.001"^^xsd:decimal ;
    :git_commit "b2c3d4e5f6g7" ;
    # Links to agents & data
    :integrationUsedByAgent example:NotificationAgent ;
    :integrationUsedBySkill example:ApprovalSkill .

example:SlackRateLimit a :RateLimit ;
    :requests_per_minute 60 ;
    :requests_per_day 86400 .

# ===== AGENT EXAMPLE: ApprovalDecisionAgent =====

example:ApprovalDecisionAgent a :Agent ;
    :agent_id "agent-approval-decision-v1" ;
    :agent_name "Approval Decision Agent" ;
    :model "claude-opus-4.6" ;
    :version "1.0.0" ;
    :marketplace :AgentsMarketplace ;
    :publisher example:AcmeCorp ;
    :published_at "2026-02-10T11:00:00Z"^^xsd:dateTime ;
    :access_level :Private ;
    :capabilities example:ApprovalCapability ;
    :tools example:SearchTool, example:DatabaseTool ;
    :cost_per_invocation "0.05"^^xsd:decimal ;
    :context_window 200000 ;
    :latency_p95 5000 ;
    :git_commit "c3d4e5f6g7h8" ;
    # Links
    :agentUsesIntegration example:DatabaseIntegration ;
    :agentConsumesData example:PolicyDatabase .

example:ApprovalCapability a :Capability ;
    :capability_name "Evaluate Approval Request" ;
    :input_schema example:ApprovalRequestSchema ;
    :output_schema example:ApprovalDecisionSchema .

example:SearchTool a :Tool ;
    :tool_name "Vector Search" ;
    :tool_description "Search policy documents by similarity" ;
    :tool_uri "https://tools.yawl-marketplace.org/search" .

example:DatabaseTool a :Tool ;
    :tool_name "SQL Query" ;
    :tool_description "Execute SQL queries against policy database" ;
    :tool_uri "https://tools.yawl-marketplace.org/sql" .

# ===== DATA SOURCE EXAMPLE: UserAuditLog =====

example:UserAuditLog a :DataSource ;
    :datasource_id "data-audit-log-v1" ;
    :datasource_name "User Audit Log" ;
    :data_type :Analytics ;
    :version "1.0.0" ;
    :marketplace :DataMarketplace ;
    :publisher example:SecurityTeam ;
    :published_at "2026-01-01T00:00:00Z"^^xsd:dateTime ;
    :update_frequency "PT1H" ;
    :schema example:AuditLogSchema ;
    :cost_per_gb "0.10"^^xsd:decimal ;
    :retention_days 365 ;
    :query_engine :SQL ;
    :git_commit "d4e5f6g7h8i9" ;
    # Links
    :dataConsumedBySkill example:ApprovalSkill ;
    :dataConsumedByAgent example:AnalyticsAgent .

example:AuditLogSchema a :DataSchema ;
    :schema_type :JSONSchema ;
    :schema_uri <http://schemas.yawl-marketplace.org/audit-log.json> ;
    :version "1.0" .

# ===== PUBLISHER EXAMPLE: AcmeCorp =====

example:AcmeCorp a :Publisher ;
    :publisher_id "publisher-acme-corp" ;
    :publisher_name "Acme Corporation" ;
    :contact_email "marketplace@acme.com" ;
    :tier :Enterprise ;
    :published_items example:ApprovalSkill, example:ApprovalDecisionAgent, example:NotificationAgent .

# ===== USAGE EXAMPLE: Billing =====

example:UsageEvent_20260215_001 a :Usage ;
    :usage_id "usage-20260215-001" ;
    :entity example:ApprovalSkill ;
    :publisher example:AcmeCorp ;
    :invocation_count 42 ;
    :timestamp "2026-02-15T23:59:59Z"^^xsd:dateTime ;
    :amount_charged "0.42"^^xsd:decimal .

example:UsageEvent_20260215_002 a :Usage ;
    :usage_id "usage-20260215-002" ;
    :entity example:SlackIntegration ;
    :publisher example:SlackTeam ;
    :invocation_count 156 ;
    :timestamp "2026-02-15T23:59:59Z"^^xsd:dateTime ;
    :amount_charged "0.156"^^xsd:decimal .

example:UsageEvent_20260215_003 a :Usage ;
    :usage_id "usage-20260215-003" ;
    :entity example:UserAuditLog ;
    :publisher example:SecurityTeam ;
    :data_consumed_gb "2.5"^^xsd:decimal ;
    :timestamp "2026-02-15T23:59:59Z"^^xsd:dateTime ;
    :amount_charged "0.25"^^xsd:decimal .
```

---

## Part 2: Core MVP Scope

### 2.1 RDF Store Setup (Apache Jena TDB2)

**File**: `/yawl/yawl-marketplace/src/main/java/org/yawlfoundation/yawl/marketplace/RDFGraphRepository.java`

```java
package org.yawlfoundation.yawl.marketplace;

import org.apache.jena.db.DBFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * RDF Graph Repository (Jena TDB2 Backend)
 *
 * Responsibilities:
 * - Load ontology (YAWL Marketplace Ontology)
 * - Manage marketplace entities (Skills, Integrations, Agents, Data)
 * - Index triples for fast SPARQL queries
 * - Persist graph to disk (TDB2)
 * - Support cross-marketplace linking
 */
@Repository
public class RDFGraphRepository {

    private final String tdb2Path;
    private final String ontologyPath;
    private Dataset dataset;
    private Model model;

    @Value("${rdf.tdb2.path:./data/rdf-store}")
    private String rdfTdb2Path;

    @Value("${rdf.ontology.path:classpath:ontology/yawl-marketplace-ontology.ttl}")
    private String rdfOntologyPath;

    public RDFGraphRepository() {
        this.tdb2Path = "./data/rdf-store";
        this.ontologyPath = "classpath:ontology/yawl-marketplace-ontology.ttl";
    }

    /**
     * Initialize RDF store: load ontology + create dataset
     */
    public void initialize() throws IOException {
        // 1. Create TDB2 dataset
        dataset = TDB2Factory.connectDataset(tdb2Path);
        model = dataset.getDefaultModel();

        // 2. Load YAWL Marketplace Ontology (if not already loaded)
        if (model.isEmpty()) {
            loadOntology();
        }

        // 3. Register custom SPARQL functions
        registerCustomFunctions();
    }

    /**
     * Load YAWL Marketplace Ontology from Turtle file
     */
    private void loadOntology() throws IOException {
        String ontologyContent = new String(
            Files.readAllBytes(Paths.get(ontologyPath))
        );

        // Parse Turtle
        model.read(
            new java.io.ByteArrayInputStream(ontologyContent.getBytes()),
            null,  // base URI
            "TURTLE"
        );

        // Commit to persistent storage
        dataset.commit();
    }

    /**
     * Add marketplace entity to RDF graph
     */
    public void addEntity(MarketplaceEntity entity) {
        Resource resource = model.createResource(entity.getUri());

        // Add RDF type
        resource.addProperty(RDF.type,
            model.createResource(entity.getEntityType()));

        // Add properties
        if (entity.getName() != null) {
            resource.addProperty(
                model.createProperty("http://yawl-marketplace.org/ontology#name"),
                entity.getName()
            );
        }

        if (entity.getVersion() != null) {
            resource.addProperty(
                model.createProperty("http://yawl-marketplace.org/ontology#version"),
                entity.getVersion()
            );
        }

        // Add other properties based on entity type...

        dataset.commit();
    }

    /**
     * Link entities across marketplaces
     * Example: Skill → Integration, Agent → Data
     */
    public void addCrossMarketplaceLink(
        String sourceUri,
        String linkType,
        String targetUri
    ) {
        Resource source = model.createResource(sourceUri);
        Resource target = model.createResource(targetUri);
        Property property = model.createProperty(
            "http://yawl-marketplace.org/ontology#" + linkType
        );

        source.addProperty(property, target);
        dataset.commit();
    }

    /**
     * Get entity by URI
     */
    public MarketplaceEntity getEntity(String uri) {
        Resource resource = model.getResource(uri);

        if (resource == null) {
            return null;
        }

        return parseResource(resource);
    }

    /**
     * Count entities by type
     */
    public long countEntities(String entityType) {
        String sparql = String.format(
            "SELECT (COUNT(?entity) as ?count) WHERE { " +
            "  ?entity rdf:type <%s> " +
            "}",
            entityType
        );

        // Execute query (see SPARQLQueryEngine)
        return 0;  // TODO: implement
    }

    /**
     * Register custom SPARQL functions (e.g., string matching, scoring)
     */
    private void registerCustomFunctions() {
        // Example: custom function for relevance scoring
        FunctionRegistry.get().put(
            "http://yawl-marketplace.org/functions#relevanceScore",
            new RelevanceScoreFunction()
        );
    }

    /**
     * Close dataset and persist to disk
     */
    public void close() {
        if (dataset != null) {
            dataset.commit();
            dataset.close();
        }
    }

    private MarketplaceEntity parseResource(Resource resource) {
        // Parse RDF resource into MarketplaceEntity POJO
        // Implementation details...
        return null;
    }
}
```

### 2.2 SPARQL Query Engine

**File**: `/yawl/yawl-marketplace/src/main/java/org/yawlfoundation/yawl/marketplace/SPARQLQueryEngine.java`

This class implements 15-20 pre-written discovery queries:

```java
package org.yawlfoundation.yawl.marketplace;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.springframework.stereotype.Service;

/**
 * SPARQL Query Engine
 *
 * Pre-written queries for cross-marketplace discovery:
 * 1. Find compatible skills by input/output type
 * 2. Find skills using a specific integration
 * 3. Find agents supporting a capability
 * 4. Find data sources by schema
 * 5. Cross-marketplace dependency graph
 * 6. Skills → Agents → Integrations chains
 * And 14 more...
 */
@Service
public class SPARQLQueryEngine {

    private final RDFGraphRepository rdfRepository;
    private QueryExecution queryExecution;

    // ===== QUERY 1: Find Skills by Input Type =====

    /**
     * Query: Find all skills accepting a specific input type
     *
     * Use case: "I need a skill that accepts OrderRequest as input"
     */
    public List<SkillDTO> findSkillsByInputType(String inputType) {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "PREFIX dcterms: <http://purl.org/dc/terms/> " +
            "" +
            "SELECT ?skill ?name ?version ?publisher " +
            "WHERE { " +
            "  ?skill a :Skill ; " +
            "    :skill_name ?name ; " +
            "    :version ?version ; " +
            "    :publisher ?pubUri ; " +
            "    :input_schema / :schema_uri ?inputUri . " +
            "  " +
            "  ?pubUri dcterms:title ?publisher . " +
            "  " +
            "  FILTER(CONTAINS(str(?inputUri), ?inputType)) " +
            "} " +
            "ORDER BY DESC(?version) ";

        return executeQuery(sparql, (row) -> {
            return new SkillDTO(
                row.getResource("skill").getURI(),
                row.getLiteral("name").getString(),
                row.getLiteral("version").getString(),
                row.getLiteral("publisher").getString()
            );
        });
    }

    // ===== QUERY 2: Find Skills Using Integration =====

    /**
     * Query: Find all skills that use a specific integration
     *
     * Use case: "Which skills call Salesforce?"
     */
    public List<SkillDTO> findSkillsUsingIntegration(String integrationId) {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "" +
            "SELECT ?skill ?name ?version ?integrationCount " +
            "WHERE { " +
            "  ?skill a :Skill ; " +
            "    :skill_name ?name ; " +
            "    :version ?version ; " +
            "    :skillUsesIntegration ?integration . " +
            "  " +
            "  ?integration :integration_id '" + integrationId + "' . " +
            "  " +
            "  # Count how many integrations this skill uses " +
            "  { " +
            "    SELECT ?skill (COUNT(?int) as ?integrationCount) " +
            "    WHERE { ?skill :skillUsesIntegration ?int } " +
            "    GROUP BY ?skill " +
            "  } " +
            "} " +
            "ORDER BY ?integrationCount DESC ";

        return executeQuery(sparql, (row) -> {
            return new SkillDTO(
                row.getResource("skill").getURI(),
                row.getLiteral("name").getString(),
                row.getLiteral("version").getString(),
                null
            );
        });
    }

    // ===== QUERY 3: Find Agents by Capability =====

    /**
     * Query: Find agents supporting a specific capability
     *
     * Use case: "Find agents that can evaluate approval requests"
     */
    public List<AgentDTO> findAgentsByCapability(String capabilityName) {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "" +
            "SELECT ?agent ?name ?model ?contextWindow " +
            "WHERE { " +
            "  ?agent a :Agent ; " +
            "    :agent_name ?name ; " +
            "    :model ?model ; " +
            "    :context_window ?contextWindow ; " +
            "    :capabilities [ :capability_name '" + capabilityName + "' ] . " +
            "} " +
            "ORDER BY DESC(?contextWindow) ";

        return executeQuery(sparql, (row) -> {
            return new AgentDTO(
                row.getResource("agent").getURI(),
                row.getLiteral("name").getString(),
                row.getLiteral("model").getString(),
                row.getLiteral("contextWindow").getInt()
            );
        });
    }

    // ===== QUERY 4: Find Data Sources by Schema =====

    /**
     * Query: Find data sources matching a schema pattern
     *
     * Use case: "Find analytics datasets with user audit information"
     */
    public List<DataSourceDTO> findDataSourcesBySchema(String schemaPattern) {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "" +
            "SELECT ?datasource ?name ?dataType ?costPerGb " +
            "WHERE { " +
            "  ?datasource a :DataSource ; " +
            "    :datasource_name ?name ; " +
            "    :data_type ?dataType ; " +
            "    :cost_per_gb ?costPerGb ; " +
            "    :schema / :schema_uri ?schemaUri . " +
            "  " +
            "  FILTER(CONTAINS(str(?schemaUri), ?schemaPattern)) " +
            "} " +
            "ORDER BY ?costPerGb ";

        return executeQuery(sparql, (row) -> {
            return new DataSourceDTO(
                row.getResource("datasource").getURI(),
                row.getLiteral("name").getString(),
                row.getLiteral("dataType").getString(),
                row.getLiteral("costPerGb").getDouble()
            );
        });
    }

    // ===== QUERY 5: Skill → Integration → Agent Chain =====

    /**
     * Query: Find all skills and their dependency chain
     *
     * Use case: "Map how Approval Skill uses integrations and agents"
     * Result: Skill → Integration(Slack, Email) → Agent(Decision) → Data(Audit)
     */
    public List<DependencyChainDTO> findDependencyChains(String skillId) {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "" +
            "SELECT ?skill ?skillName " +
            "       ?integration ?integrationName " +
            "       ?agent ?agentName " +
            "       ?datasource ?datasourceName " +
            "WHERE { " +
            "  ?skill a :Skill ; " +
            "    :skill_id '" + skillId + "' ; " +
            "    :skill_name ?skillName . " +
            "  " +
            "  OPTIONAL { " +
            "    ?skill :skillUsesIntegration ?integration . " +
            "    ?integration :integration_name ?integrationName . " +
            "  } " +
            "  " +
            "  OPTIONAL { " +
            "    ?skill :skillUsesAgent ?agent . " +
            "    ?agent :agent_name ?agentName . " +
            "  } " +
            "  " +
            "  OPTIONAL { " +
            "    ?skill :skillConsumesData ?datasource . " +
            "    ?datasource :datasource_name ?datasourceName . " +
            "  } " +
            "} ";

        return executeQuery(sparql, (row) -> {
            return new DependencyChainDTO(
                row.getResource("skill").getURI(),
                row.getLiteral("skillName").getString(),
                row.getResource("integration") != null ?
                    row.getResource("integration").getURI() : null,
                row.getLiteral("integrationName") != null ?
                    row.getLiteral("integrationName").getString() : null,
                row.getResource("agent") != null ?
                    row.getResource("agent").getURI() : null,
                row.getLiteral("agentName") != null ?
                    row.getLiteral("agentName").getString() : null,
                row.getResource("datasource") != null ?
                    row.getResource("datasource").getURI() : null,
                row.getLiteral("datasourceName") != null ?
                    row.getLiteral("datasourceName").getString() : null
            );
        });
    }

    // ===== QUERY 6: Calculate Total Cost of Skill (All Dependencies) =====

    /**
     * Query: Calculate total monthly cost of using a skill
     *
     * Cost = (Skill invocations × cost_per_call) +
     *        sum(Integration calls × cost_per_call) +
     *        sum(Agent invocations × cost_per_invocation) +
     *        sum(Data consumed × cost_per_gb)
     */
    public SkillCostAnalysisDTO analyzeCost(String skillId, int monthlyInvocations) {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "" +
            "SELECT " +
            "  ?skillCostPerCall " +
            "  ?integrationCostPerCall " +
            "  ?agentCostPerInvocation " +
            "  ?dataCostPerGb " +
            "WHERE { " +
            "  ?skill a :Skill ; " +
            "    :skill_id '" + skillId + "' . " +
            "  " +
            "  # Get skill cost (if any) " +
            "  OPTIONAL { ?skill :cost_per_call ?skillCostPerCall } " +
            "  " +
            "  # Get integration costs " +
            "  OPTIONAL { " +
            "    ?skill :skillUsesIntegration / :cost_per_call ?integrationCostPerCall . " +
            "  } " +
            "  " +
            "  # Get agent costs " +
            "  OPTIONAL { " +
            "    ?skill :skillUsesAgent / :cost_per_invocation ?agentCostPerInvocation . " +
            "  } " +
            "  " +
            "  # Get data costs " +
            "  OPTIONAL { " +
            "    ?skill :skillConsumesData / :cost_per_gb ?dataCostPerGb . " +
            "  } " +
            "} ";

        // Execute and calculate total cost
        // Implementation details...
        return null;
    }

    // ===== QUERY 7: Marketplace Growth Stats =====

    /**
     * Query: Count entities by marketplace and type
     *
     * Use case: Dashboard showing "Skills: 250, Integrations: 45, Agents: 12, Data: 8"
     */
    public MarketplaceStatsDTO getMarketplaceStats() {
        String sparql = "" +
            "PREFIX : <http://yawl-marketplace.org/ontology#> " +
            "" +
            "SELECT " +
            "  (COUNT(DISTINCT ?skill) AS ?skillCount) " +
            "  (COUNT(DISTINCT ?integration) AS ?integrationCount) " +
            "  (COUNT(DISTINCT ?agent) AS ?agentCount) " +
            "  (COUNT(DISTINCT ?datasource) AS ?datasourceCount) " +
            "  (COUNT(DISTINCT ?publisher) AS ?publisherCount) " +
            "WHERE { " +
            "  OPTIONAL { ?skill a :Skill } " +
            "  OPTIONAL { ?integration a :Integration } " +
            "  OPTIONAL { ?agent a :Agent } " +
            "  OPTIONAL { ?datasource a :DataSource } " +
            "  OPTIONAL { " +
            "    ?entity a :MarketplaceEntity ; " +
            "      :publisher ?publisher . " +
            "  } " +
            "} ";

        // Execute and return stats
        return null;
    }

    // ===== Helper: Execute Query and Map Results =====

    private <T> List<T> executeQuery(String sparql, ResultProcessor<T> processor) {
        List<T> results = new java.util.ArrayList<>();

        try {
            QueryFactory qf = QueryFactory.create(sparql);
            queryExecution = QueryExecutionFactory.create(qf, rdfRepository.getModel());

            ResultSet rs = queryExecution.execSelect();

            while (rs.hasNext()) {
                QuerySolution row = rs.next();
                results.add(processor.process(row));
            }
        } finally {
            if (queryExecution != null) {
                queryExecution.close();
            }
        }

        return results;
    }

    @FunctionalInterface
    private interface ResultProcessor<T> {
        T process(QuerySolution row);
    }
}
```

### 2.3 Git Sync Layer

**File**: `/yawl/yawl-marketplace/src/main/java/org/yawlfoundation/yawl/marketplace/GitSyncService.java`

```java
package org.yawlfoundation.yawl.marketplace;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Git Sync Service
 *
 * Bidirectional sync between RDF graph and Git repository
 *
 * Features:
 * - Commit RDF changes to Git (Turtle format)
 * - Pull latest from Git + reload RDF
 * - Track history (who changed what, when)
 * - Immutable audit trail
 */
@Service
public class GitSyncService {

    private final String gitRepoPath;
    private final String gitBranch;
    private Git git;
    private RDFGraphRepository rdfRepository;

    public GitSyncService(RDFGraphRepository rdfRepository) {
        this.rdfRepository = rdfRepository;
        this.gitRepoPath = "./marketplace-registry";
        this.gitBranch = "main";
    }

    /**
     * Initialize Git repository (clone if not exists)
     */
    public void initialize(String gitUrl) throws IOException {
        File repoDir = new File(gitRepoPath);

        if (repoDir.exists()) {
            // Open existing repo
            this.git = Git.open(repoDir);
        } else {
            // Clone repo
            this.git = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(repoDir)
                .call();
        }
    }

    /**
     * Commit RDF changes to Git
     *
     * Workflow:
     * 1. Serialize RDF model to Turtle file
     * 2. Git add + commit
     * 3. Create signed commit (GPG)
     * 4. Push to remote
     */
    public CommitResult commitChanges(
        String entityId,
        String entityType,
        String changeDescription,
        String authorName,
        String authorEmail
    ) throws IOException {

        // 1. Serialize RDF to Turtle
        String turtleContent = serializeRDF();

        // 2. Write to file (organized by marketplace + entityId)
        String entityDir = entityType.toLowerCase() + "s";
        Path filePath = Paths.get(gitRepoPath, entityDir, entityId + ".ttl");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, turtleContent.getBytes(StandardCharsets.UTF_8));

        // 3. Git add
        git.add()
            .addFilepattern(entityDir + "/" + entityId + ".ttl")
            .call();

        // 4. Git commit with details
        String commitMessage = String.format(
            "[%s] %s\n\nEntity: %s\nDescription: %s",
            entityType, entityId, entityId, changeDescription
        );

        RevCommit commit = git.commit()
            .setAuthor(authorName, authorEmail)
            .setMessage(commitMessage)
            .call();

        // 5. Push to remote
        git.push()
            .setRemote("origin")
            .setBranchesToPush(Collections.singleton(gitBranch))
            .call();

        return new CommitResult(
            commit.getName(),  // commit hash
            entityId,
            changeDescription,
            Instant.ofEpochSecond(commit.getCommitTime()),
            authorName
        );
    }

    /**
     * Pull latest from Git + reload RDF
     *
     * Workflow:
     * 1. Git pull (with rebase)
     * 2. Load all Turtle files
     * 3. Update RDF graph
     */
    public PullResult pullLatest() throws IOException {
        PullResult result = git.pull()
            .setRebase(true)
            .call();

        if (result.isSuccessful()) {
            // 1. Load all Turtle files from Git
            loadAllTurtleFiles();

            // 2. Rebuild RDF graph
            rdfRepository.initialize();
        }

        return result;
    }

    /**
     * Get audit trail for an entity
     *
     * Returns: list of commits affecting this entity (sorted by time, newest first)
     */
    public List<AuditEntry> getAuditTrail(String entityId) throws IOException {
        List<AuditEntry> entries = new java.util.ArrayList<>();

        String filePath = entityId + ".ttl";

        // Get all commits affecting this file
        Iterable<RevCommit> commits = git.log()
            .addPath(filePath)
            .call();

        for (RevCommit commit : commits) {
            entries.add(new AuditEntry(
                commit.getName(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                commit.getFullMessage(),
                Instant.ofEpochSecond(commit.getCommitTime())
            ));
        }

        return entries;
    }

    /**
     * Get diff between two versions (commits)
     */
    public String getDiff(String fromCommitId, String toCommitId) throws IOException {
        ObjectId fromId = git.getRepository().resolve(fromCommitId);
        ObjectId toId = git.getRepository().resolve(toCommitId);

        // Get diff
        List<String> diffs = new java.util.ArrayList<>();

        git.diff()
            .setOldTree(fromId)
            .setNewTree(toId)
            .call()
            .forEach(entry -> diffs.add(entry.toString()));

        return String.join("\n", diffs);
    }

    /**
     * Revert to previous version
     */
    public void revertToCommit(String commitId) throws IOException {
        git.revert()
            .include(git.getRepository().resolve(commitId))
            .call();

        git.push().call();
    }

    // ===== Helper Methods =====

    private String serializeRDF() {
        // Serialize RDF model to Turtle format
        // Using Apache Jena's model.write()
        java.io.StringWriter writer = new java.io.StringWriter();
        rdfRepository.getModel().write(writer, "TURTLE");
        return writer.toString();
    }

    private void loadAllTurtleFiles() throws IOException {
        Files.walk(Paths.get(gitRepoPath))
            .filter(path -> path.toString().endsWith(".ttl"))
            .filter(path -> !path.toString().contains(".git"))
            .forEach(path -> {
                try {
                    String content = new String(
                        Files.readAllBytes(path),
                        StandardCharsets.UTF_8
                    );
                    // Parse and add to RDF graph
                    rdfRepository.getModel().read(
                        new java.io.ByteArrayInputStream(content.getBytes()),
                        null,
                        "TURTLE"
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    // ===== DTOs =====

    public static class CommitResult {
        public final String commitHash;
        public final String entityId;
        public final String description;
        public final Instant timestamp;
        public final String author;

        public CommitResult(String hash, String id, String desc, Instant time, String author) {
            this.commitHash = hash;
            this.entityId = id;
            this.description = desc;
            this.timestamp = time;
            this.author = author;
        }
    }

    public static class AuditEntry {
        public final String commitId;
        public final String author;
        public final String email;
        public final String message;
        public final Instant timestamp;

        public AuditEntry(String id, String auth, String email, String msg, Instant time) {
            this.commitId = id;
            this.author = auth;
            this.email = email;
            this.message = msg;
            this.timestamp = time;
        }
    }
}
```

---

## Part 3: Billing Model (MVP)

### 3.1 Billing Tiers

| Tier | Skills | Integrations | Agents | Data | Monthly Cost | Use Case |
|------|--------|--------------|--------|------|--------------|----------|
| **Free** | Browse only | Browse only | - | Browse only | $0 | Evaluation |
| **Pro** ($99) | Unlimited calls | 10 calls/day | 1 agent | 100GB/month | $99 | Small team |
| **Business** ($999) | Unlimited calls | 10K calls/day | 5 agents | 10TB/month | $999 | Department |
| **Enterprise** (Custom) | All | Unlimited | Unlimited | Unlimited | Negotiated | Large org |

### 3.2 Billing Calculator

**File**: `/yawl/yawl-marketplace/src/main/java/org/yawlfoundation/yawl/marketplace/BillingCalculator.java`

```java
package org.yawlfoundation.yawl.marketplace;

import org.springframework.stereotype.Service;

/**
 * Billing Calculator
 *
 * Calculates charges based on:
 * - Entity invocations (skills, agents)
 * - Integration calls
 * - Data consumed (GB)
 * - Subscription tier
 */
@Service
public class BillingCalculator {

    // Pricing per entity type (USD)
    private static final double SKILL_CALL_COST = 0.001;
    private static final double INTEGRATION_CALL_COST = 0.001;
    private static final double AGENT_CALL_COST = 0.05;
    private static final double DATA_PER_GB_COST = 0.10;

    /**
     * Calculate monthly charge for an organization
     */
    public MonthlyChargeDTO calculateMonthlyCharge(
        String organizationId,
        BillingPeriod period
    ) {
        // 1. Get all usage events for this organization in period
        List<UsageEvent> events = getUsageEventsForOrg(organizationId, period);

        // 2. Calculate costs by category
        double skillCost = events.stream()
            .filter(e -> e.getEntityType().equals("Skill"))
            .mapToDouble(e -> e.getInvocationCount() * SKILL_CALL_COST)
            .sum();

        double integrationCost = events.stream()
            .filter(e -> e.getEntityType().equals("Integration"))
            .mapToDouble(e -> e.getInvocationCount() * INTEGRATION_CALL_COST)
            .sum();

        double agentCost = events.stream()
            .filter(e -> e.getEntityType().equals("Agent"))
            .mapToDouble(e -> e.getInvocationCount() * AGENT_CALL_COST)
            .sum();

        double dataCost = events.stream()
            .filter(e -> e.getEntityType().equals("DataSource"))
            .mapToDouble(e -> e.getDataConsumedGb() * DATA_PER_GB_COST)
            .sum();

        // 3. Apply tier-based discounts
        String tier = getSubscriptionTier(organizationId);
        double total = skillCost + integrationCost + agentCost + dataCost;
        double discount = calculateDiscount(tier, total);
        double finalCharge = total - discount;

        return new MonthlyChargeDTO(
            organizationId,
            period,
            skillCost,
            integrationCost,
            agentCost,
            dataCost,
            discount,
            finalCharge,
            events.size()
        );
    }

    /**
     * Calculate discount based on subscription tier
     */
    private double calculateDiscount(String tier, double subtotal) {
        return switch (tier) {
            case "Free" -> 0.0;
            case "Pro" -> {
                // Free: 100 skill calls, 10 integration calls per day
                yield subtotal * 0.05;  // 5% discount
            }
            case "Business" -> subtotal * 0.15;  // 15% discount
            case "Enterprise" -> subtotal * 0.30;  // 30% discount
            default -> 0.0;
        };
    }

    // ===== Helper Methods =====

    private List<UsageEvent> getUsageEventsForOrg(String orgId, BillingPeriod period) {
        // Query database for usage events
        // Implementation details...
        return new java.util.ArrayList<>();
    }

    private String getSubscriptionTier(String orgId) {
        // Query database for org subscription tier
        // Implementation details...
        return "Free";
    }

    // ===== DTOs =====

    public static class MonthlyChargeDTO {
        public final String organizationId;
        public final BillingPeriod period;
        public final double skillCost;
        public final double integrationCost;
        public final double agentCost;
        public final double dataCost;
        public final double discount;
        public final double totalCharge;
        public final int usageEventCount;

        public MonthlyChargeDTO(
            String orgId, BillingPeriod period, double skillCost, double intCost,
            double agentCost, double dataCost, double discount, double total,
            int eventCount
        ) {
            this.organizationId = orgId;
            this.period = period;
            this.skillCost = skillCost;
            this.integrationCost = intCost;
            this.agentCost = agentCost;
            this.dataCost = dataCost;
            this.discount = discount;
            this.totalCharge = total;
            this.usageEventCount = eventCount;
        }
    }

    public static class BillingPeriod {
        public final java.time.YearMonth month;
        public final java.time.LocalDate startDate;
        public final java.time.LocalDate endDate;

        public BillingPeriod(int year, int month) {
            java.time.YearMonth ym = java.time.YearMonth.of(year, month);
            this.month = ym;
            this.startDate = ym.atDay(1);
            this.endDate = ym.atEndOfMonth();
        }
    }
}
```

---

## Part 4: 4-Week Implementation Roadmap

### Week 1: Foundation (RDF Ontology & Jena Setup)

**Deliverables**:
1. YAWL Marketplace Ontology (Turtle, 550 lines)
   - Classes: Skill, Integration, Agent, DataSource, Publisher
   - Properties: names, versions, costs, dependencies
   - Cross-linking: SkillUsesIntegration, AgentConsumesData, etc.

2. RDF Graph Repository (Java, 200 lines)
   - Apache Jena TDB2 initialization
   - Load ontology from file
   - Add/get/update entities in graph
   - Persist to disk

3. SPARQL Endpoint (HTTP)
   - Jena Fuseki server (Docker)
   - SPARQL endpoint: `/federation/sparql`
   - Support SELECT, CONSTRUCT, ASK queries

4. Git integration skeleton
   - JGit library setup
   - Repository initialization
   - Initial Turtle files committed

**Success Criteria**:
- Ontology validates with no errors
- Jena stores 100+ triples in TDB2
- SPARQL endpoint responds to test queries
- Git repo syncs with local filesystem

**Files to Create**:
```
yawl-marketplace/
├── src/main/java/org/yawlfoundation/yawl/marketplace/
│   ├── RDFGraphRepository.java
│   ├── SPARQLQueryEngine.java (stub)
│   └── GitSyncService.java (stub)
├── src/main/resources/
│   └── ontology/
│       ├── yawl-marketplace-ontology.ttl
│       └── example-graph.ttl
└── docker-compose.yml (Jena + PostgreSQL)
```

### Week 2: SPARQL Discovery Engine (15-20 Queries)

**Deliverables**:
1. 15 core SPARQL queries
   - Find skills by input type
   - Find skills using integration
   - Find agents by capability
   - Find data sources by schema
   - Skill → Integration → Agent → Data chains
   - Cost analysis queries
   - Marketplace stats

2. Query optimization
   - Index optimization in Jena
   - Cache frequently-used queries
   - Query performance benchmarking (<500ms target)

3. REST API endpoints
   - GET /api/skills (with filters)
   - GET /api/integrations
   - GET /api/agents
   - GET /api/data-sources
   - POST /api/search (SPARQL query endpoint)
   - GET /api/marketplace-stats

4. Integration tests
   - Load sample RDF (1000 entities)
   - Run all 15 queries
   - Verify latency <500ms (p95)

**Success Criteria**:
- All 15 queries execute <500ms (p95)
- Discovery API returns correct results
- Integration tests pass
- Can search across all 4 marketplaces

**Files to Create**:
```
yawl-marketplace/
├── src/main/java/org/yawlfoundation/yawl/marketplace/
│   ├── SPARQLQueryEngine.java (complete)
│   ├── rest/
│   │   ├── SkillController.java
│   │   ├── IntegrationController.java
│   │   ├── AgentController.java
│   │   └── DataSourceController.java
│   └── dto/
│       ├── SkillDTO.java
│       ├── DependencyChainDTO.java
│       └── MarketplaceStatsDTO.java
├── src/test/java/.../
│   └── SPARQLPerformanceTest.java
```

### Week 3: Git Sync & Governance

**Deliverables**:
1. Git sync layer (bidirectional)
   - Commit RDF changes to Git
   - Pull latest + reload graph
   - Track history per entity
   - Immutable audit trail

2. Governance features
   - Change notifications (GitHub issues/PRs)
   - Version control (MAJOR.MINOR.PATCH)
   - Breaking change detection
   - Approval workflow

3. Integration tests
   - Commit → Git → Reload → Query
   - Audit trail retrieval
   - Diff computation
   - Rollback capability

**Success Criteria**:
- Git history shows all marketplace changes
- Audit trail is complete and immutable
- Breaking changes detected automatically
- Sync latency <2s

**Files to Create**:
```
yawl-marketplace/
├── src/main/java/org/yawlfoundation/yawl/marketplace/
│   ├── GitSyncService.java (complete)
│   ├── governance/
│   │   ├── ChangeNotificationService.java
│   │   ├── VersionValidator.java
│   │   └── BreakingChangeDetector.java
│   └── audit/
│       └── AuditService.java
├── src/test/java/.../
│   └── GitSyncIntegrationTest.java
```

### Week 4: Billing & Integration Tests

**Deliverables**:
1. Billing system
   - Usage event tracking (Skill calls, Integration calls, Agent calls, Data consumed)
   - Monthly charge calculation
   - Tier-based pricing (Free, Pro, Business, Enterprise)
   - Invoice generation (CSV)

2. End-to-end tests
   - Create skill with dependencies
   - Track usage across marketplaces
   - Calculate charges
   - Verify all 4 marketplaces integrated

3. Documentation
   - API documentation (Swagger/OpenAPI)
   - Ontology documentation
   - Deployment guide (Docker Compose)
   - Example queries

**Success Criteria**:
- Billing API calculates charges correctly
- 1000+ entities in RDF graph
- All 4 marketplaces linked and discoverable
- SPARQL queries <500ms (p95)
- Git history shows all changes (audit trail)
- Full integration test passes

**Files to Create**:
```
yawl-marketplace/
├── src/main/java/org/yawlfoundation/yawl/marketplace/
│   ├── BillingCalculator.java
│   ├── billing/
│   │   ├── UsageEventService.java
│   │   ├── InvoiceGenerator.java
│   │   └── StripeWebhookHandler.java
│   └── rest/
│       ├── BillingController.java
│       └── UsageController.java
├── src/test/java/.../
│   └── EndToEndIntegrationTest.java
├── docs/
│   ├── API.md
│   ├── ONTOLOGY.md
│   ├── DEPLOYMENT.md
│   └── EXAMPLE-QUERIES.md
```

---

## Part 5: Maven Module Structure

**Add to pom.xml** (in parent):

```xml
<module>yawl-marketplace</module>
```

**Create** `/yawl/yawl-marketplace/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Alpha</version>
    </parent>

    <artifactId>yawl-marketplace</artifactId>
    <name>YAWL Marketplace Platform</name>
    <description>Unified RDF-backed marketplace for skills, integrations, agents, and data</description>

    <dependencies>
        <!-- Apache Jena (RDF & SPARQL) -->
        <dependency>
            <groupId>org.apache.jena</groupId>
            <artifactId>apache-jena-libs</artifactId>
            <version>5.0.0</version>
            <type>pom</type>
        </dependency>
        <dependency>
            <groupId>org.apache.jena</groupId>
            <artifactId>jena-core</artifactId>
            <version>5.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.apache.jena</groupId>
            <artifactId>jena-arq</artifactId>
            <version>5.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.apache.jena</groupId>
            <artifactId>jena-tdb2</artifactId>
            <version>5.0.0</version>
        </dependency>

        <!-- JGit (Git integration) -->
        <dependency>
            <groupId>org.eclipse.jgit</groupId>
            <artifactId>org.eclipse.jgit</artifactId>
            <version>6.8.0.202312181205-r</version>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.2.2</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
            <version>3.2.2</version>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.1</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.9.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>5.9.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>3.2.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Part 6: Docker Compose (Local Development)

**File**: `/yawl/yawl-marketplace/docker-compose.yml`

```yaml
version: '3.9'

services:
  # Apache Jena Fuseki (SPARQL Endpoint)
  jena:
    image: stain/jena-fuseki:latest
    ports:
      - "3030:3030"
    volumes:
      - jena-data:/fuseki/data
    environment:
      FUSEKI_DATASET_NAME: /federation

  # PostgreSQL (Case Store + Billing)
  postgres:
    image: postgres:15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: marketplace
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: dev_password_123
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql

  # Spring Boot API
  api:
    build:
      context: .
      dockerfile: Dockerfile.api
    ports:
      - "8080:8080"
    environment:
      SPARQL_ENDPOINT: http://jena:3030/federation/sparql
      DATABASE_URL: jdbc:postgresql://postgres:5432/marketplace
      DATABASE_USER: admin
      DATABASE_PASSWORD: dev_password_123
    depends_on:
      - jena
      - postgres

volumes:
  jena-data:
  postgres-data:
```

---

## Part 7: Success Criteria & Metrics

By end of Week 4, the MVP must achieve:

| Metric | Target | Validation |
|--------|--------|-----------|
| **RDF Entities** | 1000+ | Count query on Jena: `SELECT (COUNT(?x) AS ?count) WHERE { ?x rdf:type ?type }` |
| **SPARQL Latency (p95)** | <500ms | Query 15 discovery queries, measure 100x each |
| **Git History** | 100% of changes | Pull latest, verify all 1000 entities have git commits |
| **Cross-marketplace Links** | 100% verified | Run query: `SELECT ?skill ?integration ?agent ?data WHERE { ... }` |
| **Billing Accuracy** | 100% | Create 10 test usage events, calculate charges, verify against manual calc |
| **Uptime** | >99% | Run 24h stability test |
| **Integration Tests** | 100% passing | All modules passing CI/CD |

---

## Part 8: Risk Assessment & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| **SPARQL query complexity** | Medium | High | Pre-optimize queries, cache results, index optimization |
| **RDF scalability** | Medium | Medium | Test with 10K entities, graph partitioning if needed |
| **Git sync delays** | Low | Medium | Async commits, queue-based sync |
| **Billing accuracy** | Low | Critical | Comprehensive tests, manual validation against invoices |
| **Cross-marketplace linking errors** | Medium | High | Property validation, SHACL constraints |
| **Authentication/Authorization gaps** | Low | Critical | OAuth2 + JWT tokens, RBAC enforcement |

---

## Part 9: Post-MVP Roadmap (Months 3-6)

1. **Advanced Features**:
   - Federation (multi-tenant, decentralized registry)
   - SHACL validation (schema constraints)
   - Petri net verification (deadlock detection)
   - Advanced billing (usage analytics, reports)

2. **Marketplace Growth**:
   - 100+ publishers
   - 5000+ entities across 4 marketplaces
   - $10K → $100K MRR

3. **Platform Features**:
   - GraphQL API (in addition to REST)
   - Full-text search
   - Recommendation engine
   - Community reviews & ratings

---

## Conclusion

This 4-week MVP delivers a **unified, RDF-backed marketplace platform** connecting YAWL skills, integrations, agents, and data. By focusing on the 80/20 principle, we avoid complexity while delivering core value: cross-marketplace discovery, Git-backed versioning, and usage-based billing.

**Key Success Factors**:
- **Simplicity**: Turtle files, Jena TDB2, PostgreSQL (no exotic tech)
- **Git-native**: All changes tracked, auditable, reversible
- **SPARQL-first**: Discovery queries are pre-written, optimized
- **Billing as first-class feature**: Usage tracking from day one
- **Small team**: 3 engineers, $75K, 4 weeks

**Next Steps**:
1. Get executive approval for scope
2. Assign engineering team
3. Set up Git repos + CI/CD pipeline
4. Begin Week 1: Ontology + Jena setup

---

**Document Version**: 1.0
**Status**: Ready for Implementation
**Recommended Action**: Start Week 1 immediately
