# ODBC scenarios

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

## Run every demo with one command

From the repository root:

```bash
./run-all-demos.sh
```

Because each demo on its own listens on port `8080` and expects its own MariaDB
on `3306`, this script brings up a **single shared MariaDB** and then starts each
app on its own port:

| Demo | URL |
|---|---|
| Java (Spring Boot) | http://localhost:8081 |
| Python (FastAPI) | http://localhost:8082 |
| Node.js (Express) | http://localhost:8083 |
| Go (net/http) | http://localhost:8084 |
| Rust (Axum) | http://localhost:8085 |

Each demo needs its own toolchain (`mvn`, `python3`, `npm`, `go`, `cargo`) plus
Docker; any demo whose toolchain is missing is skipped with a note, and the rest
still run. Per-demo output is written to `./logs/<demo>.log`, and first-time
builds (Maven, Cargo, npm) can take a while. Press `Ctrl+C` to stop every app and
tear down the shared MariaDB.

## Results

All five give **identical business results** (e.g. `/customers/revenue-by-country`
returns `US: 5, 1195500.85`, `NL: 4, 478501.20`, `DE: 2, 187000.90`,
`FR: 1, 89000.60` everywhere). The differences are only at the **JSON
serialization layer** — the same DuckDB `DECIMAL`/`BIGINT` encoded per binding
(from `/customers/top`):

| Demo | `DECIMAL` revenue | `BIGINT` count | JSON key order |
|---|---|---|---|
| Java | number — `560000.10` | number — `5` | insertion |
| Python | number — `560000.1` | number — `5` | insertion |
| Node.js | **string** — `"560000.10"` | **string** — `"5"` | insertion |
| Go | **string** — `"560000.1"` | number — `5` | alphabetical |
| Rust | **string** — `"560000.10"` | number — `5` | struct order |

Native bindings (Node, Go, Rust) return `DECIMAL` as a string to avoid float
loss; JDBC/PyPI emit a number; Node also stringifies `BIGINT`; key order follows
each language's map/struct conventions.

## Benchmark

The **benchmark trio** (100M `orders` rows) shows the *data path* dominates — same
DuckDB core and identical work everywhere, the binding only marshals the call:

| Path | Time | What it does |
|---|---|---|
| ODBC extension | ~35–40 s | Streams every row out of MySQL over ODBC, then aggregates |
| MySQL scanner | ~12 s | Aggregates through DuckDB's native MySQL scanner |
| Native DuckDB | ~0.2 s | Aggregates a local DuckDB table — no round-trip |

The three rows are three different *paths to the same aggregation*, from slowest
to fastest. Moving from the ODBC extension to DuckDB's native MySQL scanner is
worth about **3×** (~35–40 s → ~12 s), because the scanner pushes the work down
instead of streaming every row out of MySQL. Moving again from the scanner to a
local DuckDB table is worth about another **60×** (~12 s → ~0.2 s), because there
is no MySQL round-trip at all. So the two speedups compound: what matters is
*where the data lives and how it is read*, not the client language.

The **language binding does not change performance**: a controlled A/B (Java vs
Python, same 100M table, back-to-back) is identical within noise — earlier
"Python faster" runs were a host-cache artifact, not a real effect.
