use axum::{
    extract::Json,
    http::StatusCode,
    response::{IntoResponse, Response},
};
use serde::{Deserialize, Serialize};

use crate::pm::{self, *};

/// Health check response
#[derive(Serialize)]
pub struct HealthResponse {
    pub status: String,
    pub version: String,
    pub timestamp: String,
}

/// Conformance check request (token-based replay)
#[derive(Deserialize)]
pub struct ReplayRequest {
    pub pnml: String,
    pub xes: String,
}

/// Conformance check response
#[derive(Serialize)]
pub struct ReplayResponse {
    pub fitness: f64,
    pub produced: u64,
    pub consumed: u64,
    pub missing: u64,
    pub remaining: u64,
    pub deviating_cases: Vec<String>,
}

/// Process discovery request (generic XES input)
#[derive(Deserialize)]
pub struct DiscoveryRequest {
    pub xes: String,
}

/// DFG discovery response
#[derive(Serialize)]
pub struct DfgResponse {
    pub nodes: Vec<DfgNode>,
    pub edges: Vec<DfgEdge>,
    pub start_activities: serde_json::Value,
    pub end_activities: serde_json::Value,
}

/// Alpha++ discovery response
#[derive(Serialize)]
pub struct AlphaResponse {
    pub pnml: String,
}

/// Performance analysis response
#[derive(Serialize)]
pub struct PerformanceResponse {
    pub trace_count: usize,
    pub total_events: usize,
    pub avg_trace_length: f64,
    pub min_trace_length: usize,
    pub max_trace_length: usize,
    pub avg_flow_time_ms: f64,
    pub throughput_per_hour: f64,
    pub activity_stats: serde_json::Value,
}

/// Error response struct
#[derive(Serialize)]
pub struct ErrorResponse {
    pub error: String,
    pub details: Option<String>,
}

impl IntoResponse for ErrorResponse {
    fn into_response(self) -> Response {
        (
            StatusCode::BAD_REQUEST,
            Json(self),
        )
            .into_response()
    }
}

/// Health check endpoint
pub async fn health() -> Json<HealthResponse> {
    tracing::info!("Health check requested");
    Json(HealthResponse {
        status: "ok".to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
        timestamp: chrono::Utc::now().to_rfc3339(),
    })
}

/// Token-based replay conformance checking
pub async fn token_replay(
    Json(req): Json<ReplayRequest>,
) -> Result<Json<ReplayResponse>, (StatusCode, Json<ErrorResponse>)> {
    tracing::info!("Token-based replay conformance check requested");

    // Parse event log from XES
    let log = pm::parse_xes(&req.xes).map_err(|e| {
        tracing::error!("XES parse error: {}", e);
        (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "XES parse error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    // Parse Petri net from PNML
    let net = pm::parse_pnml(&req.pnml).map_err(|e| {
        tracing::error!("PNML parse error: {}", e);
        (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "PNML parse error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    // Apply token-based replay
    let replay_result = pm::token_based_replay(&net, &log).map_err(|e| {
        tracing::error!("Token-based replay error: {}", e);
        (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(ErrorResponse {
                error: "Replay execution error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    tracing::info!(
        "Replay complete: fitness={:.4}, deviating_traces={}",
        replay_result.fitness,
        replay_result.deviating_traces.len()
    );

    Ok(Json(ReplayResponse {
        fitness: replay_result.fitness,
        produced: replay_result.produced,
        consumed: replay_result.consumed,
        missing: replay_result.missing,
        remaining: replay_result.remaining,
        deviating_cases: replay_result.deviating_traces,
    }))
}

/// Discover Directly-Follows Graph (DFG)
pub async fn discover_dfg(
    Json(req): Json<DiscoveryRequest>,
) -> Result<Json<DfgResponse>, (StatusCode, Json<ErrorResponse>)> {
    tracing::info!("DFG discovery requested");

    let log = pm::parse_xes(&req.xes).map_err(|e| {
        tracing::error!("XES parse error: {}", e);
        (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "XES parse error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    let dfg_result = pm::discover_dfg(&log);

    tracing::info!(
        "DFG discovery complete: {} nodes, {} edges",
        dfg_result.nodes.len(),
        dfg_result.edges.len()
    );

    Ok(Json(DfgResponse {
        nodes: dfg_result.nodes,
        edges: dfg_result.edges,
        start_activities: serde_json::to_value(&dfg_result.start_activities)
            .unwrap_or(serde_json::Value::Object(Default::default())),
        end_activities: serde_json::to_value(&dfg_result.end_activities)
            .unwrap_or(serde_json::Value::Object(Default::default())),
    }))
}

/// Discover Petri net using Alpha++ algorithm
pub async fn discover_alpha_ppp(
    Json(req): Json<DiscoveryRequest>,
) -> Result<Json<AlphaResponse>, (StatusCode, Json<ErrorResponse>)> {
    tracing::info!("Alpha++ Petri net discovery requested");

    let log = pm::parse_xes(&req.xes).map_err(|e| {
        tracing::error!("XES parse error: {}", e);
        (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "XES parse error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    let net = pm::discover_alpha_ppp(&log).map_err(|e| {
        tracing::error!("Alpha++ discovery error: {}", e);
        (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(ErrorResponse {
                error: "Alpha++ discovery error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    let pnml = pm::export_pnml(&net);

    tracing::info!(
        "Alpha++ discovery complete: {} places, {} transitions",
        net.places.len(),
        net.transitions.len()
    );

    Ok(Json(AlphaResponse { pnml }))
}

/// Performance analysis (flow time, throughput, activity statistics)
pub async fn performance_analysis(
    Json(req): Json<DiscoveryRequest>,
) -> Result<Json<PerformanceResponse>, (StatusCode, Json<ErrorResponse>)> {
    tracing::info!("Performance analysis requested");

    let log = pm::parse_xes(&req.xes).map_err(|e| {
        tracing::error!("XES parse error: {}", e);
        (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "XES parse error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    let stats = pm::compute_performance_stats(&log);

    tracing::info!(
        "Performance analysis complete: {} traces, avg flow time={:.0}ms, throughput={:.1}/hr",
        stats.trace_count, stats.avg_flow_time_ms, stats.throughput_per_hour
    );

    Ok(Json(PerformanceResponse {
        trace_count: stats.trace_count,
        total_events: stats.total_events,
        avg_trace_length: stats.avg_trace_length,
        min_trace_length: stats.min_trace_length,
        max_trace_length: stats.max_trace_length,
        avg_flow_time_ms: stats.avg_flow_time_ms,
        throughput_per_hour: stats.throughput_per_hour,
        activity_stats: serde_json::to_value(&stats.activity_stats)
            .unwrap_or(serde_json::Value::Object(Default::default())),
    }))
}

/// Convert event log (XES) to OCEL 2.0 format
pub async fn xes_to_ocel(
    Json(req): Json<DiscoveryRequest>,
) -> Result<(StatusCode, Json<serde_json::Value>), (StatusCode, Json<ErrorResponse>)> {
    tracing::info!("XES to OCEL 2.0 conversion requested");

    let log = pm::parse_xes(&req.xes).map_err(|e| {
        tracing::error!("XES parse error: {}", e);
        (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "XES parse error".to_string(),
                details: Some(e),
            }),
        )
    })?;

    let ocel_json = pm::convert_to_ocel(&log);

    tracing::info!(
        "XES to OCEL conversion complete: {} objects, {} events",
        log.traces.len(),
        log.traces.iter().map(|t| t.events.len()).sum::<usize>()
    );

    Ok((StatusCode::OK, Json(ocel_json)))
}
