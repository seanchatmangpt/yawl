use std::path::{Path, PathBuf};

use crate::ticket::TicketFile;

/// Output from the inject command — the context string to inject into Claude.
pub struct InjectionOutput {
    pub context: String,
    pub ticket_id: String,
}

/// Generate the session-start injection for the active ticket.
/// Reads `.claude/context/active-ticket.toml` (or the first IN_PROGRESS ticket in .claude/jira/tickets/).
pub fn inject_session(repo_root: &Path) -> anyhow::Result<InjectionOutput> {
    let ticket = load_active_ticket(repo_root)?;
    let context = format_session_context(&ticket, repo_root);

    Ok(InjectionOutput {
        context,
        ticket_id: ticket.ticket.id.clone(),
    })
}

/// Generate the prompt-level injection for the given prompt text.
/// Only injects the relevant slice of the ticket context.
pub fn inject_prompt(repo_root: &Path, prompt: &str) -> anyhow::Result<InjectionOutput> {
    let ticket = load_active_ticket(repo_root)?;
    let context = format_prompt_context(&ticket, repo_root, prompt);

    Ok(InjectionOutput {
        context,
        ticket_id: ticket.ticket.id.clone(),
    })
}

fn load_active_ticket(repo_root: &Path) -> anyhow::Result<TicketFile> {
    // First try active-ticket.toml
    let active_path = repo_root.join(".claude").join("context").join("active-ticket.toml");
    if active_path.exists() {
        return TicketFile::load(&active_path);
    }

    // Fall back to first IN_PROGRESS ticket in .claude/jira/tickets/
    let tickets_dir = repo_root.join(".claude").join("jira").join("tickets");
    if tickets_dir.exists() {
        let mut entries: Vec<PathBuf> = std::fs::read_dir(&tickets_dir)?
            .filter_map(|e| e.ok())
            .filter(|e| e.path().extension().is_some_and(|ext| ext == "toml"))
            .map(|e| e.path())
            .collect();
        entries.sort();

        for path in entries {
            if let Ok(ticket) = TicketFile::load(&path) {
                if ticket.ticket.status.to_uppercase().contains("IN_PROGRESS") {
                    return Ok(ticket);
                }
            }
        }

        // If no IN_PROGRESS, return the first ticket
        if let Ok(entries2) = std::fs::read_dir(&tickets_dir) {
            let mut paths: Vec<PathBuf> = entries2
                .filter_map(|e| e.ok())
                .filter(|e| e.path().extension().is_some_and(|ext| ext == "toml"))
                .map(|e| e.path())
                .collect();
            paths.sort();
            if let Some(path) = paths.first() {
                return TicketFile::load(path);
            }
        }
    }

    Err(anyhow::anyhow!(
        "No active ticket found. Create .claude/context/active-ticket.toml or .claude/jira/tickets/*.toml"
    ))
}

