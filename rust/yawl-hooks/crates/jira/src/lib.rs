use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

/// Acceptance criterion for ticket
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AcceptanceCriterion {
    pub text: String,
    pub satisfied: bool,
}

/// Correction entry on ticket
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Correction {
    pub timestamp: String,
    pub hash: String,
    pub text: String,
    pub rule_added: Option<String>,
}

/// JIRA Ticket stored in TOML
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Ticket {
    pub id: String,
    pub title: String,
    pub status: String, // OPEN | IN_PROGRESS | REVIEW | DONE
    pub priority: String,
    pub quantum: String,
    pub spr_section: String,

    #[serde(default)]
    pub acceptance_criteria: BTreeMap<String, bool>,

    #[serde(default)]
    pub context_refs: ContextRefs,

    #[serde(default)]
    pub depends_on: DependsOn,

    #[serde(default)]
    pub corrections: Vec<Correction>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct ContextRefs {
    pub files: Vec<String>,
    pub docs: Vec<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct DependsOn {
    pub ids: Vec<String>,
}

impl Ticket {
    /// Load ticket from TOML file
    pub fn from_file(path: &Path) -> Result<Self, Box<dyn std::error::Error>> {
        let content = fs::read_to_string(path)?;
        let table: toml::Table = toml::from_str(&content)?;

        let ticket_table = table
            .get("ticket")
            .and_then(|v| v.as_table())
            .ok_or("Missing [ticket] section")?;

        let id = ticket_table
            .get("id")
            .and_then(|v| v.as_str())
            .ok_or("Missing ticket.id")?
            .to_string();

        let title = ticket_table
            .get("title")
            .and_then(|v| v.as_str())
            .ok_or("Missing ticket.title")?
            .to_string();

        let status = ticket_table
            .get("status")
            .and_then(|v| v.as_str())
            .unwrap_or("OPEN")
            .to_string();

        let priority = ticket_table
            .get("priority")
            .and_then(|v| v.as_str())
            .unwrap_or("P2")
            .to_string();

        let quantum = ticket_table
            .get("quantum")
            .and_then(|v| v.as_str())
            .unwrap_or("general")
            .to_string();

        let spr_section = ticket_table
            .get("spr_section")
            .and_then(|v| v.as_str())
            .unwrap_or("general")
            .to_string();

        // Parse acceptance criteria
        let mut acceptance_criteria = BTreeMap::new();
        if let Some(ac_table) = ticket_table
            .get("acceptance_criteria")
            .and_then(|v| v.as_table())
        {
            for (key, val) in ac_table {
                if let Some(b) = val.as_bool() {
                    acceptance_criteria.insert(key.clone(), b);
                }
            }
        }

        // Parse context refs
        let mut context_refs = ContextRefs::default();
        if let Some(ctx) = ticket_table.get("context_refs").and_then(|v| v.as_table()) {
            if let Some(files) = ctx.get("files").and_then(|v| v.as_array()) {
                context_refs.files = files
                    .iter()
                    .filter_map(|v| v.as_str().map(|s| s.to_string()))
                    .collect();
            }
            if let Some(docs) = ctx.get("docs").and_then(|v| v.as_array()) {
                context_refs.docs = docs
                    .iter()
                    .filter_map(|v| v.as_str().map(|s| s.to_string()))
                    .collect();
            }
        }

        // Parse depends_on
        let mut depends_on = DependsOn::default();
        if let Some(dep) = ticket_table.get("depends_on").and_then(|v| v.as_table()) {
            if let Some(ids) = dep.get("ids").and_then(|v| v.as_array()) {
                depends_on.ids = ids
                    .iter()
                    .filter_map(|v| v.as_str().map(|s| s.to_string()))
                    .collect();
            }
        }

        // Parse corrections
        let mut corrections = Vec::new();
        if let Some(corr_array) = table.get("ticket").and_then(|v| v.get("corrections")).and_then(|v| v.as_array()) {
            for item in corr_array {
                if let Some(obj) = item.as_table() {
                    let timestamp = obj
                        .get("timestamp")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string();
                    let hash = obj
                        .get("hash")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string();
                    let text = obj
                        .get("text")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string();
                    let rule_added = obj
                        .get("rule_added")
                        .and_then(|v| v.as_str())
                        .map(|s| s.to_string());

                    corrections.push(Correction {
                        timestamp,
                        hash,
                        text,
                        rule_added,
                    });
                }
            }
        }

        Ok(Ticket {
            id,
            title,
            status,
            priority,
            quantum,
            spr_section,
            acceptance_criteria,
            context_refs,
            depends_on,
            corrections,
        })
    }

    /// Format ticket as markdown for injection
    pub fn to_markdown(&self) -> String {
        let mut md = format!(
            "## Active Ticket: {}\n**Title**: {}\n**Priority**: {} | **Quantum**: {}\n\n",
            self.id, self.title, self.priority, self.quantum
        );

        md.push_str("### Acceptance Criteria\n");
        for (criterion, satisfied) in &self.acceptance_criteria {
            let check = if *satisfied { "✓" } else { " " };
            md.push_str(&format!("- [{}] {}\n", check, criterion));
        }

        if !self.context_refs.files.is_empty() {
            md.push_str("\n### Context\n");
            for file in &self.context_refs.files {
                md.push_str(&format!("- `{}`\n", file));
            }
        }

        if !self.depends_on.ids.is_empty() {
            md.push_str("\n### Dependencies\n");
            for id in &self.depends_on.ids {
                md.push_str(&format!("- {}\n", id));
            }
        }

        if !self.corrections.is_empty() {
            md.push_str("\n### Corrections on Record\n");
            for corr in &self.corrections {
                md.push_str(&format!("[{}] {}", corr.timestamp, corr.text));
                if let Some(rule) = &corr.rule_added {
                    md.push_str(&format!("\n  Rule: {}", rule));
                }
                md.push('\n');
            }
        }

        md
    }

    /// Check if criterion is satisfied
    pub fn is_criterion_satisfied(&self, criterion_text: &str) -> bool {
        self.acceptance_criteria
            .iter()
            .find(|(k, _)| k.contains(criterion_text))
            .map(|(_, v)| *v)
            .unwrap_or(false)
    }

    /// Mark criterion as satisfied
    pub fn mark_criterion_satisfied(&mut self, criterion_text: &str) {
        for key in self.acceptance_criteria.keys() {
            if key.contains(criterion_text) {
                self.acceptance_criteria.insert(key.clone(), true);
                break;
            }
        }
    }

    /// Save ticket back to TOML file
    pub fn save_to_file(&self, path: &Path) -> Result<(), Box<dyn std::error::Error>> {
        // Convert to TOML format
        let mut content = String::from("[ticket]\n");
        content.push_str(&format!("id = \"{}\"\n", self.id));
        content.push_str(&format!("title = \"{}\"\n", self.title));
        content.push_str(&format!("status = \"{}\"\n", self.status));
        content.push_str(&format!("priority = \"{}\"\n", self.priority));
        content.push_str(&format!("quantum = \"{}\"\n", self.quantum));
        content.push_str(&format!("spr_section = \"{}\"\n", self.spr_section));

        // Write acceptance criteria
        if !self.acceptance_criteria.is_empty() {
            content.push_str("\n[ticket.acceptance_criteria]\n");
            for (crit, satisfied) in &self.acceptance_criteria {
                content.push_str(&format!("\"{}\" = {}\n", crit.replace("\"", "\\\""), satisfied));
            }
        }

        // Write context refs
        if !self.context_refs.files.is_empty() || !self.context_refs.docs.is_empty() {
            content.push_str("\n[ticket.context_refs]\n");
            if !self.context_refs.files.is_empty() {
                content.push_str("files = [\n");
                for file in &self.context_refs.files {
                    content.push_str(&format!("    \"{}\",\n", file));
                }
                content.push_str("]\n");
            }
            if !self.context_refs.docs.is_empty() {
                content.push_str("docs = [\n");
                for doc in &self.context_refs.docs {
                    content.push_str(&format!("    \"{}\",\n", doc));
                }
                content.push_str("]\n");
            }
        }

        // Write depends_on
        if !self.depends_on.ids.is_empty() {
            content.push_str("\n[ticket.depends_on]\n");
            content.push_str("ids = [\n");
            for id in &self.depends_on.ids {
                content.push_str(&format!("    \"{}\",\n", id));
            }
            content.push_str("]\n");
        }

        // Write corrections
        if !self.corrections.is_empty() {
            content.push_str("\n[[ticket.corrections]]\n");
            for (i, corr) in self.corrections.iter().enumerate() {
                if i > 0 {
                    content.push_str("\n[[ticket.corrections]]\n");
                }
                content.push_str(&format!("timestamp = \"{}\"\n", corr.timestamp));
                content.push_str(&format!("hash = \"{}\"\n", corr.hash));
                content.push_str(&format!("text = \"{}\"\n", corr.text.replace("\"", "\\\"")));
                if let Some(rule) = &corr.rule_added {
                    content.push_str(&format!("rule_added = \"{}\"\n", rule.replace("\"", "\\\"")));
                }
            }
        }

        fs::write(path, content)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ticket_markdown() {
        let mut ticket = Ticket {
            id: "YAWL-247".to_string(),
            title: "Test".to_string(),
            status: "IN_PROGRESS".to_string(),
            priority: "P1".to_string(),
            quantum: "Engine".to_string(),
            spr_section: "φ".to_string(),
            acceptance_criteria: BTreeMap::new(),
            context_refs: ContextRefs::default(),
            depends_on: DependsOn::default(),
            corrections: Vec::new(),
        };
        ticket.acceptance_criteria.insert("test criterion".to_string(), false);

        let md = ticket.to_markdown();
        assert!(md.contains("YAWL-247"));
        assert!(md.contains("test criterion"));
    }
}
