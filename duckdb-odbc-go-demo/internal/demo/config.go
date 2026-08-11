// Package demo holds the DuckDB-ODBC demo: configuration, the single shared
// DuckDB connection, and the ODBC query / benchmark services.
package demo

import "os"

// Config mirrors the Java demo's application.yml `app.*` block. Values can be
// overridden with APP_* environment variables, which keeps connection details
// out of the source the same way application.yml does.
type Config struct {
	// In-memory DuckDB. Use e.g. "/tmp/demo.duckdb" for a persistent file.
	DuckDBDatabase string

	// ODBC connection string used by the DuckDB odbc extension.
	// {MariaDB} must match the driver name registered in /etc/odbcinst.ini
	ODBCConnectionString string
	// Native connection string used by the DuckDB mysql scanner extension.
	ScannerConnectionString string

	Username string
	Password string

	Port string
}

// LoadConfig reads configuration from the environment, falling back to the
// same defaults as the other demos.
func LoadConfig() Config {
	return Config{
		DuckDBDatabase: env("APP_DUCKDB_DATABASE", ":memory:"),
		ODBCConnectionString: env(
			"APP_MYSQL_ODBC_CONNECTION_STRING",
			"Driver={MariaDB};Server=127.0.0.1;Port=3306;Database=demo",
		),
		ScannerConnectionString: env(
			"APP_MYSQL_SCANNER_CONNECTION_STRING",
			"host=127.0.0.1 port=3306 user=app_user password=app_password database=demo",
		),
		Username: env("APP_MYSQL_USERNAME", "app_user"),
		Password: env("APP_MYSQL_PASSWORD", "app_password"),
		Port:     env("PORT", "8080"),
	}
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
