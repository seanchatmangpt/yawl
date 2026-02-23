use axum::{
    routing::{get, post},
    Router,
};
use std::net::SocketAddr;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;

mod handlers;
mod pm;

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive("yawl_processmining_service=debug".parse().unwrap()),
        )
        .init();

    // Build router with process mining endpoints
    let app = Router::new()
        .route("/health", get(handlers::health))
        .route("/conformance/token-replay", post(handlers::token_replay))
        .route("/discovery/dfg", post(handlers::discover_dfg))
        .route("/discovery/alpha-ppp", post(handlers::discover_alpha_ppp))
        .route("/analysis/performance", post(handlers::performance_analysis))
        .route("/ocel/convert", post(handlers::xes_to_ocel))
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http());

    let port: u16 = std::env::var("PORT")
        .unwrap_or_else(|_| "8082".to_string())
        .parse()
        .expect("PORT must be a valid port number");

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    tracing::info!("rust4pm service listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind TCP listener");

    axum::serve(listener, app)
        .await
        .expect("Server error");
}
