# duckdb-wasm-graalpy-demo

Three things in one request path: a **DuckDB-Wasm** browser page, a **Java**
backend, and **Python** running on **GraalPy** inside that backend.

- The browser loads DuckDB-Wasm from a CDN and runs every aggregation as SQL,
  in the tab.
- The Java `HttpServer` serves the page and one data endpoint.
- That endpoint's CSV is produced by **Python**, running on GraalPy in the same
  JVM — no Java data library, no subprocess, no separate Python service.

So the data is shaped in Python, moved once as CSV, and queried in the browser.
Each side does the part it reads most naturally: Python writes the rows, SQL
does the `GROUP BY`.

| File | Role |
|---|---|
| [`Server.java`](src/main/java/org/example/Server.java) | Boots one GraalPy context, serves the page and `/api/sales.csv` |
| [`generate.py`](src/main/resources/generate.py) | Builds the sales rows in Python; returns CSV text |
| [`index.html`](src/main/resources/index.html) | Loads DuckDB-Wasm, fetches the CSV, runs the SQL in the browser |
| [`pom.xml`](pom.xml) | GraalVM `polyglot` / `python-community` (no DuckDB on the server) |

This is the [`graalpy-scenarios/duckdb-graalpy-demo`](../../graalpy-scenarios/duckdb-graalpy-demo)
idea pointed the other way: there DuckDB is a Java library and Python post-processes
the result; here DuckDB lives in the browser and Python feeds it.

## Run it

Needs JDK 17 or newer and Maven. The first build downloads the GraalPy runtime,
which is large.

```bash
mvn -q exec:java
```

Then open <http://localhost:8080>. The page boots DuckDB-Wasm, fetches the
Python-generated CSV, and renders revenue by region — the `GROUP BY`, `sum` and
`avg` all run in the browser tab.

The same SQL the browser runs, checked against the endpoint with the DuckDB CLI:

```
| region | orders | revenue  | avg_order |
|--------|--------|----------|-----------|
| West   |    258 | 13916.63 |     53.94 |
| South  |    269 | 13477.37 |     50.10 |
| East   |    236 | 12940.69 |     54.83 |
| North  |    237 | 12193.90 |     51.45 |
```

## What to notice

- **One engine, two places.** DuckDB-Wasm in the tab is the same engine as
  server-side DuckDB, so a query written for one runs unchanged on the other.
- **Python with no boundary to cross.** GraalPy runs the generator in the JVM
  the server already has — not a REST call to a Python service, not a shelled-out
  interpreter. The context is built once and reused per request.
- **No external Python packages.** `generate.py` imports only the stdlib
  (`random`), so GraalPy needs nothing installed. For PyPI packages, GraalPy
  manages a venv through the `python-embedding` / `graalpy-maven-plugin` tooling.
- **The data leaves the server once.** After the CSV download the browser asks
  every follow-up question locally; the backend is not in the loop.

On a stock JDK, GraalPy runs in interpreter mode (the demo silences the
performance warning in `pom.xml`). On GraalVM it compiles the Python through the
same JIT as the Java.