fn format_session_context(ticket: &TicketFile, repo_root: &Path) -> String {
    let t = &ticket.ticket;
    let mut out = String::new();

    out.push_str(&format!("## Active Ticket: {}\n", t.id));
    out.push_str(&format!("**Title**: {}\n", t.title));
    out.push_str(&format!(
        "**Priority**: {} | **Quantum**: {}\n\n",
        t.priority, t.quantum
    ));

    out.push_str("### Acceptance Criteria\n");
    for (ac, done) in &t.acceptance_criteria {
        let mark = if *done { "x" } else { " " };
        out.push_str(&format!("- [{}] {}\n", mark, ac));
    }
    out.push('\n');

    if !t.context_refs.files.is_empty() || !t.context_refs.docs.is_empty() {
        out.push_str("### Context\n");
        for file in &t.context_refs.files {
            out.push_str(&format!("- `{}`\n", file));
        }
        for doc in &t.context_refs.docs {
            out.push_str(&format!("- {}\n", doc));
        }
        if !t.depends_on.ids.is_empty() {
            out.push_str(&format!("- Depends on: {}\n", t.depends_on.ids.join(", ")));
        }
        out.push('\n');
    }

    if !ticket.corrections.is_empty() {
        out.push_str("### Corrections on Record\n");
        for correction in &ticket.corrections {
            let date = correction.timestamp.format("%Y-%m-%d");
            out.push_str(&format!(
                "[{}] {}\nRule: {}\n\n",
                date, correction.text, correction.rule_added
            ));
        }
    }

    // Append live intelligence if available
    let intel_path = repo_root.join(".claude").join("context").join("live-intelligence.md");
    if let Ok(intel) = std::fs::read_to_string(&intel_path) {
        if !intel.contains("never (initial placeholder") && !intel.trim().is_empty() {
            out.push_str("\n### Live Intelligence\n");
            // Include first 500 chars to stay within token budget
            let preview: String = intel.chars().take(500).collect();
            out.push_str(&preview);
            if intel.len() > 500 {
                out.push_str("\n*[truncated — see .claude/context/live-intelligence.md for full intelligence]*\n");
            }
        }
    }

    out
}

fn format_prompt_context(ticket: &TicketFile, _repo_root: &Path, prompt: &str) -> String {
    let t = &ticket.ticket;
    let prompt_lower = prompt.to_lowercase();
    let mut out = String::new();

    // Check if prompt mentions any keyword from ticket context
    let relevant_files: Vec<&str> = t
        .context_refs
        .files
        .iter()
        .filter(|f| {
            let basename = f.split('/').next_back().unwrap_or(f);
            prompt_lower.contains(&f.to_lowercase())
                || prompt_lower.contains(&basename.to_lowercase())
        })
        .map(|s| s.as_str())
        .collect();

    // Check for corrections relevant to the prompt
    let relevant_corrections: Vec<_> = ticket
        .corrections
        .iter()
        .filter(|c| {
            c.text.split_whitespace().any(|word| {
                word.len() > 4 && prompt_lower.contains(&word.to_lowercase())
            }) || c.rule_added.split_whitespace().any(|word| {
                word.len() > 4 && prompt_lower.contains(&word.to_lowercase())
            })
        })
        .collect();

    // Check for relevant acceptance criteria
    let relevant_ac: Vec<(&String, &bool)> = t
        .acceptance_criteria
        .iter()
        .filter(|(ac, _)| {
            ac.split_whitespace().any(|word| {
                word.len() > 4 && prompt_lower.contains(&word.to_lowercase())
            })
        })
        .collect();

    // Only inject if there's something relevant
    if relevant_files.is_empty() && relevant_corrections.is_empty() && relevant_ac.is_empty() {
        // Always inject basic ticket status
        out.push_str(&format!(
            "**Active Ticket**: {} — {} ({}/{} criteria met)\n",
            t.id,
            t.title,
            ticket.satisfied_count(),
            ticket.criteria_count()
        ));
        return out;
    }

    out.push_str(&format!("## YAWL Intelligence — {} Context\n", t.id));

    if !relevant_ac.is_empty() {
        out.push_str("### Relevant Criteria\n");
        for (ac, done) in &relevant_ac {
            let mark = if **done { "x" } else { " " };
            out.push_str(&format!("- [{}] {}\n", mark, ac));
        }
        out.push('\n');
    }

    if !relevant_corrections.is_empty() {
        out.push_str("### Relevant Corrections\n");
        for correction in relevant_corrections {
            let date = correction.timestamp.format("%Y-%m-%d");
            out.push_str(&format!(
                "[{}] {}\nRule: {}\n\n",
                date, correction.text, correction.rule_added
            ));
        }
    }

    if !relevant_files.is_empty() {
        out.push_str("### Referenced Files\n");
        for f in relevant_files {
            out.push_str(&format!("- `{}`\n", f));
        }
    }

    out
}
