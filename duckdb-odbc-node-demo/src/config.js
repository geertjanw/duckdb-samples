// Configuration, mirroring the Java demo's application.yml `app.*` block.
// Values can be overridden with APP_* environment variables, which keeps
// connection details out of the source the same way application.yml does.

export const config = {
  duckdb: {
    // In-memory DuckDB. Use e.g. "/tmp/demo.duckdb" for a persistent file.
    database: process.env.APP_DUCKDB_DATABASE ?? ':memory:',
  },
  mysql: {
    // ODBC connection string used by the DuckDB odbc extension.
    // {MariaDB} must match the driver name registered in /etc/odbcinst.ini
    odbcConnectionString:
      process.env.APP_MYSQL_ODBC_CONNECTION_STRING ??
      'Driver={MariaDB};Server=127.0.0.1;Port=3306;Database=demo',
    // Native connection string used by the DuckDB mysql scanner extension.
    scannerConnectionString:
      process.env.APP_MYSQL_SCANNER_CONNECTION_STRING ??
      'host=127.0.0.1 port=3306 user=app_user password=app_password database=demo',
    username: process.env.APP_MYSQL_USERNAME ?? 'app_user',
    password: process.env.APP_MYSQL_PASSWORD ?? 'app_password',
  },
  port: Number(process.env.PORT ?? 8080),
};
