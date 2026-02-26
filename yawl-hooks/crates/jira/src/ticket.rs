use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;

/// JIRA ticket status.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum TicketStatus {
    #[serde(rename = "OPEN")]
    Open,
    #[serde(rename = "IN_PROGRESS")]
    InProgress,
    #[serde(rename = "REVIEW")]
    Review,
    #[serde(rename = "DONE")]
    Done,
}

impl std::fmt::Display for TicketStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TicketStatus::Open => write!(f, "OPEN"),
            TicketStatus::InProgress => write!(f, "IN_PROGRESS"),
            TicketStatus::Review => write!(f, "REVIEW"),
            TicketStatus::Done => write!(f, "DONE"),
        }
    }
}

/// A correction recorded on a ticket from a prior session.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Correction {
    pub timestamp: DateTime<Utc>,
    pub hash: String,
    pub text: String,
    pub rule_added: String,
}

/// Context references: files and docs referenced by this ticket.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ContextRefs {
    #[serde(default)]
    pub files: Vec<String>,
    #[serde(default)]
    pub docs: Vec<String>,
}

/// Dependencies on other tickets.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct DependsOn {
    #[serde(default)]
    pub ids: Vec<String>,
}

/// The inner ticket schema (under `[ticket]` in TOML).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TicketData {
    pub id: String,
    pub title: String,
    pub status: String,
    pub priority: String,
    pub quantum: String,
    pub spr_section: String,
    pub acceptance_criteria: HashMap<String, bool>,
    #[serde(default)]
    pub context_refs: ContextRefs,
    #[serde(default)]
    pub depends_on: DependsOn,
}

/// Top-level TOML ticket file.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TicketFile {
    pub ticket: TicketData,
    #[serde(default)]
    pub corrections: Vec<Correction>,
}

impl TicketFile {
    /// Load a ticket from a TOML file.
    pub fn load(path: &Path) -> anyhow::Result<Self> {
        let content = std::fs::read_to_string(path)?;
        Self::from_toml(&content)
    }

    /// Parse from TOML string.
    pub fn from_toml(content: &str) -> anyhow::Result<Self> {
        let mut value: toml::Value = content.parse().map_err(|e| anyhow::anyhow!("TOML error: {}", e))?;

        // Corrections are at top level as [[ticket.corrections]] or top-level [[corrections]]
        // Handle both forms
        let corrections = if let Some(toml::Value::Array(arr)) = value.get("ticket").and_then(|t| t.get("corrections")) {
            arr.iter()
                .filter_map(|v| toml::Value::try_into::<Correction>(v.clone()).ok())
                .collect()
        } else if let Some(toml::Value::Array(arr)) = value.get("corrections") {
            arr.iter()
                .filter_map(|v| toml::Value::try_into::<Correction>(v.clone()).ok())
                .collect()
        } else {
            vec![]
        };

        // Remove corrections from ticket sub-table if present to avoid deserialization conflict
        if let Some(toml::Value::Table(t)) = value.get_mut("ticket") {
            t.remove("corrections");
        }

        let ticket_data: TicketData = value
            .get("ticket")
            .ok_or_else(|| anyhow::anyhow!("Missing [ticket] section"))?
            .clone()
            .try_into()
            .map_err(|e| anyhow::anyhow!("Ticket parse error: {}", e))?;

        Ok(Self {
            ticket: ticket_data,
            corrections,
        })
    }

    /// Save ticket back to TOML.
    pub fn to_toml(&self) -> anyhow::Result<String> {
        let s = toml::to_string_pretty(self).map_err(|e| anyhow::anyhow!("TOML serialize error: {}", e))?;
        Ok(s)
    }

    /// Mark an acceptance criterion as satisfied.
    pub fn satisfy_criterion(&mut self, ac: &str) -> bool {
        if let Some(val) = self.ticket.acceptance_criteria.get_mut(ac) {
            *val = true;
            return true;
        }
        // Fuzzy match: check if any criterion contains the AC text
        for (key, val) in &mut self.ticket.acceptance_criteria {
            if key.contains(ac) || ac.contains(key.as_str()) {
                *val = true;
                return true;
            }
        }
        false
    }

    /// Check if all acceptance criteria are satisfied.
    pub fn is_done(&self) -> bool {
        self.ticket.acceptance_criteria.values().all(|&v| v)
    }

    /// Returns the count of satisfied criteria.
    pub fn satisfied_count(&self) -> usize {
        self.ticket.acceptance_criteria.values().filter(|&&v| v).count()
    }

    /// Returns total count of criteria.
    pub fn criteria_count(&self) -> usize {
        self.ticket.acceptance_criteria.len()
    }
}
