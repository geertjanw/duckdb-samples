# hello-world

The smallest dbt project that runs: one model that selects a string. DuckDB is
only here because dbt needs an adapter, and dbt-duckdb is the one that works with
zero setup — no server, no credentials, no cloud warehouse. Nothing in this
project is DuckDB-specific; see [`../hello-world-duckdb`](../hello-world-duckdb) for
that.

## Files

| File | Purpose |
|---|---|
| `dbt_project.yml` | Project name, profile, and that models are built as tables |
| `profiles.yml` | Connection: a local DuckDB file `dev.duckdb` |
| `models/hello_world.sql` | The one model — `select 'hello world' as message` |

## Run it

```bash
pip install dbt-duckdb
cd dbt-scenarios/hello-world
dbt run --profiles-dir .        # builds the hello_world table in dev.duckdb
dbt show --profiles-dir . --select hello_world   # prints the row
```

Expected output from `dbt show`:

```
| message     |
| ----------- |
| hello world |
```
