# duckdb-wasm-java-demo

A Java backend sends a dataset to the browser once; DuckDB-Wasm then runs every
aggregation and filter locally in the tab, as SQL. Two files, no Maven, no npm,
no framework.

| File | Role |
|---|---|
| [`Server.java`](Server.java) | JDK `HttpServer`: serves the page and a `/api/sales.csv` endpoint that generates 1,000 rows |
| [`index.html`](index.html) | Boots DuckDB-Wasm, fetches the CSV once, and runs a `GROUP BY` in the browser |

## Run it

Needs JDK 17 or newer, and internet on first load (the DuckDB-Wasm bundle is
pulled from the jsDelivr CDN and cached afterwards).

```bash
java Server.java
```

Then open http://localhost:8080. The status line moves from `Loading DuckDB…`
to `Fetching data from Java…` to `Analysed 1000 rows in the browser.`, and the
table shows orders, revenue and average order value per region.

## What to notice

- **Java only produces raw rows.** The `GROUP BY`, `sum` and `avg` run in the
  browser tab, not on the server. Add a filter dropdown and it re-queries
  locally, with no further request to Java.
- **Same engine, same SQL as server-side DuckDB.** A query written here runs
  unchanged against DuckDB on the JVM via JDBC.
- **`Locale.ROOT` in the CSV writer** keeps `%.2f` emitting `12.34`, not `12,34`
  on a machine whose locale uses a decimal comma — otherwise the CSV breaks.

The engine runs client-side, so the dataset has to fit in the tab and the user
can read everything sent. Filter by permissions in Java before sending; for
large or sensitive data, query server-side instead.
