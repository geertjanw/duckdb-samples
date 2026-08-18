# Chunked Query Results in the DuckDB Java Driver — Samples

Companion code for the blog post *"Chunked Query Results in the DuckDB Java Driver"*. Two minimal programs read the same 10M-row table (`BIGINT`, `DOUBLE`) and compute the same checksum:

| Sample | File | What it shows |
|---|---|---|
| 1 | `S01_ResultSetBaseline` | The classic row-at-a-time JDBC `ResultSet` loop — the "row-at-a-time tax" |
| 2 | `S02_ChunkedResult` | The same result read as lazily fetched columnar data chunks via `DuckDBChunkedResult` / `DuckDBDataChunkReader` ([duckdb-java#682](https://github.com/duckdb/duckdb-java/pull/682)) |

Both print row count and elapsed time, so running them back to back gives a rough feel for the difference on your machine. (For real numbers use a proper harness such as JMH — a single timed pass includes JIT warmup.)

## Requirements

- Java 17+
- Maven 3.x
- Internet access to Maven Central on first build (the `duckdb_jdbc` jar embeds the native DuckDB library — nothing else to install)

## Run

```bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S01_ResultSetBaseline
mvn -q exec:java -Dexec.mainClass=org.example.duckdb.S02_ChunkedResult
```

## Notes

- Driver version is pinned in `pom.xml` (`1.5.5.1`, latest at the time of writing — Aug 2026).
- Sample 2 follows the usage example from PR [#682](https://github.com/duckdb/duckdb-java/pull/682). Cross-check method names against your driver version if it differs.
- Chunked-result limitations as of 1.5.5.x: basic data types only (`LIST`/`STRUCT` planned), and `query()` exists on prepared statements only — no `query(String)` overload yet.
- Statement parameters are 1-based; chunk columns and rows are 0-based.
- Everything runs in-memory (`jdbc:duckdb:`); no files are created.

## Further reading

- Blog post: *Chunked Query Results in the DuckDB Java Driver* (draft)
- Java client docs: https://duckdb.org/docs/current/clients/java
- Chunked results PR: https://github.com/duckdb/duckdb-java/pull/682
