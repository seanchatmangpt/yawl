//! SPARQL runner — executes SPARQL CONSTRUCT/SELECT/ASK over Turtle RDF via Oxigraph.
//!
//! This binary bridges the Java YAWL integration layer to the Oxigraph SPARQL engine.
//! It implements the same pattern as ggen-core's ConstructExecutor
//! (vendors/ggen/crates/ggen-core/src/graph/construct.rs), extracted as a standalone CLI
//! so that the Java integration module can invoke real SPARQL CONSTRUCT without
//! introducing Apache Jena or the full ggen-core dependency tree.
//!
//! # Usage
//!
//! ```text
//! sparql-runner --query <sparql-file> --input <turtle-file>
//! ```
//!
//! # Output (stdout, JSON)
//!
//! CONSTRUCT:
//! ```json
//! {"triples": ["<s> <p> <o> .", ...], "count": N}
//! ```
//!
//! SELECT:
//! ```json
//! {"solutions": [{"var1": "value1", "var2": "value2"}, ...], "count": N}
//! ```
//!
//! ASK:
//! ```json
//! {"result": true}
//! ```
//!
//! # Exit codes
//! - 0: success
//! - 1: error (message on stderr)
//!
//! # CONSTRUCT Coordination Model
//!
//! This binary is the execution engine for the YAWL CONSTRUCT coordination layer.
//! When `yawl_generate_tool_schema` runs `generate-mcp-tools.sparql` over a workflow
//! net serialised as Turtle, it uses this binary to execute the CONSTRUCT query.
//! The result is MCP tool schemas derived from the formal workflow specification —
//! not hand-authored. This is the CONSTRUCT claim made executable.

use oxigraph::io::RdfFormat;
use oxigraph::sparql::QueryResults;
use oxigraph::store::Store;
use serde_json::{json, Value};
use std::collections::BTreeMap;
use std::{env, fs, process};

fn main() {
    let args: Vec<String> = env::args().collect();

    let (query_path, input_path) = parse_args(&args);

    let query = fs::read_to_string(&query_path).unwrap_or_else(|e| {
        eprintln!("error: cannot read query file '{}': {}", query_path, e);
        process::exit(1);
    });

    let turtle = fs::read_to_string(&input_path).unwrap_or_else(|e| {
        eprintln!("error: cannot read input file '{}': {}", input_path, e);
        process::exit(1);
    });

    // Create in-memory Oxigraph store — same pattern as ggen-core's Graph::new()
    let store = Store::new().unwrap_or_else(|e| {
        eprintln!("error: cannot create Oxigraph store: {}", e);
        process::exit(1);
    });

    // Load Turtle RDF — same as ggen-core's insert_turtle() using load_from_reader
    store
        .load_from_reader(RdfFormat::Turtle, turtle.as_bytes())
        .unwrap_or_else(|e| {
            eprintln!("error: cannot load Turtle RDF: {}", e);
            process::exit(1);
        });

    // Execute SPARQL — same as ggen-core's Graph::query()
    let results = store.query(&query).unwrap_or_else(|e| {
        eprintln!("error: SPARQL query execution failed: {}", e);
        process::exit(1);
    });

    // Serialise results to JSON — same pattern as ggen-core's materialize_results()
    let output: Value = match results {
        QueryResults::Graph(quads) => {
            // CONSTRUCT: yields quads (triples in default graph); to_string() → N-Quads/N-Triples
            let mut triples: Vec<String> = Vec::new();
            for quad_result in quads {
                match quad_result {
                    Ok(quad) => triples.push(quad.to_string()),
                    Err(e) => {
                        eprintln!("warning: quad error: {}", e);
                    }
                }
            }
            let count = triples.len();
            json!({ "triples": triples, "count": count })
        }

        QueryResults::Solutions(solutions) => {
            // SELECT: collect variable bindings as JSON objects
            let mut rows: Vec<Value> = Vec::new();
            for solution_result in solutions {
                match solution_result {
                    Ok(solution) => {
                        let mut row: BTreeMap<String, String> = BTreeMap::new();
                        for (var, term) in solution.iter() {
                            row.insert(var.as_str().to_string(), term.to_string());
                        }
                        rows.push(json!(row));
                    }
                    Err(e) => {
                        eprintln!("warning: solution error: {}", e);
                    }
                }
            }
            let count = rows.len();
            json!({ "solutions": rows, "count": count })
        }

        QueryResults::Boolean(b) => {
            // ASK
            json!({ "result": b })
        }
    };

    println!("{}", output);
}

/// Parse --query <path> --input <path> from argv.
fn parse_args(args: &[String]) -> (String, String) {
    let mut query_path: Option<String> = None;
    let mut input_path: Option<String> = None;
    let mut i = 1usize;

    while i < args.len() {
        match args[i].as_str() {
            "--query" if i + 1 < args.len() => {
                query_path = Some(args[i + 1].clone());
                i += 2;
            }
            "--input" if i + 1 < args.len() => {
                input_path = Some(args[i + 1].clone());
                i += 2;
            }
            "--help" | "-h" => {
                eprintln!(
                    "usage: sparql-runner --query <sparql-file> --input <turtle-file>\n\
                     \n\
                     Executes a SPARQL CONSTRUCT/SELECT/ASK query over a Turtle RDF file.\n\
                     Outputs JSON to stdout. Exit 0 on success, 1 on error."
                );
                process::exit(0);
            }
            other => {
                eprintln!("warning: unknown argument '{}'", other);
                i += 1;
            }
        }
    }

    let q = query_path.unwrap_or_else(|| {
        eprintln!(
            "error: --query <sparql-file> is required\n\
             usage: sparql-runner --query <sparql-file> --input <turtle-file>"
        );
        process::exit(1);
    });

    let inp = input_path.unwrap_or_else(|| {
        eprintln!(
            "error: --input <turtle-file> is required\n\
             usage: sparql-runner --query <sparql-file> --input <turtle-file>"
        );
        process::exit(1);
    });

    (q, inp)
}
