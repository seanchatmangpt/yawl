use std::io::{self, Read};
use std::path::{Path, PathBuf};

use anyhow::Context;
use clap::{Parser, Subcommand};
use serde_json::json;

use delta::receipt::{DeltaReceipt, SessionReceipt};
use differ::compute_deltas;
use jira::{inject_prompt, inject_session, ticket::TicketFile};

/// yawl-jira — YAWL Intelligence Layer JIRA hook binary.
///
/// Exit codes:
///   0  Success, JSON on stdout (if applicable)
///   1  Non-blocking error (execution continues)
///   2  Blocking error (hook blocks the action)
#[derive(Parser)]
#[command(name = "yawl-jira", version, about)]
struct Cli {
    #[command(subcommand)]
    command: Command,

    /// Repository root (default: find git root from CWD)
    #[arg(long, global = true)]
    repo_root: Option<PathBuf>,
}

#[derive(Subcommand)]
enum Command {
    /// Inject active ticket context at SessionStart
    Inject {
        #[command(subcommand)]
        mode: InjectMode,
    },
    /// Pre-write hook: inject research context if topic is novel
    PreWrite,
    /// Post-write hook: compute typed delta, update ticket state
    PostWrite,
    /// Checkpoint: flush watermarks, emit session receipt
    Checkpoint,
    /// Update a ticket's status
    Status {
        ticket_id: String,
        status: String,
    },
}

#[derive(Subcommand)]
enum InjectMode {
    /// Full session context injection (SessionStart)
    Session,
    /// Prompt-level context injection (UserPromptSubmit)
    Prompt,
}

fn main() {
    let cli = Cli::parse();
    let repo_root = resolve_repo_root(cli.repo_root.as_deref());

    let result = match cli.command {
        Command::Inject { mode: InjectMode::Session } => cmd_inject_session(&repo_root),
        Command::Inject { mode: InjectMode::Prompt } => cmd_inject_prompt(&repo_root),
        Command::PreWrite => cmd_pre_write(&repo_root),
        Command::PostWrite => cmd_post_write(&repo_root),
        Command::Checkpoint => cmd_checkpoint(&repo_root),
        Command::Status { ticket_id, status } => cmd_set_status(&repo_root, &ticket_id, &status),
    };

    if let Err(e) = result {
        eprintln!("[yawl-jira] ERROR: {:?}", e);
        std::process::exit(1);
    }
}

fn cmd_inject_session(repo_root: &Path) -> anyhow::Result<()> {
    let output = inject_session(repo_root)
        .context("Failed to inject session context")?;

    // Hook output format: { "additionalContext": "..." }
    let response = json!({
        "additionalContext": output.context
    });
    println!("{}", serde_json::to_string(&response)?);
    Ok(())
}

fn cmd_inject_prompt(repo_root: &Path) -> anyhow::Result<()> {
    // UserPromptSubmit hook: read JSON from stdin
    let mut stdin_buf = String::new();
    io::stdin().read_to_string(&mut stdin_buf)?;

    let prompt_text = if stdin_buf.trim().is_empty() {
        String::new()
    } else {
        // Try to parse as JSON first
        if let Ok(v) = serde_json::from_str::<serde_json::Value>(&stdin_buf) {
            v.get("prompt")
                .and_then(|p| p.as_str())
                .unwrap_or("")
                .to_string()
        } else {
            stdin_buf.clone()
        }
    };

    let output = inject_prompt(repo_root, &prompt_text)
        .context("Failed to inject prompt context")?;

    // Only inject if there's something meaningful (not just ticket status)
    if !output.context.trim().is_empty() {
        let response = json!({
            "additionalContext": output.context
        });
        println!("{}", serde_json::to_string(&response)?);
    }

    Ok(())
}

fn cmd_pre_write(repo_root: &Path) -> anyhow::Result<()> {
    // PreToolUse hook: read tool info from stdin
    let mut stdin_buf = String::new();
    io::stdin().read_to_string(&mut stdin_buf)?;

    let tool_input: serde_json::Value = serde_json::from_str(&stdin_buf)
        .unwrap_or_else(|_| json!({}));

    let file_path = tool_input
        .get("file_path")
        .and_then(|v| v.as_str())
        .unwrap_or("");

    // Check if this file is referenced by the active ticket
    let ticket_path = repo_root.join(".claude").join("context").join("active-ticket.toml");
    if ticket_path.exists() {
        let ticket = TicketFile::load(&ticket_path)
            .context("Failed to load active ticket")?;

        let is_in_scope = ticket.ticket.context_refs.files
            .iter()
            .any(|f| file_path.contains(f.as_str()) || f.contains(file_path));

        let reason = if is_in_scope {
            format!("ticket {} active; write to {} is in scope", ticket.ticket.id, file_path)
        } else {
            format!("write to {} (ticket {} active)", file_path, ticket.ticket.id)
        };

        let response = json!({
            "decision": "approve",
            "reason": reason
        });
        println!("{}", serde_json::to_string(&response)?);
    }

    Ok(())
}

