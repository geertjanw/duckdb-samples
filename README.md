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

**Benchmark trio (`/benchmark/*`), 100,000,000 generated `orders` rows.** All
five ship the same DuckDB core (1.5.5), open it in-memory, and default to the
same 12 threads, so the aggregation itself is the same work everywhere — the
binding just marshals the call. Each demo below was run against its own **freshly
recreated MariaDB** (`docker compose down -v` between runs) so InnoDB started cold
and comparable:

| Demo | Load into MariaDB | ODBC extension | MySQL scanner | Native DuckDB |
|---|---|---|---|---|
| Java | 244.4 s | 36.2 s | 12.5 s | 0.22 s |
| Python | 128.2 s | 21.8 s | 3.7 s | 0.18 s |
| Node.js | 239.2 s | 34.8 s | 11.7 s | 0.18 s |
| Go | 244.1 s | 34.9 s | 12.1 s | 0.13 s |
| Rust | 206.2 s | 35.1 s | 12.3 s | 0.20 s |

Reading the table:

- **The `native` column is the clean apples-to-apples number** — a pure local
  DuckDB aggregation with no MySQL round-trip. It is essentially identical across
  all five (0.13–0.22 s), which is the real result: **the language binding does
  not change DuckDB's execution speed.**
- **The relative ordering is stark and holds within every single run**: the
  **ODBC extension** is the slowest read path (streams every row over ODBC), the
  **native MySQL scanner** is roughly 3× faster, and the **local DuckDB table** is
  ~100× faster again — effectively instant. This matched a smaller 1,000,000-row
  pass where all five clustered at ODBC ~0.31 s, scanner ~0.11 s, native ~0 s.
- **The MySQL-touching columns (load, ODBC, scanner) carry real variance** that is
  *not* about the language: they depend on MariaDB/InnoDB and host disk state. The
  four JVM/Go/Node/Rust runs cluster tightly (ODBC ~35 s, scanner ~12 s), while
  the Python run reproduced markedly lower numbers (ODBC 21.8 s, scanner 3.7 s) in
  two separate measurements. Both DuckDB builds are 1.5.5 with identical settings,
  so this looks like a difference in the PyPI build / its downloaded extension
  binaries rather than anything about Python the language — recorded here as an
  honest observation, not a fully diagnosed root cause.

Bottom line: choose a language for its ergonomics. Within a run the data path you
pick (ODBC vs. scanner vs. local) dominates everything, and the no-transfer local
DuckDB baseline is identical across all five bindings.
