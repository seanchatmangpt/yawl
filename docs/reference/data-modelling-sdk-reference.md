# Data Modelling SDK Reference

Complete API reference for data-modelling-sdk v2.3.0.

---

## Bridge Lifecycle

| Method | Description |
|-------|-------------|
| `DataModellingBridge()` | Create bridge with pool size 1 |
| `DataModellingBridge(int poolSize)` | Create bridge with custom context pool |
| `close()` | Release all contexts and temp files |

---

## Schema Import (10+ Formats)
| Method | Parameters | Returns |
|-------|------------|---------|
| `parseOdcsYaml(String yaml)` | ODCS v3.1.0 YAML | Workspace JSON |
| `parseOdcsYamlV2(String yaml)` | ODCS v2.x YAML | Workspace JSON |
| `importFromSql(String sql, String dialect)` | SQL DDL, dialect: `postgres`, `mysql`, `sqlite`, `databricks`, `generic` | Workspace JSON |
| `importFromAvro(String avro)` | Avro JSON schema | Workspace JSON |
| `importFromJsonSchema(String schema)` | JSON Schema | Workspace JSON |
| `importFromProtobuf(String proto)` | Protobuf 3 schema | Workspace JSON |
| `importFromCads(String yaml)` | CADS YAML | Workspace JSON |
| `importFromOdps(String yaml)` | ODPS YAML | Workspace JSON |
| `importBpmnModel(String domainId, String xml, String name)` | BPMN 2.0 XML | Domain JSON |
| `importDmnModel(String domainId, String xml, String name)` | DMN 1.3 XML | Domain JSON |
| `importOpenapiSpec(String domainId, String content, String name)` | OpenAPI 3.x (YAML/JSON) | Domain JSON |

---

## Schema Export
| Method | Parameters | Returns |
|-------|------------|---------|
| `exportOdcsYamlV2(String workspaceJson)` | Workspace JSON | ODCS v3.1.0 YAML |
| `exportToSql(String workspaceJson, String dialect)` | Workspace JSON, dialect | SQL DDL |
| `exportBpmnModel(String xml)` | BPMN XML | Normalized BPMN XML |
| `exportDmnModel(String xml)` | DMN XML | Normalized DMN XML |
| `exportOdcsYamlToMarkdown(String yaml)` | ODCS YAML | Markdown documentation |
| `exportOdpsToMarkdown(String productJson)` | ODPS JSON | Markdown documentation |

---

## Format Conversion
| Method | Parameters | Returns |
|-------|------------|---------|
| `convertToOdcs(String input, String format)` | Content, format hint: `sql`, `avro`, `json_schema`, `openapi`, etc. | ODCS JSON |
| `convertOpenapiToOdcs(String openapi, String component, String tableName)` | OpenAPI spec, component name, optional table name | ODCS table JSON |
| `analyzeOpenapiConversion(String openapi, String component)` | OpenAPI spec, component name | Feasibility analysis JSON |
| `migrateDataflowToDomain(String yaml, String domainName)` | DataFlow YAML, optional domain name | Domain JSON |

---

## Workspace Operations
| Method | Parameters | Returns |
|-------|------------|---------|
| `createWorkspace(String name, String ownerId)` | Workspace name, owner email | Workspace JSON |
| `parseWorkspaceYaml(String yaml)` | Workspace YAML | Workspace JSON |
| `addRelationshipToWorkspace(String workspaceJson, String relationshipJson)` | Workspace JSON, relationship JSON | Updated workspace JSON |
| `removeRelationshipFromWorkspace(String workspaceJson, String relationshipId)` | Workspace JSON, relationship ID | Updated workspace JSON |

---

## Domain Operations
| Method | Parameters | Returns |
|-------|------------|---------|
| `createDomain(String name)` | Domain name | Domain JSON |
| `addDomainToWorkspace(String workspaceJson, String domainId, String domainName)` | Workspace JSON, domain ID, name | Updated workspace JSON |
| `removeDomainFromWorkspace(String workspaceJson, String domainId)` | Workspace JSON, domain ID | Updated workspace JSON |
| `addSystemToDomain(String workspaceJson, String domainId, String systemJson)` | Workspace JSON, domain ID, system JSON | Updated workspace JSON |
| `addOdcsNodeToDomain(String workspaceJson, String domainId, String nodeJson)` | Workspace JSON, domain ID, ODCS node JSON | Updated workspace JSON |
| `addCadsNodeToDomain(String workspaceJson, String domainId, String nodeJson)` | Workspace JSON, domain ID, CADS node JSON | Updated workspace JSON |

