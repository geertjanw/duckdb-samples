//! Axum entry point and HTTP routes - the Rust analogue of DemoController plus
//! DuckdbOdbcApplication.
//!
//! The ODBC connection is opened at startup and the process exits if MySQL is
//! not reachable, matching the Java demo's fail-fast @PostConstruct behavior.

mod benchmark_service;
mod config;
mod duckdb_conn;
mod odbc_query_service;

use std::collections::HashMap;
use std::sync::Arc;

use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde_json::{json, Value};

use benchmark_service::{to_json, BenchmarkService};
use config::Config;
use duckdb_conn::DuckDb;

/// Shared application state handed to every request.
#[derive(Clone)]
struct AppState {
    db: DuckDb,
    bench: Arc<BenchmarkService>,
}

#[tokio::main]
async fn main() {
    let cfg = Config::from_env();

    let db = match DuckDb::open(&cfg.duckdb_database) {
        Ok(db) => db,
        Err(e) => {
            eprintln!("Startup failed: {e}");
            std::process::exit(1);
        }
    };

    // Open the ODBC connection at startup; fail fast if MySQL isn't reachable.
    if let Err(e) = odbc_query_service::init(&db, &cfg) {
        eprintln!("Startup failed: {e}");
        std::process::exit(1);
    }

    let bench = Arc::new(BenchmarkService::new(db.clone(), cfg.clone()));
    let state = AppState { db, bench };

    let app = Router::new()
        // Top customers, computed inside MySQL, fetched through ODBC.
        .route("/customers/top", get(top_customers))
        // MySQL rows aggregated by DuckDB - the hybrid query demo.
        .route("/customers/revenue-by-country", get(revenue_by_country))
        // Generate and load N rows into MySQL (default 100M).
        .route("/benchmark/load", post(bench_load))
        // Benchmark the aggregation through the ODBC extension.
        .route("/benchmark/odbc", get(bench_odbc))
        // Benchmark the same aggregation through the native MySQL scanner.
        .route("/benchmark/scanner", get(bench_scanner))
        // Benchmark the same aggregation on the local DuckDB table (no transfer).
        .route("/benchmark/native", get(bench_native))
        .with_state(state);

    let addr = format!("0.0.0.0:{}", cfg.port);
    let listener = match tokio::net::TcpListener::bind(&addr).await {
        Ok(l) => l,
        Err(e) => {
            eprintln!("Could not bind {addr}: {e}");
            std::process::exit(1);
        }
    };
    println!("Listening on http://localhost:{}", cfg.port);
    axum::serve(listener, app).await.unwrap();
}

/// Run a blocking DuckDB call off the async runtime and turn the result into a
/// JSON response, surfacing any DuckDB/ODBC error as a 500 JSON body (matching
/// the other demos' error shape).
async fn blocking_json<F>(f: F) -> Response
where
    F: FnOnce() -> duckdb::Result<Value> + Send + 'static,
{
    match tokio::task::spawn_blocking(f).await {
        Ok(Ok(value)) => Json(value).into_response(),
        Ok(Err(e)) => error_response(e.to_string()),
        Err(e) => error_response(format!("task join error: {e}")),
    }
}

fn error_response(message: String) -> Response {
    eprintln!("{message}");
    (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "error": message }))).into_response()
}

async fn top_customers(State(s): State<AppState>, Query(q): Query<HashMap<String, String>>) -> Response {
    let limit = q.get("limit").and_then(|v| v.parse().ok()).unwrap_or(10i64);
    let db = s.db.clone();
    blocking_json(move || Ok(Value::Array(
        odbc_query_service::top_customers(&db, limit)?
            .into_iter()
            .map(Value::Object)
            .collect(),
    )))
    .await
}

async fn revenue_by_country(State(s): State<AppState>) -> Response {
    let db = s.db.clone();
    blocking_json(move || Ok(Value::Array(
        odbc_query_service::revenue_by_country(&db)?
            .into_iter()
            .map(Value::Object)
            .collect(),
    )))
    .await
}

async fn bench_load(State(s): State<AppState>, Query(q): Query<HashMap<String, String>>) -> Response {
    let rows = q.get("rows").and_then(|v| v.parse().ok()).unwrap_or(100_000_000i64);
    let bench = s.bench.clone();
    blocking_json(move || Ok(to_json(&bench.generate_and_load_orders(rows)?))).await
}

async fn bench_odbc(State(s): State<AppState>) -> Response {
    let bench = s.bench.clone();
    blocking_json(move || Ok(to_json(&bench.benchmark_odbc()?))).await
}

async fn bench_scanner(State(s): State<AppState>) -> Response {
    let bench = s.bench.clone();
    blocking_json(move || Ok(to_json(&bench.benchmark_mysql_scanner()?))).await
}

async fn bench_native(State(s): State<AppState>) -> Response {
    let bench = s.bench.clone();
    blocking_json(move || Ok(to_json(&bench.benchmark_native()?))).await
}
