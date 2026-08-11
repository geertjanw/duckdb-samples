# DuckDB Samples

A repository of samples that make DuckDB immediately relevant and approachable.

The samples are grouped into scenario folders:

| Folder | What it shows |
|---|---|
| [`odbc-scenarios`](odbc-scenarios) | Querying MySQL through the DuckDB **ODBC extension** with DuckDB embedded in a web app — the same demo built five times, once per language/framework. |
| [`batch-scenarios`](batch-scenarios) | Data transformation inside **Spring Batch** jobs — DuckDB's vectorized engine vs. looping over rows in in-memory Java collections. |

## odbc-scenarios: DuckDB ODBC extension demo

The same demo — query MySQL through the **DuckDB ODBC extension**, with DuckDB
embedded in a web app — built five times, once per language/framework. Each
exposes the same HTTP endpoints (`/customers/top`, `/customers/revenue-by-country`,
and an ODBC-vs-scanner-vs-native benchmark trio) over the same MariaDB dataset,
so you can compare how the identical DuckDB SQL looks through each binding.

| Demo | Language | Framework | DuckDB binding |
|---|---|---|---|
| [`duckdb-odbc-java-demo`](odbc-scenarios/duckdb-odbc-java-demo) | Java | Spring Boot | JDBC |
| [`duckdb-odbc-python-demo`](odbc-scenarios/duckdb-odbc-python-demo) | Python | FastAPI | `duckdb` (PyPI) |
| [`duckdb-odbc-node-demo`](odbc-scenarios/duckdb-odbc-node-demo) | Node.js | Express | `@duckdb/node-api` |
| [`duckdb-odbc-go-demo`](odbc-scenarios/duckdb-odbc-go-demo) | Go | net/http | `duckdb-go` (cgo) |
| [`duckdb-odbc-rust-demo`](odbc-scenarios/duckdb-odbc-rust-demo) | Rust | Axum | `duckdb` crate (bundled) |

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
five ship the same DuckDB core (1.5.5), open it in-memory, default to the same 12
threads, and load the *same* `odbc`/`mysql` extension binaries from the shared
`~/.duckdb` cache. The aggregation is therefore identical work everywhere — the
binding only marshals the call.

The **relative ordering of the three data paths is the real lesson, and it is
stark** (representative single run, 100M rows):

| Path | Time | What it does |
|---|---|---|
| ODBC extension (`/benchmark/odbc`) | ~35–40 s | Streams every row out of MySQL over ODBC, then aggregates |
| MySQL scanner (`/benchmark/scanner`) | ~12 s | Aggregates through DuckDB's native MySQL scanner |
| Native DuckDB (`/benchmark/native`) | ~0.2 s | Aggregates a local DuckDB table — no round-trip |

Choosing the data path is worth ~3× (ODBC → scanner) and then ~60× again
(scanner → local, no transfer). The same ordering held in a smaller 1,000,000-row
pass (ODBC ~0.31 s, scanner ~0.11 s, native ~0 s).

**The language binding does *not* change performance.** An early set of
per-language runs *appeared* to show Python 2–3× faster on the MySQL-reading
paths, but that was a benchmarking artifact, not a real effect. Measuring each
demo in its own separate run let host-level state drift between runs — the macOS
page cache, the Docker/Rancher VM's buffer cache, and warm disk blocks all persist
and keep warming across a session, and `docker compose down -v` resets only the
container's InnoDB, not that host state. A controlled A/B settles it: with Java and
Python pointed at the **same** loaded 100M table and queried back to back, the
numbers are identical within noise —

| Path (same table, back-to-back) | Java | Python |
|---|---|---|
| MySQL scanner | 12.4 / 12.1 / 11.9 s | 12.2 / 12.1 / 12.4 s |
| ODBC extension | 38.0 s | 41.2 s |

The `native` (no-I/O) path was likewise identical across all five (0.13–0.22 s)
from the start — the giveaway that only the MySQL-touching paths were picking up
external variance.

Bottom line: choose a language for its ergonomics. Within a run the *data path*
you pick dominates everything; the binding does not. And treat absolute
MySQL-path timings as I/O-bound and machine-dependent — compare paths within one
run, not one language against another across runs.

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
