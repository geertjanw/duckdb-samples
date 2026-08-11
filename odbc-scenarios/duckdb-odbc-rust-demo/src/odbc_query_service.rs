//! Uses the DuckDB ODBC extension (a.k.a. odbc_scanner) to query MySQL.
//!
//! Note the direction of the ODBC arrow:
//!  - DuckDB's ODBC *client/driver* lets other apps connect INTO DuckDB.
//!  - The ODBC *extension* (used here) lets DuckDB connect OUT to other
//!    databases through their ODBC drivers.

use crate::config::Config;
use crate::duckdb_conn::{DuckDb, Row};

/// Install/load the odbc extension and open the ODBC connection once.
///
/// The handle is stashed in a DuckDB session variable, exactly as in the Java
/// demo. Because everything runs on one shared connection, the variable stays
/// visible across every request.
pub fn init(db: &DuckDb, cfg: &Config) -> duckdb::Result<()> {
    println!("Installing and loading DuckDB odbc extension...");
    db.execute_batch("INSTALL odbc; LOAD odbc;")?;

    // Open the ODBC connection once and stash the handle in a session variable.
    db.execute(
        "SET VARIABLE conn = odbc_connect(?, ?, ?)",
        &[&cfg.odbc_connection_string, &cfg.username, &cfg.password],
    )?;
    println!("Connected to MySQL via ODBC.");
    Ok(())
}

/// Run an arbitrary (read-only) SQL statement on MySQL through ODBC.
fn query_mysql(db: &DuckDb, mysql_sql: &str) -> duckdb::Result<Vec<Row>> {
    db.query(
        "SELECT * FROM odbc_query(getvariable('conn'), ?)",
        &[mysql_sql],
    )
}

/// Simple demo query: top customers by revenue, computed inside MySQL.
pub fn top_customers(db: &DuckDb, limit: i64) -> duckdb::Result<Vec<Row>> {
    // limit is an integer, so it is safe to interpolate into the remote SQL
    // string; odbc_query's SQL argument is a constant expression, not a bind slot.
    let sql = format!(
        "SELECT id, name, country, revenue
         FROM customers
         ORDER BY revenue DESC
         LIMIT {limit}"
    );
    query_mysql(db, &sql)
}

/// DuckDB's real superpower: pull rows from MySQL over ODBC and aggregate them
/// in DuckDB, in one SQL statement.
pub fn revenue_by_country(db: &DuckDb) -> duckdb::Result<Vec<Row>> {
    db.query(
        "SELECT country, count(*) AS customers, sum(revenue) AS total_revenue
         FROM odbc_query(getvariable('conn'),
              'SELECT country, revenue FROM customers')
         GROUP BY country
         ORDER BY total_revenue DESC",
        &[],
    )
}
