//! Ontology-driven MCP tool and A2A skill derivation via Oxigraph SPARQL CONSTRUCT.
//!
//! Flow:
//!   1. Load `yawl-public-roots.ttl` into an in-memory Oxigraph store
//!   2. Execute CONSTRUCT query → materialize mcp:Tool / a2a:Skill triples back into the store
//!   3. SELECT over materialized graph → extract descriptor structs
//!   4. Serialize as JSON for consumption by Java factories via HTTP

use crate::graph::{term_value, ConstructExecutor, Graph};
use serde::Serialize;
use std::collections::HashMap;

// ─── Compile-time resource embedding ──────────────────────────────────────────

const ONTOLOGY_TTL: &str =
    include_str!("../../src/main/resources/ontology/yawl-public-roots.ttl");
const CONSTRUCT_MCP: &str =
    include_str!("../../src/main/resources/sparql/construct-mcp-from-workflow-ontology.sparql");
const CONSTRUCT_A2A: &str =
    include_str!("../../src/main/resources/sparql/construct-a2a-from-workflow-ontology.sparql");

// ─── SELECT queries run against materialized graph ─────────────────────────────

const SELECT_MCP_TOOLS: &str = r#"
PREFIX mcp: <http://yawlfoundation.org/mcp#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
SELECT ?toolNode ?name ?description ?epistemicRoot ?rootDescription ?permission
WHERE {
  ?toolNode a mcp:Tool ;
            mcp:name ?name ;
            mcp:description ?description ;
            mcp:epistemicRoot ?epistemicRoot ;
            mcp:rootDescription ?rootDescription ;
            mcp:permission ?permission .
}
ORDER BY ?name
"#;

const SELECT_MCP_PARAMS: &str = r#"
PREFIX mcp: <http://yawlfoundation.org/mcp#>
SELECT ?toolNode ?paramName ?paramType ?required ?paramDesc
WHERE {
  ?toolNode mcp:hasParameter ?p .
  ?p mcp:paramName ?paramName ;
     mcp:paramType ?paramType ;
     mcp:required ?required .
  OPTIONAL { ?p mcp:paramDescription ?paramDesc }
}
"#;

const SELECT_A2A_SKILLS: &str = r#"
PREFIX a2a: <http://yawlfoundation.org/a2a#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
SELECT ?skillNode ?skillId ?skillName ?description ?capability ?permission ?extendsRoot ?rootLabel
WHERE {
  ?skillNode a a2a:Skill ;
             a2a:skillId ?skillId ;
             a2a:skillName ?skillName ;
             a2a:skillDescription ?description ;
             a2a:capability ?capability ;
             a2a:permission ?permission ;
             a2a:extendsRoot ?extendsRoot ;
             a2a:rootLabel ?rootLabel .
}
ORDER BY ?skillId
"#;

const SELECT_A2A_TAGS: &str = r#"
PREFIX a2a: <http://yawlfoundation.org/a2a#>
SELECT ?skillNode ?tag
WHERE { ?skillNode a2a:tag ?tag . }
"#;

const SELECT_A2A_EXAMPLES: &str = r#"
PREFIX a2a: <http://yawlfoundation.org/a2a#>
SELECT ?skillNode ?example
WHERE { ?skillNode a2a:example ?example . }
"#;

// ─── JSON output descriptors ───────────────────────────────────────────────────

#[derive(Serialize)]
pub struct McpParameterDescriptor {
    pub name: String,
    pub r#type: String,
    pub required: bool,
    pub description: String,
}

#[derive(Serialize)]
pub struct McpToolDescriptor {
    pub name: String,
    pub description: String,
    pub epistemic_root: String,
    pub root_description: String,
    pub permission: String,
    pub parameters: Vec<McpParameterDescriptor>,
}

#[derive(Serialize)]
pub struct A2aSkillDescriptor {
    pub skill_id: String,
    pub skill_name: String,
    pub description: String,
    pub capability: String,
    pub permission: String,
    pub extends_root: String,
    pub root_label: String,
    pub tags: Vec<String>,
    pub examples: Vec<String>,
}

// ─── Public derivation functions ───────────────────────────────────────────────

