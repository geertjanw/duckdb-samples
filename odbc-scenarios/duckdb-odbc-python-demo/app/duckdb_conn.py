"""Single shared DuckDB connection, the Python analogue of DuckDbConfig.

Why one connection? The ODBC extension stores its connection handle in a DuckDB
*session variable* (SET VARIABLE conn = odbc_connect(...)), which is scoped to a
single DuckDB connection. In the Java demo this forced a SingleConnectionDataSource
instead of a pool. In Python it is simpler: a `duckdb.connect()` object *is* one
connection, so we create it once and share it. It also means each new connection
would be its own in-memory database, so sharing the handle is essential.

DuckDB's Python connection is not thread-safe for concurrent use, so a lock
serializes access - fine for a demo, and honest about the single-connection model.
"""
from threading import Lock
from typing import Any

import duckdb

from .config import settings

_conn: duckdb.DuckDBPyConnection = duckdb.connect(settings.duckdb_database)
_lock = Lock()


def query(sql: str, params: list[Any] | None = None) -> list[dict[str, Any]]:
    """Run a query and return rows as a list of dicts (like JdbcTemplate.queryForList)."""
    with _lock:
        cur = _conn.execute(sql, params) if params else _conn.execute(sql)
        columns = [d[0] for d in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def execute(sql: str, params: list[Any] | None = None) -> None:
    """Run a statement that returns no rows (like JdbcTemplate.execute/update)."""
    with _lock:
        _conn.execute(sql, params) if params else _conn.execute(sql)
