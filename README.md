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

### Run every demo with one command

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

### Results: running all five side by side

All five demos were built and run together against the same seeded MariaDB
(toolchains used: Java 21, Python 3.13, Node 20, Go 1.24 auto-fetched, Rust via
Cargo 1.97). Every one started cleanly, installed and loaded the DuckDB `odbc`
extension at startup, and answered the same endpoints.

**The business results are identical across all five.** For example,
`/customers/revenue-by-country` returns the same rows and totals everywhere:

```
US: 5 customers, 1195500.85    NL: 4 customers, 478501.20
DE: 2 customers,  187000.90    FR: 1 customer,   89000.60
```

**The interesting differences are at the JSON serialization layer** — the same
DuckDB `DECIMAL` and `BIGINT` values are encoded differently by each binding.
From `/customers/top` (revenue is a `DECIMAL`, counts are `BIGINT`):

| Demo | `DECIMAL` revenue | `BIGINT` count | JSON key order |
|---|---|---|---|
| Java | number, trailing zeros kept — `560000.10` | number — `5` | insertion (`id, name, country, revenue`) |
| Python | number, trailing zeros dropped — `560000.1` | number — `5` | insertion |
| Node.js | **string**, 2 decimals — `"560000.10"` | **string** — `"5"` | insertion |
| Go | **string**, trailing zeros dropped — `"560000.1"`, `"480000"` | number — `5` | alphabetical (`country, id, name, revenue`) |
| Rust | **string**, 2 decimals — `"560000.10"` | number — `5` | struct order (`country, id, name, revenue`) |

Takeaways: the three native-binding demos (Node, Go, Rust) return `DECIMAL` as a
**string** to avoid float precision loss, while the JDBC and PyPI bindings emit a
JSON **number**; Node additionally stringifies `BIGINT`; and key ordering follows
each language's map/struct conventions (Go sorts map keys alphabetically, Rust
follows struct field order, the JVM/Python preserve insertion order).

**Benchmark trio (`/benchmark/*`), 1,000,000 generated `orders` rows.** The
timings are effectively identical across languages, because the aggregation runs
inside DuckDB/MariaDB — the binding only marshals the call, so it barely moves the
numbers:

| Demo | Load into MariaDB | ODBC extension | MySQL scanner | Native DuckDB |
|---|---|---|---|---|
| Java | 2.39 s | 0.31 s | 0.11 s | ~0.00 s |
| Python | 2.05 s | 0.32 s | 0.12 s | ~0.00 s |
| Node.js | 2.23 s | 0.33 s | 0.12 s | ~0.00 s |
| Go | 2.41 s | 0.31 s | 0.11 s | ~0.00 s |
| Rust | 2.19 s | 0.31 s | 0.12 s | ~0.00 s |

The relative ordering is the real lesson and it holds everywhere: reading through
the **ODBC extension** is the slowest path (~0.3 s), the **native MySQL scanner**
is roughly 3× faster (~0.1 s), and querying a **local DuckDB table** with no
round-trip is effectively instant. Choose a language for its ergonomics — the
DuckDB engine, not the binding, decides performance.
