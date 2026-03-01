package org.yawlfoundation.yawl.datamodelling.api;

import org.yawlfoundation.yawl.datamodelling.OdpsValidationException;
import org.yawlfoundation.yawl.datamodelling.model.*;

import java.util.List;

/**
 * Layer 3 service interface for data-modelling operations.
 * Obtain an instance via {@link org.yawlfoundation.yawl.datamodelling.DataModellingModule#create()}.
 *
 * <p>All methods throw {@link org.yawlfoundation.yawl.datamodelling.DataModellingException}
 * (unchecked) on native error, or {@link UnsupportedOperationException} if the native library
 * is not loaded.
 */
public interface DataModellingService extends AutoCloseable {

    // Group A — ODCS Core
    WorkspaceModel parseOdcsYaml(String yaml);
    String exportToOdcsYaml(WorkspaceModel model);
    WorkspaceModel convertToOdcs(String json, String sourceFormat);

    // Group B — SQL
    WorkspaceModel importFromSql(String sql, SqlDialect dialect);
    String exportToSql(WorkspaceModel model, SqlDialect dialect);

    // Group C — Schema Format Import
    WorkspaceModel importFromAvro(String schema);
    WorkspaceModel importFromJsonSchema(String schema);
    WorkspaceModel importFromProtobuf(String schema);
    WorkspaceModel importFromCads(String json);
    WorkspaceModel importFromOdps(String yaml);

    // Group D — Schema Format Export
    String exportToAvro(WorkspaceModel model);
    String exportToJsonSchema(WorkspaceModel model);
    String exportToProtobuf(WorkspaceModel model);
    String exportToCads(WorkspaceModel model);
    String exportToOdps(WorkspaceModel model);

    // Group E — ODPS Validation
    /** @throws OdpsValidationException if the ODPS YAML is invalid */
    void validateOdps(String yaml);

    // Group F — BPMN
    WorkspaceModel importBpmnModel(String xml);
    String exportBpmnModel(WorkspaceModel model);

    // Group G — DMN
    WorkspaceModel importDmnModel(String xml);
    String exportDmnModel(WorkspaceModel model);

    // Group H — OpenAPI
    WorkspaceModel importOpenapiSpec(String yamlOrJson);
    String exportOpenapiSpec(WorkspaceModel model);
    WorkspaceModel convertOpenapiToOdcs(String yamlOrJson);
    OpenApiConversionAnalysis analyzeOpenapiConversion(String yamlOrJson);

    // Group I — DataFlow
    BusinessDomain migrateDataflowToDomain(String json);

    // Group J — Sketch
    Sketch parseSketchYaml(String yaml);
    SketchIndex parseSketchIndexYaml(String yaml);
    String exportSketchToYaml(Sketch sketch);
    String exportSketchIndexToYaml(SketchIndex index);
    Sketch createSketch(String name, String sketchType, String description);
    SketchIndex createSketchIndex(String name);
    SketchIndex addSketchToIndex(SketchIndex index, Sketch sketch);
    List<Sketch> searchSketches(SketchIndex index, String query);

    // Group K — Domain
    BusinessDomain createDomain(String name, String description);
    BusinessDomain addSystemToDomain(BusinessDomain domain, SystemDefinition system);
    BusinessDomain addCadsNodeToDomain(BusinessDomain domain, CadsNode node);
    BusinessDomain addOdcsNodeToDomain(BusinessDomain domain, OdcsNode node);

    // Group L — Filter
    List<CadsNode> filterNodesByOwner(List<CadsNode> nodes, String owner);
    List<String> filterRelationshipsByOwner(String json, String owner);
    List<CadsNode> filterNodesByInfrastructureType(List<CadsNode> nodes, String infraType);
    List<String> filterRelationshipsByInfrastructureType(String json, String infraType);
    List<CadsNode> filterByTags(List<CadsNode> nodes, List<String> tags);

    @Override void close();
}
