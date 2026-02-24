//! Live Workflow Runtime Oracle emitter.
//!
//! Probes the YAWL engine REST API (or TCP socket if available) to collect
//! live runtime facts: engine status, active cases, workitems, deadlocks.
//!
//! Non-blocking: If engine is offline, returns offline facts within 500ms timeout.
//! Uses stdlib TCP connect (no external HTTP crate) for simplicity and reliability.

use serde_json::{json, Value};
use crate::emitters::{EmitCtx, EmitResult, write_json};
use crate::discovery::Discovery;
use crate::cache::Cache;
use std::net::TcpStream;
use std::time::Duration;
use std::io::{Read, Write};

pub fn emit(_ctx: &EmitCtx, _disc: &Discovery, _cache: &Cache) -> EmitResult {
    let facts_path = _ctx.facts_dir().join("workflow-runtime.json");

    // Check YAWL_ENGINE_URL env var, default to localhost:8080
    let engine_url = std::env::var("YAWL_ENGINE_URL")
        .unwrap_or_else(|_| "http://localhost:8080/yawl".to_string());

    let runtime = probe_engine(&engine_url);
    write_json(&facts_path, &runtime)
}

/// Probe YAWL engine for live runtime facts.
///
/// Attempts TCP connection with 500ms timeout. If successful, sends HTTP GET
/// to /yawl/ib/checkConnection and parses response. Falls back to offline
/// facts on timeout or connection error.
fn probe_engine(url: &str) -> Value {
    match extract_host_port(url) {
        Some((host, port)) => {
            match probe_tcp(&host, port) {
                Some(response) => parse_engine_response(url, &response),
                None => offline_facts(url),
            }
        }
        None => offline_facts(url),
    }
}

/// Extract host and port from HTTP URL.
///
/// Handles both http:// and https:// URLs. Defaults to port 80 if not specified.
/// Examples:
///   "http://localhost:8080/yawl" → Some(("localhost", 8080))
///   "http://engine.local/yawl"  → Some(("engine.local", 80))
///   "invalid" → None
fn extract_host_port(url: &str) -> Option<(String, u16)> {
    // Remove http:// or https://
    let rest = url
        .strip_prefix("https://")
        .or_else(|| url.strip_prefix("http://"))?;

    // Take up to / (path part)
    let host_port = rest.split('/').next()?;

    if let Some(colon) = host_port.rfind(':') {
        let host = host_port[..colon].to_string();
        let port_str = &host_port[colon + 1..];
        let port: u16 = port_str.parse().ok()?;
        Some((host, port))
    } else {
        Some((host_port.to_string(), 80))
    }
}

/// Attempt TCP connection with 500ms timeout.
///
/// Sends HTTP GET /yawl/ib/checkConnection and reads response.
/// Returns Some(response) on success, None on timeout or error.
fn probe_tcp(host: &str, port: u16) -> Option<String> {
    let addr = format!("{}:{}", host, port);
    let timeout = Duration::from_millis(500);

    let mut stream = TcpStream::connect_timeout(
        &addr.parse().ok()?,
        timeout
    ).ok()?;

    // Set read timeout (additional safety)
    stream.set_read_timeout(Some(Duration::from_millis(300))).ok()?;

    // Send HTTP GET request
    let request = "GET /yawl/ib/checkConnection HTTP/1.1\r\n\
                   Host: localhost\r\n\
                   Connection: close\r\n\
                   \r\n";
    stream.write_all(request.as_bytes()).ok()?;

    // Read response (limited to 4KB)
    let mut buffer = [0u8; 4096];
    match stream.read(&mut buffer) {
        Ok(n) if n > 0 => {
            let response = String::from_utf8_lossy(&buffer[..n]).to_string();
            Some(response)
        }
        _ => None,
    }
}

/// Parse HTTP response from engine.
///
/// Extracts body after headers (empty line). Looks for JSON or "success" text.
/// If engine responds at all, it's online. Extracts case/workitem counts if possible.
fn parse_engine_response(url: &str, response: &str) -> Value {
    // Check if response contains HTTP 200 OK
    let is_ok = response.contains("200 OK") || response.contains("200OK");

    // Find body (after empty line)
    let body = response
        .split("\r\n\r\n")
        .nth(1)
        .or_else(|| response.split("\n\n").nth(1))
        .unwrap_or("");

    // Try to parse JSON response
    if let Ok(json) = serde_json::from_str::<Value>(body) {
        json!({
            "engine_status": if is_ok { "online" } else { "offline" },
            "engine_url": url,
            "active_cases": json.get("activeCases").and_then(|v| v.as_u64()).unwrap_or(0),
            "enabled_workitems": json.get("enabledWorkitems").and_then(|v| v.as_u64()).unwrap_or(0),
            "executing_workitems": json.get("executingWorkitems").and_then(|v| v.as_u64()).unwrap_or(0),
            "deadlocked_cases": json.get("deadlockedCases").and_then(|v| v.as_u64()).unwrap_or(0),
            "loaded_specifications": json.get("loadedSpecifications").and_then(|v| v.as_u64()).unwrap_or(0),
            "queried_at": crate::emitters::utc_now(),
            "response_sample": &body[..body.len().min(100)],
        })
    } else if is_ok {
        // Engine responded but didn't return JSON — still online
        json!({
            "engine_status": "online",
            "engine_url": url,
            "active_cases": 0,
            "enabled_workitems": 0,
            "executing_workitems": 0,
            "deadlocked_cases": 0,
            "loaded_specifications": 0,
            "queried_at": crate::emitters::utc_now(),
            "note": "Engine online but response format not recognized",
        })
    } else {
        offline_facts(url)
    }
}

/// Return offline facts when engine is not reachable.
fn offline_facts(url: &str) -> Value {
    json!({
        "engine_status": "offline",
        "engine_url": url,
        "active_cases": 0,
        "enabled_workitems": 0,
        "executing_workitems": 0,
        "deadlocked_cases": 0,
        "loaded_specifications": 0,
        "queried_at": crate::emitters::utc_now(),
        "reason": "Connection timeout or refused (500ms max wait)",
    })
}
