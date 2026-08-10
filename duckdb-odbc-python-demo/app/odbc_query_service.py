"""Uses the DuckDB ODBC extension (a.k.a. odbc_scanner) to query MySQL.

Note the direction of the ODBC arrow:
 - DuckDB's ODBC *client/driver* lets other apps connect INTO DuckDB.
 - The ODBC *extension* (used here) lets DuckDB connect OUT to other databases
   through their ODBC drivers.
"""
import logging
from typing import Any

from . import duckdb_conn
from .config import settings

log = logging.getLogger(__name__)


def init() -> None:
    """Install/load the odbc extension and open the ODBC connection once.

    The handle is stashed in a DuckDB session variable, exactly as in the Java
    demo. Because we run everything on one shared connection, the variable stays
    visible across every request.
    """
    log.info("Installing and loading DuckDB odbc extension...")
    duckdb_conn.execute("INSTALL odbc")
    duckdb_conn.execute("LOAD odbc")

    # Open the ODBC connection once and stash the handle in a session variable.
    duckdb_conn.execute(
        "SET VARIABLE conn = odbc_connect(?, ?, ?)",
        [settings.odbc_connection_string, settings.username, settings.password],
    )
    log.info("Connected to MySQL via ODBC.")


def query_mysql(mysql_sql: str) -> list[dict[str, Any]]:
    """Run an arbitrary (read-only) SQL statement on MySQL through ODBC."""
    return duckdb_conn.query(
        "SELECT * FROM odbc_query(getvariable('conn'), ?)", [mysql_sql]
    )


def top_customers(limit: int) -> list[dict[str, Any]]:
    """Simple demo query: top customers by revenue, computed inside MySQL."""
    return query_mysql(
        f"""
        SELECT id, name, country, revenue
        FROM customers
        ORDER BY revenue DESC
        LIMIT {limit}"""
    )


def revenue_by_country() -> list[dict[str, Any]]:
    """DuckDB's real superpower: pull rows from MySQL over ODBC and aggregate
    them in DuckDB, in one SQL statement."""
    return duckdb_conn.query(
        """
        SELECT country, count(*) AS customers, sum(revenue) AS total_revenue
        FROM odbc_query(getvariable('conn'),
             'SELECT country, revenue FROM customers')
        GROUP BY country
        ORDER BY total_revenue DESC
        """
    )
