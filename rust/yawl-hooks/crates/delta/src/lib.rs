use serde::{Deserialize, Serialize};

/// Change type within a delta
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum Change {
    Added,
    Removed,
    #[serde(rename = "Modified")]
    Modified { from: String, to: String },
}

/// Kind of declaration
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum DeclKind {
    Function,
    Type,
    Constant,
    Import,
    Module,
    Field,
}

/// Core delta type — semantic fact transition
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind")]
pub enum Delta {
    #[serde(rename = "declaration")]
    Declaration {
        #[serde(rename = "decl_kind")]
        kind: DeclKind,
        name: String,
        change: Change,
    },
    #[serde(rename = "rule")]
    Rule {
        section: String,
        predicate: String,
        change: Change,
    },
    #[serde(rename = "criterion")]
    Criterion {
        ticket_id: String,
        ac: String,
        change: Change,
    },
    #[serde(rename = "dependency")]
    Dependency {
        name: String,
        from: String,
        to: String,
        breaking: bool,
    },
    #[serde(rename = "behavior")]
    Behavior {
        component: String,
        old: String,
        new: String,
        breaking: bool,
    },
    #[serde(rename = "quad")]
    Quad {
        adds: Vec<String>,
        removes: Vec<String>,
    },
}

/// Serializable delta list
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct DeltaList {
    pub deltas: Vec<Delta>,
}

impl DeltaList {
    pub fn new() -> Self {
        Self { deltas: Vec::new() }
    }

    pub fn add(&mut self, delta: Delta) {
        self.deltas.push(delta);
    }

    pub fn canonical_json(&self) -> Result<String, serde_json::Error> {
        let json = serde_json::to_string(&self.deltas)?;
        Ok(json)
    }

    pub fn hash(&self) -> Result<String, Box<dyn std::error::Error>> {
        let json = self.canonical_json()?;
        let hash = blake3::hash(json.as_bytes());
        Ok(hash.to_hex().to_string())
    }
}

/// Receipt for delta production
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GuardReceipt {
    pub timestamp_ns: u64,
    pub session_id: String,
    pub file_path: String,
    pub input_hash: String,
    pub output_hash: String,
    pub delta_hash: String,
    pub injected: bool,
    pub delta_count: usize,
}

impl GuardReceipt {
    pub fn new(
        session_id: String,
        file_path: String,
        input_hash: String,
        output_hash: String,
        delta_hash: String,
        delta_count: usize,
    ) -> Self {
        Self {
            timestamp_ns: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos() as u64,
            session_id,
            file_path,
            input_hash,
            output_hash,
            delta_hash,
            injected: false,
            delta_count,
        }
    }

    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string_pretty(self)
    }
}

/// Hash wrapper for file contents
pub fn hash_content(content: &[u8]) -> String {
    blake3::hash(content).to_hex().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_delta_serialization() {
        let delta = Delta::Declaration {
            kind: DeclKind::Function,
            name: "test_fn".to_string(),
            change: Change::Added,
        };
        let json = serde_json::to_string(&delta).unwrap();
        assert!(json.contains("test_fn"));
    }

    #[test]
    fn test_delta_list_hash() {
        let mut deltas = DeltaList::new();
        deltas.add(Delta::Declaration {
            kind: DeclKind::Function,
            name: "foo".to_string(),
            change: Change::Added,
        });
        let hash = deltas.hash().unwrap();
        assert_eq!(hash.len(), 64); // blake3 hex = 64 chars
    }

    #[test]
    fn test_guard_receipt() {
        let receipt = GuardReceipt::new(
            "session-123".to_string(),
            "src/test.java".to_string(),
            "abc".to_string(),
            "def".to_string(),
            "ghi".to_string(),
            1,
        );
        let json = receipt.to_json().unwrap();
        assert!(json.contains("session-123"));
    }
}
