package org.yawlfoundation.yawl.datamodelling.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.datamodelling.Capability;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;
import org.yawlfoundation.yawl.datamodelling.MapsToCapability;
import org.yawlfoundation.yawl.datamodelling.OdpsValidationException;
import org.yawlfoundation.yawl.datamodelling.bridge.DataModellingBridge;
import org.yawlfoundation.yawl.datamodelling.model.*;

import java.util.List;

import static org.yawlfoundation.yawl.datamodelling.Capability.*;

/**
 * Layer 3 service implementation. Delegates to {@link DataModellingBridge} (Layer 2)
 * with JSON encode/decode for typed domain objects.
 */
public final class DataModellingServiceImpl implements DataModellingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DataModellingBridge bridge;

    public DataModellingServiceImpl(DataModellingBridge bridge) {
        this.bridge = bridge;
    }

    private <T> T decode(String json, Class<T> type) {
        return Json.decode(json, type);
    }

    private <T> T decodeList(String json, TypeReference<T> ref) {
        try { return MAPPER.readValue(json, ref); }
        catch (Exception e) { throw new DataModellingException("JSON decode failed: " + e.getMessage(), e); }
    }

    // Group A
    @MapsToCapability(PARSE_ODCS_YAML)
    @Override public WorkspaceModel parseOdcsYaml(String yaml) {
        return decode(bridge.parseOdcsYaml(yaml), WorkspaceModel.class);
    }

    @MapsToCapability(EXPORT_TO_ODCS_YAML)
    @Override public String exportToOdcsYaml(WorkspaceModel model) {
        return bridge.exportToOdcsYaml(Json.encode(model));
    }

    @MapsToCapability(CONVERT_TO_ODCS)
    @Override public WorkspaceModel convertToOdcs(String json, String sourceFormat) {
        return decode(bridge.convertToOdcs(json, sourceFormat), WorkspaceModel.class);
    }

    // Group B
    @MapsToCapability(IMPORT_FROM_SQL)
    @Override public WorkspaceModel importFromSql(String sql, SqlDialect dialect) {
        return decode(bridge.importFromSql(sql, dialect.name()), WorkspaceModel.class);
    }

    @MapsToCapability(EXPORT_TO_SQL)
    @Override public String exportToSql(WorkspaceModel model, SqlDialect dialect) {
        return bridge.exportToSql(Json.encode(model), dialect.name());
    }

    // Group C
    @MapsToCapability(IMPORT_FROM_AVRO)
    @Override public WorkspaceModel importFromAvro(String schema) {
        return decode(bridge.importFromAvro(schema), WorkspaceModel.class);
    }

    @MapsToCapability(IMPORT_FROM_JSON_SCHEMA)
    @Override public WorkspaceModel importFromJsonSchema(String schema) {
        return decode(bridge.importFromJsonSchema(schema), WorkspaceModel.class);
    }

    @MapsToCapability(IMPORT_FROM_PROTOBUF)
    @Override public WorkspaceModel importFromProtobuf(String schema) {
        return decode(bridge.importFromProtobuf(schema), WorkspaceModel.class);
    }

    @MapsToCapability(IMPORT_FROM_CADS)
    @Override public WorkspaceModel importFromCads(String json) {
        return decode(bridge.importFromCads(json), WorkspaceModel.class);
    }

    @MapsToCapability(IMPORT_FROM_ODPS)
    @Override public WorkspaceModel importFromOdps(String yaml) {
        return decode(bridge.importFromOdps(yaml), WorkspaceModel.class);
    }

    // Group D
    @MapsToCapability(EXPORT_TO_AVRO)
    @Override public String exportToAvro(WorkspaceModel model) {
        return bridge.exportToAvro(Json.encode(model));
    }

    @MapsToCapability(EXPORT_TO_JSON_SCHEMA)
    @Override public String exportToJsonSchema(WorkspaceModel model) {
        return bridge.exportToJsonSchema(Json.encode(model));
    }

    @MapsToCapability(EXPORT_TO_PROTOBUF)
    @Override public String exportToProtobuf(WorkspaceModel model) {
        return bridge.exportToProtobuf(Json.encode(model));
    }

    @MapsToCapability(EXPORT_TO_CADS)
    @Override public String exportToCads(WorkspaceModel model) {
        return bridge.exportToCads(Json.encode(model));
    }

    @MapsToCapability(EXPORT_TO_ODPS)
    @Override public String exportToOdps(WorkspaceModel model) {
        return bridge.exportToOdps(Json.encode(model));
    }

    // Group E
    @MapsToCapability(VALIDATE_ODPS)
    @Override public void validateOdps(String yaml) {
        bridge.validateOdps(yaml);
    }

    // Group F
    @MapsToCapability(IMPORT_BPMN_MODEL)
    @Override public WorkspaceModel importBpmnModel(String xml) {
        return decode(bridge.importBpmnModel(xml), WorkspaceModel.class);
    }

    @MapsToCapability(EXPORT_BPMN_MODEL)
    @Override public String exportBpmnModel(WorkspaceModel model) {
        return bridge.exportBpmnModel(Json.encode(model));
    }

    // Group G
    @MapsToCapability(IMPORT_DMN_MODEL)
    @Override public WorkspaceModel importDmnModel(String xml) {
        return decode(bridge.importDmnModel(xml), WorkspaceModel.class);
    }

    @MapsToCapability(EXPORT_DMN_MODEL)
    @Override public String exportDmnModel(WorkspaceModel model) {
        return bridge.exportDmnModel(Json.encode(model));
    }

    // Group H
    @MapsToCapability(IMPORT_OPENAPI_SPEC)
    @Override public WorkspaceModel importOpenapiSpec(String yamlOrJson) {
        return decode(bridge.importOpenapiSpec(yamlOrJson), WorkspaceModel.class);
    }

    @MapsToCapability(EXPORT_OPENAPI_SPEC)
    @Override public String exportOpenapiSpec(WorkspaceModel model) {
        return bridge.exportOpenapiSpec(Json.encode(model));
    }

    @MapsToCapability(CONVERT_OPENAPI_TO_ODCS)
    @Override public WorkspaceModel convertOpenapiToOdcs(String yamlOrJson) {
        return decode(bridge.convertOpenapiToOdcs(yamlOrJson), WorkspaceModel.class);
    }

    @MapsToCapability(ANALYZE_OPENAPI_CONVERSION)
    @Override public OpenApiConversionAnalysis analyzeOpenapiConversion(String yamlOrJson) {
        return decode(bridge.analyzeOpenapiConversion(yamlOrJson), OpenApiConversionAnalysis.class);
    }

    // Group I
    @MapsToCapability(MIGRATE_DATAFLOW_TO_DOMAIN)
    @Override public BusinessDomain migrateDataflowToDomain(String json) {
        return decode(bridge.migrateDataflowToDomain(json), BusinessDomain.class);
    }

    // Group J
    @MapsToCapability(PARSE_SKETCH_YAML)
    @Override public Sketch parseSketchYaml(String yaml) {
        return decode(bridge.parseSketchYaml(yaml), Sketch.class);
    }

    @MapsToCapability(PARSE_SKETCH_INDEX_YAML)
    @Override public SketchIndex parseSketchIndexYaml(String yaml) {
        return decode(bridge.parseSketchIndexYaml(yaml), SketchIndex.class);
    }

    @MapsToCapability(EXPORT_SKETCH_TO_YAML)
    @Override public String exportSketchToYaml(Sketch sketch) {
        return bridge.exportSketchToYaml(Json.encode(sketch));
    }

    @MapsToCapability(EXPORT_SKETCH_INDEX_TO_YAML)
    @Override public String exportSketchIndexToYaml(SketchIndex index) {
        return bridge.exportSketchIndexToYaml(Json.encode(index));
    }

    @MapsToCapability(CREATE_SKETCH)
    @Override public Sketch createSketch(String name, String sketchType, String description) {
        return decode(bridge.createSketch(name, sketchType, description), Sketch.class);
    }

    @MapsToCapability(CREATE_SKETCH_INDEX)
    @Override public SketchIndex createSketchIndex(String name) {
        return decode(bridge.createSketchIndex(name), SketchIndex.class);
    }

    @MapsToCapability(ADD_SKETCH_TO_INDEX)
    @Override public SketchIndex addSketchToIndex(SketchIndex index, Sketch sketch) {
        return decode(bridge.addSketchToIndex(Json.encode(index), Json.encode(sketch)), SketchIndex.class);
    }

    @MapsToCapability(SEARCH_SKETCHES)
    @Override public List<Sketch> searchSketches(SketchIndex index, String query) {
        return decodeList(bridge.searchSketches(Json.encode(index), query),
            new TypeReference<List<Sketch>>() {});
    }

    // Group K
    @MapsToCapability(CREATE_DOMAIN)
    @Override public BusinessDomain createDomain(String name, String description) {
        return decode(bridge.createDomain(name, description), BusinessDomain.class);
    }

    @MapsToCapability(ADD_SYSTEM_TO_DOMAIN)
    @Override public BusinessDomain addSystemToDomain(BusinessDomain domain, SystemDefinition system) {
        return decode(bridge.addSystemToDomain(Json.encode(domain), Json.encode(system)), BusinessDomain.class);
    }

    @MapsToCapability(ADD_CADS_NODE_TO_DOMAIN)
    @Override public BusinessDomain addCadsNodeToDomain(BusinessDomain domain, CadsNode node) {
        return decode(bridge.addCadsNodeToDomain(Json.encode(domain), Json.encode(node)), BusinessDomain.class);
    }

    @MapsToCapability(ADD_ODCS_NODE_TO_DOMAIN)
    @Override public BusinessDomain addOdcsNodeToDomain(BusinessDomain domain, OdcsNode node) {
        return decode(bridge.addOdcsNodeToDomain(Json.encode(domain), Json.encode(node)), BusinessDomain.class);
    }

    // Group L
    @MapsToCapability(FILTER_NODES_BY_OWNER)
    @Override public List<CadsNode> filterNodesByOwner(List<CadsNode> nodes, String owner) {
        return decodeList(bridge.filterNodesByOwner(Json.encode(nodes), owner),
            new TypeReference<List<CadsNode>>() {});
    }

    @MapsToCapability(FILTER_RELATIONSHIPS_BY_OWNER)
    @Override public List<String> filterRelationshipsByOwner(String json, String owner) {
        return decodeList(bridge.filterRelationshipsByOwner(json, owner),
            new TypeReference<List<String>>() {});
    }

    @MapsToCapability(FILTER_NODES_BY_INFRASTRUCTURE_TYPE)
    @Override public List<CadsNode> filterNodesByInfrastructureType(List<CadsNode> nodes, String infraType) {
        return decodeList(bridge.filterNodesByInfrastructureType(Json.encode(nodes), infraType),
            new TypeReference<List<CadsNode>>() {});
    }

    @MapsToCapability(FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE)
    @Override public List<String> filterRelationshipsByInfrastructureType(String json, String infraType) {
        return decodeList(bridge.filterRelationshipsByInfrastructureType(json, infraType),
            new TypeReference<List<String>>() {});
    }

    @MapsToCapability(FILTER_BY_TAGS)
    @Override public List<CadsNode> filterByTags(List<CadsNode> nodes, List<String> tags) {
        return decodeList(bridge.filterByTags(Json.encode(nodes), Json.encode(tags)),
            new TypeReference<List<CadsNode>>() {});
    }

    @Override public void close() {
        bridge.close();
    }
}
