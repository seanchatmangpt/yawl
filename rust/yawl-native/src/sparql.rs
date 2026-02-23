/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public License.
 */

//! Oxigraph-backed SPARQL store for the yawl-native service.
//!
//! Mirrors the pattern from `ggen-core/src/graph/core.rs`:
//! - `Arc<Store>` for shared, thread-safe access across Axum handlers
//! - Epoch counter (`AtomicU64`) incremented on every write for cache invalidation
//! - CONSTRUCT chain: execute a sequence of rules, materialising each into the store
//!
//! The in-memory store is sufficient for the marketplace use-case (dozens to hundreds
//! of listings). A RocksDB-backed persistent store can be layered on by passing a path
//! to `SparqlStore::open`.

use oxigraph::io::{RdfFormat, RdfParser, RdfSerializer};
use oxigraph::model::GraphName;
use oxigraph::sparql::{QueryResults, Update};
use oxigraph::store::Store;
use serde::{Deserialize, Serialize};
use std::io::{BufWriter, Cursor};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};

/// Thread-safe Oxigraph store with epoch-based cache tracking.
///
/// Cloning is cheap: all clones share the same underlying `Arc<Store>` and epoch counter.
#[derive(Clone)]
pub struct SparqlStore {
    inner: Arc<Store>,
    /// Incremented on every write operation. Can be used by callers to detect staleness.
    epoch: Arc<AtomicU64>,
}

#[derive(Debug)]
pub struct SparqlError(pub String);

impl std::fmt::Display for SparqlError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "SparqlError: {}", self.0)
    }
}

impl std::error::Error for SparqlError {}

impl From<oxigraph::store::StorageError> for SparqlError {
    fn from(e: oxigraph::store::StorageError) -> Self {
        SparqlError(e.to_string())
    }
}

impl From<oxigraph::sparql::EvaluationError> for SparqlError {
    fn from(e: oxigraph::sparql::EvaluationError) -> Self {
        SparqlError(e.to_string())
    }
}

impl From<std::io::Error> for SparqlError {
    fn from(e: std::io::Error) -> Self {
        SparqlError(e.to_string())
    }
}

/// Health information returned by `GET /sparql/health`.
#[derive(Serialize, Deserialize)]
pub struct SparqlHealthInfo {
    pub status: &'static str,
    pub engine: &'static str,
    pub version: &'static str,
    pub triples: usize,
    pub epoch: u64,
}

impl SparqlStore {
    /// Create a new in-memory Oxigraph store.
    pub fn new() -> Result<Self, SparqlError> {
        let store = Store::new().map_err(|e| SparqlError(e.to_string()))?;
        Ok(Self {
            inner: Arc::new(store),
            epoch: Arc::new(AtomicU64::new(0)),
        })
    }

    /// Load Turtle RDF into the default graph (graph_name = None) or a named graph.
    ///
    /// The epoch is incremented after a successful load.
    pub fn load_turtle(&self, turtle: &str, graph_name: Option<&str>) -> Result<(), SparqlError> {
        let graph: GraphName = match graph_name {
            None => GraphName::DefaultGraph,
            Some(iri) => {
                let named = oxigraph::model::NamedNode::new(iri)
                    .map_err(|e| SparqlError(format!("Invalid graph IRI '{}': {}", iri, e)))?;
                GraphName::NamedNode(named)
            }
        };

        let parser = RdfParser::from_format(RdfFormat::Turtle)
            .with_default_graph(graph.clone());

        self.inner
            .load_from_reader(parser, Cursor::new(turtle.as_bytes()))
            .map_err(|e| SparqlError(e.to_string()))?;

        self.epoch.fetch_add(1, Ordering::Relaxed);
        Ok(())
    }

    /// Execute a SPARQL 1.1 Update (INSERT DATA, DELETE DATA, CLEAR, etc.).
    ///
    /// The epoch is incremented after a successful update.
    pub fn sparql_update(&self, update: &str) -> Result<(), SparqlError> {
        let parsed = Update::parse(update, None)
            .map_err(|e| SparqlError(format!("SPARQL update parse error: {}", e)))?;
        self.inner
            .update(parsed)
            .map_err(|e| SparqlError(e.to_string()))?;
        self.epoch.fetch_add(1, Ordering::Relaxed);
        Ok(())
    }

    /// Execute a SPARQL CONSTRUCT query and return the result as a Turtle string.
    pub fn construct_to_turtle(&self, query: &str) -> Result<String, SparqlError> {
        let results = self
            .inner
            .query(query)
            .map_err(|e| SparqlError(e.to_string()))?;

        match results {
            QueryResults::Graph(triples) => {
                let mut buf = BufWriter::new(Vec::new());
                let mut serializer = RdfSerializer::from_format(RdfFormat::Turtle)
                    .serialize_to_write(&mut buf);
                for triple_result in triples {
                    let triple = triple_result.map_err(|e| SparqlError(e.to_string()))?;
                    serializer
                        .serialize_triple(triple.as_ref())
                        .map_err(|e| SparqlError(e.to_string()))?;
                }
                serializer.finish().map_err(|e| SparqlError(e.to_string()))?;
                let bytes = buf.into_inner().map_err(|e| SparqlError(e.to_string()))?;
                String::from_utf8(bytes)
                    .map_err(|e| SparqlError(format!("UTF-8 error in Turtle output: {}", e)))
            }
            _ => Err(SparqlError(
                "Expected a CONSTRUCT query; got SELECT or ASK".to_string(),
            )),
        }
    }

    /// Execute a chain of CONSTRUCT queries, materialising each result into the store.
    ///
    /// Mirrors `ggen-core ConstructExecutor::execute_chain`. Each query's result triples
    /// are inserted into the default graph before the next query runs. Returns the number
    /// of triples materialised per query.
    pub fn construct_chain(&self, queries: &[&str]) -> Result<Vec<usize>, SparqlError> {
        let mut counts = Vec::with_capacity(queries.len());
        for query in queries {
            let turtle = self.construct_to_turtle(query)?;
            // Count triples materialised by parsing the Turtle back
            let triples_before = self.triple_count();
            self.load_turtle(&turtle, None)?;
            counts.push(self.triple_count() - triples_before);
        }
        Ok(counts)
    }

    /// Drop all triples in the named graph (or default graph if None).
    pub fn clear_graph(&self, graph_name: Option<&str>) -> Result<(), SparqlError> {
        match graph_name {
            None => {
                self.inner
                    .clear_graph(GraphName::DefaultGraph)
                    .map_err(|e| SparqlError(e.to_string()))?;
            }
            Some(iri) => {
                let named = oxigraph::model::NamedNode::new(iri)
                    .map_err(|e| SparqlError(format!("Invalid graph IRI '{}': {}", iri, e)))?;
                self.inner
                    .clear_graph(named)
                    .map_err(|e| SparqlError(e.to_string()))?;
            }
        }
        self.epoch.fetch_add(1, Ordering::Relaxed);
        Ok(())
    }

    /// Total number of quads (subject + predicate + object + graph) in the store.
    pub fn triple_count(&self) -> usize {
        self.inner.len().unwrap_or(0)
    }

    /// Current epoch value (incremented on every write).
    pub fn epoch(&self) -> u64 {
        self.epoch.load(Ordering::Relaxed)
    }

    /// Build a health snapshot.
    pub fn health_info(&self) -> SparqlHealthInfo {
        SparqlHealthInfo {
            status: "ok",
            engine: "oxigraph",
            version: "0.4",
            triples: self.triple_count(),
            epoch: self.epoch(),
        }
    }
}