fn cmd_post_write(repo_root: &Path) -> anyhow::Result<()> {
    // PostToolUse hook: compute typed delta
    let mut stdin_buf = String::new();
    io::stdin().read_to_string(&mut stdin_buf)?;

    let tool_result: serde_json::Value = serde_json::from_str(&stdin_buf)
        .unwrap_or_else(|_| json!({}));

    let file_path = tool_result
        .get("tool_input")
        .and_then(|v| v.get("file_path"))
        .and_then(|v| v.as_str())
        .unwrap_or("");

    let old_content = tool_result
        .get("old_content")
        .and_then(|v| v.as_str())
        .unwrap_or("");

    let new_content = tool_result
        .get("tool_input")
        .and_then(|v| v.get("new_string").or_else(|| v.get("content")))
        .and_then(|v| v.as_str())
        .unwrap_or("");

    if file_path.is_empty() || new_content.is_empty() {
        return Ok(());
    }

    // Compute typed deltas
    let deltas = compute_deltas(file_path, old_content, new_content)
        .context("Failed to compute deltas")?;

    // Create receipt
    let session_id = std::env::var("CLAUDE_SESSION_ID").unwrap_or_else(|_| "unknown".to_string());
    let receipt = DeltaReceipt::new(
        session_id,
        file_path.to_string(),
        old_content,
        new_content,
        deltas.clone(),
        false,
    ).context("Failed to create receipt")?;

    // Save receipt
    let receipts_dir = repo_root.join(".claude").join("context").join("receipts");
    std::fs::create_dir_all(&receipts_dir)?;
    let receipt_path = receipts_dir.join(format!(
        "{}-{}.delta.json",
        receipt.timestamp_ns,
        sanitize_path(file_path)
    ));
    let receipt_json = serde_json::to_string_pretty(&receipt)?;
    std::fs::write(&receipt_path, receipt_json)?;

    // Check if any acceptance criteria are now satisfied
    let ticket_path = repo_root.join(".claude").join("context").join("active-ticket.toml");
    if ticket_path.exists() {
        let mut ticket = TicketFile::load(&ticket_path)
            .context("Failed to load active ticket")?;

        let satisfied = jira::criterion::check_satisfaction(
            &mut ticket,
            file_path,
            new_content,
            &deltas,
        );

        if !satisfied.is_empty() {
            // Save updated ticket
            let ticket_toml = ticket.to_toml()
                .context("Failed to serialize ticket")?;
            std::fs::write(&ticket_path, ticket_toml)?;

            eprintln!(
                "[yawl-jira] Criteria satisfied: {}",
                satisfied.join(", ")
            );
        }
    }

    Ok(())
}

fn cmd_checkpoint(repo_root: &Path) -> anyhow::Result<()> {
    let session_id = std::env::var("CLAUDE_SESSION_ID").unwrap_or_else(|_| "unknown".to_string());

    // Load all delta receipts from this session
    let receipts_dir = repo_root.join(".claude").join("context").join("receipts");
    let mut session_receipt = SessionReceipt::new(session_id.clone());

    if receipts_dir.exists() {
        let mut entries: Vec<_> = std::fs::read_dir(&receipts_dir)?
            .filter_map(|e| e.ok())
            .filter(|e| {
                e.path()
                    .file_name()
                    .and_then(|n| n.to_str())
                    .is_some_and(|n| n.ends_with(".delta.json"))
            })
            .collect();
        entries.sort_by_key(|e| e.path());

        for entry in entries {
            let content = std::fs::read_to_string(entry.path())?;
            if let Ok(receipt) = serde_json::from_str::<DeltaReceipt>(&content) {
                session_receipt.push(receipt)?;
            }
        }
    }

    session_receipt.close();

    // Write session receipt
    let session_receipts_dir = repo_root.join("receipts");
    std::fs::create_dir_all(&session_receipts_dir)?;
    let session_receipt_path = session_receipts_dir.join(format!("{}.receipt.json", session_id));
    let json = serde_json::to_string_pretty(&session_receipt)?;
    std::fs::write(&session_receipt_path, json)?;

    eprintln!(
        "[yawl-jira] Session receipt written: {} ({} deltas)",
        session_receipt_path.display(),
        session_receipt.receipt_count
    );

    Ok(())
}

fn cmd_set_status(repo_root: &Path, ticket_id: &str, status: &str) -> anyhow::Result<()> {
    let tickets_dir = repo_root.join(".claude").join("jira").join("tickets");
    let ticket_path = tickets_dir.join(format!("{}.toml", ticket_id));

    let mut ticket = TicketFile::load(&ticket_path)
        .with_context(|| format!("Failed to load ticket {}", ticket_id))?;

    ticket.ticket.status = status.to_uppercase();

    let toml = ticket.to_toml()?;
    std::fs::write(&ticket_path, toml)?;

    // Also update active-ticket.toml if it matches
    let active_path = repo_root.join(".claude").join("context").join("active-ticket.toml");
    if active_path.exists() {
        if let Ok(mut active) = TicketFile::load(&active_path) {
            if active.ticket.id == ticket_id {
                active.ticket.status = status.to_uppercase();
                let toml = active.to_toml()?;
                std::fs::write(&active_path, toml)?;
            }
        }
    }

    eprintln!("[yawl-jira] Ticket {} status → {}", ticket_id, status.to_uppercase());
    Ok(())
}

fn resolve_repo_root(specified: Option<&Path>) -> PathBuf {
    if let Some(p) = specified {
        return p.to_path_buf();
    }

    // Walk up from CWD to find git root
    let cwd = std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
    let mut current = cwd.as_path();
    loop {
        if current.join(".git").exists() {
            return current.to_path_buf();
        }
        match current.parent() {
            Some(p) => current = p,
            None => return cwd,
        }
    }
}

fn sanitize_path(path: &str) -> String {
    path.replace(['/', '\\', ':'], "_")
        .chars()
        .take(50)
        .collect()
}
