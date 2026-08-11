//! Single shared DuckDB connection, the Rust analogue of DuckDbConfig.
//!
//! Why one connection? The ODBC extension stores its connection handle in a
//! DuckDB *session variable* (SET VARIABLE conn = odbc_connect(...)), which is
//! scoped to a single DuckDB connection. In the Java demo this forced a
//! SingleConnectionDataSource instead of a pool. Here it is a
//! `duckdb::Connection`, which *is* one connection, so we create it once and
//! share it. Each new connection to ':memory:' would be its own database, so
//! sharing the handle is essential, not just an optimization.
//!
//! `duckdb::Connection` is `Send` but not `Sync`, so it cannot be shared across
//! threads concurrently. We wrap it in `Arc<Mutex<..>>`: the mutex serializes
//! access to the one connection - which is also what keeps the SET VARIABLE
//! state from interleaving across concurrent requests - and matches the
//! single-connection model of the Java, Python, and Node demos. (An r2d2 pool
//! would hand out *independent* connections that don't share the session
//! variable, so a pool is the wrong tool here.)

use std::sync::{Arc, Mutex};

use duckdb::types::ValueRef;
use duckdb::{params_from_iter, Connection};
use serde_json::{Map, Value};

/// A JSON-safe row: column name -> value, matching the other demos' output.
pub type Row = Map<String, Value>;

/// Shared handle to the single DuckDB connection.
#[derive(Clone)]
pub struct DuckDb {
    conn: Arc<Mutex<Connection>>,
}

impl DuckDb {
    /// Open the in-memory (or file-backed) DuckDB. ":memory:" (the default) maps
    /// to an in-memory database, matching the other demos' convention.
    pub fn open(database: &str) -> duckdb::Result<Self> {
        let conn = if database == ":memory:" {
            Connection::open_in_memory()?
        } else {
            Connection::open(database)?
        };
        Ok(DuckDb {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    /// Run one or more `;`-separated statements that return no rows we care
    /// about (INSTALL/LOAD, ATTACH, COPY). No parameters.
    pub fn execute_batch(&self, sql: &str) -> duckdb::Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute_batch(sql)
    }

    /// Run a single statement with bound (string) parameters, e.g.
    /// `SET VARIABLE conn = odbc_connect(?, ?, ?)`.
    pub fn execute(&self, sql: &str, params: &[&str]) -> duckdb::Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(sql, params_from_iter(params.iter()))?;
        Ok(())
    }

    /// Run remote DDL/side-effecting SQL through the `odbc_query` table function.
    /// `odbc_query` is a *table* function, so it must sit in a `FROM` clause and
    /// its result set must be drained to force execution - even for DDL that
    /// returns nothing meaningful.
    pub fn drain(&self, sql: &str, params: &[&str]) -> duckdb::Result<()> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(sql)?;
        let mut rows = stmt.query(params_from_iter(params.iter()))?;
        while rows.next()?.is_some() {}
        Ok(())
    }

    /// Run a query and return rows as JSON-safe maps (like
    /// JdbcTemplate.queryForList). Column types vary per query, so each cell is
    /// converted from its DuckDB runtime type into a serde_json::Value.
    pub fn query(&self, sql: &str, params: &[&str]) -> duckdb::Result<Vec<Row>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(sql)?;

        let mut rows = stmt.query(params_from_iter(params.iter()))?;

        // Column names are only available once the statement has been executed,
        // so read them from the started result set rather than the prepared
        // statement (column_names() panics if called before execution).
        let col_names: Vec<String> = match rows.as_ref() {
            Some(s) => s.column_names(),
            None => Vec::new(),
        };

        let mut out = Vec::new();
        while let Some(row) = rows.next()? {
            let mut obj = Map::new();
            for (i, name) in col_names.iter().enumerate() {
                obj.insert(name.clone(), value_to_json(row.get_ref(i)?));
            }
            out.push(obj);
        }
        Ok(out)
    }
}

/// Convert a DuckDB value into a JSON-safe value. DECIMAL and HUGEINT are
/// rendered as strings so large/exact values survive JSON without precision
/// loss, matching the Node and Python demos (`revenue` comes back as a string).
fn value_to_json(v: ValueRef<'_>) -> Value {
    match v {
        ValueRef::Null => Value::Null,
        ValueRef::Boolean(b) => Value::from(b),
        ValueRef::TinyInt(n) => Value::from(n),
        ValueRef::SmallInt(n) => Value::from(n),
        ValueRef::Int(n) => Value::from(n),
        ValueRef::BigInt(n) => Value::from(n),
        ValueRef::HugeInt(n) => Value::from(n.to_string()),
        ValueRef::UTinyInt(n) => Value::from(n),
        ValueRef::USmallInt(n) => Value::from(n),
        ValueRef::UInt(n) => Value::from(n),
        ValueRef::UBigInt(n) => Value::from(n),
        ValueRef::Float(f) => Value::from(f),
        ValueRef::Double(f) => Value::from(f),
        ValueRef::Decimal(d) => Value::from(d.to_string()),
        ValueRef::Text(bytes) => Value::from(String::from_utf8_lossy(bytes).into_owned()),
        ValueRef::Blob(bytes) => Value::from(String::from_utf8_lossy(bytes).into_owned()),
        other => Value::from(format!("{other:?}")),
    }
}
