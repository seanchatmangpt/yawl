use axum::{
    routing::{delete, get, post},
    Router,
};
use std::net::SocketAddr;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;

mod graph;
mod handlers;
mod ontology;
mod pm;
mod sparql;
mod sparql_handlers;

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive("yawl_native=debug".parse().unwrap()),
        )
        .init();

    // Initialise the Oxigraph in-memory store (shared across all requests)
    let sparql_store = sparql::SparqlStore::new().expect("Failed to create SparqlStore");

    // Build router with process mining + SPARQL endpoints
    let app = Router::new()
        // Health
        .route("/health", get(handlers::health))
        // Process mining
        .route("/conformance/token-replay", post(handlers::token_replay))
        .route("/discovery/dfg", post(handlers::discover_dfg))
        .route("/discovery/alpha-ppp", post(handlers::discover_alpha_ppp))
        .route("/analysis/performance", post(handlers::performance_analysis))
        .route("/ocel/convert", post(handlers::xes_to_ocel))
        .route("/ontology/mcp-tools", get(handlers::ontology_mcp_tools))
        .route("/ontology/a2a-skills", get(handlers::ontology_a2a_skills))
        // SPARQL (Oxigraph)
        .route("/sparql/health", get(sparql_handlers::sparql_health))
        .route("/sparql/query", post(sparql_handlers::sparql_query))
        .route("/sparql/update", post(sparql_handlers::sparql_update))
        .route("/sparql/load", post(sparql_handlers::sparql_load))
        .route("/sparql/graph", delete(sparql_handlers::sparql_delete_graph))
        .with_state(sparql_store)
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http());

    // Default port 8083 (avoids conflict with H2 console on 8082)
    let port: u16 = std::env::var("PORT")
        .unwrap_or_else(|_| "8083".to_string())
        .parse()
        .expect("PORT must be a valid port number");

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    tracing::info!("yawl-native service listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind TCP listener");

    axum::serve(listener, app)
        .await
        .expect("Server error");
}
