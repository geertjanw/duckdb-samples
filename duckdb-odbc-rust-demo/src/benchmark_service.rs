//! Benchmark:
//!  1. Generate a large orders table and load it into MySQL/MariaDB.
//!  2. Benchmark the same aggregation through
//!     (a) the ODBC extension, (b) the native MySQL scanner extension, and
//!     (c) a local DuckDB table as the no-transfer baseline.
//!
//! Uses the DuckDB core `odbc_scanner` extension API:
//!  - odbc_query(conn, sql)  -- table function; runs any SQL (incl. DDL) in the remote DB
//!  - odbc_copy(conn, ...)   -- bulk-loads rows into the remote DB over ODBC,
//!                              batching inserts (up to 2048 rows per SQLExecute)
//!
//! Note: there is no odbc_exec in this extension; DDL also goes through odbc_query.
//!
//! MariaDB/MySQL specifics encountered along the way:
//!  - odbc_copy's create_table=true fails ("column type not recognized:
//!    DUCKDB_TYPE_BIGINT"): the automatic type mapping is incomplete for
//!    MySQL/MariaDB (Tier 2 support). We create the table explicitly instead,
//!    which also lets us keep the PRIMARY KEY.
//!  - odbc_copy quotes column names with double quotes by default, which MariaDB
//!    rejects in its default sql_mode (it expects backticks), hence column_quotes = '`'.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Instant;

use serde::Serialize;
use serde_json::Value;

use crate::config::Config;
use crate::duckdb_conn::{DuckDb, Row};

/// Owns the bulk-load and the three benchmark paths.
pub struct BenchmarkService {
    db: DuckDb,
    cfg: Config,
    scanner_attached: AtomicBool,
    // Serializes the one-time attach so two concurrent requests can't both run it.
    scanner_lock: Mutex<()>,
}

/// Per-phase timing report returned by the load endpoint.
#[derive(Serialize)]
pub struct LoadReport {
    pub generate_seconds: f64,
    pub stage_csv_seconds: f64,
    pub mysql_load_seconds: f64,
    pub rows: i64,
}

/// Shape returned by each benchmark path.
#[derive(Serialize)]
pub struct BenchResult {
    pub path: &'static str,
    pub seconds: f64,
    pub top5: Vec<Row>,
}

impl BenchmarkService {
    pub fn new(db: DuckDb, cfg: Config) -> Self {
        BenchmarkService {
            db,
            cfg,
            scanner_attached: AtomicBool::new(false),
            scanner_lock: Mutex::new(()),
        }
    }

    /// Generate `rows` orders into a local DuckDB table (which doubles as the
    /// baseline for the native benchmark), stage them as CSV, and bulk-load them
    /// into MySQL with odbc_copy. Row-by-row INSERTs over ODBC would take forever
    /// at this scale; odbc_copy batches up to 2048 rows per round trip and wraps
    /// the whole load in a remote transaction.
    ///
    /// The CSV detour exists because odbc_copy runs its source query in a
    /// separate DuckDB instance and cannot see orders_local directly. The CSV
    /// column order must match the remote table column order.
    pub fn generate_and_load_orders(&self, rows: i64) -> duckdb::Result<LoadReport> {
        let csv_path = std::env::temp_dir().join("orders.csv");
        let csv_path = csv_path.to_string_lossy().into_owned();

        // 1. Generate into a local table: the native benchmark scans this.
        let t0 = Instant::now();
        self.db.execute_batch(&format!(
            "CREATE OR REPLACE TABLE orders_local AS
             SELECT range AS id,
                    (range % 1000)::INTEGER AS customer_id,
                    round(random() * 100, 2) AS amount
             FROM range({rows})"
        ))?;
        let generate_seconds = seconds_since(t0);

        // 2. Stage as CSV for odbc_copy.
        let t1 = Instant::now();
        self.db.execute_batch(&format!(
            "COPY orders_local TO '{csv_path}' (FORMAT CSV, HEADER true)"
        ))?;
        let stage_csv_seconds = seconds_since(t1);

        // 3. Remote DDL goes through odbc_query (a table function, hence FROM ...).
        //    Explicit CREATE TABLE because create_table=true fails for MySQL/MariaDB
        //    ("column type not recognized: DUCKDB_TYPE_BIGINT").
        self.db.drain(
            "SELECT * FROM odbc_query(getvariable('conn'), ?)",
            &["DROP TABLE IF EXISTS orders"],
        )?;
        self.db.drain(
            "SELECT * FROM odbc_query(getvariable('conn'), ?)",
            &["CREATE TABLE orders (id BIGINT PRIMARY KEY, customer_id INT, amount DOUBLE)"],
        )?;

        // 4. Bulk-load over ODBC. column_quotes: MariaDB's default sql_mode rejects
        //    double-quoted identifiers, so quote with backticks.
        let t2 = Instant::now();
        self.db.execute_batch(&format!(
            "FROM odbc_copy(getvariable('conn'),
                source_file = '{csv_path}',
                dest_table = 'orders',
                batch_size = 2048,
                column_quotes = '`')"
        ))?;
        let mysql_load_seconds = seconds_since(t2);