---

## Decision Records (MADR)
| Method | Parameters | Returns |
|-------|------------|---------|
| `createDecision(int number, String title, String context, String decision, String author)` | Decision number, title, context, decision, author email | Decision JSON |
| `createDecisionIndex()` | — | Empty index JSON |
| `parseDecisionYaml(String yaml)` | Decision YAML | Decision JSON |
| `exportDecisionToYaml(String decisionJson)` | Decision JSON | YAML string |
| `exportDecisionToMarkdown(String decisionJson)` | Decision JSON | MADR Markdown |
| `parseDecisionIndexYaml(String yaml)` | Index YAML | Index JSON |
| `exportDecisionIndexToYaml(String indexJson)` | Index JSON | YAML string |
| `addDecisionToIndex(String indexJson, String decisionJson, String filename)` | Index JSON, decision JSON, filename | Updated index JSON |

---

## Knowledge Base
| Method | Parameters | Returns |
|-------|------------|---------|
| `createKnowledgeArticle(int number, String title, String summary, String content, String author)` | Article number, title, summary, markdown content, author email | Article JSON |
| `createKnowledgeIndex()` | — | Empty index JSON |
| `parseKnowledgeYaml(String yaml)` | Article YAML | Article JSON |
| `exportKnowledgeToYaml(String articleJson)` | Article JSON | YAML string |
| `exportKnowledgeToMarkdown(String articleJson)` | Article JSON | Markdown |
| `parseKnowledgeIndexYaml(String yaml)` | Index YAML | Index JSON |
| `exportKnowledgeIndexToYaml(String indexJson)` | Index JSON | YAML string |
| `addArticleToKnowledgeIndex(String indexJson, String articleJson, String filename)` | Index JSON, article JSON, filename | Updated index JSON |
| `searchKnowledgeArticles(String articlesJson, String query)` | JSON array of articles, search query | JSON array of matches |

---

## Sketches (Excalidraw)
| Method | Parameters | Returns |
|-------|------------|---------|
| `createSketch(int number, String title, String sketchType, String excalidrawData)` | Sketch number, title, type (`architecture`, `workflow`, etc.), Excalidraw JSON | Sketch JSON |
| `createSketchIndex()` | — | Empty index JSON |
| `parseSketchYaml(String yaml)` | Sketch YAML | Sketch JSON |
| `exportSketchToYaml(String sketchJson)` | Sketch JSON | YAML string |
| `parseSketchIndexYaml(String yaml)` | Index YAML | Index JSON |
| `addSketchToIndex(String indexJson, String sketchJson, String filename)` | Index JSON, sketch JSON, filename | Updated index JSON |
| `searchSketches(String sketchesJson, String query)` | JSON array of sketches, search query | JSON array of matches |

---

## Validation
| Method | Parameters | Returns |
|-------|------------|---------|
| `validateOdps(String yaml)` | ODPS YAML | Throws on validation failure |
| `validateTableName(String name)` | Table name | Validation result JSON |
| `validateColumnName(String name)` | Column name | Validation result JSON |
| `validateDataType(String dataType)` | Data type string | Validation result JSON |
| `checkCircularDependency(String relationshipsJson, String sourceId, String targetId)` | Relationships JSON array, source table ID, target table ID | `"true"` or `"false"` |
| `detectNamingConflicts(String existingJson, String newJson)` | Existing tables JSON, new tables JSON | JSON array of conflicts |

---