/// Load yawl-public-roots.ttl, execute CONSTRUCT_MCP, and derive MCP tool descriptors.
pub fn build_mcp_tools() -> Result<Vec<McpToolDescriptor>, String> {
    let graph = Graph::new()?;
    graph.insert_turtle(ONTOLOGY_TTL)?;

    let executor = ConstructExecutor { graph: &graph };
    let count = executor.execute_and_materialize(CONSTRUCT_MCP)?;
    tracing::info!("CONSTRUCT_MCP materialized {} triples", count);

    // Extract all tools
    let tool_rows = graph.query_select(SELECT_MCP_TOOLS)?;

    // Extract parameters, grouped by toolNode IRI
    let param_rows = graph.query_select(SELECT_MCP_PARAMS)?;
    let mut params_by_tool: HashMap<String, Vec<McpParameterDescriptor>> = HashMap::new();
    for row in param_rows {
        let tool_node = row.get("toolNode").map(|s| term_value(s)).unwrap_or_default();
        if tool_node.is_empty() {
            continue;
        }
        let param_name = row.get("paramName").map(|s| term_value(s)).unwrap_or_default();
        let param_type = row
            .get("paramType")
            .map(|s| term_value(s))
            .unwrap_or_else(|| "string".to_string());
        let required = row
            .get("required")
            .map(|s| term_value(s) == "true")
            .unwrap_or(false);
        let description = row
            .get("paramDesc")
            .map(|s| term_value(s))
            .unwrap_or_else(|| param_name.clone());

        params_by_tool
            .entry(tool_node)
            .or_default()
            .push(McpParameterDescriptor {
                name: param_name,
                r#type: param_type,
                required,
                description,
            });
    }

    // Assemble tool descriptors
    let mut tools = Vec::new();
    for row in tool_rows {
        let tool_node = row.get("toolNode").map(|s| term_value(s)).unwrap_or_default();
        let name = row.get("name").map(|s| term_value(s)).unwrap_or_default();
        if name.is_empty() {
            continue;
        }
        let description = row.get("description").map(|s| term_value(s)).unwrap_or_default();
        let epistemic_root = row
            .get("epistemicRoot")
            .map(|s| term_value(s))
            .unwrap_or_default();
        let root_description = row
            .get("rootDescription")
            .map(|s| term_value(s))
            .unwrap_or_default();
        let permission = row
            .get("permission")
            .map(|s| term_value(s))
            .unwrap_or_else(|| "workflow:read".to_string());
        let parameters = params_by_tool.remove(&tool_node).unwrap_or_default();

        tools.push(McpToolDescriptor {
            name,
            description,
            epistemic_root,
            root_description,
            permission,
            parameters,
        });
    }

    tracing::info!("Derived {} MCP tool descriptors from ontology", tools.len());
    Ok(tools)
}

/// Load yawl-public-roots.ttl, execute CONSTRUCT_A2A, and derive A2A skill descriptors.
pub fn build_a2a_skills() -> Result<Vec<A2aSkillDescriptor>, String> {
    let graph = Graph::new()?;
    graph.insert_turtle(ONTOLOGY_TTL)?;

    let executor = ConstructExecutor { graph: &graph };
    let count = executor.execute_and_materialize(CONSTRUCT_A2A)?;
    tracing::info!("CONSTRUCT_A2A materialized {} triples", count);

    // Extract all skills
    let skill_rows = graph.query_select(SELECT_A2A_SKILLS)?;

    // Extract tags grouped by skillNode IRI
    let tag_rows = graph.query_select(SELECT_A2A_TAGS)?;
    let mut tags_by_skill: HashMap<String, Vec<String>> = HashMap::new();
    for row in tag_rows {
        let skill_node = row.get("skillNode").map(|s| term_value(s)).unwrap_or_default();
        let tag = row.get("tag").map(|s| term_value(s)).unwrap_or_default();
        if !skill_node.is_empty() && !tag.is_empty() {
            tags_by_skill.entry(skill_node).or_default().push(tag);
        }
    }

    // Extract examples grouped by skillNode IRI
    let example_rows = graph.query_select(SELECT_A2A_EXAMPLES)?;
    let mut examples_by_skill: HashMap<String, Vec<String>> = HashMap::new();
    for row in example_rows {
        let skill_node = row.get("skillNode").map(|s| term_value(s)).unwrap_or_default();
        let example = row.get("example").map(|s| term_value(s)).unwrap_or_default();
        if !skill_node.is_empty() && !example.is_empty() {
            examples_by_skill.entry(skill_node).or_default().push(example);
        }
    }

    // Assemble skill descriptors
    let mut skills = Vec::new();
    for row in skill_rows {
        let skill_node = row.get("skillNode").map(|s| term_value(s)).unwrap_or_default();
        let skill_id = row.get("skillId").map(|s| term_value(s)).unwrap_or_default();
        if skill_id.is_empty() {
            continue;
        }
        let skill_name = row.get("skillName").map(|s| term_value(s)).unwrap_or_default();
        let description = row.get("description").map(|s| term_value(s)).unwrap_or_default();
        let capability = row.get("capability").map(|s| term_value(s)).unwrap_or_default();
        let permission = row
            .get("permission")
            .map(|s| term_value(s))
            .unwrap_or_else(|| "workflow:read".to_string());
        let extends_root = row
            .get("extendsRoot")
            .map(|s| term_value(s))
            .unwrap_or_default();
        let root_label = row
            .get("rootLabel")
            .map(|s| term_value(s))
            .unwrap_or_default();
        let tags = tags_by_skill.remove(&skill_node).unwrap_or_default();
        let examples = examples_by_skill.remove(&skill_node).unwrap_or_default();

        skills.push(A2aSkillDescriptor {
            skill_id,
            skill_name,
            description,
            capability,
            permission,
            extends_root,
            root_label,
            tags,
            examples,
        });
    }

    tracing::info!("Derived {} A2A skill descriptors from ontology", skills.len());
    Ok(skills)
}
