"""Configuration, mirroring the Java demo's application.yml `app.*` block.

Values can be overridden with environment variables (prefix APP_), which keeps
connection details out of the source the same way application.yml does.
"""
from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    # In-memory DuckDB. Use e.g. "/tmp/demo.duckdb" for a persistent file.
    duckdb_database: str = os.getenv("APP_DUCKDB_DATABASE", ":memory:")

    # ODBC connection string used by the DuckDB odbc extension.
    # {MariaDB} must match the driver name registered in /etc/odbcinst.ini
    odbc_connection_string: str = os.getenv(
        "APP_MYSQL_ODBC_CONNECTION_STRING",
        "Driver={MariaDB};Server=127.0.0.1;Port=3306;Database=demo",
    )
    # Native connection string used by the DuckDB mysql scanner extension.
    scanner_connection_string: str = os.getenv(
        "APP_MYSQL_SCANNER_CONNECTION_STRING",
        "host=127.0.0.1 port=3306 user=app_user password=app_password database=demo",
    )
    username: str = os.getenv("APP_MYSQL_USERNAME", "app_user")
    password: str = os.getenv("APP_MYSQL_PASSWORD", "app_password")


settings = Settings()
