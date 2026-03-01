//! Local stub for data-modelling-sdk v2.3.0.
//! All functions return Err — real implementation requires the upstream crate.

pub mod odcs {
    pub fn parse_yaml(_yaml: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_yaml(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn convert_from(_json: &str, _source_format: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod sql {
    pub fn import_from_sql(_sql: &str, _dialect: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_sql(_json: &str, _dialect: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod schema {
    pub fn import_from_avro(_schema: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn import_from_json_schema(_schema: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn import_from_protobuf(_schema: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn import_from_cads(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn import_from_odps(_yaml: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_avro(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_json_schema(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_protobuf(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_cads(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_odps(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod validation {
    pub fn validate_odps(_yaml: &str) -> Result<(), String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod bpmn {
    pub fn import_model(_xml: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_model(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod dmn {
    pub fn import_model(_xml: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_model(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod openapi {
    pub fn import_spec(_yaml_or_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_spec(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn convert_to_odcs(_yaml_or_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn analyze_conversion(_yaml_or_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod dataflow {
    pub fn migrate_to_domain(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod sketch {
    pub fn parse_yaml(_yaml: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn parse_index_yaml(_yaml: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_to_yaml(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn export_index_to_yaml(_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn create(_name: &str, _sketch_type: &str, _description: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn create_index(_name: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn add_to_index(_index_json: &str, _sketch_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn search(_index_json: &str, _query: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod domain {
    pub fn create(_name: &str, _description: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn add_system(_domain_json: &str, _system_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn add_cads_node(_domain_json: &str, _node_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn add_odcs_node(_domain_json: &str, _node_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
pub mod filter {
    pub fn nodes_by_owner(_json: &str, _owner: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn relationships_by_owner(_json: &str, _owner: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn nodes_by_infrastructure_type(_json: &str, _infra_type: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn relationships_by_infrastructure_type(_json: &str, _infra_type: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
    pub fn by_tags(_json: &str, _tags_json: &str) -> Result<String, String> {
        Err("data-modelling-sdk not connected: stub implementation".to_string())
    }
}