## Advanced Querying
| Method | Parameters | Returns |
|-------|------------|---------|
| `queryBuilder(String workspaceJson)` | Workspace JSON | `DataModellingQueryBuilder` instance |
| `filterTablesByOwner(String workspaceJson, String owner)` | Workspace JSON, owner email | JSON array of tables |
| `filterTablesByTag(String workspaceJson, String tag)` | Workspace JSON, tag name | JSON array of tables |
| `filterTablesByInfrastructure(String workspaceJson, String type)` | Workspace JSON, infrastructure type | JSON array of tables |
| `filterTablesByMedallionLayer(String workspaceJson, String layer)` | Workspace JSON, layer (`bronze`, `silver`, `gold`) | JSON array of tables |
| `queryTableRelationships(String workspaceJson, String tableId, String type)` | Workspace JSON, table ID, optional relationship type | JSON array of relationships |
| `getImpactAnalysis(String workspaceJson, String tableId)` | Workspace JSON, table ID | JSON array of impacted tables |
| `getDataLineageReport(String workspaceJson, String tableId)` | Workspace JSON, table ID | JSON object with `upstream`, `downstream`, `table` |
| `hasCyclicDependencies(String workspaceJson)` | Workspace JSON | `"true"` or `"false"` |
| `detectCyclePath(String workspaceJson)` | Workspace JSON | JSON array of table IDs forming cycle |

---

## LLM Integration
| Method | Parameters | Returns |
|-------|------------|---------|
| `checkLlmAvailability(LlmConfig config)` | LLM configuration | `"true"` or `"false"` |
| `refineSchemaWithLlmOffline(String schema, String[] samples, String[] objectives, String context, LlmConfig config)` | Schema content, sample data, refinement objectives, context, config | Refined schema JSON |
| `refineSchemaWithLlmOnline(String schema, String[] samples, String[] objectives, String context, LlmConfig config)` | Schema content, sample data, refinement objectives, context, config | Refined schema JSON |
| `matchFieldsWithLlm(String sourceSchema, String targetSchema, LlmConfig config)` | Source schema JSON, target schema JSON, config | Field mappings JSON |
| `enrichDocumentationWithLlm(String schema, LlmConfig config)` | Schema JSON, config | Enriched schema with descriptions |
| `detectPatternsWithLlm(String schema, LlmConfig config)` | Schema JSON, config | Detected patterns JSON |

---

## Pipeline Integration (Throws UnsupportedOperationException)
| Method | Description |
|-------|-------------|
| `inferSchemaFromJson(String jsonData, InferenceConfig config)` | Infer schema from sample JSON data (not yet exposed in SDK) |
| `mapSchemas(String sourceSchema, String targetSchema, MappingConfig config)` | Map fields between schemas (not yet exposed in SDK) |
| `generateTransform(String mappingResultJson, String format)` | Generate SQL/JQ/Python transformation script (not yet exposed in SDK) |

---

## LlmConfig Properties
| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mode` | `Mode.OFFLINE` / `Mode.ONLINE` | `ONLINE` | LLM execution mode |
| `baseUrl` | String | `http://localhost:11434` | Ollama API URL (online mode) |
| `model` | String | `llama3.2` | Model name |
| `temperature` | double | 0.7 | Sampling temperature |
| `maxTokens` | int | 2048 | Maximum response tokens |
| `timeoutSeconds` | int | 60 | Request timeout |

---

## DataModellingException.ErrorKind
| Value | Description |
|-------|-------------|
| `MODULE_LOAD_ERROR` | WASM binary or JS glue not found |
| `EXECUTION_ERROR` | Runtime error in WASM function |

---

## Performance Characteristics
| Operation | Typical Latency | Notes |
|-----------|-----------------|-------|
| Schema import (small) | 2-5ms | < 10 tables |
| Schema import (medium) | 10-30ms | 10-100 tables |
| Schema export | 2-10ms | JSON/YAML serialization |
| LLM refinement (offline) | 5-30s | Depends on model size |
| LLM refinement (online) | 2-10s | Network + inference time |
| Validation | 1-5ms | Schema structure check |
| Query operations | <1ms | In-memory filtering |

---

## Related Documentation
- [Tutorial: Getting Started](../tutorials/data-modelling-sdk-getting-started.md)
- [How-To: Common Tasks](../how-to/data-modelling-sdk-how-to.md)
- [Explanation: SDK Architecture](../explanation/data-modelling-sdk-facade.md)
