//! Configuration, mirroring the Java demo's application.yml `app.*` block.
//!
//! Values can be overridden with environment variables (prefix APP_), which
//! keeps connection details out of the source the same way application.yml does.

use std::env;

#[derive(Clone)]
pub struct Config {
    /// In-memory DuckDB. Use e.g. "/tmp/demo.duckdb" for a persistent file.
    pub duckdb_database: String,

    /// ODBC connection string used by the DuckDB odbc extension.
    /// {MariaDB} must match the driver name registered in /etc/odbcinst.ini
    pub odbc_connection_string: String,
    /// Native connection string used by the DuckDB mysql scanner extension.
    pub scanner_connection_string: String,

    pub username: String,
    pub password: String,

    pub port: u16,
}

impl Config {
    pub fn from_env() -> Self {
        Config {
            duckdb_database: env_or("APP_DUCKDB_DATABASE", ":memory:"),
            odbc_connection_string: env_or(
                "APP_MYSQL_ODBC_CONNECTION_STRING",
                "Driver={MariaDB};Server=127.0.0.1;Port=3306;Database=demo",
            ),
            scanner_connection_string: env_or(
                "APP_MYSQL_SCANNER_CONNECTION_STRING",
                "host=127.0.0.1 port=3306 user=app_user password=app_password database=demo",
            ),
            username: env_or("APP_MYSQL_USERNAME", "app_user"),
            password: env_or("APP_MYSQL_PASSWORD", "app_password"),
            port: env_or("PORT", "8080").parse().unwrap_or(8080),
        }
    }
}

fn env_or(key: &str, fallback: &str) -> String {
    match env::var(key) {
        Ok(v) if !v.is_empty() => v,
        _ => fallback.to_string(),
    }
}
