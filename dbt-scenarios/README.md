# dbt-scenarios

Two tiny [dbt](https://docs.getdbt.com/) projects, both using the
[dbt-duckdb](https://github.com/duckdb/dbt-duckdb) adapter (the zero-setup way to
run dbt locally — no server, no credentials).

| Folder | What it shows |
|---|---|
| [`hello-world`](hello-world) | The smallest dbt project that runs: one model, `select 'hello world'`. Nothing DuckDB-specific. |
| [`hello-world-duckdb`](hello-world-duckdb) | DuckDB doing real work inside dbt: a model reads a **remote Parquet file directly**, a mart aggregates it via `ref()`. |

Both run with:

```bash
pip install dbt-duckdb
cd dbt-scenarios/<folder>
dbt run --profiles-dir .
```

See each folder's README for details.
