/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public License.
 */

//! Axum HTTP handlers for the SPARQL service endpoints.
//!
//! All handlers share the `SparqlStore` via Axum's `State` extractor.
//! Routes registered in `main.rs`:
//!
//! ```text
//! GET  /sparql/health           → SparqlHealthInfo JSON
//! POST /sparql/query            → CONSTRUCT query → Turtle
//! POST /sparql/update           → SPARQL 1.1 Update (INSERT/DELETE/CLEAR)
//! POST /sparql/load             → Load Turtle into default or named graph
//! DELETE /sparql/graph          → Drop a named graph (?graph=<iri>)
//! ```

use crate::sparql::SparqlStore;
use axum::{
    body::Bytes,
    extract::{Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Deserialize;

// ---------------------------------------------------------------------------
// Helper: convert SparqlError to HTTP 500
// ---------------------------------------------------------------------------

fn sparql_err(e: crate::sparql::SparqlError) -> Response {
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        format!("SPARQL error: {}", e),
    )
        .into_response()
}

// ---------------------------------------------------------------------------
// GET /sparql/health
// ---------------------------------------------------------------------------

pub async fn sparql_health(State(store): State<SparqlStore>) -> impl IntoResponse {
    Json(store.health_info())
}

// ---------------------------------------------------------------------------
// POST /sparql/query
//
// Body: a SPARQL CONSTRUCT query (Content-Type: application/sparql-query or text/plain)
// Response: Turtle (text/turtle)
// ---------------------------------------------------------------------------

pub async fn sparql_query(
    State(store): State<SparqlStore>,
    body: Bytes,
) -> Result<Response, Response> {
    let query = std::str::from_utf8(&body)
        .map_err(|_| (StatusCode::BAD_REQUEST, "Query must be valid UTF-8").into_response())?;

    let turtle = store.construct_to_turtle(query).map_err(sparql_err)?;

    Ok((
        StatusCode::OK,
        [("content-type", "text/turtle; charset=utf-8")],
        turtle,
    )
        .into_response())
}

// ---------------------------------------------------------------------------
// POST /sparql/update
//
// Body: a SPARQL 1.1 Update string (INSERT DATA / DELETE DATA / CLEAR / …)
// ---------------------------------------------------------------------------

pub async fn sparql_update(
    State(store): State<SparqlStore>,
    body: Bytes,
) -> Result<Response, Response> {
    let update = std::str::from_utf8(&body)
        .map_err(|_| (StatusCode::BAD_REQUEST, "Update must be valid UTF-8").into_response())?;

    store.sparql_update(update).map_err(sparql_err)?;

    Ok(StatusCode::NO_CONTENT.into_response())
}

// ---------------------------------------------------------------------------
// POST /sparql/load
//
// Body: Turtle RDF (text/turtle)
// Optional query param: ?graph=<iri>  (if absent, loads into default graph)
// ---------------------------------------------------------------------------

#[derive(Deserialize)]
pub struct GraphParam {
    pub graph: Option<String>,
}

pub async fn sparql_load(
    State(store): State<SparqlStore>,
    Query(params): Query<GraphParam>,
    body: Bytes,
) -> Result<Response, Response> {
    let turtle = std::str::from_utf8(&body)
        .map_err(|_| (StatusCode::BAD_REQUEST, "Turtle body must be valid UTF-8").into_response())?;

    let graph_name = params.graph.as_deref();
    store.load_turtle(turtle, graph_name).map_err(sparql_err)?;

    Ok(StatusCode::NO_CONTENT.into_response())
}

// ---------------------------------------------------------------------------
// DELETE /sparql/graph
//
// Required query param: ?graph=<iri>
// Drops all triples in the named graph.
// ---------------------------------------------------------------------------

pub async fn sparql_delete_graph(
    State(store): State<SparqlStore>,
    Query(params): Query<GraphParam>,
) -> Result<Response, Response> {
    let graph_iri = params.graph.as_deref().ok_or_else(|| {
        (
            StatusCode::BAD_REQUEST,
            "Required query parameter ?graph=<iri> is missing",
        )
            .into_response()
    })?;

    store
        .clear_graph(Some(graph_iri))
        .map_err(sparql_err)?;

    Ok(StatusCode::NO_CONTENT.into_response())
}