        let report = LoadReport {
            generate_seconds,
            stage_csv_seconds,
            mysql_load_seconds,
            rows,
        };
        println!(
            "Loaded {} rows into MySQL: generate={}s stage={}s load={}s",
            rows, report.generate_seconds, report.stage_csv_seconds, report.mysql_load_seconds
        );
        Ok(report)
    }

    /// Aggregation pushed through the ODBC extension (row-by-row transfer).
    pub fn benchmark_odbc(&self) -> duckdb::Result<BenchResult> {
        let t0 = Instant::now();
        let top5 = self.db.query(
            "SELECT customer_id, sum(amount) AS total
             FROM odbc_query(getvariable('conn'),
                  'SELECT customer_id, amount FROM orders')
             GROUP BY customer_id
             ORDER BY total DESC
             LIMIT 5",
            &[],
        )?;
        Ok(BenchResult {
            path: "odbc_extension",
            seconds: seconds_since(t0),
            top5,
        })
    }

    /// Same aggregation through the native MySQL scanner extension.
    pub fn benchmark_mysql_scanner(&self) -> duckdb::Result<BenchResult> {
        self.attach_scanner()?;
        let t0 = Instant::now();
        let top5 = self.db.query(
            "SELECT customer_id, sum(amount) AS total
             FROM mysqldb.orders
             GROUP BY customer_id
             ORDER BY total DESC
             LIMIT 5",
            &[],
        )?;
        Ok(BenchResult {
            path: "mysql_scanner",
            seconds: seconds_since(t0),
            top5,
        })
    }

    /// Same aggregation on the local DuckDB table: the no-transfer baseline.
    /// Same rows as MySQL holds (loaded from the same generation), so the top-5
    /// output matches the other two paths for a given load.
    pub fn benchmark_native(&self) -> duckdb::Result<BenchResult> {
        let t0 = Instant::now();
        let top5 = self.db.query(
            "SELECT customer_id, sum(amount) AS total
             FROM orders_local
             GROUP BY customer_id
             ORDER BY total DESC
             LIMIT 5",
            &[],
        )?;
        Ok(BenchResult {
            path: "native_duckdb",
            seconds: seconds_since(t0),
            top5,
        })
    }

    /// Attach the mysql scanner lazily and exactly once: attaching twice with
    /// the same alias fails. READ_ONLY because the benchmark only reads and it
    /// lets DuckDB skip transaction bookkeeping on the attached database.
    fn attach_scanner(&self) -> duckdb::Result<()> {
        if self.scanner_attached.load(Ordering::Acquire) {
            return Ok(());
        }
        let _guard = self.scanner_lock.lock().unwrap();
        // Re-check under the lock: another request may have attached first.
        if self.scanner_attached.load(Ordering::Acquire) {
            return Ok(());
        }
        self.db.execute_batch("INSTALL mysql; LOAD mysql;")?;
        self.db.execute_batch(&format!(
            "ATTACH '{}' AS mysqldb (TYPE mysql, READ_ONLY)",
            self.cfg.scanner_connection_string
        ))?;
        self.scanner_attached.store(true, Ordering::Release);
        println!("MySQL scanner attached.");
        Ok(())
    }
}

/// Turn a Serialize value (BenchResult / LoadReport) into a serde_json::Value
/// so the handlers can return everything through one `Json<Value>` path.
pub fn to_json<T: Serialize>(value: &T) -> Value {
    serde_json::to_value(value).unwrap_or(Value::Null)
}

fn seconds_since(start: Instant) -> f64 {
    // Round to 2 decimals to match the other demos' reports.
    (start.elapsed().as_secs_f64() * 100.0).round() / 100.0
}
