"""Benchmark:
 1. Generate a large orders table and load it into MySQL/MariaDB.
 2. Benchmark the same aggregation through
    (a) the ODBC extension, (b) the native MySQL scanner extension, and
    (c) a local DuckDB table as the no-transfer baseline.

Uses the DuckDB core `odbc_scanner` extension API:
 - odbc_query(conn, sql)  -- table function; runs any SQL (incl. DDL) in the remote DB
 - odbc_copy(conn, ...)   -- bulk-loads rows into the remote DB over ODBC,
                             batching inserts (up to 2048 rows per SQLExecute)
Note: there is no odbc_exec in this extension; DDL also goes through odbc_query.

MariaDB/MySQL specifics encountered along the way:
 - odbc_copy's create_table=true fails ("column type not recognized:
   DUCKDB_TYPE_BIGINT"): the automatic type mapping is incomplete for
   MySQL/MariaDB (Tier 2 support). We create the table explicitly instead,
   which also lets us keep the PRIMARY KEY.
 - odbc_copy quotes column names with double quotes by default, which MariaDB
   rejects in its default sql_mode (it expects backticks), hence column_quotes = '`'.
"""
import logging
import tempfile
import time
from pathlib import Path
from threading import Lock
from typing import Any

from . import duckdb_conn
from .config import settings

log = logging.getLogger(__name__)

_scanner_attached = False
_scanner_lock = Lock()


def generate_and_load_orders(rows: int) -> dict[str, Any]:
    """Generate `rows` orders into a local DuckDB table (which doubles as the
    baseline for the native benchmark), stage them as CSV, and bulk-load them
    into MySQL with odbc_copy. Row-by-row INSERTs over ODBC would take forever
    at this scale; odbc_copy batches up to 2048 rows per round trip and wraps
    the whole load in a remote transaction.

    The CSV detour exists because odbc_copy runs its source query in a separate
    DuckDB instance and cannot see orders_local directly. The CSV column order
    must match the remote table column order.
    """
    report: dict[str, Any] = {}
    csv_path = str(Path(tempfile.gettempdir()) / "orders.csv")

    # 1. Generate into a local table: the native benchmark scans this.
    t0 = time.perf_counter()
    duckdb_conn.execute(
        f"""
        CREATE OR REPLACE TABLE orders_local AS
        SELECT range AS id,
               (range % 1000)::INTEGER AS customer_id,
               round(random() * 100, 2) AS amount
        FROM range({rows})
        """
    )
    report["generate_seconds"] = _seconds_since(t0)

    # 2. Stage as CSV for odbc_copy.
    t1 = time.perf_counter()
    duckdb_conn.execute(
        f"COPY orders_local TO '{csv_path}' (FORMAT CSV, HEADER true)"
    )
    report["stage_csv_seconds"] = _seconds_since(t1)

    # 3. Remote DDL goes through odbc_query (a table function, hence FROM ...).
    #    Explicit CREATE TABLE because create_table=true fails for MySQL/MariaDB
    #    ("column type not recognized: DUCKDB_TYPE_BIGINT").
    duckdb_conn.execute(
        "FROM odbc_query(getvariable('conn'), 'DROP TABLE IF EXISTS orders')"
    )
    duckdb_conn.execute(
        """
        FROM odbc_query(getvariable('conn'),
            'CREATE TABLE orders (
                 id BIGINT PRIMARY KEY,
                 customer_id INT,
                 amount DOUBLE)')
        """
    )

    # 4. Bulk-load over ODBC. column_quotes: MariaDB's default sql_mode rejects
    #    double-quoted identifiers, so quote with backticks.
    t2 = time.perf_counter()
    duckdb_conn.execute(
        f"""
        FROM odbc_copy(getvariable('conn'),
            source_file = '{csv_path}',
            dest_table = 'orders',
            batch_size = 2048,
            column_quotes = '`')
        """
    )
    report["mysql_load_seconds"] = _seconds_since(t2)
    report["rows"] = rows
    log.info("Loaded %d rows into MySQL: %s", rows, report)
    return report


def benchmark_odbc() -> dict[str, Any]:
    """Aggregation pushed through the ODBC extension (row-by-row transfer)."""
    t0 = time.perf_counter()
    result = duckdb_conn.query(
        """
        SELECT customer_id, sum(amount) AS total
        FROM odbc_query(getvariable('conn'),
             'SELECT customer_id, amount FROM orders')
        GROUP BY customer_id
        ORDER BY total DESC
        LIMIT 5
        """
    )
    return _report("odbc_extension", _seconds_since(t0), result)


def benchmark_mysql_scanner() -> dict[str, Any]:
    """Same aggregation through the native MySQL scanner extension."""
    _attach_mysql_scanner()
    t0 = time.perf_counter()
    result = duckdb_conn.query(
        """
        SELECT customer_id, sum(amount) AS total
        FROM mysqldb.orders
        GROUP BY customer_id
        ORDER BY total DESC
        LIMIT 5
        """
    )
    return _report("mysql_scanner", _seconds_since(t0), result)


def benchmark_native() -> dict[str, Any]:
    """Same aggregation on the local DuckDB table: the no-transfer baseline.
    Same rows as MySQL holds (loaded from the same generation), so the top-5
    output matches the other two paths for a given load."""
    t0 = time.perf_counter()
    result = duckdb_conn.query(
        """
        SELECT customer_id, sum(amount) AS total
        FROM orders_local
        GROUP BY customer_id
        ORDER BY total DESC
        LIMIT 5
        """
    )
    return _report("native_duckdb", _seconds_since(t0), result)


def _attach_mysql_scanner() -> None:
    global _scanner_attached
    with _scanner_lock:
        if _scanner_attached:
            return
        duckdb_conn.execute("INSTALL mysql")
        duckdb_conn.execute("LOAD mysql")
        duckdb_conn.execute(
            f"ATTACH '{settings.scanner_connection_string}' "
            "AS mysqldb (TYPE mysql, READ_ONLY)"
        )
        _scanner_attached = True
        log.info("MySQL scanner attached.")


def _seconds_since(start: float) -> float:
    return round(time.perf_counter() - start, 2)


def _report(path: str, seconds: float, sample: list[dict[str, Any]]) -> dict[str, Any]:
    return {"path": path, "seconds": seconds, "top5": sample}
