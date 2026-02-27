use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::hash::{blake3_json, blake3_str};
use crate::types::Delta;

/// A cryptographic receipt for a single write event.
/// Answers: "What typed deltas was Claude shown, when, and for which file transition?"
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeltaReceipt {
    /// Nanosecond-precision timestamp
    pub timestamp_ns: u64,
    /// RFC 3339 human-readable timestamp
    pub timestamp_rfc: DateTime<Utc>,
    /// Claude Code session identifier
    pub session_id: String,
    /// Path of the file that changed
    pub file_path: String,
    /// blake3(old_content)
    pub input_hash: String,
    /// blake3(new_content)
    pub output_hash: String,
    /// blake3(canonical_json(Vec<Delta>))
    pub delta_hash: String,
    /// Whether this delta was injected into Claude's context
    pub injected: bool,
    /// The typed deltas produced
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub deltas: Vec<Delta>,
}

impl DeltaReceipt {
    /// Create a new receipt for a file transition.
    pub fn new(
        session_id: String,
        file_path: String,
        old_content: &str,
        new_content: &str,
        deltas: Vec<Delta>,
        injected: bool,
    ) -> anyhow::Result<Self> {
        let now = Utc::now();
        let timestamp_ns = now
            .timestamp_nanos_opt()
            .unwrap_or_else(|| now.timestamp() * 1_000_000_000) as u64;

        let input_hash = blake3_str(old_content);
        let output_hash = blake3_str(new_content);
        let delta_hash = blake3_json(&deltas)?;

        Ok(Self {
            timestamp_ns,
            timestamp_rfc: now,
            session_id,
            file_path,
            input_hash,
            output_hash,
            delta_hash,
            injected,
            deltas,
        })
    }

    /// Verify this receipt is self-consistent (delta_hash matches the deltas).
    pub fn verify(&self) -> anyhow::Result<bool> {
        let expected = blake3_json(&self.deltas)?;
        Ok(expected == self.delta_hash)
    }
}

/// A session-level receipt aggregating all delta receipts.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionReceipt {
    pub session_id: String,
    pub started_at: DateTime<Utc>,
    pub ended_at: Option<DateTime<Utc>>,
    pub receipt_count: usize,
    /// blake3 of all individual receipt delta_hashes concatenated
    pub chain_hash: String,
    pub receipts: Vec<DeltaReceipt>,
}

impl SessionReceipt {
    pub fn new(session_id: String) -> Self {
        Self {
            session_id,
            started_at: Utc::now(),
            ended_at: None,
            receipt_count: 0,
            chain_hash: String::new(),
            receipts: Vec::new(),
        }
    }

    /// Add a receipt and recompute the chain hash.
    pub fn push(&mut self, receipt: DeltaReceipt) -> anyhow::Result<()> {
        self.receipts.push(receipt);
        self.receipt_count = self.receipts.len();
        self.recompute_chain()?;
        Ok(())
    }

    fn recompute_chain(&mut self) -> anyhow::Result<()> {
        let concatenated: String = self
            .receipts
            .iter()
            .map(|r| r.delta_hash.as_str())
            .collect::<Vec<_>>()
            .join("");
        self.chain_hash = blake3_str(&concatenated);
        Ok(())
    }

    pub fn close(&mut self) {
        self.ended_at = Some(Utc::now());
    }
}
