# dbt-scenarios

A [dbt](https://docs.getdbt.com/) project built on the
[dbt-duckdb](https://github.com/duckdb/dbt-duckdb) adapter (the zero-setup way to
run dbt locally — no server, no credentials), plus the talk materials that demo it.

| Folder | What it is |
|---|---|
| [`end-to-end-duckdb`](end-to-end-duckdb) | A dbt project **developed, tested, deployed, and served** entirely on DuckDB — a Python model, open-Parquet output, and a DuckDB-WASM page. Built to demo live in a few commands. |
| [`presentation`](presentation) | Outline, speaker notes, and a DuckDB-branded deck for presenting the demo above. |

Run the project with:

```bash
pip install dbt-duckdb pandas
cd dbt-scenarios/end-to-end-duckdb
export DBT_PROFILES_DIR=.
dbt run
```

See [`end-to-end-duckdb/README.md`](end-to-end-duckdb/README.md) for the full
four-command demo.
