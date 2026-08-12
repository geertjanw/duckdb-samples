# DuckDB Samples

A repository of samples that make DuckDB immediately relevant and approachable.

The samples are grouped into scenario folders:

| Folder | What it shows |
|---|---|
| [`odbc-scenarios`](odbc-scenarios) | Querying MySQL through the DuckDB **ODBC extension** with DuckDB embedded in a web app — the same demo built five times, once per language/framework. |
| [`batch-scenarios`](batch-scenarios) | Data transformation inside **Spring Batch** jobs — DuckDB's vectorized engine vs. looping over rows in in-memory Java collections. |

## odbc-scenarios: DuckDB ODBC vs. MySQL scanner in a web app

Query MySQL through the **DuckDB ODBC extension**, with DuckDB embedded in a web
app — built five times, once per language. See
[`odbc-scenarios`](odbc-scenarios) for details.

| Demo | Language | DuckDB binding |
|---|---|---|
| [`duckdb-odbc-java-demo`](odbc-scenarios/duckdb-odbc-java-demo) | Java (Spring Boot) | JDBC |
| [`duckdb-odbc-python-demo`](odbc-scenarios/duckdb-odbc-python-demo) | Python (FastAPI) | `duckdb` (PyPI) |
| [`duckdb-odbc-node-demo`](odbc-scenarios/duckdb-odbc-node-demo) | Node.js (Express) | `@duckdb/node-api` |
| [`duckdb-odbc-go-demo`](odbc-scenarios/duckdb-odbc-go-demo) | Go (net/http) | `duckdb-go` (cgo) |
| [`duckdb-odbc-rust-demo`](odbc-scenarios/duckdb-odbc-rust-demo) | Rust (Axum) | `duckdb` crate |

All five give **identical business results**; only JSON serialization differs.
The benchmark trio (100M `orders` rows) shows the data path dominates:

| Path | Time | What it does |
|---|---|---|
| ODBC extension | ~35–40 s | Streams every MySQL row over ODBC, then aggregates |
| MySQL scanner | ~12 s | Aggregates through DuckDB's native MySQL scanner |
| Native DuckDB | ~0.2 s | Aggregates a local DuckDB table — no round-trip |

The binding does not change performance; choosing the path is worth ~3× then
~60× again.

## batch-scenarios: DuckDB vs. Java loops in a Spring Batch job

Two Spring Boot + Spring Batch apps run the *same* transformation over the *same*
generated dataset — one looping over rows into an in-memory Java `HashMap`, the
other handing the aggregation to DuckDB's vectorized engine as one SQL statement.
See [`batch-scenarios`](batch-scenarios) for details.

| Demo | Transform step |
|---|---|
| [`spring-batch-java-demo`](batch-scenarios/spring-batch-java-demo) | Row-at-a-time loop into in-memory Java collections |
| [`spring-batch-duckdb-demo`](batch-scenarios/spring-batch-duckdb-demo) | One `COPY (SELECT … GROUP BY …)` on DuckDB's vectorized engine |

Both produce a **byte-identical** 8,000-group summary; only the transform time
differs (this machine, timing the transform step only):

| Rows | Java (in-memory collections) | DuckDB (vectorized) | Speedup |
|---|---|---|---|
| 10,000,000 | 1.00 s | 0.17 s | ~6× |
| 50,000,000 | 4.83 s | 0.61 s | ~8× |

The Java loop is single-threaded and pays per-row parsing/boxing costs; DuckDB
vectorizes the scan and aggregation across all cores — which is exactly why you'd
reach for it as the transformation step inside a batch job.
