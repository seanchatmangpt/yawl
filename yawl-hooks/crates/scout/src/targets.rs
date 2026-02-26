use serde::{Deserialize, Serialize};
use std::path::Path;

/// A fetch target declaration from targets.toml.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Target {
    pub name: String,
    pub url: String,
    pub semantic_unit: String,
    pub watermark_ttl_hours: u64,
}

/// The top-level targets.toml structure.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TargetsConfig {
    pub target: Vec<Target>,
}

impl TargetsConfig {
    /// Load targets from a TOML file.
    pub fn load(path: &Path) -> anyhow::Result<Self> {
        let content = std::fs::read_to_string(path)?;
        Self::from_toml(&content)
    }

    /// Parse from TOML string.
    pub fn from_toml(content: &str) -> anyhow::Result<Self> {
        let config: TargetsConfig = toml::from_str(content)
            .map_err(|e| anyhow::anyhow!("targets.toml parse error: {}", e))?;
        Ok(config)
    }
}
