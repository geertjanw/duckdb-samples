# DuckDB Samples

A repository of samples that make DuckDB immediately relevant and approachable.

## DuckDB ODBC extension demo

The same demo — query MySQL through the **DuckDB ODBC extension**, with DuckDB
embedded in a web app — built five times, once per language/framework. Each
exposes the same HTTP endpoints (`/customers/top`, `/customers/revenue-by-country`,
and an ODBC-vs-scanner-vs-native benchmark trio) over the same MariaDB dataset,
so you can compare how the identical DuckDB SQL looks through each binding.

| Demo | Language | Framework | DuckDB binding |
|---|---|---|---|
| [`duckdb-odbc-java-demo`](duckdb-odbc-java-demo) | Java | Spring Boot | JDBC |
| [`duckdb-odbc-python-demo`](duckdb-odbc-python-demo) | Python | FastAPI | `duckdb` (PyPI) |
| [`duckdb-odbc-node-demo`](duckdb-odbc-node-demo) | Node.js | Express | `@duckdb/node-api` |
| [`duckdb-odbc-go-demo`](duckdb-odbc-go-demo) | Go | net/http | `duckdb-go` (cgo) |
| [`duckdb-odbc-rust-demo`](duckdb-odbc-rust-demo) | Rust | Axum | `duckdb` crate (bundled) |

Every demo shares the same `docker-compose.yml` (a seeded MariaDB container) and
the same explanation of the ODBC chain, so start with whichever language you
know best — each README stands on its own.
