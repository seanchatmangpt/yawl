//! Oxigraph RDF/SPARQL wrapper — mirrors ggen-core/src/graph/ API.
//!
//! Follows ggen-core conventions exactly:
//!   - `Arc<Store>` for thread-safe sharing
//!   - `.map_err(|e| format!("...: {}", e))` — never `?` for oxigraph errors
//!   - CONSTRUCT results are N-Triples strings via `quad.to_string()`
//!   - SELECT results are `Vec<BTreeMap<String, String>>` (N-Triples encoded values)
//!   - `term_value()` helper strips angle brackets / quotes for callers needing raw values

use std::collections::BTreeMap;
use std::sync::Arc;

use oxigraph::io::RdfFormat;
use oxigraph::sparql::{QueryResults, SparqlEvaluator};
use oxigraph::store::Store;

/// In-memory RDF graph backed by Oxigraph.
pub struct Graph {
    inner: Arc<Store>,
}

impl Graph {
    /// Create a new in-memory graph.
    pub fn new() -> Result<Self, String> {
        let store = Store::new().map_err(|e| format!("Store::new: {}", e))?;
        Ok(Self { inner: Arc::new(store) })
    }

    /// Load Turtle-formatted RDF triples into the graph.
    pub fn insert_turtle(&self, ttl: &str) -> Result<(), String> {
        self.inner
            .load_from_reader(RdfFormat::Turtle, ttl.as_bytes())
            .map_err(|e| format!("insert_turtle: {}", e))
    }

    /// Load N-Triples into the graph (used to reload CONSTRUCT output).
    pub fn insert_ntriples(&self, ntriples: &str) -> Result<(), String> {
        self.inner
            .load_from_reader(RdfFormat::NTriples, ntriples.as_bytes())
            .map_err(|e| format!("insert_ntriples: {}", e))
    }

    /// Execute a SPARQL SELECT and return rows as BTreeMap<variable, N-Triples-encoded value>.
    /// Use `term_value()` to strip angle brackets / quotes from individual values.
    pub fn query_select(&self, sparql: &str) -> Result<Vec<BTreeMap<String, String>>, String> {
        let results = SparqlEvaluator::new()
            .parse_query(sparql)
            .map_err(|e| format!("parse_query: {}", e))?
            .on_store(self.inner.as_ref())
            .execute()
            .map_err(|e| format!("execute: {}", e))?;

        match results {
            QueryResults::Solutions(solutions) => {
                let names = solutions.variables().to_vec();
                let mut rows = Vec::new();
                for sol in solutions {
                    let sol = sol.map_err(|e| format!("solution: {}", e))?;
                    let row: BTreeMap<String, String> = names
                        .iter()
                        .filter_map(|v| {
                            sol.get(v).map(|term| (v.as_str().to_string(), term.to_string()))
                        })
                        .collect();
                    rows.push(row);
                }
                Ok(rows)
            }
            _ => Err("Expected Solutions from SELECT query".to_string()),
        }
    }
}

/// Executes SPARQL CONSTRUCT queries and materializes results back into the graph.
pub struct ConstructExecutor<'a> {
    pub graph: &'a Graph,
}

impl<'a> ConstructExecutor<'a> {
    /// Execute a CONSTRUCT query and return the result triples as N-Triples strings.
    pub fn execute(&self, sparql: &str) -> Result<Vec<String>, String> {
        let results = SparqlEvaluator::new()
            .parse_query(sparql)
            .map_err(|e| format!("parse_query: {}", e))?
            .on_store(self.graph.inner.as_ref())
            .execute()
            .map_err(|e| format!("execute: {}", e))?;

        match results {
            QueryResults::Graph(quads) => quads
                .map(|q| {
                    q.map(|q| q.to_string())
                        .map_err(|e| format!("quad: {}", e))
                })
                .collect(),
            _ => Err("Expected Graph results from CONSTRUCT query".to_string()),
        }
    }

    /// Execute a CONSTRUCT query and reload the resulting triples back into the graph.
    /// Returns the number of triples added (mirrors ggen-core's execute_and_materialize).
    pub fn execute_and_materialize(&self, sparql: &str) -> Result<usize, String> {
        let triples = self.execute(sparql)?;
        let count = triples.len();
        if count > 0 {
            self.graph.insert_ntriples(&triples.join("\n"))?;
        }
        Ok(count)
    }
}

/// Strip N-Triples encoding from a term string to get the raw value.
///
/// - IRI `<http://example.org/foo>` → `http://example.org/foo`
/// - Literal `"hello"` or `"hello"^^<xsd:string>` → `hello`
/// - Blank node `_:b0` → `_:b0` (returned as-is)
pub fn term_value(s: &str) -> String {
    if s.starts_with('"') {
        // Find closing quote for the literal value (first " after position 0)
        if let Some(end) = s[1..].find('"') {
            return s[1..end + 1].to_string();
        }
    } else if s.starts_with('<') && s.ends_with('>') {
        return s[1..s.len() - 1].to_string();
    }
    s.to_string()
}
