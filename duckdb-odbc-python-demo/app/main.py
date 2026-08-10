"""FastAPI entry point and HTTP routes - the Python analogue of DemoController
plus DuckdbOdbcApplication.

The ODBC connection is opened at startup (lifespan) and fails fast if MySQL is
not reachable, matching the Java demo's @PostConstruct behavior.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from . import benchmark_service, odbc_query_service

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Open the ODBC connection at startup; fail fast if MySQL isn't reachable.
    odbc_query_service.init()
    yield


app = FastAPI(title="DuckDB ODBC Extension × FastAPI Demo", lifespan=lifespan)


@app.get("/customers/top")
def top_customers(limit: int = 10):
    """Top customers, computed inside MySQL, fetched through ODBC."""
    return odbc_query_service.top_customers(limit)


@app.get("/customers/revenue-by-country")
def revenue_by_country():
    """MySQL rows aggregated by DuckDB - the hybrid query demo."""
    return odbc_query_service.revenue_by_country()


@app.post("/benchmark/load")
def load(rows: int = 100_000_000):
    """Stretch goal: generate and load N rows into MySQL (default 100M)."""
    return benchmark_service.generate_and_load_orders(rows)


@app.get("/benchmark/odbc")
def bench_odbc():
    """Benchmark the aggregation through the ODBC extension."""
    return benchmark_service.benchmark_odbc()


@app.get("/benchmark/scanner")
def bench_scanner():
    """Benchmark the same aggregation through the native MySQL scanner."""
    return benchmark_service.benchmark_mysql_scanner()


@app.get("/benchmark/native")
def bench_native():
    """Benchmark the same aggregation on the local DuckDB table (no transfer)."""
    return benchmark_service.benchmark_native()
