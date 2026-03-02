package org.yawlfoundation.yawl.datamodelling.bridge;

import java.io.IOException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.bridge.JTBDTestSupport.assertPlausible;
import static org.yawlfoundation.yawl.datamodelling.bridge.JTBDTestSupport.loadFixture;

/**
 * L4 Jobs To Be Done tests for the DataModelling native bridge.
 *
 * <p>Each test encodes one real business job performed by a real actor
 * under a real business trigger. Tests exercise multi-step L3 workflows
 * and assert plausibility of the final output.
 *
 * <p><b>These tests skip gracefully when the native library is absent.</b>
 * The {@code assumeTrue(l3.isAvailable())} guard ensures CI stays green
 * on machines without {@code libdatamodelling.so}. Skipped ≠ Failed.
 *
 * <p>JTBD tests block <em>release</em>, not merge.
 * They must pass in the native-library-present release environment.
 *
 * <p>Jobs under test:
 * <ol>
 *   <li>{@link DataModellingJTBDJob#SCHEMA_DOCUMENTATION_PUBLISHING} — DataEngineer / ApiDocumentationUpdate</li>
 *   <li>{@link DataModellingJTBDJob#PROCESS_DECISION_EXTRACTION} — BusinessAnalyst / DecisionGovernanceReview</li>
 *   <li>{@link DataModellingJTBDJob#VOCABULARY_HARMONIZATION} — DataSteward / OpenDataComplianceAudit</li>
 *   <li>{@link DataModellingJTBDJob#SCHEMA_CONSTRAINT_DISCOVERY} — DataGovernanceOfficer / DataQualityIncident</li>
 *   <li>{@link DataModellingJTBDJob#KNOWLEDGE_GAP_ANALYSIS} — KnowledgeEngineer / DomainModelRefactoring</li>
 * </ol>
 */
class DataModellingJTBDTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Job 1: Schema Documentation Publishing
    // Actor: DataEngineer | Trigger: ApiDocumentationUpdate
    // ─────────────────────────────────────────────────────────────────────────

    @JTBDTest(DataModellingJTBDJob.SCHEMA_DOCUMENTATION_PUBLISHING)
    void schemaDocumentationPublishing_dataEngineer_apiDocumentationUpdate() throws IOException {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assumeTrue(l3.isAvailable(),
                "libdatamodelling.so not loaded — skipping live JTBD test "
                    + DataModellingJTBDJob.SCHEMA_DOCUMENTATION_PUBLISHING.name());

            // Step 1: import ODCS schema
            String importReq = loadFixture("odcs-schema-simple");
            String importedSchema = l3.importOdcsSchema(importReq);
            assertPlausible(importedSchema, "schema_id");

            // Step 2: convert ODCS schema to OpenAPI
            String convertReq = loadFixture("convert-odcs-to-openapi");
            String openapi = l3.convertOdcsToOpenapi(convertReq);
            assertPlausible(openapi, "openapi");

            // Step 3: export OpenAPI schema — final business output
            String exported = l3.exportOpenapiSchema(convertReq);
            assertPlausible(exported, "content");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job 2: Process Decision Extraction
    // Actor: BusinessAnalyst | Trigger: DecisionGovernanceReview
    // ─────────────────────────────────────────────────────────────────────────

    @JTBDTest(DataModellingJTBDJob.PROCESS_DECISION_EXTRACTION)
    void processDecisionExtraction_businessAnalyst_decisionGovernanceReview() throws IOException {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assumeTrue(l3.isAvailable(),
                "libdatamodelling.so not loaded — skipping live JTBD test "
                    + DataModellingJTBDJob.PROCESS_DECISION_EXTRACTION.name());

            // Step 1: import BPMN process
            String processReq = loadFixture("bpmn-process-simple");
            String importedProcess = l3.importBpmnProcess(processReq);
            assertPlausible(importedProcess, "process_id");

            // Step 2: convert BPMN to DMN decision
            String dmn = l3.convertBpmnToDmn(processReq);
            assertPlausible(dmn, "decision_id");

            // Step 3: validate the DMN decision — final governance artefact
            String validationResult = l3.validateDmnDecision(processReq);
            assertPlausible(validationResult, "valid");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job 3: Vocabulary Harmonization
    // Actor: DataSteward | Trigger: OpenDataComplianceAudit
    // ─────────────────────────────────────────────────────────────────────────

    @JTBDTest(DataModellingJTBDJob.VOCABULARY_HARMONIZATION)
    void vocabularyHarmonization_dataSteward_openDataComplianceAudit() throws IOException {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assumeTrue(l3.isAvailable(),
                "libdatamodelling.so not loaded — skipping live JTBD test "
                    + DataModellingJTBDJob.VOCABULARY_HARMONIZATION.name());

            // Step 1: import source vocabulary
            String vocabReq = loadFixture("vocabulary-simple");
            String importedVocab = l3.importVocabulary(vocabReq);
            assertPlausible(importedVocab, "vocabulary_id");

            // Step 2: convert vocabulary to ODCS format
            String odcsVocab = l3.convertVocabularyToOdcs(vocabReq);
            assertPlausible(odcsVocab, "schema_id");

            // Step 3: map vocabularies to produce compliance artefact
            String mappings = l3.mapVocabularies(vocabReq);
            assertPlausible(mappings, "mappings");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job 4: Schema Constraint Discovery
    // Actor: DataGovernanceOfficer | Trigger: DataQualityIncident
    // ─────────────────────────────────────────────────────────────────────────

    @JTBDTest(DataModellingJTBDJob.SCHEMA_CONSTRAINT_DISCOVERY)
    void schemaConstraintDiscovery_dataGovernanceOfficer_dataQualityIncident() throws IOException {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assumeTrue(l3.isAvailable(),
                "libdatamodelling.so not loaded — skipping live JTBD test "
                    + DataModellingJTBDJob.SCHEMA_CONSTRAINT_DISCOVERY.name());

            // Step 1: import ODCS schema under investigation
            String schemaReq = loadFixture("odcs-schema-simple");
            String importedSchema = l3.importOdcsSchema(schemaReq);
            assertPlausible(importedSchema, "schema_id");

            // Step 2: infer constraints from schema structure
            String constraints = l3.inferConstraints(schemaReq);
            assertPlausible(constraints, "constraints");

            // Step 3: export constraint set as governance artefact
            String constraintSet = l3.exportConstraintSet(schemaReq);
            assertPlausible(constraintSet, "content");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job 5: Knowledge Gap Analysis
    // Actor: KnowledgeEngineer | Trigger: DomainModelRefactoring
    // ─────────────────────────────────────────────────────────────────────────

    @JTBDTest(DataModellingJTBDJob.KNOWLEDGE_GAP_ANALYSIS)
    void knowledgeGapAnalysis_knowledgeEngineer_domainModelRefactoring() throws IOException {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assumeTrue(l3.isAvailable(),
                "libdatamodelling.so not loaded — skipping live JTBD test "
                    + DataModellingJTBDJob.KNOWLEDGE_GAP_ANALYSIS.name());

            // Step 1: import domain organisation model
            String domainReq = loadFixture("vocabulary-simple");
            String importedDomain = l3.importDomainOrg(domainReq);
            assertPlausible(importedDomain, "domain_id");

            // Step 2: infer knowledge gaps in the domain model
            String gaps = l3.inferKnowledgeGaps(domainReq);
            assertPlausible(gaps, "gaps");

            // Step 3: export the enriched knowledge base
            String knowledgeBase = l3.exportKnowledgeBase(domainReq);
            assertPlausible(knowledgeBase, "content");
        }
    }
}
